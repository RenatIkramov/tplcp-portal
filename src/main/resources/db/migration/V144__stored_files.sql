-- =====================================================================
-- V144. Файловое хранилище: слой хранения (Ф1) и отчёты через него (Ф2)
-- =====================================================================
-- Решение заказчика 16.11.2026 («Файловое хранилище — решение»):
-- содержимое файлов — в папке на сервере портала, описание — в базе.
--
--   stored_file — описание файла: чей, какого вида, к чему относится,
--   имя, тип, размер, контрольная сумма, срок. Содержимое лежит в папке
--   под именем file_id — имя, которое прислали человек или WMS, в путь
--   на диске не попадает.
--
-- Отчёты: задание готово — портал ОДИН раз забирает файл у WMS, кладёт
-- в хранилище и дальше отдаёт свой. Раньше «Скачать» каждый раз шло
-- в WMS по номеру задания. Запуск удаляется по сроку хранения (V139) —
-- описание файла уходит вместе с ним, содержимое убирает ночная уборка.
--
-- Виды файлов пока один — отчёт; копии файлов WMS и файлы клиента
-- добавят следующие поставки (Ф3, Ф4).
SET search_path = dbo;

CREATE TABLE stored_file (
  file_id       uuid         PRIMARY KEY,
  depositor_id  uuid         NOT NULL REFERENCES depositor (depositor_id),
  kind          varchar(20)  NOT NULL CHECK (kind IN ('report')),
  report_run_id uuid         UNIQUE REFERENCES report_run (run_id) ON DELETE CASCADE,
  file_name     varchar(255) NOT NULL,
  content_type  varchar(120),
  size_bytes    bigint       NOT NULL CHECK (size_bytes >= 0),
  sha256        char(64)     NOT NULL,
  created_at    timestamptz  NOT NULL DEFAULT now(),
  expires_at    timestamptz,
  CHECK (kind <> 'report' OR report_run_id IS NOT NULL)
);
COMMENT ON TABLE  stored_file IS 'Файлы портала: описание; содержимое — в папке на сервере под именем file_id (V144)';
COMMENT ON COLUMN stored_file.expires_at IS 'Когда файл удалить; NULL — живёт, пока жив его владелец (запуск отчёта)';
CREATE INDEX ix_stored_file_expires ON stored_file (expires_at) WHERE expires_at IS NOT NULL;

ALTER TABLE stored_file ENABLE ROW LEVEL SECURITY;
ALTER TABLE stored_file FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON stored_file
  USING (depositor_id = current_depositor_id())
  WITH CHECK (depositor_id = current_depositor_id());
GRANT SELECT ON stored_file TO portal_app;

-- Файл отчёта получен и лежит в папке: записать описание и закрыть запуск
-- как «Готов» — одним действием. false — запуск уже не ждёт (закрыт по
-- времени или удалён): портал уберёт только что положенный файл.
CREATE FUNCTION report_run_file_ready(p_run_id uuid, p_file_id uuid, p_file_name text, p_content_type text,
                                      p_size bigint, p_sha256 text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep uuid;
BEGIN
  UPDATE report_run
     SET status_id = (SELECT id FROM enum_report_run_status WHERE code = '03'),
         error_key = NULL, error_detail = NULL,
         file_name = left(p_file_name, 255), content_type = left(p_content_type, 120),
         finished_at = now(), next_check_at = NULL
   WHERE run_id = p_run_id AND status_id IN (1, 2)
  RETURNING depositor_id INTO v_dep;
  IF v_dep IS NULL THEN
    RETURN false;
  END IF;
  INSERT INTO stored_file (file_id, depositor_id, kind, report_run_id, file_name, content_type, size_bytes, sha256)
  VALUES (p_file_id, v_dep, 'report', p_run_id, left(p_file_name, 255), left(p_content_type, 120), p_size, p_sha256);
  RETURN true;
END
$$;

-- Ночная уборка, шаг 1: описания с истёкшим сроком удаляются, их file_id
-- возвращаются — портал удалит содержимое.
CREATE FUNCTION stored_file_expire()
RETURNS SETOF uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  DELETE FROM stored_file WHERE expires_at IS NOT NULL AND expires_at < now() RETURNING file_id
$$;

-- Ночная уборка, шаг 2: сверка папки с базой. На входе — file_id всех
-- файлов папки. 'orphan' — файл есть, описания нет (портал его удалит);
-- 'missing' — описание есть, файла нет (в журнал: скачать его нельзя).
CREATE FUNCTION stored_file_check(p_on_disk uuid[])
RETURNS TABLE (file_id uuid, problem text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT d.id, 'orphan' FROM unnest(COALESCE(p_on_disk, '{}')) AS d(id)
   WHERE NOT EXISTS (SELECT 1 FROM stored_file f WHERE f.file_id = d.id)
  UNION ALL
  SELECT f.file_id, 'missing' FROM stored_file f
   WHERE NOT (f.file_id = ANY (COALESCE(p_on_disk, '{}')))
$$;

REVOKE EXECUTE ON FUNCTION report_run_file_ready(uuid, uuid, text, text, bigint, text),
  stored_file_expire(), stored_file_check(uuid[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION report_run_file_ready(uuid, uuid, text, text, bigint, text),
  stored_file_expire(), stored_file_check(uuid[]) TO portal_app;

-- Текст отказа: файл теперь хранит портал, а не отдаёт склад.
UPDATE ui_text_key SET note = 'файла отчёта в хранилище портала нет (V138, V144)'
 WHERE text_key = 'error.report_run.file_gone';
UPDATE ui_text SET value = v.value
  FROM (VALUES
    ('ru', 'Файл отчёта больше не хранится. Сформируйте отчёт заново'),
    ('en', 'The report file is no longer stored. Run the report again')
  ) AS v(code, value), dictionary_locale l
 WHERE l.code = v.code AND ui_text.locale_id = l.locale_id AND ui_text.text_key = 'error.report_run.file_gone';
