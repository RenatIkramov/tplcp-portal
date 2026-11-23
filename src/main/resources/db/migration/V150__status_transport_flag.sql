SET search_path = dbo;

-- =====================================================================
-- V150. Правка транспорта после отправки — галочкой у статуса
-- =====================================================================
-- Решение заказчика 21.11.2026. Какие статусы разрешают править
-- транспорт отправленного документа, решает признак статуса
-- allows_transport_edit (V16). Его нельзя было поменять из портала, и он
-- стоял только у 10 «Принят WMS». Теперь это галочка в окне значения
-- перечисления «Статусы» (группа «Перечисления»), а правка пишется в
-- журнал действий.
--
-- Статусы общие для всех документов, но у каждого типа свой набор
-- (enum_status_scope): 20, 21, 40 бывают только у заказов и перемещений.
-- Поэтому одной галочки у статуса хватает и без пары «тип + статус».
--
-- По умолчанию транспорт правится, пока документ не отгружен и не принят:
-- 10, 20, 21, 30, 40.
--
-- Признак «да/нет» у значения перечисления описан в реестре в общем виде:
-- flag_column, flag_label_key, flag_hint_key. Окно значения рисует его
-- галочкой у любого перечисления, у которого он задан.

ALTER TABLE enum_registry
  ADD COLUMN flag_column    varchar(40),
  ADD COLUMN flag_label_key varchar(80),
  ADD COLUMN flag_hint_key  varchar(80);
COMMENT ON COLUMN enum_registry.flag_column IS
  'признак «да/нет» значения, который правится галочкой в окне значения (V150)';

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.label.allows_transport_edit', 'admin', 'label', 'admin', 'статус: можно править транспорт отправленного документа (V150)'),
  ('admin.hint.allows_transport_edit',  'admin', 'hint',  'admin', 'статус: можно править транспорт отправленного документа (V150)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.label.allows_transport_edit', 'ru', 'Можно править транспорт'),
    ('admin.label.allows_transport_edit', 'en', 'Transport can be edited'),
    ('admin.hint.allows_transport_edit', 'ru', 'В этом статусе клиент может поменять транспорт уже отправленного документа (если у него есть право «Разрешить правку транспортного раздела»); правка уходит в WMS.'),
    ('admin.hint.allows_transport_edit', 'en', 'In this status the client can change the transport of a document already sent (if they have the “Allow editing transport details” right); the change is sent to WMS.')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

UPDATE enum_registry
   SET flag_column = 'allows_transport_edit',
       flag_label_key = 'admin.label.allows_transport_edit',
       flag_hint_key = 'admin.hint.allows_transport_edit'
 WHERE enum_key = 'enum_status';

UPDATE enum_status SET allows_transport_edit = (code IN ('10', '20', '21', '30', '40'));

-- в журнале признак подписан (V116 оставил его без подписи)
UPDATE audit_field SET text_key = 'admin.label.allows_transport_edit'
 WHERE source_table = 'enum_status' AND column_name = 'allows_transport_edit';
