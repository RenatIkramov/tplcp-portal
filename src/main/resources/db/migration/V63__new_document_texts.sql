-- =====================================================================
-- V63. Тексты нового, ещё не сохранённого документа
-- =====================================================================
-- «Создать новое» больше не пишет документ в базу: карточка открывается
-- пустой, а запись появляется по кнопке «Сохранить». Прежде каждое
-- нажатие оставляло в базе документ с номером, даже если человек сразу
-- закрыл карточку, — номера расходовались, а список зарастал пустыми
-- черновиками.
--
-- Отсюда два новых текста:
--
--   common.text.number_assigned_on_save — подсказка в карточке нового
--     документа. Номер в ней не показан, и это надо объяснить: пустое
--     место на месте номера человек прочтёт как ошибку портала.
--
--   common.message.leave_unsaved_document — вопрос при уходе из карточки,
--     в которой есть что терять. Спрашиваем только когда заполнено хоть
--     что-то: вопрос на пустом месте человек перестаёт читать, и тогда
--     он не поможет там, где нужен.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.text.number_assigned_on_save', 'common', 'text', 'documents',
   'подсказка в карточке нового документа: номер появится после сохранения'),
  ('common.message.leave_unsaved_document', 'common', 'message', 'documents',
   'вопрос при уходе из карточки несохранённого документа')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     owner   = excluded.owner,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('common.text.number_assigned_on_save',
     'Новый документ. Номер будет присвоен при сохранении.',
     'New document. The number is assigned when you save it.'),
    ('common.message.leave_unsaved_document',
     'Документ не сохранён. Уйти и потерять введённое?',
     'The document is not saved. Leave and lose what you entered?')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Ключ без текста портал покажет человеку как есть — проверяем здесь же.
DO $texts$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(k.text_key || ' (' || l.code || ')', ', ')
    INTO missing
    FROM ui_text_key k
    JOIN dictionary_locale l ON l.code IN ('ru', 'en')
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('common.text.number_assigned_on_save',
                        'common.message.leave_unsaved_document')
     AND t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для ключей нового документа: %', missing;
  END IF;
END $texts$;
