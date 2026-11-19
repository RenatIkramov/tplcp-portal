-- ---------------------------------------------------------------------
-- V95. Перечисления — своя группа в администрировании, правка в окне
-- ---------------------------------------------------------------------
-- Решение заказчика от 02.11.2026:
--   * все перечисления — в администрировании, в новой группе
--     «Перечисления». Вкладки «Единицы измерения», «Типы заказов»,
--     «Типы поступлений» уходят туда из «Общих справочников»;
--   * значение создаётся и правится в модальном окне, в нём же —
--     переводы на включённые языки;
--   * добавлять и править можно все перечисления, удалять — только
--     значение, на которое ничего не ссылается;
--   * сводная вкладка «Тексты интерфейса → Перечисления» остаётся.
--
-- Что здесь:
--   1. enum_registry узнаёт, как записывается код значения (образец
--      для проверки) и есть ли у значения второй код — обозначение
--      единицы («pcs»), буквенный код валюты («KZT»);
--   2. enum_system_value — значения, на коды которых опирается сама
--      программа (DocumentStatus, EnumCodes, роли, виды объектов).
--      Их код не меняется, сами они не удаляются и не отключаются:
--      иначе портал перестанет узнавать, например, черновик;
--   3. enum_value_usage() — сколько записей ссылается на каждое
--      значение, по всем внешним ключам на таблицы перечислений
--      сразу. Прежде на каждый справочник была своя функция
--      (unit_usage, order_type_usage, receipt_doc_type_usage) —
--      новое перечисление требовало бы новой функции, а забытая
--      ссылка — молча разрешённого удаления;
--   4. next_enum_id() выдаёт номер любому перечислению из реестра;
--   5. уникальность кода там, где её не было;
--   6. тексты окна и сообщений; тексты удалённых вкладок — удаляются.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Как записывается код
-- ---------------------------------------------------------------------
ALTER TABLE enum_registry
  ADD COLUMN IF NOT EXISTS code_pattern    varchar(100),
  ADD COLUMN IF NOT EXISTS extra_column    varchar(40),
  ADD COLUMN IF NOT EXISTS extra_pattern   varchar(100),
  ADD COLUMN IF NOT EXISTS extra_label_key varchar(80);

COMMENT ON COLUMN enum_registry.code_pattern IS
  'образец кода значения (регулярное выражение); пусто — любой непустой код';
COMMENT ON COLUMN enum_registry.extra_column IS
  'второй код значения: обозначение единицы (symbol), буквенный код валюты (code)';

UPDATE enum_registry SET code_pattern = '^[0-9]{2}$'
 WHERE enum_key IN ('enum_status', 'enum_sku_status', 'enum_invoice_status',
                    'enum_order_type', 'enum_receipt_doc_type', 'enum_quality',
                    'enum_user_status', 'enum_document_origin', 'enum_transport_state',
                    'enum_import_kind', 'enum_import_status', 'enum_sync_direction',
                    'enum_sync_status');
UPDATE enum_registry SET code_pattern = '^[0-9]{3}$',
       extra_column = 'symbol', extra_label_key = 'references.column.unit_symbol'
 WHERE enum_key = 'enum_uom';
UPDATE enum_registry SET code_pattern = '^[0-9]{3}$',
       extra_column = 'code', extra_pattern = '^[A-Z]{3}$',
       extra_label_key = 'admin.label.currency_alpha_code'
 WHERE enum_key = 'enum_currency';
UPDATE enum_registry SET code_pattern = '^[a-z][a-z_]*$'
 WHERE enum_key IN ('enum_packaging_type', 'enum_user_role', 'enum_object_type');
UPDATE enum_registry SET code_pattern = '^[0-9](\.[0-9])?$' WHERE enum_key = 'enum_hazard_class';
UPDATE enum_registry SET code_pattern = '^(0|I|II|III|IV|V)$' WHERE enum_key = 'enum_hazard_group';

-- ---------------------------------------------------------------------
-- 2. Значения, на которые опирается программа
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enum_system_value (
  enum_key varchar(40) NOT NULL REFERENCES enum_registry (enum_key),
  code     varchar(20) NOT NULL,
  PRIMARY KEY (enum_key, code)
);
COMMENT ON TABLE enum_system_value IS
  'значения перечислений, код которых записан в программе: код не меняется, значение не удаляется и не отключается';
GRANT SELECT ON enum_system_value TO portal_app;

