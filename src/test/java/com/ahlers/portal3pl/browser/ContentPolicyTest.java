package com.ahlers.portal3pl.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Портал работает под полным Content-Security-Policy, а чужой встроенный
 * скрипт под ним не выполняется (аудит 11.11.2026, A3).
 *
 * <p>Здесь браузер соблюдает правило портала ({@link #strictContentPolicy}),
 * поэтому проверка обходится без {@code page.evaluate}: правило запрещает
 * код строкой, и Playwright-у в том числе. Только навигация, щелчки, ввод
 * и сообщения консоли.
 */
class ContentPolicyTest extends BrowserTestBase {

    @Override
    protected boolean strictContentPolicy() {
        return true;
    }

    private final List<String> violations = new ArrayList<>();

    private void watchViolations() {
        page.onConsoleMessage(m -> {
            String t = m.text();
            if (t.contains("Content Security Policy") || t.contains("Refused to")) {
                violations.add(t);
            }
        });
    }

    /** Вход и основные разделы — без единого нарушения правила. */
    @Test
    void portalWorksUnderThePolicy() {
        watchViolations();
        ensurePassword(USER, USER_PASSWORD);
        page.navigate(baseUrl() + "/");
        page.fill("#loginEmail", USER);
        page.fill("#loginPassword", USER_PASSWORD);
        page.click("#loginForm button[type=submit]");
        page.waitForSelector("#appShell", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
        for (String tab : List.of("receipts", "stock", "references", "orders")) {
            page.click("nav.tabs button[data-tab='" + tab + "']");
            page.waitForSelector("#view-" + tab + ".active");
        }
        waitForQuiet();
        assertTrue(violations.isEmpty(), "портал нарушает собственное правило:\n" + String.join("\n", violations));
    }

    /** Разметка с обработчиком строкой, попавшая в страницу, ничего не выполняет. */
    @Test
    void injectedHandlerDoesNotRun() {
        page.route("**/", (Route route) -> {
            var answer = route.fetch();
            String html = answer.text().replace("</body>",
                    "<button id=\"qaBtn\" onclick=\"document.title='XSS'\">x</button>"
                            + "<img src=\"x\" onerror=\"document.title='XSS'\"></body>");
            route.fulfill(new Route.FulfillOptions().setResponse(answer).setBody(html));
        });
        watchViolations();
        page.navigate(baseUrl() + "/");
        page.click("#qaBtn");
        waitForQuiet();
        assertFalse("XSS".equals(page.title()), "встроенный обработчик выполнился — правило не действует");
        assertFalse(violations.isEmpty(), "браузер не сообщил о заблокированном скрипте");
    }
}
