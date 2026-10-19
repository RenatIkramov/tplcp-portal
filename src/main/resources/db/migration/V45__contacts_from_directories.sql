-- =====================================================================
-- V45. Страница «Контакты» собирается из справочников
-- =====================================================================
-- Раньше реквизиты, склады и менеджеры были вписаны в вёрстку демо:
-- переезд склада означал правку HTML и выкладку заново. Теперь всё
-- это берётся из справочников, которые администратор и так ведёт.
--
-- Что откуда:
--   реквизиты  → operator
--   склады     → warehouse (через v_warehouses: клиент видит только
--                свои склады — те же, что доступны ему в документах)
--   менеджеры  → app_user с ролью «менеджер», активные
SET search_path = dbo;

-- ---------------------------------------------------------------------
-- 1. У склада появляются площадь и режим работы
-- ---------------------------------------------------------------------
-- В прототипе на странице контактов они есть («Площадь: 6 700 м²,
-- класс B+», «пн–сб, 08:00–20:00»), а в базе их не было — блок склада
-- вышел бы беднее согласованного макета.
--
-- Режим работы — строкой, а не расписанием по дням. Расписание нужно
-- было бы разбирать и сравнивать, а портал его только показывает:
-- структура, которой никто не пользуется, стоит дороже, чем даёт.
ALTER TABLE warehouse
  ADD COLUMN IF NOT EXISTS area_m2    numeric(10,1),
  ADD COLUMN IF NOT EXISTS work_hours varchar(120);

COMMENT ON COLUMN warehouse.area_m2 IS
  'Площадь склада, м² — показывается на странице «Контакты»';
COMMENT ON COLUMN warehouse.work_hours IS
  'Режим работы строкой, как его показывают клиенту: «пн–сб, 08:00–20:00»';

-- Оба представления дописываем. CREATE OR REPLACE умеет добавлять
-- колонки только в конец — поэтому и добавляем в конец.
CREATE OR REPLACE VIEW v_warehouses AS
SELECT w.warehouse_id, w.code, w.name, w.address, w.responsible, w.phone, w.email,
       w.is_active, w.area_m2, w.work_hours
FROM warehouse w
WHERE w.is_active
  AND (NOT EXISTS (SELECT 1 FROM depositor_warehouse dw
                    WHERE dw.depositor_id = current_depositor_id())
       OR EXISTS (SELECT 1 FROM depositor_warehouse dw
                   WHERE dw.depositor_id = current_depositor_id()
                     AND dw.warehouse_id = w.warehouse_id));

CREATE OR REPLACE VIEW v_warehouses_all AS
SELECT w.warehouse_id, w.code, w.name, w.address, w.responsible, w.phone, w.email,
       w.is_active, w.area_m2, w.work_hours
FROM warehouse w;

