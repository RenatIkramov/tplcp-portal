-- =====================================================================
-- V48. Переименование полей — в карточке поклажедателя
-- =====================================================================
-- Раньше в администрировании была вкладка «Настройка полей
-- поклажедателя» с двумя подвкладками: «Пользовательское наименование
-- полей» (список переименований по ВСЕМ клиентам, с фильтром по
-- клиенту) и «Настройка видимости полей» (заглушка «Раздел
-- в разработке», потому что видимость уже настраивается в карточке
-- доступов поклажедателя).
--
-- Настройка одного клиента была разбросана по двум экранам: склады,
-- разделы и видимость полей — в карточке, а названия полей — отдельной
-- вкладкой со своим фильтром. Теперь всё в карточке: выбрал клиента —
-- видишь и правишь всё, что к нему относится. Фильтр по клиенту
-- становится ненужным: клиент задан карточкой.
--
-- В базе для этого всё уже есть: depositor_field_label,
-- admin_field_label_save() и admin_field_labels() заведены в V29/V31.
-- Не хватало только списка полей, которые МОЖНО переименовать:
-- прежний экран был макетом и брал его из массива в прототипе.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Какие поля вообще можно переименовать
-- ---------------------------------------------------------------------
-- Строка на поле, а не на колонку: одно поле показывается в нескольких
-- таблицах (вес в заказе и вес в строке заказа), а название у него
-- одно — переименование срабатывает везде сразу. Поэтому группируем
-- по полю и рядом перечисляем таблицы, где подпись поменяется.
--
-- Портальное название берётся на языке администратора: он выбирает
-- поле по подписи, которую сам видит на экранах, а не по коду.
-- Если перевода нет, ui_texts() уже отдала текст языка по умолчанию,
-- а если и его нет — показываем ключ, чтобы строка не оказалась пустой.
--
-- SECURITY DEFINER тут не нужен: каталог колонок и тексты — портальные,
-- они не под RLS, и право администратора проверяется в приложении.
CREATE OR REPLACE FUNCTION admin_field_catalog(p_locale text)
RETURNS TABLE (
  object_code   varchar(20),
  object_name   varchar(100),
  field_id      varchar(100),
  portal_label  text,
  shown_in      text
)
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  WITH texts AS (SELECT t.text_key, t.value FROM ui_texts(p_locale) t)
  SELECT
    o.code,
    o.name,
    c.field_id,
    -- у одного поля в разных таблицах может быть свой ключ подписи
    -- («Вес» и «Вес, кг»): берём любой устойчиво — min по ключу,
    -- иначе список полей перетряхивался бы от запроса к запросу
    COALESCE(min(t.value), min(c.text_key))::text AS portal_label,
    string_agg(DISTINCT c.table_key, ', ' ORDER BY c.table_key) AS shown_in
  FROM table_column c
  JOIN enum_object_type o ON o.id = c.object_type_id
  LEFT JOIN texts t ON t.text_key = c.text_key
  WHERE c.field_id IS NOT NULL
  GROUP BY o.code, o.name, c.field_id
  ORDER BY o.code, 4
$$;

COMMENT ON FUNCTION admin_field_catalog(text) IS
  'Поля, которые поклажедателю можно переименовать: строка на поле, '
  'рядом таблицы, где подпись поменяется';

GRANT EXECUTE ON FUNCTION admin_field_catalog(text) TO portal_app;

-- ---------------------------------------------------------------------
-- 2. Тексты новой вкладки
-- ---------------------------------------------------------------------
-- Название самой вкладки не заводим: ключ admin.button.custom_field_names
-- («Пользовательское наименование полей») уже есть с V26 — вкладка
-- переехала, а называется по-прежнему.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.hint.depositor_field_labels', 'admin', 'hint', NULL,
   'пояснение к вкладке переименования полей в карточке поклажедателя'),
  ('admin.column.shown_in', 'admin', 'column', NULL,
   'в каких таблицах поле показывается — где подпись поменяется'),
  ('common.title.edit_override', 'common', 'title', NULL,
   'заголовок окна правки переименования поля'),
  ('admin.hint.empty_label_restores_portal_name', 'admin', 'hint', NULL,
   'подсказка в окне: пустое наименование возвращает портальное')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('admin.hint.depositor_field_labels', 'ru',
   'Как этот поклажедатель называет поля — например, «Код» → «Артикул '
   'клиента». Название действует во всех таблицах и карточках, где поле '
   'показывается, и приоритетнее языковых настроек: при работе с этим '
   'поклажедателем любой пользователь увидит именно его, независимо от '
   'выбранного языка интерфейса.'),
  ('admin.hint.depositor_field_labels', 'en',
   'What this depositor calls a field — for example, "Code" to "Customer '
   'SKU". The name applies everywhere the field is shown and takes '
   'precedence over the interface language: while this depositor is '
   'selected, every user sees this name whatever language they chose.'),
  ('admin.column.shown_in', 'ru', 'Где показывается'),
  ('admin.column.shown_in', 'en', 'Shown in'),
  ('common.title.edit_override', 'ru', 'Редактирование переопределения'),
  ('common.title.edit_override', 'en', 'Edit override'),
  ('admin.hint.empty_label_restores_portal_name', 'ru',
   'Пустое наименование возвращает портальное название поля'),
  ('admin.hint.empty_label_restores_portal_name', 'en',
   'An empty name restores the portal name of the field')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
