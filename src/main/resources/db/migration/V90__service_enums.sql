-- ---------------------------------------------------------------------
-- V90. Служебные перечисления: статус пользователя, передача транспорта,
--      загрузки Excel, обмен с WMS, источник документа
-- ---------------------------------------------------------------------
-- Список согласован 29.10.2026 («Перечисления — согласование списков»,
-- §9). До сих пор это были английские слова в колонках (sent, failed,
-- checked, portal_to_wms, portal…) или флаги. Слово — не код: его
-- нельзя перевести, и у WMS оно может оказаться другим. Теперь у
-- каждого — перечисление с кодом 01…, имя английское, русское — перевод.
--
-- Как хранится: в тех же колонках, но КОДОМ ('01', '02'…), с внешним
-- ключом на код перечисления. Отдельный числовой идентификатор здесь
-- лишний: значения служебные, их ставит программа, а не человек,
-- и читать журнал с кодом проще, чем с номером строки справочника.
--
-- Статус пользователя не хранится: он выводится из двух признаков
-- (is_active, must_change_password), и третье поле означало бы две
-- правды о том же самом. Перечисление даёт ему код и перевод.
--
-- sync_run.status по умолчанию был 'running', но так его никто не
-- писал: журнал заполняется готовой записью. Умолчание снято — статус
-- в журнал пишется явно.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Перечисления
-- ---------------------------------------------------------------------
DO $create$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['enum_user_status', 'enum_transport_state', 'enum_import_status',
                           'enum_import_kind', 'enum_sync_status', 'enum_sync_direction',
                           'enum_document_origin'] LOOP
    EXECUTE format($f$
      CREATE TABLE %I (
        id          smallint     PRIMARY KEY,
        code        varchar(30)  NOT NULL UNIQUE CHECK (code ~ '^[0-9]{2}$'),
        name        varchar(255) NOT NULL,
        sort_order  smallint,
        is_active   boolean      NOT NULL DEFAULT true
      )$f$, t);
    EXECUTE format('GRANT SELECT ON %I TO portal_app', t);
  END LOOP;
END
$create$;

COMMENT ON TABLE enum_user_status     IS 'статус пользователя (V90) — выводится из is_active и must_change_password';
COMMENT ON TABLE enum_transport_state IS 'передача транспорта в WMS (V90)';
COMMENT ON TABLE enum_import_status   IS 'статус загрузки Excel (V90)';
COMMENT ON TABLE enum_import_kind     IS 'что загружали из Excel (V90)';
COMMENT ON TABLE enum_sync_status     IS 'статус обмена с WMS (V90)';
COMMENT ON TABLE enum_sync_direction  IS 'направление обмена с WMS (V90)';
COMMENT ON TABLE enum_document_origin IS 'где создан документ (V90)';

INSERT INTO enum_user_status (id, code, name, sort_order) VALUES
  (1, '01', 'Active', 1), (2, '02', 'Awaiting password change', 2), (3, '03', 'Blocked', 3);
INSERT INTO enum_transport_state (id, code, name, sort_order) VALUES
  (1, '01', 'Sent', 1), (2, '02', 'Not sent', 2);
INSERT INTO enum_import_status (id, code, name, sort_order) VALUES
  (1, '01', 'Checked', 1), (2, '02', 'Applied', 2), (3, '03', 'Cancelled', 3);
INSERT INTO enum_import_kind (id, code, name, sort_order) VALUES
  (1, '01', 'Items', 1), (2, '02', 'Packaging', 2), (3, '03', 'Order', 3);
INSERT INTO enum_sync_status (id, code, name, sort_order) VALUES
  (1, '01', 'Success', 1), (2, '02', 'Failed', 2), (3, '03', 'Retry scheduled', 3);
INSERT INTO enum_sync_direction (id, code, name, sort_order) VALUES
  (1, '01', 'Portal → WMS', 1), (2, '02', 'WMS → portal', 2);
INSERT INTO enum_document_origin (id, code, name, sort_order) VALUES
  (1, '01', 'Portal', 1), (2, '02', 'WMS', 2);

INSERT INTO enum_registry (enum_key, code_column, has_symbol, text_key, sort_order) VALUES
  ('enum_user_status',     'code', false, 'enums.name.enum_user_status',     105),
  ('enum_document_origin', 'code', false, 'enums.name.enum_document_origin', 120),
  ('enum_transport_state', 'code', false, 'enums.name.enum_transport_state', 130),
  ('enum_import_kind',     'code', false, 'enums.name.enum_import_kind',     140),
  ('enum_import_status',   'code', false, 'enums.name.enum_import_status',   150),
  ('enum_sync_direction',  'code', false, 'enums.name.enum_sync_direction',  160),
  ('enum_sync_status',     'code', false, 'enums.name.enum_sync_status',     170)
