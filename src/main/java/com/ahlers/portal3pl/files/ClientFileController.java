package com.ahlers.portal3pl.files;

import com.ahlers.portal3pl.documents.DocumentType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Файлы клиента к документу (V145):
 * <pre>
 *   GET    /api/{вид}/{docId}/files                  список и ограничения приёма
 *   POST   /api/{вид}/{docId}/files                  приложить (multipart, поле files, можно несколько)
 *   GET    /api/{вид}/{docId}/files/{fileId}         скачать
 *   DELETE /api/{вид}/{docId}/files/{fileId}         убрать — у черновика
 *   POST   /api/{вид}/{docId}/files/{fileId}/resend  отправить в WMS ещё раз
 * </pre>
 * Вход и клиент проверяет общий фильтр запросов, права — служба.
 */
@RestController
public class ClientFileController {

    private static final String BASE = "/api/{docType:receipts|orders|transfers}/{docId}/files";

    private final ClientFileService files;

    public ClientFileController(ClientFileService files) {
        this.files = files;
    }

    @GetMapping(BASE)
    public Map<String, Object> list(@PathVariable String docType, @PathVariable String docId) {
        return files.list(DocumentType.ofPath(docType), docId);
    }

    @PostMapping(BASE)
    public Map<String, Object> add(@PathVariable String docType, @PathVariable String docId,
                                   @RequestParam("files") List<MultipartFile> parts) {
        List<ClientFileService.Upload> uploads = new ArrayList<>();
        for (MultipartFile part : parts) {
            try {
                uploads.add(new ClientFileService.Upload(part.getOriginalFilename(), part.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files.add(DocumentType.ofPath(docType), docId, uploads);
    }

    @GetMapping(BASE + "/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String docType, @PathVariable String docId,
                                           @PathVariable String fileId) {
        ClientFileService.Download file = files.download(DocumentType.ofPath(docType), docId, fileId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(file.contentType() == null ? "application/octet-stream" : file.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @DeleteMapping(BASE + "/{fileId}")
    public Map<String, Object> remove(@PathVariable String docType, @PathVariable String docId,
                                      @PathVariable String fileId) {
        return files.remove(DocumentType.ofPath(docType), docId, fileId);
    }

    @PostMapping(BASE + "/{fileId}/resend")
    public Map<String, Object> resend(@PathVariable String docType, @PathVariable String docId,
                                      @PathVariable String fileId) {
        return files.resend(DocumentType.ofPath(docType), docId, fileId);
    }
}
