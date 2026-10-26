-- ---------------------------------------------------------------------
-- V79. Запасы: отбор и порядок — в справочник колонок
-- ---------------------------------------------------------------------
-- Экран «Запасы» отбирал в браузере по четырём полям, вписанным
-- в разметку: код, наименование, группа, «Только брак». Приделать
-- к такому отбору страницы нельзя: сервер отрезал бы двадцать строк
-- ДО того, как экран применит свои четыре поля, и человек получал бы
-- пустую страницу при непустом отборе.
--
-- Поэтому запасы переходят на тот же механизм, что документы (V68–V73,
-- V78): панель строится из справочника колонок, отбор и порядок считает
-- сервер, а окно ⚙ Фильтры даёт человеку выбрать, что держать на панели.
--
-- Что из этого человек увидит сразу:
--   * отбор по складу, единице измерения и диапазонам количеств —
--     его не было вовсе;
--   * список групп в выпадающем списке перестанет врать. Сейчас там
--     вписаны «Сырьё», «Мясная продукция», «Молочная продукция»,
--     «Готовые наборы» — наследство демо. Группа товара с V38 — это
--     свободный текст поклажедателя, и совпадение с этими четырьмя
--     значениями случайно. Теперь список собирается из тех групп,
--     которые у этого клиента действительно есть (filter_enum =
--     'item_group'), и собирает его сервер — под RLS, то есть чужих
--     групп в нём не будет.

-- ---------------------------------------------------------------------
-- Новый вид фильтра: флажок
-- ---------------------------------------------------------------------
-- «Только брак» — это не диапазон и не текст: это вопрос «есть ли».
-- Выразить его числовым фильтром можно («брак с 0,001»), но человеку
-- вместо одного щелчка пришлось бы вспоминать, какое число написать.
-- Поэтому отдельный вид: флажок, условие «больше нуля».
ALTER TABLE table_column DROP CONSTRAINT IF EXISTS chk_table_column_filter_kind;
ALTER TABLE table_column ADD CONSTRAINT chk_table_column_filter_kind
  CHECK (filter_kind IS NULL
         OR filter_kind IN ('text', 'enum', 'date', 'number', 'flag'));

-- ---------------------------------------------------------------------
-- Колонки запасов
-- ---------------------------------------------------------------------
-- Колонка сортировки и колонка отбора — РАЗНЫЕ вещи там, где человек
-- читает одно, а сравнивается другое. Склад отбирается по
-- warehouse_id (выбран конкретный склад), а сортируется по
-- warehouse_name: порядок по идентификаторам выглядит случайным.
UPDATE table_column c
   SET filter_kind   = v.filter_kind,
       filter_column = v.filter_column,
       filter_enum   = v.filter_enum,
       filter_default = v.filter_default,
       sort_column   = v.sort_column
  FROM (VALUES
    ('warehouse', 'enum',   'warehouse_id',    'warehouse', true,  'warehouse_name'),
    ('code',      'text',   'sku_code',        NULL,        true,  'sku_code'),
    ('name',      'text',   'sku_name',        NULL,        true,  'sku_name'),
    ('group',     'enum',   'item_group_name', 'item_group', true,  'item_group_name'),
    ('unit',      'text',   'unit_name',       NULL,        false, 'unit_name'),
    ('stock',     'number', 'quantity',        NULL,        false, 'quantity'),
    ('available', 'number', 'available_qty',   NULL,        false, 'available_qty'),
    ('reserved',  'number', 'reserved_qty',    NULL,        false, 'reserved_qty'),
    ('blocked',   'number', 'blocked_qty',     NULL,        false, 'blocked_qty'),
    ('defect',    'flag',   'defect_qty',      NULL,        true,  'defect_qty'),
    ('incoming',  'number', 'incoming_qty',    NULL,        false, 'incoming_qty')
  ) AS v(column_id, filter_kind, filter_column, filter_enum, filter_default, sort_column)
 WHERE c.table_key = 'stock'
   AND c.column_id = v.column_id;

