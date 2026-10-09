package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.DepositorContext;
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
 * Единицы измерения — вкладка раздела «Справочники».
 *
 * <p>Таблица {@code enum_uom} одна на весь портал и не под RLS: «шт»
 * означает штуку для всех клиентов, и у неё нет владельца. Из этого
 * следует всё остальное в этом классе:
 * <ul>
 *   <li>читать список вправе любой вошедший — без единиц не заполнить
 *       ни товар, ни документ;</li>
 *   <li>править вправе только администратор портала: иначе один клиент
 *       переименует единицу всем остальным, и спросить будет некого;</li>
 *   <li>используемую единицу нельзя удалить — {@code unit_usage} считает
 *       ссылки по ВСЕМ поклажедателям (SECURITY DEFINER, иначе под RLS
 *       администратор увидел бы ноль и решил, что единица свободна).
 *       Рабочий способ вывести единицу из обращения — снять галочку
 *       «Действует», как в остальных справочниках портала.</li>
 * </ul>
 *
 * <p>Идентификатор — не автонумерация: номера пришли из WMS. Новым
 * строкам его выдаёт {@code next_enum_id('enum_uom')}, работающая
 * по белому списку таблиц.
 */
@Service
public class UnitService {

    private final JdbcClient jdbc;

    public UnitService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Строка справочника.
     *
     * @param mkeiCode  код МКЕИ — единственное, что связывает единицу
     *                  с внешними системами, поэтому он обязателен
     * @param symbol    краткое обозначение: «шт», «кг»
     * @param name      наименование: «Штука»
     */
    public record Unit(String mkeiCode, String symbol, String name,
                       Integer sortOrder, Boolean isActive) {
    }

    /**
     * Список единиц и признак «этот пользователь вправе их править» —
     * экрану нужно знать, показывать кнопки или только список.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("units", rows());
        out.put("canEdit", isAdmin());
        return out;
    }

    @Transactional
    public Map<String, Object> save(Integer id, Unit unit) {
        requireAdmin();
        if (isBlank(unit.symbol()) || isBlank(unit.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заполните обозначение и наименование единицы измерения");
        }
        if (isBlank(unit.mkeiCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заполните код МКЕИ — по нему единицу опознают внешние системы");
        }
        requireFits("mkei_code", "Код МКЕИ", unit.mkeiCode());
        requireFits("symbol", "Обозначение", unit.symbol());
        requireFits("name", "Наименование", unit.name());
        requireUnique(id, unit);
        try {
            if (id == null) {
                jdbc.sql("""
                                INSERT INTO dbo.enum_uom (id, mkei_code, symbol, name, sort_order, is_active)
                                VALUES (dbo.next_enum_id('enum_uom'::text), :mkei::varchar,
                                        :symbol::varchar, :name::varchar, :sortOrder,
                                        COALESCE(:isActive, true))
                                """)
                        .param("mkei", text(unit.mkeiCode()))
                        .param("symbol", text(unit.symbol()))
                        .param("name", text(unit.name()))
                        .param("sortOrder", unit.sortOrder())
                        .param("isActive", unit.isActive())
                        .update();
            } else {
                requireUnit(id);
                jdbc.sql("""
                                UPDATE dbo.enum_uom
                                   SET mkei_code = :mkei::varchar,
                                       symbol = :symbol::varchar,
                                       name = :name::varchar,
                                       sort_order = :sortOrder,
                                       is_active = COALESCE(:isActive, true)
                                 WHERE id = :id
                                """)
                        .param("mkei", text(unit.mkeiCode()))
                        .param("symbol", text(unit.symbol()))
                        .param("name", text(unit.name()))
                        .param("sortOrder", unit.sortOrder())
                        .param("isActive", unit.isActive())
                        .param("id", id)
                        .update();
            }
        } catch (DuplicateKeyException e) {
            // Сюда попадаем, только если строку завели одновременно
            // в двух сеансах: проверка выше уже прошла, а ограничение
            // в базе (V39) всё равно сработало.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Такая единица измерения уже есть в справочнике");
        }
        return list();
    }

    /** Снять или вернуть галочку «Действует». */
    @Transactional
    public Map<String, Object> setActive(int id, boolean active) {
        requireAdmin();
        requireUnit(id);
        jdbc.sql("UPDATE dbo.enum_uom SET is_active = :active WHERE id = :id")
                .param("active", active)
                .param("id", id)
                .update();
        return list();
    }

