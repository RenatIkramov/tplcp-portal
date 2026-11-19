package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Экран черновика отправляет версию, которую видел (аудит 11.11.2026, A9).
 *
 * <p>Пока черновик открыт, его изменили в другой вкладке. «Сохранить»
 * на первой не затирает чужую правку, а говорит человеку, что документ
 * уже изменён. Проверка — через экран: версию в запрос кладёт draft.js,
 * сервер без неё не сверяет.
 */
class DraftConflictScreenTest extends BrowserTestBase {

    private String docId;

    @Test
    void saveOverOthersChangeIsRefused() {
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('receipts')");
        page.waitForFunction("() => !!PORTAL.card.doc('receipts')");
        fillErp("receipts");
        page.evaluate("() => PORTAL.draft.save('receipts')");
        page.waitForFunction("() => !!(PORTAL.card.doc('receipts') && PORTAL.card.doc('receipts').docId)");
        docId = (String) page.evaluate("() => PORTAL.card.doc('receipts').docId");

        // другая вкладка: правка тем же человеком, но экран этой вкладки её не видел
        page.evaluate("""
                id => fetch('/api/receipts/' + id, {method: 'PUT',
                    headers: {'Content-Type': 'application/json', Accept: 'application/json'},
                    body: JSON.stringify({comment: 'из другой вкладки'})})
                """, docId);
        page.evaluate("() => { window.__said = []; const n = window.notify;"
                + " window.notify = m => { window.__said.push(m); n(m); }; }");
        page.evaluate("() => PORTAL.draft.save('receipts')");
        page.waitForFunction("() => window.__said.length > 0");

        @SuppressWarnings("unchecked")
        List<String> said = (List<String>) page.evaluate("() => window.__said");
        assertTrue(said.getFirst().contains("другой пользователь") || said.getFirst().contains("Another user"),
                "человеку не сказали, что документ изменён: " + said);
        assertEquals("из другой вкладки", singleValue(
                "SELECT comment FROM dbo.receipts WHERE doc_id = '" + docId + "'"), "чужая правка затёрта");
        assertNoJsErrors();
    }

    @AfterEach
    void cleanUp() {
        if (docId != null) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'");
        }
    }
}
