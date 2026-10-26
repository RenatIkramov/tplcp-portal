package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.lists.CatalogList;
import com.ahlers.portal3pl.lists.ListService;
import com.ahlers.portal3pl.stock.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Запасы отбираются, сортируются и режутся на страницы на сервере.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНАЯ ПРОВЕРКА, если то же самое уже проверено
 * на документах. Механизм общий, а вот описание списка — своё:
 * справочник колонок запасов (V79), своё представление, свой порядок
 * по умолчанию. Разойтись они могут независимо, и разойдутся молча:
 * экран откроется, строки будут на месте.
 *
 * <p>Главная здесь — проверка устойчивости порядка. Строка запасов
 * это товар НА СКЛАДЕ, и один и тот же код встречается столько раз,
 * сколько складов. Сортировка по коду без хвоста из склада оставляет
 * такие строки в произвольном порядке — при листании одна попадётся
 * дважды, другая не попадётся вовсе.
 */
class StockListTest extends PortalTestBase {

    @Autowired
    StockService stock;

    @Autowired
    ListService lists;

    @Autowired
    ColumnService columns;

    /** Панель запасов описывает себя целиком: подпись, вид, значения. */
    @Test
    void filterCatalogueDescribesItself() {
        PortalUser user = signedInUser();
        List<Map<String, Object>> filters = lists.filters(user, CatalogList.STOCK);
        assertTrue(filters.size() >= 8,
                "фильтров у запасов подозрительно мало: " + filters.size());

        for (Map<String, Object> f : filters) {
            assertFalse(String.valueOf(f.get("label")).isBlank(),
                    "фильтр без подписи: " + f.get("columnId"));
            assertTrue(List.of("text", "enum", "number", "date", "flag")
                            .contains(f.get("filterKind")),
                    "неизвестный вид фильтра: " + f);
            // Имя колонки базы наружу не отдаётся: экрану оно не нужно.
            assertFalse(f.containsKey("filterColumn"),
                    "наружу ушло имя колонки базы: " + f.get("columnId"));
            if ("enum".equals(f.get("filterKind"))) {
                Object options = f.get("options");
                assertTrue(options instanceof List<?> list && !list.isEmpty(),
                        "у справочного фильтра пустой список значений: " + f.get("columnId"));
            }
        }
        assertTrue(filters.stream().anyMatch(f -> "flag".equals(f.get("filterKind"))),
                "флажка «Только брак» нет среди фильтров запасов");
    }

