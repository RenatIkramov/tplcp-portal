-- ---------------------------------------------------------------------
-- V85. Брак в строке заказа
-- ---------------------------------------------------------------------
-- Решение заказчика от 27.10.2026: у строки заказа появляется признак
-- «Брак». Стоит — товар резервируется с качеством «Брак», иначе — годный.
--
-- Где хранится: order_details.quality ПЛАНОВОЙ записи строки (решение Р1,
-- batch_number IS NULL). Значения — те же слова, которыми WMS помечает
-- остаток и которые уже считает v_stock_available.defect_qty:
--   'Брак'    — галочка стоит;
--   'Годный'  — галочки нет. Пишется явно (решение заказчика), а не
--               пустотой: WMS может требовать качество всегда.
-- Какие значения допустимы, проверяет сервер (DraftService); схема не
-- ограничивает колонку, потому что в записях партий качество пишет WMS
-- своими словами («Карантин» и т. п.).
--
-- Одна позиция может быть двумя строками — годная и брак. Повторы
-- объединяются только при совпадении товара, качества и комментария.
--
-- Это подготовка к импорту заказа из Excel: в шаблоне колонка «Брак».
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Качество строки в составе заказа
-- ---------------------------------------------------------------------
-- Колонка дописана в конец. WITH (security_invoker = true) обязателен:
-- замена представления без него сбрасывает параметр (см. V83).
CREATE OR REPLACE VIEW v_order_lines WITH (security_invoker = true) AS
 SELECT od.doc_id,
    od.line_number,
    od.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    u.symbol AS unit_name,
    bool_and(s.is_in_wms) AS sku_in_wms,
    max(od.ordered_qty) AS ordered_qty,
    max(od.ordered_qty_wms) AS ordered_qty_wms,
    sum(od.picked_qty) AS picked_qty,
    sum(od.weight_kg) AS weight_kg,
    max(od.price) AS price,
    max(cur.code::text) AS currency_code,
    max(od.price) * COALESCE(sum(od.picked_qty), max(od.ordered_qty)) AS line_sum,
    count(od.batch_number) AS batch_count,
    max(od.line_comment) AS line_comment,
    max(od.quality::text) FILTER (WHERE od.batch_number IS NULL) AS quality
   FROM order_details od
     LEFT JOIN sku s ON s.sku_id = od.sku_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_currency cur ON cur.id = od.currency_id
  GROUP BY od.doc_id, od.line_number, od.sku_id, s.code, s.name, u.symbol;

-- ---------------------------------------------------------------------
-- 2. Колонка «Качество» в составе заказа
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('col.orderComposition.quality', 'orders', 'column', 'orderComposition',
   'состав заказа: качество строки — годный или брак (V85)'),
  ('orders.label.defect', 'orders', 'label', NULL,
   'окно ввода строки заказа: галочка «Брак» (V85)'),
  ('orders.hint.defect_stock', 'orders', 'hint', NULL,
   'окно ввода строки заказа: при галочке «Брак» остатки — по браку'),
  ('error.line.bad_quality', 'server', 'message', 'documents',
   'сообщение сервера; собирается по ключу в PortalMessages')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('col.orderComposition.quality', 'ru', 'Качество'),
    ('col.orderComposition.quality', 'en', 'Quality'),
    ('orders.label.defect',          'ru', 'Брак'),
    ('orders.label.defect',          'en', 'Defective'),
    ('orders.hint.defect_stock',     'ru', 'Остатки показаны по браку'),
    ('orders.hint.defect_stock',     'en', 'Stock figures are for defective goods'),
    ('error.line.bad_quality',       'ru', 'Качество строки — «Годный» или «Брак», а пришло «{value}»'),
    ('error.line.bad_quality',       'en', 'Unknown line quality «{value}»: a line is either good or defective')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

INSERT INTO table_column
  (table_key, column_id, text_key, object_type_id, field_id,
   sort_order, is_default, is_locked, is_pinned, group_key)
VALUES
  ('orderComposition', 'quality', 'col.orderComposition.quality',
   (SELECT id FROM enum_object_type WHERE code = 'orders'), 'quality',
   12, true, false, false, 'orders.title.order_contents')
ON CONFLICT (table_key, column_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF (SELECT reloptions FROM pg_class WHERE oid = 'dbo.v_order_lines'::regclass)
       IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
    RAISE EXCEPTION 'v_order_lines потеряло security_invoker';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'dbo' AND table_name = 'v_order_lines'
                    AND column_name = 'quality') THEN
    RAISE EXCEPTION 'в v_order_lines нет колонки quality';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM table_column
                  WHERE table_key = 'orderComposition' AND column_id = 'quality') THEN
    RAISE EXCEPTION 'нет колонки «Качество» в составе заказа';
  END IF;
END
$check$;
