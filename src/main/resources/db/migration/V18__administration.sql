-- V18__administration.sql
-- Учётная запись администратора портала, смена пароля и справочник
-- «Пользователи» в режиме администрирования.
SET search_path = dbo;

-- =====================================================================
-- 1. Пароль, заданный администратором, нужно сменить при первом входе
-- =====================================================================
-- Администратор не должен знать пароль пользователя: он выдаёт временный,
-- а портал при первом входе требует задать свой. До смены пароля
-- пользователь не получает данных — только экран смены пароля.
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN app_user.must_change_password IS
  'Пароль выдан администратором: до смены пользователю доступен только экран смены пароля';

-- =====================================================================
-- 2. Учётная запись администратора портала
-- =====================================================================
-- Логин «admin», первый пароль — Nachalo-2026!, со смены которого начинается
-- работа: флаг must_change_password стоит, портал сразу попросит свой пароль.
INSERT INTO app_user (login, password_hash, full_name, role_id, position,
                      organization, phone, is_active, must_change_password,
                      password_changed_at)
SELECT 'admin',
       '$2b$10$uEAViuskGZAy8ZGvF96rEu2NO8f2CdP7As50Xj1OPPV1V4dYtGfwe',
       'Администратор портала', 4, 'Администратор портала',
       'Ahlers Logistics', NULL, true, true, now()
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE lower(login) = 'admin');

-- Администратору доступны все поклажедатели (§6.4 — выбор всё равно
-- остаётся осознанным: при нескольких доступных портал ждёт выбора).
INSERT INTO user_depositor_access (user_id, depositor_id)
SELECT u.user_id, d.depositor_id
FROM app_user u, depositor d
WHERE lower(u.login) = 'admin'
  AND NOT EXISTS (SELECT 1 FROM user_depositor_access a
                   WHERE a.user_id = u.user_id AND a.depositor_id = d.depositor_id);

-- =====================================================================
-- 3. Смена пароля
-- =====================================================================
-- Приложение никогда не пишет в app_user напрямую — пароль меняется
-- через функцию. Так в одном месте видно всё, что происходит при смене:
-- сбрасываются неудачные попытки, снимается блокировка и флаг «сменить».
CREATE OR REPLACE FUNCTION auth_set_password(p_user_id uuid, p_hash text,
                                            p_must_change boolean DEFAULT false)
RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  UPDATE app_user
     SET password_hash = p_hash,
         password_changed_at = now(),
         must_change_password = p_must_change,
         failed_login_count = 0,
         locked_until = NULL
   WHERE user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION auth_set_password(uuid, text, boolean) TO portal_app;

-- Хеш по идентификатору: нужен, чтобы проверить текущий пароль, когда
-- пользователь меняет его сам.
CREATE OR REPLACE FUNCTION auth_password_hash(p_user_id uuid)
RETURNS varchar
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT password_hash FROM app_user WHERE user_id = p_user_id
$$;
GRANT EXECUTE ON FUNCTION auth_password_hash(uuid) TO portal_app;

