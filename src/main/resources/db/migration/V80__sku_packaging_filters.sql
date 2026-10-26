-- ---------------------------------------------------------------------
-- V80. Товары и упаковки: отбор и порядок — в справочник колонок
-- ---------------------------------------------------------------------
-- Последние два списка, отбиравшие в браузере. У товаров панель знала
-- четыре поля из шестидесяти двух колонок, у упаковок отбора не было
-- вовсе. Сервер при этом умел меньше, чем показывал экран: /api/sku
-- знал поиск, группу и тип, /api/packaging — ничего.
--
-- Теперь оба списка живут по тем же правилам, что документы (V78)
-- и запасы (V79): панель строится из справочника, отбор и порядок
-- считает сервер, окно ⚙ Фильтры даёт выбрать, что держать на панели.
--
-- Фильтр получает КАЖДАЯ колонка, по которой есть что искать. Это
-- не щедрость: колонка без фильтра — это заголовок, который нажимается
-- и ничего не делает, и доп. поле клиента, по которому нельзя найти
-- свой же товар.

-- ---------------------------------------------------------------------
-- Ещё один вид фильтра: признак
-- ---------------------------------------------------------------------
-- «Хрупкий», «Партионный учёт», «Требует маркировки» — это да/нет,
-- а не текст и не диапазон. Флажок V79 («больше нуля») тут не годится:
-- там колонка числовая, здесь логическая. Разные вопросы — разные виды,
-- иначе один предикат пришлось бы писать так, чтобы он угадывал тип
-- колонки, а угадывание в условии отбора — худшее место для догадок.
ALTER TABLE table_column DROP CONSTRAINT IF EXISTS chk_table_column_filter_kind;
ALTER TABLE table_column ADD CONSTRAINT chk_table_column_filter_kind
  CHECK (filter_kind IS NULL
         OR filter_kind IN ('text', 'enum', 'date', 'number', 'flag', 'bool'));

-- ---------------------------------------------------------------------
-- Товары
-- ---------------------------------------------------------------------
-- Колонка сортировки совпадает с колонкой отбора везде, кроме
-- справочных: у них в представлении лежит уже НАИМЕНОВАНИЕ
-- (item_group_name, status_name), по нему и сортируем — человек
-- читает его же.
UPDATE table_column c
   SET filter_kind   = v.kind,
       filter_column = v.column_name,
       filter_enum   = v.enum_source,
       filter_default = v.on_panel,
       sort_column   = v.column_name
  FROM (VALUES
    -- общие сведения
    ('itemCode',              'text',   'item_code',               NULL,           false),
    ('code',                  'text',   'code',                    NULL,           true ),
    ('name',                  'text',   'name',                    NULL,           true ),
    ('itemFullName',          'text',   'item_full_name',          NULL,           false),
    ('unit',                  'text',   'uom_name',                NULL,           false),
    ('orderUnit',             'text',   'order_unit_name',         NULL,           false),
    ('barcode',               'text',   'barcode',                 NULL,           false),
    ('group',                 'enum',   'item_group_name',         'sku_group',    true ),
    ('productType',           'enum',   'item_type_name',          'sku_type',     true ),
    ('statusProduct',         'enum',   'status_name',             'sku_status',   false),
    -- физические свойства
    ('length',                'number', 'length',                  NULL,           false),
    ('width',                 'number', 'width',                   NULL,           false),
    ('height',                'number', 'height',                  NULL,           false),
    ('volume',                'number', 'volume',                  NULL,           false),
    ('weightGross',           'number', 'weight_gross',            NULL,           false),
    ('weightNet',             'number', 'weight_net',              NULL,           false),
    ('color',                 'text',   'color',                   NULL,           false),
    ('size',                  'text',   'size',                    NULL,           false),
    -- производитель и классификация
    ('manufacturer',          'text',   'manufacturer',            NULL,           false),
    ('countryOrigin',         'text',   'country_origin',          NULL,           false),
    ('brand',                 'text',   'brand',                   NULL,           false),
    ('abcClass',              'text',   'abc_class',               NULL,           false),
    ('xyzClass',              'text',   'xyz_class',               NULL,           false),
    -- признаки и прослеживаемость
    ('isFragile',             'bool',   'is_fragile',              NULL,           false),
    ('isSerialTracked',       'bool',   'is_serial_tracked',       NULL,           false),
    -- WMS отдаёт эти две колонки текстом («Да»/«Нет»), не логическим типом
    ('batchTracking',         'text',   'batch_tracking',          NULL,           false),
    ('expiryTracking',        'text',   'expiry_tracking',         NULL,           false),
    ('isKit',                 'bool',   'is_kit',                  NULL,           false),
    ('isMarkingRequired',     'bool',   'is_marking_required',     NULL,           false),
    ('isExcise',              'bool',   'is_excise',               NULL,           false),
    ('isControlled',          'bool',   'is_controlled',           NULL,           false),
    ('hasPackaging',          'bool',   'has_packaging',           NULL,           false),
    ('isWmsActive',           'bool',   'is_wms_active',           NULL,           false),
    -- условия хранения
    ('hazardClass',           'text',   'hazard_class_name',       NULL,           false),
    ('hazardGroup',           'text',   'hazard_group_name',       NULL,           false),
    ('minStorageTemp',        'number', 'min_storage_temp',        NULL,           false),
    ('maxStorageTemp',        'number', 'max_storage_temp',        NULL,           false),
    ('minHumidity',           'number', 'min_humidity',            NULL,           false),
    ('maxHumidity',           'number', 'max_humidity',            NULL,           false),
    ('minDaysForReceipt',     'number', 'min_days_for_receipt',    NULL,           false),
    ('minDaysForReservation', 'number', 'min_days_for_reservation', NULL,          false),
    ('shelfLifeDays',         'number', 'shelf_life_days',         NULL,           false),
    -- коды и цена
    ('hsCode',                'text',   'hs_code',                 NULL,           false),
    ('enstruCode',            'text',   'enstru_code',             NULL,           false),
    ('currency',              'text',   'currency_code',           NULL,           false),
    ('cost',                  'number', 'cost',                    NULL,           false),
    -- служебное
    ('comment',               'text',   'comment',                 NULL,           false),
    ('createdBy',             'text',   'created_by',              NULL,           false),
    ('createdAt',             'date',   'created_at',              NULL,           false),
    ('updatedBy',             'text',   'updated_by',              NULL,           false),
    ('updatedAt',             'date',   'updated_at',              NULL,           false),
    -- доп. атрибуты клиента
    ('customAttr1',  'text', 'custom_attr1',  NULL, false),
    ('customAttr2',  'text', 'custom_attr2',  NULL, false),
    ('customAttr3',  'text', 'custom_attr3',  NULL, false),
    ('customAttr4',  'text', 'custom_attr4',  NULL, false),
    ('customAttr5',  'text', 'custom_attr5',  NULL, false),
    ('customAttr6',  'text', 'custom_attr6',  NULL, false),
    ('customAttr7',  'text', 'custom_attr7',  NULL, false),
    ('customAttr8',  'text', 'custom_attr8',  NULL, false),
    ('customAttr9',  'text', 'custom_attr9',  NULL, false),
    ('customAttr10', 'text', 'custom_attr10', NULL, false)
  ) AS v(column_id, kind, column_name, enum_source, on_panel)
 WHERE c.table_key = 'sku'
   AND c.column_id = v.column_id;

