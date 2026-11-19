package com.ahlers.portal3pl.integration;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Администрирование → «Интеграция» (V143):
 * <pre>
 *   GET|PUT /api/admin/wms-settings           настройки интеграции целиком
 *   GET     /api/admin/stock-sync             журнал сверок остатков
 *   POST    /api/admin/stock-sync             сверить сейчас
 *   GET     /api/admin/stock-sync/{id}/diff   расхождения одной сверки
 * </pre>
 */
@RestController
public class WmsSettingsController {

    private final WmsSettingsService settings;
    private final StockSyncService sync;
    private final SessionUser session;

    public WmsSettingsController(WmsSettingsService settings, StockSyncService sync, SessionUser session) {
        this.settings = settings;
        this.sync = sync;
        this.session = session;
    }

    @GetMapping("/api/admin/wms-settings")
    public Map<String, Object> settings(HttpServletRequest request) {
        return settings.settings(session.requireUser(request));
    }

    @PutMapping("/api/admin/wms-settings")
    public Map<String, Object> save(@RequestBody WmsSettingsService.Settings body, HttpServletRequest request) {
        return settings.save(session.requireUser(request), body);
    }

    @GetMapping("/api/admin/stock-sync")
    public List<Map<String, Object>> log(HttpServletRequest request) {
        return sync.log(session.requireUser(request));
    }

    @PostMapping("/api/admin/stock-sync")
    public Map<String, Object> runNow(HttpServletRequest request) {
        return sync.runNow(session.requireUser(request));
    }

    @GetMapping("/api/admin/stock-sync/{id}/diff")
    public List<Map<String, Object>> diff(@PathVariable String id, HttpServletRequest request) {
        return sync.diff(session.requireUser(request), id);
    }
}
