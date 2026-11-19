-- ---------------------------------------------------------------------
-- V94. Склад в шапке портала — из справочника, общий фильтр списков
-- ---------------------------------------------------------------------
-- Решение заказчика от 30.10.2026. Список складов в шапке был заглушкой
-- прототипа: два склада вписаны в код, со справочником не связаны,
-- выбор ничего не отбирал. Теперь список — склады поклажедателя из
-- справочника, а выбор отбирает поступления, заказы, перемещения
-- (отправитель или получатель) и запасы на сервере — параметром
-- warehouse (ListKind.warehouseColumns). Схема для этого не меняется;
-- здесь — заголовок выпадающего списка, который прототип писал
-- по-русски прямо в коде.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.title.warehouse_filter', 'common', 'title', NULL,
   'шапка портала: заголовок списка складов (V94)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.title.warehouse_filter', 'ru', 'Фильтр по складу'),
    ('common.title.warehouse_filter', 'en', 'Filter by warehouse')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
