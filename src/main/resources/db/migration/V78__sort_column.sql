-- ---------------------------------------------------------------------
-- V78. Чем сортировать список: колонка сортировки в справочнике колонок
-- ---------------------------------------------------------------------
-- Разбивка на страницы переезжает на сервер, и вместе с ней обязана
-- переехать сортировка. Отсортировать страницу, которую уже отрезали,
-- значит отсортировать двадцать строк из ста тридцати: человек щёлкает
-- «Вес» и получает самый тяжёлый документ не из списка, а из того,
-- что сейчас на экране.
--
-- В заголовке таблицы колонка названа идентификатором раскладки
-- (weight, supplier), а ORDER BY нужен по колонке представления
-- (weight_kg, supplier_name). Подставлять в SQL то, что пришло
-- с экрана, нельзя — это дыра; значит имя надо не подставлять,
-- а СВЕРЯТЬ со справочником, как уже сверяются фильтры.
--
-- Почему не хватило filter_column:
--
--   1. Он заполнен не везде. У «Объёма», «Мест», «Штук», всего
--      транспортного раздела и доп. полей фильтра нет — сортировать
--      по ним человек всё равно хочет, и колонка, заголовок которой
--      нажимается и ничего не делает, хуже неподвижного заголовка.
--
--   2. Там, где он есть, он нередко НЕ ТА колонка. Фильтр по складу
--      сравнивает warehouse_id — это верно: выбран конкретный склад.
--      Сортировка по warehouse_id расставила бы строки по случайному
--      порядку uuid: человек видит названия, а порядок — по невидимым
--      идентификаторам. Сортируем по тому, что человек читает:
--      warehouse_name, status_name, doc_type_name, order_type_name.
ALTER TABLE table_column ADD COLUMN IF NOT EXISTS sort_column varchar(63);
COMMENT ON COLUMN table_column.sort_column IS
  'Колонка представления, по которой сортируется список; NULL — сортировка по этой колонке не поддержана';

-- Общие для всех трёх списков документов.
UPDATE table_column c
   SET sort_column = v.sort_column
  FROM (VALUES
    ('status',             'status_name'),
    ('erpNo',              'erp_number'),
    ('wmsNumber',          'doc_number'),
    ('no',                 'document_no'),
    ('date',               'doc_date'),
    ('author',             'author_name'),
    ('comment',            'comment'),
    ('trip',               'trip'),
    ('warehouse',          'warehouse_name'),
    ('lineCount',          'line_count'),
    ('weight',             'weight_kg'),
    ('volume',             'volume_m3'),
    ('palletCount',        'pallet_count'),
    ('cargoPlaces',        'cargo_places'),
    ('totalPieces',        'total_pieces'),
    ('carrier',            'carrier'),
    ('transportType',      'transport_type'),
    ('transportBrand',     'transport_brand'),
    ('carNumber',          'car_number'),
    ('trailerNumber',      'trailer_number'),
    ('techPassportNumber', 'tech_passport_number'),
    ('containerNumber',    'container_number'),
    ('sealNumber',         'seal_number'),
    ('driverName',         'driver_name'),
    ('driverPhone',        'driver_phone'),
    ('driverIdNumber',     'driver_id_number'),
    ('driverInn',          'driver_inn'),
    ('customField1',       'custom_field_1'),
    ('customField2',       'custom_field_2'),
    ('customField3',       'custom_field_3'),
    ('customField4',       'custom_field_4'),
    ('customField5',       'custom_field_5'),
    ('customField6',       'custom_field_6'),
    ('customField7',       'custom_field_7'),
    ('customField8',       'custom_field_8'),
    ('customField9',       'custom_field_9'),
    ('customField10',      'custom_field_10')
  ) AS v(column_id, sort_column)
 WHERE c.table_key IN ('receipts', 'orders', 'transfers')
   AND c.column_id = v.column_id;

-- Свои у каждого списка.
UPDATE table_column c
   SET sort_column = v.sort_column
  FROM (VALUES
    ('receipts',  'eta',                   'eta_date'),
    ('receipts',  'arrivalDate',           'arrival_date'),
    ('receipts',  'receivingEndDate',      'receiving_end_date'),
    ('receipts',  'supplier',              'supplier_name'),
    ('receipts',  'docType',               'doc_type_name'),
    ('receipts',  'accompanyingDocNumber', 'accompanying_doc_number'),
    ('receipts',  'gtd',                   'gtd'),
    ('orders',    'ship',                  'ship_date'),
    ('orders',    'plannedShip',           'planned_ship_date'),
    ('orders',    'consignee',             'consignee_name'),
    ('orders',    'deliveryAddress',       'delivery_address'),
    ('orders',    'orderType',             'order_type_name'),
    ('transfers', 'fromWarehouse',         'from_warehouse_name'),
    ('transfers', 'toWarehouse',           'to_warehouse_name'),
    ('transfers', 'shipDate',              'ship_date'),
    ('transfers', 'receivedDate',          'received_date')
  ) AS v(table_key, column_id, sort_column)
 WHERE c.table_key = v.table_key
   AND c.column_id = v.column_id;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
