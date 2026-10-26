-- ---------------------------------------------------------------------
-- V74. Русский текст из разметки, собираемой кодом, — в ключи
-- ---------------------------------------------------------------------
-- Продолжение V66. Тогда на ключи перевели плашки (notify/say); теперь
-- очередь разметки, которую портал рисует сам: заголовки карточек
-- доступов, правила пароля, подсказки справочников и карточки товара,
-- подписи экрана текстов интерфейса.
--
-- Почему это важнее, чем кажется. Такая строка не отличается от
-- переведённой, пока язык русский, — и ровно поэтому её не замечают.
-- При английском языке экран становится наполовину русским, причём
-- русскими остаются подсказки, то есть именно то, что читают, когда
-- не поняли.
--
-- Найдено 61 русская строка в десяти файлах. Из них:
--   * 31 ключ заводится здесь;
--   * 6 текстов нашлись в каталоге готовыми (не удалось получить
--     данные, поля со звёздочкой, нет, Основной и другие) — код просто
--     начинает их звать;
--   * 8 строк оставлены: это подписи для console.warn, их читает
--     разработчик в отладчике, а не человек на экране;
--   * 3 строки оставлены: это НЕ текст, а образцы для поиска кнопки
--     по её onclick из прототипа;
--   * 2 строки удалены вместе с мёртвым кодом: генератор пароля
--     собирал второе, кириллическое слово и никуда его не девал.
--     Выглядело хуже, чем было: правило пароля требует латинских
--     букв, и строки читались как «портал выдаёт пароль, который сам
--     же и отвергнет».

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.text.view_mode_access', 'admin', 'text', NULL, 'плашка над экраном доступов у того, кто правит их не вправе'),
  ('admin.title.manager_access', 'admin', 'title', NULL, 'заголовок карточки доступов менеджера, дальше идёт его имя'),
  ('admin.hint.manager_sees_only_checked', 'admin', 'hint', NULL, 'подсказка под заголовком доступов менеджера'),
  ('admin.text.by_warehouses', 'admin', 'text', NULL, 'в списке сотрудников склада вместо кодов поклажедателей: доступ идёт по складам'),
  ('admin.title.employee_access', 'admin', 'title', NULL, 'заголовок карточки доступов сотрудника склада, дальше идёт его имя'),
  ('admin.hint.employee_sees_by_warehouses', 'admin', 'hint', NULL, 'подсказка под заголовком доступов сотрудника склада'),
  ('admin.title.available_warehouses', 'admin', 'title', NULL, 'заголовок карточки складов поклажедателя, дальше идёт его название'),
  ('admin.label.depositor_code', 'admin', 'label', NULL, 'подпись кода поклажедателя в шапке карточки складов'),
  ('admin.hint.depositor_warehouses', 'admin', 'hint', NULL, 'подсказка под заголовком складов поклажедателя'),
  ('admin.text.latin_letters_both_cases', 'admin', 'text', NULL, 'правило пароля: буквы'),
  ('admin.text.digit_or_sign', 'admin', 'text', NULL, 'правило пароля: цифра или знак'),
  ('admin.text.password_issued_by_admin', 'admin', 'text', NULL, 'подсказка на обязательной смене пароля, перед «введите выданный пароль»'),
  ('admin.hint.must_change_password_badge', 'admin', 'hint', NULL, 'всплывающая подсказка значка «сменит пароль при входе»'),
  ('admin.hint.cannot_block_yourself', 'admin', 'hint', NULL, 'всплывающая подсказка на снятой галочке блокировки собственной учётной записи'),
  ('admin.hint.own_account', 'admin', 'hint', NULL, 'подсказка в карточке собственной учётной записи'),
  ('admin.text.view_mode_users', 'admin', 'text', NULL, 'плашка над карточкой пользователя у того, кто правит её не вправе'),
  ('auth.text.manager_mode_depositors', 'auth', 'text', NULL, 'строка режима в шапке у менеджера; {names} — перечень поклажедателей'),
  ('auth.text.organization', 'auth', 'text', NULL, 'подпись организации в шапке у обычного пользователя'),
  ('admin.placeholder.ui_texts_search', 'admin', 'placeholder', NULL, 'подсказка в поле поиска по текстам интерфейса'),
  ('admin.label.scope', 'admin', 'label', NULL, 'раздел ключа: общий, администрирование, товары и прочие'),
  ('admin.label.element', 'admin', 'label', NULL, 'вид текста: кнопка, подпись, плашка и прочие'),
  ('admin.option.all_scopes', 'admin', 'option', NULL, 'вариант «все» в отборе по разделу'),
  ('admin.option.all_elements', 'admin', 'option', NULL, 'вариант «все» в отборе по виду'),
  ('admin.column.belongs_to', 'admin', 'column', NULL, 'колонка «к чему относится ключ» в текстах интерфейса'),
  ('admin.text.users_with_access', 'admin', 'text', NULL, 'в карточке склада: сколько человек имеет к нему доступ; {count} — число'),
  ('sku.hint.save_product_first', 'sku', 'hint', NULL, 'вкладка упаковок у несохранённого товара'),
  ('sku.text.by_default', 'sku', 'text', NULL, 'пометка упаковки, выбранной единицей заказа по умолчанию'),
  ('sku.hint.fields_set_by_wms', 'sku', 'hint', NULL, 'вкладка карточки товара, поля которой портал не правит'),
  ('sku.text.no_fields_on_this_tab', 'sku', 'text', NULL, 'вкладка карточки товара, все поля которой закрыты поклажедателю'),
  ('sku.hint.required_and_one_save', 'sku', 'hint', NULL, 'подсказка под полями карточки товара'),
  ('common.text.sku_template_file_name', 'common', 'text', NULL, 'имя файла шаблона номенклатуры при скачивании, без расширения')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('admin.text.view_mode_access',
     '👁 Режим просмотра — менять доступы вправе только администратор.',
     '👁 View only — access rights can be changed by an administrator.'),
    ('admin.title.manager_access',
     'Доступы менеджера',
     'Manager access'),
    ('admin.hint.manager_sees_only_checked',
     'Менеджер видит только отмеченных. Если доступен не один, портал при входе спросит, с кем он работает.',
     'A manager sees only the depositors ticked here. If more than one is available, the portal asks which one to work with at sign-in.'),
    ('admin.text.by_warehouses',
     'по складам',
     'by warehouses'),
    ('admin.title.employee_access',
     'Доступы сотрудника',
     'Employee access'),
    ('admin.hint.employee_sees_by_warehouses',
     'Поклажедатели сотруднику не назначаются отдельно: он видит тех, чей товар лежит на его складах.',
     'Depositors are not assigned to an employee separately: they see whoever stores goods at their warehouses.'),
    ('admin.title.available_warehouses',
     'Доступные склады',
     'Available warehouses'),
    ('admin.label.depositor_code',
     'Код поклажедателя',
     'Depositor code'),
    ('admin.hint.depositor_warehouses',
     'Отмеченные склады поклажедатель видит в документах. Если не отмечен ни один — доступны все: так забытая настройка не оставит клиента без складов.',
     'The depositor sees the ticked warehouses in documents. If none are ticked, all of them are available: a forgotten setting must not leave a client with no warehouses.'),
    ('admin.text.latin_letters_both_cases',
     'латинские буквы: и заглавные, и строчные',
     'Latin letters, both upper and lower case'),
    ('admin.text.digit_or_sign',
     'цифра или знак (например - _ . ! #)',
     'a digit or a sign (for example - _ . ! #)'),
    ('admin.text.password_issued_by_admin',
     'Пароль выдал администратор портала — работать можно только со своим.',
     'Your password was issued by the portal administrator — you may only work with one of your own.'),
    ('admin.hint.must_change_password_badge',
     'Пароль выдан администратором и будет сменён при входе',
     'The password was issued by an administrator and will be changed at sign-in'),
    ('admin.hint.cannot_block_yourself',
     'Свою учётную запись заблокировать нельзя',
     'You cannot block your own account'),
    ('admin.hint.own_account',
     'Это ваша учётная запись: роль и блокировку для себя менять нельзя.',
     'This is your own account: you cannot change your own role or block yourself.'),
    ('admin.text.view_mode_users',
     '👁 Режим просмотра — менять пользователей вправе только администратор.',
     '👁 View only — users can be changed by an administrator.'),
    ('auth.text.manager_mode_depositors',
     'Режим менеджера: доступны поклажедатели — {names}',
     'Manager mode: available depositors — {names}'),
    ('auth.text.organization',
     'Организация',
     'Organization'),
    ('admin.placeholder.ui_texts_search',
     'напр. Создать',
     'e.g. Create'),
    ('admin.label.scope',
     'Раздел',
     'Scope'),
    ('admin.label.element',
     'Вид',
     'Kind'),
    ('admin.option.all_scopes',
     'Все разделы',
     'All scopes'),
    ('admin.option.all_elements',
     'Все виды',
     'All kinds'),
    ('admin.column.belongs_to',
     'Относится к',
     'Belongs to'),
    ('admin.text.users_with_access',
     'Пользователей с доступом: {count}',
     'Users with access: {count}'),
    ('sku.hint.save_product_first',
     'Сначала сохраните товар — упаковки задаются уже для него',
     'Save the product first — packagings are defined for an existing one'),
    ('sku.text.by_default',
     'по умолчанию',
     'default'),
    ('sku.hint.fields_set_by_wms',
     'Эти поля портал не меняет: их ставит обмен с WMS или сам портал',
     'The portal does not change these fields: they are set by the WMS exchange or by the portal itself'),
    ('sku.text.no_fields_on_this_tab',
     'На этой вкладке у вас нет доступных полей',
     'You have no available fields on this tab'),
    ('sku.hint.required_and_one_save',
     'Поля со звёздочкой обязательны. Правки со всех вкладок сохраняются одной кнопкой.',
     'Fields marked with an asterisk are required. Changes from all tabs are saved by one button.'),
    ('common.text.sku_template_file_name',
     'Шаблон_Номенклатура',
     'Product_template')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
