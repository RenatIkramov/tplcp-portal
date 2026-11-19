package com.ahlers.portal3pl.reports;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Каталог отчётов (V135):
 * <pre>
 *   GET            /api/admin/reports                         каталог
 *   GET            /api/admin/report-options                  места, типы параметров, языки
 *   GET|PUT        /api/admin/report-settings                 срок хранения запусков {retentionDays}
 *   POST           /api/admin/reports                         новый отчёт
 *   GET|PUT|DELETE /api/admin/reports/{id}                    карточка, правка, удаление
 *   GET|PUT        /api/admin/depositor-directory/{id}/reports  отчёты клиента
 *
 *   GET            /api/reports?place=01                      отчёты для экрана клиента
 * </pre>
 */
@RestController
public class ReportCatalogController {

    private final ReportCatalogService reports;
    private final SessionUser session;

    public ReportCatalogController(ReportCatalogService reports, SessionUser session) {
        this.reports = reports;
        this.session = session;
    }

    @GetMapping("/api/admin/reports")
    public List<Map<String, Object>> list(HttpServletRequest request) {
        return reports.list(session.requireUser(request));
    }

    @GetMapping("/api/admin/report-options")
    public Map<String, Object> options(HttpServletRequest request) {
        return reports.options(session.requireUser(request));
    }

    public record Settings(Integer retentionDays) {
    }

    @GetMapping("/api/admin/report-settings")
    public Map<String, Object> settings(HttpServletRequest request) {
        return reports.settings(session.requireUser(request));
    }

    @PutMapping("/api/admin/report-settings")
    public Map<String, Object> saveSettings(@RequestBody Settings body, HttpServletRequest request) {
        return reports.saveSettings(session.requireUser(request), body == null ? null : body.retentionDays());
    }

    @GetMapping("/api/admin/reports/{id}")
    public Map<String, Object> card(@PathVariable String id, HttpServletRequest request) {
        return reports.card(session.requireUser(request), id);
    }

    @PostMapping("/api/admin/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody ReportCatalogService.Report report, HttpServletRequest request) {
        return reports.create(session.requireUser(request), report);
    }

    @PutMapping("/api/admin/reports/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody ReportCatalogService.Report report,
                                      HttpServletRequest request) {
        return reports.update(session.requireUser(request), id, report);
    }

    @DeleteMapping("/api/admin/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, HttpServletRequest request) {
        reports.delete(session.requireUser(request), id);
    }

    @GetMapping("/api/admin/depositor-directory/{id}/reports")
    public List<Map<String, Object>> depositorReports(@PathVariable String id, HttpServletRequest request) {
        return reports.depositorReports(session.requireUser(request), id);
    }

    @PutMapping("/api/admin/depositor-directory/{id}/reports")
    public List<Map<String, Object>> saveDepositorReports(@PathVariable String id,
                                                          @RequestBody List<ReportCatalogService.Assignment> rows,
                                                          HttpServletRequest request) {
        return reports.saveDepositorReports(session.requireUser(request), id, rows);
    }

    @GetMapping("/api/reports")
    public List<Map<String, Object>> clientReports(@RequestParam(required = false) String place,
                                                   HttpServletRequest request) {
        session.requireUser(request);
        return reports.clientReports(place);
    }
}
