package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Переводы перечислений (V86).
 *
 * <p>Перечисление переводит база: {@code enum_name()} берёт язык из
 * {@code app.locale}, а его в каждое соединение пишет
 * {@code LocaleDataSource}. Поэтому проверяется путь целиком — язык
 * пользователя → соединение → имя значения, — а не одна функция.
 *
 * <p>Вкладки «Перечисления» в «Текстах интерфейса» больше нет (V112):
 * переводы ведутся в окне значения перечисления (EnumValueTest,
 * EnumValuesTabTest). Здесь — путь перевода к человеку и функция сохранения.
 *
 * <p>Пробы пишутся на казахский: он выключен и переводов перечислений
 * у него нет, так что тест не задевает то, что видят люди.
 */
class EnumTextTest extends PortalTestBase {

    /** Единица «штука», МКЕИ 796: переведена на русский в самой V86. */
    private static final String PIECE = "1";

    /** Так переводят представления: основное имя передаётся запасным. */
    private static final String NAME = "SELECT dbo.enum_name('enum_uom', u.id::text, u.name)"
            + " FROM dbo.enum_uom u WHERE u.id::text = :id";
    private static final String SYMBOL = "SELECT dbo.enum_symbol('enum_uom', u.id::text, u.symbol)"
            + " FROM dbo.enum_uom u WHERE u.id::text = :id";

    @AfterEach
    void restore() {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("DELETE FROM dbo.enum_text WHERE locale_id ="
                    + " (SELECT locale_id FROM dbo.dictionary_locale WHERE code = 'kk')").update();
        });
    }

    /** Имя и обозначение единицы — так, как их получит любой запрос портала. */
    private List<String> pieceAs(PortalUser user, String locale) {
        if (locale != null) {
            localeOf(user, locale);
        }
        signIn();
        return new TransactionTemplate(txManager).execute(tx -> List.of(
                jdbc.sql(NAME)
                        .param("id", PIECE).query(String.class).single(),
                jdbc.sql(SYMBOL)
                        .param("id", PIECE).query(String.class).single()));
    }

    @Test
    void namesFollowTheUserLanguage() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertEquals(List.of("Piece", "pcs"), pieceAs(user, null),
                "язык не выбран — действует язык портала, английский");
        assertEquals(List.of("Штука", "шт"), pieceAs(user, "ru"));
        assertEquals(List.of("Piece", "pcs"), pieceAs(user, "en"));
    }

    /**
     * Соединения из пула переиспользуются. Если бы язык писался один раз,
     * следующий пользователь увидел бы портал на языке предыдущего.
     */
    @Test
    void languageIsSetAnewForEveryUser() {
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signIn();
        String ru = new TransactionTemplate(txManager).execute(tx ->
                jdbc.sql(NAME)
                        .param("id", PIECE).query(String.class).single());
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        String other = new TransactionTemplate(txManager).execute(tx ->
                jdbc.sql(NAME)
                        .param("id", PIECE).query(String.class).single());
        assertEquals("Штука", ru);
        assertEquals("Piece", other, "у второго пользователя язык не выбран — английский");
    }

    /**
     * Сохранение перевода — функция базы {@code enum_text_save}: её зовёт
     * окно значения перечисления (EnumValueService, V95). Пустой перевод
     * удаляется, а не хранится пустой строкой — иначе он заслонял бы
     * основное имя пустотой.
     */
    @Test
    void emptyTranslationIsRemovedNotStored() {
        String count = "SELECT count(*)::text FROM dbo.enum_text"
                + " WHERE enum_key = 'enum_uom' AND value_id = '" + PIECE + "' AND locale_id ="
                + " (SELECT locale_id FROM dbo.dictionary_locale WHERE code = 'kk')";
        save("Дана", "дн");
        assertEquals("1", singleValue(count));
        save(null, null);
        assertEquals("0", singleValue(count), "пустой перевод должен удаляться");
    }

    private void save(String name, String symbol) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> jdbc.sql(
                        "SELECT dbo.enum_text_save('enum_uom', :id::text, 'kk', :name::text, :symbol::text)")
                .param("id", PIECE).param("name", name).param("symbol", symbol)
                .query().listOfRows());
    }
}