    @Transactional
    public Map<String, Object> delete(int id) {
        requireAdmin();
        requireUnit(id);

        Map<String, Object> usage = jdbc.sql("SELECT * FROM dbo.unit_usage(:id::smallint)")
                .param("id", id)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(Map.of());

        List<String> where = new ArrayList<>();
        long skuCount = count(usage.get("sku_count"));
        long stockCount = count(usage.get("stock_count"));
        if (skuCount > 0) {
            where.add("в товарах (" + skuCount + ")");
        }
        if (stockCount > 0) {
            where.add("в запасах (" + stockCount + ")");
        }
        if (!where.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Единица измерения используется " + String.join(", ", where)
                            + ". Её можно отключить, но не удалить");
        }

        jdbc.sql("DELETE FROM dbo.enum_uom WHERE id = :id")
                .param("id", id)
                .update();
        return list();
    }

    /* ======================= Вспомогательное ======================= */

    private List<Map<String, Object>> rows() {
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.v_units")
                .query()
                .listOfRows());
    }

    /**
     * Проверяет длину значения до записи.
     *
     * <p>Колонки короткие и по делу: код МКЕИ — ровно три знака,
     * обозначение — десять. Без этой проверки слишком длинное значение
     * падало бы на вставке ошибкой базы «значение не умещается в тип
     * character varying(3)» — человеку она не говорит ни какое поле
     * виновато, ни сколько знаков можно. На этом споткнулся наш же тест.
     *
     * <p>Ограничение читается из базы, а не вписано числом: иначе
     * проверка и схема однажды разойдутся.
     */
    private void requireFits(String column, String label, String value) {
        if (value == null) {
            return;
        }
        Integer limit = jdbc.sql("""
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = 'enum_uom'
                          AND column_name = :column
                        """)
                .param("column", column)
                .query(Integer.class)
                .optional()
                .orElse(null);
        if (limit != null && value.trim().length() > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + ": не больше " + limit + " знаков");
        }
    }

    /**
     * Ни обозначения, ни кода МКЕИ не должно быть дважды.
     *
     * <p>Обозначение сравнивается без учёта регистра: «шт» и «ШТ» для
     * человека одно и то же, и две такие строки в карточке товара
     * означают выбор между одинаковыми вариантами. Ровно так же
     * устроено ограничение в базе (V39) — проверка здесь нужна, чтобы
     * сказать, что именно повторяется.
     *
     * <p>При правке своя же строка из проверки исключается, иначе
     * единицу нельзя было бы сохранить, не меняя обозначения.
     */
    private void requireUnique(Integer id, Unit unit) {
        for (Map<String, Object> row : jdbc.sql("""
                        SELECT symbol, mkei_code
                        FROM dbo.enum_uom
                        WHERE (lower(symbol) = lower(:symbol::varchar)
                               OR mkei_code = :mkei::varchar)
                          AND (:id::int IS NULL OR id <> :id::int)
                        """)
                .param("symbol", text(unit.symbol()))
                .param("mkei", text(unit.mkeiCode()))
                .param("id", id)
                .query()
                .listOfRows()) {
            if (String.valueOf(row.get("symbol")).equalsIgnoreCase(text(unit.symbol()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Единица измерения «" + text(unit.symbol())
                                + "» уже есть в справочнике");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Код МКЕИ " + text(unit.mkeiCode()) + " уже занят единицей «"
                            + row.get("symbol") + "»");
        }
    }

    private void requireUnit(int id) {
        boolean exists = Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.enum_uom WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single());
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Единица измерения не найдена");
        }
    }

    /**
     * Разработчик в портале только смотрит — поэтому в правке он
     * не участвует, в отличие от проверки в администрировании.
     */
    private boolean isAdmin() {
        return "admin".equals(role());
    }

    private void requireAdmin() {
        if (!isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Единицы измерения общие для всех поклажедателей,"
                            + " их ведёт администратор портала");
        }
    }

    private String role() {
        return jdbc.sql("SELECT dbo.auth_role_by_login(:login::text)")
                .param("login", DepositorContext.userLogin())
                .query(String.class)
                .optional()
                .orElse("");
    }

    private static long count(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String text(String s) {
        return s == null ? null : s.trim();
    }
}
