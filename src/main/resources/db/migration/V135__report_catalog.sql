SET search_path = dbo;

-- =====================================================================
-- V135. Каталог отчётов (решение 13.11.2026; прежде — демо, js/demo/reports.js).
--
-- Отчёты формирует WMS: портал шлёт ей код отчёта и параметры (решение
-- 04.11.2026) и получает файл. Портал хранит каталог: какие отчёты есть,
-- с какими параметрами, кому из клиентов какой доступен.
--
--   report              настроенный отчёт, общий для всех клиентов.
--                       wms_code не уникален: один отчёт WMS можно завести
--                       несколькими строками с разным набором параметров.
--   report_text         название на других языках (основное — английское,
--                       как у перечислений; нет перевода — основное).
--   report_param        параметры: ключ для WMS, подпись, тип, умолчания.
--   report_param_text   подпись параметра на других языках.
--   depositor_report    какие отчёты доступны клиенту; своё название
--                       (одно на все языки, решение 13.11.2026), порядок.
--
-- Перечисления (в группе «Перечисления» администрирования, системные):
--   enum_report_place       где кнопка: 01 Отчёты, 02 Биллинг,
--                           03 Поступления, 04 Заказы, 05 Перемещения;
--   enum_report_param_type  тип параметра. 09 и 10 подставляет портал сам
--                           (код клиента в WMS, номер документа) —
--                           человек их не видит и подменить не может.
--
-- Каталог портал читает напрямую; пишет — только функциями admin_report_*
-- (право администратора проверяет портал, как во всём администрировании).
-- depositor_report — под RLS: клиент видит только свои отчёты.
-- =====================================================================

-- ---------- Перечисления -------------------------------------------------------

CREATE TABLE enum_report_place (
  id         smallint PRIMARY KEY,
  code       varchar(30) NOT NULL UNIQUE CHECK (code ~ '^[0-9]{2}$'),
  name       varchar(255) NOT NULL,
  sort_order smallint,
  is_active  boolean NOT NULL DEFAULT true
);

CREATE TABLE enum_report_param_type (
  id         smallint PRIMARY KEY,
  code       varchar(30) NOT NULL UNIQUE CHECK (code ~ '^[0-9]{2}$'),
  name       varchar(255) NOT NULL,
  sort_order smallint,
  is_active  boolean NOT NULL DEFAULT true
);

-- id = число кода, как у типов заказов (V88)
INSERT INTO enum_report_place (id, code, name, sort_order) VALUES
  (1, '01', 'Reports',   1),
  (2, '02', 'Billing',   2),
  (3, '03', 'Receipts',  3),
  (4, '04', 'Orders',    4),
  (5, '05', 'Transfers', 5);

INSERT INTO enum_report_param_type (id, code, name, sort_order) VALUES
  (1,  '01', 'Date',                         1),
  (2,  '02', 'Number',                       2),
  (3,  '03', 'Text',                         3),
  (4,  '04', 'Warehouse',                    4),
  (5,  '05', 'Supplier',                     5),
  (6,  '06', 'Consignee',                    6),
  (7,  '07', 'Order type',                   7),
  (8,  '08', 'Receipt type',                 8),
  (9,  '09', 'Depositor code (set by portal)', 9),
  (10, '10', 'Document number (set by portal)', 10);

INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order, code_pattern) VALUES
  ('enum_report_place',      'code', false, 'enums.name.enum_report_place',      180, '^[0-9]{2}$'),
  ('enum_report_param_type', 'code', false, 'enums.name.enum_report_param_type', 185, '^[0-9]{2}$');

-- Имена и переводы значений правит окно «Перечисления» (EnumValueService)
-- прямым запросом, как у остальных перечислений; журнал — тем же триггером.
GRANT SELECT, INSERT, UPDATE, DELETE ON enum_report_place, enum_report_param_type TO portal_app;
CREATE TRIGGER audit AFTER INSERT OR DELETE OR UPDATE ON enum_report_place
  FOR EACH ROW EXECUTE FUNCTION audit_row();
