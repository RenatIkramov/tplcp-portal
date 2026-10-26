-- ---------------------------------------------------------------------
-- V70. ⚙ Фильтры: человек сам выбирает, какие фильтры ему видны
-- ---------------------------------------------------------------------
-- Шаг 2 замысла из «Фильтров в документах». Фильтруемых колонок около
-- тридцати на экран; на панели рисуется набор по умолчанию, а остальное
-- человек добавляет себе сам — как с колонками.
--
-- Устроено ровно как выбор колонок (V29), и это не лень: человек уже
-- умеет пользоваться окном ⚙ Колонки, и второе окно с другими
-- правилами — повод каждый раз вспоминать, какое из них как себя ведёт.
--
-- Заодно:
--   * номер в ERP возвращается на панель. Объединённое поле «Номер или
--     контрагент» заказчику не понравилось, и правильно: одно поле,
--     которое ищет по четырём разным вещам, невозможно объяснить
--     подписью. Вместо него отдельные поля, как и было;
--   * подпись того поля удаляется — она больше ни к чему не привязана.

-- ---------------------------------------------------------------------
-- 1. Выбор пользователя
-- ---------------------------------------------------------------------
-- Выбор фильтров НЕЗАВИСИМ от выбора колонок, и отдельная таблица нужна
-- именно поэтому. Отбирать по колонке, которую не показываешь, —
-- обычное дело: «покажи мне мои заказы за октябрь» не требует колонки
-- «дата» на экране, достаточно знать, что все строки за октябрь.
CREATE TABLE IF NOT EXISTS user_table_filter (
  user_id     uuid        NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
  table_key   varchar(40) NOT NULL,
  column_id   varchar(40) NOT NULL,
  sort_order  smallint    NOT NULL,
  PRIMARY KEY (user_id, table_key, column_id),
  FOREIGN KEY (table_key, column_id)
      REFERENCES table_column (table_key, column_id) ON DELETE CASCADE
);

COMMENT ON TABLE user_table_filter IS
  'Фильтры, выбранные пользователем. Наличие строки означает, что фильтр на панели';

-- ---------------------------------------------------------------------
-- 2. Запись и сброс выбора
-- ---------------------------------------------------------------------
-- Порядок фильтров — порядок массива: человек перетаскивает строки
-- в окне, и панель должна выйти в том же порядке.
DROP FUNCTION IF EXISTS user_filters_save(uuid, text, text[]);
CREATE FUNCTION user_filters_save(p_user_id uuid, p_table_key text,
                                  p_columns text[])
RETURNS void
LANGUAGE sql
SET search_path = dbo
AS $$
  WITH wipe AS (
    DELETE FROM user_table_filter
    WHERE user_id = p_user_id AND table_key = p_table_key
    RETURNING 1
  )
  INSERT INTO user_table_filter (user_id, table_key, column_id, sort_order)
  SELECT p_user_id, p_table_key, x.column_id, x.ord::smallint
  FROM unnest(p_columns) WITH ORDINALITY AS x(column_id, ord)
  WHERE EXISTS (SELECT 1 FROM wipe) OR true;
$$;

GRANT EXECUTE ON FUNCTION user_filters_save(uuid, text, text[]) TO portal_app;

COMMENT ON FUNCTION user_filters_save(uuid, text, text[]) IS
  'Выбор фильтров пользователя. Пустой массив не записывается: отсутствие строк значит «не настраивал»';

DROP FUNCTION IF EXISTS user_filters_reset(uuid, text);
CREATE FUNCTION user_filters_reset(p_user_id uuid, p_table_key text)
RETURNS void
LANGUAGE sql
SET search_path = dbo
AS $$
  DELETE FROM user_table_filter
  WHERE user_id = p_user_id
    AND (p_table_key IS NULL OR table_key = p_table_key);
$$;

GRANT EXECUTE ON FUNCTION user_filters_reset(uuid, text) TO portal_app;

COMMENT ON FUNCTION user_filters_reset(uuid, text) IS
  'Сброс выбора фильтров: без таблицы — по всем таблицам сразу';

