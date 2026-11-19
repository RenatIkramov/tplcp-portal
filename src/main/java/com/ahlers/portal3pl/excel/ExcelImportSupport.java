package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.EnumCodes;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.MsgRef;
import com.ahlers.portal3pl.common.PortalException;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.excel.SkuImportColumns.Column;
import com.ahlers.portal3pl.excel.SkuImportColumns.Layout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Общее у всех загрузок из Excel: файл, строки, хранение результата,
 * применение по строкам, отмена.
 *
 * <p>Товары и упаковки отличаются тем, что лежит в строке и как она
 * пишется в справочник. Всё остальное — проверка, что файл .xlsx и не
 * для другого клиента, чтение шапки, хранение разобранных строк,
 * применение каждой строки своей транзакцией, двойной щелчок, журнал —
 * одно и то же и живёт здесь. Своей копии у каждой загрузки нет
 * нарочно: копия расходится с оригиналом на первой же правке, и обычно
 * это обнаруживает не проверка, а клиент.
 *
 * <p>Объяснение каждого правила оставлено при нём, как было в
 * {@code SkuImportService}, откуда этот код вынесен без изменений.
 */
@Component
public class ExcelImportSupport {

    /** Больше 2 МБ для ста строк справочника быть не может. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final PlatformTransactionManager txManager;
    private final ActorText texts;

    public ExcelImportSupport(JdbcClient jdbc, DepositorScope depositorScope,
                              PlatformTransactionManager txManager, ActorText texts) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.txManager = txManager;
        this.texts = texts;
    }

    /** «Да» / «Нет» на языке пользователя — так значение видно в окне проверки. */
    String yesNo(boolean value) {
        return texts.of(value ? "common.value.yes" : "common.value.no");
    }

    /* ===================== Файл ===================== */

