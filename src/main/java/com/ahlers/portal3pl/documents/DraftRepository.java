package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.SqlCast;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Запись черновиков. Имена таблиц берутся из DocumentType, имена колонок —
 * из его же списков разрешённых полей: всё, что пришло снаружи и в список
 * не входит, молча отбрасывается и в SQL не попадает.
 */
@Repository
public class DraftRepository {

    private final JdbcClient jdbc;

    public DraftRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Создаёт пустой черновик и возвращает его doc_id. */
    public String createDraft(DocumentType type, String depositorId, String userId) {
        String sql = """
                INSERT INTO dbo.%s (document_no, depositor_id, status_id, doc_date,
                                    origin, author_id, created_by, created_at)
                VALUES (dbo.next_document_no(:type),
                        :depositorId::uuid,
                        (SELECT id FROM dbo.enum_status
                          WHERE object_type_id = :objectType AND code = 'draft'),
                        current_date, 'portal', :userId::uuid, :userId::uuid, now())
                RETURNING doc_id::text
                """.formatted(type.headerTable());
        return jdbc.sql(sql)
                .param("type", type.path())
                .param("depositorId", depositorId)
                .param("objectType", type.objectTypeId())
                .param("userId", userId)
                .query(String.class)
                .single();
    }

    /** Обновляет разрешённые поля шапки. Возвращает число изменённых строк. */
    public int updateHeader(DocumentType type, String docId, Map<String, Object> values,
                            Set<String> allowed, String userId, boolean draftOnly) {
        Map<String, Object> clean = filter(values, allowed);
        if (clean.isEmpty()) {
            return 0;
        }
        // Значения приходят из JSON строками, поэтому типы указываем явно (SqlCast)
        String assignments = clean.keySet().stream()
                .map(SqlCast::assignment)
                .collect(Collectors.joining(", "));

        String draftCondition = draftOnly
                ? """
                  AND origin = 'portal'
                  AND status_id = (SELECT id FROM dbo.enum_status
                                    WHERE object_type_id = :objectType AND code = 'draft')
                  """
                : "";

        String sql = """
                UPDATE dbo.%s SET %s, updated_by = :__userId::uuid, updated_at = now()
                WHERE doc_id = :__docId::uuid
                %s
                """.formatted(type.headerTable(), assignments, draftCondition);

        var spec = jdbc.sql(sql)
                .param("__docId", docId)
                .param("__userId", userId)
                .param("objectType", type.objectTypeId());
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec.update();
    }

    public int deleteDraft(DocumentType type, String docId) {
        String sql = """
                DELETE FROM dbo.%s
                WHERE doc_id = :docId::uuid AND origin = 'portal'
                  AND status_id = (SELECT id FROM dbo.enum_status
                                    WHERE object_type_id = :objectType AND code = 'draft')
                """.formatted(type.headerTable());
        return jdbc.sql(sql)
                .param("docId", docId)
                .param("objectType", type.objectTypeId())
                .update();
    }

    public int deleteLines(DocumentType type, String docId, List<Integer> lineNumbers) {
        String sql = """
                DELETE FROM dbo.%s
                WHERE doc_id = :docId::uuid AND line_number IN (:lines)
                """.formatted(type.lineTable());
        return jdbc.sql(sql)
                .param("docId", docId)
                .param("lines", lineNumbers)
                .update();
    }

    /** Удаляет весь состав документа — используется при замене состава целиком. */
    public int deleteAllLines(DocumentType type, String docId) {
        return jdbc.sql("DELETE FROM dbo.%s WHERE doc_id = :docId::uuid".formatted(type.lineTable()))
                .param("docId", docId)
                .update();
    }

    public int nextLineNumber(DocumentType type, String docId) {
        String sql = "SELECT COALESCE(MAX(line_number), 0) + 1 FROM dbo.%s WHERE doc_id = :docId::uuid"
                .formatted(type.lineTable());
        return jdbc.sql(sql).param("docId", docId).query(Integer.class).single();
    }

    /** Добавляет строку состава: плановая запись без партии (решение Р1). */
    public int addLine(DocumentType type, String docId, int lineNumber, Map<String, Object> values) {
        Map<String, Object> clean = filter(values, type.editableLineColumns());
        clean.put("doc_id", docId);
        clean.put("line_number", lineNumber);

        List<String> cols = new ArrayList<>(clean.keySet());
        String columns = String.join(", ", cols);
        String params = cols.stream()
                .map(SqlCast::placeholder)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO dbo.%s (%s) VALUES (%s)".formatted(type.lineTable(), columns, params);
        var spec = jdbc.sql(sql);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec.update();
    }

