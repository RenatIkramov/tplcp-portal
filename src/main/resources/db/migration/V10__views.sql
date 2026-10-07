-- V10__views.sql
-- Представления портала. Каждое питает один экран; внешние ключи разрешены
-- в читаемые значения, чтобы приложению не приходилось джойнить ради подписи.
-- Решение Р1: *_lines — агрегаты по номеру строки, *_line_details — записи партий.
SET search_path = dbo;

-- =====================================================================
-- Справочники
-- =====================================================================

CREATE VIEW v_depositors AS
SELECT d.depositor_id, d.code, d.org_name, d.bin, d.legal_address,
       d.phone, d.email, d.contract_number, d.contract_valid_until,
       d.operator_id, o.name AS operator_name
FROM depositor d
LEFT JOIN operator o ON o.operator_id = d.operator_id;

CREATE VIEW v_warehouses AS
SELECT warehouse_id, code, name, address, responsible, phone, email
FROM warehouse;

CREATE VIEW v_suppliers AS
SELECT s.supplier_id, s.depositor_id, s.code, s.name, s.bin, s.legal_address,
       s.contact_name, s.contact_phone, s.contact_email
FROM supplier s;

CREATE VIEW v_consignees AS
SELECT c.consignee_id, c.depositor_id, c.code, c.name, c.bin, c.legal_address,
       c.contact_name, c.contact_phone, c.contact_email,
       (SELECT dp.address FROM delivery_point dp
         WHERE dp.consignee_id = c.consignee_id AND dp.is_default
         LIMIT 1) AS default_address
FROM consignee c;

CREATE VIEW v_users AS
SELECT u.user_id, u.login, u.full_name, r.name AS role_name, r.code AS role_code,
       u.position, u.organization, u.phone, u.is_active, u.last_login_at
FROM app_user u
LEFT JOIN enum_user_role r ON r.id = u.role_id;

-- Экран «Доступы менеджеров»: кто к каким поклажедателям и складам допущен
CREATE VIEW v_user_access AS
SELECT u.user_id, u.full_name, u.login, r.name AS role_name,
       (SELECT string_agg(d.org_name, ', ' ORDER BY d.org_name)
          FROM user_depositor_access a
          JOIN depositor d ON d.depositor_id = a.depositor_id
         WHERE a.user_id = u.user_id) AS depositors,
       (SELECT string_agg(w.name, ', ' ORDER BY w.name)
          FROM user_warehouse_access a
          JOIN warehouse w ON w.warehouse_id = a.warehouse_id
         WHERE a.user_id = u.user_id) AS warehouses,
       u.is_active
FROM app_user u
LEFT JOIN enum_user_role r ON r.id = u.role_id;

-- Полная карточка товара: все поля SKU + разрешённые ссылки
CREATE VIEW v_sku_full AS
SELECT s.sku_id, s.depositor_id, d.org_name AS depositor_name,
       s.item_code, s.code, s.name, s.item_full_name,
       s.barcode, u.symbol AS uom_name,
       (SELECT p.packaging_code FROM product_packaging p
         WHERE p.packaging_id = s.order_unit_id) AS order_unit_name,
       g.name AS item_group_name, t.name AS item_type_name,
       s.has_packaging, s.length, s.width, s.height, s.volume,
       s.weight_gross, s.weight_net,
       s.manufacturer, s.country_origin, s.brand,
       s.is_fragile, s.is_serial_tracked, s.batch_tracking, s.expiry_tracking,
       s.min_storage_temp, s.max_storage_temp, s.min_humidity, s.max_humidity,
       s.min_days_for_receipt, s.min_days_for_reservation, s.shelf_life_days,
       s.color, s.size, s.abc_class, s.xyz_class,
       s.hs_code, s.enstru_code, s.cost, cur.code AS currency_code,
       s.is_kit, s.is_marking_required, s.is_excise, s.is_controlled, s.is_wms_active,
       hc.name AS hazard_class_name, hg.name AS hazard_group_name,
       st.name AS status_name, s.comment,
       s.created_by, s.created_at, s.updated_by, s.updated_at,
       s.custom_attr1, s.custom_attr2, s.custom_attr3, s.custom_attr4, s.custom_attr5,
       s.custom_attr6, s.custom_attr7, s.custom_attr8, s.custom_attr9, s.custom_attr10,
       s.synced_at