-- Копирование настроек с другого пользователя (V29) переносит удобства
-- работы, и выбор фильтров — ровно такое удобство. Не добавить его сюда
-- значило бы: колонки у нового сотрудника как у наставника, а фильтры
-- почему-то нет.
CREATE OR REPLACE FUNCTION user_filters_copy(p_from uuid, p_to uuid)
RETURNS void
LANGUAGE sql
SET search_path = dbo
AS $$
  WITH wipe AS (
    DELETE FROM user_table_filter WHERE user_id = p_to RETURNING 1
  )
  INSERT INTO user_table_filter (user_id, table_key, column_id, sort_order)
  SELECT p_to, table_key, column_id, sort_order
    FROM user_table_filter
   WHERE user_id = p_from
     AND (EXISTS (SELECT 1 FROM wipe) OR true);
$$;

GRANT EXECUTE ON FUNCTION user_filters_copy(uuid, uuid) TO portal_app;

-- ---------------------------------------------------------------------
-- 3. Номер в ERP снова на панели
-- ---------------------------------------------------------------------
UPDATE table_column
   SET filter_default = true
 WHERE table_key IN ('receipts', 'orders', 'transfers')
   AND column_id = 'erpNo';

-- ---------------------------------------------------------------------
-- 4. Справочник фильтров учитывает выбор человека
-- ---------------------------------------------------------------------
-- Возвращаются ВСЕ фильтруемые колонки, а не только выбранные: из этого
-- же списка строится окно ⚙ Фильтры, и второй запрос за полным списком
-- был бы лишним. Что на панели — говорит is_chosen.
--
-- Правило выбора: есть личная строка — она и решает; нет ни одной
-- личной строки по этой таблице — действует набор по умолчанию.
-- Проверяется наличие строк ПО ТАБЛИЦЕ, а не по колонке, иначе снятую
-- галочку нельзя было бы отличить от «человек ничего не настраивал».
--
-- Из этого следует, что пустой выбор записать нельзя: ноль строк
-- означает «не настраивал», и отличить его от «настроил на пустую
-- панель» нечем. Поначалу так и было задумано — панель без фильтров
-- как осознанный выбор, — но вышло бы хуже задуманного: человек снимает
-- все галочки, нажимает «Применить» и видит набор по умолчанию, то есть
-- окно делает ровно обратное. Поэтому хотя бы один фильтр обязателен
-- (DocumentFilters.saveMine), как и хотя бы одна колонка (V29): панель
-- без полей — это три кнопки, которым нечего делать.
-- DROP, а не CREATE OR REPLACE: у функции меняется набор возвращаемых
-- колонок, а его Postgres заменить на месте не даёт.
DROP FUNCTION IF EXISTS table_filters(uuid, text, text);
CREATE FUNCTION table_filters(p_user_id uuid,
                                         p_locale   text,
                                         p_table_key text DEFAULT NULL)
RETURNS TABLE (table_key      varchar,
               column_id      varchar,
               label          text,
               filter_kind    varchar,
               filter_column  varchar,
               filter_enum    varchar,
               object_type_id smallint,
               is_default     boolean,
               is_chosen      boolean,
               sort_order     smallint)
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  WITH listed AS (
    SELECT tc.table_key,
           tc.column_id,
           tc.label,
           c.filter_kind,
           c.filter_column,
           c.filter_enum,
           c.object_type_id,
           c.filter_default,
           tc.sort_order
      FROM table_columns(p_user_id, p_locale, p_table_key) tc
      JOIN table_column c
        ON c.table_key = tc.table_key AND c.column_id = tc.column_id
     WHERE c.filter_kind IS NOT NULL
  ),
  tuned AS (
    SELECT DISTINCT user_table_filter.table_key
      FROM user_table_filter
     WHERE user_id = p_user_id
  )
  SELECT l.table_key,
         l.column_id,
         l.label,
         l.filter_kind,
         l.filter_column,
         l.filter_enum,
         l.object_type_id,
         l.filter_default,
         CASE WHEN l.table_key IN (SELECT table_key FROM tuned)
              THEN u.column_id IS NOT NULL
              ELSE l.filter_default
         END,
         COALESCE(u.sort_order, l.sort_order)
    FROM listed l
    LEFT JOIN user_table_filter u
           ON u.user_id = p_user_id
          AND u.table_key = l.table_key
          AND u.column_id = l.column_id
   ORDER BY l.table_key, COALESCE(u.sort_order, l.sort_order), l.column_id;
$$;

GRANT EXECUTE ON FUNCTION table_filters(uuid, text, text) TO portal_app;

