package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Вызов несуществующей функции во фронтенде ловится проверкой, а не
 * щелчком человека (заказчик, 09.11.2026: «добавить проверку на
 * пропущенные методы»; повод — пропавшая {@code say} в filters.js).
 *
 * <p>ЧЕГО НЕ ВИДИТ ПРОВЕРКА ТИПОВ. Она ловит вызов несуществующей функции,
 * записанный кодом. Но половина вызовов портала записана ИМЕНЕМ В СТРОКЕ:
 * кнопка несёт {@code data-call="PORTAL.draft.confirm"}, и функцию по
 * этому имени ищет общий обработчик в момент щелчка. Опечатка в имени
 * или удалённая функция — это строка в консоли и кнопка, которая молча
 * ничего не делает. То же с именами, которые зовутся через
 * {@code typeof x === 'function'}: проверка есть, функции нет — ветка
 * молча не выполняется никогда.
 *
 * <p>КАК ПРОВЕРЯЕМ. Имена собираются из исходников — разметки и модулей,
 * а существование проверяется в открытой странице, у настоящих объектов:
 * разбирать глазами, что именно модуль положил в {@code PORTAL.x}, не
 * нужно, и обмануться тут нельзя.
 * <ol>
 *   <li>Имена обработчиков: {@code data-call / press / change / input /
 *       backdrop}, первый аргумент {@code PORTAL.call…(…)}, строки вида
 *       {@code 'PORTAL.модуль.функция'} и описаний {@code open: 'имя'},
 *       {@code download: 'имя'} — так имя доезжает до обработчика через
 *       переменную.</li>
 *   <li>Прямые вызовы {@code PORTAL.модуль.функция(…)}: объекты модулей
 *       описаны в типах свободно, и опечатку в них проверка типов
 *       пропускает.</li>
 *   <li>Имена из {@code typeof имя === 'function'} и списка перерисовки
 *       при смене языка (i18n.js).</li>
 *   <li>Глобальные функции, объявленные в {@code window.d.ts}: объявление
 *       без функции обещает проверке типов то, чего нет.</li>
 * </ol>
 *
 * <p>Имя обработчика, которое нельзя прочитать из исходника (собрано
 * из кусков), — тоже отказ: пишите имя целиком.
 */
class HandlerNamesTest extends BrowserTestBase {

    private static final Path STATIC = Path.of("src", "main", "resources", "static");
    private static final Path WINDOW_TYPES = Path.of("src", "main", "types", "window.d.ts");

    private static final List<Pattern> HANDLERS = List.of(
            Pattern.compile("data-(?:call|press|change|input|backdrop)=\\\\?[\"']([\\w.]+)"),
            Pattern.compile("setAttribute\\('data-(?:call|press|change|input|backdrop)',\\s*'([\\w.]+)'"),
            Pattern.compile("PORTAL\\.call(?:On\\w+)?\\(\\s*'([\\w.]+)'"),
            Pattern.compile("'(PORTAL\\.\\w+(?:\\.\\w+)+)'"),
            // имя глобальной функции в описании списка или загрузки: {open: 'portalOpenSku'}
            Pattern.compile("\\b(?:open|download)\\s*:\\s*'(\\w+)'"));

    /** Первый аргумент {@code PORTAL.call…} — не строка: имя пришло переменной или собрано. */
    private static final Pattern CALL_BY_VARIABLE = Pattern.compile("PORTAL\\.call(?:On\\w+)?\\(\\s*([^'\\s][^,)]*)");
    /** Имя собрано сложением — прочитать его из исходника нельзя. */
    private static final Pattern GLUED_NAME = Pattern.compile("PORTAL\\.call(?:On\\w+)?\\(\\s*'[\\w.]*'\\s*\\+");

