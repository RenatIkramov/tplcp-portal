package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.i18n.EnumValueService;
import com.ahlers.portal3pl.i18n.EnumValueService.EnumValue;
import com.ahlers.portal3pl.i18n.EnumValueService.Translation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Группа «Перечисления» (V95): значения всех перечислений и их переводы
 * правятся в одном окне.
 *
 * <p>Главное, что здесь проверяется:
 * <ul>
 *   <li>значение и переводы сохраняются вместе, и отказ по переводу
 *       не оставляет значения без него;</li>
 *   <li>удалить можно только то, на что ничего не ссылается;</li>
 *   <li>код используемого значения не меняется;</li>
 *   <li>системное значение (на его код опирается программа)
 *       не удаляется, не отключается и кода не меняет.</li>
 * </ul>
 *
 * <p>Пробные значения — из заведомо свободных кодов: тип заказа «98»,
 * единица МКЕИ «99x», валюта «999».
 */
class EnumValueTest extends PortalTestBase {

    private static final String ORDER_TYPE = "enum_order_type";
    private static final String UOM = "enum_uom";
    private static final String CURRENCY = "enum_currency";

    @Autowired
    EnumValueService values;

    @AfterEach
    void removeTestRows() {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("""
                    DELETE FROM dbo.enum_text t
                     WHERE (t.enum_key = 'enum_order_type' AND t.value_id IN
                              (SELECT id::text FROM dbo.enum_order_type WHERE code IN ('97', '98')))
                        OR (t.enum_key = 'enum_uom' AND t.value_id IN
                              (SELECT id::text FROM dbo.enum_uom WHERE mkei_code LIKE '99_'))
                        OR (t.enum_key = 'enum_currency' AND t.value_id IN
                              (SELECT id::text FROM dbo.enum_currency WHERE num_code = '999'))
                    """).update();
            jdbc.sql("DELETE FROM dbo.enum_order_type WHERE code IN ('97', '98')").update();
            jdbc.sql("DELETE FROM dbo.enum_uom WHERE mkei_code LIKE '99_'").update();
            jdbc.sql("DELETE FROM dbo.enum_currency WHERE num_code = '999'").update();
        });
    }

    /* ---------------------------- чтение ---------------------------- */

    @Test
    void listHasEveryEnumAndOnlyEnabledLanguages() {
        PortalUser admin = portalAdmin();
        Map<String, Object> answer = values.list(admin);

        assertEquals(Integer.parseInt(singleValue("SELECT count(*)::text FROM dbo.enum_registry")),
                list(answer, "enums").size(), "в группе — все перечисления реестра");

        // Переводы — только на включённые языки, кроме основного:
        // английское имя и есть основное, а выключенный язык человек
        // всё равно не увидит
        List<String> locales = list(answer, "locales").stream()
                .map(l -> String.valueOf(l.get("code"))).toList();
        List<String> expected = jdbc.sql("""
                        SELECT code FROM dbo.dictionary_locale
                        WHERE COALESCE(is_active, false) AND NOT COALESCE(is_default, false)
                        ORDER BY sort_order NULLS LAST, code
                        """).query(String.class).list();
        assertEquals(expected, locales);
        assertFalse(locales.contains("en"), "основной язык — не перевод");

        Map<String, Object> draft = row(answer, "enum_status", "01");
        assertEquals(Boolean.TRUE, draft.get("isSystem"), "на «черновик» опирается программа");

        Map<String, Object> piece = row(answer, UOM, "796");
        assertNotNull(piece.get("extra"), "у единицы есть обозначение");
        assertTrue(((Number) piece.get("usage")).longValue() > 0, "штукой пользуются товары");
    }

    /* ---------------------------- запись ---------------------------- */

    @Test
    void valueAndTranslationAreSavedTogether() {
        PortalUser admin = portalAdmin();

        Map<String, Object> answer = values.save(admin, ORDER_TYPE, null,
                new EnumValue("98", "QA order type", null, 900, true,
                        Map.of("ru", new Translation("Проверочный тип", null))));
        Map<String, Object> added = row(answer, ORDER_TYPE, "98");
        assertEquals("QA order type", added.get("name"));
        assertEquals("Проверочный тип", translation(added, "ru"));
        assertEquals(0L, ((Number) added.get("usage")).longValue());
        assertEquals(Boolean.FALSE, added.get("isSystem"));

        String id = String.valueOf(added.get("valueId"));
        // Пустой перевод удаляется: человек увидит основное имя, а не пустоту
        answer = values.save(admin, ORDER_TYPE, id,
                new EnumValue("97", "QA order type 2", null, 900, false,
                        Map.of("ru", new Translation("  ", null))));
        Map<String, Object> changed = row(answer, ORDER_TYPE, "97");
        assertEquals("QA order type 2", changed.get("name"));
        assertEquals(Boolean.FALSE, changed.get("isActive"), "значение можно отключить, не удаляя");
        assertNull(translation(changed, "ru"));

        answer = values.delete(admin, ORDER_TYPE, id);
        assertTrue(rows(answer, ORDER_TYPE).stream().noneMatch(r -> "97".equals(r.get("code"))));
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.enum_text"
                + " WHERE enum_key = 'enum_order_type' AND value_id = '" + id + "'"),
                "переводы удалённого значения не остаются сиротами");
    }

    /** Отказ по переводу — отказ целиком: значения без перевода не появляется. */
    @Test
    void refusedTranslationLeavesNoValue() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> values.save(admin, ORDER_TYPE, null,
                new EnumValue("98", "QA order type", null, 900, true,
                        Map.of("ru", new Translation("я".repeat(300), null)))));
        // Выключенный язык: переводить на него здесь нельзя
        expectRejection(404, () -> values.save(admin, ORDER_TYPE, null,
                new EnumValue("98", "QA order type", null, 900, true,
                        Map.of("kk", new Translation("Тексеру", null)))));
        assertTrue(rows(values.list(admin), ORDER_TYPE).stream()
                .noneMatch(r -> "98".equals(r.get("code"))));
    }

    @Test
    void codeIsWrittenLikeTheOthers() {
        PortalUser admin = portalAdmin();
        for (String code : List.of("1", "100", "qa", "0a")) {
            expectRejection(400, () -> values.save(admin, ORDER_TYPE, null,
                    new EnumValue(code, "QA", null, null, true, Map.of())));
        }
        expectRejection(400, () -> values.save(admin, ORDER_TYPE, null,
                new EnumValue(" ", "QA", null, null, true, Map.of())));
        expectRejection(400, () -> values.save(admin, ORDER_TYPE, null,
                new EnumValue("98", " ", null, null, true, Map.of())));
        // Код уже занят другим значением этого перечисления
        expectRejection(409, () -> values.save(admin, ORDER_TYPE, null,
                new EnumValue("01", "QA", null, null, true, Map.of())));
    }

    /** Единица: код МКЕИ — три цифры, обозначение обязательно и не повторяется. */
    @Test
    void unitHasSymbolThatIsNotRepeated() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> values.save(admin, UOM, null,
                new EnumValue("990", "QA box", null, 900, true, Map.of())));
        expectRejection(400, () -> values.save(admin, UOM, null,
                new EnumValue("99001", "QA box", "qa", 900, true, Map.of())));
        // Обозначение — десять знаков: портал говорит это словами,
        // а не отдаёт ошибку базы про varchar
        expectRejection(400, () -> values.save(admin, UOM, null,
                new EnumValue("990", "QA box", "qa-too-long-symbol", 900, true, Map.of())));

        Map<String, Object> answer = values.save(admin, UOM, null,
                new EnumValue("990", "QA box", "qa", 900, true,
                        Map.of("ru", new Translation("Пробный ящик", "пя"))));
        Map<String, Object> box = row(answer, UOM, "990");
        assertEquals("qa", box.get("extra"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ru = (Map<String, Object>) ((Map<String, Object>) box.get("values")).get("ru");
        assertEquals("пя", ru.get("symbol"), "обозначение единицы переводится вместе с именем");

        expectRejection(409, () -> values.save(admin, UOM, null,
                new EnumValue("991", "QA box 2", "QA", 901, true, Map.of())));
        expectRejection(409, () -> values.save(admin, UOM, null,
                new EnumValue("990", "QA box 2", "qb", 901, true, Map.of())));
    }

    @Test
    void currencyKeepsAlphabeticCode() {
        PortalUser admin = portalAdmin();
        expectRejection(400, () -> values.save(admin, CURRENCY, null,
                new EnumValue("999", "QA currency", "qax", 900, true, Map.of())));
        Map<String, Object> answer = values.save(admin, CURRENCY, null,
                new EnumValue("999", "QA currency", "QAX", 900, true, Map.of()));
        assertEquals("QAX", row(answer, CURRENCY, "999").get("extra"));
        expectRejection(409, () -> values.save(admin, CURRENCY, null,
                new EnumValue("998", "QA currency 2", "KZT", 901, true, Map.of())));
    }

    /* ---------------------------- запреты ---------------------------- */

    /** Используемое значение: не удаляется, код не меняется, имя — можно. */
    @Test
    void usedValueKeepsItsCode() {
        PortalUser admin = portalAdmin();
        String usedId = singleValue("SELECT order_type_id::text FROM dbo.orders"
                + " WHERE order_type_id IS NOT NULL LIMIT 1");
        Map<String, Object> used = rows(values.list(admin), ORDER_TYPE).stream()
                .filter(r -> usedId.equals(r.get("valueId"))).findFirst().orElseThrow();
        String code = String.valueOf(used.get("code"));
        String name = String.valueOf(used.get("name"));
        assertTrue(((Number) used.get("usage")).longValue() > 0);

        expectRejection(409, () -> values.delete(admin, ORDER_TYPE, usedId));
        expectRejection(409, () -> values.save(admin, ORDER_TYPE, usedId,
                new EnumValue("98", name, null, null, true, Map.of())));
        try {
            Map<String, Object> answer = values.save(admin, ORDER_TYPE, usedId,
                    new EnumValue(code, name + " (QA)", null, (Integer) number(used.get("sortOrder")),
                            true, Map.of()));
            assertEquals(name + " (QA)", row(answer, ORDER_TYPE, code).get("name"));
        } finally {
            values.save(admin, ORDER_TYPE, usedId,
                    new EnumValue(code, name, null, (Integer) number(used.get("sortOrder")), true, Map.of()));
        }
        // Единица, которой пользуются товары, — тоже
        String piece = String.valueOf(row(values.list(admin), UOM, "796").get("valueId"));
        expectRejection(409, () -> values.delete(admin, UOM, piece));
    }

    /** Системное значение: на его код опирается программа. */
    @Test
    void systemValueIsProtected() {
        PortalUser admin = portalAdmin();
        // Свободное от ссылок системное значение — чтобы отказ был
        // именно за «системное», а не за «используется»
        Map<String, Object> answer = values.list(admin);
        Map<String, Object> system = rows(answer, "enum_import_kind").stream()
                .filter(r -> Boolean.TRUE.equals(r.get("isSystem")))
                .filter(r -> ((Number) r.get("usage")).longValue() == 0)
                .findFirst().orElseThrow();
        String id = String.valueOf(system.get("valueId"));
        String code = String.valueOf(system.get("code"));
        String name = String.valueOf(system.get("name"));

        expectRejection(409, () -> values.delete(admin, "enum_import_kind", id));
        expectRejection(409, () -> values.save(admin, "enum_import_kind", id,
                new EnumValue(code, name, null, null, false, Map.of())));
        expectRejection(409, () -> values.save(admin, "enum_import_kind", id,
                new EnumValue("99", name, null, null, true, Map.of())));
        try {
            Map<String, Object> renamed = values.save(admin, "enum_import_kind", id,
                    new EnumValue(code, name + " (QA)", null, (Integer) number(system.get("sortOrder")),
                            true, Map.of()));
            assertEquals(name + " (QA)", row(renamed, "enum_import_kind", code).get("name"),
                    "имя системного значения править можно");
        } finally {
            values.save(admin, "enum_import_kind", id,
                    new EnumValue(code, name, null, (Integer) number(system.get("sortOrder")), true, Map.of()));
        }
    }

    @Test
    void onlyAdministratorEdits() {
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> values.list(client));
        expectRejection(403, () -> values.save(client, ORDER_TYPE, null,
                new EnumValue("98", "QA", null, null, true, Map.of())));
        PortalUser admin = portalAdmin();
        expectRejection(404, () -> values.save(admin, "orders", null,
                new EnumValue("98", "QA", null, null, true, Map.of())));
        expectRejection(404, () -> values.delete(admin, ORDER_TYPE, "32000"));
    }

    /* ---------------------------- вспомогательное ---------------------------- */

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> answer, String part) {
        return (List<Map<String, Object>>) answer.get(part);
    }

    private static List<Map<String, Object>> rows(Map<String, Object> answer, String enumKey) {
        return list(answer, "rows").stream().filter(r -> enumKey.equals(r.get("enumKey"))).toList();
    }

    private static Map<String, Object> row(Map<String, Object> answer, String enumKey, String code) {
        return rows(answer, enumKey).stream()
                .filter(r -> code.equals(String.valueOf(r.get("code"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("в " + enumKey + " нет значения " + code));
    }

    @SuppressWarnings("unchecked")
    private static String translation(Map<String, Object> row, String locale) {
        Map<String, Object> v = (Map<String, Object>) ((Map<String, Object>) row.get("values")).get(locale);
        return v == null ? null : (String) v.get("name");
    }

    private static Object number(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
