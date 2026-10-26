package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.lists.ListPaging;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.documents.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Страницы и порядок списка документов.
 *
 * <p>ПОЧЕМУ ЭТО ВАЖНО ПРОВЕРЯТЬ. Разбивка на страницы ломается тихо.
 * Ни одна из поломок ниже не выглядит как поломка: список открывается,
 * строки на месте, число внизу есть.
 *
 * <ul>
 *   <li><b>Неустойчивый порядок.</b> Две строки с одинаковой датой
 *       идут как придётся, и порядок волен поменяться между запросами:
 *       один документ человек видит на обеих страницах, другого —
 *       ни на одной. Заметить это можно, только пройдя все страницы
 *       и сложив строки, что и делает проверка ниже.</li>
 *   <li><b>Сортировка в пределах страницы.</b> Отсортировать двадцать
 *       строк из ста тридцати — значит показать «самый тяжёлый
 *       документ» из видимых. Человек увидит порядок и поверит ему.</li>
 *   <li><b>«Всего» по странице.</b> Посчитать итог по присланным
 *       строкам — получить «всего 20» при ста тридцати.</li>
 * </ul>
 *
 * <p>Здесь же вторая граница доверия, рядом с фильтрами: имя колонки
 * сортировки попадает в текст SQL. В запрос идёт не то, что прислали,
 * а то, что нашлось в справочнике (V78).
 */
class DocumentPagingTest extends PortalTestBase {

    @Autowired
    DocumentService documents;

    @Autowired
    ColumnService columns;

    /** На странице ровно столько строк, сколько просили, а «всего» — про весь отбор. */
    @Test
    void pageIsCutButTheTotalIsNot() {
        PortalUser user = signedInUser();
        int all = listTotal(documents.list(user, DocumentType.ORDERS, Map.of()));
        assertTrue(all >= 3, "в демо-данных мало заказов, страницы не на чем проверить: " + all);

        Map<String, Object> first = documents.list(user, DocumentType.ORDERS,
                Map.of("page", "1", "pageSize", "2"));
        assertEquals(2, listRows(first).size(), "страница отдала не два документа");
        assertEquals(all, listTotal(first), "«всего» посчитано по странице, а не по отбору");
        assertEquals(1, first.get("page"));
        assertEquals(2, first.get("pageSize"));
    }

    /**
     * Страницы в сумме дают весь список: ни повторов, ни пропаж.
     *
     * <p>Проверка идёт по одной строке на странице нарочно: при
     * размере страницы в одну строку любая неустойчивость порядка
     * вылезает сразу, а при двадцати её можно не встретить годами.
     */
    @Test
    void pagesNeitherRepeatNorLoseRows() {
        PortalUser user = signedInUser();
        int all = listTotal(documents.list(user, DocumentType.ORDERS, Map.of()));

        Set<String> seen = new LinkedHashSet<>();
        List<String> order = new ArrayList<>();
        for (int page = 1; page <= all; page++) {
            List<Map<String, Object>> rows = listRows(documents.list(user, DocumentType.ORDERS,
                    Map.of("page", String.valueOf(page), "pageSize", "1")));
            assertEquals(1, rows.size(), "страница " + page + " отдала не одну строку");
            String id = String.valueOf(rows.getFirst().get("docId"));
            order.add(id);
            seen.add(id);
        }
        assertEquals(all, seen.size(),
                "документы повторились или потерялись при обходе страниц: " + order);
    }

    /**
     * Сортировка идёт по всему списку, а не по странице.
     *
     * <p>Проверяется тем, что первая страница при сортировке
     * по возрастанию начинается с наименьшего значения ВСЕГО
     * списка. Сортировка в пределах страницы этого не даст.
     */
    @Test
    void sortingCoversTheWholeListNotJustThePage() {
        PortalUser user = signedInUser();
        // Сортируется число строк: оно заполнено у каждого документа.
        // Вес в демо-данных почти везде пуст, и проверка свелась бы
        // к сравнению одного значения с самим собой.
        List<Double> everything = listRows(documents.list(user, DocumentType.ORDERS,
                        Map.of("pageSize", "200")))
                .stream().map(r -> number(r.get("lineCount"))).sorted().toList();
        assertTrue(everything.size() >= 3, "мало заказов для проверки сортировки");
        assertTrue(everything.getFirst() < everything.getLast(),
                "у всех заказов одинаковое число строк — сортировать нечего");

        Map<String, Object> ascending = documents.list(user, DocumentType.ORDERS,
                Map.of("sort", "lineCount", "dir", "asc", "pageSize", "1"));
        assertEquals(everything.getFirst(), number(listRows(ascending).getFirst().get("lineCount")),
                "по возрастанию первой идёт не наименьшая строка списка");

        Map<String, Object> descending = documents.list(user, DocumentType.ORDERS,
                Map.of("sort", "lineCount", "dir", "desc", "pageSize", "1"));
        assertEquals(everything.getLast(), number(listRows(descending).getFirst().get("lineCount")),
                "по убыванию первой идёт не наибольшая строка списка");
    }

    /** Без сортировки — свежие сверху: с этого человек начинает список. */
    @Test
    void defaultOrderIsNewestFirst() {
        PortalUser user = signedInUser();
        List<String> dates = listRows(documents.list(user, DocumentType.RECEIPTS,
                        Map.of("pageSize", "200")))
                .stream().map(r -> String.valueOf(r.get("docDate"))).toList();
        List<String> sorted = new ArrayList<>(dates);
        sorted.sort((a, b) -> b.compareTo(a));
        assertEquals(sorted, dates, "список открылся не свежими документами сверху");
    }

