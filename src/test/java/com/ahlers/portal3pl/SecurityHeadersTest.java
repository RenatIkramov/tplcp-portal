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
 * Защитные заголовки на каждом ответе (аудит 11.11.2026, S7).
 *
 * <p>НАЙДЕНО АУДИТОМ. Страница портала вставлялась в чужой сайт во фрейме,
 * браузер угадывал тип файлов, адрес портала уходил наружу в Referer.
 * Проверка — по настоящему HTTP: заголовки ставит фильтр, а не контроллер.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHeadersTest extends PortalTestBase {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Страница, файл скрипта, ответ API и отказ без входа — у всех одни заголовки. */
    @Test
    void everyAnswerCarriesHeaders() throws Exception {
        for (String path : new String[]{"/", "/js/core.js", "/api/i18n/locales", "/api/stock"}) {
            HttpResponse<String> answer = get(path);
            assertEquals("nosniff", header(answer, "X-Content-Type-Options"), path);
            assertEquals("DENY", header(answer, "X-Frame-Options"), path);
            assertTrue(header(answer, "Content-Security-Policy").contains("frame-ancestors 'none'"), path);
            assertEquals("same-origin", header(answer, "Referrer-Policy"), path);
        }
    }

    /** Скрипты — только файлами портала: встроенный скрипт и onclick="…" браузер не выполнит (аудит A3). */
    @Test
    void scriptsOnlyFromPortalFiles() throws Exception {
        String csp = header(get("/"), "Content-Security-Policy");
        String scripts = java.util.Arrays.stream(csp.split(";")).map(String::trim)
                .filter(d -> d.startsWith("script-src")).findFirst().orElse("");
        assertEquals("script-src 'self'", scripts, "скрипты разрешены шире, чем файлами портала: " + csp);
        assertTrue(csp.contains("object-src 'none'") && csp.contains("base-uri 'self'"), csp);
    }

    /** По HTTP строгий HTTPS не объявляется — иначе он закрыл бы localhost разработчику. */
    @Test
    void noStrictTransportOverPlainHttp() throws Exception {
        assertTrue(get("/").headers().firstValue("Strict-Transport-Security").isEmpty());
    }

    private static String header(HttpResponse<String> answer, String name) {
        return answer.headers().firstValue(name).orElse("(нет заголовка " + name + ")");
    }
}
