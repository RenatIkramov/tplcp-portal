package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.files.FileStore;
import com.ahlers.portal3pl.reports.AdminReportRunService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Запуски отчётов» администратора (V149): запуски всех клиентов.
 *
 * <p>Запуски заводятся владельцем прямо в базе ({@link QaReportRuns}):
 * готовый у одного клиента и неудачный у другого. Клиенту под RLS
 * видны только свои — администратору здесь все, с отборами и файлом.
 */
class AdminReportRunsTest extends PortalTestBase {

    private static final byte[] CONTENT = "QA report runs".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private AdminReportRunService runs;
    @Autowired
    private FileStore files;

    private final JsonMapper json = JsonMapper.builder().build();
    private String ready;
    private String failed;
    private UUID depositor;
    private UUID other;

    @BeforeEach
    void addRuns() {
        QaReportRuns.removeAll(owner(), files);
        depositor = UUID.fromString(depositorId(USER, DEPOSITOR));
        other = UUID.fromString(depositorId(OTHER_USER, OTHER_DEPOSITOR));
        ready = QaReportRuns.add(owner(), files, depositor.toString(), "QA ready", "03", CONTENT);
        failed = QaReportRuns.add(owner(), files, other.toString(), "QA failed", "04", null);
    }

    @AfterEach
    void removeRuns() {
        QaReportRuns.removeAll(owner(), files);
    }

    /** Наши запуски на странице «сегодня» при заданном отборе. */
    private List<JsonNode> ours(UUID dep, String status, LocalDate from, LocalDate to) {
        JsonNode page = json.readTree(runs.page(portalAdmin(), dep, null, status, from, to, 1, 200));
        List<JsonNode> out = new ArrayList<>();
        page.get("rows").forEach(r -> {
            if (QaReportRuns.WMS_CODE.equals(r.get("wmsCode").asString())) {
                out.add(r);
            }
        });
        return out;
    }

    private static List<String> ids(List<JsonNode> rows) {
        return rows.stream().map(r -> r.get("runId").asString()).sorted().toList();
    }

    @Test
    void adminSeesRunsOfEveryClient() {
        List<JsonNode> rows = ours(null, null, LocalDate.now(), null);
        assertEquals(List.of(ready, failed).stream().sorted().toList(), ids(rows),
                "администратору видны не все запуски клиентов");
        JsonNode bad = rows.stream().filter(r -> failed.equals(r.get("runId").asString())).findFirst().orElseThrow();
        assertEquals("04", bad.get("statusCode").asString());
        assertEquals("error.report_run.wms_failed", bad.get("errorKey").asString(), "нет причины ошибки");
        assertFalse(bad.get("depositorName").isNull(), "у запуска не подписан клиент");
        JsonNode good = rows.stream().filter(r -> ready.equals(r.get("runId").asString())).findFirst().orElseThrow();
        assertTrue(good.get("hasFile").asBoolean(), "у готового запуска не отмечен файл");
        assertEquals("2026-11-01", good.get("params").get("date_from").asString());
    }

    @Test
    void filtersNarrowTheList() {
        assertEquals(List.of(ready), ids(ours(depositor, null, null, null)), "отбор по клиенту");
        assertEquals(List.of(failed), ids(ours(null, "04", null, null)), "отбор по статусу");
        assertTrue(ours(null, null, LocalDate.now().plusDays(1), null).isEmpty(), "отбор «с завтра» — пусто");
        assertTrue(ours(null, null, null, LocalDate.now().minusDays(1)).isEmpty(), "отбор «по вчера» — пусто");
    }

    /** Страница за концом списка — первая: отбор сузили, а номер страницы остался. */
    @Test
    void pageBeyondTheEndFallsBackToTheFirst() {
        JsonNode page = json.readTree(runs.page(portalAdmin(), depositor, null, null, LocalDate.now(), null, 10_000, 1));
        assertEquals(1, page.get("page").asInt());
        assertEquals(1, page.get("rows").size());
    }

    @Test
    void adminDownloadsAnyClientsFile() {
        WmsGateway.ReportFile file = runs.file(portalAdmin(), UUID.fromString(ready));
        assertEquals(new String(CONTENT, StandardCharsets.UTF_8), new String(file.content(), StandardCharsets.UTF_8));
        expectRejection(409, () -> runs.file(portalAdmin(), UUID.fromString(failed)));
        expectRejection(404, () -> runs.file(portalAdmin(), UUID.randomUUID()));
    }

    @Test
    void clientIsRefused() {
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> runs.page(client, null, null, null, null, null, 1, 50));
        expectRejection(403, () -> runs.statuses(client));
        expectRejection(403, () -> runs.file(client, UUID.fromString(ready)));
    }
}
