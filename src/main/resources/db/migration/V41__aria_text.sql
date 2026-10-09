-- =====================================================================
-- V41. Имя поля для программ чтения с экрана
-- =====================================================================
-- У галочки «выбрать все строки» в шапке таблиц документов нет и не
-- может быть видимой подписи: колонка шириной в саму галочку. Для
-- человека это понятно по месту, для программы чтения с экрана —
-- нет: она называет такое поле просто «флажок».
--
-- Имя задаётся атрибутом aria-label, а он, как и подсказка в поле,
-- обязан переводиться: иначе на английском портале поля зовутся
-- по-русски. Разметка несёт ключ в data-i18n-aria, текст лежит здесь.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.aria.select_all_rows', 'common', 'aria', NULL,
   'имя галочки «выбрать все строки» для программ чтения с экрана')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('common.aria.select_all_rows', 'ru', 'Выбрать все строки'),
  ('common.aria.select_all_rows', 'en', 'Select all rows')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
