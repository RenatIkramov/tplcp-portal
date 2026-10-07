-- V1__enums.sql
-- Сгенерировано из словаря полей демо (Developer Settings).
-- Проверить вручную: NOT NULL, значения по умолчанию, размеры типов.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Статусы
CREATE TABLE enum_status (
  id                           int PRIMARY KEY,
  object_type_id               smallint,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_status.id IS 'ID';
COMMENT ON COLUMN enum_status.object_type_id IS 'Object Type ID';
COMMENT ON COLUMN enum_status.code IS 'Code';
COMMENT ON COLUMN enum_status.name IS 'Name';
COMMENT ON COLUMN enum_status.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_status.is_active IS 'Is Active';

-- Роли пользователей
CREATE TABLE enum_user_role (
  id                           smallint PRIMARY KEY,
  code                         varchar(20) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_user_role.id IS 'ID';
COMMENT ON COLUMN enum_user_role.code IS 'Code';
COMMENT ON COLUMN enum_user_role.name IS 'Name';
COMMENT ON COLUMN enum_user_role.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_user_role.is_active IS 'Is Active';

-- Типы заказов
CREATE TABLE enum_order_type (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_order_type.id IS 'ID';
COMMENT ON COLUMN enum_order_type.code IS 'Code';
COMMENT ON COLUMN enum_order_type.name IS 'Name';
COMMENT ON COLUMN enum_order_type.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_order_type.is_active IS 'Is Active';

-- Типы приходных документов
CREATE TABLE enum_receipt_doc_type (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_receipt_doc_type.id IS 'ID';
COMMENT ON COLUMN enum_receipt_doc_type.code IS 'Code';
COMMENT ON COLUMN enum_receipt_doc_type.name IS 'Name';
COMMENT ON COLUMN enum_receipt_doc_type.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_receipt_doc_type.is_active IS 'Is Active';

-- Причины блокировки
CREATE TABLE enum_block_reason (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_block_reason.id IS 'ID';
COMMENT ON COLUMN enum_block_reason.code IS 'Code';
COMMENT ON COLUMN enum_block_reason.name IS 'Name';
COMMENT ON COLUMN enum_block_reason.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_block_reason.is_active IS 'Is Active';

-- Типы упаковки
CREATE TABLE enum_packaging_type (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_packaging_type.id IS 'ID';
COMMENT ON COLUMN enum_packaging_type.code IS 'Code';
COMMENT ON COLUMN enum_packaging_type.name IS 'Name';
COMMENT ON COLUMN enum_packaging_type.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_packaging_type.is_active IS 'Is Active';

-- Валюты
CREATE TABLE enum_currency (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_currency.id IS 'ID';
COMMENT ON COLUMN enum_currency.code IS 'Code';
COMMENT ON COLUMN enum_currency.name IS 'Name';
COMMENT ON COLUMN enum_currency.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_currency.is_active IS 'Is Active';

-- Языки
CREATE TABLE dictionary_locale (
  locale_id                    smallint PRIMARY KEY,
  code                         varchar(10) NOT NULL,
  name                         varchar(50) NOT NULL,
  is_default                   boolean,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN dictionary_locale.locale_id IS 'Locale ID';
COMMENT ON COLUMN dictionary_locale.code IS 'Locale Code';
COMMENT ON COLUMN dictionary_locale.name IS 'Language Name';
COMMENT ON COLUMN dictionary_locale.is_default IS 'Is Default';
COMMENT ON COLUMN dictionary_locale.sort_order IS 'Sort Order';
COMMENT ON COLUMN dictionary_locale.is_active IS 'Is Active';

-- Единицы измерения
CREATE TABLE enum_uom (
  id                           smallint PRIMARY KEY,
  mkei_code                    varchar(3),
  symbol                       varchar(10),
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_uom.id IS 'ID';
COMMENT ON COLUMN enum_uom.mkei_code IS 'MKEI Code';
COMMENT ON COLUMN enum_uom.symbol IS 'Symbol';
COMMENT ON COLUMN enum_uom.name IS 'Full Name';
COMMENT ON COLUMN enum_uom.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_uom.is_active IS 'Is Active';

-- Классы опасности
CREATE TABLE enum_hazard_class (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_hazard_class.id IS 'ID';
COMMENT ON COLUMN enum_hazard_class.code IS 'Code';
COMMENT ON COLUMN enum_hazard_class.name IS 'Name';
COMMENT ON COLUMN enum_hazard_class.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_hazard_class.is_active IS 'Is Active';

-- Группы опасности
CREATE TABLE enum_hazard_group (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_hazard_group.id IS 'ID';
COMMENT ON COLUMN enum_hazard_group.code IS 'Code';
COMMENT ON COLUMN enum_hazard_group.name IS 'Name';
COMMENT ON COLUMN enum_hazard_group.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_hazard_group.is_active IS 'Is Active';

-- Типы объектов
CREATE TABLE enum_object_type (
  id                           smallint PRIMARY KEY,
  code                         varchar(30) NOT NULL,
  name                         varchar(255) NOT NULL,
  sort_order                   smallint,
  is_active                    boolean
);
COMMENT ON COLUMN enum_object_type.id IS 'ID';
COMMENT ON COLUMN enum_object_type.code IS 'Code';
COMMENT ON COLUMN enum_object_type.name IS 'Name';
COMMENT ON COLUMN enum_object_type.sort_order IS 'Sort Order';
COMMENT ON COLUMN enum_object_type.is_active IS 'Is Active';
