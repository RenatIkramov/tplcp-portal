-- V35__depositor_field_visibility.sql
-- Настройка видимости полей для поклажедателя: экран в разделе
-- «Доступы поклажедателей», разбитый по вкладкам карточки товара.
--
-- Что здесь делается:
--   1. group_key у колонки — по какой вкладке её показывать;
--   2. блокировка трёх обязательных полей товара (их нельзя спрятать);
--   3. запрет снять доступность у заблокированной колонки — на сервере,
--      а не только серой галочкой на экране;
--   4. таблица depositor_settings — настройки поклажедателя по одному
--      значению на клиента;
--   5. «Показать раздел перемещения» переезжает с пользователя на
--      поклажедателя, колонка user_settings.hide_transfers_tab удаляется;
--   6. ключи текстов для новых экранов и вкладок биллинга.
--
-- ВАЖНО о порядке. DROP COLUMN не выполнится, пока на колонку
-- ссылаются функции с RETURNS TABLE (admin_users) и телом, читающим её
-- (admin_save_user, user_settings_copy). Поэтому сперва переопределяем
-- функции, и только потом удаляем колонку. Наоборот — миграция упала бы
-- на первом же применении.
SET search_path = dbo;

-- =====================================================================
-- 1. Группа колонки: по какой вкладке её показывать
-- =====================================================================
-- Разбивка берётся из карточки товара, а не придумывается заново:
-- иначе появился бы третий список полей, который разойдётся с первыми
-- двумя. Ссылка на ui_text_key — чтобы название вкладки переводилось
-- тем же механизмом и теми же ключами, что и вкладка карточки.
ALTER TABLE table_column
  ADD COLUMN IF NOT EXISTS group_key varchar(80) REFERENCES ui_text_key (text_key);
COMMENT ON COLUMN table_column.group_key IS
  'Вкладка, на которой колонка показывается в настройках. Ключ текста — как у вкладки карточки';

-- Колонки товара по вкладкам карточки. Поля, которых в карточке нет
-- (GUID, кто создал, когда изменено, есть ли упаковки), разложены по
-- смыслу: служебные — в системную информацию, признаки — к признакам.
WITH g(group_key, ids) AS (VALUES
  -- sku.text.general_information: 10
  ('sku.text.general_information', ARRAY['barcode', 'code', 'group', 'itemCode', 'itemFullName', 'name', 'orderUnit', 'productType', 'statusProduct', 'unit']),
  -- sku.text.physical_properties: 8
  ('sku.text.physical_properties', ARRAY['color', 'height', 'length', 'size', 'volume', 'weightGross', 'weightNet', 'width']),
  -- sku.text.storage_conditions: 9
  ('sku.text.storage_conditions', ARRAY['hazardClass', 'hazardGroup', 'maxHumidity', 'maxStorageTemp', 'minDaysForReceipt', 'minDaysForReservation', 'minHumidity', 'minStorageTemp', 'shelfLifeDays']),
  -- sku.text.manufacturer_and_classification: 5
  ('sku.text.manufacturer_and_classification', ARRAY['abcClass', 'brand', 'countryOrigin', 'manufacturer', 'xyzClass']),
  -- sku.text.codes_and_identifiers: 5
  ('sku.text.codes_and_identifiers', ARRAY['cost', 'currency', 'enstruCode', 'guid', 'hsCode']),
  -- sku.text.attributes_and_tracking: 10
  ('sku.text.attributes_and_tracking', ARRAY['batchTracking', 'expiryTracking', 'hasPackaging', 'isControlled', 'isExcise', 'isFragile', 'isKit', 'isMarkingRequired', 'isSerialTracked', 'isWmsActive']),
  -- sku.text.system_information: 5
  ('sku.text.system_information', ARRAY['comment', 'createdAt', 'createdBy', 'updatedAt', 'updatedBy']),
  -- sku.text.custom_attributes: 10
  ('sku.text.custom_attributes', ARRAY['customAttr1', 'customAttr10', 'customAttr2', 'customAttr3', 'customAttr4', 'customAttr5', 'customAttr6', 'customAttr7', 'customAttr8', 'customAttr9'])
)
UPDATE table_column c
   SET group_key = g.group_key
  FROM g
 WHERE c.table_key = 'sku'
   AND c.column_id = ANY (g.ids);

