package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Отправить ещё раз» у документа с ошибкой отправки (аудит 11.11.2026, A1; V127).
 *
 * <p>Прежде документ в «Ошибке отправки» застревал: править нельзя, кнопки
 * повтора нет. Ошибку здесь ставит проверка прямо в базе — заглушка WMS
 * не падает никогда, а как документ в неё попадает, проверяет WmsOutboxTest.
 */
class SendAgainScreenTest extends BrowserTestBase {

    @Autowired
    DraftService drafts;

    private String docId;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> header(Map<String, Object> card) {
        return (Map<String, Object>) card.get("header");
    }

    @Test
    void failedDocumentCanBeSentAgain() {
        signIn();
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", "WH-ALA");
        head.put("eta_date", "2026-11-05");
        head.put("supplier_id", singleValue("SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1"));
        docId = header(drafts.create(DocumentType.RECEIPTS, withErp(head))).get("docId").toString();
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id",
                singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'"), "expected_qty", 5));
        inContext("UPDATE dbo.receipts SET status_id = (SELECT id FROM dbo.enum_status WHERE code = '03')"
                + " WHERE doc_id = '" + docId + "'");

        signInUiAsClient();
        page.evaluate("id => PORTAL.draft.open('receipts', id)", docId);
        page.waitForFunction("() => (PORTAL.card.doc('receipts') || {}).statusCode === '03'");
        assertTrue(page.isVisible("#receiptResendActions button"), "у ошибки отправки нет кнопки повтора");
        assertFalse(page.isVisible("#receiptDraftActions"), "ошибку отправки можно править как черновик");

        page.click("#receiptResendActions button");
        page.waitForFunction("() => (PORTAL.card.doc('receipts') || {}).statusCode === '02'");
        assertFalse(page.isVisible("#receiptResendActions"), "кнопка повтора осталась у отправленного");
        assertEquals("02", singleValue("SELECT st.code FROM dbo.receipts r JOIN dbo.enum_status st"
                + " ON st.id = r.status_id WHERE r.doc_id = '" + docId + "'"));
        assertNoJsErrors();
    }

    @AfterEach
    void cleanUp() {
        if (docId != null) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'");
        }
    }
}
