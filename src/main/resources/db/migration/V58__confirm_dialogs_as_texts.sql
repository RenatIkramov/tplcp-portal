-- =====================================================================
-- V58. Вопросы перед удалением — в каталог текстов
-- =====================================================================
-- Отказы сервера переведены (V57), а окно подтверждения рисует браузер,
-- и текст для него собирал экран по-русски прямо в коде:
--
--     window.confirm('Удалить «' + name + '»?')
--
-- У клиента на английском получалось так: портал на английском, кнопка
-- «Delete», а вопрос перед удалением — по-русски. Это то же самое, что
-- было с отказами, только на другой стороне.
--
-- ПОДСТАНОВКИ. В тексте они стоят в фигурных скобках: «Удалить
-- «{name}»?». Раньше T(ключ) умел только отдать строку, теперь принимает
-- и значения: T('common.message.confirm_delete_named', {name: …}).
-- Правило то же, что на сервере (PortalMessages): идём по заданным
-- значениям, неизвестные скобки оставляем как есть — лишняя скобка
-- в переводе не должна ломать окно.
--
-- ПОЧЕМУ НЕ ОДИН КЛЮЧ НА ВСЕХ. Вопросы различаются не только именем:
-- «Удалить упаковку «К-01»?» и «Удалить адрес «ул. Абая, 10»?» —
-- разные фразы, и собирать их из «Удалить» + название вида записи
-- нельзя: это снова склейка, из-за которой в V57 и чинили сообщения.
-- Поэтому ключ на фразу, а общие («Удалить «{name}»?») переиспользуются
-- там, где вид записи в тексте не назван.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.message.confirm_delete_named', 'common', 'message', NULL,
   'вопрос перед удалением записи, вид записи в тексте не назван'),
  ('common.message.confirm_delete_forever', 'common', 'message', NULL,
   'вопрос перед необратимым удалением карточки'),
  ('sku.message.confirm_delete_item', 'sku', 'message', NULL,
   'вопрос перед необратимым удалением товара'),
  ('sku.message.confirm_delete_packaging', 'sku', 'message', NULL,
   'вопрос перед удалением упаковки товара'),
  ('partners.message.confirm_delete_address', 'partners', 'message', NULL,
   'вопрос перед удалением адреса доставки'),
  ('admin.message.confirm_reset_field_label', 'admin', 'message', NULL,
   'вопрос перед возвратом портального названия поля'),
  ('admin.message.confirm_reset_field_label_plain', 'admin', 'message', NULL,
   'то же, когда портальное название поля неизвестно')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
        ('common.message.confirm_delete_named',
         'Удалить «{name}»?',
         'Delete "{name}"?'),
        ('common.message.confirm_delete_forever',
         'Удалить «{name}» без возможности восстановления?',
         'Delete "{name}" permanently?'),
        ('sku.message.confirm_delete_item',
         'Удалить товар «{code}» без возможности восстановления?',
         'Delete product "{code}" permanently?'),
        ('sku.message.confirm_delete_packaging',
         'Удалить упаковку «{code}»?',
         'Delete packaging "{code}"?'),
        ('partners.message.confirm_delete_address',
         'Удалить адрес «{address}»?',
         'Delete address "{address}"?'),
        ('admin.message.confirm_reset_field_label',
         'Вернуть портальное название поля «{field}» → «{portal}»?',
         'Restore the portal name of field "{field}" → "{portal}"?'),
        ('admin.message.confirm_reset_field_label_plain',
         'Вернуть портальное название поля «{field}»?',
         'Restore the portal name of field "{field}"?')
       ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Ключ без текста экран покажет как есть — проверяем здесь же.
DO $$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(k.text_key || ' (' || l.code || ')', ', ' ORDER BY k.text_key)
    INTO missing
    FROM ui_text_key k
    JOIN dictionary_locale l ON l.code IN ('ru', 'en')
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key LIKE '%.confirm_%' AND t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для вопросов перед удалением: %', missing;
  END IF;
END $$;
