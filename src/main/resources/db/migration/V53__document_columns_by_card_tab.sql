-- =====================================================================
-- V53. Поля документов разложены по вкладкам их карточки
-- =====================================================================
-- У товара каждая колонка знает свою вкладку карточки: V35 завела
-- table_column.group_key и расставила его по восьми вкладкам карточки
-- товара. Благодаря этому настройка видимости полей выглядит так же,
-- как сама карточка, и администратор ищет поле там же, где его видит
-- клиент.
--
-- У документов group_key пустой, и поля валились одним списком.
-- Ставим его по тому же правилу — по вкладкам карточки документа:
--
--   «Общая информация»        — шапка: номера, даты, контрагент, итоги;
--   «Транспортный раздел»     — рейс, перевозчик, водитель, машина;
--   «Дополнительная информация» — доп. поля шапки (V51);
--   «Состав документа»        — строки (receiptComposition и соседи);
--   «Детали строки»           — партии и сроки (receiptLineDetail).
--
-- Разбивка взята из самой карточки (GENERAL_TAB_COLUMNS и
-- TRANSPORT_TAB_COLUMNS в js/prototype.js), а не придумана заново:
-- иначе появился бы третий список полей, который разойдётся с первыми
-- двумя. Ровно по этой причине так сделано и у товара (см. V35).
--
-- Запасы к карточке документа отношения не имеют, но раскладка экрана
-- та же, поэтому их две таблицы тоже получают свои группы.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Шапка документа: общая информация и транспорт
-- ---------------------------------------------------------------------
-- Колонка, не попавшая ни в один список, остаётся в общей информации:
-- это номер, статус, автор, комментарий — то, что карточка показывает
-- в шапке. Пустой group_key оставлять нельзя, иначе поле потеряется
-- между вкладками.
UPDATE table_column
   SET group_key = 'common.title.general_information'
 WHERE table_key IN ('receipts', 'orders', 'transfers');

UPDATE table_column
   SET group_key = 'common.title.transport_section'
 WHERE table_key IN ('receipts', 'orders', 'transfers')
   AND column_id IN ('trip', 'carrier', 'driverName', 'driverInn', 'driverIdNumber',
                     'driverPhone', 'transportType', 'transportBrand', 'carNumber',
                     'trailerNumber', 'techPassportNumber', 'containerNumber',
                     'sealNumber', 'deliveryDate');

UPDATE table_column
   SET group_key = 'common.title.additional_information'
 WHERE table_key IN ('receipts', 'orders', 'transfers')
   AND column_id LIKE 'customField%';

-- ---------------------------------------------------------------------
-- 2. Состав документа и детали строки
-- ---------------------------------------------------------------------
-- У состава подпись своя на каждый тип документа — так она и звучит
-- в карточке: «Состав поступления», «Состав заказа», «Состав
-- перемещения».
UPDATE table_column SET group_key = 'receipts.title.receipt_contents'
 WHERE table_key = 'receiptComposition';
UPDATE table_column SET group_key = 'orders.title.order_contents'
 WHERE table_key = 'orderComposition';
UPDATE table_column SET group_key = 'transfers.title.transfer_contents'
 WHERE table_key = 'transferComposition';

UPDATE table_column SET group_key = 'admin.table.line_details'
 WHERE table_key IN ('receiptLineDetail', 'orderLineDetail', 'transferLineDetail');

-- ---------------------------------------------------------------------
-- 3. Запасы
-- ---------------------------------------------------------------------
UPDATE table_column SET group_key = 'admin.table.stock_lines' WHERE table_key = 'stock';
UPDATE table_column SET group_key = 'admin.table.batches'     WHERE table_key = 'stockBatch';

-- ---------------------------------------------------------------------
-- 4. Проверка: поля без вкладки не остались
-- ---------------------------------------------------------------------
-- Кроме упаковок: их карточка отдельная, и в настройке видимости
-- полей поклажедателя они не участвуют.
DO $$
DECLARE
  v_lost int;
BEGIN
  SELECT count(*) INTO v_lost FROM table_column
   WHERE group_key IS NULL AND table_key <> 'packaging';
  IF v_lost > 0 THEN
    RAISE EXCEPTION 'Колонок без вкладки осталось: %', v_lost;
  END IF;
END $$;
