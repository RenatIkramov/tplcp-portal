-- V28: подписи, которые остались в карточках и фильтрах.
--
-- Сюда попало то, что в прототипе было написано примерами с названиями
-- вымышленной компании (подсказки «напр. …», ФИО и организация в карточке
-- пользователя) и длинные пояснения, собранные в коде из нескольких
-- кусков. Примеры заодно обезличены: ни страны, ни выдуманных фирм.
--
-- Правка значения существующего ключа (подсказка на вкладке «Фотографии»)
-- делается здесь, а не в V27: применённую миграцию менять нельзя.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
 ('common.placeholder.eg_receipt_no', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_receipt_wms_no', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_transfer_no', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_transfer_wms_no', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_order_no', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_order_wms_no', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_sku_code', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_sku_name', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_supplier', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.eg_consignee', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.attribute_name', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.placeholder.value_optional', 'common', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.hint.sort_by_this_field', 'common', 'hint', NULL, 'карточки, фильтры и подсказки'),
 ('auth.hint.manual_sign_in_disabled_in_demo', 'auth', 'hint', NULL, 'карточки, фильтры и подсказки'),
 ('admin.placeholder.full_name_example', 'admin', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('admin.placeholder.organization_example', 'admin', 'placeholder', NULL, 'карточки, фильтры и подсказки'),
 ('common.text.never', 'common', 'text', NULL, 'карточки, фильтры и подсказки'),
 ('admin.hint.depositors_of_a_user', 'admin', 'hint', NULL, 'карточки, фильтры и подсказки'),
 ('admin.hint.administrator_cannot_see_password', 'admin', 'hint', NULL, 'карточки, фильтры и подсказки'),
 ('admin.hint.new_user_password', 'admin', 'hint', NULL, 'карточки, фильтры и подсказки')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
 ('common.placeholder.eg_receipt_no', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. ПР-000123'),
 ('common.placeholder.eg_receipt_no', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. PR-000123'),
 ('common.placeholder.eg_receipt_wms_no', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. ПРБ-260701-01'),
 ('common.placeholder.eg_receipt_wms_no', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. PRB-260701-01'),
 ('common.placeholder.eg_transfer_no', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. ПМ-000001'),
 ('common.placeholder.eg_transfer_no', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. TR-000001'),
 ('common.placeholder.eg_transfer_wms_no', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. ПРМ-260701-01'),
 ('common.placeholder.eg_transfer_wms_no', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. TRM-260701-01'),
 ('common.placeholder.eg_order_no', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. ЗК-000045'),
 ('common.placeholder.eg_order_no', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. OR-000045'),
 ('common.placeholder.eg_order_wms_no', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. ОТГ-260701-01'),
 ('common.placeholder.eg_order_wms_no', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. SHP-260701-01'),
 ('common.placeholder.eg_sku_code', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. RK-1001'),
 ('common.placeholder.eg_sku_code', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. SKU-1001'),
 ('common.placeholder.eg_sku_name', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'напр. Рога'),
 ('common.placeholder.eg_sku_name', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. Antlers'),
 ('common.placeholder.eg_supplier', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ИП «Козлов»'),
 ('common.placeholder.eg_supplier', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. Acme Supplies'),
 ('common.placeholder.eg_consignee', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ИП «Барашкин»'),
 ('common.placeholder.eg_consignee', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'e.g. Acme Retail'),
 ('common.placeholder.attribute_name', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Название параметра'),
 ('common.placeholder.attribute_name', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Attribute name'),
 ('common.placeholder.value_optional', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Значение (необязательно)'),
 ('common.placeholder.value_optional', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Value (optional)'),
 ('common.hint.sort_by_this_field', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сортировать по этому полю'),
 ('common.hint.sort_by_this_field', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Sort by this field'),
 ('auth.hint.manual_sign_in_disabled_in_demo', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ручной вход отключён в демо-версии — выберите пользователя ниже'),
 ('auth.hint.manual_sign_in_disabled_in_demo', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Manual sign-in is disabled in the demo — pick a user below'),
 ('admin.placeholder.full_name_example', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Иванов Иван Иванович'),
 ('admin.placeholder.full_name_example', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'John Smith'),
 ('admin.placeholder.organization_example', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ТОО «Рога и Копыта»'),
 ('admin.placeholder.organization_example', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Acme Logistics'),
 ('common.text.never', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ни разу'),
 ('common.text.never', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'never'),
 ('admin.hint.depositors_of_a_user', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Поклажедателю обычно отмечают одного — свою организацию. Менеджеру — тех клиентов, с которыми он работает.'),
 ('admin.hint.depositors_of_a_user', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'A depositor is usually linked to one organisation — their own. A manager is linked to the depositors they work with.'),
 ('admin.hint.administrator_cannot_see_password', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Администратор не видит пароль пользователя. Можно выдать новый — портал потребует сменить его при первом входе.'),
 ('admin.hint.administrator_cannot_see_password', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'The administrator cannot see a user password. You may issue a new one — the portal will require changing it at the first sign-in.'),
 ('admin.hint.new_user_password', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Придумайте пароль или оставьте поле пустым — портал сгенерирует его сам и покажет.'),
 ('admin.hint.new_user_password', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Choose a password or leave the field empty — the portal will generate one and show it.'),
 ('common.text.most_photos_arrive_automatically_f', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Большая часть фотографий поступает автоматически из WMS. Вы можете дополнительно загрузить фото со своего компьютера — по умолчанию подписью станет имя файла, его можно изменить.'),
 ('common.text.most_photos_arrive_automatically_f', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Most photos arrive automatically from WMS. You may additionally upload photos from your computer — the file name becomes the caption by default and can be changed.')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
