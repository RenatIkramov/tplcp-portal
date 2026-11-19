package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.excel.DocumentTemplateService;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.options.FilePayload;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поступление на экране (V100): загрузка из Excel и качество в окне строки.
 *
 * <p>Правила разбора файла проверяет {@code ReceiptExcelTest}. Здесь — что
 * видит человек: кнопка загрузки открывает созданный черновик с полями,
 * а в окне строки поступления есть список качества, как у заказа.
 */
class ReceiptExcelAndQualityTest extends BrowserTestBase {

    private static final String SKU = "RK-ROG-01";

    @Autowired
    DocumentTemplateService templates;

    private final List<String> created = new ArrayList<>();
    private String erp;

    @AfterEach
    void cleanUp() {
        for (String docId : created) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
        }
        if (erp != null) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id IN"
                    + " (SELECT doc_id FROM dbo.receipts WHERE erp_number = '" + erp + "')");
            inContext("DELETE FROM dbo.receipts WHERE erp_number = '" + erp + "'");
        }
        inContext("DELETE FROM dbo.excel_import WHERE kind = '04'");
    }

    private void signedInRu() {
        ensurePassword(USER, USER_PASSWORD);
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
    }

    private byte[] file() throws IOException {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        erp = uniqueErp();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                templates.build(user, DocumentType.RECEIPTS).content()))) {
            XSSFTable header = wb.getTable("header");
            AreaReference a = header.getArea();
            int c = a.getFirstCell().getCol();
            for (int r = a.getFirstCell().getRow() + 1; r <= a.getLastCell().getRow(); r++) {
                Row row = header.getXSSFSheet().getRow(r);
                if ("erpNo".equals(row.getCell(c + 2).getStringCellValue())) {
                    row.getCell(c + 1).setCellValue(erp);
                }
            }
            XSSFTable goods = wb.getTable("Goods");
            Row first = goods.getXSSFSheet().getRow(goods.getArea().getFirstCell().getRow() + 1);
            first.createCell(0).setCellValue(SKU);
            first.createCell(1).setCellValue("3");
            first.createCell(2).setCellValue("02 — Брак");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void goodFileOpensTheNewReceiptDraft() throws IOException {
        byte[] content = file();
        signedInRu();
        page.evaluate("() => showTab('receipts')");
        page.waitForSelector("#receiptImportBtn:visible");
        assertTrue(page.isVisible("#receiptTemplateBtn"), "нет кнопки шаблона поступления");

        FileChooser chooser = page.waitForFileChooser(() -> page.click("#receiptImportBtn"));
        chooser.setFiles(new FilePayload("Поступление.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content));
        page.waitForFunction("() => PORTAL.docImport.last !== null");
        assertEquals(Boolean.TRUE, page.evaluate("() => PORTAL.docImport.last.ok"),
                "загрузка отказала: " + page.evaluate("() => JSON.stringify(PORTAL.docImport.last)"));

        String no = singleValue("SELECT document_no FROM dbo.receipts WHERE erp_number = '" + erp + "'");
        page.waitForFunction("no => PORTAL.card.doc('receipts')"
                + " && PORTAL.card.doc('receipts').no === no", no);
        page.waitForFunction("v => (document.getElementById('rf_erpNo') || {}).value === v", erp);
        assertEquals("02", page.evaluate("() => PORTAL.card.quality(PORTAL.card.lines('receipts')[0])"),
                "качество строки из файла не дошло до карточки");
        assertNoJsErrors();
    }

    @Test
    void receiptLineQualityIsChosenFromTheList() {
        signedInRu();
        page.evaluate("() => PORTAL.draft.create('receipts')");
        page.waitForFunction("() => !!PORTAL.card.doc('receipts')");
        fillErp("receipts");

        page.evaluate("() => PORTAL.lineModal.add('receipts')");
        page.waitForSelector("#lineEditOverlay.active");
        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) page.evaluate("() => Array.from("
                + "document.getElementById('lineEditQuality').options).map(o => o.value)");
        assertEquals(List.of("01", "02", "03", "04"), options);
        assertEquals("01", page.inputValue("#lineEditQuality"), "по умолчанию — «Годный»");

        page.evaluate("code => PORTAL.lineModal.pick('receipts', code)", SKU);
        page.selectOption("#lineEditQuality", "04");
        page.evaluate("() => { const p = document.getElementById('lineEditPackaging');"
                + " if (p && p.value) { p.value = ''; PORTAL.lineModal.packagingChanged('receipts'); }"
                + " const f = document.getElementById('lineEditExpected'); f.value = '6';"
                + " f.dispatchEvent(new Event('input', {bubbles: true})); }");
        page.evaluate("() => PORTAL.lineModal.save('receipts')");

        page.evaluate("() => PORTAL.draft.save('receipts')");
        page.waitForFunction("() => !!(PORTAL.card.doc('receipts') && PORTAL.card.doc('receipts').docId)");
        waitForQuiet();
        String docId = (String) page.evaluate("() => PORTAL.card.doc('receipts').docId");
        created.add(docId);
        assertEquals("04|6.000", singleValue("""
                SELECT string_agg(q.code || '|' || d.expected_qty::text, ';' ORDER BY d.line_number)
                  FROM dbo.receipt_details d JOIN dbo.enum_quality q ON q.id = d.quality_id
                 WHERE d.doc_id = '%s'::uuid AND d.batch_number IS NULL
                """.formatted(docId)), "в базу ушёл код качества");
        assertNoJsErrors();
    }
}
