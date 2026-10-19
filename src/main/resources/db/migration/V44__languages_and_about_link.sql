-- =====================================================================
-- V44. Языки портала и ссылка на страницу «О нас»
-- =====================================================================
-- 1. Отключаем языки, для которых переводов нет.
-- 2. Адрес внешней страницы «О нас» — своим значением на каждый язык.
-- 3. Тексты для справочника языков и для выпадающего списка выбора.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Казахский и нидерландский выключаем
-- ---------------------------------------------------------------------
-- Механизм был готов с V25: v_locales отдаёт только is_active, то есть
-- переключатель показывал четыре языка просто потому, что все четыре
-- включены. Переводов у kk и nl нет ни для одного из 1326 ключей —
-- человек, выбрав такой язык, увидел бы английский (откат на язык
-- портала) и решил, что портал сломан.
--
-- Это данные, а не схема: включить язык обратно можно из портала,
-- справочником языков, без миграции.
UPDATE dictionary_locale SET is_active = false WHERE code IN ('kk', 'nl');

-- Язык по умолчанию обязан быть включённым: на него откатываются все
-- недостающие переводы, и выключенный default означал бы портал
-- вообще без текстов.
UPDATE dictionary_locale SET is_active = true WHERE is_default;

-- ---------------------------------------------------------------------
-- 2. Ссылка «О нас»
-- ---------------------------------------------------------------------
-- Своей страницы «О нас» у портала больше нет: кнопка открывает сайт
-- оператора в новой вкладке. Адрес хранится здесь, а не в настройках
-- приложения и не отдельной таблицей, по трём причинам:
--
--   * ui_text по устройству — это «строка на каждый язык», а адрес
--     и нужен свой на каждый язык;
--   * экран правки уже есть («Администрирование → Тексты интерфейса»),
--     то есть адрес меняется без пересборки и без доступа к базе;
--   * нет перевода — сработает откат на язык портала, и это именно
--     то, что нужно: нет казахской страницы — откроется английская.
--
-- Колонка operator.about_content при этом осиротела: содержимое
-- страницы больше не наше. Не удаляю — удалять колонку из применённой
-- схемы дороже, чем оставить её пустой.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('about.url', 'about', 'url', NULL,
   'адрес внешней страницы «О нас», свой на каждый язык; пусто — кнопка не показывается')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

-- ---------------------------------------------------------------------
-- 3. Тексты справочника языков и выбора языка
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.aria.choose_language', 'common', 'aria', NULL,
   'имя списка выбора языка для программ чтения с экрана'),
  ('references.button.languages', 'references', 'button', NULL, NULL),
  ('references.title.languages', 'references', 'title', NULL, NULL),
  ('references.hint.languages', 'references', 'hint', NULL, NULL),
  ('references.column.shown', 'references', 'column', NULL,
   'показывать язык в списке выбора'),
  ('references.column.default_language', 'references', 'column', NULL,
   'язык портала по умолчанию — на него откатываются недостающие переводы'),
  ('references.column.translations', 'references', 'column', NULL,
   'сколько текстов заведено на этом языке — сразу видно, готов ли язык'),
  ('references.hint.default_language_cannot_be_hidden', 'references', 'hint', NULL, NULL)
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  -- Пока адрес один и тот же: страница оператора существует только
  -- по-английски. Значения разведены по языкам, чтобы заменить
  -- любое из них можно было, не трогая код.
  ('about.url', 'ru', 'https://www.ahlers.com/en/sklad-alers-v-almaty'),
  ('about.url', 'en', 'https://www.ahlers.com/en/sklad-alers-v-almaty'),
  ('common.aria.choose_language', 'ru', 'Язык интерфейса'),
  ('common.aria.choose_language', 'en', 'Interface language'),
  ('references.button.languages', 'ru', 'Языки'),
  ('references.button.languages', 'en', 'Languages'),
  ('references.title.languages', 'ru', 'Языки интерфейса'),
  ('references.title.languages', 'en', 'Interface languages'),
  ('references.hint.languages', 'ru',
   'В списке выбора языка показываются только включённые языки. '
   'Язык по умолчанию выключить нельзя: на него откатываются тексты, '
   'перевода для которых нет.'),
  ('references.hint.languages', 'en',
   'Only enabled languages appear in the language picker. The default '
   'language cannot be disabled: it is what untranslated texts fall back to.'),
  ('references.column.shown', 'ru', 'Показывать'),
  ('references.column.shown', 'en', 'Shown'),
  ('references.column.default_language', 'ru', 'По умолчанию'),
  ('references.column.default_language', 'en', 'Default'),
  ('references.column.translations', 'ru', 'Переводов'),
  ('references.column.translations', 'en', 'Translations'),
  ('references.hint.default_language_cannot_be_hidden', 'ru',
   'Язык по умолчанию выключить нельзя'),
  ('references.hint.default_language_cannot_be_hidden', 'en',
   'The default language cannot be disabled')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- 4. Полный список языков для администратора
-- ---------------------------------------------------------------------
-- v_locales отдаёт только включённые — он для переключателя. Чтобы
-- язык можно было ВКЛЮЧИТЬ обратно, администратору нужен список целиком,
-- включая выключенные.
CREATE OR REPLACE VIEW v_locales_all AS
SELECT l.locale_id, l.code, l.name,
       COALESCE(l.is_default, false) AS is_default,
       COALESCE(l.is_active, false) AS is_active,
       l.sort_order,
       (SELECT count(*) FROM ui_text t WHERE t.locale_id = l.locale_id) AS text_count
FROM dictionary_locale l;
ALTER VIEW v_locales_all SET (security_invoker = true);
GRANT SELECT ON v_locales_all TO portal_app;
COMMENT ON VIEW v_locales_all IS
  'Все языки, включая выключенные, со числом заведённых переводов — '
  'для справочника языков в администрировании';
