package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.MsgRef;
import com.ahlers.portal3pl.common.PortalException;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorContext;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ведение справочников контрагентов: поставщики, грузополучатели и адреса
 * доставки.
 *
 * <p>Правила те же, что у товаров (V22): правят поклажедатель, менеджер и
 * администратор; убрать из работы — архив, удаление только для тех, кто
 * нигде не задействован; изоляцию обеспечивает RLS, `depositor_id`
 * подставляется базой из контекста.
 *
 * <p>Адреса доставки намеренно не отдельный справочник, а часть карточки
 * грузополучателя: адрес без грузополучателя не имеет смысла, а при выборе
 * в заказе человек сначала называет кому, потом куда. Основной адрес
 * переключается одной функцией базы — два запроса подряд оставили бы между
 * собой мгновение с двумя «основными», и ограничение в базе отказало бы.
 */
@Service
public class PartnerWriteService {

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final CurrentUser currentUser;

    public PartnerWriteService(JdbcClient jdbc, DepositorScope depositorScope, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.currentUser = currentUser;
    }

    /* ======================= Контрагент ======================= */

    @Transactional(readOnly = true)
    public Map<String, Object> card(PartnerKind kind, String id) {
        depositorScope.apply();
        List<Map<String, Object>> rows = jdbc
                .sql("SELECT * FROM dbo.%s WHERE %s = :id::uuid".formatted(kind.view(), kind.key()))
                .param("id", id)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            throw notFound(kind);
        }
        Map<String, Object> card = new LinkedHashMap<>(asText(SqlRows.toCamelCase(rows)).getFirst());
        if (kind == PartnerKind.CONSIGNEE) {
            card.put("addresses", addresses(id));
        }
        return card;
    }

    @Transactional
    public Map<String, Object> create(PartnerKind kind, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        Map<String, Object> clean = filter(values);
        requireFields(clean, kind);

        List<String> columns = new ArrayList<>(clean.keySet());
        String names = String.join(", ", columns);
        String params = columns.stream()
                .map(c -> ":" + c + "::" + PartnerKind.COLUMNS.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("""
                INSERT INTO dbo.%s (depositor_id, %s, is_active)
                VALUES (dbo.current_depositor_id(), %s, true)
                RETURNING %s::text
                """.formatted(kind.table(), names, params, kind.key()));
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        String id;
        try {
            id = spec.query(String.class).single();
        } catch (DuplicateKeyException e) {
            throw duplicateCode(kind, clean.get("code"));
        }
        return card(kind, id);
    }

    @Transactional
    public Map<String, Object> update(PartnerKind kind, String id, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        requireExists(kind, id);

        Map<String, Object> clean = filter(values);
        if (clean.isEmpty()) {
            return card(kind, id);
        }
        // Обязательные поля проверяем только если их прислали: экран может
        // сохранять карточку частями, и пустое «наименование» в такой
        // отправке означало бы стирание нужного значения.
        for (String field : PartnerKind.REQUIRED) {
            if (clean.containsKey(field) && isBlank(clean.get(field))) {
                throw Fail.badRequest("error.field.empty", "field", field);
            }
        }
        String assignments = clean.keySet().stream()
                .map(c -> c + " = :" + c + "::" + PartnerKind.COLUMNS.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("UPDATE dbo.%s SET %s WHERE %s = :__id::uuid"
                        .formatted(kind.table(), assignments, kind.key()))
                .param("__id", id);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        try {
            spec.update();
        } catch (DuplicateKeyException e) {
            throw duplicateCode(kind, clean.get("code"));
        }
        return card(kind, id);
    }

    @Transactional
    public Map<String, Object> setActive(PartnerKind kind, String id, boolean active) {
        depositorScope.apply();
        requireEditor();
        requireExists(kind, id);
        jdbc.sql("UPDATE dbo.%s SET is_active = :active WHERE %s = :id::uuid"
                        .formatted(kind.table(), kind.key()))
                .param("active", active)
                .param("id", id)
                .update();
        return card(kind, id);
    }

    /**
     * Удаление — только для случайно заведённых карточек. Если контрагент
     * указан в документах, отказываем и объясняем, где именно: «нельзя»
     * без объяснения заставляет человека гадать.
     */
    @Transactional
    public void delete(PartnerKind kind, String id) {
        depositorScope.apply();
        requireEditor();
        requireExists(kind, id);

        Map<String, Object> usage = SqlRows.toCamelCase(
                        jdbc.sql("SELECT * FROM dbo.%s(:id::uuid)".formatted(kind.usageFunction()))
                                .param("id", id).query().listOfRows())
                .getFirst();

        List<MsgRef> where = new ArrayList<>();
        kind.usageLabels().forEach((column, key) -> {
            Object count = usage.get(column);
            if (count != null && ((Number) count).longValue() > 0) {
                where.add(MsgRef.of(key, "count", count));
            }
        });
        if (!where.isEmpty()) {
            throw Fail.conflict(kind.error("in_use"), "where", where);
        }
        if (kind == PartnerKind.CONSIGNEE) {
            // адреса принадлежат карточке и живут только вместе с ней
            jdbc.sql("DELETE FROM dbo.delivery_point WHERE consignee_id = :id::uuid")
                    .param("id", id)
                    .update();
        }
        jdbc.sql("DELETE FROM dbo.%s WHERE %s = :id::uuid".formatted(kind.table(), kind.key()))
                .param("id", id)
                .update();
    }

    /* ======================= Адреса доставки ======================= */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> addresses(String consigneeId) {
        return asText(SqlRows.toCamelCase(jdbc.sql("""
                        SELECT * FROM dbo.v_delivery_points
                        WHERE consignee_id = :id::uuid
                        ORDER BY is_default DESC, sort_order NULLS LAST, address
                        """)
                .param("id", consigneeId)
                .query()
                .listOfRows()));
    }

    @Transactional
    public List<Map<String, Object>> addAddress(String consigneeId, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        requireExists(PartnerKind.CONSIGNEE, consigneeId);

        String address = text(values.get("address"));
        if (address == null) {
            throw Fail.badRequest("error.address.required");
        }
        // Первый адрес сразу становится основным: иначе у грузополучателя
        // с единственным адресом заказ подставлять нечего.
        boolean first = Boolean.TRUE.equals(jdbc
                .sql("SELECT NOT EXISTS (SELECT 1 FROM dbo.delivery_point WHERE consignee_id = :id::uuid)")
                .param("id", consigneeId)
                .query(Boolean.class)
                .single());
        try {
            jdbc.sql("""
                            INSERT INTO dbo.delivery_point (consignee_id, address, is_default, sort_order)
                            VALUES (:consignee::uuid, :address::varchar, :isDefault, :sortOrder)
                            """)
                    .param("consignee", consigneeId)
                    .param("address", address)
                    .param("isDefault", first)
                    .param("sortOrder", intOrNull(values.get("sortOrder")))
                    .update();
        } catch (DuplicateKeyException e) {
            throw Fail.conflict("error.address.duplicate");
        }
        return addresses(consigneeId);
    }

    @Transactional
    public List<Map<String, Object>> updateAddress(String addressId, Map<String, Object> values) {
        depositorScope.apply();
        requireEditor();
        String consigneeId = consigneeOfAddress(addressId);

        String address = text(values.get("address"));
        if (address == null) {
            throw Fail.badRequest("error.address.required");
        }
        try {
            jdbc.sql("""
                            UPDATE dbo.delivery_point
                               SET address = :address::varchar, sort_order = :sortOrder
                             WHERE address_id = :id::uuid
                            """)
                    .param("address", address)
                    .param("sortOrder", intOrNull(values.get("sortOrder")))
                    .param("id", addressId)
                    .update();
        } catch (DuplicateKeyException e) {
            throw Fail.conflict("error.address.duplicate");
        }
        return addresses(consigneeId);
    }

    /** Основной адрес один: прежний снимается и новый ставится одной операцией. */
    @Transactional
    public List<Map<String, Object>> setDefaultAddress(String addressId) {
        depositorScope.apply();
        requireEditor();
        String consigneeId = consigneeOfAddress(addressId);
        jdbc.sql("SELECT dbo.set_default_delivery_point(:id::uuid)")
                .param("id", addressId)
                .query()
                .listOfRows();
        return addresses(consigneeId);
    }

    @Transactional
    public List<Map<String, Object>> deleteAddress(String addressId) {
        depositorScope.apply();
        requireEditor();
        String consigneeId = consigneeOfAddress(addressId);

        Long orders = jdbc.sql("SELECT orders FROM dbo.delivery_point_usage(:id::uuid)")
                .param("id", addressId)
                .query(Long.class)
                .single();
        if (orders != null && orders > 0) {
            throw Fail.conflict("error.address.in_use", "count", orders);
        }
        boolean wasDefault = Boolean.TRUE.equals(jdbc
                .sql("SELECT COALESCE(is_default, false) FROM dbo.delivery_point WHERE address_id = :id::uuid")
                .param("id", addressId)
                .query(Boolean.class)
                .single());
        jdbc.sql("DELETE FROM dbo.delivery_point WHERE address_id = :id::uuid")
                .param("id", addressId)
                .update();
        if (wasDefault) {
            // Оставить грузополучателя без основного адреса значит сломать
            // подстановку в заказе — назначаем первый из оставшихся.
            jdbc.sql("""
                            UPDATE dbo.delivery_point SET is_default = true
                             WHERE address_id = (SELECT address_id FROM dbo.delivery_point
                                                  WHERE consignee_id = :id::uuid
                                                  ORDER BY sort_order NULLS LAST, address
                                                  LIMIT 1)
                            """)
                    .param("id", consigneeId)
                    .update();
        }
        return addresses(consigneeId);
    }

    /* ======================= Вспомогательное ======================= */

    private Map<String, Object> filter(Map<String, Object> values) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (values == null) {
            return clean;
        }
        values.forEach((key, value) -> {
            if (PartnerKind.COLUMNS.containsKey(key)) {
                // пустая строка из формы означает «нет значения», а не текст ""
                clean.put(key, value instanceof String s && s.isBlank() ? null : value);
            }
        });
        return clean;
    }

    private static void requireFields(Map<String, Object> clean, PartnerKind kind) {
        for (String field : PartnerKind.REQUIRED) {
            if (!clean.containsKey(field) || isBlank(clean.get(field))) {
                throw Fail.badRequest(kind.error("create_field_required"), "field", field);
            }
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static String text(Object value) {
        return isBlank(value) ? null : String.valueOf(value).trim();
    }

    private static Integer intOrNull(Object value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw Fail.badRequest("error.sort_order.not_a_number");
        }
    }

    private void requireExists(PartnerKind kind, String id) {
        boolean exists = Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.%s WHERE %s = :id::uuid)"
                        .formatted(kind.table(), kind.key()))
                .param("id", id)
                .query(Boolean.class)
                .single());
        if (!exists) {
            throw notFound(kind);
        }
    }

    private String consigneeOfAddress(String addressId) {
        return jdbc.sql("SELECT consignee_id::text FROM dbo.delivery_point WHERE address_id = :id::uuid")
                .param("id", addressId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> Fail.notFound("error.address.not_found"));
    }

    /** Кто ведёт справочники. Кладовщик и разработчик — только смотрят. */
    private void requireEditor() {
        String role = jdbc.sql("SELECT dbo.auth_role_by_login(:login::text)")
                .param("login", DepositorContext.userLogin())
                .query(String.class)
                .optional()
                .orElse("");
        if (!List.of("depositor", "manager", "admin").contains(role)) {
            throw Fail.forbidden("error.access.partners");
        }
        currentUser.requireUserId();   // заодно убеждаемся, что учётка активна
    }

    /** Идентификаторы — строками (см. SkuWriteService: из базы они приходят как UUID). */
    private static List<Map<String, Object>> asText(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            row.replaceAll((key, value) -> value instanceof UUID ? value.toString() : value);
        }
        return rows;
    }

    private static PortalException notFound(PartnerKind kind) {
        // чужая карточка неотличима от несуществующей — так же, как у документов
        return Fail.notFound(kind.error("not_found"));
    }

    private static PortalException duplicateCode(PartnerKind kind, Object code) {
        return Fail.conflict(kind.error("code_taken"), "code", code);
    }
}
