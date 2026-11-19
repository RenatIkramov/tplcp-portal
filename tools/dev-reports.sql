-- =====================================================================
-- Демо-каталог отчётов (V135) — для базы разработчика, не миграция.
--
--   psql -U postgres -d tplcp -f tools/dev-reports.sql
--
-- Можно запускать повторно: всё вставляется с ON CONFLICT DO NOTHING,
-- ничего не удаляется. Отдельным файлом от dev-data.sql: базе, где набор
-- уже лежит, каталог добавляется одной короткой командой. На чистой базе —
-- после dev-data.sql (нужны поклажедатели D-001 и D-002).
--
-- Коды отчётов и ключи параметров — ПРЕДВАРИТЕЛЬНЫЕ: настоящие даст
-- контракт ArtWMS (этап 8). Отчёты — из демо (js/demo/reports.js).
-- =====================================================================

BEGIN;
SET search_path = dbo;

INSERT INTO report (report_id, wms_code, name, place_id, sort_order) VALUES
  ('5e0a0001-0000-4000-8000-000000000001', 'RPT-STOCK',        'Stock balance',                    1, 1),
  ('5e0a0001-0000-4000-8000-000000000002', 'RPT-ORD',          'Orders report',                    1, 2),
  ('5e0a0001-0000-4000-8000-000000000003', 'RPT-RCPT',         'Receipts report',                  1, 3),
  ('5e0a0001-0000-4000-8000-000000000004', 'RPT-EXP',          'Expiry dates',                     1, 4),
  ('5e0a0001-0000-4000-8000-000000000005', 'RPT-MOVE',         'Goods movement for the period',    1, 5),
  ('5e0a0001-0000-4000-8000-000000000006', 'RPT-ORD',          'Orders today',                     1, 6),
  ('5e0a0001-0000-4000-8000-000000000007', 'RPT-SVC',          'Services rendered',                2, 1),
  ('5e0a0001-0000-4000-8000-000000000011', 'RPT-SKU-MOVE',     'Item movement history',            6, 1)
ON CONFLICT DO NOTHING;

INSERT INTO report_text (report_id, locale_id, name)
SELECT v.id::uuid, l.locale_id, v.name
  FROM (VALUES
    ('5e0a0001-0000-4000-8000-000000000001', 'Остатки товаров'),
    ('5e0a0001-0000-4000-8000-000000000002', 'Отчёт по заказам'),
    ('5e0a0001-0000-4000-8000-000000000003', 'Отчёт по поступлениям'),
    ('5e0a0001-0000-4000-8000-000000000004', 'Остатки сроков годности'),
    ('5e0a0001-0000-4000-8000-000000000005', 'Ведомость по движению товаров за период'),
    ('5e0a0001-0000-4000-8000-000000000006', 'Заказы за сегодня'),
    ('5e0a0001-0000-4000-8000-000000000007', 'Сводный отчёт по оказанным услугам'),
    ('5e0a0001-0000-4000-8000-000000000011', 'История движения товара')
  ) AS v(id, name)
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT DO NOTHING;

-- Параметры: код клиента (09) — у всех; код товара (11) — у отчёта
-- карточки товара (V139: отчётов в карточках документов больше нет). Отчёт «Заказы за сегодня» — тот же RPT-ORD, что
-- «Отчёт по заказам», но период скрыт и равен сегодняшнему дню.
INSERT INTO report_param (param_id, report_id, param_key, label, type_id, is_required,
                          use_current_date, is_hidden, sort_order)
