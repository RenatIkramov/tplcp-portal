#!/usr/bin/env python3
"""Этап 4: подключаем экран «Запасы» демо к реальному API портала.

Правка минимальная и обратимая: исходный файл не переписывается построчно,
в конец <body> добавляется один блок <script>, который переопределяет
источник данных для экрана «Запасы». Остальные экраны продолжают работать
на демо-данных, пока под них не появятся эндпоинты.
"""
import sys, re

SRC = '/root/.claude/uploads/9b555f04-0ff6-56df-8eda-edbeec9ab0c5/52c39c71-3pl-customer-portal-demo_1.html'
DST = '/home/claude/tplcp-portal/src/main/resources/static/index.html'

PATCH = r"""
<!-- ================= ЭТАП 4: экран «Запасы» из реального API =================
     Этот блок добавлен к демо-файлу. Он не меняет вёрстку — только подменяет
     источник данных для таблицы «Запасы»: вместо массива products в памяти
     данные берутся из GET /api/stock.

     Остальные экраны пока работают на демо-данных. По мере появления
     эндпоинтов они переключаются тем же способом.
================================================================================ -->
<script>
(function(){
  'use strict';

  // Поклажедатель и пользователь берутся из сеанса (этап 5): сервер знает,
  // кто вошёл, по cookie сессии. В адресе и заголовках ничего не передаётся —
  // подставить чужой код поклажедателя из браузера невозможно.
  const PORTAL = {
    depositorCode: '',      // заполняется из GET /api/auth/session
    depositorName: '',
    session: null,
    user: null,
    loaders: [],           // что перезагрузить после входа и смены поклажедателя
    rows: [],
    loadedAt: null,
    error: null,
    loading: false
  };
  window.PORTAL = PORTAL;

  const num = v => v === null || v === undefined ? 0 : Number(v);

  /** Приводит строку API к виду, который ожидают колонки таблицы демо. */
  function toRow(r){
    return {
      // поля, по которым работают фильтры и сортировка демо
      code: r.skuCode,
      name: r.skuName,
      group: r.itemGroupName || '—',
      unit: r.unitName || '',
      warehouse: r.warehouseId,
      warehouseName: r.warehouseName,
      // количества
      stock: num(r.quantity),
      reserved: num(r.reservedQty),
      blocked: num(r.blockedQty),
      available: num(r.availableQty),
      defect: num(r.defectQty),
      incoming: num(r.incomingQty),
      batchCount: num(r.batchCount),
      syncedAt: r.syncedAt
    };
  }

  /** Колонки экрана «Запасы» начинают читать значения прямо из строки API. */
  function patchColumns(){
    if (typeof stockColumnDefs === 'undefined') return;
    const set = (id, render) => {
      const c = stockColumnDefs.find(c => c.id === id);
      if (c) c.render = render;
    };
    set('warehouse', p => p.warehouseName || p.warehouse);
    set('code',      p => '<strong>' + p.code + '</strong>');
    set('name',      p => p.name);
    set('group',     p => p.group);
    set('unit',      p => p.unit);
    set('stock',     p => p.stock);
    set('available', p => p.available);
    set('reserved',  p => p.reserved);
    set('blocked',   p => p.blocked);
    set('defect',    p => p.defect);
    set('incoming',  p => p.incoming);
  }

  /** Строка состояния: «данные на 14:32 · Обновить» (решение Р4 — кэш WMS). */
  function renderStatusBar(){
    const host = document.getElementById('stockBody');
    if (!host) return;
    const table = host.closest('table');
    if (!table) return;
    let bar = document.getElementById('portalStockStatus');
    if (!bar){
      bar = document.createElement('div');
      bar.id = 'portalStockStatus';
      bar.style.cssText = 'margin:6px 0 10px; font-size:12px; color:var(--muted);';
      table.parentNode.insertBefore(bar, table);
    }
    if (PORTAL.loading){
      bar.innerHTML = 'Загрузка данных из портала…';
      return;
    }
    if (PORTAL.error){
      bar.innerHTML = '<span style="color:#c0392b;">Не удалось получить данные: '
        + PORTAL.error + '</span> · <a href="#" id="portalStockRefresh">Повторить</a>';
    } else {
      const t = PORTAL.loadedAt
        ? PORTAL.loadedAt.toLocaleTimeString('ru-RU', {hour:'2-digit', minute:'2-digit'})
        : '—';
      bar.innerHTML = 'Поклажедатель <b>' + PORTAL.depositorCode + '</b> · данные на ' + t
        + ' · <a href="#" id="portalStockRefresh">Обновить</a>';
    }
    const link = document.getElementById('portalStockRefresh');
    if (link) link.onclick = e => { e.preventDefault(); loadStock(); };
  }

  async function loadStock(){
    PORTAL.loading = true;
    PORTAL.error = null;
    renderStatusBar();
    try {
      const url = '/api/stock';
      const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
      if (!resp.ok){
        throw new Error(await PORTAL.сообщениеОтвета(resp));
      }
      PORTAL.rows = (await resp.json()).map(toRow);
      PORTAL.loadedAt = new Date();
    } catch (e){
      PORTAL.rows = [];
      PORTAL.error = e.message;
    } finally {
      PORTAL.loading = false;
      renderStatusBar();
      if (typeof renderStock === 'function') renderStock();
    }
  }
  window.portalReloadStock = loadStock;

  /**
   * Экраны не грузят данные сами по себе: до входа запрашивать нечего,
   * сервер всё равно ответит 401. Каждый экран регистрирует здесь свою
   * загрузку, а блок входа (этап 5) вызывает её после того, как стало
   * известно, кто вошёл и с каким поклажедателем он работает.
   */
  /**
   * Текст ошибки для человека. Сервер почти всегда присылает его сам
   * (поле message), но если ответ пустой — подставляем понятный по смыслу
   * кода, а не «код 400», из которого пользователю ничего не ясно.
   */
  const ПО_КОДУ = {
    400: 'Запрос не принят: проверьте заполненные поля',
    401: 'Нужно войти в портал заново',
    403: 'Действие недоступно вашей учётной записи',
    404: 'Запись не найдена',
    409: 'Действие сейчас невозможно',
    422: 'Данные заполнены не полностью',
    429: 'Слишком много попыток. Повторите позже',
    500: 'Портал не смог выполнить запрос. Обратитесь к администратору',
    502: 'Нет связи с сервером портала',
    503: 'Портал временно недоступен'
  };
  PORTAL.текстОшибки = (status, data) =>
    (data && (data.message || data.detail)) || ПО_КОДУ[status]
    || ('запрос не выполнен (код ' + status + ')');

  /** Разбирает неудачный ответ fetch и возвращает готовое сообщение. */
  PORTAL.сообщениеОтвета = async resp => {
    let data = null;
    try { data = await resp.clone().json(); } catch (e) {}
    return PORTAL.текстОшибки(resp.status, data);
  };

  PORTAL.onLogin = fn => { PORTAL.loaders.push(fn); };
  PORTAL.reloadAll = () => { PORTAL.loaders.forEach(fn => { try { fn(); } catch (e) {} }); };

  /**
   * Забыть всё, что было показано. Вызывается при выходе и перед сменой
   * поклажедателя: иначе на экране остались бы чужие данные до того момента,
   * как придёт ответ сервера, — а при выходе они видны и вовсе не тому.
   */
  PORTAL.forgetAll = () => {
    PORTAL.rows = [];
    PORTAL.loadedAt = null;
    PORTAL.error = null;
    [PORTAL.docs, PORTAL.docsError, PORTAL.docsLoadedAt, PORTAL.catalog, PORTAL.catalogError]
      .forEach(store => {
        if (!store) return;
        Object.keys(store).forEach(k => { store[k] = Array.isArray(store[k]) ? [] : null; });
      });
    ['renderStock','renderReceipts','renderOrders','renderTransfers','renderSku',
     'renderBillingServices','renderBillingInvoices','renderSuppliers','renderConsignees']
      .forEach(fn => { if (typeof window[fn] === 'function'){ try { window[fn](); } catch (e) {} } });
  };

  /** Клик по строке: карточки товара из API пока нет (появится на этапе 6). */
  window.portalOpenStockRow = function(code){
    if (typeof notify === 'function'){
      notify('Карточка товара ' + code + ' появится, когда будет эндпоинт /api/sku');
    }
  };

  /**
   * Замена штатного renderStock: та же фильтрация, сортировка и постраничный
   * вывод, но источник — PORTAL.rows вместо массива products.
   */
  function renderStockFromApi(){
    const body = document.getElementById('stockBody');
    if (!body) return;

    const codeQ = (document.getElementById('stockSearchCode')?.value || '').trim().toLowerCase();
    const nameQ = (document.getElementById('stockSearchName')?.value || '').trim().toLowerCase();
    const groupQ = document.getElementById('stockFilterGroup')?.value || '';
    const defectOnly = document.getElementById('stockDefectFilterCb')?.checked;

    let rows = PORTAL.rows.slice();
    if (codeQ)  rows = rows.filter(p => (p.code || '').toLowerCase().includes(codeQ));
    if (nameQ)  rows = rows.filter(p => (p.name || '').toLowerCase().includes(nameQ));
    if (groupQ) rows = rows.filter(p => p.group === groupQ);
    if (defectOnly) rows = rows.filter(p => p.defect > 0);

    if (typeof fcApplySort === 'function') rows = fcApplySort('stock', rows);

    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage('stock', rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }

    const counter = document.getElementById('stockCount');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead('stock');
    body.innerHTML = items.length
      ? items.map(p => renderTableRow('stock', p, 'portalOpenStockRow')).join('')
      : '<tr><td colspan="20" style="color:var(--muted);">'
        + (PORTAL.error ? 'Данные недоступны' : 'Ничего не найдено') + '</td></tr>';

    if (typeof renderPaginationBar === 'function') renderPaginationBar('stock', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('stock'), 0);
    renderStatusBar();
  }

  function install(){
    patchColumns();
    window.renderStock = renderStockFromApi;
    PORTAL.onLogin(loadStock);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


PATCH_DOCS = r"""
<!-- ============ ЭТАП 6: экраны «Поступления» и «Заказы» из реального API ============
     Тот же приём, что и для «Запасов»: вёрстка демо не меняется, подменяется
     только источник данных списков. Карточки документов пока демонстрационные —
     они подключаются следующим шагом.
