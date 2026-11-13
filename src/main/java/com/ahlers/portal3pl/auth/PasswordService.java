package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.common.Fail;
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

    /** Минимальная длина. Перебор дополнительно сдерживают задержка и лимит по адресу (V123). */
    private static final int MIN_LENGTH = 8;

    /**
     * Больше bcrypt не проверяет: хвост длиннее 72 байт он молча
     * отбрасывает, и пароль «совпадал» бы с любым, у кого те же первые
     * 72 байта (аудит S11).
     */
    static final int MAX_BYTES = 72;

    /** Длина выданного пароля (решение заказчика 11.11.2026: 12 знаков подряд). */
    static final int ISSUED_LENGTH = 12;

    /** Знаки выданного пароля: без похожих — 0/O/o, 1/l/I. Его диктуют по телефону. */
    static final String ISSUED_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

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
        String hash = passwordHash(user.userId());
        boolean matched = hash != null && !hash.isBlank() && encoder.matches(currentPassword, hash);
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
     * Временный пароль, который выдаёт администратор: 12 знаков подряд из
     * {@link #ISSUED_ALPHABET} (решение заказчика 11.11.2026).
     *
     * <p>ЧТО БЫЛО (аудит S11). {@code java.util.Random} — предсказуем по
     * времени запуска, — и шаблон «три слога, дефис, четыре цифры»: около
     * 4·10⁸ вариантов. Теперь {@code SecureRandom} и около 7·10²⁰.
     * Пароль всегда отвечает требованиям: обе буквы и цифра есть.
     */
    public static String generate() {
        while (true) {
            StringBuilder out = new StringBuilder(ISSUED_LENGTH);
            for (int i = 0; i < ISSUED_LENGTH; i++) {
                out.append(ISSUED_ALPHABET.charAt(RANDOM.nextInt(ISSUED_ALPHABET.length())));
            }
            String p = out.toString();
            if (p.chars().anyMatch(Character::isUpperCase) && p.chars().anyMatch(Character::isLowerCase)
                    && p.chars().anyMatch(Character::isDigit)) {
                return p;
            }
        }
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
     * правил, а задержка после неудач подряд и лимит по адресу (V123).
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
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw Fail.badRequest("error.password.too_long", "max", MAX_BYTES);
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
