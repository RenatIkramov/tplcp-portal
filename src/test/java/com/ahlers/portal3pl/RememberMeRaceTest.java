package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * «Запомнить меня»: загрузка страницы шлёт три запроса разом (аудит
 * 11.11.2026, S13).
 *
 * <p>НАЙДЕНО АУДИТОМ (гипотеза, здесь — воспроизведение). Сеанса нет, есть
 * только cookie «запомнить меня». Страница одновременно спрашивает
 * {@code /api/auth/session}, языки и тексты. Токен одноразовый: один
 * запрос его гасит и выдаёт новый, двум другим он уже не достаётся — и они
 * отвечали «стереть cookie». Что останется в браузере, решал порядок
 * ответов: новый токен мог стереться, а если токен успел погасить запрос
 * текстов, {@code /api/auth/session} получал 401 и человек видел форму
 * входа, хотя просил его запомнить.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RememberMeRaceTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private String rememberedCookie() throws Exception {
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"login\":\"" + USER + "\",\"password\":\""
                        + USER_PASSWORD + "\",\"remember\":true}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return remember(login).orElseThrow(() -> new AssertionError("вход не выдал cookie «запомнить меня»"));
    }

    private static java.util.Optional<String> remember(HttpResponse<?> r) {
        return r.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("portal_remember=")).findFirst()
                .map(c -> c.substring(0, c.indexOf(';') < 0 ? c.length() : c.indexOf(';')));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private CompletableFuture<HttpResponse<String>> get(String path, String cookie) {
        return http.sendAsync(HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Три запроса загрузки страницы с одним токеном: сеанс восстанавливается,
     * и ни один ответ не стирает cookie — иначе следующий запуск браузера
     * снова потребует входа. Повторяется несколько раз: гонка случайна.
     */
    @Test
    void parallelPageLoadKeepsTheSignIn() throws Exception {
        for (int round = 0; round < 5; round++) {
            String cookie = rememberedCookie();
            List<CompletableFuture<HttpResponse<String>>> all = new ArrayList<>();
            CompletableFuture<HttpResponse<String>> session = get("/api/auth/session", cookie);
            all.add(get("/api/i18n/locales", cookie));
            all.add(get("/api/i18n/texts/ru", cookie));
            all.add(session);
            CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();

            assertEquals(200, session.get().statusCode(),
                    "раунд " + round + ": сеанс не восстановлен — человек видит форму входа");
            for (CompletableFuture<HttpResponse<String>> f : all) {
                HttpResponse<String> r = f.get();
                remember(r).ifPresent(c -> assertFalse(c.equals("portal_remember="),
                        "ответ " + r.uri().getPath() + " стёр cookie «запомнить меня»"));
            }
        }
    }

    /** Погашенный токен второй раз не впускает — даже пока он не стирает cookie. */
    @Test
    void usedTokenDoesNotSignInAgain() throws Exception {
        String cookie = rememberedCookie();
        HttpResponse<String> first = get("/api/auth/session", cookie).join();
        assertEquals(200, first.statusCode(), first.body());
        String renewed = remember(first).orElseThrow(() -> new AssertionError("новый токен не выдан"));

        assertEquals(401, get("/api/auth/session", cookie).join().statusCode(),
                "погашенный токен впустил второй раз");
        assertEquals(200, get("/api/auth/session", renewed).join().statusCode(), "новый токен не впускает");
    }
}