-- Проверка на месте: если в каталоге появилась колонка товара, которой
-- не нашлось вкладки, экран показал бы её в «прочем» или потерял вовсе.
-- Лучше узнать об этом при миграции.
DO $$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(column_id, ', ' ORDER BY column_id) INTO v_missing
  FROM table_column WHERE table_key = 'sku' AND group_key IS NULL;
  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'У колонок товара нет вкладки: %', v_missing;
  END IF;
END;
$$;

-- =====================================================================
-- 2. Обязательные поля товара спрятать нельзя
-- =====================================================================
-- Ровно те три, что проверяет сервер при записи товара
-- (SkuColumns.SKU_REQUIRED): коммерческий код, наименование, базовая
-- единица измерения. Список обязательных должен быть один, иначе экран
-- разрешит спрятать поле, без которого товар не сохранится.
--
-- Следствие, которое заметят пользователи: is_locked учитывается и в
-- личном выборе колонок (table_columns принудительно показывает такие),
-- то есть код и наименование станут неотключаемыми и в ⚙ Колонки.
-- Это сознательно: список товаров без кода и наименования бесполезен.
UPDATE table_column
   SET is_locked = true
 WHERE table_key = 'sku'
   AND column_id IN ('code', 'name', 'unit');

-- =====================================================================
-- 3. Нельзя снять доступность у заблокированной колонки
-- =====================================================================
-- Серая галочка на экране — удобство, а не защита: запрос уходит от
-- браузера, и браузеру верить нельзя (§6.3 про то же самое).
DROP FUNCTION IF EXISTS admin_depositor_column_save(uuid, text, text, boolean, boolean);
CREATE FUNCTION admin_depositor_column_save(p_depositor_id uuid, p_table_key text,
                                            p_column_id text, p_is_available boolean,
                                            p_is_default boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  v_portal_default boolean;
  v_locked         boolean;
BEGIN
  SELECT is_default, is_locked INTO v_portal_default, v_locked
  FROM table_column
  WHERE table_key = p_table_key AND column_id = p_column_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Колонки % в таблице % нет', p_column_id, p_table_key;
  END IF;

  IF v_locked AND NOT p_is_available THEN
    RAISE EXCEPTION 'Поле «%» обязательное, его нельзя скрыть от поклажедателя',
      p_column_id USING ERRCODE = 'check_violation';
  END IF;

  IF p_is_available AND p_is_default IS NOT DISTINCT FROM v_portal_default THEN
    DELETE FROM depositor_table_column
    WHERE depositor_id = p_depositor_id
      AND table_key = p_table_key AND column_id = p_column_id;
    RETURN;
  END IF;

  INSERT INTO depositor_table_column
    (depositor_id, table_key, column_id, is_available, is_default)
  VALUES (p_depositor_id, p_table_key, p_column_id, p_is_available, p_is_default)
  ON CONFLICT (depositor_id, table_key, column_id) DO UPDATE SET
    is_available = excluded.is_available,
    is_default   = excluded.is_default;
END;
$$;
GRANT EXECUTE ON FUNCTION
  admin_depositor_column_save(uuid, text, text, boolean, boolean) TO portal_app;

-- Экрану нужна вкладка и признак блокировки, поэтому набор колонок
-- в ответе меняется — функцию приходится пересоздавать.
DROP FUNCTION IF EXISTS admin_depositor_columns(uuid, text, text);
CREATE FUNCTION admin_depositor_columns(p_depositor_id uuid, p_locale text,
                                        p_table_key text DEFAULT NULL)
RETURNS TABLE (
  table_key     varchar(40),
  column_id     varchar(40),
  label         text,
  portal_label  text,
  group_key     varchar(80),
  group_label   text,
  is_available  boolean,
  is_default    boolean,
  is_locked     boolean,
  sort_order    smallint
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo
AS $$
  WITH texts AS (SELECT t.text_key, t.value FROM ui_texts(p_locale) t)
  SELECT
    c.table_key,
    c.column_id,
    COALESCE(lbl.custom_label, t.value, c.text_key)::text AS label,
    COALESCE(t.value, c.text_key)::text                   AS portal_label,
    c.group_key,
    COALESCE(gt.value, c.group_key)::text                 AS group_label,
    COALESCE(d.is_available, true)                        AS is_available,
    COALESCE(d.is_default, c.is_default)                  AS is_default,
    c.is_locked,
    COALESCE(d.sort_order, c.sort_order)::smallint        AS sort_order
  FROM table_column c
  LEFT JOIN depositor_table_column d
         ON d.depositor_id = p_depositor_id
        AND d.table_key = c.table_key AND d.column_id = c.column_id
  LEFT JOIN depositor_field_label lbl
         ON lbl.depositor_id   = p_depositor_id
        AND lbl.object_type_id = c.object_type_id
        AND lbl.field_id       = c.field_id
  LEFT JOIN texts t  ON t.text_key = c.text_key
  LEFT JOIN texts gt ON gt.text_key = c.group_key
  WHERE p_table_key IS NULL OR c.table_key = p_table_key
  ORDER BY c.table_key, 10, c.column_id;
$$;
GRANT EXECUTE ON FUNCTION admin_depositor_columns(uuid, text, text) TO portal_app;

-- =====================================================================
-- 4. Настройки поклажедателя
-- =====================================================================
-- Отдельная таблица, как user_settings у пользователя: справочник
-- организаций и настройки поведения портала — разные вещи с разной
-- частотой правок, и настроек тут со временем станет больше.
--
-- Списками эта таблица не занимается: склады лежат в
-- depositor_warehouse, поля — в depositor_table_column. Там на клиента
-- много строк, в колонку такое не положишь.
CREATE TABLE IF NOT EXISTS depositor_settings (
  depositor_id       uuid    PRIMARY KEY REFERENCES depositor (depositor_id),
  hide_transfers_tab boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE depositor_settings IS
  'Настройки портала для поклажедателя. Одно значение на клиента; списки — в своих таблицах';
COMMENT ON COLUMN depositor_settings.hide_transfers_tab IS
  'Скрыть раздел «Перемещения» у всех пользователей этого поклажедателя';

GRANT SELECT ON depositor_settings TO portal_app;

CREATE OR REPLACE FUNCTION depositor_settings_of(p_depositor_id uuid)
RETURNS TABLE (hide_transfers_tab boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT COALESCE(s.hide_transfers_tab, false)
  FROM depositor d
  LEFT JOIN depositor_settings s ON s.depositor_id = d.depositor_id
  WHERE d.depositor_id = p_depositor_id;
$$;
GRANT EXECUTE ON FUNCTION depositor_settings_of(uuid) TO portal_app;

CREATE OR REPLACE FUNCTION admin_save_depositor_settings(p_depositor_id uuid,
                                                         p_hide_transfers_tab boolean)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM depositor WHERE depositor_id = p_depositor_id) THEN
    RAISE EXCEPTION 'Поклажедателя % нет', p_depositor_id;
  END IF;

  INSERT INTO depositor_settings (depositor_id, hide_transfers_tab)
  VALUES (p_depositor_id, COALESCE(p_hide_transfers_tab, false))
  ON CONFLICT (depositor_id) DO UPDATE SET
    hide_transfers_tab = excluded.hide_transfers_tab;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_save_depositor_settings(uuid, boolean) TO portal_app;

-- =====================================================================
-- 5. «Показать раздел перемещения» уезжает с пользователя
-- =====================================================================
-- Настройка становится настройкой клиента, а не человека: значения
-- пользователей не переносим — так решено, они ничего не стоят.
-- Сперва функции, потом колонка (см. замечание о порядке в начале).

DROP FUNCTION IF EXISTS admin_users();
CREATE FUNCTION admin_users()
RETURNS TABLE (user_id text, login varchar, full_name varchar, role_code varchar,
               role_name varchar, user_position varchar, organization varchar, phone varchar,
               is_active boolean, must_change_password boolean,
               last_login_at timestamp, password_changed_at timestamptz,
               scope text, depositor_codes text,
               hide_billing_tab boolean,
               hide_services_tab boolean, can_create_receipts boolean,
               can_create_orders boolean, can_create_transfers boolean,
               can_upload_sku_excel boolean, can_upload_packaging_excel boolean,
               can_edit_transport boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.user_id::text, u.login, u.full_name, r.code, r.name,
         u.position, u.organization, u.phone, u.is_active, u.must_change_password,
         u.last_login_at, u.password_changed_at,
         CASE r.code
           WHEN 'depositor' THEN COALESCE(u.organization, '—')
           WHEN 'staff'     THEN COALESCE((SELECT string_agg(w.name, ', ' ORDER BY w.name)
                                             FROM user_warehouse_access a
                                             JOIN warehouse w ON w.warehouse_id = a.warehouse_id
                                            WHERE a.user_id = u.user_id), 'Склады не назначены')
           WHEN 'manager'   THEN COALESCE((SELECT string_agg(d.org_name, ', ' ORDER BY d.org_name)
                                             FROM user_depositor_access a
                                             JOIN depositor d ON d.depositor_id = a.depositor_id
                                            WHERE a.user_id = u.user_id), 'Поклажедатели не назначены')
           ELSE 'Весь портал'
         END AS scope,
         COALESCE((SELECT string_agg(d.code, ', ' ORDER BY d.code)
                     FROM user_depositor_access a
                     JOIN depositor d ON d.depositor_id = a.depositor_id
                    WHERE a.user_id = u.user_id), '') AS depositor_codes,
         COALESCE(s.hide_billing_tab, false),
         COALESCE(s.hide_services_tab, false), COALESCE(s.can_create_receipts, true),
         COALESCE(s.can_create_orders, true), COALESCE(s.can_create_transfers, true),
         COALESCE(s.can_upload_sku_excel, true), COALESCE(s.can_upload_packaging_excel, true),
         COALESCE(s.can_edit_transport, true)
  FROM app_user u
  LEFT JOIN enum_user_role r ON r.id = u.role_id
  LEFT JOIN user_settings s ON s.user_id = u.user_id
  ORDER BY r.sort_order, u.full_name
$$;
GRANT EXECUTE ON FUNCTION admin_users() TO portal_app;

-- Прежняя функция сохранения принимала признак перемещений вторым
-- булевым параметром. Набор параметров меняется, поэтому старую
-- удаляем: иначе в базе остались бы две перегрузки, и вызов с девятью
-- аргументами попал бы в старую, которая читает удалённую колонку.
DROP FUNCTION IF EXISTS admin_save_user(uuid, boolean, boolean, boolean, boolean,
    boolean, boolean, boolean, boolean, boolean, boolean);
CREATE FUNCTION admin_save_user(
    p_user_id uuid,
    p_is_active boolean,
    p_hide_billing_tab boolean, p_hide_services_tab boolean,
    p_can_create_receipts boolean, p_can_create_orders boolean, p_can_create_transfers boolean,
    p_can_upload_sku_excel boolean, p_can_upload_packaging_excel boolean,
    p_can_edit_transport boolean)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  UPDATE app_user SET is_active = p_is_active WHERE user_id = p_user_id;

  INSERT INTO user_settings (user_id, hide_billing_tab,
      hide_services_tab, can_create_receipts, can_create_orders, can_create_transfers,
      can_upload_sku_excel, can_upload_packaging_excel, can_edit_transport)
  VALUES (p_user_id, p_hide_billing_tab, p_hide_services_tab,
      p_can_create_receipts, p_can_create_orders, p_can_create_transfers,
      p_can_upload_sku_excel, p_can_upload_packaging_excel, p_can_edit_transport)
  ON CONFLICT (user_id) DO UPDATE SET
      hide_billing_tab = EXCLUDED.hide_billing_tab,
      hide_services_tab = EXCLUDED.hide_services_tab,
      can_create_receipts = EXCLUDED.can_create_receipts,
      can_create_orders = EXCLUDED.can_create_orders,
      can_create_transfers = EXCLUDED.can_create_transfers,
      can_upload_sku_excel = EXCLUDED.can_upload_sku_excel,
      can_upload_packaging_excel = EXCLUDED.can_upload_packaging_excel,
      can_edit_transport = EXCLUDED.can_edit_transport;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_save_user(uuid, boolean, boolean, boolean, boolean,
    boolean, boolean, boolean, boolean, boolean) TO portal_app;

-- Копирование настроек между пользователями: признака перемещений
-- в списке больше нет.
CREATE OR REPLACE FUNCTION user_settings_copy(p_from uuid, p_to uuid)
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  IF p_from = p_to THEN
    RETURN;
  END IF;

  INSERT INTO user_settings AS s (user_id, hide_billing_tab,
      hide_services_tab, can_create_receipts, can_create_orders,
      can_create_transfers, can_upload_sku_excel, can_upload_packaging_excel,
      can_edit_transport)
  SELECT p_to, f.hide_billing_tab, f.hide_services_tab,
         f.can_create_receipts, f.can_create_orders, f.can_create_transfers,
         f.can_upload_sku_excel, f.can_upload_packaging_excel, f.can_edit_transport
  FROM user_settings f
  WHERE f.user_id = p_from
  ON CONFLICT (user_id) DO UPDATE SET
    hide_billing_tab           = excluded.hide_billing_tab,
    hide_services_tab          = excluded.hide_services_tab,
    can_create_receipts        = excluded.can_create_receipts,
    can_create_orders          = excluded.can_create_orders,
    can_create_transfers       = excluded.can_create_transfers,
    can_upload_sku_excel       = excluded.can_upload_sku_excel,
    can_upload_packaging_excel = excluded.can_upload_packaging_excel,
    can_edit_transport         = excluded.can_edit_transport;

  -- Колонки переносятся целиком: и выбор, и порядок. Прежний выбор
  -- получателя стирается, иначе получилась бы смесь двух раскладок.
  DELETE FROM user_table_column WHERE user_id = p_to;
  INSERT INTO user_table_column (user_id, table_key, column_id, sort_order)
  SELECT p_to, table_key, column_id, sort_order
  FROM user_table_column WHERE user_id = p_from;
END;
$$;

-- Теперь колонку ничто не читает.
ALTER TABLE user_settings DROP COLUMN IF EXISTS hide_transfers_tab;

-- =====================================================================
-- 6. Тексты новых экранов
-- =====================================================================
-- Как и в V30: заполняем русский и английский, остальные языки
-- добираются отдельным шагом вместе с переводом справочников.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.tab.sections_and_warehouses',  'admin', 'tab',     NULL,
   'карточка доступов поклажедателя'),
  ('admin.tab.product_fields',           'admin', 'tab',     NULL,
   'карточка доступов поклажедателя'),
  ('admin.label.show_transfers_section', 'admin', 'label',   NULL,
   'настройка поклажедателя, прежде была у пользователя'),
  ('admin.hint.transfers_section',       'admin', 'hint',    NULL,
   'карточка доступов поклажедателя'),
  ('admin.hint.product_fields',          'admin', 'hint',    NULL,
   'карточка доступов поклажедателя'),
  ('admin.text.required_field_locked',   'admin', 'text',    NULL,
   'подпись у заблокированной галочки обязательного поля'),
  ('admin.message.fields_updated',       'admin', 'message', NULL,
   'карточка доступов поклажедателя'),
  ('admin.message.settings_updated',     'admin', 'message', NULL,
   'карточка доступов поклажедателя'),
  ('admin.title.sections',               'admin', 'title',   NULL,
   'заголовок блока настроек разделов'),
  ('admin.hint.depositor_warehouses',    'admin', 'hint',    NULL,
   'пояснение к галочкам складов поклажедателя'),
  ('common.text.list_is_empty',          'common', 'text',   NULL,
   'подпись вместо пустого списка галочек'),
  ('billing.tab.reports',                'billing', 'tab',   NULL, 'вкладки раздела биллинга'),
  ('billing.tab.services_and_rates',     'billing', 'tab',   NULL, 'вкладки раздела биллинга'),
  ('billing.tab.invoices',               'billing', 'tab',   NULL, 'вкладки раздела биллинга')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('admin.tab.sections_and_warehouses',  'ru', 'Разделы и склады'),
  ('admin.tab.sections_and_warehouses',  'en', 'Sections and warehouses'),
  ('admin.tab.product_fields',           'ru', 'Поля товаров'),
  ('admin.tab.product_fields',           'en', 'Product fields'),
  ('admin.label.show_transfers_section', 'ru', 'Показать раздел «Перемещения»'),
  ('admin.label.show_transfers_section', 'en', 'Show the Transfers section'),
  ('admin.hint.transfers_section',       'ru',
     'Настройка действует на всех пользователей этого поклажедателя.'),
  ('admin.hint.transfers_section',       'en',
     'Applies to every user of this depositor.'),
  ('admin.hint.product_fields',          'ru',
     'Снятая галочка убирает поле у клиента везде: из карточки товара, из выбора колонок и из шаблона Excel.'),
  ('admin.hint.product_fields',          'en',
     'Clearing a box removes the field everywhere for this client: from the item card, from the column picker and from the Excel template.'),
  ('admin.text.required_field_locked',   'ru', 'обязательное поле'),
  ('admin.text.required_field_locked',   'en', 'required field'),
  ('admin.message.fields_updated',       'ru', 'Видимость полей обновлена'),
  ('admin.message.fields_updated',       'en', 'Field visibility updated'),
  ('admin.message.settings_updated',     'ru', 'Настройки поклажедателя обновлены'),
  ('admin.message.settings_updated',     'en', 'Depositor settings updated'),
  ('admin.title.sections',               'ru', 'Разделы'),
  ('admin.title.sections',               'en', 'Sections'),
  ('admin.hint.depositor_warehouses',    'ru',
     'Отмеченные склады поклажедатель видит в документах. Если не отмечен ни один — доступны все: так забытая настройка не оставит клиента без складов.'),
  ('admin.hint.depositor_warehouses',    'en',
     'The depositor sees the ticked warehouses in documents. None ticked means all are available, so a forgotten setting does not leave the client without warehouses.'),
  ('common.text.list_is_empty',          'ru', 'Список пуст'),
  ('common.text.list_is_empty',          'en', 'The list is empty'),
  ('billing.tab.reports',                'ru', 'Отчёты по биллингу'),
  ('billing.tab.reports',                'en', 'Billing reports'),
  ('billing.tab.services_and_rates',     'ru', 'Услуги и Тарифы'),
  ('billing.tab.services_and_rates',     'en', 'Services and rates'),
  ('billing.tab.invoices',               'ru', 'Счета'),
  ('billing.tab.invoices',               'en', 'Invoices')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
