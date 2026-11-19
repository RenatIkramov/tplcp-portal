SET search_path = dbo;

-- =====================================================================
-- V137. Значение скрытого параметра — у клиента (решение 14.11.2026).
--
-- ЧТО БЫЛО (V135). Скрытый параметр обязан был иметь значение в каталоге —
-- одно на всех клиентов.
--
-- КАК ТЕПЕРЬ. Скрытый параметр в каталоге может быть без значения: его
-- задают при выдаче отчёта клиенту, во вкладке «Отчёты» карточки
-- поклажедателя — у каждого клиента своё. Значение из каталога — умолчание:
-- у клиента пусто — уходит оно. Ни там, ни там нет — выдать отчёт нельзя
-- (проверяет портал: error.report.client_value_required).
--
--   depositor_report.param_values  {"код параметра WMS": "значение"}
--
-- Ключ — код параметра, а не param_id: параметры отчёта сохраняются
-- целиком (V135, admin_report_save), и param_id при каждой правке новый,
-- а код параметра в WMS — тот же.
--
-- Попутно: «Ключ для WMS» в окне отчёта переименован в «Код параметра в
-- WMS» — так его и называют, рядом с «Кодом отчёта в WMS».
-- =====================================================================

DO $$
DECLARE
  v_name text;
BEGIN
  SELECT conname INTO v_name FROM pg_constraint
   WHERE conrelid = 'report_param'::regclass AND contype = 'c'
     AND pg_get_constraintdef(oid) LIKE '%is_hidden%';
  IF v_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE report_param DROP CONSTRAINT %I', v_name);
  END IF;
END
$$;

ALTER TABLE depositor_report
  ADD COLUMN param_values jsonb NOT NULL DEFAULT '{}'
  CHECK (jsonb_typeof(param_values) = 'object');
COMMENT ON COLUMN depositor_report.param_values IS
  'Значения скрытых параметров для этого клиента: {"код параметра WMS": "значение"} (V137)';

-- ---------- Отчёты клиента: со скрытыми параметрами и их значениями ----------

DROP FUNCTION admin_depositor_reports(uuid);

-- hidden_params — скрытые параметры, которые задаёт человек (не 09, 10):
-- [{"key", "label", "type", "defaultValue"}] в порядке окна.
CREATE FUNCTION admin_depositor_reports(p_depositor_id uuid)
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
                    WHERE rp.report_id = r.report_id AND rp.is_hidden AND rp.type_id NOT IN (9, 10)),
                  '[]')::text
    FROM report r
    JOIN enum_report_place p ON p.id = r.place_id
    LEFT JOIN depositor_report dr ON dr.report_id = r.report_id AND dr.depositor_id = p_depositor_id
   ORDER BY p.sort_order, COALESCE(dr.sort_order, r.sort_order), 3
$$;

-- Набор клиента целиком, теперь и со значениями скрытых параметров:
--   p_rows [{"reportId", "customName", "sortOrder", "active", "values": {...}}, ...]
CREATE OR REPLACE FUNCTION admin_depositor_reports_save(p_depositor_id uuid, p_rows jsonb)
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
  INSERT INTO depositor_report (depositor_id, report_id, custom_name, sort_order, is_active, param_values)
  SELECT p_depositor_id, (x ->> 'reportId')::uuid,
         NULLIF(btrim(x ->> 'customName'), ''),
         COALESCE((x ->> 'sortOrder')::integer, 0),
         COALESCE((x ->> 'active')::boolean, true),
         COALESCE(x -> 'values', '{}')
    FROM jsonb_array_elements(COALESCE(p_rows, '[]')) x
  ON CONFLICT (depositor_id, report_id) DO UPDATE
     SET custom_name  = excluded.custom_name,
         sort_order   = excluded.sort_order,
         is_active    = excluded.is_active,
         param_values = excluded.param_values;
END
$$;

