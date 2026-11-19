-- ---------------------------------------------------------------------
-- V87. Статусы: один общий список для документов
-- ---------------------------------------------------------------------
-- Решения заказчика от 29.10.2026 (документ «Перечисления — согласование
-- списков», §1):
--   * у поступлений, заказов и перемещений — ОДИН список статусов,
--     а не свой набор у каждого: человек статус руками не ставит,
--     и три «Черновика» с разными идентификаторами только путали;
--   * код — отдельным полем, текстом, две цифры: 01 … 90. Обмен с WMS —
--     по коду. Имя по умолчанию английское, русское — перевод (V86);
--   * статусы товара и счёта — свои перечисления, к документам они
--     отношения не имеют: enum_sku_status, enum_invoice_status;
--   * статусы упаковки (51, 52) нигде не использовались — удалены;
--   * правка транспорта после отправки — пока только в статусе
--     10 «Принят WMS» (временно, правило обсуждается отдельно);
--   * «Ожидается» в запасах — поступления в статусах 02, 10, 30.
--     «Ошибка отправки» больше не считается: склад о таком поступлении
--     не знает и машину не ждёт.
--
-- Где какой статус бывает — таблица enum_status_scope (колонка «Где»
-- согласованной таблицы). По ней фильтр заказов предлагает только
-- статусы заказов, а неизвестный статус от WMS заводится только для
-- того вида документа, откуда пришёл.
--
-- Идентификаторы новых статусов — 100 + код (101, 102, 103, 110 … 190).
-- Прежние 11…37 заняты до конца миграции, а совпадение «старый 21 —
-- черновик заказа, новый 21 — зарезервирован частично» молча подменило
-- бы статус документа. Наружу идентификатор не виден: везде код.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Статусы товара и счёта — отдельно от документов
-- ---------------------------------------------------------------------
CREATE TABLE enum_sku_status (
  id          integer      PRIMARY KEY,
  code        varchar(30)  NOT NULL UNIQUE,
  name        varchar(255) NOT NULL,
  sort_order  smallint,
  is_active   boolean      NOT NULL DEFAULT true
);
COMMENT ON TABLE enum_sku_status IS 'статус товара (V87): 01 активен, 02 неактивен';
INSERT INTO enum_sku_status (id, code, name, sort_order) VALUES
  (1, '01', 'Active',   1),
  (2, '02', 'Inactive', 2);

CREATE TABLE enum_invoice_status (
  id          integer      PRIMARY KEY,
  code        varchar(30)  NOT NULL UNIQUE,
  name        varchar(255) NOT NULL,
  sort_order  smallint,
  is_active   boolean      NOT NULL DEFAULT true
);
COMMENT ON TABLE enum_invoice_status IS 'статус счёта (V87): 01 ожидает оплаты, 02 оплачен, 03 просрочен';
INSERT INTO enum_invoice_status (id, code, name, sort_order) VALUES
  (1, '01', 'Awaiting payment', 1),
  (2, '02', 'Paid',             2),
  (3, '03', 'Overdue',          3);

GRANT SELECT ON enum_sku_status, enum_invoice_status TO portal_app;

-- Перевод ссылок. Неизвестный код — ошибка, а не NULL: молча потерять
-- статус у товара или счёта хуже, чем остановить миграцию.
DO $remap$
DECLARE
  bad text;
BEGIN
  SELECT string_agg(DISTINCT es.code, ', ') INTO bad
    FROM sku s JOIN enum_status es ON es.id = s.status_id
   WHERE es.code NOT IN ('active', 'inactive');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'у товаров статусы, которых нет в новом списке: %', bad;
  END IF;
  SELECT string_agg(DISTINCT es.code, ', ') INTO bad
    FROM invoice i JOIN enum_status es ON es.id = i.status_id
   WHERE es.code NOT IN ('invoice_awaiting', 'invoice_paid', 'invoice_overdue');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'у счетов статусы, которых нет в новом списке: %', bad;
  END IF;
END
$remap$;

ALTER TABLE sku     DROP CONSTRAINT IF EXISTS fk_sku_status_id;
ALTER TABLE invoice DROP CONSTRAINT IF EXISTS fk_invoice_status_id;

