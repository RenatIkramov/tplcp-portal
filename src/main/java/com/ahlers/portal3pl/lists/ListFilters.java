package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.i18n.LocaleService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Фильтры списка документов: что человеку разрешено и во что это
 * превращается в запросе.
 *
 * <p>ПОЧЕМУ ОТДЕЛЬНЫЙ КЛАСС. Здесь проходит граница доверия. Имя колонки
 * приходит из адреса запроса и попадает в текст SQL — подставить его как
 * есть значит отдать базу тому, кто правит адрес в браузере. Поэтому имя
 * не подставляется, а <b>сверяется</b>: в запрос попадает не то, что
 * прислали, а то, что нашлось в справочнике. Значения не подставляются
 * никогда — только параметрами.
 *
 * <p>Справочник тот же, по которому строится заголовок таблицы
 * ({@code table_filters} поверх {@code table_columns}), поэтому запреты
 * поклажедателя действуют и здесь: колонка, скрытая от клиента, не дойдёт
 * до списка разрешённых. Это не формальность — по скрытому полю можно
 * было бы перебором значений узнать то, что спрятали.
 *
 * <p>ФОРМАТ ЗАПРОСА. {@code filter.<колонка>} для текста и справочника,
 * {@code filter.<колонка>.from} и {@code .to} для дат и чисел. Один
 * фильтр — одна колонка, вид сравнения определяется видом колонки,
 * а не выбором человека: так панель остаётся простой.
 */
@Component
public class ListFilters {

    /** Приставка, по которой фильтры отличаются от прочих параметров адреса. */
    private static final String PREFIX = "filter.";

    private final JdbcClient jdbc;
    private final LocaleService locales;

    public ListFilters(JdbcClient jdbc, LocaleService locales) {
        this.jdbc = jdbc;
        this.locales = locales;
    }

    /**
     * Одно проверенное условие.
     *
     * @param column колонка представления — из справочника, не из запроса
     * @param kind   вид фильтра: text, enum, date, number
     * @param bound  граница диапазона: {@code from}, {@code to} или {@code null}
     * @param value  значение, как его прислали; уходит параметром
     */
    public record Condition(String column, String kind, String bound, String value) {
    }

