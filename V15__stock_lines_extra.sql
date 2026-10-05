-- V15__stock_lines_extra.sql
-- Этап 4: экрану «Запасы» из демо нужны две колонки, которых не было
-- в представлении — группа товара и количество брака.
--   item_group_name — колонка «Группа» на экране;
--   defect_qty      — колонка «Брак»: сумма по партиям с качеством «Брак».
SET search_path = dbo;

DROP VIEW IF EXISTS v_stock_lines;

CREATE VIEW v_stock_lines AS
SELECT st.depositor_id, st.sku_id, s.code AS sku_code, s.name AS sku_name,
       g.name AS item_group_name,
       st.warehouse_id, w.name AS warehouse_name, u.symbol AS unit_name,
       SUM(st.quantity)                      AS quantity,
       SUM(st.reserved_qty)                  AS reserved_qty,
       SUM(st.blocked_qty)                   AS blocked_qty,
       SUM(st.available_qty)                 AS available_qty,
       SUM(CASE WHEN st.quality = 'Брак' THEN st.quantity ELSE 0 END) AS defect_qty,
       COALESCE(inc.incoming_qty, 0)         AS incoming_qty,
       COUNT(*)                              AS batch_count,
       MAX(st.synced_at)                     AS synced_at
FROM stock st
LEFT JOIN sku s           ON s.sku_id = st.sku_id
LEFT JOIN product_group g ON g.id = s.item_group_id
LEFT JOIN warehouse w     ON w.warehouse_id = st.warehouse_id
LEFT JOIN enum_uom u      ON u.id = st.unit_id
LEFT JOIN LATERAL (
  -- §8.3.2: «Ожидается» = количество по подтверждённым, но не принятым поступлениям
  SELECT SUM(rd.expected_qty) AS incoming_qty
  FROM receipt_details rd
  JOIN receipts r      ON r.doc_id = rd.doc_id
  JOIN enum_status es  ON es.id = r.status_id
  WHERE rd.sku_id = st.sku_id
    AND r.warehouse_id = st.warehouse_id
    AND rd.batch_number IS NULL              -- плановая запись строки (решение Р1)
    AND es.code NOT IN ('draft','received')
) inc ON true
GROUP BY st.depositor_id, st.sku_id, s.code, s.name, g.name,
         st.warehouse_id, w.name, u.symbol, inc.incoming_qty;

ALTER VIEW v_stock_lines SET (security_invoker = true);
