SET search_path = dbo;

-- V117. Журнал действий, часть 2 (11.11.2026): журнал администратора,
-- срок хранения, неудачные входы.
--
-- * audit_net() — общая «сводка» событий: события одной транзакции по
--   одной записи и одной её части сводятся в одно (было — до первого,
--   стало — после последнего). Ею пользуются и история документа
--   (audit_history, переписана поверх неё), и журнал администратора.
-- * admin_audit_log() — журнал по всему порталу для администратора:
--   отборы, страница, всего. С правами владельца: журнал нужен по всем
--   клиентам, а под RLS администратор видел бы одного. Слова для
--   ссылок (поставщик, товар) читаются под контекстом клиента события.
-- * portal_setting — настройки портала; первая — срок хранения журнала
--   в месяцах (решение заказчика: настраивается, по умолчанию 12).
--   audit_cleanup() удаляет старше срока: раз в сутки (AuditService)
--   и сразу после смены срока.
-- * Неудачные входы — действие login_failed, объект «вход», кто —
--   набранный логин (обрезан до 100 знаков: его набирает кто угодно).

-- ---------- Неудачный вход ------------------------------------------------------

ALTER TABLE audit_log DROP CONSTRAINT audit_log_action_check;
ALTER TABLE audit_log ALTER COLUMN action TYPE varchar(20);   -- 'login_failed' длиннее 10
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check
  CHECK (action IN ('create', 'update', 'delete', 'status', 'login_failed'));

CREATE FUNCTION audit_login_failed(p_login text, p_reason text) RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  INSERT INTO audit_log (actor, depositor_id, object_type, object_id, object_label,
                         source_table, action, part, changes)
  VALUES (left(COALESCE(NULLIF(btrim(p_login), ''), '?'), 100), NULL, 'login',
          left(COALESCE(NULLIF(btrim(p_login), ''), '?'), 100), NULL,
          'login', 'login_failed', NULL,
          jsonb_build_object('reason', jsonb_build_object('n', p_reason)))
$$;
GRANT EXECUTE ON FUNCTION audit_login_failed(text, text) TO portal_app;

-- ---------- Настройки портала и срок хранения ---------------------------------

CREATE TABLE portal_setting (
  setting_key  varchar(60) PRIMARY KEY,
  value        text        NOT NULL,
  note         text
);
COMMENT ON TABLE portal_setting IS 'настройки портала, ключ — значение (V117)';
INSERT INTO portal_setting (setting_key, value, note)
VALUES ('audit_retention_months', '12',
        'сколько месяцев хранить журнал действий; старше — удаляется раз в сутки');
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON portal_setting FROM portal_app;
GRANT SELECT ON portal_setting TO portal_app;

CREATE FUNCTION audit_cleanup() RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_months integer;
  v_count  integer;
BEGIN
  SELECT value::integer INTO v_months FROM portal_setting WHERE setting_key = 'audit_retention_months';
  IF v_months IS NULL OR v_months < 1 THEN
    RETURN 0;   -- срок не задан — ничего не удаляем
  END IF;
  DELETE FROM audit_log WHERE at < now() - make_interval(months => v_months);
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END
$$;
GRANT EXECUTE ON FUNCTION audit_cleanup() TO portal_app;

-- Срок меняет администратор (право проверяет портал); сразу и чистим.
CREATE FUNCTION admin_set_audit_retention(p_months integer) RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  IF p_months IS NULL OR p_months < 1 OR p_months > 120 THEN
    RAISE EXCEPTION 'audit retention out of range: %', p_months;
  END IF;
  UPDATE portal_setting SET value = p_months::text WHERE setting_key = 'audit_retention_months';
  RETURN audit_cleanup();
END
$$;
GRANT EXECUTE ON FUNCTION admin_set_audit_retention(integer) TO portal_app;

-- Смена срока — тоже действие администратора: журналируем.
CREATE OR REPLACE FUNCTION audit_row() RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
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
      v_part := CASE TG_TABLE_NAME WHEN 'user_settings' THEN 'settings'
                                   WHEN 'user_depositor_access' THEN 'depositors' ELSE 'warehouses' END;
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
$$;

