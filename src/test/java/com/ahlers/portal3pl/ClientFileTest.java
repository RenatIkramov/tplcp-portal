package com.ahlers.portal3pl;

import com.ahlers.portal3pl.common.PortalException;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.files.ClientFileService;
import com.ahlers.portal3pl.files.ClientFileService.Upload;
import com.ahlers.portal3pl.files.FileStore;
import com.ahlers.portal3pl.files.StoredFileCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Файлы клиента к документам (V145, Ф3; решения заказчика 16.11.2026 и
 * 18.11.2026): проверка на сервере, ожидание у черновика, отправка в WMS
 * вслед за документом, повтор, удаление только у черновика, чужой файл
 * не виден.
 *
 * <p>WMS изображает подменённый {@link WmsGateway}; расписание в проверке
 * выключено — очередь разбираем сами.
 */
@TestPropertySource(properties = "portal.client-files.retry-ms=3600000")
class ClientFileTest extends PortalTestBase {

    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    DraftService drafts;

    @Autowired
    ClientFileService files;

    @Autowired
    FileStore store;

    @Autowired
    StoredFileCleanup cleanup;

    @MockitoSpyBean
    WmsGateway wms;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeCreated() {
        doCallRealMethod().when(wms).uploadFile(any(), anyString(), anyString(), anyString(), any());
        for (String docId : created) {
            List<UUID> ids = owner().jdbc.sql("SELECT file_id FROM dbo.stored_file WHERE doc_id = :d::uuid")
                    .param("d", docId).query(UUID.class).list();
            jdbc.sql("SELECT dbo.wms_outbox_finish(outbox_id, NULL, NULL) FROM dbo.wms_outbox"
                    + " WHERE doc_id = :d::uuid AND state = 'pending'").param("d", docId).query().listOfRows();
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
            ids.forEach(store::delete);
        }
    }

    private String draft() {
        signIn();
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", "WH-ALA");
        head.put("eta_date", "2026-12-01");
        @SuppressWarnings("unchecked")
        String docId = String.valueOf(((Map<String, Object>) drafts.create(DocumentType.RECEIPTS, withErp(head))
                .get("header")).get("docId"));
        created.add(docId);
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id",
                singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'"), "expected_qty", 5));
        return docId;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listed(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("files");
    }

    private String state(String docId) {
        return owner().jdbc.sql("SELECT string_agg(wms_state, ',' ORDER BY created_at) FROM dbo.stored_file"
                + " WHERE doc_id = :d::uuid").param("d", docId).query(String.class).single();
    }

    /** У черновика файл ждёт; «Подтвердить» — уходит в WMS вслед за документом; скачивается всегда. */
    @Test
    void draftFileWaitsAndFollowsTheDocument() {
        String docId = draft();
        Map<String, Object> answer = files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("C:\\Users\\client\\накладная.pdf", PDF)));
        Map<String, Object> f = listed(answer).getFirst();
        assertEquals("накладная.pdf", f.get("fileName"), "путь из браузера остался в имени");
        assertEquals("waiting", f.get("wmsState"));
        verify(wms, never()).uploadFile(any(), anyString(), anyString(), anyString(), any());

        drafts.confirm(DocumentType.RECEIPTS, docId);
        assertEquals("pending", state(docId), "документ ушёл, а файл не встал в очередь");
        files.deliverDue();
        assertEquals("sent", state(docId));
        verify(wms, times(1)).uploadFile(any(), anyString(), anyString(), anyString(), any());

