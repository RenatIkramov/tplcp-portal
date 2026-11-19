package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Доп. поля шапки документа и «Доступы поклажедателей» (03.11.2026).
 *
 * <p>Закрытое поклажедателю доп. поле в карточке не рисуется; закрыты
 * все десять — нет и вкладки «Дополнительная информация».
 */
class ExtraInfoVisibilityTest extends BrowserTestBase {

    private static final String TAB = "Дополнительная информация";

    @Autowired
    ColumnService columnService;

    private Runnable restore;

    /** Закрыть перечисленные доп. поля заказа, остальные — открыть. */
    private void close(int... fields) {
        PortalUser admin = portalAdmin();
        String depositor = depositorId(USER, DEPOSITOR);
        restore = keepDepositorSetup(DEPOSITOR);
        java.util.Set<Integer> closed = new java.util.HashSet<>();
        for (int i : fields) {
            closed.add(i);
        }
        for (int i = 1; i <= 10; i++) {
            columnService.saveDepositorColumn(admin, depositor, "orders", "customField" + i,
                    !closed.contains(i), false);
        }
    }

    private void openNewOrder() {
        ensurePassword(USER, USER_PASSWORD);
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
        waitForColumns("orders");
        page.evaluate("() => PORTAL.draft.create('orders')");
        page.waitForFunction("() => !!PORTAL.card.doc('orders')");
    }

    private boolean hasTab() {
        return (Boolean) page.evaluate("t => Array.from(document.querySelectorAll('.admin-subtab-btn'))"
                + ".some(b => b.offsetParent !== null && b.textContent.trim() === t)", TAB);
    }

    private boolean present(String id) {
        return (Boolean) page.evaluate("id => !!document.getElementById(id)", id);
    }

    @Test
    void closedFieldIsNotDrawn() {
        close(3);
        openNewOrder();
        assertTrue(hasTab(), "открытые доп. поля есть — вкладка должна остаться");
        page.evaluate("() => PORTAL.card.tab('orders', 'extra')");
        page.waitForSelector("#of_customField1");
        assertFalse(present("of_customField3"), "закрытое доп. поле нарисовано в карточке");
        assertTrue(present("of_customField4"));
        assertNoJsErrors();
    }

    @Test
    void allClosedHideTheTab() {
        close(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        openNewOrder();
        assertFalse(hasTab(), "все доп. поля закрыты, а вкладка осталась");
        assertNoJsErrors();
    }

    @AfterEach
    void reopen() {
        // настройки «Доступов» — как были до проверки
        if (restore != null) {
            restore.run();
        }
    }
}
