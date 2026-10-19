package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
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
 * Ожидаемые поступления в запасах (V61).
 *
 * <p>Два правила, которые раньше не выполнялись.
 *
 * <p>Первое: «Доступно» и «Ожидается» считаются в ОДНОМ месте (§8.3.2).
 * Подзапрос «ожидается» был скопирован в два представления, а «доступно»
 * выводилось в них по разным правилам. Числа совпадали, но разойтись
 * могли в любой момент, и никто бы не узнал, какое из двух верное.
 *
 * <p>Второе: товар с нулевым остатком и ожидаемым поступлением не был
 * виден никак. Представления строились от таблицы остатков, и «ожидается
 * 200» по складу, где товара пока нет, не показывалось — а это как раз
 * тот случай, когда число важнее всего.
 *
 * <p>Проверки не опираются на состав демо-данных: пара «товар + склад без
 * его остатка» ищется запросом, а поступление заводится и убирается здесь
 * же. Тест, ждущий заранее известной позиции, падал бы от любой правки
 * справочника руками — на этом в проекте уже спотыкались.
 */
class StockIncomingTest extends PortalTestBase {

    @Autowired
    StockService stock;

    @Autowired
    DraftService drafts;

    /**
     * Представления согласованы: по каждой паре «товар + склад» оба дают
     * одно и то же «доступно» и «ожидается».
     *
     * <p>Сравниваются не числа с константами, а два представления между
     * собой: пока расчёт в одном месте, расхождений быть не может.
     * Появятся — значит правило снова размножилось.
     */
    @Test
    void bothViewsAgreeOnAvailableAndIncoming() {
        signIn();
        String mismatch = singleValue("""
                SELECT COALESCE(string_agg(sku_id::text || '/' || warehouse_id, ', '), '')
                  FROM (
                    SELECT l.sku_id, l.warehouse_id
                      FROM dbo.v_stock_lines l
                      JOIN dbo.v_stock_available a
                        ON a.sku_id = l.sku_id AND a.warehouse_id = l.warehouse_id
                     WHERE l.available_qty <> a.available_qty
                        OR l.incoming_qty <> a.incoming_qty
                  ) x
                """);
        assertEquals("", mismatch,
                "представления расходятся по «доступно» или «ожидается»: " + mismatch);
    }

    /**
     * Склад, на котором товара нет, но куда он едет, в запасах виден.
     *
     * <p>Поступление доводится до статуса «отправлен в WMS»: черновик
     * не считается, для склада документа ещё нет.
     */
    @Test
    void warehouseWithoutStockButWithIncomingIsVisible() {
        signIn();
        String[] pair = pairWithoutStock();
        String skuCode = pair[0];
        String warehouseId = pair[1];

        assertTrue(linesFor(skuCode, warehouseId).isEmpty(),
                "до поступления этой пары «товар + склад» в запасах быть не должно");

        String docId = createSentReceipt(skuCode, warehouseId, 200);
        try {
            List<StockLine> rows = linesFor(skuCode, warehouseId);
            assertFalse(rows.isEmpty(), "товар с ожидаемым поступлением не появился в запасах");
            StockLine row = rows.getFirst();
            assertEquals(0, row.quantity().intValue(),
                    "остатка нет, и это ноль, а не пропуск строки");
            assertEquals(200, row.incomingQty().intValue(), "ожидаемое количество не то");
            assertEquals(0, row.batchCount(), "партий нет");
            assertNotNull(row.unitName(),
                    "единицу измерения тут можно взять только из карточки товара — она пуста");
        } finally {
            removeReceipt(docId);
        }
    }

    /** Черновик не считается ожидаемым: для склада документа ещё нет. */
    @Test
    void draftReceiptIsNotCountedAsIncoming() {
        signIn();
        String[] pair = pairWithoutStock();
        String skuCode = pair[0];
        String warehouseId = pair[1];

        Map<String, Object> card = drafts.create(DocumentType.RECEIPTS,
                Map.of("warehouse_id", warehouseId));
        String docId = docIdOf(card);
        try {
            drafts.addLine(DocumentType.RECEIPTS, docId,
                    Map.of("sku_id", skuIdOf(skuCode), "expected_qty", 150));
            assertTrue(linesFor(skuCode, warehouseId).isEmpty(),
                    "черновик попал в ожидаемые поступления");
        } finally {
            drafts.delete(DocumentType.RECEIPTS, docId);
        }
    }

    /**
     * Пара «товар и склад, на котором его нет» — та, на которой раньше
     * ломалось. Товар берётся свой, склад — из выданных клиенту: иначе
     * поступление создать не выйдет.
     */
    private String[] pairWithoutStock() {
        String found = singleValue("""
                SELECT s.code || '|' || dw.warehouse_id
                  FROM dbo.sku s
                  JOIN dbo.depositor_warehouse dw ON dw.depositor_id = s.depositor_id
                 WHERE s.is_active
                   AND NOT EXISTS (SELECT 1 FROM dbo.stock st
                                    WHERE st.sku_id = s.sku_id
                                      AND st.warehouse_id = dw.warehouse_id)
                 ORDER BY s.code, dw.warehouse_id
                 LIMIT 1
                """);
        assertNotNull(found, "не нашлось товара, которого нет на одном из складов клиента");
        return found.split("\\|");
    }

    /** Строки запасов по одной паре «товар + склад». */
    private List<StockLine> linesFor(String skuCode, String warehouseId) {
        return stock.list(warehouseId, skuCode).stream()
                .filter(r -> skuCode.equals(r.skuCode()))
                .toList();
    }

    /**
     * Поступление, о котором склад уже знает.
     *
     * <p>Статус выставляется запросом, а не через {@code confirm}: тот
     * ушёл бы в WMS, а заглушка отправки — не предмет этой проверки.
     */
    private String createSentReceipt(String skuCode, String warehouseId, int qty) {
        Map<String, Object> card = drafts.create(DocumentType.RECEIPTS,
                Map.of("warehouse_id", warehouseId));
        String docId = docIdOf(card);
        drafts.addLine(DocumentType.RECEIPTS, docId,
                Map.of("sku_id", skuIdOf(skuCode), "expected_qty", qty));
        inContext("UPDATE dbo.receipts SET status_id ="
                + " (SELECT id FROM dbo.enum_status WHERE code = 'sent_to_wms' LIMIT 1)"
                + " WHERE doc_id = '" + docId + "'::uuid");
        return docId;
    }

    /**
     * Убрать поступление. Удалять приходится запросом: портал удаляет
     * только черновики, а этот документ уже «отправлен в WMS» — так
     * и задумано (решение Р5).
     */
    private void removeReceipt(String docId) {
        inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
        inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
    }

    private String skuIdOf(String skuCode) {
        return singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = '" + skuCode + "'");
    }

    @SuppressWarnings("unchecked")
    private static String docIdOf(Map<String, Object> card) {
        return String.valueOf(((Map<String, Object>) card.get("header")).get("docId"));
    }
}
