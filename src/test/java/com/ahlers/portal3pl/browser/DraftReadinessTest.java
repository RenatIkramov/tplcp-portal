package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пустой документ не отправляется, и человеку сказано почему — словами.
 *
 * <p>Сначала портал держал над черновиком постоянный баннер с причинами.
 * Он ругался на незаполненные поля, к которым человек ещё не дошёл,
 * и называл их именами колонок базы: «не заполнены поля: to_warehouse_id,
 * ship_date». Теперь причины приходят тогда, когда человек сам нажал
 * «Подтвердить», и поля названы так, как они подписаны у него на экране.
 *
 * <p>Поэтому проверка смотрит две вещи: отказ появился и в нём нет
 * ни одного имени колонки базы.
 */
class DraftReadinessTest extends BrowserTestBase {

    private boolean draftCreated;

    @Test
    void emptyDraftRefusesWithReadableReasons() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        // Карточка нового документа открывается пустой, в базе его ещё
        // нет: запись появляется по кнопке. Поэтому ждём саму карточку,
        // а не идентификатор — его выдаст сохранение, которое сделает
        // за нас «Подтвердить».
        page.evaluate("() => createNewReceiptDraft()");
        page.waitForFunction("() => !!currentReceiptRef");
        draftCreated = true;

        page.evaluate("() => confirmReceiptDraft()");
        page.waitForSelector("#receiptDetailBanner .hint");

        String refusal = (String) page.evaluate(
                "() => document.getElementById('receiptDetailBanner').innerText");
        assertTrue(refusal.contains(text("error.ready.no_lines")),
                "в отказе нет причины «нет ни одной строки»: " + refusal);
        assertTrue(refusal.contains(label("receipts", "warehouse")),
                "в отказе нет подписи незаполненного поля «склад»: " + refusal);
        assertFalse(refusal.contains("_id") || refusal.contains("_date"),
                "в отказе показаны имена колонок базы вместо подписей: " + refusal);
        assertNoJsErrors();
    }

    /** Черновик за собой убираем: проверка создаёт настоящий документ. */
    @AfterEach
    void removeDraft() {
        if (draftCreated && page != null) {
            try {
                page.evaluate("() => deleteReceiptDraft()");
                page.waitForFunction("() => !currentReceiptRef");
            } catch (RuntimeException e) {
                System.out.println("не удалось удалить черновик проверки: " + e.getMessage());
            }
        }
    }

    /** Подпись колонки так, как её видит этот пользователь. */
    private String label(String table, String columnId) {
        return jdbc.sql("SELECT label FROM dbo.table_columns(:user::uuid, 'ru'::text, :table::text)"
                        + " WHERE column_id = :column::text")
                .param("user", userId(USER))
                .param("table", table)
                .param("column", columnId)
                .query(String.class)
                .single();
    }

    private String text(String key) {
        return jdbc.sql("SELECT value FROM dbo.ui_texts('ru') WHERE text_key = :key")
                .param("key", key)
                .query(String.class)
                .single();
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