INSERT INTO enum_system_value (enum_key, code) VALUES
  -- DocumentStatus и отбор «ждёт склад» в v_stock_available
  ('enum_status', '01'), ('enum_status', '02'), ('enum_status', '03'),
  ('enum_status', '10'), ('enum_status', '30'),
  -- LineQuality.GOOD и «брак» в v_stock_available
  ('enum_quality', '01'), ('enum_quality', '02')
ON CONFLICT DO NOTHING;

-- Служебные перечисления, роли и виды объектов — целиком: каждое
-- их значение где-то в программе сравнивается по коду.
INSERT INTO enum_system_value (enum_key, code)
SELECT 'enum_user_role', code FROM enum_user_role
UNION ALL SELECT 'enum_object_type', code FROM enum_object_type
UNION ALL SELECT 'enum_user_status', code FROM enum_user_status
UNION ALL SELECT 'enum_document_origin', code FROM enum_document_origin
UNION ALL SELECT 'enum_transport_state', code FROM enum_transport_state
UNION ALL SELECT 'enum_import_kind', code FROM enum_import_kind
UNION ALL SELECT 'enum_import_status', code FROM enum_import_status
UNION ALL SELECT 'enum_sync_direction', code FROM enum_sync_direction
UNION ALL SELECT 'enum_sync_status', code FROM enum_sync_status
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Ссылки на значения — по всем внешним ключам сразу
-- ---------------------------------------------------------------------
-- SECURITY DEFINER: ссылки считаются по ВСЕМ поклажедателям. Под RLS
-- администратор увидел бы ноль у единицы, которой пользуется чужой
-- товар, и удалил бы её.
CREATE OR REPLACE FUNCTION enum_value_usage()
RETURNS TABLE (enum_key varchar, value_id text, source_table text, usage_count bigint)
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  q text;
BEGIN
  SELECT string_agg(format(
           'SELECT %L::varchar, e.id::text, %L::text, count(*)::bigint'
           ' FROM %I e JOIN %I s ON s.%I = e.%I GROUP BY e.id',
           r.enum_key, src.relname, r.enum_key, src.relname, sa.attname, ta.attname),
         ' UNION ALL ')
    INTO q
    FROM enum_registry r
    JOIN pg_class tgt ON tgt.relname = r.enum_key
    JOIN pg_namespace n ON n.oid = tgt.relnamespace AND n.nspname = 'dbo'
    JOIN pg_constraint c ON c.confrelid = tgt.oid AND c.contype = 'f'
    JOIN pg_class src ON src.oid = c.conrelid
    JOIN pg_attribute sa ON sa.attrelid = c.conrelid AND sa.attnum = c.conkey[1]
    JOIN pg_attribute ta ON ta.attrelid = c.confrelid AND ta.attnum = c.confkey[1];

  IF q IS NULL THEN
    RETURN;
  END IF;
  RETURN QUERY EXECUTE q;
END;
$$;
REVOKE ALL ON FUNCTION enum_value_usage() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION enum_value_usage() TO portal_app;

-- Прежние функции на каждый справочник — больше не нужны
DROP FUNCTION IF EXISTS unit_usage(smallint);
DROP FUNCTION IF EXISTS order_type_usage(smallint);
DROP FUNCTION IF EXISTS receipt_doc_type_usage(smallint);
DROP VIEW IF EXISTS v_units;

-- ---------------------------------------------------------------------
-- 4. Номер нового значения — для любого перечисления из реестра
-- ---------------------------------------------------------------------
-- Номера пришли из WMS, поэтому не автонумерация: новому значению
-- база выдаёт следующий за наибольшим.
CREATE OR REPLACE FUNCTION next_enum_id(p_table text)
RETURNS smallint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE v_id smallint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM enum_registry WHERE enum_key = p_table) THEN
    RAISE EXCEPTION 'Неизвестное перечисление: %', p_table;
  END IF;
  EXECUTE format('SELECT COALESCE(max(id), 0) + 1 FROM %I', p_table) INTO v_id;
  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------
