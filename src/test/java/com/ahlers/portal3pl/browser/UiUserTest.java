package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вошедший на экране — с полями как в ответах сервера (аудит 11.11.2026, D4).
 *
 * <p>НАЙДЕНО АУДИТОМ. auth.js собирал пользователя «в том виде, в каком его
 * ждёт интерфейс демо»: {@code full_name}, {@code manager}, — а права
 * пересчитывал в двух местах разными строками. Прототипа давно нет; имена
 * теперь серверные, правило прав — одно.
 */
class UiUserTest extends BrowserTestBase {

    @Test
    void signedInUserLooksLikeServerAnswer() {
        signInUiAsClient();
        page.waitForFunction("() => !!PORTAL.uiUser");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) page.evaluate(
                "() => ({fullName: PORTAL.uiUser.fullName, demo: 'full_name' in PORTAL.uiUser || 'manager' in PORTAL.uiUser,"
                        + " line: document.getElementById('managerLine').textContent})");
        assertEquals(singleValue("SELECT full_name FROM dbo.app_user WHERE login = '" + USER + "'"),
                user.get("fullName"), "имя вошедшего не то");
        assertFalse(Boolean.TRUE.equals(user.get("demo")), "у вошедшего снова поля демо: full_name / manager");
        assertFalse(String.valueOf(user.get("line")).isBlank(), "строка режима под шапкой пуста");

        // права с сервера на лету — тем же правилом, что при входе
        jdbc.sql("UPDATE dbo.user_settings SET hide_billing_tab = true"
                        + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = :l)")
                .param("l", USER).update();
        page.evaluate("() => PORTAL.refreshSettings()");
        page.waitForFunction("() => PORTAL.uiUser.hiddenTabs.includes('billing')");
        assertTrue(sectionHidden("billing"), "скрытый раздел не скрыт после обновления настроек");
        assertNoJsErrors();
    }

    private boolean sectionHidden(String tab) {
        return Boolean.TRUE.equals(page.evaluate("t => PORTAL.sectionHidden(t)", tab));
    }
}
