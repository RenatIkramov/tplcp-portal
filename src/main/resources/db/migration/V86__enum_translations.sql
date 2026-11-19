-- ---------------------------------------------------------------------
-- V86. Перевод перечислений: механизм
-- ---------------------------------------------------------------------
-- Решения заказчика от 28–29.10.2026 (документ «Перечисления —
-- согласование списков»):
--   * перечисления (статусы, типы, единицы, роли…) переводятся в базе,
--     а не на экране: тогда перевод сразу работает в списках, карточках,
--     сортировке по названию, вариантах фильтров и шаблонах Excel;
--   * основной язык портала — английский: имя значения по умолчанию
--     (колонка name) английское, остальные языки — переводы;
--   * переводы ведутся на вкладке «Перечисления» (Администрирование →
--     «Тексты интерфейса»); правка из карточки справочника — позже;
--   * пустая таблица translation (V1) удаляется: она не использовалась
--     и для этой задачи не подходит.
--
-- Этот шаг — только механизм. Сами списки (статусы, типы, валюты, ДОПОГ,
-- коды) меняются следующими миграциями. Английские имена с русским
-- переводом здесь получают перечисления, списки которых не меняются:
-- роли, типы объектов, типы упаковки, единицы измерения.
--
-- КАК РАБОТАЕТ. Портал при каждом получении соединения из пула пишет
-- в сессию язык пользователя: set_config('app.locale', 'ru', false)
-- (LocaleDataSource). Функция enum_name() отдаёт перевод на этот язык,
-- а если его нет — основное имя. Представления зовут её вместо name.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Пустая таблица translation — удаляется
-- ---------------------------------------------------------------------
DO $drop$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
              WHERE table_schema = 'dbo' AND table_name = 'translation') THEN
    IF EXISTS (SELECT 1 FROM translation) THEN
      RAISE EXCEPTION 'в translation есть строки — удалять её вслепую нельзя';
    END IF;
    DROP TABLE translation;
  END IF;
END
$drop$;

-- ---------------------------------------------------------------------
-- 2. Какие перечисления переводятся
-- ---------------------------------------------------------------------
-- Список, а не «все таблицы enum_*»: вкладка показывает именно его,
-- и функции проверяют имя перечисления по нему — имя таблицы
-- подставляется в динамический запрос, и выдумывать его нельзя.
CREATE TABLE IF NOT EXISTS enum_registry (
  enum_key     varchar(40)  PRIMARY KEY,
  code_column  varchar(40)  NOT NULL,
  has_symbol   boolean      NOT NULL DEFAULT false,
  text_key     varchar(80)  NOT NULL,
  sort_order   smallint     NOT NULL
);
COMMENT ON TABLE enum_registry IS
  'перечисления, которые переводятся: таблица, колонка кода, есть ли обозначение (V86)';

INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order) VALUES
  ('enum_status',           'code',      false, 'enums.name.enum_status',           10),
  ('enum_order_type',       'code',      false, 'enums.name.enum_order_type',       20),
  ('enum_receipt_doc_type', 'code',      false, 'enums.name.enum_receipt_doc_type', 30),
  ('enum_uom',              'mkei_code', true,  'enums.name.enum_uom',              40),
  ('enum_currency',         'code',      false, 'enums.name.enum_currency',         50),
  ('enum_packaging_type',   'code',      false, 'enums.name.enum_packaging_type',   60),
  ('enum_block_reason',     'code',      false, 'enums.name.enum_block_reason',     70),
  ('enum_hazard_class',     'code',      false, 'enums.name.enum_hazard_class',     80),
  ('enum_hazard_group',     'code',      false, 'enums.name.enum_hazard_group',     90),
  ('enum_user_role',        'code',      false, 'enums.name.enum_user_role',       100),
  ('enum_object_type',      'code',      false, 'enums.name.enum_object_type',     110)
ON CONFLICT (enum_key) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Переводы
-- ---------------------------------------------------------------------
-- value_id — идентификатор значения текстом: у разных перечислений id
-- разных типов, а одна таблица на все удобнее и вкладке, и функциям.
-- symbol — обозначение (только у единиц: «шт» → «pcs»).
CREATE TABLE IF NOT EXISTS enum_text (
  enum_key   varchar(40)  NOT NULL REFERENCES enum_registry (enum_key),
  value_id   varchar(20)  NOT NULL,
  locale_id  smallint     NOT NULL REFERENCES dictionary_locale (locale_id),
  name       varchar(255),
  symbol     varchar(30),
  PRIMARY KEY (enum_key, value_id, locale_id)
);
COMMENT ON TABLE enum_text IS
  'переводы значений перечислений; нет перевода — показывается основное (английское) имя (V86)';
GRANT SELECT ON enum_registry TO portal_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON enum_text TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Функции
-- ---------------------------------------------------------------------
-- Язык — из сессии (app.locale). Пусто — значит основное имя.
CREATE OR REPLACE FUNCTION enum_name(p_enum text, p_id text, p_default text)
RETURNS text
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  SELECT COALESCE(
           (SELECT NULLIF(btrim(et.name), '')
              FROM enum_text et
              JOIN dictionary_locale l ON l.locale_id = et.locale_id
             WHERE et.enum_key = p_enum AND et.value_id = p_id
               AND l.code = NULLIF(current_setting('app.locale', true), '')),
           p_default)
$$;
COMMENT ON FUNCTION enum_name(text, text, text) IS
  'имя значения перечисления на языке сессии (app.locale), иначе основное (V86)';

CREATE OR REPLACE FUNCTION enum_symbol(p_enum text, p_id text, p_default text)
RETURNS text
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  SELECT COALESCE(
           (SELECT NULLIF(btrim(et.symbol), '')
              FROM enum_text et
              JOIN dictionary_locale l ON l.locale_id = et.locale_id
             WHERE et.enum_key = p_enum AND et.value_id = p_id
               AND l.code = NULLIF(current_setting('app.locale', true), '')),
           p_default)
