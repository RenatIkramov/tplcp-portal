-- V30: подписи экранов справочника колонок.
--
-- Отдельной миграцией, а не внутри V29: V29 к этому моменту уже
-- применена, а применённую миграцию менять нельзя — Flyway сверяет
-- контрольную сумму.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
 ('references.title.columns', 'references', 'title', NULL, 'справочник колонок'),
 ('references.column.column', 'references', 'column', NULL, 'справочник колонок'),
 ('references.column.label_for_client', 'references', 'column', NULL, 'справочник колонок'),
 ('references.column.available', 'references', 'column', NULL, 'справочник колонок'),
 ('references.column.show_at_once', 'references', 'column', NULL, 'справочник колонок'),
 ('references.text.pick_a_section_on_the_left', 'references', 'text', NULL, 'справочник колонок'),
 ('references.text.instead_of', 'references', 'text', NULL, 'справочник колонок'),
 ('references.hint.unavailable_column_is_hidden', 'references', 'hint', NULL, 'справочник колонок'),
 ('admin.label.copy_settings_from_user', 'admin', 'label', NULL, 'справочник колонок'),
 ('admin.button.copy', 'admin', 'button', NULL, 'справочник колонок'),
 ('common.option.not_selected', 'common', 'option', NULL, 'справочник колонок'),
 ('admin.hint.what_copy_settings_transfers', 'admin', 'hint', NULL, 'справочник колонок'),
 ('admin.message.pick_whom_to_copy_from', 'admin', 'message', NULL, 'справочник колонок'),
 ('admin.message.settings_copied', 'admin', 'message', NULL, 'справочник колонок'),
 ('common.message.keep_at_least_one_column', 'common', 'message', NULL, 'справочник колонок'),
 ('common.message.columns_shown_but_not_saved', 'common', 'message', NULL, 'справочник колонок'),
 ('common.message.columns_reset_to_default', 'common', 'message', NULL, 'справочник колонок')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
 ('references.title.columns', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Колонки'),
 ('references.title.columns', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Columns'),
 ('references.column.column', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Колонка'),
 ('references.column.column', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Column'),
 ('references.column.label_for_client', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Подпись у клиента'),
 ('references.column.label_for_client', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Label for the client'),
 ('references.column.available', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Доступна'),
 ('references.column.available', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Available'),
 ('references.column.show_at_once', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Показывать сразу'),
 ('references.column.show_at_once', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Shown by default'),
 ('references.text.pick_a_section_on_the_left', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Выберите раздел слева.'),
 ('references.text.pick_a_section_on_the_left', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Pick a section on the left.'),
 ('references.text.instead_of', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'вместо'),
 ('references.text.instead_of', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'instead of'),
 ('references.hint.unavailable_column_is_hidden', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Недоступная колонка не попадёт клиенту даже в окно выбора колонок — включить её себе он не сможет. «Показывать сразу» — только значение по умолчанию, пользователь вправе поменять.'),
 ('references.hint.unavailable_column_is_hidden', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'An unavailable column does not reach the client even in the column picker — they cannot switch it on. “Shown by default” is only the default; the user may change it.'),
 ('admin.label.copy_settings_from_user', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Скопировать настройки у пользователя'),
 ('admin.label.copy_settings_from_user', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Copy settings from user'),
 ('admin.button.copy', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Скопировать'),
 ('admin.button.copy', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Copy'),
 ('common.option.not_selected', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '— не выбран —'),
 ('common.option.not_selected', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '— not selected —'),
 ('admin.hint.what_copy_settings_transfers', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Перенесутся видимость пунктов меню, разрешения и выбранные колонки. Роль и доступы к поклажедателям и складам не копируются — их выдают отдельно и осознанно.'),
 ('admin.hint.what_copy_settings_transfers', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Menu visibility, permissions and chosen columns are transferred. The role and the access to depositors and warehouses are not copied — those are granted separately and deliberately.'),
 ('admin.message.pick_whom_to_copy_from', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Выберите, у кого копировать настройки'),
 ('admin.message.pick_whom_to_copy_from', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Pick the user to copy the settings from'),
 ('admin.message.settings_copied', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Настройки скопированы'),
 ('admin.message.settings_copied', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Settings copied'),
 ('common.message.keep_at_least_one_column', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Оставьте хотя бы одну колонку'),
 ('common.message.keep_at_least_one_column', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Keep at least one column'),
 ('common.message.columns_shown_but_not_saved', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Колонки показаны, но не сохранились'),
 ('common.message.columns_shown_but_not_saved', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Columns are shown but were not saved'),
 ('common.message.columns_reset_to_default', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Колонки возвращены к настройкам по умолчанию'),
 ('common.message.columns_reset_to_default', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Columns restored to the defaults')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
