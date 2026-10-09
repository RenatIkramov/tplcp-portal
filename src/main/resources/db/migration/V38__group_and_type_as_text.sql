-- V38__group_and_type_as_text.sql
-- Группа и тип товара перестают быть справочниками и становятся строками.
-- Единицы измерения получают экран в разделе «Справочники».
--
-- Решение заказчика (09.10.2026): справочники `product_group`
-- и `product_type` удалить, поля в товаре сделать текстовыми.
--
-- ПОРЯДОК ВАЖЕН. Сначала заводим текстовые колонки и переносим в них
-- названия, потом пересобираем представления, и только потом удаляем
-- старые колонки и таблицы. Наоборот — у товаров исчезла бы группа:
-- в `sku` лежит только ссылка, само название живёт в справочнике.
SET search_path = dbo;

-- =====================================================================
-- 1. Текстовые колонки и перенос значений
-- =====================================================================
ALTER TABLE sku
  ADD COLUMN IF NOT EXISTS item_group varchar(255),
  ADD COLUMN IF NOT EXISTS item_type  varchar(255);
COMMENT ON COLUMN sku.item_group IS 'Группа товара (строка; прежде ссылка на product_group)';
COMMENT ON COLUMN sku.item_type  IS 'Тип товара (строка; прежде ссылка на product_type)';

UPDATE sku s SET item_group = g.name
  FROM product_group g WHERE g.id = s.item_group_id AND s.item_group IS NULL;
UPDATE sku s SET item_type = t.name
  FROM product_type t WHERE t.id = s.item_type_id AND s.item_type IS NULL;

-- Проверка переноса: если у товара была ссылка, строка обязана
-- появиться. Иначе миграция тихо обнулила бы данные клиентов.
DO $$
DECLARE
  v_lost_group int;
  v_lost_type  int;
BEGIN
  SELECT count(*) INTO v_lost_group
  FROM sku WHERE item_group_id IS NOT NULL AND item_group IS NULL;
  SELECT count(*) INTO v_lost_type
  FROM sku WHERE item_type_id IS NOT NULL AND item_type IS NULL;
  IF v_lost_group > 0 OR v_lost_type > 0 THEN
    RAISE EXCEPTION 'Перенос не полон: без группы % товаров, без типа %',
      v_lost_group, v_lost_type;
  END IF;
END;
$$;

-- =====================================================================
-- 2. Представления без соединения со справочниками
-- =====================================================================
-- Имена выходных колонок (`item_group_name`, `item_type_name`)
-- сохраняются намеренно: приложение читает именно их, и менять их
-- значило бы править заодно StockLine и чтение справочника товаров
-- без всякой на то причины. Пропадают только `item_group_id`
-- и `item_type_id` — ссылаться больше не на что.
-- DROP, а не CREATE OR REPLACE: из представления уходят колонки
-- item_group_id и item_type_id, а заменой колонки убрать нельзя —
-- Postgres отвечает «cannot drop columns from view». Зависимых
-- представлений у v_sku_full нет, проверено.
DROP VIEW IF EXISTS v_sku_full;
CREATE VIEW v_sku_full AS
SELECT s.sku_id,
    s.depositor_id,
    d.org_name AS depositor_name,
    s.item_code,
    s.code,
    s.name,
    s.item_full_name,
    s.barcode,
    u.symbol AS uom_name,
    s.uom_id,
    ( SELECT p.packaging_code
           FROM product_packaging p
          WHERE p.packaging_id = s.order_unit_id) AS order_unit_name,
    s.order_unit_id,
    s.item_group AS item_group_name,
    s.item_type  AS item_type_name,
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
    hc.name AS hazard_class_name,
    s.hazard_class_id,
    hg.name AS hazard_group_name,
    s.hazard_group_id,
    st.name AS status_name,
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
   FROM sku s
     LEFT JOIN depositor d ON d.depositor_id = s.depositor_id
     LEFT JOIN enum_uom u ON u.id = s.uom_id
     LEFT JOIN enum_currency cur ON cur.id = s.currency_id
     LEFT JOIN enum_hazard_class hc ON hc.id = s.hazard_class_id
     LEFT JOIN enum_hazard_group hg ON hg.id = s.hazard_group_id
     LEFT JOIN enum_status st ON st.id = s.status_id;

-- CREATE OR REPLACE VIEW сбрасывает настройки представления, поэтому
-- security_invoker ставим заново: представление должно читать таблицы
-- правами приложения, иначе оно обойдёт RLS.
ALTER VIEW v_sku_full SET (security_invoker = true);

