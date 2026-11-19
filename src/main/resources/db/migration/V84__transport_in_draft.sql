-- ---------------------------------------------------------------------
-- V84. Транспорт в черновике и состояние его передачи в WMS
-- ---------------------------------------------------------------------
-- Решение заказчика от 27.10.2026 (документ «Решение Р5»: «Транспорт
-- в черновике»):
--   * транспортный раздел заполняется уже в черновике — у поступлений,
--     заказов и перемещений; сохраняется общей кнопкой вместе с шапкой;
--   * при подтверждении транспорт уходит в WMS вслед за документом;
--   * документ ушёл, а транспорт нет — документ остаётся отправленным,
--     у транспорта отметка «не передан» с причиной и кнопка повтора.
--     Автоповтор — вместе с интеграцией (этап 8): его может взять шина.
--
-- Колонки транспорта в шапках есть с V4 — здесь только состояние передачи.
-- Правило «после отправки транспорт правится в статусах с флагом
-- allows_transport_edit» не меняется.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Состояние передачи транспорта
-- ---------------------------------------------------------------------
--   transport_state   NULL — не отправлялся (черновик или транспорта нет),
--                     'sent' — передан, 'failed' — не передан;
--   transport_sent_at когда последний раз передан успешно;
--   transport_error   почему не передан — то, что ответила WMS или шина.
DO $cols$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['receipts', 'orders', 'transfers'] LOOP
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS transport_state varchar(10)', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS transport_sent_at timestamptz', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS transport_error text', t);
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS ck_%s_transport_state', t, t);
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT ck_%s_transport_state'
                   ' CHECK (transport_state IN (''sent'', ''failed''))', t, t);
    EXECUTE format('COMMENT ON COLUMN %I.transport_state IS %L', t,
                   'передача транспорта в WMS: NULL — не отправлялся, sent — передан, failed — не передан (V84)');
    EXECUTE format('COMMENT ON COLUMN %I.transport_sent_at IS %L', t,
                   'когда транспорт последний раз передан в WMS (V84)');
    EXECUTE format('COMMENT ON COLUMN %I.transport_error IS %L', t,
                   'почему транспорт не передан в WMS (V84)');
  END LOOP;
END
$cols$;

-- ---------------------------------------------------------------------
-- 2. Представления шапок
-- ---------------------------------------------------------------------
-- Колонки дописаны в конец. WITH (security_invoker = true) обязателен:
-- замена представления без него сбрасывает параметр (см. V83), и RLS
-- перестала бы действовать от имени вызывающего.
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
    COALESCE(st.name, r.wms_status_raw) AS status_name,
    st.code AS status_code,
    r.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    dt.name AS doc_type_name,
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
    COALESCE(st.name, o.wms_status_raw) AS status_name,
    st.code AS status_code,
    o.wms_status_raw,
    COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
    ot.name AS order_type_name,
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
    COALESCE(st.name, t.wms_status_raw) AS status_name,
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

-- ---------------------------------------------------------------------
-- 3. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.hint.transport_goes_with_document', 'common', 'hint', NULL,
   'вкладка «Транспорт» черновика: когда транспорт уйдёт в WMS'),
  ('common.text.transport_sent', 'common', 'text', NULL,
   'вкладка «Транспорт» отправленного документа: транспорт передан'),
  ('common.text.transport_not_sent', 'common', 'text', NULL,
   'вкладка «Транспорт» отправленного документа: транспорт не передан'),
  ('common.button.resend_transport', 'common', 'button', NULL,
   'вкладка «Транспорт»: повторить отправку непереданного транспорта'),
  ('error.transport.nothing_to_resend', 'server', 'message', 'documents',
   'сообщение сервера; собирается по ключу в PortalMessages')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.hint.transport_goes_with_document', 'ru',
     'Транспорт уйдёт в WMS вместе с документом, когда вы его подтвердите.'),
    ('common.hint.transport_goes_with_document', 'en',
     'The transport details go to the WMS together with the document when you confirm it.'),
    ('common.text.transport_sent',       'ru', 'Транспорт передан в WMS: {time}'),
    ('common.text.transport_sent',       'en', 'Transport details sent to the WMS: {time}'),
    ('common.text.transport_not_sent',   'ru', 'Транспорт не передан в WMS: {error}'),
    ('common.text.transport_not_sent',   'en', 'Transport details were not sent to the WMS: {error}'),
    ('common.button.resend_transport',   'ru', 'Повторить отправку транспорта'),
    ('common.button.resend_transport',   'en', 'Resend transport details'),
    ('error.transport.nothing_to_resend', 'ru', 'Транспорт уже передан в WMS — повторять нечего'),
    ('error.transport.nothing_to_resend', 'en', 'The transport details have already been sent to the WMS')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  v text;
  missing text;
BEGIN
  FOREACH v IN ARRAY ARRAY['v_receipts', 'v_orders', 'v_transfers'] LOOP
    IF (SELECT reloptions FROM pg_class WHERE oid = ('dbo.' || v)::regclass)
         IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
      RAISE EXCEPTION '% потеряло security_invoker', v;
    END IF;
    SELECT string_agg(c, ', ') INTO missing
      FROM unnest(ARRAY['transport_state', 'transport_sent_at', 'transport_error',
                        'allows_transport_edit']) AS c
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'dbo' AND table_name = v AND column_name = c);
    IF missing IS NOT NULL THEN
      RAISE EXCEPTION 'в % нет колонок: %', v, missing;
    END IF;
  END LOOP;
END
$check$;
