package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.auth.DefaultPasswordGuard.Verdict;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Рабочий портал не живёт с паролем admin из V18 и с настройками машины
 * разработчика (аудит 11.11.2026, S9 и S12).
 *
 * <p>Решение проверяется без базы: базу для профиля prod тестам не поднять,
 * а ошибиться можно только в выборе — сама замена пароля одна строка.
 */
class DefaultPasswordGuardTest {

    @Test
    void decision() {
        assertEquals(Verdict.FINE, DefaultPasswordGuard.decide(true, false, null), "пароль уже свой");
        assertEquals(Verdict.WARN, DefaultPasswordGuard.decide(false, true, null), "разработчик");
        assertEquals(Verdict.REPLACE, DefaultPasswordGuard.decide(true, true, "Zk7mPq2rTw9x"), "задан новый");
        assertEquals(Verdict.REFUSE, DefaultPasswordGuard.decide(true, true, null), "нового нет");
        assertEquals(Verdict.REFUSE, DefaultPasswordGuard.decide(true, true, ""), "пустой");
        assertEquals(Verdict.REFUSE, DefaultPasswordGuard.decide(true, true, "short-1"), "короткий");
    }

    /** Профиль prod: куки только по HTTPS, адрес из прокси, паролей в файле нет. */
    @Test
    void prodProfileIsSafe() throws Exception {
        String yml = Files.readString(Path.of("src", "main", "resources", "application-prod.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yml.matches("(?s).*\\n\\s+secure: true.*"), "кука сеанса не Secure");
        assertTrue(yml.contains("forward-headers-strategy: native"), "HTTPS за прокси не виден порталу");
        assertTrue(yml.contains("baseline-on-migrate: false"));
        assertFalse(yml.matches("(?s).*\\n\\s*password:.*"), "в профиль prod вписан пароль");
    }
}
