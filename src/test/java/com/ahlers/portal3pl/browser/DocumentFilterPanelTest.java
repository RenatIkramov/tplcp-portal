package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Панель фильтров: собралась из справочника и отбирает на сервере.
 *
 * <p>ПОЧЕМУ ЧЕРЕЗ БРАУЗЕР. Серверную половину держит
 * {@code DocumentFilterTest}: он ходит в сервис напрямую и об экране
 * не знает. А вся прежняя поломка жила ровно между сервисом и экраном:
 * панель была вписана в разметку руками, отбирала строки, уже лежащие
 * на странице, и сравнивала статус по переведённой подписи. Из
 * сервисного теста этого не видно — видно только на отрисованной
 * странице.
 *
 * <p>Проверки нарочно читают то, что видит человек: подписи полей,
 * число строк в таблице, счётчик рядом с заголовком. И отдельно —
 * что запрос за данными ушёл с фильтром: панель, которая выглядит
 * работающей, но отбор не отправляет, хуже отсутствующей панели.
 */
class DocumentFilterPanelTest extends BrowserTestBase {

    /**
     * Панель построена: номер, статус, диапазон даты, поставщик, рейс.
     *
     * <p>Проверяется и обратное — что в панели не осталось полей,
     * вписанных в разметку. Их имена были прежними именами фильтров,
     * и живой элемент с таким именем означал бы, что правка разметки
     * доехала не целиком.
     */
    @Test
    void panelIsBuiltFromTheCatalogue() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-status");

        assertNotNull(page.querySelector("#filter-receipts-erpNo"),
                "в панели нет фильтра по номеру");
        assertNotNull(page.querySelector("#filter-receipts-supplier"),
                "в панели нет фильтра по поставщику");
        // Объединённого поля «Номер или контрагент» здесь быть не должно:
        // одно поле, ищущее по четырём разным вещам, подписать нечем.
        assertNull(page.querySelector("#filter-receipts-search"),
                "в панели осталось объединённое поле поиска");
        assertNotNull(page.querySelector("#filter-receipts-date-from"),
                "у фильтра по дате нет границы «с» — диапазон задать нечем");
        assertNotNull(page.querySelector("#filter-receipts-date-to"),
                "у фильтра по дате нет границы «по»");
        assertNotNull(page.querySelector("#filter-receipts-trip"),
                "в панели нет фильтра по рейсу");

        // Подпись фильтра — это подпись колонки, и приходит она
        // с сервера. Пустая подпись означала бы, что справочник
        // до экрана не доехал, а поля всё равно нарисовались.
        assertEquals(label("receipts", "trip"), labelOf("filter-receipts-trip"),
                "подпись фильтра по рейсу не совпадает с подписью колонки");
        assertTrue(labelOf("filter-receipts-date-from").startsWith(label("receipts", "date")),
                "подпись «с» не названа колонкой: " + labelOf("filter-receipts-date-from"));

