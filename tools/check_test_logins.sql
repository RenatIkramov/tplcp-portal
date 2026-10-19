-- Почему тесты получают 401 «Неверный логин или пароль».
--
-- Запускать под portal_app, как ходит приложение:
--
--   psql -U portal_app -f tools/check_test_logins.sql
--
-- База берётся из переменных среды libpq (PGDATABASE, PGHOST, PGPORT),
-- как у любого другого инструмента PostgreSQL.
--
-- Падают ровно те тесты, которые входят КЛИЕНТСКОЙ учётной записью
-- (client@roga-kopyta.kz, client@mol-reki.kz) с демо-паролем из V17.
-- Администратора тесты заводят себе сами и приводят в известное
-- состояние, поэтому админские проверки проходят. Значит дело
-- в состоянии клиентских учёток в базе, а не в коде.
--
-- Код 401 возникает ровно в трёх случаях (AuthService.authenticate):
--
--   * логина нет;
--   * учётка заблокирована, то есть is_active = false;
--   * хеш пароля не совпадает с демо-паролем.
--
-- Временная блокировка после неудачных попыток даёт код 429,
-- а не 401, но её тоже видно ниже.
\pset border 2

SELECT
  u.login,
  u.is_active                AS "активен",
  u.must_change_password     AS "сменить пароль",
  u.failed_login_count       AS "неудачных попыток",
  u.locked_until             AS "заблокирован до",
  u.password_changed_at      AS "пароль менялся",
  length(u.password_hash)    AS "длина хеша",
  u.password_hash = v.seed   AS "хеш из V17"
FROM dbo.app_user u
JOIN (VALUES
  ('client@roga-kopyta.kz',
   '$2b$10$q7mhcKPaJO1aUCEPl4waPeIGHR3jil1J9TDHtJJq/j4ht/BPBtBTC'),
  ('client@mol-reki.kz',
   '$2b$10$lp7kQfKd6VJOX/ONduV0cOKndaPYkndnNioWjxOo2LDZC2PRwkfrK')
) AS v(login, seed) ON v.login = u.login
ORDER BY u.login;

-- Как читать вывод.
--
-- «Хеш из V17» равен false — пароль у этой учётки меняли, скорее всего
-- руками в портале при проверке экранов. Лечится вторым файлом,
-- tools/reset_test_logins.sql.
--
-- «Активен» равен false — учётку заблокировали на экране доступов.
-- Лечится тем же файлом.
--
-- «Заблокирован до» указывает в будущее — была серия неудачных попыток.
-- Это код 429, снимается тем же файлом.
