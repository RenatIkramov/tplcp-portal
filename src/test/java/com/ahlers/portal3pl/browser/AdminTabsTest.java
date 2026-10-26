package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Администрирование разложено по группам, справочники портала — в нём.
 *
 * <p>ЗАЧЕМ. Раздел рос вкладками по одной, а рядом, в «Справочниках»,
 * в одном ряду с товарами и контрагентами клиента, стояли справочники
 * ВСЕГО портала: склады, пользователи, поклажедатели, типы, единицы,
 * языки. Тринадцать подписей в строку порядка не держали.
 *
 * <p>ЧТО ПРОВЕРЯЕТСЯ. Что на экране два ряда и что в них попало ровно
 * то, что задумано: группы — в верхнем, вкладки выбранной группы —
 * в нижнем, а группа из одной вкладки второго ряда не рисует. Состав
 * собирается кодом из списка, и список легко разъедется с тем, что
 * существует: кнопка показывается только тогда, когда её панель есть,
 * иначе человек щёлкает в пустоту.
 *
 * <p>Подписи сверяются с каталогом текстов, а не вписаны сюда: вкладка
 * переименуется — проверка узнает об этом из базы, как и экран.
 */
class AdminTabsTest extends BrowserTestBase {

    @Test
    void administrationIsSplitIntoGroups() {
        // Язык учётной записи задаётся явно: подписи сверяются
        // с русскими текстами каталога.
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => showView('admin')");
        page.waitForFunction(
                "() => document.querySelectorAll('#adminGroupTabs .admin-subtab-btn').length > 0");

        assertEquals(List.of(text("admin.button.shared_directories"),
                        text("admin.button.accesses"),
                        text("admin.button.errors"),
                        text("admin.button.report_settings"),
                        text("admin.button.interface_texts")),
                labels("#adminGroupTabs"),
                "верхний ряд администрирования собран не из тех групп");

        // Справочники портала — все восемь, в заданном порядке.
        assertEquals(List.of(text("admin.title.operator_organization"),
                        text("references.button.warehouses"),
                        text("references.button.users"),
                        text("references.button.depositors"),
                        text("references.button.units"),
                        text("references.button.order_types"),
                        text("references.button.expected_document_types"),
                        text("references.button.languages")),
                labels("#adminSubTabs"),
                "в «Общих справочниках» не те вкладки или не в том порядке");

        // Открыта первая вкладка группы, и открыта именно ПАНЕЛЬ:
        // подсвеченная кнопка при пустом экране — худший из исходов.
        assertEquals(List.of("admin-sub-operator"), activePanes(),
                "вместе с группой не открылась её первая вкладка");
        assertNoJsErrors();
    }

    @Test
    void everyGroupOpensItsOwnTabs() {
        // Язык учётной записи задаётся явно: подписи сверяются
        // с русскими текстами каталога.
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => showView('admin')");
        page.waitForFunction(
                "() => document.querySelectorAll('#adminGroupTabs .admin-subtab-btn').length > 0");

        openGroup("accesses");
        assertEquals(3, labels("#adminSubTabs").size(), "в группе доступов не три вкладки");
        assertTrue(activePanes().contains("admin-sub-access"),
                "группа доступов открыла чужую панель: " + activePanes());

        openGroup("errors");
        assertEquals(2, labels("#adminSubTabs").size(), "в группе ошибок не две вкладки");

        // Группа из одной вкладки второго ряда не рисует: ряд из одной
        // кнопки, повторяющей название группы, ничего не сообщает.
        for (String single : List.of("reports", "uitexts")) {
            openGroup(single);
            assertEquals("none", page.evaluate(
                            "() => getComputedStyle(document.getElementById('adminSubTabs')).display"),
                    "группа " + single + " из одной вкладки рисует второй ряд");
            assertEquals(1, activePanes().size(),
                    "группа " + single + " открыла не одну панель: " + activePanes());
        }

        // Вкладку, на которой человек остановился, группа помнит:
        // вернуться к своему экрану нужно чаще, чем к первому в ряду.
        openGroup("accesses");
        page.evaluate("() => showAdminSub('staff-access')");
        openGroup("errors");
        openGroup("accesses");
        assertEquals(List.of("admin-sub-staff-access"), activePanes(),
                "группа забыла, на какой вкладке её оставили");
        assertNoJsErrors();
    }