-- ---------- Тексты ----------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('admin.button.logs', 'admin', 'button', 'admin', 'журнал действий (V117)'),
  ('admin.button.audit_log', 'admin', 'button', 'admin', 'журнал действий (V117)'),
  ('audit.hint.audit_log', 'audit', 'hint', 'audit', 'журнал действий (V117)'),
  ('audit.label.retention_months', 'audit', 'label', 'audit', 'журнал действий (V117)'),
  ('audit.message.retention_saved', 'audit', 'message', 'audit', 'журнал действий (V117)'),
  ('audit.label.object_type', 'audit', 'label', 'audit', 'журнал действий (V117)'),
  ('audit.label.date_from', 'audit', 'label', 'audit', 'журнал действий (V117)'),
  ('audit.label.date_to', 'audit', 'label', 'audit', 'журнал действий (V117)'),
  ('audit.column.object', 'audit', 'column', 'audit', 'журнал действий (V117)'),
  ('audit.column.action', 'audit', 'column', 'audit', 'журнал действий (V117)'),
  ('audit.column.changes', 'audit', 'column', 'audit', 'журнал действий (V117)'),
  ('audit.text.more_fields', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.text.nothing_found', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.action.create', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.action.update', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.action.delete', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.action.login_failed', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.receipts', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.orders', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.transfers', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.sku', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.packaging', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.supplier', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.consignee', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.warehouse', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.depositor', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.operator', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.app_user', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.locale', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.ui_text', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.enum', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.setting', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.object.login', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.line', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.delivery_point', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.settings', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.depositors', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.warehouses', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.sections', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.column', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.label', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.part.locale', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.setting.audit_retention_months', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.field.setting_value', 'audit', 'label', 'audit', 'журнал действий (V117)'),
  ('audit.field.login_reason', 'audit', 'label', 'audit', 'журнал действий (V117)'),
  ('audit.login.password', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.login.unknown', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.login.inactive', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('audit.login.locked', 'audit', 'text', 'audit', 'журнал действий (V117)'),
  ('error.audit.retention_range', 'server', 'message', 'server', 'журнал действий (V117)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.button.logs', 'ru', 'Журналы'),
    ('admin.button.logs', 'en', 'Logs'),
    ('admin.button.audit_log', 'ru', 'Журнал действий'),
    ('admin.button.audit_log', 'en', 'Activity log'),
    ('audit.hint.audit_log', 'ru', 'Кто, когда и что менял в портале. Записи старше срока хранения удаляются сами раз в сутки.'),
    ('audit.hint.audit_log', 'en', 'Who changed what in the portal, and when. Entries older than the retention period are deleted automatically once a day.'),
    ('audit.label.retention_months', 'ru', 'Хранить журнал, месяцев'),
    ('audit.label.retention_months', 'en', 'Keep the log, months'),
    ('audit.message.retention_saved', 'ru', 'Срок хранения сохранён'),
    ('audit.message.retention_saved', 'en', 'Retention period saved'),
    ('audit.label.object_type', 'ru', 'Объект'),
    ('audit.label.object_type', 'en', 'Object'),
    ('audit.label.date_from', 'ru', 'С'),
    ('audit.label.date_from', 'en', 'From'),
    ('audit.label.date_to', 'ru', 'По'),
    ('audit.label.date_to', 'en', 'To'),
    ('audit.column.object', 'ru', 'Объект'),
    ('audit.column.object', 'en', 'Object'),
    ('audit.column.action', 'ru', 'Действие'),
    ('audit.column.action', 'en', 'Action'),
    ('audit.column.changes', 'ru', 'Изменения'),
    ('audit.column.changes', 'en', 'Changes'),
    ('audit.text.more_fields', 'ru', 'ещё {n}'),
    ('audit.text.more_fields', 'en', '{n} more'),
    ('audit.text.nothing_found', 'ru', 'Записей нет'),
    ('audit.text.nothing_found', 'en', 'No entries'),
    ('audit.action.create', 'ru', 'Создание'),
    ('audit.action.create', 'en', 'Created'),
    ('audit.action.update', 'ru', 'Изменение'),
    ('audit.action.update', 'en', 'Changed'),
    ('audit.action.delete', 'ru', 'Удаление'),
    ('audit.action.delete', 'en', 'Deleted'),
    ('audit.action.login_failed', 'ru', 'Неудачный вход'),
    ('audit.action.login_failed', 'en', 'Failed sign-in'),
    ('audit.object.receipts', 'ru', 'Поступление'),
    ('audit.object.receipts', 'en', 'Receipt'),
    ('audit.object.orders', 'ru', 'Заказ'),
    ('audit.object.orders', 'en', 'Order'),
    ('audit.object.transfers', 'ru', 'Перемещение'),
    ('audit.object.transfers', 'en', 'Transfer'),
    ('audit.object.sku', 'ru', 'Товар'),
    ('audit.object.sku', 'en', 'Item'),
    ('audit.object.packaging', 'ru', 'Упаковка'),
    ('audit.object.packaging', 'en', 'Packaging'),
    ('audit.object.supplier', 'ru', 'Поставщик'),
    ('audit.object.supplier', 'en', 'Supplier'),
    ('audit.object.consignee', 'ru', 'Грузополучатель'),
    ('audit.object.consignee', 'en', 'Consignee'),
    ('audit.object.warehouse', 'ru', 'Склад'),
    ('audit.object.warehouse', 'en', 'Warehouse'),
    ('audit.object.depositor', 'ru', 'Поклажедатель'),
    ('audit.object.depositor', 'en', 'Depositor'),
    ('audit.object.operator', 'ru', 'Организация оператора'),
    ('audit.object.operator', 'en', 'Operator organization'),
    ('audit.object.app_user', 'ru', 'Пользователь'),
    ('audit.object.app_user', 'en', 'User'),
    ('audit.object.locale', 'ru', 'Язык'),
    ('audit.object.locale', 'en', 'Language'),
    ('audit.object.ui_text', 'ru', 'Текст интерфейса'),
    ('audit.object.ui_text', 'en', 'Interface text'),
    ('audit.object.enum', 'ru', 'Значение перечисления'),
    ('audit.object.enum', 'en', 'Enumeration value'),
    ('audit.object.setting', 'ru', 'Настройка портала'),
    ('audit.object.setting', 'en', 'Portal setting'),
    ('audit.object.login', 'ru', 'Вход'),
    ('audit.object.login', 'en', 'Sign-in'),
    ('audit.part.line', 'ru', 'строка {n}'),
    ('audit.part.line', 'en', 'line {n}'),
    ('audit.part.delivery_point', 'ru', 'место доставки «{v}»'),
    ('audit.part.delivery_point', 'en', 'delivery point “{v}”'),
    ('audit.part.settings', 'ru', 'права'),
    ('audit.part.settings', 'en', 'permissions'),
    ('audit.part.depositors', 'ru', 'доступ к поклажедателям'),
    ('audit.part.depositors', 'en', 'depositor access'),
    ('audit.part.warehouses', 'ru', 'доступ к складам'),
    ('audit.part.warehouses', 'en', 'warehouse access'),
    ('audit.part.sections', 'ru', 'разделы'),
    ('audit.part.sections', 'en', 'sections'),
    ('audit.part.column', 'ru', 'поле «{v}»'),
    ('audit.part.column', 'en', 'field “{v}”'),
    ('audit.part.label', 'ru', 'наименование поля «{v}»'),
    ('audit.part.label', 'en', 'field name “{v}”'),
    ('audit.part.locale', 'ru', 'язык: {v}'),
    ('audit.part.locale', 'en', 'language: {v}'),
    ('audit.setting.audit_retention_months', 'ru', 'Срок хранения журнала, месяцев'),
    ('audit.setting.audit_retention_months', 'en', 'Activity log retention, months'),
    ('audit.field.setting_value', 'ru', 'Значение'),
    ('audit.field.setting_value', 'en', 'Value'),
    ('audit.field.login_reason', 'ru', 'Причина'),
    ('audit.field.login_reason', 'en', 'Reason'),
    ('audit.login.password', 'ru', 'Неверный пароль'),
    ('audit.login.password', 'en', 'Wrong password'),
    ('audit.login.unknown', 'ru', 'Нет такого пользователя'),
    ('audit.login.unknown', 'en', 'No such user'),
    ('audit.login.inactive', 'ru', 'Пользователь отключён'),
    ('audit.login.inactive', 'en', 'User is deactivated'),
    ('audit.login.locked', 'ru', 'Вход временно закрыт после неудачных попыток'),
    ('audit.login.locked', 'en', 'Sign-in temporarily locked after failed attempts'),
    ('error.audit.retention_range', 'ru', 'Срок хранения — от {min} до {max} месяцев'),
    ('error.audit.retention_range', 'en', 'Retention must be between {min} and {max} months')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Группа «Ошибки» стала «Журналы»: прежняя подпись больше не нужна.
DELETE FROM ui_text     WHERE text_key = 'admin.button.errors';
DELETE FROM ui_text_key WHERE text_key = 'admin.button.errors';

COMMENT ON FUNCTION audit_row() IS 'триггер журнала действий (V116, V117: настройки портала)';

CREATE TRIGGER audit AFTER INSERT OR UPDATE OR DELETE ON portal_setting
  FOR EACH ROW EXECUTE FUNCTION audit_row();

INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order) VALUES
  ('portal_setting', 'setting_key', 'skip', NULL, 1),
  ('portal_setting', 'value', 'text', 'audit.field.setting_value', 2),
  ('portal_setting', 'note', 'skip', NULL, 3),
  ('login', 'reason', 'uikey:audit.login.', 'audit.field.login_reason', 1);

