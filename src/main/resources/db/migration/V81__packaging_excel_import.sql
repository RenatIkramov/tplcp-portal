-- ---------------------------------------------------------------------
-- V81. Загрузка упаковок из Excel
-- ---------------------------------------------------------------------
-- Механизм тот же, что у номенклатуры (V37, V38, V40): шаблон, разбор
-- с проверкой, экран проверки, применение по строкам, журнал. Таблицы
-- загрузок общие — у них с самого начала есть колонка kind. Здесь
-- только то, чего упаковкам не хватает.
--
-- Решения заказчика от 26.10.2026:
--   * предел — 100 строк, как у товаров;
--   * в шаблоне есть колонка «Единица заказа» (да/нет) и подчиняется
--     тому же правилу, что всё остальное: заполнить пустое можно,
--     поменять уже выбранное нельзя.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Найденная упаковка в разобранной строке
-- ---------------------------------------------------------------------
-- У товара строка опознаётся одним кодом, и найденную позицию хранит
-- sku_id. У упаковки ключ — пара «товар + код упаковки»: sku_id держит
-- товар, а саму упаковку, если она уже заведена, — эта колонка. Без неё
-- при обновлении пришлось бы искать упаковку заново по коду, и между
-- проверкой и кнопкой «Обновить» её успели бы переименовать.
--
-- Внешнего ключа нет так же, как у sku_id: строка загрузки живёт
-- минуты, а удалённая за это время упаковка должна давать понятное
-- замечание при записи, а не отказ на вставке разобранного файла.
ALTER TABLE excel_import_row ADD COLUMN IF NOT EXISTS packaging_id uuid;

COMMENT ON COLUMN excel_import_row.packaging_id IS
  'загрузка упаковок: найденная упаковка, если пара «товар + код упаковки» уже заведена';

-- ---------------------------------------------------------------------
-- 2. «Единица заказа» — колонкой списка упаковок
-- ---------------------------------------------------------------------
-- Шаблон и загрузчик берут подписи колонок из справочника колонок: так
-- заголовок файла совпадает с подписью на экране, с учётом
-- переименования поля клиентом. Колонки «Единица заказа» у упаковок
-- в справочнике не было — признак жил только в карточке товара. Теперь
-- он виден и в списке упаковок: «да» у той, что окно строки
-- подставляет само.
--
-- Ключ подписи не заводится: «Единица заказа» уже есть (col.sku.orderUnit),
-- и два ключа на одну фразу значили бы переводить её дважды (так же
-- рассуждала V76).
--
-- Фильтр — галочка (V80): «Только единицы заказа». Сортировка — по
-- самому признаку.
INSERT INTO table_column
  (table_key, column_id, text_key, object_type_id, field_id,
   sort_order, is_default, is_locked, is_pinned,
   filter_kind, filter_column, filter_default, sort_column)
VALUES
  ('packaging', 'isDefaultOrderUnit', 'col.sku.orderUnit',
   (SELECT id FROM enum_object_type WHERE code = 'packaging'),
   'isDefaultOrderUnit', 14, true, false, false,
   'bool', 'is_default_order_unit', false, 'is_default_order_unit')
ON CONFLICT (table_key, column_id) DO UPDATE
   SET text_key       = excluded.text_key,
       sort_order     = excluded.sort_order,
       filter_kind    = excluded.filter_kind,
       filter_column  = excluded.filter_column,
       filter_default = excluded.filter_default,
       sort_column    = excluded.sort_column;

