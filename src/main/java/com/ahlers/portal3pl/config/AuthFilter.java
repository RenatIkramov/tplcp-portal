package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.RememberMeService;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Пропускает к данным только вошедших пользователей и переносит в контекст
 * запроса то, что лежит в сессии.
 * <p>
 * Открыты без входа: страница портала и статика, вход, выход и тексты
 * интерфейса (на них написана сама форма входа). Всё остальное
 * под /api/ требует сессии и отвечает 401, если её нет, — экран по этому коду
 * показывает форму входа.
 */
@Component
public class AuthFilter implements Filter {

    private final SessionUser session;
    private final RememberMeService rememberMe;

    public AuthFilter(SessionUser session, RememberMeService rememberMe) {
        this.session = session;
        this.rememberMe = rememberMe;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = http.getRequestURI();

        try {
            PortalUser user = session.currentUser(http);
            if (user == null) {
                user = restoreRemembered(http, resp);
            }
            if (user != null) {
                DepositorContext.set(user.login(), session.depositorId(http));
            }

            if (requiresLogin(path) && user == null) {
                response(resp, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"message\":\"Требуется вход в портал\"}");
                return;
            }

            // Пароль выдан администратором: до смены пароля пользователь
            // не получает никаких данных — только экран смены пароля.
            if (user != null && user.mustChangePassword() && !allowedBeforePasswordChange(path)) {
                response(resp, HttpServletResponse.SC_FORBIDDEN,
                        "{\"message\":\"Требуется сменить пароль\",\"mustChangePassword\":true}");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            // потоки переиспользуются: забытое значение утекло бы в чужой запрос
            DepositorContext.clear();
        }
    }

    private static void response(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(body);
    }

    /** Что доступно, пока пароль не сменён: сам вход, выход, «кто я» и смена пароля. */
    private static boolean allowedBeforePasswordChange(String path) {
        return !path.startsWith("/api/")
                || path.startsWith("/api/auth/")
                || path.equals("/api/me")
                || path.equals("/api/me/password")
                || path.startsWith("/api/i18n/");
    }

    /**
     * Сеанса нет — пробуем признак «запомнить меня».
     *
     * <p>Только для обращений к API: на каждую картинку и каждый .js
     * ходить в базу незачем, а первым делом экран всё равно спрашивает
     * у портала, кто вошёл.
     *
     * <p>Удачное восстановление заводит обычную серверную сессию и
     * выдаёт НОВЫЙ токен: использованный уже погашен, и украденная
     * cookie второй раз не сработает.
     */
    private PortalUser restoreRemembered(HttpServletRequest http, HttpServletResponse resp) {
        if (!http.getRequestURI().startsWith("/api/")) {
            return null;
        }
        PortalUser user = rememberMe.restore(http, resp);
        if (user == null) {
            return null;
        }
        session.setUser(http, user);
        rememberMe.remember(http, resp, user.userId());
        return user;
    }

    private static boolean requiresLogin(String path) {
        if (!path.startsWith("/api/")) {
            return false;
        }
        // Тексты интерфейса нужны до входа: на них написана сама форма входа.
        // Ничего о данных портала они не раскрывают — это подписи кнопок.
        return !path.equals("/api/auth/login")
                && !path.equals("/api/auth/logout")
                && !path.startsWith("/api/i18n/");
    }
}