-- 5. Код — один на значение
-- ---------------------------------------------------------------------
-- Без регистра: «Box» и «box» для человека одно и то же.
CREATE UNIQUE INDEX IF NOT EXISTS enum_user_role_code_ci_uk      ON enum_user_role (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS enum_object_type_code_ci_uk    ON enum_object_type (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS enum_packaging_type_code_ci_uk ON enum_packaging_type (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS enum_hazard_class_code_ci_uk   ON enum_hazard_class (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS enum_hazard_group_code_ci_uk   ON enum_hazard_group (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS enum_currency_code_ci_uk       ON enum_currency (lower(code));

-- ---------------------------------------------------------------------
-- 6. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.hint.enum_values', 'admin', 'hint', NULL,
   'группа «Перечисления»: что здесь ведут (V95)'),
  ('admin.button.add_enum_value', 'admin', 'button', NULL,
   'группа «Перечисления»: кнопка нового значения (V95)'),
  ('admin.title.new_enum_value', 'admin', 'title', NULL,
   'окно значения перечисления: заголовок нового (V95)'),
  ('admin.title.edit_enum_value', 'admin', 'title', NULL,
   'окно значения перечисления: заголовок правки (V95)'),
  ('admin.title.enum_translations', 'admin', 'title', NULL,
   'окно значения перечисления: блок переводов (V95)'),
  ('admin.hint.enum_translations', 'admin', 'hint', NULL,
   'окно значения перечисления: пустой перевод — покажется основное имя (V95)'),
  ('admin.hint.enum_no_languages', 'admin', 'hint', NULL,
   'окно значения перечисления: включённых языков кроме основного нет (V95)'),
  ('admin.hint.enum_system_value', 'admin', 'hint', NULL,
   'окно значения перечисления: почему код не меняется (V95)'),
  ('admin.hint.enum_code_in_use', 'admin', 'hint', NULL,
   'окно значения перечисления: код занятого значения не меняется (V95)'),
  ('admin.hint.enum_delete_in_use', 'admin', 'hint', NULL,
   'окно значения перечисления: почему нельзя удалить (V95)'),
  ('admin.label.base_name', 'admin', 'label', NULL,
   'окно значения перечисления: основное (английское) имя (V95)'),
  ('admin.label.currency_alpha_code', 'admin', 'label', NULL,
   'окно значения перечисления: буквенный код валюты (V95)'),
  ('admin.label.enum_active', 'admin', 'label', NULL,
   'окно значения перечисления: галочка «действует» (V95)'),
  ('admin.label.enum_symbol', 'admin', 'label', NULL,
   'окно значения перечисления: обозначение единицы на языке (V95)'),
  ('admin.column.enum_usage', 'admin', 'column', NULL,
   'группа «Перечисления»: сколько записей ссылается на значение (V95)'),
  ('admin.text.system_value', 'admin', 'text', NULL,
   'группа «Перечисления»: отметка значения, на которое опирается программа (V95)'),
  ('admin.text.untranslated_count', 'admin', 'text', NULL,
   'группа «Перечисления»: сколько значений без перевода (V95)'),
  ('admin.confirm.delete_enum_value', 'admin', 'confirm', NULL,
   'группа «Перечисления»: подтверждение удаления (V95)'),
  ('error.enum.code_required', 'server', 'message', 'admin',
   'сообщение сервера: у значения нет кода (V95)'),
  ('error.enum.name_required', 'server', 'message', 'admin',
   'сообщение сервера: у значения нет основного имени (V95)'),
  ('error.enum.extra_required', 'server', 'message', 'admin',
   'сообщение сервера: не заполнен второй код — обозначение или буквенный код (V95)'),
  ('error.enum.code_format', 'server', 'message', 'admin',
   'сообщение сервера: код записан не так, как у остальных значений (V95)'),
  ('error.enum.code_taken', 'server', 'message', 'admin',
   'сообщение сервера: такой код уже есть (V95)'),
  ('error.enum.code_locked', 'server', 'message', 'admin',
   'сообщение сервера: код системного или используемого значения не меняется (V95)'),
  ('error.enum.system_value', 'server', 'message', 'admin',
   'сообщение сервера: системное значение нельзя удалить или отключить (V95)'),
  ('error.enum.in_use', 'server', 'message', 'admin',
   'сообщение сервера: на значение ссылаются — удалить нельзя (V95)'),
  ('error.usage.users', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: пользователях (V95)'),
  ('error.usage.invoices', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: счетах (V95)'),
  ('error.usage.excel_imports', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: журнале загрузок Excel (V95)'),
  ('error.usage.sync_runs', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: журнале обмена с WMS (V95)'),
  ('error.usage.document_lines', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: строках документов (V95)'),
  ('error.usage.photos', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: фотографиях (V95)'),
  ('error.usage.settings', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: настройках портала (V95)'),
  ('error.usage.other', 'server', 'message', 'admin',
   'часть сообщения «используется в …»: прочих записях (V95)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.hint.enum_values', 'ru',
     'Короткие списки значений с кодом: статусы, типы, единицы, валюты. Основное имя — английское; переводы на включённые языки вносятся в окне значения'),
    ('admin.hint.enum_values', 'en',
     'Short lists of coded values: statuses, types, units, currencies. The base name is English; translations into enabled languages are entered in the value window'),
    ('admin.button.add_enum_value', 'ru', '+ Добавить значение'),
    ('admin.button.add_enum_value', 'en', '+ Add value'),
    ('admin.title.new_enum_value', 'ru', 'Новое значение'),
    ('admin.title.new_enum_value', 'en', 'New value'),
    ('admin.title.edit_enum_value', 'ru', 'Значение перечисления'),
    ('admin.title.edit_enum_value', 'en', 'Enumeration value'),
    ('admin.title.enum_translations', 'ru', 'Переводы'),
    ('admin.title.enum_translations', 'en', 'Translations'),
    ('admin.hint.enum_translations', 'ru', 'Пустое поле — перевода нет, покажется основное имя'),
    ('admin.hint.enum_translations', 'en', 'An empty field means no translation: the base name is shown'),
    ('admin.hint.enum_no_languages', 'ru',
     'Кроме основного, включённых языков нет. Языки включаются в «Общих справочниках → Языки»'),
    ('admin.hint.enum_no_languages', 'en',
     'No languages are enabled besides the base one. Languages are enabled in Shared directories → Languages'),
    ('admin.hint.enum_system_value', 'ru',
     'На код этого значения опирается программа: код не меняется, значение не отключается и не удаляется. Имя и переводы править можно'),
    ('admin.hint.enum_system_value', 'en',
     'The program relies on this value''s code: the code cannot change and the value cannot be disabled or deleted. The name and translations can be edited'),
    ('admin.hint.enum_code_in_use', 'ru', 'Значение используется — код не меняется'),
    ('admin.hint.enum_code_in_use', 'en', 'The value is in use, so its code cannot change'),
    ('admin.hint.enum_delete_in_use', 'ru', 'Используется в записях ({count}) — удалить нельзя, можно отключить'),
    ('admin.hint.enum_delete_in_use', 'en', 'Used in records ({count}): it cannot be deleted, but can be disabled'),
    ('admin.label.base_name', 'ru', 'Основное имя (английский)'),
    ('admin.label.base_name', 'en', 'Base name (English)'),
    ('admin.label.currency_alpha_code', 'ru', 'Буквенный код'),
    ('admin.label.currency_alpha_code', 'en', 'Alphabetic code'),
    ('admin.label.enum_active', 'ru', 'Действует'),
    ('admin.label.enum_active', 'en', 'Active'),
    ('admin.label.enum_symbol', 'ru', 'Обозначение'),
    ('admin.label.enum_symbol', 'en', 'Symbol'),
    ('admin.column.enum_usage', 'ru', 'Используется'),
    ('admin.column.enum_usage', 'en', 'In use'),
    ('admin.text.system_value', 'ru', 'системное'),
    ('admin.text.system_value', 'en', 'system'),
    ('admin.text.untranslated_count', 'ru', 'без перевода: {count}'),
    ('admin.text.untranslated_count', 'en', 'untranslated: {count}'),
    ('admin.confirm.delete_enum_value', 'ru', 'Удалить значение «{name}»?'),
    ('admin.confirm.delete_enum_value', 'en', 'Delete the value “{name}”?'),
    ('error.enum.code_required', 'ru', 'Заполните код'),
    ('error.enum.code_required', 'en', 'Enter the code'),
    ('error.enum.name_required', 'ru', 'Заполните основное имя'),
    ('error.enum.name_required', 'en', 'Enter the base name'),
    ('error.enum.extra_required', 'ru', 'Заполните поле «{field}»'),
    ('error.enum.extra_required', 'en', 'Fill in “{field}”'),
    ('error.enum.code_format', 'ru', 'Код записывается так же, как у остальных значений, например «{example}»'),
    ('error.enum.code_format', 'en', 'Write the code the same way as the other values, for example “{example}”'),
    ('error.enum.code_taken', 'ru', 'Значение с кодом «{code}» уже есть'),
    ('error.enum.code_taken', 'en', 'A value with the code “{code}” already exists'),
    ('error.enum.code_locked', 'ru', 'Код этого значения не меняется: на него опирается программа или ссылаются записи'),
    ('error.enum.code_locked', 'en', 'This value''s code cannot change: the program relies on it or records refer to it'),
    ('error.enum.system_value', 'ru', 'На это значение опирается программа — его нельзя отключить или удалить'),
    ('error.enum.system_value', 'en', 'The program relies on this value: it cannot be disabled or deleted'),
    ('error.enum.in_use', 'ru', 'Значение используется в {where}. Его можно отключить, но не удалить'),
    ('error.enum.in_use', 'en', 'The value is used in {where}. It can be disabled but not deleted'),
    ('error.usage.users', 'ru', 'пользователях ({count})'),
    ('error.usage.users', 'en', 'users ({count})'),
    ('error.usage.invoices', 'ru', 'счетах ({count})'),
    ('error.usage.invoices', 'en', 'invoices ({count})'),
    ('error.usage.excel_imports', 'ru', 'журнале загрузок Excel ({count})'),
    ('error.usage.excel_imports', 'en', 'the Excel import log ({count})'),
    ('error.usage.sync_runs', 'ru', 'журнале обмена с WMS ({count})'),
    ('error.usage.sync_runs', 'en', 'the WMS exchange log ({count})'),
    ('error.usage.document_lines', 'ru', 'строках документов ({count})'),
    ('error.usage.document_lines', 'en', 'document lines ({count})'),
    ('error.usage.photos', 'ru', 'фотографиях ({count})'),
    ('error.usage.photos', 'en', 'photos ({count})'),
    ('error.usage.settings', 'ru', 'настройках портала ({count})'),
    ('error.usage.settings', 'en', 'portal settings ({count})'),
    ('error.usage.other', 'ru', 'других записях ({count})'),
    ('error.usage.other', 'en', 'other records ({count})')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Сообщения прежних вкладок: их место заняли общие error.enum.*
DELETE FROM ui_text WHERE text_key IN (
  'error.directory.code_two_digits', 'error.directory.code_and_name_required',
  'error.order_type.code_taken', 'error.order_type.in_use', 'error.order_type.not_found',
  'error.receipt_doc_type.code_taken', 'error.receipt_doc_type.in_use',
  'error.receipt_doc_type.not_found',
  'error.unit.symbol_and_name_required', 'error.unit.mkei_required', 'error.unit.duplicate',
  'error.unit.symbol_taken', 'error.unit.mkei_taken', 'error.unit.not_found',
  'error.unit.in_use', 'error.access.units');
DELETE FROM ui_text_key WHERE text_key IN (
  'error.directory.code_two_digits', 'error.directory.code_and_name_required',
  'error.order_type.code_taken', 'error.order_type.in_use', 'error.order_type.not_found',
  'error.receipt_doc_type.code_taken', 'error.receipt_doc_type.in_use',
  'error.receipt_doc_type.not_found',
  'error.unit.symbol_and_name_required', 'error.unit.mkei_required', 'error.unit.duplicate',
  'error.unit.symbol_taken', 'error.unit.mkei_taken', 'error.unit.not_found',
  'error.unit.in_use', 'error.access.units');

-- Подписи убранных вкладок «Единицы измерения», «Типы заказов»,
-- «Типы поступлений»: больше нигде не показываются. Обозначение
-- единицы (references.column.unit_symbol) остаётся — это подпись
-- второго кода в окне значения (enum_registry.extra_label_key).
DELETE FROM ui_text WHERE text_key IN (
  'references.button.units', 'references.column.mkei_code', 'references.hint.units',
  'references.title.units', 'references.title.order_types',
  'references.title.expected_document_types');
DELETE FROM ui_text_key WHERE text_key IN (
  'references.button.units', 'references.column.mkei_code', 'references.hint.units',
  'references.title.units', 'references.title.order_types',
  'references.title.expected_document_types');

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  n int;
  r record;
BEGIN
  -- Каждое существующее значение записано по своему образцу: иначе
  -- образец отказал бы при первой же правке имени
  FOR r IN SELECT * FROM enum_registry WHERE code_pattern IS NOT NULL LOOP
    EXECUTE format('SELECT count(*) FROM %I WHERE NOT (%I::text ~ $1)', r.enum_key, r.code_column)
       INTO n USING r.code_pattern;
    IF n > 0 THEN
      RAISE EXCEPTION 'V95: в % коды не по образцу %', r.enum_key, r.code_pattern;
    END IF;
  END LOOP;
  FOR r IN SELECT * FROM enum_registry WHERE extra_pattern IS NOT NULL LOOP
    EXECUTE format('SELECT count(*) FROM %I WHERE NOT (%I::text ~ $1)', r.enum_key, r.extra_column)
       INTO n USING r.extra_pattern;
    IF n > 0 THEN
      RAISE EXCEPTION 'V95: в % второй код не по образцу %', r.enum_key, r.extra_pattern;
    END IF;
  END LOOP;

  SELECT count(*) INTO n FROM enum_value_usage();
  IF n = 0 THEN
    RAISE EXCEPTION 'V95: enum_value_usage() не нашла ни одной ссылки — внешние ключи не прочитаны';
  END IF;
END;
$check$;
