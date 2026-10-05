package com.ahlers.portal3pl.stock;

import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Бизнес-слой экрана «Запасы». Пока логики немного: включить изоляцию
 * по поклажедателю и прочитать представление. Дальше сюда добавятся
 * проверки прав и правила из §8.3.
 */
@Service
public class StockService {

    private final DepositorScope depositorScope;
    private final StockRepository repository;

    public StockService(DepositorScope depositorScope, StockRepository repository) {
        this.depositorScope = depositorScope;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StockLine> list(String warehouseId, String search) {
        depositorScope.apply();
        return repository.find(warehouseId, search);
    }

    /** Разбивка остатка по партиям для одного товара. */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> details(String skuCode, String warehouseId) {
        depositorScope.apply();
        return repository.details(skuCode, warehouseId);
    }
}
