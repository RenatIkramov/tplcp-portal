package com.ahlers.portal3pl.audit;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Журнал действий в администрировании (группа «Журналы»):
 * <pre>
 *   GET /api/admin/audit?depositorId=&amp;actor=&amp;objectType=&amp;from=&amp;to=&amp;page=&amp;pageSize=
 *   GET /api/admin/audit/actors
 *   GET /api/admin/audit/settings          {retentionMonths}
 *   PUT /api/admin/audit/settings          {retentionMonths}
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditService audit;
    private final SessionUser session;

    public AuditController(AuditService audit, SessionUser session) {
        this.audit = audit;
        this.session = session;
    }

    /** Ответ — готовый JSON из базы: {total, page, pageSize, rows}. */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String page(@RequestParam(required = false) String depositorId,
                       @RequestParam(required = false) String actor,
                       @RequestParam(required = false) String objectType,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "50") int pageSize,
                       HttpServletRequest request) {
        return audit.page(session.requireUser(request), depositorId, actor, objectType, from, to, page, pageSize);
    }

    @GetMapping("/actors")
    public List<Map<String, Object>> actors(HttpServletRequest request) {
        return audit.actors(session.requireUser(request));
    }

    @GetMapping("/settings")
    public Map<String, Object> settings(HttpServletRequest request) {
        return audit.settings(session.requireUser(request));
    }

    public record Settings(Integer retentionMonths) {
    }

    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Settings body, HttpServletRequest request) {
        return audit.saveSettings(session.requireUser(request), body.retentionMonths());
    }
}
