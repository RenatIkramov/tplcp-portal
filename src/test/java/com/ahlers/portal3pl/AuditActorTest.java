package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Журнал действий помнит человека, а не логин (аудит 11.11.2026, A8; V126).
 *
 * <p>НАЙДЕНО АУДИТОМ. В журнал писался логин. После смены логина записи
 * человека оставались под старым: имя у них пропадало, а отбор
 * «Пользователь» по новому логину их не находил.
 *
 * <p>Всё — в одной транзакции с откатом: логин клиента после проверки
 * прежний, записей журнала не остаётся.
 */
class AuditActorTest extends PortalTestBase {

    @Test
    void renamedUserKeepsHistory() {
        String depositor = depositorId(USER, DEPOSITOR);
        String renamed = "renamed-" + System.nanoTime() + "@example.com";
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.sql("SELECT set_config('app.depositor_id', :d, true), set_config('app.actor', :a, true),"
                            + " set_config('app.locale', 'ru', true)")
                    .param("d", depositor).param("a", USER).query().listOfRows();
            String docId = jdbc.sql("SELECT doc_id::text FROM dbo.receipts ORDER BY doc_id LIMIT 1")
                    .query(String.class).optional().orElse(null);
            assertTrue(docId != null, "у клиента нет поступлений — проверять нечего");
            String mark = "кто-это-" + System.nanoTime();
            jdbc.sql("UPDATE dbo.receipts SET comment = :c WHERE doc_id = :id::uuid")
                    .param("c", mark).param("id", docId).update();

            // логин сменили — администратор, уже после правки
            jdbc.sql("SELECT set_config('app.actor', '', true)").query().listOfRows();
            // функцией, как это делает карточка пользователя: напрямую портал
            // в app_user больше не пишет (V133), а откат должен вернуть логин
            jdbc.sql("SELECT dbo.admin_change_login(:id::uuid, :n)")
                    .param("id", userId(USER)).param("n", renamed).query().listOfRows();

            String found = jdbc.sql("SELECT dbo.admin_audit_log(:d::uuid, :a, 'receipts', NULL, NULL, 1, 200)::text")
                    .param("d", depositor).param("a", renamed).query(String.class).single();
            assertTrue(found.contains(mark), "по новому логину правка не находится: " + found);
            assertTrue(found.contains("\"actor\": \"" + renamed + "\""), "правка показана под старым логином");
            String name = jdbc.sql("SELECT full_name FROM dbo.app_user WHERE login = :l")
                    .param("l", renamed).query(String.class).single();
            assertTrue(found.contains("\"actorName\": \"" + name + "\""), "у правки пропало имя: " + found);

            List<String> actors = jdbc.sql("SELECT actor FROM dbo.admin_audit_actors()")
                    .query(String.class).list();
            assertTrue(actors.contains(renamed), "в отборе нет нового логина");
            assertFalse(actors.contains(USER), "в отборе остался старый логин — человек разделён надвое");

            List<String> history = jdbc.sql("SELECT actor_name FROM dbo.audit_history('receipts', :id)"
                            + " WHERE new_text = :c")
                    .param("id", docId).param("c", mark).query(String.class).list();
            assertEquals(List.of(name), history, "история документа без имени");
        });
    }
}
