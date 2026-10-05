package com.ahlers.portal3pl.config;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Кто выполняет действие. До появления аутентификации (этап 5) логин приходит
 * в заголовке X-User-Login; дальше здесь же будет чтение из сессии,
 * и остальной код не изменится.
 *
 * Для записи документов пользователь обязателен: поля «кто создал» и
 * «кто изменил» требует §11 спецификации, и ставить их наугад нельзя.
 */
@Component
public class CurrentUser {

    private final JdbcClient jdbc;

    public CurrentUser(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** user_id текущего пользователя. Ошибка, если логин не передан или пользователь заблокирован. */
    public String requireUserId() {
        String login = DepositorContext.userLogin();
        if (login == null || login.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не указан пользователь: передайте заголовок X-User-Login");
        }
        return jdbc.sql("SELECT user_id::text FROM dbo.app_user WHERE login = :login AND is_active")
                .param("login", login)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Пользователь не найден или заблокирован"));
    }

    /** Разрешено ли пользователю править транспортный раздел (решение Р5). */
    public boolean mayEditTransport() {
        String login = DepositorContext.userLogin();
        if (login == null || login.isBlank()) {
            return false;
        }
        Boolean allowed = jdbc.sql("""
                        SELECT COALESCE(s.can_edit_transport, true)
                        FROM dbo.app_user u
                        LEFT JOIN dbo.user_settings s ON s.user_id = u.user_id
                        WHERE u.login = :login AND u.is_active
                        """)
                .param("login", login)
                .query(Boolean.class)
                .optional()
                .orElse(false);
        return Boolean.TRUE.equals(allowed);
    }
}
