-- =====================================================================
-- V57. Сообщения сервера — в каталог текстов
-- =====================================================================
-- Отказы сервера были написаны в коде по-русски:
--
--     throw new ResponseStatusException(CONFLICT, "Логин «" + login + "» уже занят");
--
-- Для русского поклажедателя это работало, для остальных — нет: экран
-- переведён, а отказ приходит по-русски, и переводчику его негде взять.
-- Теперь код бросает КЛЮЧ и подстановки, а фразу собирает сервер на языке
-- того, кто сделал запрос (com.ahlers.portal3pl.common.PortalMessages).
-- Тексты лежат там же, где подписи интерфейса, и правятся тем же экраном
-- «Тексты интерфейса»: для переводчика сообщение об отказе — такой же
-- текст портала, как заголовок кнопки.
--
-- Почему на сервере, а не на экране. Текст отказа показывается в десятках
-- мест, в том числе кодом прототипа, который о ключах не знает: первое же
-- забытое место выдало бы человеку «error.sku.not_found» вместо фразы.
--
-- ЧТО ЗДЕСЬ ЕСТЬ:
--   * error.*       — отказы: по одному ключу на отказ;
--   * error.usage.* — слова для подстановки {where}: «поступлениях (3)».
--     Это не самостоятельные сообщения, а части фразы «Товар используется
--     в поступлениях (3), заказах (1)». Собирать их склейкой в коде нельзя:
--     падежи и порядок слов в языках разные, и переводчику нужна
--     вся фраза, а не половина;
--   * error.status.*, error.request.unreadable, error.internal — отказ без
--     своего текста (его дал Spring, а не портал): подставляется по коду
--     ответа, чтобы человек не видел «400»;
--   * info.* — подтверждения («Пароль изменён»): они из того же ответа
--     сервера и переводятся так же.
--
-- Подстановки записаны как {name} и заменяются на значения из кода.
-- Шаблон правит переводчик, значения приходят из портала — поэтому
-- лишняя скобка в переводе не ломает ответ: неизвестная скобка остаётся
-- в тексте как есть, это видно на экране и чинится правкой текста.
--
-- СОХРАНЯЕМ ФОРМУЛИРОВКИ. Русские фразы перенесены из кода слово в слово:
-- они выверены по экранам, и менять их заодно с переездом значило бы
-- смешать два изменения в одном. Исключение одно: у единицы измерения
-- было «используется товарах (3)» — склейка без предлога; стало
-- «используется в товарах (3)».
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Список: ключ, к чему относится, русский и английский текст
-- ---------------------------------------------------------------------
-- Временная таблица, а не два списка подряд: ui_text ссылается на
-- ui_text_key внешним ключом, значит сначала ключи, потом тексты —
-- двумя отдельными командами. Повторять список ключей дважды нельзя:
-- через месяц они разойдутся, и ошибку никто не заметит.
CREATE TEMP TABLE server_text (
  text_key varchar(60) PRIMARY KEY,
  owner    varchar(40) NOT NULL,
  ru       text        NOT NULL,
  en       text        NOT NULL
);

