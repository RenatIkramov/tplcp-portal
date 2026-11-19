SET search_path = dbo;

-- V116. Журнал действий (§11 ТЗ, 11.11.2026), часть 1: запись и история
-- документа.
--
-- КАК УСТРОЕНО.
-- * audit_log — одна таблица на всё, только дописывается: строка —
--   одно изменение одной записи (создана / изменена / удалена) со списком
--   «поле: было → стало» в changes (jsonb: {"поле": {"o": было, "n": стало}}).
-- * Пишут ТРИГГЕРЫ, а не Java: в портал пишут карточки, Excel, а потом
--   и обмен с WMS, и вызов «запиши в журнал» в каждом месте однажды
--   забыли бы — молча. Тот же довод, что у RLS (§5).
-- * Кто действует — app.actor: его ставит пул соединений портала
--   (LocaleDataSource) при каждой выдаче соединения, логин вошедшего.
--   Нет app.actor (миграции, psql, загрузка демо-данных, тесты напрямую
--   в базу) — в журнал не пишем: это не действие человека в портале.
--   Обмен с WMS (этап 8) выставит app.actor = 'wms' сам.
-- * Значения хранятся как есть (идентификаторы, коды), а словами их
--   делает чтение — на языке читающего (как сообщения, Р14):
--   audit_value().
-- * Что и как писать по колонкам — audit_field: подпись (ключ текста),
--   вид значения (перечисление, ссылка, да/нет, секрет) или 'skip' —
--   служебное (отметки времени, кто изменил, пересчитываемые итоги,
--   идентификаторы). Проверка AuditTest требует, чтобы каждая колонка
--   журналируемой таблицы была в audit_field: новая колонка без строки
--   здесь — падение теста, а не тихий пробел в журнале.
-- * Строки документа пишутся в журнал документа с пометкой part =
--   'line:N'. Портал сохраняет состав целиком (удалить все строки —
--   вставить заново), поэтому чтение (audit_history) сводит события
--   одной транзакции по строке: «удалена + добавлена» с теми же
--   значениями — ничего, с другими — «изменена».
--
-- Журнал администратора, срок хранения и неудачные входы — часть 2.

-- ---------- Таблицы -----------------------------------------------------------

CREATE TABLE audit_log (
  audit_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  at            timestamptz  NOT NULL DEFAULT now(),
  tx            bigint       NOT NULL DEFAULT txid_current(),
  actor         varchar(100) NOT NULL,
  depositor_id  uuid,
  object_type   varchar(30)  NOT NULL,
  object_id     text         NOT NULL,
  object_label  text,
  source_table  varchar(63)  NOT NULL,
  action        varchar(10)  NOT NULL CHECK (action IN ('create', 'update', 'delete', 'status')),
  part          text,
  changes       jsonb        NOT NULL DEFAULT '{}'
);
CREATE INDEX audit_log_object_ix    ON audit_log (object_type, object_id, audit_id);
CREATE INDEX audit_log_at_ix        ON audit_log (at);
CREATE INDEX audit_log_depositor_ix ON audit_log (depositor_id, at);
CREATE INDEX audit_log_actor_ix     ON audit_log (actor, at);

COMMENT ON TABLE audit_log IS
  'журнал действий (V116): одно изменение одной записи; пишут триггеры audit_row()';

CREATE TABLE audit_field (
  source_table  varchar(63)  NOT NULL,
  column_name   varchar(63)  NOT NULL,
  kind          varchar(40)  NOT NULL,
  text_key      varchar(80)  REFERENCES ui_text_key (text_key),
  sort_order    integer      NOT NULL DEFAULT 0,
  PRIMARY KEY (source_table, column_name)
);
COMMENT ON TABLE audit_field IS
  'колонки журналируемых таблиц: подпись и вид значения (skip, text, bool, secret, enum:<таблица>, '
  'enumcode:<таблица>, ref:<что>) (V116)';

-- Клиент видит журнал своих записей (история документа), остальное —
-- через функции администратора (часть 2). Без FORCE: триггер пишет
-- от имени владельца (SECURITY DEFINER) строки любого клиента.
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON audit_log
  USING (depositor_id = current_depositor_id());

-- Права по умолчанию (V12) дали бы приложению и правку: журнал
-- только читается, пишет его один триггер.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON audit_log FROM portal_app;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON audit_field FROM portal_app;
GRANT SELECT ON audit_log, audit_field TO portal_app;

