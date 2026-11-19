package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.config.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Личный кабинет (ProfileService, 11.11.2026).
 *
 * <p>Прежде ниже шапки было демо с прочерками. Проверяется, что каждый
 * раздел приходит из того же места, что и в остальном портале:
 * реквизиты — из справочника поклажедателей выбранного клиента,
 * менеджеры — «свои» со страницы «Контакты», склады — открытые человеку
 * (V110). И что чужого кабинет не отдаёт: без выбранного клиента
 * реквизитов и менеджеров нет.
 */
class ProfileTest extends PortalTestBase {

    @Autowired
    ProfileService profiles;

    @Test
    @SuppressWarnings("unchecked")
    void clientSeesOwnOrganizationManagersAndWarehouses() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        Map<String, Object> p = profiles.profile(user);

        Map<String, Object> org = (Map<String, Object>) p.get("organization");
        assertEquals(DEPOSITOR, org.get("code"));
        String contract = new TransactionTemplate(txManager).execute(tx -> jdbc.sql(
                        "SELECT contract_number FROM dbo.depositors_for_user(:l::text) WHERE code = :c::text")
                .param("l", USER).param("c", DEPOSITOR).query(String.class).single());
        assertEquals(contract, org.get("contractNumber"), "номер договора не из справочника");

        List<String> own = new TransactionTemplate(txManager).execute(tx -> jdbc.sql(
                        "SELECT email FROM dbo.contact_managers(:d::uuid) WHERE is_yours ORDER BY 1")
                .param("d", depositorId(USER, DEPOSITOR)).query(String.class).list());
        assertEquals(own, ((List<Map<String, Object>>) p.get("managers")).stream()
                .map(m -> String.valueOf(m.get("email"))).sorted().toList(), "менеджеры — не «свои»");

        // PersonalSettings начинает тест с «все склады» — значит, все действующие
        List<String> active = jdbc.sql("SELECT warehouse_id FROM dbo.warehouse WHERE is_active ORDER BY 1")
                .query(String.class).list();
        assertEquals(active, ((List<Map<String, Object>>) p.get("warehouses")).stream()
                .map(w -> String.valueOf(w.get("warehouseId"))).sorted().toList());
        assertTrue(String.valueOf(p.get("roleName")).length() > 0, "нет названия роли");
    }

    @Test
    @SuppressWarnings("unchecked")
    void closedWarehouseIsNotListed() {
        String id = userId(USER);
        owner().jdbc.sql("UPDATE dbo.app_user SET all_warehouses = false WHERE user_id = :id::uuid").param("id", id).update();
        owner().jdbc.sql("DELETE FROM dbo.user_warehouse_access WHERE user_id = :id::uuid").param("id", id).update();
        owner().jdbc.sql("INSERT INTO dbo.user_warehouse_access (user_id, warehouse_id) VALUES (:id::uuid, 'WH-ALA')")
                .param("id", id).update();
        signIn();
        Map<String, Object> p = profiles.profile(authService.authenticate(USER, USER_PASSWORD));
        assertEquals(new TreeSet<>(List.of("WH-ALA")), new TreeSet<>(((List<Map<String, Object>>) p.get("warehouses"))
                .stream().map(w -> String.valueOf(w.get("warehouseId"))).toList()));
    }

    /** Администратор без выбранного клиента: зона доступа есть, чужих реквизитов нет. */
    @Test
    void adminWithoutChosenDepositorGetsScopeOnly() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, null);
        Map<String, Object> p = profiles.profile(admin);
        assertEquals(Boolean.TRUE, p.get("allDepositors"));
        assertNull(p.get("organization"));
        assertTrue(((List<?>) p.get("managers")).isEmpty());
        assertTrue(!((List<?>) p.get("depositors")).isEmpty(), "у администратора нет поклажедателей");
    }
}