INSERT INTO server_text (text_key, owner, ru, en) VALUES
  ('error.auth.sign_in_required', 'auth',
   'Требуется вход в портал',
   'Please sign in to the portal'),
  ('error.auth.password_change_required', 'auth',
   'Требуется сменить пароль',
   'You need to change your password'),
  ('error.auth.bad_credentials', 'auth',
   'Неверный логин или пароль',
   'Wrong login or password'),
  ('error.auth.locked_out', 'auth',
   'Вход временно заблокирован из-за неудачных попыток. Повторите позже',
   'Sign-in is temporarily blocked after failed attempts. Try again later'),
  ('error.password.required', 'auth',
   'Введите новый пароль',
   'Enter a new password'),
  ('error.password.current_wrong', 'auth',
   'Текущий пароль указан неверно',
   'The current password is wrong'),
  ('error.password.same_as_current', 'auth',
   'Новый пароль совпадает с текущим',
   'The new password is the same as the current one'),
  ('error.password.too_short', 'auth',
   'Пароль должен быть не короче {min} символов, в этом — {length}',
   'The password must be at least {min} characters long; this one has {length}'),
  ('error.password.latin_only', 'auth',
   'В пароле можно использовать только латинские буквы',
   'Only Latin letters are allowed in the password'),
  ('error.password.needs_both_cases', 'auth',
   'В пароле нужны латинские буквы и заглавные, и строчные',
   'The password needs both upper-case and lower-case Latin letters'),
  ('error.password.needs_digit_or_symbol', 'auth',
   'Добавьте в пароль цифру или знак (например - _ . ! #)',
   'Add a digit or a symbol to the password (for example - _ . ! #)'),
  ('error.password.contains_login', 'auth',
   'Пароль не должен содержать логин («{login}»)',
   'The password must not contain the login ("{login}")'),
  ('error.user.not_found', 'admin',
   'Пользователь не найден',
   'User not found'),
  ('error.user.not_found_or_blocked', 'admin',
   'Пользователь не найден или заблокирован',
   'User not found or blocked'),
  ('error.user.login_required', 'admin',
   'Укажите логин',
   'Enter a login'),
  ('error.user.full_name_required', 'admin',
   'Укажите ФИО',
   'Enter the full name'),
  ('error.user.role_required', 'admin',
   'Выберите роль пользователя',
   'Choose a user role'),
  ('error.user.login_taken', 'admin',
   'Логин «{login}» уже занят',
   'Login "{login}" is already taken'),
  ('error.user.cannot_block_self', 'admin',
   'Нельзя заблокировать свою учётную запись',
   'You cannot block your own account'),
  ('error.user.cannot_change_own_role', 'admin',
   'Нельзя изменить роль своей учётной записи',
   'You cannot change the role of your own account'),
  ('error.access.admin_only', 'admin',
   'Раздел доступен только администратору портала',
   'This section is available to the portal administrator only'),
  ('error.access.partners', 'catalog',
   'Справочники контрагентов доступны поклажедателю, менеджеру и администратору',
   'Partner directories are available to the depositor, the manager and the administrator'),
  ('error.access.sku', 'catalog',
   'Справочник товаров доступен поклажедателю, менеджеру и администратору',
   'The product directory is available to the depositor, the manager and the administrator'),
  ('error.access.units', 'catalog',
   'Единицы измерения общие для всех поклажедателей, их ведёт администратор портала',
   'Units of measure are shared by all depositors and are maintained by the portal administrator'),
  ('error.depositor.required', 'admin',
   'Укажите поклажедателя',
   'Choose a depositor'),
  ('error.depositor.not_found', 'admin',
   'Поклажедатель не найден',
   'Depositor not found'),
  ('error.depositor.not_chosen', 'admin',
   'Не выбран поклажедатель',
   'No depositor chosen'),
  ('error.depositor.not_available', 'auth',
   'Этот поклажедатель недоступен вашей учётной записи',
   'This depositor is not available to your account'),
  ('error.depositor.code_and_name_required', 'admin',
   'У поклажедателя обязательны код и наименование организации',
   'A depositor needs both a code and a company name'),
  ('error.depositor.code_taken', 'admin',
   'Поклажедатель с кодом «{code}» уже есть',
   'A depositor with code "{code}" already exists'),
  ('error.depositor.in_use', 'admin',
   'Поклажедатель используется в {where}. Его можно перевести в архив, но не удалить',
   'The depositor is used in {where}. It can be archived, but not deleted'),
  ('error.warehouse.wms_id_required', 'admin',
   'Укажите идентификатор склада — тот же, что в WMS',
   'Enter the warehouse identifier — the same one as in WMS'),
  ('error.warehouse.duplicate', 'admin',
   'Склад с таким идентификатором или кодом уже есть',
   'A warehouse with this identifier or code already exists'),
  ('error.warehouse.code_taken', 'admin',
   'Такой код склада уже есть',
   'This warehouse code is already taken'),
  ('error.warehouse.not_found', 'admin',
   'Склад не найден',
   'Warehouse not found'),
  ('error.warehouse.in_use', 'admin',
   'Склад используется в {where}. Его можно перевести в архив, но не удалить',
   'The warehouse is used in {where}. It can be archived, but not deleted'),
  ('error.warehouse.create_field_required', 'admin',
   'Чтобы создать склад, заполните поле «{field}»',
   'To create a warehouse, fill in the "{field}" field'),
  ('error.operator.code_and_name_required', 'admin',
   'У организации обязательны код и наименование',
   'An organization needs both a code and a name'),
  ('error.operator.code_taken', 'admin',
   'Организация с кодом «{code}» уже есть',
   'An organization with code "{code}" already exists'),
  ('error.operator.not_found', 'admin',
   'Организация не найдена',
   'Organization not found'),
  ('error.operator.in_use', 'admin',
   'Организация используется: {where}. Снимите её у поклажедателей или отправьте в архив',
   'The organization is in use: {where}. Unassign it from the depositors or archive it'),
  ('error.directory.unknown', 'catalog',
   'Неизвестный справочник: {path}',
   'Unknown directory: {path}'),
  ('error.directory.code_and_name_required', 'admin',
   'Заполните код и наименование',
   'Fill in the code and the name'),
  ('error.order_type.code_taken', 'admin',
   'Тип заказа с кодом «{code}» уже есть',
   'An order type with code "{code}" already exists'),
  ('error.order_type.in_use', 'admin',
   'Тип заказа используется в заказах ({count}). Его можно отключить, но не удалить',
   'The order type is used in orders ({count}). It can be switched off, but not deleted'),
  ('error.order_type.not_found', 'admin',
   'Тип заказа не найден',
   'Order type not found'),
  ('error.receipt_doc_type.code_taken', 'admin',
   'Тип документа с кодом «{code}» уже есть',
   'A document type with code "{code}" already exists'),
  ('error.receipt_doc_type.in_use', 'admin',
   'Тип документа используется в поступлениях ({count}). Его можно отключить, но не удалить',
   'The document type is used in receipts ({count}). It can be switched off, but not deleted'),
  ('error.receipt_doc_type.not_found', 'admin',
   'Тип документа не найден',
   'Document type not found'),
  ('error.supplier.not_found', 'catalog',
   'Поставщик не найден',
   'Supplier not found'),
  ('error.supplier.code_taken', 'catalog',
   'Поставщик с кодом «{code}» у этого поклажедателя уже есть',
   'This depositor already has a supplier with code "{code}"'),
  ('error.supplier.in_use', 'catalog',
   'Поставщик используется в {where}. Его можно перевести в архив, но не удалить',
   'The supplier is used in {where}. It can be archived, but not deleted'),
  ('error.supplier.create_field_required', 'catalog',
   'Чтобы создать поставщика, заполните поле «{field}»',
   'To create a supplier, fill in the "{field}" field'),
  ('error.consignee.not_found', 'catalog',
   'Грузополучатель не найден',
   'Consignee not found'),
  ('error.consignee.code_taken', 'catalog',
   'Грузополучатель с кодом «{code}» у этого поклажедателя уже есть',
   'This depositor already has a consignee with code "{code}"'),
  ('error.consignee.in_use', 'catalog',
   'Грузополучатель используется в {where}. Его можно перевести в архив, но не удалить',
   'The consignee is used in {where}. It can be archived, but not deleted'),
  ('error.consignee.create_field_required', 'catalog',
   'Чтобы создать грузополучателя, заполните поле «{field}»',
   'To create a consignee, fill in the "{field}" field'),
  ('error.address.required', 'catalog',
   'Укажите адрес доставки',
   'Enter the delivery address'),
  ('error.address.duplicate', 'catalog',
   'Такой адрес у этого грузополучателя уже есть',
   'This consignee already has such an address'),
  ('error.address.in_use', 'catalog',
   'Адрес указан в заказах ({count}), удалить его нельзя',
   'The address is used in orders ({count}) and cannot be deleted'),
  ('error.address.not_found', 'catalog',
   'Адрес не найден',
   'Address not found'),
  ('error.sku.not_found', 'catalog',
   'Товар не найден',
   'Product not found'),
  ('error.sku.code_taken', 'catalog',
   'Товар с кодом «{code}» у этого поклажедателя уже есть',
   'This depositor already has a product with code "{code}"'),
  ('error.sku.in_use', 'catalog',
   'Товар используется в {where}. Его можно перевести в архив, но не удалить',
   'The product is used in {where}. It can be archived, but not deleted'),
  ('error.sku.create_field_required', 'catalog',
   'Чтобы создать товар, заполните поле «{field}»',
   'To create a product, fill in the "{field}" field'),
  ('error.packaging.not_found', 'catalog',
   'Упаковка не найдена',
   'Packaging not found'),
  ('error.packaging.code_taken', 'catalog',
   'Упаковка с кодом «{code}» у этого товара уже есть',
   'This product already has a packaging with code "{code}"'),
  ('error.packaging.duplicate', 'catalog',
   'Упаковка с таким кодом у этого товара уже есть',
   'This product already has a packaging with the same code'),
  ('error.packaging.create_field_required', 'catalog',
   'Чтобы создать упаковку, заполните поле «{field}»',
   'To create a packaging, fill in the "{field}" field'),
  ('error.unit.symbol_and_name_required', 'catalog',
   'Заполните обозначение и наименование единицы измерения',
   'Fill in the symbol and the name of the unit'),
  ('error.unit.mkei_required', 'catalog',
   'Заполните код МКЕИ — по нему единицу опознают внешние системы',
   'Fill in the UNECE code — external systems identify the unit by it'),
  ('error.unit.duplicate', 'catalog',
   'Такая единица измерения уже есть в справочнике',
   'This unit is already in the directory'),
  ('error.unit.symbol_taken', 'catalog',
   'Единица измерения «{symbol}» уже есть в справочнике',
   'Unit "{symbol}" is already in the directory'),
  ('error.unit.mkei_taken', 'catalog',
   'Код МКЕИ {code} уже занят единицей «{symbol}»',
   'UNECE code {code} is already taken by unit "{symbol}"'),
  ('error.unit.not_found', 'catalog',
   'Единица измерения не найдена',
   'Unit not found'),
  ('error.unit.in_use', 'catalog',
   'Единица измерения используется в {where}. Её можно отключить, но не удалить',
   'The unit is used in {where}. It can be switched off, but not deleted'),
  ('error.usage.items', 'common',
   'товарах ({count})',
   'products ({count})'),
  ('error.usage.packagings', 'common',
   'упаковках ({count})',
   'packagings ({count})'),
  ('error.usage.receipts', 'common',
   'поступлениях ({count})',
   'receipts ({count})'),
  ('error.usage.orders', 'common',
   'заказах ({count})',
   'orders ({count})'),
  ('error.usage.transfers', 'common',
   'перемещениях ({count})',
   'transfers ({count})'),
  ('error.usage.stock', 'common',
   'остатках ({count})',
   'stock records ({count})'),
  ('error.usage.granted_access', 'common',
   'выданных доступах ({count})',
   'granted access rights ({count})'),
  ('error.usage.depositor_access', 'common',
   'доступах поклажедателей ({count})',
   'depositor access rights ({count})'),
  ('error.usage.staff_access', 'common',
   'доступах сотрудников ({count})',
   'staff access rights ({count})'),
  ('error.usage.depositors', 'common',
   'поклажедатели ({count})',
   'depositors ({count})'),
  ('error.columns.at_least_one', 'columns',
   'Оставьте хотя бы одну колонку',
   'Keep at least one column'),
  ('error.columns.not_available', 'columns',
   'Колонка «{column}» в таблице «{table}» недоступна',
   'Column "{column}" of table "{table}" is not available'),
  ('error.columns.pinned', 'columns',
   'Эти колонки убрать нельзя: {columns}',
   'These columns cannot be removed: {columns}'),
  ('error.columns.table_unknown', 'columns',
   'Таблицы «{table}» в справочнике колонок нет',
   'Table "{table}" is not in the column directory'),
  ('error.columns.column_unknown', 'columns',
   'Колонки «{column}» в таблице «{table}» нет',
   'Table "{table}" has no column "{column}"'),
  ('error.field.required', 'columns',
   'Укажите поле',
   'Choose a field'),
  ('error.field.empty', 'common',
   'Поле «{field}» не может быть пустым',
   'Field "{field}" cannot be empty'),
  ('error.field.too_long', 'common',
   '{field}: не больше {limit} знаков',
   '{field}: no more than {limit} characters'),
  ('error.field.locked', 'columns',
   'Поле «{field}» нельзя скрыть от поклажедателя',
   'Field "{field}" cannot be hidden from the depositor'),
  ('error.field.shown_nowhere', 'columns',
   'Поле «{field}» у объекта «{object}» нигде не показывается',
   'Field "{field}" of object "{object}" is not shown anywhere'),
  ('error.object.unknown', 'columns',
   'Неизвестный тип объекта: {code}',
   'Unknown object type: {code}'),
  ('error.settings.copy_both_required', 'columns',
   'Укажите, у кого и кому копировать настройки',
   'Choose whose settings to copy and to whom'),
  ('error.settings.copy_same_user', 'columns',
   'Это один и тот же пользователь',
   'This is the same user'),
  ('error.sort_order.not_a_number', 'catalog',
   'Порядок — это число',
   'The sort order must be a number'),
  ('error.document.not_found', 'documents',
   'Документ не найден',
   'Document not found'),
  ('error.document.unknown_type', 'documents',
   'Неизвестный тип документа: {path}',
   'Unknown document type: {path}'),
  ('error.document.sent_to_wms', 'documents',
   'Документ уже отправлен в WMS и из портала не редактируется',
   'The document has been sent to WMS and cannot be edited in the portal any more'),
  ('error.document.not_ready', 'documents',
   'Документ не готов к отправке: {problems}',
   'The document is not ready to be sent: {problems}'),
  ('error.draft.not_found', 'documents',
   'Черновик не найден',
   'Draft not found'),
  ('error.line.not_found', 'documents',
   'Строка не найдена',
   'Line not found'),
  ('error.quantity.must_be_positive', 'documents',
   'Количество должно быть больше нуля',
   'The quantity must be greater than zero'),
  ('error.transport.not_allowed', 'documents',
   'Пользователю не разрешено редактировать транспортный раздел',
   'This user is not allowed to edit the transport section'),
  ('error.transport.status_locked', 'documents',
   'В статусе «{status}» транспортный раздел не редактируется',
   'The transport section cannot be edited in status "{status}"'),
  ('error.ready.fields_missing', 'documents',
   'не заполнены поля: {fields}',
   'these fields are empty: {fields}'),
  ('error.ready.no_lines', 'documents',
   'нет ни одной строки',
   'there is not a single line'),
  ('error.ready.bad_quantity', 'documents',
   'есть строки с нулевым или пустым количеством',
   'some lines have a zero or empty quantity'),
  ('error.ready.unknown_sku', 'documents',
   'есть позиции, неизвестные WMS ({count})',
   'some products are unknown to WMS ({count})'),
  ('error.file.not_chosen', 'excel',
   'Файл не выбран',
   'No file chosen'),
  ('error.file.name_missing', 'excel',
   'Имя файла не передано',
   'The file name is missing'),
  ('error.file.empty', 'excel',
   'Файл пуст',
   'The file is empty'),
  ('error.file.too_big', 'excel',
   'Файл больше 2 МБ. Для ста строк номенклатуры столько не нужно — вероятно, выбран не тот файл',
   'The file is larger than 2 MB. A hundred rows of products need far less — you have probably picked the wrong file'),
  ('error.file.not_excel', 'excel',
   'Это не файл Excel. Нужен .xlsx, сохранённый из Excel',
   'This is not an Excel file. A .xlsx saved from Excel is required'),
  ('error.file.unreadable', 'excel',
   'Не удалось прочитать переданный файл',
   'The uploaded file could not be read'),
  ('error.excel.unreadable', 'excel',
   'Не удалось прочитать файл. Проверьте, что это .xlsx, сохранённый из Excel, и без пароля',
   'The file could not be read. Check that it is a .xlsx saved from Excel and not password-protected'),
  ('error.excel.no_sheets', 'excel',
   'В файле нет заполненных листов',
   'The file has no filled sheets'),
  ('error.excel.no_header_row', 'excel',
   'В файле нет строки заголовков',
   'The file has no header row'),
  ('error.excel.no_rows', 'excel',
   'В файле нет ни одной заполненной строки',
   'The file has not a single filled row'),
  ('error.excel.no_code_column', 'excel',
   'В файле нет столбца с коммерческим кодом товара. Найденные заголовки: {headers}',
   'The file has no column with the product code. Headers found: {headers}'),
  ('error.excel.too_many_rows', 'excel',
   'В файле больше {limit} строк с данными. Разделите его на части',
   'The file has more than {limit} data rows. Split it into parts'),
  ('error.excel.other_depositor_marker', 'excel',
   'Файл сформирован для поклажедателя «{marker}», а вы работаете с «{current}»',
   'The file was built for depositor "{marker}", while you are working with "{current}"'),
  ('error.excel.other_depositor_name', 'excel',
   'В имени файла указан поклажедатель «{other}», а вы работаете с «{current}»',
   'The file name mentions depositor "{other}", while you are working with "{current}"'),
  ('error.excel.upload_disabled', 'excel',
   'Загрузка из Excel для вас отключена',
   'Excel upload is switched off for your account'),
  ('error.import.not_found', 'excel',
   'Загрузка не найдена',
   'Upload not found'),
  ('error.import.not_found_maybe_cancelled', 'excel',
   'Загрузка не найдена — возможно, она уже отменена',
   'Upload not found — it may have been cancelled already'),
  ('error.import.other_user', 'excel',
   'Эту загрузку начинал другой пользователь. Загрузите файл заново',
   'Another user started this upload. Upload the file again'),
  ('error.template.no_columns', 'excel',
   'Для этого поклажедателя не осталось доступных колонок товара',
   'No product columns are left available for this depositor'),
  ('error.template.build_failed', 'excel',
   'Не удалось собрать шаблон',
   'The template could not be built'),
  ('error.locale.unknown', 'i18n',
   'Неизвестный язык: {code}',
   'Unknown language: {code}'),
  ('error.locale.default_cannot_hide', 'i18n',
   'Это язык портала по умолчанию — на него откатываются недостающие переводы, выключить его нельзя',
   'This is the portal default language — missing translations fall back to it, so it cannot be switched off'),
  ('error.locale.nothing_to_change', 'i18n',
   'Нечего менять: укажите «показывать» или «сделать основным»',
   'Nothing to change: specify "show" or "make default"'),
  ('error.text.key_required', 'i18n',
   'Укажите ключ текста',
   'Enter the text key'),
  ('error.text.key_unused', 'i18n',
   'Ключ «{key}» в интерфейсе не используется',
   'Key "{key}" is not used in the interface'),
  ('error.status.forbidden', 'common',
   'Действие недоступно вашей учётной записи',
   'This action is not available to your account'),
  ('error.status.not_found', 'common',
   'Запись не найдена',
   'Record not found'),
  ('error.status.conflict', 'common',
   'Действие сейчас невозможно',
   'This action is not possible right now'),
  ('error.status.unprocessable', 'common',
   'Данные заполнены не полностью',
   'The data is incomplete'),
  ('error.status.too_many', 'common',
   'Слишком много попыток. Повторите позже',
   'Too many attempts. Try again later'),
  ('error.status.failed', 'common',
   'Запрос не выполнен',
   'The request was not carried out'),
  ('error.request.unreadable', 'common',
   'Запрос не распознан порталом',
   'The portal did not recognize the request'),
  ('error.internal', 'common',
   'Внутренняя ошибка портала. Обратитесь к администратору',
   'Internal portal error. Please contact the administrator'),
  ('info.password.changed', 'auth',
   'Пароль изменён',
   'The password has been changed'),
  ('info.user.created', 'admin',
   'Пользователь создан. Пароль нужно передать ему — при первом входе портал попросит задать свой',
   'The user has been created. Pass the password on to them — the portal will ask for a new one at the first sign-in'),
  ('info.user.password_set', 'admin',
   'Пароль задан. Пользователь сменит его при первом входе',
   'The password is set. The user will change it at the first sign-in')
