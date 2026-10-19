-- =====================================================================
-- V42. Тексты кнопок «Назад» в администрировании
-- =====================================================================
-- Экран-деталь `view-admin-user-edit` переиспользуется четырьмя разными
-- местами: карточкой пользователя, доступами менеджера, доступами
-- кладовщика и карточкой поклажедателя. А кнопка «Назад» была одна
-- и прошита в разметке — «к пользователям», в раздел «Справочники».
-- Человек заходил из «Администрирования» и возвращался в другой раздел,
-- то есть кнопка вела не назад, а в сторону.
--
-- Теперь возврат задаёт тот, кто экран открыл (PORTAL.setBack), и ему
-- нужны тексты на все случаи. Два уже были — «к пользователям»
-- и «к доступам менеджеров»; здесь недостающие два.
--
-- Значение текста содержит стрелку «←»: так заведены все остальные
-- кнопки возврата в портале, и разметка не дописывает её сама.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.button.back_to_depositor_access', 'admin', 'button', NULL,
   'возврат из карточки поклажедателя в «Доступы поклажедателей»'),
  ('admin.button.back_to_staff_access', 'admin', 'button', NULL,
   'возврат из доступов кладовщика в «Доступы сотрудников склада»')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('admin.button.back_to_depositor_access', 'ru', '← Назад к доступам поклажедателей'),
  ('admin.button.back_to_depositor_access', 'en', '← Back to depositor access'),
  ('admin.button.back_to_staff_access', 'ru', '← Назад к доступам сотрудников склада'),
  ('admin.button.back_to_staff_access', 'en', '← Back to warehouse staff access')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
