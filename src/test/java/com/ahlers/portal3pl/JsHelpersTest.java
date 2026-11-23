package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Помощники экранирования — одни на весь фронтенд (аудит 11.11.2026, A4).
 *
 * <p>НАЙДЕНО АУДИТОМ. Почти каждый модуль заводил свои {@code esc},
 * {@code attr}, {@code text}, {@code dash}, {@code say}, и копии
 * расходились: в draft.js «экранирование» трогало одну кавычку, и адрес
 * доставки, введённый клиентом, становился разметкой (S1). Теперь они
 * объявлены в core.js ({@code PORTAL.esc} и соседи), модули их берут.
 */
class JsHelpersTest {

    private static final Path PORTAL_JS = Path.of("src", "main", "resources", "static", "js", "portal");

    /** Своё определение помощника — {@code const esc = v => …} и т. п. */
    private static final Pattern OWN = Pattern.compile(
            "^\\s*const (esc|attr|text|dash|say|messageOf) = \\w+ =>\\s*(String\\(|\\(\\w+ ===|\\{ if \\(typeof notify|\\(e instanceof)",
            Pattern.MULTILINE);

    @Test
    void modulesUseSharedHelpers() throws IOException {
        List<String> own = new ArrayList<>();
        try (Stream<Path> files = Files.list(PORTAL_JS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".js") && !p.endsWith("core.js")).toList()) {
                var m = OWN.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    own.add(f.getFileName() + ": " + m.group(1));
                }
            }
        }
        assertTrue(own.isEmpty(), "модули снова заводят свои помощники вместо PORTAL.esc и соседей:\n"
                + String.join("\n", own));
    }

    /** Обработчик строкой в разметке: onclick="…", onchange="…", oninput="…" и прочие on…="…". */
    private static final Pattern INLINE = Pattern.compile("\\son[a-z]+=\\\\?[\"']");

    /**
     * Разметка, которую собирают модули, — без {@code onclick="…"} (аудит A3):
     * щелчок идёт через {@code PORTAL.call} и общий обработчик. Строка
     * скрипта, собранная из данных, — это дыра S1; без неё на страницу
     * можно будет поставить полный CSP.
     */
    @Test
    void modulesBuildNoOnclick() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(PORTAL_JS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".js")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (INLINE.matcher(lines.get(i)).find()) {
                        found.add(f.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "модуль снова собирает обработчик строкой (onclick, onchange…) — нужен PORTAL.call:\n" + String.join("\n", found));
    }

    /**
     * Разметка страницы — тоже без обработчиков строкой (аудит A3): кнопки
     * зовут функции через {@code data-call} и соседей. Без этого на страницу
     * не поставить CSP с запретом встроенных скриптов.
     */
    @Test
    void pageHasNoInlineHandlers() throws IOException {
        String html = Files.readString(Path.of("src", "main", "resources", "static", "index.html"),
                StandardCharsets.UTF_8);
        var m = Pattern.compile("\\son[a-z]+=\"[^\"]*\"").matcher(html);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group().trim());
        }
        assertTrue(found.isEmpty(), "в index.html обработчики строкой:\n" + String.join("\n", found));
    }
}
