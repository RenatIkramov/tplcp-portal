SET search_path = dbo;

-- =====================================================================
-- V138. Запуск отчётов (каталог — V135, V137).
--
-- WMS отвечает на запрос отчёта заданием, файл готов позже (решение
-- 13.11.2026). Портал записывает запуск, отправляет запрос, раз в
-- portal.reports.poll-ms спрашивает WMS о готовности; готовый файл
-- забирает у WMS по номеру задания, когда человек нажимает «Скачать»
-- (файлового хранилища пока нет — решение 13.11.2026).
--
--   report_run   запуск: кто, какой отчёт (название и код — как были на
--                момент запуска), с какими значениями ушёл запрос, номер
--                задания WMS, статус, ошибка, имя файла.
--
-- Статусы — перечисление enum_report_run_status (системное):
--   01 В очереди   — записан, запрос в WMS ещё не ушёл;
--   02 Формируется — WMS приняла задание;
--   03 Готов       — файл можно скачать;
--   04 Ошибка      — WMS отказала, не ответила или не успела.
--
-- Под RLS: запуски клиента видят все его пользователи (решение 13.11.2026).
-- Портал запуск только вставляет; статус меняют функции report_run_*:
-- опрос готовности идёт без входа, по всем клиентам сразу.
-- =====================================================================

CREATE TABLE enum_report_run_status (
  id         smallint PRIMARY KEY,
  code       varchar(30) NOT NULL UNIQUE CHECK (code ~ '^[0-9]{2}$'),
  name       varchar(255) NOT NULL,
  sort_order smallint,
  is_active  boolean NOT NULL DEFAULT true
);

INSERT INTO enum_report_run_status (id, code, name, sort_order) VALUES
  (1, '01', 'Queued',      1),
  (2, '02', 'In progress', 2),
  (3, '03', 'Ready',       3),
  (4, '04', 'Failed',      4);

INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order, code_pattern)
VALUES ('enum_report_run_status', 'code', false, 'enums.name.enum_report_run_status', 190, '^[0-9]{2}$');

INSERT INTO enum_system_value (enum_key, code)
SELECT 'enum_report_run_status', code FROM enum_report_run_status;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_report_run_status', v.id, l.locale_id, v.name
  FROM (VALUES ('1', 'В очереди'), ('2', 'Формируется'), ('3', 'Готов'), ('4', 'Ошибка')) AS v(id, name)
  JOIN dictionary_locale l ON l.code = 'ru';

GRANT SELECT, INSERT, UPDATE, DELETE ON enum_report_run_status TO portal_app;
CREATE TRIGGER audit AFTER INSERT OR DELETE OR UPDATE ON enum_report_run_status
  FOR EACH ROW EXECUTE FUNCTION audit_row();
INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order) VALUES
  ('enum_report_run_status', 'id',         'skip', NULL, 1),
  ('enum_report_run_status', 'code',       'text', 'audit.field.code', 2),
  ('enum_report_run_status', 'name',       'text', 'audit.field.name', 3),
  ('enum_report_run_status', 'sort_order', 'skip', NULL, 4),
  ('enum_report_run_status', 'is_active',  'bool', 'audit.field.is_active', 5);

-- ---------- Запуски --------------------------------------------------------------

CREATE TABLE report_run (
  run_id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id   uuid NOT NULL REFERENCES depositor (depositor_id),
  report_id      uuid REFERENCES report (report_id) ON DELETE SET NULL,
  requested_by   uuid REFERENCES app_user (user_id) ON DELETE SET NULL,
  requested_login varchar(255) NOT NULL,
  report_name    varchar(255) NOT NULL,
  wms_code       varchar(100) NOT NULL,
  params         jsonb NOT NULL DEFAULT '{}',
  doc_id         uuid,
  document_no    varchar(50),
  status_id      smallint NOT NULL DEFAULT 1 REFERENCES enum_report_run_status (id),
  wms_job_id     varchar(200),
  error_key      varchar(100),
  error_detail   text,
  file_name      varchar(255),
  content_type   varchar(100),
  requested_at   timestamptz NOT NULL DEFAULT now(),
  started_at     timestamptz,
  finished_at    timestamptz,
  next_check_at  timestamptz,
  checks         integer NOT NULL DEFAULT 0
);
COMMENT ON TABLE report_run IS 'Запуски отчётов WMS (V138); статус меняют функции report_run_*';
COMMENT ON COLUMN report_run.params IS 'Значения, с которыми ушёл запрос: {"код параметра WMS": "значение"}';
CREATE INDEX report_run_depositor ON report_run (depositor_id, requested_at DESC);
CREATE INDEX report_run_due ON report_run (next_check_at) WHERE status_id = 2;

