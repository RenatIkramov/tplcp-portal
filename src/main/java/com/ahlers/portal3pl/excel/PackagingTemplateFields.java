package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.catalog.SkuColumns;
import com.ahlers.portal3pl.excel.ExcelField.Kind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Какие поля упаковки попадают в шаблон Excel и чем они заполняются.
 *
 * <p>Состав — правимые поля упаковки ({@link SkuColumns#PACKAGING}) плюс
 * два, которых в той таблице нет:
 * <ul>
 *   <li><b>товар</b> — упаковка без товара не существует, и строка файла
 *       опознаётся парой «товар + код упаковки». В файле товар пишется
 *       коммерческим кодом, в базу уходит его идентификатор;</li>
 *   <li><b>единица заказа</b> — признак лежит у товара
 *       ({@code sku.order_unit_id}), а не у упаковки, но человек
 *       думает о нём как о свойстве упаковки: «заказывают коробами».</li>
 * </ul>
 *
 * <p>Наименования товара, единицы измерения и «наименования упаковки»
 * в шаблоне нет: первые два принадлежат товару, третье экран собирает
 * сам из типа, коэффициента и единицы.
 *
 * <p>Идентификаторы колонок — те же, что в справочнике колонок
 * {@code packaging} (V76, V81): по ним берётся подпись заголовка
 * и опознаётся заголовок загруженного файла.
 */
public final class PackagingTemplateFields {

    private PackagingTemplateFields() {
    }

    /** Справочник типов упаковки — тот же, что в карточке товара. */
    public static final String TYPE_DICTIONARY = "packaging_type";

    /** Поле «товар» — его идентификатор, хотя в файле стоит код. */
    public static final String SKU = "sku_id";

    /** Поле «единица заказа». В таблице упаковок его нет — пишется в товар. */
    public static final String ORDER_UNIT = "is_default_order_unit";

    /** Объём: если заданы длина, ширина и высота, считается сам (V82). */
    public static final String VOLUME = "volume_m3";

    private static final Map<String, ExcelField> FIELDS;

    static {
        Map<String, ExcelField> f = new LinkedHashMap<>();
        // Код товара — текстом, без выпадающего списка (V97): товаров у клиента
        // могут быть тысячи, и список из них не помогает, а мешает. Код
        // сверяет с базой загрузка.
        put(f, new ExcelField(SKU, "skuCode", Kind.TEXT, null,
                true, true, false));
        put(f, new ExcelField("packaging_code", "packagingCode", Kind.TEXT, null,
                true, true, false));
        put(f, new ExcelField("packaging_type_id", "packagingTypeName", Kind.DICTIONARY,
                TYPE_DICTIONARY, false, false, false));
        put(f, new ExcelField("barcode", "barcode", Kind.TEXT, null, false, true, false));
        // Коэффициент — сколько базовых единиц в упаковке. Ноль бессмыслен:
        // окно строки делит на него, пересчитывая количество.
        put(f, new ExcelField("coefficient", "coefficient", Kind.NUMBER, null,
                true, false, true));
        put(f, new ExcelField("length_cm", "lengthCm", Kind.NUMBER, null, false, false, false));
        put(f, new ExcelField("width_cm", "widthCm", Kind.NUMBER, null, false, false, false));
        put(f, new ExcelField("height_cm", "heightCm", Kind.NUMBER, null, false, false, false));
        put(f, new ExcelField("volume_m3", "volumeM3", Kind.NUMBER, null, false, false, false));
        put(f, new ExcelField("gross_weight_kg", "grossWeightKg", Kind.NUMBER, null,
                false, false, false));
        put(f, new ExcelField(ORDER_UNIT, "isDefaultOrderUnit", Kind.BOOLEAN, null,
                false, false, false));
        FIELDS = Collections.unmodifiableMap(f);

        // Каждое поле упаковки, которое правит карточка, обязано быть
        // и в шаблоне. Иначе новое поле в SkuColumns молча не попало бы
        // в загрузку, и человек решил бы, что Excel его не поддерживает.
        for (String column : SkuColumns.PACKAGING.keySet()) {
            if (!FIELDS.containsKey(column)) {
                throw new IllegalStateException(
                        "поле упаковки " + column + " не описано в шаблоне Excel");
            }
        }
    }

    private static void put(Map<String, ExcelField> f, ExcelField field) {
        f.put(field.column(), field);
    }

    /** Все поля шаблона по имени поля в базе. Порядок задаёт справочник колонок. */
    public static Map<String, ExcelField> fields() {
        return FIELDS;
    }

    /** Поле базы → идентификатор колонки в справочнике. */
    public static String catalogId(String column) {
        ExcelField f = FIELDS.get(column);
        return f == null ? column : f.catalogId();
    }
}
