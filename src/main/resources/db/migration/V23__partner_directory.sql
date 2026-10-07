-- V23__partner_directory.sql
-- Справочники контрагентов: поставщики, грузополучатели и адреса доставки.
-- Устроено так же, как справочник товаров (V22): архив вместо удаления,
-- уникальный код без учёта регистра, удаление только для неиспользуемых.
SET search_path = dbo;

-- =====================================================================
-- 1. Архив
-- =====================================================================
ALTER TABLE supplier  ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
ALTER TABLE consignee ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN supplier.is_active IS
  'false — контрагент в архиве: не предлагается в документах, но остаётся в истории';
COMMENT ON COLUMN consignee.is_active IS
  'false — контрагент в архиве: не предлагается в документах, но остаётся в истории';

-- Коды уже уникальны у поклажедателя (V11), но с учётом регистра: «ПОСТ-1»
-- и «пост-1» проходили как два разных. Для человека это один код, поэтому
-- добавляем регистронезависимое ограничение рядом со старым.
CREATE UNIQUE INDEX IF NOT EXISTS supplier_code_ci_uk  ON supplier  (depositor_id, lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS consignee_code_ci_uk ON consignee (depositor_id, lower(code));

-- Адрес у грузополучателя не повторяется: две одинаковые строки в списке
-- означают ошибку ввода, а не два места доставки.
CREATE UNIQUE INDEX IF NOT EXISTS delivery_point_uk
  ON delivery_point (consignee_id, lower(address));

-- Основной адрес ровно один. Частичный индекс — ровно то средство: он
-- ничего не говорит про строки с is_default = false и NULL, и запрещает
-- вторую «галочку» на уровне базы, а не на уровне внимательности портала.
CREATE UNIQUE INDEX IF NOT EXISTS delivery_point_default_uk
  ON delivery_point (consignee_id) WHERE is_default;

-- =====================================================================
-- 2. Представления
-- =====================================================================
-- Добавляем признак архива; в грузополучателе — ещё идентификатор
-- основного адреса и число адресов (список показывает его столбцом).
-- CREATE OR REPLACE умеет только дописывать колонки в конец, а признак
-- архива нужен рядом с остальными — пересоздаём.
DROP VIEW IF EXISTS v_suppliers;
CREATE VIEW v_suppliers AS
SELECT s.supplier_id, s.depositor_id, s.code, s.name, s.bin, s.legal_address,
       s.contact_name, s.contact_phone, s.contact_email, s.is_active
FROM supplier s;
ALTER VIEW v_suppliers SET (security_invoker = true);
GRANT SELECT ON v_suppliers TO portal_app;

DROP VIEW IF EXISTS v_consignees;
CREATE VIEW v_consignees AS
SELECT c.consignee_id, c.depositor_id, c.code, c.name, c.bin, c.legal_address,
       c.contact_name, c.contact_phone, c.contact_email, c.is_active,
       (SELECT dp.address FROM delivery_point dp
         WHERE dp.consignee_id = c.consignee_id AND dp.is_default
         LIMIT 1) AS default_address,
       (SELECT dp.address_id FROM delivery_point dp
         WHERE dp.consignee_id = c.consignee_id AND dp.is_default
         LIMIT 1) AS default_address_id,
       (SELECT count(*) FROM delivery_point dp
         WHERE dp.consignee_id = c.consignee_id) AS address_count
FROM consignee c;
ALTER VIEW v_consignees SET (security_invoker = true);
GRANT SELECT ON v_consignees TO portal_app;

-- Адреса доставки: до сих пор отдельного представления не было, адрес
-- был виден только как «основной» в карточке грузополучателя.
CREATE OR REPLACE VIEW v_delivery_points AS
SELECT dp.address_id, dp.consignee_id, c.code AS consignee_code, c.name AS consignee_name,
       dp.address, COALESCE(dp.is_default, false) AS is_default, dp.sort_order
FROM delivery_point dp
LEFT JOIN consignee c ON c.consignee_id = dp.consignee_id;
ALTER VIEW v_delivery_points SET (security_invoker = true);
GRANT SELECT ON v_delivery_points TO portal_app;

-- =====================================================================
-- 3. Где используется контрагент
-- =====================================================================
-- Нужны перед удалением: портал объясняет, что именно мешает, вместо
-- короткого «нельзя». Читают мимо RLS намеренно — отвечают только
-- «сколько» по конкретному идентификатору и чужих данных не отдают.
CREATE OR REPLACE FUNCTION supplier_usage(p_supplier_id uuid)
RETURNS TABLE (receipts bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM receipts WHERE supplier_id = p_supplier_id)
$$;
GRANT EXECUTE ON FUNCTION supplier_usage(uuid) TO portal_app;

CREATE OR REPLACE FUNCTION consignee_usage(p_consignee_id uuid)
RETURNS TABLE (orders bigint, addresses bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM orders WHERE consignee_id = p_consignee_id),
         (SELECT count(*) FROM delivery_point WHERE consignee_id = p_consignee_id)
$$;
GRANT EXECUTE ON FUNCTION consignee_usage(uuid) TO portal_app;

-- Адрес удаляют отдельно от грузополучателя, и у него своя помеха:
-- заказы, в которых он указан местом доставки.
CREATE OR REPLACE FUNCTION delivery_point_usage(p_address_id uuid)
RETURNS TABLE (orders bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM orders WHERE delivery_point_id = p_address_id)
$$;
GRANT EXECUTE ON FUNCTION delivery_point_usage(uuid) TO portal_app;

-- =====================================================================
-- 4. Выбор основного адреса
-- =====================================================================
-- Одной операцией: снять прежний и поставить новый. Делать это двумя
-- запросами из приложения нельзя — между ними база на мгновение видит
-- два основных адреса, и частичный индекс выше честно откажет.
CREATE OR REPLACE FUNCTION set_default_delivery_point(p_address_id uuid)
RETURNS void
-- Без SECURITY DEFINER: функция меняет данные правами вызывающего, то есть
-- под теми же политиками RLS. search_path задаём явно — у роли приложения
-- своего нет, и внутри функции имена таблиц иначе не находятся.
LANGUAGE sql SET search_path = dbo AS $$
  UPDATE delivery_point SET is_default = false
   WHERE consignee_id = (SELECT consignee_id FROM delivery_point WHERE address_id = p_address_id)
     AND is_default;
  UPDATE delivery_point SET is_default = true WHERE address_id = p_address_id;
$$;
GRANT EXECUTE ON FUNCTION set_default_delivery_point(uuid) TO portal_app;
