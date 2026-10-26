package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.Fail;

/**
 * Справочники и прочие «плоские» списки, которые экран читает как есть.
 * Как и у документов, перечисление защищает от подстановки произвольного
 * имени представления в запрос.
 *
 * <p>Товары и упаковки остались здесь не ради своего экрана — его ведёт
 * {@code CatalogList} со страницами и отбором, — а ради выбора:
 * {@code /api/sku/options} отдаёт весь набор, потому что искать товар
 * человек начинает с середины наименования.
 *
 * <p>{@code orderBy} — порядок по умолчанию.
 */
public enum CatalogType {

    SKU("sku", "v_sku_full", "code"),
    PACKAGING("packaging", "v_product_packaging", "sku_code, coefficient"),
    SUPPLIERS("suppliers", "v_suppliers", "name"),
    CONSIGNEES("consignees", "v_consignees", "name"),
    WAREHOUSES("warehouses", "v_warehouses", "name"),
    DEPOSITORS("depositors", "v_depositors", "org_name"),
    SERVICE_TARIFFS("service-tariffs", "v_service_tariffs", "service"),
    INVOICES("invoices", "v_invoices", "period_from DESC");

    private final String path;
    private final String view;
    private final String orderBy;

    CatalogType(String path, String view, String orderBy) {
        this.path = path;
        this.view = view;
        this.orderBy = orderBy;
    }

    public static CatalogType ofPath(String path) {
        for (CatalogType t : values()) {
            if (t.path.equals(path)) {
                return t;
            }
        }
        throw Fail.notFound("error.directory.unknown", "path", path);
    }

    public String view() {
        return view;
    }

    public String orderBy() {
        return orderBy;
    }

    /**
     * Есть ли у справочника архив (колонка is_active). Склады, поклажедатели,
     * тарифы и счета архивом не управляются из портала, поэтому условие
     * по is_active к ним не добавляется — колонки просто нет.
     */
    public boolean hasArchive() {
        return this == SKU || this == SUPPLIERS || this == CONSIGNEES;
    }
}
