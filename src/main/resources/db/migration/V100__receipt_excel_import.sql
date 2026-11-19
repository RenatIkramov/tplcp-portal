-- ---------------------------------------------------------------------
-- V100. Поступление из Excel; качество в строке поступления;
--       поставщик поступления необязателен
-- ---------------------------------------------------------------------
-- Решения заказчика от 03.11.2026:
--
--   1. Поступление загружается из Excel так же, как заказ (V96–V99):
--      один файл — один черновик, ошибки — списком, номер ERP
--      обязателен и уникален. Шаблон, загрузка и окно ошибок у двух
--      документов теперь общие; своё у каждого — поля заголовка
--      и несколько фраз.
--   2. В строке поступления — качество, как в заказе: код перечисления
--      enum_quality, пусто — «Годный». Колонка quality_id в
--      receipt_details есть с V89 (её пишет WMS для принятых партий);
--      теперь её задаёт и портал — в плановой строке.
--   3. Поставщик в поступлении необязателен: его больше нет среди
--      полей, без которых «Подтвердить» откажет (DocumentType), и пустой
--      поставщик в файле — не ошибка.
--
-- Тексты. Общие для двух документов фразы переезжают с ключей заказа
-- на общие: excel.order.* → excel.doc.*, error.order_import.* →
-- error.doc_import.*, orders.*import* → common.*import*. Ключ заказа
-- у общей фразы обманывал бы: та же строка звучит и в поступлении.
-- У каждого документа остаются только его фразы («Состав заказа»,
-- «Товар в архиве — заказать его нельзя»).
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Общие тексты — на общие ключи
-- ---------------------------------------------------------------------
CREATE TEMP TABLE v100_rename (old_key varchar(120) PRIMARY KEY, new_key varchar(120) NOT NULL,
                               scope varchar(40)) ON COMMIT DROP;
INSERT INTO v100_rename (old_key, new_key, scope)
SELECT k, replace(k, 'excel.order.', 'excel.doc.'), NULL
  FROM unnest(ARRAY[
    'excel.order.column_field', 'excel.order.column_key', 'excel.order.column_value',
    'excel.order.goods_code', 'excel.order.goods_comment', 'excel.order.goods_currency',
    'excel.order.goods_price', 'excel.order.goods_qty', 'excel.order.goods_quality',
    'excel.order.lists_sheet', 'excel.order.note_code', 'excel.order.note_date',
    'excel.order.note_key', 'excel.order.note_list', 'excel.order.note_qty',
    'excel.order.note_quality', 'excel.order.note_text', 'excel.order.title_transport',
    'excel.order.validation_date_message', 'excel.order.validation_date_title',
    'excel.order.validation_list_message', 'excel.order.validation_list_title',
    'excel.order.validation_price_message', 'excel.order.validation_price_title',
    'excel.order.validation_qty_message', 'excel.order.validation_qty_title',
    'excel.order.validation_text_message', 'excel.order.validation_text_title']) AS k
UNION ALL
SELECT k, replace(k, 'error.order_import.', 'error.doc_import.'), NULL
  FROM unnest(ARRAY[
    'error.order_import.bad_date', 'error.order_import.bad_price',
    'error.order_import.bad_quantity', 'error.order_import.code_required',
    'error.order_import.erp_required', 'error.order_import.not_in_list',
    'error.order_import.price_differs', 'error.order_import.too_long',
    'error.order_import.transport_not_allowed', 'error.order_import.unknown_item']) AS k
UNION ALL
SELECT k, replace(k, 'orders.', 'common.'), 'common'
  FROM unnest(ARRAY[
    'orders.column.import_field', 'orders.column.import_problem', 'orders.column.import_row',
    'orders.column.import_table', 'orders.hint.import_failed', 'orders.message.importing',
    'orders.text.import_table_file', 'orders.text.import_table_goods',
    'orders.text.import_table_transport']) AS k;

-- Ключ текста — первичный ключ без каскада на изменение: новый ключ
-- заводится, переводы переносятся, старый удаляется.
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT r.new_key, COALESCE(r.scope, k.scope), k.element, k.owner,
       replace(replace(k.note, 'шаблон заказа', 'шаблон документа'), 'загрузка заказа', 'загрузка документа')
  FROM v100_rename r JOIN ui_text_key k ON k.text_key = r.old_key
ON CONFLICT (text_key) DO NOTHING;
UPDATE ui_text t SET text_key = r.new_key
  FROM v100_rename r
 WHERE t.text_key = r.old_key
   AND NOT EXISTS (SELECT 1 FROM ui_text x WHERE x.text_key = r.new_key AND x.locale_id = t.locale_id);
DELETE FROM ui_text_key k USING v100_rename r WHERE k.text_key = r.old_key;

-- Проверки кода товара списком нет с V97 — её тексты никому не нужны
DELETE FROM ui_text_key
 WHERE text_key IN ('excel.order.validation_code_message', 'excel.order.validation_code_title');

