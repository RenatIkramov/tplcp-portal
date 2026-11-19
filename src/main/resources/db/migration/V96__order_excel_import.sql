-- ---------------------------------------------------------------------
-- V96. Заказ из Excel: шаблон и загрузка
-- ---------------------------------------------------------------------
-- Решения заказчика от 27.10 и 02.11.2026 (документ «Excel — импорт
-- заказа»):
--   * один файл — один заказ, результат — черновик; ошибки — списком,
--     и тогда ничего не создаётся;
--   * лист с тремя умными таблицами Goods / header / transport;
--     в header и transport — «Поле | Значение | Ключ», читается ключ;
--   * значения списков — «код — имя», читается код;
--   * качество строки — списком из перечисления (V89), пусто = Годный.
--
-- Здесь:
--   1. статус загрузки «Отклонено» — заказ из файла с ошибками
--      не создаётся, и «Проверено» о нём говорило бы неправду;
--   2. в журнале загрузок — какой черновик создан (doc_id, doc_number);
--      admin_excel_imports() отдаёт номер документа;
--   3. тексты шаблона, сообщения загрузки и окна ошибок.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Статус «Отклонено»
-- ---------------------------------------------------------------------
INSERT INTO enum_import_status (id, code, name, sort_order, is_active)
SELECT next_enum_id('enum_import_status'), '04', 'Rejected', 40, true
 WHERE NOT EXISTS (SELECT 1 FROM enum_import_status WHERE code = '04');

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_import_status', s.id::text, l.locale_id, 'Отклонено'
  FROM enum_import_status s, dictionary_locale l
 WHERE s.code = '04' AND l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- На код опирается программа (EnumCodes.ImportStatus.REJECTED)
INSERT INTO enum_system_value (enum_key, code) VALUES ('enum_import_status', '04')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Созданный документ — в журнале загрузок
-- ---------------------------------------------------------------------
-- Без внешнего ключа нарочно: черновик могут удалить, а запись журнала
-- о том, что он был создан из такого-то файла, должна остаться.
ALTER TABLE excel_import
  ADD COLUMN IF NOT EXISTS doc_id     uuid,
  ADD COLUMN IF NOT EXISTS doc_number varchar(30);
COMMENT ON COLUMN excel_import.doc_number IS
  'номер черновика, созданного загрузкой заказа (V96); без ссылки — черновик могут удалить';

DROP FUNCTION IF EXISTS admin_excel_imports(uuid, integer);
CREATE FUNCTION admin_excel_imports(p_depositor_id uuid DEFAULT NULL::uuid, p_limit integer DEFAULT 200)
RETURNS TABLE(import_id text, started_at timestamp with time zone, depositor_code character varying,
              depositor_name character varying, login character varying, full_name character varying,
              file_name character varying, row_count integer, created_rows integer,
              updated_rows integer, error_count integer, status character varying,
              import_kind character varying, status_name character varying,
              import_kind_name character varying, doc_number character varying)
LANGUAGE sql STABLE SECURITY DEFINER
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
         e.kind,
         enum_name('enum_import_status', st.id::text, st.name)::varchar,
         enum_name('enum_import_kind', ik.id::text, ik.name)::varchar,
         e.doc_number
  FROM excel_import e
  LEFT JOIN enum_import_status st ON st.code = e.status
  LEFT JOIN enum_import_kind ik ON ik.code = e.kind
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC
  LIMIT p_limit;