CREATE TRIGGER audit AFTER INSERT OR DELETE OR UPDATE ON enum_report_param_type
  FOR EACH ROW EXECUTE FUNCTION audit_row();

-- Что показывать в журнале — как у остальных перечислений (V90).
INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order)
SELECT t.tbl, f.col, f.kind, f.text_key, f.ord
  FROM (VALUES ('enum_report_place'), ('enum_report_param_type')) AS t(tbl),
       (VALUES ('id', 'skip', NULL, 1), ('code', 'text', 'audit.field.code', 2),
               ('name', 'text', 'audit.field.name', 3), ('sort_order', 'skip', NULL, 4),
               ('is_active', 'bool', 'audit.field.is_active', 5)) AS f(col, kind, text_key, ord);

-- Поведение портала держится на кодах — значения системные: не удаляются,
-- не отключаются, код не меняется; имя и переводы править можно (V95).
INSERT INTO enum_system_value (enum_key, code)
SELECT 'enum_report_place', code FROM enum_report_place
UNION ALL
SELECT 'enum_report_param_type', code FROM enum_report_param_type;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT v.enum_key, v.value_id, l.locale_id, v.name
  FROM (VALUES
    ('enum_report_place', '1', 'Отчёты'),
    ('enum_report_place', '2', 'Биллинг'),
    ('enum_report_place', '3', 'Поступления'),
    ('enum_report_place', '4', 'Заказы'),
    ('enum_report_place', '5', 'Перемещения'),
    ('enum_report_param_type', '1',  'Дата'),
    ('enum_report_param_type', '2',  'Число'),
    ('enum_report_param_type', '3',  'Строка'),
    ('enum_report_param_type', '4',  'Склад'),
    ('enum_report_param_type', '5',  'Поставщик'),
    ('enum_report_param_type', '6',  'Грузополучатель'),
    ('enum_report_param_type', '7',  'Тип заказа'),
    ('enum_report_param_type', '8',  'Тип поступления'),
    ('enum_report_param_type', '9',  'Код поклажедателя (подставляет портал)'),
    ('enum_report_param_type', '10', 'Номер документа (подставляет портал)')
  ) AS v(enum_key, value_id, name)
  JOIN dictionary_locale l ON l.code = 'ru';

-- ---------- Каталог ------------------------------------------------------------