-- RLS: перевод касается всех поклажедателей сразу. Flyway ходит
-- суперпользователем (application.yml), а он RLS не подчиняется.
-- Если когда-нибудь миграции пойдут ролью без этого права, строки
-- чужих поклажедателей останутся со старыми статусами — и удаление
-- старых статусов ниже упадёт на внешнем ключе. Громко, а не молча.

UPDATE sku s
   SET status_id = CASE es.code WHEN 'active' THEN 1 WHEN 'inactive' THEN 2 END
  FROM enum_status es
 WHERE es.id = s.status_id;

UPDATE invoice i
   SET status_id = CASE es.code WHEN 'invoice_awaiting' THEN 1
                                WHEN 'invoice_paid'     THEN 2
                                WHEN 'invoice_overdue'  THEN 3 END
  FROM enum_status es
 WHERE es.id = i.status_id;

ALTER TABLE sku ADD CONSTRAINT fk_sku_status_id
  FOREIGN KEY (status_id) REFERENCES enum_sku_status (id);
ALTER TABLE invoice ADD CONSTRAINT fk_invoice_status_id
  FOREIGN KEY (status_id) REFERENCES enum_invoice_status (id);

-- ---------------------------------------------------------------------
-- 2. Общий список статусов документов
-- ---------------------------------------------------------------------
CREATE TABLE enum_status_scope (
  status_id       integer  NOT NULL REFERENCES enum_status (id) ON DELETE CASCADE,
  object_type_id  smallint NOT NULL REFERENCES enum_object_type (id),
  PRIMARY KEY (status_id, object_type_id)
);
COMMENT ON TABLE enum_status_scope IS
  'у каких документов бывает статус (V87): 1 поступления, 2 заказы, 3 перемещения';
GRANT SELECT ON enum_status_scope TO portal_app;

INSERT INTO enum_status (id, object_type_id, code, name, sort_order, is_active,
                         allows_transport_edit, is_auto_created) VALUES
  (101, NULL, '01', 'Draft',                     1, true, false, false),
  (102, NULL, '02', 'Sent to WMS',               2, true, false, false),
  (103, NULL, '03', 'Sending failed',            3, true, false, false),
  (110, NULL, '10', 'Accepted by WMS',          10, true, true,  false),
  (120, NULL, '20', 'Reserved',                 20, true, false, false),
  (121, NULL, '21', 'Partially reserved',       21, true, false, false),
  (130, NULL, '30', 'In progress',              30, true, false, false),
  (140, NULL, '40', 'Ready for shipment',       40, true, false, false),
  (150, NULL, '50', 'Shipped',                  50, true, false, false),
  (151, NULL, '51', 'Partially shipped',        51, true, false, false),
  (160, NULL, '60', 'Received',                 60, true, false, false),
  (161, NULL, '61', 'Received with discrepancy', 61, true, false, false),
  (190, NULL, '90', 'Cancelled',                90, true, false, false);

-- «Где» из согласованной таблицы: П — 1, З — 2, Пм — 3
INSERT INTO enum_status_scope (status_id, object_type_id)
SELECT s.id, t.object_type_id
  FROM (VALUES
    ('01', '{1,2,3}'), ('02', '{1,2,3}'), ('03', '{1,2,3}'), ('10', '{1,2,3}'),
    ('20', '{2,3}'),   ('21', '{2,3}'),   ('30', '{1,2,3}'), ('40', '{2,3}'),
    ('50', '{2,3}'),   ('51', '{2}'),     ('60', '{1,3}'),   ('61', '{1,3}'),
    ('90', '{1,2,3}')
  ) AS v(code, types)
  JOIN enum_status s ON s.code = v.code AND s.id >= 100
  CROSS JOIN LATERAL unnest(v.types::smallint[]) AS t(object_type_id);

-- Прежние статусы → новые (таблица «было → станет» от 29.10.2026)
CREATE TEMP TABLE v87_status_map (object_type_id smallint, old_code text, new_code text)
  ON COMMIT DROP;
