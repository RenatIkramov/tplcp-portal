package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Настройки клиента — вкладки его карточки (V111, 11.11.2026).
 *
 * <p>Прежде разделы, поля и наименования полей открывались из отдельного
 * списка «Доступы поклажедателей». Теперь — из карточки в «Общих
 * справочниках». Проверяется то, что легко потерять при переезде:
 * вкладки есть и рисуют своё; галочка, снятая на одной вкладке,
 * переживает уход на другую; «Отмена» возвращает сохранённое, не уводя
 * из карточки. Ничего не сохраняется — настройки клиента не меняются.
 */
class DepositorCardTabsTest extends BrowserTestBase {

    @Test
    void settingsLiveInTheDepositorCard() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        String depositor = depositorId(ADMIN, DEPOSITOR);
        page.evaluate("id => portalOpenPortalDir('depositorsDir', id)", depositor);
        page.waitForSelector("#view-portaldir-detail.active #portalDirTabs [data-tab='sections']");

        @SuppressWarnings("unchecked")
        List<String> tabs = (List<String>) page.evaluate(
                "() => [...document.querySelectorAll('#portalDirTabs [data-tab]')].map(b => b.dataset.tab)");
        // «История» — последней (журнал действий, V118)
        assertEquals(List.of("requisites", "sections", "fields", "labels", "reports", "history"), tabs);

        page.click("#portalDirTabs [data-tab='fields']");
        page.waitForSelector("#portalDirBody input[data-column-id]");

        page.click("#portalDirTabs [data-tab='sections']");
        page.waitForSelector("#portalDirBody #depVisTransfers");
        boolean was = page.isChecked("#depVisTransfers");
        page.click("#depVisTransfers");

        // ушли на реквизиты и вернулись — несохранённая галочка на месте
        page.click("#portalDirTabs [data-tab='requisites']");
        page.waitForSelector("#portalDirBody .kv-grid");
        page.click("#portalDirTabs [data-tab='sections']");
        page.waitForSelector("#portalDirBody #depVisTransfers");
        assertEquals(!was, page.isChecked("#depVisTransfers"), "переход между вкладками потерял правку");

        // «Отмена» — как сохранено, и мы всё ещё в карточке
        page.click("#portalDirBody [data-dep-cancel]");
        page.waitForFunction("w => document.getElementById('depVisTransfers')"
                + " && document.getElementById('depVisTransfers').checked === w", was);
        assertTrue(page.isVisible("#view-portaldir-detail"), "«Отмена» увела из карточки");

        page.click("#portalDirTabs [data-tab='labels']");
        page.waitForFunction("() => document.getElementById('portalDirBody').children.length > 0");
        assertNoJsErrors();
    }
}
