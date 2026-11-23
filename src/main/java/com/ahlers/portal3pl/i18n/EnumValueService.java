package com.ahlers.portal3pl.i18n;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.MsgRef;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Группа администрирования «Перечисления» (V95): значения всех
 * перечислений и их переводы.
 *
 * <p>Одна служба на все двадцать перечислений: какая таблица, какая
 * колонка кода и есть ли второй код (обозначение единицы, буквенный
 * код валюты) — записано в {@code enum_registry}. Прежде у единиц,
 * типов заказов и типов поступлений были свои службы и свои вкладки,
 * а остальные перечисления править было негде.
 *
 * <p>Правила:
 * <ul>
 *   <li>добавлять и править можно любое перечисление;</li>
 *   <li>удалить можно только значение, на которое ничего не ссылается
 *       ({@code enum_value_usage()} — по всем внешним ключам сразу);
 *       используемое отключают;</li>
 *   <li>код используемого значения не меняется: записи ссылаются
 *       на него, а WMS знает значение по коду;</li>
 *   <li>значения из {@code enum_system_value} — те, чей код записан
 *       в программе (черновик, роль «admin»…), — не удаляются,
 *       не отключаются и кода не меняют. Имя и переводы править можно.</li>
 * </ul>
 *
 * <p>Значение и его переводы сохраняются одной транзакцией: окно
 * показывает их вместе, и половина сохранения была бы хуже никакого.
 *
 * <p>Право — администратора портала ({@link AdminUserService#requireAdmin}).
 */
@Service
public class EnumValueService {

    /**
     * Где используется значение — по таблице, которая на него ссылается.
     * Ключ сообщения подставляется в «Значение используется в …».
     */
    private static final Map<String, String> USAGE_KEYS = Map.ofEntries(
            Map.entry("sku", "error.usage.items"),
            Map.entry("stock", "error.usage.stock"),
            Map.entry("receipts", "error.usage.receipts"),
            Map.entry("orders", "error.usage.orders"),
            Map.entry("transfers", "error.usage.transfers"),
            Map.entry("receipt_details", "error.usage.document_lines"),
            Map.entry("order_details", "error.usage.document_lines"),
            Map.entry("transfer_details", "error.usage.document_lines"),
            Map.entry("product_packaging", "error.usage.packagings"),
            Map.entry("app_user", "error.usage.users"),
            Map.entry("invoice", "error.usage.invoices"),
            Map.entry("excel_import", "error.usage.excel_imports"),
            Map.entry("sync_run", "error.usage.sync_runs"),
            Map.entry("enum_status_scope", "error.usage.settings"),
            Map.entry("table_column", "error.usage.settings"),
            Map.entry("depositor_field_label", "error.usage.settings"));

    private static final String USAGE_OTHER = "error.usage.other";

    private final JdbcClient jdbc;
    private final AdminUserService users;

    public EnumValueService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** Перевод значения на один язык. */
    public record Translation(String name, String symbol) {
    }

    /**
     * Значение, как его заполняют в окне.
     *
     * @param code         код ({@code code_column} из реестра)
     * @param name         основное (английское) имя
     * @param extra        второй код: обозначение единицы, буквенный код валюты
     * @param translations переводы по кодам языков
     * @param flag         признак «да/нет» из реестра (у статуса — «можно править
     *                     транспорт», V150); {@code null} — не менять
     */
    public record EnumValue(String code, String name, String extra, Integer sortOrder,
                            Boolean isActive, Map<String, Translation> translations, Boolean flag) {

        public EnumValue(String code, String name, String extra, Integer sortOrder,
                         Boolean isActive, Map<String, Translation> translations) {
            this(code, name, extra, sortOrder, isActive, translations, null);
        }
    }

    /** Описание перечисления из реестра. */
    private record Registry(String enumKey, String codeColumn, boolean hasSymbol,
                            String textKey, String codePattern, String extraColumn,
                            String extraPattern, String extraLabelKey,
                            String flagColumn, String flagLabelKey, String flagHintKey) {
    }

    /* ============================== чтение ============================== */

    /**
     * Перечисления, их значения и переводы на включённые языки.
     *
     * @return {@code {enums, locales, rows}}; у строки — код, основное имя,
     *         второй код, порядок, «действует», «системное», сколько записей
     *         на неё ссылается и переводы {@code {ru: {name, symbol}}}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(PortalUser actor) {
        users.requireAdmin(actor);
        List<Registry> registry = registry();
        List<Map<String, Object>> locales = locales();
        Map<String, Map<String, Long>> usage = usage();
        Set<String> system = systemValues();
        Map<String, Map<String, Object>> texts = texts();

        List<Map<String, Object>> enums = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Registry r : registry) {
            List<Map<String, Object>> values = values(r);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("enumKey", r.enumKey());
            e.put("textKey", r.textKey());
            e.put("hasSymbol", r.hasSymbol());
            e.put("extraLabelKey", r.extraColumn() == null ? null : r.extraLabelKey());
            e.put("codeExample", example(values, "code"));
            e.put("extraExample", r.extraColumn() == null ? null : example(values, "extra"));
            e.put("flagLabelKey", r.flagColumn() == null ? null : r.flagLabelKey());
            e.put("flagHintKey", r.flagColumn() == null ? null : r.flagHintKey());
            enums.add(e);

            for (Map<String, Object> v : values) {
                String id = String.valueOf(v.get("value_id"));
                String key = r.enumKey() + "|" + id;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("enumKey", r.enumKey());
                row.put("valueId", id);
                row.put("code", v.get("code"));
                row.put("name", v.get("name"));
                row.put("extra", v.get("extra"));
                row.put("flag", v.get("flag"));
                row.put("sortOrder", v.get("sort_order"));
                row.put("isActive", v.get("is_active"));
                row.put("isSystem", system.contains(r.enumKey() + "|" + v.get("code")));
                row.put("usage", usage.getOrDefault(key, Map.of()).values().stream()
                        .mapToLong(Long::longValue).sum());
                row.put("values", texts.getOrDefault(key, Map.of()));
                rows.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enums", enums);
        out.put("locales", locales);
        out.put("rows", rows);
        return out;
    }

    /* ============================== запись ============================== */

    /**
     * Создать ({@code valueId == null}) или изменить значение вместе
     * с переводами.
     */
    @Transactional
    public Map<String, Object> save(PortalUser actor, String enumKey, String valueId, EnumValue v) {
        users.requireAdmin(actor);
        Registry r = registryOf(enumKey);

        String code = trim(v.code());
        String name = trim(v.name());
        String extra = trim(v.extra());
        if (code == null) {
            throw Fail.badRequest("error.enum.code_required");
        }
        if (name == null) {
            throw Fail.badRequest("error.enum.name_required");
        }
        if (r.extraColumn() != null && extra == null) {
            throw Fail.badRequest("error.enum.extra_required", "field", MsgRef.of(r.extraLabelKey()));
        }
        requireFits(r.enumKey(), r.codeColumn(), "admin.column.enum_code", code);
        requireFits(r.enumKey(), "name", "admin.label.base_name", name);
        if (r.extraColumn() != null) {
            requireFits(r.enumKey(), r.extraColumn(), r.extraLabelKey(), extra);
        }
        List<Map<String, Object>> existing = values(r);
        requirePattern(r.codePattern(), code, example(existing, "code"));
        if (r.extraColumn() != null) {
            requirePattern(r.extraPattern(), extra, example(existing, "extra"));
        }

        if (valueId != null) {
            Map<String, Object> current = existing.stream()
                    .filter(x -> valueId.equals(String.valueOf(x.get("value_id"))))
                    .findFirst()
                    .orElseThrow(() -> Fail.notFound("error.enum.value_unknown", "value", valueId));
            String oldCode = String.valueOf(current.get("code"));
            boolean system = systemValues().contains(r.enumKey() + "|" + oldCode);
            if (!oldCode.equals(code) && (system || usageOf(r.enumKey(), valueId) > 0)) {
                throw Fail.conflict("error.enum.code_locked");
            }
            if (system && Boolean.FALSE.equals(v.isActive())) {
                throw Fail.conflict("error.enum.system_value");
            }
        }
        requireUnique(existing, valueId, "code", code);
        if (r.extraColumn() != null) {
            requireUnique(existing, valueId, "extra", extra);
        }
        Map<String, Translation> translations = v.translations() == null ? Map.of() : v.translations();
        Set<String> enabled = enabledLocales();
        for (Map.Entry<String, Translation> t : translations.entrySet()) {
            if (!enabled.contains(t.getKey())) {
                throw Fail.notFound("error.locale.unknown", "code", t.getKey());
            }
            Translation tr = t.getValue() == null ? new Translation(null, null) : t.getValue();
            requireFits("enum_text", "name", "admin.label.base_name", trim(tr.name()));
            requireFits("enum_text", "symbol", "admin.label.enum_symbol", trim(tr.symbol()));
        }

        String id;
        try {
            if (valueId == null) {
                id = String.valueOf(jdbc.sql("SELECT dbo.next_enum_id(:table::text)")
                        .param("table", r.enumKey())
                        .query(Integer.class)
                        .single());
                jdbc.sql(("INSERT INTO dbo.%s (id, %s, name, sort_order, is_active%s%s)"
                                + " VALUES (:id::int, :code::varchar, :name::varchar, :sortOrder,"
                                + " COALESCE(:isActive, true)%s%s)")
                                .formatted(r.enumKey(), r.codeColumn(),
                                        r.extraColumn() == null ? "" : ", " + r.extraColumn(),
                                        r.flagColumn() == null ? "" : ", " + r.flagColumn(),
                                        r.extraColumn() == null ? "" : ", :extra::varchar",
                                        r.flagColumn() == null ? "" : ", COALESCE(:flag, false)"))
                        .param("id", Integer.parseInt(id))
                        .param("code", code)
                        .param("name", name)
                        .param("sortOrder", v.sortOrder())
                        .param("isActive", v.isActive())
                        .param("extra", extra)
                        .param("flag", v.flag())
                        .update();
            } else {
                id = valueId;
                jdbc.sql(("UPDATE dbo.%s SET %s = :code::varchar, name = :name::varchar,"
                                + " sort_order = :sortOrder, is_active = COALESCE(:isActive, true)%s%s"
                                + " WHERE id::text = :id")
                                .formatted(r.enumKey(), r.codeColumn(),
                                        r.extraColumn() == null ? ""
                                                : ", " + r.extraColumn() + " = :extra::varchar",
                                        r.flagColumn() == null ? ""
                                                : ", " + r.flagColumn() + " = COALESCE(:flag, " + r.flagColumn() + ")"))
                        .param("code", code)
                        .param("name", name)
                        .param("sortOrder", v.sortOrder())
                        .param("isActive", v.isActive())
                        .param("extra", extra)
                        .param("flag", v.flag())
                        .param("id", id)
                        .update();
            }
        } catch (DuplicateKeyException e) {
            // Значение с тем же кодом завели одновременно в другом сеансе:
            // проверка выше прошла, а ограничение базы сработало.
            throw Fail.conflict("error.enum.code_taken", "code", code);
        }

        for (Map.Entry<String, Translation> t : translations.entrySet()) {
            Translation tr = t.getValue() == null ? new Translation(null, null) : t.getValue();
            jdbc.sql("SELECT dbo.enum_text_save(:enum::text, :id::text, :locale::text,"
                            + " :name::text, :symbol::text)")
                    .param("enum", r.enumKey())
                    .param("id", id)
                    .param("locale", t.getKey())
                    .param("name", trim(tr.name()))
                    .param("symbol", r.hasSymbol() ? trim(tr.symbol()) : null)
                    .query()
                    .listOfRows();
        }
        return list(actor);
    }

    /** Удалить значение, на которое ничего не ссылается. */
    @Transactional
    public Map<String, Object> delete(PortalUser actor, String enumKey, String valueId) {
        users.requireAdmin(actor);
        Registry r = registryOf(enumKey);
        Map<String, Object> current = values(r).stream()
                .filter(x -> String.valueOf(x.get("value_id")).equals(valueId))
                .findFirst()
                .orElseThrow(() -> Fail.notFound("error.enum.value_unknown", "value", valueId));
        if (systemValues().contains(r.enumKey() + "|" + current.get("code"))) {
            throw Fail.conflict("error.enum.system_value");
        }

        Map<String, Long> byKey = new LinkedHashMap<>();
        usage().getOrDefault(r.enumKey() + "|" + valueId, Map.of()).forEach((table, count) ->
                byKey.merge(USAGE_KEYS.getOrDefault(table, USAGE_OTHER), count, Long::sum));
        if (!byKey.isEmpty()) {
            List<MsgRef> where = new ArrayList<>();
            byKey.forEach((key, count) -> where.add(MsgRef.of(key, "count", count)));
            throw Fail.conflict("error.enum.in_use", "where", where);
        }

        jdbc.sql("DELETE FROM dbo.enum_text WHERE enum_key = :enum AND value_id = :id")
                .param("enum", r.enumKey())
                .param("id", valueId)
                .update();
        jdbc.sql("DELETE FROM dbo.%s WHERE id::text = :id".formatted(r.enumKey()))
                .param("id", valueId)
                .update();
        return list(actor);
    }

    /* ============================== вспомогательное ============================== */

    private List<Registry> registry() {
        return jdbc.sql("""
                        SELECT enum_key, code_column, has_symbol, text_key, code_pattern,
                               extra_column, extra_pattern, extra_label_key,
                               flag_column, flag_label_key, flag_hint_key
                        FROM dbo.enum_registry ORDER BY sort_order
                        """)
                .query((rs, n) -> new Registry(rs.getString("enum_key"), rs.getString("code_column"),
                        rs.getBoolean("has_symbol"), rs.getString("text_key"),
                        rs.getString("code_pattern"), rs.getString("extra_column"),
                        rs.getString("extra_pattern"), rs.getString("extra_label_key"),
                        rs.getString("flag_column"), rs.getString("flag_label_key"),
                        rs.getString("flag_hint_key")))
                .list();
    }

    private Registry registryOf(String enumKey) {
        return registry().stream()
                .filter(r -> r.enumKey().equals(enumKey))
                .findFirst()
                .orElseThrow(() -> Fail.notFound("error.enum.unknown", "enum", enumKey));
    }

    /**
     * Значения одного перечисления. Имя таблицы и колонок — из реестра:
     * в текст запроса попадает только то, что записала миграция.
     */
    private List<Map<String, Object>> values(Registry r) {
        return jdbc.sql(("SELECT id AS value_id, %s::varchar AS code, name, %s AS extra, %s AS flag,"
                        + " sort_order, COALESCE(is_active, true) AS is_active"
                        + " FROM dbo.%s ORDER BY sort_order NULLS LAST, id")
                        .formatted(r.codeColumn(),
                                r.extraColumn() == null ? "NULL::varchar" : r.extraColumn() + "::varchar",
                                r.flagColumn() == null ? "NULL::boolean" : r.flagColumn() + "::boolean",
                                r.enumKey()))
                .query()
                .listOfRows();
    }

    /** Языки переводов: включённые, кроме основного — его имя и есть основное. */
    private List<Map<String, Object>> locales() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> l : jdbc.sql("""
                        SELECT code, name FROM dbo.dictionary_locale
                        WHERE COALESCE(is_active, false) AND NOT COALESCE(is_default, false)
                        ORDER BY sort_order NULLS LAST, code
                        """).query().listOfRows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", l.get("code"));
            m.put("name", l.get("name"));
            out.add(m);
        }
        return out;
    }

    private Set<String> enabledLocales() {
        Set<String> out = new HashSet<>();
        locales().forEach(l -> out.add(String.valueOf(l.get("code"))));
        return out;
    }

    /** Переводы: «перечисление|значение» → {язык: {name, symbol}}. */
    private Map<String, Map<String, Object>> texts() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> t : jdbc.sql("""
                        SELECT et.enum_key, et.value_id, l.code, et.name, et.symbol
                        FROM dbo.enum_text et JOIN dbo.dictionary_locale l USING (locale_id)
                        """).query().listOfRows()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", t.get("name"));
            v.put("symbol", t.get("symbol"));
            out.computeIfAbsent(t.get("enum_key") + "|" + t.get("value_id"), k -> new LinkedHashMap<>())
                    .put(String.valueOf(t.get("code")), v);
        }
        return out;
    }

    /** Ссылки: «перечисление|значение» → {таблица: сколько записей}. */
    private Map<String, Map<String, Long>> usage() {
        Map<String, Map<String, Long>> out = new HashMap<>();
        for (Map<String, Object> u : jdbc.sql("SELECT * FROM dbo.enum_value_usage()")
                .query().listOfRows()) {
            out.computeIfAbsent(u.get("enum_key") + "|" + u.get("value_id"), k -> new LinkedHashMap<>())
                    .merge(String.valueOf(u.get("source_table")),
                            ((Number) u.get("usage_count")).longValue(), Long::sum);
        }
        return out;
    }

    private long usageOf(String enumKey, String valueId) {
        return usage().getOrDefault(enumKey + "|" + valueId, Map.of()).values().stream()
                .mapToLong(Long::longValue).sum();
    }

    private Set<String> systemValues() {
        return new HashSet<>(jdbc.sql("SELECT enum_key || '|' || code FROM dbo.enum_system_value")
                .query(String.class)
                .list());
    }

    /** Образец кода для подсказки: код первого значения перечисления. */
    private static String example(List<Map<String, Object>> values, String column) {
        return values.stream()
                .map(v -> v.get(column))
                .filter(x -> x != null && !String.valueOf(x).isBlank())
                .map(String::valueOf)
                .findFirst()
                .orElse(null);
    }

    private static void requirePattern(String pattern, String value, String example) {
        if (pattern != null && !value.matches(pattern)) {
            throw Fail.badRequest("error.enum.code_format", "example", example == null ? "" : example);
        }
    }

    /** Без учёта регистра: «Box» и «box» для человека одно и то же. */
    private static void requireUnique(List<Map<String, Object>> values, String valueId,
                                      String column, String value) {
        boolean taken = values.stream()
                .filter(x -> !String.valueOf(x.get("value_id")).equals(valueId))
                .anyMatch(x -> x.get(column) != null
                        && String.valueOf(x.get(column)).equalsIgnoreCase(value));
        if (taken) {
            throw Fail.conflict("error.enum.code_taken", "code", value);
        }
    }

    /**
     * Длина значения — до записи. Предел читается из схемы, а не вписан
     * числом: иначе проверка и база однажды разойдутся. Без проверки
     * человек получил бы ошибку базы про varchar(3) вместо понятного
     * «не больше трёх знаков».
     */
    private void requireFits(String table, String column, String labelKey, String value) {
        if (value == null) {
            return;
        }
        Integer limit = jdbc.sql("""
                        SELECT character_maximum_length FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = :table AND column_name = :column
                        """)
                .param("table", table)
                .param("column", column)
                .query(Integer.class)
                .optional()
                .orElse(null);
        if (limit != null && value.length() > limit) {
            throw Fail.badRequest("error.field.too_long", "field", MsgRef.of(labelKey), "limit", limit);
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
