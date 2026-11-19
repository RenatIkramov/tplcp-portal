-- =====================================================================
-- V143. Кэш остатков: настройки интеграции и сверка остатков
--       (план «Кэш остатков», К7 — часть без документов WMS — и К5 —
--       всё, кроме самого запроса к WMS)
-- =====================================================================
-- Настройки слоя перевода (решение заказчика 15.11.2026: «нужен
-- интерфейс для настройки»):
--   wms_warehouse — адрес WMS склада. Склады в API WMS — разные порты,
--                   поэтому адрес с портом хранится по складу.
--   wms_depositor — код клиента в WMS.
--   wms_quality   — код блокировки WMS → качество портала (несколько
--                   кодов WMS могут вести в одно качество).
-- Логин и пароль к WMS здесь НЕ хранятся — это application.yml.
--
-- Сверка остатков:
--   stock_apply_snapshot — записать срез WMS по клиенту и складу:
--     сравнить с тем, что насчитал портал, расхождения — в журнал,
--     наличие подменить одним действием, пересчитать резерв и «к
--     получению». Строка среза — в формате портала: код товара портала,
--     код качества WMS, количество. В формат портала ответ WMS переводит
--     переводчик (WmsGateway) — его ещё нет, нужны актуальные документы.
--   Портал при сверке не блокируется (решение 14.11.2026): подмена —
--   одна транзакция, до неё люди видят прежние числа.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Настройки
-- ---------------------------------------------------------------------
CREATE TABLE wms_warehouse (
  warehouse_id varchar(20)  PRIMARY KEY REFERENCES warehouse (warehouse_id),
  base_url     varchar(255) NOT NULL CHECK (base_url ~ '^https?://[^[:space:]]+$')
);
COMMENT ON TABLE wms_warehouse IS 'Адрес WMS склада (с портом): склады в API WMS — разные порты (V143)';

CREATE TABLE wms_depositor (
  depositor_id uuid        PRIMARY KEY REFERENCES depositor (depositor_id),
  wms_code     varchar(20) NOT NULL UNIQUE CHECK (wms_code ~ '^[^[:space:]]+$')
);
COMMENT ON TABLE wms_depositor IS 'Код клиента в WMS (V143)';

CREATE TABLE wms_quality (
  wms_code   varchar(35) PRIMARY KEY CHECK (wms_code ~ '^[^[:space:]]+$'),
  quality_id smallint    NOT NULL REFERENCES enum_quality (id)
);
COMMENT ON TABLE wms_quality IS 'Код блокировки WMS → качество портала (V143)';

-- Из мэппинга полей ArtWMS (может устареть — правится на экране):
-- 010 не заблокировано, 020 карантин, 030 брак.
INSERT INTO wms_quality (wms_code, quality_id)
SELECT v.wms_code, q.id
  FROM (VALUES ('010', '01'), ('020', '04'), ('030', '02')) AS v(wms_code, quality_code)
  JOIN enum_quality q ON q.code = v.quality_code;

INSERT INTO portal_setting (setting_key, value, note) VALUES
  ('stock_sync_enabled', 'false', 'ночная сверка остатков с WMS: true / false'),
  ('stock_sync_time',    '02:30', 'время ночной сверки остатков, ЧЧ:ММ по часам сервера базы')
ON CONFLICT (setting_key) DO NOTHING;