-- ---------- Значение словами: + причина неудачного входа -------------------------

CREATE OR REPLACE FUNCTION audit_value(p_kind text, p_value jsonb) RETURNS text
LANGUAGE plpgsql
STABLE
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v   text := p_value #>> '{}';
  t   text;
  res text;
BEGIN
  IF p_value IS NULL OR v IS NULL THEN
    RETURN NULL;
  END IF;
  CASE
    WHEN p_kind = 'secret' THEN
      RETURN '•••';
    WHEN p_kind = 'bool' OR p_kind LIKE 'uikey:%' THEN
      t := CASE WHEN p_kind = 'bool'
                THEN CASE WHEN v = 'true' THEN 'common.value.yes' ELSE 'common.value.no' END
                ELSE substr(p_kind, 7) || v END;
      SELECT ut.value INTO res
        FROM ui_text ut JOIN dictionary_locale l ON l.locale_id = ut.locale_id
       WHERE ut.text_key = t
       ORDER BY (l.code = NULLIF(current_setting('app.locale', true), '')) DESC, l.is_default DESC
       LIMIT 1;
    WHEN p_kind LIKE 'enum:%' THEN
      t := substr(p_kind, 6);
      EXECUTE format('SELECT enum_name(%L, id::text, name) FROM %I WHERE id::text = $1', t, t)
         INTO res USING v;
    WHEN p_kind LIKE 'enumcode:%' THEN
      t := substr(p_kind, 10);
      EXECUTE format('SELECT enum_name(%L, id::text, name) FROM %I WHERE code = $1', t, t)
         INTO res USING v;
    WHEN p_kind = 'ref:warehouse' THEN
      SELECT name INTO res FROM warehouse WHERE warehouse_id = v;
    WHEN p_kind = 'ref:supplier' THEN
      SELECT name INTO res FROM supplier WHERE supplier_id::text = v;
    WHEN p_kind = 'ref:consignee' THEN
      SELECT name INTO res FROM consignee WHERE consignee_id::text = v;
    WHEN p_kind = 'ref:delivery_point' THEN
      SELECT address INTO res FROM delivery_point WHERE address_id::text = v;
    WHEN p_kind = 'ref:sku' THEN
      SELECT code || ' — ' || name INTO res FROM sku WHERE sku_id::text = v;
    WHEN p_kind = 'ref:packaging' THEN
      SELECT packaging_code INTO res FROM product_packaging WHERE packaging_id::text = v;
    WHEN p_kind = 'ref:depositor' THEN
      SELECT org_name INTO res FROM depositor WHERE depositor_id::text = v;
    WHEN p_kind = 'ref:locale' THEN
      SELECT name INTO res FROM dictionary_locale WHERE locale_id::text = v;
    WHEN p_kind = 'ref:operator' THEN
      SELECT name INTO res FROM operator WHERE operator_id::text = v;
    WHEN jsonb_typeof(p_value) = 'number' AND v LIKE '%.%' THEN
      res := rtrim(rtrim(v, '0'), '.');
    ELSE
      res := v;
  END CASE;
  RETURN COALESCE(res, v);
