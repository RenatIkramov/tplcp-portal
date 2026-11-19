package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Открытый сеанс гаснет, когда у человека отняли право (аудит
 * 11.11.2026, S4; V121).
 *
 * <p>НАЙДЕНО АУДИТОМ. Сеанс помнил пользователя с момента входа и базу
 * больше не спрашивал: заблокированный читал запасы и заказы, пока не
 * закроет вкладку; отозванный поклажедатель оставался виден; смена
 * пароля не трогала другие открытые сеансы.
 *
 * <p>Пароль, роль, блокировку клиента базовый класс возвращает после
 * теста сам (PersonalSettings); доступ к поклажедателю — этот тест.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCheckTest extends PortalTestBase {

    private static final String NEW_PASSWORD = "Portal-New-2026!";

    @LocalServerPort
    int port;

    private HttpClient signedIn(String password) throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> answer = send(client, "POST", "/api/auth/login",
                "{\"login\":\"" + USER + "\",\"password\":\"" + password + "\"}");
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

    private void assertEnded(HttpClient client, String why) throws Exception {
        HttpResponse<String> answer = send(client, "GET", "/api/stock", null);
        assertEquals(401, answer.statusCode(), why + ": сеанс продолжил отдавать данные");
        assertTrue(answer.body().contains("error.auth.session_ended"), answer.body());
        // погашен, а не только отказал: следующий запрос — уже без сеанса
        assertEquals(401, send(client, "GET", "/api/me", null).statusCode(), why + ": сеанс не закрыт");
    }

    @Test
    void blockingEndsTheOpenSession() throws Exception {
        HttpClient client = signedIn(USER_PASSWORD);
        assertEquals(200, send(client, "GET", "/api/stock", null).statusCode());
        owner().jdbc.sql("UPDATE dbo.app_user SET is_active = false WHERE login = :l").param("l", USER).update();
        assertEnded(client, "блокировка");
    }

    @Test
    void roleChangeEndsTheOpenSession() throws Exception {
        HttpClient client = signedIn(USER_PASSWORD);
        owner().jdbc.sql("UPDATE dbo.app_user SET role_id = (SELECT id FROM dbo.enum_user_role WHERE code = 'staff')"
                + " WHERE login = :l").param("l", USER).update();
        assertEnded(client, "смена роли");
    }

    /** Свой пароль сменил в одном сеансе — этот работает, второй гаснет. */
    @Test
    void passwordChangeEndsOtherSessions() throws Exception {
        HttpClient first = signedIn(USER_PASSWORD);
        HttpClient second = signedIn(USER_PASSWORD);
        HttpResponse<String> changed = send(first, "POST", "/api/me/password",
                "{\"currentPassword\":\"" + USER_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}");
        assertEquals(200, changed.statusCode(), "пароль не сменился: " + changed.body());

        assertEquals(200, send(first, "GET", "/api/stock", null).statusCode(),
                "сменивший пароль выброшен из своего сеанса");
        assertEnded(second, "смена пароля");
    }

    @Test
    void revokedDepositorEndsTheOpenSession() throws Exception {
        String uid = userId(USER);
        List<String> had = jdbc.sql("SELECT depositor_id::text FROM dbo.user_depositor_access WHERE user_id = :u::uuid")
                .param("u", uid).query(String.class).list();
        HttpClient client = signedIn(USER_PASSWORD);
        assertEquals(200, send(client, "GET", "/api/stock", null).statusCode());
        try {
            owner().jdbc.sql("DELETE FROM dbo.user_depositor_access WHERE user_id = :u::uuid").param("u", uid).update();
            assertEnded(client, "отзыв доступа к поклажедателю");
        } finally {
            for (String depositor : had) {
                owner().jdbc.sql("INSERT INTO dbo.user_depositor_access (user_id, depositor_id)"
                                + " VALUES (:u::uuid, :d::uuid) ON CONFLICT DO NOTHING")
                        .param("u", uid).param("d", depositor).update();
            }
        }
    }
}
