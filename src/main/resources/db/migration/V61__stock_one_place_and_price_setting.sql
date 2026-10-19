-- =====================================================================
-- V61. Остатки — в одном месте; цена в окне строки — настройкой клиента
-- =====================================================================
-- Две независимые вещи в одной миграции, потому что обе понадобились
-- окну ввода строки документа (§8.2.2.2–8.2.2.3).
--
-- ЧАСТЬ 1. «Доступно» и «Ожидается» считались ДВАЖДЫ.
--
-- План (§8.3.2) требует одного места. На деле подзапрос «ожидается»
-- был скопирован в v_stock_available и в v_stock_lines, причём
-- «доступно» в них считалось по-разному: одно представление
-- суммировало колонку available_qty, пришедшую из WMS, другое
-- выводило её само из качества и признака блокировки. На сегодняшних
-- данных числа совпадают, но разойтись могли в любой момент — и никто
-- бы не заметил, какое из двух правильное.
--
-- Теперь v_stock_available — единственное место, где это считается,
-- а v_stock_lines строится поверх него и только добавляет имена.
--
-- «Доступно» берётся из available_qty, то есть от WMS. Портал владеет
-- копией остатков, а не оригиналом (решение Р4), и выводить доступность
-- своей формулой значит спорить с системой, которая её и считает.
-- Если однажды WMS пришлёт доступное количество по заблокированной
-- партии — это её ошибка, и увидеть её лучше, чем прикрыть.
--
-- ЧАСТЬ 2. Товар с нулевым остатком и ожидаемым поступлением был невидим.
--
-- Оба представления строились ОТ таблицы остатков: нет строки
-- в stock — нет строки вовсе. А «ожидается 200» по товару, которого
-- на складе пока нет, — как раз тот случай, когда число важнее всего:
-- человек заводит заказ и должен видеть, что товар едет.
--
-- Поэтому остатки и ожидаемые поступления сводятся полным соединением:
-- пара «товар + склад» попадает в выборку, если есть хотя бы одно
-- из двух. Количества у такой строки — нули, а не NULL: ноль на складе
-- это знание, а не неизвестность.
--
-- ЧАСТЬ 3. Цена и сумма в окне строки — настройка поклажедателя.
--
-- Большинству клиентов цена в документе не нужна: товар не меняет
-- владельца, портал ведёт склад, а не торговлю. Поле просят убрать,
-- и настройка нужна отдельно по типу документа — у одного клиента
-- цена осмысленна в поступлении (приход с закупки) и бессмысленна
-- в заказе. У перемещения цены нет вовсе и быть не может, поэтому
-- флага для него нет: это не настройка, а свойство документа.
--
-- По умолчанию ВЫКЛЮЧЕНО — так просил заказчик: «в большинстве случаев
-- оно будет не нужным». Клиентам, которым цена нужна, галочку ставит
-- администратор в карточке клиента.
--
-- Это удобство, а не право: сервер по-прежнему принимает цену в строке.
-- Скрытое поле не защита (§6.3 про то же самое), и выключенная галочка
-- не должна молча стирать цены, уже введённые раньше.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. v_stock_available — единственное место расчёта
-- ---------------------------------------------------------------------
-- Представления пересоздаются, а не заменяются: CREATE OR REPLACE VIEW
-- умеет только дописывать колонки в конец, а здесь меняется и состав,
-- и порядок. Пересоздание сбрасывает security_invoker, без которого RLS
-- перестанет действовать от имени вызывающего, — поэтому он выставлен
-- явно у каждого представления. Забыть его здесь означает открыть
-- остатки всех клиентов каждому.
DROP VIEW IF EXISTS v_stock_lines;
DROP VIEW IF EXISTS v_stock_available;

