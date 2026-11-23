package com.ahlers.portal3pl;

import com.ahlers.portal3pl.common.PortalException;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.documents.WmsGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Проверка остатков при подтверждении (V142, план «Кэш остатков», К2;
 * решение заказчика 14.11.2026): заказ сверх «Доступно» без проверки
 * в WMS не проходит.
 *
 * <p>WMS изображает подменённый {@link WmsGateway}: заглушка своих
 * остатков не имеет и числа не даёт. Количества берутся от того, что
 * есть в базе на момент проверки, а не константами.
 */
class StockConfirmTest extends PortalTestBase {

    @Autowired
    DraftService drafts;

    @MockitoSpyBean
    WmsGateway wms;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeCreated() {
        doCallRealMethod().when(wms).freeStock(anyString(), anyString(), anyString(), anyString());
        for (String doc : created) {
            String[] p = doc.split("\\|");
            jdbc.sql("SELECT dbo.wms_outbox_finish(outbox_id, NULL, NULL) FROM dbo.wms_outbox"
                    + " WHERE doc_id = :d::uuid AND state = 'pending'").param("d", p[1]).query().listOfRows();
            inContext("DELETE FROM dbo." + p[0].replaceFirst("s$", "") + "_details WHERE doc_id = '" + p[1] + "'::uuid");
            inContext("DELETE FROM dbo." + p[0] + " WHERE doc_id = '" + p[1] + "'::uuid");
        }
        // наличие, которое проверки записали «от WMS», — обратно из партий
        owner().jdbc.sql("SELECT dbo.stock_rebase_from_details(NULL)").query(rs -> { });
    }

    /** Хватает — заказ уходит, WMS о остатках не спрашивают. */
    @Test
    void enoughStockPassesWithoutAskingWms() {
        signIn();
        Item item = goodStock();
        String order = order(item, 5);
        assertEquals("02", status(drafts.confirm(DocumentType.ORDERS, order, false)));
        verify(wms, never()).freeStock(anyString(), anyString(), anyString(), anyString());
    }

