package com.ahlers.portal3pl;

import java.util.List;
import java.util.Map;

/**
 * Настройки интеграции (V143) на время проверки: запомнить, а после —
 * вернуть ровно как были. Их человек задаёт руками в «Интеграции», и
 * проверка не должна их стирать. Заодно убирает сверки, которые проверка
 * записала в журнал, и возвращает наличие из партий.
 */
final class WmsSettingsKeeper {

    private final OwnerDb owner;
    private final List<Map<String, Object>> settings;
    private final List<Map<String, Object>> warehouses;
    private final List<Map<String, Object>> depositors;
    private final List<Map<String, Object>> qualities;
    private final java.sql.Timestamp since;

    WmsSettingsKeeper(OwnerDb owner) {
        this.owner = owner;
        settings = owner.jdbc.sql("SELECT setting_key, value FROM dbo.portal_setting WHERE setting_key IN"
                + " ('stock_count_transfers', 'stock_sync_enabled', 'stock_sync_time')").query().listOfRows();
        warehouses = owner.jdbc.sql("SELECT warehouse_id, base_url FROM dbo.wms_warehouse").query().listOfRows();
        depositors = owner.jdbc.sql("SELECT depositor_id, wms_code FROM dbo.wms_depositor").query().listOfRows();
        qualities = owner.jdbc.sql("SELECT wms_code, quality_id FROM dbo.wms_quality").query().listOfRows();
        since = owner.jdbc.sql("SELECT clock_timestamp()::timestamp").query(java.sql.Timestamp.class).single();
    }

    void restore() {
        owner.tx.executeWithoutResult(tx -> {
            settings.forEach(s -> owner.jdbc.sql("UPDATE dbo.portal_setting SET value = :v WHERE setting_key = :k")
                    .param("v", s.get("value")).param("k", s.get("setting_key")).update());
            owner.jdbc.sql("DELETE FROM dbo.wms_warehouse").update();
            owner.jdbc.sql("DELETE FROM dbo.wms_depositor").update();
            owner.jdbc.sql("DELETE FROM dbo.wms_quality").update();
            warehouses.forEach(r -> owner.jdbc.sql("INSERT INTO dbo.wms_warehouse VALUES (:a, :b)")
                    .param("a", r.get("warehouse_id")).param("b", r.get("base_url")).update());
            depositors.forEach(r -> owner.jdbc.sql("INSERT INTO dbo.wms_depositor VALUES (:a, :b)")
                    .param("a", r.get("depositor_id")).param("b", r.get("wms_code")).update());
            qualities.forEach(r -> owner.jdbc.sql("INSERT INTO dbo.wms_quality VALUES (:a, :b)")
                    .param("a", r.get("wms_code")).param("b", r.get("quality_id")).update());
            owner.jdbc.sql("DELETE FROM dbo.sync_run WHERE entity = 'stock' AND started_at >= :t::timestamp")
                    .param("t", since).update();
        });
        owner.jdbc.sql("SELECT dbo.stock_rebase_from_details(NULL)").query(rs -> { });
    }
}
