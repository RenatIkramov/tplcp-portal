package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Страница любого списка портала и настройка его фильтров.
 *
 * <p>Всё, что умеет этот класс, одинаково для документов, запасов,
 * товаров и упаковок — отличается только {@link ListKind}. Поэтому
 * экранные службы ({@code DocumentService}, {@code StockService})
 * сюда делегируют, а не повторяют у себя.
 */
@Service
public class ListService {

    private final DepositorScope depositorScope;
    private final ListRepository repository;
    private final ListFilters filters;
    private final ListPaging paging;

    public ListService(DepositorScope depositorScope, ListRepository repository,
                       ListFilters filters, ListPaging paging) {
        this.depositorScope = depositorScope;
        this.repository = repository;
        this.filters = filters;
        this.paging = paging;
    }

    /**
     * Страница списка с отбором и порядком из адреса запроса.
     *
     * <p>Контекст поклажедателя выставляется ДО разбора фильтров,
     * а не после: справочник читает запреты клиента через
     * {@code current_depositor_id()}, и без контекста он отдал бы
     * пустой список разрешённых — то есть отказ на любой фильтр.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> page(PortalUser user, ListKind kind, Map<String, String> params) {
        depositorScope.apply();
        List<ListFilters.Condition> conditions = filters.parse(user, kind, params);
        ListPaging.Page page = paging.parse(user, kind, params);
        boolean withArchived = params != null
                && "true".equalsIgnoreCase(params.get("withArchived"));

        List<Map<String, Object>> rows = repository.page(kind, conditions, page, withArchived);
        if (rows.isEmpty() && page.page() > 1) {
            // Страница кончилась под ногами: человек стоял на пятой,
            // поставил фильтр — и строк осталось на одну. Пустой экран
            // с надписью «ничего не найдено» соврал бы: найдено, просто
            // не здесь. Возвращаем первую страницу и говорим об этом
            // номером в ответе — экран подхватит его и перерисует ряд.
            page = new ListPaging.Page(1, page.size(), page.sortColumn(), page.descending());
            rows = repository.page(kind, conditions, page, withArchived);
        }
        return envelope(rows, page);
    }

    /** Какие фильтры этот человек может поставить на этом экране. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> filters(PortalUser user, ListKind kind) {
        depositorScope.apply();
        return filters.catalogue(user, kind);
    }

    /**
     * Выбор фильтров человека: какие из них на панели и в каком порядке.
     *
     * <p>Пустой список отвергается: ноль строк в таблице выбора уже
     * означает «не настраивал», и панель вернулась бы к набору
     * по умолчанию вместо того, чтобы опустеть.
     */
    @Transactional
    public List<Map<String, Object>> saveFilters(PortalUser user, ListKind kind,
                                                 List<String> columns) {
        depositorScope.apply();
        return filters.saveMine(user, kind, columns);
    }

    /** Вернуть набор фильтров по умолчанию. */
    @Transactional
    public List<Map<String, Object>> resetFilters(PortalUser user, ListKind kind) {
        depositorScope.apply();
        return filters.resetMine(user, kind);
    }

    /**
     * Ответ списка: строки и то, что о них нужно знать ряду страниц.
     *
     * <p>Голый массив отдать уже нельзя — вместе со строками нужно
     * «всего 134», а сосчитать это по двадцати присланным строкам
     * не из чего.
     *
     * <p>{@code totalRows} из каждой строки убирается: это служебная
     * колонка запроса, а не поле документа. Оставить её значило бы
     * отдавать одно и то же число двадцать раз и ждать, что никто
     * не примет его за свойство строки.
     */
    private static Map<String, Object> envelope(List<Map<String, Object>> rows,
                                                ListPaging.Page page) {
        int total = 0;
        List<Map<String, Object>> clean = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            Object count = copy.remove("totalRows");
            if (count instanceof Number number) {
                total = number.intValue();
            }
            clean.add(copy);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", clean);
        out.put("total", total);
        out.put("page", page.page());
        out.put("pageSize", page.size());
        return out;
    }
}
