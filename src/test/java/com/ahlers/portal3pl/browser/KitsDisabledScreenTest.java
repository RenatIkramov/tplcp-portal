package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Экраны при выключенных комплектах (V155) и галочка в карточке клиента.
 *
 * <p>Галочку клиента D-001 и состав демо-комплекта проверка запоминает
 * и после возвращает.
 */
class KitsDisabledScreenTest extends BrowserTestBase {

    private static final String KIT = "RK-KIT-01";

    private boolean before;
    private String composition;
    private Boolean wasKit;

    private String dep() {
        return depositorId(USER, DEPOSITOR);
    }

    private void setKits(boolean on) {
        owner().jdbc.sql("SELECT dbo.admin_save_depositor_kits(:d::uuid, :on)")
                .param("d", dep()).param("on", on).query(rs -> { });
    }

    private void compose(String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", dep()).param("kit", KIT).param("rows", rows).query(rs -> { });
    }

    @BeforeEach
    void remember() {
        before = owner().jdbc.sql("SELECT kits_enabled FROM dbo.depositor_settings_of(:d::uuid)")
                .param("d", dep()).query(Boolean.class).single();
        composition = owner().jdbc.sql("""
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('code', c.code, 'qty', k.qty)
                                                  ORDER BY k.sort_order), '[]')::text
                          FROM dbo.sku_kit_component k
                          JOIN dbo.sku s ON s.sku_id = k.kit_sku_id
                          JOIN dbo.sku c ON c.sku_id = k.component_sku_id
                         WHERE s.code = :kit
                        """).param("kit", KIT).query(String.class).single();
        wasKit = owner().jdbc.sql("SELECT is_kit FROM dbo.sku WHERE code = :kit").param("kit", KIT)
                .query(Boolean.class).optional().orElse(null);
        compose("[{\"code\": \"RK-ROG-01\", \"qty\": 2}]");
    }

    @AfterEach
    void restore() {
        setKits(before);
        compose(composition);
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :kit").param("k", wasKit).param("kit", KIT).update();
    }

    /** Выключено: нет «Комплектов», «Состава», признака «Комплект» и отметки в «Запасах». */
    @Test
    void clientWithoutKitsSeesNoKitScreens() {
        setKits(false);
        ensurePassword(USER, USER_PASSWORD);
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
        openSkuList();
        assertFalse(page.isVisible("#view-references [data-refsub='kits']"), "подвкладка «Комплекты» видна");

        page.evaluate("code => portalOpenSku(code)", KIT);
        page.waitForSelector("#view-sku-detail.active #skuDetailBody .kv");
        assertEquals(0, page.querySelectorAll("#skuDetailTabs [data-tab='kit']").size(), "вкладка «Состав» видна");
        page.evaluate("() => PORTAL.skuCard.tab('attributes')");
        page.waitForSelector("#skuDetailBody .kv label");
        assertEquals(Boolean.FALSE, page.evaluate("() => [...document.querySelectorAll('#skuDetailBody .kv label')]"
                + ".some(l => l.textContent.trim() === T('col.sku.isKit'))"), "признак «Комплект» в карточке");

        page.evaluate("() => showTab('stock')");
        page.waitForSelector("#filter-stock-code");
        page.evaluate("code => { document.getElementById('filter-stock-code').value = code; onStockFilterChange(); }", KIT);
        page.waitForSelector("#stockBody tr:has-text('" + KIT + "')");
        assertEquals(0, page.querySelectorAll("#stockBody [data-kit-mark]").size(), "отметка «комплект» в «Запасах»");
        assertNoJsErrors();
    }

    /** Администратор снимает галочку в карточке клиента — подвкладка уходит сразу. */
    @Test
    void adminTurnsKitsOffInTheDepositorCard() {
        setKits(true);
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("id => portalSelectDepositor(id)", dep());
        waitForQuiet();
        assertTrue(page.evaluate("() => PORTAL.kitsEnabled()").equals(Boolean.TRUE));

        page.evaluate("id => portalOpenPortalDir('depositorsDir', id)", dep());
        page.waitForSelector("#view-portaldir-detail.active #portalDirTabs [data-tab='sections']");
        page.click("#portalDirTabs [data-tab='sections']");
        page.waitForSelector("#portalDirBody #depVisKits");
        assertTrue(page.isChecked("#depVisKits"));
        page.click("#depVisKits");
        page.click("#portalDirBody [data-dep-save]");
        page.waitForFunction("() => PORTAL.kitsEnabled() === false");
        assertEquals(false, owner().jdbc.sql("SELECT kits_enabled FROM dbo.depositor_settings_of(:d::uuid)")
                .param("d", dep()).query(Boolean.class).single(), "галочка не сохранилась");

        page.evaluate("() => showTab('references')");
        assertFalse(page.isVisible("#view-references [data-refsub='kits']"), "подвкладка «Комплекты» осталась");
        assertNoJsErrors();
    }
}
