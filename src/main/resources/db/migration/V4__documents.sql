-- V4__documents.sql
-- Сгенерировано из словаря полей демо (Developer Settings).
-- Проверить вручную: NOT NULL, значения по умолчанию, размеры типов.

-- Поступления
CREATE TABLE receipts (
  doc_id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_no                  varchar(30),
  depositor_id                 uuid NOT NULL,
  doc_number                   varchar(30),
  erp_number                   varchar(30),
  author_id                    uuid,
  status_id                    int,
  doc_date                     date,
  warehouse_id                 varchar(20),
  trip                         varchar(30),
  comment                      text,
  doc_type_id                  smallint,
  supplier_id                  uuid,
  accompanying_doc_number      varchar(50),
  invoice_number               varchar(50),
  gtd                          varchar(50),
  eta_date                     date,
  arrival_date                 date,
  receiving_end_date           date,
  line_count                   int,
  total_pieces                 numeric(14,3),
  cargo_places                 int,
  pallet_count                 int,
  weight_kg                    numeric(12,3),
  volume_m3                    numeric(12,5),
  carrier                      varchar(255),
  transport_type               varchar(50),
  transport_brand              varchar(100),
  car_number                   varchar(20),
  trailer_number               varchar(20),
  tech_passport_number         varchar(50),
  container_number             varchar(20),
  seal_number                  varchar(20),
  driver_name                  varchar(150),
  driver_phone                 varchar(30),
  driver_id_number             varchar(30),
  driver_inn                   varchar(20),
  custom_field_1               varchar(255),
  custom_field_2               varchar(255),
  custom_field_3               varchar(255),
  custom_field_4               varchar(255),
  custom_field_5               varchar(255),
  custom_field_6               varchar(255),
  custom_field_7               varchar(255),
  custom_field_8               varchar(255),
  custom_field_9               varchar(255),
  custom_field_10              varchar(255)
);
COMMENT ON COLUMN receipts.doc_id IS 'Document ID';
COMMENT ON COLUMN receipts.document_no IS 'Document No';
COMMENT ON COLUMN receipts.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN receipts.doc_number IS 'Document Number (WMS)';
COMMENT ON COLUMN receipts.erp_number IS 'Document Number (ERP)';
COMMENT ON COLUMN receipts.author_id IS 'Author ID';
COMMENT ON COLUMN receipts.status_id IS 'Status ID';
COMMENT ON COLUMN receipts.doc_date IS 'Document Date';
COMMENT ON COLUMN receipts.warehouse_id IS 'Warehouse ID';
COMMENT ON COLUMN receipts.trip IS 'Trip Number';
COMMENT ON COLUMN receipts.comment IS 'Comment';
COMMENT ON COLUMN receipts.doc_type_id IS 'Receipt Document Type ID';
COMMENT ON COLUMN receipts.supplier_id IS 'Supplier ID';
COMMENT ON COLUMN receipts.accompanying_doc_number IS 'Accompanying Document Number';
COMMENT ON COLUMN receipts.invoice_number IS 'Invoice Number';
COMMENT ON COLUMN receipts.gtd IS 'Customs Declaration Number';
COMMENT ON COLUMN receipts.eta_date IS 'Expected Arrival Date';
COMMENT ON COLUMN receipts.arrival_date IS 'Arrival Date';
COMMENT ON COLUMN receipts.receiving_end_date IS 'Receiving End Date';
COMMENT ON COLUMN receipts.line_count IS 'Line Count';
COMMENT ON COLUMN receipts.total_pieces IS 'Total Pieces';
COMMENT ON COLUMN receipts.cargo_places IS 'Cargo Places';
COMMENT ON COLUMN receipts.pallet_count IS 'Pallet Count';
COMMENT ON COLUMN receipts.weight_kg IS 'Weight';
COMMENT ON COLUMN receipts.volume_m3 IS 'Volume';
COMMENT ON COLUMN receipts.carrier IS 'Carrier';
COMMENT ON COLUMN receipts.transport_type IS 'Transport Type';
COMMENT ON COLUMN receipts.transport_brand IS 'Transport Brand';
COMMENT ON COLUMN receipts.car_number IS 'Car Number';
COMMENT ON COLUMN receipts.trailer_number IS 'Trailer Number';
COMMENT ON COLUMN receipts.tech_passport_number IS 'Technical Passport Number';
COMMENT ON COLUMN receipts.container_number IS 'Container Number';
COMMENT ON COLUMN receipts.seal_number IS 'Seal Number';
COMMENT ON COLUMN receipts.driver_name IS 'Driver Name';
COMMENT ON COLUMN receipts.driver_phone IS 'Driver Phone';
COMMENT ON COLUMN receipts.driver_id_number IS 'Driver ID Number';
COMMENT ON COLUMN receipts.driver_inn IS 'Driver INN';
COMMENT ON COLUMN receipts.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN receipts.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN receipts.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN receipts.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN receipts.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN receipts.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN receipts.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN receipts.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN receipts.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN receipts.custom_field_10 IS 'Custom Field 10';

