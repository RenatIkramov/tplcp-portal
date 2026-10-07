-- V29: справочник колонок.
--
-- Закрывает три задачи, потому что все три — про одно и то же:
--   1) пользователь выбрал колонки — выбор должен запомниться;
--   2) администратор решает, какие колонки видит поклажедатель;
--   3) 204 подписи колонок перестают быть разбросанными по вёрстке.
--
-- Устройство повторяет ui_text_key + ui_text: отдельно каталог «что
-- существует», отдельно настройки. У определения колонки в коде есть
-- функция отрисовки — её в базу не положить, поэтому делим так:
-- в коде остаётся «как нарисовать», в базе — «как называется,
-- показывать ли, в каком порядке, кому разрешено».
--
-- Каталог засевается из того же tableColumns при сборке, вручную
-- не правится: двух источников правды быть не должно.
SET search_path = dbo;

-- Запасы — объект, у которого есть переименовываемые поля, но в перечне
-- типов объектов его не было: он не документ и не справочник.
-- next_enum_id() намеренно разрешён только для двух перечислений —
-- это защита, расширять её ради одной строки не нужно.
INSERT INTO enum_object_type (id, code, name, sort_order, is_active)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM enum_object_type),
       'stock', 'Запасы',
       (SELECT COALESCE(MAX(sort_order), 0) + 10 FROM enum_object_type), true
WHERE NOT EXISTS (SELECT 1 FROM enum_object_type WHERE code = 'stock');

-- =====================================================================
-- Каталог колонок
-- =====================================================================
CREATE TABLE IF NOT EXISTS table_column (
  table_key       varchar(40)  NOT NULL,
  column_id       varchar(40)  NOT NULL,
  text_key        varchar(80)  NOT NULL REFERENCES ui_text_key (text_key),
  object_type_id  smallint     REFERENCES enum_object_type (id),
  field_id        varchar(100),
  sort_order      smallint     NOT NULL,
  is_default      boolean      NOT NULL DEFAULT false,
  is_locked       boolean      NOT NULL DEFAULT false,
  PRIMARY KEY (table_key, column_id)
);

COMMENT ON TABLE  table_column IS 'Каталог колонок интерфейса: что существует';
COMMENT ON COLUMN table_column.table_key IS 'Таблица на экране: receipts, sku, receiptComposition, ...';
COMMENT ON COLUMN table_column.column_id IS 'Колонка внутри таблицы';
COMMENT ON COLUMN table_column.text_key IS 'Ключ подписи в каталоге текстов';
COMMENT ON COLUMN table_column.object_type_id IS 'Какого объекта поле показывает колонка';
COMMENT ON COLUMN table_column.field_id IS 'Какое поле показывает колонка — по нему работает переименование';
COMMENT ON COLUMN table_column.sort_order IS 'Порядок по умолчанию для портала';
COMMENT ON COLUMN table_column.is_default IS 'Показывать по умолчанию';
COMMENT ON COLUMN table_column.is_locked IS 'Спрятать нельзя';

-- Поле — то, у чего есть имя; колонка — место, где имя показывается.
-- Одно поле попадает в несколько таблиц (вес заказа и вес строки,
-- остаток в запасах и остаток по партии), и переименование клиентом
-- срабатывает во всех сразу. Индекс нужен именно для такого поиска.
CREATE INDEX IF NOT EXISTS table_column_field_ix
  ON table_column (object_type_id, field_id);

-- =====================================================================
-- Что видит поклажедатель. Строка есть только там, где настройка
-- отличается от портальной: пустая таблица = всё как у портала.
-- =====================================================================
CREATE TABLE IF NOT EXISTS depositor_table_column (
  depositor_id  uuid        NOT NULL REFERENCES depositor (depositor_id),
  table_key     varchar(40) NOT NULL,
  column_id     varchar(40) NOT NULL,
  is_available  boolean     NOT NULL DEFAULT true,
  is_default    boolean,
  sort_order    smallint,
  PRIMARY KEY (depositor_id, table_key, column_id),
  FOREIGN KEY (table_key, column_id)
      REFERENCES table_column (table_key, column_id) ON DELETE CASCADE
);

COMMENT ON COLUMN depositor_table_column.is_available IS
  'false — колонку этому клиенту не показывать вовсе, в «Колонки» она не попадёт';
COMMENT ON COLUMN depositor_table_column.is_default IS
  'Показывать по умолчанию; NULL — как у портала';

ALTER TABLE depositor_table_column ENABLE ROW LEVEL SECURITY;
ALTER TABLE depositor_table_column FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS depositor_isolation ON depositor_table_column;
-- Читать свои настройки можно, править — только администратору,
-- и он это делает через функции ниже (SECURITY DEFINER).
CREATE POLICY depositor_isolation ON depositor_table_column
  USING (depositor_id = current_depositor_id());

