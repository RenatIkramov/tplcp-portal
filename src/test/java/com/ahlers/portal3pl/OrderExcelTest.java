package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.excel.DocumentImportService;
import com.ahlers.portal3pl.excel.DocumentTemplateService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.ahlers.portal3pl.ExcelTestFiles.goods;
import static com.ahlers.portal3pl.ExcelTestFiles.keys;
import static com.ahlers.portal3pl.ExcelTestFiles.open;
import static com.ahlers.portal3pl.ExcelTestFiles.set;
import static com.ahlers.portal3pl.ExcelTestFiles.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Заказ из Excel (V96): шаблон и загрузка.
 *
 * <p>Файл для загрузки — это скачанный шаблон, заполненный здесь же
 * через POI. Так проверяется путь человека целиком: что шаблон даёт,
 * то загрузка и понимает, — и не нужно держать двоичных образцов.
 */
class OrderExcelTest extends PortalTestBase {

    private static final String ARCHIVED = "QA-ORD-ARCH";

    @Autowired
    ColumnService columnService;

    @Autowired
    DocumentTemplateService templates;

    @Autowired
    DocumentImportService imports;

    @Autowired
    DraftService drafts;

    @Autowired
    com.ahlers.portal3pl.excel.ImportLogService importLog;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String docId : created) {
            inContext("DELETE FROM dbo.order_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.orders WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
        inContext("DELETE FROM dbo.excel_import WHERE kind = '03'");
        inContext("DELETE FROM dbo.sku WHERE code = '" + ARCHIVED + "'");
    }

    private PortalUser user() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }

    /**
     * Доп. поля заказа, закрытые поклажедателю в «Доступах», не попадают
     * в шаблон — ни у клиента, ни у администратора, выбравшего этого
     * поклажедателя (замечание от 03.11.2026).
     */
    @Test
    void hiddenCustomFieldsAreNotInTheTemplate() throws IOException {
        PortalUser admin = portalAdmin();
        String dep = depositorId(USER, DEPOSITOR);
        Runnable restore = keepDepositorSetup(DEPOSITOR);
        try {
            for (int i = 1; i <= 10; i++) {
                columnService.saveDepositorColumn(admin, dep, "orders", "customField" + i, false, false);
            }
            PortalUser client = user();
            try (XSSFWorkbook wb = open(templates.build(client, DocumentType.ORDERS).content())) {
                assertFalse(keys(table(wb, "header")).stream().anyMatch(k -> k.startsWith("customField")),
                        "клиент: закрытые доп. поля в шаблоне " + keys(table(wb, "header")));
            }
            signInAs(ADMIN, DEPOSITOR);
            try (XSSFWorkbook wb = open(templates.build(admin, DocumentType.ORDERS).content())) {
                assertFalse(keys(table(wb, "header")).stream().anyMatch(k -> k.startsWith("customField")),
                        "администратор: закрытые доп. поля в шаблоне " + keys(table(wb, "header")));
            }
        } finally {
            restore.run();
        }
    }

    /* ---------------------------- шаблон ---------------------------- */

    @Test
    void templateHasThreeTablesKeysAndCodeNameLists() throws IOException {
        PortalUser user = user();
        try (XSSFWorkbook wb = open(templates.build(user, DocumentType.ORDERS).content())) {
            XSSFTable goods = table(wb, "Goods");
            assertNotNull(goods);
            // 500 — предел строк состава (OrderExcel.MAX_ROWS)
            assertEquals(500, goods.getArea().getLastCell().getRow()
                    - goods.getArea().getFirstCell().getRow(),
                    "таблица состава — сразу на все допустимые строки (03.11.2026)");
            XSSFTable header = table(wb, "header");
            assertNotNull(header);
            assertNotNull(table(wb, "transport"), "у пользователя есть право на транспорт");

            List<String> keys = keys(header);
            assertTrue(keys.containsAll(List.of("orderType", "warehouse", "erpNo", "plannedShip",
                    "consignee", "deliveryAddress", "consigneeContact", "comment")), "в шапке: " + keys);
            assertFalse(keys.contains("status"), "статус человек не заполняет");
            assertFalse(keys.contains("lineCount"), "расчётных полей в шаблоне нет");
            assertFalse(keys.contains("deliveryDate"), "дату доставки ставит WMS (V97)");
            assertFalse(keys(table(wb, "transport")).contains("trip"), "рейс заводит WMS (V97)");
            assertTrue(keys(table(wb, "transport")).contains("transportType"));

            XSSFSheet dict = wb.getSheet("Справочники");
            assertTrue(wb.isSheetHidden(wb.getSheetIndex(dict)));
            assertEquals("depositor:" + DEPOSITOR, dict.getRow(0).getCell(0).getStringCellValue());
            assertTrue(dict.getRow(0).getCell(1).getStringCellValue().startsWith("goods:code,qty,quality"));

            // Списки — на видимом листе (V97); кодов товаров среди них нет
            XSSFSheet lists = wb.getSheetAt(1);
            assertFalse(wb.isSheetHidden(1), "лист списков виден");
            List<String> values = new ArrayList<>();
            lists.forEach(r -> r.forEach(c -> values.add(c.toString())));
            assertTrue(values.stream().anyMatch(v -> v.startsWith("01 — ")),
                    "значения списков — «код — имя»: " + values);
            assertFalse(values.contains("RK-ROG-01"), "кодов товаров списком нет: их бывают тысячи");
            assertTrue(values.contains("г. Алматы, ул. Розыбакиева, 101"), "места доставки — на листе списков");

            // Место доставки — список по выбранному грузополучателю
            XSSFSheet sheet = header.getXSSFSheet();
            boolean dependent = sheet.getDataValidations().stream()
                    .anyMatch(v -> v.getValidationConstraint().getFormula1() != null
                            && v.getValidationConstraint().getFormula1().startsWith("OFFSET("));
            assertTrue(dependent, "у места доставки — список адресов грузополучателя");
        }
    }

    @Test
    void transportTableOnlyWithTheRight() throws IOException {
        inContext("UPDATE dbo.user_settings SET can_edit_transport = false"
                + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        PortalUser user = user();
        try (XSSFWorkbook wb = open(templates.build(user, DocumentType.ORDERS).content())) {
            assertNull(table(wb, "transport"));
        }
    }

    @Test
    void withoutRightToCreateOrdersNoTemplateAndNoImport() {
        inContext("UPDATE dbo.user_settings SET can_create_orders = false"
                + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        PortalUser user = user();
        expectRejection(403, () -> templates.build(user, DocumentType.ORDERS));
        expectRejection(403, () -> imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", new byte[]{1}));
    }

    /* ---------------------------- загрузка ---------------------------- */

    @Test
    void filledTemplateBecomesADraft() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "orderType", "01 — что угодно");
            set(wb, "header", "warehouse", "WH-ALA — Склад Алматы");
            set(wb, "header", "consignee", "CON-001");
            set(wb, "header", "plannedShip", "25.12.2026");
            set(wb, "header", "erpNo", "ERP-QA-1");
            set(wb, "transport", "carNumber", "123 QA 02");
            set(wb, "transport", "transportType", "04 — Грузовой");
            goods(wb, List.of(
                    List.of("RK-ROG-01", "2", "", ""),
                    List.of("rk-rog-01", "3", "", ""),        // повтор: складывается
                    List.of("RK-ROG-01", "1", "02 — Брак", ""), // другое качество — своя строка
                    List.of("RK-ROG-01", "1", "", "хрупкое")));  // другой комментарий — своя строка
        });

        Map<String, Object> answer = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.TRUE, answer.get("ok"), "ошибки: " + answer.get("issues"));
        String docId = String.valueOf(answer.get("docId"));
        created.add(docId);
        assertEquals(3, answer.get("lineCount"));

        Map<String, Object> card = drafts.card(DocumentType.ORDERS, docId);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals("01", header.get("statusCode"), "результат загрузки — черновик");
        assertEquals("WH-ALA", header.get("warehouseId"));
        assertEquals("ERP-QA-1", header.get("erpNumber"));
        assertEquals("123 QA 02", header.get("carNumber"));
        assertEquals("04", header.get("transportType"), "тип транспорта — кодом перечисления (V97)");
        assertEquals("2026-12-25", String.valueOf(header.get("plannedShipDate")).substring(0, 10));
        assertNotNull(header.get("deliveryPointId"), "пустое место доставки — основной адрес");
        assertNotNull(header.get("consigneeContact"), "контакт подставлен по адресу (V83)");

        assertEquals("5", singleValue("SELECT trim_scale(sum(d.ordered_qty))::text FROM dbo.order_details d"
                + " JOIN dbo.enum_quality q ON q.id = d.quality_id"
                + " WHERE d.doc_id = '" + docId + "'::uuid AND q.code = '01' AND d.line_comment IS NULL"));
        assertEquals("02", singleValue("SELECT q.code FROM dbo.order_details d"
                + " JOIN dbo.enum_quality q ON q.id = d.quality_id"
                + " WHERE d.doc_id = '" + docId + "'::uuid AND q.code <> '01'"));

        assertEquals("02|" + answer.get("documentNo"), singleValue(
                "SELECT status || '|' || doc_number FROM dbo.excel_import WHERE kind = '03'"),
                "журнал: заказ создан, номер черновика записан");
    }

    @Test
    void errorsAreListedAndNothingIsCreated() {
        inContext("INSERT INTO dbo.sku (depositor_id, code, name, is_active)"
                + " SELECT depositor_id, '" + ARCHIVED + "', 'Архивный', false FROM dbo.depositor"
                + " WHERE code = '" + DEPOSITOR + "'");
        PortalUser user = user();
        String before = singleValue("SELECT count(*)::text FROM dbo.orders");
        byte[] file = filled(user, wb -> {
            set(wb, "header", "orderType", "77 — нет такого");
            set(wb, "header", "plannedShip", "завтра");
            set(wb, "header", "consignee", "CON-001");
            set(wb, "header", "deliveryAddress", "Луна, кратер Тихо");
            goods(wb, List.of(
                    List.of("NO-SUCH-ITEM", "1", "", ""),
                    List.of(ARCHIVED, "1", "", ""),
                    List.of("RK-ROG-01", "0", "", ""),
                    List.of("RK-ROG-02", "1", "99", "")));
        });

        Map<String, Object> answer = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.FALSE, answer.get("ok"));
        @SuppressWarnings("unchecked")
        List<DocumentImportService.Issue> issues = (List<DocumentImportService.Issue>) answer.get("issues");
        List<String> where = issues.stream().map(i -> i.table() + "." + i.key()).toList();
        assertTrue(where.containsAll(List.of("header.orderType", "header.plannedShip",
                        "header.deliveryAddress", "Goods.code", "Goods.qty", "Goods.quality")),
                "найдено: " + where);
        assertEquals(2, issues.stream().filter(i -> "Goods.code".equals(i.table() + "." + i.key())).count(),
                "неизвестный и архивный товар — две ошибки");
        assertTrue(issues.stream().allMatch(i -> i.row() > 0 && i.message() != null && !i.message().isBlank()),
                "у каждой ошибки — строка листа и текст");

        assertEquals(before, singleValue("SELECT count(*)::text FROM dbo.orders"), "черновик не создан");
        assertEquals("04", singleValue("SELECT status FROM dbo.excel_import WHERE kind = '03'"));
        assertEquals(String.valueOf(issues.size()), singleValue(
                "SELECT count(*)::text FROM dbo.excel_import_issue i JOIN dbo.excel_import e USING (import_id)"
                        + " WHERE e.kind = '03'"), "все причины — в журнале администратора");
    }

    /** Контакт из файла главнее подстановки по адресу. */
    @Test
    void contactFromTheFileIsKept() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "consignee", "CON-001");
            set(wb, "header", "deliveryAddress", "г. Алматы, ул. Розыбакиева, 101");
            set(wb, "header", "consigneeContact", "Кладовщик Иван, +7 700 000 00 00");
            set(wb, "header", "erpNo", uniqueErp());
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        Map<String, Object> answer = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.TRUE, answer.get("ok"), "ошибки: " + answer.get("issues"));
        String docId = String.valueOf(answer.get("docId"));
        created.add(docId);
        assertEquals("Кладовщик Иван, +7 700 000 00 00", singleValue(
                "SELECT consignee_contact FROM dbo.orders WHERE doc_id = '" + docId + "'::uuid"));
    }

    /** Тот же номер ERP второй раз — отказ с номером уже созданного заказа (V98). */
    @Test
    void sameErpNumberTwiceIsRefused() {
        PortalUser user = user();
        String erp = uniqueErp();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "erpNo", erp);
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        Map<String, Object> first = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.TRUE, first.get("ok"), "ошибки: " + first.get("issues"));
        created.add(String.valueOf(first.get("docId")));

        Map<String, Object> again = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.FALSE, again.get("ok"), "повторная загрузка того же файла — не новый заказ");
        @SuppressWarnings("unchecked")
        List<DocumentImportService.Issue> issues = (List<DocumentImportService.Issue>) again.get("issues");
        assertEquals("error.order_import.erp_taken", issues.getFirst().messageKey());
        assertTrue(issues.getFirst().message().contains(String.valueOf(first.get("documentNo"))));

        byte[] empty = filled(user, wb -> goods(wb, List.of(List.of("RK-ROG-01", "1", "", ""))));
        @SuppressWarnings("unchecked")
        List<DocumentImportService.Issue> none = (List<DocumentImportService.Issue>)
                imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", empty).get("issues");
        assertTrue(none.stream().anyMatch(i -> "error.doc_import.erp_required".equals(i.messageKey())));
    }

    /**
     * Журнал хранит ключ причины, а читает её администратор на своём
     * языке (V98): загрузил англоязычный клиент, а администратор видит
     * причину по-русски.
     */
    @Test
    void issueIsReadInTheAdministratorsLanguage() {
        PortalUser user = user();                     // язык не выбран — английский портала
        byte[] file = filled(user, wb -> {
            set(wb, "header", "erpNo", uniqueErp());
            goods(wb, List.of(List.of("NO-SUCH-ITEM", "1", "", "")));
        });
        Map<String, Object> answer = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.FALSE, answer.get("ok"));
        assertEquals("error.doc_import.unknown_item", singleValue(
                "SELECT i.message_key FROM dbo.excel_import_issue i JOIN dbo.excel_import e USING (import_id)"
                        + " WHERE e.kind = '03' LIMIT 1"));

        PortalUser admin = portalAdmin();
        localeOf(admin, "ru");
        try {
            signInAs(ADMIN, DEPOSITOR);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> log = (List<Map<String, Object>>) importLog.log(admin, null).get("issues");
            Map<String, Object> issue = log.stream()
                    .filter(i -> "error.doc_import.unknown_item".equals(i.get("messageKey")))
                    .findFirst().orElseThrow();
            assertEquals(text("error.doc_import.unknown_item", "ru"), issue.get("message"));
            // вид загрузки — именем на языке администратора, а не кодом «03» (V103)
            assertEquals("Заказ", issue.get("importKindName"));
        } finally {
            owner().jdbc.sql("UPDATE dbo.app_user SET locale_id = NULL WHERE login = :l")
                    .param("l", ADMIN).update();
        }
    }

    /** Подписи шапки — как в карточке документа, а не как в колонках списка. */
    @Test
    void headerLabelsAreTheCardLabels() throws IOException {
        PortalUser user = user();
        try (XSSFWorkbook wb = open(templates.build(user, DocumentType.ORDERS).content())) {
            XSSFTable header = table(wb, "header");
            AreaReference a = header.getArea();
            int c = a.getFirstCell().getCol();
            String erpLabel = null;
            for (int r = a.getFirstCell().getRow() + 1; r <= a.getLastCell().getRow(); r++) {
                Row row = header.getXSSFSheet().getRow(r);
                if ("erpNo".equals(row.getCell(c + 2).getStringCellValue())) {
                    erpLabel = row.getCell(c).getStringCellValue();
                }
            }
            assertEquals(text("common.label.document_number_client_erp", "en") + " *", erpLabel,
                    "«Номер документа (в ERP клиента)», как в карточке, и отмечен обязательным");
        }
    }

    private String text(String key, String locale) {
        return jdbc.sql("SELECT value FROM dbo.ui_texts(:l::text) WHERE text_key = :k")
                .param("l", locale).param("k", key).query(String.class).single();
    }

    @Test
    void emptyGoodsIsAnError() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> set(wb, "header", "erpNo", "ERP-QA-2"));
        Map<String, Object> answer = imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.FALSE, answer.get("ok"));
    }

    /** Транспорт без права — ошибка, а не молча выброшенные поля. */
    @Test
    void transportWithoutTheRightIsRefused() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> {
            set(wb, "transport", "carNumber", "123 QA 02");
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        inContext("UPDATE dbo.user_settings SET can_edit_transport = false"
                + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        PortalUser again = user();
        Map<String, Object> answer = imports.importFile(again, DocumentType.ORDERS, "Заказ.xlsx", file);
        assertEquals(Boolean.FALSE, answer.get("ok"));
    }

    /** Файл, собранный для другого клиента, не принимается. */
    @Test
    void otherDepositorsFileIsRefused() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> {
            wb.getSheet("Справочники").getRow(0).getCell(0).setCellValue("depositor:" + OTHER_DEPOSITOR);
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        expectRejection(409, () -> imports.importFile(user, DocumentType.ORDERS, "Заказ.xlsx", file));
    }

    /* ---------------------------- помощники ---------------------------- */

    private byte[] filled(PortalUser user, Consumer<XSSFWorkbook> fill) {
        try (XSSFWorkbook wb = open(templates.build(user, DocumentType.ORDERS).content())) {
            fill.accept(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }





}
