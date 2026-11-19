package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.integration.StockSyncService;
import com.ahlers.portal3pl.integration.WmsSettingsService;
import com.ahlers.portal3pl.integration.WmsSettingsService.Depositor;
import com.ahlers.portal3pl.integration.WmsSettingsService.Quality;
import com.ahlers.portal3pl.integration.WmsSettingsService.Settings;
import com.ahlers.portal3pl.integration.WmsSettingsService.Warehouse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Настройки интеграции и сверка остатков с WMS (V143, план «Кэш
 * остатков», К7 и К5).
 *
 * <p>WMS изображает подменённый {@link WmsGateway}. Настройки, которые
 * человек задал руками, сохраняются и возвращаются ({@link WmsSettingsKeeper}).
 * Расписание в проверках выключено: сверку зовём сами.
 */
@TestPropertySource(properties = "portal.stock-sync.check-ms=3600000")
class StockSyncTest extends PortalTestBase {

    private static final String URL = "http://qa-wms.test:13515";

    @Autowired
    WmsSettingsService settings;

    @Autowired
    StockSyncService sync;

    @MockitoSpyBean
    WmsGateway wms;

    private WmsSettingsKeeper keeper;
    private PortalUser admin;
    private String depositorId;
    private String warehouse;

    @BeforeEach
    void rememberSettings() {
        keeper = new WmsSettingsKeeper(owner());
        admin = portalAdmin();
        signIn();
        depositorId = depositorId(USER, DEPOSITOR);
        warehouse = singleValue("SELECT warehouse_id FROM dbo.v_stock_available ORDER BY available_qty DESC LIMIT 1");
        // сверяем только своего клиента и один склад — что бы ни было настроено руками
        owner().jdbc.sql("DELETE FROM dbo.wms_warehouse").update();
        owner().jdbc.sql("DELETE FROM dbo.wms_depositor").update();
    }

    @AfterEach
    void restoreSettings() {
        doCallRealMethod().when(wms).stockSnapshot(anyString(), anyString());
        keeper.restore();
    }

    private Settings with(boolean transfers, String time, String url, String code, List<Quality> qualities) {
        return new Settings(transfers, false, time,
                List.of(new Warehouse(warehouse, url)), List.of(new Depositor(depositorId, code)), qualities);
    }

    private List<Quality> standardQualities() {
        return List.of(new Quality("010", "01"), new Quality("030", "02"));
    }

    private void configure() {
        settings.save(admin, with(false, "02:30", URL, "QA-0001", standardQualities()));
    }

    /** Настройки сохраняются целиком и читаются обратно; адрес — без хвостовой черты. */
    @Test
    @SuppressWarnings("unchecked")
    void settingsAreSavedAndReadBack() {
        Map<String, Object> saved = settings.save(admin, with(true, "03:15", URL + "/", "QA-0001",
                List.of(new Quality("010", "01"), new Quality("QA-Q", "04"))));
        assertEquals(true, saved.get("countTransfers"));
        assertEquals("03:15", saved.get("syncTime"));
        assertTrue(((List<Map<String, Object>>) saved.get("warehouses")).stream().anyMatch(w ->
                warehouse.equals(w.get("warehouseId")) && URL.equals(w.get("baseUrl"))), "адрес склада не записан");
        assertTrue(((List<Map<String, Object>>) saved.get("depositors")).stream().anyMatch(d ->
                depositorId.equals(d.get("depositorId")) && "QA-0001".equals(d.get("wmsCode"))), "код клиента не записан");
        assertEquals(List.of("010", "QA-Q"), ((List<Map<String, Object>>) saved.get("qualities")).stream()
                .map(q -> String.valueOf(q.get("wmsCode"))).toList(), "коды качества — ровно присланные");

        // пустой адрес и пустой код — строки нет
        Map<String, Object> cleared = settings.save(admin, with(false, "03:15", " ", "", standardQualities()));
        assertTrue(((List<Map<String, Object>>) cleared.get("warehouses")).stream()
                .allMatch(w -> w.get("baseUrl") == null), "адрес не очищен");
    }

    /** Плохие значения — отказ с причиной; настройки видит и меняет только администратор. */
    @Test
    void badValuesAndStrangersAreRefused() {
        expectRejection(400, () -> settings.save(admin, with(false, "25:00", URL, "QA-0001", standardQualities())));
        expectRejection(400, () -> settings.save(admin, with(false, "02:30", "ftp://wms", "QA-0001", standardQualities())));
        expectRejection(400, () -> settings.save(admin, with(false, "02:30", URL, "QA 0001", standardQualities())));
        expectRejection(400, () -> settings.save(admin, with(false, "02:30", URL, "QA-0001",
                List.of(new Quality("010", "01"), new Quality("010", "02")))));
        PortalUser client = authService.authenticate(USER, USER_PASSWORD);
        expectRejection(403, () -> settings.settings(client));
        expectRejection(403, () -> sync.runNow(client));
        expectRejection(403, () -> sync.log(client));
    }