ON CONFLICT (enum_key) DO NOTHING;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT v.enum_key, v.id, l.locale_id, v.name
  FROM (VALUES
    ('enum_user_status', '1', 'Активен'),
    ('enum_user_status', '2', 'Ожидает смены пароля'),
    ('enum_user_status', '3', 'Заблокирован'),
    ('enum_transport_state', '1', 'Передан'),
    ('enum_transport_state', '2', 'Не передан'),
    ('enum_import_status', '1', 'Проверен'),
    ('enum_import_status', '2', 'Записан'),
    ('enum_import_status', '3', 'Отменён'),
    ('enum_import_kind', '1', 'Товары'),
    ('enum_import_kind', '2', 'Упаковки'),
    ('enum_import_kind', '3', 'Заказ'),
    ('enum_sync_status', '1', 'Успешно'),
    ('enum_sync_status', '2', 'Ошибка'),
    ('enum_sync_status', '3', 'Повтор запланирован'),
    ('enum_sync_direction', '1', 'Портал → WMS'),
    ('enum_sync_direction', '2', 'WMS → портал'),
    ('enum_document_origin', '1', 'Портал'),
    ('enum_document_origin', '2', 'WMS')
  ) AS v(enum_key, id, name)
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- ---------------------------------------------------------------------
-- 2. Слова → коды
-- ---------------------------------------------------------------------
-- Незнакомое слово — остановка: куда его отнести, решает человек.
DO $words$
DECLARE
  bad text;
BEGIN
  SELECT string_agg(DISTINCT w, ', ') INTO bad FROM (
    SELECT 'origin=' || origin AS w FROM receipts  WHERE origin NOT IN ('portal', 'wms')
    UNION ALL SELECT 'origin=' || origin FROM orders    WHERE origin NOT IN ('portal', 'wms')
    UNION ALL SELECT 'origin=' || origin FROM transfers WHERE origin NOT IN ('portal', 'wms')
    UNION ALL SELECT 'excel_import.status=' || status FROM excel_import
               WHERE status NOT IN ('checked', 'applied', 'cancelled')
    UNION ALL SELECT 'excel_import.kind=' || kind FROM excel_import
               WHERE kind NOT IN ('sku', 'packaging', 'order')
    UNION ALL SELECT 'sync_run.status=' || status FROM sync_run
               WHERE status NOT IN ('success', 'failed', 'retry')
    UNION ALL SELECT 'sync_run.direction=' || direction FROM sync_run
               WHERE direction NOT IN ('portal_to_wms', 'wms_to_portal')
  ) a;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'значения без перевода в код: %', bad;
  END IF;
END
$words$;

-- Документы: источник и передача транспорта.
-- RLS: Flyway ходит суперпользователем и видит строки всех поклажедателей;
-- непереведённая строка не дала бы поставить внешний ключ ниже.
ALTER TABLE receipts  DROP CONSTRAINT IF EXISTS ck_receipts_origin;
ALTER TABLE orders    DROP CONSTRAINT IF EXISTS ck_orders_origin;
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS ck_transfers_origin;
ALTER TABLE receipts  DROP CONSTRAINT IF EXISTS ck_receipts_transport_state;
ALTER TABLE orders    DROP CONSTRAINT IF EXISTS ck_orders_transport_state;
ALTER TABLE transfers DROP CONSTRAINT IF EXISTS ck_transfers_transport_state;

UPDATE receipts  SET origin = CASE origin WHEN 'portal' THEN '01' ELSE '02' END;
UPDATE orders    SET origin = CASE origin WHEN 'portal' THEN '01' ELSE '02' END;
UPDATE transfers SET origin = CASE origin WHEN 'portal' THEN '01' ELSE '02' END;
UPDATE receipts  SET transport_state = CASE transport_state WHEN 'sent' THEN '01' ELSE '02' END
 WHERE transport_state IS NOT NULL;
UPDATE orders    SET transport_state = CASE transport_state WHEN 'sent' THEN '01' ELSE '02' END
 WHERE transport_state IS NOT NULL;
UPDATE transfers SET transport_state = CASE transport_state WHEN 'sent' THEN '01' ELSE '02' END
 WHERE transport_state IS NOT NULL;

