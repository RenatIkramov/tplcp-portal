SET search_path = dbo;

-- =====================================================================
-- V126. Журнал действий помнит человека, а не логин (аудит 11.11.2026, A8).
--
-- ЧТО БЫЛО. audit_log.actor — логин, под которым действовали. После смены
-- логина (admin_change_login) старые записи теряли имя (оно искалось по
-- нынешнему логину), а отбор «Пользователь» делил историю человека
-- надвое: до смены и после.
--
-- КАК ТЕПЕРЬ.
--   * audit_log.actor_id — идентификатор пользователя; его ставит триггер
--     по логину в момент записи. Прежние записи заполнены здесь же по
--     нынешним логинам. Внешнего ключа нет: пользователя удаляют целиком,
--     а журнал о нём остаётся.
--   * audit_net показывает нынешний логин того, кто действовал, и
--     отбирает по человеку. Имена в истории и журнале (audit_history,
--     admin_audit_log, admin_audit_history) ищутся по логину из audit_net —
--     теперь это нынешний логин, и их переписывать не нужно.
--   * admin_audit_actors — список для отбора: по человеку, нынешним логином.
--   * actor остаётся как был — тем, что записано тогда (журнал не правится).
-- =====================================================================

ALTER TABLE audit_log ADD COLUMN actor_id uuid;
COMMENT ON COLUMN audit_log.actor_id IS
  'кто действовал — пользователь (V126); NULL — служебное действие или логин, которого не было';

-- Прежние записи: по нынешним логинам. Неудачный вход несуществующего
-- логина остаётся без человека — так и должно быть.
UPDATE audit_log a SET actor_id = u.user_id
  FROM app_user u
 WHERE lower(u.login) = lower(a.actor) AND a.actor_id IS NULL;

CREATE INDEX audit_log_actor_id_ix ON audit_log (actor_id, at);

CREATE FUNCTION audit_log_actor_id() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
BEGIN
  IF NEW.actor_id IS NULL THEN
    NEW.actor_id := (SELECT u.user_id FROM app_user u WHERE lower(u.login) = lower(NEW.actor));
  END IF;
  RETURN NEW;
END
$$;

CREATE TRIGGER actor_id BEFORE INSERT ON audit_log
  FOR EACH ROW EXECUTE FUNCTION audit_log_actor_id();

-- ---------- Сводка журнала: по человеку ------------------------------------

CREATE OR REPLACE FUNCTION audit_net(p_object_type text, p_object_id text, p_depositor uuid, p_actor text, p_from timestamp with time zone, p_to timestamp with time zone)
 RETURNS TABLE(event_id bigint, at timestamp with time zone, actor text, depositor_id uuid, object_type text, object_id text, object_label text, source_table text, action text, part text, changes jsonb)
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'dbo', 'pg_temp'
AS $function$
DECLARE
  -- Отбор «Пользователь» приходит логином — сегодняшним. Записи ищутся по
  -- человеку (actor_id), а не по логину, под которым он тогда работал.
  v_actor_id uuid := (SELECT u.user_id FROM app_user u WHERE lower(u.login) = lower(p_actor));
