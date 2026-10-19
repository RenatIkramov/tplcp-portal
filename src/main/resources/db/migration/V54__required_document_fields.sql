-- =====================================================================
-- V54. Обязательные поля документов и запасов
-- =====================================================================
-- is_locked значит «поклажедателю скрыть нельзя» — поле остаётся
-- в списке и в карточке у любого клиента. До сих пор признак стоял
-- только у товара (V36): код, наименование, единица, служебная
-- информация, группа.
--
-- Заказчик прошёл по карточкам документов и назвал поля, без которых
-- документ бесполезен: по чему его найти (номер, дата, статус), с кем
-- он (контрагент, склад), и чем закрыт (принято, собрано, количество).
-- Их и закрепляем.
--
-- Заодно две правки наименований:
--   * «Код» в запасах → «Коммерческий код», как в товарах и в составах
--     документов. Поле одно и то же, и два названия на одно поле —
--     ровно та путаница, которую портал должен убирать;
--   * вкладка «Партии» в запасах → «Детали», как «Детали строки»
--     у документов: на ней не только партии, но и серии, сроки,
--     качество и причина блокировки.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Обязательные поля
-- ---------------------------------------------------------------------
UPDATE table_column SET is_locked = true
 WHERE (table_key, column_id) IN (VALUES
   -- Поступление, общая информация
   ('receipts', 'status'), ('receipts', 'erpNo'), ('receipts', 'wmsNumber'),
   ('receipts', 'date'), ('receipts', 'eta'), ('receipts', 'receivingEndDate'),
   ('receipts', 'docType'), ('receipts', 'lineCount'), ('receipts', 'totalPieces'),
   ('receipts', 'arrivalDate'), ('receipts', 'no'), ('receipts', 'author'),
   -- Поступление, состав
   ('receiptComposition', 'code'), ('receiptComposition', 'name'),
   ('receiptComposition', 'unit'), ('receiptComposition', 'expected'),
   ('receiptComposition', 'expectedWms'), ('receiptComposition', 'accepted'),
   ('receiptComposition', 'defect'),
   -- Поступление, детали строки
   ('receiptLineDetail', 'q'), ('receiptLineDetail', 'exp'),
   ('receiptLineDetail', 'stock'),

   -- Заказ, общая информация. Тип заказа, планируемая дата отгрузки
   -- и количество штук названы заказчиком в составе, но живут в шапке:
   -- в строке заказа таких полей нет. Закрепляем там, где они есть.
   ('orders', 'status'), ('orders', 'erpNo'), ('orders', 'date'),
   ('orders', 'ship'), ('orders', 'consignee'), ('orders', 'lineCount'),
   ('orders', 'deliveryAddress'), ('orders', 'no'), ('orders', 'wmsNumber'),
   ('orders', 'author'), ('orders', 'orderType'), ('orders', 'plannedShip'),
   ('orders', 'totalPieces'),
   -- Заказ, состав
   ('orderComposition', 'code'), ('orderComposition', 'name'),
   ('orderComposition', 'unit'), ('orderComposition', 'ordered'),
   ('orderComposition', 'orderedWms'), ('orderComposition', 'picked'),
   -- Заказ, детали строки
   ('orderLineDetail', 'q'), ('orderLineDetail', 'exp'),
   ('orderLineDetail', 'stock'),

   -- Перемещение, общая информация
   ('transfers', 'status'), ('transfers', 'erpNo'), ('transfers', 'fromWarehouse'),
   ('transfers', 'toWarehouse'), ('transfers', 'date'), ('transfers', 'shipDate'),
   ('transfers', 'receivedDate'), ('transfers', 'lineCount'), ('transfers', 'no'),
   ('transfers', 'wmsNumber'), ('transfers', 'author'), ('transfers', 'totalPieces'),
   -- Перемещение, состав
   ('transferComposition', 'code'), ('transferComposition', 'name'),
   ('transferComposition', 'unit'), ('transferComposition', 'qty'),
   ('transferComposition', 'qtyWms'),
   -- Перемещение, детали строки
   ('transferLineDetail', 'q'), ('transferLineDetail', 'exp'),
   ('transferLineDetail', 'stock')
 );

-- Поле стало обязательным — значит прежние запреты на него недействительны.
-- Строку в depositor_table_column держать нельзя: экран её больше не
-- снимет (сервер откажет с 409), а поле так и останется скрытым.
-- Таблица под FORCE ROW LEVEL SECURITY, поэтому чистим функцией
-- с SECURITY DEFINER — у миграции контекста поклажедателя нет.
CREATE OR REPLACE FUNCTION admin_unlock_locked_columns()
RETURNS int
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo
AS $$
  WITH gone AS (
    DELETE FROM depositor_table_column d
     USING table_column c
     WHERE c.table_key = d.table_key AND c.column_id = d.column_id
       AND c.is_locked AND d.is_available = false
    RETURNING 1
  )
  SELECT count(*)::int FROM gone;
$$;

COMMENT ON FUNCTION admin_unlock_locked_columns() IS
  'Снимает запреты поклажедателей на поля, ставшие обязательными';

SELECT admin_unlock_locked_columns();

-- ---------------------------------------------------------------------
-- 2. «Код» в запасах — это коммерческий код
-- ---------------------------------------------------------------------
UPDATE ui_text SET value = 'Коммерческий код'
 WHERE text_key = 'col.stock.code'
   AND locale_id = (SELECT locale_id FROM dictionary_locale WHERE code = 'ru');
UPDATE ui_text SET value = 'Commercial code'
 WHERE text_key = 'col.stock.code'
   AND locale_id = (SELECT locale_id FROM dictionary_locale WHERE code = 'en');

-- ---------------------------------------------------------------------
-- 3. Вкладка «Партии» в запасах → «Детали»
-- ---------------------------------------------------------------------
-- Ключ заводим новый, а не правим текст старого: имя ключа читают
-- в коде и в экране переводов, и admin.table.batches со значением
-- «Детали» сбивало бы с толку ровно так же, как сама вкладка.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.table.details', 'admin', 'text', NULL,
   'вкладка запасов: партии, серии, сроки, качество — не только партии')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT 'admin.table.details', l.locale_id,
       CASE l.code WHEN 'ru' THEN 'Детали' ELSE 'Details' END
  FROM dictionary_locale l
 WHERE l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

UPDATE table_column SET group_key = 'admin.table.details'
 WHERE table_key = 'stockBatch';

-- Старый ключ больше нигде не используется. Не удаляем: на ui_text_key
-- ссылается ui_text, а чужие переводы, если их успели завести,
-- удалять молча нельзя. Текст его правим, чтобы в экране переводов
-- он не выглядел действующей подписью.
UPDATE ui_text SET value = 'Детали'
 WHERE text_key = 'admin.table.batches'
   AND locale_id = (SELECT locale_id FROM dictionary_locale WHERE code = 'ru');
UPDATE ui_text SET value = 'Details'
 WHERE text_key = 'admin.table.batches'
   AND locale_id = (SELECT locale_id FROM dictionary_locale WHERE code = 'en');