-- ---------------------------------------------------------------------
-- Упаковки
-- ---------------------------------------------------------------------
UPDATE table_column c
   SET filter_kind   = v.kind,
       filter_column = v.column_name,
       filter_enum   = v.enum_source,
       filter_default = v.on_panel,
       sort_column   = v.column_name
  FROM (VALUES
    ('skuCode',           'text',   'sku_code',            NULL,              true ),
    ('skuName',           'text',   'sku_name',            NULL,              true ),
    ('uomName',           'text',   'uom_name',            NULL,              false),
    ('packagingCode',     'text',   'packaging_code',      NULL,              true ),
    ('packagingTypeName', 'enum',   'packaging_type_name', 'packaging_type',  true ),
    ('barcode',           'text',   'barcode',             NULL,              false),
    ('coefficient',       'number', 'coefficient',         NULL,              false),
    ('lengthCm',          'number', 'length_cm',           NULL,              false),
    ('widthCm',           'number', 'width_cm',            NULL,              false),
    ('heightCm',          'number', 'height_cm',           NULL,              false),
    ('volumeM3',          'number', 'volume_m3',           NULL,              false),
    ('grossWeightKg',     'number', 'gross_weight_kg',     NULL,              false)
  ) AS v(column_id, kind, column_name, enum_source, on_panel)
 WHERE c.table_key = 'packaging'
   AND c.column_id = v.column_id;

-- ---------------------------------------------------------------------
-- Подпись признака
-- ---------------------------------------------------------------------
-- У флажка-признака подпись — название колонки («Хрупкий»), а что он
-- делает, говорит подсказка. Ключ общий на все такие флажки: правило
-- у них одно.
--
-- Второй ключ — подсказка галочки «показать архив». Архив не фильтр
-- панели, а свойство списка, и объяснить его надо отдельно: флажок
-- признака СУЖАЕТ список, а этот — РАСШИРЯЕТ, и одна подсказка
-- на оба сказала бы человеку неправду про один из них.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.hint.only_marked', 'common', 'hint', NULL,
   'подсказка флажка-фильтра по признаку: оставить только те строки, где признак стоит'),
  ('common.hint.show_archived', 'common', 'hint', NULL,
   'подсказка галочки архива в панели фильтров: показать и погашенные записи')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.hint.only_marked', 'ru', 'Только строки, где признак стоит'),
    ('common.hint.only_marked', 'en', 'Only rows where the flag is set'),
    ('common.hint.show_archived', 'ru', 'Показать и погашенные записи'),
    ('common.hint.show_archived', 'en', 'Show deactivated records too')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Ключ, которому стало нечего подписывать
