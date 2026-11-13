package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.common.Fail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Что портал помнит о текущем сеансе: кто вошёл и с каким поклажедателем
 * работает. Хранится в серверной сессии (§4 ТЗ) — в браузер уходит только
 * идентификатор сессии, никаких прав и признаков доступа.
 */
@Component
public class SessionUser {

    private static final String USER = "portal.user";
    private static final String DEPOSITOR = "portal.depositorId";
    /** Когда открыт сеанс — по часам базы: с ними сверяется sessions_valid_after (V121). */
    private static final String SIGNED_IN_AT = "portal.signedInAt";

    private final JdbcClient jdbc;

    public SessionUser(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void setUser(HttpServletRequest request, PortalUser user) {
        // Новая сессия на каждый вход: старый идентификатор перестаёт работать,
        // поэтому перехваченный до входа cookie не даёт войти в чужой сеанс.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER, user);
        session.setAttribute(SIGNED_IN_AT, databaseNow());
    }

    /**
     * Сеанс остаётся в силе после смены СВОЕГО пароля: отметка
     * «прежние сеансы недействительны» гасит остальные, а этот
     * открывается заново — по часам базы, позже отметки.
     */
    public void renew(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SIGNED_IN_AT, databaseNow());
        }
    }

    /**
     * Действует ли ещё сеанс (аудит 11.11.2026, S4): учётка активна,
     * сеанс не старше отметки «прежние недействительны», выбранный
     * поклажедатель всё ещё доступен. Ответ базы: ok, inactive, stale,
     * depositor.
     */
    public String verdict(HttpServletRequest request, PortalUser user) {
        HttpSession session = request.getSession(false);
        Object since = session == null ? null : session.getAttribute(SIGNED_IN_AT);
        return jdbc.sql("SELECT dbo.auth_session_check(:login::text, :dep::uuid, :since::timestamptz)")
                .param("login", user.login())
                .param("dep", depositorId(request))
                .param("since", since instanceof OffsetDateTime t ? t : null)
                .query(String.class)
                .single();
    }

    private OffsetDateTime databaseNow() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single();
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
