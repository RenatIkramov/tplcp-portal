-- =====================================================================
-- V141. Кэш остатков, поставка К1: сжатый слой и пересчёт
-- =====================================================================
-- План — «Кэш остатков — план реализации» (14.11.2026).
--
-- Было: одна таблица stock — строка на партию, и в ней же «резерв»
-- и «доступно». Их никто не считал: это числа из демо-данных.
--
-- Стало: два слоя.
--   stock         — ПОДРОБНО, «как в WMS»: партии, сроки, качество,
--                   количество. Портал в ней ничего не считает.
--   stock_balance — СЖАТО, «что считает портал»: строка на товар +
--                   склад + качество: наличие, резерв, к получению,
--                   доступно, нехватка.
--
-- Резерв и «к получению» НЕ копятся плюсами и минусами, а каждый раз
-- считаются заново из открытых документов — функцией stock_recalc.
-- Её зовут триггеры на документах и их строках, поэтому забыть
-- пересчёт в коде портала нельзя.
--
-- Правила (решения заказчика 14.11.2026):
--   резерв       — заказы от «Отправляется» до «Готов к отгрузке»
--                  (04, 02, 10, 20, 21, 30, 40) — с нажатия
--                  «Подтвердить»; у частично отгруженного (51) —
--                  неотгруженный остаток;
--   к получению  — поступления 04, 02, 10, 30: ожидаемое минус уже
--                  принятое;
--   перемещения  — учитываются, только если включена настройка
--                  stock_count_transfers: резерв на складе-отправителе
--                  до отгрузки, «к получению» на складе-получателе
--                  до приёмки;
--   доступно     = наличие − резерв, не ниже нуля; разница — нехватка.
--   Количество строки — то, что вернула WMS (*_qty_wms), иначе плановое.
--   План — на плановой записи строки (без партии, решение Р1); факт —
--   на записях той же строки с партиями: принято, брак, собрано.
--
-- Наличие (base_qty) в этой поставке берётся из подробного слоя:
-- ночной сверки с WMS ещё нет (поставка К5). Когда появится — срез
-- будет писать base_qty сам, а триггеры на stock (часть 5) уйдут.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Таблица сжатого слоя
-- ---------------------------------------------------------------------
CREATE TABLE stock_balance (
  depositor_id   uuid          NOT NULL REFERENCES depositor (depositor_id),
  sku_id         uuid          NOT NULL REFERENCES sku (sku_id),
  warehouse_id   varchar(20)   NOT NULL REFERENCES warehouse (warehouse_id),
  quality_id     smallint      NOT NULL REFERENCES enum_quality (id),
  base_qty       numeric(14,3) NOT NULL DEFAULT 0,
  base_at        timestamptz,
  on_hand_qty    numeric(14,3) NOT NULL DEFAULT 0,
  reserved_qty   numeric(14,3) NOT NULL DEFAULT 0,
  incoming_qty   numeric(14,3) NOT NULL DEFAULT 0,
  available_qty  numeric(14,3) NOT NULL DEFAULT 0,
  shortage_qty   numeric(14,3) NOT NULL DEFAULT 0,
  calculated_at  timestamptz   NOT NULL DEFAULT now(),
  PRIMARY KEY (depositor_id, sku_id, warehouse_id, quality_id)
);
COMMENT ON TABLE  stock_balance IS 'Остатки, сжатый слой: что считает портал. Пишет только stock_recalc (V141)';
COMMENT ON COLUMN stock_balance.base_qty      IS 'Количество из последнего среза WMS';
COMMENT ON COLUMN stock_balance.base_at       IS 'Момент среза; NULL — среза по строке не было';
COMMENT ON COLUMN stock_balance.on_hand_qty   IS 'Наличие сейчас';
COMMENT ON COLUMN stock_balance.reserved_qty  IS 'Резерв: открытые заказы (и перемещения)';
COMMENT ON COLUMN stock_balance.incoming_qty  IS 'К получению: открытые поступления (и перемещения)';
COMMENT ON COLUMN stock_balance.available_qty IS 'Доступно = наличие − резерв, не ниже нуля';
COMMENT ON COLUMN stock_balance.shortage_qty  IS 'Нехватка: на сколько резерв больше наличия';

