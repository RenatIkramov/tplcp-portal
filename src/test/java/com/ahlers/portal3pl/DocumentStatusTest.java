package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.lists.ListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Статусы документов — один общий список (V87).
 *
 * <p>Фильтр статуса у каждого документа предлагает только те статусы,
 * что у него бывают (колонка «Где» согласованной таблицы), значение
 * фильтра — код, подпись — на языке пользователя.
 */
class DocumentStatusTest extends PortalTestBase {

    @Autowired
    ListService lists;

    @SuppressWarnings("unchecked")
    private Map<String, String> statusOptions(PortalUser user, DocumentType type) {
        signIn();
        Map<String, Object> filter = lists.filters(user, type).stream()
                .filter(f -> "status".equals(f.get("columnId")))
                .findFirst().orElseThrow();
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> o : (List<Map<String, Object>>) filter.get("options")) {
            out.put(String.valueOf(o.get("value")), String.valueOf(o.get("label")));
        }
        return out;
    }

    @Test
    void eachDocumentOffersOnlyItsOwnStatuses() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertEquals(List.of("01", "02", "03", "04", "10", "30", "60", "61", "90"),
                List.copyOf(statusOptions(user, DocumentType.RECEIPTS).keySet()));
        assertEquals(List.of("01", "02", "03", "04", "10", "20", "21", "30", "40", "50", "51", "90"),
                List.copyOf(statusOptions(user, DocumentType.ORDERS).keySet()));
        assertEquals(List.of("01", "02", "03", "04", "10", "20", "21", "30", "40", "50", "60", "61", "90"),
                List.copyOf(statusOptions(user, DocumentType.TRANSFERS).keySet()));
    }

    @Test
    void statusNamesFollowTheUserLanguage() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertEquals("Draft", statusOptions(user, DocumentType.ORDERS).get("01"),
                "язык не выбран — английский, язык портала");
        localeOf(user, "ru");
        Map<String, String> ru = statusOptions(user, DocumentType.ORDERS);
        assertEquals("Черновик", ru.get("01"));
        assertEquals("Готов к отгрузке", ru.get("40"));
    }

    /** Один код — один статус: прежних трёх «Черновиков» быть не должно. */
    @Test
    void everyCodeIsOneStatus() {
        assertEquals("0", singleValue("SELECT count(*)::text FROM (SELECT code FROM dbo.enum_status"
                + " GROUP BY code HAVING count(*) > 1) d"));
        assertEquals("10", singleValue("SELECT string_agg(code, ',') FROM dbo.enum_status"
                + " WHERE allows_transport_edit"), "правка транспорта после отправки — только в 10");
    }

    /** Статусы товара и счёта — свои перечисления, переведённые. */
    @Test
    void itemAndInvoiceStatusesAreSeparate() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signIn();
        List<String> names = new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(USER, DEPOSITOR)).query(String.class).single();
            return jdbc.sql("SELECT DISTINCT status_name FROM dbo.v_sku_full WHERE status_name IS NOT NULL"
                    + " UNION SELECT DISTINCT status_name FROM dbo.v_invoices").query(String.class).list();
        });
        assertFalse(names.isEmpty());
        assertTrue(List.of("Активен", "Неактивен", "Ожидает оплаты", "Оплачен", "Просрочен")
                .containsAll(names), names.toString());
    }
}
