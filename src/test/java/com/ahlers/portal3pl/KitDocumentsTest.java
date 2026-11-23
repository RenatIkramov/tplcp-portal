package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Комплект в поступлении и перемещении (V153, шаг Н3): строка комплекта
 * становится строками комплектующих — и при добавлении строки, и при
 * правке, и при замене состава целиком (так сохраняет экран и Excel).
 * В заказе комплект остаётся строкой.
 *
 * <p>Состав демо-комплекта проверка задаёт сама и после возвращает прежний;
 * черновики убирает.
 */
class KitDocumentsTest extends PortalTestBase {

    private static final String KIT = "RK-KIT-01";

    @Autowired
    DraftService drafts;

    private final List<String> created = new ArrayList<>();
    private String before;
    private Boolean wasKit;
    private String kit;
    private String rog;
    private String kop;

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
        compose("[{\"code\": \"RK-ROG-01\", \"qty\": 2}, {\"code\": \"RK-KOP-01\", \"qty\": 4}]");
        kit = idOf(KIT);
        rog = idOf("RK-ROG-01");
        kop = idOf("RK-KOP-01");
    }

    @AfterEach
    void restore() {
        for (String doc : created) {
            String[] p = doc.split("\\|");
            inContext("DELETE FROM dbo." + p[0].replaceFirst("s$", "") + "_details WHERE doc_id = '" + p[1] + "'::uuid");
            inContext("DELETE FROM dbo." + p[0] + " WHERE doc_id = '" + p[1] + "'::uuid");
        }
        compose(before);
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :kit").param("k", wasKit).param("kit", KIT).update();
    }

    private void compose(String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", depositorId(USER, DEPOSITOR)).param("kit", KIT).param("rows", rows).query(rs -> { });
    }

    private String idOf(String code) {
        return owner().jdbc.sql("SELECT sku_id::text FROM dbo.sku WHERE code = :c").param("c", code)
                .query(String.class).single();
    }

    private String warehouse() {
        return owner().jdbc.sql("SELECT warehouse_id FROM dbo.warehouse WHERE COALESCE(is_active, true)"
                + " ORDER BY warehouse_id LIMIT 1").query(String.class).single();
    }

    @SuppressWarnings("unchecked")
    private String draft(DocumentType type) {
        signIn();
        Map<String, Object> header = type == DocumentType.TRANSFERS
                ? Map.of("from_warehouse_id", warehouse(), "to_warehouse_id", warehouse())
                : Map.of("warehouse_id", warehouse());
        Map<String, Object> card = drafts.create(type, withErp(header));
        String docId = String.valueOf(((Map<String, Object>) card.get("header")).get("docId"));
        created.add(type.path() + "|" + docId);
        return docId;
    }

    /** Строки документа: «код|количество», по порядку строк. */
    private List<String> lines(DocumentType type, String docId) {
        return owner().jdbc.sql(("SELECT s.code || '|' || (%s)::numeric(14,3)::text FROM dbo.%s d"
                        + " JOIN dbo.sku s ON s.sku_id = d.sku_id WHERE d.doc_id = :d::uuid ORDER BY d.line_number")
                        .formatted(type.plannedQtyColumn(), type.lineTable()))
                .param("d", docId).query(String.class).list();
    }

    @Test
    void receiptLineWithKitBecomesComponents() {
        String docId = draft(DocumentType.RECEIPTS);
        Map<String, Object> card = drafts.addLine(DocumentType.RECEIPTS, docId,
                Map.of("sku_id", kit, "expected_qty", 3, "line_comment", "QA", "quality", "02"));
        assertEquals(List.of(KIT), card.get("expandedKits"), "экрану не сказано, что комплект разложен");
        assertEquals(List.of("RK-ROG-01|6.000", "RK-KOP-01|12.000"), lines(DocumentType.RECEIPTS, docId));
        assertEquals("QA|02|2", owner().jdbc.sql("""
                        SELECT min(d.line_comment) || '|' || min(q.code) || '|' || count(*)::text
                          FROM dbo.receipt_details d JOIN dbo.enum_quality q ON q.id = d.quality_id
                         WHERE d.doc_id = :d::uuid""").param("d", docId).query(String.class).single(),
                "комментарий и качество строки комплекта не перешли на комплектующие");
    }

    /** Замена состава целиком (экран, Excel): комплект — на своём месте, нумерация подряд. */
    @Test
    void replacedLinesKeepTheirOrder() {
        String docId = draft(DocumentType.RECEIPTS);
        drafts.replaceLines(DocumentType.RECEIPTS, docId, List.of(
                Map.of("sku_id", idOf("RK-SHE-01"), "expected_qty", 1),
                Map.of("sku_id", kit, "expected_qty", 1, "price", new BigDecimal("100")),
                Map.of("sku_id", idOf("RK-MYA-01"), "expected_qty", 5)));
        assertEquals(List.of("RK-SHE-01|1.000", "RK-ROG-01|2.000", "RK-KOP-01|4.000", "RK-MYA-01|5.000"),
                lines(DocumentType.RECEIPTS, docId));
        assertEquals(0, owner().jdbc.sql("SELECT count(*) FROM dbo.receipt_details"
                        + " WHERE doc_id = :d::uuid AND sku_id = :s::uuid AND price IS NOT NULL")
                .param("d", docId).param("s", rog).query(Integer.class).single(), "цена комплекта ушла на комплектующую");
        assertEquals(List.of(1, 2, 3, 4), owner().jdbc.sql("SELECT line_number FROM dbo.receipt_details"
                + " WHERE doc_id = :d::uuid ORDER BY line_number").param("d", docId).query(Integer.class).list());
    }

    @Test
    void transferIsExpandedAndOrderIsNot() {
        String transfer = draft(DocumentType.TRANSFERS);
        drafts.addLine(DocumentType.TRANSFERS, transfer, Map.of("sku_id", kit, "qty", 2));
        assertEquals(List.of("RK-ROG-01|4.000", "RK-KOP-01|8.000"), lines(DocumentType.TRANSFERS, transfer));

        String order = draft(DocumentType.ORDERS);
        drafts.addLine(DocumentType.ORDERS, order, Map.of("sku_id", kit, "ordered_qty", 2));
        assertEquals(List.of(KIT + "|2.000"), lines(DocumentType.ORDERS, order), "заказ комплекта разложен");

        // правка строки на комплект — тоже раскладывается
        drafts.addLine(DocumentType.TRANSFERS, transfer, Map.of("sku_id", kop, "qty", 1));
        drafts.updateLine(DocumentType.TRANSFERS, transfer, 3, Map.of("sku_id", kit, "qty", 1));
        assertEquals(List.of("RK-ROG-01|4.000", "RK-KOP-01|8.000", "RK-ROG-01|2.000", "RK-KOP-01|4.000"),
                lines(DocumentType.TRANSFERS, transfer));
    }

    /** Признак «Комплект» есть, а состава из WMS ещё нет — разложить нечем: понятный отказ. */
    @Test
    void kitWithoutCompositionIsRefused() {
        compose("[]");
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = true WHERE code = :kit").param("kit", KIT).update();
        String docId = draft(DocumentType.RECEIPTS);
        expectRejection(400, () -> drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", kit, "expected_qty", 1)));
        assertEquals(List.of(), lines(DocumentType.RECEIPTS, docId), "строка без состава осталась в документе");
    }
}
