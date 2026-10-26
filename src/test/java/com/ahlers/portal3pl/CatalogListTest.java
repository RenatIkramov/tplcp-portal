package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.lists.CatalogList;
import com.ahlers.portal3pl.lists.ListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Товары и упаковки на общем механизме списков: отбор, порядок,
 * страницы, архив.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНО ОТ ДОКУМЕНТОВ. Механизм один, но у этих двух
 * списков есть своё, чего нет у документов: архив как свойство списка
 * и галочка по логической колонке. Оба легко сломать молча — архив
 * показал бы погашенные позиции всегда, а галочка отобрала бы не то, —
 * и в обоих случаях человек увидел бы список, которому верит.
 *
 * <p>Проверки сравнивают ПОВЕДЕНИЕ, а не состав базы: «отбор сузил»
 * и «в выдаче только запрошенное», а не «ровно тринадцать позиций».
 * Точные числа ломались от каждого товара, заведённого руками при
 * другой проверке.
 */
class CatalogListTest extends PortalTestBase {

    @Autowired
    ListService lists;

    /**
     * Справочник фильтров описывает себя целиком.
     *
     * <p>Фильтр без подписи или с незнакомым видом — это поле, которое
     * экран нарисует пустым или не нарисует вовсе, и оба случая человек
     * увидит как «фильтра тут нет».
     */
    @Test
    void catalogueDescribesEveryFilter() {
        PortalUser user = signedInUser();
        for (CatalogList list : List.of(CatalogList.SKU, CatalogList.PACKAGING)) {
            List<Map<String, Object>> filters = lists.filters(user, list);
            assertTrue(filters.size() > 5,
                    list.tableKey() + ": фильтров подозрительно мало — " + filters.size());

            int chosen = 0;
            for (Map<String, Object> f : filters) {
                assertFalse(String.valueOf(f.get("label")).isBlank(),
                        list.tableKey() + ": фильтр без подписи — " + f.get("columnId"));
                assertTrue(List.of("text", "enum", "date", "number", "flag", "bool")
                                .contains(f.get("filterKind")),
                        list.tableKey() + ": неизвестный вид фильтра — " + f);
                // Имя колонки базы наружу не отдаётся: экрану оно
                // не нужно, а рассказывать об устройстве без нужды незачем.
                assertFalse(f.containsKey("filterColumn"),
                        list.tableKey() + ": имя колонки базы ушло на экран — " + f);
                if ("enum".equals(f.get("filterKind"))) {
                    assertNotNull(f.get("options"),
                            list.tableKey() + ": у справочного фильтра нет вариантов — "
                                    + f.get("columnId"));
                }
                if (Boolean.TRUE.equals(f.get("isChosen"))) {
                    chosen++;
                }
            }
            assertTrue(chosen >= 3,
                    list.tableKey() + ": на панели по умолчанию мало полей — " + chosen);
        }
    }

    /**
     * Варианты справочных фильтров — те, что действительно встречаются
     * у этого клиента.
     *
     * <p>Группа и вид товара — свободный текст поклажедателя (V38),
     * справочника у них нет. Вписанный заранее список («Сырьё»,
     * «Готовая продукция») был наследством прототипа: у настоящего
     * клиента он не совпадал ни с чем, и фильтр не находил ничего.
     */
    @Test
    void enumOptionsComeFromTheClientsOwnData() {
        PortalUser user = signedInUser();
        List<String> groups = optionsOf(lists.filters(user, CatalogList.SKU), "group");
        assertFalse(groups.isEmpty(), "список групп пуст");

        // Вместе с архивом: группа может встречаться только у погашенной
        // позиции, и список вариантов собирается по всему справочнику.
        List<Map<String, Object>> all = listRows(lists.page(user, CatalogList.SKU,
                Map.of("pageSize", "200", "withArchived", "true")));
        List<String> real = all.stream()
                .map(r -> String.valueOf(r.get("itemGroupName")))
                .distinct()
                .toList();
        for (String group : groups) {
            assertTrue(real.contains(group),
                    "в фильтре группа, которой нет ни у одного товара: " + group);
        }
    }

