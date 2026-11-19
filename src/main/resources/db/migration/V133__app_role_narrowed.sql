SET search_path = dbo;

-- =====================================================================
-- V133. Портал не пишет в учётные записи и доступы напрямую (аудит
-- 11.11.2026, S10, вторая часть).
--
-- ЧТО БЫЛО. У portal_app были SELECT/INSERT/UPDATE/DELETE на app_user
-- (вместе с хэшами паролей), user_depositor_access, user_warehouse_access
-- и auth_remember_token. Функции admin_* при этом ничего не защищали:
-- то же делалось прямым запросом, и одна будущая ошибка с внедрением SQL
-- дала бы хэши всех паролей и выдачу себе роли администратора.
--
-- КАК ТЕПЕРЬ.
--   * app_user — только чтение, и без password_hash. Пароль сверяют и
--     меняют функции auth_* (владельца), как и прежде.
--   * доступы к поклажедателям и складам — только чтение; выдают их
--     admin_set_depositor_access / admin_set_warehouse_access.
--   * токены «запомнить меня» — ни чтения, ни записи: всё через функции
--     auth_remember_* ниже (их зовёт RememberMeService).
--
-- ВАЖНО НА БУДУЩЕЕ. Чтение app_user выдано по колонкам: новую колонку
-- portal_app не увидит, пока её не выдать — GRANT SELECT (колонка)
-- ON app_user TO portal_app в той же миграции, что её добавляет.
-- =====================================================================

-- ---------- Функции «запомнить меня» -----------------------------------------
-- Тексты запросов — те же, что были в RememberMeService (V130).

CREATE FUNCTION auth_remember_issue(p_user_id uuid, p_hash varchar, p_days integer, p_agent varchar)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  INSERT INTO auth_remember_token (user_id, token_hash, expires_at, user_agent)
  VALUES (p_user_id, p_hash, now() + make_interval(days => p_days), left(p_agent, 255));
$$;

-- Погасить токен и вернуть, чей он; пусто — токена нет, просрочен,
-- уже погашен или учётка заблокирована.
CREATE FUNCTION auth_remember_use(p_hash varchar)
RETURNS TABLE (user_id text, login text, full_name text, role_code text, must_change_password boolean)
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  UPDATE auth_remember_token t SET last_used_at = now()
    FROM app_user u, enum_user_role r
   WHERE t.token_hash = p_hash
     AND t.last_used_at IS NULL
     AND u.user_id = t.user_id
     AND r.id = u.role_id
     AND t.expires_at > now()
     AND u.is_active
RETURNING u.user_id::text, u.login::text, u.full_name::text, r.code::text, u.must_change_password;
$$;

-- Погашен меньше p_grace секунд назад — соседним запросом той же страницы.
CREATE FUNCTION auth_remember_just_replaced(p_hash varchar, p_grace integer)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT EXISTS (SELECT 1 FROM auth_remember_token
                  WHERE token_hash = p_hash
                    AND last_used_at > now() - make_interval(secs => p_grace));
$$;

CREATE FUNCTION auth_remember_forget(p_hash varchar)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  DELETE FROM auth_remember_token WHERE token_hash = p_hash;
$$;

CREATE FUNCTION auth_remember_forget_all(p_user_id uuid)
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  DELETE FROM auth_remember_token WHERE user_id = p_user_id;
$$;

REVOKE EXECUTE ON FUNCTION auth_remember_issue(uuid, varchar, integer, varchar),
                           auth_remember_use(varchar),
                           auth_remember_just_replaced(varchar, integer),
                           auth_remember_forget(varchar),
                           auth_remember_forget_all(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_remember_issue(uuid, varchar, integer, varchar),
                          auth_remember_use(varchar),
                          auth_remember_just_replaced(varchar, integer),
                          auth_remember_forget(varchar),
                          auth_remember_forget_all(uuid) TO portal_app;

-- ---------- Права portal_app ---------------------------------------------------

REVOKE ALL ON auth_remember_token FROM portal_app;

REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON user_depositor_access, user_warehouse_access FROM portal_app;

-- app_user: только чтение, все колонки, кроме password_hash.
REVOKE ALL ON app_user FROM portal_app;
DO $$
DECLARE
  v_cols text;
BEGIN
  SELECT string_agg(quote_ident(attname), ', ' ORDER BY attnum)
    INTO v_cols
    FROM pg_attribute
   WHERE attrelid = 'app_user'::regclass
     AND attnum > 0 AND NOT attisdropped
     AND attname <> 'password_hash';
  EXECUTE format('GRANT SELECT (%s) ON app_user TO portal_app', v_cols);
END
$$;
