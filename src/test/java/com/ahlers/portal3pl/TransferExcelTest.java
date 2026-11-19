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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перемещение из Excel (V101): тот же файл, что у заказа и поступления,
 * со своим заголовком — склад-отправитель и склад-получатель.
 *
 * <p>Общие правила разбора проверяет {@code OrderExcelTest}; здесь — то,
 * чем перемещение отличается: нет цены, одинаковые склады — ошибка,
 * качество строки записывается.
 */
class TransferExcelTest extends PortalTestBase {

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
            inContext("DELETE FROM dbo.transfer_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.transfers WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
        inContext("DELETE FROM dbo.excel_import WHERE kind = '05'");
    }

    private PortalUser user() {
        signIn();
        return authService.authenticate(USER, USER_PASSWORD);
    }

    @Test
    void templateHasTheTransferHeaderAndNoPrice() throws IOException {
        try (XSSFWorkbook wb = open(templates.build(user(), DocumentType.TRANSFERS).content())) {
            List<String> keys = keys(table(wb, "header"));
            assertTrue(keys.containsAll(List.of("fromWarehouse", "toWarehouse", "erpNo", "comment")),
                    "в заголовке: " + keys);
            assertFalse(keys.contains("warehouse"), "у перемещения два склада, а не один");
            assertNotNull(table(wb, "transport"));

            XSSFSheet dict = wb.getSheet("Справочники");
            assertEquals("goods:code,qty,quality,comment", dict.getRow(0).getCell(1).getStringCellValue(),
                    "цены в перемещении нет, качество есть (V101)");
        }
    }

    @Test
    void filledTemplateBecomesADraft() {
        PortalUser user = user();
        byte[] file = filled(user, wb -> {
            set(wb, "header", "fromWarehouse", "WH-ALA — Склад Алматы");
            set(wb, "header", "toWarehouse", "WH-KGD");
            set(wb, "header", "erpNo", uniqueErp());
            goods(wb, List.of(
                    List.of("RK-ROG-01", "2", "02 — Брак", ""),
                    List.of("RK-ROG-01", "3", "", "")));
        });

        Map<String, Object> answer = imports.importFile(user, DocumentType.TRANSFERS, "Перемещение.xlsx", file);
        assertEquals(Boolean.TRUE, answer.get("ok"), "ошибки: " + answer.get("issues"));
        String docId = String.valueOf(answer.get("docId"));
        created.add(docId);
        assertEquals(2, answer.get("lineCount"), "брак и годный — разные строки");

        Map<String, Object> card = drafts.card(DocumentType.TRANSFERS, docId);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals("WH-ALA", header.get("fromWarehouseId"));
        assertEquals("WH-KGD", header.get("toWarehouseId"));

        assertEquals("01:3|02:2", singleValue("SELECT string_agg(q.code || ':' || trim_scale(d.qty),"
                + " '|' ORDER BY q.code) FROM dbo.transfer_details d JOIN dbo.enum_quality q ON q.id = d.quality_id"
                + " WHERE d.doc_id = '" + docId + "'::uuid"), "качество строк записано кодом перечисления");
        assertEquals("02|" + answer.get("documentNo"), singleValue(
                "SELECT status || '|' || doc_number FROM dbo.excel_import WHERE kind = '05'"),
                "журнал: вид «Перемещение», номер черновика записан");
    }

    @Test
    void sameWarehouseIsAnError() {
        PortalUser user = user();
        String before = singleValue("SELECT count(*)::text FROM dbo.transfers");
        byte[] file = filled(user, wb -> {
            set(wb, "header", "fromWarehouse", "WH-ALA");
            set(wb, "header", "toWarehouse", "wh-ala");
            set(wb, "header", "erpNo", uniqueErp());
            goods(wb, List.of(List.of("RK-ROG-01", "1", "", "")));
        });
        Map<String, Object> answer = imports.importFile(user, DocumentType.TRANSFERS, "Перемещение.xlsx", file);
        assertEquals(Boolean.FALSE, answer.get("ok"));
        @SuppressWarnings("unchecked")
        List<DocumentImportService.Issue> issues = (List<DocumentImportService.Issue>) answer.get("issues");
        assertTrue(issues.stream().anyMatch(i -> "toWarehouse".equals(i.key())
                && "error.transfer_import.same_warehouse".equals(i.messageKey()) && i.row() > 0),
                "найдено: " + issues);
        assertEquals(before, singleValue("SELECT count(*)::text FROM dbo.transfers"), "черновик не создан");
    }

    @Test
    void transfersNeedTheRightToCreateThem() {
        PortalUser user = user();
        inContext("UPDATE dbo.user_settings SET can_create_transfers = false"
                + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        expectRejection(403, () -> templates.build(user, DocumentType.TRANSFERS));
        expectRejection(403, () -> imports.importFile(user, DocumentType.TRANSFERS, "Перемещение.xlsx",
                new byte[]{1}));
    }

    /* ---------------------------- помощники ---------------------------- */

    private byte[] filled(PortalUser user, Consumer<XSSFWorkbook> fill) {
        try (XSSFWorkbook wb = open(templates.build(user, DocumentType.TRANSFERS).content())) {
            fill.accept(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
