-- V33: справочник организаций оператора.
--
-- Таблица operator создана ещё на этапе 1 под решение Р2 (два юрлица),
-- но API и экрана у неё не было. Р2 отменено: счета и акты приходят
-- из интеграции готовыми, и выяснять, у какого юрлица договор, порталу
-- не нужно. Сама таблица при этом полезна: в ней лежат реквизиты
-- организации и содержимое страниц «О нас» и «Контакты», которое
-- сейчас зашито в вёрстку.
--
-- Поэтому это именно справочник реквизитов, а не механизм выбора
-- оператора: нигде в коде по operator_id ничего не ветвится.
SET search_path = dbo;

-- Логотип не трогаем: он лежит в объектном хранилище (logo_object_key),
-- а хранилища пока нет. Появится вместе с фотографиями документов.
CREATE OR REPLACE VIEW v_operators AS
SELECT o.operator_id, o.code, o.name, o.bin, o.legal_address,
       o.bank_name, o.bank_account, o.phone, o.email, o.is_active,
       (SELECT count(*) FROM depositor d WHERE d.operator_id = o.operator_id)
         AS depositor_count
FROM operator o;
ALTER VIEW v_operators SET (security_invoker = true);
GRANT SELECT ON v_operators TO portal_app;

COMMENT ON VIEW v_operators IS
  'Организации оператора для экрана администрирования';

