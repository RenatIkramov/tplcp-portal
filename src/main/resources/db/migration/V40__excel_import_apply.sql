-- =====================================================================
-- V40. Загрузка из Excel: применение и журнал (шаг 3)
-- =====================================================================
-- V37 завела разбор и проверку, V38 — показ значений. Здесь появляется
-- запись в справочник и журнал для администратора.
--
-- Сама запись идёт из приложения через SkuWriteService — тем же путём,
-- что ручная правка карточки товара. Здесь только то, что относится
-- к хранению: счётчики применённого, состояния загрузки, журнал
-- и чистка.
SET search_path = dbo;

-- =====================================================================
-- 1. Сколько применено
-- =====================================================================
-- to_create и to_update считаются по НЕприменённым годным строкам —
-- то есть это «сколько ещё можно записать». После нажатия кнопки они
-- обнуляются, и журналу было бы нечего показать: «загружено 0».
-- Поэтому рядом два счётчика итога, которые только растут.
ALTER TABLE excel_import
  ADD COLUMN IF NOT EXISTS created_rows integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_rows integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN excel_import.created_rows IS
  'сколько позиций создано этой загрузкой (итог, не убывает)';
COMMENT ON COLUMN excel_import.updated_rows IS
  'сколько позиций обновлено этой загрузкой (итог, не убывает)';

COMMENT ON COLUMN excel_import.status IS
  'checked — разобрано, ждёт решения; applied — что-то записано; '
  'cancelled — закрыто без записи';

-- Строка, которая не записалась при применении, получает замечание
-- вида apply: причина пришла не из файла, а из отказа портала
-- (например, товар с таким кодом успели создать руками).
COMMENT ON COLUMN excel_import_issue.kind IS
  'required | value | conflict | nothing | duplicate | archived | apply';

-- =====================================================================
-- 2. Журнал для администратора
-- =====================================================================
-- Подвкладка «Ошибки загрузки Excel» в прототипе — плоский список
-- замечаний: дата, пользователь, файл, строка, ошибка. Так и оставляем:
-- администратор ищет в нём причину, по которой у клиента что-то
-- не загрузилось, а не изучает устройство загрузки.
--
-- SECURITY DEFINER: журнал нужен по ВСЕМ поклажедателям, а таблицы
-- под RLS. Право на раздел проверяет приложение, как и в остальном
-- администрировании (V18–V20).
CREATE OR REPLACE FUNCTION admin_excel_import_issues(p_depositor_id uuid DEFAULT NULL,
                                                     p_limit int DEFAULT 500)
RETURNS TABLE (
  import_id      text,
  started_at     timestamptz,
  depositor_code varchar,
  depositor_name varchar,
  login          varchar,
  full_name      varchar,
  file_name      varchar,
  row_number     integer,
  row_class      varchar,
  kind           varchar,
  column_id      varchar,
  message        text,
  file_value     text,
  portal_value   text
)
LANGUAGE sql
SECURITY DEFINER
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
         i.portal_value
  FROM excel_import_issue i
  JOIN excel_import e ON e.import_id = i.import_id
  LEFT JOIN excel_import_row r
         ON r.import_id = i.import_id AND r.row_number = i.row_number
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC, i.row_number, i.id
  LIMIT p_limit;
$$;
GRANT EXECUTE ON FUNCTION admin_excel_import_issues(uuid, int) TO portal_app;

-- Список самих загрузок — та же причина для SECURITY DEFINER.
-- Нужен и администратору (сводка над списком замечаний), и на будущее
-- для разбора «что вообще грузили».
CREATE OR REPLACE FUNCTION admin_excel_imports(p_depositor_id uuid DEFAULT NULL,
                                               p_limit int DEFAULT 200)
