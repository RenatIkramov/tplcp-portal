-- ---------------------------------------------------------------------
-- V101. Перемещение из Excel; качество в строке перемещения
-- ---------------------------------------------------------------------
-- Решения заказчика от 03.11.2026:
--
--   1. Перемещение загружается из Excel так же, как заказ (V96)
--      и поступление (V100): один файл — один черновик, ошибки —
--      списком, номер ERP обязателен и уникален. Код общий, у
--      перемещения свои поля заголовка (склад-отправитель и
--      склад-получатель) и несколько фраз. Цены в перемещении нет:
--      товар не меняет владельца.
--   2. Одинаковые склад-отправитель и склад-получатель в файле —
--      ошибка загрузки, черновик не создаётся.
--   3. В строке перемещения — качество, как в заказе и поступлении:
--      код enum_quality, пусто — «Годный». Колонка quality_id в
--      transfer_details есть с V89 (её писала только WMS); теперь
--      плановую строку задаёт портал — можно переместить, например,
--      только брак.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Тексты перемещения
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.k, v.scope, v.element, v.owner, v.note
  FROM (VALUES
    ('excel.transfer.sheet', 'excel', 'text', NULL, 'шаблон перемещения: имя листа (V101)'),
    ('excel.transfer.file_name', 'excel', 'text', NULL, 'шаблон перемещения: имя файла (V101)'),
    ('excel.transfer.title_goods', 'excel', 'text', NULL, 'шаблон перемещения: заголовок таблицы состава (V101)'),
    ('excel.transfer.title_header', 'excel', 'text', NULL, 'шаблон перемещения: заголовок таблицы заголовка (V101)'),
    ('excel.transfer.lists_hint', 'excel', 'text', NULL, 'шаблон перемещения: пояснение на листе «Списки» (V101)'),
    ('error.transfer_import.archived_item', 'server', 'message', 'transfers', 'загрузка перемещения: товар в архиве (V101)'),
    ('error.transfer_import.erp_taken', 'server', 'message', 'transfers', 'загрузка перемещения: номер ERP уже занят (V101)'),
    ('error.transfer_import.field_not_available', 'server', 'message', 'transfers', 'загрузка перемещения: поле закрыто (V101)'),
    ('error.transfer_import.no_goods', 'server', 'message', 'transfers', 'загрузка перемещения: пустой состав (V101)'),
    ('error.transfer_import.no_goods_table', 'server', 'message', 'transfers', 'загрузка перемещения: нет таблицы Goods (V101)'),
    ('error.transfer_import.same_warehouse', 'server', 'message', 'transfers', 'загрузка перемещения: склады совпадают (V101)'),
    ('error.transfer.create_disabled', 'server', 'message', 'transfers', 'создавать перемещения пользователю не разрешено (V101)'),
    ('transfers.title.import_failed', 'transfers', 'title', NULL, 'окно ошибок загрузки перемещения: заголовок (V101)'),
    ('transfers.message.imported', 'transfers', 'message', NULL, 'плашка: перемещение создано из файла (V101)'),
    ('transfers.text.import_table_header', 'transfers', 'text', NULL, 'окно ошибок загрузки перемещения: таблица заголовка (V101)'),
    ('transfers.text.transfer_template_file_name', 'transfers', 'text', NULL, 'имя скачиваемого шаблона перемещения (V101)'),
    ('col.transferComposition.quality', 'transfers', 'column', 'transferComposition', 'состав перемещения: качество строки (V101)'),
    ('transfers.label.quality', 'transfers', 'label', NULL, 'окно ввода строки перемещения: качество (V101)')
  ) AS v(k, scope, element, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('excel.transfer.sheet', 'ru', 'Перемещение'),
    ('excel.transfer.sheet', 'en', 'Transfer'),
    ('excel.transfer.file_name', 'ru', 'Шаблон_Перемещение'),
    ('excel.transfer.file_name', 'en', 'Transfer_template'),
    ('excel.transfer.title_goods', 'ru', 'Состав перемещения'),
    ('excel.transfer.title_goods', 'en', 'Transfer lines'),
    ('excel.transfer.title_header', 'ru', 'Заголовок перемещения'),
    ('excel.transfer.title_header', 'en', 'Transfer header'),
    ('excel.transfer.lists_hint', 'ru', 'Значения для выпадающих списков листа перемещения — на момент скачивания шаблона. Если нужного значения нет, скачайте шаблон заново.'),
    ('excel.transfer.lists_hint', 'en', 'Values for the drop-down lists of the transfer sheet, as of the template download. If a value is missing, download the template again.'),
    ('error.transfer_import.archived_item', 'ru', 'Товар в архиве — переместить его нельзя'),
    ('error.transfer_import.archived_item', 'en', 'The item is archived and cannot be transferred'),
    ('error.transfer_import.erp_taken', 'ru', 'Перемещение с этим номером уже есть: {no}. Возможно, файл уже загружали'),
    ('error.transfer_import.erp_taken', 'en', 'A transfer with this number already exists: {no}. The file may have been uploaded already'),
    ('error.transfer_import.field_not_available', 'ru', 'Это поле в перемещении вам недоступно'),
    ('error.transfer_import.field_not_available', 'en', 'This transfer field is not available to you'),
    ('error.transfer_import.no_goods', 'ru', 'Состав перемещения пуст'),
    ('error.transfer_import.no_goods', 'en', 'The transfer has no lines'),
    ('error.transfer_import.no_goods_table', 'ru', 'В файле нет таблицы состава перемещения (Goods). Скачайте шаблон перемещения заново'),
    ('error.transfer_import.no_goods_table', 'en', 'The file has no transfer lines table (Goods). Download the transfer template again'),
    ('error.transfer_import.same_warehouse', 'ru', 'Склад-получатель совпадает со складом-отправителем'),
    ('error.transfer_import.same_warehouse', 'en', 'The destination warehouse is the same as the source warehouse'),
    ('error.transfer.create_disabled', 'ru', 'Создание перемещений для вас отключено'),
    ('error.transfer.create_disabled', 'en', 'Creating transfers is disabled for you'),
    ('transfers.title.import_failed', 'ru', 'Перемещение не создано'),
    ('transfers.title.import_failed', 'en', 'Transfer not created'),
    ('transfers.message.imported', 'ru', 'Создан черновик перемещения {no}, строк: {count}. Проверьте его и подтвердите'),
    ('transfers.message.imported', 'en', 'Transfer draft {no} created, lines: {count}. Check it and confirm'),
    ('transfers.text.import_table_header', 'ru', 'Заголовок перемещения'),
    ('transfers.text.import_table_header', 'en', 'Transfer header'),
    ('transfers.text.transfer_template_file_name', 'ru', 'Шаблон_Перемещение'),
    ('transfers.text.transfer_template_file_name', 'en', 'Transfer_template'),
    ('col.transferComposition.quality', 'ru', 'Качество'),
    ('col.transferComposition.quality', 'en', 'Quality'),
    ('transfers.label.quality', 'ru', 'Качество'),
    ('transfers.label.quality', 'en', 'Quality')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- 2. Вид загрузки «Перемещение» — в журнале администратора
-- ---------------------------------------------------------------------
INSERT INTO enum_import_kind (id, code, name, sort_order, is_active)
SELECT next_enum_id('enum_import_kind'), '05', 'Transfer', 5, true
 WHERE NOT EXISTS (SELECT 1 FROM enum_import_kind WHERE code = '05');

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_import_kind', k.id::text, l.locale_id, 'Перемещение'
  FROM enum_import_kind k, dictionary_locale l
 WHERE k.code = '05' AND l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- На код опирается программа (EnumCodes.ImportKind.TRANSFER)
INSERT INTO enum_system_value (enum_key, code) VALUES ('enum_import_kind', '05')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Качество в строке перемещения
-- ---------------------------------------------------------------------
COMMENT ON COLUMN transfer_details.quality_id IS
  'качество: в плановой строке — что перемещать, задаёт портал (V101); в партии — пишет WMS (V89)';

-- Колонки дописываются в конец: CREATE OR REPLACE не даёт менять
-- порядок прежних. Качество — плановой строки, как в заказе (V89).
CREATE OR REPLACE VIEW v_transfer_lines WITH (security_invoker = true) AS
 SELECT td.doc_id,
    td.line_number,
    td.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    enum_symbol('enum_uom'::text, u.id::text, u.symbol::text)::character varying(10) AS unit_name,
    sum(td.qty) AS qty,
    sum(td.qty_wms) AS qty_wms,
    count(td.batch_number) AS batch_count,
    max(td.line_comment) AS line_comment,
    max(enum_name('enum_quality'::text, q.id::text, q.name::text)) AS quality,
    max(q.code::text) AS quality_code
   FROM transfer_details td
     LEFT JOIN sku s ON s.sku_id = td.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_quality q ON q.id = td.quality_id AND td.batch_number IS NULL
  GROUP BY td.doc_id, td.line_number, td.sku_id, s.code, s.name, u.symbol, u.id;

INSERT INTO table_column
  (table_key, column_id, text_key, object_type_id, field_id,
   sort_order, is_default, is_locked, is_pinned, group_key)
SELECT 'transferComposition', 'quality', 'col.transferComposition.quality',
       (SELECT id FROM enum_object_type WHERE code = 'transfers'), 'quality',
       COALESCE((SELECT max(sort_order) + 1 FROM table_column WHERE table_key = 'transferComposition'), 7),
       true, false, false, 'transfers.title.transfer_contents'
ON CONFLICT (table_key, column_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF (SELECT reloptions FROM pg_class WHERE oid = 'dbo.v_transfer_lines'::regclass)
       IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
    RAISE EXCEPTION 'v_transfer_lines потеряло security_invoker';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'dbo' AND table_name = 'v_transfer_lines'
                    AND column_name = 'quality_code') THEN
    RAISE EXCEPTION 'в v_transfer_lines нет quality_code';
  END IF;
END
$check$;
