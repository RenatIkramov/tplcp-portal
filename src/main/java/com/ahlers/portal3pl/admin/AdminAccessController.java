package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Экраны доступов режима администрирования. */
@RestController
@RequestMapping("/api/admin")
public class AdminAccessController {

    private final AdminAccessService access;
    private final DepositorSettingsService settings;
    private final SessionUser session;

    public AdminAccessController(AdminAccessService access,
                                 DepositorSettingsService settings, SessionUser session) {
        this.access = access;
        this.settings = settings;
        this.session = session;
    }

    /** Все склады портала — для галочек. */
    @GetMapping("/warehouses")
    public List<Map<String, Object>> warehouses(HttpServletRequest request) {
        return access.warehouses(actor(request));
    }

    /* ---- склады пользователя (сохраняет карточка — PUT /users/{id}/profile) ---- */

    @GetMapping("/users/{userId}/warehouses")
    public List<String> ofUser(@PathVariable String userId, HttpServletRequest request) {
        return access.warehousesOfUser(actor(request), userId);
    }

    /* ---- настройки поклажедателя ---- */

    /**
     * Настройки, которые действуют на всех пользователей клиента.
     * Прежде «Показать раздел перемещения» стояло у каждого человека
     * отдельно; с V35 это настройка клиента.
     */
    @GetMapping("/depositors/{depositorId}/settings")
    public Map<String, Object> settingsOfDepositor(@PathVariable String depositorId,
                                                   HttpServletRequest request) {
        return settings.settings(actor(request), depositorId);
    }

    @PutMapping("/depositors/{depositorId}/settings")
    public Map<String, Object> saveSettingsOfDepositor(
            @PathVariable String depositorId,
            @RequestBody DepositorSettingsService.Settings body,
            HttpServletRequest request) {
        return settings.save(actor(request), depositorId, body);
    }

    private PortalUser actor(HttpServletRequest request) {
        return session.requireUser(request);
    }
}
