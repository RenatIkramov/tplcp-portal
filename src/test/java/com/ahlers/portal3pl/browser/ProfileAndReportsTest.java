package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Личный кабинет после того, как prototype.js не стало (11.11.2026).
 * Экран «Отчёты» с 14.11.2026 на данных сервера — его проверяет
 * {@code ClientReportsScreenTest}.
 *
 * <p>ЗАЧЕМ. Кабинет переехал в js/portal/profile.js на данные сервера (ProfileTest), а то, на что
 * они опирались, — в модули: навигация и плашка в core.js, перерисовка
 * после смены языка в i18n.js. Проверок на эти экраны не было, и
 * пропавшее имя нашлось бы только нажатием. Кнопку «Сменить пароль»
 * прежде перехватывал admin.js по тексту кнопки — теперь она зовёт окно
 * сама, и это стоит проверить нажатием, а не чтением разметки.
 */
class ProfileAndReportsTest extends BrowserTestBase {

    @Test
    void profileShowsTheUserAndItsButtonOpensPasswordChange() {
        signInUiAsClient();
        page.evaluate("() => openProfile()");
        page.waitForSelector("#view-profile.active #profileContent h2");
        assertTrue(page.innerText("#profileContent").contains(USER),
                "в личном кабинете нет учётной записи вошедшего");
        // реквизиты клиента — из справочника, а не прочерки демо
        String contract = jdbc.sql("SELECT contract_number FROM dbo.depositors_for_user(:l::text)"
                        + " WHERE code = :c::text")
                .param("l", USER).param("c", DEPOSITOR).query(String.class).single();
        page.waitForFunction("c => document.getElementById('profileContent').innerText.includes(c)", contract);

        page.click("#profileContent .actions button");
        page.waitForSelector("#pwOverlay.active");
        assertNoJsErrors();
    }
}
