package com.ahlers.portal3pl.reports;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Запуски отчётов» администратора (V149):
 * <pre>
 *   GET /api/admin/report-runs?depositorId=&reportId=&status=&from=&to=&page=&pageSize=
 *   GET /api/admin/report-runs/statuses      статусы для отбора
 *   GET /api/admin/report-runs/{runId}/file  файл готового отчёта любого клиента
 * </pre>
 * Неверный идентификатор или дата в запросе — 400 (разбирает Spring).
 */
@RestController
@RequestMapping("/api/admin/report-runs")
public class AdminReportRunController {

    private final AdminReportRunService runs;
    private final SessionUser session;

    public AdminReportRunController(AdminReportRunService runs, SessionUser session) {
        this.runs = runs;
        this.session = session;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String page(@RequestParam(required = false) UUID depositorId,
                       @RequestParam(required = false) UUID reportId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "50") int pageSize,
                       HttpServletRequest request) {
        return runs.page(session.requireUser(request), depositorId, reportId, status, from, to, page, pageSize);
    }

    @GetMapping("/statuses")
    public List<Map<String, Object>> statuses(HttpServletRequest request) {
        return runs.statuses(session.requireUser(request));
    }

    @GetMapping("/{runId}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID runId, HttpServletRequest request) {
        return ReportRunController.attachment(runs.file(session.requireUser(request), runId));
    }
}
