package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.QaReportRuns;
import com.ahlers.portal3pl.files.FileStore;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вкладка «Запуски отчётов» группы «Отчёты» (V149).
 *
 * <p>Правила отбора проверяет {@code AdminReportRunsTest}. Здесь — экран:
 * администратор видит запуски двух клиентов, отбор по клиенту оставляет
 * одного, у неудачного видна причина, готовый скачивается, неготовый — нет.
 */
class ReportRunsScreenTest extends BrowserTestBase {

    private static final byte[] CONTENT = "QA runs screen".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private FileStore files;

    @AfterEach
    void cleanUp() {
        QaReportRuns.removeAll(owner(), files);
    }

    @Test
    void adminSeesFiltersAndDownloadsRunsOfAllClients() {
        QaReportRuns.removeAll(owner(), files);
        String depositor = depositorId(USER, DEPOSITOR);
        String ready = QaReportRuns.add(owner(), files, depositor, "QA ready", "03", CONTENT);
        String failed = QaReportRuns.add(owner(), files, depositorId(OTHER_USER, OTHER_DEPOSITOR),
                "QA failed", "04", null);

        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => { showView('admin'); showAdminGroup('reports'); showAdminSub('reports-runs'); }");
        page.waitForSelector("#admin-sub-reports-runs.active #runsBody tr[data-run='" + ready + "']");
        page.waitForSelector("#runsBody tr[data-run='" + failed + "']");

        // причина неудачи видна, скачать нечего
        String bad = page.innerText("#runsBody tr[data-run='" + failed + "']");
        assertTrue(bad.contains("QA: WMS отказала"), "у неудачного запуска не видна причина: " + bad);
        assertTrue((Boolean) page.evaluate("id => document.querySelector("
                        + "'#runsBody tr[data-run=\"' + id + '\"] [data-run-download]').disabled", failed),
                "у неудачного запуска можно нажать «Скачать»");

        // отбор по клиенту
        page.selectOption("#runsDepositor", depositor);
        page.click("#runsSearch");
        page.waitForFunction("id => !document.querySelector('#runsBody tr[data-run=\"' + id + '\"]')", failed);
        assertEquals(1, page.querySelectorAll("#runsBody tr[data-run='" + ready + "']").size(),
                "отбор по клиенту убрал и его запуск");

        Download file = page.waitForDownload(() ->
                page.click("#runsBody tr[data-run='" + ready + "'] [data-run-download]"));
        try {
            assertEquals(new String(CONTENT, StandardCharsets.US_ASCII),
                    java.nio.file.Files.readString(file.path(), StandardCharsets.US_ASCII), "скачался не тот файл");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        assertNoJsErrors();
    }
}