-- Поступления
CREATE TABLE receipt_details (
  line_id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doc_id                       uuid,
  line_number                  smallint,
  sku_id                       uuid,
  expected_qty                 numeric(14,3),
  expected_qty_wms             numeric(14,3),
  accepted_qty                 numeric(14,3),
  defect_qty                   numeric(14,3),
  batch_number                 varchar(50),
  series_number                varchar(50),
  expiry_date                  date,
  quality                      varchar(20),
  price                        numeric(14,2),
  currency_id                  smallint,
  line_comment                 text,
  custom_field_1               varchar(255),
  custom_field_2               varchar(255),
  custom_field_3               varchar(255),
  custom_field_4               varchar(255),
  custom_field_5               varchar(255),
  custom_field_6               varchar(255),
  custom_field_7               varchar(255),
  custom_field_8               varchar(255),
  custom_field_9               varchar(255),
  custom_field_10              varchar(255)
);
COMMENT ON COLUMN receipt_details.line_id IS 'Line ID';
COMMENT ON COLUMN receipt_details.doc_id IS 'Document ID';
COMMENT ON COLUMN receipt_details.line_number IS 'Line Number';
COMMENT ON COLUMN receipt_details.sku_id IS 'SKU ID';
COMMENT ON COLUMN receipt_details.expected_qty IS 'Expected Quantity';
COMMENT ON COLUMN receipt_details.expected_qty_wms IS 'Expected Quantity (WMS)';
COMMENT ON COLUMN receipt_details.accepted_qty IS 'Accepted Quantity';
COMMENT ON COLUMN receipt_details.defect_qty IS 'Defect Quantity';
COMMENT ON COLUMN receipt_details.batch_number IS 'Batch Number';
COMMENT ON COLUMN receipt_details.series_number IS 'Series Number';
COMMENT ON COLUMN receipt_details.expiry_date IS 'Expiry Date';
COMMENT ON COLUMN receipt_details.quality IS 'Quality';
COMMENT ON COLUMN receipt_details.price IS 'Price';
COMMENT ON COLUMN receipt_details.currency_id IS 'Currency ID';
COMMENT ON COLUMN receipt_details.line_comment IS 'Line Comment';
COMMENT ON COLUMN receipt_details.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN receipt_details.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN receipt_details.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN receipt_details.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN receipt_details.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN receipt_details.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN receipt_details.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN receipt_details.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN receipt_details.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN receipt_details.custom_field_10 IS 'Custom Field 10';

