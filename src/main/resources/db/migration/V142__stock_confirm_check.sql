-- =====================================================================
-- V142. Кэш остатков, поставка К2: проверка при подтверждении
-- =====================================================================
-- Решение заказчика 14.11.2026: заказ сверх «Доступно» без проверки
-- не проходит. Портал показывает, чего не хватает, и даёт кнопку
-- «Проверить актуальные остатки» — она спрашивает WMS. Хватает —
-- заказ уходит, не хватает — отказ.
--
--   stock_doc_shortage   — каких строк документа не хватает по «Доступно».
--                          Берёт ту же блокировку клиента, что и пересчёт:
--                          пока «Подтвердить» не закончилось, второй заказ
--                          на тот же остаток ждёт и видит уже новый резерв.
--   stock_apply_wms_free — записать ответ WMS «свободно для резерва».
--                          Свободное WMS = наличие − резерв заказов,
--                          которые WMS уже знает (статус 10 и дальше).
--                          Отсюда наличие = свободное + этот резерв, а
--                          «Доступно» портала выходит свободным WMS минус
--                          заказы, до WMS ещё не дошедшие (04, 02).
--
-- Правило резерва по-прежнему в одном месте — stock_open_documents;
-- она теперь отдаёт ещё и резерв, известный WMS.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Правило: плюс «резерв, известный WMS»
-- ---------------------------------------------------------------------
DROP FUNCTION stock_open_documents(uuid, uuid[]);

CREATE FUNCTION stock_open_documents(p_depositor uuid, p_skus uuid[])
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
    ) x
   WHERE x.sku_id IS NOT NULL AND x.warehouse_id IS NOT NULL
     AND (p_skus IS NULL OR x.sku_id = ANY (p_skus))
   GROUP BY x.sku_id, x.warehouse_id, x.quality_id;
END
$$;
COMMENT ON FUNCTION stock_open_documents(uuid, uuid[]) IS
  'Резерв, «к получению» и резерв, известный WMS (статус 10+), по открытым документам — единственное место правила (V141, V142)';
REVOKE EXECUTE ON FUNCTION stock_open_documents(uuid, uuid[]) FROM PUBLIC, portal_app;

-- ---------------------------------------------------------------------
-- 2. Чего не хватает документу
-- ---------------------------------------------------------------------
-- Права вызывающего: документ и остатки читаются под RLS, чужого
-- клиента функция не увидит. Заказ проверяется всегда, перемещение —
-- если его учёт в остатках включён; поступление товар не расходует.
CREATE FUNCTION stock_doc_shortage(p_type text, p_doc uuid)
RETURNS TABLE (sku_id uuid, sku_code varchar, sku_name varchar, warehouse_id varchar,
               quality_code varchar, quality_name varchar,
               required_qty numeric, available_qty numeric, shortage_qty numeric)
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
  need AS (
    SELECT d.sku_id, o.warehouse_id, COALESCE(d.quality_id, (SELECT id FROM good)) AS quality_id,
           sum(COALESCE(d.ordered_qty, 0)) AS qty
      FROM orders o JOIN order_details d ON d.doc_id = o.doc_id
     WHERE p_type = 'orders' AND o.doc_id = p_doc AND d.batch_number IS NULL AND d.sku_id IS NOT NULL
     GROUP BY 1, 2, 3
    UNION ALL
    SELECT d.sku_id, t.from_warehouse_id, COALESCE(d.quality_id, (SELECT id FROM good)),
           sum(COALESCE(d.qty, 0))
      FROM transfers t JOIN transfer_details d ON d.doc_id = t.doc_id
     WHERE p_type = 'transfers' AND t.doc_id = p_doc AND d.batch_number IS NULL AND d.sku_id IS NOT NULL
     GROUP BY 1, 2, 3
  )
  SELECT n.sku_id, s.code, s.name, n.warehouse_id,
         q.code, enum_name('enum_quality', q.id::text, q.name::text)::varchar,
         n.qty, COALESCE(b.available_qty, 0), n.qty - COALESCE(b.available_qty, 0)
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
  'Строки заказа (перемещения), которым не хватает «Доступно»; берёт блокировку пересчёта клиента (V142)';
