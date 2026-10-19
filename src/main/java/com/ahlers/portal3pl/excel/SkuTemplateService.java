package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.excel.SkuTemplateFields.Field;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidation;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Шаблон Excel для загрузки номенклатуры.
 *
 * <p>Файл — настоящая «умная таблица» Excel: одна строка заголовка,
 * фильтры, проверка данных на колонках со своими текстами ошибок.
 * Человек может удалить любые столбцы, кроме обязательных, и дописать
 * строки: загрузчик опознаёт колонки по заголовкам, а не по позициям.
 *
 * <p>Подписи берутся из справочника колонок, то есть на языке
 * пользователя и с учётом того, как клиент переименовал поле. Колонка,
 * запрещённая этому поклажедателю, в шаблон не попадает — иначе запрет
 * на экране ничего не значил бы.
 *
 * <p>Кода товара WMS в шаблоне нет: его присваивает WMS. Поклажедателя
 * тоже — он берётся из сеанса. В имя файла идёт наименование его
 * организации (для человека), а код — в скрытую метку (для машины):
 * по ней загрузка отличит файл, собранный для другого клиента, и метка
 * переживёт переименование файла.
 */
@Service
public class SkuTemplateService {

    /**
     * Предел строк при загрузке. Сам шаблон на столько строк не
     * растягивается (см. {@link #TEMPLATE_ROWS}) — это ограничение
     * загрузчика и граница, до которой расставлена проверка данных.
     */
    public static final int MAX_ROWS = 100;

    /**
     * Сколько строк данных в умной таблице шаблона.
     *
     * <p>Одна. Растянутая на сто строк таблица выглядит как сто пустых
     * записей, мешает смотреть файл и ничего не даёт: умная таблица
     * расширяется сама, стоит начать писать в строке под ней, и
     * переносит на новую строку и формат, и проверку данных.
     */
    public static final int TEMPLATE_ROWS = 1;

    private static final String SHEET = "Номенклатура";
    private static final String DICT_SHEET = "Справочники";
    /** Ячейка скрытого листа, в которой лежит код поклажедателя. */
    private static final String MARKER_CELL = "A1";
    private static final String MARKER_PREFIX = "depositor:";

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final ColumnService columns;
    private final SkuWriteService sku;