END
$$;

-- ---------- Сводка событий ------------------------------------------------------------

CREATE FUNCTION audit_net(p_object_type text, p_object_id text, p_depositor uuid, p_actor text,
                          p_from timestamptz, p_to timestamptz)
RETURNS TABLE (event_id bigint, at timestamptz, actor text, depositor_id uuid, object_type text,
               object_id text, object_label text, source_table text, action text, part text,
               changes jsonb)
LANGUAGE sql
STABLE
SET search_path = dbo, pg_temp
AS $$
  WITH ev AS (
    SELECT a.* FROM audit_log a
     WHERE (p_object_type IS NULL OR a.object_type = p_object_type)
       AND (p_object_id IS NULL OR a.object_id = p_object_id)
       AND (p_depositor IS NULL OR a.depositor_id = p_depositor)
       AND (p_actor IS NULL OR a.actor = p_actor)
       AND (p_from IS NULL OR a.at >= p_from)
       AND (p_to IS NULL OR a.at < p_to)
  ),
  grp AS (
    SELECT e.tx, e.object_type, e.object_id, e.part,
           max(e.audit_id) AS event_id,
           max(e.at) AS at,
           (array_agg(e.actor ORDER BY e.audit_id DESC))[1] AS actor,
           (array_agg(e.depositor_id ORDER BY e.audit_id DESC))[1] AS depositor_id,
           (array_agg(e.object_label ORDER BY e.audit_id DESC))[1] AS object_label,
           (array_agg(e.source_table ORDER BY e.audit_id))[1] AS source_table,
           (array_agg(e.action ORDER BY e.audit_id))[1] AS first_action,
           (array_agg(e.action ORDER BY e.audit_id DESC))[1] AS last_action
      FROM ev e
     GROUP BY e.tx, e.object_type, e.object_id, e.part
  ),
  fld AS (
    SELECT e.tx, e.object_type, e.object_id, e.part, c.key,
           (array_agg(c.value -> 'o' ORDER BY e.audit_id))[1] AS o,
           (array_agg(c.value -> 'n' ORDER BY e.audit_id DESC))[1] AS n
      FROM ev e
      CROSS JOIN LATERAL jsonb_each(e.changes) c
     GROUP BY e.tx, e.object_type, e.object_id, e.part, c.key
  ),
  net AS (
    SELECT g.*,
           CASE WHEN g.first_action = 'login_failed' THEN 'login_failed'
                WHEN g.first_action = 'create' AND g.last_action = 'delete' THEN NULL
                WHEN g.first_action = 'create' THEN 'create'
                WHEN g.last_action = 'delete' THEN 'delete'
                ELSE 'update' END AS act
      FROM grp g
  ),
  ch AS (
    SELECT n.tx, n.object_type, n.object_id, n.part,
           jsonb_object_agg(f.key, jsonb_strip_nulls(jsonb_build_object('o', f.oo, 'n', f.nn))) AS changes
      FROM net n
      CROSS JOIN LATERAL (
        SELECT f.key,
               CASE WHEN n.act IN ('create', 'login_failed') THEN NULL ELSE f.o END AS oo,
               CASE WHEN n.act = 'delete' THEN NULL ELSE f.n END AS nn
          FROM fld f
         WHERE f.tx = n.tx AND f.object_type = n.object_type AND f.object_id = n.object_id
           AND f.part IS NOT DISTINCT FROM n.part
      ) f
     WHERE n.act IS NOT NULL
       AND (n.act <> 'update' OR f.oo IS DISTINCT FROM f.nn)
     GROUP BY n.tx, n.object_type, n.object_id, n.part
  )
  SELECT n.event_id, n.at, n.actor, n.depositor_id, n.object_type, n.object_id, n.object_label,
         n.source_table,
         CASE WHEN n.act = 'update' AND n.part IS NULL AND n.object_type IN ('receipts', 'orders', 'transfers')
                   AND c.changes ? 'status_id' THEN 'status' ELSE n.act END,
         n.part,
         COALESCE(c.changes, '{}'::jsonb)
    FROM net n
    LEFT JOIN ch c ON c.tx = n.tx AND c.object_type = n.object_type AND c.object_id = n.object_id
                  AND c.part IS NOT DISTINCT FROM n.part
   WHERE n.act IS NOT NULL
     -- сохранили без настоящих изменений (например, состав «как был»)
     AND NOT (n.act = 'update' AND c.changes IS NULL)
