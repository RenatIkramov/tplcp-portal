-- V16__document_lifecycle.sql
-- Этап 7, решение Р5: жизненный цикл документа и граница ответственности с WMS.
SET search_path = dbo;

-- =====================================================================
-- 1. Владелец строки: черновик портала или копия документа WMS
-- =====================================================================
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['receipts','orders','transfers'] LOOP
    EXECUTE format($f$
      ALTER TABLE %I
        ADD COLUMN origin          varchar(10) NOT NULL DEFAULT 'portal',
        ADD COLUMN wms_status_raw  varchar(100),
        ADD COLUMN confirmed_at    timestamptz,
        ADD COLUMN created_by      uuid,
        ADD COLUMN created_at      timestamptz NOT NULL DEFAULT now(),
        ADD COLUMN updated_by      uuid,
        ADD COLUMN updated_at      timestamptz
    $f$, t);
    EXECUTE format($f$
      ALTER TABLE %I ADD CONSTRAINT ck_%s_origin CHECK (origin IN ('portal','wms'))
    $f$, t, t);
    EXECUTE format($f$
      ALTER TABLE %I ADD CONSTRAINT fk_%s_created_by
        FOREIGN KEY (created_by) REFERENCES app_user(user_id)
    $f$, t, t);
    EXECUTE format($f$
      ALTER TABLE %I ADD CONSTRAINT fk_%s_updated_by
        FOREIGN KEY (updated_by) REFERENCES app_user(user_id)
    $f$, t, t);
    -- Сопоставление с документом WMS: номер WMS уникален в рамках поклажедателя.
    -- Частичный индекс — у черновиков номера WMS ещё нет.
    EXECUTE format($f$
      CREATE UNIQUE INDEX uq_%s_wms_number ON %I (depositor_id, doc_number)
        WHERE doc_number IS NOT NULL
    $f$, t, t);
    EXECUTE format($f$COMMENT ON COLUMN %I.origin IS
      'portal — черновик, владелец портал; wms — копия документа WMS (Р5)'$f$, t);
    EXECUTE format($f$COMMENT ON COLUMN %I.wms_status_raw IS
      'Статус в том виде, в каком его прислала WMS — показывается, если код неизвестен'$f$, t);
  END LOOP;
END $$;

-- Человекочитаемый номер черновика. Нумерация сквозная по порталу (решение Р2).
CREATE SEQUENCE IF NOT EXISTS seq_receipt_no  START 1000;
CREATE SEQUENCE IF NOT EXISTS seq_order_no    START 1000;
CREATE SEQUENCE IF NOT EXISTS seq_transfer_no START 1000;
GRANT USAGE ON SEQUENCE seq_receipt_no, seq_order_no, seq_transfer_no TO portal_app;

CREATE OR REPLACE FUNCTION next_document_no(p_type text) RETURNS varchar
LANGUAGE sql VOLATILE SET search_path = dbo AS $$
  SELECT CASE p_type
    WHEN 'receipts'  THEN 'ПН-' || lpad(nextval('seq_receipt_no')::text, 6, '0')
    WHEN 'orders'    THEN 'ЗО-' || lpad(nextval('seq_order_no')::text, 6, '0')
    WHEN 'transfers' THEN 'ПМ-' || lpad(nextval('seq_transfer_no')::text, 6, '0')
  END::varchar
$$;
GRANT EXECUTE ON FUNCTION next_document_no(text) TO portal_app;

-- =====================================================================
-- 2. Новые статусы: отправлен в WMS и ошибка отправки
-- =====================================================================
ALTER TABLE enum_status
  ADD COLUMN allows_transport_edit boolean NOT NULL DEFAULT false,
  ADD COLUMN is_auto_created       boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN enum_status.allows_transport_edit IS
  'В этом статусе транспортный раздел редактируется из портала (Р5)';
COMMENT ON COLUMN enum_status.is_auto_created IS
  'Статус заведён автоматически при получении неизвестного кода от WMS';

