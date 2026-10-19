package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * В списке документов три номера, и каждый в своей колонке.
 *
 * <p>Проверка появилась после замечания: в колонке «Номер» стоял номер,
 * выданный порталом, а не номер документа в ERP клиента. Так было
 * сделано нарочно — «чтобы колонка не пустовала», — и вышло хуже
 * пустоты: человек видел в поле со своим названием чужое число
 * и считал его своим.
 *
 * <p>Проверка читает ЭКРАН, а не промежуточные объекты: находит колонку
 * по подписи в заголовке и смотрит, что в ней стоит. Подпись приходит
 * из справочника колонок с переводом и переименованием клиента — то
 * есть ровно то, что видит человек.
 */
class DocumentListNumbersTest extends BrowserTestBase {

    @Test
    void numberColumnShowsErpNumber() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");

        // Документ, у которого номер в ERP заполнен и отличается
        // от номера портала: только на таком видно, какой из двух
        // попал в колонку.
        // Запрос обёрнут в SELECT (…), чтобы при пустом результате
        // вернулся NULL, а не исключение: иначе проверка упала бы
        // раньше понятного сообщения.
        String row = singleValue("""
                SELECT (SELECT document_no || '|' || erp_number
                          FROM dbo.v_orders
                         WHERE erp_number IS NOT NULL AND erp_number <> ''
                           AND erp_number <> document_no
                         ORDER BY document_no
                         LIMIT 1)
                """);
        assertNotNull(row, "в демо-данных нет заказа с номером в ERP — проверять нечего");
        String portalNo = row.split("\\|")[0];
        String erpNo = row.split("\\|")[1];

        signInUiAsClient();
        page.evaluate("() => showTab('orders')");
        waitForColumns("orders");
        page.waitForFunction("() => document.querySelectorAll('#ordersBody tr').length > 0");

        List<String> head = headers("ordersHead");
        int number = head.indexOf(label("orders", "erpNo"));
        int portalId = head.indexOf(label("orders", "no"));
        assertTrue(number >= 0, "в списке нет колонки «Номер»: " + head);
        assertTrue(portalId >= 0,
                "в списке нет колонки «ID документа» — строку нельзя назвать по номеру: " + head);

        Map<String, Object> cells = cellsOfRow(portalId, portalNo, number);
        assertNotNull(cells, "в списке не нашлось строки с ID документа " + portalNo);
        assertEquals(erpNo, String.valueOf(cells.get("number")).trim(),
                "в колонке «Номер» не номер документа в ERP клиента");
        assertNotEquals(portalNo, String.valueOf(cells.get("number")).trim(),
                "в колонке «Номер» стоит номер портала — у него своя колонка");
        assertNoJsErrors();
    }

    /**
     * Ищет строку по значению в колонке «ID документа» и возвращает
     * содержимое запрошенной колонки.
     */
    private Map<String, Object> cellsOfRow(int idColumn, String idValue, int wanted) {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) page.evaluate("""
                (arg) => {
                  const rows = Array.from(document.querySelectorAll('#ordersBody tr'));
                  for (const tr of rows){
                    const tds = tr.querySelectorAll('td');
                    if (!tds[arg.idColumn]) continue;
                    if (tds[arg.idColumn].textContent.trim() === arg.idValue){
                      return {number: tds[arg.wanted] ? tds[arg.wanted].textContent : ''};
                    }
                  }
                  return null;
                }
                """, Map.of("idColumn", idColumn, "idValue", idValue, "wanted", wanted));
        return out;
    }

    /** Подпись колонки так, как её видит этот пользователь. */
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
