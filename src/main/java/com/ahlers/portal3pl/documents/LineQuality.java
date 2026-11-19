package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.Fail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Качество строки заказа (V89), поступления (V100) и перемещения (V101) —
 * перечисление {@code enum_quality}:
 * 01 годный, 02 брак, 03 некомплект, 04 контроль качества.
 *
 * <p>Экран и импорт присылают КОД качества в поле {@code quality};
 * в базу пишется {@code quality_id}. Код, а не имя: имя переводится,
 * а код — то, чем портал обменивается с WMS.
 *
 * <p>Пусто — «Годный», и пишется он явно, а не пустотой (решение
 * заказчика от 27.10): WMS может требовать качество у каждой строки
 * резерва. Неизвестный или выключенный код — отказ.
 */
@Component
public class LineQuality {

    /** Код качества по умолчанию — «Годный». */
    public static final String GOOD = "01";

    private final JdbcClient jdbc;

    public LineQuality(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Строка с проверенным качеством: {@code quality} (код)
     * заменяется на {@code quality_id}. Исходная карта не меняется.
     */
    public Map<String, Object> normalized(Map<String, Object> line) {
        Map<String, Object> out = new LinkedHashMap<>(line);
        Object raw = out.remove("quality");
        String code = raw == null ? "" : String.valueOf(raw).trim();
        if (code.isEmpty()) {
            code = GOOD;
        }
        Integer id = jdbc.sql("SELECT id::int FROM dbo.enum_quality WHERE code = :code::varchar AND is_active")
                .param("code", code)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> Fail.badRequest("error.line.bad_quality", "value", String.valueOf(raw)));
        out.put("quality_id", id);
        return out;
    }
}