;

-- ---------------------------------------------------------------------
-- 2. Ключи в каталоге
-- ---------------------------------------------------------------------
-- scope = 'server': на экране переводов это отдельный раздел — видно,
-- что текст показывает сервер, а не вёрстка. element = 'message'.
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT s.text_key, 'server', 'message', s.owner,
       'сообщение сервера; собирается по ключу в PortalMessages'
  FROM server_text s
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     owner   = excluded.owner,
                                     note    = excluded.note;

-- ---------------------------------------------------------------------
-- 3. Тексты на русском и английском
-- ---------------------------------------------------------------------
-- DO NOTHING, а не DO UPDATE: если текст в базе уже есть, его правил
-- человек на экране переводов, и затирать его миграцией нельзя.
INSERT INTO ui_text (text_key, locale_id, value)
SELECT s.text_key, l.locale_id,
       CASE l.code WHEN 'ru' THEN s.ru ELSE s.en END
  FROM server_text s
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. Проверка
-- ---------------------------------------------------------------------
-- Ключ без текста хуже отсутствующего ключа: портал покажет человеку
-- сам ключ. Если после миграции такой найдётся — останавливаем её,
-- сборка упадёт здесь, а не на экране у поклажедателя.
DO $$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(s.text_key || ' (' || l.code || ')', ', ' ORDER BY s.text_key)
    INTO missing
    FROM server_text s
    JOIN dictionary_locale l ON l.code IN ('ru', 'en')
    LEFT JOIN ui_text t ON t.text_key = s.text_key AND t.locale_id = l.locale_id
   WHERE t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для ключей сообщений сервера: %', missing;
  END IF;
