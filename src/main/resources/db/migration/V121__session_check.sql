SET search_path = dbo;

-- V121. Открытые сеансы гаснут при блокировке, смене пароля и роли,
-- отзыве доступа к поклажедателю (аудит 11.11.2026, S4).
--
-- Прежде сеанс помнил пользователя с момента входа и базу больше не
-- спрашивал: заблокированный читал запасы и заказы до закрытия вкладки,
-- отозванный поклажедатель оставался виден, смена пароля не трогала
-- другие открытые сеансы.
--
-- * sessions_valid_after — «сеансы, открытые раньше, недействительны».
--   Ставит триггер, а не код портала: правка из администрирования, смена
--   своего пароля и UPDATE руками действуют одинаково.
-- * auth_session_check() — один вопрос на каждый запрос к API: активен
--   ли, не устарел ли сеанс, доступен ли выбранный поклажедатель.
--   Ответ: ok | inactive | stale | depositor.

ALTER TABLE app_user ADD COLUMN sessions_valid_after timestamptz;
COMMENT ON COLUMN app_user.sessions_valid_after IS
  'сеансы, открытые раньше этого момента, недействительны: блокировка, смена пароля или роли (V121)';

-- В журнале действий не показывается: это следствие правки, а не правка.
INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order)
VALUES ('app_user', 'sessions_valid_after', 'skip', NULL, 17)
ON CONFLICT (source_table, column_name) DO NOTHING;

CREATE FUNCTION app_user_sessions_mark() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.password_hash IS DISTINCT FROM OLD.password_hash
     OR NEW.role_id IS DISTINCT FROM OLD.role_id
     OR (COALESCE(OLD.is_active, false) AND NOT COALESCE(NEW.is_active, false)) THEN
    -- clock_timestamp, а не now(): сеанс, обновлённый в той же
    -- транзакции после смены своего пароля, должен оказаться позже отметки
    NEW.sessions_valid_after := clock_timestamp();
  END IF;
  RETURN NEW;
END
$$;

CREATE TRIGGER sessions_mark BEFORE UPDATE ON app_user
  FOR EACH ROW EXECUTE FUNCTION app_user_sessions_mark();

CREATE FUNCTION auth_session_check(p_login text, p_depositor_id uuid, p_signed_in_at timestamptz)
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT CASE
           WHEN u.user_id IS NULL OR NOT COALESCE(u.is_active, false) THEN 'inactive'
           WHEN u.sessions_valid_after IS NOT NULL
                AND (p_signed_in_at IS NULL OR u.sessions_valid_after > p_signed_in_at) THEN 'stale'
           WHEN p_depositor_id IS NOT NULL
                AND NOT user_may_access_depositor(p_login, p_depositor_id) THEN 'depositor'
           ELSE 'ok'
         END
    FROM (SELECT 1) AS one
    LEFT JOIN app_user u ON lower(u.login) = lower(p_login)
$$;
GRANT EXECUTE ON FUNCTION auth_session_check(text, uuid, timestamptz) TO portal_app;

-- Текст отказа: экран по 401 показывает форму входа с этим сообщением.
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('error.auth.session_ended', 'server', 'message', 'server', 'сеанс завершён — изменились права или пароль (V121)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.auth.session_ended', 'ru', 'Сеанс завершён: изменились права или пароль. Войдите снова'),
    ('error.auth.session_ended', 'en', 'Your session has ended because your access or password changed. Please sign in again')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
