package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.excel.SkuTemplateService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаблон Excel для загрузки номенклатуры.
 *
 * <p>Проверка устроена так: шаблон собирается и тут же разбирается
 * обратно через POI. Иначе о нём нельзя утверждать ничего — файл либо
 * открывается в Excel как умная таблица с проверкой данных, либо нет,
 * и увидеть это можно только прочитав то, что записалось.
 */
class SkuTemplateTest extends PortalTestBase {

    @Autowired
    SkuTemplateService template;

    @Autowired
    ColumnService columns;

    @Autowired
    CurrentUser currentUser;

    @AfterEach
    void cleanUp() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        columns.resetDepositorColumns(admin, depositorId(ADMIN, DEPOSITOR), null);
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("UPDATE dbo.user_settings SET can_upload_sku_excel = true"
                                + " WHERE user_id IN (SELECT user_id FROM dbo.app_user"
                                + " WHERE login = :login)")
                        .param("login", USER)
                        .update());
        // язык пользователя возвращаем к портальному, чтобы он не утекал
        // в другие тесты
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("UPDATE dbo.app_user SET locale_id = NULL WHERE login = :login")
                        .param("login", USER)
                        .update());
    }

    /**
     * Пользователь-клиент с явно заданным русским языком.
     *
     * <p>Подписи колонок в шаблоне приходят на языке пользователя, поэтому
     * язык задаётся явно: иначе действует портальный (английский) и
     * проверки заголовков сравнивали бы русский текст с английским.
     */
    private PortalUser client() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        return user;
    }

    private XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static List<String> headers(Sheet sheet) {
        List<String> out = new ArrayList<>();
        Row row = sheet.getRow(0);
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            out.add(c == null ? null : c.getStringCellValue());
        }
        return out;
    }

    @Test
    void templateIsASmartTableWithOneHeaderRow() throws Exception {
        PortalUser user = client();
        SkuTemplateService.Template file = template.build(user);

        try (XSSFWorkbook wb = open(file.content())) {
            XSSFSheet sheet = wb.getSheet("Номенклатура");
            assertNotNull(sheet, "лист с таблицей называется «Номенклатура»");

            // умная таблица: ровно одна строка заголовка, иначе Excel
            // считает вторую строкой данных и ломает фильтры
            assertEquals(1, sheet.getTables().size(), "на листе одна умная таблица");
            var table = sheet.getTables().getFirst();
            assertEquals(0, table.getStartRowIndex(), "таблица начинается с заголовка");
            // таблица на одну строку данных: растянутая на сто строк
            // выглядит как сто пустых записей, а расширяется она сама
            assertEquals(SkuTemplateService.TEMPLATE_ROWS, table.getEndRowIndex(),
                    "в таблице одна строка под данные");

            assertNotNull(sheet.getPaneInformation(), "строка заголовка закреплена");
            assertEquals(SkuTemplateService.TEMPLATE_ROWS, sheet.getLastRowNum(),
                    "лишних пустых строк в файле нет");
        }
    }

    @Test
    void requiredColumnsComeFirstAndAreMarked() throws Exception {
        PortalUser user = client();

        try (XSSFWorkbook wb = open(template.build(user).content())) {
            List<String> head = headers(wb.getSheet("Номенклатура"));
            assertTrue(head.size() >= 45, "в шаблоне все правимые поля товара");

            // три обязательных стоят первыми и помечены звёздочкой
            for (int i = 0; i < 3; i++) {
                assertTrue(head.get(i).endsWith(" *"),
                        "обязательная колонка помечена: " + head.get(i));
            }
            assertFalse(head.get(3).endsWith(" *"), "обязательных ровно три");
            assertTrue(head.stream().anyMatch(h -> h.startsWith("Коммерческий код")));
            assertTrue(head.stream().anyMatch(h -> h.startsWith("Наименование товара")));
        }
    }

    @Test
    void headersAreUniqueBecauseExcelDemandsIt() throws Exception {
        PortalUser user = client();

        try (XSSFWorkbook wb = open(template.build(user).content())) {
            List<String> head = headers(wb.getSheet("Номенклатура"));
            Set<String> unique = new HashSet<>();
            for (String h : head) {
                assertTrue(unique.add(h.toLowerCase()),
                        "повтор заголовка «" + h + "»: Excel объявил бы файл повреждённым");
            }
        }
    }

    @Test
    void wmsItemCodeIsNotInTheTemplate() throws Exception {
        PortalUser user = client();

        try (XSSFWorkbook wb = open(template.build(user).content())) {
            List<String> head = headers(wb.getSheet("Номенклатура"));
            // код товара присваивает WMS; его пустота на портале означает
            // «WMS о товаре ещё не знает», и заполнять его из файла нельзя
            assertTrue(head.stream().noneMatch(h -> h.startsWith("Код товара")),
                    "кода товара WMS в шаблоне быть не должно: " + head);
        }
    }

    @Test
    void dictionaryColumnsHaveDropDownsAndNumbersAreChecked() throws Exception {
        PortalUser user = client();

        try (XSSFWorkbook wb = open(template.build(user).content())) {
            XSSFSheet sheet = wb.getSheet("Номенклатура");
            List<? extends DataValidation> checks = sheet.getDataValidations();
            assertFalse(checks.isEmpty(), "проверка данных расставлена");

            // у каждой проверки свой текст: стандартное «Это значение
            // не подходит» человеку ничего не объясняет
            assertTrue(checks.stream().allMatch(v -> v.getShowErrorBox()
                            && v.getErrorBoxText() != null && !v.getErrorBoxText().isBlank()),
                    "у каждой проверки есть свой текст ошибки");

            long lists = checks.stream()
                    .filter(v -> v.getValidationConstraint().getValidationType()
                            == DataValidationConstraint.ValidationType.LIST)
                    .count();
            // Четыре поля-ссылки (ед. изм., валюта, класс и группа
            // опасности; группа и вид товара стали свободным текстом
            // в V38) плюс поля «да/нет» — списков заведомо больше пяти.
            assertTrue(lists >= 6, "у полей-ссылок есть выпадающие списки, найдено: " + lists);

            // Галочка «Список допустимых значений» в Excel. Признак в формате
            // перевёрнут: showDropDown="1" значит «список не показывать»,
            // а POI переворачивает его ещё раз — поэтому проверяем сам XML.
            // Без этого проверка данных работает, но списка в ячейке нет.
            CTDataValidations raw = sheet.getCTWorksheet().getDataValidations();
            assertNotNull(raw);
            assertTrue(raw.getDataValidationList().stream().noneMatch(
                            v -> v.isSetShowDropDown() && v.getShowDropDown()),
                    "список в ячейке не подавлен — иначе галочки «Список "
                            + "допустимых значений» в Excel не будет");

            long numbers = checks.stream()
                    .filter(v -> v.getValidationConstraint().getValidationType()
                            == DataValidationConstraint.ValidationType.DECIMAL)
                    .count();
            assertTrue(numbers > 0, "числовые поля проверяются на число и неотрицательность");
        }
    }

    @Test
    void referenceValuesLiveOnAHiddenSheet() throws Exception {
        PortalUser user = client();

        try (XSSFWorkbook wb = open(template.build(user).content())) {
            Sheet dict = wb.getSheet("Справочники");
            assertNotNull(dict, "значения для списков лежат на отдельном листе");
            assertTrue(wb.isSheetHidden(wb.getSheetIndex(dict)),
                    "лист скрыт: человеку он не нужен, а Excel его читает");

            // единицы измерения — 16 кодов МКЕИ, они точно заполнены
            boolean hasValues = false;
            for (int r = 1; r <= 20 && !hasValues; r++) {
                Row row = dict.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 2; c < 10; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.STRING
                            && !cell.getStringCellValue().isBlank()) {
                        hasValues = true;
                        break;
                    }
                }
            }
            assertTrue(hasValues, "справочные значения записаны");
        }
    }

    @Test
    void fileCarriesOrgNameInNameAndCodeInMarker() throws Exception {
        PortalUser user = client();
        SkuTemplateService.Template file = template.build(user);

        // В имени файла — наименование организации: человек узнаёт
        // своего клиента в списке скачанных файлов, код ему ничего
        // не говорит. Наименование берём из базы, а не из константы:
        // демо-данные меняются, а правило — нет.
        String orgName = depositorOrgName(USER, DEPOSITOR);
        assertTrue(file.fileName().contains(orgName.replaceAll("[\\\\/:*?\"<>|]", " ")),
                "наименование организации в имени файла: " + file.fileName());
        assertTrue(file.fileName().endsWith(".xlsx"));

        try (XSSFWorkbook wb = open(file.content())) {
            // метка переживает переименование файла — на неё и опирается
            // загрузка, чтобы не принять файл, собранный для другого клиента
            assertEquals(DEPOSITOR, SkuTemplateService.markerOf(wb),
                    "скрытая метка хранит код поклажедателя");
        }
    }

    @Test
    void columnForbiddenToDepositorIsNotInTheTemplate() throws Exception {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        // запрет на экране должен значить запрет и в файле, иначе
        // колонку можно было бы увидеть, скачав шаблон
        columns.saveDepositorColumn(admin, depositorId, "sku", "brand", false, true);

        PortalUser user = client();
        try (XSSFWorkbook wb = open(template.build(user).content())) {
            List<String> head = headers(wb.getSheet("Номенклатура"));
            assertTrue(head.stream().noneMatch(h -> h.startsWith("Торговая марка")),
                    "запрещённой колонки в шаблоне нет: " + head);
        }
    }

    @Test
    void codeColumnsAreStoredAsText() throws Exception {
        PortalUser user = client();

        try (XSSFWorkbook wb = open(template.build(user).content())) {
            XSSFSheet sheet = wb.getSheet("Номенклатура");
            List<String> head = headers(sheet);
            int codeColumn = -1;
            for (int i = 0; i < head.size(); i++) {
                if (head.get(i).startsWith("Коммерческий код")) {
                    codeColumn = i;
                }
            }
            assertTrue(codeColumn >= 0);
            // иначе Excel сам превратит 0012 в 12, а 1-2 в дату,
            // и исходное значение будет потеряно безвозвратно
            assertNotNull(sheet.getColumnStyle(codeColumn));
            assertEquals("@", sheet.getColumnStyle(codeColumn).getDataFormatString(),
                    "колонка с кодом хранится текстом");
        }
    }

    @Test
    void templateRefusedWhenUploadDisabled() {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("""
                                INSERT INTO dbo.user_settings (user_id, can_upload_sku_excel)
                                SELECT user_id, false FROM dbo.app_user WHERE login = :login
                                ON CONFLICT (user_id) DO UPDATE SET can_upload_sku_excel = false
                                """)
                        .param("login", USER)
                        .update());

        signIn();
        // §6.3: право проверяется на сервере, а не скрытием кнопки.
        // Шаблон за этой же проверкой: кому загружать нельзя, тому
        // и шаблон незачем.
        expectRejection(403, () -> currentUser.requireExcelUpload("sku"));
    }
}
