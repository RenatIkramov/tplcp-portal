package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.catalog.PartnerKind;
import com.ahlers.portal3pl.catalog.PartnerWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * То, что ввёл клиент, на экране остаётся текстом и не выполняется
 * (аудит 11.11.2026, S1).
 *
 * <p>НАЙДЕНО АУДИТОМ. Щелчок по строке списка собирался из кода товара
 * прямо в текст скрипта: {@code onclick="portalOpenSku('<код>')"}.
 * Кавычка экранировалась обратной косой, а сама косая — нет, и код
 * {@code \');window.__xss=1;//} закрывал строку: дальше браузер выполнял
 * то, что клиент вписал в код товара, — у менеджера или администратора,
 * щёлкнувшего по строке. Так же собирался выбор товара в окне строки
 * документа. А места доставки шли в {@code <option>} через innerHTML с
 * «экранированием» одной кавычки — тег в адресе становился тегом.
 *
 * <p>Проверка — через экран: дыра была в разметке, которую собирает
 * браузер, а сервер всё отдавал как есть и правильно.
 */
class ScriptInjectionTest extends BrowserTestBase {

    private static final String PREFIX = "QA-XSS-";
    /** Код товара, который закрывал строку скрипта в onclick. */
    private static final String CODE = PREFIX + "\\');window.__xss=1;//";
    private static final String NAME = PREFIX + "товар-ловушка";
    /** Место доставки с тегом: прежде становилось картинкой с обработчиком. */
    private static final String ADDRESS = PREFIX + "<img src=x onerror=\"window.__xss=2\">";

    @Autowired
    PartnerWriteService partners;

    private void createTrapSku() {
        inContext("INSERT INTO dbo.sku (depositor_id, code, name, is_active)"
                + " SELECT depositor_id, '" + CODE.replace("'", "''") + "', '" + NAME + "', true"
                + " FROM dbo.depositor WHERE code = '" + DEPOSITOR + "'");
    }

    private Object injected() {
        return page.evaluate("() => window.__xss === undefined ? null : window.__xss");
    }

    /** Строка списка товаров с таким кодом открывает карточку и ничего не выполняет. */
    @Test
    void skuCodeInListRowIsNotExecuted() {
        createTrapSku();
        signInUiAsClient();
        openSkuList();
        page.waitForFunction("c => PORTAL.catalog.sku.some(s => s.code === c)", CODE);
        // Страница с сервера уже пришла — иначе её ответ перерисует список поверх ловушки
        page.waitForFunction("() => PORTAL.page && Array.isArray(PORTAL.page.sku) && PORTAL.page.sku.length > 0");
        // Ловушка — на текущую страницу списка: искать её по страницам незачем,
        // проверяется сборка строки, а не листание.
        page.evaluate("c => { PORTAL.page.sku = [PORTAL.catalog.sku.find(s => s.code === c)];"
                + " PORTAL.pageTotal.sku = 1; PORTAL.tables.redraw('sku');"
                + " PORTAL.skuCard.state.skuId = null; }", CODE);

        assertEquals(0, ((Number) page.evaluate(
                "() => document.querySelectorAll('#skuBody tr[onclick]').length")).intValue(),
                "строка списка всё ещё собирает скрипт из данных");
        page.click("#skuBody tr[data-args*='" + PREFIX + "']");
        page.waitForFunction("() => !!PORTAL.skuCard.state.skuId");
        assertNull(injected(), "код товара выполнился как скрипт");
        String opened = (String) page.evaluate("() => PORTAL.skuCard.state.row && PORTAL.skuCard.state.row.code");
        assertEquals(CODE, opened, "открылась не та карточка");
        assertNoJsErrors();
    }

    /** Выбор такого товара в окне строки документа: товар выбран, ничего не выполнено. */
    @Test
    void skuCodeInLineModalIsNotExecuted() {
        createTrapSku();
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('receipts')");
        page.waitForFunction("() => !!PORTAL.card.doc('receipts')");
        page.evaluate("() => PORTAL.lineModal.add('receipts')");
        page.waitForSelector("#lineEditOverlay.active");
        page.waitForFunction("c => ((PORTAL.draft.refs || {}).skuList || []).some(s => s.code === c)", CODE);
        page.evaluate("q => PORTAL.lineModal.search('receipts', q)", NAME);
        page.waitForSelector("#lineEditProductResults [data-press]");
        page.dispatchEvent("#lineEditProductResults [data-press]", "mousedown");

        assertNull(injected(), "код товара выполнился как скрипт");
        String chosen = (String) page.evaluate("() => document.getElementById('lineEditProductSearch').value");
        assertTrue(chosen.startsWith(CODE), "товар не выбран: " + chosen);
        assertNoJsErrors();
    }

    /** Место доставки с тегом — текст пункта списка, а не картинка с обработчиком. */
    @Test
    void deliveryAddressIsText() {
        signIn();
        String consigneeId = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "1", "name", "ТОО «Проверка адреса»")).get("consigneeId");
        partners.addAddress(consigneeId, Map.of("address", ADDRESS));

        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('orders')");
        page.waitForFunction("() => !!PORTAL.card.doc('orders') && !!document.getElementById('of_consignee')");
        page.evaluate("id => { document.getElementById('of_consignee').value = id;"
                + " PORTAL.draft.consigneeChanged(); }", consigneeId);
        page.waitForFunction("a => Array.from(document.getElementById('of_deliveryAddress').options)"
                + ".some(o => o.text === a)", ADDRESS);
        page.waitForTimeout(300);   // картинке с ошибкой загрузки — время сработать

        assertNull(injected(), "адрес выполнился как скрипт");
        assertEquals(0, ((Number) page.evaluate(
                "() => document.querySelectorAll('#of_deliveryAddress img').length")).intValue(),
                "адрес стал разметкой");
        assertNoJsErrors();
    }

    @AfterEach
    void cleanUp() {
        for (String type : new String[]{"receipts", "orders"}) {
            try {
                page.evaluate("t => { const d = PORTAL.card.doc(t); if (d && d.docId)"
                        + " return fetch('/api/' + t + '/' + d.docId, {method: 'DELETE'}); }", type);
            } catch (RuntimeException ignored) {
                // карточка могла не открыться — убирать нечего
            }
        }
        inContext("DELETE FROM dbo.sku WHERE code LIKE '" + PREFIX + "%'");
        inContext("DELETE FROM dbo.delivery_point WHERE consignee_id IN"
                + " (SELECT consignee_id FROM dbo.consignee WHERE code LIKE '" + PREFIX + "%')");
        inContext("DELETE FROM dbo.consignee WHERE code LIKE '" + PREFIX + "%'");
    }
}