REVOKE EXECUTE ON FUNCTION admin_depositor_reports(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_depositor_reports(uuid) TO portal_app;

-- ---------- Тексты ---------------------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.report.client_value_required', 'server', 'message', 'reports',
   'скрытому параметру нужно значение у клиента или в каталоге (V137)'),
  ('reports.button.add_to_client',  'admin', 'button', 'reports', 'выдать клиенту отчёт из каталога (V137)'),
  ('reports.label.choose_report',   'admin', 'label',  'reports', 'выбор отчёта из каталога (V137)'),
  ('reports.text.all_assigned',     'admin', 'text',   'reports', 'клиенту выданы все отчёты каталога (V137)'),
  ('reports.text.none_assigned',    'admin', 'text',   'reports', 'клиенту не выдано ни одного отчёта (V137)'),
  ('reports.label.hidden_values',   'admin', 'label',  'reports', 'значения скрытых параметров у клиента (V137)'),
  ('reports.button.remove_from_client', 'admin', 'button', 'reports', 'снять отчёт с клиента (V137)'),
  ('reports.hint.hidden_value',     'admin', 'hint',   'reports', 'подсказка к «Скрыт» в окне отчёта (V137)'),
  ('reports.text.default_is',       'admin', 'text',   'reports', 'значение по умолчанию из каталога (V137)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.report.client_value_required', 'ru', 'Отчёт «{report}»: задайте значение параметра «{label}» — он скрыт, и человек его не заполнит'),
    ('error.report.client_value_required', 'en', 'Report “{report}”: set the value of “{label}” — it is hidden and nobody will fill it in'),
    ('reports.button.add_to_client', 'ru', '+ Добавить отчёт'),
    ('reports.button.add_to_client', 'en', '+ Add report'),
    ('reports.label.choose_report', 'ru', 'Отчёт из каталога'),
    ('reports.label.choose_report', 'en', 'Report from the catalogue'),
    ('reports.text.all_assigned', 'ru', 'Клиенту выданы все отчёты каталога'),
    ('reports.text.all_assigned', 'en', 'The client has all catalogue reports'),
    ('reports.text.none_assigned', 'ru', 'Клиенту пока не выдано ни одного отчёта'),
    ('reports.text.none_assigned', 'en', 'No reports assigned to the client yet'),
    ('reports.label.hidden_values', 'ru', 'Скрытые параметры — значения для этого клиента'),
    ('reports.label.hidden_values', 'en', 'Hidden parameters — values for this client'),
    ('reports.button.remove_from_client', 'ru', 'Убрать у клиента'),
    ('reports.button.remove_from_client', 'en', 'Remove from client'),
    ('reports.hint.hidden_value', 'ru', 'значение — здесь или у клиента'),
    ('reports.hint.hidden_value', 'en', 'value here or per client'),
    ('reports.text.default_is', 'ru', 'по умолчанию: {value}'),
    ('reports.text.default_is', 'en', 'default: {value}'),
    ('reports.hint.depositor', 'ru', 'Отчёты, доступные клиенту. Своё название — одно на все языки; пусто — название из каталога.'),
    ('reports.hint.depositor', 'en', 'Reports available to the client. The own name is one for all languages; empty — the catalogue name.'),
    -- переименование (V136): так его и называют
    ('reports.column.param_key', 'ru', 'Код параметра в WMS'),
    ('reports.column.param_key', 'en', 'WMS parameter code'),
    ('reports.hint.params', 'ru', 'Код параметра — его имя в запросе к WMS, не переводится. Скрытый параметр человек не видит: уходит значение, заданное у клиента (вкладка «Отчёты» карточки поклажедателя), а если там пусто — значение по умолчанию отсюда.'),
    ('reports.hint.params', 'en', 'The parameter code is its name in the WMS request and is not translated. A hidden parameter is not shown: the value set for the client (“Reports” tab of the depositor card) is sent, or the default value from here if that is empty.'),
    ('error.report.param_key_required', 'ru', 'У параметра нужен код параметра в WMS — без пробелов'),
    ('error.report.param_key_required', 'en', 'The parameter needs a WMS parameter code — no spaces'),
    ('error.report.param_key_twice', 'ru', 'Код параметра повторяется: {key}'),
    ('error.report.param_key_twice', 'en', 'The parameter code is used twice: {key}')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Отказ «скрытому нужно значение» в каталоге больше не нужен (V135).
DELETE FROM ui_text WHERE text_key = 'error.report.hidden_needs_value';
DELETE FROM ui_text_key WHERE text_key = 'error.report.hidden_needs_value';
