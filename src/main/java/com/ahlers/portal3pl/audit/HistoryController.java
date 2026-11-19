package com.ahlers.portal3pl.audit;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * «История» карточек (V118):
 * <pre>
 *   GET /api/history/{sku|packaging|supplier|consignee}/{id}       клиент и оператор, под RLS
 *   GET /api/history/{app_user|warehouse|depositor|operator}/{id}  только администратор
 * </pre>
 * У документов свой адрес: {@code /api/{receipts|orders|transfers}/{id}/history}.
 */
@RestController
public class HistoryController {

    private final AuditService audit;
    private final SessionUser session;

    public HistoryController(AuditService audit, SessionUser session) {
        this.audit = audit;
        this.session = session;
    }

    @GetMapping("/api/history/{objectType}/{objectId}")
    public List<Map<String, Object>> history(@PathVariable String objectType, @PathVariable String objectId,
                                             HttpServletRequest request) {
        return audit.history(session.requireUser(request), objectType, objectId);
    }
}
