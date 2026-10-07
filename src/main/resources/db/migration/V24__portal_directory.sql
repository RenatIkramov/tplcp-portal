-- V24__portal_directory.sql
-- Справочники уровня портала: склады, поклажедатели, типы заказов
-- и типы приходных документов. Общие для всех клиентов, ведёт их
-- только администратор.
SET search_path = dbo;

-- =====================================================================
-- 1. Архив
-- =====================================================================
-- Склад и поклажедателя удалять почти никогда нельзя: на них ссылается
-- вся история. Рабочий способ убрать из обращения — архив.
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
ALTER TABLE depositor ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN warehouse.is_active IS
  'false — склад в архиве: не предлагается в документах, но остаётся в истории';
COMMENT ON COLUMN depositor.is_active IS
  'false — поклажедатель в архиве: его пользователи не войдут, данные остаются';

-- Коды уникальны без учёта регистра. У склада их два: сам идентификатор
-- (он же код в WMS) и отдельная колонка code — проверяем видимый.
CREATE UNIQUE INDEX IF NOT EXISTS warehouse_code_ci_uk ON warehouse (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS depositor_code_ci_uk ON depositor (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS order_type_code_ci_uk ON enum_order_type (lower(code));
CREATE UNIQUE INDEX IF NOT EXISTS receipt_doc_type_code_ci_uk ON enum_receipt_doc_type (lower(code));

-- =====================================================================
-- 2. Склады: видимость и архив
-- =====================================================================
-- v_warehouses (V20) отдаёт только склады, разрешённые поклажедателю.
-- Теперь он ещё и скрывает архивные: склад, выведенный из обращения,
-- не должен предлагаться в новых документах.
DROP VIEW IF EXISTS v_warehouses;
CREATE VIEW v_warehouses AS
SELECT w.warehouse_id, w.code, w.name, w.address, w.responsible, w.phone, w.email, w.is_active
FROM warehouse w
WHERE w.is_active
  AND (NOT EXISTS (SELECT 1 FROM depositor_warehouse dw
                    WHERE dw.depositor_id = current_depositor_id())
       OR EXISTS (SELECT 1 FROM depositor_warehouse dw
                   WHERE dw.depositor_id = current_depositor_id()
                     AND dw.warehouse_id = w.warehouse_id));
ALTER VIEW v_warehouses SET (security_invoker = true);
GRANT SELECT ON v_warehouses TO portal_app;

-- Экрану администратора нужны все склады, включая архивные и те, что
-- не выданы текущему поклажедателю: он ведёт справочник, а не работает
-- с документами.
CREATE OR REPLACE VIEW v_warehouses_all AS
SELECT w.warehouse_id, w.code, w.name, w.address, w.responsible, w.phone, w.email, w.is_active
FROM warehouse w;
ALTER VIEW v_warehouses_all SET (security_invoker = true);
GRANT SELECT ON v_warehouses_all TO portal_app;

-- =====================================================================
-- 3. Поклажедатели
-- =====================================================================
-- Таблица под RLS: обычный пользователь видит только своего клиента.
-- Администратор ведёт их всех, поэтому чтение и запись идут через
-- функции с правами владельца — право на это проверяет портал
-- (роль admin), как и в остальном администрировании.
CREATE OR REPLACE VIEW v_depositors AS
SELECT d.depositor_id, d.code, d.org_name, d.bin, d.legal_address, d.phone, d.email,
       d.contract_number, d.contract_valid_until, d.operator_id, o.name AS operator_name,
       d.is_active
FROM depositor d
LEFT JOIN operator o ON o.operator_id = d.operator_id;
ALTER VIEW v_depositors SET (security_invoker = true);
GRANT SELECT ON v_depositors TO portal_app;

CREATE OR REPLACE FUNCTION admin_depositor_list()
RETURNS TABLE (depositor_id text, code varchar, org_name varchar, bin varchar,
               legal_address varchar, phone varchar, email varchar,
               contract_number varchar, contract_valid_until date,
               is_active boolean, user_count bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT d.depositor_id::text, d.code, d.org_name, d.bin, d.legal_address, d.phone, d.email,
         d.contract_number, d.contract_valid_until, d.is_active,
         (SELECT count(*) FROM user_depositor_access a WHERE a.depositor_id = d.depositor_id)
    FROM depositor d
   ORDER BY d.code
$$;
GRANT EXECUTE ON FUNCTION admin_depositor_list() TO portal_app;

CREATE OR REPLACE FUNCTION admin_depositor_card(p_depositor_id uuid)
RETURNS TABLE (depositor_id text, code varchar, org_name varchar, bin varchar,
               legal_address varchar, phone varchar, email varchar,
               contract_number varchar, contract_valid_until date,
               is_active boolean, user_count bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT * FROM admin_depositor_list() WHERE depositor_id = p_depositor_id::text
$$;
GRANT EXECUTE ON FUNCTION admin_depositor_card(uuid) TO portal_app;

CREATE OR REPLACE FUNCTION admin_save_depositor(
  p_depositor_id uuid,          -- NULL — создание
  p_code text, p_org_name text, p_bin text, p_legal_address text,
  p_phone text, p_email text, p_contract_number text, p_contract_valid_until date)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE v_id uuid;
BEGIN
  IF p_depositor_id IS NULL THEN
    INSERT INTO depositor (code, org_name, bin, legal_address, phone, email,
                           contract_number, contract_valid_until, is_active)
    VALUES (p_code, p_org_name, p_bin, p_legal_address, p_phone, p_email,
            p_contract_number, p_contract_valid_until, true)
    RETURNING depositor_id INTO v_id;
  ELSE
    UPDATE depositor
       SET code = p_code, org_name = p_org_name, bin = p_bin,
           legal_address = p_legal_address, phone = p_phone, email = p_email,
           contract_number = p_contract_number, contract_valid_until = p_contract_valid_until
     WHERE depositor_id = p_depositor_id
    RETURNING depositor_id INTO v_id;
  END IF;
  RETURN v_id::text;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_save_depositor(uuid, text, text, text, text, text, text, text, date)
  TO portal_app;

CREATE OR REPLACE FUNCTION admin_set_depositor_active(p_depositor_id uuid, p_active boolean)
RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  UPDATE depositor SET is_active = p_active WHERE depositor_id = p_depositor_id;
$$;
GRANT EXECUTE ON FUNCTION admin_set_depositor_active(uuid, boolean) TO portal_app;

CREATE OR REPLACE FUNCTION admin_delete_depositor(p_depositor_id uuid)
RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  DELETE FROM depositor WHERE depositor_id = p_depositor_id;
$$;
GRANT EXECUTE ON FUNCTION admin_delete_depositor(uuid) TO portal_app;

-- =====================================================================
-- 4. Где используется
-- =====================================================================
CREATE OR REPLACE FUNCTION warehouse_usage(p_warehouse_id varchar)
RETURNS TABLE (receipts bigint, orders bigint, transfers bigint,
               stock_rows bigint, depositors bigint, staff bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM receipts  WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM orders    WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM transfers WHERE from_warehouse_id = p_warehouse_id
                                            OR to_warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM stock     WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM depositor_warehouse WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM user_warehouse_access WHERE warehouse_id = p_warehouse_id)
$$;
GRANT EXECUTE ON FUNCTION warehouse_usage(varchar) TO portal_app;

CREATE OR REPLACE FUNCTION depositor_usage(p_depositor_id uuid)
RETURNS TABLE (users bigint, items bigint, receipts bigint, orders bigint, stock_rows bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM user_depositor_access WHERE depositor_id = p_depositor_id),
         (SELECT count(*) FROM sku       WHERE depositor_id = p_depositor_id),
         (SELECT count(*) FROM receipts  WHERE depositor_id = p_depositor_id),
         (SELECT count(*) FROM orders    WHERE depositor_id = p_depositor_id),
         (SELECT count(*) FROM stock     WHERE depositor_id = p_depositor_id)
$$;
GRANT EXECUTE ON FUNCTION depositor_usage(uuid) TO portal_app;

-- Типы приходных документов и типы заказов — короткие перечисления,
-- помехой удалению служат только документы, которые на них ссылаются.
CREATE OR REPLACE FUNCTION order_type_usage(p_id smallint)
RETURNS TABLE (orders bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM orders WHERE order_type_id = p_id)
$$;
GRANT EXECUTE ON FUNCTION order_type_usage(smallint) TO portal_app;

CREATE OR REPLACE FUNCTION receipt_doc_type_usage(p_id smallint)
RETURNS TABLE (receipts bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM receipts WHERE doc_type_id = p_id)
$$;
GRANT EXECUTE ON FUNCTION receipt_doc_type_usage(smallint) TO portal_app;

-- =====================================================================
-- 5. Новый номер в перечислении
-- =====================================================================
-- У перечислений ключ — маленькое число без автонумерации: значения
-- пришли из WMS и там зафиксированы. Новым строкам даём следующий
-- свободный номер, а не придумываем в приложении.
CREATE OR REPLACE FUNCTION next_enum_id(p_table text)
RETURNS smallint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE v_id smallint;
BEGIN
  IF p_table NOT IN ('enum_order_type', 'enum_receipt_doc_type') THEN
    RAISE EXCEPTION 'Неизвестное перечисление: %', p_table;
  END IF;
  EXECUTE format('SELECT COALESCE(max(id), 0) + 1 FROM %I', p_table) INTO v_id;
  RETURN v_id;
END;
$$;
GRANT EXECUTE ON FUNCTION next_enum_id(text) TO portal_app;
