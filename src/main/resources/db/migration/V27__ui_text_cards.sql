-- V27__ui_text_cards.sql
-- Надписи карточек документов, вкладок «Фотографии» и «Услуги», кнопок
-- справочника пользователей и подсказки на форме входа.
--
-- Отдельной версией, а не правкой V26: та миграция уже накачена, и менять
-- её нельзя — Flyway хранит отпечаток файла и при расхождении
-- останавливает приложение.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.title.general_information', 'common', 'title', NULL, 'карточка документа и справочники'),
  ('common.title.transport_section', 'common', 'title', NULL, 'карточка документа и справочники'),
  ('common.title.additional_information', 'common', 'title', NULL, 'карточка документа и справочники'),
  ('common.title.photos', 'common', 'title', NULL, 'карточка документа и справочники'),
  ('common.title.services', 'common', 'title', NULL, 'карточка документа и справочники'),
  ('common.label.document_id', 'common', 'label', NULL, 'карточка документа и справочники'),
  ('common.label.created_by', 'common', 'label', NULL, 'карточка документа и справочники'),
  ('common.label.contract_number', 'common', 'label', NULL, 'карточка документа и справочники'),
  ('orders.title.shipment_order', 'orders', 'title', NULL, 'карточка документа и справочники'),
  ('common.button.add', 'common', 'button', NULL, 'карточка документа и справочники'),
  ('common.label.custom_field', 'common', 'label', NULL, 'карточка документа и справочники'),
  ('common.button.add_photo', 'common', 'button', NULL, 'карточка документа и справочники'),
  ('common.text.from_your_computer', 'common', 'text', NULL, 'карточка документа и справочники'),
  ('common.text.most_photos_arrive_automatically_f', 'common', 'text', NULL, 'карточка документа и справочники'),
  ('common.column.markup', 'common', 'column', NULL, 'карточка документа и справочники'),
  ('common.column.amount_with_markup', 'common', 'column', NULL, 'карточка документа и справочники'),
  ('common.column.vat_12', 'common', 'column', NULL, 'карточка документа и справочники'),
  ('common.text.total', 'common', 'text', NULL, 'карточка документа и справочники'),
  ('admin.button.set_password', 'admin', 'button', NULL, 'карточка документа и справочники'),
  ('admin.placeholder.password_rule', 'admin', 'placeholder', NULL, 'карточка документа и справочники'),
  ('auth.placeholder.user_ahlers_com', 'auth', 'placeholder', NULL, 'карточка документа и справочники')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
  ('common.title.general_information', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Общая информация'),
  ('common.title.general_information', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'General information'),
  ('common.title.transport_section', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Транспортный раздел'),
  ('common.title.transport_section', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Transport section'),
  ('common.title.additional_information', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дополнительная информация'),
  ('common.title.additional_information', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Additional information'),
  ('common.title.photos', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Фотографии'),
  ('common.title.photos', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Photos'),
  ('common.title.services', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Услуги'),
  ('common.title.services', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Services'),
  ('common.label.document_id', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ID документа:'),
  ('common.label.document_id', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Document ID:'),
  ('common.label.created_by', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Автор:'),
  ('common.label.created_by', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Created by:'),
  ('common.label.contract_number', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Номер договора:'),
  ('common.label.contract_number', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Contract number:'),
  ('orders.title.shipment_order', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Заказ на отгрузку'),
  ('orders.title.shipment_order', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Shipment order'),
  ('common.button.add', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Добавить'),
  ('common.button.add', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Add'),
  ('common.label.custom_field', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Доп. поле'),
  ('common.label.custom_field', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Custom field'),
  ('common.button.add_photo', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Добавить фото'),
  ('common.button.add_photo', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Add photo'),
  ('common.text.from_your_computer', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'с компьютера'),
  ('common.text.from_your_computer', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'from your computer'),
  ('common.text.most_photos_arrive_automatically_f', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Большая часть фотографий поступает автоматически из WMS. Вы можете добавить свои.'),
  ('common.text.most_photos_arrive_automatically_f', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Most photos arrive automatically from WMS. You can add your own.'),
  ('common.column.markup', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наценка %'),
  ('common.column.markup', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Markup %'),
  ('common.column.amount_with_markup', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сумма с наценкой'),
  ('common.column.amount_with_markup', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Amount with markup'),
  ('common.column.vat_12', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'НДС (12%)'),
  ('common.column.vat_12', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'VAT (12%)'),
  ('common.text.total', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Итого'),
  ('common.text.total', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Total'),
  ('admin.button.set_password', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Задать пароль'),
  ('admin.button.set_password', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Set password'),
  ('admin.placeholder.password_rule', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'латинские буквы разного регистра + цифра или знак'),
  ('admin.placeholder.password_rule', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'lower and upper case latin letters + a digit or a symbol'),
  ('auth.placeholder.user_ahlers_com', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'user@ahlers.com'),
  ('auth.placeholder.user_ahlers_com', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'user@ahlers.com')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
