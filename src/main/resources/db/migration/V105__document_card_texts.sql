SET search_path = dbo;

-- V105. Тексты карточки документа и окна ввода строки — на ключи.
--
-- Карточку поступления, заказа и перемещения перенесли из прототипа
-- в модули doc-card.js и line-modal.js (10.11.2026). При переносе
-- нашлись надписи, записанные прямо в коде по-русски: на английском
-- интерфейсе они оставались русскими. Здесь — их ключи и тексты.

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('common.title.add_line', 'common', 'title', 'documents',
   'окно ввода строки документа: заголовок при добавлении (V105)'),
  ('common.message.line_added', 'common', 'message', 'documents',
   'окно ввода строки: строка добавлена в состав (V105)'),
  ('common.message.line_updated', 'common', 'message', 'documents',
   'окно ввода строки: строка состава изменена (V105)'),
  ('common.message.lines_deleted', 'common', 'message', 'documents',
   'состав документа: удалены отмеченные строки, {count} — сколько (V105)'),
  ('common.option.no_packaging', 'common', 'option', 'documents',
   'окно ввода строки: количество без упаковки (V105)'),
  ('common.label.unit_of_measure', 'common', 'label', 'documents',
   'окно ввода строки: единица измерения товара (V105)'),
  ('common.label.stock_total_at_warehouse', 'common', 'label', 'documents',
   'окно ввода строки: общий остаток товара на складе документа (V105)'),
  ('common.label.stock_available_at_warehouse', 'common', 'label', 'documents',
   'окно ввода строки: доступный остаток товара на складе документа (V105)'),
  ('common.text.confirm_delete_draft', 'common', 'text', 'documents',
   'вопрос перед удалением черновика документа (V105)'),
  ('common.button.save_transport', 'common', 'button', 'documents',
   'карточка документа, вкладка «Транспортный раздел» после отправки (V105)'),
  ('common.text.item_already_in_document', 'common', 'text', 'documents',
   'окно «Товар уже добавлен»: {name} — товар, {qty} — количество в документе (V105)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.title.add_line', 'ru', 'Добавление строки'),
    ('common.title.add_line', 'en', 'Add line'),
    ('common.message.line_added', 'ru', 'Строка добавлена'),
    ('common.message.line_added', 'en', 'Line added'),
    ('common.message.line_updated', 'ru', 'Строка обновлена'),
    ('common.message.line_updated', 'en', 'Line updated'),
    ('common.message.lines_deleted', 'ru', 'Удалено строк: {count}'),
    ('common.message.lines_deleted', 'en', 'Lines deleted: {count}'),
    ('common.option.no_packaging', 'ru', 'Без упаковки'),
    ('common.option.no_packaging', 'en', 'No packaging'),
    ('common.label.unit_of_measure', 'ru', 'Единица измерения'),
    ('common.label.unit_of_measure', 'en', 'Unit of measure'),
    ('common.label.stock_total_at_warehouse', 'ru', 'Общий остаток на складе'),
    ('common.label.stock_total_at_warehouse', 'en', 'Total stock at the warehouse'),
    ('common.label.stock_available_at_warehouse', 'ru', 'Доступный остаток на складе'),
    ('common.label.stock_available_at_warehouse', 'en', 'Available stock at the warehouse'),
    ('common.text.confirm_delete_draft', 'ru', 'Вы уверены, что хотите удалить черновик?'),
    ('common.text.confirm_delete_draft', 'en', 'Are you sure you want to delete the draft?'),
    ('common.button.save_transport', 'ru', 'Сохранить транспортную информацию'),
    ('common.button.save_transport', 'en', 'Save transport details'),
    ('common.text.item_already_in_document', 'ru',
     'Товар «{name}» уже есть в документе в количестве {qty}'),
    ('common.text.item_already_in_document', 'en',
     'The item "{name}" is already in the document, quantity {qty}')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
