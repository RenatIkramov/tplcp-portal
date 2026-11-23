package com.ahlers.portal3pl.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Очередь отправки документов в WMS (аудит 11.11.2026, A1; V127).
 *
 * <p>Задание кладёт «Подтвердить» — в своей транзакции, вместе со статусом
 * «Отправляется». Отправляет {@link #deliver} — вне транзакции: соединение
 * с базой не ждёт ответа WMS, а сбой записи после ответа не потеряет
 * задание. Итог (принят / повторить / ошибка) пишет одна функция базы
 * {@code wms_outbox_finish}.
 *
 * <p>Первая попытка — сразу, в том же запросе «Подтвердить»: человек видит
 * «Отправлен в WMS» без ожидания. Остальные — {@link #retryDue} раз в
 * минуту; после 15-й неудачи — «Ошибка отправки» (решение заказчика 12.11.2026).
 */
@Component
public class WmsOutbox {

    private static final Logger log = LoggerFactory.getLogger(WmsOutbox.class);

    private final JdbcClient jdbc;
    private final WmsGateway wms;
    private final TransactionTemplate tx;

    public WmsOutbox(JdbcClient jdbc, WmsGateway wms, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.wms = wms;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Положить задание. Зовётся внутри транзакции «Подтвердить»: задание и
     * статус «Отправляется» появляются вместе или не появляются вовсе.
     * Следующая попытка по расписанию — через минуту: первую делает сам
     * запрос, и расписание не должно отправить тот же документ параллельно.
     */
    public long enqueue(DocumentType type, String docId, String depositorId, boolean withTransport) {
        return jdbc.sql("""
                        INSERT INTO dbo.wms_outbox (doc_type, doc_id, depositor_id, with_transport, next_attempt_at)
                        VALUES (:type, :doc::uuid, :dep::uuid, :tr, now() + interval '1 minute')
                        RETURNING outbox_id
                        """)
                .param("type", type.path())
                .param("doc", docId)
                .param("dep", depositorId)
                .param("tr", withTransport)
                .query(Long.class).single();
    }

    /**
     * Одна попытка: отправить документ (и транспорт вслед за ним) и записать
     * итог. Вне транзакции — её открывает только запись итога.
     *
     * @return статус документа после попытки ({@link DocumentStatus}),
     *         {@code null} — задание уже завершено
     */
    public String deliver(long outboxId) {
        Map<String, Object> job = tx.execute(s -> jdbc.sql("""
                        SELECT doc_type, doc_id::text AS doc_id, with_transport
                          FROM dbo.wms_outbox WHERE outbox_id = :id AND state = 'pending'
                        """)
                .param("id", outboxId).query().listOfRows().stream().findFirst().orElse(null));
        if (job == null) {
            return null;
        }
        DocumentType type = DocumentType.ofPath((String) job.get("doc_type"));
        String docId = (String) job.get("doc_id");
        String error = attempt(() -> wms.sendDocument(type, docId));
        String transportError = error == null && Boolean.TRUE.equals(job.get("with_transport"))
                ? attempt(() -> wms.sendTransport(type, docId)) : null;
        String status = tx.execute(s -> jdbc.sql("SELECT dbo.wms_outbox_finish(:id, :e, :te)")
                .param("id", outboxId).param("e", error).param("te", transportError)
                .query(String.class).optional().orElse(null));
        if (error != null) {
            log.warn("WMS не принял документ {} ({}): {}; статус {}", docId, type.path(), error, status);
        }
        return status;
    }

    /** Повтор заданий, которым пора. Сбой одного не мешает остальным. */
    @Scheduled(fixedDelayString = "${portal.wms.retry-ms:60000}",
               initialDelayString = "${portal.wms.retry-ms:60000}")
    public void retryDue() {
        List<Long> due = tx.execute(s -> jdbc.sql("SELECT dbo.wms_outbox_due()").query(Long.class).list());
        for (Long id : due) {
            try {
                deliver(id);
            } catch (RuntimeException e) {
                log.error("Повтор отправки в WMS, задание {}", id, e);
            }
        }
    }

    /** Текст ошибки вызова WMS или {@code null}, если вызов прошёл. */
    private static String attempt(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        }
    }
}
