package com.ahlers.portal3pl.stock;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HTTP-слой: превращает запрос GET /api/stock в вызов сервиса,
 * а результат — в JSON. Больше ничего здесь делать не нужно.
 * <p>
 * Примеры:
 *   GET /api/stock?page=1&pageSize=20
 *   GET /api/stock?filter.warehouse=WH-ALA&sort=code&dir=asc
 *   GET /api/stock?filter.name=рога&filter.defect=true
 *
 * <p>Отбор описан справочником колонок, как у документов: что
 * законно, решает {@code ListFilters}, страницу и порядок —
 * {@code ListPaging}. Ответ — конверт
 * {@code {rows, total, page, pageSize}}: вместе со строками экрану
 * нужно «всего 134», а по двадцати присланным строкам это число
 * не сосчитать.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;
    private final SessionUser session;

    public StockController(StockService service, SessionUser session) {
        this.service = service;
        this.session = session;
    }

    /** GET /api/stock/details?skuCode=RK-ROG-01 — партии одного товара. */
    @GetMapping("/details")
    public List<java.util.Map<String, Object>> details(
            @RequestParam String skuCode,
            @RequestParam(required = false) String warehouseId) {
        return service.details(skuCode, warehouseId);
    }

    /**
     * Страница запасов.
     *
     * <p>Параметры принимаются картой целиком: набор колонок задаётся
     * справочником и меняется миграцией, а не подписью этого метода.
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> params,
                                    HttpServletRequest request) {
        return service.list(session.requireUser(request), params);
    }
}
