package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.stock.StockLine;
import com.ahlers.portal3pl.stock.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чтение: запасы, документы, справочники. Работает против локальной базы
 * tplcp с загруженными демо-данными.
 */
class ReadApiTest extends PortalTestBase {

    @Autowired
    StockService stock;

    @Autowired
    DocumentService documents;

    @Autowired
    CatalogService catalog;

    @Autowired
    AuthService auth;

    // ---------- Запасы ----------

    /**
     * Запасы поклажедателя отдаются.
     *
     * <p>Точного числа строк здесь нет намеренно. Демо-данные лежат в той
     * же базе разработки, по которой мы щёлкаем руками: один заведённый
     * товар — и тест падает, не найдя при этом ни одной ошибки. Ровно это
     * и случилось с {@code skuAndFilters}, ждавшим восьми позиций.
     * Проверяется то, что обязано быть верно всегда.
     */
    @Test
    void depositorStock() {
        signIn();
        List<StockLine> rows = stock.list(null, null);
        assertFalse(rows.isEmpty(), "запасы поклажедателя отдаются");
        assertTrue(rows.stream().anyMatch(r -> "RK-ROG-01".equals(r.skuCode())),
                "в запасах есть демо-позиция RK-ROG-01");
    }

    /**
     * Фильтры сужают список и отдают только подходящее.
     *
     * <p>Раньше здесь стояли числа 8 и 3. Они говорили лишь о том, что
     * состав демо-данных не менялся, — а не о том, что фильтр работает.
     * Теперь утверждается именно работа: выдача не пуста, не больше полной
     * и не содержит ничего, что фильтру не соответствует. Условие
     * «не содержит лишнего» повторяет запрос: поиск идёт по коду и
     * наименованию, без учёта регистра.
     */
    @Test
    void stockFilters() {
        signIn();
        List<StockLine> all = stock.list(null, null);

        List<StockLine> almaty = stock.list("WH-ALA", null);
        assertFalse(almaty.isEmpty(), "на складе Алматы есть запасы");
        assertTrue(almaty.size() <= all.size(), "один склад — не больше, чем все");
        assertTrue(almaty.stream().allMatch(r -> "WH-ALA".equals(r.warehouseId())),
                "в выдаче только запрошенный склад");

        List<StockLine> found = stock.list(null, "рога");
        assertFalse(found.isEmpty(), "поиск по наименованию находит");
        assertTrue(found.stream().allMatch(r -> contains(r.skuName(), "рога")
                        || contains(r.skuCode(), "рога")),
                "в выдаче нет строк, не подходящих под поиск");
    }

    /**
     * Изоляция поклажедателей.
     *
     * <p>Сравнивались числа 9 и 4; сравниваются множества кодов. Правило
     * в том, что они не пересекаются ВОВСЕ, и оно не зависит ни от состава
     * демо-данных, ни от того, что кто-то заведёт позицию руками.
     */
    @Test
    void differentDepositorsSeeDifferentData() {
        signIn();
        Set<String> mine = skuCodesInStock();
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        Set<String> theirs = skuCodesInStock();

        assertFalse(mine.isEmpty(), "у «Рога и Копыта» есть запасы");
        assertFalse(theirs.isEmpty(), "у «Молочных рек» тоже");
        assertTrue(Collections.disjoint(mine, theirs),
                "ни одной общей позиции: клиенты не видят чужого");
    }

    /** §6.4: вошёл, но поклажедателя не выбрал — данные не отдаются (409). */
    @Test
    void noDepositorNoData() {
        signInAs(USER, null);
        expectRejection(409, () -> stock.list(null, null));
    }

    /** А без входа вообще — 401, независимо от поклажедателя. */
    @Test
    void noLoginNoData() {
        expectRejection(401, () -> stock.list(null, null));
    }

    /**
     * Чужой поклажедатель отсекается при ВЫБОРЕ, в сессии, а не на чтении:
     * политика RLS отдаёт то, что выставлено в контексте запроса, и о
     * пользователе ничего не знает. Поэтому проверяем здесь именно проверку
     * доступа — подставить чужой идентификатор в обход сессии из браузера
     * невозможно, он больше не передаётся ни параметром, ни заголовком.
     */
    @Test
    void foreignDepositorRejectedOnSelect() {
        String foreignId = depositorId(OTHER_USER, OTHER_DEPOSITOR);
        assertFalse(auth.mayAccess(USER, foreignId),
                "клиенту «Рога и Копыта» недоступны «Молочные реки»");
        assertTrue(auth.mayAccess(OTHER_USER, foreignId), "а их собственному пользователю — доступны");
    }

