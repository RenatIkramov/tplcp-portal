package com.ahlers.portal3pl.documents;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Один контроллер на три типа документов: поступления, заказы, перемещения.
 * Тип приходит частью адреса и проверяется по перечислению DocumentType.
 *
 *   GET /api/receipts?search=ПН&status=received&dateFrom=2026-09-01
 *   GET /api/orders/{docId}            — карточка: шапка, состав, детализация
 *   GET /api/transfers/{docId}/lines   — только состав
 */
@RestController
@RequestMapping("/api/{docType:receipts|orders|transfers}")
public class DocumentController {

    private final DocumentService service;
    private final DraftService drafts;

    public DocumentController(DocumentService service, DraftService drafts) {
        this.service = service;
        this.drafts = drafts;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @PathVariable String docType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return service.list(DocumentType.ofPath(docType), search, status, warehouseId, dateFrom, dateTo);
    }

    @GetMapping("/{docId}")
    public Map<String, Object> card(@PathVariable String docType, @PathVariable String docId) {
        return service.card(DocumentType.ofPath(docType), docId);
    }

    @GetMapping("/{docId}/lines")
    public List<Map<String, Object>> lines(@PathVariable String docType, @PathVariable String docId) {
        return service.lines(DocumentType.ofPath(docType), docId);
    }

    @GetMapping("/{docId}/line-details")
    public List<Map<String, Object>> lineDetails(
            @PathVariable String docType,
            @PathVariable String docId,
            @RequestParam(required = false) Integer lineNumber) {
        return service.lineDetails(DocumentType.ofPath(docType), docId, lineNumber);
    }

    // ---------- Запись: черновики (этап 7, решение Р5) ----------

    /** Создать черновик. Тело запроса — поля шапки, можно пустое. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable String docType,
                                      @RequestBody(required = false) Map<String, Object> header) {
        return drafts.create(DocumentType.ofPath(docType), header == null ? Map.of() : header);
    }

    /** Изменить шапку черновика. */
    @PutMapping("/{docId}")
    public Map<String, Object> updateHeader(@PathVariable String docType,
                                            @PathVariable String docId,
                                            @RequestBody Map<String, Object> header) {
        return drafts.updateHeader(DocumentType.ofPath(docType), docId, header);
    }

    /** Транспортный раздел: отдельный запрос, свои правила доступа (Р5). */
    @PutMapping("/{docId}/transport")
    public Map<String, Object> updateTransport(@PathVariable String docType,
                                               @PathVariable String docId,
                                               @RequestBody Map<String, Object> transport) {
        return drafts.updateTransport(DocumentType.ofPath(docType), docId, transport);
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String docType, @PathVariable String docId) {
        drafts.delete(DocumentType.ofPath(docType), docId);
    }

    @PostMapping("/{docId}/lines")
    public Map<String, Object> addLine(@PathVariable String docType,
                                       @PathVariable String docId,
                                       @RequestBody Map<String, Object> line) {
        return drafts.addLine(DocumentType.ofPath(docType), docId, line);
    }

    /** Заменить состав документа целиком (экран черновика присылает итоговый список). */
    @PutMapping("/{docId}/lines")
    public Map<String, Object> replaceLines(@PathVariable String docType,
                                            @PathVariable String docId,
                                            @RequestBody List<Map<String, Object>> lines) {
        return drafts.replaceLines(DocumentType.ofPath(docType), docId, lines);
    }

    @PutMapping("/{docId}/lines/{lineNumber}")
    public Map<String, Object> updateLine(@PathVariable String docType,
                                          @PathVariable String docId,
                                          @PathVariable int lineNumber,
                                          @RequestBody Map<String, Object> line) {
        return drafts.updateLine(DocumentType.ofPath(docType), docId, lineNumber, line);
    }

    /** Удаление строк пачкой — в демо это флажки в составе документа. */
    @DeleteMapping("/{docId}/lines")
    public Map<String, Object> deleteLines(@PathVariable String docType,
                                           @PathVariable String docId,
                                           @RequestParam List<Integer> lineNumbers) {
        return drafts.deleteLines(DocumentType.ofPath(docType), docId, lineNumbers);
    }

    /** Проверить готовность к отправке, не отправляя. */
    @GetMapping("/{docId}/validate")
    public Map<String, Object> validate(@PathVariable String docType, @PathVariable String docId) {
        List<String> problems = drafts.validateForConfirm(DocumentType.ofPath(docType), docId);
        return Map.of("canConfirm", problems.isEmpty(), "problems", problems);
    }

    /** Подтвердить: проверка, отправка в WMS, смена статуса. */
    @PostMapping("/{docId}/confirm")
    public Map<String, Object> confirm(@PathVariable String docType, @PathVariable String docId) {
        return drafts.confirm(DocumentType.ofPath(docType), docId);
    }
}
