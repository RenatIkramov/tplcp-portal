SET search_path = dbo;

-- =====================================================================
-- V154. Комплекты: вкладка в «Справочниках» и состав вручную
-- =====================================================================
-- Документ проекта «Комплекты — решение», решение заказчика 22.11.2026.
--
-- * «Справочники → Комплекты» рядом с «Упаковками»: комплекты клиента,
--   их состав и сколько можно собрать по складам; щелчок открывает
--   карточку товара на вкладке «Состав».
-- * Пока состав не приходит из WMS, администратор и сотрудник склада
--   задают его в карточке. Пишет та же функция kit_components_apply,
--   что будет у синхронизации (V151): правила, журнал и пересчёт
--   остатков — в одном месте. Схема базы не меняется — здесь только тексты.

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('references.button.kits',       'references', 'button',  'sku', 'подвкладка «Комплекты» в «Справочниках» (V154)'),
  ('references.column.composition', 'references', 'column', 'sku', '«Комплекты»: состав (V154)'),
  ('references.column.can_assemble', 'references', 'column', 'sku', '«Комплекты»: можно собрать по складам (V154)'),
  ('references.text.kit_no_composition', 'references', 'text', 'sku', '«Комплекты»: у комплекта нет состава (V154)'),
  ('references.text.kits_empty',   'references', 'text',    'sku', '«Комплекты»: у клиента нет комплектов (V154)'),
  ('references.hint.kits',         'references', 'hint',    'sku', '«Комплекты»: что на вкладке (V154)'),
  ('references.button.add_kit',    'references', 'button',  'sku', '«Комплекты»: сделать товар комплектом (V154)'),
  ('references.label.kit_sku',     'references', 'label',   'sku', '«Комплекты»: какой товар станет комплектом (V154)'),
  ('references.button.set_composition', 'references', 'button', 'sku', '«Комплекты»: открыть товар и задать состав (V154)'),
  ('references.message.pick_kit_sku', 'references', 'message', 'sku', '«Комплекты»: товар не выбран (V154)'),
  ('sku.button.kit_edit',          'sku', 'button',  'sku', 'вкладка «Состав»: править состав вручную (V154)'),
  ('sku.button.kit_add_component', 'sku', 'button',  'sku', 'вкладка «Состав»: добавить комплектующую (V154)'),
  ('sku.button.kit_save',          'sku', 'button',  'sku', 'вкладка «Состав»: сохранить состав (V154)'),
  ('sku.hint.kit_manual',          'sku', 'hint',    'sku', 'вкладка «Состав»: состав вручную до WMS (V154)'),
  ('sku.message.kit_saved',        'sku', 'message', 'sku', 'вкладка «Состав»: состав сохранён (V154)'),
  ('sku.message.kit_cleared',      'sku', 'message', 'sku', 'вкладка «Состав»: состав пуст — товар больше не комплект (V154)'),
  ('error.kit.forbidden',          'server', 'message', 'sku', 'состав вручную — только администратор и сотрудник склада (V154)'),
  ('error.kit.component_required', 'server', 'message', 'sku', 'строка состава без кода (V154)'),
  ('error.kit.component_unknown',  'server', 'message', 'sku', 'комплектующей нет в справочнике (V154)'),
  ('error.kit.component_self',     'server', 'message', 'sku', 'комплект в своём составе (V154)'),
  ('error.kit.qty_positive',       'server', 'message', 'sku', 'количество комплектующей не больше нуля (V154)'),
  ('error.kit.component_is_kit',   'server', 'message', 'sku', 'комплектующая — сама комплект (V154)'),
  ('error.kit.is_component',       'server', 'message', 'sku', 'товар — комплектующая другого комплекта (V154)'),
  ('error.kit.duplicate',          'server', 'message', 'sku', 'комплектующая указана дважды (V154)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('references.button.kits', 'ru', 'Комплекты'),
    ('references.button.kits', 'en', 'Kits'),
    ('references.column.composition', 'ru', 'Состав'),
    ('references.column.composition', 'en', 'Composition'),
    ('references.column.can_assemble', 'ru', 'Можно собрать'),
    ('references.column.can_assemble', 'en', 'Can be assembled'),
    ('references.text.kit_no_composition', 'ru', 'состав не задан'),
    ('references.text.kit_no_composition', 'en', 'no composition'),
    ('references.text.kits_empty', 'ru', 'Комплектов пока нет'),
    ('references.text.kits_empty', 'en', 'No kits yet'),
    ('references.hint.kits', 'ru', 'Своего остатка у комплекта нет: «Можно собрать» — сколько комплектов собирается из свободных комплектующих на складе. Щелчок по строке открывает состав.'),
    ('references.hint.kits', 'en', 'A kit has no stock of its own: “Can be assembled” is how many kits the free components in a warehouse make. Click a row to open its composition.'),
    ('references.button.add_kit', 'ru', '+ Добавить комплект'),
    ('references.button.add_kit', 'en', '+ Add kit'),
    ('references.label.kit_sku', 'ru', 'Товар, который станет комплектом'),
    ('references.label.kit_sku', 'en', 'Item that becomes a kit'),
    ('references.button.set_composition', 'ru', 'Задать состав'),
    ('references.button.set_composition', 'en', 'Set composition'),
    ('references.message.pick_kit_sku', 'ru', 'Выберите товар из списка'),
    ('references.message.pick_kit_sku', 'en', 'Pick an item from the list'),
    ('sku.button.kit_edit', 'ru', 'Изменить состав'),
    ('sku.button.kit_edit', 'en', 'Edit composition'),
    ('sku.button.kit_add_component', 'ru', '+ Комплектующая'),
    ('sku.button.kit_add_component', 'en', '+ Component'),
    ('sku.button.kit_save', 'ru', 'Сохранить состав'),
    ('sku.button.kit_save', 'en', 'Save composition'),
    ('sku.hint.kit_manual', 'ru', 'Пока состав не приходит из WMS, его можно задать здесь. Состав из WMS заменит введённый.'),
    ('sku.hint.kit_manual', 'en', 'Until the composition comes from WMS it can be set here. The composition from WMS will replace it.'),
    ('sku.message.kit_saved', 'ru', 'Состав комплекта сохранён'),
    ('sku.message.kit_saved', 'en', 'Kit composition saved'),
    ('sku.message.kit_cleared', 'ru', 'Состав пуст — товар больше не комплект'),
    ('sku.message.kit_cleared', 'en', 'The composition is empty — the item is no longer a kit'),
    ('error.kit.forbidden', 'ru', 'Состав комплекта задают администратор и сотрудник склада'),
    ('error.kit.forbidden', 'en', 'Only an administrator or warehouse staff can set a kit composition'),
    ('error.kit.component_required', 'ru', 'В строке состава не указан код комплектующей'),
    ('error.kit.component_required', 'en', 'A composition line has no component code'),
    ('error.kit.component_unknown', 'ru', 'Товара {code} нет в справочнике'),
    ('error.kit.component_unknown', 'en', 'Item {code} is not in the catalog'),
    ('error.kit.component_self', 'ru', 'Комплект {code} не может входить в свой состав'),
    ('error.kit.component_self', 'en', 'Kit {code} cannot be part of its own composition'),
    ('error.kit.qty_positive', 'ru', 'Количество комплектующей {code} должно быть больше нуля'),
    ('error.kit.qty_positive', 'en', 'The quantity of component {code} must be greater than zero'),
    ('error.kit.component_is_kit', 'ru', '{code} — сам комплект: комплект не может входить в другой комплект'),
    ('error.kit.component_is_kit', 'en', '{code} is a kit itself: a kit cannot be part of another kit'),
    ('error.kit.is_component', 'ru', '{code} входит в комплект {kits}: комплектующая не может быть комплектом'),
    ('error.kit.is_component', 'en', '{code} is a component of kit {kits}: a component cannot be a kit'),
    ('error.kit.duplicate', 'ru', 'Комплектующая {code} указана дважды'),
    ('error.kit.duplicate', 'en', 'Component {code} is listed twice')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
