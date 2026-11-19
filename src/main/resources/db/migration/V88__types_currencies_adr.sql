-- ---------------------------------------------------------------------
-- V88. Типы заказов и поступлений, валюты, ДОПОГ
-- ---------------------------------------------------------------------
-- Списки согласованы 29.10.2026 («Перечисления — согласование списков»,
-- §2, §3, §6, §7). Имя по умолчанию английское, русское — перевод (V86).
--
--   * Типы заказов и поступлений: по шесть значений, код 01…06.
--     Прежние переводятся по согласованной таблице. Новые типы
--     администратор заводит сам — код у них тоже две цифры (проверка
--     в базе и в портале), иначе обмен с WMS по коду рассыплется.
--   * Валюты: код перечисления — цифровой ISO 4217 (398, 840…),
--     отдельной колонкой num_code. Буквенный (KZT, USD…) остаётся
--     в code: его видят люди, по нему узнаётся валюта в Excel.
--     Добавлен китайский юань.
--   * Классы опасности — по ДОПОГ (0, 1, 2, 3, 4.1 … 9), группы
--     упаковки — 0, I, II, III. Код — как он пишется в документах.
--   * Единицы измерения переведены на МКЕИ ещё в V86.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Типы заказов и поступлений
-- ---------------------------------------------------------------------
-- Порядок: снять внешний ключ, перевести документы на новые номера,
-- заменить строки, вернуть ключ. Идентификатор = число кода (1…6).
-- Тип, которого нет в таблице перевода (заведён администратором),
-- миграцию останавливает: куда его девать, решает человек.
-- Остатки проверок: PortalDirectoryTest заводит тип «qa-type» и сам
-- его удаляет, но упавший посреди прогон мог его оставить. Документов
-- на нём нет — убираем, иначе он остановил бы миграцию ниже.
DELETE FROM enum_order_type t WHERE t.code = 'qa-type'
   AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.order_type_id = t.id);
DELETE FROM enum_receipt_doc_type t WHERE t.code = 'qa-type'
   AND NOT EXISTS (SELECT 1 FROM receipts r WHERE r.doc_type_id = t.id);

DO $types$
DECLARE
  bad text;
BEGIN
  SELECT string_agg(code, ', ') INTO bad FROM enum_order_type
   WHERE code NOT IN ('sale', 'return_supplier', 'transfer_out', 'writeoff');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'типы заказов без перевода в новый список: %', bad;
  END IF;
  SELECT string_agg(code, ', ') INTO bad FROM enum_receipt_doc_type
   WHERE code NOT IN ('invoice', 'import', 'return', 'transfer_in');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'типы поступлений без перевода в новый список: %', bad;
  END IF;
END
$types$;

ALTER TABLE orders   DROP CONSTRAINT fk_orders_order_type_id;
ALTER TABLE receipts DROP CONSTRAINT fk_receipts_doc_type_id;

-- RLS: Flyway ходит суперпользователем и видит все документы. Если
-- однажды пойдёт ролью без этого права — непереведённые документы
-- не дадут вернуть внешний ключ ниже. Громко, а не молча.
UPDATE orders o
   SET order_type_id = CASE t.code WHEN 'sale'            THEN 1
                                   WHEN 'return_supplier' THEN 5
                                   WHEN 'transfer_out'    THEN 1
                                   WHEN 'writeoff'        THEN 6 END
  FROM enum_order_type t
 WHERE t.id = o.order_type_id;

UPDATE receipts r
   SET doc_type_id = CASE t.code WHEN 'invoice'     THEN 1
                                 WHEN 'import'      THEN 2
                                 WHEN 'return'      THEN 3
                                 WHEN 'transfer_in' THEN 1 END
  FROM enum_receipt_doc_type t
 WHERE t.id = r.doc_type_id;

