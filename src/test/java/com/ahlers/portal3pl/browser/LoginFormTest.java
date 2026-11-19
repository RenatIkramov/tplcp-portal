package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Набранный пароль не стирается, пока человек ещё не вошёл (11.11.2026).
 *
 * <p>ЗАЧЕМ. Любой ответ 401 портал считал концом сессии: возвращал на
 * экран входа и стирал пароль. Но до входа 401 — обычный ответ, а запросы
 * до входа уходят: смена языка карточки входа пересобирает панели
 * фильтров, и они просят справочник колонок. Пароль, набранный в эти
 * доли секунды, пропадал, и вход отвечал «введите логин и пароль».
 * Так падали браузерные проверки — в песочнице раз за разом, по таймауту
 * на входе, — и ровно так же это увидел бы человек, который быстро
 * набирает пароль сразу после открытия страницы.
 */
class LoginFormTest extends BrowserTestBase {

    @Test
    void refusalBeforeSignInKeepsTheTypedPassword() {
        page.navigate(baseUrl() + "/");
        page.fill("#loginEmail", USER);
        page.fill("#loginPassword", USER_PASSWORD);

        // запрос, который до входа получит 401
        int status = ((Number) page.evaluate(
                "async () => (await fetch('/api/stock')).status")).intValue();
        assertEquals(401, status, "до входа запасы должны отвечать отказом — иначе проверять нечего");

        assertEquals(USER_PASSWORD, page.inputValue("#loginPassword"),
                "отказ до входа стёр набранный пароль");
        assertFalse(page.isVisible("#portalLoginError"),
                "до входа портал сказал, что сессия закончилась");

        signInUiAsClient();
        assertNoJsErrors();
    }
}