-- Подписи полей журнала: audit.field.<колонка> (часть скопирована
-- с подписей колонок товара, остальное — новые тексты).
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('audit.field.abc_class', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.about_content', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.accepted_qty', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.accompanying_doc_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.address', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.all_depositors', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.all_warehouses', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.area_m2', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.arrival_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.bank_account', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.bank_name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.barcode', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.batch_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.batch_tracking', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.bin', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.brand', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.can_create_orders', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.can_create_receipts', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.can_create_transfers', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.can_edit_transport', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.can_upload_packaging_excel', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.can_upload_sku_excel', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.car_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.cargo_places', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.carrier', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.code', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.coefficient', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.color', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.comment', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.consignee_contact', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.consignee_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contact', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contact_email', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contact_name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contact_phone', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contacts_content', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.container_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contract_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.contract_valid_until', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.cost', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.country_origin', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.currency_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr1', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr10', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr2', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr3', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr4', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr5', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr6', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr7', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr8', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_attr9', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_1', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_10', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_2', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_3', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_4', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_5', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_6', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_7', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_8', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_field_9', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.custom_label', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.defect_qty', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.delivery_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.delivery_point_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.depositor_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.doc_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.doc_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.doc_type_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.driver_id_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.driver_inn', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.driver_name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.driver_phone', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.email', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.enstru_code', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.erp_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.eta_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.expected_qty', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.expected_qty_wms', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.expiry_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.expiry_tracking', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.from_warehouse_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.full_name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.gross_weight_kg', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.gtd', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.has_packaging', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.hazard_class_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.hazard_group_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.height', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.height_cm', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.hide_billing_tab', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.hide_services_tab', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.hide_transfers_tab', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.hs_code', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.invoice_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_active', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_available', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_controlled', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_default_address', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_default_column', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_default_locale', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_excise', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_fragile', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_kit', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_marking_required', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_serial_tracked', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.is_wms_active', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.item_code', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.item_full_name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.item_group', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.item_type', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.legal_address', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.length', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.length_cm', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.line_comment', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.locale_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.login', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.manufacturer', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.max_humidity', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.max_storage_temp', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.min_days_for_receipt', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.min_days_for_reservation', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.min_humidity', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.min_storage_temp', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.must_change_password', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.operator_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.order_type_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.order_unit_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.ordered_qty', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.ordered_qty_wms', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.org_name', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.organization', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.packaging_code', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.packaging_type_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.pallet_count', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.password_hash', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.phone', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.picked_qty', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.planned_ship_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.position', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.price', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.price_in_orders', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.price_in_receipts', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.qty', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.qty_wms', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.quality_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.received_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.receiving_end_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.responsible', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.role_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.seal_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.series_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.shelf_life_days', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.ship_date', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.size', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.sku_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.sku_status', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.status_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.supplier_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.symbol', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.tech_passport_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.text_value', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.to_warehouse_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.total_pieces', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.trailer_number', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.transport_brand', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.transport_state', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.transport_type', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.trip', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.uom_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.volume', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.volume_m3', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.warehouse_id', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.weight_gross', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.weight_kg', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.weight_net', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.width', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.width_cm', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.work_hours', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.field.xyz_class', 'audit', 'label', 'audit', 'журнал действий (V116)'),
  ('audit.tab.history', 'audit', 'tab', 'audit', 'журнал действий (V116)'),
  ('audit.text.no_history', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.action.doc_created', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.action.changed', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.action.status', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.action.line_added', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.action.line_changed', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.action.line_deleted', 'audit', 'text', 'audit', 'журнал действий (V116)'),
  ('audit.column.when', 'audit', 'column', 'audit', 'журнал действий (V116)'),
  ('audit.column.who', 'audit', 'column', 'audit', 'журнал действий (V116)'),
  ('audit.column.field', 'audit', 'column', 'audit', 'журнал действий (V116)'),
  ('audit.column.was', 'audit', 'column', 'audit', 'журнал действий (V116)'),
  ('audit.column.became', 'audit', 'column', 'audit', 'журнал действий (V116)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('audit.field.abc_class', 'ru', 'ABC Класс'),
    ('audit.field.abc_class', 'en', 'ABC class'),
    ('audit.field.about_content', 'ru', 'Текст страницы «О нас»'),
    ('audit.field.about_content', 'en', '“About us” page text'),
    ('audit.field.accepted_qty', 'ru', 'Принято'),
    ('audit.field.accepted_qty', 'en', 'Accepted'),
    ('audit.field.accompanying_doc_number', 'ru', 'Номер сопроводительного документа'),
    ('audit.field.accompanying_doc_number', 'en', 'Accompanying document number'),
    ('audit.field.address', 'ru', 'Адрес'),
    ('audit.field.address', 'en', 'Address'),
    ('audit.field.all_depositors', 'ru', 'Все поклажедатели'),
    ('audit.field.all_depositors', 'en', 'All depositors'),
    ('audit.field.all_warehouses', 'ru', 'Все склады'),
    ('audit.field.all_warehouses', 'en', 'All warehouses'),
    ('audit.field.area_m2', 'ru', 'Площадь, м²'),
    ('audit.field.area_m2', 'en', 'Area, m²'),
    ('audit.field.arrival_date', 'ru', 'Дата прибытия'),
    ('audit.field.arrival_date', 'en', 'Arrival date'),
    ('audit.field.bank_account', 'ru', 'Расчётный счёт (IBAN)'),
    ('audit.field.bank_account', 'en', 'Bank account (IBAN)'),
    ('audit.field.bank_name', 'ru', 'Банк'),
    ('audit.field.bank_name', 'en', 'Bank'),
    ('audit.field.barcode', 'ru', 'Штрих код'),
    ('audit.field.barcode', 'en', 'Barcode'),
    ('audit.field.batch_number', 'ru', 'Партия'),
    ('audit.field.batch_number', 'en', 'Batch'),
    ('audit.field.batch_tracking', 'ru', 'Учёт партий'),
    ('audit.field.batch_tracking', 'en', 'Batch tracking'),
    ('audit.field.bin', 'ru', 'БИН'),
    ('audit.field.bin', 'en', 'BIN'),
    ('audit.field.brand', 'ru', 'Торговая марка'),
    ('audit.field.brand', 'en', 'Brand'),
    ('audit.field.can_create_orders', 'ru', 'Может создавать заказы'),
    ('audit.field.can_create_orders', 'en', 'May create orders'),
    ('audit.field.can_create_receipts', 'ru', 'Может создавать поступления'),
    ('audit.field.can_create_receipts', 'en', 'May create receipts'),
    ('audit.field.can_create_transfers', 'ru', 'Может создавать перемещения'),
    ('audit.field.can_create_transfers', 'en', 'May create transfers'),
    ('audit.field.can_edit_transport', 'ru', 'Может править транспорт'),
    ('audit.field.can_edit_transport', 'en', 'May edit transport'),
    ('audit.field.can_upload_packaging_excel', 'ru', 'Загрузка упаковок из Excel'),
    ('audit.field.can_upload_packaging_excel', 'en', 'Packaging import from Excel'),
    ('audit.field.can_upload_sku_excel', 'ru', 'Загрузка товаров из Excel'),
    ('audit.field.can_upload_sku_excel', 'en', 'Item import from Excel'),
    ('audit.field.car_number', 'ru', 'Номер машины'),
    ('audit.field.car_number', 'en', 'Vehicle number'),
    ('audit.field.cargo_places', 'ru', 'Грузовых мест'),
    ('audit.field.cargo_places', 'en', 'Cargo places'),
    ('audit.field.carrier', 'ru', 'Перевозчик'),
    ('audit.field.carrier', 'en', 'Carrier'),
    ('audit.field.code', 'ru', 'Код'),
    ('audit.field.code', 'en', 'Code'),
    ('audit.field.coefficient', 'ru', 'Коэффициент'),
    ('audit.field.coefficient', 'en', 'Factor'),
    ('audit.field.color', 'ru', 'Цвет'),
    ('audit.field.color', 'en', 'Colour'),
    ('audit.field.comment', 'ru', 'Комментарий'),
    ('audit.field.comment', 'en', 'Comment'),
    ('audit.field.consignee_contact', 'ru', 'Контакт грузополучателя'),
    ('audit.field.consignee_contact', 'en', 'Consignee contact'),
    ('audit.field.consignee_id', 'ru', 'Грузополучатель'),
    ('audit.field.consignee_id', 'en', 'Consignee'),
    ('audit.field.contact', 'ru', 'Контакт'),
    ('audit.field.contact', 'en', 'Contact'),
    ('audit.field.contact_email', 'ru', 'Почта контакта'),
    ('audit.field.contact_email', 'en', 'Contact email'),
    ('audit.field.contact_name', 'ru', 'Контактное лицо'),
    ('audit.field.contact_name', 'en', 'Contact person'),
    ('audit.field.contact_phone', 'ru', 'Телефон контакта'),
    ('audit.field.contact_phone', 'en', 'Contact phone'),
    ('audit.field.contacts_content', 'ru', 'Текст страницы «Контакты»'),
    ('audit.field.contacts_content', 'en', '“Contacts” page text'),
    ('audit.field.container_number', 'ru', 'Номер контейнера'),
    ('audit.field.container_number', 'en', 'Container number'),
    ('audit.field.contract_number', 'ru', 'Номер договора'),
    ('audit.field.contract_number', 'en', 'Contract number'),
    ('audit.field.contract_valid_until', 'ru', 'Договор действует до'),
    ('audit.field.contract_valid_until', 'en', 'Contract valid until'),
    ('audit.field.cost', 'ru', 'Стоимость'),
    ('audit.field.cost', 'en', 'Cost'),
    ('audit.field.country_origin', 'ru', 'Страна производитель'),
    ('audit.field.country_origin', 'en', 'Country of origin'),
    ('audit.field.currency_id', 'ru', 'Валюта'),
    ('audit.field.currency_id', 'en', 'Currency'),
    ('audit.field.custom_attr1', 'ru', 'Доп. атрибут 1'),
    ('audit.field.custom_attr1', 'en', 'Custom attribute 1'),
    ('audit.field.custom_attr10', 'ru', 'Доп. атрибут 10'),
    ('audit.field.custom_attr10', 'en', 'Custom attribute 10'),
    ('audit.field.custom_attr2', 'ru', 'Доп. атрибут 2'),
    ('audit.field.custom_attr2', 'en', 'Custom attribute 2'),
    ('audit.field.custom_attr3', 'ru', 'Доп. атрибут 3'),
    ('audit.field.custom_attr3', 'en', 'Custom attribute 3'),
    ('audit.field.custom_attr4', 'ru', 'Доп. атрибут 4'),
    ('audit.field.custom_attr4', 'en', 'Custom attribute 4'),
    ('audit.field.custom_attr5', 'ru', 'Доп. атрибут 5'),
    ('audit.field.custom_attr5', 'en', 'Custom attribute 5'),
    ('audit.field.custom_attr6', 'ru', 'Доп. атрибут 6'),
    ('audit.field.custom_attr6', 'en', 'Custom attribute 6'),
    ('audit.field.custom_attr7', 'ru', 'Доп. атрибут 7'),
    ('audit.field.custom_attr7', 'en', 'Custom attribute 7'),
    ('audit.field.custom_attr8', 'ru', 'Доп. атрибут 8'),
    ('audit.field.custom_attr8', 'en', 'Custom attribute 8'),
    ('audit.field.custom_attr9', 'ru', 'Доп. атрибут 9'),
    ('audit.field.custom_attr9', 'en', 'Custom attribute 9'),
    ('audit.field.custom_field_1', 'ru', 'Доп. поле 1'),
    ('audit.field.custom_field_1', 'en', 'Custom field 1'),
    ('audit.field.custom_field_10', 'ru', 'Доп. поле 10'),
    ('audit.field.custom_field_10', 'en', 'Custom field 10'),
    ('audit.field.custom_field_2', 'ru', 'Доп. поле 2'),
    ('audit.field.custom_field_2', 'en', 'Custom field 2'),
    ('audit.field.custom_field_3', 'ru', 'Доп. поле 3'),
    ('audit.field.custom_field_3', 'en', 'Custom field 3'),
    ('audit.field.custom_field_4', 'ru', 'Доп. поле 4'),
    ('audit.field.custom_field_4', 'en', 'Custom field 4'),
    ('audit.field.custom_field_5', 'ru', 'Доп. поле 5'),
    ('audit.field.custom_field_5', 'en', 'Custom field 5'),
    ('audit.field.custom_field_6', 'ru', 'Доп. поле 6'),
    ('audit.field.custom_field_6', 'en', 'Custom field 6'),
    ('audit.field.custom_field_7', 'ru', 'Доп. поле 7'),
    ('audit.field.custom_field_7', 'en', 'Custom field 7'),
    ('audit.field.custom_field_8', 'ru', 'Доп. поле 8'),
    ('audit.field.custom_field_8', 'en', 'Custom field 8'),
    ('audit.field.custom_field_9', 'ru', 'Доп. поле 9'),
    ('audit.field.custom_field_9', 'en', 'Custom field 9'),
    ('audit.field.custom_label', 'ru', 'Своё наименование поля'),
    ('audit.field.custom_label', 'en', 'Custom field name'),
    ('audit.field.defect_qty', 'ru', 'Брак'),
    ('audit.field.defect_qty', 'en', 'Defective'),
    ('audit.field.delivery_date', 'ru', 'Дата доставки'),
    ('audit.field.delivery_date', 'en', 'Delivery date'),
    ('audit.field.delivery_point_id', 'ru', 'Место доставки'),
    ('audit.field.delivery_point_id', 'en', 'Delivery point'),
    ('audit.field.depositor_id', 'ru', 'Поклажедатель'),
    ('audit.field.depositor_id', 'en', 'Depositor'),
    ('audit.field.doc_date', 'ru', 'Дата документа'),
    ('audit.field.doc_date', 'en', 'Document date'),
    ('audit.field.doc_number', 'ru', 'Номер документа в WMS'),
    ('audit.field.doc_number', 'en', 'WMS document number'),
    ('audit.field.doc_type_id', 'ru', 'Тип приходного документа'),
    ('audit.field.doc_type_id', 'en', 'Receipt document type'),
    ('audit.field.driver_id_number', 'ru', 'Номер удостоверения водителя'),
    ('audit.field.driver_id_number', 'en', 'Driver ID number'),
    ('audit.field.driver_inn', 'ru', 'ИИН водителя'),
    ('audit.field.driver_inn', 'en', 'Driver tax ID'),
    ('audit.field.driver_name', 'ru', 'Водитель'),
    ('audit.field.driver_name', 'en', 'Driver'),
    ('audit.field.driver_phone', 'ru', 'Телефон водителя'),
    ('audit.field.driver_phone', 'en', 'Driver phone'),
    ('audit.field.email', 'ru', 'Почта'),
    ('audit.field.email', 'en', 'Email'),
    ('audit.field.enstru_code', 'ru', 'Код ЕНСТРУ'),
    ('audit.field.enstru_code', 'en', 'ENSTRU code'),
    ('audit.field.erp_number', 'ru', 'Номер в ERP'),
    ('audit.field.erp_number', 'en', 'ERP number'),
    ('audit.field.eta_date', 'ru', 'Ожидаемая дата'),
    ('audit.field.eta_date', 'en', 'Expected date'),
    ('audit.field.expected_qty', 'ru', 'Ожидается'),
    ('audit.field.expected_qty', 'en', 'Expected'),
    ('audit.field.expected_qty_wms', 'ru', 'Ожидается (WMS)'),
    ('audit.field.expected_qty_wms', 'en', 'Expected (WMS)'),
    ('audit.field.expiry_date', 'ru', 'Срок годности'),
    ('audit.field.expiry_date', 'en', 'Expiry date'),
    ('audit.field.expiry_tracking', 'ru', 'Учёт срока годности'),
    ('audit.field.expiry_tracking', 'en', 'Expiry tracking'),
    ('audit.field.from_warehouse_id', 'ru', 'Склад-отправитель'),
    ('audit.field.from_warehouse_id', 'en', 'Source warehouse'),
    ('audit.field.full_name', 'ru', 'ФИО'),
    ('audit.field.full_name', 'en', 'Full name'),
    ('audit.field.gross_weight_kg', 'ru', 'Вес брутто, кг'),
    ('audit.field.gross_weight_kg', 'en', 'Gross weight, kg'),
    ('audit.field.gtd', 'ru', 'Номер ГТД'),
    ('audit.field.gtd', 'en', 'Customs declaration number'),
    ('audit.field.has_packaging', 'ru', 'Есть упаковки'),
    ('audit.field.has_packaging', 'en', 'Has packaging'),
    ('audit.field.hazard_class_id', 'ru', 'Класс опасности'),
    ('audit.field.hazard_class_id', 'en', 'Hazard class'),
    ('audit.field.hazard_group_id', 'ru', 'Группа опасности'),
    ('audit.field.hazard_group_id', 'en', 'Hazard group'),
    ('audit.field.height', 'ru', 'Высота'),
    ('audit.field.height', 'en', 'Height'),
    ('audit.field.height_cm', 'ru', 'Высота, см'),
    ('audit.field.height_cm', 'en', 'Height, cm'),
    ('audit.field.hide_billing_tab', 'ru', 'Скрыть «Биллинг»'),
    ('audit.field.hide_billing_tab', 'en', 'Hide “Billing”'),
    ('audit.field.hide_services_tab', 'ru', 'Скрыть вкладку «Услуги»'),
    ('audit.field.hide_services_tab', 'en', 'Hide “Services” tab'),
    ('audit.field.hide_transfers_tab', 'ru', 'Скрыть «Перемещения»'),
    ('audit.field.hide_transfers_tab', 'en', 'Hide “Transfers”'),
    ('audit.field.hs_code', 'ru', 'Код ТНВЭД'),
    ('audit.field.hs_code', 'en', 'HS code'),
    ('audit.field.invoice_number', 'ru', 'Номер счёта-фактуры'),
    ('audit.field.invoice_number', 'en', 'Invoice number'),
    ('audit.field.is_active', 'ru', 'Действует'),
    ('audit.field.is_active', 'en', 'Active'),
    ('audit.field.is_available', 'ru', 'Доступно клиенту'),
    ('audit.field.is_available', 'en', 'Available to the client'),
    ('audit.field.is_controlled', 'ru', 'Контролируемый/лицензируемый товар'),
    ('audit.field.is_controlled', 'en', 'Controlled / licensed goods'),
    ('audit.field.is_default_address', 'ru', 'Основной адрес'),
    ('audit.field.is_default_address', 'en', 'Default address'),
    ('audit.field.is_default_column', 'ru', 'Показывать по умолчанию'),
    ('audit.field.is_default_column', 'en', 'Shown by default'),
    ('audit.field.is_default_locale', 'ru', 'Язык по умолчанию'),
    ('audit.field.is_default_locale', 'en', 'Default language'),
    ('audit.field.is_excise', 'ru', 'Акцизный товар'),
    ('audit.field.is_excise', 'en', 'Excisable goods'),
    ('audit.field.is_fragile', 'ru', 'Хрупкий товар'),
    ('audit.field.is_fragile', 'en', 'Fragile'),
    ('audit.field.is_kit', 'ru', 'Признак комплекта'),
    ('audit.field.is_kit', 'en', 'Kit'),
    ('audit.field.is_marking_required', 'ru', 'Обязательная маркировка'),
    ('audit.field.is_marking_required', 'en', 'Mandatory labelling'),
    ('audit.field.is_serial_tracked', 'ru', 'Учёт серийных номеров'),
    ('audit.field.is_serial_tracked', 'en', 'Serial number tracking'),
    ('audit.field.is_wms_active', 'ru', 'Данные WMS действительны'),
    ('audit.field.is_wms_active', 'en', 'WMS data valid'),
    ('audit.field.item_code', 'ru', 'Код товара'),
    ('audit.field.item_code', 'en', 'Item code'),
    ('audit.field.item_full_name', 'ru', 'Полное наименование товара'),
    ('audit.field.item_full_name', 'en', 'Full item name'),
    ('audit.field.item_group', 'ru', 'Группа товара'),
    ('audit.field.item_group', 'en', 'Product group'),
    ('audit.field.item_type', 'ru', 'Тип товара'),
    ('audit.field.item_type', 'en', 'Item type'),
    ('audit.field.legal_address', 'ru', 'Юридический адрес'),
    ('audit.field.legal_address', 'en', 'Legal address'),
    ('audit.field.length', 'ru', 'Длина'),
    ('audit.field.length', 'en', 'Length'),
    ('audit.field.length_cm', 'ru', 'Длина, см'),
    ('audit.field.length_cm', 'en', 'Length, cm'),
    ('audit.field.line_comment', 'ru', 'Комментарий строки'),
    ('audit.field.line_comment', 'en', 'Line comment'),
    ('audit.field.locale_id', 'ru', 'Язык'),
    ('audit.field.locale_id', 'en', 'Language'),
    ('audit.field.login', 'ru', 'Логин'),
    ('audit.field.login', 'en', 'Login'),
    ('audit.field.manufacturer', 'ru', 'Производитель'),
    ('audit.field.manufacturer', 'en', 'Manufacturer'),
    ('audit.field.max_humidity', 'ru', 'Макс. влажность хранения'),
    ('audit.field.max_humidity', 'en', 'Max storage humidity'),
    ('audit.field.max_storage_temp', 'ru', 'Макс. температура хранения'),
    ('audit.field.max_storage_temp', 'en', 'Max storage temperature'),
    ('audit.field.min_days_for_receipt', 'ru', 'Мин. кол-во дней для приёмки'),
    ('audit.field.min_days_for_receipt', 'en', 'Min days for receiving'),
    ('audit.field.min_days_for_reservation', 'ru', 'Мин. кол-во дней для резервирования'),
    ('audit.field.min_days_for_reservation', 'en', 'Min days for reservation'),
    ('audit.field.min_humidity', 'ru', 'Мин. влажность хранения'),
    ('audit.field.min_humidity', 'en', 'Min storage humidity'),
    ('audit.field.min_storage_temp', 'ru', 'Мин. температура хранения'),
    ('audit.field.min_storage_temp', 'en', 'Min storage temperature'),
    ('audit.field.must_change_password', 'ru', 'Сменить пароль при входе'),
    ('audit.field.must_change_password', 'en', 'Change password at sign-in'),
    ('audit.field.name', 'ru', 'Наименование'),
    ('audit.field.name', 'en', 'Name'),
    ('audit.field.operator_id', 'ru', 'Организация оператора'),
    ('audit.field.operator_id', 'en', 'Operator organization'),
    ('audit.field.order_type_id', 'ru', 'Тип заказа'),
    ('audit.field.order_type_id', 'en', 'Order type'),
    ('audit.field.order_unit_id', 'ru', 'Единица заказа'),
    ('audit.field.order_unit_id', 'en', 'Order unit'),
    ('audit.field.ordered_qty', 'ru', 'Заказано'),
    ('audit.field.ordered_qty', 'en', 'Ordered'),
    ('audit.field.ordered_qty_wms', 'ru', 'Заказано (WMS)'),
    ('audit.field.ordered_qty_wms', 'en', 'Ordered (WMS)'),
    ('audit.field.org_name', 'ru', 'Наименование организации'),
    ('audit.field.org_name', 'en', 'Organization name'),
    ('audit.field.organization', 'ru', 'Организация'),
    ('audit.field.organization', 'en', 'Organization'),
    ('audit.field.packaging_code', 'ru', 'Код упаковки'),
    ('audit.field.packaging_code', 'en', 'Packaging code'),
    ('audit.field.packaging_type_id', 'ru', 'Тип упаковки'),
    ('audit.field.packaging_type_id', 'en', 'Packaging type'),
    ('audit.field.pallet_count', 'ru', 'Паллет'),
    ('audit.field.pallet_count', 'en', 'Pallets'),
    ('audit.field.password_hash', 'ru', 'Пароль'),
    ('audit.field.password_hash', 'en', 'Password'),
    ('audit.field.phone', 'ru', 'Телефон'),
    ('audit.field.phone', 'en', 'Phone'),
    ('audit.field.picked_qty', 'ru', 'Собрано'),
    ('audit.field.picked_qty', 'en', 'Picked'),
    ('audit.field.planned_ship_date', 'ru', 'Планируемая дата отгрузки'),
    ('audit.field.planned_ship_date', 'en', 'Planned ship date'),
    ('audit.field.position', 'ru', 'Должность'),
    ('audit.field.position', 'en', 'Position'),
    ('audit.field.price', 'ru', 'Цена'),
    ('audit.field.price', 'en', 'Price'),
    ('audit.field.price_in_orders', 'ru', 'Цена в заказах'),
    ('audit.field.price_in_orders', 'en', 'Price in orders'),
    ('audit.field.price_in_receipts', 'ru', 'Цена в поступлениях'),
    ('audit.field.price_in_receipts', 'en', 'Price in receipts'),
    ('audit.field.qty', 'ru', 'Количество'),
    ('audit.field.qty', 'en', 'Quantity'),
    ('audit.field.qty_wms', 'ru', 'Количество (WMS)'),
    ('audit.field.qty_wms', 'en', 'Quantity (WMS)'),
    ('audit.field.quality_id', 'ru', 'Качество'),
    ('audit.field.quality_id', 'en', 'Quality'),
    ('audit.field.received_date', 'ru', 'Дата приёмки'),
    ('audit.field.received_date', 'en', 'Receiving date'),
    ('audit.field.receiving_end_date', 'ru', 'Дата окончания приёмки'),
    ('audit.field.receiving_end_date', 'en', 'Receiving end date'),
    ('audit.field.responsible', 'ru', 'Ответственный'),
    ('audit.field.responsible', 'en', 'Responsible person'),
    ('audit.field.role_id', 'ru', 'Роль'),
    ('audit.field.role_id', 'en', 'Role'),
    ('audit.field.seal_number', 'ru', 'Номер пломбы'),
    ('audit.field.seal_number', 'en', 'Seal number'),
    ('audit.field.series_number', 'ru', 'Серия'),
    ('audit.field.series_number', 'en', 'Series'),
    ('audit.field.shelf_life_days', 'ru', 'Срок хранения, дней'),
    ('audit.field.shelf_life_days', 'en', 'Shelf life, days'),
    ('audit.field.ship_date', 'ru', 'Дата отгрузки'),
    ('audit.field.ship_date', 'en', 'Ship date'),
    ('audit.field.size', 'ru', 'Размер'),
    ('audit.field.size', 'en', 'Size'),
    ('audit.field.sku_id', 'ru', 'Товар'),
    ('audit.field.sku_id', 'en', 'Item'),
    ('audit.field.sku_status', 'ru', 'Статус товара'),
    ('audit.field.sku_status', 'en', 'Item status'),
    ('audit.field.status_id', 'ru', 'Статус'),
    ('audit.field.status_id', 'en', 'Status'),
    ('audit.field.supplier_id', 'ru', 'Поставщик'),
    ('audit.field.supplier_id', 'en', 'Supplier'),
    ('audit.field.symbol', 'ru', 'Обозначение'),
    ('audit.field.symbol', 'en', 'Symbol'),
    ('audit.field.tech_passport_number', 'ru', 'Номер техпаспорта'),
    ('audit.field.tech_passport_number', 'en', 'Vehicle registration certificate'),
    ('audit.field.text_value', 'ru', 'Текст'),
    ('audit.field.text_value', 'en', 'Text'),
    ('audit.field.to_warehouse_id', 'ru', 'Склад-получатель'),
    ('audit.field.to_warehouse_id', 'en', 'Destination warehouse'),
    ('audit.field.total_pieces', 'ru', 'Всего штук'),
    ('audit.field.total_pieces', 'en', 'Total pieces'),
    ('audit.field.trailer_number', 'ru', 'Номер прицепа'),
    ('audit.field.trailer_number', 'en', 'Trailer number'),
    ('audit.field.transport_brand', 'ru', 'Марка транспорта'),
    ('audit.field.transport_brand', 'en', 'Vehicle make'),
    ('audit.field.transport_state', 'ru', 'Передача транспорта'),
    ('audit.field.transport_state', 'en', 'Transport transfer'),
    ('audit.field.transport_type', 'ru', 'Тип транспорта'),
    ('audit.field.transport_type', 'en', 'Transport type'),
    ('audit.field.trip', 'ru', 'Рейс'),
    ('audit.field.trip', 'en', 'Trip'),
    ('audit.field.uom_id', 'ru', 'Базовая единица измерения'),
    ('audit.field.uom_id', 'en', 'Base unit of measure'),
    ('audit.field.volume', 'ru', 'Объём'),
    ('audit.field.volume', 'en', 'Volume'),
    ('audit.field.volume_m3', 'ru', 'Объём, м³'),
    ('audit.field.volume_m3', 'en', 'Volume, m³'),
    ('audit.field.warehouse_id', 'ru', 'Склад'),
    ('audit.field.warehouse_id', 'en', 'Warehouse'),
    ('audit.field.weight_gross', 'ru', 'Вес брутто'),
    ('audit.field.weight_gross', 'en', 'Gross weight'),
    ('audit.field.weight_kg', 'ru', 'Вес, кг'),
    ('audit.field.weight_kg', 'en', 'Weight, kg'),
    ('audit.field.weight_net', 'ru', 'Вес нетто'),
    ('audit.field.weight_net', 'en', 'Net weight'),
    ('audit.field.width', 'ru', 'Ширина'),
    ('audit.field.width', 'en', 'Width'),
    ('audit.field.width_cm', 'ru', 'Ширина, см'),
    ('audit.field.width_cm', 'en', 'Width, cm'),
    ('audit.field.work_hours', 'ru', 'Режим работы'),
    ('audit.field.work_hours', 'en', 'Working hours'),
    ('audit.field.xyz_class', 'ru', 'XYZ Класс'),
    ('audit.field.xyz_class', 'en', 'XYZ class'),
    ('audit.tab.history', 'ru', 'История'),
    ('audit.tab.history', 'en', 'History'),
    ('audit.text.no_history', 'ru', 'Изменений пока нет'),
    ('audit.text.no_history', 'en', 'No changes yet'),
    ('audit.action.doc_created', 'ru', 'Документ создан'),
    ('audit.action.doc_created', 'en', 'Document created'),
    ('audit.action.changed', 'ru', 'Изменения'),
    ('audit.action.changed', 'en', 'Changes'),
    ('audit.action.status', 'ru', 'Смена статуса'),
    ('audit.action.status', 'en', 'Status change'),
    ('audit.action.line_added', 'ru', 'Добавлена строка {n}'),
    ('audit.action.line_added', 'en', 'Line {n} added'),
    ('audit.action.line_changed', 'ru', 'Изменена строка {n}'),
    ('audit.action.line_changed', 'en', 'Line {n} changed'),
    ('audit.action.line_deleted', 'ru', 'Удалена строка {n}'),
    ('audit.action.line_deleted', 'en', 'Line {n} deleted'),
    ('audit.column.when', 'ru', 'Когда'),
    ('audit.column.when', 'en', 'When'),
    ('audit.column.who', 'ru', 'Кто'),
    ('audit.column.who', 'en', 'Who'),
    ('audit.column.field', 'ru', 'Поле'),
    ('audit.column.field', 'en', 'Field'),
    ('audit.column.was', 'ru', 'Было'),
    ('audit.column.was', 'en', 'Was'),
    ('audit.column.became', 'ru', 'Стало'),
    ('audit.column.became', 'en', 'Became')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------- Колонки журналируемых таблиц ----------------------------------------
INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order) VALUES
  ('app_user', 'user_id', 'skip', NULL, 0),
  ('app_user', 'login', 'text', 'audit.field.login', 1),
  ('app_user', 'password_hash', 'secret', 'audit.field.password_hash', 2),
  ('app_user', 'full_name', 'text', 'audit.field.full_name', 3),
  ('app_user', 'role_id', 'enum:enum_user_role', 'audit.field.role_id', 4),
  ('app_user', 'position', 'text', 'audit.field.position', 5),
  ('app_user', 'organization', 'text', 'audit.field.organization', 6),
  ('app_user', 'phone', 'text', 'audit.field.phone', 7),
  ('app_user', 'is_active', 'bool', 'audit.field.is_active', 8),
  ('app_user', 'last_login_at', 'skip', NULL, 9),
  ('app_user', 'failed_login_count', 'skip', NULL, 10),
  ('app_user', 'locked_until', 'skip', NULL, 11),
  ('app_user', 'password_changed_at', 'skip', NULL, 12),
  ('app_user', 'must_change_password', 'bool', 'audit.field.must_change_password', 13),
  ('app_user', 'locale_id', 'ref:locale', 'audit.field.locale_id', 14),
  ('app_user', 'all_depositors', 'bool', 'audit.field.all_depositors', 15),
  ('app_user', 'all_warehouses', 'bool', 'audit.field.all_warehouses', 16),
  ('consignee', 'consignee_id', 'skip', NULL, 17),
  ('consignee', 'depositor_id', 'skip', NULL, 18),
  ('consignee', 'code', 'text', 'audit.field.code', 19),
  ('consignee', 'name', 'text', 'audit.field.name', 20),
  ('consignee', 'bin', 'text', 'audit.field.bin', 21),
  ('consignee', 'legal_address', 'text', 'audit.field.legal_address', 22),
  ('consignee', 'contact_name', 'text', 'audit.field.contact_name', 23),
  ('consignee', 'contact_phone', 'text', 'audit.field.contact_phone', 24),
  ('consignee', 'contact_email', 'text', 'audit.field.contact_email', 25),
  ('consignee', 'is_active', 'bool', 'audit.field.is_active', 26),
  ('delivery_point', 'address_id', 'skip', NULL, 27),
  ('delivery_point', 'consignee_id', 'skip', NULL, 28),
  ('delivery_point', 'address', 'text', 'audit.field.address', 29),
  ('delivery_point', 'is_default', 'bool', 'audit.field.is_default_address', 30),
  ('delivery_point', 'sort_order', 'skip', NULL, 31),
  ('delivery_point', 'contact', 'text', 'audit.field.contact', 32),
  ('depositor', 'depositor_id', 'skip', NULL, 33),
  ('depositor', 'code', 'text', 'audit.field.code', 34),
  ('depositor', 'org_name', 'text', 'audit.field.org_name', 35),
  ('depositor', 'bin', 'text', 'audit.field.bin', 36),
  ('depositor', 'legal_address', 'text', 'audit.field.legal_address', 37),
  ('depositor', 'phone', 'text', 'audit.field.phone', 38),
  ('depositor', 'email', 'text', 'audit.field.email', 39),
  ('depositor', 'contract_number', 'text', 'audit.field.contract_number', 40),
  ('depositor', 'contract_valid_until', 'text', 'audit.field.contract_valid_until', 41),
  ('depositor', 'operator_id', 'ref:operator', 'audit.field.operator_id', 42),
  ('depositor', 'is_active', 'bool', 'audit.field.is_active', 43),
  ('depositor_field_label', 'id', 'skip', NULL, 44),
  ('depositor_field_label', 'depositor_id', 'skip', NULL, 45),
  ('depositor_field_label', 'object_type_id', 'skip', NULL, 46),
  ('depositor_field_label', 'field_id', 'skip', NULL, 47),
  ('depositor_field_label', 'custom_label', 'text', 'audit.field.custom_label', 48),
  ('depositor_settings', 'depositor_id', 'skip', NULL, 49),
  ('depositor_settings', 'hide_transfers_tab', 'bool', 'audit.field.hide_transfers_tab', 50),
  ('depositor_settings', 'price_in_receipts', 'bool', 'audit.field.price_in_receipts', 51),
  ('depositor_settings', 'price_in_orders', 'bool', 'audit.field.price_in_orders', 52),
  ('depositor_table_column', 'depositor_id', 'skip', NULL, 53),
  ('depositor_table_column', 'table_key', 'skip', NULL, 54),
  ('depositor_table_column', 'column_id', 'skip', NULL, 55),
  ('depositor_table_column', 'is_available', 'bool', 'audit.field.is_available', 56),
  ('depositor_table_column', 'is_default', 'bool', 'audit.field.is_default_column', 57),
  ('depositor_table_column', 'sort_order', 'skip', NULL, 58),
  ('dictionary_locale', 'locale_id', 'skip', NULL, 59),
  ('dictionary_locale', 'code', 'text', 'audit.field.code', 60),
  ('dictionary_locale', 'name', 'text', 'audit.field.name', 61),
  ('dictionary_locale', 'is_default', 'bool', 'audit.field.is_default_locale', 62),
  ('dictionary_locale', 'sort_order', 'skip', NULL, 63),
  ('dictionary_locale', 'is_active', 'bool', 'audit.field.is_active', 64),
  ('enum_text', 'enum_key', 'skip', NULL, 65),
  ('enum_text', 'value_id', 'skip', NULL, 66),
  ('enum_text', 'locale_id', 'skip', NULL, 67),
  ('enum_text', 'name', 'text', 'audit.field.name', 68),
  ('enum_text', 'symbol', 'text', 'audit.field.symbol', 69),
  ('operator', 'operator_id', 'skip', NULL, 70),
  ('operator', 'code', 'text', 'audit.field.code', 71),
  ('operator', 'name', 'text', 'audit.field.name', 72),
  ('operator', 'bin', 'text', 'audit.field.bin', 73),
  ('operator', 'legal_address', 'text', 'audit.field.legal_address', 74),
  ('operator', 'bank_name', 'text', 'audit.field.bank_name', 75),
  ('operator', 'bank_account', 'text', 'audit.field.bank_account', 76),
  ('operator', 'phone', 'text', 'audit.field.phone', 77),
  ('operator', 'email', 'text', 'audit.field.email', 78),
  ('operator', 'logo_object_key', 'skip', NULL, 79),
  ('operator', 'about_content', 'text', 'audit.field.about_content', 80),
  ('operator', 'contacts_content', 'text', 'audit.field.contacts_content', 81),
  ('operator', 'is_active', 'bool', 'audit.field.is_active', 82),
  ('order_details', 'line_id', 'skip', NULL, 83),
  ('order_details', 'doc_id', 'skip', NULL, 84),
  ('order_details', 'line_number', 'skip', NULL, 85),
  ('order_details', 'sku_id', 'ref:sku', 'audit.field.sku_id', 86),
  ('order_details', 'ordered_qty', 'text', 'audit.field.ordered_qty', 87),
  ('order_details', 'ordered_qty_wms', 'text', 'audit.field.ordered_qty_wms', 88),
  ('order_details', 'picked_qty', 'text', 'audit.field.picked_qty', 89),
  ('order_details', 'weight_kg', 'text', 'audit.field.weight_kg', 90),
  ('order_details', 'batch_number', 'text', 'audit.field.batch_number', 91),
  ('order_details', 'series_number', 'text', 'audit.field.series_number', 92),
  ('order_details', 'expiry_date', 'text', 'audit.field.expiry_date', 93),
  ('order_details', 'price', 'text', 'audit.field.price', 94),
  ('order_details', 'currency_id', 'enum:enum_currency', 'audit.field.currency_id', 95),
  ('order_details', 'line_comment', 'text', 'audit.field.line_comment', 96),
  ('order_details', 'custom_field_1', 'text', 'audit.field.custom_field_1', 97),
  ('order_details', 'custom_field_2', 'text', 'audit.field.custom_field_2', 98),
  ('order_details', 'custom_field_3', 'text', 'audit.field.custom_field_3', 99),
  ('order_details', 'custom_field_4', 'text', 'audit.field.custom_field_4', 100),
  ('order_details', 'custom_field_5', 'text', 'audit.field.custom_field_5', 101),
  ('order_details', 'custom_field_6', 'text', 'audit.field.custom_field_6', 102),
  ('order_details', 'custom_field_7', 'text', 'audit.field.custom_field_7', 103),
  ('order_details', 'custom_field_8', 'text', 'audit.field.custom_field_8', 104),
  ('order_details', 'custom_field_9', 'text', 'audit.field.custom_field_9', 105),
  ('order_details', 'custom_field_10', 'text', 'audit.field.custom_field_10', 106),
  ('order_details', 'quality_id', 'enum:enum_quality', 'audit.field.quality_id', 107),
  ('orders', 'doc_id', 'skip', NULL, 108),
  ('orders', 'document_no', 'skip', NULL, 109),
  ('orders', 'depositor_id', 'skip', NULL, 110),
  ('orders', 'doc_number', 'text', 'audit.field.doc_number', 111),
  ('orders', 'erp_number', 'text', 'audit.field.erp_number', 112),
  ('orders', 'author_id', 'skip', NULL, 113),
  ('orders', 'status_id', 'enum:enum_status', 'audit.field.status_id', 114),
  ('orders', 'doc_date', 'text', 'audit.field.doc_date', 115),
  ('orders', 'warehouse_id', 'ref:warehouse', 'audit.field.warehouse_id', 116),
  ('orders', 'trip', 'text', 'audit.field.trip', 117),
  ('orders', 'comment', 'text', 'audit.field.comment', 118),
  ('orders', 'order_type_id', 'enum:enum_order_type', 'audit.field.order_type_id', 119),
  ('orders', 'consignee_id', 'ref:consignee', 'audit.field.consignee_id', 120),
  ('orders', 'delivery_point_id', 'ref:delivery_point', 'audit.field.delivery_point_id', 121),
  ('orders', 'consignee_contact', 'text', 'audit.field.consignee_contact', 122),
  ('orders', 'planned_ship_date', 'text', 'audit.field.planned_ship_date', 123),
  ('orders', 'ship_date', 'text', 'audit.field.ship_date', 124),
  ('orders', 'line_count', 'skip', NULL, 125),
  ('orders', 'total_pieces', 'text', 'audit.field.total_pieces', 126),
  ('orders', 'cargo_places', 'text', 'audit.field.cargo_places', 127),
  ('orders', 'pallet_count', 'text', 'audit.field.pallet_count', 128),
  ('orders', 'weight_kg', 'text', 'audit.field.weight_kg', 129),
  ('orders', 'volume_m3', 'text', 'audit.field.volume_m3', 130),
  ('orders', 'carrier', 'text', 'audit.field.carrier', 131),
  ('orders', 'transport_type', 'enumcode:enum_transport_type', 'audit.field.transport_type', 132),
  ('orders', 'transport_brand', 'text', 'audit.field.transport_brand', 133),
  ('orders', 'car_number', 'text', 'audit.field.car_number', 134),
  ('orders', 'trailer_number', 'text', 'audit.field.trailer_number', 135),
  ('orders', 'tech_passport_number', 'text', 'audit.field.tech_passport_number', 136),
  ('orders', 'container_number', 'text', 'audit.field.container_number', 137),
  ('orders', 'seal_number', 'text', 'audit.field.seal_number', 138),
  ('orders', 'driver_name', 'text', 'audit.field.driver_name', 139),
  ('orders', 'driver_phone', 'text', 'audit.field.driver_phone', 140),
  ('orders', 'driver_id_number', 'text', 'audit.field.driver_id_number', 141),
  ('orders', 'driver_inn', 'text', 'audit.field.driver_inn', 142),
  ('orders', 'delivery_date', 'text', 'audit.field.delivery_date', 143),
  ('orders', 'custom_field_1', 'text', 'audit.field.custom_field_1', 144),
  ('orders', 'custom_field_2', 'text', 'audit.field.custom_field_2', 145),
  ('orders', 'custom_field_3', 'text', 'audit.field.custom_field_3', 146),
  ('orders', 'custom_field_4', 'text', 'audit.field.custom_field_4', 147),
  ('orders', 'custom_field_5', 'text', 'audit.field.custom_field_5', 148),
  ('orders', 'custom_field_6', 'text', 'audit.field.custom_field_6', 149),
  ('orders', 'custom_field_7', 'text', 'audit.field.custom_field_7', 150),
  ('orders', 'custom_field_8', 'text', 'audit.field.custom_field_8', 151),
  ('orders', 'custom_field_9', 'text', 'audit.field.custom_field_9', 152),
  ('orders', 'custom_field_10', 'text', 'audit.field.custom_field_10', 153),
  ('orders', 'origin', 'skip', NULL, 154),
  ('orders', 'wms_status_raw', 'skip', NULL, 155),
  ('orders', 'confirmed_at', 'skip', NULL, 156),
  ('orders', 'created_by', 'skip', NULL, 157),
  ('orders', 'created_at', 'skip', NULL, 158),
  ('orders', 'updated_by', 'skip', NULL, 159),
  ('orders', 'updated_at', 'skip', NULL, 160),
  ('orders', 'transport_state', 'enumcode:enum_transport_state', 'audit.field.transport_state', 161),
  ('orders', 'transport_sent_at', 'skip', NULL, 162),
  ('orders', 'transport_error', 'skip', NULL, 163),
  ('product_packaging', 'packaging_id', 'skip', NULL, 164),
  ('product_packaging', 'depositor_id', 'skip', NULL, 165),
  ('product_packaging', 'sku_id', 'ref:sku', 'audit.field.sku_id', 166),
  ('product_packaging', 'packaging_code', 'text', 'audit.field.packaging_code', 167),
  ('product_packaging', 'packaging_type_id', 'enum:enum_packaging_type', 'audit.field.packaging_type_id', 168),
  ('product_packaging', 'barcode', 'text', 'audit.field.barcode', 169),
  ('product_packaging', 'coefficient', 'text', 'audit.field.coefficient', 170),
  ('product_packaging', 'length_cm', 'text', 'audit.field.length_cm', 171),
  ('product_packaging', 'width_cm', 'text', 'audit.field.width_cm', 172),
  ('product_packaging', 'height_cm', 'text', 'audit.field.height_cm', 173),
  ('product_packaging', 'volume_m3', 'text', 'audit.field.volume_m3', 174),
  ('product_packaging', 'gross_weight_kg', 'text', 'audit.field.gross_weight_kg', 175),
  ('receipt_details', 'line_id', 'skip', NULL, 176),
  ('receipt_details', 'doc_id', 'skip', NULL, 177),
  ('receipt_details', 'line_number', 'skip', NULL, 178),
  ('receipt_details', 'sku_id', 'ref:sku', 'audit.field.sku_id', 179),
  ('receipt_details', 'expected_qty', 'text', 'audit.field.expected_qty', 180),
  ('receipt_details', 'expected_qty_wms', 'text', 'audit.field.expected_qty_wms', 181),
  ('receipt_details', 'accepted_qty', 'text', 'audit.field.accepted_qty', 182),
  ('receipt_details', 'defect_qty', 'text', 'audit.field.defect_qty', 183),
  ('receipt_details', 'batch_number', 'text', 'audit.field.batch_number', 184),
  ('receipt_details', 'series_number', 'text', 'audit.field.series_number', 185),
  ('receipt_details', 'expiry_date', 'text', 'audit.field.expiry_date', 186),
  ('receipt_details', 'price', 'text', 'audit.field.price', 187),
  ('receipt_details', 'currency_id', 'enum:enum_currency', 'audit.field.currency_id', 188),
  ('receipt_details', 'line_comment', 'text', 'audit.field.line_comment', 189),
  ('receipt_details', 'custom_field_1', 'text', 'audit.field.custom_field_1', 190),
  ('receipt_details', 'custom_field_2', 'text', 'audit.field.custom_field_2', 191),
  ('receipt_details', 'custom_field_3', 'text', 'audit.field.custom_field_3', 192),
  ('receipt_details', 'custom_field_4', 'text', 'audit.field.custom_field_4', 193),
  ('receipt_details', 'custom_field_5', 'text', 'audit.field.custom_field_5', 194),
  ('receipt_details', 'custom_field_6', 'text', 'audit.field.custom_field_6', 195),
  ('receipt_details', 'custom_field_7', 'text', 'audit.field.custom_field_7', 196),
  ('receipt_details', 'custom_field_8', 'text', 'audit.field.custom_field_8', 197),
  ('receipt_details', 'custom_field_9', 'text', 'audit.field.custom_field_9', 198),
  ('receipt_details', 'custom_field_10', 'text', 'audit.field.custom_field_10', 199),
  ('receipt_details', 'quality_id', 'enum:enum_quality', 'audit.field.quality_id', 200),
  ('receipts', 'doc_id', 'skip', NULL, 201),
  ('receipts', 'document_no', 'skip', NULL, 202),
  ('receipts', 'depositor_id', 'skip', NULL, 203),
  ('receipts', 'doc_number', 'text', 'audit.field.doc_number', 204),
  ('receipts', 'erp_number', 'text', 'audit.field.erp_number', 205),
  ('receipts', 'author_id', 'skip', NULL, 206),
  ('receipts', 'status_id', 'enum:enum_status', 'audit.field.status_id', 207),
  ('receipts', 'doc_date', 'text', 'audit.field.doc_date', 208),
  ('receipts', 'warehouse_id', 'ref:warehouse', 'audit.field.warehouse_id', 209),
  ('receipts', 'trip', 'text', 'audit.field.trip', 210),
  ('receipts', 'comment', 'text', 'audit.field.comment', 211),
  ('receipts', 'doc_type_id', 'enum:enum_receipt_doc_type', 'audit.field.doc_type_id', 212),
  ('receipts', 'supplier_id', 'ref:supplier', 'audit.field.supplier_id', 213),
  ('receipts', 'accompanying_doc_number', 'text', 'audit.field.accompanying_doc_number', 214),
  ('receipts', 'invoice_number', 'text', 'audit.field.invoice_number', 215),
  ('receipts', 'gtd', 'text', 'audit.field.gtd', 216),
  ('receipts', 'eta_date', 'text', 'audit.field.eta_date', 217),
  ('receipts', 'arrival_date', 'text', 'audit.field.arrival_date', 218),
  ('receipts', 'receiving_end_date', 'text', 'audit.field.receiving_end_date', 219),
  ('receipts', 'line_count', 'skip', NULL, 220),
  ('receipts', 'total_pieces', 'text', 'audit.field.total_pieces', 221),
  ('receipts', 'cargo_places', 'text', 'audit.field.cargo_places', 222),
  ('receipts', 'pallet_count', 'text', 'audit.field.pallet_count', 223),
  ('receipts', 'weight_kg', 'text', 'audit.field.weight_kg', 224),
  ('receipts', 'volume_m3', 'text', 'audit.field.volume_m3', 225),
  ('receipts', 'carrier', 'text', 'audit.field.carrier', 226),
  ('receipts', 'transport_type', 'enumcode:enum_transport_type', 'audit.field.transport_type', 227),
  ('receipts', 'transport_brand', 'text', 'audit.field.transport_brand', 228),
  ('receipts', 'car_number', 'text', 'audit.field.car_number', 229),
  ('receipts', 'trailer_number', 'text', 'audit.field.trailer_number', 230),
  ('receipts', 'tech_passport_number', 'text', 'audit.field.tech_passport_number', 231),
  ('receipts', 'container_number', 'text', 'audit.field.container_number', 232),
  ('receipts', 'seal_number', 'text', 'audit.field.seal_number', 233),
  ('receipts', 'driver_name', 'text', 'audit.field.driver_name', 234),
  ('receipts', 'driver_phone', 'text', 'audit.field.driver_phone', 235),
  ('receipts', 'driver_id_number', 'text', 'audit.field.driver_id_number', 236),
  ('receipts', 'driver_inn', 'text', 'audit.field.driver_inn', 237),
  ('receipts', 'custom_field_1', 'text', 'audit.field.custom_field_1', 238),
  ('receipts', 'custom_field_2', 'text', 'audit.field.custom_field_2', 239),
  ('receipts', 'custom_field_3', 'text', 'audit.field.custom_field_3', 240),
  ('receipts', 'custom_field_4', 'text', 'audit.field.custom_field_4', 241),
  ('receipts', 'custom_field_5', 'text', 'audit.field.custom_field_5', 242),
  ('receipts', 'custom_field_6', 'text', 'audit.field.custom_field_6', 243),
  ('receipts', 'custom_field_7', 'text', 'audit.field.custom_field_7', 244),
  ('receipts', 'custom_field_8', 'text', 'audit.field.custom_field_8', 245),
  ('receipts', 'custom_field_9', 'text', 'audit.field.custom_field_9', 246),
  ('receipts', 'custom_field_10', 'text', 'audit.field.custom_field_10', 247),
  ('receipts', 'origin', 'skip', NULL, 248),
  ('receipts', 'wms_status_raw', 'skip', NULL, 249),
  ('receipts', 'confirmed_at', 'skip', NULL, 250),
  ('receipts', 'created_by', 'skip', NULL, 251),
  ('receipts', 'created_at', 'skip', NULL, 252),
  ('receipts', 'updated_by', 'skip', NULL, 253),
  ('receipts', 'updated_at', 'skip', NULL, 254),
  ('receipts', 'transport_state', 'enumcode:enum_transport_state', 'audit.field.transport_state', 255),
  ('receipts', 'transport_sent_at', 'skip', NULL, 256),
  ('receipts', 'transport_error', 'skip', NULL, 257),
  ('sku', 'sku_id', 'skip', NULL, 258),
  ('sku', 'depositor_id', 'skip', NULL, 259),
  ('sku', 'item_code', 'text', 'audit.field.item_code', 260),
  ('sku', 'code', 'text', 'audit.field.code', 261),
  ('sku', 'name', 'text', 'audit.field.name', 262),
  ('sku', 'has_packaging', 'bool', 'audit.field.has_packaging', 263),
  ('sku', 'barcode', 'text', 'audit.field.barcode', 264),
  ('sku', 'uom_id', 'enum:enum_uom', 'audit.field.uom_id', 265),
  ('sku', 'order_unit_id', 'ref:packaging', 'audit.field.order_unit_id', 266),
  ('sku', 'length', 'text', 'audit.field.length', 267),
  ('sku', 'width', 'text', 'audit.field.width', 268),
  ('sku', 'height', 'text', 'audit.field.height', 269),
  ('sku', 'volume', 'text', 'audit.field.volume', 270),
  ('sku', 'weight_gross', 'text', 'audit.field.weight_gross', 271),
  ('sku', 'manufacturer', 'text', 'audit.field.manufacturer', 272),
  ('sku', 'country_origin', 'text', 'audit.field.country_origin', 273),
  ('sku', 'brand', 'text', 'audit.field.brand', 274),
  ('sku', 'is_fragile', 'bool', 'audit.field.is_fragile', 275),
  ('sku', 'is_serial_tracked', 'bool', 'audit.field.is_serial_tracked', 276),
  ('sku', 'batch_tracking', 'bool', 'audit.field.batch_tracking', 277),
  ('sku', 'expiry_tracking', 'bool', 'audit.field.expiry_tracking', 278),
  ('sku', 'item_full_name', 'text', 'audit.field.item_full_name', 279),
  ('sku', 'weight_net', 'text', 'audit.field.weight_net', 280),
  ('sku', 'max_storage_temp', 'text', 'audit.field.max_storage_temp', 281),
  ('sku', 'min_storage_temp', 'text', 'audit.field.min_storage_temp', 282),
  ('sku', 'max_humidity', 'text', 'audit.field.max_humidity', 283),
  ('sku', 'min_humidity', 'text', 'audit.field.min_humidity', 284),
  ('sku', 'min_days_for_receipt', 'text', 'audit.field.min_days_for_receipt', 285),
  ('sku', 'min_days_for_reservation', 'text', 'audit.field.min_days_for_reservation', 286),
  ('sku', 'shelf_life_days', 'text', 'audit.field.shelf_life_days', 287),
  ('sku', 'color', 'text', 'audit.field.color', 288),
  ('sku', 'size', 'text', 'audit.field.size', 289),
  ('sku', 'abc_class', 'text', 'audit.field.abc_class', 290),
  ('sku', 'xyz_class', 'text', 'audit.field.xyz_class', 291),
  ('sku', 'guid', 'skip', NULL, 292),
  ('sku', 'hs_code', 'text', 'audit.field.hs_code', 293),
  ('sku', 'enstru_code', 'text', 'audit.field.enstru_code', 294),
  ('sku', 'cost', 'text', 'audit.field.cost', 295),
  ('sku', 'comment', 'text', 'audit.field.comment', 296),
  ('sku', 'is_kit', 'bool', 'audit.field.is_kit', 297),
  ('sku', 'is_marking_required', 'bool', 'audit.field.is_marking_required', 298),
  ('sku', 'is_excise', 'bool', 'audit.field.is_excise', 299),
  ('sku', 'is_controlled', 'bool', 'audit.field.is_controlled', 300),
  ('sku', 'is_wms_active', 'bool', 'audit.field.is_wms_active', 301),
  ('sku', 'created_by', 'skip', NULL, 302),
  ('sku', 'created_at', 'skip', NULL, 303),
  ('sku', 'updated_by', 'skip', NULL, 304),
  ('sku', 'updated_at', 'skip', NULL, 305),
  ('sku', 'custom_attr1', 'text', 'audit.field.custom_attr1', 306),
  ('sku', 'custom_attr2', 'text', 'audit.field.custom_attr2', 307),
  ('sku', 'custom_attr3', 'text', 'audit.field.custom_attr3', 308),
  ('sku', 'custom_attr4', 'text', 'audit.field.custom_attr4', 309),
  ('sku', 'custom_attr5', 'text', 'audit.field.custom_attr5', 310),
  ('sku', 'custom_attr6', 'text', 'audit.field.custom_attr6', 311),
  ('sku', 'custom_attr7', 'text', 'audit.field.custom_attr7', 312),
  ('sku', 'custom_attr8', 'text', 'audit.field.custom_attr8', 313),
  ('sku', 'custom_attr9', 'text', 'audit.field.custom_attr9', 314),
  ('sku', 'custom_attr10', 'text', 'audit.field.custom_attr10', 315),
  ('sku', 'hazard_class_id', 'enum:enum_hazard_class', 'audit.field.hazard_class_id', 316),
  ('sku', 'hazard_group_id', 'enum:enum_hazard_group', 'audit.field.hazard_group_id', 317),
  ('sku', 'currency_id', 'enum:enum_currency', 'audit.field.currency_id', 318),
  ('sku', 'status_id', 'enum:enum_sku_status', 'audit.field.sku_status', 319),
  ('sku', 'synced_at', 'skip', NULL, 320),
  ('sku', 'wms_version', 'skip', NULL, 321),
  ('sku', 'is_in_wms', 'skip', NULL, 322),
  ('sku', 'is_active', 'bool', 'audit.field.is_active', 323),
  ('sku', 'item_group', 'text', 'audit.field.item_group', 324),
  ('sku', 'item_type', 'text', 'audit.field.item_type', 325),
  ('supplier', 'supplier_id', 'skip', NULL, 326),
  ('supplier', 'depositor_id', 'skip', NULL, 327),
  ('supplier', 'code', 'text', 'audit.field.code', 328),
  ('supplier', 'name', 'text', 'audit.field.name', 329),
  ('supplier', 'bin', 'text', 'audit.field.bin', 330),
  ('supplier', 'legal_address', 'text', 'audit.field.legal_address', 331),
  ('supplier', 'contact_name', 'text', 'audit.field.contact_name', 332),
  ('supplier', 'contact_phone', 'text', 'audit.field.contact_phone', 333),
  ('supplier', 'contact_email', 'text', 'audit.field.contact_email', 334),
  ('supplier', 'is_active', 'bool', 'audit.field.is_active', 335),
  ('transfer_details', 'line_id', 'skip', NULL, 336),
  ('transfer_details', 'doc_id', 'skip', NULL, 337),
  ('transfer_details', 'line_number', 'skip', NULL, 338),
  ('transfer_details', 'sku_id', 'ref:sku', 'audit.field.sku_id', 339),
  ('transfer_details', 'qty', 'text', 'audit.field.qty', 340),
  ('transfer_details', 'qty_wms', 'text', 'audit.field.qty_wms', 341),
  ('transfer_details', 'batch_number', 'text', 'audit.field.batch_number', 342),
  ('transfer_details', 'series_number', 'text', 'audit.field.series_number', 343),
  ('transfer_details', 'expiry_date', 'text', 'audit.field.expiry_date', 344),
  ('transfer_details', 'line_comment', 'text', 'audit.field.line_comment', 345),
  ('transfer_details', 'custom_field_1', 'text', 'audit.field.custom_field_1', 346),
  ('transfer_details', 'custom_field_2', 'text', 'audit.field.custom_field_2', 347),
  ('transfer_details', 'custom_field_3', 'text', 'audit.field.custom_field_3', 348),
  ('transfer_details', 'custom_field_4', 'text', 'audit.field.custom_field_4', 349),
  ('transfer_details', 'custom_field_5', 'text', 'audit.field.custom_field_5', 350),
  ('transfer_details', 'custom_field_6', 'text', 'audit.field.custom_field_6', 351),
  ('transfer_details', 'custom_field_7', 'text', 'audit.field.custom_field_7', 352),
  ('transfer_details', 'custom_field_8', 'text', 'audit.field.custom_field_8', 353),
  ('transfer_details', 'custom_field_9', 'text', 'audit.field.custom_field_9', 354),
  ('transfer_details', 'custom_field_10', 'text', 'audit.field.custom_field_10', 355),
  ('transfer_details', 'quality_id', 'enum:enum_quality', 'audit.field.quality_id', 356),
  ('transfers', 'doc_id', 'skip', NULL, 357),
  ('transfers', 'document_no', 'skip', NULL, 358),
  ('transfers', 'depositor_id', 'skip', NULL, 359),
  ('transfers', 'doc_number', 'text', 'audit.field.doc_number', 360),
  ('transfers', 'erp_number', 'text', 'audit.field.erp_number', 361),
  ('transfers', 'author_id', 'skip', NULL, 362),
  ('transfers', 'status_id', 'enum:enum_status', 'audit.field.status_id', 363),
  ('transfers', 'doc_date', 'text', 'audit.field.doc_date', 364),
  ('transfers', 'warehouse_id', 'ref:warehouse', 'audit.field.warehouse_id', 365),
  ('transfers', 'trip', 'text', 'audit.field.trip', 366),
  ('transfers', 'comment', 'text', 'audit.field.comment', 367),
  ('transfers', 'from_warehouse_id', 'ref:warehouse', 'audit.field.from_warehouse_id', 368),
  ('transfers', 'to_warehouse_id', 'ref:warehouse', 'audit.field.to_warehouse_id', 369),
  ('transfers', 'ship_date', 'text', 'audit.field.ship_date', 370),
  ('transfers', 'received_date', 'text', 'audit.field.received_date', 371),
  ('transfers', 'line_count', 'skip', NULL, 372),
  ('transfers', 'total_pieces', 'text', 'audit.field.total_pieces', 373),
  ('transfers', 'cargo_places', 'text', 'audit.field.cargo_places', 374),
  ('transfers', 'pallet_count', 'text', 'audit.field.pallet_count', 375),
  ('transfers', 'weight_kg', 'text', 'audit.field.weight_kg', 376),
  ('transfers', 'volume_m3', 'text', 'audit.field.volume_m3', 377),
  ('transfers', 'carrier', 'text', 'audit.field.carrier', 378),
  ('transfers', 'transport_type', 'enumcode:enum_transport_type', 'audit.field.transport_type', 379),
  ('transfers', 'transport_brand', 'text', 'audit.field.transport_brand', 380),
  ('transfers', 'car_number', 'text', 'audit.field.car_number', 381),
  ('transfers', 'trailer_number', 'text', 'audit.field.trailer_number', 382),
  ('transfers', 'tech_passport_number', 'text', 'audit.field.tech_passport_number', 383),
  ('transfers', 'container_number', 'text', 'audit.field.container_number', 384),
  ('transfers', 'seal_number', 'text', 'audit.field.seal_number', 385),
  ('transfers', 'driver_name', 'text', 'audit.field.driver_name', 386),
  ('transfers', 'driver_phone', 'text', 'audit.field.driver_phone', 387),
  ('transfers', 'driver_id_number', 'text', 'audit.field.driver_id_number', 388),
  ('transfers', 'driver_inn', 'text', 'audit.field.driver_inn', 389),
  ('transfers', 'custom_field_1', 'text', 'audit.field.custom_field_1', 390),
  ('transfers', 'custom_field_2', 'text', 'audit.field.custom_field_2', 391),
  ('transfers', 'custom_field_3', 'text', 'audit.field.custom_field_3', 392),
  ('transfers', 'custom_field_4', 'text', 'audit.field.custom_field_4', 393),
  ('transfers', 'custom_field_5', 'text', 'audit.field.custom_field_5', 394),
  ('transfers', 'custom_field_6', 'text', 'audit.field.custom_field_6', 395),
  ('transfers', 'custom_field_7', 'text', 'audit.field.custom_field_7', 396),
  ('transfers', 'custom_field_8', 'text', 'audit.field.custom_field_8', 397),
  ('transfers', 'custom_field_9', 'text', 'audit.field.custom_field_9', 398),
  ('transfers', 'custom_field_10', 'text', 'audit.field.custom_field_10', 399),
  ('transfers', 'origin', 'skip', NULL, 400),
  ('transfers', 'wms_status_raw', 'skip', NULL, 401),
  ('transfers', 'confirmed_at', 'skip', NULL, 402),
  ('transfers', 'created_by', 'skip', NULL, 403),
  ('transfers', 'created_at', 'skip', NULL, 404),
  ('transfers', 'updated_by', 'skip', NULL, 405),
  ('transfers', 'updated_at', 'skip', NULL, 406),
  ('transfers', 'transport_state', 'enumcode:enum_transport_state', 'audit.field.transport_state', 407),
  ('transfers', 'transport_sent_at', 'skip', NULL, 408),
  ('transfers', 'transport_error', 'skip', NULL, 409),
  ('ui_text', 'text_key', 'skip', NULL, 410),
  ('ui_text', 'locale_id', 'skip', NULL, 411),
  ('ui_text', 'value', 'text', 'audit.field.text_value', 412),
  ('user_depositor_access', 'id', 'skip', NULL, 413),
  ('user_depositor_access', 'user_id', 'skip', NULL, 414),
  ('user_depositor_access', 'depositor_id', 'ref:depositor', 'audit.field.depositor_id', 415),
  ('user_settings', 'user_id', 'skip', NULL, 416),
  ('user_settings', 'hide_billing_tab', 'bool', 'audit.field.hide_billing_tab', 417),
  ('user_settings', 'hide_services_tab', 'bool', 'audit.field.hide_services_tab', 418),
  ('user_settings', 'can_create_receipts', 'bool', 'audit.field.can_create_receipts', 419),
  ('user_settings', 'can_create_orders', 'bool', 'audit.field.can_create_orders', 420),
  ('user_settings', 'can_create_transfers', 'bool', 'audit.field.can_create_transfers', 421),
  ('user_settings', 'can_upload_sku_excel', 'bool', 'audit.field.can_upload_sku_excel', 422),
  ('user_settings', 'can_upload_packaging_excel', 'bool', 'audit.field.can_upload_packaging_excel', 423),
  ('user_settings', 'can_edit_transport', 'bool', 'audit.field.can_edit_transport', 424),
  ('user_warehouse_access', 'id', 'skip', NULL, 425),
  ('user_warehouse_access', 'user_id', 'skip', NULL, 426),
  ('user_warehouse_access', 'warehouse_id', 'ref:warehouse', 'audit.field.warehouse_id', 427),
  ('warehouse', 'warehouse_id', 'skip', NULL, 428),
  ('warehouse', 'code', 'text', 'audit.field.code', 429),
  ('warehouse', 'name', 'text', 'audit.field.name', 430),
  ('warehouse', 'address', 'text', 'audit.field.address', 431),
  ('warehouse', 'responsible', 'text', 'audit.field.responsible', 432),
  ('warehouse', 'phone', 'text', 'audit.field.phone', 433),
  ('warehouse', 'email', 'text', 'audit.field.email', 434),
  ('warehouse', 'is_active', 'bool', 'audit.field.is_active', 435),
  ('warehouse', 'area_m2', 'text', 'audit.field.area_m2', 436),
  ('warehouse', 'work_hours', 'text', 'audit.field.work_hours', 437)
;

-- ---------- Триггер -------------------------------------------------------------

CREATE FUNCTION audit_row() RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_actor  text := NULLIF(current_setting('app.actor', true), '');
  o        jsonb;
  n        jsonb;
  r        jsonb;
  kinds    jsonb;
  ch       jsonb := '{}';
  k        text;
  kd       text;
  ov       jsonb;
  nv       jsonb;
  v_type   text;
  v_id     text;
  v_label  text;
  v_dep    uuid;
  v_part   text;
  v_action text := lower(TG_OP);
  v_code   text;
BEGIN
  -- Не действие человека в портале (миграция, psql, демо-данные) — не пишем.
  IF v_actor IS NULL THEN
    RETURN NULL;
  END IF;
  IF TG_OP <> 'INSERT' THEN o := to_jsonb(OLD); END IF;
  IF TG_OP <> 'DELETE' THEN n := to_jsonb(NEW); END IF;
  r := COALESCE(n, o);
  IF v_action = 'insert' THEN v_action := 'create'; END IF;

  SELECT jsonb_object_agg(column_name, kind) INTO kinds
    FROM audit_field WHERE source_table = TG_TABLE_NAME;

  FOR k IN SELECT jsonb_object_keys(r) LOOP
    kd := COALESCE(kinds ->> k, 'text');
    CONTINUE WHEN kd = 'skip';
    ov := NULLIF(o -> k, 'null'::jsonb);
    nv := NULLIF(n -> k, 'null'::jsonb);
    CONTINUE WHEN ov IS NOT DISTINCT FROM nv;
    IF kd = 'secret' THEN
      -- что пароль сменили — видно, сам он (даже хэш) в журнал не идёт
      ov := CASE WHEN ov IS NULL THEN NULL ELSE '"***"'::jsonb END;
      nv := CASE WHEN nv IS NULL THEN NULL ELSE '"***"'::jsonb END;
    END IF;
    ch := ch || jsonb_build_object(k, jsonb_strip_nulls(jsonb_build_object('o', ov, 'n', nv)));
  END LOOP;

  IF TG_OP = 'UPDATE' AND ch = '{}'::jsonb THEN
    RETURN NULL;   -- сохранили без изменений
  END IF;

  -- Чей это объект: строка документа — документа, адрес — грузополучателя,
  -- доступы и права — пользователя, настройки клиента — поклажедателя.
  CASE TG_TABLE_NAME
    WHEN 'receipts', 'orders', 'transfers' THEN
      v_type := TG_TABLE_NAME;
      v_id := r ->> 'doc_id';
      v_label := r ->> 'document_no';
      v_dep := (r ->> 'depositor_id')::uuid;
      IF TG_OP = 'UPDATE' AND ch ? 'status_id' THEN v_action := 'status'; END IF;
    WHEN 'receipt_details', 'order_details', 'transfer_details' THEN
      v_type := CASE TG_TABLE_NAME WHEN 'receipt_details' THEN 'receipts'
                                   WHEN 'order_details' THEN 'orders' ELSE 'transfers' END;
      v_id := r ->> 'doc_id';
      EXECUTE format('SELECT document_no, depositor_id FROM %I WHERE doc_id = $1', v_type)
         INTO v_label, v_dep USING (r ->> 'doc_id')::uuid;
      -- Документ удаляют вместе со строками: удаление записано у шапки.
      IF v_dep IS NULL THEN RETURN NULL; END IF;
      v_part := 'line:' || (r ->> 'line_number');
    WHEN 'sku' THEN
      v_type := 'sku'; v_id := r ->> 'sku_id'; v_label := r ->> 'code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'product_packaging' THEN
      v_type := 'packaging'; v_id := r ->> 'packaging_id'; v_label := r ->> 'packaging_code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'supplier', 'consignee' THEN
      v_type := TG_TABLE_NAME; v_id := r ->> (TG_TABLE_NAME || '_id'); v_label := r ->> 'code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'delivery_point' THEN
      v_type := 'consignee'; v_id := r ->> 'consignee_id';
      SELECT c.code, c.depositor_id INTO v_label, v_dep
        FROM consignee c WHERE c.consignee_id = (r ->> 'consignee_id')::uuid;
      IF v_dep IS NULL THEN RETURN NULL; END IF;
      v_part := 'delivery_point:' || COALESCE(r ->> 'address', '');
    WHEN 'warehouse' THEN
      v_type := 'warehouse'; v_id := r ->> 'warehouse_id'; v_label := r ->> 'name';
    WHEN 'depositor' THEN
      v_type := 'depositor'; v_id := r ->> 'depositor_id'; v_label := r ->> 'code';
      v_dep := (r ->> 'depositor_id')::uuid;
    WHEN 'operator' THEN
      v_type := 'operator'; v_id := r ->> 'operator_id'; v_label := r ->> 'code';
    WHEN 'app_user' THEN
      v_type := 'app_user'; v_id := r ->> 'user_id'; v_label := r ->> 'login';
    WHEN 'user_settings', 'user_depositor_access', 'user_warehouse_access' THEN
      v_type := 'app_user'; v_id := r ->> 'user_id';
      SELECT login INTO v_label FROM app_user WHERE user_id = (r ->> 'user_id')::uuid;
      IF v_label IS NULL THEN RETURN NULL; END IF;   -- пользователя удаляют целиком
      v_part := CASE TG_TABLE_NAME WHEN 'user_settings' THEN 'settings'
                                   WHEN 'user_depositor_access' THEN 'depositors' ELSE 'warehouses' END;
    WHEN 'depositor_settings', 'depositor_table_column', 'depositor_field_label' THEN
      v_type := 'depositor'; v_id := r ->> 'depositor_id';
      v_dep := (r ->> 'depositor_id')::uuid;
      SELECT code INTO v_label FROM depositor WHERE depositor_id = v_dep;
      IF v_label IS NULL THEN RETURN NULL; END IF;
      v_part := CASE TG_TABLE_NAME
                  WHEN 'depositor_settings' THEN 'sections'
                  WHEN 'depositor_table_column' THEN 'column:' || (r ->> 'table_key') || '.' || (r ->> 'column_id')
                  ELSE 'label:' || COALESCE((SELECT code FROM enum_object_type
                                              WHERE id = (r ->> 'object_type_id')::smallint), '?')
                       || '.' || (r ->> 'field_id') END;
    WHEN 'dictionary_locale' THEN
      v_type := 'locale'; v_id := r ->> 'code'; v_label := r ->> 'name';
    WHEN 'ui_text' THEN
      v_type := 'ui_text'; v_id := r ->> 'text_key'; v_label := r ->> 'text_key';
      v_part := 'locale:' || COALESCE((SELECT code FROM dictionary_locale
                                        WHERE locale_id = (r ->> 'locale_id')::smallint), '?');
    WHEN 'enum_text' THEN
      v_type := 'enum'; v_id := (r ->> 'enum_key') || ':' || (r ->> 'value_id');
      SELECT code_column INTO v_code FROM enum_registry WHERE enum_key = r ->> 'enum_key';
      IF v_code IS NOT NULL THEN
        EXECUTE format('SELECT %I::text FROM %I WHERE id::text = $1', v_code, r ->> 'enum_key')
           INTO v_label USING r ->> 'value_id';
      END IF;
      v_part := 'locale:' || COALESCE((SELECT code FROM dictionary_locale
                                        WHERE locale_id = (r ->> 'locale_id')::smallint), '?');
    ELSE
      -- значения перечислений (таблицы enum_* из enum_registry)
      SELECT code_column INTO v_code FROM enum_registry WHERE enum_key = TG_TABLE_NAME;
      v_type := 'enum'; v_id := TG_TABLE_NAME || ':' || (r ->> 'id');
      v_label := r ->> COALESCE(v_code, 'code');
  END CASE;

  INSERT INTO audit_log (actor, depositor_id, object_type, object_id, object_label,
                         source_table, action, part, changes)
  VALUES (v_actor, v_dep, v_type, v_id, v_label, TG_TABLE_NAME, v_action, v_part, ch);
  RETURN NULL;
END
$$;
COMMENT ON FUNCTION audit_row() IS 'триггер журнала действий (V116)';

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'receipts', 'orders', 'transfers', 'receipt_details', 'order_details', 'transfer_details',
    'sku', 'product_packaging', 'supplier', 'consignee', 'delivery_point',
    'warehouse', 'depositor', 'operator',
    'app_user', 'user_settings', 'user_depositor_access', 'user_warehouse_access',
    'depositor_settings', 'depositor_table_column', 'depositor_field_label',
    'dictionary_locale', 'ui_text', 'enum_text']
  LOOP
    EXECUTE format('CREATE TRIGGER audit AFTER INSERT OR UPDATE OR DELETE ON %I '
                   'FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
  END LOOP;
  FOR t IN SELECT enum_key FROM enum_registry LOOP
    EXECUTE format('CREATE TRIGGER audit AFTER INSERT OR UPDATE OR DELETE ON %I '
                   'FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
  END LOOP;
END $$;

-- Колонки таблиц перечислений: id и код — служебные, остальное — как есть.
INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order)
SELECT c.table_name, c.column_name,
       CASE WHEN c.column_name IN ('id', 'sort_order') THEN 'skip'
            WHEN c.data_type = 'boolean' THEN 'bool' ELSE 'text' END,
       CASE WHEN c.column_name IN ('id', 'sort_order') THEN NULL
            WHEN c.column_name = 'code' THEN 'audit.field.code'
            WHEN c.column_name = 'name' THEN 'audit.field.name'
            WHEN c.column_name = 'is_active' THEN 'audit.field.is_active'
            WHEN c.column_name = 'symbol' THEN 'audit.field.symbol' END,
       c.ordinal_position
  FROM information_schema.columns c
  JOIN enum_registry e ON e.enum_key = c.table_name
 WHERE c.table_schema = 'dbo'
ON CONFLICT DO NOTHING;

-- ---------- Чтение ---------------------------------------------------------------

-- Значение словами на языке читающего (app.locale).
CREATE FUNCTION audit_value(p_kind text, p_value jsonb) RETURNS text
LANGUAGE plpgsql
STABLE
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v   text := p_value #>> '{}';
  t   text;
  res text;
BEGIN
  IF p_value IS NULL OR v IS NULL THEN
    RETURN NULL;
  END IF;
  CASE
    WHEN p_kind = 'secret' THEN
      RETURN '•••';
    WHEN p_kind = 'bool' THEN
      SELECT ut.value INTO res
        FROM ui_text ut JOIN dictionary_locale l ON l.locale_id = ut.locale_id
       WHERE ut.text_key = CASE WHEN v = 'true' THEN 'common.value.yes' ELSE 'common.value.no' END
       ORDER BY (l.code = NULLIF(current_setting('app.locale', true), '')) DESC, l.is_default DESC
       LIMIT 1;
    WHEN p_kind LIKE 'enum:%' THEN
      t := substr(p_kind, 6);
      EXECUTE format('SELECT enum_name(%L, id::text, name) FROM %I WHERE id::text = $1', t, t)
         INTO res USING v;
    WHEN p_kind LIKE 'enumcode:%' THEN
      t := substr(p_kind, 10);
      EXECUTE format('SELECT enum_name(%L, id::text, name) FROM %I WHERE code = $1', t, t)
         INTO res USING v;
    WHEN p_kind = 'ref:warehouse' THEN
      SELECT name INTO res FROM warehouse WHERE warehouse_id = v;
    WHEN p_kind = 'ref:supplier' THEN
      SELECT name INTO res FROM supplier WHERE supplier_id::text = v;
    WHEN p_kind = 'ref:consignee' THEN
      SELECT name INTO res FROM consignee WHERE consignee_id::text = v;
    WHEN p_kind = 'ref:delivery_point' THEN
      SELECT address INTO res FROM delivery_point WHERE address_id::text = v;
    WHEN p_kind = 'ref:sku' THEN
      SELECT code || ' — ' || name INTO res FROM sku WHERE sku_id::text = v;
    WHEN p_kind = 'ref:packaging' THEN
      SELECT packaging_code INTO res FROM product_packaging WHERE packaging_id::text = v;
    WHEN p_kind = 'ref:depositor' THEN
      SELECT org_name INTO res FROM depositor WHERE depositor_id::text = v;
    WHEN p_kind = 'ref:locale' THEN
      SELECT name INTO res FROM dictionary_locale WHERE locale_id::text = v;
    WHEN p_kind = 'ref:operator' THEN
      SELECT name INTO res FROM operator WHERE operator_id::text = v;
    WHEN jsonb_typeof(p_value) = 'number' AND v LIKE '%.%' THEN
      -- 12.000 → 12, 12.500 → 12.5
      res := rtrim(rtrim(v, '0'), '.');
    ELSE
      res := v;
  END CASE;
  -- Удалённую запись словами не назвать — показываем, что было записано.
  RETURN COALESCE(res, v);
END
$$;
GRANT EXECUTE ON FUNCTION audit_value(text, jsonb) TO portal_app;

-- История одной записи: события по порядку, у каждого — изменённые поля.
--
-- События одной транзакции по одной части (шапка или строка N) сводятся
-- в одно: было — значение до первого события, стало — после последнего.
-- Так «удалить все строки и вставить заново» при сохранении состава
-- показывает только то, что действительно поменялось. Создание самой
-- записи (part пустой) — без полей: это не изменения, а начальные
-- значения, их видно в карточке.
--
-- Читается от имени вызывающего: клиент видит историю только своих
-- записей (RLS на audit_log).
CREATE FUNCTION audit_history(p_object_type text, p_object_id text)
RETURNS TABLE (event_id bigint, at timestamptz, actor text, actor_name text, action text,
               part text, field text, field_key text, old_text text, new_text text)
LANGUAGE sql
STABLE
SET search_path = dbo, pg_temp
AS $$
  WITH ev AS (
    SELECT a.* FROM audit_log a
     WHERE a.object_type = p_object_type AND a.object_id = p_object_id
  ),
  grp AS (
    SELECT e.tx, e.part,
           max(e.audit_id) AS event_id,
           max(e.at) AS at,
           (array_agg(e.actor ORDER BY e.audit_id DESC))[1] AS actor,
           (array_agg(e.source_table ORDER BY e.audit_id))[1] AS source_table,
           (array_agg(e.action ORDER BY e.audit_id))[1] AS first_action,
           (array_agg(e.action ORDER BY e.audit_id DESC))[1] AS last_action
      FROM ev e
     GROUP BY e.tx, e.part
  ),
  fld AS (
    SELECT e.tx, e.part, c.key,
           (array_agg(c.value -> 'o' ORDER BY e.audit_id))[1] AS o,
           (array_agg(c.value -> 'n' ORDER BY e.audit_id DESC))[1] AS n
      FROM ev e
      CROSS JOIN LATERAL jsonb_each(e.changes) c
     GROUP BY e.tx, e.part, c.key
  ),
  net AS (
    SELECT g.*,
           CASE WHEN g.first_action = 'create' AND g.last_action = 'delete' THEN NULL
                WHEN g.first_action = 'create' THEN 'create'
                WHEN g.last_action = 'delete' THEN 'delete'
                WHEN g.part IS NULL AND EXISTS (
                       SELECT 1 FROM fld f WHERE f.tx = g.tx AND f.part IS NULL AND f.key = 'status_id'
                          AND (f.o IS DISTINCT FROM f.n)) THEN 'status'
                ELSE 'update' END AS action
      FROM grp g
  ),
  rows AS (
    SELECT n.event_id, n.at, n.actor, n.action, n.part, f.key AS field,
           COALESCE(af.text_key, 'audit.field.' || f.key) AS field_key,
           audit_value(af.kind, CASE WHEN n.action = 'create' THEN NULL ELSE f.o END) AS old_text,
           audit_value(af.kind, CASE WHEN n.action = 'delete' THEN NULL ELSE f.n END) AS new_text,
           COALESCE(af.sort_order, 0) AS sort_order
      FROM net n
      JOIN fld f ON f.tx = n.tx AND f.part IS NOT DISTINCT FROM n.part
      LEFT JOIN audit_field af ON af.source_table = n.source_table AND af.column_name = f.key
     WHERE n.action IS NOT NULL
       AND NOT (n.action = 'create' AND n.part IS NULL)
       AND (n.action IN ('create', 'delete')
            OR (CASE WHEN n.action = 'create' THEN NULL ELSE f.o END)
               IS DISTINCT FROM (CASE WHEN n.action = 'delete' THEN NULL ELSE f.n END))
  ),
  heads AS (
    -- событие без полей: создание записи, или сводка дала «ничего не изменилось»
    SELECT n.event_id, n.at, n.actor, n.action, n.part
      FROM net n
     WHERE n.action IS NOT NULL
       AND (NOT EXISTS (SELECT 1 FROM rows r WHERE r.event_id = n.event_id))
       AND (n.action IN ('create', 'delete'))
  )
  SELECT x.event_id, x.at, x.actor, COALESCE(u.full_name, x.actor), x.action, x.part,
         x.field, x.field_key, x.old_text, x.new_text
    FROM (SELECT event_id, at, actor, action, part, field, field_key, old_text, new_text, sort_order FROM rows
          UNION ALL
          SELECT event_id, at, actor, action, part, NULL, NULL, NULL, NULL, 0 FROM heads) x
    LEFT JOIN app_user u ON u.login = x.actor
   ORDER BY x.event_id DESC, x.sort_order
$$;
GRANT EXECUTE ON FUNCTION audit_history(text, text) TO portal_app;
