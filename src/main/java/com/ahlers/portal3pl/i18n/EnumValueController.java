package com.ahlers.portal3pl.i18n;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Группа администрирования «Перечисления» (V95).
 * <pre>
 *   GET    /api/admin/enum-values                 все перечисления, значения, переводы
 *   POST   /api/admin/enum-values/{enum}          новое значение с переводами
 *   PUT    /api/admin/enum-values/{enum}/{id}     изменить значение и переводы
 *   DELETE /api/admin/enum-values/{enum}/{id}     удалить, если на него ничего не ссылается
 * </pre>
 * Каждый ответ — весь список заново: окно закрылось, и экран должен
 * показать то, что записала база, а не то, что ушло в запросе.
 */
@RestController
public class EnumValueController {

    private final EnumValueService values;
    private final SessionUser session;

    public EnumValueController(EnumValueService values, SessionUser session) {
        this.values = values;
        this.session = session;
    }

    @GetMapping("/api/admin/enum-values")
    public Map<String, Object> list(HttpServletRequest request) {
        return values.list(session.requireUser(request));
    }

    @PostMapping("/api/admin/enum-values/{enumKey}")
    public Map<String, Object> create(@PathVariable String enumKey,
                                      @RequestBody EnumValueService.EnumValue body,
                                      HttpServletRequest request) {
        return values.save(session.requireUser(request), enumKey, null, body);
    }

    @PutMapping("/api/admin/enum-values/{enumKey}/{valueId}")
    public Map<String, Object> update(@PathVariable String enumKey, @PathVariable String valueId,
                                      @RequestBody EnumValueService.EnumValue body,
                                      HttpServletRequest request) {
        return values.save(session.requireUser(request), enumKey, valueId, body);
    }

    @DeleteMapping("/api/admin/enum-values/{enumKey}/{valueId}")
    public Map<String, Object> delete(@PathVariable String enumKey, @PathVariable String valueId,
                                      HttpServletRequest request) {
        return values.delete(session.requireUser(request), enumKey, valueId);
    }
}
