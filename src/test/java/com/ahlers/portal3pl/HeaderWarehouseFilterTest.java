package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.stock.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Склад из шапки портала отбирает списки на сервере (V94): параметр
 * {@code warehouse} у поступлений, заказов, перемещений и запасов.
 */
class HeaderWarehouseFilterTest extends PortalTestBase {

    @Autowired
    DocumentService documents;

    @Autowired
    StockService stock;

    private PortalUser user() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }

    @Test
    void receiptsAndStockKeepOnlyTheChosenWarehouse() {
        PortalUser user = user();
        List<Map<String, Object>> all = listRows(documents.list(user, DocumentType.RECEIPTS,
                Map.of("pageSize", "200")));
        List<Map<String, Object>> almaty = listRows(documents.list(user, DocumentType.RECEIPTS,
                Map.of("pageSize", "200", "warehouse", "WH-ALA")));
        assertFalse(almaty.isEmpty());
        assertTrue(almaty.size() < all.size(), "склад отбирает, а не показывает всё");
        assertTrue(almaty.stream().allMatch(r -> "WH-ALA".equals(r.get("warehouseId"))));

        List<Map<String, Object>> rows = listRows(stock.list(user, Map.of("pageSize", "200", "warehouse", "WH-KGD")));
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(r -> "WH-KGD".equals(r.get("warehouseId"))));
    }

    /** Перемещение подходит, если склад — отправитель или получатель. */
    @Test
    void transferMatchesEitherEnd() {
        PortalUser user = user();
        List<Map<String, Object>> karaganda = listRows(documents.list(user, DocumentType.TRANSFERS,
                Map.of("pageSize", "200", "warehouse", "WH-KGD")));
        assertTrue(karaganda.stream().allMatch(r -> "WH-KGD".equals(r.get("fromWarehouseId"))
                || "WH-KGD".equals(r.get("toWarehouseId"))));
        assertTrue(karaganda.stream().anyMatch(r -> "WH-ALA".equals(r.get("fromWarehouseId"))),
                "перемещение из Алматы в Караганду видно по Караганде");
        String total = singleValue("SELECT count(*)::text FROM dbo.transfers"
                + " WHERE from_warehouse_id = 'WH-KGD' OR to_warehouse_id = 'WH-KGD'");
        assertEquals(Integer.parseInt(total), karaganda.size());
    }

    @Test
    void emptyWarehouseMeansAll() {
        PortalUser user = user();
        assertEquals(listTotal(documents.list(user, DocumentType.ORDERS, Map.of())),
                listTotal(documents.list(user, DocumentType.ORDERS, Map.of("warehouse", ""))));
    }
}