REVOKE EXECUTE ON FUNCTION stock_doc_shortage(text, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION stock_doc_shortage(text, uuid) TO portal_app;

-- ---------------------------------------------------------------------
-- 3. Ответ WMS «свободно для резерва»
-- ---------------------------------------------------------------------
CREATE FUNCTION stock_apply_wms_free(p_sku uuid, p_warehouse text, p_quality_code text, p_free numeric)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep     uuid := current_depositor_id();
  v_quality smallint;
  v_known   numeric;
BEGIN
  IF v_dep IS NULL THEN
    RAISE EXCEPTION 'stock_apply_wms_free: no depositor in context';
  END IF;
  IF p_free IS NULL OR p_free < 0 THEN
    RAISE EXCEPTION 'stock_apply_wms_free: bad quantity %', p_free;
  END IF;
  SELECT id INTO v_quality FROM enum_quality WHERE code = p_quality_code;
  IF v_quality IS NULL
     OR NOT EXISTS (SELECT 1 FROM sku WHERE sku_id = p_sku AND depositor_id = v_dep)
     OR NOT EXISTS (SELECT 1 FROM warehouse WHERE warehouse_id = p_warehouse) THEN
    RAISE EXCEPTION 'stock_apply_wms_free: unknown item, warehouse or quality';
  END IF;

  PERFORM pg_advisory_xact_lock(141, hashtext(v_dep::text));

  SELECT COALESCE(sum(w.reserved_wms), 0) INTO v_known
    FROM stock_open_documents(v_dep, ARRAY[p_sku]) w
   WHERE w.warehouse_id = p_warehouse AND w.quality_id = v_quality;

  INSERT INTO stock_balance (depositor_id, sku_id, warehouse_id, quality_id, base_qty, base_at)
  VALUES (v_dep, p_sku, p_warehouse, v_quality, p_free + v_known, now())
  ON CONFLICT (depositor_id, sku_id, warehouse_id, quality_id)
  DO UPDATE SET base_qty = EXCLUDED.base_qty, base_at = EXCLUDED.base_at;

  PERFORM stock_recalc(v_dep, ARRAY[p_sku]);
END
$$;
COMMENT ON FUNCTION stock_apply_wms_free(uuid, text, text, numeric) IS
  'Записать ответ WMS «свободно для резерва» по товару, складу и качеству своего клиента и пересчитать (V142)';
REVOKE EXECUTE ON FUNCTION stock_apply_wms_free(uuid, text, text, numeric) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION stock_apply_wms_free(uuid, text, text, numeric) TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.stock.not_enough',         'server', 'message', 'stock', 'подтверждение: по данным портала товара не хватает (V142)'),
  ('error.stock.not_enough_checked', 'server', 'message', 'stock', 'подтверждение: WMS подтвердила, что товара не хватает (V142)'),
  ('error.stock.check_unavailable',  'server', 'message', 'stock', 'подтверждение: WMS не ответила на проверку остатков (V142)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.text_key, 'common', v.element, 'stock', v.note
  FROM (VALUES
    ('stock.title.not_enough',        'title',  'окно нехватки при подтверждении'),
    ('stock.hint.not_enough',         'hint',   'окно нехватки: что делать'),
    ('stock.hint.not_enough_checked', 'hint',   'окно нехватки: WMS подтвердила'),
    ('stock.button.check_wms',        'button', 'проверить актуальные остатки в WMS'),
    ('stock.button.checking',         'button', 'идёт проверка в WMS'),
    ('stock.column.required',         'column', 'окно нехватки: сколько нужно документу'),
    ('stock.column.shortage',         'column', 'окно нехватки: сколько не хватает'),
    ('stock.column.quality',          'column', 'окно нехватки: качество')
  ) AS v(text_key, element, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.stock.not_enough', 'ru', 'Товара не хватает: {items}. Проверьте актуальные остатки.'),
    ('error.stock.not_enough', 'en', 'Not enough stock: {items}. Check the current stock.'),
    ('error.stock.not_enough_checked', 'ru', 'Склад подтвердил: товара не хватает — {items}. Уменьшите количество в документе.'),
    ('error.stock.not_enough_checked', 'en', 'The warehouse confirmed: not enough stock — {items}. Reduce the quantity in the document.'),
    ('error.stock.check_unavailable', 'ru', 'Проверить остатки сейчас нельзя: склад не отвечает. Документ остался черновиком, попробуйте позже.'),
    ('error.stock.check_unavailable', 'en', 'Stock cannot be checked right now: the warehouse is not responding. The document is still a draft, try again later.'),
    ('stock.title.not_enough', 'ru', 'Товара не хватает'),
    ('stock.title.not_enough', 'en', 'Not enough stock'),
    ('stock.hint.not_enough', 'ru', 'По данным портала доступного количества меньше, чем в документе. Данные могли устареть — проверьте актуальные остатки на складе. Если товара хватает, документ будет отправлен сразу.'),
    ('stock.hint.not_enough', 'en', 'According to the portal, less is available than the document requires. The data may be out of date — check the current stock at the warehouse. If there is enough, the document is sent at once.'),
    ('stock.hint.not_enough_checked', 'ru', 'Склад подтвердил: товара не хватает. Уменьшите количество в документе или дождитесь поступления.'),
    ('stock.hint.not_enough_checked', 'en', 'The warehouse confirmed there is not enough stock. Reduce the quantity in the document or wait for a receipt.'),
    ('stock.button.check_wms', 'ru', 'Проверить актуальные остатки'),
    ('stock.button.check_wms', 'en', 'Check current stock'),
    ('stock.button.checking', 'ru', 'Проверяем…'),
    ('stock.button.checking', 'en', 'Checking…'),
    ('stock.column.required', 'ru', 'В документе'),
    ('stock.column.required', 'en', 'In the document'),
    ('stock.column.shortage', 'ru', 'Не хватает'),
    ('stock.column.shortage', 'en', 'Short by'),
    ('stock.column.quality', 'ru', 'Качество'),
    ('stock.column.quality', 'en', 'Quality')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
