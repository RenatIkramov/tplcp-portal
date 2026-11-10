package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.PortalDirectoryService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
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
 * Справочники уровня портала (V24): склады, поклажедатели, типы заказов
 * и типы приходных документов.
 * <p>
 * Главное, что здесь проверяется: вести их может только администратор,
 * архивный склад исчезает из выбора в документах, а запись, на которую
 * ссылается история, удалить нельзя.
 */
class PortalDirectoryTest extends PortalTestBase {

    private static final String WAREHOUSE = "QA-WH";
    private static final String DEPOSITOR_CODE = "QA-D1";
    /** Код проверочного типа: две цифры (V88), последний из свободных. */
    @Autowired
    PortalDirectoryService directory;

    @Autowired
    CatalogService catalogs;

    @AfterEach
    void removeTestRows() {
        jdbc.sql("DELETE FROM dbo.user_warehouse_access WHERE warehouse_id LIKE 'QA-%'").update();
        inContext("DELETE FROM dbo.warehouse WHERE warehouse_id LIKE 'QA-%'");
        // Поклажедатели под RLS: обычным запросом чужие строки не удалить,
        // убираем тем же способом, каким их заводит портал
        new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(tx -> jdbc.sql("""
                        SELECT dbo.admin_delete_depositor(depositor_id::uuid)
                        FROM dbo.admin_depositor_list() WHERE code LIKE 'QA-%'
                        """).query().listOfRows());
    }

    /* ---------------- Склады ---------------- */

    @Test
    void warehouseCreatedEditedArchivedAndDeleted() {
        PortalUser admin = portalAdmin();

        Map<String, Object> card = directory.createWarehouse(admin, Map.of(
                "warehouse_id", WAREHOUSE, "code", WAREHOUSE, "name", "Склад проверки"));
        assertEquals(WAREHOUSE, card.get("warehouseId"));
        assertEquals(Boolean.TRUE, card.get("isActive"));

        card = directory.updateWarehouse(admin, WAREHOUSE, Map.of("name", "Склад проверки 2",
                // идентификатор правке не подлежит: по нему идёт обмен с WMS
                "warehouse_id", "ПОДМЕНА"));
        assertEquals("Склад проверки 2", card.get("name"));
        assertEquals(WAREHOUSE, card.get("warehouseId"));

        // Склады выдаются человеку (V110). С «все склады» новый склад
        // доступен сам; со списком — только после выдачи. Проверяем оба,
        // а не «виден всем подряд». Как было, вернёт PersonalSettings.
        assertTrue(warehouseCodes().contains(WAREHOUSE), "«все склады» — новый склад доступен сам");
        String user = userId(USER);
        jdbc.sql("UPDATE dbo.app_user SET all_warehouses = false WHERE user_id = :id::uuid")
                .param("id", user).update();
        assertFalse(warehouseCodes().contains(WAREHOUSE), "без выданного доступа склада не видно");
        jdbc.sql("INSERT INTO dbo.user_warehouse_access (user_id, warehouse_id) VALUES (:id::uuid, :w)")
                .param("id", user).param("w", WAREHOUSE).update();
        assertTrue(warehouseCodes().contains(WAREHOUSE));
        directory.setWarehouseActive(admin, WAREHOUSE, false);
        assertFalse(warehouseCodes().contains(WAREHOUSE), "архивный склад в документах не предлагается");

        // выданный людям склад удалить нельзя — сперва забрать доступ
        expectRejection(409, () -> directory.deleteWarehouse(admin, WAREHOUSE));
        jdbc.sql("DELETE FROM dbo.user_warehouse_access WHERE warehouse_id = :w")
                .param("w", WAREHOUSE).update();
        directory.deleteWarehouse(admin, WAREHOUSE);
        assertTrue(directory.warehouses(admin).stream()
                .noneMatch(w -> WAREHOUSE.equals(w.get("warehouseId"))));
    }

