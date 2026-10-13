
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

  const refs = {loaded:false, warehouses:[], suppliers:[], consignees:[], skuByCode:{}};
  const me = {canEditTransport:false, loaded:false};

  async function loadDirectories(){
    if (refs.loaded) return refs;
    const [w, s, c, sku, meResponse] = await Promise.all([
      api('GET','/api/warehouses'), api('GET','/api/suppliers'),
      api('GET','/api/consignees'), api('GET','/api/sku'), api('GET','/api/me')
    ]);
    me.canEditTransport = meResponse.canEditTransport === true;
    me.roleCode = meResponse.roleCode;
    me.loaded = true;
    refs.warehouses = w.map(x => ({value:x.warehouseId, text:x.name}));
    refs.suppliers  = s.map(x => ({value:x.supplierId,  text:x.name}));
    refs.consignees = c.map(x => ({value:x.consigneeId, text:x.name}));

    // Окно ввода строки ищет товар в массиве products — наполняем его
    // настоящей номенклатурой, чтобы поиск работал по данным базы.
    refs.skuByCode = {};
    refs.skuList = sku.map(x => {
      refs.skuByCode[x.code] = x;
      return {
        skuId: x.skuId, code: x.code, name: x.name, unit: x.uomName || '',
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
         + '<select class="kv-input kv-input-editable" ' + idAttr + '>'
         + placeholder + html + '</select></div>';
  };

  /* ---------------- поля шапки: что куда сохраняется ---------------- */

  // поле карточки → колонка базы
  const FIELDS = {
    receipts: {
      rf_erpNo:'erp_number', rf_warehouse:'warehouse_id', rf_supplier:'supplier_id',
      rf_docType:'doc_type_id', rf_eta:'eta_date', rf_date:'doc_date',
      rf_accompanyingDocNumber:'accompanying_doc_number', rf_invoiceNumber:'invoice_number',
      rf_gtd:'gtd', rf_comment:'comment', rf_trip:'trip',
      rf_palletCount:'pallet_count', rf_cargoPlaces:'cargo_places'
    },
    orders: {
      of_erpNo:'erp_number', of_warehouse:'warehouse_id', of_consignee:'consignee_id',
      of_orderType:'order_type_id', of_plannedShip:'planned_ship_date', of_date:'doc_date',
      of_deliveryDate:'delivery_date', of_comment:'comment', of_trip:'trip',
      of_palletCount:'pallet_count', of_cargoPlaces:'cargo_places'
    }
  };

  function collectHeader(type){
    const store = {};
    Object.entries(FIELDS[type]).forEach(([id, column]) => {
      const el = document.getElementById(id);
      if (!el) return;
      const v = (el.value || '').trim();
      store[column] = v === '' || v === '—' ? null : v;
    });
    return store;
  }

  /** Состав документа в том виде, в каком его ждёт API. */
  function collectLines(type){
    const composition = type === 'receipts' ? currentReceiptComposition : currentOrderComposition;
    return (composition || []).map(l => {
      // строки, созданные окном демо, несут код товара, а не идентификатор
      const skuId = l.skuId || (refs.skuByCode[l.code] && refs.skuByCode[l.code].skuId);
      const line = {sku_id: skuId, line_comment: l.lineComment || null};
      if (type === 'receipts') line.expected_qty = Number(l.expected);
      else { line.ordered_qty = Number(l.ordered); if (l.weight) line.weight_kg = Number(l.weight); }
      if (l.price !== null && l.price !== undefined && l.price !== '') {
        line.price = Number(l.price);
        line.currency_id = 1;
      }
      return line;
    });
  }

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
      date: ruDate(h.docDate), author: dash(h.authorName), comment: h.comment || '',
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
    if (type === 'receipts'){
      Object.assign(totalCount, {
        supplier: h.supplierId, supplierName: dash(h.supplierName),
        docType: h.docTypeId, docTypeName: dash(h.docTypeName),
        eta: ruDate(h.etaDate), etaIso: h.etaDate,
        accompanyingDocNumber: h.accompanyingDocNumber || '', invoiceNumber: h.invoiceNumber || '',
        gtd: h.gtd || ''
      });
    } else {
      Object.assign(totalCount, {
        consignee: h.consigneeId, consigneeName: dash(h.consigneeName),
        orderType: h.orderTypeId, orderTypeName: dash(h.orderTypeName),
        plannedShip: ruDate(h.plannedShipDate), ship: ruDate(h.shipDate),
        deliveryDate: ruDate(h.deliveryDate), deliveryAddress: dash(h.deliveryAddress)
      });
    }
    totalCount.lines = (card.lines || []).map(l => ({
      skuId: l.skuId, code: l.skuCode, name: l.skuName, unit: l.unitName || '',
      skuInWms: l.skuInWms !== false,
      expected: l.expectedQty, expectedWms: l.expectedQtyWms ?? l.expectedQty,
      // у черновика фактических количеств ещё нет — показываем прочерк, а не пустоту
      accepted: l.acceptedQty ?? '—', defect: l.defectQty ?? '—',
      ordered: l.orderedQty, orderedWms: l.orderedQtyWms ?? l.orderedQty,
      picked: l.pickedQty ?? '—', weight: l.weightKg ?? '—',
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
    if (type === 'receipts'){
      receipts.length = 0; receipts.push(doc);
      openReceipt(doc.no);
    } else {
      orders.length = 0; orders.push(doc);
      openOrder(doc.no);
    }
    showReasons(type, doc);
    return doc;
  }

  /** Причины, по которым документ нельзя отправить — в штатный баннер карточки. */
  function showReasons(type, doc){
    const banner = document.getElementById(type === 'receipts' ? 'receiptDetailBanner' : 'orderDetailBanner');
    if (!banner) return;
    const problems = (doc && doc.problems) || [];
    if (doc && doc.statusCode === 'draft' && problems.length){
      banner.innerHTML = '<div class="hint" style="background:#fdf2f2; border-left:3px solid #c0392b;'
        + ' padding:10px 14px; border-radius:6px; color:#8a2b2b;">'
        + T('sku.text.document_not_ready_yet') + ' ' + problems.join('; ') + '</div>';
    } else {
      banner.innerHTML = '';
    }
    // кнопка «Подтвердить» неактивна, пока есть причины
    const button = document.querySelector('#' + (type === 'receipts' ? 'receiptDraftActions' : 'orderDraftActions')
                                          + ' .btn.primary');
    if (button){
      const block = problems.length > 0;
      button.disabled = block;
      button.style.opacity = block ? '.5' : '';
      button.style.cursor = block ? 'not-allowed' : '';
      button.title = block
        ? T('sku.text.fix_problems_first') + ' ' + problems.join('; ')
        : T('sku.text.send_document_to_wms');
    }
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
        const rule = rules[dbField];
        // Подпись поля — общим правилом портала: имя клиента, если он
        // поле переименовал, иначе перевод. Эти же поля в режиме
        // просмотра рисует прототип, и зваться они должны одинаково.
        const label = rule ? PORTAL.fieldLabel(tableKey, dbField, rule.key) : null;
        if (rule && editable){
          return kvField(label, doc[rule.value],
              {id: rule.id, editable: true, type: 'select', options: rule.options()});
        }
        if (rule && !editable){
          return kvField(label, dash(doc[rule.name]), {id: rule.id});
        }
        return original(dbField, doc, editable);
      };
    };

    wrap('fcReceiptSpecialFieldHtml', 'receipts', {
      warehouse: {id:'rf_warehouse', key:'sku.label.warehouse', value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      supplier:  {id:'rf_supplier', key:'col.receipts.supplier', value:'supplier', name:'supplierName',
                  options:() => refs.suppliers}
    });
    wrap('fcOrderSpecialFieldHtml', 'orders', {
      warehouse: {id:'of_warehouse', key:'sku.label.warehouse', value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      consignee: {id:'of_consignee', key:'partners.title.consignee', value:'consignee', name:'consigneeName',
                  options:() => refs.consignees}
    });
  }

  /* ---------------- действия с черновиком ---------------- */

  function tell(e){
    if (typeof notify === 'function') notify(e.message || String(e));
  }

  async function save(type, quiet){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref || ref.statusCode !== 'draft') return null;
    await api('PUT', '/api/' + type + '/' + ref.docId, collectHeader(type));
    await api('PUT', '/api/' + type + '/' + ref.docId + '/lines', collectLines(type));
    const doc = await openFromApi(type, ref.docId);
    if (!quiet && typeof notify === 'function'){
      notify(type === 'receipts' ? T('sku.text.receipt_changes_saved') : T('sku.text.order_changes_saved'));
    }
    return doc;
  }

  window.createNewReceiptDraft = async function(){
    try {
      await loadDirectories();
      const card = await api('POST', '/api/receipts', {});
      await openFromApi('receipts', card.header.docId);
      if (typeof notify === 'function') notify('Создан черновик поступления ' + card.header.documentNo);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    } catch (e){ tell(e); }
  };

  window.createNewOrderDraft = async function(){
    try {
      await loadDirectories();
      const card = await api('POST', '/api/orders', {});
      await openFromApi('orders', card.header.docId);
      if (typeof notify === 'function') notify('Создан черновик заказа ' + card.header.documentNo);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    } catch (e){ tell(e); }
  };

  window.saveReceiptDraft = () => save('receipts').catch(tell);
  window.saveOrderDraft = () => save('orders').catch(tell);

  async function confirm(type){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref) return;
    try {
      await save(type, true);
      const card = await api('POST', '/api/' + type + '/' + ref.docId + '/confirm');
      const doc = toDocument(type, card);
      if (type === 'receipts'){ receipts.length = 0; receipts.push(doc); openReceipt(doc.no); }
      else { orders.length = 0; orders.push(doc); openOrder(doc.no); }
      showReasons(type, doc);
      if (typeof notify === 'function'){
        notify(doc.statusCode === 'send_failed'
            ? T('sku.text.sending_to_wms_failed_the_document')
            : 'Документ ' + doc.no + ' отправлен в WMS');
      }
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    } catch (e){
      // Причины отказа показываем там же, где пользователь их ожидает — в баннере карточки
      const banner = document.getElementById(type === 'receipts' ? 'receiptDetailBanner' : 'orderDetailBanner');
      if (banner){
        banner.innerHTML = '<div class="hint" style="background:#fdf2f2; border-left:3px solid #c0392b;'
          + ' padding:10px 14px; border-radius:6px; color:#8a2b2b;">' + (e.message || e) + '</div>';
      }
      tell(e);
    }
  }

  window.confirmReceiptDraft = () => confirm('receipts');
  window.confirmOrderDraft = () => confirm('orders');

  async function remove(type){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref) return;
    try {
      await api('DELETE', '/api/' + type + '/' + ref.docId);
      if (typeof closeDeleteReceiptDraftConfirm === 'function' && type === 'receipts') closeDeleteReceiptDraftConfirm();
      if (typeof closeDeleteOrderDraftConfirm === 'function' && type === 'orders') closeDeleteOrderDraftConfirm();
      if (type === 'receipts') currentReceiptRef = null; else currentOrderRef = null;
      showTab(type);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
      if (typeof notify === 'function') notify('Черновик ' + ref.no + ' удалён');
    } catch (e){ tell(e); }
  }

  window.deleteReceiptDraft = () => remove('receipts');
  window.deleteOrderDraft = () => remove('orders');

  /* ---------------- строки состава ---------------- */

  // Окно ввода строки остаётся демонстрационным: поиск товара, упаковка,
  // пересчёт, цена, объединение дублей. Мы подключаемся после сохранения
  // и отправляем получившийся состав целиком.
  function afterLinesChanged(functionName, type){
    const original = window[functionName];
    if (typeof original !== 'function') return;
    window[functionName] = function(){
      const result = original.apply(this, arguments);
      const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
      if (ref && ref.statusCode === 'draft'){
        save(type, true).catch(tell);
      }
      return result;
    };
  }

  /* ---------------- состав: позиции, неизвестные WMS ---------------- */

  function markUnknownRows(){
    ['receipt','order'].forEach(prefix => {
      const original = window['render' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Composition'];
      if (typeof original !== 'function') return;
      window['render' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Composition'] = function(){
        const result = original.apply(this, arguments);
        const composition = prefix === 'receipt' ? currentReceiptComposition : currentOrderComposition;
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
    if (docType === 'receipts' || docType === 'orders'){
      saveTransport(docType);
      return;
    }
    if (typeof notify === 'function') notify(T('sku.message.transport_details_are_not_availabl'));
  };

  /* ---------------- остатки при вводе строки заказа ---------------- */

  /**
   * §8.3.2 и решение Р4: портал владеет копией остатков, а не оригиналом,
   * поэтому превышение доступного количества — предупреждение, а не запрет.
   * Последнее слово остаётся за WMS.
   */
  function stockWarning(){
    const original = window.saveOrderLineEditModal;
    if (typeof original !== 'function') return;
    window.saveOrderLineEditModal = function(){
      const product = window.orderLineEditSelectedProduct;
      const field = document.getElementById('orderLineEditQty');
      const count = field ? Number(field.value) : null;
      const result = original.apply(this, arguments);
      if (product && count){
        const stockRow = (PORTAL.rows || []).find(r => r.code === product.code);
        if (stockRow && count > stockRow.available && typeof notify === 'function'){
          notify('Внимание: заказано ' + count + ' ' + (product.unit || '')
               + ', доступно по данным портала ' + stockRow.available
               + '. Фактическую доступность подтвердит WMS.');
        }
      }
      return result;
    };
  }

  /* ---------------- открытие документа из списка ---------------- */

  const demoView = window.portalOpenDocument;
  window.portalOpenDocument = function(no){
    const type = (PORTAL.docs.receipts || []).some(r => r.no === no) ? 'receipts'
               : ((PORTAL.docs.orders || []).some(r => r.no === no) ? 'orders' : 'transfers');
    const row = (PORTAL.docs[type] || []).find(r => r.no === no);
    if (row && (type === 'receipts' || type === 'orders')){
      openFromApi(type, row.docId).catch(tell);
      return;
    }
    if (typeof demoView === 'function') demoView(no);
  };

  /** Перед открытием окна ввода строки подкладываем настоящую номенклатуру. */
  function wrapLineModal(){
    ['openLineAddModal','openLineEditModal','openOrderLineAddModal','openOrderLineEditModal']
      .forEach(fieldName => {
        const original = window[fieldName];
        if (typeof original !== 'function') return;
        window[fieldName] = function(){
          fillProducts();
          return original.apply(this, arguments);
        };
      });
  }

  function install(){
    wrapHeaderFields();
    wrapLineModal();
    stockWarning();
    markUnknownRows();
    afterLinesChanged('saveLineEditModal', 'receipts');
    afterLinesChanged('saveOrderLineEditModal', 'orders');
    afterLinesChanged('deleteSelectedReceiptLines', 'receipts');
    afterLinesChanged('deleteSelectedOrderLines', 'orders');
    // справочники зависят от поклажедателя — перечитываем их при каждом входе
    PORTAL.onLogin(() => { refs.loaded = false; loadDirectories().catch(() => {}); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
