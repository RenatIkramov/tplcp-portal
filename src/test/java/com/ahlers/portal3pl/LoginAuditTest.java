package com.ahlers.portal3pl;

import com.ahlers.portal3pl.audit.AuditService;
import com.ahlers.portal3pl.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Неудачные входы и журнал администратора (V117).
 *
 * <p>НАЙДЕНО ПО ХОДУ (11.11.2026). Вход отказывал исключением внутри
 * транзакции, и транзакция откатывалась — вместе со счётчиком неудачных
 * попыток. Счётчик всегда оставался нулём, и вход после пяти неудачных
 * попыток не закрывался никогда. Теперь отказ транзакцию не откатывает:
 * счётчик растёт, и в журнал пишется неудачный вход с причиной.
 */
class LoginAuditTest extends PortalTestBase {

    @Autowired
    private AuditService audit;

    @Test
    void failedSignInIsCountedAndLogged() {
        int before = failedCount();
        assertThrows(ResponseStatusException.class,
                () -> authService.authenticate(USER, "wrong-" + System.nanoTime()));
        int after = failedCount();
        authService.authenticate(USER, USER_PASSWORD);   // сбрасывает счётчик
        assertEquals(before + 1, after, "неудачная попытка не посчитана — отказ откатил транзакцию");

        String unknown = "nobody-" + System.nanoTime() + "@example.com";
        assertThrows(ResponseStatusException.class, () -> authService.authenticate(unknown, "x"));

        String page = audit.page(portalAdmin(), null, null, "login", null, null, 1, 200);
        assertTrue(page.contains(unknown), "неудачный вход несуществующего логина не в журнале");
        assertTrue(page.contains("\"action\": \"login_failed\"") || page.contains("\"action\":\"login_failed\""),
                "нет действия login_failed: " + page);
    }

    /**
     * Учётка не запирается (V123, решение заказчика 11.11.2026): после
     * пяти неудач подряд верный пароль входит сразу, а на неудачи растёт
     * задержка ответа — с четвёртой: 1, 2, 4, 8 секунд, не больше.
     *
     * <p>Прежде пять неудач закрывали вход на 15 минут, и посторонний,
     * зная логин, мог держать чужую учётку запертой. Счётчик клиента после
     * теста возвращает базовый класс.
     */
    @Test
    void failuresDelayButDoNotLock() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ResponseStatusException.class,
                    () -> authService.authenticate(USER, "wrong-" + System.nanoTime()));
        }
        assertEquals(2000, authService.failureDelayMs(USER), "пятая неудача подряд — задержка 2 с");
        authService.authenticate(USER, USER_PASSWORD);   // не запер: верный пароль входит
        assertEquals(0, authService.failureDelayMs(USER), "удачный вход не обнулил счёт неудач");

        assertEquals(List.of(0L, 0L, 0L, 1000L, 2000L, 4000L, 8000L, 8000L),
                java.util.stream.IntStream.rangeClosed(1, 8)
                        .mapToObj(n -> AuthService.delayFor(n)).toList(),
                "лестница задержки");
    }

    /** Срок хранения: в пределах — сохраняется, вне — отказ. Значение заказчика возвращается. */
    @Test
    void retentionIsCheckedAndSaved() {
        int months = ((Number) audit.settings(portalAdmin()).get("retentionMonths")).intValue();
        Map<String, Object> saved = audit.saveSettings(portalAdmin(), months);
        assertEquals(months, ((Number) saved.get("retentionMonths")).intValue());
        assertThrows(ResponseStatusException.class, () -> audit.saveSettings(portalAdmin(), 0));
        assertThrows(ResponseStatusException.class, () -> audit.saveSettings(portalAdmin(), 121));
    }

    private int failedCount() {
        return jdbc.sql("SELECT failed_login_count FROM dbo.app_user WHERE login = :l")
                .param("l", USER).query(Integer.class).single();
    }
}
