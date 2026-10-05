package com.ahlers.portal3pl.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Представления отдают колонки в стиле snake_case (document_no, supplier_name),
 * а в JSON для фронтенда принято camelCase (documentNo, supplierName).
 *
 * Для экранов с фиксированным набором полей (как «Запасы») удобнее record —
 * Spring делает преобразование сам. Для документов полей под полсотни и они
 * разные у трёх типов, поэтому строки читаются как Map, а имена колонок
 * переводятся здесь.
 */
public final class SqlRows {

    private SqlRows() {
    }

    public static List<Map<String, Object>> toCamelCase(List<Map<String, Object>> rows) {
        return rows.stream().map(SqlRows::convertRow).toList();
    }

    private static Map<String, Object> convertRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(camel(key), value));
        return out;
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
