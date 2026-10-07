package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.stock.StockLine;
import com.ahlers.portal3pl.stock.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чтение: запасы, документы, справочники. Работает против локальной базы
 * tplcp с загруженными демо-данными.
 */
class ReadApiTest extends PortalTestBase {

    @Autowired
    StockService stock;

    @Autowired
    DocumentService documents;

    @Autowired
    CatalogService catalog;

    @Autowired
    AuthService auth;

    // ---------- Запасы ----------

    @Test
    void depositorStock() {
        signIn();
        List<StockLine> rows = stock.list(null, null);
        assertEquals(9, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "RK-ROG-01".equals(r.skuCode())));
    }

    @Test
    void stockFilters() {
        signIn();
        assertEquals(8, stock.list("WH-ALA", null).size(), "только склад Алматы");
        assertEquals(3, stock.list(null, "рога").size(), "поиск по наименованию");
    }

    @Test
    void differentDepositorsSeeDifferentData() {
        signIn();
        int ownCount = stock.list(null, null).size();
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        int otherCount = stock.list(null, null).size();
        assertEquals(9, ownCount);
        assertEquals(4, otherCount);
    }

    /** §6.4: вошёл, но поклажедателя не выбрал — данные не отдаются (409). */
    @Test
    void noDepositorNoData() {
        signInAs(USER, null);
        expectRejection(409, () -> stock.list(null, null));
    }

    /** А без входа вообще — 401, независимо от поклажедателя. */
    @Test
    void noLoginNoData() {
        expectRejection(401, () -> stock.list(null, null));
    }

    /**
     * Чужой поклажедатель отсекается при ВЫБОРЕ, в сессии, а не на чтении:
     * политика RLS отдаёт то, что выставлено в контексте запроса, и о
     * пользователе ничего не знает. Поэтому проверяем здесь именно проверку
     * доступа — подставить чужой идентификатор в обход сессии из браузера
     * невозможно, он больше не передаётся ни параметром, ни заголовком.
     */
    @Test
    void foreignDepositorRejectedOnSelect() {
        String foreignId = depositorId(OTHER_USER, OTHER_DEPOSITOR);
        assertFalse(auth.mayAccess(USER, foreignId),
                "клиенту «Рога и Копыта» недоступны «Молочные реки»");
        assertTrue(auth.mayAccess(OTHER_USER, foreignId), "а их собственному пользователю — доступны");
    }

    @Test
    void batchDetails() {
        signIn();
        List<Map<String, Object>> batches = stock.details("RK-ROG-01", null);
        assertEquals(2, batches.size());
        assertEquals("П-2609-А", batches.getFirst().get("batchNumber"));
    }

    // ---------- Документы ----------

    /**
     * Списки отдают документы поклажедателя.
     * <p>
     * Проверяем наличие демо-документов, а не их точное число: в базу
     * разработки добавляются черновики — и руками через портал, и тестами
     * черновиков. Тест, падающий от каждого созданного документа, ловит
     * не ошибку, а факт работы с порталом.
     */
    @Test
    void documentLists() {
        signIn();
        assertTrue(documentNumbers(DocumentType.RECEIPTS).containsAll(
                        List.of("ПН-000148", "ПН-000151", "ПН-000152")),
                "в списке поступлений есть демо-документы");
        assertTrue(documentNumbers(DocumentType.ORDERS).containsAll(
                        List.of("ЗО-000322", "ЗО-000329", "ЗО-000331")),
                "в списке заказов есть демо-документы");
        assertFalse(documentNumbers(DocumentType.TRANSFERS).isEmpty(), "перемещения отдаются");
    }

    private List<String> documentNumbers(DocumentType type) {
        return documents.list(type, null, null, null, null, null).stream()
                .map(r -> String.valueOf(r.get("documentNo")))
                .toList();
    }

    @Test
    void documentFilters() {
        signIn();
        List<Map<String, Object>> received =
                documents.list(DocumentType.RECEIPTS, null, "received", null, null, null);
        assertEquals(1, received.size());
        assertEquals(RECEIPT_NO, received.getFirst().get("documentNo"));

        assertEquals(1, documents.list(DocumentType.RECEIPTS, "козлов", null, null, null, null).size());
    }

    @Test
    void foreignDocumentsHidden() {
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(0, documents.list(DocumentType.RECEIPTS, null, null, null, null, null).size());
    }

    /**
     * Ключевая проверка решения Р1: строка 1 принята двумя партиями, в составе
     * документа это одна строка (план 1000, факт 980), в детализации — две записи.
     */
    @Test
    void documentLinesAggregateBatches() {
        signIn();
        String docId = receiptId();

        List<Map<String, Object>> lines = documents.lines(DocumentType.RECEIPTS, docId);
        assertEquals(2, lines.size());
        Map<String, Object> line1 = lines.getFirst();
        assertEquals(1000, asLong(line1.get("expectedQty")));
        assertEquals(980, asLong(line1.get("acceptedQty")));
        assertEquals(2, asLong(line1.get("batchCount")));

        assertEquals(2, documents.lineDetails(DocumentType.RECEIPTS, docId, 1).size());
        assertEquals(3, documents.lineDetails(DocumentType.RECEIPTS, docId, null).size());
    }

    @Test
    void documentCard() {
        signIn();
        Map<String, Object> card = documents.card(DocumentType.RECEIPTS, receiptId());
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals(RECEIPT_NO, header.get("documentNo"));
        assertEquals(2, ((List<?>) card.get("lines")).size());
        assertEquals(3, ((List<?>) card.get("lineDetails")).size());
    }

    /**
     * Чужой документ неотличим от несуществующего — намеренно: иначе по коду
     * ответа можно было бы перебором узнать, какие документы есть у других.
     */
    @Test
    void foreignDocumentNotFound() {
        signIn();
        String docId = receiptId();
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        expectRejection(404, () -> documents.card(DocumentType.RECEIPTS, docId));
    }

    // ---------- Справочники ----------

    @Test
    void skuAndFilters() {
        signIn();
        assertEquals(8, catalog.list(CatalogType.SKU, null, null, null, false).size());
        assertEquals(3, catalog.list(CatalogType.SKU, "рога", null, null, false).size());
        assertEquals(2, catalog.list(CatalogType.SKU, null, "Мясная продукция", null, false).size());

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(4, catalog.list(CatalogType.SKU, null, null, null, false).size());
    }

    @Test
    void catalogsAndBilling() {
        signIn();
        assertEquals(2, catalog.list(CatalogType.SUPPLIERS, null, null, null, false).size());
        assertEquals(3, catalog.list(CatalogType.CONSIGNEES, null, null, null, false).size());
        assertEquals(2, catalog.list(CatalogType.WAREHOUSES, null, null, null, false).size());
        assertEquals(7, catalog.list(CatalogType.SERVICE_TARIFFS, null, null, null, false).size());

        List<Map<String, Object>> invoices = catalog.list(CatalogType.INVOICES, null, null, null, false);
        assertEquals(4, invoices.size());
        assertNotNull(invoices.getFirst().get("amountWithVat"), "сумма с НДС рассчитана в представлении");

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(0, catalog.list(CatalogType.INVOICES, null, null, null, false).size());
    }

    // ---------- вспомогательное ----------

    /** Поступление из демо-данных, на котором проверяются карточка и состав. */
    private static final String RECEIPT_NO = "ПН-000148";

    private String receiptId() {
        return documents.list(DocumentType.RECEIPTS, RECEIPT_NO, null, null, null, null)
                .getFirst().get("docId").toString();
    }

    private static long asLong(Object v) {
        return v == null ? 0 : (long) Double.parseDouble(v.toString());
    }
}
