package com.ahlers.portal3pl.catalog;

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

    /** Колонки товара, доступные правке, и их тип для приведения в SQL. */
    public static final Map<String, String> SKU = Map.ofEntries(
            Map.entry("code", "varchar"),
            Map.entry("item_code", "varchar"),
            Map.entry("name", "varchar"),
            Map.entry("item_full_name", "text"),
            Map.entry("barcode", "varchar"),
            Map.entry("uom_id", "smallint"),
            Map.entry("item_group_id", "uuid"),
            Map.entry("item_type_id", "uuid"),
            Map.entry("has_packaging", "boolean"),
            Map.entry("length", "numeric"),
            Map.entry("width", "numeric"),
            Map.entry("height", "numeric"),
            Map.entry("volume", "numeric"),
            Map.entry("weight_gross", "numeric"),
            Map.entry("weight_net", "numeric"),
            Map.entry("manufacturer", "varchar"),
            Map.entry("country_origin", "varchar"),
            Map.entry("brand", "varchar"),
            Map.entry("is_fragile", "boolean"),
            Map.entry("is_serial_tracked", "boolean"),
            Map.entry("batch_tracking", "varchar"),
            Map.entry("expiry_tracking", "varchar"),
            Map.entry("min_storage_temp", "numeric"),
            Map.entry("max_storage_temp", "numeric"),
            Map.entry("min_humidity", "numeric"),
            Map.entry("max_humidity", "numeric"),
            Map.entry("min_days_for_receipt", "numeric"),
            Map.entry("min_days_for_reservation", "numeric"),
            Map.entry("shelf_life_days", "numeric"),
            Map.entry("color", "varchar"),
            Map.entry("size", "varchar"),
            Map.entry("abc_class", "varchar"),
            Map.entry("xyz_class", "varchar"),
            Map.entry("hs_code", "varchar"),
            Map.entry("enstru_code", "varchar"),
            Map.entry("cost", "numeric"),
            Map.entry("currency_id", "smallint"),
            Map.entry("is_kit", "boolean"),
            Map.entry("is_marking_required", "boolean"),
            Map.entry("is_excise", "boolean"),
            Map.entry("is_controlled", "boolean"),
            Map.entry("hazard_class_id", "smallint"),
            Map.entry("hazard_group_id", "smallint"),
            Map.entry("comment", "text"),
            Map.entry("custom_attr1", "varchar"),
            Map.entry("custom_attr2", "varchar"),
            Map.entry("custom_attr3", "varchar"),
            Map.entry("custom_attr4", "varchar"),
            Map.entry("custom_attr5", "varchar"));

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
