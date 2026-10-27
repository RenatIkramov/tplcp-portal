package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuColumns;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.excel.ExcelImportSupport.Dictionary;
import com.ahlers.portal3pl.excel.SkuImportColumns.Column;
import com.ahlers.portal3pl.excel.SkuImportColumns.Layout;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
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
 *
 * <p>Общее с загрузкой упаковок — файл, хранение строк, применение
 * по строкам, отмена — живёт в {@link ExcelImportSupport}. Здесь только
 * то, что знает про товар: как опознать позицию и чем её записать.
 */
@Service
public class SkuImportService {

    /**
     * Больше портал не примет. Предел тот же, что заложен в шаблон,
     * и берётся оттуда же: два числа разошлись бы при первой правке.
     */
    public static final int MAX_ROWS = SkuTemplateService.MAX_ROWS;

    /** Больше 2 МБ для ста строк номенклатуры быть не может. */
    public static final int MAX_BYTES = ExcelImportSupport.MAX_BYTES;

    private static final String SHEET = "Номенклатура";

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final SkuImportColumns matcher;
    private final SkuWriteService sku;
    private final CurrentUser current;
    private final ExcelImportSupport support;

    public SkuImportService(JdbcClient jdbc, DepositorScope depositorScope,
                            SkuImportColumns matcher, SkuWriteService sku,
                            CurrentUser current, ExcelImportSupport support) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.matcher = matcher;
        this.sku = sku;
        this.current = current;
        this.support = support;
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
        support.requireFile(fileName, content);
        support.cleanup();

        String depositorId = depositorScope.currentDepositorId();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            support.requireOwnFile(wb, fileName);

            Sheet sheet = support.sheetOf(wb, SHEET);
            List<String> headers = support.headers(sheet);
            Layout layout = matcher.match(headers, depositorId);
            requireKeyColumns(layout);

            List<Map<String, Object>> parsed = parseRows(sheet, layout);
            if (parsed.isEmpty()) {
                throw Fail.badRequest("error.excel.no_rows");
            }

            String importId = support.createImport(user, depositorId, "sku", fileName,
                    content.length, layout);
            support.storeRows(importId, depositorId, parsed);
            support.recount(importId);
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

    /**
     * Без коммерческого кода файл разбирать бессмысленно: по нему
     * строка опознаётся. Остальные обязательные поля проверяются
     * построчно — у существующей позиции они уже заполнены в портале.
     */
    private void requireKeyColumns(Layout layout) {
        if (!layout.has("code")) {
            throw Fail.badRequest("error.excel.no_code_column",
                    "headers", ExcelImportSupport.foundHeaders(layout));
        }
    }

    /* ===================== Строки ===================== */

    private List<Map<String, Object>> parseRows(Sheet sheet, Layout layout) {
        DataFormatter fmt = new DataFormatter();
        Map<String, Dictionary> dictionaries = dictionaries();
        Map<String, Integer> lengths = support.columnLengths("sku");
        Map<String, Map<String, Object>> existing = existingByCode();
        Map<String, Integer> seenCode = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        int first = sheet.getFirstRowNum() + 1;
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelImportSupport.blank(row, layout, fmt)) {
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
            ExcelField f = c.field();
            String raw = ExcelImportSupport.text(row, c.index(), fmt);
            if (raw.isEmpty()) {
                continue;            // пусто в файле означает «нет значения»
            }
            Object value = ExcelImportSupport.coerce(f, raw, dictionaries, issues);
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
        out.put("packagingId", null);
        out.put("values", values);
        out.put("display", display);
        out.put("fill", fill);
        out.put("issues", issues);
        return out;
    }

    /**
     * Обязательные поля нужны только новой позиции: у существующей они уже есть.
     *
     * <p>Поле, значение которого стоит в файле, но не разобралось
     * («штук» вместо «шт»), уже получило своё замечание. Второе —
     * «не заполнено» — было бы неправдой: ячейка заполнена.
     */
    private void checkRequired(Map<String, Object> values, List<Map<String, Object>> issues) {
        for (String column : SkuColumns.SKU_REQUIRED) {
            if (values.get(column) == null
                    && !ExcelImportSupport.hasIssue(issues, SkuTemplateFields.catalogId(column))) {
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
            if (!ExcelImportSupport.same(was, e.getValue())) {
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
        sku.dictionaries().forEach((kind, items) -> out.put(kind, Dictionary.of(items)));
        return out;
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

    /* ===================== Применение ===================== */

    /**
     * Записывает в справочник строки одного класса — через
     * {@link SkuWriteService}, тем же путём, что ручная правка карточки:
     * те же проверки, тот же аудит, то же RLS. В обход записывать нельзя,
     * иначе импорт станет дыркой в правилах.
     *
     * <p>Как именно идут строки — каждая своей транзакцией, со своим
     * замечанием при отказе, — описано в {@link ExcelImportSupport#apply}.
     *
     * @param creating true — создавать новые позиции, false — обновлять
     *                 существующие
     */
    public Map<String, Object> apply(PortalUser user, String importId, boolean creating) {
        current.requireExcelUpload("sku");
        return support.apply(user, importId, "sku", creating, (ids, values) -> {
            if (creating) {
                sku.createSku(values);
            } else {
                sku.updateSku(String.valueOf(ids.get("sku_id")), values);
            }
        });
    }

    /** Результат проверки для экрана: сводка, колонки файла и строки. */
    @Transactional(readOnly = true)
    public Map<String, Object> result(String importId) {
        depositorScope.apply();
        support.requireKind(importId, "sku");
        return support.result(importId);
    }

    /** Окно проверки закрыто: разобранные строки больше не нужны. */
    @Transactional
    public void cancel(String importId) {
        depositorScope.apply();
        support.requireKind(importId, "sku");
        support.cancel(importId);
    }

    private static Map<String, Object> issue(String columnId, String kind, String message,
                                             String fileValue, String portalValue) {
        return ExcelImportSupport.issue(columnId, kind, message, fileValue, portalValue);
    }
}