ALTER TABLE receipts  ALTER COLUMN origin SET DEFAULT '01';
ALTER TABLE orders    ALTER COLUMN origin SET DEFAULT '01';
ALTER TABLE transfers ALTER COLUMN origin SET DEFAULT '01';

ALTER TABLE receipts  ADD CONSTRAINT fk_receipts_origin  FOREIGN KEY (origin) REFERENCES enum_document_origin (code);
ALTER TABLE orders    ADD CONSTRAINT fk_orders_origin    FOREIGN KEY (origin) REFERENCES enum_document_origin (code);
ALTER TABLE transfers ADD CONSTRAINT fk_transfers_origin FOREIGN KEY (origin) REFERENCES enum_document_origin (code);
ALTER TABLE receipts  ADD CONSTRAINT fk_receipts_transport_state
  FOREIGN KEY (transport_state) REFERENCES enum_transport_state (code);
ALTER TABLE orders    ADD CONSTRAINT fk_orders_transport_state
  FOREIGN KEY (transport_state) REFERENCES enum_transport_state (code);
ALTER TABLE transfers ADD CONSTRAINT fk_transfers_transport_state
  FOREIGN KEY (transport_state) REFERENCES enum_transport_state (code);

-- Загрузки Excel
UPDATE excel_import
   SET status = CASE status WHEN 'checked' THEN '01' WHEN 'applied' THEN '02' ELSE '03' END,
       kind   = CASE kind WHEN 'sku' THEN '01' WHEN 'packaging' THEN '02' ELSE '03' END;
ALTER TABLE excel_import ALTER COLUMN status SET DEFAULT '01';
ALTER TABLE excel_import ADD CONSTRAINT fk_excel_import_status
  FOREIGN KEY (status) REFERENCES enum_import_status (code);
ALTER TABLE excel_import ADD CONSTRAINT fk_excel_import_kind
  FOREIGN KEY (kind) REFERENCES enum_import_kind (code);

-- Журнал обмена с WMS
UPDATE sync_run
   SET status    = CASE status WHEN 'success' THEN '01' WHEN 'failed' THEN '02' ELSE '03' END,
       direction = CASE direction WHEN 'portal_to_wms' THEN '01' ELSE '02' END;
ALTER TABLE sync_run ALTER COLUMN status DROP DEFAULT;
ALTER TABLE sync_run ADD CONSTRAINT fk_sync_run_status
  FOREIGN KEY (status) REFERENCES enum_sync_status (code);
ALTER TABLE sync_run ADD CONSTRAINT fk_sync_run_direction
  FOREIGN KEY (direction) REFERENCES enum_sync_direction (code);

-- ---------------------------------------------------------------------
-- 3. Статус пользователя и журнал загрузок — с именами
-- ---------------------------------------------------------------------
-- Колонки дописаны в конец. Тип результата у функции меняется, поэтому
-- её приходится пересоздать — CREATE OR REPLACE этого не умеет.
CREATE OR REPLACE FUNCTION user_status_code(p_is_active boolean, p_must_change_password boolean)
RETURNS varchar
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE WHEN NOT COALESCE(p_is_active, false) THEN '03'
              WHEN COALESCE(p_must_change_password, false) THEN '02'
              ELSE '01' END::varchar
$$;
COMMENT ON FUNCTION user_status_code(boolean, boolean) IS
  'статус пользователя (V90): 03 заблокирован, 02 ожидает смены пароля, иначе 01 активен';
GRANT EXECUTE ON FUNCTION user_status_code(boolean, boolean) TO portal_app;
DROP FUNCTION admin_users();
CREATE FUNCTION admin_users()
 RETURNS TABLE(user_id text, login character varying, full_name character varying, role_code character varying, role_name character varying, user_position character varying, organization character varying, phone character varying, is_active boolean, must_change_password boolean, last_login_at timestamp without time zone, password_changed_at timestamp with time zone, scope text, depositor_codes text, hide_billing_tab boolean, hide_services_tab boolean, can_create_receipts boolean, can_create_orders boolean, can_create_transfers boolean, can_upload_sku_excel boolean, can_upload_packaging_excel boolean, can_edit_transport boolean, user_status_code character varying, user_status_name character varying)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'dbo'
AS $function$
  SELECT u.user_id::text, u.login, u.full_name, r.code, enum_name('enum_user_role', r.id::text, r.name)::varchar,
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
$function$;
GRANT EXECUTE ON FUNCTION admin_users() TO portal_app;