-- Ключ без текста хуже русской строки: человек увидит
-- «admin.hint.own_account». Проверяется здесь, а не только тестом:
-- миграция, записавшая ключи без переводов, не должна применяться.
DO $check$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(k.text_key || ' [' || l.code || ']', ', ')
    INTO missing
    FROM ui_text_key k
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE l.code IN ('ru', 'en')
     AND k.text_key IN (
       'admin.text.view_mode_access',
       'admin.title.manager_access',
       'admin.hint.manager_sees_only_checked',
       'admin.text.by_warehouses',
       'admin.title.employee_access',
       'admin.hint.employee_sees_by_warehouses',
       'admin.title.available_warehouses',
       'admin.label.depositor_code',
       'admin.hint.depositor_warehouses',
       'admin.text.latin_letters_both_cases',
       'admin.text.digit_or_sign',
       'admin.text.password_issued_by_admin',
       'admin.hint.must_change_password_badge',
       'admin.hint.cannot_block_yourself',
       'admin.hint.own_account',
       'admin.text.view_mode_users',
       'auth.text.manager_mode_depositors',
       'auth.text.organization',
       'admin.placeholder.ui_texts_search',
       'admin.label.scope',
       'admin.label.element',
       'admin.option.all_scopes',
       'admin.option.all_elements',
       'admin.column.belongs_to',
       'admin.text.users_with_access',
       'sku.hint.save_product_first',
       'sku.text.by_default',
       'sku.hint.fields_set_by_wms',
       'sku.text.no_fields_on_this_tab',
       'sku.hint.required_and_one_save',
       'common.text.sku_template_file_name'
     )
     AND (t.value IS NULL OR t.value = '');
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'ключи заведены без текста: %', missing;
  END IF;
END
$check$;