CREATE OR REPLACE VIEW v_stock_lines AS
 SELECT st.depositor_id,
    st.sku_id,
    s.code AS sku_code,
    s.name AS sku_name,
    s.item_group AS item_group_name,
    st.warehouse_id,
    w.name AS warehouse_name,
    u.symbol AS unit_name,
    sum(st.quantity) AS quantity,
    sum(st.reserved_qty) AS reserved_qty,
    sum(st.blocked_qty) AS blocked_qty,
    sum(st.available_qty) AS available_qty,
    sum(
        CASE
            WHEN st.quality::text = 'Брак'::text THEN st.quantity
            ELSE 0::numeric
        END) AS defect_qty,
    COALESCE(inc.incoming_qty, 0::numeric) AS incoming_qty,
    count(*) AS batch_count,
    max(st.synced_at) AS synced_at
   FROM stock st
     LEFT JOIN sku s ON s.sku_id = st.sku_id
     LEFT JOIN warehouse w ON w.warehouse_id::text = st.warehouse_id::text
     LEFT JOIN enum_uom u ON u.id = st.unit_id
     LEFT JOIN LATERAL ( SELECT sum(rd.expected_qty) AS incoming_qty
           FROM receipt_details rd
             JOIN receipts r ON r.doc_id = rd.doc_id
             JOIN enum_status es ON es.id = r.status_id
          WHERE rd.sku_id = st.sku_id
            AND r.warehouse_id::text = st.warehouse_id::text
            AND rd.batch_number IS NULL
            AND (es.code::text <> ALL (ARRAY['draft'::character varying,
                                             'received'::character varying]::text[]))) inc ON true
  GROUP BY st.depositor_id, st.sku_id, s.code, s.name, s.item_group,
           st.warehouse_id, w.name, u.symbol, inc.incoming_qty;

ALTER VIEW v_stock_lines SET (security_invoker = true);

GRANT SELECT ON v_sku_full TO portal_app;
GRANT SELECT ON v_stock_lines TO portal_app;

-- =====================================================================
-- 3. Прощание со справочниками
-- =====================================================================
-- Значения уже перенесены и проверены выше, представления на таблицы
-- больше не ссылаются.
ALTER TABLE sku DROP COLUMN IF EXISTS item_group_id;
ALTER TABLE sku DROP COLUMN IF EXISTS item_type_id;

DROP TABLE IF EXISTS product_group;
DROP TABLE IF EXISTS product_type;

-- =====================================================================
-- 4. Справочник единиц измерения: экран в «Справочниках»
-- =====================================================================
-- enum_uom общая для всех поклажедателей и не под RLS — так и было
-- задумано: единица измерения «шт» одна на весь портал. Поэтому
-- править её вправе только администратор, иначе один клиент
-- переименует её всем, и откатить будет некому. Право проверяется
-- в приложении, как и для складов с поклажедателями.

-- Новым единицам нужен идентификатор. Функция намеренно работает
-- по белому списку: подставить в неё произвольное имя таблицы было бы
-- дыркой, а не удобством. Добавляем enum_uom к двум прежним.
CREATE OR REPLACE FUNCTION next_enum_id(p_table text)
RETURNS smallint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE v_id smallint;
BEGIN
  IF p_table NOT IN ('enum_order_type', 'enum_receipt_doc_type', 'enum_uom') THEN
    RAISE EXCEPTION 'Неизвестное перечисление: %', p_table;
  END IF;
  EXECUTE format('SELECT COALESCE(max(id), 0) + 1 FROM %I', p_table) INTO v_id;
  RETURN v_id;
END;
$$;
GRANT EXECUTE ON FUNCTION next_enum_id(text) TO portal_app;

CREATE OR REPLACE VIEW v_units AS
SELECT u.id::text        AS unit_id,
       u.mkei_code,
       u.symbol,
       u.name,
       u.sort_order,
       COALESCE(u.is_active, true) AS is_active
FROM enum_uom u
ORDER BY u.sort_order NULLS LAST, u.symbol;
ALTER VIEW v_units SET (security_invoker = true);
GRANT SELECT ON v_units TO portal_app;

-- Где используется единица: товары и запасы. Нужно до удаления —
-- используемую единицу удалять нельзя, предлагаем архив, как во всех
-- остальных справочниках портала.
--
-- SECURITY DEFINER: считать надо по ВСЕМ поклажедателям, а не только
-- по текущему. Под RLS администратор увидел бы ноль и решил, что
-- единица свободна.
CREATE OR REPLACE FUNCTION unit_usage(p_unit_id smallint)
RETURNS TABLE (sku_count int, stock_count int)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*)::int FROM sku WHERE uom_id = p_unit_id),
         (SELECT count(*)::int FROM stock WHERE unit_id = p_unit_id);