-- Заказы
CREATE TABLE orders (
  doc_id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_no                  varchar(30),
  depositor_id                 uuid NOT NULL,
  doc_number                   varchar(30),
  erp_number                   varchar(30),
  author_id                    uuid,
  status_id                    int,
  doc_date                     date,
  warehouse_id                 varchar(20),
  trip                         varchar(30),
  comment                      text,
  order_type_id                smallint,
  consignee_id                 uuid,
  delivery_point_id            uuid,
  consignee_contact            varchar(255),
  planned_ship_date            date,
  ship_date                    date,
  line_count                   int,
  total_pieces                 numeric(14,3),
  cargo_places                 int,
  pallet_count                 int,
  weight_kg                    numeric(12,3),
  volume_m3                    numeric(12,5),
  carrier                      varchar(255),
  transport_type               varchar(50),
  transport_brand              varchar(100),
  car_number                   varchar(20),
  trailer_number               varchar(20),
  tech_passport_number         varchar(50),
  container_number             varchar(20),
  seal_number                  varchar(20),
  driver_name                  varchar(150),
  driver_phone                 varchar(30),
  driver_id_number             varchar(30),
  driver_inn                   varchar(20),
  delivery_date                date,
  custom_field_1               varchar(255),
  custom_field_2               varchar(255),
  custom_field_3               varchar(255),
  custom_field_4               varchar(255),
  custom_field_5               varchar(255),
  custom_field_6               varchar(255),
  custom_field_7               varchar(255),
  custom_field_8               varchar(255),
  custom_field_9               varchar(255),
  custom_field_10              varchar(255)
);
COMMENT ON COLUMN orders.doc_id IS 'Document ID';
COMMENT ON COLUMN orders.document_no IS 'Document No';
COMMENT ON COLUMN orders.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN orders.doc_number IS 'Document Number (WMS)';
COMMENT ON COLUMN orders.erp_number IS 'Document Number (ERP)';
COMMENT ON COLUMN orders.author_id IS 'Author ID';
COMMENT ON COLUMN orders.status_id IS 'Status ID';
COMMENT ON COLUMN orders.doc_date IS 'Document Date';
COMMENT ON COLUMN orders.warehouse_id IS 'Warehouse ID';
COMMENT ON COLUMN orders.trip IS 'Trip Number';
COMMENT ON COLUMN orders.comment IS 'Comment';
COMMENT ON COLUMN orders.order_type_id IS 'Order Type ID';
COMMENT ON COLUMN orders.consignee_id IS 'Consignee ID';
COMMENT ON COLUMN orders.delivery_point_id IS 'Delivery Point ID';
COMMENT ON COLUMN orders.consignee_contact IS 'Consignee Contact';
COMMENT ON COLUMN orders.planned_ship_date IS 'Planned Ship Date';
COMMENT ON COLUMN orders.ship_date IS 'Ship Date';
COMMENT ON COLUMN orders.line_count IS 'Line Count';
COMMENT ON COLUMN orders.total_pieces IS 'Total Pieces';
COMMENT ON COLUMN orders.cargo_places IS 'Cargo Places';
COMMENT ON COLUMN orders.pallet_count IS 'Pallet Count';
COMMENT ON COLUMN orders.weight_kg IS 'Weight';
COMMENT ON COLUMN orders.volume_m3 IS 'Volume';
COMMENT ON COLUMN orders.carrier IS 'Carrier';
COMMENT ON COLUMN orders.transport_type IS 'Transport Type';
COMMENT ON COLUMN orders.transport_brand IS 'Transport Brand';
COMMENT ON COLUMN orders.car_number IS 'Car Number';
COMMENT ON COLUMN orders.trailer_number IS 'Trailer Number';
COMMENT ON COLUMN orders.tech_passport_number IS 'Technical Passport Number';
COMMENT ON COLUMN orders.container_number IS 'Container Number';
COMMENT ON COLUMN orders.seal_number IS 'Seal Number';
COMMENT ON COLUMN orders.driver_name IS 'Driver Name';
COMMENT ON COLUMN orders.driver_phone IS 'Driver Phone';
COMMENT ON COLUMN orders.driver_id_number IS 'Driver ID Number';
COMMENT ON COLUMN orders.driver_inn IS 'Driver INN';
COMMENT ON COLUMN orders.delivery_date IS 'Delivery Date';
COMMENT ON COLUMN orders.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN orders.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN orders.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN orders.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN orders.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN orders.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN orders.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN orders.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN orders.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN orders.custom_field_10 IS 'Custom Field 10';

-- Заказы
CREATE TABLE order_details (
  line_id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doc_id                       uuid,
  line_number                  smallint,
  sku_id                       uuid,
  ordered_qty                  numeric(14,3),
  ordered_qty_wms              numeric(14,3),
  picked_qty                   numeric(14,3),
  weight_kg                    numeric(12,3),
  batch_number                 varchar(50),
  series_number                varchar(50),
  expiry_date                  date,
  quality                      varchar(20),
  price                        numeric(14,2),
  currency_id                  smallint,
  line_comment                 text,
  custom_field_1               varchar(255),
  custom_field_2               varchar(255),
  custom_field_3               varchar(255),
  custom_field_4               varchar(255),
  custom_field_5               varchar(255),
  custom_field_6               varchar(255),
  custom_field_7               varchar(255),
  custom_field_8               varchar(255),
  custom_field_9               varchar(255),
  custom_field_10              varchar(255)
);
COMMENT ON COLUMN order_details.line_id IS 'Line ID';
COMMENT ON COLUMN order_details.doc_id IS 'Document ID';
COMMENT ON COLUMN order_details.line_number IS 'Line Number';
COMMENT ON COLUMN order_details.sku_id IS 'SKU ID';
COMMENT ON COLUMN order_details.ordered_qty IS 'Ordered Quantity';
COMMENT ON COLUMN order_details.ordered_qty_wms IS 'Ordered Quantity (WMS)';
COMMENT ON COLUMN order_details.picked_qty IS 'Picked Quantity';
COMMENT ON COLUMN order_details.weight_kg IS 'Weight';
COMMENT ON COLUMN order_details.batch_number IS 'Batch Number';
COMMENT ON COLUMN order_details.series_number IS 'Series Number';
COMMENT ON COLUMN order_details.expiry_date IS 'Expiry Date';
COMMENT ON COLUMN order_details.quality IS 'Quality';
COMMENT ON COLUMN order_details.price IS 'Price';
COMMENT ON COLUMN order_details.currency_id IS 'Currency ID';
COMMENT ON COLUMN order_details.line_comment IS 'Line Comment';
COMMENT ON COLUMN order_details.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN order_details.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN order_details.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN order_details.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN order_details.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN order_details.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN order_details.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN order_details.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN order_details.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN order_details.custom_field_10 IS 'Custom Field 10';

