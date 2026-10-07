-- V31: исправления к справочнику колонок.
--
-- 1. Перечень таблиц расщеплялся на 16 строк вместо 12.
-- 2. Настройки поклажедателя нечем было вернуть к портальным.
-- 3. Переименование полей читалось, но записать его было нечем.
--
-- Отдельной миграцией, а не правкой V29: та уже применена, а применённую
-- миграцию менять нельзя — Flyway сверяет контрольную сумму.
SET search_path = dbo;

-- =====================================================================
-- 0. Повторное сохранение выбора колонок падало на уникальном ключе
--
-- В V29 функция была написана одним запросом: удаление прежнего выбора
-- в data-modifying CTE и вставка нового в основном запросе. Так делать
-- нельзя: CTE и основной запрос работают в одном снимке данных, и
-- проверка уникальности всё ещё видит удаляемые строки. Первое
-- сохранение проходило (строк не было), а второе падало с
-- «duplicate key value violates unique constraint».
--
-- Удаление и вставка должны быть отдельными операторами.
-- =====================================================================
DROP FUNCTION IF EXISTS user_columns_save(uuid, text, text[]);
CREATE FUNCTION user_columns_save(p_user_id uuid, p_table_key text,
                                  p_columns text[])
RETURNS void
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  DELETE FROM user_table_column
  WHERE user_id = p_user_id AND table_key = p_table_key;

  INSERT INTO user_table_column (user_id, table_key, column_id, sort_order)
  SELECT p_user_id, p_table_key, x.column_id, x.ord::smallint
  FROM unnest(p_columns) WITH ORDINALITY AS x(column_id, ord);
END;
$$;

COMMENT ON FUNCTION user_columns_save(uuid, text, text[]) IS
  'Выбор колонок пользователя; порядок — порядок элементов массива';

-- =====================================================================
-- 1. Перечень таблиц
--
-- Колонка «Коммерческий код» в составе документа относится к объекту
-- «Товар» (одно переименование — во всех списках сразу), а остальные
-- колонки той же таблицы — к объекту «Поступление». Из-за этого
-- группировка по паре (таблица, объект) разваливала таблицу на две
-- строки: «receiptComposition / Поступление» и «receiptComposition /
-- Товар». В перечне получалось 16 строк вместо 12, и экран настройки
-- показывал один и тот же раздел дважды.
--
-- Объект таблицы и объект поля — разные вещи. Объект таблицы берём
-- по большинству её колонок: у состава документа это документ,
-- у запасов — запасы, и ни одного спорного случая среди двенадцати
-- таблиц нет.
-- =====================================================================
DROP VIEW IF EXISTS v_column_tables;
CREATE VIEW v_column_tables AS
SELECT
  c.table_key,
  mode() WITHIN GROUP (ORDER BY o.code) AS object_code,
  mode() WITHIN GROUP (ORDER BY o.name) AS object_name,
  count(*)::int                             AS column_count,
  count(*) FILTER (WHERE c.is_default)::int  AS default_count
FROM table_column c
LEFT JOIN enum_object_type o ON o.id = c.object_type_id
GROUP BY c.table_key
ORDER BY c.table_key;

COMMENT ON VIEW v_column_tables IS
  'Разделы для экрана настройки колонок: по строке на таблицу';

-- =====================================================================
-- 2. Вернуть поклажедателю портальные настройки колонок
--
-- Строки в depositor_table_column — это только отличия от портала,
-- поэтому «вернуть как у портала» = убрать их. Делать это обычным
-- DELETE нельзя: таблица под FORCE ROW LEVEL SECURITY, и запрос без
-- контекста поклажедателя молча удаляет ноль строк. Администратор
-- работает с чужим клиентом, своего контекста у него нет — отсюда
-- SECURITY DEFINER. Право проверяется в приложении до вызова.
-- =====================================================================
DROP FUNCTION IF EXISTS admin_depositor_columns_reset(uuid, text);
CREATE FUNCTION admin_depositor_columns_reset(p_depositor_id uuid,
                                              p_table_key text DEFAULT NULL)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  v_count integer;
BEGIN
  IF p_depositor_id IS NULL THEN
    RAISE EXCEPTION 'Укажите поклажедателя';
  END IF;
  DELETE FROM depositor_table_column
  WHERE depositor_id = p_depositor_id
    AND (p_table_key IS NULL OR table_key = p_table_key);
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;

