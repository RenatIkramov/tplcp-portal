SET search_path = dbo;

-- V118. «История» в карточках (11.11.2026): товар, упаковка, поставщик,
-- грузополучатель, поклажедатель, склад, организация оператора,
-- пользователь.
--
-- Данные клиента (товар, упаковка, контрагенты) читаются обычной
-- audit_history — под RLS, как сами карточки. Карточки администрирования
-- (пользователь, склад, поклажедатель, организация оператора) читаются
-- этой функцией с правами владельца: у пользователя или склада в журнале
-- нет поклажедателя, и под RLS их историю не увидел бы никто. Право на
-- раздел проверяет портал (AuditService).
--
-- Ссылки на данные клиента (поставщик в истории поклажедателя и т. п.)
-- словами читаются под контекстом клиента события — как в admin_audit_log.
--
-- audit_net переписана: соединение вместо подзапроса на каждое событие
-- и plpgsql вместо sql (запрос планируется с известными отборами) —
-- журнал в 10 тысяч событий: 25 секунд → полсекунды.
--
-- Доступы пользователя пишутся теперь по строке на поклажедателя или склад
-- (part = 'depositors:<id>' / 'warehouses:<id>'): раньше все строки одного
-- сохранения сводились в одну, и «склад А → склад Б» показывалось там,
-- где на деле добавили Б к А.

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
COMMENT ON FUNCTION audit_row() IS 'триггер журнала действий (V116; V117 — настройки портала; V118 — доступы по строке)';

-- Сводка событий быстрее: V117 считала журнал в 10 тысяч строк больше 10 секунд.
CREATE OR REPLACE FUNCTION audit_net(p_object_type text, p_object_id text, p_depositor uuid, p_actor text,
                          p_from timestamptz, p_to timestamptz)
RETURNS TABLE (event_id bigint, at timestamptz, actor text, depositor_id uuid, object_type text,
               object_id text, object_label text, source_table text, action text, part text,
               changes jsonb)
LANGUAGE plpgsql
STABLE
SET search_path = dbo, pg_temp
AS $$
BEGIN
  -- plpgsql, а не sql: запрос планируется с известными значениями отборов.
  -- Функция на sql планировалась «вообще», и сводка журнала в 10 тысяч
  -- строк шла 12 секунд вместо трети секунды.
  RETURN QUERY
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
    -- Соединение, а не подзапрос на каждое событие: подзапрос давал
    -- квадрат от числа событий (10 тысяч — 25 секунд). Пустая часть
    -- сравнивается через COALESCE, чтобы соединение шло по хэшу.
    SELECT n.tx, n.object_type, n.object_id, n.part,
           jsonb_object_agg(f.key, jsonb_strip_nulls(jsonb_build_object('o', x.oo, 'n', x.nn))) AS changes
      FROM net n
      JOIN fld f ON f.tx = n.tx AND f.object_type = n.object_type AND f.object_id = n.object_id
                AND COALESCE(f.part, '') = COALESCE(n.part, '')
      CROSS JOIN LATERAL (
        SELECT CASE WHEN n.act IN ('create', 'login_failed') THEN NULL ELSE f.o END AS oo,
               CASE WHEN n.act = 'delete' THEN NULL ELSE f.n END AS nn
      ) x
     WHERE n.act IS NOT NULL
       AND (n.act <> 'update' OR x.oo IS DISTINCT FROM x.nn)
     GROUP BY n.tx, n.object_type, n.object_id, n.part
  )
  SELECT n.event_id, n.at, n.actor::text, n.depositor_id, n.object_type::text, n.object_id, n.object_label,
         n.source_table::text,
         CASE WHEN n.act = 'update' AND n.part IS NULL AND n.object_type IN ('receipts', 'orders', 'transfers')
                   AND c.changes ? 'status_id' THEN 'status' ELSE n.act END::text,
         n.part,
         COALESCE(c.changes, '{}'::jsonb)
    FROM net n
    LEFT JOIN ch c ON c.tx = n.tx AND c.object_type = n.object_type AND c.object_id = n.object_id
                  AND COALESCE(c.part, '') = COALESCE(n.part, '')
   WHERE n.act IS NOT NULL
     -- сохранили без настоящих изменений (например, состав «как был»)
     AND NOT (n.act = 'update' AND c.changes IS NULL);
END
$$;


CREATE FUNCTION admin_audit_history(p_object_type text, p_object_id text)
RETURNS TABLE (event_id bigint, at timestamptz, actor text, actor_name text, action text,
               part text, field text, field_key text, old_text text, new_text text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_old text := current_setting('app.depositor_id', true);
  r     record;
BEGIN
  FOR r IN SELECT * FROM audit_net(p_object_type, p_object_id, NULL, NULL, NULL, NULL) n
            ORDER BY n.event_id DESC LOOP
    PERFORM set_config('app.depositor_id', COALESCE(r.depositor_id::text, ''), true);
    RETURN QUERY
      SELECT r.event_id, r.at, r.actor::text,
             COALESCE((SELECT u.full_name FROM app_user u WHERE u.login = r.actor), r.actor)::text,
             r.action::text, r.part::text,
             c.key::text, COALESCE(af.text_key, 'audit.field.' || c.key)::text,
             audit_value(af.kind, c.value -> 'o'), audit_value(af.kind, c.value -> 'n')
        FROM (SELECT 1) one
        -- создание самой записи — без полей, как в audit_history
        LEFT JOIN LATERAL jsonb_each(CASE WHEN r.action = 'create' AND r.part IS NULL
                                          THEN '{}'::jsonb ELSE r.changes END) c ON true
        LEFT JOIN audit_field af ON af.source_table = r.source_table AND af.column_name = c.key
       ORDER BY COALESCE(af.sort_order, 0);
  END LOOP;
  PERFORM set_config('app.depositor_id', COALESCE(v_old, ''), true);
END
$$;
GRANT EXECUTE ON FUNCTION admin_audit_history(text, text) TO portal_app;

-- Тексты: подпись свёрнутого раздела в карточке пользователя и заголовки
-- событий для карточек справочников («Запись создана» вместо «Документ создан»).
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('audit.title.change_history', 'audit', 'title', 'audit', '«История» в карточках (V118)'),
  ('audit.action.record_created', 'audit', 'text', 'audit', '«История» в карточках (V118)'),
  ('error.audit.unknown_object', 'server', 'message', 'server', '«История» в карточках (V118)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('audit.title.change_history', 'ru', 'История изменений'),
    ('audit.title.change_history', 'en', 'Change history'),
    ('audit.action.record_created', 'ru', 'Запись создана'),
    ('audit.action.record_created', 'en', 'Record created'),
    ('error.audit.unknown_object', 'ru', 'Для этого объекта истории нет'),
    ('error.audit.unknown_object', 'en', 'There is no history for this object')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
