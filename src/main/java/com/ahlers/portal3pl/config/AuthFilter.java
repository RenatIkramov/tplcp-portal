package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.auth.PortalUser;
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

    public AuthFilter(SessionUser session) {
        this.session = session;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = http.getRequestURI();

        try {
            PortalUser user = session.currentUser(http);
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
