package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Вопрос перед удалением — на языке пользователя.
 *
 * <p>Окно подтверждения рисует браузер, и текст для него собирает экран.
 * Поэтому оно оставалось русским у клиента с английским интерфейсом даже
 * после того, как сообщения сервера переехали в каталог текстов (V57):
 * сервер в этом окне не участвует вовсе. Починено в V58 — проверка
 * сторожит, чтобы не вернулось.
 *
 * <p>Удаление подтверждать нельзя: окно закрываем отказом, товар
 * остаётся на месте. Нам нужен текст вопроса, а не его последствия.
 */
class ConfirmDialogLanguageTest extends BrowserTestBase {

    @Test
    void deleteQuestionIsInUserLanguage() {
        PortalUser user = signedInUser();
        localeOf(user, "en");

        List<String> asked = new ArrayList<>();
        page.onDialog(dialog -> {
            asked.add(dialog.message());
            dialog.dismiss();           // отказ: товар не удаляем
        });

        signInUiAsClient();
        openSkuList();
        String skuCode = anySkuCode();
        page.evaluate("code => portalOpenSku(code)", skuCode);
        page.click("[onclick^='portalSkuDelete']");

        assertFalse(asked.isEmpty(), "портал не спросил подтверждения перед удалением товара");
        // Вопрос состоит из двух строк: сам вопрос и пояснение про архив.
        // Сверяем первую строку целиком с текстом из каталога — так
        // проверка поймает и забытый перевод, и потерянную подстановку.
        assertEquals(expectedQuestion(skuCode), asked.getFirst().split("\n")[0],
                "вопрос перед удалением показан не на языке пользователя"
                        + " либо подстановка не сработала");
        assertNoJsErrors();
    }

    /**
     * Любой товар из открытого списка.
     *
     * <p>Код берётся с экрана, а не из базы, и это важно: карточку
     * открывает {@code portalOpenSku}, который ищет строку в уже
     * загруженном списке. Первый же товар из базы мог оказаться
     * на другой странице списка — и проверка упала бы не на том,
     * что проверяет. Вписывать код в проверку тоже нельзя: набор
     * данных разработки меняется.
     */
    private String anySkuCode() {
        Object code = page.evaluate("() => { const rows = (window.PORTAL"
                + " && PORTAL.catalog && PORTAL.catalog.sku) || [];"
                + " const row = rows.find(r => r.isActive !== false);"
                + " return row ? row.code : null; }");
        if (code == null) {
            Assumptions.abort("в списке товаров пусто — удалять нечего");
        }
        return String.valueOf(code);
    }

    /** Ожидаемый вопрос: английский текст из каталога с подставленным кодом. */
    private String expectedQuestion(String skuCode) {
        String template = jdbc
                .sql("SELECT value FROM dbo.ui_texts('en') WHERE text_key = :key")
                .param("key", "sku.message.confirm_delete_item")
                .query(String.class)
                .single();
        return template.replace("{code}", skuCode);
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }
}
