SET search_path = dbo;

-- V122. Тексты интерфейса и переименования полей не несут чужой
-- разметки (аудит 11.11.2026, S5).
--
-- Тексты вставляются в страницу как разметка — и на форме входа тоже:
-- часть их и правда с <b>, <br/> и ссылкой на почту. Текст с <script>
-- или <img onerror> выполнился бы у каждого, кто открыл портал, и мог
-- бы перехватывать пароли. Сегодня тексты пишет только администратор,
-- но любая запись в ui_text — ошибка кода, импорт переводов, UPDATE
-- руками — становилась бы захватом всех учётных записей.
--
-- Поэтому правило — в базе, а не только в экране:
-- * ui_text: разрешены только <b>, <strong>, <i>, <em>, <br>, <br/>
--   и ссылка <a href="mailto:…"> / <a href="https://…">. Атрибутов,
--   кроме href у ссылки, нет. Остальное — отказ при записи.
-- * depositor_field_label: переименование поля — простой текст,
--   угловые скобки не нужны вовсе.
-- Экран вдобавок пропускает разметку текстов через тот же белый список
-- (PORTAL.safeHtml, core.js).

CREATE FUNCTION ui_text_markup_ok(p_value text) RETURNS boolean
LANGUAGE sql
IMMUTABLE
SET search_path = dbo, pg_temp
AS $$
  SELECT regexp_replace(
           regexp_replace(COALESCE(p_value, ''),
                          '<a href="(mailto:|https://)[^"<>]*">', '', 'gi'),
           '</?(b|strong|i|em)>|<br\s*/?>|</a>', '', 'gi')
         !~ '<[A-Za-z/!?]'
$$;
GRANT EXECUTE ON FUNCTION ui_text_markup_ok(text) TO portal_app;
COMMENT ON FUNCTION ui_text_markup_ok(text) IS
  'в тексте интерфейса только разрешённая разметка: b, strong, i, em, br, a href mailto/https (V122)';

-- Всё, что уже лежит в каталоге, правилу отвечает — иначе миграция
-- остановится здесь, а не на первой правке.
DO $$
DECLARE
  v_bad text;
BEGIN
  SELECT string_agg(text_key, ', ') INTO v_bad FROM ui_text WHERE NOT ui_text_markup_ok(value);
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'тексты с запрещённой разметкой: %', v_bad;
  END IF;
  SELECT string_agg(field_id, ', ') INTO v_bad FROM depositor_field_label WHERE custom_label ~ '[<>]';
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'переименования полей с угловыми скобками: %', v_bad;
  END IF;
END
$$;

CREATE FUNCTION ui_text_markup_guard() RETURNS trigger
LANGUAGE plpgsql
SET search_path = dbo, pg_temp   -- пишут и из psql без dbo в пути
AS $$
BEGIN
  IF NOT ui_text_markup_ok(NEW.value) THEN
    RAISE EXCEPTION 'ui_text %: markup not allowed', NEW.text_key USING ERRCODE = '22023';
  END IF;
  RETURN NEW;
END
$$;
CREATE TRIGGER markup_guard BEFORE INSERT OR UPDATE OF value ON ui_text
  FOR EACH ROW EXECUTE FUNCTION ui_text_markup_guard();

CREATE FUNCTION field_label_markup_guard() RETURNS trigger
LANGUAGE plpgsql
SET search_path = dbo, pg_temp   -- пишут и из psql без dbo в пути
AS $$
BEGIN
  IF NEW.custom_label ~ '[<>]' THEN
    RAISE EXCEPTION 'depositor_field_label %: angle brackets not allowed', NEW.field_id USING ERRCODE = '22023';
  END IF;
  RETURN NEW;
END
$$;
CREATE TRIGGER markup_guard BEFORE INSERT OR UPDATE OF custom_label ON depositor_field_label
  FOR EACH ROW EXECUTE FUNCTION field_label_markup_guard();

-- Тексты отказа для администратора.
INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('error.text.markup_not_allowed', 'server', 'message', 'server', 'текст с запрещённой разметкой (V122)'),
  ('error.field_label.angle_brackets', 'server', 'message', 'server', 'переименование поля с < или > (V122)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.text.markup_not_allowed', 'ru',
     'В тексте можно только жирный, курсив, перенос строки и ссылку на почту или https — остальная разметка не сохраняется'),
    ('error.text.markup_not_allowed', 'en',
     'Only bold, italic, line break and a mailto or https link are allowed in a text; other markup is not saved'),
    ('error.field_label.angle_brackets', 'ru', 'В названии поля не может быть знаков < и >'),
    ('error.field_label.angle_brackets', 'en', 'A field name cannot contain < or >')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