ALTER TABLE report_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_run FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON report_run
  USING (depositor_id = current_depositor_id())
  WITH CHECK (depositor_id = current_depositor_id());

-- Портал запуск вставляет и читает; статус — только функциями ниже.
GRANT SELECT, INSERT ON report_run TO portal_app;

-- Список запусков клиента: кто запустил — именем, статус — на языке сеанса.
CREATE VIEW v_report_runs WITH (security_invoker = true) AS
SELECT r.run_id, r.report_id, r.report_name, r.wms_code, r.params, r.document_no,
       s.code AS status_code, enum_name('enum_report_run_status', s.id::text, s.name) AS status_name,
       r.error_key, r.error_detail, r.file_name,
       r.requested_login, u.full_name AS requested_name,
       r.requested_at, r.finished_at
  FROM report_run r
  JOIN enum_report_run_status s ON s.id = r.status_id
  LEFT JOIN app_user u ON u.user_id = r.requested_by;
GRANT SELECT ON v_report_runs TO portal_app;

-- ---------- Смена статуса -------------------------------------------------------

-- WMS приняла задание: 01 → 02, первая проверка — через p_check_seconds.
CREATE FUNCTION report_run_started(p_run_id uuid, p_job_id text, p_check_seconds integer)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  UPDATE report_run
     SET status_id = 2, wms_job_id = p_job_id, started_at = now(),
         next_check_at = now() + make_interval(secs => p_check_seconds)
   WHERE run_id = p_run_id AND status_id = 1
$$;

-- Итог: 03 (готов, с именем и типом файла) или 04 (ошибка: ключ текста
-- портала и/или текст WMS). Завершённый запуск не меняется.
CREATE FUNCTION report_run_finish(p_run_id uuid, p_status_code text, p_error_key text,
                                  p_error_detail text, p_file_name text, p_content_type text)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  UPDATE report_run
     SET status_id = (SELECT id FROM enum_report_run_status WHERE code = p_status_code),
         error_key = p_error_key, error_detail = left(p_error_detail, 2000),
         file_name = p_file_name, content_type = p_content_type,
         finished_at = now(), next_check_at = NULL
   WHERE run_id = p_run_id AND status_id IN (1, 2)
$$;

-- Ещё формируется: следующая проверка — через p_check_seconds.
CREATE FUNCTION report_run_checked(p_run_id uuid, p_check_seconds integer)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  UPDATE report_run
     SET checks = checks + 1, next_check_at = now() + make_interval(secs => p_check_seconds)
   WHERE run_id = p_run_id AND status_id = 2
$$;