INSERT INTO enum_status (id, object_type_id, code, name, sort_order, is_active) VALUES
  (16,1,'sent_to_wms','Отправлен в WMS',15,true),
  (17,1,'send_failed','Ошибка отправки',16,true),
  (26,2,'sent_to_wms','Отправлен в WMS',15,true),
  (27,2,'send_failed','Ошибка отправки',16,true),
  (36,3,'sent_to_wms','Отправлен в WMS',15,true),
  (37,3,'send_failed','Ошибка отправки',16,true);

-- Где транспорт редактируется.
-- Поступления: только «Ожидает приёмки».
UPDATE enum_status SET allows_transport_edit = true
 WHERE object_type_id = 1 AND code = 'awaiting';
-- Заказы: предварительно «В резерве» и «Комплектуется» — перечень уточняется
-- с заказчиком, меняется строкой UPDATE без правки кода.
UPDATE enum_status SET allows_transport_edit = true
 WHERE object_type_id = 2 AND code IN ('reserved','picking');

-- =====================================================================
-- 3. Право редактировать транспорт — не у всех пользователей
-- =====================================================================
ALTER TABLE user_settings
  ADD COLUMN can_edit_transport boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN user_settings.can_edit_transport IS
  'Пользователь может вносить и обновлять транспортную информацию (Р5)';

-- =====================================================================
-- 4. Товары, не известные WMS
-- =====================================================================
ALTER TABLE sku
  ADD COLUMN is_in_wms boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN sku.is_in_wms IS
  'Позиция существует в WMS. Документ с позициями без этого признака '
  'нельзя подтвердить (Р5)';
-- Демо-данные: всё, что пришло из синхронизации, считаем известным WMS
UPDATE sku SET is_in_wms = true WHERE synced_at IS NOT NULL;

-- =====================================================================
-- 5. Представления: отдаём новые поля экранам
-- =====================================================================
DROP VIEW IF EXISTS v_receipts;
CREATE VIEW v_receipts AS
SELECT r.doc_id, r.document_no, r.depositor_id, r.doc_number, r.erp_number,
       r.origin, r.confirmed_at,
       r.doc_date, r.eta_date, r.arrival_date, r.receiving_end_date,
       COALESCE(st.name, r.wms_status_raw) AS status_name,
       st.code AS status_code, r.wms_status_raw,
       COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
       dt.name AS doc_type_name, r.doc_type_id,
       sp.name AS supplier_name, r.supplier_id,
       w.name  AS warehouse_name, r.warehouse_id,
       a.full_name AS author_name,
       r.accompanying_doc_number, r.invoice_number, r.gtd, r.trip, r.comment,
       r.line_count, r.total_pieces, r.cargo_places, r.pallet_count,
       r.weight_kg, r.volume_m3,
       r.carrier, r.transport_type, r.transport_brand, r.car_number,
       r.trailer_number, r.tech_passport_number, r.container_number, r.seal_number,
       r.driver_name, r.driver_phone, r.driver_id_number, r.driver_inn,
       r.custom_field_1, r.custom_field_2, r.custom_field_3, r.custom_field_4,
       r.custom_field_5, r.custom_field_6, r.custom_field_7, r.custom_field_8,
       r.custom_field_9, r.custom_field_10,
       r.created_at, r.updated_at,
       -- мешают ли подтверждению позиции, неизвестные WMS
       EXISTS (SELECT 1 FROM receipt_details d
                JOIN sku s ON s.sku_id = d.sku_id
               WHERE d.doc_id = r.doc_id AND NOT s.is_in_wms) AS has_unknown_sku
FROM receipts r
LEFT JOIN enum_status st           ON st.id = r.status_id
LEFT JOIN enum_receipt_doc_type dt ON dt.id = r.doc_type_id
LEFT JOIN supplier sp              ON sp.supplier_id = r.supplier_id
LEFT JOIN warehouse w              ON w.warehouse_id = r.warehouse_id
LEFT JOIN app_user a               ON a.user_id = r.author_id;

