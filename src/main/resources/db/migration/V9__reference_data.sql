-- V9__reference_data.sql
-- Справочные данные портала (не тестовые — нужны и в продакшне).
-- Значения взяты из HTML-демо.
SET search_path = dbo;

-- ---------- Типы объектов (полиморфные ссылки) -------------------------------
INSERT INTO enum_object_type (id, code, name, sort_order, is_active) VALUES
  (1,'receipts','Поступление',10,true),
  (2,'orders','Заказ',20,true),
  (3,'transfers','Перемещение',30,true),
  (4,'sku','Товар',40,true),
  (5,'packaging','Упаковка',50,true),
  (6,'depositor','Поклажедатель',60,true),
  (7,'supplier','Поставщик',70,true),
  (8,'consignee','Грузополучатель',80,true),
  (9,'warehouse','Склад',90,true),
  (10,'app_user','Пользователь',100,true);

-- ---------- Языки ------------------------------------------------------------
INSERT INTO dictionary_locale (locale_id, code, name, is_default, sort_order, is_active) VALUES
  (1,'ru','Русский',true,10,true),
  (2,'en','English',false,20,true),
  (3,'kk','Қазақша',false,30,true);

-- ---------- Роли пользователей ----------------------------------------------
INSERT INTO enum_user_role (id, code, name, sort_order, is_active) VALUES
  (1,'depositor','Поклажедатель',10,true),
  (2,'manager','Менеджер 3PL-оператора',20,true),
  (3,'staff','Складской сотрудник',30,true),
  (4,'admin','Администратор',40,true),
  (5,'developer','Разработчик',50,true);

-- ---------- Статусы (по типам объектов) --------------------------------------
-- Поступления
INSERT INTO enum_status (id, object_type_id, code, name, sort_order, is_active) VALUES
  (11,1,'draft','Черновик',10,true),
  (12,1,'awaiting','Ожидает приёмки',20,true),
  (13,1,'in_progress','В работе',30,true),
  (14,1,'received','Принято',40,true),
  (15,1,'discrepancy','Расхождение по кол-ву',50,true),
-- Заказы
  (21,2,'draft','Черновик',10,true),
  (22,2,'reserved','В резерве',20,true),
  (23,2,'picking','Комплектуется',30,true),
  (24,2,'shipped','Отгружен',40,true),
  (25,2,'delayed','Задержка отгрузки',50,true),
-- Перемещения
  (31,3,'draft','Черновик',10,true),
  (32,3,'forming','Формируется',20,true),
  (33,3,'in_transit','В пути',30,true),
  (34,3,'received','Принято',40,true),
  (35,3,'form_error','Ошибка формирования',50,true),
-- Товар
  (41,4,'active','Активен',10,true),
  (42,4,'inactive','Неактивен',20,true),
-- Упаковка
  (51,5,'active','Активна',10,true),
  (52,5,'inactive','Неактивна',20,true);

-- Статусы счетов (в демо — бейджи биллинга; тип объекта отдельный не вводился,
-- используется тип «Поклажедатель» как владелец счёта — уточнить при реализации биллинга)
INSERT INTO enum_status (id, object_type_id, code, name, sort_order, is_active) VALUES
  (61,6,'invoice_awaiting','Ожидает оплаты',10,true),
  (62,6,'invoice_paid','Оплачен',20,true),
  (63,6,'invoice_overdue','Просрочен',30,true);

-- ---------- Единицы измерения (коды МКЕИ) ------------------------------------
INSERT INTO enum_uom (id, mkei_code, symbol, name, sort_order, is_active) VALUES
  (1,'796','шт','Штука',10,true),
  (2,'163','г','Грамм',20,true),
  (3,'166','кг','Килограмм',30,true),
  (4,'168','т','Тонна',40,true),
  (5,'112','л','Литр',50,true),
  (6,'006','м','Метр',60,true),
  (7,'055','м²','Метр квадратный',70,true),
  (8,'113','м³','Метр кубический',80,true),
  (9,'704','пар','Пара',90,true),
  (10,'778','уп','Упаковка',100,true),
  (11,'839','компл','Комплект',110,true),
  (12,'736','рул','Рулон',120,true),
  (13,'715','меш','Мешок',130,true),
  (14,'977','тюк','Тюк',140,true),
  (15,'984','кор','Короб',150,true),
  (16,'719','канистра','Канистра',160,true);

-- ---------- Валюты -----------------------------------------------------------
INSERT INTO enum_currency (id, code, name, sort_order, is_active) VALUES
  (1,'KZT','Тенге',10,true),
  (2,'USD','Доллар США',20,true),
  (3,'EUR','Евро',30,true),
  (4,'RUB','Российский рубль',40,true);

-- ---------- Типы приходных документов ---------------------------------------
INSERT INTO enum_receipt_doc_type (id, code, name, sort_order, is_active) VALUES
  (1,'invoice','Накладная',10,true),
  (2,'import','Импортная поставка',20,true),
  (3,'return','Возврат от покупателя',30,true),
  (4,'transfer_in','Приём перемещения',40,true);

-- ---------- Типы заказов -----------------------------------------------------
INSERT INTO enum_order_type (id, code, name, sort_order, is_active) VALUES
  (1,'sale','Отгрузка покупателю',10,true),
  (2,'return_supplier','Возврат поставщику',20,true),
  (3,'transfer_out','Отгрузка на перемещение',30,true),
  (4,'writeoff','Списание',40,true);

-- ---------- Типы упаковки ----------------------------------------------------
INSERT INTO enum_packaging_type (id, code, name, sort_order, is_active) VALUES
  (1,'unit','Единица товара',10,true),
  (2,'box','Короб',20,true),
  (3,'pallet','Паллета',30,true),
  (4,'bag','Мешок',40,true);

-- ---------- Причины блокировки ----------------------------------------------
INSERT INTO enum_block_reason (id, code, name, sort_order, is_active) VALUES
  (1,'quality','Проверка качества',10,true),
  (2,'expiry','Истёк срок годности',20,true),
  (3,'damage','Повреждение упаковки',30,true),
  (4,'claim','Претензия',40,true),
  (5,'inventory','Инвентаризация',50,true);

-- ---------- Классы и группы опасности ---------------------------------------
INSERT INTO enum_hazard_class (id, code, name, sort_order, is_active) VALUES
  (1,'none','Не опасный',10,true),
  (2,'3','Класс 3 — легковоспламеняющиеся жидкости',20,true),
  (3,'8','Класс 8 — коррозионные вещества',30,true),
  (4,'9','Класс 9 — прочие опасные',40,true);

INSERT INTO enum_hazard_group (id, code, name, sort_order, is_active) VALUES
  (1,'none','Без группы',10,true),
  (2,'I','Группа I — высокая опасность',20,true),
  (3,'II','Группа II — средняя опасность',30,true),
  (4,'III','Группа III — низкая опасность',40,true);
