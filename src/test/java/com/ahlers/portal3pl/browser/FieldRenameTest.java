package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Клиентское название поля доходит до экрана и переживает смену языка.
 *
 * <p>Эта проверка написана по следам двух поломок подряд. Сначала
 * переименования вообще не доезжали: {@code i18n.js} перетирал
 * {@code localizeColumnLabel} собственной заглушкой, и клиент видел
 * портальную подпись. Потом исправление «всегда брать подпись сервера»
 * убрало из карточки товара обязательные поля. Оба раза сервисные тесты
 * были зелёными — название приходило правильное, терялось оно
 * на отрисовке.
 *
 * <p>Поэтому проверяем ровно то, что видит человек: заголовок колонки
 * в списке товаров, и отдельно — что он остался клиентским после
 * переключения на английский, пока сосед по таблице стал английским.
 */
class FieldRenameTest extends BrowserTestBase {

    private static final String CUSTOM = "Код клиента (проверка)";

    @Test
    void renamedColumnSurvivesLanguageSwitch() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        rename(CUSTOM);

        signInUiAsClient();
        openSkuList();

        List<String> ru = headers("skuHead");
        assertTrue(ru.contains(CUSTOM),
                "в списке товаров нет клиентского названия поля. Заголовки: " + ru);
        assertFalse(ru.contains(portalLabel()),
                "рядом с клиентским названием осталось портальное: " + ru);

        // Английский: клиентское название не переводится — его придумал
        // клиент, и ждать от переводчика его перевода нельзя. А соседняя
        // колонка перевестись обязана, иначе проверка ничего не значит.
        String nameEn = text("col.sku.name", "en");
        page.evaluate("code => portalSetLanguage(code)", "en");
        page.waitForFunction("expected => Array.from(document.querySelectorAll('#skuHead th'))"
                + ".some(th => th.textContent.trim() === expected)", nameEn);

        List<String> en = headers("skuHead");
        assertTrue(en.contains(CUSTOM),
                "после переключения языка клиентское название потерялось. Заголовки: " + en);
        assertTrue(en.contains(nameEn),
                "соседняя колонка не перевелась — проверка смотрит не туда: " + en);
        assertNoJsErrors();
    }

    /** Возвращаем справочник в исходное состояние: пустая подпись — это отмена. */
    @AfterEach
    void dropRename() {
        rename("");
    }

    private void rename(String label) {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("SELECT dbo.admin_field_label_save(:dep::uuid, 'sku'::text,"
                                + " 'code'::text, :label::text)")
                        .param("dep", depositorId(USER, DEPOSITOR))
                        .param("label", label)
                        .query()
                        .listOfRows());
    }

    /** Портальное название того же поля — на языке пользователя. */
    private String portalLabel() {
        return text("col.sku.code", "ru");
    }

    private String text(String key, String locale) {
        return jdbc.sql("SELECT value FROM dbo.ui_texts(:code::text) WHERE text_key = :key")
                .param("code", locale)
                .param("key", key)
                .query(String.class)
                .single();
    }

    /** Пользователь портала, которым ходит проверка. */
    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
