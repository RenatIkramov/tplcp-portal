SET search_path = dbo;

-- =====================================================================
-- V153. Комплекты, шаг 3: поступления и перемещения — комплектующими
-- =====================================================================
-- Документ проекта «Комплекты — решение». WMS принимает и перевозит
-- только комплектующие: комплект в поступлении и перемещении сразу
-- раскладывается на них (состав × количество). Раскладывают окно строки
-- (на экране, сразу) и сервер (DraftService — добавление, правка, замена
-- состава, а с ней загрузка из Excel). Схема базы не меняется — здесь
-- только тексты.
--
-- Комплект с признаком, но без состава из WMS разложить нечем: сервер
-- отказывает с понятной причиной.

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.message.kit_expanded',  'common', 'message', 'documents', 'окно строки: комплект разложен на комплектующие (V153)'),
  ('common.message.kits_expanded', 'common', 'message', 'documents', 'сохранение, загрузка из Excel: комплекты разложены (V153)'),
  ('error.kit.no_composition',     'server', 'message', 'documents',   'комплект без состава в поступлении или перемещении (V153)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.message.kit_expanded', 'ru', 'Комплект {code} разложен на комплектующие: строк — {count}. Склад принимает и перевозит комплектующие'),
    ('common.message.kit_expanded', 'en', 'Kit {code} has been split into its components: {count} lines. The warehouse receives and moves components'),
    ('common.message.kits_expanded', 'ru', 'Комплекты разложены на комплектующие: {codes}'),
    ('common.message.kits_expanded', 'en', 'Kits have been split into their components: {codes}'),
    ('error.kit.no_composition', 'ru', 'Состав комплекта {codes} ещё не получен из WMS — укажите комплектующие вместо комплекта'),
    ('error.kit.no_composition', 'en', 'The composition of kit {codes} has not been received from WMS yet — enter its components instead')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
