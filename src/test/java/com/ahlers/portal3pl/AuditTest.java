package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Журнал действий (V116): что пишут триггеры и что показывает история.
 *
 * <p>Изменения делаются прямо в базе, в транзакции, которая в конце
 * откатывается: в журнале и в документах заказчика ничего не остаётся.
 * Кто действует — {@code app.actor}, как его ставит пул соединений портала.
 */
class AuditTest extends PortalTestBase {

    /**
     * Каждая колонка журналируемой таблицы описана в audit_field.
     *
     * <p>Без этого новая колонка попадала бы в журнал под своим именем
     * из базы, а служебная (отметка времени, пересчитываемый итог) —
     * засоряла бы историю. Добавил колонку — реши здесь, что с ней делать.
     */
    @Test
    void everyAuditedColumnIsDescribed() {
        List<String> missing = jdbc.sql("""
                        SELECT c.table_name || '.' || c.column_name
                          FROM information_schema.columns c
                          JOIN pg_trigger t ON t.tgname = 'audit'
                                           AND t.tgrelid = ('dbo.' || c.table_name)::regclass
                          LEFT JOIN dbo.audit_field f ON f.source_table = c.table_name
                                                     AND f.column_name = c.column_name
                         WHERE c.table_schema = 'dbo' AND f.column_name IS NULL
                         ORDER BY 1
                        """)
                .query(String.class).list();
        assertTrue(missing.isEmpty(), "колонки журналируемых таблиц без строки в audit_field: " + missing);

        int tables = jdbc.sql("SELECT count(*) FROM pg_trigger WHERE tgname = 'audit'")
                .query(Integer.class).single();
        assertTrue(tables > 40, "триггеров журнала подозрительно мало: " + tables);
    }

    /** У каждой подписи поля журнала есть текст на русском и английском. */
    @Test
    void everyFieldLabelIsTranslated() {
        List<String> missing = jdbc.sql("""
                        SELECT DISTINCT f.text_key || ' ' || l.code
                          FROM dbo.audit_field f
                          CROSS JOIN dbo.dictionary_locale l
                         WHERE f.text_key IS NOT NULL AND l.code IN ('ru', 'en')
                           AND NOT EXISTS (SELECT 1 FROM dbo.ui_text t
                                            WHERE t.text_key = f.text_key AND t.locale_id = l.locale_id
                                              AND btrim(t.value) <> '')
                         ORDER BY 1
                        """)
                .query(String.class).list();
        assertTrue(missing.isEmpty(), "подписи полей журнала без перевода: " + missing);
    }