    /** Не хватает — отказ со строками нехватки; документ остался черновиком, резерв не тронут. */
    @Test
    void shortageIsRefusedWithItsLines() {
        signIn();
        Item item = goodStock();
        BigDecimal reservedBefore = reserved(item);
        String order = order(item, item.available.intValue() + 3);

        PortalException refusal = assertThrows(PortalException.class,
                () -> drafts.confirm(DocumentType.ORDERS, order, false));
        assertEquals(409, refusal.getStatusCode().value());
        assertEquals("error.stock.not_enough", refusal.key());
        Map<String, Object> line = lines(refusal).getFirst();
        assertEquals(item.code, line.get("skuCode"));
        assertEquals(0, new BigDecimal("3").compareTo(number(line.get("shortageQty"))), "не хватает — не 3");
        assertEquals(0, item.available.compareTo(number(line.get("availableQty"))), "доступно в отказе не то");

        assertEquals("01", statusOf(order), "документ с нехваткой ушёл из черновиков");
        assertEquals(reservedBefore, reserved(item), "отказанный заказ оставил резерв");
        verify(wms, never()).freeStock(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * «Проверить актуальные остатки»: WMS говорит, что товара больше, чем
     * знал портал, — заказ уходит, а портал теперь знает число WMS.
     */
    @Test
    void checkPassesWhenWmsHasEnough() {
        signIn();
        Item item = goodStock();
        int qty = item.available.intValue() + 3;
        String order = order(item, qty);
        doReturn(wmsFree(item, 10))
                .when(wms).freeStock(anyString(), eq(item.warehouse), eq(item.code), eq("01"));

        assertEquals("02", status(drafts.confirm(DocumentType.ORDERS, order, true)));
        verify(wms, times(1)).freeStock(anyString(), anyString(), anyString(), anyString());
        assertEquals(0, new BigDecimal("7").compareTo(available(item)),
                "доступно после заказа — свободное WMS минус заказ");
    }

    /** WMS подтвердила нехватку — отказ уже с её числами. */
    @Test
    void checkRefusesWhenWmsConfirmsShortage() {
        signIn();
        Item item = goodStock();
        String order = order(item, item.available.intValue() + 3);
        doReturn(wmsFree(item, -2)).when(wms).freeStock(anyString(), anyString(), anyString(), anyString());

        PortalException refusal = assertThrows(PortalException.class,
                () -> drafts.confirm(DocumentType.ORDERS, order, true));
        assertEquals("error.stock.not_enough_checked", refusal.key());
        Map<String, Object> line = lines(refusal).getFirst();
        assertEquals(0, item.available.subtract(new BigDecimal("2")).compareTo(number(line.get("availableQty"))),
                "в отказе не число WMS");
        assertEquals(0, new BigDecimal("5").compareTo(number(line.get("shortageQty"))));
        assertEquals("01", statusOf(order));
    }

    /** WMS не ответила — документ остаётся черновиком, отказ 503. */
    @Test
    void silentWmsLeavesDraft() {
        signIn();
        Item item = goodStock();
        String order = order(item, item.available.intValue() + 3);
        doThrow(new IllegalStateException("WMS не отвечает"))
                .when(wms).freeStock(anyString(), anyString(), anyString(), anyString());

        PortalException refusal = assertThrows(PortalException.class,
                () -> drafts.confirm(DocumentType.ORDERS, order, true));
        assertEquals(503, refusal.getStatusCode().value());
        assertEquals("error.stock.check_unavailable", refusal.key());
        assertEquals("01", statusOf(order));
    }

    /** Заглушка WMS числа не даёт — портал остаётся при своих данных и отказывает. */
    @Test
    void stubGivesNoNumberAndPortalKeepsItsOwn() {
        signIn();
        Item item = goodStock();
        String order = order(item, item.available.intValue() + 3);
        PortalException refusal = assertThrows(PortalException.class,
                () -> drafts.confirm(DocumentType.ORDERS, order, true));
        assertEquals("error.stock.not_enough_checked", refusal.key());
        assertEquals(0, item.available.compareTo(number(lines(refusal).getFirst().get("availableQty"))));
    }

    /** Два заказа на один остаток: первый забрал почти всё — второй не проходит. */
    @Test
    void secondOrderSeesTheFirstOnesReserve() {
        signIn();
        Item item = goodStock();
        String first = order(item, item.available.intValue() - 1);
        String second = order(item, 2);
        assertEquals("02", status(drafts.confirm(DocumentType.ORDERS, first, false)));
        PortalException refusal = assertThrows(PortalException.class,
                () -> drafts.confirm(DocumentType.ORDERS, second, false));
        assertEquals("error.stock.not_enough", refusal.key());
        assertEquals(0, BigDecimal.ONE.compareTo(number(lines(refusal).getFirst().get("shortageQty"))));
    }

    /**
     * Ответ WMS — «свободно для резерва»: заказы, которые WMS уже приняла,
     * в нём учтены. Портал не вычитает их второй раз.
     */
    @Test
    void wmsAnswerAlreadyCountsOrdersWmsKnows() {
        signIn();
        Item item = goodStock();
        String known = order(item, 10);
        inContext("UPDATE dbo.orders SET status_id = (SELECT id FROM dbo.enum_status WHERE code = '10')"
                + " WHERE doc_id = '" + known + "'::uuid");
        String order = order(item, item.available.intValue());   // на 10 больше, чем осталось
        clearInvocations(wms);
        doReturn(wmsFree(item, 0)).when(wms).freeStock(anyString(), anyString(), anyString(), anyString());

        assertEquals("02", status(drafts.confirm(DocumentType.ORDERS, order, true)),
                "свободное WMS уменьшено на заказ, который в нём уже учтён");
        assertEquals(0, available(item).signum());
    }

    /** Поступление товар не расходует — его проверка не касается. */
    @Test
    void receiptIsNotChecked() {
        signIn();
        Item item = goodStock();
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", item.warehouse);
        head.put("eta_date", "2026-12-01");
        String receipt = docIdOf(drafts.create(DocumentType.RECEIPTS, withErp(head)));
        created.add("receipts|" + receipt);
        drafts.addLine(DocumentType.RECEIPTS, receipt,
                Map.of("sku_id", item.skuId, "expected_qty", item.available.intValue() + 1000));
        assertEquals("02", status(drafts.confirm(DocumentType.RECEIPTS, receipt, false)));
    }

    /** Чужую строку остатков ответом WMS не записать. */
    @Test
    void wmsAnswerCannotBeWrittenForAnotherClient() {
        signIn();
        String foreignSku = owner().jdbc.sql("""
                SELECT s.sku_id::text FROM dbo.sku s JOIN dbo.depositor d ON d.depositor_id = s.depositor_id
                 WHERE d.code = :code LIMIT 1""").param("code", OTHER_DEPOSITOR).query(String.class).single();
        assertThrows(RuntimeException.class, () -> inContext(
                "SELECT dbo.stock_apply_wms_free('" + foreignSku + "'::uuid, 'WH-ALA', '01', 5)"));
    }

    /* ----------------------- помощники ----------------------- */

    private record Item(String skuId, String code, String warehouse, BigDecimal available) { }

    private Item goodStock() {
        String[] p = singleValue("""
                SELECT a.sku_id::text || '|' || s.code || '|' || a.warehouse_id || '|' || a.available_qty::numeric(14,3)
                  FROM dbo.v_stock_available a JOIN dbo.sku s ON s.sku_id = a.sku_id
                 WHERE a.available_qty >= 50 ORDER BY a.available_qty DESC, a.sku_id LIMIT 1""").split("\\|");
        return new Item(p[0], p[1], p[2], new BigDecimal(p[3]));
    }

    /**
     * Ответ WMS «свободно для резерва», при котором портал насчитает
     * «Доступно» = прежнее + {@code delta}. Свободное WMS не знает о заказах,
     * которые до неё ещё не дошли (04, 02), — портал вычтет их сам, поэтому
     * в ответ они добавляются. В базе, где человек подтверждал заказы руками,
     * такие есть всегда: заглушка WMS дальше «Отправлен» их не ведёт.
     */
    private BigDecimal wmsFree(Item item, int delta) {
        BigDecimal notInWms = new BigDecimal(singleValue("""
                SELECT COALESCE(sum(COALESCE(d.ordered_qty_wms, d.ordered_qty, 0)), 0)::text
                  FROM dbo.orders o JOIN dbo.enum_status st ON st.id = o.status_id
                  JOIN dbo.order_details d ON d.doc_id = o.doc_id
                 WHERE st.code IN ('04', '02') AND d.batch_number IS NULL
                   AND COALESCE(d.quality_id, 1) = (SELECT id FROM dbo.enum_quality WHERE code = '01')
                   AND d.sku_id = '%s'::uuid AND o.warehouse_id = '%s'""".formatted(item.skuId, item.warehouse)));
        return item.available.add(notInWms).add(new BigDecimal(delta));
    }

    /** Заказ, готовый к подтверждению: обязательные поля шапки и одна строка. */
    private String order(Item item, int qty) {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", item.warehouse);
        head.put("planned_ship_date", "2026-12-01");
        head.put("consignee_id", singleValue("SELECT consignee_id::text FROM dbo.consignee ORDER BY 1 LIMIT 1"));
        String docId = docIdOf(drafts.create(DocumentType.ORDERS, withErp(head)));
        created.add("orders|" + docId);
        drafts.addLine(DocumentType.ORDERS, docId, Map.of("sku_id", item.skuId, "ordered_qty", qty));
        return docId;
    }

    private BigDecimal available(Item item) {
        return new BigDecimal(singleValue("SELECT available_qty::text FROM dbo.v_stock_available WHERE sku_id = '"
                + item.skuId + "'::uuid AND warehouse_id = '" + item.warehouse + "'"));
    }

    private BigDecimal reserved(Item item) {
        return new BigDecimal(singleValue("SELECT reserved_qty::text FROM dbo.v_stock_available WHERE sku_id = '"
                + item.skuId + "'::uuid AND warehouse_id = '" + item.warehouse + "'"));
    }

    private String statusOf(String orderId) {
        return singleValue("SELECT st.code FROM dbo.orders o JOIN dbo.enum_status st ON st.id = o.status_id"
                + " WHERE o.doc_id = '" + orderId + "'::uuid");
    }

    private static BigDecimal number(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(PortalException refusal) {
        return (List<Map<String, Object>>) refusal.params().get("lines");
    }

    @SuppressWarnings("unchecked")
    private static String status(Map<String, Object> card) {
        return String.valueOf(((Map<String, Object>) card.get("header")).get("statusCode"));
    }

    @SuppressWarnings("unchecked")
    private static String docIdOf(Map<String, Object> card) {
        return String.valueOf(((Map<String, Object>) card.get("header")).get("docId"));
    }
}