    /** Отбор сужает список, и в выдаче только запрошенное. */
    @Test
    void filtersNarrowTheList() {
        PortalUser user = signedInUser();
        int total = listTotal(lists.page(user, CatalogList.SKU, Map.of()));
        assertTrue(total > 1, "в справочнике слишком мало товаров для проверки отбора");

        String group = optionsOf(lists.filters(user, CatalogList.SKU), "group").getFirst();
        Map<String, Object> narrowed = lists.page(user, CatalogList.SKU,
                Map.of("filter.group", group, "pageSize", "200"));
        assertTrue(listTotal(narrowed) < total, "отбор по группе не сузил список");
        assertFalse(listRows(narrowed).isEmpty(), "отбор по группе ничего не нашёл");
        for (Map<String, Object> row : listRows(narrowed)) {
            assertEquals(group, row.get("itemGroupName"), "в выдаче товар другой группы");
        }

        // Упаковки отбираются по тому же правилу: тип — точным значением.
        String type = optionsOf(lists.filters(user, CatalogList.PACKAGING), "packagingTypeName")
                .getFirst();
        List<Map<String, Object>> boxes = listRows(lists.page(user, CatalogList.PACKAGING,
                Map.of("filter.packagingTypeName", type, "pageSize", "200")));
        assertFalse(boxes.isEmpty(), "отбор упаковок по типу ничего не нашёл");
        for (Map<String, Object> row : boxes) {
            assertEquals(type, row.get("packagingTypeName"), "в выдаче упаковка другого типа");
        }
    }

    /**
     * Архив: погашенных позиций в списке нет, пока их не попросят.
     *
     * <p>Проверка сама гасит товар и сама возвращает его: опираться
     * на то, что в демо-данных есть архивная позиция, значило бы
     * проверять состав базы.
     */
    @Test
    void archivedProductsAppearOnlyWhenAsked() {
        PortalUser user = signedInUser();
        String code = String.valueOf(listRows(lists.page(user, CatalogList.SKU,
                Map.of("pageSize", "1"))).getFirst().get("code"));

        inContext("UPDATE dbo.sku SET is_active = false WHERE code = '" + code + "'");
        try {
            assertFalse(codesOf(user, Map.of("pageSize", "200")).contains(code),
                    "погашенный товар остался в списке");
            assertTrue(codesOf(user, Map.of("pageSize", "200", "withArchived", "true"))
                            .contains(code),
                    "погашенный товар не показался даже по просьбе");
        } finally {
            inContext("UPDATE dbo.sku SET is_active = true WHERE code = '" + code + "'");
        }
        assertTrue(codesOf(user, Map.of("pageSize", "200")).contains(code),
                "возвращённый из архива товар не вернулся в список");
    }

    /**
     * Галочка по логической колонке отбирает по признаку, а не по тексту.
     *
     * <p>Пустое значение — это «не отбирать»: экран присылает панель
     * целиком, и снятая галочка приходит пустой. Если бы пустое
     * значение понималось как «нет признака», снятая галочка оставляла
     * бы в списке только нехрупкие товары — молча.
     */
    @Test
    void theFlagFiltersByTheFlagAndAnEmptyOneFiltersNothing() {
        PortalUser user = signedInUser();
        String code = String.valueOf(listRows(lists.page(user, CatalogList.SKU,
                Map.of("pageSize", "1"))).getFirst().get("code"));
        int total = listTotal(lists.page(user, CatalogList.SKU, Map.of()));

        assertEquals(total, listTotal(lists.page(user, CatalogList.SKU,
                        Map.of("filter.isFragile", ""))),
                "снятая галочка сузила список");

        inContext("UPDATE dbo.sku SET is_fragile = true WHERE code = '" + code + "'");
        try {
            List<Map<String, Object>> fragile = listRows(lists.page(user, CatalogList.SKU,
                    Map.of("filter.isFragile", "true", "pageSize", "200")));
            assertFalse(fragile.isEmpty(), "галочка «хрупкий» ничего не нашла");
            for (Map<String, Object> row : fragile) {
                assertEquals(Boolean.TRUE, row.get("isFragile"),
                        "в выдаче нехрупкий товар");
            }
        } finally {
            inContext("UPDATE dbo.sku SET is_fragile = false WHERE code = '" + code + "'");
        }
    }

