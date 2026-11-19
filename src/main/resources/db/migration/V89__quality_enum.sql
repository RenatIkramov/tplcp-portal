-- ---------------------------------------------------------------------
-- V89. Качество — перечисление
-- ---------------------------------------------------------------------
-- Решения заказчика от 28–29.10.2026:
--   * качество — перечисление с кодом: 01 Годный, 02 Брак,
--     03 Некомплект, 04 Контроль качества. Имя по умолчанию английское,
--     русское — перевод (V86). В WMS уходит код;
--   * в строке заказа вместо галочки «Брак» (V85) — выбор из списка,
--     по умолчанию «Годный»; остатки в окне строки — по выбранному
--     качеству;
--   * перечисление — ВЕЗДЕ, где есть качество: строки заказа, партии
--     поступлений и перемещений, остатки. Одно значение во всём портале:
--     «Заблокировано» (в пуле) потом считается по коду, а не по слову;
--   * «Карантин» в остатках — это 04 «Контроль качества»;
--   * качество, которого нет в списке, при обмене с WMS — отказ, а не
--     новое значение (в отличие от статусов, Р5). Здесь это держит
--     внешний ключ: записать строку с чужим качеством нельзя.
--
-- Прежняя текстовая колонка quality удаляется из таблиц — вместо неё
-- quality_id. В представлениях колонка quality остаётся, но теперь это
-- имя на языке пользователя; рядом — quality_code для обмена и экрана.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Перечисление
-- ---------------------------------------------------------------------
CREATE TABLE enum_quality (
  id          smallint     PRIMARY KEY,
  code        varchar(30)  NOT NULL UNIQUE CHECK (code ~ '^[0-9]{2}$'),
  name        varchar(255) NOT NULL,
  sort_order  smallint,
  is_active   boolean      NOT NULL DEFAULT true
);
COMMENT ON TABLE enum_quality IS
  'качество товара (V89): 01 годный, 02 брак, 03 некомплект, 04 контроль качества';
INSERT INTO enum_quality (id, code, name, sort_order) VALUES
  (1, '01', 'Good',            1),
  (2, '02', 'Defective',       2),
  (3, '03', 'Incomplete',      3),
  (4, '04', 'Quality control', 4);
GRANT SELECT ON enum_quality TO portal_app;

INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order) VALUES
  ('enum_quality', 'code', false, 'enums.name.enum_quality', 75)
ON CONFLICT (enum_key) DO NOTHING;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_quality', v.id, l.locale_id, v.name
  FROM (VALUES ('1', 'Годный'), ('2', 'Брак'), ('3', 'Некомплект'), ('4', 'Контроль качества'))
       AS v(id, name)
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- ---------------------------------------------------------------------
-- 2. Слова → коды
-- ---------------------------------------------------------------------
-- Слова, которыми качество писали до сих пор: портал (V85) — «Годный»
-- и «Брак», WMS в демо-данных — ещё «Карантин». Пустое качество
-- остаётся пустым: у строки, записанной до V85, его просто нет.
CREATE TEMP TABLE v89_quality_word (word text PRIMARY KEY, quality_id smallint) ON COMMIT DROP;
INSERT INTO v89_quality_word VALUES
  ('годный', 1), ('годен', 1), ('good', 1),
  ('брак', 2), ('defective', 2),
  ('некомплект', 3), ('incomplete', 3),
  ('контроль качества', 4), ('карантин', 4), ('quality control', 4);

-- Незнакомое слово — остановка: куда его отнести, решает человек.
DO $words$
DECLARE
  bad text;
BEGIN
  SELECT string_agg(DISTINCT w, ', ') INTO bad
    FROM (SELECT quality AS w FROM stock
          UNION ALL SELECT quality FROM order_details
          UNION ALL SELECT quality FROM receipt_details
          UNION ALL SELECT quality FROM transfer_details) a
   WHERE NULLIF(btrim(w), '') IS NOT NULL
     AND lower(btrim(w)) NOT IN (SELECT word FROM v89_quality_word);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'качество, которого нет в перечислении: %', bad;
  END IF;
END
$words$;

-- RLS: Flyway ходит суперпользователем и видит строки всех
-- поклажедателей. Иначе непереведённые строки поймает проверка в конце.
ALTER TABLE stock            ADD COLUMN quality_id smallint REFERENCES enum_quality (id);
ALTER TABLE order_details    ADD COLUMN quality_id smallint REFERENCES enum_quality (id);
ALTER TABLE receipt_details  ADD COLUMN quality_id smallint REFERENCES enum_quality (id);
ALTER TABLE transfer_details ADD COLUMN quality_id smallint REFERENCES enum_quality (id);