END $$;

-- ---------------------------------------------------------------------
-- 5. Старые ключи сообщений сервера — удаляем
-- ---------------------------------------------------------------------
-- server.message.password_changed, .record_not_found, .supplier,
-- .this_action_is_not_possible_right остались от первой выгрузки
-- каталога: ключи завели «про запас», а код их никогда не читал —
-- он писал те же фразы по-русски прямо в throw. Теперь у этих фраз
-- есть настоящие ключи (info.password.changed, error.status.not_found,
-- error.status.conflict), а «про запас» в каталоге текстов опасен:
-- переводчик видит действующую подпись и переводит её в никуда.
-- ON DELETE CASCADE снимет и переводы.
DELETE FROM ui_text_key
 WHERE text_key LIKE 'server.message.%';

DROP TABLE server_text;

-- ---------------------------------------------------------------------
-- 6. Две подписи рядом с этими причинами
-- ---------------------------------------------------------------------
-- Причины «чего не хватает» теперь приходят на языке пользователя,
-- а баннер карточки документа и подсказка кнопки «Подтвердить»
-- склеивали их с русским текстом прямо в draft.js. У английского
-- клиента вышла бы строка из двух языков в одной фразе, поэтому
-- заводим им ключи здесь же — дешевле, чем отдельная миграция
-- ради двух надписей.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('sku.text.document_not_ready_yet', 'sku', 'text', NULL,
   'баннер карточки документа перед списком причин'),
  ('sku.text.fix_problems_first', 'sku', 'text', NULL,
   'подсказка неактивной кнопки «Подтвердить»')
ON CONFLICT (text_key) DO NOTHING;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
        ('sku.text.document_not_ready_yet',
         'Документ пока нельзя отправить в WMS:',
         'The document cannot be sent to WMS yet:'),
        ('sku.text.fix_problems_first',
         'Сначала устраните:',
         'Fix these first:')
       ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;
