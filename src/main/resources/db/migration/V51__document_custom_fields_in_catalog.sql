-- =====================================================================
-- V51. Доп. поля шапки документа — в справочнике колонок
-- =====================================================================
-- На вкладке «Дополнительная информация» карточки прихода, заказа и
-- перемещения показываются десять доп. полей. В справочнике колонок
-- их не было: там заведены только доп. поля СТРОК документа
-- (receiptLineDetail, orderLineDetail, transferLineDetail) и партий
-- (stockBatch).
--
-- Из-за этого доп. поля шапки выпадали из двух механизмов сразу:
--
--   * переименование под клиента — хотя это ровно тот случай, ради
--     которого оно делалось: «Доп. поле 3» у клиента называется
--     «Номер нашей заявки»;
--   * видимость полей для поклажедателя — вкладка показывала все
--     десять, даже когда клиент пользуется двумя, и восемь пустых
--     строк висели на экране у всех.
--
-- field_id тот же, что у доп. полей строк: custom_field1…10. Это
-- не совпадение и не ошибка — так устроен справочник. Строка в нём
-- заводится на ПОЛЕ, а не на колонку: одно поле показывается
-- в нескольких таблицах, а название у него одно (см. комментарий
-- к table_column.field_id и функцию admin_field_labels). Поэтому
-- переименование «Доп. поля 1» у прихода подействует и в шапке,
-- и в строках — это одно и то же поле документа, и звать его
-- в двух местах по-разному было бы хуже.
--
-- Подписи по умолчанию оставляем как есть: «Доп. поле N». Осмысленное
-- название — дело клиента, портал не должен угадывать за него.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Ключи текстов
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT 'col.' || t.table_key || '.customField' || n, 'col', 'column', t.table_key,
       'доп. поле шапки документа; клиент переименовывает его под себя'
  FROM (VALUES ('receipts'), ('orders'), ('transfers')) AS t(table_key),
       generate_series(1, 10) AS n
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     owner = excluded.owner,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT 'col.' || t.table_key || '.customField' || n, l.locale_id,
       CASE l.code WHEN 'ru' THEN 'Доп. поле ' || n ELSE 'Custom field ' || n END
  FROM (VALUES ('receipts'), ('orders'), ('transfers')) AS t(table_key),
       generate_series(1, 10) AS n,
       dictionary_locale l
 WHERE l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- 2. Сами колонки
-- ---------------------------------------------------------------------
-- is_default = false: в списке документов доп. поля по умолчанию
-- не показываются — их десять, и у большинства клиентов они пусты.
-- Человек добавит нужные сам через окно ⚙ Колонки.
--
-- is_locked = false: скрыть их поклажедателю МОЖНО, в этом и смысл.
-- is_pinned = false: убрать из своей таблицы тоже можно.
INSERT INTO table_column (table_key, column_id, text_key, object_type_id,
                          field_id, sort_order, is_default, is_locked, is_pinned)
SELECT t.table_key,
       'customField' || n,
       'col.' || t.table_key || '.customField' || n,
       o.id,
       'custom_field' || n,
       (t.base + n)::smallint,
       false, false, false
  FROM (VALUES ('receipts', 40), ('orders', 40), ('transfers', 40))
         AS t(table_key, base)
  CROSS JOIN generate_series(1, 10) AS n
  JOIN enum_object_type o ON o.code = t.table_key
ON CONFLICT (table_key, column_id) DO NOTHING;

-- Порядок начат с 41: в самой длинной из трёх таблиц (приходы) занято
-- 33 места, запас оставлен намеренно — новые поля шапки появятся
-- раньше доп. полей, а не между ними.
