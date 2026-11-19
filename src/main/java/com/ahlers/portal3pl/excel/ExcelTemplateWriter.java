package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.common.ActorText;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сборка шаблона Excel — одна на все справочники.
 *
 * <p>Файл — настоящая «умная таблица» Excel: одна строка заголовка,
 * фильтры, проверка данных на колонках со своими текстами ошибок,
 * скрытый лист со значениями для выпадающих списков и меткой клиента.
 * Товары и упаковки отличаются только составом колонок; всё остальное —
 * вплоть до обхода ошибки POI с выпадающими списками — общее. Держать
 * это в двух местах значило бы однажды исправить ошибку только в одном.
 *
 * <p>Здесь нет ни одного обращения к базе: что писать, решает служба
 * своего справочника, а этот класс только раскладывает готовое по листу.
 */
final class ExcelTemplateWriter {

    /**
     * Сколько строк данных в умной таблице шаблона.
     *
     * <p>Одна. Растянутая на сто строк таблица выглядит как сто пустых
     * записей, мешает смотреть файл и ничего не даёт: умная таблица
     * расширяется сама, стоит начать писать в строке под ней, и
     * переносит на новую строку и формат, и проверку данных.
     */
    static final int TEMPLATE_ROWS = 1;

    static final String DICT_SHEET = "Справочники";
    /** Ячейка скрытого листа, в которой лежит код поклажедателя. */
    private static final String MARKER_CELL = "A1";
    private static final String MARKER_PREFIX = "depositor:";

    private ExcelTemplateWriter() {
    }

    /**
     * Свои тексты для выпадающего списка.
     *
     * <p>Стандартные тексты справочного поля говорят «значения ведёт
     * оператор» — это правда про единицы измерения и неправда про коды
     * товаров: товар заводит сам клиент. Справочнику, про который
     * стандартные слова врут, они подменяются этой записью.
     *
     * @param title   заголовок окна ошибки
     * @param message текст окна ошибки
     * @param note    примечание к заголовку колонки
     */
    record ListText(String title, String message, String note) {
    }

    /**
     * Ячейка «да/нет» на языке того, кто скачал шаблон (V91).
     *
     * <p>Загрузка понимает любой язык (да/yes/1/+), поэтому файл,
     * скачанный по-английски, загрузит и человек с русским порталом.
     * А вот список в ячейке и подсказка должны быть на его языке —
     * иначе англоязычный клиент видит «да» и «нет», которых не читает.
     *
     * @param yes     «да» для списка
     * @param no      «нет» для списка
     * @param title   заголовок окна ошибки
     * @param message текст окна ошибки
     */
    record BooleanText(String yes, String no, String title, String message) {

        /**
         * Тексты на языке текущего пользователя. В списке ячейки —
         * строчными, как их и пишут в таблице: «да», «yes».
         */
        static BooleanText of(ActorText texts) {
            java.util.Locale locale = java.util.Locale.forLanguageTag(texts.locale());
            String yes = texts.of("common.value.yes").toLowerCase(locale);
            String no = texts.of("common.value.no").toLowerCase(locale);
            return new BooleanText(yes, no, texts.of("excel.validation.boolean_title"),
                    texts.of("excel.validation.boolean_message", "yes", yes, "no", no));
        }
    }

    /**
     * Порядок колонок в шаблоне: обязательные первыми, дальше — как
     * на экране (порядок из справочника колонок).
     *
     * @param catalogOrder идентификатор колонки справочника → его порядок
     */
    static List<ExcelField> ordered(Collection<ExcelField> fields,
                                    Map<String, Integer> catalogOrder) {
        return fields.stream()
                .sorted((a, b) -> {
                    if (a.required() != b.required()) {
                        return a.required() ? -1 : 1;
                    }
                    int pa = catalogOrder.getOrDefault(a.catalogId(), Integer.MAX_VALUE);
                    int pb = catalogOrder.getOrDefault(b.catalogId(), Integer.MAX_VALUE);
                    if (pa != pb) {
                        return Integer.compare(pa, pb);
                    }
                    return a.column().compareTo(b.column());
                })
                .toList();
    }

    /**
     * Имя файла — по наименованию организации: человек узнаёт своего
     * клиента в списке скачанных файлов, а код ему ничего не говорит.
     *
     * <p>Код при этом из файла не исчезает — он лежит в скрытой метке,
     * и загрузка опирается именно на неё. Так и должно быть: имя файла
     * человек переименует, а метка останется.
     *
     * @param prefix начало имени: «Шаблон_Номенклатура», «Шаблон_Упаковки»
     */
    static String fileName(String prefix, String orgName) {
        // в имени файла нельзя \ / : * ? " < > |, а длинное название
        // обрезаем, чтобы вместе с путём не упереться в предел Windows
        String safe = orgName == null ? "" : orgName
                .replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (safe.length() > 60) {
            safe = safe.substring(0, 60).trim();
        }
        return safe.isBlank() ? prefix + ".xlsx" : prefix + "_" + safe + ".xlsx";
    }

