package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.excel.SkuImportColumns;
import com.ahlers.portal3pl.excel.ImportLogService;
import com.ahlers.portal3pl.excel.SkuImportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Разбор и проверка файла номенклатуры (шаг 2 загрузки из Excel).
 *
 * <p>Проверяется не «разбор прошёл», а правила, на которых держится
 * экран проверки: по какому признаку строка опознаётся как новая или
 * существующая, что считается ошибкой, и что импорт <b>не может</b>
 * перезаписать уже заполненное поле.
 *
 * <p>Файлы собираются здесь же, через POI: иначе пришлось бы держать
 * в репозитории двоичные образцы, которые невозможно прочитать глазами
 * при разборе упавшего теста.
 */
class SkuImportTest extends PortalTestBase {

    private static final String PREFIX = "QA-IMP-";

    @Autowired
    SkuImportService imports;

    @Autowired
    SkuWriteService sku;

    @Autowired
    ColumnService columns;

    @Autowired
    ImportLogService importLog;

    @AfterEach
    void cleanUp() {
        inContext("DELETE FROM dbo.excel_import");
        inContext("UPDATE dbo.sku SET order_unit_id = NULL WHERE code LIKE '" + PREFIX + "%'");
        inContext("DELETE FROM dbo.sku WHERE code LIKE '" + PREFIX + "%'");
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        columns.resetDepositorColumns(admin, depositorId(ADMIN, DEPOSITOR), null);
        columns.saveFieldLabel(admin, depositorId(ADMIN, DEPOSITOR), "sku", "code", null);
    }

    /* ======================= Сборка файла ======================= */

    /** Файл с указанной шапкой и строками; метка — код текущего клиента. */
    private byte[] file(List<String> headers, List<List<String>> rows) {
        return file(headers, rows, DEPOSITOR);
    }