FROM sku s
LEFT JOIN depositor d        ON d.depositor_id = s.depositor_id
LEFT JOIN enum_uom u         ON u.id = s.uom_id
LEFT JOIN product_group g    ON g.id = s.item_group_id
LEFT JOIN product_type t     ON t.id = s.item_type_id
LEFT JOIN enum_currency cur  ON cur.id = s.currency_id
LEFT JOIN enum_hazard_class hc ON hc.id = s.hazard_class_id
LEFT JOIN enum_hazard_group hg ON hg.id = s.hazard_group_id
LEFT JOIN enum_status st     ON st.id = s.status_id;

-- =====================================================================
-- Запасы
-- =====================================================================

-- Экран «Запасы»: одна строка = товар + склад (агрегат по партиям)
CREATE VIEW v_stock_lines AS
SELECT st.depositor_id, st.sku_id, s.code AS sku_code, s.name AS sku_name,
       st.warehouse_id, w.name AS warehouse_name, u.symbol AS unit_name,
       SUM(st.quantity)                      AS quantity,
       SUM(st.reserved_qty)                  AS reserved_qty,
       SUM(st.blocked_qty)                   AS blocked_qty,
       SUM(st.available_qty)                 AS available_qty,
       COALESCE(inc.incoming_qty, 0)         AS incoming_qty,
       COUNT(*)                              AS batch_count,
       MAX(st.synced_at)                     AS synced_at
FROM stock st
LEFT JOIN sku s        ON s.sku_id = st.sku_id
LEFT JOIN warehouse w  ON w.warehouse_id = st.warehouse_id
LEFT JOIN enum_uom u   ON u.id = st.unit_id
LEFT JOIN LATERAL (
  -- §8.3.2: «Ожидается» = количество по подтверждённым, но не принятым поступлениям.
  -- В демо это псевдослучайное число; здесь — настоящая агрегация.
  SELECT SUM(rd.expected_qty) AS incoming_qty
  FROM receipt_details rd
  JOIN receipts r      ON r.doc_id = rd.doc_id
  JOIN enum_status es  ON es.id = r.status_id
  WHERE rd.sku_id = st.sku_id
    AND r.warehouse_id = st.warehouse_id
    AND rd.batch_number IS NULL              -- плановая запись строки (решение Р1)
    AND es.code NOT IN ('draft','received')
) inc ON true
GROUP BY st.depositor_id, st.sku_id, s.code, s.name,
         st.warehouse_id, w.name, u.symbol, inc.incoming_qty;

-- Детализация по партиям для одного товара
CREATE VIEW v_stock_details AS
SELECT st.register_id, st.depositor_id, st.sku_id, s.code AS sku_code, s.name AS sku_name,
       st.warehouse_id, w.name AS warehouse_name,
       st.batch_number, st.series_number, st.expiry_date, st.quality,
       br.name AS block_reason_name,
       st.quantity, st.reserved_qty, st.blocked_qty, st.available_qty,
       u.symbol AS unit_name, st.updated_at, st.synced_at
FROM stock st
LEFT JOIN sku s                 ON s.sku_id = st.sku_id
LEFT JOIN warehouse w           ON w.warehouse_id = st.warehouse_id
LEFT JOIN enum_uom u            ON u.id = st.unit_id
LEFT JOIN enum_block_reason br  ON br.id = st.block_reason_id;