    /**
     * Собирает книгу.
     *
     * @param sheetName    имя листа — на языке того, кто скачал (V102)
     * @param tableName    имя умной таблицы — латиницей и не переводится,
     *                     как у документов: {@code Items}, {@code Packaging}
     * @param fields       колонки в нужном порядке
     * @param labels       идентификатор колонки справочника → подпись
     * @param dictionaries имя справочника → значения выпадающего списка
     * @param listTexts    свои тексты для отдельных справочников
     * @param maxRows      до какой строки расставлять проверку данных
     * @param texts        тексты проверок и подсказок на языке скачавшего
     */
    static byte[] write(String sheetName, String tableName, List<ExcelField> fields,
                        Map<String, String> labels, Map<String, List<String>> dictionaries,
                        Map<String, ListText> listTexts, BooleanText booleans,
                        String depositorCode, int maxRows, ActorText texts)
            throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = (XSSFSheet) wb.createSheet(sheetName);
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
                ExcelField f = fields.get(i);
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
            table.setName(tableName);
            table.setDisplayName(tableName);
            if (!table.getCTTable().isSetAutoFilter()) {
                table.getCTTable().addNewAutoFilter().setRef(area.formatAsString());
            }
            table.updateHeaders();

            // 4. Форматы и ширина колонок.
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).textFormat()) {
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
            applyValidation(sheet, fields, dictionaries, listTexts, booleans, maxRows, texts);

            // 7. Подсказки на заголовках обязательных и справочных колонок.
            addComments(wb, sheet, fields, listTexts, booleans, texts);

            wb.setSheetHidden(wb.getSheetIndex(dict), true);
            wb.setActiveSheet(wb.getSheetIndex(sheet));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Скрытый лист. Метка поклажедателя переживает переименование файла,
     * поэтому загрузка опирается на неё, а не только на имя.
     */
    private static void writeDictionarySheet(Sheet dict, Map<String, List<String>> dictionaries,
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

    private static void applyValidation(XSSFSheet sheet, List<ExcelField> fields,
                                        Map<String, List<String>> dictionaries,
                                        Map<String, ListText> listTexts, BooleanText booleans,
                                        int maxRows, ActorText texts) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        List<String> kinds = new ArrayList<>(dictionaries.keySet());

        for (int i = 0; i < fields.size(); i++) {
            ExcelField f = fields.get(i);
            // Проверка ставится сразу на все строки предела, хотя в таблице
            // одна: человек чаще всего вставляет готовые строки из другого
            // файла, а вставка перекрывает проверку в ячейке. Если она там
            // уже стоит заранее — списки и ошибки остаются на месте.
            CellRangeAddressList range = new CellRangeAddressList(1, maxRows, i, i);
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
                    ListText own = listTexts.get(f.dictionary());
                    title = own != null ? own.title() : texts.of("excel.doc.validation_list_title");
                    message = own != null ? own.message()
                            : texts.of("excel.catalog.validation_list_message");
                }
                case NUMBER -> {
                    if (f.positive()) {
                        constraint = helper.createDecimalConstraint(
                                DataValidationConstraint.OperatorType.GREATER_THAN, "0", null);
                        title = texts.of("excel.doc.validation_qty_title");
                        message = texts.of("excel.catalog.validation_positive_message");
                    } else {
                        constraint = helper.createDecimalConstraint(
                                DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null);
                        title = texts.of("excel.catalog.validation_number_title");
                        message = texts.of("excel.catalog.validation_number_message");
                    }
                }
                case BOOLEAN -> {
                    constraint = helper.createExplicitListConstraint(
                            new String[]{booleans.yes(), booleans.no()});
                    title = booleans.title();
                    message = booleans.message();
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
    static void showDropDowns(XSSFSheet sheet) {
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

    private static void addComments(Workbook wb, XSSFSheet sheet, List<ExcelField> fields,
                                    Map<String, ListText> listTexts, BooleanText booleans,
                                    ActorText texts) {
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper factory = wb.getCreationHelper();
        Row header = sheet.getRow(0);

        for (int i = 0; i < fields.size(); i++) {
            ExcelField f = fields.get(i);
            ListText own = f.kind() == ExcelField.Kind.DICTIONARY
                    ? listTexts.get(f.dictionary()) : null;
            String note = switch (f.kind()) {
                case DICTIONARY -> own != null ? own.note() : texts.of("excel.catalog.note_list");
                case NUMBER -> texts.of(f.positive() ? "excel.catalog.note_positive" : "excel.catalog.note_number");
                case BOOLEAN -> texts.of("excel.catalog.note_boolean", "yes", booleans.yes(), "no", booleans.no());
                default -> f.textFormat() ? texts.of("excel.catalog.note_text") : null;
            };
            if (f.required()) {
                note = texts.of("excel.catalog.note_required") + (note == null ? "" : " " + note);
            }
            if (PackagingTemplateFields.VOLUME.equals(f.column())) {
                // Объём упаковки следует за размерами (V82): без этой
                // подсказки человек удивится, что его число заменили.
                note = (note == null ? "" : note + " ") + texts.of("excel.catalog.note_volume");
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

    private static CellStyle headerStyle(Workbook wb, boolean required) {
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

    private static CellStyle textStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("@"));
        return style;
    }

    /**
     * Метка поклажедателя на скрытом листе — для шаблонов, которые
     * раскладывают лист сами (заказ, V96). Формат тот же, что у
     * {@link #write}: загрузка читает её одним {@link #markerOf}.
     */
    static void writeMarker(Sheet dict, String depositorCode) {
        CellReference marker = new CellReference(MARKER_CELL);
        Row row = dict.getRow(marker.getRow()) != null
                ? dict.getRow(marker.getRow()) : dict.createRow(marker.getRow());
        row.createCell(marker.getCol())
                .setCellValue(MARKER_PREFIX + (depositorCode == null ? "" : depositorCode));
    }

    /* ============ Чтение метки при загрузке ============ */

    /**
     * Код поклажедателя из скрытой метки, если она есть.
     * Нужен загрузке: файл, собранный для другого клиента, принимать нельзя.
     */
    static String markerOf(Workbook wb) {
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
        if (cell == null || cell.getCellType() != CellType.STRING) {
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
