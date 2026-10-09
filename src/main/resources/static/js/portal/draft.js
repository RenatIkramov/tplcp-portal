
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
      const e = new Error(PORTAL.текстОшибки(resp.status, data));
      e.status = resp.status;
      throw e;
    }
    return data;
  }

  /* ---------------- справочники для выпадающих списков ---------------- */

  const refs = {loaded:false, warehouses:[], suppliers:[], consignees:[], skuByCode:{}};
  const me = {canEditTransport:false, loaded:false};

  async function загрузитьСправочники(){
    if (refs.loaded) return refs;
    const [w, s, c, sku, кто] = await Promise.all([
      api('GET','/api/warehouses'), api('GET','/api/suppliers'),
      api('GET','/api/consignees'), api('GET','/api/sku'), api('GET','/api/me')
    ]);
    me.canEditTransport = кто.canEditTransport === true;
    me.roleCode = кто.roleCode;
    me.loaded = true;
    refs.warehouses = w.map(x => ({value:x.warehouseId, text:x.name}));
    refs.suppliers  = s.map(x => ({value:x.supplierId,  text:x.name}));
    refs.consignees = c.map(x => ({value:x.consigneeId, text:x.name}));

    // Окно ввода строки ищет товар в массиве products — наполняем его
    // настоящей номенклатурой, чтобы поиск работал по данным базы.
    refs.skuByCode = {};
    const список = sku.map(x => {
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
    refs.skuList = список;
    наполнитьТовары();
    refs.loaded = true;
    return refs;
  }

  /**
   * Массив products демо переназначается при входе и смене поклажедателя,
   * поэтому наполняем его не один раз, а перед каждым открытием окна строки:
   * поиск товара в окне работает именно по нему.
   */
  function наполнитьТовары(){
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
  const kvFieldОригинал = window.kvField;
  window.kvField = function(label, value, opts){
    opts = opts || {};
    const options = opts.options || [];
    const сложные = options.length && typeof options[0] === 'object';
    if (opts.type !== 'select' || !сложные){
      return kvFieldОригинал(label, value, opts);
    }
    const esc = s => String(s === null || s === undefined ? '' : s).replace(/"/g,'&quot;');
    const idAttr = opts.id ? 'id="' + opts.id + '"' : '';
    const выбран = String(value === null || value === undefined ? '' : value);
    const placeholder = options.some(o => String(o.value) === выбран)
        ? '' : '<option value="" selected>' + T('sku.text.not_selected') + '</option>';
    const html = options.map(o =>
        '<option value="' + esc(o.value) + '"' +
        (String(o.value) === выбран ? ' selected' : '') + '>' + esc(o.text) + '</option>').join('');
    const label2 = (typeof localizeColumnLabel === 'function') ? localizeColumnLabel(label) : label;
    return '<div class="kv"><label>' + label2 + '</label>'
         + '<select class="kv-input kv-input-editable" ' + idAttr + '>'
         + placeholder + html + '</select></div>';
  };

  /* ---------------- поля шапки: что куда сохраняется ---------------- */

  // поле карточки → колонка базы
  const ПОЛЯ = {
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

  function собратьШапку(type){
    const данные = {};
    Object.entries(ПОЛЯ[type]).forEach(([id, column]) => {
      const el = document.getElementById(id);
      if (!el) return;
      const v = (el.value || '').trim();
      данные[column] = v === '' || v === '—' ? null : v;
    });
    return данные;
  }

  /** Состав документа в том виде, в каком его ждёт API. */
  function собратьСостав(type){
    const состав = type === 'receipts' ? currentReceiptComposition : currentOrderComposition;
    return (состав || []).map(l => {
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
  function вДокумент(type, card){
    const h = card.header;
    const BADGE = {draft:'draft', awaiting:'warn', in_progress:'warn', received:'ok', discrepancy:'err',
                   sent_to_wms:'warn', send_failed:'err',
                   reserved:'warn', picking:'warn', shipped:'ok', delayed:'err'};
    const общее = {
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
      Object.assign(общее, {
        supplier: h.supplierId, supplierName: dash(h.supplierName),
        docType: h.docTypeId, docTypeName: dash(h.docTypeName),
        eta: ruDate(h.etaDate), etaIso: h.etaDate,
        accompanyingDocNumber: h.accompanyingDocNumber || '', invoiceNumber: h.invoiceNumber || '',
        gtd: h.gtd || ''
      });
    } else {
      Object.assign(общее, {
        consignee: h.consigneeId, consigneeName: dash(h.consigneeName),
        orderType: h.orderTypeId, orderTypeName: dash(h.orderTypeName),
        plannedShip: ruDate(h.plannedShipDate), ship: ruDate(h.shipDate),
        deliveryDate: ruDate(h.deliveryDate), deliveryAddress: dash(h.deliveryAddress)
      });
    }
    общее.lines = (card.lines || []).map(l => ({
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
    общее.items = общее.lines.length;
    return общее;
  }

  async function открытьИзApi(type, docId){
    await загрузитьСправочники();
    const card = await api('GET', '/api/' + type + '/' + docId);
    const doc = вДокумент(type, card);
    if (type === 'receipts'){
      receipts.length = 0; receipts.push(doc);
      openReceipt(doc.no);
    } else {
      orders.length = 0; orders.push(doc);
      openOrder(doc.no);
    }
    показатьПричины(type, doc);
    return doc;
  }

  /** Причины, по которым документ нельзя отправить — в штатный баннер карточки. */
  function показатьПричины(type, doc){
    const banner = document.getElementById(type === 'receipts' ? 'receiptDetailBanner' : 'orderDetailBanner');
    if (!banner) return;
    const problems = (doc && doc.problems) || [];
    if (doc && doc.statusCode === 'draft' && problems.length){
      banner.innerHTML = '<div class="hint" style="background:#fdf2f2; border-left:3px solid #c0392b;'
        + ' padding:10px 14px; border-radius:6px; color:#8a2b2b;">'
        + 'Документ пока нельзя отправить в WMS: ' + problems.join('; ') + '</div>';
    } else {
      banner.innerHTML = '';
    }
    // кнопка «Подтвердить» неактивна, пока есть причины
    const кнопка = document.querySelector('#' + (type === 'receipts' ? 'receiptDraftActions' : 'orderDraftActions')
                                          + ' .btn.primary');
    if (кнопка){
      const блок = problems.length > 0;
      кнопка.disabled = блок;
      кнопка.style.opacity = блок ? '.5' : '';
      кнопка.style.cursor = блок ? 'not-allowed' : '';
      кнопка.title = блок ? 'Сначала устраните: ' + problems.join('; ') : T('sku.text.send_document_to_wms');
    }
  }

  /* ---------------- поля шапки: выпадающие списки из API ---------------- */

  function обернутьПоляШапки(){
    const оберни = (имяФункции, карта) => {
      const оригинал = window[имяФункции];
      if (typeof оригинал !== 'function') return;
      window[имяФункции] = function(dbField, doc, editable){
        const правило = карта[dbField];
        if (правило && editable){
          return kvField(правило.label, doc[правило.value],
              {id: правило.id, editable: true, type: 'select', options: правило.options()});
        }
        if (правило && !editable){
          return kvField(правило.label, dash(doc[правило.name]), {id: правило.id});
        }
        return оригинал(dbField, doc, editable);
      };
    };

    оберни('fcReceiptSpecialFieldHtml', {
      warehouse: {id:'rf_warehouse', label:T('sku.label.warehouse'), value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      supplier:  {id:'rf_supplier', label:T('server.message.supplier'), value:'supplier', name:'supplierName',
                  options:() => refs.suppliers}
    });
    оберни('fcOrderSpecialFieldHtml', {
      warehouse: {id:'of_warehouse', label:T('sku.label.warehouse'), value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      consignee: {id:'of_consignee', label:T('partners.title.consignee'), value:'consignee', name:'consigneeName',
                  options:() => refs.consignees}
    });
  }

  /* ---------------- действия с черновиком ---------------- */

  function сообщить(e){
    if (typeof notify === 'function') notify(e.message || String(e));
  }

  async function сохранить(type, тихо){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref || ref.statusCode !== 'draft') return null;
    await api('PUT', '/api/' + type + '/' + ref.docId, собратьШапку(type));
    await api('PUT', '/api/' + type + '/' + ref.docId + '/lines', собратьСостав(type));
    const doc = await открытьИзApi(type, ref.docId);
    if (!тихо && typeof notify === 'function'){
      notify(type === 'receipts' ? T('sku.text.receipt_changes_saved') : T('sku.text.order_changes_saved'));
    }
    return doc;
  }

  window.createNewReceiptDraft = async function(){
    try {
      await загрузитьСправочники();
      const card = await api('POST', '/api/receipts', {});
      await открытьИзApi('receipts', card.header.docId);
      if (typeof notify === 'function') notify('Создан черновик поступления ' + card.header.documentNo);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    } catch (e){ сообщить(e); }
  };

  window.createNewOrderDraft = async function(){
    try {
      await загрузитьСправочники();
      const card = await api('POST', '/api/orders', {});
      await открытьИзApi('orders', card.header.docId);
      if (typeof notify === 'function') notify('Создан черновик заказа ' + card.header.documentNo);
      if (typeof portalReloadDocuments === 'function') portalReloadDocuments();
    } catch (e){ сообщить(e); }
  };

  window.saveReceiptDraft = () => сохранить('receipts').catch(сообщить);
  window.saveOrderDraft = () => сохранить('orders').catch(сообщить);

  async function подтвердить(type){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref) return;
    try {
      await сохранить(type, true);
      const card = await api('POST', '/api/' + type + '/' + ref.docId + '/confirm');
      const doc = вДокумент(type, card);
      if (type === 'receipts'){ receipts.length = 0; receipts.push(doc); openReceipt(doc.no); }
      else { orders.length = 0; orders.push(doc); openOrder(doc.no); }
      показатьПричины(type, doc);
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
      сообщить(e);
    }
  }

  window.confirmReceiptDraft = () => подтвердить('receipts');
  window.confirmOrderDraft = () => подтвердить('orders');

  async function удалить(type){
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
    } catch (e){ сообщить(e); }
  }

  window.deleteReceiptDraft = () => удалить('receipts');
  window.deleteOrderDraft = () => удалить('orders');

  /* ---------------- строки состава ---------------- */

  // Окно ввода строки остаётся демонстрационным: поиск товара, упаковка,
  // пересчёт, цена, объединение дублей. Мы подключаемся после сохранения
  // и отправляем получившийся состав целиком.
  function послеИзмененияСостава(имяФункции, type){
    const оригинал = window[имяФункции];
    if (typeof оригинал !== 'function') return;
    window[имяФункции] = function(){
      const результат = оригинал.apply(this, arguments);
      const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
      if (ref && ref.statusCode === 'draft'){
        сохранить(type, true).catch(сообщить);
      }
      return результат;
    };
  }

  /* ---------------- состав: позиции, неизвестные WMS ---------------- */

  function пометитьНеизвестныеПозиции(){
    ['receipt','order'].forEach(prefix => {
      const оригинал = window['render' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Composition'];
      if (typeof оригинал !== 'function') return;
      window['render' + prefix.charAt(0).toUpperCase() + prefix.slice(1) + 'Composition'] = function(){
        const результат = оригинал.apply(this, arguments);
        const состав = prefix === 'receipt' ? currentReceiptComposition : currentOrderComposition;
        const rows = document.querySelectorAll('#' + prefix + 'DetailRows tr');
        (состав || []).forEach((l, i) => {
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
        return результат;
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
  async function сохранитьТранспорт(type){
    const ref = type === 'receipts' ? currentReceiptRef : currentOrderRef;
    if (!ref) return;
    const поля = ['trip','carrier','transportType','transportBrand','carNumber','trailerNumber',
                  'techPassportNumber','containerNumber','sealNumber',
                  'driverName','driverPhone','driverIdNumber','driverInn'];
    const prefix = type === 'receipts' ? 'rf_' : 'of_';
    const данные = {};
    поля.forEach(f => {
      const el = document.getElementById(prefix + f);
      if (!el) return;
      const колонка = f.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
      данные[колонка] = (el.value || '').trim() === '' || el.value === '—' ? null : el.value.trim();
    });
    try {
      await api('PUT', '/api/' + type + '/' + ref.docId + '/transport', данные);
      await открытьИзApi(type, ref.docId);
      if (typeof notify === 'function') notify(T('sku.text.transport_details_sent_to_wms'));
    } catch (e){ сообщить(e); }
  }
  window.portalSaveTransport = сохранитьТранспорт;
  /** Открыть документ по идентификатору — используется проверками и ссылками. */
  window.portalOpenDocumentById = (type, docId) => открытьИзApi(type, docId).catch(сообщить);

  /**
   * Кнопка «Сохранить транспортную информацию» в прототипе уже есть —
   * подменяем только то, что происходит по нажатию.
   */
  window.fcSaveTransportSection = function(docType){
    if (docType === 'receipts' || docType === 'orders'){
      сохранитьТранспорт(docType);
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
  function предупреждениеПоОстаткам(){
    const оригинал = window.saveOrderLineEditModal;
    if (typeof оригинал !== 'function') return;
    window.saveOrderLineEditModal = function(){
      const товар = window.orderLineEditSelectedProduct;
      const поле = document.getElementById('orderLineEditQty');
      const кол = поле ? Number(поле.value) : null;
      const результат = оригинал.apply(this, arguments);
      if (товар && кол){
        const остаток = (PORTAL.rows || []).find(r => r.code === товар.code);
        if (остаток && кол > остаток.available && typeof notify === 'function'){
          notify('Внимание: заказано ' + кол + ' ' + (товар.unit || '')
               + ', доступно по данным портала ' + остаток.available
               + '. Фактическую доступность подтвердит WMS.');
        }
      }
      return результат;
    };
  }

  /* ---------------- открытие документа из списка ---------------- */

  const просмотрДемо = window.portalOpenDocument;
  window.portalOpenDocument = function(no){
    const type = (PORTAL.docs.receipts || []).some(r => r.no === no) ? 'receipts'
               : ((PORTAL.docs.orders || []).some(r => r.no === no) ? 'orders' : 'transfers');
    const row = (PORTAL.docs[type] || []).find(r => r.no === no);
    if (row && (type === 'receipts' || type === 'orders')){
      открытьИзApi(type, row.docId).catch(сообщить);
      return;
    }
    if (typeof просмотрДемо === 'function') просмотрДемо(no);
  };

  /** Перед открытием окна ввода строки подкладываем настоящую номенклатуру. */
  function обернутьОкноСтроки(){
    ['openLineAddModal','openLineEditModal','openOrderLineAddModal','openOrderLineEditModal']
      .forEach(имя => {
        const оригинал = window[имя];
        if (typeof оригинал !== 'function') return;
        window[имя] = function(){
          наполнитьТовары();
          return оригинал.apply(this, arguments);
        };
      });
  }

  function install(){
    обернутьПоляШапки();
    обернутьОкноСтроки();
    предупреждениеПоОстаткам();
    пометитьНеизвестныеПозиции();
    послеИзмененияСостава('saveLineEditModal', 'receipts');
    послеИзмененияСостава('saveOrderLineEditModal', 'orders');
    послеИзмененияСостава('deleteSelectedReceiptLines', 'receipts');
    послеИзмененияСостава('deleteSelectedOrderLines', 'orders');
    // справочники зависят от поклажедателя — перечитываем их при каждом входе
    PORTAL.onLogin(() => { refs.loaded = false; загрузитьСправочники().catch(() => {}); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
