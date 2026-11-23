SET search_path = dbo;

-- =====================================================================
-- V152. Комплекты, шаг 2: остатки и проверка заказа
-- =====================================================================
-- Документ проекта «Комплекты — решение». Своего остатка у комплекта нет:
-- WMS хранит комплектующие и собирает комплект при отгрузке.
--
-- * Резерв. Строка заказа с комплектом резервирует его комплектующие:
--   открытое количество × количество в составе (stock_open_documents —
--   по-прежнему единственное место правила). Строки поступлений и
--   перемещений раскладываются так же; с шагом 3 комплектов в них не будет.
-- * «Можно собрать». У комплекта в stock_balance своя строка по складу
--   и качеству: «В наличии» 0, «Доступно» — наименьшее по комплектующим
--   из «Доступно комплектующей ÷ количество в составе», вниз до целого.
--   Комплект, из которого ничего не собрать, строки не имеет. Резерв
--   у комплекта не показывается: он стоит на комплектующих.
-- * Пересчёт. stock_recalc по товарам заодно пересчитывает комплектующие
--   их комплектов и комплекты, в которые входят товары; смена состава
--   (kit_components_apply) пересчитывает старые и новые комплектующие.
-- * Проверка при «Подтвердить» (stock_doc_shortage) — по комплектующим;
--   в ответе — в каких комплектах заказа эта комплектующая (kit_codes).
-- * «Запасы»: признак is_kit у строки (v_stock_lines) — экран ставит
--   отметку «комплект».

-- ---------------------------------------------------------------------
-- 1. Правило резерва: комплект — это его комплектующие
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION stock_open_documents(p_depositor uuid, p_skus uuid[])
RETURNS TABLE (sku_id uuid, warehouse_id varchar, quality_id smallint,
               reserved numeric, incoming numeric, reserved_wms numeric)
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
  SELECT x.sku_id, x.warehouse_id, x.quality_id::smallint, sum(x.reserved), sum(x.incoming),
         sum(CASE WHEN x.status NOT IN ('04', '02') THEN x.reserved ELSE 0 END)
    FROM (
    -- строка с комплектом (V152) — это его комплектующие: состав × количество;
    -- своего остатка у комплекта нет
    SELECT COALESCE(k.component_sku_id, x0.sku_id) AS sku_id, x0.warehouse_id, x0.quality_id, x0.status,
           x0.reserved * COALESCE(k.qty, 1) AS reserved, x0.incoming * COALESCE(k.qty, 1) AS incoming
      FROM (
      -- заказы: резерв
      SELECT d.sku_id, o.warehouse_id, COALESCE(d.quality_id, v_good) AS quality_id, es.code AS status,
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
      SELECT d.sku_id, r.warehouse_id, COALESCE(d.quality_id, v_good), es.code,
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
      SELECT d.sku_id, t.from_warehouse_id, COALESCE(d.quality_id, v_good), es.code,
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
      SELECT d.sku_id, t.to_warehouse_id, COALESCE(d.quality_id, v_good), es.code,
             0, COALESCE(d.qty_wms, d.qty, 0)
        FROM transfers t
        JOIN enum_status es     ON es.id = t.status_id
        JOIN transfer_details d ON d.doc_id = t.doc_id
       WHERE v_transfers
         AND t.depositor_id = p_depositor
         AND es.code IN ('04', '02', '10', '20', '21', '30', '40', '50', '51')
         AND d.batch_number IS NULL
      ) x0
      LEFT JOIN sku_kit_component k ON k.kit_sku_id = x0.sku_id
    ) x
   WHERE x.sku_id IS NOT NULL AND x.warehouse_id IS NOT NULL
     AND (p_skus IS NULL OR x.sku_id = ANY (p_skus))
   GROUP BY x.sku_id, x.warehouse_id, x.quality_id;
END
$$;