$$;
GRANT EXECUTE ON FUNCTION unit_usage(smallint) TO portal_app;

-- =====================================================================
-- 5. Тексты
-- =====================================================================
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('references.button.units',              'references', 'button', NULL,
   'вкладка справочника единиц измерения'),
  ('references.title.units',               'references', 'title',  NULL,
   'справочник единиц измерения'),
  ('references.hint.units',                'references', 'hint',   NULL,
   'справочник единиц измерения: общий для всех клиентов'),
  ('references.column.mkei_code',          'references', 'column', NULL,
   'код МКЕИ единицы измерения'),
  ('references.column.unit_symbol',        'references', 'column', NULL,
   'краткое обозначение единицы'),
  ('references.hint.column_cannot_be_hidden', 'references', 'hint', NULL,
   'подсказка у серой галочки прикреплённой колонки'),
  ('references.label.item_group_text',     'references', 'label',  NULL,
   'подсказка к полю группы товара после отказа от справочника')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('references.button.units',              'ru', 'Единицы измерения'),
  ('references.button.units',              'en', 'Units of measure'),
  ('references.title.units',               'ru', 'Единицы измерения'),
  ('references.title.units',               'en', 'Units of measure'),
  ('references.hint.units',                'ru',
   'Справочник общий для всех поклажедателей, поэтому править его вправе только администратор. Используемую единицу нельзя удалить — её можно перевести в архив.'),
  ('references.hint.units',                'en',
   'This directory is shared by all depositors, so only an administrator may change it. A unit in use cannot be deleted — it can be archived instead.'),
  ('references.column.mkei_code',          'ru', 'Код МКЕИ'),
  ('references.column.mkei_code',          'en', 'MKEI code'),
  ('references.column.unit_symbol',        'ru', 'Обозначение'),
  ('references.column.unit_symbol',        'en', 'Symbol'),
  ('references.hint.column_cannot_be_hidden', 'ru',
   'Эту колонку убрать нельзя — без неё список бесполезен'),
  ('references.hint.column_cannot_be_hidden', 'en',
   'This column cannot be removed — the list is useless without it'),
  ('references.label.item_group_text',     'ru',
   'Группа и тип товара — свободный текст: справочника больше нет'),
  ('references.label.item_group_text',     'en',
   'Item group and type are free text: the directory is gone')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- =====================================================================
-- 6. Экран проверки загрузки: значения в человеческом виде
-- =====================================================================
-- В cell_values у ссылочных полей лежит идентификатор — то, что пойдёт
-- в базу при применении. Экрану он не нужен: человек, сверяя файл,
-- должен видеть «шт», а не «1». Складывать одно в другое нельзя —
-- применение перестало бы работать, а разбирать названия обратно
-- на экране означало бы второй раз пройти весь справочник.
--
-- Поэтому рядом ещё одна колонка: то же самое, но как показывать.
-- Пишется при разборе, когда названия уже прочитаны, и только для
-- тех полей, где показ отличается от значения (ссылки и «да/нет»).
ALTER TABLE excel_import_row
  ADD COLUMN IF NOT EXISTS cell_display jsonb NOT NULL DEFAULT '{}'::jsonb;

-- CREATE OR REPLACE VIEW умеет добавлять колонки только в конец —
-- поэтому cell_display стоит последней, а не рядом с cell_values.
CREATE OR REPLACE VIEW v_excel_import_rows AS
SELECT
  r.import_id,
  r.row_number,
  r.row_class,
  r.is_valid,
  r.applied,
  r.row_key,
  r.sku_id::text    AS sku_id,
  r.cell_values,
  r.fill_fields,
  COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
             'columnId',    i.column_id,
             'kind',        i.kind,
             'message',     i.message,
             'fileValue',   i.file_value,
             'portalValue', i.portal_value)
           ORDER BY i.id)
    FROM excel_import_issue i
    WHERE i.import_id = r.import_id AND i.row_number = r.row_number
  ), '[]'::jsonb) AS issues,
  r.cell_display
FROM excel_import_row r
ORDER BY r.row_number;

ALTER VIEW v_excel_import_rows SET (security_invoker = true);
GRANT SELECT ON v_excel_import_rows TO portal_app;