-- ---------------------------------------------------------------------
-- 3. Журнал: что грузили
-- ---------------------------------------------------------------------
-- Журнал администратора не различал товары и упаковки: kind в таблице
-- был, а наружу не выходил. С появлением второй загрузки строка
-- «Не найдено в справочнике» стала двусмысленной — в каком?
--
-- Функции пересоздаются: CREATE OR REPLACE не меняет набор
-- возвращаемых колонок (грабля из V70, V72).
DROP FUNCTION IF EXISTS admin_excel_import_issues(uuid, int);
CREATE FUNCTION admin_excel_import_issues(p_depositor_id uuid DEFAULT NULL,
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
  portal_value   text,
  import_kind    varchar
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
         i.portal_value,
         e.kind
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

DROP FUNCTION IF EXISTS admin_excel_imports(uuid, int);
CREATE FUNCTION admin_excel_imports(p_depositor_id uuid DEFAULT NULL,
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
  status         varchar,
  import_kind    varchar
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
         e.status,
         e.kind
  FROM excel_import e
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC
  LIMIT p_limit;
$$;
GRANT EXECUTE ON FUNCTION admin_excel_imports(uuid, int) TO portal_app;

COMMENT ON COLUMN excel_import.kind IS 'что грузили: sku — номенклатура, packaging — упаковки';

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
-- Экран проверки общий с номенклатурой; свои у упаковок только слова,
-- где сказано, ЧТО создаётся. «Создать новые позиции» над списком
-- упаковок читалось бы как «создать товары».
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('references.title.packaging_import_check', 'references', 'title', NULL,
   'экран проверки загрузки упаковок'),
  ('references.tab.new_packagings', 'references', 'tab', NULL,
   'экран проверки загрузки упаковок: вкладка и галочка-фильтр'),
  ('references.tab.existing_packagings', 'references', 'tab', NULL,
   'экран проверки загрузки упаковок: вкладка и галочка-фильтр'),
  ('references.button.create_new_packagings', 'references', 'button', NULL,
   'экран проверки загрузки упаковок'),
  ('references.button.update_existing_packagings', 'references', 'button', NULL,
   'экран проверки загрузки упаковок'),
  ('references.message.created_packagings', 'references', 'message', NULL,
   'итог применения загрузки упаковок'),
  ('references.message.updated_packagings', 'references', 'message', NULL,
   'итог применения загрузки упаковок'),
  ('common.text.packaging_template_file_name', 'common', 'text', NULL,
   'имя файла шаблона упаковок при скачивании, без расширения'),
  ('admin.column.import_kind', 'admin', 'column', NULL,
   'журнал загрузок Excel: что грузили — товары или упаковки'),
  ('error.excel.no_packaging_key_columns', 'server', 'message', 'excel',
   'сообщение сервера; собирается по ключу в PortalMessages'),
  ('error.template.no_packaging_columns', 'server', 'message', 'excel',
   'сообщение сервера; собирается по ключу в PortalMessages'),
  ('error.packaging.order_unit_taken', 'server', 'message', 'excel',
   'сообщение сервера; собирается по ключу в PortalMessages')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('references.title.packaging_import_check',      'ru', 'Проверка загрузки упаковок'),
    ('references.title.packaging_import_check',      'en', 'Packaging upload check'),
    ('references.tab.new_packagings',                'ru', 'Новые упаковки'),
    ('references.tab.new_packagings',                'en', 'New packagings'),
    ('references.tab.existing_packagings',           'ru', 'Существующие упаковки'),
    ('references.tab.existing_packagings',           'en', 'Existing packagings'),
    ('references.button.create_new_packagings',      'ru', 'Создать новые упаковки'),
    ('references.button.create_new_packagings',      'en', 'Create new packagings'),
    ('references.button.update_existing_packagings', 'ru', 'Обновить существующие упаковки'),
    ('references.button.update_existing_packagings', 'en', 'Update existing packagings'),
    ('references.message.created_packagings',        'ru', 'Создано упаковок'),
    ('references.message.created_packagings',        'en', 'Packagings created'),
    ('references.message.updated_packagings',        'ru', 'Обновлено упаковок'),
    ('references.message.updated_packagings',        'en', 'Packagings updated'),
    ('common.text.packaging_template_file_name',     'ru', 'Шаблон_Упаковки'),
    ('common.text.packaging_template_file_name',     'en', 'Packaging_template'),
    ('admin.column.import_kind',                     'ru', 'Справочник'),
    ('admin.column.import_kind',                     'en', 'Directory'),
    ('error.excel.no_packaging_key_columns',         'ru',
     'В файле нет столбца с кодом товара или с кодом упаковки — по ним опознаётся упаковка. Найденные заголовки: {headers}'),
    ('error.excel.no_packaging_key_columns',         'en',
     'The file has no column with the item code or the packaging code — they identify a packaging. Headers found: {headers}'),
    ('error.template.no_packaging_columns',          'ru',
     'Для этого поклажедателя не осталось доступных колонок упаковок'),
    ('error.template.no_packaging_columns',          'en',
     'No packaging columns are available for this depositor'),
    -- Единицу заказа успели выбрать в карточке товара, пока окно
    -- проверки было открыто: назначить её поверх — изменить заполненное.
    ('error.packaging.order_unit_taken',             'ru',
     'У товара уже выбрана другая единица заказа — её выбрали, пока шла проверка файла'),
    ('error.packaging.order_unit_taken',             'en',
     'The item already has another order unit — it was chosen while the file was being checked')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  nameless text;
  missing  text;
BEGIN
  -- Колонка фильтра и сортировки обязана существовать в представлении:
  -- иначе первый щелчок по заголовку упадёт ошибкой базы.
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'dbo' AND table_name = 'v_product_packaging'
                    AND column_name = 'is_default_order_unit'
                    AND data_type = 'boolean') THEN
    RAISE EXCEPTION 'в v_product_packaging нет логической колонки is_default_order_unit';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'dbo' AND table_name = 'excel_import_row'
                    AND column_name = 'packaging_id') THEN
    RAISE EXCEPTION 'в excel_import_row нет колонки packaging_id';
  END IF;

  -- Журнал отдаёт вид загрузки — без него экран не отличит упаковки.
  SELECT string_agg(f, ', ') INTO missing
    FROM (VALUES ('admin_excel_imports'), ('admin_excel_import_issues')) AS v(f)
   WHERE NOT EXISTS (
     SELECT 1 FROM pg_proc p
       JOIN pg_namespace n ON n.oid = p.pronamespace
      WHERE n.nspname = 'dbo' AND p.proname = v.f
        AND 'import_kind' = ANY (p.proargnames));
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'функции журнала без import_kind: %', missing;
  END IF;

  -- Ключ без текста человек увидит как «references.tab.new_packagings».
  SELECT string_agg(k.text_key || ':' || l.code, ', ') INTO nameless
    FROM ui_text_key k
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('references.title.packaging_import_check',
                        'references.tab.new_packagings',
                        'references.tab.existing_packagings',
                        'references.button.create_new_packagings',
                        'references.button.update_existing_packagings',
                        'references.message.created_packagings',
                        'references.message.updated_packagings',
                        'common.text.packaging_template_file_name',
                        'admin.column.import_kind',
                        'error.excel.no_packaging_key_columns',
                        'error.template.no_packaging_columns',
                        'error.packaging.order_unit_taken',
                        'col.sku.orderUnit')
     AND l.code IN ('ru', 'en')
     AND (t.value IS NULL OR t.value = '');
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'нет текста у ключей: %', nameless;
  END IF;
END
$check$;
