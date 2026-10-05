package com.ahlers.portal3pl.common;

import java.util.Set;

/**
 * Приведение типов для значений, пришедших из JSON.
 *
 * В JSON нет ни дат, ни uuid — они приезжают строками. PostgreSQL строгий:
 * «столбец eta_date имеет тип date, а выражение — character varying» он
 * выдаёт сразу, без попытки догадаться. Поэтому для каждой колонки, тип
 * которой не текстовый, в запрос подставляется явное приведение.
 *
 * Список маленький и опирается на правила именования схемы, а не на
 * перечисление всех полей: пропустить колонку здесь дешевле, чем
 * расходиться со схемой.
 */
public final class SqlCast {

    /** Идентификаторы-uuid. Склады — varchar, поэтому их здесь нет. */
    private static final Set<String> UUID_COLUMNS = Set.of(
            "sku_id", "supplier_id", "consignee_id", "delivery_point_id",
            "author_id", "created_by", "updated_by", "depositor_id", "doc_id",
            "item_group_id", "item_type_id", "order_unit_id", "packaging_id");

    /** Небольшие числовые справочные ключи. */
    private static final Set<String> SMALLINT_COLUMNS = Set.of(
            "doc_type_id", "order_type_id", "currency_id", "unit_id", "uom_id",
            "packaging_type_id", "block_reason_id", "hazard_class_id", "hazard_group_id",
            "object_type_id", "locale_id", "line_number", "sort_order");

    /** Целые счётчики в шапке документа. */
    private static final Set<String> INT_COLUMNS = Set.of(
            "line_count", "cargo_places", "pallet_count", "status_id");

    /** Дробные количества, веса, объёмы, цены. */
    private static final Set<String> NUMERIC_COLUMNS = Set.of(
            "expected_qty", "expected_qty_wms", "accepted_qty", "defect_qty",
            "ordered_qty", "ordered_qty_wms", "picked_qty", "qty", "qty_wms",
            "quantity", "reserved_qty", "blocked_qty", "available_qty",
            "price", "weight_kg", "volume_m3", "total_pieces", "cost", "rate");

    private SqlCast() {
    }

    /** Суффикс приведения для колонки: "::date", "::uuid" и т. п. Пусто — приведение не нужно. */
    public static String of(String column) {
        if (UUID_COLUMNS.contains(column)) {
            return "::uuid";
        }
        if (column.endsWith("_date") || column.equals("expiry_date")) {
            return "::date";
        }
        if (column.endsWith("_at")) {
            return "::timestamptz";
        }
        if (SMALLINT_COLUMNS.contains(column)) {
            return "::smallint";
        }
        if (INT_COLUMNS.contains(column)) {
            return "::int";
        }
        if (NUMERIC_COLUMNS.contains(column)) {
            return "::numeric";
        }
        if (column.startsWith("is_") || column.startsWith("has_") || column.startsWith("can_")) {
            return "::boolean";
        }
        return "";
    }

    /** "eta_date = :eta_date::date" */
    public static String assignment(String column) {
        return column + " = :" + column + of(column);
    }

    /** ":eta_date::date" */
    public static String placeholder(String column) {
        return ":" + column + of(column);
    }
}
