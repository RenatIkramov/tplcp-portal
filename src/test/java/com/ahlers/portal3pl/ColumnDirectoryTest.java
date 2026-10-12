package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.DepositorSettingsService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.config.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Справочник колонок (V29).
 * <p>
 * Проверяется не «сохранилось ли», а порядок, в котором спорят три
 * уровня настройки: портал, поклажедатель и сам пользователь. Именно
 * здесь легко получить портал, где клиент видит чужую себестоимость,
 * или таблицу, из которой пользователь убрал всё и не может вернуть.
 */
class ColumnDirectoryTest extends PortalTestBase {

    private static final String TABLE = "receipts";

    @Autowired
    ColumnService columns;

    @Autowired
    DepositorSettingsService depositorSettings;

    @Autowired
    CurrentUser current;

    /**
     * Уборка идёт теми же путями, какими работает приложение, а не прямыми
     * DELETE. Причина не в красоте: depositor_table_column и
     * depositor_field_label под FORCE ROW LEVEL SECURITY, и запрос без
     * контекста поклажедателя удаляет ноль строк — молча, без ошибки.
     * Настройка от одного теста доживала до следующего и роняла его.
     */
    @AfterEach
    void cleanUp() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        for (String code : new String[]{DEPOSITOR, OTHER_DEPOSITOR}) {
            String id = depositorId(ADMIN, code);
            columns.resetDepositorColumns(admin, id, null);
            columns.saveFieldLabel(admin, id, "sku", "code", null);
            // настройки разделов тоже возвращаем: скрытый раздел дожил бы
            // до следующего теста и уронил бы его
            depositorSettings.save(admin, id, new DepositorSettingsService.Settings(false));
        }
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("DELETE FROM dbo.user_table_column WHERE user_id IN"
                                + " (SELECT user_id FROM dbo.app_user WHERE login IN (:a, :b))")
                        .param("a", USER)
                        .param("b", OTHER_USER)
                        .update());
        // язык пользователя возвращаем к портальному
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("UPDATE dbo.app_user SET locale_id = NULL WHERE login = :login")
                        .param("login", USER)
                        .update());
    }

    private static boolean visible(List<Map<String, Object>> layout, String columnId) {
        return layout.stream()
                .filter(r -> columnId.equals(r.get("columnId")))
                .findFirst()
                .map(r -> Boolean.TRUE.equals(r.get("isVisible")))
                .orElse(false);
    }

    private static List<String> shown(List<Map<String, Object>> layout) {
        return layout.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("isVisible")))
                .map(r -> String.valueOf(r.get("columnId")))
                .toList();
    }

    @Test
    void catalogCoversEveryTable() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        List<Map<String, Object>> tables = columns.tables(admin);
        assertEquals(12, tables.size(), "таблиц в справочнике двенадцать");
        int total = tables.stream()
                .mapToInt(t -> ((Number) t.get("columnCount")).intValue())
                .sum();
        assertEquals(257, total,
                "колонок столько же, сколько в прототипе: 204 из V29 плюс 53 из V34");
    }

    @Test
    void untouchedTableUsesPortalDefaults() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> layout = columns.layout(user, TABLE);
        assertEquals(33, layout.size(), "в выдаче все колонки — и скрытые тоже, для окна выбора");
        assertEquals(14, shown(layout).size(), "по умолчанию показываются четырнадцать");
    }

    @Test
    void customFieldsAreOfferedButHidden() {
        // §8.1.2: доп. поля открываются именно в окне «⚙ Колонки».
        // При засеве каталога (V29) они туда не попали, потому что
        // прототип создаёт их выражением, а не литералом, — и исчезли
        // с экрана совсем. Закрыто в V34.
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        // подпись проверяется ниже, поэтому язык задаём явно: без этого
        // действует портальный, а он английский (см. labelOf)
        localeOf(user, "ru");
        List<Map<String, Object>> sku = columns.layout(user, "sku");
        assertEquals(62, sku.size(), "у товара 62 колонки, включая десять доп. параметров");
        assertEquals(10, sku.stream()
                .filter(r -> String.valueOf(r.get("columnId")).startsWith("customAttr"))
                .count());
        assertTrue(sku.stream()
                        .filter(r -> String.valueOf(r.get("columnId")).startsWith("customAttr"))
                        .noneMatch(r -> Boolean.TRUE.equals(r.get("isVisible"))),
                "по умолчанию они скрыты: десять лишних колонок испортили бы таблицу всем");
        assertEquals("Доп. параметр 1",
                labelOf(sku, "customAttr1"));

        assertEquals(15, columns.layout(user, "receiptLineDetail").size(),
                "в деталях строки поступления 5 колонок плюс десять доп. полей");
    }

    @Test
    void stockScreenIsNotEmptyByDefault() {
        // у колонок «Запасов» в прототипе нет признака «по умолчанию», но
        // скрытых там нет вовсе: перенеси это буквально — и экран пустой
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> layout = columns.layout(user, "stock");
        assertEquals(layout.size(), shown(layout).size(), "в запасах показываются все колонки");
        assertFalse(layout.isEmpty());
    }

    @Test
    void userChoiceWinsAndKeepsOrder() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> layout =
                columns.saveMine(user, TABLE, List.of("status", "no", "warehouse"));
        assertEquals(List.of("status", "no", "warehouse"), shown(layout),
                "порядок колонок — тот, в котором их выбрали");

        // колонка, видимая по умолчанию, но не выбранная, становится скрытой:
        // иначе спрятать её было бы невозможно
        assertFalse(visible(layout, "supplier"));
    }

    @Test
    void choiceInOneTableDoesNotAffectOthers() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        columns.saveMine(user, TABLE, List.of("no"));
        assertEquals(20, shown(columns.layout(user, "sku")).size(),
                "товары остались на умолчаниях");
    }

    @Test
    void resetBringsDefaultsBack() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        columns.saveMine(user, TABLE, List.of("no"));
        assertEquals(1, shown(columns.layout(user, TABLE)).size());
        assertEquals(14, shown(columns.resetMine(user, TABLE)).size(),
                "сброс возвращает портальные умолчания");
    }

    @Test
    void emptyChoiceRejected() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        // таблица без колонок — это не настройка, а экран, с которого
        // уже не выбраться: для этого есть сброс
        expectRejection(400, () -> columns.saveMine(user, TABLE, List.of()));
        expectRejection(404, () -> columns.saveMine(user, TABLE, List.of("нет-такой")));
        expectRejection(404, () -> columns.layout(user, "нет-такой-таблицы"));
    }

    @Test
    void repeatedColumnSavedOnce() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> layout =
                columns.saveMine(user, TABLE, List.of("no", "status", "no"));
        assertEquals(List.of("no", "status"), shown(layout),
                "повтор не ломает порядок и не задваивает колонку");
    }

    @Test
    void columnForbiddenToDepositorIsNotEvenOffered() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        columns.saveDepositorColumn(admin, depositorId, TABLE, "supplier", false, true);

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> layout = columns.layout(user, TABLE);
        assertEquals(32, layout.size(), "запрещённая колонка не попадает даже в окно выбора");
        assertTrue(layout.stream().noneMatch(r -> "supplier".equals(r.get("columnId"))));

        // и включить её себе пользователь не может — на этом держится
        // сокрытие себестоимости и цен
        expectRejection(404, () -> columns.saveMine(user, TABLE, List.of("no", "supplier")));
    }

    @Test
    void depositorDefaultAppliesUntilUserChooses() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        // колонка скрыта у портала — включаем её этому клиенту
        columns.saveDepositorColumn(admin, depositorId, TABLE, "weight", true, true);

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertTrue(visible(columns.layout(user, TABLE), "weight"),
                "настройка клиента сильнее портальной");

        // но свой выбор сильнее настройки клиента
        assertFalse(visible(columns.saveMine(user, TABLE, List.of("no")), "weight"));
    }

    @Test
    void depositorRowDisappearsWhenItMatchesPortalAgain() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);

        columns.saveDepositorColumn(admin, depositorId, TABLE, "supplier", false, true);
        assertEquals(1, countDepositorRows(admin, depositorId), "отличие от портала записано");

        columns.saveDepositorColumn(admin, depositorId, TABLE, "supplier", true, true);
        assertEquals(0, countDepositorRows(admin, depositorId),
                "отличие исчезло — строка убрана, в таблице только правки руками");
    }

    // Прямое чтение depositor_table_column вернуло бы ноль: таблица под RLS,
    // а у администратора нет контекста поклажедателя. Спрашиваем так же,
    // как это делает экран настройки.
    private int countDepositorRows(PortalUser admin, String depositorId) {
        return columns.depositorColumnCount(admin, depositorId, null);
    }

    @Test
    void renamingAFieldAppliesEverywhereItIsShown() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        // через API, а не прямой вставкой: таблица под RLS, и вставка без
        // контекста поклажедателя отклоняется политикой
        columns.saveFieldLabel(admin, depositorId, "sku", "code", "Артикул клиента");

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> all = columns.layout(user, null);
        List<String> labels = all.stream()
                .filter(r -> "code".equals(r.get("columnId")))
                .map(r -> String.valueOf(r.get("label")))
                .toList();
        assertFalse(labels.isEmpty());
        assertTrue(labels.stream().allMatch("Артикул клиента"::equals),
                "коммерческий код — поле товара: переименование видно и в справочнике,"
                        + " и в запасах, и в составе документа, а не на одном экране");
    }

    @Test
    void labelFollowsUserLanguage() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        // язык пользователя надо выставить явно: пока он не выбран, приходит
        // язык портала, а он английский — и «до» совпало бы с «после»
        localeOf(user, "ru");
        String ru = labelOf(columns.layout(user, TABLE), "status");

        localeOf(user, "en");
        String en = labelOf(columns.layout(user, TABLE), "status");

        assertNotEquals(ru, en, "подпись колонки приходит на языке пользователя");
        assertEquals("Статус", ru);
        assertEquals("Status", en);
    }

    /**
     * Подпись колонки из раскладки.
     * <p>
     * Прежде чем сверять её с русским текстом, задайте язык через
     * {@link #localeOf}: уборка после теста сбрасывает язык пользователя,
     * и по умолчанию действует язык портала — английский. На этом уже
     * дважды спотыкались.
     */
    private static String labelOf(List<Map<String, Object>> layout, String columnId) {
        return layout.stream()
                .filter(r -> columnId.equals(r.get("columnId")))
                .findFirst()
                .map(r -> String.valueOf(r.get("label")))
                .orElseThrow();
    }

    @Test
    void settingsCopiedWithoutAccess() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        columns.saveMine(user, TABLE, List.of("status", "no"));

        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        int accessBefore = depositorAccessCount(OTHER_USER);
        columns.copySettings(admin, userId(USER), userId(OTHER_USER));

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        PortalUser other = authService.authenticate(OTHER_USER, USER_PASSWORD);
        assertEquals(List.of("status", "no"), shown(columns.layout(other, TABLE)),
                "раскладка перенеслась вместе с порядком");

        signInAs(ADMIN, DEPOSITOR);
        assertEquals(accessBefore, depositorAccessCount(OTHER_USER),
                "доступы к поклажедателям не копируются: это «кто видит чьи данные»,"
                        + " и выдавать их одной кнопкой нельзя");
    }

    private int depositorAccessCount(String login) {
        return new TransactionTemplate(txManager).execute(tx ->
                jdbc.sql("""
                                SELECT count(*) FROM dbo.user_depositor_access a
                                JOIN dbo.app_user u ON u.user_id = a.user_id
                                WHERE u.login = :login
                                """)
                        .param("login", login)
                        .query(Integer.class)
                        .single());
    }

    @Test
    void copyingOntoItselfRejected() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String id = userId(USER);
        expectRejection(400, () -> columns.copySettings(admin, id, id));
        expectRejection(404, () ->
                columns.copySettings(admin, id, "00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void depositorSettingsCanBeReturnedToPortalDefaults() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        columns.saveDepositorColumn(admin, depositorId, TABLE, "supplier", false, true);
        columns.saveDepositorColumn(admin, depositorId, "sku", "hsCode", true, true);
        assertEquals(2, countDepositorRows(admin, depositorId));

        columns.resetDepositorColumns(admin, depositorId, TABLE);
        assertEquals(1, countDepositorRows(admin, depositorId), "сброшен только один раздел");

        columns.resetDepositorColumns(admin, depositorId, null);
        assertEquals(0, countDepositorRows(admin, depositorId), "без таблицы — все разделы");

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertEquals(33, columns.layout(user, TABLE).size(),
                "запрещённая колонка вернулась в выдачу");
    }

    @Test
    void emptyFieldLabelRestoresThePortalName() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);

        List<Map<String, Object>> labels =
                columns.saveFieldLabel(admin, depositorId, "sku", "code", "Артикул клиента");
        assertEquals(1, labels.size());
        // рядом видно, где подпись поменяется, — иначе это приходится угадывать
        assertTrue(String.valueOf(labels.getFirst().get("shownIn")).contains("receiptComposition"));

        // Раскладка отдаёт обе подписи (V50): по разнице между ними экран
        // понимает, что поле переименовано. Без этого признака он либо
        // не применял бы переименование вовсе — так и было, —
        // либо подставлял бы серверную подпись всегда, а она при пустом
        // каталоге текстов доходит до ключа.
        signIn();
        PortalUser renamed = authService.authenticate(USER, USER_PASSWORD);
        localeOf(renamed, "ru");
        Map<String, Object> code = columns.layout(renamed, "sku").stream()
                .filter(r -> "code".equals(r.get("columnId")))
                .findFirst()
                .orElseThrow();
        assertEquals("Артикул клиента", code.get("label"));
        assertEquals("Коммерческий код", code.get("portalLabel"),
                "портальное название приходит рядом с переименованным");
        Map<String, Object> plain = columns.layout(renamed, "sku").stream()
                .filter(r -> "barcode".equals(r.get("columnId")))
                .findFirst()
                .orElseThrow();
        assertEquals(plain.get("portalLabel"), plain.get("label"),
                "не переименованное поле: обе подписи совпадают — экран переводит сам");

        signInAs(ADMIN, DEPOSITOR);
        assertTrue(columns.saveFieldLabel(admin, depositorId, "sku", "code", "  ").isEmpty(),
                "пустая подпись возвращает портальное название, а не хранится пустой");

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        // Язык задаём явно. Служба берёт его из учётной записи, а не из
        // аргумента: пока пользователь язык не выбрал, приходит портальный,
        // то есть английский, — и проверка на русскую подпись врала бы
        // не про переименование, а про язык.
        localeOf(user, "ru");
        assertEquals("Коммерческий код", labelOf(columns.layout(user, "sku"), "code"),
                "после очистки подписи возвращается портальное название поля");
    }

    @Test
    void fieldThatIsShownNowhereCannotBeRenamed() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        // иначе в справочнике появятся подписи, которых никто не увидит
        expectRejection(404, () ->
                columns.saveFieldLabel(admin, depositorId, "sku", "нет-такого-поля", "Имя"));
        expectRejection(404, () ->
                columns.saveFieldLabel(admin, depositorId, "нет-объекта", "code", "Имя"));
        expectRejection(400, () ->
                columns.saveFieldLabel(admin, depositorId, "sku", "  ", "Имя"));
    }

    /**
     * Каталог переименуемых полей (V48) — список для окна правки.
     *
     * <p>Прежний экран брал его из массива в прототипе, поэтому в окне
     * были только пять «объектов» и колонки макета. Проверяем, что
     * список приходит из каталога колонок: строка на поле, рядом
     * таблицы, где подпись поменяется.
     */
    @Test
    void fieldCatalogListsRenameableFields() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        localeOf(admin, "ru");
        List<Map<String, Object>> catalog = columns.fieldCatalog(admin);

        assertFalse(catalog.isEmpty(), "каталог полей не может быть пустым");
        Map<String, Object> code = catalog.stream()
                .filter(r -> "sku".equals(r.get("objectCode"))
                        && "code".equals(r.get("fieldId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("в каталоге нет поля sku.code"));
        // Обязательное поле (is_locked) переименовать можно: «обязательное»
        // значит «нельзя скрыть от поклажедателя», а не «нельзя назвать
        // по-своему».
        assertEquals("Коммерческий код", code.get("portalLabel"),
                "поле зовётся так, как в своём разделе: в товарах — «Коммерческий"
                        + " код», а не «Код» из запасов");
        columns.saveFieldLabel(admin, depositorId(ADMIN, DEPOSITOR),
                "sku", "code", "Артикул клиента");
        assertEquals("Артикул клиента",
                labelOf(columns.layout(admin, "sku"), "code"),
                "обязательное поле переименовывается наравне с остальными");
        assertTrue(String.valueOf(code.get("shownIn")).contains("sku"),
                "рядом с полем перечислены таблицы, где подпись поменяется: "
                        + code.get("shownIn"));

        // Строка на ПОЛЕ, а не на колонку: одно поле показывается
        // в нескольких таблицах, а название у него одно.
        long duplicates = catalog.size() - catalog.stream()
                .map(r -> r.get("objectCode") + "|" + r.get("fieldId"))
                .distinct()
                .count();
        assertEquals(0, duplicates, "поле в каталоге повторяться не должно");

        // Переименовать можно только то, что где-то показывается, —
        // ровно это и проверяет saveFieldLabel.
        String depositorId = depositorId(ADMIN, DEPOSITOR);
        for (Map<String, Object> field : catalog) {
            columns.saveFieldLabel(admin, depositorId,
                    String.valueOf(field.get("objectCode")),
                    String.valueOf(field.get("fieldId")), "");
        }
    }

    @Test
    void onlyAdminChangesDepositorSettings() {
        signIn();
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        String depositorId = depositorId(USER, DEPOSITOR);
        expectRejection(403, () -> columns.tables(client));
        expectRejection(403, () -> columns.depositorColumns(client, depositorId, TABLE));
        expectRejection(403, () ->
                columns.saveDepositorColumn(client, depositorId, TABLE, "supplier", false, true));
        expectRejection(403, () ->
                columns.copySettings(client, userId(USER), userId(OTHER_USER)));
        expectRejection(403, () -> columns.resetDepositorColumns(client, depositorId, null));
        expectRejection(403, () -> columns.fieldLabels(client, depositorId));
        expectRejection(403, () -> columns.fieldCatalog(client));
        expectRejection(403, () ->
                columns.saveFieldLabel(client, depositorId, "sku", "code", "Своё"));
    }

    /* ============== Видимость полей для поклажедателя (V35) ============== */

    @Test
    void everyProductColumnBelongsToATabOfTheCard() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        localeOf(admin, "ru");
        List<Map<String, Object>> sku =
                columns.depositorColumns(admin, depositorId(ADMIN, DEPOSITOR), "sku");

        // Экран настройки разложен по вкладкам карточки товара. Колонка
        // без вкладки нигде не показалась бы — и администратор не смог бы
        // её настроить, не понимая, почему её нет.
        assertTrue(sku.stream().noneMatch(r -> r.get("groupKey") == null),
                "у каждой колонки товара есть вкладка");
        assertEquals(8, sku.stream().map(r -> r.get("groupKey")).distinct().count(),
                "вкладок восемь — как в карточке, без упаковок");
        assertTrue(sku.stream().anyMatch(r ->
                        "Основная информация".equals(r.get("groupLabel"))),
                "подпись вкладки приходит переведённой, а не ключом");
    }

    @Test
    void requiredProductFieldsCannotBeHidden() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);

        // Ровно те три, что проверяет сервер при записи товара. Спрятать
        // их значило бы оставить клиента с карточкой, в которой товар
        // нельзя сохранить.
        for (String column : new String[]{"code", "name", "unit"}) {
            expectRejection(409, () -> columns.saveDepositorColumn(
                    admin, depositorId, "sku", column, false, true));
        }
    }

    @Test
    void requiredFieldsStayVisibleEvenInThePersonalPicker() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        // Личный выбор колонок обязательные не отключает: список товаров
        // без кода и наименования бесполезен. Убрать их из набора нельзя —
        // поэтому в выборе они есть, даже когда человек оставил одну колонку.
        columns.saveMine(user, "sku", List.of("code", "name", "unit", "hsCode"));
        List<Map<String, Object>> sku = columns.layout(user, "sku");
        for (String column : new String[]{"code", "name", "unit"}) {
            assertTrue(sku.stream().anyMatch(r -> column.equals(r.get("columnId"))
                            && Boolean.TRUE.equals(r.get("isVisible"))),
                    "обязательная колонка осталась видимой: " + column);
            assertTrue(sku.stream().anyMatch(r -> column.equals(r.get("columnId"))
                            && Boolean.TRUE.equals(r.get("isPinned"))),
                    "и помечена как прикреплённая: " + column);
        }
    }

    @Test
    void systemFieldsAreNotHiddenFromTheClientButAreNotPinnedEither() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);

        // Два разных «нельзя скрыть» (V36): клиенту — нельзя,
        // себе в таблицу — можно. Иначе «кто создал» и «когда изменено»
        // навсегда повисли бы в списке товаров у каждого человека.
        for (String column : new String[]{"createdBy", "updatedAt", "group"}) {
            expectRejection(409, () -> columns.saveDepositorColumn(
                    admin, depositorId, "sku", column, false, true));
        }

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> sku = columns.layout(user, "sku");
        assertTrue(sku.stream().anyMatch(r -> "createdBy".equals(r.get("columnId"))
                        && !Boolean.TRUE.equals(r.get("isPinned"))),
                "служебное поле не прикреплено к таблице");
    }

    @Test
    void pinnedColumnCannotBeRemovedFromYourOwnTable() {
        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        // Серая галочка в окне ⚙ Колонки — удобство; запрет стоит в базе.
        expectRejection(409, () -> columns.saveMine(user, "sku", List.of("hsCode")));
    }

    @Test
    void statusAndWmsFlagLiveWithTheSystemFields() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        localeOf(admin, "ru");
        List<Map<String, Object>> sku =
                columns.depositorColumns(admin, depositorId(ADMIN, DEPOSITOR), "sku");
        // Оба ставит обмен с WMS, а не человек: в основной информации
        // и в признаках они выглядели как заполняемые вручную.
        for (String column : new String[]{"statusProduct", "isWmsActive"}) {
            assertTrue(sku.stream().anyMatch(r -> column.equals(r.get("columnId"))
                            && "sku.text.system_information".equals(r.get("groupKey"))),
                    "поле на вкладке служебной информации: " + column);
        }
    }

    @Test
    void fieldHiddenFromDepositorDisappearsFromTheLayout() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        columns.saveDepositorColumn(admin, depositorId(ADMIN, DEPOSITOR),
                "sku", "brand", false, true);

        signIn();
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        List<Map<String, Object>> sku = columns.layout(user, "sku");
        // Запрещённой колонки нет вовсе, а не «есть, но скрыта»: иначе
        // пользователь включил бы её себе в окне выбора колонок.
        assertTrue(sku.stream().noneMatch(r -> "brand".equals(r.get("columnId"))),
                "запрещённое поле не приходит пользователю даже скрытым");
    }

    @Test
    void transfersSectionIsSettledPerDepositorNotPerPerson() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        String depositorId = depositorId(ADMIN, DEPOSITOR);

        assertEquals(false, depositorSettings.settings(admin, depositorId)
                        .get("hideTransfersTab"),
                "по умолчанию раздел виден");

        depositorSettings.save(admin, depositorId,
                new DepositorSettingsService.Settings(true));
        assertEquals(true, depositorSettings.settings(admin, depositorId)
                        .get("hideTransfersTab"),
                "настройка сохранилась");

        // И действует на всех пользователей клиента, а не на одного:
        // настройка переехала с человека на организацию (V35).
        signIn();
        assertEquals(true, current.settings().get("hideTransfersTab"),
                "пользователь этого клиента раздела не видит");
    }

    @Test
    void onlyAdminChangesDepositorSections() {
        signIn();
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        String depositorId = depositorId(USER, DEPOSITOR);
        expectRejection(403, () -> depositorSettings.settings(client, depositorId));
        expectRejection(403, () -> depositorSettings.save(client, depositorId,
                new DepositorSettingsService.Settings(true)));
    }

    @Test
    void unknownDepositorIsRejectedRatherThanSilentlyIgnored() {
        PortalUser admin = portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        // Опечатка в идентификаторе давала бы пустые настройки вместо
        // отказа, и администратор решил бы, что сохранил.
        expectRejection(404, () -> depositorSettings.settings(
                admin, "00000000-0000-0000-0000-000000000000"));
        expectRejection(400, () -> depositorSettings.settings(admin, "  "));
    }
}
