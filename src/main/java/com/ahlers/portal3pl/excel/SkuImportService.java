package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuColumns;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.excel.SkuImportColumns.Column;
import com.ahlers.portal3pl.excel.SkuImportColumns.Layout;
import com.ahlers.portal3pl.excel.SkuTemplateFields.Field;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Загрузка номенклатуры из Excel: разбор файла и проверка.
 *
 * <p>Этот шаг в справочник товаров ничего не пишет. Он разбирает файл,
 * раскладывает строки по классам и годности и складывает результат
 * в {@code excel_import*}. Запись — следующий шаг, отдельным запросом:
 * человек сначала смотрит на экран проверки.
 *
 * <p>Состояние строки — два независимых признака:
 * <ul>
 *   <li><b>класс</b> — новая позиция, существующая или неопознанная
 *       (коммерческий код пуст, позицию определить нельзя);</li>
 *   <li><b>годность</b> — запишется строка или нет.</li>
 * </ul>
 * Одно не выводится из другого: у новой позиции может быть не заполнено
 * обязательное поле, а существующая может не менять ни одного поля.
 *
 * <p>«Ошибка» значит «строка не будет записана», по любой причине.
 * Причина всегда записывается в {@code excel_import_issue} — человеку
 * нужен не признак, а объяснение.
 */
@Service
public class SkuImportService {

    /**
     * Больше портал не примет. Предел тот же, что заложен в шаблон,
     * и берётся оттуда же: два числа разошлись бы при первой правке.
     */
    public static final int MAX_ROWS = SkuTemplateService.MAX_ROWS;

    /** Больше 2 МБ для ста строк номенклатуры быть не может. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final String SHEET = "Номенклатура";

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final SkuImportColumns matcher;
    private final SkuWriteService sku;
    private final CurrentUser current;
    private final PlatformTransactionManager txManager;

    public SkuImportService(JdbcClient jdbc, DepositorScope depositorScope,
                            SkuImportColumns matcher, SkuWriteService sku,
                            CurrentUser current, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.matcher = matcher;
        this.sku = sku;
        this.current = current;
        this.txManager = txManager;
    }

    /* ===================== Разбор и проверка ===================== */

