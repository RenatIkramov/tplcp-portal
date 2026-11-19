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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Заказ из Excel на экране (V96): кнопка загрузки, окно ошибок
 * и открытие созданного черновика.
 *
 * <p>Правила разбора проверяет {@code OrderExcelTest}. Здесь — что видит
 * человек: файл с ошибками даёт окно со списком и ничего не создаёт,
 * файл без ошибок сразу открывает черновик.
 */
class OrderImportTest extends BrowserTestBase {

    private static final String ERP = "ERP-QA-BROWSER";

    @Autowired
    DocumentTemplateService templates;

    @AfterEach
    void cleanUp() {
        inContext("DELETE FROM dbo.order_details WHERE doc_id IN"
                + " (SELECT doc_id FROM dbo.orders WHERE erp_number = '" + ERP + "')");
        inContext("DELETE FROM dbo.orders WHERE erp_number = '" + ERP + "'");
        inContext("DELETE FROM dbo.excel_import WHERE kind = '03'");
    }

    private byte[] file(String code, String qty) throws IOException {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(templates.build(user, DocumentType.ORDERS).content()))) {
            XSSFTable header = wb.getTable("header");
            AreaReference a = header.getArea();
            int c = a.getFirstCell().getCol();
            for (int r = a.getFirstCell().getRow() + 1; r <= a.getLastCell().getRow(); r++) {
                Row row = header.getXSSFSheet().getRow(r);
                if ("erpNo".equals(row.getCell(c + 2).getStringCellValue())) {
                    row.getCell(c + 1).setCellValue(ERP);
                }
            }
            XSSFTable goods = wb.getTable("Goods");
            Row first = goods.getXSSFSheet().getRow(goods.getArea().getFirstCell().getRow() + 1);
            first.createCell(0).setCellValue(code);
            first.createCell(1).setCellValue(qty);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void upload(byte[] content) {
        FileChooser chooser = page.waitForFileChooser(() -> page.click("#orderImportBtn"));
        chooser.setFiles(new FilePayload("Заказ.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content));
        page.waitForFunction("() => PORTAL.docImport.last !== null");
    }

    @Test
    void fileWithErrorsShowsTheListAndCreatesNothing() throws IOException {
        byte[] content = file("NO-SUCH-ITEM", "1");
        signInUiAsClient();
        page.evaluate("() => showTab('orders')");
        page.waitForSelector("#orderImportBtn:visible");
        upload(content);

        page.waitForSelector("#docImportOverlay.active");
        assertTrue(page.querySelectorAll("#docImportBody tr").size() >= 1, "ошибки — списком");
        assertTrue(page.textContent("#docImportBody").contains("NO-SUCH-ITEM"),
                "в строке ошибки — значение из файла");
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.orders WHERE erp_number = '" + ERP + "'"));
        page.click("#docImportClose");
        assertNoJsErrors();
    }

    @Test
    void goodFileOpensTheNewDraft() throws IOException {
        byte[] content = file("RK-ROG-01", "2");
        signInUiAsClient();
        page.evaluate("() => showTab('orders')");
        page.waitForSelector("#orderImportBtn:visible");
        upload(content);

        String no = singleValue("SELECT document_no FROM dbo.orders WHERE erp_number = '" + ERP + "'");
        page.waitForFunction("no => PORTAL.card.doc('orders')"
                + " && PORTAL.card.doc('orders').no === no", no);
        // Поля шапки заполнены сразу, без переоткрытия документа (03.11.2026)
        page.waitForFunction("v => (document.getElementById('of_erpNo') || {}).value === v", ERP);
        assertEquals(Boolean.TRUE, page.evaluate("() => PORTAL.docImport.last.ok"));
        assertNoJsErrors();
    }
}