$$;
COMMENT ON FUNCTION enum_symbol(text, text, text) IS
  'обозначение единицы на языке сессии (app.locale), иначе основное (V86)';

-- Все написания значения: основное и переводы на все языки. Нужно
-- загрузке Excel: файл, скачанный по-английски, загружает человек
-- с русским портал, и «pcs» должно найтись так же, как «шт».
CREATE OR REPLACE FUNCTION enum_aliases(p_enum text, p_id text, p_default text,
                                        p_symbol boolean)
RETURNS text[]
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  SELECT ARRAY(
    SELECT DISTINCT v FROM (
      SELECT p_default AS v
      UNION ALL
      SELECT CASE WHEN p_symbol THEN et.symbol ELSE et.name END
        FROM enum_text et
       WHERE et.enum_key = p_enum AND et.value_id = p_id
    ) a
    WHERE v IS NOT NULL AND btrim(v) <> '')
$$;

-- Таблица для вкладки «Перечисления»: каждое значение каждого
-- перечисления на каждом языке. Перечисления перебираются по
-- enum_registry — у них разные колонки кода и не у всех есть symbol.
CREATE OR REPLACE FUNCTION enum_text_matrix()
RETURNS TABLE (enum_key varchar, value_id text, code varchar, base_name varchar,
               base_symbol varchar, sort_order int, is_active boolean,
               locale_code varchar, name varchar, symbol varchar)
LANGUAGE plpgsql
STABLE
SET search_path = dbo
AS $$
DECLARE
  q text;
BEGIN
  SELECT string_agg(format(
           'SELECT %L::varchar AS enum_key, e.id::text AS value_id, e.%I::varchar AS code,'
           ' e.name::varchar AS base_name, %s AS base_symbol,'
           ' COALESCE(e.sort_order, 0)::int AS sort_order, COALESCE(e.is_active, true) AS is_active,'
           ' %s::int AS reg_order FROM %I e',
           r.enum_key, r.code_column,
           CASE WHEN r.has_symbol THEN 'e.symbol::varchar' ELSE 'NULL::varchar' END,
           r.sort_order, r.enum_key), ' UNION ALL ')
    INTO q
    FROM enum_registry r;

  RETURN QUERY EXECUTE
    'SELECT v.enum_key, v.value_id, v.code, v.base_name, v.base_symbol, v.sort_order,'
    ' v.is_active, l.code, et.name, et.symbol'
    ' FROM (' || q || ') v'
    ' CROSS JOIN dictionary_locale l'
    ' LEFT JOIN enum_text et ON et.enum_key = v.enum_key AND et.value_id = v.value_id'
    '                       AND et.locale_id = l.locale_id'
    ' ORDER BY v.reg_order, v.sort_order, v.value_id, l.sort_order';
END;
$$;
GRANT EXECUTE ON FUNCTION enum_text_matrix() TO portal_app;

-- Сохранить перевод. Пустые имя и обозначение — удалить перевод.
CREATE OR REPLACE FUNCTION enum_text_save(p_enum text, p_id text, p_locale text,
                                          p_name text, p_symbol text)
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
DECLARE
  v_locale smallint;
  v_exists boolean;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM enum_registry WHERE enum_key = p_enum) THEN
    RAISE EXCEPTION 'неизвестное перечисление: %', p_enum;
  END IF;
  SELECT locale_id INTO v_locale FROM dictionary_locale WHERE code = p_locale;
  IF v_locale IS NULL THEN
    RAISE EXCEPTION 'неизвестный язык: %', p_locale;
  END IF;
  EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE id::text = $1)', p_enum)
     INTO v_exists USING p_id;
  IF NOT v_exists THEN
    RAISE EXCEPTION 'в % нет значения %', p_enum, p_id;
  END IF;

  IF NULLIF(btrim(p_name), '') IS NULL AND NULLIF(btrim(p_symbol), '') IS NULL THEN
    DELETE FROM enum_text WHERE enum_key = p_enum AND value_id = p_id AND locale_id = v_locale;
  ELSE
    INSERT INTO enum_text (enum_key, value_id, locale_id, name, symbol)
    VALUES (p_enum, p_id, v_locale, NULLIF(btrim(p_name), ''), NULLIF(btrim(p_symbol), ''))
    ON CONFLICT (enum_key, value_id, locale_id)
    DO UPDATE SET name = excluded.name, symbol = excluded.symbol;
  END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION enum_name(text, text, text), enum_symbol(text, text, text),
                          enum_aliases(text, text, text, boolean),
                          enum_text_save(text, text, text, text, text) TO portal_app;

