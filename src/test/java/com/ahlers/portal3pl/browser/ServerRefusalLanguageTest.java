package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.microsoft.playwright.Dialog;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отказ сервера доходит до человека на его языке.
 *
 * <p>С V57 сервер отдаёт отказ ключом и сам разворачивает его в фразу
 * на языке того, кто сделал запрос. Проверка смотрит последний участок
 * этого пути — тот, где фраза показывается: удаляем товар, который
 * где-то используется, и читаем, что портал сказал человеку.
 *
 * <p>Товар выбирается тот, который удалить ЗАВЕДОМО нельзя: иначе
 * подтверждение в окне браузера удалило бы живую запись. Если такого
 * товара в базе нет, проверка помечается пропущенной.
 */
class ServerRefusalLanguageTest extends BrowserTestBase {

    @Test
    void refusalIsShownInUserLanguage() {
        PortalUser user = signedInUser();
        localeOf(user, "en");

        signInUiAsClient();
        openSkuList();
        String code = usedSkuCode();

        // Окно подтверждения принимаем: отказ придёт от сервера, и это
        // ровно то, что проверяется. Товар при этом не пострадает —
        // он используется, и портал удалить его не даст.
        page.onDialog(Dialog::accept);
        // Сообщения портала перехватываем в месте показа: всплывающая
        // плашка живёт две секунды и своего признака в разметке не имеет.
        page.evaluate("() => { window.__said = []; const show = window.notify;"
                + " window.notify = m => { window.__said.push(String(m)); show(m); }; }");

        page.evaluate("c => portalOpenSku(c)", code);
        page.click("[onclick^='portalSkuDelete']");
        page.waitForFunction("() => (window.__said || []).length > 0");

        @SuppressWarnings("unchecked")
        List<String> said = (List<String>) page.evaluate("() => window.__said");
        String expected = englishRefusal();
        assertTrue(said.stream().anyMatch(m -> m.startsWith(expected)),
                "портал объяснил отказ не по-английски: " + said + ", ожидалось начало: " + expected);
        assertNoJsErrors();
    }

    /** Начало английского текста отказа — до подстановки «где используется». */
    private String englishRefusal() {
        String template = jdbc
                .sql("SELECT value FROM dbo.ui_texts('en') WHERE text_key = 'error.sku.in_use'")
                .query(String.class)
                .single();
        return template.substring(0, template.indexOf("{where}"));
    }

    /**
     * Код товара из списка на экране, который где-то используется.
     *
     * <p>Берём именно из списка: карточку открывает поиск по загруженным
     * строкам, и товар с другой страницы списка не открылся бы.
     */
    private String usedSkuCode() {
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) page.evaluate(
                "() => (PORTAL.catalog.sku || []).map(r => r.code)");
        assertFalse(codes.isEmpty(), "список товаров пуст");

        for (String code : codes) {
            if (usageCount(code) > 0) {
                return code;
            }
        }
        Assumptions.abort("ни один товар из списка нигде не используется —"
                + " удалять такой нельзя, проверять отказ не на чем");
        return null;
    }

    private long usageCount(String code) {
        Long total = new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(USER, DEPOSITOR))
                    .query(String.class)
                    .single();
            return jdbc.sql("""
                            SELECT u.receipts + u.orders + u.transfers
                                 + u.stock_rows + u.packagings
                              FROM dbo.sku s, LATERAL dbo.sku_usage(s.sku_id) u
                             WHERE s.code = :code::text
                            """)
                    .param("code", code)
                    .query(Long.class)
                    .optional()
                    .orElse(0L);
        });
        return total == null ? 0 : total;
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
