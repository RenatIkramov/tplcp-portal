-- =====================================================================
-- V68. Фильтры документов живут в справочнике колонок
-- =====================================================================
-- ЗАЧЕМ. Фильтры списков были зашиты в вёрстку: у поступлений шесть
-- полей, у заказов шесть, у перемещений четыре — ни статуса, ни
-- контрагента. Добавить фильтр значило дописать <div> в index.html
-- и продублировать разбор в двух файлах, не забыв третий экран.
--
-- При этом рядом уже есть справочник, который знает про каждую колонку
-- всё: подпись с переводом, переименование под поклажедателя, видимость,
-- выбор пользователя. Фильтры переезжают на него — и половина задач
-- решается сама:
--
--   * клиент переименовал «Рейс» в «Маршрут» — фильтр подписан так же;
--   * колонка запрещена поклажедателю — фильтра по ней нет (иначе
--     перебором значений можно было бы вытащить то, что спрятали);
--   * колонка добавлена миграцией — фильтр появляется вместе с ней.
--
-- ЧТО ЭТО ЧИНИТ ПОПУТНО. На экране заказов фильтр дат подписан «Дата
-- отгрузки», а отбирал по дате документа: на сервере был один
-- безымянный dateFrom/dateTo по doc_date. Когда фильтр — это колонка,
-- подпись и поведение разойтись не могут: «Дата отгрузки» фильтрует
-- ship_date, потому что это она и есть.
--
-- ПОЧЕМУ СВЯЗЬ С КОЛОНКОЙ БАЗЫ ЗАДАНА ЯВНО. Вывести её из column_id
-- нельзя: date → doc_date, ship → ship_date, plannedShip →
-- planned_ship_date. Поле field_id, на которое была надежда, оказалось
-- копией column_id. Поэтому filter_column заполняется руками — заодно
-- это и белый список для сборки SQL: фильтровать можно только по тому,
-- что здесь названо.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Признаки фильтра у колонки
-- ---------------------------------------------------------------------
ALTER TABLE table_column
  ADD COLUMN IF NOT EXISTS filter_kind    varchar(10),
  ADD COLUMN IF NOT EXISTS filter_column  varchar(40),
  ADD COLUMN IF NOT EXISTS filter_enum    varchar(40),
  ADD COLUMN IF NOT EXISTS filter_default boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN table_column.filter_kind IS
  'Вид фильтра: text, enum, date, number. Пусто — по колонке не фильтруют';
COMMENT ON COLUMN table_column.filter_column IS
  'Имя колонки в представлении — белый список для сборки условия';
COMMENT ON COLUMN table_column.filter_enum IS
  'Справочник значений для filter_kind = enum';
COMMENT ON COLUMN table_column.filter_default IS
  'Показывать фильтр без настройки';

-- Ограничения держат то, что иначе развалилось бы молча: фильтр без
-- колонки базы собрать не из чего, а enum без справочника нечем
-- наполнить.
ALTER TABLE table_column
  DROP CONSTRAINT IF EXISTS chk_table_column_filter_kind;
ALTER TABLE table_column
  ADD CONSTRAINT chk_table_column_filter_kind
  CHECK (filter_kind IS NULL OR filter_kind IN ('text', 'enum', 'date', 'number'));

ALTER TABLE table_column
  DROP CONSTRAINT IF EXISTS chk_table_column_filter_column;
ALTER TABLE table_column
  ADD CONSTRAINT chk_table_column_filter_column
  CHECK (filter_kind IS NULL OR filter_column IS NOT NULL);

ALTER TABLE table_column
  DROP CONSTRAINT IF EXISTS chk_table_column_filter_enum;
ALTER TABLE table_column
  ADD CONSTRAINT chk_table_column_filter_enum
  CHECK (filter_kind IS DISTINCT FROM 'enum' OR filter_enum IS NOT NULL);

