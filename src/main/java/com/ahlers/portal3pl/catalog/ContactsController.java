package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Страница «Контакты»:
 * <pre>
 *   GET /api/contacts   реквизиты оператора, склады, менеджеры
 * </pre>
 *
 * <p>Вход нужен: это не публичная страница сайта, а раздел портала,
 * и состав ответа зависит от того, кто спрашивает.
 */
@RestController
public class ContactsController {

    private final ContactsService contacts;
    private final SessionUser session;

    public ContactsController(ContactsService contacts, SessionUser session) {
        this.contacts = contacts;
        this.session = session;
    }

    @GetMapping("/api/contacts")
    public Map<String, Object> page(HttpServletRequest request) {
        return contacts.page(session.requireUser(request));
    }
}
