package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.MsgRef;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.documents.DocumentService;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.DraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Черновики документов: создание, правка, подтверждение (этап 7, решение Р5).
 * <p>
 * Тесты создают документы в базе и убирают их за собой, чтобы не ломать
 * проверки, которые опираются на количество демо-документов.
 */
class DraftTest extends PortalTestBase {

    @Autowired
    DraftService drafts;

    @Autowired
    DocumentService documents;

    @Autowired
    ColumnService columnService;

    private final List<String> created = new ArrayList<>();
    private final List<String> createdOrders = new ArrayList<>();

    @AfterEach
    void removeCreated() {
        for (String docId : created) {
            inContext("DELETE FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.receipts WHERE doc_id = '" + docId + "'::uuid");
        }
        for (String docId : createdOrders) {
            inContext("DELETE FROM dbo.order_details WHERE doc_id = '" + docId + "'::uuid");
            inContext("DELETE FROM dbo.orders WHERE doc_id = '" + docId + "'::uuid");
        }
        created.clear();
        createdOrders.clear();
    }

    private Map<String, Object> draft(Map<String, Object> header) {
        Map<String, Object> card = drafts.create(DocumentType.RECEIPTS, withErp(header));
        created.add(header(card).get("docId").toString());
        return card;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> header(Map<String, Object> card) {
        return (Map<String, Object>) card.get("header");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> card) {
        return (List<Map<String, Object>>) card.get("lines");
    }

    @SuppressWarnings("unchecked")
    private static List<String> problems(Map<String, Object> card) {
        return (List<String>) card.get("problems");
    }

    @Test
    void draftCreatedWithNumberAndNoLines() {
        signIn();
        Map<String, Object> card = draft(Map.of());
        Map<String, Object> h = header(card);

        assertTrue(h.get("documentNo").toString().startsWith("R"), "портал присваивает свой номер");
        assertEquals("01", h.get("statusCode"));
        assertEquals("01", h.get("origin"), "владелец черновика — портал");
        assertEquals(0, rows(card).size());
        assertFalse(problems(card).isEmpty(), "пустой черновик отправить нельзя");
    }

    /* ---------------- Номер ERP (V98) ---------------- */

    /** Без номера ERP черновик не сохраняется — ни новый, ни правкой. */
    @Test
    void erpNumberIsRequired() {
        signIn();
        expectRejection(400, () -> drafts.create(DocumentType.RECEIPTS, Map.of()));
        expectRejection(400, () -> drafts.create(DocumentType.RECEIPTS, Map.of("erp_number", "  ")));
        String docId = header(draft(Map.of())).get("docId").toString();
        expectRejection(400, () -> drafts.updateHeader(DocumentType.RECEIPTS, docId,
                Map.of("erp_number", "")));
        // Шапка без номера — правка других полей, номер не стирается
        Map<String, Object> card = drafts.updateHeader(DocumentType.RECEIPTS, docId,
                Map.of("comment", "без номера в запросе"));
        assertNotNull(header(card).get("erpNumber"));
    }

    /** Номер ERP не повторяется у поклажедателя в пределах типа документа. */
    @Test
    void erpNumberIsUniquePerDepositorAndType() {
        signIn();
        String erp = uniqueErp();
        String first = header(draft(Map.of("erp_number", erp))).get("docId").toString();
        // Регистр и пробелы по краям не различаются
        expectRejection(409, () -> draft(Map.of("erp_number", " " + erp.toLowerCase() + " ")));
        String second = header(draft(Map.of())).get("docId").toString();
        expectRejection(409, () -> drafts.updateHeader(DocumentType.RECEIPTS, second,
                Map.of("erp_number", erp)));
        // Свой же номер при сохранении — не повтор
        drafts.updateHeader(DocumentType.RECEIPTS, first, Map.of("erp_number", erp));
        // У заказа тот же номер можно: нумерация типов в ERP своя
        String order = header(drafts.create(DocumentType.ORDERS, Map.of("erp_number", erp)))
                .get("docId").toString();
        createdOrders.add(order);
    }

    /**
     * Склад у человека один — новый черновик получает его сам. Склады
     * выдаются человеку (V110); как было, вернёт PersonalSettings.
     */
    @Test
    void theOnlyWarehouseIsFilledIn() {
        int active = jdbc.sql("SELECT count(*)::int FROM dbo.warehouse WHERE is_active")
                .query(Integer.class).single();
        assertTrue(active > 1, "для проверки в портале должно быть несколько складов");
        String user = userId(USER);
        jdbc.sql("UPDATE dbo.app_user SET all_warehouses = false WHERE user_id = :id::uuid")
                .param("id", user).update();
        jdbc.sql("DELETE FROM dbo.user_warehouse_access WHERE user_id = :id::uuid")
                .param("id", user).update();
        jdbc.sql("INSERT INTO dbo.user_warehouse_access (user_id, warehouse_id) VALUES (:id::uuid, 'WH-ALA')")
                .param("id", user).update();
        signIn();
        assertEquals("WH-ALA", header(draft(Map.of())).get("warehouseId"));

        jdbc.sql("UPDATE dbo.app_user SET all_warehouses = true WHERE user_id = :id::uuid")
                .param("id", user).update();
        signIn();
        assertNull(header(draft(Map.of())).get("warehouseId"), "складов несколько — человек выбирает сам");
    }

    /**
     * Доп. поле, закрытое поклажедателю, черновик не записывает, даже
     * если запрос его прислал; открытое — записывает (03.11.2026).
     */
    @Test
    void closedCustomFieldIsNotSaved() {
        var admin = portalAdmin();
        String dep = depositorId(USER, DEPOSITOR);
        Runnable restore = keepDepositorSetup(DEPOSITOR);
        columnService.saveDepositorColumn(admin, dep, "receipts", "customField1", true, false);
        columnService.saveDepositorColumn(admin, dep, "receipts", "customField2", false, false);
        try {
            signIn();
            Map<String, Object> card = draft(Map.of("custom_field_1", "открыто", "custom_field_2", "закрыто"));
            String docId = header(card).get("docId").toString();
            assertEquals("открыто", singleValue("SELECT custom_field_1 FROM dbo.receipts WHERE doc_id = '"
                    + docId + "'::uuid"));
            assertEquals("(пусто)", singleValue("SELECT COALESCE(custom_field_2, '(пусто)') FROM dbo.receipts"
                    + " WHERE doc_id = '" + docId + "'::uuid"), "закрытое доп. поле записалось");
        } finally {
            restore.run();
        }
    }

    @Test
    void headerAndLinesEditable() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();

        Map<String, Object> card = drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of(
                "warehouse_id", "WH-ALA",
                "eta_date", "2026-11-01",
                "erp_number", "ERP-TEST-77-" + docId.substring(0, 8),
                "comment", "тестовый черновик"));
        assertEquals("ERP-TEST-77-" + docId.substring(0, 8), header(card).get("erpNumber"));
        assertEquals("Склад Алматы", header(card).get("warehouseName"));

        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        card = drafts.addLine(DocumentType.RECEIPTS, docId, Map.of(
                "sku_id", skuId, "expected_qty", 120, "price", 2500, "currency_id", 1));
        assertEquals(1, rows(card).size());
        assertEquals("RK-ROG-01", rows(card).getFirst().get("skuCode"));