-- Единственное место расчёта «доступно» (§8.3.2).
-- ВНИМАНИЕ (решение Р4): если WMS отдаёт доступное количество сама,
-- брать его из stock.available_qty, а не пересчитывать здесь.
CREATE VIEW v_stock_available AS
SELECT st.depositor_id, st.sku_id, st.warehouse_id,
       SUM(st.quantity) AS quantity,
       SUM(st.reserved_qty) AS reserved_qty,
       SUM(st.blocked_qty) AS blocked_qty,
       SUM(CASE WHEN st.quality = 'Годный' AND st.block_reason_id IS NULL
                THEN st.quantity - COALESCE(st.reserved_qty,0) ELSE 0 END) AS available_qty,
       COALESCE((
         SELECT SUM(rd.expected_qty)
         FROM receipt_details rd
         JOIN receipts r     ON r.doc_id = rd.doc_id
         JOIN enum_status es ON es.id = r.status_id
         WHERE rd.sku_id = st.sku_id
           AND r.warehouse_id = st.warehouse_id
           AND rd.batch_number IS NULL
           AND es.code NOT IN ('draft','received')
       ), 0) AS incoming_qty,
       MAX(st.synced_at) AS synced_at
FROM stock st
GROUP BY st.depositor_id, st.sku_id, st.warehouse_id;

-- =====================================================================
-- Документы: заголовки
-- =====================================================================

CREATE VIEW v_receipts AS
SELECT r.doc_id, r.document_no, r.depositor_id, r.doc_number, r.erp_number,
       r.doc_date, r.eta_date, r.arrival_date, r.receiving_end_date,
       st.name AS status_name, st.code AS status_code,
       dt.name AS doc_type_name,
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
       r.custom_field_9, r.custom_field_10
FROM receipts r
LEFT JOIN enum_status st           ON st.id = r.status_id
LEFT JOIN enum_receipt_doc_type dt ON dt.id = r.doc_type_id
LEFT JOIN supplier sp              ON sp.supplier_id = r.supplier_id
LEFT JOIN warehouse w              ON w.warehouse_id = r.warehouse_id
LEFT JOIN app_user a               ON a.user_id = r.author_id;

CREATE VIEW v_orders AS
SELECT o.doc_id, o.document_no, o.depositor_id, o.doc_number, o.erp_number,
       o.doc_date, o.planned_ship_date, o.ship_date, o.delivery_date,
       st.name AS status_name, st.code AS status_code,
       ot.name AS order_type_name,
       c.name  AS consignee_name, o.consignee_id,
       dp.address AS delivery_address, o.consignee_contact,
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
       o.custom_field_9, o.custom_field_10
FROM orders o
LEFT JOIN enum_status st     ON st.id = o.status_id
LEFT JOIN enum_order_type ot ON ot.id = o.order_type_id
LEFT JOIN consignee c        ON c.consignee_id = o.consignee_id
LEFT JOIN delivery_point dp  ON dp.address_id = o.delivery_point_id
LEFT JOIN warehouse w        ON w.warehouse_id = o.warehouse_id
LEFT JOIN app_user a         ON a.user_id = o.author_id;

CREATE VIEW v_transfers AS
SELECT t.doc_id, t.document_no, t.depositor_id, t.doc_number, t.erp_number,
       t.doc_date, t.ship_date, t.received_date,
       st.name AS status_name, st.code AS status_code,
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
       t.custom_field_9, t.custom_field_10
FROM transfers t
LEFT JOIN enum_status st ON st.id = t.status_id
LEFT JOIN warehouse wf   ON wf.warehouse_id = t.from_warehouse_id
LEFT JOIN warehouse wt   ON wt.warehouse_id = t.to_warehouse_id
LEFT JOIN app_user a     ON a.user_id = t.author_id;

-- =====================================================================
-- Документы: состав (агрегат по строке) и детализация (записи партий)
-- Решение Р1: план берётся с записи без партии (MAX), факт суммируется.
-- =====================================================================

CREATE VIEW v_receipt_lines AS
SELECT rd.doc_id, rd.line_number,
       rd.sku_id, s.code AS sku_code, s.name AS sku_name, u.symbol AS unit_name,
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
LEFT JOIN sku s            ON s.sku_id = rd.sku_id
LEFT JOIN enum_uom u       ON u.id = s.uom_id
LEFT JOIN enum_currency cur ON cur.id = rd.currency_id
GROUP BY rd.doc_id, rd.line_number, rd.sku_id, s.code, s.name, u.symbol;

