package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Журналы → Журнал действий» (V117): вкладка открывается, строки рисуются
 * словами, отбор по объекту уходит на сервер.
 *
 * <p>Неудачный вход делается заранее с выдуманным логином — так в журнале
 * наверняка есть строка, которую можно найти отбором «Вход».
 */
class AuditLogTabTest extends BrowserTestBase {

    @Test
    void adminSeesFailedSignInInTheLog() {
        String unknown = "nobody-" + System.nanoTime() + "@example.com";
        assertThrows(ResponseStatusException.class, () -> authService.authenticate(unknown, "x"));

        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => { showView('admin'); showAdminGroup('logs'); showAdminSub('audit'); }");
        page.waitForSelector("#admin-sub-audit.active #auditBody tr");
        // журнал открывается за последнюю неделю, а не за всю историю (V131)
        assertEquals(java.time.LocalDate.now().minusDays(6).toString(), page.inputValue("#auditFrom"),
                "журнал открылся не за последние 7 дней");

        page.selectOption("#auditObjectType", "login");
        page.click("#auditSearch");
        page.waitForFunction("l => document.getElementById('auditBody').innerText.includes(l)", unknown);
        String shown = page.innerText("#auditBody");
        String failed = jdbc.sql("SELECT value FROM dbo.ui_texts('ru') WHERE text_key = 'audit.action.login_failed'")
                .query(String.class).single();
        String reason = jdbc.sql("SELECT value FROM dbo.ui_texts('ru') WHERE text_key = 'audit.login.unknown'")
                .query(String.class).single();
        assertTrue(shown.contains(failed), "нет «Неудачный вход»: " + shown);
        assertTrue(shown.contains(reason), "причина не словами: " + shown);
        assertTrue(page.inputValue("#auditRetention").matches("\\d+"), "срок хранения не показан");
        assertNoJsErrors();
    }
}
