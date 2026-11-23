SET search_path = dbo;

-- =====================================================================
-- V148. Журнал действий: каталог отчётов и сопоставления с WMS
-- =====================================================================
-- Правки каталога отчётов (V135–V139) и настроек интеграции (V143) шли
-- мимо журнала. Теперь их пишут те же триггеры audit_row():
-- * отчёт, его названия, параметры и подписи параметров — объект
--   «Отчёт» (история — в окне отчёта каталога);
-- * отчёты клиента — у поклажедателя, частью «отчёт «код»» (решение
--   заказчика 19.11.2026: это настройка клиента);
-- * адрес WMS склада и код клиента в WMS — частью «сопоставление с WMS»
--   карточки склада и поклажедателя;
-- * качество WMS — настройкой портала «Качество WMS», часть на код WMS.
-- Настройки портала из V138/V143/V145 журналировались и раньше, но в
-- журнале показывались ключом: добавлены их подписи.

CREATE OR REPLACE FUNCTION audit_row()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'dbo', 'pg_temp'
AS $function$
DECLARE
  v_actor  text := NULLIF(current_setting('app.actor', true), '');
  o        jsonb;
  n        jsonb;
  r        jsonb;
  kinds    jsonb;
  ch       jsonb := '{}';
  k        text;
  kd       text;
  ov       jsonb;
  nv       jsonb;
  v_type   text;
  v_id     text;
  v_label  text;
  v_dep    uuid;
  v_part   text;
  v_action text := lower(TG_OP);
  v_code   text;
