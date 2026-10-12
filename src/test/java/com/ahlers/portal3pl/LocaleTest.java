package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.i18n.LocaleService;
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
 * Языки портала (V25).
 * <p>
 * Главное, что здесь проверяется: непереведённый ключ не превращается
 * в пустое место на экране — вместо него приходит текст на языке портала
 * по умолчанию (английском).
 */
class LocaleTest extends PortalTestBase {

    /** Ключ, который точно есть в каталоге: подпись вкладки «Заказы». */
    private static final String KEY = "common.button.orders";

    @Autowired
    LocaleService locales;

    @AfterEach
    void restoreState() {
        // язык пользователя и пробные переводы возвращаем как было
        new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(tx -> {
                    jdbc.sql("UPDATE dbo.app_user SET locale_id = NULL WHERE login = :login")
                            .param("login", USER).update();
                    jdbc.sql("DELETE FROM dbo.ui_text WHERE locale_id ="
                                    + " (SELECT locale_id FROM dbo.dictionary_locale WHERE code = 'nl')")
                            .update();
                });
    }

    /**
     * Два списка языков — и это не дублирование.
     *
     * <p>{@code locales()} отвечает на вопрос «что показать в
     * переключателе» и выключенные языки намеренно скрывает:
     * выбрать язык, для которого нет переводов, человек не должен.
     * {@code allLocales()} — справочник целиком, он нужен администратору,
     * чтобы язык можно было подготовить и включить.
     *
     * <p>С V44 казахский и нидерландский выключены: переводов у них нет
     * ни для одного из 1326 ключей.
     */
    @Test
    void englishIsThePortalDefault() {
        assertEquals("en", locales.defaultLocale());

        List<Map<String, Object>> shown = locales.locales();
        assertTrue(shown.size() >= 2, "в переключателе есть хотя бы английский и русский");
        assertEquals("en", shown.getFirst().get("code"), "английский идёт первым");
        assertEquals(1, shown.stream().filter(l -> Boolean.TRUE.equals(l.get("isDefault"))).count(),
                "язык по умолчанию ровно один");
        assertTrue(shown.stream().noneMatch(l -> "nl".equals(l.get("code"))),
                "выключенный язык в переключателе не предлагается");

        List<Map<String, Object>> all = locales.allLocales(portalAdmin());
        assertTrue(all.size() >= 4, "в справочнике четыре языка: en, ru, nl, kk");
        assertTrue(all.stream().anyMatch(l -> "nl".equals(l.get("code"))),
                "выключенный язык остаётся в справочнике — иначе его не включить обратно");

        assertEquals("Depositor", locales.texts("en").get("common.label.depositor"),
                "поклажедатель по-английски — depositor, а не client");
    }

    /**
     * Готовить переводы можно и для выключенного языка.
     *
     * <p>Иначе получается замкнутый круг: язык выключен — колонки в
     * таблице переводов нет — тексты завести нельзя; текстов нет —
     * включать язык нельзя. Так новый язык в портале не появился бы
     * никогда (V47).
     */
    @Test
    void disabledLanguageCanStillBeTranslated() {
        PortalUser admin = portalAdmin();
        locales.save(admin, KEY, "nl", "Bestellingen");
        assertEquals("Bestellingen", locales.texts("nl").get(KEY),
                "перевод для выключенного языка сохраняется и читается");

        // но выбрать себе выключенный язык нельзя: переводов почти нет,
        // и человек увидел бы портал на языке отката
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(404, () -> locales.setLocaleOf(client, "nl"));

        locales.save(admin, KEY, "nl", null);
    }

    @Test
    void textsComeInRequestedLanguage() {
        Map<String, Object> en = locales.texts("en");
        Map<String, Object> ru = locales.texts("ru");
        assertFalse(en.isEmpty());
        assertEquals(en.size(), ru.size(), "набор ключей не зависит от языка");
        assertEquals("Orders", en.get(KEY));
        assertEquals("Заказы", ru.get(KEY));
    }

    @Test
    void untranslatedKeyFallsBackToDefault() {
        // нидерландский ещё не заполнен — портал обязан показать английский,
        // а не пустую кнопку
        Map<String, Object> nl = locales.texts("nl");
        assertEquals(locales.texts("en").size(), nl.size(), "ключи те же, что и в английском");
        assertEquals("Orders", nl.get(KEY));
    }

    @Test
    void savedTranslationIsUsedAndRemovedOnClear() {
        PortalUser admin = portalAdmin();
        locales.save(admin, KEY, "nl", "Bestellingen");
        assertEquals("Bestellingen", locales.texts("nl").get(KEY));

        // пустое значение означает «перевода нет»: строка убирается,
        // и снова работает откат, а не пустая подпись
        locales.save(admin, KEY, "nl", "   ");
        assertEquals("Orders", locales.texts("nl").get(KEY));
    }

    @Test
    void unknownKeyAndLanguageRejected() {
        PortalUser admin = portalAdmin();
        expectRejection(404, () -> locales.save(admin, "no.such.key", "ru", "Текст"));
        expectRejection(404, () -> locales.save(admin, KEY, "fr", "Commandes"));
        expectRejection(404, () -> locales.texts("fr"));
    }

    @Test
    void userLanguageIsRememberedInAccount() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertEquals("en", locales.localeOf(USER), "пока не выбран — язык портала");

        locales.setLocaleOf(user, "ru");
        assertEquals("ru", locales.localeOf(USER), "выбор хранится в учётной записи, а не в браузере");

        expectRejection(404, () -> locales.setLocaleOf(user, "fr"));
    }

    @Test
    void translationTableShowsGaps() {
        PortalUser admin = portalAdmin();
        List<Map<String, Object>> matrix = locales.matrix(admin);
        assertFalse(matrix.isEmpty());

        Map<String, Object> row = matrix.stream()
                .filter(r -> KEY.equals(r.get("textKey")))
                .findFirst()
                .orElseThrow();
        // признаки ключа: по ним переводчик понимает, что именно он переводит
        assertEquals("common", row.get("scope"));
        assertEquals("button", row.get("element"));
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) row.get("values");
        assertEquals("Orders", values.get("en"));
        assertEquals("Заказы", values.get("ru"));
        assertNotNull(values, "в таблице есть колонка на каждый язык");
        assertTrue(values.containsKey("nl") && values.get("nl") == null,
                "пустая ячейка показывает, что перевода нет");
    }

    @Test
    void onlyAdminEditsTexts() {
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> locales.matrix(client));
        expectRejection(403, () -> locales.save(client, KEY, "nl", "Bestellingen"));
    }
}
