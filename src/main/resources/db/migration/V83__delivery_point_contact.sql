-- ---------------------------------------------------------------------
-- V83. Контакт у места доставки
-- ---------------------------------------------------------------------
-- Решения заказчика от 27.10.2026:
--   * у адреса доставки появляется поле «Контактные данные» — одной
--     строкой, как поле контакта в заказе (consignee_contact);
--   * контакт заказа подставляется по выбранному адресу, а если у адреса
--     своего нет — из карточки грузополучателя: «контактное лицо, телефон».
--
-- Зачем: контакт обычно привязан к точке, а не к организации целиком —
-- у разных магазинов одной сети разные приёмщики. И это подготовка
-- к импорту заказа из Excel: в шаблоне контакта нет, он подставляется
-- тем же правилом, что и в карточке.
--
-- Правило одно и живёт здесь, функцией delivery_contact(): карточка
-- заказа получает уже готовое значение в v_delivery_points, импорт
-- позовёт ту же функцию. Две копии правила разошлись бы при первой правке.
SET search_path = dbo;

ALTER TABLE delivery_point ADD COLUMN IF NOT EXISTS contact varchar(255);
COMMENT ON COLUMN delivery_point.contact IS
  'Контактные данные на этом адресе (лицо, телефон) — подставляются в заказ (V83)';

-- ---------------------------------------------------------------------
-- 1. Правило подстановки
-- ---------------------------------------------------------------------
-- SECURITY INVOKER (по умолчанию): адрес и грузополучатель читаются под
-- RLS вызывающего. Чужой адрес даст NULL, а не чужой контакт.
CREATE OR REPLACE FUNCTION delivery_contact(p_address_id uuid)
RETURNS varchar
LANGUAGE sql
STABLE
SET search_path = dbo
AS $$
  SELECT left(COALESCE(
           NULLIF(btrim(dp.contact), ''),
           NULLIF(concat_ws(', ', NULLIF(btrim(c.contact_name), ''),
                                  NULLIF(btrim(c.contact_phone), '')), '')
         ), 255)::varchar
    FROM delivery_point dp
    LEFT JOIN consignee c ON c.consignee_id = dp.consignee_id
   WHERE dp.address_id = p_address_id
$$;
COMMENT ON FUNCTION delivery_contact(uuid) IS
  'контакт для заказа по месту доставки: свой у адреса, иначе контакты грузополучателя (V83)';
GRANT EXECUTE ON FUNCTION delivery_contact(uuid) TO portal_app;

-- ---------------------------------------------------------------------
-- 2. Представление адресов
-- ---------------------------------------------------------------------
-- Колонки только дописываются в конец — иначе CREATE OR REPLACE VIEW
-- откажет. И security_invoker указывается ЗАНОВО: замена представления
-- без WITH сбрасывает его параметры, и RLS перестала бы действовать
-- от имени вызывающего. Проверка ниже это и поймала на первом прогоне.
--   contact       — то, что записано у адреса (для карточки грузополучателя);
--   order_contact — то, что подставится в заказ (с запасным контактом).
CREATE OR REPLACE VIEW v_delivery_points WITH (security_invoker = true) AS
  SELECT dp.address_id,
         dp.consignee_id,
         c.code AS consignee_code,
         c.name AS consignee_name,
         dp.address,
         COALESCE(dp.is_default, false) AS is_default,
         dp.sort_order,
         dp.contact,
         delivery_contact(dp.address_id) AS order_contact
    FROM delivery_point dp
    LEFT JOIN consignee c ON c.consignee_id = dp.consignee_id;

-- ---------------------------------------------------------------------
-- 3. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('partners.column.address_contact', 'partners', 'column', NULL,
   'адреса доставки грузополучателя: колонка и поле «Контактные данные»'),
  ('partners.hint.address_contact', 'partners', 'hint', NULL,
   'окно адреса доставки: откуда берётся контакт заказа'),
  ('error.address.contact_too_long', 'server', 'message', 'partners',
   'сообщение сервера; собирается по ключу в PortalMessages')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('partners.column.address_contact', 'ru', 'Контактные данные'),
    ('partners.column.address_contact', 'en', 'Contact details'),
    ('partners.hint.address_contact',   'ru',
     'Подставляются в заказ при выборе этого адреса. Если не заполнены — берутся контакты грузополучателя из его карточки.'),
    ('partners.hint.address_contact',   'en',
     'Filled into an order when this address is chosen. If empty, the consignee''s contacts from its card are used.'),
    ('error.address.contact_too_long',  'ru', 'Контактные данные длиннее {limit} знаков'),
    ('error.address.contact_too_long',  'en', 'Contact details are longer than {limit} characters')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;

-- ---------------------------------------------------------------------
-- Проверка на месте
-- ---------------------------------------------------------------------
DO $check$
DECLARE
  dep uuid;
  con uuid;
  adr uuid;
  got varchar;
BEGIN
  IF (SELECT reloptions FROM pg_class
       WHERE oid = 'dbo.v_delivery_points'::regclass) IS DISTINCT FROM ARRAY['security_invoker=true'] THEN
    RAISE EXCEPTION 'v_delivery_points потеряло security_invoker';
  END IF;

  SELECT depositor_id INTO dep FROM depositor LIMIT 1;
  IF dep IS NULL THEN
    RETURN;                  -- пустая база: проверять не на чем
  END IF;
  -- Под чьей бы учётной записью ни шли миграции, строки должны пройти RLS
  PERFORM set_config('app.depositor_id', dep::text, true);

  BEGIN
    INSERT INTO consignee (depositor_id, code, name, contact_name, contact_phone)
    VALUES (dep, 'V83-CHECK', 'Проверка V83', 'Петров П.', '+7 701 000 00 00')
    RETURNING consignee_id INTO con;
    INSERT INTO delivery_point (consignee_id, address) VALUES (con, 'Адрес V83')
    RETURNING address_id INTO adr;

    got := delivery_contact(adr);
    IF got IS DISTINCT FROM 'Петров П., +7 701 000 00 00' THEN
      RAISE EXCEPTION 'без своего контакта ждали контакты грузополучателя, получили %', got;
    END IF;

    UPDATE delivery_point SET contact = 'Сидоров С., +7 702 111 11 11' WHERE address_id = adr;
    got := delivery_contact(adr);
    IF got IS DISTINCT FROM 'Сидоров С., +7 702 111 11 11' THEN
      RAISE EXCEPTION 'свой контакт адреса не взят, получили %', got;
    END IF;

    UPDATE delivery_point SET contact = '  ' WHERE address_id = adr;
    UPDATE consignee SET contact_name = NULL WHERE consignee_id = con;
    got := delivery_contact(adr);
    IF got IS DISTINCT FROM '+7 701 000 00 00' THEN
      RAISE EXCEPTION 'пробелы вместо контакта и один телефон: получили %', got;
    END IF;

    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'v83-rollback';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'v83-rollback' THEN
      RAISE;
    END IF;
  END;
  PERFORM set_config('app.depositor_id', '', true);
END
$check$;
