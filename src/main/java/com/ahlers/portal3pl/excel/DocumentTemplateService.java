package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.excel.DocumentExcel.Field;
import com.ahlers.portal3pl.excel.DocumentExcel.GoodsColumn;
import com.ahlers.portal3pl.excel.DocumentExcel.Kind;
import com.ahlers.portal3pl.excel.DocumentExcel.Spec;
import com.ahlers.portal3pl.excel.SkuTemplateService.Template;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Шаблон Excel для загрузки документа: заказа (V96), поступления (V100)
 * и перемещения (V101).
 *
 * <p>Устройство файла описано в {@link DocumentExcel}. Здесь — что в него
 * попадает:
 * <ul>
 *   <li>в заголовке — только поля, которые человек заполняет руками и которые
 *       открыты этому поклажедателю, с его подписями;</li>
 *   <li>транспорт — только тому, у кого есть право на транспорт;</li>
 *   <li>в составе — код, количество, качество, комментарий; цена и валюта —
 *       если у поклажедателя включена цена в этом документе (V61);</li>
 *   <li>выпадающие списки — «код — имя» на языке пользователя: типы
 *       документа, склады, грузополучатели или поставщики, качество,
 *       валюты, типы транспорта; места доставки заказа — по выбранному
 *       грузополучателю. Все списки — на видимом листе «Списки» (V97);</li>
 *   <li>кода товара списком нет (V97): товаров бывают тысячи;</li>
 *   <li>у каждой ячейки — проверка данных: список, число, дата или
 *       длина текста по колонке базы.</li>
 * </ul>
 *
 * <p>Все тексты файла — из каталога текстов, на языке того, кто скачал
 * шаблон. Шаблон собирается сам, без {@link ExcelTemplateWriter#write}:
 * там одна таблица на лист, а здесь три.
 */
@Service
public class DocumentTemplateService {

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final ColumnService columns;
    private final CurrentUser current;
    private final ActorText texts;

    public DocumentTemplateService(JdbcClient jdbc, DepositorScope depositorScope,
                                ColumnService columns, CurrentUser current, ActorText texts) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.columns = columns;
        this.current = current;
        this.texts = texts;
    }

    /** Что попадёт в шаблон этому пользователю — общее с загрузкой. */
    record Plan(Spec spec, List<Field> header, List<Field> transport, List<GoodsColumn> goods,
                Map<String, String> labels) {
    }

    /**
     * Состав шаблона для текущего пользователя и поклажедателя.
     *
     * <p>Поле справочника колонок, закрытое поклажедателю, в раскладке
     * отсутствует — его не будет и в шаблоне, и загрузка его не примет.
     */
    Plan plan(PortalUser user, Spec spec) {
        List<Map<String, Object>> headerLayout = columns.layout(user, spec.tableKey());
        Map<String, String> visible = labels(headerLayout);
        Map<String, String> renamed = renames(headerLayout);
        Map<String, String> goodsLabels = labels(columns.layout(user, spec.compositionKey()));

        Map<String, String> labels = new HashMap<>();
        List<Field> header = new ArrayList<>();
        for (Field f : spec.fields()) {
            if (!f.inCatalog() || visible.containsKey(f.key())) {
                header.add(f);
                labels.put(f.key(), label(f, renamed));
            }
        }
        List<Field> transport = new ArrayList<>();
        if (current.mayEditTransport()) {
            for (Field f : DocumentExcel.transportFields()) {
                if (!f.inCatalog() || visible.containsKey(f.key())) {
                    transport.add(f);
                    labels.put(f.key(), label(f, renamed));
                }
            }
        }
        List<GoodsColumn> goods = new ArrayList<>(List.of(DocumentExcel.CODE, DocumentExcel.QTY,
                DocumentExcel.QUALITY, DocumentExcel.COMMENT));
        if (priceIn(spec)) {
            goods.add(DocumentExcel.PRICE);
            goods.add(DocumentExcel.CURRENCY);
        }
        for (GoodsColumn c : goods) {
            labels.put("goods." + c.key(), goodsLabels.getOrDefault(spec.catalogIdOf(c),
                    texts.of(DocumentExcel.TEXT + "goods_" + c.key())));
        }
        return new Plan(spec, header, transport, goods, labels);
    }

    /**
     * Цена в строках — своя настройка у заказа и поступления (V61).
     * У перемещения цены нет: товар не меняет владельца.
     */
    private boolean priceIn(Spec spec) {
        return switch (spec.type()) {
            case ORDERS -> current.priceInOrders();
            case RECEIPTS -> current.priceInReceipts();
            case TRANSFERS -> false;
        };
    }

    /** Создавать документ вправе не каждый: кнопка, скрытая на экране, не закрывает адрес. */
    void requireMayCreate(Spec spec) {
        current.requireMayCreate(spec.type());
    }

    /**
     * Подпись поля — как в карточке документа: переименование
     * поклажедателя, иначе перевод ключа карточки. Подписи колонок
     * списка для этого не годятся — там «Номер», а в карточке
     * «Номер документа (в ERP клиента)».
     */
    private String label(Field f, Map<String, String> renamed) {
        String own = renamed.get(f.key());
        return own != null ? own : texts.of(f.cardKey());
    }

    private static Map<String, String> labels(List<Map<String, Object>> layout) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> row : layout) {
            out.put(String.valueOf(row.get("columnId")), String.valueOf(row.get("label")));
        }
        return out;
    }

    /** Колонки, которые поклажедатель переименовал: подпись отличается от портальной. */
    private static Map<String, String> renames(List<Map<String, Object>> layout) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, Object> row : layout) {
            Object label = row.get("label");
            Object portal = row.get("portalLabel");
            if (label != null && portal != null && !label.equals(portal)) {
                out.put(String.valueOf(row.get("columnId")), String.valueOf(label));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Template build(PortalUser user, DocumentType type) {
        Spec spec = DocumentExcel.spec(type);
        requireMayCreate(spec);
        depositorScope.apply();
        Map<String, Object> depositor = SkuTemplateService.depositor(jdbc, depositorScope);
        Plan plan = plan(user, spec);
        Lists lists = lists(plan);
        try {
            byte[] bytes = write(plan, lists, SkuTemplateService.str(depositor.get("code")));
            return new Template(ExcelTemplateWriter.fileName(texts.of(spec.text("file_name")),
                    SkuTemplateService.str(depositor.get("org_name"))), bytes);
        } catch (IOException e) {
            throw Fail.internal("error.template.build_failed");
        }
    }

    /* ============================== списки ============================== */

    /**
     * Значения выпадающих списков.
     *
     * @param values    имя списка → «код — имя»
     * @param addresses места доставки: «код — имя» грузополучателя → адреса;
     *                  порядок — по грузополучателю, так их и разложит лист.
     *                  Пусто, если в заголовке нет места доставки
     */
    record Lists(Map<String, List<String>> values, Map<String, List<String>> addresses) {
    }

    private static final String L_ORDER_TYPE = "orderType";
    private static final String L_RECEIPT_TYPE = "docType";
    private static final String L_WAREHOUSE = "warehouse";
    private static final String L_CONSIGNEE = "consignee";
    private static final String L_SUPPLIER = "supplier";
    private static final String L_QUALITY = "quality";
    private static final String L_CURRENCY = "currency";
    private static final String L_TRANSPORT_TYPE = "transportType";

    /**
     * Списки — только те, что нужны этому шаблону: у поступления нет
     * грузополучателей, у заказа — поставщиков. Лишний список на листе
     * «Списки» человека только путал бы.
     */
    private Lists lists(Plan plan) {
        java.util.Set<Kind> kinds = java.util.EnumSet.noneOf(Kind.class);
        plan.header().forEach(f -> kinds.add(f.kind()));
        plan.transport().forEach(f -> kinds.add(f.kind()));

        Map<String, List<String>> out = new LinkedHashMap<>();
        if (kinds.contains(Kind.ORDER_TYPE)) {
            out.put(L_ORDER_TYPE, options("""
                    SELECT code, dbo.enum_name('enum_order_type', id::text, name) AS name
                    FROM dbo.enum_order_type WHERE COALESCE(is_active, true)
                    ORDER BY COALESCE(sort_order, id), code
                    """));
        }
        if (kinds.contains(Kind.RECEIPT_TYPE)) {
            out.put(L_RECEIPT_TYPE, options("""
                    SELECT code, dbo.enum_name('enum_receipt_doc_type', id::text, name) AS name
                    FROM dbo.enum_receipt_doc_type WHERE COALESCE(is_active, true)
                    ORDER BY COALESCE(sort_order, id), code
                    """));
        }
        if (kinds.contains(Kind.WAREHOUSE)) {
            out.put(L_WAREHOUSE, options("""
                    SELECT warehouse_id AS code, name FROM dbo.v_warehouses
                    WHERE COALESCE(is_active, true) ORDER BY name
                    """));
        }
        if (kinds.contains(Kind.CONSIGNEE)) {
            out.put(L_CONSIGNEE, options("""
                    SELECT code, name FROM dbo.consignee WHERE is_active ORDER BY name
                    """));
        }
        if (kinds.contains(Kind.SUPPLIER)) {
            out.put(L_SUPPLIER, options("""
                    SELECT code, name FROM dbo.supplier WHERE is_active ORDER BY name
                    """));
        }
        out.put(L_QUALITY, options("""
                SELECT code, dbo.enum_name('enum_quality', id::text, name) AS name
                FROM dbo.enum_quality WHERE COALESCE(is_active, true)
                ORDER BY COALESCE(sort_order, id), code
                """));
        // Валюты нужны, только когда в составе есть цена (V61)
        if (plan.goods().contains(DocumentExcel.CURRENCY)) {
            out.put(L_CURRENCY, options("""
                    SELECT code, dbo.enum_name('enum_currency', id::text, name) AS name
                    FROM dbo.enum_currency WHERE COALESCE(is_active, true)
                    ORDER BY COALESCE(sort_order, id), code
                    """));
        }
        if (kinds.contains(Kind.TRANSPORT_TYPE)) {
            out.put(L_TRANSPORT_TYPE, options("""
                    SELECT code, dbo.enum_name('enum_transport_type', id::text, name) AS name
                    FROM dbo.enum_transport_type WHERE COALESCE(is_active, true)
                    ORDER BY COALESCE(sort_order, id), code
                    """));
        }

        // Места доставки — по грузополучателю: список в ячейке показывает
        // адреса того, кто выбран в строке «Грузополучатель». Основной
        // адрес — первым.
        Map<String, List<String>> addresses = new LinkedHashMap<>();
        if (kinds.contains(Kind.ADDRESS)) {
            for (Map<String, Object> r : jdbc.sql("""
                            SELECT c.code, c.name, dp.address
                            FROM dbo.consignee c
                            JOIN dbo.delivery_point dp ON dp.consignee_id = c.consignee_id
                            WHERE c.is_active AND dp.address IS NOT NULL AND btrim(dp.address) <> ''
                            ORDER BY c.name, c.code, COALESCE(dp.is_default, false) DESC,
                                     dp.sort_order NULLS LAST, dp.address
                            """).query().listOfRows()) {
                addresses.computeIfAbsent(DocumentExcel.option(String.valueOf(r.get("code")),
                                r.get("name") == null ? null : String.valueOf(r.get("name"))),
                        k -> new ArrayList<>()).add(String.valueOf(r.get("address")));
            }
        }
        return new Lists(out, addresses);
    }

    private List<String> options(String sql) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.sql(sql).query().listOfRows()) {
            out.add(DocumentExcel.option(String.valueOf(r.get("code")),
                    r.get("name") == null ? null : String.valueOf(r.get("name"))));
        }
        return out;
    }

    private static String listOf(Kind kind) {
        return switch (kind) {
            case ORDER_TYPE -> L_ORDER_TYPE;
            case RECEIPT_TYPE -> L_RECEIPT_TYPE;
            case WAREHOUSE -> L_WAREHOUSE;
            case CONSIGNEE -> L_CONSIGNEE;
            case SUPPLIER -> L_SUPPLIER;
            case TRANSPORT_TYPE -> L_TRANSPORT_TYPE;
            default -> null;
        };
    }

    /** Подпись списка на листе «Списки» — та же, что у поля в шапке. */
    private static String listTitle(String list, Plan plan) {
        return switch (list) {
            case L_QUALITY -> plan.labels().get("goods." + DocumentExcel.QUALITY.key());
            case L_CURRENCY -> plan.labels().getOrDefault("goods." + DocumentExcel.CURRENCY.key(), list);
            default -> plan.labels().getOrDefault(list, list);
        };
    }

    /* ============================== книга ============================== */

    /** Где на листе «Списки» лежит список. */
    private record Placed(String ref, String keysRef, String valuesFirst) {
    }

    /** Первая строка значений на листе «Списки»: выше пояснение и подписи. */
    private static final int LIST_FIRST_ROW = 3;

    private byte[] write(Plan plan, Lists lists, String depositorCode) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Spec spec = plan.spec();
            XSSFSheet sheet = wb.createSheet(safeSheetName(texts.of(spec.text("sheet")),
                    switch (spec.type()) {
                        case ORDERS -> "Order";
                        case RECEIPTS -> "Receipt";
                        case TRANSFERS -> "Transfer";
                    }));
            XSSFSheet listSheet = wb.createSheet(safeSheetName(texts.of(DocumentExcel.TEXT + "lists_sheet"), "Lists"));
            Sheet dict = wb.createSheet(ExcelTemplateWriter.DICT_SHEET);
            Styles st = new Styles(wb);

            // Скрытый лист — только служебное: метка клиента и колонки состава.
            ExcelTemplateWriter.writeMarker(dict, depositorCode);
            CellReference layoutCell = new CellReference(DocumentExcel.GOODS_LAYOUT_CELL);
            dict.getRow(layoutCell.getRow()).createCell(layoutCell.getCol())
                    .setCellValue(DocumentExcel.GOODS_LAYOUT_PREFIX + String.join(",",
                            plan.goods().stream().map(GoodsColumn::key).toList()));

            Map<String, Placed> placed = writeLists(listSheet, lists, plan, st);

            Row titles = sheet.createRow(0);
            Row heads = sheet.createRow(1);
            DataValidationHelper helper = sheet.getDataValidationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper factory = wb.getCreationHelper();

            // 1. Состав
            int n = plan.goods().size();
            title(titles, 0, texts.of(spec.text("title_goods")), st.title);
            for (int i = 0; i < n; i++) {
                GoodsColumn c = plan.goods().get(i);
                boolean required = c == DocumentExcel.CODE || c == DocumentExcel.QTY;
                Cell cell = heads.createCell(i);
                cell.setCellValue(plan.labels().get("goods." + c.key()) + (required ? " *" : ""));
                cell.setCellStyle(required ? st.headRequired : st.head);
                sheet.setColumnWidth(i, (c == DocumentExcel.COMMENT ? 32 : 20) * 256);
            }
            sheet.setDefaultColumnStyle(0, st.text);
            // Таблица сразу на все допустимые строки (замечание от 03.11.2026):
            // человек вставляет состав из своей выгрузки целиком и видит
            // рамку таблицы до самого предела. Пустые строки загрузка
            // пропускает, так что лишние ничему не мешают.
            table(wb, sheet, DocumentExcel.GOODS, 1, 0, DocumentExcel.MAX_ROWS + 1, n - 1);
            goodsValidation(sheet, helper, plan, placed, drawing, factory, heads);

            // 2. Заголовок и 3. транспорт — «Поле | Значение | Ключ»
            int headerCol = n + 1;
            writeFields(wb, sheet, plan, plan.header(), headerCol, DocumentExcel.HEADER,
                    texts.of(spec.text("title_header")), titles, st, placed, helper, drawing, factory);
            if (!plan.transport().isEmpty()) {
                writeFields(wb, sheet, plan, plan.transport(), headerCol + 4,
                        DocumentExcel.TRANSPORT, texts.of(DocumentExcel.TEXT + "title_transport"), titles, st,
                        placed, helper, drawing, factory);
            }

            sheet.createFreezePane(0, 2);
            ExcelTemplateWriter.showDropDowns(sheet);
            wb.setSheetHidden(wb.getSheetIndex(dict), true);
            wb.setActiveSheet(wb.getSheetIndex(sheet));
            wb.setFirstVisibleTab(wb.getSheetIndex(sheet));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Видимый лист «Списки» (V97): из чего выбирать в ячейках листа
     * документа. Человек может открыть его и посмотреть варианты целиком —
     * в выпадающем списке ячейки их видно по восемь.
     *
     * <p>Места доставки — двумя колонками «грузополучатель | адрес»,
     * подряд по грузополучателю: список в ячейке места доставки берёт
     * из них только строки выбранного грузополучателя.
     */
    private Map<String, Placed> writeLists(XSSFSheet sheet, Lists lists, Plan plan, Styles st) {
        Map<String, Placed> placed = new HashMap<>();
        String q = "'" + sheet.getSheetName().replace("'", "''") + "'!";
        Row hint = sheet.createRow(0);
        Cell h = hint.createCell(0);
        h.setCellValue(texts.of(plan.spec().text("lists_hint")));
        Row heads = sheet.createRow(LIST_FIRST_ROW - 1);

        int col = 0;
        for (Map.Entry<String, List<String>> e : lists.values().entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            Cell head = heads.createCell(col);
            head.setCellValue(listTitle(e.getKey(), plan));
            head.setCellStyle(st.head);
            List<String> values = e.getValue();
            for (int i = 0; i < values.size(); i++) {
                row(sheet, LIST_FIRST_ROW + i).createCell(col).setCellValue(values.get(i));
            }
            String letter = CellReference.convertNumToColString(col);
            placed.put(e.getKey(), new Placed(q + "$" + letter + "$" + (LIST_FIRST_ROW + 1)
                    + ":$" + letter + "$" + (LIST_FIRST_ROW + values.size()), null, null));
            sheet.setColumnWidth(col, 30 * 256);
            col++;
        }

        if (!lists.addresses().isEmpty()) {
            col++;                                   // пустая колонка — граница
            int keyCol = col;
            int valueCol = col + 1;
            Cell k = heads.createCell(keyCol);
            k.setCellValue(texts.of("excel.order.lists_address_consignee"));
            k.setCellStyle(st.head);
            Cell v = heads.createCell(valueCol);
            v.setCellValue(plan.labels().getOrDefault("deliveryAddress", "deliveryAddress"));
            v.setCellStyle(st.head);
            int r = LIST_FIRST_ROW;
            for (Map.Entry<String, List<String>> e : lists.addresses().entrySet()) {
                for (String address : e.getValue()) {
                    Row row = row(sheet, r++);
                    row.createCell(keyCol).setCellValue(e.getKey());
                    row.createCell(valueCol).setCellValue(address);
                }
            }
            String kl = CellReference.convertNumToColString(keyCol);
            String vl = CellReference.convertNumToColString(valueCol);
            int last = r;                            // номер последней строки, считая с 1
            placed.put("address", new Placed(null,
                    q + "$" + kl + "$" + (LIST_FIRST_ROW + 1) + ":$" + kl + "$" + last,
                    q + "$" + vl + "$" + (LIST_FIRST_ROW + 1)));
            sheet.setColumnWidth(keyCol, 34 * 256);
            sheet.setColumnWidth(valueCol, 48 * 256);
        }
        sheet.createFreezePane(0, LIST_FIRST_ROW);
        return placed;
    }

    private void goodsValidation(XSSFSheet sheet, DataValidationHelper helper, Plan plan,
                                 Map<String, Placed> placed, Drawing<?> drawing,
                                 CreationHelper factory, Row heads) {
        for (int i = 0; i < plan.goods().size(); i++) {
            GoodsColumn c = plan.goods().get(i);
            CellRangeAddressList range = new CellRangeAddressList(2, DocumentExcel.MAX_ROWS + 1, i, i);
            String note;
            if (c == DocumentExcel.CODE) {
                // Кода товара списком нет (V97): товаров бывают тысячи.
                // Код сверяет с базой загрузка; здесь — только длина.
                Integer limit = limitOf("sku", "code");
                textLength(sheet, helper, range, limit);
                note = texts.of(DocumentExcel.TEXT + "note_code");
            } else if (c == DocumentExcel.QTY) {
                number(sheet, helper, range, true);
                note = texts.of(DocumentExcel.TEXT + "note_qty");
            } else if (c == DocumentExcel.QUALITY) {
                list(sheet, helper, range, ref(placed, L_QUALITY));
                note = texts.of(DocumentExcel.TEXT + "note_quality");
            } else if (c == DocumentExcel.PRICE) {
                number(sheet, helper, range, false);
                note = null;
            } else if (c == DocumentExcel.CURRENCY) {
                list(sheet, helper, range, ref(placed, L_CURRENCY));
                note = texts.of(DocumentExcel.TEXT + "note_list");
            } else {
                Integer limit = limitOf(plan.spec().linesTable(), "line_comment");
                textLength(sheet, helper, range, limit);
                note = limit == null ? null : texts.of(DocumentExcel.TEXT + "note_text", "limit", limit);
            }
            if (note != null) {
                comment(drawing, factory, heads.getCell(i), note);
            }
        }
    }

    private static String ref(Map<String, Placed> placed, String list) {
        Placed p = placed.get(list);
        return p == null ? null : p.ref();
    }

    private void writeFields(XSSFWorkbook wb, XSSFSheet sheet, Plan plan, List<Field> fields,
                             int col, String tableName, String title,
                             Row titles, Styles st, Map<String, Placed> placed,
                             DataValidationHelper helper, Drawing<?> drawing,
                             CreationHelper factory) {
        title(titles, col, title, st.title);
        Row heads = sheet.getRow(1);
        Map<String, String> labels = plan.labels();
        String headerTable = plan.spec().type().headerTable();
        String[] names = {texts.of(DocumentExcel.TEXT + "column_field"), texts.of(DocumentExcel.TEXT + "column_value"),
                texts.of(DocumentExcel.TEXT + "column_key")};
        for (int i = 0; i < 3; i++) {
            Cell cell = heads.createCell(col + i);
            cell.setCellValue(names[i]);
            cell.setCellStyle(st.head);
        }
        comment(drawing, factory, heads.getCell(col + 2), texts.of(DocumentExcel.TEXT + "note_key"));

        // Ячейка грузополучателя — от неё зависит список мест доставки
        String consigneeCell = null;
        for (int r = 0; r < fields.size(); r++) {
            if (fields.get(r).kind() == Kind.CONSIGNEE) {
                consigneeCell = "$" + CellReference.convertNumToColString(col + 1) + "$" + (2 + r + 1);
            }
        }

        for (int r = 0; r < fields.size(); r++) {
            Field f = fields.get(r);
            int rowIndex = 2 + r;
            Row row = row(sheet, rowIndex);
            Cell label = row.createCell(col);
            // Номер ERP обязателен (V98) — отмечен так же, как обязательные колонки состава
            boolean required = "erpNo".equals(f.key());
            label.setCellValue(labels.getOrDefault(f.key(), f.key()) + (required ? " *" : ""));
            if (required) {
                label.setCellStyle(st.headRequired);
            }
            Cell value = row.createCell(col + 1);
            Cell key = row.createCell(col + 2);
            key.setCellValue(f.key());
            key.setCellStyle(st.key);

            CellRangeAddressList one = new CellRangeAddressList(rowIndex, rowIndex, col + 1, col + 1);
            switch (f.kind()) {
                case DATE -> {
                    value.setCellStyle(st.date);
                    date(sheet, helper, one);
                    comment(drawing, factory, label, texts.of(DocumentExcel.TEXT + "note_date"));
                }
                case ADDRESS -> {
                    value.setCellStyle(st.text);
                    Placed a = placed.get("address");
                    if (a != null && consigneeCell != null) {
                        // Адреса выбранного грузополучателя: с первой его строки
                        // в колонке адресов, столько строк, сколько у него адресов.
                        String formula = "OFFSET(" + a.valuesFirst() + ",MATCH(" + consigneeCell + ","
                                + a.keysRef() + ",0)-1,0,COUNTIF(" + a.keysRef() + "," + consigneeCell
                                + "),1)";
                        list(sheet, helper, one, formula);
                    }
                    comment(drawing, factory, label, texts.of("excel.order.note_address"));
                }
                case CONTACT -> {
                    value.setCellStyle(st.text);
                    textLength(sheet, helper, one, limitOf(headerTable, f.column()));
                    comment(drawing, factory, label, texts.of("excel.order.note_contact"));
                }
                case TEXT -> {
                    value.setCellStyle(st.text);
                    Integer limit = limitOf(headerTable, f.column());
                    textLength(sheet, helper, one, limit);
                    if (limit != null) {
                        comment(drawing, factory, label, texts.of(DocumentExcel.TEXT + "note_text", "limit", limit));
                    }
                }
                default -> {
                    list(sheet, helper, one, ref(placed, listOf(f.kind())));
                    comment(drawing, factory, label, texts.of(DocumentExcel.TEXT + "note_list"));
                }
            }
        }
        sheet.setColumnWidth(col, 34 * 256);
        sheet.setColumnWidth(col + 1, 40 * 256);
        sheet.setColumnWidth(col + 2, 18 * 256);
        table(wb, sheet, tableName, 1, col, 1 + Math.max(1, fields.size()), col + 2);
    }

    private Integer limitOf(String table, String column) {
        return jdbc.sql("""
                        SELECT character_maximum_length FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = :t AND column_name = :c
                        """)
                .param("t", table).param("c", column)
                .query(Integer.class).optional().orElse(null);
    }

    private static Row row(Sheet sheet, int index) {
        return sheet.getRow(index) != null ? sheet.getRow(index) : sheet.createRow(index);
    }

    /* ============================== мелочи ============================== */

    private void list(XSSFSheet sheet, DataValidationHelper helper, CellRangeAddressList range,
                      String ref) {
        if (ref == null) {
            return;              // список пуст — проверять не по чему
        }
        add(sheet, helper, helper.createFormulaListConstraint(ref), range,
                texts.of(DocumentExcel.TEXT + "validation_list_title"),
                texts.of(DocumentExcel.TEXT + "validation_list_message"));
    }

    private void number(XSSFSheet sheet, DataValidationHelper helper, CellRangeAddressList range,
                        boolean positive) {
        DataValidationConstraint c = positive
                ? helper.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_THAN, "0", null)
                : helper.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null);
        add(sheet, helper, c, range,
                texts.of(DocumentExcel.TEXT + (positive ? "validation_qty_title" : "validation_price_title")),
                texts.of(DocumentExcel.TEXT + (positive ? "validation_qty_message" : "validation_price_message")));
    }

    /** Текст не длиннее колонки базы — иначе загрузка всё равно откажет. */
    private void textLength(XSSFSheet sheet, DataValidationHelper helper, CellRangeAddressList range,
                            Integer limit) {
        if (limit == null) {
            return;
        }
        DataValidationConstraint c = helper.createTextLengthConstraint(
                DataValidationConstraint.OperatorType.LESS_OR_EQUAL, String.valueOf(limit), null);
        add(sheet, helper, c, range, texts.of(DocumentExcel.TEXT + "validation_text_title"),
                texts.of(DocumentExcel.TEXT + "validation_text_message", "limit", limit));
    }

    private void date(XSSFSheet sheet, DataValidationHelper helper, CellRangeAddressList range) {
        DataValidationConstraint c = helper.createDateConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, "DATE(2000,1,1)", "DATE(2100,12,31)", null);
        add(sheet, helper, c, range, texts.of(DocumentExcel.TEXT + "validation_date_title"),
                texts.of(DocumentExcel.TEXT + "validation_date_message"));
    }

    private static void add(XSSFSheet sheet, DataValidationHelper helper, DataValidationConstraint c,
                            CellRangeAddressList range, String title, String message) {
        DataValidation v = helper.createValidation(c, range);
        v.setShowErrorBox(true);
        v.setErrorStyle(DataValidation.ErrorStyle.STOP);
        v.createErrorBox(title, message);
        v.setEmptyCellAllowed(true);
        sheet.addValidationData(v);
    }

    private static void title(Row titles, int col, String text, CellStyle style) {
        Cell cell = titles.createCell(col);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private static void table(XSSFWorkbook wb, XSSFSheet sheet, String name,
                              int row1, int col1, int row2, int col2) {
        AreaReference area = new AreaReference(new CellReference(row1, col1),
                new CellReference(row2, col2), wb.getSpreadsheetVersion());
        for (int r = row1; r <= row2; r++) {
            Row row = sheet.getRow(r) != null ? sheet.getRow(r) : sheet.createRow(r);
            for (int c = col1; c <= col2; c++) {
                if (row.getCell(c) == null) {
                    row.createCell(c);
                }
            }
        }
        XSSFTable table = sheet.createTable(area);
        table.setName(name);
        table.setDisplayName(name);
        if (!table.getCTTable().isSetAutoFilter()) {
            table.getCTTable().addNewAutoFilter().setRef(area.formatAsString());
        }
        // Оформление умной таблицы: без него три таблицы на листе
        // неотличимы от простых ячеек, и не видно, где кончается одна.
        var style = table.getCTTable().isSetTableStyleInfo()
                ? table.getCTTable().getTableStyleInfo() : table.getCTTable().addNewTableStyleInfo();
        style.setName("TableStyleLight9");
        style.setShowRowStripes(true);
        table.updateHeaders();
    }

    private static void comment(Drawing<?> drawing, CreationHelper factory, Cell cell, String text) {
        if (cell == null || text == null || text.isBlank()) {
            return;
        }
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 4);
        Comment c = drawing.createCellComment(anchor);
        c.setString(factory.createRichTextString(text));
        cell.setCellComment(c);
    }

    /** В имени листа Excel нельзя []:*?/\ и больше 31 знака. */
    private static String safeSheetName(String name, String fallback) {
        String safe = name == null ? "" : name.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (safe.length() > 31) {
            safe = safe.substring(0, 31);
        }
        return safe.isEmpty() ? fallback : safe;
    }

    /** Стили книги: создаются один раз — Excel ограничивает их число. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle head;
        final CellStyle headRequired;
        final CellStyle key;
        final CellStyle text;
        final CellStyle date;

        Styles(XSSFWorkbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 12);
            Font grey = wb.createFont();
            grey.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            title = wb.createCellStyle();
            title.setFont(big);
            head = wb.createCellStyle();
            head.setFont(bold);
            head.setWrapText(true);
            headRequired = wb.createCellStyle();
            headRequired.cloneStyleFrom(head);
            headRequired.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            headRequired.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            key = wb.createCellStyle();
            key.setFont(grey);
            text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("dd.mm.yyyy"));
        }
    }
}
