package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Журнал загрузок из Excel для администратора:
 * <pre>
 *   GET /api/admin/excel-imports                   все клиенты
 *   GET /api/admin/excel-imports?depositorId=…     один клиент
 * </pre>
 *
 * <p>Один адрес отдаёт и список загрузок, и список замечаний: экран
 * показывает их вместе, а два запроса вместо одного означали бы, что
 * сводка и таблица могут разойтись во времени.
 */
@RestController
public class ImportLogController {

    private final ImportLogService log;
    private final SessionUser session;

    public ImportLogController(ImportLogService log, SessionUser session) {
        this.log = log;
        this.session = session;
    }

    @GetMapping("/api/admin/excel-imports")
    public Map<String, Object> log(@RequestParam(required = false) String depositorId,
                                   HttpServletRequest request) {
        return log.log(session.requireUser(request),
                depositorId == null || depositorId.isBlank() ? null : depositorId);
    }
}
