package com.ahlers.portal3pl.catalog;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Ведение контрагентов. Списки по-прежнему отдаёт {@link CatalogController}
 * (GET /api/suppliers, GET /api/consignees), здесь — карточка и изменения:
 * <pre>
 *   GET    /api/{suppliers|consignees}/{id}          карточка
 *   POST   /api/{suppliers|consignees}               создать
 *   PUT    /api/{suppliers|consignees}/{id}          сохранить
 *   POST   /api/{suppliers|consignees}/{id}/archive  в архив / вернуть
 *   DELETE /api/{suppliers|consignees}/{id}          удалить
 *
 *   GET    /api/consignees/{id}/addresses    адреса доставки
 *   POST   /api/consignees/{id}/addresses    добавить адрес
 *   PUT    /api/addresses/{id}               сохранить адрес
 *   POST   /api/addresses/{id}/default       сделать основным
 *   DELETE /api/addresses/{id}               удалить адрес
 * </pre>
 *
 * <p>Изменения адреса возвращают весь список адресов грузополучателя,
 * изменения карточки — всю карточку: экран перерисовывает её целиком и не
 * додумывает результат по коду ответа.
 */
@RestController
public class PartnerWriteController {

    /** Один и тот же набор действий для двух справочников — различие в пути. */
    private static final String PATHS = "{kind:suppliers|consignees}";

    private final PartnerWriteService service;

    public PartnerWriteController(PartnerWriteService service) {
        this.service = service;
    }

    /* ======================= Контрагент ======================= */

    @GetMapping("/api/" + PATHS + "/{id}")
    public Map<String, Object> card(@PathVariable String kind, @PathVariable String id) {
        return service.card(PartnerKind.ofPath(kind), id);
    }

    @PostMapping("/api/" + PATHS)
    public Map<String, Object> create(@PathVariable String kind,
                                      @RequestBody Map<String, Object> values) {
        return service.create(PartnerKind.ofPath(kind), values);
    }

    @PutMapping("/api/" + PATHS + "/{id}")
    public Map<String, Object> update(@PathVariable String kind, @PathVariable String id,
                                      @RequestBody Map<String, Object> values) {
        return service.update(PartnerKind.ofPath(kind), id, values);
    }

    /** {"active": false} — в архив, {"active": true} — вернуть в работу. */
    @PostMapping("/api/" + PATHS + "/{id}/archive")
    public Map<String, Object> archive(@PathVariable String kind, @PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        boolean active = body != null && Boolean.TRUE.equals(body.get("active"));
        return service.setActive(PartnerKind.ofPath(kind), id, active);
    }

    @DeleteMapping("/api/" + PATHS + "/{id}")
    public Map<String, Object> delete(@PathVariable String kind, @PathVariable String id) {
        service.delete(PartnerKind.ofPath(kind), id);
        return Map.of("deleted", true);
    }

    /* ======================= Адреса доставки ======================= */

    @GetMapping("/api/consignees/{id}/addresses")
    public List<Map<String, Object>> addresses(@PathVariable String id) {
        return service.addresses(id);
    }

    @PostMapping("/api/consignees/{id}/addresses")
    public List<Map<String, Object>> addAddress(@PathVariable String id,
                                                @RequestBody Map<String, Object> values) {
        return service.addAddress(id, values);
    }

    @PutMapping("/api/addresses/{id}")
    public List<Map<String, Object>> updateAddress(@PathVariable String id,
                                                   @RequestBody Map<String, Object> values) {
        return service.updateAddress(id, values);
    }

    @PostMapping("/api/addresses/{id}/default")
    public List<Map<String, Object>> makeDefault(@PathVariable String id) {
        return service.setDefaultAddress(id);
    }

    @DeleteMapping("/api/addresses/{id}")
    public List<Map<String, Object>> deleteAddress(@PathVariable String id) {
        return service.deleteAddress(id);
    }
}
