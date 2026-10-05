package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CatalogService {

    private final DepositorScope depositorScope;
    private final CatalogRepository repository;

    public CatalogService(DepositorScope depositorScope, CatalogRepository repository) {
        this.depositorScope = depositorScope;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(CatalogType type, String search, String group, String itemType) {
        // Склады общие для портала, но контекст выставляем всё равно:
        // тогда поведение одинаково для всех справочников, и забыть его негде.
        depositorScope.apply();
        return repository.list(type, search, group, itemType);
    }
}
