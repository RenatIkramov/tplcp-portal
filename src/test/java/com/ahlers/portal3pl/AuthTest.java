package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вход в портал и доступы (этап 5).
 * Пароль демо-пользователей задан миграцией V17 — Portal123!
 */
class AuthTest extends PortalTestBase {

    private static final String PASSWORD = "Portal123!";

    @Autowired
    AuthService auth;

    @Test
    void signsInWithCorrectPassword() {
        PortalUser user = auth.authenticate(USER, PASSWORD);
        assertEquals(USER, user.login());
        assertEquals("depositor", user.roleCode());
    }

    @Test
    void wrongPasswordRejected() {
        expectRejection(401, () -> auth.authenticate(USER, "неверный"));
    }

    /** По тексту ошибки нельзя отличить «нет такого пользователя» от «неверный пароль». */
    @Test
    void unknownUserLooksLikeWrongPassword() {
        expectRejection(401, () -> auth.authenticate("нет-такого@ahlers.com", PASSWORD));
    }

    /** §6.2: заблокированная учётная запись не входит даже с верным паролем. */
    @Test
    void blockedUserCannotSignIn() {
        expectRejection(401, () -> auth.authenticate("blocked@roga-kopyta.kz", PASSWORD));
    }

    @Test
    void clientSeesOnlyOwnDepositor() {
        List<Map<String, Object>> items = auth.availableDepositors(USER);
        assertEquals(1, items.size());
        assertEquals("D-001", items.getFirst().get("code"));
    }

    @Test
    void managerSeesSeveralDepositors() {
        // Сверяется с тем, что менеджеру выдано, а не с запомненной
        // двойкой: выдач в демо-данных может стать больше, и проверка
        // падала бы, ничего не проверив. Правило же такое — менеджер
        // видит ровно тех, кого ему выдали: не меньше (иначе он
        // не доберётся до клиента) и не больше (иначе это утечка).
        String manager = "n.nazarbayev@ahlers.com";
        Integer granted = jdbc.sql("SELECT count(*)::int FROM dbo.user_depositor_access ud"
                        + " JOIN dbo.app_user u ON u.user_id = ud.user_id"
                        + " WHERE u.login = :login::text")
                .param("login", manager)
                .query(Integer.class)
                .single();
        assertTrue(granted >= 2,
                "менеджеру выдан " + granted + " поклажедатель — проверять «несколько» не на чем");
        assertEquals(granted.intValue(), auth.availableDepositors(manager).size(),
                "менеджер видит не тех поклажедателей, что ему выданы");
    }

    @Test
    void foreignDepositorForbidden() {
        String ownId = depositorId(USER, DEPOSITOR);
        String foreignId = depositorId("n.nazarbayev@ahlers.com", OTHER_DEPOSITOR);

        assertTrue(auth.mayAccess(USER, ownId));
        assertFalse(auth.mayAccess(USER, foreignId), "клиенту недоступен чужой поклажедатель");
        assertTrue(auth.mayAccess("n.nazarbayev@ahlers.com", foreignId), "менеджеру доступен");
    }

    /** Без выбранного поклажедателя данные не отдаются (§6.4). */
    @Test
    void noDataWithoutDepositorChoice() {
        signInAs(USER, null);
        expectRejection(409, () -> stockService().list(null, null));
    }

    @Autowired
    com.ahlers.portal3pl.stock.StockService stock;

    private com.ahlers.portal3pl.stock.StockService stockService() {
        return stock;
    }
}
