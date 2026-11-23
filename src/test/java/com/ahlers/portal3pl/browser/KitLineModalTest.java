package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Окно строки поступления: комплект сразу становится комплектующими
 * (V153). Состав проверка задаёт сама и после возвращает прежний.
 */
class KitLineModalTest extends BrowserTestBase {

    private static final String KIT = "RK-KIT-01";

    private String before;
    private Boolean wasKit;

    private void compose(String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", depositorId(USER, DEPOSITOR)).param("kit", KIT).param("rows", rows).query(rs -> { });
    }

    @BeforeEach
    void composeKit() {
        before = owner().jdbc.sql("""
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('code', c.code, 'qty', k.qty)
                                                  ORDER BY k.sort_order), '[]')::text
                          FROM dbo.sku_kit_component k
                          JOIN dbo.sku s ON s.sku_id = k.kit_sku_id
                          JOIN dbo.sku c ON c.sku_id = k.component_sku_id
                         WHERE s.code = :kit
                        """).param("kit", KIT).query(String.class).single();
        wasKit = owner().jdbc.sql("SELECT is_kit FROM dbo.sku WHERE code = :kit").param("kit", KIT)
                .query(Boolean.class).optional().orElse(null);
        compose("[{\"code\": \"RK-ROG-01\", \"qty\": 2}, {\"code\": \"RK-KOP-01\", \"qty\": 4}]");
    }

    @AfterEach
    void restore() {
        compose(before);
        owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :kit").param("k", wasKit).param("kit", KIT).update();
    }

    @Test
    @SuppressWarnings("unchecked")
    void kitInReceiptBecomesComponentsAtOnce() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('receipts')");
        page.waitForFunction("() => !!PORTAL.card.doc('receipts')");
        page.evaluate("() => PORTAL.lineModal.add('receipts')");
        page.waitForSelector("#lineEditOverlay.active");
        page.evaluate("code => PORTAL.lineModal.pick('receipts', code)", KIT);
        // у демо-комплекта упаковка по умолчанию — количество тогда считает она; вводим штуками
        page.evaluate("() => { const s = document.getElementById('lineEditPackaging');"
                + " if (s) { s.value = ''; PORTAL.lineModal.packagingChanged('receipts'); } }");
        page.fill("#lineEditExpected", "3");
        page.evaluate("() => PORTAL.lineModal.save('receipts')");
        page.waitForFunction("() => !document.getElementById('lineEditOverlay').classList.contains('active')");

        List<String> lines = (List<String>) page.evaluate(
                "() => PORTAL.card.lines('receipts').map(l => l.code + '|' + l.expected)");
        assertEquals(List.of("RK-ROG-01|6", "RK-KOP-01|12"), lines, "комплект в окне не разложился");
        assertNoJsErrors();
    }
}
