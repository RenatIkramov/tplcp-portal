package com.ahlers.portal3pl.stock;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Доступ к данным запасов. Читаем представление v_stock_lines напрямую
 * через JdbcClient — без ORM: представление уже отдаёт готовые для экрана
 * колонки, и заворачивать его в сущности Hibernate смысла нет (§4 ТЗ).
 * <p>
 * Фильтра по поклажедателю в запросе НЕТ намеренно: его накладывает сама
 * база по политике RLS, опираясь на app.depositor_id (см. StockService).
 * Так забытый фильтр перестаёт быть утечкой данных между клиентами.
 */
@Repository
public class StockRepository {

    private static final String SQL = """
            SELECT sku_code, sku_name, item_group_name,
                   warehouse_id, warehouse_name, unit_name,
                   quantity, reserved_qty, blocked_qty, available_qty,
                   defect_qty, incoming_qty,
                   batch_count, synced_at
            FROM dbo.v_stock_lines
            WHERE (:warehouseId::text IS NULL OR warehouse_id = :warehouseId::text)
              AND (:search::text IS NULL OR sku_code ILIKE :search::text
                                         OR sku_name ILIKE :search::text)
            ORDER BY sku_code
            """;

    private final JdbcClient jdbc;

    public StockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String DETAILS_SQL = """
            SELECT sku_code, sku_name, warehouse_id, warehouse_name,
                   batch_number, series_number, expiry_date, quality,
                   block_reason_name, quantity, reserved_qty, blocked_qty, available_qty,
                   unit_name, updated_at, synced_at
            FROM dbo.v_stock_details
            WHERE sku_code = :skuCode
              AND (:warehouseId::text IS NULL OR warehouse_id = :warehouseId::text)
            ORDER BY warehouse_id, batch_number NULLS FIRST
            """;

    /** Партии одного товара — экран «Запасы» → «Детализация». */
    public List<java.util.Map<String, Object>> details(String skuCode, String warehouseId) {
        return com.ahlers.portal3pl.common.SqlRows.toCamelCase(jdbc.sql(DETAILS_SQL)
                .param("skuCode", skuCode)
                .param("warehouseId", warehouseId == null || warehouseId.isBlank() ? null : warehouseId)
                .query()
                .listOfRows());
    }

    public List<StockLine> find(String warehouseId, String search) {
        return jdbc.sql(SQL)
                .param("warehouseId", warehouseId)
                .param("search", search == null || search.isBlank() ? null : "%" + search + "%")
                .query(StockLine.class)
                .list();
    }
}