CREATE TABLE report (
  report_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  wms_code   varchar(100) NOT NULL CHECK (wms_code ~ '^\S+$'),
  name       varchar(255) NOT NULL CHECK (btrim(name) <> ''),
  place_id   smallint NOT NULL REFERENCES enum_report_place (id),
  sort_order smallint NOT NULL DEFAULT 0,
  is_active  boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE report IS 'Каталог отчётов WMS (V135): настроенный отчёт, общий для всех клиентов';
COMMENT ON COLUMN report.wms_code IS 'Код отчёта в запросе к WMS; не уникален — один отчёт с разными параметрами';

CREATE TABLE report_text (
  report_id uuid NOT NULL REFERENCES report (report_id) ON DELETE CASCADE,
  locale_id smallint NOT NULL REFERENCES dictionary_locale (locale_id),
  name      varchar(255) NOT NULL CHECK (btrim(name) <> ''),
  PRIMARY KEY (report_id, locale_id)
);

CREATE TABLE report_param (
  param_id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  report_id        uuid NOT NULL REFERENCES report (report_id) ON DELETE CASCADE,
  param_key        varchar(100) NOT NULL CHECK (param_key ~ '^\S+$'),
  label            varchar(255) NOT NULL CHECK (btrim(label) <> ''),
  type_id          smallint NOT NULL REFERENCES enum_report_param_type (id),
  is_required      boolean NOT NULL DEFAULT false,
  default_value    varchar(255),
  use_current_date boolean NOT NULL DEFAULT false,
  is_hidden        boolean NOT NULL DEFAULT false,
  sort_order       smallint NOT NULL DEFAULT 0,
  UNIQUE (report_id, param_key),
  -- «текущая дата» — только у даты
  CHECK (NOT use_current_date OR type_id = 1),
  -- скрытому параметру нужно значение: его человек не введёт
  CHECK (NOT is_hidden OR type_id IN (9, 10) OR use_current_date OR NULLIF(btrim(default_value), '') IS NOT NULL)
);
COMMENT ON COLUMN report_param.param_key IS 'Имя параметра в запросе к WMS; не переводится';

CREATE TABLE report_param_text (
  param_id  uuid NOT NULL REFERENCES report_param (param_id) ON DELETE CASCADE,
  locale_id smallint NOT NULL REFERENCES dictionary_locale (locale_id),
  label     varchar(255) NOT NULL CHECK (btrim(label) <> ''),
  PRIMARY KEY (param_id, locale_id)
);

CREATE TABLE depositor_report (
  depositor_id uuid NOT NULL REFERENCES depositor (depositor_id),
  report_id    uuid NOT NULL REFERENCES report (report_id),
  custom_name  varchar(255) CHECK (custom_name IS NULL OR btrim(custom_name) <> ''),
  sort_order   smallint NOT NULL DEFAULT 0,
  is_active    boolean NOT NULL DEFAULT true,
  PRIMARY KEY (depositor_id, report_id)
);
COMMENT ON TABLE depositor_report IS 'Отчёты, доступные клиенту (V135); custom_name — своё название, одно на все языки';

ALTER TABLE depositor_report ENABLE ROW LEVEL SECURITY;
ALTER TABLE depositor_report FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON depositor_report
  USING (depositor_id = current_depositor_id())
  WITH CHECK (depositor_id = current_depositor_id());

-- Права (V134: новые таблицы у портала — только чтение). Запись — функциями.
GRANT SELECT ON enum_report_place, enum_report_param_type, report, report_text,
                report_param, report_param_text, depositor_report TO portal_app;

-- ---------- Имена на языке сеанса ---------------------------------------------

CREATE FUNCTION report_name(p_report_id uuid, p_default text)
RETURNS text
LANGUAGE sql
STABLE
SET search_path = dbo, pg_temp
AS $$
  SELECT COALESCE(
           (SELECT t.name FROM report_text t
              JOIN dictionary_locale l ON l.locale_id = t.locale_id
             WHERE t.report_id = p_report_id
               AND l.code = NULLIF(current_setting('app.locale', true), '')),
           p_default)
$$;

CREATE FUNCTION report_param_label(p_param_id uuid, p_default text)
RETURNS text
LANGUAGE sql
STABLE
SET search_path = dbo, pg_temp
AS $$
  SELECT COALESCE(
           (SELECT t.label FROM report_param_text t
              JOIN dictionary_locale l ON l.locale_id = t.locale_id
             WHERE t.param_id = p_param_id
               AND l.code = NULLIF(current_setting('app.locale', true), '')),
           p_default)
$$;

-- ---------- Что видит клиент -----------------------------------------------------
-- Под RLS (security_invoker): только отчёты своего клиента, действующие и
-- в каталоге, и у клиента. Своё название клиента — поверх перевода.

CREATE VIEW v_client_reports WITH (security_invoker = true) AS
SELECT r.report_id,
       COALESCE(dr.custom_name, report_name(r.report_id, r.name)) AS name,
       p.code AS place_code,
       dr.sort_order,
       r.wms_code
  FROM depositor_report dr
  JOIN report r ON r.report_id = dr.report_id
  JOIN enum_report_place p ON p.id = r.place_id
 WHERE dr.is_active AND r.is_active;

-- Параметры, которые человек заполняет; скрытые и подставляемые порталом
-- (09, 10) экрану не нужны — их значения портал берёт сам.
CREATE VIEW v_client_report_params WITH (security_invoker = true) AS
SELECT rp.report_id,
       rp.param_key,
       report_param_label(rp.param_id, rp.label) AS label,
       t.code AS type_code,
       rp.is_required,
       rp.default_value,
       rp.use_current_date,
       rp.sort_order
  FROM report_param rp
  JOIN enum_report_param_type t ON t.id = rp.type_id
 WHERE NOT rp.is_hidden AND rp.type_id NOT IN (9, 10)
   AND EXISTS (SELECT 1 FROM v_client_reports c WHERE c.report_id = rp.report_id);

GRANT SELECT ON v_client_reports, v_client_report_params TO portal_app;

-- ---------- Ведение каталога (администратор) ----------------------------------
-- Отчёт сохраняется целиком: шапка, переводы и параметры одним вызовом,
-- как его и правят — в одном окне.
--   p_texts  {"ru": "Остатки товаров", ...}
--   p_params [{"key", "label", "type", "required", "default", "currentDate",
--              "hidden", "texts": {"ru": ...}}, ...] — в порядке экрана

CREATE FUNCTION admin_report_save(p_report_id uuid, p_wms_code text, p_name text,
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
           COALESCE((p ->> 'hidden')::boolean, false) OR t.id IN (9, 10),
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

-- Скольким клиентам выдан отчёт — без учёта RLS: удалить выданный нельзя,
-- его можно только выключить.
CREATE FUNCTION admin_report_usage(p_report_id uuid)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT count(*)::integer FROM depositor_report WHERE report_id = p_report_id
$$;

CREATE FUNCTION admin_report_delete(p_report_id uuid)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  DELETE FROM report WHERE report_id = p_report_id
$$;

-- Отчёты для карточки клиента: весь каталог, у выданных — своё название,
-- порядок и активность.
CREATE FUNCTION admin_depositor_reports(p_depositor_id uuid)
RETURNS TABLE (report_id uuid, wms_code text, name text, place_code text, report_active boolean,
               assigned boolean, custom_name text, sort_order integer, is_active boolean)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT r.report_id, r.wms_code::text, report_name(r.report_id, r.name), p.code::text, r.is_active,
         dr.report_id IS NOT NULL, dr.custom_name::text,
         COALESCE(dr.sort_order, r.sort_order)::integer, COALESCE(dr.is_active, false)
    FROM report r
    JOIN enum_report_place p ON p.id = r.place_id
    LEFT JOIN depositor_report dr ON dr.report_id = r.report_id AND dr.depositor_id = p_depositor_id
   ORDER BY p.sort_order, COALESCE(dr.sort_order, r.sort_order), 3
$$;

-- Сохранить набор отчётов клиента целиком: чего нет в списке — снято.
--   p_rows [{"reportId", "customName", "sortOrder", "active"}, ...]
CREATE FUNCTION admin_depositor_reports_save(p_depositor_id uuid, p_rows jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  DELETE FROM depositor_report d
   WHERE d.depositor_id = p_depositor_id
     AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(p_rows, '[]')) x
                      WHERE (x ->> 'reportId')::uuid = d.report_id);
  INSERT INTO depositor_report (depositor_id, report_id, custom_name, sort_order, is_active)
  SELECT p_depositor_id, (x ->> 'reportId')::uuid,
         NULLIF(btrim(x ->> 'customName'), ''),
         COALESCE((x ->> 'sortOrder')::integer, 0),
         COALESCE((x ->> 'active')::boolean, true)
    FROM jsonb_array_elements(COALESCE(p_rows, '[]')) x
  ON CONFLICT (depositor_id, report_id) DO UPDATE
     SET custom_name = excluded.custom_name,
         sort_order  = excluded.sort_order,
         is_active   = excluded.is_active;
END
$$;

-- V134 выдаёт новые функции portal_app и не выдаёт PUBLIC — здесь явно,
-- чтобы миграция не зависела от умолчаний.
REVOKE EXECUTE ON FUNCTION report_name(uuid, text), report_param_label(uuid, text),
                           admin_report_save(uuid, text, text, text, integer, boolean, jsonb, jsonb),
                           admin_report_usage(uuid), admin_report_delete(uuid),
                           admin_depositor_reports(uuid), admin_depositor_reports_save(uuid, jsonb)
  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION report_name(uuid, text), report_param_label(uuid, text),
                          admin_report_save(uuid, text, text, text, integer, boolean, jsonb, jsonb),
                          admin_report_usage(uuid), admin_report_delete(uuid),
                          admin_depositor_reports(uuid), admin_depositor_reports_save(uuid, jsonb)
  TO portal_app;

-- ---------- Тексты --------------------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('enums.name.enum_report_place',      'admin',  'label',   'reports', 'перечисление: где кнопка отчёта (V135)'),
  ('enums.name.enum_report_param_type', 'admin',  'label',   'reports', 'перечисление: тип параметра отчёта (V135)'),
  ('error.report.not_found',            'server', 'message', 'reports', 'нет такого отчёта (V135)'),
  ('error.report.in_use',               'server', 'message', 'reports', 'отчёт выдан клиентам — удалить нельзя (V135)'),
  ('error.report.wms_code_required',    'server', 'message', 'reports', 'не задан код отчёта WMS (V135)'),
  ('error.report.name_required',        'server', 'message', 'reports', 'не задано название отчёта (V135)'),
  ('error.report.bad_place',            'server', 'message', 'reports', 'неизвестное место отчёта (V135)'),
  ('error.report.param_key_required',   'server', 'message', 'reports', 'у параметра нет ключа WMS (V135)'),
  ('error.report.param_key_twice',      'server', 'message', 'reports', 'ключ параметра повторяется (V135)'),
  ('error.report.param_label_required', 'server', 'message', 'reports', 'у параметра нет подписи (V135)'),
  ('error.report.bad_param_type',       'server', 'message', 'reports', 'неизвестный тип параметра (V135)'),
  ('error.report.hidden_needs_value',   'server', 'message', 'reports', 'скрытому параметру нужно значение (V135)'),
  ('error.report.current_date_only_date','server','message', 'reports', '«текущая дата» — только у даты (V135)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('enums.name.enum_report_place', 'ru', 'Место отчёта'),
    ('enums.name.enum_report_place', 'en', 'Report place'),
    ('enums.name.enum_report_param_type', 'ru', 'Тип параметра отчёта'),
    ('enums.name.enum_report_param_type', 'en', 'Report parameter type'),
    ('error.report.not_found', 'ru', 'Отчёт не найден'),
    ('error.report.not_found', 'en', 'Report not found'),
    ('error.report.in_use', 'ru', 'Отчёт выдан клиентам — удалить его нельзя, можно выключить'),
    ('error.report.in_use', 'en', 'The report is assigned to clients — it cannot be deleted, only switched off'),
    ('error.report.wms_code_required', 'ru', 'Укажите код отчёта в WMS — без пробелов'),
    ('error.report.wms_code_required', 'en', 'Enter the WMS report code — no spaces'),
    ('error.report.name_required', 'ru', 'Укажите название отчёта'),
    ('error.report.name_required', 'en', 'Enter the report name'),
    ('error.report.bad_place', 'ru', 'Выберите, где показывать отчёт'),
    ('error.report.bad_place', 'en', 'Choose where to show the report'),
    ('error.report.param_key_required', 'ru', 'У параметра нужен ключ для WMS — без пробелов'),
    ('error.report.param_key_required', 'en', 'The parameter needs a WMS key — no spaces'),
    ('error.report.param_key_twice', 'ru', 'Ключ параметра повторяется: {key}'),
    ('error.report.param_key_twice', 'en', 'The parameter key is used twice: {key}'),
    ('error.report.param_label_required', 'ru', 'У параметра нужна подпись'),
    ('error.report.param_label_required', 'en', 'The parameter needs a label'),
    ('error.report.bad_param_type', 'ru', 'Выберите тип параметра'),
    ('error.report.bad_param_type', 'en', 'Choose the parameter type'),
    ('error.report.hidden_needs_value', 'ru', 'Скрытый параметр «{label}» человек не заполнит — задайте значение'),
    ('error.report.hidden_needs_value', 'en', 'Nobody will fill in the hidden parameter “{label}” — set a value'),
    ('error.report.current_date_only_date', 'ru', '«Текущая дата» бывает только у параметра-даты'),
    ('error.report.current_date_only_date', 'en', '“Current date” applies to date parameters only')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
