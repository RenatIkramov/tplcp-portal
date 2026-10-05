package com.ahlers.portal3pl.stock;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Одна строка экрана «Запасы» — ровно то, что отдаёт представление v_stock_lines.
 *
 * record — короткая форма неизменяемого класса: Java сама создаёт конструктор,
 * геттеры, equals и toString. Spring превращает такой объект в JSON,
 * где имена полей становятся именами ключей.
 */
public record StockLine(
        String skuCode,
        String skuName,
        String itemGroupName,
        String warehouseId,
        String warehouseName,
        String unitName,
        BigDecimal quantity,
        BigDecimal reservedQty,
        BigDecimal blockedQty,
        BigDecimal availableQty,
        BigDecimal defectQty,
        BigDecimal incomingQty,
        int batchCount,
        OffsetDateTime syncedAt
) {
}
