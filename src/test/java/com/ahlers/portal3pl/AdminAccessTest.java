package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.AdminAccessService;
import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Экраны доступов (V20): склады кладовщика и склады поклажедателя.
 * <p>
 * Самое важное здесь — последняя проверка: настройка складов поклажедателя
 * должна действительно сужать список складов в документах, иначе это
 * просто галочки, которые ни на что не влияют.
 */
class AdminAccessTest extends PortalTestBase {

    private static final String STAFF_USER = "warehouse@ahlers.com";

    @Autowired
    AuthService auth;

    @Autowired
    AdminAccessService access;

    @Autowired
    CatalogService catalogs;

    @AfterEach
    void restoreAccess() {
        PortalUser actor = portalAdmin();
        // кладовщик в демо-данных работает на складе Алматы
        access.saveWarehousesOfUser(actor, userId(STAFF_USER), List.of("WH-ALA"));
        // поклажедателю D-001 в демо-данных доступны оба склада
        access.saveWarehousesOfDepositor(actor, depositorId(ADMIN, DEPOSITOR),
                List.of("WH-ALA", "WH-KGD"));
    }

    /* ---------------- Склады кладовщика ---------------- */

    @Test
    void staffWarehousesGrantedAndRevoked() {
        PortalUser actor = portalAdmin();
        String id = userId(STAFF_USER);

        assertEquals(List.of("WH-ALA", "WH-KGD"),
                access.saveWarehousesOfUser(actor, id, List.of("WH-ALA", "WH-KGD")).stream().sorted().toList());
        assertEquals(List.of("WH-KGD"), access.saveWarehousesOfUser(actor, id, List.of("WH-KGD")));
        assertTrue(access.saveWarehousesOfUser(actor, id, List.of()).isEmpty(),
                "доступы можно снять полностью");
    }

    /** Несуществующий склад молча отбрасывается, а не ломает сохранение. */
    @Test
    void unknownWarehouseIgnored() {
        PortalUser actor = portalAdmin();
        List<String> result = access.saveWarehousesOfUser(actor, userId(STAFF_USER),
                List.of("WH-ALA", "WH-НЕТ-ТАКОГО"));
        assertEquals(List.of("WH-ALA"), result);
    }

    /* ---------------- Склады поклажедателя ---------------- */

    @Test
    void depositorWarehousesNarrowDocumentList() {
        PortalUser actor = portalAdmin();
        String d1 = depositorId(ADMIN, DEPOSITOR);

        // Сколько складов всего — спрашиваем базу, а не помним.
        // Здесь стояла двойка: столько складов в демо-данных сегодня.
        // Появится третий — и проверка упадёт на строках, которые
        // о числе складов не говорят вовсе.
        int allWarehouses = warehouseCount();
        assertTrue(allWarehouses >= 2,
                "для этой проверки нужно хотя бы два склада, а их " + allWarehouses);

        // оба склада — клиент видит оба
        access.saveWarehousesOfDepositor(actor, d1, List.of("WH-ALA", "WH-KGD"));
        signIn();
        assertEquals(2, warehouses().size(), "отмечены два склада — видно два");

        // оставили один — в документах остался один
        access.saveWarehousesOfDepositor(actor, d1, List.of("WH-ALA"));
        signIn();
        List<Map<String, Object>> single = warehouses();
        assertEquals(1, single.size());
        assertEquals("WH-ALA", single.getFirst().get("warehouseId"));

        // не отмечено ничего — ограничений нет, доступны все:
        // забытая настройка не должна оставлять клиента без складов
        access.saveWarehousesOfDepositor(actor, d1, List.of());
        signIn();
        assertEquals(allWarehouses, warehouses().size(),
                "без ограничений клиенту доступны все склады портала");
    }

    @Test
    void depositorListShowsWarehouses() {
        PortalUser actor = portalAdmin();
        access.saveWarehousesOfDepositor(actor, depositorId(ADMIN, DEPOSITOR), List.of("WH-ALA"));

        Map<String, Object> row = access.depositorAccess(actor).stream()
                .filter(d -> DEPOSITOR.equals(d.get("code")))
                .findFirst()
                .orElseThrow();
        assertEquals("Склад Алматы", row.get("warehouses"));
    }

    @Test
    void sectionOnlyForAdmin() {
        PortalUser client = auth.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> access.warehouses(client));
        expectRejection(403, () -> access.depositorAccess(client));
        expectRejection(403, () -> access.warehousesOfUser(client, userId(STAFF_USER)));
        expectRejection(403, () -> access.saveWarehousesOfDepositor(client,
                depositorId(ADMIN, DEPOSITOR), List.of("WH-ALA")));
    }

    /** Сколько складов в портале всего — чтобы не помнить это числом. */
    private int warehouseCount() {
        return jdbc.sql("SELECT count(*)::int FROM dbo.warehouse WHERE COALESCE(is_active, true)")
                .query(Integer.class)
                .single();
    }

    private List<Map<String, Object>> warehouses() {
        return catalogs.list(CatalogType.WAREHOUSES, false);
    }
}