-- ---------------------------------------------------------------------
-- 'sku.text.archive' был подписью над галочкой «показать архив»,
-- которую экран товаров вписывал себе сам. Галочку теперь рисует
-- панель фильтров — с подписью колонки и подсказкой, — и подпись
-- над ней не нужна. Оставить ключ значило бы оставить его в списке
-- «Тексты интерфейса»: человек переведёт его и будет ждать, что
-- перевод где-то виден.
DELETE FROM ui_text WHERE text_key = 'sku.text.archive';
DELETE FROM ui_text_key WHERE text_key = 'sku.text.archive';

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  stranger text;
  nameless text;
  defaults int;
BEGIN
  -- Имя колонки попадает в текст запроса. Опечатку здесь не увидеть
  -- ни глазами, ни тестом сервиса: вылезет она у человека, который
  -- поставил фильтр или нажал на заголовок.
  SELECT string_agg(c.table_key || '.' || c.column_id || ' → ' || x.name, ', ')
    INTO stranger
    FROM table_column c
    CROSS JOIN LATERAL (VALUES (c.filter_column), (c.sort_column)) AS x(name)
   WHERE c.table_key IN ('sku', 'packaging')
     AND x.name IS NOT NULL
     AND NOT EXISTS (
           SELECT 1 FROM information_schema.columns ic
            WHERE ic.table_schema = 'dbo'
              AND ic.table_name = CASE c.table_key WHEN 'sku' THEN 'v_sku_full'
                                                   ELSE 'v_product_packaging' END
              AND ic.column_name = x.name);
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'отбор или сортировка ссылаются на несуществующие колонки: %', stranger;
  END IF;

  -- Колонок без фильтра должно остаться ровно две, и обе названы
  -- поимённо: у них нет своей колонки в представлении.
  --   sku.guid              — идентификатор в WMS, экран его рисует,
  --                           а представление не отдаёт;
  --   packaging.displayName — подпись собирается экраном из типа,
  --                           коэффициента и единицы измерения.
  -- Любая третья — это забытая колонка, а не решение.
  SELECT string_agg(table_key || '.' || column_id, ', ')
    INTO nameless
    FROM table_column
   WHERE table_key IN ('sku', 'packaging')
     AND filter_kind IS NULL
     AND (table_key, column_id) NOT IN (('sku', 'guid'), ('packaging', 'displayName'));
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'колонки остались без фильтра: %', nameless;
  END IF;

  -- Справочный фильтр без источника значений — пустой выпадающий список.
  SELECT string_agg(table_key || '.' || column_id, ', ')
    INTO stranger
    FROM table_column
   WHERE table_key IN ('sku', 'packaging')
     AND filter_kind = 'enum' AND filter_enum IS NULL;
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'у справочного фильтра нет источника значений: %', stranger;
  END IF;

  -- Панель, у которой ни одного фильтра по умолчанию, открывается пустой.
  FOR stranger IN SELECT unnest(ARRAY['sku', 'packaging']) LOOP
    SELECT count(*) INTO defaults
      FROM table_column WHERE table_key = stranger AND filter_default;
    IF defaults < 3 THEN
      RAISE EXCEPTION 'фильтров по умолчанию у % всего %, панель будет пустой',
            stranger, defaults;
    END IF;
  END LOOP;

  -- Признак спрашивает «стоит ли» — значит колонка должна быть логической.
  SELECT string_agg(c.column_id, ', ') INTO stranger
    FROM table_column c
    JOIN information_schema.columns ic
      ON ic.table_schema = 'dbo' AND ic.table_name = 'v_sku_full'
     AND ic.column_name = c.filter_column
   WHERE c.table_key = 'sku' AND c.filter_kind = 'bool'
     AND ic.data_type <> 'boolean';
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'признак поставлен не на логическую колонку: %', stranger;
  END IF;

  SELECT string_agg(k.text_key || ':' || l.code, ', ') INTO nameless
    FROM ui_text_key k
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('common.hint.only_marked', 'common.hint.show_archived')
     AND l.code IN ('ru', 'en')
     AND (t.value IS NULL OR t.value = '');
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'нет текста у ключей: %', nameless;
  END IF;
END
$check$;
