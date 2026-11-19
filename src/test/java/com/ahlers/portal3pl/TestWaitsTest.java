package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Браузерные проверки ждут условие, а не время (аудит 11.11.2026, D7).
 *
 * <p>{@code waitForLoadState(NETWORKIDLE)} после открытия страницы
 * возвращается сразу и ничего не ждёт; {@code waitForTimeout(…)} ждёт
 * «сколько обычно хватает» — на медленной машине не хватает. Ждать нужно
 * условия или {@code waitForQuiet()} (BrowserTestBase). Пауза допустима,
 * только когда ждать нечего по существу (картинке — сработать с ошибкой,
 * «запроса не было»), и тогда рядом стоит объяснение.
 */
class TestWaitsTest {

    private static final Path BROWSER = Path.of("src", "test", "java", "com", "ahlers", "portal3pl", "browser");

    @Test
    void browserChecksWaitForConditions() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.list(BROWSER)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*") || line.trim().startsWith("//")) {
                        continue;
                    }
                    if (line.contains("LoadState.NETWORKIDLE")) {
                        bad.add(f.getFileName() + ":" + (i + 1) + " NETWORKIDLE");
                    }
                    boolean explained = line.contains("//") || (i > 0 && lines.get(i - 1).trim().startsWith("//"));
                    if ((line.contains("waitForTimeout(") || line.contains("Thread.sleep(")) && !explained) {
                        bad.add(f.getFileName() + ":" + (i + 1) + " пауза без объяснения");
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "проверки ждут время, а не условие:\n" + String.join("\n", bad));
    }
}
