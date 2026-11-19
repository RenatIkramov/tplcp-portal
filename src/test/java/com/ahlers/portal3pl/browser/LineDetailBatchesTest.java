package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Экран «Детализация» строки показывает партии строки (10.11.2026).
 *
 * <p>Партии сервер отдаёт вместе с карточкой документа (lineDetails),
 * но раскладывал их по строкам card.js — путём, которым карточку давно
 * не открывали. Экран был пуст у любого документа, и ни одна проверка
 * этого не замечала: проверяли карточку, а не её второй экран.
 */
class LineDetailBatchesTest extends BrowserTestBase {

    @Test
    void receiptLineShowsItsBatches() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();

        // Поступление клиента с партиями — ищем через API под его же входом
        // (из теста базу видно без клиента, а строки защищены RLS). Такого
        // нет — проверять нечего.
        @SuppressWarnings("unchecked")
        Map<String, Object> found = (Map<String, Object>) page.evaluate("""
                async () => {
                  const list = await PORTAL.fetchJson('/api/receipts?pageSize=200');
                  for (const row of (list.rows || [])) {
                    const card = await PORTAL.fetchJson('/api/receipts/' + row.docId);
                    const d = (card.lineDetails || []).find(x => x.batchNumber);
                    if (d) return {docId: row.docId, lineNumber: d.lineNumber, batch: d.batchNumber};
                  }
                  return null;
                }
                """);
        assumeFalse(found == null, "у клиента нет поступления с партиями");
        String docId = (String) found.get("docId");
        int lineNumber = ((Number) found.get("lineNumber")).intValue();
        String batch = (String) found.get("batch");

        page.evaluate("id => PORTAL.draft.open('receipts', id)", docId);
        page.waitForFunction("id => PORTAL.card.doc('receipts') && PORTAL.card.doc('receipts').docId === id",
                docId);

        page.evaluate("n => PORTAL.card.openLine('receipts',"
                + " PORTAL.card.lines('receipts').findIndex(l => l.lineNumber === n))", lineNumber);
        page.waitForSelector("#view-receipt-line-detail.active");
        String rows = page.innerText("#receiptLineDetailRows");
        assertTrue(rows.contains(batch), "на экране «Детализация» нет партии " + batch + ": " + rows);
        assertNoJsErrors();
    }
}
