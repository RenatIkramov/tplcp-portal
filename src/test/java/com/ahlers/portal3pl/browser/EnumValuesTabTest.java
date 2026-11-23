package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Группа «Перечисления» (V95): значение создаётся и правится в окне,
 * там же — переводы.
 *
 * <p>Правила записи проверяет {@code EnumValueTest}. Здесь — экран:
 * окно открывается из списка, переводы — только на включённые языки,
 * отказ сервера виден в самом окне, а системное значение не предлагает
 * того, что сервер всё равно запретит. У статуса — галочка «можно править
 * транспорт» (V150).
 */
class EnumValuesTabTest extends BrowserTestBase {

    @AfterEach
    void cleanUp() {
        inContext("DELETE FROM dbo.enum_text t WHERE t.enum_key = 'enum_order_type'"
                + " AND t.value_id IN (SELECT id::text FROM dbo.enum_order_type WHERE code = '98')");
        inContext("DELETE FROM dbo.enum_order_type WHERE code = '98'");
    }

    private void openOrderTypes() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => { showView('admin'); showAdminGroup('enumValues'); }");
        page.waitForSelector("#enumValuesList [data-enum-key='enum_order_type']");
        page.click("#enumValuesList [data-enum-key='enum_order_type']");
        page.waitForSelector("#enumValuesBody tr[data-value-id]");
    }

    @Test
    void valueIsCreatedTranslatedAndDeletedInTheWindow() {
        openOrderTypes();
        assertEquals(singleValue("SELECT count(*)::text FROM dbo.enum_registry"),
                String.valueOf(page.querySelectorAll("#enumValuesList [data-enum-key]").size()),
                "слева — все перечисления реестра");

        page.click("#enumValuesAdd");
        page.waitForSelector("#enumValueOverlay.active");
        assertEquals(0, page.querySelectorAll("#enumValueFlag").size(),
                "у типов заказов нет признака «да/нет» — и галочки в окне быть не должно");
        // Переводы — только на включённые языки, кроме основного
        assertEquals(singleValue("SELECT count(*)::text FROM dbo.dictionary_locale"
                        + " WHERE COALESCE(is_active, false) AND NOT COALESCE(is_default, false)"),
                String.valueOf(page.querySelectorAll("#enumValueFields [data-part='name']").size()));
        assertEquals(0, page.querySelectorAll("#enumValueFields [data-locale='kk']").size(),
                "выключенного языка в окне быть не должно");

        // Отказ сервера — в окне, и окно не закрывается: человек
        // исправит поле, а не наберёт всё заново
        page.fill("#enumValueCode", "1");
        page.fill("#enumValueName", "QA order type");
        page.fill("#enumValueFields [data-locale='ru'][data-part='name']", "Проверочный тип");
        page.click("#enumValueSave");
        page.waitForSelector("#enumValueError:not([style*='none'])");
        assertTrue((Boolean) page.evaluate(
                "() => document.getElementById('enumValueOverlay').classList.contains('active')"));

        page.fill("#enumValueCode", "98");
        page.click("#enumValueSave");
        page.waitForFunction("() => !document.getElementById('enumValueOverlay').classList.contains('active')");
        page.waitForFunction("() => PORTAL.enumValues.rows.some(r => r.enumKey === 'enum_order_type'"
                + " && r.code === '98' && (r.values.ru || {}).name === 'Проверочный тип')");
        assertTrue(page.textContent("#enumValuesBody").contains("Проверочный тип"),
                "перевод должен быть виден в списке");

        String id = singleValue("SELECT id::text FROM dbo.enum_order_type WHERE code = '98'");
        page.click("#enumValuesBody tr[data-value-id='" + id + "']");
        page.waitForSelector("#enumValueOverlay.active");
        assertEquals("Проверочный тип",
                page.inputValue("#enumValueFields [data-locale='ru'][data-part='name']"));
        assertFalse(page.isDisabled("#enumValueDelete"), "свободное значение удалить можно");
        page.click("#enumValueDelete");
        page.click("#enumValueConfirmYes");
        page.waitForFunction("() => !PORTAL.enumValues.rows.some(r => r.enumKey === 'enum_order_type'"
                + " && r.code === '98')");
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.enum_order_type WHERE code = '98'"));
        assertNoJsErrors();
    }

    /** Системное значение: код, «действует» и удаление — недоступны. */
    @Test
    void systemValueOffersOnlyWhatIsAllowed() {
        openOrderTypes();
        page.click("#enumValuesList [data-enum-key='enum_status']");
        String draft = singleValue("SELECT id::text FROM dbo.enum_status WHERE code = '01'");
        page.click("#enumValuesBody tr[data-value-id='" + draft + "']");
        page.waitForSelector("#enumValueOverlay.active");

        assertTrue((Boolean) page.evaluate("() => document.getElementById('enumValueCode').readOnly"),
                "код системного значения не меняется");
        assertTrue(page.isDisabled("#enumValueActive"), "системное значение не отключается");
        assertTrue(page.isDisabled("#enumValueDelete"), "системное значение не удаляется");
        // а признак статуса «можно править транспорт» (V150) у системного значения правится
        assertFalse(page.isDisabled("#enumValueFlag"), "у системного статуса нельзя поставить галочку транспорта");
        assertEquals(singleValue("SELECT allows_transport_edit::text FROM dbo.enum_status WHERE code = '01'"),
                String.valueOf(page.isChecked("#enumValueFlag")), "галочка не та, что в базе");
        page.click("#enumValueCancel");
        page.waitForFunction("() => !document.getElementById('enumValueOverlay').classList.contains('active')");
        assertNoJsErrors();
    }

    /** Прежних вкладок в «Общих справочниках» нет — их перечисления здесь. */
    @Test
    void oldTabsAreGone() {
        openOrderTypes();
        for (String old : new String[] {"units", "orderTypes", "docTypes"}) {
            assertFalse((Boolean) page.evaluate("id => !!document.getElementById('admin-sub-' + id)", old),
                    "вкладка " + old + " должна была уйти в «Перечисления»");
        }
        assertNoJsErrors();
    }
}
