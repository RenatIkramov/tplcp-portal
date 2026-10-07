-- V5__registers.sql
-- Сгенерировано из словаря полей демо (Developer Settings).
-- Проверить вручную: NOT NULL, значения по умолчанию, размеры типов.

-- Запасы
CREATE TABLE stock (
  register_id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  sku_id                       uuid,
  warehouse_id                 varchar(20),
  unit_id                      smallint,
  batch_number                 varchar(50),
  series_number                varchar(50),
  expiry_date                  date,
  quality                      varchar(20),
  block_reason_id              smallint,
  quantity                     numeric(14,3),
  reserved_qty                 numeric(14,3),
  blocked_qty                  numeric(14,3),
  available_qty                numeric(14,3),
  updated_at                   timestamp,
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
COMMENT ON COLUMN stock.register_id IS 'Register Record ID';
COMMENT ON COLUMN stock.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN stock.sku_id IS 'SKU ID';
COMMENT ON COLUMN stock.warehouse_id IS 'Warehouse ID';
COMMENT ON COLUMN stock.unit_id IS 'Unit ID';
COMMENT ON COLUMN stock.batch_number IS 'Batch Number';
COMMENT ON COLUMN stock.series_number IS 'Series Number';
COMMENT ON COLUMN stock.expiry_date IS 'Expiry Date';
COMMENT ON COLUMN stock.quality IS 'Quality';
COMMENT ON COLUMN stock.block_reason_id IS 'Block Reason ID';
COMMENT ON COLUMN stock.quantity IS 'Quantity On Hand';
COMMENT ON COLUMN stock.reserved_qty IS 'Reserved Quantity';
COMMENT ON COLUMN stock.blocked_qty IS 'Blocked Quantity';
COMMENT ON COLUMN stock.available_qty IS 'Available Quantity';
COMMENT ON COLUMN stock.updated_at IS 'Updated At';
COMMENT ON COLUMN stock.custom_field_1 IS 'Custom Field 1';
COMMENT ON COLUMN stock.custom_field_2 IS 'Custom Field 2';
COMMENT ON COLUMN stock.custom_field_3 IS 'Custom Field 3';
COMMENT ON COLUMN stock.custom_field_4 IS 'Custom Field 4';
COMMENT ON COLUMN stock.custom_field_5 IS 'Custom Field 5';
COMMENT ON COLUMN stock.custom_field_6 IS 'Custom Field 6';
COMMENT ON COLUMN stock.custom_field_7 IS 'Custom Field 7';
COMMENT ON COLUMN stock.custom_field_8 IS 'Custom Field 8';
COMMENT ON COLUMN stock.custom_field_9 IS 'Custom Field 9';
COMMENT ON COLUMN stock.custom_field_10 IS 'Custom Field 10';

-- Зарегистрированные услуги
CREATE TABLE registered_service (
  id                           bigint PRIMARY KEY,
  depositor_id                 uuid NOT NULL,
  doc_id                       uuid,
  service                      varchar(255),
  qty                          numeric(14,3),
  cost                         numeric(14,2),
  sum                          numeric(14,2),
  markup_percent               numeric(6,2),
  sum_with_markup              numeric(14,2),
  vat_amount                   numeric(14,2),
  sum_with_vat                 numeric(14,2)
);
COMMENT ON COLUMN registered_service.id IS 'Record ID';
COMMENT ON COLUMN registered_service.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN registered_service.doc_id IS 'Document ID';
COMMENT ON COLUMN registered_service.service IS 'Service';
COMMENT ON COLUMN registered_service.qty IS 'Quantity';
COMMENT ON COLUMN registered_service.cost IS 'Cost';
COMMENT ON COLUMN registered_service.sum IS 'Sum';
COMMENT ON COLUMN registered_service.markup_percent IS 'Markup %';
COMMENT ON COLUMN registered_service.sum_with_markup IS 'Sum With Markup';
COMMENT ON COLUMN registered_service.vat_amount IS 'VAT Amount';
COMMENT ON COLUMN registered_service.sum_with_vat IS 'Sum With VAT';

-- Тарифы услуг поклажедателя
CREATE TABLE service_tariff (
  id                           bigint PRIMARY KEY,
  depositor_id                 uuid NOT NULL,
  service                      varchar(255),
  rate                         numeric(14,2),
  unit                         varchar(100),
  frequency                    varchar(50)
);
COMMENT ON COLUMN service_tariff.id IS 'Record ID';
COMMENT ON COLUMN service_tariff.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN service_tariff.service IS 'Service';
COMMENT ON COLUMN service_tariff.rate IS 'Rate';
COMMENT ON COLUMN service_tariff.unit IS 'Unit';
COMMENT ON COLUMN service_tariff.frequency IS 'Frequency';

-- Счета
CREATE TABLE invoice (
  id                           bigint PRIMARY KEY,
  depositor_id                 uuid NOT NULL,
  invoice_number               varchar(30),
  period_from                  date,
  period_to                    date,
  issue_date                   date,
  amount_no_vat                numeric(14,2),
  amount_vat                   numeric(14,2),
  status_id                    int,
  pdf_file                     varchar(255)
);
COMMENT ON COLUMN invoice.id IS 'Record ID';
COMMENT ON COLUMN invoice.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN invoice.invoice_number IS 'Invoice Number';
COMMENT ON COLUMN invoice.period_from IS 'Period From';
COMMENT ON COLUMN invoice.period_to IS 'Period To';
COMMENT ON COLUMN invoice.issue_date IS 'Issue Date';
COMMENT ON COLUMN invoice.amount_no_vat IS 'Amount, No VAT';
COMMENT ON COLUMN invoice.amount_vat IS 'Amount, VAT Included';
COMMENT ON COLUMN invoice.status_id IS 'Status ID';
COMMENT ON COLUMN invoice.pdf_file IS 'PDF File Path';

-- Фотографии документов
CREATE TABLE document_photo (
  id                           bigint PRIMARY KEY,
  depositor_id                 uuid NOT NULL,
  doc_id                       uuid,
  object_type_id               smallint,
  object_key                   varchar(500),
  file_name                    varchar(255),
  caption                      varchar(255),
  mime_type                    varchar(100),
  file_size                    bigint,
  source                       varchar(20),
  uploaded_by                  uuid,
  uploaded_at                  timestamp,
  sort_order                   smallint
);
COMMENT ON COLUMN document_photo.id IS 'Record ID';
COMMENT ON COLUMN document_photo.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN document_photo.doc_id IS 'Document ID';
COMMENT ON COLUMN document_photo.object_type_id IS 'Document Kind';
COMMENT ON COLUMN document_photo.object_key IS 'Storage Object Key';
COMMENT ON COLUMN document_photo.file_name IS 'Original File Name';
COMMENT ON COLUMN document_photo.caption IS 'Caption';
COMMENT ON COLUMN document_photo.mime_type IS 'MIME Type';
COMMENT ON COLUMN document_photo.file_size IS 'File Size, bytes';
COMMENT ON COLUMN document_photo.source IS 'Source (''wms'' / ''manual'')';
COMMENT ON COLUMN document_photo.uploaded_by IS 'Uploaded By';
COMMENT ON COLUMN document_photo.uploaded_at IS 'Uploaded At';
COMMENT ON COLUMN document_photo.sort_order IS 'Sort Order';

-- §11: индексы под расчёт остатков
CREATE INDEX ix_stock_depositor_sku_wh ON stock (depositor_id, sku_id, warehouse_id);
CREATE INDEX ix_receipt_details_sku ON receipt_details (sku_id);
CREATE INDEX ix_order_details_sku ON order_details (sku_id);
