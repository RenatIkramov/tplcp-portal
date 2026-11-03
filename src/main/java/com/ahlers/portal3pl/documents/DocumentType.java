package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.lists.ListKind;

/**
 * Три типа документов портала. Перечисление заодно защищает от подстановки
 * произвольного имени таблицы в запрос: в SQL попадают только значения
 * отсюда, а не то, что пришло из адреса.
 */
public enum DocumentType implements ListKind {

    RECEIPTS("receipts", "v_receipts", "v_receipt_lines", "v_receipt_line_details"),
    ORDERS("orders", "v_orders", "v_order_lines", "v_order_line_details"),
    TRANSFERS("transfers", "v_transfers", "v_transfer_lines", "v_transfer_line_details");

    private final String path;
    private final String headerView;
    private final String linesView;
    private final String lineDetailsView;

    DocumentType(String path, String headerView, String linesView, String lineDetailsView) {
        this.path = path;
        this.headerView = headerView;
        this.linesView = linesView;
        this.lineDetailsView = lineDetailsView;
    }

    public static DocumentType ofPath(String path) {
        for (DocumentType t : values()) {
            if (t.path.equals(path)) {
                return t;
            }
        }
        throw Fail.notFound("error.document.unknown_type", "path", path);
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

    /** Склад из шапки портала: у перемещения — отправитель или получатель (V94). */
    @Override
    public java.util.List<String> warehouseColumns() {
        return this == TRANSFERS
                ? java.util.List.of("from_warehouse_id", "to_warehouse_id")
                : java.util.List.of("warehouse_id");
    }

    public String path() {
        return path;
    }

    /* ---- ListKind: общее у всех списков портала (отбор, порядок, страницы) ---- */

    @Override
    public String tableKey() {
        return path;
    }

    @Override
    public String view() {
        return headerView;
    }

    /**
     * Список документов открывается свежими сверху.
     *
     * <p>Хвост {@code doc_id} — не украшение: без него два документа
     * одной даты идут в произвольном порядке, и при листании один
     * попадётся дважды, а другой не попадётся вовсе.
     */
    @Override
    public String defaultOrder() {
        return "doc_date DESC NULLS LAST, doc_id DESC";
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
    @Override
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
        // Чего здесь нарочно нет:
        //
        //   doc_date      — дату создания ставит база при создании черновика,
        //                   вводить её человеку незачем;
        //   cargo_places,
        //   pallet_count,
        //   total_pieces,
        //   weight_kg,
        //   volume_m3     — это измеренные склада́ми факты, они приходят
        //                   из WMS. Портал их показывает, но не задаёт:
        //                   иначе в документе соседствовали бы «сколько
        //                   обещали» и «сколько приняли» под одним именем;
        //   trip,
        //   delivery_date — рейс и дата доставки тоже данные WMS
        //                   (решение заказчика от 02.11.2026, V97).
        java.util.Set<String> common = new java.util.LinkedHashSet<>(java.util.List.of(
                "erp_number", "comment",
                "custom_field_1", "custom_field_2", "custom_field_3", "custom_field_4",
                "custom_field_5", "custom_field_6", "custom_field_7", "custom_field_8",
                "custom_field_9", "custom_field_10"));
        switch (this) {
            case RECEIPTS -> common.addAll(java.util.List.of(
                    "warehouse_id", "supplier_id", "doc_type_id", "eta_date",
                    "accompanying_doc_number", "invoice_number", "gtd"));
            case ORDERS -> common.addAll(java.util.List.of(
                    "warehouse_id", "consignee_id", "delivery_point_id", "consignee_contact",
                    "order_type_id", "planned_ship_date"));
            // Дату отгрузки перемещения портал не задаёт: её ставит склад,
            // когда действительно отгружает. Поэтому её нет и среди
            // обязательных для подтверждения — требовать то, чего нельзя
            // заполнить, значит запереть документ навсегда.
            case TRANSFERS -> common.addAll(java.util.List.of(
                    "from_warehouse_id", "to_warehouse_id"));
        }
        return common;
    }

    /**
     * Транспортный блок — общий для всех типов, правится по своим правилам (Р5).
     *
     * <p>Рейса здесь нет с V97: его заводит WMS. {@code transport_type} —
     * код перечисления «Типы транспорта» (V97).
     */
    public static java.util.Set<String> transportColumns() {
        return java.util.Set.of(
                "carrier", "transport_type", "transport_brand", "car_number",
                "trailer_number", "tech_passport_number", "container_number", "seal_number",
                "driver_name", "driver_phone", "driver_id_number", "driver_inn");
    }

    /** Поля строки состава, доступные для ввода. */
    public java.util.Set<String> editableLineColumns() {
        java.util.Set<String> common = new java.util.LinkedHashSet<>(java.util.List.of(
                "sku_id", "line_comment",
                "custom_field_1", "custom_field_2", "custom_field_3", "custom_field_4",
                "custom_field_5", "custom_field_6", "custom_field_7", "custom_field_8",
                "custom_field_9", "custom_field_10"));
        switch (this) {
            // quality_id — качество строки (заказ — V89, поступление — V100, перемещение — V101);
            // экран присылает код, в quality_id его переводит LineQuality
            case RECEIPTS -> common.addAll(java.util.List.of("expected_qty", "price", "currency_id",
                    "quality_id"));
            case ORDERS -> common.addAll(java.util.List.of("ordered_qty", "weight_kg", "price", "currency_id",
                    "quality_id"));
            case TRANSFERS -> common.addAll(java.util.List.of("qty", "quality_id"));
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

    /**
     * Поля, без которых документ нельзя подтвердить: колонка базы → колонка
     * справочника колонок.
     *
     * <p>Второе значение нужно, чтобы назвать поле человеку так, как оно
     * подписано у него на экране. Соответствие задано списком, а не
     * правилом перевода имени: {@code eta_date} в справочнике зовётся
     * {@code eta}, а {@code planned_ship_date} — {@code plannedShip},
     * и никакое механическое преобразование этого не угадает.
     */
    public java.util.Map<String, String> requiredForConfirm() {
        return switch (this) {
            // Поставщик поступления необязателен (решение заказчика от 03.11.2026)
            case RECEIPTS -> new java.util.LinkedHashMap<>(java.util.Map.of(
                    "erp_number", "erpNo",
                    "warehouse_id", "warehouse",
                    "eta_date", "eta"));
            case ORDERS -> new java.util.LinkedHashMap<>(java.util.Map.of(
                    "erp_number", "erpNo",
                    "warehouse_id", "warehouse",
                    "consignee_id", "consignee",
                    "planned_ship_date", "plannedShip"));
            case TRANSFERS -> new java.util.LinkedHashMap<>(java.util.Map.of(
                    "erp_number", "erpNo",
                    "from_warehouse_id", "fromWarehouse",
                    "to_warehouse_id", "toWarehouse"));
        };
    }
}
