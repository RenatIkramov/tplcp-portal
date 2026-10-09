-- Почему ReadApiTest.skuAndFilters ждёт 8 товаров, а видит 9.
--
-- Запускать ОТ ИМЕНИ portal_app, не от postgres: под суперпользователем
-- RLS не действует, и список получится не тот, что видит портал.
--
--   psql -U portal_app -d tplcp -f check_sku_count.sql
--
-- Контекст поклажедателя ставится так же, как его ставит портал:
-- через set_config('app.depositor_id', ...). Без этого RLS вернёт ноль
-- строк и будет казаться, что товаров нет вовсе.

\set ON_ERROR_STOP on

-- 1. Кто такой D-001 («Рога и Копыта») с точки зрения доступов клиента.
--    Колонка называется org_name: у поклажедателя в базе хранится
--    наименование организации, а не просто «имя».
SELECT depositor_id, code, org_name
FROM dbo.depositors_for_user('client@roga-kopyta.kz')
WHERE code = 'D-001';

-- 2. Ставим контекст этого поклажедателя на текущий сеанс
SELECT set_config('app.depositor_id',
                  (SELECT depositor_id::text
                   FROM dbo.depositors_for_user('client@roga-kopyta.kz')
                   WHERE code = 'D-001'),
                  false);

-- 3. Сколько товаров видит портал (тест ждёт 8)
SELECT count(*) AS sku_total FROM dbo.sku;

-- 4. Все товары по порядку создания: последние — самые подозрительные
SELECT code, name, item_group, item_type, is_active, created_at
FROM dbo.sku
ORDER BY created_at NULLS FIRST, code;

-- 5. Остатки тестовых прогонов. Должно быть пусто:
--    QA-TEST- оставляет SkuDirectoryTest, QA-IMP- — SkuImportTest.
SELECT code, name, created_at
FROM dbo.sku
WHERE code LIKE 'QA-TEST-%' OR code LIKE 'QA-IMP-%';

-- 6. Незакрытые загрузки Excel — тоже остаток прерванного прогона
SELECT import_id, file_name, status, row_count, created_rows, updated_rows,
       error_count, started_at
FROM dbo.excel_import
ORDER BY started_at DESC;

-- 7. Если в п.5 что-то нашлось — вот удаление. Раскомментировать осознанно.
--    Порядок важен: упаковки и ссылку order_unit_id снимаем до товара,
--    иначе внешний ключ не даст удалить.
--
-- DELETE FROM dbo.product_packaging WHERE sku_id IN
--   (SELECT sku_id FROM dbo.sku
--     WHERE code LIKE 'QA-TEST-%' OR code LIKE 'QA-IMP-%');
-- UPDATE dbo.sku SET order_unit_id = NULL
--   WHERE code LIKE 'QA-TEST-%' OR code LIKE 'QA-IMP-%';
-- DELETE FROM dbo.sku
--   WHERE code LIKE 'QA-TEST-%' OR code LIKE 'QA-IMP-%';