    /** Справочник фильтров для экрана: подпись, вид, значения справочника. */
    public List<Map<String, Object>> catalogue(PortalUser user, ListKind kind) {
        List<Map<String, Object>> rows = available(user, kind);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            // Значения справочника подкладываем сразу: экран рисует
            // выпадающий список и не должен ходить за ним отдельно,
            // иначе панель появится раньше своих вариантов.
            if ("enum".equals(row.get("filterKind"))) {
                item.put("options", options(String.valueOf(row.get("filterEnum")),
                        kind.objectTypeId()));
            }
            // Имя колонки базы экрану незачем: он работает с columnId.
            // Отдавать его наружу значит рассказывать о внутреннем
            // устройстве без нужды.
            item.remove("filterColumn");
            out.add(item);
        }
        return out;
    }

    /**
     * Превращает параметры адреса в условия, отвергая всё, чего нет
     * в справочнике.
     *
     * <p>Неизвестная колонка — это отказ, а не молчаливый пропуск.
     * Пропустить значило бы показать человеку список, отобранный не так,
     * как он просил, и никак об этом не сказать.
     */
    public List<Condition> parse(PortalUser user, ListKind kind, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> allowed = new LinkedHashMap<>();
        for (Map<String, Object> row : available(user, kind)) {
            allowed.put(String.valueOf(row.get("columnId")), row);
        }

        List<Condition> conditions = new ArrayList<>();
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (!param.getKey().startsWith(PREFIX)) {
                continue;
            }
            String value = param.getValue();
            if (value == null || value.isBlank()) {
                // Пустое поле — это «не фильтровать», а не «найти пустые».
                // Экран присылает всю панель целиком, и половина полей
                // обычно пуста.
                continue;
            }
            String rest = param.getKey().substring(PREFIX.length());
            String bound = null;
            String columnId = rest;
            int dot = rest.lastIndexOf('.');
            if (dot > 0) {
                String tail = rest.substring(dot + 1);
                if ("from".equals(tail) || "to".equals(tail)) {
                    bound = tail;
                    columnId = rest.substring(0, dot);
                }
            }

            Map<String, Object> column = allowed.get(columnId);
            if (column == null) {
                throw Fail.badRequest("error.filter.unknown_column", "column", columnId);
            }
            String filterKind = String.valueOf(column.get("filterKind"));
            boolean ranged = "date".equals(filterKind) || "number".equals(filterKind);
            if (ranged == (bound == null)) {
                // Диапазон без границы или граница у того, что диапазоном
                // не бывает: и то и другое — ошибка экрана, и молчать
                // о ней значит отдать список, отобранный наугад.
                throw Fail.badRequest("error.filter.wrong_bound", "column", columnId);
            }
            conditions.add(new Condition(String.valueOf(column.get("filterColumn")),
                    filterKind, bound, value.trim()));
        }
        return conditions;
    }

    /**
     * Запоминает, какие фильтры человек держит на панели и в каком
     * порядке.
     *
     * <p>Каждое присланное имя сверяется с тем же справочником, по
     * которому строится панель. Иначе здесь открывалась бы щель:
     * записать себе в выбор колонку, запрещённую поклажедателем, —
     * и она пришла бы обратно из {@code table_filters} уже как своя.
     *
     * <p>Пустой список отвергается. Задумано было иначе — панель без
     * фильтров как осознанный выбор, — но отсутствие строк в
     * {@code user_table_filter} уже значит «человек не настраивал»,
     * и отличить одно от другого нечем: сняв все галочки, человек
     * получил бы назад набор по умолчанию, то есть окно сделало бы
     * ровно обратное. Хотя бы один фильтр обязателен, как и хотя бы
     * одна колонка.
     */
    public List<Map<String, Object>> saveMine(PortalUser user, ListKind kind,
                                              List<String> columns) {
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(
                columns == null ? List.of() : columns));
        if (unique.isEmpty()) {
            throw Fail.badRequest("error.filter.at_least_one");
        }
        List<String> allowed = available(user, kind).stream()
                .map(row -> String.valueOf(row.get("columnId")))
                .toList();
        for (String column : unique) {
            if (!allowed.contains(column)) {
                throw Fail.badRequest("error.filter.unknown_column", "column", column);
            }
        }
        jdbc.sql("SELECT dbo.user_filters_save(:user::uuid, :table::text, :cols::text[])")
                .param("user", user.userId())
                .param("table", kind.tableKey())
                .param("cols", unique.toArray(String[]::new))
                .query()
                .listOfRows();
        return catalogue(user, kind);
    }

    /** Убирает личный выбор: действует набор по умолчанию. */
    public List<Map<String, Object>> resetMine(PortalUser user, ListKind kind) {
        jdbc.sql("SELECT dbo.user_filters_reset(:user::uuid, :table::text)")
                .param("user", user.userId())
                .param("table", kind.tableKey())
                .query()
                .listOfRows();
        return catalogue(user, kind);
    }

    /* ----------------------------- внутреннее ----------------------------- */

    private List<Map<String, Object>> available(PortalUser user, ListKind kind) {
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.table_filters(:user::uuid, :locale::text, :table::text)")
                .param("user", user.userId())
                .param("locale", locales.localeOf(user.login()))
                .param("table", kind.tableKey())
                .query()
                .listOfRows());
    }

    /**
     * Значения справочника для выпадающего списка.
     *
     * <p>Имя справочника берётся из каталога, а не из запроса, и всё же
     * сверяется со списком известных: каталог правится миграцией, а
     * опечатка в нём иначе дошла бы до текста запроса.
     *
     * <p>Статусы разные у поступлений, заказов и перемещений, поэтому
     * {@code enum_status} отбирается по виду документа. Остальные
     * справочники общие.
     */
    private List<Map<String, Object>> options(String source, short objectTypeId) {
        return switch (source) {
            case "enum_status" -> SqlRows.toCamelCase(jdbc.sql("""
                            SELECT code AS value, name AS label
                              FROM dbo.enum_status
                             WHERE object_type_id = :objectType::smallint AND is_active
                             ORDER BY sort_order, name
                            """)
                    .param("objectType", objectTypeId)
                    .query().listOfRows());
            case "enum_order_type", "enum_receipt_doc_type" -> SqlRows.toCamelCase(jdbc
                    .sql("SELECT id AS value, name AS label FROM dbo.%s WHERE is_active"
                            .formatted(source) + " ORDER BY sort_order, name")
                    .query().listOfRows());
            // Группы товара — свободный текст поклажедателя (V38),
            // справочника у них нет. Поэтому список собирается
            // из того, что у этого клиента действительно лежит
            // на складе: вписанный заранее список групп («Сырьё»,
            // «Мясная продукция») был наследством демо и у настоящего
            // клиента не совпадал ни с чем.
            case "item_group" -> SqlRows.toCamelCase(jdbc.sql("""
                            SELECT DISTINCT item_group_name AS value,
                                   item_group_name AS label
                              FROM dbo.v_stock_lines
                             WHERE item_group_name IS NOT NULL
                               AND item_group_name <> ''
                             ORDER BY 1
                            """).query().listOfRows());
            // Группа, вид и состояние товара, вид упаковки — тоже свободный
            // текст, пришедший из WMS. Собираются так же, как группа в
            // запасах, но из своего представления: в запасах лежит не весь
            // справочник, а то, что сейчас на складе, и список групп в
            // товарах оказался бы короче самого справочника.
            case "sku_group" -> distinct("v_sku_full", "item_group_name");
            case "sku_type" -> distinct("v_sku_full", "item_type_name");
            case "sku_status" -> distinct("v_sku_full", "status_name");
            case "packaging_type" -> distinct("v_product_packaging", "packaging_type_name");
            case "warehouse" -> SqlRows.toCamelCase(jdbc.sql("""
                            SELECT warehouse_id AS value, name AS label
                              FROM dbo.warehouse
                             WHERE COALESCE(is_active, true)
                             ORDER BY name
                            """).query().listOfRows());
            default -> throw Fail.internal("error.filter.unknown_enum", "enum", source);
        };
    }

    /**
     * Варианты для справочника без справочника: то, что действительно
     * встречается в колонке.
     *
     * <p>Имена представления и колонки приходят только из {@code switch}
     * выше — из текста программы, не из запроса, — поэтому их подстановка
     * безопасна. Представления открыты от имени обратившегося
     * ({@code security_invoker}), так что клиент видит варианты только
     * своего поклажедателя.
     */
    private List<Map<String, Object>> distinct(String view, String column) {
        return SqlRows.toCamelCase(jdbc.sql("""
                        SELECT DISTINCT %1$s AS value, %1$s AS label
                          FROM dbo.%2$s
                         WHERE %1$s IS NOT NULL AND %1$s <> ''
                         ORDER BY 1
                        """.formatted(column, view))
                .query().listOfRows());
    }
}