-- Логин больше не читается без учёта флага смены пароля: функция входа
-- пересоздаётся с дополнительной колонкой.
DROP FUNCTION IF EXISTS auth_user_by_login(text);
CREATE FUNCTION auth_user_by_login(p_login text)
RETURNS TABLE (user_id uuid, login varchar, password_hash varchar, full_name varchar,
               role_code varchar, is_active boolean, locked_until timestamptz,
               failed_login_count smallint, must_change_password boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.user_id, u.login, u.password_hash, u.full_name, r.code, u.is_active,
         u.locked_until, u.failed_login_count, u.must_change_password
  FROM app_user u
  LEFT JOIN enum_user_role r ON r.id = u.role_id
  WHERE lower(u.login) = lower(p_login)
$$;
GRANT EXECUTE ON FUNCTION auth_user_by_login(text) TO portal_app;

-- =====================================================================
-- 4. Справочник «Пользователи» для режима администрирования
-- =====================================================================
-- Одна функция отдаёт всё, что показывает справочник: роль, зону (своя
-- организация для поклажедателя, список поклажедателей для менеджера,
-- склады для кладовщика) и персональные настройки видимости (§6.3).
CREATE OR REPLACE FUNCTION admin_users()
RETURNS TABLE (user_id uuid, login varchar, full_name varchar, role_code varchar,
               role_name varchar, user_position varchar, organization varchar, phone varchar,
               is_active boolean, must_change_password boolean,
               last_login_at timestamp, password_changed_at timestamptz,
               scope text, depositor_codes text,
               hide_transfers_tab boolean, hide_billing_tab boolean,
               hide_services_tab boolean, can_create_receipts boolean,
               can_create_orders boolean, can_create_transfers boolean,
               can_upload_sku_excel boolean, can_upload_packaging_excel boolean,
               can_edit_transport boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.user_id, u.login, u.full_name, r.code, r.name,
         u.position, u.organization, u.phone, u.is_active, u.must_change_password,
         u.last_login_at, u.password_changed_at,
         CASE r.code
           WHEN 'depositor' THEN COALESCE(u.organization, '—')
           WHEN 'staff'     THEN COALESCE((SELECT string_agg(w.name, ', ' ORDER BY w.name)
                                             FROM user_warehouse_access a
                                             JOIN warehouse w ON w.warehouse_id = a.warehouse_id
                                            WHERE a.user_id = u.user_id), 'Склады не назначены')
           WHEN 'manager'   THEN COALESCE((SELECT string_agg(d.org_name, ', ' ORDER BY d.org_name)
                                             FROM user_depositor_access a
                                             JOIN depositor d ON d.depositor_id = a.depositor_id
                                            WHERE a.user_id = u.user_id), 'Поклажедатели не назначены')
           ELSE 'Весь портал'
         END AS scope,
         COALESCE((SELECT string_agg(d.code, ', ' ORDER BY d.code)
                     FROM user_depositor_access a
                     JOIN depositor d ON d.depositor_id = a.depositor_id
                    WHERE a.user_id = u.user_id), '') AS depositor_codes,
         COALESCE(s.hide_transfers_tab, false), COALESCE(s.hide_billing_tab, false),
         COALESCE(s.hide_services_tab, false), COALESCE(s.can_create_receipts, true),
         COALESCE(s.can_create_orders, true), COALESCE(s.can_create_transfers, true),
         COALESCE(s.can_upload_sku_excel, true), COALESCE(s.can_upload_packaging_excel, true),
         COALESCE(s.can_edit_transport, true)
  FROM app_user u
  LEFT JOIN enum_user_role r ON r.id = u.role_id
  LEFT JOIN user_settings s ON s.user_id = u.user_id
  ORDER BY r.sort_order, u.full_name
$$;
GRANT EXECUTE ON FUNCTION admin_users() TO portal_app;

-- Сохранение карточки пользователя: блокировка и галочки видимости.
-- Настройки лежат в отдельной таблице, строки может ещё не быть — поэтому
-- запись идёт «вставить или обновить».
CREATE OR REPLACE FUNCTION admin_save_user(
    p_user_id uuid,
    p_is_active boolean,
    p_hide_transfers_tab boolean, p_hide_billing_tab boolean, p_hide_services_tab boolean,
    p_can_create_receipts boolean, p_can_create_orders boolean, p_can_create_transfers boolean,
    p_can_upload_sku_excel boolean, p_can_upload_packaging_excel boolean,
    p_can_edit_transport boolean)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
BEGIN
  UPDATE app_user SET is_active = p_is_active WHERE user_id = p_user_id;

  INSERT INTO user_settings (user_id, hide_transfers_tab, hide_billing_tab,
      hide_services_tab, can_create_receipts, can_create_orders, can_create_transfers,
      can_upload_sku_excel, can_upload_packaging_excel, can_edit_transport)
  VALUES (p_user_id, p_hide_transfers_tab, p_hide_billing_tab, p_hide_services_tab,
      p_can_create_receipts, p_can_create_orders, p_can_create_transfers,
      p_can_upload_sku_excel, p_can_upload_packaging_excel, p_can_edit_transport)
  ON CONFLICT (user_id) DO UPDATE SET
      hide_transfers_tab = EXCLUDED.hide_transfers_tab,
      hide_billing_tab = EXCLUDED.hide_billing_tab,
      hide_services_tab = EXCLUDED.hide_services_tab,
      can_create_receipts = EXCLUDED.can_create_receipts,
      can_create_orders = EXCLUDED.can_create_orders,
      can_create_transfers = EXCLUDED.can_create_transfers,
      can_upload_sku_excel = EXCLUDED.can_upload_sku_excel,
      can_upload_packaging_excel = EXCLUDED.can_upload_packaging_excel,
      can_edit_transport = EXCLUDED.can_edit_transport;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_save_user(uuid, boolean, boolean, boolean, boolean,
    boolean, boolean, boolean, boolean, boolean, boolean) TO portal_app;

-- Роль пользователя по логину — проверка «администратор ли это» перед
-- действиями администрирования. Решение всё равно принимает сервер.
CREATE OR REPLACE FUNCTION auth_role_by_login(p_login text)
RETURNS varchar
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT r.code FROM app_user u
  JOIN enum_user_role r ON r.id = u.role_id
  WHERE lower(u.login) = lower(p_login) AND u.is_active
$$;
GRANT EXECUTE ON FUNCTION auth_role_by_login(text) TO portal_app;