    /**
     * Страницы не теряют и не повторяют строк.
     *
     * <p>Это свойство порядка, а не страниц: без устойчивого хвоста
     * в ORDER BY строки с одинаковым значением перескакивают между
     * запросами, и один товар человек увидит дважды, другого — ни разу.
     */
    @Test
    void pagesCoverTheListExactlyOnce() {
        PortalUser user = signedInUser();
        for (CatalogList list : List.of(CatalogList.SKU, CatalogList.PACKAGING)) {
            int total = listTotal(lists.page(user, list, Map.of()));
            assertTrue(total > 1, list.tableKey() + ": строк слишком мало для проверки страниц");

            List<Object> byPages = new ArrayList<>();
            for (int page = 1; page <= total; page++) {
                List<Map<String, Object>> rows = listRows(lists.page(user, list,
                        Map.of("page", String.valueOf(page), "pageSize", "1")));
                if (rows.isEmpty()) {
                    break;
                }
                byPages.add(rows.getFirst());
            }
            assertEquals(total, byPages.size(),
                    list.tableKey() + ": по одной строке на страницу собралось не всё");
            assertEquals(total, Set.copyOf(byPages).size(),
                    list.tableKey() + ": страницы повторяют строки");
        }
    }

    /** Порядок задаётся идентификатором колонки экрана, а не колонкой базы. */
    @Test
    void sortingGoesByTheScreensColumnId() {
        PortalUser user = signedInUser();
        List<String> up = column(listRows(lists.page(user, CatalogList.SKU,
                Map.of("sort", "name", "dir", "asc", "pageSize", "200"))), "name");
        List<String> down = column(listRows(lists.page(user, CatalogList.SKU,
                Map.of("sort", "name", "dir", "desc", "pageSize", "200"))), "name");
        assertEquals(up.size(), down.size(), "порядок изменил состав списка");
        // Порядок сверяется с самой базой, а не с compareTo из Java.
        // Первая версия проверки сравнивала строки по кодам символов —
        // и упала на «I сорт» после кириллицы: база сортирует по правилам
        // языка (сопоставление базы), а Java ставит латиницу раньше
        // кириллицы. Неправа была проверка, не портал: человек ждёт
        // именно того порядка, который даёт база.
        //
        // Хвост порядка — тот же, что у CatalogList.SKU: code, sku_id.
        assertEquals(inDatabaseOrder("ASC"), up, "по возрастанию порядок не тот, что у базы");
        assertEquals(inDatabaseOrder("DESC"), down, "по убыванию порядок не тот, что у базы");

        expectRejection(400, () -> lists.page(user, CatalogList.SKU,
                Map.of("sort", "item_group_name")));
    }

    /** Незнакомая и скрытая колонка отбора отвергаются, а не игнорируются. */
    @Test
    void unknownFilterIsRefused() {
        PortalUser user = signedInUser();
        expectRejection(400, () -> lists.page(user, CatalogList.SKU,
                Map.of("filter.nosuchthing", "x")));
        // Граница у того, что диапазоном не бывает, — ошибка экрана.
        // Молчать о ней значит отдать список, отобранный наугад.
        expectRejection(400, () -> lists.page(user, CatalogList.SKU,
                Map.of("filter.code.from", "A")));
    }

    /* ---------------------- вспомогательное ---------------------- */

    /**
     * Вход и ссылка на вошедшего: фильтры читаются от его имени.
     *
     * <p>Свой в каждом классе проверок, как в DocumentPagingTest
     * и ReadApiTest: общего в основе нет.
     */
    private PortalUser signedInUser() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }

    /**
     * Наименования рабочих товаров в порядке, который даёт сама база.
     *
     * <p>Разделитель — символ 0x1F (разделитель единиц): в наименовании
     * товара его не бывает, а запятая и точка с запятой бывают.
     */
    private List<String> inDatabaseOrder(String direction) {
        String joined = singleValue("""
                SELECT COALESCE(string_agg(COALESCE(name, 'null'), chr(31)
                         ORDER BY name %s NULLS LAST, code, sku_id), '')
                  FROM dbo.v_sku_full
                 WHERE COALESCE(is_active, true)
                """.formatted(direction));
        return joined.isEmpty() ? List.of() : List.of(joined.split("\u001f", -1));
    }

    private List<String> codesOf(PortalUser user, Map<String, String> params) {
        return column(listRows(lists.page(user, CatalogList.SKU, params)), "code");
    }

    private List<String> column(List<Map<String, Object>> rows, String field) {
        return rows.stream().map(r -> String.valueOf(r.get(field))).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> optionsOf(List<Map<String, Object>> filters, String columnId) {
        Map<String, Object> filter = filters.stream()
                .filter(f -> columnId.equals(f.get("columnId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет фильтра " + columnId));
        List<Map<String, Object>> options =
                (List<Map<String, Object>>) filter.get("options");
        return options.stream().map(o -> String.valueOf(o.get("value"))).toList();
    }
}
