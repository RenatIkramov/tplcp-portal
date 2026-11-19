package com.ahlers.portal3pl.reports;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Каталог отчётов WMS (V135).
 *
 * <p>Отчёт формирует WMS: портал шлёт ей код отчёта и параметры. Здесь —
 * то, что портал о них знает:
 * <ul>
 *   <li><b>каталог</b> — общий для всех клиентов. Один отчёт WMS можно
 *       завести несколькими строками с разным набором параметров, поэтому
 *       код WMS не уникален;</li>
 *   <li><b>отчёты клиента</b> — какие из каталога выданы поклажедателю,
 *       под каким своим названием (одно на все языки) и в каком порядке.</li>
 * </ul>
 *
 * <p>Ведёт каталог администратор; пишется он только функциями
 * {@code admin_report_*}. Проверки здесь — ради понятного отказа: те же
 * правила держит и база (CHECK в V135).
 */
@Service
public class ReportCatalogService {

    /** Ключ WMS и код отчёта: что угодно, кроме пробелов. */
    private static final Pattern NO_SPACES = Pattern.compile("\\S+");

    /** Подставляет портал сам: код клиента, номер документа (до V139), код товара. */
    static final Set<String> SET_BY_PORTAL = Set.of("09", "10", "11");

    static final String DATE = "01";

    private final JdbcClient jdbc;
    private final AdminUserService users;
    private final DepositorScope scope;
    private final CurrentUser current;
    private final JsonMapper json = JsonMapper.builder().build();

    public ReportCatalogService(JdbcClient jdbc, AdminUserService users, DepositorScope scope,
                                CurrentUser current) {
        this.jdbc = jdbc;
        this.users = users;
        this.scope = scope;
        this.current = current;
    }

    /* ======================= Каталог ======================= */

    /** Отчёт и его параметры, как их правят в одном окне. */
    public record Param(String key, String label, String type, Boolean required, String defaultValue,
                        Boolean useCurrentDate, Boolean hidden, Map<String, String> texts) {
    }

    public record Report(String wmsCode, String name, String place, Integer sortOrder, Boolean active,
                         Map<String, String> texts, List<Param> params) {
    }

    /**
     * Что нужно окну отчёта: места кнопки, типы параметров (имена на языке
     * пользователя) и языки переводов — включённые, кроме основного:
     * его имя и есть основное, как в окне перечисления.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> options(PortalUser actor) {
        users.requireAdmin(actor);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("places", jdbc.sql("""
                        SELECT code, dbo.enum_name('enum_report_place', id::text, name) AS name
                          FROM dbo.enum_report_place WHERE is_active ORDER BY sort_order, id
                        """).query().listOfRows());
        out.put("paramTypes", jdbc.sql("""
                        SELECT code, dbo.enum_name('enum_report_param_type', id::text, name) AS name
                          FROM dbo.enum_report_param_type WHERE is_active ORDER BY sort_order, id
                        """).query().listOfRows());
        out.put("setByPortal", List.copyOf(SET_BY_PORTAL));
        out.put("locales", jdbc.sql("""
                        SELECT code, name FROM dbo.dictionary_locale
                         WHERE COALESCE(is_active, false) AND NOT COALESCE(is_default, false)
                         ORDER BY sort_order NULLS LAST, code
                        """).query().listOfRows());
        return out;
    }

    /** Весь каталог: строка на отчёт, с местом, числом параметров и клиентов. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("""
                        SELECT r.report_id::text, r.wms_code, r.name AS base_name,
                               dbo.report_name(r.report_id, r.name) AS name,
                               p.code AS place_code, dbo.enum_name('enum_report_place', p.id::text, p.name) AS place_name,
                               r.sort_order, r.is_active,
                               (SELECT count(*) FROM dbo.report_param rp WHERE rp.report_id = r.report_id)::int AS param_count,
                               dbo.admin_report_usage(r.report_id) AS depositor_count
                          FROM dbo.report r
                          JOIN dbo.enum_report_place p ON p.id = r.place_id
                         ORDER BY p.sort_order, r.sort_order, 4
                        """).query().listOfRows());
    }

    /** Карточка отчёта: шапка, переводы, параметры с переводами. */
    @Transactional(readOnly = true)
    public Map<String, Object> card(PortalUser actor, String reportId) {
        users.requireAdmin(actor);
        return cardOf(reportId);
    }

    @Transactional
    public Map<String, Object> create(PortalUser actor, Report report) {
        users.requireAdmin(actor);
        return cardOf(save(null, report));
    }

