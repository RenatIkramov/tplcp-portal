-- =====================================================================
-- V46. «Запомнить меня» — долгий вход без повторного пароля
-- =====================================================================
-- Сеанс портала живёт в памяти Tomcat: любой перезапуск приложения
-- выбрасывает всех, и человек вводит пароль заново. Решение заказчика:
-- галочка «запомнить меня», после которой портал не спрашивает пароль,
-- пока человек заходит хотя бы раз в 15 дней.
--
-- Устройство — долгоживущий токен в cookie и его отпечаток здесь.
--
-- Почему именно отпечаток, а не сам токен: в таблице лежит хеш SHA-256,
-- и по содержимому базы войти нельзя. Утечка резервной копии перестаёт
-- быть утечкой доступов.
--
-- Почему токен меняется при каждом использовании (ротация): если cookie
-- всё же украли, им воспользуется только тот, кто успел первым. Когда
-- придёт второй — токена уже нет, и вход потребует пароль. Кража
-- перестаёт быть бессрочной.
SET search_path = dbo;

CREATE TABLE IF NOT EXISTS auth_remember_token (
  token_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
  -- SHA-256 в шестнадцатеричном виде: 64 знака
  token_hash   varchar(64) NOT NULL UNIQUE,
  issued_at    timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL,
  last_used_at timestamptz,
  -- чем заходили: пригодится, когда появится экран «мои устройства»
  user_agent   varchar(255)
);

COMMENT ON TABLE auth_remember_token IS
  'Токены «запомнить меня». В базе только отпечаток токена (SHA-256); '
  'сам токен живёт в cookie браузера и меняется при каждом использовании';
COMMENT ON COLUMN auth_remember_token.expires_at IS
  'Срок скользящий: каждое использование продлевает его на 15 дней';

CREATE INDEX IF NOT EXISTS auth_remember_token_user_ix
  ON auth_remember_token (user_id);
CREATE INDEX IF NOT EXISTS auth_remember_token_expires_ix
  ON auth_remember_token (expires_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON auth_remember_token TO portal_app;

-- Просроченные токены — мусор, который ещё и замедляет поиск.
-- Чистим при выдаче нового: отдельного расписания ради одной таблицы
-- заводить не стоит, а вход случается часто.
CREATE OR REPLACE FUNCTION auth_remember_cleanup()
RETURNS void
LANGUAGE sql
SECURITY DEFINER SET search_path = dbo AS $$
  DELETE FROM auth_remember_token WHERE expires_at < now();
$$;
GRANT EXECUTE ON FUNCTION auth_remember_cleanup() TO portal_app;

-- ---------------------------------------------------------------------
-- Тексты формы входа
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('auth.label.remember_me', 'auth', 'label', NULL,
   'галочка «запомнить меня» на форме входа'),
  ('auth.hint.remember_me', 'auth', 'hint', NULL,
   'пояснение: сколько портал не будет спрашивать пароль')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('auth.label.remember_me', 'ru', 'Запомнить меня'),
  ('auth.label.remember_me', 'en', 'Remember me'),
  ('auth.hint.remember_me', 'ru',
   'Портал не будет спрашивать пароль, пока вы заходите хотя бы раз в 15 дней. '
   'Не включайте на чужом компьютере.'),
  ('auth.hint.remember_me', 'en',
   'The portal will not ask for your password as long as you sign in at least '
   'once every 15 days. Do not enable this on a shared computer.')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
