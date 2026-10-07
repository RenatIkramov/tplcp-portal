package com.ahlers.portal3pl.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Проверка учётных данных.
 * <p>
 * Выделено в отдельный класс намеренно: если организация решит перейти
 * на корпоративный вход, меняется только этот класс — остальной портал
 * работает с уже установленной сессией и ничего не знает о том,
 * как именно проверялся пароль.
 */
@Service
public class AuthService {

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Проверяет логин и пароль. Любая неудача выглядит одинаково: по тексту
     * ошибки нельзя понять, существует ли пользователь — иначе форма входа
     * превращается в способ перебирать учётные записи.
     */
    @Transactional
    public PortalUser authenticate(String login, String password) {
        List<Map<String, Object>> rows = jdbc
                .sql("SELECT * FROM dbo.auth_user_by_login(:login::text)")
                .param("login", login == null ? "" : login.trim())
                .query()
                .listOfRows();

        if (rows.isEmpty()) {
            throw unauthorized();
        }
        Map<String, Object> row = rows.getFirst();

        OffsetDateTime lockedUntil = (OffsetDateTime) row.get("locked_until");
        if (lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Вход временно заблокирован из-за неудачных попыток. Повторите позже");
        }
        if (!Boolean.TRUE.equals(row.get("is_active"))) {
            // §6.2: заблокированный пользователь не входит никаким путём
            registerAttempt(login, false);
            throw unauthorized();
        }

        String hash = (String) row.get("password_hash");
        if (hash == null || hash.isBlank() || !passwordEncoder.matches(password, hash)) {
            registerAttempt(login, false);
            throw unauthorized();
        }

        registerAttempt(login, true);
        return new PortalUser(
                String.valueOf(row.get("user_id")),
                (String) row.get("login"),
                (String) row.get("full_name"),
                (String) row.get("role_code"),
                true,
                Boolean.TRUE.equals(row.get("must_change_password")));
    }

    /** Поклажедатели, доступные пользователю (§6.4). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableDepositors(String login) {
        return jdbc.sql("""
                        SELECT depositor_id::text AS "depositorId", code, org_name AS "orgName"
                        FROM dbo.depositors_for_user(:login::text)
                        """)
                .param("login", login)
                .query()
                .listOfRows();
    }

    @Transactional(readOnly = true)
    public boolean mayAccess(String login, String depositorId) {
        return Boolean.TRUE.equals(jdbc
                .sql("SELECT dbo.user_may_access_depositor(:login::text, :id::uuid)")
                .param("login", login)
                .param("id", depositorId)
                .query(Boolean.class)
                .single());
    }

    private void registerAttempt(String login, boolean success) {
        jdbc.sql("SELECT dbo.auth_register_attempt(:login::text, :success)")
                .param("login", login == null ? "" : login.trim())
                .param("success", success)
                .query()
                .listOfRows();
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    }
}
