package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Остатки комплектов (V152, документ «Комплекты — решение», шаг Н2).
 *
 * <p>Своего остатка у комплекта нет: заказ комплекта резервирует его
 * комплектующие, а у комплекта в «Запасах» — «можно собрать». Состав
 * проверка задаёт сама (той же функцией, что синхронизация с WMS) из двух
 * товаров с годным остатком на одном складе и после возвращает прежний;
 * числа сравниваются с тем, что было, а не с константами.
 */
class KitStockTest extends PortalTestBase {

    private static final String KIT = "RK-KIT-01";

    @Autowired
    DraftService drafts;

    private final List<String> created = new ArrayList<>();
    private String before;
    private Boolean wasKit;
    private String kitId;
    private String warehouse;
    private String a;
    private String aCode;
    private String b;

    @BeforeEach
    void composeKit() {
        before = owner().jdbc.sql("""
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('code', c.code, 'qty', k.qty)
                                                  ORDER BY k.sort_order), '[]')::text
                          FROM dbo.sku_kit_component k
                          JOIN dbo.sku s ON s.sku_id = k.kit_sku_id
                          JOIN dbo.sku c ON c.sku_id = k.component_sku_id
                         WHERE s.code = :kit
                        """).param("kit", KIT).query(String.class).single();
        wasKit = owner().jdbc.sql("SELECT is_kit FROM dbo.sku WHERE code = :kit").param("kit", KIT)
                .query(Boolean.class).optional().orElse(null);
        kitId = owner().jdbc.sql("SELECT sku_id::text FROM dbo.sku WHERE code = :kit").param("kit", KIT)
                .query(String.class).single();
        // две комплектующие с годным остатком на одном складе клиента
        List<Map<String, Object>> pair = owner().jdbc.sql("""
                        SELECT b.warehouse_id, s.sku_id::text AS sku_id, s.code
                          FROM dbo.stock_balance b
                          JOIN dbo.sku s ON s.sku_id = b.sku_id
                          JOIN dbo.enum_quality q ON q.id = b.quality_id AND q.code = '01'
                         WHERE s.depositor_id = :dep::uuid AND s.code <> :kit AND b.available_qty >= 20
                           AND NOT EXISTS (SELECT 1 FROM dbo.sku_kit_component k WHERE k.kit_sku_id = s.sku_id)
                           AND b.warehouse_id = (SELECT b2.warehouse_id FROM dbo.stock_balance b2
                                                   JOIN dbo.sku s2 ON s2.sku_id = b2.sku_id
                                                  WHERE s2.depositor_id = :dep::uuid AND b2.available_qty >= 20
                                                  GROUP BY b2.warehouse_id HAVING count(DISTINCT b2.sku_id) >= 2
                                                  ORDER BY count(DISTINCT b2.sku_id) DESC, b2.warehouse_id LIMIT 1)
                         ORDER BY s.code LIMIT 2
                        """).param("dep", depositorId(USER, DEPOSITOR)).param("kit", KIT).query().listOfRows();
        assertEquals(2, pair.size(), "у клиента нет двух товаров с остатком на одном складе — проверять нечего");
        warehouse = (String) pair.get(0).get("warehouse_id");
        a = (String) pair.get(0).get("sku_id");
        aCode = (String) pair.get(0).get("code");
        b = (String) pair.get(1).get("sku_id");
        compose("[{\"code\": \"" + aCode + "\", \"qty\": 2}, {\"code\": \"" + pair.get(1).get("code") + "\", \"qty\": 3}]");
    }

    @AfterEach
    void restore() {
        for (String doc : created) {
            inContext("DELETE FROM dbo.order_details WHERE doc_id = '" + doc + "'::uuid");
            inContext("DELETE FROM dbo.orders WHERE doc_id = '" + doc + "'::uuid");
        }
        compose(before);
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :kit").param("k", wasKit).param("kit", KIT).update();
    }

    private void compose(String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", depositorId(USER, DEPOSITOR)).param("kit", KIT).param("rows", rows).query(rs -> { });
    }

    private BigDecimal available(String sku) {
        return owner().jdbc.sql("""
                        SELECT COALESCE(sum(b.available_qty), 0) FROM dbo.stock_balance b
                          JOIN dbo.enum_quality q ON q.id = b.quality_id AND q.code = '01'
                         WHERE b.sku_id = :s::uuid AND b.warehouse_id = :w""")
                .param("s", sku).param("w", warehouse).query(BigDecimal.class).single();
    }

