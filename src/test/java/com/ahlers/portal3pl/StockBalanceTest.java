package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сжатый слой остатков (V141, план «Кэш остатков», поставка К1).
 *
 * <p>Резерв и «к получению» портал считает сам — из открытых документов,
 * каждый раз заново. По одной проверке на правило из решений заказчика
 * 14.11.2026 и на случаи, ради которых выбран пересчёт, а не «прибавить /
 * отнять»: отмена, недобор, приёмка по частям, два заказа одновременно.
 *
 * <p>Числа сравниваются не с константами, а с тем, что было до проверки:
 * демо-данные правят руками, и ждать заранее известного остатка нельзя.
 * Документы заводятся и убираются здесь же.
 */
class StockBalanceTest extends PortalTestBase {

    @Autowired
    DraftService drafts;

    /** Заведённые документы: «таблица|doc_id» — убрать после проверки. */
    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeDocuments() {
        for (String doc : created) {
            String[] p = doc.split("\\|");
            inContext("DELETE FROM dbo." + p[0].replaceFirst("s$", "") + "_details WHERE doc_id = '" + p[1] + "'::uuid");
            inContext("DELETE FROM dbo." + p[0] + " WHERE doc_id = '" + p[1] + "'::uuid");
        }
        owner().jdbc.sql("UPDATE dbo.portal_setting SET value = 'false' WHERE setting_key = 'stock_count_transfers'")
                .update();
        owner().jdbc.sql("SELECT dbo.stock_recalc(NULL)").query(rs -> { });
    }

    /* ----------------------- что проверяется ----------------------- */

