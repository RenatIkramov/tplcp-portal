SET search_path = dbo;

-- =====================================================================
-- V139. Отчёты: места, товар, повторный запуск, срок хранения
-- (решения заказчика 14.11.2026).
--
-- 1. Отчётов в карточках документов больше нет: кнопку «Печать» убрали,
--    печатные формы приходят из WMS готовыми файлами во вкладку
--    «Прикреплённые файлы». Места 03 Поступления, 04 Заказы,
--    05 Перемещения и тип параметра 10 «номер документа» выключаются
--    (не удаляются: значения системные, на них могут ссылаться старые
--    запуски). Отчёты каталога в этих местах выключаются — администратор
--    увидит их в каталоге и решит, куда перенести.
--
-- 2. Новое место 06 «Карточка товара» (вкладка «Отчёты» карточки товара:
--    например, история движения товара) и тип параметра 11 «код товара» —
--    его подставляет портал, как код клиента.
--
-- 3. Один и тот же отчёт человек не запускает второй раз, пока первый
--    не закончился (кнопка блокируется; база держит правило и для двух
--    окон браузера) — уникальный индекс по незаконченным запускам.
--
-- 4. Срок хранения запусков — настройка report_run_retention_days
--    (по умолчанию 30 дней), меняется в «Настройке отчётов»; старше —
--    удаляется ночью (report_run_cleanup).
-- =====================================================================

-- ---------- 1. Места документов — выключены --------------------------------------

UPDATE enum_report_place SET is_active = false WHERE code IN ('03', '04', '05');
UPDATE enum_report_param_type SET is_active = false WHERE code = '10';
UPDATE report SET is_active = false, updated_at = now()
 WHERE place_id IN (SELECT id FROM enum_report_place WHERE code IN ('03', '04', '05'));

-- ---------- 2. Карточка товара ------------------------------------------------------

INSERT INTO enum_report_place (id, code, name, sort_order) VALUES (6, '06', 'Item card', 6);
INSERT INTO enum_report_param_type (id, code, name, sort_order) VALUES (11, '11', 'Item code (set by portal)', 11);
INSERT INTO enum_system_value (enum_key, code) VALUES
  ('enum_report_place', '06'), ('enum_report_param_type', '11');
INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT v.enum_key, v.value_id, l.locale_id, v.name
  FROM (VALUES ('enum_report_place', '6', 'Карточка товара'),
               ('enum_report_param_type', '11', 'Код товара (подставляет портал)')) AS v(enum_key, value_id, name)
  JOIN dictionary_locale l ON l.code = 'ru';

ALTER TABLE report_run
  ADD COLUMN sku_id uuid,
  ADD COLUMN sku_code varchar(100);
COMMENT ON COLUMN report_run.sku_id IS 'Товар отчёта из карточки товара (место 06), V139';

-- Список запусков — с товаром и местом отчёта: таблица раздела показывает
-- запуски своих отчётов, карточка товара — по своему товару.
DROP VIEW v_report_runs;
CREATE VIEW v_report_runs WITH (security_invoker = true) AS
SELECT r.run_id, r.report_id, r.report_name, r.wms_code, r.params, r.document_no,
       r.sku_id, r.sku_code, p.code AS place_code,
       s.code AS status_code, enum_name('enum_report_run_status', s.id::text, s.name) AS status_name,
       r.error_key, r.error_detail, r.file_name,
       r.requested_login, u.full_name AS requested_name,
       r.requested_at, r.finished_at
  FROM report_run r
  JOIN enum_report_run_status s ON s.id = r.status_id
  LEFT JOIN report rp ON rp.report_id = r.report_id
  LEFT JOIN enum_report_place p ON p.id = rp.place_id
  LEFT JOIN app_user u ON u.user_id = r.requested_by;
GRANT SELECT ON v_report_runs TO portal_app;


-- Код товара (11) подставляет портал, как код клиента (09): такой параметр
-- всегда скрыт и значения у клиента не требует — функции V135/V137 знали
-- только 09 и 10.
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

