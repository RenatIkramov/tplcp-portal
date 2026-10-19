package com.ahlers.portal3pl.documents;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Списки для выпадающих списков шапки документа.
 *
 * <pre>
 *   GET /api/document-dictionaries   типы заказов и типы приходных документов
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

    public DocumentDictionaryController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/document-dictionaries")
    public Map<String, Object> dictionaries() {
        return service.dictionaries();
    }
}
