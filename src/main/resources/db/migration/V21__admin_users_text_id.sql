-- V21__admin_users_text_id.sql
-- Идентификатор пользователя в справочнике отдаётся текстом.
--
-- Почему это важно. Во всех остальных функциях и запросах портала мы приводим
-- uuid к text (`user_id::text`, `depositor_id::text`): тогда и приложение, и
-- экран работают с идентификатором одинаково — как со строкой. Здесь колонка
-- осталась типа uuid, JDBC отдавал java.util.UUID, и сравнение со строкой
-- молча не срабатывало. Приводим к общему виду.
--
-- Возвращаемый тип функции менять нельзя на месте, поэтому пересоздаём.
SET search_path = dbo;

DROP FUNCTION IF EXISTS admin_users();

CREATE FUNCTION admin_users()
RETURNS TABLE (user_id text, login varchar, full_name varchar, role_code varchar,
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
  SELECT u.user_id::text, u.login, u.full_name, r.code, r.name,
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