        card = drafts.updateLine(DocumentType.RECEIPTS, docId, 1, Map.of("expected_qty", 150));
        assertEquals(150, (long) Double.parseDouble(rows(card).getFirst().get("expectedQty").toString()));

        card = drafts.deleteLines(DocumentType.RECEIPTS, docId, List.of(1));
        assertEquals(0, rows(card).size());
    }

    @Test
    void quantityMustBePositive() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");

        expectRejection(400, () -> drafts.addLine(DocumentType.RECEIPTS, docId,
                Map.of("sku_id", skuId, "expected_qty", 0)));
    }

    @Test
    void cannotConfirmWithoutRequiredFields() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();

        List<MsgRef> problems = drafts.validateForConfirm(DocumentType.RECEIPTS, docId);
        assertFalse(problems.isEmpty());
        expectRejection(422, () -> drafts.confirm(DocumentType.RECEIPTS, docId));
    }

    @Test
    void filledDraftConfirmedAndLocked() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String supplierId = singleValue(
                "SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");

        String docId = header(draft(Map.of(
                "warehouse_id", "WH-ALA", "eta_date", "2026-11-05"))).get("docId").toString();
        drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of("supplier_id", supplierId));
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", skuId, "expected_qty", 80));

        assertTrue(drafts.validateForConfirm(DocumentType.RECEIPTS, docId).isEmpty(),
                "все условия выполнены");

        Map<String, Object> card = drafts.confirm(DocumentType.RECEIPTS, docId);
        assertEquals("02", header(card).get("statusCode"));

        // после отправки документ из портала не редактируется (Р5)
        expectRejection(409, () -> drafts.updateHeader(DocumentType.RECEIPTS, docId,
                Map.of("comment", "поздняя правка")));
        expectRejection(409, () -> drafts.delete(DocumentType.RECEIPTS, docId));
    }

    @Test
    void skuUnknownToWmsBlocksConfirm() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-KOP-01'");
        String supplierId = singleValue(
                "SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");

        inContext("UPDATE dbo.sku SET is_in_wms = false WHERE code = 'RK-KOP-01'");
        try {
            String docId = header(draft(Map.of(
                    "warehouse_id", "WH-ALA", "eta_date", "2026-11-07"))).get("docId").toString();
            drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of("supplier_id", supplierId));
            Map<String, Object> card = drafts.addLine(DocumentType.RECEIPTS, docId,
                    Map.of("sku_id", skuId, "expected_qty", 50));

            assertEquals(Boolean.TRUE, header(card).get("hasUnknownSku"));
            assertTrue(drafts.validateForConfirm(DocumentType.RECEIPTS, docId).stream()
                            .anyMatch(p -> "error.ready.unknown_sku".equals(p.key())),
                    "причина должна называться ключом, а не текстом: "
                            + drafts.validateForConfirm(DocumentType.RECEIPTS, docId));
            expectRejection(422, () -> drafts.confirm(DocumentType.RECEIPTS, docId));
        } finally {
            inContext("UPDATE dbo.sku SET is_in_wms = true WHERE code = 'RK-KOP-01'");
        }
    }

    /**
     * Списки для шапки документа отдаются и состоят из ключей.
     *
     * <p>До этого их в портале не было вовсе: карточка предлагала
     * наименования из демо-массива прототипа, и выбранное наименование
     * уходило в колонку ключа — сохранение падало ошибкой приведения
     * типа. Проверяется поэтому не только «список не пуст», но и «ключ
     * — число»: список из наименований такую проверку не прошёл бы.
     */
    @Test
    void documentDictionariesAreKeyed() {
        signIn();
        Map<String, Object> all = documents.dictionaries();

        for (String kind : List.of("orderTypes", "receiptDocTypes")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) all.get(kind);
            assertNotNull(rows, kind + " не отдаётся вовсе");
            assertFalse(rows.isEmpty(), kind + ": список пуст, в карточке нечего выбрать");
            for (Map<String, Object> row : rows) {
                assertNotNull(row.get("id"), kind + ": у значения нет ключа");
                assertInstanceOf(Number.class, row.get("id"),
                        kind + ": ключ не число — в карточку уйдёт наименование");
                assertNotNull(row.get("name"), kind + ": у значения нет наименования");
            }
        }
    }

    /**
     * Дата уходит на экран календарной датой, а не мгновением времени.
     *
     * <p>Проверка появилась после настоящей ошибки, и она не про формат,
     * а про сутки. Драйвер отдаёт колонку {@code date} как
     * {@code java.sql.Date} — момент времени с нулевым временем, — и
     * в JSON он превращался в мгновение по UTC. В Алматы (UTC+5)
     * «1 декабря» приезжало на экран как «30 ноября»: ровно на разницу
     * с UTC назад.
     *
     * <p>Поэтому утверждается именно строка, а не «дата та же»: тип
     * {@code java.sql.Date} эту проверку не прошёл бы, хотя его
     * {@code toString()} даёт тот же текст. Сдвиг возникал не при
     * чтении, а при выводе в JSON, и закрыть его можно только тем,
     * что из репозитория уходит уже строка.
     */
    @Test
    void dateStaysTheSameDay() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();

        drafts.updateHeader(DocumentType.RECEIPTS, docId, Map.of("eta_date", "2026-12-01"));
        Object eta = header(drafts.card(DocumentType.RECEIPTS, docId)).get("etaDate");

        assertEquals("2026-12-01", eta,
                "дата уехала или пришла мгновением времени: " + eta
                + " (" + (eta == null ? "null" : eta.getClass().getName()) + ")");
    }

    /**
     * Номер документа по правилам V65: [тип][YYMMDD][счётчик].
     *
     * <p>Проверяется ВИД номера и день в нём, а не само число: счётчик
     * общий на тип и день, он растёт от любого документа — и своего,
     * и чужого, — поэтому ждать «001» значит ждать, что в этот день
     * никто больше ничего не создал.
     *
     * <p>День сверяется с датой документа. Расхождение здесь никто
     * не заметит, пока кто-нибудь не станет искать документ по номеру
     * за «тот день»: номер и дата обязаны говорить об одном дне.
     */
    @Test
    void numberFollowsTheRules() {
        signIn();
        String number = header(draft(Map.of())).get("documentNo").toString();

        String today = singleValue("SELECT to_char(current_date, 'YYMMDD')");
        assertTrue(number.matches("^R" + today + "[0-9]{3,}$"),
                "номер не по правилам ([тип][YYMMDD][счётчик]): " + number);
    }

    /**
     * Номера не повторяются, и счётчик идёт вперёд.
     *
     * <p>Уникальность подпёрта и базой — на document_no стоит уникальный
     * индекс, — но проверка нужна именно на выдачу: индекс сообщит
     * о дубле отказом сохранения, то есть человеку, а не нам.
     *
     * <p>Сравниваются хвосты как числа, а не строки: на тысячном
     * документе за день счётчик становится четырёхзначным, и «1000»
     * как строка меньше, чем «999».
     */
    @Test
    void numbersGrowAndDoNotRepeat() {
        signIn();
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            numbers.add(header(draft(Map.of())).get("documentNo").toString());
        }

        assertEquals(numbers.size(), Set.copyOf(numbers).size(), "номера повторяются: " + numbers);
        for (int i = 1; i < numbers.size(); i++) {
            assertTrue(tail(numbers.get(i)) > tail(numbers.get(i - 1)),
                    "счётчик не идёт вперёд: " + numbers);
        }
    }

    /** Счётчик из номера: всё после буквы типа и шести знаков даты. */
    private static long tail(String number) {
        return Long.parseLong(number.substring(7));
    }

    @Test
    void draftDeleted() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        drafts.delete(DocumentType.RECEIPTS, docId);
        expectRejection(404, () -> drafts.card(DocumentType.RECEIPTS, docId));
    }

    /**
     * Черновик со строками тоже удаляется.
     *
     * <p>Проверка появилась после настоящей ошибки: внешний ключ строк
     * объявлен без каскада, и удаление шапки у документа, где есть хоть
     * одна строка, падало ошибкой целостности. Прежняя проверка удаляла
     * ПУСТОЙ черновик и потому ничего не замечала — а пустых документов
     * в работе не бывает.
     */
    @Test
    void draftWithLinesDeleted() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        drafts.addLine(DocumentType.RECEIPTS, docId,
                Map.of("sku_id", skuId, "expected_qty", 10));

        drafts.delete(DocumentType.RECEIPTS, docId);
        expectRejection(404, () -> drafts.card(DocumentType.RECEIPTS, docId));
        assertEquals("0", singleValue(
                "SELECT count(*)::text FROM dbo.receipt_details WHERE doc_id = '" + docId + "'::uuid"),
                "строки удалённого черновика остались в базе");
    }

    /* ---------------- Качество строки заказа (V85, V89) ---------------- */

    private String orderDraft() {
        Map<String, Object> card = drafts.create(DocumentType.ORDERS, withErp(Map.of()));
        String docId = header(card).get("docId").toString();
        createdOrders.add(docId);
        return docId;
    }

    /**
     * Качество строки — код перечисления (V89). Пусто — «Годный», и он
     * пишется явно; чужой код — отказ. Строки разного качества одного
     * товара — разные строки.
     */
    @Test
    void orderLineQualityIsACodeAndGoodByDefault() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String docId = orderDraft();

        drafts.replaceLines(DocumentType.ORDERS, docId, List.of(
                Map.of("sku_id", skuId, "ordered_qty", 3),
                Map.of("sku_id", skuId, "ordered_qty", 2, "quality", "02"),
                Map.of("sku_id", skuId, "ordered_qty", 1, "quality", "04")));
        List<Map<String, Object>> lines = rows(drafts.card(DocumentType.ORDERS, docId));
        assertEquals(3, lines.size(), "строки разного качества одного товара — разные строки");
        assertEquals(List.of("01", "02", "04"),
                lines.stream().map(l -> l.get("qualityCode")).toList(),
                "без выбора качество пишется явно — «Годный»");
        assertEquals("1,2,4", singleValue("SELECT string_agg(quality_id::text, ',' ORDER BY line_number)"
                + " FROM dbo.order_details WHERE doc_id = '" + docId + "'::uuid AND batch_number IS NULL"));

        // Код, а не слово: «Брак» словом больше не принимается
        expectRejection(400, () -> drafts.addLine(DocumentType.ORDERS, docId,
                Map.of("sku_id", skuId, "ordered_qty", 1, "quality", "Брак")));
        expectRejection(400, () -> drafts.addLine(DocumentType.ORDERS, docId,
                Map.of("sku_id", skuId, "ordered_qty", 1, "quality", "99")));

        // Правка строки меняет качество
        drafts.updateLine(DocumentType.ORDERS, docId, 1, Map.of("quality", "03"));
        assertEquals("03", rows(drafts.card(DocumentType.ORDERS, docId)).get(0).get("qualityCode"));
    }

    /** Имя качества — на языке пользователя, код — один на всех. */
    @Test
    void orderLineQualityNameFollowsTheUserLanguage() {
        signIn();
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String docId = orderDraft();
        drafts.replaceLines(DocumentType.ORDERS, docId, List.of(
                Map.of("sku_id", skuId, "ordered_qty", 2, "quality", "02")));
        assertEquals("Defective", rows(drafts.card(DocumentType.ORDERS, docId)).get(0).get("quality"));
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        localeOf(user, "ru");
        try {
            signIn();
            assertEquals("Брак", rows(drafts.card(DocumentType.ORDERS, docId)).get(0).get("quality"));
        } finally {
            new org.springframework.transaction.support.TransactionTemplate(txManager)
                    .executeWithoutResult(tx -> jdbc.sql("UPDATE dbo.app_user SET locale_id = NULL"
                            + " WHERE login = :login").param("login", USER).update());
        }
    }

    /**
     * Отдельная отправка транспорта — только после подтверждения: WMS
     * о черновике не знает (Р5). В черновике транспорт пишется вместе
     * с шапкой — см. {@link #transportIsSavedWithTheDraftHeader}.
     */
    @Test
    void transportNotSentSeparatelyFromDraft() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        expectRejection(409, () -> drafts.updateTransport(DocumentType.RECEIPTS, docId,
                Map.of("carrier", "ТОО «КазТранс»", "car_number", "999 ZZZ 02")));
    }

    /** С V84 транспорт заполняется в черновике и сохраняется вместе с шапкой. */
    @Test
    void transportIsSavedWithTheDraftHeader() {
        signIn();
        String docId = header(draft(Map.of("carrier", "ТОО «КазТранс»"))).get("docId").toString();
        Map<String, Object> card = drafts.updateHeader(DocumentType.RECEIPTS, docId,
                Map.of("car_number", "999 ZZZ 02", "driver_name", "Ахметов А."));
        assertEquals("ТОО «КазТранс»", header(card).get("carrier"), "транспорт при создании");
        assertEquals("999 ZZZ 02", header(card).get("carNumber"));
        assertEquals("Ахметов А.", header(card).get("driverName"));
        assertEquals(null, header(card).get("transportState"), "из черновика в WMS ничего не уходит");
    }

    /** Без права на транспорт его поля в черновике не принимаются. */
    @Test
    void transportInDraftNeedsTheRight() {
        signIn();
        String docId = header(draft(Map.of())).get("docId").toString();
        inContext("UPDATE dbo.user_settings SET can_edit_transport = false"
                + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        try {
            signIn();
            Map<String, Object> card = drafts.updateHeader(DocumentType.RECEIPTS, docId,
                    Map.of("car_number", "999 ZZZ 02", "comment", "шапка пишется"));
            assertEquals(null, header(card).get("carNumber"), "транспорт без права записался");
            assertEquals("шапка пишется", header(card).get("comment"), "остальная шапка пишется");
        } finally {
            inContext("UPDATE dbo.user_settings SET can_edit_transport = true"
                    + " WHERE user_id = (SELECT user_id FROM dbo.app_user WHERE login = '" + USER + "')");
        }
    }

    /** Подтверждение: транспорт из черновика уходит вслед за документом. */
    @Test
    void transportFromDraftGoesWithConfirmation() {
        signIn();
        String withTransport = readyDraft(Map.of("car_number", "777 TRN 02"));
        String without = readyDraft(Map.of());

        Map<String, Object> sent = header(drafts.confirm(DocumentType.RECEIPTS, withTransport));
        assertEquals("02", sent.get("statusCode"));
        assertEquals("01", sent.get("transportState"));
        assertNotNull(sent.get("transportSentAt"));

        Map<String, Object> bare = header(drafts.confirm(DocumentType.RECEIPTS, without));
        assertEquals("02", bare.get("statusCode"));
        assertEquals(null, bare.get("transportState"), "пустой транспорт не отправляется");

        expectRejection(409, () -> drafts.resendTransport(DocumentType.RECEIPTS, withTransport));
    }

    /** Черновик, который можно подтвердить, — с дополнительными полями шапки. */
    private String readyDraft(Map<String, Object> more) {
        String skuId = singleValue("SELECT sku_id::text FROM dbo.sku WHERE code = 'RK-ROG-01'");
        String supplierId = singleValue(
                "SELECT supplier_id::text FROM dbo.supplier WHERE code = 'SUP-001' LIMIT 1");
        Map<String, Object> head = new java.util.LinkedHashMap<>(more);
        head.put("warehouse_id", "WH-ALA");
        head.put("eta_date", "2026-11-05");
        head.put("supplier_id", supplierId);
        String docId = header(draft(head)).get("docId").toString();
        drafts.addLine(DocumentType.RECEIPTS, docId, Map.of("sku_id", skuId, "expected_qty", 5));
        return docId;
    }

    @Test
    void transportEditableWhenAwaiting() {
        signIn();
        String docId = singleValue(
                "SELECT doc_id::text FROM dbo.receipts WHERE document_no = 'ПН-000151'");

        Map<String, Object> card = drafts.updateTransport(DocumentType.RECEIPTS, docId,
                Map.of("carrier", "ТОО «КазТранс»", "car_number", "777 AAA 02"));
        assertEquals("777 AAA 02", header(card).get("carNumber"));
    }
}
