-- V25__languages.sql
-- Мультиязычность портала: языки, язык пользователя и тексты интерфейса.
--
-- Разделение намеренное: тексты интерфейса (подписи кнопок, заголовки)
-- живут в ui_text, переводы данных (наименования товаров, услуг) — в уже
-- существующей таблице translation. Это разные вещи: первые пишет тот,
-- кто ведёт портал, вторые приходят вместе с данными клиента.
SET search_path = dbo;

-- =====================================================================
-- 1. Языки
-- =====================================================================
-- Основной язык портала — английский: на нём ведётся головная компания,
-- и именно он подставляется, когда перевода на выбранный язык нет.
-- Порядок остальных — русский, нидерландский, казахский.
INSERT INTO dictionary_locale (locale_id, code, name, is_default, sort_order, is_active)
VALUES (4, 'nl', 'Nederlands', false, 40, true)
ON CONFLICT (locale_id) DO NOTHING;

UPDATE dictionary_locale SET is_default = (code = 'en');
UPDATE dictionary_locale SET sort_order = CASE code
         WHEN 'en' THEN 10 WHEN 'ru' THEN 20 WHEN 'nl' THEN 30 WHEN 'kk' THEN 40
         ELSE sort_order END;

-- Язык по умолчанию ровно один: иначе непонятно, на какой откатываться.
CREATE UNIQUE INDEX IF NOT EXISTS dictionary_locale_default_uk
  ON dictionary_locale ((true)) WHERE is_default;

-- =====================================================================
-- 2. Язык пользователя
-- =====================================================================
-- Храним в учётной записи, а не в браузере: человек заходит с разных
-- компьютеров, и язык должен ехать за ним. Пусто — язык по умолчанию.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locale_id smallint;
COMMENT ON COLUMN app_user.locale_id IS
  'Выбранный язык интерфейса; NULL — язык портала по умолчанию';
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_app_user_locale') THEN
    ALTER TABLE app_user ADD CONSTRAINT fk_app_user_locale
      FOREIGN KEY (locale_id) REFERENCES dictionary_locale (locale_id);
  END IF;
END $$;

-- =====================================================================
-- 3. Тексты интерфейса
-- =====================================================================
-- Раньше словарь был зашит в вёрстку (I18N_MAP, 276 строк): добавить язык
-- значило править HTML. Теперь тексты в базе и ведутся из администрирования.
CREATE TABLE IF NOT EXISTS ui_text (
  text_key   varchar(60)  NOT NULL,
  locale_id  smallint     NOT NULL REFERENCES dictionary_locale (locale_id),
  value      text         NOT NULL,
  PRIMARY KEY (text_key, locale_id)
);
COMMENT ON TABLE ui_text IS 'Тексты интерфейса портала по языкам';
COMMENT ON COLUMN ui_text.text_key IS 'Ключ из вёрстки (атрибут data-i18n)';
GRANT SELECT, INSERT, UPDATE, DELETE ON ui_text TO portal_app;