BEGIN
  -- plpgsql, а не sql: запрос планируется с известными значениями отборов.
  -- Функция на sql планировалась «вообще», и сводка журнала в 10 тысяч
  -- строк шла 12 секунд вместо трети секунды.
  RETURN QUERY
  WITH ev AS (
    -- who — нынешний логин того, кто действовал; нет такого пользователя
    -- (удалён, служебное действие) — логин, записанный тогда.
    SELECT a.*, COALESCE(u.login, a.actor)::text AS who
      FROM audit_log a
      LEFT JOIN app_user u ON u.user_id = a.actor_id
     WHERE (p_object_type IS NULL OR a.object_type = p_object_type)
       AND (p_object_id IS NULL OR a.object_id = p_object_id)
       AND (p_depositor IS NULL OR a.depositor_id = p_depositor)
       AND (p_actor IS NULL OR a.actor_id = v_actor_id
            OR (a.actor_id IS NULL AND a.actor = p_actor))
       AND (p_from IS NULL OR a.at >= p_from)
       AND (p_to IS NULL OR a.at < p_to)
  ),
  grp AS (
    SELECT e.tx, e.object_type, e.object_id, e.part,
           max(e.audit_id) AS event_id,
           max(e.at) AS at,
           (array_agg(e.who ORDER BY e.audit_id DESC))[1] AS actor,
           (array_agg(e.depositor_id ORDER BY e.audit_id DESC))[1] AS depositor_id,
           (array_agg(e.object_label ORDER BY e.audit_id DESC))[1] AS object_label,
           (array_agg(e.source_table ORDER BY e.audit_id))[1] AS source_table,
           (array_agg(e.action ORDER BY e.audit_id))[1] AS first_action,
           (array_agg(e.action ORDER BY e.audit_id DESC))[1] AS last_action
      FROM ev e
     GROUP BY e.tx, e.object_type, e.object_id, e.part
  ),
  fld AS (
    SELECT e.tx, e.object_type, e.object_id, e.part, c.key,
           (array_agg(c.value -> 'o' ORDER BY e.audit_id))[1] AS o,
           (array_agg(c.value -> 'n' ORDER BY e.audit_id DESC))[1] AS n
      FROM ev e
      CROSS JOIN LATERAL jsonb_each(e.changes) c
     GROUP BY e.tx, e.object_type, e.object_id, e.part, c.key
  ),
  net AS (
    SELECT g.*,
           CASE WHEN g.first_action = 'login_failed' THEN 'login_failed'
                WHEN g.first_action = 'create' AND g.last_action = 'delete' THEN NULL
                WHEN g.first_action = 'create' THEN 'create'
                WHEN g.last_action = 'delete' THEN 'delete'
                ELSE 'update' END AS act
      FROM grp g
  ),
  ch AS (
    -- Соединение, а не подзапрос на каждое событие: подзапрос давал
    -- квадрат от числа событий (10 тысяч — 25 секунд). Пустая часть
    -- сравнивается через COALESCE, чтобы соединение шло по хэшу.
    SELECT n.tx, n.object_type, n.object_id, n.part,
           jsonb_object_agg(f.key, jsonb_strip_nulls(jsonb_build_object('o', x.oo, 'n', x.nn))) AS changes
      FROM net n
      JOIN fld f ON f.tx = n.tx AND f.object_type = n.object_type AND f.object_id = n.object_id
                AND COALESCE(f.part, '') = COALESCE(n.part, '')
      CROSS JOIN LATERAL (
        SELECT CASE WHEN n.act IN ('create', 'login_failed') THEN NULL ELSE f.o END AS oo,
               CASE WHEN n.act = 'delete' THEN NULL ELSE f.n END AS nn
      ) x
     WHERE n.act IS NOT NULL
       AND (n.act <> 'update' OR x.oo IS DISTINCT FROM x.nn)
     GROUP BY n.tx, n.object_type, n.object_id, n.part
  )
  SELECT n.event_id, n.at, n.actor::text, n.depositor_id, n.object_type::text, n.object_id, n.object_label,
         n.source_table::text,
         CASE WHEN n.act = 'update' AND n.part IS NULL AND n.object_type IN ('receipts', 'orders', 'transfers')
                   AND c.changes ? 'status_id' THEN 'status' ELSE n.act END::text,
         n.part,
         COALESCE(c.changes, '{}'::jsonb)
    FROM net n
    LEFT JOIN ch c ON c.tx = n.tx AND c.object_type = n.object_type AND c.object_id = n.object_id
                  AND COALESCE(c.part, '') = COALESCE(n.part, '')
   WHERE n.act IS NOT NULL
     -- сохранили без настоящих изменений (например, состав «как был»)
     AND NOT (n.act = 'update' AND c.changes IS NULL);
END
$function$;

-- ---------- Список «Пользователь» в отборе журнала -------------------------

CREATE OR REPLACE FUNCTION admin_audit_actors()
 RETURNS TABLE(actor text, actor_name text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'dbo', 'pg_temp'
AS $function$
  -- один человек — одна строка, под нынешним логином, сколько бы раз он
  -- его ни менял
  SELECT DISTINCT COALESCE(u.login, a.actor)::text, COALESCE(u.full_name, a.actor)::text
    FROM audit_log a
    LEFT JOIN app_user u ON u.user_id = a.actor_id
   WHERE a.action <> 'login_failed'
   ORDER BY 2
$function$;
