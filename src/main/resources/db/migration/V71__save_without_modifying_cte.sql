-- ---------------------------------------------------------------------
-- V71. Запись выбора без изменяющего CTE
-- ---------------------------------------------------------------------
-- ЧТО СЛОМАЛОСЬ. user_filters_save (V70) падал на повторной записи,
-- если в новом наборе была колонка из прежнего:
--
--   duplicate key value violates unique constraint "user_table_filter_pkey"
--   Key (user_id, table_key, column_id)=(…, receipts, trip) already exists
--
-- То есть окно ⚙ Фильтры работало ровно один раз: первый выбор
-- записывался, а любая правка, сохраняющая хоть один фильтр, отказом.
-- Это самый неприятный вид поломки — тот, что проходит по счастливому
-- пути и ломается на втором нажатии.
--
-- ПОЧЕМУ. Написано было так:
--
--   WITH wipe AS (DELETE FROM … RETURNING 1)
--   INSERT INTO … SELECT … WHERE EXISTS (SELECT 1 FROM wipe) OR true;
--
-- Приём известный: изменяющий CTE исполняется всегда, поэтому DELETE
-- и INSERT получаются «одной командой». Но именно поэтому они и
-- получаются одной командой: обе части видят один и тот же снимок
-- данных, и проверка уникальности при вставке считает удаляемые строки
-- живыми. Порядок, в котором планировщик их переплетёт, не определён —
-- отсюда и то, что на одной таблице это падало, а на другой нет.
--
-- ТО ЖЕ САМОЕ ЛЕЖИТ В user_columns_save (V29) — слово в слово. Там
-- не выстрелило: на моей проверке две записи с общей колонкой прошли.
-- Но это не «там правильно», а «там повезло с планом»; выбор колонок
-- человек меняет чаще, чем фильтры, и отказ оттуда пришёл бы как
-- «колонки показаны, но выбор не сохранён» — плашка, по которой
-- причину не найти. Правится здесь же: одна и та же ошибка в двух
-- местах правится вместе, иначе второе место остаётся ждать.
--
-- КАК ПРАВИЛЬНО. Два отдельных оператора в plpgsql. DELETE завершается,
-- и INSERT видит таблицу уже без старых строк.

CREATE OR REPLACE FUNCTION user_filters_save(p_user_id uuid, p_table_key text,
                                             p_columns text[])
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  DELETE FROM user_table_filter
   WHERE user_id = p_user_id AND table_key = p_table_key;

  INSERT INTO user_table_filter (user_id, table_key, column_id, sort_order)
  SELECT p_user_id, p_table_key, x.column_id, x.ord::smallint
    FROM unnest(p_columns) WITH ORDINALITY AS x(column_id, ord);
END
$$;

COMMENT ON FUNCTION user_filters_save(uuid, text, text[]) IS
  'Выбор фильтров пользователя. Пустой массив не записывается: отсутствие строк значит «не настраивал»';

CREATE OR REPLACE FUNCTION user_filters_copy(p_from uuid, p_to uuid)
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  IF p_from = p_to THEN
    RETURN;
  END IF;

  DELETE FROM user_table_filter WHERE user_id = p_to;

  INSERT INTO user_table_filter (user_id, table_key, column_id, sort_order)
  SELECT p_to, table_key, column_id, sort_order
    FROM user_table_filter
   WHERE user_id = p_from;
END
$$;

CREATE OR REPLACE FUNCTION user_columns_save(p_user_id uuid, p_table_key text,
                                             p_columns text[])
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  DELETE FROM user_table_column
   WHERE user_id = p_user_id AND table_key = p_table_key;

  INSERT INTO user_table_column (user_id, table_key, column_id, sort_order)
  SELECT p_user_id, p_table_key, x.column_id, x.ord::smallint
    FROM unnest(p_columns) WITH ORDINALITY AS x(column_id, ord);
END
$$;

COMMENT ON FUNCTION user_columns_save(uuid, text, text[]) IS
  'Выбор колонок пользователя. Порядок массива — порядок колонок на экране';

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
-- Проверяется именно то, что падало: вторая запись с общей колонкой.
-- Без этой проверки правка выглядела бы сделанной и на первом
-- нажатии вела бы себя как исправленная.
DO $check$
DECLARE
  sample uuid;
  kept   int;
BEGIN
  SELECT user_id INTO sample FROM app_user ORDER BY login LIMIT 1;
  IF sample IS NULL THEN
    RETURN;  -- пустая база: проверять не на ком
  END IF;

  PERFORM user_filters_save(sample, 'receipts', ARRAY['trip']);
  PERFORM user_filters_save(sample, 'receipts', ARRAY['supplier', 'trip']);
  SELECT count(*) INTO kept
    FROM user_table_filter
   WHERE user_id = sample AND table_key = 'receipts';
  PERFORM user_filters_reset(sample, 'receipts');
  IF kept <> 2 THEN
    RAISE EXCEPTION 'после повторной записи фильтров осталось % строк вместо 2', kept;
  END IF;

  PERFORM user_columns_save(sample, 'receipts', ARRAY['no', 'status']);
  PERFORM user_columns_save(sample, 'receipts', ARRAY['status', 'trip']);
  SELECT count(*) INTO kept
    FROM user_table_column
   WHERE user_id = sample AND table_key = 'receipts';
  PERFORM user_columns_reset(sample, 'receipts');
  IF kept <> 2 THEN
    RAISE EXCEPTION 'после повторной записи колонок осталось % строк вместо 2', kept;
  END IF;
END
$check$;
