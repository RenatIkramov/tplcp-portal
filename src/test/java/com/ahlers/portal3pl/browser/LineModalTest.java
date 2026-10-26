package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.admin.DepositorSettingsService;
import com.ahlers.portal3pl.stock.StockService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

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

    /** Тот же сервис, в который ходит экран, — для разбора расхождений. */
    @Autowired
    StockService stock;

    private String docId;

    /**
     * Запросы окна к запасам — для сообщения об ошибке.
     *
     * <p>Проверка однажды упала с «показан нулём: 0 шт», и по этому
     * сообщению нельзя было понять ничего: то ли экран спросил не тот
     * склад, то ли не тот товар, то ли сервер не нашёл строку.
     * Сообщение, по которому нельзя начать разбор, стоит дешевле
     * своего отсутствия — оно только отнимает прогон.
     */
    private final List<String> stockAsked = new ArrayList<>();

    /**
     * Окно строки показывает те же числа, что список запасов.
     *
     * <p>ОТКУДА БЕРЁТСЯ ОЖИДАЕМОЕ. Прежде проверка выбирала пару
     * «товар + склад» запросом к базе — от имени поклажедателя,
     * которого назначала сама. А окно работает от имени ВОШЕДШЕГО,
     * и его поклажедатель определяется входом. Пока он совпадал,
     * проверка была зелёной; стоило им разойтись — и проверка ждала
     * числа по строке, которой вошедший не видит вовсе, а окно честно
     * показывало ноль.
     *
     * <p>Поэтому пара берётся с экрана запасов: это ровно то, что
     * видит вошедший. Заодно проверка стала строже — сравниваются
     * не «не ноль», а сами числа: список и окно читают один и тот же
     * остаток, и расходиться им нельзя.
     */
    @Test
    void stockNumbersMatchTheStockScreen() {
        page.onRequest(request -> {
            if (request.url().contains("/api/stock") && !request.url().contains("/details")) {
                stockAsked.add(request.url().replaceAll("^.*/api/stock", "/api/stock"));
            }
        });
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        priceSetting(false, false);

        signInUiAsClient();

        // Строка запасов глазами вошедшего: товар, склад и три числа.
        //
        // Страницу берём побольше: список режет сервер (V79), и строка
        // с остатком может не попасть в первые двадцать — проверка
        // упала бы на пустом ожидании, а причина была бы в размере
        // страницы, а не в окне.
        page.evaluate("() => { showTab('stock');"
                + " if (typeof paginationState !== 'undefined') paginationState.stock.pageSize = 100;"
                + " return portalReloadStock(); }");
        page.waitForFunction("() => (PORTAL.rows || []).some(r => Number(r.stock) > 0)");
        @SuppressWarnings("unchecked")
        List<String> row = (List<String>) page.evaluate("""
                () => {
                  const r = (PORTAL.rows || []).find(x => Number(x.stock) > 0);
                  return [r.code, r.warehouse, String(r.stock), String(r.available),
                          String(r.incoming)];
                }
                """);
        String skuCode = row.get(0);
        String warehouseId = row.get(1);

        createDraftWithWarehouse(warehouseId);
        openLineModalWith(skuCode);

        String total = text("lineEditStockTotal");
        String avail = text("lineEditStockAvail");
        String incoming = text("lineEditStockIncoming");

        // Что именно спрашивал экран — в сообщении: без этого
        // «показан нулём» не говорит, где искать причину.
        String asked = "\nтовар " + skuCode + ", склад документа " + warehouseId
                + "\nсписок запасов показывает: " + row
                + "\nпоклажедатель сеанса: " + page.evaluate("() => PORTAL.depositorCode")
                + "\nэкран выбрал склад: " + page.evaluate(
                        "() => { const f = document.getElementById('rf_warehouse');"
                                + " return f ? f.value : 'поля нет'; }")
                + "\nэкран выбрал товар: " + page.evaluate(
                        "() => typeof lineEditSelectedProduct !== 'undefined'"
                                + " && lineEditSelectedProduct"
                                + " ? lineEditSelectedProduct.code : 'не выбран'")
                + "\nзапросы к запасам: " + stockAsked
                // Значения в кавычках и с длиной: невидимый пробел
                // в коде или в складе выглядит в сообщении так же,
                // как его отсутствие, а отбор ломает намертво.
                + "\nзначения: товар «" + skuCode + "» (" + skuCode.length()
                + "), склад «" + warehouseId + "» (" + warehouseId.length() + ")"
                // Отказы сервера: 400 на запрос окна экран проглатывает
                // молча, а в полях остаются нули, которые прототип
                // написал до нашего ответа. Ноль выглядит как ответ.
                + "\nотказы сервера за сеанс: " + refusedRequests()
                // И тот же отбор, сделанный сервисом напрямую: если
                // здесь строка есть, а в окне её нет, дело не в отборе,
                // а в пути от сервера до экрана.
                + "\nсервис по тому же отбору: " + directly(user, warehouseId, skuCode);

        assertNotEquals("—", total, "остаток в окне не заполнился: " + total + asked);
        assertTrue(total.matches("^\\d+([.,]\\d+)?\\s+\\S+$"),
                "остаток должен быть числом с единицей измерения, а пришло: " + total + asked);
        assertEquals(number(row.get(2)), number(total),
                "остаток в окне и в списке запасов разошлись" + asked);
        assertEquals(number(row.get(3)), number(avail),
                "доступное в окне и в списке запасов разошлись" + asked);
        assertEquals(number(row.get(4)), number(incoming),
                "«К получению» в окне и в списке запасов разошлись" + asked);
        assertNoJsErrors();
    }

    /**
     * Тот же отбор сервисом, в обход экрана.
     *
     * <p>Отказ не роняет разбор, а становится его частью: ради него
     * этот вызов и сделан.
     */
    private String directly(PortalUser user, String warehouseId, String skuCode) {
        try {
            // Контекст поклажедателя для прямого вызова: без него база
            // честно вернёт ноль строк, и разбор уведёт не туда.
            signIn();
            return String.valueOf(listRows(stock.list(user,
                    java.util.Map.of("filter.warehouse", warehouseId,
                            "filter.code", skuCode, "pageSize", "50"))));
        } catch (RuntimeException e) {
            return "отказ — " + e.getMessage();
        }
    }

    /** Число из подписи «180.000 шт» или «180» — для сравнения списка с окном. */
    private static double number(String text) {
        String digits = text.trim().split("\\s+")[0].replace(',', '.');
        return digits.isBlank() ? 0 : Double.parseDouble(digits);
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
