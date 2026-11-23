package com.ahlers.portal3pl.integration;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.documents.WmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сверка остатков с WMS (V143, план «Кэш остатков», К5).
 *
 * <p>По каждой паре «клиент с кодом WMS × склад с адресом WMS» портал
 * берёт у WMS срез остатков и отдаёт его базе: та сравнивает срез с тем,
 * что насчитал портал, пишет расхождения в журнал и подменяет наличие
 * одним действием ({@code stock_apply_snapshot}). Портал на время сверки
 * не блокируется (решение заказчика 14.11.2026).
 *
 * <p>WMS спрашивают вне транзакции и по очереди: сбой одной пары не
 * мешает остальным и попадает в журнал. Запрос к WMS — в
 * {@link WmsGateway#stockSnapshot}; пока там заглушка, она данных не даёт.
 */
@Service
public class StockSyncService {

    private static final Logger log = LoggerFactory.getLogger(StockSyncService.class);

    private final JdbcClient jdbc;
    private final WmsGateway wms;
    private final AdminUserService users;
    private final TransactionTemplate tx;
    private final JsonMapper json = JsonMapper.builder().build();

    public StockSyncService(JdbcClient jdbc, WmsGateway wms, AdminUserService users,
                            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.wms = wms;
        this.users = users;
        this.tx = new TransactionTemplate(txManager);
    }

    /** «Сверить сейчас» — кнопка администратора. */
    public Map<String, Object> runNow(PortalUser actor) {
        users.requireAdmin(actor);
        if (targets().isEmpty()) {
            throw Fail.conflict("error.stock_sync.no_targets");
        }
        return runAll("manual");
    }

    /** Раз в минуту: пора ли ночной сверке (включена, время настало, сегодня ещё не шла). */
    @Scheduled(fixedDelayString = "${portal.stock-sync.check-ms:60000}",
               initialDelayString = "${portal.stock-sync.check-ms:60000}")
    public void runIfDue() {
        Boolean due = tx.execute(s -> jdbc.sql("SELECT dbo.stock_sync_due()").query(Boolean.class).single());
        if (due && !targets().isEmpty()) {
            Map<String, Object> result = runAll("schedule");
            log.info("Ночная сверка остатков: пар {}, не удалось {}", result.get("pairs"), result.get("failed"));
        }
    }

    /** Сверить все пары; возвращает, сколько их было и сколько не удалось. */
    Map<String, Object> runAll(String trigger) {
        int failed = 0;
        List<Map<String, Object>> targets = targets();
        for (Map<String, Object> t : targets) {
            String depositorId = String.valueOf(t.get("depositor_id"));
            String warehouseId = (String) t.get("warehouse_id");
            try {
                List<WmsGateway.StockRow> rows = wms.stockSnapshot((String) t.get("base_url"),
                        (String) t.get("depositor_wms_code"));
                if (rows == null) {
                    // в журнал — ключ: экран покажет его на языке того, кто смотрит
                    fail(depositorId, warehouseId, trigger, "error.stock_sync.no_data");
                    failed++;
                    continue;
                }
                List<Map<String, Object>> body = new ArrayList<>();
                for (WmsGateway.StockRow r : rows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sku", r.skuCode());
                    row.put("quality", r.wmsQualityCode());
                    row.put("qty", r.qty());
                    body.add(row);
                }
                String payload = json.writeValueAsString(body);
                tx.executeWithoutResult(s -> jdbc.sql(
                                "SELECT dbo.stock_apply_snapshot(:d::uuid, :w, :rows::jsonb, :t)")
                        .param("d", depositorId).param("w", warehouseId).param("rows", payload).param("t", trigger)
                        .query(rs -> { }));
            } catch (RuntimeException e) {
                log.warn("Сверка остатков {} / {} не удалась: {}", t.get("depositor_wms_code"), warehouseId, e.toString());
                fail(depositorId, warehouseId, trigger, String.valueOf(e.getMessage()));
                failed++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pairs", targets.size());
        out.put("failed", failed);
        return out;
    }

    public List<Map<String, Object>> log(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("""
                SELECT sync_run_id::text AS sync_run_id, started_at, finished_at, trigger_source,
                       depositor_code, depositor_name, warehouse_id, status_code, status_name,
                       rows_received, rows_applied, diff_count, error_message
                  FROM dbo.admin_stock_sync_log(100)
                """).query().listOfRows());
    }

    public List<Map<String, Object>> diff(PortalUser actor, String runId) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_stock_sync_diff(:id::uuid)")
                .param("id", runId).query().listOfRows());
    }

    private List<Map<String, Object>> targets() {
        List<Map<String, Object>> rows = tx.execute(s -> jdbc.sql(
                "SELECT depositor_id::text AS depositor_id, depositor_wms_code, warehouse_id, base_url"
                        + " FROM dbo.stock_sync_targets()").query().listOfRows());
        return rows;
    }

    private void fail(String depositorId, String warehouseId, String trigger, String error) {
        tx.executeWithoutResult(s -> jdbc.sql("SELECT dbo.stock_sync_failed(:d::uuid, :w, :t, :e)")
                .param("d", depositorId).param("w", warehouseId).param("t", trigger).param("e", error)
                .query(rs -> { }));
    }
}
