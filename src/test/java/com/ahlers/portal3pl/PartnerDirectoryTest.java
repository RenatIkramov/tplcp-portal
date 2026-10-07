package com.ahlers.portal3pl;

import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import com.ahlers.portal3pl.catalog.PartnerKind;
import com.ahlers.portal3pl.catalog.PartnerWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Справочники контрагентов (V23): поставщики, грузополучатели и адреса.
 * <p>
 * Главное здесь — адреса: основной ровно один, он не теряется при удалении
 * и не пропадает у грузополучателя с единственным адресом.
 */
class PartnerDirectoryTest extends PortalTestBase {

    private static final String PREFIX = "QA-P-";
    private static final String STAFF_USER = "warehouse@ahlers.com";

    @Autowired
    PartnerWriteService partners;

    @Autowired
    CatalogService catalogs;

    @AfterEach
    void removeTestRows() {
        inContext("DELETE FROM dbo.delivery_point WHERE consignee_id IN"
                + " (SELECT consignee_id FROM dbo.consignee WHERE code LIKE '" + PREFIX + "%')");
        inContext("DELETE FROM dbo.consignee WHERE code LIKE '" + PREFIX + "%'");
        inContext("DELETE FROM dbo.supplier WHERE code LIKE '" + PREFIX + "%'");
    }

    /* ---------------- Карточка ---------------- */

    @Test
    void supplierCreatedAndSaved() {
        signIn();
        Map<String, Object> card = partners.create(PartnerKind.SUPPLIER,
                Map.of("code", PREFIX + "S1", "name", "ТОО «Тест»"));
        assertNotNull(card.get("supplierId"));
        assertEquals(Boolean.TRUE, card.get("isActive"));

        String id = (String) card.get("supplierId");
        Map<String, Object> saved = partners.update(PartnerKind.SUPPLIER, id,
                Map.of("name", "ТОО «Тест-2»", "contact_phone", "+7 701 000 00 00",
                        // depositor_id портал не меняет — поле должно быть пропущено
                        "depositor_id", "00000000-0000-0000-0000-000000000000"));
        assertEquals("ТОО «Тест-2»", saved.get("name"));
        assertEquals("+7 701 000 00 00", saved.get("contactPhone"));
    }

    @Test
    void nameRequiredAndCodeUniqueIgnoringCase() {
        signIn();
        expectRejection(400, () -> partners.create(PartnerKind.SUPPLIER,
                Map.of("code", PREFIX + "S2")));
        partners.create(PartnerKind.SUPPLIER, Map.of("code", PREFIX + "S3", "name", "Первый"));
        expectRejection(409, () -> partners.create(PartnerKind.SUPPLIER,
                Map.of("code", PREFIX.toLowerCase() + "s3", "name", "Второй")));
    }

