package com.ahlers.portal3pl;

import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import com.ahlers.portal3pl.catalog.SkuWriteService;
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
 * Справочник товаров и упаковок (V22): ведение поклажедателем.
 * <p>
 * Проверяем не «запрос прошёл», а правила: код товара уникален без учёта
 * регистра, архив убирает позицию из рабочего списка, используемый товар
 * удалить нельзя, упаковка по умолчанию одна, складской сотрудник
 * справочник не правит.
 */
class SkuDirectoryTest extends PortalTestBase {

    /** Префикс для тестовых позиций: по нему же их и убираем после теста. */
    private static final String PREFIX = "QA-TEST-";

    private static final String STAFF_USER = "warehouse@ahlers.com";

    @Autowired
    SkuWriteService sku;

    @Autowired
    CatalogService catalogs;

    @AfterEach
    void removeTestItems() {
        inContext("DELETE FROM dbo.product_packaging WHERE sku_id IN"
                + " (SELECT sku_id FROM dbo.sku WHERE code LIKE '" + PREFIX + "%')");
        inContext("UPDATE dbo.sku SET order_unit_id = NULL WHERE code LIKE '" + PREFIX + "%'");
        inContext("DELETE FROM dbo.sku WHERE code LIKE '" + PREFIX + "%'");
    }

    /* ---------------- Товар ---------------- */

    @Test
    void itemCreatedWithRequiredFieldsOnly() {
        signIn();
        Map<String, Object> card = sku.createSku(Map.of(
                "code", PREFIX + "001",
                "name", "Тестовая позиция",
                "uom_id", 1));
        assertNotNull(card.get("skuId"));
        assertEquals(PREFIX + "001", card.get("code"));
        // Решение Р5: новая позиция складу ещё не известна
        assertEquals(Boolean.FALSE, card.get("isInWms"));
        assertEquals(Boolean.TRUE, card.get("isActive"));
        assertEquals(USER, card.get("createdBy"));
        assertEquals(List.of(), card.get("packagings"));
    }

    @Test
    void itemWithoutNameRejected() {
        signIn();
        expectRejection(400, () -> sku.createSku(Map.of("code", PREFIX + "002", "uom_id", 1)));
    }

    @Test
    void duplicateCodeRejectedIgnoringCase() {
        signIn();
        sku.createSku(Map.of("code", PREFIX + "003", "name", "Первая", "uom_id", 1));
        // «qa-test-003» для человека тот же код, что «QA-TEST-003»
        expectRejection(409, () -> sku.createSku(Map.of(
                "code", PREFIX.toLowerCase() + "003", "name", "Вторая", "uom_id", 1)));
    }

    @Test
    void cardSavedAndServiceColumnsIgnored() {
        signIn();
        String id = (String) sku.createSku(Map.of(
                "code", PREFIX + "004", "name", "Было", "uom_id", 1)).get("skuId");

        Map<String, Object> card = sku.updateSku(id, Map.of(
                "name", "Стало",
                "brand", "Тестовый бренд",
                "weight_gross", 2.5,
                // поля синхронизации портал править не даёт — должны быть
                // пропущены молча, а не записаны
                "is_in_wms", true,
                "depositor_id", "00000000-0000-0000-0000-000000000000"));

        assertEquals("Стало", card.get("name"));
        assertEquals("Тестовый бренд", card.get("brand"));
        assertEquals(Boolean.FALSE, card.get("isInWms"), "признак WMS ставит только синхронизация");
        assertEquals(USER, card.get("updatedBy"));
    }

    @Test
    void archivedItemLeavesWorkingList() {
        signIn();
        String id = (String) sku.createSku(Map.of(
                "code", PREFIX + "005", "name", "В архив", "uom_id", 1)).get("skuId");

        assertTrue(codes(false).contains(PREFIX + "005"));
        sku.setSkuActive(id, false);
        assertFalse(codes(false).contains(PREFIX + "005"), "архивная позиция в рабочем списке не нужна");
        assertTrue(codes(true).contains(PREFIX + "005"), "но по запросу архив виден");

        sku.setSkuActive(id, true);
        assertTrue(codes(false).contains(PREFIX + "005"), "из архива можно вернуть");
    }

