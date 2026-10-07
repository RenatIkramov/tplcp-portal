-- V14__dev_helpers.sql
-- Служебная функция для разработки: список всех поклажедателей.
-- Прямой SELECT из depositor под ролью приложения ничего не вернёт —
-- таблица под RLS. SECURITY DEFINER обходит политики.
--
-- Удалить вместе с переходом на аутентификацию (этап 5).
SET search_path = dbo;

CREATE OR REPLACE FUNCTION dev_all_depositors()
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar, operator_name varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT d.depositor_id, d.code, d.org_name, o.name
  FROM depositor d
  LEFT JOIN operator o ON o.operator_id = d.operator_id
  ORDER BY d.code
$$;
GRANT EXECUTE ON FUNCTION dev_all_depositors() TO portal_app;
COMMENT ON FUNCTION dev_all_depositors() IS
  'Только для разработки: удалить после внедрения аутентификации (этап 5)';

-- Список пользователей — тоже удобно видеть при тестировании ролей.
-- Таблица app_user под RLS не стоит, но функция даёт готовый вид.
CREATE OR REPLACE FUNCTION dev_all_users()
RETURNS TABLE (login varchar, full_name varchar, role_code varchar, is_active boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.login, u.full_name, r.code, u.is_active
  FROM app_user u
  LEFT JOIN enum_user_role r ON r.id = u.role_id
  ORDER BY r.sort_order, u.full_name
$$;
GRANT EXECUTE ON FUNCTION dev_all_users() TO portal_app;
COMMENT ON FUNCTION dev_all_users() IS
  'Только для разработки: удалить после внедрения аутентификации (этап 5)';
