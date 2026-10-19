-- Вернуть клиентские учётные записи тестов в состояние из V17.
--
--   psql -U portal_app -f tools/reset_test_logins.sql
--
-- База берётся из переменных среды libpq, как и у соседнего файла
-- tools/check_test_logins.sql.
--
-- Что делает: снимает блокировки, обнуляет счётчик неудачных попыток
-- и возвращает обеим учёткам демо-пароль из V17. Хеши ниже — те же
-- строки, что в миграции. Это не новый пароль, а возврат
-- демо-состояния.
--
-- Почему это не миграция: миграция ставит хеш только там, где его нет,
-- и намеренно не перетирает пароль, заданный человеком. Здесь мы
-- перетираем сознательно и только для двух демо-учёток.
--
-- Правка пароля через портал после этого работает как раньше: файл
-- ничего не ломает, он возвращает исходную точку.
UPDATE dbo.app_user u
   SET password_hash        = v.seed,
       password_changed_at  = now(),
       must_change_password = false,
       is_active            = true,
       failed_login_count   = 0,
       locked_until         = NULL
  FROM (VALUES
    ('client@roga-kopyta.kz',
     '$2b$10$q7mhcKPaJO1aUCEPl4waPeIGHR3jil1J9TDHtJJq/j4ht/BPBtBTC'),
    ('client@mol-reki.kz',
     '$2b$10$lp7kQfKd6VJOX/ONduV0cOKndaPYkndnNioWjxOo2LDZC2PRwkfrK')
  ) AS v(login, seed)
 WHERE u.login = v.login;

-- Токены «запомнить меня» этих учёток тоже снимаем: пароль сменился,
-- а токен, выданный под прежний, остался бы действующим.
DELETE FROM dbo.auth_remember_token t
 WHERE t.user_id IN (
   SELECT u.user_id FROM dbo.app_user u
    WHERE u.login IN ('client@roga-kopyta.kz', 'client@mol-reki.kz'));

SELECT u.login, u.is_active, u.must_change_password,
       u.failed_login_count, u.locked_until
  FROM dbo.app_user u
 WHERE u.login IN ('client@roga-kopyta.kz', 'client@mol-reki.kz')
 ORDER BY u.login;
