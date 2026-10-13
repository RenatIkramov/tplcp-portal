
/* ============== I18N ============== */
const I18N_MAP = {"t0": {"ru": "Заказы", "en": "Orders"}, "t1": {"ru": "Поступления", "en": "Receipts"}, "t2": {"ru": "Запасы", "en": "Stock"}, "t3": {"ru": "Товары", "en": "Products"}, "t4": {"ru": "Биллинг", "en": "Billing"}, "t5": {"ru": "Отчёты", "en": "Reports"}, "t6": {"ru": "⚙ Администрирование", "en": "⚙ Administration"}, "t7": {"ru": "Личный кабинет", "en": "My Account"}, "t8": {"ru": "О нас", "en": "About Us"}, "t9": {"ru": "Контакты", "en": "Contacts"}, "t10": {"ru": "Выход", "en": "Log Out"}, "t11": {"ru": "Поступления <span class=\"count\" id=\"receiptsCount\"></span>", "en": "Receipts <span class=\"count\" id=\"receiptsCount\"></span>"}, "t12": {"ru": "Заказы на отгрузку <span class=\"count\" id=\"ordersCount\"></span>", "en": "Shipment Orders <span class=\"count\" id=\"ordersCount\"></span>"}, "t13": {"ru": "Запасы <span class=\"count\" id=\"stockCount\"></span>", "en": "Stock <span class=\"count\" id=\"stockCount\"></span>"}, "t14": {"ru": "Товары <span class=\"count\" id=\"skuCount\"></span>", "en": "Products <span class=\"count\" id=\"skuCount\"></span>"}, "t15": {"ru": "Биллинг", "en": "Billing"}, "t16": {"ru": "Отчёты <span class=\"count\" id=\"reportsCount\"></span>", "en": "Reports <span class=\"count\" id=\"reportsCount\"></span>"}, "t17": {"ru": "Администрирование портала", "en": "Portal Administration"}, "t18": {"ru": "Контакты", "en": "Contacts"}, "t19": {"ru": "Состав документа", "en": "Document Contents"}, "t20": {"ru": "Транспорт", "en": "Transport"}, "t21": {"ru": "Состав заказа", "en": "Order Contents"}, "t22": {"ru": "Транспорт и доставка", "en": "Transport &amp; Delivery"}, "t23": {"ru": "Детализация", "en": "Details"}, "t24": {"ru": "История движения", "en": "Movement History"}, "t25": {"ru": "Перечень оказываемых услуг с тарифами", "en": "Service Price List"}, "t26": {"ru": "Отчёты по оказанным услугам", "en": "Service Reports"}, "t27": {"ru": "Счета, выставленные бухгалтерией", "en": "Invoices Issued by Accounting"}, "t28": {"ru": "Пользователи портала", "en": "Portal Users"}, "t29": {"ru": "Назначение складов и поклажедателей менеджерам", "en": "Assigning Warehouses and Clients to Managers"}, "t30": {"ru": "Ошибки интеграционных сообщений (портал ↔ ArtWMS)", "en": "Integration Message Errors (Portal ↔ ArtWMS)"}, "t31": {"ru": "Ошибки загрузки данных из Excel", "en": "Excel Data Upload Errors"}, "t32": {"ru": "О компании", "en": "About the Company"}, "t33": {"ru": "Наши услуги", "en": "Our Services"}, "t34": {"ru": "Наши преимущества", "en": "Our Advantages"}, "t35": {"ru": "Склад в Алматы", "en": "Almaty Warehouse"}, "t36": {"ru": "Офисное пространство", "en": "Office Space"}, "t37": {"ru": "Отраслевые решения", "en": "Industry Solutions"}, "t38": {"ru": "Реквизиты компании", "en": "Company Details"}, "t39": {"ru": "Склады", "en": "Warehouses"}, "t40": {"ru": "Персональные менеджеры", "en": "Account Managers"}, "t41": {"ru": "Есть вопрос или предложение?", "en": "Have a question or suggestion?"}, "t42": {"ru": "+ Создать новый", "en": "+ Create New"}, "t43": {"ru": "Загрузить из Excel", "en": "Import from Excel"}, "t44": {"ru": "Скачать шаблон Excel", "en": "Download Excel Template"}, "t45": {"ru": "Экспортировать в Excel", "en": "Export to Excel"}, "t46": {"ru": "+ Создать новый", "en": "+ Create New"}, "t47": {"ru": "Загрузить из Excel", "en": "Import from Excel"}, "t48": {"ru": "Скачать шаблон Excel", "en": "Download Excel Template"}, "t49": {"ru": "Экспортировать в Excel", "en": "Export to Excel"}, "t50": {"ru": "+ Создать новый", "en": "+ Create New"}, "t51": {"ru": "Загрузить из Excel", "en": "Import from Excel"}, "t52": {"ru": "Скачать шаблон Excel", "en": "Download Excel Template"}, "t53": {"ru": "Экспортировать в Excel", "en": "Export to Excel"}, "t54": {"ru": "+ Создать новый", "en": "+ Create New"}, "t55": {"ru": "Загрузить из Excel", "en": "Import from Excel"}, "t56": {"ru": "Скачать шаблон Excel", "en": "Download Excel Template"}, "t57": {"ru": "+ Создать пользователя", "en": "+ Create User"}, "t58": {"ru": "Номер документа", "en": "Document Number"}, "t59": {"ru": "Поставщик", "en": "Supplier"}, "t60": {"ru": "Дата с", "en": "Date From"}, "t61": {"ru": "Дата по", "en": "Date To"}, "t62": {"ru": "Статус", "en": "Status"}, "t63": {"ru": "Номер заказа", "en": "Order Number"}, "t64": {"ru": "Грузополучатель", "en": "Consignee"}, "t65": {"ru": "Дата отгрузки с", "en": "Ship Date From"}, "t66": {"ru": "Дата отгрузки по", "en": "Ship Date To"}, "t67": {"ru": "Статус", "en": "Status"}, "t68": {"ru": "Код / артикул", "en": "Code / SKU"}, "t69": {"ru": "Наименование", "en": "Name"}, "t70": {"ru": "Группа", "en": "Category"}, "t71": {"ru": "Код / артикул", "en": "Code / SKU"}, "t72": {"ru": "Наименование", "en": "Name"}, "t73": {"ru": "Группа", "en": "Category"}, "t74": {"ru": "Период с", "en": "Period From"}, "t75": {"ru": "Период по", "en": "Period To"}, "t76": {"ru": "Статус", "en": "Status"}, "t77": {"ru": "Тип отчёта", "en": "Report Type"}, "t78": {"ru": "Период с", "en": "Period From"}, "t79": {"ru": "Период по", "en": "Period To"}, "t80": {"ru": "⚙ Колонки", "en": "⚙ Columns"}, "t81": {"ru": "Экспорт в Excel", "en": "Export to Excel"}, "t82": {"ru": "⚙ Колонки", "en": "⚙ Columns"}, "t83": {"ru": "Экспорт в Excel", "en": "Export to Excel"}, "t84": {"ru": "⚙ Колонки", "en": "⚙ Columns"}, "t85": {"ru": "Экспорт в Excel", "en": "Export to Excel"}, "t86": {"ru": "⚙ Колонки", "en": "⚙ Columns"}, "t87": {"ru": "Экспорт в Excel", "en": "Export to Excel"}, "t88": {"ru": "Сформировать отчёт", "en": "Generate Report"}, "t89": {"ru": "Все", "en": "All"}, "t90": {"ru": "Черновик", "en": "Draft"}, "t91": {"ru": "Ожидает приёмки", "en": "Awaiting Receiving"}, "t92": {"ru": "В работе", "en": "In Progress"}, "t93": {"ru": "Принято", "en": "Received"}, "t94": {"ru": "Расхождение", "en": "Discrepancy"}, "t95": {"ru": "Все", "en": "All"}, "t96": {"ru": "Черновик", "en": "Draft"}, "t97": {"ru": "В резерве", "en": "Reserved"}, "t98": {"ru": "Комплектуется", "en": "Picking"}, "t99": {"ru": "Отгружен", "en": "Shipped"}, "t100": {"ru": "Задержка", "en": "Delayed"}, "t101": {"ru": "Все", "en": "All"}, "t102": {"ru": "Сырьё", "en": "Raw Materials"}, "t103": {"ru": "Мясная продукция", "en": "Meat Products"}, "t104": {"ru": "Молочная продукция", "en": "Dairy Products"}, "t105": {"ru": "Готовые наборы", "en": "Ready-Made Kits"}, "t106": {"ru": "Все", "en": "All"}, "t107": {"ru": "Сырьё", "en": "Raw Materials"}, "t108": {"ru": "Мясная продукция", "en": "Meat Products"}, "t109": {"ru": "Молочная продукция", "en": "Dairy Products"}, "t110": {"ru": "Готовые наборы", "en": "Ready-Made Kits"}, "t111": {"ru": "Все", "en": "All"}, "t112": {"ru": "Оплачен", "en": "Paid"}, "t113": {"ru": "Ожидает оплаты", "en": "Awaiting Payment"}, "t114": {"ru": "Просрочен", "en": "Overdue"}, "t115": {"ru": "Все", "en": "All"}, "t116": {"ru": "Остатки на складе", "en": "Stock on Hand"}, "t117": {"ru": "Приёмка", "en": "Receiving"}, "t118": {"ru": "Отгрузки", "en": "Shipments"}, "t119": {"ru": "Оказанные услуги", "en": "Services Rendered"}, "t120": {"ru": "Код", "en": "Code"}, "t121": {"ru": "Наименование товара", "en": "Item Name"}, "t122": {"ru": "Ожидается, шт", "en": "Expected, pcs"}, "t123": {"ru": "Принято, шт", "en": "Received, pcs"}, "t124": {"ru": "Брак, шт", "en": "Defect, pcs"}, "t125": {"ru": "Партия / срок годности", "en": "Batch / Expiry"}, "t126": {"ru": "Код", "en": "Code"}, "t127": {"ru": "Наименование товара", "en": "Item Name"}, "t128": {"ru": "Заказано, шт", "en": "Ordered, pcs"}, "t129": {"ru": "Собрано, шт", "en": "Picked, pcs"}, "t130": {"ru": "Вес, кг", "en": "Weight, kg"}, "t131": {"ru": "Статус позиции", "en": "Line Status"}, "t132": {"ru": "Партия", "en": "Batch"}, "t133": {"ru": "Срок годности", "en": "Expiry Date"}, "t134": {"ru": "Кол-во, шт", "en": "Qty, pcs"}, "t135": {"ru": "Место хранения", "en": "Storage Location"}, "t136": {"ru": "Качество", "en": "Quality"}, "t137": {"ru": "Дата", "en": "Date"}, "t138": {"ru": "Документ", "en": "Document"}, "t139": {"ru": "Операция", "en": "Operation"}, "t140": {"ru": "Кол-во, шт", "en": "Qty, pcs"}, "t141": {"ru": "Услуга", "en": "Service"}, "t142": {"ru": "Тариф", "en": "Rate"}, "t143": {"ru": "Ед. измерения", "en": "Unit of Measure"}, "t144": {"ru": "Периодичность", "en": "Frequency"}, "t145": {"ru": "Услуга", "en": "Service"}, "t146": {"ru": "Кол-во операций", "en": "Operation Count"}, "t147": {"ru": "Объём", "en": "Volume"}, "t148": {"ru": "Сумма, KZT", "en": "Amount, KZT"}, "t149": {"ru": "Номер счёта", "en": "Invoice Number"}, "t150": {"ru": "Период", "en": "Period"}, "t151": {"ru": "Дата выставления", "en": "Issue Date"}, "t152": {"ru": "Сумма без НДС", "en": "Amount excl. VAT"}, "t153": {"ru": "Сумма с НДС", "en": "Amount incl. VAT"}, "t154": {"ru": "Статус", "en": "Status"}, "t155": {"ru": "Наименование отчёта", "en": "Report Name"}, "t156": {"ru": "Период", "en": "Period"}, "t157": {"ru": "Запрошен", "en": "Requested"}, "t158": {"ru": "Формат", "en": "Format"}, "t159": {"ru": "Статус", "en": "Status"}, "t160": {"ru": "ФИО", "en": "Full Name"}, "t161": {"ru": "Роль", "en": "Role"}, "t162": {"ru": "Организация / зона", "en": "Organization / Scope"}, "t163": {"ru": "Логин", "en": "Login"}, "t164": {"ru": "Привязка в ArtWMS", "en": "ArtWMS Binding"}, "t165": {"ru": "Статус", "en": "Status"}, "t166": {"ru": "Менеджер", "en": "Manager"}, "t167": {"ru": "Доступные склады", "en": "Accessible Warehouses"}, "t168": {"ru": "Доступные поклажедатели", "en": "Accessible Clients"}, "t169": {"ru": "Дата/время", "en": "Date/Time"}, "t170": {"ru": "Направление", "en": "Direction"}, "t171": {"ru": "Документ", "en": "Document"}, "t172": {"ru": "Ошибка", "en": "Error"}, "t173": {"ru": "Статус", "en": "Status"}, "t174": {"ru": "Дата/время", "en": "Date/Time"}, "t175": {"ru": "Пользователь", "en": "User"}, "t176": {"ru": "Файл", "en": "File"}, "t177": {"ru": "Строка", "en": "Row"}, "t178": {"ru": "Ошибка", "en": "Error"}, "t179": {"ru": "Вход в 3PL Customer Portal", "en": "Sign in to 3PL Customer Portal"}, "t180": {"ru": "Введите логин (email) и пароль для входа", "en": "Enter your email and password to sign in"}, "t181": {"ru": "Email / логин", "en": "Email / Login"}, "t182": {"ru": "Пароль", "en": "Password"}, "t183": {"ru": "Войти", "en": "Sign In"}, "t184": {"ru": "Демо-доступ — выберите пользователя", "en": "Demo access — select a user"}, "t185": {"ru": "Демонстрационный прототип интерфейса — все данные вымышленные · Склад с. Байсерке, ул. Султана Бейбарыса, 1", "en": "Demonstration UI prototype — all data is fictional · с. Байсерке, ул. Султана Бейбарыса, 1"}, "t186": {"ru": "ДЕМО — фиктивные данные", "en": "DEMO — fictional data"}, "t187": {"ru": "Пользователи", "en": "Users"}, "t188": {"ru": "Доступы менеджеров", "en": "Manager Access"}, "t189": {"ru": "Ошибки интеграции", "en": "Integration Errors"}, "t190": {"ru": "Ошибки загрузки Excel", "en": "Excel Upload Errors"}, "t191": {"ru": "Складские услуги", "en": "Warehousing Services"}, "t192": {"ru": "Обработка грузов", "en": "Cargo Handling"}, "t193": {"ru": "WMS и IT-поддержка", "en": "WMS &amp; IT Support"}, "t194": {"ru": "Дополнительные услуги", "en": "Additional Services"}, "t195": {"ru": "Площадь", "en": "Area"}, "t196": {"ru": "Офисные помещения", "en": "Office Space"}, "t197": {"ru": "Температурный режим", "en": "Temperature Control"}, "t198": {"ru": "Безопасность", "en": "Security"}, "t199": {"ru": "Расположение", "en": "Location"}, "t200": {"ru": "Технологии", "en": "Technology"}, "t201": {"ru": "Склад в Алматы", "en": "Almaty Warehouse"}, "t202": {"ru": "Склад в Караганде", "en": "Karaganda Warehouse"}, "t203": {"ru": "Площадь: 6 700 м², класс B+<br/>Режим работы: пн–сб, 08:00–20:00", "en": "Area: 6,700 m², Class B+<br/>Business Hours: Mon–Sat, 08:00–20:00"}, "t204": {"ru": "Площадь: 4 200 м², класс B<br/>Режим работы: пн–сб, 08:00–20:00", "en": "Area: 4,200 m², Class B<br/>Business Hours: Mon–Sat, 08:00–20:00"}, "t205": {"ru": "Менеджер по работе с клиентами — склад Алматы", "en": "Account Manager — Almaty Warehouse"}, "t206": {"ru": "Тел.: 8 905 895 95 85<br/>Email: <a href=\"mailto:n.nazarbayev@ahlers.com\">n.nazarbayev@ahlers.com</a>", "en": "Tel.: 8 905 895 95 85<br/>Email: <a href=\"mailto:n.nazarbayev@ahlers.com\">n.nazarbayev@ahlers.com</a>"}, "t207": {"ru": "Менеджер по работе с клиентами — склад Караганда", "en": "Account Manager — Karaganda Warehouse"}, "t208": {"ru": "Тел.: 8 701 442 17 63<br/>Email: <a href=\"mailto:s.akhmetova@ahlers.com\">s.akhmetova@ahlers.com</a>", "en": "Tel.: 8 701 442 17 63<br/>Email: <a href=\"mailto:s.akhmetova@ahlers.com\">s.akhmetova@ahlers.com</a>"}, "t209": {"ru": "<b>Европейское качество</b> — полный комплекс складских услуг по стандартам ЕС.", "en": "<b>European Quality</b> — full range of warehousing services to EU standards."}, "t210": {"ru": "<b>Надёжный партнёр</b> — работаем с крупными международными клиентами.", "en": "<b>Reliable Partner</b> — we work with major international clients."}, "t211": {"ru": "<b>Прозрачные тарифы</b> — не меняем расценки без предупреждения и не прячем условия в договоре.", "en": "<b>Transparent Pricing</b> — we do not change rates without notice or hide terms in the contract."}, "t212": {"ru": "<b>Бытовая техника</b> — надёжная паллетизация, обработка возвратов, проверка оборудования.", "en": "<b>Household Appliances</b> — secure palletizing, returns processing, equipment inspection."}, "t213": {"ru": "<b>Косметика</b> — контроль температуры и влажности, отслеживание сроков годности, промо-наборы.", "en": "<b>Cosmetics</b> — temperature and humidity control, expiry tracking, promo kits."}, "t214": {"ru": "<b>" + T('stock.option.spare_parts') + "</b> — чёткая маркировка и разделение паллет, обратная логистика и проверка возвратов.", "en": "<b>Spare Parts</b> — clear labeling and pallet segregation, reverse logistics and returns inspection."}, "t215": {"ru": "<b>Текстиль</b> — хранение с сохранением формы и качества товара, быстрое масштабирование в пиковый сезон.", "en": "<b>Textile</b> — storage that preserves shape and quality, fast scaling during peak season."}, "t216": {"ru": "<span class=\"dot\"></span>Ожидает оплаты", "en": "<span class=\"dot\"></span>Awaiting Payment"}, "t217": {"ru": "<span class=\"dot\"></span>Оплачен", "en": "<span class=\"dot\"></span>Paid"}, "t218": {"ru": "<span class=\"dot\"></span>Оплачен", "en": "<span class=\"dot\"></span>Paid"}, "t219": {"ru": "<span class=\"dot\"></span>Просрочен", "en": "<span class=\"dot\"></span>Overdue"}, "t220": {"ru": "<span class=\"dot\"></span>Не обработано", "en": "<span class=\"dot\"></span>Not Processed"}, "t221": {"ru": "<span class=\"dot\"></span>Не обработано", "en": "<span class=\"dot\"></span>Not Processed"}, "t222": {"ru": "<span class=\"dot\"></span>Повтор запланирован", "en": "<span class=\"dot\"></span>Retry Scheduled"}, "t223": {"ru": "<span class=\"dot\"></span>Обработано", "en": "<span class=\"dot\"></span>Processed"}, "t224": {"ru": "Хранение товара", "en": "Storage"}, "t225": {"ru": "Ежедневно", "en": "Daily"}, "t226": {"ru": "Приёмка товара", "en": "Goods Receiving"}, "t227": {"ru": "За операцию", "en": "Per Operation"}, "t228": {"ru": "Отбор и упаковка заказа", "en": "Order Picking &amp; Packing"}, "t229": {"ru": "За операцию", "en": "Per Operation"}, "t230": {"ru": "Погрузо-разгрузочные работы", "en": "Loading/Unloading"}, "t231": {"ru": "За операцию", "en": "Per Operation"}, "t232": {"ru": "Кросс-докинг", "en": "Cross-Docking"}, "t233": {"ru": "За операцию", "en": "Per Operation"}, "t234": {"ru": "Стикеровка / маркировка товара", "en": "Labeling / Marking"}, "t235": {"ru": "За операцию", "en": "Per Operation"}, "t236": {"ru": "Ответственное хранение с контролем сроков годности", "en": "Custody Storage with Expiry Tracking"}, "t237": {"ru": "Ежедневно", "en": "Daily"}, "t238": {"ru": "Формирование комплектов (сборка наборов)", "en": "Kitting (Set Assembly)"}, "t239": {"ru": "За операцию", "en": "Per Operation"}, "t240": {"ru": "Хранение товара", "en": "Storage"}, "t241": {"ru": "Приёмка товара", "en": "Goods Receiving"}, "t242": {"ru": "Отбор и упаковка заказа", "en": "Order Picking &amp; Packing"}, "t243": {"ru": "Погрузо-разгрузочные работы", "en": "Loading/Unloading"}, "t244": {"ru": "Формирование комплектов", "en": "Kitting"}, "t245": {"ru": "Итого за период", "en": "Total for Period"}, "t246": {"ru": "<span class=\"badge warn\" data-i18n=\"t216\"><span class=\"dot\"></span>" + T('billing.option.awaiting_payment') + "</span>", "en": "<span class=\"badge warn\"><span class=\"dot\"></span>Awaiting Payment</span>"}, "t247": {"ru": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Скачивается: Счёт_СЧ-000712.pdf')\">" + T('common.text.download_pdf') + "</button>", "en": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Downloading: Invoice_INV-000712.pdf')\">Download PDF</button>"}, "t248": {"ru": "<span class=\"badge ok\" data-i18n=\"t217\"><span class=\"dot\"></span>" + T('billing.option.paid') + "</span>", "en": "<span class=\"badge ok\"><span class=\"dot\"></span>Paid</span>"}, "t249": {"ru": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Скачивается: Счёт_СЧ-000689.pdf')\">" + T('common.text.download_pdf') + "</button>", "en": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Downloading: Invoice_INV-000689.pdf')\">Download PDF</button>"}, "t250": {"ru": "<span class=\"badge ok\" data-i18n=\"t218\"><span class=\"dot\"></span>" + T('billing.option.paid') + "</span>", "en": "<span class=\"badge ok\"><span class=\"dot\"></span>Paid</span>"}, "t251": {"ru": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Скачивается: Счёт_СЧ-000654.pdf')\">" + T('common.text.download_pdf') + "</button>", "en": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Downloading: Invoice_INV-000654.pdf')\">Download PDF</button>"}, "t252": {"ru": "<span class=\"badge err\" data-i18n=\"t219\"><span class=\"dot\"></span>" + T('billing.option.overdue') + "</span>", "en": "<span class=\"badge err\"><span class=\"dot\"></span>Overdue</span>"}, "t253": {"ru": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Скачивается: Счёт_СЧ-000611.pdf')\">" + T('common.text.download_pdf') + "</button>", "en": "<button class=\"btn ghost\" onclick=\"event.stopPropagation(); notify('Downloading: Invoice_INV-000611.pdf')\">Download PDF</button>"}, "t254": {"ru": "Портал → WMS", "en": "Portal → WMS"}, "t255": {"ru": "<span class=\"badge err\" data-i18n=\"t220\"><span class=\"dot\"></span>Не обработано</span>", "en": "<span class=\"badge err\"><span class=\"dot\"></span>Not Processed</span>"}, "t256": {"ru": "WMS → Портал", "en": "WMS → Portal"}, "t257": {"ru": "<span class=\"badge err\" data-i18n=\"t221\"><span class=\"dot\"></span>Не обработано</span>", "en": "<span class=\"badge err\"><span class=\"dot\"></span>Not Processed</span>"}, "t258": {"ru": "Портал → WMS", "en": "Portal → WMS"}, "t259": {"ru": "Код склада не найден в схеме назначения", "en": "Warehouse code not found in mapping schema"}, "t260": {"ru": "<span class=\"badge warn\" data-i18n=\"t222\"><span class=\"dot\"></span>Повтор запланирован</span>", "en": "<span class=\"badge warn\"><span class=\"dot\"></span>Retry Scheduled</span>"}, "t261": {"ru": "WMS → Портал", "en": "WMS → Portal"}, "t262": {"ru": "<span class=\"badge ok\" data-i18n=\"t223\"><span class=\"dot\"></span>Обработано</span>", "en": "<span class=\"badge ok\"><span class=\"dot\"></span>Processed</span>"}, "t263": {"ru": "Заявка_приход_29.07.xlsx", "en": "Receipt_Request_29.07.xlsx"}, "t264": {"ru": "Не заполнено поле «Ожидаемая дата»", "en": "Field \"Expected Date\" is not filled in"}, "t265": {"ru": "Номенклатура_пол_покрытия.xlsx", "en": "Flooring_Products.xlsx"}, "t266": {"ru": "Дублирующийся коммерческий код товара 00944450040", "en": "Duplicate SAP code 00944450040"}, "t267": {"ru": "Заказ_отгрузка_28.07.xlsx", "en": "Shipment_Order_28.07.xlsx"}, "t268": {"ru": "Некорректный формат числа в столбце «Вес, кг»", "en": "Invalid number format in column \"Weight, kg\""}, "t269": {"ru": "Полное наименование", "en": "Full Legal Name"}, "t270": {"ru": "БИН", "en": "Tax ID (BIN)"}, "t271": {"ru": "Юридический адрес", "en": "Legal Address"}, "t272": {"ru": "Банк", "en": "Bank"}, "t273": {"ru": "ИИК", "en": "IBAN"}, "t274": {"ru": "Общая почта", "en": "General Email"}, "t275": {"ru": "Жалобы и предложения", "en": "Feedback &amp; Complaints"}};

let currentLang = 'ru';
function applyLanguage(lang){
  currentLang = lang;
  document.querySelectorAll('[data-i18n]').forEach(el=>{
    const entry = I18N_MAP[el.getAttribute('data-i18n')];
    if(entry && entry[lang]) el.innerHTML = entry[lang];
  });
  const NAV_I18N = {
    orders:{ru:'Заказы',en:'Orders'}, receipts:{ru:'Поступления',en:'Receipts'},
    transfers:{ru:'Перемещения',en:'Transfers'}, stock:{ru:'Запасы',en:'Stock'},
    sku:{ru:'Товары',en:'Products'}, billing:{ru:'Биллинг',en:'Billing'},
    reports:{ru:'Отчёты',en:'Reports'}, admin:{ru:'⚙ Администрирование',en:'⚙ Administration'},
  };
  document.querySelectorAll('nav.tabs button[data-tab]').forEach(btn=>{
    const e = NAV_I18N[btn.dataset.tab];
    if(e) btn.textContent = e[lang] || e.ru;
  });
  document.querySelectorAll('.lang span').forEach(s=>{
    s.classList.toggle('active', s.textContent.trim().toLowerCase() === lang);
  });
  if(typeof renderReceipts === 'function'){
    renderReceipts(); renderOrders(); renderStock(); renderSku(); renderReports(); if(typeof renderTransfers==='function') renderTransfers();
    if(typeof renderSuppliers==='function'){ renderSuppliers(); renderConsignees(); }
  }
  if(document.getElementById('view-admin')?.classList.contains('active')){
    renderAdminUsers(); renderAdminAccess(); renderAdminDepositorAccess(); renderAdminStaffAccess();
  }
  if(document.getElementById('view-profile')?.classList.contains('active') && typeof currentUser !== 'undefined' && currentUser){
    renderProfile(currentUser);
  }
  localStorage?.setItem && null; // demo: no persistence needed
}
function setLanguage(lang){
  if(lang === 'kz'){ notify(currentLang==='en' ? 'Kazakh version is not available in this demo' : 'Казахская версия недоступна в демо-версии'); return; }
  applyLanguage(lang);
}
document.addEventListener('DOMContentLoaded', ()=>{
  document.querySelectorAll('.lang span').forEach(s=>{
    s.style.cursor = 'pointer';
    s.addEventListener('click', ()=> setLanguage(s.textContent.trim().toLowerCase()));
  });
});

/* ============== ДАННЫЕ (фиктивные) ============== */

const DEPOSITORS = {
  rogaikopyta: { id:'rogaikopyta', name:'ТОО «Рога и Копыта»' },
  boltygaiki:  { id:'boltygaiki',  name:'ТОО «Болты и Гайки»' },
  polovoygigant: { id:'polovoygigant', name:'ООО «Половой гигант»' },
};
/* Реестр складов */
const WAREHOUSES = {
  almaty: { id:'almaty', name:'Алматы' },
  karagandy: { id:'karagandy', name:'Караганда' },
};
/* Какие склады доступны каждому поклажедателю (редактируется в администрировании) */
const DEPOSITOR_WAREHOUSES = {
  rogaikopyta: ['almaty','karagandy'],
  boltygaiki: ['almaty'],
  polovoygigant: ['karagandy'],
};
let currentWarehouseFilter = 'all';
/* Скрытый служебный "поклажедатель" — используется, когда у менеджера не осталось ни одного доступа */
const EMPTY_DEPOSITOR = { id:'none', name:'Нет доступных поклажедателей' };
const CHOOSE_DEPOSITOR_PLACEHOLDER = { id:'choose', name:'Выберите поклажедателя' };
const ALL_DEPOSITORS = Object.assign({}, DEPOSITORS, { none: EMPTY_DEPOSITOR, choose: CHOOSE_DEPOSITOR_PLACEHOLDER });
let currentDepositorId = 'rogaikopyta';

/* ---------- Рога и Копыта ---------- */
const suppliers_rogaikopyta = ["ИП «Козлов»"];
const consignees_rogaikopyta = ["ИП «Барашкин»", "ИП «Овечкин»"];
const consigneeAddresses_rogaikopyta = {
  "ИП «Барашкин»": "г. Алматы, ул. Розыбакиева, 121",
  "ИП «Овечкин»": "г. Алматы, пр. Райымбека, 348"
};
const products_rogaikopyta = [
  {warehouse:"almaty", code:"RK-1001", defaultOrderPackaging:"PKG-01B", sap:"00921147010", name:"Рога тур сорт, комплект", group:"Сырьё", unit:"шт", stock:420, reserved:60, defect:3, batch:"Партия 07-26/А", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"—", barcode:"4100100000000", weightGross:0.3, weightNet:0.25, volume:0.002, length:10, width:8, height:5, series:"С-2600-01"},
  {warehouse:"almaty", code:"RK-1002", sap:"00921147011", name:"Копыта, мешок 20 кг", group:"Сырьё", unit:"меш", stock:180, reserved:20, defect:0, batch:"Партия 07-26/Б", exp:"—", statusProduct:"Активен", packageType:"Мешок", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"—", barcode:"4100200000000", weightGross:0.67, weightNet:0.6, volume:0.0051, length:17, width:13, height:8, series:"С-2601-02"},
  {warehouse:"almaty", code:"RK-1003", sap:"00921147012", name:"Шерсть овечья мытая, тюк", group:"Сырьё", unit:"тюк", stock:96, reserved:12, defect:1, batch:"Партия 06-26/В", exp:"—", statusProduct:"Активен", packageType:"Тюк", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Китай", brand:"—", barcode:"4100300000000", weightGross:1.04, weightNet:0.95, volume:0.0082, length:24, width:18, height:11, series:"С-2602-03"},
  {warehouse:"almaty", code:"RK-2001", sap:"00922314208", name:"Молоко козье, 1 л", group:"Молочная продукция", unit:"шт", stock:640, reserved:150, defect:8, batch:"Партия 25.07.26", exp:"05.08.2026", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"141", countryOrigin:"Германия", brand:"—", barcode:"4200100000000", weightGross:1.41, weightNet:1.3, volume:0.0113, length:31, width:23, height:14, series:"С-2603-04"},
  {warehouse:"almaty", code:"RK-2002", sap:"00922314209", name:"Сыр овечий твёрдый, 400 г", group:"Молочная продукция", unit:"шт", stock:210, reserved:40, defect:2, batch:"Партия 20.07.26", exp:"20.10.2026", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"158", countryOrigin:"Беларусь", brand:"—", barcode:"4200200000000", weightGross:1.78, weightNet:1.65, volume:0.0144, length:38, width:28, height:17, series:"С-2604-05"},
  {warehouse:"almaty", code:"RK-3001", sap:"00923050125", name:"Баранина, тушка охл.", group:"Мясная продукция", unit:"шт", stock:75, reserved:30, defect:0, batch:"Партия 28.07.26", exp:"04.08.2026", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"C", shelfLifeDays:"175", countryOrigin:"Турция", brand:"—", barcode:"4300100000000", weightGross:2.15, weightNet:2.0, volume:0.0175, length:45, width:33, height:20, series:"С-2605-06"},
  {warehouse:"almaty", code:"RK-3002", sap:"00923050126", name:"Козлятина, полутуша замор.", group:"Мясная продукция", unit:"шт", stock:52, reserved:10, defect:1, batch:"Партия 15.07.26", exp:"15.01.2027", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"192", countryOrigin:"Казахстан", brand:"—", barcode:"4300200000000", weightGross:2.52, weightNet:2.35, volume:0.0206, length:52, width:38, height:23, series:"С-2606-07"},
  {warehouse:"almaty", code:"RK-3003", sap:"00923050127", name:"Ливер бараний, кг", group:"Мясная продукция", unit:"кг", stock:140, reserved:0, defect:0, batch:"Партия 27.07.26", exp:"03.08.2026", statusProduct:"Активен", packageType:"Весовой товар", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"209", countryOrigin:"Россия", brand:"—", barcode:"4300300000000", weightGross:2.89, weightNet:2.7, volume:0.0237, length:59, width:43, height:26, series:"С-2607-08"},
  {warehouse:"almaty", code:"RK-4001", sap:"00924114095", name:"Суп-набор «Бешбармак», кор.", group:"Готовые наборы", unit:"кор", stock:310, reserved:80, defect:5, batch:"Партия 22.07.26", exp:"22.01.2027", statusProduct:"Активен", packageType:"Короб", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"226", countryOrigin:"Китай", brand:"—", barcode:"4400100000000", weightGross:3.26, weightNet:3.05, volume:0.0268, length:66, width:48, height:29, series:"С-2608-09"},
  {warehouse:"almaty", code:"RK-4002", sap:"00924114096", name:"Суп-набор «Шурпа», кор.", group:"Готовые наборы", unit:"кор", stock:275, reserved:45, defect:0, batch:"Партия 24.07.26", exp:"24.01.2027", statusProduct:"Активен", packageType:"Короб", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"243", countryOrigin:"Германия", brand:"—", barcode:"4400200000000", weightGross:3.63, weightNet:3.4, volume:0.0299, length:73, width:53, height:32, series:"С-2609-01"},
  {warehouse:"almaty", code:"RK-4003", sap:"00924114097", name:"Набор «Казы + Шужук», кор.", group:"Готовые наборы", unit:"кор", stock:88, reserved:88, defect:0, batch:"Партия 29.07.26", exp:"29.10.2026", statusProduct:"Архив", packageType:"Короб", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"260", countryOrigin:"Беларусь", brand:"—", barcode:"4400300000000", weightGross:4.0, weightNet:3.75, volume:0.033, length:80, width:58, height:35, series:"С-2610-02"},
  {warehouse:"almaty", code:"RK-1004", sap:"00921147013", name:"Овчина выделанная, шт", group:"Сырьё", unit:"шт", stock:64, reserved:6, defect:2, batch:"Партия 05-26/Г", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"C", shelfLifeDays:"—", countryOrigin:"Турция", brand:"—", barcode:"4100400000000", weightGross:4.37, weightNet:4.1, volume:0.0361, length:87, width:63, height:38, series:"С-2611-03"},
  {warehouse:"almaty", code:"RK-2003", sap:"00922314210", name:"Творог козий, 500 г", group:"Молочная продукция", unit:"шт", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"—", barcode:"4200300000000", weightGross:4.74, weightNet:4.45, volume:0.0392, length:94, width:8, height:41, series:"С-2612-04"},
  {warehouse:"almaty", code:"RK-3004", sap:"00923050128", name:"Казы конская, кг", group:"Мясная продукция", unit:"кг", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Весовой товар", productType:"Готовая продукция", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"—", barcode:"4300400000000", weightGross:5.11, weightNet:4.8, volume:0.0423, length:11, width:13, height:44, series:"С-2613-05"},
  {warehouse:"almaty", code:"RK-4004", sap:"00924114098", name:"Суп-набор «Наурыз-коже», кор.", group:"Готовые наборы", unit:"кор", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Короб", productType:"Готовая продукция", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Китай", brand:"—", barcode:"4400400000000", weightGross:5.48, weightNet:5.15, volume:0.0454, length:18, width:18, height:7, series:"С-2614-06"},
  {warehouse:"karagandy", code:"RK-5001", sap:"00925220010", name:"Рога тур сорт, комплект", group:"Сырьё", unit:"шт", stock:140, reserved:15, defect:1, batch:"Партия KRG-07-26/А", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Германия", brand:"—", barcode:"4500100000000", weightGross:5.85, weightNet:5.8, volume:0.0485, length:25, width:23, height:10, series:"С-2615-07"},
  {warehouse:"karagandy", code:"RK-5002", sap:"00925220011", name:"Шерсть овечья мытая, тюк", group:"Сырьё", unit:"тюк", stock:58, reserved:6, defect:0, batch:"Партия KRG-06-26/Б", exp:"—", statusProduct:"Активен", packageType:"Тюк", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Беларусь", brand:"—", barcode:"4500200000000", weightGross:6.22, weightNet:6.15, volume:0.0516, length:32, width:28, height:13, series:"С-2616-08"},
  {warehouse:"karagandy", code:"RK-5003", sap:"00925220012", name:"Баранина, тушка охл.", group:"Мясная продукция", unit:"шт", stock:34, reserved:12, defect:0, batch:"Партия KRG-28.07.26", exp:"04.08.2026", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"C", shelfLifeDays:"109", countryOrigin:"Турция", brand:"—", barcode:"4500300000000", weightGross:6.59, weightNet:6.5, volume:0.0547, length:39, width:33, height:16, series:"С-2617-09"},
  {warehouse:"karagandy", code:"RK-5004", sap:"00925220013", name:"Суп-набор «Бешбармак», кор.", group:"Готовые наборы", unit:"кор", stock:120, reserved:20, defect:2, batch:"Партия KRG-22.07.26", exp:"22.01.2027", statusProduct:"Активен", packageType:"Короб", productType:"Готовая продукция", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"126", countryOrigin:"Казахстан", brand:"—", barcode:"4500400000000", weightGross:6.96, weightNet:6.85, volume:0.0578, length:46, width:38, height:19, series:"С-2618-01"},
  {warehouse:"karagandy", code:"RK-5005", sap:"00925220014", name:"Молоко козье, 1 л", group:"Молочная продукция", unit:"шт", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Готовая продукция", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"—", barcode:"4500500000000", weightGross:7.33, weightNet:7.2, volume:0.0609, length:53, width:43, height:22, series:"С-2619-02"},
  {warehouse:"almaty", code:"RK-6001", defaultOrderPackaging:"PKG-08B", sap:"00926330010", name:"Струя бобра, настойка", group:"Сырьё", unit:"л", stock:210, reserved:35, defect:0, batch:"Партия 07-26/Н1", exp:"—", statusProduct:"Активен", packageType:"Разливной товар", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"730", countryOrigin:"Казахстан", brand:"—", barcode:"4600100000000", weightGross:1.05, weightNet:1.0, volume:0.001, length:8, width:8, height:20, series:"С-2620-01"},
  {warehouse:"almaty", code:"RK-6002", defaultOrderPackaging:"PKG-09A", sap:"00926330011", name:"Сыворотка молочная сырая", group:"Молочная продукция", unit:"л", stock:640, reserved:80, defect:0, batch:"Партия 07-26/С2", exp:"12.08.2026", statusProduct:"Активен", packageType:"Разливной товар", productType:"Сырьё", batchTracking:"Да", expiryTracking:"Да", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"10", countryOrigin:"Казахстан", brand:"—", barcode:"4600200000000", weightGross:1.03, weightNet:1.0, volume:0.001, length:20, width:20, height:30, series:"С-2621-02"},
];
const receipts_rogaikopyta = [
  {warehouse:"almaty", no:"ПР-000118", date:"28.07.2026", eta:"31.07.2026", supplier:suppliers_rogaikopyta[0], items:4, volume:12.4, transport:"№ 452-KZ / вод. Ахметов Б.", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-1000", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300000", gtd:"ГТД № 50000000/2026", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"700KZH01", trailerNumber:"P100KZH01", techPassportNumber:"KZ1000000", containerNumber:"CONT5000", sealNumber:"S40000", driverName:"Оспанов Д.Е.", driverPhone:"8 701 300 10 20", driverIdNumber:"N4000000", driverInn:"900000000000", weight:72.0, lineCount:4, palletCount:1, cargoPlaces:1, totalPieces:40, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ПР-000117", date:"27.07.2026", eta:"29.07.2026", supplier:suppliers_rogaikopyta[0], items:6, volume:24.8, transport:"№ 118-KZ / вод. Сериков Т.", status:"warn", statusLabel:"Ожидает приёмки", erpNo:"ERP-2026-1001", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300001", gtd:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"701KZH01", trailerNumber:"—", techPassportNumber:"KZ1000001", containerNumber:"—", sealNumber:"S40001", driverName:"Ертаев С.Б.", driverPhone:"8 702 301 11 21", driverIdNumber:"N4000001", driverInn:"900000000001", weight:128.4, lineCount:6, palletCount:2, cargoPlaces:3, totalPieces:66, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ПР-000116", date:"25.07.2026", eta:"27.07.2026", supplier:suppliers_rogaikopyta[0], items:3, volume:9.1, transport:"№ 733-KZ / вод. Нурлан А.", status:"warn", statusLabel:"В работе", erpNo:"ERP-2026-1002", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300002", gtd:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"702KZH01", trailerNumber:"P102KZH01", techPassportNumber:"KZ1000002", containerNumber:"—", sealNumber:"S40002", driverName:"Мукатаев Б.А.", driverPhone:"8 703 302 12 22", driverIdNumber:"N4000002", driverInn:"900000000002", weight:74.4, lineCount:3, palletCount:1, cargoPlaces:1, totalPieces:36, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ПР-000115", date:"22.07.2026", eta:"24.07.2026", supplier:suppliers_rogaikopyta[0], items:8, volume:31.6, transport:"№ 205-KZ / вод. Досжанов Е.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1003", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300003", gtd:"ГТД № 50000003/2026", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"703KZH01", trailerNumber:"—", techPassportNumber:"KZ1000003", containerNumber:"CONT5003", sealNumber:"S40003", driverName:"Сагындыков Р.К.", driverPhone:"8 704 303 13 23", driverIdNumber:"N4000003", driverInn:"900000000003", weight:225.6, lineCount:8, palletCount:3, cargoPlaces:4, totalPieces:104, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ПР-000114", date:"20.07.2026", eta:"22.07.2026", supplier:suppliers_rogaikopyta[0], items:5, volume:18.0, transport:"№ 340-KZ / вод. Байтасов Р.", status:"err", statusLabel:"Расхождение по кол-ву", erpNo:"ERP-2026-1004", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300004", gtd:"—", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"704KZH01", trailerNumber:"P104KZH01", techPassportNumber:"KZ1000004", containerNumber:"—", sealNumber:"S40004", driverName:"Оспанов Д.Е.", driverPhone:"8 705 304 14 24", driverIdNumber:"N4000004", driverInn:"900000000004", weight:158.0, lineCount:5, palletCount:1, cargoPlaces:1, totalPieces:70, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ПР-000113", date:"18.07.2026", eta:"20.07.2026", supplier:suppliers_rogaikopyta[0], items:2, volume:6.2, transport:"№ 981-KZ / вод. Куанышев М.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1005", docType:"Возврат", comment:"—", accompanyingDocNumber:"ТТН-300005", gtd:"—", carrier:"ИП Оспанов", transportType:"Фургон", transportBrand:"MAN TGX", carNumber:"705KZH01", trailerNumber:"—", techPassportNumber:"KZ1000005", containerNumber:"—", sealNumber:"S40005", driverName:"Ертаев С.Б.", driverPhone:"8 706 305 15 25", driverIdNumber:"N4000005", driverInn:"900000000005", weight:70.0, lineCount:2, palletCount:1, cargoPlaces:2, totalPieces:30, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ПР-000112", date:"14.07.2026", eta:"16.07.2026", supplier:suppliers_rogaikopyta[0], items:7, volume:27.9, transport:"№ 512-KZ / вод. Оспанов Д.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1006", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300006", gtd:"ГТД № 50000006/2026", carrier:"ТОО «АвтоЛогистик»", transportType:"Грузовой автомобиль", transportBrand:"Hino 500", carNumber:"706KZH01", trailerNumber:"P106KZH01", techPassportNumber:"KZ1000006", containerNumber:"CONT5006", sealNumber:"S40006", driverName:"Мукатаев Б.А.", driverPhone:"8 707 306 16 26", driverIdNumber:"N4000006", driverInn:"900000000006", weight:268.8, lineCount:7, palletCount:2, cargoPlaces:2, totalPieces:112, author:"Серикова Айгерим Батыровна"},
  {warehouse:"karagandy", no:"ПР-К00042", date:"29.07.2026", eta:"01.08.2026", supplier:suppliers_rogaikopyta[0], items:3, volume:9.6, transport:"№ 118-KRG / вод. Ертаев С.", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-1007", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300007", gtd:"—", carrier:"ИП Ертаев", transportType:"Автопоезд", transportBrand:"Volvo FH", carNumber:"707KZH01", trailerNumber:"—", techPassportNumber:"KZ1000007", containerNumber:"—", sealNumber:"S40007", driverName:"Сагындыков Р.К.", driverPhone:"8 708 307 17 27", driverIdNumber:"N4000007", driverInn:"900000000007", weight:54.0, lineCount:3, palletCount:1, cargoPlaces:2, totalPieces:51, author:"Серикова Айгерим Батыровна"},
  {warehouse:"karagandy", no:"ПР-К00041", date:"26.07.2026", eta:"28.07.2026", supplier:suppliers_rogaikopyta[0], items:4, volume:14.2, transport:"№ 205-KRG / вод. Мукатаев Б.", status:"warn", statusLabel:"Ожидает приёмки", erpNo:"ERP-2026-1008", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300008", gtd:"—", carrier:"ТОО «КазТранс»", transportType:"Фургон", transportBrand:"Kamaz 65117", carNumber:"708KZH01", trailerNumber:"P108KZH01", techPassportNumber:"KZ1000008", containerNumber:"—", sealNumber:"S40008", driverName:"Оспанов Д.Е.", driverPhone:"8 709 308 18 28", driverIdNumber:"N4000008", driverInn:"900000000008", weight:85.6, lineCount:4, palletCount:1, cargoPlaces:1, totalPieces:72, author:"Серикова Айгерим Батыровна"},
  {warehouse:"karagandy", no:"ПР-К00040", date:"22.07.2026", eta:"24.07.2026", supplier:suppliers_rogaikopyta[0], items:2, volume:5.1, transport:"№ 340-KRG / вод. Сагындыков Р.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1009", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300009", gtd:"ГТД № 50000009/2026", carrier:"ИП Оспанов", transportType:"Грузовой автомобиль", transportBrand:"MAN TGX", carNumber:"709KZH01", trailerNumber:"—", techPassportNumber:"KZ1000009", containerNumber:"CONT5009", sealNumber:"S40009", driverName:"Ертаев С.Б.", driverPhone:"8 701 309 19 29", driverIdNumber:"N4000009", driverInn:"900000000009", weight:49.6, lineCount:2, palletCount:1, cargoPlaces:2, totalPieces:38, author:"Серикова Айгерим Батыровна"},
];
const orders_rogaikopyta = [
  {warehouse:"almaty", no:"ЗК-000091", date:"29.07.2026", ship:"01.08.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:3, weight:420, orderType:"Обычный", plannedShip:"01.08.2026", volume:2.0, transport:"№ 662-KZ / ТОО «ТрансЛог»", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2000", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"800KZH01", trailerNumber:"P200KZH01", techPassportNumber:"KZ2000000", containerNumber:"CONT6000", sealNumber:"S50000", driverName:"Байтасов Р.С.", driverPhone:"8 701 400 15 25", driverIdNumber:"N5000000", driverInn:"910000000000", lineCount:3, palletCount:2, cargoPlaces:2, totalPieces:24, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000090", date:"28.07.2026", ship:"30.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:5, weight:610, orderType:"Экспресс", plannedShip:"30.07.2026", volume:3.7, transport:"№ 219-KZ / ИП Жумабеков", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2001", comment:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"801KZH01", trailerNumber:"—", techPassportNumber:"KZ2000001", containerNumber:"—", sealNumber:"S50001", driverName:"Нурлан А.Т.", driverPhone:"8 702 401 16 26", driverIdNumber:"N5000001", driverInn:"910000000001", lineCount:4, palletCount:2, cargoPlaces:3, totalPieces:36, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000089", date:"27.07.2026", ship:"29.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:2, weight:180, orderType:"Обычный", plannedShip:"29.07.2026", volume:5.4, transport:"№ 447-KZ / ИП Садыков", status:"warn", statusLabel:"В резерве", erpNo:"ERP-2026-2002", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"802KZH01", trailerNumber:"P202KZH01", techPassportNumber:"KZ2000002", containerNumber:"—", sealNumber:"S50002", driverName:"Досжанов Е.М.", driverPhone:"8 703 402 17 27", driverIdNumber:"N5000002", driverInn:"910000000002", lineCount:5, palletCount:2, cargoPlaces:2, totalPieces:50, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000088", date:"25.07.2026", ship:"27.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:6, weight:730, orderType:"Экспресс", plannedShip:"27.07.2026", volume:7.1, transport:"№ 118-KZ / ТОО «КазТранс»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2003", comment:"—", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"803KZH01", trailerNumber:"—", techPassportNumber:"KZ2000003", containerNumber:"CONT6003", sealNumber:"S50003", driverName:"Жумабеков С.К.", driverPhone:"8 704 403 18 28", driverIdNumber:"N5000003", driverInn:"910000000003", lineCount:6, palletCount:3, cargoPlaces:4, totalPieces:66, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000087", date:"23.07.2026", ship:"25.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:4, weight:355, orderType:"Обычный", plannedShip:"25.07.2026", volume:8.8, transport:"№ 902-KZ / ИП Барашкин", status:"err", statusLabel:"Задержка отгрузки", erpNo:"ERP-2026-2004", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"804KZH01", trailerNumber:"P204KZH01", techPassportNumber:"KZ2000004", containerNumber:"—", sealNumber:"S50004", driverName:"Байтасов Р.С.", driverPhone:"8 705 404 19 29", driverIdNumber:"N5000004", driverInn:"910000000004", lineCount:7, palletCount:4, cargoPlaces:4, totalPieces:84, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000086", date:"20.07.2026", ship:"22.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:1, weight:60, orderType:"Экспресс", plannedShip:"22.07.2026", volume:10.5, transport:"№ 305-KZ / ИП Овечкин", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2005", comment:"—", carrier:"ИП Оспанов", transportType:"Фургон", transportBrand:"MAN TGX", carNumber:"805KZH01", trailerNumber:"—", techPassportNumber:"KZ2000005", containerNumber:"—", sealNumber:"S50005", driverName:"Нурлан А.Т.", driverPhone:"8 706 405 20 30", driverIdNumber:"N5000005", driverInn:"910000000005", lineCount:3, palletCount:2, cargoPlaces:3, totalPieces:39, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000085", date:"17.07.2026", ship:"19.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:8, weight:940, orderType:"Обычный", plannedShip:"19.07.2026", volume:12.2, transport:"№ 771-KZ / ТОО «КазТранс»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2006", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Грузовой автомобиль", transportBrand:"Hino 500", carNumber:"806KZH01", trailerNumber:"P206KZH01", techPassportNumber:"KZ2000006", containerNumber:"CONT6006", sealNumber:"S50006", driverName:"Досжанов Е.М.", driverPhone:"8 707 406 21 31", driverIdNumber:"N5000006", driverInn:"910000000006", lineCount:4, palletCount:2, cargoPlaces:2, totalPieces:56, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000084", date:"16.07.2026", ship:"18.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:1, weight:50, orderType:"Обычный", plannedShip:"18.07.2026", volume:1.5, transport:"№ 300-KZ / ТОО «ТрансЛог»", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2007", comment:"—", carrier:"ИП Ертаев", transportType:"Автопоезд", transportBrand:"Volvo FH", carNumber:"807KZH01", trailerNumber:"—", techPassportNumber:"KZ2000007", containerNumber:"—", sealNumber:"S50007", driverName:"Жумабеков С.К.", driverPhone:"8 708 407 22 32", driverIdNumber:"N5000007", driverInn:"910000000007", lineCount:5, palletCount:2, cargoPlaces:3, totalPieces:75, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000083", date:"15.07.2026", ship:"17.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:4, weight:97, orderType:"Экспресс", plannedShip:"17.07.2026", volume:2.8, transport:"№ 313-KZ / ИП Жумабеков", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2008", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Фургон", transportBrand:"Kamaz 65117", carNumber:"808KZH01", trailerNumber:"P208KZH01", techPassportNumber:"KZ2000008", containerNumber:"—", sealNumber:"S50008", driverName:"Байтасов Р.С.", driverPhone:"8 709 408 23 33", driverIdNumber:"N5000008", driverInn:"910000000008", lineCount:6, palletCount:3, cargoPlaces:3, totalPieces:96, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000082", date:"14.07.2026", ship:"16.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:7, weight:144, orderType:"Обычный", plannedShip:"16.07.2026", volume:4.1, transport:"№ 326-KZ / ИП Садыков", status:"warn", statusLabel:"В резерве", erpNo:"ERP-2026-2009", comment:"—", carrier:"ИП Оспанов", transportType:"Грузовой автомобиль", transportBrand:"MAN TGX", carNumber:"809KZH01", trailerNumber:"—", techPassportNumber:"KZ2000009", containerNumber:"CONT6009", sealNumber:"S50009", driverName:"Нурлан А.Т.", driverPhone:"8 701 409 24 34", driverIdNumber:"N5000009", driverInn:"910000000009", lineCount:7, palletCount:4, cargoPlaces:5, totalPieces:119, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000081", date:"13.07.2026", ship:"15.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:1, weight:191, orderType:"Экспресс", plannedShip:"15.07.2026", volume:5.4, transport:"№ 339-KZ / ТОО «КазТранс»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2010", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Автопоезд", transportBrand:"Hino 500", carNumber:"810KZH01", trailerNumber:"P210KZH01", techPassportNumber:"KZ2000010", containerNumber:"—", sealNumber:"S50010", driverName:"Досжанов Е.М.", driverPhone:"8 702 410 25 35", driverIdNumber:"N5000010", driverInn:"910000000010", lineCount:3, palletCount:2, cargoPlaces:2, totalPieces:54, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000080", date:"12.07.2026", ship:"14.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:4, weight:238, orderType:"Обычный", plannedShip:"14.07.2026", volume:6.7, transport:"№ 352-KZ / ИП Барашкин", status:"err", statusLabel:"Задержка отгрузки", erpNo:"ERP-2026-2011", comment:"—", carrier:"ИП Ертаев", transportType:"Фургон", transportBrand:"Volvo FH", carNumber:"811KZH01", trailerNumber:"—", techPassportNumber:"KZ2000011", containerNumber:"—", sealNumber:"S50011", driverName:"Жумабеков С.К.", driverPhone:"8 703 411 26 36", driverIdNumber:"N5000011", driverInn:"910000000011", lineCount:4, palletCount:2, cargoPlaces:3, totalPieces:76, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000079", date:"11.07.2026", ship:"13.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:7, weight:285, orderType:"Экспресс", plannedShip:"13.07.2026", volume:8.0, transport:"№ 365-KZ / ИП Овечкин", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2012", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"812KZH01", trailerNumber:"P212KZH01", techPassportNumber:"KZ2000012", containerNumber:"CONT6012", sealNumber:"S50012", driverName:"Байтасов Р.С.", driverPhone:"8 704 412 27 37", driverIdNumber:"N5000012", driverInn:"910000000012", lineCount:5, palletCount:2, cargoPlaces:2, totalPieces:40, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000078", date:"10.07.2026", ship:"12.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:1, weight:332, orderType:"Обычный", plannedShip:"12.07.2026", volume:9.3, transport:"№ 378-KZ / ТОО «АвтоЛайн»", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2013", comment:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"813KZH01", trailerNumber:"—", techPassportNumber:"KZ2000013", containerNumber:"—", sealNumber:"S50013", driverName:"Нурлан А.Т.", driverPhone:"8 705 413 28 38", driverIdNumber:"N5000013", driverInn:"910000000013", lineCount:6, palletCount:3, cargoPlaces:4, totalPieces:54, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000077", date:"09.07.2026", ship:"11.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:4, weight:379, orderType:"Экспресс", plannedShip:"11.07.2026", volume:10.6, transport:"№ 391-KZ / ИП Нурланов", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2014", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"814KZH01", trailerNumber:"P214KZH01", techPassportNumber:"KZ2000014", containerNumber:"—", sealNumber:"S50014", driverName:"Досжанов Е.М.", driverPhone:"8 706 414 29 39", driverIdNumber:"N5000014", driverInn:"910000000014", lineCount:7, palletCount:4, cargoPlaces:4, totalPieces:70, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000076", date:"08.07.2026", ship:"10.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:7, weight:426, orderType:"Обычный", plannedShip:"10.07.2026", volume:11.9, transport:"№ 404-KZ / ТОО «ТрансЛог»", status:"warn", statusLabel:"В резерве", erpNo:"ERP-2026-2015", comment:"—", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"815KZH01", trailerNumber:"—", techPassportNumber:"KZ2000015", containerNumber:"CONT6015", sealNumber:"S50015", driverName:"Жумабеков С.К.", driverPhone:"8 707 415 30 40", driverIdNumber:"N5000015", driverInn:"910000000015", lineCount:3, palletCount:2, cargoPlaces:3, totalPieces:33, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000075", date:"07.07.2026", ship:"09.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:1, weight:473, orderType:"Экспресс", plannedShip:"09.07.2026", volume:13.2, transport:"№ 417-KZ / ИП Жумабеков", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2016", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"816KZH01", trailerNumber:"P216KZH01", techPassportNumber:"KZ2000016", containerNumber:"—", sealNumber:"S50016", driverName:"Байтасов Р.С.", driverPhone:"8 708 416 31 41", driverIdNumber:"N5000016", driverInn:"910000000016", lineCount:4, palletCount:2, cargoPlaces:2, totalPieces:48, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000074", date:"06.07.2026", ship:"08.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:4, weight:520, orderType:"Обычный", plannedShip:"08.07.2026", volume:2.5, transport:"№ 430-KZ / ИП Садыков", status:"err", statusLabel:"Задержка отгрузки", erpNo:"ERP-2026-2017", comment:"—", carrier:"ИП Оспанов", transportType:"Фургон", transportBrand:"MAN TGX", carNumber:"817KZH01", trailerNumber:"—", techPassportNumber:"KZ2000017", containerNumber:"—", sealNumber:"S50017", driverName:"Нурлан А.Т.", driverPhone:"8 709 417 32 42", driverIdNumber:"N5000017", driverInn:"910000000017", lineCount:5, palletCount:2, cargoPlaces:3, totalPieces:65, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000073", date:"05.07.2026", ship:"07.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:7, weight:567, orderType:"Экспресс", plannedShip:"07.07.2026", volume:3.8, transport:"№ 443-KZ / ТОО «КазТранс»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2018", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Грузовой автомобиль", transportBrand:"Hino 500", carNumber:"818KZH01", trailerNumber:"P218KZH01", techPassportNumber:"KZ2000018", containerNumber:"CONT6018", sealNumber:"S50018", driverName:"Досжанов Е.М.", driverPhone:"8 701 418 33 43", driverIdNumber:"N5000018", driverInn:"910000000018", lineCount:6, palletCount:3, cargoPlaces:3, totalPieces:84, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000072", date:"04.07.2026", ship:"06.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:1, weight:614, orderType:"Обычный", plannedShip:"06.07.2026", volume:5.1, transport:"№ 456-KZ / ИП Барашкин", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2019", comment:"—", carrier:"ИП Ертаев", transportType:"Автопоезд", transportBrand:"Volvo FH", carNumber:"819KZH01", trailerNumber:"—", techPassportNumber:"KZ2000019", containerNumber:"—", sealNumber:"S50019", driverName:"Жумабеков С.К.", driverPhone:"8 702 419 34 44", driverIdNumber:"N5000019", driverInn:"910000000019", lineCount:7, palletCount:4, cargoPlaces:5, totalPieces:105, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000071", date:"03.07.2026", ship:"05.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:4, weight:661, orderType:"Экспресс", plannedShip:"05.07.2026", volume:6.4, transport:"№ 469-KZ / ИП Овечкин", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2020", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Фургон", transportBrand:"Kamaz 65117", carNumber:"820KZH01", trailerNumber:"P220KZH01", techPassportNumber:"KZ2000020", containerNumber:"—", sealNumber:"S50020", driverName:"Байтасов Р.С.", driverPhone:"8 703 420 35 45", driverIdNumber:"N5000020", driverInn:"910000000020", lineCount:3, palletCount:2, cargoPlaces:2, totalPieces:48, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000070", date:"02.07.2026", ship:"04.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:7, weight:708, orderType:"Обычный", plannedShip:"04.07.2026", volume:7.7, transport:"№ 482-KZ / ТОО «АвтоЛайн»", status:"warn", statusLabel:"В резерве", erpNo:"ERP-2026-2021", comment:"—", carrier:"ИП Оспанов", transportType:"Грузовой автомобиль", transportBrand:"MAN TGX", carNumber:"821KZH01", trailerNumber:"—", techPassportNumber:"KZ2000021", containerNumber:"CONT6021", sealNumber:"S50021", driverName:"Нурлан А.Т.", driverPhone:"8 704 421 36 46", driverIdNumber:"N5000021", driverInn:"910000000021", lineCount:4, palletCount:2, cargoPlaces:3, totalPieces:68, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000069", date:"01.07.2026", ship:"03.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:1, weight:755, orderType:"Экспресс", plannedShip:"03.07.2026", volume:9.0, transport:"№ 495-KZ / ИП Нурланов", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2022", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Автопоезд", transportBrand:"Hino 500", carNumber:"822KZH01", trailerNumber:"P222KZH01", techPassportNumber:"KZ2000022", containerNumber:"—", sealNumber:"S50022", driverName:"Досжанов Е.М.", driverPhone:"8 705 422 37 47", driverIdNumber:"N5000022", driverInn:"910000000022", lineCount:5, palletCount:2, cargoPlaces:2, totalPieces:90, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000068", date:"30.06.2026", ship:"02.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:4, weight:802, orderType:"Обычный", plannedShip:"02.07.2026", volume:10.3, transport:"№ 508-KZ / ТОО «ТрансЛог»", status:"err", statusLabel:"Задержка отгрузки", erpNo:"ERP-2026-2023", comment:"—", carrier:"ИП Ертаев", transportType:"Фургон", transportBrand:"Volvo FH", carNumber:"823KZH01", trailerNumber:"—", techPassportNumber:"KZ2000023", containerNumber:"—", sealNumber:"S50023", driverName:"Жумабеков С.К.", driverPhone:"8 706 423 38 48", driverIdNumber:"N5000023", driverInn:"910000000023", lineCount:6, palletCount:3, cargoPlaces:4, totalPieces:114, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000067", date:"29.06.2026", ship:"01.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:7, weight:849, orderType:"Экспресс", plannedShip:"01.07.2026", volume:11.6, transport:"№ 521-KZ / ИП Жумабеков", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2024", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"824KZH01", trailerNumber:"P224KZH01", techPassportNumber:"KZ2000024", containerNumber:"CONT6024", sealNumber:"S50024", driverName:"Байтасов Р.С.", driverPhone:"8 707 424 39 49", driverIdNumber:"N5000024", driverInn:"910000000024", lineCount:7, palletCount:4, cargoPlaces:4, totalPieces:56, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000066", date:"28.06.2026", ship:"30.06.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:1, weight:896, orderType:"Обычный", plannedShip:"30.06.2026", volume:12.9, transport:"№ 534-KZ / ИП Садыков", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2025", comment:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"825KZH01", trailerNumber:"—", techPassportNumber:"KZ2000025", containerNumber:"—", sealNumber:"S50025", driverName:"Нурлан А.Т.", driverPhone:"8 708 425 40 50", driverIdNumber:"N5000025", driverInn:"910000000025", lineCount:3, palletCount:2, cargoPlaces:3, totalPieces:27, author:"Серикова Айгерим Батыровна"},
  {warehouse:"almaty", no:"ЗК-000065", date:"27.06.2026", ship:"29.06.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:4, weight:943, orderType:"Экспресс", plannedShip:"29.06.2026", volume:2.2, transport:"№ 547-KZ / ТОО «КазТранс»", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2026", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"826KZH01", trailerNumber:"P226KZH01", techPassportNumber:"KZ2000026", containerNumber:"—", sealNumber:"S50026", driverName:"Досжанов Е.М.", driverPhone:"8 709 426 41 51", driverIdNumber:"N5000026", driverInn:"910000000026", lineCount:4, palletCount:2, cargoPlaces:2, totalPieces:40, author:"Серикова Айгерим Батыровна"},
  {warehouse:"karagandy", no:"ЗК-К00033", date:"29.07.2026", ship:"01.08.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[0]], items:2, weight:130, orderType:"Обычный", plannedShip:"01.08.2026", volume:1.8, transport:"№ 118-KRG / ИП Садыков", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2027", comment:"—", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"827KZH01", trailerNumber:"—", techPassportNumber:"KZ2000027", containerNumber:"CONT6027", sealNumber:"S50027", driverName:"Жумабеков С.К.", driverPhone:"8 701 427 42 52", driverIdNumber:"N5000027", driverInn:"910000000027", lineCount:5, palletCount:2, cargoPlaces:3, totalPieces:55, author:"Серикова Айгерим Батыровна"},
  {warehouse:"karagandy", no:"ЗК-К00032", date:"26.07.2026", ship:"28.07.2026", consignee:consignees_rogaikopyta[1], deliveryAddress:consigneeAddresses_rogaikopyta[consignees_rogaikopyta[1]], items:3, weight:210, orderType:"Экспресс", plannedShip:"28.07.2026", volume:3.4, transport:"№ 205-KRG / ИП Овечкин", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2028", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"828KZH01", trailerNumber:"P228KZH01", techPassportNumber:"KZ2000028", containerNumber:"—", sealNumber:"S50028", driverName:"Байтасов Р.С.", driverPhone:"8 702 428 43 53", driverIdNumber:"N5000028", driverInn:"910000000028", lineCount:6, palletCount:3, cargoPlaces:3, totalPieces:72, author:"Серикова Айгерим Батыровна"},
  {warehouse:"karagandy", no:"ЗК-К00031", date:"20.07.2026", ship:"22.07.2026", consignee:consignees_rogaikopyta[0], deliveryAddress:"г. Алматы, ул. Жандосова, 58, склад №3", items:1, weight:75, orderType:"Обычный", plannedShip:"22.07.2026", volume:0.9, transport:"№ 340-KRG / ИП Барашкин", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2029", comment:"—", carrier:"ИП Оспанов", transportType:"Фургон", transportBrand:"MAN TGX", carNumber:"829KZH01", trailerNumber:"—", techPassportNumber:"KZ2000029", containerNumber:"—", sealNumber:"S50029", driverName:"Нурлан А.Т.", driverPhone:"8 703 429 44 54", driverIdNumber:"N5000029", driverInn:"910000000029", lineCount:7, palletCount:4, cargoPlaces:5, totalPieces:91, author:"Серикова Айгерим Батыровна"},
];
const reports_rogaikopyta = [
  {name:"Наличие на складе — сводный", period:"на 31.07.2026", requested:"31.07.2026 09:14", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Отчёт по приёмке за июль", period:"01.07.2026 – 31.07.2026", requested:"31.07.2026 08:02", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Отчёт по отгрузкам за июль", period:"01.07.2026 – 31.07.2026", requested:"30.07.2026 18:40", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Сводный отчёт по оказанным услугам", period:"01.07.2026 – 31.07.2026", requested:"30.07.2026 12:10", format:"XLSX", status:"warn", statusLabel:"Формируется"},
  {name:"KPI 3PL-оператора", period:"II квартал 2026", requested:"29.07.2026 16:55", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Своевременность приёмки и размещения", period:"01.07.2026 – 31.07.2026", requested:"28.07.2026 10:20", format:"XLSX", status:"err", statusLabel:"Ошибка формирования"},
];

/* ---------- Болты и Гайки ---------- */
const suppliers_boltygaiki = ["ИП «Винтик»", "ИП «Шпунтик»"];
const consignees_boltygaiki = ["ТОО «АвтоДеталь Плюс»", "ИП «Гаечкин»"];
const consigneeAddresses_boltygaiki = {
  "ТОО «АвтоДеталь Плюс»": "г. Алматы, ул. Толе би, 273",
  "ИП «Гаечкин»": "г. Алматы, ул. Сатпаева, 90"
};
const products_boltygaiki = [
  {warehouse:"almaty", code:"BG-1001", sap:"00931220010", name:"Болт М8×40 оцинк., уп. 100 шт", group:"Крепёж", unit:"уп", stock:860, reserved:120, defect:4, batch:"Партия 07-26/К1", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"Fischer", barcode:"4100100000000", weightGross:0.3, weightNet:0.25, volume:0.002, length:10, width:8, height:5, series:"С-2600-01"},
  {warehouse:"almaty", code:"BG-1002", sap:"00931220011", name:"Гайка М8 оцинк., уп. 100 шт", group:"Крепёж", unit:"уп", stock:920, reserved:140, defect:0, batch:"Партия 07-26/К2", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"Fischer", barcode:"4100200000000", weightGross:0.67, weightNet:0.6, volume:0.0051, length:17, width:13, height:8, series:"С-2601-02"},
  {warehouse:"almaty", code:"BG-1003", sap:"00931220012", name:"Шайба гровер М8, уп. 200 шт", group:"Крепёж", unit:"уп", stock:410, reserved:30, defect:2, batch:"Партия 06-26/К3", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Китай", brand:"Fischer", barcode:"4100300000000", weightGross:1.04, weightNet:0.95, volume:0.0082, length:24, width:18, height:11, series:"С-2602-03"},
  {warehouse:"almaty", code:"BG-2001", sap:"00932310020", name:"Фильтр масляный OIL-204", group:"Фильтры", unit:"шт", stock:145, reserved:25, defect:1, batch:"Партия 25.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Германия", brand:"MannFilter", barcode:"4200100000000", weightGross:1.41, weightNet:1.3, volume:0.0113, length:31, width:23, height:14, series:"С-2603-04"},
  {warehouse:"almaty", code:"BG-2002", sap:"00932310021", name:"Фильтр воздушный AIR-118", group:"Фильтры", unit:"шт", stock:98, reserved:12, defect:0, batch:"Партия 20.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Беларусь", brand:"MannFilter", barcode:"4200200000000", weightGross:1.78, weightNet:1.65, volume:0.0144, length:38, width:28, height:17, series:"С-2604-05"},
  {warehouse:"almaty", code:"BG-2003", sap:"00932310022", name:"Фильтр салонный CAB-77", group:"Фильтры", unit:"шт", stock:76, reserved:8, defect:0, batch:"Партия 22.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"C", shelfLifeDays:"—", countryOrigin:"Турция", brand:"MannFilter", barcode:"4200300000000", weightGross:2.15, weightNet:2.0, volume:0.0175, length:45, width:33, height:20, series:"С-2605-06"},
  {warehouse:"almaty", code:"BG-3001", sap:"00933450030", name:"Колодки тормозные передние, компл.", group:"Тормозная система", unit:"компл", stock:64, reserved:18, defect:1, batch:"Партия 28.07.26", exp:"—", statusProduct:"Активен", packageType:"Комплект", productType:"Запчасти", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"TRW", barcode:"4300100000000", weightGross:2.52, weightNet:2.35, volume:0.0206, length:52, width:38, height:23, series:"С-2606-07"},
  {warehouse:"almaty", code:"BG-3002", sap:"00933450031", name:"Диск тормозной вентил. 280 мм", group:"Тормозная система", unit:"шт", stock:52, reserved:10, defect:0, batch:"Партия 15.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Запчасти", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"TRW", barcode:"4300200000000", weightGross:2.89, weightNet:2.7, volume:0.0237, length:59, width:43, height:26, series:"С-2607-08"},
  {warehouse:"almaty", code:"BG-4001", sap:"00934560040", name:"Свеча зажигания Iridium", group:"Электрика", unit:"шт", stock:310, reserved:40, defect:3, batch:"Партия 22.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Запчасти", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"9 класс — прочие опасные вещества", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Китай", brand:"Bosch", barcode:"4400100000000", weightGross:3.26, weightNet:3.05, volume:0.0268, length:66, width:48, height:29, series:"С-2608-09"},
  {warehouse:"almaty", code:"BG-4002", sap:"00934560041", name:"Аккумулятор 60 А·ч", group:"Электрика", unit:"шт", stock:34, reserved:6, defect:0, batch:"Партия 24.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Запчасти", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"9 класс — прочие опасные вещества", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Германия", brand:"Bosch", barcode:"4400200000000", weightGross:3.63, weightNet:3.4, volume:0.0299, length:73, width:53, height:32, series:"С-2609-01"},
  {warehouse:"almaty", code:"BG-5001", sap:"00935670050", name:"Масло моторное 5W-40, канистра 4 л", group:"Автохимия", unit:"канистра", stock:210, reserved:55, defect:2, batch:"Партия 29.07.26", exp:"29.10.2028", statusProduct:"Архив", packageType:"Канистра", productType:"Расходные материалы", batchTracking:"Да", expiryTracking:"Да", hazardClass:"3 класс — легковоспламеняющиеся", abcClass:"B", shelfLifeDays:"260", countryOrigin:"Беларусь", brand:"Shell", barcode:"4500100000000", weightGross:4.0, weightNet:3.75, volume:0.033, length:80, width:58, height:35, series:"С-2610-02"},
  {warehouse:"almaty", code:"BG-5002", sap:"00935670051", name:"Антифриз G12, канистра 5 л", group:"Автохимия", unit:"канистра", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Канистра", productType:"Расходные материалы", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"3 класс — легковоспламеняющиеся", abcClass:"C", shelfLifeDays:"—", countryOrigin:"Турция", brand:"Shell", barcode:"4500200000000", weightGross:4.37, weightNet:4.1, volume:0.0361, length:87, width:63, height:38, series:"С-2611-03"},
  {warehouse:"almaty", code:"BG-6001", sap:"00936780060", name:"Амортизатор передний газо-масл.", group:"Подвеска", unit:"шт", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Запчасти", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"Sachs", barcode:"4600100000000", weightGross:4.74, weightNet:4.45, volume:0.0392, length:94, width:8, height:41, series:"С-2612-04"},
  {warehouse:"almaty", code:"BG-6002", sap:"00936780061", name:"Сайлентблок рычага передний", group:"Подвеска", unit:"шт", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Запчасти", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"Sachs", barcode:"4600200000000", weightGross:5.11, weightNet:4.8, volume:0.0423, length:11, width:13, height:44, series:"С-2613-05"},
   {warehouse:"almaty", code:"BG-7001", defaultOrderPackaging:"PKG-10A", sap:"00937890070", name:"Болт на ваше мнение", group:"Крепёж", unit:"шт", stock:340, reserved:40, defect:0, batch:"Партия 07-26/К9", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"—", barcode:"4700100000000", weightGross:0.05, weightNet:0.045, volume:0.00002, length:5, width:1, height:1, series:"С-2614-06"},
];
const receipts_boltygaiki = [
  {warehouse:"almaty", no:"ПР-000221", date:"29.07.2026", eta:"01.08.2026", supplier:suppliers_boltygaiki[0], items:5, volume:8.2, transport:"№ 118-АГ / вод. Смагулов К.", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-1000", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300000", gtd:"ГТД № 50000000/2026", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"700KZH01", trailerNumber:"P100KZH01", techPassportNumber:"KZ1000000", containerNumber:"CONT5000", sealNumber:"S40000", driverName:"Оспанов Д.Е.", driverPhone:"8 701 300 10 20", driverIdNumber:"N4000000", driverInn:"900000000000", weight:90.0, lineCount:5, palletCount:2, cargoPlaces:2, totalPieces:50, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ПР-000220", date:"27.07.2026", eta:"29.07.2026", supplier:suppliers_boltygaiki[1], items:4, volume:6.5, transport:"№ 552-АГ / вод. Ержанов Т.", status:"warn", statusLabel:"Ожидает приёмки", erpNo:"ERP-2026-1001", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300001", gtd:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"701KZH01", trailerNumber:"—", techPassportNumber:"KZ1000001", containerNumber:"—", sealNumber:"S40001", driverName:"Ертаев С.Б.", driverPhone:"8 702 301 11 21", driverIdNumber:"N4000001", driverInn:"900000000001", weight:85.6, lineCount:4, palletCount:1, cargoPlaces:2, totalPieces:44, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ПР-000219", date:"25.07.2026", eta:"27.07.2026", supplier:suppliers_boltygaiki[0], items:3, volume:5.1, transport:"№ 274-АГ / вод. Мукатов Б.", status:"warn", statusLabel:"В работе", erpNo:"ERP-2026-1002", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300002", gtd:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"702KZH01", trailerNumber:"P102KZH01", techPassportNumber:"KZ1000002", containerNumber:"—", sealNumber:"S40002", driverName:"Мукатаев Б.А.", driverPhone:"8 703 302 12 22", driverIdNumber:"N4000002", driverInn:"900000000002", weight:74.4, lineCount:3, palletCount:1, cargoPlaces:1, totalPieces:36, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ПР-000218", date:"22.07.2026", eta:"24.07.2026", supplier:suppliers_boltygaiki[1], items:7, volume:14.3, transport:"№ 903-АГ / вод. Кенжебаев А.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1003", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300003", gtd:"ГТД № 50000003/2026", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"703KZH01", trailerNumber:"—", techPassportNumber:"KZ1000003", containerNumber:"CONT5003", sealNumber:"S40003", driverName:"Сагындыков Р.К.", driverPhone:"8 704 303 13 23", driverIdNumber:"N4000003", driverInn:"900000000003", weight:197.4, lineCount:7, palletCount:2, cargoPlaces:3, totalPieces:91, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ПР-000217", date:"18.07.2026", eta:"20.07.2026", supplier:suppliers_boltygaiki[0], items:2, volume:3.4, transport:"№ 415-АГ / вод. Тлеубердин Е.", status:"err", statusLabel:"Расхождение по кол-ву", erpNo:"ERP-2026-1004", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300004", gtd:"—", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"704KZH01", trailerNumber:"P104KZH01", techPassportNumber:"KZ1000004", containerNumber:"—", sealNumber:"S40004", driverName:"Оспанов Д.Е.", driverPhone:"8 705 304 14 24", driverIdNumber:"N4000004", driverInn:"900000000004", weight:63.2, lineCount:2, palletCount:1, cargoPlaces:1, totalPieces:28, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ПР-000216", date:"14.07.2026", eta:"16.07.2026", supplier:suppliers_boltygaiki[1], items:6, volume:11.0, transport:"№ 660-АГ / вод. Рахимов С.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1005", docType:"Возврат", comment:"—", accompanyingDocNumber:"ТТН-300005", gtd:"—", carrier:"ИП Оспанов", transportType:"Фургон", transportBrand:"MAN TGX", carNumber:"705KZH01", trailerNumber:"—", techPassportNumber:"KZ1000005", containerNumber:"—", sealNumber:"S40005", driverName:"Ертаев С.Б.", driverPhone:"8 706 305 15 25", driverIdNumber:"N4000005", driverInn:"900000000005", weight:210.0, lineCount:6, palletCount:1, cargoPlaces:2, totalPieces:90, author:"Тулегенов Аскар Болатович"},
];
const orders_boltygaiki = [
  {warehouse:"almaty", no:"ЗК-000144", date:"29.07.2026", ship:"01.08.2026", consignee:consignees_boltygaiki[0], deliveryAddress:consigneeAddresses_boltygaiki[consignees_boltygaiki[0]], items:4, weight:180, orderType:"Обычный", plannedShip:"01.08.2026", volume:2.0, transport:"№ 221-АГ / ТОО «КазТранс»", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2000", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"800KZH01", trailerNumber:"P200KZH01", techPassportNumber:"KZ2000000", containerNumber:"CONT6000", sealNumber:"S50000", driverName:"Байтасов Р.С.", driverPhone:"8 701 400 15 25", driverIdNumber:"N5000000", driverInn:"910000000000", lineCount:3, palletCount:2, cargoPlaces:2, totalPieces:24, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ЗК-000143", date:"28.07.2026", ship:"30.07.2026", consignee:consignees_boltygaiki[1], deliveryAddress:consigneeAddresses_boltygaiki[consignees_boltygaiki[1]], items:2, weight:65, orderType:"Экспресс", plannedShip:"30.07.2026", volume:3.7, transport:"№ 118-АГ / ИП Гаечкин", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2001", comment:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"801KZH01", trailerNumber:"—", techPassportNumber:"KZ2000001", containerNumber:"—", sealNumber:"S50001", driverName:"Нурлан А.Т.", driverPhone:"8 702 401 16 26", driverIdNumber:"N5000001", driverInn:"910000000001", lineCount:4, palletCount:2, cargoPlaces:3, totalPieces:36, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ЗК-000142", date:"26.07.2026", ship:"28.07.2026", consignee:consignees_boltygaiki[0], deliveryAddress:consigneeAddresses_boltygaiki[consignees_boltygaiki[0]], items:6, weight:240, orderType:"Обычный", plannedShip:"28.07.2026", volume:5.4, transport:"№ 447-АГ / ТОО «ТрансЛог»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2002", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"802KZH01", trailerNumber:"P202KZH01", techPassportNumber:"KZ2000002", containerNumber:"—", sealNumber:"S50002", driverName:"Досжанов Е.М.", driverPhone:"8 703 402 17 27", driverIdNumber:"N5000002", driverInn:"910000000002", lineCount:5, palletCount:2, cargoPlaces:2, totalPieces:50, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ЗК-000141", date:"23.07.2026", ship:"25.07.2026", consignee:consignees_boltygaiki[1], deliveryAddress:consigneeAddresses_boltygaiki[consignees_boltygaiki[1]], items:1, weight:20, orderType:"Экспресс", plannedShip:"25.07.2026", volume:7.1, transport:"№ 305-АГ / ИП Гаечкин", status:"err", statusLabel:"Задержка отгрузки", erpNo:"ERP-2026-2003", comment:"—", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"803KZH01", trailerNumber:"—", techPassportNumber:"KZ2000003", containerNumber:"CONT6003", sealNumber:"S50003", driverName:"Жумабеков С.К.", driverPhone:"8 704 403 18 28", driverIdNumber:"N5000003", driverInn:"910000000003", lineCount:6, palletCount:3, cargoPlaces:4, totalPieces:66, author:"Тулегенов Аскар Болатович"},
  {warehouse:"almaty", no:"ЗК-000140", date:"19.07.2026", ship:"21.07.2026", consignee:consignees_boltygaiki[0], deliveryAddress:consigneeAddresses_boltygaiki[consignees_boltygaiki[0]], items:8, weight:410, orderType:"Обычный", plannedShip:"21.07.2026", volume:8.8, transport:"№ 771-АГ / ТОО «КазТранс»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2004", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"804KZH01", trailerNumber:"P204KZH01", techPassportNumber:"KZ2000004", containerNumber:"—", sealNumber:"S50004", driverName:"Байтасов Р.С.", driverPhone:"8 705 404 19 29", driverIdNumber:"N5000004", driverInn:"910000000004", lineCount:7, palletCount:4, cargoPlaces:4, totalPieces:84, author:"Тулегенов Аскар Болатович"},
];
const reports_boltygaiki = [
  {name:"Наличие на складе — сводный", period:"на 31.07.2026", requested:"31.07.2026 09:20", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Отчёт по приёмке за июль", period:"01.07.2026 – 31.07.2026", requested:"31.07.2026 08:10", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Отчёт по отгрузкам за июль", period:"01.07.2026 – 31.07.2026", requested:"30.07.2026 17:55", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Сводный отчёт по оказанным услугам", period:"01.07.2026 – 31.07.2026", requested:"30.07.2026 12:40", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"KPI 3PL-оператора", period:"II квартал 2026", requested:"29.07.2026 16:10", format:"XLSX", status:"warn", statusLabel:"Формируется"},
];

/* ---------- Половой гигант ---------- */
const suppliers_polovoygigant = ["ИП «Ламинатов»", "ТОО «ПаркетСнаб»"];
const consignees_polovoygigant = ["ТОО «СтройМир»", "ИП «Полов Р.Б.»"];
const consigneeAddresses_polovoygigant = {
  "ТОО «СтройМир»": "г. Алматы, ул. Момышулы, 15",
  "ИП «Полов Р.Б.»": "г. Алматы, ул. Наурызбай батыра, 62"
};
const products_polovoygigant = [
  {warehouse:"karagandy", code:"PG-1001", sap:"00941120010", name:"Ламинат 33 класс, дуб, уп. 2,4 м²", group:"Ламинат", unit:"уп", stock:540, reserved:80, defect:6, batch:"Партия 07-26/Л1", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"Kronospan", barcode:"4100100000000", weightGross:0.3, weightNet:0.25, volume:0.002, length:10, width:8, height:5, series:"С-2600-01"},
  {warehouse:"karagandy", code:"PG-1002", sap:"00941120011", name:"Ламинат 32 класс, венге, уп. 2,4 м²", group:"Ламинат", unit:"уп", stock:310, reserved:40, defect:2, batch:"Партия 07-26/Л2", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"Kronospan", barcode:"4100200000000", weightGross:0.67, weightNet:0.6, volume:0.0051, length:17, width:13, height:8, series:"С-2601-02"},
  {warehouse:"karagandy", code:"PG-2001", sap:"00942230020", name:"Линолеум бытовой, рулон 3×20 м", group:"Линолеум", unit:"рулон", stock:64, reserved:12, defect:1, batch:"Партия 06-26/Р1", exp:"—", statusProduct:"Активен", packageType:"Рулон", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Китай", brand:"Tarkett", barcode:"4200100000000", weightGross:1.04, weightNet:0.95, volume:0.0082, length:24, width:18, height:11, series:"С-2602-03"},
  {warehouse:"karagandy", code:"PG-2002", sap:"00942230021", name:"Линолеум коммерческий, рулон 2×25 м", group:"Линолеум", unit:"рулон", stock:38, reserved:6, defect:0, batch:"Партия 06-26/Р2", exp:"—", statusProduct:"Активен", packageType:"Рулон", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Германия", brand:"Tarkett", barcode:"4200200000000", weightGross:1.41, weightNet:1.3, volume:0.0113, length:31, width:23, height:14, series:"С-2603-04"},
  {warehouse:"karagandy", code:"PG-3001", sap:"00943340030", name:"Паркетная доска дуб, уп. 2,2 м²", group:"Паркет", unit:"уп", stock:210, reserved:35, defect:3, batch:"Партия 20.07.26", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Беларусь", brand:"Barlinek", barcode:"4300100000000", weightGross:1.78, weightNet:1.65, volume:0.0144, length:38, width:28, height:17, series:"С-2604-05"},
  {warehouse:"karagandy", code:"PG-3002", sap:"00943340031", name:"Паркетная доска ясень, уп. 2,2 м²", group:"Паркет", unit:"уп", stock:96, reserved:14, defect:0, batch:"Партия 22.07.26", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"C", shelfLifeDays:"—", countryOrigin:"Турция", brand:"Barlinek", barcode:"4300200000000", weightGross:2.15, weightNet:2.0, volume:0.0175, length:45, width:33, height:20, series:"С-2605-06"},
  {warehouse:"karagandy", code:"PG-4001", sap:"00944450040", name:"Плитка керамогранит 60×60, уп. 1,44 м²", group:"Плитка", unit:"уп", stock:420, reserved:90, defect:8, batch:"Партия 25.07.26", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Казахстан", brand:"Kerama Marazzi", barcode:"4400100000000", weightGross:2.52, weightNet:2.35, volume:0.0206, length:52, width:38, height:23, series:"С-2606-07"},
  {warehouse:"karagandy", code:"PG-4002", sap:"00944450041", name:"Плитка керамическая 30×30, уп. 1,08 м²", group:"Плитка", unit:"уп", stock:280, reserved:35, defect:2, batch:"Партия 26.07.26", exp:"—", statusProduct:"Активен", packageType:"Упаковка", productType:"Строительные материалы", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"A", shelfLifeDays:"—", countryOrigin:"Россия", brand:"Kerama Marazzi", barcode:"4400200000000", weightGross:2.89, weightNet:2.7, volume:0.0237, length:59, width:43, height:26, series:"С-2607-08"},
  {warehouse:"karagandy", code:"PG-5001", sap:"00945560050", name:"Подложка под ламинат 3 мм, рулон 15 м²", group:"Подложка и крепёж", unit:"рулон", stock:150, reserved:20, defect:0, batch:"Партия 27.07.26", exp:"—", statusProduct:"Активен", packageType:"Рулон", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Китай", brand:"—", barcode:"4500100000000", weightGross:3.26, weightNet:3.05, volume:0.0268, length:66, width:48, height:29, series:"С-2608-09"},
  {warehouse:"karagandy", code:"PG-5002", sap:"00945560051", name:"Плинтус ПВХ 2,5 м", group:"Подложка и крепёж", unit:"шт", stock:640, reserved:110, defect:5, batch:"Партия 28.07.26", exp:"—", statusProduct:"Активен", packageType:"Поштучно", productType:"Комплектующие", batchTracking:"Да", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Германия", brand:"—", barcode:"4500200000000", weightGross:3.63, weightNet:3.4, volume:0.0299, length:73, width:53, height:32, series:"С-2609-01"},
  {warehouse:"karagandy", code:"PG-6001", sap:"00946670060", name:"Клей для плитки, мешок 25 кг", group:"Клеи и смеси", unit:"мешок", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Архив", packageType:"Мешок", productType:"Расходные материалы", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"B", shelfLifeDays:"—", countryOrigin:"Беларусь", brand:"—", barcode:"4600100000000", weightGross:4.0, weightNet:3.75, volume:0.033, length:80, width:58, height:35, series:"С-2610-02"},
  {warehouse:"karagandy", code:"PG-6002", sap:"00946670061", name:"Наливной пол, мешок 20 кг", group:"Клеи и смеси", unit:"мешок", stock:0, reserved:0, defect:0, batch:"—", exp:"—", statusProduct:"Активен", packageType:"Мешок", productType:"Расходные материалы", batchTracking:"Нет", expiryTracking:"Нет", hazardClass:"Не применяется", abcClass:"C", shelfLifeDays:"—", countryOrigin:"Турция", brand:"—", barcode:"4600200000000", weightGross:4.37, weightNet:4.1, volume:0.0361, length:87, width:63, height:38, series:"С-2611-03"},
];
const receipts_polovoygigant = [
  {warehouse:"karagandy", no:"ПР-000331", date:"29.07.2026", eta:"01.08.2026", supplier:suppliers_polovoygigant[0], items:4, volume:22.5, transport:"№ 118-ПГ / вод. Садвакасов Н.", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-1000", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300000", gtd:"ГТД № 50000000/2026", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"700KZH01", trailerNumber:"P100KZH01", techPassportNumber:"KZ1000000", containerNumber:"CONT5000", sealNumber:"S40000", driverName:"Оспанов Д.Е.", driverPhone:"8 701 300 10 20", driverIdNumber:"N4000000", driverInn:"900000000000", weight:72.0, lineCount:4, palletCount:1, cargoPlaces:1, totalPieces:40, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ПР-000330", date:"27.07.2026", eta:"29.07.2026", supplier:suppliers_polovoygigant[1], items:3, volume:15.8, transport:"№ 402-ПГ / вод. Ержигитов Д.", status:"warn", statusLabel:"Ожидает приёмки", erpNo:"ERP-2026-1001", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300001", gtd:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"701KZH01", trailerNumber:"—", techPassportNumber:"KZ1000001", containerNumber:"—", sealNumber:"S40001", driverName:"Ертаев С.Б.", driverPhone:"8 702 301 11 21", driverIdNumber:"N4000001", driverInn:"900000000001", weight:64.2, lineCount:3, palletCount:1, cargoPlaces:2, totalPieces:33, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ПР-000329", date:"24.07.2026", eta:"26.07.2026", supplier:suppliers_polovoygigant[0], items:6, volume:31.2, transport:"№ 275-ПГ / вод. Калиев Б.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1002", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300002", gtd:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"702KZH01", trailerNumber:"P102KZH01", techPassportNumber:"KZ1000002", containerNumber:"—", sealNumber:"S40002", driverName:"Мукатаев Б.А.", driverPhone:"8 703 302 12 22", driverIdNumber:"N4000002", driverInn:"900000000002", weight:148.8, lineCount:6, palletCount:1, cargoPlaces:1, totalPieces:72, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ПР-000328", date:"20.07.2026", eta:"22.07.2026", supplier:suppliers_polovoygigant[1], items:2, volume:9.4, transport:"№ 819-ПГ / вод. Мұратов С.", status:"err", statusLabel:"Расхождение по кол-ву", erpNo:"ERP-2026-1003", docType:"Стандартный", comment:"—", accompanyingDocNumber:"ТТН-300003", gtd:"ГТД № 50000003/2026", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"703KZH01", trailerNumber:"—", techPassportNumber:"KZ1000003", containerNumber:"CONT5003", sealNumber:"S40003", driverName:"Сагындыков Р.К.", driverPhone:"8 704 303 13 23", driverIdNumber:"N4000003", driverInn:"900000000003", weight:56.4, lineCount:2, palletCount:1, cargoPlaces:2, totalPieces:26, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ПР-000327", date:"16.07.2026", eta:"18.07.2026", supplier:suppliers_polovoygigant[0], items:5, volume:19.6, transport:"№ 553-ПГ / вод. Тастанов Е.", status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-1004", docType:"Стандартный", comment:"Требуется усиленный контроль качества", accompanyingDocNumber:"ТТН-300004", gtd:"—", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"704KZH01", trailerNumber:"P104KZH01", techPassportNumber:"KZ1000004", containerNumber:"—", sealNumber:"S40004", driverName:"Оспанов Д.Е.", driverPhone:"8 705 304 14 24", driverIdNumber:"N4000004", driverInn:"900000000004", weight:158.0, lineCount:5, palletCount:1, cargoPlaces:1, totalPieces:70, author:"Половцева Ирина Сергеевна"},
];
const orders_polovoygigant = [
  {warehouse:"karagandy", no:"ЗК-000271", date:"29.07.2026", ship:"01.08.2026", consignee:consignees_polovoygigant[0], deliveryAddress:consigneeAddresses_polovoygigant[consignees_polovoygigant[0]], items:5, weight:860, orderType:"Обычный", plannedShip:"01.08.2026", volume:2.0, transport:"№ 118-ПГ / ТОО «КазТранс»", status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-2000", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"800KZH01", trailerNumber:"P200KZH01", techPassportNumber:"KZ2000000", containerNumber:"CONT6000", sealNumber:"S50000", driverName:"Байтасов Р.С.", driverPhone:"8 701 400 15 25", driverIdNumber:"N5000000", driverInn:"910000000000", lineCount:3, palletCount:2, cargoPlaces:2, totalPieces:24, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ЗК-000270", date:"27.07.2026", ship:"29.07.2026", consignee:consignees_polovoygigant[1], deliveryAddress:consigneeAddresses_polovoygigant[consignees_polovoygigant[1]], items:2, weight:140, orderType:"Экспресс", plannedShip:"29.07.2026", volume:3.7, transport:"№ 340-ПГ / ИП Полов Р.Б.", status:"warn", statusLabel:"Комплектуется", erpNo:"ERP-2026-2001", comment:"—", carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"801KZH01", trailerNumber:"—", techPassportNumber:"KZ2000001", containerNumber:"—", sealNumber:"S50001", driverName:"Нурлан А.Т.", driverPhone:"8 702 401 16 26", driverIdNumber:"N5000001", driverInn:"910000000001", lineCount:4, palletCount:2, cargoPlaces:3, totalPieces:36, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ЗК-000269", date:"25.07.2026", ship:"27.07.2026", consignee:consignees_polovoygigant[0], deliveryAddress:consigneeAddresses_polovoygigant[consignees_polovoygigant[0]], items:7, weight:1120, orderType:"Обычный", plannedShip:"27.07.2026", volume:5.4, transport:"№ 771-ПГ / ТОО «ТрансЛог»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2002", comment:"—", carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"802KZH01", trailerNumber:"P202KZH01", techPassportNumber:"KZ2000002", containerNumber:"—", sealNumber:"S50002", driverName:"Досжанов Е.М.", driverPhone:"8 703 402 17 27", driverIdNumber:"N5000002", driverInn:"910000000002", lineCount:5, palletCount:2, cargoPlaces:2, totalPieces:50, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ЗК-000268", date:"22.07.2026", ship:"24.07.2026", consignee:consignees_polovoygigant[1], deliveryAddress:consigneeAddresses_polovoygigant[consignees_polovoygigant[1]], items:1, weight:65, orderType:"Экспресс", plannedShip:"24.07.2026", volume:7.1, transport:"№ 205-ПГ / ИП Полов Р.Б.", status:"err", statusLabel:"Задержка отгрузки", erpNo:"ERP-2026-2003", comment:"—", carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"803KZH01", trailerNumber:"—", techPassportNumber:"KZ2000003", containerNumber:"CONT6003", sealNumber:"S50003", driverName:"Жумабеков С.К.", driverPhone:"8 704 403 18 28", driverIdNumber:"N5000003", driverInn:"910000000003", lineCount:6, palletCount:3, cargoPlaces:4, totalPieces:66, author:"Половцева Ирина Сергеевна"},
  {warehouse:"karagandy", no:"ЗК-000267", date:"18.07.2026", ship:"20.07.2026", consignee:consignees_polovoygigant[0], deliveryAddress:consigneeAddresses_polovoygigant[consignees_polovoygigant[0]], items:4, weight:590, orderType:"Обычный", plannedShip:"20.07.2026", volume:8.8, transport:"№ 449-ПГ / ТОО «КазТранс»", status:"ok", statusLabel:"Отгружен", erpNo:"ERP-2026-2004", comment:"Клиент просит уведомить за час до прибытия", carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"804KZH01", trailerNumber:"P204KZH01", techPassportNumber:"KZ2000004", containerNumber:"—", sealNumber:"S50004", driverName:"Байтасов Р.С.", driverPhone:"8 705 404 19 29", driverIdNumber:"N5000004", driverInn:"910000000004", lineCount:7, palletCount:4, cargoPlaces:4, totalPieces:84, author:"Половцева Ирина Сергеевна"},
];
const reports_polovoygigant = [
  {name:"Наличие на складе — сводный", period:"на 31.07.2026", requested:"31.07.2026 09:25", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Отчёт по приёмке за июль", period:"01.07.2026 – 31.07.2026", requested:"31.07.2026 08:15", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Отчёт по отгрузкам за июль", period:"01.07.2026 – 31.07.2026", requested:"30.07.2026 18:05", format:"XLSX", status:"ok", statusLabel:"Готов"},
  {name:"Сводный отчёт по оказанным услугам", period:"01.07.2026 – 31.07.2026", requested:"30.07.2026 12:50", format:"XLSX", status:"warn", statusLabel:"Формируется"},
  {name:"KPI 3PL-оператора", period:"II квартал 2026", requested:"29.07.2026 16:20", format:"XLSX", status:"ok", statusLabel:"Готов"},
];

const transfers_rogaikopyta = [
  {no:"ПМ-000005", date:"29.07.2026", shipDate:"30.07.2026", receivedDate:"—", fromWarehouse:"almaty", toWarehouse:"karagandy", items:3, lineCount:3, totalPieces:0, status:"draft", statusLabel:"Черновик", erpNo:"ERP-2026-3000", comment:"Требуется усиленный контроль качества упаковки", weight:60.0, volume:1.2, palletCount:1, cargoPlaces:1, carrier:"ТОО «КазТранс»", transportType:"Грузовой автомобиль", transportBrand:"Kamaz 65117", carNumber:"900KZH01", trailerNumber:"P300KZH01", techPassportNumber:"KZ3000000", containerNumber:"CONT7000", sealNumber:"S60000", driverName:"Ахметов Р.С.", driverPhone:"8 701 500 12 22", driverIdNumber:"N6000000", driverInn:"920000000000", author:"Серикова Айгерим Батыровна"},
  {no:"ПМ-000004", date:"25.07.2026", shipDate:"26.07.2026", receivedDate:"—", fromWarehouse:"almaty", toWarehouse:"karagandy", items:5, lineCount:5, totalPieces:0, status:"warn", statusLabel:"В пути", erpNo:"ERP-2026-3001", comment:"—", weight:69.4, volume:1.7, palletCount:2, cargoPlaces:3, carrier:"ИП Оспанов", transportType:"Автопоезд", transportBrand:"MAN TGX", carNumber:"901KZH01", trailerNumber:"—", techPassportNumber:"KZ3000001", containerNumber:"—", sealNumber:"S60001", driverName:"Бекенов Т.А.", driverPhone:"8 702 501 13 23", driverIdNumber:"N6000001", driverInn:"920000000001", author:"Серикова Айгерим Батыровна"},
  {no:"ПМ-000003", date:"20.07.2026", shipDate:"20.07.2026", receivedDate:"22.07.2026", fromWarehouse:"karagandy", toWarehouse:"almaty", items:2, lineCount:2, totalPieces:0, status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-3002", comment:"—", weight:78.8, volume:2.2, palletCount:3, cargoPlaces:3, carrier:"ТОО «АвтоЛогистик»", transportType:"Фургон", transportBrand:"Hino 500", carNumber:"902KZH01", trailerNumber:"P302KZH01", techPassportNumber:"KZ3000002", containerNumber:"—", sealNumber:"S60002", driverName:"Досов Е.Н.", driverPhone:"8 703 502 14 24", driverIdNumber:"N6000002", driverInn:"920000000002", author:"Серикова Айгерим Батыровна"},
  {no:"ПМ-000002", date:"14.07.2026", shipDate:"14.07.2026", receivedDate:"16.07.2026", fromWarehouse:"almaty", toWarehouse:"karagandy", items:6, lineCount:6, totalPieces:0, status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-3003", comment:"Требуется усиленный контроль качества упаковки", weight:88.2, volume:2.7, palletCount:4, cargoPlaces:5, carrier:"ИП Ертаев", transportType:"Грузовой автомобиль", transportBrand:"Volvo FH", carNumber:"903KZH01", trailerNumber:"—", techPassportNumber:"KZ3000003", containerNumber:"CONT7003", sealNumber:"S60003", driverName:"Каримов С.Б.", driverPhone:"8 704 503 15 25", driverIdNumber:"N6000003", driverInn:"920000000003", author:"Серикова Айгерим Батыровна"},
  {no:"ПМ-000001", date:"05.07.2026", shipDate:"05.07.2026", receivedDate:"07.07.2026", fromWarehouse:"almaty", toWarehouse:"karagandy", items:4, lineCount:4, totalPieces:0, status:"ok", statusLabel:"Принято", erpNo:"ERP-2026-3004", comment:"—", weight:97.6, volume:3.2, palletCount:1, cargoPlaces:1, carrier:"ТОО «КазТранс»", transportType:"Автопоезд", transportBrand:"Kamaz 65117", carNumber:"904KZH01", trailerNumber:"P304KZH01", techPassportNumber:"KZ3000004", containerNumber:"—", sealNumber:"S60004", driverName:"Ахметов Р.С.", driverPhone:"8 705 504 16 26", driverIdNumber:"N6000004", driverInn:"920000000004", author:"Серикова Айгерим Батыровна"},
];
const transfers_boltygaiki = [];
const transfers_polovoygigant = [];

const BILLING_SERVICES_DATA = {
  rogaikopyta: [
    {service:'Хранение товара', rate:'14,50', unit:'KZT / паллето-место / сутки', frequency:'Ежедневно'},
    {service:'Приёмка товара', rate:'180,00', unit:'KZT / паллета', frequency:'За операцию'},
    {service:'Отбор и упаковка заказа', rate:'95,00', unit:'KZT / строка заказа', frequency:'За операцию'},
    {service:'Погрузо-разгрузочные работы', rate:'6 500,00', unit:'KZT / машина', frequency:'За операцию'},
    {service:'Кросс-докинг', rate:'220,00', unit:'KZT / паллета', frequency:'За операцию'},
  ],
  boltygaiki: [
    {service:'Хранение товара', rate:'13,80', unit:'KZT / паллето-место / сутки', frequency:'Ежедневно'},
    {service:'Приёмка товара', rate:'165,00', unit:'KZT / паллета', frequency:'За операцию'},
    {service:'Стикеровка / маркировка товара', rate:'12,00', unit:'KZT / шт', frequency:'За операцию'},
    {service:'Формирование комплектов (сборка наборов)', rate:'45,00', unit:'KZT / комплект', frequency:'За операцию'},
  ],
  polovoygigant: [
    {service:'Ответственное хранение с контролем сроков годности', rate:'3,00', unit:'KZT / шт / сутки', frequency:'Ежедневно'},
    {service:'Приёмка товара', rate:'190,00', unit:'KZT / паллета', frequency:'За операцию'},
    {service:'Отбор и упаковка заказа', rate:'99,00', unit:'KZT / строка заказа', frequency:'За операцию'},
  ],
};
const BILLING_INVOICES_DATA = {
  rogaikopyta: [
    {no:'СЧ-000712', period:'01.07.2026 – 31.07.2026', issueDate:'01.08.2026', amountNoVat:'174 324,00', amountVat:'195 242,88', status:'warn', statusLabel:'Ожидает оплаты'},
    {no:'СЧ-000689', period:'01.06.2026 – 30.06.2026', issueDate:'01.07.2026', amountNoVat:'158 960,00', amountVat:'178 035,20', status:'ok', statusLabel:'Оплачен'},
    {no:'СЧ-000654', period:'01.05.2026 – 31.05.2026', issueDate:'01.06.2026', amountNoVat:'142 110,00', amountVat:'159 163,20', status:'ok', statusLabel:'Оплачен'},
  ],
  boltygaiki: [
    {no:'СЧ-000701', period:'01.07.2026 – 31.07.2026', issueDate:'01.08.2026', amountNoVat:'96 480,00', amountVat:'108 057,60', status:'err', statusLabel:'Просрочен'},
    {no:'СЧ-000677', period:'01.06.2026 – 30.06.2026', issueDate:'01.07.2026', amountNoVat:'88 210,00', amountVat:'98 795,20', status:'ok', statusLabel:'Оплачен'},
  ],
  polovoygigant: [
    {no:'СЧ-000699', period:'01.07.2026 – 31.07.2026', issueDate:'01.08.2026', amountNoVat:'61 050,00', amountVat:'68 376,00', status:'ok', statusLabel:'Оплачен'},
  ],
};
const DEPOSITOR_DATA = {
  rogaikopyta: {products:products_rogaikopyta, receipts:receipts_rogaikopyta, orders:orders_rogaikopyta, reports:reports_rogaikopyta, transfers:transfers_rogaikopyta},
  boltygaiki:  {products:products_boltygaiki,  receipts:receipts_boltygaiki,  orders:orders_boltygaiki,  reports:reports_boltygaiki, transfers:transfers_boltygaiki},
  polovoygigant: {products:products_polovoygigant, receipts:receipts_polovoygigant, orders:orders_polovoygigant, reports:reports_polovoygigant, transfers:transfers_polovoygigant},
  none: {products:[], receipts:[], orders:[], reports:[], transfers:[]},
  choose: {products:[], receipts:[], orders:[], reports:[], transfers:[]},
};
(function fcAssignTripCodes(){
  const prefixes = {receipts:'ПРБ', orders:'ОТГ', transfers:'ПРМ'};
  Object.keys(DEPOSITOR_DATA).forEach(kId=>{
    if(kId === 'none') return;
    ['receipts','orders','transfers'].forEach(docType=>{
      (DEPOSITOR_DATA[kId][docType] || []).forEach((doc,i)=>{
        if(doc.status !== 'draft' && !doc.trip){
          const datePart = (doc.date || '').split('.').reverse().join('').slice(2) || '260701';
          doc.trip = `${prefixes[docType]}-${datePart}-${String(i+1).padStart(2,'0')}`;
        }
      });
    });
  });
})();

let products = DEPOSITOR_DATA[currentDepositorId].products;
let receipts = DEPOSITOR_DATA[currentDepositorId].receipts;
let orders   = DEPOSITOR_DATA[currentDepositorId].orders;
let reports  = DEPOSITOR_DATA[currentDepositorId].reports;
let transfers = DEPOSITOR_DATA[currentDepositorId].transfers;

function updateWarehouseFilterUI(depositorId){
  const allowed = (DEPOSITOR_WAREHOUSES[depositorId] || Object.keys(WAREHOUSES));
  if(!allowed.includes(currentWarehouseFilter)) currentWarehouseFilter = 'all';
  const el = document.getElementById('warehouseFilter');
  if(!el) return;
  el.textContent = currentWarehouseFilter === 'all' ? T('common.button.all_warehouses') : WAREHOUSES[currentWarehouseFilter].name;
  el.classList.toggle('switchable', allowed.length > 1);
}

function setDepositorData(depositorId){
  currentDepositorId = depositorId;
  const d = DEPOSITOR_DATA[depositorId] || DEPOSITOR_DATA.none;
  products = d.products; receipts = d.receipts; orders = d.orders; reports = d.reports; transfers = d.transfers || [];
  ['receipts','orders','stock','sku','transfers'].forEach(k=>{ if(paginationState[k]) paginationState[k].page = 1; });
  updateWarehouseFilterUI(depositorId);
  renderReceipts(); renderOrders(); renderStock(); renderSku(); renderReports(); renderTransfers();
  renderBillingServices(); renderBillingInvoices();
  if(typeof renderSuppliers==='function'){ renderSuppliers(); renderConsignees(); }
  if(typeof renderPackaging==='function'){ renderPackaging(); }
  document.getElementById('tabsOrg').textContent = (ALL_DEPOSITORS[depositorId] || EMPTY_DEPOSITOR).name;
}
function renderBillingServices(){
  const body = document.getElementById('billingServicesBody');
  if(!body) return;
  const list = BILLING_SERVICES_DATA[currentDepositorId] || [];
  body.innerHTML = list.length ? list.map(s=>`
    <tr><td>${s.service}</td><td class="num">${s.rate}</td><td>${s.unit}</td><td>${s.frequency}</td></tr>
  `).join('') : `<tr><td colspan="4" style="color:var(--muted);">${T('common.text.no_rates_defined_for_this_deposito')}</td></tr>`;
}
function renderBillingInvoices(){
  const body = document.getElementById('billingInvoicesBody');
  if(!body) return;
  const list = BILLING_INVOICES_DATA[currentDepositorId] || [];
  body.innerHTML = list.length ? list.map(inv=>`
    <tr>
      <td><strong>${inv.no}</strong></td><td>${inv.period}</td><td>${inv.issueDate}</td>
      <td class="num">${inv.amountNoVat}</td><td class="num">${inv.amountVat}</td>
      <td>${badge(inv.status, inv.statusLabel)}</td>
      <td><button class="btn ghost" onclick="event.stopPropagation(); notify('Скачивается: Счёт_${inv.no}.pdf')">${T('common.text.download_pdf')}</button></td>
    </tr>
  `).join('') : `<tr><td colspan="7" style="color:var(--muted);">${T('common.text.no_invoices_for_this_depositor')}</td></tr>`;
}
function setWarehouseFilter(wid){
  currentWarehouseFilter = wid;
  document.getElementById('warehouseSwitcher').classList.remove('active');
  if(currentUserRole === 'staff'){
    setStaffData();
    return;
  }
  updateWarehouseFilterUI(currentDepositorId);
  ['receipts','orders','stock','sku','transfers'].forEach(k=>{ if(paginationState[k]) paginationState[k].page = 1; });
  renderReceipts(); renderOrders(); renderStock(); renderSku(); renderTransfers();
  if(typeof renderSuppliers==='function'){ renderSuppliers(); renderConsignees(); }
}
function toggleWarehouseSwitcher(){
  const el = document.getElementById('warehouseSwitcher');
  if(currentUserRole === 'staff'){
    if(currentStaffWarehouses.length <= 1) return;
    el.innerHTML = `<div class="ks-title">Фильтр по складу</div>
      <div class="ks-item ${currentWarehouseFilter==='all'?'current':''}" onclick="setWarehouseFilter('all')"><span>${T('common.button.all_warehouses')}</span>${currentWarehouseFilter==='all'?'<span>✓</span>':''}</div>` +
      currentStaffWarehouses.map(wid=>`<div class="ks-item ${currentWarehouseFilter===wid?'current':''}" onclick="setWarehouseFilter('${wid}')"><span>${WAREHOUSES[wid].name}</span>${currentWarehouseFilter===wid?'<span>✓</span>':''}</div>`).join('');
    el.classList.toggle('active');
    return;
  }
  const allowed = (DEPOSITOR_WAREHOUSES[currentDepositorId] || Object.keys(WAREHOUSES));
  if(allowed.length <= 1) return;
  el.innerHTML = `<div class="ks-title">Фильтр по складу</div>
    <div class="ks-item ${currentWarehouseFilter==='all'?'current':''}" onclick="setWarehouseFilter('all')"><span>${T('common.button.all_warehouses')}</span>${currentWarehouseFilter==='all'?'<span>✓</span>':''}</div>` +
    allowed.map(wid=>`<div class="ks-item ${currentWarehouseFilter===wid?'current':''}" onclick="setWarehouseFilter('${wid}')"><span>${WAREHOUSES[wid].name}</span>${currentWarehouseFilter===wid?'<span>✓</span>':''}</div>`).join('');
  el.classList.toggle('active');
}
document.addEventListener('click', (e)=>{
  const wrap = document.getElementById('warehouseFilter')?.closest('.tabs-org-wrap');
  if(wrap && !wrap.contains(e.target)) document.getElementById('warehouseSwitcher')?.classList.remove('active');
});

/* ============== РЕНДЕР ============== */
/* ============== РЕНДЕР ============== */

const STATUS_LABELS = {
  draft: {ru:'Черновик', en:'Draft'},
  warn_receipt_wait: {ru:'Ожидает приёмки', en:'Awaiting receiving'},
  warn_progress: {ru:'В работе', en:'In progress'},
  ok_received: {ru:'Принято', en:'Received'},
  err_qty: {ru:'Расхождение по кол-ву', en:'Quantity mismatch'},
  warn_reserve: {ru:'В резерве', en:'Reserved'},
  warn_picking: {ru:'Комплектуется', en:'Picking'},
  ok_shipped: {ru:'Отгружен', en:'Shipped'},
  err_delay: {ru:'Задержка отгрузки', en:'Shipment delayed'},
  warn_transit: {ru:'В пути', en:'In transit'},
};
const STATUS_LABEL_BY_RU = {};
Object.entries(STATUS_LABELS).forEach(([key,v])=>{ STATUS_LABEL_BY_RU[v.ru] = key; });
function localizeStatusLabel(ruLabel){
  const key = STATUS_LABEL_BY_RU[ruLabel];
  if(key && STATUS_LABELS[key]) return STATUS_LABELS[key][currentLang] || ruLabel;
  return ruLabel;
}
function badge(status, label){
  label = localizeStatusLabel(label);
  return `<span class="badge ${status}"><span class="dot"></span>${label}</span>`;
}

/* --- Конфигурация колонок: id, заголовок, признак числовой колонки, функция вывода значения --- */
function getProductStockSummary(p){
  if(p.__stockSummary) return p.__stockSummary;
  const batches = groupBatchRows(mockBatches(p));
  const blocked = batches.reduce((s,b)=>s+b.blocked, 0);
  const available = batches.reduce((s,b)=>s+b.available, 0);
  p.__stockSummary = {blocked, available};
  return p.__stockSummary;
}
function getProductIncomingQty(p){
  if(p.__incomingQty !== undefined) return p.__incomingQty;
  const pendingReceipts = (receipts || []).filter(r => r.status !== 'draft' && r.status !== 'ok');
  const seed = fcHashSeed(p.code + '_incoming');
  let total = 0;
  pendingReceipts.forEach((r, i) => {
    const includesThisSku = fcSeededRand(seed, i) > 0.6;
    if(includesThisSku){
      total += Math.floor(fcSeededRand(seed, i + 100) * 40) + 5;
    }
  });
  p.__incomingQty = total;
  return total;
}
const receiptCompositionColumnDefs = [
  {id:'code', key:'col.receiptComposition.code', label:'Коммерческий код', default:1, render:l=>l.code},
  {id:'name', key:'col.receiptComposition.name', label:'Наименование товара', default:1, wrap:true, render:l=>l.name},
  {id:'unit', key:'col.receiptComposition.unit', label:'Ед.', default:1, render:l=>l.unit || '—'},
  {id:'expected', key:'col.receiptComposition.expected', label:'Ожидается', default:1, num:true, render:l=>l.expected},
  {id:'expectedWms', key:'col.receiptComposition.expectedWms', label:'Ожидаемое количество WMS', default:1, num:true, render:l=>l.expectedWms ?? l.expected},
  {id:'accepted', key:'col.receiptComposition.accepted', label:'Принято', default:1, num:true, render:l=>l.accepted},
  {id:'defect', key:'col.receiptComposition.defect', label:'Брак', default:1, num:true, render:l=>l.defect},
  {id:'price', key:'col.receiptComposition.price', label:'Цена', default:1, num:true, render:l=>l.price ?? '—'},
  {id:'sum', key:'col.receiptComposition.sum', label:'Сумма', default:1, num:true, render:l=>(l.price && l.expected) ? (l.price*l.expected).toFixed(2) : '—'},
  {id:'currency', key:'col.receiptComposition.currency', label:'Валюта', default:1, render:l=>l.currency || '—'},
  {id:'lineComment', key:'col.receiptComposition.lineComment', label:'Комментарий', default:1, wrap:true, render:l=>l.lineComment || '—'},
];
const orderCompositionColumnDefs = [
  {id:'code', key:'col.orderComposition.code', label:'Коммерческий код', default:1, render:l=>l.code},
  {id:'name', key:'col.orderComposition.name', label:'Товар', default:1, wrap:true, render:l=>l.name},
  {id:'unit', key:'col.orderComposition.unit', label:'Ед.', default:1, render:l=>l.unit || '—'},
  {id:'ordered', key:'col.orderComposition.ordered', label:'Заказано', default:1, num:true, render:l=>l.ordered},
  {id:'orderedWms', key:'col.orderComposition.orderedWms', label:'Заказано WMS', default:1, num:true, render:l=>l.orderedWms ?? l.ordered},
  {id:'picked', key:'col.orderComposition.picked', label:'Собрано', default:1, num:true, render:l=>l.picked},
  {id:'weight', key:'col.orderComposition.weight', label:'Вес, кг', default:1, num:true, render:l=>l.weight},
  {id:'price', key:'col.orderComposition.price', label:'Цена', default:1, num:true, render:l=>l.price ?? '—'},
  {id:'sum', key:'col.orderComposition.sum', label:'Сумма', default:1, num:true, render:l=>(l.price && l.ordered) ? (l.price*l.ordered).toFixed(2) : '—'},
  {id:'currency', key:'col.orderComposition.currency', label:'Валюта', default:1, render:l=>l.currency || '—'},
  {id:'lineComment', key:'col.orderComposition.lineComment', label:'Комментарий', default:1, wrap:true, render:l=>l.lineComment || '—'},
];
const transferCompositionColumnDefs = [
  {id:'code', key:'col.transferComposition.code', label:'Коммерческий код', default:1, render:l=>l.code},
  {id:'name', key:'col.transferComposition.name', label:'Наименование товара', default:1, wrap:true, render:l=>l.name},
  {id:'unit', key:'col.transferComposition.unit', label:'Ед.', default:1, render:l=>l.unit || '—'},
  {id:'qty', key:'col.transferComposition.qty', label:'Кол-во', default:1, num:true, render:l=>l.qty},
  {id:'qtyWms', key:'col.transferComposition.qtyWms', label:'Количество WMS', default:1, num:true, render:l=>l.qtyWms ?? l.qty},
  {id:'lineComment', key:'col.transferComposition.lineComment', label:'Комментарий', default:1, wrap:true, render:l=>l.lineComment || '—'},
];
const orderLineDetailColumnDefs = [
  {id:'q', key:'col.orderLineDetail.q', label:'Качество', default:1, render:b=>b.q},
  {id:'batch', key:'col.orderLineDetail.batch', label:'Партия', default:1, render:b=>b.batch},
  {id:'series', key:'col.orderLineDetail.series', label:'Серия', default:1, render:b=>b.series},
  {id:'exp', key:'col.orderLineDetail.exp', label:'Срок годности', default:1, render:b=>b.exp},
  {id:'stock', key:'col.orderLineDetail.stock', label:'Собрано', default:1, num:true, render:b=>b.stock},
  ...Array.from({length:10}, (_,i)=>({id:'customField'+(i+1), key:'col.orderLineDetail.customField'+(i+1), label:T('common.label.custom_field')+' '+(i+1), default:0, render:b=>b['customField'+(i+1)] ?? '—'})),
];
const receiptLineDetailColumnDefs = [
  {id:'q', key:'col.receiptLineDetail.q', label:'Качество', default:1, render:b=>b.q},
  {id:'batch', key:'col.receiptLineDetail.batch', label:'Партия', default:1, render:b=>b.batch},
  {id:'series', key:'col.receiptLineDetail.series', label:'Серия', default:1, render:b=>b.series},
  {id:'exp', key:'col.receiptLineDetail.exp', label:'Срок годности', default:1, render:b=>b.exp},
  {id:'stock', key:'col.receiptLineDetail.stock', label:'Принято', default:1, num:true, render:b=>b.stock},
  ...Array.from({length:10}, (_,i)=>({id:'customField'+(i+1), key:'col.receiptLineDetail.customField'+(i+1), label:T('common.label.custom_field')+' '+(i+1), default:0, render:b=>b['customField'+(i+1)] ?? '—'})),
];
const transferLineDetailColumnDefs = [
  {id:'q', key:'col.transferLineDetail.q', label:'Качество', default:1, render:b=>b.q},
  {id:'batch', key:'col.transferLineDetail.batch', label:'Партия', default:1, render:b=>b.batch},
  {id:'series', key:'col.transferLineDetail.series', label:'Серия', default:1, render:b=>b.series},
  {id:'exp', key:'col.transferLineDetail.exp', label:'Срок годности', default:1, render:b=>b.exp},
  {id:'stock', key:'col.transferLineDetail.stock', label:'Кол-во', default:1, num:true, render:b=>b.stock},
  ...Array.from({length:10}, (_,i)=>({id:'customField'+(i+1), key:'col.transferLineDetail.customField'+(i+1), label:T('common.label.custom_field')+' '+(i+1), default:0, render:b=>b['customField'+(i+1)] ?? '—'})),
];
const stockBatchColumnDefs = [
  {id:'batch', key:'col.stockBatch.batch', label:'Партия',              default:1, render:b=>b.batch},
  {id:'series', key:'col.stockBatch.series', label:'Серия',               default:1, render:b=>b.series},
  {id:'exp', key:'col.stockBatch.exp', label:'Срок годности',       default:1, render:b=>b.exp},
  {id:'stock', key:'col.stockBatch.stock', label:'Наличие',             default:1, num:true, render:b=>b.stock},
  {id:'available', key:'col.stockBatch.available', label:'Доступно',            default:1, num:true, render:b=>b.available},
  {id:'reserved', key:'col.stockBatch.reserved', label:'Резерв',              default:1, num:true, render:b=>b.reserved},
  {id:'q', key:'col.stockBatch.q', label:'Качество',            default:1, render:b=>badge(b.q==='Годен'?'ok':'err', b.q)},
  {id:'blockReason', key:'col.stockBatch.blockReason', label:'Причина блокировки',  default:1, render:b=>b.blockReason ? badge('warn', b.blockReason) : '—'},
  ...Array.from({length:10}, (_,i)=>({id:'customField'+(i+1), key:'col.stockBatch.customField'+(i+1), label:T('common.label.custom_field')+' '+(i+1), default:0, render:b=>b['customField'+(i+1)] ?? '—'})),
];
const stockColumnDefs = [
  {id:'warehouse', key:'col.stock.warehouse', label:'Склад',             render:p=>WAREHOUSES[p.warehouse]?.name || p.warehouse},
  {id:'code', key:'col.stock.code', label:'Код',                render:p=>`<strong>${p.code}</strong>`},
  {id:'name', key:'col.stock.name', label:'Наименование',       render:p=>p.name, wrap:true},
  {id:'group', key:'col.stock.group', label:'Группа',             render:p=>p.group},
  {id:'unit', key:'col.stock.unit', label:'Ед.',                render:p=>p.unit},
  {id:'stock', key:'col.stock.stock', label:'Наличие',            num:true, render:p=>p.stock},
  {id:'available', key:'col.stock.available', label:'Доступное количество', num:true, render:p=>getProductStockSummary(p).available},
  {id:'reserved', key:'col.stock.reserved', label:'Резерв',            num:true, render:p=>p.reserved},
  {id:'blocked', key:'col.stock.blocked', label:'Заблокировано',     num:true, render:p=>getProductStockSummary(p).blocked},
  {id:'defect', key:'col.stock.defect', label:'Брак',               num:true, render:p=>p.defect},
  {id:'incoming', key:'col.stock.incoming', label:'К получению',      num:true, render:p=>getProductIncomingQty(p)},
];

/* Колонки номенклатуры (мастер-данные товара). default:1 — выводится по умолчанию */
const skuColumnDefs = [
  {id:'itemCode', key:'col.sku.itemCode', label:'Код товара',                 default:1, render:p=>getProductFullInfo(p).itemCode},
  {id:'code', key:'col.sku.code', label:'Коммерческий код',           default:1, render:p=>`<strong>${p.code}</strong>`},
  {id:'name', key:'col.sku.name', label:'Наименование товара',        default:1, render:p=>p.name, wrap:true},
  {id:'unit', key:'col.sku.unit', label:'Базовая единица измерения',  default:1, render:p=>p.unit},
  {id:'hasPackaging', key:'col.sku.hasPackaging', dbField:'has_packaging', labelEn:'Uses Packaging', label:'Используются упаковки', default:0, render:p=>fcYesNo(PRODUCT_PACKAGING.some(x=>x.productCode===p.code))},
  {id:'orderUnit', key:'col.sku.orderUnit', labelEn:'Order Unit', label:'Единица заказа', default:0, render:p=>{
    const pk = PRODUCT_PACKAGING.find(x=>x.packagingCode===p.defaultOrderPackaging && x.productCode===p.code);
    return pk ? fcPackagingName(pk, p.unit) : '—';
  }},
  {id:'barcode', key:'col.sku.barcode', label:'Штрих код',                  default:1, render:p=>p.barcode},
  {id:'group', key:'col.sku.group', label:'Группа товара',              default:1, render:p=>p.group},
  {id:'productType', key:'col.sku.productType', label:'Тип товара',                 default:1, render:p=>p.productType},
  {id:'length', key:'col.sku.length', label:'Длина',                      default:1, render:p=>p.length, num:true},
  {id:'width', key:'col.sku.width', label:'Ширина',                     default:1, render:p=>p.width, num:true},
  {id:'height', key:'col.sku.height', label:'Высота',                     default:1, render:p=>p.height, num:true},
  {id:'volume', key:'col.sku.volume', label:'Объём',                      default:1, render:p=>p.volume, num:true},
  {id:'weightGross', key:'col.sku.weightGross', label:'Вес брутто',                 default:1, render:p=>p.weightGross, num:true},
  {id:'manufacturer', key:'col.sku.manufacturer', label:'Производитель',              default:1, render:p=>getProductFullInfo(p).manufacturer},
  {id:'countryOrigin', key:'col.sku.countryOrigin', label:'Страна производитель',       default:1, render:p=>p.countryOrigin},
  {id:'brand', key:'col.sku.brand', label:'Торговая марка',             default:1, render:p=>p.brand},
  {id:'isFragile', key:'col.sku.isFragile', label:'Хрупкий товар',              default:1, render:p=>fcYesNo(getProductFullInfo(p).isFragile)},
  {id:'isSerialTracked', key:'col.sku.isSerialTracked', label:'Учёт серийных номеров',      default:1, render:p=>fcYesNo(getProductFullInfo(p).isSerialTracked)},
  {id:'batchTracking', key:'col.sku.batchTracking', label:'Учёт партий',                default:1, render:p=>p.batchTracking},
  {id:'expiryTracking', key:'col.sku.expiryTracking', label:'Учёт срока годности',        default:1, render:p=>p.expiryTracking},
  {id:'statusProduct', key:'col.sku.statusProduct', label:'Статус товара',              default:1, render:p=>p.statusProduct},
  {id:'itemFullName', key:'col.sku.itemFullName', label:'Полное наименование товара', default:0, render:p=>getProductFullInfo(p).itemFullName, wrap:true},
  {id:'hazardClass', key:'col.sku.hazardClass', label:'Класс опасности',            default:0, render:p=>p.hazardClass},
  {id:'hazardGroup', key:'col.sku.hazardGroup', label:'Группа опасности',           default:0, render:p=>getProductFullInfo(p).hazardGroup},
  {id:'weightNet', key:'col.sku.weightNet', label:'Вес нетто',                  default:0, render:p=>getProductFullInfo(p).netWeight, num:true},
  {id:'maxStorageTemp', key:'col.sku.maxStorageTemp', label:'Макс. температура хранения', default:0, render:p=>getProductFullInfo(p).maxStorageTemp, num:true},
  {id:'minStorageTemp', key:'col.sku.minStorageTemp', label:'Мин. температура хранения',  default:0, render:p=>getProductFullInfo(p).minStorageTemp, num:true},
  {id:'maxHumidity', key:'col.sku.maxHumidity', label:'Макс. влажность хранения',   default:0, render:p=>getProductFullInfo(p).maxHumidity, num:true},
  {id:'minHumidity', key:'col.sku.minHumidity', label:'Мин. влажность хранения',    default:0, render:p=>getProductFullInfo(p).minHumidity, num:true},
  {id:'minDaysForReceipt', key:'col.sku.minDaysForReceipt', label:'Мин. кол-во дней для приёмки', default:0, render:p=>getProductFullInfo(p).minDaysForReceipt, num:true},
  {id:'minDaysForReservation', key:'col.sku.minDaysForReservation', label:'Мин. кол-во дней для резервирования', default:0, render:p=>getProductFullInfo(p).minDaysForReservation, num:true},
  {id:'shelfLifeDays', key:'col.sku.shelfLifeDays', label:'Срок хранения, дней',        default:0, render:p=>p.shelfLifeDays, num:true},
  {id:'color', key:'col.sku.color', label:'Цвет',                       default:0, render:p=>getProductFullInfo(p).color},
  {id:'size', key:'col.sku.size', label:'Размер',                     default:0, render:p=>getProductFullInfo(p).size},
  {id:'abcClass', key:'col.sku.abcClass', label:'ABC Класс',                  default:0, render:p=>p.abcClass},
  {id:'xyzClass', key:'col.sku.xyzClass', label:'XYZ Класс',                  default:0, render:p=>getProductFullInfo(p).xyzClass},
  {id:'guid', key:'col.sku.guid', label:'GUID',                       default:0, render:p=>getProductFullInfo(p).guid},
  {id:'hsCode', key:'col.sku.hsCode', label:'Код ТНВЭД',                  default:0, render:p=>getProductFullInfo(p).hsCode},
  {id:'enstruCode', key:'col.sku.enstruCode', label:'Код ЕНСТРУ',                 default:0, render:p=>getProductFullInfo(p).enstruCode},
  {id:'currency', key:'col.sku.currency', label:'Валюта',                     default:0, render:p=>getProductFullInfo(p).currency},
  {id:'cost', key:'col.sku.cost', label:'Стоимость',                  default:0, render:p=>getProductFullInfo(p).cost, num:true},
  {id:'comment', key:'col.sku.comment', label:'Комментарий',                default:0, render:p=>getProductFullInfo(p).comment, wrap:true},
  {id:'isKit', key:'col.sku.isKit', label:'Признак комплекта',          default:0, render:p=>fcYesNo(getProductFullInfo(p).isKit)},
  {id:'isMarkingRequired', key:'col.sku.isMarkingRequired', label:'Обязательная маркировка',  default:0, render:p=>fcYesNo(getProductFullInfo(p).isMarkingRequired)},
  {id:'isExcise', key:'col.sku.isExcise', label:'Акцизный товар',             default:0, render:p=>fcYesNo(getProductFullInfo(p).isExcise)},
  {id:'isControlled', key:'col.sku.isControlled', label:'Контролируемый/лицензируемый товар', default:0, render:p=>fcYesNo(getProductFullInfo(p).isControlled)},
  {id:'isWmsActive', key:'col.sku.isWmsActive', label:'Данные WMS действительны',   default:0, render:p=>fcYesNo(getProductFullInfo(p).isWmsActive)},
  {id:'createdBy', key:'col.sku.createdBy', label:'Создал',                     default:0, render:p=>getProductFullInfo(p).createdBy},
  {id:'createdAt', key:'col.sku.createdAt', label:'Создано',                    default:0, render:p=>getProductFullInfo(p).createdAt},
  {id:'updatedBy', key:'col.sku.updatedBy', label:'Изменил',                    default:0, render:p=>getProductFullInfo(p).updatedBy},
  {id:'updatedAt', key:'col.sku.updatedAt', label:'Изменено',                   default:0, render:p=>getProductFullInfo(p).updatedAt},
  ...Array.from({length:10}, (_,i)=>({id:'customAttr'+(i+1), key:'col.sku.customAttr'+(i+1), label:T('sku.label.custom_attribute')+' '+(i+1), default:0, render:p=>getProductFullInfo(p).customAttrs[i]})),
];

const tableColumns = {
  receipts: [
    {id:'status', key:'col.receipts.status', label:'Статус',           default:1, render:r=>badge(r.status, r.statusLabel)},
    {id:'erpNo', key:'col.receipts.erpNo', label:'Номер',            default:1, render:r=>`<strong>${r.erpNo}</strong>`},
    {id:'wmsNumber', key:'col.receipts.wmsNumber', label:'Номер документа в WMS', default:1, render:r=>r.wmsNumber || '—'},
    {id:'warehouse', key:'col.receipts.warehouse', label:'Склад',            default:1, render:r=>WAREHOUSES[r.warehouse]?.name || r.warehouse},
    {id:'date', key:'col.receipts.date', label:'Дата документа',   default:1, render:r=>r.date},
    {id:'eta', key:'col.receipts.eta', label:'Ожидаемая дата',   default:1, render:r=>r.eta},
    {id:'receivingEndDate', key:'col.receipts.receivingEndDate', label:'Дата приёмки', default:1, render:r=>r.status==='ok' ? r.eta : '—'},
    {id:'supplier', key:'col.receipts.supplier', label:'Поставщик',        default:1, render:r=>r.supplier},
    {id:'lineCount', key:'col.receipts.lineCount', label:'Кол-во строк',     default:1, num:true, render:r=>r.lineCount},
    {id:'weight', key:'col.receipts.weight', label:'Вес, кг',          default:1, num:true, render:r=>r.weight},
    {id:'volume', key:'col.receipts.volume', label:'Объём, м³',        default:1, num:true, render:r=>(typeof r.volume==='number' ? r.volume.toFixed(1) : (r.volume || '—'))},
    {id:'carNumber', key:'col.receipts.carNumber', label:'Номер авто',       default:1, render:r=>r.carNumber},
    {id:'driverName', key:'col.receipts.driverName', label:'Водитель',        default:1, render:r=>r.driverName},
    {id:'comment', key:'col.receipts.comment', label:'Комментарий',      default:1, render:r=>r.comment},
    {id:'no', key:'col.receipts.no', label:'ID документа',     default:0, render:r=>r.no},
    {id:'author', key:'col.receipts.author', label:'Автор',            default:0, render:r=>r.author},
    {id:'docType', key:'col.receipts.docType', label:'Тип приходного документа', default:0, render:r=>r.docType},
    {id:'arrivalDate', key:'col.receipts.arrivalDate', label:'Дата прибытия', default:0, render:r=>r.status!=='draft' ? r.eta : '—'},
    {id:'accompanyingDocNumber', key:'col.receipts.accompanyingDocNumber', label:'Номер сопроводительного документа', default:0, render:r=>r.accompanyingDocNumber},
    {id:'gtd', key:'col.receipts.gtd', label:'ГТД',              default:0, render:r=>r.gtd},
    {id:'palletCount', key:'col.receipts.palletCount', label:'Количество паллет', default:0, num:true, render:r=>r.palletCount},
    {id:'cargoPlaces', key:'col.receipts.cargoPlaces', label:'Количество грузомест', default:0, num:true, render:r=>r.cargoPlaces},
    {id:'totalPieces', key:'col.receipts.totalPieces', label:'Количество штук', default:0, num:true, render:r=>r.totalPieces},
    {id:'carrier', key:'col.receipts.carrier', label:'Перевозчик',       default:0, render:r=>r.carrier},
    {id:'transportType', key:'col.receipts.transportType', label:'Тип транспорта', default:0, render:r=>r.transportType},
    {id:'transportBrand', key:'col.receipts.transportBrand', label:'Марка транспорта', default:0, render:r=>r.transportBrand},
    {id:'trailerNumber', key:'col.receipts.trailerNumber', label:'Номер прицепа', default:0, render:r=>r.trailerNumber},
    {id:'techPassportNumber', key:'col.receipts.techPassportNumber', label:'Номер технического паспорта', default:0, render:r=>r.techPassportNumber},
    {id:'containerNumber', key:'col.receipts.containerNumber', label:'Номер контейнера', default:0, render:r=>r.containerNumber},
    {id:'sealNumber', key:'col.receipts.sealNumber', label:'Номер пломбы',   default:0, render:r=>r.sealNumber},
    {id:'driverPhone', key:'col.receipts.driverPhone', label:'Телефон водителя', default:0, render:r=>r.driverPhone},
    {id:'driverIdNumber', key:'col.receipts.driverIdNumber', label:'Номер удостоверения (паспорта) водителя', default:0, render:r=>r.driverIdNumber},
    {id:'driverInn', key:'col.receipts.driverInn', label:'ИИН (ИНН) водителя', default:0, render:r=>r.driverInn},
  ],
  orders: [
    {id:'status', key:'col.orders.status', label:'Статус',           default:1, render:o=>badge(o.status, o.statusLabel)},
    {id:'erpNo', key:'col.orders.erpNo', label:'Номер',            default:1, render:o=>`<strong>${o.erpNo}</strong>`},
    {id:'warehouse', key:'col.orders.warehouse', label:'Склад',            default:1, render:o=>WAREHOUSES[o.warehouse]?.name || o.warehouse},
    {id:'date', key:'col.orders.date', label:'Дата документа',   default:1, render:o=>o.date},
    {id:'ship', key:'col.orders.ship', label:'Дата отгрузки',    default:1, render:o=>o.ship},
    {id:'consignee', key:'col.orders.consignee', label:'Грузополучатель',  default:1, render:o=>o.consignee},
    {id:'lineCount', key:'col.orders.lineCount', label:'Кол-во строк',     default:1, num:true, render:o=>o.lineCount},
    {id:'weight', key:'col.orders.weight', label:'Вес, кг',          default:1, num:true, render:o=>o.weight},
    {id:'volume', key:'col.orders.volume', label:'Объём, м³',        default:1, num:true, render:o=>(typeof o.volume==='number' ? o.volume.toFixed(1) : o.volume)},
    {id:'carNumber', key:'col.orders.carNumber', label:'Номер авто',       default:1, render:o=>o.carNumber},
    {id:'driverName', key:'col.orders.driverName', label:'Водитель',        default:1, render:o=>o.driverName},
    {id:'comment', key:'col.orders.comment', label:'Комментарий',      default:1, render:o=>o.comment},
    {id:'deliveryAddress', key:'col.orders.deliveryAddress', label:'Место доставки', default:1, render:o=>o.deliveryAddress},
    {id:'no', key:'col.orders.no', label:'ID документа',     default:0, render:o=>o.no},
    {id:'wmsNumber', key:'col.orders.wmsNumber', label:'Номер документа в WMS', default:0, render:o=>o.wmsNumber || '—'},
    {id:'author', key:'col.orders.author', label:'Автор',            default:0, render:o=>o.author},
    {id:'orderType', key:'col.orders.orderType', label:'Тип заказа',       default:0, render:o=>o.orderType},
    {id:'plannedShip', key:'col.orders.plannedShip', label:'Планируемая дата отгрузки', default:0, render:o=>o.plannedShip},
    {id:'palletCount', key:'col.orders.palletCount', label:'Количество паллет', default:0, num:true, render:o=>o.palletCount},
    {id:'cargoPlaces', key:'col.orders.cargoPlaces', label:'Количество грузомест', default:0, num:true, render:o=>o.cargoPlaces},
    {id:'totalPieces', key:'col.orders.totalPieces', label:'Количество штук', default:0, num:true, render:o=>o.totalPieces},
    {id:'carrier', key:'col.orders.carrier', label:'Перевозчик',       default:0, render:o=>o.carrier},
    {id:'transportType', key:'col.orders.transportType', label:'Тип транспорта', default:0, render:o=>o.transportType},
    {id:'transportBrand', key:'col.orders.transportBrand', label:'Марка транспорта', default:0, render:o=>o.transportBrand},
    {id:'trailerNumber', key:'col.orders.trailerNumber', label:'Номер прицепа', default:0, render:o=>o.trailerNumber},
    {id:'techPassportNumber', key:'col.orders.techPassportNumber', label:'Номер технического паспорта', default:0, render:o=>o.techPassportNumber},
    {id:'containerNumber', key:'col.orders.containerNumber', label:'Номер контейнера', default:0, render:o=>o.containerNumber},
    {id:'sealNumber', key:'col.orders.sealNumber', label:'Номер пломбы',   default:0, render:o=>o.sealNumber},
    {id:'driverPhone', key:'col.orders.driverPhone', label:'Телефон водителя', default:0, render:o=>o.driverPhone},
    {id:'driverIdNumber', key:'col.orders.driverIdNumber', label:'Номер удостоверения (паспорта) водителя', default:0, render:o=>o.driverIdNumber},
    {id:'driverInn', key:'col.orders.driverInn', label:'ИИН (ИНН) водителя', default:0, render:o=>o.driverInn},
  ],
  stock: stockColumnDefs,
  stockBatch: stockBatchColumnDefs,
  receiptComposition: receiptCompositionColumnDefs,
  orderComposition: orderCompositionColumnDefs,
  transferComposition: transferCompositionColumnDefs,
  orderLineDetail: orderLineDetailColumnDefs,
  receiptLineDetail: receiptLineDetailColumnDefs,
  transferLineDetail: transferLineDetailColumnDefs,
  sku: skuColumnDefs,
  transfers: [
    {id:'status', key:'col.transfers.status', label:'Статус',           default:1, render:t=>badge(t.status, t.statusLabel)},
    {id:'erpNo', key:'col.transfers.erpNo', label:'Номер',            default:1, render:t=>`<strong>${t.erpNo}</strong>`},
    {id:'fromWarehouse', key:'col.transfers.fromWarehouse', label:'Склад-отправитель', default:1, render:t=>WAREHOUSES[t.fromWarehouse]?.name || t.fromWarehouse},
    {id:'toWarehouse', key:'col.transfers.toWarehouse', label:'Склад-получатель',  default:1, render:t=>WAREHOUSES[t.toWarehouse]?.name || t.toWarehouse},
    {id:'date', key:'col.transfers.date', label:'Дата документа',   default:1, render:t=>t.date},
    {id:'shipDate', key:'col.transfers.shipDate', label:'Дата отгрузки',    default:1, render:t=>t.shipDate},
    {id:'receivedDate', key:'col.transfers.receivedDate', label:'Дата приёмки', default:1, render:t=>t.receivedDate},
    {id:'lineCount', key:'col.transfers.lineCount', label:'Кол-во строк',     default:1, num:true, render:t=>t.lineCount},
    {id:'weight', key:'col.transfers.weight', label:'Вес, кг',          default:1, num:true, render:t=>t.weight},
    {id:'volume', key:'col.transfers.volume', label:'Объём, м³',        default:1, num:true, render:t=>(typeof t.volume==='number' ? t.volume.toFixed(1) : t.volume)},
    {id:'carNumber', key:'col.transfers.carNumber', label:'Номер авто',       default:1, render:t=>t.carNumber},
    {id:'driverName', key:'col.transfers.driverName', label:'Водитель',        default:1, render:t=>t.driverName},
    {id:'comment', key:'col.transfers.comment', label:'Комментарий',      default:1, render:t=>t.comment},
    {id:'no', key:'col.transfers.no', label:'ID документа',     default:0, render:t=>t.no},
    {id:'wmsNumber', key:'col.transfers.wmsNumber', label:'Номер документа в WMS', default:0, render:t=>t.wmsNumber || '—'},
    {id:'author', key:'col.transfers.author', label:'Автор',            default:0, render:t=>t.author},
    {id:'palletCount', key:'col.transfers.palletCount', label:'Количество паллет', default:0, num:true, render:t=>t.palletCount},
    {id:'cargoPlaces', key:'col.transfers.cargoPlaces', label:'Количество грузомест', default:0, num:true, render:t=>t.cargoPlaces},
    {id:'totalPieces', key:'col.transfers.totalPieces', label:'Количество штук', default:0, num:true, render:t=>t.totalPieces},
    {id:'carrier', key:'col.transfers.carrier', label:'Перевозчик',       default:0, render:t=>t.carrier},
    {id:'transportType', key:'col.transfers.transportType', label:'Тип транспорта', default:0, render:t=>t.transportType},
    {id:'transportBrand', key:'col.transfers.transportBrand', label:'Марка транспорта', default:0, render:t=>t.transportBrand},
    {id:'trailerNumber', key:'col.transfers.trailerNumber', label:'Номер прицепа', default:0, render:t=>t.trailerNumber},
    {id:'techPassportNumber', key:'col.transfers.techPassportNumber', label:'Номер технического паспорта', default:0, render:t=>t.techPassportNumber},
    {id:'containerNumber', key:'col.transfers.containerNumber', label:'Номер контейнера', default:0, render:t=>t.containerNumber},
    {id:'sealNumber', key:'col.transfers.sealNumber', label:'Номер пломбы',   default:0, render:t=>t.sealNumber},
    {id:'driverPhone', key:'col.transfers.driverPhone', label:'Телефон водителя', default:0, render:t=>t.driverPhone},
    {id:'driverIdNumber', key:'col.transfers.driverIdNumber', label:'Номер удостоверения (паспорта) водителя', default:0, render:t=>t.driverIdNumber},
    {id:'driverInn', key:'col.transfers.driverInn', label:'ИИН (ИНН) водителя', default:0, render:t=>t.driverInn},
  ],
};

/* Ключевое поле для перехода в детальную карточку по каждой таблице */
const tableKeyField = { receipts:'no', orders:'no', stock:'code', sku:'code', transfers:'no' };

/* Текущий порядок + видимость колонок для каждой таблицы (по умолчанию — всё видно, в исходном порядке) */
const tableColumnState = {
  receipts: tableColumns.receipts.map(c=>c.id),
  orders:   tableColumns.orders.map(c=>c.id),
  stock:    tableColumns.stock.map(c=>c.id),
  stockBatch: tableColumns.stockBatch.map(c=>c.id),
  receiptComposition: tableColumns.receiptComposition.map(c=>c.id),
  orderComposition: tableColumns.orderComposition.map(c=>c.id),
  transferComposition: tableColumns.transferComposition.map(c=>c.id),
  orderLineDetail: tableColumns.orderLineDetail.map(c=>c.id),
  receiptLineDetail: tableColumns.receiptLineDetail.map(c=>c.id),
  transferLineDetail: tableColumns.transferLineDetail.map(c=>c.id),
  sku: tableColumns.sku.map(c=>c.id),
  transfers: tableColumns.transfers.map(c=>c.id),
};
const tableColumnHidden = {
  receipts: new Set(tableColumns.receipts.filter(c=>!c.default).map(c=>c.id)),
  orders: new Set(tableColumns.orders.filter(c=>!c.default).map(c=>c.id)),
  stock:new Set(),
  stockBatch: new Set(tableColumns.stockBatch.filter(c=>!c.default).map(c=>c.id)),
  receiptComposition: new Set(tableColumns.receiptComposition.filter(c=>!c.default).map(c=>c.id)),
  orderComposition: new Set(tableColumns.orderComposition.filter(c=>!c.default).map(c=>c.id)),
  transferComposition: new Set(tableColumns.transferComposition.filter(c=>!c.default).map(c=>c.id)),
  orderLineDetail: new Set(tableColumns.orderLineDetail.filter(c=>!c.default).map(c=>c.id)),
  receiptLineDetail: new Set(tableColumns.receiptLineDetail.filter(c=>!c.default).map(c=>c.id)),
  transferLineDetail: new Set(tableColumns.transferLineDetail.filter(c=>!c.default).map(c=>c.id)),
  sku: new Set(tableColumns.sku.filter(c=>!c.default).map(c=>c.id)),
  transfers: new Set(tableColumns.transfers.filter(c=>!c.default).map(c=>c.id)),
};
/* Колонки, заблокированные администратором для текущего пользователя — не показываются даже в панели настройки колонок */
const tableColumnLocked = { receipts:new Set(), orders:new Set(), stock:new Set(), stockBatch:new Set(), receiptComposition:new Set(), orderComposition:new Set(), transferComposition:new Set(), orderLineDetail:new Set(), receiptLineDetail:new Set(), transferLineDetail:new Set(), sku:new Set(), transfers:new Set() };

const COLUMN_LABEL_I18N = {
  'Общая информация':'General Information', 'Состав поступления':'Receipt Contents', 'Состав заказа':'Order Contents',
  'Состав перемещения':'Transfer Contents', 'Транспортный раздел':'Transport Section', 'Дополнительная информация':'Additional Information',
  'Фотографии':'Photos', 'Услуги':'Services',
  'Вес брутто, кг':'Gross Weight, kg', 'Вес нетто, кг':'Net Weight, kg', 'Высота, см':'Height, cm',
  'Дата доставки':'Delivery Date', 'Дата окончания приёмки':'Receiving End Date', 'Длина, см':'Length, cm',
  'Количество строк':'Line Count', 'Контактные данные грузополучателя':'Consignee Contact Info',
  'Макс. влажность хранения, %':'Max Storage Humidity, %', 'Макс. температура хранения, °C':'Max Storage Temperature, °C',
  'Мин. влажность хранения, %':'Min Storage Humidity, %', 'Мин. температура хранения, °C':'Min Storage Temperature, °C',
  'Минимальное кол-во дней для приёмки':'Min Days for Receiving', 'Минимальное кол-во дней для резервирования':'Min Days for Reservation',
  'Номер Инвойса':'Invoice Number', 'Номер документа (в ERP клиента)':'Document Number (ERP Client)',
  'Номер документа WMS':'WMS Document Number', 'Ожидаемая дата поставки':'Expected Delivery Date',
  'Рейс':'Trip', 'ФИО водителя':'Driver Full Name', 'Ширина, см':'Width, cm',
  'ABC Класс':'ABC Class', 'ID документа':'Document ID', 'XYZ Класс':'XYZ Class', 'Автор':'Author',
  'Адреса доставки грузополучателей':'Consignee Delivery Addresses', 'Акцизный товар':'Excise Goods',
  'Базовая единица измерения':'Base Unit of Measure', 'Валюта':'Currency', 'Валюты':'Currencies',
  'Вес брутто':'Gross Weight', 'Вес нетто':'Net Weight', 'Водитель':'Driver', 'Высота':'Height', 'ГТД':'Customs Declaration',
  'Грузополучатели':'Consignees', 'Группа опасности':'Hazard Group', 'Группа товара':'Item Group',
  'Группы опасности':'Hazard Groups', 'Группы товара':'Item Groups', 'Данные WMS действительны':'WMS Data Valid',
  'Дата прибытия':'Arrival Date', 'Дата приёмки':'Receiving Date', 'Длина':'Length', 'Доп. параметр ':'Custom Attribute ',
  'Доп. поле ':'Custom Field ', 'Доступно':'Available', 'Доступное количество':'Available Quantity',
  'Доступность поклажедателей для пользователя':'Depositor Access for User', 'Доступность складов для пользователя':'Warehouse Access for User',
  'Единица заказа':'Order Unit', 'Единицы измерения':'Units of Measure', 'Заблокировано':'Blocked', 'Заказано':'Ordered',
  'Заказано WMS':'Ordered (WMS)', 'Заказы':'Orders', 'Запасы':'Stock', 'Зарегистрированные услуги':'Registered Services',
  'ИИН (ИНН) водителя':'Driver Tax ID', 'Изменено':'Modified', 'Изменил':'Modified By', 'Используются упаковки':'Uses Packaging',
  'К получению':'Incoming', 'Качество':'Quality', 'Класс опасности':'Hazard Class', 'Классы опасности':'Hazard Classes',
  'Код ЕНСТРУ':'ENSTRU Code', 'Код ТНВЭД':'HS Code', 'Код товара':'Item Code', 'Кол-во':'Qty', 'Кол-во строк':'Line Count',
  'Количество WMS':'Quantity (WMS)', 'Количество грузомест':'Cargo Places', 'Количество паллет':'Pallet Count',
  'Количество штук':'Piece Count', 'Комментарий':'Comment', 'Коммерческий код':'Commercial Code',
  'Контролируемый/лицензируемый товар':'Controlled/Licensed Item', 'Макс. влажность хранения':'Max Storage Humidity',
  'Макс. температура хранения':'Max Storage Temperature', 'Марка транспорта':'Vehicle Make',
  'Мин. влажность хранения':'Min Storage Humidity', 'Мин. кол-во дней для приёмки':'Min Days for Receiving',
  'Мин. кол-во дней для резервирования':'Min Days for Reservation', 'Мин. температура хранения':'Min Storage Temperature',
  'Наименование товара':'Item Name', 'Настройка полей поклажедателя':'Depositor Field Settings',
  'Настройки пользователей':'User Settings', 'Номер авто':'Vehicle Number', 'Номер документа в WMS':'WMS Document Number',
  'Номер контейнера':'Container Number', 'Номер пломбы':'Seal Number', 'Номер прицепа':'Trailer Number',
  'Номер сопроводительного документа':'Accompanying Document Number', 'Номер технического паспорта':'Technical Passport Number',
  'Номер удостоверения (паспорта) водителя':'Driver License Number', 'Объём':'Volume', 'Обязательная маркировка':'Mandatory Labeling',
  'Ожидаемое количество WMS':'Expected Quantity (WMS)', 'Ожидается':'Expected', 'Партия':'Batch', 'Переводы':'Translations',
  'Перевозчик':'Carrier', 'Перемещения':'Transfers', 'Поклажедатели':'Depositors', 'Полное наименование товара':'Full Item Name',
  'Пользователи':'Users', 'Поставщики':'Suppliers', 'Поступления':'Receipts', 'Признак комплекта':'Kit Flag',
  'Принято':'Accepted', 'Причина блокировки':'Block Reason', 'Причины блокировки':'Block Reasons', 'Производитель':'Manufacturer',
  'Размер':'Size', 'Роли пользователей':'User Roles', 'Серия':'Series', 'Склады':'Warehouses', 'Собрано':'Picked',
  'Создал':'Created By', 'Создано':'Created', 'Срок годности':'Expiry Date', 'Срок хранения, дней':'Shelf Life, Days',
  'Статус товара':'Item Status', 'Статусы':'Statuses', 'Стоимость':'Cost', 'Страна производитель':'Country of Origin',
  'Сумма':'Sum', 'Счета':'Invoices', 'Тарифы услуг поклажедателя':'Depositor Service Tariffs', 'Телефон водителя':'Driver Phone',
  'Тип приходного документа':'Receipt Document Type', 'Тип товара':'Item Type', 'Тип транспорта':'Transport Type',
  'Типы заказов':'Order Types', 'Типы объектов':'Object Types', 'Типы приходных документов':'Receipt Document Types',
  'Типы товара':'Item Types', 'Типы упаковки':'Packaging Types', 'Товар':'Item', 'Товары':'Products',
  'Торговая марка':'Brand', 'Упаковка':'Packaging', 'Учёт партий':'Batch Tracking', 'Учёт серийных номеров':'Serial Number Tracking',
  'Учёт срока годности':'Expiry Date Tracking', 'Фотографии документов':'Document Photos', 'Хрупкий товар':'Fragile Item',
  'Цвет':'Color', 'Цена':'Price', 'Ширина':'Width', 'Штрих код':'Barcode', 'Языки':'Languages',
  'Статус':'Status', 'Номер':'Number', 'Дата документа':'Document date', 'Ожидаемая дата':'Expected date',
  'Поставщик':'Supplier', 'Кол-во позиций':'Item count', 'Объём, м³':'Volume, m³', 'Транспорт':'Transport',
  'Дата создания':'Date created', 'Тип заказа':'Order type', 'Дата отгрузки':'Ship date',
  'Планируемая дата отгрузки':'Planned ship date', 'Грузополучатель':'Consignee', 'Место доставки':'Delivery address',
  'Вес, кг':'Weight, kg', 'Код':'Code',  'Наименование':'Name', 'Группа':'Category',
  'Ед.':'Unit', 'Наличие':'In stock', 'Резерв':'Reserved', 'Брак':'Defect', 'Партия / срок годности':'Batch / expiry',
  'Склад':'Warehouse', 'Дата':'Date', 'Склад-отправитель':'From warehouse', 'Склад-получатель':'To warehouse',
  'Дата отгрузки':'Ship date', 'Дата приёма':'Receipt date',
};
function localizeColumnLabel(ruLabel, tableKey, colId){
  if(tableKey && colId){
    const override = getDepositorFieldLabel(tableKey, colId);
    if(override) return override;
  }
  if(currentLang !== 'en') return ruLabel;
  if(COLUMN_LABEL_I18N[ruLabel]) return COLUMN_LABEL_I18N[ruLabel];
  const prefixMatch = ruLabel.match(/^(Доп\. поле |Доп\. параметр )(\d+)$/);
  if(prefixMatch) return COLUMN_LABEL_I18N[prefixMatch[1]] + prefixMatch[2];
  return ruLabel;
}
let sortState = {receipts:{col:null,dir:1}, orders:{col:null,dir:1}, transfers:{col:null,dir:1}, stock:{col:null,dir:1}, sku:{col:null,dir:1}};
function fcSortValue(item, col){
  if(item[col.id] !== undefined && item[col.id] !== null) return item[col.id];
  try{
    const html = col.render(item);
    return String(html).replace(/<[^>]+>/g,'');
  }catch(e){ return ''; }
}
function fcApplySort(tableKey, list){
  const st = sortState[tableKey];
  if(!st || !st.col) return list;
  const cols = tableColumns[tableKey];
  const col = cols.find(c=>c.id===st.col);
  if(!col) return list;
  return list.slice().sort((a,b)=>{
    let va = fcSortValue(a, col), vb = fcSortValue(b, col);
    const na = parseFloat(va), nb = parseFloat(vb);
    if(!isNaN(na) && !isNaN(nb) && va!=='' && vb!==''){ va = na; vb = nb; }
    if(va < vb) return -1 * st.dir;
    if(va > vb) return 1 * st.dir;
    return 0;
  });
}
function onSortColumnClick(tableKey, colId){
  const st = sortState[tableKey];
  if(!st) return;
  if(st.col === colId){ st.dir = -st.dir; } else { st.col = colId; st.dir = 1; }
  const renderFns = {receipts:renderReceipts, orders:renderOrders, transfers:renderTransfers, stock:renderStock, sku:renderSku};
  if(renderFns[tableKey]) renderFns[tableKey]();
}
function renderTableHead(tableKey){
  const cols = tableColumns[tableKey];
  const order = tableColumnState[tableKey];
  const hidden = tableColumnHidden[tableKey];
  const locked = tableColumnLocked[tableKey] || new Set();
  const st = sortState[tableKey];
  const head = order.filter(id=>!hidden.has(id) && !locked.has(id)).map(id=>{
    const c = cols.find(c=>c.id===id);
    const arrow = (st && st.col===id) ? (st.dir===1 ? ' ▲' : ' ▼') : '';
    return `<th${c.num?' class="num"':''} style="cursor:pointer; user-select:none;" onclick="onSortColumnClick('${tableKey}','${id}')" title="Сортировать по этому полю">${localizeColumnLabel(c.label, tableKey, id)}${arrow}</th>`;
  }).join('');
  document.getElementById(tableKey+'Head').innerHTML = head;
}

function renderTableRow(tableKey, item, onClickFn){
  const cols = tableColumns[tableKey];
  const order = tableColumnState[tableKey];
  const hidden = tableColumnHidden[tableKey];
  const locked = tableColumnLocked[tableKey] || new Set();
  const keyVal = item[tableKeyField[tableKey]];
  const cells = order.filter(id=>!hidden.has(id) && !locked.has(id)).map(id=>{
    const c = cols.find(c=>c.id===id);
    const cls = [c.num?'num':'', c.wrap?'wrap':''].filter(Boolean).join(' ');
    return `<td${cls?` class="${cls}"`:''}>${c.render(item)}</td>`;
  }).join('');
  return `<tr onclick="${onClickFn}('${keyVal}')">${cells}</tr>`;
}

/* ============== Пагинация ============== */
const paginationState = {
  receipts: {page:1, pageSize:20},
  orders: {page:1, pageSize:20},
  stock: {page:1, pageSize:20},
  sku: {page:1, pageSize:20},
  transfers: {page:1, pageSize:20},
  packaging: {page:1, pageSize:20},
};
function getPage(key, fullArray){
  const st = paginationState[key];
  const totalPages = Math.max(1, Math.ceil(fullArray.length / st.pageSize));
  if(st.page > totalPages) st.page = totalPages;
  if(st.page < 1) st.page = 1;
  const startIdx = (st.page - 1) * st.pageSize;
  return { items: fullArray.slice(startIdx, startIdx + st.pageSize), totalPages, total: fullArray.length };
}
function renderPaginationBar(key, totalPages, total){
  const el = document.getElementById(key + 'Pagination');
  if(!el) return;
  const st = paginationState[key];
  if(total === 0){ el.innerHTML = ''; return; }
  const t = currentLang === 'en'
    ? {rows:'Rows per page:', prev:'‹ Prev', next:'Next ›', page:'Page', of:'of', total:'total'}
    : {rows:'Строк на странице:', prev:'‹ Назад', next:'Вперёд ›', page:'Стр.', of:'из', total:'всего'};
  el.innerHTML = `
    <div class="page-size-select">${t.rows}
      <select onchange="changePageSize('${key}', this.value)">
        ${[10,20,50,100].map(n=>`<option value="${n}" ${st.pageSize===n?'selected':''}>${n}</option>`).join('')}
      </select>
    </div>
    <div class="pager">
      <button onclick="changePage('${key}', -1)" ${st.page<=1?'disabled':''}>${t.prev}</button>
      <span class="page-info">${t.page} ${st.page} ${t.of} ${totalPages} · ${t.total} ${total}</span>
      <button onclick="changePage('${key}', 1)" ${st.page>=totalPages?'disabled':''}>${t.next}</button>
    </div>`;
}
const TABLE_RENDER_FN = {};
function changePageSize(key, val){
  paginationState[key].pageSize = parseInt(val, 10);
  paginationState[key].page = 1;
  TABLE_RENDER_FN[key]();
}
function changePage(key, delta){
  paginationState[key].page += delta;
  TABLE_RENDER_FN[key]();
}

function byWarehouse(arr){
  return currentWarehouseFilter === 'all' ? arr : arr.filter(x=>x.warehouse===currentWarehouseFilter);
}
function parseRuDate(str){
  if(!str || str === '—') return null;
  const parts = String(str).split('.');
  if(parts.length !== 3) return null;
  const [d, m, y] = parts;
  const dt = new Date(+y, +m - 1, +d);
  return isNaN(dt.getTime()) ? null : dt;
}
function dateInRange(dateStr, fromISO, toISO){
  const d = parseRuDate(dateStr);
  if(!d) return true;
  if(fromISO){ const f = new Date(fromISO); if(d < f) return false; }
  if(toISO){ const t = new Date(toISO); t.setHours(23,59,59,999); if(d > t) return false; }
  return true;
}
let receiptsFiltersActive = false;
function onReceiptsFilterChange(){
  receiptsFiltersActive = true;
  paginationState.receipts.page = 1;
  renderReceipts();
}
function renderReceipts(){
  let filtered = byWarehouse(receipts);
  if(receiptsFiltersActive){
    const noQ = (document.getElementById('receiptsSearchNo')?.value || '').trim().toLowerCase();
    const supplierQ = (document.getElementById('receiptsSearchSupplier')?.value || '').trim().toLowerCase();
    const tripQ = (document.getElementById('receiptsSearchTrip')?.value || '').trim().toLowerCase();
    const statusQ = document.getElementById('receiptsFilterStatus')?.value || '';
    const fromISO = document.getElementById('receiptsDateFrom')?.value || '';
    const toISO = document.getElementById('receiptsDateTo')?.value || '';
    if(noQ) filtered = filtered.filter(r=>(r.erpNo||'').toLowerCase().includes(noQ) || (r.no||'').toLowerCase().includes(noQ));
    if(supplierQ) filtered = filtered.filter(r=>(r.supplier||'').toLowerCase().includes(supplierQ));
    if(tripQ) filtered = filtered.filter(r=>(r.trip||'').toLowerCase().includes(tripQ));
    if(statusQ && statusQ !== 'Все') filtered = filtered.filter(r=>(r.statusLabel||'').toLowerCase().includes(statusQ.toLowerCase()));
    filtered = filtered.filter(r=>dateInRange(r.date, fromISO, toISO));
  }
  filtered = fcApplySort('receipts', filtered);
  const { items, totalPages, total } = getPage('receipts', filtered);
  document.getElementById('receiptsCount').textContent = `(${total})`;
  renderTableHead('receipts');
  document.getElementById('receiptsBody').innerHTML = items.length ? items.map(r=>renderTableRow('receipts', r, 'openReceipt')).join('') : `<tr><td colspan="20" style="color:var(--muted);">${T('stock.text.nothing_found')}</td></tr>`;
  renderPaginationBar('receipts', totalPages, total);
  setTimeout(()=>updateTopScrollWidth('receipts'), 0);
}

let ordersFiltersActive = false;
function onOrdersFilterChange(){
  ordersFiltersActive = true;
  paginationState.orders.page = 1;
  renderOrders();
}
function renderOrders(){
  let filtered = byWarehouse(orders);
  if(ordersFiltersActive){
    const noQ = (document.getElementById('ordersSearchNo')?.value || '').trim().toLowerCase();
    const consigneeQ = (document.getElementById('ordersSearchConsignee')?.value || '').trim().toLowerCase();
    const tripQ = (document.getElementById('ordersSearchTrip')?.value || '').trim().toLowerCase();
    const statusQ = document.getElementById('ordersFilterStatus')?.value || '';
    const fromISO = document.getElementById('ordersDateFrom')?.value || '';
    const toISO = document.getElementById('ordersDateTo')?.value || '';
    if(noQ) filtered = filtered.filter(o=>(o.erpNo||'').toLowerCase().includes(noQ) || (o.no||'').toLowerCase().includes(noQ));
    if(consigneeQ) filtered = filtered.filter(o=>(o.consignee||'').toLowerCase().includes(consigneeQ));
    if(tripQ) filtered = filtered.filter(o=>(o.trip||'').toLowerCase().includes(tripQ));
    if(statusQ && statusQ !== 'Все') filtered = filtered.filter(o=>(o.statusLabel||'').toLowerCase().includes(statusQ.toLowerCase()));
    filtered = filtered.filter(o=>dateInRange(o.ship, fromISO, toISO));
  }
  filtered = fcApplySort('orders', filtered);
  const { items, totalPages, total } = getPage('orders', filtered);
  document.getElementById('ordersCount').textContent = `(${total})`;
  renderTableHead('orders');
  document.getElementById('ordersBody').innerHTML = items.length ? items.map(o=>renderTableRow('orders', o, 'openOrder')).join('') : `<tr><td colspan="20" style="color:var(--muted);">${T('stock.text.nothing_found')}</td></tr>`;
  renderPaginationBar('orders', totalPages, total);
  setTimeout(()=>updateTopScrollWidth('orders'), 0);
}

function groupStockRows(rows){
  const map = new Map();
  rows.forEach(p=>{
    const key = [p.warehouse, p.code, p.name, p.group, p.unit].join('|');
    if(map.has(key)){
      const g = map.get(key);
      g.stock += p.stock; g.reserved += p.reserved; g.defect += p.defect;
    }else{
      map.set(key, {...p});
    }
  });
  return [...map.values()];
}
function onStockFilterChange(){
  paginationState.stock.page = 1;
  renderStock();
}
function renderStock(){
  const codeQ = (document.getElementById('stockSearchCode')?.value || '').trim().toLowerCase();
  const nameQ = (document.getElementById('stockSearchName')?.value || '').trim().toLowerCase();
  const groupQ = document.getElementById('stockFilterGroup')?.value || '';
  const typeQ = document.getElementById('stockFilterType')?.value || '';
  let source = products.filter(p=>p.stock > 0);
  if(codeQ) source = source.filter(p=>(p.code||'').toLowerCase().includes(codeQ));
  if(nameQ) source = source.filter(p=>(p.name||'').toLowerCase().includes(nameQ));
  if(groupQ) source = source.filter(p=>p.group===groupQ);
  if(typeQ) source = source.filter(p=>p.productType===typeQ);
  let inStock = groupStockRows(byWarehouse(source));
  if(document.getElementById('stockDefectFilterCb')?.checked) inStock = inStock.filter(p=>p.defect > 0);
  inStock = fcApplySort('stock', inStock);
  const { items, totalPages, total } = getPage('stock', inStock);
  document.getElementById('stockCount').textContent = `(${total})`;
  renderTableHead('stock');
  document.getElementById('stockBody').innerHTML = items.length ? items.map(p=>renderTableRow('stock', p, 'openProduct')).join('') : `<tr><td colspan="20" style="color:var(--muted);">${T('stock.text.nothing_found')}</td></tr>`;
  renderPaginationBar('stock', totalPages, total);
  setTimeout(()=>updateTopScrollWidth('stock'), 0);
}

function onSkuFilterChange(){
  paginationState.sku.page = 1;
  renderSku();
}
function renderSku(){
  const codeQ = (document.getElementById('skuSearchCode')?.value || '').trim().toLowerCase();
  const nameQ = (document.getElementById('skuSearchName')?.value || '').trim().toLowerCase();
  const groupQ = document.getElementById('skuFilterGroup')?.value || '';
  const typeQ = document.getElementById('skuFilterType')?.value || '';
  let filtered = byWarehouse(products);
  if(codeQ) filtered = filtered.filter(p=>(p.code||'').toLowerCase().includes(codeQ));
  if(nameQ) filtered = filtered.filter(p=>(p.name||'').toLowerCase().includes(nameQ));
  if(groupQ) filtered = filtered.filter(p=>p.group===groupQ);
  if(typeQ) filtered = filtered.filter(p=>p.productType===typeQ);
  filtered = fcApplySort('sku', filtered);
  const { items, totalPages, total } = getPage('sku', filtered);
  document.getElementById('skuCount').textContent = `(${total})`;
  renderTableHead('sku');
  document.getElementById('skuBody').innerHTML = items.length ? items.map(p=>renderTableRow('sku', p, 'openSkuItem')).join('') : `<tr><td colspan="20" style="color:var(--muted);">${T('stock.text.nothing_found')}</td></tr>`;
  renderPaginationBar('sku', totalPages, total);
  setTimeout(()=>updateTopScrollWidth('sku'), 0);
}
function getScopedSupplierNames(){
  if(currentUserRole === 'staff'){
    return [...new Set((receipts||[]).map(r=>r.supplier).filter(Boolean))].sort();
  }
  const map = {rogaikopyta:suppliers_rogaikopyta, boltygaiki:suppliers_boltygaiki, polovoygigant:suppliers_polovoygigant};
  return (map[currentDepositorId] || []).slice().sort();
}
function getSupplierDepositorId(name){
  if(suppliers_rogaikopyta.includes(name)) return 'rogaikopyta';
  if(suppliers_boltygaiki.includes(name)) return 'boltygaiki';
  if(suppliers_polovoygigant.includes(name)) return 'polovoygigant';
  return '';
}
function renderSuppliers(){
  const body = document.getElementById('suppliersBody');
  if(!body) return;
  let names = getScopedSupplierNames();
  names = fcDirSortArray('suppliers', names, name=>{
    const col = dirSortState.suppliers.col;
    if(col === 'name') return name;
    const d = SUPPLIER_DETAILS[name] || {};
    return d[col] ?? '';
  });
  document.getElementById('suppliersCount').textContent = `(${names.length})`;
  body.innerHTML = names.length ? names.map(name=>{
    const d = SUPPLIER_DETAILS[name] || {};
    return `<tr data-depositor-id="${getSupplierDepositorId(name)}"><td>${d.code || '—'}</td><td>${name}</td><td>${d.bin || '—'}</td><td>${d.legalAddress || '—'}</td><td>${d.contactName || '—'}</td><td>${d.contactPhone || '—'}</td><td>${d.contactEmail || '—'}</td></tr>`;
  }).join('')
    : `<tr><td colspan="7" style="color:var(--muted);">${T('partners.text.directory_is_empty')}</td></tr>`;
}
const ORDER_TYPES = ["Обычный", "Экспресс", "Авиа", "Маркетплейс", "Отгрузка брака"];
const RECEIPT_DOC_TYPES = ["Стандартный", "Возврат", "Кросс-докинг"];
/* ============== Справочники: Поклажедатели и Склады (только для администратора, только просмотр) ============== */
const DEPOSITOR_DIRECTORY = [
  {code:'KPR-001', depositorId:'rogaikopyta', org_name:'ТОО «Рога и Копыта»', bin:'050140004521', legalAddress:'г. Алматы, ул. Джандосова, 98', phone:'+7 701 234 56 78', email:'office@rogaikopyta.kz', contractNumber:'ДГ-2024/014', contractValidUntil:'31.12.2026'},
  {code:'KPR-002', depositorId:'boltygaiki', org_name:'ТОО «Болты и Гайки»', bin:'070240008734', legalAddress:'г. Алматы, ул. Толе би, 275', phone:'+7 707 345 12 90', email:'info@boltygaiki.kz', contractNumber:'ДГ-2024/027', contractValidUntil:'30.06.2027'},
  {code:'KPR-003', depositorId:'polovoygigant', org_name:'ООО «Половой гигант»', bin:'090340012456', legalAddress:'г. Алматы, ул. Момышулы, 17', phone:'+7 705 678 44 21', email:'office@polgigant.kz', contractNumber:'ДГ-2025/003', contractValidUntil:'31.03.2027'},
];
const WAREHOUSE_DIRECTORY = [
  {code:'WH-ALM', name:'Алматы', address:'с. Байсерке, ул. Султана Бейбарыса, 1', responsible:'Оспанов Данияр Ерланович', phone:'8 701 555 10 20', email:'almaty.wh@ahlers.kz'},
  {code:'WH-KGD', name:'Караганда', address:'г. Караганда, ул. Складская, 14', responsible:'Ертаев Серик Болатович', phone:'8 705 555 30 40', email:'karagandy.wh@ahlers.kz'},
];
const PRODUCT_PACKAGING = [
  {productCode:'RK-1001', depositorId:'rogaikopyta', packagingCode:'PKG-01B', barcode:'4810001000012', type:'Короб', coefficient:10, length:30, width:25, height:15, volume:0.01125, weightGross:3.2},
  {productCode:'RK-2001', depositorId:'rogaikopyta', packagingCode:'PKG-04B', barcode:'4810001000029', type:'Групповая упаковка', coefficient:12, length:30, width:20, height:25, volume:0.015, weightGross:13.5},
  {productCode:'RK-3001', depositorId:'rogaikopyta', packagingCode:'PKG-05B', barcode:'4810001000036', type:'Паллета', coefficient:10, length:120, width:80, height:100, volume:0.96, weightGross:157},
  {productCode:'RK-6001', depositorId:'rogaikopyta', packagingCode:'PKG-08A', barcode:'4810001000043', type:'Бутылка', coefficient:3, length:12, width:12, height:28, volume:0.004, weightGross:3.15},
  {productCode:'RK-6001', depositorId:'rogaikopyta', packagingCode:'PKG-08B', barcode:'4810001000050', type:'Бидон', coefficient:10, length:24, width:24, height:34, volume:0.0196, weightGross:10.6},
  {productCode:'RK-6001', depositorId:'rogaikopyta', packagingCode:'PKG-08C', barcode:'4810001000067', type:'Бочка', coefficient:200, length:58, width:58, height:88, volume:0.296, weightGross:212},
  {productCode:'RK-6002', depositorId:'rogaikopyta', packagingCode:'PKG-09A', barcode:'4810001000074', type:'Бидон', coefficient:20, length:30, width:30, height:38, volume:0.0342, weightGross:21.4},
  {productCode:'RK-6002', depositorId:'rogaikopyta', packagingCode:'PKG-09B', barcode:'4810001000081', type:'Бочка', coefficient:200, length:58, width:58, height:88, volume:0.296, weightGross:210},
  {productCode:'BG-7001', depositorId:'boltygaiki', packagingCode:'PKG-10A', barcode:'4810002000018', type:'Короб', coefficient:10, length:14, width:10, height:6, volume:0.00084, weightGross:0.55},
];
function getCurrentDepositorContractNumber(){
  const k = DEPOSITOR_DIRECTORY.find(x=>x.depositorId === currentDepositorId);
  return k ? k.contractNumber : '—';
}
function findProductAnyDepositor(code){
  for(const kId in DEPOSITOR_DATA){
    const found = (DEPOSITOR_DATA[kId].products || []).find(p=>p.code === code);
    if(found) return found;
  }
  return null;
}
function fcPackagingName(pk, unit){
  return `${pk.type} (${pk.coefficient}${unit || ''})`;
}
function onPackagingFilterChange(){
  paginationState.packaging.page = 1;
  renderPackaging();
}
function clearPackagingFilters(){
  document.getElementById('packagingSearchCode').value = '';
  document.getElementById('packagingSearchName').value = '';
  paginationState.packaging.page = 1;
  renderPackaging();
}
function clearSkuFilters(){
  document.getElementById('skuSearchCode').value = '';
  document.getElementById('skuSearchName').value = '';
  document.getElementById('skuFilterGroup').value = '';
  document.getElementById('skuFilterType').value = '';
  onSkuFilterChange();
}
function clearStockFilters(){
  document.getElementById('stockSearchCode').value = '';
  document.getElementById('stockSearchName').value = '';
  document.getElementById('stockFilterGroup').value = '';
  document.getElementById('stockFilterType').value = '';
  document.getElementById('stockDefectFilterCb').checked = false;
  onStockFilterChange();
}
function clearReceiptsFilters(){
  const no = document.getElementById('receiptsSearchNo');
  const supplier = document.getElementById('receiptsSearchSupplier');
  const trip = document.getElementById('receiptsSearchTrip');
  const status = document.getElementById('receiptsFilterStatus');
  if(no) no.value = '';
  if(supplier) supplier.value = '';
  if(trip) trip.value = '';
  if(status) status.selectedIndex = 0;
  const { from, to } = fcDefaultDateRangeISO();
  document.getElementById('receiptsDateFrom').value = from;
  document.getElementById('receiptsDateTo').value = to;
  receiptsFiltersActive = false;
  paginationState.receipts.page = 1;
  renderReceipts();
}
function clearOrdersFilters(){
  const no = document.getElementById('ordersSearchNo');
  const consignee = document.getElementById('ordersSearchConsignee');
  const trip = document.getElementById('ordersSearchTrip');
  const status = document.getElementById('ordersFilterStatus');
  if(no) no.value = '';
  if(consignee) consignee.value = '';
  if(trip) trip.value = '';
  if(status) status.selectedIndex = 0;
  const { from, to } = fcDefaultDateRangeISO();
  document.getElementById('ordersDateFrom').value = from;
  document.getElementById('ordersDateTo').value = to;
  ordersFiltersActive = false;
  paginationState.orders.page = 1;
  renderOrders();
}
function clearTransfersFilters(){
  const no = document.getElementById('transfersSearchNo');
  const trip = document.getElementById('transfersSearchTrip');
  if(no) no.value = '';
  if(trip) trip.value = '';
  const { from, to } = fcDefaultDateRangeISO();
  document.getElementById('transfersDateFrom').value = from;
  document.getElementById('transfersDateTo').value = to;
  transfersFiltersActive = false;
  paginationState.transfers.page = 1;
  renderTransfers();
}
let dirSortState = {suppliers:{col:null,dir:1}, consignees:{col:null,dir:1}, depositorsDir:{col:null,dir:1}, warehousesDir:{col:null,dir:1}, packaging:{col:null,dir:1}};
function onDirSortClick(table, col){
  const st = dirSortState[table];
  if(!st) return;
  if(st.col === col){ st.dir = -st.dir; } else { st.col = col; st.dir = 1; }
  const renderFns = {suppliers:renderSuppliers, consignees:renderConsignees, depositorsDir:renderDepositorsDir, warehousesDir:renderWarehousesDir, packaging:renderPackaging};
  if(renderFns[table]) renderFns[table]();
  updateDirSortHeaderArrows(table);
}
function updateDirSortHeaderArrows(table){
  const st = dirSortState[table];
  document.querySelectorAll(`th[onclick^="onDirSortClick('${table}',"]`).forEach(th=>{
    if(!th.hasAttribute('data-label')) th.setAttribute('data-label', th.textContent.trim());
    const base = th.getAttribute('data-label');
    const m = th.getAttribute('onclick').match(/onDirSortClick\('[^']+','([^']+)'\)/);
    const col = m ? m[1] : null;
    th.textContent = base + (st.col === col ? (st.dir === 1 ? ' ▲' : ' ▼') : '');
  });
}
function fcDirSortArray(table, arr, valueFn){
  const st = dirSortState[table];
  if(!st || !st.col) return arr;
  return arr.slice().sort((a,b)=>{
    let va = valueFn(a), vb = valueFn(b);
    const na = parseFloat(va), nb = parseFloat(vb);
    if(!isNaN(na) && !isNaN(nb) && va!=='' && vb!==''){ va = na; vb = nb; }
    if(va < vb) return -1 * st.dir;
    if(va > vb) return 1 * st.dir;
    return 0;
  });
}
function renderPackaging(){
  const body = document.getElementById('packagingBody');
  if(!body) return;
  const codeQ = (document.getElementById('packagingSearchCode')?.value || '').trim().toLowerCase();
  const nameQ = (document.getElementById('packagingSearchName')?.value || '').trim().toLowerCase();
  let filtered = PRODUCT_PACKAGING.filter(pk=>pk.depositorId === currentDepositorId).filter(pk=>{
    const p = findProductAnyDepositor(pk.productCode);
    const name = p ? p.name : '';
    if(codeQ && !pk.productCode.toLowerCase().includes(codeQ)) return false;
    if(nameQ && !name.toLowerCase().includes(nameQ)) return false;
    return true;
  });
  filtered = fcDirSortArray('packaging', filtered, pk=>{
    const p = findProductAnyDepositor(pk.productCode);
    const col = dirSortState.packaging.col;
    if(col === 'productName') return p ? p.name : '';
    if(col === 'unit') return p ? p.unit : '';
    if(col === 'displayName') return fcPackagingName(pk, p ? p.unit : '');
    return pk[col] ?? '';
  });
  const { items: rows, totalPages, total } = getPage('packaging', filtered);
  document.getElementById('packagingCount').textContent = `(${total})`;
  body.innerHTML = rows.length ? rows.map(pk=>{
    const p = findProductAnyDepositor(pk.productCode);
    const unit = p ? p.unit : '';
    const productId = p ? getProductFullInfo(p).itemCode : '';
    return `
    <tr data-product-id="${productId}" data-depositor-id="${pk.depositorId}">
      <td><strong>${pk.productCode}</strong></td>
      <td>${p ? p.name : '—'}</td>
      <td>${unit || '—'}</td>
      <td>${pk.packagingCode}</td>
      <td>${pk.type}</td>
      <td>${fcPackagingName(pk, unit)}</td>
      <td>${pk.barcode || '—'}</td>
      <td class="num">${pk.coefficient}</td>
      <td class="num">${pk.length}</td>
      <td class="num">${pk.width}</td>
      <td class="num">${pk.height}</td>
      <td class="num">${pk.volume}</td>
      <td class="num">${pk.weightGross}</td>
    </tr>`;
  }).join('') : `<tr><td colspan="13" style="color:var(--muted);">${T('stock.text.nothing_found')}</td></tr>`;
  renderPaginationBar('packaging', totalPages, total);
  setTimeout(()=>updateTopScrollWidth('packaging'), 0);
}
function renderDepositorsDir(){
  const body = document.getElementById('depositorsDirBody');
  if(!body) return;
  const list = fcDirSortArray('depositorsDir', DEPOSITOR_DIRECTORY, k=>k[dirSortState.depositorsDir.col] ?? '');
  document.getElementById('depositorsDirCount').textContent = `(${list.length})`;
  body.innerHTML = list.length ? list.map(k=>`
    <tr><td>${k.code}</td><td>${k.org_name}</td><td>${k.bin}</td><td>${k.legalAddress}</td><td>${k.phone}</td><td>${k.email}</td><td>${k.contractNumber}</td><td>${k.contractValidUntil}</td></tr>`).join('')
    : `<tr><td colspan="8" style="color:var(--muted);">${T('admin.text.no_data')}</td></tr>`;
}
function renderWarehousesDir(){
  const body = document.getElementById('warehousesDirBody');
  if(!body) return;
  const list = fcDirSortArray('warehousesDir', WAREHOUSE_DIRECTORY, w=>w[dirSortState.warehousesDir.col] ?? '');
  document.getElementById('warehousesDirCount').textContent = `(${list.length})`;
  body.innerHTML = list.length ? list.map(w=>`
    <tr><td>${w.code}</td><td>${w.name}</td><td>${w.address}</td><td>${w.responsible}</td><td>${w.phone}</td><td>${w.email}</td></tr>`).join('')
    : `<tr><td colspan="6" style="color:var(--muted);">${T('admin.text.no_data')}</td></tr>`;
}

function renderOrderTypes(){
  const body = document.getElementById('orderTypesBody');
  if(!body) return;
  body.innerHTML = ORDER_TYPES.map((t,idx)=>`<tr><td>OT-${String(idx+1).padStart(2,'0')}</td><td>${t}</td></tr>`).join('');
}
function renderDocTypes(){
  const body = document.getElementById('docTypesBody');
  if(!body) return;
  body.innerHTML = RECEIPT_DOC_TYPES.map((t,idx)=>`<tr><td>DT-${String(idx+1).padStart(2,'0')}</td><td>${t}</td></tr>`).join('');
}
const ADDR_BY_CONSIGNEE = {...consigneeAddresses_rogaikopyta, ...consigneeAddresses_boltygaiki, ...consigneeAddresses_polovoygigant};
/* Справочник адресов доставки грузополучателей — независим от истории заказов.
   У одного грузополучателя может быть несколько точек доставки. */
const CONSIGNEE_ADDRESSES = {
  "ИП «Барашкин»": ["г. Алматы, ул. Розыбакиева, 121", "г. Алматы, ул. Жандосова, 58, склад №3"],
  "ИП «Овечкин»": ["г. Алматы, пр. Райымбека, 348"],
  "ТОО «АвтоДеталь Плюс»": ["г. Алматы, ул. Толе би, 273"],
  "ИП «Гаечкин»": ["г. Алматы, ул. Сатпаева, 90"],
  "ТОО «СтройМир»": ["г. Алматы, ул. Момышулы, 15"],
  "ИП «Полов Р.Б.»": ["г. Алматы, ул. Наурызбай батыра, 62"],
};
const SUPPLIER_DETAILS = {
  "ИП «Козлов»": {code:'SUP-001', bin:"780512300123", legalAddress:"г. Алматы, ул. Сейфуллина, 15", contactName:"Козлов Алмас Серикович", contactPhone:"8 701 220 15 40", contactEmail:"kozlov.almas@mail.kz"},
  "ИП «Винтик»": {code:'SUP-002', bin:"820914400456", legalAddress:"г. Караганда, ул. Ерубаева, 22", contactName:"Винтиков Дамир Ануарович", contactPhone:"8 702 331 26 51", contactEmail:"vintik.dz@mail.kz"},
  "ИП «Шпунтик»": {code:'SUP-003', bin:"850227500789", legalAddress:"г. Караганда, ул. Гоголя, 9", contactName:"Шпунтиков Ержан Толегенович", contactPhone:"8 705 442 37 62", contactEmail:"shpuntikov.et@mail.kz"},
  "ИП «Ламинатов»": {code:'SUP-004', bin:"790618600234", legalAddress:"г. Караганда, пр. Бухар-Жырау, 44", contactName:"Ламинатов Асхат Женисович", contactPhone:"8 707 553 48 73", contactEmail:"laminatov.az@mail.kz"},
  "ТОО «ПаркетСнаб»": {code:'SUP-005', bin:"110340021567", legalAddress:"г. Караганда, ул. Пржевальского, 5", contactName:"Сатпаева Гульмира Оразовна", contactPhone:"8 708 664 59 84", contactEmail:"info@parketsnab.kz"},
};
const CONSIGNEE_DETAILS = {
  "ИП «Барашкин»": {code:'BUY-001', bin:"860113700345", legalAddress:"г. Алматы, ул. Розыбакиева, 121", contactName:"Барашкин Нурлан Ерланович", contactPhone:"8 701 774 60 15", contactEmail:"barashkin.ne@mail.kz"},
  "ИП «Овечкин»": {code:'BUY-002', bin:"901225800678", legalAddress:"г. Алматы, пр. Райымбека, 348", contactName:"Овечкин Тимур Асланович", contactPhone:"8 702 885 71 26", contactEmail:"ovechkin.ta@mail.kz"},
  "ТОО «АвтоДеталь Плюс»": {code:'BUY-003', bin:"120440032789", legalAddress:"г. Караганда, ул. Молокова, 12", contactName:"Ким Виктория Александровна", contactPhone:"8 705 996 82 37", contactEmail:"info@avtodetal-plus.kz"},
  "ИП «Гаечкин»": {code:'BUY-004', bin:"880502900123", legalAddress:"г. Караганда, ул. Чкалова, 31", contactName:"Гаечкин Бекзат Муратович", contactPhone:"8 707 107 93 48", contactEmail:"gaechkin.bm@mail.kz"},
  "ТОО «СтройМир»": {code:'BUY-005', bin:"100630043210", legalAddress:"г. Караганда, ул. Ержанова, 18", contactName:"Абенова Айгерим Куанышевна", contactPhone:"8 708 218 04 59", contactEmail:"info@stroymir.kz"},
  "ИП «Полов Р.Б.»": {code:'BUY-006', bin:"830821100456", legalAddress:"г. Караганда, пр. Independence, 7", contactName:"Полов Руслан Батырович", contactPhone:"8 701 329 15 60", contactEmail:"polov.rb@mail.kz"},
};
function getScopedConsigneeNames(){
  if(currentUserRole === 'staff'){
    return [...new Set((orders||[]).map(o=>o.consignee).filter(Boolean))].sort();
  }
  const map = {rogaikopyta:consignees_rogaikopyta, boltygaiki:consignees_boltygaiki, polovoygigant:consignees_polovoygigant};
  return (map[currentDepositorId] || []).slice().sort();
}
function getConsigneeDepositorId(name){
  if(consignees_rogaikopyta.includes(name)) return 'rogaikopyta';
  if(consignees_boltygaiki.includes(name)) return 'boltygaiki';
  if(consignees_polovoygigant.includes(name)) return 'polovoygigant';
  return '';
}
function renderConsignees(){
  const body = document.getElementById('consigneesBody');
  if(!body) return;
  let names = getScopedConsigneeNames();
  names = fcDirSortArray('consignees', names, name=>{
    const col = dirSortState.consignees.col;
    if(col === 'name') return name;
    const d = CONSIGNEE_DETAILS[name] || {};
    return d[col] ?? '';
  });
  document.getElementById('consigneesCount').textContent = `(${names.length})`;
  if(!names.length){
    body.innerHTML = `<tr><td colspan="8" style="color:var(--muted);">${T('partners.text.directory_is_empty')}</td></tr>`;
    return;
  }
  body.innerHTML = names.map(name=>{
    const addresses = getConsigneeAddresses(name);
    const d = CONSIGNEE_DETAILS[name] || {};
    const depositorId = getConsigneeDepositorId(name);
    const rowCount = Math.max(addresses.length, 1);
    let rowsHtml = '';
    for(let i=0; i<rowCount; i++){
      const addr = addresses[i] || '—';
      if(i === 0){
        rowsHtml += `<tr data-depositor-id="${depositorId}">
          <td rowspan="${rowCount}">${d.code || '—'}</td>
          <td rowspan="${rowCount}">${name}</td>
          <td>${addr}</td>
          <td rowspan="${rowCount}">${d.bin || '—'}</td>
          <td rowspan="${rowCount}">${d.legalAddress || '—'}</td>
          <td rowspan="${rowCount}">${d.contactName || '—'}</td>
          <td rowspan="${rowCount}">${d.contactPhone || '—'}</td>
          <td rowspan="${rowCount}">${d.contactEmail || '—'}</td>
        </tr>`;
      }else{
        rowsHtml += `<tr data-depositor-id="${depositorId}"><td>${addr}</td></tr>`;
      }
    }
    return rowsHtml;
  }).join('');
}
let transfersFiltersActive = false;
function onTransfersFilterChange(){
  transfersFiltersActive = true;
  paginationState.transfers.page = 1;
  renderTransfers();
}
function renderTransfers(){
  const el = document.getElementById('transfersBody');
  if(!el) return;
  let filtered = transfers || [];
  if(transfersFiltersActive){
    const noQ = (document.getElementById('transfersSearchNo')?.value || '').trim().toLowerCase();
    const tripQ = (document.getElementById('transfersSearchTrip')?.value || '').trim().toLowerCase();
    const fromISO = document.getElementById('transfersDateFrom')?.value || '';
    const toISO = document.getElementById('transfersDateTo')?.value || '';
    if(noQ) filtered = filtered.filter(t=>(t.erpNo||'').toLowerCase().includes(noQ) || (t.no||'').toLowerCase().includes(noQ));
    if(tripQ) filtered = filtered.filter(t=>(t.trip||'').toLowerCase().includes(tripQ));
    filtered = filtered.filter(t=>dateInRange(t.date, fromISO, toISO));
  }
  filtered = fcApplySort('transfers', filtered);
  const { items, totalPages, total } = getPage('transfers', filtered);
  document.getElementById('transfersCount').textContent = `(${total})`;
  renderTableHead('transfers');
  el.innerHTML = items.length ? items.map(t=>renderTableRow('transfers', t, 'openTransfer')).join('') : `<tr><td colspan="20" style="color:var(--muted);">${T('stock.text.nothing_found')}</td></tr>`;
  renderPaginationBar('transfers', totalPages, total);
  setTimeout(()=>updateTopScrollWidth('transfers'), 0);
}
TABLE_RENDER_FN.receipts = renderReceipts;
TABLE_RENDER_FN.orders = renderOrders;
TABLE_RENDER_FN.stock = renderStock;
TABLE_RENDER_FN.sku = renderSku;
TABLE_RENDER_FN.transfers = renderTransfers;

/* ============== Верхний горизонтальный скроллбар (дублирует нижний, под шапкой таблицы) ============== */
const SCROLL_SYNC_KEYS = ['receipts','orders','stock','sku','transfers'];
function setupTopScrollSync(key){
  const top = document.getElementById(key+'ScrollTop');
  const x = document.getElementById(key+'ScrollX');
  if(!top || !x) return;
  let syncing = false;
  top.addEventListener('scroll', ()=>{
    if(syncing) return; syncing = true;
    x.scrollLeft = top.scrollLeft;
    syncing = false;
  });
  x.addEventListener('scroll', ()=>{
    if(syncing) return; syncing = true;
    top.scrollLeft = x.scrollLeft;
    syncing = false;
  });
}
function updateTopScrollWidth(key){
  const inner = document.getElementById(key+'ScrollTopInner');
  const x = document.getElementById(key+'ScrollX');
  if(!inner || !x) return;
  const table = x.querySelector('table');
  if(!table) return;
  inner.style.width = table.scrollWidth + 'px';
}
function updateAllVisibleTopScrollWidths(){
  SCROLL_SYNC_KEYS.forEach(k=>{
    const view = document.getElementById('view-' + k);
    if(view && view.classList.contains('active')) updateTopScrollWidth(k);
  });
}
SCROLL_SYNC_KEYS.forEach(setupTopScrollSync);
window.addEventListener('resize', updateAllVisibleTopScrollWidths);
function toLocalISODate(d){
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
function fcDefaultDateRangeISO(){
  const today = new Date();
  const to = toLocalISODate(today);
  const lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const from = toLocalISODate(lastMonthStart);
  return { from, to };
}
function setDefaultDateFilters(){
  const { from, to } = fcDefaultDateRangeISO();
  [
    ['receiptsDateFrom','receiptsDateTo'],
    ['transfersDateFrom','transfersDateTo'],
    ['ordersDateFrom','ordersDateTo'],
  ].forEach(([fromId, toId])=>{
    const fromEl = document.getElementById(fromId);
    const toEl = document.getElementById(toId);
    if(fromEl && !fromEl.value) fromEl.value = from;
    if(toEl && !toEl.value) toEl.value = to;
  });
}
setDefaultDateFilters();
const COMPOSITION_SCROLL_SYNC_KEYS = ['receiptComposition', 'orderComposition', 'transferComposition'];
COMPOSITION_SCROLL_SYNC_KEYS.forEach(setupTopScrollSync);
window.addEventListener('resize', ()=>COMPOSITION_SCROLL_SYNC_KEYS.forEach(k=>updateTopScrollWidth(k)));

/* ============== Каталог отчётов (управляется из Администрирования) ============== */
const REPORT_LOCATION_OPTIONS = ["Отчёты", "Биллинг", "Поступления", "Заказы", "Перемещения"];
let REPORTS_CONFIG = [
  {id:'r001', order:1, depositorId:'rogaikopyta', name:'Остатки товаров', wmsCode:'RPT-STOCK-101', location:'Отчёты', params:[{name:'Склад', type:'Склады', value:'', description:''}]},
  {id:'r002', order:2, depositorId:'rogaikopyta', name:'Отчёт по заказам', wmsCode:'RPT-ORD-102', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r003', order:3, depositorId:'rogaikopyta', name:'Отчёт по поступлениям', wmsCode:'RPT-RCPT-103', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r004', order:4, depositorId:'rogaikopyta', name:'Остатки сроков годности', wmsCode:'RPT-EXP-104', location:'Отчёты', params:[]},
  {id:'r005', order:5, depositorId:'rogaikopyta', name:'Отчёт о браке, недостаче, излишках', wmsCode:'RPT-DEFECT-105', location:'Отчёты', params:[]},
  {id:'r006', order:6, depositorId:'rogaikopyta', name:'Ведомость по движению товаров за период', wmsCode:'RPT-MOVE-106', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r007', order:7, depositorId:'rogaikopyta', name:'Сводный отчёт по оказанным услугам', wmsCode:'RPT-SVC-107', location:'Биллинг', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r008', order:8, depositorId:'rogaikopyta', name:'KPI 3PL-оператора', wmsCode:'RPT-KPI-108', location:'Биллинг', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r009', order:9, depositorId:'rogaikopyta', name:'Отчёт по перемещениям', wmsCode:'RPT-TRANSFER-109', location:'Отчёты', params:[]},

  {id:'r011', order:1, depositorId:'boltygaiki', name:'Остатки товаров', wmsCode:'RPT-STOCK-201', location:'Отчёты', params:[]},
  {id:'r012', order:2, depositorId:'boltygaiki', name:'Отчёт по заказам', wmsCode:'RPT-ORD-202', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r013', order:3, depositorId:'boltygaiki', name:'Отчёт по поступлениям', wmsCode:'RPT-RCPT-203', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r014', order:4, depositorId:'boltygaiki', name:'Остатки сроков годности', wmsCode:'RPT-EXP-204', location:'Отчёты', params:[]},
  {id:'r015', order:5, depositorId:'boltygaiki', name:'Отчёт о браке, недостаче, излишках', wmsCode:'RPT-DEFECT-205', location:'Отчёты', params:[]},
  {id:'r016', order:6, depositorId:'boltygaiki', name:'Ведомость по движению товаров за период', wmsCode:'RPT-MOVE-206', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r017', order:7, depositorId:'boltygaiki', name:'Сводный отчёт по оказанным услугам', wmsCode:'RPT-SVC-207', location:'Биллинг', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r018', order:8, depositorId:'boltygaiki', name:'KPI 3PL-оператора', wmsCode:'RPT-KPI-208', location:'Биллинг', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},

  {id:'r021', order:1, depositorId:'polovoygigant', name:'Остатки товаров', wmsCode:'RPT-STOCK-301', location:'Отчёты', params:[]},
  {id:'r022', order:2, depositorId:'polovoygigant', name:'Отчёт по заказам', wmsCode:'RPT-ORD-302', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r023', order:3, depositorId:'polovoygigant', name:'Отчёт по поступлениям', wmsCode:'RPT-RCPT-303', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r024', order:4, depositorId:'polovoygigant', name:'Остатки сроков годности', wmsCode:'RPT-EXP-304', location:'Отчёты', params:[]},
  {id:'r025', order:5, depositorId:'polovoygigant', name:'Отчёт о браке, недостаче, излишках', wmsCode:'RPT-DEFECT-305', location:'Отчёты', params:[]},
  {id:'r026', order:6, depositorId:'polovoygigant', name:'Ведомость по движению товаров за период', wmsCode:'RPT-MOVE-306', location:'Отчёты', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r027', order:7, depositorId:'polovoygigant', name:'Сводный отчёт по оказанным услугам', wmsCode:'RPT-SVC-307', location:'Биллинг', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},
  {id:'r028', order:8, depositorId:'polovoygigant', name:'KPI 3PL-оператора', wmsCode:'RPT-KPI-308', location:'Биллинг', params:[{name:'Период с', type:'Дата', value:'', useCurrentDate:false},{name:'Период по', type:'Дата', value:'', useCurrentDate:true}]},

  {id:'r101', order:9, depositorId:'rogaikopyta', name:'Акт приема передачи товара на хранение', wmsCode:'RPT-ACT-PRM-101', location:'Поступления', params:[]},
  {id:'r102', order:10, depositorId:'rogaikopyta', name:'Акт недостачи', wmsCode:'RPT-ACT-SHT-102', location:'Поступления', params:[]},
  {id:'r103', order:11, depositorId:'rogaikopyta', name:'Акт забраковки', wmsCode:'RPT-ACT-DEF-103', location:'Поступления', params:[]},
  {id:'r104', order:12, depositorId:'rogaikopyta', name:'Акт перепоставки', wmsCode:'RPT-ACT-OVR-104', location:'Поступления', params:[]},
  {id:'r105', order:13, depositorId:'rogaikopyta', name:'Акт возврата товара с хранения', wmsCode:'RPT-ACT-RET-105', location:'Заказы', params:[]},
  {id:'r106', order:14, depositorId:'rogaikopyta', name:'Перемещение товаров', wmsCode:'RPT-ACT-MOV-106', location:'Перемещения', params:[]},

  {id:'r201', order:9, depositorId:'boltygaiki', name:'Акт приема передачи товара на хранение', wmsCode:'RPT-ACT-PRM-201', location:'Поступления', params:[]},
  {id:'r202', order:10, depositorId:'boltygaiki', name:'Акт недостачи', wmsCode:'RPT-ACT-SHT-202', location:'Поступления', params:[]},
  {id:'r203', order:11, depositorId:'boltygaiki', name:'Акт забраковки', wmsCode:'RPT-ACT-DEF-203', location:'Поступления', params:[]},
  {id:'r204', order:12, depositorId:'boltygaiki', name:'Акт перепоставки', wmsCode:'RPT-ACT-OVR-204', location:'Поступления', params:[]},
  {id:'r205', order:13, depositorId:'boltygaiki', name:'Акт возврата товара с хранения', wmsCode:'RPT-ACT-RET-205', location:'Заказы', params:[]},
  {id:'r206', order:14, depositorId:'boltygaiki', name:'Перемещение товаров', wmsCode:'RPT-ACT-MOV-206', location:'Перемещения', params:[]},

  {id:'r301', order:9, depositorId:'polovoygigant', name:'Акт приема передачи товара на хранение', wmsCode:'RPT-ACT-PRM-301', location:'Поступления', params:[]},
  {id:'r302', order:10, depositorId:'polovoygigant', name:'Акт недостачи', wmsCode:'RPT-ACT-SHT-302', location:'Поступления', params:[]},
  {id:'r303', order:11, depositorId:'polovoygigant', name:'Акт забраковки', wmsCode:'RPT-ACT-DEF-303', location:'Поступления', params:[]},
  {id:'r304', order:12, depositorId:'polovoygigant', name:'Акт перепоставки', wmsCode:'RPT-ACT-OVR-304', location:'Поступления', params:[]},
  {id:'r305', order:13, depositorId:'polovoygigant', name:'Акт возврата товара с хранения', wmsCode:'RPT-ACT-RET-305', location:'Заказы', params:[]},
  {id:'r306', order:14, depositorId:'polovoygigant', name:'Перемещение товаров', wmsCode:'RPT-ACT-MOV-306', location:'Перемещения', params:[]},

  {id:'r107', order:15, depositorId:'rogaikopyta', name:'Упаковочный лист', wmsCode:'RPT-PACK-107', location:'Заказы', params:[]},
  {id:'r207', order:15, depositorId:'boltygaiki', name:'Упаковочный лист', wmsCode:'RPT-PACK-207', location:'Заказы', params:[]},
  {id:'r307', order:15, depositorId:'polovoygigant', name:'Упаковочный лист', wmsCode:'RPT-PACK-307', location:'Заказы', params:[]},

  {id:'r108', order:16, depositorId:'rogaikopyta', name:'Остатки товаров (детализированный)', wmsCode:'RPT-STOCK-DET-108', location:'Отчёты', params:[{name:'Склад', type:'Склады', value:'', description:''}]},
  {id:'r208', order:16, depositorId:'boltygaiki', name:'Остатки товаров (детализированный)', wmsCode:'RPT-STOCK-DET-208', location:'Отчёты', params:[{name:'Склад', type:'Склады', value:'', description:''}]},
  {id:'r308', order:16, depositorId:'polovoygigant', name:'Остатки товаров (детализированный)', wmsCode:'RPT-STOCK-DET-308', location:'Отчёты', params:[{name:'Склад', type:'Склады', value:'', description:''}]},

  {id:'r109', order:17, depositorId:'rogaikopyta', name:'Отчёт по мастер данным', wmsCode:'RPT-MASTER-109', location:'Отчёты', params:[]},
  {id:'r209', order:17, depositorId:'boltygaiki', name:'Отчёт по мастер данным', wmsCode:'RPT-MASTER-209', location:'Отчёты', params:[]},
  {id:'r309', order:17, depositorId:'polovoygigant', name:'Отчёт по мастер данным', wmsCode:'RPT-MASTER-309', location:'Отчёты', params:[]},
];
function getScopedReportDepositorIds(){
  if(currentUserRole === 'staff'){
    return staffAvailableDepositors ? staffAvailableDepositors() : [currentDepositorId];
  }
  return [currentDepositorId];
}
function renderReportRows(bodyId, location){
  const body = document.getElementById(bodyId);
  if(!body) return;
  const depositorIds = getScopedReportDepositorIds();
  const list = REPORTS_CONFIG.filter(r=>r.location===location && depositorIds.includes(r.depositorId)).sort((a,b)=>(a.order||0)-(b.order||0));
  if(!list.length){
    body.innerHTML = `<tr><td colspan="2" style="color:var(--muted);">${T('admin.text.no_data')}</td></tr>`;
    return;
  }
  body.innerHTML = list.map(r=>`
    <tr onclick="requestGenerateReport('${r.id}')">
      <td><strong>${r.name}</strong></td>
      <td><button class="btn ghost" onclick="event.stopPropagation(); requestGenerateReport('${r.id}')">${T('common.button.generate_report')}</button></td>
    </tr>`).join('');
}
function renderReports(){
  const depositorIds = getScopedReportDepositorIds();
  const count = REPORTS_CONFIG.filter(r=>r.location==='Отчёты' && depositorIds.includes(r.depositorId)).length;
  document.getElementById('reportsCount').textContent = `(${count})`;
  renderReportRows('reportsBody', 'Отчёты');
  renderBillingReports();
}
function renderBillingReports(){
  renderReportRows('billingReportsBody', 'Биллинг');
}

/* ============== Формирование отчёта пользователем ============== */
let generatingReportId = null;
function paramHasValue(p){
  if(p.hidden) return true;
  if(p.type === 'Дата') return !!p.useCurrentDate || !!p.value;
  return p.value !== '' && p.value !== undefined && p.value !== null;
}
function requestGenerateReport(reportId){
  const r = REPORTS_CONFIG.find(x=>x.id===reportId);
  if(!r) return;
  const params = (r.params || []).filter(p=>!p.hidden);
  const missing = params.filter(p=>!paramHasValue(p));
  if(!missing.length){
    notify('Отчёт «' + r.name + '» сформирован');
    return;
  }
  generatingReportId = reportId;
  document.getElementById('genReportTitle').textContent = r.name;
  document.getElementById('genReportFields').innerHTML = params.map((p,i)=>{
    if(p.type === 'Дата'){
      const dateVal = p.useCurrentDate ? '' : toISODate(p.value);
      return `<div class="kv"><label>${p.name}</label><input type="date" class="kv-input kv-input-editable" data-gen-param="${p.name}" id="gen_field_${i}" value="${dateVal}" ${p.useCurrentDate?'placeholder="Текущая дата"':''}></div>`;
    }
    if(FC_REF_DIRECTORIES.includes(p.type) && p.type !== '—'){
      const options = fcDirectoryCodeOptions(p.type);
      return `<div class="kv"><label>${p.name}</label><select class="kv-input kv-input-editable" data-gen-param="${p.name}" id="gen_field_${i}"><option value="">${T('common.option.not_selected')}</option>${options.map(o=>`<option value="${o.code}" ${p.value===o.code?'selected':''}>${o.name}</option>`).join('')}</select></div>`;
    }
    if(p.type === 'Число'){
      return `<div class="kv"><label>${p.name}</label><input type="number" class="kv-input kv-input-editable" data-gen-param="${p.name}" id="gen_field_${i}" value="${p.value ?? ''}"></div>`;
    }
    return `<div class="kv"><label>${p.name}</label><input type="text" class="kv-input kv-input-editable" data-gen-param="${p.name}" id="gen_field_${i}" value="${(p.value||'').replace(/"/g,'&quot;')}"></div>`;
  }).join('');
  document.getElementById('genReportOverlay').classList.add('active');
}
function closeGenerateReportModal(){
  document.getElementById('genReportOverlay').classList.remove('active');
  generatingReportId = null;
}
function submitGenerateReport(){
  const r = REPORTS_CONFIG.find(x=>x.id===generatingReportId);
  if(!r){ closeGenerateReportModal(); return; }
  closeGenerateReportModal();
  notify('Отчёт «' + r.name + '» сформирован');
}

/* ============== Администрирование: настройка отчётов ============== */
function renderReportsConfigTable(){
  const body = document.getElementById('reportsConfigBody');
  if(!body) return;
  const filterSel = document.getElementById('reportsConfigDepositorFilter');
  const filterVal = filterSel ? filterSel.value : '';
  const locationSel = document.getElementById('reportsConfigLocationFilter');
  const locationVal = locationSel ? locationSel.value : '';
  const list = REPORTS_CONFIG.filter(r=>(!filterVal || r.depositorId === filterVal) && (!locationVal || r.location === locationVal)).sort((a,b)=>(a.order||0)-(b.order||0));
  body.innerHTML = list.length ? list.map(r=>`
    <tr style="cursor:pointer;" onclick="openReportConfigEdit('${r.id}')">
      <td>${r.order ?? '—'}</td>
      <td>${(DEPOSITORS[r.depositorId]||{}).name || r.depositorId}</td>
      <td>${r.name}</td>
      <td>${r.wmsCode}</td>
      <td>${(r.params && r.params.length) ? r.params.map(p=>`${p.name} (${p.type})${p.hidden?' 🔒':''}`).join(', ') : '—'}</td>
      <td>${r.location}</td>
      <td>
        <button class="btn ghost" style="color:#c0392b;border-color:#e3b3ac;" onclick="event.stopPropagation(); openDeleteReportConfigConfirm('${r.id}')">${T('common.button.delete')}</button>
      </td>
    </tr>`).join('') : `<tr><td colspan="7" style="color:var(--muted);">Нет отчётов</td></tr>`;
}
let reportConfigDeletingId = null;
function openDeleteReportConfigConfirm(id){
  reportConfigDeletingId = id;
  document.getElementById('deleteReportConfigOverlay').classList.add('active');
}
function closeDeleteReportConfigConfirm(){
  document.getElementById('deleteReportConfigOverlay').classList.remove('active');
  reportConfigDeletingId = null;
}
function confirmDeleteReportConfig(){
  if(reportConfigDeletingId) deleteReportConfig(reportConfigDeletingId);
  closeDeleteReportConfigConfirm();
}
function populateReportsConfigDepositorFilter(){
  const sel = document.getElementById('reportsConfigDepositorFilter');
  if(sel) sel.innerHTML = `<option value="">${T('admin.option.all')}</option>` + Object.values(DEPOSITORS).map(k=>`<option value="${k.id}">${k.name}</option>`).join('');
  const locSel = document.getElementById('reportsConfigLocationFilter');
  if(locSel) locSel.innerHTML = `<option value="">${T('admin.option.all')}</option>` + REPORT_LOCATION_OPTIONS.map(l=>`<option value="${l}">${l}</option>`).join('');
}
let reportConfigEditingId = null;
let reportConfigParamsDraft = [];
function paramRowHtml(i, p, removable){
  p = p || {};
  const removeBtn = removable ? `<button type="button" class="btn ghost" style="align-self:flex-start;color:#c0392b;border-color:#e3b3ac;" onclick="removeParamRow(${i-1})">Удалить параметр</button>` : '';
  return `
    <div class="line-info-card" style="margin-bottom:0;">
      <div style="display:flex; flex-direction:column; gap:8px; padding:10px 0;">
        <input type="text" class="kv-input kv-input-editable" id="rc_param${i}_name" placeholder=T('common.placeholder.attribute_name') value="${(p.name||'').replace(/"/g,'&quot;')}">
        <select class="kv-input kv-input-editable" id="rc_param${i}_type" onchange="updateParamTypeUI(${i})">
          <option value="">— тип не выбран —</option>
          ${getParamTypeOptions().map(t=>`<option value="${t}" ${p.type===t?'selected':''}>${t}</option>`).join('')}
        </select>
        <div id="rc_param${i}_valueWrap">
          <input type="date" class="kv-input kv-input-editable" id="rc_param${i}_value_date" style="display:none;" value="${toISODate(p.value)}">
          <label id="rc_param${i}_currentDateWrap" style="display:none;align-items:center;gap:6px;font-size:13px;margin-top:6px;">
            <input type="checkbox" id="rc_param${i}_currentDate" onchange="onParamCurrentDateChange(${i})" ${p.useCurrentDate?'checked':''}> Текущая дата
          </label>
          <input type="number" class="kv-input kv-input-editable" id="rc_param${i}_value_number" style="display:none;" value="${p.type==='Число' ? (p.value ?? '') : ''}">
          <input type="text" class="kv-input kv-input-editable" id="rc_param${i}_value_string" style="display:none;" placeholder=T('common.placeholder.value_optional') value="${p.type==='Строка' ? (p.value || '') : ''}">
          <div id="rc_param${i}_dirWrap" style="display:none; flex-direction:column; gap:8px;">
            <select class="kv-input kv-input-editable" id="rc_param${i}_value_dir" onchange="onParamDirSelect(${i})" data-code="${p.value || ''}"></select>
            <label style="font-size:11px; color:var(--muted); margin-bottom:-4px;">Значение (код)</label>
            <input type="text" class="kv-input" id="rc_param${i}_value_code" readonly value="${p.value || ''}">
            <label style="font-size:11px; color:var(--muted); margin-bottom:-4px;">Текстовое описание</label>
            <input type="text" class="kv-input" id="rc_param${i}_value_desc" readonly value="${p.description || ''}">
          </div>
        </div>
        <label style="display:flex;align-items:center;gap:6px;font-size:13px;margin-top:4px;">
          <input type="checkbox" id="rc_param${i}_hidden" ${p.hidden?'checked':''}> Скрыть параметр от пользователя (использовать заданное значение)
        </label>
        ${removeBtn}
      </div>
    </div>`;
}
function fcDirectoryCodeOptions(dirName){
  const map = {
    'Товары': ()=>products.map(p=>({code:p.code, name:p.name})),
    'Поставщики': ()=>Object.keys(SUPPLIER_DETAILS).map(name=>({code:SUPPLIER_DETAILS[name].code || name, name})),
    'Грузополучатели': ()=>Object.keys(CONSIGNEE_DETAILS).map(name=>({code:CONSIGNEE_DETAILS[name].code || name, name})),
    'Типы заказов': ()=>ORDER_TYPES.map(name=>({code:name, name})),
    'Типы ожидаемых документов': ()=>RECEIPT_DOC_TYPES.map(name=>({code:name, name})),
    'Поклажедатели': ()=>DEPOSITOR_DIRECTORY.map(k=>({code:k.code, name:k.org_name})),
    'Склады': ()=>WAREHOUSE_DIRECTORY.map(w=>({code:w.code, name:w.name})),
  };
  return (map[dirName] || (()=>[]))();
}
function getParamTypeOptions(){
  return ["Дата", "Число", "Строка", ...FC_REF_DIRECTORIES.filter(d=>d !== '—' && d !== 'Товары')];
}
function updateParamTypeUI(i){
  const type = document.getElementById(`rc_param${i}_type`).value;
  const isDir = FC_REF_DIRECTORIES.includes(type) && type !== '—';
  document.getElementById(`rc_param${i}_value_date`).style.display = type === 'Дата' ? '' : 'none';
  document.getElementById(`rc_param${i}_currentDateWrap`).style.display = type === 'Дата' ? 'flex' : 'none';
  document.getElementById(`rc_param${i}_value_number`).style.display = type === 'Число' ? '' : 'none';
  document.getElementById(`rc_param${i}_value_string`).style.display = type === 'Строка' ? '' : 'none';
  document.getElementById(`rc_param${i}_dirWrap`).style.display = isDir ? 'flex' : 'none';
  if(isDir){
    const select = document.getElementById(`rc_param${i}_value_dir`);
    const options = fcDirectoryCodeOptions(type);
    const currentCode = select.dataset.code || '';
    select.innerHTML = `<option value="">${T('common.option.not_selected')}</option>` + options.map(o=>`<option value="${o.code}" ${o.code===currentCode?'selected':''}>${o.name}</option>`).join('');
  }
}
function onParamDirSelect(i){
  const select = document.getElementById(`rc_param${i}_value_dir`);
  const opt = select.options[select.selectedIndex];
  select.dataset.code = select.value;
  document.getElementById(`rc_param${i}_value_code`).value = select.value || '';
  document.getElementById(`rc_param${i}_value_desc`).value = select.value ? opt.textContent : '';
}
function onParamCurrentDateChange(i){
  const cb = document.getElementById(`rc_param${i}_currentDate`);
  const dateEl = document.getElementById(`rc_param${i}_value_date`);
  dateEl.disabled = cb.checked;
  if(cb.checked) dateEl.value = '';
}
function readParamRow(i){
  const name = (document.getElementById(`rc_param${i}_name`)?.value || '').trim();
  const type = document.getElementById(`rc_param${i}_type`)?.value || '';
  const hidden = document.getElementById(`rc_param${i}_hidden`)?.checked || false;
  if(type === 'Дата'){
    const useCurrentDate = document.getElementById(`rc_param${i}_currentDate`)?.checked || false;
    const rawValue = useCurrentDate ? '' : fromISODate(document.getElementById(`rc_param${i}_value_date`)?.value);
    return {name, type, value: rawValue === '—' ? '' : rawValue, useCurrentDate, hidden};
  }
  if(type === 'Число') return {name, type, value: document.getElementById(`rc_param${i}_value_number`)?.value || '', hidden};
  if(type === 'Строка') return {name, type, value: document.getElementById(`rc_param${i}_value_string`)?.value || '', hidden};
  if(FC_REF_DIRECTORIES.includes(type) && type !== '—'){
    return {
      name, type, hidden,
      value: document.getElementById(`rc_param${i}_value_code`)?.value || '',
      description: document.getElementById(`rc_param${i}_value_desc`)?.value || '',
    };
  }
  return {name, type:'', value:'', hidden};
}
function syncParamsDraftFromDOM(){
  reportConfigParamsDraft = reportConfigParamsDraft.map((_, idx)=>readParamRow(idx+1));
}
function renderParamsDraft(){
  const wrap = document.getElementById('rc_paramsWrap');
  wrap.innerHTML = reportConfigParamsDraft.map((p,idx)=>paramRowHtml(idx+1, p, true)).join('')
    + `<button type="button" class="btn ghost" onclick="addParamRow()">+ Добавить параметр</button>`;
  reportConfigParamsDraft.forEach((p,idx)=>{
    updateParamTypeUI(idx+1);
    onParamCurrentDateChange(idx+1);
    if(FC_REF_DIRECTORIES.includes(p.type) && p.type !== '—'){
      document.getElementById(`rc_param${idx+1}_value_dir`).dataset.code = p.value || '';
      const sel = document.getElementById(`rc_param${idx+1}_value_dir`);
      sel.value = p.value || '';
    }
  });
}
function addParamRow(){
  syncParamsDraftFromDOM();
  reportConfigParamsDraft.push({name:'', type:'', value:'', hidden:false});
  renderParamsDraft();
}
function removeParamRow(idx){
  syncParamsDraftFromDOM();
  reportConfigParamsDraft.splice(idx, 1);
  renderParamsDraft();
}
function openReportConfigEdit(id){
  reportConfigEditingId = id;
  const r = id ? REPORTS_CONFIG.find(x=>x.id===id) : null;
  document.getElementById('reportConfigTitle').textContent = id ? 'Редактирование отчёта' : T('common.title.add_report');
  document.getElementById('reportConfigOverlay').classList.add('active');
  const nextOrder = r ? r.order : (Math.max(0, ...REPORTS_CONFIG.filter(x=>x.depositorId===(r?r.depositorId:Object.keys(DEPOSITORS)[0])).map(x=>x.order||0)) + 1);
  document.getElementById('rc_order').value = nextOrder;
  document.getElementById('rc_depositorId').innerHTML = Object.values(DEPOSITORS).map(k=>`<option value="${k.id}" ${r&&r.depositorId===k.id?'selected':''}>${k.name}</option>`).join('');
  document.getElementById('rc_name').value = r ? r.name : '';
  document.getElementById('rc_wmsCode').value = r ? r.wmsCode : '';
  document.getElementById('rc_location').innerHTML = REPORT_LOCATION_OPTIONS.map(l=>`<option value="${l}" ${r&&r.location===l?'selected':''}>${l}</option>`).join('');
  reportConfigParamsDraft = ((r && r.params) || []).map(p=>({...p}));
  renderParamsDraft();
}
function closeReportConfigEdit(){
  document.getElementById('reportConfigOverlay').classList.remove('active');
  reportConfigEditingId = null;
}
function saveReportConfig(){
  syncParamsDraftFromDOM();
  const name = document.getElementById('rc_name').value.trim();
  const wmsCode = document.getElementById('rc_wmsCode').value.trim();
  const orderRaw = document.getElementById('rc_order').value;
  const depositorId = document.getElementById('rc_depositorId').value;
  const location = document.getElementById('rc_location').value;
  const errors = [];
  if(!name) errors.push('не заполнено наименование отчёта');
  if(!wmsCode) errors.push('не заполнен код отчёта в WMS');
  if(!orderRaw || isNaN(Number(orderRaw))) errors.push('не заполнен порядковый номер');
  if(!depositorId) errors.push('не выбран поклажедатель');
  if(!location) errors.push('не выбрано расположение');
  reportConfigParamsDraft.forEach((p,idx)=>{
    const hasAny = p.name || p.type;
    if(hasAny && (!p.name || !p.type)){
      errors.push(`параметр №${idx+1}: не заполнено наименование или тип`);
    }
  });
  if(errors.length){
    notify(T('common.message.could_not_save_the_report') + errors.join('; '));
    renderParamsDraft();
    return;
  }
  const params = reportConfigParamsDraft.filter(p=>p.name && p.type);
  const orderVal = Number(orderRaw);
  const data = {
    order: orderVal,
    depositorId,
    name, wmsCode,
    location,
    params,
  };
  if(reportConfigEditingId){
    const r = REPORTS_CONFIG.find(x=>x.id===reportConfigEditingId);
    if(r) Object.assign(r, data);
  }else{
    REPORTS_CONFIG.push({id:'r'+Date.now(), ...data});
  }
  closeReportConfigEdit();
  renderReportsConfigTable();
  renderReports();
  notify(T('common.message.report_settings_saved'));
}
function deleteReportConfig(id){
  const idx = REPORTS_CONFIG.findIndex(x=>x.id===id);
  if(idx > -1) REPORTS_CONFIG.splice(idx, 1);
  renderReportsConfigTable();
  renderReports();
  notify(T('common.message.report_removed_from_settings'));
}

/* ============== Расширенные настройки: конфигурация полей документов ============== */
const FC_REF_DIRECTORIES = ['—', 'Товары', 'Поставщики', 'Грузополучатели', 'Типы заказов', 'Типы ожидаемых документов', 'Поклажедатели', 'Склады'];
const FC_TRANSPORT_FIELDS = new Set(['trip','carrier','transportType','transportBrand','carNumber','trailerNumber','techPassportNumber','containerNumber','sealNumber','driverName','driverPhone','driverIdNumber','driverInn','deliveryDate']);
const SPECIAL_HEADER_FIELDS = {
  receipts: new Set(['no','wmsNumber','erpNo','author','comment','date','status','supplier','docType','warehouse','accompanyingDocNumber','gtd','eta','arrivalDate','receivingEndDate','weight','volume','lineCount','palletCount','cargoPlaces','totalPieces','trip','carrier','transportType','transportBrand','carNumber','trailerNumber','techPassportNumber','containerNumber','sealNumber','driverName','driverPhone','driverIdNumber','driverInn']),
  orders: new Set(['no','wmsNumber','erpNo','author','comment','date','status','orderType','warehouse','consignee','deliveryAddress','plannedShip','ship','weight','volume','lineCount','palletCount','cargoPlaces','totalPieces','trip','carrier','transportType','transportBrand','carNumber','trailerNumber','techPassportNumber','containerNumber','sealNumber','driverName','driverPhone','driverIdNumber','driverInn','deliveryDate']),
  transfers: new Set(['no','wmsNumber','erpNo','author','comment','date','status','fromWarehouse','toWarehouse','shipDate','receivedDate','weight','volume','lineCount','palletCount','cargoPlaces','totalPieces','trip','carrier','transportType','transportBrand','carNumber','trailerNumber','techPassportNumber','containerNumber','sealNumber','driverName','driverPhone','driverIdNumber','driverInn']),
};
const FC_SKIP_HEADER_FIELDS = new Set(['no','author','status']);
function fcReceiptSpecialFieldHtml(dbField, r, isDraft){
  const L = (label) => getDepositorFieldLabel('receipts', dbField) || label;
  switch(dbField){
    case 'erpNo': return kvField(L('Номер документа (в ERP клиента)'), r.erpNo, {id:'rf_erpNo', editable:isDraft});
    case 'wmsNumber': return kvField(L('Номер документа WMS'), r.wmsNumber);
    case 'supplier': return kvField(L('Поставщик'), r.supplier, {id:'rf_supplier', editable:isDraft, type:'select', options:getSupplierOptions()});
    case 'docType': return kvField(L('Тип приходного документа'), r.docType, {id:'rf_docType', editable:isDraft, type:'select', options:RECEIPT_DOC_TYPES});
    case 'warehouse': return kvField(L('Склад'), WAREHOUSES[r.warehouse]?.name || r.warehouse, {id:'rf_warehouse', editable:isDraft});
    case 'accompanyingDocNumber': return kvField(L('Номер сопроводительного документа'), r.accompanyingDocNumber, {id:'rf_accompanyingDocNumber', editable:isDraft});
    case 'invoiceNumber': return kvField(L('Номер Инвойса'), r.invoiceNumber, {id:'rf_invoiceNumber', editable:isDraft});
    case 'gtd': return kvField(L('ГТД'), r.gtd, {id:'rf_gtd', editable:isDraft});
    case 'comment': return kvField(L('Комментарий'), r.comment, {id:'rf_comment', editable:isDraft});
    case 'date': return kvField(L('Дата создания'), r.date, {id:'rf_date', editable:isDraft, type:'date'});
    case 'eta': return kvField(L('Ожидаемая дата поставки'), r.eta, {id:'rf_eta', editable:isDraft, type:'date'});
    case 'arrivalDate': return kvField(L('Дата прибытия'), r.status !== 'draft' ? r.eta : '—');
    case 'receivingEndDate': return kvField(L('Дата окончания приёмки'), r.status === 'ok' ? r.eta : '—');
    case 'weight': return kvField(L('Вес, кг'), r.weight, {id:'rf_weight', editable:isDraft, type:'number', step:'0.1'});
    case 'volume': return kvField(L('Объём, м³'), (typeof r.volume==='number' ? r.volume.toFixed(1) : r.volume), {id:'rf_volume', editable:isDraft, type:'number', step:'0.1'});
    case 'lineCount': return kvField(L('Количество строк'), r.lineCount, {id:'rf_lineCount'});
    case 'palletCount': return kvField(L('Количество паллет'), r.palletCount, {id:'rf_palletCount', editable:isDraft, type:'number'});
    case 'cargoPlaces': return kvField(L('Количество грузомест'), r.cargoPlaces, {id:'rf_cargoPlaces', editable:isDraft, type:'number'});
    case 'totalPieces': return kvField(L('Количество штук'), r.totalPieces, {id:'rf_totalPieces'});
    case 'trip': return kvField(L('Рейс'), r.trip, {id:'rf_trip', editable:isDraft});
    case 'carrier': return kvField(L('Перевозчик'), r.carrier, {id:'rf_carrier', editable:isDraft});
    case 'transportType': return kvField(L('Тип транспорта'), r.transportType, {id:'rf_transportType', editable:isDraft});
    case 'transportBrand': return kvField(L('Марка транспорта'), r.transportBrand, {id:'rf_transportBrand', editable:isDraft});
    case 'carNumber': return kvField(L('Номер авто'), r.carNumber, {id:'rf_carNumber', editable:isDraft});
    case 'trailerNumber': return kvField(L('Номер прицепа'), r.trailerNumber, {id:'rf_trailerNumber', editable:isDraft});
    case 'techPassportNumber': return kvField(L('Номер технического паспорта'), r.techPassportNumber, {id:'rf_techPassportNumber', editable:isDraft});
    case 'containerNumber': return kvField(L('Номер контейнера'), r.containerNumber, {id:'rf_containerNumber', editable:isDraft});
    case 'sealNumber': return kvField(L('Номер пломбы'), r.sealNumber, {id:'rf_sealNumber', editable:isDraft});
    case 'driverName': return kvField(L('ФИО водителя'), r.driverName, {id:'rf_driverName', editable:isDraft});
    case 'driverPhone': return kvField(L('Телефон водителя'), r.driverPhone, {id:'rf_driverPhone', editable:isDraft});
    case 'driverIdNumber': return kvField(L('Номер удостоверения (паспорта) водителя'), r.driverIdNumber, {id:'rf_driverIdNumber', editable:isDraft});
    case 'driverInn': return kvField(L('ИИН (ИНН) водителя'), r.driverInn, {id:'rf_driverInn', editable:isDraft});
    default: return null;
  }
}
function fcOrderSpecialFieldHtml(dbField, o, isDraft){
  const L = (label) => getDepositorFieldLabel('orders', dbField) || label;
  switch(dbField){
    case 'erpNo': return kvField(L('Номер документа (в ERP клиента)'), o.erpNo, {id:'of_erpNo', editable:isDraft});
    case 'wmsNumber': return kvField(L('Номер документа WMS'), o.wmsNumber);
    case 'orderType': return kvField(L('Тип заказа'), o.orderType, {id:'of_orderType', editable:isDraft, type:'select', options:ORDER_TYPES});
    case 'warehouse': return kvField(L('Склад'), WAREHOUSES[o.warehouse]?.name || o.warehouse, {id:'of_warehouse', editable:isDraft});
    case 'consignee': return kvField(L('Грузополучатель'), o.consignee, {id:'of_consignee', editable:isDraft, type:'select', options:getConsigneeOptions(), onchange:'onOrderConsigneeChange()'});
    case 'deliveryAddress': return kvField(L('Место доставки'), o.deliveryAddress, {id:'of_deliveryAddress', editable:isDraft, type:'select', options:getConsigneeAddresses(o.consignee)});
    case 'consigneeContact': return kvField(L('Контактные данные грузополучателя'), o.consigneeContact, {id:'of_consigneeContact', editable:isDraft});
    case 'comment': return kvField(L('Комментарий'), o.comment, {id:'of_comment', editable:isDraft});
    case 'date': return kvField(L('Дата создания'), o.date, {id:'of_date', editable:isDraft, type:'date'});
    case 'plannedShip': return kvField(L('Планируемая дата отгрузки'), o.plannedShip, {id:'of_plannedShip', editable:isDraft, type:'date'});
    case 'ship': return kvField(L('Дата отгрузки'), o.ship);
    case 'weight': return kvField(L('Вес, кг'), o.weight, {id:'of_weight', editable:isDraft, type:'number', step:'0.1'});
    case 'volume': return kvField(L('Объём, м³'), (typeof o.volume==='number' ? o.volume.toFixed(1) : o.volume), {id:'of_volume', editable:isDraft, type:'number', step:'0.1'});
    case 'lineCount': return kvField(L('Количество строк'), o.lineCount, {id:'of_lineCount'});
    case 'palletCount': return kvField(L('Количество паллет'), o.palletCount, {id:'of_palletCount', editable:isDraft, type:'number'});
    case 'cargoPlaces': return kvField(L('Количество грузомест'), o.cargoPlaces, {id:'of_cargoPlaces', editable:isDraft, type:'number'});
    case 'totalPieces': return kvField(L('Количество штук'), o.totalPieces, {id:'of_totalPieces'});
    case 'trip': return kvField(L('Рейс'), o.trip, {id:'of_trip', editable:isDraft});
    case 'carrier': return kvField(L('Перевозчик'), o.carrier, {id:'of_carrier', editable:isDraft});
    case 'transportType': return kvField(L('Тип транспорта'), o.transportType, {id:'of_transportType', editable:isDraft});
    case 'transportBrand': return kvField(L('Марка транспорта'), o.transportBrand, {id:'of_transportBrand', editable:isDraft});
    case 'carNumber': return kvField(L('Номер авто'), o.carNumber, {id:'of_carNumber', editable:isDraft});
    case 'trailerNumber': return kvField(L('Номер прицепа'), o.trailerNumber, {id:'of_trailerNumber', editable:isDraft});
    case 'techPassportNumber': return kvField(L('Номер технического паспорта'), o.techPassportNumber, {id:'of_techPassportNumber', editable:isDraft});
    case 'containerNumber': return kvField(L('Номер контейнера'), o.containerNumber, {id:'of_containerNumber', editable:isDraft});
    case 'sealNumber': return kvField(L('Номер пломбы'), o.sealNumber, {id:'of_sealNumber', editable:isDraft});
    case 'deliveryDate': return kvField(L('Дата доставки'), o.deliveryDate, {id:'of_deliveryDate', editable:isDraft, type:'date'});
    case 'driverName': return kvField(L('ФИО водителя'), o.driverName, {id:'of_driverName', editable:isDraft});
    case 'driverPhone': return kvField(L('Телефон водителя'), o.driverPhone, {id:'of_driverPhone', editable:isDraft});
    case 'driverIdNumber': return kvField(L('Номер удостоверения (паспорта) водителя'), o.driverIdNumber, {id:'of_driverIdNumber', editable:isDraft});
    case 'driverInn': return kvField(L('ИИН (ИНН) водителя'), o.driverInn, {id:'of_driverInn', editable:isDraft});
    default: return null;
  }
}
function fcTransferSpecialFieldHtml(dbField, t, isDraft){
  const L = (label) => getDepositorFieldLabel('transfers', dbField) || label;
  switch(dbField){
    case 'erpNo': return kvField(L('Номер документа (в ERP клиента)'), t.erpNo, {id:'tf_erpNo', editable:isDraft});
    case 'wmsNumber': return kvField(L('Номер документа WMS'), t.wmsNumber);
    case 'fromWarehouse': return kvField(L('Склад-отправитель'), WAREHOUSES[t.fromWarehouse]?.name || t.fromWarehouse, {id:'tf_fromWarehouse', editable:isDraft, type:'select', options:Object.values(WAREHOUSES).map(w=>w.name)});
    case 'toWarehouse': return kvField(L('Склад-получатель'), WAREHOUSES[t.toWarehouse]?.name || t.toWarehouse, {id:'tf_toWarehouse', editable:isDraft, type:'select', options:Object.values(WAREHOUSES).map(w=>w.name)});
    case 'comment': return kvField(L('Комментарий'), t.comment, {id:'tf_comment', editable:isDraft});
    case 'date': return kvField(L('Дата создания'), t.date, {id:'tf_date', editable:isDraft, type:'date'});
    case 'shipDate': return kvField(L('Дата отгрузки'), t.shipDate, {id:'tf_shipDate', editable:isDraft, type:'date'});
    case 'receivedDate': return kvField(L('Дата приёмки'), t.receivedDate);
    case 'weight': return kvField(L('Вес, кг'), t.weight, {id:'tf_weight', editable:isDraft, type:'number', step:'0.1'});
    case 'volume': return kvField(L('Объём, м³'), (typeof t.volume==='number' ? t.volume.toFixed(1) : t.volume), {id:'tf_volume', editable:isDraft, type:'number', step:'0.1'});
    case 'lineCount': return kvField(L('Количество строк'), t.lineCount, {id:'tf_lineCount'});
    case 'palletCount': return kvField(L('Количество паллет'), t.palletCount, {id:'tf_palletCount', editable:isDraft, type:'number'});
    case 'cargoPlaces': return kvField(L('Количество грузомест'), t.cargoPlaces, {id:'tf_cargoPlaces', editable:isDraft, type:'number'});
    case 'totalPieces': return kvField(L('Количество штук'), t.totalPieces, {id:'tf_totalPieces'});
    case 'trip': return kvField(L('Рейс'), t.trip, {id:'tf_trip', editable:isDraft});
    case 'carrier': return kvField(L('Перевозчик'), t.carrier, {id:'tf_carrier', editable:isDraft});
    case 'transportType': return kvField(L('Тип транспорта'), t.transportType, {id:'tf_transportType', editable:isDraft});
    case 'transportBrand': return kvField(L('Марка транспорта'), t.transportBrand, {id:'tf_transportBrand', editable:isDraft});
    case 'carNumber': return kvField(L('Номер авто'), t.carNumber, {id:'tf_carNumber', editable:isDraft});
    case 'trailerNumber': return kvField(L('Номер прицепа'), t.trailerNumber, {id:'tf_trailerNumber', editable:isDraft});
    case 'techPassportNumber': return kvField(L('Номер технического паспорта'), t.techPassportNumber, {id:'tf_techPassportNumber', editable:isDraft});
    case 'containerNumber': return kvField(L('Номер контейнера'), t.containerNumber, {id:'tf_containerNumber', editable:isDraft});
    case 'sealNumber': return kvField(L('Номер пломбы'), t.sealNumber, {id:'tf_sealNumber', editable:isDraft});
    case 'driverName': return kvField(L('ФИО водителя'), t.driverName, {id:'tf_driverName', editable:isDraft});
    case 'driverPhone': return kvField(L('Телефон водителя'), t.driverPhone, {id:'tf_driverPhone', editable:isDraft});
    case 'driverIdNumber': return kvField(L('Номер удостоверения (паспорта) водителя'), t.driverIdNumber, {id:'tf_driverIdNumber', editable:isDraft});
    case 'driverInn': return kvField(L('ИИН (ИНН) водителя'), t.driverInn, {id:'tf_driverInn', editable:isDraft});
    default: return null;
  }
}
let fcActiveDocTab = {};
function isTransportEditable(doc){
  return doc.status === 'draft' || doc.status === 'warn';
}
function fcDocRefByType(docType){
  if(docType === 'receipts') return currentReceiptRef;
  if(docType === 'orders') return currentOrderRef;
  if(docType === 'transfers') return currentTransferRef;
  return null;
}
function openPrintDocumentModal(docType){
  const doc = fcDocRefByType(docType);
  if(!doc) return;
  const locationMap = {receipts:'Поступления', orders:'Заказы', transfers:'Перемещения'};
  const titleMap = {receipts:T('common.message.receipt'), orders:T('orders.title.shipment_order'), transfers:T('common.message.transfer')};
  const location = locationMap[docType];
  const depositorIds = getScopedReportDepositorIds();
  const list = REPORTS_CONFIG.filter(r=>r.location === location && depositorIds.includes(r.depositorId)).sort((a,b)=>(a.order||0)-(b.order||0));
  document.getElementById('printDocTitle').textContent = titleMap[docType] + ' ' + (doc.erpNo || doc.no);
  document.getElementById('printDocWmsCode').textContent = 'Код документа WMS: ' + (doc.wmsNumber || '—');
  document.getElementById('printDocReportsList').innerHTML = list.length ? list.map(r=>`
    <tr><td>${r.name}</td><td><button class="btn ghost" onclick="requestGenerateReport('${r.id}')">Сформировать</button></td></tr>
  `).join('') : `<tr><td colspan="2" style="color:var(--muted);">Нет доступных отчётов и актов для этого документа</td></tr>`;
  document.getElementById('printDocOverlay').classList.add('active');
}
function closePrintDocumentModal(){
  document.getElementById('printDocOverlay').classList.remove('active');
}
function fcReopenDoc(docType, no){
  if(docType === 'receipts') openReceipt(no);
  if(docType === 'orders') openOrder(no);
  if(docType === 'transfers') openTransfer(no);
}
function fcMockPhotos(doc){
  if(doc.status === 'draft') return [];
  return [
    {id:'wms1', objectKey:'depositors/'+currentDepositorId+'/'+(doc.no||'doc')+'/wms-001.jpg', url:null, fileName:'Фото при приёмке груза 1.jpg', caption:'Фото при приёмке груза 1.jpg', source:'wms', uploadedBy:'Интеграция WMS', uploadedAt:doc.date || ''},
    {id:'wms2', objectKey:'depositors/'+currentDepositorId+'/'+(doc.no||'doc')+'/wms-002.jpg', url:null, fileName:'Фото упаковки и маркировки.jpg', caption:'Фото упаковки и маркировки.jpg', source:'wms', uploadedBy:'Интеграция WMS', uploadedAt:doc.date || ''},
  ];
}
function fcRenderPhotosTab(docType, doc){
  if(!doc.photos) doc.photos = fcMockPhotos(doc);
  const cards = doc.photos.map((p,idx)=>{
    const thumb = p.url
      ? `<img src="${p.url}" style="width:100%; height:120px; object-fit:cover; border-radius:8px 8px 0 0; display:block;">`
      : `<div style="width:100%; height:120px; background:var(--bg); display:flex; align-items:center; justify-content:center; border-radius:8px 8px 0 0; color:var(--muted); font-size:30px;">📷</div>`;
    return `
      <div style="width:200px; border:1px solid var(--line); border-radius:8px; overflow:hidden; background:#fff;">
        ${thumb}
        <div style="padding:8px 10px;">
          <div style="font-size:11px; color:var(--muted); margin-bottom:6px;">${p.source==='wms' ? '📡 Из WMS (интеграция)' : '💻 Загружено вручную'}</div>
          <input type="text" class="kv-input kv-input-editable" style="font-size:12px; padding:6px 8px;" value="${String(p.caption).replace(/"/g,'&quot;')}" oninput="fcUpdatePhotoCaption('${docType}', ${idx}, this.value)">
          ${p.source==='manual' ? `<button class="btn ghost" style="margin-top:6px; width:100%; color:#c0392b; border-color:#e3b3ac; font-size:12px; padding:5px;" onclick="fcDeletePhoto('${docType}', ${idx})">${T('common.button.delete')}</button>` : ''}
        </div>
      </div>`;
  }).join('');
  return `
    <div class="hint" style="margin-bottom:14px;">${T('common.text.most_photos_arrive_automatically_f')}</div>
    <div style="display:flex; flex-wrap:wrap; gap:16px;">
      ${cards}
      <div style="width:200px; min-height:176px; border:2px dashed var(--line); border-radius:8px; display:flex; align-items:center; justify-content:center; cursor:pointer;" onclick="document.getElementById('fcPhotoFileInput_${docType}').click()">
        <div style="text-align:center; color:var(--muted); font-size:13px; padding:12px;">${T('common.button.add_photo')}<br><span style="font-size:11px;">${T('common.text.from_your_computer')}</span></div>
      </div>
    </div>
    <input type="file" id="fcPhotoFileInput_${docType}" accept="image/*" multiple style="display:none;" onchange="fcHandlePhotoUpload('${docType}', this)">
  `;
}
function fcUpdatePhotoCaption(docType, idx, value){
  const doc = fcDocRefByType(docType);
  if(doc && doc.photos && doc.photos[idx]) doc.photos[idx].caption = value;
}
function fcHandlePhotoUpload(docType, input){
  const doc = fcDocRefByType(docType);
  if(!doc) return;
  if(!doc.photos) doc.photos = [];
  const files = [...input.files];
  if(!files.length) return;
  let pending = files.length;
  files.forEach(file=>{
    const reader = new FileReader();
    reader.onload = function(e){
      doc.photos.push({
        id:'m'+Date.now()+Math.random().toString(36).slice(2),
        objectKey:'depositors/'+currentDepositorId+'/'+(doc.no||'doc')+'/manual-'+Date.now()+'-'+file.name,
        url:e.target.result,
        fileName:file.name,
        caption:file.name,
        mimeType:file.type,
        fileSize:file.size,
        source:'manual',
        uploadedBy:(currentUser && currentUser.full_name) || '',
        uploadedAt:fromISODate(new Date().toISOString().slice(0,10)),
      });
      pending--;
      if(pending === 0) fcReopenDoc(docType, doc.no);
    };
    reader.readAsDataURL(file);
  });
  input.value = '';
}
function fcDeletePhoto(docType, idx){
  const doc = fcDocRefByType(docType);
  if(!doc || !doc.photos) return;
  doc.photos.splice(idx, 1);
  fcReopenDoc(docType, doc.no);
}
function fcSaveTransportSection(docType){
  const doc = fcDocRefByType(docType);
  if(!doc) return;
  if(doc.status === 'draft'){
    ({receipts:saveReceiptDraft, orders:saveOrderDraft, transfers:saveTransferDraft}[docType])();
    notify(T('common.message.transport_details_saved'));
    return;
  }
  const prefix = {receipts:'rf_', orders:'of_', transfers:'tf_'}[docType];
  FC_TRANSPORT_FIELDS.forEach(dbField=>{
    const el = document.getElementById(prefix + dbField);
    if(el) doc[dbField] = el.value;
  });
  notify(T('common.message.transport_details_saved'));
  fcReopenDoc(docType, doc.no);
}
const STATIC_GENERAL_FIELDS = {
  receipts: ['erpNo','wmsNumber','supplier','docType','warehouse','accompanyingDocNumber','gtd','comment','date','eta','arrivalDate','receivingEndDate','weight','volume','lineCount','palletCount','cargoPlaces','totalPieces'],
  orders: ['erpNo','wmsNumber','orderType','warehouse','consignee','deliveryAddress','comment','date','plannedShip','ship','weight','volume','lineCount','palletCount','cargoPlaces','totalPieces'],
  transfers: ['erpNo','wmsNumber','fromWarehouse','toWarehouse','comment','date','shipDate','receivedDate','weight','volume','lineCount','palletCount','cargoPlaces','totalPieces'],
};
const STATIC_TRANSPORT_FIELDS = ['trip','carrier','transportType','transportBrand','carNumber','trailerNumber','techPassportNumber','containerNumber','sealNumber','driverName','driverPhone','driverIdNumber','driverInn'];
const GENERAL_TAB_COLUMNS = {
  orders: [
    ['erpNo','wmsNumber','date','plannedShip','ship'],
    ['orderType','warehouse','consignee','deliveryAddress','consigneeContact'],
    ['lineCount','totalPieces','cargoPlaces','palletCount','weight','volume'],
  ],
  receipts: [
    ['erpNo','wmsNumber','date','eta','arrivalDate','receivingEndDate'],
    ['docType','warehouse','supplier','accompanyingDocNumber','invoiceNumber','gtd'],
    ['lineCount','totalPieces','cargoPlaces','palletCount','weight','volume'],
  ],
  transfers: [
    ['erpNo','wmsNumber','date','shipDate','receivedDate'],
    ['fromWarehouse','toWarehouse'],
    ['lineCount','totalPieces','cargoPlaces','palletCount','weight','volume'],
  ],
};
const NON_EDITABLE_GENERAL_FIELDS = new Set(['weight','volume']);
const TRANSPORT_TAB_COLUMNS = {
  orders: [
    ['trip','carrier','driverName','driverInn','driverIdNumber','driverPhone'],
    ['transportType','transportBrand','carNumber','trailerNumber','techPassportNumber'],
    ['containerNumber','sealNumber','deliveryDate'],
  ],
  receipts: [
    ['trip','carrier','driverName','driverInn','driverIdNumber','driverPhone'],
    ['transportType','transportBrand','carNumber','trailerNumber','techPassportNumber'],
    ['containerNumber','sealNumber'],
  ],
  transfers: [
    ['trip','carrier','driverName','driverInn','driverIdNumber','driverPhone'],
    ['transportType','transportBrand','carNumber','trailerNumber','techPassportNumber'],
    ['containerNumber','sealNumber'],
  ],
};
const STATIC_DOC_TABS = {
  receipts: ['Общая информация', 'Состав поступления', 'Транспортный раздел', 'Дополнительная информация', 'Фотографии', 'Услуги'],
  orders: ['Общая информация', 'Состав заказа', 'Транспортный раздел', 'Дополнительная информация', 'Фотографии', 'Услуги'],
  transfers: ['Общая информация', 'Состав перемещения', 'Транспортный раздел', 'Дополнительная информация', 'Фотографии', 'Услуги'],
};
const STATIC_TAB_KIND = {
  'Общая информация': 'general',
  'Состав поступления': 'composition', 'Состав заказа': 'composition', 'Состав перемещения': 'composition',
  'Транспортный раздел': 'transport',
  'Дополнительная информация': 'extra',
  'Фотографии': 'photos',
  'Услуги': 'services',
};
const FC_COMPOSITION_SECTION_ID = {receipts:'receiptCompositionSection', orders:'orderCompositionSection', transfers:'transferCompositionSection'};
const EXTRA_INFO_FIELDS = Array.from({length:10}, (_,i)=>'customField'+(i+1));
// Префикс поля на экране однозначно говорит, чья это карточка.
const EXTRA_INFO_TABLE = {rf:'receipts', of:'orders', tf:'transfers'};
function fcExtraInfoFieldHtml(prefix, doc, dbField, isDraft){
  const id = prefix + '_' + dbField;
  // Подпись собирается ПРИ ОТРИСОВКЕ, а не при загрузке файла: раньше
  // десять подписей вычислялись один раз на верхнем уровне скрипта,
  // то есть до входа, и оставались на языке, который был в тот момент.
  //
  // С V51 эти поля есть в справочнике колонок, поэтому у каждого свой
  // ключ текста (col.receipts.customField3) и своё переименование
  // под клиента. Запасной вариант — общая подпись с номером — остаётся
  // на случай, если ключа в каталоге текстов почему-то нет: пустая
  // подпись хуже обобщённой.
  const table = EXTRA_INFO_TABLE[prefix];
  const key = 'col.' + table + '.' + dbField;
  const known = typeof T === 'function' && T(key) !== key;
  const label = (window.PORTAL && PORTAL.fieldLabel && known)
    ? PORTAL.fieldLabel(table, dbField, key)
    : T('common.label.custom_field') + ' ' + dbField.replace('customField', '');
  return kvField(label, doc[dbField], {id, editable:isDraft});
}
function fcRenderDocTabs(docType, doc, isDraft, specialFn, buttonsId, contentId){
  let tabs = STATIC_DOC_TABS[docType].slice();
  if(currentUser && currentUser.hideServicesTab){
    tabs = tabs.filter(t=>t !== 'Услуги');
  }
  const state = fcActiveDocTab[docType];
  if(!state || state.no !== doc.no || !tabs.includes(state.tab)){
    fcActiveDocTab[docType] = { no: doc.no, tab: tabs[0] };
  }
  const active = fcActiveDocTab[docType].tab;
  const kind = STATIC_TAB_KIND[active];
  document.getElementById(buttonsId).innerHTML = tabs.map(t=>`
    <button class="admin-subtab-btn ${t===active?'active':''}" onclick="fcSwitchDocTab('${docType}', '${t}')">${localizeColumnLabel(t)}</button>
  `).join('');
  const compositionSection = document.getElementById(FC_COMPOSITION_SECTION_ID[docType]);
  if(compositionSection) compositionSection.style.display = kind === 'composition' ? '' : 'none';
  if(kind === 'composition'){
    document.getElementById(contentId).innerHTML = '';
    setTimeout(()=>updateTopScrollWidth(docType + 'Composition'), 0);
    return;
  }
  if(kind === 'photos'){
    document.getElementById(contentId).innerHTML = fcRenderPhotosTab(docType, doc);
    return;
  }
  if(kind === 'services'){
    document.getElementById(contentId).innerHTML = fcRenderServicesTab(docType, doc);
    return;
  }
  if(kind === 'extra'){
    const prefix = {receipts:'rf', orders:'of', transfers:'tf'}[docType];
    const html = EXTRA_INFO_FIELDS.map(f=>fcExtraInfoFieldHtml(prefix, doc, f, isDraft)).join('');
    document.getElementById(contentId).innerHTML = `<div class="kv-columns" style="display:flex; gap:20px 24px; flex-wrap:wrap;">${html}</div>`;
    return;
  }
  const transportEditable = isTransportEditable(doc);
  const fieldEditable = kind === 'transport' ? transportEditable : isDraft;
  if(kind === 'general' && GENERAL_TAB_COLUMNS[docType]){
    const cols = GENERAL_TAB_COLUMNS[docType];
    const colsHtml = cols.map(colFields=>{
      const html = colFields.map(dbField=>{
        const editFlag = NON_EDITABLE_GENERAL_FIELDS.has(dbField) ? false : fieldEditable;
        return specialFn(dbField, doc, editFlag);
      }).filter(Boolean).join('');
      return `<div class="kv-col" style="flex:1; min-width:220px; display:flex; flex-direction:column; gap:16px;">${html}</div>`;
    }).join('');
    const commentHtml = specialFn('comment', doc, fieldEditable) || '';
    document.getElementById(contentId).innerHTML = `<div class="kv-columns" style="display:flex; gap:20px 24px; flex-wrap:wrap;">${colsHtml}</div><div style="margin-top:20px; width:100%;">${commentHtml}</div>`;
    return;
  }
  const saveTransportBtn = (kind === 'transport' && transportEditable)
    ? `<div style="margin-top:18px;"><button class="btn primary" onclick="fcSaveTransportSection('${docType}')">Сохранить транспортную информацию</button></div>`
    : '';
  if(kind === 'transport' && TRANSPORT_TAB_COLUMNS[docType]){
    const cols = TRANSPORT_TAB_COLUMNS[docType];
    const colsHtml = cols.map(colFields=>{
      const html = colFields.map(dbField=>specialFn(dbField, doc, fieldEditable)).filter(Boolean).join('');
      return `<div class="kv-col" style="flex:1; min-width:220px; display:flex; flex-direction:column; gap:16px;">${html}</div>`;
    }).join('');
    document.getElementById(contentId).innerHTML = `<div class="kv-columns" style="display:flex; gap:20px 24px; flex-wrap:wrap;">${colsHtml}</div>${saveTransportBtn}`;
    return;
  }
  const fieldList = kind === 'transport' ? STATIC_TRANSPORT_FIELDS : STATIC_GENERAL_FIELDS[docType];
  const fieldsHtml = fieldList.map(dbField=>specialFn(dbField, doc, fieldEditable)).filter(Boolean).join('');
  document.getElementById(contentId).innerHTML = `<div class="kv-columns" style="display:flex; gap:20px 24px; flex-wrap:wrap;">${fieldsHtml}</div>${saveTransportBtn}`;
}
function fcSwitchDocTab(docType, tabName){
  const state = fcActiveDocTab[docType];
  if(state) state.tab = tabName;
  if(docType === 'receipts' && currentReceiptRef) openReceipt(currentReceiptRef.no);
  if(docType === 'orders' && currentOrderRef) openOrder(currentOrderRef.no);
  if(docType === 'transfers' && currentTransferRef) openTransfer(currentTransferRef.no);
}
function fcMockServices(docType, doc){
  const svcSets = {
    receipts: [
      {service:'Приёмка товара', qty:doc.lineCount || 1, cost:350},
      {service:'Разгрузка ТС', qty:1, cost:9000},
      {service:'Ответственное хранение', qty:doc.totalPieces || 1, cost:15},
    ],
    orders: [
      {service:'Отбор и упаковка заказа', qty:doc.lineCount || 1, cost:400},
      {service:'Погрузка ТС', qty:1, cost:9500},
      {service:'Ответственное хранение', qty:doc.totalPieces || 1, cost:15},
    ],
    transfers: [
      {service:'Внутреннее перемещение товара', qty:doc.lineCount || 1, cost:250},
      {service:'Погрузо-разгрузочные работы', qty:1, cost:6000},
    ],
  };
  return (svcSets[docType] || []).map((s,idx)=>{
    const sum = s.qty * s.cost;
    const markupPct = 15;
    const sumWithMarkup = Math.round(sum * (1 + markupPct/100));
    const vatRate = 12;
    const vatAmount = Math.round(sumWithMarkup * vatRate/100);
    const sumWithVat = sumWithMarkup + vatAmount;
    return {id:'svc'+idx, service:s.service, qty:s.qty, cost:s.cost, sum, markupPct, sumWithMarkup, vatRate, vatAmount, sumWithVat};
  });
}
function fcRenderServicesTab(docType, doc){
  if(!doc.services) doc.services = fcMockServices(docType, doc);
  const rows = doc.services.map(s=>`
    <tr>
      <td>${s.service}</td>
      <td class="num">${s.qty}</td>
      <td class="num">${s.cost.toLocaleString('ru-RU')}</td>
      <td class="num">${s.sum.toLocaleString('ru-RU')}</td>
      <td class="num">${s.markupPct}%</td>
      <td class="num">${s.sumWithMarkup.toLocaleString('ru-RU')}</td>
      <td class="num">${s.vatAmount.toLocaleString('ru-RU')}</td>
      <td class="num">${s.sumWithVat.toLocaleString('ru-RU')}</td>
    </tr>`).join('');
  const total = doc.services.reduce((sum,s)=>sum+s.sumWithVat, 0);
  return `
    <div class="table-card">
    <table>
    <thead><tr><th>${T('billing.column.service')}</th><th class="num">${T('common.label.quantity')}</th><th class="num">${T('sku.text.cost')}</th><th class="num">${T('common.label.amount')}</th><th class="num">${T('common.column.markup')}</th><th class="num">${T('common.column.amount_with_markup')}</th><th class="num">${T('common.column.vat_12')}</th><th class="num">${T('billing.column.amount_incl_vat')}</th></tr></thead>
    <tbody>${rows}
    <tr style="font-weight:700;"><td colspan="7">${T('common.text.total')}</td><td class="num">${total.toLocaleString('ru-RU')}</td></tr>
    </tbody>
    </table>
    </div>`;
}

/* ============== Панель настройки колонок ============== */
let colPickerTable = null;
let colPickerDraft = null; // {order:[...], hidden:Set}

function openColumnPicker(tableKey){
  colPickerTable = tableKey;
  const locked = tableColumnLocked[tableKey] || new Set();
  colPickerDraft = {
    order: [...tableColumnState[tableKey]].filter(id=>!locked.has(id)),
    hidden: new Set([...tableColumnHidden[tableKey]].filter(id=>!locked.has(id))),
  };
  renderColumnPickerList();
  document.getElementById('colOverlay').classList.add('active');
}
function closeColumnPicker(){
  document.getElementById('colOverlay').classList.remove('active');
  colPickerTable = null;
}
function renderColumnPickerList(){
  const cols = tableColumns[colPickerTable];
  const list = document.getElementById('colList');
  list.innerHTML = colPickerDraft.order.map((id, idx)=>{
    const c = cols.find(c=>c.id===id);
    const checked = !colPickerDraft.hidden.has(id) ? 'checked' : '';
    return `<div class="col-item" draggable="true" data-idx="${idx}">
      <span class="handle">⠿</span>
      <label><input type="checkbox" ${checked} onchange="toggleColVisibility('${id}')">${localizeColumnLabel(c.label)}</label>
    </div>`;
  }).join('');
  attachDragHandlers();
}
function toggleColVisibility(id){
  if(colPickerDraft.hidden.has(id)) colPickerDraft.hidden.delete(id);
  else colPickerDraft.hidden.add(id);
}
function attachDragHandlers(){
  const items = document.querySelectorAll('#colList .col-item');
  let dragSrcIdx = null;
  items.forEach(el=>{
    el.addEventListener('dragstart', ()=>{ dragSrcIdx = +el.dataset.idx; el.classList.add('dragging'); });
    el.addEventListener('dragend', ()=>{ el.classList.remove('dragging'); });
    el.addEventListener('dragover', (e)=>{ e.preventDefault(); });
    el.addEventListener('drop', (e)=>{
      e.preventDefault();
      const targetIdx = +el.dataset.idx;
      if(dragSrcIdx===null || dragSrcIdx===targetIdx) return;
      const moved = colPickerDraft.order.splice(dragSrcIdx,1)[0];
      colPickerDraft.order.splice(targetIdx,0,moved);
      renderColumnPickerList();
    });
  });
}
function applyColumnPicker(){
  const locked = tableColumnLocked[colPickerTable] || new Set();
  const lockedIds = tableColumns[colPickerTable].map(c=>c.id).filter(id=>locked.has(id));
  tableColumnState[colPickerTable] = [...colPickerDraft.order, ...lockedIds];
  tableColumnHidden[colPickerTable] = new Set(colPickerDraft.hidden);
  const renderFn = {receipts:renderReceipts, orders:renderOrders, stock:renderStock, stockBatch:renderStockBatchTable, receiptComposition:renderReceiptComposition, orderComposition:renderOrderComposition, transferComposition:renderTransferComposition, orderLineDetail:renderOrderLineDetailTable, receiptLineDetail:renderReceiptLineDetailTable, transferLineDetail:renderTransferLineDetailTable, sku:renderSku, transfers:renderTransfers}[colPickerTable];
  renderFn();
  updateTopScrollWidth(colPickerTable);
  closeColumnPicker();
  notify(T('common.message.column_settings_applied'));
}
function resetColumnPicker(){
  const locked = tableColumnLocked[colPickerTable] || new Set();
  colPickerDraft = {
    order: tableColumns[colPickerTable].map(c=>c.id).filter(id=>!locked.has(id)),
    hidden: new Set(),
  };
  renderColumnPickerList();
}


/* ============== ДЕТАЛИ: Поступление ============== */
function toISODate(str){
  if(!str || str==='—') return '';
  const parts = str.split('.');
  if(parts.length !== 3) return '';
  const [d,m,y] = parts;
  return `${y}-${m.padStart(2,'0')}-${d.padStart(2,'0')}`;
}
function fromISODate(iso){
  if(!iso) return '—';
  const [y,m,d] = iso.split('-');
  return `${d}.${m}.${y}`;
}
function getSupplierOptions(){
  return [...new Set((receipts||[]).map(r=>r.supplier).filter(Boolean))].sort();
}
function kvField(label, value, opts){
  opts = opts || {};
  label = localizeColumnLabel(label);
  const safe = String(value ?? '—').replace(/"/g, '&quot;');
  const idAttr = opts.id ? `id="${opts.id}"` : '';
  if(!opts.editable){
    return `<div class="kv"><label>${label}</label><input class="kv-input" ${idAttr} type="text" value="${safe}" readonly tabindex="-1"></div>`;
  }
  if(opts.type === 'date'){
    return `<div class="kv"><label>${label}</label><input class="kv-input kv-input-editable" ${idAttr} type="date" value="${toISODate(value)}"></div>`;
  }
  if(opts.type === 'number'){
    return `<div class="kv"><label>${label}</label><input class="kv-input kv-input-editable" ${idAttr} type="number" min="0" step="${opts.step || 1}" value="${safe}" oninput="validateNumericInput(this)"></div>`;
  }
  if(opts.type === 'select'){
    const options = opts.options || [];
    const placeholder = !value ? `<option value="" selected>${T('sku.text.not_selected')}</option>` : '';
    const optionsHtml = options.map(o=>`<option value="${String(o).replace(/"/g,'&quot;')}" ${o===value?'selected':''}>${o}</option>`).join('');
    const onchangeAttr = opts.onchange ? `onchange="${opts.onchange}"` : '';
    return `<div class="kv"><label>${label}</label><select class="kv-input kv-input-editable" ${idAttr} ${onchangeAttr}>${placeholder}${optionsHtml}</select></div>`;
  }
  return `<div class="kv"><label>${label}</label><input class="kv-input kv-input-editable" ${idAttr} type="text" value="${safe}"></div>`;
}
function validateNumericInput(el){
  el.classList.toggle('kv-input-invalid', el.value !== '' && (isNaN(el.value) || Number(el.value) < 0));
}
let currentReceiptComposition = [];
let currentReceiptRef = null;
function openReceipt(no){
  const r = receipts.find(x=>x.no===no);
  if(!r) return;
  currentReceiptRef = r;
  const isDraft = r.status === 'draft';
  document.getElementById('receiptDraftActions').style.display = isDraft ? '' : 'none';
  document.getElementById('receiptDocMeta').innerHTML = `<div>${T('common.label.document_id')}<strong style="color:var(--ink);">${r.no}</strong></div><div>${T('common.label.created_by')}<strong style="color:var(--ink);">${r.author || '—'}</strong></div><div>${T('common.label.contract_number')}<strong style="color:var(--ink);">${getCurrentDepositorContractNumber()}</strong></div>`;
  document.getElementById('receiptDetailBanner').innerHTML = '';
  document.getElementById('receiptDetailTitle').innerHTML = `${T('common.message.receipt')} ${r.erpNo || r.no} ${badge(r.status, r.statusLabel)}`;
  fcRenderDocTabs('receipts', r, isDraft, fcReceiptSpecialFieldHtml, 'receiptDetailTabButtons', 'receiptDetailTabContent');
  if(!r.lines){
    r.lines = mockReceiptLines(r.items, isDraft);
  }
  currentReceiptComposition = r.lines.map(l=>({...l, checked:false}));
  document.getElementById('receiptCheckboxHead').style.display = isDraft ? '' : 'none';
  document.getElementById('receiptAddRowBtn').style.display = isDraft ? '' : 'none';
  document.getElementById('receiptDeleteRowsBtn').style.display = isDraft ? '' : 'none';
  renderReceiptComposition();
  showView('receipt-detail');
}
function renderReceiptComposition(){
  const isDraft = currentReceiptRef && currentReceiptRef.status === 'draft';
  const cols = tableColumns.receiptComposition;
  const order = tableColumnState.receiptComposition;
  const hidden = tableColumnHidden.receiptComposition;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('receiptCompositionHeadRow').innerHTML =
    `<th id="receiptCheckboxHead" style="display:${isDraft?'':'none'}; width:36px;"><input type="checkbox" onclick="toggleAllReceiptLineChecks(this)"></th>` +
    visibleCols.map(c=>`<th data-col="${c.id}"${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('receiptDetailRows').innerHTML = currentReceiptComposition.map((l,i)=>{
    const cellsHtml = visibleCols.map(c=>{
      const cls = [c.num?'num':'', c.wrap?'wrap':''].filter(Boolean).join(' ');
      return `<td data-col="${c.id}"${cls?` class="${cls}"`:''}>${c.render(l)}</td>`;
    }).join('');
    return `<tr style="cursor:pointer;" onclick="${isDraft ? `openLineEditModal(${i})` : `openReceiptLineDetail(${i})`}">
      ${isDraft ? `<td><input type="checkbox" data-line-idx="${i}" ${l.checked?'checked':''} onclick="event.stopPropagation(); onReceiptLineCheck(this)"></td>` : ``}
      ${cellsHtml}
    </tr>`;
  }).join('');
  recalcReceiptTotals();
  setTimeout(()=>updateTopScrollWidth('receiptComposition'), 0);
}
function mockReceiptLineBatches(line){
  const totalAccepted = Number(line.accepted) || 0;
  if(totalAccepted <= 0) return [];
  const expVal = (line.exp && line.exp !== '—') ? line.exp : '31.12.2026';
  const part1 = Math.ceil(totalAccepted * 0.7);
  const part2 = totalAccepted - part1;
  const rows = [
    {q:'Годен', batch:'П-' + (1000 + Math.floor(Math.random()*900)), series:'С-' + (100 + Math.floor(Math.random()*90)), exp:expVal, stock:part1},
  ];
  if(part2 > 0){
    rows.push({q:'Годен', batch:'П-' + (1000 + Math.floor(Math.random()*900)), series:'С-' + (100 + Math.floor(Math.random()*90)), exp:expVal, stock:part2});
  }
  return rows;
}
let receiptLineDetailIndex = null;
function openReceiptLineDetail(idx){
  const l = currentReceiptComposition[idx];
  if(!l) return;
  receiptLineDetailIndex = idx;
  document.getElementById('receiptLineDetailTitle').innerHTML = `
    <div style="font-size:13px; color:var(--muted); font-weight:400; margin-bottom:4px;">Поступление ${(currentReceiptRef.erpNo || currentReceiptRef.no)}</div>
    <div>${l.name}</div>
    <div style="color:var(--muted); margin-top:2px;">${l.code}</div>
  `;
  document.getElementById('receiptLineDetailHead').innerHTML = `
    <div class="kv"><label>${T('col.receiptComposition.expected')}</label><div>${l.expected} ${l.unit || ''}</div></div>
    <div class="kv"><label>${T('col.receiptComposition.accepted')}</label><div>${l.accepted} ${l.unit || ''}</div></div>
    <div class="kv"><label>${T('col.stock.defect')}</label><div>${l.defect} ${l.unit || ''}</div></div>
    ${l.lineComment ? `<div class="kv"><label>${T('sku.label.comment')}</label><div>${l.lineComment}</div></div>` : ''}
  `;
  if(!l.batches){
    l.batches = mockReceiptLineBatches(l);
  }
  renderReceiptLineDetailTable();
  showView('receipt-line-detail');
}
function renderReceiptLineDetailTable(){
  const l = currentReceiptComposition[receiptLineDetailIndex];
  if(!l) return;
  const batches = l.batches || [];
  const cols = tableColumns.receiptLineDetail;
  const order = tableColumnState.receiptLineDetail;
  const hidden = tableColumnHidden.receiptLineDetail;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('receiptLineDetailHeadRow').innerHTML = visibleCols.map(c=>`<th${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('receiptLineDetailRows').innerHTML = batches.length ? batches.map(b=>`
    <tr>${visibleCols.map(c=>`<td${c.num?' class="num"':''}>${c.render(b)}</td>`).join('')}</tr>
  `).join('') : `<tr><td colspan="${visibleCols.length}" style="color:var(--muted);">${T('admin.text.no_data')}</td></tr>`;
}
function closeReceiptLineDetail(){
  receiptLineDetailIndex = null;
  showView('receipt-detail');
}
function recalcReceiptTotals(){
  const lineCountEl = document.getElementById('rf_lineCount');
  const totalPiecesEl = document.getElementById('rf_totalPieces');
  const lineCount = currentReceiptComposition.length;
  const totalPieces = currentReceiptComposition.reduce((sum,l)=>sum+(Number(l.expected)||0), 0);
  if(lineCountEl) lineCountEl.value = lineCount;
  if(totalPiecesEl) totalPiecesEl.value = totalPieces;
}
function onReceiptLineCheck(cb){
  const idx = Number(cb.dataset.lineIdx);
  currentReceiptComposition[idx].checked = cb.checked;
  const anyChecked = currentReceiptComposition.some(l=>l.checked);
  document.getElementById('receiptDeleteRowsBtn').disabled = !anyChecked;
}
function toggleAllReceiptLineChecks(headerCb){
  currentReceiptComposition.forEach(l=>l.checked = headerCb.checked);
  renderReceiptComposition();
  document.getElementById('receiptDeleteRowsBtn').disabled = !headerCb.checked;
}
function deleteSelectedReceiptLines(){
  const before = currentReceiptComposition.length;
  currentReceiptComposition = currentReceiptComposition.filter(l=>!l.checked);
  const removed = before - currentReceiptComposition.length;
  if(currentReceiptRef) currentReceiptRef.lines = currentReceiptComposition.map(({checked, ...l})=>l);
  document.getElementById('receiptDeleteRowsBtn').disabled = true;
  renderReceiptComposition();
  notify(`Удалено строк: ${removed}`);
}

let lineEditIndex = null;
let lineEditMode = 'edit';
let lineEditSelectedProduct = null;
function fcPackagingOptionsFor(code){
  return PRODUCT_PACKAGING.filter(x=>x.productCode === code && x.depositorId === currentDepositorId);
}
function fcSetupLineEditPackaging(prefix, product, qtyElId, packagingCode, packageCount){
  const wrap = document.getElementById(prefix + 'PackagingWrap');
  const countWrap = document.getElementById(prefix + 'PackageCountWrap');
  const select = document.getElementById(prefix + 'Packaging');
  const countInput = document.getElementById(prefix + 'PackageCount');
  const qtyInput = document.getElementById(qtyElId);
  const options = product ? fcPackagingOptionsFor(product.code) : [];
  if(!options.length){
    wrap.style.display = 'none';
    countWrap.style.display = 'none';
    qtyInput.readOnly = false;
    return;
  }
  wrap.style.display = '';
  select.innerHTML = `<option value="">Без упаковки</option>` + options.map(o=>`<option value="${o.packagingCode}" ${o.packagingCode===packagingCode?'selected':''}>${fcPackagingName(o, product.unit)}</option>`).join('');
  const hasSelection = !!packagingCode && options.some(o=>o.packagingCode===packagingCode);
  countWrap.style.display = hasSelection ? '' : 'none';
  countInput.value = hasSelection ? (packageCount ?? '') : '';
  qtyInput.readOnly = hasSelection;
}
function fcApplyPackagingSelection(prefix, qtyElId){
  const select = document.getElementById(prefix + 'Packaging');
  const countWrap = document.getElementById(prefix + 'PackageCountWrap');
  const countInput = document.getElementById(prefix + 'PackageCount');
  const qtyInput = document.getElementById(qtyElId);
  const code = select.value;
  if(!code){
    countWrap.style.display = 'none';
    countInput.value = '';
    qtyInput.readOnly = false;
    return;
  }
  countWrap.style.display = '';
  qtyInput.readOnly = true;
  fcRecalcPackagingQty(prefix, qtyElId);
}
function fcRecalcPackagingQty(prefix, qtyElId){
  const select = document.getElementById(prefix + 'Packaging');
  const countInput = document.getElementById(prefix + 'PackageCount');
  const qtyInput = document.getElementById(qtyElId);
  const code = select.value;
  if(!code) return;
  const pkg = PRODUCT_PACKAGING.find(x=>x.packagingCode === code);
  const count = Number(countInput.value) || 0;
  qtyInput.value = pkg ? count * pkg.coefficient : '';
}
function onLineEditPackagingChange(){ fcApplyPackagingSelection('lineEdit', 'lineEditExpected'); }
function onLineEditPackageCountChange(){ fcRecalcPackagingQty('lineEdit', 'lineEditExpected'); }
function onOrderLineEditPackagingChange(){ fcApplyPackagingSelection('orderLineEdit', 'orderLineEditQty'); }
function onOrderLineEditPackageCountChange(){ fcRecalcPackagingQty('orderLineEdit', 'orderLineEditQty'); }
function onTransferLineEditPackagingChange(){ fcApplyPackagingSelection('transferLineEdit', 'transferLineEditQty'); }
function onTransferLineEditPackageCountChange(){ fcRecalcPackagingQty('transferLineEdit', 'transferLineEditQty'); }
function fcSetupLineEditPricing(prefix, product, qtyElId, price, currency){
  const currencyEl = document.getElementById(prefix + 'Currency');
  const priceEl = document.getElementById(prefix + 'Price');
  if(!currencyEl || !priceEl) return;
  if(!product){
    currencyEl.textContent = '—';
    priceEl.value = '';
    fcRecalcLineSum(prefix, qtyElId);
    return;
  }
  const f = getProductFullInfo(product);
  currencyEl.textContent = currency || f.currency || '—';
  priceEl.value = (price !== null && price !== undefined) ? price : (f.cost ?? '');
  fcRecalcLineSum(prefix, qtyElId);
}
function fcRecalcLineSum(prefix, qtyElId){
  const qtyEl = document.getElementById(qtyElId);
  const priceEl = document.getElementById(prefix + 'Price');
  const sumEl = document.getElementById(prefix + 'Sum');
  if(!qtyEl || !priceEl || !sumEl) return;
  const qty = Number(qtyEl.value) || 0;
  const price = Number(priceEl.value) || 0;
  sumEl.textContent = (qty && price) ? (qty * price).toFixed(2) : '—';
}
function onLineEditPriceChange(){ fcRecalcLineSum('lineEdit', 'lineEditExpected'); }
function onOrderLineEditPriceChange(){ fcRecalcLineSum('orderLineEdit', 'orderLineEditQty'); }
function fillLineEditProductInfo(p){
  document.getElementById('lineEditCode').textContent = p ? p.code : '—';
  document.getElementById('lineEditName').textContent = p ? p.name : '—';
  document.getElementById('lineEditUnit').textContent = p ? p.unit : '—';
  document.getElementById('lineEditStockTotal').textContent = p ? `${p.stock} ${p.unit}` : '—';
  document.getElementById('lineEditStockAvail').textContent = p ? `${p.stock - p.reserved} ${p.unit}` : '—';
  document.getElementById('lineEditStockIncoming').textContent = p ? `${getProductIncomingQty(p)} ${p.unit}` : '—';
  document.getElementById('lineEditExpectedLabel').textContent = p && p.unit ? `Ожидаемое количество, ${p.unit}` : T('common.label.expected_quantity');
  fcSetupLineEditPackaging('lineEdit', p, 'lineEditExpected', p ? (p.defaultOrderPackaging || null) : null, null);
  fcSetupLineEditPricing('lineEdit', p, 'lineEditExpected', null, null);
}
function openLineEditModal(idx){
  if(!currentReceiptRef || currentReceiptRef.status !== 'draft') return;
  lineEditMode = 'edit';
  lineEditIndex = idx;
  const line = currentReceiptComposition[idx];
  lineEditSelectedProduct = products.find(p=>p.code === line.code) || null;
  document.getElementById('lineEditOverlay').classList.add('active');
  document.getElementById('lineEditTitle').textContent = T('common.title.edit_line');
  document.getElementById('lineEditSearchWrap').style.display = 'none';
  document.getElementById('lineEditProductSearch').value = '';
  document.getElementById('lineEditProductResults').style.display = 'none';
  fillLineEditProductInfo(lineEditSelectedProduct);
  document.getElementById('lineEditExpected').value = line.expected ?? '';
  document.getElementById('lineEditExpected').classList.remove('kv-input-invalid');
  fcSetupLineEditPackaging('lineEdit', lineEditSelectedProduct, 'lineEditExpected', line.packagingCode || null, line.packageCount || null);
  fcSetupLineEditPricing('lineEdit', lineEditSelectedProduct, 'lineEditExpected', line.price ?? null, line.currency || null);
  document.getElementById('lineEditComment').value = line.lineComment || '';
}
function openLineAddModal(){
  if(!currentReceiptRef || currentReceiptRef.status !== 'draft') return;
  lineEditMode = 'add';
  lineEditIndex = null;
  lineEditSelectedProduct = null;
  document.getElementById('lineEditOverlay').classList.add('active');
  document.getElementById('lineEditTitle').textContent = 'Добавление строки';
  document.getElementById('lineEditSearchWrap').style.display = '';
  document.getElementById('lineEditProductSearch').value = '';
  document.getElementById('lineEditProductResults').style.display = 'none';
  fillLineEditProductInfo(null);
  document.getElementById('lineEditExpected').value = '';
  document.getElementById('lineEditExpected').classList.remove('kv-input-invalid');
  document.getElementById('lineEditComment').value = '';
}
function closeLineEditModal(){
  document.getElementById('lineEditOverlay').classList.remove('active');
  lineEditIndex = null;
  lineEditSelectedProduct = null;
}
function searchLineEditProduct(query){
  const resultsBox = document.getElementById('lineEditProductResults');
  const q = query.trim().toLowerCase();
  if(!q){ resultsBox.style.display = 'none'; resultsBox.innerHTML = ''; return; }
  const matches = products.filter(p =>
    p.code.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)
  ).slice(0, 8);
  if(!matches.length){
    resultsBox.innerHTML = `<div style="padding:10px 12px; font-size:13px; color:var(--muted);">${T('stock.text.nothing_found')}</div>`;
    resultsBox.style.display = '';
    return;
  }
  resultsBox.innerHTML = matches.map(p=>`
    <div style="padding:9px 12px; font-size:13px; cursor:pointer; border-bottom:1px solid var(--line);"
         onmousedown="selectLineEditProduct('${p.code}')">
      <strong>${p.code}</strong> — ${p.name} <span style="color:var(--muted);">(${p.unit})</span>
    </div>
  `).join('');
  resultsBox.style.display = '';
}
function selectLineEditProduct(code){
  const p = products.find(x=>x.code === code);
  if(!p) return;
  lineEditSelectedProduct = p;
  fillLineEditProductInfo(p);
  document.getElementById('lineEditProductSearch').value = `${p.code} — ${p.name}`;
  document.getElementById('lineEditProductResults').style.display = 'none';
  document.getElementById('lineEditProductResults').innerHTML = '';
}
function clearLineEditProductSearch(){
  lineEditSelectedProduct = null;
  fillLineEditProductInfo(null);
  document.getElementById('lineEditProductSearch').value = '';
  document.getElementById('lineEditProductResults').style.display = 'none';
  document.getElementById('lineEditProductResults').innerHTML = '';
  document.getElementById('lineEditProductSearch').focus();
}
function saveLineEditModal(){
  const expectedEl = document.getElementById('lineEditExpected');
  if(expectedEl.value === '' || isNaN(expectedEl.value) || Number(expectedEl.value) < 0){
    expectedEl.classList.add('kv-input-invalid');
    notify(T('common.message.enter_a_valid_expected_quantity'));
    return;
  }
  if(!lineEditSelectedProduct){
    notify(T('common.message.select_an_item_from_the_list'));
    return;
  }
  const lineComment = document.getElementById('lineEditComment').value.trim();
  const packagingCode = document.getElementById('lineEditPackaging').value || null;
  const packageCount = packagingCode ? (Number(document.getElementById('lineEditPackageCount').value) || 0) : null;
  const price = document.getElementById('lineEditPrice').value !== '' ? Number(document.getElementById('lineEditPrice').value) : null;
  const currency = document.getElementById('lineEditCurrency').textContent !== '—' ? document.getElementById('lineEditCurrency').textContent : null;
  const finishReceiptSave = ()=>{
    if(currentReceiptRef) currentReceiptRef.lines = currentReceiptComposition.map(({checked, ...l})=>l);
    closeLineEditModal();
    renderReceiptComposition();
    notify(lineEditMode === 'add' ? 'Строка добавлена' : 'Строка обновлена');
  };
  if(lineEditMode === 'add'){
    const dupIdx = currentReceiptComposition.findIndex(l=>l.code === lineEditSelectedProduct.code);
    if(dupIdx > -1){
      const existing = currentReceiptComposition[dupIdx];
      openDuplicateLineDialog({
        line: existing, qtyField: 'expected', newQty: Number(expectedEl.value),
        productName: lineEditSelectedProduct.name, existingQty: existing.expected, unit: lineEditSelectedProduct.unit,
        finish: finishReceiptSave,
      });
      return;
    }
    currentReceiptComposition.push({
      code: lineEditSelectedProduct.code,
      name: lineEditSelectedProduct.name,
      unit: lineEditSelectedProduct.unit,
      expected: Number(expectedEl.value),
      expectedWms: 0,
      accepted: '', defect: '', series: '', batch: '',
      exp: '—', checked: false, lineComment, packagingCode, packageCount, price, currency,
    });
  }else{
    const line = currentReceiptComposition[lineEditIndex];
    line.code = lineEditSelectedProduct.code;
    line.name = lineEditSelectedProduct.name;
    line.unit = lineEditSelectedProduct.unit;
    line.expected = Number(expectedEl.value);
    line.lineComment = lineComment;
    line.packagingCode = packagingCode;
    line.packageCount = packageCount;
    line.price = price;
    line.currency = currency;
  }
  finishReceiptSave();
}

let dupLineCtx = null;
function openDuplicateLineDialog(ctx){
  dupLineCtx = ctx;
  document.getElementById('duplicateLineMessage').textContent =
    `Товар "${ctx.productName}" ранее уже был добавлен в документ в количестве ${ctx.existingQty} ${ctx.unit}`;
  document.getElementById('duplicateLineOverlay').classList.add('active');
}
function resolveDuplicateLine(action){
  if(!dupLineCtx) return;
  const ctx = dupLineCtx;
  document.getElementById('duplicateLineOverlay').classList.remove('active');
  dupLineCtx = null;
  if(action === 'cancel') return;
  if(action === 'add'){
    ctx.line[ctx.qtyField] = (Number(ctx.line[ctx.qtyField]) || 0) + ctx.newQty;
  }else if(action === 'replace'){
    ctx.line[ctx.qtyField] = ctx.newQty;
  }
  ctx.finish();
}
function getInputValue(id){
  const el = document.getElementById(id);
  return el ? el.value : undefined;
}
function saveReceiptDraft(){
  if(!currentReceiptRef || currentReceiptRef.status !== 'draft') return;
  const invalid = document.querySelectorAll('#receiptDetailHead .kv-input-invalid, #receiptDetailTransport .kv-input-invalid');
  if(invalid.length) return;
  const r = currentReceiptRef;
  if(!r) return;

  const textFields = {
    rf_erpNo:'erpNo', rf_supplier:'supplier', rf_docType:'docType',
    rf_accompanyingDocNumber:'accompanyingDocNumber', rf_invoiceNumber:'invoiceNumber', rf_gtd:'gtd', rf_comment:'comment',
    rf_trip:'trip', rf_carrier:'carrier', rf_transportType:'transportType', rf_transportBrand:'transportBrand',
    rf_carNumber:'carNumber', rf_trailerNumber:'trailerNumber', rf_techPassportNumber:'techPassportNumber',
    rf_containerNumber:'containerNumber', rf_sealNumber:'sealNumber', rf_driverName:'driverName',
    rf_driverPhone:'driverPhone', rf_driverIdNumber:'driverIdNumber', rf_driverInn:'driverInn',
    rf_customField1:'customField1',
    rf_customField2:'customField2',
    rf_customField3:'customField3',
    rf_customField4:'customField4',
    rf_customField5:'customField5',
    rf_customField6:'customField6',
    rf_customField7:'customField7',
    rf_customField8:'customField8',
    rf_customField9:'customField9',
    rf_customField10:'customField10',
  };
  Object.entries(textFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v !== undefined) r[field] = v;
  });

  const numberFields = { rf_weight:'weight', rf_volume:'volume', rf_palletCount:'palletCount', rf_cargoPlaces:'cargoPlaces' };
  Object.entries(numberFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v !== undefined && v !== '' && !isNaN(v)) r[field] = Number(v);
  });

  const dateFields = { rf_date:'date', rf_eta:'eta' };
  Object.entries(dateFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v){ r[field] = fromISODate(v); }
  });

  const warehouseName = getInputValue('rf_warehouse');
  if(warehouseName){
    const found = Object.values(WAREHOUSES).find(w=>w.name === warehouseName);
    if(found) r.warehouse = found.id;
  }

  // Сохраняем состав документа (без учёта отметок чекбоксов)
  r.lines = currentReceiptComposition.map(({checked, ...l})=>l);

  // Авторасчёт по составу документа
  r.lineCount = currentReceiptComposition.length;
  r.totalPieces = currentReceiptComposition.reduce((sum,l)=>sum+(Number(l.expected)||0), 0);
  r.items = r.lineCount;

  renderReceipts();
}
function confirmReceiptDraft(){
  const invalid = document.querySelectorAll('#receiptDetailHead .kv-input-invalid');
  if(invalid.length){
    notify(T('common.message.check_the_numeric_fields_before_co'));
    return;
  }
  if(currentReceiptRef){
    currentReceiptRef.status = 'warn';
    currentReceiptRef.statusLabel = 'Ожидает приёмки';
    if(!currentReceiptRef.wmsNumber) currentReceiptRef.wmsNumber = docWmsNumberFromRecord(currentReceiptRef);
    notify(T('common.message.receipt') + currentReceiptRef.no + ' подтверждено');
    openReceipt(currentReceiptRef.no);
    renderReceipts();
  }
}
function loginTimeHash4(login, timeMs){
  const str = String(login) + '_' + String(timeMs);
  let hash = 0;
  for(let i=0; i<str.length; i++){
    hash = (hash * 31 + str.charCodeAt(i)) >>> 0;
  }
  return String(hash % 10000).padStart(4,'0');
}
function generateDocId(prefix){
  const now = new Date();
  const pad = n => String(n).padStart(2,'0');
  const dateStr = `${pad(now.getFullYear()%100)}${pad(now.getMonth()+1)}${pad(now.getDate())}`;
  const login = (currentUser && currentUser.login) || 'user';
  const uniq = loginTimeHash4(login, now.getTime());
  return `${prefix}${dateStr}${uniq}`;
}
function createNewReceiptDraft(){
  const no = generateDocId('WP');
  const wh = (currentWarehouseFilter && currentWarehouseFilter !== 'all') ? currentWarehouseFilter
    : (DEPOSITOR_WAREHOUSES[currentDepositorId] && DEPOSITOR_WAREHOUSES[currentDepositorId][0]) || Object.keys(WAREHOUSES)[0];
  const today = new Date();
  const todayStr = String(today.getDate()).padStart(2,'0') + '.' + String(today.getMonth()+1).padStart(2,'0') + '.' + today.getFullYear();
  const newReceipt = {
    warehouse: wh, no, date: todayStr, eta:'—', supplier:'', items:0, volume:'',
    transport:'', status:'draft', statusLabel:'Черновик',
    erpNo:'', wmsNumber:'', docType:'', comment:'', accompanyingDocNumber:'', gtd:'', author:(currentUser && currentUser.full_name) || '',
    carrier:'', transportType:'', transportBrand:'', carNumber:'', trailerNumber:'',
    techPassportNumber:'', containerNumber:'', sealNumber:'', driverName:'', driverPhone:'',
    driverIdNumber:'', driverInn:'', weight:'', lineCount:0, palletCount:'', cargoPlaces:'', totalPieces:0,
    lines: [],
  };
  receipts.unshift(newReceipt);
  renderReceipts();
  openReceipt(no);
  notify(T('common.message.new_receipt_draft_created') + no);
}
function openDeleteReceiptDraftConfirm(){
  if(!currentReceiptRef || currentReceiptRef.status !== 'draft') return;
  document.getElementById('deleteReceiptConfirmOverlay').classList.add('active');
}
function closeDeleteReceiptDraftConfirm(){
  document.getElementById('deleteReceiptConfirmOverlay').classList.remove('active');
}
function deleteReceiptDraft(){
  if(!currentReceiptRef) return;
  const no = currentReceiptRef.no;
  const idx = receipts.indexOf(currentReceiptRef);
  if(idx > -1) receipts.splice(idx, 1);
  closeDeleteReceiptDraftConfirm();
  currentReceiptRef = null;
  renderReceipts();
  showTab('receipts');
  notify(T('receipts.option.draft') + no + ' удалён');
}
let currentTransferComposition = [];
let currentTransferRef = null;
function openTransfer(no){
  const t = (transfers || []).find(x=>x.no===no);
  if(!t) return;
  currentTransferRef = t;
  const isDraft = t.status === 'draft';
  document.getElementById('transferDraftActions').style.display = isDraft ? '' : 'none';
  document.getElementById('transferDocMeta').innerHTML = `<div>${T('common.label.document_id')}<strong style="color:var(--ink);">${t.no}</strong></div><div>${T('common.label.created_by')}<strong style="color:var(--ink);">${t.author || '—'}</strong></div><div>${T('common.label.contract_number')}<strong style="color:var(--ink);">${getCurrentDepositorContractNumber()}</strong></div>`;
  document.getElementById('transferDetailBanner').innerHTML = '';
  document.getElementById('transferDetailTitle').innerHTML = `${T('common.message.transfer')} ${t.erpNo || t.no} ${badge(t.status, t.statusLabel)}`;
  fcRenderDocTabs('transfers', t, isDraft, fcTransferSpecialFieldHtml, 'transferDetailTabButtons', 'transferDetailTabContent');
  if(!t.lines){
    const pool = products.filter(p=>p.warehouse===t.fromWarehouse);
    const source = (pool.length ? pool : products).slice(0, t.items);
    t.lines = source.map(p=>{
      const qty = isDraft ? "" : 5+Math.floor(Math.random()*40);
      return {code:p.code, name:p.name, unit:p.unit, qty, qtyWms: isDraft ? 0 : qty, series:isDraft ? "" : (p.series || "—"), batch:isDraft ? "" : p.batch, exp:p.exp};
    });
  }
  currentTransferComposition = t.lines.map(l=>({...l, checked:false}));
  document.getElementById('transferCheckboxHead').style.display = isDraft ? '' : 'none';
  document.getElementById('transferAddRowBtn').style.display = isDraft ? '' : 'none';
  document.getElementById('transferDeleteRowsBtn').style.display = isDraft ? '' : 'none';
  renderTransferComposition();
  showView('transfer-detail');
}
function renderTransferComposition(){
  const isDraft = currentTransferRef && currentTransferRef.status === 'draft';
  const cols = tableColumns.transferComposition;
  const order = tableColumnState.transferComposition;
  const hidden = tableColumnHidden.transferComposition;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('transferCompositionHeadRow').innerHTML =
    `<th id="transferCheckboxHead" style="display:${isDraft?'':'none'}; width:36px;"><input type="checkbox" onclick="toggleAllTransferLineChecks(this)"></th>` +
    visibleCols.map(c=>`<th data-col="${c.id}"${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('transferDetailRows').innerHTML = currentTransferComposition.map((l,i)=>{
    const cellsHtml = visibleCols.map(c=>{
      const cls = [c.num?'num':'', c.wrap?'wrap':''].filter(Boolean).join(' ');
      return `<td data-col="${c.id}"${cls?` class="${cls}"`:''}>${c.render(l)}</td>`;
    }).join('');
    return `<tr style="cursor:pointer;" onclick="${isDraft ? `openTransferLineEditModal(${i})` : `openTransferLineDetail(${i})`}">
      ${isDraft ? `<td><input type="checkbox" data-line-idx="${i}" ${l.checked?'checked':''} onclick="event.stopPropagation(); onTransferLineCheck(this)"></td>` : ``}
      ${cellsHtml}
    </tr>`;
  }).join('');
  recalcTransferTotals();
  setTimeout(()=>updateTopScrollWidth('transferComposition'), 0);
}
function mockTransferLineBatches(line){
  const totalQty = Number(line.qty) || 0;
  if(totalQty <= 0) return [];
  const expVal = (line.exp && line.exp !== '—') ? line.exp : '31.12.2026';
  const part1 = Math.ceil(totalQty * 0.7);
  const part2 = totalQty - part1;
  const rows = [
    {q:'Годен', batch:'П-' + (1000 + Math.floor(Math.random()*900)), series:'С-' + (100 + Math.floor(Math.random()*90)), exp:expVal, stock:part1},
  ];
  if(part2 > 0){
    rows.push({q:'Годен', batch:'П-' + (1000 + Math.floor(Math.random()*900)), series:'С-' + (100 + Math.floor(Math.random()*90)), exp:expVal, stock:part2});
  }
  return rows;
}
let transferLineDetailIndex = null;
function openTransferLineDetail(idx){
  const l = currentTransferComposition[idx];
  if(!l) return;
  transferLineDetailIndex = idx;
  document.getElementById('transferLineDetailTitle').innerHTML = `
    <div style="font-size:13px; color:var(--muted); font-weight:400; margin-bottom:4px;">Перемещение ${(currentTransferRef.erpNo || currentTransferRef.no)}</div>
    <div>${l.name}</div>
    <div style="color:var(--muted); margin-top:2px;">${l.code}</div>
  `;
  document.getElementById('transferLineDetailHead').innerHTML = `
    <div class="kv"><label>${T('col.transferComposition.qty')}</label><div>${l.qty} ${l.unit || ''}</div></div>
    ${l.lineComment ? `<div class="kv"><label>${T('sku.label.comment')}</label><div>${l.lineComment}</div></div>` : ''}
  `;
  if(!l.batches){
    l.batches = mockTransferLineBatches(l);
  }
  renderTransferLineDetailTable();
  showView('transfer-line-detail');
}
function renderTransferLineDetailTable(){
  const l = currentTransferComposition[transferLineDetailIndex];
  if(!l) return;
  const batches = l.batches || [];
  const cols = tableColumns.transferLineDetail;
  const order = tableColumnState.transferLineDetail;
  const hidden = tableColumnHidden.transferLineDetail;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('transferLineDetailHeadRow').innerHTML = visibleCols.map(c=>`<th${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('transferLineDetailRows').innerHTML = batches.length ? batches.map(b=>`
    <tr>${visibleCols.map(c=>`<td${c.num?' class="num"':''}>${c.render(b)}</td>`).join('')}</tr>
  `).join('') : `<tr><td colspan="${visibleCols.length}" style="color:var(--muted);">${T('admin.text.no_data')}</td></tr>`;
}
function closeTransferLineDetail(){
  transferLineDetailIndex = null;
  showView('transfer-detail');
}
function recalcTransferTotals(){
  const lineCountEl = document.getElementById('tf_lineCount');
  const totalPiecesEl = document.getElementById('tf_totalPieces');
  const lineCount = currentTransferComposition.length;
  const totalPieces = currentTransferComposition.reduce((sum,l)=>sum+(Number(l.qty)||0), 0);
  if(lineCountEl) lineCountEl.value = lineCount;
  if(totalPiecesEl) totalPiecesEl.value = totalPieces;
}
function onTransferLineCheck(cb){
  const idx = Number(cb.dataset.lineIdx);
  currentTransferComposition[idx].checked = cb.checked;
  const anyChecked = currentTransferComposition.some(l=>l.checked);
  document.getElementById('transferDeleteRowsBtn').disabled = !anyChecked;
}
function toggleAllTransferLineChecks(headerCb){
  currentTransferComposition.forEach(l=>l.checked = headerCb.checked);
  renderTransferComposition();
  document.getElementById('transferDeleteRowsBtn').disabled = !headerCb.checked;
}
function deleteSelectedTransferLines(){
  const before = currentTransferComposition.length;
  currentTransferComposition = currentTransferComposition.filter(l=>!l.checked);
  const removed = before - currentTransferComposition.length;
  if(currentTransferRef) currentTransferRef.lines = currentTransferComposition.map(({checked, ...l})=>l);
  document.getElementById('transferDeleteRowsBtn').disabled = true;
  renderTransferComposition();
  notify(`Удалено строк: ${removed}`);
}

let transferLineEditIndex = null;
let transferLineEditMode = 'edit';
let transferLineEditSelectedProduct = null;
function fillTransferLineEditProductInfo(p){
  document.getElementById('transferLineEditCode').textContent = p ? p.code : '—';
  document.getElementById('transferLineEditName').textContent = p ? p.name : '—';
  document.getElementById('transferLineEditUnit').textContent = p ? p.unit : '—';
  document.getElementById('transferLineEditStockTotal').textContent = p ? `${p.stock} ${p.unit}` : '—';
  document.getElementById('transferLineEditStockAvail').textContent = p ? `${p.stock - p.reserved} ${p.unit}` : '—';
  document.getElementById('transferLineEditQtyLabel').textContent = p && p.unit ? `Количество, ${p.unit}` : T('common.label.quantity');
  fcSetupLineEditPackaging('transferLineEdit', p, 'transferLineEditQty', p ? (p.defaultOrderPackaging || null) : null, null);
}
function openTransferLineEditModal(idx){
  if(!currentTransferRef || currentTransferRef.status !== 'draft') return;
  transferLineEditMode = 'edit';
  transferLineEditIndex = idx;
  const line = currentTransferComposition[idx];
  transferLineEditSelectedProduct = products.find(p=>p.code === line.code) || null;
  document.getElementById('transferLineEditOverlay').classList.add('active');
  document.getElementById('transferLineEditTitle').textContent = T('common.title.edit_line');
  document.getElementById('transferLineEditSearchWrap').style.display = 'none';
  document.getElementById('transferLineEditProductSearch').value = '';
  document.getElementById('transferLineEditProductResults').style.display = 'none';
  fillTransferLineEditProductInfo(transferLineEditSelectedProduct);
  document.getElementById('transferLineEditQty').value = line.qty ?? '';
  document.getElementById('transferLineEditQty').classList.remove('kv-input-invalid');
  fcSetupLineEditPackaging('transferLineEdit', transferLineEditSelectedProduct, 'transferLineEditQty', line.packagingCode || null, line.packageCount || null);
  document.getElementById('transferLineEditComment').value = line.lineComment || '';
}
function openTransferLineAddModal(){
  if(!currentTransferRef || currentTransferRef.status !== 'draft') return;
  transferLineEditMode = 'add';
  transferLineEditIndex = null;
  transferLineEditSelectedProduct = null;
  document.getElementById('transferLineEditOverlay').classList.add('active');
  document.getElementById('transferLineEditTitle').textContent = 'Добавление строки';
  document.getElementById('transferLineEditSearchWrap').style.display = '';
  document.getElementById('transferLineEditProductSearch').value = '';
  document.getElementById('transferLineEditProductResults').style.display = 'none';
  fillTransferLineEditProductInfo(null);
  document.getElementById('transferLineEditQty').value = '';
  document.getElementById('transferLineEditQty').classList.remove('kv-input-invalid');
  document.getElementById('transferLineEditComment').value = '';
}
function closeTransferLineEditModal(){
  document.getElementById('transferLineEditOverlay').classList.remove('active');
  transferLineEditIndex = null;
  transferLineEditSelectedProduct = null;
}
function searchTransferLineProduct(query){
  const resultsBox = document.getElementById('transferLineEditProductResults');
  const q = query.trim().toLowerCase();
  if(!q){ resultsBox.style.display = 'none'; resultsBox.innerHTML = ''; return; }
  const matches = products.filter(p =>
    p.code.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)
  ).slice(0, 8);
  if(!matches.length){
    resultsBox.innerHTML = `<div style="padding:10px 12px; font-size:13px; color:var(--muted);">${T('stock.text.nothing_found')}</div>`;
    resultsBox.style.display = '';
    return;
  }
  resultsBox.innerHTML = matches.map(p=>`
    <div style="padding:9px 12px; font-size:13px; cursor:pointer; border-bottom:1px solid var(--line);"
         onmousedown="selectTransferLineProduct('${p.code}')">
      <strong>${p.code}</strong> — ${p.name} <span style="color:var(--muted);">(${p.unit})</span>
    </div>
  `).join('');
  resultsBox.style.display = '';
}
function selectTransferLineProduct(code){
  const p = products.find(x=>x.code === code);
  if(!p) return;
  transferLineEditSelectedProduct = p;
  fillTransferLineEditProductInfo(p);
  document.getElementById('transferLineEditProductSearch').value = `${p.code} — ${p.name}`;
  document.getElementById('transferLineEditProductResults').style.display = 'none';
  document.getElementById('transferLineEditProductResults').innerHTML = '';
}
function clearTransferLineProductSearch(){
  transferLineEditSelectedProduct = null;
  fillTransferLineEditProductInfo(null);
  document.getElementById('transferLineEditProductSearch').value = '';
  document.getElementById('transferLineEditProductResults').style.display = 'none';
  document.getElementById('transferLineEditProductResults').innerHTML = '';
  document.getElementById('transferLineEditProductSearch').focus();
}
function saveTransferLineEditModal(){
  const qtyEl = document.getElementById('transferLineEditQty');
  if(qtyEl.value === '' || isNaN(qtyEl.value) || Number(qtyEl.value) < 0){
    qtyEl.classList.add('kv-input-invalid');
    notify(T('common.message.enter_a_valid_quantity'));
    return;
  }
  if(!transferLineEditSelectedProduct){
    notify(T('common.message.select_an_item_from_the_list'));
    return;
  }
  const lineComment = document.getElementById('transferLineEditComment').value.trim();
  const packagingCode = document.getElementById('transferLineEditPackaging').value || null;
  const packageCount = packagingCode ? (Number(document.getElementById('transferLineEditPackageCount').value) || 0) : null;
  const finishTransferSave = ()=>{
    if(currentTransferRef) currentTransferRef.lines = currentTransferComposition.map(({checked, ...l})=>l);
    closeTransferLineEditModal();
    renderTransferComposition();
    notify(transferLineEditMode === 'add' ? 'Строка добавлена' : 'Строка обновлена');
  };
  if(transferLineEditMode === 'add'){
    const dupIdx = currentTransferComposition.findIndex(l=>l.code === transferLineEditSelectedProduct.code);
    if(dupIdx > -1){
      const existing = currentTransferComposition[dupIdx];
      openDuplicateLineDialog({
        line: existing, qtyField: 'qty', newQty: Number(qtyEl.value),
        productName: transferLineEditSelectedProduct.name, existingQty: existing.qty, unit: transferLineEditSelectedProduct.unit,
        finish: finishTransferSave,
      });
      return;
    }
    currentTransferComposition.push({
      code: transferLineEditSelectedProduct.code,
      name: transferLineEditSelectedProduct.name,
      unit: transferLineEditSelectedProduct.unit,
      qty: Number(qtyEl.value),
      qtyWms: 0,
      series: '', batch: '',
      exp: '—', checked: false, lineComment, packagingCode, packageCount,
    });
  }else{
    const line = currentTransferComposition[transferLineEditIndex];
    line.code = transferLineEditSelectedProduct.code;
    line.name = transferLineEditSelectedProduct.name;
    line.unit = transferLineEditSelectedProduct.unit;
    line.qty = Number(qtyEl.value);
    line.packagingCode = packagingCode;
    line.packageCount = packageCount;
    line.lineComment = lineComment;
  }
  finishTransferSave();
}

function saveTransferDraft(){
  if(!currentTransferRef || currentTransferRef.status !== 'draft') return;
  const invalid = document.querySelectorAll('#transferDetailHead .kv-input-invalid, #transferDetailTransport .kv-input-invalid');
  if(invalid.length) return;
  const t = currentTransferRef;
  if(!t) return;

  const textFields = {
    tf_erpNo:'erpNo', tf_comment:'comment',
    tf_trip:'trip', tf_carrier:'carrier', tf_transportType:'transportType', tf_transportBrand:'transportBrand',
    tf_carNumber:'carNumber', tf_trailerNumber:'trailerNumber', tf_techPassportNumber:'techPassportNumber',
    tf_containerNumber:'containerNumber', tf_sealNumber:'sealNumber', tf_driverName:'driverName',
    tf_driverPhone:'driverPhone', tf_driverIdNumber:'driverIdNumber', tf_driverInn:'driverInn',
    tf_customField1:'customField1',
    tf_customField2:'customField2',
    tf_customField3:'customField3',
    tf_customField4:'customField4',
    tf_customField5:'customField5',
    tf_customField6:'customField6',
    tf_customField7:'customField7',
    tf_customField8:'customField8',
    tf_customField9:'customField9',
    tf_customField10:'customField10',
  };
  Object.entries(textFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v !== undefined) t[field] = v;
  });

  const numberFields = { tf_weight:'weight', tf_volume:'volume', tf_palletCount:'palletCount', tf_cargoPlaces:'cargoPlaces' };
  Object.entries(numberFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v !== undefined && v !== '' && !isNaN(v)) t[field] = Number(v);
  });

  const dateFields = { tf_date:'date', tf_shipDate:'shipDate' };
  Object.entries(dateFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v){ t[field] = fromISODate(v); }
  });

  const fromName = getInputValue('tf_fromWarehouse');
  if(fromName){
    const found = Object.values(WAREHOUSES).find(w=>w.name === fromName);
    if(found) t.fromWarehouse = found.id;
  }
  const toName = getInputValue('tf_toWarehouse');
  if(toName){
    const found = Object.values(WAREHOUSES).find(w=>w.name === toName);
    if(found) t.toWarehouse = found.id;
  }

  t.lines = currentTransferComposition.map(({checked, ...l})=>l);
  t.lineCount = currentTransferComposition.length;
  t.totalPieces = currentTransferComposition.reduce((sum,l)=>sum+(Number(l.qty)||0), 0);
  t.items = t.lineCount;

  renderTransfers();
}
function confirmTransferDraft(){
  const invalid = document.querySelectorAll('#transferDetailHead .kv-input-invalid');
  if(invalid.length){
    notify(T('common.message.check_the_numeric_fields_before_co'));
    return;
  }
  if(currentTransferRef){
    currentTransferRef.status = 'warn';
    currentTransferRef.statusLabel = 'В пути';
    if(!currentTransferRef.wmsNumber) currentTransferRef.wmsNumber = docWmsNumberFromRecord(currentTransferRef);
    notify(T('common.message.transfer') + currentTransferRef.no + ' подтверждено');
    openTransfer(currentTransferRef.no);
    renderTransfers();
  }
}
function createNewTransferDraft(){
  const no = generateDocId('WT');
  const whIds = Object.keys(WAREHOUSES);
  const fromWh = (currentWarehouseFilter && currentWarehouseFilter !== 'all') ? currentWarehouseFilter
    : (DEPOSITOR_WAREHOUSES[currentDepositorId] && DEPOSITOR_WAREHOUSES[currentDepositorId][0]) || whIds[0];
  const toWh = whIds.find(w=>w!==fromWh) || fromWh;
  const today = new Date();
  const todayStr = String(today.getDate()).padStart(2,'0') + '.' + String(today.getMonth()+1).padStart(2,'0') + '.' + today.getFullYear();
  const newTransfer = {
    no, date: todayStr, shipDate:'—', receivedDate:'—', fromWarehouse:fromWh, toWarehouse:toWh,
    items:0, status:'draft', statusLabel:'Черновик',
    erpNo:'', wmsNumber:'', comment:'', weight:'', volume:'', lineCount:0, palletCount:'', cargoPlaces:'', totalPieces:0, author:(currentUser && currentUser.full_name) || '',
    carrier:'', transportType:'', transportBrand:'', carNumber:'', trailerNumber:'',
    techPassportNumber:'', containerNumber:'', sealNumber:'', driverName:'', driverPhone:'',
    driverIdNumber:'', driverInn:'',
    lines: [],
  };
  transfers.unshift(newTransfer);
  renderTransfers();
  openTransfer(no);
  notify(T('common.message.new_transfer_draft_created') + no);
}
function openDeleteTransferDraftConfirm(){
  if(!currentTransferRef || currentTransferRef.status !== 'draft') return;
  document.getElementById('deleteTransferConfirmOverlay').classList.add('active');
}
function closeDeleteTransferDraftConfirm(){
  document.getElementById('deleteTransferConfirmOverlay').classList.remove('active');
}
function deleteTransferDraft(){
  if(!currentTransferRef) return;
  const no = currentTransferRef.no;
  const idx = transfers.indexOf(currentTransferRef);
  if(idx > -1) transfers.splice(idx, 1);
  closeDeleteTransferDraftConfirm();
  currentTransferRef = null;
  renderTransfers();
  showTab('transfers');
  notify(T('receipts.option.draft') + no + ' удалён');
}

/* ============== ДЕТАЛИ: Заказ ============== */
function getConsigneeOptions(){
  return [...new Set((orders||[]).map(o=>o.consignee).filter(Boolean))].sort();
}
function getConsigneeAddresses(consigneeName){
  if(!consigneeName) return [];
  return CONSIGNEE_ADDRESSES[consigneeName] || (ADDR_BY_CONSIGNEE[consigneeName] ? [ADDR_BY_CONSIGNEE[consigneeName]] : []);
}
function onOrderConsigneeChange(){
  const consigneeName = document.getElementById('of_consignee').value;
  const addresses = getConsigneeAddresses(consigneeName);
  const addrSelect = document.getElementById('of_deliveryAddress');
  if(addresses.length === 1){
    addrSelect.innerHTML = `<option value="${addresses[0].replace(/"/g,'&quot;')}" selected>${addresses[0]}</option>`;
  }else{
    const placeholder = `<option value="" selected>${T('sku.text.not_selected')}</option>`;
    addrSelect.innerHTML = placeholder + addresses.map(a=>`<option value="${a.replace(/"/g,'&quot;')}">${a}</option>`).join('');
  }
}
let currentOrderComposition = [];
let currentOrderRef = null;
function openOrder(no){
  const o = orders.find(x=>x.no===no);
  if(!o) return;
  currentOrderRef = o;
  const isDraft = o.status === 'draft';
  document.getElementById('orderDraftActions').style.display = isDraft ? '' : 'none';
  document.getElementById('orderDocMeta').innerHTML = `<div>${T('common.label.document_id')}<strong style="color:var(--ink);">${o.no}</strong></div><div>${T('common.label.created_by')}<strong style="color:var(--ink);">${o.author || '—'}</strong></div><div>${T('common.label.contract_number')}<strong style="color:var(--ink);">${getCurrentDepositorContractNumber()}</strong></div>`;
  document.getElementById('orderDetailBanner').innerHTML = '';
  document.getElementById('orderDetailTitle').innerHTML = `${T('orders.title.shipment_order')} ${o.erpNo || o.no} ${badge(o.status, o.statusLabel)}`;
  fcRenderDocTabs('orders', o, isDraft, fcOrderSpecialFieldHtml, 'orderDetailTabButtons', 'orderDetailTabContent');
  if(!o.lines){
    o.lines = mockOrderLines(o.items, isDraft);
  }
  currentOrderComposition = o.lines.map(l=>({...l, checked:false}));
  document.getElementById('orderCheckboxHead').style.display = isDraft ? '' : 'none';
  document.getElementById('orderAddRowBtn').style.display = isDraft ? '' : 'none';
  document.getElementById('orderDeleteRowsBtn').style.display = isDraft ? '' : 'none';
  renderOrderComposition();
  showView('order-detail');
}
function renderOrderComposition(){
  const isDraft = currentOrderRef && currentOrderRef.status === 'draft';
  const cols = tableColumns.orderComposition;
  const order = tableColumnState.orderComposition;
  const hidden = tableColumnHidden.orderComposition;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('orderCompositionHeadRow').innerHTML =
    `<th id="orderCheckboxHead" style="display:${isDraft?'':'none'}; width:36px;"><input type="checkbox" onclick="toggleAllOrderLineChecks(this)"></th>` +
    visibleCols.map(c=>`<th data-col="${c.id}"${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('orderDetailRows').innerHTML = currentOrderComposition.map((l,i)=>{
    const cellsHtml = visibleCols.map(c=>{
      const cls = [c.num?'num':'', c.wrap?'wrap':''].filter(Boolean).join(' ');
      return `<td data-col="${c.id}"${cls?` class="${cls}"`:''}>${c.render(l)}</td>`;
    }).join('');
    return `<tr style="cursor:pointer;" onclick="${isDraft ? `openOrderLineEditModal(${i})` : `openOrderLineDetail(${i})`}">
      ${isDraft ? `<td><input type="checkbox" data-line-idx="${i}" ${l.checked?'checked':''} onclick="event.stopPropagation(); onOrderLineCheck(this)"></td>` : ``}
      ${cellsHtml}
    </tr>`;
  }).join('');
  recalcOrderTotals();
  setTimeout(()=>updateTopScrollWidth('orderComposition'), 0);
}
function mockOrderLineBatches(line){
  const totalPicked = Number(line.picked) || 0;
  if(totalPicked <= 0) return [];
  const expVal = (line.exp && line.exp !== '—') ? line.exp : '31.12.2026';
  const part1 = Math.ceil(totalPicked * 0.7);
  const part2 = totalPicked - part1;
  const rows = [
    {q:'Годен', batch:'П-' + (1000 + Math.floor(Math.random()*900)), series:'С-' + (100 + Math.floor(Math.random()*90)), exp:expVal, stock:part1},
  ];
  if(part2 > 0){
    rows.push({q:'Годен', batch:'П-' + (1000 + Math.floor(Math.random()*900)), series:'С-' + (100 + Math.floor(Math.random()*90)), exp:expVal, stock:part2});
  }
  return rows;
}
let orderLineDetailIndex = null;
function openOrderLineDetail(idx){
  const l = currentOrderComposition[idx];
  if(!l) return;
  orderLineDetailIndex = idx;
  document.getElementById('orderLineDetailTitle').innerHTML = `
    <div style="font-size:13px; color:var(--muted); font-weight:400; margin-bottom:4px;">Заказ на отгрузку ${(currentOrderRef.erpNo || currentOrderRef.no)}</div>
    <div>${l.name}</div>
    <div style="color:var(--muted); margin-top:2px;">${l.code}</div>
  `;
  document.getElementById('orderLineDetailHead').innerHTML = `
    <div class="kv"><label>${T('col.orderComposition.ordered')}</label><div>${l.ordered} ${l.unit || ''}</div></div>
    <div class="kv"><label>${T('col.orderComposition.picked')}</label><div>${l.picked} ${l.unit || ''}</div></div>
    <div class="kv"><label>${T('col.orderComposition.weight')}</label><div>${l.weight}</div></div>
    ${l.lineComment ? `<div class="kv"><label>${T('sku.label.comment')}</label><div>${l.lineComment}</div></div>` : ''}
  `;
  if(!l.batches){
    l.batches = mockOrderLineBatches(l);
  }
  renderOrderLineDetailTable();
  showView('order-line-detail');
}
function renderOrderLineDetailTable(){
  const l = currentOrderComposition[orderLineDetailIndex];
  if(!l) return;
  const batches = l.batches || [];
  const cols = tableColumns.orderLineDetail;
  const order = tableColumnState.orderLineDetail;
  const hidden = tableColumnHidden.orderLineDetail;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('orderLineDetailHeadRow').innerHTML = visibleCols.map(c=>`<th${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('orderLineDetailRows').innerHTML = batches.length ? batches.map(b=>`
    <tr>${visibleCols.map(c=>`<td${c.num?' class="num"':''}>${c.render(b)}</td>`).join('')}</tr>
  `).join('') : `<tr><td colspan="${visibleCols.length}" style="color:var(--muted);">${T('admin.text.no_data')}</td></tr>`;
}
function closeOrderLineDetail(){
  orderLineDetailIndex = null;
  showView('order-detail');
}
function recalcOrderTotals(){
  const lineCountEl = document.getElementById('of_lineCount');
  const totalPiecesEl = document.getElementById('of_totalPieces');
  const lineCount = currentOrderComposition.length;
  const totalPieces = currentOrderComposition.reduce((sum,l)=>sum+(Number(l.ordered)||0), 0);
  if(lineCountEl) lineCountEl.value = lineCount;
  if(totalPiecesEl) totalPiecesEl.value = totalPieces;
}
function onOrderLineCheck(cb){
  const idx = Number(cb.dataset.lineIdx);
  currentOrderComposition[idx].checked = cb.checked;
  const anyChecked = currentOrderComposition.some(l=>l.checked);
  document.getElementById('orderDeleteRowsBtn').disabled = !anyChecked;
}
function toggleAllOrderLineChecks(headerCb){
  currentOrderComposition.forEach(l=>l.checked = headerCb.checked);
  renderOrderComposition();
  document.getElementById('orderDeleteRowsBtn').disabled = !headerCb.checked;
}
function deleteSelectedOrderLines(){
  const before = currentOrderComposition.length;
  currentOrderComposition = currentOrderComposition.filter(l=>!l.checked);
  const removed = before - currentOrderComposition.length;
  if(currentOrderRef) currentOrderRef.lines = currentOrderComposition.map(({checked, ...l})=>l);
  document.getElementById('orderDeleteRowsBtn').disabled = true;
  renderOrderComposition();
  notify(`Удалено строк: ${removed}`);
}

let orderLineEditIndex = null;
let orderLineEditMode = 'edit';
let orderLineEditSelectedProduct = null;
function fillOrderLineEditProductInfo(p){
  document.getElementById('orderLineEditCode').textContent = p ? p.code : '—';
  document.getElementById('orderLineEditName').textContent = p ? p.name : '—';
  document.getElementById('orderLineEditUnit').textContent = p ? p.unit : '—';
  document.getElementById('orderLineEditStockTotal').textContent = p ? `${p.stock} ${p.unit}` : '—';
  document.getElementById('orderLineEditStockAvail').textContent = p ? `${p.stock - p.reserved} ${p.unit}` : '—';
  document.getElementById('orderLineEditQtyLabel').textContent = p && p.unit ? `Заказано, ${p.unit}` : T('col.orderComposition.ordered');
  fcSetupLineEditPackaging('orderLineEdit', p, 'orderLineEditQty', p ? (p.defaultOrderPackaging || null) : null, null);
  fcSetupLineEditPricing('orderLineEdit', p, 'orderLineEditQty', null, null);
}
function openOrderLineEditModal(idx){
  if(!currentOrderRef || currentOrderRef.status !== 'draft') return;
  orderLineEditMode = 'edit';
  orderLineEditIndex = idx;
  const line = currentOrderComposition[idx];
  orderLineEditSelectedProduct = products.find(p=>p.code === line.code) || null;
  document.getElementById('orderLineEditOverlay').classList.add('active');
  document.getElementById('orderLineEditTitle').textContent = T('common.title.edit_line');
  document.getElementById('orderLineEditSearchWrap').style.display = 'none';
  document.getElementById('orderLineEditProductSearch').value = '';
  document.getElementById('orderLineEditProductResults').style.display = 'none';
  fillOrderLineEditProductInfo(orderLineEditSelectedProduct);
  document.getElementById('orderLineEditQty').value = line.ordered ?? '';
  document.getElementById('orderLineEditQty').classList.remove('kv-input-invalid');
  fcSetupLineEditPackaging('orderLineEdit', orderLineEditSelectedProduct, 'orderLineEditQty', line.packagingCode || null, line.packageCount || null);
  fcSetupLineEditPricing('orderLineEdit', orderLineEditSelectedProduct, 'orderLineEditQty', line.price ?? null, line.currency || null);
  document.getElementById('orderLineEditComment').value = line.lineComment || '';
}
function openOrderLineAddModal(){
  if(!currentOrderRef || currentOrderRef.status !== 'draft') return;
  orderLineEditMode = 'add';
  orderLineEditIndex = null;
  orderLineEditSelectedProduct = null;
  document.getElementById('orderLineEditOverlay').classList.add('active');
  document.getElementById('orderLineEditTitle').textContent = 'Добавление строки';
  document.getElementById('orderLineEditSearchWrap').style.display = '';
  document.getElementById('orderLineEditProductSearch').value = '';
  document.getElementById('orderLineEditProductResults').style.display = 'none';
  fillOrderLineEditProductInfo(null);
  document.getElementById('orderLineEditQty').value = '';
  document.getElementById('orderLineEditQty').classList.remove('kv-input-invalid');
  document.getElementById('orderLineEditComment').value = '';
}
function closeOrderLineEditModal(){
  document.getElementById('orderLineEditOverlay').classList.remove('active');
  orderLineEditIndex = null;
  orderLineEditSelectedProduct = null;
}
function searchOrderLineProduct(query){
  const resultsBox = document.getElementById('orderLineEditProductResults');
  const q = query.trim().toLowerCase();
  if(!q){ resultsBox.style.display = 'none'; resultsBox.innerHTML = ''; return; }
  const matches = products.filter(p =>
    p.code.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)
  ).slice(0, 8);
  if(!matches.length){
    resultsBox.innerHTML = `<div style="padding:10px 12px; font-size:13px; color:var(--muted);">${T('stock.text.nothing_found')}</div>`;
    resultsBox.style.display = '';
    return;
  }
  resultsBox.innerHTML = matches.map(p=>`
    <div style="padding:9px 12px; font-size:13px; cursor:pointer; border-bottom:1px solid var(--line);"
         onmousedown="selectOrderLineProduct('${p.code}')">
      <strong>${p.code}</strong> — ${p.name} <span style="color:var(--muted);">(${p.unit})</span>
    </div>
  `).join('');
  resultsBox.style.display = '';
}
function selectOrderLineProduct(code){
  const p = products.find(x=>x.code === code);
  if(!p) return;
  orderLineEditSelectedProduct = p;
  fillOrderLineEditProductInfo(p);
  document.getElementById('orderLineEditProductSearch').value = `${p.code} — ${p.name}`;
  document.getElementById('orderLineEditProductResults').style.display = 'none';
  document.getElementById('orderLineEditProductResults').innerHTML = '';
}
function clearOrderLineProductSearch(){
  orderLineEditSelectedProduct = null;
  fillOrderLineEditProductInfo(null);
  document.getElementById('orderLineEditProductSearch').value = '';
  document.getElementById('orderLineEditProductResults').style.display = 'none';
  document.getElementById('orderLineEditProductResults').innerHTML = '';
  document.getElementById('orderLineEditProductSearch').focus();
}
function saveOrderLineEditModal(){
  const qtyEl = document.getElementById('orderLineEditQty');
  if(qtyEl.value === '' || isNaN(qtyEl.value) || Number(qtyEl.value) < 0){
    qtyEl.classList.add('kv-input-invalid');
    notify(T('common.message.enter_a_valid_quantity'));
    return;
  }
  if(!orderLineEditSelectedProduct){
    notify(T('common.message.select_an_item_from_the_list'));
    return;
  }
  const lineComment = document.getElementById('orderLineEditComment').value.trim();
  const packagingCode = document.getElementById('orderLineEditPackaging').value || null;
  const packageCount = packagingCode ? (Number(document.getElementById('orderLineEditPackageCount').value) || 0) : null;
  const price = document.getElementById('orderLineEditPrice').value !== '' ? Number(document.getElementById('orderLineEditPrice').value) : null;
  const currency = document.getElementById('orderLineEditCurrency').textContent !== '—' ? document.getElementById('orderLineEditCurrency').textContent : null;
  const finishOrderSave = ()=>{
    if(currentOrderRef) currentOrderRef.lines = currentOrderComposition.map(({checked, ...l})=>l);
    closeOrderLineEditModal();
    renderOrderComposition();
    notify(orderLineEditMode === 'add' ? 'Строка добавлена' : 'Строка обновлена');
  };
  if(orderLineEditMode === 'add'){
    const dupIdx = currentOrderComposition.findIndex(l=>l.code === orderLineEditSelectedProduct.code);
    if(dupIdx > -1){
      const existing = currentOrderComposition[dupIdx];
      openDuplicateLineDialog({
        line: existing, qtyField: 'ordered', newQty: Number(qtyEl.value),
        productName: orderLineEditSelectedProduct.name, existingQty: existing.ordered, unit: orderLineEditSelectedProduct.unit,
        finish: finishOrderSave,
      });
      return;
    }
    currentOrderComposition.push({
      code: orderLineEditSelectedProduct.code,
      name: orderLineEditSelectedProduct.name,
      unit: orderLineEditSelectedProduct.unit,
      ordered: Number(qtyEl.value),
      orderedWms: 0,
      picked: '', weight: '', status: '', series: '', batch: '',
      exp: '—', checked: false, lineComment, packagingCode, packageCount, price, currency,
    });
  }else{
    const line = currentOrderComposition[orderLineEditIndex];
    line.code = orderLineEditSelectedProduct.code;
    line.name = orderLineEditSelectedProduct.name;
    line.unit = orderLineEditSelectedProduct.unit;
    line.ordered = Number(qtyEl.value);
    line.lineComment = lineComment;
    line.price = price;
    line.currency = currency;
    line.packagingCode = packagingCode;
    line.packageCount = packageCount;
  }
  finishOrderSave();
}

function saveOrderDraft(){
  if(!currentOrderRef || currentOrderRef.status !== 'draft') return;
  const invalid = document.querySelectorAll('#orderDetailHead .kv-input-invalid, #orderDetailTransport .kv-input-invalid');
  if(invalid.length) return;
  const o = currentOrderRef;
  if(!o) return;

  const textFields = {
    of_erpNo:'erpNo', of_orderType:'orderType', of_deliveryAddress:'deliveryAddress', of_comment:'comment', of_consigneeContact:'consigneeContact',
    of_trip:'trip', of_carrier:'carrier', of_transportType:'transportType', of_transportBrand:'transportBrand',
    of_carNumber:'carNumber', of_trailerNumber:'trailerNumber', of_techPassportNumber:'techPassportNumber',
    of_containerNumber:'containerNumber', of_sealNumber:'sealNumber', of_driverName:'driverName',
    of_driverPhone:'driverPhone', of_driverIdNumber:'driverIdNumber', of_driverInn:'driverInn',
    of_customField1:'customField1',
    of_customField2:'customField2',
    of_customField3:'customField3',
    of_customField4:'customField4',
    of_customField5:'customField5',
    of_customField6:'customField6',
    of_customField7:'customField7',
    of_customField8:'customField8',
    of_customField9:'customField9',
    of_customField10:'customField10',
  };
  Object.entries(textFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v !== undefined) o[field] = v;
  });

  const consigneeVal = getInputValue('of_consignee');
  if(consigneeVal !== undefined) o.consignee = consigneeVal;

  const numberFields = { of_weight:'weight', of_volume:'volume', of_palletCount:'palletCount', of_cargoPlaces:'cargoPlaces' };
  Object.entries(numberFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v !== undefined && v !== '' && !isNaN(v)) o[field] = Number(v);
  });

  const dateFields = { of_date:'date', of_plannedShip:'plannedShip', of_deliveryDate:'deliveryDate' };
  Object.entries(dateFields).forEach(([id, field])=>{
    const v = getInputValue(id);
    if(v){ o[field] = fromISODate(v); }
  });

  const warehouseName = getInputValue('of_warehouse');
  if(warehouseName){
    const found = Object.values(WAREHOUSES).find(w=>w.name === warehouseName);
    if(found) o.warehouse = found.id;
  }

  o.lines = currentOrderComposition.map(({checked, ...l})=>l);
  o.lineCount = currentOrderComposition.length;
  o.totalPieces = currentOrderComposition.reduce((sum,l)=>sum+(Number(l.ordered)||0), 0);
  o.items = o.lineCount;

  renderOrders();
}
function confirmOrderDraft(){
  const invalid = document.querySelectorAll('#orderDetailHead .kv-input-invalid');
  if(invalid.length){
    notify(T('common.message.check_the_numeric_fields_before_co'));
    return;
  }
  if(currentOrderRef){
    currentOrderRef.status = 'warn';
    currentOrderRef.statusLabel = 'В резерве';
    if(!currentOrderRef.wmsNumber) currentOrderRef.wmsNumber = docWmsNumberFromRecord(currentOrderRef);
    notify(T('common.message.order') + currentOrderRef.no + ' подтверждён');
    openOrder(currentOrderRef.no);
    renderOrders();
  }
}
function createNewOrderDraft(){
  const no = generateDocId('WO');
  const wh = (currentWarehouseFilter && currentWarehouseFilter !== 'all') ? currentWarehouseFilter
    : (DEPOSITOR_WAREHOUSES[currentDepositorId] && DEPOSITOR_WAREHOUSES[currentDepositorId][0]) || Object.keys(WAREHOUSES)[0];
  const today = new Date();
  const todayStr = String(today.getDate()).padStart(2,'0') + '.' + String(today.getMonth()+1).padStart(2,'0') + '.' + today.getFullYear();
  const newOrder = {
    warehouse: wh, no, date: todayStr, ship:'—', plannedShip:'—', consignee:'', deliveryAddress:'',
    items:0, weight:'', volume:'', transport:'', status:'draft', statusLabel:'Черновик',
    erpNo:'', wmsNumber:'', orderType:'', comment:'', author:(currentUser && currentUser.full_name) || '',
    carrier:'', transportType:'', transportBrand:'', carNumber:'', trailerNumber:'',
    techPassportNumber:'', containerNumber:'', sealNumber:'', driverName:'', driverPhone:'',
    driverIdNumber:'', driverInn:'', lineCount:0, palletCount:'', cargoPlaces:'', totalPieces:0,
    lines: [],
  };
  orders.unshift(newOrder);
  renderOrders();
  openOrder(no);
  notify(T('common.message.new_order_draft_created') + no);
}
function openDeleteOrderDraftConfirm(){
  if(!currentOrderRef || currentOrderRef.status !== 'draft') return;
  document.getElementById('deleteOrderConfirmOverlay').classList.add('active');
}
function closeDeleteOrderDraftConfirm(){
  document.getElementById('deleteOrderConfirmOverlay').classList.remove('active');
}
function deleteOrderDraft(){
  if(!currentOrderRef) return;
  const no = currentOrderRef.no;
  const idx = orders.indexOf(currentOrderRef);
  if(idx > -1) orders.splice(idx, 1);
  closeDeleteOrderDraftConfirm();
  currentOrderRef = null;
  renderOrders();
  showTab('orders');
  notify(T('receipts.option.draft') + no + ' удалён');
}

/* ============== ДЕТАЛИ: Товар ============== */
function fcHashSeed(str){
  let h = 0;
  const s = String(str || 'x');
  for(let i=0; i<s.length; i++){ h = (h*31 + s.charCodeAt(i)) >>> 0; }
  return h;
}
function fcSeededRand(seed, i){
  const x = Math.sin(seed + i*999.9) * 10000;
  return x - Math.floor(x);
}
function getProductFullInfo(p){
  if(p.__full) return p.__full;
  const seed = fcHashSeed(p.code || p.name);
  const rnd = (i, min, max) => Math.floor(fcSeededRand(seed, i) * (max - min + 1)) + min;
  const pick = (i, arr) => arr[rnd(i, 0, arr.length - 1)];
  const guidPart = (i, len) => Array.from({length:len}, (_,k)=>((seed + i*7 + k) % 16).toString(16)).join('');
  const full = {
    itemCode: 'WMS-' + String(10000 + (seed % 89999)),
    itemFullName: `${p.name} — упаковка ${p.packageType || 'поштучно'}, арт. ${p.code}`,
    hazardGroup: p.hazardClass && p.hazardClass !== 'Не применяется' ? pick(1, ['Группа I','Группа II','Группа III']) : '—',
    netWeight: p.weightNet ?? (typeof p.weightGross === 'number' ? +(p.weightGross*0.9).toFixed(2) : '—'),
    maxStorageTemp: rnd(2, 18, 25), maxHumidity: rnd(3, 55, 70),
    minStorageTemp: rnd(4, -5, 10), minHumidity: rnd(5, 20, 40),
    minDaysForReceipt: rnd(6, 0, 2), minDaysForReservation: rnd(7, 0, 3),
    manufacturer: pick(8, ['ТОО «КазПромИзделия»', 'ООО «СтройМатериалы Плюс»', 'ИП Сериков А.Б.', 'ТОО «АлматыИнвестТорг»', 'Фабрика «Восток»']),
    color: pick(9, ['—','Белый','Чёрный','Серый','Синий','Коричневый']),
    size: pick(10, ['—','S','M','L','XL']),
    xyzClass: pick(11, ['X','Y','Z']),
    guid: `${guidPart(0,8)}-${guidPart(1,4)}-${guidPart(2,4)}-${guidPart(3,4)}-${guidPart(4,12)}`,
    hsCode: String(1000000000 + (seed % 8999999999)).slice(0, 10),
    enstruCode: 'ENSTRU-' + String(100000 + (seed % 899999)),
    currency: 'KZT',
    cost: rnd(12, 500, 50000),
    comment: '—',
    isFragile: pick(13, [true, false, false]),
    isSerialTracked: pick(14, [true, false, false, false]),
    isKit: pick(15, [true, false, false, false, false]),
    isMarkingRequired: pick(16, [true, false, false, false]),
    isExcise: pick(17, [true, false, false, false, false]),
    isControlled: pick(18, [true, false, false, false, false]),
    isWmsActive: true,
    updatedAt: '28.07.2026', updatedBy: 'ArtWMS Integration',
    createdBy: 'ArtWMS Integration', createdAt: '15.01.2025',
    customAttrs: Array.from({length:10}, ()=>'—'),
  };
  p.__full = full;
  return full;
}
function fcYesNo(v){ return v ? 'Да' : 'Нет'; }
function fcKv(label, value){
  return `<div class="kv"><label>${localizeColumnLabel(label)}</label><div>${(value === undefined || value === null || value === '') ? '—' : value}</div></div>`;
}
function openSkuItem(code){
  const p = products.find(x=>x.code===code);
  if(!p) return;
  const f = getProductFullInfo(p);
  const L = (label, fieldId) => getDepositorFieldLabel('sku', fieldId) || label;
  document.getElementById('skuDetailTitle').textContent = p.name;
  const groups = [
    {title:T('sku.text.general_information'), rows:[
      fcKvC('itemCode', 'Код товара', f.itemCode),
      fcKvC('code', L('Коммерческий код', 'code'), p.code),
      fcKvC('name', L('Наименование товара', 'name'), p.name),
      fcKvC('itemFullName', 'Полное наименование товара', f.itemFullName),
      fcKvC('group', L('Группа товара', 'group'), p.group),
      fcKvC('productType', L('Тип товара', 'productType'), p.productType),
      fcKvC('unit', L('Базовая единица измерения', 'unit'), p.unit),
      fcKvC('orderUnit', 'Единица заказа', (()=>{
        const pk = PRODUCT_PACKAGING.find(x=>x.packagingCode === p.defaultOrderPackaging && x.productCode === p.code);
        return pk ? `${pk.packagingCode} — ${fcPackagingName(pk, p.unit)}` : '—';
      })()),
      fcKvC('barcode', L('Штрих код', 'barcode'), p.barcode),
    ]},
    {title:T('sku.text.physical_properties'), rows:[
      fcKvC('length', 'Длина, см', p.length),
      fcKvC('width', 'Ширина, см', p.width),
      fcKvC('height', 'Высота, см', p.height),
      fcKvC('volume', 'Объём, м³', p.volume),
      fcKvC('weightGross', 'Вес брутто, кг', p.weightGross),
      fcKvC('weightNet', 'Вес нетто, кг', f.netWeight),
      fcKvC('color', 'Цвет', f.color),
      fcKvC('size', 'Размер', f.size),
    ]},
    {title:T('sku.text.storage_conditions'), rows:[
      fcKvC('hazardClass', 'Класс опасности', p.hazardClass),
      fcKvC('hazardGroup', 'Группа опасности', f.hazardGroup),
      fcKvC('maxStorageTemp', 'Макс. температура хранения, °C', f.maxStorageTemp),
      fcKvC('minStorageTemp', 'Мин. температура хранения, °C', f.minStorageTemp),
      fcKvC('maxHumidity', 'Макс. влажность хранения, %', f.maxHumidity),
      fcKvC('minHumidity', 'Мин. влажность хранения, %', f.minHumidity),
      fcKvC('minDaysForReceipt', 'Минимальное кол-во дней для приёмки', f.minDaysForReceipt),
      fcKvC('minDaysForReservation', 'Минимальное кол-во дней для резервирования', f.minDaysForReservation),
      fcKvC('shelfLifeDays', 'Срок хранения, дней', p.shelfLifeDays),
    ]},
    {title:T('sku.text.manufacturer_and_classification'), rows:[
      fcKvC('manufacturer', 'Производитель', f.manufacturer),
      fcKvC('countryOrigin', 'Страна производитель', p.countryOrigin),
      fcKvC('brand', 'Торговая марка', p.brand),
      fcKvC('abcClass', 'ABC Класс', p.abcClass),
      fcKvC('xyzClass', 'XYZ Класс', f.xyzClass),
    ]},
    {title:T('sku.text.codes_and_identifiers'), rows:[
      fcKvC('guid', 'GUID', f.guid),
      fcKvC('hsCode', 'Код ТНВЭД', f.hsCode),
      fcKvC('enstruCode', 'Код ЕНСТРУ', f.enstruCode),
      fcKvC('currency', 'Валюта', f.currency),
      fcKvC('cost', 'Стоимость', f.cost),
    ]},
    {title:T('sku.text.attributes_and_tracking'), rows:[
      fcKvC('isFragile', 'Хрупкий товар', fcYesNo(f.isFragile)),
      fcKvC('isSerialTracked', 'Учёт серийных номеров', fcYesNo(f.isSerialTracked)),
      fcKvC('batchTracking', 'Учёт партий', p.batchTracking),
      fcKvC('expiryTracking', 'Учёт срока годности', p.expiryTracking),
      fcKvC('isKit', 'Признак комплекта', fcYesNo(f.isKit)),
      fcKvC('isMarkingRequired', 'Обязательная маркировка', fcYesNo(f.isMarkingRequired)),
      fcKvC('isExcise', 'Акцизный товар', fcYesNo(f.isExcise)),
      fcKvC('isControlled', 'Контролируемый/лицензируемый товар', fcYesNo(f.isControlled)),
    ]},
    {title:T('sku.text.system_information'), rows:[
      fcKvC('statusProduct', L('Статус товара', 'statusProduct'), p.statusProduct),
      fcKvC('isWmsActive', 'Данные WMS действительны', fcYesNo(f.isWmsActive)),
      fcKvC('comment', 'Комментарий', f.comment),
      fcKvC('createdBy', 'Создал', f.createdBy),
      fcKvC('createdAt', 'Создано', f.createdAt),
      fcKvC('updatedBy', 'Изменил', f.updatedBy),
      fcKvC('updatedAt', 'Изменено', f.updatedAt),
    ]},
    {title:T('sku.text.custom_attributes'), rows: f.customAttrs.map((v,i)=>fcKvC('customAttr'+(i+1), T('sku.label.custom_attribute')+' '+(i+1), v))},
    {title:T('common.label.packaging'), rows: (()=>{
      const pkgRows = PRODUCT_PACKAGING.filter(x=>x.productCode === p.code && x.depositorId === currentDepositorId);
      if(!pkgRows.length) return [`<div style="color:var(--muted); font-size:13px;">${T('sku.text.no_packaging_defined_for_this_item')}</div>`];
      return [`
        <div class="table-card" style="margin-top:4px;">
        <table>
        <thead><tr><th>${T('references.column.packaging_code')}</th><th>${T('references.column.packaging_type')}</th><th>${T('references.column.name')}</th><th>${T('sku.text.packaging_barcode')}</th><th class="num">${T('sku.text.factor')}</th><th class="num">${T('sku.label.length_cm')}</th><th class="num">${T('references.column.width_cm')}</th><th class="num">${T('sku.text.height_cm')}</th><th class="num">${T('sku.label.volume_m')}</th><th class="num">${T('sku.text.gross_weight_kg')}</th><th>${T('col.sku.orderUnit')}</th></tr></thead>
        <tbody>${pkgRows.map(r=>`
          <tr>
            <td>${r.packagingCode}</td>
            <td>${r.type}</td>
            <td>${fcPackagingName(r, p.unit)}</td>
            <td>${r.barcode || '—'}</td>
            <td class="num">${r.coefficient}</td>
            <td class="num">${r.length}</td>
            <td class="num">${r.width}</td>
            <td class="num">${r.height}</td>
            <td class="num">${r.volume}</td>
            <td class="num">${r.weightGross}</td>
            <td>${r.packagingCode === p.defaultOrderPackaging ? '★ по умолчанию' : '—'}</td>
          </tr>`).join('')}</tbody>
        </table>
        </div>
      `];
    })()},
  ];
  fcActiveSkuTab = groups.some(g=>g.title === fcActiveSkuTab) ? fcActiveSkuTab : groups[0].title;
  document.getElementById('skuDetailTabs').innerHTML = groups.map(g=>`
    <button class="admin-subtab-btn ${g.title===fcActiveSkuTab?'active':''}" onclick="showSkuDetailTab('${code}', '${g.title.replace(/'/g,"\\'")}')">${g.title}</button>
  `).join('');
  const active = groups.find(g=>g.title === fcActiveSkuTab) || groups[0];
  document.getElementById('skuDetailBody').innerHTML = active.title === T('common.label.packaging')
    ? active.rows.join('')
    : `<div class="kv-grid">${active.rows.join('')}</div>`;
  showView('sku-detail');
}
let fcActiveSkuTab = 'Основная информация';
function showSkuDetailTab(code, title){
  fcActiveSkuTab = title;
  openSkuItem(code);
}
const DEV_DB_STRUCTURE = {
  documents: {
    label: T('dev.button.documents'),
    items: [
      {id:'receipts', label:T('common.button.receipts')},
      {id:'orders', label:T('common.button.orders')},
      {id:'transfers', label:T('transfers.title.transfers')},
    ],
  },
  registers: {
    label: T('dev.button.registers'),
    items: [
      {id:'stock', label:T('stock.title.stock')},
      {id:'services', label:'Зарегистрированные услуги'},
      {id:'serviceTariffs', label:'Тарифы услуг поклажедателя'},
      {id:'invoices', label:'Счета'},
      {id:'documentPhotos', label:'Фотографии документов'},
    ],
  },
  views: {
    label: T('dev.button.views'),
    items: [
      {id:'vSkuFull', label:'v_sku_full'},
      {id:'vStockLines', label:'v_stock_lines'},
      {id:'vStockDetails', label:'v_stock_details'},
      {id:'vStockAvailable', label:'v_stock_available'},
      {id:'vUserAccess', label:'v_user_access'},
      {id:'vReceipts', label:'v_receipts'},
      {id:'vOrders', label:'v_orders'},
      {id:'vTransfers', label:'v_transfers'},
      {id:'vReceiptLines', label:'v_receipt_lines'},
      {id:'vReceiptLineDetails', label:'v_receipt_line_details'},
      {id:'vOrderLines', label:'v_order_lines'},
      {id:'vOrderLineDetails', label:'v_order_line_details'},
      {id:'vTransferLines', label:'v_transfer_lines'},
      {id:'vTransferLineDetails', label:'v_transfer_line_details'},
      {id:'vUsers', label:'v_users'},
      {id:'vSuppliers', label:'v_suppliers'},
      {id:'vConsignees', label:'v_consignees'},
      {id:'vWarehouses', label:'v_warehouses'},
      {id:'vDepositors', label:'v_depositors'},
      {id:'vServiceTariffs', label:'v_service_tariffs'},
      {id:'vInvoices', label:'v_invoices'},
      {id:'vDocumentPhotos', label:'v_document_photos'},
    ],
  },
  directories: {
    label: T('dev.button.main_directories'),
    items: [
      {id:'sku', label:T('references.title.products')},
      {id:'suppliers', label:T('references.button.suppliers')},
      {id:'consignees', label:T('references.title.consignees')},
      {id:'warehouses', label:T('references.title.warehouses')},
      {id:'depositors', label:T('references.button.depositors')},
      {id:'users', label:T('references.button.users')},
    ],
  },
  auxDirectories: {
    label: T('dev.button.supporting_directories'),
    items: [
      {id:'packaging', label:T('common.label.packaging')},
      {id:'consigneeAddresses', label:'Адреса доставки грузополучателей'},
      {id:'userSettings', label:'Настройки пользователей'},
      {id:'userDepositorAccess', label:'Доступность поклажедателей для пользователя'},
      {id:'userWarehouseAccess', label:'Доступность складов для пользователя'},
      {id:'productGroups', label:'Группы товара'},
      {id:'productTypes', label:'Типы товара'},
      {id:'depositorFieldLabels', label:T('admin.title.depositor_field_settings')},
      {id:'translations', label:'Переводы'},
    ],
  },
  enums: {
    label: T('dev.button.enumerations'),
    items: [
      {id:'statuses', label:'Статусы'},
      {id:'userRoles', label:'Роли пользователей'},
      {id:'orderTypes', label:T('references.button.order_types')},
      {id:'receiptDocTypes', label:'Типы приходных документов'},
      {id:'blockReasons', label:'Причины блокировки'},
      {id:'packagingTypes', label:'Типы упаковки'},
      {id:'currencies', label:'Валюты'},
      {id:'locales', label:'Языки'},
      {id:'units', label:'Единицы измерения'},
      {id:'hazardClasses', label:'Классы опасности'},
      {id:'hazardGroups', label:'Группы опасности'},
      {id:'objectTypes', label:'Типы объектов'},
    ],
  },
};
let devActiveMainTab = 'documents';
let devActiveSubtab = { documents:'receipts', registers:'stock', views:'vSkuFull', directories:'sku', auxDirectories:'packaging', enums:'statuses' };
function showDevMainTab(mainId){
  devActiveMainTab = mainId;
  document.querySelectorAll('.dev-maintab-btn').forEach(b=>b.classList.toggle('active', b.dataset.devmain===mainId));
  renderDevSubtabsBar();
  renderDevSettingsContent();
}
function renderDevSubtabsBar(){
  const group = DEV_DB_STRUCTURE[devActiveMainTab];
  const active = devActiveSubtab[devActiveMainTab];
  document.getElementById('devSubtabsBar').innerHTML = group.items.map(it=>`
    <button class="admin-subtab-btn ${it.id===active?'active':''}" onclick="showDevSubtab('${it.id}')">${it.label}</button>
  `).join('');
}
function showDevSubtab(itemId){
  devActiveSubtab[devActiveMainTab] = itemId;
  renderDevSubtabsBar();
  renderDevSettingsContent();
}
const DEV_FIELD_EN_DICT = {
  itemCode:'Item Code', code:'Commercial Code', name:'Item Name', unit:'Base Unit of Measure',
  hasPackaging:'Uses Packaging', orderUnit:'Order Unit', barcode:'Barcode', group:'Item Group',
  productType:'Item Type', length:'Length', width:'Width', height:'Height', volume:'Volume',
  weightGross:'Gross Weight', manufacturer:'Manufacturer', countryOrigin:'Country of Origin',
  brand:'Brand', isFragile:'Is Fragile', isSerialTracked:'Is Serial Tracked', batchTracking:'Is Batch Tracked',
  expiryTracking:'Is Expiry Tracked', statusProduct:'Item Status', itemFullName:'Full Item Name',
  hazardClass:'Hazard Class', hazardGroup:'Hazard Group', weightNet:'Net Weight',
  maxStorageTemp:'Max Storage Temperature', minStorageTemp:'Min Storage Temperature',
  maxHumidity:'Max Humidity', minHumidity:'Min Humidity', minDaysForReceipt:'Min Days For Receipt',
  minDaysForReservation:'Min Days For Reservation', shelfLifeDays:'Shelf Life (Days)', color:'Color',
  size:'Size', abcClass:'ABC Class', xyzClass:'XYZ Class', guid:'GUID', hsCode:'HS Code',
  enstruCode:'ENSTRU Code', currency:'Currency', cost:'Cost', comment:'Comment', isKit:'Is Kit',
  isMarkingRequired:'Is Marking Required', isExcise:'Is Excise', isControlled:'Is Controlled',
  isWmsActive:'Is WMS Active', createdBy:'Created By', createdAt:'Created At', updatedBy:'Updated By',
  updatedAt:'Updated At',
};
for(let i=1;i<=10;i++) DEV_FIELD_EN_DICT['customAttr'+i] = 'Custom Attribute ' + i;
function fcToSnakeCase(id){
  return id.replace(/([a-z0-9])([A-Z])/g,'$1_$2').toLowerCase();
}
function fcInferDataType(f){
  if(f.id === 'hasPackaging' || /^is[A-Z]/.test(f.id)) return 'boolean';
  if(f.id === 'guid') return 'uuid';
  if(f.id === 'createdAt' || f.id === 'updatedAt') return 'timestamp';
  if(f.id === 'cost') return 'numeric(14,2)';
  if(f.num) return 'numeric(12,3)';
  if(f.id === 'comment' || f.id === 'itemFullName') return 'text';
  if(f.id === 'name') return 'varchar(255)';
  return 'varchar(100)';
}
function buildFieldTableRows(defs, systemRows){
  const rows = (systemRows || []).slice();
  defs.forEach(f=>{
    rows.push({
      pk:false,
      dbField: f.dbField || fcToSnakeCase(f.id),
      dataType: fcInferDataType(f),
      hidden: false,
      nameEN: f.labelEn || DEV_FIELD_EN_DICT[f.id] || f.id,
      nameRU: f.label,
    });
  });
  return rows;
}
function fcSimpleNameField(baseField, ruLabel, enLabel){
  return [{pk:false, dbField:baseField, dataType:'varchar(255)', hidden:false, nameEN:enLabel, nameRU:ruLabel}];
}
function fcNameFieldRows(baseField, ruLabel, enLabel){
  return [
    {pk:false, dbField:baseField+'_ru', dataType:'varchar(255)', hidden:false, nameEN:enLabel+' (RU)', nameRU:ruLabel+' (RU)'},
    {pk:false, dbField:baseField+'_en', dataType:'varchar(255)', hidden:false, nameEN:enLabel+' (EN)', nameRU:ruLabel+' (EN)'},
    {pk:false, dbField:baseField+'_lang1', dataType:'varchar(255)', hidden:false, nameEN:enLabel+' (Lang1)', nameRU:ruLabel+' (Lang1)'},
    {pk:false, dbField:baseField+'_lang2', dataType:'varchar(255)', hidden:false, nameEN:enLabel+' (Lang2)', nameRU:ruLabel+' (Lang2)'},
  ];
}
function renderDevFieldTable(rows, opts){
  const hideWms = opts && opts.hideWms;
  return `
    <div class="table-card">
    <table>
    <thead><tr>
      <th>KEY</th><th>DB Field Name</th><th>Data Type</th><th>Hidden</th>
      <th>Name</th>${hideWms ? '' : '<th>External System Field Code (WMS)</th>'}
    </tr></thead>
    <tbody>${rows.map(r=>`
      <tr>
        <td>${r.pk ? '🔑 PK' : (r.fk ? `🔗 FK → ${r.fk}` : '')}</td>
        <td><code>${r.dbField}</code></td>
        <td>${r.dataType}</td>
        <td>${r.hidden ? 'Yes' : 'No'}</td>
        <td>${r.nameEN}</td>${hideWms ? '' : '<td>—</td>'}
      </tr>`).join('')}</tbody>
    </table>
    </div>`;
}
function buildDocHeaderRows(docType){
  const common = [
    {pk:true, dbField:'doc_id', dataType:'uuid', hidden:true, nameEN:'Document ID', nameRU:'ID документа'},
    {pk:false, dbField:'document_no', dataType:'varchar(30)', hidden:false, nameEN:'Document No', nameRU:'Номер документа (внутренний)'},
    {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID депозитора'},
    {pk:false, dbField:'doc_number', dataType:'varchar(30)', hidden:false, nameEN:'Document Number (WMS)', nameRU:'Номер документа WMS'},
    {pk:false, dbField:'erp_number', dataType:'varchar(30)', hidden:false, nameEN:'Document Number (ERP)', nameRU:'Номер документа (в ERP клиента)'},
    {pk:false, fk:'user', dbField:'author_id', dataType:'uuid', hidden:true, nameEN:'Author ID', nameRU:'ID автора'},
    {pk:false, fk:'enum_status', dbField:'status_id', dataType:'int', hidden:true, nameEN:'Status ID', nameRU:'ID статуса'},
    {pk:false, dbField:'doc_date', dataType:'date', hidden:false, nameEN:'Document Date', nameRU:'Дата создания'},
    {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Warehouse ID', nameRU:'Склад'},
    {pk:false, dbField:'trip', dataType:'varchar(30)', hidden:false, nameEN:'Trip Number', nameRU:'Рейс'},
    {pk:false, dbField:'comment', dataType:'text', hidden:false, nameEN:'Comment', nameRU:'Комментарий'},
  ];
  const typeSpecific = {
    receipts: [
      {pk:false, fk:'enum_receipt_doc_type', dbField:'doc_type_id', dataType:'smallint', hidden:true, nameEN:'Receipt Document Type ID', nameRU:'ID типа приходного документа'},
      {pk:false, fk:'supplier', dbField:'supplier_id', dataType:'uuid', hidden:true, nameEN:'Supplier ID', nameRU:'ID поставщика'},
      {pk:false, dbField:'accompanying_doc_number', dataType:'varchar(50)', hidden:false, nameEN:'Accompanying Document Number', nameRU:'Номер сопроводительного документа'},
      {pk:false, dbField:'invoice_number', dataType:'varchar(50)', hidden:false, nameEN:'Invoice Number', nameRU:'Номер Инвойса'},
      {pk:false, dbField:'gtd', dataType:'varchar(50)', hidden:false, nameEN:'Customs Declaration Number', nameRU:'ГТД'},
      {pk:false, dbField:'eta_date', dataType:'date', hidden:false, nameEN:'Expected Arrival Date', nameRU:'Ожидаемая дата поставки'},
      {pk:false, dbField:'arrival_date', dataType:'date', hidden:false, nameEN:'Arrival Date', nameRU:'Дата прибытия'},
      {pk:false, dbField:'receiving_end_date', dataType:'date', hidden:false, nameEN:'Receiving End Date', nameRU:'Дата окончания приёмки'},
    ],
    orders: [
      {pk:false, fk:'enum_order_type', dbField:'order_type_id', dataType:'smallint', hidden:true, nameEN:'Order Type ID', nameRU:'ID типа заказа'},
      {pk:false, fk:'consignee', dbField:'consignee_id', dataType:'uuid', hidden:true, nameEN:'Consignee ID', nameRU:'ID грузополучателя (consignee)'},
      {pk:false, fk:'delivery_point', dbField:'delivery_point_id', dataType:'uuid', hidden:true, nameEN:'Delivery Point ID', nameRU:'ID точки доставки'},
      {pk:false, dbField:'consignee_contact', dataType:'varchar(255)', hidden:false, nameEN:'Consignee Contact', nameRU:'Контактные данные грузополучателя'},
      {pk:false, dbField:'planned_ship_date', dataType:'date', hidden:false, nameEN:'Planned Ship Date', nameRU:'Планируемая дата отгрузки'},
      {pk:false, dbField:'ship_date', dataType:'date', hidden:false, nameEN:'Ship Date', nameRU:'Дата отгрузки'},
    ],
    transfers: [
      {pk:false, fk:'warehouse', dbField:'from_warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'From Warehouse ID', nameRU:'Склад-отправитель'},
      {pk:false, fk:'warehouse', dbField:'to_warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'To Warehouse ID', nameRU:'Склад-получатель'},
      {pk:false, dbField:'ship_date', dataType:'date', hidden:false, nameEN:'Ship Date', nameRU:'Дата отгрузки'},
      {pk:false, dbField:'received_date', dataType:'date', hidden:false, nameEN:'Received Date', nameRU:'Дата приёмки'},
    ],
  };
  const totals = [
    {pk:false, dbField:'line_count', dataType:'int', hidden:false, nameEN:'Line Count', nameRU:'Количество строк'},
    {pk:false, dbField:'total_pieces', dataType:'numeric(14,3)', hidden:false, nameEN:'Total Pieces', nameRU:'Количество штук'},
    {pk:false, dbField:'cargo_places', dataType:'int', hidden:false, nameEN:'Cargo Places', nameRU:'Количество грузомест'},
    {pk:false, dbField:'pallet_count', dataType:'int', hidden:false, nameEN:'Pallet Count', nameRU:'Количество паллет'},
    {pk:false, dbField:'weight_kg', dataType:'numeric(12,3)', hidden:false, nameEN:'Weight', nameRU:'Вес, кг'},
    {pk:false, dbField:'volume_m3', dataType:'numeric(12,5)', hidden:false, nameEN:'Volume', nameRU:'Объём, м³'},
  ];
  const transport = [
    {pk:false, dbField:'carrier', dataType:'varchar(255)', hidden:false, nameEN:'Carrier', nameRU:'Перевозчик'},
    {pk:false, dbField:'transport_type', dataType:'varchar(50)', hidden:false, nameEN:'Transport Type', nameRU:'Тип транспорта'},
    {pk:false, dbField:'transport_brand', dataType:'varchar(100)', hidden:false, nameEN:'Transport Brand', nameRU:'Марка транспорта'},
    {pk:false, dbField:'car_number', dataType:'varchar(20)', hidden:false, nameEN:'Car Number', nameRU:'Номер авто'},
    {pk:false, dbField:'trailer_number', dataType:'varchar(20)', hidden:false, nameEN:'Trailer Number', nameRU:'Номер прицепа'},
    {pk:false, dbField:'tech_passport_number', dataType:'varchar(50)', hidden:false, nameEN:'Technical Passport Number', nameRU:'Номер технического паспорта'},
    {pk:false, dbField:'container_number', dataType:'varchar(20)', hidden:false, nameEN:'Container Number', nameRU:'Номер контейнера'},
    {pk:false, dbField:'seal_number', dataType:'varchar(20)', hidden:false, nameEN:'Seal Number', nameRU:'Номер пломбы'},
    {pk:false, dbField:'driver_name', dataType:'varchar(150)', hidden:false, nameEN:'Driver Name', nameRU:'ФИО водителя'},
    {pk:false, dbField:'driver_phone', dataType:'varchar(30)', hidden:false, nameEN:'Driver Phone', nameRU:'Телефон водителя'},
    {pk:false, dbField:'driver_id_number', dataType:'varchar(30)', hidden:false, nameEN:'Driver ID Number', nameRU:'Номер удостоверения водителя'},
    {pk:false, dbField:'driver_inn', dataType:'varchar(20)', hidden:false, nameEN:'Driver INN', nameRU:'ИИН водителя'},
  ];
  if(docType === 'orders'){
    transport.push({pk:false, dbField:'delivery_date', dataType:'date', hidden:false, nameEN:'Delivery Date', nameRU:'Дата доставки'});
  }
  const extraInfo = Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)}));
  return [...common, ...typeSpecific[docType], ...totals, ...transport, ...extraInfo];
}
function buildResolvedHeaderRows(docType){
  const rows = buildDocHeaderRows(docType).filter(f=>!f.pk);
  const RESOLVE_MAP = {
    depositor_id: {dbField:'depositor_name', dataType:'varchar(255)', nameEN:'Depositor Name'},
    author_id: {dbField:'author_name', dataType:'varchar(150)', nameEN:'Author Name'},
    status_id: {dbField:'status_name', dataType:'varchar(100)', nameEN:'Status'},
    warehouse_id: {dbField:'warehouse_name', dataType:'varchar(100)', nameEN:'Warehouse Name'},
    from_warehouse_id: {dbField:'from_warehouse_name', dataType:'varchar(100)', nameEN:'From Warehouse Name'},
    to_warehouse_id: {dbField:'to_warehouse_name', dataType:'varchar(100)', nameEN:'To Warehouse Name'},
    doc_type_id: {dbField:'doc_type_name', dataType:'varchar(100)', nameEN:'Receipt Document Type'},
    supplier_id: {dbField:'supplier_name', dataType:'varchar(255)', nameEN:'Supplier Name'},
    order_type_id: {dbField:'order_type_name', dataType:'varchar(100)', nameEN:'Order Type'},
    consignee_id: {dbField:'consignee_name', dataType:'varchar(255)', nameEN:'Consignee Name'},
    delivery_point_id: {dbField:'delivery_point_address', dataType:'varchar(255)', nameEN:'Delivery Address'},
  };
  return rows.map(r => {
    const resolved = RESOLVE_MAP[r.dbField];
    if(!resolved) return {pk:false, dbField:r.dbField, dataType:r.dataType, hidden:false, nameEN:r.nameEN};
    return {pk:false, dbField:resolved.dbField, dataType:resolved.dataType, hidden:false, nameEN:resolved.nameEN};
  });
}
function buildDocCompositionRows(docType){
  const headerTableFk = {receipts:'receipts', orders:'orders', transfers:'transfers'}[docType];
  const common = [
    {pk:true, dbField:'line_id', dataType:'uuid', hidden:true, nameEN:'Line ID', nameRU:'ID строки'},
    {pk:false, fk:headerTableFk, dbField:'doc_id', dataType:'uuid', hidden:true, nameEN:'Document ID', nameRU:'ID документа'},
    {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number', nameRU:'Номер строки'},
    {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:true, nameEN:'SKU ID', nameRU:'ID SKU'},
  ];
  const qtyFields = {
    receipts: [
      {pk:false, dbField:'expected_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Expected Quantity', nameRU:'Ожидается'},
      {pk:false, dbField:'expected_qty_wms', dataType:'numeric(14,3)', hidden:false, nameEN:'Expected Quantity (WMS)', nameRU:'Ожидаемое количество WMS'},
      {pk:false, dbField:'accepted_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Accepted Quantity', nameRU:'Принято'},
      {pk:false, dbField:'defect_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Defect Quantity', nameRU:'Брак'},
    ],
    orders: [
      {pk:false, dbField:'ordered_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Ordered Quantity', nameRU:'Заказано'},
      {pk:false, dbField:'ordered_qty_wms', dataType:'numeric(14,3)', hidden:false, nameEN:'Ordered Quantity (WMS)', nameRU:'Заказано WMS'},
      {pk:false, dbField:'picked_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Picked Quantity', nameRU:'Собрано'},
    ],
    transfers: [
      {pk:false, dbField:'qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity', nameRU:'Кол-во'},
      {pk:false, dbField:'qty_wms', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity (WMS)', nameRU:'Количество WMS'},
    ],
  };
  const weight = docType === 'orders' ? [{pk:false, dbField:'weight_kg', dataType:'numeric(12,3)', hidden:false, nameEN:'Weight', nameRU:'Вес, кг'}] : [];
  const batch = [
    {pk:false, dbField:'batch_number', dataType:'varchar(50)', hidden:false, nameEN:'Batch Number', nameRU:'Партия'},
    {pk:false, dbField:'series_number', dataType:'varchar(50)', hidden:false, nameEN:'Series Number', nameRU:'Серия'},
    {pk:false, dbField:'expiry_date', dataType:'date', hidden:false, nameEN:'Expiry Date', nameRU:'Срок годности'},
    {pk:false, dbField:'quality', dataType:'varchar(20)', hidden:false, nameEN:'Quality', nameRU:'Качество'},
  ];
  const pricing = (docType === 'receipts' || docType === 'orders') ? [
    {pk:false, dbField:'price', dataType:'numeric(14,2)', hidden:false, nameEN:'Price', nameRU:'Цена'},
    {pk:false, fk:'enum_currency', dbField:'currency_id', dataType:'smallint', hidden:true, nameEN:'Currency ID', nameRU:'ID валюты'},
  ] : [];
  const comment = [{pk:false, dbField:'line_comment', dataType:'text', hidden:false, nameEN:'Line Comment', nameRU:'Комментарий'}];
  const extraInfo = Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)}));
  return [...common, ...qtyFields[docType], ...weight, ...batch, ...pricing, ...comment, ...extraInfo];
}
function renderDevSettingsContent(){
  const group = DEV_DB_STRUCTURE[devActiveMainTab];
  const activeId = devActiveSubtab[devActiveMainTab];
  const item = group.items.find(it=>it.id===activeId);
  const I18N_NOTE = `<div class="hint" style="margin-bottom:16px;">Наименование хранится в поле по умолчанию (языком по умолчанию, обычно RU); переводы на остальные языки — в общей таблице <code>translation</code> (см. подвкладку «Переводы»).</div>`;
  if(devActiveMainTab === 'documents'){
    const DOC_TABLE_NAMES = {
      receipts: {header:'receipts', comp:'receipt_details'},
      orders: {header:'orders', comp:'order_details'},
      transfers: {header:'transfers', comp:'transfer_details'},
    };
    const names = DOC_TABLE_NAMES[activeId];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">${item.label}</div>
      <div class="hint" style="margin-bottom:16px;">В БД документ хранится двумя таблицами: заголовок документа и состав документа (табличная часть), связанные по <code>doc_id</code>.</div>
      <div class="section-title">Таблица «Заголовок документа»: <code>${names.header}</code></div>
      ${renderDevFieldTable(buildDocHeaderRows(activeId))}
      <div class="section-title" style="margin-top:24px;">Таблица «Состав документа»: <code>${names.comp}</code></div>
      <div class="hint" style="margin-bottom:16px;">Поле «Сумма» в БД не хранится — вычисляется на лету как «Количество × Цена».</div>
      ${renderDevFieldTable(buildDocCompositionRows(activeId))}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && (activeId === 'productGroups' || activeId === 'productTypes')){
    const isGroup = activeId === 'productGroups';
    const rows = [
      {pk:true, dbField:'id', dataType:'uuid', hidden:true, nameEN:'ID'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID'},
      {pk:false, dbField:'code', dataType:'varchar(30)', hidden:false, nameEN:'Code'},
      ...fcSimpleNameField('name', isGroup ? 'Наименование группы' : 'Наименование типа', 'Name'),
      {pk:false, dbField:'description', dataType:'text', hidden:false, nameEN:'Description'},
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>${isGroup ? 'product_group' : 'product_type'}</code></div>
      ${I18N_NOTE}
      <div class="hint" style="margin-bottom:16px;">В отличие от простых перечислений (<code>enum_*</code>), это полноценный справочник — предполагается большое и часто пополняемое количество значений, поэтому есть описание и он вынесен из «Перечислений». Значения — в разрезе поклажедателя (у каждого поклажедателя своя номенклатура групп/типов), а не общие на всю систему.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vSkuFull'){
    const NORMALIZED_FIELDS = new Set(['name','unit','hazardClass','hazardGroup','group','productType','currency','orderUnit','statusProduct']);
    const defsNoName = skuColumnDefs.filter(f=>!NORMALIZED_FIELDS.has(f.id));
    const rows = buildFieldTableRows(defsNoName, [
      {pk:false, dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'depositor_name', dataType:'varchar(255)', hidden:false, nameEN:'Depositor Name'},
    ]);
    const codeIdx = rows.findIndex(r=>r.dbField === 'code');
    rows.splice(codeIdx + 1, 0, {pk:false, dbField:'name', dataType:'varchar(255)', hidden:false, nameEN:'Item Name'});
    const barcodeIdx = rows.findIndex(r=>r.dbField === 'barcode');
    rows.splice(barcodeIdx + 1, 0,
      {pk:false, dbField:'uom_name', dataType:'varchar(50)', hidden:false, nameEN:'Unit of Measure'},
      {pk:false, dbField:'order_unit_name', dataType:'varchar(255)', hidden:false, nameEN:'Order Unit'}
    );
    const typeIdx = rows.findIndex(r=>r.dbField === 'item_type' || r.dbField === 'product_type');
    rows.splice((typeIdx>-1?typeIdx:rows.length-1) + 1, 0,
      {pk:false, dbField:'item_group_name', dataType:'varchar(100)', hidden:false, nameEN:'Item Group'},
      {pk:false, dbField:'item_type_name', dataType:'varchar(100)', hidden:false, nameEN:'Item Type'}
    );
    rows.push(
      {pk:false, dbField:'hazard_class_name', dataType:'varchar(100)', hidden:false, nameEN:'Hazard Class'},
      {pk:false, dbField:'hazard_group_name', dataType:'varchar(100)', hidden:false, nameEN:'Hazard Group'},
      {pk:false, dbField:'currency_code', dataType:'varchar(3)', hidden:false, nameEN:'Currency Code'},
      {pk:false, dbField:'status_name', dataType:'varchar(100)', hidden:false, nameEN:'Status'}
    );
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_sku_full</code></div>
      <div class="hint" style="margin-bottom:16px;">Полный «плоский» набор полей таблицы <code>SKU</code> (включая все дополнительные параметры) — построено из того же нормализованного набора полей, что и сама таблица (см. подвкладку «Товары»): все ссылочные поля (<code>uom_id</code>→<code>uom_name</code>, <code>order_unit_id</code>→<code>order_unit_name</code>, <code>item_group_id</code>→<code>item_group_name</code>, <code>item_type_id</code>→<code>item_type_name</code>, <code>status_id</code>→<code>status_name</code>, <code>hazard_class_id</code>→<code>hazard_class_name</code>, <code>hazard_group_id</code>→<code>hazard_group_name</code>, <code>currency_id</code>→<code>currency_code</code>, <code>depositor_id</code>→<code>depositor_name</code>) заменяются на читаемые значения — без ручных JOIN'ов в коде приложения. Это представление один в один формирует таблицу «Товары» в приложении.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vStockLines'){
    const rows = [
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'sku_code', dataType:'varchar(50)', hidden:false, nameEN:'SKU Commercial Code'},
      {pk:false, dbField:'sku_name', dataType:'varchar(255)', hidden:false, nameEN:'SKU Name'},
      {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Warehouse ID'},
      {pk:false, dbField:'unit_name', dataType:'varchar(50)', hidden:false, nameEN:'Unit Name'},
      {pk:false, dbField:'quantity', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity On Hand'},
      {pk:false, dbField:'reserved_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Reserved Quantity'},
      {pk:false, dbField:'blocked_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Blocked Quantity'},
      {pk:false, dbField:'available_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Available Quantity'},
      {pk:false, dbField:'incoming_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Incoming Quantity'},
      ...Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)})),
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_stock_lines</code></div>
      <div class="hint" style="margin-bottom:16px;">Питает список «Запасы» (одна строка = одна строка на экране, агрегация по товару+складу). Соответствие полям экрана: <code>sku_code</code> → «Код», <code>sku_name</code> → «Наименование», <code>warehouse_id</code> → «Склад», <code>quantity</code> → «Наличие», <code>reserved_qty</code> → «Резерв», <code>blocked_qty</code> → «Заблокировано», <code>available_qty</code> → «Доступное количество», <code>incoming_qty</code> → «К получению». Обратите внимание: поля «Группа» и «Ед.» экрана «Запасы» относятся к товару, а не к остатку — берутся через <code>sku_id</code> из <code>v_sku_full</code>, а не хранятся здесь.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vStockDetails'){
    const rows = [
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'sku_code', dataType:'varchar(50)', hidden:false, nameEN:'SKU Commercial Code'},
      {pk:false, dbField:'sku_name', dataType:'varchar(255)', hidden:false, nameEN:'SKU Name'},
      {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Warehouse ID'},
      {pk:false, dbField:'batch_number', dataType:'varchar(50)', hidden:false, nameEN:'Batch Number'},
      {pk:false, dbField:'series_number', dataType:'varchar(50)', hidden:false, nameEN:'Series Number'},
      {pk:false, dbField:'expiry_date', dataType:'date', hidden:false, nameEN:'Expiry Date'},
      {pk:false, dbField:'quality', dataType:'varchar(20)', hidden:false, nameEN:'Quality'},
      {pk:false, dbField:'block_reason', dataType:'varchar(50)', hidden:false, nameEN:'Block Reason'},
      {pk:false, dbField:'unit_name', dataType:'varchar(50)', hidden:false, nameEN:'Unit Name'},
      {pk:false, dbField:'quantity', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity On Hand'},
      ...Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)})),
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_stock_details</code></div>
      <div class="hint" style="margin-bottom:16px;">Питает таблицу «Детализация» — открывается по клику на строку в «Запасы», показывает разбивку остатка по партиям. Соответствие полям экрана: <code>batch_number</code> → «Партия», <code>series_number</code> → «Серия», <code>expiry_date</code> → «Срок годности», <code>quantity</code> → «Наличие», <code>quality</code> → «Качество», <code>block_reason</code> → «Причина блокировки». Поля «Доступно» и «Резерв» экрана детализации сюда не входят по заданному списку — они расчётные для конкретной партии, при необходимости можно добавить отдельно.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vReceipts'){
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_receipts</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>receipt</code> — ссылочные поля (<code>depositor_id</code>→depositor_name, <code>author_id</code>→author_name, <code>status_id</code>→status_name, <code>warehouse_id</code>→warehouse_name, <code>doc_type_id</code>→doc_type_name, <code>supplier_id</code>→supplier_name) заменены на читаемые значения. <code>document_no</code> — внутренний номер документа (аналог <code>r.no</code> в коде). Формирует список «Поступления» и заголовок карточки поступления в приложении.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(buildResolvedHeaderRows('receipts'), {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vOrders'){
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_orders</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>order</code> — ссылочные поля (<code>depositor_id</code>→depositor_name, <code>author_id</code>→author_name, <code>status_id</code>→status_name, <code>warehouse_id</code>→warehouse_name, <code>order_type_id</code>→order_type_name, <code>consignee_id</code>→consignee_name, <code>delivery_point_id</code>→delivery_point_address) заменены на читаемые значения. <code>document_no</code> — внутренний номер документа (используется для открытия карточки, аналог <code>o.no</code> в коде). Формирует список «Заказы» и заголовок карточки заказа в приложении.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(buildResolvedHeaderRows('orders'), {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vTransfers'){
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_transfers</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>transfer</code> — ссылочные поля (<code>depositor_id</code>→depositor_name, <code>from_warehouse_id</code>→from_warehouse_name, <code>to_warehouse_id</code>→to_warehouse_name, <code>status_id</code>→status_name) заменены на читаемые значения. <code>document_no</code> — внутренний номер документа (аналог <code>t.no</code> в коде). Формирует список «Перемещения» и заголовок карточки перемещения в приложении.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(buildResolvedHeaderRows('transfers'), {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vOrderLineDetails'){
    const rows = [
      {pk:false, fk:'orders', dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'quality', dataType:'varchar(20)', hidden:false, nameEN:'Quality'},
      {pk:false, dbField:'batch_number', dataType:'varchar(50)', hidden:false, nameEN:'Batch Number'},
      {pk:false, dbField:'series_number', dataType:'varchar(50)', hidden:false, nameEN:'Series Number'},
      {pk:false, dbField:'expiry_date', dataType:'date', hidden:false, nameEN:'Expiry Date'},
      {pk:false, dbField:'picked_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Picked Quantity (this batch)'},
      ...Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)})),
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_order_line_details</code></div>
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>order_details</code> (та же таблица, что и <code>v_order_lines</code>) — просто другой набор колонок той же строки: качество, партия, серия, срок годности, собранное количество.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vReceiptLines'){
    const rows = [
      {pk:false, fk:'receipts', dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'sku_code', dataType:'varchar(50)', hidden:false, nameEN:'SKU Commercial Code'},
      {pk:false, dbField:'sku_name', dataType:'varchar(255)', hidden:false, nameEN:'SKU Name'},
      {pk:false, dbField:'unit_name', dataType:'varchar(50)', hidden:false, nameEN:'Unit'},
      {pk:false, dbField:'expected_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Expected Quantity'},
      {pk:false, dbField:'expected_qty_wms', dataType:'numeric(14,3)', hidden:false, nameEN:'Expected Quantity (WMS)'},
      {pk:false, dbField:'accepted_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Accepted Quantity'},
      {pk:false, dbField:'defect_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Defect Quantity'},
      {pk:false, dbField:'price', dataType:'numeric(14,2)', hidden:false, nameEN:'Price'},
      {pk:false, dbField:'sum', dataType:'numeric(14,2)', hidden:false, nameEN:'Sum'},
      {pk:false, dbField:'currency_code', dataType:'varchar(3)', hidden:false, nameEN:'Currency Code'},
      {pk:false, dbField:'line_comment', dataType:'text', hidden:false, nameEN:'Line Comment'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>receipt_details</code> — те же строки, что и «Состав поступления» в приложении. <code>sku_id</code> расшифрован в <code>sku_code</code>/<code>sku_name</code> (+ <code>unit_name</code>), <code>currency_id</code> → <code>currency_code</code>. <code>sum</code> не хранится — вычисляется как <code>expected_qty × price</code>.</div>
      <div class="hint" style="margin-bottom:16px;">Строго соответствует реальной таблице «Состав поступления» в приложении (<code>receiptCompositionColumnDefs</code>): <code>sku_id</code> расшифрован в <code>sku_code</code>/<code>sku_name</code>, <code>currency_id</code> → <code>currency_code</code>, добавлена единица измерения. <code>sum</code> не хранится — вычисляется как <code>expected_qty × price</code>.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vReceiptLineDetails'){
    const rows = [
      {pk:false, fk:'receipts', dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'quality', dataType:'varchar(20)', hidden:false, nameEN:'Quality'},
      {pk:false, dbField:'batch_number', dataType:'varchar(50)', hidden:false, nameEN:'Batch Number'},
      {pk:false, dbField:'series_number', dataType:'varchar(50)', hidden:false, nameEN:'Series Number'},
      {pk:false, dbField:'expiry_date', dataType:'date', hidden:false, nameEN:'Expiry Date'},
      {pk:false, dbField:'accepted_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Accepted Quantity (this batch)'},
      ...Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)})),
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>receipt_details</code> (та же таблица, что и <code>v_receipt_lines</code>) — просто другой набор колонок той же строки: качество, партия, серия, срок годности, принятое количество.</div>
      <div class="hint" style="margin-bottom:16px;">Строго соответствует реальной таблице «Детализация» строки поступления (<code>receiptLineDetailColumnDefs</code>): качество, партия, серия, срок годности и принятое количество по этой партии.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vOrderLines'){
    const rows = [
      {pk:false, fk:'orders', dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'sku_code', dataType:'varchar(50)', hidden:false, nameEN:'SKU Commercial Code'},
      {pk:false, dbField:'sku_name', dataType:'varchar(255)', hidden:false, nameEN:'SKU Name'},
      {pk:false, dbField:'unit_name', dataType:'varchar(50)', hidden:false, nameEN:'Unit'},
      {pk:false, dbField:'ordered_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Ordered Quantity'},
      {pk:false, dbField:'ordered_qty_wms', dataType:'numeric(14,3)', hidden:false, nameEN:'Ordered Quantity (WMS)'},
      {pk:false, dbField:'picked_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Picked Quantity'},
      {pk:false, dbField:'weight_kg', dataType:'numeric(12,3)', hidden:false, nameEN:'Weight'},
      {pk:false, dbField:'price', dataType:'numeric(14,2)', hidden:false, nameEN:'Price'},
      {pk:false, dbField:'sum', dataType:'numeric(14,2)', hidden:false, nameEN:'Sum'},
      {pk:false, dbField:'currency_code', dataType:'varchar(3)', hidden:false, nameEN:'Currency Code'},
      {pk:false, dbField:'line_comment', dataType:'text', hidden:false, nameEN:'Line Comment'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>order_details</code> — те же строки, что и «Состав заказа» в приложении. <code>sku_id</code> расшифрован в <code>sku_code</code>/<code>sku_name</code> (+ <code>unit_name</code>), <code>currency_id</code> → <code>currency_code</code>. <code>sum</code> не хранится — вычисляется как <code>ordered_qty × price</code>.</div>
      <div class="hint" style="margin-bottom:16px;">Строго соответствует реальной таблице «Состав заказа» в приложении (<code>orderCompositionColumnDefs</code>): <code>sku_id</code> расшифрован в <code>sku_code</code>/<code>sku_name</code>, <code>currency_id</code> → <code>currency_code</code>, добавлена единица измерения <code>unit_name</code>. <code>sum</code> не хранится — вычисляется как <code>ordered_qty × price</code>.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vTransferLines'){
    const rows = [
      {pk:false, fk:'transfers', dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'sku_code', dataType:'varchar(50)', hidden:false, nameEN:'SKU Commercial Code'},
      {pk:false, dbField:'sku_name', dataType:'varchar(255)', hidden:false, nameEN:'SKU Name'},
      {pk:false, dbField:'unit_name', dataType:'varchar(50)', hidden:false, nameEN:'Unit'},
      {pk:false, dbField:'qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity'},
      {pk:false, dbField:'qty_wms', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity (WMS)'},
      {pk:false, dbField:'line_comment', dataType:'text', hidden:false, nameEN:'Line Comment'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>transfer_details</code> — те же строки, что и «Состав перемещения» в приложении. <code>sku_id</code> расшифрован в <code>sku_code</code>/<code>sku_name</code> (+ <code>unit_name</code>). У перемещений (в отличие от поступлений/заказов) нет ни цены/валюты, ни упаковки — этих полей в таблице просто нет.</div>
      <div class="hint" style="margin-bottom:16px;">Строго соответствует реальной таблице «Состав перемещения» в приложении (<code>transferCompositionColumnDefs</code>): <code>sku_id</code> расшифрован в <code>sku_code</code>/<code>sku_name</code>, добавлена единица измерения. У перемещений (в отличие от поступлений/заказов) нет ни цены/валюты, ни упаковки, ни статуса строки — этих полей в реальной таблице просто не существует.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vTransferLineDetails'){
    const rows = [
      {pk:false, fk:'transfers', dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'line_number', dataType:'smallint', hidden:false, nameEN:'Line Number'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, dbField:'quality', dataType:'varchar(20)', hidden:false, nameEN:'Quality'},
      {pk:false, dbField:'batch_number', dataType:'varchar(50)', hidden:false, nameEN:'Batch Number'},
      {pk:false, dbField:'series_number', dataType:'varchar(50)', hidden:false, nameEN:'Series Number'},
      {pk:false, dbField:'expiry_date', dataType:'date', hidden:false, nameEN:'Expiry Date'},
      {pk:false, dbField:'qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity (this batch)'},
      ...Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)})),
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>transfer_details</code> (та же таблица, что и <code>v_transfer_lines</code>) — просто другой набор колонок той же строки: качество, партия, серия, срок годности, количество.</div>
      <div class="hint" style="margin-bottom:16px;">Строго соответствует реальной таблице «Детализация» строки перемещения (<code>transferLineDetailColumnDefs</code>): качество, партия, серия, срок годности и количество по этой партии.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vStockAvailable'){
    const rows = [
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:false, nameEN:'SKU ID'},
      {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Warehouse ID'},
      {pk:false, dbField:'quantity_total', dataType:'numeric(14,3)', hidden:false, nameEN:'Total Quantity'},
      {pk:false, dbField:'reserved_total', dataType:'numeric(14,3)', hidden:false, nameEN:'Total Reserved'},
      {pk:false, dbField:'blocked_total', dataType:'numeric(14,3)', hidden:false, nameEN:'Total Blocked'},
      {pk:false, dbField:'available_total', dataType:'numeric(14,3)', hidden:false, nameEN:'Total Available'},
      {pk:false, dbField:'incoming_total', dataType:'numeric(14,3)', hidden:false, nameEN:'Total Incoming'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_stock_available</code></div>
      <div class="hint" style="margin-bottom:16px;">Агрегация регистра <code>stock</code> до уровня товар + склад. <code>available_total</code> — сумма по строкам, где <code>quality = 'Годен'</code> и <code>block_reason IS NULL</code>, за вычетом резерва. <code>incoming_total</code> — сумма поля «Ожидается» по строкам таблицы <code>receipt_details</code>, у которых заголовок <code>receipts</code> имеет статус, отличный от «Черновик» и «Принято» (то есть поступление уже подтверждено клиентом, но ещё не полностью принято на складе). Логика расчёта хранится в одном месте (в самом представлении), а не дублируется в каждом отчёте и на каждой странице приложения.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vUserAccess'){
    const rows = [
      {pk:false, fk:'user', dbField:'user_id', dataType:'uuid', hidden:false, nameEN:'User ID'},
      {pk:false, dbField:'login', dataType:'varchar(150)', hidden:false, nameEN:'Login'},
      {pk:false, dbField:'full_name', dataType:'varchar(150)', hidden:false, nameEN:'Full Name'},
      {pk:false, dbField:'role_name', dataType:'varchar(100)', hidden:false, nameEN:'Role'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Accessible Depositor ID'},
      {pk:false, dbField:'depositor_name', dataType:'varchar(255)', hidden:false, nameEN:'Depositor Name'},
      {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Accessible Warehouse ID'},
      {pk:false, dbField:'warehouse_name', dataType:'varchar(100)', hidden:false, nameEN:'Warehouse Name'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_user_access</code></div>
      <div class="hint" style="margin-bottom:16px;">Сводка прав доступа пользователя: <code>JOIN</code> таблиц <code>user</code>, <code>user_depositor_access</code>, <code>user_warehouse_access</code> и справочника ролей — используется на экране администратора «Доступы», чтобы одним запросом показать, какие поклажедатели/склады доступны каждому пользователю, без сборки этого списка на лету в коде приложения.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vUsers'){
    const rows = [
      {pk:false, dbField:'user_id', dataType:'uuid', hidden:false, nameEN:'User ID'},
      {pk:false, dbField:'login', dataType:'varchar(150)', hidden:false, nameEN:'Login (Email)'},
      {pk:false, dbField:'full_name', dataType:'varchar(150)', hidden:false, nameEN:'Full Name'},
      {pk:false, dbField:'role_name', dataType:'varchar(100)', hidden:false, nameEN:'Role'},
      {pk:false, dbField:'position', dataType:'varchar(150)', hidden:false, nameEN:'Position'},
      {pk:false, dbField:'organization', dataType:'varchar(255)', hidden:false, nameEN:'Organization'},
      {pk:false, dbField:'phone', dataType:'varchar(30)', hidden:false, nameEN:'Phone'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active'},
      {pk:false, dbField:'last_login_at', dataType:'timestamp', hidden:false, nameEN:'Last Login At'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_users</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>user</code>, кроме <code>password_hash</code> (не должен попадать в отображаемые представления), — <code>role_id</code> заменён на читаемое значение <code>role_name</code>. Формирует таблицу «Пользователи».</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && (activeId === 'vSuppliers' || activeId === 'vConsignees')){
    const isConsignee = activeId === 'vConsignees';
    const rows = [
      {pk:false, dbField: isConsignee ? 'consignee_id' : 'supplier_id', dataType:'uuid', hidden:false, nameEN: isConsignee ? 'Consignee ID' : 'Supplier ID'},
      {pk:false, dbField:'depositor_name', dataType:'varchar(255)', hidden:false, nameEN:'Depositor Name'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code'},
      {pk:false, dbField:'name', dataType:'varchar(255)', hidden:false, nameEN:'Name'},
      {pk:false, dbField:'bin', dataType:'varchar(12)', hidden:false, nameEN:'BIN'},
      {pk:false, dbField:'legal_address', dataType:'varchar(255)', hidden:false, nameEN:'Legal Address'},
      {pk:false, dbField:'contact_name', dataType:'varchar(150)', hidden:false, nameEN:'Contact Person'},
      {pk:false, dbField:'contact_phone', dataType:'varchar(30)', hidden:false, nameEN:'Contact Phone'},
      {pk:false, dbField:'contact_email', dataType:'varchar(150)', hidden:false, nameEN:'Contact Email'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>${isConsignee ? 'v_consignees' : 'v_suppliers'}</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>${isConsignee ? 'consignee' : 'supplier'}</code> — <code>depositor_id</code> заменён на читаемое значение <code>depositor_name</code>. Формирует таблицу «${isConsignee ? 'Грузополучатели' : 'Поставщики'}».</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vWarehouses'){
    const rows = [
      {pk:false, dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Warehouse ID'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code'},
      {pk:false, dbField:'name', dataType:'varchar(100)', hidden:false, nameEN:'Name'},
      {pk:false, dbField:'address', dataType:'varchar(255)', hidden:false, nameEN:'Address'},
      {pk:false, dbField:'responsible', dataType:'varchar(150)', hidden:false, nameEN:'Responsible Person'},
      {pk:false, dbField:'phone', dataType:'varchar(30)', hidden:false, nameEN:'Phone'},
      {pk:false, dbField:'email', dataType:'varchar(150)', hidden:false, nameEN:'Email'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_warehouses</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>warehouse</code> как есть — ссылочных полей нет, склады не привязаны к поклажедателю. Формирует таблицу «Склады».</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vDepositors'){
    const rows = [
      {pk:false, dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code'},
      {pk:false, dbField:'org_name', dataType:'varchar(255)', hidden:false, nameEN:'Organization Name'},
      {pk:false, dbField:'bin', dataType:'varchar(12)', hidden:false, nameEN:'BIN'},
      {pk:false, dbField:'legal_address', dataType:'varchar(255)', hidden:false, nameEN:'Legal Address'},
      {pk:false, dbField:'phone', dataType:'varchar(30)', hidden:false, nameEN:'Phone'},
      {pk:false, dbField:'email', dataType:'varchar(150)', hidden:false, nameEN:'Email'},
      {pk:false, dbField:'contract_number', dataType:'varchar(50)', hidden:false, nameEN:'Contract Number'},
      {pk:false, dbField:'contract_valid_until', dataType:'date', hidden:false, nameEN:'Contract Valid Until'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_depositors</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>depositor</code> как есть — это верхнеуровневая сущность, ссылочных полей нет. Формирует таблицу «Поклажедатели».</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vServiceTariffs'){
    const rows = [
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, dbField:'depositor_name', dataType:'varchar(255)', hidden:false, nameEN:'Depositor Name'},
      {pk:false, dbField:'service', dataType:'varchar(255)', hidden:false, nameEN:'Service'},
      {pk:false, dbField:'rate', dataType:'numeric(14,2)', hidden:false, nameEN:'Rate'},
      {pk:false, dbField:'unit', dataType:'varchar(100)', hidden:false, nameEN:'Unit'},
      {pk:false, dbField:'frequency', dataType:'varchar(50)', hidden:false, nameEN:'Frequency'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_service_tariffs</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>service_tariff</code> — <code>depositor_id</code> дополнен читаемым <code>depositor_name</code>. Формирует таблицу «Перечень оказываемых услуг с тарифами» на вкладке «Биллинг» — фильтруется по текущему поклажедателю.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vDocumentPhotos'){
    const rows = [
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, dbField:'doc_id', dataType:'uuid', hidden:false, nameEN:'Document ID'},
      {pk:false, dbField:'doc_kind', dataType:'varchar(20)', hidden:false, nameEN:'Document Kind'},
      {pk:false, dbField:'photo_id', dataType:'bigint', hidden:false, nameEN:'Photo ID'},
      {pk:false, dbField:'file_name', dataType:'varchar(255)', hidden:false, nameEN:'Original File Name'},
      {pk:false, dbField:'caption', dataType:'varchar(255)', hidden:false, nameEN:'Caption'},
      {pk:false, dbField:'source', dataType:'varchar(20)', hidden:false, nameEN:'Source'},
      {pk:false, dbField:'uploaded_by_name', dataType:'varchar(150)', hidden:false, nameEN:'Uploaded By'},
      {pk:false, dbField:'uploaded_at', dataType:'timestamp', hidden:false, nameEN:'Uploaded At'},
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_document_photos</code></div>
      <div class="hint" style="margin-bottom:16px;">Строится на основе таблицы <code>document_photo</code>. <code>object_type_id</code> расшифрован в <code>doc_kind</code>, <code>uploaded_by</code> → <code>uploaded_by_name</code>. Формирует вкладку «Фотографии» в карточке документа.</div>
      <div class="hint" style="margin-bottom:16px;"><b>Обратите внимание:</b> <code>object_key</code> в представление намеренно не выводится — путь во внутреннем хранилище не должен уходить на клиент. Вместо него приложение отдаёт подписанную временную ссылку, сгенерированную по ключу в момент запроса.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'views' && activeId === 'vInvoices'){
    const rows = [
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:false, nameEN:'Depositor ID'},
      {pk:false, dbField:'depositor_name', dataType:'varchar(255)', hidden:false, nameEN:'Depositor Name'},
      {pk:false, dbField:'invoice_number', dataType:'varchar(30)', hidden:false, nameEN:'Invoice Number'},
      {pk:false, dbField:'period_from', dataType:'date', hidden:false, nameEN:'Period From'},
      {pk:false, dbField:'period_to', dataType:'date', hidden:false, nameEN:'Period To'},
      {pk:false, dbField:'issue_date', dataType:'date', hidden:false, nameEN:'Issue Date'},
      {pk:false, dbField:'amount_no_vat', dataType:'numeric(14,2)', hidden:false, nameEN:'Amount, No VAT'},
      {pk:false, dbField:'amount_vat', dataType:'numeric(14,2)', hidden:false, nameEN:'Amount, VAT Included'},
      {pk:false, dbField:'status_name', dataType:'varchar(100)', hidden:false, nameEN:'Status'},
      {pk:false, dbField:'pdf_file', dataType:'varchar(255)', hidden:false, nameEN:'PDF File Path'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Представление: <code>v_invoices</code></div>
      <div class="hint" style="margin-bottom:16px;">Все поля таблицы <code>invoice</code> — <code>depositor_id</code> дополнен <code>depositor_name</code>, <code>status_id</code> заменён на <code>status_name</code>. Формирует таблицу «Счета, выставленные бухгалтерией» на вкладке «Биллинг» — фильтруется по текущему поклажедателю.</div>
      <div class="section-title">Колонки представления</div>
      ${renderDevFieldTable(rows, {hideWms:true})}
    `;
    return;
  }
  if(devActiveMainTab === 'registers' && activeId === 'services'){
    const rows = [
      {pk:true, dbField:'id', dataType:'bigint', hidden:true, nameEN:'Record ID'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID'},
      {pk:false, fk:'receipts / orders / transfers', dbField:'doc_id', dataType:'uuid', hidden:true, nameEN:'Document ID'},
      {pk:false, dbField:'service', dataType:'varchar(255)', hidden:false, nameEN:'Service'},
      {pk:false, dbField:'qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity'},
      {pk:false, dbField:'cost', dataType:'numeric(14,2)', hidden:false, nameEN:'Cost'},
      {pk:false, dbField:'sum', dataType:'numeric(14,2)', hidden:false, nameEN:'Sum'},
      {pk:false, dbField:'markup_percent', dataType:'numeric(6,2)', hidden:false, nameEN:'Markup %'},
      {pk:false, dbField:'sum_with_markup', dataType:'numeric(14,2)', hidden:false, nameEN:'Sum With Markup'},
      {pk:false, dbField:'vat_amount', dataType:'numeric(14,2)', hidden:false, nameEN:'VAT Amount'},
      {pk:false, dbField:'sum_with_vat', dataType:'numeric(14,2)', hidden:false, nameEN:'Sum With VAT'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>registered_service</code></div>
      <div class="hint" style="margin-bottom:16px;">Регистр зарегистрированных услуг — одна строка на услугу, оказанную по конкретному документу (приёмка, отгрузка, перемещение). Документ определяется полем <code>doc_id</code> — ссылается на заголовок любого из трёх типов документов. <code>vat_amount</code> = <code>sum_with_markup</code> × ставка НДС (12%), <code>sum_with_vat</code> = <code>sum_with_markup</code> + <code>vat_amount</code>.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(devActiveMainTab === 'registers' && activeId === 'serviceTariffs'){
    const rows = [
      {pk:true, dbField:'id', dataType:'bigint', hidden:true, nameEN:'Record ID'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID'},
      {pk:false, dbField:'service', dataType:'varchar(255)', hidden:false, nameEN:'Service'},
      {pk:false, dbField:'rate', dataType:'numeric(14,2)', hidden:false, nameEN:'Rate'},
      {pk:false, dbField:'unit', dataType:'varchar(100)', hidden:false, nameEN:'Unit'},
      {pk:false, dbField:'frequency', dataType:'varchar(50)', hidden:false, nameEN:'Frequency'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>service_tariff</code></div>
      <div class="hint" style="margin-bottom:16px;">Прейскурант — перечень оказываемых услуг с тарифами, индивидуально по каждому поклажедателю (одни и те же услуги могут стоить по-разному для разных клиентов по договору). Показывается на вкладке «Биллинг» → «Перечень оказываемых услуг с тарифами». В отличие от <code>registered_service</code> (факт оказания услуги по конкретному документу), это справочник цен, не привязанный к документам.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(devActiveMainTab === 'registers' && activeId === 'documentPhotos'){
    const rows = [
      {pk:true, dbField:'id', dataType:'bigint', hidden:true, nameEN:'Record ID'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID'},
      {pk:false, fk:'receipts / orders / transfers', dbField:'doc_id', dataType:'uuid', hidden:true, nameEN:'Document ID'},
      {pk:false, fk:'enum_object_type', dbField:'object_type_id', dataType:'smallint', hidden:true, nameEN:'Document Kind'},
      {pk:false, dbField:'object_key', dataType:'varchar(500)', hidden:true, nameEN:'Storage Object Key'},
      {pk:false, dbField:'file_name', dataType:'varchar(255)', hidden:false, nameEN:'Original File Name'},
      {pk:false, dbField:'caption', dataType:'varchar(255)', hidden:false, nameEN:'Caption'},
      {pk:false, dbField:'mime_type', dataType:'varchar(100)', hidden:true, nameEN:'MIME Type'},
      {pk:false, dbField:'file_size', dataType:'bigint', hidden:true, nameEN:'File Size, bytes'},
      {pk:false, dbField:'source', dataType:'varchar(20)', hidden:false, nameEN:"Source ('wms' / 'manual')"},
      {pk:false, fk:'user', dbField:'uploaded_by', dataType:'uuid', hidden:true, nameEN:'Uploaded By'},
      {pk:false, dbField:'uploaded_at', dataType:'timestamp', hidden:false, nameEN:'Uploaded At'},
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:true, nameEN:'Sort Order'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>document_photo</code></div>
      <div class="hint" style="margin-bottom:16px;"><b>Сами файлы в БД не хранятся</b> — они лежат в объектном хранилище (S3 / MinIO), а здесь только метаданные и <code>object_key</code> — путь к объекту внутри хранилища (например <code>depositors/rogaikopyta/receipts/2026/07/a3f9…jpg</code>).</div>
      <div class="hint" style="margin-bottom:16px;">Почему <code>object_key</code>, а не готовый URL: ссылка на приватный файл должна быть подписанной и временной (иначе по утёкшей ссылке фото увидит кто угодно), поэтому URL генерируется на лету при отдаче. Плюс при переезде хранилища или смене домена сохранённые URL стали бы нерабочими, а ключ остаётся валидным.</div>
      <div class="hint" style="margin-bottom:16px;">Документ определяется парой <code>doc_id</code> + <code>object_type_id</code> — та же схема полиморфной ссылки, что и в <code>registered_service</code>. <code>source</code> различает фото, пришедшие из WMS по интеграции, и загруженные пользователем вручную (последние можно удалять, первые — нет).</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(devActiveMainTab === 'registers' && activeId === 'invoices'){
    const rows = [
      {pk:true, dbField:'id', dataType:'bigint', hidden:true, nameEN:'Record ID'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID'},
      {pk:false, dbField:'invoice_number', dataType:'varchar(30)', hidden:false, nameEN:'Invoice Number'},
      {pk:false, dbField:'period_from', dataType:'date', hidden:false, nameEN:'Period From'},
      {pk:false, dbField:'period_to', dataType:'date', hidden:false, nameEN:'Period To'},
      {pk:false, dbField:'issue_date', dataType:'date', hidden:false, nameEN:'Issue Date'},
      {pk:false, dbField:'amount_no_vat', dataType:'numeric(14,2)', hidden:false, nameEN:'Amount, No VAT'},
      {pk:false, dbField:'amount_vat', dataType:'numeric(14,2)', hidden:false, nameEN:'Amount, VAT Included'},
      {pk:false, fk:'enum_status', dbField:'status_id', dataType:'int', hidden:true, nameEN:'Status ID'},
      {pk:false, dbField:'pdf_file', dataType:'varchar(255)', hidden:false, nameEN:'PDF File Path'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>invoice</code></div>
      <div class="hint" style="margin-bottom:16px;">Счета, выставленные бухгалтерией поклажедателю за оказанные за период услуги — обычно формируются на основе строк <code>registered_service</code> за соответствующий период, но хранятся отдельным документом со своим статусом оплаты. Показывается на вкладке «Биллинг» → «Счета, выставленные бухгалтерией».</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(devActiveMainTab === 'registers' && activeId === 'stock'){
    const rows = [
      {pk:true, dbField:'register_id', dataType:'uuid', hidden:true, nameEN:'Register Record ID', nameRU:'ID записи регистра'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID депозитора'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:true, nameEN:'SKU ID', nameRU:'ID SKU'},
      {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:false, nameEN:'Warehouse ID', nameRU:'Склад'},
      {pk:false, fk:'enum_uom', dbField:'unit_id', dataType:'smallint', hidden:true, nameEN:'Unit ID', nameRU:'ID единицы измерения'},
      {pk:false, dbField:'batch_number', dataType:'varchar(50)', hidden:false, nameEN:'Batch Number', nameRU:'Партия'},
      {pk:false, dbField:'series_number', dataType:'varchar(50)', hidden:false, nameEN:'Series Number', nameRU:'Серия'},
      {pk:false, dbField:'expiry_date', dataType:'date', hidden:false, nameEN:'Expiry Date', nameRU:'Срок годности'},
      {pk:false, dbField:'quality', dataType:'varchar(20)', hidden:false, nameEN:'Quality', nameRU:'Качество'},
      {pk:false, fk:'enum_block_reason', dbField:'block_reason_id', dataType:'smallint', hidden:true, nameEN:'Block Reason ID', nameRU:'ID причины блокировки'},
      {pk:false, dbField:'quantity', dataType:'numeric(14,3)', hidden:false, nameEN:'Quantity On Hand', nameRU:'Наличие'},
      {pk:false, dbField:'reserved_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Reserved Quantity', nameRU:'Резерв'},
      {pk:false, dbField:'blocked_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Blocked Quantity', nameRU:'Заблокировано'},
      {pk:false, dbField:'available_qty', dataType:'numeric(14,3)', hidden:false, nameEN:'Available Quantity', nameRU:'Доступное количество'},
      {pk:false, dbField:'updated_at', dataType:'timestamp', hidden:false, nameEN:'Updated At', nameRU:'Обновлено'},
      ...Array.from({length:10}, (_,i)=>({pk:false, dbField:'custom_field_'+(i+1), dataType:'varchar(255)', hidden:false, nameEN:'Custom Field '+(i+1)})),
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>stock</code></div>
      <div class="hint" style="margin-bottom:16px;">Регистр накопления остатков — одна строка на уникальное сочетание товар + склад + партия + серия + качество + причина блокировки. «Наличие» суммируется до карточки товара, «Доступно» рассчитывается как остаток с качеством «Годен» и без причины блокировки, за вычетом резерва.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'sku'){
    const NORMALIZED_FIELDS = new Set(['name','unit','hazardClass','hazardGroup','group','productType','currency','orderUnit','statusProduct']);
    const defsNoName = skuColumnDefs.filter(f=>!NORMALIZED_FIELDS.has(f.id));
    const rows = buildFieldTableRows(defsNoName, [
      {pk:true, dbField:'sku_id', dataType:'uuid', hidden:true, nameEN:'SKU ID', nameRU:'ID SKU'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID депозитора'},
    ]);
    const codeIdx = rows.findIndex(r=>r.dbField === 'code');
    rows.splice(codeIdx + 1, 0, {pk:false, dbField:'name', dataType:'varchar(255)', hidden:false, nameEN:'Item Name', nameRU:'Наименование товара'});
    // Нормализация: заменяем свободный текст ссылками на справочники
    const barcodeIdx = rows.findIndex(r=>r.dbField === 'barcode');
    rows.splice(barcodeIdx + 1, 0,
      {pk:false, fk:'enum_uom', dbField:'uom_id', dataType:'smallint', hidden:true, nameEN:'Unit of Measure ID', nameRU:'ID единицы измерения'},
      {pk:false, fk:'product_packaging', dbField:'order_unit_id', dataType:'uuid', hidden:true, nameEN:'Order Unit ID', nameRU:'ID единицы заказа'}
    );
    const typeIdx = rows.findIndex(r=>r.dbField === 'item_type' || r.dbField === 'product_type');
    rows.splice((typeIdx>-1?typeIdx:rows.length-1) + 1, 0,
      {pk:false, fk:'product_group', dbField:'item_group_id', dataType:'uuid', hidden:true, nameEN:'Item Group ID', nameRU:'ID группы товара'},
      {pk:false, fk:'product_type', dbField:'item_type_id', dataType:'uuid', hidden:true, nameEN:'Item Type ID', nameRU:'ID типа товара'}
    );
    rows.push(
      {pk:false, fk:'enum_hazard_class', dbField:'hazard_class_id', dataType:'smallint', hidden:true, nameEN:'Hazard Class ID', nameRU:'ID класса опасности'},
      {pk:false, fk:'enum_hazard_group', dbField:'hazard_group_id', dataType:'smallint', hidden:true, nameEN:'Hazard Group ID', nameRU:'ID группы опасности'},
      {pk:false, fk:'enum_currency', dbField:'currency_id', dataType:'smallint', hidden:true, nameEN:'Currency ID', nameRU:'ID валюты'},
      {pk:false, fk:'enum_status', dbField:'status_id', dataType:'int', hidden:true, nameEN:'Status ID', nameRU:'ID статуса (тип объекта = «Товар»)'}
    );
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>SKU</code></div>
      <div class="hint" style="margin-bottom:16px;">Таблица нормализована: единица измерения, класс и группа опасности, группа и тип товара, валюта — вынесены в справочники-перечисления и хранятся как FK, а не свободным текстом. Мультиязычность для этой таблицы намеренно не используется — <code>name</code> и <code>full_name</code> хранятся в одном языке, без записей в таблице <code>translation</code>.</div>
      <div class="section-title" style="margin-top:16px;">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'packaging'){
    const rows = [
      {pk:true, dbField:'packaging_id', dataType:'uuid', hidden:true, nameEN:'Packaging Record ID', nameRU:'ID записи упаковки'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID депозитора'},
      {pk:false, fk:'SKU', dbField:'sku_id', dataType:'uuid', hidden:true, nameEN:'SKU ID', nameRU:'ID SKU'},
      {pk:false, dbField:'packaging_code', dataType:'varchar(20)', hidden:false, nameEN:'Packaging Code', nameRU:'Код упаковки'},
      {pk:false, fk:'enum_packaging_type', dbField:'packaging_type_id', dataType:'smallint', hidden:true, nameEN:'Packaging Type ID', nameRU:'ID типа упаковки'},
      {pk:false, dbField:'barcode', dataType:'varchar(20)', hidden:false, nameEN:'Barcode', nameRU:'Штрих код упаковки'},
      {pk:false, dbField:'coefficient', dataType:'numeric(12,3)', hidden:false, nameEN:'Coefficient', nameRU:'Коэффициент'},
      {pk:false, dbField:'length_cm', dataType:'numeric(10,2)', hidden:false, nameEN:'Length', nameRU:'Длина'},
      {pk:false, dbField:'width_cm', dataType:'numeric(10,2)', hidden:false, nameEN:'Width', nameRU:'Ширина'},
      {pk:false, dbField:'height_cm', dataType:'numeric(10,2)', hidden:false, nameEN:'Height', nameRU:'Высота'},
      {pk:false, dbField:'volume_m3', dataType:'numeric(12,5)', hidden:false, nameEN:'Volume', nameRU:'Объём'},
      {pk:false, dbField:'gross_weight_kg', dataType:'numeric(12,3)', hidden:false, nameEN:'Gross Weight', nameRU:'Вес брутто'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>product_packaging</code></div>
      <div class="hint" style="margin-bottom:16px;">Одна строка — один вариант упаковки товара (у товара может быть несколько строк с разным коэффициентом). Тип упаковки — ссылка на справочник <code>enum_packaging_type</code> (общий для всех поклажедателей, см. «Типы упаковки»). Поле «Наименование» (например, «Короб (10шт)») в БД не хранится — формируется на лету по шаблону из типа упаковки, коэффициента и базовой единицы измерения товара.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && (activeId === 'suppliers' || activeId === 'consignees')){
    const isConsignee = activeId === 'consignees';
    const rows = [
      {pk:true, dbField: isConsignee ? 'consignee_id' : 'supplier_id', dataType:'uuid', hidden:true, nameEN: isConsignee ? 'Consignee ID' : 'Supplier ID', nameRU: isConsignee ? 'ID грузополучателя' : 'ID поставщика'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID депозитора'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code', nameRU:'Код'},
      ...fcSimpleNameField('name', isConsignee ? 'Наименование грузополучателя' : 'Наименование поставщика', 'Name'),
      {pk:false, dbField:'bin', dataType:'varchar(12)', hidden:false, nameEN:'BIN', nameRU:'БИН'},
      {pk:false, dbField:'legal_address', dataType:'varchar(255)', hidden:false, nameEN:'Legal Address', nameRU:'Юр. адрес'},
      {pk:false, dbField:'contact_name', dataType:'varchar(150)', hidden:false, nameEN:'Contact Person', nameRU:'Контактное лицо (ФИО)'},
      {pk:false, dbField:'contact_phone', dataType:'varchar(30)', hidden:false, nameEN:'Contact Phone', nameRU:'Телефон'},
      {pk:false, dbField:'contact_email', dataType:'varchar(150)', hidden:false, nameEN:'Contact Email', nameRU:'Email'},
    ];
    const extraHint = I18N_NOTE + (isConsignee ? `<div class="hint" style="margin-bottom:16px;">Адреса доставки — множественные, хранятся отдельным справочником <code>delivery_point</code> со связью к грузополучателю (см. подвкладку «Адреса доставки грузополучателей»).</div>` : '');
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>${isConsignee ? 'consignee' : 'supplier'}</code></div>
      ${extraHint}
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'consigneeAddresses'){
    const rows = [
      {pk:true, dbField:'address_id', dataType:'uuid', hidden:true, nameEN:'Address ID', nameRU:'ID адреса'},
      {pk:false, fk:'consignee', dbField:'consignee_id', dataType:'uuid', hidden:true, nameEN:'Consignee ID', nameRU:'ID грузополучателя (consignee)'},
      {pk:false, dbField:'address', dataType:'varchar(255)', hidden:false, nameEN:'Delivery Address', nameRU:'Адрес доставки'},
      {pk:false, dbField:'is_default', dataType:'boolean', hidden:false, nameEN:'Is Default', nameRU:'Основной адрес'},
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order', nameRU:'Порядок сортировки'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>delivery_point</code></div>
      <div class="hint" style="margin-bottom:16px;">У одного грузополучателя может быть несколько адресов доставки — связь один-ко-многим к справочнику <code>consignee</code>.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'depositors'){
    const rows = [
      {pk:true, dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID депозитора'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code', nameRU:'Код'},
      ...fcSimpleNameField('org_name', 'Наименование организации', 'Organization Name'),
      {pk:false, dbField:'bin', dataType:'varchar(12)', hidden:false, nameEN:'BIN', nameRU:'БИН организации'},
      {pk:false, dbField:'legal_address', dataType:'varchar(255)', hidden:false, nameEN:'Legal Address', nameRU:'Юр. адрес'},
      {pk:false, dbField:'phone', dataType:'varchar(30)', hidden:false, nameEN:'Phone', nameRU:'Телефон'},
      {pk:false, dbField:'email', dataType:'varchar(150)', hidden:false, nameEN:'Email', nameRU:'Email'},
      {pk:false, dbField:'contract_number', dataType:'varchar(50)', hidden:false, nameEN:'Contract Number', nameRU:'Номер договора'},
      {pk:false, dbField:'contract_valid_until', dataType:'date', hidden:false, nameEN:'Contract Valid Until', nameRU:'Срок действия договора'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>depositor</code></div>
      ${I18N_NOTE}
      <div class="section-title" style="margin-top:16px;">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'warehouses'){
    const rows = [
      {pk:true, dbField:'warehouse_id', dataType:'varchar(20)', hidden:true, nameEN:'Warehouse ID', nameRU:'ID склада'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code', nameRU:'Код'},
      ...fcSimpleNameField('name', 'Наименование склада', 'Name'),
      {pk:false, dbField:'address', dataType:'varchar(255)', hidden:false, nameEN:'Address', nameRU:'Адрес'},
      {pk:false, dbField:'responsible', dataType:'varchar(150)', hidden:false, nameEN:'Responsible Person', nameRU:'Ответственный'},
      {pk:false, dbField:'phone', dataType:'varchar(30)', hidden:false, nameEN:'Phone', nameRU:'Телефон'},
      {pk:false, dbField:'email', dataType:'varchar(150)', hidden:false, nameEN:'Email', nameRU:'Email'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>warehouse</code></div>
      ${I18N_NOTE}
      <div class="section-title" style="margin-top:16px;">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'users'){
    const rows = [
      {pk:true, dbField:'user_id', dataType:'uuid', hidden:true, nameEN:'User ID', nameRU:'ID пользователя'},
      {pk:false, dbField:'login', dataType:'varchar(150)', hidden:false, nameEN:'Login (Email)', nameRU:'Логин (Email)'},
      {pk:false, dbField:'password_hash', dataType:'varchar(255)', hidden:true, nameEN:'Password Hash', nameRU:'Хеш пароля'},
      {pk:false, dbField:'full_name', dataType:'varchar(150)', hidden:false, nameEN:'Full Name', nameRU:'ФИО'},
      {pk:false, fk:'enum_user_role', dbField:'role_id', dataType:'smallint', hidden:true, nameEN:'Role ID', nameRU:'ID роли'},
      {pk:false, dbField:'position', dataType:'varchar(150)', hidden:false, nameEN:'Position', nameRU:'Должность'},
      {pk:false, dbField:'organization', dataType:'varchar(255)', hidden:false, nameEN:'Organization', nameRU:'Организация'},
      {pk:false, dbField:'phone', dataType:'varchar(30)', hidden:false, nameEN:'Phone', nameRU:'Телефон'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active', nameRU:'Активен'},
      {pk:false, dbField:'last_login_at', dataType:'timestamp', hidden:false, nameEN:'Last Login At', nameRU:'Последний вход'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>user</code></div>
      <div class="hint" style="margin-bottom:16px;">Роль вынесена в справочник <code>enum_user_role</code>. Настройки отображения интерфейса — в отдельной таблице <code>user_settings</code> (см. подвкладку «Настройки пользователей»). Доступ к нескольким поклажедателям и складам — в справочниках <code>user_depositor_access</code> и <code>user_warehouse_access</code> (см. соответствующие подвкладки).</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'userRoles'){
    const rows = [
      {pk:true, dbField:'id', dataType:'smallint', hidden:true, nameEN:'ID'},
      {pk:false, dbField:'code', dataType:'varchar(20)', hidden:false, nameEN:'Code'},
      ...fcSimpleNameField('name', 'Наименование роли', 'Name'),
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active'},
    ];
    const ROLE_VALUES = [
      ['depositor', 'Поклажедатель'], ['manager', 'Менеджер 3PL-оператора'], ['staff', 'Складской сотрудник'],
      ['admin', 'Администратор'], ['developer', 'Разработчик'],
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>enum_user_role</code></div>
      ${I18N_NOTE}
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
      <div class="section-title" style="margin-top:24px;">Заполненные значения</div>
      <div class="table-card">
      <table>
      <thead><tr><th>${T('references.column.code')}</th><th>${T('references.column.name')}</th></tr></thead>
      <tbody>${ROLE_VALUES.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td></tr>`).join('')}</tbody>
      </table>
      </div>
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'userSettings'){
    const rows = [
      {pk:true, fk:'user', dbField:'user_id', dataType:'uuid', hidden:true, nameEN:'User ID', nameRU:'ID пользователя'},
      {pk:false, dbField:'hide_transfers_tab', dataType:'boolean', hidden:false, nameEN:'Hide Transfers Tab', nameRU:'Скрыть раздел «Перемещения»'},
      {pk:false, dbField:'hide_billing_tab', dataType:'boolean', hidden:false, nameEN:'Hide Billing Tab', nameRU:'Скрыть раздел «Биллинг»'},
      {pk:false, dbField:'hide_services_tab', dataType:'boolean', hidden:false, nameEN:'Hide Services Tab', nameRU:'Скрыть вкладку «Услуги»'},
      {pk:false, dbField:'can_create_receipts', dataType:'boolean', hidden:false, nameEN:'Can Create Receipts', nameRU:'Разрешено создание поступлений'},
      {pk:false, dbField:'can_create_orders', dataType:'boolean', hidden:false, nameEN:'Can Create Orders', nameRU:'Разрешено создание заказов на отгрузку'},
      {pk:false, dbField:'can_create_transfers', dataType:'boolean', hidden:false, nameEN:'Can Create Transfers', nameRU:'Разрешено создание перемещений'},
      {pk:false, dbField:'can_upload_sku_excel', dataType:'boolean', hidden:false, nameEN:'Can Upload SKU From Excel', nameRU:'Разрешена загрузка номенклатуры из Excel'},
      {pk:false, dbField:'can_upload_packaging_excel', dataType:'boolean', hidden:false, nameEN:'Can Upload Packaging From Excel', nameRU:'Разрешена загрузка упаковок из Excel'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>user_settings</code></div>
      <div class="hint" style="margin-bottom:16px;">Расширение таблицы <code>user</code> (связь один-к-одному по <code>user_id</code>) — персональные настройки отображения интерфейса, вынесенные отдельно, чтобы не раздувать основную таблицу пользователя редко используемыми и часто меняющимися полями.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'depositorFieldLabels'){
    const rows = [
      {pk:true, dbField:'id', dataType:'bigint', hidden:true, nameEN:'Record ID'},
      {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID'},
      {pk:false, fk:'enum_object_type', dbField:'object_type_id', dataType:'smallint', hidden:true, nameEN:'Object Type ID'},
      {pk:false, dbField:'field_id', dataType:'varchar(100)', hidden:false, nameEN:'Field ID'},
      {pk:false, dbField:'custom_label', dataType:'varchar(255)', hidden:false, nameEN:'Custom Field Label'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>depositor_field_label</code></div>
      <div class="hint" style="margin-bottom:16px;">Пользовательские названия полей таблиц/списков индивидуально под каждого поклажедателя. Уникальность: (<code>depositor_id</code>, <code>object_type_id</code>, <code>field_id</code>). Приоритет выше языковых настроек (<code>translation</code>) — при чтении заголовка колонки сначала проверяется эта таблица по текущему поклажедателю, и только если override не найден — берётся перевод/значение по умолчанию. Настраивается в разделе «Администрирование → Доступы поклажедателей» → карточка поклажедателя → вкладка «Пользовательское наименование полей».</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && (activeId === 'userDepositorAccess' || activeId === 'userWarehouseAccess')){
    const isDepositor = activeId === 'userDepositorAccess';
    const rows = [
      {pk:true, dbField:'id', dataType:'bigint', hidden:true, nameEN:'Record ID'},
      {pk:false, fk:'user', dbField:'user_id', dataType:'uuid', hidden:true, nameEN:'User ID', nameRU:'ID пользователя'},
      isDepositor
        ? {pk:false, fk:'depositor', dbField:'depositor_id', dataType:'uuid', hidden:true, nameEN:'Depositor ID', nameRU:'ID поклажедателя'}
        : {pk:false, fk:'warehouse', dbField:'warehouse_id', dataType:'varchar(20)', hidden:true, nameEN:'Warehouse ID', nameRU:'ID склада'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>user_${isDepositor?'depositor':'warehouse'}_access</code></div>
      <div class="hint" style="margin-bottom:16px;">Связь многие-ко-многим между пользователем и ${isDepositor?'поклажедателями':'складами'}, к которым у него есть доступ (например, у менеджера может быть доступ к нескольким поклажедателям, у кладовщика — к нескольким складам).</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'statuses'){
    const rows = [
      {pk:true, dbField:'id', dataType:'int', hidden:true, nameEN:'ID', nameRU:'ID'},
      {pk:false, fk:'enum_object_type', dbField:'object_type_id', dataType:'smallint', hidden:true, nameEN:'Object Type ID', nameRU:'ID типа объекта'},
      {pk:false, dbField:'code', dataType:'varchar(30)', hidden:false, nameEN:'Code', nameRU:'Код'},
      ...fcSimpleNameField('name', 'Наименование', 'Name'),
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order', nameRU:'Порядок сортировки'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active', nameRU:'Активен'},
    ];
    const SAMPLE_STATUSES = {
      'Поступление': ['Черновик','Ожидает приёмки','В работе','Принято','Расхождение по кол-ву'],
      'Заказ': ['Черновик','В резерве','Комплектуется','Отгружен','Задержка отгрузки'],
      'Перемещение': ['Черновик','Формируется','В пути','Принято','Ошибка формирования'],
      'Товар': ['Активен','Неактивен'],
      'Упаковка': ['Активна','Неактивна'],
    };
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>enum_status</code></div>
      ${I18N_NOTE}
      <div class="hint" style="margin-bottom:16px;">Общий справочник статусов для всех объектов системы. У разных объектов (документов и справочников) — разные наборы статусов, поэтому набор значений разбивается полем «Тип объекта» (ссылка на справочник <code>enum_object_type</code>: «Поступление», «Заказ», «Перемещение», «Товар», «Упаковка» и т.д.).</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
      <div class="section-title" style="margin-top:24px;">Заполненные значения (по типу объекта)</div>
      <div class="table-card">
      <table>
      <thead><tr><th>Тип объекта</th><th>Статусы</th></tr></thead>
      <tbody>${Object.keys(SAMPLE_STATUSES).map(k=>`<tr><td>${k}</td><td>${SAMPLE_STATUSES[k].join(', ')}</td></tr>`).join('')}</tbody>
      </table>
      </div>
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'locales'){
    const rows = [
      {pk:true, dbField:'locale_id', dataType:'smallint', hidden:true, nameEN:'Locale ID', nameRU:'ID языка'},
      {pk:false, dbField:'code', dataType:'varchar(10)', hidden:false, nameEN:'Locale Code', nameRU:'Код языка (ISO)'},
      {pk:false, dbField:'name', dataType:'varchar(50)', hidden:false, nameEN:'Language Name', nameRU:'Наименование языка'},
      {pk:false, dbField:'is_default', dataType:'boolean', hidden:false, nameEN:'Is Default', nameRU:'Язык по умолчанию'},
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order', nameRU:'Порядок сортировки'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active', nameRU:'Активен'},
    ];
    const SAMPLE_LOCALES = [['ru','Русский','Да'],['en','English','Нет'],['kk','Қазақша','Нет']];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>dictionary_locale</code></div>
      <div class="hint" style="margin-bottom:16px;">Справочник поддерживаемых языков системы. Добавление нового языка — просто новая строка здесь, без изменения структуры других таблиц.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
      <div class="section-title" style="margin-top:24px;">Заполненные значения</div>
      <div class="table-card">
      <table>
      <thead><tr><th>${T('references.column.code')}</th><th>${T('references.column.name')}</th><th>По умолчанию</th></tr></thead>
      <tbody>${SAMPLE_LOCALES.map(l=>`<tr><td>${l[0]}</td><td>${l[1]}</td><td>${l[2]}</td></tr>`).join('')}</tbody>
      </table>
      </div>
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'translations'){
    const rows = [
      {pk:true, dbField:'translation_id', dataType:'bigint', hidden:true, nameEN:'Translation ID', nameRU:'ID записи перевода'},
      {pk:false, fk:'enum_object_type', dbField:'entity_type_id', dataType:'smallint', hidden:false, nameEN:'Entity Type ID', nameRU:'ID типа сущности'},
      {pk:false, dbField:'entity_id', dataType:'varchar(64)', hidden:false, nameEN:'Entity ID', nameRU:'ID записи (произвольной таблицы)'},
      {pk:false, dbField:'field_name', dataType:'varchar(50)', hidden:false, nameEN:'Field Name', nameRU:'Название переводимого поля'},
      {pk:false, fk:'dictionary_locale', dbField:'locale_id', dataType:'smallint', hidden:false, nameEN:'Locale ID', nameRU:'ID языка'},
      {pk:false, dbField:'value', dataType:'text', hidden:false, nameEN:'Translated Value', nameRU:'Переведённое значение'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>translation</code></div>
      <div class="hint" style="margin-bottom:16px;">Единая таблица переводов для всей системы. Базовое (языком по умолчанию, обычно RU) значение хранится прямо в поле сущности (например, <code>supplier.name</code>); переводы на остальные языки — здесь, по ключу «тип сущности + ID записи + название поля + язык». Уникальность: (<code>entity_type_id</code>, <code>entity_id</code>, <code>field_name</code>, <code>locale_id</code>). Нет физического FK на <code>entity_id</code>, так как он ссылается то на одну, то на другую таблицу — целостность поддерживается на уровне приложения.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'objectTypes'){
    const rows = [
      {pk:true, dbField:'id', dataType:'smallint', hidden:true, nameEN:'ID'},
      {pk:false, dbField:'code', dataType:'varchar(30)', hidden:false, nameEN:'Code'},
      ...fcSimpleNameField('name', 'Наименование', 'Name'),
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active'},
    ];
    const OBJECT_TYPE_VALUES = [
      ['receipts','Поступление'], ['orders','Заказ'], ['transfers','Перемещение'],
      ['sku','Товар'], ['packaging','Упаковка'], ['depositor','Поклажедатель'],
      ['supplier','Поставщик'], ['consignee','Грузополучатель'], ['warehouse','Склад'], ['user','Пользователь'],
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>enum_object_type</code></div>
      ${I18N_NOTE}
      <div class="hint" style="margin-bottom:16px;">Справочник типов объектов системы — используется как FK-цель там, где одна таблица ссылается «то на одну, то на другую» сущность: <code>translation.entity_type_id</code> (какую сущность переводим) и <code>enum_status.object_type_id</code> (у какого объекта этот статус).</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
      <div class="section-title" style="margin-top:24px;">Заполненные значения</div>
      <div class="table-card">
      <table>
      <thead><tr><th>${T('references.column.code')}</th><th>${T('references.column.name')}</th></tr></thead>
      <tbody>${OBJECT_TYPE_VALUES.map(v=>`<tr><td>${v[0]}</td><td>${v[1]}</td></tr>`).join('')}</tbody>
      </table>
      </div>
    `;
    return;
  }
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && activeId === 'units'){
    const rows = [
      {pk:true, dbField:'id', dataType:'smallint', hidden:true, nameEN:'ID', nameRU:'ID'},
      {pk:false, dbField:'mkei_code', dataType:'varchar(3)', hidden:false, nameEN:'MKEI Code', nameRU:'Код МКЕИ'},
      {pk:false, dbField:'symbol', dataType:'varchar(10)', hidden:false, nameEN:'Symbol', nameRU:'Условное обозначение'},
      ...fcSimpleNameField('name', 'Полное наименование', 'Full Name'),
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order', nameRU:'Порядок сортировки'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active', nameRU:'Активна'},
    ];
    const MKEI_UNITS = [
      ['796','шт','Штука'], ['163','г','Грамм'], ['166','кг','Килограмм'], ['168','т','Тонна'],
      ['112','л','Литр'], ['006','м','Метр'], ['055','м²','Метр квадратный'], ['113','м³','Метр кубический'],
      ['704','пар','Пара'], ['778','уп','Упаковка'], ['839','компл','Комплект'], ['736','рул','Рулон'],
      ['715','меш','Мешок'], ['977','тюк','Тюк'], ['984','кор','Короб'], ['719','канистра','Канистра'],
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">Название таблицы в БД: <code>enum_uom</code></div>
      ${I18N_NOTE}
      <div class="hint" style="margin-bottom:16px;">Справочник единиц измерения по МКЕИ (общий, без привязки к поклажедателю). Заполнен наиболее часто используемыми в системе значениями.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
      <div class="section-title" style="margin-top:24px;">Заполненные значения</div>
      <div class="table-card">
      <table>
      <thead><tr><th>Код МКЕИ</th><th>Условное обозначение</th><th>${T('common.text.full_legal_name')}</th></tr></thead>
      <tbody>${MKEI_UNITS.map(u=>`<tr><td>${u[0]}</td><td>${u[1]}</td><td>${u[2]}</td></tr>`).join('')}</tbody>
      </table>
      </div>
    `;
    return;
  }
  const DEV_ENUM_TABLES = {
    orderTypes: {table:'enum_order_type', title:T('references.button.order_types')},
    receiptDocTypes: {table:'enum_receipt_doc_type', title:'Типы приходных документов'},
    blockReasons: {table:'enum_block_reason', title:'Причины блокировки'},
    packagingTypes: {table:'enum_packaging_type', title:'Типы упаковки'},
    currencies: {table:'enum_currency', title:'Валюты'},
    hazardClasses: {table:'enum_hazard_class', title:'Классы опасности'},
    hazardGroups: {table:'enum_hazard_group', title:'Группы опасности'},
  };
  if(['directories','auxDirectories','enums'].includes(devActiveMainTab) && DEV_ENUM_TABLES[activeId]){
    const meta = DEV_ENUM_TABLES[activeId];
    const rows = [
      {pk:true, dbField:'id', dataType:'smallint', hidden:true, nameEN:'ID', nameRU:'ID'},
      {pk:false, dbField:'code', dataType:'varchar(30)', hidden:false, nameEN:'Code', nameRU:'Код'},
      ...fcSimpleNameField('name', 'Наименование', 'Name'),
      {pk:false, dbField:'sort_order', dataType:'smallint', hidden:false, nameEN:'Sort Order', nameRU:'Порядок сортировки'},
      {pk:false, dbField:'is_active', dataType:'boolean', hidden:false, nameEN:'Is Active', nameRU:'Активна'},
    ];
    document.getElementById('devSettingsContent').innerHTML = `
      <div class="section-title" style="margin-top:0;">${meta.title} · Название таблицы в БД: <code>${meta.table}</code></div>
      ${I18N_NOTE}
      <div class="hint" style="margin-bottom:16px;">Простой справочник-перечисление: фиксированный набор значений, на который ссылаются другие таблицы по коду.</div>
      <div class="section-title">Поля таблицы</div>
      ${renderDevFieldTable(rows)}
    `;
    return;
  }
  document.getElementById('devSettingsContent').innerHTML = `
    <div class="section-title" style="margin-top:0;">${group.label} → ${item.label}</div>
    <div class="hint">Структура для этого справочника будет описана на следующем шаге.</div>
  `;
}
let currentStockDetailCode = null;
function openProduct(code){
  const p = products.find(x=>x.code===code);
  if(!p) return;
  currentStockDetailCode = code;
  const batches = groupBatchRows(mockBatches(p));
  const totalBlocked = batches.reduce((sum,b)=>sum + b.blocked, 0);
  const totalAvailable = batches.reduce((sum,b)=>sum + b.available, 0);
  const L = (label, fieldId) => localizeColumnLabel(getDepositorFieldLabel('stock', fieldId) || label);
  document.getElementById('productDetailHead').innerHTML = `
    <div>
      <h2>${p.name}</h2>
      <div class="meta">Код: ${p.code} · Группа: ${p.group}</div>
      <div class="kv-grid">
        <div class="kv"><label>${L('Наличие', 'stock')}</label><div>${p.stock} ${p.unit}</div></div>
        <div class="kv"><label>${L('Резерв', 'reserved')}</label><div>${p.reserved} ${p.unit}</div></div>
        <div class="kv"><label>${L('Брак', 'defect')}</label><div>${p.defect} ${p.unit}</div></div>
        <div class="kv"><label>${L('Заблокировано', 'blocked')}</label><div>${totalBlocked} ${p.unit}</div></div>
        <div class="kv"><label>${L('Доступное количество', 'available')}</label><div>${totalAvailable} ${p.unit}</div></div>
      </div>
    </div>
    <div>${badge(p.defect>0?'warn':'ok', p.defect>0?'Есть брак':'Без замечаний')}</div>
  `;
  renderStockBatchTable();
  showView('product-detail');
}
function renderStockBatchTable(){
  const p = products.find(x=>x.code===currentStockDetailCode);
  if(!p) return;
  const batches = groupBatchRows(mockBatches(p));
  const cols = tableColumns.stockBatch;
  const order = tableColumnState.stockBatch;
  const hidden = tableColumnHidden.stockBatch;
  const visibleCols = order.filter(id=>!hidden.has(id)).map(id=>cols.find(c=>c.id===id));
  document.getElementById('stockBatchHead').innerHTML = visibleCols.map(c=>`<th${c.num?' class="num"':''}>${typeof COLTH === 'function' ? COLTH(c) : c.label}</th>`).join('');
  document.getElementById('productDetailBatches').innerHTML = batches.map(b=>`
    <tr>${visibleCols.map(c=>`<td${c.num?' class="num"':''}>${c.render(b)}</td>`).join('')}</tr>
  `).join('');
}

/* ============== Генераторы фиктивных подстрок ============== */
function mockReceiptLines(n, isDraft){
  const pool = products.slice(0, Math.max(n,3));
  return pool.slice(0,n).map(p=>{
    const expected = 10+Math.floor(Math.random()*90);
    const f = getProductFullInfo(p);
    return {
      code:p.code, name:p.name, unit:p.unit,
      expected,
      expectedWms: isDraft ? 0 : expected,
      accepted: isDraft ? "" : 10+Math.floor(Math.random()*90),
      defect: isDraft ? "" : Math.floor(Math.random()*3),
      series: isDraft ? "" : (p.series || "—"),
      batch: isDraft ? "" : p.batch,
      exp: p.exp,
      price: f.cost,
      currency: f.currency,
    };
  });
}
function mockOrderLines(n, isDraft){
  const pool = products.slice(0, Math.max(n,3));
  return pool.slice(0,n).map(p=>{
    const ordered = 5+Math.floor(Math.random()*40);
    const f = getProductFullInfo(p);
    return {
      code:p.code, name:p.name, unit:p.unit,
      ordered,
      orderedWms: isDraft ? 0 : ordered,
      picked: isDraft ? "" : 5+Math.floor(Math.random()*40),
      weight: isDraft ? "" : 5+Math.floor(Math.random()*80),
      status: isDraft ? "" : ["Собрано","В сборке","Ожидает"][Math.floor(Math.random()*3)],
      series: isDraft ? "" : (p.series || "—"),
      batch: isDraft ? "" : p.batch,
      exp: p.exp,
      price: f.cost,
      currency: f.currency,
    };
  });
}
function groupBatchRows(rows){
  const map = new Map();
  rows.forEach(b=>{
    const key = [b.batch, b.series, b.exp, b.q, b.blockReason].join('|');
    if(map.has(key)){
      const g = map.get(key);
      g.stock += b.stock; g.reserved += b.reserved; g.available += b.available; g.blocked += b.blocked;
    }else{
      map.set(key, {...b});
    }
  });
  return [...map.values()];
}
function mockBatches(p){
  const ratio = p.stock > 0 ? p.reserved / p.stock : 0;
  const rows = [
    {batch:p.batch, series:p.series || "—", exp:p.exp, qty:Math.floor(p.stock*0.5), q:"Годен", blockReason:""},
    {batch:p.batch.replace(/\/.$/, "/доп"), series:p.series || "—", exp:p.exp, qty:Math.floor(p.stock*0.2), q:"Годен", blockReason:"Временная блокировка"},
    {batch:p.batch.replace(/\/.$/, "/рез"), series:p.series || "—", exp:p.exp, qty:Math.floor(p.stock*0.1), q:"Годен", blockReason:"Недостача"},
    {batch:"Возврат 22.07.26", series:p.series || "—", exp:p.exp, qty:p.defect, q:p.defect>0?"Брак":"Годен", blockReason:p.defect>0?"Брак":""},
  ].filter(b=>b.qty>0);
  return rows.map(b=>{
    const reserved = Math.round(b.qty*ratio);
    const blocked = b.blockReason ? b.qty : 0;
    const available = (b.q==='Годен' && !b.blockReason) ? Math.max(0, b.qty - reserved) : 0;
    return {...b, stock:b.qty, reserved, available, blocked};
  });
}
function mockHistory(){
  return [
    {date:"29.07.2026", doc:"ЗК-000091", op:"Резервирование", qty:-24},
    {date:"27.07.2026", doc:"ПР-000116", op:"Приёмка на склад", qty:+58},
    {date:"25.07.2026", doc:"ЗК-000089", op:"Отгрузка", qty:-12},
    {date:"20.07.2026", doc:"ПР-000114", op:"Приёмка на склад", qty:+35},
  ];
}

/* ============== Навигация ============== */
let lastMainView = 'orders';
function showView(name){
  document.querySelectorAll('.view').forEach(v=>v.classList.remove('active'));
  document.getElementById('view-'+name).classList.add('active');
  window.scrollTo({top:0, behavior:'instant'});
}
function showTab(tab){
  if(tab === 'admin' && currentUserRole !== 'admin' && currentUserRole !== 'developer') { tab = 'orders'; }
  if(tab === 'dev-settings' && currentUserRole !== 'developer') { tab = 'orders'; }
  document.querySelectorAll('nav.tabs button').forEach(b=>b.classList.toggle('active', b.dataset.tab===tab));
  lastMainView = tab;
  showView(tab);
  if(tab === 'admin'){ renderAdminAccess(); renderAdminDepositorAccess(); renderAdminStaffAccess(); populateReportsConfigDepositorFilter(); renderReportsConfigTable(); }
  if(tab === 'dev-settings'){ renderDevSubtabsBar(); renderDevSettingsContent(); }
  if(tab === 'references'){ showReferencesSub(currentReferencesSub); }
  if(typeof SCROLL_SYNC_KEYS !== 'undefined' && SCROLL_SYNC_KEYS.includes(tab)){
    setTimeout(()=>updateTopScrollWidth(tab), 0);
  }
}
let currentReferencesSub = 'sku';
function showReferencesSub(sub){
  const adminOnlySubs = ['orderTypes', 'docTypes', 'depositorsDir', 'warehousesDir', 'users'];
  if(adminOnlySubs.includes(sub) && currentUserRole !== 'admin' && currentUserRole !== 'developer') sub = 'sku';
  currentReferencesSub = sub;
  document.querySelectorAll('[data-refsub]').forEach(b=>b.classList.toggle('active', b.dataset.refsub===sub));
  ['sku','packaging','suppliers','consignees','orderTypes','docTypes','depositorsDir','warehousesDir','users'].forEach(s=>{
    document.getElementById('ref-sub-'+s).classList.toggle('active', s===sub);
  });
  if(sub === 'sku') renderSku();
  if(sub === 'packaging') renderPackaging();
  if(sub === 'suppliers') renderSuppliers();
  if(sub === 'consignees') renderConsignees();
  if(sub === 'orderTypes') renderOrderTypes();
  if(sub === 'docTypes') renderDocTypes();
  if(sub === 'depositorsDir') renderDepositorsDir();
  if(sub === 'warehousesDir') renderWarehousesDir();
  if(sub === 'users'){
    renderAdminUsers();
    const actions = document.getElementById('usersRefActions');
    if(actions) actions.style.display = currentUserRole === 'developer' ? 'none' : '';
  }
}
function showAdminSub(sub){
  document.querySelectorAll('.admin-subtab-btn[data-sub]').forEach(b=>b.classList.toggle('active', b.dataset.sub===sub));
  document.querySelectorAll('.admin-sub[id^="admin-sub-"]').forEach(s=>s.classList.toggle('active', s.id==='admin-sub-'+sub));
  if(sub === 'reports-config') renderReportsConfigTable();
}
/* ============ Пользовательское наименование полей ============

   Раздел «Администрирование → Настройка полей поклажедателя» был
   макетом: список переименований лежал в массиве на странице, и окно
   правки правило этот же массив. Теперь это вкладка карточки
   поклажедателя (js/portal/field-labels.js), данные — в таблице
   depositor_field_label, а подписи приходят с сервера уже
   переименованными.

   Функция ниже осталась: ею обёрнуты подписи в отрисовке списков
   и карточек прототипа. Теперь она спрашивает подпись у раскладки
   с сервера — там переименование уже разрешено, — а при отсутствии
   раскладки отдаёт null, и подпись остаётся портальной.              */
function getDepositorFieldLabel(objectType, fieldId){
  const columns = window.PORTAL && PORTAL.columns;
  if (!columns || !columns.renameOf) return null;
  // objectType здесь совпадает с ключом таблицы ('sku', 'stock',
  // 'receipts', 'orders', 'transfers'), а fieldId — с идентификатором
  // колонки: в этих таблицах поле и колонка зовутся одинаково.
  return columns.renameOf(objectType, fieldId);
}


function renderAdminUsers(){
  const body = document.getElementById('adminUsersBody');
  if(!body) return;
  const roleLabel = {depositor:'Поклажедатель', manager:'Менеджер', admin:'Администратор', staff:'Сотрудник склада', developer:'Разработчик'};
  body.innerHTML = DEMO_USERS.map(u=>{
    const scope = u.role === 'depositor' ? u.organization
      : u.role === 'manager' ? ((u.allowedDepositors||[]).map(id=>DEPOSITORS[id]?.name).join(', ') || 'Нет доступных поклажедателей')
      : 'Весь портал';
    const wmsBind = u.role === 'depositor' ? `Код контрагента: ${(u.defaultDepositor||'').toUpperCase()}-CTR`
      : u.role === 'manager' ? `Логин ArtWMS: ${u.login.split('@')[0]}`
      : '—';
    return `<tr onclick="openAdminUserEdit('${u.login}')">
      <td><strong>${u.full_name}</strong></td>
      <td>${roleLabel[u.role]||u.role}</td>
      <td>${scope}</td>
      <td>${u.login}</td>
      <td>${wmsBind}</td>
      <td><span class="badge ok"><span class="dot"></span>${T('admin.text.active')}</span></td>
    </tr>`;
  }).join('');
}
function renderAdminAccess(){
  const body = document.getElementById('adminAccessBody');
  if(!body) return;
  body.innerHTML = DEMO_USERS.filter(u=>u.role==='manager').map(u=>{
    const depositorNames = (u.allowedDepositors||[]).map(id=>DEPOSITORS[id]?.name).join(', ');
    return `<tr onclick="openAdminAccessEdit('${u.login}')">
      <td><strong>${u.full_name}</strong></td>
      <td>${depositorNames || '—'}</td>
    </tr>`;
  }).join('');
}
function renderAdminDepositorAccess(){
  const body = document.getElementById('adminDepositorAccessBody');
  if(!body) return;
  body.innerHTML = Object.values(DEPOSITORS).map(k=>{
    const whNames = (DEPOSITOR_WAREHOUSES[k.id]||[]).map(id=>WAREHOUSES[id]?.name).join(', ');
    return `<tr onclick="openAdminDepositorAccessEdit('${k.id}')">
      <td><strong>${k.name}</strong></td>
      <td>${whNames || '—'}</td>
      <td></td>
    </tr>`;
  }).join('');
}
function openAdminDepositorAccessEdit(depositorId){
  const k = DEPOSITORS[depositorId];
  if(!k) return;
  const el = document.getElementById('adminUserEditContent');
  const allowed = new Set(DEPOSITOR_WAREHOUSES[depositorId] || []);
  el.innerHTML = `
    <div class="detail-head">
      <div><h2>Доступные склады — ${k.name}</h2></div>
    </div>
    <div class="section-title" style="margin-top:0;">Отметьте склады, на которых поклажедатель хранит товар</div>
    <div class="table-card" style="padding:18px 20px;">
      ${Object.values(WAREHOUSES).map(w=>`
        <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;padding:9px 0;border-bottom:1px solid var(--line);">
          <input type="checkbox" data-warehouse-id="${w.id}" ${allowed.has(w.id)?'checked':''}>
          ${w.name}
        </label>
      `).join('')}
    </div>
    <div class="actions" style="margin-top:18px;">
      <button class="btn primary" onclick="saveAdminDepositorAccessEdit('${depositorId}')">${T('portal.text.save')}</button>
      <button class="btn ghost" onclick="showView('admin'); showAdminSub('depositor-access');">${T('common.button.cancel')}</button>
    </div>
  `;
  showView('admin-user-edit');
}
function saveAdminDepositorAccessEdit(depositorId){
  const checked = [...document.querySelectorAll('#adminUserEditContent input[data-warehouse-id]:checked')].map(cb=>cb.dataset.warehouseId);
  DEPOSITOR_WAREHOUSES[depositorId] = checked;
  persistAdminState();
  notify(T('admin.text.accessible_warehouses_updated') + DEPOSITORS[depositorId].name);
  renderAdminDepositorAccess();
  if(currentDepositorId === depositorId) updateWarehouseFilterUI(depositorId);
  showView('admin');
  showAdminSub('depositor-access');
}

/* ---- Доступы сотрудников склада (админ) ---- */
function renderAdminStaffAccess(){
  const body = document.getElementById('adminStaffAccessBody');
  if(!body) return;
  body.innerHTML = DEMO_USERS.filter(u=>u.role==='staff').map(u=>{
    const whs = u.warehouses || (u.warehouse ? [u.warehouse] : []);
    const whNames = whs.map(id=>WAREHOUSES[id]?.name).join(', ') || '—';
    const depositorNames = u.allowedDepositors ? (u.allowedDepositors.map(id=>DEPOSITORS[id]?.name).join(', ') || '—') : 'Все поклажедатели';
    return `<tr onclick="openAdminStaffAccessEdit('${u.login}')">
      <td><strong>${u.full_name}</strong></td>
      <td>${whNames}</td>
      <td>${depositorNames}</td>
    </tr>`;
  }).join('');
}
function openAdminStaffAccessEdit(login){
  const u = DEMO_USERS.find(x=>x.login===login && x.role==='staff');
  if(!u) return;
  const el = document.getElementById('adminUserEditContent');
  const whs = new Set(u.warehouses || (u.warehouse ? [u.warehouse] : []));
  const allDepositorsAllowed = !u.allowedDepositors;
  const allowedDepositors = new Set(u.allowedDepositors || []);
  el.innerHTML = `
    <div class="detail-head">
      <div>
        <h2>Доступы сотрудника склада — ${u.full_name}</h2>
        <div class="meta">${u.position}</div>
      </div>
    </div>

    <div class="section-title" style="margin-top:0;">${T('admin.column.accessible_warehouses')}</div>
    <div class="table-card" style="padding:18px 20px;">
      ${Object.values(WAREHOUSES).map(w=>`
        <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;padding:9px 0;border-bottom:1px solid var(--line);">
          <input type="checkbox" data-staff-warehouse-id="${w.id}" ${whs.has(w.id)?'checked':''}>
          ${w.name}
        </label>
      `).join('')}
    </div>

    <div class="section-title">${T('admin.text.accessible_depositors')}</div>
    <div class="hint" style="margin-bottom:10px;">Если ничего не отмечено — сотруднику доступны все поклажедатели, хранящиеся на выбранных выше складах.</div>
    <div class="table-card" style="padding:18px 20px;">
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;padding:9px 0;border-bottom:1px solid var(--line);font-weight:600;">
        <input type="checkbox" id="staffAllDepositors" ${allDepositorsAllowed?'checked':''} onchange="document.querySelectorAll('[data-staff-depositor-id]').forEach(cb=>cb.disabled=this.checked)">
        Все поклажедатели
      </label>
      ${Object.values(DEPOSITORS).map(k=>`
        <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;padding:9px 0;border-bottom:1px solid var(--line);">
          <input type="checkbox" data-staff-depositor-id="${k.id}" ${allowedDepositors.has(k.id)?'checked':''} ${allDepositorsAllowed?'disabled':''}>
          ${k.name}
        </label>
      `).join('')}
    </div>

    <div class="actions" style="margin-top:18px;">
      <button class="btn primary" onclick="saveAdminStaffAccessEdit('${u.login}')">${T('portal.text.save')}</button>
      <button class="btn ghost" onclick="showView('admin'); showAdminSub('staff-access');">${T('common.button.cancel')}</button>
    </div>
  `;
  showView('admin-user-edit');
}
function saveAdminStaffAccessEdit(login){
  const u = DEMO_USERS.find(x=>x.login===login && x.role==='staff');
  if(!u) return;
  const warehouses = [...document.querySelectorAll('#adminUserEditContent input[data-staff-warehouse-id]:checked')].map(cb=>cb.dataset.staffWarehouseId);
  u.warehouses = warehouses;
  delete u.warehouse;
  const allDepositorsAllowed = document.getElementById('staffAllDepositors')?.checked;
  if(allDepositorsAllowed){
    u.allowedDepositors = null;
  } else {
    u.allowedDepositors = [...document.querySelectorAll('#adminUserEditContent input[data-staff-depositor-id]:checked')].map(cb=>cb.dataset.staffDepositorId);
  }
  persistAdminState();
  notify(T('common.message.employee_access_updated') + u.full_name);
  renderAdminStaffAccess();
  if(currentUser && currentUser.login === u.login){
    currentStaffWarehouses = u.warehouses;
    currentStaffAllowedDepositors = u.allowedDepositors;
    currentWarehouseFilter = currentStaffWarehouses.length === 1 ? currentStaffWarehouses[0] : 'all';
    currentStaffDepositorFilter = staffAvailableDepositors()[0] || 'none';
    setStaffData();
  }
  showView('admin');
  showAdminSub('staff-access');
}

/* ---- Сохранение настроек админки между перезагрузками страницы (демо, localStorage) ---- */
function persistAdminState(){
  try{
    const state = {
      depositorWarehouses: DEPOSITOR_WAREHOUSES,
      users: DEMO_USERS.map(u=>({
        login:u.login,
        hiddenTabs:u.hiddenTabs || null,
        allowedDepositors: u.role==='staff' ? (u.allowedDepositors===undefined ? null : u.allowedDepositors) : (u.allowedDepositors || null),
        defaultDepositor:u.defaultDepositor || null,
        warehouses:u.warehouses || null,
        canCreateReceipts: u.canCreateReceipts === false ? false : null,
        canCreateTransfers: u.canCreateTransfers === false ? false : null,
        canCreateOrders: u.canCreateOrders === false ? false : null,
        canUploadSkuExcel: u.canUploadSkuExcel === false ? false : null,
        canUploadPackagingExcel: u.canUploadPackagingExcel === false ? false : null,
        isActive: u.isActive === false ? false : null,
        hideServicesTab: u.hideServicesTab ? true : null,
      })),
    };
    localStorage.setItem('ahlersDemoAdminState', JSON.stringify(state));
  }catch(e){ /* localStorage может быть недоступен — молча игнорируем в демо */ }
}
function restoreAdminState(){
  try{
    const raw = localStorage.getItem('ahlersDemoAdminState');
    if(!raw) return;
    const state = JSON.parse(raw);
    if(state.depositorWarehouses){
      Object.keys(state.depositorWarehouses).forEach(kid=>{ DEPOSITOR_WAREHOUSES[kid] = state.depositorWarehouses[kid]; });
    }
    (state.users || []).forEach(su=>{
      const u = DEMO_USERS.find(x=>x.login===su.login);
      if(!u) return;
      if(su.hiddenTabs) u.hiddenTabs = su.hiddenTabs;
      if(u.role === 'staff'){
        if('allowedDepositors' in su) u.allowedDepositors = su.allowedDepositors;
      } else if(su.allowedDepositors){
        u.allowedDepositors = su.allowedDepositors;
      }
      if(su.defaultDepositor) u.defaultDepositor = su.defaultDepositor;
      if(su.warehouses){ u.warehouses = su.warehouses; delete u.warehouse; }
      if('canCreateReceipts' in su && su.canCreateReceipts !== null) u.canCreateReceipts = su.canCreateReceipts;
      if('canCreateTransfers' in su && su.canCreateTransfers !== null) u.canCreateTransfers = su.canCreateTransfers;
      if('canCreateOrders' in su && su.canCreateOrders !== null) u.canCreateOrders = su.canCreateOrders;
      if('canUploadSkuExcel' in su && su.canUploadSkuExcel !== null) u.canUploadSkuExcel = su.canUploadSkuExcel;
      if('canUploadPackagingExcel' in su && su.canUploadPackagingExcel !== null) u.canUploadPackagingExcel = su.canUploadPackagingExcel;
      if('isActive' in su && su.isActive !== null) u.isActive = su.isActive;
      if('hideServicesTab' in su && su.hideServicesTab !== null) u.hideServicesTab = su.hideServicesTab;
    });
  }catch(e){ /* игнорируем повреждённые/недоступные данные в демо */ }
}

/* ---- Редактирование пользователя (админ) ---- */
function saveAdminUserEdit(login){
  const u = DEMO_USERS.find(x=>x.login===login);
  if(!u) return;
  const hiddenTabs = [];
  if(!document.getElementById('menuVisTransfers')?.checked) hiddenTabs.push('transfers');
  if(!document.getElementById('menuVisBilling')?.checked) hiddenTabs.push('billing');
  u.hiddenTabs = hiddenTabs;
  u.canCreateReceipts = document.getElementById('menuVisCreateReceipts')?.checked !== false;
  u.canCreateTransfers = document.getElementById('menuVisCreateTransfers')?.checked !== false;
  u.canCreateOrders = document.getElementById('menuVisCreateOrders')?.checked !== false;
  u.canUploadSkuExcel = document.getElementById('menuVisUploadSku')?.checked !== false;
  u.canUploadPackagingExcel = document.getElementById('menuVisUploadPackaging')?.checked !== false;
  u.hideServicesTab = document.getElementById('menuVisServicesTab')?.checked === false;
  u.isActive = document.getElementById('userIsActive')?.checked !== false;
  persistAdminState();
  notify(T('admin.text.changes_saved') + u.full_name);
  if(currentUser && currentUser.login === u.login){
    applyUserTabVisibility(u);
    applyUserCreatePermissions(u);
    renderReceipts(); renderOrders(); renderStock(); renderSku(); renderTransfers();
  }
  showView('admin');
  showAdminSub('users');
}
function openAdminUserEdit(login){
  const u = DEMO_USERS.find(x=>x.login===login);
  if(!u) return;
  const isActive = u.isActive !== false;
  const el = document.getElementById('adminUserEditContent');
  el.innerHTML = `
    <div class="detail-head">
      <div>
        <h2>${u.full_name}</h2>
        <div class="meta">${u.position} · ${u.organization}</div>
      </div>
      <div>
        <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;cursor:pointer;">
          <input type="checkbox" id="userIsActive" ${isActive ? 'checked' : ''} onchange="const b=document.getElementById('userIsActiveBadge'); b.className='badge '+(this.checked?'ok':'err'); b.innerHTML='<span class=\"dot\"></span>'+(this.checked?'Активен':'Заблокирован');">
          <span class="badge ${isActive ? 'ok' : 'err'}" id="userIsActiveBadge"><span class="dot"></span>${isActive ? 'Активен' : 'Заблокирован'}</span>
        </label>
      </div>
    </div>

    <div class="section-title" style="margin-top:0;">${T('admin.text.user_details')}</div>
    <div class="detail-head">
      <div class="kv-grid" style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">
        <div class="kv"><label>${T('admin.text.full_name')}</label><div><input type="text" value="${u.full_name}" style="width:100%;padding:7px 9px;border:1px solid var(--line);border-radius:4px;font-size:13.5px;"></div></div>
        <div class="kv"><label>${T('admin.text.position')}</label><div><input type="text" value="${u.position}" style="width:100%;padding:7px 9px;border:1px solid var(--line);border-radius:4px;font-size:13.5px;"></div></div>
        <div class="kv"><label>${T('common.label.email_login')}</label><div><input type="text" value="${u.login}" style="width:100%;padding:7px 9px;border:1px solid var(--line);border-radius:4px;font-size:13.5px;"></div></div>
        <div class="kv"><label>${T('partners.label.phone')}</label><div><input type="text" value="${u.phone||''}" style="width:100%;padding:7px 9px;border:1px solid var(--line);border-radius:4px;font-size:13.5px;"></div></div>
        <div class="kv"><label>${T('references.column.role')}</label><div>
          <select style="width:100%;padding:7px 9px;border:1px solid var(--line);border-radius:4px;font-size:13.5px;">
            <option ${u.role==='depositor'?'selected':''}>${T('admin.column.depositor')}</option>
            <option ${u.role==='manager'?'selected':''}>${T('admin.column.manager')}</option>
            <option ${u.role==='admin'?'selected':''}>Администратор</option>
          </select>
        </div></div>
        <div class="kv"><label>${T('references.column.artwms_binding')}</label><div><input type="text" value="${u.role==='depositor' ? (u.defaultDepositor||'').toUpperCase()+'-CTR' : u.login.split('@')[0]}" style="width:100%;padding:7px 9px;border:1px solid var(--line);border-radius:4px;font-size:13.5px;"></div></div>
      </div>
    </div>

    <div class="section-title">${T('admin.text.menu_visibility')}</div>
    <div class="table-card" style="padding:18px 20px;">
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisServicesTab" ${u.hideServicesTab?'':'checked'}>${T('admin.text.show_the_services_tab_in_documents')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisBilling" ${((u.hiddenTabs||[]).includes('billing'))?'':'checked'}>${T('admin.text.show_the_billing_section')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisUploadSku" ${u.canUploadSkuExcel===false?'':'checked'}>${T('admin.text.allow_importing_products_from_exce')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisUploadPackaging" ${u.canUploadPackagingExcel===false?'':'checked'}>${T('admin.text.allow_importing_packaging_from_exc')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisCreateOrders" ${u.canCreateOrders===false?'':'checked'}>${T('admin.text.allow_creating_shipment_orders')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisCreateReceipts" ${u.canCreateReceipts===false?'':'checked'}>${T('admin.text.allow_creating_receipts')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;"><input type="checkbox" id="menuVisCreateTransfers" ${u.canCreateTransfers===false?'':'checked'}>${T('admin.text.allow_creating_transfers')}</label>
      <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;"><input type="checkbox" id="menuVisTransfers" ${((u.hiddenTabs||[]).includes('transfers'))?'':'checked'}>${T('admin.text.show_the_transfers_section')}</label>
    </div>

    <div class="actions" style="margin-top:18px;">
      <button class="btn primary" onclick="saveAdminUserEdit('${u.login}')">${T('portal.text.save')}</button>
      <button class="btn ghost" onclick="showTab('references'); showReferencesSub('users');">${T('common.button.cancel')}</button>
    </div>
  `;
  if(currentUserRole === 'developer'){
    el.insertAdjacentHTML('afterbegin', `<div style="background:#eef2ff; border:1px solid #c7d2fe; color:#3730a3; border-radius:8px; padding:10px 14px; margin-bottom:14px; font-size:13px;">👁 Режим просмотра — редактирование пользователей доступно только администратору.</div>`);
    el.querySelectorAll('input, select, textarea').forEach(elm=>elm.disabled = true);
    el.querySelectorAll('button').forEach(btn=>{
      if(btn.getAttribute('onclick') && btn.getAttribute('onclick').includes('save')) btn.style.display = 'none';
    });
  }
  showView('admin-user-edit');
}

/* ---- Редактирование доступов менеджера (админ) ---- */
function openAdminAccessEdit(login){
  const u = DEMO_USERS.find(x=>x.login===login && x.role==='manager');
  if(!u) return;
  const el = document.getElementById('adminAccessEditContent');
  const allowed = new Set(u.allowedDepositors||[]);
  el.innerHTML = `
    <div class="detail-head">
      <div>
        <h2>Доступы менеджера — ${u.full_name}</h2>
        <div class="meta">${u.position}</div>
      </div>
    </div>

    <div class="section-title" style="margin-top:0;">Поклажедатели, доступные менеджеру</div>
    <div class="table-card" style="padding:18px 20px;">
      ${Object.values(DEPOSITORS).map(k=>`
        <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;padding:9px 0;border-bottom:1px solid var(--line);">
          <input type="checkbox" data-depositor-id="${k.id}" ${allowed.has(k.id)?'checked':''}>
          ${k.name}
        </label>
      `).join('')}
    </div>

    <div class="actions" style="margin-top:18px;">
      <button class="btn primary" onclick="saveAdminAccessEdit('${login}')">${T('portal.text.save')}</button>
      <button class="btn ghost" onclick="showView('admin'); showAdminSub('access');">${T('common.button.cancel')}</button>
    </div>
  `;
  showView('admin-access-edit');
}
function saveAdminAccessEdit(login){
  const u = DEMO_USERS.find(x=>x.login===login && x.role==='manager');
  if(!u) return;
  const checked = [...document.querySelectorAll('#adminAccessEditContent input[type=checkbox]:checked')].map(cb=>cb.dataset.depositorId);
  u.allowedDepositors = checked;
  if(!checked.includes(u.defaultDepositor)) u.defaultDepositor = checked[0] || 'none';
  persistAdminState();
  notify(T('admin.text.access_updated') + u.full_name);
  renderAdminAccess();
  showView('admin');
  showAdminSub('access');
}
function renderProfile(user){
  const el = document.getElementById('profileContent');
  if(!el || !user) return;
  const loginShort = user.login.split('@')[0];
  const roleLabel = (currentLang==='en'
    ? {depositor:'Client — full access', manager:'3PL account manager', admin:'Portal administrator', staff:'Warehouse staff'}
    : {depositor:'Поклажедатель — полный доступ', manager:'Менеджер 3PL-оператора', admin:'Администратор портала', staff:'Сотрудник склада'})[user.role] || user.role;

  let html = `
    <div class="detail-head">
      <div>
        <h2>${user.full_name}</h2>
        <div class="meta">${user.position} · ${user.organization} · логин: ${loginShort}</div>
        <div class="kv-grid">
          <div class="kv"><label>Email</label><div>${user.login}</div></div>
          <div class="kv"><label>${T('partners.label.phone')}</label><div>${user.phone || '—'}</div></div>
          <div class="kv"><label>${T('admin.text.portal_role')}</label><div>${roleLabel}</div></div>
          <div class="kv"><label>${T('admin.text.last_sign_in')}</label><div>${user.lastLogin || '—'}</div></div>
        </div>
      </div>
      <div class="actions">
        <button class="btn" onclick="notify('Открыта форма смены пароля')">${T('admin.text.change_password2')}</button>
      </div>
    </div>`;

  if(user.role === 'depositor'){
    const req = COMPANY_REQUISITES[user.defaultDepositor] || {};
    const mgr = user.managerContact || {};
    html += `
    <div class="section-title">Организация — поклажедатель</div>
    <div class="detail-head">
      <div class="kv-grid" style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">
        <div class="kv"><label>${T('references.column.name')}</label><div>${user.organization}</div></div>
        <div class="kv"><label>${T('partners.label.bin')}</label><div>${req.bin || '—'}</div></div>
        <div class="kv"><label>${T('common.text.legal_address')}</label><div>${req.legalAddress || '—'}</div></div>
        <div class="kv"><label>Фактический адрес</label><div>${req.legalAddress || '—'}</div></div>
        <div class="kv"><label>${T('admin.column.bank')}</label><div>${req.bank || '—'}</div></div>
        <div class="kv"><label>${T('common.text.iban')}</label><div>${req.iik || '—'}</div></div>
      </div>
    </div>

    <div class="section-title">Персональный менеджер 3PL-оператора</div>
    <div class="detail-head">
      <div class="kv-grid" style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">
        <div class="kv"><label>${T('admin.text.full_name')}</label><div>${mgr.name || '—'}</div></div>
        <div class="kv"><label>${T('partners.label.phone')}</label><div>${mgr.phone || '—'}</div></div>
        <div class="kv"><label>Email</label><div>${mgr.email || '—'}</div></div>
        <div class="kv"><label>${T('sku.label.warehouse')}</label><div>с. Байсерке, ул. Султана Бейбарыса, 1</div></div>
      </div>
    </div>

    <div class="panel-head">
      <div class="section-title" style="margin:0;">Пользователи организации</div>
    </div>
    <div class="table-card">
      <table>
        <thead><tr><th>${T('admin.text.full_name')}</th><th>${T('admin.text.position')}</th><th>${T('references.column.login')}</th><th>${T('references.column.role')}</th><th>${T('billing.label.status')}</th><th></th></tr></thead>
        <tbody>
          <tr>
            <td><strong>${user.full_name}</strong></td><td>${user.position}</td><td>${loginShort}</td>
            <td>Полный доступ</td><td><span class="badge ok"><span class="dot"></span>${T('admin.text.active')}</span></td><td></td>
          </tr>
        </tbody>
      </table>
    </div>`;
  } else if(user.role === 'manager'){
    const depositorNames = (user.allowedDepositors || []).map(id => DEPOSITORS[id]?.name).filter(Boolean);
    html += `
    <div class="section-title">Зона ответственности</div>
    <div class="detail-head">
      <div class="kv-grid" style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">
        <div class="kv"><label>${T('sku.label.warehouse')}</label><div>с. Байсерке, ул. Султана Бейбарыса, 1</div></div>
        <div class="kv"><label>${T('admin.text.accessible_depositors')}</label><div>${depositorNames.join(', ') || '—'}</div></div>
      </div>
    </div>

    <div class="section-title">${T('references.button.depositors')}</div>
    <div class="table-card">
      <table>
        <thead><tr><th>${T('references.column.name')}</th><th>${T('billing.label.status')}</th></tr></thead>
        <tbody>
          ${depositorNames.map(name=>`<tr><td>${name}</td><td><span class="badge ok"><span class="dot"></span>${T('admin.text.active')}</span></td></tr>`).join('')}
        </tbody>
      </table>
    </div>`;
  } else if(user.role === 'admin'){
    const allDepositorNames = Object.values(DEPOSITORS).map(k=>k.name);
    html += `
    <div class="section-title">Права администратора</div>
    <div class="detail-head">
      <div class="kv-grid" style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">
        <div class="kv"><label>Доступ</label><div>Все поклажедатели и склады</div></div>
        <div class="kv"><label>Управление</label><div>Пользователи, права, видимость меню, интеграционные логи</div></div>
      </div>
    </div>

    <div class="section-title">Все поклажедатели портала</div>
    <div class="table-card">
      <table>
        <thead><tr><th>${T('references.column.name')}</th><th>${T('billing.label.status')}</th></tr></thead>
        <tbody>
          ${allDepositorNames.map(name=>`<tr><td>${name}</td><td><span class="badge ok"><span class="dot"></span>${T('admin.text.active')}</span></td></tr>`).join('')}
        </tbody>
      </table>
    </div>

    <div class="panel-head">
      <div class="section-title" style="margin:0;">${T('references.title.portal_users')}</div>
      <div class="actions">
        <button class="btn primary" onclick="notify('Открыта форма создания нового пользователя')">${T('references.button.create_user')}</button>
      </div>
    </div>
    <div class="table-card">
      <table>
        <thead><tr><th>${T('admin.text.full_name')}</th><th>${T('references.column.role')}</th><th>${T('references.column.login')}</th><th>${T('billing.label.status')}</th><th></th></tr></thead>
        <tbody>
          ${DEMO_USERS.map(u=>`<tr><td><strong>${u.full_name}</strong></td><td>${u.position}</td><td>${u.login.split('@')[0]}</td><td><span class="badge ok"><span class="dot"></span>${T('admin.text.active')}</span></td><td></td></tr>`).join('')}
        </tbody>
      </table>
    </div>`;
  }

  el.innerHTML = html;
}

function openProfile(){
  showView('profile');
}
function openAbout(){
  showView('about');
}
function openContacts(){
  showView('contacts');
}

/* ============== Авторизация (демо) ============== */
const DEMO_USERS = [
  {group:'Администратор', role:'admin', full_name:'Байменов Ерлан Сакенович', position:'Администратор портала', organization:'Ahlers Logistics — портал', login:'admin@ahlers.com', phone:'8 702 119 43 07', lastLogin:'31.07.2026, 07:30', manager:'Режим администратора: управление пользователями и доступами', defaultDepositor:'rogaikopyta', allowedDepositors:['rogaikopyta','boltygaiki','polovoygigant']},
  {group:'Разработчик', role:'developer', full_name:'Смагулов Тимур Айдарович', position:'Разработчик портала', organization:'Ahlers Logistics — портал', login:'dev@ahlers.com', phone:'8 705 220 61 14', lastLogin:'02.08.2026, 11:05', manager:'Режим разработчика: настройка структуры документов и полей', defaultDepositor:'rogaikopyta', allowedDepositors:['rogaikopyta','boltygaiki','polovoygigant']},
  {group:'Поклажедатели', role:'depositor', full_name:'Серикова Айгерим Батыровна', position:'Логист-координатор', organization:'ТОО «Рога и Копыта»', login:'a.serikova@rogaikopyta.kz', phone:'+7 701 234 56 78', lastLogin:'31.07.2026, 09:12', manager:'Менеджер: Назарбаев Н.А. · тел.: 8 905 895 95 85', managerContact:{name:'Назарбаев Н.А.', phone:'8 905 895 95 85', email:'n.nazarbayev@ahlers.com'}, defaultDepositor:'rogaikopyta'},
  {group:'Поклажедатели', role:'depositor', full_name:'Тулегенов Аскар Болатович', position:'Снабженец', organization:'ТОО «Болты и Гайки»', login:'a.tulegenov@boltygaiki.kz', phone:'+7 707 345 12 90', lastLogin:'31.07.2026, 08:47', manager:'Менеджер: Назарбаев Н.А. · тел.: 8 905 895 95 85', managerContact:{name:'Назарбаев Н.А.', phone:'8 905 895 95 85', email:'n.nazarbayev@ahlers.com'}, defaultDepositor:'boltygaiki'},
  {group:'Поклажедатели', role:'depositor', full_name:'Половцева Ирина Сергеевна', position:'Директор по логистике', organization:'ООО «Половой гигант»', login:'i.polovtseva@polgigant.kz', phone:'+7 705 678 44 21', lastLogin:'31.07.2026, 10:03', manager:'Менеджер: Ахметова С.К. · тел.: 8 701 442 17 63', managerContact:{name:'Ахметова С.К.', phone:'8 701 442 17 63', email:'s.akhmetova@ahlers.com'}, defaultDepositor:'polovoygigant'},
  {group:'Менеджеры 3PL-оператора', role:'manager', full_name:'Назарбаев Нурлан Асхатович', position:'Менеджер по работе с клиентами · склад Алматы',    organization:'Ahlers Logistics — склад Алматы',    login:'n.nazarbayev@ahlers.com', phone:'8 905 895 95 85', lastLogin:'31.07.2026, 08:55', manager:'Режим менеджера: доступны поклажедатели «Рога и Копыта», «Болты и Гайки»', defaultDepositor:'rogaikopyta', allowedDepositors:['rogaikopyta','boltygaiki']},
  {group:'Менеджеры 3PL-оператора', role:'manager', full_name:'Ахметова Сая Кайратовна',    position:'Менеджер по работе с клиентами · склад Караганда', organization:'Ahlers Logistics — склад Караганда', login:'s.akhmetova@ahlers.com',  phone:'8 701 442 17 63', lastLogin:'31.07.2026, 09:40', manager:'Режим менеджера: доступен только поклажедатель «Половой гигант»', defaultDepositor:'polovoygigant', allowedDepositors:['polovoygigant']},
  {group:'Складские сотрудники', role:'staff', full_name:'Оспанов Даулет Ержанович', position:'Кладовщик · склад Алматы', organization:'Ahlers Logistics — склад Алматы', login:'d.ospanov@ahlers.com', phone:'8 707 300 12 45', lastLogin:'31.07.2026, 07:55', manager:'Режим кладовщика: доступны все документы и остатки склада Алматы', warehouses:['almaty'], allowedDepositors:null},
  {group:'Складские сотрудники', role:'staff', full_name:'Ертаев Серик Болатович', position:'Кладовщик · склад Караганда', organization:'Ahlers Logistics — склад Караганда', login:'s.yertayev@ahlers.com', phone:'8 701 300 88 21', lastLogin:'31.07.2026, 08:10', manager:'Режим кладовщика: доступны все документы и остатки склада Караганда', warehouses:['karagandy'], allowedDepositors:null},
];

/* Реквизиты организаций-поклажедателей для личного кабинета */
const COMPANY_REQUISITES = {
  rogaikopyta: { bin:'040540017382', legalAddress:'г. Алматы, ул. Жандосова, 58, оф. 14', bank:'АО «Halyk Bank», БИК HSBKKZKX', iik:'KZ12 3456 7890 1234 5678' },
  boltygaiki:  { bin:'070840029105', legalAddress:'г. Алматы, ул. Толе би, 105, оф. 3', bank:'АО «Kaspi Bank», БИК CASPKZKA', iik:'KZ45 6789 0123 4567 8901' },
  polovoygigant: { bin:'091240018837', legalAddress:'г. Алматы, ул. Момышулы, 15, оф. 210', bank:'АО «Forte Bank», БИК IRTYKZKA', iik:'KZ78 9012 3456 7890 1234' },
};

function initials(fullName){
  const parts = fullName.split(' ').filter(Boolean);
  return (parts[0]?.[0]||'') + (parts[1]?.[0]||'');
}
function renderDemoUsers(){
  const groups = {};
  DEMO_USERS.forEach((u,i)=>{ (groups[u.group] = groups[u.group]||[]).push({...u, idx:i}); });
  const html = Object.keys(groups).map(groupName=>`
    <div>
      <div class="demo-group-title">${groupName}</div>
      ${groups[groupName].map(u=>`
        <div class="demo-user" onclick="quickLogin(${u.idx})">
          <div class="avatar">${initials(u.full_name)}</div>
          <div class="info">
            <div class="n">${u.full_name}</div>
            <div class="r">${u.organization} · ${u.position}</div>
          </div>
        </div>
      `).join('')}
    </div>
  `).join('');
  document.getElementById('demoUserList').innerHTML = html;
}

function buildStaffData(warehouseIds, depositorFilter, allowedDepositors){
  const allProducts=[], allReceipts=[], allOrders=[], allTransfers=[];
  Object.entries(DEPOSITOR_DATA).forEach(([kid, d])=>{
    if(kid==='none') return;
    if(allowedDepositors && !allowedDepositors.includes(kid)) return;
    if(depositorFilter && depositorFilter!=='all' && kid!==depositorFilter) return;
    const depositorName = DEPOSITORS[kid]?.name || kid;
    (d.products||[]).filter(p=>warehouseIds.includes(p.warehouse)).forEach(p=>allProducts.push({...p, depositor:depositorName}));
    (d.receipts||[]).filter(r=>warehouseIds.includes(r.warehouse)).forEach(r=>allReceipts.push({...r, depositor:depositorName}));
    (d.orders||[]).filter(o=>warehouseIds.includes(o.warehouse)).forEach(o=>allOrders.push({...o, depositor:depositorName}));
    (d.transfers||[]).filter(t=>warehouseIds.includes(t.fromWarehouse) || warehouseIds.includes(t.toWarehouse)).forEach(t=>allTransfers.push({...t, depositor:depositorName}));
  });
  return {products:allProducts, receipts:allReceipts, orders:allOrders, reports:[], transfers:allTransfers};
}
let currentStaffWarehouses = [];
let currentStaffAllowedDepositors = null; // null = доступны все поклажедатели этих складов
let currentStaffDepositorFilter = 'all';
function staffAvailableDepositors(){
  const whs = currentWarehouseFilter==='all' ? currentStaffWarehouses : [currentWarehouseFilter];
  return Object.keys(DEPOSITORS).filter(kid=>{
    if(currentStaffAllowedDepositors && !currentStaffAllowedDepositors.includes(kid)) return false;
    const kws = DEPOSITOR_WAREHOUSES[kid]||[];
    return whs.some(w=>kws.includes(w));
  });
}
function setStaffData(){
  currentDepositorId = 'staff';
  let availableDepositors = staffAvailableDepositors();
  if(!availableDepositors.includes(currentStaffDepositorFilter)){
    currentStaffDepositorFilter = availableDepositors[0] || 'none';
  }
  const whs = currentWarehouseFilter==='all' ? currentStaffWarehouses : [currentWarehouseFilter];
  const d = buildStaffData(whs, currentStaffDepositorFilter, currentStaffAllowedDepositors);
  products = d.products; receipts = d.receipts; orders = d.orders; reports = d.reports; transfers = d.transfers;
  ['receipts','orders','stock','sku','transfers'].forEach(k=>{ if(paginationState[k]) paginationState[k].page = 1; });
  renderReceipts(); renderOrders(); renderStock(); renderSku(); renderReports(); renderTransfers();

  const orgLabel = DEPOSITORS[currentStaffDepositorFilter]?.name || 'Нет доступных поклажедателей';
  document.getElementById('tabsOrg').textContent = orgLabel;
  document.getElementById('tabsOrg').classList.toggle('switchable', availableDepositors.length > 1);

  const wf = document.getElementById('warehouseFilter');
  wf.textContent = currentWarehouseFilter === 'all' ? T('common.button.all_warehouses') : WAREHOUSES[currentWarehouseFilter].name;
  wf.classList.toggle('switchable', currentStaffWarehouses.length > 1);
}
function setStaffDepositorFilter(kid){
  if(!kid || kid === 'all') return; // обязательна фильтрация по конкретному поклажедателю
  currentStaffDepositorFilter = kid;
  document.getElementById('depositorSwitcher').classList.remove('active');
  setStaffData();
}


function logout(){
  document.getElementById('appShell').style.display = 'none';
  document.getElementById('loginScreen').classList.add('active');
  document.getElementById('loginEmail').value = '';
  document.getElementById('loginPassword').value = '';
}
let currentUserRole = 'depositor';
let currentUserDepositors = ['rogaikopyta'];
let currentUser = null;

function applyUserTabVisibility(user){
  const hidden = new Set((user && user.hiddenTabs) || []);
  document.querySelectorAll('nav.tabs button[data-tab]').forEach(btn=>{
    btn.style.display = hidden.has(btn.dataset.tab) ? 'none' : '';
  });
  if(hidden.has(lastMainView)) showTab('orders');
}
function applyUserCreatePermissions(user){
  const canCreateReceipts = user ? user.canCreateReceipts !== false : true;
  const canCreateTransfers = user ? user.canCreateTransfers !== false : true;
  const canCreateOrders = user ? user.canCreateOrders !== false : true;
  const canUploadSkuExcel = user ? user.canUploadSkuExcel !== false : true;
  const canUploadPackagingExcel = user ? user.canUploadPackagingExcel !== false : true;
  const receiptBtn = document.getElementById('createReceiptBtn');
  const transferBtn = document.getElementById('createTransferBtn');
  const orderBtn = document.getElementById('createOrderBtn');
  const uploadSkuBtn = document.getElementById('uploadSkuExcelBtn');
  const uploadPackagingBtn = document.getElementById('uploadPackagingExcelBtn');
  if(receiptBtn) receiptBtn.style.display = canCreateReceipts ? '' : 'none';
  if(transferBtn) transferBtn.style.display = canCreateTransfers ? '' : 'none';
  if(orderBtn) orderBtn.style.display = canCreateOrders ? '' : 'none';
  if(uploadSkuBtn) uploadSkuBtn.style.display = canUploadSkuExcel ? '' : 'none';
  if(uploadPackagingBtn) uploadPackagingBtn.style.display = canUploadPackagingExcel ? '' : 'none';
}

function enterApp(user){
  document.getElementById('loginScreen').classList.remove('active');
  document.getElementById('appShell').style.display = '';
  currentUserRole = user.role;
  currentUser = user;
  applyUserTabVisibility(user);
  applyUserCreatePermissions(user);

  if(user.role === 'staff'){
    document.getElementById('depositorSwitcher').classList.remove('active');
    currentUserDepositors = [];
    currentStaffWarehouses = user.warehouses || (user.warehouse ? [user.warehouse] : []);
    currentStaffAllowedDepositors = user.allowedDepositors || null;
    currentWarehouseFilter = currentStaffWarehouses.length === 1 ? currentStaffWarehouses[0] : 'all';
    const availableDepositorsAtLogin = staffAvailableDepositors();
    currentStaffDepositorFilter = availableDepositorsAtLogin[0] || 'none';
    setStaffData();
    document.getElementById('managerLine').textContent = user.manager;
    document.getElementById('adminTabBtn').style.display = 'none';
    renderProfile(user);
    lastMainView = 'orders';
    showTab('orders');
    notify(`Вы вошли как ${user.full_name}`);
    return;
  }

  const canSwitch = user.role === 'manager' || user.role === 'admin' || user.role === 'developer';
  currentUserDepositors = canSwitch
    ? (user.allowedDepositors ? (user.allowedDepositors.length ? user.allowedDepositors : ['none']) : Object.keys(DEPOSITORS))
    : [user.defaultDepositor || 'rogaikopyta'];

  // менеджер/админ/разработчик с несколькими доступными поклажедателями не должны видеть чужие данные по умолчанию —
  // ждём осознанного выбора через переключатель. Если доступен только один — выбирать нечего, подставляем его сразу.
  const needsExplicitChoice = canSwitch && currentUserDepositors.length > 1;
  setDepositorData(needsExplicitChoice ? 'choose' : (canSwitch ? currentUserDepositors[0] : (user.defaultDepositor || currentUserDepositors[0])));
  const tabsOrgEl = document.getElementById('tabsOrg');
  tabsOrgEl.classList.toggle('switchable', canSwitch && currentUserDepositors.length > 1);
  document.getElementById('depositorSwitcher').classList.remove('active');
  renderDepositorSwitcher();
  if(needsExplicitChoice) toggleDepositorSwitcher();

  if (!canSwitch) {
    document.getElementById('tabsOrg').textContent = user.organization;
  }
  document.getElementById('managerLine').textContent = user.manager;
  document.getElementById('adminTabBtn').style.display = (user.role === 'admin' || user.role === 'developer') ? '' : 'none';
  document.getElementById('devSettingsTabBtn').style.display = user.role === 'developer' ? '' : 'none';
  document.getElementById('refSubBtnOrderTypes').style.display = (user.role === 'admin' || user.role === 'developer') ? '' : 'none';
  document.getElementById('refSubBtnDocTypes').style.display = (user.role === 'admin' || user.role === 'developer') ? '' : 'none';
  document.getElementById('refSubBtnDepositorsDir').style.display = (user.role === 'admin' || user.role === 'developer') ? '' : 'none';
  document.getElementById('refSubBtnWarehousesDir').style.display = (user.role === 'admin' || user.role === 'developer') ? '' : 'none';
  document.getElementById('refSubBtnUsers').style.display = (user.role === 'admin' || user.role === 'developer') ? '' : 'none';
  renderProfile(user);
  lastMainView = 'orders';
  showTab('orders');
  notify(`Вы вошли как ${user.full_name}`);
}
function doLogin(){
  const email = document.getElementById('loginEmail').value.trim();
  if(!email){ notify(T('common.message.enter_your_login_and_password')); return; }
  const found = DEMO_USERS.find(u=>u.login.toLowerCase()===email.toLowerCase());
  const target = found || DEMO_USERS[0];
  if(target.isActive === false){ notify(T('common.message.account_is_blocked_please_cont')); return; }
  enterApp(target);
}
function quickLogin(idx){
  const target = DEMO_USERS[idx];
  if(target.isActive === false){ notify(T('common.message.account_is_blocked_please_cont')); return; }
  enterApp(target);
}

/* ---- Переключатель поклажедателей (доступен менеджеру/администратору и сотруднику склада) ---- */
function toggleDepositorSwitcher(){
  if(currentUserRole === 'staff'){
    const list = staffAvailableDepositors();
    if(list.length <= 1) return;
    renderDepositorSwitcher();
    document.getElementById('depositorSwitcher').classList.toggle('active');
    return;
  }
  if((currentUserRole !== 'manager' && currentUserRole !== 'admin' && currentUserRole !== 'developer') || currentUserDepositors.length <= 1) return;
  renderDepositorSwitcher();
  document.getElementById('depositorSwitcher').classList.toggle('active');
}
function renderDepositorSwitcher(){
  const el = document.getElementById('depositorSwitcher');
  if(currentUserRole === 'staff'){
    const list = staffAvailableDepositors().map(id=>DEPOSITORS[id]).filter(Boolean);
    el.innerHTML = `<div class="ks-title">${T('auth.text.switch_depositor')}</div>` +
      list.map(k=>`
        <div class="ks-item ${k.id===currentStaffDepositorFilter?'current':''}" onclick="setStaffDepositorFilter('${k.id}')">
          <span>${k.name}</span>${k.id===currentStaffDepositorFilter?'<span>✓</span>':''}
        </div>
      `).join('');
    return;
  }
  const list = currentUserDepositors.map(id=>DEPOSITORS[id]).filter(Boolean);
  el.innerHTML = `<div class="ks-title">${T('auth.text.switch_depositor')}</div>` + list.map(k=>`
    <div class="ks-item ${k.id===currentDepositorId?'current':''}" onclick="selectDepositor('${k.id}')">
      <span>${k.name}</span>${k.id===currentDepositorId?'<span>✓</span>':''}
    </div>
  `).join('');
}
function selectDepositor(id){
  if((currentUserRole !== 'manager' && currentUserRole !== 'admin' && currentUserRole !== 'developer') || !currentUserDepositors.includes(id)) return;
  setDepositorData(id);
  document.getElementById('depositorSwitcher').classList.remove('active');
  renderDepositorSwitcher();
  notify(T('auth.text.depositor_selected') + DEPOSITORS[id].name);
}
document.addEventListener('click', (e)=>{
  const wrap = document.getElementById('tabsOrg')?.closest('.tabs-org-wrap');
  if(wrap && !wrap.contains(e.target)){
    document.getElementById('depositorSwitcher')?.classList.remove('active');
  }
});

renderDemoUsers();
document.querySelectorAll('nav.tabs button').forEach(btn=>{
  btn.addEventListener('click', ()=>showTab(btn.dataset.tab));
});

function notify(msg){
  // лёгкая имитация действия в демо-версии
  const toast = document.createElement('div');
  toast.textContent = msg;
  toast.style.cssText = `
    position:fixed;top:20px;left:50%;transform:translateX(-50%);
    background:#1b2430;color:#fff;padding:10px 18px;border-radius:6px;font-size:13px;
    z-index:100;box-shadow:0 4px 14px rgba(0,0,0,.2);opacity:0;transition:opacity .2s;`;
  document.body.appendChild(toast);
  requestAnimationFrame(()=>toast.style.opacity='1');
  setTimeout(()=>{toast.style.opacity='0'; setTimeout(()=>toast.remove(),250);}, 2200);
}

/* ============== Init ============== */
function docIdFromRecord(prefix, doc){
  const parts = (doc.date || '').split('.');
  const dateStr = parts.length === 3 ? (parts[2].slice(2) + parts[1] + parts[0]) : '000000';
  const login = doc.author || 'user';
  const uniq = loginTimeHash4(login, doc.no || dateStr);
  return `${prefix}${dateStr}${uniq}`;
}
function docWmsNumberFromRecord(doc){
  const parts = (doc.date || '').split('.');
  const dateStr = parts.length === 3 ? (parts[2].slice(2) + parts[1] + parts[0]) : '000000';
  const login = doc.author || 'user';
  const uniq = loginTimeHash4('wms_' + login, doc.no || dateStr);
  return `WMS${dateStr}${uniq}`;
}
function normalizeLegacyDocIds(){
  const receiptSets = [receipts_rogaikopyta, receipts_boltygaiki, receipts_polovoygigant];
  const orderSets = [orders_rogaikopyta, orders_boltygaiki, orders_polovoygigant];
  const transferSets = [transfers_rogaikopyta, transfers_boltygaiki, transfers_polovoygigant];
  receiptSets.forEach(arr=>arr.forEach(r=>{ r.no = docIdFromRecord('WP', r); r.wmsNumber = r.status==='draft' ? '' : docWmsNumberFromRecord(r); }));
  orderSets.forEach(arr=>arr.forEach(o=>{ o.no = docIdFromRecord('WO', o); o.wmsNumber = o.status==='draft' ? '' : docWmsNumberFromRecord(o); }));
  transferSets.forEach(arr=>arr.forEach(t=>{ t.no = docIdFromRecord('WT', t); t.wmsNumber = t.status==='draft' ? '' : docWmsNumberFromRecord(t); }));
}
normalizeLegacyDocIds();
renderReceipts();
renderOrders();
renderStock();
renderSku();
renderReports();
restoreAdminState();
logout(); /* стартовая страница — авторизация */