-- Чтение для экрана администратора. Функцией владельца: список клиентов
-- под RLS администратору без выбранного клиента не виден.
CREATE FUNCTION admin_wms_settings()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT jsonb_build_object(
    'countTransfers', COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_count_transfers'), false),
    'syncEnabled',    COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_sync_enabled'), false),
    'syncTime',       (SELECT value FROM portal_setting WHERE setting_key = 'stock_sync_time'),
    'warehouses', COALESCE((SELECT jsonb_agg(jsonb_build_object('warehouseId', w.warehouse_id, 'name', w.name,
                                      'isActive', w.is_active, 'baseUrl', ww.base_url) ORDER BY w.warehouse_id)
                              FROM warehouse w LEFT JOIN wms_warehouse ww ON ww.warehouse_id = w.warehouse_id), '[]'),
    'depositors', COALESCE((SELECT jsonb_agg(jsonb_build_object('depositorId', d.depositor_id, 'code', d.code,
                                      'orgName', d.org_name, 'isActive', d.is_active, 'wmsCode', wd.wms_code) ORDER BY d.code)
                              FROM depositor d LEFT JOIN wms_depositor wd ON wd.depositor_id = d.depositor_id), '[]'),
    'qualities',  COALESCE((SELECT jsonb_agg(jsonb_build_object('wmsCode', wq.wms_code, 'qualityCode', q.code)
                                             ORDER BY wq.wms_code)
                              FROM wms_quality wq JOIN enum_quality q ON q.id = wq.quality_id), '[]'),
    'qualityOptions', COALESCE((SELECT jsonb_agg(jsonb_build_object('code', q.code,
                                      'name', enum_name('enum_quality', q.id::text, q.name::text))
                                      ORDER BY q.sort_order, q.id)
                                  FROM enum_quality q WHERE q.is_active), '[]'))
$$;

-- Настройки целиком — одним сохранением:
-- {countTransfers, syncEnabled, syncTime,
--  warehouses: [{warehouseId, baseUrl}], depositors: [{depositorId, wmsCode}],
--  qualities: [{wmsCode, qualityCode}]}
-- Пустой адрес или код — строки нет.
CREATE FUNCTION admin_wms_settings_save(p jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_transfers text := CASE WHEN COALESCE((p ->> 'countTransfers')::boolean, false) THEN 'true' ELSE 'false' END;
  v_changed   boolean;
BEGIN
  IF COALESCE(p ->> 'syncTime', '') !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$' THEN
    RAISE EXCEPTION 'wms settings: bad sync time %', p ->> 'syncTime';
  END IF;

  SELECT value <> v_transfers INTO v_changed FROM portal_setting WHERE setting_key = 'stock_count_transfers';
  UPDATE portal_setting SET value = v_transfers
   WHERE setting_key = 'stock_count_transfers' AND value <> v_transfers;
  UPDATE portal_setting
     SET value = CASE WHEN COALESCE((p ->> 'syncEnabled')::boolean, false) THEN 'true' ELSE 'false' END
   WHERE setting_key = 'stock_sync_enabled'
     AND value <> CASE WHEN COALESCE((p ->> 'syncEnabled')::boolean, false) THEN 'true' ELSE 'false' END;
  UPDATE portal_setting SET value = p ->> 'syncTime'
   WHERE setting_key = 'stock_sync_time' AND value <> p ->> 'syncTime';

  -- склады
  DELETE FROM wms_warehouse w
   WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(p -> 'warehouses', '[]')) e
                      WHERE e ->> 'warehouseId' = w.warehouse_id AND btrim(COALESCE(e ->> 'baseUrl', '')) <> '');
  INSERT INTO wms_warehouse (warehouse_id, base_url)
  SELECT e ->> 'warehouseId', rtrim(btrim(e ->> 'baseUrl'), '/')
    FROM jsonb_array_elements(COALESCE(p -> 'warehouses', '[]')) e
   WHERE btrim(COALESCE(e ->> 'baseUrl', '')) <> ''
  ON CONFLICT (warehouse_id) DO UPDATE SET base_url = EXCLUDED.base_url
    WHERE wms_warehouse.base_url <> EXCLUDED.base_url;

  -- клиенты: сначала убрать, потом записать — коды могут меняться местами
  DELETE FROM wms_depositor d
   WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(p -> 'depositors', '[]')) e
                      WHERE (e ->> 'depositorId')::uuid = d.depositor_id
                        AND btrim(COALESCE(e ->> 'wmsCode', '')) = d.wms_code);
  INSERT INTO wms_depositor (depositor_id, wms_code)
  SELECT (e ->> 'depositorId')::uuid, btrim(e ->> 'wmsCode')
    FROM jsonb_array_elements(COALESCE(p -> 'depositors', '[]')) e
   WHERE btrim(COALESCE(e ->> 'wmsCode', '')) <> ''
  ON CONFLICT (depositor_id) DO NOTHING;

  -- качество
  DELETE FROM wms_quality q
   WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(p -> 'qualities', '[]')) e
                      WHERE btrim(COALESCE(e ->> 'wmsCode', '')) = q.wms_code);
  INSERT INTO wms_quality (wms_code, quality_id)
  SELECT btrim(e ->> 'wmsCode'), eq.id
    FROM jsonb_array_elements(COALESCE(p -> 'qualities', '[]')) e
    JOIN enum_quality eq ON eq.code = e ->> 'qualityCode'
   WHERE btrim(COALESCE(e ->> 'wmsCode', '')) <> ''
  ON CONFLICT (wms_code) DO UPDATE SET quality_id = EXCLUDED.quality_id
    WHERE wms_quality.quality_id <> EXCLUDED.quality_id;

  IF v_changed THEN
    PERFORM stock_recalc(NULL);
  END IF;
