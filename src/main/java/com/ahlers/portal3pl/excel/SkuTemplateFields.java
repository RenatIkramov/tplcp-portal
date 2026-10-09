package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.catalog.SkuColumns;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Какие поля товара попадают в шаблон Excel и чем они заполняются.
 *
 * <p>Состав шаблона не перечисляется здесь руками: он выводится из того,
 * что уже есть в проекте, — из списка правимых полей ({@link SkuColumns})
 * и из справочника колонок (таблица {@code table_column}, V29/V34).
 * Иначе появился бы третий список полей товара, который неизбежно
 * разошёлся бы с первыми двумя.
 *
 * <p>Здесь остаются только исключения, которые данными не выразить.
 */
public final class SkuTemplateFields {

    private SkuTemplateFields() {
    }

    /**
     * Поля, которых в шаблоне нет.
     *
     * <p>{@code item_code} — код товара в WMS: его присваивает WMS,
     * и пустота этого поля на портале означает «WMS о товаре ещё
     * не знает» (ср. {@code sku.is_in_wms}, решение Р5). Заполнять его
     * из файла клиента значило бы соврать про состояние обмена.
     */
    public static final Set<String> EXCLUDED = Set.of("item_code");

    /** Поля, без которых строку принять нельзя. */
    public static final Set<String> REQUIRED = SkuColumns.SKU_REQUIRED;

    /**
     * Поле базы → идентификатор колонки в справочнике, если они не
     * совпадают по правилу snake_case → camelCase.
     *
     * <p>Расходятся только поля-ссылки: в таблице лежит
     * {@code uom_id}, а в списках показывается разрешённое имя,
     * и колонка называется {@code unit}.
     */
    public static final Map<String, String> CATALOG_ID = Map.of(
            "uom_id", "unit",
            "item_group", "group",
            "item_type", "productType",
            "currency_id", "currency",
            "hazard_class_id", "hazardClass",
            "hazard_group_id", "hazardGroup");

    /**
     * Поле базы → справочник, из которого берётся выпадающий список.
     * Ключи совпадают с теми, что отдаёт {@code SkuWriteService.dictionaries()}.
     */
    public static final Map<String, String> DICTIONARY = Map.of(
            "uom_id", "uom",
            "currency_id", "currency",
            "hazard_class_id", "hazard_class",
            "hazard_group_id", "hazard_group");

    /**
     * Поля, которые Excel обязан хранить текстом.
     *
     * <p>Иначе он сам превратит {@code 0012} в {@code 12}, а {@code 1-2}
     * в дату — и человек этого не заметит. Самая частая порча данных
     * в таких файлах, и исправить её после загрузки уже нельзя:
     * исходное значение потеряно.
     */
    public static final Set<String> TEXT_FORMAT = Set.of(
            "code", "barcode", "hs_code", "enstru_code", "abc_class", "xyz_class");

    /** Как вести себя с ячейкой: тип определяет проверку в Excel и разбор. */
    public enum Kind { TEXT, NUMBER, BOOLEAN, DICTIONARY }

    /**
     * Колонка шаблона.
     *
     * @param column    имя поля в базе
     * @param catalogId идентификатор колонки в справочнике — по нему берётся подпись
     * @param kind      чем заполняется
     * @param dictionary имя справочника для выпадающего списка, иначе null
     * @param required  обязательна ли
     */
    public record Field(String column, String catalogId, Kind kind,
                        String dictionary, boolean required) {

        public boolean textFormat() {
            return TEXT_FORMAT.contains(column);
        }
    }

    /** Поле базы → идентификатор колонки в справочнике. */
    public static String catalogId(String column) {
        String mapped = CATALOG_ID.get(column);
        return mapped != null ? mapped : camel(column);
    }

    /**
     * Все поля шаблона. Порядок задаётся отдельно, по справочнику
     * колонок, — здесь только состав и свойства.
     */
    public static Map<String, Field> fields() {
        Map<String, Field> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : SkuColumns.SKU.entrySet()) {
            String column = e.getKey();
            if (EXCLUDED.contains(column)) {
                continue;
            }
            String dict = DICTIONARY.get(column);
            Kind kind;
            if (dict != null) {
                kind = Kind.DICTIONARY;
            } else if ("boolean".equals(e.getValue())) {
                kind = Kind.BOOLEAN;
            } else if ("numeric".equals(e.getValue())) {
                kind = Kind.NUMBER;
            } else {
                kind = Kind.TEXT;
            }
            out.put(column, new Field(column, catalogId(column), kind, dict,
                    REQUIRED.contains(column)));
        }
        return out;
    }

    /**
     * Порядок колонок в шаблоне: обязательные первыми, дальше — как
     * в карточке товара (порядок из справочника колонок).
     *
     * @param catalogOrder идентификатор колонки справочника → его порядок
     */
    public static List<Field> ordered(Map<String, Integer> catalogOrder) {
        return fields().values().stream()
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

    static String camel(String snake) {
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
