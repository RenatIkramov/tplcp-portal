-- =====================================================================
-- V145. Файлы клиента к документам (Ф3, часть без WMS)
-- =====================================================================
-- Решения заказчика 16.11.2026 и 18.11.2026 («Файловое хранилище — решение»):
--   * клиент прикладывает файлы к поступлению, заказу, перемещению;
--     файл уходит в WMS;
--   * у черновика файл ждёт на портале и уходит вслед за документом
--     (статус «Отправлен в WMS»), у отправленного — сразу;
--   * не дошёл — повтор очередью, после 15-й неудачи «Ошибка отправки»
--     и «Отправить ещё раз»;
--   * дошедший файл портал хранит, пока жив документ.
--
-- Хранение — stored_file из V144, новый вид 'client'. Сам вызов WMS —
-- заглушка WmsGateway.uploadFile, пока не придут документы ArtWMS.
--
-- Состояние отправки (wms_state):
--   waiting — документ ещё не в WMS (черновик, «Отправляется», ошибка отправки)
--   pending — пора отправлять (next_attempt_at)
--   sent    — WMS приняла (wms_file_id — её номер файла)
--   failed  — 15 неудач подряд
--
-- Заодно удалены мёртвые document_photo и v_document_photos: таблица из
-- схемы V1, ни одной строки, никто не пишет и не читает, связь — только
-- с поступлениями.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Мёртвое
-- ---------------------------------------------------------------------
DROP VIEW v_document_photos;
DROP TABLE document_photo;
DELETE FROM cache_policy WHERE entity = 'document_photo';
DELETE FROM ui_text     WHERE text_key = 'error.usage.photos';
DELETE FROM ui_text_key WHERE text_key = 'error.usage.photos';

-- ---------------------------------------------------------------------
-- 2. stored_file: файл клиента
-- ---------------------------------------------------------------------
ALTER TABLE stored_file DROP CONSTRAINT stored_file_kind_check;
ALTER TABLE stored_file ADD CONSTRAINT stored_file_kind_check CHECK (kind IN ('report', 'client'));

ALTER TABLE stored_file
  ADD COLUMN doc_type        varchar(20) CHECK (doc_type IN ('receipts', 'orders', 'transfers')),
  ADD COLUMN doc_id          uuid,
  ADD COLUMN uploaded_by     uuid REFERENCES app_user (user_id),
  ADD COLUMN wms_state       varchar(10) CHECK (wms_state IN ('waiting', 'pending', 'sent', 'failed')),
  ADD COLUMN wms_file_id     varchar(64),
  ADD COLUMN attempts        integer NOT NULL DEFAULT 0,
  ADD COLUMN next_attempt_at timestamptz,
  ADD COLUMN last_error      text,
  ADD COLUMN sent_at         timestamptz,
  ADD CONSTRAINT stored_file_client_check
      CHECK (kind <> 'client' OR (doc_type IS NOT NULL AND doc_id IS NOT NULL AND wms_state IS NOT NULL));
CREATE INDEX ix_stored_file_doc ON stored_file (doc_id) WHERE doc_id IS NOT NULL;
CREATE INDEX ix_stored_file_due ON stored_file (next_attempt_at) WHERE wms_state = 'pending';
COMMENT ON COLUMN stored_file.wms_state IS
  'Файл клиента: waiting — документ ещё не в WMS, pending — к отправке, sent — принят WMS, failed — 15 неудач (V145)';

-- Настройки приёма (правятся в «Интеграции»)
INSERT INTO portal_setting (setting_key, value, note) VALUES
  ('client_file_max_mb', '20', 'предел размера файла, который клиент прикладывает к документу, МБ (1–100)'),
  ('client_file_types',  'pdf,jpg,jpeg,png,doc,docx,xls,xlsx',
   'расширения файлов, которые клиент может приложить к документу, через запятую')
