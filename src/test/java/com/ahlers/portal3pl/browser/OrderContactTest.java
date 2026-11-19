package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.catalog.PartnerKind;
import com.ahlers.portal3pl.catalog.PartnerWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Контакт заказа подставляется по месту доставки (V83).
 *
 * <p>Что подставить, решает база ({@code delivery_contact}: свой контакт
 * адреса, иначе контакты грузополучателя) — это проверяет
 * {@code PartnerDirectoryTest}. Здесь — поведение карточки: подстановка
 * идёт вслед за выбором адреса, но набранное руками не стирает, а смена
 * грузополучателя убирает контакт прежней организации.
 */
class OrderContactTest extends BrowserTestBase {

    private static final String PREFIX = "QA-OC-";
    private static final String OWN = "Сидоров С., +7 702 111 11 11";
    private static final String FALLBACK = "Петров П., +7 701 000 00 00";

    @Autowired
    PartnerWriteService partners;

    private String consigneeId;
    private String withContact;
    private String withoutContact;

    private void prepareConsignee() {
        signIn();
        consigneeId = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "1", "name", "ТОО «Проверка контакта»",
                        "contact_name", "Петров П.", "contact_phone", "+7 701 000 00 00"))
                .get("consigneeId");
        // Первый адрес становится основным — он и подставится первым
        withContact = id(partners.addAddress(consigneeId,
                Map.of("address", "Алматы, Контактная, 1", "contact", OWN)), "Алматы, Контактная, 1");
        withoutContact = id(partners.addAddress(consigneeId,
                Map.of("address", "Алматы, Контактная, 2")), "Алматы, Контактная, 2");
    }

    private static String id(List<Map<String, Object>> list, String address) {
        return (String) list.stream().filter(a -> address.equals(a.get("address")))
                .findFirst().orElseThrow().get("addressId");
    }

    private String contact() {
        return (String) page.evaluate(
                "() => String((document.getElementById('of_consigneeContact') || {}).value || '')");
    }

    private void chooseConsignee(String id) {
        page.evaluate("id => { document.getElementById('of_consignee').value = id;"
                + " PORTAL.draft.consigneeChanged(); }", id);
    }

    private void chooseAddress(String id) {
        page.evaluate("id => { document.getElementById('of_deliveryAddress').value = id;"
                + " PORTAL.draft.addressChanged(); }", id);
    }

    @Test
    void contactFollowsTheAddressButKeepsWhatWasTyped() {
        prepareConsignee();
        signInUiAsClient();
        page.evaluate("() => PORTAL.draft.create('orders')");
        page.waitForFunction("() => !!PORTAL.card.doc('orders') && !!document.getElementById('of_consignee')");

        chooseConsignee(consigneeId);
        page.waitForFunction("v => document.getElementById('of_consigneeContact').value === v", OWN);
        assertEquals(withContact, page.evaluate("() => document.getElementById('of_deliveryAddress').value"),
                "основной адрес подставился сам");

        chooseAddress(withoutContact);
        assertEquals(FALLBACK, contact(), "у адреса своего контакта нет — взяты контакты грузополучателя");

        page.fill("#of_consigneeContact", "Особый приёмщик");
        chooseAddress(withContact);
        assertEquals("Особый приёмщик", contact(), "набранное руками смена адреса не стирает");

        // Другой грузополучатель — контакт прежней организации уходит
        String other = (String) page.evaluate("id => Array.from(document.getElementById('of_consignee').options)"
                + ".map(o => o.value).find(v => v && v !== id) || ''", consigneeId);
        chooseConsignee(other);
        page.waitForFunction("() => document.getElementById('of_consigneeContact').value !== 'Особый приёмщик'");
        assertNotEquals(OWN, contact());
        assertNoJsErrors();
    }

    @AfterEach
    void cleanUp() {
        try {
            page.evaluate("() => { if (PORTAL.card.doc('orders'))"
                    + " PORTAL.draft.remove('orders'); }");
        } catch (RuntimeException ignored) {
            // карточка могла не открыться — убирать нечего
        }
        inContext("DELETE FROM dbo.delivery_point WHERE consignee_id IN"
                + " (SELECT consignee_id FROM dbo.consignee WHERE code LIKE '" + PREFIX + "%')");
        inContext("DELETE FROM dbo.consignee WHERE code LIKE '" + PREFIX + "%'");
    }
}
