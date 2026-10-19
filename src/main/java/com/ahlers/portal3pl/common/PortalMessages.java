package com.ahlers.portal3pl.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Текст отказа на языке того, кто сделал запрос.
 *
 * <p>Единственное место, где ключ превращается в фразу. Собирать текст
 * на экране мы не стали намеренно: {@code e.message} показывается
 * в десятках мест, в том числе кодом прототипа, который о ключах
 * не знает, — первое же забытое место выдало бы человеку
 * {@code error.sku.not_found} вместо фразы.
 *
 * <p>Тексты лежат там же, где подписи интерфейса, и правятся тем же
 * экраном «Тексты интерфейса»: для переводчика сообщение об ошибке —
 * такой же текст портала, как заголовок кнопки.
 */
@Service
public class PortalMessages {

    private static final Logger log = LoggerFactory.getLogger(PortalMessages.class);

    private final JdbcClient jdbc;

    public PortalMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Фраза по ключу, с подстановками.
     *
     * <p>Своя транзакция ({@code REQUIRES_NEW}): текст читается, когда
     * основная работа уже отменена отказом, и доставать его из
     * откатываемой транзакции нельзя.
     *
     * <p>Если текста нет, возвращаем сам ключ и пишем в журнал. Пустая
     * строка на экране была бы хуже: человек не поймёт, что произошло,
     * а в журнале не останется следа.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String text(String key, Map<String, Object> params, String localeCode) {
        String value;
        try {
            value = jdbc.sql("SELECT value FROM dbo.ui_texts(:code::text) WHERE text_key = :key")
                    .param("code", localeCode == null || localeCode.isBlank() ? "en" : localeCode)
                    .param("key", key)
                    .query(String.class)
                    .optional()
                    .orElse(null);
        } catch (RuntimeException e) {
            // База недоступна ровно в тот момент, когда мы объясняем отказ.
            // Показать ключ лучше, чем вместо отказа отдать 500.
            log.warn("Не удалось прочитать текст отказа {}: {}", key, e.getMessage());
            return key;
        }
        if (value == null || value.isBlank()) {
            log.warn("Нет текста для ключа отказа: {}", key);
            return key;
        }
        return substitute(value, params, localeCode);
    }

    /**
     * Подстановки вида {@code {name}}.
     *
     * <p>Идём по заданным значениям, а не разбираем шаблон: значения
     * приходят из кода портала, а шаблон — из базы, где его правит
     * переводчик. Лишняя скобка в переводе не должна ломать ответ,
     * поэтому неизвестные скобки остаются как есть — это видно
     * на экране и чинится правкой текста.
     */
    private String substitute(String template, Map<String, Object> params, String localeCode) {
        if (params == null || params.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, Object> param : params.entrySet()) {
            out = out.replace("{" + param.getKey() + "}", value(param.getValue(), localeCode));
        }
        return out;
    }

    /**
     * Значение подстановки строкой.
     *
     * <p>{@link MsgRef} — это не значение, а слово портала: разворачиваем
     * его на том же языке. Список таких ссылок собираем через запятую:
     * «поступлениях (3), заказах (1)». Всё остальное — данные, их
     * показываем как есть.
     */
    private String value(Object raw, String localeCode) {
        return switch (raw) {
            case null -> "";
            case MsgRef(String key, Map<String, Object> params) -> text(key, params, localeCode);
            case Iterable<?> list -> joined(list, localeCode);
            default -> String.valueOf(raw);
        };
    }

    /** Список ссылок через запятую: «поступлениях (3), заказах (1)». */
    private String joined(Iterable<?> list, String localeCode) {
        StringBuilder out = new StringBuilder();
        for (Object item : list) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(value(item, localeCode));
        }
        return out.toString();
    }
}
