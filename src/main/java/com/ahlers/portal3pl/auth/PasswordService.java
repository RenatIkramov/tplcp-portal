package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.common.Fail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Смена пароля — своим пользователем и администратором.
 * <p>
 * Два пути сознательно разные. Свой пароль меняется только с вводом текущего:
 * иначе чужая незакрытая сессия в браузере означала бы захват учётной записи.
 * Администратор текущего пароля не знает и знать не должен — он выдаёт
 * временный, и портал требует сменить его при первом входе.
 */
@Service
public class PasswordService {

    /** Минимальная длина. Перебор дополнительно сдерживает блокировка после пяти попыток. */
    private static final int MIN_LENGTH = 8;

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final RememberMeService rememberMe;

    public PasswordService(JdbcClient jdbc, PasswordEncoder encoder,
                           RememberMeService rememberMe) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.rememberMe = rememberMe;
    }

    /** Пользователь меняет свой пароль. Возвращает его же, но уже без флага «сменить». */
    @Transactional
    public PortalUser changeOwn(PortalUser user, String currentPassword, String newPassword) {
        long startedAt = System.currentTimeMillis();
        String hash = passwordHash(user.userId());
        long readAt = System.currentTimeMillis();
        boolean matched = hash != null && !hash.isBlank() && encoder.matches(currentPassword, hash);
        long verifiedAt = System.currentTimeMillis();
        // Если смена пароля подтормаживает, по этой строке видно, где именно:
        // чтение из базы или расчёт хеша (он медленный по назначению).
        log.info("Смена пароля {}: чтение {} мс, сверка пароля {} мс",
                user.login(), readAt - startedAt, verifiedAt - readAt);
        if (!matched) {
            throw Fail.forbidden("error.password.current_wrong");
        }
        if (encoder.matches(newPassword, hash)) {
            throw Fail.badRequest("error.password.same_as_current");
        }
        validate(newPassword, user.login());
        store(user.userId(), newPassword, false);
        return user.withOwnPassword();
    }

    /**
     * Администратор задаёт пользователю новый пароль. Флаг «сменить при входе»
     * ставится всегда: пароль, который знает кто-то ещё, временный по определению.
     */
    @Transactional
    public void setByAdmin(String userId, String userLogin, String newPassword) {
        validate(newPassword, userLogin);
        store(userId, newPassword, true);
    }

    /**
     * Хеш для ещё не существующего пользователя: проверяет пароль по тем же
     * требованиям и возвращает хеш, ничего не записывая. Нужен при создании
     * пользователя — открытый пароль в базу не попадает.
     */
    public String hashForNewUser(String login, String password) {
        validate(password, login);
        return encoder.encode(password);
    }

    /**
     * Временный пароль из слогов: читается вслух и переносится без ошибок,
     * при этом отвечает требованиям — оба регистра, знак и цифры.
     */
    public static String generate() {
        String consonants = "kmnprst", vowels = "aeiou";
        java.util.Random r = new java.util.Random();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            word.append(consonants.charAt(r.nextInt(consonants.length())))
                 .append(vowels.charAt(r.nextInt(vowels.length())));
        }
        String base = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        return base + "-" + (1000 + r.nextInt(9000));
    }

    private String passwordHash(String userId) {
        return jdbc.sql("SELECT dbo.auth_password_hash(:id::uuid)")
                .param("id", userId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /**
     * Записывает новый пароль и гасит все долгие входы этого человека.
     *
     * <p>Гашение здесь, в одном месте на оба пути (своя смена и выдача
     * администратором), а не у каждого вызывающего: пароль меняют как раз
     * затем, чтобы отобрать доступ, и «запомнить меня» на старом устройстве
     * сводило бы смену пароля на нет.
     */
    private void store(String userId, String password, boolean forceChange) {
        jdbc.sql("SELECT dbo.auth_set_password(:id::uuid, :hash::text, :must)")
                .param("id", userId)
                .param("hash", encoder.encode(password))
                .param("must", forceChange)
                .query()
                .listOfRows();
        rememberMe.forgetAll(userId);
    }

    /**
     * Требования к паролю — ровно те, что портал показывает в окне смены:
     * не короче восьми символов, латинские буквы заглавные и строчные, плюс
     * цифра или знак. Больше условий не ставим: сдерживает перебор не длина
     * правил, а блокировка после пяти неудачных попыток.
     * <p>
     * Кириллица запрещена сознательно: на чужой раскладке такой пароль
     * не ввести, а пользователь увидит только «неверный пароль».
     */
    private void validate(String password, String login) {
        if (password == null || password.isBlank()) {
            throw Fail.badRequest("error.password.required");
        }
        if (password.length() < MIN_LENGTH) {
            throw Fail.badRequest("error.password.too_short",
                    "min", MIN_LENGTH, "length", password.length());
        }
        boolean lower = false, upper = false, digit = false, symbol = false, nonLatin = false;
        for (char c : password.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else if (Character.isLetter(c)) {
                nonLatin = true;          // любая нелатинская буква
            } else {
                symbol = true;
            }
        }
        if (nonLatin) {
            throw Fail.badRequest("error.password.latin_only");
        }
        if (!lower || !upper) {
            throw Fail.badRequest("error.password.needs_both_cases");
        }
        if (!digit && !symbol) {
            throw Fail.badRequest("error.password.needs_digit_or_symbol");
        }
        if (login != null && !login.isBlank()) {
            String base = login.split("@")[0].toLowerCase();
            if (base.length() >= 4 && password.toLowerCase().contains(base)) {
                throw Fail.badRequest("error.password.contains_login", "login", base);
            }
        }
    }

}
