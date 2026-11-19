package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Экраны каталога отчётов (V135, V136): «Настройка отчётов»
 * в администрировании и вкладка «Отчёты» карточки поклажедателя.
 *
 * <p>Правила записи проверяет {@code ReportCatalogTest}. Здесь — экран:
 * отчёт заводится в окне вместе с параметрами и переводами, отказ сервера
 * виден и окно не закрывается, клиенту отчёт выдаётся галочкой со своим
 * названием, а прочие отчёты клиента при этом не трогаются.
 */
class ReportCatalogScreenTest extends BrowserTestBase {

    private static final String CODE = "QA-RPT-SCREEN";

    @AfterEach
    void cleanUp() {
        owner().jdbc.sql("""
                DELETE FROM dbo.depositor_report
                 WHERE report_id IN (SELECT report_id FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%')
                """).update();
        owner().jdbc.sql("DELETE FROM dbo.report WHERE wms_code LIKE 'QA-RPT-%'").update();
    }

    private void openCatalog() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        // администратор без выбранного клиента видит открытый список
        // клиентов в шапке — он перекрыл бы кнопки панели
        page.evaluate("id => portalSelectDepositor(id)", depositorId(USER, DEPOSITOR));
        waitForQuiet();
        page.evaluate("() => { showView('admin'); showAdminGroup('reports'); }");
        page.waitForSelector("#admin-sub-reports-config.active #reportCatalogBody");
    }

    @Test
    void reportIsCreatedWithParamsInTheWindow() {
        openCatalog();
        page.click("#reportAdd");
        page.waitForSelector("#reportEditOverlay.active");

        // отказ сервера — плашкой, окно остаётся: человек исправит поле
        page.fill("#reportWmsCode", "QA RPT");
        page.fill("#reportName", "Screen report");
        page.click("#reportSave");
        waitForQuiet();
        assertTrue((Boolean) page.evaluate(
                "() => document.getElementById('reportEditOverlay').classList.contains('active')"),
                "окно закрылось после отказа — набранное пропало бы");
        // причина — в самом окне: плашка портала оказывается под окном
        // и не видна (заказчик 14.11.2026: «Сохранить не работает»)
        page.waitForSelector("#reportEditOverlay #reportError");
        assertTrue(page.textContent("#reportError").contains(singleValue("""
                SELECT value FROM dbo.ui_text t JOIN dbo.dictionary_locale l USING (locale_id)
                 WHERE t.text_key = 'error.report.wms_code_required' AND l.code = 'ru'
                """)), "в окне не та причина отказа: " + page.textContent("#reportError"));

        page.fill("#reportWmsCode", CODE);
        page.fill("#reportName_ru", "Отчёт с экрана");
        page.click("#reportParamAdd");
        page.fill("[data-param='0'] [data-param-field='key']", "date_to");
        page.fill("[data-param='0'] [data-param-field='label']", "To");
        page.fill("[data-param='0'] [data-param-text='ru']", "По");
        page.check("[data-param='0'] [data-param-flag='useCurrentDate']");
        page.click("#reportParamAdd");
        page.fill("[data-param='1'] [data-param-field='key']", "client");
        page.fill("[data-param='1'] [data-param-field='label']", "Client");
        page.selectOption("[data-param='1'] [data-param-field='type']", "09");
        assertEquals(0, page.querySelectorAll("[data-param='1'] [data-param-flag]").size(),
                "у параметра, который подставляет портал, нечего отмечать");
        // набранное в первом параметре пережило перерисовку окна
        assertEquals("date_to", page.inputValue("[data-param='0'] [data-param-field='key']"));

        page.click("#reportSave");
        page.waitForFunction("() => !document.getElementById('reportEditOverlay').classList.contains('active')");
        page.waitForSelector("#reportCatalogBody tr:has-text('" + CODE + "')");
        assertTrue(page.textContent("#reportCatalogBody").contains("Отчёт с экрана"),
                "в списке — название на языке пользователя");

        assertEquals("date_to:t|client:t", singleValue("""
                SELECT string_agg(p.param_key || ':' || CASE WHEN p.is_hidden OR p.use_current_date THEN 't' ELSE 'f' END,
                                  '|' ORDER BY p.sort_order)
                  FROM dbo.report_param p JOIN dbo.report r USING (report_id) WHERE r.wms_code = 'QA-RPT-SCREEN'
                """));
        assertEquals("По", singleValue("""
                SELECT t.label FROM dbo.report_param_text t JOIN dbo.report_param p USING (param_id)
                  JOIN dbo.report r USING (report_id) WHERE r.wms_code = 'QA-RPT-SCREEN' AND p.param_key = 'date_to'
                """));

        // удаление — с вопросом в самом окне
        page.click("#reportCatalogBody tr:has-text('" + CODE + "')");
        page.waitForSelector("#reportEditOverlay.active #reportDelete");
        page.click("#reportDelete");
        page.click("#reportDeleteYes");
        page.waitForFunction("() => !document.getElementById('reportEditOverlay').classList.contains('active')");
        waitForQuiet();
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.report WHERE wms_code = 'QA-RPT-SCREEN'"));
        assertNoJsErrors();
    }

    /**
     * Отчёт выдаётся кнопкой «+ Добавить отчёт»; значение скрытого
     * параметра задаётся здесь же, для этого клиента (V137). Без него
     * сохранение отказывает — и причина видна на вкладке.
     */
    @Test
    void clientGetsTheReportInItsCard() {
        String reportId = owner().jdbc.sql("""
                INSERT INTO dbo.report (wms_code, name, place_id) VALUES ('QA-RPT-SCREEN', 'Screen report', 1)
                RETURNING report_id::text
                """).query(String.class).single();
        owner().jdbc.sql("""
                INSERT INTO dbo.report_param (report_id, param_key, label, type_id, is_hidden)
                VALUES (:r::uuid, 'warehouse_id', 'Warehouse', 4, true)
                """).param("r", reportId).update();
        String depositor = depositorId(USER, DEPOSITOR);
        String before = owner().jdbc.sql("SELECT count(*)::text FROM dbo.depositor_report")
                .query(String.class).single();

        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("id => portalOpenPortalDir('depositorsDir', id)", depositor);
        page.waitForSelector("#portalDirTabs [data-tab='reports']");
        page.click("#portalDirTabs [data-tab='reports']");
        page.waitForSelector("#depositorReportAdd");
        String row = "#depositorReportsBody tr[data-report='" + reportId + "']";
        assertEquals(0, page.querySelectorAll(row).size(), "невыданный отчёт не должен стоять в таблице");

        page.click("#depositorReportAdd");
        page.selectOption("#depositorReportChoice", reportId);
        page.waitForSelector(row);
        page.fill(row + " [data-dr='customName']", "Наш отчёт");
        page.click("#depositorReportsSave");
        page.waitForSelector("#depositorReportsError");
        assertEquals("0", owner().jdbc.sql("SELECT count(*)::text FROM dbo.depositor_report WHERE report_id = :r::uuid")
                .param("r", reportId).query(String.class).single(), "отчёт выдан без значения скрытого параметра");

        page.fill("[data-report-hidden='" + reportId + "'] [data-hidden-key='warehouse_id']", "WH-ALA");
        page.click("#depositorReportsSave");
        page.waitForFunction("() => !document.getElementById('depositorReportsError')");
        waitForQuiet();

        assertEquals("Наш отчёт|WH-ALA", owner().jdbc.sql("""
                SELECT custom_name || '|' || (param_values ->> 'warehouse_id') FROM dbo.depositor_report
                 WHERE report_id = :r::uuid AND depositor_id = :d::uuid
                """).param("r", reportId).param("d", depositor).query(String.class).single());
        assertEquals(Integer.parseInt(before) + 1,
                Integer.parseInt(owner().jdbc.sql("SELECT count(*)::text FROM dbo.depositor_report")
                        .query(String.class).single()),
                "сохранение вкладки задело другие отчёты клиентов");
        assertNoJsErrors();
    }
}
