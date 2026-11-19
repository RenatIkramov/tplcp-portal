SET search_path = dbo;

-- =====================================================================
-- V136. Тексты экранов каталога отчётов (V135): «Настройка отчётов»
-- в администрировании и вкладка «Отчёты» карточки поклажедателя
-- (js/portal/report-catalog.js). Демо-настройка прототипа удалена.
-- =====================================================================

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
SELECT v.text_key, 'admin', v.element, 'reports', v.note
  FROM (VALUES
    ('reports.hint.catalog',          'hint',    'что такое каталог отчётов'),
    ('reports.hint.params',           'hint',    'ключ параметра и скрытый параметр'),
    ('reports.hint.depositor',        'hint',    'вкладка «Отчёты» карточки клиента'),
    ('reports.tab.reports',           'tab',     'вкладка карточки поклажедателя'),
    ('reports.title.new',             'title',   'окно нового отчёта'),
    ('reports.title.edit',            'title',   'окно отчёта'),
    ('reports.column.order',          'column',  'порядок'),
    ('reports.column.clients',        'column',  'скольким клиентам выдан'),
    ('reports.column.active',         'column',  'действует'),
    ('reports.column.assigned',       'column',  'выдан клиенту'),
    ('reports.column.custom_name',    'column',  'своё название у клиента'),
    ('reports.column.param_key',      'column',  'ключ параметра для WMS'),
    ('reports.column.param_label',    'column',  'подпись параметра'),
    ('reports.column.param_type',     'column',  'тип параметра'),
    ('reports.column.default',        'placeholder', 'значение по умолчанию'),
    ('reports.label.required',        'label',   'обязательный параметр'),
    ('reports.label.current_date',    'label',   'текущая дата'),
    ('reports.label.hidden',          'label',   'скрыт от пользователя'),
    ('reports.button.add_param',      'button',  'добавить параметр'),
    ('reports.button.remove_param',   'button',  'убрать параметр'),
    ('reports.button.move_up',        'button',  'параметр выше'),
    ('reports.button.move_down',      'button',  'параметр ниже'),
    ('reports.button.delete',         'button',  'удалить отчёт'),
    ('reports.text.delete_confirm',   'confirm', 'вопрос перед удалением'),
    ('reports.text.catalog_empty',    'text',    'каталог пуст'),
    ('reports.text.no_params',        'text',    'параметров нет'),
    ('reports.text.set_by_portal',    'text',    'параметр подставляет портал'),
    ('reports.text.off_in_catalog',   'text',    'отчёт выключен в каталоге'),
    ('reports.message.saved',         'message', 'отчёт сохранён'),
    ('reports.message.deleted',       'message', 'отчёт удалён'),
    ('reports.message.depositor_saved','message','отчёты клиента сохранены')
  ) AS v(text_key, element, note)
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('reports.hint.catalog', 'ru', 'Отчёты формирует WMS: портал отправляет ей код отчёта и параметры. Здесь — общий каталог; клиенту отчёт выдаётся в его карточке, вкладка «Отчёты».'),
    ('reports.hint.catalog', 'en', 'Reports are produced by the WMS: the portal sends it the report code and parameters. This is the shared catalogue; a client gets a report in their card, “Reports” tab.'),
    ('reports.hint.params', 'ru', 'Ключ — имя параметра в запросе к WMS, его не переводят. Скрытый параметр человек не видит — уходит значение по умолчанию.'),
    ('reports.hint.params', 'en', 'The key is the parameter name in the WMS request and is not translated. A hidden parameter is not shown — its default value is sent.'),
    ('reports.hint.depositor', 'ru', 'Отметьте отчёты, доступные клиенту. Своё название — одно на все языки; пусто — название из каталога.'),
    ('reports.hint.depositor', 'en', 'Tick the reports available to the client. The own name is one for all languages; empty — the catalogue name.'),
    ('reports.tab.reports', 'ru', 'Отчёты'),
    ('reports.tab.reports', 'en', 'Reports'),
    ('reports.title.new', 'ru', 'Новый отчёт'),
    ('reports.title.new', 'en', 'New report'),
    ('reports.title.edit', 'ru', 'Отчёт'),
    ('reports.title.edit', 'en', 'Report'),
    ('reports.column.order', 'ru', 'Порядок'),
    ('reports.column.order', 'en', 'Order'),
    ('reports.column.clients', 'ru', 'Клиентов'),
    ('reports.column.clients', 'en', 'Clients'),
    ('reports.column.active', 'ru', 'Действует'),
    ('reports.column.active', 'en', 'Active'),
    ('reports.column.assigned', 'ru', 'Выдан'),
    ('reports.column.assigned', 'en', 'Assigned'),
    ('reports.column.custom_name', 'ru', 'Своё название'),
    ('reports.column.custom_name', 'en', 'Own name'),
    ('reports.column.param_key', 'ru', 'Ключ для WMS'),
    ('reports.column.param_key', 'en', 'WMS key'),
    ('reports.column.param_label', 'ru', 'Подпись'),
    ('reports.column.param_label', 'en', 'Label'),
    ('reports.column.param_type', 'ru', 'Тип'),
    ('reports.column.param_type', 'en', 'Type'),
    ('reports.column.default', 'ru', 'Значение по умолчанию'),
    ('reports.column.default', 'en', 'Default value'),
    ('reports.label.required', 'ru', 'Обязательный'),
    ('reports.label.required', 'en', 'Required'),
    ('reports.label.current_date', 'ru', 'Текущая дата'),
    ('reports.label.current_date', 'en', 'Current date'),
    ('reports.label.hidden', 'ru', 'Скрыт'),
    ('reports.label.hidden', 'en', 'Hidden'),
    ('reports.button.add_param', 'ru', '+ Параметр'),
    ('reports.button.add_param', 'en', '+ Parameter'),
    ('reports.button.remove_param', 'ru', 'Убрать параметр'),
    ('reports.button.remove_param', 'en', 'Remove parameter'),
    ('reports.button.move_up', 'ru', 'Выше'),
    ('reports.button.move_up', 'en', 'Up'),
    ('reports.button.move_down', 'ru', 'Ниже'),
    ('reports.button.move_down', 'en', 'Down'),
    ('reports.button.delete', 'ru', 'Удалить отчёт'),
    ('reports.button.delete', 'en', 'Delete report'),
    ('reports.text.delete_confirm', 'ru', 'Удалить отчёт из каталога? Это нельзя отменить.'),
    ('reports.text.delete_confirm', 'en', 'Delete the report from the catalogue? This cannot be undone.'),
    ('reports.text.catalog_empty', 'ru', 'В каталоге пока нет отчётов'),
    ('reports.text.catalog_empty', 'en', 'There are no reports in the catalogue yet'),
    ('reports.text.no_params', 'ru', 'Параметров нет'),
    ('reports.text.no_params', 'en', 'No parameters'),
    ('reports.text.set_by_portal', 'ru', 'Значение подставляет портал — человек его не видит'),
    ('reports.text.set_by_portal', 'en', 'The portal fills this in — the user does not see it'),
    ('reports.text.off_in_catalog', 'ru', 'выключен в каталоге'),
    ('reports.text.off_in_catalog', 'en', 'switched off in the catalogue'),
    ('reports.message.saved', 'ru', 'Отчёт сохранён'),
    ('reports.message.saved', 'en', 'Report saved'),
    ('reports.message.deleted', 'ru', 'Отчёт удалён'),
    ('reports.message.deleted', 'en', 'Report deleted'),
    ('reports.message.depositor_saved', 'ru', 'Отчёты клиента сохранены'),
    ('reports.message.depositor_saved', 'en', 'Client reports saved')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