    /**
     * Детализация разворачивает позицию по партиям.
     *
     * <p>«Не меньше двух» вместо «ровно две»: смысл проверки в том, что
     * партии не слиплись в одну строку. Если на этот товар появится третья
     * партия, проверка от этого не станет хуже.
     */
    @Test
    void batchDetails() {
        signIn();
        List<Map<String, Object>> batches = stock.details("RK-ROG-01", null);
        assertTrue(batches.size() >= 2, "позиция разложена по партиям, а не слита в одну строку");
        assertTrue(column(batches, "batchNumber").contains("П-2609-А"),
                "демо-партия П-2609-А на месте");
    }

    // ---------- Документы ----------

    /**
     * Списки отдают документы поклажедателя.
     * <p>
     * Проверяем наличие демо-документов, а не их точное число: в базу
     * разработки добавляются черновики — и руками через портал, и тестами
     * черновиков. Тест, падающий от каждого созданного документа, ловит
     * не ошибку, а факт работы с порталом.
     */
    @Test
    void documentLists() {
        signIn();
        assertTrue(documentNumbers(DocumentType.RECEIPTS).containsAll(
                        List.of("ПН-000148", "ПН-000151", "ПН-000152")),
                "в списке поступлений есть демо-документы");
        assertTrue(documentNumbers(DocumentType.ORDERS).containsAll(
                        List.of("ЗО-000322", "ЗО-000329", "ЗО-000331")),
                "в списке заказов есть демо-документы");
        assertFalse(documentNumbers(DocumentType.TRANSFERS).isEmpty(), "перемещения отдаются");
    }

    private List<String> documentNumbers(DocumentType type) {
        return documents.list(currentUser(), type, Map.of()).stream()
                .map(r -> String.valueOf(r.get("documentNo")))
                .toList();
    }

    /**
     * Фильтры списка документов.
     *
     * <p>Точных чисел здесь тоже нет: черновики в базе разработки
     * появляются и из портала, и из тестов черновиков. Проверяется, что
     * фильтр находит нужный документ и сужает список.
     */
    @Test
    void documentFilters() {
        PortalUser user = signedInUser();
        List<Map<String, Object>> all = documents.list(user, DocumentType.RECEIPTS, Map.of());

        List<Map<String, Object>> received = documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.status", "received"));
        assertTrue(column(received, "documentNo").contains(RECEIPT_NO),
                "принятое поступление " + RECEIPT_NO + " попадает под фильтр по статусу");
        assertTrue(received.size() <= all.size(), "фильтр по статусу не расширяет список");

        // Поставщик — такой же фильтр по колонке, как остальные.
        // Здесь был общий поиск по номерам и контрагенту одной строкой;
        // поле убрано с панели, и проверять нечего, кроме отбора
        // по названной колонке.
        List<Map<String, Object>> bySupplier = documents.list(user, DocumentType.RECEIPTS,
                Map.of("filter.supplier", "козлов"));
        assertFalse(bySupplier.isEmpty(), "отбор по поставщику находит");
        assertTrue(bySupplier.size() <= all.size(), "отбор не расширяет список");
    }

    /** Вход и ссылка на вошедшего: фильтры читаются от его имени. */
    private PortalUser signedInUser() {
        signIn();
        return currentUser();
    }

    /** Ссылка на основного пользователя, без повторного входа. */
    private PortalUser currentUser() {
        return authService.authenticate(USER, USER_PASSWORD);
    }

    @Test
    void foreignDocumentsHidden() {
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        // Список читается глазами ВОШЕДШЕГО: подставить сюда основного
        // пользователя значило бы проверить не ту изоляцию.
        PortalUser other = authService.authenticate(OTHER_USER, USER_PASSWORD);
        assertEquals(0, documents.list(other, DocumentType.RECEIPTS, Map.of()).size());
    }

    /**
     * Ключевая проверка решения Р1: строка 1 принята двумя партиями, в составе
     * документа это одна строка (план 1000, факт 980), в детализации — две записи.
     */
    @Test
    void documentLinesAggregateBatches() {
        signIn();
        String docId = receiptId();

        List<Map<String, Object>> lines = documents.lines(DocumentType.RECEIPTS, docId);
        assertEquals(2, lines.size());
        Map<String, Object> line1 = lines.getFirst();
        assertEquals(1000, asLong(line1.get("expectedQty")));
        assertEquals(980, asLong(line1.get("acceptedQty")));
        assertEquals(2, asLong(line1.get("batchCount")));

        assertEquals(2, documents.lineDetails(DocumentType.RECEIPTS, docId, 1).size());
        assertEquals(3, documents.lineDetails(DocumentType.RECEIPTS, docId, null).size());
    }

    @Test
    void documentCard() {
        signIn();
        Map<String, Object> card = documents.card(DocumentType.RECEIPTS, receiptId());
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals(RECEIPT_NO, header.get("documentNo"));
        // Числа относятся к одному документу, а не ко всей базе: сломаются
        // только если править именно ПН-000148 — и тогда правильно сломаются.
        assertEquals(2, ((List<?>) card.get("lines")).size(),
                "строк в " + RECEIPT_NO);
        assertEquals(3, ((List<?>) card.get("lineDetails")).size(),
                "записей детализации в " + RECEIPT_NO);
    }

