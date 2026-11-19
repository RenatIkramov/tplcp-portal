SET search_path = dbo;

-- V107. Подсказка окна «⚙ Колонки» — на ключ.
--
-- Окно колонок переехало из прототипа в tables.js (12.11.2026).
-- Подсказка под заголовком была вписана в разметку по-русски и на
-- английском интерфейсе такой и оставалась. У окна фильтров такая же
-- подсказка давно ключом (common.hint.filter_settings).

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES ('common.hint.column_settings', 'common', 'hint', 'common',
        'окно «⚙ Колонки»: как выбрать и упорядочить колонки (V107)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.hint.column_settings', 'ru', 'Отметьте нужные колонки и перетащите для изменения порядка вывода'),
    ('common.hint.column_settings', 'en', 'Tick the columns you need and drag them to change the order')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