$$;
GRANT EXECUTE ON FUNCTION audit_net(text, text, uuid, text, timestamptz, timestamptz) TO portal_app;

-- История одной записи — теперь поверх общей сводки. Вид ответа прежний.
CREATE OR REPLACE FUNCTION audit_history(p_object_type text, p_object_id text)
RETURNS TABLE (event_id bigint, at timestamptz, actor text, actor_name text, action text,
               part text, field text, field_key text, old_text text, new_text text)
LANGUAGE sql
STABLE
SET search_path = dbo, pg_temp
AS $$
  SELECT n.event_id, n.at, n.actor, COALESCE(u.full_name, n.actor), n.action, n.part,
         c.key, COALESCE(af.text_key, 'audit.field.' || c.key),
         audit_value(af.kind, c.value -> 'o'), audit_value(af.kind, c.value -> 'n')
    FROM audit_net(p_object_type, p_object_id, NULL, NULL, NULL, NULL) n
    LEFT JOIN app_user u ON u.login = n.actor
    -- создание самой записи — без полей: это начальные значения, их видно в карточке
    LEFT JOIN LATERAL jsonb_each(CASE WHEN n.action = 'create' AND n.part IS NULL
                                      THEN '{}'::jsonb ELSE n.changes END) c ON true
    LEFT JOIN audit_field af ON af.source_table = n.source_table AND af.column_name = c.key
   ORDER BY n.event_id DESC, COALESCE(af.sort_order, 0)
