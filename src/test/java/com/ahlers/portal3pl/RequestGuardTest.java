package com.ahlers.portal3pl;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки входа нельзя обойти записью адреса, а неверный запрос — не сбой
 * (аудит 11.11.2026, S2 и S6).
 *
 * <p>НАЙДЕНО АУДИТОМ. Фильтр входа сравнивал сырой адрес с «/api/», а Spring
 * выбирает обработчик по разобранному: {@code /%61pi/me} и {@code /api;x/me}
 * фильтр считал «не API» и пропускал, Spring отдавал тем же контроллерам.
 * Без входа так отдавались справочники шапки документа, а правило «до смены
 * выданного пароля — ничего» обходилось целиком.
 *
 * <p>И второе: неизвестный адрес, не тот метод, кривой идентификатор
 * отвечали 500 со стеком в журнале — любой сканер заливал журнал
 * «внутренними ошибками», и настоящие в нём тонули.
 *
 * <p>Запросы — настоящие HTTP, как их шлёт браузер: фильтр и разбор адреса
 * работают только на этом пути.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestGuardTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private int status(HttpClient client, String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String body(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpClient signedIn(String login, String password) throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> answer = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, answer.statusCode(), "вход не удался: " + answer.body());
        return client;
    }

    /** Закодированная буква и параметр пути не уводят запрос мимо проверки входа. */
    @Test
    void disguisedAddressStillNeedsSignIn() throws Exception {
        HttpClient anonymous = HttpClient.newHttpClient();
        List<String> leaked = new ArrayList<>();
        for (String path : List.of("/%61pi/me", "/api;x=1/me", "/%61pi/document-dictionaries",
                "/api;x/document-dictionaries", "/api/document-dictionaries", "/%61pi/stock",
                "/%61pi/admin/users")) {
            int code = status(anonymous, "GET", path);
            if (code != 401) {
                leaked.add(path + " → " + code);
            }
        }
        assertTrue(leaked.isEmpty(), "без входа отвечают не 401: " + leaked);
    }

    /** С выданным паролем — только смена пароля, как бы ни был записан адрес. */
    @Test
    void disguisedAddressStillNeedsPasswordChange() throws Exception {
        String uid = userId(USER);
        owner().jdbc.sql("UPDATE dbo.app_user SET must_change_password = true WHERE user_id = :u::uuid")
                .param("u", uid).update();
        try {
            HttpClient client = signedIn(USER, USER_PASSWORD);
            for (String path : List.of("/api/stock", "/%61pi/stock", "/api;x/stock",
                    "/%61pi/sku/dictionaries")) {
                assertEquals(403, status(client, "GET", path), path + ": данные отданы до смены пароля");
                assertTrue(body(client, path).contains("mustChangePassword"),
                        path + ": отказ не про смену пароля");
            }
        } finally {
            owner().jdbc.sql("UPDATE dbo.app_user SET must_change_password = false WHERE user_id = :u::uuid")
                    .param("u", uid).update();
        }
    }

    /**
     * Администрирование закрыто для клиента ещё до метода: прежде отказ давал
     * сам метод, и запрос без нужного параметра падал раньше проверки — 500
     * вместо 403.
     */
    @Test
    void adminAddressesAreClosedForClient() throws Exception {
        HttpClient client = signedIn(USER, USER_PASSWORD);
        for (String path : List.of("/api/admin/field-labels", "/api/admin/depositor-columns",
                "/api/admin/users", "/%61pi/admin/users", "/api/admin/nonexistent")) {
            assertEquals(403, status(client, "GET", path), path);
        }
    }

    /** Неверный запрос — ответ 4xx, а не «внутренняя ошибка». */
    @Test
    void badRequestsAreNotServerErrors() throws Exception {
        HttpClient anonymous = HttpClient.newHttpClient();
        assertEquals(404, status(anonymous, "GET", "/nope.txt"));
        assertEquals(404, status(anonymous, "GET", "/actuator/health"));
        assertEquals(404, status(anonymous, "GET", "/API/me"));

        HttpClient client = signedIn(USER, USER_PASSWORD);
        assertEquals(405, status(client, "DELETE", "/api/me"));
        assertEquals(404, status(client, "GET", "/api/nonexistent"));
        assertEquals(400, status(client, "GET", "/api/stock/details"), "нет обязательного параметра");
        assertEquals(400, status(client, "GET", "/api/sku/not-a-uuid"), "кривой идентификатор");
        String text = body(client, "/api/stock/details");
        assertTrue(text.contains("\"message\""), "у отказа нет текста для человека: " + text);
    }
}
