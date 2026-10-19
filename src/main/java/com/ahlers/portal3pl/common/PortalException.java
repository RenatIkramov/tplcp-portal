package com.ahlers.portal3pl.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Отказ портала: код ответа, ключ текста и подстановки к нему.
 *
 * <p>Раньше отказ нёс готовую русскую строку, и клиент с английским
 * интерфейсом получал русский текст: подписи переводились, а сообщения
 * об ошибках — нет. Теперь отказ несёт КЛЮЧ, а текст собирается в одном
 * месте ({@link PortalMessages}) на языке того, кто сделал запрос.
 *
 * <p>Наследуемся от {@link ResponseStatusException}, а не заводим своё
 * дерево исключений: на нём держится весь остальной портал — коды
 * ответов, обработчик, тесты. Сообщение в {@code reason} кладём равным
 * ключу: если текста в базе не окажется, человек увидит
 * {@code error.sku.not_found} вместо пустоты, и пропуск будет заметен.
 */
public class PortalException extends ResponseStatusException {

    private final transient String key;
    private final transient Map<String, Object> params;

    public PortalException(HttpStatusCode status, String key, Map<String, Object> params) {
        super(status, key);
        this.key = key;
        this.params = params == null ? Map.of() : params;
    }

    public String key() {
        return key;
    }

    public Map<String, Object> params() {
        return params;
    }
}
