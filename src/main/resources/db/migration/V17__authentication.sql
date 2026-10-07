-- V17__authentication.sql
-- Этап 5: вход по логину и паролю, доступы.
SET search_path = dbo;

-- =====================================================================
-- 1. Пароли
-- =====================================================================
-- Хеши BCrypt. Пароль у всех демо-пользователей — Portal123!
-- В продакшне пароли задаёт администратор при создании пользователя,
-- а эти строки не выполняются (миграция ставит их только там, где пусто).
-- Учёт попыток входа: нужен и для блокировки перебора, и для §11 (аудит)
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS failed_login_count smallint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS locked_until       timestamptz,
  ADD COLUMN IF NOT EXISTS password_changed_at timestamptz;
CREATE TEMP TABLE _seed_passwords (login varchar, hash varchar);
INSERT INTO _seed_passwords (login, hash) VALUES
  ('client@roga-kopyta.kz', '$2b$10$q7mhcKPaJO1aUCEPl4waPeIGHR3jil1J9TDHtJJq/j4ht/BPBtBTC'),
  ('client@mol-reki.kz', '$2b$10$lp7kQfKd6VJOX/ONduV0cOKndaPYkndnNioWjxOo2LDZC2PRwkfrK'),
  ('n.nazarbayev@ahlers.com', '$2b$10$13oYL.qTXmqqTsbnYtmyeu1J/IbCJ6dgzHl.2PFi.1K/YHbCYWyqy'),
  ('s.akhmetova@ahlers.com', '$2b$10$abjJXjIkoEKAp6p4H7JxwelRiMFjIFqViEj7QzDbaz6qhpqzIFCsG'),
  ('warehouse@ahlers.com', '$2b$10$IVhATNyBrnRt9k4buCTXvOB/bYdNBYhqRo/yN9BoaIoeg0PlUh7BS'),
  ('admin@ahlers.com', '$2b$10$pBGZ0IKfaLUBUfeao2HQoeXAHsw1ljMEqmU8UnSuZYTEy0SEzIhRe'),
  ('dev@ahlers.com', '$2b$10$y16fp4VXcMS0F2bmaIfQfekzDNZTG7v/JGjRE50/h46mK17FQaT4e'),
  ('blocked@roga-kopyta.kz', '$2b$10$KUFkoFmrH7jNj5CJF.2wmO35Y4ndOjjzZpMRcm.ZHND4cbAMcfl1u');

-- Условие «настоящий ли это хеш»: длина BCrypt всегда 60 символов.
-- Проверять по префиксу $2 нельзя — заглушка в демо-данных начинается так же.
UPDATE app_user u SET password_hash = p.hash, password_changed_at = now()
  FROM _seed_passwords p
 WHERE u.login = p.login
   AND (u.password_hash IS NULL OR length(u.password_hash) <> 60);

DROP TABLE _seed_passwords;

COMMENT ON COLUMN app_user.password_hash IS
  'BCrypt-хеш пароля. Пустое значение означает, что вход по паролю невозможен';

COMMENT ON COLUMN app_user.locked_until IS
  'Временная блокировка после серии неудачных попыток входа';

-- =====================================================================
-- 2. Служебные функции разработки больше не нужны
-- =====================================================================
-- Вход теперь настоящий, поклажедатель берётся из сессии пользователя,
-- поэтому обход RLS через dev-функции недопустим (решение этапа 5).
DROP FUNCTION IF EXISTS dev_depositor_by_code(text);
DROP FUNCTION IF EXISTS dev_all_depositors();
DROP FUNCTION IF EXISTS dev_all_users();

-- =====================================================================
-- 3. Проверка учётных данных
-- =====================================================================
-- Таблица app_user под RLS не стоит, но читать хеш пароля приложению
-- нужно строго по логину и ничего больше — отдельная функция делает
-- намерение явным и не даёт выбрать таблицу целиком.
CREATE OR REPLACE FUNCTION auth_user_by_login(p_login text)
RETURNS TABLE (user_id uuid, login varchar, password_hash varchar, full_name varchar,
               role_code varchar, is_active boolean, locked_until timestamptz,
               failed_login_count smallint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.user_id, u.login, u.password_hash, u.full_name, r.code, u.is_active,
         u.locked_until, u.failed_login_count
  FROM app_user u
  LEFT JOIN enum_user_role r ON r.id = u.role_id
  WHERE lower(u.login) = lower(p_login)
$$;
GRANT EXECUTE ON FUNCTION auth_user_by_login(text) TO portal_app;

CREATE OR REPLACE FUNCTION auth_register_attempt(p_login text, p_success boolean)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  IF p_success THEN
    UPDATE app_user SET failed_login_count = 0, locked_until = NULL, last_login_at = now()
     WHERE lower(login) = lower(p_login);
  ELSE
    UPDATE app_user
       SET failed_login_count = failed_login_count + 1,
           -- после пяти неудачных попыток вход закрывается на 15 минут
           locked_until = CASE WHEN failed_login_count + 1 >= 5
                               THEN now() + interval '15 minutes' ELSE locked_until END
     WHERE lower(login) = lower(p_login);
  END IF;
END $$;
GRANT EXECUTE ON FUNCTION auth_register_attempt(text, boolean) TO portal_app;

-- =====================================================================
-- 4. Настройки видимости — уже есть в user_settings (§6.3), добавляем
--    только право администрировать портал
-- =====================================================================
COMMENT ON TABLE user_settings IS
  'Персональные настройки доступа: видимость разделов и права на создание (§6.3, Р5)';