-- Подсказка к коду товара говорила о списке, которого с V97 нет
UPDATE ui_text t SET value = v.value
  FROM (VALUES
    ('ru', 'Коммерческий код товара из справочника товаров.'),
    ('en', 'Commercial item code from the item directory.')) AS v(code, value)
  JOIN dictionary_locale l ON l.code = v.code
 WHERE t.text_key = 'excel.doc.note_code' AND t.locale_id = l.locale_id;

-- ---------------------------------------------------------------------
-- 2. Тексты поступления
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.k, v.scope, v.element, v.owner, v.note
  FROM (VALUES
    ('excel.receipt.sheet', 'excel', 'text', NULL, 'шаблон поступления: имя листа (V100)'),
    ('excel.receipt.file_name', 'excel', 'text', NULL, 'шаблон поступления: имя файла (V100)'),
    ('excel.receipt.title_goods', 'excel', 'text', NULL, 'шаблон поступления: заголовок таблицы состава (V100)'),
    ('excel.receipt.title_header', 'excel', 'text', NULL, 'шаблон поступления: заголовок таблицы заголовка (V100)'),
    ('excel.receipt.lists_hint', 'excel', 'text', NULL, 'шаблон поступления: пояснение на листе «Списки» (V100)'),
    ('error.receipt_import.archived_item', 'server', 'message', 'receipts', 'загрузка поступления: товар в архиве (V100)'),
    ('error.receipt_import.erp_taken', 'server', 'message', 'receipts', 'загрузка поступления: номер ERP уже занят (V100)'),
    ('error.receipt_import.field_not_available', 'server', 'message', 'receipts', 'загрузка поступления: поле закрыто (V100)'),
    ('error.receipt_import.no_goods', 'server', 'message', 'receipts', 'загрузка поступления: пустой состав (V100)'),
    ('error.receipt_import.no_goods_table', 'server', 'message', 'receipts', 'загрузка поступления: нет таблицы Goods (V100)'),
    ('error.receipt.create_disabled', 'server', 'message', 'receipts', 'создавать поступления пользователю не разрешено (V100)'),
    ('receipts.title.import_failed', 'receipts', 'title', NULL, 'окно ошибок загрузки поступления: заголовок (V100)'),
    ('receipts.message.imported', 'receipts', 'message', NULL, 'плашка: поступление создано из файла (V100)'),
    ('receipts.text.import_table_header', 'receipts', 'text', NULL, 'окно ошибок загрузки поступления: таблица заголовка (V100)'),
    ('receipts.text.receipt_template_file_name', 'receipts', 'text', NULL, 'имя скачиваемого шаблона поступления (V100)'),
    ('col.receiptComposition.quality', 'receipts', 'column', 'receiptComposition', 'состав поступления: качество строки (V100)'),
    ('receipts.label.quality', 'receipts', 'label', NULL, 'окно ввода строки поступления: качество (V100)')
  ) AS v(k, scope, element, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('excel.receipt.sheet', 'ru', 'Поступление'),
    ('excel.receipt.sheet', 'en', 'Receipt'),
    ('excel.receipt.file_name', 'ru', 'Шаблон_Поступление'),
    ('excel.receipt.file_name', 'en', 'Receipt_template'),
    ('excel.receipt.title_goods', 'ru', 'Состав поступления'),
    ('excel.receipt.title_goods', 'en', 'Receipt lines'),
    ('excel.receipt.title_header', 'ru', 'Заголовок поступления'),
    ('excel.receipt.title_header', 'en', 'Receipt header'),
    ('excel.receipt.lists_hint', 'ru', 'Значения для выпадающих списков листа поступления — на момент скачивания шаблона. Если нужного значения нет, скачайте шаблон заново.'),
    ('excel.receipt.lists_hint', 'en', 'Values for the drop-down lists of the receipt sheet, as of the template download. If a value is missing, download the template again.'),
    ('error.receipt_import.archived_item', 'ru', 'Товар в архиве — ожидать его поступления нельзя'),
    ('error.receipt_import.archived_item', 'en', 'The item is archived and cannot be received'),
    ('error.receipt_import.erp_taken', 'ru', 'Поступление с этим номером уже есть: {no}. Возможно, файл уже загружали'),
    ('error.receipt_import.erp_taken', 'en', 'A receipt with this number already exists: {no}. The file may have been uploaded already'),
    ('error.receipt_import.field_not_available', 'ru', 'Это поле в поступлении вам недоступно'),
    ('error.receipt_import.field_not_available', 'en', 'This receipt field is not available to you'),
    ('error.receipt_import.no_goods', 'ru', 'Состав поступления пуст'),
    ('error.receipt_import.no_goods', 'en', 'The receipt has no lines'),
    ('error.receipt_import.no_goods_table', 'ru', 'В файле нет таблицы состава поступления (Goods). Скачайте шаблон поступления заново'),
    ('error.receipt_import.no_goods_table', 'en', 'The file has no receipt lines table (Goods). Download the receipt template again'),
    ('error.receipt.create_disabled', 'ru', 'Создавать поступления вам не разрешено'),
    ('error.receipt.create_disabled', 'en', 'You are not allowed to create receipts'),
    ('receipts.title.import_failed', 'ru', 'Поступление не создано'),
    ('receipts.title.import_failed', 'en', 'Receipt not created'),
    ('receipts.message.imported', 'ru', 'Создан черновик поступления {no}, строк: {count}. Проверьте его и подтвердите'),
    ('receipts.message.imported', 'en', 'Receipt draft {no} created, lines: {count}. Check it and confirm'),
    ('receipts.text.import_table_header', 'ru', 'Заголовок поступления'),
    ('receipts.text.import_table_header', 'en', 'Receipt header'),
    ('receipts.text.receipt_template_file_name', 'ru', 'Шаблон_Поступление'),
    ('receipts.text.receipt_template_file_name', 'en', 'Receipt_template'),
    ('col.receiptComposition.quality', 'ru', 'Качество'),
    ('col.receiptComposition.quality', 'en', 'Quality'),
    ('receipts.label.quality', 'ru', 'Качество'),
    ('receipts.label.quality', 'en', 'Quality')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- 3. Вид загрузки «Поступление» — в журнале администратора
-- ---------------------------------------------------------------------
INSERT INTO enum_import_kind (id, code, name, sort_order, is_active)
SELECT next_enum_id('enum_import_kind'), '04', 'Receipt', 4, true
 WHERE NOT EXISTS (SELECT 1 FROM enum_import_kind WHERE code = '04');

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_import_kind', k.id::text, l.locale_id, 'Поступление'
  FROM enum_import_kind k, dictionary_locale l
 WHERE k.code = '04' AND l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- На код опирается программа (EnumCodes.ImportKind.RECEIPT)
INSERT INTO enum_system_value (enum_key, code) VALUES ('enum_import_kind', '04')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. Качество в строке поступления
-- ---------------------------------------------------------------------
COMMENT ON COLUMN receipt_details.quality_id IS
  'качество: в плановой строке — ожидаемое, задаёт портал (V100); в принятой партии — пишет WMS (V89)';

-- Колонки дописываются в конец: CREATE OR REPLACE не даёт менять
-- порядок прежних. Качество — плановой строки, как в заказе (V89).
CREATE OR REPLACE VIEW v_receipt_lines WITH (security_invoker = true) AS
 SELECT rd.doc_id,
    rd.line_number,
    rd.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    enum_symbol('enum_uom'::text, u.id::text, u.symbol::text)::character varying(10) AS unit_name,
    bool_and(s.is_in_wms) AS sku_in_wms,
    max(rd.expected_qty) AS expected_qty,
    max(rd.expected_qty_wms) AS expected_qty_wms,
    sum(rd.accepted_qty) AS accepted_qty,
    sum(rd.defect_qty) AS defect_qty,
    max(rd.price) AS price,
    max(cur.code::text) AS currency_code,
    max(rd.price) * COALESCE(sum(rd.accepted_qty), max(rd.expected_qty)) AS line_sum,
    count(rd.batch_number) AS batch_count,
    max(rd.line_comment) AS line_comment,
    max(enum_name('enum_quality'::text, q.id::text, q.name::text)) AS quality,
    max(q.code::text) AS quality_code
   FROM receipt_details rd
     LEFT JOIN sku s ON s.sku_id = rd.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_currency cur ON cur.id = rd.currency_id
     LEFT JOIN enum_quality q ON q.id = rd.quality_id AND rd.batch_number IS NULL
  GROUP BY rd.doc_id, rd.line_number, rd.sku_id, s.code, s.name, u.symbol, u.id;

INSERT INTO table_column
  (table_key, column_id, text_key, object_type_id, field_id,
   sort_order, is_default, is_locked, is_pinned, group_key)
SELECT 'receiptComposition', 'quality', 'col.receiptComposition.quality',
       (SELECT id FROM enum_object_type WHERE code = 'receipts'), 'quality',
       COALESCE((SELECT max(sort_order) + 1 FROM table_column WHERE table_key = 'receiptComposition'), 12),
       true, false, false, 'receipts.title.receipt_contents'
ON CONFLICT (table_key, column_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF (SELECT reloptions FROM pg_class WHERE oid = 'dbo.v_receipt_lines'::regclass)
       IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
    RAISE EXCEPTION 'v_receipt_lines потеряло security_invoker';
  END IF;
  IF EXISTS (SELECT 1 FROM ui_text_key WHERE text_key LIKE 'excel.order.validation%') THEN
    RAISE EXCEPTION 'общие тексты шаблона остались на ключах заказа';
  END IF;
  IF (SELECT count(*) FROM ui_text WHERE text_key = 'excel.doc.validation_list_title') = 0 THEN
    RAISE EXCEPTION 'переводы общих текстов потерялись при переносе';
  END IF;
END
$check$;
