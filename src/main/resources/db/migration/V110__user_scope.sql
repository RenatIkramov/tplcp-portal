SET search_path = dbo;

-- =====================================================================
-- V110. Доступ человека: поклажедатели и склады — в карточке пользователя
-- =====================================================================
-- Решения заказчика (11.11.2026):
--
--   * склады выдаются ЧЕЛОВЕКУ, у поклажедателя этой настройки больше
--     нет (таблица depositor_warehouse уходит);
--   * неотмеченный склад закрывает и выбор (шапка, черновик, Excel),
--     и данные — его запасы и документы не показываются;
--   * «все поклажедатели» и «все склады» — признаки самого человека,
--     а не отметка всех строк: новый клиент или склад попадает
--     в доступ сам;
--   * роль «поклажедатель» — ровно один поклажедатель (проверяет
--     сервер, AdminUserService), флажка «все поклажедатели» у неё нет;
--   * экраны «Доступы менеджеров» и «Доступы сотрудников склада» убраны:
--     всё про доступ человека — в его карточке.
--
-- Как база узнаёт, чьи склады: DepositorScope кладёт в сеанс базы рядом
-- с app.depositor_id логин вошедшего — app.user_login. Логина нет
-- (служебные запросы, проверки под portal_app) — ограничения складов
-- нет: это не вход человека, а работа самой системы. Поклажедатель
-- по-прежнему закрыт RLS без всяких оговорок.

-- ---------------------------------------------------------------------
-- 1. Признаки «все»
-- ---------------------------------------------------------------------
ALTER TABLE app_user
  ADD COLUMN all_depositors boolean NOT NULL DEFAULT false,
  ADD COLUMN all_warehouses boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN app_user.all_depositors IS
  'доступны все поклажедатели портала, и новые тоже (V110); у роли depositor всегда false';
COMMENT ON COLUMN app_user.all_warehouses IS
  'доступны все склады, и новые тоже (V110); false — только user_warehouse_access';

-- ---------------------------------------------------------------------
-- 2. Перенос: никто не теряет того, что видел
-- ---------------------------------------------------------------------
-- Клиенты: склады их поклажедателя переходят к ним самим. Не было
-- у поклажедателя списка — значит, были доступны все: признак остаётся.
INSERT INTO user_warehouse_access (user_id, warehouse_id)
SELECT DISTINCT u.user_id, dw.warehouse_id
  FROM app_user u
  JOIN enum_user_role r ON r.id = u.role_id AND r.code = 'depositor'
  JOIN user_depositor_access a ON a.user_id = u.user_id
  JOIN depositor_warehouse dw ON dw.depositor_id = a.depositor_id
ON CONFLICT (user_id, warehouse_id) DO NOTHING;

UPDATE app_user u SET all_warehouses = false
  FROM enum_user_role r
 WHERE r.id = u.role_id AND r.code = 'depositor'
   AND EXISTS (SELECT 1 FROM user_depositor_access a
                 JOIN depositor_warehouse dw ON dw.depositor_id = a.depositor_id
                WHERE a.user_id = u.user_id);

-- Сотрудник склада и раньше видел только свои склады. Поклажедателей
-- ему выводили по остаткам на этих складах; теперь склады закрывают
-- данные сами, и «все поклажедатели» даёт то же самое.
UPDATE app_user u SET all_warehouses = false, all_depositors = true
  FROM enum_user_role r
 WHERE r.id = u.role_id AND r.code = 'staff';

-- Администратор работает по всему порталу.
UPDATE app_user u SET all_depositors = true
  FROM enum_user_role r
 WHERE r.id = u.role_id AND r.code = 'admin';

-- Поклажедатель с двумя организациями или без организации миграцию
-- не останавливает: портал продолжит работать, а правило потребует
-- выбрать одного при следующем сохранении карточки. Назовём таких.
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT u.login, count(a.depositor_id) AS n
      FROM app_user u
      JOIN enum_user_role ro ON ro.id = u.role_id AND ro.code = 'depositor'
      LEFT JOIN user_depositor_access a ON a.user_id = u.user_id
     GROUP BY u.login
    HAVING count(a.depositor_id) <> 1
  LOOP
    RAISE NOTICE 'V110: у поклажедателя % выдано организаций: % (нужна ровно одна)', r.login, r.n;
  END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 3. Кто спрашивает и какие склады ему открыты
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION current_user_login()
RETURNS text
LANGUAGE sql STABLE AS $$
  SELECT nullif(current_setting('app.user_login', true), '')
