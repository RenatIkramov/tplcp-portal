package com.ahlers.portal3pl.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Представления отдают колонки в стиле snake_case (document_no, supplier_name),
 * а в JSON для фронтенда принято camelCase (documentNo, supplierName).
 * <p>
 * Для экранов с фиксированным набором полей (как «Запасы») удобнее record —
 * Spring делает преобразование сам. Для документов полей под полсотни и они
 * разные у трёх типов, поэтому строки читаются как Map, а имена колонок
 * переводятся здесь.
 * <p>
 * Здесь же календарная дата превращается в строку — см. {@link #value(Object)}.
 */
public final class SqlRows {

    private SqlRows() {
    }

    public static List<Map<String, Object>> toCamelCase(List<Map<String, Object>> rows) {
        return rows.stream().map(SqlRows::convertRow).toList();
    }

    private static Map<String, Object> convertRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(camel(key), value(value)));
        return out;
    }

    /**
     * Календарная дата уходит наружу строкой {@code 2026-12-01}.
     * <p>
     * Драйвер отдаёт колонку типа {@code date} как {@link java.sql.Date} —
     * а это момент времени, пусть и с нулевым временем. Превращая его
     * в JSON, Jackson пишет мгновение по UTC, и дата съезжает на сутки
     * назад ровно на столько, на сколько часовой пояс сервера впереди
     * UTC: в Алматы (UTC+5) «1 декабря» приезжало на экран как
     * «30 ноября». Экран при этом ни при чём — он берёт первые десять
     * знаков того, что пришло.
     * <p>
     * Поэтому дату отдаём строкой. У календарной даты нет мгновения,
     * и приписывать ей полночь в каком-то поясе — та самая ошибка,
     * с которой начинается сдвиг. Формат {@code java.sql.Date.toString()}
     * — это ISO {@code yyyy-MM-dd}, то же самое, что ждёт экран
     * и что принимает база при записи.
     * <p>
     * Отметки времени ({@code timestamptz} — created_at, synced_at)
     * не трогаем: у них мгновение есть, и часовой пояс для них
     * не искажение, а смысл.
     */
    private static Object value(Object value) {
        return value instanceof java.sql.Date date ? date.toString() : value;
    }

    static String camel(String snake) {
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