DELETE FROM enum_order_type;
INSERT INTO enum_order_type (id, code, name, sort_order, is_active) VALUES
  (1, '01', 'Standard order',         1, true),
  (2, '02', 'Express',                2, true),
  (3, '03', 'Shipment to production', 3, true),
  (4, '04', 'Cross-docking',          4, true),
  (5, '05', 'Return to supplier',     5, true),
  (6, '06', 'Write-off',              6, true);

DELETE FROM enum_receipt_doc_type;
INSERT INTO enum_receipt_doc_type (id, code, name, sort_order, is_active) VALUES
  (1, '01', 'Standard delivery',          1, true),
  (2, '02', 'Import (customs warehouse)', 2, true),
  (3, '03', 'Customer return',            3, true),
  (4, '04', 'Production',                 4, true),
  (5, '05', 'Cross-docking',              5, true),
  (6, '06', 'Stock posting',              6, true);

ALTER TABLE orders ADD CONSTRAINT fk_orders_order_type_id
  FOREIGN KEY (order_type_id) REFERENCES enum_order_type (id);
ALTER TABLE receipts ADD CONSTRAINT fk_receipts_doc_type_id
  FOREIGN KEY (doc_type_id) REFERENCES enum_receipt_doc_type (id);

-- Код — две цифры. Портал проверяет это сам и говорит понятно;
-- здесь — на случай правки мимо портала.
ALTER TABLE enum_order_type ADD CONSTRAINT enum_order_type_code_format
  CHECK (code ~ '^[0-9]{2}$');
ALTER TABLE enum_receipt_doc_type ADD CONSTRAINT enum_receipt_doc_type_code_format
  CHECK (code ~ '^[0-9]{2}$');

-- ---------------------------------------------------------------------
-- 2. Валюты
-- ---------------------------------------------------------------------
ALTER TABLE enum_currency ADD COLUMN num_code varchar(3);
COMMENT ON COLUMN enum_currency.num_code IS
  'цифровой код ISO 4217 — код перечисления, по нему обмен с WMS (V88)';
COMMENT ON COLUMN enum_currency.code IS
  'буквенный код ISO 4217 (KZT, USD…) — для людей и Excel';

UPDATE enum_currency SET num_code = '398', name = 'Kazakhstani tenge', sort_order = 1 WHERE code = 'KZT';
UPDATE enum_currency SET num_code = '840', name = 'US dollar',         sort_order = 2 WHERE code = 'USD';
UPDATE enum_currency SET num_code = '978', name = 'Euro',              sort_order = 3 WHERE code = 'EUR';
UPDATE enum_currency SET num_code = '643', name = 'Russian rouble',    sort_order = 4 WHERE code = 'RUB';
INSERT INTO enum_currency (id, code, name, sort_order, is_active, num_code)
SELECT COALESCE(max(id), 0) + 1, 'CNY', 'Chinese yuan', 5, true, '156' FROM enum_currency
 WHERE NOT EXISTS (SELECT 1 FROM enum_currency WHERE code = 'CNY');

DO $cur$
BEGIN
  IF EXISTS (SELECT 1 FROM enum_currency WHERE num_code IS NULL) THEN
    RAISE EXCEPTION 'валюта без цифрового кода ISO 4217: %',
      (SELECT string_agg(code, ', ') FROM enum_currency WHERE num_code IS NULL);
  END IF;
END
$cur$;
ALTER TABLE enum_currency ALTER COLUMN num_code SET NOT NULL;
CREATE UNIQUE INDEX enum_currency_num_code_uq ON enum_currency (num_code);

UPDATE enum_registry SET code_column = 'num_code' WHERE enum_key = 'enum_currency';

-- ---------------------------------------------------------------------
-- 3. ДОПОГ: классы опасности и группы упаковки
-- ---------------------------------------------------------------------
DO $adr$
DECLARE
  bad text;
BEGIN
  SELECT string_agg(code, ', ') INTO bad FROM enum_hazard_class
   WHERE code NOT IN ('none', '3', '8', '9');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'классы опасности без перевода в ДОПОГ: %', bad;
  END IF;
  SELECT string_agg(code, ', ') INTO bad FROM enum_hazard_group
   WHERE code NOT IN ('none', 'I', 'II', 'III');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'группы опасности без перевода в ДОПОГ: %', bad;
  END IF;
