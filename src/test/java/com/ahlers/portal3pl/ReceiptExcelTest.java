package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.excel.DocumentImportService;
import com.ahlers.portal3pl.excel.DocumentTemplateService;
import org.apache.poi.xssf.usermodel.XSSFSheet;
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
 * Поступление из Excel (V100): тот же файл, что у заказа (V96), со своим
 * заголовком — тип поступления, поставщик, ожидаемая дата, номера
 * сопроводительного документа, инвойса и ГТД.
 *
 * <p>Общие правила разбора (повторы, числа, чужой файл) проверяет
 * {@code OrderExcelTest}; здесь — то, чем поступление отличается.
 */
class ReceiptExcelTest extends PortalTestBase {

    @Autowired
    DocumentTemplateService templates;

    @Autowired
    DocumentImportService imports;

    @Autowired
    DraftService drafts;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String docId : created) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
        inContext("DELETE FROM dbo.excel_import WHERE kind = '04'");
    }

    private PortalUser user() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }

    @Test
    void templateHasTheReceiptHeader() throws IOException {
        try (XSSFWorkbook wb = open(templates.build(user(), DocumentType.RECEIPTS).content())) {
            List<String> keys = keys(table(wb, "header"));
            assertTrue(keys.containsAll(List.of("docType", "warehouse", "erpNo", "eta", "supplier",
                    "accompanyingDocNumber", "invoiceNumber", "gtd", "comment")), "в заголовке: " + keys);
            assertFalse(keys.contains("consignee"), "грузополучатель — поле заказа");
            assertFalse(keys.contains("orderType"));
            assertNotNull(table(wb, "transport"));

            XSSFSheet dict = wb.getSheet("Справочники");
            assertTrue(dict.getRow(0).getCell(1).getStringCellValue().startsWith("goods:code,qty,quality"),
                    "качество — в составе поступления (V100)");

            // Списки — свои: поставщики есть, грузополучателей и мест доставки нет
            String lists = sheetText(wb.getSheetAt(1));
            assertTrue(lists.contains("SUP-001"), "нет поставщиков на листе «Списки»");
            assertFalse(lists.contains("CON-001"), "грузополучатели в шаблоне поступления лишние");
        }
    }

    @Test
    void filledTemplateBecomesADraftWithoutSupplier() {
        PortalUser user = user();
        String erp = uniqueErp();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "docType", "01 — что угодно");
            set(wb, "header", "warehouse", "WH-ALA");
            set(wb, "header", "erpNo", erp);
            set(wb, "header", "eta", "20.12.2026");
            set(wb, "header", "invoiceNumber", "INV-77");
            goods(wb, List.of(
                    List.of("RK-ROG-01", "4", "", ""),
                    List.of("RK-ROG-01", "1", "02 — Брак", "")));
        });

        Map<String, Object> answer = imports.importFile(user, DocumentType.RECEIPTS, "Поступление.xlsx", file);
        assertEquals(Boolean.TRUE, answer.get("ok"), "ошибки: " + answer.get("issues"));
        String docId = String.valueOf(answer.get("docId"));
        created.add(docId);
        assertEquals(2, answer.get("lineCount"), "другое качество — своя строка");

        Map<String, Object> card = drafts.card(DocumentType.RECEIPTS, docId);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals("01", header.get("statusCode"));
        assertEquals("WH-ALA", header.get("warehouseId"));
        assertEquals("INV-77", header.get("invoiceNumber"));
        assertNull(header.get("supplierId"), "поставщик необязателен — пустой не ошибка");
        assertEquals("2026-12-20", String.valueOf(header.get("etaDate")).substring(0, 10));

        assertEquals("01:4|02:1", singleValue("SELECT string_agg(q.code || ':' || trim_scale(d.expected_qty),"
                + " '|' ORDER BY q.code) FROM dbo.receipt_details d JOIN dbo.enum_quality q ON q.id = d.quality_id"
                + " WHERE d.doc_id = '" + docId + "'::uuid"), "качество строк записано кодом перечисления");

        @SuppressWarnings("unchecked")
        List<Object> problems = (List<Object>) card.get("problems");
        assertFalse(String.valueOf(problems).contains("supplier"),
                "«Подтвердить» не требует поставщика: " + problems);

        assertEquals("02|" + answer.get("documentNo"), singleValue(
                "SELECT status || '|' || doc_number FROM dbo.excel_import WHERE kind = '04'"),
                "журнал: вид «Поступление», номер черновика записан");
    }

    @Test
    void unknownSupplierIsAnError() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "erpNo", uniqueErp());
            set(wb, "header", "supplier", "SUP-404 — нет такого");
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        Map<String, Object> answer = imports.importFile(user, DocumentType.RECEIPTS, "Поступление.xlsx", file);
        assertEquals(Boolean.FALSE, answer.get("ok"));
        @SuppressWarnings("unchecked")
        List<DocumentImportService.Issue> issues = (List<DocumentImportService.Issue>) answer.get("issues");
        assertTrue(issues.stream().anyMatch(i -> "supplier".equals(i.key())
                && "error.doc_import.not_in_list".equals(i.messageKey())), "найдено: " + issues);
    }

    @Test
    void sameErpNumberTwiceIsRefused() {
        PortalUser user = user();
        String erp = uniqueErp();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "erpNo", erp);
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        Map<String, Object> first = imports.importFile(user, DocumentType.RECEIPTS, "Поступление.xlsx", file);
        assertEquals(Boolean.TRUE, first.get("ok"), "ошибки: " + first.get("issues"));
        created.add(String.valueOf(first.get("docId")));

        Map<String, Object> again = imports.importFile(user, DocumentType.RECEIPTS, "Поступление.xlsx", file);
        assertEquals(Boolean.FALSE, again.get("ok"));
        @SuppressWarnings("unchecked")
        List<DocumentImportService.Issue> issues = (List<DocumentImportService.Issue>) again.get("issues");
        assertEquals("error.receipt_import.erp_taken", issues.getFirst().messageKey());
        assertTrue(issues.getFirst().message().contains(String.valueOf(first.get("documentNo"))),
                "человек видит номер уже созданного поступления: " + issues.getFirst().message());
    }

    @Test
    void receiptsNeedTheRightToCreateThem() {
        PortalUser user = user();
        inContext("UPDATE dbo.user_settings SET can_create_receipts = false"
                + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        expectRejection(403, () -> templates.build(user, DocumentType.RECEIPTS));
        expectRejection(403, () -> imports.importFile(user, DocumentType.RECEIPTS, "Поступление.xlsx",
                new byte[]{1}));
    }

    /* ---------------------------- помощники ---------------------------- */

    private byte[] filled(PortalUser user, Consumer<XSSFWorkbook> fill) {
        try (XSSFWorkbook wb = open(templates.build(user, DocumentType.RECEIPTS).content())) {
            fill.accept(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sheetText(XSSFSheet sheet) {
        StringBuilder out = new StringBuilder();
        sheet.forEach(row -> row.forEach(cell -> out.append(cell.toString()).append('\n')));
        return out.toString();
    }
}
