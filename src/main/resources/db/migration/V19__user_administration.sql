-- V19__user_administration.sql
-- Создание и правка пользователей администратором портала.
SET search_path = dbo;

-- =====================================================================
-- 1. Логин должен быть уникальным
-- =====================================================================
-- Без этого можно завести второго «admin», и вход стал бы неоднозначным:
-- auth_user_by_login вернул бы две строки. Регистр не различаем — человек
-- наберёт «Admin» и будет прав.
CREATE UNIQUE INDEX IF NOT EXISTS app_user_login_uk ON app_user (lower(login));

-- =====================================================================
-- 2. Создание пользователя
-- =====================================================================
-- Пароль приходит уже посчитанным хешем: открытый пароль в базу
-- не попадает даже на мгновение. Флаг смены ставится сразу — пароль
-- задал администратор, значит он временный (см. V18).
CREATE OR REPLACE FUNCTION admin_create_user(
    p_login text, p_full_name text, p_role_code text, p_position text,
    p_organization text, p_phone text, p_password_hash text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE
  v_role_id smallint;
  v_user_id uuid;
BEGIN
  SELECT id INTO v_role_id FROM enum_user_role WHERE code = p_role_code AND is_active;
  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'Неизвестная роль: %', p_role_code;
  END IF;

  INSERT INTO app_user (login, password_hash, full_name, role_id, position,
                        organization, phone, is_active, must_change_password,
                        password_changed_at)
  VALUES (btrim(p_login), p_password_hash, btrim(p_full_name), v_role_id,
          nullif(btrim(coalesce(p_position, '')), ''),
          nullif(btrim(coalesce(p_organization, '')), ''),
          nullif(btrim(coalesce(p_phone, '')), ''),
          true, true, now())
  RETURNING user_id INTO v_user_id;

  -- Настройки видимости создаются сразу со значениями по умолчанию:
  -- иначе карточка пользователя показывала бы «ничего не задано».
  INSERT INTO user_settings (user_id) VALUES (v_user_id)
  ON CONFLICT (user_id) DO NOTHING;

  RETURN v_user_id;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_create_user(text, text, text, text, text, text, text) TO portal_app;

-- =====================================================================
-- 3. Правка данных пользователя
-- =====================================================================
CREATE OR REPLACE FUNCTION admin_update_user(
    p_user_id uuid, p_full_name text, p_role_code text, p_position text,
    p_organization text, p_phone text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE
  v_role_id smallint;
BEGIN
  SELECT id INTO v_role_id FROM enum_user_role WHERE code = p_role_code AND is_active;
  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'Неизвестная роль: %', p_role_code;
  END IF;

  UPDATE app_user
     SET full_name    = btrim(p_full_name),
         role_id      = v_role_id,
         position     = nullif(btrim(coalesce(p_position, '')), ''),
         organization = nullif(btrim(coalesce(p_organization, '')), ''),
         phone        = nullif(btrim(coalesce(p_phone, '')), '')
   WHERE user_id = p_user_id;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_update_user(uuid, text, text, text, text, text) TO portal_app;

-- Логин меняется отдельной функцией: это смена способа входа, а не
-- правка анкеты, и ошибка здесь закрывает человеку доступ.
CREATE OR REPLACE FUNCTION admin_change_login(p_user_id uuid, p_login text)
RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  UPDATE app_user SET login = btrim(p_login) WHERE user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION admin_change_login(uuid, text) TO portal_app;

-- =====================================================================
-- 4. Доступы к поклажедателям
-- =====================================================================
-- Набор заменяется целиком: экран присылает итоговый список галочек,
-- а не «что добавить и что убрать» — так не рассинхронизируется.
CREATE OR REPLACE FUNCTION admin_set_depositor_access(p_user_id uuid, p_depositor_ids uuid[])
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  DELETE FROM user_depositor_access
   WHERE user_id = p_user_id
     AND NOT (depositor_id = ANY (coalesce(p_depositor_ids, '{}'::uuid[])));

  INSERT INTO user_depositor_access (user_id, depositor_id)
  SELECT p_user_id, d
  FROM unnest(coalesce(p_depositor_ids, '{}'::uuid[])) AS d
  ON CONFLICT (user_id, depositor_id) DO NOTHING;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_set_depositor_access(uuid, uuid[]) TO portal_app;

-- Какие поклажедатели уже выданы пользователю (для галочек в карточке).
CREATE OR REPLACE FUNCTION admin_user_depositors(p_user_id uuid)
RETURNS TABLE (depositor_id uuid)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT depositor_id FROM user_depositor_access WHERE user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION admin_user_depositors(uuid) TO portal_app;

-- =====================================================================
-- 5. Списки для выпадающих списков и галочек
-- =====================================================================
-- Таблица depositor под RLS, поэтому администратору нужен отдельный,
-- явно привилегированный способ получить её целиком.
CREATE OR REPLACE FUNCTION admin_depositors()
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT depositor_id, code, org_name FROM depositor ORDER BY org_name
$$;
GRANT EXECUTE ON FUNCTION admin_depositors() TO portal_app;

CREATE OR REPLACE FUNCTION admin_roles()
RETURNS TABLE (code varchar, name varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT code, name FROM enum_user_role WHERE is_active ORDER BY sort_order
$$;
GRANT EXECUTE ON FUNCTION admin_roles() TO portal_app;
