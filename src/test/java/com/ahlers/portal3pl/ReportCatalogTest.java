package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.reports.ReportCatalogService;
import com.ahlers.portal3pl.reports.ReportCatalogService.Assignment;
import com.ahlers.portal3pl.reports.ReportCatalogService.Param;
import com.ahlers.portal3pl.reports.ReportCatalogService.Report;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Каталог отчётов (V135, решение 13.11.2026).
 *
 * <p>Отчёт общий для всех клиентов; клиенту он выдаётся отдельно, со своим
 * названием. Один отчёт WMS можно завести несколькими строками с разным
 * набором параметров. Параметры, которые подставляет портал (код клиента,
 * номер документа), и скрытые экрану клиента не отдаются.
 */
class ReportCatalogTest extends PortalTestBase {

    private static final String CODE = "QA-RPT-ORDERS";

    @Autowired
    ReportCatalogService reports;

    @AfterEach
    void removeTestReports() {
        owner().jdbc.sql("""
                DELETE FROM dbo.depositor_report
                 WHERE report_id IN (SELECT report_id FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%')
                """).update();
        owner().jdbc.sql("DELETE FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%'").update();
    }

    private static Param param(String key, String label, String type) {
        return new Param(key, label, type, false, null, false, false, Map.of());
    }

    private static Report ordersReport(List<Param> params) {
        return new Report(CODE, "Orders report", "01", 1, true, Map.of("ru", "Отчёт по заказам"), params);
    }

    private static List<Param> period() {
        return List.of(
                new Param("date_from", "From", "01", true, null, false, false, Map.of("ru", "Период с")),
                new Param("date_to", "To", "01", true, null, true, false, Map.of("ru", "Период по")),
                param("client", "Client", "09"));
    }

    private String id(Map<String, Object> card) {
        return String.valueOf(card.get("reportId"));
    }

    /**
     * Выдать клиенту отчёт проверки, не трогая остальные его отчёты:
     * набор сохраняется целиком, и «только свой» стёр бы то, что
     * клиенту выдали руками.
     */
    private void assign(PortalUser admin, String dep, String reportId, String name, int order, boolean active) {
        List<Assignment> rows = new java.util.ArrayList<>(others(admin, dep, reportId));
        rows.add(new Assignment(reportId, name, order, active));
        reports.saveDepositorReports(admin, dep, rows);
    }

    private void unassign(PortalUser admin, String dep, String reportId) {
        reports.saveDepositorReports(admin, dep, others(admin, dep, reportId));
    }

