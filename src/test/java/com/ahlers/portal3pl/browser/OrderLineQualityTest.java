package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Качество в окне строки заказа (V89) — выбор из списка вместо
 * галочки «Брак» (V85).
 *
 * <p>Что видит человек:
 * <ul>
 *   <li>в списке — четыре качества на его языке, по умолчанию «Годный»;</li>
 *   <li>«Остаток» и «Доступно» в окне — по выбранному качеству;</li>
 *   <li>строки разного качества одного товара — разные строки, повтор —
 *       только тот же товар, то же качество и тот же комментарий;</li>
 *   <li>в базу уходит код качества.</li>
 * </ul>
 */
class OrderLineQualityTest extends BrowserTestBase {

    /** В демо-данных у этого товара на складе Алматы есть годный и бракованный остаток. */
    private static final String SKU = "RK-ROG-02";
    private static final String WAREHOUSE = "WH-ALA";

    private String docId;

    private static BigDecimal number(String shown) {
        return new BigDecimal(shown.replaceAll("[^0-9.,]", "").replace(',', '.'));
    }

    private String stockTotal() {
        return (String) page.evaluate("() => document.getElementById('orderLineEditStockTotal').textContent");
    }

    private BigDecimal stockOf(String qualityCode) {
        String value = singleValue("""
                SELECT COALESCE(sum(st.quantity), 0)::text FROM dbo.stock st
                  JOIN dbo.sku s ON s.sku_id = st.sku_id
                  JOIN dbo.enum_quality q ON q.id = st.quality_id
                 WHERE s.code = '%s' AND st.warehouse_id = '%s' AND q.code = '%s'
                """.formatted(SKU, WAREHOUSE, qualityCode));
        return new BigDecimal(value);
    }

    private void chooseQuality(String code) {
        page.selectOption("#orderLineEditQuality", code);
    }

    private void addLine(String quality, String qty) {
        page.evaluate("() => PORTAL.lineModal.add('orders')");
        page.waitForSelector("#orderLineEditOverlay.active");
        page.evaluate("code => PORTAL.lineModal.pick('orders', code)", SKU);
        page.waitForFunction("() => document.getElementById('orderLineEditStockTotal').textContent !== '—'");
        chooseQuality(quality);
        // Упаковки в первой версии не используются (Р9): если у товара есть
        // единица заказа, окно считает количество по упаковкам и поле
        // количества закрыто. Снимаем упаковку и вводим штуки.
        page.evaluate("q => { const p = document.getElementById('orderLineEditPackaging');"
                + " if (p && p.value) { p.value = ''; PORTAL.lineModal.packagingChanged('orders'); }"
                + " const f = document.getElementById('orderLineEditQty'); f.value = q;"
                + " f.dispatchEvent(new Event('input', {bubbles: true})); }", qty);
        page.evaluate("() => PORTAL.lineModal.save('orders')");
    }

    @Test
    void qualityIsChosenFromTheListAndDrivesTheStockFigures() {
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('orders')");
        page.waitForFunction("() => !!PORTAL.card.doc('orders') && !!document.getElementById('of_warehouse')");
        page.evaluate("w => { const f = document.getElementById('of_warehouse'); f.value = w;"
                + " f.dispatchEvent(new Event('change', {bubbles: true})); }", WAREHOUSE);

        page.evaluate("() => PORTAL.lineModal.add('orders')");
        page.waitForSelector("#orderLineEditOverlay.active");
        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) page.evaluate("() => Array.from("
                + "document.getElementById('orderLineEditQuality').options).map(o => o.value + ' ' + o.text)");
        assertEquals(List.of("01 Годный", "02 Брак", "03 Некомплект", "04 Контроль качества"), options);
        assertEquals("01", page.inputValue("#orderLineEditQuality"), "по умолчанию — «Годный»");

        page.evaluate("code => PORTAL.lineModal.pick('orders', code)", SKU);
        page.waitForFunction("() => document.getElementById('orderLineEditStockTotal').textContent !== '—'");
        assertEquals(0, stockOf("01").compareTo(number(stockTotal())), "остаток не по годному: " + stockTotal());
        chooseQuality("02");
        assertEquals(0, stockOf("02").compareTo(number(stockTotal())), "остаток не по браку: " + stockTotal());
        page.evaluate("() => PORTAL.lineModal.close('orders')");

        // Брак и годный — две строки, без вопроса о повторе
        addLine("02", "2");
        addLine("01", "1");
        @SuppressWarnings("unchecked")
        List<String> qualities = (List<String>) page.evaluate(
                "() => PORTAL.card.lines('orders').map(l => PORTAL.card.quality(l))");
        assertEquals(List.of("02", "01"), qualities);
        assertEquals(Boolean.FALSE, page.evaluate(
                "() => document.getElementById('duplicateLineOverlay').classList.contains('active')"));

        // Тот же товар, тот же брак, тот же (пустой) комментарий — повтор
        addLine("02", "5");
        assertTrue((Boolean) page.evaluate(
                        "() => document.getElementById('duplicateLineOverlay').classList.contains('active')"),
                "повтор брака не распознан");
        page.evaluate("() => PORTAL.lineModal.resolveDuplicate('add')");

        // Сохранение и чтение обратно: в базу ушёл код
        fillErp("orders");
        page.evaluate("() => PORTAL.draft.save('orders')");
        page.waitForFunction("() => !!(PORTAL.card.doc('orders') && PORTAL.card.doc('orders').docId)");
        waitForQuiet();
        docId = (String) page.evaluate("() => PORTAL.card.doc('orders').docId");
        assertEquals("02|7.000;01|1.000", singleValue("""
                SELECT string_agg(q.code || '|' || od.ordered_qty::text, ';' ORDER BY od.line_number)
                  FROM dbo.order_details od JOIN dbo.enum_quality q ON q.id = od.quality_id
                 WHERE od.doc_id = '%s'::uuid AND od.batch_number IS NULL
                """.formatted(docId)));
        assertNoJsErrors();
    }

    @AfterEach
    void cleanUp() {
        if (docId != null) {
            inContext("DELETE FROM dbo.order_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.orders WHERE doc_id = '" + docId + "'::uuid");
        }
    }
}
