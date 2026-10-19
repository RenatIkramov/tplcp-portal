package com.ahlers.portal3pl.common;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Отказы портала одной строкой: {@code throw Fail.notFound("error.sku.not_found")}.
 *
 * <p>Подстановки передаются парами «имя, значение»:
 *
 * <pre>
 *   throw Fail.conflict("error.user.login_taken", "login", login);
 * </pre>
 *
 * <p>В тексте они стоят в фигурных скобках: {@code Логин «{login}» уже
 * занят}. Имена, а не номера, намеренно: порядок слов
 * в разных языках разный, и {@code {0}} в переводе легко поставить не туда,
 * а {@code {name}} переводчик видит и понимает.
 *
 * <p>Почему статические методы, а не конструктор исключения: место отказа
 * должно читаться одной строкой и одинаково во всём портале. Сравните
 * с прежним {@code new ResponseStatusException(HttpStatus.CONFLICT, "…")} —
 * половина строки уходила на обвязку.
 */
public final class Fail {

    private Fail() {
    }

    /** Данные запроса неверны: не заполнено, не то число, не тот формат. */
    public static PortalException badRequest(String key, Object... params) {
        return new PortalException(HttpStatus.BAD_REQUEST, key, map(params));
    }

    /** Записи нет — или она есть, но этому пользователю не видна. */
    public static PortalException notFound(String key, Object... params) {
        return new PortalException(HttpStatus.NOT_FOUND, key, map(params));
    }

    /** Данные верны, но действие сейчас невозможно: занято, используется, поздно. */
    public static PortalException conflict(String key, Object... params) {
        return new PortalException(HttpStatus.CONFLICT, key, map(params));
    }

    /** Вошедший есть, но этого ему нельзя. */
    public static PortalException forbidden(String key, Object... params) {
        return new PortalException(HttpStatus.FORBIDDEN, key, map(params));
    }

    /** Войти нужно заново. */
    public static PortalException unauthorized(String key, Object... params) {
        return new PortalException(HttpStatus.UNAUTHORIZED, key, map(params));
    }

    /** Слишком часто — перебор пароля. */
    public static PortalException tooManyRequests(String key, Object... params) {
        return new PortalException(HttpStatus.TOO_MANY_REQUESTS, key, map(params));
    }

    /** Данные приняты и разобраны, но документ так отправить нельзя. */
    public static PortalException unprocessable(String key, Object... params) {
        return new PortalException(HttpStatus.UNPROCESSABLE_CONTENT, key, map(params));
    }

    /** Сломался сам портал. Человеку — общий текст, подробности в журнал. */
    public static PortalException internal(String key, Object... params) {
        return new PortalException(HttpStatus.INTERNAL_SERVER_ERROR, key, map(params));
    }

    /**
     * Пары «имя, значение» в карту.
     *
     * <p>Нечётное число аргументов — ошибка программиста, и она должна
     * вскрыться сразу, а не превратиться в текст с пропущенной
     * подстановкой у клиента.
     */
    private static Map<String, Object> map(Object... params) {
        if (params == null || params.length == 0) {
            return Map.of();
        }
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Подстановки задаются парами «имя, значение», получено: " + params.length);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            out.put(String.valueOf(params[i]), params[i + 1]);
        }
        return out;
    }
}
