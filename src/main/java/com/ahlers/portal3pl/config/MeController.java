package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Кто сейчас работает и что ему разрешено.
 * <p>
 * Экрану это нужно, чтобы не предлагать действий, которые сервер всё равно
 * запретит: скрыть вкладку, убрать кнопку создания. Сами запреты остаются
 * на сервере — здесь только подсказка интерфейсу.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentUser currentUser;
    private final SessionUser session;
    private final ProfileService profiles;

    public MeController(CurrentUser currentUser, SessionUser session, ProfileService profiles) {
        this.currentUser = currentUser;
        this.session = session;
        this.profiles = profiles;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> me(HttpServletRequest request) {
        PortalUser user = session.requireUser(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("login", user.login());
        response.put("fullName", user.fullName());
        response.put("roleCode", user.roleCode());
        response.put("mustChangePassword", user.mustChangePassword());
        response.put("depositorId", session.depositorId(request));
        response.putAll(currentUser.settings());
        return response;
    }

    /** Личный кабинет: роль, зона доступа, организация, менеджеры (ProfileService). */
    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        return profiles.profile(session.requireUser(request));
    }
}