-- Кому пора проверить готовность — по всем клиентам. Задания, которые
-- формируются дольше p_timeout_minutes, сначала закрываются ошибкой
-- p_timeout_key (ключ текста передаёт портал — он и показывает текст).
CREATE FUNCTION report_run_due(p_timeout_minutes integer, p_timeout_key text)
RETURNS TABLE (run_id uuid, wms_job_id text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  UPDATE report_run r
     SET status_id = 4, error_key = p_timeout_key,
         finished_at = now(), next_check_at = NULL
   WHERE r.status_id = 2 AND r.started_at < now() - make_interval(mins => p_timeout_minutes);
  RETURN QUERY
    SELECT r.run_id, r.wms_job_id::text FROM report_run r
     WHERE r.status_id = 2 AND r.next_check_at <= now()
     ORDER BY r.next_check_at
     LIMIT 100;
END
$$;

REVOKE EXECUTE ON FUNCTION report_run_started(uuid, text, integer),
                           report_run_finish(uuid, text, text, text, text, text),
                           report_run_checked(uuid, integer), report_run_due(integer, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION report_run_started(uuid, text, integer),
                          report_run_finish(uuid, text, text, text, text, text),
                          report_run_checked(uuid, integer), report_run_due(integer, text) TO portal_app;

-- ---------- Тексты ---------------------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('enums.name.enum_report_run_status', 'admin',  'label',   'reports', 'перечисление: статус запуска отчёта (V138)'),
  ('error.report_run.not_found',        'server', 'message', 'reports', 'отчёт клиенту не выдан или выключен (V138)'),
  ('error.report_run.value_required',   'server', 'message', 'reports', 'не заполнен обязательный параметр (V138)'),
  ('error.report_run.bad_date',         'server', 'message', 'reports', 'параметр — не дата (V138)'),
  ('error.report_run.bad_number',       'server', 'message', 'reports', 'параметр — не число (V138)'),
  ('error.report_run.unknown_value',    'server', 'message', 'reports', 'значения нет в справочнике клиента (V138)'),
  ('error.report_run.document_required','server', 'message', 'reports', 'отчёт по документу без документа (V138)'),
  ('error.report_run.wms_unavailable',  'server', 'message', 'reports', 'WMS не приняла запрос отчёта (V138)'),
  ('error.report_run.wms_failed',       'server', 'message', 'reports', 'WMS не смогла сформировать отчёт (V138)'),
  ('error.report_run.timeout',          'server', 'message', 'reports', 'WMS не успела сформировать отчёт (V138)'),
  ('error.report_run.not_ready',        'server', 'message', 'reports', 'файл ещё не готов (V138)'),
  ('error.report_run.file_gone',        'server', 'message', 'reports', 'WMS не отдала файл (V138)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('enums.name.enum_report_run_status', 'ru', 'Статус запуска отчёта'),
    ('enums.name.enum_report_run_status', 'en', 'Report run status'),
    ('error.report_run.not_found', 'ru', 'Этот отчёт вам недоступен'),
    ('error.report_run.not_found', 'en', 'This report is not available to you'),
    ('error.report_run.value_required', 'ru', 'Заполните параметр «{label}»'),
    ('error.report_run.value_required', 'en', 'Fill in “{label}”'),
    ('error.report_run.bad_date', 'ru', '«{label}»: нужна дата'),
    ('error.report_run.bad_date', 'en', '“{label}”: a date is needed'),
    ('error.report_run.bad_number', 'ru', '«{label}»: нужно число'),
    ('error.report_run.bad_number', 'en', '“{label}”: a number is needed'),
    ('error.report_run.unknown_value', 'ru', '«{label}»: значения «{value}» нет в ваших справочниках'),
    ('error.report_run.unknown_value', 'en', '“{label}”: “{value}” is not in your directories'),
    ('error.report_run.document_required', 'ru', 'Этот отчёт формируется из карточки документа'),
    ('error.report_run.document_required', 'en', 'This report is produced from a document card'),
    ('error.report_run.wms_unavailable', 'ru', 'Склад не принял запрос отчёта. Попробуйте ещё раз позже'),
    ('error.report_run.wms_unavailable', 'en', 'The warehouse did not accept the report request. Please try again later'),
    ('error.report_run.wms_failed', 'ru', 'Склад не смог сформировать отчёт'),
    ('error.report_run.wms_failed', 'en', 'The warehouse could not produce the report'),
    ('error.report_run.timeout', 'ru', 'Склад не успел сформировать отчёт. Запустите его ещё раз'),
    ('error.report_run.timeout', 'en', 'The warehouse did not produce the report in time. Run it again'),
    ('error.report_run.not_ready', 'ru', 'Отчёт ещё не готов'),
    ('error.report_run.not_ready', 'en', 'The report is not ready yet'),
    ('error.report_run.file_gone', 'ru', 'Склад не отдал файл отчёта. Сформируйте отчёт заново'),
    ('error.report_run.file_gone', 'en', 'The warehouse did not return the report file. Run the report again')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