-- ---------------------------------------------------------------------
-- 5. Английские имена и русский перевод — перечисления, которые
--    дальше не меняются
-- ---------------------------------------------------------------------
-- Сначала прежнее (русское) имя уходит в перевод, потом основное
-- становится английским. Значения, которых нет в списке (заведённые
-- администратором), остаются как есть: их переведут на вкладке.
CREATE TEMP TABLE v86_en (enum_key text, code text, name text, symbol text) ON COMMIT DROP;
INSERT INTO v86_en VALUES
  ('enum_user_role', 'depositor', 'Depositor', NULL),
  ('enum_user_role', 'manager',   '3PL manager', NULL),
  ('enum_user_role', 'staff',     'Warehouse staff', NULL),
  ('enum_user_role', 'admin',     'Administrator', NULL),
  ('enum_user_role', 'developer', 'Developer', NULL),
  ('enum_object_type', 'receipts',  'Receipt', NULL),
  ('enum_object_type', 'orders',    'Order', NULL),
  ('enum_object_type', 'transfers', 'Transfer', NULL),
  ('enum_object_type', 'sku',       'Item', NULL),
  ('enum_object_type', 'packaging', 'Packaging', NULL),
  ('enum_object_type', 'depositor', 'Depositor', NULL),
  ('enum_object_type', 'supplier',  'Supplier', NULL),
  ('enum_object_type', 'consignee', 'Consignee', NULL),
  ('enum_object_type', 'warehouse', 'Warehouse', NULL),
  ('enum_object_type', 'app_user',  'User', NULL),
  ('enum_object_type', 'stock',     'Stock', NULL),
  ('enum_packaging_type', 'unit',   'Unit', NULL),
  ('enum_packaging_type', 'box',    'Box', NULL),
  ('enum_packaging_type', 'pallet', 'Pallet', NULL),
  ('enum_packaging_type', 'bag',    'Bag', NULL),
  ('enum_uom', '796', 'Piece',        'pcs'),
  ('enum_uom', '163', 'Gram',         'g'),
  ('enum_uom', '166', 'Kilogram',     'kg'),
  ('enum_uom', '168', 'Tonne',        't'),
  ('enum_uom', '112', 'Litre',        'l'),
  ('enum_uom', '006', 'Metre',        'm'),
  ('enum_uom', '055', 'Square metre', 'm²'),
  ('enum_uom', '113', 'Cubic metre',  'm³'),
  ('enum_uom', '704', 'Pair',         'pr'),
  ('enum_uom', '778', 'Pack',         'pk'),
  ('enum_uom', '839', 'Set',          'set'),
  ('enum_uom', '736', 'Roll',         'roll'),
  ('enum_uom', '715', 'Bag',          'bag'),
  ('enum_uom', '977', 'Bale',         'bale'),
  ('enum_uom', '984', 'Box',          'box'),
  ('enum_uom', '719', 'Canister',     'can');

DO $en$
DECLARE
  ru smallint := (SELECT locale_id FROM dictionary_locale WHERE code = 'ru');
BEGIN
  -- роли, типы объектов, типы упаковки: код в колонке code
  INSERT INTO enum_text (enum_key, value_id, locale_id, name)
  SELECT 'enum_user_role', r.id::text, ru, r.name
    FROM enum_user_role r JOIN v86_en e ON e.enum_key = 'enum_user_role' AND e.code = r.code
   WHERE r.name IS DISTINCT FROM e.name
  ON CONFLICT DO NOTHING;
  UPDATE enum_user_role r SET name = e.name
    FROM v86_en e WHERE e.enum_key = 'enum_user_role' AND e.code = r.code;

  INSERT INTO enum_text (enum_key, value_id, locale_id, name)
  SELECT 'enum_object_type', o.id::text, ru, o.name
    FROM enum_object_type o JOIN v86_en e ON e.enum_key = 'enum_object_type' AND e.code = o.code
   WHERE o.name IS DISTINCT FROM e.name
  ON CONFLICT DO NOTHING;
  UPDATE enum_object_type o SET name = e.name
    FROM v86_en e WHERE e.enum_key = 'enum_object_type' AND e.code = o.code;

  INSERT INTO enum_text (enum_key, value_id, locale_id, name)
  SELECT 'enum_packaging_type', p.id::text, ru, p.name
    FROM enum_packaging_type p JOIN v86_en e ON e.enum_key = 'enum_packaging_type' AND e.code = p.code
   WHERE p.name IS DISTINCT FROM e.name
  ON CONFLICT DO NOTHING;
  UPDATE enum_packaging_type p SET name = e.name
    FROM v86_en e WHERE e.enum_key = 'enum_packaging_type' AND e.code = p.code;

  -- единицы: код — МКЕИ, переводится и название, и обозначение
  INSERT INTO enum_text (enum_key, value_id, locale_id, name, symbol)
  SELECT 'enum_uom', u.id::text, ru, u.name, u.symbol
    FROM enum_uom u JOIN v86_en e ON e.enum_key = 'enum_uom' AND e.code = u.mkei_code
   WHERE u.name IS DISTINCT FROM e.name OR u.symbol IS DISTINCT FROM e.symbol
  ON CONFLICT DO NOTHING;
  UPDATE enum_uom u SET name = e.name, symbol = e.symbol
    FROM v86_en e WHERE e.enum_key = 'enum_uom' AND e.code = u.mkei_code;
END
$en$;

-- ---------------------------------------------------------------------
-- 6. Представления и функции отдают имена через enum_name()
-- ---------------------------------------------------------------------
-- Колонки и их порядок не меняются — меняется содержимое. Тип каждой
-- колонки приведён явно: CREATE OR REPLACE VIEW не меняет тип колонки,
-- а функция возвращает text. WITH (security_invoker = true) обязателен
-- (см. V83). В группировках к u.symbol добавлен u.id: перевод ищется
-- по идентификатору, а строки от этого не делятся — у товара одна единица.
CREATE OR REPLACE VIEW v_invoices WITH (security_invoker = true) AS
 SELECT i.id,
    i.depositor_id,
    i.invoice_number,
    i.period_from,
    i.period_to,
    i.issue_date,
    i.amount_no_vat,
    i.amount_vat,
    i.amount_no_vat + COALESCE(i.amount_vat, 0::numeric) AS amount_with_vat,
    (enum_name('enum_status'::text, st.id::text, st.name))::character varying(255) AS status_name,
    st.code AS status_code,
    i.pdf_file IS NOT NULL AS has_pdf
   FROM invoice i
     LEFT JOIN enum_status st ON st.id = i.status_id;

