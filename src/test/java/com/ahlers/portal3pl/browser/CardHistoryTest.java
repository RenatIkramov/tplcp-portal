package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.audit.AuditService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «История» в карточках справочников и пользователя (V118).
 *
 * <p>Клиент меняет телефон своего поставщика и возвращает прежний — в
 * истории карточки видно оба изменения. У администратора «История» есть
 * у склада (вкладка) и у пользователя (раздел внизу формы), а клиенту
 * история пользователя не отдаётся вовсе.
 */
class CardHistoryTest extends BrowserTestBase {

    @Autowired
    private AuditService audit;

    @Test
    void supplierCardShowsItsChanges() {
        ensurePassword(USER, USER_PASSWORD);
        localeOf(authService.authenticate(USER, USER_PASSWORD), "ru");
        signInUiAsClient();

        String marker = "+7 700 " + (System.nanoTime() % 10_000_000);
        @SuppressWarnings("unchecked")
        Map<String, Object> s = (Map<String, Object>) page.evaluate("""
                async (marker) => {
                  const call = (method, url, body) => fetch(url, {method,
                      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
                      body: body ? JSON.stringify(body) : undefined}).then(r => r.json());
                  const list = await call('GET', '/api/suppliers');
                  const rows = Array.isArray(list) ? list : (list.rows || []);
                  const row = rows[0];
                  await call('PUT', '/api/suppliers/' + row.supplierId, {contact_phone: marker});
                  await call('PUT', '/api/suppliers/' + row.supplierId, {contact_phone: row.contactPhone || null});
                  return {id: row.supplierId};
                }
                """, marker);
        page.evaluate("id => portalOpenPartner('suppliers', id)", s.get("id"));
        page.waitForSelector("#partnerTabs [data-args*='history']");
        page.click("#partnerTabs [data-args*='history']");
        page.waitForSelector("#partnerBody .history-table");
        String shown = page.innerText("#partnerBody");
        assertTrue(shown.contains(marker), "в истории поставщика нет изменения телефона: " + shown);
        assertTrue(shown.contains(text("audit.field.contact_phone")), "поле не подписано: " + shown);
        assertNoJsErrors();
    }

    @Test
    void adminCardsHaveHistory() {
        localeOf(portalAdmin(), "ru");
        signInUi(ADMIN, ADMIN_PASSWORD);

        // склад: вкладка «История»
        page.evaluate("() => portalOpenPortalDir('warehousesDir', 'WH-ALA')");
        page.waitForSelector("#portalDirTabs [data-tab='history']");
        page.click("#portalDirTabs [data-tab='history']");
        page.waitForFunction("() => !!document.querySelector('#portalDirBody .history-table, #portalDirBody .hint')"
                + " && !document.getElementById('portalDirBody').innerText.includes('" + text("stock.text.loading_data") + "')");

        // пользователь: раздел внизу формы, читается при раскрытии
        String id = userId(USER);
        page.waitForFunction("id => !!document.querySelector('#adminUsersBody tr[data-args*=\"' + id + '\"]')", id);
        page.evaluate("id => portalOpenAdminUser(id)", id);
        page.waitForSelector("#auHistory summary");
        page.click("#auHistory summary");
        page.waitForFunction("() => { const b = document.getElementById('auHistoryBody');"
                + " return !!b && !!b.querySelector('.history-table, .hint')"
                + " && !b.innerText.includes('" + text("stock.text.loading_data") + "'); }");
        assertNoJsErrors();
    }

    /** История пользователя — только администратору; неизвестный вид — 404. */
    @Test
    void clientCannotReadAdminHistory() {
        ensurePassword(USER, USER_PASSWORD);
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        signInAs(USER, DEPOSITOR);
        try {
            expectRejection(403, () -> audit.history(client, "app_user", userId(USER)));
            expectRejection(404, () -> audit.history(client, "audit_log", "1"));
        } finally {
            com.ahlers.portal3pl.config.DepositorContext.clear();
        }
    }

    private String text(String key) {
        return jdbc.sql("SELECT value FROM dbo.ui_texts('ru') WHERE text_key = :key")
                .param("key", key).query(String.class).single();
    }
}
