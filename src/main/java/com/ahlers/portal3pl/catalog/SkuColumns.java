package com.ahlers.portal3pl.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Какие поля товара и упаковки портал разрешает править и как приводить
 * их типы в SQL.
 *
 * <p>Белый список, а не «всё, кроме»: в таблице `sku` около шестидесяти
 * колонок, часть из них принадлежит WMS и синхронизации. Если разрешать
 * по остаточному принципу, любая новая колонка автоматически окажется
 * доступной для правки из браузера — это ошибка, которую легко не заметить.
 *
 * <p>Намеренно НЕ редактируются: `is_in_wms`, `is_wms_active`, `synced_at`,
 * `wms_version` (их ставит синхронизация, решение Р5), `status_id`,
 * `depositor_id`, поля аудита `created_*` / `updated_*` (их ставит портал
 * сам, §11) и `order_unit_id` — упаковка по умолчанию выбирается
 * на вкладке «Упаковка», а не в анкете товара.
 */
public final class SkuColumns {

    private SkuColumns() {
    }

    /**
     * Колонки товара, доступные правке, и их тип для приведения в SQL.
     *
     * <p>Собирается в статическом блоке, а не одним {@code Map.ofEntries}
     * на 54 элемента: такой вызов — один метод с 54 аргументами, и среда
     * разработки честно предупреждает, что разбор типов на нём
     * замедляется. Заодно список стал читаемым: поля сгруппированы
     * по смыслу, а десять доп. параметров строятся циклом вместо
     * десяти почти одинаковых строк.
     */
    public static final Map<String, String> SKU;

    static {
        Map<String, String> sku = new LinkedHashMap<>();

        // опознание и наименование
        sku.put("code", "varchar");
        sku.put("item_code", "varchar");
        sku.put("name", "varchar");
        sku.put("item_full_name", "text");
        sku.put("barcode", "varchar");
        sku.put("uom_id", "smallint");
        sku.put("item_group_id", "uuid");
        sku.put("item_type_id", "uuid");
        sku.put("has_packaging", "boolean");

        // физические свойства
        sku.put("length", "numeric");
        sku.put("width", "numeric");
        sku.put("height", "numeric");
        sku.put("volume", "numeric");
        sku.put("weight_gross", "numeric");
        sku.put("weight_net", "numeric");
        sku.put("color", "varchar");
        sku.put("size", "varchar");

        // производитель и классификация
        sku.put("manufacturer", "varchar");
        sku.put("country_origin", "varchar");
        sku.put("brand", "varchar");
        sku.put("abc_class", "varchar");
        sku.put("xyz_class", "varchar");

        // условия хранения
        sku.put("min_storage_temp", "numeric");
        sku.put("max_storage_temp", "numeric");
        sku.put("min_humidity", "numeric");
        sku.put("max_humidity", "numeric");
        sku.put("min_days_for_receipt", "numeric");
        sku.put("min_days_for_reservation", "numeric");
        sku.put("shelf_life_days", "numeric");
        sku.put("hazard_class_id", "smallint");
        sku.put("hazard_group_id", "smallint");

        // коды, стоимость
        sku.put("hs_code", "varchar");
        sku.put("enstru_code", "varchar");
        sku.put("cost", "numeric");
        sku.put("currency_id", "smallint");

        // признаки и учёт
        sku.put("is_fragile", "boolean");
        sku.put("is_serial_tracked", "boolean");
        sku.put("batch_tracking", "varchar");
        sku.put("expiry_tracking", "varchar");
        sku.put("is_kit", "boolean");
        sku.put("is_marking_required", "boolean");
        sku.put("is_excise", "boolean");
        sku.put("is_controlled", "boolean");

        sku.put("comment", "text");

        // доп. параметры: в базе их десять, и правятся все десять
        for (int i = 1; i <= 10; i++) {
            sku.put("custom_attr" + i, "varchar");
        }

        SKU = Collections.unmodifiableMap(sku);
    }

    /** Колонки упаковки. */
    public static final Map<String, String> PACKAGING = Map.of(
            "packaging_code", "varchar",
            "packaging_type_id", "smallint",
            "barcode", "varchar",
            "coefficient", "numeric",
            "length_cm", "numeric",
            "width_cm", "numeric",
            "height_cm", "numeric",
            "volume_m3", "numeric",
            "gross_weight_kg", "numeric");

    /** Поля, без которых товар не имеет смысла. */
    public static final Set<String> SKU_REQUIRED = Set.of("code", "name", "uom_id");

    /** Поля, без которых упаковка не имеет смысла. */
    public static final Set<String> PACKAGING_REQUIRED = Set.of("packaging_code", "coefficient");
}
