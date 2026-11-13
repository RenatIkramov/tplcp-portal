package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.common.Fail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** С какой неудачи подряд ответ начинает задерживаться (V123). */
    static final int DELAY_FROM = 4;
    /** Задержка не растёт дальше этой. */
    static final long MAX_DELAY_MS = 8_000;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle throttle;
    /**
     * Хэш, с которым сверяется пароль, когда сверять не с чем:
     * несуществующий или заблокированный логин. Без этой сверки такие
     * отказы приходили быстрее (bcrypt медленный по назначению), и по
     * времени ответа узнавалось, есть ли логин (аудит S8).
     */
    private final String dummyHash;

    public AuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder, LoginThrottle throttle) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.dummyHash = passwordEncoder.encode("not-a-password-" + java.util.UUID.randomUUID());
    }

    /** Вход не из сети (проверки, служебные вызовы): без ограничения по адресу. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PortalUser authenticate(String login, String password) {
        return authenticate(login, password, null);
    }

    /**
     * Проверяет логин и пароль. Любая неудача выглядит одинаково — и по
     * тексту, и по времени ответа: нельзя понять, существует ли
     * пользователь, иначе форма входа превращается в способ перебирать
     * учётные записи.
     *
     * <p>Отказ — исключение, но транзакция при этом НЕ откатывается
     * ({@code noRollbackFor}): иначе вместе с ней откатывались бы счётчик
     * неудач и запись в журнал ({@code LoginAuditTest}).
     *
     * <p>Учётка не запирается (V123, решение заказчика): ответ на неудачу
     * задерживает AuthController по счёту неудач подряд, а с одного адреса
     * больше {@link LoginThrottle#LIMIT} неудач за пять минут — отказ
     * «слишком много попыток».
     *
     * <p>Каждая неудача — в журнал действий с причиной (V117); вход под
     * несуществующим логином — не чаще раза в минуту на пару «адрес + логин».
     *
     * @param address адрес отправителя; {@code null} — вход не из сети
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PortalUser authenticate(String login, String password, String address) {
        if (throttle.blocked(address)) {
            throw Fail.tooManyRequests("error.auth.too_many_attempts");
        }
        List<Map<String, Object>> rows = jdbc
                .sql("SELECT * FROM dbo.auth_user_by_login(:login::text)")
                .param("login", login == null ? "" : login.trim())
                .query()
                .listOfRows();

        if (rows.isEmpty()) {
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            throttle.failed(address);
            if (address == null || throttle.journalUnknown(address, login)) {
                loginFailed(login, "unknown");
            }
            throw unauthorized();
        }
        Map<String, Object> row = rows.getFirst();

        if (!Boolean.TRUE.equals(row.get("is_active"))) {
            // §6.2: заблокированный пользователь не входит никаким путём.
            // Попытки не считаем: задерживать вход в закрытую учётку незачем.
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            throttle.failed(address);
            loginFailed(login, "inactive");
            throw unauthorized();
        }

        String hash = (String) row.get("password_hash");
        if (hash == null || hash.isBlank() || !passwordEncoder.matches(password, hash)) {
            registerAttempt(login, false);
            throttle.failed(address);
            loginFailed(login, "password");
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

    /**
     * На сколько задержать ответ на неудачный вход: с {@link #DELAY_FROM}-й
     * неудачи подряд — 1, 2, 4, 8 секунд, дальше 8. Неудачи старше 15 минут
     * не в счёт. Несуществующий логин — без задержки: его сдерживает
     * ограничение по адресу.
     */
    @Transactional(readOnly = true)
    public long failureDelayMs(String login) {
        Integer streak = jdbc.sql("""
                        SELECT failed_login_count FROM dbo.app_user
                        WHERE lower(login) = lower(:login)
                          AND last_failed_at > now() - interval '15 minutes'
                        """)
                .param("login", login == null ? "" : login.trim())
                .query(Integer.class)
                .optional()
                .orElse(0);
        return delayFor(streak);
    }

    public static long delayFor(int streak) {
        if (streak < DELAY_FROM) {
            return 0;
        }
        int step = Math.min(streak - DELAY_FROM, 3);
        return Math.min(1000L << step, MAX_DELAY_MS);
    }

    /**
     * Поклажедатели, доступные пользователю (§6.4), — с номером договора:
     * его показывает карточка документа (V108).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableDepositors(String login) {
        return jdbc.sql("""
                        SELECT depositor_id::text AS "depositorId", code, org_name AS "orgName",
                               contract_number AS "contractNumber"
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

    /** Неудачный вход — в журнал действий: набранный логин и причина (V117). */
    private void loginFailed(String login, String reason) {
        jdbc.sql("SELECT dbo.audit_login_failed(:login::text, :reason::text)")
                .param("login", login == null ? "" : login.trim())
                .param("reason", reason)
                .query()
                .listOfRows();
    }

    private static ResponseStatusException unauthorized() {
        return Fail.unauthorized("error.auth.bad_credentials");
    }
}
