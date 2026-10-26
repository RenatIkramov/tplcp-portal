package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Окно ⚙ Колонки показывает ВСЕ колонки справочника, а не только те,
 * что знает вёрстка демо.
 *
 * <p>ЗАЧЕМ. Раскладку колонок решает база (V29), но экран собирал
 * список выбора из массива, вписанного в {@code prototype.js}, и всё,
 * чего там нет, молча отбрасывал. Молчание и было бедой: «Рейс»
 * (настоящая колонка всех трёх списков с V68) и десять «Доп. полей»
 * поклажедателя лежали в справочнике, были разрешены клиенту — и в окно
 * не попадали. Включить их человек не мог никак, а причину увидеть
 * тем более: отсутствие строки ничем не отличается от «такой колонки
 * не бывает».
 *
 * <p>Проверка сравнивает список окна со справочником сервера — то есть
 * спрашивает ровно то, что обещает каталог колонок: что видно
 * на экране, решает он.
 */
class ColumnPickerTest extends BrowserTestBase {

    @Test
    void pickerListsEveryColumnOfTheCatalogue() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");

        // Сверяется по ИДЕНТИФИКАТОРУ колонки, а не по подписи.
        // Сперва сверялось по подписи — и проверка упала на «Доп. поле 1»
        // у Рога и Копыта: строка в окне была, но подпись на экране
        // проходит через переименование поклажедателя и законно
        // отличается от подписи справочника. Подпись — то, что человек
        // читает; тождество колонки — её идентификатор, и сверять надо
        // его, иначе проверка ругается на работающий экран.
        List<String> expected = jdbc.sql("""
                        SELECT column_id FROM dbo.table_columns(
                                 :user::uuid, 'ru'::text, 'receipts'::text)
                        """)
                .param("user", userId(USER))
                .query(String.class)
                .list();
        assertTrue(expected.size() > 30,
                "в справочнике колонок поступлений подозрительно мало строк: " + expected.size());

        page.evaluate("() => openColumnPicker('receipts')");
        page.waitForSelector("#colOverlay.active");
        List<String> offered = pickerColumnIds();

        TreeSet<String> missing = new TreeSet<>(expected);
        missing.removeAll(offered);
        assertTrue(missing.isEmpty(),
                "колонки есть в справочнике, а в окне ⚙ Колонки их нет — включить нельзя: "
                        + missing);

        // Названы отдельно: именно на них поломка и выходила наружу.
        for (String id : List.of("trip", "customField1")) {
            assertTrue(offered.contains(id), "в окне нет колонки " + id);
        }

