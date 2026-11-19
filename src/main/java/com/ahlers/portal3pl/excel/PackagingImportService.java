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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Загрузка упаковок из Excel: разбор, проверка, применение.
 *
 * <p>Устроена как загрузка номенклатуры ({@link SkuImportService}) и
 * стоит на том же {@link ExcelImportSupport}: два признака строки —
 * класс и годность, — правило «заполнить пустое, заполненное не
 * трогать», каждая строка своей транзакцией. Своё здесь — как
 * опознаётся упаковка и что делать с единицей заказа.
 *
 * <p><b>Ключ — пара «товар + код упаковки».</b> Так устроен уникальный
 * индекс {@code packaging_code_uk}: у разных товаров бывают упаковки
 * с одинаковым кодом «BOX», у одного товара — нет.
 * <ul>
 *   <li>товар не найден → строка неопознанна и ошибочна. Импорт упаковок
 *       товары не создаёт: опечатка в коде иначе молча завела бы новый
 *       товар без наименования;</li>
 *   <li>товар в архиве → ошибка: заводить упаковки погашенному незачем;</li>
 *   <li>пары нет → новая упаковка, пара есть → существующая.</li>
 * </ul>
 *
 * <p><b>Единица заказа</b> — признак товара ({@code sku.order_unit_id}),
 * и правило для него то же: заполнить можно, поменять нельзя. «Да» у
 * товара без единицы заказа — назначит; «да» у товара, где выбрана
 * другая, — ошибка с её кодом; два «да» у одного товара в файле —
 * ошибка второй строки. «Нет» ничего не снимает: снять признак с той
 * упаковки, что уже единица заказа, — значит изменить заполненное.
 */
@Service
public class PackagingImportService {

    /** Предел строк — тот же, что заложен в шаблон. */
    public static final int MAX_ROWS = PackagingTemplateService.MAX_ROWS;

    private static final String KIND = "packaging";
    private static final String CODE = "packaging_code";
    private static final String SKU = PackagingTemplateFields.SKU;
    private static final String ORDER_UNIT = PackagingTemplateFields.ORDER_UNIT;
    private static final String VOLUME = PackagingTemplateFields.VOLUME;
    private static final List<String> DIMENSIONS = List.of("length_cm", "width_cm", "height_cm");

    /**
     * Вид замечания, которое строку не портит: объём пересчитан по
     * размерам. Человек его видит, но записи оно не мешает и в ошибки
     * не считается.
     */
    static final String NOTE_RECALCULATED = "recalculated";

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final SkuImportColumns matcher;
    private final SkuWriteService sku;
    private final CurrentUser current;
    private final ExcelImportSupport support;