UPDATE stock t SET quality_id = w.quality_id
  FROM v89_quality_word w WHERE w.word = lower(btrim(t.quality));
UPDATE order_details t SET quality_id = w.quality_id
  FROM v89_quality_word w WHERE w.word = lower(btrim(t.quality));
UPDATE receipt_details t SET quality_id = w.quality_id
  FROM v89_quality_word w WHERE w.word = lower(btrim(t.quality));
UPDATE transfer_details t SET quality_id = w.quality_id
  FROM v89_quality_word w WHERE w.word = lower(btrim(t.quality));

COMMENT ON COLUMN stock.quality_id IS 'качество партии (V89), пишет WMS кодом';
COMMENT ON COLUMN order_details.quality_id IS
  'качество: у плановой строки — выбранное в заказе (по умолчанию 01), у партий — от WMS (V89)';
COMMENT ON COLUMN receipt_details.quality_id IS 'качество принятой партии (V89), пишет WMS';
COMMENT ON COLUMN transfer_details.quality_id IS 'качество партии перемещения (V89), пишет WMS';

-- ---------------------------------------------------------------------
-- 3. Представления
-- ---------------------------------------------------------------------
-- Запасы: колонки прежние, в конец добавлена quality_qty — остаток
-- и доступное по каждому качеству, JSON-строкой {"01": {quantity,
-- available}, …}. По ней окно строки заказа показывает цифры
-- выбранного качества. «Брак» (defect_qty) — по коду 02.
-- «Доступно» у годного — то, что WMS называет доступным (available_qty).
-- У остальных качеств WMS ставит доступное в ноль: некондиция заблокирована
-- для обычного резерва. Но заказ с качеством «Брак» резервирует как раз её,
-- поэтому доступное там — остаток за вычетом уже зарезервированного
-- (так и считала галочка «Брак» в V85).
-- security_invoker указывается заново: замена без WITH его сбрасывает.
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
                    WHEN ((q.code)::text = '02'::text) THEN stock.quantity
                    ELSE (0)::numeric
                END) AS defect_qty,
            count(*) AS batch_count,
            max(stock.synced_at) AS synced_at
           FROM (dbo.stock
             LEFT JOIN dbo.enum_quality q ON ((q.id = stock.quality_id)))
          GROUP BY stock.depositor_id, stock.sku_id, stock.warehouse_id
        ), by_quality AS (
         SELECT b.depositor_id,
            b.sku_id,
            b.warehouse_id,
            (json_object_agg(b.code, json_build_object('quantity', b.quantity, 'available', b.available_qty)
              ORDER BY b.code))::text AS quality_qty
           FROM ( SELECT stock.depositor_id,
                    stock.sku_id,
                    stock.warehouse_id,
                    q.code,
                    sum(stock.quantity) AS quantity,
                    CASE
                        WHEN ((q.code)::text = '01'::text) THEN sum(stock.available_qty)
                        ELSE sum((stock.quantity - COALESCE(stock.reserved_qty, (0)::numeric)))
                    END AS available_qty
                   FROM (dbo.stock
                     JOIN dbo.enum_quality q ON ((q.id = stock.quality_id)))
                  GROUP BY stock.depositor_id, stock.sku_id, stock.warehouse_id, q.code) b
          GROUP BY b.depositor_id, b.sku_id, b.warehouse_id
        ), incoming AS (
         SELECT r.depositor_id,
            rd.sku_id,
            r.warehouse_id,
            sum(rd.expected_qty) AS incoming_qty
           FROM ((dbo.receipt_details rd
             JOIN dbo.receipts r ON ((r.doc_id = rd.doc_id)))
             JOIN dbo.enum_status es ON ((es.id = r.status_id)))
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
    h.synced_at,
    bq.quality_qty
   FROM ((on_hand h
     FULL JOIN incoming i ON (((i.depositor_id = h.depositor_id) AND (i.sku_id = h.sku_id) AND ((i.warehouse_id)::text = (h.warehouse_id)::text))))
     LEFT JOIN by_quality bq ON (((bq.depositor_id = h.depositor_id) AND (bq.sku_id = h.sku_id) AND ((bq.warehouse_id)::text = (h.warehouse_id)::text))));
CREATE OR REPLACE VIEW v_stock_lines WITH (security_invoker = true) AS
 SELECT a.depositor_id,
    a.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    s.item_group AS item_group_name,
    a.warehouse_id,
    w.name AS warehouse_name,
    (dbo.enum_symbol('enum_uom'::text, (u.id)::text, (u.symbol)::text))::character varying(10) AS unit_name,
    a.quantity,
    a.reserved_qty,
    a.blocked_qty,
    a.available_qty,
    a.defect_qty,
    a.incoming_qty,
    a.batch_count,
    a.synced_at,
    a.quality_qty
   FROM (((dbo.v_stock_available a
     LEFT JOIN dbo.sku s ON ((s.sku_id = a.sku_id)))
     LEFT JOIN dbo.warehouse w ON (((w.warehouse_id)::text = (a.warehouse_id)::text)))
     LEFT JOIN dbo.enum_uom u ON ((u.id = COALESCE(a.unit_id, s.uom_id))));
DROP VIEW v_stock_details;
CREATE VIEW v_stock_details WITH (security_invoker = true) AS
 SELECT st.register_id,
    st.depositor_id,
    st.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    st.warehouse_id,
    w.name AS warehouse_name,
    st.batch_number,
    st.series_number,
    st.expiry_date,
    (dbo.enum_name('enum_quality'::text, (q.id)::text, (q.name)::text))::character varying(255) AS quality,
    q.code AS quality_code,
    (dbo.enum_name('enum_block_reason'::text, (br.id)::text, (br.name)::text))::character varying(255) AS block_reason_name,
    st.quantity,
    st.reserved_qty,
    st.blocked_qty,
    st.available_qty,
    (dbo.enum_symbol('enum_uom'::text, (u.id)::text, (u.symbol)::text))::character varying(10) AS unit_name,
    st.updated_at,
    st.synced_at
   FROM (((((dbo.stock st
     LEFT JOIN dbo.sku s ON ((s.sku_id = st.sku_id)))
     LEFT JOIN dbo.warehouse w ON (((w.warehouse_id)::text = (st.warehouse_id)::text)))
     LEFT JOIN dbo.enum_uom u ON ((u.id = st.unit_id)))
     LEFT JOIN dbo.enum_block_reason br ON ((br.id = st.block_reason_id)))
     LEFT JOIN dbo.enum_quality q ON ((q.id = st.quality_id)));

DROP VIEW v_receipt_line_details;
CREATE VIEW v_receipt_line_details WITH (security_invoker = true) AS
 SELECT rd.line_id,
    rd.doc_id,
    rd.line_number,
    rd.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    rd.batch_number,
    rd.series_number,
    rd.expiry_date,
    (dbo.enum_name('enum_quality'::text, (q.id)::text, (q.name)::text))::character varying(255) AS quality,
    q.code AS quality_code,
    rd.accepted_qty,
    rd.defect_qty,
    rd.line_comment
   FROM ((dbo.receipt_details rd
     LEFT JOIN dbo.sku s ON ((s.sku_id = rd.sku_id)))
     LEFT JOIN dbo.enum_quality q ON ((q.id = rd.quality_id)))
  WHERE (rd.batch_number IS NOT NULL);

DROP VIEW v_order_line_details;
CREATE VIEW v_order_line_details WITH (security_invoker = true) AS
 SELECT od.line_id,
    od.doc_id,
    od.line_number,
    od.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    od.batch_number,
    od.series_number,
    od.expiry_date,
    (dbo.enum_name('enum_quality'::text, (q.id)::text, (q.name)::text))::character varying(255) AS quality,
    q.code AS quality_code,
    od.picked_qty,
    od.weight_kg,
    od.line_comment
   FROM ((dbo.order_details od
     LEFT JOIN dbo.sku s ON ((s.sku_id = od.sku_id)))
     LEFT JOIN dbo.enum_quality q ON ((q.id = od.quality_id)))
  WHERE (od.batch_number IS NOT NULL);

DROP VIEW v_transfer_line_details;
CREATE VIEW v_transfer_line_details WITH (security_invoker = true) AS
 SELECT td.line_id,
    td.doc_id,
    td.line_number,
    td.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    td.batch_number,
    td.series_number,
    td.expiry_date,
    (dbo.enum_name('enum_quality'::text, (q.id)::text, (q.name)::text))::character varying(255) AS quality,
    q.code AS quality_code,
    td.qty,
    td.qty_wms,
    td.line_comment
   FROM ((dbo.transfer_details td
     LEFT JOIN dbo.sku s ON ((s.sku_id = td.sku_id)))
     LEFT JOIN dbo.enum_quality q ON ((q.id = td.quality_id)))
  WHERE (td.batch_number IS NOT NULL);

DROP VIEW v_order_lines;
CREATE VIEW v_order_lines WITH (security_invoker = true) AS
 SELECT od.doc_id,
    od.line_number,
    od.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    (dbo.enum_symbol('enum_uom'::text, (u.id)::text, (u.symbol)::text))::character varying(10) AS unit_name,
    bool_and(s.is_in_wms) AS sku_in_wms,
    max(od.ordered_qty) AS ordered_qty,
    max(od.ordered_qty_wms) AS ordered_qty_wms,
    sum(od.picked_qty) AS picked_qty,
    sum(od.weight_kg) AS weight_kg,
    max(od.price) AS price,
    max((cur.code)::text) AS currency_code,
    (max(od.price) * COALESCE(sum(od.picked_qty), max(od.ordered_qty))) AS line_sum,
    count(od.batch_number) AS batch_count,
    max(od.line_comment) AS line_comment,
    max((dbo.enum_name('enum_quality'::text, (q.id)::text, (q.name)::text))::text) AS quality,
    max((q.code)::text) AS quality_code
   FROM ((((dbo.order_details od
     LEFT JOIN dbo.sku s ON ((s.sku_id = od.sku_id)))
     LEFT JOIN dbo.enum_uom u ON ((u.id = s.uom_id)))
     LEFT JOIN dbo.enum_currency cur ON ((cur.id = od.currency_id)))
     LEFT JOIN dbo.enum_quality q ON (((q.id = od.quality_id) AND (od.batch_number IS NULL))))
  GROUP BY od.doc_id, od.line_number, od.sku_id, s.code, s.name, u.symbol, u.id;

GRANT SELECT ON v_stock_details, v_receipt_line_details, v_order_line_details,
                v_transfer_line_details, v_order_lines TO portal_app;

-- Слова больше не нужны: всё переведено в quality_id
ALTER TABLE stock            DROP COLUMN quality;
ALTER TABLE order_details    DROP COLUMN quality;
ALTER TABLE receipt_details  DROP COLUMN quality;
ALTER TABLE transfer_details DROP COLUMN quality;

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
-- Галочка «Брак» уходит вместе со своими подписями: вместо неё список.
DELETE FROM ui_text     WHERE text_key IN ('orders.label.defect', 'orders.hint.defect_stock');
DELETE FROM ui_text_key WHERE text_key IN ('orders.label.defect', 'orders.hint.defect_stock');

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('orders.label.quality', 'orders', 'label', NULL,
   'окно ввода строки заказа: выбор качества (V89)'),
  ('orders.hint.quality_stock', 'orders', 'hint', NULL,
   'окно ввода строки заказа: остатки — по выбранному качеству (V89)'),
  ('enums.name.enum_quality', 'admin', 'text', NULL, 'название перечисления (V89)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('orders.label.quality',      'ru', 'Качество'),
    ('orders.label.quality',      'en', 'Quality'),
    ('orders.hint.quality_stock', 'ru', 'Остаток и доступное — по выбранному качеству'),
    ('orders.hint.quality_stock', 'en', 'On hand and available are for the selected quality'),
    ('enums.name.enum_quality',   'ru', 'Качество'),
    ('enums.name.enum_quality',   'en', 'Quality'),
    ('error.line.bad_quality',    'ru', 'Неизвестный код качества «{value}»'),
    ('error.line.bad_quality',    'en', 'Unknown quality code «{value}»')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

UPDATE ui_text_key SET note = 'состав заказа: качество строки (V89 — перечисление)'
 WHERE text_key = 'col.orderComposition.quality';

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  v text;
BEGIN
  FOREACH v IN ARRAY ARRAY['v_stock_available', 'v_stock_lines', 'v_stock_details',
                           'v_receipt_line_details', 'v_order_line_details',
                           'v_transfer_line_details', 'v_order_lines'] LOOP
    IF (SELECT reloptions FROM pg_class WHERE oid = ('dbo.' || v)::regclass)
         IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
      RAISE EXCEPTION '% без security_invoker', v;
    END IF;
  END LOOP;

  PERFORM set_config('app.locale', 'ru', true);
  IF enum_name('enum_quality', '4', 'Quality control') IS DISTINCT FROM 'Контроль качества' THEN
    RAISE EXCEPTION 'нет русского перевода качества 04';
  END IF;
  PERFORM set_config('app.locale', '', true);
END
$check$;
