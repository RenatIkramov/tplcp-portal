package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.documents.WmsGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * Транспорт не дошёл до WMS (V84).
 *
 * <p>Отправка в WMS пока заглушка и не падает никогда, поэтому неудачу
 * здесь изображает подменённый {@link WmsGateway}. Проверяется главное
 * правило решения Р5: документ, который ушёл, остаётся отправленным —
 * откатывать заказ в черновик из-за водителя нельзя, — а у транспорта
 * видна причина, и его можно отправить ещё раз.
 *
 * <p>Своим классом, а не в {@code DraftTest}: подмена бина заводит
 * отдельный контекст Spring, и остальным проверкам он не нужен.
 */
class TransportDeliveryTest extends PortalTestBase {

    @Autowired
    DraftService drafts;

    @MockitoSpyBean
    WmsGateway wms;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeCreated() {
        for (String docId : created) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> header(Map<String, Object> card) {
        return (Map<String, Object>) card.get("header");
    }

    private String readyDraftWithTransport() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String supplierId = singleValue(
                "SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", "WH-ALA");
        head.put("eta_date", "2026-11-05");
        head.put("supplier_id", supplierId);
        head.put("car_number", "555 FAIL 02");
        String docId = header(drafts.create(DocumentType.RECEIPTS, withErp(head))).get("docId").toString();
        created.add(docId);
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", skuId, "expected_qty", 5));
        return docId;
    }

    @Test
    void failedTransportLeavesTheDocumentSentAndCanBeResent() {
        String docId = readyDraftWithTransport();
        doThrow(new IllegalStateException("WMS не ответила"))
                .when(wms).sendTransport(any(DocumentType.class), anyString());

        Map<String, Object> sent = header(drafts.confirm(DocumentType.RECEIPTS, docId));
        assertEquals("02", sent.get("statusCode"), "документ откатился из-за транспорта");
        assertEquals("02", sent.get("transportState"));
        assertTrue(String.valueOf(sent.get("transportError")).contains("WMS не ответила"),
                "причина не сохранилась: " + sent.get("transportError"));

        // Повтор, пока WMS всё ещё не отвечает: состояние то же, данные на месте
        Map<String, Object> again = header(drafts.resendTransport(DocumentType.RECEIPTS, docId));
        assertEquals("02", again.get("transportState"));
        assertEquals("555 FAIL 02", again.get("carNumber"));

        // WMS ожила — повтор проходит, причина стирается
        doCallRealMethod().when(wms).sendTransport(any(DocumentType.class), anyString());
        Map<String, Object> fixed = header(drafts.resendTransport(DocumentType.RECEIPTS, docId));
        assertEquals("01", fixed.get("transportState"));
        assertNull(fixed.get("transportError"));
    }
}