SELECT v.pid::uuid, v.rid::uuid, v.key, v.label, v.type_id, v.req, v.today, v.hidden, v.ord
  FROM (VALUES
    ('5e0a0002-0000-4000-8000-000000000101', '5e0a0001-0000-4000-8000-000000000001', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000102', '5e0a0001-0000-4000-8000-000000000001', 'warehouse_id', 'Warehouse',      4, false, false, false, 2),
    ('5e0a0002-0000-4000-8000-000000000201', '5e0a0001-0000-4000-8000-000000000002', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000202', '5e0a0001-0000-4000-8000-000000000002', 'date_from',    'Period from',    1, true,  false, false, 2),
    ('5e0a0002-0000-4000-8000-000000000203', '5e0a0001-0000-4000-8000-000000000002', 'date_to',      'Period to',      1, true,  true,  false, 3),
    ('5e0a0002-0000-4000-8000-000000000301', '5e0a0001-0000-4000-8000-000000000003', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000302', '5e0a0001-0000-4000-8000-000000000003', 'date_from',    'Period from',    1, true,  false, false, 2),
    ('5e0a0002-0000-4000-8000-000000000303', '5e0a0001-0000-4000-8000-000000000003', 'date_to',      'Period to',      1, true,  true,  false, 3),
    ('5e0a0002-0000-4000-8000-000000000401', '5e0a0001-0000-4000-8000-000000000004', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000501', '5e0a0001-0000-4000-8000-000000000005', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000502', '5e0a0001-0000-4000-8000-000000000005', 'date_from',    'Period from',    1, true,  false, false, 2),
    ('5e0a0002-0000-4000-8000-000000000503', '5e0a0001-0000-4000-8000-000000000005', 'date_to',      'Period to',      1, true,  true,  false, 3),
    ('5e0a0002-0000-4000-8000-000000000601', '5e0a0001-0000-4000-8000-000000000006', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000602', '5e0a0001-0000-4000-8000-000000000006', 'date_from',    'Period from',    1, false, true,  true, 2),
    ('5e0a0002-0000-4000-8000-000000000603', '5e0a0001-0000-4000-8000-000000000006', 'date_to',      'Period to',      1, false, true,  true, 3),
    ('5e0a0002-0000-4000-8000-000000000701', '5e0a0001-0000-4000-8000-000000000007', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000000702', '5e0a0001-0000-4000-8000-000000000007', 'date_from',    'Period from',    1, true,  false, false, 2),
    ('5e0a0002-0000-4000-8000-000000000703', '5e0a0001-0000-4000-8000-000000000007', 'date_to',      'Period to',      1, true,  true,  false, 3),
    ('5e0a0002-0000-4000-8000-000000001101', '5e0a0001-0000-4000-8000-000000000011', 'depositor',    'Depositor code', 9, false, false, true, 1),
    ('5e0a0002-0000-4000-8000-000000001102', '5e0a0001-0000-4000-8000-000000000011', 'item_code',    'Item code',     11, false, false, true, 2),
    ('5e0a0002-0000-4000-8000-000000001103', '5e0a0001-0000-4000-8000-000000000011', 'date_from',    'Period from',    1, true,  false, false, 3),
    ('5e0a0002-0000-4000-8000-000000001104', '5e0a0001-0000-4000-8000-000000000011', 'date_to',      'Period to',      1, true,  true,  false, 4)
  ) AS v(pid, rid, key, label, type_id, req, today, hidden, ord)
ON CONFLICT DO NOTHING;

INSERT INTO report_param_text (param_id, locale_id, label)
SELECT p.param_id, l.locale_id,
       CASE p.param_key WHEN 'depositor' THEN 'Код поклажедателя'
                        WHEN 'warehouse_id' THEN 'Склад'
                        WHEN 'date_from' THEN 'Период с'
                        WHEN 'date_to' THEN 'Период по'
                        WHEN 'item_code' THEN 'Код товара' END
  FROM report_param p
  JOIN dictionary_locale l ON l.code = 'ru'
 WHERE p.param_id::text LIKE '5e0a0002-%'
ON CONFLICT DO NOTHING;

-- Выдать: «Рога и Копыта» — все; «Молочные реки» — без «Заказов за
-- сегодня» и со своим названием остатков.
INSERT INTO depositor_report (depositor_id, report_id, custom_name, sort_order)
SELECT d.depositor_id, r.report_id, NULL, r.sort_order
  FROM depositor d, report r
 WHERE d.code = 'D-001' AND r.report_id::text LIKE '5e0a0001-%'
ON CONFLICT DO NOTHING;

INSERT INTO depositor_report (depositor_id, report_id, custom_name, sort_order)
SELECT d.depositor_id, r.report_id,
       CASE WHEN r.wms_code = 'RPT-STOCK' THEN 'Что лежит на складе' END, r.sort_order
  FROM depositor d, report r
 WHERE d.code = 'D-002' AND r.report_id::text LIKE '5e0a0001-%'
   AND r.report_id <> '5e0a0001-0000-4000-8000-000000000006'
ON CONFLICT DO NOTHING;

COMMIT;
