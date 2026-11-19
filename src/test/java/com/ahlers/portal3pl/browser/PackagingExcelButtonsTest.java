package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Кнопки Excel на экране упаковок.
 *
 * <p>ЧТО БЫЛО. «Загрузить из Excel» пряталась как заглушка, а «Скачать
 * шаблон Excel» оставалась видимой и по нажатию показывала «Скачивается:
 * Шаблон_Упаковка.xlsx» — и не скачивала ничего. Кнопка, которая
 * выглядит рабочей и не работает, хуже отсутствующей. С V81 обе
 * работают, и проверяется именно это: кнопки на месте и подключены
 * к настоящим обработчикам, а не к заглушке прототипа.
 *
 * <p>Право «загрузка упаковок» своё. Прежде экран брал его из права на
 * товары, и снятая галочка не прятала кнопку упаковок.
 */
class PackagingExcelButtonsTest extends BrowserTestBase {

    private void openPackaging() {
        signInUiAsClient();
        openReferences("packaging");
        page.waitForSelector("#packagingBody tr");
        waitForQuiet();
    }

    private boolean visible(String selector) {
        return (Boolean) page.evaluate(
                "s => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }",
                selector);
    }

    @Test
    void bothButtonsAreWiredToRealHandlers() {
        openPackaging();

        assertTrue(visible("#uploadPackagingExcelBtn"), "кнопки загрузки упаковок не видно");
        assertEquals(null, page.getAttribute("#uploadPackagingExcelBtn", "data-call"),
                "у кнопки загрузки остался обработчик-заглушка прототипа");

        assertFalse(page.querySelectorAll("#packagingTemplateBtn").isEmpty(),
                "кнопка шаблона упаковок не подключена");
        assertEquals(null, page.getAttribute("#packagingTemplateBtn", "data-call"),
                "у кнопки шаблона остался обработчик-заглушка прототипа");
        assertNoJsErrors();
    }

    /**
     * Администратор с несколькими клиентами, выбор ещё не сделан.
     *
     * <p>Так нашли ошибку на живом портале: кнопки подключались по входу,
     * а «вход» для подписчиков наступает только после выбора клиента.
     * До выбора работали заглушки прототипа: шаблон «скачивался» одной
     * плашкой, загрузка не открывала выбор файла.
     */
    @Test
    void adminSeesWorkingButtonsBeforeChoosingDepositor() {
        portalAdmin();
        signInUi(ADMIN, ADMIN_PASSWORD);
        assertTrue((Boolean) page.evaluate("() => !!(PORTAL.session && PORTAL.session.needsDepositorChoice)"),
                "у тестового администратора должен быть выбор клиента");

        page.evaluate("() => { showTab('references'); showReferencesSub('packaging'); }");

        assertEquals(null, page.getAttribute("#uploadPackagingExcelBtn", "data-call"),
                "до выбора клиента у кнопки загрузки осталась заглушка");
        assertFalse(page.querySelectorAll("#packagingTemplateBtn").isEmpty(),
                "до выбора клиента кнопка шаблона не подключена");
        assertEquals(null, page.getAttribute("#packagingTemplateBtn", "data-call"));
        assertEquals(null, page.getAttribute("#skuTemplateBtn", "data-call"));
        assertEquals(null, page.getAttribute("#uploadSkuExcelBtn", "data-call"));
    }

    @Test
    void uploadButtonFollowsItsOwnRight() {
        jdbc.sql("""
                        INSERT INTO dbo.user_settings (user_id, can_upload_packaging_excel)
                        SELECT user_id, false FROM dbo.app_user WHERE login = :login
                        ON CONFLICT (user_id) DO UPDATE SET can_upload_packaging_excel = false
                        """)
                .param("login", USER)
                .update();

        openPackaging();

        assertFalse(visible("#uploadPackagingExcelBtn"),
                "право на загрузку упаковок снято, а кнопка видна");
        assertNoJsErrors();
    }
}
