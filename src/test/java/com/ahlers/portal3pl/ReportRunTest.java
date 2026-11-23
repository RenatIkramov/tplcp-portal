package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.reports.ReportCatalogService;
import com.ahlers.portal3pl.reports.ReportCatalogService.Assignment;
import com.ahlers.portal3pl.reports.ReportCatalogService.Param;
import com.ahlers.portal3pl.reports.ReportCatalogService.Report;
import com.ahlers.portal3pl.reports.ReportRunService;
import com.ahlers.portal3pl.reports.ReportRunService.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Запуск отчёта (V138): запрос в WMS, опрос готовности, файл.
 *
 * <p>WMS отвечает заданием, файл готов позже (решение 13.11.2026). Здесь —
 * что уходит в WMS (введённое, скрытое клиента, по умолчанию, текущая
 * дата, код клиента, номер документа), что отказывает до записи, как
 * запуск проходит «формируется → готов / ошибка» и что чужой запуск
 * не виден. Отказы WMS изображает подменённый {@link WmsGateway}.
 *
 * <p>Расписание опроса отключено ({@code poll-ms} — час): проверка зовёт
 * {@link ReportRunService#pollDue()} сама и видит каждый шаг.
 */
@TestPropertySource(properties = "portal.reports.poll-ms=3600000")
class ReportRunTest extends PortalTestBase {

    @Autowired
    ReportCatalogService catalog;

    @Autowired
    ReportRunService runs;

    @MockitoSpyBean
    WmsGateway wms;

    @AfterEach
    void removeTestReports() {
        reset(wms);
        owner().jdbc.sql("""
                DELETE FROM dbo.report_run
                 WHERE report_id IN (SELECT report_id FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%')
                    OR wms_code LIKE 'QA-RPT-%'
                """).update();
        owner().jdbc.sql("""
                DELETE FROM dbo.depositor_report
                 WHERE report_id IN (SELECT report_id FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%')
                """).update();
        owner().jdbc.sql("DELETE FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%'").update();
    }

    private static Param param(String key, String type, boolean required, String def, boolean today, boolean hidden) {
        return new Param(key, key, type, required, def, today, hidden, Map.of());
    }

    /** Отчёт в каталоге, выданный клиенту USER со значениями скрытых параметров. */
    private String report(String place, List<Param> params, Map<String, String> clientValues) {
        PortalUser admin = portalAdmin();
        String id = String.valueOf(catalog.create(admin,
                new Report("QA-RPT-RUN", "Run report", place, 1, true, Map.of(), params)).get("reportId"));
        String dep = depositorId(USER, DEPOSITOR);
        List<Assignment> rows = new ArrayList<>(catalog.depositorReports(admin, dep).stream()
                .filter(r -> Boolean.TRUE.equals(r.get("assigned")))
                .map(r -> new Assignment(String.valueOf(r.get("reportId")), (String) r.get("customName"),
                        ((Number) r.get("sortOrder")).intValue(), (Boolean) r.get("isActive")))
                .toList());
        rows.add(new Assignment(id, null, 99, true, clientValues));
        catalog.saveDepositorReports(admin, dep, rows);
        return id;
    }

    private PortalUser client() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }

    private String runCount() {
        return owner().jdbc.sql("SELECT count(*)::text FROM dbo.report_run WHERE wms_code LIKE 'QA-RPT-%'")
                .query(String.class).single();
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCarriesEveryKindOfValueAndBecomesReady() {
        String warehouse = singleValue("SELECT warehouse_id FROM dbo.v_warehouses ORDER BY warehouse_id LIMIT 1");
        String id = report("01", List.of(
                param("date_from", "01", true, null, false, false),
                param("date_to", "01", false, null, true, false),
                param("warehouse_id", "04", false, null, false, false),
                param("client", "09", false, null, false, false),
                param("lang", "03", false, "ru", false, true),
                param("zone", "03", false, null, false, true)), Map.of("zone", "A"));
        PortalUser user = client();

        Map<String, Object> run = runs.start(user, id,
                new Request(Map.of("date_from", "2026-11-01", "warehouse_id", warehouse, "client", "ПОДМЕНА"), null));

        ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(wms).requestReport(eq("QA-RPT-RUN"), sent.capture());
        assertEquals(Map.of("date_from", "2026-11-01", "date_to", LocalDate.now().toString(),
                        "warehouse_id", warehouse, "client", DEPOSITOR, "lang", "ru", "zone", "A"),
                sent.getValue(), "в WMS ушло не то");
        assertEquals("02", run.get("statusCode"), "WMS приняла — «формируется»");
        assertEquals(USER, run.get("requestedLogin"));

        due(String.valueOf(run.get("runId")));
        runs.pollDue();
        signIn();
        Map<String, Object> ready = runs.list().stream()
                .filter(r -> r.get("runId").equals(run.get("runId"))).findFirst().orElseThrow();
        assertEquals("03", ready.get("statusCode"), "опрос не довёл запуск до «готов»");
        WmsGateway.ReportFile file = runs.file(String.valueOf(run.get("runId")));
        assertTrue(new String(file.content(), StandardCharsets.UTF_8).contains("zone;A"), "файл не тот");
        assertTrue(file.fileName().startsWith("QA-RPT-RUN"));
    }

    @Test
    void wrongValuesAreRefusedBeforeAnythingIsWritten() {
        String id = report("01", List.of(
                param("date_from", "01", true, null, false, false),
                param("qty", "02", false, null, false, false),
                param("warehouse_id", "04", false, null, false, false)), Map.of());
        PortalUser user = client();
        String before = runCount();

        expectRejection(400, () -> runs.start(user, id, new Request(Map.of(), null)));
        expectRejection(400, () -> runs.start(user, id, new Request(Map.of("date_from", "01.11.2026"), null)));
        expectRejection(400, () -> runs.start(user, id,
                new Request(Map.of("date_from", "2026-11-01", "qty", "много"), null)));
        expectRejection(400, () -> runs.start(user, id,
                new Request(Map.of("date_from", "2026-11-01", "warehouse_id", "NO-SUCH-WH"), null)));
        expectRejection(404, () -> runs.start(user, "00000000-0000-4000-8000-000000000000", new Request(Map.of(), null)));
        assertEquals(before, runCount(), "отказ оставил запуск");
    }

    @Test
    void wmsRefusalAndFailureEndWithError() {
        String id = report("01", List.of(), Map.of());
        PortalUser user = client();

        doThrow(new IllegalStateException("WMS спит")).when(wms).requestReport(anyString(), anyMap());
        Map<String, Object> refused = runs.start(user, id, new Request(Map.of(), null));
        assertEquals("04", refused.get("statusCode"));
        assertEquals("error.report_run.wms_unavailable", refused.get("errorKey"));

        reset(wms);
        doReturn(new WmsGateway.ReportState(WmsGateway.ReportStatus.FAILED, null, null, "нет данных"))
                .when(wms).reportState(anyString());
        Map<String, Object> run = runs.start(user, id, new Request(Map.of(), null));
        due(String.valueOf(run.get("runId")));
        runs.pollDue();
        signIn();
        Map<String, Object> failed = runs.list().stream()
                .filter(r -> r.get("runId").equals(run.get("runId"))).findFirst().orElseThrow();
        assertEquals("04", failed.get("statusCode"));
        assertEquals("нет данных", failed.get("errorDetail"), "текст WMS потерян");
        expectRejection(409, () -> runs.file(String.valueOf(run.get("runId"))));
    }

    /** Пока формируется — спрашиваем снова; дольше срока — ошибка по времени. */
    @Test
    void longRunningJobEndsByTimeout() {
        String id = report("01", List.of(), Map.of());
        PortalUser user = client();
        doReturn(new WmsGateway.ReportState(WmsGateway.ReportStatus.RUNNING, null, null, null))
                .when(wms).reportState(anyString());
        String runId = String.valueOf(runs.start(user, id, new Request(Map.of(), null)).get("runId"));

        due(runId);
        runs.pollDue();
        assertEquals("02|1", status(runId), "формируется — проверка записана, запуск ждёт");

        owner().jdbc.sql("UPDATE dbo.report_run SET started_at = now() - interval '2 hours' WHERE run_id = :r::uuid")
                .param("r", runId).update();
        runs.pollDue();
        assertEquals("04|1", status(runId));
        assertEquals("error.report_run.timeout", owner().jdbc.sql(
                "SELECT error_key FROM dbo.report_run WHERE run_id = :r::uuid").param("r", runId).query(String.class).single());
    }

    /** Проверке пора — не ждать интервала опроса. */
    private void due(String runId) {
        owner().jdbc.sql("UPDATE dbo.report_run SET next_check_at = now() WHERE run_id = :r::uuid")
                .param("r", runId).update();
    }

    private String status(String runId) {
        return owner().jdbc.sql("""
                SELECT s.code || '|' || r.checks FROM dbo.report_run r
                  JOIN dbo.enum_report_run_status s ON s.id = r.status_id WHERE r.run_id = :r::uuid
                """).param("r", runId).query(String.class).single();
    }

    /**
     * Отчёт из карточки товара (V139): код товара подставляет портал —
     * своего товара; присланное человеком не берётся.
     */
    @Test
    void itemReportTakesTheCodeOfOwnItem() {
        String id = report("06", List.of(param("item", "11", false, null, false, false)), Map.of());
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku ORDER BY code LIMIT 1");
        String code = singleValue("SELECT code FROM dbo.sku WHERE sku_id = '" + skuId + "'");
        PortalUser user = client();

        expectRejection(400, () -> runs.start(user, id, new Request(Map.of(), null)));
        Map<String, Object> run = runs.start(user, id, new Request(Map.of("item", "ПОДМЕНА"), null, skuId));
        assertEquals(code, run.get("skuCode"));
        assertEquals(Map.of("item", code), run.get("params"));
        signIn();
        assertTrue(runs.list("06", skuId).stream().anyMatch(r -> r.get("runId").equals(run.get("runId"))),
                "в карточке товара нет его запуска");
        String otherSku = singleValue("SELECT sku_id::text FROM dbo.sku WHERE sku_id <> '" + skuId + "' LIMIT 1");
        assertTrue(runs.list("06", otherSku).stream().noneMatch(r -> r.get("runId").equals(run.get("runId"))),
                "запуск показан в карточке чужого товара");
        assertTrue(runs.list("01", null).stream().noneMatch(r -> r.get("runId").equals(run.get("runId"))),
                "запуск показан не в своём разделе");
    }

    /**
     * Тот же отчёт тот же человек второй раз не запускает, пока первый не
     * закончился (V139): кнопку экран блокирует, а сервер держит правило
     * и для двух окон браузера. Другой человек того же клиента — может.
     */
    @Test
    void sameReportIsNotStartedTwiceByTheSamePerson() {
        String id = report("01", List.of(), Map.of());
        PortalUser user = client();
        doReturn(new WmsGateway.ReportState(WmsGateway.ReportStatus.RUNNING, null, null, null))
                .when(wms).reportState(anyString());
        String first = String.valueOf(runs.start(user, id, new Request(Map.of(), null)).get("runId"));
        signIn();
        expectRejection(409, () -> runs.start(user, id, new Request(Map.of(), null)));

        reset(wms);
        due(first);
        runs.pollDue();
        signIn();
        assertEquals("03", status(first).substring(0, 2));
        assertEquals("02", runs.start(user, id, new Request(Map.of(), null)).get("statusCode"),
                "закончившийся запуск должен освобождать отчёт для нового");
    }

    /** Тело запроса экрана разбирается и с товаром, и без него. */
    @Test
    void requestBodyIsRead() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals("s1", mapper.readValue("{\"values\":{\"a\":\"1\"},\"skuId\":\"s1\"}", Request.class).skuId());
        assertEquals(Map.of("a", "1"), mapper.readValue("{\"values\":{\"a\":\"1\"}}", Request.class).values());
    }

    /** Запуски клиента видят все его пользователи — и никто больше. */
    @Test
    void otherClientSeesNothing() {
        String id = report("01", List.of(), Map.of());
        String runId = String.valueOf(runs.start(client(), id, new Request(Map.of(), null)).get("runId"));
        runs.pollDue();

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertTrue(runs.list().stream().noneMatch(r -> runId.equals(r.get("runId"))), "чужой запуск виден");
        expectRejection(404, () -> runs.file(runId));
        PortalUser other = authService.authenticate(OTHER_USER, USER_PASSWORD);
        expectRejection(404, () -> runs.start(other, id, new Request(Map.of(), null)));
    }

    /* ----------------------- файловое хранилище (V144) ----------------------- */

    @Autowired
    com.ahlers.portal3pl.files.FileStore files;

    @Autowired
    com.ahlers.portal3pl.files.StoredFileCleanup cleanup;

    private String readyRun() {
        String id = report("01", List.of(), Map.of());
        String runId = String.valueOf(runs.start(client(), id, new Request(Map.of(), null)).get("runId"));
        due(runId);
        runs.pollDue();
        assertEquals("03", status(runId).split("\\|")[0], "запуск не стал «готов»");
        return runId;
    }

    private java.util.UUID fileOf(String runId) {
        return owner().jdbc.sql("SELECT file_id FROM dbo.stored_file WHERE report_run_id = :r::uuid")
                .param("r", runId).query(java.util.UUID.class).optional().orElse(null);
    }

    /**
     * Файл забирается у WMS один раз и дальше отдаётся из хранилища
     * портала — даже когда WMS о задании уже не помнит. Раньше «Скачать»
     * каждый раз шло в WMS, и после её забывчивости готовый отчёт не скачивался.
     */
    @Test
    void readyFileIsKeptByThePortalAndWmsIsAskedOnce() {
        String runId = readyRun();
        doThrow(new IllegalStateException("WMS задание забыла")).when(wms).reportFile(anyString());

        WmsGateway.ReportFile first = runs.file(runId);
        WmsGateway.ReportFile second = runs.file(runId);
        assertTrue(first.fileName().startsWith("QA-RPT-RUN"));
        assertEquals(new String(first.content(), StandardCharsets.UTF_8), new String(second.content(), StandardCharsets.UTF_8));

        Map<String, Object> row = owner().jdbc.sql("SELECT size_bytes, sha256, kind FROM dbo.stored_file"
                + " WHERE report_run_id = :r::uuid").param("r", runId).query().singleRow();
        assertEquals(first.content().length, ((Number) row.get("size_bytes")).longValue());
        assertEquals("report", row.get("kind"));
        assertEquals(64, String.valueOf(row.get("sha256")).trim().length());
    }

    /** WMS задание выполнила, а файл не отдала — запуск остаётся в работе, спросим ещё раз. */
    @Test
    void fileNotGivenYetKeepsTheRunOpen() {
        String id = report("01", List.of(), Map.of());
        String runId = String.valueOf(runs.start(client(), id, new Request(Map.of(), null)).get("runId"));
        doThrow(new IllegalStateException("файл ещё пишется")).when(wms).reportFile(anyString());
        due(runId);
        runs.pollDue();
        assertEquals("02", status(runId).split("\\|")[0], "без файла запуск стал «готов»");
        assertNull(fileOf(runId));

        org.mockito.Mockito.doCallRealMethod().when(wms).reportFile(anyString());
        due(runId);
        runs.pollDue();
        assertEquals("03", status(runId).split("\\|")[0]);
        assertTrue(runs.file(runId).content().length > 0);
    }

    /** Файл пропал из папки — отказ «больше не хранится», а не пустой файл и не ошибка сервера. */
    @Test
    void lostFileIsRefusedPlainly() {
        String runId = readyRun();
        files.delete(fileOf(runId));
        expectRejection(409, () -> runs.file(runId));
    }

    /**
     * Уборка: запуск удалён по сроку — описание ушло с ним, содержимое
     * убирает уборка; свежий файл без описания не трогается (описание
     * пишется после содержимого); истёкший срок — удаляются оба.
     */
    @Test
    void cleanupRemovesFilesOfRemovedRunsAndExpiredOnes() {
        String gone = readyRun();
        java.util.UUID goneFile = fileOf(gone);
        owner().jdbc.sql("DELETE FROM dbo.report_run WHERE run_id = :r::uuid").param("r", gone).update();
        assertNotNull(files.read(goneFile), "содержимое пропало раньше уборки");

        cleanup.sweep(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
        assertNotNull(files.read(goneFile), "уборка удалила свежий файл — он мог быть ещё без описания");
        cleanup.sweep(java.time.Instant.now().plusSeconds(60));
        assertNull(files.read(goneFile), "файл удалённого запуска остался в папке");

        String expiring = String.valueOf(runs.start(client(), reportOf(), new Request(Map.of(), null)).get("runId"));
        due(expiring);
        runs.pollDue();
        java.util.UUID expiringFile = fileOf(expiring);
        owner().jdbc.sql("UPDATE dbo.stored_file SET expires_at = now() - interval '1 minute' WHERE file_id = :f")
                .param("f", expiringFile).update();
        cleanup.sweep(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
        assertNull(files.read(expiringFile), "файл с истёкшим сроком остался");
        assertNull(fileOf(expiring), "описание с истёкшим сроком осталось");
    }

    private String reportOf() {
        return owner().jdbc.sql("SELECT report_id::text FROM dbo.report WHERE wms_code = 'QA-RPT-RUN' LIMIT 1")
                .query(String.class).single();
    }

    /** Чужой файл не прочитать и напрямую: описания под RLS. */
    @Test
    void anotherClientDoesNotSeeTheFileRow() {
        String runId = readyRun();
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals("0", new org.springframework.transaction.support.TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(OTHER_USER, OTHER_DEPOSITOR)).query(String.class).single();
            return jdbc.sql("SELECT count(*)::text FROM dbo.stored_file WHERE report_run_id = :r::uuid")
                    .param("r", runId).query(String.class).single();
        }));
    }

    /** Сводка хранилища для администратора (V146): папка, файл отчёта посчитан, итог уборки запомнен. */
    @Test
    void adminSeesTheStorageSummary() {
        readyRun();
        cleanup.sweep(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
        com.ahlers.portal3pl.files.FileStore.Usage usage = files.usage();
        assertTrue(usage.location().contains("tplcp-portal-test-files"), "не та папка: " + usage.location());
        assertTrue(usage.exists() && usage.files() >= 1 && usage.bytes() > 0 && usage.freeBytes() > 0,
                "сводка папки пустая: " + usage);
        Map<String, Object> reports = owner().jdbc.sql("SELECT file_count, total_bytes FROM dbo.admin_file_storage_stats()"
                + " WHERE kind = 'report'").query().singleRow();
        assertTrue(((Number) reports.get("file_count")).longValue() >= 1, "файл отчёта не посчитан");
        assertTrue(cleanup.last() != null && cleanup.last().at() != null, "итог уборки не запомнен");
    }
}