INSERT INTO v87_status_map VALUES
  (1, 'draft', '01'), (1, 'sent_to_wms', '02'), (1, 'send_failed', '03'),
  (1, 'awaiting', '10'), (1, 'in_progress', '30'), (1, 'received', '60'),
  (1, 'discrepancy', '61'),
  (2, 'draft', '01'), (2, 'sent_to_wms', '02'), (2, 'send_failed', '03'),
  (2, 'reserved', '20'), (2, 'picking', '30'), (2, 'shipped', '50'),
  (2, 'delayed', '30'),
  (3, 'draft', '01'), (3, 'sent_to_wms', '02'), (3, 'send_failed', '03'),
  (3, 'forming', '30'), (3, 'in_transit', '50'), (3, 'received', '60'),
  (3, 'form_error', '03');

DO $remap$
DECLARE
  bad text;
BEGIN
  -- Статус, которого нет ни в таблице перевода, ни среди заведённых
  -- автоматически, — значит, его завели руками, и куда его девать,
  -- решает человек. Миграция останавливается, а не угадывает.
  SELECT string_agg(DISTINCT es.code, ', ') INTO bad
    FROM enum_status es
   WHERE es.id < 100 AND es.object_type_id IN (1, 2, 3)
     AND NOT es.is_auto_created
     AND NOT EXISTS (SELECT 1 FROM v87_status_map m
                      WHERE m.object_type_id = es.object_type_id AND m.old_code = es.code);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'статусы документов без перевода в новый список: %', bad;
  END IF;
END
$remap$;

-- Старый id → новый: и для перевода документов, и для проверки ниже
CREATE TEMP TABLE v87_id_map ON COMMIT DROP AS
SELECT es.id AS old_id, ns.id AS new_id
  FROM enum_status es
  JOIN v87_status_map m ON m.object_type_id = es.object_type_id AND m.old_code = es.code
  JOIN enum_status ns ON ns.code = m.new_code AND ns.id >= 100
 WHERE es.id < 100 AND NOT es.is_auto_created;

UPDATE receipts  d SET status_id = m.new_id FROM v87_id_map m WHERE d.status_id = m.old_id;
UPDATE orders    d SET status_id = m.new_id FROM v87_id_map m WHERE d.status_id = m.old_id;
UPDATE transfers d SET status_id = m.new_id FROM v87_id_map m WHERE d.status_id = m.old_id;

-- Заведённые автоматически (Р5) остаются: код у них — от WMS,
-- имя — то, что WMS прислала. Где бывают — там, откуда пришли.
INSERT INTO enum_status_scope (status_id, object_type_id)
SELECT id, object_type_id FROM enum_status
 WHERE id < 100 AND is_auto_created AND object_type_id IN (1, 2, 3);

-- Старые статусы документов, товара, упаковки и счёта больше не нужны.
-- Ссылок на них не осталось — внешние ключи это и проверят.
DELETE FROM enum_status WHERE id < 100 AND NOT is_auto_created;
DELETE FROM enum_status WHERE id < 100 AND is_auto_created AND object_type_id NOT IN (1, 2, 3);

ALTER TABLE enum_status DROP CONSTRAINT IF EXISTS fk_enum_status_object_type_id;
ALTER TABLE enum_status DROP COLUMN object_type_id;
CREATE UNIQUE INDEX enum_status_code_key ON enum_status (code);
COMMENT ON TABLE enum_status IS
  'статусы документов — один список для поступлений, заказов и перемещений (V87); где какой бывает — enum_status_scope';


-- ---------------------------------------------------------------------
-- 3. Неизвестный статус от WMS (Р5) — по коду в общем списке
-- ---------------------------------------------------------------------
-- Сигнатура прежняя: вид документа нужен, чтобы новый статус появился
-- только у того документа, откуда пришёл, а не во всех фильтрах сразу.
-- Если код уже есть, но у этого вида документа не бывал, — добавляется
-- в его «Где»: WMS прислала, значит, бывает.
CREATE OR REPLACE FUNCTION resolve_wms_status(p_object_type_id smallint, p_code text,
                                              p_name text DEFAULT NULL::text)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  v_id integer;
