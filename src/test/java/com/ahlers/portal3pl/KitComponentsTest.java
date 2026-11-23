package com.ahlers.portal3pl;

import com.ahlers.portal3pl.catalog.SkuWriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Состав комплекта (V151): запись для синхронизации с WMS и показ в
 * карточке товара.
 *
 * <p>Всё — в транзакции, которая в конце откатывается: состав демо-комплекта
 * и признак «Комплект» после проверки такие же, как были. Пишет та же
 * функция, которой будет пользоваться синхронизация.
 */
class KitComponentsTest extends PortalTestBase {

    private static final String KIT = "RK-KIT-01";

    @Autowired
    SkuWriteService skus;

    /** Клиент D-001 в контексте, действует {@code actor}; всё откатывается. */
    private void asClient(String actor, Consumer<String> body) {
        signIn();
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true), set_config('app.actor', :a, true),"
                            + " set_config('app.locale', 'ru', true)")
                    .param("d", depositorId(USER, DEPOSITOR)).param("a", actor).query().listOfRows();
            body.accept(depositorId(USER, DEPOSITOR));
        });
    }

    private int apply(String dep, String kit, String rows) {
        return jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", dep).param("kit", kit).param("rows", rows).query(Integer.class).single();
    }

    private String skuId(String code) {
        return jdbc.sql("SELECT sku_id::text FROM dbo.sku WHERE code = :c").param("c", code).query(String.class).single();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> components(String code) {
        return (List<Map<String, Object>>) skus.card(skuId(code)).get("kitComponents");
    }

    @Test
    void compositionIsWrittenWholeAndShownInTheCard() {
        asClient(ADMIN, dep -> {
            assertEquals(2, apply(dep, KIT, "[{\"code\": \"RK-ROG-02\", \"qty\": 3}, {\"code\": \"RK-KOP-01\", \"qty\": 1.5}]"));
            List<Map<String, Object>> rows = components(KIT);
            assertEquals(List.of("RK-ROG-02", "RK-KOP-01"), rows.stream().map(r -> r.get("code")).toList(),
                    "состав не тот или не в порядке из WMS");
            assertEquals("3", String.valueOf(rows.getFirst().get("qty")).replaceAll("\\.0+$", ""));
            assertEquals("true", String.valueOf(skus.card(skuId(KIT)).get("isKit")), "признак «Комплект» не встал");

            // целиком: прежней комплектующей больше нет
            assertEquals(1, apply(dep, KIT, "[{\"code\": \"RK-KOP-01\", \"qty\": 2}]"));
            assertEquals(List.of("RK-KOP-01"), components(KIT).stream().map(r -> r.get("code")).toList());

            // пусто — больше не комплект
            assertEquals(0, apply(dep, KIT, "[]"));
            assertTrue(components(KIT).isEmpty());
            assertEquals("false", String.valueOf(skus.card(skuId(KIT)).get("isKit")));

            // у обычного товара состава нет
            assertTrue(components("RK-ROG-01").isEmpty());
        });
    }

    @Test
    void badCompositionIsRefused() {
        asClient(ADMIN, dep -> {
            apply(dep, KIT, "[{\"code\": \"RK-ROG-01\", \"qty\": 2}]");
            for (String bad : List.of(
                    "[{\"code\": \"NO-SUCH\", \"qty\": 1}]",         // нет такого товара
                    "[{\"code\": \"MR-MOL-01\", \"qty\": 1}]",       // товар другого клиента
                    "[{\"code\": \"" + KIT + "\", \"qty\": 1}]",     // сам себя
                    "[{\"code\": \"RK-KOP-01\", \"qty\": 0}]")) {    // ноль
                jdbc.sql("SAVEPOINT bad").update();
                assertThrows(RuntimeException.class, () -> apply(dep, KIT, bad), "принят состав " + bad);
                jdbc.sql("ROLLBACK TO SAVEPOINT bad").update();
            }
            // комплект в комплекте: состав одноуровневый
            jdbc.sql("SAVEPOINT bad").update();
            assertThrows(RuntimeException.class,
                    () -> apply(dep, "RK-MYA-01", "[{\"code\": \"" + KIT + "\", \"qty\": 1}]"),
                    "комплект принят комплектующей");
            jdbc.sql("ROLLBACK TO SAVEPOINT bad").update();
            jdbc.sql("SAVEPOINT bad").update();
            assertThrows(RuntimeException.class,
                    () -> apply(dep, "RK-ROG-01", "[{\"code\": \"RK-SHE-02\", \"qty\": 1}]"),
                    "комплектующая стала комплектом");
            jdbc.sql("ROLLBACK TO SAVEPOINT bad").update();
        });
    }

    /** Другой клиент состав не видит; правка состава — в истории товара-комплекта. */
    @Test
    void compositionIsTheClientsOwnAndJournaled() {
        asClient("wms", dep -> {
            apply(dep, KIT, "[{\"code\": \"RK-ROG-02\", \"qty\": 7}]");
            List<Map<String, Object>> history = jdbc.sql("SELECT * FROM dbo.audit_history('sku', :id)")
                    .param("id", skuId(KIT)).query().listOfRows();
            assertTrue(history.stream().anyMatch(r -> "kit:RK-ROG-02".equals(r.get("part"))
                            && "wms".equals(r.get("actor"))),
                    "состав не записан в историю комплекта: " + history);

            jdbc.sql("SELECT set_config('app.depositor_id', :d, true)")
                    .param("d", depositorId(OTHER_USER, OTHER_DEPOSITOR)).query().listOfRows();
            assertEquals(0, jdbc.sql("SELECT count(*) FROM dbo.sku_kit_component").query(Integer.class).single(),
                    "другому клиенту виден чужой состав");
        });
    }
}
