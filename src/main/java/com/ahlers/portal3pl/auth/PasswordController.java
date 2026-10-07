package com.ahlers.portal3pl.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Смена пароля пользователем — кнопка «Сменить пароль» в личном кабинете. */
@RestController
public class PasswordController {

    private final PasswordService passwords;
    private final SessionUser session;

    public PasswordController(PasswordService passwords, SessionUser session) {
        this.passwords = passwords;
        this.session = session;
    }

    public record ChangeRequest(String currentPassword, String newPassword) {
    }

    @PostMapping("/api/me/password")
    public Map<String, Object> change(@RequestBody ChangeRequest body, HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        PortalUser updated = passwords.changeOwn(user,
                body.currentPassword(), body.newPassword());
        // в сессии хранится копия записи пользователя — обновляем, иначе
        // портал продолжит требовать смену пароля уже после смены
        session.updateUser(request, updated);
        return Map.of("message", "Пароль изменён");
    }
}