    /**
     * Чужой документ неотличим от несуществующего — намеренно: иначе по коду
     * ответа можно было бы перебором узнать, какие документы есть у других.
     */
    @Test
    void foreignDocumentNotFound() {
        signIn();
        String docId = receiptId();
        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        expectRejection(404, () -> documents.card(DocumentType.RECEIPTS, docId));
    }

    // ---------- Справочники ----------

    /**
     * Номенклатура: демо-позиции на месте, фильтры работают, чужого не видно.
     *
     * <p>Именно этот тест падал на числе 8 после того, как в справочнике
     * появился товар, заведённый руками при проверке загрузки из Excel.
     * Падение не означало ошибки — проверка сравнивала состав базы, а не
     * поведение портала. Теперь проверяется поведение.
     */
    @Test
    void skuAndFilters() {
        signIn();
        List<Map<String, Object>> all = catalog.list(CatalogType.SKU, null, null, null, false);
        assertTrue(column(all, "code").containsAll(List.of("RK-ROG-01", "RK-ROG-02", "RK-MYA-01")),
                "демо-позиции в справочнике");

        List<Map<String, Object>> found = catalog.list(CatalogType.SKU, "рога", null, null, false);
        assertTrue(found.size() < all.size(), "поиск сужает список");
        assertTrue(column(found, "code").containsAll(List.of("RK-ROG-01", "RK-ROG-02")),
                "поиск находит то, что искали");

        // Фильтр по группе сравнивает значение точно, поэтому и проверка
        // точная: в выдаче не должно быть ни одной позиции другой группы.
        List<Map<String, Object>> meat =
                catalog.list(CatalogType.SKU, null, "Мясная продукция", null, false);
        assertFalse(meat.isEmpty(), "фильтр по группе находит");
        assertTrue(meat.stream().allMatch(r -> "Мясная продукция".equals(r.get("itemGroupName"))),
                "в выдаче только запрошенная группа");

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        List<String> theirs = column(catalog.list(CatalogType.SKU, null, null, null, false), "code");
        assertFalse(theirs.isEmpty(), "у другого клиента своя номенклатура");
        assertTrue(Collections.disjoint(column(all, "code"), theirs),
                "и она не пересекается с нашей");
    }

    /**
     * Остальные справочники и счета.
     *
     * <p>Шесть точных чисел заменены на «отдаётся и не пусто»: сколько в
     * базе разработки поставщиков или тарифов — не свойство портала.
     * А вот ноль у другого поклажедателя остаётся: это не снимок данных,
     * а правило изоляции.
     */
    @Test
    void catalogsAndBilling() {
        signIn();
        assertFalse(catalog.list(CatalogType.SUPPLIERS, null, null, null, false).isEmpty(),
                "поставщики отдаются");
        assertFalse(catalog.list(CatalogType.CONSIGNEES, null, null, null, false).isEmpty(),
                "грузополучатели отдаются");
        assertFalse(catalog.list(CatalogType.WAREHOUSES, null, null, null, false).isEmpty(),
                "склады отдаются");
        assertFalse(catalog.list(CatalogType.SERVICE_TARIFFS, null, null, null, false).isEmpty(),
                "тарифы отдаются");

        List<Map<String, Object>> invoices = catalog.list(CatalogType.INVOICES, null, null, null, false);
        assertFalse(invoices.isEmpty(), "счета отдаются");
        assertNotNull(invoices.getFirst().get("amountWithVat"), "сумма с НДС рассчитана в представлении");

        signInAs(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(0, catalog.list(CatalogType.INVOICES, null, null, null, false).size(),
                "своих счетов у второго поклажедателя нет, а чужие ему не видны");
    }

    // ---------- вспомогательное ----------

    /** Поступление из демо-данных, на котором проверяются карточка и состав. */
    private static final String RECEIPT_NO = "ПН-000148";

    private String receiptId() {
        return documents.list(currentUser(), DocumentType.RECEIPTS,
                        Map.of("filter.no", RECEIPT_NO))
                .getFirst().get("docId").toString();
    }

    /** Значения одной колонки списка — для проверок «такие строки есть». */
    private static List<String> column(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> String.valueOf(r.get(key))).toList();
    }

    /** Коды позиций в запасах текущего поклажедателя. */
    private Set<String> skuCodesInStock() {
        return stock.list(null, null).stream()
                .map(StockLine::skuCode)
                .collect(Collectors.toSet());
    }

    /** Вхождение без учёта регистра — так же, как ILIKE в запросе. */
    private static boolean contains(String value, String part) {
        return value != null && value.toLowerCase().contains(part.toLowerCase());
    }

    private static long asLong(Object v) {
        return v == null ? 0 : (long) Double.parseDouble(v.toString());
    }
}
