package com.ahlers.portal3pl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Права и скрытые разделы проверяет сервер, а не только экран
 * (аудит 11.11.2026, S3; ТЗ §6.3).
 *
 * <p>НАЙДЕНО АУДИТОМ. Снятая галочка «может создавать заказы» прятала
 * кнопку, а {@code POST /api/orders} создавал черновик (её проверяла
 * только загрузка из Excel). Скрытые «Биллинг» и «Перемещения» прятали
 * вкладку, а счета, тарифы и перемещения отдавались по адресу.
 *
 * <p>Личные права клиента базовый класс возвращает после теста сам
 * (PersonalSettings); настройку поклажедателя — этот тест.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectionAccessTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private Runnable restoreDepositor = () -> { };

    @AfterEach
    void restore() {
        restoreDepositor.run();
        // под контекстом клиента: таблица под RLS, без него не удалилось бы ничего
        inContext("DELETE FROM dbo.orders WHERE erp_number LIKE 'QA-SA-%'");
    }

    private HttpClient signedIn() throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> answer = send(client, "POST", "/api/auth/login",
                "{\"login\":\"" + USER + "\",\"password\":\"" + USER_PASSWORD + "\"}");
        assertEquals(200, answer.statusCode(), "вход не удался: " + answer.body());
        return client;
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (json == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void userSetting(String column, boolean value) {
        jdbc.sql("UPDATE dbo.user_settings SET " + column + " = :v WHERE user_id = :u::uuid")
                .param("v", value).param("u", userId(USER)).update();
    }

    /** Галочка «может создавать заказы» закрывает и создание адресом. */
    @Test
    void createRightIsCheckedByServer() throws Exception {
        HttpClient client = signedIn();
        String body = "{\"erp_number\":\"QA-SA-" + System.nanoTime() + "\"}";

        userSetting("can_create_orders", false);
        HttpResponse<String> refused = send(client, "POST", "/api/orders", body);
        assertEquals(403, refused.statusCode(), "черновик создан при снятой галочке: " + refused.body());
        assertTrue(refused.body().contains("error.order.create_disabled"), refused.body());

        userSetting("can_create_orders", true);
        HttpResponse<String> allowed = send(client, "POST", "/api/orders",
                "{\"erp_number\":\"QA-SA-" + System.nanoTime() + "\"}");
        assertEquals(2, allowed.statusCode() / 100, "с галочкой создать нельзя: " + allowed.body());
    }

    /** Скрытый «Биллинг» — ни счетов, ни тарифов по адресу. */
    @Test
    void hiddenBillingIsClosed() throws Exception {
        HttpClient client = signedIn();
        userSetting("hide_billing_tab", true);
        for (String path : new String[]{"/api/invoices", "/api/service-tariffs"}) {
            HttpResponse<String> answer = send(client, "GET", path, null);
            assertEquals(403, answer.statusCode(), path + ": скрытый раздел отдан");
            assertTrue(answer.body().contains("error.access.section_hidden"), answer.body());
        }
        userSetting("hide_billing_tab", false);
        assertEquals(200, send(client, "GET", "/api/invoices", null).statusCode());
        assertEquals(200, send(client, "GET", "/api/service-tariffs", null).statusCode());
    }

    /** Скрытые у клиента «Перемещения» — ни списка, ни шаблона по адресу. */
    @Test
    void hiddenTransfersAreClosed() throws Exception {
        restoreDepositor = keepDepositorSetup(DEPOSITOR);
        HttpClient client = signedIn();
        inContext("INSERT INTO dbo.depositor_settings (depositor_id, hide_transfers_tab)"
                + " VALUES (current_setting('app.depositor_id')::uuid, true)"
                + " ON CONFLICT (depositor_id) DO UPDATE SET hide_transfers_tab = true");
        for (String path : new String[]{"/api/transfers", "/api/transfers/excel-template"}) {
            HttpResponse<String> answer = send(client, "GET", path, null);
            assertEquals(403, answer.statusCode(), path + ": скрытый раздел отдан");
            assertTrue(answer.body().contains("error.access.section_hidden"), answer.body());
        }
        inContext("UPDATE dbo.depositor_settings SET hide_transfers_tab = false"
                + " WHERE depositor_id = current_setting('app.depositor_id')::uuid");
        assertEquals(200, send(client, "GET", "/api/transfers", null).statusCode());
    }
}
