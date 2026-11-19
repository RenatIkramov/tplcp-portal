SET search_path = dbo;

-- V120. Текст отказа «раздел скрыт» (аудит 11.11.2026, S3).
--
-- Скрытые разделы («Перемещения» у поклажедателя, «Биллинг» у
-- пользователя) теперь закрыты и на сервере — SectionGuard. Отказ
-- приходит, только если адрес набран руками: экран в скрытый раздел
-- не ходит.

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('error.access.section_hidden', 'server', 'message', 'server', 'отказ «раздел скрыт» (V120)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.access.section_hidden', 'ru', 'Этот раздел вам недоступен'),
    ('error.access.section_hidden', 'en', 'This section is not available to you')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
