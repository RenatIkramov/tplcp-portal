package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сторож соглашения о сообщениях сервера.
 *
 * <p>Сообщения портала живут в каталоге текстов, а код бросает ключ:
 * {@code throw Fail.conflict("error.user.login_taken", "login", login)}.
 * Соглашение держится ровно до первого отказа, написанного по-старому
 * русской строкой прямо в коде, — он не переведётся никогда, и заметят
 * это не здесь, а у клиента, которому портал ответит по-русски.
 *
 * <p>Поэтому проверяем то, что человек проверять не станет:
 * <ul>
 *   <li>в коде не появилось отказа с текстом вместо ключа;</li>
 *   <li>у каждого ключа из кода есть текст на русском и английском —
 *       иначе портал покажет человеку сам ключ;</li>
 *   <li>состав ключей в коде и в каталоге совпадает: ключ без текста
 *       портал покажет как есть, а текст без ключа переводчик примет
 *       за действующую подпись и переведёт в никуда.</li>
 * </ul>
 */
class ServerMessagesTest extends PortalTestBase {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** Ключи сообщений: встречаются в коде строкой целиком. */
    private static final Pattern KEY = Pattern.compile("\"((?:error|info)\\.[a-z0-9_.]+)\"");

    /**
     * Ключи, которые код собирает из частей: у справочника свой корень,
     * а повод один и тот же ({@code kind.error("not_found")}). Целиком
     * такой ключ в коде не встречается, поэтому перечисляем их здесь.
     *
     * @see com.ahlers.portal3pl.catalog.PartnerKind#error(String)
     */
    private static final Map<String, List<String>> BUILT_FROM_PARTS = Map.of(
            "error.supplier", List.of("not_found", "code_taken", "in_use", "create_field_required"),
            "error.consignee", List.of("not_found", "code_taken", "in_use", "create_field_required"),
            "error.order_type", List.of("not_found", "code_taken", "in_use"),
            "error.receipt_doc_type", List.of("not_found", "code_taken", "in_use"));

    @Test
    void rejectionsCarryKeyNotText() {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            int number = 0;
            for (String line : read(file).split("\n", -1)) {
                number++;
                String code = line.strip();
                // комментарии пропускаем: в описании класса Fail старый
                // вызов приведён нарочно, как пример «было — стало»
                if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                    continue;
                }
                if (code.contains("new ResponseStatusException(")) {
                    offenders.add(file + ":" + number + "  " + code);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "отказ портала бросается через Fail.<код>(\"ключ\", ...), иначе его текст "
                        + "не перевести:\n" + String.join("\n", offenders));
    }

    @Test
    void everyKeyFromCodeHasRussianAndEnglishText() {
        Set<String> missing = new TreeSet<>();
        for (String key : keysUsedInCode()) {
            for (String locale : List.of("ru", "en")) {
                if (text(key, locale) == null) {
                    missing.add(key + " (" + locale + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "нет текста для ключей сообщений — портал покажет человеку сам ключ: " + missing);
    }

    @Test
    void codeAndCatalogueAgreeOnKeys() {
        Set<String> inCode = keysUsedInCode();
        Set<String> inCatalogue = new TreeSet<>(jdbc
                .sql("SELECT text_key FROM dbo.ui_text_key WHERE scope = 'server'")
                .query(String.class)
                .list());

        Set<String> notInCatalogue = new TreeSet<>(inCode);
        notInCatalogue.removeAll(inCatalogue);
        Set<String> notInCode = new TreeSet<>(inCatalogue);
        notInCode.removeAll(inCode);

        assertTrue(notInCatalogue.isEmpty(),
                "ключ есть в коде, а в каталоге текстов его нет — добавить миграцией: "
                        + notInCatalogue);
        assertTrue(notInCode.isEmpty(),
                "ключ есть в каталоге текстов, а в коде его нет — удалить миграцией: " + notInCode);
    }

    /* ----------------------------- сбор ключей ----------------------------- */

    private Set<String> keysUsedInCode() {
        Set<String> keys = new TreeSet<>();
        for (Path file : javaFiles()) {
            Matcher m = KEY.matcher(read(file));
            while (m.find()) {
                keys.add(m.group(1));
            }
        }
        // корень составного ключа сам сообщением не является
        keys.removeAll(BUILT_FROM_PARTS.keySet());
        BUILT_FROM_PARTS.forEach((root, reasons) ->
                reasons.forEach(reason -> keys.add(root + "." + reason)));
        return keys;
    }

    private String text(String key, String locale) {
        return jdbc.sql("SELECT value FROM dbo.ui_texts(:code::text) WHERE text_key = :key")
                .param("code", locale)
                .param("key", key)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /* ----------------------------- чтение файлов ---------------------------- */

    /**
     * Исходники портала.
     *
     * <p>Пустой список здесь означал бы, что проверка ничего не смотрит
     * и молча проходит, — это хуже, чем её отсутствие. Поэтому пустоту
     * считаем провалом: значит, тест запущен не из корня проекта.
     */
    private static List<Path> javaFiles() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<Path> found = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            assertFalse(found.isEmpty(), "не нашлось исходников в " + MAIN.toAbsolutePath());
            return found;
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