DROP VIEW IF EXISTS v_orders;
CREATE VIEW v_orders AS
SELECT o.doc_id, o.document_no, o.depositor_id, o.doc_number, o.erp_number,
       o.origin, o.confirmed_at,
       o.doc_date, o.planned_ship_date, o.ship_date, o.delivery_date,
       COALESCE(st.name, o.wms_status_raw) AS status_name,
       st.code AS status_code, o.wms_status_raw,
       COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
       ot.name AS order_type_name, o.order_type_id,
       c.name  AS consignee_name, o.consignee_id,
       dp.address AS delivery_address, o.delivery_point_id, o.consignee_contact,
       w.name  AS warehouse_name, o.warehouse_id,
       a.full_name AS author_name,
       o.trip, o.comment,
       o.line_count, o.total_pieces, o.cargo_places, o.pallet_count,
       o.weight_kg, o.volume_m3,
       o.carrier, o.transport_type, o.transport_brand, o.car_number,
       o.trailer_number, o.tech_passport_number, o.container_number, o.seal_number,
       o.driver_name, o.driver_phone, o.driver_id_number, o.driver_inn,
       o.custom_field_1, o.custom_field_2, o.custom_field_3, o.custom_field_4,
       o.custom_field_5, o.custom_field_6, o.custom_field_7, o.custom_field_8,
       o.custom_field_9, o.custom_field_10,
       o.created_at, o.updated_at,
       EXISTS (SELECT 1 FROM order_details d
                JOIN sku s ON s.sku_id = d.sku_id
               WHERE d.doc_id = o.doc_id AND NOT s.is_in_wms) AS has_unknown_sku
FROM orders o
LEFT JOIN enum_status st     ON st.id = o.status_id
LEFT JOIN enum_order_type ot ON ot.id = o.order_type_id
LEFT JOIN consignee c        ON c.consignee_id = o.consignee_id
LEFT JOIN delivery_point dp  ON dp.address_id = o.delivery_point_id
LEFT JOIN warehouse w        ON w.warehouse_id = o.warehouse_id
LEFT JOIN app_user a         ON a.user_id = o.author_id;

DROP VIEW IF EXISTS v_transfers;
CREATE VIEW v_transfers AS
SELECT t.doc_id, t.document_no, t.depositor_id, t.doc_number, t.erp_number,
       t.origin, t.confirmed_at,
       t.doc_date, t.ship_date, t.received_date,
       COALESCE(st.name, t.wms_status_raw) AS status_name,
       st.code AS status_code, t.wms_status_raw,
       COALESCE(st.allows_transport_edit, false) AS allows_transport_edit,
       wf.name AS from_warehouse_name, t.from_warehouse_id,
       wt.name AS to_warehouse_name,   t.to_warehouse_id,
       a.full_name AS author_name,
       t.trip, t.comment,
       t.line_count, t.total_pieces, t.cargo_places, t.pallet_count,
       t.weight_kg, t.volume_m3,
       t.carrier, t.transport_type, t.transport_brand, t.car_number,
       t.trailer_number, t.tech_passport_number, t.container_number, t.seal_number,
       t.driver_name, t.driver_phone, t.driver_id_number, t.driver_inn,
       t.custom_field_1, t.custom_field_2, t.custom_field_3, t.custom_field_4,
       t.custom_field_5, t.custom_field_6, t.custom_field_7, t.custom_field_8,
       t.custom_field_9, t.custom_field_10,
       t.created_at, t.updated_at,
       EXISTS (SELECT 1 FROM transfer_details d
                JOIN sku s ON s.sku_id = d.sku_id
               WHERE d.doc_id = t.doc_id AND NOT s.is_in_wms) AS has_unknown_sku
FROM transfers t
LEFT JOIN enum_status st ON st.id = t.status_id
LEFT JOIN warehouse wf   ON wf.warehouse_id = t.from_warehouse_id
LEFT JOIN warehouse wt   ON wt.warehouse_id = t.to_warehouse_id
LEFT JOIN app_user a     ON a.user_id = t.author_id;