CREATE VIEW v_stock_available WITH (security_invoker = true) AS
WITH on_hand AS (
  SELECT depositor_id,
         sku_id,
         warehouse_id,
         max(unit_id)                         AS unit_id,
         sum(quantity)                        AS quantity,
         sum(reserved_qty)                    AS reserved_qty,
         sum(blocked_qty)                     AS blocked_qty,
         sum(available_qty)                   AS available_qty,
         sum(CASE WHEN quality = 'Брак' THEN quantity ELSE 0 END) AS defect_qty,
         count(*)                             AS batch_count,
         max(synced_at)                       AS synced_at
    FROM stock
   GROUP BY depositor_id, sku_id, warehouse_id
),
-- Ожидаемое количество — только по плановым записям строк
-- (batch_number IS NULL): фактические количества из WMS лежат
-- на записях партий, и сложить их вместе значило бы посчитать дважды
-- (решение Р1).
--
-- Черновики не считаются: документа ещё нет для склада. Принятые —
-- тоже: то, что принято, уже лежит в остатках.
incoming AS (
  SELECT r.depositor_id,
         rd.sku_id,
         r.warehouse_id,
         sum(rd.expected_qty) AS incoming_qty
    FROM receipt_details rd
    JOIN receipts r      ON r.doc_id = rd.doc_id
    JOIN enum_status es  ON es.id = r.status_id
   WHERE rd.batch_number IS NULL
     AND es.code NOT IN ('draft', 'received')
   GROUP BY r.depositor_id, rd.sku_id, r.warehouse_id
)
SELECT COALESCE(h.depositor_id, i.depositor_id)   AS depositor_id,
       COALESCE(h.sku_id, i.sku_id)               AS sku_id,
       COALESCE(h.warehouse_id, i.warehouse_id)   AS warehouse_id,
       h.unit_id                                  AS unit_id,
       COALESCE(h.quantity, 0)                    AS quantity,
       COALESCE(h.reserved_qty, 0)                AS reserved_qty,
       COALESCE(h.blocked_qty, 0)                 AS blocked_qty,
       COALESCE(h.available_qty, 0)               AS available_qty,
       COALESCE(h.defect_qty, 0)                  AS defect_qty,
       COALESCE(i.incoming_qty, 0)                AS incoming_qty,
       COALESCE(h.batch_count, 0)                 AS batch_count,
       h.synced_at                                AS synced_at
  FROM on_hand h
  FULL JOIN incoming i
         ON i.depositor_id = h.depositor_id
        AND i.sku_id = h.sku_id
        AND i.warehouse_id = h.warehouse_id;

COMMENT ON VIEW v_stock_available IS
  'Количества по паре «товар + склад»: остаток, резерв, блокировка, доступно, брак, ожидается. Единственное место расчёта (§8.3.2). Строка есть, если есть остаток ИЛИ ожидаемое поступление.';

-- ---------------------------------------------------------------------
-- 2. v_stock_lines — то же плюс имена
-- ---------------------------------------------------------------------
-- Состав и порядок колонок прежние: их читает и StockLine в Java,
-- и справочник колонок экрана «Запасы».
--
-- Единица измерения берётся со строки остатка, а если остатка нет —
-- из карточки товара. Иначе у товара, который только едет, единица
-- оказалась бы пустой, и «Ожидается 200» читалось бы без указания чего.
CREATE VIEW v_stock_lines WITH (security_invoker = true) AS
SELECT a.depositor_id,
       a.sku_id,
       s.code          AS sku_code,
       s.name          AS sku_name,
       s.item_group    AS item_group_name,
       a.warehouse_id,
       w.name          AS warehouse_name,
       u.symbol        AS unit_name,
       a.quantity,
       a.reserved_qty,
       a.blocked_qty,
       a.available_qty,
       a.defect_qty,
       a.incoming_qty,
       a.batch_count,
       a.synced_at
  FROM v_stock_available a
  LEFT JOIN sku s        ON s.sku_id = a.sku_id
  LEFT JOIN warehouse w  ON w.warehouse_id = a.warehouse_id
  LEFT JOIN enum_uom u   ON u.id = COALESCE(a.unit_id, s.uom_id);

COMMENT ON VIEW v_stock_lines IS
  'Экран «Запасы»: количества из v_stock_available плюс наименования товара, склада и единицы измерения.';

-- ---------------------------------------------------------------------
-- 3. Настройка «показывать цену» у поклажедателя
-- ---------------------------------------------------------------------
ALTER TABLE depositor_settings
  ADD COLUMN price_in_receipts boolean NOT NULL DEFAULT false,
  ADD COLUMN price_in_orders   boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN depositor_settings.price_in_receipts IS
  'Показывать цену и сумму в окне ввода строки поступления';
COMMENT ON COLUMN depositor_settings.price_in_orders IS
  'Показывать цену и сумму в окне ввода строки заказа';

-- Функция меняет состав возвращаемых колонок, а это не замена,
-- а другая функция — CREATE OR REPLACE такого не умеет.
DROP FUNCTION IF EXISTS depositor_settings_of(uuid);

CREATE FUNCTION depositor_settings_of(p_depositor_id uuid)
RETURNS TABLE (hide_transfers_tab boolean,
               price_in_receipts  boolean,
               price_in_orders    boolean)
