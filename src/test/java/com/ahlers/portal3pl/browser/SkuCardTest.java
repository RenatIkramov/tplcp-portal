package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Карточка товара — один список полей на просмотр и правку (11.11.2026).
 *
 * <p>Прежде просмотр рисовал прототип со своими русскими подписями,
 * а правку — sku-edit.js со своими ключами: два списка полей одной
 * карточки, которые могли разойтись. Проверяется, что поле в правке
 * называется так же, как в просмотре, что подписи — из каталога текстов,
 * и что закрытое клиенту поле не рисуется ни там, ни там.
 */
class SkuCardTest extends BrowserTestBase {

    @Autowired
    ColumnService columnService;

    private Runnable restore;

    private void openFirstSku() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();
        openSkuList();
        page.evaluate("() => portalOpenSku(PORTAL.catalog.sku[0].code)");
        page.waitForSelector("#view-sku-detail.active #skuDetailBody .kv");
    }

    @SuppressWarnings("unchecked")
    private List<String> labels() {
        return (List<String>) page.evaluate("() => Array.from(document.querySelectorAll('#skuDetailBody .kv > label'))"
                + ".map(l => l.textContent.replace(/\\s*\\*$/, '').trim())");
    }

    private void tab(String id) {
        page.evaluate("id => PORTAL.skuCard.tab(id)", id);
    }

    /** Текст ключа на нужном языке — из каталога, а не вписанный сюда. */
    private String text(String key, String locale) {
        return jdbc.sql("""
                        SELECT t.value FROM dbo.ui_text t
                          JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                         WHERE t.text_key = :key::text AND l.code = :locale::text
                        """)
                .param("key", key)
                .param("locale", locale)
                .query(String.class)
                .single();
    }

    @Test
    void editCallsFieldsAsTheViewDoes() {
        restore = startFromPortalSetup(DEPOSITOR);
        openFirstSku();
        int compared = 0;
        for (String id : List.of("general", "physical", "storage", "manufacturer", "codes", "attributes")) {
            tab(id);
            List<String> view = labels();
            page.evaluate("() => PORTAL.skuCard.edit()");
            List<String> edit = labels();
            page.evaluate("() => PORTAL.skuCard.cancel()");
            assertFalse(edit.isEmpty(), "вкладка " + id + ": в правке нет ни одного поля");
            for (String label : edit) {
                assertTrue(view.contains(label), "вкладка " + id + ": поле «" + label
                        + "» в правке зовётся не так, как в просмотре: " + view);
                compared++;
            }
        }
        assertTrue(compared > 20, "сравнено слишком мало полей: " + compared);
        assertNoJsErrors();
    }

    @Test
    void labelsComeFromTheCatalogue() {
        restore = startFromPortalSetup(DEPOSITOR);
        openFirstSku();
        List<String> view = labels();
        assertTrue(view.contains(text("sku.text.commercial_code", "ru")),
                "подпись «Коммерческий код» не из каталога: " + view);
        assertTrue(view.contains(text("col.sku.orderUnit", "ru")), "нет поля «Единица заказа»: " + view);
        String code = (String) page.evaluate("() => PORTAL.skuCard.state.row.code");
        assertTrue(page.innerText("#skuDetailBody").contains(code), "в карточке нет кода товара " + code);
        assertNoJsErrors();
    }

    @Test
    void closedFieldIsHiddenInViewAndEdit() {
        restore = startFromPortalSetup(DEPOSITOR);
        PortalUser admin = portalAdmin();
        columnService.saveDepositorColumn(admin, depositorId(USER, DEPOSITOR), "sku", "manufacturer", false, false);
        openFirstSku();
        String manufacturer = text("sku.text.manufacturer", "ru");

        tab("manufacturer");
        assertFalse(labels().contains(manufacturer), "закрытое поле видно в просмотре");
        page.evaluate("() => PORTAL.skuCard.edit()");
        List<String> edit = labels();
        assertFalse(edit.contains(manufacturer), "закрытое поле видно в правке");
        assertFalse(edit.isEmpty(), "вместе с закрытым полем пропали и открытые");
        page.evaluate("() => PORTAL.skuCard.cancel()");
        assertEquals("view", page.evaluate("() => PORTAL.skuCard.state.mode"));
        assertNoJsErrors();
    }

    @AfterEach
    void putBack() {
        if (restore != null) {
            restore.run();
        }
    }
}
