package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Последние следы демо в разметке (V114, 11.11.2026).
 *
 * <p>Подвал прежде гласил «все данные вымышленные» и называл склад,
 * вписанный в разметку; теперь в нём организация оператора из
 * справочника — та же, что клиент видит в «Контактах». В «Ошибках
 * интеграции» стояли четыре выдуманные строки; до подключения WMS
 * журнал пуст и говорит об этом.
 */
class FooterTest extends BrowserTestBase {

    @Test
    void footerNamesTheOperatorOfTheClient() {
        signInUiAsClient();
        String name = jdbc.sql("""
                        SELECT o.name FROM dbo.contact_operators(
                               (SELECT depositor_id FROM dbo.depositors_for_user(:l::text) WHERE code = :c::text)) o
                         ORDER BY o.name LIMIT 1""")
                .param("l", USER).param("c", DEPOSITOR).query(String.class).single();
        page.waitForFunction("n => document.getElementById('portalFooter').textContent.includes(n)", name);
        String text = page.textContent("#portalFooter");
        assertFalse(text.contains("вымышл"), "в подвале осталась строка демо");
        assertEquals(0, ((Number) page.evaluate("() => document.querySelectorAll('.demo-flag').length")).intValue());
        assertNoJsErrors();
    }

    @Test
    void integrationLogHasNoInventedRows() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => { showView('admin'); showAdminGroup('logs'); showAdminSub('integration'); }");
        page.waitForSelector("#admin-sub-integration.active #integrationLogBody tr");
        assertEquals(1, ((Number) page.evaluate(
                "() => document.querySelectorAll('#integrationLogBody tr').length")).intValue());
        assertTrue(page.isVisible("#integrationLogBody [data-i18n='admin.text.integration_log_empty']"),
                "журнал интеграции без пояснения, что обмена ещё не было");
        assertNoJsErrors();
    }
}
