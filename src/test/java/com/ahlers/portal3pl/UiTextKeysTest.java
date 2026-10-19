package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Каждый ключ, на который ссылается экран, есть в каталоге текстов.
 *
 * <p>НАПИСАНО ПО СЛЕДАМ СВОЕЙ ОШИБКИ. Миграция V57 удалила четыре ключа
 * {@code server.message.*} как неиспользуемые — инструмент
 * {@code tools/find_unused_texts.py} их не защитил, и вот почему: он
 * идёт от каталога к коду («этот ключ ещё нужен?»), а здесь требовалась
 * обратная дорога — от кода к каталогу («этому ключу есть что показать?»).
 * Два из четырёх были живыми: в карточке поступления вместо подписи
 * «Поставщик» человек увидел {@code server.message.supplier}.
 *
 * <p>Проверка дешёвая и быстрая: читает вёрстку и код экранов, собирает
 * ключи и сверяет их с {@code ui_text_key}. Браузер для этого не нужен.
 *
 * <p>Ключом считается то, что похоже на ключ по форме И начинается
 * с раздела, который есть в каталоге. Иначе проверка цеплялась бы
 * к любому полю {@code key: 'что.то.такое'} в коде.
 */
class UiTextKeysTest extends PortalTestBase {

    private static final Path STATIC = Path.of("src", "main", "resources", "static");

    /** Запасной словарь собирается из базы — в нём есть каждый ключ. */
    private static final String GENERATED = "i18n-fallback.js";

    /** Откуда экран берёт ключ: разметка, T('...'), PORTAL.t('...'), поля key/titleKey. */
    private static final Pattern REFERENCE = Pattern.compile(
            "data-i18n(?:-[a-z-]+)?=\"([^\"]+)\""
                    + "|\\bT\\(\\s*'([^']+)'"
                    + "|PORTAL\\.t\\(\\s*'([^']+)'"
                    + "|\\b(?:key|titleKey|labelKey|textKey)\\s*:\\s*'([^']+)'");

    private static final Pattern SHAPE = Pattern.compile("[a-z][a-z0-9]*\\.[a-z0-9_]+\\.[a-z0-9_]+");

    @Test
    void everyKeyUsedByScreensExistsInCatalogue() {
        Set<String> scopes = new TreeSet<>(jdbc
                .sql("SELECT DISTINCT scope FROM dbo.ui_text_key WHERE scope ~ '^[a-z]+$'")
                .query(String.class)
                .list());
        Set<String> catalogue = new TreeSet<>(jdbc
                .sql("SELECT text_key FROM dbo.ui_text_key")
                .query(String.class)
                .list());

        Map<String, Set<String>> used = new TreeMap<>();
        for (Path file : screenFiles()) {
            Matcher m = REFERENCE.matcher(read(file));
            while (m.find()) {
                String key = firstGroup(m);
                if (key == null || !SHAPE.matcher(key).matches()) {
                    continue;
                }
                if (!scopes.contains(key.substring(0, key.indexOf('.')))) {
                    continue;
                }
                used.computeIfAbsent(key, k -> new TreeSet<>()).add(file.getFileName().toString());
            }
        }

        // Если выражение поиска сломается, проверка начнёт молча проходить
        // на пустом множестве. Порог грубый нарочно: он ловит ноль и
        // десяток, а не следит за точным числом ключей.
        assertTrue(used.size() > 400,
                "ключей в экранах нашлось подозрительно мало (" + used.size()
                        + ") — сломалось само выражение поиска");

        Map<String, Set<String>> missing = new TreeMap<>();
        used.forEach((key, files) -> {
            if (!catalogue.contains(key)) {
                missing.put(key, files);
            }
        });
        assertTrue(missing.isEmpty(),
                "экран ссылается на ключи, которых нет в каталоге текстов — человек увидит"
                        + " сам ключ вместо надписи:\n" + missing);
    }

    private static String firstGroup(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return m.group(i);
            }
        }
        return null;
    }

    private static List<Path> screenFiles() {
        try (Stream<Path> files = Files.walk(STATIC)) {
            return files.filter(p -> {
                String name = p.getFileName().toString();
                return (name.endsWith(".html") || name.endsWith(".js")) && !name.equals(GENERATED);
            }).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