CREATE OR REPLACE FUNCTION admin_depositor_reports(p_depositor_id uuid)
RETURNS TABLE (report_id uuid, wms_code text, name text, place_code text, report_active boolean,
               assigned boolean, custom_name text, sort_order integer, is_active boolean,
               param_values text, hidden_params text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT r.report_id, r.wms_code::text, report_name(r.report_id, r.name), p.code::text, r.is_active,
         dr.report_id IS NOT NULL, dr.custom_name::text,
         COALESCE(dr.sort_order, r.sort_order)::integer, COALESCE(dr.is_active, false),
         COALESCE(dr.param_values, '{}')::text,
         COALESCE((SELECT jsonb_agg(jsonb_build_object(
                             'key', rp.param_key,
                             'label', report_param_label(rp.param_id, rp.label),
                             'type', t.code,
                             'defaultValue', rp.default_value,
                             'useCurrentDate', rp.use_current_date)
                           ORDER BY rp.sort_order)
                     FROM report_param rp JOIN enum_report_param_type t ON t.id = rp.type_id
                    WHERE rp.report_id = r.report_id AND rp.is_hidden AND rp.type_id NOT IN (9, 10, 11)),
                  '[]')::text
    FROM report r
    JOIN enum_report_place p ON p.id = r.place_id
    LEFT JOIN depositor_report dr ON dr.report_id = r.report_id AND dr.depositor_id = p_depositor_id
   ORDER BY p.sort_order, COALESCE(dr.sort_order, r.sort_order), 3
$$;

-- ---------- 3. Один незаконченный запуск отчёта на человека -------------------------

CREATE UNIQUE INDEX report_run_one_open ON report_run (report_id, requested_login)
  WHERE status_id IN (1, 2);

-- ---------- 4. Срок хранения -------------------------------------------------------

INSERT INTO portal_setting (setting_key, value, note) VALUES
  ('report_run_retention_days', '30',
   'сколько дней хранить запуски отчётов (и ссылки на файлы); старше — удаляется раз в сутки')
ON CONFLICT (setting_key) DO NOTHING;

CREATE FUNCTION report_run_cleanup()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_days    integer;
  v_removed integer;
BEGIN
  SELECT value::integer INTO v_days FROM portal_setting WHERE setting_key = 'report_run_retention_days';
  DELETE FROM report_run
   WHERE requested_at < now() - make_interval(days => COALESCE(v_days, 30))
     AND status_id IN (3, 4);
  GET DIAGNOSTICS v_removed = ROW_COUNT;
  RETURN v_removed;
END
$$;

CREATE FUNCTION admin_set_report_retention(p_days integer)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  IF p_days IS NULL OR p_days < 1 OR p_days > 365 THEN
    RAISE EXCEPTION 'report retention out of range: %', p_days;
  END IF;
  UPDATE portal_setting SET value = p_days::text WHERE setting_key = 'report_run_retention_days';
  RETURN report_run_cleanup();
END
$$;

REVOKE EXECUTE ON FUNCTION report_run_cleanup(), admin_set_report_retention(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION report_run_cleanup(), admin_set_report_retention(integer) TO portal_app;

-- ---------- Тексты ---------------------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.report_run.already_running', 'server', 'message', 'reports', 'отчёт уже формируется у этого человека (V139)'),
  ('error.report_run.item_required',   'server', 'message', 'reports', 'отчёт по товару без товара (V139)'),
  ('error.report.retention_range',     'server', 'message', 'reports', 'срок хранения отчётов вне допустимого (V139)'),
  ('reports.label.retention_days',     'admin',  'label',   'reports', 'срок хранения готовых отчётов, дней (V139)'),
  ('reports.message.retention_saved',  'admin',  'message', 'reports', 'срок хранения сохранён (V139)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.report_run.already_running', 'ru', 'Этот отчёт уже формируется — дождитесь его'),
    ('error.report_run.already_running', 'en', 'This report is already being produced — please wait for it'),
    ('error.report_run.item_required', 'ru', 'Этот отчёт формируется из карточки товара'),
    ('error.report_run.item_required', 'en', 'This report is produced from an item card'),
    ('error.report.retention_range', 'ru', 'Срок хранения — от {min} до {max} дней'),
    ('error.report.retention_range', 'en', 'Retention is {min} to {max} days'),
    ('reports.label.retention_days', 'ru', 'Хранить готовые отчёты, дней'),
    ('reports.label.retention_days', 'en', 'Keep finished reports, days'),
    ('reports.message.retention_saved', 'ru', 'Срок хранения сохранён'),
    ('reports.message.retention_saved', 'en', 'Retention saved')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