        for (String old : List.of("receiptsSearchNo", "receiptsSearchSupplier",
                "receiptsSearchTrip", "receiptsFilterStatus",
                "receiptsDateFrom", "receiptsDateTo")) {
            assertNull(page.querySelector("#" + old),
                    "в панели осталось поле из разметки — " + old);
        }
        assertNoJsErrors();
    }

    /**
     * Отбор по статусу уходит на сервер и список сужается.
     *
     * <p>Это и была первая из пяти поломок: экран сравнивал
     * переведённую подпись статуса с наименованием из базы, которое
     * не переводится. Поэтому смотрим не только на число строк,
     * но и на адрес запроса: сузиться список мог бы и в браузере.
     */
    @Test
    void statusFilterGoesToTheServer() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-status");
        page.waitForFunction("() => document.querySelectorAll('#receiptsBody tr').length > 0");

        // Диапазон даты по умолчанию мешает счёту: он ограничивает
        // список и прошлым месяцем, и сегодняшним днём. Для этой
        // проверки нужен весь список, поэтому даты очищаются.
        page.evaluate("""
                () => {
                  document.getElementById('filter-receipts-date-from').value = '';
                  document.getElementById('filter-receipts-date-to').value = '';
                }
                """);
        // Ждём ОТВЕТ на запрос без дат, а не «отметка загрузки есть»:
        // она есть с первой загрузки, и ожидание вернулось бы сразу,
        // а строки на экране остались бы прежними — счёт вышел бы
        // не тот, который мы думаем.
        page.waitForResponse(
                response -> response.url().contains("/api/receipts")
                        && !response.url().contains("filter.date"),
                () -> page.evaluate("() => onReceiptsFilterChange()"));
        page.waitForTimeout(300);
        int all = rowCount();
        assertTrue(all > 1, "в демо-данных слишком мало поступлений: " + all);

        String url = page.waitForRequest(
                r -> r.url().contains("/api/receipts") && r.url().contains("filter.status="),
                () -> page.evaluate("""
                        () => {
                          document.getElementById('filter-receipts-status').value = 'draft';
                          onReceiptsFilterChange();
                        }
                        """)).url();
        assertTrue(url.contains("filter.status=draft"),
                "в запросе нет кода статуса: " + url);

        page.waitForTimeout(300);
        int drafts = rowCount();
        assertTrue(drafts > 0, "отбор по статусу «черновик» не нашёл ни одного документа");
        assertTrue(drafts < all,
                "отбор по статусу не сузил список: было " + all + ", стало " + drafts);
        assertNoJsErrors();
    }

    /**
     * На английском языке отбор по статусу работает так же.
     *
     * <p>Прежний фильтр здесь и ломался: он сравнивал английскую
     * подпись («Draft») с русским наименованием статуса из базы
     * («Черновик») — и не находил ничего. Отказа при этом не было,
     * человек просто видел пустой список и не знал, почему.
     */
    @Test
    void statusFilterWorksInEnglishToo() {
        PortalUser user = signedInUser();
        localeOf(user, "en");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-status");

        // Ждём ответ на запрос с отбором, а не «отметка загрузки есть»:
        // она есть с первой загрузки, и ожидание вернулось бы сразу.
        page.waitForResponse(
                response -> response.url().contains("/api/receipts")
                        && response.url().contains("filter.status=draft"),
                () -> page.evaluate("""
                        () => {
                          document.getElementById('filter-receipts-date-from').value = '';
                          document.getElementById('filter-receipts-date-to').value = '';
                          document.getElementById('filter-receipts-status').value = 'draft';
                          onReceiptsFilterChange();
                        }
                        """));
        page.waitForTimeout(300);
        assertTrue(rowCount() > 0,
                "на английском языке отбор по статусу не нашёл ничего — "
                        + "фильтр снова зависит от языка подписи");

        // Значение в списке — код статуса, а подпись человеку видна
        // переведённая. Если наружу ушла подпись, фильтр снова
        // сломается на следующем языке.
        assertEquals("draft", page.inputValue("#filter-receipts-status"),
                "в выпадающем списке статусов значение не код");
        assertNoJsErrors();
    }

    /**
     * «Очистить фильтры» очищает всё — и даты тоже.
     *
     * <p>Раньше кнопка возвращала диапазон дат по умолчанию: пустой
     * диапазон означал бы список за все годы, а сервер отдавал его
     * целиком. Страницы считает сервер (V78), выгрузки всей истории
     * больше не случится — и «Очистить», которая что-то оставляет
     * заполненным, сбивает с толку сильнее, чем длинный список.
     */
    @Test
    void clearingEmptiesEveryField() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-date-from");

        page.evaluate("""
                () => {
                  document.getElementById('filter-receipts-date-from').value = '2026-01-01';
                  document.getElementById('filter-receipts-trip').value = 'нет такого рейса';
                  clearReceiptsFilters();
                }
                """);
        page.waitForTimeout(300);
        assertEquals("", page.inputValue("#filter-receipts-trip"),
                "«Очистить фильтры» оставила текст в поле рейса");
        assertEquals("", page.inputValue("#filter-receipts-date-from"),
                "«Очистить фильтры» оставила дату заполненной");
        assertNoJsErrors();
    }

    /**
     * Окно ⚙ Фильтры: снятая галочка убирает поле с панели, и это
     * держится после перезагрузки страницы.
     *
     * <p>Выбор хранится на сервере, а не в браузере, и проверка идёт
     * через перезагрузку именно поэтому: сохрани его экран у себя —
     * человек настроил бы панель на одной машине и не нашёл настройки
     * на другой.
     */
    @Test
    void filterPickerChoiceSurvivesReload() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-trip");

        try {
            // Через окно, а не запросом: проверка должна ходить той же
            // дорогой, что человек.
            page.evaluate("() => openFilterPicker('receipts')");
            page.waitForSelector("#filterPickerOverlay.active");
            assertTrue(untick("trip"), "в окне ⚙ Фильтры нет строки «Рейс»");
            page.evaluate("() => document.getElementById('filterPickerApply').click()");
            page.waitForFunction("() => !document.getElementById('filter-receipts-trip')");

            page.navigate(baseUrl() + "/");
            page.waitForFunction("() => { const s = document.getElementById('appShell');"
                    + " return s && s.style.display !== 'none'; }");
            page.evaluate("() => showTab('receipts')");
            page.waitForSelector("#filter-receipts-status");
            assertNull(page.querySelector("#filter-receipts-trip"),
                    "после перезагрузки снятый фильтр вернулся на панель");
        } finally {
            documentFiltersReset();
        }
        assertNoJsErrors();
    }

    /**
     * Кнопки панели не переезжают, когда меняется состав фильтров.
     *
     * <p>Полей на панели от четырёх до семи, и человек меняет их число
     * сам из ⚙ Фильтров. Пока кнопки стояли в общем потоке, каждая
     * правка переставляла «Найти» на новое место, и кнопку приходилось
     * искать глазами заново. Теперь они живут отдельной строкой.
     */
    @Test
    void actionButtonsStayPut() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-trip");

        Object before = searchButtonBox();
        try {
            // Убираем одно поле с панели — тем же путём, каким это
            // делает человек.
            page.evaluate("""
                    () => {
                      return fetch('/api/list-filters/receipts', {
                        method: 'PUT',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({columns: ['status']})
                      }).then(() => PORTAL.rebuildFilters());
                    }
                    """);
            page.waitForFunction("() => !document.getElementById('filter-receipts-trip')");
            assertEquals(before, searchButtonBox(),
                    "кнопка «Найти» переехала после изменения состава фильтров");
        } finally {
            documentFiltersReset();
        }
        assertNoJsErrors();
    }

    /**
     * «Применить» в окне ⚙ Фильтры видна без прокрутки.
     *
     * <p>Фильтруемых колонок тридцать, и прокручивалась вся панель
     * целиком — вместе с кнопками. «Применить» уезжала на тысячу
     * с лишним пикселей вниз: отметить фильтр человек мог, а применить
     * — только догадавшись прокрутить. Теперь прокручивается список,
     * а кнопки стоят.
     */
    @Test
    void applyButtonNeedsNoScrolling() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-status");

        page.evaluate("() => openFilterPicker('receipts')");
        page.waitForSelector("#filterPickerOverlay.active");
        int rows = ((Number) page.evaluate(
                "() => document.querySelectorAll('#filterPickerList .col-item').length")).intValue();
        assertTrue(rows > 20, "в окне подозрительно мало строк: " + rows);

        assertTrue(Boolean.TRUE.equals(page.evaluate("""
                () => {
                  const b = document.getElementById('filterPickerApply');
                  const r = b.getBoundingClientRect();
                  return r.top >= 0 && r.bottom <= window.innerHeight;
                }
                """)), "кнопка «Применить» вне экрана — её придётся искать прокруткой");
        assertNoJsErrors();
    }

    /**
     * Панель не заполняет даты сама — ни одну.
     *
     * <p>История в два хода. Сперва диапазон «с первого числа прошлого
     * месяца по сегодня» ставился КАЖДОЙ дате на панели, и документ
     * с пустой датой приёмки исчезал из списка без видимой причины:
     * дат в справочнике девять, заполнить их все — значит задать
     * девять условий разом. Потом диапазон оставили одной дате
     * документа: она есть у каждого документа всегда.
     *
     * <p>Теперь не осталось и его. Диапазон стоял не ради человека,
     * а ради размера списка — сервер отдавал весь отбор целиком.
     * Страницы считает сервер (V78), список не распухает, а молча
     * спрятанные документы постарше остались бы поломкой, которую
     * человек объясняет себе сам: поля-то заполнены, и выглядит это
     * как его собственный выбор.
     */
    @Test
    void noDateIsPrefilled() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-date-from");

        try {
            // Ставим на панель четыре даты сразу — тем же путём,
            // каким это делает человек в окне ⚙ Фильтры.
            page.evaluate("""
                    () => fetch('/api/list-filters/receipts', {
                      method: 'PUT',
                      headers: {'Content-Type': 'application/json'},
                      body: JSON.stringify({columns:
                          ['status', 'date', 'eta', 'arrivalDate', 'receivingEndDate']})
                    }).then(() => PORTAL.rebuildFilters())
                    """);
            page.waitForSelector("#filter-receipts-eta-from");

            for (String column : List.of("date", "eta", "arrivalDate", "receivingEndDate")) {
                assertEquals("", page.inputValue("#filter-receipts-" + column + "-from"),
                        "дата «" + column + " с» заполнилась сама — она отберёт лишнее");
                assertEquals("", page.inputValue("#filter-receipts-" + column + "-to"),
                        "дата «" + column + " по» заполнилась сама");
            }

            // «Очистить» живёт по тому же правилу: пусто — значит пусто.
            page.evaluate("() => clearReceiptsFilters()");
            page.waitForTimeout(300);
            assertEquals("", page.inputValue("#filter-receipts-date-from"),
                    "«Очистить» заполнила дату документа");
            assertEquals("", page.inputValue("#filter-receipts-eta-from"),
                    "«Очистить» заполнила дату, которой заполняться не положено");
        } finally {
            documentFiltersReset();
        }
        assertNoJsErrors();
    }

    /**
     * Выбор в выпадающем списке сам по себе список не перечитывает.
     *
     * <p>Здесь висел onchange: выбрал статус — ушёл запрос. Казалось
     * удобным, а выходило непоследовательно: текстовые поля ждут
     * кнопки, а статус нет, и набрать отбор целиком было нельзя —
     * панель уходила на сервер на полпути.
     *
     * <p>Считаются ЗАПРОСЫ, а не отметка времени загрузки. Сперва
     * сравнивалась она — и это была негодная мера: отметка пишется
     * датой, строкой она выходит с точностью до секунды, а список
     * перечитывается быстрее. «Не изменилась» означало бы и «запроса
     * не было», и «запрос успел пройти в ту же секунду»; ожидание
     * изменения после нажатия по той же причине не дожидалось ничего
     * и падало по времени. Запрос либо был, либо нет — третьего нет.
     */
    @Test
    void choosingInASelectDoesNotSearchByItself() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForSelector("#filter-receipts-status");
        page.waitForFunction("() => window.PORTAL && PORTAL.docsLoadedAt.receipts");

        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        page.onRequest(request -> {
            if (request.url().contains("/api/receipts")) {
                asked.add(request.url());
            }
        });

        page.evaluate("""
                () => {
                  const s = document.getElementById('filter-receipts-status');
                  s.value = 'draft';
                  s.dispatchEvent(new Event('change', {bubbles: true}));
                }
                """);
        page.waitForTimeout(700);
        assertTrue(asked.isEmpty(),
                "список перечитался от одного выбора в списке, без нажатия «Найти»: " + asked);

        // А по кнопке — перечитывается, и отбор уходит на сервер.
        String url = page.waitForRequest(
                request -> request.url().contains("/api/receipts"),
                () -> page.evaluate("() => onReceiptsFilterChange()")).url();
        assertTrue(url.contains("filter.status=draft"),
                "по кнопке ушёл запрос без выбранного статуса: " + url);
        assertNoJsErrors();
    }

    /* ---------------- помощники ---------------- */


    /** Где стоит кнопка «Найти» — в целых пикселях. */
    private Object searchButtonBox() {
        return page.evaluate("""
                () => {
                  const b = document.querySelector('#view-receipts .filter-actions .btn.primary');
                  const r = b.getBoundingClientRect();
                  return Math.round(r.left) + 'x' + Math.round(r.top);
                }
                """);
    }


    /**
     * Снимает галочку у строки окна по подписи колонки.
     * Возвращает false, если строки нет — тогда проверять нечего
     * и об этом надо сказать, а не тихо пройти.
     *
     * <p>Подпись читается у самого {@code label}, а не у строки целиком:
     * строка начинается со значка перетаскивания, и сравнение
     * «начинается с подписи» на нём и спотыкалось. У диапазона подпись
     * вдобавок заканчивается пометкой «(с…по)», поэтому сравнение
     * по вхождению, а не по равенству.
     */
    private boolean untick(String columnId) {
        return Boolean.TRUE.equals(page.evaluate("""
                (want) => {
                  const items = Array.from(
                      document.querySelectorAll('#filterPickerList .col-item'));
                  for (const item of items){
                    const cap = item.querySelector('label');
                    if (!cap || !cap.textContent.trim().includes(want)) continue;
                    const cb = item.querySelector('input[type=checkbox]');
                    if (!cb || !cb.checked) continue;
                    cb.click();
                    return true;
                  }
                  return false;
                }
                """, label("receipts", columnId)));
    }

    /** Возвращает панель к набору по умолчанию — чтобы не мешать соседям. */
    private void documentFiltersReset() {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("SELECT dbo.user_filters_reset(:id::uuid, NULL::text)")
                        .param("id", userId(USER))
                        .query()
                        .listOfRows());
    }


    private int rowCount() {
        return ((Number) page.evaluate(
                "() => document.querySelectorAll('#receiptsBody tr').length")).intValue();
    }

    /** Подпись поля так, как её видит человек. */
    private String labelOf(String fieldId) {
        return (String) page.evaluate(
                "id => { const l = document.querySelector('label[for=\"' + id + '\"]');"
                        + " return l ? l.textContent.trim() : ''; }", fieldId);
    }

    /** Подпись колонки для этого пользователя — из того же справочника. */
    private String label(String table, String columnId) {
        return jdbc.sql("SELECT label FROM dbo.table_columns(:user::uuid, 'ru'::text, :table::text)"
                        + " WHERE column_id = :column::text")
                .param("user", userId(USER))
                .param("table", table)
                .param("column", columnId)
                .query(String.class)
                .single();
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