    public PackagingImportService(JdbcClient jdbc, DepositorScope depositorScope,
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
        current.requireExcelUpload(KIND);
        depositorScope.apply();
        support.requireFile(fileName, content);
        support.cleanup();

        String depositorId = depositorScope.currentDepositorId();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            support.requireOwnFile(wb, fileName);

            Sheet sheet = support.sheetOf(wb, PackagingTemplateService.TABLE, "excel.packaging.sheet");
            Layout layout = matcher.match(support.headers(sheet), depositorId, KIND,
                    PackagingTemplateFields.fields());
            // Без товара или без кода упаковки строку не опознать вовсе.
            // Остальные обязательные поля проверяются построчно: у
            // существующей упаковки коэффициент уже есть в портале.
            if (!layout.has(SKU) || !layout.has(CODE)) {
                throw Fail.badRequest("error.excel.no_packaging_key_columns",
                        "headers", ExcelImportSupport.foundHeaders(layout));
            }

            List<Map<String, Object>> parsed = parseRows(sheet, layout);
            if (parsed.isEmpty()) {
                throw Fail.badRequest("error.excel.no_rows");
            }

            String importId = support.createImport(user, depositorId, KIND, fileName,
                    content.length, layout);
            support.storeRows(importId, depositorId, parsed);
            support.recount(importId);
            return importId;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // Повреждённый файл, не .xlsx внутри, пароль на книге —
            // причина человеку всё равно непонятна.
            throw Fail.badRequest("error.excel.unreadable");
        }
    }

    /** Всё, что нужно разбору, прочитанное из базы один раз на файл. */
    private record Portal(Map<String, Map<String, Object>> products,
                          Map<String, Map<String, Object>> packagings,
                          Map<String, String> codeById,
                          Map<String, Dictionary> dictionaries,
                          Map<String, Integer> lengths) {
    }

    /** Что уже встретилось в файле: повторы ключа и второе «да» у товара. */
    private record Seen(Map<String, Integer> keys, Map<String, Integer> orderUnits) {
    }

    private List<Map<String, Object>> parseRows(Sheet sheet, Layout layout) {
        DataFormatter fmt = new DataFormatter();
        Portal portal = new Portal(products(), packagings(), new HashMap<>(),
                dictionaries(), support.columnLengths("product_packaging"));
        portal.packagings().values().forEach(p -> portal.codeById()
                .put(String.valueOf(p.get("packaging_id")), String.valueOf(p.get(CODE))));
        Seen seen = new Seen(new HashMap<>(), new HashMap<>());

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
            rows.add(parseRow(row, r + 1, layout, fmt, portal, seen));
        }
        return rows;
    }

    /**
     * Разбирает одну строку: приводит значения, собирает замечания,
     * определяет класс и годность.
     */
    private Map<String, Object> parseRow(Row row, int excelRow, Layout layout, DataFormatter fmt,
                                         Portal portal, Seen seen) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> display = new LinkedHashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        Map<String, Object> product = null;
        String productCode = "";

        for (Column c : layout.columns()) {
            if (!c.recognized()) {
                continue;
            }
            ExcelField f = c.field();
            String raw = ExcelImportSupport.text(row, c.index(), fmt);
            if (raw.isEmpty()) {
                continue;            // пусто в файле означает «нет значения»
            }
            if (SKU.equals(f.column())) {
                // Товар ищется не по выпадающему списку шаблона, а по базе:
                // список в файле — снимок на момент скачивания, а вставка
                // готовых строк его обходит. Ищем среди всех товаров,
                // включая архивные, — чтобы сказать «в архиве», а не
                // «не найдено».
                productCode = raw;
                product = portal.products().get(raw.toLowerCase(Locale.ROOT));
                if (product == null) {
                    issues.add(issue(f.catalogId(), "value", "error.packaging_import.unknown_item",
                            raw, null, "code", raw));
                } else {
                    values.put(SKU, product.get("sku_id"));
                    display.put(SKU, product.get("code"));
                }
                continue;
            }
            Object value = ExcelImportSupport.coerce(f, raw, portal.dictionaries(), issues);
            if (value == null) {
                continue;            // не разобрали — замечание уже записано
            }
            Integer limit = portal.lengths().get(f.column());
            if (limit != null && String.valueOf(value).length() > limit) {
                issues.add(issue(f.catalogId(), "value",
                        "error.catalog_import.too_long", raw, null, "limit", limit));
                continue;
            }
            values.put(f.column(), value);
            String asShown = shown(f, value, portal.dictionaries());
            if (asShown != null) {
                display.put(f.column(), asShown);
            }
        }

        String code = values.get(CODE) == null ? "" : String.valueOf(values.get(CODE)).trim();
        if (productCode.isEmpty()) {
            issues.add(issue(catalogId(SKU), "required", "error.packaging_import.item_required", null, null));
        }
        if (code.isEmpty()) {
            issues.add(issue(catalogId(CODE), "required", "error.packaging_import.code_required", null, null));
        }

        String rowClass = "unknown";
        Map<String, Object> existing = null;
        String key = productCode.isEmpty() || code.isEmpty() ? null : productCode + " / " + code;

        if (product != null && !code.isEmpty()) {
            String skuId = String.valueOf(product.get("sku_id"));
            Integer before = seen.keys().putIfAbsent(
                    (skuId + "|" + code).toLowerCase(Locale.ROOT), excelRow);
            if (before != null) {
                issues.add(issue(catalogId(CODE), "duplicate", "error.packaging_import.code_duplicate",
                        code, null, "row", before));
            }
            if (!Boolean.TRUE.equals(product.get("is_active"))) {
                issues.add(issue(null, "archived", "error.packaging_import.item_archived", null, null));
            }
            existing = portal.packagings().get((skuId + "|" + code).toLowerCase(Locale.ROOT));
            rowClass = existing == null ? "new" : "existing";
        }

        List<String> fill = new ArrayList<>();
        BigDecimal volume = "unknown".equals(rowClass)
                ? null : volumeByDimensions(values, display, existing, issues);
        if ("new".equals(rowClass)) {
            // Не разобранное значение («0», «двенадцать») уже получило своё
            // замечание; «не заполнено» поверх него было бы неправдой.
            if (values.get("coefficient") == null
                    && !ExcelImportSupport.hasIssue(issues, catalogId("coefficient"))) {
                issues.add(issue(catalogId("coefficient"), "required",
                        "error.catalog_import.required", null, null));
            }
            orderUnit(values, product, null, fill, issues, portal, seen, excelRow);
        } else if ("existing".equals(rowClass)) {
            compare(values, existing, fill, issues, portal.dictionaries(), volume != null);
            orderUnit(values, product, existing, fill, issues, portal, seen, excelRow);
            if (volume != null && !fill.isEmpty()) {
                portalVolume(volume, existing, fill, issues);
            }
            if (fill.isEmpty() && errorsOnly(issues).isEmpty()) {
                // Строка есть, а делать по ней нечего. По решению заказчика
                // это тоже ошибка: человек ждал от строки действия.
                issues.add(issue(null, "nothing",
                        "error.catalog_import.nothing",
                        null, null));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rowNumber", excelRow);
        out.put("rowClass", rowClass);
        // Замечание «объём пересчитан» строку не портит
        out.put("isValid", errorsOnly(issues).isEmpty());
        out.put("rowKey", key);
        out.put("skuId", product == null ? null : product.get("sku_id"));
        out.put("packagingId", existing == null ? null : existing.get("packaging_id"));
        out.put("values", values);
        out.put("display", display);
        out.put("fill", fill);
        out.put("issues", issues);
        return out;
    }

    /**
     * Сравнение с существующей упаковкой — то же правило, что у товаров:
     * заполняем только пустое, а заполненное поле с другим значением
     * делает ошибочной всю строку. <b>Импорт не может перезаписать
     * данные, которые в портале уже есть.</b>
     *
     * <p>Товар и код упаковки не сравниваются — по ним упаковка найдена.
     * Единица заказа — отдельно, в {@link #orderUnit}: она живёт у товара.
     */
    private void compare(Map<String, Object> values, Map<String, Object> portal,
                         List<String> fill, List<Map<String, Object>> issues,
                         Map<String, Dictionary> dictionaries, boolean volumeComputed) {
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String column = e.getKey();
            if (SKU.equals(column) || CODE.equals(column) || ORDER_UNIT.equals(column)) {
                continue;
            }
            if (volumeComputed && VOLUME.equals(column)) {
                continue;            // объём следует за размерами — см. portalVolume
            }
            Object was = portal.get(column);
            if (was == null || String.valueOf(was).isBlank()) {
                fill.add(column);
                continue;
            }
            if (!ExcelImportSupport.same(was, e.getValue())) {
                ExcelField f = PackagingTemplateFields.fields().get(column);
                issues.add(issue(catalogId(column), "conflict",
                        "error.catalog_import.conflict",
                        shownOrSelf(f, e.getValue(), dictionaries),
                        shownOrSelf(f, was, dictionaries)));
            }
        }
    }

    /**
     * Единица заказа: заполнить можно, поменять нельзя.
     *
     * @param existing найденная упаковка или null, если она новая
     */
    private void orderUnit(Map<String, Object> values, Map<String, Object> product,
                           Map<String, Object> existing, List<String> fill,
                           List<Map<String, Object>> issues, Portal portal, Seen seen,
                           int excelRow) {
        Object flag = values.get(ORDER_UNIT);
        if (flag == null || product == null) {
            return;
        }
        String column = catalogId(ORDER_UNIT);
        Object chosen = product.get("order_unit_id");
        String self = existing == null ? null : String.valueOf(existing.get("packaging_id"));
        boolean isSelf = chosen != null && String.valueOf(chosen).equals(self);

        if (Boolean.FALSE.equals(flag)) {
            if (isSelf) {
                issues.add(issue(column, "conflict", "error.packaging_import.order_unit_locked",
                        support.yesNo(false), support.yesNo(true)));
            }
            return;
        }

        Integer before = seen.orderUnits().putIfAbsent(
                String.valueOf(product.get("sku_id")), excelRow);
        if (before != null) {
            issues.add(issue(column, "conflict", "error.packaging_import.order_unit_twice",
                    support.yesNo(true), null, "row", before));
            return;
        }
        if (isSelf) {
            return;                  // уже так — делать нечего
        }
        if (chosen != null) {
            String taken = portal.codeById().getOrDefault(String.valueOf(chosen), "—");
            issues.add(issue(column, "conflict", "error.packaging_import.order_unit_taken",
                    support.yesNo(true), taken, "code", taken));
            return;
        }
        if (existing != null) {
            fill.add(ORDER_UNIT);
        }
    }

    /* ===================== Объём по размерам ===================== */

    /**
     * Объём, если известны все три размера: Д × Ш × В (см) / 1 000 000,
     * пять знаков, как в колонке базы. То же правило держит триггер
     * {@code product_packaging_volume} (V82) — здесь оно повторено, чтобы
     * окно проверки показало то, что действительно запишется.
     *
     * <p>Размер берётся из файла, а если в файле его нет — из портала:
     * у существующей упаковки файл может принести только недостающую
     * высоту.
     *
     * <p>Если в файле стоял другой объём, он заменяется посчитанным,
     * а строка получает замечание — оно не мешает записи, но подсветит
     * ошибку вроде миллиметров вместо сантиметров.
     *
     * @return посчитанный объём или null, если какого-то размера нет
     */
    private static BigDecimal volumeByDimensions(Map<String, Object> values,
                                                 Map<String, Object> display,
                                                 Map<String, Object> existing,
                                                 List<Map<String, Object>> issues) {
        BigDecimal product = BigDecimal.ONE;
        for (String d : DIMENSIONS) {
            Object v = values.get(d);
            if (v == null && existing != null) {
                v = existing.get(d);
            }
            BigDecimal n = number(v);
            if (n == null || n.signum() <= 0) {
                return null;
            }
            product = product.multiply(n);
        }
        BigDecimal volume = volumeOf(product);
        Object inFile = values.get(VOLUME);
        if (inFile != null && !ExcelImportSupport.same(volume, inFile)) {
            issues.add(issue(catalogId(VOLUME), NOTE_RECALCULATED, "error.packaging_import.volume_from_file",
                    plain(inFile), null, "file", plain(inFile), "volume", plain(volume)));
        }
        values.put(VOLUME, volume);
        display.put(VOLUME, plain(volume));
        return volume;
    }

    /**
     * Существующая упаковка меняется — значит, триггер пересчитает её
     * объём. Если посчитанный объём расходится с тем, что в портале,
     * это надо показать заранее: иначе запись молча поменяла бы
     * заполненное поле.
     */
    private static void portalVolume(BigDecimal volume, Map<String, Object> existing,
                                     List<String> fill, List<Map<String, Object>> issues) {
        Object was = existing.get(VOLUME);
        if (was != null && ExcelImportSupport.same(was, volume)) {
            return;
        }
        fill.add(VOLUME);
        if (was != null && !ExcelImportSupport.hasIssue(issues, catalogId(VOLUME))) {
            issues.add(issue(catalogId(VOLUME), NOTE_RECALCULATED, "error.packaging_import.volume_from_portal",
                    plain(volume), plain(was), "portal", plain(was), "volume", plain(volume)));
        }
    }

    /** Произведение размеров в см³ → м³ с пятью знаками. */
    static BigDecimal volumeOf(BigDecimal cubicCm) {
        return cubicCm.divide(BigDecimal.valueOf(1_000_000), 5, RoundingMode.HALF_UP);
    }

    private static BigDecimal number(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String plain(Object v) {
        BigDecimal n = number(v);
        return n == null ? String.valueOf(v) : n.stripTrailingZeros().toPlainString();
    }

    /** Замечания, которые делают строку ошибочной, — без пометок о пересчёте. */
    private static List<Map<String, Object>> errorsOnly(List<Map<String, Object>> issues) {
        return issues.stream().filter(i -> !NOTE_RECALCULATED.equals(i.get("kind"))).toList();
    }

    /* ===================== Чтение из базы ===================== */

    /**
     * Товары клиента по коммерческому коду в нижнем регистре — все,
     * включая архивные. Под RLS: чужих не будет.
     */
    private Map<String, Map<String, Object>> products() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.sql("""
                        SELECT sku_id::text AS sku_id, code, is_active,
                               order_unit_id::text AS order_unit_id
                        FROM dbo.sku
                        WHERE code IS NOT NULL
                        """).query().listOfRows()) {
            out.put(String.valueOf(row.get("code")).trim().toLowerCase(Locale.ROOT), row);
        }
        return out;
    }

    /** Упаковки клиента по паре «товар | код упаковки» в нижнем регистре. */
    private Map<String, Map<String, Object>> packagings() {
        String list = String.join(", ", SkuColumns.PACKAGING.keySet());
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> row : jdbc
                .sql("SELECT packaging_id::text AS packaging_id, sku_id::text AS sku_id, "
                        + list + " FROM dbo.product_packaging"
                        + " WHERE sku_id IS NOT NULL AND packaging_code IS NOT NULL")
                .query()
                .listOfRows()) {
            String key = row.get("sku_id") + "|" + String.valueOf(row.get(CODE)).trim();
            out.put(key.toLowerCase(Locale.ROOT), row);
        }
        return out;
    }

    /** Справочники «название → идентификатор» — те же, что у карточки товара. */
    private Map<String, Dictionary> dictionaries() {
        Map<String, Dictionary> out = new LinkedHashMap<>();
        sku.dictionaries().forEach((kind, items) -> out.put(kind, Dictionary.of(items)));
        return out;
    }

    /**
     * Значение в человеческом виде — или null, если экрану хватит
     * самого значения (текст и числа).
     */
    private String shown(ExcelField f, Object value, Map<String, Dictionary> dictionaries) {
        if (value == null) {
            return null;
        }
        if (f != null && f.kind() == ExcelField.Kind.DICTIONARY) {
            Dictionary dictionary = dictionaries.get(f.dictionary());
            String name = dictionary == null
                    ? null : dictionary.nameById().get(String.valueOf(value));
            return name != null ? name : String.valueOf(value);
        }
        if (value instanceof Boolean b) {
            return support.yesNo(b);
        }
        return null;
    }

    private String shownOrSelf(ExcelField f, Object value,
                                      Map<String, Dictionary> dictionaries) {
        String name = shown(f, value, dictionaries);
        return name != null ? name : String.valueOf(value);
    }

    private static String catalogId(String column) {
        return PackagingTemplateFields.catalogId(column);
    }

    private static Map<String, Object> issue(String columnId, String kind, String messageKey,
                                             String fileValue, String portalValue, Object... params) {
        return ExcelImportSupport.issue(columnId, kind, messageKey, fileValue, portalValue, params);
    }

    /* ===================== Применение ===================== */

    /**
     * Записывает строки одного класса — через {@link SkuWriteService},
     * тем же путём, что правка упаковки в карточке товара.
     *
     * <p>Единица заказа назначается после записи упаковки и в той же
     * транзакции строки: упаковка без признака, когда в файле стояло
     * «да», — это не запись, а половина записи.
     *
     * @param creating true — создавать новые упаковки, false — обновлять
     *                 существующие
     */
    public Map<String, Object> apply(PortalUser user, String importId, boolean creating) {
        current.requireExcelUpload(KIND);
        return support.apply(user, importId, KIND, creating, (ids, values) -> {
            boolean makeOrderUnit = "true".equalsIgnoreCase(String.valueOf(values.get(ORDER_UNIT)));
            Map<String, Object> fields = new LinkedHashMap<>(values);
            fields.remove(ORDER_UNIT);
            fields.remove(SKU);

            String skuId = String.valueOf(ids.get("sku_id"));
            String packagingId;
            if (creating) {
                sku.createPackaging(skuId, fields);
                packagingId = packagingOf(skuId, String.valueOf(values.get(CODE)));
            } else {
                packagingId = String.valueOf(ids.get("packaging_id"));
                if (!fields.isEmpty()) {
                    sku.updatePackaging(packagingId, fields);
                }
            }
            if (makeOrderUnit) {
                requireOrderUnitFree(skuId, packagingId);
                sku.setDefaultPackaging(packagingId);
            }
        });
    }

    /** Только что созданная упаковка — по товару и коду. */
    private String packagingOf(String skuId, String code) {
        return jdbc.sql("""
                        SELECT packaging_id::text FROM dbo.product_packaging
                        WHERE sku_id = :sku::uuid AND lower(packaging_code) = lower(:code::text)
                        """)
                .param("sku", skuId)
                .param("code", code)
                .query(String.class)
                .single();
    }

    /**
     * Единица заказа не занята — проверено заново, при записи.
     *
     * <p>Между проверкой файла и кнопкой «Создать» единицу заказа могли
     * выбрать в карточке товара. Назначить её поверх значило бы молча
     * изменить заполненное — ровно то, чего импорт не делает никогда.
     */
    private void requireOrderUnitFree(String skuId, String packagingId) {
        // Списком, а не optional(): значение законно бывает NULL,
        // и одиночная выборка спотыкалась бы о пустую ячейку.
        List<String> found = jdbc.sql(
                        "SELECT order_unit_id::text FROM dbo.sku WHERE sku_id = :id::uuid")
                .param("id", skuId)
                .query(String.class)
                .list();
        String chosen = found.isEmpty() ? null : found.getFirst();
        if (chosen != null && !chosen.equals(packagingId)) {
            throw Fail.conflict("error.packaging.order_unit_taken");
        }
    }

    /** Результат проверки для экрана: сводка, колонки файла и строки. */
    @Transactional(readOnly = true)
    public Map<String, Object> result(String importId) {
        depositorScope.apply();
        support.requireKind(importId, KIND);
        return support.result(importId);
    }

    /** Окно проверки закрыто: разобранные строки больше не нужны. */
    @Transactional
    public void cancel(String importId) {
        depositorScope.apply();
        support.requireKind(importId, KIND);
        support.cancel(importId);
    }
}
