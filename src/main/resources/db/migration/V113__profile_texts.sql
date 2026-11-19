SET search_path = dbo;

-- V113. Личный кабинет на данных портала (11.11.2026).
--
-- Ниже шапки кабинета было демо прототипа: реквизиты и менеджер
-- с прочерками, адрес склада в коде, русские заголовки без перевода.
-- Теперь кабинет — js/portal/profile.js и GET /api/me/profile
-- (ProfileService). Здесь — заголовки, которых в каталоге не было.

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('profile.title.your_manager', 'profile', 'title', 'profile', 'личный кабинет поклажедателя (V113)'),
  ('profile.text.no_manager', 'profile', 'text', 'profile', 'личный кабинет поклажедателя (V113)'),
  ('profile.title.access_zone', 'profile', 'title', 'profile', 'личный кабинет менеджера, сотрудника склада, администратора (V113)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('profile.title.your_manager', 'ru', 'Ваш менеджер'),
    ('profile.title.your_manager', 'en', 'Your account manager'),
    ('profile.text.no_manager', 'ru', 'Менеджер ещё не назначен'),
    ('profile.text.no_manager', 'en', 'No account manager assigned yet'),
    ('profile.title.access_zone', 'ru', 'Зона доступа'),
    ('profile.title.access_zone', 'en', 'Access scope')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