RETURNS TABLE (
  import_id      text,
  started_at     timestamptz,
  depositor_code varchar,
  depositor_name varchar,
  login          varchar,
  full_name      varchar,
  file_name      varchar,
  row_count      integer,
  created_rows   integer,
  updated_rows   integer,
  error_count    integer,
  status         varchar
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo
AS $$
  SELECT e.import_id::text,
         e.started_at,
         d.code,
         d.org_name,
         u.login,
         u.full_name,
         e.file_name,
         e.row_count,
         e.created_rows,
         e.updated_rows,
         e.error_count,
         e.status
  FROM excel_import e
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC
  LIMIT p_limit;
$$;
GRANT EXECUTE ON FUNCTION admin_excel_imports(uuid, int) TO portal_app;

-- =====================================================================
-- 3. Чистка
-- =====================================================================
-- Решение заказчика: журнал держим 90 дней, разобранные строки —
-- до применения или отмены.
--
-- Разобранные строки (cell_values) — промежуточные данные на минуты
-- между проверкой и нажатием кнопки. Их убирает приложение, когда
-- окно проверки закрывают. Но окно могут и не закрыть — закрыть
-- браузер, уснуть, потерять связь; тогда строки остались бы навсегда.
-- Поэтому здесь ещё и срок: строки старше недели применить всё равно
-- нельзя, а объяснения в замечаниях остаются.
--
-- Две операции в одной функции нарочно: вызывается она из одного места
-- (начало новой загрузки), и забыть одну из них не получится.
--
-- SECURITY DEFINER: чистит по всем поклажедателям, а не только
-- по текущему.
CREATE OR REPLACE FUNCTION excel_import_cleanup()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
BEGIN
  DELETE FROM excel_import
   WHERE started_at < now() - interval '90 days';

  DELETE FROM excel_import_row
   WHERE import_id IN (SELECT import_id FROM excel_import
                        WHERE started_at < now() - interval '7 days');
END;
$$;
GRANT EXECUTE ON FUNCTION excel_import_cleanup() TO portal_app;

COMMENT ON FUNCTION excel_import_cleanup() IS
  'Журнал старше 90 дней удаляется целиком; разобранные строки старше '
  '7 дней — забытые окна проверки. Вызывается при начале новой загрузки.';

-- =====================================================================
-- 4. Тексты
-- =====================================================================
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
    ('sku.message.created_items',     'sku',   'message', NULL,
   'итог применения: создано позиций'),
  ('sku.message.updated_items',     'sku',   'message', NULL,
   'итог применения: обновлено позиций'),
  ('sku.message.nothing_to_apply',  'sku',   'message', NULL,
   'применять нечего: всё уже записано'),
  ('sku.message.apply_partly',      'sku',   'message', NULL,
   'часть строк не записалась, причина в таблице'),
  ('sku.text.apply_failed',         'sku',   'text',   NULL,
   'замечание строки, которую не удалось записать'),
  ('admin.column.row_state',        'admin', 'column', NULL,
   'колонка журнала загрузок: класс строки'),
  ('admin.text.import_log_empty',   'admin', 'text',   NULL,
   'в журнале загрузок нет записей'),
  ('admin.text.import_count',       'admin', 'text',   NULL,
   'сводка журнала: сколько было загрузок'),
  ('admin.hint.import_log',         'admin', 'hint',   NULL,
   'пояснение к журналу загрузок из Excel')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

-- Заглушка «запись появится следующим шагом» больше не показывается:
-- кнопки работают. Ключ оставляем в каталоге — удалять текст, на который
-- мог ссылаться старый собранный экран, незачем.

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('sku.message.created_items',    'ru', 'Создано позиций'),
  ('sku.message.created_items',    'en', 'Items created'),
  ('sku.message.updated_items',    'ru', 'Обновлено позиций'),
  ('sku.message.updated_items',    'en', 'Items updated'),
  ('sku.message.nothing_to_apply', 'ru', 'Записывать нечего — всё уже применено'),
  ('sku.message.nothing_to_apply', 'en', 'Nothing left to write — everything is applied'),
  ('sku.message.apply_partly',     'ru',
   'Часть строк записать не удалось — причина указана в таблице'),
  ('sku.message.apply_partly',     'en',
   'Some rows could not be written — the reason is shown in the table'),
  ('sku.text.apply_failed',        'ru', 'не записана'),
  ('sku.text.apply_failed',        'en', 'not written'),
  ('admin.column.row_state',       'ru', 'Строка файла'),
  ('admin.column.row_state',       'en', 'File row'),
  ('admin.text.import_count',      'ru', 'Загрузок'),
  ('admin.text.import_count',      'en', 'Imports'),
  ('admin.text.import_log_empty',  'ru', 'Загрузок из Excel пока не было'),
  ('admin.text.import_log_empty',  'en', 'No Excel imports yet'),
  ('admin.hint.import_log',        'ru',
   'Замечания по всем поклажедателям за последние 90 дней. Строка, по которой есть замечание, в справочник не записана.'),
  ('admin.hint.import_log',        'en',
   'Issues across all depositors for the last 90 days. A row with an issue was not written to the directory.')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