-- ---------------------------------------------------------------------
-- 2. Пересчёт: комплектующие и «можно собрать»
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION stock_recalc(p_depositor uuid, p_skus uuid[] DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep  uuid;
  v_skus uuid[];
  v_kits uuid[];
BEGIN
  IF p_depositor IS NULL THEN
    FOR v_dep IN SELECT depositor_id FROM depositor ORDER BY depositor_id LOOP
      PERFORM stock_recalc(v_dep, p_skus);
    END LOOP;
    RETURN;
  END IF;

  PERFORM pg_advisory_xact_lock(141, hashtext(p_depositor::text));

  -- что пересчитать: товары и комплектующие их комплектов (V152);
  -- комплекты — те, в которые входит что-то из этого
  IF p_skus IS NULL THEN
    SELECT array_agg(DISTINCT k.kit_sku_id) INTO v_kits
      FROM sku_kit_component k WHERE k.depositor_id = p_depositor;
  ELSE
    SELECT array_agg(DISTINCT x) INTO v_skus
      FROM (SELECT unnest(p_skus) AS x
            UNION SELECT k.component_sku_id FROM sku_kit_component k WHERE k.kit_sku_id = ANY (p_skus)) s;
    SELECT array_agg(DISTINCT k.kit_sku_id) INTO v_kits
      FROM sku_kit_component k
     WHERE k.kit_sku_id = ANY (v_skus) OR k.component_sku_id = ANY (v_skus);
  END IF;

  -- строки, которых ещё нет: товар только едет или только заказан
  INSERT INTO stock_balance (depositor_id, sku_id, warehouse_id, quality_id)
  SELECT p_depositor, w.sku_id, w.warehouse_id, w.quality_id
    FROM stock_open_documents(p_depositor, v_skus) w
  ON CONFLICT DO NOTHING;

  UPDATE stock_balance b
     SET on_hand_qty   = b.base_qty,
         reserved_qty  = COALESCE(w.reserved, 0),
         incoming_qty  = COALESCE(w.incoming, 0),
         available_qty = GREATEST(b.base_qty - COALESCE(w.reserved, 0), 0),
         shortage_qty  = GREATEST(COALESCE(w.reserved, 0) - b.base_qty, 0),
         calculated_at = now()
    FROM stock_balance s
    LEFT JOIN stock_open_documents(p_depositor, v_skus) w
           ON w.sku_id = s.sku_id AND w.warehouse_id = s.warehouse_id AND w.quality_id = s.quality_id
   WHERE s.depositor_id = p_depositor
     AND (v_skus IS NULL OR s.sku_id = ANY (v_skus))
     AND b.depositor_id = s.depositor_id AND b.sku_id = s.sku_id
     AND b.warehouse_id = s.warehouse_id AND b.quality_id = s.quality_id;

  -- строка, которую держали только документы, а документов больше нет
  DELETE FROM stock_balance b
   WHERE b.depositor_id = p_depositor
     AND (v_skus IS NULL OR b.sku_id = ANY (v_skus))
     AND b.base_at IS NULL AND b.base_qty = 0 AND b.reserved_qty = 0 AND b.incoming_qty = 0;

  -- комплекты: «можно собрать» из свободных комплектующих, по складу и качеству
  IF v_kits IS NOT NULL THEN
    DELETE FROM stock_balance b WHERE b.depositor_id = p_depositor AND b.sku_id = ANY (v_kits);
    INSERT INTO stock_balance (depositor_id, sku_id, warehouse_id, quality_id, available_qty, calculated_at)
    SELECT p_depositor, k.kit_sku_id, wq.warehouse_id, wq.quality_id,
           min(floor(COALESCE(b.available_qty, 0) / k.qty)), now()
      FROM sku_kit_component k
      JOIN (SELECT DISTINCT k2.kit_sku_id, c.warehouse_id, c.quality_id
              FROM sku_kit_component k2
              JOIN stock_balance c ON c.depositor_id = p_depositor AND c.sku_id = k2.component_sku_id
             WHERE k2.kit_sku_id = ANY (v_kits)) wq ON wq.kit_sku_id = k.kit_sku_id
      LEFT JOIN stock_balance b
             ON b.depositor_id = p_depositor AND b.sku_id = k.component_sku_id
            AND b.warehouse_id = wq.warehouse_id AND b.quality_id = wq.quality_id
     WHERE k.kit_sku_id = ANY (v_kits)
     GROUP BY k.kit_sku_id, wq.warehouse_id, wq.quality_id
    HAVING min(floor(COALESCE(b.available_qty, 0) / k.qty)) > 0;
  END IF;
END
$$;
COMMENT ON FUNCTION stock_recalc(uuid, uuid[]) IS
  'Пересчёт сжатого слоя остатков из открытых документов; комплекты — «можно собрать»; NULL — все (V141, V152)';

-- ---------------------------------------------------------------------
-- 3. Смена состава пересчитывает остатки
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION kit_components_apply(p_depositor uuid, p_kit_code text, p_rows jsonb)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_kit   uuid;
  v_bad   text;
  v_count integer;
  v_old   uuid[];
BEGIN
  SELECT sku_id INTO v_kit FROM sku WHERE depositor_id = p_depositor AND code = p_kit_code;
  IF v_kit IS NULL THEN
    RAISE EXCEPTION 'kit_components_apply: no item % for depositor %', p_kit_code, p_depositor;
  END IF;

  SELECT e ->> 'code' INTO v_bad
    FROM jsonb_array_elements(COALESCE(p_rows, '[]')) e
   WHERE NOT EXISTS (SELECT 1 FROM sku s WHERE s.depositor_id = p_depositor AND s.code = e ->> 'code')
      OR e ->> 'code' = p_kit_code
      OR COALESCE((e ->> 'qty')::numeric, 0) <= 0
      OR EXISTS (SELECT 1 FROM sku_kit_component k JOIN sku s ON s.sku_id = k.kit_sku_id
                  WHERE s.depositor_id = p_depositor AND s.code = e ->> 'code')
   LIMIT 1;
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'kit_components_apply: bad component % of kit %', v_bad, p_kit_code;
  END IF;
  IF jsonb_array_length(COALESCE(p_rows, '[]')) > 0
     AND EXISTS (SELECT 1 FROM sku_kit_component WHERE component_sku_id = v_kit) THEN
    RAISE EXCEPTION 'kit_components_apply: % is a component of another kit', p_kit_code;
  END IF;

  -- прежние комплектующие: их резерв под заказы комплекта уйдёт или сменится
  SELECT array_agg(component_sku_id) INTO v_old FROM sku_kit_component WHERE kit_sku_id = v_kit;

  -- целиком: убрать лишнее, записать остальное (неизменённое — не событие журнала)
  DELETE FROM sku_kit_component k
   WHERE k.kit_sku_id = v_kit
     AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(p_rows, '[]')) e
                       JOIN sku s ON s.depositor_id = p_depositor AND s.code = e ->> 'code'
                      WHERE s.sku_id = k.component_sku_id);
  INSERT INTO sku_kit_component (depositor_id, kit_sku_id, component_sku_id, qty, sort_order)
  SELECT p_depositor, v_kit, s.sku_id, (e.value ->> 'qty')::numeric, e.ordinality::smallint
    FROM jsonb_array_elements(COALESCE(p_rows, '[]')) WITH ORDINALITY e
    JOIN sku s ON s.depositor_id = p_depositor AND s.code = e.value ->> 'code'
  ON CONFLICT (kit_sku_id, component_sku_id) DO UPDATE
     SET qty = EXCLUDED.qty, sort_order = EXCLUDED.sort_order, synced_at = now()
   WHERE (sku_kit_component.qty, sku_kit_component.sort_order)
         IS DISTINCT FROM (EXCLUDED.qty, EXCLUDED.sort_order);

  SELECT count(*) INTO v_count FROM sku_kit_component WHERE kit_sku_id = v_kit;
  UPDATE sku SET is_kit = (v_count > 0) WHERE sku_id = v_kit AND is_kit IS DISTINCT FROM (v_count > 0);
  -- остатки: комплект, его прежние и новые комплектующие (V152)
  PERFORM stock_recalc(p_depositor, ARRAY[v_kit] || COALESCE(v_old, '{}')
                       || COALESCE((SELECT array_agg(component_sku_id) FROM sku_kit_component
                                     WHERE kit_sku_id = v_kit), '{}'));
  RETURN v_count;
