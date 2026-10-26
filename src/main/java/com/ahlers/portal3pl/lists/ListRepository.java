package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Один запрос на все списки портала: отбор, порядок, страница.
 *
 * <p>Фильтра по поклажедателю здесь нет намеренно — его накладывает
 * база по политике RLS. Так забытый фильтр перестаёт быть утечкой
 * между клиентами.
 *
 * <p>Имена колонок подставляются в текст запроса, и безопасно это
 * ровно потому, что пришли они не из адреса, а из справочника:
 * {@link ListFilters} и {@link ListPaging} сверяют каждое и отвергают
 * незнакомое. Значения подставляются только параметрами — всегда.
 */
@Repository
public class ListRepository {

    private final JdbcClient jdbc;

    public ListRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> page(ListKind kind, List<ListFilters.Condition> filters,
                                          ListPaging.Page page, boolean withArchived) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> values = new LinkedHashMap<>();

        int n = 0;
        if (kind.archiveColumn() != null && !withArchived) {
            n++;
            where.append("WHERE COALESCE(").append(kind.archiveColumn())
                    .append(", true)\n");
        }
        for (ListFilters.Condition f : filters) {
            String name = "f" + n++;
            where.append(n == 1 ? "WHERE " : "  AND ")
                    .append(predicate(f, name)).append('\n');
            // Флажок и галочка сравниваются с постоянной, а не со
            // значением поля: параметр им не нужен, и класть его в запрос
            // значило бы объявить параметр, которого в тексте нет.
            if (!"flag".equals(f.kind()) && !"bool".equals(f.kind())) {
                values.put(name, "text".equals(f.kind()) ? "%" + f.value() + "%" : f.value());
            }
        }

        // Сколько всего строк — тем же запросом, а не вторым.
        //
        // Второй запрос считал бы по другому снимку данных: между ними
        // кто-то подтверждает документ, и человек видит «всего 134»
        // над страницей из 133-х. Окно count(*) OVER () считает по тому
        // же отбору и до LIMIT — ровно то, что надо строке
        // «Стр. 2 из 7 · всего 134».
        String sql = """
                SELECT *, count(*) OVER ()::int AS total_rows
                  FROM dbo.%s
                %s ORDER BY %s
                 LIMIT :limit::int OFFSET :offset::int
                """.formatted(kind.view(), where, orderBy(kind, page));

        var query = jdbc.sql(sql)
                .param("limit", page.size())
                .param("offset", page.offset());
        for (Map.Entry<String, Object> value : values.entrySet()) {
            query = query.param(value.getKey(), value.getValue());
        }
        return SqlRows.toCamelCase(query.query().listOfRows());
    }

    /** Условие под один фильтр. Колонка — из справочника, значение — параметром. */
    private static String predicate(ListFilters.Condition f, String name) {
        return switch (f.kind()) {
            case "text" -> "COALESCE(%s::text,'') ILIKE :%s::text".formatted(f.column(), name);
            case "enum" -> "%s::text = :%s::text".formatted(f.column(), name);
            case "number" -> "%s %s :%s::numeric"
                    .formatted(f.column(), "from".equals(f.bound()) ? ">=" : "<=", name);
            // Флажок спрашивает «есть ли»: «Только брак» — это строки,
            // где брак больше нуля. Числовым фильтром это выражается
            // («брак с 0,001»), но человеку пришлось бы вместо щелчка
            // вспоминать, какое число написать.
            case "flag" -> "COALESCE(%s, 0) > 0".formatted(f.column());
            // Галочка — тот же вопрос «есть ли», но по логической
            // колонке: «Только хрупкие». Снятая галочка не присылается
            // вовсе, поэтому обратного условия («только нехрупкие»)
            // здесь нет: отсутствие отметки значит «не отбирать».
            case "bool" -> "COALESCE(%s, false)".formatted(f.column());
            // Дата «по» — строго меньше следующего дня, а не «меньше или
            // равно». Колонки вроде doc_date с V62 хранят отметку времени,
            // и условие doc_date <= DATE '2026-12-01' отсекло бы всё,
            // что случилось в этот день позже полуночи, то есть почти
            // весь последний день диапазона.
            case "date" -> "from".equals(f.bound())
                    ? "%s >= :%s::date".formatted(f.column(), name)
                    : "%s < :%s::date + 1".formatted(f.column(), name);
            default -> throw Fail.internal("error.filter.unknown_kind", "kind", f.kind());
        };
    }

    /**
     * Порядок строк.
     *
     * <p>Хвост из устойчивой колонки обязателен — его даёт
     * {@link ListKind#defaultOrder()}. Без него две строки с одинаковым
     * значением идут в произвольном порядке, и порядок этот волен
     * поменяться между запросами: одну строку человек увидит на обеих
     * страницах, другую — ни на одной. Страницы без устойчивого
     * порядка — это не страницы, а выборка наугад.
     *
     * <p>{@code NULLS LAST} в обе стороны: пустое поле — это
     * «неизвестно», и наверху оно не нужно ни при возрастании,
     * ни при убывании.
     */
    private static String orderBy(ListKind kind, ListPaging.Page page) {
        if (page.sortColumn() == null) {
            return kind.defaultOrder();
        }
        String direction = page.descending() ? "DESC" : "ASC";
        return "%s %s NULLS LAST, %s"
                .formatted(page.sortColumn(), direction, kind.defaultOrder());
    }
}