    public SkuTemplateService(JdbcClient jdbc, DepositorScope depositorScope,
                              ColumnService columns, SkuWriteService sku) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.columns = columns;
        this.sku = sku;
    }

    /** Готовый файл и имя, под которым его отдать. */
    public record Template(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public Template build(PortalUser user) {
        depositorScope.apply();
        Map<String, Object> depositor = depositor();
        String depositorCode = str(depositor.get("code"));
        String orgName = str(depositor.get("org_name"));
        Map<String, String> labels = new HashMap<>();
        Map<String, Integer> order = new HashMap<>();
        for (Map<String, Object> row : columns.layout(user, "sku")) {
            String id = String.valueOf(row.get("columnId"));
            labels.put(id, String.valueOf(row.get("label")));
            order.put(id, ((Number) row.get("sortOrder")).intValue());
        }

        // Колонка, запрещённая поклажедателю, в раскладке отсутствует —
        // значит её не будет и в шаблоне.
        List<Field> fields = SkuTemplateFields.ordered(order).stream()
                .filter(f -> labels.containsKey(f.catalogId()))
                .toList();
        if (fields.isEmpty()) {
            throw Fail.conflict("error.template.no_columns");
        }

        Map<String, List<String>> dictionaries = dictionaryValues();

        try (Workbook wb = new XSSFWorkbook()) {
            byte[] bytes = write(wb, fields, labels, dictionaries, depositorCode);
            return new Template(fileName(orgName), bytes);
        } catch (IOException e) {
            throw Fail.internal("error.template.build_failed");
        }
    }

    /**
     * Имя файла — по наименованию организации: человек узнаёт своего
     * клиента в списке скачанных файлов, а код ему ничего не говорит.
     *
     * <p>Код при этом из файла не исчезает — он лежит в скрытой метке,
     * и загрузка опирается именно на неё. Так и должно быть: имя файла
     * человек переименует, а метка останется.
     */
    private String fileName(String orgName) {
        // в имени файла нельзя \ / : * ? " < > |, а длинное название
        // обрезаем, чтобы вместе с путём не упереться в предел Windows
        String safe = orgName == null ? "" : orgName
                .replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (safe.length() > 60) {
            safe = safe.substring(0, 60).trim();
        }
        return safe.isBlank() ? "Шаблон_Номенклатура.xlsx"
                : "Шаблон_Номенклатура_" + safe + ".xlsx";
    }

    /** Код и наименование текущего поклажедателя. */
    private Map<String, Object> depositor() {
        return jdbc.sql("SELECT code, org_name FROM dbo.depositor"
                        + " WHERE depositor_id = :id::uuid")
                .param("id", depositorScope.currentDepositorId())
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(Map.of());
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Названия справочных значений — ровно те, что видит человек на экране. */
    private Map<String, List<String>> dictionaryValues() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> all = sku.dictionaries();
        for (String kind : new HashSet<>(SkuTemplateFields.DICTIONARY.values())) {
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : all.getOrDefault(kind, List.of())) {
                Object name = row.get("name");
                if (name != null && !String.valueOf(name).isBlank()) {
                    names.add(String.valueOf(name));
                }
            }
            out.put(kind, names);
        }
        return out;
    }

    /* ======================= Сборка файла ======================= */

    private byte[] write(Workbook wb, List<Field> fields, Map<String, String> labels,
                         Map<String, List<String>> dictionaries, String depositorCode)
            throws IOException {
        XSSFSheet sheet = (XSSFSheet) wb.createSheet(SHEET);
        Sheet dict = wb.createSheet(DICT_SHEET);

        CellStyle head = headerStyle(wb, false);
        CellStyle headRequired = headerStyle(wb, true);
        CellStyle text = textStyle(wb);

        // 1. Заголовки. Excel-таблица не допускает повторов в заголовках —
        //    а они возможны: клиент мог переименовать поле в то, что уже
        //    занято. Повтор разводим суффиксом, иначе Excel объявит файл
        //    повреждённым и «починит» его сам.
        Row header = sheet.createRow(0);
        Set<String> used = new HashSet<>();
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            String base = labels.getOrDefault(f.catalogId(), f.column());
            if (f.required()) {
                base = base + " *";
            }
            String title = base;
            for (int n = 2; !used.add(title.toLowerCase()); n++) {
                title = base + " (" + n + ")";
            }
            headers.add(title);
            Cell cell = header.createCell(i);
            cell.setCellValue(title);
            cell.setCellStyle(f.required() ? headRequired : head);
        }

        // 2. Пустая строка под данные: ячейки должны существовать, иначе
        //    таблице не на чем держаться. Строка одна — таблица дорастёт
        //    сама, когда человек начнёт писать ниже.
        for (int r = 1; r <= TEMPLATE_ROWS; r++) {
            Row row = sheet.createRow(r);
            for (int i = 0; i < fields.size(); i++) {
                row.createCell(i);
            }
        }

        // 3. Умная таблица поверх заголовка и строки данных.
        AreaReference area = new AreaReference(
                new CellReference(0, 0),
                new CellReference(TEMPLATE_ROWS, fields.size() - 1),
                wb.getSpreadsheetVersion());
        XSSFTable table = sheet.createTable(area);
        table.setName("Номенклатура");
        table.setDisplayName("Номенклатура");
        if (!table.getCTTable().isSetAutoFilter()) {
            table.getCTTable().addNewAutoFilter().setRef(area.formatAsString());
        }
        table.updateHeaders();

        // 4. Форматы и ширина колонок.
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            if (f.textFormat()) {
                sheet.setDefaultColumnStyle(i, text);
            }
            int width = Math.clamp(headers.get(i).length() + 4, 12, 42);
            sheet.setColumnWidth(i, width * 256);
        }
        sheet.createFreezePane(0, 1);

        // 5. Скрытый лист: значения для выпадающих списков и метка клиента.
        writeDictionarySheet(dict, dictionaries, depositorCode);

        // 6. Проверка данных. Тексты ошибок свои: стандартное
        //    «Это значение не подходит» человеку ничего не говорит.
        applyValidation(sheet, fields, dictionaries);

        // 7. Подсказки на заголовках обязательных и справочных колонок.
        addComments(wb, sheet, fields);

        wb.setSheetHidden(wb.getSheetIndex(dict), true);
        wb.setActiveSheet(wb.getSheetIndex(sheet));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    /**
     * Скрытый лист. Метка поклажедателя переживает переименование файла,
     * поэтому загрузка опирается на неё, а не только на имя.
     */
    private void writeDictionarySheet(Sheet dict, Map<String, List<String>> dictionaries,
                                      String depositorCode) {
        CellReference marker = new CellReference(MARKER_CELL);
        Row first = dict.createRow(marker.getRow());
        first.createCell(marker.getCol())
                .setCellValue(MARKER_PREFIX + (depositorCode == null ? "" : depositorCode));

        int col = 2;                              // метка занимает колонку A
        for (Map.Entry<String, List<String>> e : dictionaries.entrySet()) {
            Row head = dict.getRow(0) != null ? dict.getRow(0) : dict.createRow(0);
            head.createCell(col).setCellValue(e.getKey());
            List<String> values = e.getValue();
            for (int i = 0; i < values.size(); i++) {
                Row row = dict.getRow(i + 1) != null ? dict.getRow(i + 1) : dict.createRow(i + 1);
                row.createCell(col).setCellValue(values.get(i));
            }
            col++;
        }
    }

    private void applyValidation(XSSFSheet sheet, List<Field> fields,
                                 Map<String, List<String>> dictionaries) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        List<String> kinds = new ArrayList<>(dictionaries.keySet());

        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            // Проверка ставится сразу на сто строк, хотя в таблице одна:
            // человек чаще всего вставляет готовые строки из другого
            // файла, а вставка перекрывает проверку в ячейке. Если она
            // там уже стоит заранее — списки и ошибки остаются на месте.
            CellRangeAddressList range = new CellRangeAddressList(1, MAX_ROWS, i, i);
            DataValidationConstraint constraint;
            String title;
            String message;

            switch (f.kind()) {
                case DICTIONARY -> {
                    List<String> values = dictionaries.getOrDefault(f.dictionary(), List.of());
                    if (values.isEmpty()) {
                        continue;        // справочник пуст — проверять не по чему
                    }
                    int col = 2 + kinds.indexOf(f.dictionary());
                    String ref = "'" + DICT_SHEET + "'!$"
                            + CellReference.convertNumToColString(col)
                            + "$2:$" + CellReference.convertNumToColString(col)
                            + "$" + (values.size() + 1);
                    constraint = helper.createFormulaListConstraint(ref);
                    title = "Значение не из списка";
                    message = "Выберите значение из списка. Если нужного нет — "
                            + "обратитесь к оператору, справочник ведёт он.";
                }
                case NUMBER -> {
                    constraint = helper.createDecimalConstraint(
                            DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null);
                    title = "Нужно число";
                    message = "Введите число, не меньше нуля. "
                            + "Дробную часть отделяйте запятой или точкой.";
                }
                case BOOLEAN -> {
                    constraint = helper.createExplicitListConstraint(new String[]{"да", "нет"});
                    title = "Да или нет";
                    message = "Выберите «да» или «нет».";
                }
                default -> {
                    continue;            // текстовые поля не ограничиваем
                }
            }

            DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox(title, message);
            validation.setEmptyCellAllowed(!f.required());
            sheet.addValidationData(validation);
        }

        showDropDowns(sheet);
    }

    /**
     * Включает у списков галочку «Список допустимых значений».
     *
     * <p>Через POI это сделать нельзя: её метод {@code
     * setSuppressDropDownArrow} переворачивает значение ещё раз, и
     * {@code false} («не подавлять») превращается в {@code
     * showDropDown="1"}. А в самом формате этот признак уже
     * перевёрнут: {@code showDropDown="1"} означает «список не
     * показывать». Два переворота давали ровно то, что мы и увидели —
     * проверка на месте, а выпадающего списка нет.
     *
     * <p>Поэтому признак снимаем целиком: когда его в файле нет,
     * Excel показывает список — это его поведение по умолчанию.
     * Правим XML напрямую, чтобы результат не зависел от того, как
     * очередная версия POI понимает это слово.
     */
    private void showDropDowns(XSSFSheet sheet) {
        CTDataValidations all = sheet.getCTWorksheet().getDataValidations();
        if (all == null) {
            return;
        }
        for (CTDataValidation v : all.getDataValidationList()) {
            if (v.isSetShowDropDown()) {
                v.unsetShowDropDown();
            }
        }
    }

    private void addComments(Workbook wb, XSSFSheet sheet, List<Field> fields) {
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper factory = wb.getCreationHelper();
        Row header = sheet.getRow(0);

        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            String note = switch (f.kind()) {
                case DICTIONARY -> "Выберите из списка. Значения ведёт оператор.";
                case NUMBER -> "Число, не меньше нуля.";
                case BOOLEAN -> "«да» или «нет».";
                default -> f.textFormat()
                        ? "Хранится текстом: ведущие нули и дефисы не потеряются."
                        : null;
            };
            if (f.required()) {
                note = "Обязательное поле. " + (note == null ? "" : note);
            }
            if (note == null) {
                continue;
            }
            ClientAnchor anchor = factory.createClientAnchor();
            anchor.setCol1(i);
            anchor.setCol2(i + 3);
            anchor.setRow1(0);
            anchor.setRow2(4);
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(factory.createRichTextString(note));
            header.getCell(i).setCellComment(comment);
        }
    }

    private CellStyle headerStyle(Workbook wb, boolean required) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setWrapText(true);
        if (required) {
            // обязательные видно сразу, без чтения подсказок
            style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private CellStyle textStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("@"));
        return style;
    }

    /* ============ Чтение метки при загрузке ============ */

    /**
     * Код поклажедателя из скрытой метки, если она есть.
     * Нужен загрузке: файл, собранный для другого клиента, принимать нельзя.
     */
    public static String markerOf(Workbook wb) {
        Sheet dict = wb.getSheet(DICT_SHEET);
        if (dict == null) {
            return null;
        }
        CellReference ref = new CellReference(MARKER_CELL);
        Row row = dict.getRow(ref.getRow());
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(ref.getCol());
        if (cell == null || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.STRING) {
            return null;
        }
        String value = cell.getStringCellValue();
        if (value == null || !value.startsWith(MARKER_PREFIX)) {
            return null;
        }
        String code = value.substring(MARKER_PREFIX.length()).trim();
        return code.isEmpty() ? null : code;
    }
}