END
$$;

-- ---------------------------------------------------------------------
-- 2. Журнал сверок
-- ---------------------------------------------------------------------
ALTER TABLE sync_run ADD COLUMN warehouse_id varchar(20) REFERENCES warehouse (warehouse_id);

CREATE TABLE stock_sync_diff (
  sync_run_id  uuid          NOT NULL REFERENCES sync_run (sync_run_id) ON DELETE CASCADE,
  sku_id       uuid          NOT NULL REFERENCES sku (sku_id),
  quality_id   smallint      NOT NULL REFERENCES enum_quality (id),
  portal_qty   numeric(14,3) NOT NULL,
  wms_qty      numeric(14,3) NOT NULL,
  PRIMARY KEY (sync_run_id, sku_id, quality_id)
);
COMMENT ON TABLE stock_sync_diff IS
  'Расхождения сверки остатков: что насчитал портал и что пришло из WMS (V143)';
-- Читает только администратор — функцией; порталу напрямую незачем.
REVOKE ALL ON stock_sync_diff FROM portal_app;

-- ---------------------------------------------------------------------
-- 3. Сверка
-- ---------------------------------------------------------------------
-- p_rows: [{"sku": код товара портала, "quality": код качества WMS, "qty": число}]
-- Неизвестный товар или код качества — строка пропускается и попадает
-- в текст запуска; наличие по остальным всё равно записывается.
CREATE FUNCTION stock_snapshot_rows(p_depositor uuid, p_rows jsonb)
RETURNS TABLE (sku_code text, wms_quality text, qty numeric, sku_id uuid, quality_id smallint)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT e ->> 'sku', e ->> 'quality', (e ->> 'qty')::numeric, s.sku_id, wq.quality_id
    FROM jsonb_array_elements(COALESCE(p_rows, '[]')) e
    LEFT JOIN sku s          ON s.depositor_id = p_depositor AND s.code = e ->> 'sku'
    LEFT JOIN wms_quality wq ON wq.wms_code = e ->> 'quality'
$$;

