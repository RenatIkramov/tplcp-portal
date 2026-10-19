package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.admin.DepositorSettingsService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Окно ввода строки документа: числа настоящие, упаковки настоящие,
 * цена — по настройке клиента (V61).
 *
 * <p>Окно было подключено к демо-данным, и половина его не работала.
 * Упаковки брались из массива прототипа с демо-идентификаторами клиентов
 * — список выходил пустым, и окно прятало оба поля пересчёта. Остаток
 * и доступное приходили нулями. А «К получению» прототип считал
 * псевдослучайно от хэша кода товара, то есть показывал выдуманное число:
 * выдуманное хуже нуля — ноль видно, что заглушка, а 37 выглядит
 * как данные.
 *
 * <p>Проверка идёт через экран, а не через API: именно экран здесь и был
 * сломан, а сервер всё нужное отдавал с самого начала.
 */
class LineModalTest extends BrowserTestBase {

    @Autowired
    DepositorSettingsService settings;

    private String docId;

    /**
     * Остаток, доступное и «К получению» — числа по складу документа,
     * а не нули и не выдумка.
     */
    @Test
    void stockNumbersComeFromTheWarehouseOfTheDocument() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        priceSetting(false, false);

        // Товар, который точно лежит на складе документа: его числа
        // и должны появиться в окне.
        String[] pair = stockedSkuAndWarehouse();

        signInUiAsClient();
        createDraftWithWarehouse(pair[1]);
        openLineModalWith(pair[0]);

        String total = text("lineEditStockTotal");
        String avail = text("lineEditStockAvail");
        String incoming = text("lineEditStockIncoming");

