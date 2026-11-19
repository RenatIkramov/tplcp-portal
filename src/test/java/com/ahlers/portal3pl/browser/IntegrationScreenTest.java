package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.OwnerDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Администрирование → «Интеграция» (V143): настройки слоя перевода
 * сохраняются одной кнопкой, отказ сервера виден в самом блоке, «Сверить
 * сейчас» пишет в журнал. Заглушка WMS данных не даёт — сверка в журнале
 * «не удалась», с объяснением на языке смотрящего.
 *
 * <p>Настройки и журнал, которые были до проверки, возвращаются как были.
 */
@TestPropertySource(properties = "portal.stock-sync.check-ms=3600000")
class IntegrationScreenTest extends BrowserTestBase {

    private List<Map<String, Object>> settings;
    private List<Map<String, Object>> warehouses;
    private List<Map<String, Object>> depositors;
    private java.sql.Timestamp since;

    @BeforeEach
    void remember() {
        OwnerDb o = owner();
        settings = o.jdbc.sql("SELECT setting_key, value FROM dbo.portal_setting WHERE setting_key LIKE 'stock_%'")
                .query().listOfRows();
        warehouses = o.jdbc.sql("SELECT warehouse_id, base_url FROM dbo.wms_warehouse").query().listOfRows();
        depositors = o.jdbc.sql("SELECT depositor_id, wms_code FROM dbo.wms_depositor").query().listOfRows();
        since = o.jdbc.sql("SELECT clock_timestamp()::timestamp").query(java.sql.Timestamp.class).single();
        o.jdbc.sql("DELETE FROM dbo.wms_warehouse").update();
        o.jdbc.sql("DELETE FROM dbo.wms_depositor").update();
    }

    @AfterEach
    void restore() {
        OwnerDb o = owner();
        settings.forEach(s -> o.jdbc.sql("UPDATE dbo.portal_setting SET value = :v WHERE setting_key = :k")
                .param("v", s.get("value")).param("k", s.get("setting_key")).update());
        o.jdbc.sql("DELETE FROM dbo.wms_warehouse").update();
        o.jdbc.sql("DELETE FROM dbo.wms_depositor").update();
        warehouses.forEach(r -> o.jdbc.sql("INSERT INTO dbo.wms_warehouse VALUES (:a, :b)")
                .param("a", r.get("warehouse_id")).param("b", r.get("base_url")).update());
        depositors.forEach(r -> o.jdbc.sql("INSERT INTO dbo.wms_depositor VALUES (:a, :b)")
                .param("a", r.get("depositor_id")).param("b", r.get("wms_code")).update());
        o.jdbc.sql("DELETE FROM dbo.sync_run WHERE entity = 'stock' AND started_at >= :t::timestamp")
                .param("t", since).update();
        o.jdbc.sql("SELECT dbo.stock_recalc(NULL)").query(rs -> { });
    }

    @Test
    void settingsAreSavedAndReconciliationIsJournaled() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        // администратор без выбранного клиента видит открытый список клиентов — он перекрыл бы кнопки
        page.evaluate("id => portalSelectDepositor(id)", depositorId(USER, DEPOSITOR));
        waitForQuiet();
        page.evaluate("() => { showView('admin'); showAdminGroup('integration'); }");
        page.waitForSelector("#admin-sub-wms.active #wmsSettingsSave");

        // хранилище файлов (V146): папка показана — та, куда пишет портал
        page.waitForSelector("#fileStorageFolder");
        assertTrue(page.textContent("#fileStorageFolder").contains("tplcp-portal-test-files"),
                "показана не та папка: " + page.textContent("#fileStorageFolder"));

        String url = "#admin-sub-wms input[data-wms-url='WH-ALA']";
        String code = "#admin-sub-wms input[data-wms-depositor='" + DEPOSITOR + "']";

        // плохой адрес — отказ в самом блоке, набранное на месте
        page.fill(url, "ftp://wms");
        page.fill(code, "QA-0001");
        page.click("#wmsSettingsSave");
        page.waitForSelector("#wmsSettingsError");
        assertEquals("ftp://wms", page.inputValue(url), "набранное пропало после отказа");

        page.fill(url, "http://qa-wms.test:13515/");
        page.check("#wmsCountTransfers");
        page.click("#wmsSettingsSave");
        page.waitForFunction("() => !document.getElementById('wmsSettingsError')"
                + " && !document.getElementById('wmsSettingsSave').disabled"
                + " && document.querySelector(\"input[data-wms-url='WH-ALA']\").value === 'http://qa-wms.test:13515'");
        assertEquals("http://qa-wms.test:13515", owner().jdbc.sql(
                "SELECT base_url FROM dbo.wms_warehouse WHERE warehouse_id = 'WH-ALA'").query(String.class).single());
        assertEquals("true", owner().jdbc.sql(
                "SELECT value FROM dbo.portal_setting WHERE setting_key = 'stock_count_transfers'")
                .query(String.class).single());

        // «Сверить сейчас»: заглушка WMS данных не даёт — в журнале строка с объяснением по-русски
        page.click("#wmsSyncNow");
        page.waitForSelector("#wmsSyncLog tr[data-sync-run][data-status='02']");
        assertTrue(page.textContent("#wmsSyncLog tr[data-sync-run]").contains("WMS не дала данных"),
                "в журнале нет объяснения: " + page.textContent("#wmsSyncLog tr[data-sync-run]"));
        assertNoJsErrors();
    }
}
