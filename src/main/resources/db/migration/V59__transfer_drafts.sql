-- =====================================================================
-- V59. Черновики перемещений: два текста
-- =====================================================================
-- Перемещения сервер умел с самого начала — тип документа заведён
-- наравне с поступлениями и заказами: свои представления, свои статусы
-- (черновик → отправлен в WMS → формируется → в пути → принято),
-- правка шапки ограничена складом-отправителем, складом-получателем
-- и датой отгрузки, строка состава несёт товар, количество и
-- комментарий. Цены у перемещения нет вовсе: товар не меняет владельца,
-- меняется только место хранения.
--
-- Не было экрана: создание и правку черновика рисовал прототип
-- демо-данными из памяти. Теперь этим занимается портал, и ему нужны
-- два текста, которых в каталоге не оказалось.
--
-- СКЛАДЫ СОВПАДАЮТ — причина, а не отказ при вводе. Человек заполняет
-- шапку в любом порядке, и запрещать сохранение на полпути нельзя:
-- он только что выбрал склад-получатель и ещё не дошёл до отправителя.
-- Поэтому совпадение складов встаёт в тот же список причин, что
-- «нет ни одной строки», — документ просто нельзя подтвердить, пока
-- оно не исправлено.
SET search_path = dbo;

INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('error.ready.same_warehouse', 'server', 'message', 'documents',
   'причина: склад-отправитель и склад-получатель совпадают'),
  ('sku.text.transfer_changes_saved', 'sku', 'text', NULL,
   'подтверждение после сохранения черновика перемещения')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
        ('error.ready.same_warehouse',
         'склад-отправитель и склад-получатель совпадают',
         'the source and the destination warehouse are the same'),
        ('sku.text.transfer_changes_saved',
         'Изменения по перемещению сохранены',
         'Transfer changes saved')
       ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Ключ без текста портал покажет человеку как есть — проверяем здесь же.
DO $$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(k.text_key || ' (' || l.code || ')', ', ' ORDER BY k.text_key)
    INTO missing
    FROM ui_text_key k
    JOIN dictionary_locale l ON l.code IN ('ru', 'en')
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('error.ready.same_warehouse', 'sku.text.transfer_changes_saved')
     AND t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для ключей перемещений: %', missing;
  END IF;
END $$;
