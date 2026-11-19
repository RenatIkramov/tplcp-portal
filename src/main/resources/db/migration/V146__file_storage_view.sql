-- =====================================================================
-- V146. Хранилище файлов — сводка для администратора
-- =====================================================================
-- Решение заказчика 19.11.2026: папку хранилища в администрировании
-- только показывать, менять — в application.yml (portal.files.dir)
-- с перезапуском. В «Интеграции» — блок «Хранилище файлов»: папка,
-- сколько файлов и места по видам, свободное место, итог ночной уборки.
SET search_path = dbo;

-- Файлы всех клиентов по видам: описания под RLS, администратору без
-- выбранного клиента их не видно — поэтому функцией владельца.
CREATE FUNCTION admin_file_storage_stats()
RETURNS TABLE (kind varchar, file_count bigint, total_bytes bigint)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT f.kind, count(*), COALESCE(sum(f.size_bytes), 0)::bigint
    FROM stored_file f
   GROUP BY f.kind
   ORDER BY f.kind
$$;
REVOKE EXECUTE ON FUNCTION admin_file_storage_stats() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_file_storage_stats() TO portal_app;

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.text_key, 'admin', v.element, 'integration', v.note
  FROM (VALUES
    ('integration.title.file_storage',   'title',  'блок: хранилище файлов'),
    ('integration.hint.file_storage',    'hint',   'пояснение: где папка и как её сменить'),
    ('integration.label.folder',         'label',  'папка хранилища'),
    ('integration.label.kind_report',    'label',  'вид файла: отчёты'),
    ('integration.label.kind_client',    'label',  'вид файла: файлы клиентов'),
    ('integration.label.on_disk',        'label',  'в папке'),
    ('integration.label.free_space',     'label',  'свободно на диске'),
    ('integration.label.last_sweep',     'label',  'последняя ночная уборка'),
    ('integration.text.files_size',      'text',   'N файлов, объём'),
    ('integration.text.sweep_result',    'text',   'итог уборки'),
    ('integration.text.sweep_never',     'text',   'уборки после запуска портала ещё не было'),
    ('integration.text.folder_missing',  'text',   'папки ещё нет — появится с первым файлом')
  ) AS v(text_key, element, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('integration.title.file_storage', 'ru', 'Хранилище файлов'),
    ('integration.title.file_storage', 'en', 'File storage'),
    ('integration.hint.file_storage', 'ru', 'Готовые отчёты и файлы клиентов лежат в этой папке на сервере портала. Сменить папку — настройка portal.files.dir (переменная PORTAL_FILES_DIR) и перезапуск портала; файлы перенести вручную. Папку включите в резервное копирование.'),
    ('integration.hint.file_storage', 'en', 'Finished reports and client files are kept in this folder on the portal server. To change it, set portal.files.dir (variable PORTAL_FILES_DIR) and restart the portal; move the files by hand. Include the folder in backups.'),
    ('integration.label.folder', 'ru', 'Папка'),
    ('integration.label.folder', 'en', 'Folder'),
    ('integration.label.kind_report', 'ru', 'Отчёты'),
    ('integration.label.kind_report', 'en', 'Reports'),
    ('integration.label.kind_client', 'ru', 'Файлы клиентов'),
    ('integration.label.kind_client', 'en', 'Client files'),
    ('integration.label.on_disk', 'ru', 'Всего в папке'),
    ('integration.label.on_disk', 'en', 'Total in the folder'),
    ('integration.label.free_space', 'ru', 'Свободно на диске'),
    ('integration.label.free_space', 'en', 'Free disk space'),
    ('integration.label.last_sweep', 'ru', 'Ночная уборка'),
    ('integration.label.last_sweep', 'en', 'Nightly cleanup'),
    ('integration.text.files_size', 'ru', '{count} шт., {size}'),
    ('integration.text.files_size', 'en', '{count} files, {size}'),
    ('integration.text.sweep_result', 'ru', '{at}: удалено по сроку {expired}, без описания {orphans}; описаний без файла {missing}'),
    ('integration.text.sweep_result', 'en', '{at}: removed as expired {expired}, without description {orphans}; descriptions without a file {missing}'),
    ('integration.text.sweep_never', 'ru', 'после запуска портала ещё не было'),
    ('integration.text.sweep_never', 'en', 'not run since the portal started'),
    ('integration.text.folder_missing', 'ru', 'папки ещё нет — появится с первым файлом'),
    ('integration.text.folder_missing', 'en', 'the folder does not exist yet — it appears with the first file')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
