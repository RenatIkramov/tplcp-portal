package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PasswordService;
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
 * Смена пароля и справочник «Пользователи» (этап 5б).
 * <p>
 * Тесты меняют пароли в базе, поэтому каждый возвращает исходный:
 * демо-пароль Portal123! для обычных пользователей.
 */
class PasswordTest extends PortalTestBase {

    private static final String PASSWORD = "Portal123!";
    private static final String NEW_PASSWORD = "Vesma-nadezhno-77";


    @Autowired
    AuthService auth;

    @Autowired
    PasswordService passwords;

    @Autowired
    AdminUserService admins;

    @AfterEach
    void restorePasswords() {
        // пароль живой учётки `admin` тесты не трогают: он временный по
        // замыслу портала, человек меняет его при первом входе
        restore(USER, PASSWORD, false);
    }

    private void restore(String login, String password, boolean forceChange) {
        String id = userId(login);
        passwords.setByAdmin(id, login, password);
        if (!forceChange) {
            jdbc.sql("UPDATE dbo.app_user SET must_change_password = false WHERE user_id = :id::uuid")
                    .param("id", id).update();
        }
    }

    /* ---------------- Пользователь меняет свой пароль ---------------- */

    @Test
    void userChangesOwnPassword() {
        PortalUser user = auth.authenticate(USER, PASSWORD);
        PortalUser after = passwords.changeOwn(user, PASSWORD, NEW_PASSWORD);

        assertFalse(after.mustChangePassword(), "после своей смены флаг снимается");
        assertEquals(USER, auth.authenticate(USER, NEW_PASSWORD).login(), "вход по новому паролю");
        expectRejection(401, () -> auth.authenticate(USER, PASSWORD));
    }

    /** Без текущего пароля сменить нельзя: иначе чужая сессия = чужая учётка. */
    @Test
    void changeRequiresCurrentPassword() {
        PortalUser user = auth.authenticate(USER, PASSWORD);
        expectRejection(403, () -> passwords.changeOwn(user, "не тот", NEW_PASSWORD));
        assertEquals(USER, auth.authenticate(USER, PASSWORD).login(), "старый пароль цел");
    }

    @Test
    void weakPasswordRejected() {
        PortalUser user = auth.authenticate(USER, PASSWORD);
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, "Parol1"));          // 6 символов
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, "parol123"));        // нет заглавных
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, "PAROL123"));        // нет строчных
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, "ParolParol"));      // нет цифры и знака
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, "Парольный1"));      // кириллица
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, "Client-parol1"));   // содержит логин
        expectRejection(400, () -> passwords.changeOwn(user, PASSWORD, PASSWORD));            // тот же

        // минимум по требованиям: 8 символов, оба регистра, цифра или знак
        passwords.changeOwn(user, PASSWORD, "Parol123");
    }

    /** Знак вместо цифры тоже подходит. */
    @Test
    void symbolInsteadOfDigitAllowed() {
        PortalUser user = auth.authenticate(USER, PASSWORD);
        passwords.changeOwn(user, PASSWORD, "Parol-parol");
        assertEquals(USER, auth.authenticate(USER, "Parol-parol").login());
    }

    /* ---------------- Пароль от администратора ---------------- */

    /** Учётка администратора портала из V18 на месте и с нужной ролью. */
    @Test
    void portalAdminAccountExists() {
        Map<String, Object> row = admins.users(portalAdmin()).stream()
                .filter(u -> "admin".equals(u.get("login")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("учётка admin не найдена"));
        assertEquals("admin", row.get("roleCode"));
        assertEquals(true, row.get("isActive"));
    }

    @Test
    void adminIssuedPasswordMustBeChanged() {
        PortalUser admin = portalAdmin();
        String id = userId(USER);

        admins.setPassword(admin, id, NEW_PASSWORD);

        PortalUser client = auth.authenticate(USER, NEW_PASSWORD);
        assertTrue(client.mustChangePassword(), "выданный пароль надо сменить при входе");

        // и сам пользователь снимает флаг, задав свой
        PortalUser after = passwords.changeOwn(client, NEW_PASSWORD, "Svoy-parol-2026");
        assertFalse(after.mustChangePassword());
    }

    /** Раздел администрирования закрыт для остальных ролей (§6.2). */
    @Test
    void nonAdminCannotListUsers() {
        PortalUser client = auth.authenticate(USER, PASSWORD);
        expectRejection(403, () -> admins.users(client));
        expectRejection(403, () -> admins.setPassword(client, userId(ADMIN), NEW_PASSWORD));
    }

    /* ---------------- Справочник «Пользователи» ---------------- */

    @Test
    void userListShowsScope() {
        PortalUser admin = portalAdmin();
        List<Map<String, Object>> items = admins.users(admin);

        assertTrue(items.size() >= 8, "в демо-данных восемь учёток плюс admin");
        Map<String, Object> client = items.stream()
                .filter(u -> USER.equals(u.get("login")))
                .findFirst()
                .orElseThrow();
        assertEquals("depositor", client.get("roleCode"));
        assertEquals("ТОО «Рога и Копыта»", client.get("scope"));

        Map<String, Object> manager = items.stream()
                .filter(u -> "n.nazarbayev@ahlers.com".equals(u.get("login")))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(manager.get("scope")).contains(","),
                "у менеджера в зоне два поклажедателя");
    }

    @Test
    void visibilitySettingsSaved() {
        PortalUser admin = portalAdmin();
        String id = userId(USER);

        // «Показать раздел перемещения» в карточке человека больше нет:
        // с V35 это настройка поклажедателя (см. ColumnDirectoryTest).
        admins.save(admin, id, new AdminUserService.Settings(
                true, true, false, true, true, false, true, true, false));
        Map<String, Object> after = row(admins.users(admin), USER);
        assertEquals(true, after.get("hideBillingTab"));
        assertEquals(false, after.get("canCreateTransfers"));
        assertEquals(false, after.get("canEditTransport"));

        // возвращаем как было, чтобы не ломать остальные тесты
        admins.save(admin, id, new AdminUserService.Settings(
                true, false, false, true, true, true, true, true, true));
        Map<String, Object> restored = row(admins.users(admin), USER);
        assertEquals(false, restored.get("hideBillingTab"));
        assertEquals(true, restored.get("canEditTransport"));
    }

    /** Администратор не может заблокировать себя — иначе портал без администратора. */
    @Test
    void cannotBlockYourself() {
        PortalUser admin = portalAdmin();
        String ownId = userId(ADMIN);
        expectRejection(409, () -> admins.save(admin, ownId, new AdminUserService.Settings(
                false, false, false, true, true, true, true, true, true)));
    }

    private Map<String, Object> row(List<Map<String, Object>> items, String login) {
        return items.stream()
                .filter(u -> login.equals(u.get("login")))
                .findFirst()
                .orElseThrow();
    }
}
