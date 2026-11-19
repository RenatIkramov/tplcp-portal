
/*
  Карточка документа — поступления, заказа и перемещения (10.11.2026).

  Одна реализация на три вида документа. Прежде карточка жила
  в прототипе тремя копиями (openReceipt, openOrder, openTransfer и всё,
  что вокруг них), а draft.js подменял около двадцати её функций
  обёртками. Теперь различия видов собраны в таблицы TYPES и FIELDS,
  а всё остальное одно.

  Что здесь: шапка и вкладки (общая информация, состав, транспорт,
  доп. информация, фото, услуги), состав с галочками и итогами, экран
  «Детализация» строки (партии), вопрос об удалении черновика.
  Окно ввода строки — line-modal.js, сохранение и отправка — draft.js.

  Разметка карточки в index.html пока тремя блоками; нужный выбирается
  по виду документа (TYPES[type].view): view-receipt-detail,
  receiptDetailRows и т. д.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /**
   * Чем виды документов отличаются в карточке.
   * view — начало идентификаторов разметки, field — начало
   * идентификаторов полей шапки (rf_erpNo), qty — свойство количества
   * строки.
   */
  const TYPES = {
    receipts: {view: 'receipt', field: 'rf', title: 'common.message.receipt',
               contents: 'receipts.title.receipt_contents', qty: 'expected',
               confirmDelete: 'deleteReceiptConfirmOverlay'},
    orders: {view: 'order', field: 'of', title: 'orders.title.shipment_order',
             contents: 'orders.title.order_contents', qty: 'ordered',
             confirmDelete: 'deleteOrderConfirmOverlay'},
    transfers: {view: 'transfer', field: 'tf', title: 'common.message.transfer',
                contents: 'transfers.title.transfer_contents', qty: 'qty',
                confirmDelete: 'deleteTransferConfirmOverlay'}
  };

  /** Код качества «Годный»: пустое качество строки значит его. */
  const GOOD = '01';

  /**
   * Открытый документ каждого вида: сам документ, его строки (с галочкой
   * выбора), открытая вкладка и строка на экране «Детализация».
   * @type {Record<string, {doc: any, lines: any[], tab: string, detail: number | null}>}
   */
  const state = {
    receipts: {doc: null, lines: [], tab: 'general', detail: null},
    orders: {doc: null, lines: [], tab: 'general', detail: null},
    transfers: {doc: null, lines: [], tab: 'general', detail: null}
  };

  const isDraft = doc => !!doc && doc.statusCode === PORTAL.STATUS.DRAFT;
  const el = id => document.getElementById(id);

  // общие помощники — core.js (аудит A4)
  const {dash, esc: attr, esc: text} = window.PORTAL;
  const draftApi = () => PORTAL.draft || null;

  /* ===================== поля шапки ===================== */

  /**
   * Поле шапки: ключ подписи и вид.
   *  text   — строка, правится в черновике;
   *  date   — дата, правится в черновике (в документе лежит по-русски);
   *  select — выбор из справочника: в документе ключ (value), на экране
   *           имя (name), список — options(doc);
   *  fixed  — только показывается: его задаёт база или WMS, сервер
   *           такое поле не примет.
   * В ключе подписи {t} — вид документа: col.{t}.carrier → col.orders.carrier.
   * @typedef {{key: string, kind: 'text' | 'date' | 'select' | 'fixed',
   *            value?: string, name?: string, options?: (doc: any) => SelectOption[],
   *            onchange?: string, show?: (doc: any) => unknown}} HeaderField
   */

  /** @type {(name: string) => SelectOption[]} */
  const refOf = name => {
    const d = draftApi();
    return (d && /** @type {Record<string, any>} */ (d.refs)[name]) || [];
  };
  const warehouses = () => refOf('warehouses');
  const refList = name => () => refOf(name);

  /**
   * Склады перемещения: получателем не предлагаем выбранный отправителем
   * (и наоборот). Выбранное в САМОМ поле значение остаётся в списке
   * всегда: список, из которого пропало сохранённое значение, молча
   * подменил бы его первым складом.
   */
  const otherWarehouses = (otherId, keep) => {
    const chosen = (el(otherId) || {}).value || '';
    return warehouses().filter(w => String(w.value) === String(keep || '')
        || String(w.value) !== String(chosen));
  };

  /** @type {Record<string, HeaderField>} */
  const COMMON_FIELDS = {
    erpNo: {key: 'common.label.document_number_client_erp', kind: 'text'},
    wmsNumber: {key: 'common.label.wms_document_number', kind: 'fixed'},
    date: {key: 'common.label.created_at', kind: 'fixed'},
    comment: {key: 'sku.label.comment', kind: 'text'},
    weight: {key: 'col.orderComposition.weight', kind: 'fixed'},
    volume: {key: 'sku.label.volume_m', kind: 'fixed',
             show: d => typeof d.volume === 'number' ? d.volume.toFixed(1) : d.volume},
    lineCount: {key: 'common.label.lines', kind: 'fixed'},
    palletCount: {key: 'col.{t}.palletCount', kind: 'fixed'},
    cargoPlaces: {key: 'col.{t}.cargoPlaces', kind: 'fixed'},
    totalPieces: {key: 'col.{t}.totalPieces', kind: 'fixed'},
    // рейс и дата доставки — WMS (решение от 02.11.2026, V97)
    trip: {key: 'receipts.label.trip', kind: 'fixed'},
    carrier: {key: 'col.{t}.carrier', kind: 'text'},
    // тип транспорта — перечисление (V97): выбирается списком, хранится код
    transportType: {key: 'col.{t}.transportType', kind: 'select', value: 'transportType',
                    name: 'transportTypeName', options: refList('transportTypes')},
    transportBrand: {key: 'col.{t}.transportBrand', kind: 'text'},
    carNumber: {key: 'col.{t}.carNumber', kind: 'text'},
    trailerNumber: {key: 'col.{t}.trailerNumber', kind: 'text'},
    techPassportNumber: {key: 'col.{t}.techPassportNumber', kind: 'text'},
    containerNumber: {key: 'col.{t}.containerNumber', kind: 'text'},
    sealNumber: {key: 'col.{t}.sealNumber', kind: 'text'},
    driverName: {key: 'common.label.driver_full_name', kind: 'text'},
    driverPhone: {key: 'col.{t}.driverPhone', kind: 'text'},
    driverIdNumber: {key: 'col.{t}.driverIdNumber', kind: 'text'},
    driverInn: {key: 'col.{t}.driverInn', kind: 'text'}
  };

  /** @type {Record<string, Record<string, HeaderField>>} */
  const FIELDS = {
    receipts: Object.assign({}, COMMON_FIELDS, {
      warehouse: {key: 'sku.label.warehouse', kind: 'select', value: 'warehouse',
                  name: 'warehouseName', options: warehouses},
      supplier: {key: 'col.receipts.supplier', kind: 'select', value: 'supplier',
                 name: 'supplierName', options: refList('suppliers')},
      docType: {key: 'col.receipts.docType', kind: 'select', value: 'docType',
                name: 'docTypeName', options: refList('docTypes')},
      accompanyingDocNumber: {key: 'col.receipts.accompanyingDocNumber', kind: 'text'},
      invoiceNumber: {key: 'receipts.label.invoice_number', kind: 'text'},
      gtd: {key: 'col.receipts.gtd', kind: 'text'},
      eta: {key: 'receipts.label.expected_delivery_date', kind: 'date'},
      // Даты прибытия и окончания приёмки пришлёт WMS. Прототип рисовал
      // в них ожидаемую дату — выдуманное значение (убрано 10.11.2026).
      arrivalDate: {key: 'col.receipts.arrivalDate', kind: 'fixed'},
      receivingEndDate: {key: 'receipts.label.receiving_completed', kind: 'fixed'}
    }),
    orders: Object.assign({}, COMMON_FIELDS, {
      warehouse: {key: 'sku.label.warehouse', kind: 'select', value: 'warehouse',
                  name: 'warehouseName', options: warehouses},
      orderType: {key: 'col.orders.orderType', kind: 'select', value: 'orderType',
                  name: 'orderTypeName', options: refList('orderTypes')},
      consignee: {key: 'partners.title.consignee', kind: 'select', value: 'consignee',
                  name: 'consigneeName', options: refList('consignees'),
                  onchange: 'PORTAL.draft.consigneeChanged'},
      // Место доставки — адреса выбранного грузополучателя; список
      // дочитывает draft.js, когда грузополучатель известен.
      deliveryAddress: {key: 'col.orders.deliveryAddress', kind: 'select', value: 'deliveryAddress',
                        name: 'deliveryAddressName',
                        options: d => { const api = draftApi(); return api ? api.addressOptions(d.consignee) : []; },
                        onchange: 'PORTAL.draft.addressChanged'},
      consigneeContact: {key: 'orders.label.consignee_contact_details', kind: 'text'},
      plannedShip: {key: 'col.orders.plannedShip', kind: 'date'},
      ship: {key: 'col.orders.ship', kind: 'fixed'},
      deliveryDate: {key: 'orders.label.delivery_date', kind: 'fixed'}
    }),
    transfers: Object.assign({}, COMMON_FIELDS, {
      fromWarehouse: {key: 'col.transfers.fromWarehouse', kind: 'select', value: 'fromWarehouse',
                      name: 'fromWarehouseName',
                      options: d => otherWarehouses('tf_toWarehouse', d.fromWarehouse)},
      toWarehouse: {key: 'col.transfers.toWarehouse', kind: 'select', value: 'toWarehouse',
                    name: 'toWarehouseName',
                    options: d => otherWarehouses('tf_fromWarehouse', d.toWarehouse)},
      // дату отгрузки ставит склад, когда действительно отгружает
      shipDate: {key: 'col.transfers.shipDate', kind: 'fixed'},
      receivedDate: {key: 'col.transfers.receivedDate', kind: 'fixed'}
    })
  };

  /** Раскладка вкладок по колонкам; комментарий — под ними во всю ширину. */
  const GENERAL_COLUMNS = {
    receipts: [
      ['erpNo', 'wmsNumber', 'date', 'eta', 'arrivalDate', 'receivingEndDate'],
      ['docType', 'warehouse', 'supplier', 'accompanyingDocNumber', 'invoiceNumber', 'gtd'],
      ['lineCount', 'totalPieces', 'cargoPlaces', 'palletCount', 'weight', 'volume']
    ],
    orders: [
      ['erpNo', 'wmsNumber', 'date', 'plannedShip', 'ship'],
      ['orderType', 'warehouse', 'consignee', 'deliveryAddress', 'consigneeContact'],
      ['lineCount', 'totalPieces', 'cargoPlaces', 'palletCount', 'weight', 'volume']
    ],
    transfers: [
      ['erpNo', 'wmsNumber', 'date', 'shipDate', 'receivedDate'],
      ['fromWarehouse', 'toWarehouse'],
      ['lineCount', 'totalPieces', 'cargoPlaces', 'palletCount', 'weight', 'volume']
    ]
  };
  const TRANSPORT_COLUMNS = [
    ['trip', 'carrier', 'driverName', 'driverInn', 'driverIdNumber', 'driverPhone'],
    ['transportType', 'transportBrand', 'carNumber', 'trailerNumber', 'techPassportNumber'],
    ['containerNumber', 'sealNumber']
  ];
  /** У заказа в транспорте ещё дата доставки. */
  const transportColumns = type => type === 'orders'
      ? [TRANSPORT_COLUMNS[0], TRANSPORT_COLUMNS[1], TRANSPORT_COLUMNS[2].concat('deliveryDate')]
      : TRANSPORT_COLUMNS;

  /** Подпись поля: имя клиента, если он поле переименовал, иначе перевод. */
  function labelOf(type, name, key){
    return PORTAL.fieldLabel(type, name, key.replace('{t}', type));
  }

  /**
   * Разметка поля.
   * @param {string} label готовая разметка подписи
   * @param {any} value
   * @param {{id?: string, editable?: boolean, kind?: string, options?: SelectOption[], onchange?: string}} o
   */
  function fieldHtml(label, value, o){
    const id = o.id ? ' id="' + o.id + '"' : '';
    if (!o.editable){
      return '<div class="kv"><label>' + label + '</label><input class="kv-input"' + id
          + ' type="text" value="' + attr(dash(value)) + '" readonly tabindex="-1"></div>';
    }
    if (o.kind === 'date'){
      return '<div class="kv"><label>' + label + '</label><input class="kv-input kv-input-editable"'
          + id + ' type="date" value="' + attr(toISODate(value)) + '"></div>';
    }
    if (o.kind === 'select'){
      const options = o.options || [];
      const selected = String(value === null || value === undefined ? '' : value);
      const placeholder = options.some(x => String(x.value) === selected)
          ? '' : '<option value="" selected>' + text(T('sku.text.not_selected')) + '</option>';
      const html = options.map(x => '<option value="' + attr(x.value) + '"'
          + (String(x.value) === selected ? ' selected' : '') + '>' + text(x.text) + '</option>').join('');
      // имя функции из описания поля, а не строка скрипта (аудит A3)
      const change = o.onchange ? PORTAL.callOnChange(o.onchange) : '';
      return '<div class="kv"><label>' + label + '</label><select class="kv-input kv-input-editable"'
          + id + change + '>' + placeholder + html + '</select></div>';
    }
    return '<div class="kv"><label>' + label + '</label><input class="kv-input kv-input-editable"'
        + id + ' type="text" value="' + attr(value === null || value === undefined ? '' : value) + '"></div>';
  }

  /** Поле шапки документа; editable — открыта ли вкладка для правки. */
  function headerField(type, name, doc, editable){
    const f = FIELDS[type][name];
    if (!f) return '';
    const id = TYPES[type].field + '_' + name;
    const label = labelOf(type, name, f.key);
    const canEdit = editable && f.kind !== 'fixed';
    if (f.kind === 'select'){
      return canEdit
        ? fieldHtml(label, doc[f.value || name], {id, editable: true, kind: 'select',
                    options: f.options ? f.options(doc) : [], onchange: f.onchange})
        : fieldHtml(label, doc[f.name || name], {id});
    }
    const value = f.show ? f.show(doc) : doc[name];
    return fieldHtml(label, value, {id, editable: canEdit, kind: f.kind});
  }

  /** Доп. поля, открытые поклажедателю (03.11.2026); закрытые не рисуются. */
  function extraFields(type){
    const allowed = PORTAL.fieldAllowed;
    return Array.from({length: 10}, (_, i) => 'customField' + (i + 1))
        .filter(f => !allowed || allowed(f, type));
  }

  function extraField(type, name, doc, editable){
    // С V51 у каждого доп. поля свой ключ (col.orders.customField3)
    // и своё переименование под клиента; общая подпись с номером —
    // на случай, если ключа в каталоге нет: пустая подпись хуже.
    const key = 'col.' + type + '.' + name;
    const label = T(key) !== key
        ? PORTAL.fieldLabel(type, name, key)
        : text(T('common.label.custom_field') + ' ' + name.replace('customField', ''));
    return fieldHtml(label, doc[name], {id: TYPES[type].field + '_' + name, editable});
  }

  /* ===================== вкладки ===================== */

  const TAB_KEYS = {
    general: () => 'common.title.general_information',
    composition: type => TYPES[type].contents,
    transport: () => 'common.title.transport_section',
    extra: () => 'common.title.additional_information',
    photos: () => 'common.title.photos',
    services: () => 'common.title.services',
    history: () => 'audit.tab.history'
  };

  /** Номер договора выбранного поклажедателя — из сеанса (V108). */
  function contractNumber(){
    const s = PORTAL.session;
    const d = s && (s.depositors || []).find(x => x.depositorId === s.depositorId);
    return (d && d.contractNumber) || '—';
  }

  function tabsOf(type){
    return Object.keys(TAB_KEYS).filter(tab =>
        !(tab === 'services' && PORTAL.uiUser && PORTAL.uiUser.hideServicesTab)
        // все доп. поля закрыты — вкладки нет: пустая выглядит как ошибка
        && !(tab === 'extra' && !extraFields(type).length)
        // у несохранённого черновика истории и файлов ещё нет: файл прикладывается к документу
        && !((tab === 'history' || tab === 'photos') && !(state[type].doc && state[type].doc.docId)));
  }

  const columnsHtml = cols => '<div class="kv-columns" style="display:flex; gap:20px 24px; flex-wrap:wrap;">'
      + cols.map(html => '<div class="kv-col" style="flex:1; min-width:220px; display:flex;'
          + ' flex-direction:column; gap:16px;">' + html + '</div>').join('') + '</div>';

  function renderTab(type){
    const t = TYPES[type];
    const s = state[type];
    const doc = s.doc;
    const tabs = tabsOf(type);
    if (tabs.indexOf(s.tab) < 0) s.tab = tabs[0];
    const tab = s.tab;
    el(t.view + 'DetailTabButtons').innerHTML = tabs.map(x =>
        '<button class="admin-subtab-btn' + (x === tab ? ' active' : '') + '" data-tab="' + x + '"'
        + PORTAL.call('PORTAL.card.tab', type, x) + '>'
        + PORTAL.t(TAB_KEYS[x](type)) + '</button>').join('');
    const section = el(t.view + 'CompositionSection');
    if (section) section.style.display = tab === 'composition' ? '' : 'none';
    const content = el(t.view + 'DetailTabContent');
    const draft = isDraft(doc);

    if (tab === 'composition'){
      content.innerHTML = '';
      setTimeout(() => PORTAL.tables.updateScroll(t.view + 'Composition'), 0);
      return;
    }
    if (tab === 'photos'){ if (PORTAL.docFiles) PORTAL.docFiles.show(content, type, doc); return; }
    if (tab === 'services'){ content.innerHTML = servicesHtml(doc); return; }
    if (tab === 'history'){ loadHistory(type, doc, content); return; }
    if (tab === 'extra'){
      content.innerHTML = '<div class="kv-columns" style="display:flex; gap:20px 24px; flex-wrap:wrap;">'
          + extraFields(type).map(f => extraField(type, f, doc, draft)).join('') + '</div>';
      return;
    }
    if (tab === 'general'){
      const cols = GENERAL_COLUMNS[type].map(names =>
          names.map(n => headerField(type, n, doc, draft)).join(''));
      content.innerHTML = columnsHtml(cols)
          + '<div style="margin-top:20px; width:100%;">' + headerField(type, 'comment', doc, draft) + '</div>';
      return;
    }
    // транспорт: правится в черновике и после отправки, если статус разрешает
    const d = draftApi();
    const editable = !!(d && d.transportEditable(doc));
    const cols = transportColumns(type).map(names =>
        names.map(n => headerField(type, n, doc, editable)).join(''));
    // Отдельная кнопка — только после отправки: в черновике транспорт
    // сохраняется общей кнопкой вместе с шапкой (V84).
    const button = editable && d && d.transportSavedSeparately(doc)
        ? '<div style="margin-top:18px;"><button class="btn primary" data-action="save-transport"'
          + PORTAL.call('PORTAL.draft.saveTransport', type) + '>'
          + PORTAL.t('common.button.save_transport') + '</button></div>'
        : '';
    content.innerHTML = columnsHtml(cols) + (d ? d.transportStateHtml(type, doc) : '') + button;
  }

  /** Услуги по документу придут из WMS; пока их нет — так и пишем. */
  function servicesHtml(doc){
    const services = doc.services || [];
    if (!services.length) return '<div class="hint">' + PORTAL.t('common.text.no_document_services') + '</div>';
    const n = v => Number(v || 0).toLocaleString(PORTAL.timeLocale());
    const rows = services.map(s => '<tr><td>' + text(s.service) + '</td><td class="num">' + text(s.qty)
        + '</td><td class="num">' + n(s.cost) + '</td><td class="num">' + n(s.sum) + '</td><td class="num">'
        + text(s.markupPct) + '%</td><td class="num">' + n(s.sumWithMarkup) + '</td><td class="num">'
        + n(s.vatAmount) + '</td><td class="num">' + n(s.sumWithVat) + '</td></tr>').join('');
    const total = services.reduce((sum, s) => sum + (s.sumWithVat || 0), 0);
    const th = (key, num) => '<th' + (num ? ' class="num"' : '') + '>' + PORTAL.t(key) + '</th>';
    return '<div class="table-card"><table><thead><tr>' + th('billing.column.service')
        + th('common.label.quantity', true) + th('sku.text.cost', true) + th('common.label.amount', true)
        + th('common.column.markup', true) + th('common.column.amount_with_markup', true)
        + th('common.column.vat_12', true) + th('billing.column.amount_incl_vat', true)
        + '</tr></thead><tbody>' + rows + '<tr style="font-weight:700;"><td colspan="7">'
        + PORTAL.t('common.text.total') + '</td><td class="num">' + n(total) + '</td></tr></tbody></table></div>';
  }

  /* ===================== история (журнал действий, V116) ===================== */

  /** Таблицу рисует общий модуль history.js; ответ после ухода с вкладки не рисуется. */
  function loadHistory(type, doc, content){
    PORTAL.history.show(content, '/api/' + type + '/' + doc.docId + '/history', {
      createdKey: 'audit.action.doc_created',
      stillWanted: () => state[type].doc === doc && state[type].tab === 'history'
    });
  }

  /* ===================== карточка ===================== */

  /**
   * Показать документ.
   * keepTab — остаться на открытой вкладке: так после сохранения
   * человек не оказывается на «Общей» без всякой причины. Документ,
   * открытый заново (из списка, новый черновик), начинается с «Общей».
   */
  function show(type, doc, keepTab){
    const s = state[type];
    s.doc = doc;
    s.lines = (doc.lines || []).map(l => Object.assign({}, l, {checked: false}));
    s.detail = null;
    if (!keepTab) s.tab = 'general';
    el(TYPES[type].view + 'DetailBanner').innerHTML = '';
    render(type);
    showView(TYPES[type].view + '-detail');
  }

  /** Перерисовать карточку из памяти: шапка, вкладка, состав. */
  function render(type){
    const t = TYPES[type];
    const doc = state[type].doc;
    if (!doc) return;
    const draft = isDraft(doc);
    el(t.view + 'DraftActions').style.display = draft ? '' : 'none';
    // Ошибка отправки (V127): документ не черновик, но его можно отправить ещё раз
    el(t.view + 'ResendActions').style.display =
        doc.statusCode === PORTAL.STATUS.SENDING_FAILED ? '' : 'none';
    const meta = (key, value) => '<div>' + text(T(key)) + '<strong style="color:var(--ink);">'
        + text(value) + '</strong></div>';
    el(t.view + 'DocMeta').innerHTML = meta('common.label.document_id', doc.no)
        + meta('common.label.created_by', doc.author || '—')
        + meta('common.label.contract_number', contractNumber());
    el(t.view + 'DetailTitle').innerHTML = text(T(t.title)) + ' ' + text(doc.erpNo || doc.no) + ' '
        + badge(doc.status, doc.statusLabel);
    renderTab(type);
    el(t.view + 'AddRowBtn').style.display = draft ? '' : 'none';
    el(t.view + 'DeleteRowsBtn').style.display = draft ? '' : 'none';
    renderLines(type);
  }

  /** Видимые колонки таблицы в порядке, выбранном человеком (⚙ Колонки). */
  const visibleColumns = tableKey => PORTAL.tables.visible(tableKey);

  /** Состав документа. */
  function renderLines(type){
    const t = TYPES[type];
    const s = state[type];
    if (!s.doc) return;
    const draft = isDraft(s.doc);
    const cols = visibleColumns(t.view + 'Composition');
    el(t.view + 'CompositionHeadRow').innerHTML =
        '<th id="' + t.view + 'CheckboxHead" style="display:' + (draft ? '' : 'none') + '; width:36px;">'
        + '<input type="checkbox" aria-label="' + attr(T('common.aria.select_all_rows')) + '"'
        + (s.lines.length && s.lines.every(l => l.checked) ? ' checked' : '')
        + PORTAL.call('PORTAL.card.checkAll', type, PORTAL.CHECKED) + '></th>'
        + cols.map(c => '<th data-col="' + c.id + '"' + (c.num ? ' class="num"' : '') + '>'
            + PORTAL.tables.label(t.view + 'Composition', c) + '</th>').join('');
    el(t.view + 'DetailRows').innerHTML = s.lines.map((l, i) => {
      // Позиция, которой нет в WMS (V65), подсвечивается: документ с ней
      // не подтвердится, и человек должен видеть, какая строка мешает.
      const unknown = l.skuInWms === false;
      const cells = cols.map(c => {
        const cls = [c.num ? 'num' : '', c.wrap ? 'wrap' : ''].filter(Boolean).join(' ');
        const chip = unknown && c.id === 'code'
          ? '<span class="pd-chip" style="margin-left:8px; padding:1px 8px; border-radius:10px;'
            + ' background:#f6c343; color:#4a3500; font-size:11px;">' + text(T('sku.text.not_in_wms')) + '</span>'
          : '';
        return '<td data-col="' + c.id + '"' + (cls ? ' class="' + cls + '"' : '') + '>' + c.render(l) + chip + '</td>';
      }).join('');
      const open = draft ? PORTAL.call('PORTAL.lineModal.edit', type, i)
                         : PORTAL.call('PORTAL.card.openLine', type, i);
      const check = draft
        ? '<td><input type="checkbox" data-line-idx="' + i + '"' + (l.checked ? ' checked' : '')
          + PORTAL.call('PORTAL.card.check', type, i, PORTAL.CHECKED) + '></td>'
        : '';
      return '<tr style="cursor:pointer;' + (unknown ? ' background:#fff6e6;' : '') + '"' + open + '>'
          + check + cells + '</tr>';
    }).join('');
    el(t.view + 'DeleteRowsBtn').disabled = !s.lines.some(l => l.checked);
    totals(type);
    setTimeout(() => PORTAL.tables.updateScroll(t.view + 'Composition'), 0);
  }

  /** Число строк и штук в шапке — по составу на экране, ещё до сохранения. */
  function totals(type){
    const t = TYPES[type];
    const lines = state[type].lines;
    const count = el(t.field + '_lineCount');
    const pieces = el(t.field + '_totalPieces');
    if (count) count.value = lines.length;
    if (pieces) pieces.value = lines.reduce((sum, l) => sum + (Number(l[t.qty]) || 0), 0);
  }

  /** Строки в том виде, в каком их держит документ: без галочки выбора. */
  function keepLines(type){
    const s = state[type];
    if (s.doc) s.doc.lines = s.lines.map(l => {
      const copy = Object.assign({}, l);
      delete copy.checked;
      return copy;
    });
  }

  /** Состав изменился: перерисовать и сообщить черновику (он сохранит сам). */
  function linesChanged(type){
    keepLines(type);
    renderLines(type);
    const d = draftApi();
    if (d) d.linesChanged(type);
  }

  /* ===================== галочки и удаление строк ===================== */

  function check(type, index, on){
    const line = state[type].lines[index];
    if (line) line.checked = !!on;
    el(TYPES[type].view + 'DeleteRowsBtn').disabled = !state[type].lines.some(l => l.checked);
  }

  function checkAll(type, on){
    state[type].lines.forEach(l => { l.checked = !!on; });
    renderLines(type);
  }

  function deleteChecked(type){
    const s = state[type];
    const before = s.lines.length;
    s.lines = s.lines.filter(l => !l.checked);
    linesChanged(type);
    notify(T('common.message.lines_deleted', {count: before - s.lines.length}));
  }

  /* ===================== «Детализация» строки ===================== */

  /** Что показать над партиями: количества строки по виду документа. */
  const DETAIL_HEAD = {
    receipts: [['col.receiptComposition.expected', 'expected', true],
               ['col.receiptComposition.accepted', 'accepted', true],
               ['col.stock.defect', 'defect', true]],
    orders: [['col.orderComposition.ordered', 'ordered', true],
             ['col.orderComposition.picked', 'picked', true],
             ['col.orderComposition.weight', 'weight', false]],
    transfers: [['col.transferComposition.qty', 'qty', true]]
  };

  function openLine(type, index){
    const t = TYPES[type];
    const s = state[type];
    const l = s.lines[index];
    if (!l) return;
    s.detail = index;
    el(t.view + 'LineDetailTitle').innerHTML =
        '<div style="font-size:13px; color:var(--muted); font-weight:400; margin-bottom:4px;">'
        + text(T(t.title)) + ' ' + text(s.doc.erpNo || s.doc.no) + '</div>'
        + '<div>' + text(l.name) + '</div><div style="color:var(--muted); margin-top:2px;">' + text(l.code) + '</div>';
    const kv = (label, value) => '<div class="kv"><label>' + label + '</label><div>' + text(value) + '</div></div>';
    el(t.view + 'LineDetailHead').innerHTML = DETAIL_HEAD[type]
        .map(([key, prop, unit]) => kv(PORTAL.t(key), dash(l[prop]) + (unit && l.unit ? ' ' + l.unit : ''))).join('')
        + (l.lineComment ? kv(PORTAL.t('sku.label.comment'), l.lineComment) : '');
    renderLineDetail(type);
    showView(t.view + '-line-detail');
  }

  /** Партии строки (они приходят с карточкой документа, lineDetails). */
  function renderLineDetail(type){
    const t = TYPES[type];
    const s = state[type];
    const l = s.detail === null ? null : s.lines[s.detail];
    if (!l) return;
    const batches = l.batches || [];
    const cols = visibleColumns(t.view + 'LineDetail');
    el(t.view + 'LineDetailHeadRow').innerHTML = cols.map(c =>
        '<th' + (c.num ? ' class="num"' : '') + '>' + PORTAL.tables.label(t.view + 'LineDetail', c) + '</th>').join('');
    el(t.view + 'LineDetailRows').innerHTML = batches.length
      ? batches.map(b => '<tr>' + cols.map(c => '<td' + (c.num ? ' class="num"' : '') + '>'
          + c.render(b) + '</td>').join('') + '</tr>').join('')
      : '<tr><td colspan="' + cols.length + '" style="color:var(--muted);">'
        + text(T('admin.text.no_data')) + '</td></tr>';
  }

  function closeLine(type){
    state[type].detail = null;
    showView(TYPES[type].view + '-detail');
  }

  /* ===================== удаление черновика ===================== */

  function askDelete(type){
    if (!isDraft(state[type].doc)) return;
    el(TYPES[type].confirmDelete).classList.add('active');
  }

  function closeAskDelete(type){
    el(TYPES[type].confirmDelete).classList.remove('active');
  }

  /* ===================== качество строки ===================== */

  /**
   * Качество строки — КОД перечисления enum_quality (заказ V89,
   * поступление V100, перемещение V101): 01 годный, 02 брак,
   * 03 некомплект, 04 контроль качества. Пусто — значит годный.
   */
  const quality = line => (line && line.quality) || GOOD;

  /** Имя качества на языке пользователя (приходит с сервера). */
  function qualityName(line){
    const code = quality(line);
    const found = (PORTAL.qualities || []).find(q => q.code === code);
    return found ? found.name : code;
  }

  /* ===================== наружу ===================== */

  // Смена раскладки колонок (⚙ Колонки, вход) перерисовывает состав и партии.
  Object.keys(TYPES).forEach(type => {
    PORTAL.tables.register(TYPES[type].view + 'Composition', {render: () => renderLines(type)});
    PORTAL.tables.register(TYPES[type].view + 'LineDetail', {render: () => renderLineDetail(type)});
  });

  PORTAL.card = {
    TYPES, GOOD,
    show,
    render,
    /** Открытый документ вида type (null — не открыт). */
    doc: type => state[type].doc,
    /** Строки открытого документа — те, что на экране. */
    lines: type => state[type].lines,
    /** Заменить строки (окно ввода строки) и перерисовать. */
    setLines: (type, lines) => { state[type].lines = lines; linesChanged(type); },
    forget: type => { state[type].doc = null; state[type].lines = []; },
    /** Открытая вкладка. */
    activeTab: type => state[type].tab,
    /** Перейти на вкладку; набранное в шапке переносится в документ (draft.js). */
    tab: (type, tab) => {
      const d = draftApi();
      if (d && isDraft(state[type].doc)) d.keepUnsaved(type);
      state[type].tab = tab;
      renderTab(type);
      totals(type);
    },
    renderLines,
    renderLineDetail,
    openLine,
    closeLine,
    check,
    checkAll,
    deleteChecked,
    askDelete,
    closeAskDelete,
    extraFields,
    quality,
    qualityName
  };
})();