    private static final Pattern DIRECT_CALL = Pattern.compile("(?<![\\w.'])PORTAL\\.(\\w+)\\.(\\w+)\\(");
    private static final Pattern GUARDED = Pattern.compile("typeof (?:window\\.)?(\\w+) === 'function'");
    private static final Pattern RERENDER_LIST = Pattern.compile("\\[((?:\\s*'render\\w+',?)+)\\s*]");
    private static final Pattern DECLARED = Pattern.compile("^\\s*var (\\w+)\\s*:\\s*(.*)$", Pattern.MULTILINE);

    @Test
    void everyNamedFunctionExists() throws IOException {
        // имя → где встретилось (первый файл): по нему находят место
        Map<String, String> functions = new TreeMap<>();
        Map<String, String> anything = new TreeMap<>();
        List<String> unreadable = new ArrayList<>();

        List<Path> sources = new ArrayList<>();
        sources.add(STATIC.resolve("index.html"));
        try (Stream<Path> js = Files.list(STATIC.resolve("js").resolve("portal"))) {
            sources.addAll(js.filter(p -> p.toString().endsWith(".js")).sorted().toList());
        }
        for (Path file : sources) {
            String name = file.getFileName().toString();
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (Pattern p : HANDLERS) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    functions.putIfAbsent(m.group(1), name);
                }
            }
            Matcher direct = DIRECT_CALL.matcher(text);
            while (direct.find()) {
                functions.putIfAbsent("PORTAL." + direct.group(1) + "." + direct.group(2), name);
            }
            Matcher guard = GUARDED.matcher(text);
            while (guard.find()) {
                functions.putIfAbsent(guard.group(1), name);
            }
            Matcher list = RERENDER_LIST.matcher(text);
            while (list.find()) {
                Matcher item = Pattern.compile("'(render\\w+)'").matcher(list.group(1));
                while (item.find()) {
                    functions.putIfAbsent(item.group(1), name);
                }
            }
            Matcher glued = GLUED_NAME.matcher(text);
            while (glued.find()) {
                unreadable.add(name + ": " + glued.group());
            }
        }
        // параметры и локальные переменные, попавшие под «typeof x === 'function'», — не имена функций портала
        List.of("fn", "target", "value", "handler", "callback").forEach(functions::remove);

        Matcher declared = DECLARED.matcher(Files.readString(WINDOW_TYPES, StandardCharsets.UTF_8));
        while (declared.find()) {
            (declared.group(2).contains("=>") ? functions : anything).putIfAbsent(declared.group(1), "window.d.ts");
        }
        assertTrue(functions.size() > 150, "имён собрано подозрительно мало (" + functions.size()
                + ") — разбор исходников сломался, проверка ничего не проверяет");
        assertTrue(unreadable.isEmpty(), "имя обработчика собрано из кусков — проверить его нельзя, пишите целиком: "
                + unreadable);

        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("id => portalSelectDepositor(id)", depositorId(USER, DEPOSITOR));
        waitForQuiet();

        @SuppressWarnings("unchecked")
        List<String> found = (List<String>) page.evaluate("""
                names => names.filter(([path, mustBeFunction]) => {
                  let target = window;
                  for (const part of path.split('.')) {
                    if (target === null || target === undefined) return false;
                    target = target[part];
                  }
                  return mustBeFunction ? typeof target === 'function' : target !== undefined;
                }).map(([path]) => path)
                """, Stream.concat(
                        functions.keySet().stream().map(n -> List.of(n, true)),
                        anything.keySet().stream().map(n -> List.of(n, false))).toList());

        TreeSet<String> missing = new TreeSet<>();
        functions.forEach((n, where) -> { if (!found.contains(n)) missing.add(n + " (" + where + ")"); });
        anything.forEach((n, where) -> { if (!found.contains(n)) missing.add(n + " (" + where + ")"); });
        assertTrue(missing.isEmpty(), "имя есть, а функции нет — кнопка или вызов молча ничего не сделает ("
                + missing.size() + "):\n  " + String.join("\n  ", missing));
        assertNoJsErrors();
    }
}