BEGIN
  SELECT id INTO v_id FROM enum_status WHERE code = p_code;
  IF v_id IS NULL THEN
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_id FROM enum_status;
    INSERT INTO enum_status (id, code, name, sort_order, is_active, is_auto_created)
    VALUES (v_id, p_code, COALESCE(p_name, p_code), 900, true, true);
  END IF;
  INSERT INTO enum_status_scope (status_id, object_type_id)
  VALUES (v_id, p_object_type_id)
  ON CONFLICT DO NOTHING;
  RETURN v_id;
END
$$;

-- ---------------------------------------------------------------------
-- 4. Представления
-- ---------------------------------------------------------------------
-- Колонки и их типы прежние — меняется только источник статуса.
-- security_invoker указывается заново: замена без WITH его сбрасывает.
CREATE OR REPLACE VIEW v_sku_full WITH (security_invoker = true) AS
 SELECT s.sku_id,
    s.depositor_id,
    d.org_name AS depositor_name,
    s.item_code,
    s.code,
    s.name,
    s.item_full_name,
    s.barcode,
    (enum_symbol('enum_uom'::text, (u.id)::text, (u.symbol)::text))::character varying(10) AS uom_name,
    s.uom_id,
    ( SELECT p.packaging_code
           FROM product_packaging p
          WHERE (p.packaging_id = s.order_unit_id)) AS order_unit_name,
    s.order_unit_id,
    s.item_group AS item_group_name,
    s.item_type AS item_type_name,
    s.has_packaging,
    s.length,
    s.width,
    s.height,
    s.volume,
    s.weight_gross,
    s.weight_net,
    s.manufacturer,
    s.country_origin,
    s.brand,
    s.is_fragile,
    s.is_serial_tracked,
    s.batch_tracking,
    s.expiry_tracking,
    s.min_storage_temp,
    s.max_storage_temp,
    s.min_humidity,
    s.max_humidity,
    s.min_days_for_receipt,
    s.min_days_for_reservation,
    s.shelf_life_days,
    s.color,
    s.size,
    s.abc_class,
    s.xyz_class,
    s.hs_code,
    s.enstru_code,
    s.cost,
    cur.code AS currency_code,
    s.currency_id,
    s.is_kit,
    s.is_marking_required,
    s.is_excise,
    s.is_controlled,
    s.is_wms_active,
    (enum_name('enum_hazard_class'::text, (hc.id)::text, (hc.name)::text))::character varying(255) AS hazard_class_name,
    s.hazard_class_id,
    (enum_name('enum_hazard_group'::text, (hg.id)::text, (hg.name)::text))::character varying(255) AS hazard_group_name,
    s.hazard_group_id,
    (enum_name('enum_sku_status'::text, (st.id)::text, (st.name)::text))::character varying(255) AS status_name,
    s.comment,
    s.is_active,
    s.is_in_wms,
    s.synced_at,
    s.wms_version,
    s.created_by,
    s.created_at,
    s.updated_by,
    s.updated_at,
    s.custom_attr1,
    s.custom_attr2,
    s.custom_attr3,
    s.custom_attr4,
    s.custom_attr5,
    s.custom_attr6,
    s.custom_attr7,
    s.custom_attr8,
    s.custom_attr9,
    s.custom_attr10
   FROM ((((((sku s
     LEFT JOIN depositor d ON ((d.depositor_id = s.depositor_id)))
     LEFT JOIN enum_uom u ON ((u.id = s.uom_id)))
     LEFT JOIN enum_currency cur ON ((cur.id = s.currency_id)))
     LEFT JOIN enum_hazard_class hc ON ((hc.id = s.hazard_class_id)))
     LEFT JOIN enum_hazard_group hg ON ((hg.id = s.hazard_group_id)))
     LEFT JOIN enum_sku_status st ON ((st.id = s.status_id)));
