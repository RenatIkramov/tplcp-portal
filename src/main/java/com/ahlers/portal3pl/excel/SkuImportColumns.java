package com.ahlers.portal3pl.excel;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Опознание колонок загружаемого файла по заголовкам.
 *
 * <p>Загрузчик не привязан к номерам столбцов: человек вправе удалить
 * из шаблона всё, кроме обязательных полей, и переставить остальное.
 * Поэтому колонка ищется по подписи.
 *
 * <p>Сопоставление намеренно терпимое, иначе лишний пробел отправлял бы
 * человека разбираться:
 * <ol>
 *   <li>обрезаются пробелы, сжимаются повторные, снимается звёздочка
 *       обязательности и суффикс «(2)», которым шаблон разводит
 *       одинаковые подписи;</li>
 *   <li>регистр не учитывается, ё приравнивается к е;</li>
 *   <li>подпись ищется среди названий поля <b>на любом языке портала</b>
 *       и среди переименований этого клиента.</li>
 * </ol>
 *
 * <p>То есть «Коммерческий код», {@code Commercial code} и «Артикул
 * клиента» (если клиент так переименовал поле) ведут к одному и тому же
 * полю {@code code}.
 */
@Component
public class SkuImportColumns {

    private final JdbcClient jdbc;

    public SkuImportColumns(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Колонка файла.
     *
     * @param index    номер столбца в файле, с нуля
     * @param header   заголовок, как он написан в файле
     * @param field    поле справочника, если заголовок опознан, иначе null
     */
    public record Column(int index, String header, ExcelField field) {

        public boolean recognized() {
            return field != null;
        }

        public String columnId() {
            return field == null ? null : field.catalogId();
        }

        public String dbColumn() {
            return field == null ? null : field.column();
        }
    }

    /** Результат опознания шапки файла. */
    public record Layout(List<Column> columns, List<String> unrecognized,
                         Map<String, Column> byDbColumn) {

        public Column of(String dbColumn) {
            return byDbColumn.get(dbColumn);
        }

        public boolean has(String dbColumn) {
            return byDbColumn.containsKey(dbColumn);
        }
    }

    /** Единицы измерения, которые бывают приписаны к названию поля. */
    private static final Set<String> UNITS = Set.of(
            "см", "мм", "кг", "г", "м³", "м3", "л", "°c", "%", "дней", "шт",
            "cm", "mm", "kg", "g", "m3", "l", "days", "pcs");

    /**
     * Приводит подпись к сравнимому виду.
     *
     * <p>Открытый метод: тем же способом нормализуются и заголовки файла,
     * и названия полей из базы. Если нормализация разойдётся между этими
     * двумя местами, не совпадёт ничего, и причину будет не найти.
     */
    public static String normalize(String label) {
        if (label == null) {
            return "";
        }
        String s = label.replace(' ', ' ').trim();
        // звёздочка обязательности и суффикс развода одинаковых подписей
        s = s.replaceAll("\\s*\\*$", "");
        s = s.replaceAll("\\s*\\((\\d+)\\)$", "");
        s = s.replaceAll("\\s+", " ");
        s = s.toLowerCase().replace('ё', 'е');
        return withoutUnit(s);
    }

    /**
     * Отбрасывает приписанную единицу измерения: «вес брутто, кг» → «вес брутто».
     *
     * <p>В справочнике колонок единицы проставлены непоследовательно:
     * «Вес брутто» без единицы, а «Срок хранения, дней» — с ней. А в карточке
     * товара то же поле подписано «Вес брутто, кг». Человек, собирая файл
     * сам, напишет так, как видел на экране, и файл не должен из-за
     * этого отвалиться — на этом споткнулся наш же тест.
     *
     * <p>Проверено по базе: отбрасывание единицы не склеивает две разные
     * колонки товара ни на одном языке портала.
     */
    private static String withoutUnit(String s) {
        int cut = Math.max(s.lastIndexOf(','), s.lastIndexOf(' '));
        if (cut <= 0 || cut == s.length() - 1) {
            return s;
        }
        if (!UNITS.contains(s.substring(cut + 1).trim())) {
            return s;
        }
        String head = s.substring(0, cut).trim();
        return head.endsWith(",") ? head.substring(0, head.length() - 1).trim() : head;
    }

    /**
     * Опознаёт шапку файла товаров.
     *
     * @param headers      заголовки по порядку, как прочитаны из файла
     * @param depositorId  поклажедатель — его переименования полей учитываются
     */
    public Layout match(List<String> headers, String depositorId) {
        return match(headers, depositorId, "sku", SkuTemplateFields.fields());
    }

    /**
     * Опознаёт шапку файла любого справочника.
     *
     * <p>Правила одни для товаров и упаковок — потому и метод один:
     * разойдись нормализация заголовков между двумя загрузками, человек
     * не понял бы, почему файл упаковок не узнаёт столбец, который файл
     * товаров узнаёт.
     *
     * @param tableKey ключ таблицы в справочнике колонок: {@code sku}, {@code packaging}
     * @param fields   поля справочника, которые можно загрузить
     */
    public Layout match(List<String> headers, String depositorId, String tableKey,
                        Map<String, ExcelField> fields) {
        Map<String, ExcelField> byLabel = labels(depositorId, tableKey, fields);

        List<Column> columns = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>();
        Map<String, Column> byDbColumn = new LinkedHashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header == null || header.isBlank()) {
                continue;            // пустой столбец в шапке просто пропускаем
            }
            ExcelField field = byLabel.get(normalize(header));

            // Поле могло быть опознано, но уже занято другим столбцом —
            // два столбца на одно поле означают, что непонятно, какое
            // значение брать. Второй считаем нераспознанным.
            if (field != null && byDbColumn.containsKey(field.column())) {
                unrecognized.add(header);
                columns.add(new Column(i, header, null));
                continue;
            }

            Column column = new Column(i, header, field);
            columns.add(column);
            if (field == null) {
                unrecognized.add(header);
            } else {
                byDbColumn.put(field.column(), column);
            }
        }

