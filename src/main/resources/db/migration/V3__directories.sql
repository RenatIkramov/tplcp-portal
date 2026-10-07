-- V3__directories.sql
-- Сгенерировано из словаря полей демо (Developer Settings).
-- Проверить вручную: NOT NULL, значения по умолчанию, размеры типов.

-- Группы товара
CREATE TABLE product_group (
  id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  description                  text,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN product_group.id IS 'ID';
COMMENT ON COLUMN product_group.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN product_group.code IS 'Code';
COMMENT ON COLUMN product_group.name IS 'Name';
COMMENT ON COLUMN product_group.description IS 'Description';
COMMENT ON COLUMN product_group.sort_order IS 'Sort Order';
COMMENT ON COLUMN product_group.is_active IS 'Is Active';

-- Типы товара
CREATE TABLE product_type (
  id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  description                  text,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN product_type.id IS 'ID';
COMMENT ON COLUMN product_type.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN product_type.code IS 'Code';
COMMENT ON COLUMN product_type.name IS 'Name';
COMMENT ON COLUMN product_type.description IS 'Description';
COMMENT ON COLUMN product_type.sort_order IS 'Sort Order';
COMMENT ON COLUMN product_type.is_active IS 'Is Active';

-- Товары
CREATE TABLE sku (
  sku_id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  item_code                    varchar(100),
  code                         varchar(100) NOT NULL,
  name                         varchar(255) NOT NULL,
  has_packaging                boolean,
  barcode                      varchar(100),
  uom_id                       smallint,
  order_unit_id                uuid,
  length                       numeric(12,3),
  width                        numeric(12,3),
  height                       numeric(12,3),
  volume                       numeric(12,3),
  weight_gross                 numeric(12,3),
  manufacturer                 varchar(100),
  country_origin               varchar(100),
  brand                        varchar(100),
  is_fragile                   boolean,
  is_serial_tracked            boolean,
  batch_tracking               varchar(100),
  expiry_tracking              varchar(100),
  item_full_name               text,
  weight_net                   numeric(12,3),
  max_storage_temp             numeric(12,3),
  min_storage_temp             numeric(12,3),
  max_humidity                 numeric(12,3),
  min_humidity                 numeric(12,3),
  min_days_for_receipt         numeric(12,3),
  min_days_for_reservation     numeric(12,3),
  shelf_life_days              numeric(12,3),
  color                        varchar(100),
  size                         varchar(100),
  abc_class                    varchar(100),
  xyz_class                    varchar(100),
  guid                         uuid,
  hs_code                      varchar(100),
  enstru_code                  varchar(100),
  cost                         numeric(14,2),
  comment                      text,
  is_kit                       boolean,
  is_marking_required          boolean,
  is_excise                    boolean,
  is_controlled                boolean,
  is_wms_active                boolean,
  created_by                   varchar(100),
  created_at                   timestamp,
  updated_by                   varchar(100),
  updated_at                   timestamp,
  custom_attr1                 varchar(100),
  custom_attr2                 varchar(100),
  custom_attr3                 varchar(100),
  custom_attr4                 varchar(100),
  custom_attr5                 varchar(100),
  custom_attr6                 varchar(100),
  custom_attr7                 varchar(100),
  custom_attr8                 varchar(100),
  custom_attr9                 varchar(100),
  custom_attr10                varchar(100),
  item_group_id                uuid,
  item_type_id                 uuid,
  hazard_class_id              smallint,
  hazard_group_id              smallint,
  currency_id                  smallint,
  status_id                    int
);
COMMENT ON COLUMN sku.sku_id IS 'SKU ID';
COMMENT ON COLUMN sku.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN sku.item_code IS 'Item Code';
COMMENT ON COLUMN sku.code IS 'Commercial Code';
COMMENT ON COLUMN sku.name IS 'Item Name';
COMMENT ON COLUMN sku.has_packaging IS 'Uses Packaging';
COMMENT ON COLUMN sku.barcode IS 'Barcode';
COMMENT ON COLUMN sku.uom_id IS 'Unit of Measure ID';
COMMENT ON COLUMN sku.order_unit_id IS 'Order Unit ID';
COMMENT ON COLUMN sku.length IS 'Length';
COMMENT ON COLUMN sku.width IS 'Width';
COMMENT ON COLUMN sku.height IS 'Height';
COMMENT ON COLUMN sku.volume IS 'Volume';
COMMENT ON COLUMN sku.weight_gross IS 'Gross Weight';
COMMENT ON COLUMN sku.manufacturer IS 'Manufacturer';
COMMENT ON COLUMN sku.country_origin IS 'Country of Origin';
COMMENT ON COLUMN sku.brand IS 'Brand';
COMMENT ON COLUMN sku.is_fragile IS 'Is Fragile';
COMMENT ON COLUMN sku.is_serial_tracked IS 'Is Serial Tracked';
COMMENT ON COLUMN sku.batch_tracking IS 'Is Batch Tracked';
COMMENT ON COLUMN sku.expiry_tracking IS 'Is Expiry Tracked';
COMMENT ON COLUMN sku.item_full_name IS 'Full Item Name';
COMMENT ON COLUMN sku.weight_net IS 'Net Weight';
COMMENT ON COLUMN sku.max_storage_temp IS 'Max Storage Temperature';
COMMENT ON COLUMN sku.min_storage_temp IS 'Min Storage Temperature';
COMMENT ON COLUMN sku.max_humidity IS 'Max Humidity';
COMMENT ON COLUMN sku.min_humidity IS 'Min Humidity';
COMMENT ON COLUMN sku.min_days_for_receipt IS 'Min Days For Receipt';
COMMENT ON COLUMN sku.min_days_for_reservation IS 'Min Days For Reservation';
COMMENT ON COLUMN sku.shelf_life_days IS 'Shelf Life (Days)';
COMMENT ON COLUMN sku.color IS 'Color';
COMMENT ON COLUMN sku.size IS 'Size';
COMMENT ON COLUMN sku.abc_class IS 'ABC Class';
COMMENT ON COLUMN sku.xyz_class IS 'XYZ Class';
COMMENT ON COLUMN sku.guid IS 'GUID';
COMMENT ON COLUMN sku.hs_code IS 'HS Code';
COMMENT ON COLUMN sku.enstru_code IS 'ENSTRU Code';
COMMENT ON COLUMN sku.cost IS 'Cost';
COMMENT ON COLUMN sku.comment IS 'Comment';
COMMENT ON COLUMN sku.is_kit IS 'Is Kit';
COMMENT ON COLUMN sku.is_marking_required IS 'Is Marking Required';
COMMENT ON COLUMN sku.is_excise IS 'Is Excise';
COMMENT ON COLUMN sku.is_controlled IS 'Is Controlled';
COMMENT ON COLUMN sku.is_wms_active IS 'Is WMS Active';
COMMENT ON COLUMN sku.created_by IS 'Created By';
COMMENT ON COLUMN sku.created_at IS 'Created At';
COMMENT ON COLUMN sku.updated_by IS 'Updated By';
COMMENT ON COLUMN sku.updated_at IS 'Updated At';
COMMENT ON COLUMN sku.custom_attr1 IS 'Custom Attribute 1';
COMMENT ON COLUMN sku.custom_attr2 IS 'Custom Attribute 2';
COMMENT ON COLUMN sku.custom_attr3 IS 'Custom Attribute 3';
COMMENT ON COLUMN sku.custom_attr4 IS 'Custom Attribute 4';
COMMENT ON COLUMN sku.custom_attr5 IS 'Custom Attribute 5';
COMMENT ON COLUMN sku.custom_attr6 IS 'Custom Attribute 6';
COMMENT ON COLUMN sku.custom_attr7 IS 'Custom Attribute 7';
COMMENT ON COLUMN sku.custom_attr8 IS 'Custom Attribute 8';
COMMENT ON COLUMN sku.custom_attr9 IS 'Custom Attribute 9';
COMMENT ON COLUMN sku.custom_attr10 IS 'Custom Attribute 10';
COMMENT ON COLUMN sku.item_group_id IS 'Item Group ID';
COMMENT ON COLUMN sku.item_type_id IS 'Item Type ID';
COMMENT ON COLUMN sku.hazard_class_id IS 'Hazard Class ID';
COMMENT ON COLUMN sku.hazard_group_id IS 'Hazard Group ID';
COMMENT ON COLUMN sku.currency_id IS 'Currency ID';
COMMENT ON COLUMN sku.status_id IS 'Status ID';

-- Упаковка
CREATE TABLE product_packaging (
  packaging_id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  sku_id                       uuid,
  packaging_code               varchar(20),
  packaging_type_id            smallint,
  barcode                      varchar(20),
  coefficient                  numeric(12,3),
  length_cm                    numeric(10,2),
  width_cm                     numeric(10,2),
  height_cm                    numeric(10,2),
  volume_m3                    numeric(12,5),
  gross_weight_kg              numeric(12,3)
);
COMMENT ON COLUMN product_packaging.packaging_id IS 'Packaging Record ID';
COMMENT ON COLUMN product_packaging.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN product_packaging.sku_id IS 'SKU ID';
COMMENT ON COLUMN product_packaging.packaging_code IS 'Packaging Code';
COMMENT ON COLUMN product_packaging.packaging_type_id IS 'Packaging Type ID';
COMMENT ON COLUMN product_packaging.barcode IS 'Barcode';
COMMENT ON COLUMN product_packaging.coefficient IS 'Coefficient';
COMMENT ON COLUMN product_packaging.length_cm IS 'Length';
COMMENT ON COLUMN product_packaging.width_cm IS 'Width';
COMMENT ON COLUMN product_packaging.height_cm IS 'Height';
COMMENT ON COLUMN product_packaging.volume_m3 IS 'Volume';
COMMENT ON COLUMN product_packaging.gross_weight_kg IS 'Gross Weight';

-- Поставщики
CREATE TABLE supplier (
  supplier_id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  code                         varchar(20) NOT NULL,
  name                         varchar(255) NOT NULL,
  bin                          varchar(12),
  legal_address                varchar(255),
  contact_name                 varchar(150),
  contact_phone                varchar(30),
  contact_email                varchar(150)
);
COMMENT ON COLUMN supplier.supplier_id IS 'Supplier ID';
COMMENT ON COLUMN supplier.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN supplier.code IS 'Code';
COMMENT ON COLUMN supplier.name IS 'Name';
COMMENT ON COLUMN supplier.bin IS 'BIN';
COMMENT ON COLUMN supplier.legal_address IS 'Legal Address';
COMMENT ON COLUMN supplier.contact_name IS 'Contact Person';
COMMENT ON COLUMN supplier.contact_phone IS 'Contact Phone';
COMMENT ON COLUMN supplier.contact_email IS 'Contact Email';

-- Грузополучатели
CREATE TABLE consignee (
  consignee_id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  depositor_id                 uuid NOT NULL,
  code                         varchar(20) NOT NULL,
  name                         varchar(255) NOT NULL,
  bin                          varchar(12),
  legal_address                varchar(255),
  contact_name                 varchar(150),
  contact_phone                varchar(30),
  contact_email                varchar(150)
);
COMMENT ON COLUMN consignee.consignee_id IS 'Consignee ID';
COMMENT ON COLUMN consignee.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN consignee.code IS 'Code';
COMMENT ON COLUMN consignee.name IS 'Name';
COMMENT ON COLUMN consignee.bin IS 'BIN';
COMMENT ON COLUMN consignee.legal_address IS 'Legal Address';
COMMENT ON COLUMN consignee.contact_name IS 'Contact Person';
COMMENT ON COLUMN consignee.contact_phone IS 'Contact Phone';
COMMENT ON COLUMN consignee.contact_email IS 'Contact Email';

-- Адреса доставки грузополучателей
CREATE TABLE delivery_point (
  address_id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  consignee_id                 uuid,
  address                      varchar(255),
  is_default                   boolean,
  sort_order                   smallint
);
COMMENT ON COLUMN delivery_point.address_id IS 'Address ID';
COMMENT ON COLUMN delivery_point.consignee_id IS 'Consignee ID';
COMMENT ON COLUMN delivery_point.address IS 'Delivery Address';
COMMENT ON COLUMN delivery_point.is_default IS 'Is Default';
COMMENT ON COLUMN delivery_point.sort_order IS 'Sort Order';

-- Переводы
CREATE TABLE translation (
  translation_id               bigint PRIMARY KEY,
  entity_type_id               smallint,
  entity_id                    varchar(64),
  field_name                   varchar(50),
  locale_id                    smallint,
  value                        text
);
COMMENT ON COLUMN translation.translation_id IS 'Translation ID';
COMMENT ON COLUMN translation.entity_type_id IS 'Entity Type ID';
COMMENT ON COLUMN translation.entity_id IS 'Entity ID';
COMMENT ON COLUMN translation.field_name IS 'Field Name';
COMMENT ON COLUMN translation.locale_id IS 'Locale ID';
COMMENT ON COLUMN translation.value IS 'Translated Value';

-- Настройка полей поклажедателя
CREATE TABLE depositor_field_label (
  id                           bigint PRIMARY KEY,
  depositor_id                 uuid NOT NULL,
  object_type_id               smallint,
  field_id                     varchar(100),
  custom_label                 varchar(255)
);
COMMENT ON COLUMN depositor_field_label.id IS 'Record ID';
COMMENT ON COLUMN depositor_field_label.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN depositor_field_label.object_type_id IS 'Object Type ID';
COMMENT ON COLUMN depositor_field_label.field_id IS 'Field ID';
COMMENT ON COLUMN depositor_field_label.custom_label IS 'Custom Field Label';
