package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.auth.SessionUser;
import com.ahlers.portal3pl.documents.DocumentType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Панель фильтров любого списка портала.
 *
 * <pre>
 *   GET    /api/list-filters/{список}  что человек может поставить
 *   PUT    /api/list-filters/{список}  что он держит на панели и в каком порядке
 *   DELETE /api/list-filters/{список}  вернуть набор по умолчанию
 * </pre>
 *
 * <p>Список — это {@code receipts}, {@code orders}, {@code transfers},
 * {@code stock}. Прежде адрес назывался {@code /api/document-filters}
 * и знал только про документы; запасам пришлось бы заводить второй
 * такой же. Два адреса с одним смыслом расходятся — сначала в мелочи,
 * потом в поведении, и чинить их приходится дважды.
 *
 * <p>Адрес отдельный, а не {@code /api/receipts/filters}, нарочно:
 * там уже живёт {@code /api/receipts/{docId}}, и «filters» спорил бы
 * с идентификатором документа. Спор этот Spring разрешает в нашу
 * пользу, но полагаться на порядок разбора путей там, где можно просто
 * не создавать двусмысленности, — лишний риск.
 */
@RestController
public class ListFiltersController {

    private final ListService service;
    private final SessionUser session;

    public ListFiltersController(ListService service, SessionUser session) {
        this.service = service;
        this.session = session;
    }

    @GetMapping("/api/list-filters/{list}")
    public List<Map<String, Object>> filters(@PathVariable String list,
                                             HttpServletRequest request) {
        return service.filters(session.requireUser(request), kindOf(list));
    }

    /**
     * Какие фильтры человек держит на панели и в каком порядке —
     * окно ⚙ Фильтры.
     *
     * <p>Тело запроса такое же, как у выбора колонок
     * ({@code {"columns": [...]}}): устроено одинаково нарочно, чтобы
     * два окна с одним смыслом не расходились ни на экране, ни здесь.
     */
    @PutMapping("/api/list-filters/{list}")
    public List<Map<String, Object>> saveFilters(@PathVariable String list,
                                                 @RequestBody Map<String, List<String>> body,
                                                 HttpServletRequest request) {
        return service.saveFilters(session.requireUser(request), kindOf(list),
                body == null ? List.of() : body.getOrDefault("columns", List.of()));
    }

    /** Вернуть набор фильтров по умолчанию — кнопка «Сбросить». */
    @DeleteMapping("/api/list-filters/{list}")
    public List<Map<String, Object>> resetFilters(@PathVariable String list,
                                                  HttpServletRequest request) {
        return service.resetFilters(session.requireUser(request), kindOf(list));
    }

    /**
     * Имя списка — в известный вид списка.
     *
     * <p>Сначала документы, потом остальные: имя приходит из адреса
     * и дальше подставляется в текст запроса, поэтому превратиться
     * оно может только в значение перечисления. Незнакомое имя —
     * отказ, а не пустой список: пустой список панель поняла бы как
     * «фильтров нет», и человек увидел бы экран без отбора вместо
     * сообщения о поломке.
     */
    private static ListKind kindOf(String list) {
        for (DocumentType type : DocumentType.values()) {
            if (type.tableKey().equals(list)) {
                return type;
            }
        }
        return CatalogList.ofPath(list);
    }
}
