package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Портал не говорит с человеком русским текстом из кода.
 *
 * <p>ЗАЧЕМ. Плашка (notify/say/tell/showMessage) — то единственное, что
 * человек видит после нажатия кнопки: «сохранено», «не найдено»,
 * «не удалось». Эти тексты были написаны по-русски прямо в коде портала,
 * и при английском языке интерфейса оставались русскими. Выходил худший
 * из возможных случаев: экран английский, а ответ портала на действие —
 * нет, то есть непереведённым оставалось именно то, что читают
 * внимательнее всего.
 *
 * <p>Переписать разом — работа на один вечер. Удержать — другое дело:
 * следующая плашка пишется в спешке, рядом с отладкой, и русская строка
 * возвращается. Поэтому проверка, а не только правка.
 *
 * <p>ГРАНИЦА. Смотрим только {@code static/js/portal} — код самого
 * портала. {@code prototype.js} — демонстрационная вёрстка заказчика,
 * она живёт по своим правилам и переводится отдельно; требовать от неё
 * того же значит каждый раз падать на чужом файле.
 */
class PortalTextsTest extends PortalTestBase {

    private static final Path PORTAL_JS =
            Path.of("src", "main", "resources", "static", "js", "portal");

    private static final Path STATIC = Path.of("src", "main", "resources", "static");

    private static final String GENERATED = "i18n-fallback.js";

    /** Функции, которыми портал говорит с человеком. */
    private static final Pattern NOTIFY_CALL =
            Pattern.compile("\\b(notify|say|tell|showMessage|window\\.confirm)\\s*\\(");

    private static final Pattern CYRILLIC = Pattern.compile("[А-Яа-яЁё]");

    /** Вызов с подстановками: T('ключ', {имя: значение}). */
    private static final Pattern WITH_PARAMS =
            Pattern.compile("\\bT\\(\\s*'([a-z][a-zA-Z0-9_.]+)'\\s*,\\s*\\{");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    @Test
    void notifyTextsComeFromTheCatalogue() {
        List<String> found = new ArrayList<>();
        for (Path file : portalFiles()) {
            String source = read(file);
            Matcher m = NOTIFY_CALL.matcher(source);
            while (m.find()) {
                String args = balanced(source, m.end() - 1);
                for (String literal : literals(args)) {
                    if (CYRILLIC.matcher(literal).find()) {
                        found.add(file.getFileName() + ": " + m.group(1) + "(… '" + literal + "' …)");
                    }
                }
            }
        }
        assertTrue(found.isEmpty(),
                "портал говорит с человеком русским текстом из кода — при английском языке"
                        + " он таким и останется; текст надо завести ключом в миграции:\n"
                        + String.join("\n", found));
    }

    /**
     * Подстановки в тексте и значения в коде — одно и то же множество.
     *
     * <p>Две разные беды с одним корнем. Текст ждёт {@code {no}}, а код
     * передаёт {@code number} — человек увидит плашку с дырой «Черновик
     * {no} удалён». Наоборот: код передаёт {@code {name}}, а в тексте
     * его нет — значение пропадёт молча, и плашка выйдет без главного.
     * Ни то, ни другое не видно при чтении кода: имена лежат в базе.
     *
     * <p>Проверяются ВСЕ языки каталога, а не только русский: перевод
     * пишет человек, и подстановку в нём легко потерять или перевести
     * вместе с остальными словами.
     */
    @Test
    void substitutionsMatchTheTexts() {
        Map<String, Map<String, String>> texts = new HashMap<>();
        for (Map<String, Object> row : jdbc.sql("""
                SELECT t.text_key, l.code, t.value
                  FROM dbo.ui_text t
                  JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                """).query().listOfRows()) {
            texts.computeIfAbsent(String.valueOf(row.get("text_key")), k -> new HashMap<>())
                    .put(String.valueOf(row.get("code")), String.valueOf(row.get("value")));
        }

        List<String> wrong = new ArrayList<>();
        int calls = 0;
        for (Path file : screenFiles()) {
            String source = read(file);
            Matcher m = WITH_PARAMS.matcher(source);
            while (m.find()) {
                calls++;
                String key = m.group(1);
                Set<String> given = topLevelNames(balanced(source, m.end() - 1));
                texts.getOrDefault(key, Map.of()).forEach((locale, value) -> {
                    Set<String> expected = placeholders(value);
                    if (!expected.equals(given)) {
                        wrong.add(file.getFileName() + ": " + key + " [" + locale + "] текст ждёт "
                                + expected + ", код передаёт " + given);
                    }
                });
            }
        }

        // Сломанное выражение поиска даёт ноль вызовов и молча зелёную
        // проверку. Порог грубый нарочно — он ловит ноль, а не следит
        // за точным числом.
        assertTrue(calls > 20,
                "вызовов T(ключ, {…}) нашлось подозрительно мало (" + calls
                        + ") — сломалось само выражение поиска");
        assertTrue(wrong.isEmpty(),
                "подстановка в тексте и значение в коде называются по-разному — человек увидит"
                        + " плашку с дырой:\n" + String.join("\n", new TreeSet<>(wrong)));
    }

