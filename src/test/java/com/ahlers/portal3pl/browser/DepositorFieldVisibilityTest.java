package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Поле, скрытое от поклажедателя, не видно нигде.
 *
 * <p>Запрет живёт на сервере: {@code table_columns} просто не отдаёт
 * запрещённую колонку. Поэтому проверять надо не только таблицу,
 * но и окно «⚙ Колонки»: там человек выбирает себе колонки сам,
 * и показать ему в этом списке запрещённое — значит пообещать то,
 * чего портал не даст.
 *
 * <p>Колонка выбирается на ходу: любая незапертая из тех, что портал
 * показывает по умолчанию. Вписать её в проверку нельзя — справочник
 * колонок меняется, а смысл проверки от конкретной колонки не зависит.
 */
class DepositorFieldVisibilityTest extends BrowserTestBase {

    private String columnId;
    private boolean portalDefault;
    private String label;

    @Test
    void hiddenColumnIsOfferedNowhere() {
        // Подпись колонки сверяется с русским текстом из каталога,
        // поэтому язык пользователя задаём явно: соседние проверки
        // переключают его, а порядок проверок не определён.
        localeOf(signedInUser(), "ru");
        chooseColumn();
        setAvailable(false, false);

        signInUiAsClient();
        openSkuList();

        List<String> headers = headers("skuHead");
        assertFalse(headers.contains(label),
                "скрытая от клиента колонка «" + label + "» осталась в списке товаров: " + headers);

        boolean sentByServer = Boolean.TRUE.equals(page.evaluate(
                "id => (PORTAL.columns.all.sku || []).some(c => c.columnId === id)", columnId));
        assertFalse(sentByServer,
                "сервер всё ещё отдаёт запрещённую колонку " + columnId);

        page.evaluate("() => openColumnPicker('sku')");
        page.waitForSelector("#colList .col-item");
        String picker = (String) page.evaluate("() => document.getElementById('colList').innerText");
        assertFalse(picker.contains(label),
                "скрытая колонка «" + label + "» предлагается в окне «Колонки»");

        // И наоборот: окно не пустое, иначе проверка «не содержит» ничего не значит
        assertFalse(picker.isEmpty(), "окно выбора колонок пусто — проверка смотрит не туда");
        assertNoJsErrors();
    }

    /** Возвращаем колонку клиенту: проверка не должна менять настройки насовсем. */
    @AfterEach
    void restoreColumn() {
        if (columnId != null) {
            setAvailable(true, portalDefault);
        }
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }

    /** Любая незапертая колонка товара, показываемая по умолчанию. */
    private void chooseColumn() {
        Map<String, Object> row = new TransactionTemplate(txManager).execute(tx ->
                jdbc.sql("""
                                SELECT c.column_id, c.is_default, t.value AS label
                                  FROM dbo.table_column c
                                  JOIN dbo.ui_texts('ru') t ON t.text_key = c.text_key
                                 WHERE c.table_key = 'sku' AND NOT c.is_locked AND c.is_default
                                 ORDER BY c.sort_order
                                 LIMIT 1
                                """)
                        .query()
                        .listOfRows()
                        .stream()
                        .findFirst()
                        .orElse(null));
        if (row == null) {
            Assumptions.abort("в справочнике колонок нет незапертой колонки товара");
        }
        columnId = String.valueOf(row.get("column_id"));
        portalDefault = Boolean.TRUE.equals(row.get("is_default"));
        label = String.valueOf(row.get("label"));
    }

    private void setAvailable(boolean available, boolean shownByDefault) {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("SELECT dbo.admin_depositor_column_save(:dep::uuid, 'sku'::text,"
                                + " :col::text, :available::boolean, :shown::boolean)")
                        .param("dep", depositorId(USER, DEPOSITOR))
                        .param("col", columnId)
                        .param("available", available)
                        .param("shown", shownByDefault)
                        .query()
                        .listOfRows());
    }
}
