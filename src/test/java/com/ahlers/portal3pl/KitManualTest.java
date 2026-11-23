package com.ahlers.portal3pl;

import com.ahlers.portal3pl.catalog.KitService;
import com.ahlers.portal3pl.common.PortalException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Комплекты в «Справочниках» и состав вручную (V154).
 *
 * <p>Всё — в транзакции, которая в конце откатывается: состав
 * демо-комплекта и признаки товаров после проверки такие же, как были.
 * Состав демо-комплекта проверка задаёт сама в начале: от того, что
 * сейчас на базе, она не зависит.
 */
class KitManualTest extends PortalTestBase {

    private static final String KIT = "RK-KIT-01";

    @Autowired
    KitService kits;

    /** Действует {@code login} у клиента D-001; всё откатывается. */
    private void as(String login, Runnable body) {
        if (ADMIN.equals(login)) {
            portalAdmin();
        }
        signInAs(login, DEPOSITOR);
        String dep = depositorId(login, DEPOSITOR);
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            // клиент — и для запросов самой проверки (служба ставит его себе сама)
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true)").param("d", dep).query().listOfRows();
            // демо-комплект — со своим составом: на базе разработки его могли
            // не завести (tools/dev-data.sql не перезапускали) или поменять руками
            jdbc.sql("SELECT dbo.kit_components_apply(:d::uuid, :kit, :rows::jsonb)")
                    .param("d", dep).param("kit", KIT)
                    .param("rows", "[{\"code\": \"RK-ROG-01\", \"qty\": 2}, {\"code\": \"RK-KOP-01\", \"qty\": 4}]")
                    .query(Integer.class).single();
            body.run();
        });
    }

    private String skuId(String code) {
        return jdbc.sql("SELECT sku_id::text FROM dbo.sku WHERE code = :c").param("c", code).query(String.class).single();
    }

    private static Map<String, Object> body(Object... codeQty) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < codeQty.length; i += 2) {
            rows.add(Map.of("code", codeQty[i], "qty", codeQty[i + 1]));
        }
        return Map.of("components", rows);
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> card) {
        return ((List<Map<String, Object>>) card.get("kitComponents")).stream()
                .map(r -> String.valueOf(r.get("code"))).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> partCodes(Map<String, Object> kit) {
        return ((List<Map<String, Object>>) kit.get("components")).stream().map(c -> c.get("code")).toList();
    }

    private Map<String, Object> listed(String code) {
        return kits.list().stream().filter(k -> code.equals(k.get("code"))).findFirst()
                .orElseThrow(() -> new AssertionError("нет комплекта " + code + " во вкладке «Комплекты»"));
    }

    /** Администратор задаёт состав; вкладка показывает состав и «можно собрать» из остатков. */
    @Test
    @SuppressWarnings("unchecked")
    void adminSetsCompositionAndTheListShowsIt() {
        as(ADMIN, () -> {
            Map<String, Object> card = kits.save(skuId(KIT), body("RK-ROG-02", "3", "RK-KOP-01", "1,5"));
            assertEquals(List.of("RK-ROG-02", "RK-KOP-01"), codes(card), "состав не тот или не в том порядке");

            Map<String, Object> kit = listed(KIT);
            assertEquals(List.of("RK-ROG-02", "RK-KOP-01"), partCodes(kit));
            BigDecimal canAssemble = ((List<Map<String, Object>>) kit.get("assemble")).stream()
                    .map(a -> new BigDecimal(String.valueOf(a.get("available")))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal inStock = jdbc.sql("SELECT COALESCE(sum(available_qty), 0) FROM dbo.v_stock_lines"
                            + " WHERE sku_code = :c AND is_kit")
                    .param("c", KIT).query(BigDecimal.class).single();
            assertEquals(0, inStock.compareTo(canAssemble), "«Можно собрать» не совпадает со строкой комплекта в «Запасах»");
        });
    }

    /** Обычный товар становится комплектом по составу; пустой состав снимает признак. */
    @Test
    void compositionMakesAndUnmakesAKit() {
        as(ADMIN, () -> {
            String id = skuId("RK-MYA-01");
            assertEquals("true", String.valueOf(kits.save(id, body("RK-ROG-02", 2)).get("isKit")),
                    "товар с составом не стал комплектом");
            assertEquals(List.of("RK-ROG-02"), partCodes(listed("RK-MYA-01")));

            Map<String, Object> card = kits.save(id, Map.of("components", List.of()));
            assertTrue(codes(card).isEmpty());
            assertEquals("false", String.valueOf(card.get("isKit")), "пустой состав не снял признак");
            assertTrue(kits.list().stream().noneMatch(k -> "RK-MYA-01".equals(k.get("code"))));
        });
    }

    /** Отказы — понятным ключом, до записи. */
    @Test
    void badCompositionIsRefusedWithAReadableReason() {
        as(ADMIN, () -> {
            String kit = skuId(KIT);
            refused("error.kit.component_unknown", () -> kits.save(kit, body("NO-SUCH", 1)));
            refused("error.kit.component_unknown", () -> kits.save(kit, body("MR-MOL-01", 1)));   // чужой клиент
            refused("error.kit.component_self", () -> kits.save(kit, body(KIT, 1)));
            refused("error.kit.qty_positive", () -> kits.save(kit, body("RK-ROG-02", 0)));
            refused("error.kit.qty_positive", () -> kits.save(kit, body("RK-ROG-02", "abc")));
            refused("error.kit.duplicate", () -> kits.save(kit, body("RK-ROG-02", 1, "RK-ROG-02", 2)));
            refused("error.kit.component_required", () -> kits.save(kit, body(" ", 1)));
            // состав одноуровневый
            refused("error.kit.component_is_kit", () -> kits.save(skuId("RK-MYA-01"), body(KIT, 1)));
            refused("error.kit.is_component", () -> kits.save(skuId("RK-ROG-01"), body("RK-SHE-02", 1)));
        });
    }

    /** Клиент состав не задаёт: его ведёт склад. Видит — да. */
    @Test
    void clientSeesKitsButCannotSetComposition() {
        as(USER, () -> {
            listed(KIT);
            PortalException e = assertThrows(PortalException.class,
                    () -> kits.save(skuId(KIT), body("RK-ROG-02", 1)));
            assertEquals(403, e.getStatusCode().value());
            assertEquals("error.kit.forbidden", e.key());
        });
    }

    private static void refused(String key, Runnable action) {
        PortalException e = assertThrows(PortalException.class, action::run, "принято, ждали " + key);
        assertEquals(400, e.getStatusCode().value(), key);
        assertEquals(key, e.key());
    }
}
