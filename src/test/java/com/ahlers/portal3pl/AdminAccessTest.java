package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.AdminAccessService;
import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import com.ahlers.portal3pl.stock.StockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Склады человека (V110): выдаются в карточке пользователя и закрывают
 * и выбор, и данные.
 * <p>
 * Самое важное здесь — проверка данных: неотмеченный склад должен
 * пропасть не только из списка выбора, но и из запасов. Иначе это
 * галочки, которые ни на что не влияют, — и выглядит работающим.
 * <p>
 * Клиента {@code USER} возвращает как было {@link PersonalSettings};
 * сотрудника склада — {@link #restoreStaff()}.
 */
class AdminAccessTest extends PortalTestBase {

    private static final String STAFF_USER = "warehouse@ahlers.com";

    @Autowired
    AuthService auth;

    @Autowired
    AdminAccessService access;

    @Autowired
    AdminUserService admins;

    @Autowired
    CatalogService catalogs;

    @Autowired
    StockService stock;

    @AfterEach
    void restoreStaff() {
        // сотрудник склада в демо-данных работает на складе Алматы
        scope(STAFF_USER, false, List.of("WH-ALA"));
    }

    /** Склады и признак «все склады» — через карточку, как это делает экран. */
    private void scope(String login, boolean allWarehouses, List<String> warehouseIds) {
        PortalUser actor = portalAdmin();
        Map<String, Object> row = admins.users(actor).stream()
                .filter(u -> login.equals(u.get("login")))
                .findFirst().orElseThrow();
        admins.saveProfile(actor, userId(login), new AdminUserService.Profile(
                login, (String) row.get("fullName"), (String) row.get("roleCode"),
                (String) row.get("userPosition"), (String) row.get("organization"),
                (String) row.get("phone"), null, null, allWarehouses, warehouseIds));
    }

    @Test
    void userWarehousesGrantedAndRevoked() {
        String id = userId(STAFF_USER);
        PortalUser actor = portalAdmin();

        scope(STAFF_USER, false, List.of("WH-ALA", "WH-KGD"));
        assertEquals(List.of("WH-ALA", "WH-KGD"),
                access.warehousesOfUser(actor, id).stream().sorted().toList());
        scope(STAFF_USER, false, List.of("WH-KGD"));
        assertEquals(List.of("WH-KGD"), access.warehousesOfUser(actor, id));
        scope(STAFF_USER, false, List.of());
        assertTrue(access.warehousesOfUser(actor, id).isEmpty(), "склады можно снять полностью");
    }

    /** Несуществующий склад молча отбрасывается, а не ломает сохранение. */
    @Test
    void unknownWarehouseIgnored() {
        scope(STAFF_USER, false, List.of("WH-ALA", "WH-НЕТ-ТАКОГО"));
        assertEquals(List.of("WH-ALA"), access.warehousesOfUser(portalAdmin(), userId(STAFF_USER)));
    }

    @Test
    void closedWarehouseHidesChoiceAndData() {
        int allWarehouses = warehouseCount();
        assertTrue(allWarehouses >= 2,
                "для этой проверки нужно хотя бы два склада, а их " + allWarehouses);
        Set<String> everywhere = stockWarehouses(null);
        assertTrue(everywhere.contains("WH-ALA") && everywhere.size() >= 2,
                "у клиента должны быть остатки на Алматы и ещё где-то: " + everywhere);

        // «все склады» — видно всё
        scope(USER, true, List.of());
        signIn();
        assertEquals(allWarehouses, warehouses().size(), "все склады — в выборе все");
        assertEquals(everywhere, stockWarehouses(USER), "все склады — в запасах все");
        assertEquals(everywhere, warehousesInStockList(), "все склады — список запасов полный");

        // только Алматы — и в выборе, и в запасах
        scope(USER, false, List.of("WH-ALA"));
        signIn();
        List<Map<String, Object>> single = warehouses();
        assertEquals(1, single.size());
        assertEquals("WH-ALA", single.getFirst().get("warehouseId"));
        assertEquals(Set.of("WH-ALA"), stockWarehouses(USER), "закрытый склад остался в запасах");
        assertEquals(Set.of("WH-ALA"), warehousesInStockList(),
                "закрытый склад остался в списке «Запасы» (DepositorScope не передал логин?)");

        // ничего не отмечено и «все» снят — не видно ничего
        scope(USER, false, List.of());
        signIn();
        assertTrue(warehouses().isEmpty(), "ничего не выдано — выбирать не из чего");
        assertTrue(stockWarehouses(USER).isEmpty(), "ничего не выдано — запасов не видно");
    }

    /** Новый склад при «все склады» доступен сам — отмечать не нужно. */
    @Test
    void allWarehousesIsAFlagNotATickOfEveryRow() {
        scope(USER, true, List.of("WH-ALA"));
        assertEquals(List.of("WH-ALA"), access.warehousesOfUser(portalAdmin(), userId(USER)),
                "галочки при «все» хранятся как были — снял флажок, вернулся выбор");
        signIn();
        assertEquals(warehouseCount(), warehouses().size());
    }

    @Test
    void sectionOnlyForAdmin() {
        PortalUser client = auth.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> access.warehouses(client));
        expectRejection(403, () -> access.warehousesOfUser(client, userId(STAFF_USER)));
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

    /**
     * Склады, на которых клиент видит остатки, — запросом под portal_app,
     * с тем же сеансом базы, что ставит DepositorScope. login == null —
     * служебный запрос без человека: ограничения складов нет.
     */
    private Set<String> stockWarehouses(String login) {
        String depositor = depositorId(USER, DEPOSITOR);
        return new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true), set_config('app.user_login', :l, true)")
                    .param("d", depositor)
                    .param("l", login == null ? "" : login)
                    .query().listOfRows();
            return new TreeSet<>(jdbc.sql("SELECT DISTINCT warehouse_id FROM dbo.stock")
                    .query(String.class).list());
        });
    }

    /** То же — через сам список «Запасы», как его просит экран. */
    @SuppressWarnings("unchecked")
    private Set<String> warehousesInStockList() {
        Map<String, String> params = new HashMap<>();
        params.put("pageSize", "500");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) stock
                .list(auth.authenticate(USER, USER_PASSWORD), params).get("rows");
        Set<String> found = new TreeSet<>();
        for (Map<String, Object> r : rows) {
            Object w = r.get("warehouseId");
            if (w != null) {
                found.add(String.valueOf(w));
            }
        }
        return found;
    }
}
