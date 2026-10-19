package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.microsoft.playwright.Download;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаблон Excel подписан так же, как экран.
 *
 * <p>Шаблон — это тот же справочник товаров, только в файле: человек
 * заполняет его и загружает обратно. Если клиент переименовал поле,
 * а в шаблоне осталась портальная подпись, он не найдёт в файле
 * колонку, которую видит на экране, — и заполнит не ту.
 *
 * <p>Проверка идёт до конца: файл действительно скачивается браузером
 * и читается Apache POI, тем же, которым портал его собирает. Смотреть
 * на ответ сервера мимо браузера было бы проще, но тогда проверка
 * не заметила бы, что кнопка перестала скачивать файл.
 */
class ExcelTemplateLabelsTest extends BrowserTestBase {

    private static final String CUSTOM = "Код клиента (шаблон)";
    private static final String SHEET = "Номенклатура";

    @Test
    void templateUsesDepositorFieldNames() {
        // Язык задаём явно: соседние проверки меняют его тому же
        // пользователю, а порядок проверок никто не обещал.
        localeOf(signedInUser(), "ru");
        rename(CUSTOM);

        signInUiAsClient();
        openSkuList();

        Download download = page.waitForDownload(
                () -> page.evaluate("() => portalDownloadSkuTemplate()"));
        List<String> headers = headers(download);

        assertFalse(headers.isEmpty(), "в шаблоне нет строки заголовков");
        assertTrue(headers.stream().anyMatch(h -> h.contains(CUSTOM)),
                "в шаблоне нет клиентского названия поля. Заголовки: " + headers);
        assertTrue(headers.stream().noneMatch(h -> h.contains(portalLabel())),
                "в шаблоне осталось портальное название поля. Заголовки: " + headers);
        assertNoJsErrors();
    }

    @AfterEach
    void dropRename() {
        rename("");
    }

    /**
     * Заголовки первой строки листа номенклатуры.
     *
     * <p>Подпись в шаблоне может нести пометку обязательности и единицу
     * измерения («Вес брутто, кг*»), поэтому сравниваем вхождением,
     * а не равенством.
     */
    private List<String> headers(Download download) {
        List<String> out = new ArrayList<>();
        try (InputStream in = Files.newInputStream(download.path());
             XSSFWorkbook book = new XSSFWorkbook(in)) {
            Sheet sheet = book.getSheet(SHEET);
            if (sheet == null) {
                sheet = book.getSheetAt(0);
            }
            Row first = sheet.getRow(sheet.getFirstRowNum());
            if (first == null) {
                return out;
            }
            for (Cell cell : first) {
                out.add(cell.toString().trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private void rename(String label) {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("SELECT dbo.admin_field_label_save(:dep::uuid, 'sku'::text,"
                                + " 'code'::text, :label::text)")
                        .param("dep", depositorId(USER, DEPOSITOR))
                        .param("label", label)
                        .query()
                        .listOfRows());
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }

    private String portalLabel() {
        return jdbc.sql("SELECT value FROM dbo.ui_texts('ru') WHERE text_key = 'col.sku.code'")
                .query(String.class)
                .single();
    }
}
