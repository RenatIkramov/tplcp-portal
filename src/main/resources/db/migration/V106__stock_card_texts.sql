SET search_path = dbo;

-- V106. Тексты карточки остатка — на ключи.
--
-- Карточку остатка («Запасы» → щелчок по строке) перенесли из прототипа
-- в stock-card.js (11.11.2026). Плашка «Есть брак» / «Без замечаний»
-- и сообщение кнопки «История движения» были записаны в коде по-русски
-- и на английском интерфейсе такими и оставались.

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('stock.text.has_defects', 'stock', 'text', 'stock',
   'карточка остатка: у товара есть брак (V106)'),
  ('stock.text.no_issues', 'stock', 'text', 'stock',
   'карточка остатка: брака нет (V106)'),
  ('stock.message.movement_history_later', 'stock', 'message', 'stock',
   'карточка остатка, кнопка «История движения»: отчёт появится вместе с отчётами WMS (V106)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('stock.text.has_defects', 'ru', 'Есть брак'),
    ('stock.text.has_defects', 'en', 'Has defects'),
    ('stock.text.no_issues', 'ru', 'Без замечаний'),
    ('stock.text.no_issues', 'en', 'No issues'),
    ('stock.message.movement_history_later', 'ru', 'Отчёт «История движения» появится позже'),
    ('stock.message.movement_history_later', 'en', 'The "Movement history" report will be available later')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