$$;

-- ---------- Журнал администратора --------------------------------------------------

CREATE FUNCTION admin_audit_log(p_depositor uuid, p_actor text, p_object_type text,
                                p_from date, p_to date, p_page integer, p_page_size integer)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_total  integer;
  v_rows   jsonb := '[]'::jsonb;
  v_size   integer := LEAST(GREATEST(COALESCE(p_page_size, 50), 1), 200);
  v_page   integer := GREATEST(COALESCE(p_page, 1), 1);
  v_old    text := current_setting('app.depositor_id', true);
  r        record;
BEGIN
  SELECT count(*) INTO v_total
    FROM audit_net(NULLIF(p_object_type, ''), NULL, p_depositor, NULLIF(p_actor, ''),
                   p_from::timestamptz, (p_to + 1)::timestamptz);

  FOR r IN SELECT * FROM audit_net(NULLIF(p_object_type, ''), NULL, p_depositor, NULLIF(p_actor, ''),
                                   p_from::timestamptz, (p_to + 1)::timestamptz)
            ORDER BY event_id DESC
            LIMIT v_size OFFSET (v_page - 1) * v_size LOOP
    -- Ссылки (поставщик, товар, место доставки) — данные клиента под RLS:
    -- словами их можно прочитать только в контексте этого клиента.
    PERFORM set_config('app.depositor_id', COALESCE(r.depositor_id::text, ''), true);
    v_rows := v_rows || jsonb_build_object(
      'eventId', r.event_id,
      'at', r.at,
      'actor', r.actor,
      'actorName', (SELECT u.full_name FROM app_user u WHERE u.login = r.actor),
      'depositorName', (SELECT d.org_name FROM depositor d WHERE d.depositor_id = r.depositor_id),
      'objectType', r.object_type,
      'objectId', r.object_id,
      'objectLabel', r.object_label,
      'action', r.action,
      'part', r.part,
      'fields', (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                          'field', c.key,
                          'fieldKey', COALESCE(af.text_key, 'audit.field.' || c.key),
                          'oldText', audit_value(af.kind, c.value -> 'o'),
                          'newText', audit_value(af.kind, c.value -> 'n'))
                        ORDER BY COALESCE(af.sort_order, 0)), '[]'::jsonb)
                   FROM jsonb_each(r.changes) c
                   LEFT JOIN audit_field af ON af.source_table = r.source_table
                                           AND af.column_name = c.key));
  END LOOP;
  PERFORM set_config('app.depositor_id', COALESCE(v_old, ''), true);
  RETURN jsonb_build_object('total', v_total, 'page', v_page, 'pageSize', v_size, 'rows', v_rows);
END
$$;
GRANT EXECUTE ON FUNCTION admin_audit_log(uuid, text, text, date, date, integer, integer) TO portal_app;

-- Кто встречается в журнале — для списка «Пользователь».
CREATE FUNCTION admin_audit_actors() RETURNS TABLE (actor text, actor_name text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT a.actor, COALESCE(u.full_name, a.actor)
    FROM (SELECT DISTINCT actor FROM audit_log WHERE action <> 'login_failed') a
    LEFT JOIN app_user u ON u.login = a.actor
   ORDER BY 2
$$;
GRANT EXECUTE ON FUNCTION admin_audit_actors() TO portal_app;
