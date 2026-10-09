package com.ahlers.portal3pl.catalog;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Единицы измерения — вкладка раздела «Справочники»:
 * <pre>
 *   GET    /api/units               список и признак «вправе править»
 *   POST   /api/units               создать
 *   PUT    /api/units/{id}          сохранить
 *   POST   /api/units/{id}/archive  снять или вернуть «Действует»
 *   DELETE /api/units/{id}          удалить
 * </pre>
 *
 * <p>Все изменения возвращают весь список заново: справочник короткий,
 * а экран не должен додумывать результат по коду ответа.
 *
 * <p>Читают все вошедшие, правит администратор — проверка в
 * {@link UnitService}, потому что причина запрета в природе данных
 * (справочник общий для всех клиентов), а не в адресе.
 */
@RestController
public class UnitController {

    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping("/api/units")
    public Map<String, Object> list() {
        return service.list();
    }

    @PostMapping("/api/units")
    public Map<String, Object> create(@RequestBody UnitService.Unit body) {
        return service.save(null, body);
    }

    @PutMapping("/api/units/{id}")
    public Map<String, Object> update(@PathVariable int id, @RequestBody UnitService.Unit body) {
        return service.save(id, body);
    }

    @PostMapping("/api/units/{id}/archive")
    public Map<String, Object> archive(@PathVariable int id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return service.setActive(id, body != null && Boolean.TRUE.equals(body.get("active")));
    }

    @DeleteMapping("/api/units/{id}")
    public Map<String, Object> delete(@PathVariable int id) {
        return service.delete(id);
    }
}
