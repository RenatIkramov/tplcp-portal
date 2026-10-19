package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Черновик перемещения: создаётся, объясняет себя и не даёт себя отправить.
 *
 * <p>Перемещения сервер умел с самого начала, а экран рисовал их демо-данными
 * из памяти. Проверка идёт по настоящему пути: кнопка «Создать новое» →
 * документ в базе → карточка → причины, по которым отправить нельзя.
 *
 * <p>Отдельно проверяется правило «склад-отправитель ≠ склад-получатель».
 * Через экран одинаковые склады не выбрать — список получателя не предлагает
 * того, кто уже выбран отправителем, — поэтому проверка выставляет их
 * запросом к API. Это и есть смысл правила: запрет держит сервер, а не
 * удобство выпадающего списка.
 */
class TransferDraftTest extends BrowserTestBase {

    private boolean draftCreated;

    @Test
    void emptyTransferDraftRefusesWithReadableReasons() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        createDraft();

        page.evaluate("() => confirmTransferDraft()");
        page.waitForSelector("#transferDetailBanner .hint");

        String refusal = bannerText();
        assertTrue(refusal.contains(text("error.ready.no_lines")),
                "в отказе нет причины «нет ни одной строки»: " + refusal);
        assertFalse(refusal.contains("_id") || refusal.contains("_date"),
                "в отказе показаны имена колонок базы вместо подписей: " + refusal);
        assertNoJsErrors();
    }

    @Test
    void sameWarehouseIsListedAsReason() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        createDraft();

        Object docId = page.evaluate("() => (currentTransferRef || {}).docId");
        assertNotNull(docId, "черновик перемещения не открылся");

        // Один и тот же склад в обоих полях — то, что экран не предлагает,
        // но чего никто не мешает добиться запросом.
        page.evaluate("""
                async (docId) => {
                  const warehouses = await (await fetch('/api/warehouses',
                      {headers: {Accept: 'application/json'}})).json();
                  const id = warehouses[0].warehouseId;
                  await fetch('/api/transfers/' + docId, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json', Accept: 'application/json'},
                    body: JSON.stringify({from_warehouse_id: id, to_warehouse_id: id})
                  });
                }
                """, docId);
        page.evaluate("id => portalOpenDocumentById('transfers', id)", docId);
        page.evaluate("() => confirmTransferDraft()");

        String expected = text("error.ready.same_warehouse");
        page.waitForFunction("reason => { const b = document.getElementById('transferDetailBanner');"
                + " return !!b && b.innerText.indexOf(reason) >= 0; }", expected);
        assertTrue(bannerText().contains(expected),
                "совпадение складов не названо причиной отказа: " + bannerText());
        assertNoJsErrors();
    }

    /**
     * Создаёт черновик и сохраняет его.
     *
     * <p>Сохранение здесь обязательно: карточка нового документа живёт
     * на экране, а в базу он попадает по кнопке «Сохранить» — и только
     * тогда у него появляется идентификатор, по которому проверка
     * обращается к API.
     */
    private void createDraft() {
        page.evaluate("() => createNewTransferDraft()");
        page.waitForFunction("() => !!currentTransferRef");
        page.evaluate("() => saveTransferDraft()");
        page.waitForFunction("() => !!(currentTransferRef && currentTransferRef.docId)");
        draftCreated = true;
    }

    /**
     * Черновик за собой убираем: проверка создаёт настоящий документ,
     * и через десяток прогонов список перемещений зарос бы пустыми.
     */
    @AfterEach
    void removeDraft() {
        if (draftCreated && page != null) {
            try {
                page.evaluate("() => deleteTransferDraft()");
                page.waitForFunction("() => !currentTransferRef");
            } catch (RuntimeException e) {
                System.out.println("не удалось удалить черновик перемещения: " + e.getMessage());
            }
        }
    }

    private String bannerText() {
        return (String) page.evaluate(
                "() => document.getElementById('transferDetailBanner').innerText");
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