-- строки состава должны показывать, известна ли позиция WMS
DROP VIEW IF EXISTS v_receipt_lines;
CREATE VIEW v_receipt_lines AS
SELECT rd.doc_id, rd.line_number,
       rd.sku_id, s.code AS sku_code, s.name AS sku_name, u.symbol AS unit_name,
       bool_and(s.is_in_wms) AS sku_in_wms,
       MAX(rd.expected_qty)      AS expected_qty,
       MAX(rd.expected_qty_wms)  AS expected_qty_wms,
       SUM(rd.accepted_qty)      AS accepted_qty,
       SUM(rd.defect_qty)        AS defect_qty,
       MAX(rd.price)             AS price,
       MAX(cur.code)             AS currency_code,
       MAX(rd.price) * COALESCE(SUM(rd.accepted_qty), MAX(rd.expected_qty)) AS line_sum,
       COUNT(rd.batch_number)    AS batch_count,
       MAX(rd.line_comment)      AS line_comment
FROM receipt_details rd
LEFT JOIN sku s             ON s.sku_id = rd.sku_id
LEFT JOIN enum_uom u        ON u.id = s.uom_id
LEFT JOIN enum_currency cur ON cur.id = rd.currency_id
GROUP BY rd.doc_id, rd.line_number, rd.sku_id, s.code, s.name, u.symbol;

DROP VIEW IF EXISTS v_order_lines;
CREATE VIEW v_order_lines AS
SELECT od.doc_id, od.line_number,
       od.sku_id, s.code AS sku_code, s.name AS sku_name, u.symbol AS unit_name,
       bool_and(s.is_in_wms) AS sku_in_wms,
       MAX(od.ordered_qty)      AS ordered_qty,
       MAX(od.ordered_qty_wms)  AS ordered_qty_wms,
       SUM(od.picked_qty)       AS picked_qty,
       SUM(od.weight_kg)        AS weight_kg,
       MAX(od.price)            AS price,
       MAX(cur.code)            AS currency_code,
       MAX(od.price) * COALESCE(SUM(od.picked_qty), MAX(od.ordered_qty)) AS line_sum,
       COUNT(od.batch_number)   AS batch_count,
       MAX(od.line_comment)     AS line_comment
FROM order_details od
LEFT JOIN sku s             ON s.sku_id = od.sku_id
LEFT JOIN enum_uom u        ON u.id = s.uom_id
LEFT JOIN enum_currency cur ON cur.id = od.currency_id
GROUP BY od.doc_id, od.line_number, od.sku_id, s.code, s.name, u.symbol;

ALTER VIEW v_receipts SET (security_invoker = true);
ALTER VIEW v_orders SET (security_invoker = true);
ALTER VIEW v_transfers SET (security_invoker = true);
ALTER VIEW v_receipt_lines SET (security_invoker = true);
ALTER VIEW v_order_lines SET (security_invoker = true);

-- =====================================================================
-- 6. Приём неизвестного статуса от WMS
-- =====================================================================
-- Возвращает id статуса по коду; если кода нет — заводит его автоматически
-- с пометкой is_auto_created, чтобы фильтры и отчёты не ломались, и чтобы
-- администратор увидел, что появилось новое состояние (Р5).
CREATE OR REPLACE FUNCTION resolve_wms_status(p_object_type_id smallint,
                                              p_code text,
                                              p_name text DEFAULT NULL)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE v_id integer;
BEGIN
  SELECT id INTO v_id FROM enum_status
   WHERE object_type_id = p_object_type_id AND code = p_code;
  IF v_id IS NOT NULL THEN
    RETURN v_id;
  END IF;

  SELECT COALESCE(MAX(id), 0) + 1 INTO v_id FROM enum_status;
  INSERT INTO enum_status (id, object_type_id, code, name, sort_order,
                           is_active, is_auto_created)
  VALUES (v_id, p_object_type_id, p_code,
          COALESCE(p_name, p_code), 900, true, true);
  RETURN v_id;
END $$;
GRANT EXECUTE ON FUNCTION resolve_wms_status(smallint, text, text) TO portal_app;
COMMENT ON FUNCTION resolve_wms_status(smallint, text, text) IS
  'Код статуса от WMS → id. Неизвестный код заводится автоматически (Р5)';

-- Демо-данные приведём в соответствие: существующие документы пришли из WMS
UPDATE receipts  SET origin = 'wms' WHERE status_id <> 11;
UPDATE orders    SET origin = 'wms' WHERE status_id <> 21;
UPDATE transfers SET origin = 'wms' WHERE status_id <> 31;
