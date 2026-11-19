package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вкладка «История» карточки документа (журнал действий, V116).
 *
 * <p>Путь настоящий: черновик сохраняется кнопкой, комментарий правится
 * в карточке и сохраняется ещё раз — и история показывает, кто это
 * сделал, «Документ создан» и изменённое поле со старым и новым
 * значением. Сохранение состава (удалить всё — вставить заново) не
 * должно выглядеть как удаление и добавление строк.
 */
class DocumentHistoryTest extends BrowserTestBase {

    private boolean draftCreated;

    @Test
    void historyShowsWhoChangedWhat() {
        localeOf(signedInUser(), "ru");
        signInUiAsClient();

        page.evaluate("() => PORTAL.draft.create('transfers')");
        page.waitForFunction("() => !!PORTAL.card.doc('transfers')");
        // у несохранённого черновика истории нет
        assertFalse(page.isVisible("#transferDetailTabButtons [data-tab='history']"),
                "вкладка «История» у черновика, которого ещё нет в базе");
        fillErp("transfers");
        page.evaluate("() => PORTAL.draft.save('transfers')");
        page.waitForFunction("() => !!(PORTAL.card.doc('transfers') && PORTAL.card.doc('transfers').docId)");
        draftCreated = true;

        String comment = "история " + System.nanoTime();
        page.fill("#tf_comment", comment);
        page.evaluate("() => PORTAL.draft.save('transfers')");
        page.waitForFunction("c => (PORTAL.card.doc('transfers') || {}).comment === c", comment);

        page.click("#transferDetailTabButtons [data-tab='history']");
        page.waitForSelector("#transferDetailTabContent .history-table");
        String shown = page.innerText("#transferDetailTabContent");

        String fullName = jdbc.sql("SELECT full_name FROM dbo.app_user WHERE login = :l")
                .param("l", USER).query(String.class).single();
        assertTrue(shown.contains(fullName), "в истории нет того, кто менял: " + shown);
        assertTrue(shown.contains(text("audit.action.doc_created")), "нет «Документ создан»: " + shown);
        assertTrue(shown.contains(text("audit.field.comment")) && shown.contains(comment),
                "нет изменения комментария: " + shown);
        assertFalse(shown.contains(text("audit.action.line_deleted").replace("{n}", "1")),
                "сохранение состава показано как удаление строки: " + shown);
        assertNoJsErrors();
    }

    private com.ahlers.portal3pl.auth.PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }

    private String text(String key) {
        return jdbc.sql("SELECT value FROM dbo.ui_texts('ru') WHERE text_key = :key")
                .param("key", key)
                .query(String.class)
                .single();
    }

    /** Черновик за собой убираем — как в TransferDraftTest. */
    @AfterEach
    void removeDraft() {
        if (draftCreated && page != null) {
            try {
                page.evaluate("() => PORTAL.draft.remove('transfers')");
                page.waitForFunction("() => !PORTAL.card.doc('transfers')");
            } catch (RuntimeException e) {
                System.out.println("не удалось удалить черновик перемещения: " + e.getMessage());
            }
        }
    }
}