INSERT INTO ui_text (text_key, locale_id, value) VALUES
  ('t0', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Заказы'),
  ('t0', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Orders'),
  ('t1', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Поступления'),
  ('t1', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Receipts'),
  ('t2', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Запасы'),
  ('t2', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Stock'),
  ('t3', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Товары'),
  ('t3', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Products'),
  ('t4', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Биллинг'),
  ('t4', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Billing'),
  ('t5', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отчёты'),
  ('t5', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Reports'),
  ('t6', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '⚙ Администрирование'),
  ('t6', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '⚙ Administration'),
  ('t7', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Личный кабинет'),
  ('t7', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'My Account'),
  ('t8', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'О нас'),
  ('t8', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'About Us'),
  ('t9', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Контакты'),
  ('t9', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Contacts'),
  ('t10', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Выход'),
  ('t10', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Log Out'),
  ('t11', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Поступления <span class="count" id="receiptsCount"></span>'),
  ('t11', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Receipts <span class="count" id="receiptsCount"></span>'),
  ('t12', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Заказы на отгрузку <span class="count" id="ordersCount"></span>'),
  ('t12', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Shipment Orders <span class="count" id="ordersCount"></span>'),
  ('t13', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Запасы <span class="count" id="stockCount"></span>'),
  ('t13', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Stock <span class="count" id="stockCount"></span>'),
  ('t14', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Товары <span class="count" id="skuCount"></span>'),
  ('t14', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Products <span class="count" id="skuCount"></span>'),
  ('t15', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Биллинг'),
  ('t15', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Billing'),
  ('t16', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отчёты <span class="count" id="reportsCount"></span>'),
  ('t16', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Reports <span class="count" id="reportsCount"></span>'),
  ('t17', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Администрирование портала'),
  ('t17', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Portal Administration'),
  ('t18', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Контакты'),
  ('t18', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Contacts'),
  ('t19', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Состав документа'),
  ('t19', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Document Contents'),
  ('t20', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Транспорт'),
  ('t20', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Transport'),
  ('t21', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Состав заказа'),
  ('t21', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Order Contents'),
  ('t22', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Транспорт и доставка'),
  ('t22', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Transport &amp; Delivery'),
  ('t23', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Детализация'),
  ('t23', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Details'),
  ('t24', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'История движения'),
  ('t24', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Movement History'),
  ('t25', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Перечень оказываемых услуг с тарифами'),
  ('t25', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Service Price List'),
  ('t26', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отчёты по оказанным услугам'),
  ('t26', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Service Reports'),
  ('t27', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Счета, выставленные бухгалтерией'),
  ('t27', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Invoices Issued by Accounting'),
  ('t28', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Пользователи портала'),
  ('t28', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Portal Users'),
  ('t29', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Назначение складов и поклажедателей менеджерам'),
  ('t29', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Assigning Warehouses and Clients to Managers'),
  ('t30', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ошибки интеграционных сообщений (портал ↔ ArtWMS)'),
  ('t30', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Integration Message Errors (Portal ↔ ArtWMS)'),
  ('t31', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ошибки загрузки данных из Excel'),
  ('t31', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Excel Data Upload Errors'),
  ('t32', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'О компании'),
  ('t32', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'About the Company'),
  ('t33', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наши услуги'),
  ('t33', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Our Services'),
  ('t34', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наши преимущества'),
  ('t34', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Our Advantages'),
  ('t35', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Склад в Алматы'),
  ('t35', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Almaty Warehouse'),
  ('t36', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Офисное пространство'),
  ('t36', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Office Space'),
  ('t37', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отраслевые решения'),
  ('t37', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Industry Solutions'),
  ('t38', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Реквизиты компании'),
  ('t38', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Company Details'),
  ('t39', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Склады'),
  ('t39', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Warehouses'),
  ('t40', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Персональные менеджеры'),
  ('t40', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Account Managers'),
  ('t41', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Есть вопрос или предложение?'),
  ('t41', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Have a question or suggestion?'),
  ('t42', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Создать новый'),
  ('t42', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Create New'),
  ('t43', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Загрузить из Excel'),
  ('t43', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Import from Excel'),
  ('t44', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Скачать шаблон Excel'),
  ('t44', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Download Excel Template'),
  ('t45', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспортировать в Excel'),
  ('t45', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t46', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Создать новый'),
  ('t46', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Create New'),
  ('t47', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Загрузить из Excel'),
  ('t47', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Import from Excel'),
  ('t48', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Скачать шаблон Excel'),
  ('t48', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Download Excel Template'),
  ('t49', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспортировать в Excel'),
  ('t49', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t50', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Создать новый'),
  ('t50', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Create New'),
  ('t51', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Загрузить из Excel'),
  ('t51', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Import from Excel'),
  ('t52', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Скачать шаблон Excel'),
  ('t52', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Download Excel Template'),
  ('t53', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспортировать в Excel'),
  ('t53', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t54', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Создать новый'),
  ('t54', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Create New'),
  ('t55', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Загрузить из Excel'),
  ('t55', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Import from Excel'),
  ('t56', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Скачать шаблон Excel'),
  ('t56', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Download Excel Template'),
  ('t57', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '+ Создать пользователя'),
  ('t57', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '+ Create User'),
  ('t58', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Номер документа'),
  ('t58', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Document Number'),
  ('t59', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Поставщик'),
  ('t59', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Supplier'),
  ('t60', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата с'),
  ('t60', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Date From'),
  ('t61', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата по'),
  ('t61', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Date To'),
  ('t62', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t62', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t63', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Номер заказа'),
  ('t63', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Order Number'),
  ('t64', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Грузополучатель'),
  ('t64', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Consignee'),
  ('t65', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата отгрузки с'),
  ('t65', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Ship Date From'),
  ('t66', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата отгрузки по'),
  ('t66', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Ship Date To'),
  ('t67', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t67', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t68', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Код / артикул'),
  ('t68', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Code / SKU'),
  ('t69', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наименование'),
  ('t69', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Name'),
  ('t70', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Группа'),
  ('t70', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Category'),
  ('t71', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Код / артикул'),
  ('t71', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Code / SKU'),
  ('t72', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наименование'),
  ('t72', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Name'),
  ('t73', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Группа'),
  ('t73', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Category'),
  ('t74', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Период с'),
  ('t74', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Period From'),
  ('t75', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Период по'),
  ('t75', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Period To'),
  ('t76', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t76', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t77', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Тип отчёта'),
  ('t77', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Report Type'),
  ('t78', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Период с'),
  ('t78', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Period From'),
  ('t79', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Период по'),
  ('t79', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Period To'),
  ('t80', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '⚙ Колонки'),
  ('t80', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '⚙ Columns'),
  ('t81', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспорт в Excel'),
  ('t81', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t82', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '⚙ Колонки'),
  ('t82', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '⚙ Columns'),
  ('t83', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспорт в Excel'),
  ('t83', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t84', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '⚙ Колонки'),
  ('t84', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '⚙ Columns'),
  ('t85', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспорт в Excel'),
  ('t85', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t86', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '⚙ Колонки'),
  ('t86', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '⚙ Columns'),
  ('t87', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Экспорт в Excel'),
  ('t87', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Export to Excel'),
  ('t88', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сформировать отчёт'),
  ('t88', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Generate Report'),
  ('t89', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Все'),
  ('t89', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'All'),
  ('t90', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Черновик'),
  ('t90', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Draft'),
  ('t91', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ожидает приёмки'),
  ('t91', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Awaiting Receiving'),
  ('t92', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'В работе'),
  ('t92', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'In Progress'),
  ('t93', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Принято'),
  ('t93', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Received'),
  ('t94', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Расхождение'),
  ('t94', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Discrepancy'),
  ('t95', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Все'),
  ('t95', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'All'),
  ('t96', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Черновик'),
  ('t96', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Draft'),
  ('t97', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'В резерве'),
  ('t97', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Reserved'),
  ('t98', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Комплектуется'),
  ('t98', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Picking'),
  ('t99', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отгружен'),
  ('t99', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Shipped'),
  ('t100', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Задержка'),
  ('t100', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Delayed'),
  ('t101', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Все'),
  ('t101', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'All'),
  ('t102', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сырьё'),
  ('t102', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Raw Materials'),
  ('t103', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Мясная продукция'),
  ('t103', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Meat Products'),
  ('t104', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Молочная продукция'),
  ('t104', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Dairy Products'),
  ('t105', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Готовые наборы'),
  ('t105', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Ready-Made Kits'),
  ('t106', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Все'),
  ('t106', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'All'),
  ('t107', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сырьё'),
  ('t107', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Raw Materials'),
  ('t108', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Мясная продукция'),
  ('t108', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Meat Products'),
  ('t109', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Молочная продукция'),
  ('t109', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Dairy Products'),
  ('t110', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Готовые наборы'),
  ('t110', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Ready-Made Kits'),
  ('t111', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Все'),
  ('t111', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'All'),
  ('t112', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Оплачен'),
  ('t112', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Paid'),
  ('t113', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ожидает оплаты'),
  ('t113', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Awaiting Payment'),
  ('t114', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Просрочен'),
  ('t114', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Overdue'),
  ('t115', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Все'),
  ('t115', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'All'),
  ('t116', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Остатки на складе'),
  ('t116', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Stock on Hand'),
  ('t117', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Приёмка'),
  ('t117', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Receiving'),
  ('t118', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отгрузки'),
  ('t118', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Shipments'),
  ('t119', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Оказанные услуги'),
  ('t119', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Services Rendered'),
  ('t120', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Код'),
  ('t120', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Code'),
  ('t121', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наименование товара'),
  ('t121', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Item Name'),
  ('t122', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ожидается, шт'),
  ('t122', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Expected, pcs'),
  ('t123', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Принято, шт'),
  ('t123', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Received, pcs'),
  ('t124', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Брак, шт'),
  ('t124', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Defect, pcs'),
  ('t125', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Партия / срок годности'),
  ('t125', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Batch / Expiry'),
  ('t126', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Код'),
  ('t126', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Code'),
  ('t127', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наименование товара'),
  ('t127', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Item Name'),
  ('t128', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Заказано, шт'),
  ('t128', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Ordered, pcs'),
  ('t129', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Собрано, шт'),
  ('t129', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Picked, pcs'),
  ('t130', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Вес, кг'),
  ('t130', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Weight, kg'),
  ('t131', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус позиции'),
  ('t131', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Line Status'),
  ('t132', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Партия'),
  ('t132', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Batch'),
  ('t133', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Срок годности'),
  ('t133', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Expiry Date'),
  ('t134', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Кол-во, шт'),
  ('t134', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Qty, pcs'),
  ('t135', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Место хранения'),
  ('t135', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Storage Location'),
  ('t136', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Качество'),
  ('t136', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Quality'),
  ('t137', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата'),
  ('t137', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Date'),
  ('t138', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Документ'),
  ('t138', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Document'),
  ('t139', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Операция'),
  ('t139', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Operation'),
  ('t140', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Кол-во, шт'),
  ('t140', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Qty, pcs'),
  ('t141', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Услуга'),
  ('t141', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Service'),
  ('t142', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Тариф'),
  ('t142', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Rate'),
  ('t143', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ед. измерения'),
  ('t143', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Unit of Measure'),
  ('t144', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Периодичность'),
  ('t144', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Frequency'),
  ('t145', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Услуга'),
  ('t145', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Service'),
  ('t146', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Кол-во операций'),
  ('t146', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Operation Count'),
  ('t147', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Объём'),
  ('t147', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Volume'),
  ('t148', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сумма, KZT'),
  ('t148', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Amount, KZT'),
  ('t149', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Номер счёта'),
  ('t149', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Invoice Number'),
  ('t150', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Период'),
  ('t150', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Period'),
  ('t151', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата выставления'),
  ('t151', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Issue Date'),
  ('t152', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сумма без НДС'),
  ('t152', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Amount excl. VAT'),
  ('t153', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Сумма с НДС'),
  ('t153', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Amount incl. VAT'),
  ('t154', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t154', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t155', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Наименование отчёта'),
  ('t155', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Report Name'),
  ('t156', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Период'),
  ('t156', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Period'),
  ('t157', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Запрошен'),
  ('t157', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Requested'),
  ('t158', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Формат'),
  ('t158', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Format'),
  ('t159', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t159', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t160', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ФИО'),
  ('t160', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Full Name'),
  ('t161', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Роль'),
  ('t161', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Role'),
  ('t162', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Организация / зона'),
  ('t162', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Organization / Scope'),
  ('t163', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Логин'),
  ('t163', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Login'),
  ('t164', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Привязка в ArtWMS'),
  ('t164', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'ArtWMS Binding'),
  ('t165', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t165', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t166', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Менеджер'),
  ('t166', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Manager'),
  ('t167', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Доступные склады'),
  ('t167', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Accessible Warehouses'),
  ('t168', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Доступные поклажедатели'),
  ('t168', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Accessible Clients'),
  ('t169', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата/время'),
  ('t169', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Date/Time'),
  ('t170', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Направление'),
  ('t170', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Direction'),
  ('t171', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Документ'),
  ('t171', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Document'),
  ('t172', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ошибка'),
  ('t172', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Error'),
  ('t173', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Статус'),
  ('t173', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Status'),
  ('t174', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дата/время'),
  ('t174', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Date/Time'),
  ('t175', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Пользователь'),
  ('t175', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'User'),
  ('t176', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Файл'),
  ('t176', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'File'),
  ('t177', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Строка'),
  ('t177', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Row'),
  ('t178', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ошибка'),
  ('t178', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Error'),
  ('t179', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Вход в 3PL Customer Portal'),
  ('t179', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Sign in to 3PL Customer Portal'),
  ('t180', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Введите логин (email) и пароль для входа'),
  ('t180', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Enter your email and password to sign in'),
  ('t181', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Email / логин'),
  ('t181', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Email / Login'),
  ('t182', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Пароль'),
  ('t182', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Password'),
  ('t183', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Войти'),
  ('t183', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Sign In'),
  ('t184', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Демо-доступ — выберите пользователя'),
  ('t184', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Demo access — select a user'),
  ('t185', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Демонстрационный прототип интерфейса — все данные вымышленные · Склад с. Байсерке, ул. Султана Бейбарыса, 1'),
  ('t185', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Demonstration UI prototype — all data is fictional · с. Байсерке, ул. Султана Бейбарыса, 1'),
  ('t186', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ДЕМО — фиктивные данные'),
  ('t186', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'DEMO — fictional data'),
  ('t187', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Пользователи'),
  ('t187', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Users'),
  ('t188', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Доступы менеджеров'),
  ('t188', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Manager Access'),
  ('t189', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ошибки интеграции'),
  ('t189', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Integration Errors'),
  ('t190', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ошибки загрузки Excel'),
  ('t190', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Excel Upload Errors'),
  ('t191', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Складские услуги'),
  ('t191', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Warehousing Services'),
  ('t192', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Обработка грузов'),
  ('t192', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Cargo Handling'),
  ('t193', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'WMS и IT-поддержка'),
  ('t193', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'WMS &amp; IT Support'),
  ('t194', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дополнительные услуги'),
  ('t194', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Additional Services'),
  ('t195', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Площадь'),
  ('t195', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Area'),
  ('t196', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Офисные помещения'),
  ('t196', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Office Space'),
  ('t197', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Температурный режим'),
  ('t197', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Temperature Control'),
  ('t198', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Безопасность'),
  ('t198', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Security'),
  ('t199', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Расположение'),
  ('t199', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Location'),
  ('t200', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Технологии'),
  ('t200', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Technology'),
  ('t201', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Склад в Алматы'),
  ('t201', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Almaty Warehouse'),
  ('t202', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Склад в Караганде'),
  ('t202', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Karaganda Warehouse'),
  ('t203', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Площадь: 6 700 м², класс B+<br/>Режим работы: пн–сб, 08:00–20:00'),
  ('t203', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Area: 6,700 m², Class B+<br/>Business Hours: Mon–Sat, 08:00–20:00'),
  ('t204', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Площадь: 4 200 м², класс B<br/>Режим работы: пн–сб, 08:00–20:00'),
  ('t204', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Area: 4,200 m², Class B<br/>Business Hours: Mon–Sat, 08:00–20:00'),
  ('t205', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Менеджер по работе с клиентами — склад Алматы'),
  ('t205', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Account Manager — Almaty Warehouse'),
  ('t206', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Тел.: 8 905 895 95 85<br/>Email: <a href="mailto:n.nazarbayev@ahlers.com">n.nazarbayev@ahlers.com</a>'),
  ('t206', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Tel.: 8 905 895 95 85<br/>Email: <a href="mailto:n.nazarbayev@ahlers.com">n.nazarbayev@ahlers.com</a>'),
  ('t207', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Менеджер по работе с клиентами — склад Караганда'),
  ('t207', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Account Manager — Karaganda Warehouse'),
  ('t208', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Тел.: 8 701 442 17 63<br/>Email: <a href="mailto:s.akhmetova@ahlers.com">s.akhmetova@ahlers.com</a>'),
  ('t208', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Tel.: 8 701 442 17 63<br/>Email: <a href="mailto:s.akhmetova@ahlers.com">s.akhmetova@ahlers.com</a>'),
  ('t209', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Европейское качество</b> — полный комплекс складских услуг по стандартам ЕС.'),
  ('t209', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>European Quality</b> — full range of warehousing services to EU standards.'),
  ('t210', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Надёжный партнёр</b> — работаем с крупными международными клиентами.'),
  ('t210', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>Reliable Partner</b> — we work with major international clients.'),
  ('t211', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Прозрачные тарифы</b> — не меняем расценки без предупреждения и не прячем условия в договоре.'),
  ('t211', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>Transparent Pricing</b> — we do not change rates without notice or hide terms in the contract.'),
  ('t212', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Бытовая техника</b> — надёжная паллетизация, обработка возвратов, проверка оборудования.'),
  ('t212', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>Household Appliances</b> — secure palletizing, returns processing, equipment inspection.'),
  ('t213', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Косметика</b> — контроль температуры и влажности, отслеживание сроков годности, промо-наборы.'),
  ('t213', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>Cosmetics</b> — temperature and humidity control, expiry tracking, promo kits.'),
  ('t214', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Запчасти</b> — чёткая маркировка и разделение паллет, обратная логистика и проверка возвратов.'),
  ('t214', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>Spare Parts</b> — clear labeling and pallet segregation, reverse logistics and returns inspection.'),
  ('t215', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<b>Текстиль</b> — хранение с сохранением формы и качества товара, быстрое масштабирование в пиковый сезон.'),
  ('t215', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<b>Textile</b> — storage that preserves shape and quality, fast scaling during peak season.'),
  ('t216', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Ожидает оплаты'),
  ('t216', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Awaiting Payment'),
  ('t217', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Оплачен'),
  ('t217', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Paid'),
  ('t218', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Оплачен'),
  ('t218', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Paid'),
  ('t219', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Просрочен'),
  ('t219', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Overdue'),
  ('t220', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Не обработано'),
  ('t220', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Not Processed'),
  ('t221', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Не обработано'),
  ('t221', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Not Processed'),
  ('t222', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Повтор запланирован'),
  ('t222', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Retry Scheduled'),
  ('t223', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="dot"></span>Обработано'),
  ('t223', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="dot"></span>Processed'),
  ('t224', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Хранение товара'),
  ('t224', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Storage'),
  ('t225', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ежедневно'),
  ('t225', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Daily'),
  ('t226', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Приёмка товара'),
  ('t226', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Goods Receiving'),
  ('t227', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'За операцию'),
  ('t227', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Per Operation'),
  ('t228', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отбор и упаковка заказа'),
  ('t228', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Order Picking &amp; Packing'),
  ('t229', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'За операцию'),
  ('t229', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Per Operation'),
  ('t230', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Погрузо-разгрузочные работы'),
  ('t230', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Loading/Unloading'),
  ('t231', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'За операцию'),
  ('t231', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Per Operation'),
  ('t232', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Кросс-докинг'),
  ('t232', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Cross-Docking'),
  ('t233', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'За операцию'),
  ('t233', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Per Operation'),
  ('t234', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Стикеровка / маркировка товара'),
  ('t234', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Labeling / Marking'),
  ('t235', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'За операцию'),
  ('t235', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Per Operation'),
  ('t236', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ответственное хранение с контролем сроков годности'),
  ('t236', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Custody Storage with Expiry Tracking'),
  ('t237', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Ежедневно'),
  ('t237', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Daily'),
  ('t238', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Формирование комплектов (сборка наборов)'),
  ('t238', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Kitting (Set Assembly)'),
  ('t239', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'За операцию'),
  ('t239', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Per Operation'),
  ('t240', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Хранение товара'),
  ('t240', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Storage'),
  ('t241', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Приёмка товара'),
  ('t241', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Goods Receiving'),
  ('t242', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Отбор и упаковка заказа'),
  ('t242', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Order Picking &amp; Packing'),
  ('t243', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Погрузо-разгрузочные работы'),
  ('t243', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Loading/Unloading'),
  ('t244', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Формирование комплектов'),
  ('t244', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Kitting'),
  ('t245', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Итого за период'),
  ('t245', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Total for Period'),
  ('t246', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge warn" data-i18n="t216"><span class="dot"></span>Ожидает оплаты</span>'),
  ('t246', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge warn"><span class="dot"></span>Awaiting Payment</span>'),
  ('t247', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Скачивается: Счёт_СЧ-000712.pdf'')">Скачать PDF</button>'),
  ('t247', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Downloading: Invoice_INV-000712.pdf'')">Download PDF</button>'),
  ('t248', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge ok" data-i18n="t217"><span class="dot"></span>Оплачен</span>'),
  ('t248', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge ok"><span class="dot"></span>Paid</span>'),
  ('t249', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Скачивается: Счёт_СЧ-000689.pdf'')">Скачать PDF</button>'),
  ('t249', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Downloading: Invoice_INV-000689.pdf'')">Download PDF</button>'),
  ('t250', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge ok" data-i18n="t218"><span class="dot"></span>Оплачен</span>'),
  ('t250', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge ok"><span class="dot"></span>Paid</span>'),
  ('t251', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Скачивается: Счёт_СЧ-000654.pdf'')">Скачать PDF</button>'),
  ('t251', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Downloading: Invoice_INV-000654.pdf'')">Download PDF</button>'),
  ('t252', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge err" data-i18n="t219"><span class="dot"></span>Просрочен</span>'),
  ('t252', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge err"><span class="dot"></span>Overdue</span>'),
  ('t253', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Скачивается: Счёт_СЧ-000611.pdf'')">Скачать PDF</button>'),
  ('t253', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<button class="btn ghost" onclick="event.stopPropagation(); notify(''Downloading: Invoice_INV-000611.pdf'')">Download PDF</button>'),
  ('t254', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Портал → WMS'),
  ('t254', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Portal → WMS'),
  ('t255', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge err" data-i18n="t220"><span class="dot"></span>Не обработано</span>'),
  ('t255', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge err"><span class="dot"></span>Not Processed</span>'),
  ('t256', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'WMS → Портал'),
  ('t256', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'WMS → Portal'),
  ('t257', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge err" data-i18n="t221"><span class="dot"></span>Не обработано</span>'),
  ('t257', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge err"><span class="dot"></span>Not Processed</span>'),
  ('t258', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Портал → WMS'),
  ('t258', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Portal → WMS'),
  ('t259', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Код склада не найден в схеме назначения'),
  ('t259', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Warehouse code not found in mapping schema'),
  ('t260', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge warn" data-i18n="t222"><span class="dot"></span>Повтор запланирован</span>'),
  ('t260', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge warn"><span class="dot"></span>Retry Scheduled</span>'),
  ('t261', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'WMS → Портал'),
  ('t261', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'WMS → Portal'),
  ('t262', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), '<span class="badge ok" data-i18n="t223"><span class="dot"></span>Обработано</span>'),
  ('t262', (SELECT locale_id FROM dictionary_locale WHERE code='en'), '<span class="badge ok"><span class="dot"></span>Processed</span>'),
  ('t263', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Заявка_приход_29.07.xlsx'),
  ('t263', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Receipt_Request_29.07.xlsx'),
  ('t264', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Не заполнено поле «Ожидаемая дата»'),
  ('t264', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Field "Expected Date" is not filled in'),
  ('t265', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Номенклатура_пол_покрытия.xlsx'),
  ('t265', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Flooring_Products.xlsx'),
  ('t266', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Дублирующийся коммерческий код товара 00944450040'),
  ('t266', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Duplicate SAP code 00944450040'),
  ('t267', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Заказ_отгрузка_28.07.xlsx'),
  ('t267', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Shipment_Order_28.07.xlsx'),
  ('t268', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Некорректный формат числа в столбце «Вес, кг»'),
  ('t268', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Invalid number format in column "Weight, kg"'),
  ('t269', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Полное наименование'),
  ('t269', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Full Legal Name'),
  ('t270', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'БИН'),
  ('t270', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Tax ID (BIN)'),
  ('t271', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Юридический адрес'),
  ('t271', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Legal Address'),
  ('t272', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Банк'),
  ('t272', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Bank'),
  ('t273', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'ИИК'),
  ('t273', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'IBAN'),
  ('t274', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Общая почта'),
  ('t274', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'General Email'),
  ('t275', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Жалобы и предложения'),
  ('t275', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Feedback &amp; Complaints')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- Вкладка «Перемещения» в прототипе подписана мимо словаря — ключа на неё
-- не было. Заводим, иначе на любом языке, кроме русского и английского,
-- она осталась бы русской.
INSERT INTO ui_text (text_key, locale_id, value) VALUES
  ('nav.transfers', (SELECT locale_id FROM dictionary_locale WHERE code='en'), 'Transfers'),
  ('nav.transfers', (SELECT locale_id FROM dictionary_locale WHERE code='ru'), 'Перемещения')
ON CONFLICT (text_key, locale_id) DO NOTHING;

-- =====================================================================
-- 4. Чтение с откатом на язык по умолчанию
-- =====================================================================
-- Непереведённый ключ не должен превращаться в пустое место на экране:
-- отдаём значение на языке по умолчанию. Нидерландский и казахский пока
-- не заполнены — портал на них покажет английский, а не пустые кнопки.
CREATE OR REPLACE FUNCTION ui_texts(p_locale text)
RETURNS TABLE (text_key varchar, value text)
LANGUAGE sql STABLE SET search_path = dbo AS $$
  SELECT t.text_key,
         COALESCE(
           max(t.value) FILTER (WHERE l.code = p_locale),
           max(t.value) FILTER (WHERE l.is_default)
         ) AS value
    FROM ui_text t
    JOIN dictionary_locale l ON l.locale_id = t.locale_id
   GROUP BY t.text_key
  HAVING COALESCE(
           max(t.value) FILTER (WHERE l.code = p_locale),
           max(t.value) FILTER (WHERE l.is_default)
         ) IS NOT NULL
$$;
GRANT EXECUTE ON FUNCTION ui_texts(text) TO portal_app;

-- Список языков для переключателя: код, название и признак «по умолчанию».
CREATE OR REPLACE VIEW v_locales AS
SELECT l.locale_id, l.code, l.name, l.is_default, l.sort_order
FROM dictionary_locale l
WHERE l.is_active;
ALTER VIEW v_locales SET (security_invoker = true);
GRANT SELECT ON v_locales TO portal_app;

-- Полная таблица для экрана ведения переводов: ключ и значения по языкам
-- в одной строке — так видно, чего не хватает.
CREATE OR REPLACE FUNCTION ui_text_matrix()
RETURNS TABLE (text_key varchar, locale_code varchar, value text)
LANGUAGE sql STABLE SET search_path = dbo AS $$
  SELECT k.text_key, l.code, t.value
    FROM (SELECT DISTINCT text_key FROM ui_text) k
   CROSS JOIN dictionary_locale l
    LEFT JOIN ui_text t ON t.text_key = k.text_key AND t.locale_id = l.locale_id
   WHERE l.is_active
   ORDER BY k.text_key, l.sort_order
$$;
GRANT EXECUTE ON FUNCTION ui_text_matrix() TO portal_app;

-- Запись перевода: пустое значение означает «перевода нет», и строку
-- надо убрать, а не хранить пустую — иначе откат на язык по умолчанию
-- не сработает и на экране будет пусто.
CREATE OR REPLACE FUNCTION ui_text_save(p_key text, p_locale text, p_value text)
RETURNS void
LANGUAGE plpgsql SET search_path = dbo AS $$
DECLARE v_locale smallint;
BEGIN
  SELECT locale_id INTO v_locale FROM dictionary_locale WHERE code = p_locale;
  IF v_locale IS NULL THEN
    RAISE EXCEPTION 'Неизвестный язык: %', p_locale;
  END IF;
  IF p_value IS NULL OR btrim(p_value) = '' THEN
    DELETE FROM ui_text WHERE text_key = p_key AND locale_id = v_locale;
  ELSE
    INSERT INTO ui_text (text_key, locale_id, value) VALUES (p_key, v_locale, p_value)
    ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
  END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION ui_text_save(text, text, text) TO portal_app;

-- Язык пользователя: NULL сбрасывает на язык портала.
CREATE OR REPLACE FUNCTION user_set_locale(p_user_id uuid, p_locale text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = dbo AS $$
DECLARE v_locale smallint;
BEGIN
  IF p_locale IS NOT NULL THEN
    SELECT locale_id INTO v_locale FROM dictionary_locale WHERE code = p_locale AND is_active;
    IF v_locale IS NULL THEN
      RAISE EXCEPTION 'Неизвестный язык: %', p_locale;
    END IF;
  END IF;
  UPDATE app_user SET locale_id = v_locale WHERE user_id = p_user_id;
END;
$$;
GRANT EXECUTE ON FUNCTION user_set_locale(uuid, text) TO portal_app;
