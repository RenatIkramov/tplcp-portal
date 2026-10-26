-- ---------------------------------------------------------------------
-- V69. Подписи панели фильтров и уборка после неё
-- ---------------------------------------------------------------------
-- Панель фильтров списка документов больше не написана в разметке: она
-- строится из справочника колонок, и подпись фильтра — это подпись
-- колонки, вместе с переименованием поклажедателя (V65). Поэтому
-- девятнадцать ключей, которыми подписывались вписанные руками поля,
-- остались ни к чему не привязанными. Оставить их значило бы оставить
-- в «Текстах интерфейса» девятнадцать строк, которые человек может
-- править сколько угодно без всякого следствия на экране.
--
-- Взамен нужны три ключа, которых в каталоге не было:
--   * «с» и «по» — приставки к подписи колонки у диапазона. Одна
--     колонка на панели рисуется двумя полями, и подпись у них
--     складывается: «Дата отгрузки» + «с». Поэтому приставки общие,
--     а не «Дата отгрузки с» отдельным ключом на каждую дату: дат
--     в справочнике девять, и девяти пар подписей не хотелось бы;
--   * «Номер или контрагент» — подпись поля поиска. Поиск идёт сразу
--     по трём номерам документа и по контрагенту, и это его первая
--     честная подпись: раньше поле звалось «Номер документа», а искало
--     заодно по номеру портала, номеру WMS и имени контрагента.
--
-- Что НЕ удаляется. Ключи вариантов статуса (receipts.option.draft и
-- прочие) тоже остались без разметки: выпадающий список статусов
-- теперь берёт значения из enum_status. Но enum_status.name не
-- переводится вовсе — таблица translation пуста, — и это известная
-- дыра, которую закрывать именно этими текстами. Удалить их сейчас
-- значит через месяц завести заново.

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.label.range_from', 'common', 'label', NULL,
   'приставка «с» к подписи колонки у фильтра-диапазона'),
  ('common.label.range_to', 'common', 'label', NULL,
   'приставка «по» к подписи колонки у фильтра-диапазона'),
  ('common.label.number_or_counterparty', 'common', 'label', NULL,
   'подпись поля поиска в панели фильтров: ищет по трём номерам и контрагенту')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('common.label.range_from',           'с',                    'from'),
    ('common.label.range_to',             'по',                   'to'),
    ('common.label.number_or_counterparty',
                                          'Номер или контрагент', 'Number or counterparty')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Переводы уходят каскадом (ui_text.text_key → ui_text_key
-- ON DELETE CASCADE), поэтому удаляется только ключ.
DELETE FROM ui_text_key WHERE text_key IN (
  'receipts.label.document_number',
  'receipts.label.trip',
  'receipts.label.date_from',
  'receipts.label.date_to',
  'transfers.label.document_number',
  'transfers.label.trip',
  'transfers.label.date_from',
  'transfers.label.date_to',
  'common.label.order_number',
  'common.label.ship_date_from',
  'common.label.ship_date_to',
  'common.placeholder.eg_receipt_no',
  'common.placeholder.eg_receipt_wms_no',
  'common.placeholder.eg_supplier',
  'common.placeholder.eg_transfer_no',
  'common.placeholder.eg_transfer_wms_no',
  'common.placeholder.eg_order_no',
  'common.placeholder.eg_order_wms_no',
  'common.placeholder.eg_consignee');

-- ---------------------------------------------------------------------
-- Набор по умолчанию: номер ERP с панели убирается
-- ---------------------------------------------------------------------
-- Он никуда не делся — остался фильтруемой колонкой и появится
-- в ⚙ Фильтрах. Но на панели рядом с полем поиска, которое ищет
-- по тому же номеру ERP и ещё по двум номерам и контрагенту, он был бы
-- вторым полем под то же самое. Два поля, делающих почти одно и то же,
-- человек читает как «где-то здесь подвох».
UPDATE table_column
   SET filter_default = false
 WHERE table_key IN ('receipts', 'orders', 'transfers')
   AND column_id = 'erpNo';

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
-- Панель без единого фильтра по умолчанию — это пустая панель
-- и три кнопки, которым нечего делать. Проверяется здесь же, потому
-- что строка выше как раз убавляет набор.
DO $check$
DECLARE
  bare text;
BEGIN
  SELECT string_agg(t.table_key, ', ')
    INTO bare
    FROM (VALUES ('receipts'), ('orders'), ('transfers')) AS t(table_key)
   WHERE NOT EXISTS (SELECT 1
                       FROM table_column c
                      WHERE c.table_key = t.table_key
                        AND c.filter_kind IS NOT NULL
                        AND c.filter_default);
  IF bare IS NOT NULL THEN
    RAISE EXCEPTION 'нет фильтров по умолчанию: %', bare;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM ui_text
                  WHERE text_key = 'common.label.range_from') THEN
    RAISE EXCEPTION 'подписи «с»/«по» не записались — панель дат выйдет без подписей';
  END IF;
END
$check$;