    private List<Assignment> others(PortalUser admin, String dep, String reportId) {
        return reports.depositorReports(admin, dep).stream()
                .filter(r -> Boolean.TRUE.equals(r.get("assigned")))
                .filter(r -> !reportId.equals(String.valueOf(r.get("reportId"))))
                .map(r -> new Assignment(String.valueOf(r.get("reportId")), (String) r.get("customName"),
                        ((Number) r.get("sortOrder")).intValue(), (Boolean) r.get("isActive")))
                .toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportIsSavedWholeWithParamsAndTranslations() {
        PortalUser admin = portalAdmin();
        Map<String, Object> card = reports.create(admin, ordersReport(period()));

        assertEquals(CODE, card.get("wmsCode"));
        assertEquals(Map.of("ru", "Отчёт по заказам"), card.get("texts"));
        List<Map<String, Object>> params = (List<Map<String, Object>>) card.get("params");
        assertEquals(List.of("date_from", "date_to", "client"), params.stream().map(p -> p.get("key")).toList(),
                "параметры не в порядке окна");
        assertEquals(Map.of("ru", "Период с"), params.get(0).get("texts"));
        assertEquals(Boolean.TRUE, params.get(1).get("useCurrentDate"));
        assertEquals(Boolean.TRUE, params.get(2).get("hidden"), "код клиента подставляет портал — он скрыт сам");

        // правка заменяет параметры целиком
        card = reports.update(admin, id(card), ordersReport(List.of(param("warehouse_id", "Warehouse", "04"))));
        assertEquals(List.of("warehouse_id"),
                ((List<Map<String, Object>>) card.get("params")).stream().map(p -> p.get("key")).toList());
    }

    /** Один отчёт WMS — несколько строк каталога с разными параметрами. */
    @Test
    void sameWmsReportWithDifferentParameters() {
        PortalUser admin = portalAdmin();
        reports.create(admin, ordersReport(period()));
        reports.create(admin, new Report(CODE, "Orders today", "01", 2, true, Map.of(),
                List.of(new Param("date_from", "From", "01", false, null, true, true, Map.of()))));
        long same = reports.list(admin).stream().filter(r -> CODE.equals(r.get("wmsCode"))).count();
        assertEquals(2, same);
    }

    @Test
    void wrongReportsAreRefusedWithReason() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> reports.create(admin,
                new Report("QA-RPT BAD", "x", "01", 0, true, Map.of(), List.of())));
        expectRejection(400, () -> reports.create(admin,
                new Report(CODE, " ", "01", 0, true, Map.of(), List.of())));
        expectRejection(400, () -> reports.create(admin,
                new Report(CODE, "x", "99", 0, true, Map.of(), List.of())));
        expectRejection(400, () -> reports.create(admin, ordersReport(List.of(
                param("date_from", "From", "01"), param("DATE_FROM", "Again", "01")))));
        expectRejection(400, () -> reports.create(admin, ordersReport(List.of(
                new Param("n", "Number", "02", false, null, true, false, Map.of())))));
        expectRejection(400, () -> reports.create(admin, ordersReport(List.of(param("x", "X", "77")))));
        assertTrue(reports.list(admin).stream().noneMatch(r -> CODE.equals(r.get("wmsCode"))),
                "отказ оставил отчёт в каталоге");
    }

    @Test
    void onlyAdminKeepsTheCatalogue() {
        signIn();
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> reports.list(client));
        expectRejection(403, () -> reports.create(client, ordersReport(period())));
    }

    /**
     * Клиент видит только выданные ему отчёты — под своим названием, если
     * оно задано, и только параметры, которые заполняет сам.
     */
    @Test
    @SuppressWarnings("unchecked")
    void clientSeesOwnReportsUnderOwnName() {
        PortalUser admin = portalAdmin();
        String reportId = id(reports.create(admin, ordersReport(period())));
        String dep = depositorId(USER, DEPOSITOR);
        assign(admin, dep, reportId, null, 1, true);

        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        localeOf(client, "ru");
        signIn();
        Map<String, Object> mine = reports.clientReports("01").stream()
                .filter(r -> reportId.equals(r.get("reportId"))).findFirst().orElseThrow();
        assertEquals("Отчёт по заказам", mine.get("name"), "название не на языке пользователя");
        List<Map<String, Object>> params = (List<Map<String, Object>>) mine.get("params");
        assertEquals(List.of("date_from", "date_to"), params.stream().map(p -> p.get("key")).toList(),
                "экрану отдан параметр, который подставляет портал");
        assertEquals("Период с", params.get(0).get("label"));
        assertTrue(reports.clientReports("02").stream().noneMatch(r -> reportId.equals(r.get("reportId"))),
                "отчёт показан не в своём разделе");

        assign(admin, dep, reportId, "Наши заказы", 1, true);
        signIn();
        assertEquals("Наши заказы", reports.clientReports(null).stream()
                .filter(r -> reportId.equals(r.get("reportId"))).findFirst().orElseThrow().get("name"));

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertTrue(reports.clientReports(null).stream().noneMatch(r -> reportId.equals(r.get("reportId"))),
                "отчёт чужого клиента виден");
    }

    @Test
    void switchedOffReportDisappearsFromClient() {
        PortalUser admin = portalAdmin();
        String reportId = id(reports.create(admin, ordersReport(period())));
        String dep = depositorId(USER, DEPOSITOR);
        assign(admin, dep, reportId, null, 1, false);
        signIn();
        assertTrue(reports.clientReports(null).stream().noneMatch(r -> reportId.equals(r.get("reportId"))),
                "выключенный у клиента отчёт виден");

        assign(admin, dep, reportId, null, 1, true);
        reports.update(admin, reportId, new Report(CODE, "Orders report", "01", 1, false, Map.of(), period()));
        signIn();
        assertTrue(reports.clientReports(null).stream().noneMatch(r -> reportId.equals(r.get("reportId"))),
                "выключенный в каталоге отчёт виден");
    }

    /** Выдача отчёта проверки не трогает отчёты, выданные клиенту руками. */
    @Test
    void assigningKeepsOtherReportsOfTheClient() {
        PortalUser admin = portalAdmin();
        String dep = depositorId(USER, DEPOSITOR);
        String first = id(reports.create(admin, ordersReport(period())));
        String second = id(reports.create(admin, new Report("QA-RPT-SECOND", "Second", "01", 2, true,
                Map.of(), List.of())));
        assign(admin, dep, first, "Мой", 5, true);
        assign(admin, dep, second, null, 6, true);
        unassign(admin, dep, second);
        Map<String, Object> kept = reports.depositorReports(admin, dep).stream()
                .filter(r -> first.equals(String.valueOf(r.get("reportId")))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, kept.get("assigned"), "снятие одного отчёта сняло и другой");
        assertEquals("Мой", kept.get("customName"));
    }

    /**
     * Скрытый параметр без значения в каталоге: значение задаётся у клиента
     * (решение 14.11.2026, V137). Нет ни там, ни там — выдать нельзя.
     */
    @Test
    @SuppressWarnings("unchecked")
    void hiddenValueIsSetPerClient() {
        PortalUser admin = portalAdmin();
        String reportId = id(reports.create(admin, ordersReport(List.of(
                new Param("warehouse_id", "Warehouse", "04", false, null, false, true, Map.of("ru", "Склад")),
                new Param("lang", "Language", "03", false, "ru", false, true, Map.of())))));
        String dep = depositorId(USER, DEPOSITOR);

        expectRejection(400, () -> assign(admin, dep, reportId, null, 1, true));

        List<Assignment> rows = new java.util.ArrayList<>(others(admin, dep, reportId));
        rows.add(new Assignment(reportId, null, 1, true,
                Map.of("warehouse_id", " WH-ALA ", "not_a_param", "x")));
        reports.saveDepositorReports(admin, dep, rows);

        Map<String, Object> mine = reports.depositorReports(admin, dep).stream()
                .filter(r -> reportId.equals(String.valueOf(r.get("reportId")))).findFirst().orElseThrow();
        assertEquals(Map.of("warehouse_id", "WH-ALA"), mine.get("paramValues"),
                "значение не сохранилось — или сохранилось чужое");
        List<Map<String, Object>> hidden = (List<Map<String, Object>>) mine.get("hiddenParams");
        assertEquals(List.of("warehouse_id", "lang"), hidden.stream().map(h -> h.get("key")).toList(),
                "вкладке нужны скрытые параметры отчёта");
        assertEquals("ru", hidden.get(1).get("defaultValue"));
    }

    @Test
    void assignedReportIsNotDeleted() {
        PortalUser admin = portalAdmin();
        String reportId = id(reports.create(admin, ordersReport(period())));
        String dep = depositorId(USER, DEPOSITOR);
        assign(admin, dep, reportId, "Наши", 3, true);

        Map<String, Object> row = reports.depositorReports(admin, dep).stream()
                .filter(r -> reportId.equals(String.valueOf(r.get("reportId")))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, row.get("assigned"));
        assertEquals("Наши", row.get("customName"));

        expectRejection(409, () -> reports.delete(admin, reportId));
        unassign(admin, dep, reportId);
        assertFalse(reports.depositorReports(admin, dep).stream()
                .filter(r -> reportId.equals(String.valueOf(r.get("reportId"))))
                .anyMatch(r -> Boolean.TRUE.equals(r.get("assigned"))), "снятый отчёт остался у клиента");
        reports.delete(admin, reportId);
        expectRejection(404, () -> reports.card(admin, reportId));
    }

    /** Срок хранения запусков (V139): от 1 до 365 дней; значение администратора не теряется. */
    @Test
    void retentionIsSetByAdmin() {
        PortalUser admin = portalAdmin();
        Object was = reports.settings(admin).get("retentionDays");
        try {
            expectRejection(400, () -> reports.saveSettings(admin, 0));
            expectRejection(400, () -> reports.saveSettings(admin, 366));
            assertEquals(45, reports.saveSettings(admin, 45).get("retentionDays"));
            assertEquals(45, reports.settings(admin).get("retentionDays"));
        } finally {
            reports.saveSettings(admin, ((Number) was).intValue());
        }
        signIn();
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> reports.settings(client));
    }

    /** Перечисления отчётов — в общей группе «Перечисления», системные. */
    @Test
    void placesAndTypesAreSystemEnums() {
        assertEquals("17", singleValue("""
                SELECT count(*)::text FROM dbo.enum_system_value
                 WHERE enum_key IN ('enum_report_place', 'enum_report_param_type')
                """));
    }
}
