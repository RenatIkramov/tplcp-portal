package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Карточка документа показывает номер договора выбранного клиента (V108).
 *
 * <p>Прежде номер брался из пустого демо-справочника прототипа, и в
 * карточке всегда стоял прочерк — хотя в базе номер есть. Номер должен
 * совпасть с тем, что записано у поклажедателя; если у клиента его нет,
 * проверять нечего.
 */
class DocumentContractNumberTest extends BrowserTestBase {

    @Test
    void cardShowsTheDepositorsContractNumber() {
        String number = jdbc.sql("SELECT contract_number FROM dbo.depositors_for_user(:login::text)"
                        + " WHERE code = :code::text")
                .param("login", USER)
                .param("code", DEPOSITOR)
                .query(String.class)
                .optional()
                .orElse(null);
        assumeTrue(number != null && !number.isBlank(), "у клиента нет номера договора");

        signInUiAsClient();
        page.evaluate("() => showTab('orders')");
        page.waitForSelector("#ordersBody tr[data-call]");
        page.click("#ordersBody tr[data-call]");
        page.waitForFunction("() => document.getElementById('orderDocMeta').textContent.trim().length > 0");
        String meta = page.innerText("#orderDocMeta");
        assertTrue(meta.contains(number), "в карточке нет номера договора " + number + ": " + meta);
        assertNoJsErrors();
    }
}
