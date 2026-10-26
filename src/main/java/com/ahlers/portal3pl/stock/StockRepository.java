package com.ahlers.portal3pl.stock;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Доступ к данным запасов: разбивка остатка по партиям.
 *
 * <p>Сам список запасов читает общий {@code ListRepository}: отбор,
 * порядок и страницы у всех списков портала устроены одинаково,
 * и запрос у них один. Здесь остаётся то, чего нет у других, —
 * партии одного товара.
 * <p>
 * Фильтра по поклажедателю в запросе НЕТ намеренно: его накладывает сама
 * база по политике RLS, опираясь на app.depositor_id (см. StockService).
 * Так забытый фильтр перестаёт быть утечкой данных между клиентами.
 */
@Repository
public class StockRepository {

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
}
