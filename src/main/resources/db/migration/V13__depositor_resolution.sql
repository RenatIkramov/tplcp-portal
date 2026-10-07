-- V13__depositor_resolution.sql
--
-- Проблема «курицы и яйца», которая вылезла сразу после включения RLS:
-- чтобы читать данные, приложению нужен app.depositor_id, но чтобы его
-- узнать, надо прочитать таблицу depositor — а она тоже под RLS и без
-- выставленного контекста не отдаёт ни строки.
--
-- Решение: выбор поклажедателя — отдельная, привилегированная операция.
-- Две функции SECURITY DEFINER выполняются с правами владельца, то есть
-- мимо политик, и отдают СТРОГО то, что положено: список поклажедателей,
-- к которым допущен конкретный пользователь (§6.4), и ничего больше.
SET search_path = dbo;

-- Какие поклажедатели доступны пользователю.
-- Менеджеру/админу/разработчику — по user_depositor_access,
-- складскому сотруднику — по складам, на которых он работает.
CREATE OR REPLACE FUNCTION depositors_for_user(p_login text)
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT DISTINCT d.depositor_id, d.code, d.org_name
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

-- Проверка: допущен ли пользователь к этому поклажедателю.
-- Вызывается перед выставлением app.depositor_id, чтобы подставить чужой
-- идентификатор в заголовке запроса было нельзя.
CREATE OR REPLACE FUNCTION user_may_access_depositor(p_login text, p_depositor_id uuid)
RETURNS boolean
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT EXISTS (
    SELECT 1 FROM depositors_for_user(p_login) f
    WHERE f.depositor_id = p_depositor_id
  )
$$;

GRANT EXECUTE ON FUNCTION depositors_for_user(text) TO portal_app;
GRANT EXECUTE ON FUNCTION user_may_access_depositor(text, uuid) TO portal_app;

-- Функции-обёртки для разработки, пока нет аутентификации: позволяют
-- найти поклажедателя по коду. В продакшне не нужны — удалить вместе
-- с переходом на вход по логину (этап 5).
CREATE OR REPLACE FUNCTION dev_depositor_by_code(p_code text)
RETURNS uuid
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT depositor_id FROM depositor WHERE code = p_code
$$;
GRANT EXECUTE ON FUNCTION dev_depositor_by_code(text) TO portal_app;
COMMENT ON FUNCTION dev_depositor_by_code(text) IS
  'Только для разработки: удалить после внедрения аутентификации (этап 5)';
