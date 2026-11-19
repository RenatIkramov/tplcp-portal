package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.excel.PackagingImportService;
import com.ahlers.portal3pl.excel.PackagingTemplateService;
import com.ahlers.portal3pl.excel.SkuImportService;
import com.ahlers.portal3pl.excel.SkuTemplateService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Загрузка упаковок из Excel.
 *
 * <p>Механизм общий с номенклатурой, и его общие правила проверяет
 * {@link SkuImportTest}. Здесь — то, чем упаковки отличаются, и то, на
 * чём легко ошибиться молча:
 * <ul>
 *   <li>упаковка опознаётся ПАРОЙ «товар + код упаковки», а не одним
 *       кодом: у разных товаров бывает одинаковый «BOX»;</li>
 *   <li>товар импорт не создаёт и в архиве не трогает;</li>
 *   <li>единица заказа подчиняется общему правилу — заполнить пустое
 *       можно, поменять выбранное нельзя;</li>
 *   <li>шаблон и загрузчик понимают друг друга: файл, собранный из
 *       нашего же шаблона, загружается без нераспознанных столбцов.</li>
 * </ul>
 *
 * <p>Товары для проверок заводятся здесь же, с приставкой {@link #PREFIX},
 * и после каждой проверки удаляются: опираться на демо-данные значило бы
 * проверять состав базы, а не поведение портала.
 */
class PackagingImportTest extends PortalTestBase {

    private static final String PREFIX = "QA-PKG-";

    private static final String H_SKU = "Коммерческий код товара *";
    private static final String H_CODE = "Код упаковки *";
    private static final String H_COEF = "Коэффициент *";
    private static final String H_TYPE = "Тип упаковки";
    private static final String H_BARCODE = "Штрих код упаковки";
    private static final String H_LENGTH = "Длина, см";
    private static final String H_WIDTH = "Ширина, см";
    private static final String H_HEIGHT = "Высота, см";
    private static final String H_VOLUME = "Объём, м³";
    private static final String H_ORDER = "Единица заказа";

    @Autowired
    PackagingImportService imports;

    @Autowired
    PackagingTemplateService template;

    @Autowired
    SkuImportService skuImports;

    @Autowired
    SkuWriteService sku;

    @AfterEach
    void cleanUp() {
        inContext("DELETE FROM dbo.excel_import");
        inContext("UPDATE dbo.sku SET order_unit_id = NULL WHERE code LIKE '" + PREFIX + "%'");
        inContext("DELETE FROM dbo.product_packaging WHERE sku_id IN"
                + " (SELECT sku_id FROM dbo.sku WHERE code LIKE '" + PREFIX + "%')");
        inContext("DELETE FROM dbo.sku WHERE code LIKE '" + PREFIX + "%'");
    }

    /* ======================= Подготовка ======================= */

    /** Заводит товар — под RLS, поэтому через вход. */
    private String product(String code) {
        signIn();
        return String.valueOf(sku.createSku(Map.of("code", PREFIX + code,
                "name", "Проверка упаковок " + code, "uom_id", 1)).get("skuId"));
    }

    /** Заводит упаковку товару и возвращает её идентификатор. */
    private String packaging(String skuId, String code, Map<String, Object> more) {
        signIn();
        Map<String, Object> values = new java.util.LinkedHashMap<>(more);
        values.put("packaging_code", code);
        values.putIfAbsent("coefficient", 12);
        sku.createPackaging(skuId, values);
        return singleValue("SELECT packaging_id::text FROM dbo.product_packaging"
                + " WHERE sku_id = '" + skuId + "' AND packaging_code = '" + code + "'");
    }

    private void makeOrderUnit(String packagingId) {
        signIn();
        sku.setDefaultPackaging(packagingId);
    }

    private byte[] file(List<String> headers, List<List<String>> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Упаковки");
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
            Sheet dict = wb.createSheet("Справочники");
            dict.createRow(0).createCell(0).setCellValue("depositor:" + DEPOSITOR);
            wb.setSheetHidden(wb.getSheetIndex(dict), true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("не удалось собрать файл для теста", e);
        }
    }

    /** {@code Arrays.asList}: пустые ячейки здесь и есть то, что проверяется. */
    private static List<String> row(String... cells) {
        return new ArrayList<>(Arrays.asList(cells));
    }

    private PortalUser user() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        return user;
    }

    private String checkOnly(byte[] content) {
        return imports.check(user(), "Упаковки.xlsx", content);
    }

    private Map<String, Object> check(byte[] content) {
        String id = checkOnly(content);
        return imports.result(id);
    }

    private Map<String, Object> apply(String importId, boolean creating) {
        return imports.apply(user(), importId, creating);
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

    private static String issues(Map<String, Object> row) {
        return String.valueOf(row.get("issuesJson"));
    }

    private static boolean valid(Map<String, Object> row) {
        return Boolean.TRUE.equals(row.get("isValid"));
    }

    /** Поле упаковки текстом; пустое — пустая строка, а не отказ выборки. */
    private String packagingField(String packagingId, String column) {
        return singleValue("SELECT COALESCE(" + column + "::text, '') FROM dbo.product_packaging"
                + " WHERE packaging_id = '" + packagingId + "'");
    }

    private String orderUnitOf(String skuId) {
        return singleValue("SELECT COALESCE(order_unit_id::text, '') FROM dbo.sku"
                + " WHERE sku_id = '" + skuId + "'");
    }

    /* ======================= Опознание строки ======================= */

    /**
     * Ключ — пара, а не код упаковки: у разных товаров одинаковый «BOX»
     * законен, и оба — новые упаковки.
     */
    @Test
    void rowIsIdentifiedByProductAndPackagingCodeTogether() {
        String a = product("A");
        product("B");
        packaging(a, "BOX", Map.of());

        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_COEF),
                List.of(row(PREFIX + "A", "PLT", "100"),
                        row(PREFIX + "B", "BOX", "12"),
                        row(PREFIX + "A", "BOX", "12"))));

        assertEquals("new", rowNumber(result, 2).get("rowClass"), "у товара A нет PLT");
        assertTrue(valid(rowNumber(result, 2)), issues(rowNumber(result, 2)));
        assertEquals("new", rowNumber(result, 3).get("rowClass"),
                "BOX у товара B — своя упаковка, хотя у A такой код уже есть");
        assertTrue(valid(rowNumber(result, 3)), issues(rowNumber(result, 3)));
        assertEquals("existing", rowNumber(result, 4).get("rowClass"));
        assertFalse(valid(rowNumber(result, 4)),
                "существующая строка, которая ничего не меняет, — ошибка");
        assertTrue(issues(rowNumber(result, 4)).contains("nothing"), issues(rowNumber(result, 4)));
    }

    /** Товары импорт упаковок не создаёт: опечатка завела бы товар без имени. */
    @Test
    void unknownProductIsAnErrorAndNothingIsCreated() {
        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_COEF),
                List.of(row(PREFIX + "NOPE", "BOX", "12"))));

        Map<String, Object> r = rowNumber(result, 2);
        assertEquals("unknown", r.get("rowClass"));
        assertFalse(valid(r));
        assertTrue(issues(r).contains(PREFIX + "NOPE"), "в замечании назван код: " + issues(r));
        assertEquals(0, ((Number) result.get("toCreate")).intValue());
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.sku WHERE code = '"
                + PREFIX + "NOPE'"), "товар не появился");
    }

    @Test
    void archivedProductIsRefused() {
        String a = product("ARCH");
        signIn();
        sku.setSkuActive(a, false);

        Map<String, Object> r = rowNumber(check(file(List.of(H_SKU, H_CODE, H_COEF),
                List.of(row(PREFIX + "ARCH", "BOX", "12")))), 2);
        assertFalse(valid(r));
        assertTrue(issues(r).contains("archived"), issues(r));
    }

    @Test
    void sameProductAndCodeTwiceInTheFilePointsAtTheFirstRow() {
        product("DUP");
        Map<String, Object> r = rowNumber(check(file(List.of(H_SKU, H_CODE, H_COEF),
                List.of(row(PREFIX + "DUP", "BOX", "12"),
                        row(PREFIX + "dup", "box", "24")))), 3);
        assertFalse(valid(r), "регистр не делает пару другой");
        assertTrue(issues(r).contains("строке 2"), issues(r));
    }

    /** Коэффициент делит — ноль бессмыслен, а у новой упаковки он обязателен. */
    @Test
    void coefficientIsRequiredForNewAndMustBePositive() {
        product("COEF");
        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_COEF),
                List.of(row(PREFIX + "COEF", "BOX", null),
                        row(PREFIX + "COEF", "PLT", "0"))));
        assertTrue(issues(rowNumber(result, 2)).contains("required"), issues(rowNumber(result, 2)));
        assertTrue(issues(rowNumber(result, 3)).contains("больше нуля"),
                issues(rowNumber(result, 3)));
        // Ячейка заполнена — нулём. «Не заполнено» здесь было бы неправдой.
        assertFalse(issues(rowNumber(result, 3)).contains("required"),
                "к нулю добавилось ещё и «не заполнено»: " + issues(rowNumber(result, 3)));
    }

    @Test
    void fileWithoutKeyColumnsIsRefusedWithTheHeadersListed() {
        byte[] content = file(List.of(H_SKU, H_COEF), List.of(row(PREFIX + "X", "12")));
        PortalUser user = user();
        expectRejection(400, () -> imports.check(user, "Упаковки.xlsx", content));
    }

    /* ======================= Применение ======================= */

    @Test
    void creatingWritesThePackagingAndSetsTheOrderUnit() {
        String a = product("NEW");

        String importId = checkOnly(file(List.of(H_SKU, H_CODE, H_TYPE, H_COEF, H_BARCODE, H_ORDER),
                List.of(row(PREFIX + "NEW", "BOX", "Короб", "12", "0001234", "да"),
                        row(PREFIX + "NEW", "PLT", "Паллета", "480", null, "нет"))));
        Map<String, Object> after = apply(importId, true);

        assertEquals(2, ((Number) after.get("applied")).intValue(), String.valueOf(after));
        assertEquals(0, ((Number) after.get("failed")).intValue(), String.valueOf(after));

        String box = singleValue("SELECT packaging_id::text FROM dbo.product_packaging"
                + " WHERE sku_id = '" + a + "' AND packaging_code = 'BOX'");
        assertEquals("0001234", packagingField(box, "barcode"),
                "штрих-код записан текстом, ведущие нули на месте");
        assertEquals("12.000", packagingField(box, "coefficient"));
        assertEquals(box, orderUnitOf(a), "«да» назначило единицу заказа");
    }

    @Test
    void updatingFillsOnlyEmptyFields() {
        String a = product("UPD");
        String box = packaging(a, "BOX", Map.of("barcode", "111"));

        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_BARCODE, H_LENGTH),
                List.of(row(PREFIX + "UPD", "BOX", "111", "40"))));
        Map<String, Object> r = rowNumber(result, 2);
        assertTrue(valid(r), issues(r));
        assertTrue(String.valueOf(r.get("fillFieldsJson")).contains("length_cm"));
        assertFalse(String.valueOf(r.get("fillFieldsJson")).contains("barcode"),
                "совпадающее поле не заполняется");

        apply(String.valueOf(result.get("importId")), false);
        assertEquals("40.00", packagingField(box, "length_cm"), "пустое поле заполнилось");
        assertEquals("111", packagingField(box, "barcode"));
    }

    /** Импорт не может перезаписать заполненное — ни одного поля, ни строки целиком. */
    @Test
    void filledFieldWithAnotherValueBlocksTheWholeRow() {
        String a = product("CONF");
        String box = packaging(a, "BOX", Map.of("coefficient", 12));

        Map<String, Object> r = rowNumber(check(file(List.of(H_SKU, H_CODE, H_COEF, H_LENGTH),
                List.of(row(PREFIX + "CONF", "BOX", "24", "40")))), 2);
        assertFalse(valid(r));
        assertTrue(issues(r).contains("conflict"), issues(r));
        assertEquals("", packagingField(box, "length_cm"));
    }

    /* ======================= Объём по размерам (V82) ======================= */

    /**
     * Размеры заданы — объём считается, а не берётся из файла. Другое
     * число в файле не портит строку: оно показано замечанием, чтобы
     * человек заметил, например, миллиметры вместо сантиметров.
     */
    @Test
    void volumeIsRecalculatedFromDimensionsWithANote() {
        product("VOL");
        Map<String, Object> result = check(file(
                List.of(H_SKU, H_CODE, H_COEF, H_LENGTH, H_WIDTH, H_HEIGHT, H_VOLUME),
                List.of(row(PREFIX + "VOL", "BOX", "6", "40", "30", "20", "99"),
                        row(PREFIX + "VOL", "PLT", "60", "120", "80", "100", null),
                        row(PREFIX + "VOL", "BAG", "1", "40", null, "20", "0,5"))));

        Map<String, Object> box = rowNumber(result, 2);
        assertTrue(valid(box), "замечание о пересчёте строку не портит: " + issues(box));
        assertTrue(issues(box).contains("recalculated"), issues(box));
        assertTrue(issues(box).contains("99"), "в замечании видно, что стояло в файле: " + issues(box));
        assertTrue(String.valueOf(box.get("cellValuesJson")).contains("0.024"),
                String.valueOf(box.get("cellValuesJson")));

        Map<String, Object> plt = rowNumber(result, 3);
        assertTrue(valid(plt), issues(plt));
        assertFalse(issues(plt).contains("recalculated"), "объёма в файле не было — и замечать нечего");

        Map<String, Object> bag = rowNumber(result, 4);
        assertTrue(valid(bag), issues(bag));
        assertFalse(issues(bag).contains("recalculated"), "без ширины объём остаётся из файла");

        assertEquals(3, ((Number) result.get("toCreate")).intValue(), "замечание — не ошибка");
        assertEquals(0, ((Number) result.get("errorCount")).intValue());

        apply(String.valueOf(result.get("importId")), true);
        String a = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = '" + PREFIX + "VOL'");
        assertEquals("0.02400", volumeOf(a, "BOX"));
        assertEquals("0.96000", volumeOf(a, "PLT"));
        assertEquals("0.50000", volumeOf(a, "BAG"));
    }

    /**
     * Существующая упаковка: файл принёс недостающую высоту. Объём
     * в портале был введён руками и с размерами не сходится — после
     * записи он будет посчитан, и окно проверки говорит об этом заранее.
     */
    @Test
    void fillingTheLastDimensionRecalculatesThePortalVolume() {
        String a = product("VOLX");
        String box = packaging(a, "BOX", Map.of("length_cm", 40, "width_cm", 30, "volume_m3", 1));

        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_HEIGHT),
                List.of(row(PREFIX + "VOLX", "BOX", "20"))));
        Map<String, Object> r = rowNumber(result, 2);
        assertTrue(valid(r), issues(r));
        assertTrue(String.valueOf(r.get("fillFieldsJson")).contains("volume_m3"),
                String.valueOf(r.get("fillFieldsJson")));
        assertTrue(issues(r).contains("recalculated"), issues(r));

        apply(String.valueOf(result.get("importId")), false);
        assertEquals("0.02400", packagingField(box, "volume_m3"));
    }

    private String volumeOf(String skuId, String code) {
        return singleValue("SELECT COALESCE(volume_m3::text, '') FROM dbo.product_packaging"
                + " WHERE sku_id = '" + skuId + "' AND packaging_code = '" + code + "'");
    }

    /* ======================= Единица заказа ======================= */

    /** Выбранную единицу заказа импорт не меняет — это заполненное поле. */
    @Test
    void orderUnitAlreadyChosenIsNotReplaced() {
        String a = product("OU");
        String box = packaging(a, "BOX", Map.of());
        makeOrderUnit(box);

        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_COEF, H_ORDER),
                List.of(row(PREFIX + "OU", "PLT", "480", "да"))));
        Map<String, Object> r = rowNumber(result, 2);
        assertFalse(valid(r));
        assertTrue(issues(r).contains("BOX"), "замечание называет нынешнюю единицу: " + issues(r));
        assertEquals(box, orderUnitOf(a));
    }

    @Test
    void twoYesForOneProductInTheFileIsAnError() {
        product("TWO");
        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_COEF, H_ORDER),
                List.of(row(PREFIX + "TWO", "BOX", "12", "да"),
                        row(PREFIX + "TWO", "PLT", "480", "да"))));
        assertTrue(valid(rowNumber(result, 2)), issues(rowNumber(result, 2)));
        assertFalse(valid(rowNumber(result, 3)));
        assertTrue(issues(rowNumber(result, 3)).contains("строке 2"),
                issues(rowNumber(result, 3)));
    }

    /** «Нет» у той, что уже единица заказа, — попытка изменить заполненное. */
    @Test
    void removingTheOrderUnitByImportIsRefused() {
        String a = product("RM");
        String box = packaging(a, "BOX", Map.of());
        makeOrderUnit(box);

        Map<String, Object> r = rowNumber(check(file(List.of(H_SKU, H_CODE, H_ORDER, H_LENGTH),
                List.of(row(PREFIX + "RM", "BOX", "нет", "40")))), 2);
        assertFalse(valid(r));
        assertEquals(box, orderUnitOf(a));
    }

    /** Пустую единицу заказа обновление заполняет — как любое пустое поле. */
    @Test
    void emptyOrderUnitIsFilledByUpdate() {
        String a = product("FILL");
        String box = packaging(a, "BOX", Map.of());

        Map<String, Object> result = check(file(List.of(H_SKU, H_CODE, H_ORDER),
                List.of(row(PREFIX + "FILL", "BOX", "да"))));
        assertTrue(valid(rowNumber(result, 2)), issues(rowNumber(result, 2)));
        apply(String.valueOf(result.get("importId")), false);
        assertEquals(box, orderUnitOf(a));
    }

    /**
     * Единицу заказа выбрали в карточке, пока было открыто окно проверки.
     * Кнопка «Создать» не назначает её поверх: строка становится ошибкой.
     */
    @Test
    void orderUnitChosenWhileTheWindowWasOpenIsNotOverwritten() {
        String a = product("RACE");
        String importId = checkOnly(file(List.of(H_SKU, H_CODE, H_COEF, H_ORDER),
                List.of(row(PREFIX + "RACE", "PLT", "480", "да"))));

        String box = packaging(a, "BOX", Map.of());
        makeOrderUnit(box);

        Map<String, Object> after = apply(importId, true);
        assertEquals(0, ((Number) after.get("applied")).intValue(), String.valueOf(after));
        assertEquals(1, ((Number) after.get("failed")).intValue());
        assertEquals(box, orderUnitOf(a), "единица заказа не заменена");
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.product_packaging"
                        + " WHERE sku_id = '" + a + "' AND packaging_code = 'PLT'"),
                "упаковка без своей половины не записалась: строка откатилась целиком");
    }

    /* ======================= Границы ======================= */

    /** Загрузку упаковок нельзя прочитать или применить по адресу товаров. */
    @Test
    void packagingImportIsNotReachableAsSkuImport() {
        product("KIND");
        String importId = checkOnly(file(List.of(H_SKU, H_CODE, H_COEF),
                List.of(row(PREFIX + "KIND", "BOX", "12"))));
        signIn();
        expectRejection(404, () -> skuImports.result(importId));
        PortalUser user = user();
        expectRejection(404, () -> skuImports.apply(user, importId, true));
    }

    @Test
    void importIsRefusedWhenPackagingUploadIsDisabled() {
        product("OFF");
        inContext("INSERT INTO dbo.user_settings (user_id, can_upload_packaging_excel)"
                + " SELECT user_id, false FROM dbo.app_user WHERE login = '" + USER + "'"
                + " ON CONFLICT (user_id) DO UPDATE SET can_upload_packaging_excel = false");
        byte[] content = file(List.of(H_SKU, H_CODE, H_COEF), List.of(row(PREFIX + "OFF", "B", "1")));
        PortalUser user = user();
        // §6.3: право проверяется на сервере. Право на товары при этом
        // не задето — у них свой признак.
        expectRejection(403, () -> imports.check(user, "Упаковки.xlsx", content));
        expectRejection(403, () -> template.build(user));
    }

    /* ======================= Шаблон ======================= */

    /**
     * Шаблон и загрузчик понимают друг друга.
     *
     * <p>Собираем файл из нашего же шаблона, дописываем строку и грузим.
     * Нераспознанный столбец здесь означал бы, что шаблон подписал колонку
     * иначе, чем загрузчик её ищет, — и каждый скачанный шаблон приходил
     * бы обратно с предупреждением.
     */
    @Test
    void fileMadeFromTheTemplateIsReadBackWithoutUnknownColumns() throws Exception {
        product("TPL");
        SkuTemplateService.Template file = template.build(user());
        assertTrue(file.fileName().startsWith("Шаблон_Упаковки"), file.fileName());

        byte[] filled;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = wb.getSheet("Упаковки");
            Row head = sheet.getRow(0);
            Row data = sheet.getRow(1);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < head.getLastCellNum(); i++) {
                String h = head.getCell(i).getStringCellValue();
                headers.add(h);
                String value = switch (h.replace(" *", "")) {
                    case "Коммерческий код товара" -> PREFIX + "TPL";
                    case "Код упаковки" -> "BOX";
                    case "Коэффициент" -> "12";
                    default -> null;
                };
                if (value != null) {
                    data.getCell(i).setCellValue(value);
                }
            }
            assertEquals(H_SKU, headers.get(0), "товар — первым: без него строку не опознать");
            assertTrue(headers.contains(H_ORDER), "единица заказа в шаблоне: " + headers);
            assertFalse(headers.contains("Наименование"),
                    "наименование упаковки собирает экран, в шаблоне его нет: " + headers);

            // Списка кодов товаров в шаблоне нет (V97): товаров у клиента
            // могут быть тысячи, код проверяет загрузка.
            Sheet dict = wb.getSheet("Справочники");
            boolean listed = false;
            for (Row r : dict) {
                for (var c : r) {
                    if ((PREFIX + "TPL").equals(c.toString())) {
                        listed = true;
                    }
                }
            }
            assertFalse(listed, "кодов товаров в списках шаблона нет");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            filled = out.toByteArray();
        }

        Map<String, Object> result = check(filled);
        assertEquals("[]", String.valueOf(result.get("columnsSkippedJson")),
                "все столбцы шаблона опознаны");
        assertEquals(1, ((Number) result.get("toCreate")).intValue(), String.valueOf(result));
    }
}