-- Главное здесь — вторая проверка. Опечатка в имени колонки не видна
-- ни глазами, ни тестом сервиса: ошибка вылезет у человека, нажавшего
-- на заголовок, и выглядеть будет как «список сломался». Сверяем
-- каждое имя с information_schema прямо в миграции: не доедет.
DO $check$
DECLARE
  nameless text;
  stranger text;
BEGIN
  SELECT string_agg(table_key || '.' || column_id, ', ')
    INTO nameless
    FROM table_column
   WHERE table_key IN ('receipts', 'orders', 'transfers')
     AND sort_column IS NULL;
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'у колонок документов нет колонки сортировки: %', nameless;
  END IF;

  SELECT string_agg(c.table_key || '.' || c.column_id || ' → ' || c.sort_column, ', ')
    INTO stranger
    FROM table_column c
   WHERE c.table_key IN ('receipts', 'orders', 'transfers')
     AND NOT EXISTS (
           SELECT 1 FROM information_schema.columns ic
            WHERE ic.table_schema = 'dbo'
              AND ic.table_name = 'v_' || c.table_key
              AND ic.column_name = c.sort_column);
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'сортировка ссылается на несуществующие колонки: %', stranger;
  END IF;

  -- Сортировка по идентификатору — та самая ошибка, ради которой
  -- колонка и заведена отдельно от filter_column.
  SELECT string_agg(c.table_key || '.' || c.column_id, ', ')
    INTO stranger
    FROM table_column c
   WHERE c.table_key IN ('receipts', 'orders', 'transfers')
     AND c.filter_kind = 'enum'
     AND c.sort_column LIKE '%\_id';
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'сортировка по идентификатору вместо названия: %', stranger;
  END IF;
END
$check$;

-- ---------------------------------------------------------------------
-- Подписи ряда страниц
-- ---------------------------------------------------------------------
-- Ряд «Строк на странице / Назад / Вперёд» подписан в прототипе
-- условием currentLang === 'en' — мимо каталога и мимо переключателя
-- языка. Раз ряд всё равно переделывается, подписи уезжают туда,
-- где лежат все остальные.
--
-- «Стр. 2 из 7 · всего 134» — ОДНА фраза с подстановками, а не четыре
-- обрывка. Собранная из кусков, она намертво закрепляет русский
-- порядок слов, и перевод получается русской фразой английскими
-- словами.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.sort.unknown_column', 'server', 'message', 'documents',
   'отказ: сортировка по колонке, которой нет в справочнике или которую клиенту не выдали'),
  ('error.page.bad_number', 'server', 'message', 'documents',
   'отказ: номер страницы или размер страницы не число'),
  ('common.label.rows_per_page', 'common', 'label', NULL, 'ряд страниц: выбор размера страницы'),
  ('common.button.prev_page', 'common', 'button', NULL, 'ряд страниц: предыдущая'),
  ('common.button.next_page', 'common', 'button', NULL, 'ряд страниц: следующая'),
  ('common.text.page_of', 'common', 'text', NULL,
   'ряд страниц: номер страницы, всего страниц и всего строк')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.sort.unknown_column',  'ru', 'Сортировка по полю «{column}» недоступна'),
    ('error.sort.unknown_column',  'en', 'Sorting by the field "{column}" is not available'),
    ('error.page.bad_number',      'ru', 'Неверный номер страницы'),
    ('error.page.bad_number',      'en', 'Invalid page number'),
    ('common.label.rows_per_page', 'ru', 'Строк на странице:'),
    ('common.label.rows_per_page', 'en', 'Rows per page:'),
    ('common.button.prev_page',    'ru', '‹ Назад'),
    ('common.button.prev_page',    'en', '‹ Prev'),
    ('common.button.next_page',    'ru', 'Вперёд ›'),
    ('common.button.next_page',    'en', 'Next ›'),
    ('common.text.page_of',        'ru', 'Стр. {page} из {pages} · всего {total}'),
    ('common.text.page_of',        'en', 'Page {page} of {pages} · {total} total')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

DO $check$
DECLARE
  nameless text;
BEGIN
  SELECT string_agg(k.text_key || ':' || l.code, ', ')
    INTO nameless
    FROM ui_text_key k
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('common.label.rows_per_page', 'common.button.prev_page',
                        'common.button.next_page', 'common.text.page_of',
                        'error.sort.unknown_column', 'error.page.bad_number')
     AND l.code IN ('ru', 'en')
     AND (t.value IS NULL OR t.value = '');
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'у подписей ряда страниц нет текста: %', nameless;
  END IF;
END
$check$;
