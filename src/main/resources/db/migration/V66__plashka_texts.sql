-- =====================================================================
-- V66. Плашки портала — на ключи
-- =====================================================================
-- ЗАЧЕМ. Плашка (notify/say) — единственное, что человек видит после
-- нажатия кнопки: «сохранено», «не найдено», «не удалось». В коде
-- портала эти тексты были написаны по-русски прямо в строках, и при
-- переключении языка на английский они оставались русскими. Выходил
-- худший из возможных случаев: экран английский, а ответ портала на
-- действие — нет, то есть непереведённым остаётся именно то, что
-- человек читает внимательнее всего.
--
-- ПОДСТАНОВКИ. В плашке почти всегда есть имя, номер или текст ошибки,
-- и прежде он приклеивался к строке плюсом: 'Черновик ' + no + ' удалён'.
-- В английском порядок слов другой, и склейкой его не получить. Поэтому
-- все ключи здесь — с подстановками вида {no}, {name}, {error}: порядок
-- слов остаётся за переводом, а не за кодом. Механика подстановки уже
-- есть и на сервере (PortalMessages.substitute), и на клиенте
-- (T('ключ', {no: …}) в i18n-fallback.js).
--
-- ДВА КЛЮЧА ПЕРЕПИСЫВАЮТСЯ, А НЕ ЗАВОДЯТСЯ ЗАНОВО. admin.text.access_updated
-- и admin.text.accessible_warehouses_updated уже есть — в них текст
-- заканчивается двоеточием, потому что имя приклеивалось плюсом в
-- прототипе. Завести рядом второй ключ с тем же смыслом значит оставить
-- переводчику два почти одинаковых текста и гарантировать, что однажды
-- они разойдутся. Поэтому текст меняется на вариант с {name}, а оба места
-- вызова — и прототип, и портал — переводятся на подстановку.
--
-- ЧЕГО ЗДЕСЬ НЕТ. Русский текст в портале остаётся и за пределами плашек:
-- подписи и подсказки, собираемые в разметке (экран доступов, правила
-- пароля, экран языков, подсказки справочников). Это отдельная работа,
-- и она крупнее этой. Здесь закрыты только плашки — то, что портал
-- говорит человеку в ответ на действие.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Новые ключи
-- ---------------------------------------------------------------------
-- Раздел (scope) выбран по тому, где плашка показывается, а не по файлу:
-- «Документ не найден» одинаково нужен поступлению, заказу и перемещению,
-- поэтому common, а не receipts.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.message.save_failed', 'common', 'message', 'portal',
   'плашка: запись не удалась, с текстом ошибки'),
  ('common.message.card_open_failed', 'common', 'message', 'portal',
   'плашка: карточка справочника не открылась'),
  ('common.message.document_not_in_list', 'common', 'message', 'documents',
   'плашка: щёлкнули строку, а документа нет в загруженной странице списка'),
  ('common.message.document_open_failed', 'common', 'message', 'documents',
   'плашка: карточка документа не открылась'),
  ('common.message.document_sent_to_wms', 'common', 'message', 'documents',
   'плашка: документ подтверждён и ушёл на склад'),
  ('common.message.draft_deleted', 'common', 'message', 'documents',
   'плашка: черновик удалён'),
  ('common.message.downloading_file', 'common', 'message', 'portal',
   'плашка: началась выгрузка файла'),
  ('common.message.language_switch_failed', 'common', 'message', 'portal',
   'плашка: язык интерфейса переключить не удалось'),
  ('admin.message.user_warehouses_updated', 'admin', 'message', 'access',
   'плашка: сотруднику изменили список складов'),
  ('admin.message.user_created', 'admin', 'message', 'admin',
   'плашка: пользователь создан'),
  ('admin.message.user_saved', 'admin', 'message', 'admin',
   'плашка: карточка пользователя сохранена'),
  ('admin.message.password_issued', 'admin', 'message', 'admin',
   'плашка с выданным паролем: показывается один раз, записать его можно только сейчас'),
  ('auth.message.depositor_selected', 'auth', 'message', 'auth',
   'плашка: переключились на другого поклажедателя'),
  ('auth.message.depositor_switch_failed', 'auth', 'message', 'auth',
   'плашка: переключить поклажедателя не удалось'),
  ('sku.message.not_in_loaded_list', 'sku', 'message', 'sku',
   'плашка: щёлкнули строку, а товара нет в загруженной странице справочника'),
  ('sku.message.not_in_stock_list', 'sku', 'message', 'stock',
   'плашка: щёлкнули строку запасов, а товара нет в загруженной странице'),
  ('sku.message.batches_failed', 'sku', 'message', 'stock',
   'плашка: партии товара получить не удалось'),
  ('sku.message.new_sku_open_failed', 'sku', 'message', 'sku',
   'плашка: карточка нового товара не открылась'),
  ('error.status.failed_with_code', 'server', 'message', 'common',
   'ответ без текста: показываем код, чтобы его можно было назвать в заявке')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     owner   = excluded.owner,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('common.message.save_failed',
     'Не удалось сохранить: {error}',
     'Could not save: {error}'),
    ('common.message.card_open_failed',
     'Не удалось открыть карточку: {error}',
     'Could not open the card: {error}'),
    ('common.message.document_not_in_list',
     'Документ {no} не найден в загруженном списке',
     'Document {no} is not in the loaded list'),
    ('common.message.document_open_failed',
     'Не удалось открыть документ: {error}',
     'Could not open the document: {error}'),
    ('common.message.document_sent_to_wms',
     'Документ {no} отправлен в WMS',
     'Document {no} has been sent to the WMS'),
    ('common.message.draft_deleted',
     'Черновик {no} удалён',
     'Draft {no} deleted'),
    ('common.message.downloading_file',
     'Скачивается: {name}',
     'Downloading: {name}'),
    ('common.message.language_switch_failed',
     'Не удалось переключить язык: {error}',
     'Could not switch the language: {error}'),
    ('admin.message.user_warehouses_updated',
     'Склады сотрудника обновлены: {name}',
     'Warehouses of the user updated: {name}'),
    ('admin.message.user_created',
     'Пользователь создан: {name}',
     'User created: {name}'),
    ('admin.message.user_saved',
     'Изменения сохранены: {name}',
     'Changes saved: {name}'),
    ('admin.message.password_issued',
     'Пароль: {password} — передайте его пользователю, при первом входе портал попросит сменить',
     'Password: {password} — pass it on to the user; the portal will ask them to change it at the first sign-in'),
    ('auth.message.depositor_selected',
     'Выбран поклажедатель: {name}',
     'Depositor selected: {name}'),
    ('auth.message.depositor_switch_failed',
     'Не удалось переключить: {error}',
     'Could not switch: {error}'),
    ('sku.message.not_in_loaded_list',
     'Товар {code} не найден в загруженном списке',
     'Item {code} is not in the loaded list'),
    ('sku.message.not_in_stock_list',
     'Товар {code} не найден в списке запасов',
     'Item {code} is not in the stock list'),
    ('sku.message.batches_failed',
     'Не удалось получить партии: {error}',
     'Could not load the batches: {error}'),
    ('sku.message.new_sku_open_failed',
     'Не удалось открыть создание товара: {error}',
     'Could not open the new item form: {error}'),
    ('error.status.failed_with_code',
     'Запрос не выполнен (код {code})',
     'The request failed (code {code})')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Два прежних ключа — на подстановку
