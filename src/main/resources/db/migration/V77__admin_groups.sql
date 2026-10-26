-- ---------------------------------------------------------------------
-- V77. Группы вкладок администрирования и «Типы поступлений»
-- ---------------------------------------------------------------------
-- Раздел администрирования рос вкладками: доступы менеджеров,
-- поклажедателей, сотрудников склада, две разновидности ошибок,
-- настройка отчётов, тексты интерфейса, организации оператора. Рядом,
-- в «Справочниках», стояли справочники ВСЕГО портала — склады,
-- пользователи, поклажедатели, типы, языки, единицы измерения, — и
-- стояли в одном ряду с товарами и контрагентами выбранного клиента.
-- Ряд из тринадцати подписей никакого порядка уже не держал.
--
-- Вкладки собираются в группы: «Общие справочники», «Доступы»,
-- «Ошибки», и отдельно, как было, «Настройка отчётов» и «Тексты
-- интерфейса». Подписи вкладок внутри групп НЕ меняются: «Доступы →
-- Доступы менеджеров» читается с повтором, зато вкладка называется
-- так же, как называлась, — найти её по памяти проще, чем заново
-- угадать.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.button.shared_directories', 'admin', 'button', NULL,
   'группа вкладок администрирования: справочники всего портала'),
  ('admin.button.accesses', 'admin', 'button', NULL,
   'группа вкладок администрирования: доступы менеджеров, поклажедателей, сотрудников'),
  ('admin.button.errors', 'admin', 'button', NULL,
   'группа вкладок администрирования: ошибки интеграции и загрузки Excel')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('admin.button.shared_directories', 'ru', 'Общие справочники'),
    ('admin.button.shared_directories', 'en', 'Shared directories'),
    ('admin.button.accesses',           'ru', 'Доступы'),
    ('admin.button.accesses',           'en', 'Access'),
    ('admin.button.errors',             'ru', 'Ошибки'),
    ('admin.button.errors',             'en', 'Errors')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- «Типы ожидаемых документов» → «Типы поступлений».
--
-- Ключ оставлен прежним: переименовывать ключ значит переписать его
-- во всех местах разметки и кода, а взамен получить ровно то же.
-- Ключ — это адрес надписи, а не сама надпись; меняется надпись.
UPDATE ui_text t
   SET value = CASE l.code WHEN 'ru' THEN 'Типы поступлений'
                           ELSE 'Receipt types' END
  FROM dictionary_locale l
 WHERE l.locale_id = t.locale_id
   AND l.code IN ('ru', 'en')
   AND t.text_key IN ('references.button.expected_document_types',
                      'references.title.expected_document_types');

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  nameless text;
  stale    text;
BEGIN
  -- Ключ без текста человек увидит как «admin.button.errors».
  SELECT string_agg(k.text_key || ':' || l.code, ', ')
    INTO nameless
    FROM ui_text_key k
    CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE k.text_key IN ('admin.button.shared_directories',
                        'admin.button.accesses', 'admin.button.errors')
     AND l.code IN ('ru', 'en')
     AND (t.value IS NULL OR t.value = '');
  IF nameless IS NOT NULL THEN
    RAISE EXCEPTION 'у групп вкладок нет подписи: %', nameless;
  END IF;

  -- Переименование должно было дойти до обеих надписей на обоих языках:
  -- подпись вкладки и заголовок экрана заведены отдельными ключами,
  -- и разойтись им нельзя — это одна и та же вкладка.
  SELECT string_agg(t.text_key || ':' || l.code || '=' || t.value, ', ')
    INTO stale
    FROM ui_text t
    JOIN dictionary_locale l ON l.locale_id = t.locale_id
   WHERE t.text_key IN ('references.button.expected_document_types',
                        'references.title.expected_document_types')
     AND l.code IN ('ru', 'en')
     AND t.value NOT IN ('Типы поступлений', 'Receipt types');
  IF stale IS NOT NULL THEN
    RAISE EXCEPTION 'типы поступлений переименованы не везде: %', stale;
  END IF;
END
$check$;
