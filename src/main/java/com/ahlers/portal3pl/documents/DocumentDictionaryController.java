package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Списки для выпадающих списков шапки документа.
 *
 * <pre>
 *   GET /api/document-dictionaries     типы заказов и типы приходных документов
 *   GET    /api/document-filters/{тип}  фильтры списка, доступные этому человеку
 *   PUT    /api/document-filters/{тип}  какие из них он держит на панели
 *   DELETE /api/document-filters/{тип}  вернуть набор по умолчанию
 * </pre>
 *
 * <p>Почему отдельный контроллер, а не метод в {@link DocumentController}:
 * тот отвечает по адресу {@code /api/{receipts|orders|transfers}}, то есть
 * все его пути начинаются с типа документа. А эти списки общие и к одному
 * типу не относятся — заказы и поступления выбирают из них оба.
 *
 * <p>Почему эти списки вообще понадобились порталу. До сих пор они были
 * только в администрировании (V24), а карточка документа предлагала
 * НАИМЕНОВАНИЯ из демо-массива прототипа. Выбранное наименование уходило
 * на сервер в колонку {@code order_type_id} — и сохранение шапки падало
 * ошибкой приведения типа. То есть черновик заказа нельзя было сохранить
 * вовсе, стоило лишь тронуть тип заказа.
 */
@RestController
public class DocumentDictionaryController {

    private final DocumentService service;
    private final SessionUser session;

    public DocumentDictionaryController(DocumentService service, SessionUser session) {
        this.service = service;
        this.session = session;
    }

    @GetMapping("/api/document-dictionaries")
    public Map<String, Object> dictionaries() {
        return service.dictionaries();
    }

    /**
     * Фильтры списка документов: подпись, вид, значения справочника.
     *
     * <p>Адрес отдельный, а не {@code /api/receipts/filters}, нарочно:
     * там уже живёт {@code /api/receipts/{docId}}, и «filters» спорил бы
     * с идентификатором документа. Спор этот Spring разрешает в нашу
     * пользу, но полагаться на порядок разбора путей там, где можно
     * просто не создавать двусмысленности, — лишний риск.
     */
    @GetMapping("/api/document-filters/{docType}")
    public List<Map<String, Object>> filters(@PathVariable String docType,
                                             HttpServletRequest request) {
        return service.filters(session.requireUser(request), DocumentType.ofPath(docType));
    }

    /**
     * Какие фильтры человек держит на панели и в каком порядке —
     * окно ⚙ Фильтры.
     *
     * <p>Тело запроса такое же, как у выбора колонок
     * ({@code {"columns": [...]}}): устроено одинаково нарочно, чтобы
     * два окна с одним смыслом не расходились ни на экране, ни здесь.
     */
    @PutMapping("/api/document-filters/{docType}")
    public List<Map<String, Object>> saveFilters(@PathVariable String docType,
                                                 @RequestBody Map<String, List<String>> body,
                                                 HttpServletRequest request) {
        return service.saveFilters(session.requireUser(request), DocumentType.ofPath(docType),
                body == null ? List.of() : body.getOrDefault("columns", List.of()));
    }

    /** Вернуть набор фильтров по умолчанию — кнопка «Сбросить». */
    @DeleteMapping("/api/document-filters/{docType}")
    public List<Map<String, Object>> resetFilters(@PathVariable String docType,
                                                  HttpServletRequest request) {
        return service.resetFilters(session.requireUser(request), DocumentType.ofPath(docType));
    }
}