CREATE OR REPLACE VIEW v_orders WITH (security_invoker = true) AS
 SELECT o.doc_id,
    o.document_no,
    o.depositor_id,
    o.doc_number,
    o.erp_number,
    o.origin,
    o.confirmed_at,
    o.doc_date,
    o.planned_ship_date,
    o.ship_date,
    o.delivery_date,
    (COALESCE(enum_name('enum_status'::text, st.id::text, st.name), o.wms_status_raw))::character varying AS status_name,
    st.code AS status_code,
    o.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    (enum_name('enum_order_type'::text, ot.id::text, ot.name))::character varying(255) AS order_type_name,
    o.order_type_id,
    c.name AS consignee_name,
    o.consignee_id,
    dp.address AS delivery_address,
    o.delivery_point_id,
    o.consignee_contact,
    w.name AS warehouse_name,
    o.warehouse_id,
    a.full_name AS author_name,
    o.trip,
    o.comment,
    o.line_count,
    o.total_pieces,
    o.cargo_places,
    o.pallet_count,
    o.weight_kg,
    o.volume_m3,
    o.carrier,
    o.transport_type,
    o.transport_brand,
    o.car_number,
    o.trailer_number,
    o.tech_passport_number,
    o.container_number,
    o.seal_number,
    o.driver_name,
    o.driver_phone,
    o.driver_id_number,
    o.driver_inn,
    o.custom_field_1,
    o.custom_field_2,
    o.custom_field_3,
    o.custom_field_4,
    o.custom_field_5,
    o.custom_field_6,
    o.custom_field_7,
    o.custom_field_8,
    o.custom_field_9,
    o.custom_field_10,
    o.created_at,
    o.updated_at,
    (EXISTS ( SELECT 1
           FROM order_details d
             JOIN sku s ON s.sku_id = d.sku_id
          WHERE d.doc_id = o.doc_id AND NOT s.is_in_wms)) AS has_unknown_sku,
    o.transport_state,
    o.transport_sent_at,
    o.transport_error
   FROM orders o
     LEFT JOIN enum_status st ON st.id = o.status_id
     LEFT JOIN enum_order_type ot ON ot.id = o.order_type_id
     LEFT JOIN consignee c ON c.consignee_id = o.consignee_id
     LEFT JOIN delivery_point dp ON dp.address_id = o.delivery_point_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = o.warehouse_id::text
     LEFT JOIN app_user a ON a.user_id = o.author_id;

CREATE OR REPLACE VIEW v_receipts WITH (security_invoker = true) AS
 SELECT r.doc_id,
    r.document_no,
    r.depositor_id,
    r.doc_number,
    r.erp_number,
    r.origin,
    r.confirmed_at,
    r.doc_date,
    r.eta_date,
    r.arrival_date,
    r.receiving_end_date,
    (COALESCE(enum_name('enum_status'::text, st.id::text, st.name), r.wms_status_raw))::character varying AS status_name,
    st.code AS status_code,
    r.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    (enum_name('enum_receipt_doc_type'::text, dt.id::text, dt.name))::character varying(255) AS doc_type_name,
    r.doc_type_id,
    sp.name AS supplier_name,
    r.supplier_id,
    w.name AS warehouse_name,
    r.warehouse_id,
    a.full_name AS author_name,
    r.accompanying_doc_number,
    r.invoice_number,
    r.gtd,
    r.trip,
    r.comment,
    r.line_count,
    r.total_pieces,
    r.cargo_places,
    r.pallet_count,
    r.weight_kg,
    r.volume_m3,
    r.carrier,
    r.transport_type,
    r.transport_brand,
    r.car_number,
    r.trailer_number,
    r.tech_passport_number,
    r.container_number,
    r.seal_number,
    r.driver_name,
    r.driver_phone,
    r.driver_id_number,
    r.driver_inn,
    r.custom_field_1,
    r.custom_field_2,
    r.custom_field_3,
    r.custom_field_4,
    r.custom_field_5,
    r.custom_field_6,
    r.custom_field_7,
    r.custom_field_8,
    r.custom_field_9,
    r.custom_field_10,
    r.created_at,
    r.updated_at,
    (EXISTS ( SELECT 1
           FROM receipt_details d
             JOIN sku s ON s.sku_id = d.sku_id
          WHERE d.doc_id = r.doc_id AND NOT s.is_in_wms)) AS has_unknown_sku,
    r.transport_state,
    r.transport_sent_at,
    r.transport_error
   FROM receipts r
     LEFT JOIN enum_status st ON st.id = r.status_id
     LEFT JOIN enum_receipt_doc_type dt ON dt.id = r.doc_type_id
     LEFT JOIN supplier sp ON sp.supplier_id = r.supplier_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = r.warehouse_id::text
     LEFT JOIN app_user a ON a.user_id = r.author_id;

CREATE OR REPLACE VIEW v_transfers WITH (security_invoker = true) AS
 SELECT t.doc_id,
    t.document_no,
    t.depositor_id,
    t.doc_number,
    t.erp_number,
    t.origin,
    t.confirmed_at,
    t.doc_date,
    t.ship_date,
    t.received_date,
    (COALESCE(enum_name('enum_status'::text, st.id::text, st.name), t.wms_status_raw))::character varying AS status_name,
    st.code AS status_code,
    t.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    wf.name AS from_warehouse_name,
    t.from_warehouse_id,
    wt.name AS to_warehouse_name,
    t.to_warehouse_id,
    a.full_name AS author_name,
    t.trip,
    t.comment,
    t.line_count,
    t.total_pieces,
    t.cargo_places,
    t.pallet_count,
    t.weight_kg,
    t.volume_m3,
    t.carrier,
    t.transport_type,
    t.transport_brand,
    t.car_number,
    t.trailer_number,
    t.tech_passport_number,
    t.container_number,
    t.seal_number,
    t.driver_name,
    t.driver_phone,
    t.driver_id_number,
    t.driver_inn,
    t.custom_field_1,
    t.custom_field_2,
    t.custom_field_3,
    t.custom_field_4,
    t.custom_field_5,
    t.custom_field_6,
    t.custom_field_7,
    t.custom_field_8,
    t.custom_field_9,
    t.custom_field_10,
    t.created_at,
    t.updated_at,
    (EXISTS ( SELECT 1
           FROM transfer_details d
             JOIN sku s ON s.sku_id = d.sku_id
          WHERE d.doc_id = t.doc_id AND NOT s.is_in_wms)) AS has_unknown_sku,
    t.transport_state,
    t.transport_sent_at,
    t.transport_error
   FROM transfers t
     LEFT JOIN enum_status st ON st.id = t.status_id
     LEFT JOIN warehouse wf ON wf.warehouse_id::text = t.from_warehouse_id::text
     LEFT JOIN warehouse wt ON wt.warehouse_id::text = t.to_warehouse_id::text
     LEFT JOIN app_user a ON a.user_id = t.author_id;

