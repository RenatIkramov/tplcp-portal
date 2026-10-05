package com.ahlers.portal3pl.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Справочники и списки биллинга:
 *
 *   GET /api/sku?search=рога&group=Сырьё
 *   GET /api/suppliers
 *   GET /api/consignees
 *   GET /api/warehouses
 *   GET /api/depositors
 *   GET /api/service-tariffs
 *   GET /api/invoices
 */
@RestController
@RequestMapping("/api/{catalog:sku|suppliers|consignees|warehouses|depositors|service-tariffs|invoices}")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @PathVariable String catalog,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String itemType) {
        return service.list(CatalogType.ofPath(catalog), search, group, itemType);
    }
}
