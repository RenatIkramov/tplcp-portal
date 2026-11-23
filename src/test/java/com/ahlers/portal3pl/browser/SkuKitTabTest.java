package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вкладка «Состав» в карточке товара-комплекта (V151).
 *
 * <p>Состав проверка задаёт сама — той же функцией, что синхронизация
 * с WMS, — и после возвращает, какой был: на базе разработки состав
 * демо-комплекта может быть любым (или его нет, если демо-данные
 * не перезапускали). Пишет владелец схемы — в журнал это не попадает.
 */
class SkuKitTabTest extends BrowserTestBase {

    private static final String KIT = "RK-KIT-01";

    private String before;
    private Boolean wasKit;

    private String dep() {
        return depositorId(USER, DEPOSITOR);
    }

    private void apply(String kit, String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", dep()).param("kit", kit).param("rows", rows).query(rs -> { });
    }

    @BeforeEach
    void rememberComposition() {
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
        apply(KIT, "[{\"code\": \"RK-ROG-01\", \"qty\": 2}, {\"code\": \"RK-KOP-01\", \"qty\": 4}]");
    }

    @AfterEach
    void restoreComposition() {
        apply(KIT, before);
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :kit")
                .param("k", wasKit).param("kit", KIT).update();
    }

    private void open(String code) {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();
        openSkuList();
        page.evaluate("code => portalOpenSku(code)", code);
        page.waitForSelector("#view-sku-detail.active #skuDetailBody .kv");
    }

    /**
     * «Запасы»: у комплекта строка «можно собрать» с отметкой «комплект»
     * (V152). Комплектующая — товар с годным остатком, по одной на комплект:
     * строка комплекта появится при любых остатках базы разработки.
     */
    @Test
    void stockListMarksTheKit() {
        String component = owner().jdbc.sql("""
                        SELECT s.code FROM dbo.stock_balance b
                          JOIN dbo.sku s ON s.sku_id = b.sku_id
                          JOIN dbo.enum_quality q ON q.id = b.quality_id AND q.code = '01'
                         WHERE s.depositor_id = :dep::uuid AND s.code <> :kit AND b.available_qty >= 1
                           AND NOT EXISTS (SELECT 1 FROM dbo.sku_kit_component k WHERE k.kit_sku_id = s.sku_id)
                         ORDER BY b.available_qty DESC, s.code LIMIT 1
                        """).param("dep", dep()).param("kit", KIT).query(String.class).single();
        apply(KIT, "[{\"code\": \"" + component + "\", \"qty\": 1}]");

        ensurePassword(USER, USER_PASSWORD);
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
        page.evaluate("() => showTab('stock')");
        page.waitForSelector("#filter-stock-code");
        page.evaluate("code => { document.getElementById('filter-stock-code').value = code; onStockFilterChange(); }", KIT);
        page.waitForSelector("#stockBody tr:has-text('" + KIT + "') [data-kit-mark]");
        assertNoJsErrors();
    }

    @Test
    void kitShowsItsComponentsAndOtherItemsHaveNoTab() {
        open(KIT);
        page.click("#skuDetailTabs [data-tab='kit']");
        page.waitForSelector("#skuDetailBody [data-kit-components]");
        assertEquals(2, page.querySelectorAll("#skuDetailBody [data-kit-component]").size(), "не весь состав");
        String row = page.innerText("#skuDetailBody [data-kit-component='RK-KOP-01']");
        assertTrue(row.contains("4"), "у комплектующей не то количество: " + row);

        page.evaluate("() => portalOpenSku('RK-ROG-01')");
        page.waitForFunction("() => document.getElementById('skuDetailTitle').textContent.includes('Рога бараньи сортовые')");
        assertEquals(0, page.querySelectorAll("#skuDetailTabs [data-tab='kit']").size(),
                "у обычного товара вкладка «Состав»");
        assertNoJsErrors();
    }
}
