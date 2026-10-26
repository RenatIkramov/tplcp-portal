-- ---------------------------------------------------------------------
-- V76. Упаковки — в справочник колонок
-- ---------------------------------------------------------------------
-- Упаковки оставались единственным списком портала, заголовок которого
-- вписан в разметку руками: тринадцать <th> подряд. Из этого следовало
-- три вещи, и все три человек видел как «тут почему-то не так»:
--   * нет окна ⚙ Колонки — ни скрыть лишнее, ни переставить;
--   * поклажедателю нельзя закрыть колонку, которую ему видеть незачем
--     (весовые и габаритные данные у кого-то свои, у кого-то чужие);
--   * переименование поля под клиента (V34) сюда не доходило вовсе.
--
-- Ключи подписей НЕ заводятся: все тринадцать уже есть в каталоге —
-- ими подписаны те самые <th>. Завести рядом col.packaging.* значило бы
-- держать два ключа на одну фразу и переводить её дважды.
--
-- Идентификатор колонки совпадает с именем поля в ответе /api/packaging:
-- экран рисует колонку значением одноимённого свойства строки, и
-- расхождение здесь означало бы пустой столбец. Исключение одно —
-- displayName: такого поля в ответе нет, его собирает экран из типа,
-- коэффициента и единицы измерения.

INSERT INTO table_column
  (table_key, column_id, text_key, object_type_id, field_id,
   sort_order, is_default, is_locked, is_pinned)
SELECT 'packaging', v.column_id, v.text_key,
       (SELECT id FROM enum_object_type WHERE code = 'packaging'),
       v.column_id, v.sort_order, true, false, v.pinned
  FROM (VALUES
    ('skuCode',           'references.column.commercial_item_code',  1::smallint, true ),
    ('skuName',           'references.column.item',                  2::smallint, false),
    ('uomName',           'references.column.base_unit_of_measure',  3::smallint, false),
    ('packagingCode',     'references.column.packaging_code',        4::smallint, true ),
    ('packagingTypeName', 'references.column.packaging_type',        5::smallint, false),
    ('displayName',       'references.column.name',                  6::smallint, false),
    ('barcode',           'references.column.packaging_barcode',     7::smallint, false),
    ('coefficient',       'references.column.factor',                8::smallint, false),
    ('lengthCm',          'references.column.length_cm',             9::smallint, false),
    ('widthCm',           'references.column.width_cm',             10::smallint, false),
    ('heightCm',          'references.column.height_cm',            11::smallint, false),
    ('volumeM3',          'references.column.volume_m',             12::smallint, false),
    ('grossWeightKg',     'references.column.gross_weight_kg',      13::smallint, false)
  ) AS v(column_id, text_key, sort_order, pinned)
ON CONFLICT (table_key, column_id) DO UPDATE
   SET text_key   = excluded.text_key,
       sort_order = excluded.sort_order,
       is_pinned  = excluded.is_pinned;

-- Все тринадцать показываются по умолчанию: это перекладка экрана
-- на справочник, а не правка того, что человек видит. Прятать что-то
-- заодно значило бы спрятать молча.

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  listed int;
  pinned int;
  nameless text;
BEGIN
  SELECT count(*) INTO listed FROM table_column WHERE table_key = 'packaging';
  IF listed <> 13 THEN
    RAISE EXCEPTION 'колонок упаковок записано %, а не 13', listed;
  END IF;

  -- Код товара и код упаковки убрать нельзя: без них строка списка
  -- ничего не называет, и человек смотрит на таблицу чисел.
  SELECT count(*) INTO pinned
    FROM table_column WHERE table_key = 'packaging' AND is_pinned;
  IF pinned <> 2 THEN
    RAISE EXCEPTION 'прикреплённых колонок упаковок %, а не 2', pinned;
  END IF;

  -- Ключ без текста человек увидит как «references.column.item».
  SELECT string_agg(c.text_key, ', ')
    INTO nameless
    FROM table_column c
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = c.text_key AND t.locale_id = l.locale_id
   WHERE c.table_key = 'packaging' AND l.code IN ('ru', 'en')
     AND (t.value IS NULL OR t.value = '');
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'у колонок упаковок нет подписи: %', nameless;
  END IF;
END
$check$;
