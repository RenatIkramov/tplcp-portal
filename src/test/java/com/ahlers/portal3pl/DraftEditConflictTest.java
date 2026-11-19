package com.ahlers.portal3pl;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Одновременная правка черновика (аудит 11.11.2026, A9; V125).
 *
 * <p>НАЙДЕНО АУДИТОМ. Двое открыли один черновик — сохранялся последний,
 * и его состав молча заменял состав первого. Теперь экран присылает
 * версию, которую видел ({@code If-Match}), и устаревшая запись получает
 * 409 без изменений в документе.
 *
 * <p>Запросы — настоящие HTTP: версия едет в заголовке запроса.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DraftEditConflictTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> created = new ArrayList<>();
    private HttpClient client;

    private HttpResponse<String> send(String method, String path, Object body, String version) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (version != null) {
            b.header("If-Match", "\"" + version + "\"");
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void signedIn() throws Exception {
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> answer = send("POST", "/api/auth/login",
                Map.of("login", USER, "password", USER_PASSWORD), null);
        assertEquals(200, answer.statusCode(), "вход не удался: " + answer.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> card(HttpResponse<String> answer) throws Exception {
        assertTrue(answer.statusCode() < 300, answer.statusCode() + ": " + answer.body());
        return json.readValue(answer.body(), Map.class);
    }

    private static String version(Map<String, Object> card) {
        Object v = card.get("version");
        assertNotNull(v, "карточка без версии");
        return v.toString();
    }

    /** Черновик поступления с одной строкой; возвращает карточку. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> newDraft() throws Exception {
        Map<String, Object> card = card(send("POST", "/api/receipts", withErp(Map.of()), null));
        String docId = ((Map<String, Object>) card.get("header")).get("docId").toString();
        created.add(docId);
        return card(send("PUT", "/api/receipts/" + docId + "/lines", List.of(line(10)), version(card)));
    }

    private Map<String, Object> line(int qty) {
        return Map.of("sku_id", singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'"),
                "expected_qty", qty);
    }

    @SuppressWarnings("unchecked")
    private static String docId(Map<String, Object> card) {
        return ((Map<String, Object>) card.get("header")).get("docId").toString();
    }

    private String plannedQty(String docId) {
        return singleValue("SELECT string_agg(expected_qty::int::text, ',' ORDER BY line_number)"
                + " FROM dbo.receipt_details WHERE doc_id = '" + docId + "'");
    }

    /** Второй сохраняет состав по старой версии — отказ, состав первого на месте. */
    @Test
    void staleLinesAreRefused() throws Exception {
        signedIn();
        Map<String, Object> opened = newDraft();
        String docId = docId(opened);
        String seenByBoth = version(opened);

        Map<String, Object> first = card(send("PUT", "/api/receipts/" + docId + "/lines",
                List.of(line(20)), seenByBoth));
        assertNotEquals(seenByBoth, version(first), "правка состава не сменила версию");

        HttpResponse<String> second = send("PUT", "/api/receipts/" + docId + "/lines",
                List.of(line(30)), seenByBoth);
        assertEquals(409, second.statusCode(), "устаревший состав записан: " + second.body());
        assertTrue(second.body().contains("error.document.changed_by_other"), second.body());
        assertEquals("20", plannedQty(docId), "чужой состав затёр первый");
    }

    /** Шапка и состав одного сохранения: состав едет с версией, которую выдала шапка. */
    @Test
    void headerThenLinesIsOneSave() throws Exception {
        signedIn();
        Map<String, Object> opened = newDraft();
        String docId = docId(opened);
        Map<String, Object> header = card(send("PUT", "/api/receipts/" + docId,
                Map.of("comment", "первое сохранение"), version(opened)));
        card(send("PUT", "/api/receipts/" + docId + "/lines", List.of(line(40)), version(header)));
        assertEquals("40", plannedQty(docId));

        // шапка по версии до этого сохранения — отказ
        HttpResponse<String> stale = send("PUT", "/api/receipts/" + docId,
                Map.of("comment", "чужое"), version(opened));
        assertEquals(409, stale.statusCode(), stale.body());
        assertEquals("первое сохранение", singleValue(
                "SELECT comment FROM dbo.receipts WHERE doc_id = '" + docId + "'"));
    }

    /** Удалить и подтвердить устаревший черновик тоже нельзя. */
    @Test
    void staleDeleteAndConfirmAreRefused() throws Exception {
        signedIn();
        Map<String, Object> opened = newDraft();
        String docId = docId(opened);
        card(send("PUT", "/api/receipts/" + docId, Map.of("comment", "правка"), version(opened)));

        assertEquals(409, send("DELETE", "/api/receipts/" + docId, null, version(opened)).statusCode());
        assertEquals(409, send("POST", "/api/receipts/" + docId + "/confirm", null, version(opened)).statusCode());
        assertEquals("01", singleValue("SELECT st.code FROM dbo.receipts r JOIN dbo.enum_status st"
                + " ON st.id = r.status_id WHERE r.doc_id = '" + docId + "'"), "документ ушёл со старой версией");
    }

    /** Без версии — как раньше: загрузка Excel и прочие вызовы без экрана не сверяют. */
    @Test
    void withoutVersionNothingChanges() throws Exception {
        signedIn();
        String docId = docId(newDraft());
        assertEquals(200, send("PUT", "/api/receipts/" + docId + "/lines", List.of(line(50)), null).statusCode());
        assertEquals("50", plannedQty(docId));
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (String docId : created) {
            send("DELETE", "/api/receipts/" + docId, null, null);
        }
    }
}
