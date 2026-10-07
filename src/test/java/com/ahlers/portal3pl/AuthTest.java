package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        List<Map<String, Object>> items = auth.availableDepositors("n.nazarbayev@ahlers.com");
        assertEquals(2, items.size());
    }

    @Test
    void foreignDepositorForbidden() {
        String ownId = depositorId(USER, DEPOSITOR);
        String foreignId = depositorId("n.nazarbayev@ahlers.com", OTHER_DEPOSITOR);

        assertTrue(auth.mayAccess(USER, ownId));
        assertTrue(!auth.mayAccess(USER, foreignId), "клиенту недоступен чужой поклажедатель");
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
