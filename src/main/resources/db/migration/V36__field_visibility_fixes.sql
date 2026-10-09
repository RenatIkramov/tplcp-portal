-- V36__field_visibility_fixes.sql
-- Правки по видимости полей товара после первого просмотра экрана.
--
--   1. «Статус товара» и «Данные WMS действительны» переезжают
--      на вкладку служебной информации — оба ставит обмен, а не человек;
--   2. вся служебная информация становится нескрываемой от клиента;
--   3. «Группа товара» тоже: она участвует в регистре запасов, и клиент
--      без неё не разберёт свои остатки;
--   4. два разных смысла «нельзя скрыть» разводятся по двум признакам.
--
-- О пункте 4 подробно, потому что без него пункты 2 и 3 сделали бы
-- не то, о чём просили.
--
-- До этой миграции `is_locked` значил сразу две вещи: «поклажедателю
-- скрыть нельзя» (экран доступов) и «из таблицы убрать нельзя»
-- (личное окно ⚙ Колонки — функция table_columns принудительно
-- показывала такие колонки). Для кода и наименования это совпадало.
-- Но «кто создал», «когда изменено» и прочая служебная информация
-- обязаны быть доступны клиенту и при этом не должны намертво висеть
-- в списке товаров у каждого человека.
--
-- Поэтому признаков два:
--   is_locked — поклажедателю скрыть нельзя (экран доступов);
--   is_pinned — из своей таблицы убрать нельзя (окно ⚙ Колонки).
-- Второй остаётся только у трёх обязательных полей товара.
SET search_path = dbo;

-- =====================================================================
-- 1. Признак «из таблицы убрать нельзя»
-- =====================================================================
ALTER TABLE table_column
  ADD COLUMN IF NOT EXISTS is_pinned boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN table_column.is_pinned IS
  'Колонку нельзя убрать из своей таблицы (окно выбора колонок)';
COMMENT ON COLUMN table_column.is_locked IS
  'Поле нельзя скрыть от поклажедателя (экран доступов поклажедателей)';

-- Прикреплены ровно те три, без которых список товаров бесполезен.
-- Это и есть прежний смысл is_locked, перенесённый на новый признак.
UPDATE table_column
   SET is_pinned = true
 WHERE table_key = 'sku'
   AND column_id IN ('code', 'name', 'unit');

-- =====================================================================
-- 2. Переезд двух полей в служебную информацию
-- =====================================================================
-- «Статус товара» и «Данные WMS действительны» приходят из обмена.
-- В основной информации и в признаках они выглядели как то, что
-- человек заполняет сам.
UPDATE table_column
   SET group_key = 'sku.text.system_information'
 WHERE table_key = 'sku'
   AND column_id IN ('statusProduct', 'isWmsActive');

-- =====================================================================
-- 3. Что клиенту скрыть нельзя
-- =====================================================================
-- Служебная информация целиком: по ней человек понимает, откуда взялась
-- запись и кто её менял, — без этого разбор расхождений с WMS
-- превращается в гадание.
--
-- «Группа товара» участвует в регистре запасов: клиент без неё не
-- сгруппирует свои остатки.
UPDATE table_column
   SET is_locked = true
 WHERE table_key = 'sku'
   AND (group_key = 'sku.text.system_information' OR column_id = 'group');

-- =====================================================================
-- 4. Раскладка пользователя опирается на is_pinned, а не на is_locked
-- =====================================================================
-- Набор колонок в ответе меняется (is_locked -> is_pinned), поэтому
-- функцию пересоздаём. Экран читает этот признак, чтобы сделать
-- галочку серой в окне ⚙ Колонки.
DROP FUNCTION IF EXISTS table_columns(uuid, text, text);
CREATE FUNCTION table_columns(p_user_id uuid, p_locale text,
                              p_table_key text DEFAULT NULL)
RETURNS TABLE (
  table_key   varchar(40),
  column_id   varchar(40),
  label       text,
  is_visible  boolean,
  sort_order  smallint,
  is_pinned   boolean
)
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  WITH cat AS (
    SELECT c.* FROM table_column c
    WHERE p_table_key IS NULL OR c.table_key = p_table_key
  ),
  dep AS (
    SELECT * FROM depositor_table_column
    WHERE depositor_id = current_depositor_id()
  ),
  chosen AS (
    SELECT * FROM user_table_column WHERE user_id = p_user_id
  ),
  -- таблицы, по которым пользователь уже делал выбор: в них отсутствие
  -- строки означает «спрятано», а не «не касался»
  touched AS (
    SELECT DISTINCT user_table_column.table_key FROM user_table_column
    WHERE user_id = p_user_id
  ),
  texts AS (
    SELECT t.text_key, t.value FROM ui_texts(p_locale) t
  )
  SELECT
    c.table_key,
    c.column_id,
    -- Переименование клиентом сильнее перевода: клиент назвал поле
    -- по-своему, и это важнее языка портала.
    COALESCE(lbl.custom_label, t.value, c.text_key)::text AS label,
    CASE
      WHEN c.is_pinned                THEN true
      WHEN ch.user_id IS NOT NULL     THEN true
      WHEN c.table_key IN (SELECT table_key FROM touched) THEN false
      ELSE COALESCE(d.is_default, c.is_default)
    END AS is_visible,
    COALESCE(ch.sort_order, d.sort_order, c.sort_order)::smallint AS sort_order,
    c.is_pinned
  FROM cat c
  LEFT JOIN dep d
         ON d.table_key = c.table_key AND d.column_id = c.column_id
  LEFT JOIN chosen ch
         ON ch.table_key = c.table_key AND ch.column_id = c.column_id
  LEFT JOIN depositor_field_label lbl
         ON lbl.depositor_id   = current_depositor_id()
        AND lbl.object_type_id = c.object_type_id
        AND lbl.field_id       = c.field_id
  LEFT JOIN texts t ON t.text_key = c.text_key
  -- запрещённые клиенту не отдаём вовсе: пользователь не должен
  -- увидеть их даже в списке выбора колонок
  WHERE COALESCE(d.is_available, true)
  ORDER BY c.table_key, 5, c.column_id;
