-- =====================================================================
-- V62. Дата создания и дата отгрузки — с временем до минут
-- =====================================================================
-- ЗАЧЕМ. «Дата создания» и «Дата отгрузки» хранились типом date, то есть
-- сутками. Для склада этого мало: в один день приходит и уходит несколько
-- машин, и «когда именно» — не праздный вопрос ни при разборе
-- расхождений, ни при сверке с перевозчиком. Обе становятся отметками
-- времени с точностью до минуты.
--
-- Планируемая дата отгрузки остаётся ДАТОЙ нарочно: это обещание на день,
-- а не событие. Обещать минуту, которую никто не выдержит, значит
-- заложить в документ заведомо неверные данные.
--
-- ПОЧЕМУ timestamptz, а не timestamp. Так уже хранятся created_at,
-- confirmed_at и synced_at — все отметки времени в портале одного типа,
-- и смешивать два было бы приглашением ошибиться. Часовой пояс для
-- момента времени не искажение, а смысл: отгрузка произошла в конкретную
-- минуту, как бы её ни читали.
--
-- ОСТОРОЖНО, ГРАБЛИ. Сравнение «по день» с отметкой времени работает
-- не так, как кажется: doc_date <= DATE '2026-12-01' отсечёт всё,
-- что случилось в этот день позже полуночи. Поэтому в коде фильтр
-- списка документов переписан на doc_date < dateTo + 1 день.
-- Та же ловушка ждёт любой новый запрос по этим колонкам.
--
-- Представления приходится удалять и создавать заново: Postgres
-- не меняет тип колонки, от которой зависит представление. При
-- пересоздании обязателен security_invoker — без него RLS перестанет
-- действовать от имени вызывающего, и остатки с документами всех
-- клиентов откроются каждому. Определения перенесены без изменений,
-- меняется только тип двух колонок под ними.
SET search_path = dbo;

DROP VIEW IF EXISTS v_receipts;
DROP VIEW IF EXISTS v_orders;
DROP VIEW IF EXISTS v_transfers;

-- ---------------------------------------------------------------------
-- 1. Тип колонок
-- ---------------------------------------------------------------------
-- Приведение date -> timestamptz ставит полночь по часовому поясу базы.
-- Для уже накопленных документов это ровно то, что о них известно:
-- день знаем, минуту — нет.
ALTER TABLE receipts  ALTER COLUMN doc_date  TYPE timestamptz USING doc_date::timestamptz;
ALTER TABLE orders    ALTER COLUMN doc_date  TYPE timestamptz USING doc_date::timestamptz;
ALTER TABLE orders    ALTER COLUMN ship_date TYPE timestamptz USING ship_date::timestamptz;
ALTER TABLE transfers ALTER COLUMN doc_date  TYPE timestamptz USING doc_date::timestamptz;
ALTER TABLE transfers ALTER COLUMN ship_date TYPE timestamptz USING ship_date::timestamptz;

COMMENT ON COLUMN receipts.doc_date  IS 'Дата и время создания документа';
COMMENT ON COLUMN orders.doc_date    IS 'Дата и время создания документа';
COMMENT ON COLUMN orders.ship_date   IS 'Дата и время отгрузки (ставит WMS)';
COMMENT ON COLUMN transfers.doc_date IS 'Дата и время создания документа';
COMMENT ON COLUMN transfers.ship_date IS 'Дата и время отгрузки (ставит WMS)';

-- ---------------------------------------------------------------------
-- 2. Представления — те же, поверх новых типов
-- ---------------------------------------------------------------------

CREATE VIEW v_receipts WITH (security_invoker = true) AS
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
          WHERE d.doc_id = r.doc_id AND NOT s.is_in_wms)) AS has_unknown_sku
   FROM receipts r
     LEFT JOIN enum_status st ON st.id = r.status_id
     LEFT JOIN enum_receipt_doc_type dt ON dt.id = r.doc_type_id
     LEFT JOIN supplier sp ON sp.supplier_id = r.supplier_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = r.warehouse_id::text
     LEFT JOIN app_user a ON a.user_id = r.author_id;

CREATE VIEW v_orders WITH (security_invoker = true) AS
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
          WHERE d.doc_id = o.doc_id AND NOT s.is_in_wms)) AS has_unknown_sku
   FROM orders o
     LEFT JOIN enum_status st ON st.id = o.status_id
     LEFT JOIN enum_order_type ot ON ot.id = o.order_type_id
     LEFT JOIN consignee c ON c.consignee_id = o.consignee_id
     LEFT JOIN delivery_point dp ON dp.address_id = o.delivery_point_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = o.warehouse_id::text
     LEFT JOIN app_user a ON a.user_id = o.author_id;

CREATE VIEW v_transfers WITH (security_invoker = true) AS
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
          WHERE d.doc_id = t.doc_id AND NOT s.is_in_wms)) AS has_unknown_sku
   FROM transfers t
     LEFT JOIN enum_status st ON st.id = t.status_id
     LEFT JOIN warehouse wf ON wf.warehouse_id::text = t.from_warehouse_id::text
     LEFT JOIN warehouse wt ON wt.warehouse_id::text = t.to_warehouse_id::text
     LEFT JOIN app_user a ON a.user_id = t.author_id;