        // Код товара WMS в состав полей шаблона не входит (SkuTemplateFields
        // его исключает), поэтому столбец с таким заголовком не совпадёт
        // ни с чем и попадёт в нераспознанные — то есть будет пропущен
        // с предупреждением. Это и нужно: его присваивает WMS.

        return new Layout(List.copyOf(columns), List.copyOf(unrecognized),
                Map.copyOf(byDbColumn));
    }

    /**
     * Подпись → поле товара. Собирается по всем языкам портала и по
     * переименованиям клиента.
     *
     * <p>Первое попадание сильнее: если переименование клиента совпало
     * с названием другого поля на другом языке, выиграет то, что
     * записано раньше, — но такой случай означает, что клиент назвал
     * поле как чужое, и тогда файл всё равно спорный. Важнее, что
     * сопоставление однозначно и не зависит от порядка строк в базе:
     * поэтому выборка упорядочена.
     */
    private Map<String, ExcelField> labels(String depositorId, String tableKey,
                                           Map<String, ExcelField> fields) {
        Map<String, ExcelField> byCatalogId = new HashMap<>();
        fields.values().forEach(f -> byCatalogId.put(f.catalogId(), f));

        Map<String, ExcelField> out = new HashMap<>();

        // Название поля на каждом языке портала плюс сам ключ текста:
        // ключ пригодится, если перевода на язык ещё нет.
        // Колонки, запрещённые этому клиенту, в сопоставление не входят.
        // Иначе запрет на экране доступов можно было бы обойти загрузкой:
        // поля нет в шаблоне и в карточке, а через файл оно заполнялось бы.
        List<Map<String, Object>> rows = jdbc.sql("""
                        WITH available AS (
                          SELECT c.*
                          FROM dbo.table_column c
                          LEFT JOIN dbo.depositor_table_column d
                                 ON d.depositor_id = :dep::uuid
                                AND d.table_key = c.table_key
                                AND d.column_id = c.column_id
                          WHERE c.table_key = :table::text
                            AND COALESCE(d.is_available, true)
                        )
                        SELECT c.column_id, t.value AS label
                        FROM available c
                        JOIN dbo.ui_text t ON t.text_key = c.text_key
                        UNION ALL
                        SELECT c.column_id, c.text_key AS label
                        FROM available c
                        UNION ALL
                        SELECT c.column_id, l.custom_label AS label
                        FROM available c
                        JOIN dbo.depositor_field_label l
                          ON l.object_type_id = c.object_type_id
                         AND l.field_id = c.field_id
                        WHERE l.depositor_id = :dep::uuid
                        ORDER BY 1, 2
                        """)
                .param("dep", depositorId)
                .param("table", tableKey)
                .query()
                .listOfRows();

        for (Map<String, Object> row : rows) {
            ExcelField field = byCatalogId.get(String.valueOf(row.get("column_id")));
            if (field == null) {
                continue;            // колонка есть в справочнике, но не правится
            }
            String key = normalize(String.valueOf(row.get("label")));
            if (!key.isEmpty()) {
                out.putIfAbsent(key, field);
            }
        }
        return out;
    }
}
