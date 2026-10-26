-- ---------------------------------------------------------------------
-- V72. Порядок фильтров: выбранные первыми, номера без совпадений
-- ---------------------------------------------------------------------
-- В V70 выбранные фильтры получали порядок из личных настроек (1, 2, 3…),
-- а остальные сохраняли порядок справочника (1…50). Номера из двух
-- разных рядов попадали в один ответ, и совпадения были неизбежны:
--
--   status        выбран      1
--   erpNo         выбран      2
--   customField1  выбран      3
--   wmsNumber     не выбран   3   ← то же число
--   warehouse     не выбран   4
--
-- Экран сортирует по этому числу, поэтому выбранные и невыбранные
-- перемешивались, и строка в окне ⚙ Фильтры оказывалась не там, где
-- человек её оставил. Искать в списке из тридцати строк то, что лежит
-- в непредсказуемом месте, — ровно то же, что не иметь её в списке.
--
-- Теперь порядок считает база: сперва выбранные в порядке человека,
-- потом остальные в порядке справочника, и номер — плотный ряд без
-- пропусков и совпадений. Экрану остаётся рисовать как пришло.

DROP FUNCTION IF EXISTS table_filters(uuid, text, text);
CREATE FUNCTION table_filters(p_user_id uuid,
                              p_locale   text,
                              p_table_key text DEFAULT NULL)
RETURNS TABLE (table_key      varchar,
               column_id      varchar,
               label          text,
               filter_kind    varchar,
               filter_column  varchar,
               filter_enum    varchar,
               object_type_id smallint,
               is_default     boolean,
               is_chosen      boolean,
               sort_order     smallint)
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  WITH listed AS (
    SELECT tc.table_key,
           tc.column_id,
           tc.label,
           c.filter_kind,
           c.filter_column,
           c.filter_enum,
           c.object_type_id,
           c.filter_default,
           tc.sort_order
      FROM table_columns(p_user_id, p_locale, p_table_key) tc
      JOIN table_column c
        ON c.table_key = tc.table_key AND c.column_id = tc.column_id
     WHERE c.filter_kind IS NOT NULL
  ),
  -- Настраивал ли человек эту таблицу вообще. Проверяется наличие строк
  -- ПО ТАБЛИЦЕ, а не по колонке: иначе снятую галочку не отличить
  -- от «ничего не настраивал».
  tuned AS (
    SELECT DISTINCT user_table_filter.table_key
      FROM user_table_filter
     WHERE user_id = p_user_id
  ),
  marked AS (
    SELECT l.*,
           CASE WHEN l.table_key IN (SELECT table_key FROM tuned)
                THEN u.column_id IS NOT NULL
                ELSE l.filter_default
           END AS is_chosen,
           u.sort_order AS mine
      FROM listed l
      LEFT JOIN user_table_filter u
             ON u.user_id = p_user_id
            AND u.table_key = l.table_key
            AND u.column_id = l.column_id
  )
  SELECT m.table_key,
         m.column_id,
         m.label,
         m.filter_kind,
         m.filter_column,
         m.filter_enum,
         m.object_type_id,
         m.filter_default,
         m.is_chosen,
         row_number() OVER (PARTITION BY m.table_key
                                ORDER BY m.is_chosen DESC,
                                         COALESCE(m.mine, m.sort_order),
                                         m.column_id)::smallint
    FROM marked m
   ORDER BY m.table_key,
            m.is_chosen DESC,
            COALESCE(m.mine, m.sort_order),
            m.column_id;
$$;

GRANT EXECUTE ON FUNCTION table_filters(uuid, text, text) TO portal_app;

COMMENT ON FUNCTION table_filters(uuid, text, text) IS
  'Фильтры списка для пользователя: выбранные первыми в его порядке, затем остальные';

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
-- Проверяется именно то, что было сломано: что номера не повторяются
-- и что выбранные идут первыми.
DO $check$
DECLARE
  sample uuid;
  dupes  int;
  first_unchosen int;
  last_chosen    int;
BEGIN
  SELECT user_id INTO sample FROM app_user ORDER BY login LIMIT 1;
  IF sample IS NULL THEN
    RETURN;
  END IF;

  PERFORM user_filters_save(sample, 'receipts', ARRAY['status', 'customField1']);

  SELECT count(*) INTO dupes
    FROM (SELECT sort_order
            FROM table_filters(sample, 'ru', 'receipts')
           GROUP BY sort_order HAVING count(*) > 1) q;

  SELECT min(sort_order) INTO first_unchosen
    FROM table_filters(sample, 'ru', 'receipts') WHERE NOT is_chosen;
  SELECT max(sort_order) INTO last_chosen
    FROM table_filters(sample, 'ru', 'receipts') WHERE is_chosen;

  PERFORM user_filters_reset(sample, 'receipts');

  IF dupes > 0 THEN
    RAISE EXCEPTION 'номера фильтров повторяются: % совпадений', dupes;
  END IF;
  IF last_chosen >= first_unchosen THEN
    RAISE EXCEPTION 'выбранные фильтры не идут первыми: последний выбранный %, первый невыбранный %',
                    last_chosen, first_unchosen;
  END IF;
END
$check$;
