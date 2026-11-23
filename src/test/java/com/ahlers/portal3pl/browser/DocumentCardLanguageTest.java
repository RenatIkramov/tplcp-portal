package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Карточка документа на английском — без русских подписей (V93).
 *
 * <p>Подписи полей карточки написаны в коде по-русски и переводятся
 * обратным словарём. V56 удалила 77 нужных ему ключей, и на английском
 * часть подписей осталась русской. Здесь — то, что видит человек:
 * открываем каждый вид документа и ищем на экране русские подписи.
 */
class DocumentCardLanguageTest extends BrowserTestBase {

    private static final List<String> LABELS = List.of(
            "Номер документа (в ERP клиента)", "Номер документа WMS", "Дата создания",
            "Количество строк", "Ожидаемая дата поставки", "Номер Инвойса",
            "Контактные данные грузополучателя", "Фотографии", "Услуги");

    @Test
    void documentCardsSpeakEnglish() {
        localeOf(authService.authenticate(USER, USER_PASSWORD), "en");
        signInUiAsClient();
        for (String type : List.of("receipts", "orders", "transfers")) {
            page.evaluate("t => showTab(t)", type);
            page.waitForFunction("t => (PORTAL.page && PORTAL.page[t] && PORTAL.page[t].length > 0)"
                    + " || document.querySelector('#' + t + 'Body tr[data-call]')", type);
            page.evaluate("t => { const row = document.querySelector('#' + t + 'Body tr[data-call]');"
                    + " if (row) row.click(); }", type);
            waitForQuiet();
            String text = visibleText();
            List<String> russian = LABELS.stream().filter(text::contains).toList();
            assertTrue(russian.isEmpty(), type + ": на английском экране русские подписи " + russian);
        }
        assertNoJsErrors();
    }
}
