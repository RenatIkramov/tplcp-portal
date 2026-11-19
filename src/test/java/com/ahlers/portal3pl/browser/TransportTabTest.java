package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Транспортный раздел в черновике (V84).
 *
 * <p>Прежде в черновике поля транспорта были закрыты: правило Р5 «WMS
 * ещё не знает о документе». Теперь портал хранит транспорт до
 * подтверждения, и вкладка ведёт себя как часть шапки: поля открыты,
 * отдельной кнопки сохранения нет, набранное уходит общей кнопкой.
 */
class TransportTabTest extends BrowserTestBase {

    private static final String TAB = "transport";

    private boolean editable(String id) {
        return (Boolean) page.evaluate("id => { const e = document.getElementById(id);"
                + " return !!e && !e.readOnly && !e.disabled; }", id);
    }

    private void openTransportTab() {
        page.evaluate("t => PORTAL.card.tab('orders', t)", TAB);
        page.waitForSelector("#of_carNumber");
    }

    @Test
    void draftTransportIsEditableAndSavedWithTheDraft() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('orders')");
        page.waitForFunction("() => !!PORTAL.card.doc('orders')");
        // номер ERP — на общей вкладке; ставим, пока она на экране
        fillErp("orders");
        openTransportTab();

        assertTrue(editable("of_carNumber"), "в черновике поле транспорта закрыто");
        assertTrue(editable("of_driverName"));
        assertFalse((Boolean) page.evaluate(
                        "() => !!document.querySelector('[data-action=\"save-transport\"]')"),
                "в черновике осталась отдельная кнопка сохранения транспорта");
        assertTrue(visibleText().contains("Транспорт уйдёт в WMS вместе с документом"),
                "нет подсказки, когда транспорт уйдёт в WMS");

        page.fill("#of_carNumber", "123 ABC 02");
        page.evaluate("() => PORTAL.draft.save('orders')");
        page.waitForFunction("() => !!(PORTAL.card.doc('orders') && PORTAL.card.doc('orders').docId)");
        waitForQuiet();

        String docId = (String) page.evaluate("() => PORTAL.card.doc('orders').docId");
        page.evaluate("id => PORTAL.draft.open('orders', id)", docId);
        page.waitForFunction("id => PORTAL.card.doc('orders') && PORTAL.card.doc('orders').docId === id", docId);
        openTransportTab();
        assertEquals("123 ABC 02", page.inputValue("#of_carNumber"), "транспорт черновика не сохранился");
        assertNoJsErrors();
    }

    @AfterEach
    void removeDraft() {
        try {
            page.evaluate("() => { if (PORTAL.card.doc('orders'))"
                    + " PORTAL.draft.remove('orders'); }");
            waitForQuiet();
        } catch (RuntimeException ignored) {
            // карточка могла не открыться — убирать нечего
        }
    }
}