END
$$;

-- ---------------------------------------------------------------------
-- 4. Проверка при «Подтвердить»: по комплектующим
-- ---------------------------------------------------------------------
DROP FUNCTION stock_doc_shortage(text, uuid);
CREATE FUNCTION stock_doc_shortage(p_type text, p_doc uuid)
RETURNS TABLE (sku_id uuid, sku_code varchar, sku_name varchar, warehouse_id varchar,
               quality_code varchar, quality_name varchar,
               required_qty numeric, available_qty numeric, shortage_qty numeric, kit_codes text)
LANGUAGE plpgsql
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep       uuid := current_depositor_id();
  v_transfers boolean;
BEGIN
  IF v_dep IS NULL OR p_type NOT IN ('orders', 'transfers') THEN
    RETURN;
  END IF;
  IF p_type = 'transfers' THEN
    SELECT COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_count_transfers'), false)
      INTO v_transfers;
    IF NOT v_transfers THEN
      RETURN;
    END IF;
  END IF;

  PERFORM pg_advisory_xact_lock(141, hashtext(v_dep::text));

  RETURN QUERY
  WITH good AS (SELECT id FROM enum_quality WHERE code = '01'),
  lines AS (
    SELECT d.sku_id, o.warehouse_id, COALESCE(d.quality_id, (SELECT id FROM good)) AS quality_id,
           COALESCE(d.ordered_qty, 0) AS qty
      FROM orders o JOIN order_details d ON d.doc_id = o.doc_id
     WHERE p_type = 'orders' AND o.doc_id = p_doc AND d.batch_number IS NULL AND d.sku_id IS NOT NULL
    UNION ALL
    SELECT d.sku_id, t.from_warehouse_id, COALESCE(d.quality_id, (SELECT id FROM good)),
           COALESCE(d.qty, 0)
      FROM transfers t JOIN transfer_details d ON d.doc_id = t.doc_id
     WHERE p_type = 'transfers' AND t.doc_id = p_doc AND d.batch_number IS NULL AND d.sku_id IS NOT NULL
  ),
  -- комплект проверяется по комплектующим (V152): два комплекта с общей
  -- деталью и комплект плюс та же деталь отдельной строкой — одной суммой
  need AS (
    SELECT COALESCE(k.component_sku_id, l.sku_id) AS sku_id, l.warehouse_id, l.quality_id,
           sum(l.qty * COALESCE(k.qty, 1)) AS qty,
           string_agg(DISTINCT ks.code, ', ') AS kit_codes
      FROM lines l
      LEFT JOIN sku_kit_component k ON k.kit_sku_id = l.sku_id
      LEFT JOIN sku ks ON ks.sku_id = k.kit_sku_id
     GROUP BY 1, 2, 3
  )
  SELECT n.sku_id, s.code, s.name, n.warehouse_id,
         q.code, enum_name('enum_quality', q.id::text, q.name::text)::varchar,
         n.qty, COALESCE(b.available_qty, 0), n.qty - COALESCE(b.available_qty, 0), n.kit_codes
    FROM need n
    JOIN sku s          ON s.sku_id = n.sku_id
    JOIN enum_quality q ON q.id = n.quality_id
    LEFT JOIN stock_balance b
           ON b.depositor_id = v_dep AND b.sku_id = n.sku_id
          AND b.warehouse_id = n.warehouse_id AND b.quality_id = n.quality_id
   WHERE n.warehouse_id IS NOT NULL AND n.qty > COALESCE(b.available_qty, 0)
   ORDER BY s.code, q.code;