    private byte[] file(List<String> headers, List<List<String>> rows, String marker) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Номенклатура");
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                head.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i) != null) {
                        row.createCell(i).setCellValue(values.get(i));
                    }
                }
            }
            if (marker != null) {
                Sheet dict = wb.createSheet("Справочники");
                dict.createRow(0).createCell(0).setCellValue("depositor:" + marker);
                wb.setSheetHidden(wb.getSheetIndex(dict), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("не удалось собрать файл для теста", e);
        }
    }

    /**
     * Строка файла. {@code null} означает пустую ячейку — в файле её
     * просто не будет.
     *
     * <p>{@code Arrays.asList}, а не {@code List.of}: последний
     * отвергает null, а пустые ячейки здесь и есть то, что проверяется.
     */
    private static List<String> row(String... cells) {
        return new ArrayList<>(Arrays.asList(cells));
    }

    /**
     * Заводит позицию, под которой потом проверяется обновление.
     *
     * <p>Через этот метод, а не напрямую: запись идёт под RLS, и без
     * выставленного поклажедателя SkuWriteService отвечает 401. На этом
     * споткнулись восемь тестов сразу — они сеяли данные до входа.
     */
    private String seed(Map<String, Object> values) {
        signIn();
        return String.valueOf(sku.createSku(values).get("skuId"));
    }

    private Map<String, Object> check(byte[] content) {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        return imports.result(imports.check(user, "Номенклатура.xlsx", content));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rows");
    }

    private static Map<String, Object> rowNumber(Map<String, Object> result, int number) {
        return rowsOf(result).stream()
                .filter(r -> number == ((Number) r.get("rowNumber")).intValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет строки " + number));
    }

    /* ======================= Опознание колонок ======================= */

    @Test
    void columnsAreFoundByHeaderNotByPosition() {
        // Столбцы переставлены, часть удалена, лишний добавлен человеком:
        // загрузчик обязан это выдержать — иначе шаблон нельзя править.
        byte[] content = file(
                List.of("Примечание склада", "Базовая единица измерения *",
                        "Наименование товара *", "Коммерческий код *"),
                List.of(row("для себя", "шт", "Первая", PREFIX + "001")));

        Map<String, Object> result = check(content);
        assertEquals(1, ((Number) result.get("rowCount")).intValue());
        assertEquals(1, ((Number) result.get("toCreate")).intValue());
        assertEquals(0, ((Number) result.get("errorCount")).intValue());
        assertTrue(String.valueOf(result.get("columnsSkippedJson")).contains("Примечание склада"),
                "лишний столбец попал в нераспознанные, а не уронил файл");
    }

    @Test
    void headerIsRecognizedInAnyPortalLanguage() {
        // Человек мог скачать шаблон на английском, а грузить его будет
        // тот, у кого портал по-русски.
        byte[] content = file(
                List.of("Commercial code", "Item name", "Base unit of measure"),
                List.of(row(PREFIX + "002", "Вторая", "шт")));

        Map<String, Object> result = check(content);
        assertEquals(1, ((Number) result.get("toCreate")).intValue(),
                "английская шапка опознана: " + result.get("columnsSkippedJson"));
    }

    @Test
    void depositorRenamingOfAFieldIsRecognizedToo() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        columns.saveFieldLabel(admin, depositorId(ADMIN, DEPOSITOR),
                "sku", "code", "Артикул клиента");

        // Шаблон клиенту отдаётся с его названием поля — значит и обратно
        // файл приходит с ним же.
        byte[] content = file(
                List.of("Артикул клиента", "Наименование товара *", "Базовая единица измерения *"),
                List.of(row(PREFIX + "003", "Третья", "шт")));

        Map<String, Object> result = check(content);
        assertEquals(1, ((Number) result.get("toCreate")).intValue(),
                "переименованное поле опознано: " + result.get("columnsSkippedJson"));
    }

    @Test
    void headerIsMatchedIgnoringCaseSpacesAndTheRequiredMark() {
        byte[] content = file(
                List.of("  КОММЕРЧЕСКИЙ   КОД  ", "наименование товара", "Базовая Единица Измерения *"),
                List.of(row(PREFIX + "004", "Четвёртая", "шт")));

        Map<String, Object> result = check(content);
        assertEquals(1, ((Number) result.get("toCreate")).intValue(),
                "регистр, лишние пробелы и звёздочка не мешают: "
                        + result.get("columnsSkippedJson"));
    }

    @Test
    void fileWithoutTheKeyColumnIsRefusedWithTheHeadersListed() {
        // Без коммерческого кода строку не с чем сопоставить, разбор
        // бессмысленен. В отказе перечисляем найденные заголовки —
        // иначе не догадаться, что столбец переименован.
        byte[] content = file(
                List.of("Наименование товара *", "Базовая единица измерения *"),
                List.of(row("Без кода", "шт")));

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(400, () -> imports.check(user, "Номенклатура.xlsx", content));
    }

    /* ======================= Чужой файл ======================= */

    @Test
    void fileBuiltForAnotherDepositorIsRefused() {
        // Главный опасный исход: шаблон скачали для одного клиента,
        // переключились на другого и загрузили. Товары ушли бы не тому.
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                List.of(row(PREFIX + "005", "Чужая", "шт")),
                OTHER_DEPOSITOR);

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(409, () -> imports.check(user, "Номенклатура.xlsx", content));
    }

    @Test
    void fileWithoutAMarkerIsAccepted() {
        // Человек мог собрать файл сам. Отсутствие признака ничего
        // не доказывает — в отличие от найденного несовпадения.
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                List.of(row(PREFIX + "006", "Своя", "шт")),
                null);

        Map<String, Object> result = check(content);
        assertEquals(1, ((Number) result.get("toCreate")).intValue());
    }

    /* ======================= Класс строки ======================= */

    @Test
    void rowIsNewOrExistingOrUnidentifiable() {
        seed(Map.of("code", PREFIX + "OLD", "name", "Уже есть", "uom_id", 1));

        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Вес брутто, кг"),
                List.of(
                        row(PREFIX + "NEW", "Новая", "шт", "1,5"),
                        row(PREFIX + "old", null, "шт", "2,5"),   // регистр не важен
                        row("", "Без кода", "шт", "1")));

        Map<String, Object> result = check(content);
        assertEquals("new", rowNumber(result, 2).get("rowClass"));
        assertEquals("existing", rowNumber(result, 3).get("rowClass"),
                "код сопоставляется без учёта регистра");
        // Код пуст — позицию определить нельзя, ни к новым, ни
        // к существующим такую строку отнести не получится.
        assertEquals("unknown", rowNumber(result, 4).get("rowClass"));
        assertEquals(false, rowNumber(result, 4).get("isValid"));
    }

    @Test
    void classAndValidityAreIndependent() {
        // На этом держатся галочки экрана: новая позиция может быть
        // с ошибкой, а существующая — без.
        seed(Map.of("code", PREFIX + "GOOD", "name", "Есть", "uom_id", 1));

        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Вес брутто, кг"),
                List.of(
                        row(PREFIX + "BAD", null, "шт", "1"),      // новая, без наименования
                        row(PREFIX + "GOOD", null, null, "7,5")));  // существующая, годная

        Map<String, Object> result = check(content);
        Map<String, Object> bad = rowNumber(result, 2);
        assertEquals("new", bad.get("rowClass"));
        assertEquals(false, bad.get("isValid"));

        Map<String, Object> good = rowNumber(result, 3);
        assertEquals("existing", good.get("rowClass"));
        assertEquals(true, good.get("isValid"));

        assertEquals(0, ((Number) result.get("toCreate")).intValue());
        assertEquals(1, ((Number) result.get("toUpdate")).intValue());
        assertEquals(1, ((Number) result.get("errorCount")).intValue());
    }

    @Test
    void requiredFieldsAreDemandedOnlyFromNewItems() {
        // У существующей позиции наименование и единица уже заполнены
        // в портале: требовать их в файле значило бы заставлять
        // переписывать то, что и так есть.
        seed(Map.of("code", PREFIX + "HAS", "name", "Полная", "uom_id", 1));

        byte[] content = file(
                List.of("Коммерческий код *", "Вес брутто, кг"),
                List.of(row(PREFIX + "HAS", "4,25")));

        Map<String, Object> result = check(content);
        assertEquals(true, rowNumber(result, 2).get("isValid"));
        assertEquals(1, ((Number) result.get("toUpdate")).intValue());
    }

    /* ======================= Что считается ошибкой ======================= */

    @Test
    void existingRowThatChangesNothingIsAnError() {
        // Решение заказчика: строка, которая ничего не меняет, —
        // ошибка. Человек ждал от неё действия.
        seed(Map.of("code", PREFIX + "SAME", "name", "Та же", "uom_id", 1,
                "weight_gross", 2));

        byte[] content = file(
                List.of("Коммерческий код *", "Вес брутто, кг"),
                List.of(row(PREFIX + "SAME", "2")));

        Map<String, Object> result = check(content);
        Map<String, Object> row = rowNumber(result, 2);
        assertEquals("existing", row.get("rowClass"));
        assertEquals(false, row.get("isValid"));
        assertTrue(String.valueOf(row.get("issuesJson")).contains("nothing"),
                "причина названа: " + row.get("issuesJson"));
        assertEquals(0, ((Number) result.get("toUpdate")).intValue());
    }

    @Test
    void importCannotOverwriteWhatIsAlreadyFilled() {
        // Свойство, которое стоит назвать прямо: заполнить пустое
        // импорт может, изменить заполненное — нет. Ни при какой
        // ошибке в файле и ни при какой невнимательности.
        String skuId = seed(Map.of(
                "code", PREFIX + "KEEP", "name", "Не менять", "uom_id", 1,
                "weight_gross", 2.5, "color", "красный"));
        assertNotNull(skuId);

        byte[] content = file(
                List.of("Коммерческий код *", "Вес брутто, кг", "Цвет", "Размер"),
                List.of(row(PREFIX + "KEEP", "9,9", "красный", "XL")));

        Map<String, Object> result = check(content);
        Map<String, Object> row = rowNumber(result, 2);
        // Вес отличается — вся строка не обновляется, хотя «Размер» пуст
        // и его можно было бы заполнить.
        assertEquals(false, row.get("isValid"));
        assertTrue(String.valueOf(row.get("issuesJson")).contains("conflict"));
        assertTrue(String.valueOf(row.get("issuesJson")).contains("2.5"),
                "в замечании видно, что уже в портале: " + row.get("issuesJson"));
    }

    @Test
    void onlyEmptyFieldsAreMarkedForFilling() {
        seed(Map.of("code", PREFIX + "FILL", "name", "Заполнить", "uom_id", 1,
                "color", "синий"));

        byte[] content = file(
                List.of("Коммерческий код *", "Цвет", "Размер", "Вес брутто, кг"),
                List.of(row(PREFIX + "FILL", "синий", "M", "3")));

        Map<String, Object> result = check(content);
        Map<String, Object> row = rowNumber(result, 2);
        assertEquals(true, row.get("isValid"), "замечаний нет: " + row.get("issuesJson"));
        String fill = String.valueOf(row.get("fillFieldsJson"));
        assertTrue(fill.contains("size") && fill.contains("weight_gross"), fill);
        assertFalse(fill.contains("color"),
                "цвет совпадает и уже заполнен — заполнять нечего: " + fill);
    }

    @Test
    void numbersCompareAsNumbersNotAsText() {
        // Иначе «2» в файле и «2.00» в портале давали бы расхождение
        // на пустом месте, и не обновлялась бы ни одна строка.
        seed(Map.of("code", PREFIX + "NUM", "name", "Числа", "uom_id", 1,
                "weight_gross", 2));

        byte[] content = file(
                List.of("Коммерческий код *", "Вес брутто, кг", "Цвет"),
                List.of(row(PREFIX + "NUM", "2,00", "зелёный")));

        Map<String, Object> result = check(content);
        Map<String, Object> row = rowNumber(result, 2);
        assertEquals(true, row.get("isValid"),
                "2 и 2,00 — одно число: " + row.get("issuesJson"));
    }

    @Test
    void unparsableValuesAreReportedPerCell() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Вес брутто, кг", "Хрупкий товар"),
                List.of(
                        row(PREFIX + "V1", "Не число", "шт", "много", "да"),
                        row(PREFIX + "V2", "Отрицательное", "шт", "-5", "да"),
                        row(PREFIX + "V3", "Не да и не нет", "шт", "1", "наверное"),
                        row(PREFIX + "V4", "Нет такой единицы", "штук", "1", "да")));

        Map<String, Object> result = check(content);
        assertEquals(4, ((Number) result.get("errorCount")).intValue());
        assertTrue(String.valueOf(rowNumber(result, 2).get("issuesJson")).contains("weightGross"));
        assertTrue(String.valueOf(rowNumber(result, 3).get("issuesJson"))
                .contains("Отрицательное"));
        assertTrue(String.valueOf(rowNumber(result, 4).get("issuesJson")).contains("isFragile"));
        // Подсказка про похожее значение: без неё человек видит
        // «не найдено» и не понимает, что написал «штук» вместо «шт».
        assertTrue(String.valueOf(rowNumber(result, 5).get("issuesJson")).contains("шт"),
                "подсказка о похожем значении: " + rowNumber(result, 5).get("issuesJson"));
    }

    @Test
    void duplicateCodeInsideTheFilePointsAtTheFirstRow() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                List.of(
                        row(PREFIX + "DUP", "Первая", "шт"),
                        row(PREFIX + "dup", "Вторая", "шт")));

        Map<String, Object> result = check(content);
        assertEquals(true, rowNumber(result, 2).get("isValid"));
        Map<String, Object> second = rowNumber(result, 3);
        assertEquals(false, second.get("isValid"));
        assertTrue(String.valueOf(second.get("issuesJson")).contains("строке 2"),
                "сказано, где первое вхождение: " + second.get("issuesJson"));
    }

    @Test
    void archivedItemIsNotRevivedByImport() {
        String skuId = seed(Map.of(
                "code", PREFIX + "ARCH", "name", "В архиве", "uom_id", 1));
        sku.setSkuActive(skuId, false);

        byte[] content = file(
                List.of("Коммерческий код *", "Вес брутто, кг"),
                List.of(row(PREFIX + "ARCH", "1,5")));

        Map<String, Object> result = check(content);
        Map<String, Object> row = rowNumber(result, 2);
        assertEquals(false, row.get("isValid"));
        assertTrue(String.valueOf(row.get("issuesJson")).contains("архив"),
                "импорт не оживляет то, что убрали руками: " + row.get("issuesJson"));
    }

    /* ======================= Границы и права ======================= */

    @Test
    void columnForbiddenToDepositorIsIgnoredInTheFile() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        columns.saveDepositorColumn(admin, depositorId(ADMIN, DEPOSITOR),
                "sku", "brand", false, true);

        // Поля нет в шаблоне этого клиента — значит и в файле оно
        // ничего не значит. Иначе запрет на экране можно было бы обойти
        // загрузкой.
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Торговая марка"),
                List.of(row(PREFIX + "BRAND", "С маркой", "шт", "Акме")));

        Map<String, Object> result = check(content);
        assertTrue(String.valueOf(result.get("columnsSkippedJson")).contains("Торговая марка"),
                "запрещённая колонка не распознана: " + result.get("columnsSkippedJson"));
        assertFalse(String.valueOf(rowNumber(result, 2).get("cellValuesJson")).contains("brand"),
                "и значение из неё не взято");
    }

    @Test
    void moreThanHundredRowsIsRefused() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= SkuImportService.MAX_ROWS + 1; i++) {
            rows.add(row(PREFIX + i, "Позиция " + i, "шт"));
        }
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                rows);

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(400, () -> imports.check(user, "Номенклатура.xlsx", content));
    }

    @Test
    void notAnExcelFileIsRefusedByItsFirstBytes() {
        // Переименованный .doc иначе дошёл бы до разбора и упал там
        // невнятной ошибкой.
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(400, () -> imports.check(user, "Номенклатура.xlsx",
                "это просто текст, а не книга Excel".getBytes()));
    }

    @Test
    void emptyFileIsRefused() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                List.of());

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(400, () -> imports.check(user, "Номенклатура.xlsx", content));
    }

    @Test
    void importIsRefusedWhenUploadIsDisabled() {
        inContext("INSERT INTO dbo.user_settings (user_id, can_upload_sku_excel)"
                + " SELECT user_id, false FROM dbo.app_user WHERE login = '" + USER + "'"
                + " ON CONFLICT (user_id) DO UPDATE SET can_upload_sku_excel = false");
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                List.of(row(PREFIX + "NO", "Нельзя", "шт")));

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        try {
            // §6.3: право проверяется на сервере, а не скрытием кнопки
            expectRejection(403, () -> imports.check(user, "Номенклатура.xlsx", content));
        } finally {
            inContext("UPDATE dbo.user_settings SET can_upload_sku_excel = true"
                    + " WHERE user_id IN (SELECT user_id FROM dbo.app_user"
                    + " WHERE login = '" + USER + "')");
        }
    }

    @Test
    void anotherDepositorCannotSeeTheCheckResult() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *", "Базовая единица измерения *"),
                List.of(row(PREFIX + "MINE", "Моя", "шт")));

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        String importId = imports.check(user, "Номенклатура.xlsx", content);

        // Разобранные строки — такие же данные клиента, как и товары:
        // под RLS, и чужому они не видны даже по идентификатору.
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        expectRejection(404, () -> imports.result(importId));
    }

    /* ======================= Нормализация подписей ======================= */

    @Test
    void labelNormalizationIsTheSameOnBothSides() {
        // Если нормализация разойдётся между заголовком файла и названием
        // поля из базы, не совпадёт ничего, а причину будет не найти.
        assertEquals(SkuImportColumns.normalize("Коммерческий код *"),
                SkuImportColumns.normalize("  коммерческий   КОД  "));
        assertEquals(SkuImportColumns.normalize("Длина, см (2)"),
                SkuImportColumns.normalize("Длина, см"));
        // Единица измерения в подписи отбрасывается: в справочнике колонок
        // она проставлена непоследовательно («Вес брутто» без неё,
        // «Срок хранения, дней» с ней), а в карточке товара — иначе,
        // чем в справочнике. Человек напишет так, как видел на экране.
        assertEquals(SkuImportColumns.normalize("Вес брутто"),
                SkuImportColumns.normalize("Вес брутто, кг"));
        assertEquals(SkuImportColumns.normalize("Срок хранения"),
                SkuImportColumns.normalize("Срок хранения, дней"));
        assertEquals(SkuImportColumns.normalize("Объём"),
                SkuImportColumns.normalize("Объём, м³"));
        // А вот осмысленный хвост не единица и не отбрасывается
        assertNotEquals(SkuImportColumns.normalize("Группа товара"),
                SkuImportColumns.normalize("Группа"));
        assertEquals(SkuImportColumns.normalize("Приёмка"),
                SkuImportColumns.normalize("приемка"));
    }

    /* ======================= Показ значений (V38) ======================= */

    @Test
    void referenceValuesAreKeptAsIdAndShownAsName() {
        // В cell_values лежит то, что уйдёт в базу (идентификатор
        // единицы), в cell_display — то, что человек увидит на экране.
        // Если бы сервер отдавал только одно из двух, экран показывал бы
        // «1» вместо «шт» либо применение записывало бы в uom_id слово.
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Хрупкий товар"),
                List.of(row(PREFIX + "040", "Со единицей", "шт", "да")));

        Map<String, Object> result = check(content);
        Map<String, Object> row = rowNumber(result, 2);
        String values = String.valueOf(row.get("cellValuesJson"));
        String display = String.valueOf(row.get("cellDisplayJson"));

        assertTrue(values.contains("\"uom_id\""), "значение единицы записано: " + values);
        assertFalse(values.contains("шт"),
                "в значении должен быть идентификатор, а не название: " + values);
        assertTrue(display.contains("шт"), "в показе должно быть название единицы: " + display);
        assertTrue(display.contains("Да"),
                "логический признак показывается словом, а не true: " + display);
    }

    @Test
    void itemGroupAndTypeAreFreeText() {
        // Справочники групп и типов удалены (V38): значение берётся из
        // файла как есть, и незнакомое название больше не ошибка.
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Группа товара", "Тип товара"),
                List.of(row(PREFIX + "041", "С группой", "шт",
                        "Группа, которой нет в справочниках", "Тип свой")));

        Map<String, Object> result = check(content);
        assertEquals(0, ((Number) result.get("errorCount")).intValue(),
                "свободный текст группы не ошибка: " + rowNumber(result, 2).get("issuesJson"));
        String values = String.valueOf(rowNumber(result, 2).get("cellValuesJson"));
        assertTrue(values.contains("item_group"), "группа записана строкой: " + values);
        assertTrue(values.contains("Группа, которой нет в справочниках"), values);
        assertTrue(values.contains("Тип свой"), values);
        // Показывать отдельно нечего: значение и есть название
        assertFalse(String.valueOf(rowNumber(result, 2).get("cellDisplayJson"))
                .contains("item_group"), "для текста отдельный показ не нужен");
    }

    @Test
    void conflictOnAReferenceFieldIsReportedByName() {
        // Расхождение по единице измерения человек должен прочитать как
        // «шт против кг», а не «1 против 3».
        seed(Map.of("code", PREFIX + "042", "name", "Уже с единицей", "uom_id", unitId("шт")));

        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *"),
                List.of(row(PREFIX + "042", "Уже с единицей", "кг")));

        Map<String, Object> result = check(content);
        String issues = String.valueOf(rowNumber(result, 2).get("issuesJson"));
        assertTrue(issues.contains("кг") && issues.contains("шт"),
                "в замечании названия единиц, а не идентификаторы: " + issues);
    }

    /** Идентификатор единицы измерения по обозначению. */
    private int unitId(String symbol) {
        return Integer.parseInt(singleValue(
                "SELECT id::text FROM dbo.enum_uom WHERE symbol = '" + symbol + "'"));
    }

    /** Применение: создание или обновление, как это делает экран. */
    private Map<String, Object> apply(String importId, boolean creating) {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        return imports.apply(user, importId, creating);
    }

    /** Разбор файла без чтения результата — когда нужен сам идентификатор. */
    private String checkOnly(byte[] content) {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        return imports.check(user, "Номенклатура.xlsx", content);
    }

    /**
     * Одно поле записанной позиции.
     *
     * <p>Через {@code singleValue}, а не обычным запросом: таблица под
     * RLS, и без выставленного поклажедателя база честно возвращает
     * ноль строк — без ошибки, просто пусто. На этом сначала и упали
     * три этих теста.
     */
    private String skuField(String code, String column) {
        return singleValue("SELECT " + column + "::text FROM dbo.sku WHERE code = '"
                + code + "'");
    }

    /* ======================= Применение (шаг 3) ======================= */

    @Test
    void creatingWritesOnlyNewRows() {
        seed(Map.of("code", PREFIX + "050", "name", "Уже есть", "uom_id", 1));

        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *", "Цвет"),
                List.of(row(PREFIX + "051", "Новая первая", "шт", "красный"),
                        row(PREFIX + "052", "Новая вторая", "шт", null),
                        row(PREFIX + "050", "Уже есть", "шт", "синий")));

        String importId = checkOnly(content);
        Map<String, Object> after = apply(importId, true);

        assertEquals(2, ((Number) after.get("applied")).intValue(),
                "создались только новые позиции");
        assertEquals(0, ((Number) after.get("failed")).intValue());
        assertEquals(0, ((Number) after.get("toCreate")).intValue(),
                "создавать больше нечего");

        // Позиции действительно в справочнике, и значения на месте
        assertEquals("Новая первая", skuField(PREFIX + "051", "name"));
        assertEquals("красный", skuField(PREFIX + "051", "color"));
        // Существующая строка нажатием «Создать» не тронута
        assertEquals(1, ((Number) after.get("toUpdate")).intValue());
    }

    @Test
    void updatingFillsOnlyEmptyFields() {
        seed(Map.of("code", PREFIX + "053", "name", "Пополнить", "uom_id", 1,
                "color", "синий"));

        byte[] content = file(
                List.of("Коммерческий код *", "Цвет", "Размер", "Торговая марка"),
                List.of(row(PREFIX + "053", "синий", "XL", "Рога")));

        Map<String, Object> after = apply(checkOnly(content), false);
        assertEquals(1, ((Number) after.get("applied")).intValue());

        assertEquals("XL", skuField(PREFIX + "053", "size"), "пустое поле заполнилось");
        assertEquals("Рога", skuField(PREFIX + "053", "brand"));
        assertEquals("синий", skuField(PREFIX + "053", "color"),
                "совпадающее поле не изменилось");
    }

    @Test
    void appliedRowsAreNotWrittenTwice() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *"),
                List.of(row(PREFIX + "054", "Единожды", "шт")));

        String importId = checkOnly(content);
        assertEquals(1, ((Number) apply(importId, true).get("applied")).intValue());

        // Второе нажатие — не ошибка и не второй товар: применять нечего
        Map<String, Object> again = apply(importId, true);
        assertEquals(0, ((Number) again.get("applied")).intValue());
        assertEquals(1, count("SELECT count(*)::text FROM dbo.sku WHERE code = '"
                + PREFIX + "054'"), "второй позиции не появилось");
    }

    @Test
    void rowThatCannotBeWrittenBecomesAnErrorAndTheRestAreKept() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *"),
                List.of(row(PREFIX + "055", "Первая", "шт"),
                        row(PREFIX + "056", "Вторая", "шт")));

        String importId = checkOnly(content);
        // Пока человек смотрел на экран проверки, вторую позицию
        // завели руками: её строка записаться не сможет.
        seed(Map.of("code", PREFIX + "056", "name", "Руками", "uom_id", 1));

        Map<String, Object> after = apply(importId, true);
        // Решение заказчика: годные записываются, по сбойной — замечание
        assertEquals(1, ((Number) after.get("applied")).intValue());
        assertEquals(1, ((Number) after.get("failed")).intValue());
        assertEquals("Первая", skuField(PREFIX + "055", "name"));
        assertEquals("Руками", skuField(PREFIX + "056", "name"),
                "заведённую руками позицию импорт не перезаписал");

        Map<String, Object> failedRow = rowNumber(after, 3);
        assertEquals(false, failedRow.get("isValid"), "сбойная строка стала ошибкой");
        assertTrue(String.valueOf(failedRow.get("issuesJson")).contains("apply"),
                "у неё замечание вида apply: " + failedRow.get("issuesJson"));
    }

    @Test
    void anotherUserCannotApplySomeoneElsesImport() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *"),
                List.of(row(PREFIX + "057", "Чужая", "шт")));
        String importId = checkOnly(content);

        // Окно могло остаться открытым, пока за тем же компьютером
        // вошёл другой человек — тогда в аудите товара оказался бы
        // не тот автор.
        //
        // Код отказа не фиксируем: другому пользователю загрузка может
        // быть не разрешена вовсе (403 по can_upload_sku_excel), а если
        // разрешена — отказ будет по несовпадению автора (409). Важно
        // не то, какой из двух запретов сработал, а что товар не создан.
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> imports.apply(admin, importId, true));
        assertEquals(0, count("SELECT count(*)::text FROM dbo.sku WHERE code = '"
                + PREFIX + "057'"));
    }

    @Test
    void closingTheWindowKeepsTheLogAndDropsTheRows() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *"),
                List.of(row(PREFIX + "058", "Закрыть", "шт"),
                        row(PREFIX + "059", "", "шт")));
        String importId = checkOnly(content);

        signIn();
        imports.cancel(importId);

        // Разобранные строки — промежуточные данные, их больше нет
        assertEquals(0, count("SELECT count(*)::text FROM dbo.excel_import_row"
                + " WHERE import_id = '" + importId + "'::uuid"));
        // А запись о загрузке и её замечания — это журнал, он остаётся
        assertEquals(1, count("SELECT count(*)::text FROM dbo.excel_import"
                + " WHERE import_id = '" + importId + "'::uuid"));
        assertTrue(count("SELECT count(*)::text FROM dbo.excel_import_issue"
                + " WHERE import_id = '" + importId + "'::uuid") > 0,
                "замечания остались для журнала");
        assertEquals("cancelled", singleValue("SELECT status FROM dbo.excel_import"
                + " WHERE import_id = '" + importId + "'::uuid"));
    }

    @Test
    void importAppearsInTheAdministratorLog() {
        byte[] content = file(
                List.of("Коммерческий код *", "Наименование товара *",
                        "Базовая единица измерения *"),
                List.of(row(PREFIX + "060", "В журнал", "шт"),
                        row(PREFIX + "061", "", "шт")));
        apply(checkOnly(content), true);

        PortalUser admin = portalAdmin();
        Map<String, Object> log = importLog.log(admin, null);
        assertFalse(((List<?>) log.get("imports")).isEmpty(), "загрузка видна в журнале");
        assertFalse(((List<?>) log.get("issues")).isEmpty(), "замечания видны в журнале");

        // Поклажедателю журнал не положен: он общий по всем клиентам
        signIn();
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> importLog.log(client, null));
    }

    /** Число одним запросом под контекстом поклажедателя. */
    private long count(String sql) {
        return Long.parseLong(singleValue(sql));
    }
}