    @Test
    void warehouseNeedsIdAndName() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> directory.createWarehouse(admin,
                Map.of("code", WAREHOUSE, "name", "Без идентификатора")));
        expectRejection(400, () -> directory.createWarehouse(admin,
                Map.of("warehouse_id", WAREHOUSE, "code", WAREHOUSE)));
    }

    @Test
    void usedWarehouseNotDeleted() {
        PortalUser admin = portalAdmin();
        String used = singleValue("SELECT warehouse_id FROM dbo.warehouse"
                + " WHERE EXISTS (SELECT 1 FROM dbo.receipts r WHERE r.warehouse_id = warehouse.warehouse_id)"
                + " LIMIT 1");
        expectRejection(409, () -> directory.deleteWarehouse(admin, used));
    }

    /* ---------------- Поклажедатели ---------------- */

    @Test
    void depositorCreatedAndEdited() {
        PortalUser admin = portalAdmin();
        Map<String, Object> card = directory.saveDepositor(admin, null,
                new PortalDirectoryService.Depositor(DEPOSITOR_CODE, "ТОО «Проверка»",
                        "123456789012", "Алматы", "+7 727 000 00 00", "qa@example.com",
                        "Д-100", "2027-12-31"));
        assertNotNull(card.get("depositorId"));
        assertEquals("ТОО «Проверка»", card.get("orgName"));
        assertEquals(0L, ((Number) card.get("userCount")).longValue(),
                "у нового поклажедателя ещё нет пользователей");

        String id = (String) card.get("depositorId");
        card = directory.saveDepositor(admin, id,
                new PortalDirectoryService.Depositor(DEPOSITOR_CODE, "ТОО «Проверка-2»",
                        "123456789012", "Алматы", "+7 727 000 00 00", "qa@example.com",
                        "Д-100", "2027-12-31"));
        assertEquals("ТОО «Проверка-2»", card.get("orgName"));

        directory.setDepositorActive(admin, id, false);
        assertEquals(Boolean.FALSE, directory.depositor(admin, id).get("isActive"));

        directory.deleteDepositor(admin, id);
        expectRejection(404, () -> directory.depositor(admin, id));
    }

    @Test
    void depositorNeedsCodeAndName() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> directory.saveDepositor(admin, null,
                new PortalDirectoryService.Depositor(DEPOSITOR_CODE, "  ",
                        null, null, null, null, null, null)));
    }

    @Test
    void usedDepositorNotDeleted() {
        PortalUser admin = portalAdmin();
        String id = depositorId(ADMIN, DEPOSITOR);
        expectRejection(409, () -> directory.deleteDepositor(admin, id));
    }

    /* ---------------- Доступ ---------------- */

    @Test
    void onlyAdminManagesPortalDirectories() {
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> directory.warehouses(client));
        expectRejection(403, () -> directory.depositors(client));
        expectRejection(403, () -> directory.createWarehouse(client,
                Map.of("warehouse_id", WAREHOUSE, "code", WAREHOUSE, "name", "Чужой склад")));
    }

    /* ---------------- Вспомогательное ---------------- */

    /** Склады так, как их видит экран документов: под контекстом поклажедателя. */
    private List<Object> warehouseCodes() {
        signIn();
        try {
            return catalogs.list(CatalogType.WAREHOUSES, false)
                    .stream().map(r -> r.get("code")).toList();
        } finally {
            com.ahlers.portal3pl.config.DepositorContext.clear();
        }
    }


    /* ================= Организации оператора (V33) ================= */

    @Test
    void operatorDirectoryListsAndEdits() {
        PortalUser admin = portalAdmin();
        List<Map<String, Object>> list = directory.operators(admin);
        assertFalse(list.isEmpty(), "организации засеяны ещё на этапе 1");

        String id = String.valueOf(list.getFirst().get("operatorId"));
        Map<String, Object> card = directory.operator(admin, id);
        // в списке длинных текстов нет, в карточке есть — в таблице от них
        // пользы ноль, а весить будут
        assertFalse(list.getFirst().containsKey("aboutContent"));
        assertTrue(card.containsKey("aboutContent"));
        assertTrue(card.containsKey("contactsContent"));
    }

    @Test
    void operatorKeepsPortalPages() {
        PortalUser admin = portalAdmin();
        // на своей записи, а не на засеянной: анкета уходит целиком,
        // и правка затёрла бы остальным полям значения из seed-данных
        Map<String, Object> own = directory.saveOperator(admin, null,
                new PortalDirectoryService.Operator("PAGES-OP", "Для страниц",
                        null, null, null, null, null, null, null, null));
        String id = String.valueOf(own.get("operatorId"));

        Map<String, Object> saved = directory.saveOperator(admin, id,
                new PortalDirectoryService.Operator("PAGES-OP", "Для страниц",
                        null, null, null, null, null, null,
                        "О нас: первый абзац.\nВторой абзац.", "Контакты: Алматы"));
        assertEquals("О нас: первый абзац.\nВторой абзац.", saved.get("aboutContent"),
                "содержимое страницы «О нас» сохраняется как есть, вместе с переносами");
        assertEquals("Контакты: Алматы", saved.get("contactsContent"));
        directory.deleteOperator(admin, id);
    }

    @Test
    void operatorRequiresCodeAndName() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> directory.saveOperator(admin, null,
                new PortalDirectoryService.Operator(null, "Без кода",
                        null, null, null, null, null, null, null, null)));
        expectRejection(400, () -> directory.saveOperator(admin, null,
                new PortalDirectoryService.Operator("NO-NAME", "  ",
                        null, null, null, null, null, null, null, null)));
        expectRejection(404, () -> directory.operator(admin, "нет-такой"));
    }

    @Test
    void operatorCodeIsUniqueIgnoringCase() {
        PortalUser admin = portalAdmin();
        String code = String.valueOf(directory.operators(admin).getFirst().get("code"));
        expectRejection(409, () -> directory.saveOperator(admin, null,
                new PortalDirectoryService.Operator(code.toLowerCase(), "Дубль",
                        null, null, null, null, null, null, null, null)));
    }

    @Test
    void operatorInUseIsNotDeleted() {
        PortalUser admin = portalAdmin();
        Map<String, Object> used = directory.operators(admin).stream()
                .filter(o -> ((Number) o.get("depositorCount")).intValue() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("нужна организация с поклажедателями"));
        String id = String.valueOf(used.get("operatorId"));
        // внешний ключ и так не даст, но человеку нужно объяснение,
        // а не текст ошибки базы
        expectRejection(409, () -> directory.deleteOperator(admin, id));
    }

    @Test
    void unusedOperatorIsCreatedAndDeleted() {
        PortalUser admin = portalAdmin();
        int before = directory.operators(admin).size();
        Map<String, Object> created = directory.saveOperator(admin, null,
                new PortalDirectoryService.Operator("TEST-OP", "Тестовая организация",
                        null, null, null, null, null, null, null, null));
        assertEquals(before + 1, directory.operators(admin).size());

        directory.deleteOperator(admin, String.valueOf(created.get("operatorId")));
        assertEquals(before, directory.operators(admin).size(),
                "неиспользованную организацию удалить можно");
    }

    @Test
    void onlyAdminSeesOperatorDirectory() {
        signIn();
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> directory.operators(client));
        expectRejection(403, () -> directory.saveOperator(client, null,
                new PortalDirectoryService.Operator("X", "Y",
                        null, null, null, null, null, null, null, null)));
    }
}