    /** Черновик остатков не трогает; с «Подтвердить» (04) — резерв, доступно меньше. */
    @Test
    void orderReservesFromConfirmation() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);

        String order = order(p, 7);
        assertEquals(before, numbers(p), "черновик заказа изменил остатки");

        status("orders", order, "04");
        Numbers after = numbers(p);
        assertEquals(before.reserved.add(bd(7)), after.reserved, "резерв — с подтверждения, до ответа WMS");
        assertEquals(before.available.subtract(bd(7)), after.available, "доступно не уменьшилось");
        assertEquals(before.quantity, after.quantity, "наличие заказ не меняет");
    }

    /** Резерв держат статусы до отгрузки; ошибка отправки, отгружен, отменён — нет. */
    @Test
    void reserveFollowsOrderStatus() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String order = order(p, 5);
        for (String code : List.of("04", "02", "10", "20", "21", "30", "40")) {
            status("orders", order, code);
            assertEquals(before.reserved.add(bd(5)), numbers(p).reserved, "заказ в статусе " + code + " держит резерв");
        }
        for (String code : List.of("03", "50", "90")) {
            status("orders", order, code);
            assertEquals(before.reserved, numbers(p).reserved, "заказ в статусе " + code + " резерв не держит");
        }
    }

    /** WMS вернула своё количество — резерв по нему, а не по заказанному. */
    @Test
    void reserveUsesQuantityReturnedByWms() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String order = order(p, 9);
        status("orders", order, "10");
        inContext("UPDATE dbo.order_details SET ordered_qty_wms = 6 WHERE doc_id = '" + order + "'::uuid");
        assertEquals(before.reserved.add(bd(6)), numbers(p).reserved, "резерв не по количеству WMS");
    }

    /**
     * Недобор: заказано 10, отгружено 8 — после «Отгружен» резерва нет вовсе.
     * При «прибавить / отнять» две штуки остались бы в резерве навсегда.
     */
    @Test
    void shortShipmentLeavesNoReserve() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String order = order(p, 10);
        status("orders", order, "30");
        fact("order_details", order, p, "picked_qty", 8);
        assertEquals(before.reserved.add(bd(10)), numbers(p).reserved, "до отгрузки резерв — весь заказ");

        status("orders", order, "51");
        assertEquals(before.reserved.add(bd(2)), numbers(p).reserved, "частично отгружен — в резерве остаток");

        status("orders", order, "50");
        assertEquals(before.reserved, numbers(p).reserved, "после отгрузки остался «зависший» резерв");
    }

    /** Заказано больше, чем есть: доступно — ноль, а не минус; разница — нехватка. */
    @Test
    void overbookingGivesShortageNotNegative() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        int tooMuch = before.available.intValue() + 3;
        String order = order(p, tooMuch);
        status("orders", order, "04");
        assertEquals(0, numbers(p).available.signum(), "доступно ушло в минус или не обнулилось");
        assertEquals("3.000", singleValue("SELECT sum(shortage_qty)::text FROM dbo.stock_balance b"
                + " WHERE b.sku_id = '" + p.skuId + "'::uuid AND b.warehouse_id = '" + p.warehouse + "'"),
                "нехватка не посчитана");
    }

    /** Заказ брака годное не резервирует. */
    @Test
    void defectOrderDoesNotTakeGoodStock() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String order = order(p, 4);
        inContext("UPDATE dbo.order_details SET quality_id = (SELECT id FROM dbo.enum_quality WHERE code = '02')"
                + " WHERE doc_id = '" + order + "'::uuid");
        status("orders", order, "04");
        assertEquals(before.available, numbers(p).available, "заказ брака уменьшил доступное годное");
    }

    /**
     * Приёмка по частям: ожидалось 100, 40 уже принято — к получению 60,
     * а не 100. Принят — ноль.
     */
    @Test
    void incomingShrinksAsGoodsArrive() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String receipt = receipt(p, 100);
        assertEquals(before, numbers(p), "черновик поступления изменил остатки");

        status("receipts", receipt, "04");
        assertEquals(before.incoming.add(bd(100)), numbers(p).incoming, "к получению — с подтверждения");

        status("receipts", receipt, "30");
        fact("receipt_details", receipt, p, "accepted_qty", 40);
        assertEquals(before.incoming.add(bd(60)), numbers(p).incoming, "принятое не вычтено из «к получению»");

        status("receipts", receipt, "60");
        assertEquals(before.incoming, numbers(p).incoming, "принятое поступление всё ещё ожидается");
    }

    /** Отмена и удаление строки возвращают числа без отдельного правила. */
    @Test
    void cancellationAndLineRemovalGiveBack() {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String order = order(p, 6);
        status("orders", order, "10");
        inContext("DELETE FROM dbo.order_details WHERE doc_id = '" + order + "'::uuid");
        assertEquals(before, numbers(p), "строку убрали (отмена позиции), резерв остался");

        String second = order(p, 6);
        status("orders", second, "20");
        status("orders", second, "90");
        assertEquals(before, numbers(p), "заказ отменён, резерв остался");
    }

    /** Перемещения учитываются только при включённой настройке. */
    @Test
    void transfersCountOnlyWhenSwitchedOn() {
        signIn();
        Pair p = goodStock();
        String other = singleValue("SELECT warehouse_id FROM dbo.v_warehouses WHERE warehouse_id <> '"
                + p.warehouse + "' ORDER BY warehouse_id LIMIT 1");
        Pair target = new Pair(p.skuId, other);
        Numbers before = numbers(p);
        Numbers targetBefore = numbers(target);

        String transfer = transfer(p, other, 11);
        status("transfers", transfer, "04");
        assertEquals(before, numbers(p), "настройка выключена, а перемещение учтено");

        owner().jdbc.sql("UPDATE dbo.portal_setting SET value = 'true' WHERE setting_key = 'stock_count_transfers'")
                .update();
        owner().jdbc.sql("SELECT dbo.stock_recalc(NULL)").query(rs -> { });
        assertEquals(before.reserved.add(bd(11)), numbers(p).reserved, "нет резерва на складе-отправителе");
        assertEquals(targetBefore.incoming.add(bd(11)), numbers(target).incoming, "нет «к получению» у получателя");

        status("transfers", transfer, "50");
        assertEquals(before.reserved, numbers(p).reserved, "отгружено — резерв у отправителя снят");
        assertEquals(targetBefore.incoming.add(bd(11)), numbers(target).incoming, "в пути — получатель ещё ждёт");

        status("transfers", transfer, "60");
        assertEquals(targetBefore.incoming, numbers(target).incoming, "принято — больше не ожидается");
    }

    /**
     * Два заказа подтверждаются одновременно — в резерве оба. Без общей
     * блокировки пересчёта каждый видел бы только свой документ, и второй
     * затёр бы резерв первого.
     */
    @Test
    void twoConfirmationsAtOnceBothCount() throws Exception {
        signIn();
        Pair p = goodStock();
        Numbers before = numbers(p);
        String first = order(p, 3);
        String second = order(p, 4);
        String depositor = depositorId(USER, DEPOSITOR);

        CompletableFuture<Void> slow = CompletableFuture.runAsync(() -> confirmHolding(depositor, first, 600));
        Thread.sleep(200);
        CompletableFuture<Void> fast = CompletableFuture.runAsync(() -> confirmHolding(depositor, second, 0));
        CompletableFuture.allOf(slow, fast).get(20, TimeUnit.SECONDS);

        assertEquals(before.reserved.add(bd(7)), numbers(p).reserved, "один из двух резервов потерян");
    }

    /** Портал пишет сжатый слой только пересчётом: напрямую — нельзя, как и подробный. */
    @Test
    void applicationCannotWriteStockDirectly() {
        signIn();
        for (String table : List.of("stock_balance", "stock")) {
            assertEquals("false|false|false", singleValue("SELECT has_table_privilege('dbo." + table + "', 'INSERT')::text || '|' ||"
                    + " has_table_privilege('dbo." + table + "', 'UPDATE')::text || '|' ||"
                    + " has_table_privilege('dbo." + table + "', 'DELETE')::text"), table + " открыта порталу на запись");
        }
        assertEquals("false", singleValue("SELECT has_function_privilege('dbo.stock_recalc(uuid, uuid[])', 'EXECUTE')::text"));
    }

    /** Подробный слой — только то, что знает WMS: резерва и «доступно» в нём нет. */
    @Test
    void detailLayerHasNoPortalNumbers() {
        assertEquals("0", singleValue("""
                SELECT count(*)::text FROM information_schema.columns
                 WHERE table_schema = 'dbo' AND table_name IN ('stock', 'v_stock_details')
                   AND column_name IN ('reserved_qty', 'available_qty')"""));
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.table_column"
                + " WHERE table_key = 'stockBatch' AND column_id IN ('available', 'reserved')"));
    }

    /* ----------------------- помощники ----------------------- */

    private record Pair(String skuId, String warehouse) { }

    private record Numbers(BigDecimal quantity, BigDecimal reserved, BigDecimal available, BigDecimal incoming) { }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value).setScale(3);
    }

    /** Товар с годным остатком на складе клиента — с запасом для заказов проверки. */
    private Pair goodStock() {
        String found = singleValue("""
                SELECT sku_id::text || '|' || warehouse_id FROM dbo.v_stock_available
                 WHERE available_qty >= 50 ORDER BY available_qty DESC, sku_id LIMIT 1""");
        String[] parts = found.split("\\|");
        return new Pair(parts[0], parts[1]);
    }

    private Numbers numbers(Pair p) {
        String row = singleValue("SELECT COALESCE((SELECT quantity::numeric(14,3) || '|' || reserved_qty::numeric(14,3)"
                + " || '|' || available_qty::numeric(14,3) || '|' || incoming_qty::numeric(14,3)"
                + " FROM dbo.v_stock_available WHERE sku_id = '" + p.skuId + "'::uuid AND warehouse_id = '"
                + p.warehouse + "'), '0.000|0.000|0.000|0.000')");
        String[] n = row.split("\\|");
        return new Numbers(new BigDecimal(n[0]), new BigDecimal(n[1]), new BigDecimal(n[2]), new BigDecimal(n[3]));
    }

    private String order(Pair p, int qty) {
        String docId = docIdOf(drafts.create(DocumentType.ORDERS, withErp(Map.of("warehouse_id", p.warehouse))));
        created.add("orders|" + docId);
        drafts.addLine(DocumentType.ORDERS, docId, Map.of("sku_id", p.skuId, "ordered_qty", qty));
        return docId;
    }

    private String receipt(Pair p, int qty) {
        String docId = docIdOf(drafts.create(DocumentType.RECEIPTS, withErp(Map.of("warehouse_id", p.warehouse))));
        created.add("receipts|" + docId);
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", p.skuId, "expected_qty", qty));
        return docId;
    }

    private String transfer(Pair p, String toWarehouse, int qty) {
        String docId = docIdOf(drafts.create(DocumentType.TRANSFERS,
                withErp(Map.of("from_warehouse_id", p.warehouse, "to_warehouse_id", toWarehouse))));
        created.add("transfers|" + docId);
        drafts.addLine(DocumentType.TRANSFERS, docId, Map.of("sku_id", p.skuId, "qty", qty));
        return docId;
    }

    /** Статус — запросом: отправка в WMS и её заглушка — не предмет этих проверок. */
    private void status(String table, String docId, String code) {
        inContext("UPDATE dbo." + table + " SET status_id = (SELECT id FROM dbo.enum_status WHERE code = '" + code
                + "') WHERE doc_id = '" + docId + "'::uuid");
    }

    /** Фактическая запись строки — как её пришлёт WMS: та же строка, с партией (решение Р1). */
    private void fact(String table, String docId, Pair p, String column, int qty) {
        inContext("INSERT INTO dbo." + table + " (doc_id, line_number, sku_id, batch_number, " + column + ")"
                + " SELECT doc_id, line_number, sku_id, 'QA-BATCH', " + qty + " FROM dbo." + table
                + " WHERE doc_id = '" + docId + "'::uuid AND batch_number IS NULL");
    }

    /** «Подтвердить» в своей транзакции, которая не закрывается ещё {@code holdMs}. */
    private void confirmHolding(String depositor, String docId, long holdMs) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)").param("id", depositor)
                    .query(String.class).single();
            jdbc.sql("UPDATE dbo.orders SET status_id = (SELECT id FROM dbo.enum_status WHERE code = '04')"
                    + " WHERE doc_id = :d::uuid").param("d", docId).update();
            try {
                Thread.sleep(holdMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static String docIdOf(Map<String, Object> card) {
        return String.valueOf(((Map<String, Object>) card.get("header")).get("docId"));
    }
}
