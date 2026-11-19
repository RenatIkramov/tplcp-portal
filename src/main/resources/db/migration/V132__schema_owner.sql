SET search_path = dbo;

-- =====================================================================
-- V132. Владелец схемы — не суперпользователь (аудит 11.11.2026, S10,
-- первая часть).
--
-- ЧТО БЫЛО. Все объекты dbo принадлежали postgres, Flyway ходил под ним.
-- 71 функция SECURITY DEFINER выполнялась с правами суперпользователя:
-- чтение файлов сервера, COPY … PROGRAM, создание ролей, чужие базы.
--
-- КАК ТЕПЕРЬ. Владелец — роль portal_owner: NOSUPERUSER, без права
-- создавать роли и базы. BYPASSRLS ей нужен: у 24 таблиц FORCE ROW LEVEL
-- SECURITY, политики действуют и на владельца, а функциям администратора
-- и журнала нужно видеть всех клиентов — как прежде.
--
-- Роль создаётся без входа: пароль в миграцию не кладётся (она в git).
-- Один раз руками, под postgres:
--     ALTER ROLE portal_owner LOGIN PASSWORD '…';
-- и в application.yml — spring.flyway.user: portal_owner и его пароль.
-- Следующие миграции идут уже под portal_owner; объект с другим
-- владельцем ловит DatabaseRolesTest.
--
-- Роль — на весь сервер PostgreSQL: если её уже создали (другая база
-- того же сервера), миграция её не пересоздаёт, а только выравнивает
-- признаки.
-- =====================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portal_owner') THEN
    CREATE ROLE portal_owner NOLOGIN;
  END IF;
END
$$;

ALTER ROLE portal_owner NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION BYPASSRLS INHERIT;

-- ---------- Передать схему и всё в ней ---------------------------------------

ALTER SCHEMA dbo OWNER TO portal_owner;

DO $$
DECLARE
  r record;
BEGIN
  -- Таблицы, представления, последовательности. Индексы и последовательности
  -- столбцов identity/serial переходят вместе с таблицей; их пропускаем.
  FOR r IN
    SELECT c.oid::regclass AS obj, c.relkind
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'dbo'
       AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
       AND c.relname <> 'flyway_schema_history'
       AND pg_get_userbyid(c.relowner) <> 'portal_owner'
       AND NOT (c.relkind = 'S' AND EXISTS (
             SELECT 1 FROM pg_depend d
              WHERE d.objid = c.oid AND d.classid = 'pg_class'::regclass
                AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')))
  LOOP
    EXECUTE format('ALTER %s %s OWNER TO portal_owner',
                   CASE r.relkind WHEN 'v' THEN 'VIEW'
                                  WHEN 'm' THEN 'MATERIALIZED VIEW'
                                  WHEN 'S' THEN 'SEQUENCE'
                                  WHEN 'f' THEN 'FOREIGN TABLE'
                                  ELSE 'TABLE' END,
                   r.obj);
  END LOOP;

  -- Функции и процедуры.
  FOR r IN
    SELECT p.oid::regprocedure AS obj, p.prokind
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'dbo'
       AND pg_get_userbyid(p.proowner) <> 'portal_owner'
  LOOP
    EXECUTE format('ALTER %s %s OWNER TO portal_owner',
                   CASE r.prokind WHEN 'p' THEN 'PROCEDURE'
                                  WHEN 'a' THEN 'AGGREGATE'
                                  ELSE 'FUNCTION' END,
                   r.obj);
  END LOOP;

  -- Свои типы (перечисления, домены, составные), если появятся.
  FOR r IN
    SELECT t.oid::regtype AS obj, t.typtype
      FROM pg_type t
      JOIN pg_namespace n ON n.oid = t.typnamespace
     WHERE n.nspname = 'dbo'
       AND t.typtype IN ('e', 'd', 'c', 'r', 'm')
       AND t.typrelid = 0
       AND pg_get_userbyid(t.typowner) <> 'portal_owner'
  LOOP
    EXECUTE format('ALTER %s %s OWNER TO portal_owner',
                   CASE r.typtype WHEN 'd' THEN 'DOMAIN' ELSE 'TYPE' END, r.obj);
  END LOOP;

  -- Журнал Flyway: следующую миграцию в него запишет уже portal_owner.
  -- Передать его во владение здесь нельзя: пока идёт миграция, Flyway
  -- держит журнал открытым другим соединением, и ALTER … OWNER ждал бы
  -- вечно (проверено). Права на запись — достаточно, GRANT не ждёт.
  -- Ищется в любой схеме — смотря как настроен spring.flyway.schemas.
  FOR r IN
    SELECT c.oid::regclass AS obj
      FROM pg_class c
     WHERE c.relname = 'flyway_schema_history' AND c.relkind IN ('r', 'p')
  LOOP
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %s TO portal_owner', r.obj);
    -- Порталу журнал миграций не нужен, а запись в него (V12 выдала права
    -- на все таблицы dbo) позволила бы «отметить» миграцию ненакатанной.
    EXECUTE format('REVOKE ALL ON %s FROM portal_app', r.obj);
  END LOOP;
END
$$;

-- ---------- Права на новые объекты -------------------------------------------
-- Умолчания V12 были заведены от имени postgres — для таблиц, которые
-- создаёт postgres. Теперь таблицы создаёт portal_owner: те же права,
-- но от его имени. Сузить их до «только чтение» — следующей частью S10.

ALTER DEFAULT PRIVILEGES FOR ROLE portal_owner IN SCHEMA dbo
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO portal_app;
ALTER DEFAULT PRIVILEGES FOR ROLE portal_owner IN SCHEMA dbo
  GRANT USAGE, SELECT ON SEQUENCES TO portal_app;

-- Прежние умолчания (от имени того, кто накатывал V12, — обычно postgres)
-- убираем: таблица, по ошибке созданная не под portal_owner, не должна
-- молча получить права.
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT DISTINCT pg_get_userbyid(a.defaclrole) AS who
      FROM pg_default_acl a
     WHERE a.defaclnamespace = 'dbo'::regnamespace
       AND pg_get_userbyid(a.defaclrole) <> 'portal_owner'
  LOOP
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA dbo '
                   'REVOKE ALL ON TABLES FROM portal_app', r.who);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA dbo '
                   'REVOKE ALL ON SEQUENCES FROM portal_app', r.who);
  END LOOP;
END
$$;
