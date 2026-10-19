-- =====================================================================
-- V67. «Запрос не выполнен (код N)» — текст клиента, а не сервера
-- =====================================================================
-- ИСПРАВЛЯЕТ ОШИБКУ V66. Там завели ключ error.status.failed_with_code
-- с разделом (scope) 'server' — по соседству с error.status.not_found
-- и error.status.conflict, на которые он похож по имени.
--
-- Похож, но устроен наоборот. Раздел 'server' значит не «про ошибки»,
-- а «фразу собирает сервер»: такие ключи приходят в ответе и
-- разворачиваются в PortalMessages. А этот текст собирает КЛИЕНТ —
-- core.js подставляет его, когда ответ пришёл вообще без текста
-- (пустое тело, 502 от прокси). В Java его нет и быть не должно.
--
-- Поймала это проверка ServerMessagesTest.codeAndCatalogueAgreeOnKeys:
-- она сверяет ключи раздела 'server' с ключами, встречающимися в Java,
-- и требует совпадения обоих множеств. Ключ в каталоге без ключа в коде
-- для неё — мусор, который надо убрать миграцией. Она права: иначе
-- в каталоге накапливались бы серверные ключи, которых сервер не
-- посылает, и переводчик правил бы текст, который никто не покажет.
--
-- Поэтому ключ не переносится в другой раздел, а ЗАВОДИТСЯ ЗАНОВО под
-- клиентским именем. Имя тоже имеет значение: корень error. — признак
-- серверного сообщения, по нему tools/gen_i18n_fallback.py решает, что
-- ключу не место в запасном словаре клиента. Клиентскому тексту нужно
-- клиентское имя, а не исключение из правила.
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. Новое имя
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.message.request_failed_with_code', 'common', 'message', 'portal',
   'ответ без текста: показываем код, чтобы его можно было назвать в заявке')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     owner   = excluded.owner,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('common.message.request_failed_with_code',
     'Запрос не выполнен (код {code})',
     'The request failed (code {code})')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Прежнее имя
-- ---------------------------------------------------------------------
-- Тексты уходят сами: ui_text.text_key ссылается на ui_text_key
-- с ON DELETE CASCADE (V26). На колонки и группы полей этот ключ
-- не ссылался, поэтому удаление ничего больше не тянет.
--
-- Удаляем, а не оставляем «на всякий случай»: оставленный ключ — это
-- вторая запись того же текста, и однажды переводчик поправит ту,
-- которую портал не показывает.
DELETE FROM ui_text_key WHERE text_key = 'error.status.failed_with_code';

-- ---------------------------------------------------------------------
-- 3. Проверка на месте
-- ---------------------------------------------------------------------
DO $texts$
DECLARE
  missing text;
  leftover integer;
BEGIN
  SELECT string_agg(l.code, ', ')
    INTO missing
    FROM dictionary_locale l
    LEFT JOIN ui_text t ON t.locale_id = l.locale_id
                       AND t.text_key = 'common.message.request_failed_with_code'
   WHERE l.code IN ('ru', 'en')
     AND t.text_key IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Нет текста для common.message.request_failed_with_code: %', missing;
  END IF;

  -- Подстановка обязана остаться: без неё человек увидит «Запрос
  -- не выполнен» без кода, то есть без единственного, что он мог бы
  -- назвать в заявке.
  SELECT count(*)
    INTO leftover
    FROM ui_text t
    JOIN dictionary_locale l ON l.locale_id = t.locale_id
   WHERE t.text_key = 'common.message.request_failed_with_code'
     AND l.code IN ('ru', 'en')
     AND t.value NOT LIKE '%{code}%';
  IF leftover > 0 THEN
    RAISE EXCEPTION 'Текст остался без подстановки {code}';
  END IF;

  SELECT count(*) INTO leftover
    FROM ui_text_key WHERE text_key = 'error.status.failed_with_code';
  IF leftover > 0 THEN
    RAISE EXCEPTION 'Прежний ключ error.status.failed_with_code не удалён';
  END IF;
END $texts$;
