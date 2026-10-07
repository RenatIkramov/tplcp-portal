package com.ahlers.portal3pl.i18n;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Языки портала и тексты интерфейса.
 *
 * <p>Раньше словарь подписей был зашит в вёрстку: добавить язык значило
 * править HTML и выкладывать его заново. Теперь тексты лежат в базе,
 * ведутся из администрирования, а экран получает их одним запросом.
 *
 * <p>Основной язык — английский. Если перевода на выбранный язык нет,
 * отдаётся английский: непереведённая кнопка должна оставаться кнопкой
 * с надписью, а не пустым местом.
 *
 * <p>Выбранный язык хранится в учётной записи, а не в браузере: человек
 * заходит с разных компьютеров, и язык едет за ним.
 */
@Service
public class LocaleService {

    private final JdbcClient jdbc;
    private final AdminUserService users;

    public LocaleService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** Языки для переключателя. Доступно без входа — переключатель есть и на форме входа. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> locales() {
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT code, name, is_default FROM dbo.v_locales ORDER BY sort_order, name")
                .query()
                .listOfRows());
    }

    /**
     * Все подписи на выбранном языке, ключ → текст.
     * <p>
     * Отдаём разом, а не по одной: их меньше трёхсот, экран всё равно
     * применяет их целиком при каждом переключении языка.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> texts(String localeCode) {
        requireLocale(localeCode);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.sql("SELECT * FROM dbo.ui_texts(:code::text)")
                .param("code", localeCode)
                .query()
                .listOfRows()) {
            out.put(String.valueOf(row.get("text_key")), row.get("value"));
        }
        return out;
    }

    /** Язык пользователя; пусто — язык портала по умолчанию. */
    @Transactional(readOnly = true)
    public String localeOf(String login) {
        return jdbc.sql("""
                        SELECT COALESCE(l.code, (SELECT code FROM dbo.dictionary_locale WHERE is_default))
                        FROM dbo.app_user u
                        LEFT JOIN dbo.dictionary_locale l ON l.locale_id = u.locale_id
                        WHERE u.login = :login::text
                        """)
                .param("login", login)
                .query(String.class)
                .optional()
                .orElseGet(this::defaultLocale);
    }

    @Transactional
    public String setLocaleOf(PortalUser user, String localeCode) {
        requireLocale(localeCode);
        jdbc.sql("SELECT dbo.user_set_locale(:id::uuid, :code::text)")
                .param("id", user.userId())
                .param("code", localeCode)
                .query()
                .listOfRows();
        return localeCode;
    }

    public String defaultLocale() {
        return jdbc.sql("SELECT code FROM dbo.dictionary_locale WHERE is_default")
                .query(String.class)
                .optional()
                .orElse("en");
    }

    /* ---------------- Ведение переводов ---------------- */

    /**
     * Таблица переводов для экрана администрирования: строка на ключ,
     * колонки на языки, плюс признаки ключа. Так сразу видно, где перевода
     * не хватает и к чему он относится.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> matrix(PortalUser actor) {
        users.requireAdmin(actor);
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.sql("SELECT * FROM dbo.ui_text_matrix()")
                .query()
                .listOfRows()) {
            String key = String.valueOf(row.get("text_key"));
            Map<String, Object> line = byKey.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("textKey", k);
                // признаки ключа: где надпись живёт и что это за объект —
                // по ним переводчик понимает, что именно он переводит
                m.put("scope", row.get("scope"));
                m.put("element", row.get("element"));
                m.put("owner", row.get("owner"));
                m.put("values", new LinkedHashMap<String, Object>());
                return m;
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) line.get("values");
            values.put(String.valueOf(row.get("locale_code")), row.get("value"));
        }
        return List.copyOf(byKey.values());
    }

    @Transactional
    public List<Map<String, Object>> save(PortalUser actor, String textKey, String localeCode,
                                          String value) {
        users.requireAdmin(actor);
        requireLocale(localeCode);
        if (textKey == null || textKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите ключ текста");
        }
        boolean exists = Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.ui_text_key WHERE text_key = :key::varchar)")
                .param("key", textKey)
                .query(Boolean.class)
                .single());
        if (!exists) {
            // Ключи приходят из вёрстки: выдумывать их в администрировании
            // нельзя — такой текст всё равно негде показать.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ключ «" + textKey + "» в интерфейсе не используется");
        }
        jdbc.sql("SELECT dbo.ui_text_save(:key::text, :code::text, :value::text)")
                .param("key", textKey)
                .param("code", localeCode)
                .param("value", value)
                .query()
                .listOfRows();
        return matrix(actor);
    }

    private void requireLocale(String code) {
        boolean known = Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.v_locales WHERE code = :code::varchar)")
                .param("code", code == null ? "" : code)
                .query(Boolean.class)
                .single());
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный язык: " + code);
        }
    }
}
