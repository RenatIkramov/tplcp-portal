package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.lists.ListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Фильтры списка документов: что разрешено и во что превращается.
 *
 * <p>ПОЧЕМУ ОТДЕЛЬНАЯ ПРОВЕРКА. Здесь проходит граница доверия: имя
 * колонки приходит из адреса запроса и попадает в текст SQL. Безопасно
 * это ровно потому, что имя сверяется со справочником, а не подставляется
 * как есть. Проверка держит именно это свойство — незнакомая и скрытая
 * колонка отвергаются, — а не только «фильтр что-то отбирает».
 *
 * <p>Вторая половина проверок про то, на чём мы уже спотыкались:
 * отбор по статусу шёл по человеческому тексту и на английском языке
 * не находил ничего, а «дата по» отсекала последний день диапазона.
 */
class DocumentFilterTest extends PortalTestBase {

    @Autowired
    DocumentService documents;

    @Autowired
    ColumnService columns;

    /**
     * Панель фильтров у всех списков портала одна и та же служба:
     * документы, запасы, справочники. Проверки ходят туда же, куда
     * и экран, — через неё.
     */
    @Autowired
    ListService lists;

    /**
     * Справочник фильтров не пуст и описывает себя целиком.
     *
     * <p>Порог грубый нарочно: он ловит пустой ответ, а не следит
     * за точным числом фильтров — оно меняется миграциями.
     */
    @Test
    void catalogueDescribesEveryFilter() {
        PortalUser user = signedInUser();
        for (DocumentType type : DocumentType.values()) {
            List<Map<String, Object>> filters = lists.filters(user, type);
            assertTrue(filters.size() > 10,
                    type.path() + ": фильтров подозрительно мало — " + filters.size());

            for (Map<String, Object> f : filters) {
                assertFalse(String.valueOf(f.get("label")).isBlank(),
                        type.path() + ": фильтр без подписи — " + f.get("columnId"));
                assertTrue(List.of("text", "enum", "date", "number").contains(f.get("filterKind")),
                        type.path() + ": неизвестный вид фильтра — " + f);
                // Имя колонки базы наружу не отдаётся: экрану оно
                // не нужно, а рассказывать об устройстве без нужды незачем.
                assertFalse(f.containsKey("filterColumn"),
                        type.path() + ": наружу ушло имя колонки базы — " + f.get("columnId"));
                if ("enum".equals(f.get("filterKind"))) {
                    Object options = f.get("options");
                    assertTrue(options instanceof List<?> list && !list.isEmpty(),
                            type.path() + ": у справочного фильтра пустой список значений — "
                                    + f.get("columnId"));
                }
            }
            assertTrue(filters.stream().anyMatch(f -> Boolean.TRUE.equals(f.get("isDefault"))),
                    type.path() + ": нет ни одного фильтра по умолчанию — панель будет пустой");
            // Без личных настроек выбранное обязано совпасть с набором
            // по умолчанию: разойдись оно — и панель открылась бы пустой
            // у каждого, кто окна ⚙ Фильтры ни разу не открывал.
            assertEquals(filters.stream().filter(f -> Boolean.TRUE.equals(f.get("isDefault"))).count(),
                    filters.stream().filter(f -> Boolean.TRUE.equals(f.get("isChosen"))).count(),
                    type.path() + ": выбранное и набор по умолчанию разошлись без настройки");
        }
    }