-- ---------------------------------------------------------------------
-- Подпись флажка
-- ---------------------------------------------------------------------
-- У флажка подпись — название колонки («Брак»), а что именно он делает,
-- говорит всплывающая подсказка. Ключ общий: флажков будет больше
-- одного, а правило у них одно.
--
-- Прежняя надпись «Только брак» была вписана в разметку по-русски
-- и в каталог не попадала вовсе — на английском языке она такой
-- и оставалась.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.hint.only_nonzero', 'common', 'hint', NULL,
   'подсказка флажка-фильтра: оставить только строки, где значение больше нуля'),
  ('error.list.unknown', 'server', 'message', 'lists',
   'отказ: запрошен список, которого в портале нет')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.hint.only_nonzero', 'ru', 'Только строки, где значение больше нуля'),
    ('common.hint.only_nonzero', 'en', 'Only rows where the value is above zero'),
    ('error.list.unknown',       'ru', 'Список «{list}» порталу неизвестен'),
    ('error.list.unknown',       'en', 'Unknown list "{list}"')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  nameless text;
  stranger text;
  defaults int;
  nokey    text;
BEGIN
  SELECT string_agg(k.text_key || ':' || l.code, ', ') INTO nokey
    FROM ui_text_key k
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('common.hint.only_nonzero', 'error.list.unknown')
     AND l.code IN ('ru', 'en')
     AND (t.value IS NULL OR t.value = '');
  IF nokey IS NOT NULL THEN
    RAISE EXCEPTION 'нет текста у ключей: %', nokey;
  END IF;

  -- Колонка без сортировки — неподвижный заголовок на экране, где
  -- соседние заголовки нажимаются.
  SELECT string_agg(column_id, ', ') INTO nameless
    FROM table_column WHERE table_key = 'stock' AND sort_column IS NULL;
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'у колонок запасов нет колонки сортировки: %', nameless;
  END IF;

  SELECT string_agg(column_id, ', ') INTO nameless
    FROM table_column WHERE table_key = 'stock' AND filter_kind IS NULL;
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'у колонок запасов нет вида фильтра: %', nameless;
  END IF;

  -- Имя колонки попадает в текст запроса. Опечатку здесь не увидеть
  -- ни глазами, ни тестом сервиса — вылезет она у человека, который
  -- нажал на заголовок или поставил фильтр.
  SELECT string_agg(c.column_id || ' → ' || x.name, ', ') INTO stranger
    FROM table_column c
    CROSS JOIN LATERAL (VALUES (c.filter_column), (c.sort_column)) AS x(name)
   WHERE c.table_key = 'stock'
     AND NOT EXISTS (
           SELECT 1 FROM information_schema.columns ic
            WHERE ic.table_schema = 'dbo'
              AND ic.table_name = 'v_stock_lines'
              AND ic.column_name = x.name);
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'отбор или сортировка ссылаются на несуществующие колонки: %', stranger;
  END IF;

  -- Панель, у которой ни одного фильтра по умолчанию, открывается пустой.
  SELECT count(*) INTO defaults
    FROM table_column WHERE table_key = 'stock' AND filter_default;
  IF defaults < 3 THEN
    RAISE EXCEPTION 'фильтров по умолчанию у запасов всего %, панель будет пустой', defaults;
  END IF;

  -- Флажок спрашивает «больше нуля» — значит колонка должна быть числом.
  SELECT string_agg(c.column_id, ', ') INTO stranger
    FROM table_column c
    JOIN information_schema.columns ic
      ON ic.table_schema = 'dbo' AND ic.table_name = 'v_stock_lines'
     AND ic.column_name = c.filter_column
   WHERE c.table_key = 'stock' AND c.filter_kind = 'flag'
     AND ic.data_type NOT IN ('numeric', 'integer', 'bigint', 'smallint',
                              'real', 'double precision');
  IF stranger IS NOT NULL THEN
    RAISE EXCEPTION 'флажок поставлен не на числовую колонку: %', stranger;
  END IF;
END
$check$;