    /**
     * Сортировать можно и по колонке, у которой нет фильтра.
     *
     * <p>Это и была причина завести sort_column отдельно от
     * filter_column: у «Объёма», «Мест», «Штук» и всего транспортного
     * раздела фильтра нет, а сортировка нужна. Заголовок, который
     * нажимается и ничего не делает, хуже неподвижного.
     */
    @Test
    void columnsWithoutAFilterAreStillSortable() {
        PortalUser user = signedInUser();
        for (String columnId : List.of("volume", "cargoPlaces", "driverPhone")) {
            Map<String, Object> answer = documents.list(user, DocumentType.RECEIPTS,
                    Map.of("sort", columnId, "dir", "asc"));
            assertFalse(listRows(answer).isEmpty(),
                    "сортировка по колонке " + columnId + " вернула пустой список");
        }
    }

    /** Каждая колонка документов умеет сортироваться — справочник заполнен весь. */
    @Test
    void everyColumnOfTheCatalogueCanSort() {
        PortalUser user = signedInUser();
        Set<String> broken = new TreeSet<>();
        for (DocumentType type : DocumentType.values()) {
            for (String columnId : columnIds(type)) {
                try {
                    documents.list(user, type, Map.of("sort", columnId, "pageSize", "1"));
                } catch (RuntimeException e) {
                    broken.add(type.path() + "." + columnId + ": " + e.getMessage());
                }
            }
        }
        assertTrue(broken.isEmpty(), "по этим колонкам список сортировать нельзя: " + broken);
    }

    /** Незнакомая колонка — отказ, а не молчаливый порядок по умолчанию. */
    @Test
    void unknownSortColumnIsRefused() {
        PortalUser user = signedInUser();
        expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                Map.of("sort", "depositorId")));
        expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                Map.of("sort", "doc_date; DROP TABLE receipts")));
    }

    /**
     * По колонке, скрытой от поклажедателя, сортировать нельзя.
     *
     * <p>Та же причина, что у фильтров: порядок строк — это сведения
     * о значениях. Разреши сортировку по скрытому полю — и его
     * значения восстанавливаются по порядку, не видя самой колонки.
     */
    @Test
    void columnForbiddenToDepositorIsNotSortable() {
        PortalUser user = signedInUser();
        setAvailability("receipts", "supplier", false);
        try {
            expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                    Map.of("sort", "supplier")));
        } finally {
            setAvailability("receipts", "supplier", true);
        }
    }

    /** Размер страницы ограничен сверху: иначе адрес снова выгружает всё. */
    @Test
    void pageSizeIsCapped() {
        PortalUser user = signedInUser();
        Map<String, Object> answer = documents.list(user, DocumentType.RECEIPTS,
                Map.of("pageSize", "100000"));
        assertEquals(ListPaging.MAX_SIZE, answer.get("pageSize"),
                "размер страницы не зажат потолком");
    }

    /** Не число в адресе — отказ, а не страница наугад. */
    @Test
    void nonNumericPageIsRefused() {
        PortalUser user = signedInUser();
        expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                Map.of("page", "вторая")));
    }

    /**
     * Страницы под ногами не стало — возвращается первая.
     *
     * <p>Человек стоял на пятой, поставил фильтр, строк осталось
     * на одну. Пустой экран с надписью «ничего не найдено» соврал бы:
     * найдено, просто не здесь. Номер страницы в ответе — не любезность,
     * а то, чем экран чинит свой ряд страниц.
     */
    @Test
    void aPageBeyondTheEndFallsBackToTheFirst() {
        PortalUser user = signedInUser();
        Map<String, Object> answer = documents.list(user, DocumentType.RECEIPTS,
                Map.of("page", "99", "pageSize", "20"));
        assertEquals(1, answer.get("page"), "ответ не вернул человека на первую страницу");
        assertFalse(listRows(answer).isEmpty(), "первая страница пуста, хотя документы есть");
    }

    /** Отбор и страница работают вместе, а не вместо друг друга. */
    @Test
    void filterAndPageWorkTogether() {
        PortalUser user = signedInUser();
        Map<String, Object> drafts = documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.status", "received", "pageSize", "1"));
        assertEquals(1, listRows(drafts).size(), "страница отдала не одну строку");
        assertEquals("received", listRows(drafts).getFirst().get("statusCode"),
                "на отобранной странице документ другого статуса");
        assertTrue(listTotal(drafts) <= listTotal(documents.list(user, DocumentType.RECEIPTS, Map.of())),
                "отбор со страницей расширил список");
    }

    /* ---------------- помощники ---------------- */

    /**
     * Колонки, которые портал готов показать ЭТОМУ человеку.
     *
     * <p>Берутся из той же функции, по которой строится заголовок
     * таблицы, и под контекстом поклажедателя: без него запреты
     * клиента не вычисляются, и список пришёл бы не тот.
     */
    private List<String> columnIds(DocumentType type) {
        return new org.springframework.transaction.support.TransactionTemplate(txManager)
                .execute(tx -> {
                    jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                            .param("id", depositorId(USER, DEPOSITOR))
                            .query(String.class).single();
                    return jdbc.sql("SELECT column_id FROM dbo.table_columns("
                                    + " :user::uuid, 'ru'::text, :table::text)")
                            .param("user", userId(USER))
                            .param("table", type.path())
                            .query(String.class)
                            .list();
                });
    }

    private static double number(Object value) {
        return value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private void setAvailability(String table, String column, boolean available) {
        columns.saveDepositorColumn(portalAdmin(), depositorId(ADMIN, DEPOSITOR),
                table, column, available, true);
    }

    private PortalUser signedInUser() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