-- Список и карточка: страницы «О нас» и «Контакты» в список не берём,
-- это длинные тексты — в таблице от них пользы нет, а весить будут.
CREATE OR REPLACE FUNCTION admin_operator_list()
RETURNS TABLE (operator_id text, code varchar, name varchar, bin varchar,
               legal_address text, bank_name varchar, bank_account varchar,
               phone varchar, email varchar, is_active boolean,
               depositor_count bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT o.operator_id::text, o.code, o.name, o.bin, o.legal_address,
         o.bank_name, o.bank_account, o.phone, o.email, o.is_active,
         (SELECT count(*) FROM depositor d WHERE d.operator_id = o.operator_id)
    FROM operator o
   ORDER BY o.code
$$;
GRANT EXECUTE ON FUNCTION admin_operator_list() TO portal_app;

CREATE OR REPLACE FUNCTION admin_operator_card(p_operator_id uuid)
RETURNS TABLE (operator_id text, code varchar, name varchar, bin varchar,
               legal_address text, bank_name varchar, bank_account varchar,
               phone varchar, email varchar, is_active boolean,
               depositor_count bigint, about_content text, contacts_content text)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT l.*, o.about_content, o.contacts_content
    FROM admin_operator_list() l
    JOIN operator o ON o.operator_id::text = l.operator_id
   WHERE l.operator_id = p_operator_id::text
$$;
GRANT EXECUTE ON FUNCTION admin_operator_card(uuid) TO portal_app;

CREATE OR REPLACE FUNCTION admin_save_operator(
  p_operator_id uuid,            -- NULL — создание
  p_code text, p_name text, p_bin text, p_legal_address text,
  p_bank_name text, p_bank_account text, p_phone text, p_email text,
  p_about_content text, p_contacts_content text)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE v_id uuid;
BEGIN
  IF p_code IS NULL OR btrim(p_code) = '' THEN
    RAISE EXCEPTION 'Укажите код организации';
  END IF;
  IF p_name IS NULL OR btrim(p_name) = '' THEN
    RAISE EXCEPTION 'Укажите наименование организации';
  END IF;

  IF p_operator_id IS NULL THEN
    INSERT INTO operator (code, name, bin, legal_address, bank_name, bank_account,
                          phone, email, about_content, contacts_content, is_active)
    VALUES (btrim(p_code), btrim(p_name), p_bin, p_legal_address, p_bank_name,
            p_bank_account, p_phone, p_email, p_about_content, p_contacts_content, true)
    RETURNING operator_id INTO v_id;
  ELSE
    UPDATE operator
       SET code = btrim(p_code), name = btrim(p_name), bin = p_bin,
           legal_address = p_legal_address, bank_name = p_bank_name,
           bank_account = p_bank_account, phone = p_phone, email = p_email,
           about_content = p_about_content, contacts_content = p_contacts_content
     WHERE operator_id = p_operator_id
    RETURNING operator_id INTO v_id;
    IF v_id IS NULL THEN
      RAISE EXCEPTION 'Организация не найдена';
    END IF;
  END IF;
  RETURN v_id::text;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_save_operator(uuid, text, text, text, text, text,
  text, text, text, text, text) TO portal_app;

-- Код организации уникален без учёта регистра: «ahl-kz» и «AHL-KZ» —
-- одна и та же организация, и две записи тут только путали бы.
CREATE UNIQUE INDEX IF NOT EXISTS operator_code_ci_uk ON operator (lower(code));

CREATE OR REPLACE FUNCTION admin_set_operator_active(p_operator_id uuid, p_active boolean)
RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  UPDATE operator SET is_active = p_active WHERE operator_id = p_operator_id;
$$;
GRANT EXECUTE ON FUNCTION admin_set_operator_active(uuid, boolean) TO portal_app;

-- Где используется. Удалять организацию, на которую ссылаются
-- поклажедатели, нельзя — внешний ключ и так не даст, но человеку
-- нужно объяснение, а не текст ошибки базы.
CREATE OR REPLACE FUNCTION operator_usage(p_operator_id uuid)
RETURNS TABLE (depositors bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = dbo AS $$
  SELECT (SELECT count(*) FROM depositor WHERE operator_id = p_operator_id)
$$;
GRANT EXECUTE ON FUNCTION operator_usage(uuid) TO portal_app;

CREATE OR REPLACE FUNCTION admin_delete_operator(p_operator_id uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE v_used bigint;
BEGIN
  SELECT depositors INTO v_used FROM operator_usage(p_operator_id);
  IF v_used > 0 THEN
    RAISE EXCEPTION 'Организация указана у % поклажедателей', v_used;
  END IF;
  DELETE FROM operator WHERE operator_id = p_operator_id;
END;
$$;
GRANT EXECUTE ON FUNCTION admin_delete_operator(uuid) TO portal_app;

-- =====================================================================
-- Подписи экрана
-- =====================================================================
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('admin.title.operator_organizations', 'admin', 'title', NULL, 'справочник оператора'),
  ('admin.title.new_organization', 'admin', 'title', NULL, 'справочник оператора'),
  ('admin.button.create_organization', 'admin', 'button', NULL, 'справочник оператора'),
  ('admin.column.organization_code', 'admin', 'column', NULL, 'справочник оператора'),
  ('admin.column.organization_name', 'admin', 'column', NULL, 'справочник оператора'),
  ('admin.column.bank', 'admin', 'column', NULL, 'справочник оператора'),
  ('admin.column.bank_account', 'admin', 'column', NULL, 'справочник оператора'),
  ('admin.column.depositors_count', 'admin', 'column', NULL, 'справочник оператора'),
  ('admin.label.about_page', 'admin', 'label', NULL, 'справочник оператора'),
  ('admin.label.contacts_page', 'admin', 'label', NULL, 'справочник оператора'),
  ('admin.hint.operator_directory', 'admin', 'hint', NULL, 'справочник оператора'),
  ('common.column.email', 'common', 'column', NULL, 'справочник оператора')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
  element = excluded.element, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
  ('admin.title.operator_organizations',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Организации оператора'),
  ('admin.title.operator_organizations',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Operator organizations'),
  ('admin.title.new_organization',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Новая организация'),
  ('admin.title.new_organization',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'New organization'),
  ('admin.button.create_organization',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Создать организацию'),
  ('admin.button.create_organization',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Create organization'),
  ('admin.column.organization_code',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Код организации'),
  ('admin.column.organization_code',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Organization code'),
  ('admin.column.organization_name',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наименование организации'),
  ('admin.column.organization_name',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Organization name'),
  ('admin.column.bank',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Банк'),
  ('admin.column.bank',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Bank'),
  ('admin.column.bank_account',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Счёт'),
  ('admin.column.bank_account',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Account'),
  ('admin.column.depositors_count',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Поклажедателей'),
  ('admin.column.depositors_count',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Depositors'),
  ('admin.label.about_page',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Страница «О нас»'),
  ('admin.label.about_page',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), '“About us” page'),
  ('admin.label.contacts_page',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Страница «Контакты»'),
  ('admin.label.contacts_page',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), '“Contacts” page'),
  ('admin.hint.operator_directory',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'),
   'Реквизиты организаций оператора и содержимое страниц «О нас» и «Контакты». Логотип появится вместе с файловым хранилищем.'),
  ('admin.hint.operator_directory',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'),
   'Operator organization details and the content of the “About us” and “Contacts” pages. The logo will come with the file storage.'),
  ('common.column.email',
   (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Email'),
  ('common.column.email',
   (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Email')
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
