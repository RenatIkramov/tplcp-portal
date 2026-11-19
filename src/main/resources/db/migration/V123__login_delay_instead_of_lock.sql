SET search_path = dbo;

-- V123. Вход: задержка вместо запирания учётки (аудит 11.11.2026, S8;
-- решение заказчика 11.11.2026).
--
-- Прежде после пяти неудач подряд вход закрывался на 15 минут. Посторонний,
-- зная логин, мог держать учётку запертой, присылая неверный пароль, а
-- настоящий пользователь не входил и с верным. Теперь учётка не запирается:
-- портал считает неудачи подряд и с четвёртой задерживает ОТВЕТ НА НЕУДАЧУ
-- (1, 2, 4, 8 секунд — AuthController). Верный пароль входит сразу.
-- Перебор вдобавок сдерживает ограничение по адресу (LoginThrottle).
--
-- * last_failed_at — когда была последняя неудача. Через 15 минут без
--   попыток счёт начинается заново.
-- * auth_register_failure() — отметить неудачу и вернуть, какая она по счёту.
-- * auth_register_attempt() — прежнее имя для удачного входа и неудачи;
--   запирание (locked_until) больше не ставит.
-- * Уже запертые учётки отпираются.

ALTER TABLE app_user ADD COLUMN last_failed_at timestamptz;
COMMENT ON COLUMN app_user.last_failed_at IS
  'последняя неудачная попытка входа; через 15 минут без попыток счёт неудач начинается заново (V123)';

INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order)
VALUES ('app_user', 'last_failed_at', 'skip', NULL, 18)
ON CONFLICT (source_table, column_name) DO NOTHING;

UPDATE app_user SET locked_until = NULL WHERE locked_until IS NOT NULL;

CREATE FUNCTION auth_register_failure(p_login text) RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_count integer;
BEGIN
  UPDATE app_user
     SET failed_login_count = CASE
           WHEN last_failed_at IS NULL OR last_failed_at < now() - interval '15 minutes' THEN 1
           ELSE failed_login_count + 1 END,
         last_failed_at = now()
   WHERE lower(login) = lower(p_login)
  RETURNING failed_login_count INTO v_count;
  RETURN COALESCE(v_count, 0);
END
$$;
GRANT EXECUTE ON FUNCTION auth_register_failure(text) TO portal_app;
COMMENT ON FUNCTION auth_register_failure(text) IS
  'неудачный вход: счёт неудач подряд (сброс через 15 минут тишины); учётку не запирает (V123)';

CREATE OR REPLACE FUNCTION auth_register_attempt(p_login text, p_success boolean) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  IF p_success THEN
    UPDATE app_user SET failed_login_count = 0, locked_until = NULL, last_failed_at = NULL,
                        last_login_at = now()
     WHERE lower(login) = lower(p_login);
  ELSE
    PERFORM auth_register_failure(p_login);
  END IF;
END
$$;

-- Запирания больше нет — и его текста тоже.
DELETE FROM ui_text     WHERE text_key = 'error.auth.locked_out';
DELETE FROM ui_text_key WHERE text_key = 'error.auth.locked_out';

-- Новые отказы.
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('error.auth.too_many_attempts', 'server', 'message', 'auth', 'много неудачных входов с одного адреса (V123)'),
  ('error.password.too_long', 'server', 'message', 'auth', 'пароль длиннее, чем проверяет bcrypt (V123)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.auth.too_many_attempts', 'ru', 'Слишком много попыток входа. Попробуйте через несколько минут'),
    ('error.auth.too_many_attempts', 'en', 'Too many sign-in attempts. Please try again in a few minutes'),
    ('error.password.too_long', 'ru', 'Пароль слишком длинный: не больше {max} байт (латинская буква — один байт)'),
    ('error.password.too_long', 'en', 'The password is too long: at most {max} bytes (one byte per Latin letter)')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
