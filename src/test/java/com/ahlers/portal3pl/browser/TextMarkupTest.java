package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.i18n.LocaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тексты интерфейса — без чужой разметки (аудит 11.11.2026, S5; V122).
 *
 * <p>НАЙДЕНО АУДИТОМ. Перевод вставлялся в страницу как разметка, на форме
 * входа тоже: текст с картинкой и обработчиком выполнился бы у каждого,
 * кто открыл портал, — и мог бы читать набираемый пароль. Правило теперь
 * в двух местах: база не принимает запрещённую разметку, экран пропускает
 * разметку текстов через белый список.
 */
class TextMarkupTest extends BrowserTestBase {

    private static final String TRAP = "<img src=x onerror=\"window.__xss=1\"><b>жирный</b>";

    @Autowired
    LocaleService locales;

    @Autowired
    ColumnService columns;

    /** База: ни сервисом, ни запросом руками текст с картинкой не записать. */
    @Test
    void databaseRefusesForbiddenMarkup() {
        PortalUser admin = portalAdmin();
        String cancel = singleValue("SELECT t.value FROM dbo.ui_text t JOIN dbo.dictionary_locale l"
                + " USING (locale_id) WHERE t.text_key = 'common.button.cancel' AND l.code = 'ru'");
        try {
            refuses(admin);
        } finally {
            // если правило сломается, ловушка не должна остаться в текстах портала
            jdbc.sql("UPDATE dbo.ui_text SET value = :v WHERE text_key = 'common.button.cancel'"
                            + " AND locale_id = (SELECT locale_id FROM dbo.dictionary_locale WHERE code = 'ru')"
                            + " AND value IS DISTINCT FROM :v")
                    .param("v", cancel).update();
            inContext("DELETE FROM dbo.depositor_field_label WHERE custom_label = '<b>Артикул</b>'");
        }
    }

    private void refuses(PortalUser admin) {
        expectRejection(400, () -> locales.save(admin, "common.button.cancel", "ru", TRAP));
        assertThrows(RuntimeException.class, () -> jdbc.sql(
                        "UPDATE dbo.ui_text SET value = :v WHERE text_key = 'common.button.cancel'")
                .param("v", TRAP).update(), "запрос руками записал текст с картинкой");
        expectRejection(400, () -> columns.saveFieldLabel(admin, depositorId(ADMIN, DEPOSITOR),
                "sku", "code", "<b>Артикул</b>"));
        // разрешённая разметка — по-прежнему можно
        assertEquals(Boolean.TRUE, jdbc.sql("SELECT dbo.ui_text_markup_ok(:v)")
                .param("v", "<b>Жирный</b><br/>Почта: <a href=\"mailto:a@b.kz\">a@b.kz</a>")
                .query(Boolean.class).single());
    }

    /** Экран: из текста с картинкой остаётся текст и жирный, картинки нет. */
    @Test
    void screenKeepsOnlyAllowedMarkup() {
        page.navigate(baseUrl() + "/");
        page.waitForFunction("() => window.PORTAL && PORTAL.i18n && !!PORTAL.i18n.code");
        page.evaluate("t => { PORTAL.i18n.texts['qa.markup.trap'] = t;"
                + " const s = document.createElement('span'); s.id = 'qaTrap';"
                + " s.setAttribute('data-i18n', 'qa.markup.trap'); document.body.appendChild(s); }", TRAP);
        page.waitForFunction("() => document.getElementById('qaTrap').textContent.length > 0");
        page.waitForTimeout(300);   // картинке с ошибкой загрузки — время сработать

        assertNull(page.evaluate("() => window.__xss === undefined ? null : window.__xss"),
                "текст интерфейса выполнился как скрипт");
        @SuppressWarnings("unchecked")
        Map<String, Object> shown = (Map<String, Object>) page.evaluate("""
                () => { const s = document.getElementById('qaTrap');
                        const made = document.createElement('div');
                        made.innerHTML = PORTAL.t('qa.markup.trap');
                        return {img: s.querySelectorAll('img').length, bold: s.querySelectorAll('b').length,
                                madeImg: made.querySelectorAll('img').length}; }
                """);
        assertEquals(0, ((Number) shown.get("img")).intValue(), "картинка из текста осталась");
        assertEquals(1, ((Number) shown.get("bold")).intValue(), "разрешённый жирный пропал");
        assertEquals(0, ((Number) shown.get("madeImg")).intValue(), "PORTAL.t пропустил картинку");
        assertTrue(page.isVisible("#loginScreen"), "форма входа пропала");
        assertNoJsErrors();
    }
}