    public int updateLine(DocumentType type, String docId, int lineNumber, Map<String, Object> values) {
        Map<String, Object> clean = filter(values, type.editableLineColumns());
        if (clean.isEmpty()) {
            return 0;
        }
        String assignments = clean.keySet().stream()
                .map(SqlCast::assignment)
                .collect(Collectors.joining(", "));
        String sql = """
                UPDATE dbo.%s SET %s
                WHERE doc_id = :__docId::uuid AND line_number = :__lineNumber
                  AND batch_number IS NULL
                """.formatted(type.lineTable(), assignments);
        var spec = jdbc.sql(sql)
                .param("__docId", docId)
                .param("__lineNumber", lineNumber);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec.update();
    }

    /** Переводит документ в статус по коду. */
    public int setStatus(DocumentType type, String docId, String statusCode, boolean markConfirmed) {
        String sql = """
                UPDATE dbo.%s
                   SET status_id = (SELECT id FROM dbo.enum_status
                                     WHERE object_type_id = :objectType AND code = :statusCode),
                       confirmed_at = CASE WHEN :markConfirmed THEN now() ELSE confirmed_at END,
                       updated_at = now()
                 WHERE doc_id = :docId::uuid
                """.formatted(type.headerTable());
        return jdbc.sql(sql)
                .param("objectType", type.objectTypeId())
                .param("statusCode", statusCode)
                .param("markConfirmed", markConfirmed)
                .param("docId", docId)
                .update();
    }

    /** Пересчитывает количество строк документа — колонка показывается в списке. */
    public void refreshLineCount(DocumentType type, String docId) {
        String sql = """
                UPDATE dbo.%s h SET line_count =
                  (SELECT COUNT(DISTINCT line_number) FROM dbo.%s d WHERE d.doc_id = h.doc_id)
                WHERE h.doc_id = :docId::uuid
                """.formatted(type.headerTable(), type.lineTable());
        jdbc.sql(sql).param("docId", docId).update();
    }

    /** Данные, по которым решается, можно ли подтверждать и редактировать. */
    public Map<String, Object> state(DocumentType type, String docId) {
        String sql = """
                SELECT h.doc_id::text, h.origin, h.document_no,
                       st.code AS status_code,
                       COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
                       (SELECT COUNT(DISTINCT line_number) FROM dbo.%s d WHERE d.doc_id = h.doc_id) AS line_count,
                       (SELECT COUNT(*) FROM dbo.%s d
                          JOIN dbo.sku s ON s.sku_id = d.sku_id
                         WHERE d.doc_id = h.doc_id AND NOT s.is_in_wms) AS unknown_sku_count,
                       (SELECT COUNT(*) FROM dbo.%s d
                         WHERE d.doc_id = h.doc_id AND d.batch_number IS NULL
                           AND (d.%s IS NULL OR d.%s <= 0)) AS bad_qty_count
                FROM dbo.%s h
                LEFT JOIN dbo.enum_status st ON st.id = h.status_id
                WHERE h.doc_id = :docId::uuid
                """.formatted(type.lineTable(), type.lineTable(), type.lineTable(),
                type.plannedQtyColumn(), type.plannedQtyColumn(), type.headerTable());
        List<Map<String, Object>> rows = jdbc.sql(sql).param("docId", docId).query().listOfRows();
        return rows.isEmpty() ? Map.of() : SqlRows.toCamelCase(rows).getFirst();
    }

    /** Какие обязательные поля шапки не заполнены. */
    public List<String> missingRequired(DocumentType type, String docId) {
        List<String> required = type.requiredForConfirm();
        String sql = "SELECT %s FROM dbo.%s WHERE doc_id = :docId::uuid"
                .formatted(String.join(", ", required), type.headerTable());
        List<Map<String, Object>> rows = jdbc.sql(sql).param("docId", docId).query().listOfRows();
        if (rows.isEmpty()) {
            return required;
        }
        Map<String, Object> row = rows.getFirst();
        List<String> missing = new ArrayList<>();
        for (String column : required) {
            if (row.get(column) == null) {
                missing.add(column);
            }
        }
        return missing;
    }

    /** Запись в журнал обменов — пока отправка в WMS заглушена (Р5). */
    public void logSync(DocumentType type, String depositorId, String status, String error) {
        jdbc.sql("""
                INSERT INTO dbo.sync_run (entity, depositor_id, direction, trigger_source,
                                          started_at, finished_at, status, rows_received,
                                          rows_applied, error_message)
                VALUES (:entity, :depositorId::uuid, 'portal_to_wms', 'manual',
                        now(), now(), :status, 1, :applied, :error)
                """)
                .param("entity", type.path())
                .param("depositorId", depositorId)
                .param("status", status)
                .param("applied", "success".equals(status) ? 1 : 0)
                .param("error", error)
                .update();
    }

    private static Map<String, Object> filter(Map<String, Object> values, Set<String> allowed) {
        Map<String, Object> clean = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            if (allowed.contains(k)) {
                clean.put(k, v);
            }
        });
        return clean;
    }
}
