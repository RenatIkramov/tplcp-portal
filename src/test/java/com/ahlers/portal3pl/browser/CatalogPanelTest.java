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
 * Товары и упаковки: панель из справочника, отбор и страницы на сервере.
 *
 * <p>ЧТО БЫЛО. Два поля ввода и два списка выбора, вписанные в разметку,
 * и отбор в браузере поверх всего справочника. Из этого росло четыре
 * вещи сразу: экран держал весь справочник клиента; значения в списках
 * «Группа» и «Тип товара» были вписаны из демо, а это свободный текст
 * клиента (V38), так что совпадение было случайным; отобрать можно было
 * только по этим четырём полям, сколько бы колонок человек себе
 * ни включил; и сортировка по заголовку упорядочивала показанные
 * двадцать строк, оставаясь на вид настоящей.
 *
 * <p>ПОЧЕМУ СМОТРИМ ЗАПРОСЫ. Экран выглядит одинаково, отбирай он сам
 * или проси сервер. Разница в том, по чему отобраны эти двадцать строк —
 * по всему справочнику или по самим себе.
 */
class CatalogPanelTest extends BrowserTestBase {

    private final List<String> askedSku = new ArrayList<>();
    private final List<String> askedPackaging = new ArrayList<>();

    private void watchRequests() {
        page.onRequest(request -> {
            String url = request.url();
            // /api/sku/options и /api/sku/{id} — не список: первый
            // отдаёт справочник для выбора, второй карточку.
            if (url.contains("/api/sku?") || url.endsWith("/api/sku")) {
                askedSku.add(url);
            } else if (url.contains("/api/packaging?") || url.endsWith("/api/packaging")) {
                askedPackaging.add(url);
            }
        });
    }

    /** Поля панели — те, что отмечены в справочнике как набор по умолчанию. */
    @Test
    void panelsAreBuiltFromTheCatalogue() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        openReferences("sku");
        page.waitForSelector("#filter-sku-code");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        for (String table : List.of("sku", "packaging")) {
            List<String> expected = jdbc.sql("""
                            SELECT column_id FROM dbo.table_column
                             WHERE table_key = :table::text AND filter_default
                             ORDER BY sort_order
                            """).param("table", table).query(String.class).list();
            assertTrue(expected.size() >= 3,
                    table + ": фильтров по умолчанию мало — " + expected.size());
            for (String columnId : expected) {
                assertFalse(page.querySelectorAll("#filter-" + table + "-" + columnId
                                + ", #filter-" + table + "-" + columnId + "-from").isEmpty(),
                        "поля фильтра " + columnId + " нет на панели " + table);
            }
        }

        // Список групп — тот, что есть у этого клиента. Читается
        // через singleValue: товары под RLS, и без контекста
        // поклажедателя база честно вернула бы ноль строк — проверка
        // сравнила бы пустое с пустым и молча прошла.
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) page.evaluate("""
                () => Array.from(document.querySelectorAll('#filter-sku-group option'))
                        .map(o => o.value).filter(Boolean)
                """);
        String real = singleValue("""
                SELECT COALESCE(string_agg(DISTINCT item_group_name, ','
                                           ORDER BY item_group_name), '')
                  FROM dbo.v_sku_full
                 WHERE item_group_name IS NOT NULL AND item_group_name <> ''
                """);
        assertFalse(real.isBlank(), "у клиента нет групп товара — проверять нечего");
        assertEquals(real, String.join(",", groups),
                "в списке групп не то, что есть у клиента");
        assertNoJsErrors();
    }

    /** Отбор, порядок и страница уходят на сервер, а не остаются в браузере. */
    @Test
    void filteringAndPagingGoToTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        watchRequests();

        signInUiAsClient();
        openReferences("sku");
        page.waitForSelector("#filter-sku-code");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertFalse(askedSku.isEmpty(), "экран не сходил за товарами");
        assertTrue(askedSku.getLast().contains("page=1"),
                "в запросе товаров нет номера страницы: " + askedSku.getLast());

        page.evaluate("""
                () => { document.getElementById('filter-sku-name').value = 'о';
                        onSkuFilterChange(); }
                """);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(askedSku.getLast().contains("filter.name="),
                "поле панели не дошло до сервера: " + askedSku.getLast());

        page.evaluate("() => onSortColumnClick('sku', 'name')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(askedSku.getLast().contains("sort=name"),
                "щелчок по заголовку не стал запросом: " + askedSku.getLast());

        page.evaluate("() => clearSkuFilters()");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertFalse(askedSku.getLast().contains("filter.name="),
                "после очистки отбор всё ещё уходит на сервер: " + askedSku.getLast());

        openReferences("packaging");
        page.waitForSelector("#filter-packaging-skuCode");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertFalse(askedPackaging.isEmpty(), "экран не сходил за упаковками");
        assertTrue(askedPackaging.getLast().contains("page=1"),
                "в запросе упаковок нет номера страницы: " + askedPackaging.getLast());
        assertNoJsErrors();
    }

    /**
     * Архив — галочка панели, и она доходит до сервера.
     *
     * <p>Архив не фильтр колонки, а свойство списка, поэтому проверяем
     * отдельно: его легко потерять при сборке панели — она собирается
     * заново при смене языка и после ⚙ Фильтров.
     */
    @Test
    void theArchiveCheckboxSurvivesAndReachesTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        watchRequests();

        signInUiAsClient();
        openReferences("sku");
        page.waitForSelector("#filter-sku-archived");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertFalse(askedSku.getLast().contains("withArchived"),
                "архив просят без спроса: " + askedSku.getLast());

        page.evaluate("""
                () => { document.getElementById('filter-sku-archived').checked = true;
                        onSkuFilterChange(); }
                """);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertTrue(askedSku.getLast().contains("withArchived=true"),
                "галочка архива не дошла до сервера: " + askedSku.getLast());

        // Смена языка пересобирает панель: подписи приходят с сервера.
        // Галочка обязана пережить сборку — иначе архив станет
        // недостижим, а вернуть позицию в работу можно только
        // открыв её карточку.
        page.evaluate("() => portalSetLanguage('ru')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertFalse(page.querySelectorAll("#filter-sku-archived").isEmpty(),
                "после смены языка галочка архива исчезла с панели");

        page.evaluate("() => clearSkuFilters()");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertEquals(false, page.evaluate(
                        "() => document.getElementById('filter-sku-archived').checked"),
                "«Очистить» оставила галочку архива отмеченной");
        assertNoJsErrors();
    }

    /** Счётчик и ряд страниц показывают число, пришедшее с сервера. */
    @Test
    void theRowCounterComesFromTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        openReferences("sku");
        page.waitForSelector("#skuBody tr");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String total = singleValue(
                "SELECT count(*)::text FROM dbo.v_sku_full WHERE COALESCE(is_active, true)");
        assertTrue(page.innerText("#skuCount").contains(total),
                "счётчик товаров показывает не то, что сервер (" + total + "): "
                        + page.innerText("#skuCount"));
        assertTrue(page.innerText("#skuPagination").contains(total),
                "в ряду страниц товаров нет общего числа строк: "
                        + page.innerText("#skuPagination"));
        assertNoJsErrors();
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
