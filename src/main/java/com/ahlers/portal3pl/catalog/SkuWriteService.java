package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorContext;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ведение справочника товаров и упаковок.
 *
 * <p>Кто правит: поклажедатель — свои товары, менеджер и администратор —
 * товары того поклажедателя, с которым работают. Складской сотрудник
 * справочник не меняет, разработчик — только смотрит.
 *
 * <p>Изоляция обеспечивается тем же способом, что и везде: запись идёт
 * в контексте выбранного поклажедателя, политики RLS не дадут ни создать
 * товар чужому клиенту, ни изменить чужой — подставлять `depositor_id`
 * вручную не требуется и не разрешено.
 *
 * <p>Новый товар всегда создаётся с `is_in_wms = false` (решение Р5):
 * пока синхронизация не подтвердит, что WMS о нём знает, документ с этой
 * позицией подтвердить нельзя. Так клиент может завести товар и начать
 * готовить поступление, не дожидаясь обмена, но отправить незнакомую
 * складу позицию не сможет.
 */
@Service
public class SkuWriteService {

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final CurrentUser currentUser;

    public SkuWriteService(JdbcClient jdbc, DepositorScope depositorScope, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.currentUser = currentUser;
    }

    /* ======================= Товары ======================= */

    @Transactional
    public Map<String, Object> createSku(Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        Map<String, Object> clean = filter(values, SkuColumns.SKU);
        requireFields(clean, SkuColumns.SKU_REQUIRED, "товар");

        List<String> columns = new ArrayList<>(clean.keySet());
        String names = String.join(", ", columns);
        String params = columns.stream()
                .map(c -> ":" + c + "::" + SkuColumns.SKU.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        String sql = """
                INSERT INTO dbo.sku (depositor_id, %s, is_active, is_in_wms, created_by, created_at)
                VALUES (dbo.current_depositor_id(), %s, true, false, :__author, now())
                RETURNING sku_id::text
                """.formatted(names, params);

        var spec = jdbc.sql(sql).param("__author", author());
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        String skuId;
        try {
            skuId = spec.query(String.class).single();
        } catch (DuplicateKeyException e) {
            throw duplicateCode(clean.get("code"));
        }
        return card(skuId);
    }

    @Transactional
    public Map<String, Object> updateSku(String skuId, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        requireSkuExists(skuId);

        Map<String, Object> clean = filter(values, SkuColumns.SKU);
        if (clean.isEmpty()) {
            return card(skuId);
        }
        // Обязательные поля проверяем только если их присылают: экран может
        // сохранять карточку частями, и пустое поле «наименование» в такой
        // отправке означало бы стирание нужного значения.
        for (String field : SkuColumns.SKU_REQUIRED) {
            if (clean.containsKey(field) && isBlank(clean.get(field))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Поле «" + field + "» не может быть пустым");
            }
        }

        String assignments = clean.keySet().stream()
                .map(c -> c + " = :" + c + "::" + SkuColumns.SKU.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("""
                        UPDATE dbo.sku SET %s, updated_by = :__author, updated_at = now()
                        WHERE sku_id = :__skuId::uuid
                        """.formatted(assignments))
                .param("__skuId", skuId)
                .param("__author", author());
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        try {
            spec.update();
        } catch (DuplicateKeyException e) {
            throw duplicateCode(clean.get("code"));
        }
        return card(skuId);
    }

    /** В архив и обратно: позиция исчезает из выбора, история остаётся. */
    @Transactional
    public Map<String, Object> setSkuActive(String skuId, boolean active) {
        depositorScope.apply();
        requireEditor();
        requireSkuExists(skuId);
        jdbc.sql("""
                        UPDATE dbo.sku SET is_active = :active,
                               updated_by = :author, updated_at = now()
                        WHERE sku_id = :id::uuid
                        """)
                .param("active", active)
                .param("author", author())
                .param("id", skuId)
                .update();
        return card(skuId);
    }

    /**
     * Удаление — только для случайно заведённых позиций. Если товар уже
     * где-то задействован, отказываем и объясняем, где именно: «нельзя»
     * без объяснения заставляет человека гадать.
     */
    @Transactional
    public void deleteSku(String skuId) {
        depositorScope.apply();
        requireEditor();
        requireSkuExists(skuId);

        Map<String, Object> usage = SqlRows.toCamelCase(
                        jdbc.sql("SELECT * FROM dbo.sku_usage(:id::uuid)")
                                .param("id", skuId).query().listOfRows())
                .getFirst();

        List<String> where = new ArrayList<>();
        addUsage(where, usage.get("receipts"), "поступлениях");
        addUsage(where, usage.get("orders"), "заказах");
        addUsage(where, usage.get("transfers"), "перемещениях");
        addUsage(where, usage.get("stockRows"), "остатках");
        addUsage(where, usage.get("packagings"), "упаковках");
        if (!where.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Товар используется в " + String.join(", ", where)
                            + ". Его можно перевести в архив, но не удалить");
        }
        jdbc.sql("DELETE FROM dbo.sku WHERE sku_id = :id::uuid").param("id", skuId).update();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> card(String skuId) {
        depositorScope.apply();
        List<Map<String, Object>> rows = jdbc
                .sql("SELECT * FROM dbo.v_sku_full WHERE sku_id = :id::uuid")
                .param("id", skuId)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            throw notFound();
        }
        Map<String, Object> card = new LinkedHashMap<>(asText(SqlRows.toCamelCase(rows)).getFirst());
        card.put("packagings", packagings(skuId));
        return card;
    }

    /* ======================= Упаковки ======================= */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> packagings(String skuId) {
        return asText(SqlRows.toCamelCase(jdbc.sql("""
                        SELECT * FROM dbo.v_product_packaging
                        WHERE sku_id = :id::uuid
                        ORDER BY coefficient
                        """)
                .param("id", skuId)
                .query()
                .listOfRows()));
    }

    /**
     * Идентификаторы — строками.
     * <p>
     * Из базы они приходят как {@code java.util.UUID}. В JSON разницы не видно,
     * а в Java видно: вызывающий код получает идентификатор и тут же
     * подставляет его в адрес следующего запроса, то есть обращается с ним
     * как со строкой. Приводим здесь один раз, чтобы это было правдой.
     */
    private static List<Map<String, Object>> asText(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            row.replaceAll((key, value) -> value instanceof java.util.UUID ? value.toString() : value);
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> createPackaging(String skuId, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        requireSkuExists(skuId);

        Map<String, Object> clean = filter(values, SkuColumns.PACKAGING);
        requireFields(clean, SkuColumns.PACKAGING_REQUIRED, "упаковку");

        List<String> columns = new ArrayList<>(clean.keySet());
        String names = String.join(", ", columns);
        String params = columns.stream()
                .map(c -> ":" + c + "::" + SkuColumns.PACKAGING.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("""
                        INSERT INTO dbo.product_packaging (depositor_id, sku_id, %s)
                        VALUES (dbo.current_depositor_id(), :__skuId::uuid, %s)
                        """.formatted(names, params))
                .param("__skuId", skuId);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        try {
            spec.update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Упаковка с кодом «" + clean.get("packaging_code") + "» у этого товара уже есть");
        }
        return packagings(skuId);
    }

    @Transactional
    public List<Map<String, Object>> updatePackaging(String packagingId, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        String skuId = skuOfPackaging(packagingId);

        Map<String, Object> clean = filter(values, SkuColumns.PACKAGING);
        if (clean.isEmpty()) {
            return packagings(skuId);
        }
        String assignments = clean.keySet().stream()
                .map(c -> c + " = :" + c + "::" + SkuColumns.PACKAGING.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("UPDATE dbo.product_packaging SET %s WHERE packaging_id = :__id::uuid"
                        .formatted(assignments))
                .param("__id", packagingId);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        try {
            spec.update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Упаковка с таким кодом у этого товара уже есть");
        }
        return packagings(skuId);
    }

    /** Упаковка по умолчанию — та, в которой товар заказывают (order_unit_id). */
    @Transactional
    public List<Map<String, Object>> setDefaultPackaging(String packagingId) {
        depositorScope.apply();
        requireEditor();
        String skuId = skuOfPackaging(packagingId);
        jdbc.sql("""
                        UPDATE dbo.sku SET order_unit_id = :packagingId::uuid,
                               updated_by = :author, updated_at = now()
                        WHERE sku_id = :skuId::uuid
                        """)
                .param("packagingId", packagingId)
                .param("author", author())
                .param("skuId", skuId)
                .update();
        return packagings(skuId);
    }

    @Transactional
    public List<Map<String, Object>> deletePackaging(String packagingId) {
        depositorScope.apply();
        requireEditor();
        String skuId = skuOfPackaging(packagingId);

        // Упаковка, выбранная единицей заказа, сначала перестаёт быть ею:
        // иначе внешний ключ не даст удалить, а человек увидит ошибку базы.
        jdbc.sql("""
                        UPDATE dbo.sku SET order_unit_id = NULL
                        WHERE sku_id = :skuId::uuid AND order_unit_id = :packagingId::uuid
                        """)
                .param("skuId", skuId)
                .param("packagingId", packagingId)
                .update();
        jdbc.sql("DELETE FROM dbo.product_packaging WHERE packaging_id = :id::uuid")
                .param("id", packagingId)
                .update();
        return packagings(skuId);
    }

    /* ======================= Вспомогательное ======================= */

    /** Справочники для выпадающих списков карточки: единицы, валюты, классы. */
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> dictionaries() {
        depositorScope.apply();
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.sql("""
                        SELECT kind, id, name FROM dbo.v_sku_dictionaries
                        ORDER BY kind, sort_order NULLS LAST, name
                        """).query().listOfRows()) {
            out.computeIfAbsent(String.valueOf(row.get("kind")), k -> new ArrayList<>())
                    .add(Map.of("id", row.get("id"), "name", row.get("name")));
        }
        // группы и типы товара заведены по поклажедателям — читаются под RLS
        out.put("group", SqlRows.toCamelCase(jdbc.sql("""
                        SELECT id::text AS id, name FROM dbo.product_group
                        WHERE is_active ORDER BY sort_order NULLS LAST, name
                        """).query().listOfRows()));
        out.put("type", SqlRows.toCamelCase(jdbc.sql("""
                        SELECT id::text AS id, name FROM dbo.product_type
                        WHERE is_active ORDER BY sort_order NULLS LAST, name
                        """).query().listOfRows()));
        return out;
    }

    private static void addUsage(List<String> where, Object count, String what) {
        if (count != null && ((Number) count).longValue() > 0) {
            where.add(what + " (" + count + ")");
        }
    }

    private Map<String, Object> filter(Map<String, Object> values, Map<String, String> allowed) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (values == null) {
            return clean;
        }
        values.forEach((key, value) -> {
            if (allowed.containsKey(key)) {
                // пустая строка из формы означает «нет значения», а не текст ""
                clean.put(key, value instanceof String s && s.isBlank() ? null : value);
            }
        });
        return clean;
    }

    private static void requireFields(Map<String, Object> clean, java.util.Set<String> required,
                                      String what) {
        for (String field : required) {
            if (!clean.containsKey(field) || isBlank(clean.get(field))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Чтобы создать " + what + ", заполните поле «" + field + "»");
            }
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private void requireSkuExists(String skuId) {
        boolean exists = Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.sku WHERE sku_id = :id::uuid)")
                .param("id", skuId)
                .query(Boolean.class)
                .single());
        if (!exists) {
            throw notFound();
        }
    }

    private String skuOfPackaging(String packagingId) {
        return jdbc.sql("SELECT sku_id::text FROM dbo.product_packaging WHERE packaging_id = :id::uuid")
                .param("id", packagingId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Упаковка не найдена"));
    }

    /**
     * Кто может править справочник. Складской сотрудник ведёт склад,
     * а не номенклатуру клиента; разработчик в портале только смотрит.
     */
    private void requireEditor() {
        String role = jdbc.sql("SELECT dbo.auth_role_by_login(:login::text)")
                .param("login", DepositorContext.userLogin())
                .query(String.class)
                .optional()
                .orElse("");
        if (!List.of("depositor", "manager", "admin").contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Справочник товаров доступен поклажедателю, менеджеру и администратору");
        }
        currentUser.requireUserId();   // заодно убеждаемся, что учётка активна
    }

    /** В аудит товара пишется логин: колонка текстовая и читается человеком. */
    private String author() {
        return DepositorContext.userLogin();
    }

    private static ResponseStatusException notFound() {
        // чужой товар неотличим от несуществующего — так же, как у документов
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден");
    }

    private static ResponseStatusException duplicateCode(Object code) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Товар с кодом «" + code + "» у этого поклажедателя уже есть");
    }
}
