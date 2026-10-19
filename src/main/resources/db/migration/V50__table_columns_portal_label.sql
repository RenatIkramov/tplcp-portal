-- =====================================================================
-- V50. Раскладка колонок отдаёт и портальное название
-- =====================================================================
-- table_columns() отдавала одну подпись — COALESCE(переименование
-- клиентом, перевод, ключ текста). Экрану этого мало: по такой подписи
-- не понять, ПЕРЕИМЕНОВАНО поле или нет.
--
-- А понимать нужно. Подписи в портале ставятся из трёх источников,
-- и переименование — единственный, которого экран не знает сам:
--
--   * шапки таблиц берут перевод по ключу (col.<таблица>.<колонка>);
--   * карточки товара и документов — строками, вписанными в разметку;
--   * переименование поля клиентом живёт только в базе.
--
-- Пока экран получал одну подпись, у него было два плохих выбора:
-- либо не применять переименование вовсе (так и было — его не видел
-- никто), либо подставлять серверную подпись всегда. Второе хуже:
-- если текста в каталоге нет, COALESCE доходит до ключа, и в карточке
-- вместо «Производитель» появилось бы «col.sku.manufacturer».
--
-- С двумя колонками правило становится однозначным: label <> portal_label
-- значит «клиент назвал поле по-своему» — только тогда экран и подменяет
-- подпись. Во всех остальных случаях он переводит, как переводил.
--
-- Решение заказчика: переименование действует на ЛЮБОМ языке. Клиент
-- назвал поле по-своему, и язык интерфейса его слово не отменяет.
-- Поэтому portal_label возвращается на языке пользователя, а
-- переименование — как заведено, одним текстом.
--
-- Набор колонок в ответе меняется, поэтому функцию пересоздаём:
-- CREATE OR REPLACE тип возвращаемой таблицы менять не умеет.
SET search_path = dbo;

DROP FUNCTION IF EXISTS table_columns(uuid, text, text);
CREATE FUNCTION table_columns(p_user_id uuid, p_locale text,
                              p_table_key text DEFAULT NULL)
RETURNS TABLE (
  table_key     varchar(40),
  column_id     varchar(40),
  label         text,
  portal_label  text,
  is_visible    boolean,
  sort_order    smallint,
  is_pinned     boolean
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
    -- А это то же поле без переименования — чтобы экран видел разницу
    COALESCE(t.value, c.text_key)::text                  AS portal_label,
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
  ORDER BY c.table_key, 6, c.column_id;
$$;

COMMENT ON FUNCTION table_columns(uuid, text, text) IS
  'Готовая раскладка таблиц для пользователя: подпись (с переименованием '
  'клиента), портальное название той же колонки, видимость и порядок';

GRANT EXECUTE ON FUNCTION table_columns(uuid, text, text) TO portal_app;