    private BigDecimal reserved(String sku) {
        return owner().jdbc.sql("SELECT COALESCE(sum(reserved_qty), 0) FROM dbo.stock_balance"
                        + " WHERE sku_id = :s::uuid AND warehouse_id = :w")
                .param("s", sku).param("w", warehouse).query(BigDecimal.class).single();
    }

    private BigDecimal canAssemble() {
        BigDecimal byA = available(a).divide(BigDecimal.valueOf(2), 0, RoundingMode.FLOOR);
        BigDecimal byB = available(b).divide(BigDecimal.valueOf(3), 0, RoundingMode.FLOOR);
        return byA.min(byB);
    }

    @SuppressWarnings("unchecked")
    private String order(String sku, BigDecimal qty, String extraSku, BigDecimal extraQty) {
        signIn();
        Map<String, Object> card = drafts.create(DocumentType.ORDERS, withErp(Map.of("warehouse_id", warehouse)));
        String docId = String.valueOf(((Map<String, Object>) card.get("header")).get("docId"));
        created.add(docId);
        drafts.addLine(DocumentType.ORDERS, docId, Map.of("sku_id", sku, "ordered_qty", qty));
        if (extraSku != null) {
            drafts.addLine(DocumentType.ORDERS, docId, Map.of("sku_id", extraSku, "ordered_qty", extraQty));
        }
        return docId;
    }

    private void status(String docId, String code) {
        inContext("UPDATE dbo.orders SET status_id = (SELECT id FROM dbo.enum_status WHERE code = '" + code
                + "') WHERE doc_id = '" + docId + "'::uuid");
    }

    /** У комплекта — «можно собрать»: наименьшее по комплектующим, вниз до целого; наличия нет. */
    @Test
    void kitShowsWhatCanBeAssembled() {
        assertEquals(0, canAssemble().compareTo(available(kitId)), "«можно собрать» посчитано не так");
        assertEquals("0.000|true", singleValueAsOwner("SELECT COALESCE(max(quantity), 0)::numeric(14,3)::text || '|' ||"
                + " bool_and(is_kit)::text FROM dbo.v_stock_lines WHERE sku_id = '" + kitId + "'::uuid"
                + " AND warehouse_id = '" + warehouse + "'"), "у комплекта наличие или нет отметки «комплект»");
    }

    /** Заказ комплекта резервирует комплектующие по составу; «можно собрать» уменьшается. */
    @Test
    void kitOrderReservesComponents() {
        BigDecimal ra = reserved(a);
        BigDecimal rb = reserved(b);
        String order = order(kitId, BigDecimal.valueOf(3), null, null);
        status(order, "04");
        assertEquals(0, ra.add(BigDecimal.valueOf(6)).compareTo(reserved(a)), "комплектующая A: резерв не 3 × 2");
        assertEquals(0, rb.add(BigDecimal.valueOf(9)).compareTo(reserved(b)), "комплектующая B: резерв не 3 × 3");
        assertEquals(0, canAssemble().compareTo(available(kitId)), "«можно собрать» не учло резерв");

        status(order, "90");
        assertEquals(0, ra.compareTo(reserved(a)), "отменённый заказ комплекта держит резерв");
    }

    /**
     * Проверка при «Подтвердить» — по комплектующим вместе: комплект на
     * всё, что есть у A, плюс та же A отдельной строкой — нехватка у A,
     * и видно, что A — из комплекта.
     */
    @Test
    void shortageCountsKitAndLooseComponentTogether() {
        compose("[{\"code\": \"" + aCode + "\", \"qty\": 2}]");
        BigDecimal kits = available(a).divide(BigDecimal.valueOf(2), 0, RoundingMode.FLOOR);

        String fits = order(kitId, kits, null, null);
        assertTrue(shortage(fits).isEmpty(), "комплектов ровно на остаток — а проверка нашла нехватку");

        String both = order(kitId, kits, a, BigDecimal.valueOf(2));
        List<Map<String, Object>> rows = shortage(both);
        assertEquals(1, rows.size(), "нехватка не одна строка: " + rows);
        assertEquals(aCode, rows.getFirst().get("sku_code"), "нехватка не у комплектующей");
        assertEquals(KIT, rows.getFirst().get("kit_codes"), "не видно, что комплектующая — из комплекта");
    }

    private List<Map<String, Object>> shortage(String docId) {
        return new org.springframework.transaction.support.TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true)").param("d", depositorId(USER, DEPOSITOR))
                    .query().listOfRows();
            return jdbc.sql("SELECT sku_code, kit_codes FROM dbo.stock_doc_shortage('orders', :d::uuid)")
                    .param("d", docId).query().listOfRows();
        });
    }

    private String singleValueAsOwner(String sql) {
        return owner().jdbc.sql(sql).query(String.class).single();
    }
}
