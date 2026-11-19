-- ---------------------------------------------------------------------
-- V91. «Партионный учёт» и «Учёт сроков годности» — логические признаки
-- ---------------------------------------------------------------------
-- Решение заказчика от 29.10.2026: в базе — только логический признак,
-- у него нет языка; «Да»/«Yes» — лишь подпись на экране.
--
-- Зачем. Эти два поля товара пришли из WMS текстом «Да»/«Нет» (V80),
-- и карточка товара писала в них ПОДПИСЬ на языке пользователя. Человек
-- с английским интерфейсом сохранил бы «Yes» — русский увидел бы пустое
-- поле, а в WMS ушло бы «Yes». Теперь это boolean, как остальные
-- признаки товара. Если WMS хранит их текстом, перевод истина ↔ «Да»
-- делает модуль обмена.
--
-- Тип колонки нельзя сменить, пока на неё смотрит представление, —
-- v_sku_full пересоздаётся тем же текстом.
SET search_path = dbo;

-- Незнакомое значение — остановка: угадывать «да» это или «нет» нельзя
DO $words$
DECLARE
  bad text;
BEGIN
  SELECT string_agg(DISTINCT w, ', ') INTO bad
    FROM (SELECT batch_tracking AS w FROM sku UNION ALL SELECT expiry_tracking FROM sku) a
   WHERE NULLIF(btrim(w), '') IS NOT NULL
     AND lower(btrim(w)) NOT IN ('да', 'нет', 'yes', 'no', 'true', 'false', '1', '0');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'учёт партий или сроков: значения, которые не да и не нет: %', bad;
  END IF;
END
$words$;

DROP VIEW v_sku_full;

-- RLS: Flyway ходит суперпользователем; смена типа переписывает всю
-- таблицу и под RLS не подпадает.
ALTER TABLE sku
  ALTER COLUMN batch_tracking TYPE boolean USING
    CASE WHEN lower(btrim(batch_tracking)) IN ('да', 'yes', 'true', '1') THEN true
         WHEN lower(btrim(batch_tracking)) IN ('нет', 'no', 'false', '0') THEN false END,
  ALTER COLUMN expiry_tracking TYPE boolean USING
    CASE WHEN lower(btrim(expiry_tracking)) IN ('да', 'yes', 'true', '1') THEN true
         WHEN lower(btrim(expiry_tracking)) IN ('нет', 'no', 'false', '0') THEN false END;
COMMENT ON COLUMN sku.batch_tracking IS 'учёт партий (V91 — признак, был текстом «Да»/«Нет»)';
COMMENT ON COLUMN sku.expiry_tracking IS 'учёт сроков годности (V91 — признак, был текстом «Да»/«Нет»)';

CREATE VIEW v_sku_full WITH (security_invoker = true) AS
 SELECT s.sku_id,
    s.depositor_id,
    d.org_name AS depositor_name,
    s.item_code,
    s.code,
    s.name,
    s.item_full_name,
    s.barcode,
    (dbo.enum_symbol('enum_uom'::text, (u.id)::text, (u.symbol)::text))::character varying(10) AS uom_name,
    s.uom_id,
    ( SELECT p.packaging_code
           FROM dbo.product_packaging p
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
    (dbo.enum_name('enum_hazard_class'::text, (hc.id)::text, (hc.name)::text))::character varying(255) AS hazard_class_name,
    s.hazard_class_id,
    (dbo.enum_name('enum_hazard_group'::text, (hg.id)::text, (hg.name)::text))::character varying(255) AS hazard_group_name,
    s.hazard_group_id,
    (dbo.enum_name('enum_sku_status'::text, (st.id)::text, (st.name)::text))::character varying(255) AS status_name,
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
   FROM ((((((dbo.sku s
     LEFT JOIN dbo.depositor d ON ((d.depositor_id = s.depositor_id)))
     LEFT JOIN dbo.enum_uom u ON ((u.id = s.uom_id)))
     LEFT JOIN dbo.enum_currency cur ON ((cur.id = s.currency_id)))
     LEFT JOIN dbo.enum_hazard_class hc ON ((hc.id = s.hazard_class_id)))
     LEFT JOIN dbo.enum_hazard_group hg ON ((hg.id = s.hazard_group_id)))
     LEFT JOIN dbo.enum_sku_status st ON ((st.id = s.status_id)));
GRANT SELECT, INSERT, UPDATE, DELETE ON v_sku_full TO portal_app;

-- Фильтр по этим полям — галочка, как у остальных признаков товара
UPDATE table_column SET filter_kind = 'bool'
 WHERE table_key = 'sku' AND column_id IN ('batchTracking', 'expiryTracking');

-- ---------------------------------------------------------------------
-- Тексты: одна пара «да/нет» на весь портал
-- ---------------------------------------------------------------------
-- Прежде их было две: sku.text.yes/no («Да»/«Нет») и portal.text.yes/no
-- («да»/«нет»), и рисовали их разные куски кода. А прототип писал «Да»
-- и «Нет» прямо в коде — на английском интерфейсе они оставались русскими.
-- «нет» внутри фразы («доступны поклажедатели — нет») — это «никого»,
-- а не ответ на вопрос, и у него свой ключ.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.value.yes', 'common', 'text', NULL, 'логическое значение «да» — везде, где показывается признак (V91)'),
  ('common.value.no',  'common', 'text', NULL, 'логическое значение «нет» — везде, где показывается признак (V91)'),
  ('common.text.none', 'common', 'text', NULL, '«нет» внутри фразы: никого, ничего (V91)'),
  ('excel.validation.boolean_title',   'excel', 'text', NULL, 'шаблон Excel: заголовок ошибки в ячейке да/нет (V91)'),
  ('excel.validation.boolean_message', 'excel', 'text', NULL, 'шаблон Excel: текст ошибки в ячейке да/нет (V91)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.value.yes', 'ru', 'Да'),
    ('common.value.yes', 'en', 'Yes'),
    ('common.value.no',  'ru', 'Нет'),
    ('common.value.no',  'en', 'No'),
    ('common.text.none', 'ru', 'нет'),
    ('common.text.none', 'en', 'none'),
    ('excel.validation.boolean_title',   'ru', 'Да или нет'),
    ('excel.validation.boolean_title',   'en', 'Yes or no'),
    ('excel.validation.boolean_message', 'ru', 'Выберите «{yes}» или «{no}».'),
    ('excel.validation.boolean_message', 'en', 'Choose «{yes}» or «{no}».')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

DELETE FROM ui_text     WHERE text_key IN ('sku.text.yes', 'sku.text.no', 'portal.text.yes', 'portal.text.no');
DELETE FROM ui_text_key WHERE text_key IN ('sku.text.yes', 'sku.text.no', 'portal.text.yes', 'portal.text.no');

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF (SELECT reloptions FROM pg_class WHERE oid = 'dbo.v_sku_full'::regclass)
       IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
    RAISE EXCEPTION 'v_sku_full без security_invoker';
  END IF;
  IF (SELECT data_type FROM information_schema.columns
       WHERE table_schema = 'dbo' AND table_name = 'v_sku_full' AND column_name = 'batch_tracking')
       <> 'boolean' THEN
    RAISE EXCEPTION 'v_sku_full.batch_tracking не логический';
  END IF;
END
$check$;
