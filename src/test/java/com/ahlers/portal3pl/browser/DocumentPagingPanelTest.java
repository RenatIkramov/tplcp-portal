package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Страницы и сортировка списка уходят на сервер, а не остаются в браузере.
 *
 * <p>ПОЧЕМУ СМОТРИМ ЗАПРОСЫ, А НЕ ЭКРАН. Экран выглядит одинаково
 * в обоих случаях: строки упорядочены, ряд страниц нарисован, числа
 * на месте. Разница в том, по чему именно упорядочены двадцать строк —
 * по всему списку или по самим себе, — и увидеть её на картинке
 * нельзя. Поэтому проверка считает запросы к серверу и читает их
 * параметры: щелчок по заголовку обязан стать запросом.
 *
 * <p>Заодно проверяется, что диапазон дат по умолчанию убран. Он
 * стоял не ради человека, а ради размера списка: сервер отдавал весь
 * отбор целиком. Страницы считает сервер — и документы старше
 * полутора месяцев больше не прячутся молча.
 */
class DocumentPagingPanelTest extends BrowserTestBase {

    /** Адреса запросов к списку поступлений — в порядке отправки. */
    private final List<String> asked = new ArrayList<>();

    private void watchRequests() {
        page.onRequest(request -> {
            String url = request.url();
            if (url.contains("/api/receipts")) {
                asked.add(url);
            }
        });
    }

    @Test
    void theListIsAskedPageByPage() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        watchRequests();

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertFalse(asked.isEmpty(), "экран не сходил за списком поступлений");
        String first = asked.getLast();
        assertTrue(first.contains("page=1"), "в запросе списка нет номера страницы: " + first);
        assertTrue(first.contains("pageSize="), "в запросе списка нет размера страницы: " + first);

        // Ряд страниц показывает «всего» из ответа сервера, а не число
        // строк на экране. Сверяется с базой: по-другому эти числа
        // разойтись и не могут — либо сервер посчитал, либо экран.
        String total = singleValue("SELECT count(*)::text FROM dbo.v_receipts");
        String bar = page.innerText("#receiptsPagination");
        assertTrue(bar.contains(total),
                "в ряду страниц нет общего числа документов (" + total + "): " + bar);
        assertNoJsErrors();
    }

    @Test
    void sortingAndPageSizeGoToTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        watchRequests();

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        int before = asked.size();
        page.evaluate("() => onSortColumnClick('receipts', 'lineCount')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(asked.size() > before,
                "щелчок по заголовку не стал запросом — список отсортирован в браузере");
        assertTrue(asked.getLast().contains("sort=lineCount"),
                "в запросе нет колонки сортировки: " + asked.getLast());
        assertTrue(asked.getLast().contains("dir=asc"),
                "первый щелчок сортирует по возрастанию: " + asked.getLast());

        // Второй щелчок по той же колонке переворачивает порядок.
        page.evaluate("() => onSortColumnClick('receipts', 'lineCount')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(asked.getLast().contains("dir=desc"),
                "второй щелчок не перевернул порядок: " + asked.getLast());

        page.evaluate("() => changePageSize('receipts', 10)");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(asked.getLast().contains("pageSize=10"),
                "смена размера страницы не дошла до сервера: " + asked.getLast());
        assertTrue(asked.getLast().contains("page=1"),
                "после смены размера страницы человек остался не на первой: " + asked.getLast());
        assertNoJsErrors();
    }

    /**
     * Поля дат на панели пусты при открытии.
     *
     * <p>Пока диапазон проставлялся сам, список молча начинался
     * с прошлого месяца: документ постарше человек не находил и
     * причины не видел — поля-то заполнены, и выглядит это как его
     * собственный выбор.
     */
    @Test
    void dateFiltersStartEmpty() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        @SuppressWarnings("unchecked")
        List<String> filled = (List<String>) page.evaluate("""
                () => Array.from(document.querySelectorAll('input[type=date]'))
                        .filter(i => i.offsetParent !== null && i.value)
                        .map(i => i.id + '=' + i.value)
                """);
        assertEquals(List.of(), filled,
                "поле даты заполнено само — часть документов спрятана без ведома человека");
        assertNoJsErrors();
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