LANGUAGE sql
SECURITY DEFINER
SET search_path = dbo
AS $settings$
  SELECT COALESCE(s.hide_transfers_tab, false),
         COALESCE(s.price_in_receipts,  false),
         COALESCE(s.price_in_orders,    false)
    FROM depositor d
    LEFT JOIN depositor_settings s ON s.depositor_id = d.depositor_id
   WHERE d.depositor_id = p_depositor_id;
$settings$;

-- Старая функция с двумя аргументами удаляется: у неё остался бы
-- один вызов — и он молча сбрасывал бы новые галочки в false.
-- Пусть лучше сборка упадёт сейчас, чем настройка потеряется потом.
DROP FUNCTION IF EXISTS admin_save_depositor_settings(uuid, boolean);

CREATE FUNCTION admin_save_depositor_settings(p_depositor_id      uuid,
                                              p_hide_transfers_tab boolean,
                                              p_price_in_receipts  boolean,
                                              p_price_in_orders    boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $save$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM depositor WHERE depositor_id = p_depositor_id) THEN
    RAISE EXCEPTION 'Поклажедателя % нет', p_depositor_id;
  END IF;

  INSERT INTO depositor_settings (depositor_id, hide_transfers_tab,
                                  price_in_receipts, price_in_orders)
  VALUES (p_depositor_id,
          COALESCE(p_hide_transfers_tab, false),
          COALESCE(p_price_in_receipts,  false),
          COALESCE(p_price_in_orders,    false))
  ON CONFLICT (depositor_id) DO UPDATE SET
    hide_transfers_tab = excluded.hide_transfers_tab,
    price_in_receipts  = excluded.price_in_receipts,
    price_in_orders    = excluded.price_in_orders;
END;
$save$;

GRANT EXECUTE ON FUNCTION depositor_settings_of(uuid) TO portal_app;
GRANT EXECUTE ON FUNCTION admin_save_depositor_settings(uuid, boolean, boolean, boolean)
  TO portal_app;

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
-- Только подписи двух галочек и пояснение к ним. Подпись «К получению»
-- для окна строки заказа не заводится: такой ключ уже есть —
-- col.stock.incoming на экране «Запасы». Одно и то же число должно
-- называться одинаково в обоих местах, иначе человек решит,
-- что это разные числа.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.label.price_in_receipts', 'admin', 'label', 'admin',
   'галочка в карточке клиента: цена и сумма в окне строки поступления'),
  ('admin.label.price_in_orders', 'admin', 'label', 'admin',
   'галочка в карточке клиента: цена и сумма в окне строки заказа'),
  ('admin.hint.price_in_documents', 'admin', 'hint', 'admin',
   'пояснение к галочкам цены в карточке клиента'),
  ('admin.title.line_entry_window', 'admin', 'title', 'admin',
   'заголовок раздела настроек окна ввода строки в карточке клиента'),
  ('common.message.over_available_stock', 'common', 'message', 'common',
   'предупреждение при сохранении строки: указано больше, чем доступно')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     owner   = excluded.owner,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('admin.label.price_in_receipts',
     'Цена и сумма в строке поступления',
     'Price and amount in receipt lines'),
    ('admin.label.price_in_orders',
     'Цена и сумма в строке заказа',
     'Price and amount in order lines'),
    ('admin.hint.price_in_documents',
     'Если галочка снята, в окне ввода строки нет полей цены и суммы. Уже введённые цены сохраняются. У перемещения цены нет вовсе: товар не меняет владельца.',
     'With the box cleared, the line entry window shows no price or amount fields. Prices entered earlier are kept. Transfers have no price at all: the goods do not change owner.'),
    ('admin.title.line_entry_window',
     'Окно ввода строки',
     'Line entry window'),
    -- Предупреждение, а не отказ: портал владеет копией остатков,
    -- а не оригиналом (решение Р4). Поэтому последняя фраза не лишняя —
    -- она объясняет, почему портал не запретил.
    ('common.message.over_available_stock',
     'Указано {count}, а доступно по данным портала {available}. Фактическую доступность подтвердит WMS.',
     'You entered {count}, while the portal shows {available} available. The WMS will confirm the actual availability.')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Ключ без текста портал покажет человеку как есть — проверяем здесь же.
DO $texts$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(k.text_key || ' (' || l.code || ')', ', ')
    INTO missing
    FROM ui_text_key k
    JOIN dictionary_locale l ON l.code IN ('ru', 'en')
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('admin.label.price_in_receipts',
                        'admin.label.price_in_orders',
                        'admin.hint.price_in_documents',
                        'admin.title.line_entry_window',
                        'common.message.over_available_stock')
     AND t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для ключей карточки клиента: %', missing;
  END IF;
END $texts$;
