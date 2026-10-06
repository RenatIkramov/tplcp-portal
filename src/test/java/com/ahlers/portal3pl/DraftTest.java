package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Черновики документов: создание, правка, подтверждение (этап 7, решение Р5).
 * <p>
 * Тесты создают документы в базе и убирают их за собой, чтобы не ломать
 * проверки, которые опираются на количество демо-документов.
 */
class DraftTest extends PortalTestBase {

    @Autowired
    DraftService drafts;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeCreated() {
        for (String docId : created) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
    }

    private Map<String, Object> draft(Map<String, Object> header) {
        Map<String, Object> card = drafts.create(DocumentType.RECEIPTS, header);
        created.add(header(card).get("docId").toString());
        return card;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> header(Map<String, Object> card) {
        return (Map<String, Object>) card.get("header");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> card) {
        return (List<Map<String, Object>>) card.get("lines");
    }

    @SuppressWarnings("unchecked")
    private static List<String> problems(Map<String, Object> card) {
        return (List<String>) card.get("problems");
    }

    @Test
    void draftCreatedWithNumberAndNoLines() {
        signIn();
        Map<String, Object> card = draft(Map.of());
        Map<String, Object> h = header(card);

        assertTrue(h.get("documentNo").toString().startsWith("ПН-"), "портал присваивает свой номер");
        assertEquals("draft", h.get("statusCode"));
        assertEquals("portal", h.get("origin"), "владелец черновика — портал");
        assertEquals(0, rows(card).size());
        assertFalse(problems(card).isEmpty(), "пустой черновик отправить нельзя");
    }

    @Test
    void headerAndLinesEditable() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();

        Map<String, Object> card = drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of(
                "warehouse_id", "WH-ALA",
                "eta_date", "2026-11-01",
                "erp_number", "ERP-TEST-77",
                "comment", "тестовый черновик"));
        assertEquals("ERP-TEST-77", header(card).get("erpNumber"));
        assertEquals("Склад Алматы", header(card).get("warehouseName"));

        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        card = drafts.addLine(DocumentType.RECEIPTS, docId, Map.of(
                "sku_id", skuId, "expected_qty", 120, "price", 2500, "currency_id", 1));
        assertEquals(1, rows(card).size());
        assertEquals("RK-ROG-01", rows(card).getFirst().get("skuCode"));

        card = drafts.updateLine(DocumentType.RECEIPTS, docId, 1, Map.of("expected_qty", 150));
        assertEquals(150, (long) Double.parseDouble(rows(card).getFirst().get("expectedQty").toString()));

        card = drafts.deleteLines(DocumentType.RECEIPTS, docId, List.of(1));
        assertEquals(0, rows(card).size());
    }

    @Test
    void quantityMustBePositive() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");

        expectRejection(400, () -> drafts.addLine(DocumentType.RECEIPTS, docId,
                Map.of("sku_id", skuId, "expected_qty", 0)));
    }

    @Test
    void cannotConfirmWithoutRequiredFields() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();

        List<String> problems = drafts.validateForConfirm(DocumentType.RECEIPTS, docId);
        assertFalse(problems.isEmpty());
        expectRejection(422, () -> drafts.confirm(DocumentType.RECEIPTS, docId));
    }

    @Test
    void filledDraftConfirmedAndLocked() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String supplierId = singleValue(
                "SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");

        String docId = header(draft(Map.of(
                "warehouse_id", "WH-ALA", "eta_date", "2026-11-05"))).get("docId").toString();
        drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of("supplier_id", supplierId));
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", skuId, "expected_qty", 80));

        assertTrue(drafts.validateForConfirm(DocumentType.RECEIPTS, docId).isEmpty(),
                "все условия выполнены");

        Map<String, Object> card = drafts.confirm(DocumentType.RECEIPTS, docId);
        assertEquals("sent_to_wms", header(card).get("statusCode"));

        // после отправки документ из портала не редактируется (Р5)
        expectRejection(409, () -> drafts.updateHeader(DocumentType.RECEIPTS, docId,
                Map.of("comment", "поздняя правка")));
        expectRejection(409, () -> drafts.delete(DocumentType.RECEIPTS, docId));
    }

    @Test
    void skuUnknownToWmsBlocksConfirm() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-KOP-01'");
        String supplierId = singleValue(
                "SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");

        inContext("UPDATE dbo.sku SET is_in_wms = false WHERE code = 'RK-KOP-01'");
        try {
            String docId = header(draft(Map.of(
                    "warehouse_id", "WH-ALA", "eta_date", "2026-11-07"))).get("docId").toString();
            drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of("supplier_id", supplierId));
            Map<String, Object> card = drafts.addLine(DocumentType.RECEIPTS, docId,
                    Map.of("sku_id", skuId, "expected_qty", 50));

            assertEquals(Boolean.TRUE, header(card).get("hasUnknownSku"));
            assertTrue(problems(card).stream().anyMatch(p -> p.contains("неизвестные WMS")));
            expectRejection(422, () -> drafts.confirm(DocumentType.RECEIPTS, docId));
        } finally {
            inContext("UPDATE dbo.sku SET is_in_wms = true WHERE code = 'RK-KOP-01'");
        }
    }

    @Test
    void draftDeleted() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        drafts.delete(DocumentType.RECEIPTS, docId);
        expectRejection(404, () -> drafts.card(DocumentType.RECEIPTS, docId));
    }

    @Test
    void transportNotEditableInDraft() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        // WMS о черновике не знает — связывать транспорт не с чем (Р5)
        expectRejection(409, () -> drafts.updateTransport(DocumentType.RECEIPTS, docId,
                Map.of("carrier", "ТОО «КазТранс»", "car_number", "999 ZZZ 02")));
    }

    @Test
    void transportEditableWhenAwaiting() {
        signIn();
        String docId = singleValue(
                "SELECT doc_id::text FROM dbo.receipts WHERE document_no = 'ПН-000151'");

        Map<String, Object> card = drafts.updateTransport(DocumentType.RECEIPTS, docId,
                Map.of("carrier", "ТОО «КазТранс»", "car_number", "777 AAA 02"));
        assertEquals("777 AAA 02", header(card).get("carNumber"));
    }
}
