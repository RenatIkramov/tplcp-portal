-- V2__core.sql
-- Сгенерировано из словаря полей демо (Developer Settings).
-- Проверить вручную: NOT NULL, значения по умолчанию, размеры типов.

-- Решение Р2: два юрлица 3PL на одном экземпляре портала.
-- Таблицы operator нет в демо — добавлена вручную.
CREATE TABLE operator (
  operator_id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code                         varchar(30) NOT NULL UNIQUE,
  name                         varchar(255) NOT NULL,
  bin                          varchar(12),
  legal_address                text,
  bank_name                    varchar(255),
  bank_account                 varchar(34),
  phone                        varchar(50),
  email                        varchar(255),
  logo_object_key              varchar(500),
  about_content                text,
  contacts_content             text,
  is_active                    boolean NOT NULL DEFAULT true
);
COMMENT ON TABLE operator IS '3PL-оператор (юрлицо). Определяет реквизиты и контент страниц для клиента.';

-- ВНИМАНИЕ: таблица operator не найдена в словаре демо

-- Поклажедатели
CREATE TABLE depositor (
  depositor_id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code                         varchar(20) NOT NULL,
  org_name                     varchar(255),
  bin                          varchar(12),
  legal_address                varchar(255),
  phone                        varchar(30),
  email                        varchar(150),
  contract_number              varchar(50),
  contract_valid_until         date
);
COMMENT ON COLUMN depositor.depositor_id IS 'Depositor ID';
COMMENT ON COLUMN depositor.code IS 'Code';
COMMENT ON COLUMN depositor.org_name IS 'Organization Name';
COMMENT ON COLUMN depositor.bin IS 'BIN';
COMMENT ON COLUMN depositor.legal_address IS 'Legal Address';
COMMENT ON COLUMN depositor.phone IS 'Phone';
COMMENT ON COLUMN depositor.email IS 'Email';
COMMENT ON COLUMN depositor.contract_number IS 'Contract Number';
COMMENT ON COLUMN depositor.contract_valid_until IS 'Contract Valid Until';

-- Склады
CREATE TABLE warehouse (
  warehouse_id                 varchar(20) PRIMARY KEY,
  code                         varchar(20) NOT NULL,
  name                         varchar(255) NOT NULL,
  address                      varchar(255),
  responsible                  varchar(150),
  phone                        varchar(30),
  email                        varchar(150)
);
COMMENT ON COLUMN warehouse.warehouse_id IS 'Warehouse ID';
COMMENT ON COLUMN warehouse.code IS 'Code';
COMMENT ON COLUMN warehouse.name IS 'Name';
COMMENT ON COLUMN warehouse.address IS 'Address';
COMMENT ON COLUMN warehouse.responsible IS 'Responsible Person';
COMMENT ON COLUMN warehouse.phone IS 'Phone';
COMMENT ON COLUMN warehouse.email IS 'Email';

-- Пользователи
CREATE TABLE app_user (
  user_id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  login                        varchar(150),
  password_hash                varchar(255),
  full_name                    varchar(150),
  role_id                      smallint,
  position                     varchar(150),
  organization                 varchar(255),
  phone                        varchar(30),
  is_active                    boolean,
  last_login_at                timestamp
);
COMMENT ON COLUMN app_user.user_id IS 'User ID';
COMMENT ON COLUMN app_user.login IS 'Login (Email)';
COMMENT ON COLUMN app_user.password_hash IS 'Password Hash';
COMMENT ON COLUMN app_user.full_name IS 'Full Name';
COMMENT ON COLUMN app_user.role_id IS 'Role ID';
COMMENT ON COLUMN app_user.position IS 'Position';
COMMENT ON COLUMN app_user.organization IS 'Organization';
COMMENT ON COLUMN app_user.phone IS 'Phone';
COMMENT ON COLUMN app_user.is_active IS 'Is Active';
COMMENT ON COLUMN app_user.last_login_at IS 'Last Login At';

-- Настройки пользователей
CREATE TABLE user_settings (
  user_id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  hide_transfers_tab           boolean,
  hide_billing_tab             boolean,
  hide_services_tab            boolean,
  can_create_receipts          boolean,
  can_create_orders            boolean,
  can_create_transfers         boolean,
  can_upload_sku_excel         boolean,
  can_upload_packaging_excel   boolean
);
COMMENT ON COLUMN user_settings.user_id IS 'User ID';
COMMENT ON COLUMN user_settings.hide_transfers_tab IS 'Hide Transfers Tab';
COMMENT ON COLUMN user_settings.hide_billing_tab IS 'Hide Billing Tab';
COMMENT ON COLUMN user_settings.hide_services_tab IS 'Hide Services Tab';
COMMENT ON COLUMN user_settings.can_create_receipts IS 'Can Create Receipts';
COMMENT ON COLUMN user_settings.can_create_orders IS 'Can Create Orders';
COMMENT ON COLUMN user_settings.can_create_transfers IS 'Can Create Transfers';
COMMENT ON COLUMN user_settings.can_upload_sku_excel IS 'Can Upload SKU From Excel';
COMMENT ON COLUMN user_settings.can_upload_packaging_excel IS 'Can Upload Packaging From Excel';

-- Доступность поклажедателей для пользователя
CREATE TABLE user_depositor_access (
  id                           bigint PRIMARY KEY,
  user_id                      uuid,
  depositor_id                 uuid NOT NULL
);
COMMENT ON COLUMN user_depositor_access.id IS 'Record ID';
COMMENT ON COLUMN user_depositor_access.user_id IS 'User ID';
COMMENT ON COLUMN user_depositor_access.depositor_id IS 'Depositor ID';

-- Доступность складов для пользователя
CREATE TABLE user_warehouse_access (
  id                           bigint PRIMARY KEY,
  user_id                      uuid,
  warehouse_id                 varchar(20)
);
COMMENT ON COLUMN user_warehouse_access.id IS 'Record ID';
COMMENT ON COLUMN user_warehouse_access.user_id IS 'User ID';
COMMENT ON COLUMN user_warehouse_access.warehouse_id IS 'Warehouse ID';

-- Р2: привязка поклажедателя к юрлицу оператора
ALTER TABLE depositor ADD COLUMN operator_id uuid;
ALTER TABLE depositor ADD CONSTRAINT fk_depositor_operator
  FOREIGN KEY (operator_id) REFERENCES operator(operator_id);