CREATE FUNCTION stock_apply_snapshot(p_depositor uuid, p_warehouse text, p_rows jsonb, p_trigger text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_run      uuid;
  v_received integer;
  v_applied  integer;
  v_unknown  text;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM depositor WHERE depositor_id = p_depositor)
     OR NOT EXISTS (SELECT 1 FROM warehouse WHERE warehouse_id = p_warehouse) THEN
    RAISE EXCEPTION 'stock_apply_snapshot: unknown depositor or warehouse';
  END IF;

  PERFORM pg_advisory_xact_lock(141, hashtext(p_depositor::text));

  INSERT INTO sync_run (entity, depositor_id, warehouse_id, direction, trigger_source, status)
  VALUES ('stock', p_depositor, p_warehouse, '02', p_trigger, '01')
  RETURNING sync_run_id INTO v_run;

  SELECT count(*), count(*) FILTER (WHERE r.sku_id IS NOT NULL AND r.quality_id IS NOT NULL)
    INTO v_received, v_applied
    FROM stock_snapshot_rows(p_depositor, p_rows) r;

  SELECT string_agg(DISTINCT x.what, ', ') INTO v_unknown
    FROM (SELECT CASE WHEN r.sku_id IS NULL THEN 'sku ' || COALESCE(r.sku_code, '?')
                      ELSE 'quality ' || COALESCE(r.wms_quality, '?') END AS what
            FROM stock_snapshot_rows(p_depositor, p_rows) r
           WHERE r.sku_id IS NULL OR r.quality_id IS NULL
           LIMIT 20) x;

  -- расхождения: наличие портала против среза
  INSERT INTO stock_sync_diff (sync_run_id, sku_id, quality_id, portal_qty, wms_qty)
  SELECT v_run, COALESCE(b.sku_id, w.sku_id), COALESCE(b.quality_id, w.quality_id),
         COALESCE(b.on_hand_qty, 0), COALESCE(w.qty, 0)
    FROM (SELECT sb.sku_id, sb.quality_id, sb.on_hand_qty FROM stock_balance sb
           WHERE sb.depositor_id = p_depositor AND sb.warehouse_id = p_warehouse) b
    FULL JOIN (SELECT r.sku_id, r.quality_id, sum(r.qty) AS qty
                 FROM stock_snapshot_rows(p_depositor, p_rows) r
                WHERE r.sku_id IS NOT NULL AND r.quality_id IS NOT NULL GROUP BY 1, 2) w
           ON w.sku_id = b.sku_id AND w.quality_id = b.quality_id
   WHERE COALESCE(b.on_hand_qty, 0) <> COALESCE(w.qty, 0);

  -- подмена наличия
  UPDATE stock_balance SET base_qty = 0, base_at = now()
   WHERE depositor_id = p_depositor AND warehouse_id = p_warehouse;
  INSERT INTO stock_balance (depositor_id, sku_id, warehouse_id, quality_id, base_qty, base_at)
  SELECT p_depositor, r.sku_id, p_warehouse, r.quality_id, sum(r.qty), now()
    FROM stock_snapshot_rows(p_depositor, p_rows) r
   WHERE r.sku_id IS NOT NULL AND r.quality_id IS NOT NULL
   GROUP BY r.sku_id, r.quality_id
  ON CONFLICT (depositor_id, sku_id, warehouse_id, quality_id)
  DO UPDATE SET base_qty = EXCLUDED.base_qty, base_at = EXCLUDED.base_at;

  PERFORM stock_recalc(p_depositor, NULL);

  UPDATE sync_run
     SET finished_at = clock_timestamp(), rows_received = v_received, rows_applied = v_applied,
         error_message = CASE WHEN v_unknown IS NOT NULL THEN 'unknown: ' || v_unknown END
   WHERE sync_run_id = v_run;
  RETURN v_run;
END
$$;
COMMENT ON FUNCTION stock_apply_snapshot(uuid, text, jsonb, text) IS
  'Сверка остатков: срез WMS по клиенту и складу → расхождения в журнал, наличие подменено, пересчёт (V143)';

-- Сверка не состоялась: WMS не ответила или данных не дала.
CREATE FUNCTION stock_sync_failed(p_depositor uuid, p_warehouse text, p_trigger text, p_error text)
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  INSERT INTO sync_run (entity, depositor_id, warehouse_id, direction, trigger_source, status,
                        finished_at, error_message)
  VALUES ('stock', p_depositor, p_warehouse, '02', p_trigger, '02', clock_timestamp(), left(p_error, 2000))
  RETURNING sync_run_id
$$;

-- Что сверять: клиенты с кодом WMS × склады с адресом WMS.
CREATE FUNCTION stock_sync_targets()
RETURNS TABLE (depositor_id uuid, depositor_wms_code varchar, warehouse_id varchar, base_url varchar)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT d.depositor_id, wd.wms_code, w.warehouse_id, ww.base_url
    FROM wms_depositor wd
    JOIN depositor d ON d.depositor_id = wd.depositor_id AND d.is_active
    CROSS JOIN wms_warehouse ww
    JOIN warehouse w ON w.warehouse_id = ww.warehouse_id AND w.is_active
   ORDER BY d.code, w.warehouse_id
$$;