COMMENT ON FUNCTION table_filters(uuid, text, text) IS
  'Фильтры списка для пользователя: подпись, вид, колонка базы, свой он или по умолчанию';

-- ---------------------------------------------------------------------
-- 5. Тексты окна ⚙ Фильтры
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('common.button.filters', 'common', 'button', NULL,
   'кнопка открытия окна выбора фильтров, рядом с «⚙ Колонки»'),
  ('common.title.filter_settings', 'common', 'title', NULL,
   'заголовок окна выбора фильтров'),
  ('common.hint.filter_settings', 'common', 'hint', NULL,
   'подсказка в окне выбора фильтров'),
  ('common.message.filter_settings_applied', 'common', 'message', NULL,
   'плашка после применения выбора фильтров'),
  ('common.message.filters_shown_but_not_saved', 'common', 'message', NULL,
   'панель перестроена, но выбор не записался: сервер отказал'),
  ('common.message.filters_reset_to_default', 'common', 'message', NULL,
   'плашка после сброса выбора фильтров'),
  ('error.filter.at_least_one', 'server', 'message', 'documents',
   'в окне ⚙ Фильтры снято всё: панель без полей — это три кнопки, которым нечего делать')
ON CONFLICT (text_key) DO UPDATE SET scope   = excluded.scope,
                                     element = excluded.element,
                                     note    = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, CASE l.code WHEN 'ru' THEN v.ru ELSE v.en END
  FROM (VALUES
    ('common.button.filters',
     '⚙ Фильтры',
     '⚙ Filters'),
    ('common.title.filter_settings',
     'Настройка фильтров',
     'Filter settings'),
    ('common.hint.filter_settings',
     'Отметьте нужные фильтры и перетащите для изменения порядка на панели',
     'Tick the filters you need and drag to change their order on the panel'),
    ('common.message.filter_settings_applied',
     'Фильтры настроены',
     'Filters applied'),
    ('common.message.filters_shown_but_not_saved',
     'Фильтры показаны, но выбор не сохранён',
     'Filters are shown but the choice was not saved'),
    ('common.message.filters_reset_to_default',
     'Фильтры вернулись к настройкам по умолчанию',
     'Filters are back to the default set'),
    ('error.filter.at_least_one',
     'Оставьте хотя бы один фильтр',
     'Keep at least one filter')
  ) AS v(text_key, ru, en)
  JOIN dictionary_locale l ON l.code IN ('ru', 'en')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Подпись объединённого поля поиска. Поля больше нет — вместе с ним
-- уходит и параметр search на сервере.
DELETE FROM ui_text_key WHERE text_key = 'common.label.number_or_counterparty';

-- ---------------------------------------------------------------------
-- 6. Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  sample uuid;
  chosen int;
  deflt  int;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM table_column
                  WHERE table_key = 'receipts' AND column_id = 'erpNo'
                    AND filter_default) THEN
    RAISE EXCEPTION 'номер в ERP не вернулся на панель поступлений';
  END IF;

  SELECT user_id INTO sample FROM app_user ORDER BY login LIMIT 1;
  IF sample IS NULL THEN
    RETURN;  -- пустая база: проверять выбор не на ком
  END IF;

  -- Человек ничего не настраивал: выбранное обязано совпасть
  -- с набором по умолчанию. Разойдись оно здесь — панель открылась бы
  -- пустой у каждого, кто окна ⚙ Фильтры ни разу не открывал.
  SELECT count(*) FILTER (WHERE is_chosen),
         count(*) FILTER (WHERE is_default)
    INTO chosen, deflt
    FROM table_filters(sample, 'ru', 'receipts');
  IF chosen <> deflt THEN
    RAISE EXCEPTION 'без личных настроек выбрано % фильтров, а по умолчанию %',
                    chosen, deflt;
  END IF;

  -- И обратное: записанный выбор должен вытеснить набор по умолчанию
  -- целиком, а не добавиться к нему.
  PERFORM user_filters_save(sample, 'receipts', ARRAY['trip']);
  SELECT count(*) FILTER (WHERE is_chosen) INTO chosen
    FROM table_filters(sample, 'ru', 'receipts');
  PERFORM user_filters_reset(sample, 'receipts');
  IF chosen <> 1 THEN
    RAISE EXCEPTION 'после личного выбора из одного фильтра выбрано %', chosen;
  END IF;
END
$check$;
