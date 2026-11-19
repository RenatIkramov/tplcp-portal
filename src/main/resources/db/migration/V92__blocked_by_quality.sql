-- ---------------------------------------------------------------------
-- V92. Причины блокировки убираются; «Заблокировано» — по качеству
-- ---------------------------------------------------------------------
-- Решение заказчика от 28–30.10.2026:
--   * поле «Причина блокировки» уходит из запасов целиком — колонка
--     в детализации партий, перечисление enum_block_reason и его тексты;
--   * «Заблокировано» портал считает сам: всё количество партий
--     с качеством, отличным от 01 «Годный» (V89). И в строке товара,
--     и в каждой партии;
--   * колонка stock.blocked_qty (её присылала WMS) удаляется: источник
--     правды один — качество партии. Иначе два числа «заблокировано»
--     однажды разошлись бы, и человек не понял бы, какому верить;
--   * «Доступно» по-прежнему даёт WMS (available_qty).
--
-- Партия без качества (quality_id пуст) не считается заблокированной:
-- о ней ничего не известно, и записывать её в блок значило бы выдумать.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Представления — до удаления колонок, которые они читают
-- ---------------------------------------------------------------------
-- v_stock_available: колонки и типы прежние — CREATE OR REPLACE.
-- v_stock_details теряет колонку block_reason_name — пересоздаётся.
-- security_invoker указывается заново: замена без WITH его сбрасывает.
CREATE OR REPLACE VIEW v_stock_available WITH (security_invoker = true) AS
 WITH on_hand AS (
         SELECT stock.depositor_id,
            stock.sku_id,
            stock.warehouse_id,
            max(stock.unit_id) AS unit_id,
            sum(stock.quantity) AS quantity,
            sum(stock.reserved_qty) AS reserved_qty,
            sum(
                CASE
                    WHEN ((q.code)::text <> '01'::text) THEN stock.quantity
                    ELSE (0)::numeric
                END) AS blocked_qty,
            sum(stock.available_qty) AS available_qty,
            sum(
                CASE
                    WHEN ((q.code)::text = '02'::text) THEN stock.quantity
                    ELSE (0)::numeric
                END) AS defect_qty,
            count(*) AS batch_count,
            max(stock.synced_at) AS synced_at
           FROM (dbo.stock
             LEFT JOIN dbo.enum_quality q ON ((q.id = stock.quality_id)))
          GROUP BY stock.depositor_id, stock.sku_id, stock.warehouse_id
        ), by_quality AS (
         SELECT b.depositor_id,
            b.sku_id,
            b.warehouse_id,
            (json_object_agg(b.code, json_build_object('quantity', b.quantity, 'available', b.available_qty) ORDER BY b.code))::text AS quality_qty
           FROM ( SELECT stock.depositor_id,
                    stock.sku_id,
                    stock.warehouse_id,
                    q.code,
                    sum(stock.quantity) AS quantity,
                        CASE
                            WHEN ((q.code)::text = '01'::text) THEN sum(stock.available_qty)
                            ELSE sum((stock.quantity - COALESCE(stock.reserved_qty, (0)::numeric)))
                        END AS available_qty
                   FROM (dbo.stock
                     JOIN dbo.enum_quality q ON ((q.id = stock.quality_id)))
                  GROUP BY stock.depositor_id, stock.sku_id, stock.warehouse_id, q.code) b
          GROUP BY b.depositor_id, b.sku_id, b.warehouse_id
        ), incoming AS (
         SELECT r.depositor_id,
            rd.sku_id,
            r.warehouse_id,
            sum(rd.expected_qty) AS incoming_qty
           FROM ((dbo.receipt_details rd
             JOIN dbo.receipts r ON ((r.doc_id = rd.doc_id)))
             JOIN dbo.enum_status es ON ((es.id = r.status_id)))
          WHERE ((rd.batch_number IS NULL) AND ((es.code)::text = ANY (ARRAY['02'::text, '10'::text, '30'::text])))
          GROUP BY r.depositor_id, rd.sku_id, r.warehouse_id
        )
 SELECT COALESCE(h.depositor_id, i.depositor_id) AS depositor_id,
    COALESCE(h.sku_id, i.sku_id) AS sku_id,
    COALESCE(h.warehouse_id, i.warehouse_id) AS warehouse_id,
    h.unit_id,
    COALESCE(h.quantity, (0)::numeric) AS quantity,
    COALESCE(h.reserved_qty, (0)::numeric) AS reserved_qty,
    COALESCE(h.blocked_qty, (0)::numeric) AS blocked_qty,
    COALESCE(h.available_qty, (0)::numeric) AS available_qty,
    COALESCE(h.defect_qty, (0)::numeric) AS defect_qty,
    COALESCE(i.incoming_qty, (0)::numeric) AS incoming_qty,
    COALESCE(h.batch_count, (0)::bigint) AS batch_count,
    h.synced_at,
    bq.quality_qty
   FROM ((on_hand h
     FULL JOIN incoming i ON (((i.depositor_id = h.depositor_id) AND (i.sku_id = h.sku_id) AND ((i.warehouse_id)::text = (h.warehouse_id)::text))))
     LEFT JOIN by_quality bq ON (((bq.depositor_id = h.depositor_id) AND (bq.sku_id = h.sku_id) AND ((bq.warehouse_id)::text = (h.warehouse_id)::text))));