CREATE OR REPLACE VIEW v_order_lines WITH (security_invoker = true) AS
 SELECT od.doc_id,
    od.line_number,
    od.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS unit_name,
    bool_and(s.is_in_wms) AS sku_in_wms,
    max(od.ordered_qty) AS ordered_qty,
    max(od.ordered_qty_wms) AS ordered_qty_wms,
    sum(od.picked_qty) AS picked_qty,
    sum(od.weight_kg) AS weight_kg,
    max(od.price) AS price,
    max(cur.code::text) AS currency_code,
    max(od.price) * COALESCE(sum(od.picked_qty), max(od.ordered_qty)) AS line_sum,
    count(od.batch_number) AS batch_count,
    max(od.line_comment) AS line_comment,
    max(od.quality::text) FILTER (WHERE od.batch_number IS NULL) AS quality
   FROM order_details od
     LEFT JOIN sku s ON s.sku_id = od.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_currency cur ON cur.id = od.currency_id
  GROUP BY od.doc_id, od.line_number, od.sku_id, s.code, s.name, u.symbol, u.id;

CREATE OR REPLACE VIEW v_receipt_lines WITH (security_invoker = true) AS
 SELECT rd.doc_id,
    rd.line_number,
    rd.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS unit_name,
    bool_and(s.is_in_wms) AS sku_in_wms,
    max(rd.expected_qty) AS expected_qty,
    max(rd.expected_qty_wms) AS expected_qty_wms,
    sum(rd.accepted_qty) AS accepted_qty,
    sum(rd.defect_qty) AS defect_qty,
    max(rd.price) AS price,
    max(cur.code::text) AS currency_code,
    max(rd.price) * COALESCE(sum(rd.accepted_qty), max(rd.expected_qty)) AS line_sum,
    count(rd.batch_number) AS batch_count,
    max(rd.line_comment) AS line_comment
   FROM receipt_details rd
     LEFT JOIN sku s ON s.sku_id = rd.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_currency cur ON cur.id = rd.currency_id
  GROUP BY rd.doc_id, rd.line_number, rd.sku_id, s.code, s.name, u.symbol, u.id;

CREATE OR REPLACE VIEW v_transfer_lines WITH (security_invoker = true) AS
 SELECT td.doc_id,
    td.line_number,
    td.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS unit_name,
    sum(td.qty) AS qty,
    sum(td.qty_wms) AS qty_wms,
    count(td.batch_number) AS batch_count,
    max(td.line_comment) AS line_comment
   FROM transfer_details td
     LEFT JOIN sku s ON s.sku_id = td.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
  GROUP BY td.doc_id, td.line_number, td.sku_id, s.code, s.name, u.symbol, u.id;

CREATE OR REPLACE VIEW v_product_packaging WITH (security_invoker = true) AS
 SELECT p.packaging_id,
    p.depositor_id,
    p.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS uom_name,
    p.packaging_code,
    p.packaging_type_id,
    (enum_name('enum_packaging_type'::text, pt.id::text, pt.name))::character varying(255) AS packaging_type_name,
    p.barcode,
    p.coefficient,
    p.length_cm,
    p.width_cm,
    p.height_cm,
    p.volume_m3,
    p.gross_weight_kg,
    COALESCE(s.order_unit_id = p.packaging_id, false) AS is_default_order_unit
   FROM product_packaging p
     LEFT JOIN sku s ON s.sku_id = p.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_packaging_type pt ON pt.id = p.packaging_type_id;

CREATE OR REPLACE VIEW v_sku_full WITH (security_invoker = true) AS
 SELECT s.sku_id,
    s.depositor_id,
    d.org_name AS depositor_name,
    s.item_code,
    s.code,
    s.name,
    s.item_full_name,
    s.barcode,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS uom_name,
    s.uom_id,
    ( SELECT p.packaging_code
           FROM product_packaging p
          WHERE p.packaging_id = s.order_unit_id) AS order_unit_name,
    s.order_unit_id,
    s.item_group AS item_group_name,
    s.item_type AS item_type_name,
    s.has_packaging,
    s.length,
    s.width,
    s.height,
    s.volume,
    s.weight_gross,
    s.weight_net,
    s.manufacturer,
    s.country_origin,
    s.brand,
    s.is_fragile,
    s.is_serial_tracked,
    s.batch_tracking,
    s.expiry_tracking,
    s.min_storage_temp,
    s.max_storage_temp,
    s.min_humidity,
    s.max_humidity,
    s.min_days_for_receipt,
    s.min_days_for_reservation,
    s.shelf_life_days,
    s.color,
    s.size,
    s.abc_class,
    s.xyz_class,
    s.hs_code,
    s.enstru_code,
    s.cost,
    cur.code AS currency_code,
    s.currency_id,
    s.is_kit,
    s.is_marking_required,
    s.is_excise,
    s.is_controlled,
    s.is_wms_active,
    (enum_name('enum_hazard_class'::text, hc.id::text, hc.name))::character varying(255) AS hazard_class_name,
    s.hazard_class_id,
    (enum_name('enum_hazard_group'::text, hg.id::text, hg.name))::character varying(255) AS hazard_group_name,
    s.hazard_group_id,
    (enum_name('enum_status'::text, st.id::text, st.name))::character varying(255) AS status_name,
    s.comment,
    s.is_active,
    s.is_in_wms,
    s.synced_at,
    s.wms_version,
    s.created_by,
    s.created_at,
    s.updated_by,
    s.updated_at,
    s.custom_attr1,
    s.custom_attr2,
    s.custom_attr3,
    s.custom_attr4,
    s.custom_attr5,
    s.custom_attr6,
    s.custom_attr7,
    s.custom_attr8,
    s.custom_attr9,
    s.custom_attr10
   FROM sku s
     LEFT JOIN depositor d ON d.depositor_id = s.depositor_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_currency cur ON cur.id = s.currency_id
     LEFT JOIN enum_hazard_class hc ON hc.id = s.hazard_class_id
     LEFT JOIN enum_hazard_group hg ON hg.id = s.hazard_group_id
     LEFT JOIN enum_status st ON st.id = s.status_id;