        assertNotEquals("—", total, "остаток в окне не заполнился: " + total);
        assertTrue(total.matches("^\\d+([.,]\\d+)?\\s+\\S+$"),
                "остаток должен быть числом с единицей измерения, а пришло: " + total);
        assertFalse(total.startsWith("0 "),
                "остаток товара, который лежит на складе, показан нулём: " + total);
        assertNotEquals("—", avail, "доступное в окне не заполнилось");
        // «К получению» по этому товару может быть и нулём — это законно.
        // Незаконно «—»: склад выбран, остатки прочитаны.
        assertNotEquals("—", incoming, "«К получению» в окне не заполнилось");
        assertNoJsErrors();
    }

    /**
     * Упаковки приходят из справочника клиента, и пересчёт количества
     * работает: «2 упаковки × коэффициент».
     */
    @Test
    void packagingRecalculatesQuantity() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        priceSetting(false, false);

        String warehouseId = stockedSkuAndWarehouse()[1];
        String skuCode = packagedSkuCode();

        signInUiAsClient();
        createDraftWithWarehouse(warehouseId);
        openLineModalWith(skuCode);

        page.waitForFunction(
                "() => { const s = document.getElementById('lineEditPackaging');"
                + " return !!s && s.options.length > 1; }");
        Object coefficient = page.evaluate("""
                () => {
                  const select = document.getElementById('lineEditPackaging');
                  const code = select.options[1].value;
                  select.value = code;
                  onLineEditPackagingChange();
                  document.getElementById('lineEditPackageCount').value = 2;
                  onLineEditPackageCountChange();
                  const pkg = PRODUCT_PACKAGING.find(x => x.packagingCode === code);
                  return pkg ? pkg.coefficient : null;
                }
                """);
        assertNotNull(coefficient, "упаковка выбралась, но её нет в списке PRODUCT_PACKAGING");

        String quantity = (String) page.evaluate(
                "() => document.getElementById('lineEditExpected').value");
        assertNotEquals("", quantity, "количество по упаковке не пересчиталось");
        double expected = 2 * Double.parseDouble(coefficient.toString());
        assertEquals(expected, Double.parseDouble(quantity), 0.0001,
                "количество не равно «2 × коэффициент» при коэффициенте " + coefficient);
        assertNoJsErrors();
    }

    /**
     * Настройка выключена — полей цены и суммы в окне нет.
     *
     * <p>Включённая и выключенная настройка проверяются ДВУМЯ тестами,
     * а не одним на два входа. Настройка приезжает в ответе /api/me, то
     * есть при входе, а повторно войти в том же окне браузера нельзя:
     * серверная сессия уже есть, портал её восстанавливает, и формы входа
     * на странице просто нет. Каждому тесту даётся своё окно — так проще,
     * чем выходить и заходить.
     */
    @Test
    void priceFieldsHiddenWhenSettingIsOff() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        String[] pair = stockedSkuAndWarehouse();

        priceSetting(false, false);
        signInUiAsClient();
        createDraftWithWarehouse(pair[1]);
        openLineModalWith(pair[0]);

        assertFalse(priceVisible(), "цена показана, хотя настройка выключена");
        assertNoJsErrors();
    }

    /** Настройка включена — поля цены и суммы на месте. */
    @Test
    void priceFieldsShownWhenSettingIsOn() {
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        String[] pair = stockedSkuAndWarehouse();

        priceSetting(true, false);
        signInUiAsClient();
        createDraftWithWarehouse(pair[1]);
        openLineModalWith(pair[0]);

        assertTrue(priceVisible(), "цена не показана, хотя настройка включена");
        assertNoJsErrors();
    }

    /* ---------------- помощники ---------------- */

    /**
     * Черновик поступления с заданным складом.
     *
     * <p>Склад задаётся нарочно, а не берётся первым из списка: числа
     * в окне считаются по складу документа, и проверка должна знать,
     * какой это склад, чтобы знать, какие числа ждать.
     */
    private void createDraftWithWarehouse(String warehouseId) {
        // Сохраняем сразу: карточка нового документа живёт на экране,
        // а идентификатор, по которому проверка ставит склад запросом,
        // выдаёт база при сохранении.
        page.evaluate("() => createNewReceiptDraft()");
        page.waitForFunction("() => !!currentReceiptRef");
        page.evaluate("() => saveReceiptDraft()");
        page.waitForFunction("() => !!(currentReceiptRef && currentReceiptRef.docId)");
        docId = (String) page.evaluate("() => currentReceiptRef.docId");
        page.evaluate("""
                async (arg) => {
                  await fetch('/api/receipts/' + arg.docId, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json', Accept: 'application/json'},
                    body: JSON.stringify({warehouse_id: arg.warehouseId})
                  });
                }
                """, java.util.Map.of("docId", docId, "warehouseId", warehouseId));
        openDraft();
    }

    private void openDraft() {
        page.evaluate("id => portalOpenDocumentById('receipts', id)", docId);
        page.waitForFunction("() => !!(currentReceiptRef && currentReceiptRef.docId)");
    }

    /** Открыть окно добавления строки и выбрать в нём товар по коду. */
    private void openLineModalWith(String skuCode) {
        page.evaluate("() => openLineAddModal()");
        page.waitForSelector("#lineEditOverlay.active");
        page.evaluate("code => selectLineEditProduct(code)", skuCode);
        // Числа дорисовываются после ответа с остатками — ждём не числа,
        // а исчезновение прочерка: само число проверяет уже тест.
        page.waitForFunction(
                "() => document.getElementById('lineEditStockTotal').textContent !== '—'");
    }

    private boolean priceVisible() {
        Object visible = page.evaluate("""
                () => {
                  const field = document.getElementById('lineEditPrice');
                  const row = field ? field.closest('.kv') : null;
                  return !!row && row.style.display !== 'none';
                }
                """);
        return Boolean.TRUE.equals(visible);
    }

    private String text(String elementId) {
        return ((String) page.evaluate("id => document.getElementById(id).textContent", elementId))
                .trim();
    }

    /**
     * Товар с остатком и склад, на котором он лежит.
     *
     * <p>Запрос идёт через singleValue — с выставленным контекстом
     * поклажедателя. Без него база честно вернула бы ноль строк,
     * и проверка упала бы не там, где ошибка.
     */
    private String[] stockedSkuAndWarehouse() {
        return singleValue("""
                SELECT sku_code || '|' || warehouse_id FROM dbo.v_stock_lines
                 WHERE quantity > 0 ORDER BY sku_code LIMIT 1
                """).split("\\|");
    }

    /** Товар, у которого есть упаковки: без них пересчёт не проверить. */
    private String packagedSkuCode() {
        return singleValue("""
                SELECT s.code FROM dbo.sku s
                 WHERE EXISTS (SELECT 1 FROM dbo.product_packaging p
                                WHERE p.sku_id = s.sku_id)
                 ORDER BY s.code LIMIT 1
                """);
    }

    private void priceSetting(boolean inReceipts, boolean inOrders) {
        settings.save(portalAdmin(), depositorId(ADMIN, DEPOSITOR),
                new DepositorSettingsService.Settings(false, inReceipts, inOrders));
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }

    /** Черновик за собой убираем: проверка создаёт настоящий документ. */
    @AfterEach
    void cleanUp() {
        priceSetting(false, false);
        if (docId != null && page != null) {
            try {
                page.evaluate("id => fetch('/api/receipts/' + id, {method: 'DELETE'})", docId);
            } catch (RuntimeException e) {
                System.out.println("не удалось удалить черновик поступления: " + e.getMessage());
            }
        }
    }
}