        // И подпись у каждой строки человеческая, а не сырой ключ:
        // дорисованная колонка без подписи показала бы
        // «col.receipts.trip», и это хуже отсутствия строки.
        TreeSet<String> raw = new TreeSet<>(pickerLabels().stream()
                .filter(l -> l.isBlank() || l.startsWith("col.")
                        || l.startsWith("common.") || l.startsWith("error."))
                .toList());
        assertTrue(raw.isEmpty(), "в окне ⚙ Колонки строки без подписи: " + raw);
        assertNoJsErrors();
    }

    /**
     * Колонка из справочника рисуется в таблице, а не прочерком.
     *
     * <p>Попасть в окно — половина дела: включённая колонка, которой
     * нечем рисовать, выводила бы пустой столбец, и человек решил бы,
     * что данных нет. Проверяется на «Рейсе»: он есть в демо-данных.
     */
    @Test
    void adoptedColumnShowsItsValue() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        String trip = singleValue("""
                SELECT (SELECT trip FROM dbo.v_receipts
                         WHERE trip IS NOT NULL AND trip <> ''
                         ORDER BY doc_date DESC LIMIT 1)
                """);
        if (trip == null) {
            return;  // в демо-данных нет рейса — проверять нечего
        }

        signInUiAsClient();
        page.evaluate("() => showTab('receipts')");
        waitForColumns("receipts");
        page.waitForFunction("() => document.querySelectorAll('#receiptsBody tr').length > 0");

        // Включаем «Рейс» тем же путём, каким это делает человек:
        // через сохранение выбора колонок, а не записью в таблицу.
        page.evaluate("""
                () => {
                  const cols = PORTAL.columns.all.receipts.map(c => c.columnId);
                  return fetch('/api/table-columns/receipts', {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({columns: cols})
                  }).then(() => PORTAL.columns.reload('receipts'));
                }
                """);
        page.waitForFunction("() => window.renderReceipts && (renderReceipts(), true)");
        page.waitForTimeout(300);

        List<String> head = headers("receiptsHead");
        int column = head.indexOf(label("receipts", "trip"));
        assertTrue(column >= 0, "колонка «Рейс» не появилась в заголовке: " + head);

        boolean found = Boolean.TRUE.equals(page.evaluate("""
                (arg) => Array.from(document.querySelectorAll('#receiptsBody tr')).some(tr => {
                  const td = tr.querySelectorAll('td')[arg.column];
                  return td && td.textContent.trim() === arg.trip;
                })
                """, java.util.Map.of("column", column, "trip", trip)));
        assertTrue(found, "в колонке «Рейс» нет значения «" + trip + "» — колонка пустая");
        assertNoJsErrors();
    }

    /**
     * Упаковки собирают заголовок из справочника, и колонку можно убрать.
     *
     * <p>Упаковки были последним списком портала с заголовком, вписанным
     * в разметку руками: тринадцать {@code <th>} подряд. Отсюда росло
     * три беды сразу — нет ⚙ Колонок, поклажедателю нельзя закрыть
     * лишнее поле, переименование поля под клиента сюда не доходило.
     *
     * <p>Проверка идёт до конца: мало построить заголовок из
     * справочника, надо чтобы снятая галочка действительно убирала
     * колонку. Окно, которое выглядит работающим и ничего не меняет,
     * хуже отсутствующего окна.
     */
    @Test
    void packagingHeaderComesFromTheCatalogue() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        signInUiAsClient();
        openReferences("packaging");
        waitForColumns("packaging");
        page.waitForFunction(
                "() => document.querySelectorAll('#packagingHead th').length > 0");

        List<String> expected = jdbc.sql("""
                        SELECT label FROM dbo.table_columns(
                                 :user::uuid, 'ru'::text, 'packaging'::text)
                         WHERE is_visible ORDER BY sort_order
                        """)
                .param("user", userId(USER))
                .query(String.class)
                .list();
        assertTrue(expected.size() >= 13,
                "в справочнике колонок упаковок мало строк: " + expected.size());

        List<String> head = headers("packagingHead");
        for (String label : expected) {
            assertTrue(head.stream().anyMatch(h -> h.startsWith(label)),
                    "колонки «" + label + "» нет в заголовке упаковок: " + head);
        }

        try {
            // Убираем «Штрих код упаковки» тем же путём, каким это
            // делает человек: окно, галочка, «Применить».
            page.evaluate("() => openColumnPicker('packaging')");
            page.waitForSelector("#colOverlay.active");
            assertTrue(pickerColumnIds().contains("barcode"),
                    "в окне ⚙ Колонки упаковок нет штрихкода: " + pickerColumnIds());

            page.evaluate("""
                    () => {
                      const items = Array.from(document.querySelectorAll('#colList .col-item'));
                      for (const it of items){
                        const cb = it.querySelector('input[type=checkbox]');
                        const on = cb && (cb.getAttribute('onchange') || '');
                        if (on.indexOf("'barcode'") >= 0 && cb.checked) cb.click();
                      }
                      applyColumnPicker();
                    }
                    """);
            page.waitForFunction("want => !Array.from(document.querySelectorAll('#packagingHead th'))"
                    + ".some(th => th.textContent.trim().startsWith(want))",
                    label("packaging", "barcode"));

            // Строки обязаны потерять ту же клетку: иначе данные
            // разъедутся с заголовком на одну колонку.
            int cells = ((Number) page.evaluate(
                    "() => { const tr = document.querySelector('#packagingBody tr');"
                            + " return tr ? tr.querySelectorAll('td').length : 0; }")).intValue();
            assertEquals(head.size() - 1, cells,
                    "в строке клеток не столько же, сколько колонок в заголовке");
        } finally {
            resetPackagingColumns();
        }
        assertNoJsErrors();
    }

    /* ---------------- помощники ---------------- */

    /**
     * Идентификаторы колонок, предложенных окном.
     *
     * <p>Читаются из самой разметки окна (обработчик галочки несёт
     * идентификатор), а не из глобальных переменных прототипа:
     * проверять надо то, что доехало до экрана.
     */
    private List<String> pickerColumnIds() {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) page.evaluate("""
                () => Array.from(document.querySelectorAll('#colList .col-item input[type=checkbox]'))
                        .map(cb => {
                          const m = (cb.getAttribute('onchange') || '')
                              .match(/toggleColVisibility\\('([^']+)'\\)/);
                          return m ? m[1] : '';
                        })
                        .filter(Boolean)
                """);
        return out;
    }

    private List<String> pickerLabels() {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) page.evaluate("""
                () => Array.from(document.querySelectorAll('#colList .col-item label'))
                        .map(e => e.textContent.trim())
                """);
        return out;
    }

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

    /** Возвращает выбор колонок упаковок к умолчанию — для соседних проверок. */
    private void resetPackagingColumns() {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("SELECT dbo.user_columns_reset(:id::uuid, 'packaging'::text)")
                        .param("id", userId(USER))
                        .query()
                        .listOfRows());
    }
}
