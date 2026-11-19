SET search_path = dbo;

-- =====================================================================
-- V134. Функции — только порталу, новые таблицы — только на чтение
-- (аудит 11.11.2026, S10, третья часть).
--
-- ЧТО БЫЛО.
--   * 97 функций dbo мог вызвать любой, кто вошёл на сервер PostgreSQL
--     (PUBLIC — так PostgreSQL выдаёт по умолчанию), в том числе
--     функции SECURITY DEFINER, которые работают правами владельца.
--   * У 57 функций SECURITY DEFINER search_path = dbo без pg_temp.
--     PostgreSQL тогда ищет имена сначала во временной схеме сеанса:
--     вызывающий может создать временную таблицу или функцию с тем же
--     именем и подсунуть её функции владельца.
--   * Каждая новая таблица сама получала у portal_app полную запись (V12).
--
-- КАК ТЕПЕРЬ.
--   * EXECUTE — только portal_app (и владельцу); у PUBLIC отобрано —
--     у существующих функций и по умолчанию у будущих.
--   * Всем SECURITY DEFINER — search_path = dbo, pg_temp (pg_temp
--     последней — временные объекты не перекрывают настоящие).
--   * Новая таблица у portal_app — только SELECT. Запись выдаётся
--     в миграции явно:
--         GRANT INSERT, UPDATE, DELETE ON новая_таблица TO portal_app;
--     Так право писать — решение, а не случайность.
-- =====================================================================

-- ---------- Вызов функций ------------------------------------------------------

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA dbo FROM PUBLIC;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA dbo TO portal_app;

ALTER DEFAULT PRIVILEGES FOR ROLE portal_owner IN SCHEMA dbo
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE portal_owner IN SCHEMA dbo
  GRANT EXECUTE ON FUNCTIONS TO portal_app;

-- ---------- pg_temp в конце search_path ----------------------------------------

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS fn
      FROM pg_proc p
     WHERE p.pronamespace = 'dbo'::regnamespace
       AND p.prosecdef
       AND NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c
                        WHERE c = 'search_path=dbo, pg_temp')
  LOOP
    EXECUTE format('ALTER FUNCTION %s SET search_path = dbo, pg_temp', r.fn);
  END LOOP;
END
$$;

-- ---------- Новые таблицы — только чтение --------------------------------------

ALTER DEFAULT PRIVILEGES FOR ROLE portal_owner IN SCHEMA dbo
  REVOKE INSERT, UPDATE, DELETE ON TABLES FROM portal_app;