CREATE VIEW v_receipt_line_details AS
SELECT rd.line_id, rd.doc_id, rd.line_number, rd.sku_id,
       s.code AS sku_code, s.name AS sku_name,
       rd.batch_number, rd.series_number, rd.expiry_date, rd.quality,
       rd.accepted_qty, rd.defect_qty, rd.line_comment
FROM receipt_details rd
LEFT JOIN sku s ON s.sku_id = rd.sku_id
WHERE rd.batch_number IS NOT NULL;

CREATE VIEW v_order_lines AS
SELECT od.doc_id, od.line_number,
       od.sku_id, s.code AS sku_code, s.name AS sku_name, u.symbol AS unit_name,
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
LEFT JOIN sku s            ON s.sku_id = od.sku_id
LEFT JOIN enum_uom u       ON u.id = s.uom_id
LEFT JOIN enum_currency cur ON cur.id = od.currency_id
GROUP BY od.doc_id, od.line_number, od.sku_id, s.code, s.name, u.symbol;

CREATE VIEW v_order_line_details AS
SELECT od.line_id, od.doc_id, od.line_number, od.sku_id,
       s.code AS sku_code, s.name AS sku_name,
       od.batch_number, od.series_number, od.expiry_date, od.quality,
       od.picked_qty, od.weight_kg, od.line_comment
FROM order_details od
LEFT JOIN sku s ON s.sku_id = od.sku_id
WHERE od.batch_number IS NOT NULL;

CREATE VIEW v_transfer_lines AS
SELECT td.doc_id, td.line_number,
       td.sku_id, s.code AS sku_code, s.name AS sku_name, u.symbol AS unit_name,
       SUM(td.qty)            AS qty,
       SUM(td.qty_wms)        AS qty_wms,
       COUNT(td.batch_number) AS batch_count,
       MAX(td.line_comment)   AS line_comment
FROM transfer_details td
LEFT JOIN sku s      ON s.sku_id = td.sku_id
LEFT JOIN enum_uom u ON u.id = s.uom_id
GROUP BY td.doc_id, td.line_number, td.sku_id, s.code, s.name, u.symbol;

CREATE VIEW v_transfer_line_details AS
SELECT td.line_id, td.doc_id, td.line_number, td.sku_id,
       s.code AS sku_code, s.name AS sku_name,
       td.batch_number, td.series_number, td.expiry_date, td.quality,
       td.qty, td.qty_wms, td.line_comment
FROM transfer_details td
LEFT JOIN sku s ON s.sku_id = td.sku_id
WHERE td.batch_number IS NOT NULL;

-- =====================================================================
-- Биллинг и фото
-- =====================================================================

CREATE VIEW v_service_tariffs AS
SELECT t.id, t.depositor_id, t.service, t.rate, t.unit, t.frequency
FROM service_tariff t;

CREATE VIEW v_invoices AS
SELECT i.id, i.depositor_id, i.invoice_number, i.period_from, i.period_to,
       i.issue_date, i.amount_no_vat, i.amount_vat,
       i.amount_no_vat + COALESCE(i.amount_vat,0) AS amount_with_vat,
       st.name AS status_name, st.code AS status_code,
       (i.pdf_file IS NOT NULL) AS has_pdf
FROM invoice i
LEFT JOIN enum_status st ON st.id = i.status_id;

-- §10.3: object_key наружу не отдаётся — подписанная ссылка выдаётся приложением
CREATE VIEW v_document_photos AS
SELECT p.id, p.depositor_id, p.doc_id, ot.code AS object_type_code,
       p.file_name, p.caption, p.mime_type, p.file_size, p.source,
       u.full_name AS uploaded_by_name, p.uploaded_at, p.sort_order
FROM document_photo p
LEFT JOIN enum_object_type ot ON ot.id = p.object_type_id
LEFT JOIN app_user u          ON u.user_id = p.uploaded_by;