CREATE OR REPLACE VIEW v_invoices WITH (security_invoker = true) AS
 SELECT i.id,
    i.depositor_id,
    i.invoice_number,
    i.period_from,
    i.period_to,
    i.issue_date,
    i.amount_no_vat,
    i.amount_vat,
    (i.amount_no_vat + COALESCE(i.amount_vat, (0)::numeric)) AS amount_with_vat,
    (enum_name('enum_invoice_status'::text, (st.id)::text, (st.name)::text))::character varying(255) AS status_name,
    st.code AS status_code,
    (i.pdf_file IS NOT NULL) AS has_pdf
   FROM (invoice i
     LEFT JOIN enum_invoice_status st ON ((st.id = i.status_id)));
CREATE OR REPLACE VIEW v_stock_available WITH (security_invoker = true) AS
 WITH on_hand AS (
         SELECT stock.depositor_id,
            stock.sku_id,
            stock.warehouse_id,
            max(stock.unit_id) AS unit_id,
            sum(stock.quantity) AS quantity,
            sum(stock.reserved_qty) AS reserved_qty,
            sum(stock.blocked_qty) AS blocked_qty,
            sum(stock.available_qty) AS available_qty,
            sum(
                CASE
                    WHEN ((stock.quality)::text = 'Брак'::text) THEN stock.quantity
                    ELSE (0)::numeric
                END) AS defect_qty,
            count(*) AS batch_count,
            max(stock.synced_at) AS synced_at
           FROM stock
          GROUP BY stock.depositor_id, stock.sku_id, stock.warehouse_id
        ), incoming AS (
         SELECT r.depositor_id,
            rd.sku_id,
            r.warehouse_id,
            sum(rd.expected_qty) AS incoming_qty
           FROM ((receipt_details rd
             JOIN receipts r ON ((r.doc_id = rd.doc_id)))
             JOIN enum_status es ON ((es.id = r.status_id)))
          WHERE ((rd.batch_number IS NULL) AND ((es.code)::text = ANY (ARRAY['02'::text, '10'::text, '30'::text])))
          GROUP BY r.depositor_id, rd.sku_id, r.warehouse_id
        )
 SELECT COALESCE(h.depositor_id, i.depositor_id) AS depositor_id,
    COALESCE(h.sku_id, i.sku_id) AS sku_id,
    COALESCE(h.warehouse_id, i.warehouse_id) AS warehouse_id,
    h.unit_id,
    COALESCE(h.quantity, (0)::numeric) AS quantity,
    COALESCE(h.reserved_qty, (0)::numeric) AS reserved_qty,
    COALESCE(h.blocked_qty, (0)::numeric) AS blocked_qty,
    COALESCE(h.available_qty, (0)::numeric) AS available_qty,
    COALESCE(h.defect_qty, (0)::numeric) AS defect_qty,
    COALESCE(i.incoming_qty, (0)::numeric) AS incoming_qty,
    COALESCE(h.batch_count, (0)::bigint) AS batch_count,
    h.synced_at
   FROM (on_hand h
     FULL JOIN incoming i ON (((i.depositor_id = h.depositor_id) AND (i.sku_id = h.sku_id) AND ((i.warehouse_id)::text = (h.warehouse_id)::text))));

-- ---------------------------------------------------------------------
-- 5. Перечисления на вкладке «Перечисления» и переводы
-- ---------------------------------------------------------------------
INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order) VALUES
  ('enum_sku_status',     'code', false, 'enums.name.enum_sku_status',     12),
  ('enum_invoice_status', 'code', false, 'enums.name.enum_invoice_status', 14)
