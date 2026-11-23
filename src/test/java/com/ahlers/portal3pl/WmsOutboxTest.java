package com.ahlers.portal3pl;

import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.documents.WmsOutbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Отправка документа в WMS через очередь (аудит 11.11.2026, A1; V127).
 *
 * <p>НАЙДЕНО АУДИТОМ. «Подтвердить» звал WMS внутри транзакции базы:
 * два одновременных нажатия отправляли документ дважды, документ с
 * ошибкой отправки застревал без повтора, а неответ WMS сразу был
 * ошибкой. Теперь — очередь: статус «Отправляется», повтор раз в минуту,
 * после 15-й неудачи «Ошибка отправки» и кнопка «Отправить ещё раз».
 *
 * <p>Неудачу WMS изображает подменённый {@link WmsGateway} — заглушка не
 * падает никогда. Подмена заводит свой контекст Spring, поэтому отдельный
 * класс, как {@code TransportDeliveryTest}.
 */
class WmsOutboxTest extends PortalTestBase {

    @Autowired
    DraftService drafts;

    @Autowired
    WmsOutbox outbox;

    @MockitoSpyBean
    WmsGateway wms;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeCreated() {
        doCallRealMethod().when(wms).sendDocument(any(DocumentType.class), anyString());
        for (String docId : created) {
            // задания очереди закрываем: иначе расписание повторяло бы их и после проверки
            jdbc.sql("SELECT dbo.wms_outbox_finish(outbox_id, NULL, NULL) FROM dbo.wms_outbox"
                    + " WHERE doc_id = :d::uuid AND state = 'pending'").param("d", docId).query().listOfRows();
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> header(Map<String, Object> card) {
        return (Map<String, Object>) card.get("header");
    }

    private String readyDraft() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String supplierId = singleValue("SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("warehouse_id", "WH-ALA");
        head.put("eta_date", "2026-11-05");
        head.put("supplier_id", supplierId);
        String docId = header(drafts.create(DocumentType.RECEIPTS, withErp(head))).get("docId").toString();
        created.add(docId);
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", skuId, "expected_qty", 5));
        return docId;
    }

    private String status(String docId) {
        return singleValue("SELECT st.code FROM dbo.receipts r JOIN dbo.enum_status st ON st.id = r.status_id"
                + " WHERE r.doc_id = '" + docId + "'");
    }

    private Map<String, Object> job(String docId) {
        return jdbc.sql("SELECT outbox_id, state, attempts, last_error FROM dbo.wms_outbox"
                        + " WHERE doc_id = :d::uuid ORDER BY outbox_id DESC LIMIT 1")
                .param("d", docId).query().singleRow();
    }

    /** WMS ответил сразу — «Отправлен в WMS», задание закрыто с первой попытки. */
    @Test
    void answeredAtOnce() {
        String docId = readyDraft();
        assertEquals("02", header(drafts.confirm(DocumentType.RECEIPTS, docId)).get("statusCode"));
        Map<String, Object> job = job(docId);
        assertEquals("done", job.get("state"));
        assertEquals(1, ((Number) job.get("attempts")).intValue());
    }

    /**
     * WMS молчит — «Отправляется»; повтор по расписанию не раньше минуты;
     * после 15-й неудачи — «Ошибка отправки»; «Отправить ещё раз» — снова
     * в очередь, и WMS, ожив, принимает документ.
     */
    @Test
    void silentWmsIsRetriedThenFailsThenCanBeResent() {
        String docId = readyDraft();
        doThrow(new IllegalStateException("WMS не отвечает"))
                .when(wms).sendDocument(any(DocumentType.class), anyString());

        assertEquals("04", header(drafts.confirm(DocumentType.RECEIPTS, docId)).get("statusCode"),
                "неответ WMS сразу стал ошибкой");
        Map<String, Object> job = job(docId);
        assertEquals("pending", job.get("state"));
        assertTrue(String.valueOf(job.get("last_error")).contains("WMS не отвечает"));

        outbox.retryDue();   // следующая попытка — через минуту, сейчас рано
        assertEquals(1, ((Number) job(docId).get("attempts")).intValue(), "повтор раньше срока");
        expectRejection(409, () -> drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of("comment", "x")));

        long id = ((Number) job.get("outbox_id")).longValue();
        for (int attempt = 2; attempt < 15; attempt++) {
            assertEquals("04", outbox.deliver(id), "попытка " + attempt);
        }
        assertEquals("03", outbox.deliver(id), "после 15-й неудачи документ не в ошибке");
        assertEquals("failed", job(docId).get("state"));

        doCallRealMethod().when(wms).sendDocument(any(DocumentType.class), anyString());
        assertEquals("02", header(drafts.resend(DocumentType.RECEIPTS, docId)).get("statusCode"));
        assertEquals("done", job(docId).get("state"));
        expectRejection(409, () -> drafts.resend(DocumentType.RECEIPTS, docId));
    }

    /** Два «Подтвердить» одновременно — в WMS уходит одна отправка, второе получает отказ. */
    @Test
    void doubleConfirmSendsOnce() throws Exception {
        String docId = readyDraft();
        clearInvocations(wms);
        doAnswer(call -> {
            Thread.sleep(300);   // WMS думает — второе нажатие успевает прийти
            return call.callRealMethod();
        }).when(wms).sendDocument(any(DocumentType.class), anyString());

        Callable<String> press = () -> {
            signIn();
            try {
                return String.valueOf(header(drafts.confirm(DocumentType.RECEIPTS, docId)).get("statusCode"));
            } catch (ResponseStatusException e) {
                return String.valueOf(e.getStatusCode().value());
            }
        };
        try (ExecutorService two = Executors.newFixedThreadPool(2)) {
            Future<String> a = two.submit(press);
            Future<String> b = two.submit(press);
            List<String> answers = new ArrayList<>(List.of(a.get(), b.get()));
            answers.sort(String::compareTo);
            assertEquals(List.of("02", "409"), answers, "ответы двух нажатий");
        }
        verify(wms, times(1)).sendDocument(eq(DocumentType.RECEIPTS), eq(docId));
    }
}