ON CONFLICT (setting_key) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Функции
-- ---------------------------------------------------------------------
-- Код статуса документа своего клиента; NULL — документа нет или он чужой.
CREATE FUNCTION client_file_doc_status(p_type text, p_doc uuid)
RETURNS text
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_code text;
BEGIN
  IF p_type NOT IN ('receipts', 'orders', 'transfers') THEN
    RETURN NULL;
  END IF;
  EXECUTE format('SELECT COALESCE(es.code, ''01'') FROM %I h LEFT JOIN enum_status es ON es.id = h.status_id '
                 || 'WHERE h.doc_id = $1 AND h.depositor_id = $2', p_type)
     INTO v_code USING p_doc, current_depositor_id();
  RETURN v_code;
END
$$;

-- Документ уже в WMS — файлу пора туда же.
CREATE FUNCTION client_file_doc_in_wms(p_status text)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
SET search_path = dbo, pg_temp
AS $$
  SELECT p_status IS NOT NULL AND p_status NOT IN ('01', '03', '04')
$$;

-- Приложить файл: содержимое уже лежит в папке под p_file_id.
-- Отказ — исключение с ключом текста: портал уберёт содержимое.
CREATE FUNCTION client_file_add(p_type text, p_doc uuid, p_file_id uuid, p_name text, p_content_type text,
                                p_size bigint, p_sha256 text, p_user uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_status text := client_file_doc_status(p_type, p_doc);
  v_now    boolean;
BEGIN
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'error.document.not_found';
  END IF;
  IF v_status = '90' THEN
    RAISE EXCEPTION 'error.client_file.document_cancelled';
  END IF;
  v_now := client_file_doc_in_wms(v_status);
  INSERT INTO stored_file (file_id, depositor_id, kind, doc_type, doc_id, uploaded_by, file_name, content_type,
                           size_bytes, sha256, wms_state, next_attempt_at)
  VALUES (p_file_id, current_depositor_id(), 'client', p_type, p_doc, p_user, left(p_name, 255),
          left(p_content_type, 120), p_size, p_sha256,
          CASE WHEN v_now THEN 'pending' ELSE 'waiting' END, CASE WHEN v_now THEN now() END);
END
$$;

-- Убрать файл: только у черновика и только ещё не отправленный.
-- Возвращает, удалён ли — портал уберёт содержимое.
CREATE FUNCTION client_file_remove(p_file_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_type text;
  v_doc  uuid;
BEGIN
  SELECT doc_type, doc_id INTO v_type, v_doc FROM stored_file
   WHERE file_id = p_file_id AND kind = 'client' AND depositor_id = current_depositor_id();
  IF v_doc IS NULL THEN
    RAISE EXCEPTION 'error.client_file.not_found';
  END IF;
  IF client_file_doc_status(v_type, v_doc) IS DISTINCT FROM '01' THEN
    RAISE EXCEPTION 'error.client_file.not_draft';
  END IF;
  DELETE FROM stored_file WHERE file_id = p_file_id AND wms_state = 'waiting';
  RETURN FOUND;
END
$$;

-- «Отправить ещё раз» у файла с ошибкой отправки.
CREATE FUNCTION client_file_resend(p_file_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  UPDATE stored_file SET wms_state = 'pending', attempts = 0, next_attempt_at = now(), last_error = NULL
   WHERE file_id = p_file_id AND kind = 'client' AND depositor_id = current_depositor_id()
     AND wms_state = 'failed';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'error.client_file.not_failed';
  END IF;
END
$$;

-- Очередь: файлы, которым пора в WMS. Без контекста клиента — её
-- разбирает расписание. Взятые файлы откладываются на минуту, чтобы
-- параллельный разбор не отправил их второй раз.
CREATE FUNCTION client_file_due(p_limit integer)
RETURNS TABLE (file_id uuid, depositor_id uuid, doc_type varchar, doc_id uuid, file_name varchar,
               content_type varchar)
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  UPDATE stored_file f SET next_attempt_at = now() + interval '1 minute'
   WHERE f.file_id IN (SELECT x.file_id FROM stored_file x
                        WHERE x.wms_state = 'pending' AND x.next_attempt_at <= now()
                        ORDER BY x.next_attempt_at
                        LIMIT GREATEST(1, p_limit)
                        FOR UPDATE SKIP LOCKED)
  RETURNING f.file_id, f.depositor_id, f.doc_type, f.doc_id, f.file_name, f.content_type
$$;

-- Итог попытки: p_wms_file_id — принят; иначе неудача с текстом.
CREATE FUNCTION client_file_attempt(p_file_id uuid, p_wms_file_id text, p_error text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_state text;
BEGIN
  IF p_wms_file_id IS NOT NULL THEN
    UPDATE stored_file SET wms_state = 'sent', wms_file_id = left(p_wms_file_id, 64), sent_at = now(),
                           attempts = attempts + 1, next_attempt_at = NULL, last_error = NULL
     WHERE file_id = p_file_id AND wms_state = 'pending'
    RETURNING wms_state INTO v_state;
  ELSE
    UPDATE stored_file
       SET attempts = attempts + 1, last_error = left(p_error, 2000),
           wms_state = CASE WHEN attempts + 1 >= 15 THEN 'failed' ELSE 'pending' END,
           next_attempt_at = CASE WHEN attempts + 1 >= 15 THEN NULL ELSE now() + interval '1 minute' END
     WHERE file_id = p_file_id AND wms_state = 'pending'
    RETURNING wms_state INTO v_state;
  END IF;
  RETURN v_state;
END
$$;

REVOKE EXECUTE ON FUNCTION client_file_doc_status(text, uuid), client_file_doc_in_wms(text),
  client_file_add(text, uuid, uuid, text, text, bigint, text, uuid), client_file_remove(uuid),
  client_file_resend(uuid), client_file_due(integer), client_file_attempt(uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION client_file_doc_status(text, uuid), client_file_doc_in_wms(text),
  client_file_add(text, uuid, uuid, text, text, bigint, text, uuid), client_file_remove(uuid),
  client_file_resend(uuid), client_file_due(integer), client_file_attempt(uuid, text, text) TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Файлы следуют за документом
-- ---------------------------------------------------------------------
-- Документ попал в WMS — ждавшие файлы встают в очередь. Документ
-- удалён (черновик) — его файлы тоже; содержимое уберёт ночная уборка.
CREATE FUNCTION client_files_follow_doc()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM stored_file WHERE kind = 'client' AND doc_id = OLD.doc_id;
    RETURN NULL;
  END IF;
  IF OLD.status_id IS DISTINCT FROM NEW.status_id
     AND client_file_doc_in_wms((SELECT code FROM enum_status WHERE id = NEW.status_id)) THEN
    UPDATE stored_file SET wms_state = 'pending', next_attempt_at = now()
     WHERE kind = 'client' AND doc_id = NEW.doc_id AND wms_state = 'waiting';
  END IF;
  RETURN NULL;
END
$$;
REVOKE EXECUTE ON FUNCTION client_files_follow_doc() FROM PUBLIC, portal_app;

CREATE TRIGGER client_files AFTER UPDATE OF status_id OR DELETE ON receipts
  FOR EACH ROW EXECUTE FUNCTION client_files_follow_doc();
CREATE TRIGGER client_files AFTER UPDATE OF status_id OR DELETE ON orders
  FOR EACH ROW EXECUTE FUNCTION client_files_follow_doc();
CREATE TRIGGER client_files AFTER UPDATE OF status_id OR DELETE ON transfers
  FOR EACH ROW EXECUTE FUNCTION client_files_follow_doc();

-- ---------------------------------------------------------------------
-- 5. Настройки приёма — в экран «Интеграция» (V143)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION admin_wms_settings()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT jsonb_build_object(
    'countTransfers', COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_count_transfers'), false),
    'syncEnabled',    COALESCE((SELECT value = 'true' FROM portal_setting WHERE setting_key = 'stock_sync_enabled'), false),
    'syncTime',       (SELECT value FROM portal_setting WHERE setting_key = 'stock_sync_time'),
    'fileMaxMb',      (SELECT value::int FROM portal_setting WHERE setting_key = 'client_file_max_mb'),
    'fileTypes',      (SELECT value FROM portal_setting WHERE setting_key = 'client_file_types'),
    'warehouses', COALESCE((SELECT jsonb_agg(jsonb_build_object('warehouseId', w.warehouse_id, 'name', w.name,
                                      'isActive', w.is_active, 'baseUrl', ww.base_url) ORDER BY w.warehouse_id)
                              FROM warehouse w LEFT JOIN wms_warehouse ww ON ww.warehouse_id = w.warehouse_id), '[]'),
    'depositors', COALESCE((SELECT jsonb_agg(jsonb_build_object('depositorId', d.depositor_id, 'code', d.code,
                                      'orgName', d.org_name, 'isActive', d.is_active, 'wmsCode', wd.wms_code) ORDER BY d.code)
                              FROM depositor d LEFT JOIN wms_depositor wd ON wd.depositor_id = d.depositor_id), '[]'),
    'qualities',  COALESCE((SELECT jsonb_agg(jsonb_build_object('wmsCode', wq.wms_code, 'qualityCode', q.code)
                                             ORDER BY wq.wms_code)
                              FROM wms_quality wq JOIN enum_quality q ON q.id = wq.quality_id), '[]'),
    'qualityOptions', COALESCE((SELECT jsonb_agg(jsonb_build_object('code', q.code,
                                      'name', enum_name('enum_quality', q.id::text, q.name::text))
                                      ORDER BY q.sort_order, q.id)
                                  FROM enum_quality q WHERE q.is_active), '[]'))
$$;

-- Сохранение — прежнее (V143); приём файлов пишется отдельной функцией,
-- служба зовёт обе в одной транзакции.
CREATE FUNCTION admin_client_file_settings_save(p_max_mb integer, p_types text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  IF p_max_mb IS NULL OR p_max_mb < 1 OR p_max_mb > 100 THEN
    RAISE EXCEPTION 'client file limit out of range: %', p_max_mb;
  END IF;
  IF COALESCE(p_types, '') !~ '^[a-z0-9]{1,10}(,[a-z0-9]{1,10})*$' THEN
    RAISE EXCEPTION 'client file types malformed: %', p_types;
  END IF;
  UPDATE portal_setting SET value = p_max_mb::text WHERE setting_key = 'client_file_max_mb' AND value <> p_max_mb::text;
  UPDATE portal_setting SET value = p_types WHERE setting_key = 'client_file_types' AND value <> p_types;
END
$$;
REVOKE EXECUTE ON FUNCTION admin_client_file_settings_save(integer, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_client_file_settings_save(integer, text) TO portal_app;

-- ---------------------------------------------------------------------
-- 6. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.client_file.too_big',            'server', 'message', 'files', 'файл больше предела (V145)'),
  ('error.client_file.type_not_allowed',   'server', 'message', 'files', 'расширение не из списка (V145)'),
  ('error.client_file.content_mismatch',   'server', 'message', 'files', 'содержимое не того типа, что расширение (V145)'),
  ('error.client_file.empty',              'server', 'message', 'files', 'пустой файл (V145)'),
  ('error.client_file.not_found',          'server', 'message', 'files', 'файла нет или он чужой (V145)'),
  ('error.client_file.not_draft',          'server', 'message', 'files', 'удалить можно только у черновика (V145)'),
  ('error.client_file.not_failed',         'server', 'message', 'files', 'повтор — только у файла с ошибкой (V145)'),
  ('error.client_file.document_cancelled', 'server', 'message', 'files', 'к отменённому документу не прикладывают (V145)'),
  ('error.client_file.gone',               'server', 'message', 'files', 'содержимого в папке нет (V145)'),
  ('error.wms_settings.file_limit',        'server', 'message', 'integration', 'предел размера файла вне 1–100 (V145)'),
  ('error.wms_settings.file_types',        'server', 'message', 'integration', 'список расширений не разобрать (V145)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.text_key, v.scope, v.element, v.owner, v.note
  FROM (VALUES
    ('files.button.add',             'common', 'button',  'files', 'приложить файл к документу'),
    ('files.button.resend',          'common', 'button',  'files', 'отправить файл в WMS ещё раз'),
    ('files.hint.limits',            'common', 'hint',    'files', 'какие файлы можно приложить'),
    ('files.column.name',            'common', 'column',  'files', 'имя файла'),
    ('files.column.size',            'common', 'column',  'files', 'размер'),
    ('files.column.added',           'common', 'column',  'files', 'кто и когда приложил'),
    ('files.column.wms',             'common', 'column',  'files', 'состояние отправки в WMS'),
    ('files.state.waiting',          'common', 'text',    'files', 'ждёт отправки документа'),
    ('files.state.pending',          'common', 'text',    'files', 'отправляется'),
    ('files.state.sent',             'common', 'text',    'files', 'в WMS'),
    ('files.state.failed',           'common', 'text',    'files', 'ошибка отправки'),
    ('files.message.added',          'common', 'message', 'files', 'файлы приложены'),
    ('files.message.uploading',      'common', 'message', 'files', 'идёт загрузка'),
    ('files.confirm.remove',         'common', 'confirm', 'files', 'удалить файл?'),
    ('integration.title.client_files','admin', 'title',   'integration', 'блок: файлы клиента'),
    ('integration.label.file_max_mb', 'admin', 'label',   'integration', 'предел размера файла'),
    ('integration.label.file_types',  'admin', 'label',   'integration', 'допустимые расширения'),
    ('integration.hint.client_files', 'admin', 'hint',    'integration', 'пояснение к приёму файлов')
  ) AS v(text_key, scope, element, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.client_file.too_big', 'ru', 'Файл «{name}» больше {max} МБ.'),
    ('error.client_file.too_big', 'en', 'File "{name}" is larger than {max} MB.'),
    ('error.client_file.type_not_allowed', 'ru', 'Файл «{name}» нельзя приложить: допустимы {types}.'),
    ('error.client_file.type_not_allowed', 'en', 'File "{name}" cannot be attached: allowed types are {types}.'),
    ('error.client_file.content_mismatch', 'ru', 'Файл «{name}» не похож на {ext}: содержимое другого типа.'),
    ('error.client_file.content_mismatch', 'en', 'File "{name}" does not look like {ext}: its content is of another type.'),
    ('error.client_file.empty', 'ru', 'Файл «{name}» пустой.'),
    ('error.client_file.empty', 'en', 'File "{name}" is empty.'),
    ('error.client_file.not_found', 'ru', 'Файл не найден.'),
    ('error.client_file.not_found', 'en', 'File not found.'),
    ('error.client_file.not_draft', 'ru', 'Удалить файл можно, пока документ — черновик. После отправки файл удаляет склад.'),
    ('error.client_file.not_draft', 'en', 'A file can be removed while the document is a draft. After sending, the warehouse removes it.'),
    ('error.client_file.not_failed', 'ru', 'Отправить ещё раз можно только файл с ошибкой отправки.'),
    ('error.client_file.not_failed', 'en', 'Only a file that failed to send can be sent again.'),
    ('error.client_file.document_cancelled', 'ru', 'Документ отменён — файлы к нему не прикладываются.'),
    ('error.client_file.document_cancelled', 'en', 'The document is cancelled — files cannot be attached to it.'),
    ('error.client_file.gone', 'ru', 'Файл больше не хранится на портале.'),
    ('error.client_file.gone', 'en', 'The file is no longer stored on the portal.'),
    ('error.wms_settings.file_limit', 'ru', 'Предел размера файла — от 1 до 100 МБ.'),
    ('error.wms_settings.file_limit', 'en', 'The file size limit must be from 1 to 100 MB.'),
    ('error.wms_settings.file_types', 'ru', 'Расширения укажите через запятую, латиницей, без точек: pdf, jpg, xlsx.'),
    ('error.wms_settings.file_types', 'en', 'List extensions separated by commas, in Latin letters, without dots: pdf, jpg, xlsx.'),
    ('files.button.add', 'ru', '+ Добавить файл'),
    ('files.button.add', 'en', '+ Add file'),
    ('files.button.resend', 'ru', 'Отправить ещё раз'),
    ('files.button.resend', 'en', 'Send again'),
    ('files.hint.limits', 'ru', 'До {max} МБ: {types}. У черновика файл уйдёт на склад вместе с документом.'),
    ('files.hint.limits', 'en', 'Up to {max} MB: {types}. For a draft, the file goes to the warehouse together with the document.'),
    ('files.column.name', 'ru', 'Файл'),
    ('files.column.name', 'en', 'File'),
    ('files.column.size', 'ru', 'Размер'),
    ('files.column.size', 'en', 'Size'),
    ('files.column.added', 'ru', 'Приложил'),
    ('files.column.added', 'en', 'Added by'),
    ('files.column.wms', 'ru', 'На складе'),
    ('files.column.wms', 'en', 'At the warehouse'),
    ('files.state.waiting', 'ru', 'Ждёт отправки документа'),
    ('files.state.waiting', 'en', 'Waiting for the document'),
    ('files.state.pending', 'ru', 'Отправляется'),
    ('files.state.pending', 'en', 'Sending'),
    ('files.state.sent', 'ru', 'Передан'),
    ('files.state.sent', 'en', 'Delivered'),
    ('files.state.failed', 'ru', 'Ошибка отправки'),
    ('files.state.failed', 'en', 'Sending failed'),
    ('files.message.added', 'ru', 'Файлы приложены: {count}'),
    ('files.message.added', 'en', 'Files attached: {count}'),
    ('files.message.uploading', 'ru', 'Загрузка…'),
    ('files.message.uploading', 'en', 'Uploading…'),
    ('files.confirm.remove', 'ru', 'Удалить файл «{name}»?'),
    ('files.confirm.remove', 'en', 'Remove file "{name}"?'),
    ('integration.title.client_files', 'ru', 'Файлы клиента к документам'),
    ('integration.title.client_files', 'en', 'Client files attached to documents'),
    ('integration.label.file_max_mb', 'ru', 'Предел размера файла, МБ'),
    ('integration.label.file_max_mb', 'en', 'File size limit, MB'),
    ('integration.label.file_types', 'ru', 'Допустимые расширения'),
    ('integration.label.file_types', 'en', 'Allowed extensions'),
    ('integration.hint.client_files', 'ru', 'Через запятую, без точек. Для pdf, jpg, png, doc, docx, xls, xlsx портал сверяет содержимое с расширением.'),
    ('integration.hint.client_files', 'en', 'Comma-separated, without dots. For pdf, jpg, png, doc, docx, xls, xlsx the portal checks that the content matches the extension.')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Вкладка «Прикреплённые файлы» без файлов
UPDATE ui_text SET value = v.value
  FROM (VALUES
    ('ru', 'Файлов пока нет.'),
    ('en', 'No files yet.')
  ) AS v(code, value), dictionary_locale l
 WHERE l.code = v.code AND ui_text.locale_id = l.locale_id AND ui_text.text_key = 'common.text.no_document_photos';