CREATE OR REPLACE VIEW v_stock_details WITH (security_invoker = true) AS
 SELECT st.register_id,
    st.depositor_id,
    st.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    st.warehouse_id,
    w.name AS warehouse_name,
    st.batch_number,
    st.series_number,
    st.expiry_date,
    st.quality,
    (enum_name('enum_block_reason'::text, br.id::text, br.name))::character varying(255) AS block_reason_name,
    st.quantity,
    st.reserved_qty,
    st.blocked_qty,
    st.available_qty,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS unit_name,
    st.updated_at,
    st.synced_at
   FROM stock st
     LEFT JOIN sku s ON s.sku_id = st.sku_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = st.warehouse_id::text
     LEFT JOIN enum_uom u ON u.id = st.unit_id
     LEFT JOIN enum_block_reason br ON br.id = st.block_reason_id;

CREATE OR REPLACE VIEW v_stock_lines WITH (security_invoker = true) AS
 SELECT a.depositor_id,
    a.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    s.item_group AS item_group_name,
    a.warehouse_id,
    w.name AS warehouse_name,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying(10) AS unit_name,
    a.quantity,
    a.reserved_qty,
    a.blocked_qty,
    a.available_qty,
    a.defect_qty,
    a.incoming_qty,
    a.batch_count,
    a.synced_at
   FROM v_stock_available a
     LEFT JOIN sku s ON s.sku_id = a.sku_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = a.warehouse_id::text
     LEFT JOIN enum_uom u ON u.id = COALESCE(a.unit_id, s.uom_id);

CREATE OR REPLACE VIEW v_users WITH (security_invoker = true) AS
 SELECT u.user_id,
    u.login,
    u.full_name,
    (enum_name('enum_user_role'::text, r.id::text, r.name))::character varying(255) AS role_name,
    r.code AS role_code,
    u."position",
    u.organization,
    u.phone,
    u.is_active,
    u.last_login_at
   FROM app_user u
     LEFT JOIN enum_user_role r ON r.id = u.role_id;

CREATE OR REPLACE VIEW v_user_access WITH (security_invoker = true) AS
 SELECT u.user_id,
    u.full_name,
    u.login,
    (enum_name('enum_user_role'::text, r.id::text, r.name))::character varying(255) AS role_name,
    ( SELECT string_agg(d.org_name::text, ', '::text ORDER BY (d.org_name::text)) AS string_agg
           FROM user_depositor_access a
             JOIN depositor d ON d.depositor_id = a.depositor_id
          WHERE a.user_id = u.user_id) AS depositors,
    ( SELECT string_agg(w.name::text, ', '::text ORDER BY (w.name::text)) AS string_agg
           FROM user_warehouse_access a
             JOIN warehouse w ON w.warehouse_id::text = a.warehouse_id::text
          WHERE a.user_id = u.user_id) AS warehouses,
    u.is_active
   FROM app_user u
     LEFT JOIN enum_user_role r ON r.id = u.role_id;

CREATE OR REPLACE VIEW v_column_tables WITH (security_invoker = true) AS
 SELECT c.table_key,
    mode() WITHIN GROUP (ORDER BY o.code) AS object_code,
    (mode() WITHIN GROUP (ORDER BY enum_name('enum_object_type'::text, o.id::text, o.name)))::character varying AS object_name,
    count(*)::integer AS column_count,
    count(*) FILTER (WHERE c.is_default)::integer AS default_count
   FROM table_column c
     LEFT JOIN enum_object_type o ON o.id = c.object_type_id
  GROUP BY c.table_key
  ORDER BY c.table_key;

CREATE OR REPLACE VIEW v_sku_dictionaries WITH (security_invoker = true) AS
 SELECT 'uom'::text AS kind,
    u.id::text AS id,
    (enum_symbol('enum_uom'::text, u.id::text, u.symbol))::character varying AS name,
    u.sort_order,
    enum_aliases('enum_uom'::text, u.id::text, u.symbol, true) AS aliases
   FROM enum_uom u
  WHERE u.is_active
UNION ALL
 SELECT 'currency'::text AS kind,
    c.id::text AS id,
    c.code AS name,
    c.sort_order,
    ARRAY[c.code::text] AS aliases
   FROM enum_currency c
  WHERE c.is_active
UNION ALL
 SELECT 'hazard_class'::text AS kind,
    h.id::text AS id,
    (enum_name('enum_hazard_class'::text, h.id::text, h.name))::character varying AS name,
    h.sort_order,
    enum_aliases('enum_hazard_class'::text, h.id::text, h.name, false) AS aliases
   FROM enum_hazard_class h
  WHERE h.is_active
