package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
