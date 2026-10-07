package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Создание и правка пользователей администратором (V19).
 * <p>
 * Каждый тест убирает за собой заведённые учётки: база одна и та же,
 * и мусор мешал бы следующему запуску.
 */
class AdminUserTest extends PortalTestBase {

    private static final String NEW_LOGIN = "p.petrov.test@ahlers.com";

    @Autowired
    AuthService auth;

    @Autowired
    AdminUserService admins;

    @AfterEach
    void removeTestUsers() {
        jdbc.sql("""
                DELETE FROM dbo.user_depositor_access
                 WHERE user_id IN (SELECT user_id FROM dbo.app_user WHERE login LIKE 'p.%.test@%')
                """).update();
        jdbc.sql("""
                DELETE FROM dbo.user_settings
                 WHERE user_id IN (SELECT user_id FROM dbo.app_user WHERE login LIKE 'p.%.test@%')
                """).update();
        jdbc.sql("DELETE FROM dbo.app_user WHERE login LIKE 'p.%.test@%'").update();
    }

    private AdminUserService.Profile profileOf(String login, String role, List<String> depositors) {
        return new AdminUserService.Profile(login, "Петров Пётр Петрович", role,
                "Менеджер", "Ahlers Logistics", "+7 701 000 00 00", depositors);
    }

    @Test
    void createdUserCanSignIn() {
        PortalUser actor = portalAdmin();
        Map<String, Object> response = admins.create(actor,
                profileOf(NEW_LOGIN, "manager", List.of()), "Parol-2026");

        assertEquals("Parol-2026", response.get("password"));
        PortalUser newPassword = auth.authenticate(NEW_LOGIN, "Parol-2026");
        assertEquals("manager", newPassword.roleCode());
        assertTrue(newPassword.mustChangePassword(), "пароль выдал администратор — сменить при входе");
    }

    /** Пароль можно не задавать: портал придумает его и вернёт администратору. */
    @Test
    void portalGeneratesPassword() {
        Map<String, Object> response = admins.create(portalAdmin(),
                profileOf(NEW_LOGIN, "depositor", List.of()), null);

        String password = String.valueOf(response.get("password"));
        assertTrue(password.length() >= 8, "придуманный пароль отвечает требованиям: " + password);
        assertEquals(NEW_LOGIN, auth.authenticate(NEW_LOGIN, password).login());
    }

    @Test
    void takenLoginAndEmptyFieldsRejected() {
        PortalUser actor = portalAdmin();
        expectRejection(409, () -> admins.create(actor, profileOf(USER, "depositor", List.of()), "Parol-2026"));
        // логин занят без учёта регистра
        expectRejection(409, () -> admins.create(actor,
                profileOf(ADMIN.toUpperCase(), "admin", List.of()), "Parol-2026"));
        expectRejection(400, () -> admins.create(actor, profileOf("", "depositor", List.of()), "Parol-2026"));
        expectRejection(400, () -> admins.create(actor,
                profileOf(NEW_LOGIN, "кладовщик-которого-нет", List.of()), "Parol-2026"));
    }

    @Test
    void depositorAccessGrantedAndRevoked() {
        PortalUser actor = portalAdmin();
        String d1 = depositorId(ADMIN, DEPOSITOR);
        String d2 = depositorId(ADMIN, OTHER_DEPOSITOR);

        String id = String.valueOf(admins.create(actor,
                profileOf(NEW_LOGIN, "manager", List.of(d1, d2)), "Parol-2026").get("userId"));
        assertEquals(2, admins.depositorsOfUser(actor, id).size());
        assertEquals(2, auth.availableDepositors(NEW_LOGIN).size(),
                "выданные доступы сразу видны при входе");

        admins.saveProfile(actor, id, profileOf(NEW_LOGIN, "manager", List.of(d1)));
        assertEquals(List.of(d1), admins.depositorsOfUser(actor, id));

        admins.saveProfile(actor, id, profileOf(NEW_LOGIN, "manager", List.of()));
        assertTrue(admins.depositorsOfUser(actor, id).isEmpty(), "доступы можно снять полностью");
    }

    @Test
    void profileAndLoginChange() {
        PortalUser actor = portalAdmin();
        String id = String.valueOf(admins.create(actor,
                profileOf(NEW_LOGIN, "manager", List.of()), "Parol-2026").get("userId"));

        admins.saveProfile(actor, id, new AdminUserService.Profile(
                "p.ivanov.test@ahlers.com", "Иванов Иван Иванович", "staff",
                "Кладовщик", "Ahlers Logistics", "+7 702 111 22 33", null));

        // String.valueOf: тест не должен зависеть от того, как драйвер
        // отдаёт идентификатор — строкой или объектом UUID
        Map<String, Object> row = admins.users(actor).stream()
                .filter(u -> id.equals(String.valueOf(u.get("userId"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "созданный пользователь не найден в справочнике, id=" + id));
        assertEquals("Иванов Иван Иванович", row.get("fullName"));
        assertEquals("p.ivanov.test@ahlers.com", row.get("login"));
        assertEquals("staff", row.get("roleCode"));
        assertEquals("Кладовщик", row.get("userPosition"));

        // вход работает по новому логину, по старому — нет
        assertEquals("p.ivanov.test@ahlers.com",
                auth.authenticate("p.ivanov.test@ahlers.com", "Parol-2026").login());
        expectRejection(401, () -> auth.authenticate(NEW_LOGIN, "Parol-2026"));
    }

    /** Себе роль менять нельзя: администратор лишил бы себя доступа к разделу. */
    @Test
    void cannotChangeOwnRole() {
        PortalUser actor = portalAdmin();
        String ownId = userId(ADMIN);

        expectRejection(409, () -> admins.saveProfile(actor, ownId, new AdminUserService.Profile(
                ADMIN, "Тестовый администратор", "depositor", null, null, null, null)));

        // а имя и телефон — пожалуйста
        admins.saveProfile(actor, ownId, new AdminUserService.Profile(
                ADMIN, "Тестовый администратор", "admin", "Проверка",
                "Ahlers Logistics", null, null));
        assertFalse(admins.users(actor).isEmpty());
    }

    @Test
    void nonAdminCannotCreateUser() {
        PortalUser client = auth.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> admins.create(client,
                profileOf(NEW_LOGIN, "admin", List.of()), "Parol-2026"));
        expectRejection(403, () -> admins.roles(client));
        expectRejection(403, () -> admins.depositors(client));
    }
}
