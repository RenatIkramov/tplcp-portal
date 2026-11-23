SET search_path = dbo;

-- =====================================================================
-- V155. «Используются комплекты» — настройка клиента
-- =====================================================================
-- Решение заказчика 22.11.2026. Галочка на вкладке «Разделы» карточки
-- поклажедателя. Выключена — у клиента нет экранов комплектов: вкладки
-- «Справочники → Комплекты», вкладки «Состав» и признака «Комплект»
-- в карточке товара, отметки «комплект» в «Запасах»; состав вручную
-- не задаётся.
--
-- Расчёты при этом не меняются (решение «только скрыть экраны»):
-- остатки, резерв и раскладка в документах по-прежнему идут по составу.
-- По умолчанию включено у всех клиентов.
--
-- Запись — отдельной функцией: прежнюю admin_save_depositor_settings
-- зовут и проверки, и она не должна молча сбрасывать новую галочку.

ALTER TABLE depositor_settings
  ADD COLUMN kits_enabled boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN depositor_settings.kits_enabled IS
  'у клиента используются комплекты: экраны комплектов показаны (V155)';

-- состав возвращаемых колонок меняется — это другая функция
DROP FUNCTION depositor_settings_of(uuid);
CREATE FUNCTION depositor_settings_of(p_depositor_id uuid)
RETURNS TABLE (hide_transfers_tab boolean,
               price_in_receipts  boolean,
               price_in_orders    boolean,
               kits_enabled       boolean)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $settings$
  SELECT COALESCE(s.hide_transfers_tab, false),
         COALESCE(s.price_in_receipts,  false),
         COALESCE(s.price_in_orders,    false),
         COALESCE(s.kits_enabled,       true)
    FROM depositor d
    LEFT JOIN depositor_settings s ON s.depositor_id = d.depositor_id
   WHERE d.depositor_id = p_depositor_id;
$settings$;
REVOKE EXECUTE ON FUNCTION depositor_settings_of(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION depositor_settings_of(uuid) TO portal_app;

CREATE FUNCTION admin_save_depositor_kits(p_depositor_id uuid, p_enabled boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $save$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM depositor WHERE depositor_id = p_depositor_id) THEN
    RAISE EXCEPTION 'Поклажедателя % нет', p_depositor_id;
  END IF;
  INSERT INTO depositor_settings (depositor_id, kits_enabled)
  VALUES (p_depositor_id, COALESCE(p_enabled, true))
  ON CONFLICT (depositor_id) DO UPDATE SET kits_enabled = excluded.kits_enabled
   WHERE depositor_settings.kits_enabled IS DISTINCT FROM excluded.kits_enabled;
END;
$save$;
REVOKE EXECUTE ON FUNCTION admin_save_depositor_kits(uuid, boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION admin_save_depositor_kits(uuid, boolean) TO portal_app;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.label.kits_enabled', 'admin', 'label', 'admin', 'карточка клиента, «Разделы»: используются комплекты (V155)'),
  ('admin.hint.kits_enabled',  'admin', 'hint',  'admin', 'карточка клиента, «Разделы»: что значит галочка комплектов (V155)'),
  ('audit.field.kits_enabled', 'audit', 'label', 'audit', 'журнал действий (V155)'),
  ('error.kit.disabled',       'server', 'message', 'sku', 'у клиента не используются комплекты (V155)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.label.kits_enabled', 'ru', 'Используются комплекты'),
    ('admin.label.kits_enabled', 'en', 'Kits are used'),
    ('admin.hint.kits_enabled', 'ru', 'Если галочка снята, у клиента нет вкладки «Комплекты», вкладки «Состав» и признака «Комплект» в карточке товара, отметки «комплект» в «Запасах». Остатки и документы по-прежнему считаются по составу, если он есть.'),
    ('admin.hint.kits_enabled', 'en', 'With the box cleared, the client has no “Kits” tab, no “Composition” tab or “Kit” flag in the item card and no “kit” mark in “Stock”. Stock and documents still follow the composition if there is one.'),
    ('audit.field.kits_enabled', 'ru', 'Используются комплекты'),
    ('audit.field.kits_enabled', 'en', 'Kits are used'),
    ('error.kit.disabled', 'ru', 'У клиента не используются комплекты — включите их в настройках поклажедателя'),
    ('error.kit.disabled', 'en', 'Kits are not used for this client — turn them on in the depositor settings')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

INSERT INTO audit_field (source_table, column_name, kind, text_key, sort_order)
VALUES ('depositor_settings', 'kits_enabled', 'bool', 'audit.field.kits_enabled', 53);
