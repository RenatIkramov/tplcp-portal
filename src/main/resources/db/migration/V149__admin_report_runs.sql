SET search_path = dbo;

-- =====================================================================
-- V149. «Запуски отчётов» для администратора
-- =====================================================================
-- Запуски отчётов (V138) лежат под RLS: клиент видит только свои, и
-- администратор — только запуски выбранного в шапке клиента. Новая
-- вкладка группы «Отчёты» показывает запуски всех клиентов: отборы по
-- клиенту, отчёту, статусу и датам, страницы на сервере, файл готового
-- отчёта и причина ошибки. Обе функции — с правами владельца; что их
-- зовёт только администратор, проверяет портал (/api/admin/**).

-- ---------- Страница запусков ----------------------------------------------------

CREATE FUNCTION admin_report_runs(p_depositor_id uuid, p_report_id uuid, p_status text,
                                  p_from date, p_to date, p_page integer, p_page_size integer)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_size  integer := LEAST(GREATEST(COALESCE(p_page_size, 50), 1), 200);
  v_page  integer := GREATEST(COALESCE(p_page, 1), 1);
  v_total integer;
  v_rows  jsonb;
BEGIN
  SELECT count(*) INTO v_total
    FROM report_run r JOIN enum_report_run_status s ON s.id = r.status_id
   WHERE (p_depositor_id IS NULL OR r.depositor_id = p_depositor_id)
     AND (p_report_id IS NULL OR r.report_id = p_report_id)
     AND (p_status IS NULL OR s.code = p_status)
     AND (p_from IS NULL OR r.requested_at >= p_from)
     AND (p_to IS NULL OR r.requested_at < p_to + 1);
  -- страница за концом списка (отбор сузили) — первая
  IF (v_page - 1) * v_size >= v_total THEN
    v_page := 1;
  END IF;

  SELECT COALESCE(jsonb_agg(x.row ORDER BY x.at DESC, x.id), '[]') INTO v_rows
    FROM (SELECT r.requested_at AS at, r.run_id AS id, jsonb_build_object(
                   'runId', r.run_id,
                   'requestedAt', r.requested_at,
                   'finishedAt', r.finished_at,
                   'depositorId', r.depositor_id,
                   'depositorCode', d.code,
                   'depositorName', d.org_name,
                   'requestedLogin', r.requested_login,
                   'requestedName', u.full_name,
                   'reportId', r.report_id,
                   'reportName', r.report_name,
                   'wmsCode', r.wms_code,
                   'params', r.params,
                   'documentNo', r.document_no,
                   'skuCode', r.sku_code,
                   'statusCode', s.code,
                   'statusName', enum_name('enum_report_run_status', s.id::text, s.name),
                   'errorKey', r.error_key,
                   'errorDetail', r.error_detail,
                   'fileName', r.file_name,
                   'hasFile', EXISTS (SELECT 1 FROM stored_file f WHERE f.report_run_id = r.run_id)) AS row
            FROM report_run r
            JOIN enum_report_run_status s ON s.id = r.status_id
            JOIN depositor d ON d.depositor_id = r.depositor_id
            LEFT JOIN app_user u ON u.user_id = r.requested_by
           WHERE (p_depositor_id IS NULL OR r.depositor_id = p_depositor_id)
             AND (p_report_id IS NULL OR r.report_id = p_report_id)
             AND (p_status IS NULL OR s.code = p_status)
             AND (p_from IS NULL OR r.requested_at >= p_from)
             AND (p_to IS NULL OR r.requested_at < p_to + 1)
           ORDER BY r.requested_at DESC, r.run_id
           LIMIT v_size OFFSET (v_page - 1) * v_size) x;

  RETURN jsonb_build_object('total', v_total, 'page', v_page, 'pageSize', v_size, 'rows', v_rows);
END
$$;
COMMENT ON FUNCTION admin_report_runs(uuid, uuid, text, date, date, integer, integer) IS
  'запуски отчётов всех клиентов для администратора: {total, page, pageSize, rows} (V149)';

-- ---------- Файл запуска ---------------------------------------------------------

-- То же, что читает клиент (ReportRunService.file), но без RLS: статус,
-- имя и тип файла и где он лежит в хранилище портала (V144).
CREATE FUNCTION admin_report_run_file(p_run_id uuid)
RETURNS TABLE (status text, file_name text, content_type text, file_id uuid)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT s.code::text, r.file_name::text, r.content_type::text, f.file_id
    FROM report_run r
    JOIN enum_report_run_status s ON s.id = r.status_id
    LEFT JOIN stored_file f ON f.report_run_id = r.run_id
   WHERE r.run_id = p_run_id
$$;
COMMENT ON FUNCTION admin_report_run_file(uuid) IS 'файл запуска отчёта любого клиента — для администратора (V149)';

REVOKE EXECUTE ON FUNCTION admin_report_runs(uuid, uuid, text, date, date, integer, integer),
                           admin_report_run_file(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_report_runs(uuid, uuid, text, date, date, integer, integer),
                          admin_report_run_file(uuid) TO portal_app;

-- ---------- Тексты ---------------------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.button.reports',          'admin', 'button', 'reports', 'группа «Отчёты» администрирования (V149)'),
  ('admin.button.report_runs',      'admin', 'button', 'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.hint.runs',             'admin', 'hint',   'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.column.client',         'admin', 'column',  'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.column.subject',        'admin', 'column',  'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.column.finished_at',    'admin', 'column',  'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.text.runs_not_found',   'admin', 'text',   'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.text.document',         'admin', 'text',   'reports', 'вкладка «Запуски отчётов» (V149)'),
  ('reports.text.sku',              'admin', 'text',   'reports', 'вкладка «Запуски отчётов» (V149)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.button.reports', 'ru', 'Отчёты'),
    ('admin.button.reports', 'en', 'Reports'),
    ('admin.button.report_runs', 'ru', 'Запуски отчётов'),
    ('admin.button.report_runs', 'en', 'Report runs'),
    ('reports.hint.runs', 'ru', 'Отчёты, которые запускали клиенты и сотрудники, — по всем клиентам, новые сверху. Готовые хранятся столько дней, сколько задано в «Настройке отчётов».'),
    ('reports.hint.runs', 'en', 'Reports run by clients and staff, for all clients, newest first. Finished reports are kept for the number of days set in “Report settings”.'),
    ('reports.column.client', 'ru', 'Клиент'),
    ('reports.column.client', 'en', 'Client'),
    ('reports.column.subject', 'ru', 'Документ или товар'),
    ('reports.column.subject', 'en', 'Document or item'),
    ('reports.column.finished_at', 'ru', 'Готов'),
    ('reports.column.finished_at', 'en', 'Finished'),
    ('reports.text.runs_not_found', 'ru', 'Запусков не найдено'),
    ('reports.text.runs_not_found', 'en', 'No runs found'),
    ('reports.text.document', 'ru', 'документ {v}'),
    ('reports.text.document', 'en', 'document {v}'),
    ('reports.text.sku', 'ru', 'товар {v}'),
    ('reports.text.sku', 'en', 'item {v}')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