END
$$;
COMMENT ON FUNCTION stock_doc_shortage(text, uuid) IS
  'Строки заказа (перемещения), которым не хватает «Доступно»; комплекты — по комплектующим; берёт блокировку пересчёта клиента (V142, V152)';
REVOKE EXECUTE ON FUNCTION stock_doc_shortage(text, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION stock_doc_shortage(text, uuid) TO portal_app;

-- ---------------------------------------------------------------------
-- 5. «Запасы»: отметка комплекта
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_stock_lines WITH (security_invoker = true) AS
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
       a.quality_qty,
       EXISTS (SELECT 1 FROM sku_kit_component k WHERE k.kit_sku_id = a.sku_id) AS is_kit
  FROM v_stock_available a
  LEFT JOIN sku s       ON s.sku_id = a.sku_id
  LEFT JOIN warehouse w ON w.warehouse_id = a.warehouse_id
  LEFT JOIN enum_uom u  ON u.id = COALESCE(a.unit_id, s.uom_id);

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('stock.text.kit',          'stock', 'text', 'stock', '«Запасы»: отметка у строки комплекта (V152)'),
  ('stock.hint.kit_available', 'stock', 'hint', 'stock', '«Запасы»: что значит «Доступно» у комплекта (V152)'),
  ('stock.text.in_kits',      'stock', 'text', 'stock', 'окно нехватки: в каких комплектах заказа комплектующая (V152)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('stock.text.kit', 'ru', 'комплект'),
    ('stock.text.kit', 'en', 'kit'),
    ('stock.hint.kit_available', 'ru', 'Своего остатка у комплекта нет: «Доступно» — сколько комплектов можно собрать из свободных комплектующих'),
    ('stock.hint.kit_available', 'en', 'A kit has no stock of its own: “Available” is how many kits can be assembled from free components'),
    ('stock.text.in_kits', 'ru', 'в комплекте: {codes}'),
    ('stock.text.in_kits', 'en', 'in kit: {codes}')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- пересчитать то, что уже лежит: строки комплектов и резерв их комплектующих
SELECT stock_recalc(NULL);