COMMENT ON FUNCTION admin_depositor_columns_reset(uuid, text) IS
  'Убрать отличия от портальных настроек колонок: без таблицы — по всем';

-- Сколько настроек заведено руками. Нужно и экрану («раздел настроен»),
-- и проверкам: читать таблицу напрямую мешает та же RLS.
DROP FUNCTION IF EXISTS admin_depositor_column_count(uuid, text);
CREATE FUNCTION admin_depositor_column_count(p_depositor_id uuid,
                                             p_table_key text DEFAULT NULL)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo
AS $$
  SELECT count(*)::int FROM depositor_table_column
  WHERE depositor_id = p_depositor_id
    AND (p_table_key IS NULL OR table_key = p_table_key);
$$;

-- =====================================================================
-- 3. Переименование полей поклажедателем
--
-- Таблица depositor_field_label создавалась ещё на этапе 1, но писать
-- в неё было нечем: под RLS вставка без контекста поклажедателя
-- отклоняется, а администратор ведёт чужого клиента. Чтение уже
-- работает (его делают table_columns и admin_depositor_columns),
-- не хватало записи.
--
-- Поле опознаётся парой «код объекта + идентификатор поля», как его
-- и показывает справочник колонок. Пустая подпись означает «вернуть
-- портальное название», поэтому строка убирается, а не хранится пустой.
-- =====================================================================
DROP FUNCTION IF EXISTS admin_field_label_save(uuid, text, text, text);
CREATE FUNCTION admin_field_label_save(p_depositor_id uuid, p_object_code text,
                                       p_field_id text, p_label text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = dbo
AS $$
DECLARE
  v_object_id smallint;
BEGIN
  SELECT id INTO v_object_id FROM enum_object_type WHERE code = p_object_code;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Неизвестный тип объекта: %', p_object_code;
  END IF;

  DELETE FROM depositor_field_label
  WHERE depositor_id = p_depositor_id
    AND object_type_id = v_object_id
    AND field_id = p_field_id;

  IF p_label IS NOT NULL AND btrim(p_label) <> '' THEN
    INSERT INTO depositor_field_label
      (depositor_id, object_type_id, field_id, custom_label)
    VALUES (p_depositor_id, v_object_id, p_field_id, btrim(p_label));
  END IF;
END;
$$;

COMMENT ON FUNCTION admin_field_label_save(uuid, text, text, text) IS
  'Как поклажедатель называет поле; пустая подпись возвращает портальное название';

-- Какие поля переименованы. Одна строка на поле, а не на колонку:
-- поле показывается в нескольких таблицах, а название у него одно.
DROP FUNCTION IF EXISTS admin_field_labels(uuid);
CREATE FUNCTION admin_field_labels(p_depositor_id uuid)
RETURNS TABLE (
  object_code   varchar(20),
  object_name   varchar(100),
  field_id      varchar(100),
  custom_label  varchar(255),
  shown_in      text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = dbo
AS $$
  SELECT
    o.code,
    o.name,
    l.field_id,
    l.custom_label,
    -- в каких таблицах это поле видно: так сразу ясно, где подпись
    -- поменяется, и не приходится угадывать
    COALESCE(string_agg(DISTINCT c.table_key, ', ' ORDER BY c.table_key), '—')
  FROM depositor_field_label l
  JOIN enum_object_type o ON o.id = l.object_type_id
  LEFT JOIN table_column c
         ON c.object_type_id = l.object_type_id AND c.field_id = l.field_id
  WHERE l.depositor_id = p_depositor_id
  GROUP BY o.code, o.name, l.field_id, l.custom_label
  ORDER BY o.code, l.field_id;
$$;

GRANT EXECUTE ON FUNCTION admin_depositor_columns_reset(uuid, text) TO portal_app;
GRANT EXECUTE ON FUNCTION admin_depositor_column_count(uuid, text) TO portal_app;
GRANT EXECUTE ON FUNCTION admin_field_label_save(uuid, text, text, text) TO portal_app;
GRANT EXECUTE ON FUNCTION admin_field_labels(uuid) TO portal_app;

-- Подпись кнопки сброса на экране настройки колонок поклажедателя.
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('references.button.restore_portal_defaults', 'references', 'button', NULL,
   'справочник колонок')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
  ('references.button.restore_portal_defaults',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'),
   'Вернуть портальные настройки'),
  ('references.button.restore_portal_defaults',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'),
   'Restore portal defaults')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