-- ---------------------------------------------------------------------
-- 2. «Рейс» становится колонкой
-- ---------------------------------------------------------------------
-- По рейсу фильтруют на всех трёх экранах, а колонки такой в справочнике
-- не было вовсе: поле жило только в фильтре вёрстки. Если фильтры берутся
-- из справочника, «Рейс» из них исчез бы — поэтому заводим его как
-- обычную колонку, наравне с прочими транспортными.
--
-- is_default = false: у тех, кто уже настроил списки, ничего не съедет,
-- а показать «Рейс» в таблице теперь можно через ⚙ Колонки — раньше
-- было нельзя.
INSERT INTO table_column (table_key, column_id, text_key, object_type_id, field_id,
                          sort_order, is_default, is_locked, is_pinned, group_key)
SELECT v.table_key, 'trip', 'common.label.trip', v.object_type_id, 'trip',
       (SELECT max(sort_order) + 1 FROM table_column c WHERE c.table_key = v.table_key),
       false, false, false, 'common.title.transport_section'
  FROM (VALUES ('receipts', 1), ('orders', 2), ('transfers', 3))
         AS v(table_key, object_type_id)
ON CONFLICT (table_key, column_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Какие колонки фильтруются
-- ---------------------------------------------------------------------
-- Набор по умолчанию (filter_default) повторяет то, что было на экране,
-- с двумя поправками: у перемещений появляется статус — его там не было
-- вовсе, — а у заказов дата отгрузки наконец фильтрует дату отгрузки.
-- Остальное человек добавит себе сам, когда появится ⚙ Фильтры.
UPDATE table_column c
   SET filter_kind    = v.kind,
       filter_column  = v.db_column,
       filter_enum    = v.enum_source,
       filter_default = v.by_default
  FROM (VALUES
    -- общие для трёх экранов
    ('receipts',  'status',       'enum',   'status_code',        'enum_status', true ),
    ('orders',    'status',       'enum',   'status_code',        'enum_status', true ),
    ('transfers', 'status',       'enum',   'status_code',        'enum_status', true ),
    ('receipts',  'erpNo',        'text',   'erp_number',         NULL,          true ),
    ('orders',    'erpNo',        'text',   'erp_number',         NULL,          true ),
    ('transfers', 'erpNo',        'text',   'erp_number',         NULL,          true ),
    ('receipts',  'no',           'text',   'document_no',        NULL,          false),
    ('orders',    'no',           'text',   'document_no',        NULL,          false),
    ('transfers', 'no',           'text',   'document_no',        NULL,          false),
    ('receipts',  'wmsNumber',    'text',   'doc_number',         NULL,          false),
    ('orders',    'wmsNumber',    'text',   'doc_number',         NULL,          false),
    ('transfers', 'wmsNumber',    'text',   'doc_number',         NULL,          false),
    ('receipts',  'trip',         'text',   'trip',               NULL,          true ),
    ('orders',    'trip',         'text',   'trip',               NULL,          true ),
    ('transfers', 'trip',         'text',   'trip',               NULL,          true ),
    ('receipts',  'author',       'text',   'author_name',        NULL,          false),
    ('orders',    'author',       'text',   'author_name',        NULL,          false),
    ('transfers', 'author',       'text',   'author_name',        NULL,          false),
    ('receipts',  'comment',      'text',   'comment',            NULL,          false),
    ('orders',    'comment',      'text',   'comment',            NULL,          false),
    ('transfers', 'comment',      'text',   'comment',            NULL,          false),
    ('receipts',  'carNumber',    'text',   'car_number',         NULL,          false),
    ('orders',    'carNumber',    'text',   'car_number',         NULL,          false),
    ('transfers', 'carNumber',    'text',   'car_number',         NULL,          false),
    ('receipts',  'driverName',   'text',   'driver_name',        NULL,          false),
    ('orders',    'driverName',   'text',   'driver_name',        NULL,          false),
    ('transfers', 'driverName',   'text',   'driver_name',        NULL,          false),
    ('receipts',  'carrier',      'text',   'carrier',            NULL,          false),
    ('orders',    'carrier',      'text',   'carrier',            NULL,          false),
    ('transfers', 'carrier',      'text',   'carrier',            NULL,          false),
    ('receipts',  'lineCount',    'number', 'line_count',         NULL,          false),
    ('orders',    'lineCount',    'number', 'line_count',         NULL,          false),
    ('transfers', 'lineCount',    'number', 'line_count',         NULL,          false),
    ('receipts',  'weight',       'number', 'weight_kg',          NULL,          false),
    ('orders',    'weight',       'number', 'weight_kg',          NULL,          false),
    ('transfers', 'weight',       'number', 'weight_kg',          NULL,          false),
    ('receipts',  'palletCount',  'number', 'pallet_count',       NULL,          false),
    ('orders',    'palletCount',  'number', 'pallet_count',       NULL,          false),
    ('transfers', 'palletCount',  'number', 'pallet_count',       NULL,          false),
    ('receipts',  'date',         'date',   'doc_date',           NULL,          true ),
    ('orders',    'date',         'date',   'doc_date',           NULL,          false),
    ('transfers', 'date',         'date',   'doc_date',           NULL,          true ),

    -- поступления
    ('receipts',  'warehouse',    'enum',   'warehouse_id',       'warehouse',   false),
    ('receipts',  'supplier',     'text',   'supplier_name',      NULL,          true ),
    ('receipts',  'docType',      'enum',   'doc_type_id',        'enum_receipt_doc_type', false),
    ('receipts',  'eta',          'date',   'eta_date',           NULL,          false),
    ('receipts',  'arrivalDate',  'date',   'arrival_date',       NULL,          false),
    ('receipts',  'receivingEndDate', 'date', 'receiving_end_date', NULL,        false),

    -- заказы. Дата отгрузки по умолчанию: ровно это обещала подпись
    -- фильтра на экране, пока он отбирал по дате документа.
    ('orders',    'warehouse',    'enum',   'warehouse_id',       'warehouse',   false),
    ('orders',    'consignee',    'text',   'consignee_name',     NULL,          true ),
    ('orders',    'orderType',    'enum',   'order_type_id',      'enum_order_type', false),
    ('orders',    'ship',         'date',   'ship_date',          NULL,          true ),
    ('orders',    'plannedShip',  'date',   'planned_ship_date',  NULL,          false),
    ('orders',    'deliveryAddress', 'text', 'delivery_address',  NULL,          false),

    -- перемещения
    -- Перемещение определяется парой складов, и «всё, что ехало с Алматы»
    -- — первое, что захотят отобрать. На экране перемещений фильтра
    -- по складам не было вовсе, поэтому оба сразу по умолчанию.
    ('transfers', 'fromWarehouse', 'enum',  'from_warehouse_id',  'warehouse',   true ),
    ('transfers', 'toWarehouse',   'enum',  'to_warehouse_id',    'warehouse',   true ),
    ('transfers', 'shipDate',      'date',  'ship_date',          NULL,          false),
    ('transfers', 'receivedDate',  'date',  'received_date',      NULL,          false)
  ) AS v(table_key, column_id, kind, db_column, enum_source, by_default)
 WHERE c.table_key = v.table_key AND c.column_id = v.column_id;

-- Свободные поля поклажедателя — текстом. Клиент держит в них своё
-- (номер заявки, партию, что угодно) и по этому же своему ищет.
UPDATE table_column
   SET filter_kind   = 'text',
       filter_column = 'custom_field_' || substring(column_id from 12)
 WHERE table_key IN ('receipts', 'orders', 'transfers')
   AND column_id ~ '^customField([1-9]|10)$';

-- ---------------------------------------------------------------------
-- 4. Фильтры этого пользователя
-- ---------------------------------------------------------------------
-- Близнец table_columns: тот же порядок, та же подпись с переименованием
-- клиента, те же запреты поклажедателя — колонка, запрещённая клиенту,
-- не доходит сюда вовсе, потому что её не отдаёт table_columns.
--
-- is_visible намеренно НЕ учитывается: спрятать колонку из своей таблицы
-- и отказаться от отбора по ней — разные желания. Человек убирает
-- «Комментарий» из таблицы, чтобы она была уже, и продолжает искать
-- по комментарию.
CREATE OR REPLACE FUNCTION table_filters(p_user_id uuid,
                                         p_locale   text,
                                         p_table_key text DEFAULT NULL)
RETURNS TABLE (table_key      varchar,
               column_id      varchar,
               label          text,
               filter_kind    varchar,
               filter_column  varchar,
               filter_enum    varchar,
               object_type_id smallint,
               is_default     boolean,
               sort_order     smallint)
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  SELECT tc.table_key,
         tc.column_id,
         tc.label,
         c.filter_kind,
         c.filter_column,
         c.filter_enum,
         c.object_type_id,
         c.filter_default,
         tc.sort_order
    FROM table_columns(p_user_id, p_locale, p_table_key) tc
    JOIN table_column c
      ON c.table_key = tc.table_key AND c.column_id = tc.column_id
   WHERE c.filter_kind IS NOT NULL
   ORDER BY tc.table_key, tc.sort_order, tc.column_id;
$$;

GRANT EXECUTE ON FUNCTION table_filters(uuid, text, text) TO portal_app;

COMMENT ON FUNCTION table_filters(uuid, text, text) IS
  'Фильтры списка для пользователя: подпись, вид, колонка базы. Белый список для сборки условия';

-- ---------------------------------------------------------------------
-- 5. Тексты отказов
-- ---------------------------------------------------------------------
-- Отказ, а не молчаливый пропуск: если экран прислал фильтр, которого
-- нет в справочнике, человек должен узнать об этом сразу. Молча отдать
-- список, отобранный не так, как он просил, — худшее из возможного:
-- он поверит тому, что увидел.
--
-- Два последних отказа человек видеть не должен вовсе — это ошибки
-- в самом каталоге, заведённые миграцией. Текст им нужен на случай,
-- если всё-таки увидит: сырой ключ на экране хуже любой фразы.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.filter.unknown_column', 'server', 'message', 'documents',
   'фильтр по колонке, которой нет в справочнике или она скрыта от клиента'),
  ('error.filter.wrong_bound', 'server', 'message', 'documents',
   'диапазон задан там, где его не бывает, или наоборот'),
  ('error.filter.unknown_enum', 'server', 'message', 'documents',
   'в каталоге назван справочник, которого портал не знает'),
  ('error.filter.unknown_kind', 'server', 'message', 'documents',
   'в каталоге назван вид фильтра, которого портал не знает')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     owner   = excluded.owner,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('error.filter.unknown_column',
     'Фильтр по полю «{column}» недоступен',
     'Filtering by the field "{column}" is not available'),
    ('error.filter.wrong_bound',
     'Поле «{column}» так не фильтруется',
     'The field "{column}" cannot be filtered that way'),
    ('error.filter.unknown_enum',
     'Справочник значений «{enum}» порталу неизвестен',
     'The portal does not know the value list "{enum}"'),
    ('error.filter.unknown_kind',
     'Вид фильтра «{kind}» порталу неизвестен',
     'The portal does not know the filter kind "{kind}"')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 6. Проверка на месте