END
$adr$;

ALTER TABLE sku DROP CONSTRAINT fk_sku_hazard_class_id;

-- Новые номера классов — по порядку списка: 0 → 1, 3 → 4, 8 → 13, 9 → 14
UPDATE sku s
   SET hazard_class_id = CASE h.code WHEN 'none' THEN 1 WHEN '3' THEN 4
                                     WHEN '8' THEN 13 WHEN '9' THEN 14 END
  FROM enum_hazard_class h
 WHERE h.id = s.hazard_class_id;

DELETE FROM enum_hazard_class;
INSERT INTO enum_hazard_class (id, code, name, sort_order, is_active) VALUES
  (1,  '0',   'Not dangerous goods',                                      1,  true),
  (2,  '1',   'Explosive substances and articles',                        2,  true),
  (3,  '2',   'Gases',                                                    3,  true),
  (4,  '3',   'Flammable liquids',                                        4,  true),
  (5,  '4.1', 'Flammable solids',                                         5,  true),
  (6,  '4.2', 'Substances liable to spontaneous combustion',              6,  true),
  (7,  '4.3', 'Substances which in contact with water emit flammable gases', 7, true),
  (8,  '5.1', 'Oxidizing substances',                                     8,  true),
  (9,  '5.2', 'Organic peroxides',                                        9,  true),
  (10, '6.1', 'Toxic substances',                                         10, true),
  (11, '6.2', 'Infectious substances',                                    11, true),
  (12, '7',   'Radioactive material',                                     12, true),
  (13, '8',   'Corrosive substances',                                     13, true),
  (14, '9',   'Miscellaneous dangerous substances and articles',          14, true);

ALTER TABLE sku ADD CONSTRAINT fk_sku_hazard_class_id
  FOREIGN KEY (hazard_class_id) REFERENCES enum_hazard_class (id);

-- Группы: номера прежние, меняются код и имя
UPDATE enum_hazard_group SET code = '0',   name = 'No packing group',                      sort_order = 1 WHERE code = 'none';
UPDATE enum_hazard_group SET code = 'I',   name = 'Packing group I — high danger',         sort_order = 2 WHERE code = 'I';
UPDATE enum_hazard_group SET code = 'II',  name = 'Packing group II — medium danger',      sort_order = 3 WHERE code = 'II';
UPDATE enum_hazard_group SET code = 'III', name = 'Packing group III — low danger',        sort_order = 4 WHERE code = 'III';

