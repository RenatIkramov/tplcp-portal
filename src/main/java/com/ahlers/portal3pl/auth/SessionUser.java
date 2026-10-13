package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.common.Fail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * Что портал помнит о текущем сеансе: кто вошёл и с каким поклажедателем
 * работает. Хранится в серверной сессии (§4 ТЗ) — в браузер уходит только
 * идентификатор сессии, никаких прав и признаков доступа.
 */
@Component
public class SessionUser {

    private static final String USER = "portal.user";
    private static final String DEPOSITOR = "portal.depositorId";

    public void setUser(HttpServletRequest request, PortalUser user) {
        // Новая сессия на каждый вход: старый идентификатор перестаёт работать,
        // поэтому перехваченный до входа cookie не даёт войти в чужой сеанс.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER, user);
    }

    /** Заменяет запись пользователя в сессии — например, после смены пароля. */
    public void updateUser(HttpServletRequest request, PortalUser user) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(USER, user);
        }
    }

    public PortalUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (PortalUser) session.getAttribute(USER);
    }

    public PortalUser requireUser(HttpServletRequest request) {
        PortalUser user = currentUser(request);
        if (user == null) {
            throw Fail.unauthorized("error.auth.sign_in_required");
        }
        return user;
    }

    public void selectDepositor(HttpServletRequest request, String depositorId) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(DEPOSITOR, depositorId);
        }
    }

    public String depositorId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(DEPOSITOR);
    }

    public void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
