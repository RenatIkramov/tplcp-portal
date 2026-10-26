package com.ahlers.portal3pl.stock;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.lists.CatalogList;
import com.ahlers.portal3pl.lists.ListService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Бизнес-слой экрана «Запасы». Пока логики немного: включить изоляцию
 * по поклажедателю и прочитать представление. Дальше сюда добавятся
 * проверки прав и правила из §8.3.
 */
@Service
public class StockService {

    private final DepositorScope depositorScope;
    private final StockRepository repository;
    private final ListService lists;

    public StockService(DepositorScope depositorScope, StockRepository repository,
                        ListService lists) {
        this.depositorScope = depositorScope;
        this.repository = repository;
        this.lists = lists;
    }

    /**
     * Страница запасов: отбор, порядок и страница — из адреса запроса.
     *
     * <p>Прежде здесь были два своих параметра, {@code warehouseId}
     * и {@code search}, а экран отбирал по четырём полям у себя
     * в браузере. Сходилось это плохо: сервер знал меньше, чем
     * показывал экран, а приделать к такому отбору страницы нельзя
     * вовсе — сервер отрезал бы двадцать строк ДО того, как экран
     * применит свои четыре поля.
     *
     * <p>Теперь отбор описан справочником колонок (V79) и считается
     * там же, где у документов.
     */
    public Map<String, Object> list(PortalUser user, Map<String, String> params) {
        return lists.page(user, CatalogList.STOCK, params);
    }

    /** Разбивка остатка по партиям для одного товара. */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> details(String skuCode, String warehouseId) {
        depositorScope.apply();
        return repository.details(skuCode, warehouseId);
    }
}
