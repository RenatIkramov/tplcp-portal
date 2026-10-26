package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Окно смены пароля: язык можно сменить, не выходя из него.
 *
 * <p>ПОЧЕМУ ЭТО НЕ МЕЛОЧЬ. Человеку, которому пароль выдал
 * администратор, портал ничего не отдаёт до смены пароля (§6.2):
 * окно открывается сразу после входа и не закрывается. Переключатель
 * языка при этом был только в шапке портала — а шапка остаётся
 * под накладкой во весь экран. Выходило, что сменить язык можно
 * только после смены пароля, то есть ровно тогда, когда он уже
 * не нужен: прочесть подписи полей надо было раньше.
 *
 * <p>Вторая половина проверки — про то, что окно не застревает
 * на языке, на котором его собрали. Подписи в нём вписаны кодом
 * при создании, разметки с data-i18n там нет, и общий перевод
 * страницы до них не дотягивается. Это та же болезнь, что была
 * у панели фильтров, и лечится так же — пересборкой подписей.
 */
class PasswordDialogTest extends BrowserTestBase {

    @Test
    void languageCanBeChangedFromInsideTheDialog() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => portalOpenPasswordChange()");
        page.waitForSelector("#pwOverlay.active");

        assertNotNull(page.querySelector("#pwLang .lang-current"),
                "в окне смены пароля нет переключателя языка — шапка портала"
                        + " закрыта накладкой, сменить язык неоткуда");

        String ru = title();
        assertFalse(ru.isBlank(), "у окна нет заголовка");
        assertEquals(text("admin.text.change_password", "ru"), ru,
                "заголовок окна не из каталога текстов");

        // Меняем язык той же дорогой, что человек: чип, затем вариант
        // из списка. Проверять вызовом portalSetLanguage значило бы
        // проверить не переключатель, а функцию под ним.
        page.click("#pwLang .lang-current");
        page.click("#pwLang .lang-menu [data-locale='en']");
        page.waitForFunction("prev => {"
                + " const h = document.getElementById('pwTitle');"
                + " return h && h.textContent.trim() !== prev; }", ru);

        assertEquals(text("admin.text.change_password", "en"), title(),
                "окно осталось на прежнем языке — подписи не пересобрались");
        assertTrue(page.isVisible("#pwOverlay.active"),
                "смена языка закрыла окно смены пароля");

        // Поля ввода при пересборке не трогаются: набранный пароль
        // терять нельзя, а смена языка на полпути — обычное дело
        // именно здесь.
        page.fill("#pwNew", "Kinuta-4100");
        page.click("#pwLang .lang-current");
        page.click("#pwLang .lang-menu [data-locale='ru']");
        page.waitForFunction("prev => {"
                + " const h = document.getElementById('pwTitle');"
                + " return h && h.textContent.trim() !== prev; }",
                text("admin.text.change_password", "en"));
        assertEquals("Kinuta-4100", page.inputValue("#pwNew"),
                "смена языка стёрла набранный пароль");

        assertNoJsErrors();
        localeOf(user, "ru");   // переключатель настоящий, и язык остался на сервере
    }

    /**
     * Правила пароля отмечаются галочкой и после смены языка.
     *
     * <p>Подписи правил пересобираются, а отметки «выполнено» ставит
     * другой код. Перепутай их порядок — и человек увидит правила
     * без отметок ровно тогда, когда пароль уже набран правильно.
     */
    @Test
    void ruleMarksSurviveLanguageChange() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => portalOpenPasswordChange()");
        page.waitForSelector("#pwOverlay.active");

        page.fill("#pwNew", "Kinuta-4100");
        page.waitForFunction(
                "() => document.querySelector('#pwRules [data-pw-rule=\"length\"] .pw-mark')"
                        + ".textContent.trim() === '✓'");

        page.click("#pwLang .lang-current");
        page.click("#pwLang .lang-menu [data-locale='en']");
        page.waitForTimeout(400);

        assertEquals("✓", mark("length"), "после смены языка пропала отметка «длина»");
        assertEquals("✓", mark("case"), "после смены языка пропала отметка «регистр»");
        assertEquals("✓", mark("extra"), "после смены языка пропала отметка «цифра или знак»");
        assertFalse(ruleText("length").isBlank(), "у правила пропала подпись");
        assertNoJsErrors();
        localeOf(user, "ru");   // не оставляем соседним проверкам чужой язык
    }

    /* ---------------- помощники ---------------- */

    private String title() {
        return (String) page.evaluate(
                "() => document.getElementById('pwTitle').textContent.trim()");
    }

    private String mark(String rule) {
        return (String) page.evaluate(
                "r => document.querySelector('#pwRules [data-pw-rule=\"' + r + '\"] .pw-mark')"
                        + ".textContent.trim()", rule);
    }

    private String ruleText(String rule) {
        return (String) page.evaluate(
                "r => document.querySelector('#pwRules [data-pw-rule=\"' + r + '\"] [data-pw-text]')"
                        + ".textContent.trim()", rule);
    }

    /** Текст ключа на нужном языке — из каталога, а не вписанный сюда. */
    private String text(String key, String locale) {
        return jdbc.sql("""
                        SELECT t.value FROM dbo.ui_text t
                          JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                         WHERE t.text_key = :key::text AND l.code = :locale::text
                        """)
                .param("key", key)
                .param("locale", locale)
                .query(String.class)
                .single();
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
