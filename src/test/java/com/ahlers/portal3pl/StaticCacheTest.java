package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Скрипты портала браузер обязан сверять с сервером перед использованием.
 *
 * <p>Без {@code Cache-Control} браузер держит файл сам, сколько сочтёт
 * нужным, и после обновления портала человек видит новую страницу со
 * старыми скриптами. Так было после V81: кнопка загрузки упаковок у
 * администратора не появилась, потому что прежний {@code import.js}
 * прятал её.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaticCacheTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private HttpResponse<Void> get(String path, String ifModifiedSince) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET();
        if (ifModifiedSince != null) {
            request.header("If-Modified-Since", ifModifiedSince);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
        }
    }

    @Test
    void scriptsAndPageAreRevalidatedEveryTime() throws Exception {
        for (String path : new String[]{"/js/portal/import.js", "/js/portal/reports.js", "/index.html", "/"}) {
            HttpResponse<Void> answer = get(path, null);
            assertEquals(200, answer.statusCode(), path);
            String cache = answer.headers().firstValue("Cache-Control").orElse("");
            assertTrue(cache.contains("no-cache"),
                    path + ": браузеру не велено сверяться с сервером — Cache-Control «" + cache + "»");
        }
    }

    /**
     * Сверка дешёвая: неизменный файл возвращается ответом 304 без тела.
     * Иначе no-cache означал бы полную загрузку каждого скрипта на каждый вход.
     */
    @Test
    void unchangedScriptAnswersNotModified() throws Exception {
        HttpResponse<Void> first = get("/js/portal/import.js", null);
        String stamp = first.headers().firstValue("Last-Modified").orElseThrow(
                () -> new AssertionError("сервер не прислал Last-Modified — сверять не по чему"));
        assertEquals(304, get("/js/portal/import.js", stamp).statusCode());
    }
}
