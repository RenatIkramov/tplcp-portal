package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Окно строки заказа: «можно собрать» у комплекта — свежее при каждом
 * открытии окна (22.11.2026).
 *
 * <p>Прежде окно запоминало остатки до выхода из портала: состав
 * поменяли — окно показывало прежнее число. Состав проверка задаёт сама
 * и после возвращает прежний.
 */
class KitOrderStockTest extends BrowserTestBase {

    private static final String KIT = "RK-KIT-01";
    private static final String WAREHOUSE = "WH-ALA";

    private String before;
    private Boolean wasKit;

    private void compose(String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", depositorId(USER, DEPOSITOR)).param("kit", KIT).param("rows", rows).query(rs -> { });
    }

    /** Комплектующая с годным остатком на складе — по одной на комплект. */
    private String component() {
        return owner().jdbc.sql("""
                        SELECT s.code FROM dbo.stock_balance b
                          JOIN dbo.sku s ON s.sku_id = b.sku_id
                          JOIN dbo.enum_quality q ON q.id = b.quality_id AND q.code = '01'
                         WHERE s.depositor_id = :dep::uuid AND s.code <> :kit AND b.warehouse_id = :wh
                           AND b.available_qty >= 4
                           AND NOT EXISTS (SELECT 1 FROM dbo.sku_kit_component k WHERE k.kit_sku_id = s.sku_id)
                         ORDER BY b.available_qty DESC, s.code LIMIT 1
                        """).param("dep", depositorId(USER, DEPOSITOR)).param("kit", KIT).param("wh", WAREHOUSE)
                .query(String.class).single();
    }

    @BeforeEach
    void remember() {
        before = owner().jdbc.sql("""
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('code', c.code, 'qty', k.qty)
                                                  ORDER BY k.sort_order), '[]')::text
                          FROM dbo.sku_kit_component k
                          JOIN dbo.sku s ON s.sku_id = k.kit_sku_id
                          JOIN dbo.sku c ON c.sku_id = k.component_sku_id
                         WHERE s.code = :kit
                        """).param("kit", KIT).query(String.class).single();
        wasKit = owner().jdbc.sql("SELECT is_kit FROM dbo.sku WHERE code = :kit").param("kit", KIT)
                .query(Boolean.class).optional().orElse(null);
    }

    @AfterEach
    void restore() {
        compose(before);
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :kit").param("k", wasKit).param("kit", KIT).update();
    }

    private String availableInTheWindow() {
        page.evaluate("() => PORTAL.lineModal.add('orders')");
        page.waitForFunction("() => !!document.querySelector('[id^=orderLineEdit].active, #orderLineEditOverlay.active')"
                + " || document.getElementById('orderLineEditStockAvail') !== null");
        page.evaluate("code => PORTAL.lineModal.pick('orders', code)", KIT);
        page.waitForFunction("() => document.getElementById('orderLineEditStockAvail').textContent.trim() !== '—'");
        String text = page.innerText("#orderLineEditStockAvail").trim();
        page.evaluate("() => PORTAL.lineModal.close('orders')");
        return text;
    }

    @Test
    void canAssembleIsFreshEachTimeTheWindowOpens() {
        String part = component();
        compose("[{\"code\": \"" + part + "\", \"qty\": 1}]");

        ensurePassword(USER, USER_PASSWORD);
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('orders')");
        page.waitForFunction("() => !!PORTAL.card.doc('orders')");
        page.evaluate("wh => { const f = document.getElementById('of_warehouse'); f.value = wh;"
                + " f.dispatchEvent(new Event('change', {bubbles: true})); }", WAREHOUSE);

        String first = availableInTheWindow();
        compose("[{\"code\": \"" + part + "\", \"qty\": 2}]");      // собрать можно вдвое меньше
        String second = availableInTheWindow();

        String expected = owner().jdbc.sql("""
                        SELECT trim(to_char(available_qty, 'FM999999990.###'), '.') FROM dbo.v_stock_lines
                         WHERE sku_code = :kit AND warehouse_id = :wh
                        """).param("kit", KIT).param("wh", WAREHOUSE).query(String.class).single();
        assertNotEquals(first, second, "окно показало прежнее «можно собрать»");
        assertEquals(expected, second.replaceAll("\\s.*$", ""), "в окне не то «можно собрать»");
        assertNoJsErrors();
    }
}
