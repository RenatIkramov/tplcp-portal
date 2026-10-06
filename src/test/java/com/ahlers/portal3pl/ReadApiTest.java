package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.stock.StockLine;
import com.ahlers.portal3pl.stock.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void запасыПоклажедателя() {
        войти();
        List<StockLine> rows = stock.list(null, null);
        assertEquals(9, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "RK-ROG-01".equals(r.skuCode())));
    }

    @Test
    void фильтрыЗапасов() {
        войти();
        assertEquals(8, stock.list("WH-ALA", null).size(), "только склад Алматы");
        assertEquals(3, stock.list(null, "рога").size(), "поиск по наименованию");
    }

    @Test
    void разныеПоклажедателиВидятРазныеДанные() {
        войти();
        int своих = stock.list(null, null).size();
        войтиКак(OTHER_USER, OTHER_DEPOSITOR);
        int чужих = stock.list(null, null).size();
        assertEquals(9, своих);
        assertEquals(4, чужих);
    }

    /** §6.4: вошёл, но поклажедателя не выбрал — данные не отдаются (409). */
    @Test
    void безПоклажедателяДанныеНеОтдаются() {
        войтиКак(USER, null);
        ожидатьОтказ(409, () -> stock.list(null, null));
    }

    /** А без входа вообще — 401, независимо от поклажедателя. */
    @Test
    void безВходаДанныеНеОтдаются() {
        ожидатьОтказ(401, () -> stock.list(null, null));
    }

    /**
     * Чужой поклажедатель отсекается при ВЫБОРЕ, в сессии, а не на чтении:
     * политика RLS отдаёт то, что выставлено в контексте запроса, и о
     * пользователе ничего не знает. Поэтому проверяем здесь именно проверку
     * доступа — подставить чужой идентификатор в обход сессии из браузера
     * невозможно, он больше не передаётся ни параметром, ни заголовком.
     */
    @Test
    void чужойПоклажедательНедоступенПриВыборе() {
        String чужой = поклажедатель(OTHER_USER, OTHER_DEPOSITOR);
        assertFalse(auth.mayAccess(USER, чужой),
                "клиенту «Рога и Копыта» недоступны «Молочные реки»");
        assertTrue(auth.mayAccess(OTHER_USER, чужой), "а их собственному пользователю — доступны");
    }

    @Test
    void детализацияПоПартиям() {
        войти();
        List<Map<String, Object>> batches = stock.details("RK-ROG-01", null);
        assertEquals(2, batches.size());
        assertEquals("П-2609-А", batches.get(0).get("batchNumber"));
    }

    // ---------- Документы ----------

    /**
     * Списки отдают документы поклажедателя.
     *
     * Проверяем наличие демо-документов, а не их точное число: в базу
     * разработки добавляются черновики — и руками через портал, и тестами
     * черновиков. Тест, падающий от каждого созданного документа, ловит
     * не ошибку, а факт работы с порталом.
     */
    @Test
    void спискиДокументов() {
        войти();
        assertTrue(номера(DocumentType.RECEIPTS).containsAll(
                        List.of("ПН-000148", "ПН-000151", "ПН-000152")),
                "в списке поступлений есть демо-документы");
        assertTrue(номера(DocumentType.ORDERS).containsAll(
                        List.of("ЗО-000322", "ЗО-000329", "ЗО-000331")),
                "в списке заказов есть демо-документы");
        assertFalse(номера(DocumentType.TRANSFERS).isEmpty(), "перемещения отдаются");
    }

    private List<String> номера(DocumentType type) {
        return documents.list(type, null, null, null, null, null).stream()
                .map(r -> String.valueOf(r.get("documentNo")))
                .toList();
    }

    @Test
    void фильтрыДокументов() {
        войти();
        List<Map<String, Object>> принятые =
                documents.list(DocumentType.RECEIPTS, null, "received", null, null, null);
        assertEquals(1, принятые.size());
        assertEquals("ПН-000148", принятые.get(0).get("documentNo"));

        assertEquals(1, documents.list(DocumentType.RECEIPTS, "козлов", null, null, null, null).size());
    }

    @Test
    void чужихДокументовНеВидно() {
        войтиКак(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(0, documents.list(DocumentType.RECEIPTS, null, null, null, null, null).size());
    }

    /**
     * Ключевая проверка решения Р1: строка 1 принята двумя партиями, в составе
     * документа это одна строка (план 1000, факт 980), в детализации — две записи.
     */
    @Test
    void составДокументаАгрегируетПартии() {
        войти();
        String docId = приход("ПН-000148");

        List<Map<String, Object>> lines = documents.lines(DocumentType.RECEIPTS, docId);
        assertEquals(2, lines.size());
        Map<String, Object> строка1 = lines.get(0);
        assertEquals(1000, число(строка1.get("expectedQty")));
        assertEquals(980, число(строка1.get("acceptedQty")));
        assertEquals(2, число(строка1.get("batchCount")));

        assertEquals(2, documents.lineDetails(DocumentType.RECEIPTS, docId, 1).size());
        assertEquals(3, documents.lineDetails(DocumentType.RECEIPTS, docId, null).size());
    }

    @Test
    void карточкаДокумента() {
        войти();
        Map<String, Object> card = documents.card(DocumentType.RECEIPTS, приход("ПН-000148"));
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals("ПН-000148", header.get("documentNo"));
        assertEquals(2, ((List<?>) card.get("lines")).size());
        assertEquals(3, ((List<?>) card.get("lineDetails")).size());
    }

    /**
     * Чужой документ неотличим от несуществующего — намеренно: иначе по коду
     * ответа можно было бы перебором узнать, какие документы есть у других.
     */
    @Test
    void чужойДокументНеОткрывается() {
        войти();
        String docId = приход("ПН-000148");
        войтиКак(OTHER_USER, OTHER_DEPOSITOR);
        ожидатьОтказ(404, () -> documents.card(DocumentType.RECEIPTS, docId));
    }

    // ---------- Справочники ----------

    @Test
    void номенклатураИФильтры() {
        войти();
        assertEquals(8, catalog.list(CatalogType.SKU, null, null, null).size());
        assertEquals(3, catalog.list(CatalogType.SKU, "рога", null, null).size());
        assertEquals(2, catalog.list(CatalogType.SKU, null, "Мясная продукция", null).size());

        войтиКак(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(4, catalog.list(CatalogType.SKU, null, null, null).size());
    }

    @Test
    void справочникиИБиллинг() {
        войти();
        assertEquals(2, catalog.list(CatalogType.SUPPLIERS, null, null, null).size());
        assertEquals(3, catalog.list(CatalogType.CONSIGNEES, null, null, null).size());
        assertEquals(2, catalog.list(CatalogType.WAREHOUSES, null, null, null).size());
        assertEquals(7, catalog.list(CatalogType.SERVICE_TARIFFS, null, null, null).size());

        List<Map<String, Object>> счета = catalog.list(CatalogType.INVOICES, null, null, null);
        assertEquals(4, счета.size());
        assertFalse(счета.get(0).get("amountWithVat") == null, "сумма с НДС рассчитана в представлении");

        войтиКак(OTHER_USER, OTHER_DEPOSITOR);
        assertEquals(0, catalog.list(CatalogType.INVOICES, null, null, null).size());
    }

    // ---------- вспомогательное ----------

    private String приход(String documentNo) {
        return documents.list(DocumentType.RECEIPTS, documentNo, null, null, null, null)
                .get(0).get("docId").toString();
    }

    private static long число(Object v) {
        return v == null ? 0 : (long) Double.parseDouble(v.toString());
    }
}
