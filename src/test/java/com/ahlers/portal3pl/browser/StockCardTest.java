package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Карточка остатка из «Запасов»: количества и партии (11.11.2026).
 *
 * <p>Прежде её рисовал прототип, партии подкладывались подменой его
 * функций, а плашка «Есть брак» / «Без замечаний» стояла в коде
 * по-русски. Проверяется, что партий на экране столько, сколько отдал
 * сервер, и что плашка — из каталога текстов на языке человека.
 */
class StockCardTest extends BrowserTestBase {

    @Test
    void stockRowOpensWithItsBatchesInTheUserLanguage() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "en");
        signInUiAsClient();
        page.evaluate("() => showTab('stock')");
        page.waitForFunction("() => Array.isArray(PORTAL.rows) && PORTAL.rows.length > 0");

        @SuppressWarnings("unchecked")
        Map<String, Object> found = (Map<String, Object>) page.evaluate("""
                async () => {
                  for (const r of PORTAL.rows) {
                    const b = await PORTAL.fetchJson('/api/stock/details?skuCode=' + encodeURIComponent(r.code));
                    if (b.length) return {code: r.code, batches: b.length, defect: Number(r.defect) > 0};
                  }
                  return null;
                }
                """);
        assumeTrue(found != null, "у клиента нет товара с партиями");
        String code = (String) found.get("code");

        page.evaluate("c => portalOpenStockRow(c)", code);
        page.waitForSelector("#view-product-detail.active #productDetailBatches tr");
        int rows = ((Number) page.evaluate(
                "() => document.querySelectorAll('#productDetailBatches tr').length")).intValue();
        assertEquals(((Number) found.get("batches")).intValue(), rows, "партий на экране не столько, сколько отдал сервер");

        String badge = (String) page.evaluate("() => document.querySelector('#productDetailHead .badge').textContent.trim()");
        String key = Boolean.TRUE.equals(found.get("defect")) ? "stock.text.has_defects" : "stock.text.no_issues";
        String expected = jdbc.sql("""
                        SELECT t.value FROM dbo.ui_text t
                          JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                         WHERE t.text_key = :key::text AND l.code = 'en'
                        """).param("key", key).query(String.class).single();
        assertEquals(expected, badge, "плашка карточки остатка не из каталога текстов");
        assertTrue(page.innerText("#productDetailHead").contains(code), "в шапке нет кода товара");
        assertNoJsErrors();
    }
}