BEGIN
  -- Не действие человека в портале (миграция, psql, демо-данные) — не пишем.
  IF v_actor IS NULL THEN
    RETURN NULL;
  END IF;
  IF TG_OP <> 'INSERT' THEN o := to_jsonb(OLD); END IF;
  IF TG_OP <> 'DELETE' THEN n := to_jsonb(NEW); END IF;
  r := COALESCE(n, o);
  IF v_action = 'insert' THEN v_action := 'create'; END IF;

  SELECT jsonb_object_agg(column_name, kind) INTO kinds
    FROM audit_field WHERE source_table = TG_TABLE_NAME;

  FOR k IN SELECT jsonb_object_keys(r) LOOP
    kd := COALESCE(kinds ->> k, 'text');
    CONTINUE WHEN kd = 'skip';
    ov := NULLIF(o -> k, 'null'::jsonb);
    nv := NULLIF(n -> k, 'null'::jsonb);
    CONTINUE WHEN ov IS NOT DISTINCT FROM nv;
    IF kd = 'secret' THEN
      -- что пароль сменили — видно, сам он (даже хэш) в журнал не идёт
      ov := CASE WHEN ov IS NULL THEN NULL ELSE '"***"'::jsonb END;
      nv := CASE WHEN nv IS NULL THEN NULL ELSE '"***"'::jsonb END;
    END IF;
    ch := ch || jsonb_build_object(k, jsonb_strip_nulls(jsonb_build_object('o', ov, 'n', nv)));
  END LOOP;

  IF TG_OP = 'UPDATE' AND ch = '{}'::jsonb THEN
    RETURN NULL;   -- сохранили без изменений
  END IF;

  -- Чей это объект: строка документа — документа, адрес — грузополучателя,
  -- доступы и права — пользователя, настройки клиента — поклажедателя.
  CASE TG_TABLE_NAME
    WHEN 'receipts', 'orders', 'transfers' THEN
      v_type := TG_TABLE_NAME;
      v_id := r ->> 'doc_id';
      v_label := r ->> 'document_no';
      v_dep := (r ->> 'depositor_id')::uuid;
      IF TG_OP = 'UPDATE' AND ch ? 'status_id' THEN v_action := 'status'; END IF;
    WHEN 'receipt_details', 'order_details', 'transfer_details' THEN
      v_type := CASE TG_TABLE_NAME WHEN 'receipt_details' THEN 'receipts'
                                   WHEN 'order_details' THEN 'orders' ELSE 'transfers' END;
      v_id := r ->> 'doc_id';
      EXECUTE format('SELECT document_no, depositor_id FROM %I WHERE doc_id = $1', v_type)
         INTO v_label, v_dep USING (r ->> 'doc_id')::uuid;
      -- Документ удаляют вместе со строками: удаление записано у шапки.
      IF v_dep IS NULL THEN RETURN NULL; END IF;
      v_part := 'line:' || (r ->> 'line_number');
    WHEN 'sku' THEN
      v_type := 'sku'; v_id := r ->> 'sku_id'; v_label := r ->> 'code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'product_packaging' THEN
      v_type := 'packaging'; v_id := r ->> 'packaging_id'; v_label := r ->> 'packaging_code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'supplier', 'consignee' THEN
      v_type := TG_TABLE_NAME; v_id := r ->> (TG_TABLE_NAME || '_id'); v_label := r ->> 'code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'delivery_point' THEN
      v_type := 'consignee'; v_id := r ->> 'consignee_id';
      SELECT c.code, c.depositor_id INTO v_label, v_dep
        FROM consignee c WHERE c.consignee_id = (r ->> 'consignee_id')::uuid;
      IF v_dep IS NULL THEN RETURN NULL; END IF;
      v_part := 'delivery_point:' || COALESCE(r ->> 'address', '');
    WHEN 'warehouse' THEN
      v_type := 'warehouse'; v_id := r ->> 'warehouse_id'; v_label := r ->> 'name';
    WHEN 'depositor' THEN
      v_type := 'depositor'; v_id := r ->> 'depositor_id'; v_label := r ->> 'code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'operator' THEN
      v_type := 'operator'; v_id := r ->> 'operator_id'; v_label := r ->> 'code';
    WHEN 'app_user' THEN
      v_type := 'app_user'; v_id := r ->> 'user_id'; v_label := r ->> 'login';
    WHEN 'user_settings', 'user_depositor_access', 'user_warehouse_access' THEN
      v_type := 'app_user'; v_id := r ->> 'user_id';
      SELECT login INTO v_label FROM app_user WHERE user_id = (r ->> 'user_id')::uuid;
      IF v_label IS NULL THEN RETURN NULL; END IF;   -- пользователя удаляют целиком
      -- доступ — по строке на поклажедателя или склад: сохранение «удалить
      -- всё — вставить заново» сводится тогда к тому, что добавили и убрали
      v_part := CASE TG_TABLE_NAME WHEN 'user_settings' THEN 'settings'
                                   WHEN 'user_depositor_access' THEN 'depositors:' || (r ->> 'depositor_id')
                                   ELSE 'warehouses:' || (r ->> 'warehouse_id') END;
    WHEN 'depositor_settings', 'depositor_table_column', 'depositor_field_label' THEN
      v_type := 'depositor'; v_id := r ->> 'depositor_id';
      v_dep := (r ->> 'depositor_id')::uuid;
      SELECT code INTO v_label FROM depositor WHERE depositor_id = v_dep;
      IF v_label IS NULL THEN RETURN NULL; END IF;
      v_part := CASE TG_TABLE_NAME
                  WHEN 'depositor_settings' THEN 'sections'
                  WHEN 'depositor_table_column' THEN 'column:' || (r ->> 'table_key') || '.' || (r ->> 'column_id')
                  ELSE 'label:' || COALESCE((SELECT code FROM enum_object_type
                                              WHERE id = (r ->> 'object_type_id')::smallint), '?')
                       || '.' || (r ->> 'field_id') END;
    WHEN 'portal_setting' THEN
      v_type := 'setting'; v_id := r ->> 'setting_key'; v_label := r ->> 'setting_key';
    WHEN 'dictionary_locale' THEN
      v_type := 'locale'; v_id := r ->> 'code'; v_label := r ->> 'name';
    WHEN 'ui_text' THEN
      v_type := 'ui_text'; v_id := r ->> 'text_key'; v_label := r ->> 'text_key';
      v_part := 'locale:' || COALESCE((SELECT code FROM dictionary_locale
                                        WHERE locale_id = (r ->> 'locale_id')::smallint), '?');
    WHEN 'enum_text' THEN
      v_type := 'enum'; v_id := (r ->> 'enum_key') || ':' || (r ->> 'value_id');
      SELECT code_column INTO v_code FROM enum_registry WHERE enum_key = r ->> 'enum_key';
      IF v_code IS NOT NULL THEN
        EXECUTE format('SELECT %I::text FROM %I WHERE id::text = $1', v_code, r ->> 'enum_key')
           INTO v_label USING r ->> 'value_id';
      END IF;
      v_part := 'locale:' || COALESCE((SELECT code FROM dictionary_locale
                                        WHERE locale_id = (r ->> 'locale_id')::smallint), '?');
    -- Каталог отчётов (V148): отчёт, его названия, параметры и их подписи.
    -- Части параметра — по ключу: сохранение «удалить всё — вставить заново»
    -- сводится тогда к настоящим правкам (audit_net).
    WHEN 'report' THEN
      v_type := 'report'; v_id := r ->> 'report_id'; v_label := r ->> 'wms_code';
    WHEN 'report_text' THEN
      v_type := 'report'; v_id := r ->> 'report_id';
      SELECT wms_code INTO v_label FROM report WHERE report_id = (r ->> 'report_id')::uuid;
      IF v_label IS NULL THEN RETURN NULL; END IF;   -- отчёт удаляют целиком
      v_part := 'locale:' || COALESCE((SELECT code FROM dictionary_locale
                                        WHERE locale_id = (r ->> 'locale_id')::smallint), '?');
    WHEN 'report_param' THEN
      v_type := 'report'; v_id := r ->> 'report_id';
      SELECT wms_code INTO v_label FROM report WHERE report_id = (r ->> 'report_id')::uuid;
      IF v_label IS NULL THEN RETURN NULL; END IF;
      v_part := 'param:' || (r ->> 'param_key');
    WHEN 'report_param_text' THEN
      v_type := 'report';
      SELECT rp.report_id::text, rp.param_key, rr.wms_code INTO v_id, v_code, v_label
        FROM report_param rp JOIN report rr ON rr.report_id = rp.report_id
       WHERE rp.param_id = (r ->> 'param_id')::uuid;
      IF v_label IS NULL THEN RETURN NULL; END IF;   -- параметр удаляют вместе с подписями
      v_part := 'param:' || v_code || ':locale:'
                || COALESCE((SELECT code FROM dictionary_locale
                              WHERE locale_id = (r ->> 'locale_id')::smallint), '?');
    WHEN 'depositor_report' THEN
      -- отчёты клиента — настройка клиента: в истории поклажедателя
      v_type := 'depositor'; v_id := r ->> 'depositor_id';
      v_dep := (r ->> 'depositor_id')::uuid;
      SELECT code INTO v_label FROM depositor WHERE depositor_id = v_dep;
      SELECT wms_code INTO v_code FROM report WHERE report_id = (r ->> 'report_id')::uuid;
      IF v_label IS NULL OR v_code IS NULL THEN RETURN NULL; END IF;
      v_part := 'report:' || v_code;
    -- Сопоставления с WMS (V148): у склада и клиента — часть «WMS» их карточки,
    -- качество — настройка портала «Качество WMS» с частью на каждый код WMS.
    WHEN 'wms_warehouse' THEN
      v_type := 'warehouse'; v_id := r ->> 'warehouse_id';
      SELECT name INTO v_label FROM warehouse WHERE warehouse_id = r ->> 'warehouse_id';
      IF v_label IS NULL THEN RETURN NULL; END IF;
      v_part := 'wms';
    WHEN 'wms_depositor' THEN
      v_type := 'depositor'; v_id := r ->> 'depositor_id';
      v_dep := (r ->> 'depositor_id')::uuid;
      SELECT code INTO v_label FROM depositor WHERE depositor_id = v_dep;
      IF v_label IS NULL THEN RETURN NULL; END IF;
      v_part := 'wms';
    WHEN 'wms_quality' THEN
      v_type := 'setting'; v_id := 'wms_quality'; v_label := 'wms_quality';
      v_part := 'wms:' || (r ->> 'wms_code');
    ELSE
      -- значения перечислений (таблицы enum_* из enum_registry)
      SELECT code_column INTO v_code FROM enum_registry WHERE enum_key = TG_TABLE_NAME;
      v_type := 'enum'; v_id := TG_TABLE_NAME || ':' || (r ->> 'id');
      v_label := r ->> COALESCE(v_code, 'code');
  END CASE;

  INSERT INTO audit_log (actor, depositor_id, object_type, object_id, object_label,
                         source_table, action, part, changes)
  VALUES (v_actor, v_dep, v_type, v_id, v_label, TG_TABLE_NAME, v_action, v_part, ch);
  RETURN NULL;
