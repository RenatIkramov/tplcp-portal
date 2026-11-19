SET search_path = dbo;

-- V114. Последние следы демо в разметке (11.11.2026).
--
-- * Подвал «Демонстрационный прототип интерфейса — все данные
--   вымышленные · Склад …» и плашка «ДЕМО» убраны: подвал теперь —
--   организация оператора из справочника (js/portal/contacts.js).
-- * «Ошибки интеграции»: четыре выдуманные строки заменены пустым
--   журналом с пояснением — обмена с WMS до этапа 8 нет.
-- * «Ошибки загрузки Excel»: выдуманные строки разметки (их и так
--   заменял настоящий журнал, import-log.js) убраны вместе с ключами.

DELETE FROM ui_text
 WHERE text_key IN (
   'common.text.demonstration_ui_prototype_all_dat',
   'common.text.demo_fictional_data',
   'common.text.portal_wms', 'common.text.portal_wms2',
   'common.text.wms_portal', 'common.text.wms_portal2',
   'common.text.span_class_dot_span_not_processed',
   'common.text.span_class_dot_span_not_processed2',
   'common.text.warehouse_code_not_found_in_mappin',
   'common.text.span_class_dot_span_retry_schedul',
   'common.text.span_class_dot_span_processed',
   'common.text.receipt_request_29_07_xlsx',
   'common.text.flooring_products_xlsx',
   'common.text.shipment_order_28_07_xlsx',
   'common.text.field_expected_date_is_not_filled',
   'common.text.duplicate_sap_code_00944450040',
   'common.text.invalid_number_format_in_column_we');

DELETE FROM ui_text_key
 WHERE text_key IN (
   'common.text.demonstration_ui_prototype_all_dat',
   'common.text.demo_fictional_data',
   'common.text.portal_wms', 'common.text.portal_wms2',
   'common.text.wms_portal', 'common.text.wms_portal2',
   'common.text.span_class_dot_span_not_processed',
   'common.text.span_class_dot_span_not_processed2',
   'common.text.warehouse_code_not_found_in_mappin',
   'common.text.span_class_dot_span_retry_schedul',
   'common.text.span_class_dot_span_processed',
   'common.text.receipt_request_29_07_xlsx',
   'common.text.flooring_products_xlsx',
   'common.text.shipment_order_28_07_xlsx',
   'common.text.field_expected_date_is_not_filled',
   'common.text.duplicate_sap_code_00944450040',
   'common.text.invalid_number_format_in_column_we');

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('admin.text.integration_log_empty', 'admin', 'text', 'admin', 'пустой журнал «Ошибки интеграции» до этапа 8 (V114)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.text.integration_log_empty', 'ru', 'Обмена с WMS ещё не было: журнал появится, когда будет подключена интеграция'),
    ('admin.text.integration_log_empty', 'en', 'No exchange with the WMS yet: the log will appear once the integration is connected')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