UNION ALL
 SELECT 'hazard_group'::text AS kind,
    h.id::text AS id,
    (enum_name('enum_hazard_group'::text, h.id::text, h.name))::character varying AS name,
    h.sort_order,
    enum_aliases('enum_hazard_group'::text, h.id::text, h.name, false) AS aliases
   FROM enum_hazard_group h
  WHERE h.is_active
UNION ALL
 SELECT 'packaging_type'::text AS kind,
    t.id::text AS id,
    (enum_name('enum_packaging_type'::text, t.id::text, t.name))::character varying AS name,
    t.sort_order,
    enum_aliases('enum_packaging_type'::text, t.id::text, t.name, false) AS aliases
   FROM enum_packaging_type t
  WHERE t.is_active;
CREATE OR REPLACE FUNCTION admin_roles()
 RETURNS TABLE(code character varying, name character varying)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'dbo'
AS $function$
  SELECT code, enum_name('enum_user_role', id::text, name)::varchar FROM enum_user_role WHERE is_active ORDER BY sort_order
$function$;

CREATE OR REPLACE FUNCTION admin_users()
 RETURNS TABLE(user_id text, login character varying, full_name character varying, role_code character varying, role_name character varying, user_position character varying, organization character varying, phone character varying, is_active boolean, must_change_password boolean, last_login_at timestamp without time zone, password_changed_at timestamp with time zone, scope text, depositor_codes text, hide_billing_tab boolean, hide_services_tab boolean, can_create_receipts boolean, can_create_orders boolean, can_create_transfers boolean, can_upload_sku_excel boolean, can_upload_packaging_excel boolean, can_edit_transport boolean)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'dbo'
AS $function$
  SELECT u.user_id::text, u.login, u.full_name, r.code, enum_name('enum_user_role', r.id::text, r.name)::varchar,
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
$function$;

CREATE OR REPLACE FUNCTION admin_field_catalog(p_locale text)
 RETURNS TABLE(object_code character varying, object_name character varying, field_id character varying, portal_label text, shown_in text)
 LANGUAGE sql
 STABLE
 SET search_path TO 'dbo'
AS $function$
  WITH texts AS (SELECT t.text_key, t.value FROM ui_texts(p_locale) t)
  SELECT
    o.code,
    enum_name('enum_object_type', o.id::text, o.name)::varchar,
    c.field_id,
    -- подпись своего раздела; если поле показывается только
    -- в подчинённых таблицах (строки документа), берём первую
    -- по имени таблицы — лишь бы выбор не зависел от погоды
    (array_agg(COALESCE(t.value, c.text_key)::text
               ORDER BY (c.table_key = o.code) DESC, c.table_key))[1] AS portal_label,
    string_agg(DISTINCT c.table_key, ', ' ORDER BY c.table_key) AS shown_in
  FROM table_column c
  JOIN enum_object_type o ON o.id = c.object_type_id
  LEFT JOIN texts t ON t.text_key = c.text_key
  WHERE c.field_id IS NOT NULL
  GROUP BY o.code, o.id, o.name, c.field_id
  ORDER BY o.code, 4
$function$;

