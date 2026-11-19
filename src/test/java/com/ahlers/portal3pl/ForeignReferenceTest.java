package com.ahlers.portal3pl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Документ ссылается только на записи своего клиента (аудит 11.11.2026,
 * S14; V128).
 *
 * <p>НАЙДЕНО АУДИТОМ. Черновик принимал идентификатор товара, поставщика,
 * грузополучателя, места доставки как есть — и чужой тоже: внешний ключ
 * RLS не проверяет. Экран чужих идентификаторов не показывает, поэтому
 * запросы здесь — в обход экрана, настоящие HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForeignReferenceTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> created = new ArrayList<>();
    private HttpClient client;

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void signedIn() throws Exception {
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        assertEquals(200, send("POST", "/api/auth/login",
                Map.of("login", USER, "password", USER_PASSWORD)).statusCode());
    }

    /** Значение из справочников ДРУГОГО клиента. */
    private String othersValue(String sql) {
        return new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(OTHER_USER, OTHER_DEPOSITOR)).query(String.class).single();
            return jdbc.sql(sql).query(String.class).list().getFirst();
        });
    }

    @SuppressWarnings("unchecked")
    private String draft(String type) throws Exception {
        HttpResponse<String> answer = send("POST", "/api/" + type, withErp(Map.of()));
        assertEquals(201, answer.statusCode(), answer.body());
        String docId = ((Map<String, Object>) json.readValue(answer.body(), Map.class).get("header"))
                .get("docId").toString();
        created.add(type + "/" + docId);
        return docId;
    }

    private void refused(HttpResponse<String> answer, String what) {
        assertEquals(400, answer.statusCode(), what + " другого клиента принят: " + answer.body());
        String text = new TransactionTemplate(txManager).execute(tx -> jdbc.sql(
                        "SELECT value FROM dbo.ui_text WHERE text_key = 'error.document.foreign_reference'")
                .query(String.class).list()).stream().filter(answer.body()::contains).findFirst().orElse(null);
        assertTrue(text != null, "отказ без понятного текста: " + answer.body());
    }

    @Test
    void othersSkuInLines() throws Exception {
        signedIn();
        String docId = draft("receipts");
        String own = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String others = othersValue("SELECT sku_id::text FROM dbo.sku ORDER BY code");
        refused(send("PUT", "/api/receipts/" + docId + "/lines",
                List.of(Map.of("sku_id", own, "expected_qty", 1), Map.of("sku_id", others, "expected_qty", 1))),
                "товар");
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.receipt_details WHERE doc_id = '" + docId + "'"),
                "состав записан наполовину");
        assertEquals(200, send("PUT", "/api/receipts/" + docId + "/lines",
                List.of(Map.of("sku_id", own, "expected_qty", 1))).statusCode(), "свой товар не принят");
    }

    @Test
    void othersSupplier() throws Exception {
        signedIn();
        String docId = draft("receipts");
        refused(send("PUT", "/api/receipts/" + docId,
                Map.of("supplier_id", othersValue("SELECT supplier_id::text FROM dbo.supplier ORDER BY code"))),
                "поставщик");
    }

    @Test
    void othersConsigneeAndDeliveryPoint() throws Exception {
        signedIn();
        String docId = draft("orders");
        refused(send("PUT", "/api/orders/" + docId,
                Map.of("consignee_id", othersValue("SELECT consignee_id::text FROM dbo.consignee ORDER BY code"))),
                "грузополучатель");
        refused(send("PUT", "/api/orders/" + docId, Map.of("delivery_point_id",
                        othersValue("SELECT address_id::text FROM dbo.delivery_point ORDER BY 1"))),
                "место доставки");
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (String doc : created) {
            send("DELETE", "/api/" + doc, null);
        }
    }
}
