package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorContext;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Комплекты в «Справочниках» и состав, заданный вручную (V154, решение
 * заказчика 22.11.2026).
 *
 * <p>Состав ведёт склад в WMS, но пока синхронизации нет, администратор
 * и сотрудник склада задают его в карточке товара. Запись — та же
 * функция {@code kit_components_apply}, что будет у синхронизации:
 * правила состава в одном месте, журнал и пересчёт остатков — там же.
 * Здесь же — проверка заранее, чтобы человек увидел понятную причину,
 * а не отказ базы.
 */
@Service
public class KitService {

    /** Кто задаёт состав вручную (решение 22.11.2026). */
    private static final List<String> KIT_EDITORS = List.of("staff", "admin");

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final CurrentUser currentUser;
    private final SkuWriteService skus;
    private final JsonMapper json = JsonMapper.builder().build();

    public KitService(JdbcClient jdbc, DepositorScope depositorScope, CurrentUser currentUser,
                      SkuWriteService skus) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.currentUser = currentUser;
        this.skus = skus;
    }

    /**
     * Комплекты клиента для вкладки «Комплекты»: товары с признаком или
     * с составом. {@code [{skuId, code, name, isActive, components: [{code,
     * name, qty, unitName}], assemble: [{warehouseName, available}]}]}.
     * «Можно собрать» — строки комплекта в остатках по складам. Под RLS.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        depositorScope.apply();
        requireKitsEnabled();
        Map<String, Map<String, Object>> byKit = new LinkedHashMap<>();
        for (Map<String, Object> r : SqlRows.toCamelCase(jdbc.sql("""
                        SELECT s.sku_id::text AS sku_id, s.code, s.name, s.is_active
                          FROM dbo.sku s
                         WHERE s.is_kit
                            OR EXISTS (SELECT 1 FROM dbo.sku_kit_component c WHERE c.kit_sku_id = s.sku_id)
                         ORDER BY s.code
                        """).query().listOfRows())) {
            Map<String, Object> kit = new LinkedHashMap<>(r);
            kit.put("components", new ArrayList<Map<String, Object>>());
            kit.put("assemble", new ArrayList<Map<String, Object>>());
            byKit.put(String.valueOf(r.get("skuId")), kit);
        }
        for (Map<String, Object> r : SqlRows.toCamelCase(jdbc.sql("""
                        SELECT c.kit_sku_id::text AS kit_sku_id, s.code, s.name, c.qty,
                               dbo.enum_name('enum_uom', u.id::text, u.name) AS unit_name
                          FROM dbo.sku_kit_component c
                          JOIN dbo.sku s ON s.sku_id = c.component_sku_id
                          LEFT JOIN dbo.enum_uom u ON u.id = s.uom_id
                         ORDER BY c.sort_order, s.code
                        """).query().listOfRows())) {
            add(byKit, r, "components");
        }
        for (Map<String, Object> r : SqlRows.toCamelCase(jdbc.sql("""
                        SELECT l.sku_id::text AS kit_sku_id, l.warehouse_id, l.warehouse_name,
                               sum(l.available_qty) AS available
                          FROM dbo.v_stock_lines l
                         WHERE l.is_kit
                         GROUP BY l.sku_id, l.warehouse_id, l.warehouse_name
                        HAVING sum(l.available_qty) > 0
                         ORDER BY l.warehouse_name
                        """).query().listOfRows())) {
            add(byKit, r, "assemble");
        }
        return new ArrayList<>(byKit.values());
    }

    private static void add(Map<String, Map<String, Object>> byKit, Map<String, Object> row, String list) {
        Map<String, Object> kit = byKit.get(String.valueOf(row.get("kitSkuId")));
        if (kit == null) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.remove("kitSkuId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) kit.get(list);
        items.add(item);
    }

    /**
     * Состав комплекта целиком: {@code {components: [{code, qty}]}}. Пустой
     * состав снимает с товара признак «Комплект». Возвращает карточку.
     */
    @Transactional
    public Map<String, Object> save(String skuId, Map<String, Object> body) {
        depositorScope.apply();
        requireKitsEnabled();
        requireKitEditor();
        Map<String, Object> kit = jdbc.sql("SELECT code, is_kit FROM dbo.sku WHERE sku_id = :id::uuid")
                .param("id", skuId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> Fail.notFound("error.sku.not_found"));
        String kitCode = String.valueOf(kit.get("code"));

        List<Map<String, Object>> rows = rows(body);
        if (!rows.isEmpty()) {
            String parents = jdbc.sql("""
                            SELECT string_agg(DISTINCT k.code, ', ' ORDER BY k.code)
                              FROM dbo.sku_kit_component c
                              JOIN dbo.sku k ON k.sku_id = c.kit_sku_id
                             WHERE c.component_sku_id = :id::uuid
                            """).param("id", skuId).query(String.class).optional().orElse(null);
            if (parents != null) {
                throw Fail.badRequest("error.kit.is_component", "code", kitCode, "kits", parents);
            }
        }
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> r : rows) {
            String code = String.valueOf(r.get("code"));
            if (!seen.add(code)) {
                throw Fail.badRequest("error.kit.duplicate", "code", code);
            }
            if (code.equals(kitCode)) {
                throw Fail.badRequest("error.kit.component_self", "code", code);
            }
            if (((BigDecimal) r.get("qty")).signum() <= 0) {
                throw Fail.badRequest("error.kit.qty_positive", "code", code);
            }
            List<Map<String, Object>> found = jdbc.sql("""
                            SELECT s.is_kit OR EXISTS (SELECT 1 FROM dbo.sku_kit_component c
                                                        WHERE c.kit_sku_id = s.sku_id) AS nested
                              FROM dbo.sku s
                             WHERE s.code = :code
                            """).param("code", code).query().listOfRows();
            if (found.isEmpty()) {
                throw Fail.badRequest("error.kit.component_unknown", "code", code);
            }
            if (Boolean.TRUE.equals(found.getFirst().get("nested"))) {
                throw Fail.badRequest("error.kit.component_is_kit", "code", code);
            }
        }

        jdbc.sql("SELECT dbo.kit_components_apply(dbo.current_depositor_id(), :code, :rows::jsonb)")
                .param("code", kitCode).param("rows", json.writeValueAsString(rows)).query(Integer.class).single();
        return skus.card(skuId);
    }

    /** Строки состава из тела запроса: код без пробелов по краям, количество числом. */
    private static List<Map<String, Object>> rows(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("components");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw Fail.badRequest("error.kit.component_required");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                throw Fail.badRequest("error.kit.component_required");
            }
            String code = m.get("code") == null ? "" : String.valueOf(m.get("code")).trim();
            if (code.isEmpty()) {
                throw Fail.badRequest("error.kit.component_required");
            }
            BigDecimal qty;
            try {
                qty = m.get("qty") == null ? BigDecimal.ZERO
                        : new BigDecimal(String.valueOf(m.get("qty")).trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                qty = BigDecimal.ZERO;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("qty", qty);
            rows.add(row);
        }
        return rows;
    }

    /** У клиента выключены комплекты (V155) — их экранов нет, и здесь отказ. */
    private void requireKitsEnabled() {
        if (!currentUser.kitsEnabled()) {
            throw Fail.forbidden("error.kit.disabled");
        }
    }

    /** Состав вручную задают администратор и сотрудник склада. */
    private void requireKitEditor() {
        String role = jdbc.sql("SELECT dbo.auth_role_by_login(:login::text)")
                .param("login", DepositorContext.userLogin())
                .query(String.class)
                .optional()
                .orElse("");
        if (!KIT_EDITORS.contains(role)) {
            throw Fail.forbidden("error.kit.forbidden");
        }
        currentUser.requireUserId();
    }
}