-- ---------------------------------------------------------------------
-- 4. Русские переводы
-- ---------------------------------------------------------------------
-- Прежние переводы этих перечислений (если их успели завести на вкладке)
-- относились к старым значениям — у типов и классов номера переехали.
DELETE FROM enum_text
 WHERE enum_key IN ('enum_order_type', 'enum_receipt_doc_type', 'enum_hazard_class');

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT v.enum_key, v.id, l.locale_id, v.name
  FROM (VALUES
    ('enum_order_type', '1', 'Обычный заказ'),
    ('enum_order_type', '2', 'Экспресс'),
    ('enum_order_type', '3', 'Отгрузка в производство'),
    ('enum_order_type', '4', 'Кросс-докинг'),
    ('enum_order_type', '5', 'Возврат поставщику'),
    ('enum_order_type', '6', 'Списание'),
    ('enum_receipt_doc_type', '1', 'Обычная поставка'),
    ('enum_receipt_doc_type', '2', 'Импорт (таможенный склад)'),
    ('enum_receipt_doc_type', '3', 'Возврат от покупателя'),
    ('enum_receipt_doc_type', '4', 'Производство'),
    ('enum_receipt_doc_type', '5', 'Кросс-докинг'),
    ('enum_receipt_doc_type', '6', 'Оприходование'),
    ('enum_hazard_class', '1',  'Не опасный груз'),
    ('enum_hazard_class', '2',  'Взрывчатые вещества и изделия'),
    ('enum_hazard_class', '3',  'Газы'),
    ('enum_hazard_class', '4',  'Легковоспламеняющиеся жидкости'),
    ('enum_hazard_class', '5',  'Легковоспламеняющиеся твёрдые вещества'),
    ('enum_hazard_class', '6',  'Вещества, способные к самовозгоранию'),
    ('enum_hazard_class', '7',  'Вещества, выделяющие воспламеняющиеся газы при соприкосновении с водой'),
    ('enum_hazard_class', '8',  'Окисляющие вещества'),
    ('enum_hazard_class', '9',  'Органические пероксиды'),
    ('enum_hazard_class', '10', 'Токсичные вещества'),
    ('enum_hazard_class', '11', 'Инфекционные вещества'),
    ('enum_hazard_class', '12', 'Радиоактивные материалы'),
    ('enum_hazard_class', '13', 'Коррозионные вещества'),
    ('enum_hazard_class', '14', 'Прочие опасные вещества и изделия')
  ) AS v(enum_key, id, name)
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_hazard_group', g.id::text, l.locale_id, v.name
  FROM (VALUES
    ('0',   'Без группы'),
    ('I',   'Группа упаковки I — высокая опасность'),
    ('II',  'Группа упаковки II — средняя опасность'),
    ('III', 'Группа упаковки III — низкая опасность')
  ) AS v(code, name)
  JOIN enum_hazard_group g ON g.code = v.code
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_currency', c.id::text, l.locale_id, v.name
  FROM (VALUES
    ('KZT', 'Тенге'),
    ('USD', 'Доллар США'),
    ('EUR', 'Евро'),
    ('RUB', 'Российский рубль'),
    ('CNY', 'Китайский юань')
  ) AS v(code, name)
  JOIN enum_currency c ON c.code = v.code
  JOIN dictionary_locale l ON l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- ---------------------------------------------------------------------
-- 5. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.directory.code_two_digits', 'server', 'message', 'admin',
   'сообщение сервера: код типа заказа или поступления — две цифры (V88)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.directory.code_two_digits', 'ru', 'Код — две цифры, от 01 до 99'),
    ('error.directory.code_two_digits', 'en', 'The code is two digits, from 01 to 99')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
BEGIN
  IF (SELECT string_agg(code, ',' ORDER BY sort_order) FROM enum_order_type)
       IS DISTINCT FROM '01,02,03,04,05,06' THEN
    RAISE EXCEPTION 'типы заказов не совпали с согласованным списком';
  END IF;
  IF (SELECT string_agg(code, ',' ORDER BY sort_order) FROM enum_receipt_doc_type)
       IS DISTINCT FROM '01,02,03,04,05,06' THEN
    RAISE EXCEPTION 'типы поступлений не совпали с согласованным списком';
  END IF;
  IF (SELECT string_agg(num_code, ',' ORDER BY sort_order) FROM enum_currency)
       IS DISTINCT FROM '398,840,978,643,156' THEN
    RAISE EXCEPTION 'валюты не совпали с согласованным списком';
  END IF;
  IF (SELECT count(*) FROM enum_hazard_class) <> 14 THEN
    RAISE EXCEPTION 'классов ДОПОГ должно быть 14';
  END IF;
  IF (SELECT count(*) FROM enum_text_matrix() m
       WHERE m.enum_key = 'enum_currency' AND m.locale_code = 'en' AND m.code = '398') <> 1 THEN
    RAISE EXCEPTION 'на вкладке «Перечисления» у валюты должен быть цифровой код';
  END IF;

  PERFORM set_config('app.locale', 'ru', true);
  IF enum_name('enum_order_type', '1', 'Standard order') IS DISTINCT FROM 'Обычный заказ' THEN
    RAISE EXCEPTION 'нет русского перевода типа заказа 01';
  END IF;
  PERFORM set_config('app.locale', '', true);
END
$check$;
