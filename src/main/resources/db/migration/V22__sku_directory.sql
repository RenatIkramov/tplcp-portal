-- V22__sku_directory.sql
-- Справочники товаров и упаковок: ведение из портала.
SET search_path = dbo;

-- =====================================================================
-- 1. Архив вместо удаления
-- =====================================================================
-- Товар, на который уже ссылаются документы или остатки, удалять нельзя:
-- пропадёт история. Поэтому основной способ убрать позицию из работы —
-- перевести в архив: она исчезает из выбора в документах, но остаётся
-- в старых записях. Удаление остаётся только для случайно заведённых
-- позиций, нигде не задействованных (проверку делает sku_usage ниже).
ALTER TABLE sku ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN sku.is_active IS
  'false — позиция в архиве: не предлагается в документах, но остаётся в истории';

-- Код товара уникален у поклажедателя и без учёта регистра: «RK-001»
-- и «rk-001» для человека один и тот же код, и два таких товара в списке
-- означали бы ошибку ввода, а не две позиции.
CREATE UNIQUE INDEX IF NOT EXISTS sku_code_ci_uk ON sku (depositor_id, lower(code));

-- =====================================================================
-- 2. Представление товаров: добавляем то, чего не хватало
-- =====================================================================
-- Было: только отображаемые названия (группа, тип, единица). Карточке
-- для правки нужны и сами идентификаторы — иначе в выпадающем списке
-- нечего выбрать. Плюс признак «известен WMS» (решение Р5) и признак
-- архива.
-- CREATE OR REPLACE здесь не подходит: он умеет только дописывать колонки
-- в конец, а нам нужно поставить идентификаторы рядом с названиями.
-- Поэтому пересоздаём; от представления ничего не зависит, это «лист».
DROP VIEW IF EXISTS v_sku_full;
CREATE VIEW v_sku_full AS
SELECT s.sku_id, s.depositor_id, d.org_name AS depositor_name,
       s.item_code, s.code, s.name, s.item_full_name,
       s.barcode, u.symbol AS uom_name, s.uom_id,
       (SELECT p.packaging_code FROM product_packaging p
         WHERE p.packaging_id = s.order_unit_id) AS order_unit_name,
       s.order_unit_id,
       g.name AS item_group_name, s.item_group_id,
       t.name AS item_type_name, s.item_type_id,
       s.has_packaging, s.length, s.width, s.height, s.volume,
       s.weight_gross, s.weight_net,
       s.manufacturer, s.country_origin, s.brand,
       s.is_fragile, s.is_serial_tracked, s.batch_tracking, s.expiry_tracking,
       s.min_storage_temp, s.max_storage_temp, s.min_humidity, s.max_humidity,
       s.min_days_for_receipt, s.min_days_for_reservation, s.shelf_life_days,
       s.color, s.size, s.abc_class, s.xyz_class,
       s.hs_code, s.enstru_code, s.cost, cur.code AS currency_code, s.currency_id,
       s.is_kit, s.is_marking_required, s.is_excise, s.is_controlled, s.is_wms_active,
       hc.name AS hazard_class_name, s.hazard_class_id,
       hg.name AS hazard_group_name, s.hazard_group_id,
       st.name AS status_name, s.comment,
       s.is_active, s.is_in_wms, s.synced_at, s.wms_version,
       s.created_by, s.created_at, s.updated_by, s.updated_at,
       s.custom_attr1, s.custom_attr2, s.custom_attr3, s.custom_attr4, s.custom_attr5,
       s.custom_attr6, s.custom_attr7, s.custom_attr8, s.custom_attr9, s.custom_attr10
FROM sku s
LEFT JOIN depositor d        ON d.depositor_id = s.depositor_id
LEFT JOIN enum_uom u         ON u.id = s.uom_id
LEFT JOIN product_group g    ON g.id = s.item_group_id
LEFT JOIN product_type t     ON t.id = s.item_type_id
LEFT JOIN enum_currency cur  ON cur.id = s.currency_id
LEFT JOIN enum_hazard_class hc ON hc.id = s.hazard_class_id
LEFT JOIN enum_hazard_group hg ON hg.id = s.hazard_group_id
LEFT JOIN enum_status st     ON st.id = s.status_id;

