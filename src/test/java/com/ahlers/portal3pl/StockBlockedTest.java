package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Заблокировано» в запасах — по качеству (V92): всё количество партий
 * с качеством, отличным от 01 «Годный». Причин блокировки и своего числа
 * от WMS больше нет: источник правды один.
 */
class StockBlockedTest extends PortalTestBase {

    @Test
    void blockedIsEverythingThatIsNotGood() {
        String expected = singleValue("""
                SELECT COALESCE(sum(st.quantity), 0)::text FROM dbo.stock st
                  JOIN dbo.enum_quality q ON q.id = st.quality_id
                 WHERE q.code <> '01'""");
        assertTrue(Double.parseDouble(expected) > 0, "в демо-данных есть брак и контроль качества");
        assertEquals(expected, singleValue("SELECT COALESCE(sum(blocked_qty), 0)::text FROM dbo.v_stock_lines"),
                "строки запасов");
        assertEquals(expected, singleValue("SELECT COALESCE(sum(blocked_qty), 0)::text FROM dbo.v_stock_details"),
                "партии");
        assertEquals("0", singleValue("""
                SELECT count(*)::text FROM dbo.v_stock_details
                 WHERE quality_code = '01' AND blocked_qty <> 0"""), "годная партия не заблокирована");
    }

    @Test
    void blockReasonsAreGone() {
        assertEquals("0", singleValue("""
                SELECT count(*)::text FROM information_schema.columns
                 WHERE table_schema = 'dbo' AND table_name = 'stock'
                   AND column_name IN ('block_reason_id', 'blocked_qty')"""));
        assertEquals("0", singleValue(
                "SELECT count(*)::text FROM dbo.enum_registry WHERE enum_key = 'enum_block_reason'"));
    }
}
