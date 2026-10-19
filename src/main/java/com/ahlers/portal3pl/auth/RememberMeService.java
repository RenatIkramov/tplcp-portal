package com.ahlers.portal3pl.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * «Запомнить меня»: вход без пароля, пока человек заходит хотя бы раз
 * в {@link #DAYS} дней.
 *
 * <p>Сеанс портала живёт в памяти Tomcat, поэтому перезапуск приложения
 * выбрасывал всех. Здесь — отдельный долгий признак: случайный токен
 * в cookie и его отпечаток в базе.
 *
 * <p>Что сделано для того, чтобы долгий вход не стал дырой:
 * <ul>
 *   <li><b>в базе только отпечаток</b> (SHA-256). По содержимому базы
 *       войти нельзя — утечка копии не даёт доступа;</li>
 *   <li><b>токен меняется при каждом использовании</b>. Украденной
 *       cookie воспользуется только тот, кто успел первым; у второго
 *       токена уже нет, и портал спросит пароль;</li>
 *   <li><b>срок скользящий</b> — каждое использование продлевает его
 *       на {@link #DAYS} дней от текущего момента, как и просил заказчик:
 *       «15 дней не заходил — пароль снова»;</li>
 *   <li><b>cookie HttpOnly</b>: её не прочитать из JavaScript, то есть
 *       найденная на странице уязвимость не уносит долгий вход;</li>
 *   <li><b>смена пароля и блокировка гасят все токены</b> человека.
 *       Иначе смена пароля не отбирала бы доступ — а это главное,
 *       ради чего её меняют.</li>
 * </ul>
 */
@Service
public class RememberMeService {

    /** Сколько дней портал помнит человека без пароля. */
    public static final int DAYS = 15;

    /** Имя cookie. Префикса __Host- нет намеренно: он требует https. */
    private static final String COOKIE = "portal_remember";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;

    public RememberMeService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /* ======================= Выдача ======================= */

    /**
     * Выдать токен и положить его в cookie.
     *
     * <p>Вызывается при входе с отмеченной галочкой и при каждом
     * использовании токена — старый к этому моменту уже погашен.
     */
    @Transactional
    public void remember(HttpServletRequest request, HttpServletResponse response,
                         String userId) {
        jdbc.sql("SELECT dbo.auth_remember_cleanup()").query().listOfRows();

        String token = newToken();
        jdbc.sql("""
                        INSERT INTO dbo.auth_remember_token
                               (user_id, token_hash, expires_at, user_agent)
                        VALUES (:user::uuid, :hash::varchar,
                                now() + make_interval(days => :days), :agent::varchar)
                        """)
                .param("user", userId)
                .param("hash", fingerprint(token))
                .param("days", DAYS)
                .param("agent", agent(request))
                .update();

        response.addCookie(cookie(request, token, (int) Duration.ofDays(DAYS).toSeconds()));
    }

    /* ======================= Использование ======================= */

    /**
     * Кто стоит за cookie — или {@code null}, если признака нет, он
     * просрочен, погашен или учётная запись заблокирована.
     *
     * <p>Токен гасится здесь же: вызывающий обязан выдать новый
     * (см. {@link #remember}). Так одноразовость получается сама собой,
     * без отдельного признака «использован».
     */
    @Transactional
    public PortalUser restore(HttpServletRequest request, HttpServletResponse response) {
        String token = fromCookie(request);
        if (token == null) {
            return null;
        }
        Map<String, Object> row = jdbc.sql("""
                        DELETE FROM dbo.auth_remember_token t
                         USING dbo.app_user u, dbo.enum_user_role r
                         WHERE t.token_hash = :hash::varchar
                           AND u.user_id = t.user_id
                           AND r.id = u.role_id
                           AND t.expires_at > now()
                           AND u.is_active
                     RETURNING u.user_id::text AS user_id, u.login, u.full_name,
                               r.code AS role_code, u.must_change_password
                     """)
                .param("hash", fingerprint(token))
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(null);

        if (row == null) {
            // Признак недействителен — убираем cookie, иначе браузер
            // будет присылать мусор при каждом запросе ещё 15 дней.
            forgetCookie(request, response);
            return null;
        }
        return new PortalUser(
                String.valueOf(row.get("user_id")),
                String.valueOf(row.get("login")),
                String.valueOf(row.get("full_name")),
                String.valueOf(row.get("role_code")),
                true,
                Boolean.TRUE.equals(row.get("must_change_password")));
    }

    /* ======================= Отмена ======================= */

    /** Выход: гасим именно этот токен, остальные устройства не трогаем. */
    @Transactional
    public void forget(HttpServletRequest request, HttpServletResponse response) {
        String token = fromCookie(request);
        if (token != null) {
            jdbc.sql("DELETE FROM dbo.auth_remember_token WHERE token_hash = :hash::varchar")
                    .param("hash", fingerprint(token))
                    .update();
        }
        forgetCookie(request, response);
    }

    /**
     * Погасить все токены человека: смена пароля, блокировка.
     *
     * <p>Без этого смена пароля не отбирала бы доступ у того, кто уже
     * вошёл «навсегда» — а именно для этого пароль чаще всего и меняют.
     */
    @Transactional
    public void forgetAll(String userId) {
        jdbc.sql("DELETE FROM dbo.auth_remember_token WHERE user_id = :user::uuid")
                .param("user", userId)
                .update();
    }

    /* ======================= Вспомогательное ======================= */

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Отпечаток токена: в базе храним только его. */
    private static String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 обязателен для любой Java — сюда не попасть
            throw new IllegalStateException("Нет SHA-256", e);
        }
    }

    private static String fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static void forgetCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addCookie(cookie(request, "", 0));
    }

    private static Cookie cookie(HttpServletRequest request, String value, int maxAgeSeconds) {
        Cookie c = new Cookie(COOKIE, value);
        c.setPath("/");
        // Недоступна из JavaScript: уязвимость на странице не уносит
        // долгий вход вместе с остальным.
        c.setHttpOnly(true);
        // Secure только поверх https: на машине разработчика портал
        // открыт по http, и безусловный Secure выключил бы «запомнить
        // меня» там совсем — причём молча.
        c.setSecure(request.isSecure());
        c.setMaxAge(maxAgeSeconds);
        // Lax, а не Strict: вход по ссылке из письма — обычное дело,
        // а на межсайтовые POST признак всё равно не поедет.
        c.setAttribute("SameSite", "Lax");
        return c;
    }

    private static String agent(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent == null) {
            return null;
        }
        return agent.length() > 255 ? agent.substring(0, 255) : agent;
    }
}
