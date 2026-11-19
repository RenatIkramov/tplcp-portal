SET search_path = dbo;

-- V104. Роли «Разработчик» больше нет; из карточки документа убраны
-- выдуманные фотографии и услуги (09.11.2026).
--
-- 1. Роль «Разработчик» (developer). Её держал экран «Настройки
--    разработчика» с описанием схемы базы — база сделана, экран удалён.
--    Кроме экрана, роль давала вход в администрирование в режиме
--    «только просмотр», но этот режим держался только на экране: сервер
--    пускал разработчика и на запись. Учётки с этой ролью становятся
--    администраторами — вход не теряется, отбирать у них нечего.
-- 2. Тексты, на которые больше никто не ссылается: экран разработчика,
--    режим «только просмотр», демо-вход прототипа, загрузка фото,
--    которая держала файл только в памяти страницы, и другие подписи
--    удалённого кода прототипа.
-- 3. Новые тексты пустых вкладок «Фотографии» и «Услуги»: настоящие
--    данные придут из WMS и файлового хранилища.

-- ---------------------------------------------------------------------
-- 1. Роль «Разработчик»
-- ---------------------------------------------------------------------
UPDATE app_user
   SET role_id = (SELECT id FROM enum_user_role WHERE code = 'admin')
 WHERE role_id = (SELECT id FROM enum_user_role WHERE code = 'developer');

DELETE FROM enum_text
 WHERE enum_key = 'enum_user_role'
   AND value_id = (SELECT id::text FROM enum_user_role WHERE code = 'developer');

DELETE FROM enum_system_value WHERE enum_key = 'enum_user_role' AND code = 'developer';

DELETE FROM enum_user_role WHERE code = 'developer';

-- ---------------------------------------------------------------------
-- 2. Тексты удалённого кода
-- ---------------------------------------------------------------------
DELETE FROM ui_text WHERE text_key IN (
  'admin.text.changes_saved',
  'admin.text.show_the_transfers_section',
  'admin.text.view_mode_access',
  'admin.text.view_mode_users',
  'admin.title.depositor_field_settings',
  'auth.text.depositor_selected',
  'auth.text.developer_mode_document_and_field',
  'common.button.add_photo',
  'common.button.developer_settings',
  'common.message.account_is_blocked_please_cont',
  'common.message.check_the_numeric_fields_before_co',
  'common.message.employee_access_updated',
  'common.message.enter_your_login_and_password',
  'common.message.new_order_draft_created',
  'common.message.new_receipt_draft_created',
  'common.message.new_transfer_draft_created',
  'common.message.order',
  'common.message.transport_details_saved',
  'common.text.download_pdf',
  'common.text.from_your_computer',
  'common.text.most_photos_arrive_automatically_f',
  'dev.button.documents',
  'dev.button.enumerations',
  'dev.button.main_directories',
  'dev.button.registers',
  'dev.button.supporting_directories',
  'dev.button.views',
  'references.button.order_types',
  'stock.option.spare_parts'
);
DELETE FROM ui_text_key WHERE text_key IN (
  'admin.text.changes_saved',
  'admin.text.show_the_transfers_section',
  'admin.text.view_mode_access',
  'admin.text.view_mode_users',
  'admin.title.depositor_field_settings',
  'auth.text.depositor_selected',
  'auth.text.developer_mode_document_and_field',
  'common.button.add_photo',
  'common.button.developer_settings',
  'common.message.account_is_blocked_please_cont',
  'common.message.check_the_numeric_fields_before_co',
  'common.message.employee_access_updated',
  'common.message.enter_your_login_and_password',
  'common.message.new_order_draft_created',
  'common.message.new_receipt_draft_created',
  'common.message.new_transfer_draft_created',
  'common.message.order',
  'common.message.transport_details_saved',
  'common.text.download_pdf',
  'common.text.from_your_computer',
  'common.text.most_photos_arrive_automatically_f',
  'dev.button.documents',
  'dev.button.enumerations',
  'dev.button.main_directories',
  'dev.button.registers',
  'dev.button.supporting_directories',
  'dev.button.views',
  'references.button.order_types',
  'stock.option.spare_parts'
);

-- ---------------------------------------------------------------------
-- 3. Пустые вкладки карточки документа
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('common.text.no_document_photos', 'common', 'text', 'documents',
   'карточка документа, вкладка «Фотографии»: фотографий нет (V104)'),
  ('common.text.no_document_services', 'common', 'text', 'documents',
   'карточка документа, вкладка «Услуги»: услуг нет (V104)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.text.no_document_photos', 'ru', 'Фотографий по документу пока нет.'),
    ('common.text.no_document_photos', 'en', 'There are no photos for this document yet.'),
    ('common.text.no_document_services', 'ru', 'Услуг по документу пока нет.'),
    ('common.text.no_document_services', 'en', 'There are no services for this document yet.')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
