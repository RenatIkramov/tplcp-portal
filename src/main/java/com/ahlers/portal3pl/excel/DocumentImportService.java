package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.EnumCodes;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.excel.DocumentExcel.Field;
import com.ahlers.portal3pl.excel.DocumentExcel.GoodsColumn;
import com.ahlers.portal3pl.excel.DocumentExcel.Kind;
import com.ahlers.portal3pl.excel.DocumentExcel.Spec;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Загрузка документа из Excel: заказа (V96), поступления (V100) и перемещения (V101).
 * Один файл — один черновик.
 *
 * <p>Решения заказчика от 27.10 и 03.11.2026:
 * <ul>
 *   <li>окна проверки нет. Есть ошибки — человек получает их списком
 *       (таблица, строка, поле, что не так), и ничего не создаётся.
 *       Ошибок нет — создаётся черновик, и экран сразу открывает его:
 *       карточка и есть окно проверки, подтверждает человек сам;</li>
 *   <li>повторы товара складываются, но только при совпадении товара,
 *       качества и комментария. Разная цена у такого повтора — ошибка:
 *       какую из двух взять, портал не знает;</li>
 *   <li>пустые склад, грузополучатель, поставщик или плановая дата — не ошибка.
 *       Черновик сохранится неполным, а чего не хватает, скажет
 *       «Подтвердить» — как и при вводе вручную.</li>
 * </ul>
 *
 * <p>Проверки Excel (списки, числа) опорой не служат: вставка готовых
 * строк их обходит. Поэтому каждое значение сверяется с базой заново,
 * на момент загрузки.
 *
 * <p>Каждая загрузка пишется в журнал администратора (вид «Заказ» или «Поступление»):
 * номер созданного черновика или причины отказа.
 */
@Service
public class DocumentImportService {

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final CurrentUser current;
    private final ActorText texts;
    private final ExcelImportSupport support;
    private final DocumentTemplateService template;
    private final DraftService drafts;

