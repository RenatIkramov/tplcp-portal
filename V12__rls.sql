-- V12__rls.sql
-- Этап 2 плана: изоляция данных поклажедателей на уровне БД (§5 спецификации).
--
-- Принцип: приложение в начале транзакции выставляет app.depositor_id,
-- и дальше база сама не отдаёт чужие строки — даже если в запросе забыли фильтр.
--
-- ВАЖНО, две вещи, без которых RLS не работает:
--   1) Владелец таблицы обходит политики. Поэтому приложение должно
--      подключаться НЕ владельцем схемы, а ролью portal_app (ниже).
--   2) FORCE ROW LEVEL SECURITY распространяет политики и на владельца —
--      страховка от ошибки в настройке подключения.
SET search_path = dbo;

-- ---------- Роль приложения ---------------------------------------------------
-- Пароль заменить на свой; в продакшне задавать через переменные окружения.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portal_app') THEN
    CREATE ROLE portal_app LOGIN PASSWORD 'portal_app_dev';
  END IF;
END $$;

GRANT USAGE ON SCHEMA dbo TO portal_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA dbo TO portal_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA dbo TO portal_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA dbo
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO portal_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA dbo
  GRANT USAGE, SELECT ON SEQUENCES TO portal_app;

-- ---------- Текущий поклажедатель --------------------------------------------
-- Функция-обёртка: возвращает NULL, если переменная сессии не выставлена,
-- вместо падения запроса. NULL означает «ни одной строки не видно».
CREATE OR REPLACE FUNCTION current_depositor_id() RETURNS uuid
LANGUAGE sql STABLE AS $$
  SELECT NULLIF(current_setting('app.depositor_id', true), '')::uuid
$$;
GRANT EXECUTE ON FUNCTION current_depositor_id() TO portal_app;

-- ---------- Таблицы с собственным depositor_id -------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'sku','supplier','consignee','product_packaging','product_group','product_type',
    'receipts','orders','transfers',
    'stock','registered_service','service_tariff','invoice','document_photo',
    'depositor_field_label','sync_run','sync_watermark'
  ] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format($p$
      CREATE POLICY depositor_isolation ON %I
        USING (depositor_id = current_depositor_id())
        WITH CHECK (depositor_id = current_depositor_id())
    $p$, t);
  END LOOP;
END $$;

-- sync_run / sync_watermark допускают depositor_id IS NULL (синхронизация по всем).
-- Такие строки служебные и поклажедателю не принадлежат — расширяем политику.
DROP POLICY depositor_isolation ON sync_run;
CREATE POLICY depositor_isolation ON sync_run
  USING (depositor_id IS NULL OR depositor_id = current_depositor_id())
  WITH CHECK (depositor_id IS NULL OR depositor_id = current_depositor_id());
DROP POLICY depositor_isolation ON sync_watermark;
CREATE POLICY depositor_isolation ON sync_watermark
  USING (depositor_id IS NULL OR depositor_id = current_depositor_id())
  WITH CHECK (depositor_id IS NULL OR depositor_id = current_depositor_id());

-- ---------- Сам поклажедатель -------------------------------------------------
ALTER TABLE depositor ENABLE ROW LEVEL SECURITY;
ALTER TABLE depositor FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON depositor
  USING (depositor_id = current_depositor_id());

-- ---------- Строки документов (своего depositor_id не имеют) -----------------
-- Видимость наследуется от документа-заголовка.
ALTER TABLE receipt_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE receipt_details FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON receipt_details
  USING (EXISTS (SELECT 1 FROM receipts r
                  WHERE r.doc_id = receipt_details.doc_id
                    AND r.depositor_id = current_depositor_id()))
  WITH CHECK (EXISTS (SELECT 1 FROM receipts r
                  WHERE r.doc_id = receipt_details.doc_id
                    AND r.depositor_id = current_depositor_id()));

ALTER TABLE order_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_details FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON order_details
  USING (EXISTS (SELECT 1 FROM orders o
                  WHERE o.doc_id = order_details.doc_id
                    AND o.depositor_id = current_depositor_id()))
  WITH CHECK (EXISTS (SELECT 1 FROM orders o
                  WHERE o.doc_id = order_details.doc_id
                    AND o.depositor_id = current_depositor_id()));

ALTER TABLE transfer_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE transfer_details FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON transfer_details
  USING (EXISTS (SELECT 1 FROM transfers t
                  WHERE t.doc_id = transfer_details.doc_id
                    AND t.depositor_id = current_depositor_id()))
  WITH CHECK (EXISTS (SELECT 1 FROM transfers t
                  WHERE t.doc_id = transfer_details.doc_id
                    AND t.depositor_id = current_depositor_id()));

ALTER TABLE delivery_point ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_point FORCE ROW LEVEL SECURITY;
CREATE POLICY depositor_isolation ON delivery_point
  USING (EXISTS (SELECT 1 FROM consignee c
                  WHERE c.consignee_id = delivery_point.consignee_id
                    AND c.depositor_id = current_depositor_id()))
  WITH CHECK (EXISTS (SELECT 1 FROM consignee c
                  WHERE c.consignee_id = delivery_point.consignee_id
                    AND c.depositor_id = current_depositor_id()));

-- ---------- Представления ------------------------------------------------------
-- Представления создавались владельцем схемы и по умолчанию выполняются
-- с его правами, то есть обходили бы RLS. security_invoker заставляет
-- выполнять их с правами вызывающего — политики применяются.
DO $$
DECLARE v text;
BEGIN
  FOREACH v IN ARRAY ARRAY[
    'v_sku_full','v_stock_lines','v_stock_details','v_stock_available',
    'v_receipts','v_orders','v_transfers',
    'v_receipt_lines','v_receipt_line_details',
    'v_order_lines','v_order_line_details',
    'v_transfer_lines','v_transfer_line_details',
    'v_suppliers','v_consignees','v_depositors',
    'v_service_tariffs','v_invoices','v_document_photos'
  ] LOOP
    EXECUTE format('ALTER VIEW %I SET (security_invoker = true)', v);
  END LOOP;
END $$;

-- Непоклажедательские справочники (склады, пользователи, перечисления) RLS
-- не требуют: они общие для портала. Доступ менеджеров к поклажедателям
-- решается на уровне приложения через user_depositor_access (§6.4).
