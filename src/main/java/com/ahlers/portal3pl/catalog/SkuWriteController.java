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
 * Ведение товаров и упаковок из портала.
 *
 * <p>Чтение списка товаров осталось в {@link CatalogController}
 * (GET /api/sku) — здесь только карточка и изменения:
 * <pre>
 *   GET    /api/sku/dictionaries        списки для выпадающих списков карточки
 *   GET    /api/sku/{id}                карточка товара вместе с упаковками
 *   POST   /api/sku                     создать товар
 *   PUT    /api/sku/{id}                сохранить карточку
 *   POST   /api/sku/{id}/archive        в архив / вернуть из архива
 *   DELETE /api/sku/{id}                удалить (только неиспользуемый)
 *
 *   GET    /api/sku/{id}/packagings     упаковки товара
 *   POST   /api/sku/{id}/packagings     добавить упаковку
 *   PUT    /api/packagings/{id}         сохранить упаковку
 *   POST   /api/packagings/{id}/default сделать единицей заказа
 *   DELETE /api/packagings/{id}         удалить упаковку
 * </pre>
 *
 * <p>Изменения упаковок возвращают весь список упаковок товара, а изменения
 * товара — всю карточку: экран перерисовывает её целиком и не додумывает
 * результат по коду ответа. Признак «по умолчанию» и так считается в базе,
 * собирать его в браузере значило бы держать вторую копию правила.
 */
@RestController
public class SkuWriteController {

    private final SkuWriteService service;

    public SkuWriteController(SkuWriteService service) {
        this.service = service;
    }

    /* ======================= Товар ======================= */

    // Путь объявлен до /api/sku/{id} намеренно читаемым словом: совпадения
    // с идентификатором не будет, uuid так не выглядит.
    @GetMapping("/api/sku/dictionaries")
    public Map<String, List<Map<String, Object>>> dictionaries() {
        return service.dictionaries();
    }

    @GetMapping("/api/sku/{id}")
    public Map<String, Object> card(@PathVariable String id) {
        return service.card(id);
    }

    @PostMapping("/api/sku")
    public Map<String, Object> create(@RequestBody Map<String, Object> values) {
        return service.createSku(values);
    }

    @PutMapping("/api/sku/{id}")
    public Map<String, Object> update(@PathVariable String id,
                                      @RequestBody Map<String, Object> values) {
        return service.updateSku(id, values);
    }

    /** {"active": false} — в архив, {"active": true} — вернуть в работу. */
    @PostMapping("/api/sku/{id}/archive")
    public Map<String, Object> archive(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        boolean active = body != null && Boolean.TRUE.equals(body.get("active"));
        return service.setSkuActive(id, active);
    }

    @DeleteMapping("/api/sku/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        service.deleteSku(id);
        return Map.of("deleted", true);
    }

    /* ======================= Упаковки ======================= */

    @GetMapping("/api/sku/{id}/packagings")
    public List<Map<String, Object>> packagings(@PathVariable String id) {
        return service.packagings(id);
    }

    @PostMapping("/api/sku/{id}/packagings")
    public List<Map<String, Object>> addPackaging(@PathVariable String id,
                                                  @RequestBody Map<String, Object> values) {
        return service.createPackaging(id, values);
    }

    @PutMapping("/api/packagings/{id}")
    public List<Map<String, Object>> updatePackaging(@PathVariable String id,
                                                     @RequestBody Map<String, Object> values) {
        return service.updatePackaging(id, values);
    }

    @PostMapping("/api/packagings/{id}/default")
    public List<Map<String, Object>> makeDefault(@PathVariable String id) {
        return service.setDefaultPackaging(id);
    }

    @DeleteMapping("/api/packagings/{id}")
    public List<Map<String, Object>> deletePackaging(@PathVariable String id) {
        return service.deletePackaging(id);
    }
}
