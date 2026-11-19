package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.common.EnumCodes;
import com.ahlers.portal3pl.documents.DocumentType;

import java.util.ArrayList;
import java.util.List;

/**
 * Устройство файла «документ из Excel» — общее для шаблона и загрузки.
 * Заказ — с V96, поступление — с V100, перемещение — с V101: файл у них
 * один и тот же, различаются поля заголовка и несколько фраз ({@link Spec}).
 *
 * <p>Решения заказчика от 27.10, 02.11 и 03.11.2026:
 * <ul>
 *   <li>один файл — один документ, результат загрузки — черновик;</li>
 *   <li>один лист и три умные таблицы: {@code Goods} (состав),
 *       {@code header} (заголовок) и {@code transport} (транспорт);</li>
 *   <li>в {@code header} и {@code transport} три колонки: «Поле | Значение
 *       | Ключ». Подпись — на языке пользователя и с переименованиями
 *       поклажедателя, а читается ключ: файл, скачанный по-русски,
 *       загрузит и англоязычный пользователь;</li>
 *   <li>значения выпадающих списков — «код — имя». Читается код до
 *       {@link #SEPARATOR}: перевод и переименование имени файл не ломают.</li>
 * </ul>
 *
 * <p>Имена таблиц латиницей и не переводятся: по ним загрузка находит
 * таблицы, на каком бы листе и под какой подписью они ни лежали.
 */
final class DocumentExcel {

    private DocumentExcel() {
    }

    static final String GOODS = "Goods";
    static final String HEADER = "header";
    static final String TRANSPORT = "transport";

    /** Разделитель «код — имя» в значениях выпадающих списков. */
    static final String SEPARATOR = " — ";

    /** Больше строк состава загрузка не примет: решение заказчика от 27.10.2026. */
    static final int MAX_ROWS = 500;

    /**
     * Ячейка скрытого листа с перечнем колонок состава. Колонки цены
     * и валюты есть не у всех поклажедателей, а читается состав
     * по порядку колонок — загрузке надо знать, какие были в шаблоне.
     */
    static final String GOODS_LAYOUT_CELL = "B1";
    static final String GOODS_LAYOUT_PREFIX = "goods:";

    /** Общие тексты шаблона и общие причины отказа — у всех документов одни. */
    static final String TEXT = "excel.doc.";
    static final String ERROR = "error.doc_import.";

    /** Как заполняется значение поля. */
    enum Kind {
        TEXT, DATE, ORDER_TYPE, RECEIPT_TYPE, WAREHOUSE, CONSIGNEE, SUPPLIER, ADDRESS, CONTACT,
        TRANSPORT_TYPE
    }

    /**
     * Поле заголовка или транспорта.
     *
     * @param key       ключ в третьей колонке; он же идентификатор колонки
     *                  справочника колонок (таблица документа)
     * @param column    колонка таблицы документа в базе
     * @param kind      как заполняется
     * @param cardKey   подпись поля — та же, что в карточке документа
     *                  (03.11.2026: в шаблоне стояли подписи колонок
     *                  списка, «Номер» вместо «Номер документа (в ERP
     *                  клиента)»). Переименование поклажедателем главнее
     * @param inCatalog есть ли поле в справочнике колонок: такое поле
     *                  поклажедатель может закрыть, и тогда его нет в шаблоне
     */
    record Field(String key, String column, Kind kind, String cardKey, boolean inCatalog) {
    }

    /**
     * Чем документ отличается от соседа в Excel.
     *
     * @param type          документ
     * @param tableKey      таблица справочника колонок заголовка
     * @param compositionKey таблица справочника колонок состава — оттуда подписи колонок
     * @param linesTable    таблица строк в базе — оттуда длина комментария
     * @param qtyColumn     колонка планового количества строки
     * @param qtyCatalogId  колонка справочника состава с этим количеством
     * @param importKind    вид загрузки в журнале ({@code enum_import_kind})
     * @param texts         префикс своих текстов шаблона ({@code excel.order.})
     * @param errors        префикс своих причин отказа ({@code error.order_import.})
     * @param fields        поля заголовка в порядке шаблона
     */
    record Spec(DocumentType type, String tableKey, String compositionKey, String linesTable,
                String qtyColumn, String qtyCatalogId, String importKind, String texts,
                String errors, List<Field> fields) {

        /** Свой текст документа: «Состав заказа», «Состав поступления». */
        String text(String name) {
            return texts + name;
        }

        /** Своя причина отказа: «Товар в архиве — заказать его нельзя». */
        String error(String name) {
            return errors + name;
        }

        /** Подпись колонки состава берётся из справочника под этим именем. */
        String catalogIdOf(GoodsColumn c) {
            return c == QTY ? qtyCatalogId : c.catalogId();
        }
    }

