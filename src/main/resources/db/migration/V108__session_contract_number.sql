SET search_path = dbo;

-- Номер договора поклажедателя — в сеансе (11.11.2026).
--
-- Карточка документа показывает «Номер договора», но брала его из
-- пустого демо-справочника прототипа и всегда писала прочерк. Номер
-- в базе есть (depositor.contract_number), а прочитать его портал под
-- portal_app не может: depositor под RLS, и до выбора клиента строк
-- не видно. Сеанс уже получает список доступных поклажедателей этой
-- функцией (SECURITY DEFINER) — номер договора едет тем же списком.
--
-- CREATE OR REPLACE не меняет набор возвращаемых колонок — функцию
-- пересоздаём. user_may_access_depositor вызывает её по имени из тела
-- (LANGUAGE sql без атомарного тела), зависимости в каталоге нет,
-- пересоздание её не задевает. Новая колонка — последней: прежние
-- вызовы читают колонки по имени.

DROP FUNCTION depositors_for_user(text);

CREATE FUNCTION depositors_for_user(p_login text)
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar, contract_number varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT DISTINCT d.depositor_id, d.code, d.org_name, d.contract_number
  FROM app_user u
  JOIN enum_user_role r ON r.id = u.role_id
  LEFT JOIN user_depositor_access uda ON uda.user_id = u.user_id
  LEFT JOIN user_warehouse_access uwa ON uwa.user_id = u.user_id
  JOIN depositor d ON (
        d.depositor_id = uda.depositor_id
     OR (r.code = 'staff' AND EXISTS (
           SELECT 1 FROM stock s
           WHERE s.depositor_id = d.depositor_id
             AND s.warehouse_id = uwa.warehouse_id))
  )
  WHERE u.login = p_login
    AND u.is_active                      -- §6.2: заблокированный не видит ничего
  ORDER BY d.org_name
$$;

GRANT EXECUTE ON FUNCTION depositors_for_user(text) TO portal_app;

-- Проверка: функция на месте, с правом для приложения и с новой колонкой.
DO $$
BEGIN
  IF NOT has_function_privilege('portal_app', 'dbo.depositors_for_user(text)', 'EXECUTE') THEN
    RAISE EXCEPTION 'portal_app не может вызвать depositors_for_user';
  END IF;
  IF NOT EXISTS (
      SELECT 1 FROM pg_proc p
       WHERE p.oid = 'dbo.depositors_for_user(text)'::regprocedure
         AND 'contract_number' = ANY (p.proargnames)) THEN
    RAISE EXCEPTION 'у depositors_for_user нет колонки contract_number';
  END IF;
END $$;
