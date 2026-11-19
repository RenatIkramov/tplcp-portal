-- ---------------------------------------------------------------------
-- V97. Заказ из Excel — правки после проверки; тип транспорта — перечисление
-- ---------------------------------------------------------------------
-- Замечания заказчика от 02.11.2026 к V96:
--   * списка кодов товаров в шаблонах заказа и упаковок нет: товаров
--     могут быть тысячи (код проверяет загрузка);
--   * «Дата доставки» и «Рейс» — данные WMS: из шаблона убраны,
--     в черновике портал их больше не принимает (DocumentType);
--   * место доставки — выпадающим списком по выбранному грузополучателю,
--     все списки — на видимом листе «Списки»;
--   * контактные данные грузополучателя — в шапке шаблона; пусто —
--     подставляются по месту доставки, как на экране (V83);
--   * проверка данных во всех ячейках, в том числе длина текста;
--   * «Тип транспорта» — перечисление (здесь), значения — заказчика.
--
-- Тип транспорта хранится КОДОМ в прежней колонке transport_type
-- (как источник документа в V90): у трёх документов одна колонка,
-- и переименовывать её незачем. Внешний ключ на код не даёт записать
-- в неё ничего, кроме значения перечисления.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Перечисление «Типы транспорта»
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enum_transport_type (
  id          smallint     PRIMARY KEY,
  code        varchar(30)  NOT NULL UNIQUE CHECK (code ~ '^[0-9]{2}$'),
  name        varchar(255) NOT NULL,
  sort_order  smallint,
  is_active   boolean      NOT NULL DEFAULT true
);
COMMENT ON TABLE enum_transport_type IS 'тип транспорта (V97): код хранится в receipts/orders/transfers.transport_type';
GRANT SELECT, INSERT, UPDATE, DELETE ON enum_transport_type TO portal_app;

INSERT INTO enum_transport_type (id, code, name, sort_order) VALUES
  (1,  '01', 'Passenger car',            10),
  (2,  '02', 'Light van, 1 t',           20),
  (3,  '03', 'Van, 1.5 t',               30),
  (4,  '04', 'Truck, 3 t',               40),
  (5,  '05', 'Truck, 5 t',               50),
  (6,  '06', 'Truck, 10 t',              60),
  (7,  '07', 'Truck, 15 t',              70),
  (8,  '08', 'Truck, 20 t',              80),
  (9,  '09', 'Heavy truck, 30 t',        90),
  (10, '10', 'Railway wagon',            100),
  (11, '11', 'Railway container',        110)
ON CONFLICT (id) DO NOTHING;

INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order, code_pattern)
VALUES ('enum_transport_type', 'code', false, 'enums.name.enum_transport_type', 135, '^[0-9]{2}$')
ON CONFLICT (enum_key) DO NOTHING;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_transport_type', t.id::text, l.locale_id, v.ru
  FROM (VALUES
    ('01', 'Легковой автомобиль'),
    ('02', 'Малотоннажный фургон 1 тонна'),
    ('03', 'Фургон 1,5 тонны'),
    ('04', 'Грузовой автомобиль 3 тонны'),
    ('05', 'Грузовой автомобиль 5 тонн'),
    ('06', 'Грузовой автомобиль 10 тонн'),
    ('07', 'Грузовой автомобиль 15 тонн'),
    ('08', 'Грузовой автомобиль 20 тонн'),
    ('09', 'Тяжёлый грузовой автомобиль 30 т'),
    ('10', 'Железнодорожный вагон'),
    ('11', 'Железнодорожный контейнер')
  ) AS v(code, ru)
  JOIN enum_transport_type t ON t.code = v.code
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- ---------------------------------------------------------------------
-- 2. Колонки документов: текст → код
-- ---------------------------------------------------------------------
-- Прежде здесь лежал свободный текст. Совпавшее с русским или
-- английским именем значения становится его кодом; остальное
-- очищается — свободный текст внешний ключ не пропустит, а портал
-- ещё не работает с живыми документами.
DO $map$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['receipts', 'orders', 'transfers'] LOOP
    EXECUTE format($q$
      UPDATE %I d
         SET transport_type = (
               SELECT tt.code FROM enum_transport_type tt
                LEFT JOIN enum_text et ON et.enum_key = 'enum_transport_type'
                                      AND et.value_id = tt.id::text
                WHERE lower(btrim(d.transport_type)) IN (lower(tt.name), lower(et.name), tt.code)
                LIMIT 1)
       WHERE d.transport_type IS NOT NULL$q$, t);
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', t, t || '_transport_type_fk');
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (transport_type)'
                   ' REFERENCES enum_transport_type (code)', t, t || '_transport_type_fk');
  END LOOP;