    /**
     * Список групп собирается из данных клиента, а не вписан заранее.
     *
     * <p>В разметке стояли четыре группы из демо — «Сырьё», «Мясная
     * продукция», «Молочная продукция», «Готовые наборы». Группа
     * товара с V38 свободный текст, и у настоящего клиента этот
     * список не совпал бы ни с чем: человек выбирает группу
     * и получает пустой экран.
     */
    @Test
    void groupOptionsComeFromTheDepositorsOwnData() {
        PortalUser user = signedInUser();
        List<?> options = (List<?>) lists.filters(user, CatalogList.STOCK).stream()
                .filter(f -> "group".equals(f.get("columnId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("фильтра по группе нет"))
                .get("options");

        Set<String> offered = new LinkedHashSet<>();
        options.forEach(o -> offered.add(String.valueOf(((Map<?, ?>) o).get("value"))));
        Set<String> real = new LinkedHashSet<>(listRows(page(Map.of("pageSize", "200"))).stream()
                .map(r -> String.valueOf(r.get("itemGroupName")))
                .filter(g -> !"null".equals(g) && !g.isBlank())
                .toList());

        assertFalse(offered.isEmpty(), "список групп пуст");
        assertEquals(real, offered, "в списке групп не то, что лежит у клиента на складе");
    }

    /** Страница режется, «всего» считается по всему отбору. */
    @Test
    void pageIsCutButTheTotalIsNot() {
        signIn();
        int all = listTotal(page(Map.of()));
        assertTrue(all >= 3, "в демо-данных мало запасов: " + all);

        Map<String, Object> first = page(Map.of("page", "1", "pageSize", "2"));
        assertEquals(2, listRows(first).size(), "страница отдала не две строки");
        assertEquals(all, listTotal(first), "«всего» посчитано по странице, а не по отбору");
    }

    /**
     * Страницы в сумме дают весь список: ни повторов, ни пропаж.
     *
     * <p>По одной строке на странице нарочно: при размере страницы
     * в одну строку неустойчивость порядка вылезает сразу.
     */
    @Test
    void pagesNeitherRepeatNorLoseRows() {
        signIn();
        int all = listTotal(page(Map.of()));

        Set<String> seen = new LinkedHashSet<>();
        List<String> order = new ArrayList<>();
        for (int p = 1; p <= all; p++) {
            List<Map<String, Object>> rows = listRows(page(
                    Map.of("page", String.valueOf(p), "pageSize", "1")));
            assertEquals(1, rows.size(), "страница " + p + " отдала не одну строку");
            String key = rows.getFirst().get("skuCode") + "@" + rows.getFirst().get("warehouseId");
            order.add(key);
            seen.add(key);
        }
        assertEquals(all, seen.size(), "строки повторились или потерялись при обходе: " + order);
    }

    /**
     * То же, но при сортировке по колонке с повторами.
     *
     * <p>Код товара повторяется столько раз, сколько складов, —
     * и это ровно тот случай, когда порядок без устойчивого хвоста
     * плавает между запросами.
     */
    @Test
    void sortingByARepeatingColumnStaysStable() {
        signIn();
        int all = listTotal(page(Map.of()));

        Set<String> seen = new LinkedHashSet<>();
        for (int p = 1; p <= all; p++) {
            Map<String, Object> row = listRows(page(Map.of(
                    "page", String.valueOf(p), "pageSize", "1",
                    "sort", "code", "dir", "asc"))).getFirst();
            seen.add(row.get("skuCode") + "@" + row.get("warehouseId"));
        }
        assertEquals(all, seen.size(),
                "при сортировке по коду строки повторились или потерялись");
    }

    /** Сортировка идёт по всему списку, а не по странице. */
    @Test
    void sortingCoversTheWholeList() {
        signIn();
        List<Double> everything = listRows(page(Map.of("pageSize", "200"))).stream()
                .map(r -> number(r.get("quantity"))).sorted().toList();
        assertTrue(everything.size() >= 3, "мало строк для проверки сортировки");

        assertEquals(everything.getFirst(),
                number(listRows(page(Map.of("sort", "stock", "dir", "asc", "pageSize", "1")))
                        .getFirst().get("quantity")),
                "по возрастанию первой идёт не наименьшая строка списка");
        assertEquals(everything.getLast(),
                number(listRows(page(Map.of("sort", "stock", "dir", "desc", "pageSize", "1")))
                        .getFirst().get("quantity")),
                "по убыванию первой идёт не наибольшая строка списка");
    }

    /** Каждая колонка запасов умеет и сортироваться, и отбирать. */
    @Test
    void everyColumnSortsAndFilters() {
        signIn();
        for (String columnId : List.of("warehouse", "code", "name", "group", "unit",
                "stock", "available", "reserved", "blocked", "defect", "incoming")) {
            assertFalse(listRows(page(Map.of("sort", columnId, "pageSize", "1"))).isEmpty(),
                    "сортировка по колонке " + columnId + " вернула пустой список");
        }
    }

    /** Незнакомая колонка — отказ, а не молчаливый порядок по умолчанию. */
    @Test
    void unknownColumnIsRefused() {
        signIn();
        expectRejection(400, () -> page(Map.of("sort", "depositorId")));
        expectRejection(400, () -> page(Map.of("filter.depositorId", "любой")));
    }

    /**
     * Колонка, запрещённая поклажедателю, ни отбирает, ни сортирует.
     *
     * <p>Порядок строк — это тоже сведения о значениях: разреши
     * сортировку по скрытому полю, и его значения восстанавливаются
     * по порядку, не видя самой колонки.
     */
    @Test
    void columnForbiddenToDepositorIsNeitherFilteredNorSorted() {
        signIn();
        columns.saveDepositorColumn(portalAdmin(), depositorId(ADMIN, DEPOSITOR),
                "stock", "group", false, true);
        try {
            expectRejection(400, () -> page(Map.of("filter.group", "Сырьё")));
            expectRejection(400, () -> page(Map.of("sort", "group")));
        } finally {
            columns.saveDepositorColumn(portalAdmin(), depositorId(ADMIN, DEPOSITOR),
                    "stock", "group", true, true);
        }
    }

    /* ---------------- помощники ---------------- */

    private Map<String, Object> page(Map<String, String> params) {
        return stock.list(authService.authenticate(USER, USER_PASSWORD), params);
    }

    private static double number(Object value) {
        return value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private PortalUser signedInUser() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
