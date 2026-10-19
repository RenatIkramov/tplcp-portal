-- =====================================================================
-- Выгрузка данных разработки из рабочей базы в файл
-- =====================================================================
-- ЗАЧЕМ. Поклажедатели, склады, пользователи, товары, документы и остатки
-- попали в базу вручную ещё до перехода на Flyway. В репозитории их нет,
-- поэтому на чистой базе портал не с кем открыть, а тестовая сборка
-- не стартует вовсе. Этот скрипт снимает такие данные одним файлом,
-- из которого потом делается повторяемый tools/dev-data.sql.
--
-- КАК ЗАПУСКАТЬ. Под ролью postgres (владельцем схемы) — в SQL-консоли
-- IDE или в psql. Файл пишет САМ СЕРВЕР базы, поэтому путь — это путь
-- на той машине, где стоит PostgreSQL, и права нужны суперпользователя.
-- Под portal_app скрипт не выполнится, и это правильно: роль приложения
-- не должна уметь писать файлы.
--
-- РЕЗУЛЬТАТ: C:/3PL_Customer_Portal/dev-data.sql — готовые INSERT'ы
-- в порядке зависимостей, по строке на запись.
--
-- ЧЕГО ЗДЕСЬ НЕТ НАРОЧНО:
--   * перечисления, тексты интерфейса, справочник колонок — их заводят
--     миграции, и выгружать их значит однажды получить две версии;
--   * личные настройки: переименования полей, выбор колонок, журнал
--     загрузок Excel, токены «запомнить меня» — новая база разработки
--     должна начинаться с умолчаний, иначе непонятно, что в портале
--     своё, а что нажито проверками.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Генератор INSERT'ов для одной таблицы
-- ---------------------------------------------------------------------
-- Значения заворачиваются в quote_nullable: он сам экранирует кавычки
-- и превращает NULL в NULL без кавычек. Числа, даты и uuid уходят
-- строковыми литералами — база приведёт их к типу колонки при вставке.
--
-- Переводы строк внутри значений заменяются пробелом: файл читается
-- построчно, и комментарий в две строки разорвал бы INSERT пополам.
CREATE OR REPLACE FUNCTION pg_temp.insert_lines(p_table text)
RETURNS SETOF text
LANGUAGE plpgsql AS $gen$
DECLARE
  v_columns text;
  v_values  text;
BEGIN
  SELECT string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position),
         string_agg('quote_nullable(replace(' || quote_ident(column_name)
                    || '::text, chr(10), '' ''))', ', ' ORDER BY ordinal_position)
    INTO v_columns, v_values
    FROM information_schema.columns
   WHERE table_schema = 'dbo' AND table_name = p_table;

  IF v_columns IS NULL THEN
    RAISE EXCEPTION 'Нет такой таблицы: %', p_table;
  END IF;

  RETURN QUERY EXECUTE format(
    'SELECT ''INSERT INTO dbo.%1$I (%2$s) VALUES ('' || concat_ws('', '', %3$s)'
    || ' || '') ON CONFLICT DO NOTHING;'' FROM dbo.%1$I',
    p_table, v_columns, v_values);
END
$gen$;

-- ---------------------------------------------------------------------
-- 2. Таблицы в порядке зависимостей
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.dev_data()
RETURNS SETOF text
LANGUAGE plpgsql AS $all$
DECLARE
  v_table text;
  v_order text[] := ARRAY[
    'operator', 'depositor', 'warehouse', 'depositor_warehouse', 'depositor_settings',
    'app_user', 'user_settings', 'user_depositor_access', 'user_warehouse_access',
    'supplier', 'consignee', 'delivery_point',
    'sku', 'product_packaging',
    'receipts', 'receipt_details', 'orders', 'order_details',
    'transfers', 'transfer_details', 'stock',
    'registered_service', 'service_tariff', 'invoice'];
BEGIN
  FOREACH v_table IN ARRAY v_order LOOP
    -- Пустых строк не выдаём нарочно: COPY в формате csv обрамляет
    -- пустое значение кавычкой-разделителем, и в файле появляется
    -- невидимый символ, на котором psql спотыкается синтаксической
    -- ошибкой. Разделяем таблицы комментарием, он никогда не пуст.
    RETURN NEXT '-- ------------------------------------------------';
    RETURN NEXT '-- ' || v_table;
    RETURN QUERY SELECT * FROM pg_temp.insert_lines(v_table);
  END LOOP;
END
$all$;

-- ---------------------------------------------------------------------
-- 3. Запись файла
-- ---------------------------------------------------------------------
-- FORMAT csv с невстречающимися разделителем и кавычкой: в этом режиме
-- COPY не экранирует ни кавычки, ни обратные слэши — строки уходят
-- в файл как есть. В текстовом формате обратный слэш превратился бы
-- в \\ и испортил бы данные.
COPY (SELECT * FROM pg_temp.dev_data())
  TO 'C:/3PL_Customer_Portal/dev-data.sql'
  WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01');
