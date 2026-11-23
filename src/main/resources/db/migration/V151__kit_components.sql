SET search_path = dbo;

-- =====================================================================
-- V151. Комплекты, шаг 1: состав комплекта
-- =====================================================================
-- Решение заказчика 21.11.2026 (документ проекта «Комплекты — решение»).
-- WMS хранит комплектующие; комплект собирается при отгрузке. Состав
-- комплекта (что и сколько на один комплект) ведёт склад в ArtWMS,
-- портал получает его синхронизацией и только показывает: вкладка
-- «Состав» в карточке товара.
--
-- Здесь — таблица состава и функция записи для синхронизации. Остатки
-- и документы по составу — следующие шаги (Н2, Н3).
--
-- Состав одноуровневый: комплектующая сама комплектом быть не может
-- (вопрос 5 к ArtWMS — если бывает, правило снимается здесь).

-- ---------------------------------------------------------------------
-- 1. Таблица состава
-- ---------------------------------------------------------------------
CREATE TABLE sku_kit_component (
  depositor_id     uuid          NOT NULL REFERENCES depositor (depositor_id),
  kit_sku_id       uuid          NOT NULL REFERENCES sku (sku_id) ON DELETE CASCADE,
  component_sku_id uuid          NOT NULL REFERENCES sku (sku_id),
  qty              numeric(14,3) NOT NULL CHECK (qty > 0),
  sort_order       smallint      NOT NULL DEFAULT 0,
  synced_at        timestamptz   NOT NULL DEFAULT now(),
  PRIMARY KEY (kit_sku_id, component_sku_id),
  CHECK (kit_sku_id <> component_sku_id)
);
CREATE INDEX sku_kit_component_component ON sku_kit_component (component_sku_id);
COMMENT ON TABLE  sku_kit_component IS
  'Состав комплекта: комплектующая и сколько её на один комплект. Приходит из WMS (V151)';
COMMENT ON COLUMN sku_kit_component.qty IS 'Количество комплектующей на один комплект, в её единицах';

ALTER TABLE sku_kit_component ENABLE ROW LEVEL SECURITY;
ALTER TABLE sku_kit_component FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON sku_kit_component
  USING (depositor_id = current_depositor_id());

-- Портал состав только читает; пишет функция ниже.
REVOKE ALL ON sku_kit_component FROM PUBLIC;
GRANT SELECT ON sku_kit_component TO portal_app;

-- ---------------------------------------------------------------------
-- 2. Запись состава (для синхронизации с WMS)
-- ---------------------------------------------------------------------
-- Состав комплекта целиком: p_rows = [{"code": "код комплектующей",
-- "qty": 2}, …]; пусто — товар больше не комплект. Коды — товары того
-- же клиента; комплектующая сама не комплект. Признак sku.is_kit
-- следует за составом. Возвращает число строк состава.
CREATE FUNCTION kit_components_apply(p_depositor uuid, p_kit_code text, p_rows jsonb)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_kit   uuid;
  v_bad   text;
  v_count integer;
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
  RETURN v_count;
END
$$;
COMMENT ON FUNCTION kit_components_apply(uuid, text, jsonb) IS
  'Записать состав комплекта целиком (синхронизация с WMS); признак is_kit следует за составом (V151)';
REVOKE EXECUTE ON FUNCTION kit_components_apply(uuid, text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION kit_components_apply(uuid, text, jsonb) TO portal_app;

-- ---------------------------------------------------------------------
-- 3. Журнал действий: состав — в истории товара-комплекта
-- ---------------------------------------------------------------------
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
    -- Состав комплекта (V151): у товара-комплекта, часть — комплектующая
    WHEN 'sku_kit_component' THEN
      v_type := 'sku'; v_id := r ->> 'kit_sku_id';
      v_dep := (r ->> 'depositor_id')::uuid;
      SELECT code INTO v_label FROM sku WHERE sku_id = (r ->> 'kit_sku_id')::uuid;
      SELECT code INTO v_code FROM sku WHERE sku_id = (r ->> 'component_sku_id')::uuid;
      IF v_label IS NULL OR v_code IS NULL THEN RETURN NULL; END IF;   -- товар удаляют целиком
      v_part := 'kit:' || v_code;
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
  'триггер журнала действий (V116, V117: настройки портала, V148: отчёты и WMS, V151: состав комплекта)';

CREATE TRIGGER audit AFTER INSERT OR UPDATE OR DELETE ON sku_kit_component
  FOR EACH ROW EXECUTE FUNCTION audit_row();

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('sku.tab.kit',            'sku',   'tab',   'sku',   'карточка товара: вкладка «Состав» комплекта (V151)'),
  ('sku.hint.kit_from_wms',  'sku',   'hint',  'sku',   'карточка товара: состав комплекта ведёт склад в WMS (V151)'),
  ('sku.text.kit_empty',     'sku',   'text',  'sku',   'карточка товара: состав комплекта ещё не получен из WMS (V151)'),
  ('sku.column.kit_qty',     'sku',   'column','sku',   'карточка товара: количество комплектующей на один комплект (V151)'),
  ('audit.part.kit',         'audit', 'text',  'audit', 'журнал действий: комплектующая в составе комплекта (V151)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('sku.tab.kit', 'ru', 'Состав'),
    ('sku.tab.kit', 'en', 'Components'),
    ('sku.hint.kit_from_wms', 'ru', 'Состав комплекта ведёт склад в WMS. Своего остатка у комплекта нет: он собирается из комплектующих при отгрузке.'),
    ('sku.hint.kit_from_wms', 'en', 'The kit composition is maintained by the warehouse in WMS. A kit has no stock of its own: it is assembled from its components at shipment.'),
    ('sku.text.kit_empty', 'ru', 'Состав комплекта из WMS ещё не получен'),
    ('sku.text.kit_empty', 'en', 'The kit composition has not been received from WMS yet'),
    ('sku.column.kit_qty', 'ru', 'На один комплект'),
    ('sku.column.kit_qty', 'en', 'Per kit'),
    ('audit.part.kit', 'ru', 'комплектующая «{v}»'),
    ('audit.part.kit', 'en', 'component “{v}”')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order) VALUES
  ('sku_kit_component', 'depositor_id', 'skip', NULL, 1),
  ('sku_kit_component', 'kit_sku_id', 'skip', NULL, 2),
  ('sku_kit_component', 'component_sku_id', 'skip', NULL, 3),
  ('sku_kit_component', 'qty', 'text', 'audit.field.qty', 4),
  ('sku_kit_component', 'sort_order', 'text', 'audit.field.sort_order', 5),
  ('sku_kit_component', 'synced_at', 'skip', NULL, 6);