    @Test
    void archivedPartnerLeavesWorkingList() {
        signIn();
        String id = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "C1", "name", "В архив")).get("consigneeId");

        assertTrue(codes(CatalogType.CONSIGNEES, false).contains(PREFIX + "C1"));
        partners.setActive(PartnerKind.CONSIGNEE, id, false);
        assertFalse(codes(CatalogType.CONSIGNEES, false).contains(PREFIX + "C1"));
        assertTrue(codes(CatalogType.CONSIGNEES, true).contains(PREFIX + "C1"));

        partners.setActive(PartnerKind.CONSIGNEE, id, true);
        assertTrue(codes(CatalogType.CONSIGNEES, false).contains(PREFIX + "C1"));
    }

    @Test
    void usedPartnerNotDeleted() {
        signIn();
        String id = (String) partners.create(PartnerKind.SUPPLIER,
                Map.of("code", PREFIX + "S4", "name", "Удаляемый")).get("supplierId");
        partners.delete(PartnerKind.SUPPLIER, id);
        assertFalse(codes(CatalogType.SUPPLIERS, true).contains(PREFIX + "S4"));

        // поставщик из демо-данных указан в поступлениях — его только в архив
        String used = singleValue("SELECT s.supplier_id::text FROM dbo.supplier s"
                + " WHERE EXISTS (SELECT 1 FROM dbo.receipts r WHERE r.supplier_id = s.supplier_id)"
                + " LIMIT 1");
        expectRejection(409, () -> partners.delete(PartnerKind.SUPPLIER, used));
    }

    /* ---------------- Адреса доставки ---------------- */

    @Test
    void firstAddressBecomesDefaultAndOnlyOneStays() {
        signIn();
        String id = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "C2", "name", "С адресами")).get("consigneeId");

        List<Map<String, Object>> list = partners.addAddress(id, Map.of("address", "Алматы, Складская, 2"));
        assertEquals(List.of(true), flags(list), "единственный адрес обязан быть основным");

        list = partners.addAddress(id, Map.of("address", "Шымкент, Байтурсынова, 7"));
        assertEquals(1, defaults(list), "второй адрес основным сам не становится");

        String second = (String) list.stream()
                .filter(a -> !Boolean.TRUE.equals(a.get("isDefault")))
                .findFirst().orElseThrow().get("addressId");
        list = partners.setDefaultAddress(second);
        assertEquals(1, defaults(list), "основной остаётся ровно один");
        assertEquals(second, list.getFirst().get("addressId"), "основной идёт первым");
    }

    @Test
    void duplicateAddressRejected() {
        signIn();
        String id = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "C3", "name", "Повтор")).get("consigneeId");
        partners.addAddress(id, Map.of("address", "Алматы, Складская, 2"));
        expectRejection(409, () -> partners.addAddress(id, Map.of("address", "алматы, складская, 2")));
        expectRejection(400, () -> partners.addAddress(id, Map.of("address", "   ")));
    }

    @Test
    void deletingDefaultAddressPromotesAnother() {
        signIn();
        String id = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "C4", "name", "Удаление адреса")).get("consigneeId");
        partners.addAddress(id, Map.of("address", "Алматы, Складская, 2"));
        List<Map<String, Object>> list = partners.addAddress(id, Map.of("address", "Шымкент, Байтурсынова, 7"));

        String main = (String) list.stream()
                .filter(a -> Boolean.TRUE.equals(a.get("isDefault")))
                .findFirst().orElseThrow().get("addressId");
        list = partners.deleteAddress(main);
        assertEquals(1, list.size());
        assertEquals(1, defaults(list), "грузополучатель не остаётся без основного адреса");

        // последний адрес удаляется без замены — основного просто нет
        assertTrue(partners.deleteAddress((String) list.getFirst().get("addressId")).isEmpty());
    }

    @Test
    void consigneeDeletedTogetherWithAddresses() {
        signIn();
        String id = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "C5", "name", "С адресом")).get("consigneeId");
        partners.addAddress(id, Map.of("address", "Алматы, Складская, 2"));
        partners.delete(PartnerKind.CONSIGNEE, id);
        assertFalse(codes(CatalogType.CONSIGNEES, true).contains(PREFIX + "C5"));
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.delivery_point"
                + " WHERE consignee_id = '" + id + "'::uuid"));
    }

    /* ---------------- Доступ и изоляция ---------------- */

    @Test
    void staffCannotEditDirectories() {
        signInAs(STAFF_USER, DEPOSITOR);
        expectRejection(403, () -> partners.create(PartnerKind.SUPPLIER,
                Map.of("code", PREFIX + "S9", "name", "Не должен появиться")));
    }

    @Test
    void foreignPartnerNotVisible() {
        signIn();
        String id = (String) partners.create(PartnerKind.CONSIGNEE,
                Map.of("code", PREFIX + "C9", "name", "Свой")).get("consigneeId");
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        expectRejection(404, () -> partners.card(PartnerKind.CONSIGNEE, id));
        expectRejection(404, () -> partners.update(PartnerKind.CONSIGNEE, id,
                Map.of("name", "Чужая правка")));
    }

    /* ---------------- Вспомогательное ---------------- */

    private List<Object> codes(CatalogType type, boolean withArchived) {
        return catalogs.list(type, null, null, null, withArchived)
                .stream().map(r -> r.get("code")).toList();
    }

    private static List<Object> flags(List<Map<String, Object>> addresses) {
        return addresses.stream().map(a -> a.get("isDefault")).toList();
    }

    private static long defaults(List<Map<String, Object>> addresses) {
        return addresses.stream().filter(a -> Boolean.TRUE.equals(a.get("isDefault"))).count();
    }
}
