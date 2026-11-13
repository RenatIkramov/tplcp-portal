
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /* ---------------- обращение к API ---------------- */

  // общий помощник core.js (прежде — четыре одинаковые копии здесь)
  const api = PORTAL.api;

  /* ---------------- справочники для выпадающих списков ---------------- */

  /**
   * Справочники для выбора в черновике.
   * @type {{loaded: boolean, warehouses: SelectOption[], suppliers: SelectOption[],
   *         consignees: SelectOption[], skuByCode: Record<string, SkuRow>, packagings: PackagingRow[],
   *         orderTypes: SelectOption[], docTypes: SelectOption[], transportTypes?: SelectOption[],
   *         skuList?: object[]}}
   */
  const refs = {loaded:false, warehouses:[], suppliers:[], consignees:[], skuByCode:{},
                packagings:[], orderTypes:[], docTypes:[]};
  /** @type {{canEditTransport: boolean, priceInReceipts: boolean, priceInOrders: boolean, loaded: boolean, roleCode?: string}} */
  const me = {canEditTransport:false, priceInReceipts:false, priceInOrders:false, loaded:false};

  async function loadDirectories(){
    if (refs.loaded) return refs;
    // Берём справочники ДЛЯ ВЫБОРА — /options, весь набор без страниц:
    // товар человек ищет с середины наименования, и страницами такой
    // поиск не собрать. Страница экрана справочника — это другой адрес
    // и другой набор.
    //
    // Прочитанные упаковки складываем в PORTAL.catalog.packaging
    // и оттуда же берём, если они уже там: по этому набору считает
    // колонка «Используются упаковки», и две копии одного справочника
    // однажды разойдутся.
    const [w, s, c, sku, packagings, types, meResponse] = await Promise.all([
      api('GET','/api/warehouses'), api('GET','/api/suppliers'),
      api('GET','/api/consignees'), api('GET','/api/sku/options'),
      (PORTAL.catalog && PORTAL.catalog.packaging && PORTAL.catalog.packaging.length)
        ? Promise.resolve(PORTAL.catalog.packaging)
        : api('GET','/api/packaging/options'),
      api('GET','/api/document-dictionaries'),
      api('GET','/api/me')
    ]);
    me.canEditTransport = meResponse.canEditTransport === true;
    // Нужны ли клиенту цена и сумма в окне строки (V61). Решает не роль
    // человека, а настройка поклажедателя: это свойство его документов.
    me.priceInReceipts = meResponse.priceInReceipts === true;
    me.priceInOrders = meResponse.priceInOrders === true;
    me.roleCode = meResponse.roleCode;
    me.loaded = true;
    refs.packagings = packagings || [];
    if (PORTAL.catalog) PORTAL.catalog.packaging = refs.packagings;
    refs.warehouses = w.map(x => ({value:x.warehouseId, text:x.name}));
    refs.suppliers  = s.map(x => ({value:x.supplierId,  text:x.name}));
    refs.consignees = c.map(x => ({value:x.consigneeId, text:x.name}));
    // Типы заказов и приходных документов. Прототип предлагал здесь
    // НАИМЕНОВАНИЯ из своего массива, и выбранное наименование уходило
    // в колонку order_type_id — сохранение шапки падало ошибкой
    // приведения типа, то есть черновик заказа не сохранялся вовсе.
    refs.orderTypes = (types.orderTypes || []).map(x => ({value:x.id, text:x.name}));
    refs.docTypes   = (types.receiptDocTypes || []).map(x => ({value:x.id, text:x.name}));
    // Качество строки заказа (V89): код и имя на языке пользователя.
    // Из него строится список в окне строки (line-modal.js).
    PORTAL.qualities = (types.qualities || []).map(x => ({code: x.code, name: x.name}));
    // Тип транспорта (V97): в документе хранится код
    refs.transportTypes = (types.transportTypes || []).map(x => ({value: x.code, text: x.name}));

    // Товары для окна ввода строки (line-modal.js ищет в refs.skuList).
    // Упаковка по умолчанию — её окно подставляет сразу при выборе товара.
    const defaultPackaging = {};
    (refs.packagings || []).forEach(p => {
      if (p.isDefaultOrderUnit) defaultPackaging[p.skuCode] = p.packagingCode;
    });

    refs.skuByCode = {};
    refs.skuList = sku.map(x => {
      refs.skuByCode[x.code] = x;
      return {
        skuId: x.skuId, code: x.code, name: x.name, unit: x.uomName || '',
        defaultOrderPackaging: defaultPackaging[x.code] || null,
        group: x.itemGroupName || '', productType: x.itemTypeName || '',
        barcode: x.barcode || '', cost: x.cost, currency: x.currencyCode || 'KZT',
        isInWms: x.isInWms !== false
      };
    });
    refs.loaded = true;
    return refs;
  }

  /* ---------------- адреса доставки грузополучателя ---------------- */

  /**
   * Адреса читаются по мере надобности и запоминаются: грузополучателей
   * у клиента бывает много, а в документе нужен один. Запрос уходит
   * на того, кого выбрали, и повторно уже не уходит.
   */
  const addresses = {};

  function addressOptions(consigneeId){
    return (addresses[consigneeId] || []).map(a => ({value: a.addressId, text: a.address}));
  }

  async function loadAddresses(consigneeId){
    if (!consigneeId || addresses[consigneeId]) return addresses[consigneeId] || [];
    const rows = await api('GET', '/api/consignees/' + encodeURIComponent(consigneeId) + '/addresses');
    addresses[consigneeId] = rows || [];
    return addresses[consigneeId];
  }

  /** Адрес, который подставляется сам: основной, а если он один — он же. */
  function defaultAddress(consigneeId){
    const list = addresses[consigneeId] || [];
    if (!list.length) return null;
    const main = list.find(a => a.isDefault);
    return (main || (list.length === 1 ? list[0] : null)) || null;
  }

  /**
   * Переписывает список мест доставки под выбранного грузополучателя.
   *
   * <p>Переписывается только этот список, а не карточка целиком: человек
   * мог уже что-то набрать в соседних полях, и перерисовка всё бы стёрла.
   *
   * <p>Основной адрес подставляется сам. Это не вольность: у большинства
   * грузополучателей он один, и заставлять выбирать единственное значение
   * значит добавлять щелчок без смысла.
   */
  function paintAddresses(selected){
    const field = document.getElementById('of_deliveryAddress');
    if (!field) return;
    const consigneeId = (document.getElementById('of_consignee') || {}).value || '';
    const list = addresses[consigneeId] || [];
    const chosen = selected !== undefined ? selected
        : (list.some(a => String(a.addressId) === String(field.value)) ? field.value : '');
    // Адрес вводит клиент: только текстом, не разметкой (аудит S1).
    // Прежде здесь был innerHTML с «экранированием» одной кавычки — тег
    // в адресе становился тегом.
    field.replaceChildren();
    if (!chosen) field.add(new Option(T('sku.text.not_selected'), '', true, true));
    list.forEach(a => {
      const on = String(a.addressId) === String(chosen);
      field.add(new Option(String(a.address === null || a.address === undefined ? '' : a.address),
          String(a.addressId), on, on));
    });
  }

  /**
   * Сменился грузополучатель — прежний адрес больше не его.
   *
   * <p>Поэтому выбранное место доставки сбрасывается, а не остаётся:
   * адрес чужой организации в документе хуже пустого поля.
   */
  function consigneeChanged(){
    const consigneeId = (document.getElementById('of_consignee') || {}).value || '';
    paintAddresses('');
    // Контакт прежней организации уходит вместе с её адресом — даже
    // набранный руками: чужой контакт в заказе хуже пустого поля.
    setContact('');
    autoContact = '';
    if (!consigneeId) return;
    loadAddresses(consigneeId)
      .then(() => {
        const main = defaultAddress(consigneeId);
        paintAddresses(main ? main.addressId : '');
        applyContact(main ? main.addressId : '');
      })
      .catch(() => {});
  }

  /* ---------------- контакт по месту доставки (V83) ---------------- */

  /**
   * Контакт заказа подставляется по выбранному адресу.
   *
   * <p>Что подставить, решает база (`delivery_contact`): свой контакт
   * адреса, а если его нет — контакты грузополучателя. Экран получает
   * готовое значение в `orderContact` списка адресов и правила не
   * повторяет.
   *
   * <p>Поле остаётся редактируемым, и набранное руками подстановка не
   * трогает: она заменяет только пустое поле или то, что сама же и
   * подставила (`autoContact`). Иначе смена адреса стирала бы контакт,
   * который человек только что уточнил.
   */
  let autoContact = '';

  function contactOfAddress(addressId){
    const consigneeId = (document.getElementById('of_consignee') || {}).value || '';
    const a = (addresses[consigneeId] || []).find(x => String(x.addressId) === String(addressId));
    return a && a.orderContact ? String(a.orderContact) : '';
  }

  function currentContact(){
    const el = document.getElementById('of_consigneeContact');
    if (el) return (el.value || '').trim();
    const doc = PORTAL.card.doc('orders');
    return doc && doc.consigneeContact ? String(doc.consigneeContact).trim() : '';
  }

  /** Пишет и в поле, и в документ в памяти: вкладка с полем может быть не открыта. */
  function setContact(value){
    const el = document.getElementById('of_consigneeContact');
    if (el) el.value = value;
    const doc = PORTAL.card.doc('orders');
    if (doc) doc.consigneeContact = value;
  }

  function applyContact(addressId){
    const now = currentContact();
    if (now !== '' && now !== autoContact) return;     // правил человек — не трогаем
    const next = addressId ? contactOfAddress(addressId) : '';
    setContact(next);
    autoContact = next;
  }

  function addressChanged(){
    const field = document.getElementById('of_deliveryAddress');
    applyContact(field ? field.value : '');
  }

  /**
   * Открыли сохранённый черновик: если в нём стоит ровно то, что
   * подставилось бы по его адресу, считаем это подстановкой — тогда
   * смена адреса обновит контакт. Иначе это правка человека.
   */
  function rememberContact(addressId){
    const now = currentContact();
    autoContact = now !== '' && now === contactOfAddress(addressId) ? now : '';
  }

  /* ---------------- поля шапки: что куда сохраняется ---------------- */

  // поле карточки → колонка базы
  const FIELDS = {
    receipts: {
      rf_erpNo:'erp_number', rf_warehouse:'warehouse_id', rf_supplier:'supplier_id',
      rf_docType:'doc_type_id', rf_eta:'eta_date',
      rf_accompanyingDocNumber:'accompanying_doc_number', rf_invoiceNumber:'invoice_number',
      rf_gtd:'gtd', rf_comment:'comment'
    },
    orders: {
      of_erpNo:'erp_number', of_warehouse:'warehouse_id', of_consignee:'consignee_id',
      of_deliveryAddress:'delivery_point_id', of_consigneeContact:'consignee_contact',
      of_orderType:'order_type_id', of_plannedShip:'planned_ship_date',
      of_comment:'comment'
    },
    transfers: {
      tf_erpNo:'erp_number', tf_fromWarehouse:'from_warehouse_id',
      tf_toWarehouse:'to_warehouse_id', tf_comment:'comment'
    }
  };

  // Вкладка «Доп. информация» — десять свободных полей на каждый тип
  // документа. Их не было ни среди сохраняемых, ни среди читаемых:
  // человек заполнял вкладку, а она ничего не делала. Сами поля в базе
  // есть с самого начала, и в справочнике колонок тоже (V51).
  const EXTRA_PREFIX = {receipts: 'rf', orders: 'of', transfers: 'tf'};
  Object.keys(FIELDS).forEach(type => {
    for (let i = 1; i <= 10; i++){
      FIELDS[type][EXTRA_PREFIX[type] + '_customField' + i] = 'custom_field_' + i;
    }
  });

  // Транспортный раздел — с V84 тоже поля черновика: заполняется до
  // подтверждения и сохраняется общей кнопкой вместе с шапкой. «Рейс»
  // в списке шапки уже есть. Пользователю без права на транспорт сервер
  // эти поля не примет, а экран их ему и не откроет.
  const TRANSPORT_FIELDS = ['carrier', 'transportType', 'transportBrand', 'carNumber',
                            'trailerNumber', 'techPassportNumber', 'containerNumber',
                            'sealNumber', 'driverName', 'driverPhone', 'driverIdNumber',
                            'driverInn'];
  Object.keys(FIELDS).forEach(type => {
    TRANSPORT_FIELDS.forEach(f => {
      FIELDS[type][EXTRA_PREFIX[type] + '_' + f] = f.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
    });
  });

  /* ---------------- текущий документ ----------------
     Открытый документ и его состав держит карточка (doc-card.js). */

  const refOf = type => PORTAL.card.doc(type);
  const compositionOf = type => PORTAL.card.lines(type);

  /** Текст подтверждения после сохранения черновика — свой на тип. */
  const SAVED = {
    receipts: 'sku.text.receipt_changes_saved',
    orders: 'sku.text.order_changes_saved',
    transfers: 'sku.text.transfer_changes_saved'
  };

  /** Куда карточка этого типа кладёт баннер и кнопки черновика. */
  const SCREEN = {
    receipts:  {banner:'receiptDetailBanner',  actions:'receiptDraftActions'},
    orders:    {banner:'orderDetailBanner',    actions:'orderDraftActions'},
    transfers: {banner:'transferDetailBanner', actions:'transferDraftActions'}
  };

  /**
   * Идентификатор поля на экране → свойство документа: of_erpNo → erpNo.
   *
   * <p>Карточка собирает идентификаторы именно так — префикс типа
   * документа плюс имя поля, — и это единственная связь между полем
   * на экране и объектом, из которого она рисуется.
   */
  const docField = id => id.replace(/^(rf|of|tf)_/, '');

  const docOf = refOf;

  /**
   * Значения шапки для сохранения.
   *
   * <p>Поля берутся с экрана, а чего на экране нет — из документа
   * в памяти. Второе не перестраховка: карточка разложена по вкладкам,
   * и на экране всегда только одна. Заполнил человек общую информацию,
   * ушёл на «Доп. информацию», нажал «Сохранить» — полей общей вкладки
   * в разметке уже нет, и без этой половины их правки пропали бы.
   *
   * <p>Дата в документе лежит по-русски, а база ждёт ISO — поэтому
   * значение из памяти приводится обратно. Поля-ссылки в памяти держат
   * ключ, их приводить не надо.
   */
  function collectHeader(type){
    const store = {};
    const doc = docOf(type) || {};
    Object.entries(FIELDS[type]).forEach(([id, column]) => {
      const el = document.getElementById(id);
      let v;
      if (el){
        v = (el.value || '').trim();
      } else {
        const kept = doc[docField(id)];
        v = kept === null || kept === undefined ? '' : String(kept).trim();
        if (/^\d{2}\.\d{2}\.\d{4}$/.test(v) && typeof toISODate === 'function'){
          v = toISODate(v);
        }
      }
      store[column] = v === '' || v === '—' ? null : v;
    });
    return store;
  }

  /**
   * Переносит набранное с экрана в документ, из которого карточка
   * рисуется, — перед тем как её перерисуют.
   *
   * <p>Зачем. Переключение вкладки перерисовывает карточку из памяти,
   * а не с сервера. Раньше это сходило с рук, потому что весь блок шапки
   * имел onchange и сохранял черновик после КАЖДОГО изменения поля —
   * с плашкой «изменения сохранены» на каждый щелчок. Плашку убрали
   * и сохранение оставили кнопке; значит набранное надо сохранить
   * хотя бы в памяти, иначе переход на соседнюю вкладку стирал бы
   * работу человека.
   *
   * <p>Дата на экране в формате ISO, а в документе по-русски: пишем
   * по-русски, иначе карточка (она ждёт русскую дату) покажет пустое
   * поле. Вызывает карточка — перед переходом на другую вкладку.
   */
  function keepUnsaved(type){
    const doc = docOf(type);
    if (!doc) return;
    Object.keys(FIELDS[type]).forEach(id => {
      const el = document.getElementById(id);
      if (!el || el.disabled || el.readOnly) return;
      const value = (el.value || '').trim();
      doc[docField(id)] = (el.type === 'date' && value && typeof fromISODate === 'function')
          ? fromISODate(value) : value;
    });
  }

  /**
   * Уход со страницы несохранённого документа — с вопросом.
   *
   * <p>Документ появляется в базе только по кнопке «Сохранить», значит
   * у несохранённого есть что терять. Спрашиваем, но лишь когда терять
   * действительно есть что: пустая карточка закрывается молча — вопрос
   * на пустом месте человек перестаёт читать, и тогда он не поможет
   * там, где нужен.
   */
  function guardLeave(){
    PORTAL.beforeTab(() => {
      const open = ['receipts', 'orders', 'transfers']
          .find(type => { const ref = refOf(type); return ref && !ref.docId; });
      if (open && hasData(open)
          && !window.confirm(T('common.message.leave_unsaved_document'))){
        return false;
      }
      if (open) PORTAL.card.forget(open);
    });
  }

  /** Состав документа в том виде, в каком его ждёт API. */
  function collectLines(type){
    const composition = compositionOf(type);
    return (composition || []).map(l => {
      // строки, созданные окном демо, несут код товара, а не идентификатор
      const skuId = l.skuId || (refs.skuByCode[l.code] && refs.skuByCode[l.code].skuId);
      const line = {sku_id: skuId, line_comment: l.lineComment || null};
      if (type === 'receipts') {
        line.expected_qty = Number(l.expected);
        // качество строки поступления (V100) — код, как в заказе
        line.quality = PORTAL.card.quality(l);
      }
      else if (type === 'orders') {
        line.ordered_qty = Number(l.ordered);
        if (l.weight) line.weight_kg = Number(l.weight);
        // Качество строки (V89) — код перечисления; сервер переведёт его
        // в quality_id, а пустое примет как «Годный».
        line.quality = PORTAL.card.quality(l);
      }
      // У перемещения одно количество и нет цены: товар не меняет
      // владельца, меняется только место хранения.
      else {
        line.qty = Number(l.qty);
        // качество строки перемещения (V101): что перемещать — годный, брак…
        line.quality = PORTAL.card.quality(l);
        return line;
      }
      if (l.price !== null && l.price !== undefined && l.price !== '') {
        line.price = Number(l.price);
        // Валюта берётся из карточки товара. Раньше здесь стояла единица —
        // то есть тенге всегда, и товар в долларах молча записывался
        // не в своей валюте. Валюту человек не выбирает: она свойство
        // товара, а не решение при вводе строки.
        const card = refs.skuByCode[l.code];
        if (card && card.currencyId) line.currency_id = Number(card.currencyId);
      }
      return line;
    });
  }

  // Наружу — чтобы проверки могли пройти ПО ВСЕМ полям шапки, а не по
  // списку, выписанному в самой проверке. Выписанный список покрывает
  // только то, что было в день его написания: поле, добавленное позже,
  // осталось бы непроверенным — а именно на таком поле («тип заказа»)
  // портал и спотыкался, молча отправляя наименование в колонку ключа.
  PORTAL.draftFields = FIELDS;

  /* ---------------- загрузка документа в карточку ---------------- */

  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0,10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  // общие помощники — core.js (аудит A4)
  const {dash} = window.PORTAL;

  /** Карточка из API → объект документа в терминах демо. */
  function toDocument(type, card){
    const h = card.header;
    const totalCount = {
      docId: h.docId, no: h.documentNo,
      // версия, которую видит экран: с ней уходит каждая запись (аудит A9)
      version: card.version || null,
      // пустой номер ERP оставляем пустым: заголовок карточки подставит номер портала
      erpNo: h.erpNumber || '', wmsNumber: dash(h.docNumber),
      status: PORTAL.statusBadge(h.statusCode), statusLabel: h.statusName || '', statusCode: h.statusCode,
      // Дата создания — отметка времени с V62. Отрезать первые десять
      // знаков от ISO нельзя: там мгновение по UTC.
      date: PORTAL.dateTimeText(h.docDate),
      author: dash(h.authorName), comment: h.comment || '',
      trip: h.trip || '', lineCount: h.lineCount || 0, totalPieces: h.totalPieces || 0,
      weight: h.weightKg || '', volume: h.volumeM3 || '',
      palletCount: h.palletCount || '', cargoPlaces: h.cargoPlaces || '',
      // тип транспорта — код перечисления (V97), имя — на языке пользователя
      carrier: h.carrier || '', transportType: h.transportType || '',
      transportTypeName: dash(h.transportTypeName), transportBrand: h.transportBrand || '',
      carNumber: h.carNumber || '', trailerNumber: h.trailerNumber || '',
      techPassportNumber: h.techPassportNumber || '', containerNumber: h.containerNumber || '',
      sealNumber: h.sealNumber || '', driverName: h.driverName || '', driverPhone: h.driverPhone || '',
      driverIdNumber: h.driverIdNumber || '', driverInn: h.driverInn || '',
      allowsTransportEdit: h.allowsTransportEdit === true,
      transportState: h.transportState || null,
      transportSentAt: h.transportSentAt || null,
      transportError: h.transportError || '',
      hasUnknownSku: h.hasUnknownSku === true,
      problems: card.problems || [],
      warehouse: h.warehouseId, warehouseName: h.warehouseName
    };
    if (type === 'transfers'){
      // Без этого карточка перемещения оставалась пустой: шапка приходит
      // с двумя складами, а здесь заполнялись поля заказа — выбранный
      // склад тут же превращался в прочерк.
      Object.assign(totalCount, {
        fromWarehouse: h.fromWarehouseId, fromWarehouseName: dash(h.fromWarehouseName),
        toWarehouse: h.toWarehouseId, toWarehouseName: dash(h.toWarehouseName),
        shipDate: PORTAL.dateTimeText(h.shipDate)
      });
    } else if (type === 'receipts'){
      Object.assign(totalCount, {
        supplier: h.supplierId, supplierName: dash(h.supplierName),
        docType: h.docTypeId, docTypeName: dash(h.docTypeName),
        eta: ruDate(h.etaDate),
        accompanyingDocNumber: h.accompanyingDocNumber || '', invoiceNumber: h.invoiceNumber || '',
        gtd: h.gtd || ''
      });
    } else {
      Object.assign(totalCount, {
        consignee: h.consigneeId, consigneeName: dash(h.consigneeName),
        orderType: h.orderTypeId, orderTypeName: dash(h.orderTypeName),
        // Планируемая дата отгрузки осталась днём нарочно: это обещание
        // на день, а не событие. Дата отгрузки — со временем (V62).
        plannedShip: ruDate(h.plannedShipDate), ship: PORTAL.dateTimeText(h.shipDate),
        deliveryDate: ruDate(h.deliveryDate),
        // Место доставки — ссылка: в выпадающем списке лежит ключ адреса,
        // а человеку показывается сам адрес.
        deliveryAddress: h.deliveryPointId, deliveryAddressName: dash(h.deliveryAddress),
        consigneeContact: h.consigneeContact || ''
      });
    }
    // Доп. информация: десять свободных полей. Без этого вкладка
    // оставалась пустой даже у документа, в котором они заполнены.
    for (let i = 1; i <= 10; i++){
      totalCount['customField' + i] = h['customField' + i] || '';
    }
    totalCount.lines = (card.lines || []).map(l => ({
      skuId: l.skuId, code: l.skuCode, name: l.skuName, unit: l.unitName || '',
      skuInWms: l.skuInWms !== false,
      // Количества WMS заполняет WMS: у черновика их нет, и подставлять
      // сюда своё количество значило бы выдать обещание за ответ склада
      // (замечание заказчика от 02.11.2026).
      expected: l.expectedQty, expectedWms: l.expectedQtyWms ?? '—',
      // у черновика фактических количеств ещё нет — показываем прочерк, а не пустоту
      accepted: l.acceptedQty ?? '—', defect: l.defectQty ?? '—',
      ordered: l.orderedQty, orderedWms: l.orderedQtyWms ?? '—',
      picked: l.pickedQty ?? '—', weight: l.weightKg ?? '—',
      // у перемещения своё имя количества: не «ожидается» и не «заказано»,
      // а просто «кол-во». Без него состав возвращался с экрана пустым,
      // и добавленная строка показывалась нулём.
      qty: l.qty, qtyWms: l.qtyWms ?? '—',
      price: l.price, currency: l.currencyCode, lineComment: l.lineComment === '—' ? '' : (l.lineComment || ''),
      // качество строки — код (заказ V89, поступление V100, перемещение V101); нет — значит годный
      quality: l.qualityCode || undefined,
      lineNumber: l.lineNumber
    }));
    // Партии строк — для экрана «Детализация» строки. Сервер отдаёт их
    // вместе с карточкой (lineDetails), а раскладывал по строкам card.js,
    // путём, которым карточку давно не открывают: экран был пуст всегда
    // (найдено 10.11.2026).
    const batches = (card.lineDetails || []).map(d => {
      const qty = d.acceptedQty ?? d.pickedQty ?? d.qty;
      return {q: dash(d.quality), batch: dash(d.batchNumber), series: dash(d.seriesNumber),
              exp: ruDate(d.expiryDate), stock: qty === null || qty === undefined ? 0 : Number(qty),
              lineNumber: d.lineNumber};
    });
    totalCount.lines.forEach(line => {
      line.batches = batches.filter(b => b.lineNumber === line.lineNumber);
    });
    totalCount.items = totalCount.lines.length;
    return totalCount;
  }

  /**
   * Прочитать документ и показать. keepTab — тот же документ после
   * сохранения: человек остаётся на открытой вкладке.
   */
  async function openFromApi(type, docId, keepTab){
    await loadDirectories();
    const card = await api('GET', '/api/' + type + '/' + docId);
    const doc = toDocument(type, card);
    show(type, doc, keepTab);
    clearReasons(type);
    // Адреса грузополучателя читаются после отрисовки: ждать их значит
    // задержать всю карточку из-за одного выпадающего списка. Выбранное
    // место доставки передаём явно — иначе список перерисуется пустым
    // и сохранение затёрло бы сохранённый адрес.
    if (type === 'orders' && doc.consignee){
      loadAddresses(doc.consignee)
        .then(() => {
          paintAddresses(doc.deliveryAddress || '');
          rememberContact(doc.deliveryAddress || '');
        })
        .catch(() => {});
    }
    return doc;
  }

  /** Показать документ в карточке (doc-card.js); keepTab — остаться на вкладке. */
  function show(type, doc, keepTab){
    PORTAL.card.show(type, doc, keepTab);
  }

  /**
   * Баннер причин.
   *
   * <p>Раньше он висел над документом всё время, пока тот не готов,
   * и ругался на человека за незаполненные поля, к которым он ещё
   * не дошёл. Теперь баннер молчит, а причины человек получает тогда,
   * когда сам сказал «Подтвердить»: отказ приходит с сервера одной
   * внятной фразой и остаётся на экране, пока документ не изменится.
   */
  function clearReasons(type){
    const banner = document.getElementById(SCREEN[type].banner);
    if (banner) banner.innerHTML = '';
  }

  /**
   * Нейтральная подсказка в том же баннере, где показываются отказы.
   *
   * <p>Отказ красный, подсказка серая: человек должен различать «портал
   * чего-то не может» и «портал объясняет, как устроено».
   */
  function showHint(type, message){
    const banner = document.getElementById(SCREEN[type].banner);
    if (!banner) return;
    const safe = String(message === null || message === undefined ? '' : message)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    banner.innerHTML = '<div class="hint" style="padding:10px 14px;">' + safe + '</div>';
  }

  function showRefusal(type, message){
    const banner = document.getElementById(SCREEN[type].banner);
    if (!banner) return;
    // Текст отказа приходит с сервера и попадает в разметку — экранируем.
    const safe = String(message === null || message === undefined ? '' : message)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    banner.innerHTML = '<div class="hint" style="background:#fdf2f2; border-left:3px solid #c0392b;'
      + ' padding:10px 14px; border-radius:6px; color:#8a2b2b;">' + safe + '</div>';
  }

  /* ---------------- действия с черновиком ---------------- */

  /**
   * Заголовок с версией черновика, которую видит экран (аудит A9): сервер
   * отказывает 409, если документ уже изменил другой. Без версии — без
   * сверки (документ ещё не записан).
   * @param {string|null|undefined} version
   * @returns {Record<string, string>}
   */
  function seen(version){
    return version ? {'If-Match': '"' + version + '"'} : {};
  }

  function tell(e){
    if (typeof notify === 'function') notify(e.message || String(e));
  }

  /**
   * Сохранить черновик.
   *
   * <p>Документа в базе может ещё не быть: с этой правки «Создать новое»
   * только открывает пустую карточку, а запись появляется по кнопке
   * «Сохранить». Поэтому первое сохранение СОЗДАЁТ документ, и только
   * тогда база выдаёт ему номер; дальше кнопка обновляет созданное.
   *
   * <p>Почему так лучше: прежде каждое нажатие «Создать новое» оставляло
   * в базе документ с номером, даже если человек сразу закрыл карточку.
   * Номера расходовались, а список зарастал пустыми черновиками.
   */
  async function save(type, quiet){
    const ref = refOf(type);
    if (!ref || ref.statusCode !== PORTAL.STATUS.DRAFT) return null;
    let docId = ref.docId;
    let card;
    if (!docId){
      card = await api('POST', '/api/' + type, collectHeader(type));
      docId = card.header.docId;
    } else {
      card = await api('PUT', '/api/' + type + '/' + docId, collectHeader(type), seen(ref.version));
    }
    // Состав — с версией, которую только что выдала запись шапки: шапка и
    // состав одного сохранения — одна правка, а не две.
    await api('PUT', '/api/' + type + '/' + docId + '/lines', collectLines(type), seen(card.version));
    const doc = await openFromApi(type, docId, true);
    // Список документов показывает номер, дату, контрагента и число
    // строк — всё это только что изменилось. Без обновления человек,
    // нажав «Назад», видел бы прежнюю строку и решил бы, что сохранение
    // не сработало.
    if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    if (!quiet && typeof notify === 'function'){
      notify(T(SAVED[type]));
    }
    return doc;
  }

  /* ---------------- новый документ: пока только на экране ---------------- */

  /**
   * Пустой документ в памяти — то, что видит человек, нажав
   * «Создать новое».
   *
   * <p>Номера у него нет: номер выдаёт база при создании записи,
   * а записи ещё нет. Бронировать номер заранее нельзя — закрытая
   * без сохранения карточка оставила бы дыру в нумерации.
   *
   * <p>Поля заданы все, какие рисует карточка: пропущенное показалось бы
   * пустым там, где человек ждёт прочерк.
   */
  function emptyDocument(type){
    const doc = {
      docId: null, no: '', erpNo: '', wmsNumber: '—',
      status: 'draft', statusCode: PORTAL.STATUS.DRAFT, statusLabel: T('receipts.option.draft'),
      date: PORTAL.dateTimeText(new Date().toISOString()),
      author: (PORTAL.uiUser && PORTAL.uiUser.fullName) || '',
      comment: '', trip: '', lineCount: 0, totalPieces: 0,
      weight: '', volume: '', palletCount: '', cargoPlaces: '',
      carrier: '', transportType: '', transportBrand: '', carNumber: '', trailerNumber: '',
      techPassportNumber: '', containerNumber: '', sealNumber: '',
      driverName: '', driverPhone: '', driverIdNumber: '', driverInn: '',
      allowsTransportEdit: false, hasUnknownSku: false, problems: [],
      transportState: null, transportSentAt: null, transportError: '',
      // Склад из шапки портала (V94) — сразу в черновик: человек уже
      // сказал, с каким складом работает. Склад у поклажедателя один —
      // он и подставляется (03.11.2026): выбирать из одного незачем.
      warehouse: presetWarehouse() || '',
      warehouseName: (presetWarehouse() && PORTAL.warehouseName(presetWarehouse())) || '—',
      lines: [], items: 0
    };
    for (let i = 1; i <= 10; i++){
      doc['customField' + i] = '';
    }
    if (type === 'receipts'){
      Object.assign(doc, {supplier: '', supplierName: '—', docType: '', docTypeName: '—',
                          eta: '—', accompanyingDocNumber: '', invoiceNumber: '', gtd: ''});
    } else if (type === 'orders'){
      Object.assign(doc, {consignee: '', consigneeName: '—', orderType: '', orderTypeName: '—',
                          plannedShip: '—', ship: '—', deliveryDate: '—',
                          deliveryAddress: '', deliveryAddressName: '—', consigneeContact: ''});
    } else {
      Object.assign(doc, {fromWarehouse: doc.warehouse, fromWarehouseName: doc.warehouseName,
                          toWarehouse: '', toWarehouseName: '—', shipDate: '—'});
    }
    return doc;
  }

  /** Склад, который новый черновик получает сам: из шапки портала или единственный. */
  function presetWarehouse(){
    if (PORTAL.warehouse) return PORTAL.warehouse;
    const all = PORTAL.warehouses || [];
    return all.length === 1 ? all[0].id : '';
  }

  /** Есть ли что терять: заполнено ли хоть одно поле или добавлена строка. */
  function hasData(type){
    if ((compositionOf(type) || []).length) return true;
    const header = collectHeader(type);
    // Склад, подставленный из шапки (V94), — не набранное человеком:
    // закрыть пустой черновик можно, не отвечая «потерять изменения?»
    const preset = presetWarehouse() || null;
    return Object.entries(header).some(([column, v]) => v !== null && v !== ''
        && !((column === 'warehouse_id' || column === 'from_warehouse_id') && v === preset));
  }

  /**
   * Открыть новый документ.
   *
   * <p>В базу ничего не уходит: пока человек не нажал «Сохранить»,
   * документа нет. Поэтому и списки перечитывать не надо — в них
   * ничего не изменилось.
   */
  async function createNew(type){
    await loadDirectories();
    const doc = emptyDocument(type);
    show(type, doc);
    clearReasons(type);
    showHint(type, T('common.text.number_assigned_on_save'));
  }



  async function confirm(type){
    const ref = refOf(type);
    if (!ref) return;
    try {
      // Сохранение могло ТОЛЬКО ЧТО создать документ — тогда ссылка
      // на него появилась лишь после него, и прежний ref ещё без docId.
      await save(type, true);
      const saved = refOf(type);
      if (!saved || !saved.docId) return;
      const card = await api('POST', '/api/' + type + '/' + saved.docId + '/confirm',
          undefined, seen(saved.version));
      sent(type, card);
    } catch (e){
      // Отказ показываем там же, где человек его ждёт — в карточке,
      // и плашкой: плашка заметна, баннер остаётся читаемым.
      showRefusal(type, e.message || String(e));
      tell(e);
    }
  }


  /**
   * Ответ подтверждения или повтора (V127): показать документ и сказать,
   * чем кончилась первая попытка. «Отправляется» — WMS не ответил сразу,
   * портал повторяет сам; карточка тогда перечитывается, пока открыта.
   */
  function sent(type, card){
    const doc = toDocument(type, card);
    show(type, doc, true);
    clearReasons(type);
    if (typeof notify === 'function'){
      notify(doc.statusCode === PORTAL.STATUS.SENDING
          ? T('common.message.document_sending', {no: doc.no})
          : doc.statusCode === PORTAL.STATUS.SENDING_FAILED
            ? T('sku.text.sending_to_wms_failed_the_document')
            : T('common.message.document_sent_to_wms', {no: doc.no}));
    }
    if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    watchSending(type, doc);
  }

  /** Как часто перечитывать «Отправляется»: портал повторяет раз в минуту. */
  const SENDING_REFRESH_MS = 30000;

  /**
   * Пока документ «Отправляется» и открыт в карточке, перечитывать его:
   * человек увидит «Отправлен» или «Ошибку отправки», не нажимая ничего.
   * Ушёл с карточки или открыл другой документ — перечитывание кончается.
   */
  function watchSending(type, doc){
    if (!doc || doc.statusCode !== PORTAL.STATUS.SENDING) return;
    setTimeout(() => {
      const now = refOf(type);
      if (!now || now.docId !== doc.docId || now.statusCode !== PORTAL.STATUS.SENDING) return;
      openFromApi(type, doc.docId, true)
        .then(fresh => {
          if (fresh.statusCode === PORTAL.STATUS.SENDING) watchSending(type, fresh);
          else if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
        })
        .catch(() => {});
    }, SENDING_REFRESH_MS);
  }

  /** «Отправить ещё раз» у документа с ошибкой отправки (V127). */
  async function resend(type){
    const ref = refOf(type);
    if (!ref || !ref.docId) return;
    try {
      sent(type, await api('POST', '/api/' + type + '/' + ref.docId + '/resend'));
    } catch (e){
      showRefusal(type, e.message || String(e));
      tell(e);
    }
  }

  async function remove(type){
    const ref = refOf(type);
    if (!ref) return;
    // Документа в базе нет — удалять нечего, карточку просто закрываем.
    if (!ref.docId){
      PORTAL.card.closeAskDelete(type);
      PORTAL.card.forget(type);
      showTab(type);
      return;
    }
    try {
      await api('DELETE', '/api/' + type + '/' + ref.docId, undefined, seen(ref.version));
      PORTAL.card.closeAskDelete(type);
      PORTAL.card.forget(type);
      showTab(type);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
      if (typeof notify === 'function') notify(T('common.message.draft_deleted', {no: ref.no}));
    } catch (e){ tell(e); }
  }


  /* ---------------- строки состава ---------------- */

  /**
   * Состав изменился в карточке (окно строки, удаление строк).
   *
   * <p>Сохранённый черновик сохраняется сразу, целиком. Несохранённый
   * в базу не проваливается: пока человек не нажал «Сохранить», его там
   * нет — строки живут в памяти карточки и уйдут вместе с остальным.
   */
  function linesChanged(type){
    const ref = PORTAL.card.doc(type);
    if (ref && ref.docId && ref.statusCode === PORTAL.STATUS.DRAFT){
      save(type, true).catch(tell);
    }
  }

  /* ---------------- транспортный раздел (решение Р5) ---------------- */

  /**
   * Можно ли править транспорт.
   *
   * <p>С V84 — в черновике (портал хранит его до подтверждения и отправит
   * вместе с документом) и после отправки — в статусах с флагом
   * `allows_transport_edit`. В обоих случаях нужно личное право
   * пользователя.
   */
  function isDraftDoc(doc){
    return !!doc && doc.statusCode === PORTAL.STATUS.DRAFT;
  }

  function transportEditable(doc){
    return !!(doc && me.canEditTransport && (isDraftDoc(doc) || doc.allowsTransportEdit));
  }

  /**
   * Нужна ли отдельная кнопка «Сохранить транспортную информацию».
   * В черновике — нет: поля уходят общей кнопкой «Сохранить», а две
   * кнопки, делающие одно и то же, только путают.
   */
  function transportSavedSeparately(doc){
    return !!(doc && !isDraftDoc(doc) && doc.allowsTransportEdit && me.canEditTransport);
  }

  /**
   * Строка под транспортным разделом: когда он уйдёт или чем кончилась
   * передача. «Не передан» — с причиной и кнопкой повтора (V84).
   */
  function transportStateHtml(docType, doc){
    if (!doc) return '';
    const box = (color, html) => '<div style="margin-top:16px;font-size:13px;color:' + color + ';">'
        + html + '</div>';
    if (isDraftDoc(doc)){
      return transportEditable(doc)
        ? box('var(--muted)', PORTAL.t('common.hint.transport_goes_with_document')) : '';
    }
    const html = s => String(s === null || s === undefined ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    if (doc.transportState === PORTAL.TRANSPORT.SENT){
      return box('var(--muted)', html(T('common.text.transport_sent',
          {time: PORTAL.dateTimeText(doc.transportSentAt)})));
    }
    if (doc.transportState === PORTAL.TRANSPORT.NOT_SENT){
      const again = me.canEditTransport
        ? ' <button class="btn ghost" style="margin-left:8px;" ' + PORTAL.call('PORTAL.draft.resendTransport', docType) + '>'
          + html(T('common.button.resend_transport')) + '</button>'
        : '';
      return box('#991b1b', html(T('common.text.transport_not_sent', {error: doc.transportError || '—'})) + again);
    }
    return '';
  }

  async function resendTransport(type){
    const ref = PORTAL.card.doc(type);
    if (!ref || !ref.docId) return;
    try {
      await api('POST', '/api/' + type + '/' + ref.docId + '/transport/resend');
      const doc = await openFromApi(type, ref.docId, true);
      if (typeof notify === 'function'){
        notify(doc && doc.transportState === PORTAL.TRANSPORT.SENT
          ? T('sku.text.transport_details_sent_to_wms')
          : T('common.text.transport_not_sent', {error: (doc && doc.transportError) || '—'}));
      }
    } catch (e){ tell(e); }
  }

  /** После отправки транспорт уходит в WMS отдельным запросом — поэтому и кнопка отдельная. */
  async function saveTransport(type){
    const ref = PORTAL.card.doc(type);
    if (!ref || !ref.docId) return;
    const fields = ['carrier','transportType','transportBrand','carNumber','trailerNumber',
                  'techPassportNumber','containerNumber','sealNumber',
                  'driverName','driverPhone','driverIdNumber','driverInn'];
    const prefix = EXTRA_PREFIX[type] + '_';
    const store = {};
    fields.forEach(f => {
      const el = document.getElementById(prefix + f);
      if (!el) return;
      const col = f.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
      store[col] = (el.value || '').trim() === '' || el.value === '—' ? null : el.value.trim();
    });
    try {
      await api('PUT', '/api/' + type + '/' + ref.docId + '/transport', store);
      const doc = await openFromApi(type, ref.docId, true);
      // Сохранено в портале всегда; дошло ли до WMS — по состоянию передачи
      if (typeof notify === 'function'){
        notify(doc && doc.transportState === PORTAL.TRANSPORT.NOT_SENT
          ? T('common.text.transport_not_sent', {error: doc.transportError || '—'})
          : T('sku.text.transport_details_sent_to_wms'));
      }
    } catch (e){ tell(e); }
  }

  /* ---------------- открытие документа из списка ---------------- */

  /** Щелчок по строке списка: тип — по тому, в каком списке документ. */
  window.portalOpenDocument = function(no){
    const type = (PORTAL.docs.receipts || []).some(r => r.no === no) ? 'receipts'
               : ((PORTAL.docs.orders || []).some(r => r.no === no) ? 'orders' : 'transfers');
    const row = (PORTAL.docs[type] || []).find(r => r.no === no);
    if (row && row.docId){
      openFromApi(type, row.docId).catch(tell);
      return;
    }
    if (typeof notify === 'function') notify(T('common.message.document_not_in_list', {no: no}));
  };

  /* ---------------- наружу ---------------- */

  /**
   * Черновик документа — для карточки (doc-card.js), окна строки
   * (line-modal.js), кнопок разметки и проверок.
   */
  PORTAL.draft = {
    refs, me, loadDirectories,
    /** Открыть документ по идентификатору — из загрузки Excel, ссылок и проверок. */
    open: (type, docId) => openFromApi(type, docId).catch(tell),
    create: type => createNew(type).catch(tell),
    save: type => save(type).catch(tell),
    confirm,
    resend,
    remove,
    saveTransport,
    resendTransport,
    transportEditable,
    transportSavedSeparately,
    transportStateHtml,
    keepUnsaved,
    linesChanged,
    addressOptions,
    consigneeChanged,
    addressChanged
  };

  function install(){
    guardLeave();
    // Перечитать справочники окна строки: товары, упаковки, склады.
    //
    // Окно читает их один раз за вход и держит у себя (refs). Пока
    // справочник меняли только руками в другом месте, это выглядело
    // безобидно, а было ошибкой: заведённая в карточке упаковка не
    // появлялась в окне строки до следующего входа. С загрузкой из Excel
    // это стало заметно — сто новых упаковок, и ни одной в окне.
    //
    // PORTAL.catalog.packaging сбрасываем тоже: loadDirectories берёт
    // упаковки оттуда, если они там уже есть, и без сброса прочитала бы
    // прежний набор.
    PORTAL.reloadDirectories = () => {
      refs.loaded = false;
      if (PORTAL.catalog) PORTAL.catalog.packaging = [];
      return loadDirectories().catch(() => {});
    };

    // справочники зависят от поклажедателя — перечитываем их при каждом входе
    PORTAL.onLogin(() => {
      refs.loaded = false;
      if (PORTAL.lineModal) PORTAL.lineModal.forgetStock();
      Object.keys(addresses).forEach(id => delete addresses[id]);
      loadDirectories().catch(() => {});
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();