    static Spec spec(DocumentType type) {
        return switch (type) {
            case ORDERS -> ORDER;
            case RECEIPTS -> RECEIPT;
            case TRANSFERS -> TRANSFER;
        };
    }

    /**
     * Поля заголовка заказа.
     *
     * <p>«Даты доставки» нет с V97: её ставит WMS (решение заказчика
     * от 02.11.2026), и в черновике портал её не принимает.
     */
    private static List<Field> orderFields() {
        List<Field> out = new ArrayList<>(List.of(
                new Field("orderType", "order_type_id", Kind.ORDER_TYPE, "col.orders.orderType", true),
                new Field("warehouse", "warehouse_id", Kind.WAREHOUSE, "sku.label.warehouse", true),
                new Field("erpNo", "erp_number", Kind.TEXT, "common.label.document_number_client_erp", true),
                new Field("plannedShip", "planned_ship_date", Kind.DATE, "col.orders.plannedShip", true),
                new Field("consignee", "consignee_id", Kind.CONSIGNEE, "partners.title.consignee", true),
                new Field("deliveryAddress", "delivery_point_id", Kind.ADDRESS, "col.orders.deliveryAddress", true),
                // Контакта в справочнике колонок нет — поле видно всем (V97)
                new Field("consigneeContact", "consignee_contact", Kind.CONTACT,
                        "orders.label.consignee_contact_details", false),
                new Field("comment", "comment", Kind.TEXT, "sku.label.comment", true)));
        out.addAll(customFields("orders"));
        return out;
    }

    /**
     * Поля заголовка поступления — в порядке карточки (V100).
     *
     * <p>Поставщик необязателен (решение заказчика от 03.11.2026): пустой —
     * не ошибка, а «Подтвердить» его не требует. Номера инвойса нет
     * в справочнике колонок — поле видно всем.
     */
    private static List<Field> receiptFields() {
        List<Field> out = new ArrayList<>(List.of(
                new Field("docType", "doc_type_id", Kind.RECEIPT_TYPE, "col.receipts.docType", true),
                new Field("warehouse", "warehouse_id", Kind.WAREHOUSE, "sku.label.warehouse", true),
                new Field("erpNo", "erp_number", Kind.TEXT, "common.label.document_number_client_erp", true),
                new Field("eta", "eta_date", Kind.DATE, "receipts.label.expected_delivery_date", true),
                new Field("supplier", "supplier_id", Kind.SUPPLIER, "col.receipts.supplier", true),
                new Field("accompanyingDocNumber", "accompanying_doc_number", Kind.TEXT,
                        "col.receipts.accompanyingDocNumber", true),
                new Field("invoiceNumber", "invoice_number", Kind.TEXT, "receipts.label.invoice_number", false),
                new Field("gtd", "gtd", Kind.TEXT, "col.receipts.gtd", true),
                new Field("comment", "comment", Kind.TEXT, "sku.label.comment", true)));
        out.addAll(customFields("receipts"));
        return out;
    }

    /**
     * Поля заголовка перемещения (V101). Складов два, и подставлять
     * единственный доступный нельзя; одинаковые склады — ошибка загрузки
     * (решение заказчика от 03.11.2026).
     */
    private static List<Field> transferFields() {
        List<Field> out = new ArrayList<>(List.of(
                new Field("fromWarehouse", "from_warehouse_id", Kind.WAREHOUSE, "col.transfers.fromWarehouse", true),
                new Field("toWarehouse", "to_warehouse_id", Kind.WAREHOUSE, "col.transfers.toWarehouse", true),
                new Field("erpNo", "erp_number", Kind.TEXT, "common.label.document_number_client_erp", true),
                new Field("comment", "comment", Kind.TEXT, "sku.label.comment", true)));
        out.addAll(customFields("transfers"));
        return out;
    }