    /**
     * Русского текста нет и в разметке, которую портал рисует сам.
     *
     * <p>Соседняя проверка смотрит только плашки. А экран портал
     * собирает строками: заголовки карточек доступов, правила пароля,
     * подсказки справочников. Такая строка ничем не отличается от
     * переведённой, пока язык русский, — и ровно поэтому её не
     * замечают. При английском языке экран становится наполовину
     * русским, причём русскими остаются подсказки, то есть то, что
     * читают, когда не поняли.
     *
     * <p>Разрешены два исключения, и оба — не текст для человека:
     * <ul>
     *   <li>подпись для {@code console.warn} и {@code safely(…)} —
     *       её читает разработчик в отладчике;
     *   <li>образец для {@code indexOf}/{@code includes} — им портал
     *       ищет кнопку прототипа по её {@code onclick}.
     * </ul>
     * Оба исключения узкие нарочно: расширять их значит возвращать
     * дыру, ради которой проверка и написана.
     */
    @Test
    void drawnMarkupComesFromTheCatalogue() {
        List<String> found = new ArrayList<>();
        int scanned = 0;
        for (Path file : portalFiles()) {
            String code = withoutComments(read(file));
            Matcher m = STRING.matcher(code);
            while (m.find()) {
                String literal = m.group();
                scanned++;
                if (!CYRILLIC.matcher(literal).find() || allowed(code, m.start())) {
                    continue;
                }
                found.add(file.getFileName() + ": " + literal.trim());
            }
        }

        // Сломанный разбор даёт ноль находок и молча зелёную проверку.
        assertTrue(scanned > 500,
                "строковых литералов нашлось подозрительно мало (" + scanned
                        + ") — сломался разбор, а не код");
        assertTrue(found.isEmpty(),
                "портал рисует человеку русский текст из кода — при английском языке"
                        + " он таким и останется; текст надо завести ключом в миграции"
                        + " и звать через T('ключ'):\n" + String.join("\n", found));
    }

    /** Литерал, который человек не читает: отладка или образец поиска. */
    private static boolean allowed(String code, int start) {
        String before = code.substring(Math.max(0, start - 40), start);
        return DIAGNOSTIC.matcher(before).find() || NEEDLE.matcher(before).find();
    }

    /**
     * Строковый литерал: в кавычках, в апострофах и в обратных
     * кавычках. Шаблонная строка включена не для порядка: сегодня
     * кириллицы в них нет, но написать её туда так же легко,
     * а проверка, которая смотрит не везде, успокаивает зря.
     */
    private static final Pattern STRING = Pattern.compile(
            "'(?:[^'\\\\\n]|\\\\.)*'"
            + "|\"(?:[^\"\\\\\n]|\\\\.)*\""
            + "|`(?:[^`\\\\]|\\\\[\\s\\S])*`");

    /** console.warn('…'), console.error('…'), safely('…', …) */
    private static final Pattern DIAGNOSTIC =
            Pattern.compile("(?:console\\.(?:warn|error|log)|safely)\\s*\\(\\s*$");

    /** onclick.indexOf('…'), text.includes('…') */
    private static final Pattern NEEDLE =
            Pattern.compile("\\.(?:indexOf|includes)\\s*\\(\\s*$");

    /**
     * Исходник без комментариев: по-русски в них пишут нарочно,
     * и объяснение на русском полезнее английского имени.
     *
     * <p>Строки при этом не трогаются — иначе вырезались бы и
     * апострофы внутри них, и разбор поехал бы.
     */
    private static String withoutComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != c) {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                out.append(src, i, Math.min(j + 1, src.length()));
                i = j + 1;
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int j = src.indexOf('\n', i);
                j = j < 0 ? src.length() : j;
                out.append(" ".repeat(j - i));
                i = j;
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                j = j < 0 ? src.length() : j + 2;
                out.append(" ".repeat(j - i));
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /* ---------------- разбор исходника ---------------- */

    /**
     * Текст между скобкой по адресу {@code open} и парной ей закрывающей.
     * Нужен именно он: аргументы занимают несколько строк, и регулярным
     * выражением парную скобку не найти.
     */
    private static String balanced(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        return source.substring(open + 1);
    }

    /** Строковые литералы в одинарных кавычках — то, что написано в коде. */
    private static List<String> literals(String args) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("'((?:[^'\\\\\\n]|\\\\.)*)'").matcher(args);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * Имена свойств объектного литерала — только верхнего уровня.
     *
     * <p>Иначе тернарный оператор внутри значения выглядел бы вторым
     * свойством: в {@code {code: r ? r.packagingCode : id}} двоеточие
     * стоит дважды, и наивный поиск «слово перед двоеточием» нашёл бы
     * здесь и {@code packagingCode}.
     */
    private static Set<String> topLevelNames(String body) {
        Set<String> names = new TreeSet<>();
        Matcher m = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*").matcher(body);
        int depth = 0;
        int scanned = 0;
        while (m.find()) {
            depth += depthChange(body, scanned, m.start());
            scanned = m.end();
            if (depth != 0) {
                continue;
            }
            // Перед именем — начало литерала или запятая: только тогда
            // это свойство, а не часть выражения вроде r.packagingCode.
            int before = m.start() - 1;
            while (before >= 0 && Character.isWhitespace(body.charAt(before))) {
                before--;
            }
            if (before >= 0 && body.charAt(before) != ',') {
                continue;
            }
            int after = m.end();
            while (after < body.length() && Character.isWhitespace(body.charAt(after))) {
                after++;
            }
            if (after < body.length() && body.charAt(after) == ':') {
                names.add(m.group());
            }
        }
        return names;
    }

    private static int depthChange(String body, int from, int to) {
        int change = 0;
        for (int i = from; i < to; i++) {
            char c = body.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                change++;
            } else if (c == ')' || c == ']' || c == '}') {
                change--;
            }
        }
        return change;
    }

    private static Set<String> placeholders(String value) {
        Set<String> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<Path> portalFiles() {
        return walk(PORTAL_JS);
    }

    private static List<Path> screenFiles() {
        return walk(STATIC);
    }

    private static List<Path> walk(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".js")
                    && !p.getFileName().toString().equals(GENERATED)).sorted().toList();
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