DROP VIEW v_stock_details;
CREATE VIEW v_stock_details WITH (security_invoker = true) AS
 SELECT st.register_id,
    st.depositor_id,
    st.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    st.warehouse_id,
    w.name AS warehouse_name,
    st.batch_number,
    st.series_number,
    st.expiry_date,
    (dbo.enum_name('enum_quality'::text, (q.id)::text, (q.name)::text))::character varying(255) AS quality,
    q.code AS quality_code,
    st.quantity,
    st.reserved_qty,
    CASE
        WHEN ((q.code)::text <> '01'::text) THEN st.quantity
        ELSE (0)::numeric
    END AS blocked_qty,
    st.available_qty,
    (dbo.enum_symbol('enum_uom'::text, (u.id)::text, (u.symbol)::text))::character varying(10) AS unit_name,
    st.updated_at,
    st.synced_at
   FROM ((((dbo.stock st
     LEFT JOIN dbo.sku s ON ((s.sku_id = st.sku_id)))
     LEFT JOIN dbo.warehouse w ON (((w.warehouse_id)::text = (st.warehouse_id)::text)))
     LEFT JOIN dbo.enum_uom u ON ((u.id = st.unit_id)))
     LEFT JOIN dbo.enum_quality q ON ((q.id = st.quality_id)));
GRANT SELECT ON v_stock_details TO portal_app;

-- ---------------------------------------------------------------------
-- 2. Колонки, перечисление, справочник колонок, тексты
-- ---------------------------------------------------------------------
ALTER TABLE stock DROP CONSTRAINT IF EXISTS fk_stock_block_reason_id;
ALTER TABLE stock DROP COLUMN block_reason_id;
ALTER TABLE stock DROP COLUMN blocked_qty;

DELETE FROM enum_text     WHERE enum_key = 'enum_block_reason';
DELETE FROM enum_registry WHERE enum_key = 'enum_block_reason';
DROP TABLE enum_block_reason;

-- Колонка «Причина блокировки» в детализации партий: сначала личные
-- и клиентские настройки, которые на неё ссылаются, потом она сама
DELETE FROM user_table_filter     WHERE table_key = 'stockBatch' AND column_id = 'blockReason';
DELETE FROM user_table_column     WHERE table_key = 'stockBatch' AND column_id = 'blockReason';
DELETE FROM depositor_table_column WHERE table_key = 'stockBatch' AND column_id = 'blockReason';
DELETE FROM table_column          WHERE table_key = 'stockBatch' AND column_id = 'blockReason';

DELETE FROM ui_text     WHERE text_key IN ('col.stockBatch.blockReason', 'enums.name.enum_block_reason');
DELETE FROM ui_text_key WHERE text_key IN ('col.stockBatch.blockReason', 'enums.name.enum_block_reason');

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  v text;
  bad bigint;
BEGIN
  FOREACH v IN ARRAY ARRAY['v_stock_available', 'v_stock_lines', 'v_stock_details'] LOOP
    IF (SELECT reloptions FROM pg_class WHERE oid = ('dbo.' || v)::regclass)
         IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
      RAISE EXCEPTION '% без security_invoker', v;
    END IF;
  END LOOP;

  -- Строка товара и его партии считают «заблокировано» одинаково
  SELECT count(*) INTO bad
    FROM v_stock_available a
   WHERE a.blocked_qty IS DISTINCT FROM COALESCE((
           SELECT sum(d.blocked_qty) FROM v_stock_details d
            WHERE d.depositor_id = a.depositor_id AND d.sku_id = a.sku_id
              AND d.warehouse_id = a.warehouse_id), 0);
  IF bad > 0 THEN
    RAISE EXCEPTION '«Заблокировано» в строке товара и в его партиях разошлось: % строк', bad;
  END IF;
END
$check$;