    /**
     * Разбирает файл и сохраняет результат проверки.
     *
     * @return идентификатор загрузки, по нему экран читает результат
     */
    @Transactional
    public String check(PortalUser user, String fileName, byte[] content) {
        current.requireExcelUpload("sku");
        depositorScope.apply();
        requireFile(fileName, content);

        // Чистка висит на начале новой загрузки, а не на расписании:
        // отдельного планировщика в портале пока нет, а момент
        // подходящий — человек всё равно ждёт разбора файла. Журнал
        // старше 90 дней удаляется, забытые разобранные строки —
        // старше недели (решение заказчика).
        jdbc.sql("SELECT dbo.excel_import_cleanup()").query().listOfRows();

        String depositorId = depositorScope.currentDepositorId();
        String depositorCode = depositorCode();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            requireOwnFile(wb, fileName, depositorCode);

            Sheet sheet = sheetOf(wb);
            List<String> headers = headers(sheet);
            Layout layout = matcher.match(headers, depositorId);
            requireKeyColumns(layout);

            List<Map<String, Object>> parsed = parseRows(sheet, layout);
            if (parsed.isEmpty()) {
                throw Fail.badRequest("error.excel.no_rows");
            }

            String importId = createImport(user, depositorId, fileName, content.length, layout);
            storeRows(importId, depositorId, parsed);
            recount(importId);
            return importId;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // Повреждённый файл, не .xlsx внутри, пароль на книге —
            // причина человеку всё равно непонятна, а подробность
            // нужна в журнале, не на экране.
            throw Fail.badRequest("error.excel.unreadable");
        }
    }

    private void requireFile(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw Fail.badRequest("error.file.empty");
        }
        if (content.length > MAX_BYTES) {
            throw Fail.badRequest("error.file.too_big");
        }
        // Проверяем по первым байтам, а не по расширению: переименованный
        // .doc с расширением .xlsx иначе дошёл бы до разбора и упал там
        // невнятной ошибкой. PK — подпись zip-архива, которым и является xlsx.
        boolean zip = content.length > 4 && content[0] == 'P' && content[1] == 'K'
                && (content[2] == 3 || content[2] == 5 || content[2] == 7);
        if (!zip) {
            throw Fail.badRequest("error.file.not_excel");
        }
        if (fileName == null || fileName.isBlank()) {
            throw Fail.badRequest("error.file.name_missing");
        }
    }

    /**
     * Файл, собранный для другого клиента, принимать нельзя.
     *
     * <p>Это главный опасный исход всей затеи: менеджер скачал шаблон
     * для одного клиента, переключился на другого и загрузил тот файл.
     * Товары ушли бы не тому — ровно то, от чего §6.4 защищает выбором
     * поклажедателя.
     *
     * <p>Несовпадение — отказ, отсутствие признака — не препятствие:
     * человек мог собрать файл сам. Отсутствие ничего не доказывает,
     * а найденное несовпадение доказывает.
     */
    private void requireOwnFile(Workbook wb, String fileName, String depositorCode) {
        if (depositorCode == null || depositorCode.isBlank()) {
            return;
        }
        String marker = SkuTemplateService.markerOf(wb);
        if (marker != null && !marker.equalsIgnoreCase(depositorCode)) {
            throw Fail.conflict("error.excel.other_depositor_marker",
                    "marker", marker, "current", depositorCode);
        }
        if (marker == null) {
            String other = foreignCodeInName(fileName, depositorCode);
            if (other != null) {
                throw Fail.conflict("error.excel.other_depositor_name",
                        "other", other, "current", depositorCode);
            }
        }
    }

    /** Код чужого поклажедателя, найденный в имени файла, иначе null. */
    private String foreignCodeInName(String fileName, String ownCode) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.contains(ownCode.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return jdbc.sql("""
                        SELECT code FROM dbo.depositor
                        WHERE code <> :own::varchar
                          AND position(lower(code) in :name::text) > 0
                        ORDER BY length(code) DESC
                        LIMIT 1
                        """)
                .param("own", ownCode)
                .param("name", name)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private Sheet sheetOf(Workbook wb) {
        Sheet sheet = wb.getSheet(SHEET);
        if (sheet != null) {
            return sheet;
        }
        // Лист мог быть переименован — берём первый непустой видимый.
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (!wb.isSheetHidden(i) && wb.getSheetAt(i).getPhysicalNumberOfRows() > 0) {
                return wb.getSheetAt(i);
            }
        }
        throw Fail.badRequest("error.excel.no_sheets");
    }

    private List<String> headers(Sheet sheet) {
        Row row = sheet.getRow(sheet.getFirstRowNum());
        if (row == null) {
            throw Fail.badRequest("error.excel.no_header_row");
        }
        DataFormatter fmt = new DataFormatter();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            out.add(cell == null ? null : fmt.formatCellValue(cell).trim());
        }
        return out;
    }

    /**
     * Без коммерческого кода файл разбирать бессмысленно: по нему
     * строка опознаётся. Остальные обязательные поля проверяются
     * построчно — у существующей позиции они уже заполнены в портале.
     */
    private void requireKeyColumns(Layout layout) {
        if (!layout.has("code")) {
            String found = layout.columns().stream()
                    .map(Column::header)
                    .filter(h -> h != null && !h.isBlank())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("— ни одного");
            throw Fail.badRequest("error.excel.no_code_column", "headers", found);
        }
    }

    /* ===================== Строки ===================== */

    private List<Map<String, Object>> parseRows(Sheet sheet, Layout layout) {
        DataFormatter fmt = new DataFormatter();
        Map<String, Dictionary> dictionaries = dictionaries();
        Map<String, Integer> lengths = columnLengths();
        Map<String, Map<String, Object>> existing = existingByCode();
        Map<String, Integer> seenCode = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        int first = sheet.getFirstRowNum() + 1;
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || blank(row, layout, fmt)) {
                continue;            // пустая строка шаблона — не данные
            }
            if (rows.size() >= MAX_ROWS) {
                throw Fail.badRequest("error.excel.too_many_rows", "limit", MAX_ROWS);
            }
            rows.add(parseRow(row, r + 1, layout, fmt, dictionaries, lengths,
                    existing, seenCode));
        }
        return rows;
    }

    private boolean blank(Row row, Layout layout, DataFormatter fmt) {
        for (Column c : layout.columns()) {
            if (!c.recognized()) {
                continue;
            }
            if (!text(row, c.index(), fmt).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String text(Row row, int index, DataFormatter fmt) {
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return fmt.formatCellValue(cell).replace(' ', ' ').trim();
    }

    /**
     * Разбирает одну строку: приводит значения, собирает замечания,
     * определяет класс и годность.
     */
    private Map<String, Object> parseRow(Row row, int excelRow, Layout layout,
                                         DataFormatter fmt,
                                         Map<String, Dictionary> dictionaries,
                                         Map<String, Integer> lengths,
                                         Map<String, Map<String, Object>> existing,
                                         Map<String, Integer> seenCode) {
        Map<String, Object> values = new LinkedHashMap<>();
        // Рядом со значениями — то же самое в человеческом виде: в
        // values у ссылочных полей лежит идентификатор, а экрану нужно
        // «шт», а не «1». Применение берёт values, экран — display.
        Map<String, Object> display = new LinkedHashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();

        for (Column c : layout.columns()) {
            if (!c.recognized()) {
                continue;
            }
            Field f = c.field();
            String raw = text(row, c.index(), fmt);
            if (raw.isEmpty()) {
                continue;            // пусто в файле означает «нет значения»
            }
            Object value = coerce(f, raw, dictionaries, issues);
            if (value == null) {
                continue;            // не разобрали — замечание уже записано
            }
            Integer limit = lengths.get(f.column());
            if (limit != null && String.valueOf(value).length() > limit) {
                issues.add(issue(f.catalogId(), "value",
                        "Значение длиннее " + limit + " знаков", raw, null));
                continue;
            }
            values.put(f.column(), value);
            String asShown = shown(f.column(), value, dictionaries);
            if (asShown != null) {
                display.put(f.column(), asShown);
            }
        }

        String code = values.get("code") == null ? "" : String.valueOf(values.get("code")).trim();
        String rowClass;
        Map<String, Object> portal = null;

        if (code.isEmpty()) {
            // Позицию определить нельзя — ни к новым, ни к существующим
            // такую строку отнести не получится.
            rowClass = "unknown";
            issues.add(issue("code", "required",
                    "Не заполнен коммерческий код — по нему опознаётся позиция", null, null));
        } else {
            Integer before = seenCode.putIfAbsent(code.toLowerCase(Locale.ROOT), excelRow);
            if (before != null) {
                issues.add(issue("code", "duplicate",
                        "Код повторяется: он уже есть в строке " + before, code, null));
            }
            portal = existing.get(code.toLowerCase(Locale.ROOT));
            rowClass = portal == null ? "new" : "existing";
        }

        List<String> fill = new ArrayList<>();
        if ("new".equals(rowClass)) {
            checkRequired(values, issues);
        } else if ("existing".equals(rowClass)) {
            if (!Boolean.TRUE.equals(portal.get("is_active"))) {
                issues.add(issue(null, "archived",
                        "Позиция в архиве — верните её из архива, прежде чем обновлять",
                        null, null));
            }
            compare(values, portal, fill, issues, dictionaries);
        }

        boolean valid = issues.isEmpty();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rowNumber", excelRow);
        out.put("rowClass", rowClass);
        out.put("isValid", valid);
        out.put("rowKey", code.isEmpty() ? null : code);
        out.put("skuId", portal == null ? null : portal.get("sku_id"));
        out.put("values", values);
        out.put("display", display);
        out.put("fill", fill);
        out.put("issues", issues);
        return out;
    }

    /** Обязательные поля нужны только новой позиции: у существующей они уже есть. */
    private void checkRequired(Map<String, Object> values, List<Map<String, Object>> issues) {
        for (String column : SkuColumns.SKU_REQUIRED) {
            if (values.get(column) == null) {
                issues.add(issue(SkuTemplateFields.catalogId(column), "required",
                        "Не заполнено обязательное поле", null, null));
            }
        }
    }

    /**
     * Сравнение с существующей позицией.
     *
     * <p>Правило: заполняем только пустое. Если хоть одно заполненное
     * поле портала отличается от файла — строка не обновляется целиком.
     * Отсюда свойство, которое стоит назвать прямо: <b>импорт не может
     * перезаписать данные, которые в портале уже есть.</b>
     *
     * <p>Коммерческий код не сравнивается и не обновляется: он ключ,
     * по нему позиция и найдена.
     */
    private void compare(Map<String, Object> values, Map<String, Object> portal,
                         List<String> fill, List<Map<String, Object>> issues,
                         Map<String, Dictionary> dictionaries) {
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String column = e.getKey();
            if ("code".equals(column)) {
                continue;
            }
            Object was = portal.get(column);
            if (was == null || String.valueOf(was).isBlank()) {
                fill.add(column);
                continue;
            }
            if (!same(was, e.getValue())) {
                // В замечании показываем названия, а не идентификаторы:
                // «шт» против «кг» понятно, «1» против «3» — нет.
                issues.add(issue(SkuTemplateFields.catalogId(column), "conflict",
                        "Значение отличается от того, что уже в портале",
                        shownOrSelf(column, e.getValue(), dictionaries),
                        shownOrSelf(column, was, dictionaries)));
            }
        }
        if (fill.isEmpty() && issues.isEmpty()) {
            // Строка есть, а делать по ней нечего. По решению заказчика
            // это тоже ошибка: человек ждал от строки действия.
            issues.add(issue(null, "nothing",
                    "Строка не меняет ни одного поля — всё уже заполнено так же",
                    null, null));
        }
    }

    /**
     * Сравнение после приведения типа, а не по тексту.
     *
     * <p>{@code 1,5} и {@code 1.50} равны как числа, «шт» и «ШТ»
     * разрешаются в одну единицу измерения. Иначе правило давало бы
     * расхождения на пустом месте и не обновлялась бы ни одна строка.
     */
    private static boolean same(Object portal, Object file) {
        if (portal instanceof Number || file instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(portal))
                        .compareTo(new BigDecimal(String.valueOf(file))) == 0;
            } catch (NumberFormatException ignored) {
                // не числа — сравним как текст
            }
        }
        if (portal instanceof Boolean || file instanceof Boolean) {
            return String.valueOf(portal).equalsIgnoreCase(String.valueOf(file));
        }
        return String.valueOf(portal).trim().equalsIgnoreCase(String.valueOf(file).trim());
    }

    /* ===================== Приведение значений ===================== */

    private Object coerce(Field f, String raw, Map<String, Dictionary> dictionaries,
                          List<Map<String, Object>> issues) {
        switch (f.kind()) {
            case NUMBER -> {
                String normalized = raw.replace(" ", "").replace(',', '.');
                try {
                    BigDecimal number = new BigDecimal(normalized);
                    if (number.signum() < 0) {
                        issues.add(issue(f.catalogId(), "value",
                                "Отрицательное значение здесь не имеет смысла", raw, null));
                        return null;
                    }
                    return number;
                } catch (NumberFormatException e) {
                    issues.add(issue(f.catalogId(), "value",
                            "Это не число. Дробную часть отделяйте запятой или точкой",
                            raw, null));
                    return null;
                }
            }
            case BOOLEAN -> {
                String v = raw.toLowerCase(Locale.ROOT);
                if (Set.of("да", "yes", "1", "истина", "true", "+").contains(v)) {
                    return Boolean.TRUE;
                }
                if (Set.of("нет", "no", "0", "ложь", "false", "-").contains(v)) {
                    return Boolean.FALSE;
                }
                issues.add(issue(f.catalogId(), "value",
                        "Нужно «да» или «нет»", raw, null));
                return null;
            }
            case DICTIONARY -> {
                return resolve(f, raw, dictionaries, issues);
            }
            default -> {
                return raw;
            }
        }
    }

    /**
     * Справочное значение по названию.
     *
     * <p>Новых групп и типов товара импорт не создаёт: незнакомое
     * название — ошибка строки. Создание справочных значений побочным
     * действием — то, о чём потом жалеют: опечатка в одной строке
     * навсегда добавляет клиенту кривую группу товара.
     */
    private Object resolve(Field f, String raw, Map<String, Dictionary> dictionaries,
                           List<Map<String, Object>> issues) {
        Dictionary dictionary = dictionaries.get(f.dictionary());
        if (dictionary == null) {
            issues.add(issue(f.catalogId(), "value",
                    "Справочник для этого поля пуст — обратитесь к оператору", raw, null));
            return null;
        }
        String id = dictionary.byName().get(raw.toLowerCase(Locale.ROOT));
        if (id != null) {
            return id;
        }
        String hint = closest(raw, dictionary.names());
        issues.add(issue(f.catalogId(), "value",
                "Значение «" + raw + "» не найдено в справочнике"
                        + (hint == null ? "" : ". Возможно, «" + hint + "»"),
                raw, null));
        return null;
    }

    /**
     * Похожее значение для подсказки. Без неё человек видит «не найдено»
     * и не понимает, что написал «штук» вместо «шт».
     */
    private static String closest(String raw, List<String> names) {
        String probe = raw.toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(name -> {
                    String n = name.toLowerCase(Locale.ROOT);
                    return n.startsWith(probe) || probe.startsWith(n);
                })
                .min(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    /* ===================== Чтение из базы ===================== */

    /**
     * Справочники в виде «название → идентификатор», по одному разу на файл.
     *
     * <p>Читается один раз намеренно: справочники не меняются по ходу
     * разбора, а обращение к ним на каждую ячейку означало бы под тысячу
     * запросов на файл в сто строк.
     */
    private Map<String, Dictionary> dictionaries() {
        Map<String, Dictionary> out = new LinkedHashMap<>();
        sku.dictionaries().forEach((kind, items) -> {
            Map<String, String> byName = new LinkedHashMap<>();
            Map<String, String> nameById = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String name = String.valueOf(item.get("name")).trim();
                names.add(name);
                byName.putIfAbsent(name.toLowerCase(Locale.ROOT), String.valueOf(item.get("id")));
                nameById.putIfAbsent(String.valueOf(item.get("id")), name);
            }
            out.put(kind, new Dictionary(byName, nameById, names));
        });
        return out;
    }

    /**
     * Один справочник: поиск по названию, обратный поиск по
     * идентификатору (для показа) и список названий для подсказки.
     */
    private record Dictionary(Map<String, String> byName, Map<String, String> nameById,
                              List<String> names) {
    }

    /**
     * Значение в человеческом виде — или null, если показывать нечего
     * нового. Null означает «экран покажет само значение»: для текста
     * и чисел отдельная подпись только раздувала бы хранимый JSON.
     */
    private static String shown(String column, Object value,
                                Map<String, Dictionary> dictionaries) {
        if (value == null) {
            return null;
        }
        String dictionaryKind = SkuTemplateFields.DICTIONARY.get(column);
        if (dictionaryKind != null) {
            Dictionary dictionary = dictionaries.get(dictionaryKind);
            String name = dictionary == null
                    ? null : dictionary.nameById().get(String.valueOf(value));
            return name != null ? name : String.valueOf(value);
        }
        if (value instanceof Boolean b) {
            return b ? "Да" : "Нет";
        }
        return null;
    }

    /** То же, но для текста замечания: там пустое место недопустимо. */
    private static String shownOrSelf(String column, Object value,
                                      Map<String, Dictionary> dictionaries) {
        String name = shown(column, value, dictionaries);
        return name != null ? name : String.valueOf(value);
    }

    /**
     * Ограничения длины текстовых колонок товара.
     *
     * <p>Проверяем до записи: иначе слишком длинное наименование упало бы
     * на вставке невнятной ошибкой базы, да ещё и после того, как человек
     * нажал «Создать».
     */
    private Map<String, Integer> columnLengths() {
        Map<String, Integer> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.sql("""
                        SELECT column_name, character_maximum_length AS len
                        FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = 'sku'
                          AND character_maximum_length IS NOT NULL
                        """).query().listOfRows()) {
            out.put(String.valueOf(row.get("column_name")),
                    ((Number) row.get("len")).intValue());
        }
        return out;
    }

    /**
     * Позиции клиента по коммерческому коду в нижнем регистре.
     *
     * <p>Читается один раз на файл, а не на строку: сто строк — сто
     * запросов, и это при том, что позиций у клиента обычно немного.
     */
    private Map<String, Map<String, Object>> existingByCode() {
        Set<String> columns = new LinkedHashSet<>(SkuColumns.SKU.keySet());
        columns.remove("item_code");
        String list = String.join(", ", columns);

        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> row : jdbc
                .sql("SELECT sku_id::text AS sku_id, is_active, " + list + " FROM dbo.sku")
                .query()
                .listOfRows()) {
            Object code = row.get("code");
            if (code != null) {
                out.put(String.valueOf(code).trim().toLowerCase(Locale.ROOT), row);
            }
        }
        return out;
    }

    private String depositorCode() {
        return jdbc.sql("SELECT code FROM dbo.depositor WHERE depositor_id = :id::uuid")
                .param("id", depositorScope.currentDepositorId())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /* ===================== Запись результата ===================== */

    private String createImport(PortalUser user, String depositorId, String fileName,
                                int size, Layout layout) {
        String used = layout.columns().stream()
                .filter(Column::recognized)
                .map(c -> "{\"header\":" + json(c.header())
                        + ",\"columnId\":" + json(c.columnId()) + "}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String skipped = layout.unrecognized().stream()
                .map(SkuImportService::json)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return jdbc.sql("""
                        INSERT INTO dbo.excel_import
                          (depositor_id, user_id, kind, file_name, file_size,
                           columns_used, columns_skipped, status)
                        VALUES (:dep::uuid, :user::uuid, 'sku', :name::varchar, :size,
                                :used::jsonb, :skipped::jsonb, 'checked')
                        RETURNING import_id::text
                        """)
                .param("dep", depositorId)
                .param("user", user.userId())
                .param("name", fileName)
                .param("size", size)
                .param("used", "[" + used + "]")
                .param("skipped", "[" + skipped + "]")
                .query(String.class)
                .single();
    }

    private void storeRows(String importId, String depositorId,
                           List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            jdbc.sql("""
                            INSERT INTO dbo.excel_import_row
                              (import_id, row_number, depositor_id, row_class, is_valid,
                               row_key, sku_id, cell_values, cell_display, fill_fields)
                            VALUES (:imp::uuid, :num, :dep::uuid, :class::varchar, :valid,
                                    :key::varchar, :sku::uuid, :values::jsonb,
                                    :display::jsonb, :fill::jsonb)
                            """)
                    .param("imp", importId)
                    .param("num", row.get("rowNumber"))
                    .param("dep", depositorId)
                    .param("class", row.get("rowClass"))
                    .param("valid", row.get("isValid"))
                    .param("key", row.get("rowKey"))
                    .param("sku", row.get("skuId"))
                    .param("values", jsonObject(cast(row.get("values"))))
                    .param("display", jsonObject(cast(row.get("display"))))
                    .param("fill", jsonArray(castList(row.get("fill"))))
                    .update();

            for (Map<String, Object> issue : castIssues(row.get("issues"))) {
                jdbc.sql("""
                                INSERT INTO dbo.excel_import_issue
                                  (import_id, row_number, depositor_id, column_id,
                                   kind, message, file_value, portal_value)
                                VALUES (:imp::uuid, :num, :dep::uuid, :col::varchar,
                                        :kind::varchar, :msg::text, :file::text, :portal::text)
                                """)
                        .param("imp", importId)
                        .param("num", row.get("rowNumber"))
                        .param("dep", depositorId)
                        .param("col", issue.get("columnId"))
                        .param("kind", issue.get("kind"))
                        .param("msg", issue.get("message"))
                        .param("file", issue.get("fileValue"))
                        .param("portal", issue.get("portalValue"))
                        .update();
            }
        }
    }

    private void recount(String importId) {
        jdbc.sql("SELECT dbo.excel_import_recount(:imp::uuid)")
                .param("imp", importId)
                .query()
                .listOfRows();
    }

    /* ===================== Чтение результата ===================== */

    /* ===================== Применение ===================== */

    /**
     * Записывает в справочник строки одного класса.
     *
     * <p>Кнопка применяет только свой класс: нажали «Создать новые» —
     * существующие позиции не тронуты. Тела запроса нет нарочно: что
     * записывать, сервер знает из результата проверки, а не из того,
     * что прислал браузер. Иначе подменой запроса можно было бы
     * применить строку, которую проверка забраковала.
     *
     * <p><b>Каждая строка пишется своей транзакцией.</b> По решению
     * заказчика сбой одной строки не отменяет остальные: из 61 позиции
     * 60 создаются, а по одной человек читает причину в той же таблице.
     * Поэтому метод НЕ помечен {@code @Transactional} — одна общая
     * транзакция откатила бы всё при первом же отказе.
     *
     * @param creating true — создавать новые позиции, false — обновлять
     *                 существующие
     */
    public Map<String, Object> apply(PortalUser user, String importId, boolean creating) {
        current.requireExcelUpload("sku");
        TransactionTemplate tx = ownTransaction();

        requireSameSession(user, tx.execute(status -> {
            depositorScope.apply();
            return importHead(importId);
        }));

        List<Integer> numbers = tx.execute(status -> {
            depositorScope.apply();
            return pending(importId, creating);
        });

        int done = 0;
        int failed = 0;
        for (Integer number : numbers) {
            try {
                tx.executeWithoutResult(status -> {
                    depositorScope.apply();
                    writeRow(importId, number, creating);
                });
                done++;
            } catch (RuntimeException e) {
                // Чаще всего — позицию с таким кодом успели создать
                // руками в другом окне. Строка остаётся непримененной,
                // а причина становится её замечанием: человек увидит
                // её там же, где остальные.
                failed++;
                String reason = reason(e);
                tx.executeWithoutResult(status -> {
                    depositorScope.apply();
                    markFailed(importId, number, reason);
                });
            }
        }

        int applied = done;
        tx.executeWithoutResult(status -> {
            depositorScope.apply();
            finish(importId, creating, applied);
        });

        Map<String, Object> out = new LinkedHashMap<>(tx.execute(status -> {
            depositorScope.apply();
            return result(importId);
        }));
        // Сколько записано и сколько не удалось — экрану, чтобы сказать
        // это словами, а не заставлять считать строки глазами.
        out.put("applied", done);
        out.put("failed", failed);
        return out;
    }

    /**
     * Своя транзакция на каждый шаг.
     *
     * <p>{@code REQUIRES_NEW} здесь не ради вложенности, а ради того,
     * чтобы шаги были независимы: откат одного не трогает остальные.
     */
    private TransactionTemplate ownTransaction() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private Map<String, Object> importHead(String importId) {
        return jdbc.sql("""
                        SELECT user_id::text AS user_id, status
                        FROM dbo.excel_import WHERE import_id = :imp::uuid
                        """)
                .param("imp", importId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> Fail.notFound("error.import.not_found_maybe_cancelled"));
    }

    /**
     * Применяет тот же человек и тот же поклажедатель, что начинал.
     *
     * <p>Поклажедателя проверять отдельно не нужно: загрузка читается
     * под RLS, и чужая неотличима от несуществующей. А вот
     * пользователя — нужно: окно могло остаться открытым, пока за тем
     * же компьютером вошёл другой человек, и тогда в аудите товара
     * оказался бы не тот автор.
     */
    private void requireSameSession(PortalUser user, Map<String, Object> head) {
        if (!String.valueOf(head.get("user_id")).equals(user.userId())) {
            throw Fail.conflict("error.import.other_user");
        }
    }

    /** Номера строк, которые ещё можно записать. */
    private List<Integer> pending(String importId, boolean creating) {
        return jdbc.sql("""
                        SELECT row_number FROM dbo.excel_import_row
                        WHERE import_id = :imp::uuid
                          AND row_class = :class::varchar
                          AND is_valid AND NOT applied
                        ORDER BY row_number
                        """)
                .param("imp", importId)
                .param("class", creating ? "new" : "existing")
                .query(Integer.class)
                .list();
    }

    /**
     * Записывает одну строку.
     *
     * <p>Через {@link SkuWriteService} — тем же путём, что ручная
     * правка карточки: те же проверки, тот же аудит, то же RLS.
     * В обход записывать нельзя, иначе импорт станет дыркой в правилах.
     *
     * <p>Строка берётся {@code FOR UPDATE SKIP LOCKED} и с условием
     * {@code NOT applied}: человек мог нажать кнопку дважды, пока ждал
     * ответа. Вторая попытка просто не найдёт строку.
     */
    private void writeRow(String importId, int number, boolean creating) {
        List<Map<String, Object>> locked = jdbc.sql("""
                        SELECT sku_id::text AS sku_id FROM dbo.excel_import_row
                        WHERE import_id = :imp::uuid AND row_number = :num
                          AND is_valid AND NOT applied
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("imp", importId)
                .param("num", number)
                .query()
                .listOfRows();
        if (locked.isEmpty()) {
            return;            // уже применена или занята другим запросом
        }

        Map<String, Object> values = cellValues(importId, number, creating);
        if (values.isEmpty()) {
            return;            // нечего записывать — считаем сделанным
        }
        if (creating) {
            sku.createSku(values);
        } else {
            sku.updateSku(String.valueOf(locked.getFirst().get("sku_id")), values);
        }

        jdbc.sql("""
                        UPDATE dbo.excel_import_row SET applied = true
                        WHERE import_id = :imp::uuid AND row_number = :num
                        """)
                .param("imp", importId)
                .param("num", number)
                .update();
    }

    /**
     * Значения строки для записи.
     *
     * <p>Читаются из базы через {@code jsonb_each_text}, а не разбором
     * JSON в приложении: значения всё равно уходят в запрос с явным
     * приведением типа по колонке (как и значения, пришедшие из
     * браузера), а разбор JSON ради этого завёл бы в сервисе ещё один
     * слой, который может разойтись с базой.
     *
     * <p>Проверка «поле в списке заполняемых» написана через
     * {@code jsonb_exists}, а не оператором {@code ?}: именованные
     * параметры Spring превращаются в {@code ?}, и литеральный
     * {@code ?} в тексте запроса драйвер принял бы за ещё один
     * параметр.
     *
     * <p>При обновлении берутся <b>только поля из {@code fill_fields}</b> —
     * те, что в портале пусты. Отсюда свойство, которое стоит назвать
     * прямо: импорт не может перезаписать уже заполненное. Даже если
     * в {@code cell_values} лежит больше.
     */
    private Map<String, Object> cellValues(String importId, int number, boolean creating) {
        String sql = creating
                ? """
                  SELECT c.key, c.value
                  FROM dbo.excel_import_row r,
                       jsonb_each_text(r.cell_values) c
                  WHERE r.import_id = :imp::uuid AND r.row_number = :num
                  """
                : """
                  SELECT c.key, c.value
                  FROM dbo.excel_import_row r,
                       jsonb_each_text(r.cell_values) c
                  WHERE r.import_id = :imp::uuid AND r.row_number = :num
                    AND jsonb_exists(r.fill_fields, c.key)
                  """;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.sql(sql)
                .param("imp", importId)
                .param("num", number)
                .query()
                .listOfRows()) {
            out.put(String.valueOf(row.get("key")), row.get("value"));
        }
        return out;
    }

    /** Строка не записалась: причина становится её замечанием. */
    private void markFailed(String importId, int number, String reason) {
        jdbc.sql("""
                        INSERT INTO dbo.excel_import_issue
                          (import_id, row_number, depositor_id, column_id, kind, message)
                        SELECT :imp::uuid, :num, r.depositor_id, NULL,
                               'apply'::varchar, :msg::text
                        FROM dbo.excel_import_row r
                        WHERE r.import_id = :imp::uuid AND r.row_number = :num
                        """)
                .param("imp", importId)
                .param("num", number)
                .param("msg", reason)
                .update();
        // Строка перестаёт быть годной: она не записана, и причина
        // у неё теперь есть — то есть это ошибка в том же смысле,
        // что и остальные.
        jdbc.sql("""
                        UPDATE dbo.excel_import_row SET is_valid = false
                        WHERE import_id = :imp::uuid AND row_number = :num
                        """)
                .param("imp", importId)
                .param("num", number)
                .update();
    }

    /** Итоги загрузки: счётчики, состояние, пересчёт сводки. */
    private void finish(String importId, boolean creating, int applied) {
        jdbc.sql("""
                        UPDATE dbo.excel_import
                           SET created_rows = created_rows + :created,
                               updated_rows = updated_rows + :updated,
                               created_at = CASE WHEN :created > 0 THEN now() ELSE created_at END,
                               updated_at = CASE WHEN :updated > 0 THEN now() ELSE updated_at END,
                               status = CASE WHEN :any THEN 'applied' ELSE status END
                         WHERE import_id = :imp::uuid
                        """)
                .param("created", creating ? applied : 0)
                .param("updated", creating ? 0 : applied)
                .param("any", applied > 0)
                .param("imp", importId)
                .update();
        recount(importId);
    }

    /**
     * Причина отказа человеческими словами.
     *
     * <p>Отказ портала («Товар с кодом … уже есть») объясняет сам себя.
     * Всё остальное — ошибка базы или кода, и её текст на экране
     * человеку не поможет; показываем общую причину, подробность
     * остаётся в логе приложения.
     */
    private static String reason(RuntimeException e) {
        if (e instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return "Не удалось записать строку в справочник";
    }

    /** Результат проверки для экрана: сводка, колонки файла и строки. */
    @Transactional(readOnly = true)
    public Map<String, Object> result(String importId) {
        depositorScope.apply();
        Map<String, Object> head = jdbc.sql("""
                        SELECT import_id::text AS "importId", file_name AS "fileName",
                               file_size AS "fileSize", row_count AS "rowCount",
                               to_create AS "toCreate", to_update AS "toUpdate",
                               error_count AS "errorCount",
                               columns_used::text AS "columnsUsedJson",
                               columns_skipped::text AS "columnsSkippedJson",
                               status,
                               to_char(started_at, 'DD.MM.YYYY, HH24:MI') AS "startedAt"
                        FROM dbo.excel_import WHERE import_id = :imp::uuid
                        """)
                .param("imp", importId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> Fail.notFound("error.import.not_found_maybe_cancelled"));

        Map<String, Object> out = new LinkedHashMap<>(head);
        // JSONB отдаём текстом, а экран его разбирает. Иначе наружу
        // уходит объект драйвера PostgreSQL, и в JSON он превращается
        // в {"type":"jsonb","value":"..."} — то есть в мусор. Приводить
        // здесь к структурам значило бы завести разбор JSON в сервисе
        // ради одного экрана.
        out.put("rows", SqlRows.toCamelCase(jdbc.sql("""
                        SELECT row_number, row_class, is_valid, applied, row_key,
                               sku_id,
                               cell_values::text  AS cell_values_json,
                               cell_display::text AS cell_display_json,
                               fill_fields::text  AS fill_fields_json,
                               issues::text       AS issues_json
                        FROM dbo.v_excel_import_rows
                        WHERE import_id = :imp::uuid
                        ORDER BY row_number
                        """)
                .param("imp", importId)
                .query()
                .listOfRows()));
        return out;
    }

    /**
     * Окно проверки закрыто: разобранные строки больше не нужны.
     *
     * <p>Удаляются именно строки, а не загрузка: сама запись и её
     * замечания — это журнал (§8.5.6), и администратору они нужны
     * ещё 90 дней. А {@code cell_values} — промежуточные данные
     * на минуты между проверкой и кнопкой, держать их незачем.
     *
     * <p>Состояние ставится по факту: записали хоть что-то — загрузка
     * «применена», не записали — «отменена». Отдельной кнопки
     * «отменить» у человека нет, он просто закрывает окно, поэтому
     * и спрашивать его не о чем.
     */
    @Transactional
    public void cancel(String importId) {
        depositorScope.apply();
        int n = jdbc.sql("""
                        UPDATE dbo.excel_import
                           SET status = CASE WHEN created_rows + updated_rows > 0
                                             THEN 'applied' ELSE 'cancelled' END
                         WHERE import_id = :imp::uuid
                        """)
                .param("imp", importId)
                .update();
        if (n == 0) {
            throw Fail.notFound("error.import.not_found");
        }
        jdbc.sql("DELETE FROM dbo.excel_import_row WHERE import_id = :imp::uuid")
                .param("imp", importId)
                .update();
    }

    /* ===================== Мелочь ===================== */

    private static Map<String, Object> issue(String columnId, String kind, String message,
                                             String fileValue, String portalValue) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columnId", columnId);
        out.put("kind", kind);
        out.put("message", message);
        out.put("fileValue", fileValue);
        out.put("portalValue", portalValue);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castIssues(Object value) {
        return (List<Map<String, Object>>) value;
    }

    /**
     * Строка в JSON. Своя сборка, а не библиотека: значений мало,
     * и тащить сюда Jackson ради двух полей нечего.
     */
    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String jsonObject(Map<String, Object> values) {
        return "{" + values.entrySet().stream()
                .map(e -> json(e.getKey()) + ":" + jsonValue(e.getValue()))
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "}";
    }

    private static String jsonArray(List<String> values) {
        return "[" + values.stream()
                .map(SkuImportService::json)
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }

    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return json(String.valueOf(value));
    }
}
