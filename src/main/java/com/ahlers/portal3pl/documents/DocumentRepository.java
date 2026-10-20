package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чтение документов из представлений v_receipts / v_orders / v_transfers
 * и их строк. Фильтра по поклажедателю в запросах нет — его накладывает
 * база по политике RLS (см. DepositorScope).
 * <p>
 * Имя представления подставляется в текст запроса, поэтому оно берётся
 * исключительно из перечисления DocumentType, а не из пользовательского ввода.
 */
@Repository
public class DocumentRepository {

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Типы заказов и типы приходных документов — для выпадающих списков
     * шапки документа.
     *
     * <p>Списки общие для портала: в таблицах нет поклажедателя, и RLS
     * к ним не применяется. Правит их администратор через справочники
     * уровня портала (V24), читать их вправе любой вошедший — иначе
     * в карточке нечего выбрать.
     *
     * <p>Наименование берётся из самой таблицы, без перевода: ровно так
     * же его отдают v_orders и v_receipts в колонках order_type_name
     * и doc_type_name. Одно и то же значение должно читаться одинаково
     * и в списке, и в сохранённом документе; перевод перечислений —
     * отдельная работа, и делать его в одном месте из двух нельзя.
     */
    public List<Map<String, Object>> enumValues(String table) {
        String sql = """
                SELECT id, code, name
                FROM dbo.%s
                WHERE COALESCE(is_active, true)
                ORDER BY COALESCE(sort_order, id), name
                """.formatted(table);
        return SqlRows.toCamelCase(jdbc.sql(sql).query().listOfRows());
    }

    /**
     * Список документов со сколькими угодно фильтрами по колонкам.
     *
     * <p>Здесь был ещё общий поиск: одна строка шла сразу по трём
     * номерам документа и по контрагенту. Поле убрано с панели по
     * решению заказчика, и правильно: подписать одно поле, которое
     * ищет по четырём разным вещам, нечем — человек не знает, что он
     * сейчас ищет. Вместо него отдельные фильтры по каждому номеру
     * и по контрагенту, каждый со своей подписью. Параметр убран
     * вместе с полем: звать его стало некому, а необходимый, но
     * никем не вызываемый путь — это путь, который тихо расходится
     * с остальным кодом.
     *
     * <p>Имена колонок в условиях подставляются в текст запроса, и это
     * безопасно ровно потому, что они пришли не из запроса пользователя,
     * а из справочника: {@link DocumentFilters} сверяет каждое и отвергает
     * незнакомое. Значения подставляются только параметрами — всегда.
     */
    public List<Map<String, Object>> list(DocumentType type,
                                          List<DocumentFilters.Condition> filters) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> values = new LinkedHashMap<>();

        int n = 0;
        for (DocumentFilters.Condition f : filters) {
            String name = "f" + n++;
            where.append(n == 1 ? "WHERE " : "  AND ")
                    .append(predicate(f, name)).append('\n');
            values.put(name, "text".equals(f.kind()) ? "%" + f.value() + "%" : f.value());
        }

        String sql = "SELECT * FROM dbo.%s\n%sORDER BY doc_date DESC, document_no DESC"
                .formatted(type.headerView(), where);

        var query = jdbc.sql(sql);
        for (Map.Entry<String, Object> value : values.entrySet()) {
            query = query.param(value.getKey(), value.getValue());
        }
        return SqlRows.toCamelCase(query.query().listOfRows());
    }

    /** Условие под один фильтр. Колонка — из справочника, значение — параметром. */
    private static String predicate(DocumentFilters.Condition f, String name) {
        return switch (f.kind()) {
            case "text" -> "COALESCE(%s::text,'') ILIKE :%s::text".formatted(f.column(), name);
            case "enum" -> "%s::text = :%s::text".formatted(f.column(), name);
            case "number" -> "%s %s :%s::numeric"
                    .formatted(f.column(), "from".equals(f.bound()) ? ">=" : "<=", name);
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

    /** Шапка одного документа. Пустой список означает «нет доступа или не существует». */
    public List<Map<String, Object>> header(DocumentType type, String docId) {
        String sql = "SELECT * FROM dbo.%s WHERE doc_id = :docId::uuid"
                .formatted(type.headerView());
        return SqlRows.toCamelCase(jdbc.sql(sql).param("docId", docId).query().listOfRows());
    }

    /** Состав документа: одна строка на номер позиции (решение Р1 — агрегат по партиям). */
    public List<Map<String, Object>> lines(DocumentType type, String docId) {
        String sql = """
                SELECT * FROM dbo.%s WHERE doc_id = :docId::uuid ORDER BY line_number
                """.formatted(type.linesView());
        return SqlRows.toCamelCase(jdbc.sql(sql).param("docId", docId).query().listOfRows());
    }

    /** Детализация: записи партий. lineNumber не задан — возвращаются все строки документа. */
    public List<Map<String, Object>> lineDetails(DocumentType type, String docId, Integer lineNumber) {
        String sql = """
                SELECT * FROM dbo.%s
                WHERE doc_id = :docId::uuid
                  AND (:lineNumber::int IS NULL OR line_number = :lineNumber::int)
                ORDER BY line_number, batch_number
                """.formatted(type.lineDetailsView());
        return SqlRows.toCamelCase(jdbc.sql(sql)
                .param("docId", docId)
                .param("lineNumber", lineNumber)
                .query()
                .listOfRows());
    }
}