-- CREATE OR REPLACE сбрасывает настройки представления, поэтому
-- security_invoker из V12 ставим заново: представление должно читать
-- таблицы правами приложения, иначе оно обойдёт RLS.
ALTER VIEW v_sku_full SET (security_invoker = true);
GRANT SELECT ON v_sku_full TO portal_app;

-- =====================================================================
-- 3. Представление упаковок
-- =====================================================================
-- Экран «Упаковки» до сих пор работал на демо-данных: представления не было.
CREATE OR REPLACE VIEW v_product_packaging AS
SELECT p.packaging_id, p.depositor_id, p.sku_id,
       s.code AS sku_code, s.name AS sku_name, u.symbol AS uom_name,
       p.packaging_code, p.packaging_type_id, pt.name AS packaging_type_name,
       p.barcode, p.coefficient,
       p.length_cm, p.width_cm, p.height_cm, p.volume_m3, p.gross_weight_kg,
       -- COALESCE обязателен: пока единица заказа не выбрана, order_unit_id
       -- пустой, и сравнение дало бы NULL — «неизвестно» вместо «нет».
       -- Экрану нужен ответ «да/нет», а не третье состояние.
       COALESCE(s.order_unit_id = p.packaging_id, false) AS is_default_order_unit
FROM product_packaging p
LEFT JOIN sku s ON s.sku_id = p.sku_id
LEFT JOIN enum_uom u ON u.id = s.uom_id
LEFT JOIN enum_packaging_type pt ON pt.id = p.packaging_type_id;

ALTER VIEW v_product_packaging SET (security_invoker = true);
GRANT SELECT ON v_product_packaging TO portal_app;

-- Код упаковки уникален в пределах товара — иначе непонятно, какая из двух
-- одинаковых упаковок имелась в виду при пересчёте количества.
CREATE UNIQUE INDEX IF NOT EXISTS packaging_code_uk
  ON product_packaging (sku_id, lower(packaging_code));

-- =====================================================================
-- 4. Где используется товар
-- =====================================================================
-- Нужна перед удалением: вместо «нельзя» портал объясняет, что именно
-- мешает. Функция читает таблицы мимо RLS намеренно — она отвечает только
-- «да/нет и сколько» по конкретному товару и не отдаёт чужих данных.
CREATE OR REPLACE FUNCTION sku_usage(p_sku_id uuid)
RETURNS TABLE (receipts bigint, orders bigint, transfers bigint,
               stock_rows bigint, packagings bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM receipt_details  WHERE sku_id = p_sku_id),
         (SELECT count(*) FROM order_details    WHERE sku_id = p_sku_id),
         (SELECT count(*) FROM transfer_details WHERE sku_id = p_sku_id),
         (SELECT count(*) FROM stock            WHERE sku_id = p_sku_id),
         (SELECT count(*) FROM product_packaging WHERE sku_id = p_sku_id)
$$;
GRANT EXECUTE ON FUNCTION sku_usage(uuid) TO portal_app;

-- =====================================================================
-- 5. Списки для выпадающих списков карточки товара
-- =====================================================================
-- Группы и типы товара заведены по поклажедателям и лежат под RLS,
-- поэтому отдельных функций не нужно — читаются обычным запросом
-- в контексте поклажедателя. Единицы измерения, валюты и классы
-- опасности общие для портала.
CREATE OR REPLACE VIEW v_sku_dictionaries AS
SELECT 'uom' AS kind, u.id::text AS id, u.symbol AS name, u.sort_order
  FROM enum_uom u WHERE u.is_active
UNION ALL
SELECT 'currency', c.id::text, c.code, c.sort_order
  FROM enum_currency c WHERE c.is_active
UNION ALL
SELECT 'hazard_class', h.id::text, h.name, h.sort_order
  FROM enum_hazard_class h WHERE h.is_active
UNION ALL
SELECT 'hazard_group', h.id::text, h.name, h.sort_order
  FROM enum_hazard_group h WHERE h.is_active
UNION ALL
SELECT 'packaging_type', t.id::text, t.name, t.sort_order
  FROM enum_packaging_type t WHERE t.is_active;

-- Для однородности: все представления портала читают таблицы правами
-- приложения. Здесь это ни на что не влияет (перечисления общие и не под
-- RLS), но одинаковое поведение избавляет от вопроса «почему тут иначе».
ALTER VIEW v_sku_dictionaries SET (security_invoker = true);
GRANT SELECT ON v_sku_dictionaries TO portal_app;
