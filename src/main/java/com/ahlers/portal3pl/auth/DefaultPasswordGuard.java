package com.ahlers.portal3pl.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Первый пароль администратора не доживает до рабочего портала (аудит
 * 11.11.2026, S9).
 *
 * <p>ЧТО БЫЛО. V18 создаёт логин {@code admin} с паролем из самой миграции —
 * он лежит в исходниках и в этой памятке. Флаг «сменить при входе» стоит,
 * но сменит тот, кто войдёт первым: на только что поднятом рабочем портале
 * это может быть и не администратор.
 *
 * <p>КАК ТЕПЕРЬ. При запуске портал проверяет, не прежний ли пароль у
 * действующего {@code admin}:
 * <ul>
 *   <li>не прежний — ничего не делает;</li>
 *   <li>прежний, профиль не {@code prod} (машина разработчика, проверки) —
 *       пишет предупреждение в журнал и работает;</li>
 *   <li>прежний, профиль {@code prod}, задан {@code portal.admin.initial-password}
 *       (переменная окружения {@code PORTAL_ADMIN_INITIAL_PASSWORD}) — ставит
 *       его вместо прежнего; «сменить при входе» остаётся;</li>
 *   <li>прежний, профиль {@code prod}, нового не задано — портал не
 *       запускается и пишет, что сделать.</li>
 * </ul>
 * Пароль из переменной нужен только на первом запуске: дальше пароль уже не
 * прежний, и переменную можно убрать.
 */
@Component
public class DefaultPasswordGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultPasswordGuard.class);

    /** Пароль из V18 — его и ищем. */
    static final String SHIPPED_PASSWORD = "Nachalo-2026!";

    /** Не короче выдаваемого порталом (PasswordService.generate). */
    static final int MIN_INITIAL_LENGTH = 12;

    enum Verdict { FINE, WARN, REPLACE, REFUSE }

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final Environment environment;
    private final String initialPassword;

    public DefaultPasswordGuard(JdbcClient jdbc, PasswordEncoder encoder, Environment environment,
                                @Value("${portal.admin.initial-password:}") String initialPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.environment = environment;
        this.initialPassword = initialPassword;
    }

    /** Решение без базы и Spring — его проверяет DefaultPasswordGuardTest. */
    static Verdict decide(boolean prod, boolean shippedPassword, String initial) {
        if (!shippedPassword) {
            return Verdict.FINE;
        }
        if (!prod) {
            return Verdict.WARN;
        }
        return initial != null && initial.length() >= MIN_INITIAL_LENGTH ? Verdict.REPLACE : Verdict.REFUSE;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        // Хэш — функцией: прочитать password_hash напрямую портал не может (V133).
        Optional<String> adminId = jdbc.sql("SELECT user_id::text FROM dbo.app_user"
                        + " WHERE lower(login) = 'admin' AND is_active")
                .query(String.class).optional();
        Optional<String> hash = adminId.map(id -> jdbc.sql("SELECT dbo.auth_password_hash(:id::uuid)")
                .param("id", id).query(String.class).single());
        boolean shipped = hash.isPresent() && encoder.matches(SHIPPED_PASSWORD, hash.get());
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        switch (decide(prod, shipped, initialPassword)) {
            case FINE -> { }
            case WARN -> log.warn("У логина admin прежний пароль из V18. На рабочем портале"
                    + " (профиль prod) он не запустится, пока не задан PORTAL_ADMIN_INITIAL_PASSWORD.");
            case REPLACE -> {
                jdbc.sql("SELECT dbo.auth_set_password(:id::uuid, :h::text, true)")
                        .param("id", adminId.orElseThrow())
                        .param("h", encoder.encode(initialPassword)).query().listOfRows();
                log.warn("Пароль admin заменён на заданный в PORTAL_ADMIN_INITIAL_PASSWORD;"
                        + " при входе портал попросит свой. Переменную можно убрать.");
            }
            case REFUSE -> throw new IllegalStateException("У логина admin прежний пароль из V18,"
                    + " а портал запущен как рабочий (профиль prod). Задайте переменную окружения"
                    + " PORTAL_ADMIN_INITIAL_PASSWORD (не короче " + MIN_INITIAL_LENGTH
                    + " знаков) и запустите снова.");
        }
    }
}