    @Test
    void unusedItemDeletedUsedItemNot() {
        signIn();
        String id = (String) sku.createSku(Map.of(
                "code", PREFIX + "006", "name", "Удаляемая", "uom_id", 1)).get("skuId");
        sku.deleteSku(id);
        assertFalse(codes(true).contains(PREFIX + "006"));

        // товар из демо-данных используется в документах — его только в архив
        String usedId = singleValue("SELECT sku_id::text FROM dbo.sku s"
                + " WHERE EXISTS (SELECT 1 FROM dbo.receipt_details d WHERE d.sku_id = s.sku_id)"
                + " LIMIT 1");
        expectRejection(409, () -> sku.deleteSku(usedId));
    }

    /* ---------------- Упаковки ---------------- */

    @Test
    void packagingsAddedDefaultChosenAndDeleted() {
        signIn();
        String id = (String) sku.createSku(Map.of(
                "code", PREFIX + "007", "name", "С упаковками", "uom_id", 1)).get("skuId");

        sku.createPackaging(id, Map.of("packaging_code", "BOX", "coefficient", 12));
        List<Map<String, Object>> list =
                sku.createPackaging(id, Map.of("packaging_code", "PAL", "coefficient", 480));
        assertEquals(List.of("BOX", "PAL"), list.stream().map(p -> p.get("packagingCode")).toList());
        assertTrue(list.stream().noneMatch(p -> Boolean.TRUE.equals(p.get("isDefaultOrderUnit"))),
                "пока единицу заказа не выбрали, её нет");

        String boxId = (String) list.getFirst().get("packagingId");
        String palId = (String) list.get(1).get("packagingId");

        assertEquals(List.of(true, false), flags(sku.setDefaultPackaging(boxId)));
        // единица заказа одна: выбор второй снимает первую сам, без отдельного вызова
        assertEquals(List.of(false, true), flags(sku.setDefaultPackaging(palId)));

        // удаляем ту, что выбрана единицей заказа: ссылка снимается, ошибки нет
        assertEquals(List.of("BOX"), sku.deletePackaging(palId).stream()
                .map(p -> p.get("packagingCode")).toList());
    }

    @Test
    void duplicatePackagingCodeRejected() {
        signIn();
        String id = (String) sku.createSku(Map.of(
                "code", PREFIX + "008", "name", "Упаковки", "uom_id", 1)).get("skuId");
        sku.createPackaging(id, Map.of("packaging_code", "BOX", "coefficient", 12));
        expectRejection(409, () ->
                sku.createPackaging(id, Map.of("packaging_code", "box", "coefficient", 24)));
    }

    /* ---------------- Доступ и изоляция ---------------- */

    @Test
    void staffCannotEditDirectory() {
        signInAs(STAFF_USER, DEPOSITOR);
        expectRejection(403, () -> sku.createSku(Map.of(
                "code", PREFIX + "009", "name", "Не должно появиться", "uom_id", 1)));
    }

    @Test
    void foreignItemNotVisible() {
        signIn();
        String id = (String) sku.createSku(Map.of(
                "code", PREFIX + "010", "name", "Своя позиция", "uom_id", 1)).get("skuId");
        // у другого клиента этот товар неотличим от несуществующего
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        expectRejection(404, () -> sku.card(id));
        expectRejection(404, () -> sku.updateSku(id, Map.of("name", "Чужая правка")));
    }

    @Test
    void dictionariesFilled() {
        signIn();
        Map<String, List<Map<String, Object>>> dict = sku.dictionaries();
        // выпадающие списки карточки: без любого из них поле не заполнить
        for (String kind : List.of("uom", "currency", "hazard_class", "hazard_group",
                "packaging_type")) {
            assertFalse(dict.getOrDefault(kind, List.of()).isEmpty(),
                    "список «" + kind + "» не должен быть пустым");
        }
        assertTrue(dict.containsKey("group"), "группы товара нужны выпадающему списку карточки");
        assertTrue(dict.containsKey("type"), "типы товара нужны выпадающему списку карточки");
    }

    /* ---------------- Вспомогательное ---------------- */

    private List<Object> codes(boolean withArchived) {
        return catalogs.list(CatalogType.SKU, null, null, null, withArchived)
                .stream().map(r -> r.get("code")).toList();
    }

    private static List<Object> flags(List<Map<String, Object>> packagings) {
        return packagings.stream().map(p -> p.get("isDefaultOrderUnit")).toList();
    }
}
