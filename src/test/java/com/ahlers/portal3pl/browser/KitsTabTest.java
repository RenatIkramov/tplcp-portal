package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * «Справочники → Комплекты» и состав вручную в карточке (V154).
 *
 * <p>Составы проверка задаёт сама и после возвращает, какими были
 * (вместе с признаком «Комплект»): на базе разработки состав может быть
 * любым. Возвращает владелец схемы — это в журнал не попадает.
 */
class KitsTabTest extends BrowserTestBase {

    private static final String KIT = "RK-KIT-01";
    /** Обычный товар, который проверка делает комплектом. */
    private static final String NEW_KIT = "RK-MYA-01";

    private final Map<String, String> compositions = new HashMap<>();
    private final Map<String, Boolean> flags = new HashMap<>();

    private String dep() {
        return depositorId(USER, DEPOSITOR);
    }

    private void apply(String kit, String rows) {
        owner().jdbc.sql("SELECT dbo.kit_components_apply(:dep::uuid, :kit, :rows::jsonb)")
                .param("dep", dep()).param("kit", kit).param("rows", rows).query(rs -> { });
    }

    private String composition(String kit) {
        return owner().jdbc.sql("""
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('code', c.code, 'qty', k.qty)
                                                  ORDER BY k.sort_order), '[]')::text
                          FROM dbo.sku_kit_component k
                          JOIN dbo.sku s ON s.sku_id = k.kit_sku_id
                          JOIN dbo.sku c ON c.sku_id = k.component_sku_id
                         WHERE s.code = :kit
                        """).param("kit", kit).query(String.class).single();
    }

    @BeforeEach
    void remember() {
        for (String code : new String[] {KIT, NEW_KIT}) {
            compositions.put(code, composition(code));
            flags.put(code, owner().jdbc.sql("SELECT is_kit FROM dbo.sku WHERE code = :c").param("c", code)
                    .query(Boolean.class).optional().orElse(null));
        }
        apply(NEW_KIT, "[]");
        apply(KIT, "[{\"code\": \"RK-ROG-01\", \"qty\": 2}, {\"code\": \"RK-KOP-01\", \"qty\": 4}]");
    }

    @AfterEach
    void restore() {
        apply(NEW_KIT, compositions.get(NEW_KIT));
        apply(KIT, compositions.get(KIT));
        flags.forEach((code, on) -> owner().jdbc.sql("UPDATE dbo.sku SET is_kit = :k WHERE code = :c")
                .param("k", on).param("c", code).update());
    }

    private void openKits() {
        openSkuList();
        openReferences("kits");
        page.waitForSelector("#ref-sub-kits.active [data-kit='" + KIT + "']");
    }

    /** Клиент видит комплекты и состав, но состав не правит: его ведёт склад. */
    @Test
    void clientSeesKitsAndOpensTheComposition() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        signInUiAsClient();
        openKits();

        assertEquals(2, page.querySelectorAll("[data-kit='" + KIT + "'] [data-kit-part]").size(), "не весь состав");
        assertEquals(0, page.querySelectorAll("#ref-sub-kits [data-kit-add-btn]").size(),
                "клиенту предложено добавить комплект");

        page.click("[data-kit='" + KIT + "']");
        page.waitForSelector("#view-sku-detail.active #skuDetailBody [data-kit-components]");
        assertEquals(0, page.querySelectorAll("#skuDetailBody [data-kit-edit]").size(),
                "клиенту предложено править состав");
        assertNoJsErrors();
    }

    /** Администратор делает товар комплектом и задаёт состав; он появляется во вкладке. */
    @Test
    void adminMakesAKitAndSetsItsComposition() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("id => portalSelectDepositor(id)", dep());
        waitForQuiet();
        openKits();

        page.click("#ref-sub-kits [data-kit-add-btn]");
        page.fill("#kitSkuPick", NEW_KIT);
        page.click("#ref-sub-kits [data-kit-pick]");
        page.waitForSelector("#view-sku-detail.active #skuDetailBody [data-kit-form]");

        page.locator("#skuDetailBody [data-kit-row='0'] [data-kit-code]").fill("RK-ROG-02");
        page.locator("#skuDetailBody [data-kit-row='0'] [data-kit-code]").dispatchEvent("change");
        page.waitForFunction("() => document.querySelector(\"#skuDetailBody [data-kit-row='0'] [data-kit-name]\")"
                + ".textContent.trim() !== ''");
        page.locator("#skuDetailBody [data-kit-row='0'] [data-kit-qty]").fill("3");
        page.click("#skuDetailBody [data-kit-save]");
        page.waitForSelector("#skuDetailBody [data-kit-components] [data-kit-component='RK-ROG-02']");
        assertEquals("[{\"qty\": 3, \"code\": \"RK-ROG-02\"}]", composition(NEW_KIT).replace(".000", ""),
                "состав не сохранился");

        openReferences("kits");
        page.waitForSelector("[data-kit='" + NEW_KIT + "'] [data-kit-part='RK-ROG-02']");
        assertNoJsErrors();
    }
}