    /**
     * В «Справочниках» остались справочники поклажедателя — и только.
     *
     * <p>Переезд незаметно съедает экраны: панель осталась в разметке,
     * а кнопки к ней больше нет — и справочник пропал, хотя всё
     * «на месте». Поэтому проверяется и то, чего там быть не должно,
     * и то, что ушедшие вкладки открываются на новом месте.
     */
    @Test
    void referencesKeepOnlyTheDepositorsOwnDirectories() {
        // Язык учётной записи задаётся явно: подписи сверяются
        // с русскими текстами каталога.
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => showTab('references')");
        page.waitForFunction("() => document.querySelectorAll("
                + "'#view-references [data-refsub]').length > 0");

        @SuppressWarnings("unchecked")
        List<String> refs = (List<String>) page.evaluate(
                "() => Array.from(document.querySelectorAll('#view-references [data-refsub]'))"
                        + ".map(b => b.getAttribute('data-refsub'))");
        assertEquals(List.of("sku", "packaging", "suppliers", "consignees"), refs,
                "в «Справочниках» остались не только справочники клиента: " + refs);

        for (String moved : List.of("warehousesDir", "users", "depositorsDir",
                "orderTypes", "docTypes", "units", "languages", "operator")) {
            assertTrue((Boolean) page.evaluate("id => !!document.getElementById('admin-sub-' + id)",
                            moved),
                    "справочник " + moved + " не доехал до администрирования");
        }
        assertNoJsErrors();
    }

    /**
     * «Типы ожидаемых документов» стали «Типами поступлений».
     *
     * <p>Подпись вкладки и заголовок экрана заведены разными ключами,
     * и разъехаться им нельзя: это одна и та же вкладка. Старого
     * названия не должно остаться и в разметке — текст из каталога
     * приходит только на узлы с ключом, а подпись, вписанную в вёрстку
     * один раз, переводить потом некому.
     */
    @Test
    void receiptTypesAreRenamed() {
        // Язык учётной записи задаётся явно: подписи сверяются
        // с русскими текстами каталога.
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);
        page.evaluate("() => { showView('admin'); showAdminSub('docTypes'); }");
        page.waitForSelector("#admin-sub-docTypes.active");

        String name = text("references.button.expected_document_types");
        assertTrue(labels("#adminSubTabs").contains(name),
                "вкладки «" + name + "» нет во втором ряду: " + labels("#adminSubTabs"));

        String head = ((String) page.evaluate(
                "() => document.querySelector('#admin-sub-docTypes h1').textContent")).trim();
        assertEquals(text("references.title.expected_document_types"), head,
                "заголовок экрана отстал от подписи вкладки");
        assertFalse(visibleText().contains("ожидаемых документов"),
                "на экране осталось старое название");
        assertNoJsErrors();
    }

    /* ---------------- помощники ---------------- */

    private void openGroup(String id) {
        page.evaluate("id => showAdminGroup(id)", id);
        page.waitForFunction("id => PORTAL.adminTabs.state.group === id", id);
    }

    private List<String> labels(String selector) {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) page.evaluate(
                "sel => Array.from(document.querySelectorAll(sel + ' .admin-subtab-btn'))"
                        + ".map(b => b.textContent.trim())", selector);
        return out;
    }

    private List<String> activePanes() {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) page.evaluate(
                "() => Array.from(document.querySelectorAll('#view-admin .admin-sub.active'))"
                        + ".map(d => d.id)");
        return out;
    }

    /** Подпись из каталога текстов — на языке учётной записи проверки. */
    private String text(String key) {
        return jdbc.sql("SELECT value FROM dbo.ui_text t"
                        + " JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id"
                        + " WHERE t.text_key = :key::text AND l.code = 'ru'")
                .param("key", key)
                .query(String.class)
                .single();
    }
}