-- ---------------------------------------------------------------------
-- Опечатка в filter_column проявилась бы у человека на экране отказом
-- запроса. Ловим здесь: каждое имя обязано существовать в том
-- представлении, которому принадлежит таблица.
DO $check$
DECLARE
  missing text;
  n       integer;
BEGIN
  SELECT string_agg(c.table_key || '.' || c.column_id || ' → ' || c.filter_column, ', ')
    INTO missing
    FROM table_column c
    JOIN (VALUES ('receipts', 'v_receipts'),
                 ('orders',   'v_orders'),
                 ('transfers','v_transfers')) AS v(table_key, view_name)
      ON v.table_key = c.table_key
   WHERE c.filter_kind IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns ic
                      WHERE ic.table_schema = 'dbo'
                        AND ic.table_name   = v.view_name
                        AND ic.column_name  = c.filter_column);
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Фильтр ссылается на несуществующую колонку представления: %', missing;
  END IF;

  -- И обратная беда: ни одного фильтра по умолчанию на экране — человек
  -- увидел бы пустую панель и решил, что фильтры пропали.
  SELECT string_agg(t.table_key, ', ')
    INTO missing
    FROM (VALUES ('receipts'), ('orders'), ('transfers')) AS t(table_key)
   WHERE NOT EXISTS (SELECT 1 FROM table_column c
                      WHERE c.table_key = t.table_key AND c.filter_default);
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет фильтров по умолчанию: %', missing;
  END IF;

  SELECT count(*) INTO n FROM table_column WHERE filter_kind IS NOT NULL;
  RAISE NOTICE 'Фильтруемых колонок: %', n;
END $check$;
