SET search_path = dbo;

-- =====================================================================
-- V109. Удалить тексты, на которые никто не ссылается (11.11.2026)
-- =====================================================================
-- Чистка прототипа (шаги 1–6) убрала экраны и надписи демо, а их ключи
-- остались: в экране переводов они выглядят действующими подписями,
-- и их переводили бы дальше в никуда. Заодно ушёл обратный словарь
-- BY_RU в i18n-fallback.js: TRU (перевод по русскому тексту) больше
-- никто не вызывает, и ключи, которые держал только он (их вернула
-- V93), тоже никому не нужны.
--
-- Как собран список (102 ключей). Ключ ищется ЦЕЛЫМ словом —
-- portal.text.edit не считается живым из-за portal.text.edit2 (V56):
--   * в разметке и JS портала, кроме запасного словаря;
--   * в Java — основном коде и тестах;
--   * в данных базы: все текстовые колонки dbo, кроме самих текстов
--     (справочник колонок, перечисления, вкладки…);
--   * в телах функций базы.
-- Ключи, которые код собирает из частей (col.*, excel.*, enums.*,
-- error.*), проверены по частям; живыми оказались все, кроме
-- col.stockBatch.blockReason (причина блокировки убрана в V92).
-- tools/find_unused_texts.py даёт больше — 185, но лишнее в его
-- списке — именно такие семейства из частей.
--
-- Сначала переводы, потом ключи: на ui_text_key стоит внешняя ссылка.

CREATE TEMP TABLE _dead_keys (text_key varchar PRIMARY KEY);
INSERT INTO _dead_keys (text_key) VALUES
  -- admin (1)
  ('admin.title.report_settings'),
  -- auth (1)
  ('auth.hint.manual_sign_in_disabled_in_demo'),
  -- billing (1)
  ('billing.title.billing'),
  -- col (1)
  ('col.stockBatch.blockReason'),
  -- common (58)
  ('common.label.order_number'),
  ('common.label.ship_date_from'),
  ('common.label.ship_date_to'),
  ('common.message.confirm_delete_named'),
  ('common.message.document_open_failed'),
  ('common.option.delayed'),
  ('common.option.picking'),
  ('common.option.reserved'),
  ('common.option.shipped'),
  ('common.placeholder.eg_consignee'),
  ('common.placeholder.eg_sku_code'),
  ('common.placeholder.eg_sku_name'),
  ('common.placeholder.eg_supplier'),
  ('common.text.amount_kzt'),
  ('common.text.assigning_warehouses_and_clients_t'),
  ('common.text.batch_expiry'),
  ('common.text.cross_docking'),
  ('common.text.custody_storage_with_expiry_tracki'),
  ('common.text.daily2'),
  ('common.text.date'),
  ('common.text.defect_pcs'),
  ('common.text.document_contents'),
  ('common.text.expected_pcs'),
  ('common.text.export_to_excel'),
  ('common.text.format'),
  ('common.text.good'),
  ('common.text.good2'),
  ('common.text.goods_receiving'),
  ('common.text.kitting'),
  ('common.text.kitting_set_assembly'),
  ('common.text.labeling_marking'),
  ('common.text.line_status'),
  ('common.text.loading_unloading'),
  ('common.text.operation'),
  ('common.text.operation_count'),
  ('common.text.order_picking_amp_packing'),
  ('common.text.ordered_pcs'),
  ('common.text.per_operation6'),
  ('common.text.period_from2'),
  ('common.text.period_to2'),
  ('common.text.picked_pcs'),
  ('common.text.qty_pcs'),
  ('common.text.received_pcs'),
  ('common.text.receiving'),
  ('common.text.report_type'),
  ('common.text.requested'),
  ('common.text.services_rendered'),
  ('common.text.shipments'),
  ('common.text.span_class_badge_err_span_class_d'),
  ('common.text.span_class_badge_err_span_class_d2'),
  ('common.text.span_class_badge_ok_span_class_do'),
  ('common.text.span_class_badge_warn_span_class'),
  ('common.text.stock_on_hand'),
  ('common.text.storage2'),
  ('common.text.storage_location'),
  ('common.text.total_for_period'),
  ('common.text.transport'),
  ('common.text.transport_amp_delivery'),
  -- portal (6)
  ('portal.text.disable'),
  ('portal.text.disabled'),
  ('portal.text.enable'),
  ('portal.text.in_use'),
  ('portal.title.document_type'),
  ('portal.title.order_type'),
  -- receipts (5)
  ('receipts.label.date_from'),
  ('receipts.label.date_to'),
  ('receipts.option.awaiting_receiving'),
  ('receipts.option.discrepancy'),
  ('receipts.option.in_progress'),
  -- references (15)
  ('references.button.columns'),
  ('references.button.expected_document_types'),
  ('references.label.code_sku'),
  ('references.label.item_code'),
  ('references.label.item_name'),
  ('references.label.item_type'),
  ('references.option.all'),
  ('references.option.components'),
  ('references.option.construction_materials'),
  ('references.option.consumables'),
  ('references.option.dairy_products'),
  ('references.option.finished_goods'),
  ('references.option.meat_products'),
  ('references.option.raw_materials'),
  ('references.option.spare_parts'),
  -- reports (3)
  ('reports.button.create_new'),
  ('reports.button.download_excel_template'),
  ('reports.title.reports'),
  -- sku (2)
  ('sku.message.item'),
  ('sku.message.transport_details_are_not_availabl'),
  -- stock (8)
  ('stock.label.code_sku'),
  ('stock.option.components'),
  ('stock.option.construction_materials'),
  ('stock.option.dairy_products'),
  ('stock.option.finished_goods'),
  ('stock.option.meat_products'),
  ('stock.option.raw_materials'),
  ('stock.option.ready_made_kits'),
  -- transfers (1)
  ('transfers.label.document_number');

-- Справочник колонок ссылается на ключи внешней ссылкой; если бы ключ
-- из списка там был, удаление упало бы на середине — проверяем заранее
-- и говорим, какой.
DO $$
DECLARE k text;
BEGIN
  SELECT d.text_key INTO k FROM _dead_keys d
   WHERE EXISTS (SELECT 1 FROM table_column c WHERE c.text_key = d.text_key OR c.group_key = d.text_key)
   LIMIT 1;
  IF k IS NOT NULL THEN
    RAISE EXCEPTION 'ключ % нужен справочнику колонок — удалять нельзя', k;
  END IF;
END $$;

DELETE FROM ui_text t USING _dead_keys d WHERE t.text_key = d.text_key;
DELETE FROM ui_text_key k USING _dead_keys d WHERE k.text_key = d.text_key;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM ui_text_key k JOIN _dead_keys d USING (text_key)) THEN
    RAISE EXCEPTION 'не все ключи удалены';
  END IF;
END $$;

DROP TABLE _dead_keys;