    /**
     * Сверка: наличие становится таким, как в WMS; расхождение с тем, что
     * насчитал портал, — в журнале; неизвестные товар и код качества
     * пропущены и названы; резерв пересчитан от нового наличия.
     */
    @Test
    void snapshotReplacesStockAndRecordsDifferences() {
        configure();
        String[] item = singleValue("SELECT a.sku_id::text || '|' || s.code || '|' || a.quantity::numeric(14,0)"
                + " FROM dbo.v_stock_available a JOIN dbo.sku s ON s.sku_id = a.sku_id WHERE a.warehouse_id = '"
                + warehouse + "' AND a.available_qty >= 50 AND a.blocked_qty = 0"
                + " ORDER BY a.available_qty DESC, a.sku_id LIMIT 1").split("\\|");
        BigDecimal wmsQty = new BigDecimal(item[2]).add(new BigDecimal("5"));
        doReturn(List.of(
                new WmsGateway.StockRow(item[1], "010", wmsQty),
                new WmsGateway.StockRow("QA-NO-SUCH-ITEM", "010", BigDecimal.TEN),
                new WmsGateway.StockRow(item[1], "999", BigDecimal.ONE)))
                .when(wms).stockSnapshot(eq(URL), eq("QA-0001"));

        Map<String, Object> result = sync.runNow(admin);
        assertEquals(1, result.get("pairs"));
        assertEquals(0, result.get("failed"));

        assertEquals(0, wmsQty.compareTo(new BigDecimal(singleValue("SELECT quantity::text FROM dbo.v_stock_available"
                + " WHERE sku_id = '" + item[0] + "'::uuid AND warehouse_id = '" + warehouse + "'"))),
                "наличие после сверки — не число WMS");
        assertEquals("0", singleValue("SELECT COALESCE(sum(quantity), 0)::numeric(14,0)::text FROM dbo.v_stock_available"
                + " WHERE warehouse_id = '" + warehouse + "' AND sku_id <> '" + item[0] + "'::uuid"),
                "товар, которого нет в срезе, остался в наличии");

        Map<String, Object> run = sync.log(admin).getFirst();
        assertEquals("01", run.get("statusCode"));
        assertEquals(3, ((Number) run.get("rowsReceived")).intValue());
        assertEquals(1, ((Number) run.get("rowsApplied")).intValue());
        String note = String.valueOf(run.get("errorMessage"));
        assertTrue(note.contains("QA-NO-SUCH-ITEM") && note.contains("999"), "пропущенные строки не названы: " + note);

        Map<String, Object> diff = sync.diff(admin, String.valueOf(run.get("syncRunId"))).stream()
                .filter(d -> item[1].equals(d.get("skuCode"))).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal(item[2]).compareTo(new BigDecimal(String.valueOf(diff.get("portalQty")))));
        assertEquals(0, wmsQty.compareTo(new BigDecimal(String.valueOf(diff.get("wmsQty")))));

        // вторая сверка тем же срезом расхождений не даёт
        sync.runNow(admin);
        assertEquals(0, ((Number) sync.log(admin).getFirst().get("diffCount")).intValue(),
                "повторная сверка тем же срезом нашла расхождения");
    }

    /** WMS данных не дала (заглушка) или не ответила — запись в журнале, остатки не тронуты. */
    @Test
    void noDataAndSilentWmsAreJournaledAndChangeNothing() {
        configure();
        String before = singleValue("SELECT COALESCE(sum(quantity), 0)::text FROM dbo.v_stock_available");

        assertEquals(1, sync.runNow(admin).get("failed"), "заглушка данных не даёт — сверка не состоялась");
        Map<String, Object> run = sync.log(admin).getFirst();
        assertEquals("02", run.get("statusCode"));
        assertEquals("error.stock_sync.no_data", run.get("errorMessage"));

        doThrow(new IllegalStateException("connection refused")).when(wms).stockSnapshot(anyString(), anyString());
        assertEquals(1, sync.runNow(admin).get("failed"));
        assertTrue(String.valueOf(sync.log(admin).getFirst().get("errorMessage")).contains("connection refused"));

        assertEquals(before, singleValue("SELECT COALESCE(sum(quantity), 0)::text FROM dbo.v_stock_available"),
                "несостоявшаяся сверка изменила остатки");
    }

    /** Сверять нечего — отказ с объяснением, а не пустой успех. */
    @Test
    void nothingToReconcileIsRefused() {
        expectRejection(409, () -> sync.runNow(admin));
    }

    /**
     * Ночная сверка: выключена — не идёт; включена и время настало — идёт
     * один раз в сутки. Сверки по расписанию, уже записанные сегодня,
     * на время проверки убираются в той же транзакции и возвращаются откатом.
     */
    @Test
    void nightlyRunIsDueOncePerDay() {
        owner().tx.executeWithoutResult(tx -> {
            owner().jdbc.sql("DELETE FROM dbo.sync_run WHERE entity = 'stock' AND trigger_source = 'schedule'"
                    + " AND started_at >= current_date").update();
            owner().jdbc.sql("UPDATE dbo.portal_setting SET value = '00:00' WHERE setting_key = 'stock_sync_time'").update();
            owner().jdbc.sql("UPDATE dbo.portal_setting SET value = 'false' WHERE setting_key = 'stock_sync_enabled'").update();
            assertFalse(due(), "выключенная сверка собралась идти");
            owner().jdbc.sql("UPDATE dbo.portal_setting SET value = 'true' WHERE setting_key = 'stock_sync_enabled'").update();
            assertTrue(due(), "включённая сверка не идёт, хотя время настало");
            owner().jdbc.sql("SELECT dbo.stock_sync_failed(:d::uuid, :w, 'schedule', 'qa')")
                    .param("d", depositorId).param("w", warehouse).query(rs -> { });
            assertFalse(due(), "вторая ночная сверка за сутки");
            tx.setRollbackOnly();
        });
    }

    private boolean due() {
        return Boolean.TRUE.equals(owner().jdbc.sql("SELECT dbo.stock_sync_due()").query(Boolean.class).single());
    }
}
