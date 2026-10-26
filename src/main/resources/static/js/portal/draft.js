
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /* ---------------- обращение к API ---------------- */

  async function api(method, path, body){
    const resp = await fetch(path, {
      method: method,
      headers: {'Content-Type':'application/json', 'Accept':'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (resp.status === 204) return null;
    const text = await resp.text();
    const data = text ? JSON.parse(text) : null;
    if (!resp.ok){
      const e = new Error(PORTAL.errorText(resp.status, data));
      e.status = resp.status;
      throw e;
    }
    return data;
  }

  /* ---------------- справочники для выпадающих списков ---------------- */

  const refs = {loaded:false, warehouses:[], suppliers:[], consignees:[], skuByCode:{},
                packagings:[], orderTypes:[], docTypes:[]};
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

    // Окно ввода строки ищет товар в массиве products — наполняем его
    // настоящей номенклатурой, чтобы поиск работал по данным базы.
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
        isInWms: x.isInWms !== false, stock: 0, reserved: 0, defect: 0,
        // демо иначе придумает характеристики само — отдаём настоящие
        __full: {cost: x.cost, currency: x.currencyCode || 'KZT',
                 itemCode: x.itemCode || '—', itemFullName: x.itemFullName || x.name,
                 customAttrs: Array.from({length:10}, () => '—')}
      };
    });
    fillProducts();
    // Упаковки нужны не только окну строки: колонка «Используются упаковки»
    // в списке товаров считает их по тому же массиву. Наполняем сразу,
    // чтобы ответ не зависел от того, открывали ли окно.
    fillPackagings();
    refs.loaded = true;
    return refs;
  }

  /**
   * Массив products демо переназначается при входе и смене поклажедателя,
   * поэтому наполняем его не один раз, а перед каждым открытием окна строки:
   * поиск товара в окне работает именно по нему.
   */
  function fillProducts(){
    if (typeof products === 'undefined' || !refs.skuList) return;
    products.length = 0;
    refs.skuList.forEach(p => products.push(p));
  }

  /**
   * Выпадающие списки должны показывать наименование, а отправлять
   * идентификатор: наименования могут совпадать, идентификатор уникален.
   * Штатный kvField умеет только простые строки, поэтому расширяем его
   * вариантами вида {value, text}, не ломая прежнее поведение.
   */
  const kvFieldOriginal = window.kvField;
  window.kvField = function(label, value, opts){
    opts = opts || {};
    const options = opts.options || [];
    const complex = options.length && typeof options[0] === 'object';
    if (opts.type !== 'select' || !complex){
      return kvFieldOriginal(label, value, opts);
    }
    const esc = s => String(s === null || s === undefined ? '' : s).replace(/"/g,'&quot;');
    const idAttr = opts.id ? 'id="' + opts.id + '"' : '';
    // onchange прототипа раньше терялся: список грузополучателей его
    // задаёт, чтобы перечитать адреса доставки, а обёртка собирала
    // разметку заново и атрибут не переносила.
    const onchangeAttr = opts.onchange ? ' onchange="' + esc(opts.onchange) + '"' : '';
    const selected = String(value === null || value === undefined ? '' : value);
    const placeholder = options.some(o => String(o.value) === selected)
        ? '' : '<option value="" selected>' + T('sku.text.not_selected') + '</option>';
    const html = options.map(o =>
        '<option value="' + esc(o.value) + '"' +
        (String(o.value) === selected ? ' selected' : '') + '>' + esc(o.text) + '</option>').join('');
    // Подпись может прийти готовой русской строкой — от прототипа,
    // который зовёт kvField напрямую; её по-прежнему переводим. Для
    // подписей, уже разрешённых через PORTAL.fieldLabel, это пустая
    // операция: ни ключа, ни колонки тут нет, и текст возвращается как есть.
    const label2 = (typeof localizeColumnLabel === 'function') ? localizeColumnLabel(label) : label;
    return '<div class="kv"><label>' + label2 + '</label>'
         + '<select class="kv-input kv-input-editable" ' + idAttr + onchangeAttr + '>'
         + placeholder + html + '</select></div>';
  };

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
    const esc = v => String(v === null || v === undefined ? '' : v).replace(/"/g, '&quot;');
    const options = list.map(a => '<option value="' + esc(a.addressId) + '"'
        + (String(a.addressId) === String(chosen) ? ' selected' : '') + '>'
        + esc(a.address) + '</option>').join('');
    const placeholder = chosen ? ''
        : '<option value="" selected>' + T('sku.text.not_selected') + '</option>';
    field.innerHTML = placeholder + options;
  }

  /**
   * Сменился грузополучатель — прежний адрес больше не его.
   *
   * <p>Поэтому выбранное место доставки сбрасывается, а не остаётся:
   * адрес чужой организации в документе хуже пустого поля.
   */
  window.portalOrderConsigneeChanged = function(){
    const consigneeId = (document.getElementById('of_consignee') || {}).value || '';
    paintAddresses('');
    if (!consigneeId) return;
    loadAddresses(consigneeId)
      .then(() => {
        const main = defaultAddress(consigneeId);
        paintAddresses(main ? main.addressId : '');
      })
      .catch(() => {});
  };

  /* ---------------- поля шапки: что куда сохраняется ---------------- */

  // поле карточки → колонка базы
  const FIELDS = {
    receipts: {
      rf_erpNo:'erp_number', rf_warehouse:'warehouse_id', rf_supplier:'supplier_id',
      rf_docType:'doc_type_id', rf_eta:'eta_date',
      rf_accompanyingDocNumber:'accompanying_doc_number', rf_invoiceNumber:'invoice_number',
      rf_gtd:'gtd', rf_comment:'comment', rf_trip:'trip'
    },
    orders: {
      of_erpNo:'erp_number', of_warehouse:'warehouse_id', of_consignee:'consignee_id',
      of_deliveryAddress:'delivery_point_id', of_consigneeContact:'consignee_contact',
      of_orderType:'order_type_id', of_plannedShip:'planned_ship_date',
      of_deliveryDate:'delivery_date', of_comment:'comment', of_trip:'trip'
    },
    transfers: {
      tf_erpNo:'erp_number', tf_fromWarehouse:'from_warehouse_id',
      tf_toWarehouse:'to_warehouse_id', tf_comment:'comment', tf_trip:'trip'
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

  /**
   * Поля, которые портал показывает, но не задаёт.
   *
   * <p>Дату создания ставит база при создании черновика. Количество
   * строк, штук, грузомест и паллет, вес и объём — измеренные склада́ми
   * факты, они приходят из WMS. Дата отгрузки перемещения — тоже склад:
   * он ставит её, когда действительно отгружает.
   *
   * <p>Сервер такие поля не принимает (их нет в editableHeaderColumns),
   * поэтому поле, в которое можно печатать, а оно не сохраняется, было бы
   * обманом. Запираем их в самой отрисовке, а не правкой готовой разметки:
   * карточка перерисовывается при каждом переключении вкладки, и правка
   * «после» отваливалась бы каждый раз.
   */
  const READ_ONLY = {
    common: ['date', 'lineCount', 'totalPieces', 'palletCount', 'cargoPlaces', 'weight', 'volume'],
    transfers: ['shipDate']
  };

  function readOnlyField(tableKey, dbField){
    return READ_ONLY.common.indexOf(dbField) >= 0
        || (READ_ONLY[tableKey] || []).indexOf(dbField) >= 0;
  }

  /* ---------------- текущий документ: три типа, одни правила ----------------
     Прототип держит открытый документ и его состав в своих переменных —
     по паре на тип. Обращаемся к ним через эти три функции, чтобы
     остальной код не повторял один и тот же выбор из трёх вариантов. */

  function refOf(type){
    if (type === 'receipts') return currentReceiptRef;
    if (type === 'orders') return currentOrderRef;
    return currentTransferRef;
  }

  function compositionOf(type){
    if (type === 'receipts') return currentReceiptComposition;
    if (type === 'orders') return currentOrderComposition;
    return currentTransferComposition;
  }

  function forgetRef(type){
    if (type === 'receipts') currentReceiptRef = null;
    else if (type === 'orders') currentOrderRef = null;
    else currentTransferRef = null;
  }

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
   * <p>Прототип собирает идентификаторы именно так — префикс типа
   * документа плюс имя поля, — и это единственная связь между полем
   * на экране и объектом, из которого карточка рисуется.
   */
  const docField = id => id.replace(/^(rf|of|tf)_/, '');

  /** Документ, из которого прототип рисует карточку (его держит show). */
  function docOf(type){
    const list = type === 'receipts' ? receipts : (type === 'orders' ? orders : transfers);
    return (list && list[0]) || null;
  }

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
   * по-русски, иначе kvField прототипа (он ждёт русскую дату) покажет
   * пустое поле.
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
   * Переключение вкладки карточки — с сохранением набранного в памяти.
   *
   * <p>Подменяем не отрисовку, а только момент перед ней: прототип
   * рисует вкладки сам, и переписывать это незачем.
   */
  /**
   * Уход со страницы несохранённого документа — с вопросом.
   *
   * <p>Документ появляется в базе только по кнопке «Сохранить», значит
   * у несохранённого есть что терять. Спрашиваем, но лишь когда терять
   * действительно есть что: пустая карточка закрывается молча — вопрос
   * на пустом месте человек перестаёт читать, и тогда он не поможет
   * там, где нужен.
   */
  function wrapLeave(){
    const original = window.showTab;
    if (typeof original !== 'function') return;
    window.showTab = function(tab){
      const open = ['receipts', 'orders', 'transfers']
          .find(type => { const ref = refOf(type); return ref && !ref.docId; });
      if (open && hasData(open)
          && !window.confirm(T('common.message.leave_unsaved_document'))){
        return;
      }
      if (open) forgetRef(open);
      return original.apply(this, arguments);
    };
  }

  function wrapTabSwitch(){
    const original = window.fcSwitchDocTab;
    if (typeof original !== 'function') return;
    window.fcSwitchDocTab = function(docType, tabName){
      const ref = refOf(docType);
      if (ref && ref.statusCode === 'draft'){
        keepUnsaved(docType);
      }
      return original.apply(this, arguments);
    };
  }

  /** Состав документа в том виде, в каком его ждёт API. */
  function collectLines(type){
    const composition = compositionOf(type);
    return (composition || []).map(l => {
      // строки, созданные окном демо, несут код товара, а не идентификатор
      const skuId = l.skuId || (refs.skuByCode[l.code] && refs.skuByCode[l.code].skuId);
      const line = {sku_id: skuId, line_comment: l.lineComment || null};
      if (type === 'receipts') line.expected_qty = Number(l.expected);
      else if (type === 'orders') { line.ordered_qty = Number(l.ordered); if (l.weight) line.weight_kg = Number(l.weight); }
      // У перемещения одно количество и нет цены: товар не меняет
      // владельца, меняется только место хранения.
      else { line.qty = Number(l.qty); return line; }
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
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;

  /** Карточка из API → объект документа в терминах демо. */
  function toDocument(type, card){
    const h = card.header;
    const BADGE = {draft:'draft', awaiting:'warn', in_progress:'warn', received:'ok', discrepancy:'err',
                   sent_to_wms:'warn', send_failed:'err',
                   reserved:'warn', picking:'warn', shipped:'ok', delayed:'err'};
    const totalCount = {
      docId: h.docId, no: h.documentNo,
      // пустой номер ERP оставляем пустым: заголовок карточки подставит номер портала
      erpNo: h.erpNumber || '', wmsNumber: dash(h.docNumber),
      status: BADGE[h.statusCode] || 'warn', statusLabel: h.statusName || '', statusCode: h.statusCode,
      // Дата создания — отметка времени с V62. Отрезать первые десять
      // знаков от ISO нельзя: там мгновение по UTC.
      date: PORTAL.dateTimeText(h.docDate),
      author: dash(h.authorName), comment: h.comment || '',
      trip: h.trip || '', lineCount: h.lineCount || 0, totalPieces: h.totalPieces || 0,
      weight: h.weightKg || '', volume: h.volumeM3 || '',
      palletCount: h.palletCount || '', cargoPlaces: h.cargoPlaces || '',
      carrier: h.carrier || '', transportType: h.transportType || '', transportBrand: h.transportBrand || '',
      carNumber: h.carNumber || '', trailerNumber: h.trailerNumber || '',
      techPassportNumber: h.techPassportNumber || '', containerNumber: h.containerNumber || '',
      sealNumber: h.sealNumber || '', driverName: h.driverName || '', driverPhone: h.driverPhone || '',
      driverIdNumber: h.driverIdNumber || '', driverInn: h.driverInn || '',
      allowsTransportEdit: h.allowsTransportEdit === true,
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
      expected: l.expectedQty, expectedWms: l.expectedQtyWms ?? l.expectedQty,
      // у черновика фактических количеств ещё нет — показываем прочерк, а не пустоту
      accepted: l.acceptedQty ?? '—', defect: l.defectQty ?? '—',
      ordered: l.orderedQty, orderedWms: l.orderedQtyWms ?? l.orderedQty,
      picked: l.pickedQty ?? '—', weight: l.weightKg ?? '—',
      // у перемещения своё имя количества: не «ожидается» и не «заказано»,
      // а просто «кол-во». Без него состав возвращался с экрана пустым,
      // и добавленная строка показывалась нулём.
      qty: l.qty, qtyWms: l.qtyWms ?? l.qty,
      price: l.price, currency: l.currencyCode, lineComment: l.lineComment === '—' ? '' : (l.lineComment || ''),
      lineNumber: l.lineNumber
    }));
    totalCount.items = totalCount.lines.length;
    return totalCount;
  }

  async function openFromApi(type, docId){
    await loadDirectories();
    const card = await api('GET', '/api/' + type + '/' + docId);
    const doc = toDocument(type, card);
    show(type, doc);
    clearReasons(type);
    // Адреса грузополучателя читаются после отрисовки: ждать их значит
    // задержать всю карточку из-за одного выпадающего списка. Выбранное
    // место доставки передаём явно — иначе список перерисуется пустым
    // и сохранение затёрло бы сохранённый адрес.
    if (type === 'orders' && doc.consignee){
      loadAddresses(doc.consignee)
        .then(() => paintAddresses(doc.deliveryAddress || ''))
        .catch(() => {});
    }
    return doc;
  }

  /**
   * Подкладывает документ прототипу и открывает его карточку.
   *
   * <p>Прототип рисует карточку из своего массива документов, поэтому
   * массив заменяется одним настоящим документом. Так было сделано
   * для поступлений и заказов; перемещения устроены так же.
   */
  function show(type, doc){
    // Открытую вкладку сохраняем. Прототип сбрасывает её на первую,
    // когда меняется номер документа, — а номер меняется ровно при
    // первом сохранении: человек, заполнявший «Доп. информацию»,
    // оказывался на «Общей» без всякой причины.
    try {
      const state = fcActiveDocTab[type];
      if (state) state.no = doc.no;
    } catch (e){ /* прототип ещё не загрузился — вкладку хранить негде */ }
    if (type === 'receipts'){ receipts.length = 0; receipts.push(doc); openReceipt(doc.no); }
    else if (type === 'orders'){ orders.length = 0; orders.push(doc); openOrder(doc.no); }
    else { transfers.length = 0; transfers.push(doc); openTransfer(doc.no); }
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

  /* ---------------- поля шапки: выпадающие списки из API ---------------- */

  function wrapHeaderFields(){
    // В правилах лежат КЛЮЧИ текстов, а не готовые подписи: готовая
    // вычислялась бы при загрузке файла, до входа — то есть до того, как
    // известен язык учётной записи и переименования полей клиентом.
    const wrap = (functionName, tableKey, rules) => {
      const original = window[functionName];
      if (typeof original !== 'function') return;
      window[functionName] = function(dbField, doc, editable){
        if (editable && readOnlyField(tableKey, dbField)){
          return original(dbField, doc, false);
        }
        const rule = rules[dbField];
        // Подпись поля — общим правилом портала: имя клиента, если он
        // поле переименовал, иначе перевод. Эти же поля в режиме
        // просмотра рисует прототип, и зваться они должны одинаково.
        const label = rule ? PORTAL.fieldLabel(tableKey, dbField, rule.key) : null;
        if (!rule){
          return original(dbField, doc, editable);
        }
        // Поля дат сюда не заводим нарочно. Соблазн был: <input type="date">
        // понимает только ISO, а в карточке лежит русская дата. Но kvField
        // прототипа приводит её сам (toISODate), причём ИМЕННО из русской —
        // ISO он возвращает пустым. Подсунуть ему ISO «чтобы наверняка»
        // значит обнулить поле, а следующее сохранение затрёт и дату
        // в базе. Проверено на себе: так и вышло.
        if (editable){
          return kvField(label, doc[rule.value],
              {id: rule.id, editable: true, type: 'select', options: rule.options(doc),
               onchange: rule.onchange});
        }
        return kvField(label, dash(doc[rule.name]), {id: rule.id});
      };
    };

    wrap('fcReceiptSpecialFieldHtml', 'receipts', {
      warehouse: {id:'rf_warehouse', key:'sku.label.warehouse', value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      supplier:  {id:'rf_supplier', key:'col.receipts.supplier', value:'supplier', name:'supplierName',
                  options:() => refs.suppliers},
      docType:   {id:'rf_docType', key:'col.receipts.docType', value:'docType', name:'docTypeName',
                  options:() => refs.docTypes}
    });
    // У перемещения два склада, и получателем не предлагаем тот,
    // который уже выбран отправителем: сервер такой документ всё равно
    // не подтвердит, а подсовывать заведомо неверное — неуважение
    // к человеку. Запрет держит сервер (причина error.ready.same_warehouse),
    // список лишь не мешает.
    const otherWarehouses = (fieldId, keep) => {
      const chosen = (document.getElementById(fieldId) || {}).value || '';
      // Выбранное в ЭТОМ поле значение остаётся в списке всегда, даже если
      // оно совпало с соседним: список, из которого пропало сохранённое
      // значение, молча подменил бы его первым попавшимся складом.
      return refs.warehouses.filter(w => String(w.value) === String(keep || '')
          || String(w.value) !== String(chosen));
    };

    wrap('fcTransferSpecialFieldHtml', 'transfers', {
      fromWarehouse: {id:'tf_fromWarehouse', key:'col.transfers.fromWarehouse',
                      value:'fromWarehouse', name:'fromWarehouseName',
                      options:doc => otherWarehouses('tf_toWarehouse', doc.fromWarehouse)},
      toWarehouse:   {id:'tf_toWarehouse', key:'col.transfers.toWarehouse',
                      value:'toWarehouse', name:'toWarehouseName',
                      options:doc => otherWarehouses('tf_fromWarehouse', doc.toWarehouse)}
    });
    wrap('fcOrderSpecialFieldHtml', 'orders', {
      warehouse: {id:'of_warehouse', key:'sku.label.warehouse', value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      consignee: {id:'of_consignee', key:'partners.title.consignee', value:'consignee', name:'consigneeName',
                  options:() => refs.consignees,
                  onchange:'portalOrderConsigneeChanged()'},
      // Место доставки — адреса ВЫБРАННОГО грузополучателя. Раньше список
      // брался из демо-массива по НАИМЕНОВАНИЮ организации, поэтому
      // в настоящем портале был пуст: выбрать было нечего.
      deliveryAddress: {id:'of_deliveryAddress', key:'col.orders.deliveryAddress',
                        value:'deliveryAddress', name:'deliveryAddressName',
                        options:doc => addressOptions(doc.consignee)},
      orderType: {id:'of_orderType', key:'col.orders.orderType', value:'orderType', name:'orderTypeName',
                  options:() => refs.orderTypes}
    });
  }

  /* ---------------- действия с черновиком ---------------- */

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
    if (!ref || ref.statusCode !== 'draft') return null;
    let docId = ref.docId;
    if (!docId){
      const card = await api('POST', '/api/' + type, collectHeader(type));
      docId = card.header.docId;
    } else {
      await api('PUT', '/api/' + type + '/' + docId, collectHeader(type));
    }
    await api('PUT', '/api/' + type + '/' + docId + '/lines', collectLines(type));
    const doc = await openFromApi(type, docId);
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
   * <p>Поля заданы все, какие рисует карточка: прототип дорисовывает
   * себе отсутствующее демо-данными, и пропущенное поле обернулось бы
   * выдуманным значением на экране.
   */
  function emptyDocument(type){
    const doc = {
      docId: null, no: '', erpNo: '', wmsNumber: '—',
      status: 'draft', statusCode: 'draft', statusLabel: T('receipts.option.draft'),
      date: PORTAL.dateTimeText(new Date().toISOString()),
      author: (window.currentUser && currentUser.full_name) || '',
      comment: '', trip: '', lineCount: 0, totalPieces: 0,
      weight: '', volume: '', palletCount: '', cargoPlaces: '',
      carrier: '', transportType: '', transportBrand: '', carNumber: '', trailerNumber: '',
      techPassportNumber: '', containerNumber: '', sealNumber: '',
      driverName: '', driverPhone: '', driverIdNumber: '', driverInn: '',
      allowsTransportEdit: false, hasUnknownSku: false, problems: [],
      warehouse: '', warehouseName: '—',
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
      Object.assign(doc, {fromWarehouse: '', fromWarehouseName: '—',
                          toWarehouse: '', toWarehouseName: '—', shipDate: '—'});
    }
    return doc;
  }

  /** Есть ли что терять: заполнено ли хоть одно поле или добавлена строка. */
  function hasData(type){
    if ((compositionOf(type) || []).length) return true;
    const header = collectHeader(type);
    return Object.values(header).some(v => v !== null && v !== '');
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

  window.createNewReceiptDraft = () => createNew('receipts').catch(tell);
  window.createNewOrderDraft = () => createNew('orders').catch(tell);
  window.createNewTransferDraft = () => createNew('transfers').catch(tell);

  window.saveReceiptDraft = () => save('receipts').catch(tell);
  window.saveOrderDraft = () => save('orders').catch(tell);
  window.saveTransferDraft = () => save('transfers').catch(tell);

  async function confirm(type){
    const ref = refOf(type);
    if (!ref) return;
    try {
      // Сохранение могло ТОЛЬКО ЧТО создать документ — тогда ссылка
      // на него появилась лишь после него, и прежний ref ещё без docId.
      await save(type, true);
      const saved = refOf(type);
      if (!saved || !saved.docId) return;
      const card = await api('POST', '/api/' + type + '/' + saved.docId + '/confirm');
      const doc = toDocument(type, card);
      show(type, doc);
      clearReasons(type);
      if (typeof notify === 'function'){
        notify(doc.statusCode === 'send_failed'
            ? T('sku.text.sending_to_wms_failed_the_document')
            : T('common.message.document_sent_to_wms', {no: doc.no}));
      }
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    } catch (e){
      // Отказ показываем там же, где человек его ждёт — в карточке,
      // и плашкой: плашка заметна, баннер остаётся читаемым.
      showRefusal(type, e.message || String(e));
      tell(e);
    }
  }

  window.confirmReceiptDraft = () => confirm('receipts');
  window.confirmOrderDraft = () => confirm('orders');
  window.confirmTransferDraft = () => confirm('transfers');

  async function remove(type){
    const ref = refOf(type);
    if (!ref) return;
    // Документа в базе нет — удалять нечего, карточку просто закрываем.
    if (!ref.docId){
      const closeConfirm = {receipts: 'closeDeleteReceiptDraftConfirm',
                            orders: 'closeDeleteOrderDraftConfirm',
                            transfers: 'closeDeleteTransferDraftConfirm'}[type];
      if (typeof window[closeConfirm] === 'function') window[closeConfirm]();
      forgetRef(type);
      showTab(type);
      return;
    }
    try {
      await api('DELETE', '/api/' + type + '/' + ref.docId);
      const closeConfirm = {receipts: 'closeDeleteReceiptDraftConfirm',
                            orders: 'closeDeleteOrderDraftConfirm',
                            transfers: 'closeDeleteTransferDraftConfirm'}[type];
      if (typeof window[closeConfirm] === 'function') window[closeConfirm]();
      forgetRef(type);
      showTab(type);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
      if (typeof notify === 'function') notify(T('common.message.draft_deleted', {no: ref.no}));
    } catch (e){ tell(e); }
  }

  window.deleteReceiptDraft = () => remove('receipts');
  window.deleteOrderDraft = () => remove('orders');
  window.deleteTransferDraft = () => remove('transfers');

  /* ---------------- строки состава ---------------- */

  // Окно ввода строки остаётся демонстрационным: поиск товара, упаковка,
  // пересчёт, цена, объединение дублей. Мы подключаемся после сохранения
  // и отправляем получившийся состав целиком.
  function afterLinesChanged(functionName, type){
    const original = window[functionName];
    if (typeof original !== 'function') return;
    window[functionName] = function(){
      const result = original.apply(this, arguments);
      const ref = refOf(type);
      // Несохранённый документ строкой состава в базу не проваливается:
      // пока человек не нажал «Сохранить», его там нет. Строки живут
      // в памяти карточки и уйдут вместе с остальным.
      if (ref && ref.docId && ref.statusCode === 'draft'){
        save(type, true).catch(tell);
      }
      return result;
    };
  }

  /* ---------------- состав: позиции, неизвестные WMS ---------------- */

  function markUnknownRows(){
    ['receipt','order','transfer'].forEach(prefix => {
      const original = window['render' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Composition'];
      if (typeof original !== 'function') return;
      window['render' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Composition'] = function(){
        const result = original.apply(this, arguments);
        const composition = compositionOf(prefix + 's');
        const rows = document.querySelectorAll('#' + prefix + 'DetailRows tr');
        (composition || []).forEach((l, i) => {
          const row = rows[i];
          if (!row || l.skuInWms !== false) return;
          row.style.background = '#fff6e6';
          const cells = Array.from(row.querySelectorAll('td'));
          const cell = cells.find(td => td.textContent.trim() === String(l.code)) || cells[0];
          if (cell && !cell.querySelector('.pd-chip')){
            cell.insertAdjacentHTML('beforeend',
              '<span class="pd-chip" style="margin-left:8px; padding:1px 8px; border-radius:10px;'
              + ' background:#f6c343; color:#4a3500; font-size:11px;">' + T('sku.text.not_in_wms') + '</span>');
          }
        });
        return result;
      };
    });
  }

  /* ---------------- транспортный раздел (решение Р5) ---------------- */

  /**
   * В черновике транспорт не редактируется: WMS ещё не знает о документе
   * и связывать транспортные сущности не с чем. Разрешённые статусы задаются
   * флагом на статусе в базе, а право пользователя — его настройками.
   */
  window.isTransportEditable = function(doc){
    return !!(doc && doc.allowsTransportEdit && me.canEditTransport);
  };

  /** Транспорт уходит в WMS отдельным запросом, поэтому и кнопка отдельная. */
  async function saveTransport(type){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref) return;
    const fields = ['trip','carrier','transportType','transportBrand','carNumber','trailerNumber',
                  'techPassportNumber','containerNumber','sealNumber',
                  'driverName','driverPhone','driverIdNumber','driverInn'];
    const prefix = type === 'receipts' ? 'rf_' : 'of_';
    const store = {};
    fields.forEach(f => {
      const el = document.getElementById(prefix + f);
      if (!el) return;
      const col = f.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
      store[col] = (el.value || '').trim() === '' || el.value === '—' ? null : el.value.trim();
    });
    try {
      await api('PUT', '/api/' + type + '/' + ref.docId + '/transport', store);
      await openFromApi(type, ref.docId);
      if (typeof notify === 'function') notify(T('sku.text.transport_details_sent_to_wms'));
    } catch (e){ tell(e); }
  }
  window.portalSaveTransport = saveTransport;
  /** Открыть документ по идентификатору — используется проверками и ссылками. */
  window.portalOpenDocumentById = (type, docId) => openFromApi(type, docId).catch(tell);

  /**
   * Кнопка «Сохранить транспортную информацию» в прототипе уже есть —
   * подменяем только то, что происходит по нажатию.
   */
  window.fcSaveTransportSection = function(docType){
    if (SCREEN[docType]){
      saveTransport(docType);
      return;
    }
    if (typeof notify === 'function') notify(T('sku.message.transport_details_are_not_availabl'));
  };

  /* ---------------- остатки в окне ввода строки ---------------- */

  /**
   * Числа «Остаток», «Доступно» и «К получению» в окне ввода строки.
   *
   * <p>Раньше их там не было ни у одного документа, кроме перемещения.
   * Номенклатура попадала в окно с нулями (`stock: 0, reserved: 0`),
   * а «К получению» прототип считал псевдослучайно от хэша кода товара —
   * то есть показывал выдуманное число. Выдуманное хуже нуля: ноль видно,
   * что заглушка, а 37 выглядит как данные.
   *
   * <p>Числа берутся ПО СКЛАДУ ДОКУМЕНТА, а не по всем складам клиента:
   * со склада можно забрать только то, что на нём лежит, и ровно это
   * решение уже принято для перемещения. Для перемещения склад — это
   * склад-отправитель.
   *
   * <p>Превышение доступного количества не запрещается, а предупреждается:
   * портал владеет копией остатков, а не оригиналом (решение Р4, §8.3.2),
   * и последнее слово остаётся за WMS.
   */

  // Какое поле шапки задаёт склад для каждого типа документа.
  const STOCK_WAREHOUSE_FIELD = {
    lineEdit: 'rf_warehouse',
    orderLineEdit: 'of_warehouse',
    transferLineEdit: 'tf_fromWarehouse'
  };

  // Прочитанные остатки: склад → код товара → строка (или null, если
  // товара на складе нет). Спрашиваем по одному товару, а не склад
  // целиком: запасы отдаются страницами (V79), и «прочитать весь
  // склад» теперь значит «прочитать первые двести строк» — на двести
  // первой позиции окно молча показало бы ноль вместо остатка.
  const stockByWarehouse = {};

  async function stockFor(warehouseId, code){
    if (!warehouseId || !code) return null;
    const byCode = stockByWarehouse[warehouseId]
        || (stockByWarehouse[warehouseId] = {});
    if (Object.prototype.hasOwnProperty.call(byCode, code)) return byCode[code];

    const answer = await api('GET', '/api/stock'
        + '?filter.warehouse=' + encodeURIComponent(warehouseId)
        + '&filter.code=' + encodeURIComponent(code)
        + '&pageSize=50');
    // Текстовый фильтр ищет ВХОЖДЕНИЕ: «RK-1» находит и «RK-10».
    // Поэтому нужную строку выбираем сами — и по коду, и по складу.
    //
    // Склад сверяется второй раз нарочно. Он уже стоял в запросе,
    // но если его значение окажется не тем, чем мы его считаем
    // (например именем склада вместо идентификатора), сервер вернёт
    // строки другого склада, а окно покажет их как свои. Лучше
    // показать ноль, чем чужое число.
    const same = (a, b) => String(a === null || a === undefined ? '' : a).trim().toUpperCase()
        === String(b === null || b === undefined ? '' : b).trim().toUpperCase();
    const rows = answer.rows || [];
    const row = rows.find(r => same(r.skuCode, code) && same(r.warehouseId, warehouseId))
        || null;
    if (!row && rows.length){
      // Строки пришли, а нужной среди них нет — это не «нет остатка»,
      // это непонимание между экраном и сервером, и молчать о нём
      // нельзя: в окне появится ноль, похожий на ответ.
      console.warn(`остатки: ${code}/${warehouseId} — среди ${rows.length} строк нужной нет`);
    }
    byCode[code] = row;
    return row;
  }

  /** Дочитать остаток выбранного товара и перерисовать числа окна. */
  function refreshStock(prefix){
    const product = selectedProduct(prefix);
    const warehouseId = warehouseForModal(prefix);
    const code = product && product.code;
    return stockFor(warehouseId, code)
        .then(() => paintStock(prefix))
        .catch(() => {
          // Запрос не удался — числа НЕИЗВЕСТНЫ, и сказать об этом
          // обязательно. Прежде ошибку просто глотали, а в полях
          // оставались нули, нарисованные демо-вёрсткой до нашего
          // ответа. Ноль выглядит как ответ: человек прочтёт
          // «остатка нет» там, где портал просто не смог спросить.
          const byCode = stockByWarehouse[warehouseId];
          if (byCode) delete byCode[code];
          paintStock(prefix);
        });
  }

  /** Склад, по которому считаются остатки для этого окна. */
  function warehouseForModal(prefix){
    const field = document.getElementById(STOCK_WAREHOUSE_FIELD[prefix]);
    if (field && field.value) return field.value;
    // Поля на экране может не быть: карточка уже не черновик и шапка
    // нарисована текстом. Тогда склад берётся из самого документа.
    const ref = prefix === 'lineEdit' ? currentReceiptRef
              : (prefix === 'orderLineEdit' ? currentOrderRef : currentTransferRef);
    if (!ref) return null;
    return prefix === 'transferLineEdit' ? ref.fromWarehouse : ref.warehouse;
  }

  /**
   * Товар, выбранный в окне строки.
   *
   * <p>Прототип объявил эти переменные через let на верхнем уровне. Такие
   * объявления не создают свойства у window, поэтому читаем их по голому
   * имени, прикрывшись typeof, — как это уже делает columns.js. Обращение
   * через window здесь однажды уже стоило работающего предупреждения
   * о превышении остатка: оно молча не срабатывало никогда.
   */
  function selectedProduct(prefix){
    const name = prefix + 'SelectedProduct';
    try {
      return eval('typeof ' + name) !== 'undefined' ? eval(name) : null;
    } catch (e){ return null; }
  }

  /**
   * Пишет три числа в окно — и только их.
   *
   * <p>Отрисовка окна целиком здесь не годится: она заодно сбрасывает
   * выбранную упаковку и количество, и если ответ с остатками приходит
   * после того, как человек уже набрал число, число пропадает.
   *
   * <p>Товара нет в прочитанных остатках — это ноль, а не «неизвестно»:
   * склад выбран, остатки по нему прочитаны, позиции в них нет. А вот
   * когда склад ещё не выбран, числа неизвестны — и тогда «—».
   */
  function paintStock(prefix){
    const fields = ['StockTotal', 'StockAvail', 'StockIncoming']
      .map(name => document.getElementById(prefix + name));
    const product = selectedProduct(prefix);
    const byCode = stockByWarehouse[warehouseForModal(prefix)];
    const row = product && byCode ? byCode[product.code] : undefined;
    // Три разных случая, и путать их нельзя:
    //   товара не выбрано или про него ещё не спрашивали — «—»;
    //   спросили, на складе нет                          — ноль;
    //   спросили, есть                                   — числа.
    // Прежде первые два были одним: окно показывало ноль, пока летел
    // ответ, и ноль же оставался, если остатка нет. Ноль выглядит
    // как ответ, и человек верит ему раньше, чем он стал правдой.
    if (!product || row === undefined){
      fields.forEach(el => { if (el) el.textContent = '—'; });
      return;
    }
    const unit = product.unit || '';
    const values = row ? [row.quantity, row.availableQty, row.incomingQty] : [0, 0, 0];
    fields.forEach((el, i) => {
      if (el) el.textContent = (values[i] === undefined ? 0 : values[i]) + ' ' + unit;
    });
  }

  /** После штатной отрисовки окна переписываем числа настоящими. */
  function stockFields(prefix){
    const name = 'fill' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'ProductInfo';
    const original = window[name];
    if (typeof original !== 'function') return;
    window[name] = function(){
      const result = original.apply(this, arguments);
      // Товар выбран — спрашиваем остаток именно по нему. До ответа
      // в полях стоит «—», а не ноль.
      refreshStock(prefix);
      return result;
    };
  }

  /** Предупреждение о превышении доступного количества. */
  function stockWarning(prefix, qtyFieldId, textKey){
    const name = 'save' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Modal';
    const original = window[name];
    if (typeof original !== 'function') return;
    window[name] = function(){
      const product = selectedProduct(prefix);
      const field = document.getElementById(qtyFieldId);
      const count = field ? Number(field.value) : null;
      const byCode = stockByWarehouse[warehouseForModal(prefix)];
      const row = product && byCode ? byCode[product.code] : null;
      const result = original.apply(this, arguments);
      if (row && count && count > Number(row.availableQty) && typeof notify === 'function'){
        notify(T(textKey, {count: count + ' ' + (product.unit || ''),
                           available: row.availableQty + ' ' + (product.unit || '')}));
      }
      return result;
    };
  }

  /* ---------------- упаковки: пересчёт количества ---------------- */

  /**
   * Окно строки умеет считать количество по упаковке: «5 паллет × 100 =
   * 500 кг». Список упаковок оно берёт из массива PRODUCT_PACKAGING
   * прототипа, а там лежали демо-данные с демо-идентификаторами клиентов.
   * Совпадений в настоящем портале нет, а при пустом списке окно прячет
   * оба поля — значит пересчёт не просто не работал, его не было видно.
   *
   * <p>Наполняем массив настоящими упаковками клиента по тому же правилу,
   * что и products: заменяется содержимое, а не переменная — PRODUCT_PACKAGING
   * объявлен через const, присвоить ему нельзя.
   *
   * <p>Поле depositorId проставляем текущим значением прототипа: окно
   * фильтрует по нему список, а /api/packaging/options и так отдаёт упаковки
   * только того клиента, с которым работают, — RLS не отдаст чужих.
   */
  function fillPackagings(){
    if (typeof PRODUCT_PACKAGING === 'undefined' || !refs.packagings) return;
    let depositorId = null;
    try { depositorId = eval('typeof currentDepositorId') !== 'undefined'
        ? eval('currentDepositorId') : null; } catch (e){ depositorId = null; }
    PRODUCT_PACKAGING.length = 0;
    refs.packagings.forEach(p => PRODUCT_PACKAGING.push({
      productCode: p.skuCode, depositorId: depositorId,
      packagingCode: p.packagingCode, barcode: p.barcode || '',
      type: p.packagingTypeName || p.packagingCode,
      coefficient: Number(p.coefficient) || 1,
      length: p.lengthCm, width: p.widthCm, height: p.heightCm,
      volume: p.volumeM3, weightGross: p.grossWeightKg
    }));
  }
  /* ---------------- открытие документа из списка ---------------- */

  const demoView = window.portalOpenDocument;
  window.portalOpenDocument = function(no){
    const type = (PORTAL.docs.receipts || []).some(r => r.no === no) ? 'receipts'
               : ((PORTAL.docs.orders || []).some(r => r.no === no) ? 'orders' : 'transfers');
    const row = (PORTAL.docs[type] || []).find(r => r.no === no);
    if (row && row.docId){
      openFromApi(type, row.docId).catch(tell);
      return;
    }
    if (typeof demoView === 'function') demoView(no);
  };

  /**
   * Перед открытием окна ввода строки подкладываем настоящие справочники
   * и дочитываем остатки по складу документа.
   *
   * <p>Остатки читаются именно здесь, а не при входе: склад выбирают
   * в шапке, и до открытия окна он мог быть ещё не выбран или смениться.
   * Прочитанное запоминается по складу, так что второе и последующие
   * открытия окна запроса не делают.
   */
  function wrapLineModal(){
    const PREFIX = {Line: 'lineEdit', OrderLine: 'orderLineEdit', TransferLine: 'transferLineEdit'};
    Object.keys(PREFIX).forEach(part => {
      ['open' + part + 'AddModal', 'open' + part + 'EditModal'].forEach(fieldName => {
        const original = window[fieldName];
        if (typeof original !== 'function') return;
        window[fieldName] = function(){
          fillProducts();
          fillPackagings();
          const prefix = PREFIX[part];
          const result = original.apply(this, arguments);
          // Запрос уходит после открытия окна: ждать его значит показать
          // человеку пустое окно на время ответа. Числа дорисуются сами.
          refreshStock(prefix);
          // Правка строки приходит с её номером, добавление — без него.
          applyPriceSetting(prefix, fieldName.indexOf('Edit') >= 0 ? arguments[0] : null);
          return result;
        };
      });
    });
  }

  /* ---------------- цена и сумма: настройка поклажедателя ---------------- */

  /**
   * Нужны ли этому клиенту цена и сумма в окне ввода строки (V61).
   *
   * <p>Большинству клиентов цена в документе не нужна: портал ведёт склад,
   * а не торговлю. Настройка отдельная по типу документа — цена бывает
   * осмысленна в поступлении и бессмысленна в заказе у одного и того же
   * клиента. У перемещения цены нет вовсе, и окно перемещения этих полей
   * не имеет.
   *
   * <p>Это удобство, а не право: сервер по-прежнему принимает цену, и уже
   * введённые цены скрытое поле не стирает (§6.3 про то же самое —
   * спрятанное поле защитой не считается).
   */
  function priceAllowed(prefix){
    if (prefix === 'lineEdit') return me.priceInReceipts === true;
    if (prefix === 'orderLineEdit') return me.priceInOrders === true;
    return false;
  }

  function applyPriceSetting(prefix, lineIndex){
    if (prefix === 'transferLineEdit') return;
    const show = priceAllowed(prefix);
    ['Price', 'Sum'].forEach(name => {
      const field = document.getElementById(prefix + name);
      const row = field && field.closest ? field.closest('.kv') : null;
      if (row) row.style.display = show ? '' : 'none';
    });
    if (show) return;

    // Поле спрятали, но окно уже подставило в него цену из карточки
    // товара, а сохранение состава перезаписывает строки целиком. Если
    // ничего не делать, выключенная настройка молча проставила бы цены
    // всем строкам, которые человек открыл. Поэтому в спрятанном поле
    // остаётся ровно то, что в строке и было: у новой — ничего.
    const field = document.getElementById(prefix + 'Price');
    if (!field) return;
    const type = prefix === 'lineEdit' ? 'receipts' : 'orders';
    const line = lineIndex === null || lineIndex === undefined
        ? null : (compositionOf(type) || [])[lineIndex];
    const price = line && line.price !== null && line.price !== undefined ? line.price : '';
    field.value = price;
    const sum = document.getElementById(prefix + 'Sum');
    if (sum) sum.textContent = '—';
  }

  /**
   * Прячет кнопки Excel у перемещений.
   *
   * <p>Загрузка строк перемещения из файла не сделана, и кнопки в вёрстке
   * остались от демо: по нажатию они показывают плашку «Скачивается…»,
   * а файла нет. Кнопка, которая выглядит рабочей и не работает, хуже
   * отсутствующей — так уже поступили с упаковками.
   */
  function hideDeadExcelButtons(){
    const host = document.getElementById('view-transfers');
    if (!host) return;
    host.querySelectorAll('button').forEach(btn => {
      const onclick = btn.getAttribute('onclick') || '';
      if (onclick.indexOf('Шаблон_Перемещение') >= 0 || onclick.indexOf('Excel с перемещением') >= 0){
        btn.style.display = 'none';
      }
    });
  }

  function install(){
    wrapHeaderFields();
    wrapLeave();
    wrapTabSwitch();
    wrapLineModal();
    // Числа остатков — во всех трёх окнах. Предупреждение о превышении —
    // только там, где товар со склада забирают: поступление его привозит,
    // и превышать там нечего.
    ['lineEdit', 'orderLineEdit', 'transferLineEdit'].forEach(stockFields);
    stockWarning('orderLineEdit', 'orderLineEditQty', 'common.message.over_available_stock');
    stockWarning('transferLineEdit', 'transferLineEditQty', 'common.message.over_available_stock');
    markUnknownRows();
    afterLinesChanged('saveLineEditModal', 'receipts');
    afterLinesChanged('saveOrderLineEditModal', 'orders');
    afterLinesChanged('deleteSelectedReceiptLines', 'receipts');
    afterLinesChanged('deleteSelectedOrderLines', 'orders');
    afterLinesChanged('saveTransferLineEditModal', 'transfers');
    afterLinesChanged('deleteSelectedTransferLines', 'transfers');
    // справочники зависят от поклажедателя — перечитываем их при каждом входе
    PORTAL.onLogin(() => {
      refs.loaded = false;
      Object.keys(stockByWarehouse).forEach(id => delete stockByWarehouse[id]);
      Object.keys(addresses).forEach(id => delete addresses[id]);
      hideDeadExcelButtons();
      loadDirectories().catch(() => {});
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
