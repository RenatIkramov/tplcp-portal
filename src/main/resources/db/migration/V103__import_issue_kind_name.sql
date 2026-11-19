SET search_path = dbo;

-- V103. Журнал «Ошибки загрузки Excel»: вид загрузки — именем, а не кодом.
--
-- Экран (import-log.js) показывает в колонке «Вид загрузки» имя вида
-- на языке администратора, а если имени нет — код. Список загрузок
-- имя отдаёт с V90, а список замечаний так и не начал: у каждой строки
-- ошибки вместо «Заказ» стояло «03». Нашла проверка типов фронтенда —
-- поле, которое экран читает, в ответе сервера отсутствовало.
--
-- Функция пересоздаётся целиком: у RETURNS TABLE добавляется колонка,
-- а CREATE OR REPLACE этого не позволяет. Тело — как в V98, плюс имя
-- вида последней колонкой.

DROP FUNCTION IF EXISTS admin_excel_import_issues(uuid, integer);
CREATE FUNCTION admin_excel_import_issues(p_depositor_id uuid DEFAULT NULL::uuid, p_limit integer DEFAULT 500)
RETURNS TABLE(import_id text, started_at timestamp with time zone, depositor_code character varying,
              depositor_name character varying, login character varying, full_name character varying,
              file_name character varying, row_number integer, row_class character varying,
              kind character varying, column_id character varying, message text, file_value text,
              portal_value text, import_kind character varying,
              message_key character varying, param_names text[], param_values text[],
              import_kind_name character varying)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = dbo
AS $$
  SELECT i.import_id::text,
         e.started_at,
         d.code,
         d.org_name,
         u.login,
         u.full_name,
         e.file_name,
         i.row_number,
         r.row_class,
         i.kind,
         i.column_id,
         i.message,
         i.file_value,
         i.portal_value,
         e.kind,
         i.message_key,
         -- подстановки двумя массивами: портал собирает из них фразу,
         -- не разбирая JSON
         ARRAY(SELECT p.key FROM jsonb_each_text(i.message_params) p ORDER BY p.key),
         ARRAY(SELECT p.value FROM jsonb_each_text(i.message_params) p ORDER BY p.key),
         enum_name('enum_import_kind', ik.id::text, ik.name)::varchar
  FROM excel_import_issue i
  JOIN excel_import e ON e.import_id = i.import_id
  LEFT JOIN excel_import_row r
         ON r.import_id = i.import_id AND r.row_number = i.row_number
  LEFT JOIN enum_import_kind ik ON ik.code = e.kind
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC, i.row_number, i.id
  LIMIT p_limit;
$$;
REVOKE ALL ON FUNCTION admin_excel_import_issues(uuid, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_excel_import_issues(uuid, integer) TO portal_app;