=================================================================================== -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  PORTAL.docs = { receipts: [], orders: [] };
  PORTAL.docsLoadedAt = { receipts: null, orders: null };
  PORTAL.docsError = { receipts: null, orders: null };

  const num = v => v === null || v === undefined ? 0 : Number(v);

  /** Код статуса из базы → класс бейджа демо (draft / warn / ok / err). */
  const BADGE = {
    draft: 'draft',
    awaiting: 'warn', in_progress: 'warn', received: 'ok', discrepancy: 'err',
    reserved: 'warn', picking: 'warn', shipped: 'ok', delayed: 'err',
    forming: 'warn', in_transit: 'warn', form_error: 'err'
  };

  /** 2026-09-01 → 01.09.2026 (формат, в котором даты показывает демо). */
  function ruDate(iso){
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  }
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;

  function commonFields(r){
    return {
      no: r.documentNo,
      erpNo: r.documentNo,            // колонка «Номер» в демо
      erpNumber: dash(r.erpNumber),   // номер в ERP — отдельная колонка
      wmsNumber: dash(r.docNumber),
      status: BADGE[r.statusCode] || 'warn',
      statusLabel: r.statusName || '',
      statusCode: r.statusCode,
      date: ruDate(r.docDate),
      dateIso: r.docDate,
      author: dash(r.authorName),
      trip: dash(r.trip),
      comment: dash(r.comment),
      lineCount: num(r.lineCount),
      weight: num(r.weightKg),
      volume: num(r.volumeM3),
      palletCount: num(r.palletCount),
      cargoPlaces: num(r.cargoPlaces),
      totalPieces: num(r.totalPieces),
      carrier: dash(r.carrier),
      transportType: dash(r.transportType),
      transportBrand: dash(r.transportBrand),
      carNumber: dash(r.carNumber),
      trailerNumber: dash(r.trailerNumber),
      techPassportNumber: dash(r.techPassportNumber),
      containerNumber: dash(r.containerNumber),
      sealNumber: dash(r.sealNumber),
      driverName: dash(r.driverName),
      driverPhone: dash(r.driverPhone),
      driverIdNumber: dash(r.driverIdNumber),
      driverInn: dash(r.driverInn),
      docId: r.docId
    };
  }

  const toReceipt = r => Object.assign(commonFields(r), {
    warehouse: r.warehouseName || r.warehouseId,
    supplier: dash(r.supplierName),
    docType: dash(r.docTypeName),
    eta: ruDate(r.etaDate),
    arrivalDate: ruDate(r.arrivalDate),
    receivingEndDate: ruDate(r.receivingEndDate),
    accompanyingDocNumber: dash(r.accompanyingDocNumber),
    invoiceNumber: dash(r.invoiceNumber),
    gtd: dash(r.gtd)
  });

  const toOrder = o => Object.assign(commonFields(o), {
    warehouse: o.warehouseName || o.warehouseId,
    consignee: dash(o.consigneeName),
    deliveryAddress: dash(o.deliveryAddress),
    orderType: dash(o.orderTypeName),
    plannedShip: ruDate(o.plannedShipDate),
    ship: ruDate(o.shipDate),
    deliveryDate: ruDate(o.deliveryDate)
  });

  /** Колонки, которые в демо вычислялись из mock-логики, читают поля API. */
  function patchColumns(){
    if (typeof tableColumns === 'undefined') return;
    const set = (table, id, render) => {
      const c = (tableColumns[table] || []).find(c => c.id === id);
      if (c) c.render = render;
    };
    set('receipts', 'warehouse', r => r.warehouse);
    set('receipts', 'receivingEndDate', r => r.receivingEndDate);
    set('receipts', 'arrivalDate', r => r.arrivalDate);
    set('receipts', 'volume', r => r.volume);
    set('orders', 'warehouse', o => o.warehouse);
    set('orders', 'volume', o => o.volume);
  }

  function statusBar(table){
    const body = document.getElementById(table + 'Body');
    if (!body) return;
    const host = body.closest('table');
    if (!host) return;
    let bar = document.getElementById('portal-status-' + table);
    if (!bar){
      bar = document.createElement('div');
      bar.id = 'portal-status-' + table;
      bar.style.cssText = 'margin:6px 0 10px; font-size:12px; color:var(--muted);';
      host.parentNode.insertBefore(bar, host);
    }
    const err = PORTAL.docsError[table];
    if (err){
      bar.innerHTML = '<span style="color:#c0392b;">Не удалось получить данные: ' + err + '</span>'
        + ' · <a href="#" data-portal-reload="' + table + '">Повторить</a>';
    } else {
      const t = PORTAL.docsLoadedAt[table];
      bar.innerHTML = 'Поклажедатель <b>' + PORTAL.depositorCode + '</b> · данные на '
        + (t ? t.toLocaleTimeString('ru-RU', {hour:'2-digit', minute:'2-digit'}) : '—')
        + ' · <a href="#" data-portal-reload="' + table + '">Обновить</a>';
    }
    const link = bar.querySelector('[data-portal-reload]');
    if (link) link.onclick = e => { e.preventDefault(); load(table); };
  }

  async function load(table){
    try {
      const url = '/api/' + table;
      const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
      if (!resp.ok){
        throw new Error(await PORTAL.сообщениеОтвета(resp));
      }
      const data = await resp.json();
      PORTAL.docs[table] = data.map(table === 'receipts' ? toReceipt : toOrder);
      PORTAL.docsLoadedAt[table] = new Date();
      PORTAL.docsError[table] = null;
    } catch (e){
      PORTAL.docs[table] = [];
      PORTAL.docsError[table] = e.message;
    }
    render(table);
  }

  function render(table){
    const body = document.getElementById(table + 'Body');
    if (!body) return;

    let rows = PORTAL.docs[table].slice();

    // фильтры демо: поиск по номеру, по контрагенту, по рейсу, статус, период
    const val = id => (document.getElementById(id)?.value || '').trim().toLowerCase();
    const noQ = val(table === 'receipts' ? 'receiptsSearchNo' : 'ordersSearchNo');
    const partyQ = val(table === 'receipts' ? 'receiptsSearchSupplier' : 'ordersSearchConsignee');
    const tripQ = val(table === 'receipts' ? 'receiptsSearchTrip' : 'ordersSearchTrip');
    const statusQ = document.getElementById(table + 'FilterStatus')?.value || '';
    const fromISO = document.getElementById(table + 'DateFrom')?.value || '';
    const toISO = document.getElementById(table + 'DateTo')?.value || '';

    if (noQ) rows = rows.filter(r => (r.no || '').toLowerCase().includes(noQ)
                                  || (r.erpNumber || '').toLowerCase().includes(noQ)
                                  || (r.wmsNumber || '').toLowerCase().includes(noQ));
    if (partyQ) rows = rows.filter(r => ((table === 'receipts' ? r.supplier : r.consignee) || '')
                                          .toLowerCase().includes(partyQ));
    if (tripQ) rows = rows.filter(r => (r.trip || '').toLowerCase().includes(tripQ));
    if (statusQ && statusQ !== 'Все'){
      rows = rows.filter(r => (r.statusLabel || '').toLowerCase().includes(statusQ.toLowerCase()));
    }
    if (fromISO) rows = rows.filter(r => (r.dateIso || '') >= fromISO);
    if (toISO)   rows = rows.filter(r => (r.dateIso || '') <= toISO);

    if (typeof fcApplySort === 'function') rows = fcApplySort(table, rows);

    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage(table, rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }

    const counter = document.getElementById(table + 'Count');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead(table);
    body.innerHTML = items.length
      ? items.map(r => renderTableRow(table, r, 'portalOpenDocument')).join('')
      : '<tr><td colspan="24" style="color:var(--muted);">'
        + (PORTAL.docsError[table] ? 'Данные недоступны' : 'Ничего не найдено') + '</td></tr>';

    if (typeof renderPaginationBar === 'function') renderPaginationBar(table, totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth(table), 0);
    statusBar(table);
  }

  /** Карточка документа появится следующим шагом — пока подсказка. */
  window.portalOpenDocument = function(no){
    if (typeof notify === 'function'){
      notify('Карточка документа ' + no + ' подключается к API следующим шагом');
    }
  };

  /** Перезагрузка списков — используется редактором черновиков. */
  window.portalReloadDocuments = function(){
    load('receipts');
    load('orders');
  };

  function install(){
    patchColumns();
    window.renderReceipts = () => render('receipts');
    window.renderOrders = () => render('orders');
    PORTAL.onLogin(() => { load('receipts'); load('orders'); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


PATCH_CATALOG = r"""
<!-- ======= ЭТАП 6: «Товары», «Биллинг» и справочники из реального API =======
     Тот же приём: вёрстка демо не меняется, подменяется источник данных.
=========================================================================== -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  PORTAL.catalog = {};
  PORTAL.catalogError = {};

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const yesNo = v => v === true ? 'Да' : (v === false ? 'Нет' : '—');
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };
  const money = v => v === null || v === undefined ? '—'
    : Number(v).toLocaleString('ru-RU', {minimumFractionDigits: 2, maximumFractionDigits: 2});

  async function fetchCatalog(name){
    const url = '/api/' + name;
    const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
    if (!resp.ok){
      throw new Error(await PORTAL.сообщениеОтвета(resp));
    }
    return resp.json();
  }

  async function load(name, afterRender){
    try {
      PORTAL.catalog[name] = await fetchCatalog(name);
      PORTAL.catalogError[name] = null;
    } catch (e){
      PORTAL.catalog[name] = [];
      PORTAL.catalogError[name] = e.message;
    }
    if (afterRender) afterRender();
  }

  /* ---------------- Товары (номенклатура) ---------------- */

  // id колонки демо → поле строки API (или функция)
  const SKU_MAP = {
    itemCode: 'itemCode', code: 'code', name: 'name', unit: 'uomName',
    orderUnit: 'orderUnitName', barcode: 'barcode',
    group: 'itemGroupName', productType: 'itemTypeName',
    length: 'length', width: 'width', height: 'height', volume: 'volume',
    weightGross: 'weightGross', weightNet: 'weightNet',
    manufacturer: 'manufacturer', countryOrigin: 'countryOrigin', brand: 'brand',
    batchTracking: 'batchTracking', expiryTracking: 'expiryTracking',
    statusProduct: 'statusName', itemFullName: 'itemFullName',
    hazardClass: 'hazardClassName', hazardGroup: 'hazardGroupName',
    maxStorageTemp: 'maxStorageTemp', minStorageTemp: 'minStorageTemp',
    maxHumidity: 'maxHumidity', minHumidity: 'minHumidity',
    minDaysForReceipt: 'minDaysForReceipt', minDaysForReservation: 'minDaysForReservation',
    shelfLifeDays: 'shelfLifeDays', color: 'color', size: 'size',
    abcClass: 'abcClass', xyzClass: 'xyzClass',
    hsCode: 'hsCode', enstruCode: 'enstruCode',
    currency: 'currencyCode', cost: 'cost', comment: 'comment',
    createdBy: 'createdBy', updatedBy: 'updatedBy', customAttr: 'customAttr1'
  };
  const SKU_BOOL = ['hasPackaging', 'isFragile', 'isSerialTracked', 'isKit',
                    'isMarkingRequired', 'isExcise', 'isControlled', 'isWmsActive'];

  function patchSkuColumns(){
    if (typeof tableColumns === 'undefined' || !tableColumns.sku) return;
    tableColumns.sku.forEach(c => {
      if (SKU_MAP[c.id]){
        const field = SKU_MAP[c.id];
        c.render = p => c.id === 'code' ? '<strong>' + dash(p[field]) + '</strong>' : dash(p[field]);
      } else if (SKU_BOOL.includes(c.id)){
        c.render = p => yesNo(p[c.id]);
      } else if (c.id === 'createdAt' || c.id === 'updatedAt'){
        c.render = p => ruDate(p[c.id]);
      } else {
        c.render = p => dash(p[c.id]);
      }
    });
  }

  function renderSkuFromApi(){
    const body = document.getElementById('skuBody');
    if (!body) return;
    const val = id => (document.getElementById(id)?.value || '').trim().toLowerCase();
    const codeQ = val('skuSearchCode');
    const nameQ = val('skuSearchName');
    const groupQ = document.getElementById('skuFilterGroup')?.value || '';
    const typeQ = document.getElementById('skuFilterType')?.value || '';

    let rows = (PORTAL.catalog.sku || []).slice();
    if (codeQ)  rows = rows.filter(p => (p.code || '').toLowerCase().includes(codeQ)
                                     || (p.itemCode || '').toLowerCase().includes(codeQ));
    if (nameQ)  rows = rows.filter(p => (p.name || '').toLowerCase().includes(nameQ));
    if (groupQ && groupQ !== 'Все') rows = rows.filter(p => p.itemGroupName === groupQ);
    if (typeQ && typeQ !== 'Все')   rows = rows.filter(p => p.itemTypeName === typeQ);

    if (typeof fcApplySort === 'function') rows = fcApplySort('sku', rows);

    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage('sku', rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }
    const counter = document.getElementById('skuCount');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead('sku');
    body.innerHTML = items.length
      ? items.map(p => renderTableRow('sku', p, 'portalOpenSku')).join('')
      : '<tr><td colspan="24" style="color:var(--muted);">'
        + (PORTAL.catalogError.sku ? 'Данные недоступны' : 'Ничего не найдено') + '</td></tr>';
    if (typeof renderPaginationBar === 'function') renderPaginationBar('sku', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('sku'), 0);
  }

  window.portalOpenSku = function(code){
    if (typeof notify === 'function') notify('Карточка товара ' + code + ' подключается следующим шагом');
  };

  /* ---------------- Биллинг ---------------- */

  function renderTariffsFromApi(){
    const body = document.getElementById('billingServicesBody');
    if (!body) return;
    const rows = PORTAL.catalog['service-tariffs'] || [];
    body.innerHTML = rows.length
      ? rows.map(s => '<tr><td>' + dash(s.service) + '</td><td class="num">' + money(s.rate)
          + '</td><td>' + dash(s.unit) + '</td><td>' + dash(s.frequency) + '</td></tr>').join('')
      : '<tr><td colspan="4" style="color:var(--muted);">'
        + (PORTAL.catalogError['service-tariffs'] ? 'Данные недоступны'
            : 'Тарифы для этого поклажедателя не заданы') + '</td></tr>';
  }

  const INVOICE_BADGE = {invoice_paid: 'ok', invoice_awaiting: 'warn', invoice_overdue: 'err'};

  function renderInvoicesFromApi(){
    const body = document.getElementById('billingInvoicesBody');
    if (!body) return;
    const rows = PORTAL.catalog.invoices || [];
    body.innerHTML = rows.length
      ? rows.map(inv => {
          const period = ruDate(inv.periodFrom) + ' — ' + ruDate(inv.periodTo);
          const badgeHtml = typeof badge === 'function'
            ? badge(INVOICE_BADGE[inv.statusCode] || 'warn', inv.statusName || '')
            : dash(inv.statusName);
          const pdfBtn = inv.hasPdf
            ? '<button class="btn ghost" onclick="event.stopPropagation(); notify(\'Выгрузка PDF появится вместе с генерацией счетов\')">Скачать PDF</button>'
            : '—';
          return '<tr><td><strong>' + dash(inv.invoiceNumber) + '</strong></td><td>' + period
            + '</td><td>' + ruDate(inv.issueDate) + '</td><td class="num">' + money(inv.amountNoVat)
            + '</td><td class="num">' + money(inv.amountWithVat) + '</td><td>' + badgeHtml
            + '</td><td>' + pdfBtn + '</td></tr>';
        }).join('')
      : '<tr><td colspan="7" style="color:var(--muted);">'
        + (PORTAL.catalogError.invoices ? 'Данные недоступны'
            : 'Счетов для этого поклажедателя нет') + '</td></tr>';
  }

  /* ---------------- Справочники: поставщики и грузополучатели ---------------- */

  function renderSuppliersFromApi(){
    const body = document.getElementById('suppliersBody');
    if (!body) return;
    const rows = PORTAL.catalog.suppliers || [];
    const counter = document.getElementById('suppliersCount');
    if (counter) counter.textContent = '(' + rows.length + ')';
    body.innerHTML = rows.length
      ? rows.map(s => '<tr><td>' + dash(s.code) + '</td><td>' + dash(s.name) + '</td><td>'
          + dash(s.bin) + '</td><td>' + dash(s.legalAddress) + '</td><td>' + dash(s.contactName)
          + '</td><td>' + dash(s.contactPhone) + '</td><td>' + dash(s.contactEmail)
          + '</td></tr>').join('')
      : '<tr><td colspan="7" style="color:var(--muted);">Справочник пуст</td></tr>';
  }

  function renderConsigneesFromApi(){
    const body = document.getElementById('consigneesBody');
    if (!body) return;
    const rows = PORTAL.catalog.consignees || [];
    const counter = document.getElementById('consigneesCount');
    if (counter) counter.textContent = '(' + rows.length + ')';
    body.innerHTML = rows.length
      ? rows.map(c => '<tr><td>' + dash(c.code) + '</td><td>' + dash(c.name) + '</td><td>'
          + dash(c.bin) + '</td><td>' + dash(c.legalAddress) + '</td><td>' + dash(c.defaultAddress)
          + '</td><td>' + dash(c.contactName) + '</td><td>' + dash(c.contactPhone) + '</td><td>'
          + dash(c.contactEmail) + '</td></tr>').join('')
      : '<tr><td colspan="8" style="color:var(--muted);">Справочник пуст</td></tr>';
  }

  function install(){
    patchSkuColumns();
    window.renderSku = renderSkuFromApi;
    window.renderBillingServices = renderTariffsFromApi;
    window.renderBillingInvoices = renderInvoicesFromApi;
    window.renderSuppliers = renderSuppliersFromApi;
    window.renderConsignees = renderConsigneesFromApi;

    PORTAL.onLogin(() => {
      load('sku', renderSkuFromApi);
      load('service-tariffs', renderTariffsFromApi);
      load('invoices', renderInvoicesFromApi);
      load('suppliers', renderSuppliersFromApi);
      load('consignees', renderConsigneesFromApi);
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


PATCH_CARD = r"""
<!-- ========== ЭТАП 6: карточки документов из реального API ==========
     Приём отличается от списков: вёрстку карточки и её вкладки рисует
     сам демо-код, поэтому мы не переписываем отрисовку, а подкладываем
     документу реальные данные — шапку, состав и партии — и вызываем
     штатную функцию демо. Так все вкладки (транспорт, доп. информация,
     услуги, фото) продолжают работать без правок.
================================================================== -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const n = v => v === null || v === undefined ? 0 : Number(v);
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /** Строка состава документа в том виде, в каком её ждут колонки демо. */
  function toLine(type, l){
    const base = {
      code: l.skuCode,
      name: l.skuName,
      unit: l.unitName || '',
      price: l.price === null || l.price === undefined ? null : n(l.price),
      currency: l.currencyCode || '',
      lineComment: dash(l.lineComment),
      lineNumber: l.lineNumber,
      batchCount: n(l.batchCount),
      sum: l.lineSum === null || l.lineSum === undefined ? null : n(l.lineSum)
    };
    if (type === 'receipts'){
      return Object.assign(base, {
        expected: n(l.expectedQty),
        expectedWms: l.expectedQtyWms === null ? null : n(l.expectedQtyWms),
        accepted: n(l.acceptedQty),
        defect: n(l.defectQty)
      });
    }
    if (type === 'orders'){
      return Object.assign(base, {
        ordered: n(l.orderedQty),
        orderedWms: l.orderedQtyWms === null ? null : n(l.orderedQtyWms),
        picked: n(l.pickedQty),
        weight: n(l.weightKg)
      });
    }
    return Object.assign(base, { qty: n(l.qty), qtyWms: n(l.qtyWms) });
  }

  /** Запись партии для экрана «Детализация». */
  function toBatch(d){
    const qty = d.acceptedQty !== undefined && d.acceptedQty !== null ? d.acceptedQty
              : (d.pickedQty !== undefined && d.pickedQty !== null ? d.pickedQty : d.qty);
    return {
      q: dash(d.quality),
      batch: dash(d.batchNumber),
      series: dash(d.seriesNumber),
      exp: ruDate(d.expiryDate),
      stock: n(qty),
      lineNumber: d.lineNumber
    };
  }

  async function fetchCard(type, docId){
    const url = '/api/' + type + '/' + encodeURIComponent(docId);
    const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
    if (!resp.ok){
      throw new Error(await PORTAL.сообщениеОтвета(resp));
    }
    return resp.json();
  }

  /**
   * Открытие карточки: подкладываем документ с реальными данными в массив,
   * из которого демо его берёт, и вызываем штатную функцию отрисовки.
   */
  async function openDocument(type, no){
    const row = (PORTAL.docs[type] || []).find(r => r.no === no);
    if (!row){
      if (typeof notify === 'function') notify('Документ ' + no + ' не найден в загруженном списке');
      return;
    }
    try {
      const card = await fetchCard(type, row.docId);

      const lines = (card.lines || []).map(l => toLine(type, l));
      const details = (card.lineDetails || []).map(toBatch);
      // партии раскладываем по строкам: демо берёт их из line.batches
      lines.forEach(line => {
        line.batches = details.filter(b => b.lineNumber === line.lineNumber);
      });

      const doc = Object.assign({}, row, {lines: lines, items: lines.length});

      // Массивы receipts/orders/transfers объявлены в демо через let, то есть
      // в window их нет — обращаемся к ним напрямую и подменяем содержимое
      // (именно содержимое, а не саму переменную: на неё могут быть ссылки).
      if (type === 'receipts'){
        receipts.length = 0; receipts.push(doc);
        openReceipt(no);
      } else if (type === 'orders'){
        orders.length = 0; orders.push(doc);
        openOrder(no);
      } else if (typeof transfers !== 'undefined'){
        transfers.length = 0; transfers.push(doc);
        if (typeof openTransfer === 'function') openTransfer(no);
      }
    } catch (e){
      if (typeof notify === 'function') notify('Не удалось открыть документ: ' + e.message);
    }
  }

  /** Клик по строке списка. Тип определяем по тому, какой список сейчас открыт. */
  window.portalOpenDocument = function(no){
    const type = (PORTAL.docs.receipts || []).some(r => r.no === no) ? 'receipts'
               : ((PORTAL.docs.orders || []).some(r => r.no === no) ? 'orders' : 'transfers');
    openDocument(type, no);
  };

  window.portalOpenReceipt = no => openDocument('receipts', no);
  window.portalOpenOrder = no => openDocument('orders', no);
})();
</script>
"""


PATCH_SKU = r"""
<!-- ===== ЭТАП 6: карточка товара, детализация запасов, перемещения =====
     Карточку товара и разбивку остатка по партиям рисует демо-код —
     подкладываем ему реальные данные, как и с карточками документов.
===================================================================== -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const n = v => v === null || v === undefined ? 0 : Number(v);
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /* ---------------- Карточка товара ---------------- */

  /** Строка v_sku_full → объект в терминах демо (карточка читает именно их). */
  function toProduct(s){
    return {
      __api: true,
      code: s.code, name: s.name, unit: s.uomName,
      group: s.itemGroupName, productType: s.itemTypeName,
      barcode: s.barcode, statusProduct: s.statusName,
      length: dash(s.length), width: dash(s.width), height: dash(s.height),
      volume: dash(s.volume), weightGross: dash(s.weightGross), weightNet: dash(s.weightNet),
      hazardClass: dash(s.hazardClassName),
      batchTracking: dash(s.batchTracking), expiryTracking: dash(s.expiryTracking),
      shelfLifeDays: dash(s.shelfLifeDays), abcClass: dash(s.abcClass),
      defaultOrderPackaging: s.orderUnitName,
      // то, что демо раньше придумывало само
      __full: {
        itemCode: dash(s.itemCode),
        itemFullName: dash(s.itemFullName),
        hazardGroup: dash(s.hazardGroupName),
        netWeight: dash(s.weightNet),
        maxStorageTemp: dash(s.maxStorageTemp), minStorageTemp: dash(s.minStorageTemp),
        maxHumidity: dash(s.maxHumidity), minHumidity: dash(s.minHumidity),
        minDaysForReceipt: dash(s.minDaysForReceipt),
        minDaysForReservation: dash(s.minDaysForReservation),
        manufacturer: dash(s.manufacturer), countryOrigin: dash(s.countryOrigin),
        brand: dash(s.brand), color: dash(s.color), size: dash(s.size),
        xyzClass: dash(s.xyzClass), abcClass: dash(s.abcClass),
        guid: dash(s.skuId), hsCode: dash(s.hsCode), enstruCode: dash(s.enstruCode),
        currency: dash(s.currencyCode), cost: dash(s.cost), comment: dash(s.comment),
        isFragile: !!s.isFragile, isSerialTracked: !!s.isSerialTracked,
        isKit: !!s.isKit, isMarkingRequired: !!s.isMarkingRequired,
        isExcise: !!s.isExcise, isControlled: !!s.isControlled,
        isWmsActive: !!s.isWmsActive,
        createdBy: dash(s.createdBy), createdAt: ruDate(s.createdAt),
        updatedBy: dash(s.updatedBy), updatedAt: ruDate(s.updatedAt),
        shelfLifeDays: dash(s.shelfLifeDays),
        // карточка выводит их списком — массив обязателен, иначе падает отрисовка
        customAttrs: Array.from({length: 10}, (_, i) => dash(s['customAttr' + (i + 1)]))
      }
    };
  }

  /** Подкладываем товар в массив, из которого демо его берёт, и открываем карточку. */
  window.portalOpenSku = function(code){
    const row = (PORTAL.catalog.sku || []).find(s => s.code === code);
    if (!row){
      if (typeof notify === 'function') notify('Товар ' + code + ' не найден в загруженном списке');
      return;
    }
    const p = toProduct(row);
    products.length = 0;
    products.push(p);
    if (typeof openSkuItem === 'function') openSkuItem(code);
  };

  /* ---------------- Запасы: карточка товара с партиями ---------------- */

  async function fetchBatches(skuCode){
    const url = '/api/stock/details?skuCode=' + encodeURIComponent(skuCode);
    const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.json();
  }

  /** Клик по строке экрана «Запасы» — карточка остатка с разбивкой по партиям. */
  window.portalOpenStockRow = async function(code){
    const row = (PORTAL.rows || []).find(r => r.code === code);
    const sku = (PORTAL.catalog.sku || []).find(s => s.code === code);
    if (!row){
      if (typeof notify === 'function') notify('Товар ' + code + ' не найден в списке запасов');
      return;
    }
    try {
      const batches = (await fetchBatches(code)).map(b => ({
        q: dash(b.quality),
        batch: dash(b.batchNumber),
        series: dash(b.seriesNumber),
        exp: ruDate(b.expiryDate),
        stock: n(b.quantity),
        reserved: n(b.reservedQty),
        blocked: n(b.blockedQty),
        available: n(b.availableQty),
        blockReason: dash(b.blockReasonName),
        warehouse: b.warehouseName
      }));

      const p = Object.assign(sku ? toProduct(sku) : {code: code, name: row.name, unit: row.unit},
        {
          code: code, name: row.name, unit: row.unit, group: row.group,
          stock: row.stock, reserved: row.reserved, defect: row.defect,
          __batches: batches,
          __stockSummary: {blocked: row.blocked, available: row.available}
        });

      products.length = 0;
      products.push(p);
      if (typeof openProduct === 'function') openProduct(code);
    } catch (e){
      if (typeof notify === 'function') notify('Не удалось получить партии: ' + e.message);
    }
  };

  // Демо генерировало партии само — теперь берём подложенные из API
  if (typeof window.mockBatches === 'function' || true){
    window.mockBatches = p => (p && p.__batches) ? p.__batches : [];
    window.groupBatchRows = rows => rows;
  }
  // и «придуманные» характеристики товара заменяем реальными
  const origFullInfo = window.getProductFullInfo;
  window.getProductFullInfo = function(p){
    if (p && p.__full) return p.__full;
    return origFullInfo ? origFullInfo(p) : {};
  };

  /* Качество в базе называется «Годный», в демо проверялось слово «Годен»;
     причина блокировки приходит как «—», а не пустой строкой. */
  function patchBatchColumns(){
    if (typeof tableColumns === 'undefined' || !tableColumns.stockBatch) return;
    const set = (id, render) => {
      const c = tableColumns.stockBatch.find(c => c.id === id);
      if (c) c.render = render;
    };
    set('q', b => typeof badge === 'function'
      ? badge(b.q === 'Годный' || b.q === 'Годен' ? 'ok' : 'err', b.q)
      : b.q);
    set('blockReason', b => (!b.blockReason || b.blockReason === '—')
      ? '—'
      : (typeof badge === 'function' ? badge('warn', b.blockReason) : b.blockReason));
  }

  /* ---------------- Перемещения ---------------- */

  const BADGE = {draft:'draft', forming:'warn', in_transit:'warn', received:'ok', form_error:'err'};

  function toTransfer(t){
    return {
      no: t.documentNo, erpNo: t.documentNo, wmsNumber: dash(t.docNumber),
      status: BADGE[t.statusCode] || 'warn', statusLabel: t.statusName || '',
      date: ruDate(t.docDate), dateIso: t.docDate,
      fromWarehouse: t.fromWarehouseName, toWarehouse: t.toWarehouseName,
      warehouse: t.fromWarehouseName,
      shipDate: ruDate(t.shipDate), receivedDate: ruDate(t.receivedDate),
      author: dash(t.authorName), trip: dash(t.trip), comment: dash(t.comment),
      lineCount: n(t.lineCount), totalPieces: n(t.totalPieces),
      weight: n(t.weightKg), volume: n(t.volumeM3),
      palletCount: n(t.palletCount), cargoPlaces: n(t.cargoPlaces),
      carrier: dash(t.carrier), carNumber: dash(t.carNumber),
      driverName: dash(t.driverName), driverPhone: dash(t.driverPhone),
      docId: t.docId
    };
  }

  function patchTransferColumns(){
    if (typeof tableColumns === 'undefined' || !tableColumns.transfers) return;
    const set = (id, render) => {
      const c = tableColumns.transfers.find(c => c.id === id);
      if (c) c.render = render;
    };
    set('fromWarehouse', t => t.fromWarehouse);
    set('toWarehouse', t => t.toWarehouse);
    set('warehouse', t => t.fromWarehouse);
    set('volume', t => t.volume);
  }

  async function loadTransfers(){
    try {
      const url = '/api/transfers';
      const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      PORTAL.docs.transfers = (await resp.json()).map(toTransfer);
      PORTAL.docsError.transfers = null;
    } catch (e){
      PORTAL.docs.transfers = [];
      PORTAL.docsError.transfers = e.message;
    }
    renderTransfersFromApi();
  }

  function renderTransfersFromApi(){
    const el = document.getElementById('transfersBody');
    if (!el) return;
    let rows = (PORTAL.docs.transfers || []).slice();

    const noQ = (document.getElementById('transfersSearchNo')?.value || '').trim().toLowerCase();
    const tripQ = (document.getElementById('transfersSearchTrip')?.value || '').trim().toLowerCase();
    const fromISO = document.getElementById('transfersDateFrom')?.value || '';
    const toISO = document.getElementById('transfersDateTo')?.value || '';
    if (noQ) rows = rows.filter(t => (t.no || '').toLowerCase().includes(noQ));
    if (tripQ) rows = rows.filter(t => (t.trip || '').toLowerCase().includes(tripQ));
    if (fromISO) rows = rows.filter(t => (t.dateIso || '') >= fromISO);
    if (toISO) rows = rows.filter(t => (t.dateIso || '') <= toISO);

    if (typeof fcApplySort === 'function') rows = fcApplySort('transfers', rows);
    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage('transfers', rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }
    const counter = document.getElementById('transfersCount');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead('transfers');
    el.innerHTML = items.length
      ? items.map(t => renderTableRow('transfers', t, 'portalOpenDocument')).join('')
      : '<tr><td colspan="24" style="color:var(--muted);">'
        + (PORTAL.docsError.transfers ? 'Данные недоступны' : 'Ничего не найдено') + '</td></tr>';
    if (typeof renderPaginationBar === 'function') renderPaginationBar('transfers', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('transfers'), 0);
  }

  function install(){
    patchBatchColumns();
    patchTransferColumns();
    window.renderTransfers = renderTransfersFromApi;
    PORTAL.onLogin(loadTransfers);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


PATCH_DRAFT = r"""
<!-- ===== ЭТАП 7: черновики документов в интерфейсе прототипа =====
     Интерфейс не меняется: используются карточка документа, её вкладки,
     окно ввода строки с поиском товара и пересчётом упаковки, кнопки
     «Сохранить», «Подтвердить», «Удалить черновик» и окно подтверждения.
     Подключение сделано в точках, где демо работало с данными в памяти:
     теперь те же действия уходят в API.
============================================================== -->
<script>
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
        ? '' : '<option value="" selected>— Не выбран —</option>';
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
      кнопка.title = блок ? 'Сначала устраните: ' + problems.join('; ') : 'Отправить документ в WMS';
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
      warehouse: {id:'rf_warehouse', label:'Склад', value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      supplier:  {id:'rf_supplier', label:'Поставщик', value:'supplier', name:'supplierName',
                  options:() => refs.suppliers}
    });
    оберни('fcOrderSpecialFieldHtml', {
      warehouse: {id:'of_warehouse', label:'Склад', value:'warehouse', name:'warehouseName',
                  options:() => refs.warehouses},
      consignee: {id:'of_consignee', label:'Грузополучатель', value:'consignee', name:'consigneeName',
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
      notify(type === 'receipts' ? 'Изменения по поступлению сохранены' : 'Изменения по заказу сохранены');
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
            ? 'Отправка в WMS не удалась — документ в статусе «Ошибка отправки»'
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
              + ' background:#f6c343; color:#4a3500; font-size:11px;">нет в WMS</span>');
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
      if (typeof notify === 'function') notify('Транспортная информация передана в WMS');
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
    if (typeof notify === 'function') notify('Для перемещений транспорт пока не подключён');
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
</script>
"""


PATCH_AUTH = r"""
<!-- ================== ЭТАП 5: вход в портал по логину и паролю ==================
     Демо-вход (список пользователей на экране авторизации) убран полностью.
     Теперь форма входа работает по-настоящему: логин и пароль проверяет сервер,
     он же помнит, кто вошёл, — в адресе страницы и в заголовках запросов
     ничего не передаётся, подставить чужого поклажедателя из браузера нельзя.
=============================================================================== -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

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

  /* --------------------------------------------------------------------------
     1. Сеанс кончился посреди работы
     -------------------------------------------------------------------------- */

  // Любой запрос к API может вернуться с 401 — например, если истёк срок
  // сессии. Чтобы это не выглядело как «данные пропали», перехватываем ответ
  // один раз в одном месте и возвращаем человека на экран входа.
  const штатныйFetch = window.fetch.bind(window);
  let сеансПотерян = false;
  window.fetch = async function(url, opts){
    const resp = await штатныйFetch(url, opts);
    const адрес = String(typeof url === 'string' ? url : (url && url.url) || '');
    if (resp.status === 401 && адрес.indexOf('/api/') === 0
        && адрес.indexOf('/api/auth/') !== 0 && !сеансПотерян){
      сеансПотерян = true;
      показатьВход('Сеанс завершён — войдите заново');
    }
    return resp;
  };
  /* --------------------------------------------------------------------------
     2. Экран входа
     -------------------------------------------------------------------------- */

  function поле(id){ return document.getElementById(id); }

  function ошибка(текст){
    const el = поле('portalLoginError');
    if (!el) return;
    el.textContent = текст || '';
    el.style.display = текст ? '' : 'none';
  }

  /** Включает штатную форму демо (в прототипе она была заблокирована). */
  function включитьФормуВхода(){
    const card = document.querySelector('#loginScreen .login-card');
    if (!card) return;

    ['loginEmail', 'loginPassword'].forEach(id => {
      const el = поле(id);
      if (!el) return;
      el.disabled = false;
      el.addEventListener('keydown', e => { if (e.key === 'Enter') window.doLogin(); });
    });
    const email = поле('loginEmail');
    if (email) email.placeholder = 'client@roga-kopyta.kz';

    const btn = card.querySelector('button.btn.primary');
    if (btn){
      btn.disabled = false;
      btn.style.opacity = '';
      btn.style.cursor = 'pointer';
      btn.removeAttribute('title');
      btn.onclick = () => window.doLogin();
    }

    // убираем всё, что относилось к демо-доступу
    card.querySelectorAll('.hint').forEach(el => el.remove());
    const divider = card.querySelector('.login-divider');
    if (divider) divider.remove();
    const list = поле('demoUserList');
    if (list) list.remove();
    document.querySelectorAll('.demo-flag').forEach(el => el.remove());

    if (!поле('portalLoginError')){
      const err = document.createElement('div');
      err.id = 'portalLoginError';
      err.style.cssText = 'display:none;margin-top:10px;padding:8px 10px;border-radius:6px;'
        + 'background:#fdecea;color:#c0392b;font-size:13px;text-align:center;';
      card.appendChild(err);
    }
  }

  function показатьВход(текст){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = null;
    PORTAL.user = null;
    PORTAL.depositorCode = '';
    const shell = поле('appShell');
    if (shell) shell.style.display = 'none';
    поле('loginScreen')?.classList.add('active');
    const pwd = поле('loginPassword');
    if (pwd) pwd.value = '';
    ошибка(текст);
  }

  /* --------------------------------------------------------------------------
     3. Вход и выход
     -------------------------------------------------------------------------- */

  window.doLogin = async function(){
    const login = (поле('loginEmail')?.value || '').trim();
    const password = поле('loginPassword')?.value || '';
    if (!login || !password){ ошибка('Введите логин и пароль'); return; }
    ошибка('');
    try {
      const сеанс = await api('POST', '/api/auth/login', {login: login, password: password});
      await войтиВПортал(сеанс);
    } catch (e){
      ошибка(e.message || 'Не удалось войти');
    }
  };

  // Демо-вход выбором пользователя из списка больше не существует.
  window.quickLogin = function(){};
  window.renderDemoUsers = function(){};

  window.logout = async function(){
    try { await api('POST', '/api/auth/logout'); } catch (e) {}
    сеансПотерян = false;
    const email = поле('loginEmail');
    if (email) email.value = '';
    показатьВход('');
  };

  /** Собирает объект пользователя в том виде, в каком его ждёт интерфейс демо. */
  function пользовательДляИнтерфейса(сеанс, me){
    const скрытые = [];
    if (me.hideTransfersTab) скрытые.push('transfers');
    if (me.hideBillingTab) скрытые.push('billing');
    return {
      role: сеанс.roleCode,
      login: сеанс.login,
      full_name: сеанс.fullName,
      position: me.position || '—',
      organization: me.organization || '—',
      phone: me.phone || '',
      lastLogin: me.lastLogin || '',
      manager: подписьРежима(сеанс),
      hiddenTabs: скрытые,
      hideServicesTab: me.hideServicesTab === true,
      canCreateReceipts: me.canCreateReceipts !== false,
      canCreateOrders: me.canCreateOrders !== false,
      canCreateTransfers: me.canCreateTransfers !== false,
      canUploadSkuExcel: me.canUploadSkuExcel !== false,
      canUploadPackagingExcel: me.canUploadSkuExcel !== false,
      defaultDepositor: сеанс.depositorId
    };
  }

  function подписьРежима(сеанс){
    const имена = (сеанс.depositors || []).map(d => d.orgName).join(', ');
    switch (сеанс.roleCode){
      case 'manager':   return 'Режим менеджера: доступны поклажедатели — ' + (имена || 'нет');
      case 'admin':     return 'Режим администратора: управление пользователями и доступами';
      case 'developer': return 'Режим разработчика: настройка структуры документов и полей';
      case 'staff':     return 'Режим кладовщика: доступны склады, на которые выдан доступ';
      default:          return 'Организация: ' + (имена || '—');
    }
  }

  async function войтиВПортал(сеанс){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = сеанс;
    const me = await api('GET', '/api/me');
    PORTAL.user = me;

    const user = пользовательДляИнтерфейса(сеанс, me);
    currentUser = user;
    currentUserRole = user.role;

    сеансПотерян = false;
    поле('loginScreen')?.classList.remove('active');
    const shell = поле('appShell');
    if (shell) shell.style.display = '';
    ошибка('');
    const pwd = поле('loginPassword');
    if (pwd) pwd.value = '';

    if (typeof applyUserTabVisibility === 'function') applyUserTabVisibility(user);
    if (typeof applyUserCreatePermissions === 'function') applyUserCreatePermissions(user);

    const админ = user.role === 'admin' || user.role === 'developer';
    const показать = (id, видно) => { const el = поле(id); if (el) el.style.display = видно ? '' : 'none'; };
    показать('adminTabBtn', админ);
    показать('devSettingsTabBtn', user.role === 'developer');
    ['refSubBtnOrderTypes','refSubBtnDocTypes','refSubBtnDepositorsDir',
     'refSubBtnWarehousesDir','refSubBtnUsers'].forEach(id => показать(id, админ));

    const managerLine = поле('managerLine');
    if (managerLine) managerLine.textContent = user.manager;
    if (typeof renderProfile === 'function') renderProfile(user);

    применитьПоклажедателя();

    lastMainView = 'orders';
    if (typeof showTab === 'function') showTab('orders');

    // §6.2: пароль выдал администратор — сначала свой пароль, данных пока нет
    if (сеанс.mustChangePassword && typeof window.portalOpenPasswordChange === 'function'){
      window.portalOpenPasswordChange('forced');
      return;
    }

    // §6.4: если доступен не один поклажедатель и выбор не сделан — сначала выбор
    if (сеанс.needsDepositorChoice){
      поле('depositorSwitcher')?.classList.add('active');
      if (typeof notify === 'function') notify('Выберите поклажедателя, с которым работаете');
    } else {
      PORTAL.reloadAll();
      if (typeof notify === 'function') notify('Вы вошли как ' + user.full_name);
    }
  }

  /* --------------------------------------------------------------------------
     4. Переключатель поклажедателей
     -------------------------------------------------------------------------- */

  function текущийПоклажедатель(){
    const s = PORTAL.session;
    if (!s || !s.depositorId) return null;
    return (s.depositors || []).find(d => d.depositorId === s.depositorId) || null;
  }

  /** Заголовок с названием организации и состояние переключателя. */
  function применитьПоклажедателя(){
    const s = PORTAL.session || {depositors: []};
    const текущий = текущийПоклажедатель();
    PORTAL.depositorCode = текущий ? текущий.code : '';
    PORTAL.depositorName = текущий ? текущий.orgName : '';

    const org = поле('tabsOrg');
    if (org){
      org.textContent = текущий ? текущий.orgName : 'Выберите поклажедателя';
      org.classList.toggle('switchable', (s.depositors || []).length > 1);
    }
    window.renderDepositorSwitcher();
  }

  window.renderDepositorSwitcher = function(){
    const el = поле('depositorSwitcher');
    if (!el) return;
    const s = PORTAL.session || {depositors: []};
    const выбран = s.depositorId;
    el.innerHTML = '<div class="ks-title">Переключить поклажедателя</div>'
      + (s.depositors || []).map(d =>
          '<div class="ks-item ' + (d.depositorId === выбран ? 'current' : '') + '"'
          + ' onclick="portalSelectDepositor(\'' + d.depositorId + '\')">'
          + '<span>' + d.orgName + '</span>'
          + (d.depositorId === выбран ? '<span>✓</span>' : '') + '</div>').join('');
  };

  window.toggleDepositorSwitcher = function(){
    const s = PORTAL.session;
    if (!s || (s.depositors || []).length <= 1) return;
    window.renderDepositorSwitcher();
    поле('depositorSwitcher')?.classList.toggle('active');
  };

  window.portalSelectDepositor = async function(id){
    try {
      const сеанс = await api('POST', '/api/auth/depositor', {depositorId: id});
      if (PORTAL.forgetAll) PORTAL.forgetAll();
      PORTAL.session = сеанс;
      поле('depositorSwitcher')?.classList.remove('active');
      применитьПоклажедателя();
      PORTAL.reloadAll();
      if (typeof notify === 'function') notify('Выбран поклажедатель: ' + PORTAL.depositorName);
    } catch (e){
      if (typeof notify === 'function') notify('Не удалось переключить: ' + e.message);
    }
  };
  // в прототипе у кладовщика был свой переключатель — теперь он общий
  window.selectDepositor = window.portalSelectDepositor;
  window.setStaffDepositorFilter = window.portalSelectDepositor;

  /* --------------------------------------------------------------------------
     5. Старт страницы
     -------------------------------------------------------------------------- */

  async function install(){
    включитьФормуВхода();
    // Обновление страницы не должно выбрасывать из портала: если сессия
    // ещё жива, сервер её узнает и экран входа даже не мелькнёт.
    try {
      const сеанс = await api('GET', '/api/auth/session');
      await войтиВПортал(сеанс);
    } catch (e){
      показатьВход('');
    }
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


PATCH_ADMIN = r"""
<!-- ============== ЭТАП 5б: смена пароля и справочник «Пользователи» ==============
     Используется то, что уже есть в прототипе: кнопка «Сменить пароль» в личном
     кабинете, справочник «Пользователи» в режиме администрирования и карточка
     пользователя. Окно смены пароля сделано в стиле остальных окон портала.
================================================================================ -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

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

  const экран = id => document.getElementById(id);
  const экранировать = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  /* ======================================================================
     1. Окно смены пароля
     ====================================================================== */

  // Два режима: обычная смена из личного кабинета и обязательная — когда
  // пароль выдал администратор. В обязательном режиме окно не закрывается:
  // до смены пароля сервер всё равно не отдаёт данных.
  let обязательная = false;

  function создатьОкно(){
    if (экран('pwOverlay')) return;
    const окно = document.createElement('div');
    окно.className = 'col-overlay';
    окно.id = 'pwOverlay';
    окно.style.cssText = 'align-items:center;justify-content:center;';
    окно.onclick = e => { if (e.target === окно && !обязательная) закрыть(); };
    окно.innerHTML = ''
      + '<div class="col-panel" id="pwPanel" style="width:460px;box-shadow:0 8px 32px rgba(0,0,0,.24);'
      + 'border-radius:10px;">'
      + '  <h3 id="pwTitle" style="font-size:17px;">Смена пароля</h3>'
      + '  <div class="hint" id="pwHint">Придумайте пароль, который отвечает требованиям ниже.</div>'
      + '  <div class="kv" style="margin-top:12px;"><label>Текущий пароль</label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwCurrent" autocomplete="current-password"></div>'
      + '  <div class="kv" style="margin-top:12px;"><label>Новый пароль</label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwNew" autocomplete="new-password"></div>'
      + '  <div class="kv" style="margin-top:12px;"><label>Новый пароль ещё раз</label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwRepeat" autocomplete="new-password"></div>'
      + '  <div id="pwRules" style="margin-top:12px;padding:10px 12px;background:var(--bg);'
      + 'border-radius:6px;font-size:12.5px;line-height:1.9;">'
      + '    <div style="color:var(--muted);margin-bottom:2px;">Требования к паролю:</div>'
      + '    <div data-pw-rule="length"><span class="pw-mark">○</span> не короче 8 символов</div>'
      + '    <div data-pw-rule="case"><span class="pw-mark">○</span> латинские буквы: '
      + 'и заглавные, и строчные</div>'
      + '    <div data-pw-rule="extra"><span class="pw-mark">○</span> цифра или знак '
      + '(например - _ . ! #)</div>'
      + '  </div>'
      + '  <div id="pwError" style="display:none;margin-top:12px;padding:8px 10px;border-radius:6px;'
      + 'background:#fdecea;color:#c0392b;font-size:13px;"></div>'
      + '  <div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
      + '    <button class="btn primary" id="pwSave">Сменить пароль</button>'
      + '    <button class="btn ghost" id="pwCancel">Отмена</button>'
      + '  </div>'
      + '</div>';
    document.body.appendChild(окно);
    экран('pwSave').onclick = сохранить;
    экран('pwCancel').onclick = закрыть;
    ['pwCurrent','pwNew','pwRepeat'].forEach(id => {
      экран(id).addEventListener('keydown', e => { if (e.key === 'Enter') сохранить(); });
    });
    экран('pwNew').addEventListener('input', отметитьТребования);
    отметитьТребования();
  }

  /**
   * Те же правила, что и на сервере, — здесь только чтобы человек видел,
   * чего не хватает, ещё до нажатия кнопки. Решение всё равно за сервером.
   */
  function выполненные(пароль){
    const цифраИлиЗнак = /[0-9]/.test(пароль) || /[^0-9A-Za-zА-Яа-яЁё]/.test(пароль);
    return {
      length: пароль.length >= 8,
      case: /[a-z]/.test(пароль) && /[A-Z]/.test(пароль),
      extra: цифраИлиЗнак
    };
  }

  function отметитьТребования(){
    const поле = экран('pwNew');
    const пароль = поле ? поле.value : '';
    const готово = выполненные(пароль);
    const список = экран('pwRules');
    if (!список) return;
    список.querySelectorAll('[data-pw-rule]').forEach(строка => {
      const ok = готово[строка.getAttribute('data-pw-rule')] === true;
      строка.style.color = пароль && ok ? '#1e7a3c' : 'inherit';
      const метка = строка.querySelector('.pw-mark');
      if (метка) метка.textContent = пароль && ok ? '✓' : '○';
    });
  }

  function ошибка(текст){
    const el = экран('pwError');
    if (!el) return;
    el.textContent = текст || '';
    el.style.display = текст ? '' : 'none';
  }

  function закрыть(){
    if (обязательная) return;
    экран('pwOverlay')?.classList.remove('active');
    ['pwCurrent','pwNew','pwRepeat'].forEach(id => { const el = экран(id); if (el) el.value = ''; });
    ошибка('');
  }

  /** Открыть окно. principal === 'forced' — обязательная смена после входа. */
  window.portalOpenPasswordChange = function(режим){
    создатьОкно();
    обязательная = режим === 'forced';
    экран('pwTitle').textContent = обязательная ? 'Задайте свой пароль' : 'Смена пароля';
    экран('pwHint').textContent = обязательная
      ? 'Пароль выдал администратор портала — работать можно только со своим. '
        + 'Введите выданный пароль и придумайте новый по требованиям ниже.'
      : 'Придумайте пароль, который отвечает требованиям ниже.';
    экран('pwCancel').style.display = обязательная ? 'none' : '';
    ошибка('');
    экран('pwOverlay').classList.add('active');
    setTimeout(() => экран('pwCurrent').focus(), 50);
  };

  async function сохранить(){
    const кнопка = экран('pwSave');
    if (кнопка.disabled) return;          // второй клик, пока идёт первый запрос
    const текущий = экран('pwCurrent').value;
    const новый = экран('pwNew').value;
    const повтор = экран('pwRepeat').value;
    if (!текущий || !новый){ ошибка('Заполните все поля'); return; }
    if (новый !== повтор){ ошибка('Новый пароль и его повтор не совпадают'); return; }
    // Проверка пароля на сервере по своей природе небыстрая — хеш считается
    // медленно намеренно. Показываем, что запрос идёт, и не даём кликать дважды.
    const подпись = кнопка.textContent;
    кнопка.disabled = true;
    кнопка.textContent = 'Проверяем…';
    ошибка('');
    try {
      await api('POST', '/api/me/password', {currentPassword: текущий, newPassword: новый});
      const былаОбязательная = обязательная;
      обязательная = false;
      закрыть();
      if (typeof notify === 'function') notify('Пароль изменён');
      if (былаОбязательная){
        // до смены пароля данные не загружались — делаем это теперь
        if (PORTAL.session) PORTAL.session.mustChangePassword = false;
        PORTAL.reloadAll();
      }
    } catch (e){
      // единственная причина отказа 403 на этом адресе — не тот текущий пароль
      ошибка(e.status === 403 ? 'Текущий пароль указан неверно' : e.message);
    } finally {
      кнопка.disabled = false;
      кнопка.textContent = подпись;
    }
  }

  /** Кнопка «Сменить пароль» в личном кабинете — та же, что была в прототипе. */
  function подключитьКнопкуВКабинете(){
    const штатный = window.renderProfile;
    if (typeof штатный !== 'function') return;
    window.renderProfile = function(user){
      штатный(user);
      const host = экран('profileContent');
      if (!host) return;
      [...host.querySelectorAll('button')]
        .filter(b => b.textContent.trim().indexOf('Сменить пароль') === 0)
        .forEach(b => { b.onclick = () => window.portalOpenPasswordChange(); });
    };
  }

  /* ======================================================================
     2. Справочник «Пользователи» из базы портала
     ====================================================================== */

  const админ = {пользователи: [], роли: [], поклажедатели: [], ошибка: null};

  async function загрузитьПользователей(){
    try {
      const [список, роли, поклажедатели] = await Promise.all([
        api('GET', '/api/admin/users'),
        api('GET', '/api/admin/roles'),
        api('GET', '/api/admin/depositors')
      ]);
      админ.пользователи = список;
      админ.роли = роли;
      админ.поклажедатели = поклажедатели;
      админ.ошибка = null;
    } catch (e){
      админ.пользователи = [];
      // 403 — обычное дело: раздел видит только администратор
      админ.ошибка = e.status === 403 ? null : e.message;
    }
    отрисоватьСписок();
  }

  function отрисоватьСписок(){
    const body = экран('adminUsersBody');
    if (!body) return;
    if (админ.ошибка){
      body.innerHTML = '<tr><td colspan="6" style="color:#c0392b;">Не удалось получить список: '
        + экранировать(админ.ошибка) + '</td></tr>';
      return;
    }
    if (!админ.пользователи.length){
      body.innerHTML = '<tr><td colspan="6" style="color:var(--muted);">Нет данных</td></tr>';
      return;
    }
    body.innerHTML = админ.пользователи.map(u => {
      const статус = u.isActive
        ? '<span class="badge ok"><span class="dot"></span>Активен</span>'
        : '<span class="badge err"><span class="dot"></span>Заблокирован</span>';
      const пароль = u.mustChangePassword
        ? ' <span class="badge warn" title="Пароль выдан администратором и будет сменён при входе">'
          + '<span class="dot"></span>Сменит пароль</span>'
        : '';
      return '<tr onclick="portalOpenAdminUser(\'' + u.userId + '\')">'
        + '<td><strong>' + экранировать(u.fullName) + '</strong></td>'
        + '<td>' + экранировать(u.roleName || u.roleCode) + '</td>'
        + '<td>' + экранировать(u.scope) + '</td>'
        + '<td>' + экранировать(u.login) + '</td>'
        + '<td>' + экранировать(u.depositorCodes || '—') + '</td>'
        + '<td>' + статус + пароль + '</td>'
        + '</tr>';
    }).join('');
  }

  const галочка = (id, подпись, включено) =>
    '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;">'
    + '<input type="checkbox" id="' + id + '"' + (включено ? ' checked' : '') + '> ' + подпись + '</label>';

  const строкаДанных = (подпись, значение) =>
    '<div class="kv"><label>' + подпись + '</label><div>' + экранировать(значение || '—') + '</div></div>';

  const полеВвода = (id, подпись, значение, подсказка) =>
    '<div class="kv"><label>' + подпись + '</label><div>'
    + '<input type="text" class="kv-input kv-input-editable" id="' + id + '"'
    + ' value="' + экранировать(значение || '') + '"'
    + (подсказка ? ' placeholder="' + экранировать(подсказка) + '"' : '') + '></div></div>';

  const полеРоли = (выбрана) =>
    '<div class="kv"><label>Роль на портале</label><div>'
    + '<select class="kv-input kv-input-editable" id="auRole">'
    + админ.роли.map(r => '<option value="' + r.code + '"'
        + (r.code === выбрана ? ' selected' : '') + '>' + экранировать(r.name) + '</option>').join('')
    + '</select></div></div>';

  /**
   * Галочки доступных поклажедателей. Для складского сотрудника их не
   * показываем: ему доступ выдаётся по складам, это отдельный экран.
   */
  function блокДоступов(выданные){
    const выбрано = new Set(выданные || []);
    return '<div class="section-title">Доступные поклажедатели</div>'
      + '<div class="table-card" style="padding:18px 20px;" id="auDepositors">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Поклажедателю обычно отмечают '
      + 'одного — свою организацию. Менеджеру — тех клиентов, с которыми он работает.</div>'
      + (админ.поклажедатели.length
          ? админ.поклажедатели.map(d =>
              '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
              + 'padding:8px 0;border-bottom:1px solid var(--line);">'
              + '<input type="checkbox" data-depositor-id="' + d.depositorId + '"'
              + (выбрано.has(d.depositorId) ? ' checked' : '') + '> '
              + экранировать(d.orgName) + ' <span style="color:var(--muted);">· '
              + экранировать(d.code) + '</span></label>').join('')
          : '<div style="color:var(--muted);font-size:13.5px;">Поклажедатели ещё не заведены</div>')
      + '</div>';
  }

  function отмеченныеПоклажедатели(){
    return [...document.querySelectorAll('#auDepositors input[data-depositor-id]:checked')]
      .map(cb => cb.getAttribute('data-depositor-id'));
  }

  /**
   * Карточка пользователя. Одна и та же форма работает на правку и на
   * создание: при создании `userId` не передан, доступен ввод логина
   * и пароля, а блоки «последний вход» и «пароль» выглядят иначе.
   */
  window.portalOpenAdminUser = async function(userId){
    const u = userId ? админ.пользователи.find(x => x.userId === userId) : null;
    if (userId && !u) return;
    const новый = !u;
    const el = экран('adminUserEditContent');
    if (!el) return;
    const свой = !новый && PORTAL.session && PORTAL.session.login === u.login;
    const толькоЧтение = currentUserRole === 'developer';

    // Какие поклажедатели уже выданы — спрашиваем отдельно, в списке их нет
    let выданные = [];
    if (!новый){
      try { выданные = await api('GET', '/api/admin/users/' + encodeURIComponent(u.userId) + '/depositors'); }
      catch (e){ выданные = []; }
    }

    el.innerHTML = ''
      + '<div class="detail-head">'
      + '  <div><h2>' + (новый ? 'Новый пользователь' : экранировать(u.fullName)) + '</h2>'
      + '    <div class="meta">' + (новый
            ? 'Заполните данные и выдайте пароль — при первом входе портал попросит сменить его'
            : экранировать(u.userPosition || '—') + ' · ' + экранировать(u.organization || '—')) + '</div></div>'
      + (новый ? '' : ''
      + '  <div><label style="display:flex;align-items:center;gap:8px;font-size:13.5px;cursor:pointer;">'
      + '    <input type="checkbox" id="userIsActive"' + (u.isActive ? ' checked' : '')
      +      (свой ? ' disabled title="Свою учётную запись заблокировать нельзя"' : '') + '>'
      + '    <span class="badge ' + (u.isActive ? 'ok' : 'err') + '" id="userIsActiveBadge">'
      + '<span class="dot"></span>' + (u.isActive ? 'Активен' : 'Заблокирован') + '</span>'
      + '  </label></div>')
      + '</div>'

      + '<div class="section-title" style="margin-top:0;">Данные пользователя</div>'
      + '<div class="detail-head"><div class="kv-grid" '
      + 'style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">'
      + полеВвода('auFullName', 'ФИО', новый ? '' : u.fullName, 'Иванов Иван Иванович')
      + полеВвода('auLogin', 'Email / логин', новый ? '' : u.login, 'i.ivanov@company.kz')
      + полеВвода('auPosition', 'Должность', новый ? '' : u.userPosition, 'Логист')
      + полеВвода('auOrganization', 'Организация', новый ? '' : u.organization, 'ТОО «Рога и Копыта»')
      + полеВвода('auPhone', 'Телефон', новый ? '' : u.phone, '+7 701 000 00 00')
      + полеРоли(новый ? 'depositor' : u.roleCode)
      + (новый ? '' : строкаДанных('Последний вход',
            u.lastLoginAt ? ruДатаВремя(u.lastLoginAt) : 'ни разу'))
      + (новый ? '' : строкаДанных('Пароль задан',
            u.passwordChangedAt ? ruДатаВремя(u.passwordChangedAt) : '—'))
      + '</div></div>'
      + (свой ? '<div class="hint" style="margin:-6px 0 14px;">Это ваша учётная запись: '
          + 'роль и блокировку для себя менять нельзя.</div>' : '')

      + блокДоступов(выданные)

      + '<div class="section-title">Пароль</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div style="font-size:13.5px;color:var(--muted);margin-bottom:12px;">'
      + (новый
          ? 'Придумайте пароль или оставьте поле пустым — портал сгенерирует его сам и покажет.'
          : 'Администратор не видит пароль пользователя. Можно выдать новый — портал '
            + 'потребует сменить его при первом входе.') + '</div>'
      + '  <div style="display:flex;gap:10px;align-items:flex-end;flex-wrap:wrap;">'
      + '    <div class="kv" style="margin:0;min-width:260px;"><label>'
      + (новый ? 'Пароль' : 'Новый пароль') + '</label>'
      + '      <input type="text" class="kv-input kv-input-editable" id="adminNewPassword" '
      + 'placeholder="латинские буквы разного регистра + цифра или знак"></div>'
      + '    <button class="btn" id="adminGenPassword" type="button">Придумать</button>'
      + (новый ? '' : '    <button class="btn primary" id="adminSetPassword" type="button">'
          + 'Задать пароль</button>')
      + '  </div>'
      + '  <div id="adminPasswordMsg" style="display:none;margin-top:12px;font-size:13px;"></div>'
      + '</div>'

      + (новый ? '' : ''
      + '<div class="section-title">Видимость пунктов меню</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + галочка('menuVisServicesTab', 'Показывать вкладку «Услуги» в документах', !u.hideServicesTab)
      + галочка('menuVisBilling', 'Показывать раздел «Биллинг»', !u.hideBillingTab)
      + галочка('menuVisTransfers', 'Показывать раздел «Перемещения»', !u.hideTransfersTab)
      + галочка('menuVisCreateReceipts', 'Разрешить создание Поступлений', u.canCreateReceipts)
      + галочка('menuVisCreateOrders', 'Разрешить создание заказов на отгрузку', u.canCreateOrders)
      + галочка('menuVisCreateTransfers', 'Разрешить создание Перемещений', u.canCreateTransfers)
      + галочка('menuVisUploadSku', 'Разрешить загрузку номенклатуры из Excel', u.canUploadSkuExcel)
      + галочка('menuVisUploadPackaging', 'Разрешить загрузку упаковок из Excel', u.canUploadPackagingExcel)
      + галочка('menuVisEditTransport', 'Разрешить правку транспортного раздела', u.canEditTransport)
      + '</div>')

      + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
      + '  <button class="btn primary" id="adminSaveUser" type="button">'
      + (новый ? 'Создать пользователя' : 'Сохранить') + '</button>'
      + '  <button class="btn ghost" id="adminCancelUser" type="button">Отмена</button>'
      + '</div>';

    if (!новый){
      const бейдж = экран('userIsActiveBadge');
      экран('userIsActive').onchange = function(){
        бейдж.className = 'badge ' + (this.checked ? 'ok' : 'err');
        бейдж.innerHTML = '<span class="dot"></span>' + (this.checked ? 'Активен' : 'Заблокирован');
      };
      экран('adminSetPassword').onclick = () => задатьПароль(u);
      // роль своей учётной записи не меняем — иначе можно потерять доступ
      if (свой) экран('auRole').disabled = true;
    }
    экран('adminGenPassword').onclick = () => { экран('adminNewPassword').value = придумать(); };
    экран('adminSaveUser').onclick = () => новый ? создатьПользователя() : сохранитьКарточку(u);
    экран('adminCancelUser').onclick = () => { showTab('references'); showReferencesSub('users'); };

    if (толькоЧтение){
      el.insertAdjacentHTML('afterbegin', '<div style="background:#eef2ff;border:1px solid #c7d2fe;'
        + 'color:#3730a3;border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px;">'
        + '👁 Режим просмотра — менять пользователей вправе только администратор.</div>');
      el.querySelectorAll('input, select, textarea').forEach(e => e.disabled = true);
      ['adminSetPassword','adminSaveUser','adminGenPassword'].forEach(id => {
        const b = экран(id);
        if (b) b.style.display = 'none';
      });
    }
    showView('admin-user-edit');
  };

  function ruДатаВремя(iso){
    const d = new Date(iso);
    return isNaN(d) ? String(iso)
      : d.toLocaleString('ru-RU', {day:'2-digit', month:'2-digit', year:'numeric',
                                   hour:'2-digit', minute:'2-digit'});
  }

  /** Пароль из слогов и цифр: читается вслух и переносится без ошибок. */
  function придумать(){
    const согласные = 'бвгдкмнпрстфх', гласные = 'аеиоуя';
    let слово = '';
    for (let i = 0; i < 4; i++){
      слово += согласные[Math.floor(Math.random() * согласные.length)]
             + гласные[Math.floor(Math.random() * гласные.length)];
    }
    const латиница = 'kmnprst', гл = 'aeiou';
    let вторая = '';
    for (let i = 0; i < 3; i++){
      вторая += латиница[Math.floor(Math.random() * латиница.length)]
              + гл[Math.floor(Math.random() * гл.length)];
    }
    return вторая.charAt(0).toUpperCase() + вторая.slice(1) + '-'
      + Math.floor(1000 + Math.random() * 9000);   // Kinuta-4100: регистр, знак, цифры
  }

  function сообщение(текст, успех){
    const el = экран('adminPasswordMsg');
    if (!el) return;
    el.textContent = текст;
    el.style.color = успех ? '#1e7a3c' : '#c0392b';
    el.style.display = текст ? '' : 'none';
  }

  async function задатьПароль(u){
    const пароль = (экран('adminNewPassword').value || '').trim();
    if (!пароль){ сообщение('Введите или придумайте пароль', false); return; }
    try {
      await api('POST', '/api/admin/users/' + encodeURIComponent(u.userId) + '/password',
                {newPassword: пароль});
      сообщение('Пароль задан: ' + пароль + ' — передайте его пользователю, при первом входе '
        + 'портал попросит сменить', true);
      await загрузитьПользователей();
    } catch (e){
      сообщение(e.message, false);
    }
  }

  const значение = id => (экран(id)?.value || '').trim();

  function анкета(){
    return {
      login: значение('auLogin'),
      fullName: значение('auFullName'),
      roleCode: экран('auRole') ? экран('auRole').value : 'depositor',
      position: значение('auPosition'),
      organization: значение('auOrganization'),
      phone: значение('auPhone'),
      depositorIds: отмеченныеПоклажедатели()
    };
  }

  async function создатьПользователя(){
    const кнопка = экран('adminSaveUser');
    if (кнопка.disabled) return;
    const данные = анкета();
    if (!данные.login || !данные.fullName){
      сообщение('Заполните ФИО и логин', false);
      return;
    }
    данные.password = (экран('adminNewPassword').value || '').trim();
    кнопка.disabled = true;
    const подпись = кнопка.textContent;
    кнопка.textContent = 'Создаём…';
    try {
      const ответ = await api('POST', '/api/admin/users', данные);
      await загрузитьПользователей();
      if (typeof notify === 'function') notify('Пользователь создан: ' + данные.fullName);
      // открываем уже созданного — там же задаётся видимость разделов,
      // и пароль показываем в его карточке: записать его можно только сейчас
      if (ответ.userId) await window.portalOpenAdminUser(ответ.userId);
      сообщение('Пароль: ' + ответ.password + ' — передайте его пользователю, при первом '
        + 'входе портал попросит сменить', true);
    } catch (e){
      сообщение(e.message, false);
    } finally {
      if (экран('adminSaveUser')){
        экран('adminSaveUser').disabled = false;
        экран('adminSaveUser').textContent = подпись;
      }
    }
  }

  async function сохранитьКарточку(u){
    const вкл = id => экран(id)?.checked === true;
    const кнопка = экран('adminSaveUser');
    if (кнопка.disabled) return;
    кнопка.disabled = true;
    const подпись = кнопка.textContent;
    кнопка.textContent = 'Сохраняем…';
    try {
      // сначала анкета, роль и доступы, затем видимость разделов
      await api('PUT', '/api/admin/users/' + encodeURIComponent(u.userId) + '/profile', анкета());
      админ.пользователи = await api('PUT', '/api/admin/users/' + encodeURIComponent(u.userId), {
        isActive: вкл('userIsActive'),
        hideServicesTab: !вкл('menuVisServicesTab'),
        hideBillingTab: !вкл('menuVisBilling'),
        hideTransfersTab: !вкл('menuVisTransfers'),
        canCreateReceipts: вкл('menuVisCreateReceipts'),
        canCreateOrders: вкл('menuVisCreateOrders'),
        canCreateTransfers: вкл('menuVisCreateTransfers'),
        canUploadSkuExcel: вкл('menuVisUploadSku'),
        canUploadPackagingExcel: вкл('menuVisUploadPackaging'),
        canEditTransport: вкл('menuVisEditTransport')
      });
      отрисоватьСписок();
      if (typeof notify === 'function') notify('Изменения сохранены: ' + u.fullName);
      // если администратор правил себя — применяем настройки сразу
      if (PORTAL.session && PORTAL.session.login === u.login){
        const me = await api('GET', '/api/me');
        PORTAL.user = me;
        if (typeof currentUser !== 'undefined' && currentUser){
          currentUser.hiddenTabs = [];
          if (me.hideTransfersTab) currentUser.hiddenTabs.push('transfers');
          if (me.hideBillingTab) currentUser.hiddenTabs.push('billing');
          currentUser.hideServicesTab = me.hideServicesTab === true;
          currentUser.canCreateReceipts = me.canCreateReceipts !== false;
          currentUser.canCreateOrders = me.canCreateOrders !== false;
          currentUser.canCreateTransfers = me.canCreateTransfers !== false;
          currentUser.canUploadSkuExcel = me.canUploadSkuExcel !== false;
          currentUser.canUploadPackagingExcel = me.canUploadSkuExcel !== false;
          applyUserTabVisibility(currentUser);
          applyUserCreatePermissions(currentUser);
        }
      }
      showTab('references');
      showReferencesSub('users');
    } catch (e){
      сообщение(e.message, false);
      if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
    } finally {
      if (экран('adminSaveUser')){
        экран('adminSaveUser').disabled = false;
        экран('adminSaveUser').textContent = подпись;
      }
    }
  }

  /* ======================================================================
     3. Установка
     ====================================================================== */

  function install(){
    подключитьКнопкуВКабинете();
    window.renderAdminUsers = отрисоватьСписок;
    const создать = document.querySelector('#usersRefActions .btn.primary');
    if (создать) создать.onclick = () => window.portalOpenAdminUser(null);
    PORTAL.onLogin(загрузитьПользователей);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


PATCH_ACCESS = r"""
<!-- ============== ЭТАП 5в: экраны доступов режима администрирования ==============
     Три экрана прототипа начинают работать от базы портала:
       «Доступы»              — какие поклажедатели доступны менеджеру,
       «Доступы сотрудников»  — на каких складах работает кладовщик,
       «Склады поклажедателей»— где поклажедатель хранит товар.
     Все три открываются на том же экране карточки, что и пользователь.
================================================================================ -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

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

  const экран = id => document.getElementById(id);
  const экранировать = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  const данные = {пользователи: [], поклажедатели: [], склады: [], доступыПоклажедателей: []};

  async function загрузить(){
    try {
      const [пользователи, поклажедатели, склады, доступы] = await Promise.all([
        api('GET', '/api/admin/users'),
        api('GET', '/api/admin/depositors'),
        api('GET', '/api/admin/warehouses'),
        api('GET', '/api/admin/depositor-access')
      ]);
      данные.пользователи = пользователи;
      данные.поклажедатели = поклажедатели;
      данные.склады = склады;
      данные.доступыПоклажедателей = доступы;
    } catch (e){
      // 403 — раздел доступен только администратору, это нормально
      данные.пользователи = [];
      данные.доступыПоклажедателей = [];
    }
    отрисоватьДоступыМенеджеров();
    отрисоватьДоступыСотрудников();
    отрисоватьСкладыПоклажедателей();
  }

  /** Общий блок галочек: один список, одно правило разметки. */
  function галочки(элементы, выбранные, атрибут, подпись, пояснение){
    const выбрано = new Set(выбранные || []);
    if (!элементы.length){
      return '<div style="color:var(--muted);font-size:13.5px;">Список пуст</div>';
    }
    return элементы.map(x =>
      '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
      + 'padding:9px 0;border-bottom:1px solid var(--line);">'
      + '<input type="checkbox" ' + атрибут + '="' + экранировать(x.id) + '"'
      + (выбрано.has(x.id) ? ' checked' : '') + '> ' + экранировать(подпись(x))
      + (пояснение && пояснение(x)
          ? ' <span style="color:var(--muted);">· ' + экранировать(пояснение(x)) + '</span>'
          : '')
      + '</label>').join('');
  }

  function отмеченные(атрибут){
    return [...document.querySelectorAll('#adminUserEditContent input[' + атрибут + ']:checked')]
      .map(cb => cb.getAttribute(атрибут));
  }

  function кнопки(подраздел, обработчик){
    const el = экран('adminUserEditContent');
    el.querySelector('[data-save]').onclick = обработчик;
    el.querySelector('[data-cancel]').onclick = () => {
      showView('admin');
      showAdminSub(подраздел);
    };
  }

  const действия = ''
    + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
    + '  <button class="btn primary" type="button" data-save>Сохранить</button>'
    + '  <button class="btn ghost" type="button" data-cancel>Отмена</button>'
    + '</div>';

  const толькоЧтение = () => currentUserRole === 'developer';

  function запретитьПравку(){
    if (!толькоЧтение()) return;
    const el = экран('adminUserEditContent');
    el.insertAdjacentHTML('afterbegin', '<div style="background:#eef2ff;border:1px solid #c7d2fe;'
      + 'color:#3730a3;border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px;">'
      + '👁 Режим просмотра — менять доступы вправе только администратор.</div>');
    el.querySelectorAll('input').forEach(e => e.disabled = true);
    const кнопка = el.querySelector('[data-save]');
    if (кнопка) кнопка.style.display = 'none';
  }

  /* ======================================================================
     1. «Доступы» — поклажедатели менеджера
     ====================================================================== */

  function менеджеры(){
    return данные.пользователи.filter(u => u.roleCode === 'manager');
  }

  function отрисоватьДоступыМенеджеров(){
    const body = экран('adminAccessBody');
    if (!body) return;
    const список = менеджеры();
    body.innerHTML = список.length
      ? список.map(u => '<tr onclick="portalOpenManagerAccess(\'' + u.userId + '\')">'
          + '<td><strong>' + экранировать(u.fullName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + экранировать(u.login) + '</div></td>'
          + '<td>' + экранировать(u.scope) + '</td></tr>').join('')
      : '<tr><td colspan="2" style="color:var(--muted);">Менеджеры не заведены</td></tr>';
  }

  window.portalOpenManagerAccess = async function(userId){
    const u = данные.пользователи.find(x => x.userId === userId);
    if (!u) return;
    let выданные = [];
    try { выданные = await api('GET', '/api/admin/users/' + encodeURIComponent(userId) + '/depositors'); }
    catch (e){ выданные = []; }

    экран('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступы менеджера — ' + экранировать(u.fullName) + '</h2>'
      + '  <div class="meta">' + экранировать(u.userPosition || '—') + ' · '
      +      экранировать(u.login) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">Поклажедатели, с которыми работает менеджер</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Менеджер видит только отмеченных. '
      + 'Если доступен не один, портал при входе спросит, с кем он работает.</div>'
      + галочки(данные.поклажедатели.map(d => ({id: d.depositorId, name: d.orgName, code: d.code})),
                выданные, 'data-depositor-id', x => x.name, x => x.code)
      + '</div>'
      + действия;

    кнопки('access', async () => {
      try {
        await api('PUT', '/api/admin/users/' + encodeURIComponent(userId) + '/depositors',
                  {depositorIds: отмеченные('data-depositor-id')});
        await загрузить();
        if (typeof notify === 'function') notify('Доступы обновлены: ' + u.fullName);
        showView('admin');
        showAdminSub('access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    запретитьПравку();
    showView('admin-user-edit');
  };

  /* ======================================================================
     2. «Доступы сотрудников» — склады кладовщика
     ====================================================================== */

  function кладовщики(){
    return данные.пользователи.filter(u => u.roleCode === 'staff');
  }

  function отрисоватьДоступыСотрудников(){
    const body = экран('adminStaffAccessBody');
    if (!body) return;
    const список = кладовщики();
    body.innerHTML = список.length
      ? список.map(u => '<tr onclick="portalOpenStaffAccess(\'' + u.userId + '\')">'
          + '<td><strong>' + экранировать(u.fullName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + экранировать(u.login) + '</div></td>'
          + '<td>' + экранировать(u.scope) + '</td>'
          + '<td>' + экранировать(u.depositorCodes || 'по складам') + '</td></tr>').join('')
      : '<tr><td colspan="3" style="color:var(--muted);">Складские сотрудники не заведены</td></tr>';
  }

  window.portalOpenStaffAccess = async function(userId){
    const u = данные.пользователи.find(x => x.userId === userId);
    if (!u) return;
    let выданные = [];
    try { выданные = await api('GET', '/api/admin/users/' + encodeURIComponent(userId) + '/warehouses'); }
    catch (e){ выданные = []; }

    экран('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступы сотрудника — ' + экранировать(u.fullName) + '</h2>'
      + '  <div class="meta">' + экранировать(u.userPosition || '—') + ' · '
      +      экранировать(u.login) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">Склады, на которых работает сотрудник</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Поклажедатели сотруднику не назначаются '
      + 'отдельно: он видит тех, чей товар лежит на его складах.</div>'
      + галочки(данные.склады.map(w => ({id: w.warehouseId, name: w.name, address: w.address})),
                выданные, 'data-warehouse-id', x => x.name, x => x.address)
      + '</div>'
      + действия;

    кнопки('staff-access', async () => {
      try {
        await api('PUT', '/api/admin/users/' + encodeURIComponent(userId) + '/warehouses',
                  {warehouseIds: отмеченные('data-warehouse-id')});
        await загрузить();
        if (typeof notify === 'function') notify('Склады сотрудника обновлены: ' + u.fullName);
        showView('admin');
        showAdminSub('staff-access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    запретитьПравку();
    showView('admin-user-edit');
  };

  /* ======================================================================
     3. «Склады поклажедателей»
     ====================================================================== */

  function отрисоватьСкладыПоклажедателей(){
    const body = экран('adminDepositorAccessBody');
    if (!body) return;
    const список = данные.доступыПоклажедателей;
    body.innerHTML = список.length
      ? список.map(d => '<tr onclick="portalOpenDepositorWarehouses(\'' + d.depositorId + '\')">'
          + '<td><strong>' + экранировать(d.orgName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + экранировать(d.code) + '</div></td>'
          + '<td>' + (d.warehouses
              ? экранировать(d.warehouses)
              : '<span style="color:var(--muted);">все склады</span>') + '</td>'
          + '<td></td></tr>').join('')
      : '<tr><td colspan="3" style="color:var(--muted);">Поклажедатели не заведены</td></tr>';
  }

  window.portalOpenDepositorWarehouses = async function(depositorId){
    const d = данные.доступыПоклажедателей.find(x => x.depositorId === depositorId);
    if (!d) return;
    let выданные = [];
    try {
      выданные = await api('GET', '/api/admin/depositors/'
                + encodeURIComponent(depositorId) + '/warehouses');
    } catch (e){ выданные = []; }

    экран('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступные склады — ' + экранировать(d.orgName) + '</h2>'
      + '  <div class="meta">Код поклажедателя: ' + экранировать(d.code) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">Склады, на которых поклажедатель хранит товар</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Отмеченные склады поклажедатель видит '
      + 'в документах. Если не отмечен ни один — доступны все: так забытая настройка '
      + 'не оставит клиента без складов.</div>'
      + галочки(данные.склады.map(w => ({id: w.warehouseId, name: w.name, address: w.address})),
                выданные, 'data-warehouse-id', x => x.name, x => x.address)
      + '</div>'
      + действия;

    кнопки('depositor-access', async () => {
      try {
        данные.доступыПоклажедателей = await api('PUT', '/api/admin/depositors/'
                + encodeURIComponent(depositorId) + '/warehouses',
                {warehouseIds: отмеченные('data-warehouse-id')});
        отрисоватьСкладыПоклажедателей();
        if (typeof notify === 'function') notify('Доступные склады обновлены: ' + d.orgName);
        // если правили текущего поклажедателя — обновляем списки складов в документах
        if (PORTAL.session && PORTAL.session.depositorId === depositorId){
          PORTAL.reloadAll();
        }
        showView('admin');
        showAdminSub('depositor-access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    запретитьПравку();
    showView('admin-user-edit');
  };

  /* ======================================================================
     4. Установка
     ====================================================================== */

  function install(){
    window.renderAdminAccess = отрисоватьДоступыМенеджеров;
    window.renderAdminStaffAccess = отрисоватьДоступыСотрудников;
    window.renderAdminDepositorAccess = отрисоватьСкладыПоклажедателей;
    // старые демо-обработчики строк больше не нужны
    window.openAdminAccessEdit = () => {};
    window.openAdminStaffAccessEdit = () => {};
    window.openAdminDepositorAccessEdit = window.portalOpenDepositorWarehouses;
    window.setStaffWarehouseFilter = window.setStaffWarehouseFilter || (() => {});
    PORTAL.onLogin(загрузить);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


def main():
    html = open(SRC, encoding='utf-8').read()
    if '</body>' not in html:
        sys.exit('не найден </body>')
    out = html.replace('</body>', PATCH + PATCH_DOCS + PATCH_CATALOG + PATCH_CARD + PATCH_SKU + PATCH_DRAFT + PATCH_AUTH + PATCH_ADMIN + PATCH_ACCESS + '\n</body>', 1)
    import os
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    open(DST, 'w', encoding='utf-8').write(out)
    print('записано:', DST, len(out), 'байт')


if __name__ == '__main__':
    main()
