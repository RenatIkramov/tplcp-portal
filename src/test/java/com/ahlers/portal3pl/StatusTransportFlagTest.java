package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import com.ahlers.portal3pl.i18n.EnumValueService;
import com.ahlers.portal3pl.i18n.EnumValueService.EnumValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Правка транспорта после отправки — галочкой у статуса (V150, решение
 * 21.11.2026).
 *
 * <p>Признак {@code allows_transport_edit} правится в окне значения
 * перечисления «Статусы», и портал проверяет его при правке транспорта.
 * Значение признака на базе — дело администратора: проверка его меняет
 * и возвращает, какой был, не полагаясь на умолчание.
 */
class StatusTransportFlagTest extends PortalTestBase {

    /** Поступление демо-данных в статусе 10 «Принят WMS». */
    private static final String RECEIPT = "ПН-000151";

    @Autowired
    EnumValueService values;
    @Autowired
    DraftService drafts;

    private Map<String, Boolean> before;

    @BeforeEach
    void rememberFlags() {
        before = new java.util.HashMap<>();
        owner().jdbc.sql("SELECT code, allows_transport_edit FROM dbo.enum_status").query()
                .listOfRows().forEach(r -> before.put((String) r.get("code"), (Boolean) r.get("allows_transport_edit")));
    }

    @AfterEach
    void restoreFlags() {
        before.forEach((code, on) -> setFlag(code, on));
    }

    private void setFlag(String code, boolean on) {
        owner().jdbc.sql("UPDATE dbo.enum_status SET allows_transport_edit = :on WHERE code = :code")
                .param("on", on).param("code", code).update();
    }

    private boolean flagOf(String code) {
        return owner().jdbc.sql("SELECT allows_transport_edit FROM dbo.enum_status WHERE code = :code")
                .param("code", code).query(Boolean.class).single();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> statusRow(PortalUser admin, String code) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) values.list(admin).get("rows");
        return rows.stream().filter(r -> "enum_status".equals(r.get("enumKey")) && code.equals(r.get("code")))
                .findFirst().orElseThrow(() -> new AssertionError("нет статуса " + code));
    }

    private static EnumValue same(Map<String, Object> row, Boolean flag) {
        return new EnumValue((String) row.get("code"), (String) row.get("name"), null,
                row.get("sortOrder") == null ? null : ((Number) row.get("sortOrder")).intValue(),
                (Boolean) row.get("isActive"), Map.of(), flag);
    }

    /** В окне статуса — галочка с подписью, и она сохраняется; без неё признак не трогается. */
    @Test
    @SuppressWarnings("unchecked")
    void flagIsOfferedAndSaved() {
        PortalUser admin = portalAdmin();
        Map<String, Object> status = ((List<Map<String, Object>>) values.list(admin).get("enums")).stream()
                .filter(e -> "enum_status".equals(e.get("enumKey"))).findFirst().orElseThrow();
        assertEquals("admin.label.allows_transport_edit", status.get("flagLabelKey"));
        assertNotNull(status.get("flagHintKey"), "у галочки нет подсказки");

        Map<String, Object> row = statusRow(admin, "30");
        boolean was = Boolean.TRUE.equals(row.get("flag"));
        values.save(admin, "enum_status", String.valueOf(row.get("valueId")), same(row, !was));
        assertEquals(!was, flagOf("30"), "галочка статуса не сохранилась");

        // окно без признака (старый клиент, другое перечисление) его не сбрасывает
        values.save(admin, "enum_status", String.valueOf(row.get("valueId")), same(row, null));
        assertEquals(!was, flagOf("30"), "сохранение без признака его сбросило");
        assertEquals(!was, statusRow(admin, "30").get("flag"));
    }

    /** Портал разрешает правку транспорта по галочке статуса документа. */
    @Test
    void transportFollowsTheFlag() {
        signIn();
        String docId = singleValue("SELECT doc_id::text FROM dbo.receipts WHERE document_no = '" + RECEIPT + "'");
        String status = singleValue("SELECT s.code FROM dbo.receipts r JOIN dbo.enum_status s ON s.id = r.status_id"
                + " WHERE r.document_no = '" + RECEIPT + "'");

        String car = owner().jdbc.sql("SELECT car_number FROM dbo.receipts WHERE doc_id = :id::uuid")
                .param("id", docId).query(String.class).optional().orElse(null);
        try {
            checkTransport(docId, status);
        } finally {
            // номер машины демо-документа — как был (владельцем: в журнал не пишется)
            owner().jdbc.sql("UPDATE dbo.receipts SET car_number = :car WHERE doc_id = :id::uuid")
                    .param("car", car).param("id", docId).update();
        }
    }

    private void checkTransport(String docId, String status) {
        setFlag(status, false);
        expectRejection(409, () -> drafts.updateTransport(DocumentType.RECEIPTS, docId,
                Map.of("car_number", "111 QA 02")));

        setFlag(status, true);
        drafts.updateTransport(DocumentType.RECEIPTS, docId, Map.of("car_number", "222 QA 02"));
        assertEquals("222 QA 02", singleValue("SELECT car_number FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid"));
    }
}