    private static List<Field> customFields(String table) {
        List<Field> out = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            out.add(new Field("customField" + i, "custom_field_" + i, Kind.TEXT,
                    "col." + table + ".customField" + i, true));
        }
        return out;
    }

    static final Spec ORDER = new Spec(DocumentType.ORDERS, "orders", "orderComposition", "order_details",
            "ordered_qty", "ordered", EnumCodes.ImportKind.ORDER, "excel.order.", "error.order_import.",
            List.copyOf(orderFields()));

    static final Spec RECEIPT = new Spec(DocumentType.RECEIPTS, "receipts", "receiptComposition",
            "receipt_details", "expected_qty", "expected", EnumCodes.ImportKind.RECEIPT, "excel.receipt.",
            "error.receipt_import.", List.copyOf(receiptFields()));

    static final Spec TRANSFER = new Spec(DocumentType.TRANSFERS, "transfers", "transferComposition",
            "transfer_details", "qty", "qty", EnumCodes.ImportKind.TRANSFER, "excel.transfer.",
            "error.transfer_import.", List.copyOf(transferFields()));

    /**
     * Поля транспорта в порядке шаблона; есть только у кого право на транспорт.
     * У всех документов набор один.
     *
     * <p>«Рейса» нет с V97: его заводит WMS. «Тип транспорта» —
     * перечисление (V97), выбирается списком.
     */
    static List<Field> transportFields() {
        return List.of(
                new Field("carrier", "carrier", Kind.TEXT, "col.transfers.carrier", true),
                new Field("transportType", "transport_type", Kind.TRANSPORT_TYPE, "col.orders.transportType", true),
                new Field("transportBrand", "transport_brand", Kind.TEXT, "col.receipts.transportBrand", true),
                new Field("carNumber", "car_number", Kind.TEXT, "col.orders.carNumber", true),
                new Field("trailerNumber", "trailer_number", Kind.TEXT, "col.orders.trailerNumber", true),
                new Field("techPassportNumber", "tech_passport_number", Kind.TEXT,
                        "col.receipts.techPassportNumber", true),
                new Field("containerNumber", "container_number", Kind.TEXT, "col.receipts.containerNumber", true),
                new Field("sealNumber", "seal_number", Kind.TEXT, "col.receipts.sealNumber", true),
                new Field("driverName", "driver_name", Kind.TEXT, "common.label.driver_full_name", true),
                new Field("driverPhone", "driver_phone", Kind.TEXT, "col.receipts.driverPhone", true),
                new Field("driverIdNumber", "driver_id_number", Kind.TEXT, "col.orders.driverIdNumber", true),
                new Field("driverInn", "driver_inn", Kind.TEXT, "col.transfers.driverInn", true));
    }

    /**
     * Колонка состава.
     *
     * @param key       ключ (пишется в скрытый лист)
     * @param catalogId колонка справочника состава — оттуда подпись;
     *                  у количества она своя у каждого документа
     *                  ({@link Spec#catalogIdOf})
     */
    record GoodsColumn(String key, String catalogId) {
    }

    static final GoodsColumn CODE = new GoodsColumn("code", "code");
    static final GoodsColumn QTY = new GoodsColumn("qty", null);
    static final GoodsColumn QUALITY = new GoodsColumn("quality", "quality");
    static final GoodsColumn COMMENT = new GoodsColumn("comment", "lineComment");
    static final GoodsColumn PRICE = new GoodsColumn("price", "price");
    static final GoodsColumn CURRENCY = new GoodsColumn("currency", "currency");

    static List<GoodsColumn> allGoodsColumns() {
        return List.of(CODE, QTY, QUALITY, COMMENT, PRICE, CURRENCY);
    }

    /** «код — имя» для выпадающего списка. */
    static String option(String code, String name) {
        if (name == null || name.isBlank() || name.equals(code)) {
            return code;
        }
        return code + SEPARATOR + name;
    }

    /**
     * Код из значения ячейки: всё до {@link #SEPARATOR}. Значение без
     * разделителя считается кодом целиком — так его пишет тот, кто
     * заполняет файл без списка.
     */
    static String codeOf(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        int at = v.indexOf(SEPARATOR.trim());
        if (at > 0) {
            v = v.substring(0, at).trim();
        }
        return v;
    }
}
