-- =====================================================================
-- V43. Тексты, которых не хватало подписям, нарисованным кодом
-- =====================================================================
-- Разбирались, почему посреди русского портала попадались английские
-- надписи: вкладка единиц измерения, галочка «показывать архивные»,
-- карточки контрагентов и складов, кнопки «Добавить» и «Создать».
--
-- Причина оказалась одна и в коде, а не в текстах: `T('ключ')`
-- подставляет перевод ОДИН раз — когда собирается строка разметки.
-- Если это случилось до того, как применился язык учётной записи,
-- подпись остаётся на языке портала по умолчанию навсегда: при смене
-- языка `applyTo` ищет узлы с атрибутом `data-i18n`, а у текста,
-- вписанного кодом, атрибута нет. Хуже всего были константы уровня
-- модуля (`const FIELDS = [{label: T(…)}]`) — они считаются при чтении
-- файла браузером, то есть ещё до входа.
--
-- Правка в коде: подписи оборачиваются в `<span data-i18n="…">`,
-- а в константах хранятся КЛЮЧИ, не тексты. Здесь — четыре текста,
-- которых для этого не хватило: три были вписаны в код строкой
-- (и потому не переводились вообще), один понадобился впервые.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('portal.text.no', 'portal', 'text', NULL,
   'отрицательный ответ в таблицах — пара к portal.text.yes'),
  ('common.hint.required_fields_marked', 'common', 'hint', NULL,
   'пояснение под карточкой в режиме правки'),
  ('partners.hint.save_card_before_addresses', 'partners', 'hint', NULL,
   'адреса доставки задаются уже существующему грузополучателю'),
  ('admin.title.operator_organization', 'admin', 'title', NULL,
   'одна организация оператора; множественное — admin.title.operator_organizations')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('portal.text.no', 'ru', 'нет'),
  ('portal.text.no', 'en', 'no'),
  ('common.hint.required_fields_marked', 'ru', 'Поля со звёздочкой обязательны.'),
  ('common.hint.required_fields_marked', 'en', 'Fields marked with an asterisk are required.'),
  ('partners.hint.save_card_before_addresses', 'ru',
   'Сначала сохраните карточку — адреса задаются уже для грузополучателя'),
  ('partners.hint.save_card_before_addresses', 'en',
   'Save the card first — addresses are set for an existing consignee'),
  ('admin.title.operator_organization', 'ru', 'Организация оператора'),
  ('admin.title.operator_organization', 'en', 'Operator organization')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