    void requireFile(String fileName, byte[] content) {
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
     * Чистка висит на начале новой загрузки, а не на расписании:
     * отдельного планировщика в портале пока нет, а момент подходящий —
     * человек всё равно ждёт разбора файла. Журнал старше 90 дней
     * удаляется, забытые разобранные строки — старше недели (решение
     * заказчика).
     */
    void cleanup() {
        jdbc.sql("SELECT dbo.excel_import_cleanup()").query().listOfRows();
    }

    /**
     * Файл, собранный для другого клиента, принимать нельзя.
     *
     * <p>Это главный опасный исход всей затеи: менеджер скачал шаблон
     * для одного клиента, переключился на другого и загрузил тот файл.
     * Записи ушли бы не тому — ровно то, от чего §6.4 защищает выбором
     * поклажедателя.
     *
     * <p>Несовпадение — отказ, отсутствие признака — не препятствие:
     * человек мог собрать файл сам. Отсутствие ничего не доказывает,
     * а найденное несовпадение доказывает.
     */
    void requireOwnFile(Workbook wb, String fileName) {
        String depositorCode = depositorCode();
        if (depositorCode == null || depositorCode.isBlank()) {
            return;
        }
        String marker = ExcelTemplateWriter.markerOf(wb);
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

    private String depositorCode() {
        return jdbc.sql("SELECT code FROM dbo.depositor WHERE depositor_id = :id::uuid")
                .param("id", depositorScope.currentDepositorId())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /**
     * Лист с данными загрузки (V102): тот, где лежит умная таблица
     * шаблона ({@code Items}, {@code Packaging}), — её имя не переводится,
     * а имя листа с V102 на языке скачавшего. Нет таблицы — лист с именем
     * на языке пользователя; нет и его — первый видимый непустой: так
     * читаются и файлы до V102, и собранные человеком без шаблона.
     */
    Sheet sheetOf(Workbook wb, String tableName, String sheetKey) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetAt(i) instanceof org.apache.poi.xssf.usermodel.XSSFSheet x
                    && x.getTables().stream().anyMatch(t -> tableName.equalsIgnoreCase(t.getName()))) {
                return x;
            }
        }
        Sheet sheet = wb.getSheet(texts.of(sheetKey));
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

    List<String> headers(Sheet sheet) {
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

    /** Перечень найденных заголовков — для отказа «нет ключевого столбца». */
    static String foundHeaders(Layout layout) {
        return layout.columns().stream()
                .map(Column::header)
                .filter(h -> h != null && !h.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");          // ни одного: прочерк понятен на любом языке
    }

    static boolean blank(Row row, Layout layout, DataFormatter fmt) {
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

    /**
     * Текст ячейки без пробелов по краям — и без неразрывных.
     *
     * <p>Excel и копирование из 1С, браузера, писем приносят неразрывный
     * пробел (U+00A0) и узкий неразрывный (U+202F — им русская локаль
     * разделяет разряды: «1 234»). {@code trim()} их не снимает: код
     * «ABC-1» с таким пробелом в конце не находился в справочнике,
     * а число «1 234» не читалось.
     *
     * <p>Прежде здесь стояло {@code replace(' ', ' ')} — оба аргумента
     * обычные пробелы, замена ничего не делала (нашёл анализатор IDE:
     * «Arguments of replace are the same»). Неразрывный пробел при
     * какой-то правке файла превратился в обычный. Поэтому теперь
     * символы записаны кодами: глазом их не отличить.
     */
    static String text(Row row, int index, DataFormatter fmt) {
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return fmt.formatCellValue(cell).replace('\u00a0', ' ').replace('\u202f', ' ').trim();
    }

    /* ===================== Значения ===================== */

    /**
     * Один справочник: поиск по названию, обратный поиск по
     * идентификатору (для показа) и список названий для подсказки.
     */
    record Dictionary(Map<String, String> byName, Map<String, String> nameById,
                      List<String> names) {

        /**
         * Из строк {@code {id, name}}. Названия сравниваются без учёта
         * регистра: «шт» и «ШТ» — одна единица измерения.
         */
        static Dictionary of(List<Map<String, Object>> items) {
            Map<String, String> byName = new LinkedHashMap<>();
            Map<String, String> nameById = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String name = String.valueOf(item.get("name")).trim();
                names.add(name);
                byName.putIfAbsent(name.toLowerCase(Locale.ROOT), String.valueOf(item.get("id")));
                nameById.putIfAbsent(String.valueOf(item.get("id")), name);
                // Другие написания того же значения — на других языках (V86).
                // Файл, скачанный по-английски, загружает человек с русским
                // порталом: «pcs» должно найтись так же, как «шт».
                if (item.get("aliases") instanceof List<?> aliases) {
                    for (Object alias : aliases) {
                        String a = String.valueOf(alias).trim();
                        if (!a.isEmpty()) {
                            byName.putIfAbsent(a.toLowerCase(Locale.ROOT), String.valueOf(item.get("id")));
                        }
                    }
                }
            }
            return new Dictionary(byName, nameById, names);
        }
    }

    /**
     * Приводит значение ячейки к типу поля.
     *
     * @return значение или null, если разобрать не удалось — замечание
     *         тогда уже записано в {@code issues}
     */
    static Object coerce(ExcelField f, String raw, Map<String, Dictionary> dictionaries,
                         List<Map<String, Object>> issues) {
        switch (f.kind()) {
            case NUMBER -> {
                String normalized = raw.replace(" ", "").replace(',', '.');
                try {
                    BigDecimal number = new BigDecimal(normalized);
                    if (number.signum() < 0) {
                        issues.add(issue(f.catalogId(), "value",
                                "error.catalog_import.negative", raw, null));
                        return null;
                    }
                    if (f.positive() && number.signum() == 0) {
                        issues.add(issue(f.catalogId(), "value",
                                "error.catalog_import.not_positive", raw, null));
                        return null;
                    }
                    return number;
                } catch (NumberFormatException e) {
                    issues.add(issue(f.catalogId(), "value",
                            "error.catalog_import.not_number", raw, null));
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
                        "error.catalog_import.not_boolean", raw, null));
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
     * <p>Новых справочных значений импорт не создаёт: незнакомое
     * название — ошибка строки. Создание справочных значений побочным
     * действием — то, о чём потом жалеют: опечатка в одной строке
     * навсегда добавляет клиенту кривую группу товара.
     */
    private static Object resolve(ExcelField f, String raw, Map<String, Dictionary> dictionaries,
                                  List<Map<String, Object>> issues) {
        Dictionary dictionary = dictionaries.get(f.dictionary());
        if (dictionary == null) {
            issues.add(issue(f.catalogId(), "value",
                    "error.catalog_import.dictionary_empty", raw, null));
            return null;
        }
        String id = dictionary.byName().get(raw.toLowerCase(Locale.ROOT));
        if (id != null) {
            return id;
        }
        String hint = closest(raw, dictionary.names());
        issues.add(hint == null
                ? issue(f.catalogId(), "value", "error.catalog_import.not_in_dictionary", raw, null,
                        "value", raw)
                : issue(f.catalogId(), "value", "error.catalog_import.not_in_dictionary_hint", raw, null,
                        "value", raw, "hint", hint));
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

    /**
     * Сравнение после приведения типа, а не по тексту.
     *
     * <p>{@code 1,5} и {@code 1.50} равны как числа, «шт» и «ШТ»
     * разрешаются в одну единицу измерения. Иначе правило давало бы
     * расхождения на пустом месте и не обновлялась бы ни одна строка.
     */
    static boolean same(Object portal, Object file) {
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

    /**
     * Ограничения длины текстовых колонок таблицы.
     *
     * <p>Проверяем до записи: иначе слишком длинное значение упало бы
     * на вставке невнятной ошибкой базы, да ещё и после того, как человек
     * нажал «Создать».
     */
    Map<String, Integer> columnLengths(String table) {
        Map<String, Integer> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.sql("""
                        SELECT column_name, character_maximum_length AS len
                        FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = :table::text
                          AND character_maximum_length IS NOT NULL
                        """)
                .param("table", table)
                .query().listOfRows()) {
            out.put(String.valueOf(row.get("column_name")),
                    ((Number) row.get("len")).intValue());
        }
        return out;
    }

    /* ===================== Хранение результата ===================== */

    /**
     * Запись загрузки.
     *
     * @param kind что грузим: {@code sku} или {@code packaging}
     */
    String createImport(PortalUser user, String depositorId, String kind, String fileName,
                        int size, Layout layout) {
        String used = layout.columns().stream()
                .filter(Column::recognized)
                .map(c -> "{\"header\":" + json(c.header())
                        + ",\"columnId\":" + json(c.columnId()) + "}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String skipped = layout.unrecognized().stream()
                .map(ExcelImportSupport::json)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return jdbc.sql("""
                        INSERT INTO dbo.excel_import
                          (depositor_id, user_id, kind, file_name, file_size,
                           columns_used, columns_skipped, status)
                        VALUES (:dep::uuid, :user::uuid, :kind::varchar, :name::varchar, :size,
                                :used::jsonb, :skipped::jsonb, :checked)
                        RETURNING import_id::text
                        """)
                .param("dep", depositorId)
                .param("user", user.userId())
                .param("kind", EnumCodes.ImportKind.of(kind))
                .param("checked", EnumCodes.ImportStatus.CHECKED)
                .param("name", fileName)
                .param("size", size)
                .param("used", "[" + used + "]")
                .param("skipped", "[" + skipped + "]")
                .query(String.class)
                .single();
    }

    /**
     * Разобранные строки и их замечания.
     *
     * <p>Строка — карта с ключами {@code rowNumber, rowClass, isValid,
     * rowKey, skuId, packagingId, values, display, fill, issues}.
     * {@code packagingId} есть только у упаковок; у товаров он пуст.
     */
    void storeRows(String importId, String depositorId, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            jdbc.sql("""
                            INSERT INTO dbo.excel_import_row
                              (import_id, row_number, depositor_id, row_class, is_valid,
                               row_key, sku_id, packaging_id, cell_values, cell_display,
                               fill_fields)
                            VALUES (:imp::uuid, :num, :dep::uuid, :class::varchar, :valid,
                                    :key::varchar, :sku::uuid, :pack::uuid, :values::jsonb,
                                    :display::jsonb, :fill::jsonb)
                            """)
                    .param("imp", importId)
                    .param("num", row.get("rowNumber"))
                    .param("dep", depositorId)
                    .param("class", row.get("rowClass"))
                    .param("valid", row.get("isValid"))
                    .param("key", row.get("rowKey"))
                    .param("sku", row.get("skuId"))
                    .param("pack", row.get("packagingId"))
                    .param("values", jsonObject(cast(row.get("values"))))
                    .param("display", jsonObject(cast(row.get("display"))))
                    .param("fill", jsonArray(castList(row.get("fill"))))
                    .update();

            // Причина — ключом и подстановками (V102): окно проверки и журнал
            // собирают фразу на языке того, кто смотрит. Текст в message —
            // на языке загрузившего, запасной.
            for (Map<String, Object> issue : castIssues(row.get("issues"))) {
                String key = String.valueOf(issue.get("messageKey"));
                Map<String, Object> params = cast(issue.get("messageParams"));
                jdbc.sql("""
                                INSERT INTO dbo.excel_import_issue
                                  (import_id, row_number, depositor_id, column_id,
                                   kind, message, file_value, portal_value,
                                   message_key, message_params)
                                VALUES (:imp::uuid, :num, :dep::uuid, :col::varchar,
                                        :kind::varchar, :msg::text, :file::text, :portal::text,
                                        :key::varchar, :params::jsonb)
                                """)
                        .param("imp", importId)
                        .param("num", row.get("rowNumber"))
                        .param("dep", depositorId)
                        .param("col", issue.get("columnId"))
                        .param("kind", issue.get("kind"))
                        .param("msg", texts.text(key, params))
                        .param("file", issue.get("fileValue"))
                        .param("portal", issue.get("portalValue"))
                        .param("key", key)
                        .param("params", params.isEmpty() ? null : jsonObject(params))
                        .update();
            }
        }
    }

    void recount(String importId) {
        jdbc.sql("SELECT dbo.excel_import_recount(:imp::uuid)")
                .param("imp", importId)
                .query()
                .listOfRows();
    }

    /**
     * Загрузка того вида, о котором спрашивают.
     *
     * <p>Адреса загрузки товаров и упаковок разные, а таблица загрузок
     * одна. Без этой проверки идентификатор загрузки упаковок, отправленный
     * на адрес товаров, применился бы как товары — строки упаковок ушли бы
     * в {@code createSku} и легли бы ошибками, понятными только тому,
     * кто это устроил.
     */
    void requireKind(String importId, String kind) {
        String actual = jdbc.sql("SELECT kind FROM dbo.excel_import WHERE import_id = :imp::uuid")
                .param("imp", importId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> Fail.notFound("error.import.not_found_maybe_cancelled"));
        if (!EnumCodes.ImportKind.of(kind).equals(actual)) {
            throw Fail.notFound("error.import.not_found_maybe_cancelled");
        }
    }

    /* ===================== Применение ===================== */

    /** Запись одной строки в справочник. Работает внутри своей транзакции. */
    @FunctionalInterface
    interface RowWriter {
        /**
         * @param ids    {@code sku_id} и {@code packaging_id} строки
         * @param values значения для записи (при обновлении — только пустые поля)
         */
        void write(Map<String, Object> ids, Map<String, Object> values);
    }

    /**
     * Записывает в справочник строки одного класса.
     *
     * <p>Кнопка применяет только свой класс: нажали «Создать новые» —
     * существующие записи не тронуты. Тела запроса нет нарочно: что
     * записывать, сервер знает из результата проверки, а не из того,
     * что прислал браузер. Иначе подменой запроса можно было бы
     * применить строку, которую проверка забраковала.
     *
     * <p><b>Каждая строка пишется своей транзакцией.</b> По решению
     * заказчика сбой одной строки не отменяет остальные: из 61 позиции
     * 60 создаются, а по одной человек читает причину в той же таблице.
     * Поэтому метод НЕ транзакционный — одна общая транзакция откатила
     * бы всё при первом же отказе.
     *
     * @param creating true — создавать новые записи, false — обновлять
     *                 существующие
     */
    Map<String, Object> apply(PortalUser user, String importId, String kind, boolean creating,
                              RowWriter writer) {
        TransactionTemplate tx = ownTransaction();

        tx.executeWithoutResult(status -> {
            depositorScope.apply();
            requireKind(importId, kind);
            requireSameSession(user, importHead(importId));
        });

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
                    writeRow(importId, number, creating, writer);
                });
                done++;
            } catch (RuntimeException e) {
                // Чаще всего — запись с таким кодом успели создать руками
                // в другом окне. Строка остаётся непримененной, а причина
                // становится её замечанием: человек увидит её там же,
                // где остальные.
                failed++;
                MsgRef reason = reason(e);
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
     * же компьютером вошёл другой человек, и тогда в аудите записи
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
     * <p>Строка берётся {@code FOR UPDATE SKIP LOCKED} и с условием
     * {@code NOT applied}: человек мог нажать кнопку дважды, пока ждал
     * ответа. Вторая попытка просто не найдёт строку.
     */
    private void writeRow(String importId, int number, boolean creating, RowWriter writer) {
        List<Map<String, Object>> locked = jdbc.sql("""
                        SELECT sku_id::text AS sku_id, packaging_id::text AS packaging_id
                        FROM dbo.excel_import_row
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
        writer.write(locked.getFirst(), values);

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

    /** Строка не записалась: причина становится её замечанием — тоже ключом. */
    private void markFailed(String importId, int number, MsgRef reason) {
        jdbc.sql("""
                        INSERT INTO dbo.excel_import_issue
                          (import_id, row_number, depositor_id, column_id, kind, message,
                           message_key, message_params)
                        SELECT :imp::uuid, :num, r.depositor_id, NULL,
                               'apply'::varchar, :msg::text, :key::varchar, :params::jsonb
                        FROM dbo.excel_import_row r
                        WHERE r.import_id = :imp::uuid AND r.row_number = :num
                        """)
                .param("imp", importId)
                .param("num", number)
                .param("msg", texts.text(reason.key(), reason.params()))
                .param("key", reason.key())
                .param("params", reason.params().isEmpty() ? null : jsonObject(reason.params()))
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
                               status = CASE WHEN :any THEN :applied ELSE status END
                         WHERE import_id = :imp::uuid
                        """)
                .param("created", creating ? applied : 0)
                .param("updated", creating ? 0 : applied)
                .param("any", applied > 0)
                .param("applied", EnumCodes.ImportStatus.APPLIED)
                .param("imp", importId)
                .update();
        recount(importId);
    }

    /**
     * Причина отказа записи: отказ портала несёт свой ключ, всё прочее —
     * общая фраза. Текст исключения в замечание не идёт: он на языке
     * программы, а не человека.
     */
    private static MsgRef reason(RuntimeException e) {
        if (e instanceof PortalException pe) {
            return new MsgRef(pe.key(), pe.params());
        }
        return MsgRef.of("error.catalog_import.apply_failed");
    }

    /* ===================== Чтение результата и отмена ===================== */

    /** Результат проверки для экрана: сводка, колонки файла и строки. */
    Map<String, Object> result(String importId) {
        Map<String, Object> head = jdbc.sql("""
                        SELECT import_id::text AS "importId", file_name AS "fileName",
                               file_size AS "fileSize", row_count AS "rowCount",
                               to_create AS "toCreate", to_update AS "toUpdate",
                               error_count AS "errorCount",
                               columns_used::text AS "columnsUsedJson",
                               columns_skipped::text AS "columnsSkippedJson",
                               status, kind,
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
        List<Map<String, Object>> rows = SqlRows.toCamelCase(jdbc.sql("""
                        SELECT row_number, row_class, is_valid, applied, row_key,
                               sku_id,
                               cell_values::text  AS cell_values_json,
                               cell_display::text AS cell_display_json,
                               fill_fields::text  AS fill_fields_json
                        FROM dbo.v_excel_import_rows
                        WHERE import_id = :imp::uuid
                        ORDER BY row_number
                        """)
                .param("imp", importId)
                .query()
                .listOfRows());
        Map<Integer, List<String>> issues = issuesInReadersLanguage(importId);
        for (Map<String, Object> row : rows) {
            int number = ((Number) row.get("rowNumber")).intValue();
            row.put("issuesJson", "[" + String.join(",", issues.getOrDefault(number, List.of())) + "]");
        }
        out.put("rows", rows);
        return out;
    }

    /**
     * Замечания загрузки на языке того, кто смотрит (V102): фраза
     * собирается по ключу и подстановкам. Замечания без ключа —
     * записанные до V102 — остаются своим текстом.
     *
     * @return номер строки → замечания, каждое объектом JSON для экрана
     */
    private Map<Integer, List<String>> issuesInReadersLanguage(String importId) {
        Map<Integer, List<String>> out = new HashMap<>();
        for (Map<String, Object> i : jdbc.sql("""
                        SELECT row_number, column_id, kind, message, file_value, portal_value,
                               message_key,
                               ARRAY(SELECT p.key FROM jsonb_each_text(message_params) p
                                     ORDER BY p.key) AS param_names,
                               ARRAY(SELECT p.value FROM jsonb_each_text(message_params) p
                                     ORDER BY p.key) AS param_values
                        FROM dbo.excel_import_issue
                        WHERE import_id = :imp::uuid
                        ORDER BY row_number, id
                        """)
                .param("imp", importId)
                .query()
                .listOfRows()) {
            Object key = i.get("message_key");
            String message = key == null
                    ? String.valueOf(i.get("message"))
                    : texts.text(String.valueOf(key),
                            ImportLogService.params(i.get("param_names"), i.get("param_values")));
            String json = "{" + json("columnId") + ":" + jsonValue(i.get("column_id"))
                    + "," + json("kind") + ":" + jsonValue(i.get("kind"))
                    + "," + json("message") + ":" + json(message)
                    + "," + json("fileValue") + ":" + jsonValue(i.get("file_value"))
                    + "," + json("portalValue") + ":" + jsonValue(i.get("portal_value")) + "}";
            out.computeIfAbsent(((Number) i.get("row_number")).intValue(), n -> new ArrayList<>()).add(json);
        }
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
    void cancel(String importId) {
        int n = jdbc.sql("""
                        UPDATE dbo.excel_import
                           SET status = CASE WHEN created_rows + updated_rows > 0
                                             THEN :applied ELSE :cancelled END
                         WHERE import_id = :imp::uuid
                        """)
                .param("applied", EnumCodes.ImportStatus.APPLIED)
                .param("cancelled", EnumCodes.ImportStatus.CANCELLED)
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

    /**
     * Замечание строки: ключ причины и подстановки парами «имя, значение»
     * (V102). Фразу собирает {@link #storeRows} — на языке загрузившего —
     * и окно проверки — на языке смотрящего.
     */
    static Map<String, Object> issue(String columnId, String kind, String messageKey,
                                     String fileValue, String portalValue, Object... params) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columnId", columnId);
        out.put("kind", kind);
        out.put("messageKey", messageKey);
        out.put("messageParams", MsgRef.of(messageKey, params).params());
        out.put("fileValue", fileValue);
        out.put("portalValue", portalValue);
        return out;
    }

    /** Есть ли уже замечание к этой колонке. */
    static boolean hasIssue(List<Map<String, Object>> issues, String columnId) {
        return issues.stream().anyMatch(i -> columnId.equals(i.get("columnId")));
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
    static String json(String value) {
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
                .map(ExcelImportSupport::json)
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