ON CONFLICT (enum_key) DO NOTHING;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT v.enum_key, e.id::text, l.locale_id, v.name
  FROM (VALUES
    ('enum_status', '01', 'Черновик'),
    ('enum_status', '02', 'Отправлен в WMS'),
    ('enum_status', '03', 'Ошибка отправки'),
    ('enum_status', '10', 'Принят WMS'),
    ('enum_status', '20', 'Зарезервирован'),
    ('enum_status', '21', 'Зарезервирован частично'),
    ('enum_status', '30', 'В работе'),
    ('enum_status', '40', 'Готов к отгрузке'),
    ('enum_status', '50', 'Отгружен'),
    ('enum_status', '51', 'Отгружен частично'),
    ('enum_status', '60', 'Принят'),
    ('enum_status', '61', 'Принят с расхождением'),
    ('enum_status', '90', 'Отменён')
  ) AS v(enum_key, code, name)
  JOIN enum_status e ON e.code = v.code
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT v.enum_key, v.id, l.locale_id, v.name
  FROM (VALUES
    ('enum_sku_status',     '1', 'Активен'),
    ('enum_sku_status',     '2', 'Неактивен'),
    ('enum_invoice_status', '1', 'Ожидает оплаты'),
    ('enum_invoice_status', '2', 'Оплачен'),
    ('enum_invoice_status', '3', 'Просрочен')
  ) AS v(enum_key, id, name)
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('enums.name.enum_sku_status',     'admin', 'text', NULL, 'название перечисления (V87)'),
  ('enums.name.enum_invoice_status', 'admin', 'text', NULL, 'название перечисления (V87)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('enums.name.enum_status',         'ru', 'Статусы документов'),
    ('enums.name.enum_status',         'en', 'Document statuses'),
    ('enums.name.enum_sku_status',     'ru', 'Статусы товара'),
    ('enums.name.enum_sku_status',     'en', 'Item statuses'),
    ('enums.name.enum_invoice_status', 'ru', 'Статусы счёта'),
    ('enums.name.enum_invoice_status', 'en', 'Invoice statuses')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  v text;
  n integer;
BEGIN
  FOREACH v IN ARRAY ARRAY['v_sku_full', 'v_invoices', 'v_stock_available'] LOOP
    IF (SELECT reloptions FROM pg_class WHERE oid = ('dbo.' || v)::regclass)
         IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
      RAISE EXCEPTION '% потеряло security_invoker', v;
    END IF;
  END LOOP;

  SELECT count(*) INTO n FROM enum_status WHERE NOT is_auto_created;
  IF n <> 13 THEN
    RAISE EXCEPTION 'в общем списке статусов % строк, а согласовано 13', n;
  END IF;
  IF (SELECT string_agg(code, ',' ORDER BY code) FROM enum_status WHERE allows_transport_edit)
       IS DISTINCT FROM '10' THEN
    RAISE EXCEPTION 'правка транспорта после отправки — только в статусе 10';
  END IF;
  SELECT count(*) INTO n FROM enum_status s
   WHERE NOT EXISTS (SELECT 1 FROM enum_status_scope c WHERE c.status_id = s.id);
  IF n > 0 THEN
    RAISE EXCEPTION 'у % статусов не указано, у каких документов они бывают', n;
  END IF;

  -- Перевод на месте: язык сессии русский — имя русское, без языка — английское
  PERFORM set_config('app.locale', 'ru', true);
  IF enum_name('enum_status', '101', 'Draft') IS DISTINCT FROM 'Черновик' THEN
    RAISE EXCEPTION 'нет русского перевода статуса 01';
  END IF;
  PERFORM set_config('app.locale', '', true);
  IF enum_name('enum_status', '101', 'Draft') IS DISTINCT FROM 'Draft' THEN
    RAISE EXCEPTION 'без языка ждали английское имя статуса 01';
  END IF;

  -- Неизвестный статус от WMS: заводится один раз, у своего документа
  BEGIN
    IF resolve_wms_status(2::smallint, 'V87-CHECK', 'Проверка')
         IS DISTINCT FROM resolve_wms_status(2::smallint, 'V87-CHECK') THEN
      RAISE EXCEPTION 'неизвестный статус заведён дважды';
    END IF;
    IF (SELECT array_agg(c.object_type_id) FROM enum_status_scope c
          JOIN enum_status s ON s.id = c.status_id WHERE s.code = 'V87-CHECK')
         IS DISTINCT FROM ARRAY[2::smallint] THEN
      RAISE EXCEPTION 'неизвестный статус попал не только к заказам';
    END IF;
    IF resolve_wms_status(1::smallint, '10') IS DISTINCT FROM 110 THEN
      RAISE EXCEPTION 'известный код 10 не нашёлся в общем списке';
    END IF;
    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'v87-rollback';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'v87-rollback' THEN
      RAISE;
    END IF;
  END;
END
$check$;
