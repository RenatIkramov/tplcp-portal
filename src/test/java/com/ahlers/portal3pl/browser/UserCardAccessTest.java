package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Доступ человека в его карточке (V110): поклажедатели и склады.
 *
 * <p>У роли «поклажедатель» — выбор одного варианта и никакого «все»;
 * у остальных — флажок «все поклажедатели» и галочки. Склады — у всех.
 * Смена роли перезаполняет доступ по образцу роли (решение заказчика,
 * 11.11.2026); флажок «все» гасит галочки. Сохранённое проверяется
 * по базе, а не по экрану. Клиента вернёт как было PersonalSettings.
 */
class UserCardAccessTest extends BrowserTestBase {

    @Test
    void cardFollowsTheRoleAndSavesWarehouses() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        String id = userId(USER);
        // список пользователей грузится после входа — карточка берёт человека из него
        page.waitForFunction("id => !!document.querySelector('#adminUsersBody tr[data-args*=\"' + id + '\"]')", id);
        page.evaluate("id => portalOpenAdminUser(id)", id);
        page.waitForSelector("#auDepositors input[data-depositor-id]");

        // поклажедатель: одна организация, флажка «все» нет
        assertEquals("radio", page.getAttribute("#auDepositors input[data-depositor-id]", "type"));
        assertFalse(page.isVisible("#auAllDepositors"), "у поклажедателя нет «все поклажедатели»");
        assertEquals(1, count("#auDepositors input[data-depositor-id]:checked"));
        assertTrue(page.isVisible("#auAllWarehouses"), "склады выдаются любой роли");

        // Смена роли перезаполняет доступ по образцу роли (11.11.2026).
        // менеджер: все склады и все поклажедатели, галочки погашены
        page.selectOption("#auRole", "manager");
        page.waitForSelector("#auAllDepositors");
        assertEquals("checkbox", page.getAttribute("#auDepositors input[data-depositor-id]", "type"));
        assertTrue(page.isChecked("#auAllDepositors"), "менеджеру — все поклажедатели");
        assertTrue(page.isChecked("#auAllWarehouses"), "менеджеру — все склады");
        assertEquals(count("#auDepositors input[data-depositor-id]"),
                count("#auDepositors input[data-depositor-id]:disabled"), "«все» не погасил галочки");
        assertEquals(0, count("#auDepositors input[data-depositor-id]:checked"),
                "отмеченное прежней ролью перенеслось в новую");

        // сотрудник склада: все поклажедатели, склад не выбран
        page.selectOption("#auRole", "staff");
        page.waitForFunction("() => document.getElementById('auAllWarehouses')"
                + " && !document.getElementById('auAllWarehouses').checked");
        assertTrue(page.isChecked("#auAllDepositors"), "сотруднику склада — все поклажедатели");
        assertEquals(0, count("#auWarehouses input[data-warehouse-id]:checked"), "складу выбирать ему самому");

        // поклажедатель: все склады, поклажедатель не выбран
        page.selectOption("#auRole", "depositor");
        page.waitForFunction("() => !document.getElementById('auAllDepositors')");
        assertTrue(page.isChecked("#auAllWarehouses"), "поклажедателю — все склады");
        assertEquals(0, count("#auDepositors input[data-depositor-id]:checked"), "поклажедатель должен быть не выбран");

        // выбираем свою организацию, склады — только Алматы, сохраняем
        page.check("#auDepositors input[data-depositor-id='" + depositorId(ADMIN, DEPOSITOR) + "']");
        page.uncheck("#auAllWarehouses");
        for (Object w : (List<?>) page.evaluate(
                "() => [...document.querySelectorAll('#auWarehouses input[data-warehouse-id]')]"
                        + ".map(x => x.getAttribute('data-warehouse-id'))")) {
            if ("WH-ALA".equals(w)) {
                page.check("#auWarehouses input[data-warehouse-id='WH-ALA']");
            } else {
                page.uncheck("#auWarehouses input[data-warehouse-id='" + w + "']");
            }
        }
        page.click("#adminSaveUser");
        page.waitForSelector("#view-admin.active");

        assertEquals(Boolean.FALSE, jdbc.sql("SELECT all_warehouses FROM dbo.app_user WHERE user_id = :id::uuid")
                .param("id", id).query(Boolean.class).single());
        assertEquals(List.of("WH-ALA"), jdbc.sql(
                        "SELECT warehouse_id FROM dbo.user_warehouse_access WHERE user_id = :id::uuid")
                .param("id", id).query(String.class).list());
        assertEquals("depositor", jdbc.sql("""
                        SELECT r.code FROM dbo.app_user u JOIN dbo.enum_user_role r ON r.id = u.role_id
                         WHERE u.user_id = :id::uuid""")
                .param("id", id).query(String.class).single());
        assertNoJsErrors();
    }

    private int count(String selector) {
        return ((Number) page.evaluate("s => document.querySelectorAll(s).length", selector)).intValue();
    }

    /**
     * После создания открывается карточка нового пользователя — с кнопкой
     * «Сохранить», а не «Создать пользователя» (найдено заказчиком
     * 11.11.2026: подпись прежней кнопки доставалась новой), и правка
     * в ней сохраняется.
     */
    @Test
    void createdUserOpensWithSaveButton() {
        String login = "p.card.test@ahlers.com";
        try {
            localeOf(portalAdmin(), "ru");
            signInUi(ADMIN, ADMIN_PASSWORD);
            page.waitForFunction("() => typeof portalOpenAdminUser === 'function'");
            page.evaluate("() => portalOpenAdminUser(null)");
            page.waitForSelector("#auFullName");
            page.fill("#auFullName", "Проверка Карточки");
            page.fill("#auLogin", login);
            page.selectOption("#auRole", "manager");
            page.click("#adminSaveUser");

            page.waitForFunction("l => document.getElementById('auLogin')"
                    + " && document.getElementById('auLogin').value === l"
                    + " && document.getElementById('userIsActive')", login);
            page.waitForFunction("() => !document.getElementById('adminSaveUser').disabled");
            String save = jdbc.sql("""
                            SELECT t.value FROM dbo.ui_text t
                              JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id
                             WHERE t.text_key = 'portal.text.save' AND l.code = 'ru'""")
                    .query(String.class).single();
            assertEquals(save, page.textContent("#adminSaveUser").trim(),
                    "у карточки созданного пользователя не та кнопка");

            page.fill("#auPosition", "Проверяющий");
            page.click("#adminSaveUser");
            page.waitForSelector("#view-admin.active");
            assertEquals("Проверяющий", jdbc.sql("SELECT position FROM dbo.app_user WHERE login = :l")
                    .param("l", login).query(String.class).single());
            assertNoJsErrors();
        } finally {
            for (String table : List.of("user_depositor_access", "user_warehouse_access", "user_settings")) {
                owner().jdbc.sql("DELETE FROM dbo." + table
                        + " WHERE user_id IN (SELECT user_id FROM dbo.app_user WHERE login = :l)")
                        .param("l", login).update();
            }
            owner().jdbc.sql("DELETE FROM dbo.app_user WHERE login = :l").param("l", login).update();
        }
    }
}