-- ---------------------------------------------------------------------
-- UPDATE, а не ON CONFLICT DO NOTHING: текст у этих ключей есть, и его
-- надо именно заменить. Условие на отсутствие {name} делает шаг
-- повторяемым: если текст уже с подстановкой, ничего не меняется.
UPDATE ui_text t
   SET value = v.value
  FROM (VALUES
    ('admin.text.access_updated', 'ru', 'Доступы обновлены: {name}'),
    ('admin.text.access_updated', 'en', 'Access updated: {name}'),
    ('admin.text.accessible_warehouses_updated', 'ru',
     'Доступные склады обновлены: {name}'),
    ('admin.text.accessible_warehouses_updated', 'en',
     'Accessible warehouses updated: {name}')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
 WHERE t.text_key = v.text_key
   AND t.locale_id = l.locale_id
   AND t.value NOT LIKE '%{name}%';

-- ---------------------------------------------------------------------
-- 3. Проверка на месте
-- ---------------------------------------------------------------------
-- Ключ без текста портал покажет человеку как есть — строкой
-- «common.message.draft_deleted» вместо плашки. Ловим здесь.
DO $texts$
DECLARE
  missing text;
  stale   text;
BEGIN
  -- Ключи перечислены списком, а не выбраны по note: примета в note —
  -- для человека, и проверке опираться на неё значит однажды пропустить
  -- ключ из-за опечатки в примечании.
  SELECT string_agg(k.text_key || ' (' || l.code || ')', ', ')
    INTO missing
    FROM ui_text_key k
    JOIN dictionary_locale l ON l.code IN ('ru', 'en')
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN (
           'common.message.save_failed', 'common.message.card_open_failed',
           'common.message.document_not_in_list', 'common.message.document_open_failed',
           'common.message.document_sent_to_wms', 'common.message.draft_deleted',
           'common.message.downloading_file', 'common.message.language_switch_failed',
           'admin.message.user_warehouses_updated', 'admin.message.user_created',
           'admin.message.user_saved', 'admin.message.password_issued',
           'auth.message.depositor_selected', 'auth.message.depositor_switch_failed',
           'sku.message.not_in_loaded_list', 'sku.message.not_in_stock_list',
           'sku.message.batches_failed', 'sku.message.new_sku_open_failed',
           'error.status.failed_with_code')
     AND t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для ключей плашек: %', missing;
  END IF;

  -- И обратная ошибка: текст есть, а подстановки в нём нет. Тогда код
  -- передаст номер, а человек его не увидит — плашка выйдет без главного.
  SELECT string_agg(t.text_key || ' (' || l.code || ')', ', ')
    INTO stale
    FROM ui_text t
    JOIN dictionary_locale l ON l.locale_id = t.locale_id
   WHERE t.text_key IN ('admin.text.access_updated',
                        'admin.text.accessible_warehouses_updated')
     AND l.code IN ('ru', 'en')
     AND t.value NOT LIKE '%{name}%';
  IF stale IS NOT NULL THEN
    RAISE EXCEPTION 'Текст остался без подстановки {name}: %', stale;
  END IF;
END $texts$;