    public DocumentImportService(JdbcClient jdbc, DepositorScope depositorScope, CurrentUser current,
                                 ActorText texts, ExcelImportSupport support,
                                 DocumentTemplateService template, DraftService drafts) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.current = current;
        this.texts = texts;
        this.support = support;
        this.template = template;
        this.drafts = drafts;
    }

    /**
     * Причина отказа.
     *
     * @param table {@code Goods}, {@code header}, {@code transport} или пусто — файл целиком
     * @param row   строка листа Excel (как её видит человек), 0 — файл целиком
     * @param key   ключ поля или колонки состава
     * @param field подпись поля на языке пользователя
     */
    public record Issue(String table, int row, String key, String field, String message,
                        String value, String messageKey, Map<String, Object> messageParams) {
    }

    /**
     * Разобрать файл и, если ошибок нет, создать черновик.
     *
     * @return {@code {ok: true, docId, documentNo}} или {@code {ok: false, issues: [...]}}
     */
    @Transactional
    public Map<String, Object> importFile(PortalUser user, DocumentType type, String fileName,
                                          byte[] content) {
        Spec spec = DocumentExcel.spec(type);
        template.requireMayCreate(spec);
        support.requireFile(fileName, content);
        depositorScope.apply();
        support.cleanup();

        DocumentTemplateService.Plan plan = template.plan(user, spec);
        Parse p = new Parse(plan);

        try (XSSFWorkbook wb = open(content)) {
            support.requireOwnFile(wb, fileName);
            p.read(wb);
        } catch (IOException e) {
            throw Fail.badRequest("error.excel.unreadable");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (!p.issues.isEmpty()) {
            log(user, fileName, content.length, p, null, null, EnumCodes.ImportStatus.REJECTED);
            out.put("ok", false);
            out.put("issues", p.issues);
            return out;
        }

        Map<String, Object> card = drafts.create(type, p.header);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        String docId = String.valueOf(header.get("docId"));
        Map<String, Object> saved = drafts.replaceLines(type, docId, p.lines);
        String documentNo = header.get("documentNo") == null ? null : String.valueOf(header.get("documentNo"));
        log(user, fileName, content.length, p, docId, documentNo, EnumCodes.ImportStatus.APPLIED);

        out.put("ok", true);
        out.put("docId", docId);
        out.put("documentNo", documentNo);
        out.put("lineCount", p.lines.size());
        // комплекты поступления и перемещения разложены на комплектующие (V153)
        if (saved.get("expandedKits") != null) {
            out.put("expandedKits", saved.get("expandedKits"));
        }
        return out;
    }

    /* ============================== разбор ============================== */

    /** Разбор одного файла: что прочитано и что не так. */
    private final class Parse {

        final DocumentTemplateService.Plan plan;
        final Spec spec;
        final List<Issue> issues = new ArrayList<>();
        final Map<String, Object> header = new LinkedHashMap<>();
        final List<Map<String, Object>> lines = new ArrayList<>();
        /** Строка листа каждого поля шапки и транспорта — для причин о пустом поле. */
        final Map<String, Integer> rowOfKey = new HashMap<>();
        int goodsRows;
        final DataFormatter fmt = new DataFormatter(Locale.ROOT);

        Parse(DocumentTemplateService.Plan plan) {
            this.plan = plan;
            this.spec = plan.spec();
        }

        void read(XSSFWorkbook wb) {
            XSSFTable goods = table(wb, DocumentExcel.GOODS);
            XSSFTable head = table(wb, DocumentExcel.HEADER);
            XSSFTable transport = table(wb, DocumentExcel.TRANSPORT);

            if (head != null) {
                readFields(head, DocumentExcel.HEADER);
            }
            if (transport != null) {
                readFields(transport, DocumentExcel.TRANSPORT);
            }
            erpNumber();
            sameWarehouse();
            if (goods == null) {
                issue("", 0, null, null, spec.error("no_goods_table"), null);
                return;
            }
            readGoods(wb, goods);
        }

        /* ------------------------ заголовок и транспорт ------------------------ */

        private void readFields(XSSFTable table, String tableName) {
            Map<String, Field> allowed = new HashMap<>();
            List<Field> fields = DocumentExcel.HEADER.equals(tableName) ? plan.header() : plan.transport();
            fields.forEach(f -> allowed.put(f.key(), f));
            Map<String, Field> known = new HashMap<>();
            spec.fields().forEach(f -> known.put(f.key(), f));
            DocumentExcel.transportFields().forEach(f -> known.put(f.key(), f));

            XSSFSheet sheet = table.getXSSFSheet();
            AreaReference area = table.getArea();
            int c0 = area.getFirstCell().getCol();
            String consigneeId = null;
            String addressText = null;
            int addressRow = 0;
            for (int r = area.getFirstCell().getRow() + 1; r <= area.getLastCell().getRow(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String key = ExcelImportSupport.text(row, c0 + 2, fmt);
                Cell cell = row.getCell(c0 + 1);
                String raw = ExcelImportSupport.text(row, c0 + 1, fmt);
                if (!key.isEmpty()) {
                    rowOfKey.put(key, r + 1);
                }
                if (key.isEmpty() || raw.isEmpty()) {
                    continue;
                }
                int excelRow = r + 1;
                Field f = allowed.get(key);
                if (f == null) {
                    String label = ExcelImportSupport.text(row, c0, fmt);
                    String message = DocumentExcel.TRANSPORT.equals(tableName) && known.containsKey(key)
                            && !current.mayEditTransport()
                            ? DocumentExcel.ERROR + "transport_not_allowed"
                            : spec.error("field_not_available");
                    issue(tableName, excelRow, key, label, message, raw);
                    continue;
                }
                String label = plan.labels().getOrDefault(f.key(), f.key());
                switch (f.kind()) {
                    case TEXT, CONTACT -> {
                        Integer limit = limit(f.column());
                        if (limit != null && raw.length() > limit) {
                            issue(tableName, excelRow, key, label,
                                    DocumentExcel.ERROR + "too_long", raw, "limit", limit);
                        } else {
                            header.put(f.column(), raw);
                        }
                    }
                    case DATE -> {
                        LocalDate d = date(cell, raw);
                        if (d == null) {
                            issue(tableName, excelRow, key, label,
                                    DocumentExcel.ERROR + "bad_date", raw);
                        } else {
                            header.put(f.column(), d.toString());
                        }
                    }
                    case ORDER_TYPE -> lookup(tableName, excelRow, f, label, raw, """
                            SELECT id::text FROM dbo.enum_order_type
                            WHERE lower(code) = lower(:code) AND COALESCE(is_active, true)
                            """);
                    case RECEIPT_TYPE -> lookup(tableName, excelRow, f, label, raw, """
                            SELECT id::text FROM dbo.enum_receipt_doc_type
                            WHERE lower(code) = lower(:code) AND COALESCE(is_active, true)
                            """);
                    // Поставщик необязателен (03.11.2026), но указанный —
                    // должен быть в справочнике: молча выбросить его нельзя
                    case SUPPLIER -> lookup(tableName, excelRow, f, label, raw, """
                            SELECT supplier_id::text FROM dbo.supplier
                            WHERE lower(code) = lower(:code) AND is_active
                            """);
                    case WAREHOUSE -> lookup(tableName, excelRow, f, label, raw, """
                            SELECT warehouse_id FROM dbo.v_warehouses
                            WHERE lower(warehouse_id) = lower(:code) AND COALESCE(is_active, true)
                            """);
                    case CONSIGNEE -> consigneeId = lookup(tableName, excelRow, f, label, raw, """
                            SELECT consignee_id::text FROM dbo.consignee
                            WHERE lower(code) = lower(:code) AND is_active
                            """);
                    case ADDRESS -> {
                        addressText = raw;
                        addressRow = excelRow;
                    }
                    // Тип транспорта хранится кодом (V97)
                    case TRANSPORT_TYPE -> {
                        String code = jdbc.sql("""
                                        SELECT code FROM dbo.enum_transport_type
                                        WHERE lower(code) = lower(:code) AND COALESCE(is_active, true)
                                        """)
                                .param("code", DocumentExcel.codeOf(raw))
                                .query(String.class).optional().orElse(null);
                        if (code == null) {
                            issue(tableName, excelRow, key, label,
                                    DocumentExcel.ERROR + "not_in_list", raw);
                        } else {
                            header.put(f.column(), code);
                        }
                    }
                }
            }
            boolean hasAddress = spec.fields().stream().anyMatch(x -> x.kind() == Kind.ADDRESS);
            if (DocumentExcel.HEADER.equals(tableName) && hasAddress) {
                address(consigneeId, addressText, addressRow);
            }
        }

        /**
         * Номер документа в ERP клиента — обязательный и уникальный (V98).
         * Повторная загрузка того же файла упирается именно сюда:
         * человек узнаёт номер уже созданного документа, а не получает дубль.
         */
        private void erpNumber() {
            String label = plan.labels().getOrDefault("erpNo", "erpNo");
            int row = rowOfKey.getOrDefault("erpNo", 0);
            Object erp = header.get("erp_number");
            if (erp == null || String.valueOf(erp).isBlank()) {
                if (issues.stream().noneMatch(i -> "erpNo".equals(i.key()))) {
                    issue(DocumentExcel.HEADER, row, "erpNo", label, DocumentExcel.ERROR + "erp_required", null);
                }
                return;
            }
            // Имя таблицы — из Spec, не снаружи
            jdbc.sql("""
                            SELECT COALESCE(document_no, doc_id::text) FROM dbo.%s
                            WHERE depositor_id = :dep::uuid
                              AND lower(btrim(erp_number)) = lower(btrim(:erp))
                            LIMIT 1
                            """.formatted(spec.type().headerTable()))
                    .param("dep", depositorScope.currentDepositorId())
                    .param("erp", String.valueOf(erp))
                    .query(String.class).optional()
                    .ifPresent(taken -> issue(DocumentExcel.HEADER, row, "erpNo", label,
                            spec.error("erp_taken"), String.valueOf(erp), "no", taken));
        }

        /**
         * Склад-получатель не может совпадать со складом-отправителем —
         * ошибка уже в файле, а не при «Подтвердить» (решение заказчика
         * от 03.11.2026, V101). Пустой склад — не ошибка: чего не хватает,
         * скажет «Подтвердить».
         */
        private void sameWarehouse() {
            Object from = header.get("from_warehouse_id");
            Object to = header.get("to_warehouse_id");
            if (from != null && from.equals(to)) {
                issue(DocumentExcel.HEADER, rowOfKey.getOrDefault("toWarehouse", 0), "toWarehouse",
                        plan.labels().getOrDefault("toWarehouse", "toWarehouse"),
                        spec.error("same_warehouse"), String.valueOf(to));
            }
        }

        /** Код из «код — имя» → идентификатор в базе; не нашли — ошибка. */
        private String lookup(String table, int row, Field f, String label, String raw, String sql) {
            String code = DocumentExcel.codeOf(raw);
            String id = jdbc.sql(sql).param("code", code).query(String.class).optional().orElse(null);
            if (id == null) {
                issue(table, row, f.key(), label, DocumentExcel.ERROR + "not_in_list", raw);
                return null;
            }
            header.put(f.column(), id);
            return id;
        }

        /**
         * Место доставки — адрес выбранного грузополучателя. Пусто —
         * основной адрес. Контакт заказа подставляется по адресу (V83),
         * как при выборе адреса на экране.
         */
        private void address(String consigneeId, String text, int row) {
            Field f = spec.fields().stream()
                    .filter(x -> x.kind() == Kind.ADDRESS).findFirst().orElseThrow();
            String label = plan.labels().getOrDefault(f.key(), f.key());
            if (consigneeId == null) {
                if (text != null) {
                    issue(DocumentExcel.HEADER, row, f.key(), label,
                            "error.order_import.address_without_consignee", text);
                }
                return;
            }
            List<Map<String, Object>> points = jdbc.sql("""
                            SELECT address_id::text AS id, address, COALESCE(is_default, false) AS main
                            FROM dbo.delivery_point WHERE consignee_id = :c::uuid
                            ORDER BY COALESCE(is_default, false) DESC, sort_order NULLS LAST, address
                            """)
                    .param("c", consigneeId)
                    .query().listOfRows();
            String addressId;
            if (text == null) {
                addressId = points.stream().filter(pt -> Boolean.TRUE.equals(pt.get("main")))
                        .map(pt -> String.valueOf(pt.get("id"))).findFirst().orElse(null);
            } else {
                String want = normalized(text);
                addressId = points.stream()
                        .filter(pt -> normalized(String.valueOf(pt.get("address"))).equals(want))
                        .map(pt -> String.valueOf(pt.get("id"))).findFirst().orElse(null);
                if (addressId == null) {
                    issue(DocumentExcel.HEADER, row, f.key(), label,
                            "error.order_import.address_not_found", text);
                    return;
                }
            }
            if (addressId != null) {
                header.put(f.column(), addressId);
                String contact = jdbc.sql("SELECT dbo.delivery_contact(:a::uuid)")
                        .param("a", addressId).query(String.class).optional().orElse(null);
                // Контакт из файла главнее: подставляем только в пустое
                // поле — как на экране, где набранное руками не трогается.
                if (contact != null && !contact.isBlank() && !header.containsKey("consignee_contact")) {
                    header.put("consignee_contact", contact);
                }
            }
        }

        /* ------------------------------ состав ------------------------------ */

        private void readGoods(XSSFWorkbook wb, XSSFTable table) {
            List<GoodsColumn> columns = goodsLayout(wb);
            boolean price = plan.goods().contains(DocumentExcel.PRICE);
            XSSFSheet sheet = table.getXSSFSheet();
            AreaReference area = table.getArea();
            int c0 = area.getFirstCell().getCol();
            // Читаем и ниже таблицы: строки, вставленные под ней без
            // расширения таблицы, человек считает частью состава.
            int last = sheet.getLastRowNum();
            Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
            Map<String, Integer> firstRowOf = new HashMap<>();

            for (int r = area.getFirstCell().getRow() + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> raw = new HashMap<>();
                Map<String, Cell> cells = new HashMap<>();
                boolean any = false;
                for (int i = 0; i < columns.size(); i++) {
                    String v = ExcelImportSupport.text(row, c0 + i, fmt);
                    raw.put(columns.get(i).key(), v);
                    cells.put(columns.get(i).key(), row.getCell(c0 + i));
                    any |= !v.isEmpty();
                }
                if (!any) {
                    continue;
                }
                goodsRows++;
                if (goodsRows > DocumentExcel.MAX_ROWS) {
                    issue(DocumentExcel.GOODS, r + 1, null, null,
                            "error.excel.too_many_rows", null, "limit", DocumentExcel.MAX_ROWS);
                    return;
                }
                int excelRow = r + 1;
                int before = issues.size();

                String code = raw.getOrDefault(DocumentExcel.CODE.key(), "");
                String skuId = null;
                if (code.isEmpty()) {
                    goodsIssue(excelRow, DocumentExcel.CODE, DocumentExcel.ERROR + "code_required", null);
                } else {
                    Map<String, Object> sku = jdbc.sql("""
                                    SELECT sku_id::text AS id, COALESCE(is_active, true) AS active
                                    FROM dbo.sku WHERE lower(code) = lower(:code)
                                    ORDER BY COALESCE(is_active, true) DESC LIMIT 1
                                    """)
                            .param("code", code).query().listOfRows()
                            .stream().findFirst().orElse(null);
                    if (sku == null) {
                        goodsIssue(excelRow, DocumentExcel.CODE, DocumentExcel.ERROR + "unknown_item", code);
                    } else if (!Boolean.TRUE.equals(sku.get("active"))) {
                        goodsIssue(excelRow, DocumentExcel.CODE, spec.error("archived_item"), code);
                    } else {
                        skuId = String.valueOf(sku.get("id"));
                    }
                }

                BigDecimal qty = number(cells.get(DocumentExcel.QTY.key()), raw.get(DocumentExcel.QTY.key()));
                if (qty == null || qty.signum() <= 0) {
                    goodsIssue(excelRow, DocumentExcel.QTY, DocumentExcel.ERROR + "bad_quantity",
                            raw.get(DocumentExcel.QTY.key()));
                }

                String qualityRaw = raw.getOrDefault(DocumentExcel.QUALITY.key(), "");
                String quality = qualityRaw.isEmpty() ? "01" : DocumentExcel.codeOf(qualityRaw);
                boolean qualityKnown = jdbc.sql("""
                                SELECT EXISTS (SELECT 1 FROM dbo.enum_quality
                                               WHERE code = :code AND COALESCE(is_active, true))
                                """)
                        .param("code", quality).query(Boolean.class).single();
                if (!qualityKnown) {
                    goodsIssue(excelRow, DocumentExcel.QUALITY, DocumentExcel.ERROR + "not_in_list", qualityRaw);
                }

                String comment = raw.getOrDefault(DocumentExcel.COMMENT.key(), "");
                Integer commentLimit = limitOf(spec.linesTable(), "line_comment");
                if (commentLimit != null && comment.length() > commentLimit) {
                    goodsIssue(excelRow, DocumentExcel.COMMENT, DocumentExcel.ERROR + "too_long", comment,
                            "limit", commentLimit);
                }

                BigDecimal linePrice = null;
                String currencyId = null;
                if (price && columns.contains(DocumentExcel.PRICE)) {
                    String p = raw.getOrDefault(DocumentExcel.PRICE.key(), "");
                    if (!p.isEmpty()) {
                        linePrice = number(cells.get(DocumentExcel.PRICE.key()), p);
                        if (linePrice == null || linePrice.signum() < 0) {
                            goodsIssue(excelRow, DocumentExcel.PRICE, DocumentExcel.ERROR + "bad_price", p);
                            linePrice = null;
                        }
                    }
                    String cur = raw.getOrDefault(DocumentExcel.CURRENCY.key(), "");
                    if (!cur.isEmpty()) {
                        currencyId = jdbc.sql("""
                                        SELECT id::text FROM dbo.enum_currency
                                        WHERE lower(code) = lower(:code) AND COALESCE(is_active, true)
                                        """)
                                .param("code", DocumentExcel.codeOf(cur)).query(String.class)
                                .optional().orElse(null);
                        if (currencyId == null) {
                            goodsIssue(excelRow, DocumentExcel.CURRENCY, DocumentExcel.ERROR + "not_in_list", cur);
                        }
                    }
                }
                if (issues.size() > before) {
                    continue;
                }

                // Повтор: тот же товар, качество и комментарий — одна строка
                String mergeKey = skuId + "|" + quality + "|" + comment;
                Map<String, Object> line = merged.get(mergeKey);
                if (line == null) {
                    line = new LinkedHashMap<>();
                    line.put("sku_id", skuId);
                    line.put(spec.qtyColumn(), qty);
                    line.put("quality", quality);
                    line.put("line_comment", comment.isEmpty() ? null : comment);
                    if (linePrice != null) {
                        line.put("price", linePrice);
                    }
                    if (currencyId != null) {
                        line.put("currency_id", currencyId);
                    }
                    merged.put(mergeKey, line);
                    firstRowOf.put(mergeKey, excelRow);
                } else {
                    BigDecimal was = (BigDecimal) line.get("price");
                    if (linePrice != null && was != null && was.compareTo(linePrice) != 0) {
                        goodsIssue(excelRow, DocumentExcel.PRICE, DocumentExcel.ERROR + "price_differs",
                                raw.get(DocumentExcel.PRICE.key()), "row", firstRowOf.get(mergeKey));
                        continue;
                    }
                    if (was == null && linePrice != null) {
                        line.put("price", linePrice);
                    }
                    line.put(spec.qtyColumn(), ((BigDecimal) line.get(spec.qtyColumn())).add(qty));
                }
            }
            if (goodsRows == 0) {
                issue(DocumentExcel.GOODS, 0, null, null, spec.error("no_goods"), null);
                return;
            }
            lines.addAll(merged.values());
        }

        /** Какие колонки были в шаблоне — из скрытого листа; нет метки — все по порядку. */
        private List<GoodsColumn> goodsLayout(XSSFWorkbook wb) {
            Sheet dict = wb.getSheet(ExcelTemplateWriter.DICT_SHEET);
            if (dict != null) {
                CellReference ref = new CellReference(DocumentExcel.GOODS_LAYOUT_CELL);
                Row row = dict.getRow(ref.getRow());
                Cell cell = row == null ? null : row.getCell(ref.getCol());
                if (cell != null && cell.getCellType() == CellType.STRING
                        && cell.getStringCellValue().startsWith(DocumentExcel.GOODS_LAYOUT_PREFIX)) {
                    List<GoodsColumn> out = new ArrayList<>();
                    for (String key : cell.getStringCellValue()
                            .substring(DocumentExcel.GOODS_LAYOUT_PREFIX.length()).split(",")) {
                        DocumentExcel.allGoodsColumns().stream()
                                .filter(c -> c.key().equals(key.trim()))
                                .findFirst().ifPresent(out::add);
                    }
                    if (out.contains(DocumentExcel.CODE) && out.contains(DocumentExcel.QTY)) {
                        return out;
                    }
                }
            }
            return DocumentExcel.allGoodsColumns();
        }

        private void goodsIssue(int row, GoodsColumn column, String key, String value, Object... params) {
            issue(DocumentExcel.GOODS, row, column.key(),
                    plan.labels().getOrDefault("goods." + column.key(), column.key()),
                    key, value, params);
        }

        /**
         * Причина: текст — человеку на экран сейчас, ключ и подстановки —
         * в журнал, чтобы администратор прочёл её на своём языке (V98).
         */
        private void issue(String table, int row, String key, String field, String messageKey,
                           String value, Object... params) {
            com.ahlers.portal3pl.common.MsgRef ref = com.ahlers.portal3pl.common.MsgRef.of(messageKey, params);
            issues.add(new Issue(table, row, key, field, texts.text(ref.key(), ref.params()), value,
                    ref.key(), ref.params()));
        }

        private Integer limit(String column) {
            return limitOf(spec.type().headerTable(), column);
        }
    }

    /* ============================== значения ============================== */

    /** Книга из байтов; не читается (не xlsx, с паролем, битая) — понятный отказ. */
    private static XSSFWorkbook open(byte[] content) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException e) {
            throw Fail.badRequest("error.excel.unreadable");
        }
    }

    private static XSSFTable table(XSSFWorkbook wb, String name) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            for (XSSFTable t : wb.getSheetAt(i).getTables()) {
                if (name.equalsIgnoreCase(t.getName()) || name.equalsIgnoreCase(t.getDisplayName())) {
                    return t;
                }
            }
        }
        return null;
    }

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("d/M/uuuu"));

    /** Дата из ячейки-даты или из текста «25.12.2026», «2026-12-25». */
    static LocalDate date(Cell cell, String raw) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (cell != null && cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String v = raw == null ? "" : raw.trim();
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // следующий формат
            }
        }
        return null;
    }

    /** Число из ячейки-числа или из текста «1 234,5». */
    static BigDecimal number(Cell cell, String raw) {
        if (cell != null && (cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA
                    && cell.getCachedFormulaResultType() == CellType.NUMERIC))) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        String v = raw == null ? "" : raw.replace(" ", "").replace(',', '.');
        if (v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalized(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Integer limitOf(String table, String column) {
        return jdbc.sql("""
                        SELECT character_maximum_length FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = :t AND column_name = :c
                        """)
                .param("t", table).param("c", column)
                .query(Integer.class).optional().orElse(null);
    }

    /* ============================== журнал ============================== */

    /** Подстановки причины — объектом JSON: строки и числа, другого в них нет. */
    static String jsonOf(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder("{");
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(ExcelImportSupport.json(e.getKey())).append(':');
            Object v = e.getValue();
            out.append(v instanceof Number ? v.toString() : ExcelImportSupport.json(String.valueOf(v)));
        }
        return out.append('}').toString();
    }

    private void log(PortalUser user, String fileName, int size, Parse p, String docId,
                     String documentNo, String status) {
        String depositorId = depositorScope.currentDepositorId();
        String importId = jdbc.sql("""
                        INSERT INTO dbo.excel_import
                          (depositor_id, user_id, kind, file_name, file_size, row_count,
                           created_rows, error_count, status, doc_id, doc_number, created_at)
                        VALUES (:dep::uuid, :user::uuid, :kind, :name, :size, :rows,
                                :created, :errors, :status, :doc::uuid, :no, now())
                        RETURNING import_id::text
                        """)
                .param("dep", depositorId)
                .param("user", user.userId())
                .param("kind", p.spec.importKind())
                .param("name", fileName)
                .param("size", size)
                .param("rows", p.goodsRows)
                .param("created", docId == null ? 0 : p.lines.size())
                .param("errors", p.issues.size())
                .param("status", status)
                .param("doc", docId)
                .param("no", documentNo)
                .query(String.class)
                .single();
        for (Issue i : p.issues) {
            jdbc.sql("""
                            INSERT INTO dbo.excel_import_issue
                              (import_id, row_number, depositor_id, column_id, kind, message, file_value,
                               message_key, message_params)
                            VALUES (:imp::uuid, :row, :dep::uuid, :col, 'error', :msg, :value,
                                    :key, :params::jsonb)
                            """)
                    .param("imp", importId)
                    .param("row", i.row())
                    .param("dep", depositorId)
                    .param("col", i.key() == null ? null
                            : (i.table().isEmpty() ? "" : i.table() + ".") + i.key())
                    .param("msg", i.message())
                    .param("value", i.value())
                    .param("key", i.messageKey())
                    .param("params", jsonOf(i.messageParams()))
                    .update();
        }
    }
}