    /**
     * Шапка, состав (сохраняется целиком: удалить всё — вставить заново)
     * и статус документа; история своего клиента и пустая — для чужого.
     */
    @Test
    void historyShowsRealChangesOnly() {
        String depositor = depositorId(USER, DEPOSITOR);
        String other = depositorId(OTHER_USER, OTHER_DEPOSITOR);
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true), set_config('app.actor', :a, true),"
                            + " set_config('app.locale', 'ru', true)")
                    .param("d", depositor).param("a", USER).query().listOfRows();

            String docId = jdbc.sql("""
                            SELECT r.doc_id::text FROM dbo.receipts r
                             WHERE EXISTS (SELECT 1 FROM dbo.receipt_details d
                                            WHERE d.doc_id = r.doc_id AND d.batch_number IS NULL)
                             ORDER BY r.doc_id LIMIT 1
                            """)
                    .query(String.class).optional().orElse(null);
            assertTrue(docId != null, "у клиента нет поступления со строками — проверять нечего");
            // Документ мог уже меняться — другими проверками или руками. Смотрим
            // только события этой проверки: прежде прежние события документа
            // попадали в счёт, и проверка падала на базе, где его уже правили.
            java.util.Set<Object> earlier = history(docId).stream()
                    .map(r -> r.get("event_id")).collect(java.util.stream.Collectors.toSet());

            // шапка: одно поле
            jdbc.sql("UPDATE dbo.receipts SET comment = 'аудит-проверка' WHERE doc_id = :id::uuid")
                    .param("id", docId).update();
            // состав «как сохраняет портал»: удалить всё и вставить заново,
            // поменяв количество в одной строке
            jdbc.sql("""
                            CREATE TEMP TABLE audit_lines ON COMMIT DROP AS
                              SELECT * FROM dbo.receipt_details WHERE doc_id = :id::uuid
                            """).param("id", docId).update();
            int line = jdbc.sql("SELECT min(line_number) FROM audit_lines WHERE batch_number IS NULL")
                    .query(Integer.class).single();
            jdbc.sql("DELETE FROM dbo.receipt_details WHERE doc_id = :id::uuid").param("id", docId).update();
            jdbc.sql("UPDATE audit_lines SET expected_qty = expected_qty + 1"
                            + " WHERE line_number = :n AND batch_number IS NULL")
                    .param("n", line).update();
            jdbc.sql("INSERT INTO dbo.receipt_details SELECT * FROM audit_lines").update();
            // сохранение без изменений — не событие
            jdbc.sql("UPDATE dbo.receipts SET comment = comment WHERE doc_id = :id::uuid")
                    .param("id", docId).update();

            List<Map<String, Object>> rows = history(docId).stream()
                    .filter(r -> !earlier.contains(r.get("event_id"))).toList();
            assertEquals(2, rows.size(), "в истории не только два изменённых поля: " + rows);
            Map<String, Object> qty = rows.stream().filter(r -> "expected_qty".equals(r.get("field")))
                    .findFirst().orElseThrow(() -> new AssertionError("нет изменения количества: " + rows));
            assertEquals("line:" + line, qty.get("part"));
            assertEquals("update", qty.get("action"));
            assertTrue(rows.stream().anyMatch(r -> "comment".equals(r.get("field"))
                            && "аудит-проверка".equals(r.get("new_text"))),
                    "нет изменения комментария: " + rows);
            assertTrue(rows.stream().allMatch(r -> USER.equals(r.get("actor"))), "не тот, кто действовал: " + rows);

            // статус — отдельным событием, словами на языке читающего
            jdbc.sql("""
                            UPDATE dbo.receipts SET status_id =
                                   (SELECT id FROM dbo.enum_status WHERE id <> receipts.status_id ORDER BY id LIMIT 1)
                             WHERE doc_id = :id::uuid
                            """).param("id", docId).update();
            Map<String, Object> status = history(docId).stream()
                    .filter(r -> !earlier.contains(r.get("event_id")))
                    .filter(r -> "status_id".equals(r.get("field"))).findFirst()
                    .orElseThrow(() -> new AssertionError("смена статуса не записана"));
            assertEquals("status", status.get("action"));
            String newName = jdbc.sql("""
                            SELECT dbo.enum_name('enum_status', s.id::text, s.name)
                              FROM dbo.receipts r JOIN dbo.enum_status s ON s.id = r.status_id
                             WHERE r.doc_id = :id::uuid
                            """).param("id", docId).query(String.class).single();
            assertEquals(newName, status.get("new_text"), "статус в истории не словами");

            // без того, кто действует (миграция, psql), журнал не пишется
            int before = history(docId).size();
            jdbc.sql("SELECT set_config('app.actor', '', true)").query().listOfRows();
            jdbc.sql("UPDATE dbo.receipts SET comment = 'без автора' WHERE doc_id = :id::uuid")
                    .param("id", docId).update();
            assertEquals(before, history(docId).size(), "изменение без автора попало в журнал");

            // чужой клиент истории не видит
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true)").param("d", other).query().listOfRows();
            assertEquals(0, history(docId).size(), "история документа видна другому клиенту");
        });
    }

    private List<Map<String, Object>> history(String docId) {
        return jdbc.sql("SELECT * FROM dbo.audit_history('receipts', :id)")
                .param("id", docId).query().listOfRows();
    }
}
