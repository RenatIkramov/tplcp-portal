package com.ahlers.portal3pl.stock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP-слой: превращает запрос GET /api/stock в вызов сервиса,
 * а результат — в JSON. Больше ничего здесь делать не нужно.
 * <p>
 * Примеры:
 *   GET /api/stock
 *   GET /api/stock?warehouseId=WH-ALA
 *   GET /api/stock?search=рога
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    /** GET /api/stock/details?skuCode=RK-ROG-01 — партии одного товара. */
    @GetMapping("/details")
    public List<java.util.Map<String, Object>> details(
            @RequestParam String skuCode,
            @RequestParam(required = false) String warehouseId) {
        return service.details(skuCode, warehouseId);
    }

    @GetMapping
    public List<StockLine> list(
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String search) {
        return service.list(warehouseId, search);
    }
}