$$;
GRANT EXECUTE ON FUNCTION current_user_login() TO portal_app;

-- NULL — ограничений нет: логина в сеансе нет или у человека «все склады».
-- Пустой массив — не открыто ничего (и для неизвестного логина тоже).
-- SECURITY DEFINER: app_user и user_warehouse_access приложению
-- напрямую не читаются.
CREATE OR REPLACE FUNCTION current_warehouses()
RETURNS text[]
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = dbo AS $$
  SELECT CASE
           WHEN current_user_login() IS NULL THEN NULL
           ELSE (SELECT CASE WHEN u.all_warehouses THEN NULL
                             ELSE COALESCE((SELECT array_agg(a.warehouse_id::text)
                                              FROM user_warehouse_access a
                                             WHERE a.user_id = u.user_id), '{}'::text[])
                        END
                   FROM app_user u
                  WHERE u.login = current_user_login())
         END
$$;
GRANT EXECUTE ON FUNCTION current_warehouses() TO portal_app;

-- Открыт ли склад. Склад не указан (черновик ещё без склада) — открыт.
-- (SELECT …) вокруг функции — чтобы база считала список один раз
-- на запрос, а не на каждую строку. Имя схемы в теле — обязательно:
-- функция без SET search_path (иначе её нельзя встроить в запрос),
-- а вызывают её под путём поиска вызывающего.
CREATE OR REPLACE FUNCTION warehouse_open(p_warehouse_id text)
RETURNS boolean
LANGUAGE sql STABLE AS $$
  SELECT p_warehouse_id IS NULL
      OR (SELECT dbo.current_warehouses()) IS NULL
      OR p_warehouse_id = ANY ((SELECT dbo.current_warehouses())::text[])
$$;
GRANT EXECUTE ON FUNCTION warehouse_open(text) TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Данные: закрытый склад закрывает запасы и документы
-- ---------------------------------------------------------------------
-- Политики RESTRICTIVE складываются с поклажедательской через «И».
-- Строки состава закрываются сами: их политика смотрит на шапку
-- документа, а шапка уже отобрана.
CREATE POLICY warehouse_scope ON stock AS RESTRICTIVE FOR ALL
  USING (warehouse_open(warehouse_id)) WITH CHECK (warehouse_open(warehouse_id));
CREATE POLICY warehouse_scope ON receipts AS RESTRICTIVE FOR ALL
  USING (warehouse_open(warehouse_id)) WITH CHECK (warehouse_open(warehouse_id));
CREATE POLICY warehouse_scope ON orders AS RESTRICTIVE FOR ALL
  USING (warehouse_open(warehouse_id)) WITH CHECK (warehouse_open(warehouse_id));
-- Перемещение видно, если открыт склад «откуда» или «куда»; записать
-- можно, только если открыты оба — иначе человек отправил бы товар
-- на склад, которого сам не видит.
CREATE POLICY warehouse_scope ON transfers AS RESTRICTIVE FOR ALL
  USING ((from_warehouse_id IS NULL AND to_warehouse_id IS NULL)
         OR (from_warehouse_id IS NOT NULL AND warehouse_open(from_warehouse_id))
         OR (to_warehouse_id IS NOT NULL AND warehouse_open(to_warehouse_id)))
  WITH CHECK (warehouse_open(from_warehouse_id) AND warehouse_open(to_warehouse_id));

-- ---------------------------------------------------------------------
-- 5. Выбор: список складов — склады человека
-- ---------------------------------------------------------------------
-- Представление, как и прежде, без security_invoker: у warehouse нет RLS,
-- а склады человека отбирает warehouse_open.
CREATE OR REPLACE VIEW v_warehouses AS
SELECT w.warehouse_id, w.code, w.name, w.address, w.responsible, w.phone,
       w.email, w.is_active, w.area_m2, w.work_hours
  FROM warehouse w
 WHERE w.is_active AND warehouse_open(w.warehouse_id);
