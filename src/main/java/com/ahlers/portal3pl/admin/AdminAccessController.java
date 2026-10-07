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
    private final SessionUser session;

    public AdminAccessController(AdminAccessService access, SessionUser session) {
        this.access = access;
        this.session = session;
    }

    public record Warehouses(List<String> warehouseIds) {
    }

    /** Все склады портала — для галочек. */
    @GetMapping("/warehouses")
    public List<Map<String, Object>> warehouses(HttpServletRequest request) {
        return access.warehouses(actor(request));
    }

    /* ---- склады кладовщика ---- */

    @GetMapping("/users/{userId}/warehouses")
    public List<String> ofUser(@PathVariable String userId, HttpServletRequest request) {
        return access.warehousesOfUser(actor(request), userId);
    }

    @PutMapping("/users/{userId}/warehouses")
    public List<String> saveOfUser(@PathVariable String userId, @RequestBody Warehouses body,
                                    HttpServletRequest request) {
        return access.saveWarehousesOfUser(actor(request), userId, body.warehouseIds());
    }

    /* ---- склады поклажедателя ---- */

    @GetMapping("/depositor-access")
    public List<Map<String, Object>> depositorAccess(HttpServletRequest request) {
        return access.depositorAccess(actor(request));
    }

    @GetMapping("/depositors/{depositorId}/warehouses")
    public List<String> ofDepositor(@PathVariable String depositorId, HttpServletRequest request) {
        return access.warehousesOfDepositor(actor(request), depositorId);
    }

    @PutMapping("/depositors/{depositorId}/warehouses")
    public List<Map<String, Object>> saveOfDepositor(@PathVariable String depositorId,
                                                      @RequestBody Warehouses body,
                                                      HttpServletRequest request) {
        return access.saveWarehousesOfDepositor(actor(request), depositorId, body.warehouseIds());
    }

    private PortalUser actor(HttpServletRequest request) {
        return session.requireUser(request);
    }
}
