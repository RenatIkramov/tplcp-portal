SET search_path = dbo;

-- V111. Настройки клиента — вкладки карточки поклажедателя (11.11.2026).
--
-- Разделы, поля и наименования полей поклажедателя открывались из
-- отдельного списка «Администрирование → Доступы → Доступы
-- поклажедателей». С V110 в группе «Доступы» других вкладок не осталось,
-- и решение заказчика — всё про клиента в одной карточке: «Общие
-- справочники → Поклажедатели», вкладки «Реквизиты · Разделы · Поля ·
-- Наименования полей». Группа «Доступы» и её список убраны.

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES ('portal.tab.requisites', 'portal', 'tab', 'portal',
        'карточка поклажедателя, вкладка реквизитов (V111)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('portal.tab.requisites', 'ru', 'Реквизиты'),
    ('portal.tab.requisites', 'en', 'Details')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Тексты убранного списка и группы.
CREATE TEMP TABLE _dead_keys (text_key varchar PRIMARY KEY);
INSERT INTO _dead_keys (text_key) VALUES
  ('admin.button.accesses'),
  ('admin.button.depositor_access'),
  ('admin.button.back_to_depositor_access'),
  ('admin.text.no_depositors_defined');
DELETE FROM _dead_keys d
 WHERE EXISTS (SELECT 1 FROM table_column c WHERE c.text_key = d.text_key OR c.group_key = d.text_key);
DELETE FROM ui_text t USING _dead_keys d WHERE t.text_key = d.text_key;
DELETE FROM ui_text_key k USING _dead_keys d WHERE k.text_key = d.text_key;
DROP TABLE _dead_keys;
