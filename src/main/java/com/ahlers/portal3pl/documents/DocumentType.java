package com.ahlers.portal3pl.documents;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Три типа документов портала. Перечисление заодно защищает от подстановки
 * произвольного имени таблицы в запрос: в SQL попадают только значения
 * отсюда, а не то, что пришло из адреса.
 */
public enum DocumentType {

    RECEIPTS("receipts", "v_receipts", "v_receipt_lines", "v_receipt_line_details",
            "supplier_name", "eta_date"),
    ORDERS("orders", "v_orders", "v_order_lines", "v_order_line_details",
            "consignee_name", "planned_ship_date"),
    TRANSFERS("transfers", "v_transfers", "v_transfer_lines", "v_transfer_line_details",
            "to_warehouse_name", "ship_date");

    private final String path;
    private final String headerView;
    private final String linesView;
    private final String lineDetailsView;
    /** Колонка контрагента: поставщик / грузополучатель / склад назначения. */
    private final String partyColumn;
    /** Колонка плановой даты: ожидаемая приёмка / плановая отгрузка. */
    private final String plannedDateColumn;

    DocumentType(String path, String headerView, String linesView, String lineDetailsView,
                 String partyColumn, String plannedDateColumn) {
        this.path = path;
        this.headerView = headerView;
        this.linesView = linesView;
        this.lineDetailsView = lineDetailsView;
        this.partyColumn = partyColumn;
        this.plannedDateColumn = plannedDateColumn;
    }

    public static DocumentType ofPath(String path) {
        for (DocumentType t : values()) {
            if (t.path.equals(path)) {
                return t;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный тип документа: " + path);
    }

    public String headerView() {
        return headerView;
    }

    public String linesView() {
        return linesView;
    }

    public String lineDetailsView() {
        return lineDetailsView;
    }

    public String partyColumn() {
        return partyColumn;
    }

    public String plannedDateColumn() {
        return plannedDateColumn;
    }

    /** У перемещений склад хранится в двух колонках, фильтруем по складу-источнику. */
    public String warehouseColumn() {
        return this == TRANSFERS ? "from_warehouse_id" : "warehouse_id";
    }

    public String path() {
        return path;
    }

    /** Имя таблицы-заголовка. Совпадает с путём, но это совпадение не гарантировано. */
    public String headerTable() {
        return switch (this) {
            case RECEIPTS -> "receipts";
            case ORDERS -> "orders";
            case TRANSFERS -> "transfers";
        };
    }

    public String lineTable() {
        return switch (this) {
            case RECEIPTS -> "receipt_details";
            case ORDERS -> "order_details";
            case TRANSFERS -> "transfer_details";
        };
    }

    /** enum_object_type.id — нужен для поиска статусов нужного типа документа. */
    public short objectTypeId() {
        return switch (this) {
            case RECEIPTS -> (short) 1;
            case ORDERS -> (short) 2;
            case TRANSFERS -> (short) 3;
        };
    }

    /**
     * Поля шапки, которые разрешено менять через портал в черновике.
     * Всё, чего нет в этом списке, запрос просто проигнорирует — так в SQL
     * не попадёт произвольное имя колонки, пришедшее снаружи.
     */
    public java.util.Set<String> editableHeaderColumns() {
        java.util.Set<String> common = new java.util.LinkedHashSet<>(java.util.List.of(
                "erp_number", "doc_date", "comment", "trip",
                "total_pieces", "cargo_places", "pallet_count", "weight_kg", "volume_m3",
                "custom_field_1", "custom_field_2", "custom_field_3", "custom_field_4",
                "custom_field_5", "custom_field_6", "custom_field_7", "custom_field_8",
                "custom_field_9", "custom_field_10"));
        switch (this) {
            case RECEIPTS -> common.addAll(java.util.List.of(
                    "warehouse_id", "supplier_id", "doc_type_id", "eta_date",
                    "accompanying_doc_number", "invoice_number", "gtd"));
            case ORDERS -> common.addAll(java.util.List.of(
                    "warehouse_id", "consignee_id", "delivery_point_id", "consignee_contact",
                    "order_type_id", "planned_ship_date", "delivery_date"));
            case TRANSFERS -> common.addAll(java.util.List.of(
                    "from_warehouse_id", "to_warehouse_id", "ship_date"));
        }
        return common;
    }

    /** Транспортный блок — общий для всех типов, правится по своим правилам (Р5). */
    public static java.util.Set<String> transportColumns() {
        return java.util.Set.of(
                "carrier", "transport_type", "transport_brand", "car_number",
                "trailer_number", "tech_passport_number", "container_number", "seal_number",
                "driver_name", "driver_phone", "driver_id_number", "driver_inn", "trip");
    }

    /** Поля строки состава, доступные для ввода. */
    public java.util.Set<String> editableLineColumns() {
        java.util.Set<String> common = new java.util.LinkedHashSet<>(java.util.List.of(
                "sku_id", "line_comment",
                "custom_field_1", "custom_field_2", "custom_field_3", "custom_field_4",
                "custom_field_5", "custom_field_6", "custom_field_7", "custom_field_8",
                "custom_field_9", "custom_field_10"));
        switch (this) {
            case RECEIPTS -> common.addAll(java.util.List.of("expected_qty", "price", "currency_id"));
            case ORDERS -> common.addAll(java.util.List.of("ordered_qty", "weight_kg", "price", "currency_id"));
            case TRANSFERS -> common.add("qty");
        }
        return common;
    }

    /** Плановое количество — его заполняет пользователь (решение Р1). */
    public String plannedQtyColumn() {
        return switch (this) {
            case RECEIPTS -> "expected_qty";
            case ORDERS -> "ordered_qty";
            case TRANSFERS -> "qty";
        };
    }

    /** Поля, без которых документ нельзя подтвердить. */
    public java.util.List<String> requiredForConfirm() {
        return switch (this) {
            case RECEIPTS -> java.util.List.of("warehouse_id", "supplier_id", "eta_date");
            case ORDERS -> java.util.List.of("warehouse_id", "consignee_id", "planned_ship_date");
            case TRANSFERS -> java.util.List.of("from_warehouse_id", "to_warehouse_id", "ship_date");
        };
    }
}
