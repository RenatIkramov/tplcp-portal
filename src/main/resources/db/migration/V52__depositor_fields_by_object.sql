-- =====================================================================
-- V52. Видимость полей поклажедателя — по всем объектам, в одном месте
-- =====================================================================
-- Настройка «что клиент видит» жила в двух местах сразу:
--
--   * карточка доступов поклажедателя, вкладка «Поля товаров» —
--     удобная, разложена по вкладкам карточки товара, но ТОЛЬКО товар;
--   * карточка поклажедателя в справочниках, вкладка «Колонки» —
--     все двенадцать таблиц списком, зато без признака обязательности
--     и без разбивки по смыслу.
--
-- Оба экрана писали в одни и те же строки depositor_table_column,
-- то есть расходились бы рано или поздно. Решение заказчика: всё
-- про одного клиента — в его карточке в «Доступах поклажедателей»,
-- как уже сделано с переименованием полей. Вкладка в справочнике
-- убирается.
--
-- Тексты ниже — для новой раскладки: вкладка «Поля» вместо «Поля
-- товаров» и подписи таблиц внутри объекта. Подписи именно по роли
-- таблицы, а не по её коду: человеку важно «документ / состав /
-- детали строки», а receiptLineDetail ему ничего не говорит.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.tab.fields', 'admin', 'tab', NULL,
   'вкладка карточки поклажедателя: поля всех объектов, не только товара'),
  ('admin.table.document', 'admin', 'text', NULL,
   'шапка документа: список и карточка'),
  ('admin.table.composition', 'admin', 'text', NULL,
   'состав документа — строки'),
  ('admin.table.line_details', 'admin', 'text', NULL,
   'детали строки документа: партии, сроки годности'),
  ('admin.table.stock_lines', 'admin', 'text', NULL, 'остатки по товару'),
  ('admin.table.batches', 'admin', 'text', NULL, 'остатки в разрезе партий'),
  ('admin.hint.depositor_fields', 'admin', 'hint', NULL,
   'пояснение к вкладке полей в карточке поклажедателя'),
  ('admin.text.object_fields_count', 'admin', 'text', NULL,
   'сколько полей объекта закрыто клиенту — виден прямо на подвкладке')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('admin.tab.fields', 'ru', 'Поля'),
  ('admin.tab.fields', 'en', 'Fields'),
  ('admin.table.document', 'ru', 'Документ'),
  ('admin.table.document', 'en', 'Document'),
  ('admin.table.composition', 'ru', 'Состав документа'),
  ('admin.table.composition', 'en', 'Document lines'),
  ('admin.table.line_details', 'ru', 'Детали строки'),
  ('admin.table.line_details', 'en', 'Line details'),
  ('admin.table.stock_lines', 'ru', 'Остатки'),
  ('admin.table.stock_lines', 'en', 'Stock'),
  ('admin.table.batches', 'ru', 'Партии'),
  ('admin.table.batches', 'en', 'Batches'),
  ('admin.hint.depositor_fields', 'ru',
   'Снятая галочка «Доступно» убирает поле у этого клиента везде: '
   'из карточки, из окна выбора колонок и из шаблона Excel. '
   'Галочка «Показывать сразу» решает другое — попадёт ли поле '
   'в таблицу по умолчанию; человек всё равно сможет добавить его сам.'),
  ('admin.hint.depositor_fields', 'en',
   'Clearing "Available" removes the field for this depositor '
   'everywhere: from the card, from the column picker and from the '
   'Excel template. "Show by default" decides something else — whether '
   'the field is in the table out of the box; the user can still add it.'),
  ('admin.text.object_fields_count', 'ru', 'закрыто'),
  ('admin.text.object_fields_count', 'en', 'hidden')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