        ClientFileService.Download d = files.download(DocumentType.RECEIPTS, docId, String.valueOf(f.get("fileId")));
        assertArrayEquals(PDF, d.content(), "портал не хранит дошедший файл");
        assertEquals("application/pdf", d.contentType());
    }

    /** К отправленному документу файл уходит сразу. */
    @Test
    void fileForSentDocumentGoesAtOnce() {
        String docId = draft();
        drafts.confirm(DocumentType.RECEIPTS, docId);
        files.add(DocumentType.RECEIPTS, docId, List.of(new Upload("scan.pdf", PDF)));
        assertEquals("sent", state(docId));
    }

    /** Проверка на сервере: тип по списку, содержимое по сигнатуре, пустой, слишком большой. Негодный — не пишется ни один. */
    @Test
    void badFilesAreRefusedAndNothingIsWritten() {
        String docId = draft();
        PortalException e = assertThrows(PortalException.class, () -> files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("ok.pdf", PDF), new Upload("virus.exe", PDF))));
        assertEquals("error.client_file.type_not_allowed", e.key());
        e = assertThrows(PortalException.class, () -> files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("virus.pdf", "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)))));
        assertEquals("error.client_file.content_mismatch", e.key(), "exe под видом pdf прошёл");
        e = assertThrows(PortalException.class, () -> files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("empty.pdf", new byte[0]))));
        assertEquals("error.client_file.empty", e.key());
        byte[] big = new byte[21 * 1024 * 1024];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        e = assertThrows(PortalException.class, () -> files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("big.pdf", big))));
        assertEquals("error.client_file.too_big", e.key());
        assertEquals(0, listed(files.list(DocumentType.RECEIPTS, docId)).size(), "часть файлов записалась");
    }

    /** Удалить можно у черновика; после отправки — нет. */
    @Test
    void fileIsRemovedOnlyFromDraft() {
        String docId = draft();
        String first = String.valueOf(listed(files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("a.pdf", PDF)))).getFirst().get("fileId"));
        files.remove(DocumentType.RECEIPTS, docId, first);
        assertNull(store.read(UUID.fromString(first)), "содержимое удалённого файла осталось");
        assertEquals(0, listed(files.list(DocumentType.RECEIPTS, docId)).size());

        String second = String.valueOf(listed(files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("b.pdf", PDF)))).getFirst().get("fileId"));
        drafts.confirm(DocumentType.RECEIPTS, docId);
        expectRejection(409, () -> files.remove(DocumentType.RECEIPTS, docId, second));
    }

    /** WMS не принимает — повтор; 15 неудач — ошибка; «Отправить ещё раз» — снова в очередь. */
    @Test
    void failedSendingIsRetriedAndCanBeResent() {
        String docId = draft();
        drafts.confirm(DocumentType.RECEIPTS, docId);
        doThrow(new IllegalStateException("WMS молчит")).when(wms).uploadFile(any(), anyString(), anyString(), anyString(), any());
        String fileId = String.valueOf(listed(files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("c.pdf", PDF)))).getFirst().get("fileId"));
        assertEquals("pending", state(docId));
        for (int i = 2; i <= 15; i++) {
            owner().jdbc.sql("UPDATE dbo.stored_file SET next_attempt_at = now() WHERE file_id = :f::uuid")
                    .param("f", fileId).update();
            files.deliverDue();
        }
        assertEquals("failed", state(docId), "после 15 неудач файл не в ошибке");

        doCallRealMethod().when(wms).uploadFile(any(), anyString(), anyString(), anyString(), any());
        files.resend(DocumentType.RECEIPTS, docId, fileId);
        assertEquals("sent", state(docId));
        expectRejection(409, () -> files.resend(DocumentType.RECEIPTS, docId, fileId));
    }

    /** Чужой клиент файл не видит и не скачивает. */
    @Test
    void anotherClientSeesNothing() {
        String docId = draft();
        String fileId = String.valueOf(listed(files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("d.pdf", PDF)))).getFirst().get("fileId"));
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        expectRejection(404, () -> files.list(DocumentType.RECEIPTS, docId));
        expectRejection(404, () -> files.download(DocumentType.RECEIPTS, docId, fileId));
    }

    /** Черновик удалён — его файлы тоже: описание сразу, содержимое — уборкой. */
    @Test
    void deletedDraftTakesItsFiles() {
        String docId = draft();
        UUID fileId = UUID.fromString(String.valueOf(listed(files.add(DocumentType.RECEIPTS, docId,
                List.of(new Upload("e.pdf", PDF)))).getFirst().get("fileId")));
        drafts.delete(DocumentType.RECEIPTS, docId);
        created.remove(docId);
        assertEquals(0, owner().jdbc.sql("SELECT count(*) FROM dbo.stored_file WHERE file_id = :f")
                .param("f", fileId).query(Integer.class).single());
        cleanup.sweep(Instant.now().plusSeconds(60));
        assertNull(store.read(fileId));
    }

    /** Мёртвая таблица фото из схемы V1 удалена. */
    @Test
    void oldPhotoTableIsGone() {
        assertTrue(owner().jdbc.sql("SELECT to_regclass('dbo.document_photo') IS NULL AND"
                + " to_regclass('dbo.v_document_photos') IS NULL").query(Boolean.class).single());
    }
}
