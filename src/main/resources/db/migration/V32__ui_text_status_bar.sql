-- V32: подписи строки состояния над таблицами.
--
-- Над таблицами запасов и списков документов стоит строка
-- «Поклажедатель D-001 · данные на 14:32 · Обновить» (решение Р4 — регистры
-- как кэш WMS). «Поклажедатель», «Обновить» и «Повторить» в каталоге уже
-- были, а «данные на» и текст ошибки — нет.
--
-- «данные на» отделено от самого времени: это подпись плюс значение,
-- и в переводе порядок слов другой. Склеивать их в один ключ нельзя.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.text.data_as_of', 'common', 'text', NULL, 'строка состояния над таблицами'),
  ('common.message.could_not_get_the_data', 'common', 'message', NULL,
   'строка состояния над таблицами')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
  ('common.text.data_as_of',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'данные на'),
  ('common.text.data_as_of',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'data as of'),
  ('common.message.could_not_get_the_data',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'),
   'Не удалось получить данные'),
  ('common.message.could_not_get_the_data',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'),
   'Could not get the data')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