DROP FUNCTION admin_excel_imports(uuid, integer);
CREATE FUNCTION admin_excel_imports(p_depositor_id uuid DEFAULT NULL::uuid, p_limit integer DEFAULT 200)
 RETURNS TABLE(import_id text, started_at timestamp with time zone, depositor_code character varying, depositor_name character varying, login character varying, full_name character varying, file_name character varying, row_count integer, created_rows integer, updated_rows integer, error_count integer, status character varying, import_kind character varying, status_name character varying, import_kind_name character varying)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'dbo'
AS $function$
  SELECT e.import_id::text,
         e.started_at,
         d.code,
         d.org_name,
         u.login,
         u.full_name,
         e.file_name,
         e.row_count,
         e.created_rows,
         e.updated_rows,
         e.error_count,
         e.status,
         e.kind,
         enum_name('enum_import_status', st.id::text, st.name)::varchar,
         enum_name('enum_import_kind', ik.id::text, ik.name)::varchar
  FROM excel_import e
  LEFT JOIN enum_import_status st ON st.code = e.status
  LEFT JOIN enum_import_kind ik ON ik.code = e.kind
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC
  LIMIT p_limit;
$function$;
GRANT EXECUTE ON FUNCTION admin_excel_imports(uuid, integer) TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('enums.name.enum_user_status',     'admin', 'text', NULL, 'название перечисления (V90)'),
  ('enums.name.enum_document_origin', 'admin', 'text', NULL, 'название перечисления (V90)'),
  ('enums.name.enum_transport_state', 'admin', 'text', NULL, 'название перечисления (V90)'),
  ('enums.name.enum_import_kind',     'admin', 'text', NULL, 'название перечисления (V90)'),
  ('enums.name.enum_import_status',   'admin', 'text', NULL, 'название перечисления (V90)'),
  ('enums.name.enum_sync_direction',  'admin', 'text', NULL, 'название перечисления (V90)'),
  ('enums.name.enum_sync_status',     'admin', 'text', NULL, 'название перечисления (V90)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('enums.name.enum_user_status',     'ru', 'Статусы пользователя'),
    ('enums.name.enum_user_status',     'en', 'User statuses'),
    ('enums.name.enum_document_origin', 'ru', 'Источник документа'),
    ('enums.name.enum_document_origin', 'en', 'Document origin'),
    ('enums.name.enum_transport_state', 'ru', 'Передача транспорта'),
    ('enums.name.enum_transport_state', 'en', 'Transport transfer'),
    ('enums.name.enum_import_kind',     'ru', 'Виды загрузки Excel'),
    ('enums.name.enum_import_kind',     'en', 'Excel import kinds'),
    ('enums.name.enum_import_status',   'ru', 'Статусы загрузки Excel'),
    ('enums.name.enum_import_status',   'en', 'Excel import statuses'),
    ('enums.name.enum_sync_direction',  'ru', 'Направления обмена с WMS'),
    ('enums.name.enum_sync_direction',  'en', 'WMS exchange directions'),
    ('enums.name.enum_sync_status',     'ru', 'Статусы обмена с WMS'),
    ('enums.name.enum_sync_status',     'en', 'WMS exchange statuses')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Отдельная плашка «Сменить пароль» в списке пользователей заменена
-- статусом «Ожидает смены пароля» — её подпись больше не нужна.
DELETE FROM ui_text     WHERE text_key = 'admin.text.must_change_password';
DELETE FROM ui_text_key WHERE text_key = 'admin.text.must_change_password';

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF user_status_code(true, false) <> '01' OR user_status_code(true, true) <> '02'
     OR user_status_code(false, true) <> '03' THEN
    RAISE EXCEPTION 'статус пользователя выводится неверно';
  END IF;
  IF (SELECT count(*) FROM enum_registry
       WHERE enum_key IN ('enum_user_status', 'enum_transport_state', 'enum_import_status',
                          'enum_import_kind', 'enum_sync_status', 'enum_sync_direction',
                          'enum_document_origin')) <> 7 THEN
    RAISE EXCEPTION 'не все перечисления V90 видны на вкладке «Перечисления»';
  END IF;
  PERFORM set_config('app.locale', 'ru', true);
  IF enum_name('enum_document_origin', '1', 'Portal') IS DISTINCT FROM 'Портал' THEN
    RAISE EXCEPTION 'нет русского перевода источника документа';
  END IF;
  PERFORM set_config('app.locale', '', true);
END
$check$;
