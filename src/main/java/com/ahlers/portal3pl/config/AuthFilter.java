package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.RememberMeService;
import com.ahlers.portal3pl.auth.SessionUser;
import com.ahlers.portal3pl.common.ActorText;
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
 * <p>
 * Фильтр отвечает сам, до контроллеров, поэтому общий обработчик отказов
 * ({@code ApiErrorHandler}) сюда не достаёт: текст по ключу он собирает
 * здесь же, через {@link ActorText}. Язык берётся из учётной записи, если
 * пользователь уже известен (пароль выдан администратором и его надо
 * сменить), иначе — язык портала по умолчанию: спрашивать язык у того,
 * кто ещё не вошёл, не у кого.
 */
@Component
public class AuthFilter implements Filter {

    private final SessionUser session;
    private final RememberMeService rememberMe;
    private final ActorText texts;

    public AuthFilter(SessionUser session, RememberMeService rememberMe, ActorText texts) {
        this.session = session;
        this.rememberMe = rememberMe;
        this.texts = texts;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = apiPath(http);

        try {
            PortalUser user = session.currentUser(http);
            if (user == null) {
                user = restoreRemembered(http, resp);
            }
            // Права и пароль могли измениться после входа (аудит S4):
            // сеанс спрашивает базу на каждом запросе к данным.
            if (user != null && requiresLogin(path) && !"ok".equals(session.verdict(http, user))) {
                session.signOut(http);
                response(resp, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"message\":" + json(texts.of("error.auth.session_ended"))
                                + ",\"key\":\"error.auth.session_ended\"}");
                return;
            }
            if (user != null) {
                DepositorContext.set(user.login(), session.depositorId(http));
            }

            if (requiresLogin(path) && user == null) {
                response(resp, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"message\":" + json(texts.of("error.auth.sign_in_required")) + "}");
                return;
            }

            // Пароль выдан администратором: до смены пароля пользователь
            // не получает никаких данных — только экран смены пароля.
            if (user != null && user.mustChangePassword() && !allowedBeforePasswordChange(path)) {
                response(resp, HttpServletResponse.SC_FORBIDDEN,
                        "{\"message\":" + json(texts.of("error.auth.password_change_required"))
                                + ",\"mustChangePassword\":true}");
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

    /** Строка в JSON: тело здесь собирается руками, без Jackson. */
    private static String json(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
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
        // Только запросы, которым нужен вход. Тексты и языки страница
        // просит при загрузке одновременно с /api/auth/session — тратить
        // на них одноразовый токен значило гасить его у того запроса,
        // который и должен был войти (аудит S13).
        if (!requiresLogin(apiPath(http))) {
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

    /**
     * Адрес запроса в том виде, в каком по нему выбирает обработчик Spring:
     * раскодированный ({@code %61} → {@code a}), без параметров пути
     * ({@code ;x=1}) и без двойных косых.
     *
     * <p>НАЙДЕНО АУДИТОМ (11.11.2026, S2). Прежде здесь был сырой
     * {@code getRequestURI()}: {@code /%61pi/me} и {@code /api;x/me}
     * фильтр считал «не API» и пропускал без входа, а Spring отдавал их
     * тем же контроллерам. Сравнивать надо то же, по чему выбирают.
     */
    static String apiPath(HttpServletRequest http) {
        String servlet = http.getServletPath();
        String info = http.getPathInfo();
        String path = (servlet == null ? "" : servlet) + (info == null ? "" : info);
        return path.isEmpty() ? "/" : path;
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
