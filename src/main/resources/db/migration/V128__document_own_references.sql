SET search_path = dbo;

-- =====================================================================
-- V128. Документ ссылается только на записи своего клиента (аудит
-- 11.11.2026, S14).
--
-- ЧТО БЫЛО. Черновик принимал sku_id, supplier_id, consignee_id,
-- delivery_point_id как есть. Внешний ключ RLS не проверяет: можно было
-- сослаться на uuid записи другого клиента. Утечки не было (чужое имя
-- представление под RLS не покажет), но в документе оставалась «дырявая»
-- строка, которая ушла бы в WMS.
--
-- КАК ТЕПЕРЬ. Триггер перед записью сверяет клиента ссылки с клиентом
-- документа — явно, по depositor_id, а не через видимость под RLS: так
-- правило одинаково и для портала, и для загрузки из WMS (этап 8), и для
-- запросов руками. Отказ — SQLSTATE 23T14, его портал показывает как
-- «запись другого клиента» (ApiErrorHandler). Срабатывает только когда
-- ссылку пишут: смена статуса у прежних документов его не задевает.
-- =====================================================================

CREATE FUNCTION document_own_references() RETURNS trigger
LANGUAGE plpgsql
SET search_path = dbo, pg_temp
AS $$
DECLARE
  v_dep   uuid;
  v_field text;
BEGIN
  IF TG_TABLE_NAME IN ('receipt_details', 'order_details', 'transfer_details') THEN
    IF NEW.sku_id IS NULL THEN
      RETURN NEW;
    END IF;
    EXECUTE format('SELECT depositor_id FROM %I WHERE doc_id = $1',
                   CASE TG_TABLE_NAME WHEN 'receipt_details' THEN 'receipts'
                                      WHEN 'order_details' THEN 'orders'
                                      ELSE 'transfers' END)
       INTO v_dep USING NEW.doc_id;
    IF v_dep IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sku s WHERE s.sku_id = NEW.sku_id AND s.depositor_id = v_dep) THEN
      v_field := 'sku_id';
    END IF;
  ELSIF TG_TABLE_NAME = 'receipts' THEN
    IF NEW.supplier_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM supplier s
                        WHERE s.supplier_id = NEW.supplier_id AND s.depositor_id = NEW.depositor_id) THEN
      v_field := 'supplier_id';
    END IF;
  ELSIF TG_TABLE_NAME = 'orders' THEN
    IF NEW.consignee_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM consignee c
                        WHERE c.consignee_id = NEW.consignee_id AND c.depositor_id = NEW.depositor_id) THEN
      v_field := 'consignee_id';
    ELSIF NEW.delivery_point_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM delivery_point p JOIN consignee c ON c.consignee_id = p.consignee_id
                        WHERE p.address_id = NEW.delivery_point_id
                          AND c.depositor_id = NEW.depositor_id) THEN
      v_field := 'delivery_point_id';
    END IF;
  END IF;

  IF v_field IS NOT NULL THEN
    RAISE EXCEPTION 'документ % ссылается на запись другого клиента: %', TG_TABLE_NAME, v_field
      USING ERRCODE = '23T14', COLUMN = v_field;
  END IF;
  RETURN NEW;
END
$$;

CREATE TRIGGER own_references BEFORE INSERT OR UPDATE OF sku_id ON receipt_details
  FOR EACH ROW EXECUTE FUNCTION document_own_references();
CREATE TRIGGER own_references BEFORE INSERT OR UPDATE OF sku_id ON order_details
  FOR EACH ROW EXECUTE FUNCTION document_own_references();
CREATE TRIGGER own_references BEFORE INSERT OR UPDATE OF sku_id ON transfer_details
  FOR EACH ROW EXECUTE FUNCTION document_own_references();
CREATE TRIGGER own_references BEFORE INSERT OR UPDATE OF supplier_id, depositor_id ON receipts
  FOR EACH ROW EXECUTE FUNCTION document_own_references();
CREATE TRIGGER own_references BEFORE INSERT OR UPDATE OF consignee_id, delivery_point_id, depositor_id ON orders
  FOR EACH ROW EXECUTE FUNCTION document_own_references();

-- ---------- Текст отказа ----------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES ('error.document.foreign_reference', 'server', 'message', 'documents',
        'в документе ссылка на товар, поставщика или грузополучателя другого клиента (V128)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.document.foreign_reference', 'ru',
     'В документе выбран товар, поставщик или грузополучатель, которого нет в ваших справочниках'),
    ('error.document.foreign_reference', 'en',
     'The document refers to an item, supplier or consignee that is not in your directories')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
