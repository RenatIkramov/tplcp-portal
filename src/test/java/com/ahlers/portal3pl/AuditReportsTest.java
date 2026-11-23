package com.ahlers.portal3pl;

import com.ahlers.portal3pl.audit.AuditService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Журнал действий: каталог отчётов и сопоставления с WMS (V148).
 *
 * <p>Как и в {@link AuditTest}, отчёты клиента и настройки интеграции
 * меняются в транзакции, которая в конце откатывается: после проверки
 * не остаётся ни их, ни строк журнала. Правки самого отчёта — отдельными
 * транзакциями (см. ниже); отчёт проверки убирается после неё. Сохраняют
 * те же функции базы, что и портал, — с их «удалить всё — вставить заново».
 */
class AuditReportsTest extends PortalTestBase {

    private static final String CODE = "QA-RPT-AUDIT";

    @Autowired
    private AuditService audit;

    /** Действовать от имени администратора портала и откатить всё после. */
    private void asAdmin(Consumer<String> body) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.sql("SELECT set_config('app.actor', :a, true), set_config('app.locale', 'ru', true)")
                    .param("a", ADMIN).query().listOfRows();
            body.accept(ADMIN);
        });
    }

    private List<Map<String, Object>> history(String type, String id) {
        return jdbc.sql("SELECT * FROM dbo.admin_audit_history(:t, :id)")
                .param("t", type).param("id", id).query().listOfRows();
    }

    private String saveReport(String id, String name, String paramLabel, String paramEn) {
        String params = """
                [{"key": "date_from", "label": "%s", "type": "01", "required": true,
                  "texts": {"ru": "Период с"%s}}]
                """.formatted(paramLabel, paramEn == null ? "" : ", \"en\": \"" + paramEn + "\"");
        return jdbc.sql("""
                        SELECT dbo.admin_report_save(:id::uuid, :code, :name, '01', 5, true,
                                                     '{"ru": "Отчёт журнала"}'::jsonb, :params::jsonb)::text
                        """)
                .param("id", id).param("code", CODE).param("name", name).param("params", params)
                .query(String.class).single();
    }

    /**
     * Отчёт: создание, сохранение без правок (не событие), правка подписи
     * параметра и её перевода — по части «параметр», а не заменой всего.
     *
     * <p>Каждое сохранение — своей транзакцией, как в портале: события одной
     * транзакции журнал сводит в одно, и в общей транзакции «замена» подписей
     * была бы не видна. Поэтому здесь отчёт настоящий и убирается после.
     */
    @Test
    void reportEditsAreJournaledByParameter() {
        String id = inTx(() -> saveReport(null, "Audit report", "From", null));
        int afterCreate = history("report", id).size();
        assertTrue(history("report", id).stream().anyMatch(r -> "create".equals(r.get("action")) && r.get("part") == null),
                "создание отчёта не записано: " + history("report", id));

        inTx(() -> saveReport(id, "Audit report", "From", null));
        assertEquals(afterCreate, history("report", id).size(),
                "сохранение без правок попало в журнал: " + history("report", id));

        inTx(() -> saveReport(id, "Audit report", "Date from", "Date from"));
        List<Map<String, Object>> rows = history("report", id);
        assertTrue(rows.stream().anyMatch(r -> "param:date_from".equals(r.get("part"))
                        && "label".equals(r.get("field")) && "Date from".equals(r.get("new_text"))),
                "правка подписи параметра не записана частью параметра: " + rows);
        assertTrue(rows.stream().anyMatch(r -> "param:date_from:locale:en".equals(r.get("part"))
                        && "create".equals(r.get("action"))),
                "новый перевод подписи не записан: " + rows);
        assertEquals(1, rows.stream().filter(r -> "param:date_from:locale:ru".equals(r.get("part"))).count(),
                "неизменный русский перевод записан ещё раз, как новый: " + rows);
        assertTrue(rows.stream().allMatch(r -> ADMIN.equals(r.get("actor"))), "не тот, кто действовал: " + rows);
    }

    @AfterEach
    void removeTestReport() {
        owner().jdbc.sql("DELETE FROM dbo.report WHERE wms_code = :c").param("c", CODE).update();
    }

    /** Одна транзакция от имени администратора портала — с записью. */
    private <T> T inTx(java.util.function.Supplier<T> body) {
        return new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.actor', :a, true), set_config('app.locale', 'ru', true)")
                    .param("a", ADMIN).query().listOfRows();
            return body.get();
        });
    }

    /** Отчёты клиента — в истории поклажедателя, частью «отчёт «код»». */
    @Test
    void depositorReportsGoToTheDepositorHistory() {
        String dep = depositorId(USER, DEPOSITOR);
        asAdmin(actor -> {
            String id = saveReport(null, "Audit report", "From", null);
            String keep = jdbc.sql("""
                            SELECT COALESCE(jsonb_agg(jsonb_build_object('reportId', report_id,
                                       'customName', custom_name, 'sortOrder', sort_order, 'active', is_active,
                                       'values', param_values::jsonb)), '[]')::text
                              FROM dbo.admin_depositor_reports(:dep::uuid) WHERE assigned
                            """).param("dep", dep).query(String.class).single();
            String with = keep.replaceFirst("]$", (keep.equals("[]") ? "" : ",")
                    + "{\"reportId\": \"" + id + "\", \"customName\": \"Мой отчёт\", \"sortOrder\": 1, \"active\": true}]")
                    .replaceFirst("^\\[,", "[");
            jdbc.sql("SELECT dbo.admin_depositor_reports_save(:dep::uuid, :rows::jsonb)")
                    .param("dep", dep).param("rows", with).query(rs -> { });

            List<Map<String, Object>> rows = history("depositor", dep);
            assertTrue(rows.stream().anyMatch(r -> ("report:" + CODE).equals(r.get("part"))
                            && "create".equals(r.get("action")) && "Мой отчёт".equals(r.get("new_text"))),
                    "выдача отчёта клиенту не записана у поклажедателя: " + rows);
        });
    }

    /** Адрес WMS склада, код клиента в WMS и качество WMS. */
    @Test
    void wmsMappingsAreJournaled() {
        String dep = depositorId(USER, DEPOSITOR);
        asAdmin(actor -> {
            jdbc.sql("SELECT dbo.admin_wms_settings_save(:p::jsonb)").param("p", """
                    {"countTransfers": false, "syncEnabled": false, "syncTime": "02:30",
                     "warehouses": [{"warehouseId": "WH-ALA", "baseUrl": "http://qa-audit.local:8081"}],
                     "depositors": [{"depositorId": "%s", "wmsCode": "QA-AUD"}],
                     "qualities": [{"wmsCode": "QA9", "qualityCode": "01"}]}
                    """.formatted(dep)).query(rs -> { });

            assertTrue(history("warehouse", "WH-ALA").stream().anyMatch(r -> "wms".equals(r.get("part"))
                            && "base_url".equals(r.get("field")) && "http://qa-audit.local:8081".equals(r.get("new_text"))),
                    "адрес WMS склада не записан: " + history("warehouse", "WH-ALA"));
            assertTrue(history("depositor", dep).stream().anyMatch(r -> "wms".equals(r.get("part"))
                            && "QA-AUD".equals(r.get("new_text"))),
                    "код клиента в WMS не записан: " + history("depositor", dep));
            List<Map<String, Object>> quality = history("setting", "wms_quality");
            String good = jdbc.sql("SELECT dbo.enum_name('enum_quality', id::text, name) FROM dbo.enum_quality"
                    + " WHERE code = '01'").query(String.class).single();
            assertTrue(quality.stream().anyMatch(r -> "wms:QA9".equals(r.get("part"))
                            && good.equals(r.get("new_text"))),
                    "качество WMS не записано (или не словами): " + quality);
        });
    }

    /** Историю отчёта читает только администратор. */
    @Test
    void reportHistoryIsForAdminsOnly() {
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        signInAs(USER, DEPOSITOR);
        try {
            expectRejection(403, () -> audit.history(client, "report", "00000000-0000-0000-0000-000000000000"));
        } finally {
            com.ahlers.portal3pl.config.DepositorContext.clear();
        }
        assertTrue(audit.history(portalAdmin(), "report", "00000000-0000-0000-0000-000000000000").isEmpty());
    }
}
