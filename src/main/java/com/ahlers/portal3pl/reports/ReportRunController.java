package com.ahlers.portal3pl.reports;

import com.ahlers.portal3pl.auth.SessionUser;
import com.ahlers.portal3pl.documents.WmsGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Запуск отчётов (V138):
 * <pre>
 *   POST /api/reports/{reportId}/runs     запустить: {values: {код: значение}, skuId}
 *   GET  /api/reports/runs?place=&skuId=  запуски клиента, новые сверху; отбор по месту и товару
 *   GET  /api/reports/runs/{runId}/file   файл готового отчёта
 *   GET  /api/reports/choices?type=04     значения для выпадающего списка параметра (код, имя)
 * </pre>
 */
@RestController
public class ReportRunController {

    private final ReportRunService runs;
    private final SessionUser session;

    public ReportRunController(ReportRunService runs, SessionUser session) {
        this.runs = runs;
        this.session = session;
    }

    @PostMapping("/api/reports/{reportId}/runs")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String reportId,
                                                     @RequestBody(required = false) ReportRunService.Request body,
                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(runs.start(session.requireUser(request), reportId, body));
    }

    @GetMapping("/api/reports/runs")
    public List<Map<String, Object>> list(@RequestParam(required = false) String place,
                                          @RequestParam(required = false) String skuId,
                                          HttpServletRequest request) {
        session.requireUser(request);
        return runs.list(place, skuId);
    }

    @GetMapping("/api/reports/choices")
    public List<Map<String, Object>> choices(@RequestParam String type, HttpServletRequest request) {
        session.requireUser(request);
        return runs.choices(type);
    }

    @GetMapping("/api/reports/runs/{runId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String runId, HttpServletRequest request) {
        session.requireUser(request);
        return attachment(runs.file(runId));
    }

    /** Файл отчёта ответом «скачать»: имя по-русски — в {@code filename*} (V138; и администратору, V149). */
    static ResponseEntity<byte[]> attachment(WmsGateway.ReportFile file) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(file.contentType() == null ? "application/octet-stream" : file.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName() == null ? "report" : file.fileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(file.content());
    }
}