GRANT SELECT ON v_warehouses TO portal_app;

-- ---------------------------------------------------------------------
-- 6. Поклажедатели человека
-- ---------------------------------------------------------------------
-- «Все поклажедатели» — все действующие. У роли «поклажедатель»
-- признак не действует: только своя организация. Особый случай
-- сотрудника склада (клиенты по остаткам на его складах) ушёл:
-- склады закрывают данные сами.
DROP FUNCTION depositors_for_user(text);
CREATE FUNCTION depositors_for_user(p_login text)
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar, contract_number varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT d.depositor_id, d.code, d.org_name, d.contract_number
    FROM app_user u
    JOIN enum_user_role r ON r.id = u.role_id
    JOIN depositor d ON (
          (u.all_depositors AND r.code <> 'depositor' AND d.is_active IS DISTINCT FROM false)
       OR EXISTS (SELECT 1 FROM user_depositor_access a
                   WHERE a.user_id = u.user_id AND a.depositor_id = d.depositor_id))
   WHERE u.login = p_login
     AND u.is_active                      -- §6.2: заблокированный не видит ничего
   ORDER BY d.org_name
$$;
GRANT EXECUTE ON FUNCTION depositors_for_user(text) TO portal_app;

-- ---------------------------------------------------------------------
-- 7. Администрирование
-- ---------------------------------------------------------------------
-- Список пользователей: признаки «все» и списки — экран сам складывает
-- из них колонку «Организация / зона» на языке человека (прежде её
-- собирала база русским текстом).
DROP FUNCTION admin_users();
CREATE FUNCTION admin_users()
RETURNS TABLE (user_id text, login varchar, full_name varchar, role_code varchar, role_name varchar,
               user_position varchar, organization varchar, phone varchar, is_active boolean,
               must_change_password boolean, last_login_at timestamp, password_changed_at timestamptz,
               all_depositors boolean, all_warehouses boolean,
               depositor_codes text, depositor_names text, warehouse_names text,
               hide_billing_tab boolean, hide_services_tab boolean, can_create_receipts boolean,
               can_create_orders boolean, can_create_transfers boolean, can_upload_sku_excel boolean,
               can_upload_packaging_excel boolean, can_edit_transport boolean,
               user_status_code varchar, user_status_name varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.user_id::text, u.login, u.full_name, r.code, enum_name('enum_user_role', r.id::text, r.name)::varchar,
         u.position, u.organization, u.phone, u.is_active, u.must_change_password,
         u.last_login_at, u.password_changed_at,
         u.all_depositors AND r.code <> 'depositor', u.all_warehouses,
         COALESCE((SELECT string_agg(d.code, ', ' ORDER BY d.code)
                     FROM user_depositor_access a
                     JOIN depositor d ON d.depositor_id = a.depositor_id
                    WHERE a.user_id = u.user_id), ''),
         COALESCE((SELECT string_agg(d.org_name, ', ' ORDER BY d.org_name)
                     FROM user_depositor_access a
                     JOIN depositor d ON d.depositor_id = a.depositor_id
                    WHERE a.user_id = u.user_id), ''),
         COALESCE((SELECT string_agg(w.name, ', ' ORDER BY w.name)
                     FROM user_warehouse_access a
                     JOIN warehouse w ON w.warehouse_id = a.warehouse_id
                    WHERE a.user_id = u.user_id), ''),
         COALESCE(s.hide_billing_tab, false),
         COALESCE(s.hide_services_tab, false), COALESCE(s.can_create_receipts, true),
         COALESCE(s.can_create_orders, true), COALESCE(s.can_create_transfers, true),
         COALESCE(s.can_upload_sku_excel, true), COALESCE(s.can_upload_packaging_excel, true),
         COALESCE(s.can_edit_transport, true),
         us.code,
         enum_name('enum_user_status', us.id::text, us.name)::varchar
    FROM app_user u
    LEFT JOIN enum_user_status us ON us.code = user_status_code(u.is_active, u.must_change_password)
    LEFT JOIN enum_user_role r ON r.id = u.role_id
    LEFT JOIN user_settings s ON s.user_id = u.user_id
   ORDER BY r.sort_order, u.full_name
$$;
GRANT EXECUTE ON FUNCTION admin_users() TO portal_app;

CREATE OR REPLACE FUNCTION admin_set_user_scope(p_user_id uuid, p_all_depositors boolean,
                                                p_all_warehouses boolean)
RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  UPDATE app_user u
     SET all_depositors = p_all_depositors
                          AND NOT EXISTS (SELECT 1 FROM enum_user_role r
                                           WHERE r.id = u.role_id AND r.code = 'depositor'),
         all_warehouses = p_all_warehouses
   WHERE u.user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION admin_set_user_scope(uuid, boolean, boolean) TO portal_app;

-- Сколько организаций выдано — для правила «у поклажедателя ровно одна».
CREATE OR REPLACE FUNCTION admin_user_depositor_count(p_user_id uuid)
RETURNS integer
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT count(*)::int FROM user_depositor_access WHERE user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION admin_user_depositor_count(uuid) TO portal_app;

-- Список «Доступы поклажедателей» — без складов: их у поклажедателя нет.
DROP FUNCTION admin_depositor_access();
CREATE FUNCTION admin_depositor_access()
RETURNS TABLE (depositor_id uuid, code varchar, org_name varchar)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT d.depositor_id, d.code, d.org_name FROM depositor d ORDER BY d.org_name
$$;
GRANT EXECUTE ON FUNCTION admin_depositor_access() TO portal_app;

-- Где используется склад — без складов поклажедателей.
DROP FUNCTION warehouse_usage(varchar);
CREATE FUNCTION warehouse_usage(p_warehouse_id varchar)
RETURNS TABLE (receipts bigint, orders bigint, transfers bigint, stock_rows bigint, staff bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM receipts  WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM orders    WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM transfers WHERE from_warehouse_id = p_warehouse_id
                                            OR to_warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM stock     WHERE warehouse_id = p_warehouse_id),
         (SELECT count(*) FROM user_warehouse_access WHERE warehouse_id = p_warehouse_id)
$$;
GRANT EXECUTE ON FUNCTION warehouse_usage(varchar) TO portal_app;

-- Склады поклажедателя уходят совсем.
DROP FUNCTION admin_depositor_warehouses(uuid);
DROP FUNCTION admin_set_depositor_warehouses(uuid, text[]);
DROP TABLE depositor_warehouse;

-- ---------------------------------------------------------------------
-- 8. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('admin.text.all_depositors', 'admin', 'text', 'admin', 'список пользователей, зона доступа (V110)'),
  ('admin.text.no_depositors_assigned', 'admin', 'text', 'admin', 'список пользователей, зона доступа (V110)'),
  ('admin.text.no_warehouses_assigned', 'admin', 'text', 'admin', 'список пользователей, зона доступа (V110)'),
  ('admin.label.all_depositors', 'admin', 'label', 'admin', 'карточка пользователя, флажок (V110)'),
  ('admin.label.all_warehouses', 'admin', 'label', 'admin', 'карточка пользователя, флажок (V110)'),
  ('admin.hint.all_depositors', 'admin', 'hint', 'admin', 'карточка пользователя (V110)'),
  ('admin.hint.warehouses_of_a_user', 'admin', 'hint', 'admin', 'карточка пользователя (V110)'),
  ('admin.hint.depositor_role_one', 'admin', 'hint', 'admin', 'карточка пользователя, роль «поклажедатель» (V110)'),
  ('admin.tab.sections', 'admin', 'tab', 'admin', 'карточка доступов поклажедателя (V110)'),
  ('error.user.depositor_exactly_one', 'server', 'message', 'admin',
   'сообщение сервера; собирается по ключу в PortalMessages (V110)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.text.all_depositors', 'ru', 'все поклажедатели'),
    ('admin.text.all_depositors', 'en', 'all depositors'),
    ('admin.text.no_depositors_assigned', 'ru', 'поклажедатели не назначены'),
    ('admin.text.no_depositors_assigned', 'en', 'no depositors assigned'),
    ('admin.text.no_warehouses_assigned', 'ru', 'склады не назначены'),
    ('admin.text.no_warehouses_assigned', 'en', 'no warehouses assigned'),
    ('admin.label.all_depositors', 'ru', 'Доступны все поклажедатели'),
    ('admin.label.all_depositors', 'en', 'All depositors are available'),
    ('admin.label.all_warehouses', 'ru', 'Доступны все склады'),
    ('admin.label.all_warehouses', 'en', 'All warehouses are available'),
    ('admin.hint.all_depositors', 'ru', 'Новые поклажедатели станут доступны сами — отмечать их не нужно.'),
    ('admin.hint.all_depositors', 'en', 'New depositors become available automatically — no need to tick them.'),
    ('admin.hint.warehouses_of_a_user', 'ru', 'Неотмеченный склад человек не видит: ни в выборе склада, ни в запасах и документах. С флажком «Доступны все склады» новые склады станут доступны сами.'),
    ('admin.hint.warehouses_of_a_user', 'en', 'An unticked warehouse is hidden from the user: from warehouse choice, stock and documents alike. With "All warehouses are available", new warehouses become available automatically.'),
    ('admin.hint.depositor_role_one', 'ru', 'Пользователь с ролью «Поклажедатель» работает за одну организацию — выберите её.'),
    ('admin.hint.depositor_role_one', 'en', 'A user with the Depositor role works for one organisation — choose it.'),
    ('admin.tab.sections', 'ru', 'Разделы'),
    ('admin.tab.sections', 'en', 'Sections'),
    ('error.user.depositor_exactly_one', 'ru', 'Пользователю с ролью «Поклажедатель» нужно выбрать ровно одного поклажедателя.'),
    ('error.user.depositor_exactly_one', 'en', 'A user with the Depositor role must have exactly one depositor.'),
    ('admin.hint.depositors_of_a_user', 'ru', 'Отметьте клиентов, с которыми человек работает.'),
    ('admin.hint.depositors_of_a_user', 'en', 'Tick the depositors this user works with.'),
    -- склад нельзя удалить, пока он выдан людям — уже не только сотрудникам склада
    ('error.usage.staff_access', 'ru', 'доступах пользователей ({count})'),
    ('error.usage.staff_access', 'en', 'user access rights ({count})')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Тексты убранных экранов и складов поклажедателя.
CREATE TEMP TABLE _dead_keys (text_key varchar PRIMARY KEY);
INSERT INTO _dead_keys (text_key) VALUES
  ('admin.title.assigning_depositors_to_managers'),
  ('admin.title.warehouses_and_depositors_availabl'),
  ('admin.title.warehouses_available_to_depositors'),
  ('admin.column.manager'),
  ('admin.column.employee'),
  ('admin.column.accessible_warehouses'),
  ('admin.hint.depositor_warehouses'),
  ('admin.tab.sections_and_warehouses'),
  ('error.usage.depositor_access'),
  ('admin.button.manager_access'),
  ('admin.button.warehouse_staff_access');
DELETE FROM _dead_keys d
 WHERE EXISTS (SELECT 1 FROM table_column c WHERE c.text_key = d.text_key OR c.group_key = d.text_key);
DELETE FROM ui_text t USING _dead_keys d WHERE t.text_key = d.text_key;
DELETE FROM ui_text_key k USING _dead_keys d WHERE k.text_key = d.text_key;
DROP TABLE _dead_keys;

-- ---------------------------------------------------------------------
-- 9. Проверки
-- ---------------------------------------------------------------------
DO $$
BEGIN
  IF (SELECT count(*) FROM pg_policies WHERE schemaname = 'dbo' AND policyname = 'warehouse_scope') <> 4 THEN
    RAISE EXCEPTION 'политик warehouse_scope не четыре';
  END IF;
  IF to_regclass('dbo.depositor_warehouse') IS NOT NULL THEN
    RAISE EXCEPTION 'depositor_warehouse не удалена';
  END IF;
  -- без логина в сеансе склады не ограничены
  IF current_warehouses() IS NOT NULL THEN
    RAISE EXCEPTION 'без логина в сеансе current_warehouses() должна быть NULL';
  END IF;
END $$;
