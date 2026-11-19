-- ---------------------------------------------------------------------
-- V82. Объём упаковки по размерам
-- ---------------------------------------------------------------------
-- Решение заказчика от 27.10.2026: если у упаковки заданы длина, ширина
-- и высота, объём не вводится, а считается:
--
--     volume_m3 = length_cm × width_cm × height_cm / 1 000 000
--
-- с пятью знаками — столько держит колонка numeric(12,5). Если хотя бы
-- одного размера нет, объём остаётся таким, каким его ввели.
--
-- Правило живёт в базе, триггером: упаковку пишут карточка товара,
-- загрузка из Excel и, со временем, обмен с WMS. Три копии одной
-- формулы в коде разошлись бы при первой же правке.
--
-- Уже заведённые упаковки миграция НЕ пересчитывает (решение заказчика):
-- правило срабатывает на новых и изменённых записях. Упаковка, которую
-- поправят в любом поле, получит объём по своим размерам.
SET search_path = dbo;

CREATE OR REPLACE FUNCTION product_packaging_volume()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = dbo
AS $$
BEGIN
  IF NEW.length_cm > 0 AND NEW.width_cm > 0 AND NEW.height_cm > 0 THEN
    NEW.volume_m3 := round(NEW.length_cm * NEW.width_cm * NEW.height_cm / 1000000, 5);
  END IF;
  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION product_packaging_volume() IS
  'объём упаковки по длине, ширине и высоте, если заданы все три (V82)';

DROP TRIGGER IF EXISTS product_packaging_volume ON product_packaging;
CREATE TRIGGER product_packaging_volume
  BEFORE INSERT OR UPDATE ON product_packaging
  FOR EACH ROW EXECUTE FUNCTION product_packaging_volume();

-- ---------------------------------------------------------------------
-- Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('sku.hint.volume_from_dimensions', 'sku', 'hint', NULL,
   'окно упаковки в карточке товара: объём считается по размерам'),
  ('common.button.preparing_template', 'common', 'button', NULL,
   'кнопка «Скачать шаблон Excel», пока сервер готовит файл')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('sku.hint.volume_from_dimensions',  'ru', 'Считается по длине, ширине и высоте'),
    ('sku.hint.volume_from_dimensions',  'en', 'Calculated from length, width and height'),
    ('common.button.preparing_template', 'ru', 'Готовлю шаблон…'),
    ('common.button.preparing_template', 'en', 'Preparing the template…')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
-- Пробная упаковка в откатываемом блоке: вставка считает объём,
-- правка размера пересчитывает, неполные размеры объём не трогают.
DO $check$
DECLARE
  dep  uuid;
  pid  uuid;
  v    numeric;
BEGIN
  SELECT depositor_id INTO dep FROM depositor LIMIT 1;
  IF dep IS NULL THEN
    RETURN;                  -- пустая база: проверять не на чем
  END IF;

  -- Под чьей бы учётной записью ни шли миграции, строка должна пройти
  -- политику RLS: представляемся этим поклажедателем до конца транзакции.
  PERFORM set_config('app.depositor_id', dep::text, true);

  BEGIN
    INSERT INTO product_packaging (depositor_id, packaging_code,
                                   length_cm, width_cm, height_cm, volume_m3)
    VALUES (dep, 'V82-CHECK', 40, 30, 20, 99)
    RETURNING packaging_id, volume_m3 INTO pid, v;
    IF v <> 0.024 THEN
      RAISE EXCEPTION 'объём при вставке: ждали 0.024, получили %', v;
    END IF;

    UPDATE product_packaging SET height_cm = 10 WHERE packaging_id = pid
    RETURNING volume_m3 INTO v;
    IF v <> 0.012 THEN
      RAISE EXCEPTION 'объём после правки высоты: ждали 0.012, получили %', v;
    END IF;

    UPDATE product_packaging SET height_cm = NULL, volume_m3 = 5 WHERE packaging_id = pid
    RETURNING volume_m3 INTO v;
    IF v <> 5 THEN
      RAISE EXCEPTION 'без высоты объём должен остаться введённым: получили %', v;
    END IF;

    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'v82-rollback';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'v82-rollback' THEN
      RAISE;
    END IF;
  END;
  PERFORM set_config('app.depositor_id', '', true);
END
$check$;