-- Пора ли ночной сверке: включена, время настало, сегодня по расписанию ещё не шла.
CREATE FUNCTION stock_sync_due()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_sync_enabled'), false)
     AND localtime >= COALESCE((SELECT value::time FROM portal_setting WHERE setting_key = 'stock_sync_time'), '02:30')
     AND NOT EXISTS (SELECT 1 FROM sync_run
                      WHERE entity = 'stock' AND trigger_source = 'schedule' AND started_at >= current_date)
$$;

-- Журнал сверок для экрана администратора.
CREATE FUNCTION admin_stock_sync_log(p_limit integer DEFAULT 100)
RETURNS TABLE (sync_run_id uuid, started_at timestamptz, finished_at timestamptz, trigger_source varchar,
               depositor_code varchar, depositor_name varchar, warehouse_id varchar,
               status_code varchar, status_name varchar, rows_received integer, rows_applied integer,
               diff_count bigint, error_message text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT r.sync_run_id, r.started_at, r.finished_at, r.trigger_source,
         d.code, d.org_name, r.warehouse_id,
         r.status, enum_name('enum_sync_status', st.id::text, st.name::text)::varchar,
         r.rows_received, r.rows_applied,
         (SELECT count(*) FROM stock_sync_diff x WHERE x.sync_run_id = r.sync_run_id),
         r.error_message
    FROM sync_run r
    LEFT JOIN depositor d        ON d.depositor_id = r.depositor_id
    LEFT JOIN enum_sync_status st ON st.code = r.status
   WHERE r.entity = 'stock'
   ORDER BY r.started_at DESC
   LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 100), 500))
$$;

CREATE FUNCTION admin_stock_sync_diff(p_run uuid)
RETURNS TABLE (sku_code varchar, sku_name varchar, quality_name varchar,
               portal_qty numeric, wms_qty numeric)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT s.code, s.name, enum_name('enum_quality', q.id::text, q.name::text)::varchar, x.portal_qty, x.wms_qty
    FROM stock_sync_diff x
    JOIN sku s          ON s.sku_id = x.sku_id
    JOIN enum_quality q ON q.id = x.quality_id
   WHERE x.sync_run_id = p_run
   ORDER BY s.code, q.code
$$;

