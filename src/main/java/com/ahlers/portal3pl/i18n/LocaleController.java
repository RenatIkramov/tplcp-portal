package com.ahlers.portal3pl.i18n;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Языки и тексты интерфейса:
 * <pre>
 *   GET  /api/i18n/locales        языки портала            (без входа)
 *   GET  /api/i18n/texts/{code}   подписи на этом языке    (без входа)
 *   GET  /api/admin/locales       все языки, включая выключенные (администратор)
 *   PUT  /api/admin/locales/{code} показывать язык / сделать основным
 *   POST /api/me/locale           сменить свой язык
 *   GET  /api/admin/ui-texts      таблица переводов        (администратор)
 *   PUT  /api/admin/ui-texts      сохранить один перевод   (администратор)
 * </pre>
 *
 * <p>Первые два адреса открыты без входа намеренно: на этих подписях
 * написана сама форма входа. Данных портала они не раскрывают.
 */
@RestController
public class LocaleController {

    private final LocaleService locales;
    private final SessionUser session;

    public LocaleController(LocaleService locales, SessionUser session) {
        this.locales = locales;
        this.session = session;
    }

    @GetMapping("/api/i18n/locales")
    public List<Map<String, Object>> locales() {
        return locales.locales();
    }

    @GetMapping("/api/i18n/texts/{code}")
    public Map<String, Object> texts(@PathVariable String code) {
        return locales.texts(code);
    }

    public record Locale(String code) {
    }

    @PostMapping("/api/me/locale")
    public Map<String, Object> setLocale(@RequestBody Locale body, HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        return Map.of("code", locales.setLocaleOf(user, body.code()));
    }

    /* ---- справочник языков ---- */

    @GetMapping("/api/admin/locales")
    public List<Map<String, Object>> allLocales(HttpServletRequest request) {
        return locales.allLocales(session.requireUser(request));
    }

    /** Что меняем, определяет поле: признак показа или «сделать основным». */
    public record LocaleSettings(Boolean shown, Boolean isDefault) {
    }

    @PutMapping("/api/admin/locales/{code}")
    public List<Map<String, Object>> saveLocale(@PathVariable String code,
                                               @RequestBody LocaleSettings body,
                                               HttpServletRequest request) {
        PortalUser actor = session.requireUser(request);
        if (Boolean.TRUE.equals(body.isDefault())) {
            return locales.setDefaultLocale(actor, code);
        }
        if (body.shown() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нечего менять: укажите «показывать» или «сделать основным»");
        }
        return locales.setLocaleShown(actor, code, body.shown());
    }

    /* ---- ведение переводов ---- */

    @GetMapping("/api/admin/ui-texts")
    public List<Map<String, Object>> matrix(HttpServletRequest request) {
        return locales.matrix(session.requireUser(request));
    }

    public record TextEdit(String textKey, String locale, String value) {
    }

    @PutMapping("/api/admin/ui-texts")
    public List<Map<String, Object>> save(@RequestBody TextEdit body, HttpServletRequest request) {
        return locales.save(session.requireUser(request), body.textKey(), body.locale(), body.value());
    }
}