-- =====================================================================
-- Что выбрал пользователь. Строка есть только у видимых колонок:
-- наличие строки = колонка показывается.
--
-- Почему не флаг is_visible: иначе «спрятал колонку» и «не выбирал
-- ничего» становятся неразличимы, а это разные состояния. При таком
-- виде «сбросить к умолчанию» — это один DELETE.
-- =====================================================================
CREATE TABLE IF NOT EXISTS user_table_column (
  user_id     uuid        NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
  table_key   varchar(40) NOT NULL,
  column_id   varchar(40) NOT NULL,
  sort_order  smallint    NOT NULL,
  PRIMARY KEY (user_id, table_key, column_id),
  FOREIGN KEY (table_key, column_id)
      REFERENCES table_column (table_key, column_id) ON DELETE CASCADE
);

COMMENT ON TABLE user_table_column IS
  'Колонки, выбранные пользователем. Наличие строки означает, что колонка видна';

-- =====================================================================
-- Засев каталога: 204 колонок в 12 таблицах, выгружено из tableColumns.
-- Повторное применение обновляет признаки, а настройки пользователей
-- и поклажедателей не задевает.
-- =====================================================================
INSERT INTO table_column
  (table_key, column_id, text_key, object_type_id, field_id, sort_order, is_default, is_locked)