$$;
REVOKE ALL ON FUNCTION admin_excel_imports(uuid, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_excel_imports(uuid, integer) TO portal_app;

-- ---------------------------------------------------------------------
-- 3. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.k, v.scope, v.element, v.owner, v.note
  FROM (VALUES
    ('excel.order.sheet', 'excel', 'text', NULL, 'шаблон заказа: имя листа (V96)'),
    ('excel.order.file_name', 'excel', 'text', NULL, 'шаблон заказа: начало имени файла (V96)'),
    ('excel.order.title_goods', 'excel', 'text', NULL, 'шаблон заказа: заголовок таблицы состава (V96)'),
    ('excel.order.title_header', 'excel', 'text', NULL, 'шаблон заказа: заголовок таблицы шапки (V96)'),
    ('excel.order.title_transport', 'excel', 'text', NULL, 'шаблон заказа: заголовок таблицы транспорта (V96)'),
    ('excel.order.column_field', 'excel', 'text', NULL, 'шаблон заказа: колонка «Поле» (V96)'),
    ('excel.order.column_value', 'excel', 'text', NULL, 'шаблон заказа: колонка «Значение» (V96)'),
    ('excel.order.column_key', 'excel', 'text', NULL, 'шаблон заказа: колонка «Ключ» (V96)'),
    ('excel.order.goods_code', 'excel', 'text', NULL, 'шаблон заказа: колонка кода товара, если в справочнике колонок её нет (V96)'),
    ('excel.order.goods_qty', 'excel', 'text', NULL, 'шаблон заказа: колонка количества — запасная подпись (V96)'),
    ('excel.order.goods_quality', 'excel', 'text', NULL, 'шаблон заказа: колонка качества — запасная подпись (V96)'),
    ('excel.order.goods_comment', 'excel', 'text', NULL, 'шаблон заказа: колонка комментария — запасная подпись (V96)'),
    ('excel.order.goods_price', 'excel', 'text', NULL, 'шаблон заказа: колонка цены — запасная подпись (V96)'),
    ('excel.order.goods_currency', 'excel', 'text', NULL, 'шаблон заказа: колонка валюты — запасная подпись (V96)'),
    ('excel.order.note_key', 'excel', 'text', NULL, 'шаблон заказа: подсказка к колонке «Ключ» (V96)'),
    ('excel.order.note_list', 'excel', 'text', NULL, 'шаблон заказа: подсказка к полю со списком (V96)'),
    ('excel.order.note_address', 'excel', 'text', NULL, 'шаблон заказа: подсказка к месту доставки (V96)'),
    ('excel.order.note_date', 'excel', 'text', NULL, 'шаблон заказа: подсказка к дате (V96)'),
    ('excel.order.note_code', 'excel', 'text', NULL, 'шаблон заказа: подсказка к коду товара (V96)'),
    ('excel.order.note_qty', 'excel', 'text', NULL, 'шаблон заказа: подсказка к количеству (V96)'),
    ('excel.order.note_quality', 'excel', 'text', NULL, 'шаблон заказа: подсказка к качеству (V96)'),
    ('excel.order.validation_list_title', 'excel', 'text', NULL, 'шаблон заказа: ошибка ячейки со списком, заголовок (V96)'),
    ('excel.order.validation_list_message', 'excel', 'text', NULL, 'шаблон заказа: ошибка ячейки со списком, текст (V96)'),
    ('excel.order.validation_code_title', 'excel', 'text', NULL, 'шаблон заказа: неизвестный код товара, заголовок (V96)'),
    ('excel.order.validation_code_message', 'excel', 'text', NULL, 'шаблон заказа: неизвестный код товара, текст (V96)'),
    ('excel.order.validation_qty_title', 'excel', 'text', NULL, 'шаблон заказа: количество, заголовок ошибки (V96)'),
    ('excel.order.validation_qty_message', 'excel', 'text', NULL, 'шаблон заказа: количество, текст ошибки (V96)'),
    ('excel.order.validation_price_title', 'excel', 'text', NULL, 'шаблон заказа: цена, заголовок ошибки (V96)'),
    ('excel.order.validation_price_message', 'excel', 'text', NULL, 'шаблон заказа: цена, текст ошибки (V96)'),
    ('excel.order.validation_date_title', 'excel', 'text', NULL, 'шаблон заказа: дата, заголовок ошибки (V96)'),
    ('excel.order.validation_date_message', 'excel', 'text', NULL, 'шаблон заказа: дата, текст ошибки (V96)'),
    ('error.order.create_disabled', 'server', 'message', 'orders', 'сообщение сервера: пользователю запрещено создавать заказы (V96)'),
    ('error.order_import.no_goods_table', 'server', 'message', 'orders', 'загрузка заказа: в файле нет таблицы Goods (V96)'),
    ('error.order_import.no_goods', 'server', 'message', 'orders', 'загрузка заказа: состав пуст (V96)'),
    ('error.order_import.field_not_available', 'server', 'message', 'orders', 'загрузка заказа: поле не открыто поклажедателю или неизвестно (V96)'),
    ('error.order_import.transport_not_allowed', 'server', 'message', 'orders', 'загрузка заказа: транспорт без права на транспорт (V96)'),
    ('error.order_import.too_long', 'server', 'message', 'orders', 'загрузка заказа: значение длиннее колонки (V96)'),
    ('error.order_import.bad_date', 'server', 'message', 'orders', 'загрузка заказа: неразборчивая дата (V96)'),
    ('error.order_import.not_in_list', 'server', 'message', 'orders', 'загрузка заказа: значение не из списка (V96)'),
    ('error.order_import.address_without_consignee', 'server', 'message', 'orders', 'загрузка заказа: место доставки без грузополучателя (V96)'),
    ('error.order_import.address_not_found', 'server', 'message', 'orders', 'загрузка заказа: адреса нет у грузополучателя (V96)'),
    ('error.order_import.code_required', 'server', 'message', 'orders', 'загрузка заказа: в строке нет кода товара (V96)'),
    ('error.order_import.unknown_item', 'server', 'message', 'orders', 'загрузка заказа: неизвестный товар (V96)'),
    ('error.order_import.archived_item', 'server', 'message', 'orders', 'загрузка заказа: товар в архиве (V96)'),
    ('error.order_import.bad_quantity', 'server', 'message', 'orders', 'загрузка заказа: количество не число или не больше нуля (V96)'),
    ('error.order_import.bad_price', 'server', 'message', 'orders', 'загрузка заказа: цена не число или меньше нуля (V96)'),
    ('error.order_import.price_differs', 'server', 'message', 'orders', 'загрузка заказа: у повтора строки другая цена (V96)'),
    ('orders.title.import_failed', 'orders', 'title', NULL, 'окно ошибок загрузки заказа: заголовок (V96)'),
    ('orders.hint.import_failed', 'orders', 'hint', NULL, 'окно ошибок загрузки заказа: что делать (V96)'),
    ('orders.column.import_table', 'orders', 'column', NULL, 'окно ошибок загрузки заказа: колонка «Таблица» (V96)'),
    ('orders.column.import_row', 'orders', 'column', NULL, 'окно ошибок загрузки заказа: колонка «Строка» (V96)'),
    ('orders.column.import_field', 'orders', 'column', NULL, 'окно ошибок загрузки заказа: колонка «Поле» (V96)'),
    ('orders.column.import_problem', 'orders', 'column', NULL, 'окно ошибок загрузки заказа: колонка «Что не так» (V96)'),
    ('orders.text.import_table_goods', 'orders', 'text', NULL, 'окно ошибок загрузки заказа: таблица состава (V96)'),
    ('orders.text.import_table_header', 'orders', 'text', NULL, 'окно ошибок загрузки заказа: таблица шапки (V96)'),
    ('orders.text.import_table_transport', 'orders', 'text', NULL, 'окно ошибок загрузки заказа: таблица транспорта (V96)'),
    ('orders.text.import_table_file', 'orders', 'text', NULL, 'окно ошибок загрузки заказа: файл целиком (V96)'),
    ('orders.message.imported', 'orders', 'message', NULL, 'плашка: черновик заказа создан из файла (V96)'),
    ('orders.message.importing', 'orders', 'message', NULL, 'кнопка загрузки заказа: идёт разбор файла (V96)'),
    ('orders.text.order_template_file_name', 'orders', 'text', NULL, 'запасное имя файла шаблона заказа (V96)'),
    ('admin.text.import_documents', 'admin', 'text', NULL, 'журнал загрузок: созданные из файлов документы (V96)')
  ) AS v(k, scope, element, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('excel.order.sheet', 'ru', 'Заказ'),
    ('excel.order.sheet', 'en', 'Order'),
    ('excel.order.file_name', 'ru', 'Шаблон_Заказ'),
    ('excel.order.file_name', 'en', 'Order_template'),
    ('excel.order.title_goods', 'ru', 'Состав заказа'),
    ('excel.order.title_goods', 'en', 'Order lines'),
    ('excel.order.title_header', 'ru', 'Шапка заказа'),
    ('excel.order.title_header', 'en', 'Order header'),
    ('excel.order.title_transport', 'ru', 'Транспорт'),
    ('excel.order.title_transport', 'en', 'Transport'),
    ('excel.order.column_field', 'ru', 'Поле'),
    ('excel.order.column_field', 'en', 'Field'),
    ('excel.order.column_value', 'ru', 'Значение'),
    ('excel.order.column_value', 'en', 'Value'),
    ('excel.order.column_key', 'ru', 'Ключ'),
    ('excel.order.column_key', 'en', 'Key'),
    ('excel.order.goods_code', 'ru', 'Код товара'),
    ('excel.order.goods_code', 'en', 'Item code'),
    ('excel.order.goods_qty', 'ru', 'Количество'),
    ('excel.order.goods_qty', 'en', 'Quantity'),
    ('excel.order.goods_quality', 'ru', 'Качество'),
    ('excel.order.goods_quality', 'en', 'Quality'),
    ('excel.order.goods_comment', 'ru', 'Комментарий'),
    ('excel.order.goods_comment', 'en', 'Comment'),
    ('excel.order.goods_price', 'ru', 'Цена'),
    ('excel.order.goods_price', 'en', 'Price'),
    ('excel.order.goods_currency', 'ru', 'Валюта'),
    ('excel.order.goods_currency', 'en', 'Currency'),
    ('excel.order.note_key', 'ru', 'Служебный ключ поля: по нему портал читает строку. Не меняйте его.'),
    ('excel.order.note_key', 'en', 'Technical field key: the portal reads the row by it. Do not change it.'),
    ('excel.order.note_list', 'ru', 'Выберите из списка.'),
    ('excel.order.note_list', 'en', 'Choose from the list.'),
    ('excel.order.note_address', 'ru', 'Адрес места доставки так, как он записан у грузополучателя. Пусто — основной адрес.'),
    ('excel.order.note_address', 'en', 'The delivery address as it is recorded for the consignee. Empty means the main address.'),
    ('excel.order.note_date', 'ru', 'Дата, например 25.12.2026.'),
    ('excel.order.note_date', 'en', 'A date, for example 25.12.2026.'),
    ('excel.order.note_code', 'ru', 'Коммерческий код товара. Список — товары на момент скачивания шаблона.'),
    ('excel.order.note_code', 'en', 'Commercial item code. The list holds the items at the time the template was downloaded.'),
    ('excel.order.note_qty', 'ru', 'Число больше нуля.'),
    ('excel.order.note_qty', 'en', 'A number greater than zero.'),
    ('excel.order.note_quality', 'ru', 'Выберите из списка. Пусто — годный товар.'),
    ('excel.order.note_quality', 'en', 'Choose from the list. Empty means good stock.'),
    ('excel.order.validation_list_title', 'ru', 'Значение не из списка'),
    ('excel.order.validation_list_title', 'en', 'Value is not in the list'),
    ('excel.order.validation_list_message', 'ru', 'Выберите значение из списка.'),
    ('excel.order.validation_list_message', 'en', 'Choose a value from the list.'),
    ('excel.order.validation_code_title', 'ru', 'Нет такого товара'),
    ('excel.order.validation_code_title', 'en', 'No such item'),
    ('excel.order.validation_code_message', 'ru', 'Выберите код товара из списка. Если товара нет — заведите его в справочнике товаров и скачайте шаблон заново.'),
    ('excel.order.validation_code_message', 'en', 'Choose an item code from the list. If the item is missing, add it to the item directory and download the template again.'),
    ('excel.order.validation_qty_title', 'ru', 'Нужно число больше нуля'),
    ('excel.order.validation_qty_title', 'en', 'A number greater than zero is required'),
    ('excel.order.validation_qty_message', 'ru', 'Введите число больше нуля.'),
    ('excel.order.validation_qty_message', 'en', 'Enter a number greater than zero.'),
    ('excel.order.validation_price_title', 'ru', 'Нужно число'),
    ('excel.order.validation_price_title', 'en', 'A number is required'),
    ('excel.order.validation_price_message', 'ru', 'Введите число, не меньше нуля.'),
    ('excel.order.validation_price_message', 'en', 'Enter a number, zero or more.'),
    ('excel.order.validation_date_title', 'ru', 'Нужна дата'),
    ('excel.order.validation_date_title', 'en', 'A date is required'),
    ('excel.order.validation_date_message', 'ru', 'Введите дату, например 25.12.2026.'),
    ('excel.order.validation_date_message', 'en', 'Enter a date, for example 25.12.2026.'),
    ('error.order.create_disabled', 'ru', 'Создание заказов для вас отключено'),
    ('error.order.create_disabled', 'en', 'Creating orders is disabled for you'),
    ('error.order_import.no_goods_table', 'ru', 'В файле нет таблицы состава заказа (Goods). Скачайте шаблон заказа заново'),
    ('error.order_import.no_goods_table', 'en', 'The file has no order lines table (Goods). Download the order template again'),
    ('error.order_import.no_goods', 'ru', 'Состав заказа пуст'),
    ('error.order_import.no_goods', 'en', 'The order has no lines'),
    ('error.order_import.field_not_available', 'ru', 'Это поле в заказе вам недоступно'),
    ('error.order_import.field_not_available', 'en', 'This order field is not available to you'),
    ('error.order_import.transport_not_allowed', 'ru', 'Транспорт вам заполнять не разрешено'),
    ('error.order_import.transport_not_allowed', 'en', 'You are not allowed to fill in the transport'),
    ('error.order_import.too_long', 'ru', 'Не больше {limit} знаков'),
    ('error.order_import.too_long', 'en', 'No more than {limit} characters'),
    ('error.order_import.bad_date', 'ru', 'Не удалось прочитать дату. Пример: 25.12.2026'),
    ('error.order_import.bad_date', 'en', 'The date could not be read. Example: 25.12.2026'),
    ('error.order_import.not_in_list', 'ru', 'Значения нет в списке'),
    ('error.order_import.not_in_list', 'en', 'The value is not in the list'),
    ('error.order_import.address_without_consignee', 'ru', 'Место доставки указано, а грузополучатель — нет'),
    ('error.order_import.address_without_consignee', 'en', 'A delivery address is given but the consignee is not'),
    ('error.order_import.address_not_found', 'ru', 'У грузополучателя нет такого места доставки'),
    ('error.order_import.address_not_found', 'en', 'The consignee has no such delivery address'),
    ('error.order_import.code_required', 'ru', 'Не указан код товара'),
    ('error.order_import.code_required', 'en', 'The item code is missing'),
    ('error.order_import.unknown_item', 'ru', 'Товара с таким кодом нет в справочнике'),
    ('error.order_import.unknown_item', 'en', 'There is no item with this code in the directory'),
    ('error.order_import.archived_item', 'ru', 'Товар в архиве — заказать его нельзя'),
    ('error.order_import.archived_item', 'en', 'The item is archived and cannot be ordered'),
    ('error.order_import.bad_quantity', 'ru', 'Количество — число больше нуля'),
    ('error.order_import.bad_quantity', 'en', 'The quantity must be a number greater than zero'),
    ('error.order_import.bad_price', 'ru', 'Цена — число, не меньше нуля'),
    ('error.order_import.bad_price', 'en', 'The price must be a number, zero or more'),
    ('error.order_import.price_differs', 'ru', 'Тот же товар уже есть в строке {row} с другой ценой'),
    ('error.order_import.price_differs', 'en', 'The same item is already in row {row} with a different price'),
    ('orders.title.import_failed', 'ru', 'Заказ не создан'),
    ('orders.title.import_failed', 'en', 'The order was not created'),
    ('orders.hint.import_failed', 'ru', 'Исправьте файл и загрузите его снова. Пока в файле есть ошибки, черновик не создаётся.'),
    ('orders.hint.import_failed', 'en', 'Fix the file and upload it again. No draft is created while the file has errors.'),
    ('orders.column.import_table', 'ru', 'Таблица'),
    ('orders.column.import_table', 'en', 'Table'),
    ('orders.column.import_row', 'ru', 'Строка'),
    ('orders.column.import_row', 'en', 'Row'),
    ('orders.column.import_field', 'ru', 'Поле'),
    ('orders.column.import_field', 'en', 'Field'),
    ('orders.column.import_problem', 'ru', 'Что не так'),
    ('orders.column.import_problem', 'en', 'Problem'),
    ('orders.text.import_table_goods', 'ru', 'Состав'),
    ('orders.text.import_table_goods', 'en', 'Lines'),
    ('orders.text.import_table_header', 'ru', 'Шапка'),
    ('orders.text.import_table_header', 'en', 'Header'),
    ('orders.text.import_table_transport', 'ru', 'Транспорт'),
    ('orders.text.import_table_transport', 'en', 'Transport'),
    ('orders.text.import_table_file', 'ru', 'Файл'),
    ('orders.text.import_table_file', 'en', 'File'),
    ('orders.message.imported', 'ru', 'Создан черновик заказа {no}, строк: {count}. Проверьте его и подтвердите'),
    ('orders.message.imported', 'en', 'Draft order {no} created, lines: {count}. Check it and confirm'),
    ('orders.message.importing', 'ru', 'Загружаю…'),
    ('orders.message.importing', 'en', 'Uploading…'),
    ('orders.text.order_template_file_name', 'ru', 'Шаблон_Заказ'),
    ('orders.text.order_template_file_name', 'en', 'Order_template'),
    ('admin.text.import_documents', 'ru', 'Созданы из файлов'),
    ('admin.text.import_documents', 'en', 'Created from files')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