-- ---------------------------------------------------------------------
-- 2. Контакты менеджеров
-- ---------------------------------------------------------------------
-- SECURITY DEFINER, потому что клиенту нужно увидеть сотрудников
-- оператора, а правами на таблицу пользователей он не обладает и
-- обладать не должен. Функция отдаёт СТРОГО контактные поля:
-- имя, должность, телефон, почту. Ни ролей, ни доступов, ни логинов
-- других клиентов отсюда не узнать.
--
-- Признак is_yours — менеджер, который ведёт текущего поклажедателя.
-- Показываем всех активных менеджеров (решение заказчика), но своего
-- выделяем: клиенту важно, кому писать по своим документам.
-- Самого списка клиентов менеджера наружу не отдаём: по нему один
-- клиент узнал бы, с кем ещё работает оператор.
--
-- job_title, а не position: position — зарезервированное слово SQL
-- (функция position(подстрока in строке)), и в объявлении колонки
-- оно разбирается как вызов функции. В таблице колонка так и зовётся
-- position — там это допустимо, потому что разбирается как ссылка
-- на колонку, а не как объявление.
CREATE OR REPLACE FUNCTION contact_managers(p_depositor_id uuid)
RETURNS TABLE (user_id uuid, full_name varchar, job_title varchar,
               phone varchar, email varchar, is_yours boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = dbo AS $$
  SELECT u.user_id, u.full_name, u.position AS job_title, u.phone, u.login AS email,
         EXISTS (SELECT 1 FROM user_depositor_access uda
                  WHERE uda.user_id = u.user_id
                    AND uda.depositor_id = p_depositor_id) AS is_yours
  FROM app_user u
  JOIN enum_user_role r ON r.id = u.role_id
  WHERE r.code = 'manager'
    AND u.is_active
  ORDER BY EXISTS (SELECT 1 FROM user_depositor_access uda
                    WHERE uda.user_id = u.user_id
                      AND uda.depositor_id = p_depositor_id) DESC,
           u.full_name
$$;
GRANT EXECUTE ON FUNCTION contact_managers(uuid) TO portal_app;
COMMENT ON FUNCTION contact_managers(uuid) IS
  'Контакты менеджеров оператора для страницы «Контакты». '
  'Свой менеджер идёт первым и помечен is_yours';

-- ---------------------------------------------------------------------
-- 3. Реквизиты оператора
-- ---------------------------------------------------------------------
-- Поклажедателю — организация, с которой у него договор
-- (depositor.operator_id). Остальным — все активные организации:
-- менеджер и администратор работают со всеми.
--
-- Тоже SECURITY DEFINER: таблица operator правами клиента не читается,
-- а реквизиты оператора — это то, что он и так печатает на счетах.
-- Банковский счёт здесь есть намеренно: клиенту платить по нему.
CREATE OR REPLACE FUNCTION contact_operators(p_depositor_id uuid)
RETURNS TABLE (operator_id uuid, code varchar, name varchar, bin varchar,
               legal_address text, bank_name varchar, bank_account varchar,
               phone varchar, email varchar)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = dbo AS $$
  SELECT o.operator_id, o.code, o.name, o.bin, o.legal_address,
         o.bank_name, o.bank_account, o.phone, o.email
  FROM operator o
  WHERE o.is_active
    AND (p_depositor_id IS NULL
         OR o.operator_id = (SELECT d.operator_id FROM depositor d
                              WHERE d.depositor_id = p_depositor_id))
  ORDER BY o.name
$$;
GRANT EXECUTE ON FUNCTION contact_operators(uuid) TO portal_app;
COMMENT ON FUNCTION contact_operators(uuid) IS
  'Реквизиты оператора для страницы «Контакты». Передан поклажедатель — '
  'его организация; NULL — все активные';

-- ---------------------------------------------------------------------
-- 4. Тексты
-- ---------------------------------------------------------------------
INSERT INTO ui_text_key (text_key, scope, element, owner, note) VALUES
  ('portal.label.warehouse_area', 'portal', 'label', NULL, 'площадь склада, м²'),
  ('portal.label.work_hours', 'portal', 'label', NULL, 'режим работы склада'),
  ('common.text.your_manager', 'common', 'text', NULL,
   'отметка у менеджера, который ведёт этого поклажедателя'),
  ('common.text.responsible_person', 'common', 'text', NULL, NULL),
  ('common.text.no_contacts_yet', 'common', 'text', NULL,
   'справочники пусты — показывать нечего')
ON CONFLICT (text_key) DO UPDATE SET scope = excluded.scope,
                                     element = excluded.element,
                                     note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
FROM (VALUES
  ('portal.label.warehouse_area', 'ru', 'Площадь, м²'),
  ('portal.label.warehouse_area', 'en', 'Area, m²'),
  ('portal.label.work_hours', 'ru', 'Режим работы'),
  ('portal.label.work_hours', 'en', 'Business hours'),
  ('common.text.your_manager', 'ru', 'ваш менеджер'),
  ('common.text.your_manager', 'en', 'your manager'),
  ('common.text.responsible_person', 'ru', 'Ответственный'),
  ('common.text.responsible_person', 'en', 'Responsible person'),
  ('common.text.no_contacts_yet', 'ru', 'Справочник пока не заполнен'),
  ('common.text.no_contacts_yet', 'en', 'The directory is empty so far')
) AS v(text_key, locale, value)
JOIN dictionary_locale l ON l.code = v.locale
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