    @Transactional
    public Map<String, Object> update(PortalUser actor, String reportId, Report report) {
        users.requireAdmin(actor);
        requireReport(reportId);
        return cardOf(save(reportId, report));
    }

    /** Удалить можно только отчёт, не выданный ни одному клиенту; выданный — выключить. */
    @Transactional
    public void delete(PortalUser actor, String reportId) {
        users.requireAdmin(actor);
        requireReport(reportId);
        int usage = jdbc.sql("SELECT dbo.admin_report_usage(:id::uuid)").param("id", reportId)
                .query(Integer.class).single();
        if (usage > 0) {
            throw Fail.conflict("error.report.in_use");
        }
        jdbc.sql("SELECT dbo.admin_report_delete(:id::uuid)").param("id", reportId).query().listOfRows();
    }

    private String save(String reportId, Report r) {
        if (r == null) {
            throw Fail.badRequest("error.report.wms_code_required");
        }
        String wmsCode = trim(r.wmsCode());
        if (wmsCode == null || !NO_SPACES.matcher(wmsCode).matches()) {
            throw Fail.badRequest("error.report.wms_code_required");
        }
        if (trim(r.name()) == null) {
            throw Fail.badRequest("error.report.name_required");
        }
        if (!known("enum_report_place", r.place())) {
            throw Fail.badRequest("error.report.bad_place");
        }
        List<Map<String, Object>> params = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Param p : r.params() == null ? List.<Param>of() : r.params()) {
            params.add(checked(p, keys));
        }
        return jdbc.sql("""
                        SELECT dbo.admin_report_save(:id::uuid, :code, :name, :place, :sort, :active,
                                                     :texts::jsonb, :params::jsonb)::text
                        """)
                .param("id", reportId)
                .param("code", wmsCode)
                .param("name", trim(r.name()))
                .param("place", r.place())
                .param("sort", r.sortOrder() == null ? 0 : r.sortOrder())
                .param("active", r.active() == null || r.active())
                .param("texts", json.writeValueAsString(r.texts() == null ? Map.of() : r.texts()))
                .param("params", json.writeValueAsString(params))
                .query(String.class).single();
    }

    /** Параметр в том виде, в каком его ждёт admin_report_save, — или понятный отказ. */
    private Map<String, Object> checked(Param p, Set<String> keys) {
        String key = p == null ? null : trim(p.key());
        if (key == null || !NO_SPACES.matcher(key).matches()) {
            throw Fail.badRequest("error.report.param_key_required");
        }
        if (!keys.add(key.toLowerCase())) {
            throw Fail.badRequest("error.report.param_key_twice", "key", key);
        }
        String label = trim(p.label());
        if (label == null) {
            throw Fail.badRequest("error.report.param_label_required");
        }
        if (!known("enum_report_param_type", p.type())) {
            throw Fail.badRequest("error.report.bad_param_type");
        }
        boolean currentDate = Boolean.TRUE.equals(p.useCurrentDate());
        if (currentDate && !DATE.equals(p.type())) {
            throw Fail.badRequest("error.report.current_date_only_date");
        }
        boolean setByPortal = SET_BY_PORTAL.contains(p.type());
        boolean hidden = Boolean.TRUE.equals(p.hidden()) || setByPortal;
        String defaultValue = setByPortal ? null : trim(p.defaultValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("label", label);
        out.put("type", p.type());
        out.put("required", !setByPortal && Boolean.TRUE.equals(p.required()));
        out.put("default", defaultValue);
        out.put("currentDate", currentDate);
        out.put("hidden", hidden);
        out.put("texts", p.texts() == null ? Map.of() : p.texts());
        return out;
    }

    private Map<String, Object> cardOf(String reportId) {
        Map<String, Object> head = jdbc.sql("""
                        SELECT r.report_id::text, r.wms_code, r.name, p.code AS place_code,
                               r.sort_order, r.is_active
                          FROM dbo.report r JOIN dbo.enum_report_place p ON p.id = r.place_id
                         WHERE r.report_id = :id::uuid
                        """).param("id", reportId).query().listOfRows().stream()
                .findFirst().orElseThrow(() -> Fail.notFound("error.report.not_found"));
        Map<String, Object> card = new LinkedHashMap<>(SqlRows.toCamelCase(List.of(head)).getFirst());
        card.put("texts", texts("""
                SELECT l.code, t.name AS value FROM dbo.report_text t
                  JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                 WHERE t.report_id = :id::uuid
                """, reportId));
        List<Map<String, Object>> params = new ArrayList<>();
        for (Map<String, Object> row : SqlRows.toCamelCase(jdbc.sql("""
                        SELECT rp.param_id::text, rp.param_key AS key, rp.label, t.code AS type,
                               rp.is_required AS required, rp.default_value, rp.use_current_date,
                               rp.is_hidden AS hidden
                          FROM dbo.report_param rp JOIN dbo.enum_report_param_type t ON t.id = rp.type_id
                         WHERE rp.report_id = :id::uuid
                         ORDER BY rp.sort_order
                        """).param("id", reportId).query().listOfRows())) {
            Map<String, Object> param = new LinkedHashMap<>(row);
            param.put("texts", texts("""
                    SELECT l.code, t.label AS value FROM dbo.report_param_text t
                      JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                     WHERE t.param_id = :id::uuid
                    """, String.valueOf(row.get("paramId"))));
            params.add(param);
        }
        card.put("params", params);
        return card;
    }

    private Map<String, String> texts(String sql, String id) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.sql(sql).param("id", id).query().listOfRows()
                .forEach(row -> out.put(String.valueOf(row.get("code")), String.valueOf(row.get("value"))));
        return out;
    }

    private void requireReport(String reportId) {
        boolean found = reportId != null && reportId.matches("[0-9a-fA-F-]{36}")
                && Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM dbo.report WHERE report_id = :id::uuid)")
                        .param("id", reportId).query(Boolean.class).single());
        if (!found) {
            throw Fail.notFound("error.report.not_found");
        }
    }

    private boolean known(String enumTable, String code) {
        return code != null && Boolean.TRUE.equals(jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM dbo." + enumTable + " WHERE code = :c AND is_active)")
                .param("c", code).query(Boolean.class).single());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /* ======================= Срок хранения (V139) ======================= */

    static final int RETENTION_MIN = 1;
    static final int RETENTION_MAX = 365;

    @Transactional(readOnly = true)
    public Map<String, Object> settings(PortalUser actor) {
        users.requireAdmin(actor);
        int days = jdbc.sql("SELECT value::int FROM dbo.portal_setting WHERE setting_key = 'report_run_retention_days'")
                .query(Integer.class).single();
        return Map.of("retentionDays", days);
    }

    /** Сменить срок хранения запусков: от 1 до 365 дней; старое удаляется сразу. */
    @Transactional
    public Map<String, Object> saveSettings(PortalUser actor, Integer days) {
        users.requireAdmin(actor);
        if (days == null || days < RETENTION_MIN || days > RETENTION_MAX) {
            throw Fail.badRequest("error.report.retention_range", "min", RETENTION_MIN, "max", RETENTION_MAX);
        }
        int removed = jdbc.sql("SELECT dbo.admin_set_report_retention(:d)").param("d", days)
                .query(Integer.class).single();
        return Map.of("retentionDays", days, "removed", removed);
    }

    /* ======================= Отчёты клиента ======================= */

    /**
     * Отчёт клиента. {@code values} — значения скрытых параметров для этого
     * клиента: {@code {"код параметра WMS": "значение"}} (V137).
     */
    public record Assignment(String reportId, String customName, Integer sortOrder, Boolean active,
                             Map<String, String> values) {
        public Assignment(String reportId, String customName, Integer sortOrder, Boolean active) {
            this(reportId, customName, sortOrder, active, null);
        }
    }

    /**
     * Карточка поклажедателя, вкладка «Отчёты»: весь каталог, выданные
     * отмечены; у каждого — скрытые параметры, которые задаёт человек
     * ({@code hiddenParams}), и значения этого клиента ({@code paramValues}).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> depositorReports(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        requireDepositor(depositorId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_depositor_reports(:dep::uuid)")
                .param("dep", depositorId).query().listOfRows())) {
            Map<String, Object> r = new LinkedHashMap<>(row);
            r.put("paramValues", json.readValue(String.valueOf(row.get("paramValues")), Map.class));
            r.put("hiddenParams", json.readValue(String.valueOf(row.get("hiddenParams")), List.class));
            out.add(r);
        }
        return out;
    }

    /**
     * Сохранить набор целиком: отчёта нет в списке — он снят с клиента.
     *
     * <p>Скрытому параметру нужно значение — у клиента или по умолчанию
     * в каталоге: человек его не видит и не заполнит. Нет ни того, ни
     * другого — отказ с названием отчёта и параметра. Значения чужих
     * параметров (не скрытых, не этого отчёта) не сохраняются.
     */
    @Transactional
    public List<Map<String, Object>> saveDepositorReports(PortalUser actor, String depositorId,
                                                          List<Assignment> rows) {
        users.requireAdmin(actor);
        requireDepositor(depositorId);
        List<Map<String, Object>> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Assignment a : rows == null ? List.<Assignment>of() : rows) {
            if (a == null || a.reportId() == null || !seen.add(a.reportId())) {
                continue;
            }
            requireReport(a.reportId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reportId", a.reportId());
            row.put("customName", trim(a.customName()));
            row.put("sortOrder", a.sortOrder() == null ? 0 : a.sortOrder());
            row.put("active", a.active() == null || a.active());
            row.put("values", hiddenValues(a));
            clean.add(row);
        }
        jdbc.sql("SELECT dbo.admin_depositor_reports_save(:dep::uuid, :rows::jsonb)")
                .param("dep", depositorId)
                .param("rows", json.writeValueAsString(clean))
                .query().listOfRows();
        return depositorReports(actor, depositorId);
    }

    /** Значения скрытых параметров отчёта для клиента — или отказ, если какого-то не хватает. */
    private Map<String, String> hiddenValues(Assignment a) {
        Map<String, String> given = a.values() == null ? Map.of() : a.values();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> p : jdbc.sql("""
                        SELECT rp.param_key, dbo.report_param_label(rp.param_id, rp.label) AS label,
                               rp.default_value, rp.use_current_date,
                               dbo.report_name(r.report_id, r.name) AS report
                          FROM dbo.report_param rp JOIN dbo.report r ON r.report_id = rp.report_id
                         WHERE rp.report_id = :id::uuid AND rp.is_hidden AND rp.type_id NOT IN (9, 10, 11)
                         ORDER BY rp.sort_order
                        """).param("id", a.reportId()).query().listOfRows()) {
            String key = String.valueOf(p.get("param_key"));
            String value = trim(given.get(key));
            if (value != null) {
                out.put(key, value);
            } else if (trim((String) p.get("default_value")) == null && !Boolean.TRUE.equals(p.get("use_current_date"))) {
                String name = trim(a.customName());
                throw Fail.badRequest("error.report.client_value_required",
                        "report", name != null ? name : p.get("report"), "label", p.get("label"));
            }
        }
        return out;
    }

    private void requireDepositor(String depositorId) {
        if (depositorId == null || depositorId.isBlank()) {
            throw Fail.badRequest("error.depositor.required");
        }
        boolean known = Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dbo.admin_depositor_access()
                                        WHERE depositor_id::text = :dep::text)
                        """).param("dep", depositorId).query(Boolean.class).single());
        if (!known) {
            throw Fail.notFound("error.depositor.not_found");
        }
    }

    /* ======================= Что видит клиент ======================= */

    /**
     * Отчёты клиента для раздела ({@code place} — код места, пусто — все):
     * название на языке пользователя или своё название клиента, параметры,
     * которые человек заполняет. Скрытые и подставляемые порталом наружу
     * не уходят — их значения портал берёт сам при запуске.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> clientReports(String place) {
        scope.apply();
        List<Map<String, Object>> reports = new ArrayList<>();
        for (Map<String, Object> row : SqlRows.toCamelCase(jdbc.sql("""
                        SELECT report_id::text, name, place_code FROM dbo.v_client_reports
                         WHERE :place::text IS NULL OR place_code = :place::text
                         ORDER BY place_code, sort_order, name
                        """).param("place", trim(place)).query().listOfRows())) {
            // отчёты скрытого раздела не показываются, как и сам раздел (V138)
            Object placeCode = row.get("placeCode");
            if (("02".equals(placeCode) && current.billingHidden())
                    || ("05".equals(placeCode) && current.transfersHidden())) {
                continue;
            }
            Map<String, Object> report = new LinkedHashMap<>(row);
            report.put("params", SqlRows.toCamelCase(jdbc.sql("""
                            SELECT param_key AS key, label, type_code AS type, is_required AS required,
                                   default_value, use_current_date
                              FROM dbo.v_client_report_params
                             WHERE report_id = :id::uuid
                             ORDER BY sort_order
                            """).param("id", String.valueOf(row.get("reportId"))).query().listOfRows()));
            reports.add(report);
        }
        return reports;
    }
}