-- Перемещения
CREATE TABLE transfers (
  doc_id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_no                  varchar(30),
  depositor_id                 uuid NOT NULL,
  doc_number                   varchar(30),
  erp_number                   varchar(30),
  author_id                    uuid,
  status_id                    int,
  doc_date                     date,
  warehouse_id                 varchar(20),
  trip                         varchar(30),
  comment                      text,
  from_warehouse_id            varchar(20),
  to_warehouse_id              varchar(20),
  ship_date                    date,
  received_date                date,
  line_count                   int,
  total_pieces                 numeric(14,3),
  cargo_places                 int,
  pallet_count                 int,
  weight_kg                    numeric(12,3),
  volume_m3                    numeric(12,5),
  carrier                      varchar(255),
  transport_type               varchar(50),
  transport_brand              varchar(100),
  car_number                   varchar(20),
  trailer_number               varchar(20),
  tech_passport_number         varchar(50),
  container_number             varchar(20),
  seal_number                  varchar(20),
  driver_name                  varchar(150),
  driver_phone                 varchar(30),
  driver_id_number             varchar(30),
  driver_inn                   varchar(20),
  custom_field_1               varchar(255),
  custom_field_2               varchar(255),
  custom_field_3               varchar(255),
  custom_field_4               varchar(255),
  custom_field_5               varchar(255),
  custom_field_6               varchar(255),
  custom_field_7               varchar(255),
  custom_field_8               varchar(255),
  custom_field_9               varchar(255),
  custom_field_10              varchar(255)
);
COMMENT ON COLUMN transfers.doc_id IS 'Document ID';
COMMENT ON COLUMN transfers.document_no IS 'Document No';
COMMENT ON COLUMN transfers.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN transfers.doc_number IS 'Document Number (WMS)';
COMMENT ON COLUMN transfers.erp_number IS 'Document Number (ERP)';
COMMENT ON COLUMN transfers.author_id IS 'Author ID';
COMMENT ON COLUMN transfers.status_id IS 'Status ID';
COMMENT ON COLUMN transfers.doc_date IS 'Document Date';
COMMENT ON COLUMN transfers.warehouse_id IS 'Warehouse ID';
COMMENT ON COLUMN transfers.trip IS 'Trip Number';
COMMENT ON COLUMN transfers.comment IS 'Comment';
COMMENT ON COLUMN transfers.from_warehouse_id IS 'From Warehouse ID';
COMMENT ON COLUMN transfers.to_warehouse_id IS 'To Warehouse ID';
COMMENT ON COLUMN transfers.ship_date IS 'Ship Date';
COMMENT ON COLUMN transfers.received_date IS 'Received Date';
COMMENT ON COLUMN transfers.line_count IS 'Line Count';
COMMENT ON COLUMN transfers.total_pieces IS 'Total Pieces';
COMMENT ON COLUMN transfers.cargo_places IS 'Cargo Places';
COMMENT ON COLUMN transfers.pallet_count IS 'Pallet Count';
COMMENT ON COLUMN transfers.weight_kg IS 'Weight';
COMMENT ON COLUMN transfers.volume_m3 IS 'Volume';
COMMENT ON COLUMN transfers.carrier IS 'Carrier';
COMMENT ON COLUMN transfers.transport_type IS 'Transport Type';
COMMENT ON COLUMN transfers.transport_brand IS 'Transport Brand';
COMMENT ON COLUMN transfers.car_number IS 'Car Number';
COMMENT ON COLUMN transfers.trailer_number IS 'Trailer Number';
COMMENT ON COLUMN transfers.tech_passport_number IS 'Technical Passport Number';
COMMENT ON COLUMN transfers.container_number IS 'Container Number';
COMMENT ON COLUMN transfers.seal_number IS 'Seal Number';
COMMENT ON COLUMN transfers.driver_name IS 'Driver Name';
COMMENT ON COLUMN transfers.driver_phone IS 'Driver Phone';
COMMENT ON COLUMN transfers.driver_id_number IS 'Driver ID Number';
COMMENT ON COLUMN transfers.driver_inn IS 'Driver INN';
COMMENT ON COLUMN transfers.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN transfers.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN transfers.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN transfers.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN transfers.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN transfers.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN transfers.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN transfers.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN transfers.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN transfers.custom_field_10 IS 'Custom Field 10';