END
$map$;

-- ---------------------------------------------------------------------
-- 3. Представления документов: имя типа транспорта на языке пользователя
-- ---------------------------------------------------------------------
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
    (COALESCE(dbo.enum_name('enum_status'::text, (st.id)::text, (st.name)::text), (r.wms_status_raw)::text))::character varying AS status_name,
    st.code AS status_code,
    r.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    (dbo.enum_name('enum_receipt_doc_type'::text, (dt.id)::text, (dt.name)::text))::character varying(255) AS doc_type_name,
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
           FROM (dbo.receipt_details d
             JOIN dbo.sku s ON ((s.sku_id = d.sku_id)))
          WHERE ((d.doc_id = r.doc_id) AND (NOT s.is_in_wms)))) AS has_unknown_sku,
    r.transport_state,
    r.transport_sent_at,
    r.transport_error,
    (dbo.enum_name('enum_transport_type'::text, (tt.id)::text, (tt.name)::text))::character varying(255) AS transport_type_name
   FROM (((((dbo.receipts r
     LEFT JOIN dbo.enum_status st ON ((st.id = r.status_id)))
     LEFT JOIN dbo.enum_receipt_doc_type dt ON ((dt.id = r.doc_type_id)))
     LEFT JOIN dbo.supplier sp ON ((sp.supplier_id = r.supplier_id)))
     LEFT JOIN dbo.warehouse w ON (((w.warehouse_id)::text = (r.warehouse_id)::text)))
     LEFT JOIN dbo.app_user a ON ((a.user_id = r.author_id)))
     LEFT JOIN dbo.enum_transport_type tt ON (((tt.code)::text = (r.transport_type)::text));

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
    (COALESCE(dbo.enum_name('enum_status'::text, (st.id)::text, (st.name)::text), (o.wms_status_raw)::text))::character varying AS status_name,
    st.code AS status_code,
    o.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    (dbo.enum_name('enum_order_type'::text, (ot.id)::text, (ot.name)::text))::character varying(255) AS order_type_name,
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
           FROM (dbo.order_details d
             JOIN dbo.sku s ON ((s.sku_id = d.sku_id)))
          WHERE ((d.doc_id = o.doc_id) AND (NOT s.is_in_wms)))) AS has_unknown_sku,
    o.transport_state,
    o.transport_sent_at,
    o.transport_error,
    (dbo.enum_name('enum_transport_type'::text, (tt.id)::text, (tt.name)::text))::character varying(255) AS transport_type_name
   FROM ((((((dbo.orders o
     LEFT JOIN dbo.enum_status st ON ((st.id = o.status_id)))
     LEFT JOIN dbo.enum_order_type ot ON ((ot.id = o.order_type_id)))
     LEFT JOIN dbo.consignee c ON ((c.consignee_id = o.consignee_id)))
     LEFT JOIN dbo.delivery_point dp ON ((dp.address_id = o.delivery_point_id)))
     LEFT JOIN dbo.warehouse w ON (((w.warehouse_id)::text = (o.warehouse_id)::text)))
     LEFT JOIN dbo.app_user a ON ((a.user_id = o.author_id)))
     LEFT JOIN dbo.enum_transport_type tt ON (((tt.code)::text = (o.transport_type)::text));

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
    (COALESCE(dbo.enum_name('enum_status'::text, (st.id)::text, (st.name)::text), (t.wms_status_raw)::text))::character varying AS status_name,
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
           FROM (dbo.transfer_details d
             JOIN dbo.sku s ON ((s.sku_id = d.sku_id)))
          WHERE ((d.doc_id = t.doc_id) AND (NOT s.is_in_wms)))) AS has_unknown_sku,
    t.transport_state,
    t.transport_sent_at,
    t.transport_error,
    (dbo.enum_name('enum_transport_type'::text, (tt.id)::text, (tt.name)::text))::character varying(255) AS transport_type_name
   FROM ((((dbo.transfers t
     LEFT JOIN dbo.enum_status st ON ((st.id = t.status_id)))
     LEFT JOIN dbo.warehouse wf ON (((wf.warehouse_id)::text = (t.from_warehouse_id)::text)))
     LEFT JOIN dbo.warehouse wt ON (((wt.warehouse_id)::text = (t.to_warehouse_id)::text)))
     LEFT JOIN dbo.app_user a ON ((a.user_id = t.author_id)))
     LEFT JOIN dbo.enum_transport_type tt ON (((tt.code)::text = (t.transport_type)::text));

