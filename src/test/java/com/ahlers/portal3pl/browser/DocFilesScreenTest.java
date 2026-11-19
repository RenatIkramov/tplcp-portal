package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вкладка «Прикреплённые файлы» (V145): несколько файлов разом, отказ
 * сервера — в самой вкладке, у черновика — «Ждёт отправки документа»
 * и ✕ с вопросом, «Скачать» отдаёт файл.
 */
@TestPropertySource(properties = "portal.client-files.retry-ms=3600000")
class DocFilesScreenTest extends BrowserTestBase {

    private static final byte[] PDF = "%PDF-1.4 screen".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    DraftService drafts;

    private String docId;

    @AfterEach
    void cleanUp() {
        if (docId != null) {
            List<UUID> ids = owner().jdbc.sql("SELECT file_id FROM dbo.stored_file WHERE doc_id = :d::uuid")
                    .param("d", docId).query(UUID.class).list();
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'");
            ids.forEach(id -> owner().jdbc.sql("DELETE FROM dbo.stored_file WHERE file_id = :f").param("f", id).update());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void filesAreAttachedShownDownloadedAndRemoved() {
        signIn();
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", "WH-ALA");
        docId = String.valueOf(((Map<String, Object>) drafts.create(DocumentType.RECEIPTS, withErp(head))
                .get("header")).get("docId"));

        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
        page.evaluate("id => PORTAL.draft.open('receipts', id)", docId);
        page.waitForFunction("() => (PORTAL.card.doc('receipts') || {}).statusCode === '01'");
        page.click("[data-tab='photos']");
        page.waitForSelector("#docFilesEmpty");

        // негодный — отказ во вкладке, ничего не записано
        page.setInputFiles("#docFileInput", new FilePayload("tool.exe", "application/octet-stream", PDF));
        page.waitForSelector("#docFileError");
        assertTrue(page.isVisible("#docFilesEmpty"), "после отказа в таблице что-то появилось");

        // два файла разом
        page.setInputFiles("#docFileInput", new FilePayload[]{
                new FilePayload("накладная.pdf", "application/pdf", PDF),
                new FilePayload("доверенность.pdf", "application/pdf", PDF)});
        page.waitForFunction("() => document.querySelectorAll('#docFilesBody tr[data-file]').length === 2");
        String row = "#docFilesBody tr[data-file][data-state='waiting']";
        assertEquals(2, page.querySelectorAll(row).size(), "у черновика файлы не ждут документа");
        assertTrue(page.textContent("#docFilesBody").contains("Ждёт отправки документа"),
                "нет состояния отправки: " + page.textContent("#docFilesBody"));

        // сверяется содержимое, а не имя: браузер проверок на Linux отдаёт кириллическое
        // имя скачивания как «download» (18.11.2026), у человека в Chrome имя приходит как есть
        Download file = page.waitForDownload(() -> page.click(row + " [data-file-download]"));
        try {
            assertEquals(new String(PDF, StandardCharsets.US_ASCII),
                    java.nio.file.Files.readString(file.path(), StandardCharsets.US_ASCII), "скачался не тот файл");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        // ✕ спрашивает, «Удалить» убирает
        page.click(row + " [data-file-remove]");
        page.click("#docFileRemoveYes");
        page.waitForFunction("() => document.querySelectorAll('#docFilesBody tr[data-file]').length === 1");
        assertNoJsErrors();
    }
}