VALUES
 ('receiptComposition', 'code', 'col.receiptComposition.code',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'code', 1, true, false),
 ('receiptComposition', 'name', 'col.receiptComposition.name',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'name', 2, true, false),
 ('receiptComposition', 'unit', 'col.receiptComposition.unit',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'unit', 3, true, false),
 ('receiptComposition', 'expected', 'col.receiptComposition.expected',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'expected', 4, true, false),
 ('receiptComposition', 'expectedWms', 'col.receiptComposition.expectedWms',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'expectedWms', 5, true, false),
 ('receiptComposition', 'accepted', 'col.receiptComposition.accepted',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'accepted', 6, true, false),
 ('receiptComposition', 'defect', 'col.receiptComposition.defect',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'defect', 7, true, false),
 ('receiptComposition', 'price', 'col.receiptComposition.price',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'price', 8, true, false),
 ('receiptComposition', 'sum', 'col.receiptComposition.sum',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'sum', 9, true, false),
 ('receiptComposition', 'currency', 'col.receiptComposition.currency',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'currency', 10, true, false),
 ('receiptComposition', 'lineComment', 'col.receiptComposition.lineComment',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'lineComment', 11, true, false),
 ('orderComposition', 'code', 'col.orderComposition.code',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'code', 1, true, false),
 ('orderComposition', 'name', 'col.orderComposition.name',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'name', 2, true, false),
 ('orderComposition', 'unit', 'col.orderComposition.unit',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'unit', 3, true, false),
 ('orderComposition', 'ordered', 'col.orderComposition.ordered',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'ordered', 4, true, false),
 ('orderComposition', 'orderedWms', 'col.orderComposition.orderedWms',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'orderedWms', 5, true, false),
 ('orderComposition', 'picked', 'col.orderComposition.picked',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'picked', 6, true, false),
 ('orderComposition', 'weight', 'col.orderComposition.weight',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'weight', 7, true, false),
 ('orderComposition', 'price', 'col.orderComposition.price',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'price', 8, true, false),
 ('orderComposition', 'sum', 'col.orderComposition.sum',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'sum', 9, true, false),
 ('orderComposition', 'currency', 'col.orderComposition.currency',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'currency', 10, true, false),
 ('orderComposition', 'lineComment', 'col.orderComposition.lineComment',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'lineComment', 11, true, false),
 ('transferComposition', 'code', 'col.transferComposition.code',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'code', 1, true, false),
 ('transferComposition', 'name', 'col.transferComposition.name',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'name', 2, true, false),
 ('transferComposition', 'unit', 'col.transferComposition.unit',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'unit', 3, true, false),
 ('transferComposition', 'qty', 'col.transferComposition.qty',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'qty', 4, true, false),
 ('transferComposition', 'qtyWms', 'col.transferComposition.qtyWms',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'qtyWms', 5, true, false),
 ('transferComposition', 'lineComment', 'col.transferComposition.lineComment',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'lineComment', 6, true, false),
 ('orderLineDetail', 'q', 'col.orderLineDetail.q',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'q', 1, true, false),
 ('orderLineDetail', 'batch', 'col.orderLineDetail.batch',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'batch', 2, true, false),
 ('orderLineDetail', 'series', 'col.orderLineDetail.series',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'series', 3, true, false),
 ('orderLineDetail', 'exp', 'col.orderLineDetail.exp',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'exp', 4, true, false),
 ('orderLineDetail', 'stock', 'col.orderLineDetail.stock',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'stock', 5, true, false),
 ('receiptLineDetail', 'q', 'col.receiptLineDetail.q',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'q', 1, true, false),
 ('receiptLineDetail', 'batch', 'col.receiptLineDetail.batch',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'batch', 2, true, false),
 ('receiptLineDetail', 'series', 'col.receiptLineDetail.series',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'series', 3, true, false),
 ('receiptLineDetail', 'exp', 'col.receiptLineDetail.exp',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'exp', 4, true, false),
 ('receiptLineDetail', 'stock', 'col.receiptLineDetail.stock',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'stock', 5, true, false),
 ('transferLineDetail', 'q', 'col.transferLineDetail.q',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'q', 1, true, false),
 ('transferLineDetail', 'batch', 'col.transferLineDetail.batch',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'batch', 2, true, false),
 ('transferLineDetail', 'series', 'col.transferLineDetail.series',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'series', 3, true, false),
 ('transferLineDetail', 'exp', 'col.transferLineDetail.exp',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'exp', 4, true, false),
 ('transferLineDetail', 'stock', 'col.transferLineDetail.stock',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'stock', 5, true, false),
 ('stockBatch', 'batch', 'col.stockBatch.batch',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'batch', 1, true, false),
 ('stockBatch', 'series', 'col.stockBatch.series',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'series', 2, true, false),
 ('stockBatch', 'exp', 'col.stockBatch.exp',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'exp', 3, true, false),
 ('stockBatch', 'stock', 'col.stockBatch.stock',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'stock', 4, true, false),
 ('stockBatch', 'available', 'col.stockBatch.available',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'available', 5, true, false),
 ('stockBatch', 'reserved', 'col.stockBatch.reserved',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'reserved', 6, true, false),
 ('stockBatch', 'q', 'col.stockBatch.q',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'q', 7, true, false),
 ('stockBatch', 'blockReason', 'col.stockBatch.blockReason',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'blockReason', 8, true, false),
 ('stock', 'warehouse', 'col.stock.warehouse',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'warehouse', 1, true, false),
 ('stock', 'code', 'col.stock.code',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'code', 2, true, false),
 ('stock', 'name', 'col.stock.name',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'name', 3, true, false),
 ('stock', 'group', 'col.stock.group',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'group', 4, true, false),
 ('stock', 'unit', 'col.stock.unit',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'unit', 5, true, false),
 ('stock', 'stock', 'col.stock.stock',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'stock', 6, true, false),
 ('stock', 'available', 'col.stock.available',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'available', 7, true, false),
 ('stock', 'reserved', 'col.stock.reserved',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'reserved', 8, true, false),
 ('stock', 'blocked', 'col.stock.blocked',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'blocked', 9, true, false),
 ('stock', 'defect', 'col.stock.defect',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'defect', 10, true, false),
 ('stock', 'incoming', 'col.stock.incoming',
  (SELECT id FROM enum_object_type WHERE code = 'stock'), 'incoming', 11, true, false),
 ('sku', 'itemCode', 'col.sku.itemCode',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'itemCode', 1, true, false),
 ('sku', 'code', 'col.sku.code',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'code', 2, true, false),
 ('sku', 'name', 'col.sku.name',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'name', 3, true, false),
 ('sku', 'unit', 'col.sku.unit',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'unit', 4, true, false),
 ('sku', 'barcode', 'col.sku.barcode',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'barcode', 5, true, false),
 ('sku', 'group', 'col.sku.group',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'group', 6, true, false),
 ('sku', 'productType', 'col.sku.productType',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'productType', 7, true, false),
 ('sku', 'length', 'col.sku.length',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'length', 8, true, false),
 ('sku', 'width', 'col.sku.width',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'width', 9, true, false),
 ('sku', 'height', 'col.sku.height',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'height', 10, true, false),
 ('sku', 'volume', 'col.sku.volume',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'volume', 11, true, false),
 ('sku', 'weightGross', 'col.sku.weightGross',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'weightGross', 12, true, false),
 ('sku', 'manufacturer', 'col.sku.manufacturer',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'manufacturer', 13, true, false),
 ('sku', 'countryOrigin', 'col.sku.countryOrigin',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'countryOrigin', 14, true, false),
 ('sku', 'brand', 'col.sku.brand',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'brand', 15, true, false),
 ('sku', 'isFragile', 'col.sku.isFragile',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isFragile', 16, true, false),
 ('sku', 'isSerialTracked', 'col.sku.isSerialTracked',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isSerialTracked', 17, true, false),
 ('sku', 'batchTracking', 'col.sku.batchTracking',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'batchTracking', 18, true, false),
 ('sku', 'expiryTracking', 'col.sku.expiryTracking',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'expiryTracking', 19, true, false),
 ('sku', 'statusProduct', 'col.sku.statusProduct',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'statusProduct', 20, true, false),
 ('sku', 'itemFullName', 'col.sku.itemFullName',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'itemFullName', 21, false, false),
 ('sku', 'hazardClass', 'col.sku.hazardClass',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'hazardClass', 22, false, false),
 ('sku', 'hazardGroup', 'col.sku.hazardGroup',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'hazardGroup', 23, false, false),
 ('sku', 'weightNet', 'col.sku.weightNet',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'weightNet', 24, false, false),
 ('sku', 'maxStorageTemp', 'col.sku.maxStorageTemp',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'maxStorageTemp', 25, false, false),
 ('sku', 'minStorageTemp', 'col.sku.minStorageTemp',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'minStorageTemp', 26, false, false),
 ('sku', 'maxHumidity', 'col.sku.maxHumidity',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'maxHumidity', 27, false, false),
 ('sku', 'minHumidity', 'col.sku.minHumidity',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'minHumidity', 28, false, false),
 ('sku', 'minDaysForReceipt', 'col.sku.minDaysForReceipt',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'minDaysForReceipt', 29, false, false),
 ('sku', 'minDaysForReservation', 'col.sku.minDaysForReservation',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'minDaysForReservation', 30, false, false),
 ('sku', 'shelfLifeDays', 'col.sku.shelfLifeDays',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'shelfLifeDays', 31, false, false),
 ('sku', 'color', 'col.sku.color',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'color', 32, false, false),
 ('sku', 'size', 'col.sku.size',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'size', 33, false, false),
 ('sku', 'abcClass', 'col.sku.abcClass',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'abcClass', 34, false, false),
 ('sku', 'xyzClass', 'col.sku.xyzClass',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'xyzClass', 35, false, false),
 ('sku', 'hsCode', 'col.sku.hsCode',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'hsCode', 36, false, false),
 ('sku', 'enstruCode', 'col.sku.enstruCode',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'enstruCode', 37, false, false),
 ('sku', 'currency', 'col.sku.currency',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'currency', 38, false, false),
 ('sku', 'cost', 'col.sku.cost',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'cost', 39, false, false),
 ('sku', 'comment', 'col.sku.comment',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'comment', 40, false, false),
 ('sku', 'isKit', 'col.sku.isKit',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isKit', 41, false, false),
 ('sku', 'isMarkingRequired', 'col.sku.isMarkingRequired',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isMarkingRequired', 42, false, false),
 ('sku', 'isExcise', 'col.sku.isExcise',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isExcise', 43, false, false),
 ('sku', 'isControlled', 'col.sku.isControlled',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isControlled', 44, false, false),
 ('sku', 'isWmsActive', 'col.sku.isWmsActive',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'isWmsActive', 45, false, false),
 ('sku', 'createdBy', 'col.sku.createdBy',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'createdBy', 46, false, false),
 ('sku', 'createdAt', 'col.sku.createdAt',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'createdAt', 47, false, false),
 ('sku', 'updatedBy', 'col.sku.updatedBy',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'updatedBy', 48, false, false),
 ('sku', 'updatedAt', 'col.sku.updatedAt',
  (SELECT id FROM enum_object_type WHERE code = 'sku'), 'updatedAt', 49, false, false),
 ('receipts', 'status', 'col.receipts.status',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'status', 1, true, false),
 ('receipts', 'erpNo', 'col.receipts.erpNo',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'erpNo', 2, true, false),
 ('receipts', 'wmsNumber', 'col.receipts.wmsNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'wmsNumber', 3, true, false),
 ('receipts', 'warehouse', 'col.receipts.warehouse',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'warehouse', 4, true, false),
 ('receipts', 'date', 'col.receipts.date',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'date', 5, true, false),
 ('receipts', 'eta', 'col.receipts.eta',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'eta', 6, true, false),
 ('receipts', 'receivingEndDate', 'col.receipts.receivingEndDate',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'receivingEndDate', 7, true, false),
 ('receipts', 'supplier', 'col.receipts.supplier',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'supplier', 8, true, false),
 ('receipts', 'lineCount', 'col.receipts.lineCount',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'lineCount', 9, true, false),
 ('receipts', 'weight', 'col.receipts.weight',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'weight', 10, true, false),
 ('receipts', 'volume', 'col.receipts.volume',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'volume', 11, true, false),
 ('receipts', 'carNumber', 'col.receipts.carNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'carNumber', 12, true, false),
 ('receipts', 'driverName', 'col.receipts.driverName',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'driverName', 13, true, false),
 ('receipts', 'comment', 'col.receipts.comment',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'comment', 14, true, false),
 ('receipts', 'no', 'col.receipts.no',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'no', 15, false, false),
 ('receipts', 'author', 'col.receipts.author',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'author', 16, false, false),
 ('receipts', 'docType', 'col.receipts.docType',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'docType', 17, false, false),
 ('receipts', 'arrivalDate', 'col.receipts.arrivalDate',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'arrivalDate', 18, false, false),
 ('receipts', 'accompanyingDocNumber', 'col.receipts.accompanyingDocNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'accompanyingDocNumber', 19, false, false),
 ('receipts', 'gtd', 'col.receipts.gtd',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'gtd', 20, false, false),
 ('receipts', 'palletCount', 'col.receipts.palletCount',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'palletCount', 21, false, false),
 ('receipts', 'cargoPlaces', 'col.receipts.cargoPlaces',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'cargoPlaces', 22, false, false),
 ('receipts', 'totalPieces', 'col.receipts.totalPieces',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'totalPieces', 23, false, false),
 ('receipts', 'carrier', 'col.receipts.carrier',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'carrier', 24, false, false),
 ('receipts', 'transportType', 'col.receipts.transportType',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'transportType', 25, false, false),
 ('receipts', 'transportBrand', 'col.receipts.transportBrand',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'transportBrand', 26, false, false),
 ('receipts', 'trailerNumber', 'col.receipts.trailerNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'trailerNumber', 27, false, false),
 ('receipts', 'techPassportNumber', 'col.receipts.techPassportNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'techPassportNumber', 28, false, false),
 ('receipts', 'containerNumber', 'col.receipts.containerNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'containerNumber', 29, false, false),
 ('receipts', 'sealNumber', 'col.receipts.sealNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'sealNumber', 30, false, false),
 ('receipts', 'driverPhone', 'col.receipts.driverPhone',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'driverPhone', 31, false, false),
 ('receipts', 'driverIdNumber', 'col.receipts.driverIdNumber',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'driverIdNumber', 32, false, false),
 ('receipts', 'driverInn', 'col.receipts.driverInn',
  (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'driverInn', 33, false, false),
 ('orders', 'status', 'col.orders.status',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'status', 1, true, false),
 ('orders', 'erpNo', 'col.orders.erpNo',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'erpNo', 2, true, false),
 ('orders', 'warehouse', 'col.orders.warehouse',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'warehouse', 3, true, false),
 ('orders', 'date', 'col.orders.date',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'date', 4, true, false),
 ('orders', 'ship', 'col.orders.ship',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'ship', 5, true, false),
 ('orders', 'consignee', 'col.orders.consignee',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'consignee', 6, true, false),
 ('orders', 'lineCount', 'col.orders.lineCount',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'lineCount', 7, true, false),
 ('orders', 'weight', 'col.orders.weight',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'weight', 8, true, false),
 ('orders', 'volume', 'col.orders.volume',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'volume', 9, true, false),
 ('orders', 'carNumber', 'col.orders.carNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'carNumber', 10, true, false),
 ('orders', 'driverName', 'col.orders.driverName',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'driverName', 11, true, false),
 ('orders', 'comment', 'col.orders.comment',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'comment', 12, true, false),
 ('orders', 'deliveryAddress', 'col.orders.deliveryAddress',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'deliveryAddress', 13, true, false),
 ('orders', 'no', 'col.orders.no',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'no', 14, false, false),
 ('orders', 'wmsNumber', 'col.orders.wmsNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'wmsNumber', 15, false, false),
 ('orders', 'author', 'col.orders.author',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'author', 16, false, false),
 ('orders', 'orderType', 'col.orders.orderType',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'orderType', 17, false, false),
 ('orders', 'plannedShip', 'col.orders.plannedShip',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'plannedShip', 18, false, false),
 ('orders', 'palletCount', 'col.orders.palletCount',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'palletCount', 19, false, false),
 ('orders', 'cargoPlaces', 'col.orders.cargoPlaces',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'cargoPlaces', 20, false, false),
 ('orders', 'totalPieces', 'col.orders.totalPieces',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'totalPieces', 21, false, false),
 ('orders', 'carrier', 'col.orders.carrier',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'carrier', 22, false, false),
 ('orders', 'transportType', 'col.orders.transportType',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'transportType', 23, false, false),
 ('orders', 'transportBrand', 'col.orders.transportBrand',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'transportBrand', 24, false, false),
 ('orders', 'trailerNumber', 'col.orders.trailerNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'trailerNumber', 25, false, false),
 ('orders', 'techPassportNumber', 'col.orders.techPassportNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'techPassportNumber', 26, false, false),
 ('orders', 'containerNumber', 'col.orders.containerNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'containerNumber', 27, false, false),
 ('orders', 'sealNumber', 'col.orders.sealNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'sealNumber', 28, false, false),
 ('orders', 'driverPhone', 'col.orders.driverPhone',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'driverPhone', 29, false, false),
 ('orders', 'driverIdNumber', 'col.orders.driverIdNumber',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'driverIdNumber', 30, false, false),
 ('orders', 'driverInn', 'col.orders.driverInn',
  (SELECT id FROM enum_object_type WHERE code = 'orders'), 'driverInn', 31, false, false),
 ('transfers', 'status', 'col.transfers.status',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'status', 1, true, false),
 ('transfers', 'erpNo', 'col.transfers.erpNo',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'erpNo', 2, true, false),
 ('transfers', 'fromWarehouse', 'col.transfers.fromWarehouse',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'fromWarehouse', 3, true, false),
 ('transfers', 'toWarehouse', 'col.transfers.toWarehouse',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'toWarehouse', 4, true, false),
 ('transfers', 'date', 'col.transfers.date',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'date', 5, true, false),
 ('transfers', 'shipDate', 'col.transfers.shipDate',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'shipDate', 6, true, false),
 ('transfers', 'receivedDate', 'col.transfers.receivedDate',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'receivedDate', 7, true, false),
 ('transfers', 'lineCount', 'col.transfers.lineCount',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'lineCount', 8, true, false),
 ('transfers', 'weight', 'col.transfers.weight',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'weight', 9, true, false),
 ('transfers', 'volume', 'col.transfers.volume',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'volume', 10, true, false),
 ('transfers', 'carNumber', 'col.transfers.carNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'carNumber', 11, true, false),
 ('transfers', 'driverName', 'col.transfers.driverName',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'driverName', 12, true, false),
 ('transfers', 'comment', 'col.transfers.comment',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'comment', 13, true, false),
 ('transfers', 'no', 'col.transfers.no',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'no', 14, false, false),
 ('transfers', 'wmsNumber', 'col.transfers.wmsNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'wmsNumber', 15, false, false),
 ('transfers', 'author', 'col.transfers.author',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'author', 16, false, false),
 ('transfers', 'palletCount', 'col.transfers.palletCount',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'palletCount', 17, false, false),
 ('transfers', 'cargoPlaces', 'col.transfers.cargoPlaces',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'cargoPlaces', 18, false, false),
 ('transfers', 'totalPieces', 'col.transfers.totalPieces',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'totalPieces', 19, false, false),
 ('transfers', 'carrier', 'col.transfers.carrier',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'carrier', 20, false, false),
 ('transfers', 'transportType', 'col.transfers.transportType',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'transportType', 21, false, false),
 ('transfers', 'transportBrand', 'col.transfers.transportBrand',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'transportBrand', 22, false, false),
 ('transfers', 'trailerNumber', 'col.transfers.trailerNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'trailerNumber', 23, false, false),
 ('transfers', 'techPassportNumber', 'col.transfers.techPassportNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'techPassportNumber', 24, false, false),
 ('transfers', 'containerNumber', 'col.transfers.containerNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'containerNumber', 25, false, false),
 ('transfers', 'sealNumber', 'col.transfers.sealNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'sealNumber', 26, false, false),
 ('transfers', 'driverPhone', 'col.transfers.driverPhone',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'driverPhone', 27, false, false),
 ('transfers', 'driverIdNumber', 'col.transfers.driverIdNumber',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'driverIdNumber', 28, false, false),
 ('transfers', 'driverInn', 'col.transfers.driverInn',
  (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'driverInn', 29, false, false)
ON CONFLICT (table_key, column_id) DO UPDATE SET
  text_key       = excluded.text_key,
  object_type_id = excluded.object_type_id,
  field_id       = excluded.field_id,
  sort_order     = excluded.sort_order,
  is_default     = excluded.is_default;

-- =====================================================================
-- Чтение: готовая раскладка для пользователя
-- =====================================================================

-- Поклажедатель берётся из сеанса, а не аргументом: иначе он мог бы
-- разойтись с тем, по которому работает RLS, и человек увидел бы
-- подписи одного клиента рядом с данными другого.
DROP FUNCTION IF EXISTS table_columns(uuid, text, text);
CREATE FUNCTION table_columns(p_user_id uuid, p_locale text,
                              p_table_key text DEFAULT NULL)
RETURNS TABLE (
  table_key   varchar(40),
  column_id   varchar(40),
  label       text,
  is_visible  boolean,
  sort_order  smallint,
  is_locked   boolean
)
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  WITH cat AS (
    SELECT c.* FROM table_column c
    WHERE p_table_key IS NULL OR c.table_key = p_table_key
  ),
  dep AS (
    SELECT * FROM depositor_table_column
    WHERE depositor_id = current_depositor_id()
  ),
  chosen AS (
    SELECT * FROM user_table_column WHERE user_id = p_user_id
  ),
  -- таблицы, по которым пользователь уже делал выбор: в них отсутствие
  -- строки означает «спрятано», а не «не касался»
  touched AS (
    SELECT DISTINCT user_table_column.table_key FROM user_table_column
    WHERE user_id = p_user_id
  ),
  texts AS (
    SELECT t.text_key, t.value FROM ui_texts(p_locale) t
  )
  SELECT
    c.table_key,
    c.column_id,
    -- Переименование клиентом сильнее перевода: клиент назвал поле
    -- по-своему, и это важнее языка портала.
    COALESCE(lbl.custom_label, t.value, c.text_key)::text AS label,
    CASE
      WHEN c.is_locked                THEN true
      WHEN ch.user_id IS NOT NULL     THEN true
      WHEN c.table_key IN (SELECT table_key FROM touched) THEN false
      ELSE COALESCE(d.is_default, c.is_default)
    END AS is_visible,
    COALESCE(ch.sort_order, d.sort_order, c.sort_order)::smallint AS sort_order,
    c.is_locked
  FROM cat c
  LEFT JOIN dep d
         ON d.table_key = c.table_key AND d.column_id = c.column_id
  LEFT JOIN chosen ch
         ON ch.table_key = c.table_key AND ch.column_id = c.column_id
  LEFT JOIN depositor_field_label lbl
         ON lbl.depositor_id   = current_depositor_id()
        AND lbl.object_type_id = c.object_type_id
        AND lbl.field_id       = c.field_id
  LEFT JOIN texts t ON t.text_key = c.text_key
  -- запрещённые клиенту не отдаём вовсе: пользователь не должен
  -- увидеть их даже в списке выбора колонок
  WHERE COALESCE(d.is_available, true)
  ORDER BY c.table_key, 5, c.column_id;
$$;

COMMENT ON FUNCTION table_columns(uuid, text, text) IS
  'Готовая раскладка таблиц для пользователя: подпись, видимость, порядок';

-- =====================================================================
-- Запись: выбор пользователя
-- =====================================================================

-- Порядок колонок = порядок элементов массива. Прежний выбор по этой
-- таблице стирается целиком: раскладка — это одна настройка, а не
-- набор независимых галочек.
DROP FUNCTION IF EXISTS user_columns_save(uuid, text, text[]);
CREATE FUNCTION user_columns_save(p_user_id uuid, p_table_key text,
                                  p_columns text[])
RETURNS void
LANGUAGE sql
SET search_path = dbo
AS $$
  WITH wipe AS (
    DELETE FROM user_table_column
    WHERE user_id = p_user_id AND table_key = p_table_key
    RETURNING 1
  )
  INSERT INTO user_table_column (user_id, table_key, column_id, sort_order)
  SELECT p_user_id, p_table_key, x.column_id, x.ord::smallint
  FROM unnest(p_columns) WITH ORDINALITY AS x(column_id, ord)
  WHERE EXISTS (SELECT 1 FROM wipe) OR true;
$$;

DROP FUNCTION IF EXISTS user_columns_reset(uuid, text);
CREATE FUNCTION user_columns_reset(p_user_id uuid, p_table_key text)
RETURNS void
LANGUAGE sql
SET search_path = dbo
AS $$
  DELETE FROM user_table_column
  WHERE user_id = p_user_id
    AND (p_table_key IS NULL OR table_key = p_table_key);
$$;

COMMENT ON FUNCTION user_columns_reset(uuid, text) IS
  'Сброс выбора колонок: без таблицы — по всем таблицам сразу';

-- =====================================================================
-- Копирование настроек у другого пользователя
--
-- Переносится то, что относится к удобству работы: видимость пунктов
-- меню, разрешения на создание документов и загрузку Excel, выбранные
-- колонки. Роль и доступы (к поклажедателям и складам) НЕ переносятся:
-- доступ — это «кто видит чьи данные», и тихое копирование одной
-- кнопкой — ровно тот случай, когда человек случайно получает доступ
-- к чужому клиенту.
-- =====================================================================
DROP FUNCTION IF EXISTS user_settings_copy(uuid, uuid);
CREATE FUNCTION user_settings_copy(p_from uuid, p_to uuid)
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  IF p_from = p_to THEN
    RETURN;
  END IF;

  INSERT INTO user_settings AS s (user_id, hide_transfers_tab, hide_billing_tab,
      hide_services_tab, can_create_receipts, can_create_orders,
      can_create_transfers, can_upload_sku_excel, can_upload_packaging_excel,
      can_edit_transport)
  SELECT p_to, f.hide_transfers_tab, f.hide_billing_tab, f.hide_services_tab,
         f.can_create_receipts, f.can_create_orders, f.can_create_transfers,
         f.can_upload_sku_excel, f.can_upload_packaging_excel, f.can_edit_transport
  FROM user_settings f
  WHERE f.user_id = p_from
  ON CONFLICT (user_id) DO UPDATE SET
    hide_transfers_tab         = excluded.hide_transfers_tab,
    hide_billing_tab           = excluded.hide_billing_tab,
    hide_services_tab          = excluded.hide_services_tab,
    can_create_receipts        = excluded.can_create_receipts,
    can_create_orders          = excluded.can_create_orders,
    can_create_transfers       = excluded.can_create_transfers,
    can_upload_sku_excel       = excluded.can_upload_sku_excel,
    can_upload_packaging_excel = excluded.can_upload_packaging_excel,
    can_edit_transport         = excluded.can_edit_transport;

  -- Колонки переносятся целиком: и выбор, и порядок. Прежний выбор
  -- получателя стирается, иначе получилась бы смесь двух раскладок.
  DELETE FROM user_table_column WHERE user_id = p_to;
  INSERT INTO user_table_column (user_id, table_key, column_id, sort_order)
  SELECT p_to, table_key, column_id, sort_order
  FROM user_table_column WHERE user_id = p_from;
END;
$$;

-- =====================================================================
-- Администрирование: настройки колонок поклажедателя
--
-- SECURITY DEFINER, потому что администратор портала работает с чужим
-- поклажедателем, а RLS пускает только к своему. Право проверяется
-- в приложении до вызова.
-- =====================================================================
DROP FUNCTION IF EXISTS admin_depositor_columns(uuid, text, text);
CREATE FUNCTION admin_depositor_columns(p_depositor_id uuid, p_locale text,
                                        p_table_key text DEFAULT NULL)
RETURNS TABLE (
  table_key     varchar(40),
  column_id     varchar(40),
  label         text,
  portal_label  text,
  is_available  boolean,
  is_default    boolean,
  sort_order    smallint
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo
AS $$
  WITH texts AS (SELECT t.text_key, t.value FROM ui_texts(p_locale) t)
  SELECT
    c.table_key,
    c.column_id,
    COALESCE(lbl.custom_label, t.value, c.text_key)::text AS label,
    COALESCE(t.value, c.text_key)::text                   AS portal_label,
    COALESCE(d.is_available, true)                        AS is_available,
    COALESCE(d.is_default, c.is_default)                  AS is_default,
    COALESCE(d.sort_order, c.sort_order)::smallint        AS sort_order
  FROM table_column c
  LEFT JOIN depositor_table_column d
         ON d.depositor_id = p_depositor_id
        AND d.table_key = c.table_key AND d.column_id = c.column_id
  LEFT JOIN depositor_field_label lbl
         ON lbl.depositor_id   = p_depositor_id
        AND lbl.object_type_id = c.object_type_id
        AND lbl.field_id       = c.field_id
  LEFT JOIN texts t ON t.text_key = c.text_key
  WHERE p_table_key IS NULL OR c.table_key = p_table_key
  ORDER BY c.table_key, 7, c.column_id;
$$;

-- Строка заводится только при отличии от портальной настройки, и
-- убирается, когда отличие исчезло: так в таблице видно ровно то,
-- что администратор менял руками.
DROP FUNCTION IF EXISTS admin_depositor_column_save(uuid, text, text, boolean, boolean);
CREATE FUNCTION admin_depositor_column_save(p_depositor_id uuid, p_table_key text,
                                            p_column_id text, p_is_available boolean,
                                            p_is_default boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  v_portal_default boolean;
BEGIN
  SELECT is_default INTO v_portal_default
  FROM table_column
  WHERE table_key = p_table_key AND column_id = p_column_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Колонки % в таблице % нет', p_column_id, p_table_key;
  END IF;

  IF p_is_available AND p_is_default IS NOT DISTINCT FROM v_portal_default THEN
    DELETE FROM depositor_table_column
    WHERE depositor_id = p_depositor_id
      AND table_key = p_table_key AND column_id = p_column_id;
    RETURN;
  END IF;

  INSERT INTO depositor_table_column
    (depositor_id, table_key, column_id, is_available, is_default)
  VALUES (p_depositor_id, p_table_key, p_column_id, p_is_available, p_is_default)
  ON CONFLICT (depositor_id, table_key, column_id) DO UPDATE SET
    is_available = excluded.is_available,
    is_default   = excluded.is_default;
END;
$$;

-- Перечень таблиц для экрана настройки: человеку нужен список разделов,
-- а не 204 строки подряд.
DROP VIEW IF EXISTS v_column_tables;
CREATE VIEW v_column_tables AS
SELECT
  c.table_key,
  o.code                              AS object_code,
  o.name                              AS object_name,
  count(*)::int                       AS column_count,
  count(*) FILTER (WHERE c.is_default)::int AS default_count
FROM table_column c
LEFT JOIN enum_object_type o ON o.id = c.object_type_id
GROUP BY c.table_key, o.code, o.name
ORDER BY o.code NULLS LAST, c.table_key;
