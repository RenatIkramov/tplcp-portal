package com.ahlers.portal3pl.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Справочники и прочие «плоские» списки, которые экран читает как есть.
 * Как и у документов, перечисление защищает от подстановки произвольного
 * имени представления в запрос.
 * <p>
 * searchColumns — по каким колонкам работает параметр search.
 * orderBy — порядок по умолчанию.
 */
public enum CatalogType {

    SKU("sku", "v_sku_full", List.of("code", "item_code", "name"), "code"),
    SUPPLIERS("suppliers", "v_suppliers", List.of("code", "name", "bin"), "name"),
    CONSIGNEES("consignees", "v_consignees", List.of("code", "name", "bin"), "name"),
    WAREHOUSES("warehouses", "v_warehouses", List.of("code", "name", "address"), "name"),
    DEPOSITORS("depositors", "v_depositors", List.of("code", "org_name", "bin"), "org_name"),
    SERVICE_TARIFFS("service-tariffs", "v_service_tariffs", List.of("service"), "service"),
    INVOICES("invoices", "v_invoices", List.of("invoice_number"), "period_from DESC");

    private final String path;
    private final String view;
    private final List<String> searchColumns;
    private final String orderBy;

    CatalogType(String path, String view, List<String> searchColumns, String orderBy) {
        this.path = path;
        this.view = view;
        this.searchColumns = searchColumns;
        this.orderBy = orderBy;
    }

    public static CatalogType ofPath(String path) {
        for (CatalogType t : values()) {
            if (t.path.equals(path)) {
                return t;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный справочник: " + path);
    }

    public String view() {
        return view;
    }

    public List<String> searchColumns() {
        return searchColumns;
    }

    public String orderBy() {
        return orderBy;
    }
}