REVOKE EXECUTE ON FUNCTION stock_snapshot_rows(uuid, jsonb) FROM PUBLIC, portal_app;
REVOKE EXECUTE ON FUNCTION admin_wms_settings(), admin_wms_settings_save(jsonb), stock_apply_snapshot(uuid, text, jsonb, text),
  stock_sync_failed(uuid, text, text, text), stock_sync_targets(), stock_sync_due(),
  admin_stock_sync_log(integer), admin_stock_sync_diff(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_wms_settings(), admin_wms_settings_save(jsonb), stock_apply_snapshot(uuid, text, jsonb, text),
  stock_sync_failed(uuid, text, text, text), stock_sync_targets(), stock_sync_due(),
  admin_stock_sync_log(integer), admin_stock_sync_diff(uuid) TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.wms_settings.sync_time',  'server', 'message', 'integration', 'время сверки не ЧЧ:ММ (V143)'),
  ('error.wms_settings.bad_url',    'server', 'message', 'integration', 'адрес WMS склада не http(s)://… (V143)'),
  ('error.wms_settings.bad_code',   'server', 'message', 'integration', 'код WMS с пробелом или повторяется (V143)'),
  ('error.stock_sync.no_targets',   'server', 'message', 'integration', 'сверять нечего: нет клиента с кодом WMS или склада с адресом (V143)'),
  ('error.stock_sync.no_data',      'server', 'message', 'integration', 'WMS не дала данных для сверки (V143)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.text_key, 'admin', v.element, 'integration', v.note
  FROM (VALUES
    ('admin.button.integration',            'button', 'группа вкладок «Интеграция»'),
    ('integration.title.stock',             'title',  'блок: остатки'),
    ('integration.label.count_transfers',   'label',  'учитывать перемещения в остатках'),
    ('integration.hint.count_transfers',    'hint',   'пояснение к учёту перемещений'),
    ('integration.label.sync_enabled',      'label',  'ночная сверка включена'),
    ('integration.label.sync_time',         'label',  'время сверки'),
    ('integration.title.warehouses',        'title',  'блок: адреса WMS складов'),
    ('integration.hint.warehouses',         'hint',   'пояснение к адресам'),
    ('integration.column.wms_url',          'column', 'адрес WMS'),
    ('integration.title.depositors',        'title',  'блок: коды клиентов в WMS'),
    ('integration.column.wms_code',         'column', 'код в WMS'),
    ('integration.title.qualities',         'title',  'блок: коды качества WMS'),
    ('integration.hint.qualities',          'hint',   'пояснение к кодам качества'),
    ('integration.column.portal_quality',   'column', 'качество на портале'),
    ('integration.button.add_code',         'button', 'добавить код качества'),
    ('integration.message.saved',           'message','настройки сохранены'),
    ('integration.title.sync_log',          'title',  'блок: журнал сверок'),
    ('integration.hint.sync_log',           'hint',   'пояснение к журналу сверок'),
    ('integration.button.sync_now',         'button', 'сверить сейчас'),
    ('integration.button.syncing',          'button', 'идёт сверка'),
    ('integration.text.no_runs',            'text',   'сверок ещё не было'),
    ('integration.column.started',          'column', 'начало сверки'),
    ('integration.column.trigger',          'column', 'как запущена'),
    ('integration.text.trigger_schedule',   'text',   'по расписанию'),
    ('integration.text.trigger_manual',     'text',   'вручную'),
    ('integration.column.rows',             'column', 'строк получено / записано'),
    ('integration.column.differences',      'column', 'расхождений'),
    ('integration.column.portal_qty',       'column', 'насчитал портал'),
    ('integration.column.wms_qty',          'column', 'пришло из WMS'),
    ('integration.message.sync_done',       'message','сверка завершена')
  ) AS v(text_key, element, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.wms_settings.sync_time', 'ru', 'Время сверки укажите как ЧЧ:ММ, например 02:30.'),
    ('error.wms_settings.sync_time', 'en', 'Enter the reconciliation time as HH:MM, for example 02:30.'),
    ('error.wms_settings.bad_url', 'ru', 'Адрес WMS склада {warehouse} должен начинаться с http:// или https:// и не содержать пробелов.'),
    ('error.wms_settings.bad_url', 'en', 'The WMS address of warehouse {warehouse} must start with http:// or https:// and contain no spaces.'),
    ('error.wms_settings.bad_code', 'ru', 'Код WMS «{code}» содержит пробел или указан дважды.'),
    ('error.wms_settings.bad_code', 'en', 'WMS code "{code}" contains a space or is used twice.'),
    ('error.stock_sync.no_targets', 'ru', 'Сверять нечего: нужен хотя бы один клиент с кодом WMS и один склад с адресом WMS.'),
    ('error.stock_sync.no_targets', 'en', 'Nothing to reconcile: at least one client with a WMS code and one warehouse with a WMS address are needed.'),
    ('error.stock_sync.no_data', 'ru', 'WMS не дала данных'),
    ('error.stock_sync.no_data', 'en', 'WMS returned no data'),
    ('admin.button.integration', 'ru', 'Интеграция'),
    ('admin.button.integration', 'en', 'Integration'),
    ('integration.title.stock', 'ru', 'Остатки'),
    ('integration.title.stock', 'en', 'Stock'),
    ('integration.label.count_transfers', 'ru', 'Учитывать перемещения в остатках'),
    ('integration.label.count_transfers', 'en', 'Count transfers in stock'),
    ('integration.hint.count_transfers', 'ru', 'Подтверждённое перемещение резервирует товар на складе-отправителе и показывается «к получению» на складе-получателе. При подтверждении проверяется, хватает ли товара.'),
    ('integration.hint.count_transfers', 'en', 'A confirmed transfer reserves goods at the source warehouse and shows as incoming at the destination. Availability is checked on confirmation.'),
    ('integration.label.sync_enabled', 'ru', 'Сверять остатки с WMS каждую ночь'),
    ('integration.label.sync_enabled', 'en', 'Reconcile stock with WMS every night'),
    ('integration.label.sync_time', 'ru', 'Время сверки'),
    ('integration.label.sync_time', 'en', 'Reconciliation time'),
    ('integration.title.warehouses', 'ru', 'Склады: адрес WMS'),
    ('integration.title.warehouses', 'en', 'Warehouses: WMS address'),
    ('integration.hint.warehouses', 'ru', 'Адрес с портом, например http://192.168.1.10:13515. У каждого склада свой порт. Склад без адреса с WMS не сверяется.'),
    ('integration.hint.warehouses', 'en', 'Address with port, for example http://192.168.1.10:13515. Each warehouse has its own port. A warehouse without an address is not reconciled.'),
    ('integration.column.wms_url', 'ru', 'Адрес WMS'),
    ('integration.column.wms_url', 'en', 'WMS address'),
    ('integration.title.depositors', 'ru', 'Поклажедатели: код в WMS'),
    ('integration.title.depositors', 'en', 'Depositors: WMS code'),
    ('integration.column.wms_code', 'ru', 'Код в WMS'),
    ('integration.column.wms_code', 'en', 'WMS code'),
    ('integration.title.qualities', 'ru', 'Качество: коды WMS'),
    ('integration.title.qualities', 'en', 'Quality: WMS codes'),
    ('integration.hint.qualities', 'ru', 'Каким качеством портала считать код блокировки WMS. Несколько кодов WMS могут вести в одно качество. Строка с неизвестным кодом при сверке пропускается.'),
    ('integration.hint.qualities', 'en', 'Which portal quality a WMS blocking code means. Several WMS codes may map to one quality. A row with an unknown code is skipped during reconciliation.'),
    ('integration.column.portal_quality', 'ru', 'Качество на портале'),
    ('integration.column.portal_quality', 'en', 'Portal quality'),
    ('integration.button.add_code', 'ru', '+ Добавить код'),
    ('integration.button.add_code', 'en', '+ Add code'),
    ('integration.message.saved', 'ru', 'Настройки интеграции сохранены'),
    ('integration.message.saved', 'en', 'Integration settings saved'),
    ('integration.title.sync_log', 'ru', 'Сверки остатков'),
    ('integration.title.sync_log', 'en', 'Stock reconciliations'),
    ('integration.hint.sync_log', 'ru', 'Сверка берёт остатки из WMS, сравнивает с тем, что насчитал портал, и записывает числа WMS. Расхождений быть не должно: они значат, что портал пропустил событие склада.'),
    ('integration.hint.sync_log', 'en', 'A reconciliation takes stock from WMS, compares it with what the portal calculated and stores the WMS numbers. There should be no differences: they mean the portal missed a warehouse event.'),
    ('integration.button.sync_now', 'ru', 'Сверить сейчас'),
    ('integration.button.sync_now', 'en', 'Reconcile now'),
    ('integration.button.syncing', 'ru', 'Идёт сверка…'),
    ('integration.button.syncing', 'en', 'Reconciling…'),
    ('integration.text.no_runs', 'ru', 'Сверок ещё не было.'),
    ('integration.text.no_runs', 'en', 'No reconciliations yet.'),
    ('integration.column.started', 'ru', 'Начало'),
    ('integration.column.started', 'en', 'Started'),
    ('integration.column.trigger', 'ru', 'Запуск'),
    ('integration.column.trigger', 'en', 'Trigger'),
    ('integration.text.trigger_schedule', 'ru', 'по расписанию'),
    ('integration.text.trigger_schedule', 'en', 'scheduled'),
    ('integration.text.trigger_manual', 'ru', 'вручную'),
    ('integration.text.trigger_manual', 'en', 'manual'),
    ('integration.column.rows', 'ru', 'Строк получено / записано'),
    ('integration.column.rows', 'en', 'Rows received / applied'),
    ('integration.column.differences', 'ru', 'Расхождений'),
    ('integration.column.differences', 'en', 'Differences'),
    ('integration.column.portal_qty', 'ru', 'Насчитал портал'),
    ('integration.column.portal_qty', 'en', 'Portal calculated'),
    ('integration.column.wms_qty', 'ru', 'Пришло из WMS'),
    ('integration.column.wms_qty', 'en', 'Received from WMS'),
    ('integration.message.sync_done', 'ru', 'Сверка завершена'),
    ('integration.message.sync_done', 'en', 'Reconciliation finished')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
