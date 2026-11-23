package com.ahlers.portal3pl.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Комплекты (V154):
 * <pre>
 *   GET /api/sku/kit-list    комплекты клиента: состав и «можно собрать» по складам
 *   PUT /api/sku/{id}/kit    состав целиком, {"components": [{"code", "qty"}]} — администратор, сотрудник склада
 * </pre>
 * Составы для окна строки документа — {@code GET /api/sku/kits} в {@link SkuWriteController}.
 */
@RestController
public class KitController {

    private final KitService service;

    public KitController(KitService service) {
        this.service = service;
    }

    @GetMapping("/api/sku/kit-list")
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @PutMapping("/api/sku/{id}/kit")
    public Map<String, Object> save(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.save(id, body);
    }
}