$$;
GRANT EXECUTE ON FUNCTION table_columns(uuid, text, text) TO portal_app;

-- Сохранение личного выбора: прикреплённую колонку убрать нельзя,
-- и запрет стоит в базе, а не только серой галочкой на экране.
CREATE OR REPLACE FUNCTION user_columns_save(p_user_id uuid, p_table_key text,
                                             p_columns text[])
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(c.column_id, ', ' ORDER BY c.column_id) INTO v_missing
  FROM table_column c
  WHERE c.table_key = p_table_key
    AND c.is_pinned
    AND NOT (c.column_id = ANY (coalesce(p_columns, '{}'::text[])));

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'Колонку % из таблицы убрать нельзя', v_missing
      USING ERRCODE = 'check_violation';
  END IF;

  -- Дальше — тело из V31 без изменений. Два отдельных запроса, а не
  -- DELETE внутри CTE: изменяющий CTE работает в том же снимке, что и
  -- основной запрос, и повторное сохранение падало на уникальном индексе.
  DELETE FROM user_table_column
  WHERE user_id = p_user_id AND table_key = p_table_key;

  INSERT INTO user_table_column (user_id, table_key, column_id, sort_order)
  SELECT p_user_id, p_table_key, x.column_id, x.ord::smallint
  FROM unnest(p_columns) WITH ORDINALITY AS x(column_id, ord);
END;
$$;
GRANT EXECUTE ON FUNCTION user_columns_save(uuid, text, text[]) TO portal_app;

-- Проверка: прежний смысл is_locked не должен потеряться по дороге.
DO $$
DECLARE
  v_pinned int;
  v_locked int;
BEGIN
  SELECT count(*) INTO v_pinned
  FROM table_column WHERE table_key = 'sku' AND is_pinned;
  IF v_pinned <> 3 THEN
    RAISE EXCEPTION 'Прикреплённых колонок товара должно быть три, а их %', v_pinned;
  END IF;

  SELECT count(*) INTO v_locked
  FROM table_column WHERE table_key = 'sku' AND is_locked;
  -- три обязательных + семь служебных + группа товара
  IF v_locked <> 11 THEN
    RAISE EXCEPTION 'Нескрываемых полей товара должно быть 11, а их %', v_locked;
  END IF;
END;
$$;

-- =====================================================================
-- 5. Текст отказа: «обязательное» годилось только для трёх полей
-- =====================================================================
-- Теперь нескрываемыми стали и служебные поля, и группа товара.
-- Называть «кто создал» обязательным полем неверно — отказ должен
-- говорить то, что есть: это поле нельзя скрыть от клиента.
CREATE OR REPLACE FUNCTION admin_depositor_column_save(p_depositor_id uuid, p_table_key text,
                                                       p_column_id text, p_is_available boolean,
                                                       p_is_default boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  v_portal_default boolean;
  v_locked         boolean;
BEGIN
  SELECT is_default, is_locked INTO v_portal_default, v_locked
  FROM table_column
  WHERE table_key = p_table_key AND column_id = p_column_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Колонки % в таблице % нет', p_column_id, p_table_key;
  END IF;

  IF v_locked AND NOT p_is_available THEN
    RAISE EXCEPTION 'Поле «%» нельзя скрыть от поклажедателя', p_column_id
      USING ERRCODE = 'check_violation';
  END IF;

  IF p_is_available AND p_is_default IS NOT DISTINCT FROM v_portal_default THEN
    DELETE FROM depositor_table_column
    WHERE depositor_id = p_depositor_id
      AND table_key = p_table_key AND column_id = p_column_id;
    RETURN;
  END IF;

  INSERT INTO depositor_table_column
    (depositor_id, table_key, column_id, is_available, is_default)
  VALUES (p_depositor_id, p_table_key, p_column_id, p_is_available, p_is_default)
  ON CONFLICT (depositor_id, table_key, column_id) DO UPDATE SET
    is_available = excluded.is_available,
    is_default   = excluded.is_default;
END;
$$;

-- =====================================================================
-- 6. Тексты: вкладка «Тексты интерфейса» в администрировании
-- =====================================================================
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.button.interface_texts', 'admin', 'button', NULL,
   'подвкладка администрирования: ведение переводов')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('admin.button.interface_texts', 'ru', 'Тексты интерфейса'),
  ('admin.button.interface_texts', 'en', 'Interface texts')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Заглушка «Раздел в разработке» в настройке полей поклажедателя
-- заменяется указанием, где настройка теперь живёт.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.hint.where_field_visibility', 'admin', 'hint', NULL,
   'настройка полей поклажедателя: куда переехала видимость полей')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('admin.hint.where_field_visibility', 'ru',
   'Видимость полей настраивается в карточке поклажедателя: «Доступы поклажедателей» → клиент → вкладка «Поля товаров».'),
  ('admin.hint.where_field_visibility', 'en',
   'Field visibility is set in the depositor card: Depositor access → the client → the Product fields tab.')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
