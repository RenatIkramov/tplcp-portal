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
 * Панель запасов строится из справочника, а отбор уходит на сервер.
 *
 * <p>ЧТО БЫЛО. Четыре поля, вписанные в разметку, и отбор в браузере.
 * Из этого росли три вещи, каждая незаметная по отдельности:
 * в списке групп стояли четыре значения из демо, а группа товара
 * с V38 — свободный текст клиента, так что совпадение было случайным;
 * надпись «Только брак» была вписана по-русски и на английском языке
 * такой и оставалась; и, главное, приделать к такому отбору страницы
 * было нельзя — сервер отрезал бы двадцать строк ДО того, как экран
 * применит свои четыре поля.
 *
 * <p>ПОЧЕМУ СМОТРИМ ЗАПРОСЫ. Экран выглядит одинаково, отбирай он
 * сам или проси сервер. Разница в том, по чему именно отобраны
 * двадцать строк — по всему списку или по самим себе.
 */
class StockPanelTest extends BrowserTestBase {

    private final List<String> asked = new ArrayList<>();

    private void watchRequests() {
        page.onRequest(request -> {
            String url = request.url();
            if (url.contains("/api/stock") && !url.contains("/details")) {
                asked.add(url);
            }
        });
    }

    @Test
    void panelIsBuiltFromTheCatalogue() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        watchRequests();

        signInUiAsClient();
        page.evaluate("() => showTab('stock')");
        page.waitForSelector("#filter-stock-code");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Поля панели — те, что отмечены в справочнике как набор
        // по умолчанию. Список берётся из базы, а не вписан сюда:
        // миграция поменяет набор — проверка узнает об этом сама.
        List<String> expected = jdbc.sql("""
                        SELECT column_id FROM dbo.table_column
                         WHERE table_key = 'stock' AND filter_default
                         ORDER BY sort_order
                        """).query(String.class).list();
        assertTrue(expected.size() >= 3, "фильтров по умолчанию у запасов мало");
        for (String columnId : expected) {
            assertFalse(page.querySelectorAll("#filter-stock-" + columnId
                            + ", #filter-stock-" + columnId + "-from").isEmpty(),
                    "поля фильтра " + columnId + " нет на панели запасов");
        }

        // «Только брак» — флажок, а не поле ввода: вопрос «есть ли»
        // числом не задают.
        assertEquals("checkbox", page.getAttribute("#filter-stock-defect", "type"),
                "«Брак» на панели не флажок");

        // Список групп — тот, что лежит у клиента на складе.
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) page.evaluate("""
                () => Array.from(document.querySelectorAll('#filter-stock-group option'))
                        .map(o => o.value).filter(Boolean)
                """);
        // Читается через singleValue: запасы под RLS, и без контекста
        // поклажедателя база честно вернула бы ноль строк — а проверка
        // сравнила бы пустое с пустым и молча прошла.
        String real = singleValue("""
                SELECT COALESCE(string_agg(DISTINCT item_group_name, ','
                                           ORDER BY item_group_name), '')
                  FROM dbo.v_stock_lines
                 WHERE item_group_name IS NOT NULL AND item_group_name <> ''
                """);
        assertFalse(real.isBlank(), "у клиента нет групп товара — проверять нечего");
        assertEquals(real, String.join(",", groups),
                "в списке групп не то, что лежит у клиента на складе");
        assertNoJsErrors();
    }

    @Test
    void filteringAndPagingGoToTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        watchRequests();

        signInUiAsClient();
        page.evaluate("() => showTab('stock')");
        page.waitForSelector("#filter-stock-defect");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertFalse(asked.isEmpty(), "экран не сходил за запасами");
        assertTrue(asked.getLast().contains("page=1"),
                "в запросе запасов нет номера страницы: " + asked.getLast());

        page.evaluate("""
                () => { document.getElementById('filter-stock-defect').checked = true;
                        onStockFilterChange(); }
                """);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(asked.getLast().contains("filter.defect=true"),
                "флажок не дошёл до сервера: " + asked.getLast());

        page.evaluate("() => onSortColumnClick('stock', 'code')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(asked.getLast().contains("sort=code"),
                "щелчок по заголовку не стал запросом: " + asked.getLast());

        // «Очистить» снимает и отметку флажка. Пустое значение её
        // не снимает: у checkbox value — это то, что уйдёт при
        // отметке, и список оставался бы коротким без видимой причины.
        page.evaluate("() => clearStockFilters()");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertEquals(false, page.evaluate(
                        "() => document.getElementById('filter-stock-defect').checked"),
                "«Очистить» оставила флажок отмеченным");
        assertFalse(asked.getLast().contains("filter.defect"),
                "после очистки флажок всё ещё уходит на сервер: " + asked.getLast());
        assertNoJsErrors();
    }

    /** Ряд страниц показывает число, пришедшее с сервера. */
    @Test
    void theRowCounterComesFromTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('stock')");
        page.waitForSelector("#stockBody tr");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String total = singleValue("SELECT count(*)::text FROM dbo.v_stock_lines");
        assertTrue(page.innerText("#stockPagination").contains(total),
                "в ряду страниц нет общего числа строк (" + total + "): "
                        + page.innerText("#stockPagination"));
        assertTrue(page.innerText("#stockCount").contains(total),
                "счётчик над таблицей показывает не то, что сервер");
        assertNoJsErrors();
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
