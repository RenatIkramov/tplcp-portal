package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Склад в шапке портала (V94): список — из справочника складов,
 * выбор отбирает списки на сервере и подставляется в новый черновик.
 */
class HeaderWarehouseTest extends BrowserTestBase {

    @Test
    void headerListComesFromTheDirectoryAndFiltersLists() {
        signInUiAsClient();
        page.waitForFunction("() => PORTAL.warehouses && PORTAL.warehouses.length > 1");

        page.evaluate("() => toggleWarehouseSwitcher()");
        @SuppressWarnings("unchecked")
        List<String> shown = (List<String>) page.evaluate("() => Array.from(document.querySelectorAll("
                + "'#warehouseSwitcher [data-warehouse]')).map(e => e.textContent.replace('✓', '').trim())");
        // Ожидаемое — тем же правилом, что и сервер: склады поклажедателя.
        // Таблицы под RLS, поэтому читаем в его контексте.
        String depositorId = depositorId(USER, DEPOSITOR);
        List<String> expected = new org.springframework.transaction.support.TransactionTemplate(txManager)
                .execute(tx -> {
                    jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                            .param("id", depositorId).query(String.class).single();
                    return jdbc.sql("SELECT name FROM dbo.v_warehouses ORDER BY name")
                            .query(String.class).list();
                });
        assertEquals(expected, shown.subList(1, shown.size()), "склады в шапке — из справочника");

        // Выбор склада уходит в запрос списка и отбирает на сервере
        page.evaluate("() => showTab('receipts')");
        var response = page.waitForResponse(r -> r.url().contains("/api/receipts")
                        && r.url().contains("warehouse=WH-ALA"),
                () -> page.evaluate("() => setWarehouseFilter('WH-ALA')"));
        assertEquals(200, response.status());
        String body = response.text();
        assertTrue(body.contains("\"warehouseId\":\"WH-ALA\""), "в ответе есть строки склада");
        assertTrue(!body.contains("\"warehouseId\":\"WH-KGD\""), "в ответе только выбранный склад");

        // Новый черновик получает выбранный склад
        page.evaluate("() => PORTAL.draft.create('receipts')");
        page.waitForFunction("() => !!document.getElementById('rf_warehouse')");
        assertEquals("WH-ALA", page.inputValue("#rf_warehouse"));
        page.evaluate("() => setWarehouseFilter('')");
        assertNoJsErrors();
    }
}
