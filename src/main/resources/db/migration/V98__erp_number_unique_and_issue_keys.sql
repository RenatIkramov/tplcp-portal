-- ---------------------------------------------------------------------
-- V98. Номер документа в ERP клиента — обязательный и уникальный;
--      причины в журнале загрузок — ключом, а не текстом
-- ---------------------------------------------------------------------
-- Замечания заказчика от 03.11.2026 после проверки V97 вживую:
--
--   1. Номер документа в ERP клиента (erp_number) — обязательный уже
--      при сохранении черновика и уникальный в пределах поклажедателя
--      и типа документа. Иначе дубликаты неизбежны: тот же файл Excel,
--      загруженный дважды, давал два одинаковых заказа. Обязательность
--      проверяет портал (DraftService), уникальность держит база —
--      индекс ниже. Регистр и пробелы по краям не различаются:
--      «ЗК-1» и «зк-1 » для человека один номер.
--
--   2. Журнал загрузок хранил причину текстом — на языке того, кто
--      загружал. Администратор с другим языком читал чужой язык.
--      Теперь у причины есть ключ и подстановки, а фразу собирает
--      ImportLogService на языке администратора. Колонка message
--      остаётся: в ней причины прежних загрузок и загрузок товаров
--      и упаковок, у которых ключей пока нет.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Уникальность номера ERP
-- ---------------------------------------------------------------------
-- Уже совпадающие номера (черновики, заведённые при проверках) не дали
-- бы построить индекс. Первый по дате документ сохраняет номер,
-- к остальным дописывается «(2)», «(3)»… — их видно в списке,
-- и номер остаётся узнаваемым. Пустые номера индекс не касаются:
-- документы из WMS приходят и без него.
DO $dedupe$
DECLARE
  t text;
  n int;
BEGIN
  FOREACH t IN ARRAY ARRAY['receipts', 'orders', 'transfers'] LOOP
    EXECUTE format($q$
      WITH ranked AS (
        SELECT doc_id,
               row_number() OVER (PARTITION BY depositor_id, lower(btrim(erp_number))
                                  ORDER BY doc_date NULLS LAST, doc_id) AS rn
          FROM %I
         WHERE NULLIF(btrim(erp_number), '') IS NOT NULL)
      UPDATE %I d
         SET erp_number = left(btrim(d.erp_number), 24) || ' (' || r.rn || ')'
        FROM ranked r
       WHERE r.doc_id = d.doc_id AND r.rn > 1$q$, t, t);
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n > 0 THEN
      RAISE NOTICE 'V98: в % у % документов номер ERP совпадал с уже существующим — дописан номер повтора', t, n;
    END IF;
  END LOOP;
END
$dedupe$;

CREATE UNIQUE INDEX IF NOT EXISTS receipts_erp_number_uk
    ON receipts (depositor_id, lower(btrim(erp_number)))
 WHERE NULLIF(btrim(erp_number), '') IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS orders_erp_number_uk
    ON orders (depositor_id, lower(btrim(erp_number)))
 WHERE NULLIF(btrim(erp_number), '') IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS transfers_erp_number_uk
    ON transfers (depositor_id, lower(btrim(erp_number)))
 WHERE NULLIF(btrim(erp_number), '') IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. Причина в журнале загрузок — ключом
-- ---------------------------------------------------------------------
ALTER TABLE excel_import_issue
  ADD COLUMN IF NOT EXISTS message_key    varchar(80),
  ADD COLUMN IF NOT EXISTS message_params jsonb;
COMMENT ON COLUMN excel_import_issue.message_key IS
  'ключ причины (V98): фразу на языке читающего собирает портал; message — текст на языке загрузившего';

DROP FUNCTION IF EXISTS admin_excel_import_issues(uuid, integer);
CREATE FUNCTION admin_excel_import_issues(p_depositor_id uuid DEFAULT NULL::uuid, p_limit integer DEFAULT 500)
RETURNS TABLE(import_id text, started_at timestamp with time zone, depositor_code character varying,
              depositor_name character varying, login character varying, full_name character varying,
              file_name character varying, row_number integer, row_class character varying,
              kind character varying, column_id character varying, message text, file_value text,
              portal_value text, import_kind character varying,
              message_key character varying, param_names text[], param_values text[])
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = dbo
AS $$
  SELECT i.import_id::text,
         e.started_at,
         d.code,
         d.org_name,
         u.login,
         u.full_name,
         e.file_name,
         i.row_number,
         r.row_class,
         i.kind,
         i.column_id,
         i.message,
         i.file_value,
         i.portal_value,
         e.kind,
         i.message_key,
         -- подстановки двумя массивами: портал собирает из них фразу,
         -- не разбирая JSON
         ARRAY(SELECT p.key FROM jsonb_each_text(i.message_params) p ORDER BY p.key),
         ARRAY(SELECT p.value FROM jsonb_each_text(i.message_params) p ORDER BY p.key)
  FROM excel_import_issue i
  JOIN excel_import e ON e.import_id = i.import_id
  LEFT JOIN excel_import_row r
         ON r.import_id = i.import_id AND r.row_number = i.row_number
  LEFT JOIN depositor d ON d.depositor_id = e.depositor_id
  LEFT JOIN app_user u ON u.user_id = e.user_id
  WHERE (p_depositor_id IS NULL OR e.depositor_id = p_depositor_id)
  ORDER BY e.started_at DESC, i.row_number, i.id
  LIMIT p_limit;
$$;
REVOKE ALL ON FUNCTION admin_excel_import_issues(uuid, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_excel_import_issues(uuid, integer) TO portal_app;

-- ---------------------------------------------------------------------
-- 3. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.k, v.scope, v.element, v.owner, v.note
  FROM (VALUES
    ('error.document.erp_required', 'server', 'message', 'documents',
     'сообщение сервера: без номера документа в ERP клиента черновик не сохраняется (V98)'),
    ('error.document.erp_taken', 'server', 'message', 'documents',
     'сообщение сервера: номер ERP уже занят другим документом того же типа (V98)'),
    ('error.order_import.erp_required', 'server', 'message', 'orders',
     'загрузка заказа: не заполнен номер документа в ERP клиента (V98)'),
    ('error.order_import.erp_taken', 'server', 'message', 'orders',
     'загрузка заказа: заказ с таким номером ERP уже есть (V98)')
  ) AS v(k, scope, element, owner, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.document.erp_required', 'ru', 'Заполните номер документа в ERP — без него документ не сохраняется'),
    ('error.document.erp_required', 'en', 'Fill in the document number in the ERP: the document cannot be saved without it'),
    ('error.document.erp_taken', 'ru', 'Номер «{erp}» уже есть у документа {no}. Номер ERP не должен повторяться'),
    ('error.document.erp_taken', 'en', 'The number “{erp}” is already used by document {no}. ERP numbers must not repeat'),
    ('error.order_import.erp_required', 'ru', 'Заполните номер документа в ERP'),
    ('error.order_import.erp_required', 'en', 'Fill in the document number in the ERP'),
    ('error.order_import.erp_taken', 'ru', 'Заказ с этим номером уже есть: {no}. Возможно, файл уже загружали'),
    ('error.order_import.erp_taken', 'en', 'An order with this number already exists: {no}. The file may have been uploaded already')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
