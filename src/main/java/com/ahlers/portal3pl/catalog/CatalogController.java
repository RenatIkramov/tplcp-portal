package com.ahlers.portal3pl.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Справочники и списки биллинга, которые экран читает целиком:
 * <pre>
 *   GET /api/suppliers?withArchived=true
 *   GET /api/consignees
 *   GET /api/warehouses
 *   GET /api/depositors
 *   GET /api/service-tariffs
 *   GET /api/invoices
 * </pre>
 *
 * <p>Товары и упаковки отсюда ушли: у них отбор, порядок и страницы
 * считает сервер, и живут они в {@code CatalogListController}.
 * Полный набор для выбора остался и там — {@code /api/sku/options}.
 *
 * <p>Параметров отбора здесь нет. Были — {@code search}, {@code group},
 * {@code itemType}, — и ни один экран их не присылал: справочники
 * короткие, и поиск по ним шёл в браузере. Параметр, которого никто
 * не ставит, не бездействует, а ждёт: первый, кто его пришлёт, получит
 * отбор, который никогда не проверяли.
 */
@RestController
@RequestMapping("/api/{catalog:suppliers|consignees|warehouses|depositors|service-tariffs|invoices}")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @PathVariable String catalog,
            @RequestParam(required = false, defaultValue = "false") boolean withArchived) {
        return service.list(CatalogType.ofPath(catalog), withArchived);
    }
}