END
$function$;

COMMENT ON FUNCTION audit_row() IS
  'триггер журнала действий (V116, V117: настройки портала, V148: отчёты и WMS)';

CREATE OR REPLACE FUNCTION admin_report_save(p_report_id uuid, p_wms_code text, p_name text,
                                  p_place_code text, p_sort_order integer, p_is_active boolean,
                                  p_texts jsonb, p_params jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_id    uuid := p_report_id;
  v_place smallint;
  v_param uuid;
  p       jsonb;
  n       integer := 0;
BEGIN
  SELECT id INTO v_place FROM enum_report_place WHERE code = p_place_code;
  IF v_place IS NULL THEN
    RAISE EXCEPTION 'неизвестное место отчёта: %', p_place_code USING ERRCODE = '22023';
  END IF;

  IF v_id IS NULL THEN
    INSERT INTO report (wms_code, name, place_id, sort_order, is_active)
    VALUES (btrim(p_wms_code), btrim(p_name), v_place, COALESCE(p_sort_order, 0), COALESCE(p_is_active, true))
    RETURNING report_id INTO v_id;
  ELSE
    UPDATE report
       SET wms_code = btrim(p_wms_code), name = btrim(p_name), place_id = v_place,
           sort_order = COALESCE(p_sort_order, 0), is_active = COALESCE(p_is_active, true),
           updated_at = now()
     WHERE report_id = v_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'нет отчёта %', v_id USING ERRCODE = 'P0002';
    END IF;
  END IF;

  DELETE FROM report_text WHERE report_id = v_id;
  INSERT INTO report_text (report_id, locale_id, name)
  SELECT v_id, l.locale_id, btrim(t.value)
    FROM jsonb_each_text(COALESCE(p_texts, '{}')) t
    JOIN dictionary_locale l ON l.code = t.key
   WHERE NULLIF(btrim(t.value), '') IS NOT NULL;

  -- Подписи — до параметров, пока параметр ещё есть: журнал (V148) узнаёт
  -- по нему ключ, и сохранение без правок не выглядит заменой подписей.
  DELETE FROM report_param_text
   WHERE param_id IN (SELECT param_id FROM report_param WHERE report_id = v_id);
  DELETE FROM report_param WHERE report_id = v_id;
  FOR p IN SELECT value FROM jsonb_array_elements(COALESCE(p_params, '[]'))
  LOOP
    n := n + 1;
    INSERT INTO report_param (report_id, param_key, label, type_id, is_required, default_value,
                              use_current_date, is_hidden, sort_order)
    SELECT v_id, btrim(p ->> 'key'), btrim(p ->> 'label'), t.id,
           COALESCE((p ->> 'required')::boolean, false),
           NULLIF(btrim(p ->> 'default'), ''),
           COALESCE((p ->> 'currentDate')::boolean, false),
           COALESCE((p ->> 'hidden')::boolean, false) OR t.id IN (9, 10, 11),
           n
      FROM enum_report_param_type t WHERE t.code = p ->> 'type'
    RETURNING param_id INTO v_param;
    IF v_param IS NULL THEN
      RAISE EXCEPTION 'неизвестный тип параметра: %', p ->> 'type' USING ERRCODE = '22023';
    END IF;
    INSERT INTO report_param_text (param_id, locale_id, label)
    SELECT v_param, l.locale_id, btrim(t.value)
      FROM jsonb_each_text(COALESCE(p -> 'texts', '{}')) t
      JOIN dictionary_locale l ON l.code = t.key
     WHERE NULLIF(btrim(t.value), '') IS NOT NULL;
    v_param := NULL;
  END LOOP;
  RETURN v_id;
END
$$;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['report', 'report_text', 'report_param', 'report_param_text',
                           'depositor_report', 'wms_warehouse', 'wms_depositor', 'wms_quality']
  LOOP
    EXECUTE format('CREATE TRIGGER audit AFTER INSERT OR UPDATE OR DELETE ON %I '
                   'FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
  END LOOP;
END $$;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('audit.object.report', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.part.param', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.part.report', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.part.wms', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.part.wms_code', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.field.base_url', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.wms_code', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.place_id', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.sort_order', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.label', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.type_id', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.is_required', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.default_value', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.use_current_date', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.is_hidden', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.custom_name', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.field.param_values', 'audit', 'label', 'audit', 'журнал действий (V148)'),
  ('audit.setting.stock_count_transfers', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.setting.stock_sync_enabled', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.setting.stock_sync_time', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.setting.client_file_max_mb', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.setting.client_file_types', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.setting.report_run_retention_days', 'audit', 'text', 'audit', 'журнал действий (V148)'),
  ('audit.setting.wms_quality', 'audit', 'text', 'audit', 'журнал действий (V148)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('audit.object.report', 'ru', 'Отчёт'),
    ('audit.object.report', 'en', 'Report'),
    ('audit.part.param', 'ru', 'параметр «{v}»'),
    ('audit.part.param', 'en', 'parameter “{v}”'),
    ('audit.part.report', 'ru', 'отчёт «{v}»'),
    ('audit.part.report', 'en', 'report “{v}”'),
    ('audit.part.wms', 'ru', 'сопоставление с WMS'),
    ('audit.part.wms', 'en', 'WMS mapping'),
    ('audit.part.wms_code', 'ru', 'код WMS «{v}»'),
    ('audit.part.wms_code', 'en', 'WMS code “{v}”'),
    ('audit.field.base_url', 'ru', 'Адрес WMS'),
    ('audit.field.base_url', 'en', 'WMS address'),
    ('audit.field.wms_code', 'ru', 'Код в WMS'),
    ('audit.field.wms_code', 'en', 'WMS code'),
    ('audit.field.place_id', 'ru', 'Где кнопка'),
    ('audit.field.place_id', 'en', 'Button location'),
    ('audit.field.sort_order', 'ru', 'Порядок'),
    ('audit.field.sort_order', 'en', 'Order'),
    ('audit.field.label', 'ru', 'Подпись'),
    ('audit.field.label', 'en', 'Label'),
    ('audit.field.type_id', 'ru', 'Тип'),
    ('audit.field.type_id', 'en', 'Type'),
    ('audit.field.is_required', 'ru', 'Обязательный'),
    ('audit.field.is_required', 'en', 'Required'),
    ('audit.field.default_value', 'ru', 'Значение по умолчанию'),
    ('audit.field.default_value', 'en', 'Default value'),
    ('audit.field.use_current_date', 'ru', 'Текущая дата'),
    ('audit.field.use_current_date', 'en', 'Current date'),
    ('audit.field.is_hidden', 'ru', 'Скрытый'),
    ('audit.field.is_hidden', 'en', 'Hidden'),
    ('audit.field.custom_name', 'ru', 'Своё название'),
    ('audit.field.custom_name', 'en', 'Own name'),
    ('audit.field.param_values', 'ru', 'Значения параметров'),
    ('audit.field.param_values', 'en', 'Parameter values'),
    ('audit.setting.stock_count_transfers', 'ru', 'Учитывать перемещения в остатках'),
    ('audit.setting.stock_count_transfers', 'en', 'Count transfers in stock'),
    ('audit.setting.stock_sync_enabled', 'ru', 'Ночная сверка остатков'),
    ('audit.setting.stock_sync_enabled', 'en', 'Nightly stock reconciliation'),
    ('audit.setting.stock_sync_time', 'ru', 'Время сверки остатков'),
    ('audit.setting.stock_sync_time', 'en', 'Stock reconciliation time'),
    ('audit.setting.client_file_max_mb', 'ru', 'Наибольший файл клиента, МБ'),
    ('audit.setting.client_file_max_mb', 'en', 'Largest client file, MB'),
    ('audit.setting.client_file_types', 'ru', 'Типы файлов клиента'),
    ('audit.setting.client_file_types', 'en', 'Client file types'),
    ('audit.setting.report_run_retention_days', 'ru', 'Срок хранения запусков отчётов, дней'),
    ('audit.setting.report_run_retention_days', 'en', 'Report run retention, days'),
    ('audit.setting.wms_quality', 'ru', 'Качество WMS'),
    ('audit.setting.wms_quality', 'en', 'WMS quality')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order) VALUES
  ('report', 'report_id', 'skip', NULL, 1),
  ('report', 'wms_code', 'text', 'audit.field.wms_code', 2),
  ('report', 'name', 'text', 'audit.field.name', 3),
  ('report', 'place_id', 'enum:enum_report_place', 'audit.field.place_id', 4),
  ('report', 'sort_order', 'text', 'audit.field.sort_order', 5),
  ('report', 'is_active', 'bool', 'audit.field.is_active', 6),
  ('report', 'created_at', 'skip', NULL, 7),
  ('report', 'updated_at', 'skip', NULL, 8),
  ('report_text', 'report_id', 'skip', NULL, 9),
  ('report_text', 'locale_id', 'skip', NULL, 10),
  ('report_text', 'name', 'text', 'audit.field.name', 11),
  ('report_param', 'param_id', 'skip', NULL, 12),
  ('report_param', 'report_id', 'skip', NULL, 13),
  ('report_param', 'param_key', 'skip', NULL, 14),
  ('report_param', 'label', 'text', 'audit.field.label', 15),
  ('report_param', 'type_id', 'enum:enum_report_param_type', 'audit.field.type_id', 16),
  ('report_param', 'is_required', 'bool', 'audit.field.is_required', 17),
  ('report_param', 'default_value', 'text', 'audit.field.default_value', 18),
  ('report_param', 'use_current_date', 'bool', 'audit.field.use_current_date', 19),
  ('report_param', 'is_hidden', 'bool', 'audit.field.is_hidden', 20),
  ('report_param', 'sort_order', 'text', 'audit.field.sort_order', 21),
  ('report_param_text', 'param_id', 'skip', NULL, 22),
  ('report_param_text', 'locale_id', 'skip', NULL, 23),
  ('report_param_text', 'label', 'text', 'audit.field.label', 24),
  ('depositor_report', 'depositor_id', 'skip', NULL, 25),
  ('depositor_report', 'report_id', 'skip', NULL, 26),
  ('depositor_report', 'custom_name', 'text', 'audit.field.custom_name', 27),
  ('depositor_report', 'sort_order', 'text', 'audit.field.sort_order', 28),
  ('depositor_report', 'is_active', 'bool', 'audit.field.is_active', 29),
  ('depositor_report', 'param_values', 'text', 'audit.field.param_values', 30),
  ('wms_warehouse', 'warehouse_id', 'skip', NULL, 31),
  ('wms_warehouse', 'base_url', 'text', 'audit.field.base_url', 32),
  ('wms_depositor', 'depositor_id', 'skip', NULL, 33),
  ('wms_depositor', 'wms_code', 'text', 'audit.field.wms_code', 34),
  ('wms_quality', 'wms_code', 'skip', NULL, 35),
  ('wms_quality', 'quality_id', 'enum:enum_quality', 'audit.field.quality_id', 36);