-- Фильтр и сортировка списков — по перечислению
UPDATE table_column
   SET filter_kind = 'enum', filter_column = 'transport_type',
       filter_enum = 'enum_transport_type', sort_column = 'transport_type_name'
 WHERE column_id = 'transportType' AND table_key IN ('receipts', 'orders', 'transfers');

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.k, v.scope, v.element, v.owner, v.note
  FROM (VALUES
    ('enums.name.enum_transport_type', 'admin', 'text', NULL, 'название перечисления (V97)'),
    ('excel.order.lists_sheet', 'excel', 'text', NULL, 'шаблон заказа: имя листа со списками выбора (V97)'),
    ('excel.order.lists_hint', 'excel', 'text', NULL, 'шаблон заказа: пояснение на листе списков (V97)'),
    ('excel.order.lists_address_consignee', 'excel', 'text', NULL, 'шаблон заказа: лист списков, колонка грузополучателя у мест доставки (V97)'),
    ('excel.order.note_contact', 'excel', 'text', NULL, 'шаблон заказа: подсказка к контакту грузополучателя (V97)'),
    ('excel.order.note_text', 'excel', 'text', NULL, 'шаблон заказа: подсказка к текстовому полю с пределом длины (V97)'),
    ('excel.order.validation_text_title', 'excel', 'text', NULL, 'шаблон заказа: слишком длинный текст, заголовок ошибки (V97)'),
    ('excel.order.validation_text_message', 'excel', 'text', NULL, 'шаблон заказа: слишком длинный текст, текст ошибки (V97)')
  ) AS v(k, scope, element, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('enums.name.enum_transport_type', 'ru', 'Типы транспорта'),
    ('enums.name.enum_transport_type', 'en', 'Transport types'),
    ('excel.order.lists_sheet', 'ru', 'Списки'),
    ('excel.order.lists_sheet', 'en', 'Lists'),
    ('excel.order.lists_hint', 'ru', 'Значения для выпадающих списков листа заказа — на момент скачивания шаблона. Если нужного значения нет, скачайте шаблон заново.'),
    ('excel.order.lists_hint', 'en', 'Values for the drop-down lists of the order sheet, as at the time the template was downloaded. If a value is missing, download the template again.'),
    ('excel.order.lists_address_consignee', 'ru', 'Грузополучатель места доставки'),
    ('excel.order.lists_address_consignee', 'en', 'Consignee of the delivery point'),
    ('excel.order.note_contact', 'ru', 'Пусто — подставится контакт места доставки, а если его нет — контакт грузополучателя.'),
    ('excel.order.note_contact', 'en', 'Empty means the contact of the delivery point, or the consignee contact if the point has none.'),
    ('excel.order.note_text', 'ru', 'Не больше {limit} знаков.'),
    ('excel.order.note_text', 'en', 'No more than {limit} characters.'),
    ('excel.order.validation_text_title', 'ru', 'Слишком длинный текст'),
    ('excel.order.validation_text_title', 'en', 'Text is too long'),
    ('excel.order.validation_text_message', 'ru', 'Не больше {limit} знаков.'),
    ('excel.order.validation_text_message', 'en', 'No more than {limit} characters.')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Подсказка к месту доставки: теперь это список по грузополучателю
UPDATE ui_text SET value = CASE (SELECT code FROM dictionary_locale d WHERE d.locale_id = ui_text.locale_id)
         WHEN 'ru' THEN 'Выберите из списка мест доставки выбранного грузополучателя. Пусто — основной адрес.'
         ELSE 'Choose from the delivery points of the chosen consignee. Empty means the main address.' END
 WHERE text_key = 'excel.order.note_address'
   AND locale_id IN (SELECT locale_id FROM dictionary_locale WHERE code IN ('ru', 'en'));

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  v text;
BEGIN
  FOREACH v IN ARRAY ARRAY['v_receipts', 'v_orders', 'v_transfers'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_class
                    WHERE relname = v AND 'security_invoker=true' = ANY (reloptions)) THEN
      RAISE EXCEPTION 'V97: % потеряло security_invoker', v;
    END IF;
  END LOOP;
  IF (SELECT count(*) FROM enum_transport_type) < 11 THEN
    RAISE EXCEPTION 'V97: в enum_transport_type не все значения заказчика';
  END IF;
END
$check$;
