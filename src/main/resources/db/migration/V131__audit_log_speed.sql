SET search_path = dbo;

-- =====================================================================
-- V131. Журнал действий быстрее (13.11.2026).
--
-- ЧТО БЫЛО. Первая страница журнала сводила события всей истории и
-- делала это дважды: для числа «всего» и для самой страницы. Проверки
-- пишут в журнал тысячи строк за прогон; на 450 тыс. строк страница
-- строилась 16–20 с, и AuditLogTabTest падал по таймауту. Ещё около
-- секунды на каждый проход уходило на JIT: оценка плана для jsonb_each
-- завышена в сотню раз, и PostgreSQL компилировал запрос, который и без
-- того идёт меньше секунды.
--
-- КАК ТЕПЕРЬ.
--   * Экран открывает журнал за последние 7 дней (решение заказчика,
--     audit-log.js); поле «с» можно стереть — тогда вся история.
--   * admin_audit_log сводит события один раз: «всего» — окном по тем же
--     строкам, что и страница.
--   * audit_net считает итог по полям внутри каждой группы событий (над её
--     собственным массивом изменений), а не общей сортировкой всех полей
--     всего журнала. Результат тот же — сверено на 44 тыс. строк в обе
--     стороны (EXCEPT), быстрее на четверть.
--   * jit = off у функций журнала.
-- =====================================================================

CREATE OR REPLACE FUNCTION admin_audit_log(p_depositor uuid, p_actor text, p_object_type text, p_from date, p_to date, p_page integer, p_page_size integer)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'dbo', 'pg_temp'
 SET jit TO 'off'
AS $function$
DECLARE
  v_total  integer;
  v_rows   jsonb := '[]'::jsonb;
  v_size   integer := LEAST(GREATEST(COALESCE(p_page_size, 50), 1), 200);
  v_page   integer := GREATEST(COALESCE(p_page, 1), 1);
  v_old    text := current_setting('app.depositor_id', true);
  r        record;
BEGIN
  -- Один проход сводки (V131): счёт — окном по тем же строкам, что и
  -- страница. Прежде сводка считалась дважды — для «всего» и для страницы.
  FOR r IN SELECT n.*, count(*) OVER () AS total
             FROM audit_net(NULLIF(p_object_type, ''), NULL, p_depositor, NULLIF(p_actor, ''),
                            p_from::timestamptz, (p_to + 1)::timestamptz) n
            ORDER BY n.event_id DESC
            LIMIT v_size OFFSET (v_page - 1) * v_size LOOP
    v_total := r.total;
    -- Ссылки (поставщик, товар, место доставки) — данные клиента под RLS:
    -- словами их можно прочитать только в контексте этого клиента.
    PERFORM set_config('app.depositor_id', COALESCE(r.depositor_id::text, ''), true);
    v_rows := v_rows || jsonb_build_object(
      'eventId', r.event_id,
      'at', r.at,
      'actor', r.actor,
      'actorName', (SELECT u.full_name FROM app_user u WHERE u.login = r.actor),
      'depositorName', (SELECT d.org_name FROM depositor d WHERE d.depositor_id = r.depositor_id),
      'objectType', r.object_type,
      'objectId', r.object_id,
      'objectLabel', r.object_label,
      'action', r.action,
      'part', r.part,
      'fields', (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                          'field', c.key,
                          'fieldKey', COALESCE(af.text_key, 'audit.field.' || c.key),
                          'oldText', audit_value(af.kind, c.value -> 'o'),
                          'newText', audit_value(af.kind, c.value -> 'n'))
                        ORDER BY COALESCE(af.sort_order, 0)), '[]'::jsonb)
                   FROM jsonb_each(r.changes) c
                   LEFT JOIN audit_field af ON af.source_table = r.source_table
                                           AND af.column_name = c.key));
  END LOOP;
  PERFORM set_config('app.depositor_id', COALESCE(v_old, ''), true);
  IF v_total IS NULL THEN
    -- страница за концом списка (или пусто): счёт отдельно, строк нет
    SELECT count(*) INTO v_total
      FROM audit_net(NULLIF(p_object_type, ''), NULL, p_depositor, NULLIF(p_actor, ''),
                     p_from::timestamptz, (p_to + 1)::timestamptz);
  END IF;
  RETURN jsonb_build_object('total', v_total, 'page', v_page, 'pageSize', v_size, 'rows', v_rows);
END
$function$;

-- ---------- Сводка событий --------------------------------------------------

CREATE OR REPLACE FUNCTION audit_net(p_object_type text, p_object_id text, p_depositor uuid, p_actor text, p_from timestamp with time zone, p_to timestamp with time zone)
 RETURNS TABLE(event_id bigint, at timestamp with time zone, actor text, depositor_id uuid, object_type text, object_id text, object_label text, source_table text, action text, part text, changes jsonb)
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'dbo', 'pg_temp'
 SET jit TO 'off'
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
           (array_agg(e.action ORDER BY e.audit_id DESC))[1] AS last_action,
           -- изменения событий группы по порядку: итог по полям считается
           -- внутри группы (ниже), без общей сортировки всех полей журнала
           array_agg(e.changes ORDER BY e.audit_id) AS chs
      FROM ev e
     GROUP BY e.tx, e.object_type, e.object_id, e.part
  ),
  net AS (
    SELECT g.*,
           CASE WHEN g.first_action = 'login_failed' THEN 'login_failed'
                WHEN g.first_action = 'create' AND g.last_action = 'delete' THEN NULL
                WHEN g.first_action = 'create' THEN 'create'
                WHEN g.last_action = 'delete' THEN 'delete'
                ELSE 'update' END AS act
      FROM grp g
  )
  SELECT n.event_id, n.at, n.actor::text, n.depositor_id, n.object_type::text, n.object_id, n.object_label,
         n.source_table::text,
         CASE WHEN n.act = 'update' AND n.part IS NULL AND n.object_type IN ('receipts', 'orders', 'transfers')
                   AND m.changes ? 'status_id' THEN 'status' ELSE n.act END::text,
         n.part,
         COALESCE(m.changes, '{}'::jsonb)
    FROM net n
    -- Итог по полям одной группы: «было» — из первого события, «стало» —
    -- из последнего. Подзапрос над массивом самой группы, а не над всем
    -- журналом: линейно по числу событий (V131; прежде — общая сортировка
    -- всех полей всех событий, на 450 тыс. строк — секунды на диск).
    CROSS JOIN LATERAL (
      SELECT jsonb_object_agg(k.key, jsonb_strip_nulls(jsonb_build_object('o', k.oo, 'n', k.nn))) AS changes
        FROM (SELECT f.key,
                     CASE WHEN n.act IN ('create', 'login_failed') THEN NULL
                          ELSE (array_agg(f.value -> 'o' ORDER BY u.i))[1] END AS oo,
                     CASE WHEN n.act = 'delete' THEN NULL
                          ELSE (array_agg(f.value -> 'n' ORDER BY u.i DESC))[1] END AS nn
                FROM unnest(n.chs) WITH ORDINALITY AS u(c, i)
                CROSS JOIN LATERAL jsonb_each(u.c) AS f
               GROUP BY f.key) k
       WHERE n.act <> 'update' OR k.oo IS DISTINCT FROM k.nn
    ) m
   WHERE n.act IS NOT NULL
     -- сохранили без настоящих изменений (например, состав «как был»)
     AND NOT (n.act = 'update' AND m.changes IS NULL);
END
$function$

;

ALTER FUNCTION audit_history(text, text) SET jit = off;
ALTER FUNCTION admin_audit_history(text, text) SET jit = off;
