package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.microsoft.playwright.Download;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отчёты на экранах клиента (V138–V140, решения заказчика 14.11.2026):
 * раздел «Отчёты», вкладка «Отчёты» карточки товара.
 *
 * <p>«Сформировать» блокируется, пока отчёт формируется; в таблице сразу
 * строка со статусом, таблица перечитывается сама, готов — «Скачать»
 * активна и отдаёт файл. Отказ сервера в окне параметров виден в окне.
 *
 * <p>Опрос WMS здесь — раз в секунду ({@code poll-ms}), иначе проверка
 * ждала бы десять секунд на каждый отчёт.
 */
@TestPropertySource(properties = "portal.reports.poll-ms=1000")
class ClientReportsScreenTest extends BrowserTestBase {

    @AfterEach
    void cleanUp() {
        owner().jdbc.sql("""
                DELETE FROM dbo.report_run
                 WHERE report_id IN (SELECT report_id FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%')
                """).update();
        owner().jdbc.sql("""
                DELETE FROM dbo.depositor_report
                 WHERE report_id IN (SELECT report_id FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%')
                """).update();
        owner().jdbc.sql("DELETE FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%'").update();
    }

    /** Отчёт в каталоге, выданный клиенту USER; параметры — (код, подпись, тип, обязательный). */
    private String report(String code, String name, int place, String[]... params) {
        String id = owner().jdbc.sql("""
                INSERT INTO dbo.report (wms_code, name, place_id) VALUES (:c, :n, :p) RETURNING report_id::text
                """).param("c", code).param("n", name).param("p", place).query(String.class).single();
        int order = 0;
        for (String[] p : params) {
            owner().jdbc.sql("""
                    INSERT INTO dbo.report_param (report_id, param_key, label, type_id, is_required, is_hidden, sort_order)
                    VALUES (:r::uuid, :k, :l, :t, :req, :hidden, :o)
                    """).param("r", id).param("k", p[0]).param("l", p[1]).param("t", Integer.parseInt(p[2]))
                    .param("req", "true".equals(p[3])).param("hidden", Integer.parseInt(p[2]) >= 9)
                    .param("o", ++order).update();
        }
        owner().jdbc.sql("""
                INSERT INTO dbo.depositor_report (depositor_id, report_id, sort_order) VALUES (:d::uuid, :r::uuid, 0)
                """).param("d", depositorId(USER, DEPOSITOR)).param("r", id).update();
        return id;
    }

    private void signInClient() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();
    }

    @Test
    void reportIsProducedAndDownloaded() {
        String id = report("QA-RPT-SCREEN", "Screen report", 1,
                new String[]{"date_from", "Period from", "1", "true"},
                new String[]{"warehouse_id", "Warehouse", "4", "false"});
        String warehouse = singleValue("SELECT warehouse_id FROM dbo.v_warehouses ORDER BY warehouse_id LIMIT 1");
        signInClient();
        page.evaluate("() => showTab('reports')");
        String row = "#reportsHost tr[data-report='" + id + "']";
        page.waitForSelector(row);

        page.click(row + " [data-report-run]");
        page.waitForSelector("#reportRunOverlay.active");
        // склад — списком из справочника клиента, в WMS уйдёт код
        page.selectOption("#reportRunOverlay select[data-param='warehouse_id']", warehouse);

        // отказ — в окне, окно не закрывается
        page.fill("#reportRunOverlay input[data-param='date_from']", "");
        page.click("#reportRunSubmit");
        page.waitForSelector("#reportRunError");
        assertTrue(page.isVisible("#reportRunOverlay.active"), "окно закрылось после отказа");

        page.fill("#reportRunOverlay input[data-param='date_from']", "2026-11-01");
        page.click("#reportRunSubmit");
        page.waitForFunction("() => !document.getElementById('reportRunOverlay').classList.contains('active')");

        // строка запуска сразу; пока формируется — «Сформировать» заблокирована, «Скачать» — нет
        // строка ЭТОГО отчёта: в таблице могут быть и чужие запуски — те, что человек
        // сформировал руками; по ним проверка однажды решила, что отчёт уже готов (15.11.2026)
        String run = "#reportsHost tr[data-run-report='" + id + "']";
        page.waitForSelector(run);
        String status = page.getAttribute(run, "data-status");
        if (!"03".equals(status)) {
            assertTrue(page.isDisabled(row + " [data-report-run]"), "пока отчёт формируется, кнопку можно нажать");
            assertTrue(page.isDisabled(run + " [data-run-download]"), "«Скачать» активна до готовности");
        }

        // таблица перечитывается сама — до «Готов»
        page.waitForSelector(run + "[data-status='03']",
                new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(20000));
        assertFalse(page.isDisabled(row + " [data-report-run]"), "готов — кнопка снова доступна");
        assertTrue(page.textContent(run).contains(warehouse) && page.textContent(run).contains("Warehouse"),
                "в строке нет параметров запуска с подписью");

        Download file = page.waitForDownload(() -> page.click(run + " [data-run-download]"));
        assertTrue(file.suggestedFilename().startsWith("QA-RPT-SCREEN"), "не тот файл: " + file.suggestedFilename());
        assertNoJsErrors();
    }

    /** Карточка товара: вкладка «Отчёты», код товара подставляет портал. */
    @Test
    void itemCardHasItsReports() {
        String id = report("QA-RPT-ITEM", "Item movement", 6, new String[]{"item_code", "Item", "11", "false"});
        signInClient();
        openSkuList();
        page.evaluate("() => portalOpenSku(PORTAL.catalog.sku[0].code)");
        page.waitForSelector("#view-sku-detail.active #skuDetailTabs [data-tab='reports']");
        String code = (String) page.evaluate("() => PORTAL.catalog.sku[0].code");
        page.click("#skuDetailTabs [data-tab='reports']");
        String row = "#skuDetailBody tr[data-report='" + id + "']";
        page.waitForSelector(row);
        // параметров для человека нет — окна нет, сразу в работу
        page.click(row + " [data-report-run]");
        String run = "#skuDetailBody tr[data-run-report='" + id + "']";
        page.waitForSelector(run);
        assertTrue(page.textContent(run).contains(code), "код товара не подставлен");
        assertNoJsErrors();
    }

    /** Кнопок «Печать» и «История движения» больше нет; вкладка — «Прикреплённые файлы». */
    @Test
    void printAndMovementButtonsAreGone() {
        signInClient();
        String html = page.content();
        assertFalse(html.contains("openPrintDocumentModal"), "кнопка «Печать» осталась");
        assertFalse(html.contains("movementHistory"), "кнопка «История движения» осталась");
        assertEquals("Прикреплённые файлы", page.evaluate("() => T('common.title.photos')"));
        assertNoJsErrors();
    }
}
