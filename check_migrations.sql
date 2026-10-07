-- Какие функции администрирования есть в базе
SELECT p.proname AS функция,
       pg_get_function_identity_arguments(p.oid) AS аргументы
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'dbo'
  AND p.proname IN ('admin_create_user','admin_update_user','admin_change_login',
                    'admin_set_depositor_access','admin_user_depositors','admin_depositors',
                    'admin_roles','admin_warehouses','admin_user_warehouses',
                    'admin_set_warehouse_access','admin_depositor_access',
                    'admin_depositor_warehouses','admin_set_depositor_warehouses')
ORDER BY 1;

-- Таблица складов поклажедателя из V20 (должна существовать)
SELECT to_regclass('dbo.depositor_warehouse') AS таблица_V20;
