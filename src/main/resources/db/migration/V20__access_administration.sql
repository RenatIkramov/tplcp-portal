-- V20__access_administration.sql
-- Доступы: кладовщик → склады, поклажедатель → склады.
-- Доступы менеджера к поклажедателям уже умеет V19 (admin_set_depositor_access).
SET search_path = dbo;

-- =====================================================================
-- 1. Склады, на которых работает поклажедатель
-- =====================================================================
-- Такой таблицы в схеме не было: склады раздавались всем. А поклажедатель,
-- который хранится только в Алматы, не должен видеть Караганду в выпадающем
-- списке документа — он не сможет туда ничего привезти.
CREATE TABLE IF NOT EXISTS depositor_warehouse (
  depositor_id uuid        NOT NULL REFERENCES depositor(depositor_id),
  warehouse_id varchar(20) NOT NULL REFERENCES warehouse(warehouse_id),
  PRIMARY KEY (depositor_id, warehouse_id)
);
COMMENT ON TABLE depositor_warehouse IS
  'Склады, доступные поклажедателю. Пусто у поклажедателя — доступны все склады';

-- Первое заполнение по факту: где у поклажедателя уже есть запасы или
-- документы, тот склад ему и доступен. Так настройка не обнуляет работу
-- действующих клиентов.
INSERT INTO depositor_warehouse (depositor_id, warehouse_id)
SELECT DISTINCT s.depositor_id, s.warehouse_id FROM stock s
WHERE s.warehouse_id IS NOT NULL
ON CONFLICT DO NOTHING;
INSERT INTO depositor_warehouse (depositor_id, warehouse_id)
SELECT DISTINCT r.depositor_id, r.warehouse_id FROM receipts r
WHERE r.warehouse_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Склады в портале фильтруются по этой таблице. Пустой список у
-- поклажедателя означает «ограничений нет» — иначе забытая настройка
-- оставила бы клиента вообще без складов и он не создал бы документ.
CREATE OR REPLACE VIEW v_warehouses AS
SELECT w.warehouse_id, w.code, w.name, w.address, w.responsible, w.phone, w.email
FROM warehouse w
WHERE NOT EXISTS (SELECT 1 FROM depositor_warehouse dw
                   WHERE dw.depositor_id = current_depositor_id())
   OR EXISTS (SELECT 1 FROM depositor_warehouse dw
               WHERE dw.depositor_id = current_depositor_id()
                 AND dw.warehouse_id = w.warehouse_id);

-- CREATE OR REPLACE VIEW сбрасывает настройки представления, поэтому
-- security_invoker из V12 ставим заново: представление должно читать
-- таблицы правами приложения, а не владельца схемы, иначе оно обойдёт RLS.
ALTER VIEW v_warehouses SET (security_invoker = true);

-- Представления в портале с security_invoker, то есть читают таблицы правами
-- приложения: без этого права v_warehouses вернул бы ошибку доступа.
GRANT SELECT ON depositor_warehouse TO portal_app;
GRANT SELECT ON v_warehouses TO portal_app;

-- =====================================================================
-- 2. Склады кладовщика
-- =====================================================================
CREATE OR REPLACE FUNCTION admin_user_warehouses(p_user_id uuid)
RETURNS TABLE (warehouse_id varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT warehouse_id FROM user_warehouse_access WHERE user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION admin_user_warehouses(uuid) TO portal_app;

-- Набор заменяется целиком, как и доступы к поклажедателям (V19).
CREATE OR REPLACE FUNCTION admin_set_warehouse_access(p_user_id uuid, p_warehouse_ids text[])
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  DELETE FROM user_warehouse_access
   WHERE user_id = p_user_id
     AND NOT (warehouse_id = ANY (coalesce(p_warehouse_ids, '{}'::text[])));

  INSERT INTO user_warehouse_access (user_id, warehouse_id)
  SELECT p_user_id, w
  FROM unnest(coalesce(p_warehouse_ids, '{}'::text[])) AS w
  WHERE EXISTS (SELECT 1 FROM warehouse x WHERE x.warehouse_id = w)
  ON CONFLICT (user_id, warehouse_id) DO NOTHING;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_set_warehouse_access(uuid, text[]) TO portal_app;

-- =====================================================================
-- 3. Склады поклажедателя
-- =====================================================================
CREATE OR REPLACE FUNCTION admin_depositor_warehouses(p_depositor_id uuid)
RETURNS TABLE (warehouse_id varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT warehouse_id FROM depositor_warehouse WHERE depositor_id = p_depositor_id
$$;
GRANT EXECUTE ON FUNCTION admin_depositor_warehouses(uuid) TO portal_app;

CREATE OR REPLACE FUNCTION admin_set_depositor_warehouses(p_depositor_id uuid,
                                                          p_warehouse_ids text[])
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  DELETE FROM depositor_warehouse
   WHERE depositor_id = p_depositor_id
     AND NOT (warehouse_id = ANY (coalesce(p_warehouse_ids, '{}'::text[])));

  INSERT INTO depositor_warehouse (depositor_id, warehouse_id)
  SELECT p_depositor_id, w
  FROM unnest(coalesce(p_warehouse_ids, '{}'::text[])) AS w
  WHERE EXISTS (SELECT 1 FROM warehouse x WHERE x.warehouse_id = w)
  ON CONFLICT DO NOTHING;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_set_depositor_warehouses(uuid, text[]) TO portal_app;

-- =====================================================================
-- 4. Списки для экранов доступов
-- =====================================================================
CREATE OR REPLACE FUNCTION admin_warehouses()
RETURNS TABLE (warehouse_id varchar, code varchar, name varchar, address varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT warehouse_id, code, name, address FROM warehouse ORDER BY name
$$;
GRANT EXECUTE ON FUNCTION admin_warehouses() TO portal_app;

-- Поклажедатели со списком своих складов — для экрана «Склады поклажедателей».
CREATE OR REPLACE FUNCTION admin_depositor_access()
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar, warehouses text)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT d.depositor_id, d.code, d.org_name,
         COALESCE((SELECT string_agg(w.name, ', ' ORDER BY w.name)
                     FROM depositor_warehouse dw
                     JOIN warehouse w ON w.warehouse_id = dw.warehouse_id
                    WHERE dw.depositor_id = d.depositor_id), '') AS warehouses
  FROM depositor d
  ORDER BY d.org_name
$$;
GRANT EXECUTE ON FUNCTION admin_depositor_access() TO portal_app;