-- Те же правила видимости, что у stock: свой клиент и открытые склады.
ALTER TABLE stock_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_balance FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON stock_balance
  USING (depositor_id = current_depositor_id())
  WITH CHECK (depositor_id = current_depositor_id());
CREATE POLICY warehouse_scope ON stock_balance AS RESTRICTIVE
  USING (warehouse_open(warehouse_id::text))
  WITH CHECK (warehouse_open(warehouse_id::text));
GRANT SELECT ON stock_balance TO portal_app;

-- Подробный слой — кэш WMS: портал его только читает.
REVOKE INSERT, UPDATE, DELETE ON stock FROM portal_app;

INSERT INTO portal_setting (setting_key, value, note) VALUES
  ('stock_count_transfers', 'false',
   'учитывать перемещения в остатках (резерв у отправителя, «к получению» у получателя): true / false; '
   || 'после смены — SELECT dbo.stock_recalc(NULL)')
ON CONFLICT (setting_key) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Пересчёт
-- ---------------------------------------------------------------------
-- Правило — в одной функции stock_open_documents; пересчёт stock_recalc
-- только раскладывает её ответ по строкам stock_balance.
CREATE FUNCTION stock_open_documents(p_depositor uuid, p_skus uuid[])
RETURNS TABLE (sku_id uuid, warehouse_id varchar, quality_id smallint, reserved numeric, incoming numeric)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_transfers boolean;
  v_good      smallint;
BEGIN
  SELECT COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_count_transfers'), false)
    INTO v_transfers;
  SELECT id INTO v_good FROM enum_quality WHERE code = '01';
  RETURN QUERY
  SELECT x.sku_id, x.warehouse_id, x.quality_id::smallint, sum(x.reserved), sum(x.incoming)
    FROM (
      -- заказы: резерв
      SELECT d.sku_id, o.warehouse_id, COALESCE(d.quality_id, v_good) AS quality_id,
             GREATEST(COALESCE(d.ordered_qty_wms, d.ordered_qty, 0)
                      - CASE WHEN es.code = '51'
                             THEN COALESCE((SELECT sum(f.picked_qty) FROM order_details f
                                             WHERE f.doc_id = d.doc_id AND f.line_number = d.line_number
                                               AND f.batch_number IS NOT NULL), 0)
                             ELSE 0 END, 0) AS reserved,
             0::numeric AS incoming
        FROM orders o
        JOIN enum_status es   ON es.id = o.status_id
        JOIN order_details d  ON d.doc_id = o.doc_id
       WHERE o.depositor_id = p_depositor
         AND es.code IN ('04', '02', '10', '20', '21', '30', '40', '51')
         AND d.batch_number IS NULL
      UNION ALL
      -- поступления: к получению
      SELECT d.sku_id, r.warehouse_id, COALESCE(d.quality_id, v_good),
             0,
             GREATEST(COALESCE(d.expected_qty_wms, d.expected_qty, 0)
                      - COALESCE((SELECT sum(COALESCE(f.accepted_qty, 0) + COALESCE(f.defect_qty, 0))
                                    FROM receipt_details f
                                   WHERE f.doc_id = d.doc_id AND f.line_number = d.line_number
                                     AND f.batch_number IS NOT NULL), 0), 0)
        FROM receipts r
        JOIN enum_status es    ON es.id = r.status_id
        JOIN receipt_details d ON d.doc_id = r.doc_id
       WHERE r.depositor_id = p_depositor
         AND es.code IN ('04', '02', '10', '30')
         AND d.batch_number IS NULL
      UNION ALL
      -- перемещения: резерв у отправителя — до отгрузки
      SELECT d.sku_id, t.from_warehouse_id, COALESCE(d.quality_id, v_good),
             COALESCE(d.qty_wms, d.qty, 0), 0
        FROM transfers t
        JOIN enum_status es     ON es.id = t.status_id
        JOIN transfer_details d ON d.doc_id = t.doc_id
       WHERE v_transfers
         AND t.depositor_id = p_depositor
         AND es.code IN ('04', '02', '10', '20', '21', '30', '40')
         AND d.batch_number IS NULL
      UNION ALL
      -- перемещения: к получению у получателя — до приёмки
      SELECT d.sku_id, t.to_warehouse_id, COALESCE(d.quality_id, v_good),
             0, COALESCE(d.qty_wms, d.qty, 0)
        FROM transfers t
        JOIN enum_status es     ON es.id = t.status_id
        JOIN transfer_details d ON d.doc_id = t.doc_id
       WHERE v_transfers
         AND t.depositor_id = p_depositor
         AND es.code IN ('04', '02', '10', '20', '21', '30', '40', '50', '51')
         AND d.batch_number IS NULL
    ) x
   WHERE x.sku_id IS NOT NULL AND x.warehouse_id IS NOT NULL
     AND (p_skus IS NULL OR x.sku_id = ANY (p_skus))
   GROUP BY x.sku_id, x.warehouse_id, x.quality_id;
