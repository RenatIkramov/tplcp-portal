SET search_path = dbo;

-- =====================================================================
-- V140. Экраны отчётов клиента (js/portal/reports.js; решения заказчика
-- 14.11.2026) и правки вокруг них.
--
--   * Вкладка «Фотографии» карточки документа → «Прикреплённые файлы»:
--     туда из WMS приходят и печатные формы (кнопки «Печать» больше нет).
--   * Тексты раздела «Отчёты», блока «Биллинга» и вкладки «Отчёты»
--     карточки товара: таблица готовых отчётов, статусы, «Скачать».
--   * Удалены тексты, которые остались без экрана: «Печать» в трёх
--     карточках документов, «История движения» в запасах, окна демо
--     (параметры отчёта, печать, настройка отчётов прототипа).
-- =====================================================================

UPDATE ui_text SET value = v.value
  FROM (VALUES
    ('common.title.photos', 'ru', 'Прикреплённые файлы'),
    ('common.title.photos', 'en', 'Attached files'),
    ('common.text.no_document_photos', 'ru', 'Прикреплённых файлов по документу пока нет.'),
    ('common.text.no_document_photos', 'en', 'There are no files attached to the document yet.')
  ) AS v(text_key, code, value), dictionary_locale l
 WHERE l.code = v.code AND ui_text.locale_id = l.locale_id AND ui_text.text_key = v.text_key;

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.text_key, 'common', v.element, 'reports', v.note
  FROM (VALUES
    ('reports.button.producing',     'button', 'кнопка «Сформировать», пока отчёт формируется'),
    ('reports.button.download',      'button', 'скачать готовый отчёт'),
    ('reports.title.finished',       'title',  'таблица запусков'),
    ('reports.hint.finished',        'hint',   'пояснение к таблице запусков'),
    ('reports.hint.parameters',      'hint',   'окно параметров отчёта'),
    ('reports.column.requested_by',  'column', 'кто запустил'),
    ('reports.column.requested_at',  'column', 'когда запустил'),
    ('reports.column.status',        'column', 'статус запуска'),
    ('reports.text.no_reports',      'text',   'отчётов здесь нет'),
    ('reports.text.no_runs',         'text',   'запусков ещё не было'),
    ('reports.message.started',      'message','отчёт поставлен в работу')
  ) AS v(text_key, element, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('reports.button.producing', 'ru', 'Формируется…'),
    ('reports.button.producing', 'en', 'In progress…'),
    ('reports.button.download', 'ru', 'Скачать'),
    ('reports.button.download', 'en', 'Download'),
    ('reports.title.finished', 'ru', 'Сформированные отчёты'),
    ('reports.title.finished', 'en', 'Produced reports'),
    ('reports.hint.finished', 'ru', 'Отчёт готовит склад, это может занять несколько минут — таблица обновляется сама. Готовые отчёты хранятся ограниченное время.'),
    ('reports.hint.finished', 'en', 'The warehouse prepares the report, which may take a few minutes — the table refreshes by itself. Finished reports are kept for a limited time.'),
    ('reports.hint.parameters', 'ru', 'Укажите значения для формирования отчёта'),
    ('reports.hint.parameters', 'en', 'Enter the values for the report'),
    ('reports.column.requested_by', 'ru', 'Кто запустил'),
    ('reports.column.requested_by', 'en', 'Requested by'),
    ('reports.column.requested_at', 'ru', 'Запущен'),
    ('reports.column.requested_at', 'en', 'Requested'),
    ('reports.column.status', 'ru', 'Статус'),
    ('reports.column.status', 'en', 'Status'),
    ('reports.text.no_reports', 'ru', 'Отчётов здесь пока нет'),
    ('reports.text.no_reports', 'en', 'No reports here yet'),
    ('reports.text.no_runs', 'ru', 'Отчёты ещё не формировались'),
    ('reports.text.no_runs', 'en', 'No reports have been produced yet'),
    ('reports.message.started', 'ru', 'Отчёт отправлен на склад — он появится в таблице, когда будет готов'),
    ('reports.message.started', 'en', 'The report request was sent to the warehouse — it will appear in the table when ready')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- Тексты без экрана
DELETE FROM ui_text WHERE text_key IN (
  'receipts.button.print', 'transfers.button.print', 'orders.button.print',
  'stock.button.movement_history', 'stock.message.movement_history_later',
  'common.title.report_parameters', 'common.column.report_act',
  'common.title.add_report', 'common.title.delete_report',
  'common.label.sequence_number', 'common.label.location');
DELETE FROM ui_text_key WHERE text_key IN (
  'receipts.button.print', 'transfers.button.print', 'orders.button.print',
  'stock.button.movement_history', 'stock.message.movement_history_later',
  'common.title.report_parameters', 'common.column.report_act',
  'common.title.add_report', 'common.title.delete_report',
  'common.label.sequence_number', 'common.label.location');
