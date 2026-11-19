SET search_path = dbo;

-- V119. Текст отказа «не тот метод» (аудит 11.11.2026, S6).
--
-- Прежде запрос неподдерживаемым методом (DELETE /api/me) и вообще любой
-- отказ самого Spring — неизвестный адрес, нет обязательного параметра,
-- кривой идентификатор — отвечал 500 «внутренняя ошибка» со стеком в
-- журнале. Теперь ApiErrorHandler отвечает их собственным кодом: 404,
-- 405, 400. Для 404 и 400 тексты уже были; для 405 — этот.

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('error.status.method_not_allowed', 'server', 'message', 'server', 'отказ «не тот метод» (V119)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.status.method_not_allowed', 'ru', 'Такое действие по этому адресу недоступно'),
    ('error.status.method_not_allowed', 'en', 'This action is not available at this address')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
