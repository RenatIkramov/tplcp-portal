package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.EnumCodes;
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

    /**
     * Создаёт пустой черновик и возвращает его doc_id.
     *
     * <p>Дата создания — {@code now()}, а не {@code current_date}: с V62
     * это отметка времени с точностью до минуты. На складе за день
     * проходит несколько машин, и «в какой именно момент документ
     * появился» — не праздный вопрос при разборе расхождений.
     */
    public String createDraft(DocumentType type, String depositorId, String userId) {
        String sql = """
                INSERT INTO dbo.%s (document_no, depositor_id, status_id, doc_date,
                                    origin, author_id, created_by, created_at)
                VALUES (dbo.next_document_no(:type),
                        :depositorId::uuid,
                        (SELECT id FROM dbo.enum_status WHERE code = :draft),
                        now(), :origin, :userId::uuid, :userId::uuid, now())
                RETURNING doc_id::text
                """.formatted(type.headerTable());
        return jdbc.sql(sql)
                .param("type", type.path())
                .param("depositorId", depositorId)
                .param("draft", DocumentStatus.DRAFT)
                .param("origin", EnumCodes.Origin.PORTAL)
                .param("userId", userId)
                .query(String.class)
                .single();
    }

    /** Обновляет разрешённые поля шапки. */
    public void updateHeader(DocumentType type, String docId, Map<String, Object> values,
                             Set<String> allowed, String userId, boolean draftOnly) {
        Map<String, Object> clean = filter(values, allowed);
        if (clean.isEmpty()) {
            return;
        }
        // Значения приходят из JSON строками, поэтому типы указываем явно (SqlCast)
        String assignments = clean.keySet().stream()
                .map(SqlCast::assignment)
                .collect(Collectors.joining(", "));

        String draftCondition = draftOnly
                ? """
                  AND origin = :__origin
                  AND status_id = (SELECT id FROM dbo.enum_status WHERE code = :__draft)
                  """
                : "";

        String sql = """
                UPDATE dbo.%s SET %s, updated_by = :__userId::uuid, updated_at = now()
                WHERE doc_id = :__docId::uuid
                %s
                """.formatted(type.headerTable(), assignments, draftCondition);

        var spec = jdbc.sql(sql)
                .param("__docId", docId)
                .param("__userId", userId);
        if (draftOnly) {
            spec = spec.param("__draft", DocumentStatus.DRAFT)
                    .param("__origin", EnumCodes.Origin.PORTAL);
        }
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        spec.update();
    }

    public int deleteDraft(DocumentType type, String docId) {
        String sql = """
                DELETE FROM dbo.%s
                WHERE doc_id = :docId::uuid AND origin = :origin
                  AND status_id = (SELECT id FROM dbo.enum_status WHERE code = :draft)
                """.formatted(type.headerTable());
        return jdbc.sql(sql)
                .param("docId", docId)
                .param("draft", DocumentStatus.DRAFT)
                .param("origin", EnumCodes.Origin.PORTAL)
                .update();
    }

    public void deleteLines(DocumentType type, String docId, List<Integer> lineNumbers) {
        String sql = """
                DELETE FROM dbo.%s
                WHERE doc_id = :docId::uuid AND line_number IN (:lines)
                """.formatted(type.lineTable());
        jdbc.sql(sql)
                .param("docId", docId)
                .param("lines", lineNumbers)
                .update();
    }

    /** Удаляет весь состав документа — используется при замене состава целиком. */
    public void deleteAllLines(DocumentType type, String docId) {
        jdbc.sql("DELETE FROM dbo.%s WHERE doc_id = :docId::uuid".formatted(type.lineTable()))
                .param("docId", docId)
                .update();
    }

    public int nextLineNumber(DocumentType type, String docId) {
        String sql = "SELECT COALESCE(MAX(line_number), 0) + 1 FROM dbo.%s WHERE doc_id = :docId::uuid"
                .formatted(type.lineTable());
        return jdbc.sql(sql).param("docId", docId).query(Integer.class).single();
    }

    /** Добавляет строку состава: плановая запись без партии (решение Р1). */
    public void addLine(DocumentType type, String docId, int lineNumber, Map<String, Object> values) {
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
        spec.update();
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

    /** Переводит документ в статус по коду ({@link DocumentStatus}). */
    public void setStatus(DocumentType type, String docId, String statusCode, boolean markConfirmed) {
        String sql = """
                UPDATE dbo.%s
                   SET status_id = (SELECT id FROM dbo.enum_status WHERE code = :statusCode),
                       confirmed_at = CASE WHEN :markConfirmed THEN now() ELSE confirmed_at END,
                       updated_at = now()
                 WHERE doc_id = :docId::uuid
                """.formatted(type.headerTable());
        jdbc.sql(sql)
                .param("statusCode", statusCode)
                .param("markConfirmed", markConfirmed)
                .param("docId", docId)
                .update();
    }

    /**
     * Заполнен ли транспорт хоть одним полем. «Рейс» не в счёт: он живёт
     * и в шапке, и сам по себе транспортной сущности WMS не делает.
     */
    public boolean hasTransport(DocumentType type, String docId) {
        String columns = DocumentType.transportColumns().stream()
                .filter(c -> !"trip".equals(c))
                .sorted()
                .map(c -> "NULLIF(btrim(" + c + "::text), '')")
                .collect(Collectors.joining(", "));
        String sql = "SELECT num_nonnulls(%s) > 0 FROM dbo.%s WHERE doc_id = :docId::uuid"
                .formatted(columns, type.headerTable());
        return Boolean.TRUE.equals(jdbc.sql(sql).param("docId", docId)
                .query(Boolean.class).optional().orElse(false));
    }

    /** Чем кончилась передача транспорта в WMS (V84): код {@link EnumCodes.Transport}. */
    public void setTransportState(DocumentType type, String docId, String state, String error) {
        String sql = """
                UPDATE dbo.%s
                   SET transport_state = :state,
                       transport_sent_at = CASE WHEN :state = :sent THEN now() ELSE transport_sent_at END,
                       transport_error = CASE WHEN :state = :sent THEN NULL ELSE :error END
                 WHERE doc_id = :docId::uuid
                """.formatted(type.headerTable());
        jdbc.sql(sql)
                .param("state", state)
                .param("sent", EnumCodes.Transport.SENT)
                .param("error", error == null ? "без пояснения" : error)
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
                       h.transport_state,
                       (SELECT COUNT(DISTINCT line_number) FROM dbo.%s d WHERE d.doc_id = h.doc_id) AS line_count,
                       (SELECT COUNT(*) FROM dbo.%s d
                          JOIN dbo.sku s ON s.sku_id = d.sku_id
                         WHERE d.doc_id = h.doc_id AND NOT s.is_in_wms) AS unknown_sku_count,
                       (SELECT COUNT(*) FROM dbo.%s d
                         WHERE d.doc_id = h.doc_id AND d.batch_number IS NULL
                           AND (d.%s IS NULL OR d.%s <= 0)) AS bad_qty_count,
                       %s AS same_warehouse
                FROM dbo.%s h
                LEFT JOIN dbo.enum_status st ON st.id = h.status_id
                WHERE h.doc_id = :docId::uuid
                """.formatted(type.lineTable(), type.lineTable(), type.lineTable(),
                type.plannedQtyColumn(), type.plannedQtyColumn(), sameWarehouse(type),
                type.headerTable());
        List<Map<String, Object>> rows = jdbc.sql(sql).param("docId", docId).query().listOfRows();
        return rows.isEmpty() ? Map.of() : SqlRows.toCamelCase(rows).getFirst();
    }

    /**
     * Совпадают ли склады у перемещения.
     *
     * <p>Выражение подставляется в запрос, а не считается в Java, потому
     * что остальные признаки состояния документа считаются там же —
     * одним проходом по шапке. У поступлений и заказов второго склада
     * нет вовсе, поэтому для них это всегда «нет».
     */
    private static String sameWarehouse(DocumentType type) {
        return type == DocumentType.TRANSFERS
                ? "(h.from_warehouse_id IS NOT NULL AND h.from_warehouse_id = h.to_warehouse_id)"
                : "false";
    }

    /**
     * Какие обязательные поля шапки не заполнены — ПОДПИСЯМИ, а не именами
     * колонок базы.
     *
     * <p>Человеку показывали «не заполнены поля: to_warehouse_id, ship_date».
     * Имя колонки базы ему ничего не говорит и ни с чем на экране
     * не совпадает. Подпись берём там же, где её берёт сам экран, —
     * из справочника колонок: с переводом на язык пользователя и
     * с переименованием, если клиент назвал поле по-своему.
     *
     * <p>Если подписи вдруг не нашлось, показываем имя колонки: это хуже,
     * но честнее пустоты, и сразу видно, какое поле выпало из справочника.
     */
    public List<String> missingRequired(DocumentType type, String docId,
                                        String userId, String localeCode) {
        Map<String, String> required = type.requiredForConfirm();
        String sql = "SELECT %s FROM dbo.%s WHERE doc_id = :docId::uuid"
                .formatted(String.join(", ", required.keySet()), type.headerTable());
        List<Map<String, Object>> rows = jdbc.sql(sql).param("docId", docId).query().listOfRows();
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.getFirst();

        Map<String, String> labels = labels(type, userId, localeCode);
        List<String> missing = new ArrayList<>();
        required.forEach((column, columnId) -> {
            if (rows.isEmpty() || row.get(column) == null) {
                missing.add(labels.getOrDefault(columnId, column));
            }
        });
        return missing;
    }

    /** Подписи колонок таблицы так, как их видит этот пользователь. */
    private Map<String, String> labels(DocumentType type, String userId, String localeCode) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (userId == null || userId.isBlank()) {
            return out;
        }
        jdbc.sql("SELECT column_id, label FROM dbo.table_columns(:user::uuid, :locale::text,"
                        + " :table::text)")
                .param("user", userId)
                .param("locale", localeCode == null || localeCode.isBlank() ? "en" : localeCode)
                .param("table", type.path())
                .query()
                .listOfRows()
                .forEach(r -> out.put(String.valueOf(r.get("column_id")),
                        String.valueOf(r.get("label"))));
        return out;
    }

    /** Запись в журнал обменов — пока отправка в WMS заглушена (Р5). */
    public void logSync(DocumentType type, String depositorId, String status, String error) {
        jdbc.sql("""
                INSERT INTO dbo.sync_run (entity, depositor_id, direction, trigger_source,
                                          started_at, finished_at, status, rows_received,
                                          rows_applied, error_message)
                VALUES (:entity, :depositorId::uuid, :direction, 'manual',
                        now(), now(), :status, 1, :applied, :error)
                """)
                .param("entity", type.path())
                .param("depositorId", depositorId)
                .param("status", status)
                .param("direction", EnumCodes.SyncDirection.PORTAL_TO_WMS)
                .param("applied", EnumCodes.SyncStatus.SUCCESS.equals(status) ? 1 : 0)
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
