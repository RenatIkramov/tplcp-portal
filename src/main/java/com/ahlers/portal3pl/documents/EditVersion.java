package com.ahlers.portal3pl.documents;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Какую версию черновика правит экран (аудит 11.11.2026, A9).
 *
 * <p>ЧТО БЫЛО. Двое открыли один черновик — сохранялся последний, и его
 * состав молча заменял состав первого: {@code replaceLines} пишет список
 * целиком.
 *
 * <p>КАК ТЕПЕРЬ. Карточка документа отдаёт {@code version} — момент
 * последнего изменения документа в микросекундах. Экран присылает его в
 * заголовке {@code If-Match} с каждой записью черновика; запись блокирует
 * строку шапки и сверяет версию. Не совпала — 409 «документ изменил
 * другой пользователь», ничего не записано.
 *
 * <p>Заголовок, а не параметр метода: проверка нужна запросам экрана,
 * а сервисы зовут ещё загрузка Excel и тесты — им версия ни к чему.
 * Нет заголовка — нет сверки, как раньше.
 */
final class EditVersion {

    static final String HEADER = "If-Match";

    private EditVersion() {
    }

    /** Версия, которую прислал экран, или {@code null} — сверять не с чем. */
    static String expected() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        // по правилам HTTP значение в кавычках: "123"; экран может прислать и без них
        return value.trim().replace("\"", "");
    }
}
