package com.ahlers.portal3pl.common;

import com.ahlers.portal3pl.config.DepositorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Текст портала на языке того, кто выполняет запрос.
 *
 * <p>Зачем отдельный класс. {@link PortalMessages} умеет собрать фразу
 * по ключу, но языка не знает — его надо передать. У отказов язык берёт
 * {@code ApiErrorHandler} из запроса, а в глубине работы запроса его под
 * рукой нет: сервис о HTTP не знает и знать не должен. Логин там
 * доступен — его положил в контекст {@code AuthFilter}, — значит, язык
 * можно определить по нему.
 *
 * <p>Нужен в двух случаях, кроме отказов:
 * <ul>
 *   <li>подтверждения («Пароль изменён») — они приходят экрану тем же
 *       ответом сервера, что и отказы, и переводятся так же;</li>
 *   <li>причины, по которым документ нельзя отправить: экран получает
 *       их списком и показывает как есть.</li>
 * </ul>
 *
 * <p>Язык читается своим запросом, а не через {@code LocaleService}:
 * тот ведёт справочник языков и сам опирается на администрирование
 * пользователей, а сообщения нужны и в администрировании — вышло бы
 * кольцо зависимостей. Запрос здесь ровно один и тот же.
 */
@Service
public class ActorText {

    private static final Logger log = LoggerFactory.getLogger(ActorText.class);

    private final PortalMessages messages;
    private final JdbcClient jdbc;

    public ActorText(PortalMessages messages, JdbcClient jdbc) {
        this.messages = messages;
        this.jdbc = jdbc;
    }

    /**
     * Фраза по ключу с подстановками, заданными парами «имя, значение».
     *
     * @see MsgRef#of(String, Object...)
     */
    public String of(String key, Object... params) {
        MsgRef ref = MsgRef.of(key, params);
        return text(ref.key(), ref.params());
    }

    /** То же, когда подстановки уже собраны — например, пришли с отказом. */
    public String text(String key, Map<String, Object> params) {
        return messages.text(key, params, locale());
    }

    /**
     * Язык текущего пользователя, иначе язык портала по умолчанию.
     *
     * <p>Выключенный язык считается невыбранным — так же, как при выдаче
     * подписей интерфейса: иначе человек с отключённым администратором
     * языком получал бы ключи вместо фраз.
     *
     * <p>Ошибку чтения языка проглатываем: из-за выбора языка не должно
     * падать само действие.
     *
     * <p>Своей транзакции здесь нет намеренно: запрос один, читающий,
     * и он должен работать и вне транзакции — из фильтра входа, где
     * транзакции ещё нет вовсе.
     */
    public String locale() {
        try {
            return jdbc.sql("""
                            SELECT COALESCE(
                                     (SELECT l.code
                                        FROM dbo.app_user u
                                        JOIN dbo.dictionary_locale l
                                          ON l.locale_id = u.locale_id AND l.is_active
                                       WHERE u.login = :login::text),
                                     (SELECT code FROM dbo.dictionary_locale WHERE is_default),
                                     'en')
                            """)
                    // пустая строка вместо null: ни одному логину она
                    // не равна — значит, язык портала по умолчанию, —
                    // и драйверу не нужно угадывать тип параметра
                    .param("login", login())
                    .query(String.class)
                    .optional()
                    .orElse("en");
        } catch (RuntimeException e) {
            log.warn("Не удалось определить язык текста: {}", e.getMessage());
            return "en";
        }
    }

    private static String login() {
        String login = DepositorContext.userLogin();
        return login == null ? "" : login;
    }
}
