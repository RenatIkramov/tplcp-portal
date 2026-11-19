package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поле «Объём» в окне упаковки карточки товара и отклик кнопки
 * «Скачать шаблон Excel».
 *
 * <p>Объём окончательно считает база (V82, проверяет
 * {@code PackagingVolumeTest}). Здесь — то, что видит человек: число
 * появляется, пока он вводит размеры, поле при этом не редактируется,
 * а фокус остаётся там, где он печатает.
 */
class PackagingVolumeFieldTest extends BrowserTestBase {

    private static final String VOLUME = "[data-pkg='volume_m3']";

    private void openNewPackagingForm() {
        signInUiAsClient();
        openSkuList();
        String code = (String) page.evaluate("() => PORTAL.catalog.sku[0].code");
        page.evaluate("c => portalOpenSku(c)", code);
        page.waitForFunction("() => document.querySelector('#view-sku-detail .panel-head .actions button')");
        page.evaluate("() => PORTAL.skuCard.tab('packaging')");
        page.evaluate("() => PORTAL.skuCard.packagingAdd()");
        page.waitForSelector(VOLUME);
    }

    private void type(String column, String text) {
        page.click("[data-pkg='" + column + "']");
        page.keyboard().type(text);
    }

    private boolean readOnly() {
        return (Boolean) page.evaluate("s => document.querySelector(s).readOnly", VOLUME);
    }

    @Test
    void volumeFollowsTheDimensionsWhileTyping() {
        openNewPackagingForm();

        page.fill(VOLUME, "0.5");
        type("length_cm", "40");
        type("width_cm", "30");
        assertEquals("0.5", page.inputValue(VOLUME), "двух размеров мало — объём введён руками");
        assertFalse(readOnly());

        type("height_cm", "20");
        assertEquals("0.024", page.inputValue(VOLUME), "40 × 30 × 20 см = 0,024 м³");
        assertTrue(readOnly(), "посчитанный объём руками не правится");
        assertEquals("height_cm", page.evaluate("() => document.activeElement.getAttribute('data-pkg')"),
                "фокус остался в поле, где печатают");

        page.fill("[data-pkg='height_cm']", "");
        page.dispatchEvent("[data-pkg='height_cm']", "input");
        assertFalse(readOnly(), "размер стёрли — объём снова вводится руками");
        assertNoJsErrors();
    }

    /**
     * Пока шаблон готовится, кнопка говорит об этом и не нажимается:
     * повторные клики не превращаются в несколько одинаковых файлов.
     */
    @Test
    void templateButtonRespondsAtOnceAndIgnoresRepeatedClicks() {
        signInUiAsClient();
        openReferences("packaging");
        page.waitForSelector("#packagingTemplateBtn");

        String idle = page.innerText("#packagingTemplateBtn");
        // Запрос шаблона придерживаем в самой странице, чтобы успеть увидеть
        // состояние «готовлю», и заодно считаем, сколько раз его отправили.
        page.evaluate("() => { window.__templateRequests = 0; const real = window.fetch;"
                + " window.fetch = (url, opts) => {"
                + "   if (String(url).includes('/api/packaging/excel-template')) {"
                + "     window.__templateRequests++;"
                + "     return new Promise(r => setTimeout(r, 800)).then(() => real(url, opts));"
                + "   }"
                + "   return real(url, opts); }; }");

        page.waitForDownload(() -> {
            page.click("#packagingTemplateBtn");
            assertTrue(page.isDisabled("#packagingTemplateBtn"), "кнопка не заблокировалась");
            assertFalse(idle.equals(page.innerText("#packagingTemplateBtn")),
                    "надпись не сменилась на «готовлю»");
            page.click("#packagingTemplateBtn", new com.microsoft.playwright.Page.ClickOptions().setForce(true));
        });

        page.waitForFunction("() => !document.getElementById('packagingTemplateBtn').disabled");
        assertEquals(idle, page.innerText("#packagingTemplateBtn"), "надпись не вернулась");
        assertEquals(1, ((Number) page.evaluate("() => window.__templateRequests")).intValue(),
                "повторный клик ушёл на сервер");
        assertNoJsErrors();
    }
}
