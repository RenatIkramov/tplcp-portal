-- ---------------------------------------------------------------------
-- V102. Загрузка товаров и упаковок: замечания и шаблоны — на ключах
-- ---------------------------------------------------------------------
-- Решение заказчика от 03.11.2026. Загрузки товаров и упаковок писали
-- замечания готовыми русскими фразами из кода; в английском интерфейсе
-- и в журнале администратора они оставались русскими. Шаблоны этих
-- загрузок тоже несли русский текст из кода: тексты проверок ячеек,
-- подсказки к колонкам, имена листов и файлов.
--
-- Теперь так же, как у документов (V98):
--   * у замечания есть ключ и подстановки (excel_import_issue.message_key,
--     message_params); текст в message — на языке загрузившего, запасной;
--   * окно проверки собирает фразу на языке смотрящего — портал, а не
--     представление: формат подстановок один, в PortalMessages. Поэтому
--     колонка issues из v_excel_import_rows убрана: её больше никто
--     не читает;
--   * шаблон — на языке скачавшего. Лист с таблицей тоже: загрузка ищет
--     лист по имени на своём языке, а не нашла — берёт первый видимый,
--     так что прежние файлы с листом «Номенклатура» грузятся по-прежнему.
--
-- Записи журнала, сделанные до V102, остаются текстом.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.k, v.scope, 'message', v.owner, v.note
  FROM (VALUES
    ('error.catalog_import.negative', 'server', 'sku', 'загрузка товаров/упаковок: отрицательное число (V102)'),
    ('error.catalog_import.not_positive', 'server', 'sku', 'загрузка товаров/упаковок: нужно число больше нуля (V102)'),
    ('error.catalog_import.not_number', 'server', 'sku', 'загрузка товаров/упаковок: не число (V102)'),
    ('error.catalog_import.not_boolean', 'server', 'sku', 'загрузка товаров/упаковок: не да/нет (V102)'),
    ('error.catalog_import.dictionary_empty', 'server', 'sku', 'загрузка товаров/упаковок: справочник поля пуст (V102)'),
    ('error.catalog_import.not_in_dictionary', 'server', 'sku', 'загрузка товаров/упаковок: значения нет в справочнике (V102)'),
    ('error.catalog_import.not_in_dictionary_hint', 'server', 'sku', 'загрузка товаров/упаковок: значения нет, есть похожее (V102)'),
    ('error.catalog_import.too_long', 'server', 'sku', 'загрузка товаров/упаковок: длиннее колонки (V102)'),
    ('error.catalog_import.required', 'server', 'sku', 'загрузка товаров/упаковок: обязательное поле пусто (V102)'),
    ('error.catalog_import.conflict', 'server', 'sku', 'загрузка товаров/упаковок: отличается от портала (V102)'),
    ('error.catalog_import.nothing', 'server', 'sku', 'загрузка товаров/упаковок: строка ничего не меняет (V102)'),
    ('error.catalog_import.apply_failed', 'server', 'sku', 'загрузка товаров/упаковок: строка не записалась (V102)'),
    ('error.sku_import.code_required', 'server', 'sku', 'загрузка товаров: нет коммерческого кода (V102)'),
    ('error.sku_import.code_duplicate', 'server', 'sku', 'загрузка товаров: код повторяется (V102)'),
    ('error.sku_import.archived', 'server', 'sku', 'загрузка товаров: позиция в архиве (V102)'),
    ('error.packaging_import.unknown_item', 'server', 'sku', 'загрузка упаковок: нет такого товара (V102)'),
    ('error.packaging_import.item_required', 'server', 'sku', 'загрузка упаковок: нет кода товара (V102)'),
    ('error.packaging_import.code_required', 'server', 'sku', 'загрузка упаковок: нет кода упаковки (V102)'),
    ('error.packaging_import.code_duplicate', 'server', 'sku', 'загрузка упаковок: упаковка повторяется (V102)'),
    ('error.packaging_import.item_archived', 'server', 'sku', 'загрузка упаковок: товар в архиве (V102)'),
    ('error.packaging_import.order_unit_locked', 'server', 'sku', 'загрузка упаковок: снять единицу заказа нельзя (V102)'),
    ('error.packaging_import.order_unit_twice', 'server', 'sku', 'загрузка упаковок: две единицы заказа (V102)'),
    ('error.packaging_import.order_unit_taken', 'server', 'sku', 'загрузка упаковок: единица заказа уже другая (V102)'),
    ('error.packaging_import.volume_from_file', 'server', 'sku', 'загрузка упаковок: объём пересчитан, в файле был другой (V102)'),
    ('error.packaging_import.volume_from_portal', 'server', 'sku', 'загрузка упаковок: объём пересчитан, в портале был другой (V102)'),
    ('excel.catalog.validation_list_message', 'excel', NULL, 'шаблон товаров/упаковок: проверка списка (V102)'),
    ('excel.catalog.validation_positive_message', 'excel', NULL, 'шаблон товаров/упаковок: проверка числа больше нуля (V102)'),
    ('excel.catalog.validation_number_title', 'excel', NULL, 'шаблон товаров/упаковок: заголовок проверки числа (V102)'),
    ('excel.catalog.validation_number_message', 'excel', NULL, 'шаблон товаров/упаковок: проверка числа не меньше нуля (V102)'),
    ('excel.catalog.note_list', 'excel', NULL, 'шаблон товаров/упаковок: подсказка к списку (V102)'),
    ('excel.catalog.note_positive', 'excel', NULL, 'шаблон товаров/упаковок: подсказка к числу больше нуля (V102)'),
    ('excel.catalog.note_number', 'excel', NULL, 'шаблон товаров/упаковок: подсказка к числу (V102)'),
    ('excel.catalog.note_boolean', 'excel', NULL, 'шаблон товаров/упаковок: подсказка к да/нет (V102)'),
    ('excel.catalog.note_text', 'excel', NULL, 'шаблон товаров/упаковок: подсказка к текстовой колонке (V102)'),
    ('excel.catalog.note_required', 'excel', NULL, 'шаблон товаров/упаковок: обязательное поле (V102)'),
    ('excel.catalog.note_volume', 'excel', NULL, 'шаблон упаковок: объём считается по размерам (V102)'),
    ('excel.sku.sheet', 'excel', NULL, 'шаблон товаров: имя листа (V102)'),
    ('excel.packaging.sheet', 'excel', NULL, 'шаблон упаковок: имя листа (V102)')
  ) AS v(k, scope, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

-- У текстов шаблона element — text, как у остальных excel.*
UPDATE ui_text_key SET element = 'text'
 WHERE text_key LIKE 'excel.catalog.%' OR text_key IN ('excel.sku.sheet', 'excel.packaging.sheet');

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.catalog_import.negative', 'ru', 'Отрицательное значение здесь не имеет смысла'),
    ('error.catalog_import.negative', 'en', 'A negative value makes no sense here'),
    ('error.catalog_import.not_positive', 'ru', 'Нужно число больше нуля'),
    ('error.catalog_import.not_positive', 'en', 'A number greater than zero is required'),
    ('error.catalog_import.not_number', 'ru', 'Это не число. Дробную часть отделяйте запятой или точкой'),
    ('error.catalog_import.not_number', 'en', 'This is not a number. Separate decimals with a comma or a dot'),
    ('error.catalog_import.not_boolean', 'ru', 'Нужно «да» или «нет»'),
    ('error.catalog_import.not_boolean', 'en', 'Use “yes” or “no”'),
    ('error.catalog_import.dictionary_empty', 'ru', 'Справочник для этого поля пуст — обратитесь к оператору'),
    ('error.catalog_import.dictionary_empty', 'en', 'The list for this field is empty: contact the operator'),
    ('error.catalog_import.not_in_dictionary', 'ru', 'Значение «{value}» не найдено в справочнике'),
    ('error.catalog_import.not_in_dictionary', 'en', '“{value}” is not in the list'),
    ('error.catalog_import.not_in_dictionary_hint', 'ru', 'Значение «{value}» не найдено в справочнике. Возможно, «{hint}»'),
    ('error.catalog_import.not_in_dictionary_hint', 'en', '“{value}” is not in the list. Did you mean “{hint}”?'),
    ('error.catalog_import.too_long', 'ru', 'Значение длиннее {limit} знаков'),
    ('error.catalog_import.too_long', 'en', 'The value is longer than {limit} characters'),
    ('error.catalog_import.required', 'ru', 'Не заполнено обязательное поле'),
    ('error.catalog_import.required', 'en', 'A required field is empty'),
    ('error.catalog_import.conflict', 'ru', 'Значение отличается от того, что уже в портале'),
    ('error.catalog_import.conflict', 'en', 'The value differs from the one already in the portal'),
    ('error.catalog_import.nothing', 'ru', 'Строка не меняет ни одного поля — всё уже заполнено так же'),
    ('error.catalog_import.nothing', 'en', 'The row changes nothing: every field is already filled in the same way'),
    ('error.catalog_import.apply_failed', 'ru', 'Не удалось записать строку в справочник'),
    ('error.catalog_import.apply_failed', 'en', 'The row could not be written to the directory'),
    ('error.sku_import.code_required', 'ru', 'Не заполнен коммерческий код — по нему опознаётся позиция'),
    ('error.sku_import.code_required', 'en', 'The commercial code is empty: it identifies the item'),
    ('error.sku_import.code_duplicate', 'ru', 'Код повторяется: он уже есть в строке {row}'),
    ('error.sku_import.code_duplicate', 'en', 'The code repeats: it is already in row {row}'),
    ('error.sku_import.archived', 'ru', 'Позиция в архиве — верните её из архива, прежде чем обновлять'),
    ('error.sku_import.archived', 'en', 'The item is archived: restore it before updating'),
    ('error.packaging_import.unknown_item', 'ru', 'Товара с кодом «{code}» нет в справочнике — заведите его, прежде чем загружать упаковки'),
    ('error.packaging_import.unknown_item', 'en', 'There is no item with code “{code}”: create it before uploading packagings'),
    ('error.packaging_import.item_required', 'ru', 'Не заполнен код товара — упаковка без товара не существует'),
    ('error.packaging_import.item_required', 'en', 'The item code is empty: a packaging cannot exist without an item'),
    ('error.packaging_import.code_required', 'ru', 'Не заполнен код упаковки — по нему опознаётся упаковка'),
    ('error.packaging_import.code_required', 'en', 'The packaging code is empty: it identifies the packaging'),
    ('error.packaging_import.code_duplicate', 'ru', 'Упаковка повторяется: у этого товара она уже есть в строке {row}'),
    ('error.packaging_import.code_duplicate', 'en', 'The packaging repeats: this item already has it in row {row}'),
    ('error.packaging_import.item_archived', 'ru', 'Товар в архиве — верните его из архива, прежде чем заводить упаковки'),
    ('error.packaging_import.item_archived', 'en', 'The item is archived: restore it before adding packagings'),
    ('error.packaging_import.order_unit_locked', 'ru', 'Эта упаковка — единица заказа товара. Снять признак загрузкой нельзя: выберите другую единицу заказа в карточке товара'),
    ('error.packaging_import.order_unit_locked', 'en', 'This packaging is the item''s order unit. An upload cannot clear the flag: choose another order unit in the item card'),
    ('error.packaging_import.order_unit_twice', 'ru', 'Единицей заказа у товара может быть одна упаковка, а «да» уже стоит в строке {row}'),
    ('error.packaging_import.order_unit_twice', 'en', 'An item can have only one order unit, and “yes” is already set in row {row}'),
    ('error.packaging_import.order_unit_taken', 'ru', 'У товара уже выбрана другая единица заказа — упаковка «{code}»'),
    ('error.packaging_import.order_unit_taken', 'en', 'The item already has another order unit: packaging “{code}”'),
    ('error.packaging_import.volume_from_file', 'ru', 'Объём пересчитан по размерам: в файле {file}, по длине, ширине и высоте {volume}'),
    ('error.packaging_import.volume_from_file', 'en', 'Volume recalculated from the dimensions: {file} in the file, {volume} from length, width and height'),
    ('error.packaging_import.volume_from_portal', 'ru', 'Объём пересчитан по размерам: в портале {portal}, по длине, ширине и высоте {volume}'),
    ('error.packaging_import.volume_from_portal', 'en', 'Volume recalculated from the dimensions: {portal} in the portal, {volume} from length, width and height'),
    ('excel.catalog.validation_list_message', 'ru', 'Выберите значение из списка. Если нужного нет — обратитесь к оператору, справочник ведёт он.'),
    ('excel.catalog.validation_list_message', 'en', 'Choose a value from the list. If it is missing, contact the operator: the operator maintains the list.'),
    ('excel.catalog.validation_positive_message', 'ru', 'Введите число больше нуля. Дробную часть отделяйте запятой или точкой.'),
    ('excel.catalog.validation_positive_message', 'en', 'Enter a number greater than zero. Separate decimals with a comma or a dot.'),
    ('excel.catalog.validation_number_title', 'ru', 'Нужно число'),
    ('excel.catalog.validation_number_title', 'en', 'A number is required'),
    ('excel.catalog.validation_number_message', 'ru', 'Введите число, не меньше нуля. Дробную часть отделяйте запятой или точкой.'),
    ('excel.catalog.validation_number_message', 'en', 'Enter a number, zero or more. Separate decimals with a comma or a dot.'),
    ('excel.catalog.note_list', 'ru', 'Выберите из списка. Значения ведёт оператор.'),
    ('excel.catalog.note_list', 'en', 'Choose from the list. The operator maintains the values.'),
    ('excel.catalog.note_positive', 'ru', 'Число больше нуля.'),
    ('excel.catalog.note_positive', 'en', 'A number greater than zero.'),
    ('excel.catalog.note_number', 'ru', 'Число, не меньше нуля.'),
    ('excel.catalog.note_number', 'en', 'A number, zero or more.'),
    ('excel.catalog.note_boolean', 'ru', '«{yes}» / «{no}».'),
    ('excel.catalog.note_boolean', 'en', '“{yes}” / “{no}”.'),
    ('excel.catalog.note_text', 'ru', 'Хранится текстом: ведущие нули и дефисы не потеряются.'),
    ('excel.catalog.note_text', 'en', 'Stored as text: leading zeros and dashes are kept.'),
    ('excel.catalog.note_required', 'ru', 'Обязательное поле.'),
    ('excel.catalog.note_required', 'en', 'Required field.'),
    ('excel.catalog.note_volume', 'ru', 'Если заданы длина, ширина и высота, объём рассчитается сам: Д × Ш × В (см) / 1 000 000.'),
    ('excel.catalog.note_volume', 'en', 'If length, width and height are given, the volume is calculated: L × W × H (cm) / 1,000,000.'),
    ('excel.sku.sheet', 'ru', 'Номенклатура'),
    ('excel.sku.sheet', 'en', 'Items'),
    ('excel.packaging.sheet', 'ru', 'Упаковки'),
    ('excel.packaging.sheet', 'en', 'Packaging')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- 2. Окно проверки читает замечания само — колонка issues не нужна
-- ---------------------------------------------------------------------
-- CREATE OR REPLACE не убирает колонку: представление пересоздаётся,
-- а с ним заново ставятся security_invoker и права.
DROP VIEW v_excel_import_rows;
CREATE VIEW v_excel_import_rows WITH (security_invoker = true) AS
 SELECT import_id,
    row_number,
    row_class,
    is_valid,
    applied,
    row_key,
    sku_id::text AS sku_id,
    cell_values,
    fill_fields,
    cell_display
   FROM excel_import_row r
  ORDER BY row_number;
GRANT SELECT ON v_excel_import_rows TO portal_app;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF (SELECT reloptions FROM pg_class WHERE oid = 'dbo.v_excel_import_rows'::regclass)
       IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
    RAISE EXCEPTION 'v_excel_import_rows без security_invoker';
  END IF;
  IF EXISTS (SELECT 1 FROM ui_text_key k
              WHERE (k.text_key LIKE 'error.catalog_import.%' OR k.text_key LIKE 'error.sku_import.%'
                     OR k.text_key LIKE 'error.packaging_import.%' OR k.text_key LIKE 'excel.catalog.%')
                AND (SELECT count(*) FROM ui_text t WHERE t.text_key = k.text_key) < 2) THEN
    RAISE EXCEPTION 'у части новых ключей нет русского или английского текста';
  END IF;
END
$check$;
