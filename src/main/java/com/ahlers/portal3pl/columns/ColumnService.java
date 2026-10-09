package com.ahlers.portal3pl.columns;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.i18n.LocaleService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Колонки таблиц: какие есть, как называются, кто какие видит.
 *
 * <p>Раньше это жило только в браузере: выбор колонок в окне «⚙ Колонки»
 * терялся при перезагрузке, а 204 подписи были вбиты в вёрстку. Теперь
 * раскладку собирает база и отдаёт готовой — экран её только рисует.
 *
 * <p>Три уровня настройки, и порядок между ними важен:
 * <ol>
 *   <li>портал — что показывать по умолчанию;
 *   <li>поклажедатель — что ему вообще доступно и что показывать сразу;
 *   <li>пользователь — что он выбрал лично.
 * </ol>
 *
 * <p>Запрет на уровне поклажедателя сильнее выбора пользователя:
 * запрещённая колонка не попадает даже в список выбора, поэтому включить
 * её себе нельзя. На это опирается сокрытие себестоимости и цен.
 */
@Service
public class ColumnService {

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final LocaleService locales;
    private final AdminUserService users;

    public ColumnService(JdbcClient jdbc, DepositorScope depositorScope,
                         LocaleService locales, AdminUserService users) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.locales = locales;
        this.users = users;
    }

    /* ======================= Чтение раскладки ======================= */

    /**
     * Готовая раскладка таблиц для пользователя: подпись, видимость, порядок.
     *
     * @param tableKey одна таблица или {@code null} — тогда все сразу,
     *                 чтобы экран получил всё за один запрос
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> layout(PortalUser user, String tableKey) {
        depositorScope.apply();
        if (tableKey != null) {
            requireTable(tableKey);
        }
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.table_columns(:user::uuid, :locale::text, :table::text)")
                .param("user", user.userId())
                .param("locale", locales.localeOf(user.login()))
                .param("table", tableKey)
                .query()
                .listOfRows());
    }

    /**
     * Сохранить выбор пользователя. Порядок колонок — порядок списка.
     *
     * <p>Пустой список отклоняется: таблица без колонок — это не настройка,
     * а сломанный экран, с которого уже не выбраться. Сбросить к значениям
     * по умолчанию можно отдельным вызовом.
     */
    @Transactional
    public List<Map<String, Object>> saveMine(PortalUser user, String tableKey,
                                              List<String> columns) {
        depositorScope.apply();
        requireTable(tableKey);
        if (columns == null || columns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Оставьте хотя бы одну колонку");
        }
        // повторы в списке сделали бы порядок бессмысленным, а ключ —
        // неуникальным; сохраняем первое вхождение каждой колонки
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(columns));

        List<String> allowed = jdbc.sql("""
                        SELECT column_id FROM dbo.table_columns(
                                 :user::uuid, :locale::text, :table::text)
                        """)
                .param("user", user.userId())
                .param("locale", locales.localeOf(user.login()))
                .param("table", tableKey)
                .query(String.class)
                .list();

        for (String column : unique) {
            if (!allowed.contains(column)) {
                // либо такой колонки нет, либо она запрещена поклажедателю —
                // в обоих случаях показывать её нельзя
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Колонка «" + column + "» в таблице «" + tableKey + "» недоступна");
            }
        }

        // Прикреплённую колонку убрать нельзя (V36): без кода и
        // наименования список товаров бесполезен. Запрет есть и в базе,
        // но оттуда приходит исключение Postgres, а экрану нужен код 409
        // и человеческий текст.
        List<String> pinned = jdbc.sql("""
                        SELECT COALESCE(t.value, c.text_key) AS label
                        FROM dbo.table_column c
                        LEFT JOIN dbo.ui_texts(:locale::text) t ON t.text_key = c.text_key
                        WHERE c.table_key = :table::varchar AND c.is_pinned
                          AND NOT (c.column_id = ANY (:cols::text[]))
                        ORDER BY c.sort_order
                        """)
                .param("locale", locales.localeOf(user.login()))
                .param("table", tableKey)
                .param("cols", unique.toArray(String[]::new))
                .query(String.class)
                .list();
        if (!pinned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Эти колонки убрать нельзя: " + String.join(", ", pinned));
        }

        jdbc.sql("SELECT dbo.user_columns_save(:user::uuid, :table::text, :cols::text[])")
                .param("user", user.userId())
                .param("table", tableKey)
                .param("cols", unique.toArray(String[]::new))
                .query()
                .listOfRows();
        return layout(user, tableKey);
    }

    /** Сброс к значениям по умолчанию: без таблицы — по всем сразу. */
    @Transactional
    public List<Map<String, Object>> resetMine(PortalUser user, String tableKey) {
        depositorScope.apply();
        if (tableKey != null) {
            requireTable(tableKey);
        }
        jdbc.sql("SELECT dbo.user_columns_reset(:user::uuid, :table::text)")
                .param("user", user.userId())
                .param("table", tableKey)
                .query()
                .listOfRows();
        return layout(user, tableKey);
    }

    /* ==================== Администрирование ==================== */

    /** Разделы для экрана настройки: человеку нужен список, а не 204 строки. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tables(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.v_column_tables")
                .query()
                .listOfRows());
    }

    /**
     * Что настроено у поклажедателя. Читается через SECURITY DEFINER:
     * администратор работает с чужим поклажедателем, а RLS пускает
     * только к своему.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> depositorColumns(PortalUser actor, String depositorId,
                                                      String tableKey) {
        users.requireAdmin(actor);
        if (tableKey != null) {
            requireTable(tableKey);
        }
        return SqlRows.toCamelCase(jdbc.sql("""
                        SELECT * FROM dbo.admin_depositor_columns(
                                 :dep::uuid, :locale::text, :table::text)
                        """)
                .param("dep", depositorId)
                .param("locale", locales.localeOf(actor.login()))
                .param("table", tableKey)
                .query()
                .listOfRows());
    }

    @Transactional
    public List<Map<String, Object>> saveDepositorColumn(PortalUser actor, String depositorId,
                                                         String tableKey, String columnId,
                                                         boolean available, boolean byDefault) {
        users.requireAdmin(actor);
        requireTable(tableKey);
        requireColumn(tableKey, columnId);
        if (!available) {
            requireNotLocked(tableKey, columnId);
        }
        jdbc.sql("""
                        SELECT dbo.admin_depositor_column_save(
                                 :dep::uuid, :table::text, :col::text,
                                 :available::boolean, :byDefault::boolean)
                        """)
                .param("dep", depositorId)
                .param("table", tableKey)
                .param("col", columnId)
                .param("available", available)
                .param("byDefault", byDefault)
                .query()
                .listOfRows();
        return depositorColumns(actor, depositorId, tableKey);
    }

    /**
     * Вернуть поклажедателю портальные настройки колонок.
     *
     * <p>Строки хранятся только там, где настройка отличается от портальной,
     * поэтому «вернуть как у портала» — это убрать их. Обычным DELETE этого
     * не сделать: таблица под RLS, и запрос без контекста поклажедателя молча
     * удаляет ноль строк, а у администратора своего контекста нет.
     */
    @Transactional
    public List<Map<String, Object>> resetDepositorColumns(PortalUser actor, String depositorId,
                                                           String tableKey) {
        users.requireAdmin(actor);
        if (tableKey != null) {
            requireTable(tableKey);
        }
        jdbc.sql("SELECT dbo.admin_depositor_columns_reset(:dep::uuid, :table::text)")
                .param("dep", depositorId)
                .param("table", tableKey)
                .query()
                .listOfRows();
        return depositorColumns(actor, depositorId, tableKey);
    }

    /** Сколько настроек колонок заведено руками. */
    @Transactional(readOnly = true)
    public int depositorColumnCount(PortalUser actor, String depositorId, String tableKey) {
        users.requireAdmin(actor);
        // функция возвращает count(*)::int — NULL оттуда не приходит,
        // поэтому проверка на null была мёртвой
        return jdbc
                .sql("SELECT dbo.admin_depositor_column_count(:dep::uuid, :table::text)")
                .param("dep", depositorId)
                .param("table", tableKey)
                .query(Integer.class)
                .single();
    }

    /* ============== Переименование полей поклажедателем ============== */

    /**
     * Какие поля клиент называет по-своему.
     *
     * <p>Строка на поле, а не на колонку: поле показывается в нескольких
     * таблицах, а название у него одно. Рядом видно, где именно подпись
     * поменяется, — иначе это приходится угадывать.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> fieldLabels(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.admin_field_labels(:dep::uuid)")
                .param("dep", depositorId)
                .query()
                .listOfRows());
    }

    /** Пустая подпись возвращает портальное название поля. */
    @Transactional
    public List<Map<String, Object>> saveFieldLabel(PortalUser actor, String depositorId,
                                                    String objectCode, String fieldId,
                                                    String label) {
        users.requireAdmin(actor);
        if (fieldId == null || fieldId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите поле");
        }
        boolean knownObject = jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.enum_object_type WHERE code = :code::varchar)")
                .param("code", objectCode == null ? "" : objectCode)
                .query(Boolean.class)
                .single();
        if (!knownObject) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Неизвестный тип объекта: " + objectCode);
        }
        // переименовывать можно только то, что где-то показывается:
        // иначе в справочнике появятся подписи, которых никто не увидит
        boolean shown = jdbc.sql("""
                        SELECT EXISTS (
                          SELECT 1 FROM dbo.table_column c
                          JOIN dbo.enum_object_type o ON o.id = c.object_type_id
                          WHERE o.code = :code::varchar AND c.field_id = :field::varchar)
                        """)
                .param("code", objectCode)
                .param("field", fieldId)
                .query(Boolean.class)
                .single();
        if (!shown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Поле «" + fieldId + "» у объекта «" + objectCode + "» нигде не показывается");
        }
        jdbc.sql("""
                        SELECT dbo.admin_field_label_save(
                                 :dep::uuid, :code::text, :field::text, :label::text)
                        """)
                .param("dep", depositorId)
                .param("code", objectCode)
                .param("field", fieldId)
                .param("label", label)
                .query()
                .listOfRows();
        return fieldLabels(actor, depositorId);
    }

    /**
     * Скопировать настройки одного пользователя другому.
     *
     * <p>Переносится то, что относится к удобству работы: видимость пунктов
     * меню, разрешения на создание документов и загрузку Excel, выбранные
     * колонки. Роль и доступы к поклажедателям и складам не переносятся:
     * доступ — это «кто видит чьи данные», и копирование одной кнопкой —
     * ровно тот случай, когда человек случайно получает доступ к чужому
     * клиенту. Доступы выдаются отдельно и осознанно.
     */
    @Transactional
    public void copySettings(PortalUser actor, String fromUserId, String toUserId) {
        users.requireAdmin(actor);
        if (fromUserId == null || fromUserId.isBlank() || toUserId == null || toUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите, у кого и кому копировать настройки");
        }
        if (fromUserId.equals(toUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Это один и тот же пользователь");
        }
        requireUser(fromUserId);
        requireUser(toUserId);
        jdbc.sql("SELECT dbo.user_settings_copy(:from::uuid, :to::uuid)")
                .param("from", fromUserId)
                .param("to", toUserId)
                .query()
                .listOfRows();
    }

    /* ========================= Проверки ========================= */

    private void requireTable(String tableKey) {
        boolean known = jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.table_column WHERE table_key = :key::varchar)")
                .param("key", tableKey == null ? "" : tableKey)
                .query(Boolean.class)
                .single();
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Таблицы «" + tableKey + "» в справочнике колонок нет");
        }
    }

    private void requireColumn(String tableKey, String columnId) {
        boolean known = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dbo.table_column
                                        WHERE table_key = :key::varchar
                                          AND column_id = :col::varchar)
                        """)
                .param("key", tableKey)
                .param("col", columnId == null ? "" : columnId)
                .query(Boolean.class)
                .single();
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Колонки «" + columnId + "» в таблице «" + tableKey + "» нет");
        }
    }

    /**
     * Поле, которое нельзя скрыть от поклажедателя.
     *
     * <p>Признаков два, и путать их нельзя (V36):
     * {@code is_locked} — поклажедателю скрыть нельзя (этот экран);
     * {@code is_pinned} — из своей таблицы убрать нельзя (окно
     * ⚙ Колонки). Служебные поля товара нескрываемы для клиента,
     * но в личной таблице человек их убирает свободно.
     *
     * <p>Запрет стоит и в базе, но там он поднимает исключение
     * Postgres, а экрану нужен внятный код и текст. Поэтому проверяем
     * здесь, а запрет в базе остаётся последней линией: до неё доберётся
     * всё, что обойдёт этот метод.
     */
    private void requireNotLocked(String tableKey, String columnId) {
        Map<String, Object> row = jdbc.sql("""
                        SELECT c.is_locked, COALESCE(t.value, c.text_key) AS label
                        FROM dbo.table_column c
                        LEFT JOIN dbo.ui_texts('ru') t ON t.text_key = c.text_key
                        WHERE c.table_key = :key::varchar AND c.column_id = :col::varchar
                        """)
                .param("key", tableKey)
                .param("col", columnId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(Map.of());
        if (Boolean.TRUE.equals(row.get("is_locked"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Поле «" + row.get("label") + "» нельзя скрыть от поклажедателя");
        }
    }

    private void requireUser(String userId) {
        boolean known = jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.app_user WHERE user_id = :id::uuid)")
                .param("id", userId)
                .query(Boolean.class)
                .single();
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
    }
}