    /**
     * Статус отбирается по коду, а не по подписи.
     *
     * <p>Это и была поломка: экран сравнивал переведённую подпись
     * («Draft») с наименованием статуса из базы («Черновик»), которое
     * не переводится вовсе. По-русски совпадало случайно, по-английски
     * фильтр не находил ничего.
     */
    @Test
    void statusIsFilteredByCodeNotByLabel() {
        PortalUser user = signedInUser();
        List<Map<String, Object>> drafts = listRows(documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.status", "draft")));
        assertFalse(drafts.isEmpty(), "фильтр по коду статуса ничего не нашёл");
        for (Map<String, Object> row : drafts) {
            assertEquals("draft", row.get("statusCode"), "в выборке документ другого статуса");
        }

        // Подпись статуса фильтром не является: прислали её — ничего
        // не нашлось бы, и это правильно.
        assertTrue(listRows(documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.status", "Черновик"))).isEmpty(),
                "по подписи статуса отбирать нельзя — иначе фильтр зависит от языка");
    }

    /**
     * «Дата по» включает последний день целиком.
     *
     * <p>С V62 дата документа — отметка времени, и условие
     * {@code doc_date <= DATE} отсекло бы всё, что случилось в этот день
     * позже полуночи. На этой же грабле портал стоял в прежнем фильтре.
     */
    @Test
    void dateRangeIncludesTheLastDay() {
        PortalUser user = signedInUser();
        List<Map<String, Object>> all = listRows(documents.list(user, DocumentType.RECEIPTS,
                Map.of()));
        assertFalse(all.isEmpty(), "в демо-данных нет поступлений — проверять нечего");

        String day = String.valueOf(all.getFirst().get("docDate")).substring(0, 10);
        List<Map<String, Object>> sameDay = listRows(documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.date.from", day, "filter.date.to", day)));
        assertFalse(sameDay.isEmpty(),
                "документ от " + day + " не попал в диапазон «с " + day + " по " + day + "»");
    }

    /** Текстовый фильтр ищет вхождение и не различает регистр. */
    @Test
    void textFilterMatchesPartOfTheValue() {
        PortalUser user = signedInUser();
        Map<String, Object> everything = documents.list(user, DocumentType.RECEIPTS, Map.of());
        String supplier = listRows(everything).stream()
                .map(r -> String.valueOf(r.get("supplierName")))
                .filter(s -> s.length() > 4 && !"null".equals(s))
                .findFirst()
                .orElse(null);
        assertFalse(supplier == null, "в демо-данных нет поступления с поставщиком");

        String part = supplier.substring(1, 4).toUpperCase();
        Map<String, Object> found = documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.supplier", part));
        assertFalse(listRows(found).isEmpty(),
                "поиск по части имени поставщика «" + part + "» не нашёл");
        assertTrue(listTotal(found) <= listTotal(everything), "фильтр не расширяет список");
    }

    /** Неизвестная колонка — отказ, а не молчаливый пропуск. */
    @Test
    void unknownColumnIsRefused() {
        PortalUser user = signedInUser();
        expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.depositorId", "любой")));
    }

    /**
     * Колонка, запрещённая поклажедателю, фильтром не становится.
     *
     * <p>Это не формальность: разреши отбор по скрытому полю — и его
     * значения можно вытащить перебором, не видя самой колонки.
     */
    @Test
    void columnForbiddenToDepositorIsNotFilterable() {
        PortalUser user = signedInUser();
        assertFalse(lists.filters(user, DocumentType.RECEIPTS).isEmpty());

        setAvailability("receipts", "supplier", false);
        try {
            assertTrue(lists.filters(user, DocumentType.RECEIPTS).stream()
                            .noneMatch(f -> "supplier".equals(f.get("columnId"))),
                    "скрытая от клиента колонка осталась в списке фильтров");
            expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                    Map.of("filter.supplier", "что угодно")));
        } finally {
            setAvailability("receipts", "supplier", true);
        }
    }

    /** Диапазон там, где его не бывает, — тоже отказ. */
    @Test
    void rangeOnATextColumnIsRefused() {
        PortalUser user = signedInUser();
        expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.supplier.from", "А")));
        expectRejection(400, () -> documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.date", "2026-01-01")));
    }

    /** Пустое поле — это «не фильтровать», а не «найти пустые». */
    @Test
    void blankValueDoesNotNarrowTheList() {
        PortalUser user = signedInUser();
        int all = listTotal(documents.list(user, DocumentType.RECEIPTS, Map.of()));
        int blank = listTotal(documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.supplier", "   ", "filter.date.from", "")));
        assertEquals(all, blank, "пустые поля панели сузили список");
    }

    /**
     * Выбор фильтров человека вытесняет набор по умолчанию, а сброс
     * возвращает его.
     *
     * <p>Проверяется и то, что выбор не добавляется к умолчаниям:
     * иначе снятая галочка ничего бы не снимала, и окно ⚙ Фильтры
     * выглядело бы сломанным ровно в половине действий.
     */
    @Test
    void chosenFiltersReplaceTheDefaultSet() {
        PortalUser user = signedInUser();
        long byDefault = chosen(user).size();
        assertTrue(byDefault > 1, "набор по умолчанию подозрительно мал: " + byDefault);

        try {
            lists.saveFilters(user, DocumentType.RECEIPTS, List.of("trip"));
            assertEquals(List.of("trip"), chosen(user),
                    "личный выбор не вытеснил набор по умолчанию");

            // Порядок — порядок присланного списка: человек перетаскивал
            // строки в окне именно за этим.
            lists.saveFilters(user, DocumentType.RECEIPTS, List.of("supplier", "trip"));
            assertEquals(List.of("supplier", "trip"), chosen(user),
                    "порядок фильтров не совпал с присланным");

            // Пустой выбор — отказ. Записать его было бы хуже отказа:
            // ноль строк уже значит «не настраивал», и панель вернулась
            // бы к набору по умолчанию — окно сделало бы обратное тому,
            // что человек просил.
            expectRejection(400, () ->
                    lists.saveFilters(user, DocumentType.RECEIPTS, List.of()));
            assertEquals(List.of("supplier", "trip"), chosen(user),
                    "отвергнутый пустой выбор не должен был менять панель");
        } finally {
            lists.resetFilters(user, DocumentType.RECEIPTS);
        }
        assertEquals(byDefault, chosen(user).size(), "сброс не вернул набор по умолчанию");
    }

    /**
     * Запрещённую поклажедателю колонку нельзя записать себе в выбор.
     *
     * <p>Без этой проверки оставалась бы щель: записать имя колонки
     * в свой выбор напрямую — и она вернулась бы из справочника уже
     * как своя, в обход запрета.
     */
    @Test
    void forbiddenColumnCannotBeChosen() {
        PortalUser user = signedInUser();
        setAvailability("receipts", "supplier", false);
        try {
            expectRejection(400, () ->
                    lists.saveFilters(user, DocumentType.RECEIPTS, List.of("supplier")));
        } finally {
            setAvailability("receipts", "supplier", true);
            lists.resetFilters(user, DocumentType.RECEIPTS);
        }
    }

    /* ---------------- помощники ---------------- */

    /** Колонки, которые человек держит на панели поступлений, в его порядке. */
    private List<String> chosen(PortalUser user) {
        return lists.filters(user, DocumentType.RECEIPTS).stream()
                .filter(f -> Boolean.TRUE.equals(f.get("isChosen")))
                .map(f -> String.valueOf(f.get("columnId")))
                .toList();
    }


    /**
     * Прячет колонку от поклажедателя тем же путём, каким это делает
     * администратор с экрана, — а не записью в таблицу напрямую.
     * Проверка должна ходить той же дорогой, что человек.
     */
    private void setAvailability(String table, String column, boolean available) {
        columns.saveDepositorColumn(portalAdmin(), depositorId(ADMIN, DEPOSITOR),
                table, column, available, true);
    }

    private PortalUser signedInUser() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