-- Перемещения
CREATE TABLE transfer_details (
  line_id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doc_id                       uuid,
  line_number                  smallint,
  sku_id                       uuid,
  qty                          numeric(14,3),
  qty_wms                      numeric(14,3),
  batch_number                 varchar(50),
  series_number                varchar(50),
  expiry_date                  date,
  quality                      varchar(20),
  line_comment                 text,
  custom_field_1               varchar(255),
  custom_field_2               varchar(255),
  custom_field_3               varchar(255),
  custom_field_4               varchar(255),
  custom_field_5               varchar(255),
  custom_field_6               varchar(255),
  custom_field_7               varchar(255),
  custom_field_8               varchar(255),
  custom_field_9               varchar(255),
  custom_field_10              varchar(255)
);
COMMENT ON COLUMN transfer_details.line_id IS 'Line ID';
COMMENT ON COLUMN transfer_details.doc_id IS 'Document ID';
COMMENT ON COLUMN transfer_details.line_number IS 'Line Number';
COMMENT ON COLUMN transfer_details.sku_id IS 'SKU ID';
COMMENT ON COLUMN transfer_details.qty IS 'Quantity';
COMMENT ON COLUMN transfer_details.qty_wms IS 'Quantity (WMS)';
COMMENT ON COLUMN transfer_details.batch_number IS 'Batch Number';
COMMENT ON COLUMN transfer_details.series_number IS 'Series Number';
COMMENT ON COLUMN transfer_details.expiry_date IS 'Expiry Date';
COMMENT ON COLUMN transfer_details.quality IS 'Quality';
COMMENT ON COLUMN transfer_details.line_comment IS 'Line Comment';
COMMENT ON COLUMN transfer_details.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN transfer_details.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN transfer_details.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN transfer_details.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN transfer_details.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN transfer_details.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN transfer_details.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN transfer_details.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN transfer_details.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN transfer_details.custom_field_10 IS 'Custom Field 10';

-- Р1: одна строка документа может относиться к нескольким партиям.
-- line_number больше не уникален в рамках документа; уникальность — по партии.
CREATE UNIQUE INDEX uq_receipt_details_line ON receipt_details
  (doc_id, line_number, COALESCE(batch_number,''), COALESCE(series_number,''),
   COALESCE(expiry_date,'1900-01-01'), COALESCE(quality,''));
CREATE UNIQUE INDEX uq_order_details_line ON order_details
  (doc_id, line_number, COALESCE(batch_number,''), COALESCE(series_number,''),
   COALESCE(expiry_date,'1900-01-01'), COALESCE(quality,''));
CREATE UNIQUE INDEX uq_transfer_details_line ON transfer_details
  (doc_id, line_number, COALESCE(batch_number,''), COALESCE(series_number,''),
   COALESCE(expiry_date,'1900-01-01'), COALESCE(quality,''));

-- Р1: плановое количество хранится один раз на строку (запись без партии),
-- фактическое — на записях с партией. Иначе SUM по документу удваивается.
ALTER TABLE receipt_details ADD CONSTRAINT ck_receipt_details_plan_fact CHECK (
  (batch_number IS NULL     AND expected_qty IS NOT NULL AND accepted_qty IS NULL) OR
  (batch_number IS NOT NULL AND expected_qty IS NULL)
);
ALTER TABLE order_details ADD CONSTRAINT ck_order_details_plan_fact CHECK (
  (batch_number IS NULL     AND ordered_qty IS NOT NULL AND picked_qty IS NULL) OR
  (batch_number IS NOT NULL AND ordered_qty IS NULL)
);
-- transfer_details: qty используется и как плановое, и как фактическое —
-- уточнить у заказчика, нужен ли раздельный учёт, до включения CHECK.
