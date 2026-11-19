package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Защита входа и выданный пароль (аудит 11.11.2026, S8 и S11; V123).
 *
 * <p>НАЙДЕНО АУДИТОМ. Перебор паролей сдерживало только запирание учётки
 * (его заменила задержка — решение заказчика); ограничения по адресу
 * не было; каждая попытка под несуществующим логином была строкой
 * журнала; выданный пароль строился на {@code java.util.Random} из слогов.
 *
 * <p>Запросы — настоящие HTTP: задержка и адрес живут в обработке
 * запроса, а не в сервисе. Счёт по адресу базовый класс обнуляет перед
 * каждой проверкой; счёт неудач клиента возвращает PersonalSettings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginProtectionTest extends PortalTestBase {

    @LocalServerPort
    int port;

    @Autowired
    PasswordService passwords;

    @Autowired
    com.ahlers.portal3pl.audit.AuditService audit;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> login(String login, String password) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    /** С одного адреса больше двадцати неудач за пять минут — «слишком много попыток», даже с верным паролем. */
    @Test
    void tooManyFailuresFromOneAddress() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertEquals(401, login("nobody-" + i + "-" + System.nanoTime() + "@example.com", "x").statusCode());
        }
        HttpResponse<String> refused = login(USER, USER_PASSWORD);
        assertEquals(429, refused.statusCode(), "двадцать первая попытка пропущена: " + refused.body());
        assertTrue(refused.body().contains("error.auth.too_many_attempts"), refused.body());
    }

    /** С четвёртой неудачи подряд ответ задерживается; верный пароль после этого входит сразу. */
    @Test
    void failuresSlowDownButCorrectPasswordGetsIn() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(401, login(USER, "wrong-" + i).statusCode());
        }
        long started = System.nanoTime();
        assertEquals(401, login(USER, "wrong-4").statusCode());
        long fourth = (System.nanoTime() - started) / 1_000_000;
        assertTrue(fourth >= 900, "четвёртая неудача без задержки: " + fourth + " мс");

        started = System.nanoTime();
        assertEquals(200, login(USER, USER_PASSWORD).statusCode(), "учётка заперта");
        long correct = (System.nanoTime() - started) / 1_000_000;
        assertTrue(correct < 900, "верный пароль ждал задержку: " + correct + " мс");
    }

    /** Один и тот же несуществующий логин с одного адреса — одна строка журнала в минуту. */
    @Test
    void unknownLoginIsJournaledOncePerMinute() throws Exception {
        String unknown = "ghost-" + System.nanoTime() + "@example.com";
        assertEquals(401, login(unknown, "x").statusCode());
        int once = mentions(unknown);
        assertTrue(once > 0, "неудачный вход несуществующего логина не в журнале");
        assertEquals(401, login(unknown, "y").statusCode());
        assertEquals(once, mentions(unknown), "повтор через секунду записан в журнал ещё раз");
    }

    /** Сколько раз логин встречается в журнале входов администратора. */
    private int mentions(String login) {
        String page = audit.page(portalAdmin(), null, null, "login", null, null, 1, 200);
        return page.split(java.util.regex.Pattern.quote(login), -1).length - 1;
    }

    /** Выданный пароль: 12 знаков без похожих, обе буквы и цифра, проходит требования, не повторяется. */
    @Test
    void issuedPasswordIsStrongAndReadable() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String p = PasswordService.generate();
            assertEquals(12, p.length(), p);
            assertTrue(p.matches("[A-HJ-NP-Za-km-z2-9]{12}"), "похожие знаки или лишнее: " + p);
            assertTrue(p.chars().anyMatch(Character::isUpperCase) && p.chars().anyMatch(Character::isLowerCase)
                    && p.chars().anyMatch(Character::isDigit), p);
            if (i < 20) {
                passwords.hashForNewUser("new.user@example.com", p);   // требованиям отвечает (bcrypt медленный — не на всех)
            }
            assertTrue(seen.add(p), "пароль повторился: " + p);
        }
    }

    /** Пароль длиннее 72 байт — отказ: bcrypt проверил бы только начало. */
    @Test
    void tooLongPasswordIsRefused() {
        expectRejection(400, () -> passwords.hashForNewUser("new.user@example.com", "Aa1" + "b".repeat(70)));
        passwords.hashForNewUser("new.user@example.com", "Aa1" + "b".repeat(69));   // ровно 72 — можно
    }
}