CREATE OR REPLACE FUNCTION admin_field_labels(p_depositor_id uuid)
 RETURNS TABLE(object_code character varying, object_name character varying, field_id character varying, custom_label character varying, shown_in text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'dbo'
AS $function$
  SELECT
    o.code,
    enum_name('enum_object_type', o.id::text, o.name)::varchar,
    l.field_id,
    l.custom_label,
    -- в каких таблицах это поле видно: так сразу ясно, где подпись
    -- поменяется, и не приходится угадывать
    COALESCE(string_agg(DISTINCT c.table_key, ', ' ORDER BY c.table_key), '—')
  FROM depositor_field_label l
  JOIN enum_object_type o ON o.id = l.object_type_id
  LEFT JOIN table_column c
         ON c.object_type_id = l.object_type_id AND c.field_id = l.field_id
  WHERE l.depositor_id = p_depositor_id
  GROUP BY o.code, o.id, o.name, l.field_id, l.custom_label
  ORDER BY o.code, l.field_id;
$function$;

-- ---------------------------------------------------------------------
-- 7. Тексты вкладки «Перечисления»
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.button.enums',           'admin', 'button',      NULL, 'вкладка «Перечисления» в группе «Тексты интерфейса» (V86)'),
  ('admin.hint.enums',             'admin', 'hint',        NULL, 'вкладка «Перечисления»: что здесь ведётся'),
  ('admin.label.enum',             'admin', 'label',       NULL, 'вкладка «Перечисления»: отбор по перечислению'),
  ('admin.option.all_enums',       'admin', 'option',      NULL, 'вкладка «Перечисления»: все перечисления'),
  ('admin.option.all_values',      'admin', 'option',      NULL, 'вкладка «Перечисления»: все значения'),
  ('admin.column.enum_code',       'admin', 'column',      NULL, 'вкладка «Перечисления»: код значения'),
  ('admin.column.enum_base',       'admin', 'column',      NULL, 'вкладка «Перечисления»: основное (английское) имя'),
  ('admin.placeholder.enum_search','admin', 'placeholder', NULL, 'вкладка «Перечисления»: поиск'),
  ('admin.placeholder.enum_symbol','admin', 'placeholder', NULL, 'вкладка «Перечисления»: обозначение единицы'),
  ('admin.text.inactive_value',    'admin', 'text',        NULL, 'вкладка «Перечисления»: значение выключено'),
  ('enums.name.enum_status',           'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_order_type',       'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_receipt_doc_type', 'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_uom',              'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_currency',         'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_packaging_type',   'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_block_reason',     'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_hazard_class',     'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_hazard_group',     'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_user_role',        'admin', 'text', NULL, 'название перечисления'),
  ('enums.name.enum_object_type',      'admin', 'text', NULL, 'название перечисления'),
  ('error.enum.unknown',           'server', 'message', 'i18n', 'сообщение сервера; собирается по ключу в PortalMessages'),
  ('error.enum.value_unknown',     'server', 'message', 'i18n', 'сообщение сервера; собирается по ключу в PortalMessages')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.button.enums',            'ru', 'Перечисления'),
    ('admin.button.enums',            'en', 'Enumerations'),
    ('admin.hint.enums',              'ru', 'Основное имя значения — английское, его ведут в самих справочниках. Здесь — переводы на остальные языки. Пустая ячейка — перевода нет, человек увидит основное имя.'),
    ('admin.hint.enums',              'en', 'The main name of a value is English and is maintained in the directory itself. Here you maintain translations into other languages. An empty cell means no translation: the main name is shown.'),
    ('admin.label.enum',              'ru', 'Перечисление'),
    ('admin.label.enum',              'en', 'Enumeration'),
    ('admin.option.all_enums',        'ru', 'Все перечисления'),
    ('admin.option.all_enums',        'en', 'All enumerations'),
    ('admin.option.all_values',       'ru', 'Все значения'),
    ('admin.option.all_values',       'en', 'All values'),
    ('admin.column.enum_code',        'ru', 'Код'),
    ('admin.column.enum_code',        'en', 'Code'),
    ('admin.column.enum_base',        'ru', 'Основное имя (EN)'),
    ('admin.column.enum_base',        'en', 'Main name (EN)'),
    ('admin.placeholder.enum_search', 'ru', 'код или имя'),
    ('admin.placeholder.enum_search', 'en', 'code or name'),
    ('admin.placeholder.enum_symbol', 'ru', 'обозначение'),
    ('admin.placeholder.enum_symbol', 'en', 'symbol'),
    ('admin.text.inactive_value',     'ru', 'выключено'),
    ('admin.text.inactive_value',     'en', 'inactive'),
    ('enums.name.enum_status',           'ru', 'Статусы'),
    ('enums.name.enum_status',           'en', 'Statuses'),
    ('enums.name.enum_order_type',       'ru', 'Типы заказов'),
    ('enums.name.enum_order_type',       'en', 'Order types'),
    ('enums.name.enum_receipt_doc_type', 'ru', 'Типы поступлений'),
    ('enums.name.enum_receipt_doc_type', 'en', 'Receipt types'),
    ('enums.name.enum_uom',              'ru', 'Единицы измерения'),
    ('enums.name.enum_uom',              'en', 'Units of measure'),
    ('enums.name.enum_currency',         'ru', 'Валюты'),
    ('enums.name.enum_currency',         'en', 'Currencies'),
    ('enums.name.enum_packaging_type',   'ru', 'Типы упаковки'),
    ('enums.name.enum_packaging_type',   'en', 'Packaging types'),
    ('enums.name.enum_block_reason',     'ru', 'Причины блокировки'),
    ('enums.name.enum_block_reason',     'en', 'Block reasons'),
    ('enums.name.enum_hazard_class',     'ru', 'Классы опасности'),
    ('enums.name.enum_hazard_class',     'en', 'Hazard classes'),
    ('enums.name.enum_hazard_group',     'ru', 'Группы опасности'),
    ('enums.name.enum_hazard_group',     'en', 'Hazard groups'),
    ('enums.name.enum_user_role',        'ru', 'Роли пользователей'),
    ('enums.name.enum_user_role',        'en', 'User roles'),
    ('enums.name.enum_object_type',      'ru', 'Типы объектов'),
    ('enums.name.enum_object_type',      'en', 'Object types'),
    ('error.enum.unknown',            'ru', 'Перечисления «{enum}» нет'),
    ('error.enum.unknown',            'en', 'There is no enumeration «{enum}»'),
    ('error.enum.value_unknown',      'ru', 'В перечислении нет значения {value}'),
    ('error.enum.value_unknown',      'en', 'The enumeration has no value {value}')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  v text;
  got text;
BEGIN
  -- представления не потеряли security_invoker
  FOREACH v IN ARRAY ARRAY['v_invoices','v_orders','v_receipts','v_transfers','v_order_lines',
      'v_receipt_lines','v_transfer_lines','v_product_packaging','v_sku_full','v_stock_details',
      'v_stock_lines','v_sku_dictionaries'] LOOP
    IF (SELECT reloptions FROM pg_class WHERE oid = ('dbo.' || v)::regclass)
         IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
      RAISE EXCEPTION '% потеряло security_invoker', v;
    END IF;
  END LOOP;

  -- перевод берётся по языку сессии, без языка — основное имя
  PERFORM set_config('app.locale', 'ru', true);
  got := enum_symbol('enum_uom', (SELECT id::text FROM enum_uom WHERE mkei_code = '796'), 'pcs');
  IF got IS DISTINCT FROM 'шт' THEN
    RAISE EXCEPTION 'перевод «шт» не нашёлся, получили %', got;
  END IF;
  PERFORM set_config('app.locale', '', true);
  got := enum_symbol('enum_uom', (SELECT id::text FROM enum_uom WHERE mkei_code = '796'), 'pcs');
  IF got IS DISTINCT FROM 'pcs' THEN
    RAISE EXCEPTION 'без языка ждали основное «pcs», получили %', got;
  END IF;

  -- таблица вкладки собирается по всем перечислениям
  IF (SELECT count(DISTINCT m.enum_key) FROM enum_text_matrix() m)
       <> (SELECT count(*) FROM enum_registry) THEN
    RAISE EXCEPTION 'enum_text_matrix видит не все перечисления';
  END IF;
END
$check$;
