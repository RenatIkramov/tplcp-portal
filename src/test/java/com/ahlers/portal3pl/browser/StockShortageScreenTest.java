package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.documents.WmsGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

/**
 * Окно нехватки при подтверждении заказа (V142, решение заказчика
 * 14.11.2026): строки, которым не хватает «Доступно», и кнопка
 * «Проверить актуальные остатки». Без проверки заказ не уходит; WMS
 * подтвердила нехватку — окно остаётся с её числами; товара хватает —
 * заказ уходит сразу.
 */
class StockShortageScreenTest extends BrowserTestBase {

    @Autowired
    DraftService drafts;

    @MockitoSpyBean
    WmsGateway wms;

    private String docId;

    @AfterEach
    void cleanUp() {
        doCallRealMethod().when(wms).freeStock(anyString(), anyString(), anyString(), anyString());
        if (docId != null) {
            jdbc.sql("SELECT dbo.wms_outbox_finish(outbox_id, NULL, NULL) FROM dbo.wms_outbox"
                    + " WHERE doc_id = :d::uuid AND state = 'pending'").param("d", docId).query().listOfRows();
            inContext("DELETE FROM dbo.order_details WHERE doc_id = '" + docId + "'");
            inContext("DELETE FROM dbo.orders WHERE doc_id = '" + docId + "'");
        }
        owner().jdbc.sql("SELECT dbo.stock_rebase_from_details(NULL)").query(rs -> { });
    }

    @SuppressWarnings("unchecked")
    @Test
    void shortageWindowChecksWmsAndSends() {
        signIn();
        String[] item = singleValue("""
                SELECT a.sku_id::text || '|' || s.code || '|' || a.warehouse_id || '|' || a.available_qty::numeric(14,0)
                  FROM dbo.v_stock_available a JOIN dbo.sku s ON s.sku_id = a.sku_id
                 WHERE a.available_qty >= 50 ORDER BY a.available_qty DESC, a.sku_id LIMIT 1""").split("\\|");
        int available = Integer.parseInt(item[3]);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", item[2]);
        head.put("planned_ship_date", "2026-12-01");
        head.put("consignee_id", singleValue("SELECT consignee_id::text FROM dbo.consignee ORDER BY 1 LIMIT 1"));
        docId = ((Map<String, Object>) drafts.create(DocumentType.ORDERS, withErp(head)).get("header"))
                .get("docId").toString();
        drafts.addLine(DocumentType.ORDERS, docId, Map.of("sku_id", item[0], "ordered_qty", available + 3));

        signInUiAsClient();
        page.evaluate("id => PORTAL.draft.open('orders', id)", docId);
        page.waitForFunction("() => (PORTAL.card.doc('orders') || {}).statusCode === '01'");

        // «Подтвердить» — окно со строкой нехватки, документ остался черновиком
        page.evaluate("() => PORTAL.draft.confirm('orders')");
        String row = "#stockShortageOverlay.active tr[data-shortage='" + item[1] + "']";
        page.waitForSelector(row);
        assertTrue(page.textContent(row).contains(String.valueOf(available + 3)), "в окне нет количества документа");
        assertEquals("01", status(), "заказ с нехваткой ушёл без проверки");
        String hint = page.textContent("#stockShortageHint");

        // проверка: заглушка WMS числа не даёт — нехватка подтверждена, окно на месте, текст другой
        page.click("#stockShortageCheck");
        page.waitForFunction("before => document.getElementById('stockShortageHint')"
                + " && document.getElementById('stockShortageHint').textContent !== before", hint);
        assertTrue(page.isVisible(row), "окно закрылось, хотя товара не хватает");
        assertEquals("01", status());

        // на складе товара больше, чем знал портал, — заказ уходит, окно закрыто
        // «свободно» WMS не знает о заказах, которые до неё не дошли (04, 02), — портал вычтет их сам
        BigDecimal notInWms = new BigDecimal(singleValue("""
                SELECT COALESCE(sum(COALESCE(d.ordered_qty_wms, d.ordered_qty, 0)), 0)::text
                  FROM dbo.orders o JOIN dbo.enum_status st ON st.id = o.status_id
                  JOIN dbo.order_details d ON d.doc_id = o.doc_id
                 WHERE st.code IN ('04', '02') AND d.batch_number IS NULL AND o.doc_id <> '%s'::uuid
                   AND d.sku_id = '%s'::uuid AND o.warehouse_id = '%s'""".formatted(docId, item[0], item[2])));
        doReturn(notInWms.add(new BigDecimal(available + 10))).when(wms).freeStock(anyString(), anyString(), anyString(), anyString());
        page.click("#stockShortageCheck");
        page.waitForFunction("() => (PORTAL.card.doc('orders') || {}).statusCode === '02'");
        assertTrue(page.isHidden("#stockShortageOverlay.active"), "окно нехватки осталось после отправки");
        assertEquals("02", status());
        assertNoJsErrors();
    }

    private String status() {
        return singleValue("SELECT st.code FROM dbo.orders o JOIN dbo.enum_status st ON st.id = o.status_id"
                + " WHERE o.doc_id = '" + docId + "'");
    }
}
