package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.i18n.LocaleService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Страница списка: какую часть отобранного отдать и в каком порядке.
 *
 * <p>ПОЧЕМУ РЯДОМ С ФИЛЬТРАМИ. Здесь та же граница доверия, что
 * и в {@link ListFilters}: имя колонки приходит из адреса запроса
 * и попадает в текст SQL. Поэтому оно не подставляется, а
 * <b>сверяется</b> — в запрос идёт не то, что прислали, а то, что
 * нашлось в справочнике колонок (V78, {@code table_column.sort_column}).
 *
 * <p>Сверяется заодно и право: колонка берётся из раскладки ЭТОГО
 * человека ({@code table_columns}), а она не отдаёт колонок,
 * запрещённых поклажедателем. Иначе по скрытому полю можно было бы
 * узнать больше, чем показали: порядок строк — это тоже сведения
 * о значениях.
 *
 * <p>ПОЧЕМУ СОРТИРОВКА ЗДЕСЬ, А НЕ НА ЭКРАНЕ. Отсортировать страницу,
 * которую уже отрезали, — значит отсортировать двадцать строк из ста
 * тридцати. Человек нажимает «Вес» и получает самый тяжёлый документ
 * из того, что сейчас видно, а не из списка. Как только страницы
 * считает сервер, сортировать обязан он же.
 */
@Component
public class ListPaging {

    /** Сколько строк на странице, если экран не сказал. */
    public static final int DEFAULT_SIZE = 20;

    /**
     * Потолок размера страницы.
     *
     * <p>Не «на всякий случай»: без него {@code pageSize=100000}
     * в адресе возвращает ровно то, от чего мы уходим, — выгрузку
     * всего списка одним ответом, только теперь по просьбе кого
     * угодно.
     */
    public static final int MAX_SIZE = 200;

    private final JdbcClient jdbc;
    private final LocaleService locales;

    public ListPaging(JdbcClient jdbc, LocaleService locales) {
        this.jdbc = jdbc;
        this.locales = locales;
    }

    /**
     * Проверенный запрос страницы.
     *
     * @param page       номер страницы, с единицы
     * @param size       строк на странице
     * @param sortColumn колонка представления — из справочника, не из запроса;
     *                   {@code null} — порядок по умолчанию
     * @param descending порядок убывания
     */
    public record Page(int page, int size, String sortColumn, boolean descending) {

        public int offset() {
            return (page - 1) * size;
        }
    }

    public Page parse(PortalUser user, ListKind kind, Map<String, String> params) {
        Map<String, String> safe = params == null ? Map.of() : params;
        int page = number(safe.get("page"), 1);
        int size = number(safe.get("pageSize"), DEFAULT_SIZE);
        if (page < 1) {
            page = 1;
        }
        // Размер зажимаем молча, а не отказом: это не ошибка человека,
        // а цифра из адреса, и список ему всё равно нужен.
        size = Math.clamp(size, 1, MAX_SIZE);

        String columnId = safe.get("sort");
        String sortColumn = null;
        if (columnId != null && !columnId.isBlank()) {
            sortColumn = sortColumn(user, kind, columnId.trim());
        }
        return new Page(page, size, sortColumn, "desc".equalsIgnoreCase(safe.get("dir")));
    }

    /* ----------------------------- внутреннее ----------------------------- */

    /**
     * Колонка сортировки по идентификатору колонки экрана.
     *
     * <p>Неизвестная колонка — отказ, а не молчаливый порядок
     * по умолчанию: человек нажал на заголовок и должен увидеть либо
     * сортировку, либо внятный отказ. Молча отдать список в прежнем
     * порядке — значит показать, что кнопка не работает, и не сказать
     * почему.
     */
    private String sortColumn(PortalUser user, ListKind kind, String columnId) {
        return jdbc.sql("""
                        SELECT c.sort_column
                          FROM dbo.table_column c
                         WHERE c.table_key = :table::text
                           AND c.column_id = :column::text
                           AND c.sort_column IS NOT NULL
                           AND EXISTS (SELECT 1
                                         FROM dbo.table_columns(:user::uuid, :locale::text,
                                                                :table::text) mine
                                        WHERE mine.column_id = c.column_id)
                        """)
                .param("table", kind.tableKey())
                .param("column", columnId)
                .param("user", user.userId())
                .param("locale", locales.localeOf(user.login()))
                .query(String.class)
                .optional()
                .orElseThrow(() -> Fail.badRequest("error.sort.unknown_column",
                        "column", columnId));
    }

    private static int number(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw Fail.badRequest("error.page.bad_number", "value", raw);
        }
    }
}