END
$$;
COMMENT ON FUNCTION stock_open_documents(uuid, uuid[]) IS
  'Резерв и «к получению» клиента по открытым документам — единственное место правила (V141)';

-- p_depositor NULL — все клиенты; p_skus NULL — все товары клиента.
--
-- Блокировка на клиента: два подтверждения заказов одного клиента
-- считаются по очереди. Без неё каждый пересчёт видел бы только свой
-- документ и затёр бы резерв соседа.
CREATE FUNCTION stock_recalc(p_depositor uuid, p_skus uuid[] DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep uuid;
BEGIN
  IF p_depositor IS NULL THEN
    FOR v_dep IN SELECT depositor_id FROM depositor ORDER BY depositor_id LOOP
      PERFORM stock_recalc(v_dep, p_skus);
    END LOOP;
    RETURN;
  END IF;

  PERFORM pg_advisory_xact_lock(141, hashtext(p_depositor::text));

  -- строки, которых ещё нет: товар только едет или только заказан
  INSERT INTO stock_balance (depositor_id, sku_id, warehouse_id, quality_id)
  SELECT p_depositor, w.sku_id, w.warehouse_id, w.quality_id
    FROM stock_open_documents(p_depositor, p_skus) w
  ON CONFLICT DO NOTHING;

  UPDATE stock_balance b
     SET on_hand_qty   = b.base_qty,
         reserved_qty  = COALESCE(w.reserved, 0),
         incoming_qty  = COALESCE(w.incoming, 0),
         available_qty = GREATEST(b.base_qty - COALESCE(w.reserved, 0), 0),
         shortage_qty  = GREATEST(COALESCE(w.reserved, 0) - b.base_qty, 0),
         calculated_at = now()
    FROM stock_balance s
    LEFT JOIN stock_open_documents(p_depositor, p_skus) w
           ON w.sku_id = s.sku_id AND w.warehouse_id = s.warehouse_id AND w.quality_id = s.quality_id
   WHERE s.depositor_id = p_depositor
     AND (p_skus IS NULL OR s.sku_id = ANY (p_skus))
     AND b.depositor_id = s.depositor_id AND b.sku_id = s.sku_id
     AND b.warehouse_id = s.warehouse_id AND b.quality_id = s.quality_id;

  -- строка, которую держали только документы, а документов больше нет
  DELETE FROM stock_balance b
   WHERE b.depositor_id = p_depositor
     AND (p_skus IS NULL OR b.sku_id = ANY (p_skus))
     AND b.base_at IS NULL AND b.base_qty = 0 AND b.reserved_qty = 0 AND b.incoming_qty = 0;
END
$$;
COMMENT ON FUNCTION stock_recalc(uuid, uuid[]) IS
  'Пересчёт сжатого слоя остатков из открытых документов; NULL — все клиенты / все товары (V141)';

-- ---------------------------------------------------------------------
-- 3. Срез из подробного слоя (до ночной сверки с WMS, поставка К5)
-- ---------------------------------------------------------------------
CREATE FUNCTION stock_rebase_from_details(p_depositor uuid DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep  uuid;
  v_good smallint;
BEGIN
  IF p_depositor IS NULL THEN
    FOR v_dep IN SELECT depositor_id FROM depositor ORDER BY depositor_id LOOP
      PERFORM stock_rebase_from_details(v_dep);
    END LOOP;
    RETURN;
  END IF;

  PERFORM pg_advisory_xact_lock(141, hashtext(p_depositor::text));
  SELECT id INTO v_good FROM enum_quality WHERE code = '01';

  UPDATE stock_balance SET base_qty = 0, base_at = NULL WHERE depositor_id = p_depositor;

  INSERT INTO stock_balance (depositor_id, sku_id, warehouse_id, quality_id, base_qty, base_at)
  SELECT st.depositor_id, st.sku_id, st.warehouse_id, COALESCE(st.quality_id, v_good),
         sum(COALESCE(st.quantity, 0)), COALESCE(max(st.synced_at), now())
    FROM stock st
   WHERE st.depositor_id = p_depositor AND st.sku_id IS NOT NULL AND st.warehouse_id IS NOT NULL
   GROUP BY st.depositor_id, st.sku_id, st.warehouse_id, COALESCE(st.quality_id, v_good)
  ON CONFLICT (depositor_id, sku_id, warehouse_id, quality_id)
  DO UPDATE SET base_qty = EXCLUDED.base_qty, base_at = EXCLUDED.base_at;

  PERFORM stock_recalc(p_depositor, NULL);
END
$$;
COMMENT ON FUNCTION stock_rebase_from_details(uuid) IS
  'Наличие сжатого слоя — из подробного (stock), затем пересчёт; до ночной сверки с WMS (V141)';

-- Пересчёт зовут триггеры; срез — миграция, демо-данные и проверки.
-- Порталу напрямую они не нужны.
REVOKE EXECUTE ON FUNCTION stock_open_documents(uuid, uuid[]), stock_recalc(uuid, uuid[]),
  stock_rebase_from_details(uuid) FROM PUBLIC, portal_app;

-- ---------------------------------------------------------------------
-- 4. Триггеры на документах: статус, склад, строки
-- ---------------------------------------------------------------------
-- Черновик остатков не трогает: пока документ не подтверждён, для
-- склада его нет. Поэтому правка строк черновика (в том числе загрузка
-- тысячи строк из Excel) пересчёта не вызывает.
CREATE FUNCTION stock_doc_changed()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_old  text;
  v_new  text;
  v_skus uuid[];
BEGIN
  -- Удаление не черновика (портал так не делает, только уборка руками):
  -- строк уже нет, какие товары затронуты — неизвестно; клиент целиком.
  IF TG_OP = 'DELETE' THEN
    SELECT code INTO v_old FROM enum_status WHERE id = OLD.status_id;
    IF COALESCE(v_old, '01') <> '01' THEN
      PERFORM stock_recalc(OLD.depositor_id, NULL);
    END IF;
    RETURN NULL;
  END IF;
  SELECT code INTO v_old FROM enum_status WHERE id = OLD.status_id;
  SELECT code INTO v_new FROM enum_status WHERE id = NEW.status_id;
  IF COALESCE(v_old, '01') = '01' AND COALESCE(v_new, '01') = '01' THEN
    RETURN NULL;
  END IF;
  IF OLD.status_id IS NOT DISTINCT FROM NEW.status_id
     AND to_jsonb(OLD) -> TG_ARGV[1] IS NOT DISTINCT FROM to_jsonb(NEW) -> TG_ARGV[1]
     AND (TG_NARGS < 3 OR to_jsonb(OLD) -> TG_ARGV[2] IS NOT DISTINCT FROM to_jsonb(NEW) -> TG_ARGV[2]) THEN
    RETURN NULL;
  END IF;
  EXECUTE format('SELECT array_agg(DISTINCT sku_id) FROM %I WHERE doc_id = $1 AND sku_id IS NOT NULL', TG_ARGV[0])
     INTO v_skus USING NEW.doc_id;
  IF v_skus IS NOT NULL THEN
    PERFORM stock_recalc(NEW.depositor_id, v_skus);
  END IF;
  RETURN NULL;
END
$$;

CREATE FUNCTION stock_line_changed()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_doc    uuid := CASE WHEN TG_OP = 'DELETE' THEN OLD.doc_id ELSE NEW.doc_id END;
  v_dep    uuid;
  v_status text;
  v_skus   uuid[];
BEGIN
  IF TG_OP = 'UPDATE' AND to_jsonb(OLD) IS NOT DISTINCT FROM to_jsonb(NEW) THEN
    RETURN NULL;
  END IF;
  EXECUTE format('SELECT h.depositor_id, es.code FROM %I h LEFT JOIN enum_status es ON es.id = h.status_id '
                 || 'WHERE h.doc_id = $1', TG_ARGV[0])
     INTO v_dep, v_status USING v_doc;
  IF v_dep IS NULL OR COALESCE(v_status, '01') = '01' THEN
    RETURN NULL;
  END IF;
  v_skus := array_remove(ARRAY[CASE WHEN TG_OP <> 'INSERT' THEN OLD.sku_id END,
                               CASE WHEN TG_OP <> 'DELETE' THEN NEW.sku_id END], NULL);
  IF cardinality(v_skus) > 0 THEN
    PERFORM stock_recalc(v_dep, v_skus);
  END IF;
  RETURN NULL;
END
$$;

REVOKE EXECUTE ON FUNCTION stock_doc_changed(), stock_line_changed() FROM PUBLIC, portal_app;

CREATE TRIGGER stock_recalc AFTER UPDATE OR DELETE ON orders
  FOR EACH ROW EXECUTE FUNCTION stock_doc_changed('order_details', 'warehouse_id');
CREATE TRIGGER stock_recalc AFTER UPDATE OR DELETE ON receipts
  FOR EACH ROW EXECUTE FUNCTION stock_doc_changed('receipt_details', 'warehouse_id');
CREATE TRIGGER stock_recalc AFTER UPDATE OR DELETE ON transfers
  FOR EACH ROW EXECUTE FUNCTION stock_doc_changed('transfer_details', 'from_warehouse_id', 'to_warehouse_id');

CREATE TRIGGER stock_recalc AFTER INSERT OR UPDATE OR DELETE ON order_details
  FOR EACH ROW EXECUTE FUNCTION stock_line_changed('orders');
CREATE TRIGGER stock_recalc AFTER INSERT OR UPDATE OR DELETE ON receipt_details
  FOR EACH ROW EXECUTE FUNCTION stock_line_changed('receipts');
CREATE TRIGGER stock_recalc AFTER INSERT OR UPDATE OR DELETE ON transfer_details
  FOR EACH ROW EXECUTE FUNCTION stock_line_changed('transfers');

-- ---------------------------------------------------------------------
-- 5. Триггеры на подробном слое — до ночной сверки (К5)
-- ---------------------------------------------------------------------
-- Пока среза WMS нет, наличие сжатого слоя следует за подробным:
-- поменяли строку stock — наличие клиента пересобрано.
CREATE FUNCTION stock_detail_changed()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep uuid;
BEGIN
  FOR v_dep IN SELECT DISTINCT depositor_id FROM stock_detail_rows WHERE depositor_id IS NOT NULL LOOP
    PERFORM stock_rebase_from_details(v_dep);
  END LOOP;
  RETURN NULL;
END
$$;
REVOKE EXECUTE ON FUNCTION stock_detail_changed() FROM PUBLIC, portal_app;

CREATE TRIGGER stock_rebase_ins AFTER INSERT ON stock
  REFERENCING NEW TABLE AS stock_detail_rows
  FOR EACH STATEMENT EXECUTE FUNCTION stock_detail_changed();
CREATE TRIGGER stock_rebase_upd AFTER UPDATE ON stock
  REFERENCING NEW TABLE AS stock_detail_rows
  FOR EACH STATEMENT EXECUTE FUNCTION stock_detail_changed();
CREATE TRIGGER stock_rebase_del AFTER DELETE ON stock
  REFERENCING OLD TABLE AS stock_detail_rows
  FOR EACH STATEMENT EXECUTE FUNCTION stock_detail_changed();

-- ---------------------------------------------------------------------
-- 6. Представления читают сжатый слой
-- ---------------------------------------------------------------------
-- Имена и колонки v_stock_available и v_stock_lines прежние: экран
-- «Запасы» и окно строки документа не меняются.
DROP VIEW v_stock_lines;
DROP VIEW v_stock_available;
DROP VIEW v_stock_details;

CREATE VIEW v_stock_available WITH (security_invoker = true) AS
SELECT b.depositor_id,
       b.sku_id,
       b.warehouse_id,
       (SELECT max(st.unit_id) FROM stock st
         WHERE st.depositor_id = b.depositor_id AND st.sku_id = b.sku_id
           AND st.warehouse_id = b.warehouse_id)                                   AS unit_id,
       sum(b.on_hand_qty)                                                          AS quantity,
       sum(b.reserved_qty)                                                         AS reserved_qty,
       sum(CASE WHEN q.code <> '01' THEN b.on_hand_qty ELSE 0 END)                 AS blocked_qty,
       sum(CASE WHEN q.code = '01' THEN b.available_qty ELSE 0 END)                AS available_qty,
       sum(CASE WHEN q.code = '02' THEN b.on_hand_qty ELSE 0 END)                  AS defect_qty,
       sum(b.incoming_qty)                                                         AS incoming_qty,
       (SELECT count(*) FROM stock st
         WHERE st.depositor_id = b.depositor_id AND st.sku_id = b.sku_id
           AND st.warehouse_id = b.warehouse_id)                                   AS batch_count,
       max(b.base_at)                                                              AS synced_at,
       json_object_agg(q.code, json_build_object('quantity', b.on_hand_qty, 'available', b.available_qty)
                       ORDER BY q.code)::text                                      AS quality_qty
  FROM stock_balance b
  JOIN enum_quality q ON q.id = b.quality_id
 GROUP BY b.depositor_id, b.sku_id, b.warehouse_id;
COMMENT ON VIEW v_stock_available IS
  'Остатки по товару и складу из сжатого слоя stock_balance; «доступно» — по годному (V141)';
GRANT SELECT ON v_stock_available TO portal_app;

CREATE VIEW v_stock_lines WITH (security_invoker = true) AS
SELECT a.depositor_id,
       a.sku_id,
       s.code AS sku_code,
       s.name AS sku_name,
       s.item_group AS item_group_name,
       a.warehouse_id,
       w.name AS warehouse_name,
       enum_symbol('enum_uom', u.id::text, u.symbol::text)::varchar(10) AS unit_name,
       a.quantity,
       a.reserved_qty,
       a.blocked_qty,
       a.available_qty,
       a.defect_qty,
       a.incoming_qty,
       a.batch_count,
       a.synced_at,
       a.quality_qty
  FROM v_stock_available a
  LEFT JOIN sku s       ON s.sku_id = a.sku_id
  LEFT JOIN warehouse w ON w.warehouse_id = a.warehouse_id
  LEFT JOIN enum_uom u  ON u.id = COALESCE(a.unit_id, s.uom_id);
GRANT SELECT ON v_stock_lines TO portal_app;

-- Партии: только то, что знает WMS. Резерва и «доступно» по партии
-- портал не знает — резервирует по партиям WMS.
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
       enum_name('enum_quality', q.id::text, q.name::text)::varchar(255) AS quality,
       q.code AS quality_code,
       st.quantity,
       CASE WHEN q.code <> '01' THEN st.quantity ELSE 0 END AS blocked_qty,
       enum_symbol('enum_uom', u.id::text, u.symbol::text)::varchar(10) AS unit_name,
       st.updated_at,
       st.synced_at
  FROM stock st
  LEFT JOIN sku s          ON s.sku_id = st.sku_id
  LEFT JOIN warehouse w    ON w.warehouse_id = st.warehouse_id
  LEFT JOIN enum_uom u     ON u.id = st.unit_id
  LEFT JOIN enum_quality q ON q.id = st.quality_id;
GRANT SELECT ON v_stock_details TO portal_app;

-- ---------------------------------------------------------------------
-- 7. Подробный слой без чужих колонок; колонки партий в справочнике
-- ---------------------------------------------------------------------
ALTER TABLE stock DROP COLUMN reserved_qty;
ALTER TABLE stock DROP COLUMN available_qty;

DELETE FROM user_table_filter      WHERE table_key = 'stockBatch' AND column_id IN ('available', 'reserved');
DELETE FROM user_table_column      WHERE table_key = 'stockBatch' AND column_id IN ('available', 'reserved');
DELETE FROM depositor_table_column WHERE table_key = 'stockBatch' AND column_id IN ('available', 'reserved');
DELETE FROM table_column           WHERE table_key = 'stockBatch' AND column_id IN ('available', 'reserved');
DELETE FROM ui_text     WHERE text_key IN ('col.stockBatch.available', 'col.stockBatch.reserved');
DELETE FROM ui_text_key WHERE text_key IN ('col.stockBatch.available', 'col.stockBatch.reserved');

-- ---------------------------------------------------------------------
-- 8. Заполнить сжатый слой тем, что уже есть в базе
-- ---------------------------------------------------------------------
SELECT stock_rebase_from_details(NULL);
