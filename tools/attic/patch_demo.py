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

PATCH_T = r"""
<!-- ===== Перевод строк, собираемых в коде =====
     Надписи в вёрстке переводятся по атрибуту data-i18n. Те, что код
     собирает сам (кнопки в карточках, сообщения, подписи полей), проходят
     через T(): она берёт текст на текущем языке, а если его нет — показывает
     русский, зашитый рядом при сборке. Пустых кнопок на экране не бывает.
========================================================================= -->
<script>
(function(){
  'use strict';
  const RU = __RU_FALLBACK__;
  const BY_RU = __RU_TO_KEY__;

  /*
    Прототип рисует списки сразу при загрузке страницы. Если тексты в этот
    момент ещё не пришли, таблица остаётся на языке, который был зашит
    при сборке, и перерисовывать её потом некому: половина списков строится
    один раз. Поэтому тексты забираются здесь же, до первой отрисовки,
    обычным синхронным запросом — он один, небольшой и кэшируется.
    Язык берём тот, что человек выбрал в прошлый раз: сервер всё равно
    пришлёт свой при входе, но до входа показывать надо уже на нужном.
  */
  let PRELOADED = null;
  try {
    let code = null;
    try { code = window.localStorage.getItem('portalLocale'); } catch (e) { code = null; }
    const xhr = new XMLHttpRequest();
    xhr.open('GET', '/api/i18n/texts/' + encodeURIComponent(code || 'en'), false);
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.send(null);
    if (xhr.status === 200) PRELOADED = JSON.parse(xhr.responseText);
  } catch (e) {
    PRELOADED = null;        // портал ещё не отвечает — покажем русский
  }

  function texts(){
    return (window.PORTAL && window.PORTAL.i18n && window.PORTAL.i18n.texts) || PRELOADED;
  }

  /** Перевод по ключу. Нет перевода — показываем русский, а не ключ. */
  window.T = function(key){
    const all = texts();
    const v = all ? all[key] : null;
    return (v === undefined || v === null) ? (RU[key] !== undefined ? RU[key] : key) : v;
  };

  /**
   * Перевод по русскому тексту.
   * <p>
   * Карточки документов и справочников собирают подписи так: функции
   * отрисовки получают готовую русскую строку (kvField('Склад', …)).
   * Переписывать полсотни таких вызовов на ключи — много правок ради
   * того же результата, поэтому подпись переводится по самому тексту.
   * Неизвестный текст возвращается как есть: это данные, а не надпись.
   */
  window.TRU = function(text){
    if (!text) return text;
    const key = BY_RU[String(text).trim()];
    if (!key) return text;
    const all = texts();
    const v = all ? all[key] : null;
    return (v === undefined || v === null) ? text : v;
  };
})();
</script>
"""


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

  /**
   * Язык для форматирования времени и чисел.
   *
   * Время в строке состояния над таблицами форматировалось жёстко по-русски,
   * и на английском интерфейсе рядом с английской подписью стояло русское
   * время. Берём язык, выбранный пользователем; пока переводы не загрузились
   * или язык неизвестен — английский, как язык портала по умолчанию.
   */
  PORTAL.языкВремени = function(){
    const code = (PORTAL.i18n && PORTAL.i18n.code) || 'en';
    return {ru: 'ru-RU', en: 'en-GB', nl: 'nl-NL', kk: 'kk-KZ'}[code] || 'en-GB';
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
      bar.innerHTML = '<span style="color:#c0392b;">' + 'Не удалось получить данные'
        + ': ' + PORTAL.error
        + '</span> · <a href="#" id="portalStockRefresh">Повторить</a>';
    } else {
      const t = PORTAL.loadedAt
        ? PORTAL.loadedAt.toLocaleTimeString(PORTAL.языкВремени(),
            {hour:'2-digit', minute:'2-digit'})
        : '—';
      bar.innerHTML = 'Поклажедатель <b>' + PORTAL.depositorCode + '</b> · '
        + 'данные на' + ' ' + t
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
      bar.innerHTML = '<span style="color:#c0392b;">' + 'Не удалось получить данные'
        + ': ' + err + '</span>'
        + ' · <a href="#" data-portal-reload="' + table + '">Повторить</a>';
    } else {
      const t = PORTAL.docsLoadedAt[table];
      bar.innerHTML = 'Поклажедатель <b>' + PORTAL.depositorCode + '</b> · '
        + 'данные на' + ' '
        + (t ? t.toLocaleTimeString(PORTAL.языкВремени(),
              {hour:'2-digit', minute:'2-digit'}) : '—')
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

  function install(){
    patchSkuColumns();
    window.renderSku = renderSkuFromApi;
    window.renderBillingServices = renderTariffsFromApi;
    window.renderBillingInvoices = renderInvoicesFromApi;
    // Поставщиков и грузополучателей ведёт отдельный блок (этап 7б):
    // там они не только показываются, но и правятся, поэтому список
    // грузится вместе с архивом и рисуется там же.

    // Перечитать список товаров умеют теперь и другие блоки: после
    // загрузки из Excel созданные позиции должны оказаться в списке,
    // а не ждать следующего входа.
    PORTAL.перечитатьТовары = () => load('sku', renderSkuFromApi);

    PORTAL.onLogin(() => {
      PORTAL.перечитатьТовары();
      load('service-tariffs', renderTariffsFromApi);
      load('invoices', renderInvoicesFromApi);
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

  // Правка справочника (следующий блок) открывает ту же карточку, поэтому
  // перевод строки API в объект демо нужен и ей — отдаём его наружу,
  // чтобы правило «как называется поле в карточке» жило в одном месте.
  PORTAL.skuToProduct = toProduct;

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

  /**
   * Перечитывает настройки и применяет видимость разделов.
   *
   * Часть настроек — свойство поклажедателя, а не человека: «Показать
   * раздел перемещения» живёт в настройках клиента (V35). Поэтому при
   * смене поклажедателя /api/me надо спросить заново: раздел у одного
   * клиента показан, у другого скрыт. Без этого вкладка менялась только
   * после перезагрузки страницы.
   */
  PORTAL.обновитьНастройки = async function(){
    let me;
    try {
      me = await api('GET', '/api/me');
    } catch (e){
      return;                       // не смогли спросить — оставляем как есть
    }
    PORTAL.user = me;
    if (typeof currentUser === 'undefined' || !currentUser) return;
    currentUser.hiddenTabs = [];
    if (me.hideTransfersTab) currentUser.hiddenTabs.push('transfers');
    if (me.hideBillingTab) currentUser.hiddenTabs.push('billing');
    currentUser.hideServicesTab = me.hideServicesTab === true;
    currentUser.canCreateReceipts = me.canCreateReceipts !== false;
    currentUser.canCreateOrders = me.canCreateOrders !== false;
    currentUser.canCreateTransfers = me.canCreateTransfers !== false;
    currentUser.canUploadSkuExcel = me.canUploadSkuExcel !== false;
    currentUser.canUploadPackagingExcel = me.canUploadPackagingExcel !== false;
    if (typeof applyUserTabVisibility === 'function') applyUserTabVisibility(currentUser);
    if (typeof applyUserCreatePermissions === 'function') {
      applyUserCreatePermissions(currentUser);
    }
    // открытый раздел мог только что стать скрытым — уводим на заказы
    if (typeof showTab === 'function' && typeof lastMainView === 'string'
        && currentUser.hiddenTabs.indexOf(lastMainView) >= 0){
      showTab('orders');
    }
  };

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
      // видимость разделов зависит от клиента, поэтому спрашиваем заново
      await PORTAL.обновитьНастройки();
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
      + '  <div class="hint" style="margin:-4px 0 10px;">Поклажедателю обычно отмечают одного — свою организацию. Менеджеру — тех клиентов, с которыми он работает.</div>'
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
      + полеВвода('auLogin', 'Email / логин', новый ? '' : u.login, 'j.smith@company.com')
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
          : 'Администратор не видит пароль пользователя. Можно выдать новый — портал потребует сменить его при первом входе.') + '</div>'
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
      // «Показывать раздел «Перемещения»» переехало к поклажедателю (V35):
      // это свойство клиента, а не человека, и живёт в карточке доступов.
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
        // та же функция, что и при смене поклажедателя: правила
        // видимости разделов должны считаться в одном месте
        await PORTAL.обновитьНастройки();
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


PATCH_SKU_EDIT = r"""
<!-- ===== ЭТАП 7: ведение справочника товаров и упаковок =====
     Карточка остаётся та же, что в прототипе: те же вкладки, те же
     названия полей, тот же порядок. Добавляются только кнопки действий
     и режим правки — человек не учится новому экрану, он получает
     возможность менять то, что уже видел.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /** Кто ведёт справочник. Кладовщик и разработчик — только смотрят. */
  const EDITOR_ROLES = ['depositor', 'manager', 'admin'];
  const mayEdit = () => EDITOR_ROLES.includes(PORTAL.session && PORTAL.session.roleCode);

  // Состояние карточки держим в одном объекте: вкладки перерисовывают
  // тело целиком, и после переключения вкладки введённое должно остаться.
  const state = {
    mode: 'view',      // view | edit | create
    skuId: null,
    draft: {},         // только изменённые поля — их и отправляем
    packagings: [],
    pkgForm: null,     // строка упаковки в правке: {packagingId|null, values}
    dict: null
  };
  PORTAL.skuEdit = state;

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  async function dictionaries(){
    if (!state.dict) state.dict = await api('GET', '/api/sku/dictionaries');
    return state.dict;
  }

  /* ================= Какие поля правятся и на какой вкладке =================
     Названия вкладок и подписи совпадают с прототипом: в режиме просмотра
     человек видит ровно те же строки, просто без полей ввода. Поля, которых
     здесь нет, портал не меняет — их ставит синхронизация с WMS или сам
     портал (идентификатор, статус, аудит, единица заказа).
  ========================================================================= */
  // Заголовок вкладки переводится, поэтому опознавать её по тексту нельзя:
  // держим ключ, а подпись берём через T() — так же, как её рисует прототип.
  /**
   * Доступно ли поле этому поклажедателю.
   *
   * TABS хранит имена полей базы (weight_gross), а справочник колонок —
   * идентификаторы карточки (weightGross), и у шести полей-ссылок они
   * расходятся по-особому (uom_id -> unit). Те же правила, что
   * в SkuTemplateFields на сервере: список один, иначе разъедутся.
   */
  const COL_OF = {
    uom_id: 'unit', item_group: 'group', item_type: 'productType',
    currency_id: 'currency', hazard_class_id: 'hazardClass',
    hazard_group_id: 'hazardGroup'
  };
  function полеДоступно(column){
    const id = COL_OF[column]
      || column.replace(/_([a-z0-9])/g, (m, c) => c.toUpperCase());
    return !PORTAL.полеДоступно || PORTAL.полеДоступно(id);
  }

  const TABS = {
    'sku.text.general_information': [
      {col: 'item_code', label: 'Код товара'},
      {col: 'code', label: 'Коммерческий код', required: true},
      {col: 'name', label: 'Наименование товара', required: true},
      {col: 'item_full_name', label: 'Полное наименование товара'},
      // Справочников групп и типов больше нет (V38): это свободный
      // текст. Подсказка — значения, которые у этого клиента уже
      // встречаются, чтобы «Сырьё» не превратилось в «сырье», «Сырьё.»
      // и «Сырье » тремя разными группами.
      {col: 'item_group', label: 'Группа товара', suggest: 'group',
       readCol: 'itemGroupName'},
      {col: 'item_type', label: 'Тип товара', suggest: 'type',
       readCol: 'itemTypeName'},
      {col: 'uom_id', label: 'Базовая единица измерения', dict: 'uom', required: true},
      {col: 'barcode', label: 'Штрих код'}
    ],
    'sku.text.physical_properties': [
      {col: 'length', label: 'Длина, см', num: true},
      {col: 'width', label: 'Ширина, см', num: true},
      {col: 'height', label: 'Высота, см', num: true},
      {col: 'volume', label: 'Объём, м³', num: true},
      {col: 'weight_gross', label: 'Вес брутто, кг', num: true},
      {col: 'weight_net', label: 'Вес нетто, кг', num: true},
      {col: 'color', label: 'Цвет'},
      {col: 'size', label: 'Размер'}
    ],
    'sku.text.storage_conditions': [
      {col: 'hazard_class_id', label: 'Класс опасности', dict: 'hazard_class'},
      {col: 'hazard_group_id', label: 'Группа опасности', dict: 'hazard_group'},
      {col: 'max_storage_temp', label: 'Макс. температура хранения, °C', num: true},
      {col: 'min_storage_temp', label: 'Мин. температура хранения, °C', num: true},
      {col: 'max_humidity', label: 'Макс. влажность хранения, %', num: true},
      {col: 'min_humidity', label: 'Мин. влажность хранения, %', num: true},
      {col: 'min_days_for_receipt', label: 'Минимальное кол-во дней для приёмки', num: true},
      {col: 'min_days_for_reservation', label: 'Минимальное кол-во дней для резервирования', num: true},
      {col: 'shelf_life_days', label: 'Срок хранения, дней', num: true}
    ],
    'sku.text.manufacturer_and_classification': [
      {col: 'manufacturer', label: 'Производитель'},
      {col: 'country_origin', label: 'Страна производитель'},
      {col: 'brand', label: 'Торговая марка'},
      {col: 'abc_class', label: 'ABC Класс'},
      {col: 'xyz_class', label: 'XYZ Класс'}
    ],
    'sku.text.codes_and_identifiers': [
      {col: 'hs_code', label: 'Код ТНВЭД'},
      {col: 'enstru_code', label: 'Код ЕНСТРУ'},
      {col: 'currency_id', label: 'Валюта', dict: 'currency'},
      {col: 'cost', label: 'Стоимость', num: true}
    ],
    'sku.text.attributes_and_tracking': [
      {col: 'is_fragile', label: 'Хрупкий товар', bool: true},
      {col: 'is_serial_tracked', label: 'Учёт серийных номеров', bool: true},
      {col: 'batch_tracking', label: 'Учёт партий', yesNoText: true},
      {col: 'expiry_tracking', label: 'Учёт срока годности', yesNoText: true},
      {col: 'is_kit', label: 'Признак комплекта', bool: true},
      {col: 'is_marking_required', label: 'Обязательная маркировка', bool: true},
      {col: 'is_excise', label: 'Акцизный товар', bool: true},
      {col: 'is_controlled', label: 'Контролируемый/лицензируемый товар', bool: true}
    ],
    'sku.text.system_information': [
      {col: 'comment', label: 'Комментарий'}
    ],
    // Доп. параметров в базе десять, и правятся все десять.
    'sku.text.custom_attributes': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => ({
      col: 'custom_attr' + i, label: 'Доп. параметр ' + i
    }))
  };

  /** Колонка базы → поле строки API (v_sku_full отдаёт их в camelCase). */
  const camel = col => col.replace(/_([a-z])/g, (m, c) => c.toUpperCase());

  /** Что показать в поле: сначала несохранённое, потом из карточки. */
  function value(field){
    if (Object.prototype.hasOwnProperty.call(state.draft, field.col)) {
      return state.draft[field.col];
    }
    const row = state.row || {};
    // Поле отправляется под именем колонки базы, а читается из
    // представления: у группы и типа имена расходятся (item_group ->
    // itemGroupName), поэтому место чтения задаётся явно.
    return row[field.readCol || camel(field.col)];
  }

  /* ================= Карточка товара ================= */

  const PACKAGING_TAB = 'common.label.packaging';

  /** Подпись активной вкладки → её ключ. */
  function tabKey(title){
    const keys = Object.keys(TABS).concat([PACKAGING_TAB]);
    return keys.find(k => (typeof T === 'function' ? T(k) : k) === title) || title;
  }

  function fieldHtml(field){
    const v = value(field);
    const name = 'data-col="' + field.col + '" oninput="portalSkuField(this)"'
               + ' onchange="portalSkuField(this)"';
    let input;
    if (field.dict){
      const list = (state.dict && state.dict[field.dict]) || [];
      input = '<select class="kv-input" ' + name + '><option value="">—</option>'
        + list.map(o => '<option value="' + esc(o.id) + '"'
            + (String(o.id) === String(v) ? ' selected' : '') + '>' + esc(o.name) + '</option>').join('')
        + '</select>';
    } else if (field.suggest){
      // Ввод свободный, но с подсказкой уже использованных значений:
      // datalist предлагает их списком и не мешает написать своё.
      const list = (state.dict && state.dict[field.suggest]) || [];
      const listId = 'skuSuggest_' + field.col;
      input = '<input class="kv-input" type="text" list="' + listId + '" value="'
        + esc(v === null || v === undefined ? '' : v) + '" ' + name + '/>'
        + '<datalist id="' + listId + '">'
        + list.map(o => '<option value="' + esc(o.name) + '"></option>').join('')
        + '</datalist>';
    } else if (field.bool){
      input = '<select class="kv-input" ' + name + '>'
        + '<option value="false"' + (v ? '' : ' selected') + '>Нет</option>'
        + '<option value="true"' + (v ? ' selected' : '') + '>Да</option></select>';
    } else if (field.yesNoText){
      // в базе это текст «Да»/«Нет», а не логический признак
      input = '<select class="kv-input" ' + name + '><option value="">—</option>'
        + ['Да', 'Нет'].map(o => '<option' + (o === v ? ' selected' : '') + '>' + o + '</option>').join('')
        + '</select>';
    } else {
      input = '<input class="kv-input" type="' + (field.num ? 'number' : 'text') + '"'
        + (field.num ? ' step="any"' : '') + ' value="' + esc(v === null || v === undefined ? '' : v)
        + '" ' + name + '/>';
    }
    return '<div class="kv"><label>' + esc(field.label) + (field.required ? ' *' : '')
      + '</label><div style="min-width:280px;">' + input + '</div></div>';
  }

  window.portalSkuField = function(el){
    const col = el.getAttribute('data-col');
    let v = el.value;
    if (el.tagName === 'SELECT' && (v === 'true' || v === 'false')) v = (v === 'true');
    state.draft[col] = v === '' ? null : v;
  };

  /** Кнопки действий в шапке карточки — там, где у документов. */
  function headActions(){
    if (state.mode !== 'view'){
      return '<button class="btn primary" onclick="portalSkuSave()">Сохранить</button>'
        + '<button class="btn ghost" onclick="portalSkuCancel()">Отмена</button>';
    }
    if (!mayEdit() || !state.skuId) return '';
    const archived = state.row && state.row.isActive === false;
    return '<button class="btn primary" onclick="portalSkuEdit()">Редактировать</button>'
      + '<button class="btn" onclick="portalSkuArchive(' + (archived ? 'true' : 'false') + ')">'
      + (archived ? 'Вернуть из архива' : 'В архив') + '</button>'
      + '<button class="btn ghost" onclick="portalSkuDelete()">Удалить</button>';
  }

  function packagingTabHtml(){
    if (!state.skuId){
      return '<div style="color:var(--muted); font-size:13px;">'
        + 'Сначала сохраните товар — упаковки задаются уже для него</div>';
    }
    const rows = state.packagings;
    const types = (state.dict && state.dict.packaging_type) || [];
    const canEdit = mayEdit() && state.mode === 'view';

    const table = rows.length
      ? '<div class="table-card" style="margin-top:4px;"><table><thead><tr>'
        + '<th>Код упаковки</th><th>Тип упаковки</th><th>Штрих код упаковки</th>'
        + '<th class="num">Коэффициент</th><th class="num">Длина, см</th><th class="num">Ширина, см</th>'
        + '<th class="num">Высота, см</th><th class="num">Объём, м³</th><th class="num">Вес брутто, кг</th>'
        + '<th>Единица заказа</th>' + (canEdit ? '<th></th>' : '') + '</tr></thead><tbody>'
        + rows.map(r => '<tr><td><strong>' + esc(r.packagingCode) + '</strong></td><td>'
            + dash(esc(r.packagingTypeName)) + '</td><td>' + dash(esc(r.barcode))
            + '</td><td class="num">' + dash(r.coefficient) + '</td><td class="num">' + dash(r.lengthCm)
            + '</td><td class="num">' + dash(r.widthCm) + '</td><td class="num">' + dash(r.heightCm)
            + '</td><td class="num">' + dash(r.volumeM3) + '</td><td class="num">' + dash(r.grossWeightKg)
            + '</td><td>' + (r.isDefaultOrderUnit
                ? '★ по умолчанию'
                : (canEdit ? '<button class="btn ghost" onclick="portalPackagingDefault(\'' + r.packagingId
                    + '\')">Сделать единицей заказа</button>' : '—'))
            + '</td>'
            + (canEdit ? '<td style="white-space:nowrap;">'
                + '<button class="btn ghost" onclick="portalPackagingEdit(\'' + r.packagingId + '\')">Изменить</button> '
                + '<button class="btn ghost" onclick="portalPackagingDelete(\'' + r.packagingId + '\')">Удалить</button></td>' : '')
            + '</tr>').join('')
        + '</tbody></table></div>'
      : '<div style="color:var(--muted); font-size:13px;">Для этого товара упаковки не заданы</div>';

    let form = '';
    if (state.pkgForm){
      const f = state.pkgForm.values;
      const input = (col, label, num) => '<div class="kv"><label>' + label + '</label><div>'
        + '<input class="kv-input" type="' + (num ? 'number' : 'text') + '"'
        + (num ? ' step="any"' : '') + ' value="' + esc(f[col] === undefined ? '' : f[col])
        + '" data-pkg="' + col + '" oninput="portalPackagingField(this)"/></div></div>';
      form = '<div class="table-card" style="margin-top:12px; padding:12px;">'
        + '<div class="section-title">' + (state.pkgForm.packagingId ? 'Изменение упаковки' : 'Новая упаковка') + '</div>'
        + '<div class="kv-grid">'
        + input('packaging_code', 'Код упаковки *')
        + '<div class="kv"><label>Тип упаковки</label><div><select class="kv-input"'
          + ' data-pkg="packaging_type_id" onchange="portalPackagingField(this)"><option value="">—</option>'
          + types.map(t => '<option value="' + esc(t.id) + '"'
              + (String(t.id) === String(f.packaging_type_id) ? ' selected' : '') + '>'
              + esc(t.name) + '</option>').join('')
          + '</select></div></div>'
        + input('barcode', 'Штрих код упаковки')
        + input('coefficient', 'Коэффициент *', true)
        + input('length_cm', 'Длина, см', true)
        + input('width_cm', 'Ширина, см', true)
        + input('height_cm', 'Высота, см', true)
        + input('volume_m3', 'Объём, м³', true)
        + input('gross_weight_kg', 'Вес брутто, кг', true)
        + '</div><div style="margin-top:12px;">'
        + '<button class="btn primary" onclick="portalPackagingSave()">Сохранить упаковку</button> '
        + '<button class="btn ghost" onclick="portalPackagingCancel()">Отмена</button></div></div>';
    } else if (canEdit){
      form = '<div style="margin-top:12px;">'
        + '<button class="btn" onclick="portalPackagingAdd()">+ Добавить упаковку</button></div>';
    }
    return table + form;
  }

  window.portalPackagingField = function(el){
    state.pkgForm.values[el.getAttribute('data-pkg')] = el.value === '' ? null : el.value;
  };

  /** Дорисовываем карточку прототипа: кнопки в шапке и, в правке, поля ввода. */
  function decorate(code){
    const head = document.querySelector('#view-sku-detail .panel-head');
    if (head){
      let actions = head.querySelector('.actions');
      if (!actions){
        actions = document.createElement('div');
        actions.className = 'actions';
        head.appendChild(actions);
      }
      actions.innerHTML = headActions();
    }
    const title = document.getElementById('skuDetailTitle');
    if (title && state.mode === 'create') title.textContent = 'Новый товар';
    if (title && state.row && state.row.isActive === false){
      title.innerHTML = esc(state.row.name) + ' <span class="count">(в архиве)</span>';
    }

    const body = document.getElementById('skuDetailBody');
    if (!body) return;
    // fcActiveSkuTab объявлена через let: это лексическая глобальная переменная,
    // и обращаться к ней надо по имени — свойства window у неё нет
    // прототип держит в fcActiveSkuTab подпись вкладки — переводим её обратно в ключ
    const tab = tabKey(fcActiveSkuTab);
    if (tab === PACKAGING_TAB){
      body.innerHTML = packagingTabHtml();
      return;
    }
    if (state.mode === 'view') return;     // просмотр рисует прототип
    if (!TABS[tab]){
      body.innerHTML = '<div style="color:var(--muted); font-size:13px;">'
        + 'Эти поля портал не меняет: их ставит обмен с WMS или сам портал</div>';
      return;
    }
    // Поля, закрытые этому поклажедателю, не правятся: иначе запрет
    // действовал бы только в просмотре, а в правке поле оставалось бы.
    const fields = TABS[tab].filter(f => полеДоступно(f.col));
    if (!fields.length){
      body.innerHTML = '<div style="color:var(--muted); font-size:13px;">'
        + 'На этой вкладке у вас нет доступных полей</div>';
      return;
    }
    body.innerHTML = '<div class="kv-grid">' + fields.map(fieldHtml).join('') + '</div>'
      + '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
      + 'Поля со звёздочкой обязательны. Правки со всех вкладок сохраняются одной кнопкой.</div>';
  }

  /* ================= Открытие карточки ================= */

  async function openCard(skuId){
    const card = await api('GET', '/api/sku/' + skuId);
    await dictionaries();
    state.skuId = skuId;
    state.row = card;
    state.packagings = card.packagings || [];
    state.draft = {};
    state.pkgForm = null;
    const p = PORTAL.skuToProduct(card);
    products.length = 0;
    products.push(p);
    openSkuItem(card.code);
  }

  // Список товаров открывает карточку из API: в строке списка есть skuId,
  // а карточка приходит вместе с упаковками одним запросом.
  window.portalOpenSku = function(code){
    const row = (PORTAL.catalog.sku || []).find(s => s.code === code);
    if (!row){
      say('Товар ' + code + ' не найден в загруженном списке');
      return;
    }
    state.mode = 'view';
    openCard(row.skuId).catch(e => say('Не удалось открыть карточку: ' + e.message));
  };

  window.portalSkuCreate = async function(){
    try {
      await dictionaries();
      state.mode = 'create';
      state.skuId = null;
      state.row = {};
      state.draft = {};
      state.packagings = [];
      state.pkgForm = null;
      fcActiveSkuTab = typeof T === 'function' ? T('sku.text.general_information')
                                                : 'Основная информация';
      products.length = 0;
      products.push(PORTAL.skuToProduct({code: '', name: 'Новый товар'}));
      openSkuItem('');
    } catch (e){
      say('Не удалось открыть создание товара: ' + e.message);
    }
  };

  window.portalSkuEdit = function(){
    state.mode = 'edit';
    state.draft = {};
    openSkuItem(state.row.code);
  };

  window.portalSkuCancel = function(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      showTab('references');
      showReferencesSub('sku');
      return;
    }
    state.mode = 'view';
    openSkuItem(state.row.code);
  };

  window.portalSkuSave = async function(){
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      openSkuItem(state.row.code);
      return;
    }
    try {
      const card = creating
        ? await api('POST', '/api/sku', state.draft)
        : await api('PUT', '/api/sku/' + state.skuId, state.draft);
      state.mode = 'view';
      state.skuId = card.skuId;
      state.row = card;
      state.packagings = card.packagings || [];
      state.draft = {};
      await reloadList();
      products.length = 0;
      products.push(PORTAL.skuToProduct(card));
      openSkuItem(card.code);
      say(creating ? 'Товар создан' : 'Изменения сохранены');
    } catch (e){
      say(e.message);
    }
  };

  window.portalSkuArchive = async function(archived){
    try {
      const card = await api('POST', '/api/sku/' + state.skuId + '/archive', {active: archived});
      state.row = card;
      state.packagings = card.packagings || [];
      await reloadList();
      products.length = 0;
      products.push(PORTAL.skuToProduct(card));
      openSkuItem(card.code);
      say(archived ? 'Товар возвращён в работу' : 'Товар переведён в архив');
    } catch (e){
      say(e.message);
    }
  };

  window.portalSkuDelete = async function(){
    // Подтверждение нужно: удаление необратимо, в отличие от архива
    if (!window.confirm('Удалить товар «' + state.row.code + '» без возможности восстановления?\n'
        + 'Если он где-то используется, портал откажет и предложит архив.')) return;
    try {
      await api('DELETE', '/api/sku/' + state.skuId);
      await reloadList();
      say('Товар удалён');
      showTab('references');
      showReferencesSub('sku');
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Упаковки в карточке ================= */

  const PKG_FIELDS = ['packaging_code', 'packaging_type_id', 'barcode', 'coefficient',
                      'length_cm', 'width_cm', 'height_cm', 'volume_m3', 'gross_weight_kg'];

  window.portalPackagingAdd = function(){
    state.pkgForm = {packagingId: null, values: {}};
    openSkuItem(state.row.code);
  };

  window.portalPackagingEdit = function(id){
    const r = state.packagings.find(p => p.packagingId === id);
    if (!r) return;
    const values = {};
    PKG_FIELDS.forEach(col => { values[col] = r[camel(col)]; });
    state.pkgForm = {packagingId: id, values: values};
    openSkuItem(state.row.code);
  };

  window.portalPackagingCancel = function(){
    state.pkgForm = null;
    openSkuItem(state.row.code);
  };

  window.portalPackagingSave = async function(){
    const form = state.pkgForm;
    try {
      state.packagings = form.packagingId
        ? await api('PUT', '/api/packagings/' + form.packagingId, form.values)
        : await api('POST', '/api/sku/' + state.skuId + '/packagings', form.values);
      state.pkgForm = null;
      await reloadPackagingScreen();
      openSkuItem(state.row.code);
      say('Упаковка сохранена');
    } catch (e){
      say(e.message);
    }
  };

  window.portalPackagingDefault = async function(id){
    try {
      state.packagings = await api('POST', '/api/packagings/' + id + '/default');
      await reloadList();
      await reloadPackagingScreen();
      openSkuItem(state.row.code);
    } catch (e){
      say(e.message);
    }
  };

  window.portalPackagingDelete = async function(id){
    const r = state.packagings.find(p => p.packagingId === id);
    if (!window.confirm('Удалить упаковку «' + (r ? r.packagingCode : id) + '»?')) return;
    try {
      state.packagings = await api('DELETE', '/api/packagings/' + id);
      await reloadPackagingScreen();
      openSkuItem(state.row.code);
      say('Упаковка удалена');
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Экран «Упаковки» ================= */

  /**
   * Список товаров. С сервера забираем вместе с архивом и держим полный
   * набор отдельно: иначе архивную позицию нечем вернуть в работу — её
   * просто не видно. В списке по умолчанию показываем рабочие, а архив
   * открывается флажком.
   */
  async function reloadList(){
    try {
      PORTAL.catalog.skuAll = await api('GET', '/api/sku?withArchived=true');
      PORTAL.catalogError.sku = null;
    } catch (e){
      PORTAL.catalog.skuAll = PORTAL.catalog.skuAll || [];
      PORTAL.catalogError.sku = e.message;
    }
    applyArchiveFilter();
  }

  function applyArchiveFilter(){
    const show = document.getElementById('skuShowArchived')?.checked;
    PORTAL.catalog.sku = (PORTAL.catalog.skuAll || []).filter(s => show || s.isActive !== false);
    if (typeof renderSku === 'function') renderSku();
  }

  window.portalSkuArchiveFilter = applyArchiveFilter;

  /** Архивную позицию в списке надо отличать глазом, а не по памяти. */
  function markArchivedRows(){
    if (typeof tableColumns === 'undefined' || !tableColumns.sku) return;
    const c = tableColumns.sku.find(x => x.id === 'code');
    if (!c) return;
    c.render = p => '<strong>' + esc(dash(p.code)) + '</strong>'
      + (p.isActive === false ? ' <span class="count">в архиве</span>' : '');
  }

  function addArchiveCheckbox(){
    const filters = document.querySelector('#ref-sub-sku .filters');
    if (!filters || document.getElementById('skuShowArchived')) return;
    const box = document.createElement('div');
    box.className = 'fgroup';
    box.innerHTML = '<label>Архив</label><label style="font-size:13px; font-weight:500;">'
      + '<input type="checkbox" id="skuShowArchived" onchange="portalSkuArchiveFilter()"/>'
      + ' показывать архивные</label>';
    const spacer = filters.querySelector('.spacer');
    filters.insertBefore(box, spacer || null);
  }

  async function reloadPackagingScreen(){
    try {
      PORTAL.catalog.packaging = await api('GET', '/api/packaging');
      PORTAL.catalogError.packaging = null;
    } catch (e){
      PORTAL.catalog.packaging = [];
      PORTAL.catalogError.packaging = e.message;
    }
    renderPackagingFromApi();
  }

  function renderPackagingFromApi(){
    const body = document.getElementById('packagingBody');
    if (!body) return;
    const val = id => (document.getElementById(id)?.value || '').trim().toLowerCase();
    const codeQ = val('packagingSearchCode');
    const nameQ = val('packagingSearchName');

    let rows = (PORTAL.catalog.packaging || []).slice();
    if (codeQ) rows = rows.filter(r => (r.skuCode || '').toLowerCase().includes(codeQ));
    if (nameQ) rows = rows.filter(r => (r.skuName || '').toLowerCase().includes(nameQ));

    if (typeof fcDirSortArray === 'function'){
      const map = {productCode: 'skuCode', productName: 'skuName', unit: 'uomName',
                   packagingCode: 'packagingCode', type: 'packagingTypeName',
                   displayName: 'packagingTypeName', barcode: 'barcode',
                   coefficient: 'coefficient', length: 'lengthCm', width: 'widthCm',
                   height: 'heightCm', volume: 'volumeM3', weightGross: 'grossWeightKg'};
      rows = fcDirSortArray('packaging', rows, r => {
        const col = dirSortState.packaging.col;
        return r[map[col] || col] ?? '';
      });
    }
    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage('packaging', rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }
    const counter = document.getElementById('packagingCount');
    if (counter) counter.textContent = '(' + total + ')';

    body.innerHTML = items.length
      ? items.map(r => '<tr style="cursor:pointer;" onclick="portalOpenSku(\'' + esc(r.skuCode) + '\')">'
          + '<td><strong>' + esc(r.skuCode) + '</strong></td><td>' + dash(esc(r.skuName))
          + '</td><td>' + dash(esc(r.uomName)) + '</td><td>' + esc(r.packagingCode)
          + '</td><td>' + dash(esc(r.packagingTypeName)) + '</td><td>'
          + dash(esc(r.packagingTypeName) + ' (' + dash(r.coefficient) + dash(esc(r.uomName)) + ')')
          + '</td><td>' + dash(esc(r.barcode)) + '</td><td class="num">' + dash(r.coefficient)
          + '</td><td class="num">' + dash(r.lengthCm) + '</td><td class="num">' + dash(r.widthCm)
          + '</td><td class="num">' + dash(r.heightCm) + '</td><td class="num">' + dash(r.volumeM3)
          + '</td><td class="num">' + dash(r.grossWeightKg) + '</td></tr>').join('')
      : '<tr><td colspan="13" style="color:var(--muted);">'
        + (PORTAL.catalogError.packaging ? 'Данные недоступны' : 'Ничего не найдено') + '</td></tr>';
    if (typeof renderPaginationBar === 'function') renderPaginationBar('packaging', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('packaging'), 0);
  }

  /* ================= Установка ================= */

  function addCreateButton(){
    const actions = document.querySelector('#ref-sub-sku .panel-head .actions');
    if (!actions || document.getElementById('skuCreateBtn')) return;
    const btn = document.createElement('button');
    btn.id = 'skuCreateBtn';
    btn.className = 'btn primary';
    btn.textContent = '+ Создать товар';
    btn.onclick = () => window.portalSkuCreate();
    // первым в группе: создание — главное действие экрана, Excel рядом
    actions.insertBefore(btn, actions.firstChild);
  }

  function install(){
    const origOpenSkuItem = window.openSkuItem;
    window.openSkuItem = function(code){
      origOpenSkuItem(code);
      decorate(code);
    };
    window.renderPackaging = renderPackagingFromApi;

    PORTAL.onLogin(() => {
      markArchivedRows();
      addArchiveCheckbox();
      reloadList().catch(() => {});
      reloadPackagingScreen().catch(() => {});
      if (mayEdit()) addCreateButton();
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


PATCH_PARTNERS = r"""
<!-- ===== ЭТАП 7б: контрагенты и адреса доставки =====
     В прототипе поставщики и грузополучатели были плоскими таблицами без
     карточки. Карточку добавляем в той же манере, что у товара: шапка
     с кнопками, вкладки, те же классы — экран остаётся узнаваемым.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const EDITOR_ROLES = ['depositor', 'manager', 'admin'];
  const mayEdit = () => EDITOR_ROLES.includes(PORTAL.session && PORTAL.session.roleCode);

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  /* Два справочника устроены одинаково, различия собраны здесь —
     как и на сервере (PartnerKind). */
  const KINDS = {
    suppliers: {
      path: 'suppliers', key: 'supplierId', title: 'Поставщик',
      newTitle: 'Новый поставщик', body: 'suppliersBody', count: 'suppliersCount',
      columns: 7, addresses: false
    },
    consignees: {
      path: 'consignees', key: 'consigneeId', title: 'Грузополучатель',
      newTitle: 'Новый грузополучатель', body: 'consigneesBody', count: 'consigneesCount',
      columns: 8, addresses: true
    }
  };

  const FIELDS = [
    {col: 'code', label: 'Код', required: true},
    {col: 'name', label: 'Наименование', required: true},
    {col: 'bin', label: 'БИН'},
    {col: 'legal_address', label: 'Юр. адрес'},
    {col: 'contact_name', label: 'Контактное лицо (ФИО)'},
    {col: 'contact_phone', label: 'Телефон'},
    {col: 'contact_email', label: 'Email'}
  ];

  const camel = col => col.replace(/_([a-z])/g, (m, c) => c.toUpperCase());

  const state = {
    kind: null, mode: 'view', id: null, row: null, draft: {},
    addresses: [], addressForm: null, tab: 'card'
  };
  PORTAL.partnerEdit = state;

  /* ================= Карточка ================= */

  function ensureView(){
    if (document.getElementById('view-partner-detail')) return;
    const sku = document.getElementById('view-sku-detail');
    if (!sku) return;
    const section = document.createElement('section');
    section.className = 'view';
    section.id = 'view-partner-detail';
    section.innerHTML =
        '<div class="panel-head">'
      + '<button class="back-btn" onclick="portalPartnerBack()">← Назад к списку</button>'
      + '<div class="actions" id="partnerActions"></div></div>'
      + '<div class="detail-head"><h2 id="partnerTitle" style="margin:0 0 16px;"></h2>'
      + '<div class="admin-subtabs" id="partnerTabs" style="margin-bottom:0;"></div></div>'
      + '<div id="partnerBody" style="margin-top:16px;"></div>';
    sku.parentNode.insertBefore(section, sku.nextSibling);
  }

  function value(field){
    if (Object.prototype.hasOwnProperty.call(state.draft, field.col)) return state.draft[field.col];
    return (state.row || {})[camel(field.col)];
  }

  window.portalPartnerField = function(el){
    state.draft[el.getAttribute('data-col')] = el.value === '' ? null : el.value;
  };

  function cardHtml(){
    const editing = state.mode !== 'view';
    const rows = FIELDS.map(f => {
      const v = value(f);
      const body = editing
        ? '<input class="kv-input" type="text" value="' + esc(v === null || v === undefined ? '' : v)
          + '" data-col="' + f.col + '" oninput="portalPartnerField(this)"/>'
        : esc(dash(v));
      return '<div class="kv"><label>' + esc(f.label) + (f.required && editing ? ' *' : '')
        + '</label><div' + (editing ? ' style="min-width:320px;"' : '') + '>' + body + '</div></div>';
    }).join('');
    const hint = editing
      ? '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
        + 'Поля со звёздочкой обязательны.</div>'
      : '';
    return '<div class="kv-grid">' + rows + '</div>' + hint;
  }

  function addressesHtml(){
    if (!state.id){
      return '<div style="color:var(--muted); font-size:13px;">'
        + 'Сначала сохраните карточку — адреса задаются уже для грузополучателя</div>';
    }
    const canEdit = mayEdit() && state.mode === 'view';
    const rows = state.addresses;
    const table = rows.length
      ? '<div class="table-card" style="margin-top:4px;"><table><thead><tr>'
        + '<th>Адрес доставки</th><th class="num">Порядок</th><th>Основной</th>'
        + (canEdit ? '<th></th>' : '') + '</tr></thead><tbody>'
        + rows.map(a => '<tr><td>' + esc(a.address) + '</td><td class="num">'
            + dash(a.sortOrder) + '</td><td>'
            + (a.isDefault ? '★ основной'
                : (canEdit ? '<button class="btn ghost" onclick="portalAddressDefault(\'' + a.addressId
                    + '\')">Сделать основным</button>' : '—'))
            + '</td>'
            + (canEdit ? '<td style="white-space:nowrap;">'
                + '<button class="btn ghost" onclick="portalAddressEdit(\'' + a.addressId + '\')">Изменить</button> '
                + '<button class="btn ghost" onclick="portalAddressDelete(\'' + a.addressId + '\')">Удалить</button></td>' : '')
            + '</tr>').join('')
        + '</tbody></table></div>'
      : '<div style="color:var(--muted); font-size:13px;">Адреса доставки не заданы</div>';

    let form = '';
    if (state.addressForm){
      const f = state.addressForm.values;
      form = '<div class="table-card" style="margin-top:12px; padding:12px;">'
        + '<div class="section-title">'
        + (state.addressForm.addressId ? 'Изменение адреса' : 'Новый адрес') + '</div>'
        + '<div class="kv-grid">'
        + '<div class="kv"><label>Адрес доставки *</label><div><input class="kv-input" type="text"'
          + ' style="min-width:420px;" value="' + esc(f.address || '') + '" data-addr="address"'
          + ' oninput="portalAddressField(this)"/></div></div>'
        + '<div class="kv"><label>Порядок</label><div><input class="kv-input" type="number"'
          + ' value="' + esc(f.sortOrder === null || f.sortOrder === undefined ? '' : f.sortOrder)
          + '" data-addr="sortOrder" oninput="portalAddressField(this)"/></div></div>'
        + '</div><div style="margin-top:12px;">'
        + '<button class="btn primary" onclick="portalAddressSave()">Сохранить адрес</button> '
        + '<button class="btn ghost" onclick="portalAddressCancel()">Отмена</button></div></div>';
    } else if (canEdit){
      form = '<div style="margin-top:12px;">'
        + '<button class="btn" onclick="portalAddressAdd()">+ Добавить адрес</button></div>';
    }
    return table + form;
  }

  window.portalAddressField = function(el){
    state.addressForm.values[el.getAttribute('data-addr')] = el.value === '' ? null : el.value;
  };

  function actionsHtml(){
    if (state.mode !== 'view'){
      return '<button class="btn primary" onclick="portalPartnerSave()">Сохранить</button>'
        + '<button class="btn ghost" onclick="portalPartnerCancel()">Отмена</button>';
    }
    if (!mayEdit() || !state.id) return '';
    const archived = state.row && state.row.isActive === false;
    return '<button class="btn primary" onclick="portalPartnerEdit()">Редактировать</button>'
      + '<button class="btn" onclick="portalPartnerArchive(' + (archived ? 'true' : 'false') + ')">'
      + (archived ? 'Вернуть из архива' : 'В архив') + '</button>'
      + '<button class="btn ghost" onclick="portalPartnerDelete()">Удалить</button>';
  }

  function render(){
    ensureView();
    const kind = KINDS[state.kind];
    const name = state.mode === 'create' ? kind.newTitle : (state.row && state.row.name) || kind.title;
    const archived = state.row && state.row.isActive === false;
    document.getElementById('partnerTitle').innerHTML =
        esc(name) + (archived ? ' <span class="count">(в архиве)</span>' : '');
    document.getElementById('partnerActions').innerHTML = actionsHtml();

    const tabs = [{id: 'card', title: 'Карточка'}];
    if (kind.addresses) tabs.push({id: 'addresses', title: 'Адреса доставки'});
    if (!tabs.some(t => t.id === state.tab)) state.tab = 'card';
    document.getElementById('partnerTabs').innerHTML = tabs.map(t =>
        '<button class="admin-subtab-btn ' + (t.id === state.tab ? 'active' : '') + '"'
        + ' onclick="portalPartnerTab(\'' + t.id + '\')">' + t.title + '</button>').join('');

    document.getElementById('partnerBody').innerHTML =
        state.tab === 'addresses' ? addressesHtml() : cardHtml();
    showView('partner-detail');
  }

  window.portalPartnerTab = function(tab){
    state.tab = tab;
    render();
  };

  window.portalPartnerBack = function(){
    showTab('references');
    showReferencesSub(state.kind);
  };

  /* ================= Действия ================= */

  window.portalOpenPartner = async function(kindName, id){
    try {
      state.kind = kindName;
      state.mode = 'view';
      state.tab = 'card';
      state.draft = {};
      state.addressForm = null;
      const card = await api('GET', '/api/' + kindName + '/' + id);
      state.id = id;
      state.row = card;
      state.addresses = card.addresses || [];
      render();
    } catch (e){
      say('Не удалось открыть карточку: ' + e.message);
    }
  };

  window.portalPartnerCreate = function(kindName){
    state.kind = kindName;
    state.mode = 'create';
    state.tab = 'card';
    state.id = null;
    state.row = {};
    state.draft = {};
    state.addresses = [];
    state.addressForm = null;
    render();
  };

  window.portalPartnerEdit = function(){
    state.mode = 'edit';
    state.draft = {};
    state.tab = 'card';
    render();
  };

  window.portalPartnerCancel = function(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      portalPartnerBack();
      return;
    }
    state.mode = 'view';
    render();
  };

  window.portalPartnerSave = async function(){
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      render();
      return;
    }
    try {
      const card = creating
        ? await api('POST', '/api/' + state.kind, state.draft)
        : await api('PUT', '/api/' + state.kind + '/' + state.id, state.draft);
      state.mode = 'view';
      state.id = card[KINDS[state.kind].key];
      state.row = card;
      state.addresses = card.addresses || [];
      state.draft = {};
      await reload(state.kind);
      render();
      say(creating ? 'Карточка создана' : 'Изменения сохранены');
    } catch (e){
      say(e.message);
    }
  };

  window.portalPartnerArchive = async function(archived){
    try {
      const card = await api('POST', '/api/' + state.kind + '/' + state.id + '/archive',
          {active: archived});
      state.row = card;
      state.addresses = card.addresses || state.addresses;
      await reload(state.kind);
      render();
      say(archived ? 'Контрагент возвращён в работу' : 'Контрагент переведён в архив');
    } catch (e){
      say(e.message);
    }
  };

  window.portalPartnerDelete = async function(){
    if (!window.confirm('Удалить «' + (state.row.name || '') + '» без возможности восстановления?\n'
        + 'Если контрагент указан в документах, портал откажет и предложит архив.')) return;
    try {
      await api('DELETE', '/api/' + state.kind + '/' + state.id);
      await reload(state.kind);
      say('Карточка удалена');
      portalPartnerBack();
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Адреса ================= */

  window.portalAddressAdd = function(){
    state.addressForm = {addressId: null, values: {}};
    render();
  };

  window.portalAddressEdit = function(id){
    const a = state.addresses.find(x => x.addressId === id);
    if (!a) return;
    state.addressForm = {addressId: id, values: {address: a.address, sortOrder: a.sortOrder}};
    render();
  };

  window.portalAddressCancel = function(){
    state.addressForm = null;
    render();
  };

  window.portalAddressSave = async function(){
    const form = state.addressForm;
    try {
      state.addresses = form.addressId
        ? await api('PUT', '/api/addresses/' + form.addressId, form.values)
        : await api('POST', '/api/consignees/' + state.id + '/addresses', form.values);
      state.addressForm = null;
      await reload('consignees');
      render();
      say('Адрес сохранён');
    } catch (e){
      say(e.message);
    }
  };

  window.portalAddressDefault = async function(id){
    try {
      state.addresses = await api('POST', '/api/addresses/' + id + '/default');
      await reload('consignees');
      render();
    } catch (e){
      say(e.message);
    }
  };

  window.portalAddressDelete = async function(id){
    const a = state.addresses.find(x => x.addressId === id);
    if (!window.confirm('Удалить адрес «' + (a ? a.address : id) + '»?')) return;
    try {
      state.addresses = await api('DELETE', '/api/addresses/' + id);
      await reload('consignees');
      render();
      say('Адрес удалён');
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Списки ================= */

  async function reload(kindName){
    try {
      PORTAL.catalog[kindName + 'All'] = await api('GET', '/api/' + kindName + '?withArchived=true');
      PORTAL.catalogError[kindName] = null;
    } catch (e){
      PORTAL.catalog[kindName + 'All'] = PORTAL.catalog[kindName + 'All'] || [];
      PORTAL.catalogError[kindName] = e.message;
    }
    renderList(kindName);
  }

  function visible(kindName){
    const show = document.getElementById(kindName + 'ShowArchived')?.checked;
    return (PORTAL.catalog[kindName + 'All'] || []).filter(r => show || r.isActive !== false);
  }

  function renderList(kindName){
    const kind = KINDS[kindName];
    const body = document.getElementById(kind.body);
    if (!body) return;
    let rows = visible(kindName);
    PORTAL.catalog[kindName] = rows;

    if (typeof fcDirSortArray === 'function'){
      rows = fcDirSortArray(kindName, rows.slice(), r => r[dirSortState[kindName].col] ?? '');
    }
    const counter = document.getElementById(kind.count);
    if (counter) counter.textContent = '(' + rows.length + ')';

    const cell = v => '<td>' + esc(dash(v)) + '</td>';
    body.innerHTML = rows.length
      ? rows.map(r => {
          const id = r[kind.key];
          const archived = r.isActive === false
            ? ' <span class="count">в архиве</span>' : '';
          return '<tr style="cursor:pointer;" onclick="portalOpenPartner(\'' + kindName + '\',\''
            + id + '\')"><td><strong>' + esc(dash(r.code)) + '</strong>' + archived + '</td>'
            + cell(r.name)
            + (kind.addresses
                ? '<td>' + esc(dash(r.defaultAddress))
                  + (r.addressCount > 1 ? ' <span class="count">+' + (r.addressCount - 1) + '</span>' : '')
                  + '</td>'
                : '')
            + cell(r.bin) + cell(r.legalAddress) + cell(r.contactName)
            + cell(r.contactPhone) + cell(r.contactEmail) + '</tr>';
        }).join('')
      : '<tr><td colspan="' + kind.columns + '" style="color:var(--muted);">'
        + (PORTAL.catalogError[kindName] ? 'Данные недоступны' : 'Справочник пуст') + '</td></tr>';
  }

  window.portalPartnerArchiveFilter = renderList;

  /** Кнопка создания и флажок архива в шапке списка. */
  function decorateList(kindName){
    const head = document.querySelector('#ref-sub-' + kindName + ' .panel-head');
    if (!head || document.getElementById(kindName + 'CreateBtn')) return;
    let actions = head.querySelector('.actions');
    if (!actions){
      actions = document.createElement('div');
      actions.className = 'actions';
      head.appendChild(actions);
    }
    const label = kindName === 'suppliers' ? '+ Создать поставщика' : '+ Создать грузополучателя';
    actions.insertAdjacentHTML('afterbegin',
        '<label style="font-size:13px; color:var(--muted); margin-right:12px;">'
      + '<input type="checkbox" id="' + kindName + 'ShowArchived"'
      + ' onchange="portalPartnerArchiveFilter(\'' + kindName + '\')"/> показывать архивные</label>'
      + '<button class="btn primary" id="' + kindName + 'CreateBtn"'
      + ' onclick="portalPartnerCreate(\'' + kindName + '\')">' + label + '</button>');
  }

  function install(){
    window.renderSuppliers = () => renderList('suppliers');
    window.renderConsignees = () => renderList('consignees');

    PORTAL.onLogin(() => {
      ensureView();
      if (mayEdit()){
        decorateList('suppliers');
        decorateList('consignees');
      }
      reload('suppliers').catch(() => {});
      reload('consignees').catch(() => {});
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


PATCH_PORTALDIR = r"""
<!-- ===== ЭТАП 7в: справочники уровня портала =====
     Склады, поклажедатели, типы заказов и типы приходных документов.
     Общие для всех клиентов, поэтому доступны только администратору:
     у остальных экраны остаются такими же, какими были, — просто
     без кнопок.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const isAdmin = () => ['admin', 'developer'].includes(PORTAL.session && PORTAL.session.roleCode);
  // Разработчик смотрит, но не правит: раздел ему открыт для разбора,
  // а не для ведения справочников.
  const mayEdit = () => PORTAL.session && PORTAL.session.roleCode === 'admin';

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  /* ================= Два справочника с карточкой ================= */

  const DIRS = {
    warehousesDir: {
      title: 'Склад', newTitle: 'Новый склад',
      list: '/api/admin/warehouse-directory', base: '/api/admin/warehouses',
      key: 'warehouseId', body: 'warehousesDirBody', count: 'warehousesDirCount',
      // идентификатор склада = его код в WMS, задаётся один раз при создании
      idField: {col: 'warehouse_id', readCol: 'warehouseId', label: 'Идентификатор в WMS'},
      fields: [
        {col: 'code', label: 'Код', required: true},
        {col: 'name', label: 'Наименование склада', required: true},
        {col: 'address', label: 'Адрес'},
        {col: 'responsible', label: 'Ответственный'},
        {col: 'phone', label: 'Телефон'},
        {col: 'email', label: 'Email'}
      ],
      columns: ['code', 'name', 'address', 'responsible', 'phone', 'email']
    },
    depositorsDir: {
      title: 'Поклажедатель', newTitle: 'Новый поклажедатель',
      list: '/api/admin/depositor-directory', base: '/api/admin/depositor-directory',
      key: 'depositorId', body: 'depositorsDirBody', count: 'depositorsDirCount',
      // поклажедателя портал принимает анкетой целиком, а не набором колонок
      asRecord: true,
      fields: [
        {col: 'code', label: 'Код', required: true},
        {col: 'orgName', label: 'Наименование организации', required: true},
        {col: 'bin', label: 'БИН организации'},
        {col: 'legalAddress', label: 'Юр. адрес'},
        {col: 'phone', label: 'Телефон'},
        {col: 'email', label: 'Email'},
        {col: 'contractNumber', label: 'Номер договора'},
        {col: 'contractValidUntil', label: 'Срок действия договора', date: true}
      ],
      columns: ['code', 'orgName', 'bin', 'legalAddress', 'phone', 'email',
                'contractNumber', 'contractValidUntil']
    },
    operatorsDir: {
      title: 'Организация оператора', newTitle: 'Новая организация',
      list: '/api/admin/operator-directory', base: '/api/admin/operator-directory',
      key: 'operatorId', body: 'operatorsDirBody', count: 'operatorsDirCount',
      asRecord: true,
      fields: [
        {col: 'code', label: 'Код организации', required: true},
        {col: 'name', label: 'Наименование организации', required: true},
        {col: 'bin', label: 'БИН организации'},
        {col: 'legalAddress', label: 'Юр. адрес'},
        {col: 'bankName', label: 'Банк'},
        {col: 'bankAccount', label: 'Счёт'},
        {col: 'phone', label: 'Телефон'},
        {col: 'email', label: 'Email'},
        // страницы портала — абзацы текста, им нужна не строка ввода
        {col: 'aboutContent', label: 'Страница «О нас»', long: true},
        {col: 'contactsContent', label: 'Страница «Контакты»', long: true}
      ],
      columns: ['code', 'name', 'bin', 'bankName', 'bankAccount', 'phone', 'email']
    }
  };

  const state = {dir: null, mode: 'view', id: null, row: null, draft: {}};
  PORTAL.portalDir = state;

  function ensureView(){
    if (document.getElementById('view-portaldir-detail')) return;
    const anchor = document.getElementById('view-sku-detail');
    if (!anchor) return;
    const section = document.createElement('section');
    section.className = 'view';
    section.id = 'view-portaldir-detail';
    section.innerHTML =
        '<div class="panel-head">'
      + '<button class="back-btn" onclick="portalDirBack()">← Назад к списку</button>'
      + '<div class="actions" id="portalDirActions"></div></div>'
      + '<div class="detail-head"><h2 id="portalDirTitle" style="margin:0 0 16px;"></h2></div>'
      + '<div id="portalDirBody" style="margin-top:16px;"></div>';
    anchor.parentNode.insertBefore(section, anchor.nextSibling);
  }

  function value(field){
    if (Object.prototype.hasOwnProperty.call(state.draft, field.col)) return state.draft[field.col];
    // Отправляем поле под именем колонки базы, а приходит оно в camelCase:
    // readCol говорит, где его искать в ответе.
    return (state.row || {})[field.readCol || field.col];
  }

  window.portalDirField = function(el){
    state.draft[el.getAttribute('data-col')] = el.value === '' ? null : el.value;
  };

  function fieldHtml(field, readOnly){
    const v = value(field);
    if (readOnly){
      if (field.long){
        return '<div class="kv" style="grid-column:1/-1;"><label>' + esc(field.label)
          + '</label><div style="white-space:pre-wrap;">' + esc(dash(v)) + '</div></div>';
      }
      return '<div class="kv"><label>' + esc(field.label) + '</label><div>'
        + esc(field.date ? ruDate(v) : dash(v)) + '</div></div>';
    }
    // Содержимое страниц «О нас» и «Контакты» — это абзацы текста,
    // в одну строку ввода они не помещаются.
    if (field.long){
      return '<div class="kv" style="grid-column:1/-1;"><label>' + esc(field.label)
        + '</label><div><textarea class="kv-input" rows="6"'
        + ' style="width:100%; resize:vertical; font-family:inherit;"'
        + ' data-col="' + field.col + '" oninput="portalDirField(this)">'
        + esc(v === null || v === undefined ? '' : v) + '</textarea></div></div>';
    }
    return '<div class="kv"><label>' + esc(field.label) + (field.required ? ' *' : '')
      + '</label><div style="min-width:320px;"><input class="kv-input" type="'
      + (field.date ? 'date' : 'text') + '" value="'
      + esc(v === null || v === undefined ? '' : (field.date ? String(v).slice(0, 10) : v))
      + '" data-col="' + field.col + '" oninput="portalDirField(this)"/></div></div>';
  }

  function render(){
    ensureView();
    const dir = DIRS[state.dir];
    const editing = state.mode !== 'view';
    const archived = state.row && state.row.isActive === false;
    document.getElementById('portalDirTitle').innerHTML =
        esc(state.mode === 'create' ? dir.newTitle
            : (state.row && (state.row.name || state.row.orgName)) || dir.title)
      + (archived ? ' <span class="count">(в архиве)</span>' : '');

    let actions = '';
    if (editing){
      actions = '<button class="btn primary" onclick="portalDirSave()">Сохранить</button>'
        + '<button class="btn ghost" onclick="portalDirCancel()">Отмена</button>';
    } else if (mayEdit() && state.id){
      actions = '<button class="btn primary" onclick="portalDirEdit()">Редактировать</button>'
        + '<button class="btn" onclick="portalDirArchive(' + (archived ? 'true' : 'false') + ')">'
        + (archived ? 'Вернуть из архива' : 'В архив') + '</button>'
        + '<button class="btn ghost" onclick="portalDirDelete()">Удалить</button>';
    }
    document.getElementById('portalDirActions').innerHTML = actions;

    const fields = [];
    if (dir.idField){
      // идентификатор задаётся только при создании: по нему приходят
      // документы обмена, и менять его задним числом нельзя
      fields.push(Object.assign({}, dir.idField, {required: true}));
    }
    dir.fields.forEach(f => fields.push(f));

    let html = '<div class="kv-grid">' + fields.map(f =>
        fieldHtml(f, !editing || (dir.idField && f.col === dir.idField.col && state.mode !== 'create'))
      ).join('') + '</div>';
    if (state.row && state.row.userCount !== undefined && !editing){
      html += '<div style="color:var(--muted); font-size:13px; margin-top:12px;">'
        + 'Пользователей с доступом: ' + state.row.userCount + '</div>';
    }
    if (editing){
      html += '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
        + 'Поля со звёздочкой обязательны.</div>';
    }
    document.getElementById('portalDirBody').innerHTML = html;
    showView('portaldir-detail');
  }

  window.portalDirBack = function(){
    // Организации оператора лежат в администрировании, остальные
    // справочники — в разделе «Справочники». Возврат должен попадать
    // туда, откуда человек пришёл.
    if (state.dir === 'operatorsDir'){
      showTab('admin');
      if (typeof showAdminSub === 'function') showAdminSub('operator');
      return;
    }
    showTab('references');
    showReferencesSub(state.dir);
  };

  window.portalOpenPortalDir = async function(dirName, id){
    try {
      state.dir = dirName;
      state.mode = 'view';
      state.draft = {};
      state.row = await api('GET', DIRS[dirName].base + '/' + encodeURIComponent(id));
      state.id = id;
      render();
    } catch (e){
      say('Не удалось открыть карточку: ' + e.message);
    }
  };

  window.portalDirCreate = function(dirName){
    state.dir = dirName;
    state.mode = 'create';
    state.id = null;
    state.row = {};
    state.draft = {};
    render();
  };

  window.portalDirEdit = function(){
    state.mode = 'edit';
    state.draft = {};
    render();
  };

  window.portalDirCancel = function(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      portalDirBack();
      return;
    }
    state.mode = 'view';
    render();
  };

  /**
   * Анкета уходит целиком: сервер принимает запись, а не набор полей.
   *
   * Поля берём у открытого справочника, а не у поклажедателя: раньше
   * список был прибит к DIRS.depositorsDir, и у организации оператора
   * уезжали чужие поля — страницы «О нас» и «Контакты» не сохранялись,
   * потому что их в том списке просто нет.
   */
  function recordBody(){
    const out = {};
    (DIRS[state.dir].fields || []).forEach(f => {
      const v = Object.prototype.hasOwnProperty.call(state.draft, f.col)
        ? state.draft[f.col] : (state.row || {})[f.readCol || f.col];
      out[f.col] = v === undefined ? null : v;
    });
    return out;
  }

  window.portalDirSave = async function(){
    const dir = DIRS[state.dir];
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      render();
      return;
    }
    try {
      const body = dir.asRecord ? recordBody() : state.draft;
      const card = creating
        ? await api('POST', dir.base, body)
        : await api('PUT', dir.base + '/' + encodeURIComponent(state.id), body);
      state.mode = 'view';
      state.row = card;
      state.id = card[dir.key];
      state.draft = {};
      await reload(state.dir);
      render();
      say(creating ? 'Карточка создана' : 'Изменения сохранены');
    } catch (e){
      say(e.message);
    }
  };

  window.portalDirArchive = async function(archived){
    try {
      state.row = await api('POST', DIRS[state.dir].base + '/'
          + encodeURIComponent(state.id) + '/archive', {active: archived});
      await reload(state.dir);
      render();
      say(archived ? 'Возвращено в работу' : 'Переведено в архив');
    } catch (e){
      say(e.message);
    }
  };

  window.portalDirDelete = async function(){
    const name = (state.row && (state.row.name || state.row.orgName)) || '';
    if (!window.confirm('Удалить «' + name + '» без возможности восстановления?\n'
        + 'Если запись где-то используется, портал откажет и предложит архив.')) return;
    try {
      await api('DELETE', DIRS[state.dir].base + '/' + encodeURIComponent(state.id));
      await reload(state.dir);
      say('Запись удалена');
      portalDirBack();
    } catch (e){
      say(e.message);
    }
  };

  /* ---------------- Списки ---------------- */

  async function reload(dirName){
    const dir = DIRS[dirName];
    try {
      PORTAL.catalog[dirName] = await api('GET', dir.list);
      PORTAL.catalogError[dirName] = null;
    } catch (e){
      PORTAL.catalog[dirName] = PORTAL.catalog[dirName] || [];
      PORTAL.catalogError[dirName] = e.message;
    }
    renderDir(dirName);
  }

  function renderDir(dirName){
    const dir = DIRS[dirName];
    const body = document.getElementById(dir.body);
    if (!body) return;
    const show = document.getElementById(dirName + 'ShowArchived')?.checked;
    let rows = (PORTAL.catalog[dirName] || []).filter(r => show || r.isActive !== false);
    if (typeof fcDirSortArray === 'function'){
      rows = fcDirSortArray(dirName, rows.slice(), r => r[dirSortState[dirName].col] ?? '');
    }
    const counter = document.getElementById(dir.count);
    if (counter) counter.textContent = '(' + rows.length + ')';

    body.innerHTML = rows.length
      ? rows.map(r => '<tr style="cursor:pointer;" onclick="portalOpenPortalDir(\'' + dirName
          + '\',\'' + esc(r[dir.key]) + '\')">'
          + dir.columns.map((c, i) => {
              const v = c === 'contractValidUntil' ? ruDate(r[c]) : dash(r[c]);
              const mark = (i === 0 && r.isActive === false)
                ? ' <span class="count">в архиве</span>' : '';
              return '<td>' + (i === 0 ? '<strong>' + esc(v) + '</strong>' : esc(v)) + mark + '</td>';
            }).join('')
          + '</tr>').join('')
      : '<tr><td colspan="' + dir.columns.length + '" style="color:var(--muted);">'
        + (PORTAL.catalogError[dirName] ? 'Данные недоступны' : 'Справочник пуст') + '</td></tr>';
  }

  window.portalDirArchiveFilter = renderDir;

  function decorateList(dirName, label){
    const head = document.querySelector('#ref-sub-' + dirName + ' .panel-head');
    if (!head || document.getElementById(dirName + 'CreateBtn')) return;
    let actions = head.querySelector('.actions');
    if (!actions){
      actions = document.createElement('div');
      actions.className = 'actions';
      head.appendChild(actions);
    }
    actions.insertAdjacentHTML('afterbegin',
        '<label style="font-size:13px; color:var(--muted); margin-right:12px;">'
      + '<input type="checkbox" id="' + dirName + 'ShowArchived"'
      + ' onchange="portalDirArchiveFilter(\'' + dirName + '\')"/> показывать архивные</label>'
      + '<button class="btn primary" id="' + dirName + 'CreateBtn"'
      + ' onclick="portalDirCreate(\'' + dirName + '\')">' + label + '</button>');
  }

  /* ================= Типы заказов и документов ================= */

  // Это короткие перечисления, карточка им не нужна: правка идёт прямо
  // в строке списка — так быстрее и видно сразу все значения.
  const ENUMS = {
    orderTypes: {path: 'order-types', body: 'orderTypesBody', title: 'тип заказа'},
    docTypes: {path: 'receipt-doc-types', body: 'docTypesBody', title: 'тип документа'}
  };
  const enumState = {rows: {}, form: null};
  PORTAL.portalEnums = enumState;

  async function reloadEnum(name){
    const e = ENUMS[name];
    try {
      enumState.rows[name] = await api('GET', '/api/admin/' + e.path);
    } catch (err){
      enumState.rows[name] = [];
    }
    renderEnum(name);
  }

  window.portalEnumField = function(el){
    enumState.form.values[el.getAttribute('data-enum')] = el.value === '' ? null : el.value;
  };

  function renderEnum(name){
    const body = document.getElementById(ENUMS[name].body);
    if (!body) return;
    const head = body.parentNode.querySelector('thead tr');
    const canEdit = mayEdit();
    if (head){
      head.innerHTML = '<th>Код</th><th>Наименование</th><th class="num">Порядок</th>'
        + '<th>Используется</th>' + (canEdit ? '<th></th>' : '');
    }
    const rows = enumState.rows[name] || [];
    const form = enumState.form && enumState.form.name === name ? enumState.form : null;

    const line = r => {
      if (form && form.id === r.id){
        return '<tr>' + inputCell('code', form.values.code) + inputCell('name', form.values.name)
          + inputCell('sortOrder', form.values.sortOrder, true)
          + '<td>' + (r.isActive ? 'да' : 'нет') + '</td>'
          + '<td style="white-space:nowrap;">'
          + '<button class="btn primary" onclick="portalEnumSave()">Сохранить</button> '
          + '<button class="btn ghost" onclick="portalEnumCancel()">Отмена</button></td></tr>';
      }
      return '<tr><td><strong>' + esc(r.code) + '</strong></td><td>' + esc(r.name)
        + '</td><td class="num">' + dash(r.sortOrder) + '</td>'
        + '<td>' + (r.isActive ? 'да' : '<span class="count">отключён</span>') + '</td>'
        + (canEdit ? '<td style="white-space:nowrap;">'
            + '<button class="btn ghost" onclick="portalEnumEdit(\'' + name + '\',' + r.id + ')">Изменить</button> '
            + '<button class="btn ghost" onclick="portalEnumToggle(\'' + name + '\',' + r.id + ')">'
            + (r.isActive ? 'Отключить' : 'Включить') + '</button> '
            + '<button class="btn ghost" onclick="portalEnumDelete(\'' + name + '\',' + r.id + ')">Удалить</button></td>' : '')
        + '</tr>';
    };

    let html = rows.map(line).join('');
    if (form && form.id === null){
      html += '<tr>' + inputCell('code', form.values.code) + inputCell('name', form.values.name)
        + inputCell('sortOrder', form.values.sortOrder, true) + '<td>да</td>'
        + '<td style="white-space:nowrap;">'
        + '<button class="btn primary" onclick="portalEnumSave()">Сохранить</button> '
        + '<button class="btn ghost" onclick="portalEnumCancel()">Отмена</button></td></tr>';
    }
    body.innerHTML = html || '<tr><td colspan="5" style="color:var(--muted);">Справочник пуст</td></tr>';

    if (canEdit){
      const panel = document.querySelector('#ref-sub-' + name + ' .panel-head');
      if (panel && !document.getElementById(name + 'CreateBtn')){
        let actions = panel.querySelector('.actions');
        if (!actions){
          actions = document.createElement('div');
          actions.className = 'actions';
          panel.appendChild(actions);
        }
        actions.insertAdjacentHTML('afterbegin',
            '<button class="btn primary" id="' + name + 'CreateBtn" onclick="portalEnumAdd(\''
            + name + '\')">+ Добавить</button>');
      }
    }
  }

  function inputCell(col, v, num){
    return '<td><input class="kv-input" type="' + (num ? 'number' : 'text') + '" value="'
      + esc(v === null || v === undefined ? '' : v) + '" data-enum="' + col
      + '" oninput="portalEnumField(this)"/></td>';
  }

  window.portalEnumAdd = function(name){
    enumState.form = {name: name, id: null, values: {}};
    renderEnum(name);
  };

  window.portalEnumEdit = function(name, id){
    const r = (enumState.rows[name] || []).find(x => x.id === id);
    if (!r) return;
    enumState.form = {name: name, id: id,
      values: {code: r.code, name: r.name, sortOrder: r.sortOrder}};
    renderEnum(name);
  };

  window.portalEnumCancel = function(){
    const name = enumState.form.name;
    enumState.form = null;
    renderEnum(name);
  };

  window.portalEnumSave = async function(){
    const form = enumState.form;
    const e = ENUMS[form.name];
    const body = {code: form.values.code, name: form.values.name,
      sortOrder: form.values.sortOrder === null || form.values.sortOrder === undefined
        ? null : Number(form.values.sortOrder)};
    try {
      enumState.rows[form.name] = form.id === null
        ? await api('POST', '/api/admin/' + e.path, body)
        : await api('PUT', '/api/admin/' + e.path + '/' + form.id,
            Object.assign({}, body, {isActive: true}));
      enumState.form = null;
      renderEnum(form.name);
      say('Сохранено');
    } catch (err){
      say(err.message);
    }
  };

  window.portalEnumToggle = async function(name, id){
    const r = (enumState.rows[name] || []).find(x => x.id === id);
    try {
      enumState.rows[name] = await api('PUT', '/api/admin/' + ENUMS[name].path + '/' + id,
          {code: r.code, name: r.name, sortOrder: r.sortOrder, isActive: !r.isActive});
      renderEnum(name);
    } catch (err){
      say(err.message);
    }
  };

  window.portalEnumDelete = async function(name, id){
    const r = (enumState.rows[name] || []).find(x => x.id === id);
    if (!window.confirm('Удалить «' + (r ? r.name : id) + '»?')) return;
    try {
      enumState.rows[name] = await api('DELETE', '/api/admin/' + ENUMS[name].path + '/' + id);
      renderEnum(name);
      say('Удалено');
    } catch (err){
      say(err.message);
    }
  };

  /* ========= Организации оператора в администрировании =========
     В разметке демо такой подвкладки нет: таблица operator создавалась
     под решение Р2, а экрана у неё не было. Поэтому кнопку и панель
     добавляем сами, рядом с остальными подвкладками администрирования.
  ============================================================= */

  function ensureOperatorSub(){
    const tabs = document.querySelector('#view-admin .admin-subtabs');
    if (!tabs || document.getElementById('admin-sub-operator')) return;

    const btn = document.createElement('button');
    btn.className = 'admin-subtab-btn';
    btn.setAttribute('data-sub', 'operator');
    btn.setAttribute('data-i18n', 'admin.title.operator_organizations');
    btn.textContent = 'Организации оператора';
    btn.onclick = () => showAdminSub('operator');
    tabs.appendChild(btn);

    const pane = document.createElement('div');
    pane.className = 'admin-sub';
    pane.id = 'admin-sub-operator';
    pane.innerHTML =
        '<div class="panel-head" style="margin-top:0;">'
      + '<div class="section-title" style="margin:0;" data-i18n="admin.title.operator_organizations">'
      + 'Организации оператора</div>'
      + '<div class="actions" id="operatorsDirActions"></div></div>'
      + '<div class="hint" data-i18n="admin.hint.operator_directory"'
      + ' style="margin:-4px 0 12px;">'
      + 'Реквизиты организаций оператора и содержимое страниц «О нас» и «Контакты». '
      + 'Логотип появится вместе с файловым хранилищем.</div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th data-i18n="admin.column.organization_code">Код организации</th>'
      + '<th data-i18n="admin.column.organization_name">Наименование организации</th>'
      + '<th data-i18n="references.column.company_bin">БИН организации</th>'
      + '<th data-i18n="admin.column.bank">Банк</th>'
      + '<th data-i18n="admin.column.bank_account">Счёт</th>'
      + '<th data-i18n="portal.text.phone">Телефон</th>'
      + '<th data-i18n="common.column.email">Email</th>'
      + '</tr></thead><tbody id="operatorsDirBody"></tbody></table></div>'
      + '<div class="count" id="operatorsDirCount" style="margin-top:8px;"></div>';
    const host = document.getElementById('view-admin');
    if (host) host.appendChild(pane);

    if (mayEdit() && !document.getElementById('operatorsDirCreateBtn')){
      const create = document.createElement('button');
      create.className = 'btn primary';
      create.id = 'operatorsDirCreateBtn';
      create.type = 'button';
      create.setAttribute('data-i18n', 'admin.button.create_organization');
      create.textContent = '+ Создать организацию';
      create.onclick = () => portalDirCreate('operatorsDir');
      document.getElementById('operatorsDirActions').appendChild(create);
    }
  }

  /* ================= Установка ================= */

  function install(){
    window.renderWarehousesDir = () => renderDir('warehousesDir');
    window.renderDepositorsDir = () => renderDir('depositorsDir');
    window.renderOperatorsDir = () => renderDir('operatorsDir');
    window.renderOrderTypes = () => renderEnum('orderTypes');
    window.renderDocTypes = () => renderEnum('docTypes');

    PORTAL.onLogin(() => {
      // Раздел открыт только администратору и разработчику; остальным
      // эти экраны и так не показываются (showReferencesSub).
      if (!isAdmin()) return;
      ensureView();
      if (mayEdit()){
        decorateList('warehousesDir', '+ Создать склад');
        decorateList('depositorsDir', '+ Создать поклажедателя');
      }
      ensureOperatorSub();
      reload('warehousesDir').catch(() => {});
      reload('depositorsDir').catch(() => {});
      reload('operatorsDir').catch(() => {});
      reloadEnum('orderTypes').catch(() => {});
      reloadEnum('docTypes').catch(() => {});
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

PATCH_COLUMNS = r"""
<!-- ===== Справочник колонок (V29) =====
     Выбор колонок переезжает на сервер: он теперь живёт в учётной записи,
     а не в памяти браузера, и едет за человеком с компьютера на компьютер,
     как язык. Подписи приходят оттуда же — уже на языке пользователя
     и уже с учётом того, как клиент переименовал поле.

     Окно «⚙ Колонки» не переписывается: прототип его уже рисует, а мы
     подменяем источник данных и добавляем сохранение. Переписать значило
     бы получить второе окно с теми же правилами и расхождения между ними.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  // Раскладка с сервера: таблица -> [{columnId, label, isVisible, sortOrder, isPinned}]
  //
  // isPinned — «из таблицы убрать нельзя» (V36). Это не то же, что
  // «поклажедателю скрыть нельзя»: служебные поля товара клиенту
  // скрыть нельзя, но себе в таблице человек их убирает свободно.
  const layout = {};
  // raw — какие колонки сервер вообще отдал по таблице, до отбрасывания
  // тех, которых нет в вёрстке. Именно это и есть «доступно клиенту»:
  // запрещённую колонку table_columns не отдаёт совсем.
  const raw = {};
  PORTAL.columns = {layout: layout, raw: raw};

  /* ============ Применение раскладки к состоянию прототипа ============ */

  // Видимые идут первыми в своём порядке, за ними скрытые: так выглядит
  // список в окне выбора, и так же человек ожидает его увидеть.
  function ordered(rows){
    const vis = rows.filter(r => r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    const hid = rows.filter(r => !r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    return vis.concat(hid);
  }

  // tableColumnState и соседи объявлены в прототипе через const на верхнем
  // уровне. Такие объявления — лексические глобали: свойства у window для
  // них не создаётся, и обращение через window.tableColumnState даёт
  // undefined. Поэтому читаем по голому имени, прикрываясь typeof.
  const есть = name => {
    try { return eval('typeof ' + name) !== 'undefined'; } catch (e){ return false; }
  };

  function applyTable(tableKey, rows){
    raw[tableKey] = rows.map(r => r.columnId);
    if (!есть('tableColumnState') || !tableColumnState[tableKey]) return;
    const known = new Set((tableColumns[tableKey] || []).map(c => c.id));
    // колонка могла пропасть из вёрстки, но остаться в базе — рисовать
    // её нечем, поэтому ориентируемся на то, что умеет прототип
    const rowsHere = ordered(rows.filter(r => known.has(r.columnId)));
    if (!rowsHere.length) return;

    layout[tableKey] = rowsHere;
    tableColumnState[tableKey] = rowsHere.map(r => r.columnId);
    tableColumnHidden[tableKey] = new Set(
        rowsHere.filter(r => !r.isVisible).map(r => r.columnId));
    // tableColumnLocked НЕ трогаем. В прототипе это значит «не рисовать
    // эту колонку в таблице» (renderTableHead и renderTableRow её
    // отфильтровывают), а вовсе не «нельзя скрыть». Когда в V36
    // появилось прикрепление и я записал прикреплённые сюда, коммерческий
    // код, наименование и единица пропали и из списка товаров, и из окна
    // выбора колонок. Запрет «эти не убрать» живёт в обработчике
    // сохранения (он дописывает их обратно) и в серой галочке окна.
  }

  function applyAll(rows){
    const byTable = {};
    rows.forEach(r => { (byTable[r.tableKey] = byTable[r.tableKey] || []).push(r); });
    Object.keys(byTable).forEach(t => applyTable(t, byTable[t]));
  }

  /** Подпись колонки из раскладки: в ней уже и перевод, и переименование. */
  function labelOf(tableKey, columnId){
    const rows = layout[tableKey];
    if (!rows) return null;
    const row = rows.find(r => r.columnId === columnId);
    return row ? row.label : null;
  }

  // Перерисовка таблицы после смены раскладки. Имена функций берём
  // у прототипа: каждая таблица рисуется своей.
  const RENDER = {
    receipts: 'renderReceipts', orders: 'renderOrders', transfers: 'renderTransfers',
    stock: 'renderStock', stockBatch: 'renderStockBatchTable', sku: 'renderSku',
    receiptComposition: 'renderReceiptComposition',
    orderComposition: 'renderOrderComposition',
    transferComposition: 'renderTransferComposition',
    receiptLineDetail: 'renderReceiptLineDetailTable',
    orderLineDetail: 'renderOrderLineDetailTable',
    transferLineDetail: 'renderTransferLineDetailTable'
  };

  // Таблица может быть на скрытом экране, и её отрисовка падает на
  // отсутствующем элементе. Одна такая ошибка не должна обрывать
  // остальные — та же беда уже была при смене языка.
  function redraw(tableKey){
    const name = RENDER[tableKey];
    if (!name || typeof window[name] !== 'function') return;
    try {
      window[name]();
      if (typeof updateTopScrollWidth === 'function') updateTopScrollWidth(tableKey);
    } catch (e){ /* экран не открыт — перерисуется при открытии */ }
  }

  function redrawAll(){ Object.keys(layout).forEach(redraw); }

  async function reload(tableKey){
    const url = tableKey ? '/api/table-columns/' + encodeURIComponent(tableKey)
                         : '/api/table-columns';
    const rows = await api('GET', url);
    if (tableKey) applyTable(tableKey, rows); else applyAll(rows);
    return rows;
  }
  PORTAL.columns.reload = reload;

  /* ============ Сохранение выбора ============ */

  function wrapPicker(){
    const applyOrig = window.applyColumnPicker;
    if (typeof applyOrig === 'function'){
      window.applyColumnPicker = function(){
        // прочитать ДО вызова оригинала: он закрывает окно и обнуляет обе
        const tableKey = есть('colPickerTable') ? colPickerTable : null;
        const draft = есть('colPickerDraft') ? colPickerDraft : null;
        // прототип сначала применяет выбор у себя — экран меняется сразу,
        // не дожидаясь ответа сервера
        applyOrig.apply(this, arguments);
        if (!tableKey || !draft) return;
        const chosen = draft.order.filter(id => !draft.hidden.has(id));
        const locked = (layout[tableKey] || []).filter(r => r.isPinned).map(r => r.columnId);
        locked.forEach(id => { if (!chosen.includes(id)) chosen.push(id); });
        if (!chosen.length){
          say('Оставьте хотя бы одну колонку');
          reload(tableKey).then(() => redraw(tableKey)).catch(() => {});
          return;
        }
        api('PUT', '/api/table-columns/' + encodeURIComponent(tableKey), {columns: chosen})
          .then(rows => { applyTable(tableKey, rows); redraw(tableKey); })
          .catch(err => {
            say('Колонки показаны, но не сохранились' + ': ' + err.message);
            reload(tableKey).then(() => redraw(tableKey)).catch(() => {});
          });
      };
    }

    // Прикреплённую колонку снять нельзя — галочка серая и отмечена.
    // Запрет работает и без этого (обработчик сохранения дописывает её
    // обратно), но тогда человек снимает галочку, нажимает «Применить»
    // и видит колонку на месте — выглядит как сломанное окно.
    const renderOrig = window.renderColumnPickerList;
    if (typeof renderOrig === 'function'){
      window.renderColumnPickerList = function(){
        renderOrig.apply(this, arguments);
        const tableKey = есть('colPickerTable') ? colPickerTable : null;
        if (!tableKey) return;
        const pinned = new Set((layout[tableKey] || [])
            .filter(r => r.isPinned).map(r => r.columnId));
        if (!pinned.size) return;
        const list = document.getElementById('colList');
        if (!list) return;
        [...list.querySelectorAll('.col-item')].forEach(el => {
          const cb = el.querySelector('input[type=checkbox]');
          const onchange = cb && cb.getAttribute('onchange') || '';
          const m = onchange.match(/toggleColVisibility\('([^']+)'\)/);
          if (!m || !pinned.has(m[1])) return;
          cb.checked = true;
          cb.disabled = true;
          el.title = T('references.hint.column_cannot_be_hidden');
          el.style.color = 'var(--muted)';
        });
      };
    }

    // «Сбросить» теперь значит «вернуть настройки по умолчанию», а не
    // «показать всё»: именно этого человек от кнопки и ждёт. Действие
    // применяется сразу, поэтому окно закрывается — иначе осталась бы
    // кнопка «Применить», которая записала бы умолчания как личный выбор.
    window.resetColumnPicker = function(){
      const tableKey = есть('colPickerTable') ? colPickerTable : null;
      if (!tableKey) return;
      api('DELETE', '/api/table-columns/' + encodeURIComponent(tableKey))
        .then(rows => {
          applyTable(tableKey, rows);
          redraw(tableKey);
          if (typeof closeColumnPicker === 'function') closeColumnPicker();
          say('Колонки возвращены к настройкам по умолчанию');
        })
        .catch(err => say(err.message));
    };
  }

  /* ============ Подписи ============ */

  function wrapLabels(){
    // COLTH зовут шапки таблиц в карточках документов и регистрах
    const colthOrig = window.COLTH;
    window.COLTH = function(c){
      if (c && c.id){
        for (const t of Object.keys(layout)){
          if ((tableColumns[t] || []).some(x => x === c)){
            const own = labelOf(t, c.id);
            if (own) return own;
          }
        }
      }
      return typeof colthOrig === 'function' ? colthOrig(c) : (c && c.label) || '';
    };

    const locOrig = window.localizeColumnLabel;
    window.localizeColumnLabel = function(ruLabel, tableKey, colId){
      if (tableKey && colId){
        const own = labelOf(tableKey, colId);
        if (own) return own;
      }
      // окно выбора зовёт без таблицы — берём открытую
      const open = есть('colPickerTable') ? colPickerTable : null;
      if (!tableKey && open){
        const rows = layout[open] || [];
        const cols = tableColumns[open] || [];
        const col = cols.find(x => x.label === ruLabel);
        if (col){
          const row = rows.find(r => r.columnId === col.id);
          if (row) return row.label;
        }
      }
      return typeof locOrig === 'function'
        ? locOrig.apply(this, arguments) : ruLabel;
    };
  }

  /* ============ Администрирование: колонки поклажедателя ============ */

  const admin = {depositorId: null, table: null, tables: [], rows: []};

  const isAdmin = () => ['admin', 'developer'].includes(
      PORTAL.session && PORTAL.session.roleCode);
  const mayEdit = () => PORTAL.session && PORTAL.session.roleCode === 'admin';

  function tablesHtml(){
    return '<div style="display:flex; gap:18px; align-items:flex-start; flex-wrap:wrap;">'
      + '<div style="min-width:220px;">'
      + admin.tables.map(t =>
          '<div class="col-item" style="cursor:pointer;'
          + (t.tableKey === admin.table ? ' font-weight:700;' : '') + '"'
          + ' onclick="portalDepositorColumnsTable(\'' + esc(t.tableKey) + '\')">'
          + esc(t.objectName || t.tableKey) + ' <span class="count">'
          + esc(t.tableKey) + ', ' + t.columnCount + '</span></div>').join('')
      + '</div>'
      + '<div style="flex:1; min-width:320px;" id="depColList"></div>'
      + '</div>';
  }

  function columnsHtml(){
    if (!admin.rows.length) return '<div class="hint">Выберите раздел слева.</div>';
    const ro = mayEdit() ? '' : ' disabled';
    return '<table class="table"><thead><tr>'
      + '<th>' + 'Колонка' + '</th>'
      + '<th>' + 'Подпись у клиента' + '</th>'
      + '<th style="text-align:center;">' + 'Доступна' + '</th>'
      + '<th style="text-align:center;">' + 'Показывать сразу' + '</th>'
      + '</tr></thead><tbody>'
      + admin.rows.map(r => {
          const renamed = r.label !== r.portalLabel;
          return '<tr><td>' + esc(r.columnId) + '</td><td>' + esc(r.label)
            + (renamed ? ' <span class="count">(' + 'вместо' + ' «'
                + esc(r.portalLabel) + '»)</span>' : '')
            + '</td>'
            + '<td style="text-align:center;"><input type="checkbox"' + ro
            + (r.isAvailable ? ' checked' : '')
            + ' onchange="portalDepositorColumnSave(\'' + esc(r.columnId)
            + '\', this.checked, null)"></td>'
            + '<td style="text-align:center;"><input type="checkbox"' + ro
            + (r.isDefault ? ' checked' : '') + (r.isAvailable ? '' : ' disabled')
            + ' onchange="portalDepositorColumnSave(\'' + esc(r.columnId)
            + '\', null, this.checked)"></td></tr>';
        }).join('')
      + '</tbody></table>'
      + (mayEdit()
          ? '<div style="margin-top:12px;"><button class="btn" type="button"'
            + ' onclick="portalDepositorColumnsReset()">'
            + 'Вернуть портальные настройки' + '</button></div>'
          : '')
      + '<div class="hint" style="margin-top:10px;">'
      + 'Недоступная колонка не попадёт клиенту даже в окно выбора колонок — включить её себе он не сможет. «Показывать сразу» — только значение по умолчанию, пользователь вправе поменять.</div>';
  }

  window.portalDepositorColumnsTable = async function(tableKey){
    admin.table = tableKey;
    try {
      admin.rows = await api('GET', '/api/admin/depositor-columns?depositorId='
          + encodeURIComponent(admin.depositorId) + '&table=' + encodeURIComponent(tableKey));
    } catch (e){
      admin.rows = [];
      say(e.message);
    }
    const host = document.getElementById('depColBody');
    if (host) host.innerHTML = tablesHtml();
    const list = document.getElementById('depColList');
    if (list) list.innerHTML = columnsHtml();
  };

  // Строки хранятся только там, где настройка отличается от портальной,
  // поэтому «вернуть как у портала» — это убрать их. Сбрасываем весь
  // раздел: по одной колонке это и так делается галочками.
  window.portalDepositorColumnsReset = async function(){
    if (!admin.depositorId || !admin.table) return;
    try {
      admin.rows = await api('DELETE', '/api/admin/depositor-columns?depositorId='
          + encodeURIComponent(admin.depositorId)
          + '&table=' + encodeURIComponent(admin.table));
      const list = document.getElementById('depColList');
      if (list) list.innerHTML = columnsHtml();
      say('Колонки возвращены к настройкам по умолчанию');
    } catch (e){
      say(e.message);
    }
  };

  window.portalDepositorColumnSave = async function(columnId, available, byDefault){
    const row = admin.rows.find(r => r.columnId === columnId);
    if (!row) return;
    try {
      admin.rows = await api('PUT', '/api/admin/depositor-columns', {
        depositorId: admin.depositorId,
        table: admin.table,
        columnId: columnId,
        isAvailable: available === null ? row.isAvailable : available,
        isDefault: byDefault === null ? row.isDefault : byDefault
      });
      const list = document.getElementById('depColList');
      if (list) list.innerHTML = columnsHtml();
    } catch (e){
      say(e.message);
      portalDepositorColumnsTable(admin.table);
    }
  };

  /** Вкладка «Колонки» в карточке поклажедателя. */
  async function openDepositorColumns(depositorId){
    admin.depositorId = depositorId;
    admin.table = null;
    admin.rows = [];
    try {
      if (!admin.tables.length) admin.tables = await api('GET', '/api/admin/column-tables');
    } catch (e){
      say(e.message);
      return;
    }
    const host = document.getElementById('depColBody');
    if (host) host.innerHTML = tablesHtml();
    if (admin.tables.length) portalDepositorColumnsTable(admin.tables[0].tableKey);
  }

  // Карточка поклажедателя в прототипе без вкладок, поэтому полоску
  // вкладок добавляем сами, а не переписываем карточку целиком.
  function wrapDepositorCard(){
    const renderOrig = window.portalDirRenderHook;
    const state = {tab: 'card'};

    function decorate(){
      if (!PORTAL.columns.currentDir || PORTAL.columns.currentDir !== 'depositorsDir') return;
      const body = document.getElementById('portalDirBody');
      if (!body || document.getElementById('depTabs')) return;
      const id = PORTAL.columns.currentId;
      if (!id) return;
      const strip = document.createElement('div');
      strip.className = 'admin-subtabs';
      strip.id = 'depTabs';
      strip.style.marginBottom = '12px';
      strip.innerHTML =
          '<button class="admin-subtab-btn ' + (state.tab === 'card' ? 'active' : '') + '"'
          + ' onclick="portalDepositorTab(\'card\')">Карточка</button>'
          + '<button class="admin-subtab-btn ' + (state.tab === 'columns' ? 'active' : '') + '"'
          + ' onclick="portalDepositorTab(\'columns\')">Колонки</button>';
      body.parentNode.insertBefore(strip, body);
      const pane = document.createElement('div');
      pane.id = 'depColBody';
      pane.style.display = state.tab === 'columns' ? '' : 'none';
      body.parentNode.insertBefore(pane, body.nextSibling);
      body.style.display = state.tab === 'columns' ? 'none' : '';
      if (state.tab === 'columns') openDepositorColumns(id);
    }

    window.portalDepositorTab = function(tab){
      state.tab = tab;
      const body = document.getElementById('portalDirBody');
      const pane = document.getElementById('depColBody');
      const strip = document.getElementById('depTabs');
      if (!body || !pane || !strip) return;
      body.style.display = tab === 'columns' ? 'none' : '';
      pane.style.display = tab === 'columns' ? '' : 'none';
      strip.querySelectorAll('.admin-subtab-btn').forEach((b, i) => {
        b.classList.toggle('active', (i === 0) === (tab === 'card'));
      });
      if (tab === 'columns') openDepositorColumns(PORTAL.columns.currentId);
    };

    const openOrig = window.portalOpenPortalDir;
    if (typeof openOrig === 'function'){
      window.portalOpenPortalDir = async function(dirName, id){
        PORTAL.columns.currentDir = dirName;
        PORTAL.columns.currentId = id;
        state.tab = 'card';
        const strip = document.getElementById('depTabs');
        if (strip) strip.remove();
        const pane = document.getElementById('depColBody');
        if (pane) pane.remove();
        await openOrig.apply(this, arguments);
        if (isAdmin() && dirName === 'depositorsDir') decorate();
      };
    }
    return renderOrig;
  }

  /* ============ Копирование настроек пользователю ============ */

  function wrapUserCard(){
    const openOrig = window.portalOpenAdminUser;
    if (typeof openOrig !== 'function') return;
    window.portalOpenAdminUser = async function(userId){
      await openOrig.apply(this, arguments);
      if (!mayEdit() || !userId) return;
      addCopyBlock(userId);
    };
  }

  async function addCopyBlock(userId){
    const host = document.getElementById('adminSetPassword');
    const row = host && host.parentNode;
    if (!row || document.getElementById('copySettingsFrom')) return;
    let users = [];
    try {
      users = await api('GET', '/api/admin/users');
    } catch (e){ return; }
    const others = users.filter(u => u.userId !== userId && u.isActive !== false);
    if (!others.length) return;
    const box = document.createElement('div');
    box.style.cssText = 'display:flex; gap:10px; align-items:flex-end; flex-wrap:wrap;'
        + ' margin-top:14px; width:100%;';
    box.innerHTML = '<div class="kv" style="margin:0; min-width:280px;">'
      + '<label>Скопировать настройки у пользователя</label>'
      + '<select class="kv-input kv-input-editable" id="copySettingsFrom">'
      + '<option value="">— не выбран —</option>'
      + others.map(u => '<option value="' + esc(u.userId) + '">'
          + esc(u.fullName || u.login) + ' (' + esc(u.login) + ')</option>').join('')
      + '</select></div>'
      + '<button class="btn" type="button" onclick="portalCopyUserSettings(\''
      + esc(userId) + '\')">Скопировать</button>'
      + '<div class="hint" style="width:100%; margin:0;">'
      + 'Перенесутся видимость пунктов меню, разрешения и выбранные колонки. Роль и доступы к поклажедателям и складам не копируются — их выдают отдельно и осознанно.</div>';
    row.parentNode.insertBefore(box, row.nextSibling);
  }

  window.portalCopyUserSettings = async function(toUserId){
    const sel = document.getElementById('copySettingsFrom');
    if (!sel || !sel.value){
      say('Выберите, у кого копировать настройки');
      return;
    }
    try {
      await api('POST', '/api/admin/users/copy-settings',
          {fromUserId: sel.value, toUserId: toUserId});
      say('Настройки скопированы');
    } catch (e){
      say(e.message);
    }
  };

  /* ============ Установка ============ */

  function install(){
    wrapPicker();
    wrapLabels();
    wrapDepositorCard();
    wrapUserCard();

    PORTAL.onLogin(() => {
      reload(null).then(redrawAll).catch(() => {
        // колонки не загрузились — прототип работает на своих умолчаниях,
        // экран остаётся рабочим
      });
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

PATCH_BILLING = r"""
<!-- ===== Биллинг: три вкладки вместо одного длинного экрана =====
     Экран показывал подряд тарифы, отчёты и счета — три разные задачи
     в одном свитке. Теперь это вкладки: «Отчёты по биллингу»,
     «Услуги и Тарифы», «Счета».

     Разметка не переписывается: существующие блоки раскладываются по
     вкладкам как они есть. Иначе пришлось бы дублировать таблицы,
     а их наполняет прототип по идентификаторам tbody.
================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const ВКЛАДКИ = [
    {id: 'reports',  ключ: 'billing.tab.reports'},
    {id: 'services', ключ: 'billing.tab.services_and_rates'},
    {id: 'invoices', ключ: 'billing.tab.invoices'}
  ];
  let активная = 'reports';

  /**
   * Раскладывает блоки экрана по вкладкам.
   *
   * Опознаём блоки по tbody, который наполняет прототип: у самих
   * section-title и table-card идентификаторов нет, а переименовать их
   * значило бы править разметку в двух местах.
   */
  function разложить(){
    const view = document.getElementById('view-billing');
    if (!view || view.querySelector('#billingTabs')) return;

    const узел = id => document.getElementById(id);
    const дети = [...view.children];
    const принадлежность = {};

    // Блок начинается с заголовка (section-title или panel-head) и идёт
    // до следующего заголовка. Какой это блок, определяет tbody внутри.
    let текущий = null;
    const группы = [];
    дети.forEach(el => {
      const заголовок = el.classList.contains('section-title')
        || (el.classList.contains('panel-head') && el.querySelector('.section-title'));
      if (заголовок || !текущий){
        текущий = {узлы: [], таблица: null};
        группы.push(текущий);
      }
      текущий.узлы.push(el);
      ['billingServicesBody', 'billingReportsBody', 'billingInvoicesBody'].forEach(id => {
        if (el.querySelector && el.querySelector('#' + id)) текущий.таблица = id;
      });
    });

    принадлежность.billingReportsBody = 'reports';
    принадлежность.billingServicesBody = 'services';
    принадлежность.billingInvoicesBody = 'invoices';

    const заголовокЭкрана = view.querySelector('.panel-head');
    const полосы = {};
    ВКЛАДКИ.forEach(в => {
      const d = document.createElement('div');
      d.className = 'admin-sub';
      d.id = 'billing-sub-' + в.id;
      полосы[в.id] = d;
    });

    группы.forEach(г => {
      // первый блок — это шапка экрана с кнопкой экспорта, она не таблица
      if (г.узлы[0] === заголовокЭкрана) return;
      const куда = принадлежность[г.таблица];
      if (!куда) return;
      г.узлы.forEach(el => полосы[куда].appendChild(el));
    });

    const полоса = document.createElement('div');
    полоса.className = 'admin-subtabs';
    полоса.id = 'billingTabs';
    view.appendChild(полоса);
    ВКЛАДКИ.forEach(в => view.appendChild(полосы[в.id]));

    // Кнопка «Экспортировать в Excel» выгружает тарифы и услуги —
    // на других вкладках она сбивала бы с толку, поэтому переезжает
    // вместе с ними.
    const экспорт = заголовокЭкрана
      && заголовокЭкрана.querySelector('.actions');
    if (экспорт) полосы.services.insertBefore(экспорт, полосы.services.firstChild);

    отрисовать();
  }

  function отрисовать(){
    const полоса = document.getElementById('billingTabs');
    if (!полоса) return;
    полоса.innerHTML = ВКЛАДКИ.map(в =>
      '<button class="admin-subtab-btn' + (в.id === активная ? ' active' : '') + '"'
      + ' type="button" data-billing-tab="' + в.id + '">'
      + T_(в.ключ) + '</button>').join('');
    полоса.querySelectorAll('[data-billing-tab]').forEach(b => {
      b.onclick = () => { активная = b.getAttribute('data-billing-tab'); отрисовать(); };
    });
    ВКЛАДКИ.forEach(в => {
      const el = document.getElementById('billing-sub-' + в.id);
      if (el) el.classList.toggle('active', в.id === активная);
    });
  }

  window.showBillingSub = function(id){
    if (ВКЛАДКИ.some(в => в.id === id)) активная = id;
    отрисовать();
  };

  function install(){
    разложить();
    // подписи вкладок приходят вместе с переводами
    PORTAL.onLogin(() => { разложить(); отрисовать(); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>

"""

PATCH_DEPVIS = r"""
<!-- ===== Настройки поклажедателя: разделы, склады и поля товаров =====
     Карточка в «Доступах поклажедателей» получает две вкладки.

       «Разделы и склады» — склады (было) и «Показать раздел перемещения»
                            (переехало с пользователя: это свойство
                            клиента, а не человека).
       «Поля товаров»     — восемь подвкладок по карточке товара.
                            Снятая галочка убирает поле у клиента везде:
                            из карточки, из выбора колонок и из шаблона
                            Excel. У обязательных полей галочка
                            заблокирована, и тот же запрет стоит
                            на сервере (V35).

     Разбивка по подвкладкам приходит из базы (group_key), а не задаётся
     здесь: иначе появился бы ещё один список полей товара, который
     разойдётся с карточкой.
================================================================= -->
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

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  // Состояние открытой карточки. Галочки живут здесь, а не в DOM:
  // подвкладка перерисовывается целиком, и снятые на другой подвкладке
  // галочки иначе потерялись бы при переключении.
  const state = {
    depositorId: null, depositor: null,
    tab: 'sections',          // sections | fields
    group: null,              // активная подвкладка полей
    warehouses: [],           // выданные склады
    settings: {hideTransfersTab: false},
    columns: [],              // строки admin_depositor_columns по товару
    draft: {}                 // columnId -> доступно ли (правки до сохранения)
  };

  const толькоЧтение = () => currentUserRole === 'developer';

  /* ============ Поля, недоступные поклажедателю ============ */

  /**
   * Доступна ли колонка этому клиенту.
   *
   * Раскладка с сервера запрещённых колонок не содержит вовсе
   * (table_columns их не отдаёт), поэтому «нет в раскладке» и значит
   * «клиенту недоступна». Пока раскладка не пришла, показываем всё:
   * мигание карточки хуже лишнего поля на долю секунды.
   */
  function доступна(columnId){
    const rows = PORTAL.columns && PORTAL.columns.raw && PORTAL.columns.raw.sku;
    if (!rows) return true;
    return rows.indexOf(columnId) >= 0;
  }
  PORTAL.полеДоступно = доступна;

  /**
   * Строка карточки товара с привязкой к колонке.
   *
   * Сборка переписывает fcKv(...) в fcKvC('columnId', ...), чтобы строка
   * знала своё поле. Недоступное поле не рисуется — иначе запрет работал
   * бы только в шаблоне Excel и в выборе колонок, а в карточке поле
   * оставалось бы на месте.
   */
  window.fcKvC = function(columnId, label, value){
    if (!доступна(columnId)) return '';
    return fcKv(label, value);
  };

  /* ================== Вкладки карточки ================== */

  function вкладки(){
    const кнопка = (id, ключ) =>
      '<button class="admin-subtab-btn' + (state.tab === id ? ' active' : '') + '"'
      + ' type="button" data-dep-tab="' + id + '">' + esc(T_(ключ)) + '</button>';
    return '<div class="admin-subtabs" id="depVisTabs">'
      + кнопка('sections', 'admin.tab.sections_and_warehouses')
      + кнопка('fields', 'admin.tab.product_fields')
      + '</div>';
  }

  /* ================== Вкладка «Разделы и склады» ================== */

  function складиРазделы(){
    const склады = (DEPVIS.склады || []).map(w => ({
      id: w.warehouseId, name: w.name, address: w.address
    }));
    const выдано = new Set(state.warehouses || []);
    const строкаСклада = x =>
      '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
      + 'padding:9px 0;border-bottom:1px solid var(--line);">'
      + '<input type="checkbox" data-warehouse-id="' + esc(x.id) + '"'
      + (выдано.has(x.id) ? ' checked' : '') + '> ' + esc(x.name)
      + (x.address ? ' <span style="color:var(--muted);">· ' + esc(x.address) + '</span>' : '')
      + '</label>';

    return ''
      + '<div class="section-title" style="margin-top:0;">' + esc(T_('admin.title.sections'))
      + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;">'
      + '    <input type="checkbox" id="depVisTransfers"'
      + (state.settings.hideTransfersTab ? '' : ' checked') + '> '
      + esc(T_('admin.label.show_transfers_section')) + '</label>'
      + '  <div class="hint" style="margin:8px 0 0;">'
      + esc(T_('admin.hint.transfers_section')) + '</div>'
      + '</div>'
      + '<div class="section-title">'
      + esc(T_('admin.title.warehouses_available_to_depositors')) + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">'
      + esc(T_('admin.hint.depositor_warehouses')) + '</div>'
      + (склады.length
          ? склады.map(строкаСклада).join('')
          : '<div style="color:var(--muted);font-size:13.5px;">'
            + esc(T_('common.text.list_is_empty')) + '</div>')
      + '</div>';
  }

  /* ================== Вкладка «Поля товаров» ================== */

  /** Подвкладки в порядке карточки товара: порядок задаёт сама база. */
  function группы(){
    const out = [];
    const seen = new Set();
    state.columns.forEach(c => {
      const key = c.groupKey || '';
      if (!seen.has(key)){
        seen.add(key);
        out.push({key: key, label: c.groupLabel || key});
      }
    });
    return out;
  }

  function поляТовара(){
    const гр = группы();
    if (!гр.length){
      return '<div style="color:var(--muted);font-size:13.5px;">'
        + esc(T_('common.text.list_is_empty')) + '</div>';
    }
    if (!гр.some(g => g.key === state.group)) state.group = гр[0].key;

    const подвкладки = '<div class="admin-subtabs" style="margin-top:4px;">'
      + гр.map(g => '<button class="admin-subtab-btn'
          + (g.key === state.group ? ' active' : '') + '" type="button"'
          + ' data-dep-group="' + esc(g.key) + '">' + esc(g.label) + '</button>').join('')
      + '</div>';

    const поля = state.columns.filter(c => (c.groupKey || '') === state.group);
    const строка = c => {
      const доступно = state.draft[c.columnId] !== undefined
        ? state.draft[c.columnId] : c.isAvailable !== false;
      const заперто = c.isLocked === true;
      return '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
        + 'padding:9px 0;border-bottom:1px solid var(--line);'
        + (заперто ? 'color:var(--muted);' : '') + '">'
        + '<input type="checkbox" data-column-id="' + esc(c.columnId) + '"'
        + (доступно ? ' checked' : '') + (заперто ? ' disabled' : '') + '> '
        + '<span style="color:var(--text);">' + esc(c.label) + '</span>'
        + (заперто
            ? ' <span style="color:var(--muted);">· '
              + esc(T_('admin.text.required_field_locked')) + '</span>'
            : '')
        + (c.label !== c.portalLabel
            ? ' <span style="color:var(--muted);">· ' + esc(c.portalLabel) + '</span>'
            : '')
        + '</label>';
    };

    return подвкладки
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">'
      + esc(T_('admin.hint.product_fields')) + '</div>'
      + поля.map(строка).join('')
      + '</div>';
  }

  /* ================== Сборка карточки ================== */

  const действия = ''
    + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
    + '  <button class="btn primary" type="button" data-dep-save></button>'
    + '  <button class="btn ghost" type="button" data-dep-cancel></button>'
    + '</div>';

  function нарисовать(){
    const el = document.getElementById('adminUserEditContent');
    if (!el) return;
    const d = state.depositor || {};
    el.innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>' + esc(d.orgName || '') + '</h2>'
      + '  <div class="meta">' + esc(T_('admin.column.depositor')) + ': '
      + esc(d.code || '') + '</div></div></div>'
      + вкладки()
      + (state.tab === 'fields' ? поляТовара() : складиРазделы())
      + действия;

    el.querySelectorAll('[data-dep-tab]').forEach(b => {
      b.onclick = () => { запомнить(); state.tab = b.getAttribute('data-dep-tab'); нарисовать(); };
    });
    el.querySelectorAll('[data-dep-group]').forEach(b => {
      b.onclick = () => { запомнить(); state.group = b.getAttribute('data-dep-group'); нарисовать(); };
    });

    const сохранить = el.querySelector('[data-dep-save]');
    сохранить.textContent = T_('common.button.save');
    сохранить.onclick = отправить;
    const отмена = el.querySelector('[data-dep-cancel]');
    отмена.textContent = T_('common.button.cancel');
    отмена.onclick = () => { showView('admin'); showAdminSub('depositor-access'); };

    if (толькоЧтение()){
      el.querySelectorAll('input').forEach(e => e.disabled = true);
      сохранить.style.display = 'none';
    }
  }

  /**
   * Переносит галочки с экрана в состояние.
   *
   * Вызывается перед каждой перерисовкой: подвкладка строится заново,
   * и без этого правка, сделанная на одной подвкладке, исчезала бы
   * при переходе на другую — человек бы решил, что сохранил.
   */
  function запомнить(){
    const el = document.getElementById('adminUserEditContent');
    if (!el) return;
    el.querySelectorAll('input[data-column-id]').forEach(cb => {
      if (cb.disabled) return;          // обязательные не трогаем
      state.draft[cb.getAttribute('data-column-id')] = cb.checked;
    });
    const склады = [...el.querySelectorAll('input[data-warehouse-id]')];
    if (склады.length) state.warehouses = склады.filter(c => c.checked)
      .map(c => c.getAttribute('data-warehouse-id'));
    const перемещения = el.querySelector('#depVisTransfers');
    if (перемещения) state.settings.hideTransfersTab = !перемещения.checked;
  }

  async function отправить(){
    запомнить();
    try {
      // Склады и настройку разделов отправляем всегда: их видно на одной
      // вкладке, и человек вправе рассчитывать, что кнопка сохраняет всё.
      DEPVIS.доступы = await api('PUT', '/api/admin/depositors/'
        + encodeURIComponent(state.depositorId) + '/warehouses',
        {warehouseIds: state.warehouses});
      await api('PUT', '/api/admin/depositors/'
        + encodeURIComponent(state.depositorId) + '/settings',
        {hideTransfersTab: state.settings.hideTransfersTab});

      // Поля — только изменённые: каждая строка отдельным запросом,
      // потому что база заводит запись лишь при отличии от портальной
      // настройки и сама убирает её, когда отличие исчезло.
      const изменённые = state.columns.filter(c => {
        const было = c.isAvailable !== false;
        const стало = state.draft[c.columnId];
        return стало !== undefined && стало !== было;
      });
      for (const c of изменённые){
        await api('PUT', '/api/admin/depositor-columns', {
          depositorId: state.depositorId, table: 'sku', columnId: c.columnId,
          isAvailable: state.draft[c.columnId], isDefault: c.isDefault !== false
        });
      }

      say(изменённые.length
        ? T_('admin.message.fields_updated')
        : T_('admin.message.settings_updated'));

      if (typeof renderAdminDepositorAccess === 'function') renderAdminDepositorAccess();
      // правили текущего клиента — перечитываем раскладку и меню
      if (PORTAL.session && PORTAL.session.depositorId === state.depositorId){
        PORTAL.reloadAll();
      }
      showView('admin');
      showAdminSub('depositor-access');
    } catch (e){
      say(e.message);
    }
  }

  /* ================== Открытие ================== */

  const DEPVIS = {склады: [], доступы: []};

  window.portalOpenDepositorSettings = async function(depositorId){
    const d = (DEPVIS.доступы || []).find(x => x.depositorId === depositorId);
    state.depositorId = depositorId;
    state.depositor = d || {};
    state.tab = 'sections';
    state.group = null;
    state.draft = {};
    try {
      const [склады, выдано, настройки, колонки] = await Promise.all([
        DEPVIS.склады.length ? Promise.resolve(DEPVIS.склады)
                             : api('GET', '/api/admin/warehouses'),
        api('GET', '/api/admin/depositors/' + encodeURIComponent(depositorId) + '/warehouses'),
        api('GET', '/api/admin/depositors/' + encodeURIComponent(depositorId) + '/settings'),
        api('GET', '/api/admin/depositor-columns?table=sku&depositorId='
            + encodeURIComponent(depositorId))
      ]);
      DEPVIS.склады = склады || [];
      state.warehouses = выдано || [];
      state.settings = настройки || {hideTransfersTab: false};
      state.columns = колонки || [];
    } catch (e){
      say(e.message);
      return;
    }
    нарисовать();
    showView('admin-user-edit');
  };

  /**
   * Заглушка «Раздел в разработке» в настройке полей поклажедателя.
   *
   * Этот раздел теперь существует — в карточке доступов поклажедателя.
   * Оставить заглушку значило бы держать на экране обещание, которое
   * уже выполнено в другом месте: администратор искал бы настройку там,
   * где её нет.
   */
  function подсказатьГдеНастройка(){
    const el = document.getElementById('fl-sub-visibility');
    if (!el || el.dataset.redirected) return;
    el.dataset.redirected = '1';
    el.innerHTML = '<div class="hint" style="margin-bottom:12px;">'
      + esc(T_('admin.hint.where_field_visibility')) + '</div>'
      + '<button class="btn primary" type="button" id="goDepositorAccess"></button>';
    const b = document.getElementById('goDepositorAccess');
    b.textContent = T_('admin.button.depositor_access');
    b.onclick = () => { showView('admin'); showAdminSub('depositor-access'); };
  }

  function install(){
    // Строку в списке открывает уже наша карточка: прежняя показывала
    // только склады, и две карточки на один экран не нужны.
    window.portalOpenDepositorWarehouses = window.portalOpenDepositorSettings;
    window.openAdminDepositorAccessEdit = window.portalOpenDepositorSettings;

    PORTAL.onLogin(async () => {
      try {
        const [склады, доступы] = await Promise.all([
          api('GET', '/api/admin/warehouses'),
          api('GET', '/api/admin/depositor-access')
        ]);
        DEPVIS.склады = склады || [];
        DEPVIS.доступы = доступы || [];
      } catch (e){
        DEPVIS.склады = [];
        DEPVIS.доступы = [];
      }
      подсказатьГдеНастройка();
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

PATCH_EXCEL = r"""
<!-- ===== Excel: шаблон номенклатуры =====
     Кнопка «Скачать шаблон Excel» в справочнике товаров перестаёт быть
     заглушкой: файл собирает сервер — подписи на языке пользователя,
     с переименованием полей клиента, без колонок, запрещённых этому
     поклажедателю, и с его кодом в имени файла.

     Разбор загружаемого файла появится следующим шагом; кнопку
     «Загрузить из Excel» пока не трогаем, чтобы она не обманывала.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const say = m => { if (typeof notify === 'function') notify(m); };

  /**
   * Скачивание файла с сервера.
   *
   * Простой переход по ссылке не подходит: нужен тот же cookie сеанса
   * и разбор отказа (403, если загрузка из Excel отключена) — иначе
   * вместо объяснения человек получил бы пустой файл или страницу
   * с ошибкой вместо таблицы.
   */
  async function download(url, fallbackName){
    const resp = await fetch(url, {headers: {'Accept': '*/*'}});
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));

    // имя файла сервер присылает в заголовке, в том числе по-русски
    let name = fallbackName;
    const cd = resp.headers.get('Content-Disposition') || '';
    const utf8 = cd.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
    const plain = cd.match(/filename\s*=\s*"?([^";]+)"?/i);
    if (utf8) name = decodeURIComponent(utf8[1]);
    else if (plain) name = plain[1];

    const blob = await resp.blob();
    const href = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = href;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    // ссылку на blob надо отпустить, иначе файл висит в памяти страницы
    setTimeout(() => URL.revokeObjectURL(href), 2000);
    return name;
  }

  window.portalDownloadSkuTemplate = async function(){
    try {
      const name = await download('/api/sku/excel-template', 'Шаблон_Номенклатура.xlsx');
      say('Скачивается' + ': ' + name);
    } catch (e){
      say(e.message);
    }
  };

  function install(){
    PORTAL.onLogin(() => {
      // кнопка в справочнике товаров: ищем по тексту-заглушке, которую
      // прототип поставил в onclick, — своего идентификатора у неё нет
      const host = document.getElementById('ref-sub-sku');
      if (!host) return;
      host.querySelectorAll('button').forEach(btn => {
        const onclick = btn.getAttribute('onclick') || '';
        if (onclick.indexOf('Шаблон_Номенклатура') >= 0){
          btn.removeAttribute('onclick');
          btn.id = btn.id || 'skuTemplateBtn';
          btn.onclick = () => portalDownloadSkuTemplate();
        }
      });
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

PATCH_IMPORT = r"""
<!-- ===== Загрузка номенклатуры из Excel: экран проверки =====
     Кнопка «Загрузить из Excel» в справочнике товаров перестаёт быть
     заглушкой. Файл уходит на сервер, тот его разбирает и НИЧЕГО
     не пишет в справочник — поверх справочника открывается окно
     проверки. Окно, а не отдельный экран: человек не уходит
     из справочника и, закрыв окно, остаётся там же, где нажал
     кнопку. Закрытие окна отменяет загрузку — разобранные строки
     на сервере не нужны, если их не стали применять.

     Три вкладки:
       «Все строки»           — файл как есть, со сводкой и галочками-
                                фильтрами. Кнопок записи здесь нет.
       «Новые позиции»        — что будет создано, кнопка «Создать…».
       «Существующие позиции» — что будет обновлено, кнопка «Обновить…».

     Состояние строки — два независимых признака: класс (новая /
     существующая / неопознанная) и годность. Поэтому галочки
     «Только ошибки», «Новые» и «Существующие» складываются: новая
     позиция с незаполненным обязательным полем попадает под обе.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const state = {
    result: null,              // ответ сервера целиком
    tab: 'all',                // all | new | existing
    onlyErrors: false,
    showNew: false,
    showExisting: false
  };

  /* ================= Разбор ответа ================= */

  /**
   * JSONB приходит текстом.
   *
   * Сервер отдаёт cell_values, fill_fields и issues приведёнными к text:
   * иначе наружу уходит объект драйвера PostgreSQL и в JSON превращается
   * в мусор вида {"type":"jsonb","value":"..."}. Разбираем здесь, один раз
   * на ответ.
   */
  function разобрать(ответ){
    const json = (v, запас) => {
      if (v === null || v === undefined) return запас;
      try { return JSON.parse(v); } catch (e){ return запас; }
    };
    const r = {
      importId: ответ.importId,
      fileName: ответ.fileName,
      rowCount: ответ.rowCount || 0,
      toCreate: ответ.toCreate || 0,
      toUpdate: ответ.toUpdate || 0,
      errorCount: ответ.errorCount || 0,
      columns: json(ответ.columnsUsedJson, []),
      skipped: json(ответ.columnsSkippedJson, []),
      rows: (ответ.rows || []).map(row => ({
        rowNumber: row.rowNumber,
        rowClass: row.rowClass,
        isValid: row.isValid === true,
        applied: row.applied === true,
        rowKey: row.rowKey,
        values: json(row.cellValuesJson, {}),
        display: json(row.cellDisplayJson, {}),
        fill: json(row.fillFieldsJson, []),
        issues: json(row.issuesJson, [])
      }))
    };
    return r;
  }

  /* ================= Отбор строк ================= */

  /**
   * Галочки фильтруют по двум признакам независимо.
   *
   * «Только ошибки» режет по годности, «Новые» и «Существующие» —
   * по классу и складываются между собой. Ни одна не отмечена —
   * видно всё: иначе человек, открыв экран, увидел бы пустую таблицу
   * и решил, что файл не прочитался.
   */
  function отобрать(rows){
    const классы = [];
    if (state.showNew) классы.push('new');
    if (state.showExisting) классы.push('existing');
    return rows.filter(r => {
      if (state.onlyErrors && r.isValid) return false;
      if (классы.length && классы.indexOf(r.rowClass) < 0) return false;
      return true;
    });
  }

  /* ================= Отрисовка ================= */

  const КЛАСС_ПОДПИСЬ = {
    new: 'sku.text.state_create',
    existing: 'sku.text.state_update'
  };

  function состояние(row){
    if (row.applied){
      return '<span class="badge ok">'
        + esc(T_(row.rowClass === 'new' ? 'sku.text.state_created' : 'sku.text.state_updated'))
        + '</span>';
    }
    if (!row.isValid){
      return '<span class="badge err">' + esc(T_('sku.text.state_error')) + '</span>';
    }
    const ключ = КЛАСС_ПОДПИСЬ[row.rowClass];
    let подпись = esc(T_(ключ || 'sku.text.state_error'));
    if (row.rowClass === 'existing' && row.fill.length){
      подпись += ' <span class="count">' + row.fill.length + '</span>';
    }
    return '<span class="badge">' + подпись + '</span>';
  }

  /** Замечания по конкретной колонке — ими подсвечивается ячейка. */
  function замечанияПоКолонке(row, columnId){
    return row.issues.filter(i => i.columnId === columnId);
  }

  /** Замечания к строке целиком: показываются под таблицей строки. */
  function замечанияСтроки(row){
    return row.issues.filter(i => !i.columnId);
  }

  /**
   * Что показать в ячейке.
   *
   * У ссылочных полей в values лежит идентификатор — то, что уйдёт
   * в базу. Человеку он не говорит ничего, поэтому сервер прислал
   * рядом display: «шт» вместо «1». Для текста и чисел display пуст —
   * показываем само значение.
   */
  function показать(row, column){
    const поле = dbColumnOf(column);
    const готовое = row.display ? row.display[поле] : undefined;
    return готовое !== undefined && готовое !== null ? готовое : row.values[поле];
  }

  function ячейка(row, column){
    const значение = показать(row, column);
    const замечания = замечанияПоКолонке(row, column.columnId);
    if (!замечания.length){
      return '<td>' + esc(значение === undefined ? '' : значение) + '</td>';
    }
    const i = замечания[0];
    // Расхождение показываем парой значений: без этого человек
    // не поймёт, почему строку пропустили.
    const тело = (i.portalValue !== null && i.portalValue !== undefined)
      ? esc(T_('sku.text.in_file')) + ' ' + esc(i.fileValue)
        + ' · ' + esc(T_('sku.text.in_portal')) + ' ' + esc(i.portalValue)
      // Значение не разобралось — показываем то, что стояло в файле:
      // двойной значок вместо текста ничего не объясняет.
      : esc(значение === undefined || значение === null
            ? (i.fileValue === null || i.fileValue === undefined ? '—' : i.fileValue)
            : значение);
    return '<td class="cell-bad" title="' + esc(i.message) + '">⚠ ' + тело + '</td>';
  }

  // Колонки файла приходят как {header, columnId}; значения разобранной
  // строки лежат под именами полей базы. Связь восстанавливаем по тому
  // же правилу, что на сервере.
  // Группа и тип товара стали свободным текстом (V38), поэтому
  // отдельного соответствия им больше не нужно: item_group
  // и item_type получаются из group и productType по общему правилу.
  const СВОИ = {
    unit: 'uom_id', group: 'item_group', productType: 'item_type',
    currency: 'currency_id', hazardClass: 'hazard_class_id',
    hazardGroup: 'hazard_group_id'
  };
  function dbColumnOf(column){
    const id = column.columnId;
    return СВОИ[id] || id.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
  }

  function таблица(rows, columns){
    if (!rows.length){
      return '<div style="color:var(--muted);font-size:13.5px;padding:12px 0;">'
        + esc(T_('common.text.list_is_empty')) + '</div>';
    }
    const шапка = '<tr><th>' + esc(T_('sku.column.file_row')) + '</th>'
      + '<th>' + esc(T_('sku.column.row_state')) + '</th>'
      + columns.map(c => '<th>' + esc(c.header) + '</th>').join('') + '</tr>';

    const тело = rows.map(row => {
      const общие = замечанияСтроки(row);
      let html = '<tr><td>' + row.rowNumber + '</td><td>' + состояние(row) + '</td>'
        + columns.map(c => ячейка(row, c)).join('') + '</tr>';
      if (общие.length){
        html += '<tr class="row-note"><td></td><td colspan="' + (columns.length + 1) + '"'
          + ' style="color:var(--muted);font-size:12.5px;">'
          + общие.map(i => esc(i.message)).join(' · ') + '</td></tr>';
      }
      return html;
    }).join('');

    return '<div class="table-card"><div class="table-scroll-x"><table>'
      + '<thead>' + шапка + '</thead><tbody>' + тело + '</tbody></table></div></div>';
  }

  function сводка(r){
    const счётчик = (ключ, значение, вид) =>
      '<span class="imp-count ' + вид + '">' + esc(T_(ключ)) + ' <b>' + значение + '</b></span>';
    let html = '<div class="hint" style="margin:-4px 0 10px;">'
      + esc(r.fileName) + ' · ' + r.rowCount + ' ' + esc(T_('sku.text.rows_in_file'));
    if (r.skipped.length){
      // Число рядом с перечислением лишнее, да и согласование числа
      // со словом в переводе пришлось бы решать отдельно.
      html += ' · ' + esc(T_('sku.text.columns_not_recognized'))
        + ': ' + r.skipped.map(esc).join(', ');
    }
    html += '</div><div class="imp-counts" style="display:flex;gap:16px;margin-bottom:12px;">'
      + счётчик('sku.text.count_create', r.toCreate, 'ok')
      + счётчик('sku.text.count_update', r.toUpdate, 'ok')
      + счётчик('sku.text.count_errors', r.errorCount, 'err')
      + '</div>';
    return html;
  }

  function галочки(){
    const г = (id, ключ, включено) =>
      '<label style="display:inline-flex;align-items:center;gap:6px;font-size:13.5px;'
      + 'margin-right:18px;"><input type="checkbox" data-imp-filter="' + id + '"'
      + (включено ? ' checked' : '') + '> ' + esc(T_(ключ)) + '</label>';
    return '<div class="filters" style="padding:10px 0;">'
      + г('onlyErrors', 'sku.label.only_errors', state.onlyErrors)
      + г('showNew', 'sku.label.show_new', state.showNew)
      + г('showExisting', 'sku.label.show_existing', state.showExisting)
      + '</div>';
  }

  function вкладки(){
    const кнопка = (id, ключ) =>
      '<button class="admin-subtab-btn' + (state.tab === id ? ' active' : '') + '"'
      + ' type="button" data-imp-tab="' + id + '">' + esc(T_(ключ)) + '</button>';
    return '<div class="admin-subtabs" id="impTabs">'
      + кнопка('all', 'sku.tab.all_rows')
      + кнопка('new', 'sku.tab.new_items')
      + кнопка('existing', 'sku.tab.existing_items')
      + '</div>';
  }

  function нарисовать(){
    const r = state.result;
    const view = document.getElementById('skuImportPanel');
    if (!view || !r) return;

    let html = '<div style="display:flex;align-items:flex-start;gap:12px;">'
      + '<h3 style="font-size:17px;flex:1;">' + esc(T_('sku.title.import_check')) + '</h3>'
      + '<button class="btn ghost" type="button" data-imp-close="1">'
      + esc(T_('common.button.close')) + '</button></div>'
      + сводка(r) + вкладки();

    if (state.tab === 'all'){
      html += галочки() + таблица(отобрать(r.rows), r.columns);
    } else if (state.tab === 'new'){
      // Только то, что действительно запишется: вкладка отвечает
      // на вопрос «что произойдёт», а не «что лежит в файле».
      const rows = r.rows.filter(x => x.rowClass === 'new' && x.isValid);
      html += таблица(rows, r.columns)
        + кнопкаПрименения('create', 'sku.button.create_new_items', rows);
    } else {
      const rows = r.rows.filter(x => x.rowClass === 'existing' && x.isValid);
      html += подсказкаЗаполнения(rows) + таблица(rows, r.columns)
        + кнопкаПрименения('update', 'sku.button.update_existing_items', rows);
    }

    document.getElementById('skuImportPanel').innerHTML = html;
    привязать();
  }

  function подсказкаЗаполнения(rows){
    if (!rows.length) return '';
    const поля = new Set();
    rows.forEach(r => r.fill.forEach(f => поля.add(f)));
    if (!поля.size) return '';
    // fill приходит именами полей базы — человеку они ничего не говорят.
    // Подписи берём из колонок файла: это те же названия, которые он
    // видит в шапке своего файла.
    const подпись = {};
    (state.result.columns || []).forEach(c => { подпись[dbColumnOf(c)] = c.header; });
    const имена = [...поля].map(f => подпись[f] || f);
    return '<div class="hint" style="margin:-4px 0 10px;">'
      + esc(T_('sku.text.will_fill')) + ': ' + имена.map(esc).join(', ') + '</div>';
  }

  function кнопкаПрименения(kind, ключ, rows){
    const можно = rows.some(r => !r.applied);
    return '<div class="actions" style="margin-top:16px;">'
      + '<button class="btn primary" type="button" data-imp-apply="' + kind + '"'
      + (можно ? '' : ' disabled') + '>' + esc(T_(ключ))
      + ' <span class="count">' + rows.filter(r => !r.applied).length + '</span>'
      + '</button></div>';
  }

  function привязать(){
    const view = document.getElementById('skuImportPanel');
    view.querySelectorAll('[data-imp-tab]').forEach(b => {
      b.onclick = () => { state.tab = b.getAttribute('data-imp-tab'); нарисовать(); };
    });
    view.querySelectorAll('[data-imp-filter]').forEach(cb => {
      cb.onchange = () => {
        state[cb.getAttribute('data-imp-filter')] = cb.checked;
        нарисовать();
      };
    });
    view.querySelectorAll('[data-imp-close]').forEach(b => {
      b.onclick = () => закрыть();
    });
    view.querySelectorAll('[data-imp-apply]').forEach(b => {
      b.onclick = () => применить(b.getAttribute('data-imp-apply'));
    });
  }

  /**
   * Запись в справочник.
   *
   * Тела у запроса нет: что записывать, сервер знает из результата
   * проверки. Ответ — тот же результат целиком, поэтому экран
   * перерисовывается им и ничего не додумывает: какие строки стали
   * «создано», какие остались, сколько осталось применить.
   *
   * Кнопка на время запроса гаснет: человек не должен нажать её
   * дважды. Защита от второго нажатия есть и на сервере (признак
   * applied), но заметить разницу человек должен сразу.
   */
  async function применить(kind){
    const кнопка = document.querySelector('[data-imp-apply="' + kind + '"]');
    if (кнопка) кнопка.disabled = true;
    try {
      const resp = await fetch('/api/sku/excel-import/'
          + encodeURIComponent(state.result.importId) + '/' + kind,
          {method: 'POST', headers: {'Accept': 'application/json'}});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      const ответ = await resp.json();
      state.result = разобрать(ответ);
      нарисовать();
      say(итог(kind, ответ));
      // Справочник под окном показывает прежний список: перечитываем
      // его, чтобы созданные позиции были на месте, когда окно закроют.
      if (typeof PORTAL.перечитатьТовары === 'function') PORTAL.перечитатьТовары();
    } catch (e){
      say(e.message);
      if (кнопка) кнопка.disabled = false;
    }
  }

  /** Итог словами: «Создано позиций: 60». */
  function итог(kind, ответ){
    const записано = ответ.applied || 0;
    const сбой = ответ.failed || 0;
    if (!записано && !сбой) return T_('sku.message.nothing_to_apply');
    const ключ = kind === 'create'
      ? 'sku.message.created_items' : 'sku.message.updated_items';
    let текст = T_(ключ) + ': ' + записано;
    if (сбой) текст += '. ' + T_('sku.message.apply_partly');
    return текст;
  }

  /* ================= Окно ================= */

  function создатьОкно(){
    if (document.getElementById('skuImportOverlay')) return;
    const окно = document.createElement('div');
    окно.className = 'col-overlay';
    окно.id = 'skuImportOverlay';
    окно.style.cssText = 'align-items:center;justify-content:center;';
    // Щелчок по затемнению — то же, что «Закрыть»: привычное поведение
    // окна. Но только по самому затемнению, иначе окно закрывалось бы
    // от щелчка по таблице внутри.
    окно.onclick = e => { if (e.target === окно) закрыть(); };
    окно.innerHTML = ''
      + '<div class="col-panel" id="skuImportPanel" style="width:95vw;max-width:1600px;'
      + 'max-height:92vh;overflow:auto;border-radius:10px;'
      + 'box-shadow:0 8px 32px rgba(0,0,0,.24);"></div>';
    document.body.appendChild(окно);

    // Подсветка проблемных ячеек. Своих классов у прототипа для этого
    // нет, а подсветка здесь — главное, что человек видит.
    const style = document.createElement('style');
    style.textContent = '#skuImportPanel td.cell-bad{background:#fef2f2;color:#991b1b;}'
      + '#skuImportPanel tr.row-note td{background:#fffbeb;}'
      + '#skuImportPanel .imp-count{font-size:13.5px;color:var(--muted);}'
      + '#skuImportPanel .imp-count b{color:var(--text);font-size:15px;}'
      + '#skuImportPanel .imp-count.err b{color:#991b1b;}'
      + '#skuImportPanel .table-scroll-x{max-height:60vh;overflow:auto;}';
    document.head.appendChild(style);

    // Esc закрывает окно. Обработчик один на страницу и проверяет,
    // открыто ли окно: иначе он мешал бы остальным окнам портала.
    document.addEventListener('keydown', e => {
      if (e.key !== 'Escape') return;
      const o = document.getElementById('skuImportOverlay');
      if (o && o.classList.contains('active')) закрыть();
    });
  }

  /**
   * Закрытие отменяет загрузку.
   *
   * Разобранные строки живут на сервере только ради этого окна.
   * Если их не применили, держать их незачем: следующая загрузка
   * того же файла начнётся заново, а не продолжит прошлую.
   *
   * Отказ сервера на отмене не показываем: человек уже закрыл окно,
   * и сообщение про отмену ему нечего делать. Строки всё равно
   * не применены.
   */
  async function закрыть(){
    const окно = document.getElementById('skuImportOverlay');
    const importId = state.result && state.result.importId;
    state.result = null;
    if (окно) окно.classList.remove('active');
    if (importId){
      try {
        await fetch('/api/sku/excel-import/' + encodeURIComponent(importId),
                    {method: 'DELETE'});
      } catch (e){ /* молча: окно уже закрыто */ }
    }
  }

  function открыть(ответ){
    создатьОкно();
    state.result = разобрать(ответ);
    state.tab = 'all';
    state.onlyErrors = false;
    state.showNew = false;
    state.showExisting = false;
    нарисовать();
    document.getElementById('skuImportOverlay').classList.add('active');
  }

  window.portalOpenSkuImport = открыть;

  /* ================= Выбор файла ================= */

  function выбратьФайл(){
    let input = document.getElementById('skuImportFile');
    if (!input){
      input = document.createElement('input');
      input.type = 'file';
      input.id = 'skuImportFile';
      input.accept = '.xlsx';
      input.style.display = 'none';
      document.body.appendChild(input);
      input.onchange = () => {
        const file = input.files && input.files[0];
        // значение сбрасываем, иначе повторный выбор того же файла
        // не вызовет onchange и человек решит, что кнопка сломалась
        input.value = '';
        if (file) отправить(file);
      };
    }
    input.click();
  }

  async function отправить(file){
    const body = new FormData();
    body.append('file', file);
    try {
      const resp = await fetch('/api/sku/excel-import', {method: 'POST', body: body});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      открыть(await resp.json());
    } catch (e){
      say(e.message);
    }
  }

  /**
   * Прячет кнопки «Загрузить из Excel», которые ещё ничего не делают.
   *
   * Загрузка сделана только для товаров. На поступлениях, заказах,
   * перемещениях, отчётах и упаковках кнопки остались заглушками
   * из прототипа: нажатие показывает сообщение и всё. Кнопка, которая
   * выглядит рабочей и не работает, хуже отсутствующей — человек
   * решит, что загрузил.
   *
   * Вернуть легко: убрать этот вызов.
   */
  function спрятатьЗаглушки(){
    const свои = ['uploadSkuExcelBtn'];
    document.querySelectorAll('button').forEach(btn => {
      if (свои.indexOf(btn.id) >= 0) return;
      const onclick = btn.getAttribute('onclick') || '';
      if (/notify\('(Диалог загрузки|Загрузка ранее подготовленного)/.test(onclick)){
        btn.style.display = 'none';
      }
    });
  }

  function install(){
    PORTAL.onLogin(() => {
      const btn = document.getElementById('uploadSkuExcelBtn');
      if (btn && !btn.dataset.wired){
        btn.dataset.wired = '1';
        btn.removeAttribute('onclick');
        btn.onclick = () => выбратьФайл();
      }
      спрятатьЗаглушки();
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

PATCH_UNITS = r"""
<!-- ===== Единицы измерения: вкладка в «Справочниках» =====
     Справочник один на весь портал: «шт» означает штуку для всех
     клиентов, и владельца у неё нет. Поэтому список видят все, а
     правит только администратор — иначе один клиент переименовал бы
     единицу всем остальным.

     В разметке прототипа такой вкладки нет: единицы жили только
     внутри карточки товара выпадающим списком. Кнопку и панель
     добавляем сами, рядом с товарами и упаковками.

     Правка идёт прямо в строке списка, как у типов заказов:
     справочник короткий, и карточка ему не нужна.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  // canEdit приходит с сервера, а не вычисляется по роли на странице:
  // решает всё равно сервер, и два разных правила рано или поздно
  // разошлись бы.
  const state = {rows: [], canEdit: false, form: null, loaded: false};
  PORTAL.units = state;

  /* ================= Разметка вкладки ================= */

  function создатьВкладку(){
    const tabs = document.querySelector('#view-references .admin-subtabs');
    if (!tabs || document.getElementById('ref-sub-units')) return;

    const кнопка = document.createElement('button');
    кнопка.className = 'admin-subtab-btn ref-subtab-btn';
    кнопка.type = 'button';
    кнопка.id = 'refSubBtnUnits';
    кнопка.setAttribute('data-refsub', 'units');
    кнопка.textContent = T_('references.button.units');
    кнопка.onclick = () => showReferencesSub('units');
    // Рядом с упаковками: товар, его упаковки, его единицы измерения.
    const после = tabs.querySelector('[data-refsub="packaging"]');
    if (после && после.nextSibling) tabs.insertBefore(кнопка, после.nextSibling);
    else tabs.appendChild(кнопка);

    const панель = document.createElement('div');
    панель.className = 'admin-sub ref-sub';
    панель.id = 'ref-sub-units';
    панель.innerHTML = ''
      + '<div class="panel-head"><h1>' + esc(T_('references.title.units')) + '</h1>'
      + '<div class="actions" id="unitsActions"></div></div>'
      + '<div class="hint" style="margin:-6px 0 12px;">'
      + esc(T_('references.hint.units')) + '</div>'
      + '<div class="table-card"><table><thead><tr id="unitsHead"></tr></thead>'
      + '<tbody id="unitsBody"></tbody></table></div>';
    const рядом = document.getElementById('ref-sub-packaging');
    if (рядом && рядом.parentNode){
      рядом.parentNode.insertBefore(панель, рядом.nextSibling);
    } else {
      document.getElementById('view-references').appendChild(панель);
    }
  }

  /**
   * Переключение подвкладок прототипа перебирает свой список имён
   * и о наших не знает. Поэтому оборачиваем: вкладку единиц
   * показываем сами, остальное отдаём прототипу.
   */
  function перехватитьПереключение(){
    if (!window.showReferencesSub || window.showReferencesSub.__units) return;
    const прежняя = window.showReferencesSub;
    const обёртка = function(sub){
      const наша = sub === 'units';
      const панель = document.getElementById('ref-sub-units');
      if (!наша){
        if (панель) панель.classList.remove('active');
        return прежняя(sub);
      }
      document.querySelectorAll('#view-references .ref-sub').forEach(d => {
        d.classList.toggle('active', d.id === 'ref-sub-units');
      });
      document.querySelectorAll('[data-refsub]').forEach(b => {
        b.classList.toggle('active', b.getAttribute('data-refsub') === 'units');
      });
      загрузить();
    };
    обёртка.__units = true;
    window.showReferencesSub = обёртка;
  }

  /* ================= Данные ================= */

  async function загрузить(){
    try {
      const ответ = await api('GET', '/api/units');
      state.rows = ответ.units || [];
      state.canEdit = ответ.canEdit === true;
      state.loaded = true;
    } catch (e){
      state.rows = [];
      say(e.message);
    }
    нарисовать();
  }

  /* ================= Отрисовка ================= */

  function нарисовать(){
    const body = document.getElementById('unitsBody');
    if (!body) return;
    const head = document.getElementById('unitsHead');
    if (head){
      head.innerHTML = '<th>' + esc(T_('references.column.mkei_code')) + '</th>'
        + '<th>' + esc(T_('references.column.unit_symbol')) + '</th>'
        + '<th>Наименование</th><th class="num">Порядок</th><th>Используется</th>'
        + (state.canEdit ? '<th></th>' : '');
    }

    const строка = r => {
      if (state.form && state.form.id === r.unitId){
        return форма(r.isActive ? 'да' : 'нет');
      }
      return '<tr><td>' + esc(dash(r.mkeiCode)) + '</td>'
        + '<td><strong>' + esc(r.symbol) + '</strong></td>'
        + '<td>' + esc(r.name) + '</td>'
        + '<td class="num">' + esc(dash(r.sortOrder)) + '</td>'
        + '<td>' + (r.isActive ? 'да' : '<span class="count">отключён</span>') + '</td>'
        + (state.canEdit ? '<td style="white-space:nowrap;">'
            + кнопка('portalUnitEdit', r.unitId, 'Изменить')
            + кнопка('portalUnitToggle', r.unitId, r.isActive ? 'Отключить' : 'Включить')
            + кнопка('portalUnitDelete', r.unitId, 'Удалить')
            + '</td>' : '')
        + '</tr>';
    };

    let html = state.rows.map(строка).join('');
    if (state.form && state.form.id === null){
      html += форма('да');
    }
    body.innerHTML = html
      || '<tr><td colspan="6" style="color:var(--muted);">Справочник пуст</td></tr>';

    const actions = document.getElementById('unitsActions');
    if (actions){
      actions.innerHTML = state.canEdit
        ? '<button class="btn primary" type="button" id="unitsCreateBtn">+ Добавить</button>'
        : '';
      const btn = document.getElementById('unitsCreateBtn');
      if (btn) btn.onclick = () => portalUnitAdd();
    }
  }

  function кнопка(fn, id, подпись){
    return '<button class="btn ghost" type="button" onclick="' + fn + '(\'' + id + '\')">'
      + esc(подпись) + '</button> ';
  }

  // Длины те же, что у колонок базы: код МКЕИ — три знака,
  // обозначение — десять. Сервер проверяет это сам; здесь поле просто
  // не даёт набрать лишнее, чтобы отказ не приходил после «Сохранить».
  const ДЛИНА = {mkeiCode: 3, symbol: 10, name: 255};

  function форма(признак){
    const v = state.form.values;
    const поле = (col, num) => '<td><input class="kv-input" type="'
      + (num ? 'number' : 'text') + '" value="'
      + esc(v[col] === null || v[col] === undefined ? '' : v[col]) + '"'
      + (ДЛИНА[col] ? ' maxlength="' + ДЛИНА[col] + '"' : '')
      + ' data-unit="' + col + '" oninput="portalUnitField(this)"></td>';
    return '<tr>' + поле('mkeiCode') + поле('symbol') + поле('name')
      + поле('sortOrder', true) + '<td>' + esc(признак) + '</td>'
      + '<td style="white-space:nowrap;">'
      + '<button class="btn primary" type="button" onclick="portalUnitSave()">Сохранить</button> '
      + '<button class="btn ghost" type="button" onclick="portalUnitCancel()">Отмена</button>'
      + '</td></tr>';
  }

  /* ================= Действия ================= */

  window.portalUnitField = function(el){
    const v = el.value === '' ? null : el.value;
    state.form.values[el.getAttribute('data-unit')] = v;
  };

  window.portalUnitAdd = function(){
    state.form = {id: null, values: {}};
    нарисовать();
  };

  window.portalUnitEdit = function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!r) return;
    state.form = {id: r.unitId, values:
      {mkeiCode: r.mkeiCode, symbol: r.symbol, name: r.name, sortOrder: r.sortOrder}};
    нарисовать();
  };

  window.portalUnitCancel = function(){
    state.form = null;
    нарисовать();
  };

  window.portalUnitSave = async function(){
    const form = state.form;
    const r = form.id === null
      ? null : state.rows.find(x => String(x.unitId) === String(form.id));
    const body = {
      mkeiCode: form.values.mkeiCode,
      symbol: form.values.symbol,
      name: form.values.name,
      sortOrder: form.values.sortOrder === null || form.values.sortOrder === undefined
        ? null : Number(form.values.sortOrder),
      // правка не должна менять признак «действует»: он меняется
      // отдельной кнопкой, и иначе правка возвращала бы из архива
      isActive: r ? r.isActive : true
    };
    try {
      const ответ = form.id === null
        ? await api('POST', '/api/units', body)
        : await api('PUT', '/api/units/' + encodeURIComponent(form.id), body);
      применить(ответ);
      state.form = null;
      нарисовать();
      say('Сохранено');
    } catch (e){
      say(e.message);
    }
  };

  window.portalUnitToggle = async function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!r) return;
    try {
      применить(await api('POST', '/api/units/' + encodeURIComponent(id) + '/archive',
                          {active: !r.isActive}));
      нарисовать();
    } catch (e){
      say(e.message);
    }
  };

  window.portalUnitDelete = async function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!window.confirm('Удалить «' + (r ? r.name : id) + '»?')) return;
    try {
      применить(await api('DELETE', '/api/units/' + encodeURIComponent(id)));
      нарисовать();
      say('Удалено');
    } catch (e){
      say(e.message);
    }
  };

  function применить(ответ){
    if (!ответ) return;
    state.rows = ответ.units || [];
    state.canEdit = ответ.canEdit === true;
  }

  /* ================= Установка ================= */

  function install(){
    PORTAL.onLogin(() => {
      создатьВкладку();
      перехватитьПереключение();
      state.form = null;
      state.loaded = false;
      // Права и список — чужие после смены поклажедателя или роли.
      // Сбрасываем сразу, а не при следующем открытии вкладки: иначе
      // на экране остались бы кнопки правки от прошлого входа.
      state.rows = [];
      state.canEdit = false;
      const панель = document.getElementById('ref-sub-units');
      if (панель && панель.classList.contains('active')) загрузить();
      else нарисовать();
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
PATCH_GROUPFILTER = r"""
<!-- ===== Фильтры по группе и типу товара: значения из данных =====
     В прототипе списки фильтров были вписаны в вёрстку: «Сырьё»,
     «Мясная продукция», «Молочная продукция», «Готовые наборы» —
     данные вымышленной компании. После отказа от справочника групп
     (V38) группа и тип стали свободным текстом, и выбирать их нужно
     из того, что у клиента действительно есть, иначе фильтр
     предлагает то, чего нет, и молчит о том, что есть.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  /**
   * Заполняет один список выбора.
   *
   * Первый пункт — «Все» — остаётся из вёрстки: он размечен ключом
   * перевода, и трогать его незачем. Остальные заменяются.
   * Выбранное значение сохраняем: иначе обновление списка сбрасывало бы
   * фильтр, который человек только что поставил.
   */
  function заполнить(id, значения){
    const select = document.getElementById(id);
    if (!select) return;
    const было = select.value;
    const первый = select.querySelector('option');
    select.innerHTML = первый ? первый.outerHTML : '<option value=""></option>';
    значения.forEach(v => {
      select.insertAdjacentHTML('beforeend',
        '<option value="' + esc(v) + '">' + esc(v) + '</option>');
    });
    if (было && значения.indexOf(было) >= 0) select.value = было;
  }

  async function обновить(){
    let dict;
    try {
      const resp = await fetch('/api/sku/dictionaries', {headers: {'Accept': 'application/json'}});
      if (!resp.ok) return;
      dict = await resp.json();
    } catch (e){
      return;            // фильтр останется с одним пунктом «Все» — не беда
    }
    const имена = kind => ((dict && dict[kind]) || []).map(o => o.name).filter(Boolean);
    const группы = имена('group');
    заполнить('skuFilterGroup', группы);
    заполнить('skuFilterType', имена('type'));
    // В запасах фильтр по группе тот же: регистр показывает группу
    // товара (представление v_stock_lines отдаёт её строкой).
    заполнить('stockFilterGroup', группы);
  }

  // onLogin выполняется и после входа, и после смены поклажедателя
  // (PORTAL.reloadAll прогоняет тот же список), а группы у разных
  // клиентов свои — чужие в фильтре были бы утечкой.
  function install(){
    PORTAL.onLogin(() => { обновить(); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>

"""
PATCH_IMPORTLOG = r"""
<!-- ===== Журнал загрузок из Excel =====
     Подвкладка администрирования «Ошибки загрузки Excel» в прототипе
     была заполнена выдуманными строками. Теперь она читает настоящий
     журнал: замечания по всем поклажедателям за 90 дней.

     Замечание в журнале означает, что строка в справочник НЕ записана.
     Записанные строки здесь не нужны — их результат виден в самом
     справочнике.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const state = {imports: [], issues: [], depositorId: '', loaded: false};
  PORTAL.importLog = state;

  /** «09.10.2026, 13:45» из метки времени, как её отдаёт сервер. */
  function когда(iso){
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso).slice(0, 16).replace('T', ', ');
    const двa = n => String(n).padStart(2, '0');
    return двa(d.getDate()) + '.' + двa(d.getMonth() + 1) + '.' + d.getFullYear()
      + ', ' + двa(d.getHours()) + ':' + двa(d.getMinutes());
  }

  async function загрузить(){
    const адрес = '/api/admin/excel-imports'
      + (state.depositorId ? '?depositorId=' + encodeURIComponent(state.depositorId) : '');
    try {
      const resp = await fetch(адрес, {headers: {'Accept': 'application/json'}});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      const ответ = await resp.json();
      state.imports = ответ.imports || [];
      state.issues = ответ.issues || [];
      state.loaded = true;
    } catch (e){
      state.imports = [];
      state.issues = [];
      say(e.message);
    }
    нарисовать();
  }

  /* ================= Разметка ================= */

  function подготовить(){
    const панель = document.getElementById('admin-sub-excel');
    if (!панель || панель.dataset.wired) return;
    панель.dataset.wired = '1';

    // Выдуманные строки прототипа заменяем своей разметкой целиком:
    // шапка у настоящего журнала другая (добавился поклажедатель),
    // а подставлять данные в чужую таблицу значило бы зависеть
    // от порядка её колонок.
    панель.innerHTML = ''
      + '<div class="section-title" style="margin-top:0;">'
      + esc(T_('admin.title.excel_import_errors')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 12px;">'
      + esc(T_('admin.hint.import_log')) + '</div>'
      + '<div class="filters"><div class="fgroup">'
      + '<label>' + esc(T_('admin.label.depositor')) + '</label>'
      + '<select id="impLogDepositor"></select></div>'
      + '<div class="spacer"></div></div>'
      + '<div id="impLogSummary"></div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('admin.column.date_and_time')) + '</th>'
      + '<th>' + esc(T_('admin.column.depositor')) + '</th>'
      + '<th>' + esc(T_('admin.column.user')) + '</th>'
      + '<th>' + esc(T_('admin.column.file')) + '</th>'
      + '<th>' + esc(T_('admin.column.line')) + '</th>'
      + '<th>' + esc(T_('admin.column.error')) + '</th>'
      + '</tr></thead><tbody id="impLogBody"></tbody></table></div>';

    const выбор = document.getElementById('impLogDepositor');
    выбор.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
      + (PORTAL.session && PORTAL.session.depositors || []).map(d =>
          '<option value="' + esc(d.depositorId) + '">' + esc(d.orgName) + '</option>').join('');
    выбор.onchange = () => { state.depositorId = выбор.value; загрузить(); };
  }

  function нарисовать(){
    const тело = document.getElementById('impLogBody');
    if (!тело) return;

    тело.innerHTML = state.issues.map(i =>
        '<tr><td style="white-space:nowrap;">' + esc(когда(i.startedAt)) + '</td>'
      + '<td>' + esc(dash(i.depositorName)) + '</td>'
      + '<td>' + esc(dash(i.login)) + '</td>'
      + '<td>' + esc(dash(i.fileName)) + '</td>'
      + '<td class="num">' + esc(dash(i.rowNumber)) + '</td>'
      + '<td>' + esc(i.message)
      + (i.fileValue ? ' <span class="count">' + esc(i.fileValue) + '</span>' : '')
      + '</td></tr>').join('')
      || '<tr><td colspan="6" style="color:var(--muted);">'
         + esc(T_('admin.text.import_log_empty')) + '</td></tr>';

    // Сводка по загрузкам: сколько файлов, сколько позиций создано
    // и обновлено. Без неё журнал отвечает только на вопрос
    // «что не загрузилось», а «что загрузилось» — нигде.
    const сумма = поле => state.imports.reduce((s, r) => s + (r[поле] || 0), 0);
    const сводка = document.getElementById('impLogSummary');
    if (сводка){
      сводка.innerHTML = state.imports.length
        ? '<div class="hint" style="margin:0 0 10px;">'
          + esc(T_('admin.text.import_count')) + ': ' + state.imports.length
          + ' · ' + esc(T_('sku.message.created_items')) + ': ' + сумма('createdRows')
          + ' · ' + esc(T_('sku.message.updated_items')) + ': ' + сумма('updatedRows')
          + '</div>'
        : '';
    }
  }

  /* ================= Установка ================= */

  function install(){
    // Подвкладку открывает showAdminSub прототипа; оборачиваем её,
    // чтобы журнал читался при открытии, а не висел устаревшим.
    const прежняя = window.showAdminSub;
    if (typeof прежняя === 'function' && !прежняя.__implog){
      const обёртка = function(sub){
        const r = прежняя.apply(this, arguments);
        if (sub === 'excel'){
          подготовить();
          загрузить();
        }
        return r;
      };
      обёртка.__implog = true;
      window.showAdminSub = обёртка;
    }
    PORTAL.onLogin(() => {
      state.loaded = false;
      state.depositorId = '';
      const панель = document.getElementById('admin-sub-excel');
      if (панель && панель.classList.contains('active')){
        подготовить();
        загрузить();
      }
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
PATCH_I18N = r"""
<!-- ===== ЭТАП 7г: языки портала =====
     Словарь подписей переезжает из вёрстки в базу: переключатель
     берёт тексты с сервера, выбранный язык запоминается в учётной
     записи, а переводы ведутся в администрировании.
========================================================================= -->
<script>
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  const state = {locales: [], texts: {}, code: null, matrix: null};
  PORTAL.i18n = state;

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  /* ================= Применение языка ================= */

  /**
   * Подписи приходят с сервера одним списком «ключ → текст». Демо брало
   * их из I18N_MAP; подменяем сам словарь, чтобы работал и штатный
   * applyLanguage, и всё, что на него завязано.
   */
  function fillMap(texts){
    // I18N_MAP объявлена через const: это лексическая глобальная переменная,
    // свойства window у неё нет — обращаться надо по имени.
    Object.keys(texts).forEach(key => {
      if (!I18N_MAP[key]) I18N_MAP[key] = {};
      I18N_MAP[key][state.code] = texts[key];
    });
  }

  // Подписи вкладок демо берёт из своего короткого словаря на два языка.
  // Те же тексты уже есть в базе под этими ключами — подставляем их,
  // иначе на нидерландском шапка осталась бы русской.
  const NAV_KEYS = {orders: 'common.button.orders', receipts: 'common.button.receipts',
                    stock: 'common.button.stock', sku: 'references.button.products',
                    billing: 'common.button.billing', reports: 'common.button.reports',
                    admin: 'common.button.administration',
                    transfers: 'common.button.transfers'};

  function applyNav(){
    document.querySelectorAll('nav.tabs button[data-tab]').forEach(btn => {
      const text = state.texts[NAV_KEYS[btn.dataset.tab]];
      if (text) btn.textContent = text;
    });
  }

  async function apply(code){
    if (!code) return;
    state.texts = await api('GET', '/api/i18n/texts/' + encodeURIComponent(code));
    state.code = code;
    // подсказка для следующей загрузки страницы: с какого языка начинать,
    // чтобы списки рисовались сразу на нём
    try { window.localStorage.setItem('portalLocale', code); } catch (e) { /* не беда */ }
    fillMap(state.texts);
    // Прототип при смене языка перерисовывает разом все экраны, включая
    // скрытые, и на скрытом иногда падает на отсутствующем элементе.
    // Из-за одной такой ошибки не должен срываться весь перевод —
    // и тем более сохранение выбранного языка, которое идёт следом.
    safely('перерисовка экранов', () => {
      if (typeof applyLanguage === 'function') applyLanguage(code);
    });
    safely('подписи вкладок', applyNav);
    safely('подсказки в полях', applyAttributes);
    // разметка, нарисованная кодом до смены языка, тоже должна перевестись
    safely('нарисованные экраны', () => applyTo(document.body));
    rerender();
    markSwitch(code);
  }

  /**
   * Подсказка в поле и всплывающая подпись — это атрибуты, а не текст узла,
   * поэтому ключ для них лежит в отдельном атрибуте: data-i18n-placeholder
   * и data-i18n-title. Разметку их проставляет сборка.
   */
  /**
   * Переводит надписи в куске разметки. Нужна дважды: при смене языка —
   * для всей страницы, и при появлении новых узлов — для них одних.
   */
  function applyTo(root){
    if (!root || !root.querySelectorAll) return;
    const all = [root].concat(Array.from(root.querySelectorAll('[data-i18n]')));
    all.forEach(el => {
      if (!el.getAttribute || !el.hasAttribute('data-i18n')) return;
      const t = state.texts[el.getAttribute('data-i18n')];
      if (t) el.innerHTML = t;
    });
    Array.from(root.querySelectorAll('[data-i18n-placeholder]')).forEach(el => {
      const t = state.texts[el.getAttribute('data-i18n-placeholder')];
      if (t) el.setAttribute('placeholder', t);
    });
    Array.from(root.querySelectorAll('[data-i18n-title]')).forEach(el => {
      const t = state.texts[el.getAttribute('data-i18n-title')];
      if (t) el.setAttribute('title', t);
    });
  }

  /**
   * Половина экранов портала рисуется кодом уже после загрузки страницы —
   * таблицы, карточки, окна. Такие узлы появляются уже с ключами, но
   * применить перевод некому: смена языка прошла раньше. Поэтому следим
   * за появлением узлов и переводим их по мере добавления.
   */
  function watchNewNodes(){
    if (!window.MutationObserver) return;
    const observer = new MutationObserver(records => {
      if (!state.code) return;
      records.forEach(r => Array.from(r.addedNodes).forEach(node => {
        if (node.nodeType === 1) applyTo(node);
      }));
    });
    observer.observe(document.body, {childList: true, subtree: true});
  }

  function applyAttributes(){
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const t = state.texts[el.getAttribute('data-i18n-placeholder')];
      if (t) el.setAttribute('placeholder', t);
    });
    document.querySelectorAll('[data-i18n-title]').forEach(el => {
      const t = state.texts[el.getAttribute('data-i18n-title')];
      if (t) el.setAttribute('title', t);
    });
  }

  /**
   * Заголовки колонок. У каждой колонки при сборке появился ключ, и подпись
   * берётся по нему — одним местом и для шапки таблицы, и для окна
   * «⚙ Колонки». Переопределение названия поля поклажедателем идёт первым:
   * клиент назвал поле по-своему, и это важнее языка портала.
   */
  /**
   * Переименование поля поклажедателем задаётся одним текстом, без языков:
   * свой слой переводов для него ещё не сделан. Поэтому оно действует
   * на языке, на котором заведено (русский), а на других интерфейс
   * показывает обычную подпись — иначе посреди английской таблицы
   * оказывается русское слово.
   */
  function patchOwnFieldLabels(){
    const orig = window.getDepositorFieldLabel;
    if (typeof orig !== 'function') return;
    window.getDepositorFieldLabel = function(objectType, fieldId){
      if (state.code && state.code !== 'ru') return null;
      return orig(objectType, fieldId);
    };
  }

  function patchColumnLabels(){
    window.localizeColumnLabel = function(ruLabel, tableKey, colId){
      if (tableKey && colId && typeof getDepositorFieldLabel === 'function'){
        const own = getDepositorFieldLabel(tableKey, colId);
        if (own) return own;
      }
      let key = (tableKey && colId) ? 'col.' + tableKey + '.' + colId : null;
      if (!key && typeof colPickerTable === 'string' && colPickerTable){
        // окно выбора колонок зовёт эту функцию без таблицы — берём открытую
        const c = (tableColumns[colPickerTable] || []).find(x => x.label === ruLabel);
        if (c && c.key) key = c.key;
      }
      const t = key ? state.texts[key] : null;
      // подпись могла прийти готовым русским текстом — из карточек
      // документов и справочников, где ключа рядом нет
      return t || (typeof TRU === 'function' ? TRU(ruLabel) : ruLabel);
    };

    // Внутри карточек документов и регистров заголовок рисуется по самой
    // колонке. У колонки рядом лежит ключ — он точнее обратного поиска
    // по русскому тексту, потому что одна и та же подпись встречается
    // в разных таблицах с разным переводом.
    window.COLTH = function(c){
      if (!c) return '';
      const t = c.key ? state.texts[c.key] : null;
      if (t) return t;
      return window.localizeColumnLabel ? window.localizeColumnLabel(c.label) : c.label;
    };
  }

  /**
   * Перерисовка списков после смены языка.
   * <p>
   * Таблицы собираются кодом, и подписи в них подставляются в момент
   * отрисовки. Пока список не перерисован, он показывает язык, который
   * был при его построении. Прототип это делает сам, но одним куском:
   * первая же ошибка на скрытом экране обрывает остальные. Поэтому
   * вызываем поимённо и каждую — отдельно.
   */
  function rerender(){
    ['renderReceipts', 'renderOrders', 'renderTransfers', 'renderStock', 'renderSku',
     'renderPackaging', 'renderSuppliers', 'renderConsignees', 'renderReports',
     'renderBillingServices', 'renderBillingInvoices', 'renderAdminUsers',
     'renderAdminAccess', 'renderAdminDepositorAccess', 'renderAdminStaffAccess',
     'renderOrderTypes', 'renderDocTypes', 'renderDepositorsDir', 'renderWarehousesDir',
     'renderReportsConfigTable', 'renderFieldLabelsAdmin', 'renderReportRows',
     'populateReportsConfigDepositorFilter'
    ].forEach(name => {
      if (typeof window[name] === 'function') safely(name, window[name]);
    });
  }

  function safely(what, fn){
    try {
      fn();
    } catch (e){
      console.warn('Смена языка, ' + what + ': ' + e.message);
    }
  }

  function markSwitch(code){
    document.querySelectorAll('.lang span[data-locale]').forEach(s => {
      s.classList.toggle('active', s.getAttribute('data-locale') === code);
    });
  }

  function fillSwitch(box){
    box.innerHTML = state.locales.map(l =>
        '<span data-locale="' + esc(l.code) + '" title="' + esc(l.name) + '">'
        + esc(l.code.toUpperCase()) + '</span>').join('');
    box.querySelectorAll('span').forEach(s => {
      s.style.cursor = 'pointer';
      s.addEventListener('click', () => window.portalSetLanguage(s.getAttribute('data-locale')));
    });
  }

  /**
   * Переключатель: языки берутся с сервера, а не зашиты тремя кнопками.
   * Второй такой же ставим на форму входа — выбрать язык надо до того,
   * как человек прочтёт подписи полей, а шапка портала до входа скрыта.
   */
  async function buildSwitch(){
    const box = document.querySelector('.lang');
    if (!box) return;
    try {
      state.locales = await api('GET', '/api/i18n/locales');
    } catch (e){
      return;                    // без языков оставляем то, что в вёрстке
    }
    fillSwitch(box);

    const card = document.querySelector('#loginScreen .login-card');
    if (card && !document.getElementById('loginLang')){
      const lang = document.createElement('div');
      lang.className = 'lang';
      lang.id = 'loginLang';
      lang.style.cssText = 'display:flex; gap:10px; justify-content:center; margin-bottom:12px;';
      card.insertBefore(lang, card.firstChild);
      fillSwitch(lang);
    }
  }

  /**
   * Смена языка. Вошедшему сохраняем выбор в учётной записи — язык должен
   * ехать за человеком на другой компьютер. На форме входа сохранять
   * некуда и не нужно: язык применится и так, а после входа подставится
   * тот, что записан у пользователя.
   */
  window.portalSetLanguage = async function(code){
    try {
      await apply(code);
      // язык сохраняем только вошедшему: на форме входа сохранять некуда
      if (PORTAL.session && PORTAL.session.login){
        await api('POST', '/api/me/locale', {code: code});
      }
    } catch (e){
      say('Не удалось переключить язык: ' + e.message);
    }
  };

  /* ================= Ведение переводов ================= */

  // Ведение переводов — подвкладка администрирования, рядом с
  // пользователями и доступами. Прежде это был отдельный экран за
  // кнопкой в шапке: кнопка в одном ряду с подвкладками сбивала с толку,
  // потому что ведёт туда же, куда и они.
  function ensureScreen(){
    if (document.getElementById('admin-sub-uitexts')) return;
    const host = document.getElementById('view-admin');
    const tabs = document.querySelector('#view-admin .admin-subtabs');
    if (!host || !tabs) return;

    const btn = document.createElement('button');
    btn.className = 'admin-subtab-btn';
    btn.setAttribute('data-sub', 'uitexts');
    btn.setAttribute('data-i18n', 'admin.button.interface_texts');
    btn.textContent = 'Тексты интерфейса';
    btn.onclick = () => window.portalOpenUiTexts();
    tabs.appendChild(btn);

    const section = document.createElement('div');
    section.className = 'admin-sub';
    section.id = 'admin-sub-uitexts';
    section.innerHTML =
        '<div class="panel-head" style="margin-top:0;">'
      + '<div class="section-title" style="margin:0;"'
      + ' data-i18n="admin.button.interface_texts">Тексты интерфейса</div>'
      + '<span class="count" id="uiTextsCount"></span>'
      + '</div>'
      + '<div class="filters">'
      + '<div class="fgroup"><label>Поиск по тексту или ключу</label>'
      + '<input id="uiTextsSearch" oninput="portalUiTextsRender()" placeholder="напр. Создать"/></div>'
      + '<div class="fgroup"><label>Раздел</label>'
      + '<select id="uiTextsScope" onchange="portalUiTextsRender()">'
      + '<option value="">Все разделы</option></select></div>'
      + '<div class="fgroup"><label>Вид</label>'
      + '<select id="uiTextsElement" onchange="portalUiTextsRender()">'
      + '<option value="">Все виды</option></select></div>'
      + '<div class="fgroup"><label>Показывать</label>'
      + '<select id="uiTextsFilter" onchange="portalUiTextsRender()">'
      + '<option value="">Все ключи</option>'
      + '<option value="missing">Только без перевода</option></select></div>'
      + '<div class="spacer"></div></div>'
      + '<div class="table-card"><div class="table-scroll-x">'
      + '<table><thead><tr id="uiTextsHead"></tr></thead>'
      + '<tbody id="uiTextsBody"></tbody></table></div></div>';
    host.appendChild(section);
  }

  window.portalOpenUiTexts = async function(){
    ensureScreen();
    try {
      state.matrix = await api('GET', '/api/admin/ui-texts');
    } catch (e){
      say(e.message);
      return;
    }
    fillFilter('uiTextsScope', state.matrix.map(r => r.scope));
    fillFilter('uiTextsElement', state.matrix.map(r => r.element));
    showView('admin');
    showAdminSub('uitexts');
    portalUiTextsRender();
  };

  /** Заполняет выпадающий список значениями, которые реально встречаются. */
  function fillFilter(id, values){
    const el = document.getElementById(id);
    if (!el || el.dataset.filled) return;
    const uniq = Array.from(new Set(values.filter(Boolean))).sort();
    el.insertAdjacentHTML('beforeend',
        uniq.map(v => '<option>' + esc(v) + '</option>').join(''));
    el.dataset.filled = '1';
  }

  window.portalUiTextsRender = function(){
    const body = document.getElementById('uiTextsBody');
    if (!body || !state.matrix) return;
    const codes = state.locales.map(l => l.code);
    const head = document.getElementById('uiTextsHead');
    if (head){
      head.innerHTML = '<th style="min-width:90px;">Ключ</th><th>Раздел</th><th>Вид</th>'
        + '<th>Относится к</th>'
        + codes.map(c => '<th>' + esc(c.toUpperCase()) + '</th>').join('');
    }
    const q = (document.getElementById('uiTextsSearch')?.value || '').trim().toLowerCase();
    const onlyMissing = document.getElementById('uiTextsFilter')?.value === 'missing';

    const scope = document.getElementById('uiTextsScope')?.value || '';
    const element = document.getElementById('uiTextsElement')?.value || '';
    let rows = state.matrix;
    if (scope) rows = rows.filter(r => r.scope === scope);
    if (element) rows = rows.filter(r => r.element === element);
    if (q){
      rows = rows.filter(r => r.textKey.toLowerCase().includes(q)
          || codes.some(c => String(r.values[c] || '').toLowerCase().includes(q)));
    }
    if (onlyMissing){
      rows = rows.filter(r => codes.some(c => !r.values[c]));
    }
    const counter = document.getElementById('uiTextsCount');
    if (counter) counter.textContent = '(' + rows.length + ')';

    body.innerHTML = rows.length
      ? rows.map(r => '<tr><td><strong>' + esc(r.textKey) + '</strong></td>'
          + '<td>' + esc(r.scope) + '</td><td>' + esc(r.element) + '</td>'
          + '<td>' + esc(r.owner || '—') + '</td>'
          + codes.map(c => '<td><input class="kv-input" style="min-width:220px;" value="'
              + esc(r.values[c] || '') + '" data-key="' + esc(r.textKey) + '" data-locale="' + esc(c)
              + '" onchange="portalUiTextSave(this)"/></td>').join('')
          + '</tr>').join('')
      : '<tr><td colspan="' + (codes.length + 4) + '" style="color:var(--muted);">Ничего не найдено</td></tr>';
  };

  /** Сохранение по уходу из поля: отдельной кнопки на три сотни строк не напасёшься. */
  window.portalUiTextSave = async function(el){
    const key = el.getAttribute('data-key');
    const locale = el.getAttribute('data-locale');
    try {
      state.matrix = await api('PUT', '/api/admin/ui-texts',
          {textKey: key, locale: locale, value: el.value});
      if (locale === state.code) await apply(state.code);
      el.style.borderColor = 'var(--ok, #2e7d32)';
      setTimeout(() => { el.style.borderColor = ''; }, 1200);
    } catch (e){
      say(e.message);
      portalUiTextsRender();
    }
  };

  /**
   * Подвкладка администрирования. Прежде тут была кнопка в шапке
   * раздела: она стояла в одном ряду с подвкладками и вела туда же,
   * куда они, — отсюда путаница. Кнопку подвкладки и саму панель
   * создаёт ensureScreen.
   */
  function addAdminButton(){
    ensureScreen();
  }

  /* ================= Установка ================= */

  function install(){
    patchColumnLabels();
    patchOwnFieldLabels();
    watchNewNodes();
    // Демо отказывалось переключаться на казахский заглушкой — теперь
    // язык выбирается из того, что есть в базе, и заглушка не нужна.
    window.setLanguage = code => window.portalSetLanguage(code);

    buildSwitch().then(() => {
      const стартовый = (state.locales.find(l => l.isDefault) || state.locales[0] || {}).code;
      return apply(стартовый);
    }).catch(() => {});

    PORTAL.onLogin(() => {
      // язык из учётной записи: он мог отличаться от языка формы входа
      if (PORTAL.session && PORTAL.session.locale && PORTAL.session.locale !== state.code){
        apply(PORTAL.session.locale).catch(() => {});
      }
      if (['admin', 'developer'].includes(PORTAL.session && PORTAL.session.roleCode)){
        addAdminButton();
      }
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


# ---------------------------------------------------------------------------
# Разметка вёрстки ключами переводов
#
# Прототип размечен наполовину: 276 ключей вида data-i18n="t12" и больше
# пятисот надписей без ключа вовсе. Проставлять их руками по трёхмегабайтному
# файлу — работа, которую нельзя ни проверить, ни повторить. Поэтому ключи
# расставляет этот код, по словарю i18n_keys.json: он же служит источником
# для миграции V26, так что вёрстка и база заведомо знают одни и те же ключи.
# ---------------------------------------------------------------------------
import json as _json, os as _os

_HERE = _os.path.dirname(_os.path.abspath(__file__))
KEYS = _json.load(open(_os.path.join(_HERE, 'i18n_keys.json'), encoding='utf-8'))
OLDMAP = _json.load(open(_os.path.join(_HERE, 'i18n_oldmap.json'), encoding='utf-8'))

# (раздел, вид, русский текст) → ключ; плюс запасной поиск по тексту и виду
KEY_BY_CONTEXT = {(k['scope'], k['element'], k['ru']): k['key'] for k in KEYS}
KEY_BY_KIND = {}
KEY_BY_TEXT = {}
for _k in KEYS:
    KEY_BY_KIND.setdefault((_k['element'], _k['ru']), _k['key'])
    KEY_BY_TEXT.setdefault(_k['ru'], _k['key'])

# Экраны «О нас» и «Контакты» по решению заказчика не переводим.
SKIP_VIEWS = {'about', 'contacts'}
VIEW_SCOPE = {
    'receipts': 'receipts', 'transfers': 'transfers', 'receipt-detail': 'receipts',
    'transfer-detail': 'transfers', 'order-detail': 'orders', 'order-line-detail': 'orders',
    'receipt-line-detail': 'receipts', 'transfer-line-detail': 'transfers', 'stock': 'stock',
    'references': 'references', 'sku-detail': 'sku', 'product-detail': 'stock',
    'billing': 'billing', 'reports': 'reports', 'admin': 'admin', 'dev-settings': 'dev',
    'admin-user-edit': 'admin', 'admin-access-edit': 'admin', 'profile': 'profile',
    'orders': 'orders',
}
CYR = re.compile(r'[А-Яа-яЁё]')


def _view_bounds(html):
    out = []
    for m in re.finditer(r'<section class="view" id="view-([a-z-]+)"', html):
        out.append((m.start(), html.find('</section>', m.start()), m.group(1)))
    return out


def _lookup(scope, element, text):
    text = text.strip()
    # ищем от точного совпадения к общему: раздел и вид → вид → просто текст.
    # Последний шаг нужен, когда та же надпись заведена как кнопка, а стоит
    # в обычном блоке: текст один, ключ должен быть тот же.
    return (KEY_BY_CONTEXT.get((scope, element, text))
            or KEY_BY_CONTEXT.get(('common', element, text))
            or KEY_BY_KIND.get((element, text))
            or KEY_BY_TEXT.get(text))



# Нормализация подписи карточки к подписи колонки каталога: в карточке
# у части полей к названию приписана единица измерения («Длина, см»),
# а «Минимальное» местами сокращено до «Мин.».
def _norm_label(s):
    s = s.strip()
    for tail in (', см', ', кг', ', м³', ', °C', ', %', ', дней'):
        if s.endswith(tail):
            s = s[:-len(tail)]
    s = s.replace('Минимальное кол-во', 'Мин. кол-во')
    return s




def unkey_data_captions(html):
    """Снимает data-i18n с подписей, которые являются данными, а не текстом.

    #tabsOrg показывает название организации поклажедателя, #warehouseFilter —
    выбранный склад. В разметке демо у обоих стоял data-i18n (в прототипе это
    были просто примеры: «ТОО „Рога и Копыта"» и «Все склады»). Проход
    перевода проставляет такие подписи при каждом применении языка — и
    затирал выбранного поклажедателя примером из каталога. Снаружи это
    выглядело так: переключил клиента, обновил страницу — на кнопке снова
    «Рога и Копыта», хотя работа идёт с другим клиентом.

    Текст этих двух элементов ставит только код, и значения по умолчанию
    он берёт через T() — то есть перевод не теряется.
    """
    снято = 0
    for element_id in ('tabsOrg', 'warehouseFilter'):
        pattern = re.compile(r'(<div[^>]*\bid="' + element_id + r'"[^>]*>)')
        m = pattern.search(html)
        if not m:
            sys.exit('не найден элемент #' + element_id)
        tag = m.group(1)
        clean = re.sub(r'\s*data-i18n(?:-[a-z]+)?="[^"]*"', '', tag)
        if clean != tag:
            html = html[:m.start(1)] + clean + html[m.end(1):]
            снято += 1
    print('подписей-данных отвязано от перевода: %d' % снято)
    return html


def move_system_fields(html):
    """Переносит «Статус товара» и «Данные WMS действительны»
    на вкладку служебной информации.

    Оба значения ставит обмен с WMS, а не человек: в основной информации
    и в признаках они выглядели как заполняемые вручную. Разбивка
    на экране настройки видимости полей берётся из базы (V36), и если
    не передвинуть строки в карточке, экран и карточка разойдутся —
    администратор увидит поле на одной вкладке, а клиент на другой.
    """
    start = html.index('function openSkuItem')
    end = html.index('let fcActiveSkuTab', start)
    card = html[start:end]

    переносимые = [
        "      fcKv(L('Статус товара', 'statusProduct'), p.statusProduct),\n",
        "      fcKv('Данные WMS действительны', fcYesNo(f.isWmsActive)),\n",
    ]
    for строка in переносимые:
        if строка not in card:
            sys.exit('не найдена строка карточки для переноса: ' + строка.strip())
        card = card.replace(строка, '', 1)

    # Вставляем первыми в служебную информацию: статус и признак
    # действительности данных — то, с чего человек начинает разбор.
    якорь = "    {title:'Служебная информация', rows:[\n"
    if якорь not in card:
        sys.exit('не найдена вкладка служебной информации в карточке товара')
    card = card.replace(якорь, якорь + ''.join(переносимые), 1)

    print('полей перенесено на вкладку служебной информации: %d' % len(переносимые))
    return html[:start] + card + html[end:]


def mark_card_columns(html):
    """Привязывает строки карточки товара к колонкам справочника.

    Карточка рисует поля жёстким списком: fcKv('Длина, см', p.length).
    Пока строка не знает, какой колонке она отвечает, запрет поля
    поклажедателю убирал бы его из шаблона Excel и из выбора колонок,
    а в карточке поле оставалось бы — настройка работала бы наполовину.

    Поэтому на сборке каждая строка получает идентификатор колонки:
    fcKv(...) -> fcKvC('length', ...). Сопоставление идёт по подписям из
    самого прототипа (skuColumnDefs), а не по отдельному списку — и если
    подпись не нашлась, сборка падает: молча потерянная привязка хуже,
    чем остановленная сборка.
    """
    defs_at = html.index('skuColumnDefs')
    defs = html[defs_at:defs_at + 16000]
    label_to_col = {}
    for cid, lab in re.findall(
            r"id:\s*'([A-Za-z0-9_]+)'[^}]{0,200}?label:\s*'((?:[^'\\]|\\.)*)'", defs):
        label_to_col.setdefault(_norm_label(lab.replace("\\'", "'")), cid)

    start = html.index('function openSkuItem')
    end = html.index('let fcActiveSkuTab', start)
    card = html[start:end]

    # Доп. параметры и в карточке, и в справочнике строятся в цикле:
    # подпись у них склеена с номером ('Доп. параметр ' + (i+1)), а
    # идентификатор — 'customAttr' + (i+1). По подписи их сопоставлять
    # нельзя: получилась бы одна колонка customAttr вместо десяти.
    # Поэтому разбираем их отдельно и до общего прохода.
    label_to_col.pop('Доп. параметр', None)

    missing = []
    count = [0]

    def repl(m):
        whole, label = m.group(0), m.group('label').replace("\\'", "'")
        col = label_to_col.get(_norm_label(label))
        if not col:
            missing.append(label)
            return whole
        count[0] += 1
        return whole.replace('fcKv(', "fcKvC('" + col + "', ", 1)

    custom_old = "fcKv('Доп. параметр ' + (i+1), v)"
    if custom_old not in card:
        sys.exit('не найдены доп. параметры в карточке товара')
    card = card.replace(custom_old,
                        "fcKvC('customAttr'+(i+1), 'Доп. параметр ' + (i+1), v)")
    count[0] += 1

    card = re.sub(r"fcKv\(\s*'(?P<label>(?:[^'\\]|\\.)*)'", repl, card)
    card = re.sub(r"fcKv\(\s*L\('(?P<label>(?:[^'\\]|\\.)*)'", repl, card)

    if missing:
        sys.exit('в карточке товара не опознаны поля: ' + ', '.join(sorted(set(missing))))

    print('строк карточки товара привязано к колонкам: %d' % count[0])
    return html[:start] + card + html[end:]


def mark_up(html):
    """Проставляет data-i18n там, где его не было, и переводит старые ключи."""
    state = {'bounds': [], 'scripts': []}

    def refresh():
        # Границы экранов и скриптов считаются по положению в файле, а файл
        # между проходами меняется. Если не пересчитывать, смещения уезжают
        # и часть разметки ошибочно принимается за код внутри <script>.
        state['bounds'] = _view_bounds(html)
        state['scripts'] = [(m.start(), m.end())
                            for m in re.finditer(r'<script\b[^>]*>.*?</script>', html, re.S)]

    def scope_at(pos):
        for a, b, name in state['bounds']:
            if a <= pos <= b:
                return None if name in SKIP_VIEWS else VIEW_SCOPE.get(name, 'common')
        return 'common'

    def in_script(pos):
        return any(a <= pos <= b for a, b in state['scripts'])

    refresh()
    # 1) старые ключи t12 → осмысленные
    def rename(m):
        return 'data-i18n="%s"' % OLDMAP.get(m.group(1), m.group(1))
    html = re.sub(r'data-i18n="(t\d+)"', rename, html)

    refresh()
    # 2) текст элементов без ключа
    stats = {'added': 0, 'skipped': 0}
    TAGS = [('button', 'button'), ('label', 'label'), ('option', 'option'),
            ('h1', 'title'), ('h2', 'title'), ('h3', 'title'), ('th', 'column')]
    for tag, element in TAGS:
        pattern = re.compile(r'<%s(?![a-z])([^>]*)>([^<>]+)</%s>' % (tag, tag))

        def repl(m):
            attrs, text = m.group(1), m.group(2)
            if 'data-i18n' in attrs or not CYR.search(text):
                return m.group(0)
            scope = scope_at(m.start())
            if scope is None or in_script(m.start()):
                return m.group(0)
            key = _lookup(scope, element, html_unescape(text))
            if not key:
                stats['skipped'] += 1
                return m.group(0)
            stats['added'] += 1
            return '<%s data-i18n="%s"%s>%s</%s>' % (tag, key, attrs, text, tag)

        html = pattern.sub(repl, html)

    refresh()
    # 2б) надписи в обычных блоках: подхватываем только те, что дословно
    #     совпали со словарём, — остальное на экране это данные
    for tag in ('div', 'span', 'a', 'p', 'strong', 'small', 'legend'):
        pattern = re.compile(r'<%s(?![a-z])([^>]*)>([^<>]+)</%s>' % (tag, tag))

        def repl_any(m, tag=tag):
            attrs, text = m.group(1), m.group(2)
            if 'data-i18n' in attrs or not CYR.search(text):
                return m.group(0)
            scope = scope_at(m.start())
            if scope is None or in_script(m.start()):
                return m.group(0)
            key = _lookup(scope, 'text', html_unescape(text))
            if not key:
                return m.group(0)
            stats['added'] += 1
            return '<%s data-i18n="%s"%s>%s</%s>' % (tag, key, attrs, text, tag)

        html = pattern.sub(repl_any, html)

    refresh()
    # 2в) заголовок со счётчиком: «Перемещения <span class="count">(0)</span>».
    #     Целиком такой элемент ключом не пометить — перевод затёр бы счётчик,
    #     поэтому в ключ заворачивается только сама надпись.
    for tag in ('h1', 'h2', 'h3', 'button', 'label', 'th'):
        pattern = re.compile(r'(<%s(?![a-z])[^>]*>)([^<>]*[А-Яа-яЁё][^<>]*)(<)' % tag)

        def repl_lead(m):
            opening, text, rest = m.group(1), m.group(2), m.group(3)
            if 'data-i18n' in opening:
                return m.group(0)
            scope = scope_at(m.start())
            if scope is None or in_script(m.start()):
                return m.group(0)
            key = _lookup(scope, 'title', html_unescape(text))
            if not key:
                return m.group(0)
            stats['added'] += 1
            tail = text[len(text.rstrip()):]
            return '%s<span data-i18n="%s">%s</span>%s%s' % (
                opening, key, text.strip(), tail, rest)

        html = pattern.sub(repl_lead, html)

    refresh()
    # 2б) подпись после вложенного значка: «<button><span>+</span> Добавить</button>».
    #     Такой текст не примыкает к открывающему тегу, поэтому проходы выше
    #     его не видят — оборачиваем остаток в отдельный span с ключом.
    tail_pattern = re.compile(r'(</span>)(\s*)([А-ЯЁа-яё][^<>]*?)(\s*)(</(?:button|label|a)>)')

    def repl_tail(m):
        close, gap, text, gap2, end = m.groups()
        if in_script(m.start()):
            return m.group(0)
        scope = scope_at(m.start())
        if scope is None:
            return m.group(0)
        key = _lookup(scope, 'button', html_unescape(text.strip()))
        if not key:
            stats['skipped'] += 1
            return m.group(0)
        stats['added'] += 1
        return '%s%s<span data-i18n="%s">%s</span>%s%s' % (
            close, gap, key, text.strip(), gap2, end)

    html = tail_pattern.sub(repl_tail, html)

    refresh()
    # 3) подсказки в полях и всплывающие подписи — это атрибуты, а не текст,
    #    поэтому ключ кладём в отдельный атрибут, а экран его читает сам
    for attr, element, marker_attr in (('placeholder', 'placeholder', 'data-i18n-placeholder'),
                                       ('title', 'hint', 'data-i18n-title')):
        pattern = re.compile(r'(<[a-z][^>]*?)\s%s="([^"]+)"' % attr)

        def repl_attr(m):
            head, text = m.group(1), m.group(2)
            if marker_attr in head or not CYR.search(text):
                return m.group(0)
            scope = scope_at(m.start())
            if scope is None or in_script(m.start()):
                return m.group(0)
            key = _lookup(scope, element, html_unescape(text))
            if not key:
                stats['skipped'] += 1
                return m.group(0)
            stats['added'] += 1
            return '%s %s="%s" %s="%s"' % (head, marker_attr, key, attr, text)

        html = pattern.sub(repl_attr, html)

    # 4) заголовки колонок: подпись остаётся русской на случай, если тексты
    #    не загрузились, а рядом ложится ключ — по нему таблица и окно
    #    «⚙ Колонки» берут перевод
    def col_key(m):
        cid, label = m.group(1), m.group(2)
        table = _column_table(html, m.start())
        key = 'col.%s.%s' % (table, cid) if table else None
        if not key or key not in COLUMN_KEYS:
            return m.group(0)
        return "{id:'%s', key:'%s', label:'%s'" % (cid, key, label)

    html = re.sub(r"\{\s*id\s*:\s*'([^']+)'\s*,\s*label\s*:\s*'([^']*)'", col_key, html)

    # Правило выше ждёт, что label идёт сразу за id. Три колонки товара
    # написаны иначе — у hasPackaging между ними стоит dbField, у orderUnit
    # labelEn, у guid просто широкий отступ — и ключа они не получали.
    # Отсюда они пропадали из окна «⚙ Колонки»: экран строит список
    # по раскладке с сервера, а в справочнике колонок их не было.
    def col_key_any(m):
        head, cid = m.group(1), m.group(2)
        if "key:" in m.group(0):
            return m.group(0)
        table = _column_table(html, m.start())
        key = 'col.%s.%s' % (table, cid) if table else None
        if not key or key not in COLUMN_KEYS:
            return m.group(0)
        stats['added'] += 1
        return "%sid:'%s', key:'%s'," % (head, cid, key)

    html = re.sub(r"(\{\s*)id\s*:\s*'([A-Za-z][\w]*)'\s*,(?![^\n]{0,12}key\s*:)",
                  col_key_any, html)

    # Колонки «Доп. параметр N» и «Доп. поле N» прототип создаёт
    # выражением: id:'customAttr'+(i+1). Это не литерал, поэтому ни одно
    # правило выше их не видело — а их пятьдесят, по десять на пять таблиц.
    def col_key_generated(m):
        prefix, expr = m.group(1), m.group(2)
        table = _column_table(html, m.start())
        if not table:
            return m.group(0)
        # ключ собирается тем же выражением, что и идентификатор
        return "id:'%s'+%s, key:'col.%s.%s'+%s," % (prefix, expr, table, prefix, expr)

    html, n_gen = re.subn(r"id\s*:\s*'(custom(?:Attr|Field))'\s*\+\s*(\(i\+1\)|\(i \+ 1\)|i)\s*,",
                          col_key_generated, html)
    stats['added'] += n_gen
    print('ключей проставлено: %d (из них генерируемых: %d), не нашлось: %d'
          % (stats['added'], n_gen, stats['skipped']))
    return html


COLUMN_KEYS = {k['key'] for k in KEYS if k['key'].startswith('col.')}
# где какая таблица объявлена — определяем один раз по именам переменных
COLUMN_BLOCKS = [
    ('stockColumnDefs', 'stock'), ('stockBatchColumnDefs', 'stockBatch'),
    ('receiptCompositionColumnDefs', 'receiptComposition'),
    ('orderCompositionColumnDefs', 'orderComposition'),
    ('transferCompositionColumnDefs', 'transferComposition'),
    ('orderLineDetailColumnDefs', 'orderLineDetail'),
    ('receiptLineDetailColumnDefs', 'receiptLineDetail'),
    ('transferLineDetailColumnDefs', 'transferLineDetail'),
    ('skuColumnDefs', 'sku'),
]


def _column_table(html, pos):
    """Какой таблице принадлежит определение колонки на этой позиции."""
    best = None
    for var, table in COLUMN_BLOCKS:
        i = html.find('const %s = [' % var)
        if i < 0 or i > pos:
            continue
        end = _array_end(html, html.index('[', i))
        if i <= pos <= end:
            return table
    for name in ('receipts', 'orders', 'transfers'):
        m = re.search(r'(?m)^  %s:\s*\[' % name, html)
        if m and m.start() <= pos:
            end = _array_end(html, html.index('[', m.start()))
            if pos <= end:
                best = name
    return best


def _array_end(html, start):
    depth = 0
    for i in range(start, len(html)):
        if html[i] == '[':
            depth += 1
        elif html[i] == ']':
            depth -= 1
            if depth == 0:
                return i
    return len(html)


def html_unescape(text):
    import html as _html
    return _html.unescape(text).strip()



# ---------------------------------------------------------------------------
# Перевод строк, которые блоки собирают в коде
#
# Литерал 'Сохранить' заменяется на T('common.text.save'), а внутри
# собираемой разметки '>Сохранить<' — на '>' + T(...) + '<'. Замена идёт
# по точному совпадению со словарём, построенным при разборе портала,
# поэтому она повторяема: пересобрали файл — получили то же самое.
# ---------------------------------------------------------------------------
RU2KEY = _json.load(open(_os.path.join(_HERE, 'i18n_ru2key.json'), encoding='utf-8'))


def translate_blocks(text):
    used = {}

    def esc(s):
        return s.replace('\\', '\\\\').replace("'", "\\'")

    # сначала длинные строки: иначе короткая подстрока съест часть длинной
    for ru in sorted(RU2KEY, key=len, reverse=True):
        key = RU2KEY[ru]
        lit = "'" + ru + "'"
        if lit in text:
            # литерал в позиции ключа объекта ('Текст': ...) трогать нельзя:
            # вызов функции ключом быть не может, и файл перестанет грузиться
            def _swap(m):
                head, tail = m.group(1), m.group(2)
                # ключом объекта литерал бывает только после '{' или ','.
                # После '?' двоеточие — это тернарник, и там замена нужна.
                if tail.lstrip().startswith(':') and head.rstrip().endswith(('{', ',')):
                    return m.group(0)
                used[key] = ru
                return "%sT('%s')%s" % (head, key, tail)
            # экранированную кавычку (внутри onclick="notify(\'…\')") не трогаем:
            # подстановка разорвала бы строку
            text = re.sub(r'(.?)(?<!\\)' + re.escape(lit) + r'(\s*.?)', _swap, text)
        # внутри собираемой разметки текст нередко окружён пробелами
        # («/> показывать архивные</label>») — их сохраняем как есть
        def _inner(m):
            used[key] = ru
            return ">%sT('%s')%s<" % (m.group(1) and "' + '" + m.group(1) + "' + " or "' + ",
                                      key,
                                      m.group(2) and " + '" + m.group(2) + "' + '" or " + '")
        text = re.sub(r'>(\s*)' + re.escape(ru) + r'(\s*)<', _inner, text)

        # текст может и начинать литерал: '<input .../>' + ' показывать архивные</label>'
        def _head(m):
            used[key] = ru
            return "%s%s' + T('%s') + '%s<" % (m.group(1), m.group(2), key, m.group(3))
        text = re.sub(r"(['\"])(\s*)" + re.escape(ru) + r'(\s*)<', _head, text)
    # Подсказки полей внутри собираемой разметки: это атрибут, а не текст,
    # поэтому рядом ложится ключ — экран читает его сам.
    def _ph(m):
        attr, ru = m.group(1), m.group(2)
        key = RU2KEY.get(ru)
        if not key or 'data-i18n-' + attr in m.group(0):
            return m.group(0)
        used[key] = ru
        return 'data-i18n-%s="%s" %s="%s"' % (attr, key, attr, ru)

    text = re.sub(r'(placeholder|title)="([^"<>]*[А-Яа-яЁё][^"<>]*)"', _ph, text)

    # Нумерованные подписи в блоках: 'Доп. параметр ' + i
    for ru in ('Доп. поле', 'Доп. параметр'):
        key = RU2KEY.get(ru)
        if not key:
            continue
        pat = re.compile(r"'%s '\s*\+\s*(\(\s*\w+\s*\+\s*1\s*\)|\w+)" % re.escape(ru))
        text, n = pat.subn(lambda m, key=key: "T('%s')+' '+%s" % (key, m.group(1)), text)
        if n:
            used[key] = ru
    print('строк в блоках переведено: %d' % len(used))
    return text, used


def _enclosing_quote(code, pos):
    """Какой кавычкой открыта строка, внутри которой мы находимся.

    Ищем назад по той же строке файла: разбор всего скрипта целиком
    сбивается на регулярных выражениях и апострофах в комментариях,
    а здесь достаточно ближайшей кавычки слева.
    """
    line_start = code.rfind('\n', 0, pos) + 1
    quote = None
    i = line_start
    while i < pos:
        ch = code[i]
        if ch == '\\':
            i += 2
            continue
        if ch in '\'"`':
            quote = None if quote == ch else (quote or ch)
        i += 1
    if quote:
        return quote

    # Разметка в прототипе часто собирается многострочной шаблонной строкой,
    # и кавычка тогда осталась на одной из предыдущих строк. Считаем обратные
    # кавычки назад: нечётное число — мы внутри такой строки.
    before = code[:line_start]
    if before.count('`') % 2 == 1:
        return '`'

    # Строка целиком состоит из разметки, а кавычки на ней нет — значит,
    # она продолжает многострочную шаблонную строку. Счёт обратных кавычек
    # выше иногда сбивается на апострофах в комментариях, поэтому
    # проверяем ещё и по виду строки.
    line = code[line_start:code.find('\n', line_start) if code.find('\n', line_start) > 0 else len(code)]
    stripped = line.strip()
    if stripped.startswith('<') or '</' in stripped:
        return '`'
    return None


def translate_prototype_js(html):
    """Переводит надписи, которые собирает код самого прототипа.

    Трогаем только заведомо интерфейсные места: текст внутри собираемой
    разметки (>Текст<) и сообщения notify/confirm. Демо-данные — названия
    товаров, адреса — остаются как есть: на экране их заменяют данные
    из базы, и переводить там нечего.
    """
    used = {}

    def convert(code):
        def inner(mm):
            text = mm.group(1)
            key = RU2KEY.get(text.strip())
            if not key:
                return mm.group(0)
            quote = _enclosing_quote(code, mm.start())
            if quote is None:
                return mm.group(0)
            used[key] = text.strip()
            if quote == '`':
                return '>${T(\'%s\')}<' % key
            return '>%s + T(\'%s\') + %s<' % (quote, key, quote)

        code = re.sub(r'>([^<>${}\'"`]*[А-Яа-яЁё][^<>${}\'"`]*)<', inner, code)

        def msg(mm):
            fn, q, text = mm.group(1), mm.group(2), mm.group(3)
            key = RU2KEY.get(text.strip())
            if not key:
                return mm.group(0)
            used[key] = text.strip()
            return "%s(T('%s')" % (fn, key)

        code = re.sub(r"\b(notify|confirm)\((['\"])([^'\"]+)\2", msg, code)

        # textContent = 'Надпись' — тоже надпись на экране, а не данные
        def assign(mm):
            head, q, text = mm.group(1), mm.group(2), mm.group(3)
            key = RU2KEY.get(text.strip())
            if not key:
                return mm.group(0)
            used[key] = text.strip()
            return "%sT('%s')" % (head, key)

        code = re.sub(r"((?:textContent|innerHTML|placeholder)\s*=\s*)(['\"])([^'\"]+)\2",
                      assign, code)

        # заголовок раздела карточки: {title:'Основная информация', rows:[…]}
        def prop(mm):
            head, q, text = mm.group(1), mm.group(2), mm.group(3)
            key = RU2KEY.get(text.strip())
            if not key:
                return mm.group(0)
            used[key] = text.strip()
            return "%sT('%s')" % (head, key)

        code = re.sub(r"(\btitle\s*:\s*)(['\"])([^'\"]+)\2", prop, code)

        # label:'Надпись' в списках вида «типы объектов». Колонки таблиц
        # пропускаем: у них рядом есть render, и подпись там берётся по ключу,
        # а русский текст служит запасным.
        def prop_label(line):
            if 'render:' in line:
                return line
            return re.sub(r"(\blabel\s*:\s*)(['\"])([^'\"]+)\2", prop, line)

        code = '\n'.join(prop_label(l) for l in code.split('\n'))

        # надпись может стоять в условии: textContent = всё ? 'Все склады' : имя
        def ternary(line):
            if not re.search(r'\b(textContent|innerHTML)\s*=', line):
                return line

            def one(mm):
                q, text = mm.group(1), mm.group(2)
                key = RU2KEY.get(text.strip())
                if not key:
                    return mm.group(0)
                used[key] = text.strip()
                return "T('%s')" % key

            return re.sub(r"(['\"])([^'\"]+)\1", one, line)

        return '\n'.join(ternary(l) for l in code.split('\n'))

    out, pos = [], 0
    for m in re.finditer(r'(<script\b[^>]*>)(.*?)(</script>)', html, re.S):
        out.append(html[pos:m.start()])
        out.append(m.group(1) + convert(m.group(2)) + m.group(3))
        pos = m.end()
    out.append(html[pos:])
    print('надписей в коде прототипа переведено: %d' % len(used))
    return ''.join(out), used


# ---------------------------------------------------------------------------
# Надписи, склеенные с данными
#
# «Поступление ПН-000148», «Доп. поле 3» — это не одна строка, а подпись
# плюс номер. Общие правила замены такое не берут: текст не окружён тегами
# и не стоит отдельным литералом. Здесь они перечислены поимённо — список
# короткий, и каждая замена видна глазом.
# ---------------------------------------------------------------------------
GLUED = [
    # заголовок карточки документа: подпись + номер + бейдж статуса
    ("`Поступление ${r.erpNo || r.no}", "`${T('%s')} ${r.erpNo || r.no}"),
    ("`Заказ на отгрузку ${o.erpNo || o.no}", "`${T('%s')} ${o.erpNo || o.no}"),
    ("`Перемещение ${t.erpNo || t.no}", "`${T('%s')} ${t.erpNo || t.no}"),
    # названия типов документов в заголовках списков строк
    ("{receipts:'Поступление', orders:'Заказ на отгрузку', transfers:'Перемещение'}",
     "{receipts:T('%s'), orders:T('%s'), transfers:T('%s')}"),
    # нумерованные поля и параметры
    ("label:'Доп. поле '+(i+1)", "label:T('%s')+' '+(i+1)"),
    ("label:'Доп. параметр ' + (i+1)", "label:T('%s')+' '+(i+1)"),
    ("label:'Доп. параметр '+(i+1)", "label:T('%s')+' '+(i+1)"),
]
GLUED_KEYS = {
    'Поступление': None, 'Заказ на отгрузку': None, 'Перемещение': None,
    'Доп. поле': None, 'Доп. параметр': None,
}


def translate_glued(html, used):
    for ru in GLUED_KEYS:
        GLUED_KEYS[ru] = RU2KEY.get(ru)
    pairs = [
        ("`Поступление ${r.erpNo || r.no}", "`${T('" + (GLUED_KEYS['Поступление'] or '') + "')} ${r.erpNo || r.no}", 'Поступление'),
        ("`Заказ на отгрузку ${o.erpNo || o.no}", "`${T('" + (GLUED_KEYS['Заказ на отгрузку'] or '') + "')} ${o.erpNo || o.no}", 'Заказ на отгрузку'),
        ("`Перемещение ${t.erpNo || t.no}", "`${T('" + (GLUED_KEYS['Перемещение'] or '') + "')} ${t.erpNo || t.no}", 'Перемещение'),
        ("{receipts:'Поступление', orders:'Заказ на отгрузку', transfers:'Перемещение'}",
         "{receipts:T('" + (GLUED_KEYS['Поступление'] or '') + "'), orders:T('"
         + (GLUED_KEYS['Заказ на отгрузку'] or '') + "'), transfers:T('"
         + (GLUED_KEYS['Перемещение'] or '') + "')}", 'Поступление'),
        ("label:'Доп. поле '+(i+1)", "label:T('" + (GLUED_KEYS['Доп. поле'] or '') + "')+' '+(i+1)", 'Доп. поле'),
        ("label:'Доп. параметр '+(i+1)", "label:T('" + (GLUED_KEYS['Доп. параметр'] or '') + "')+' '+(i+1)", 'Доп. параметр'),
        ("label:'Доп. параметр ' + (i+1)", "label:T('" + (GLUED_KEYS['Доп. параметр'] or '') + "')+' '+(i+1)", 'Доп. параметр'),
    ]
    count = 0
    for old, new, ru in pairs:
        if old in html:
            count += html.count(old)
            used[RU2KEY[ru]] = ru
            html = html.replace(old, new)

    # Нумерованные подписи встречаются в нескольких написаниях
    # ('Доп. поле '+(i+1), 'Доп. поле ' + (i+1), и в таблице подписей
    # по индексу), поэтому берём их выражением, а не перечислением.
    for ru in ('Доп. поле', 'Доп. параметр'):
        key = RU2KEY.get(ru)
        if not key:
            continue
        # счётчик пишут и как (i+1), и просто как i — берём оба
        pat = re.compile(r"'%s '\s*\+\s*(\(\s*\w+\s*\+\s*1\s*\)|\w+)"
                         % re.escape(ru))

        def repl(m, key=key):
            return "T('%s')+' '+%s" % (key, m.group(1))

        html, n = pat.subn(repl, html)
        if n:
            used[key] = ru
            count += n
    print('склеенных надписей переведено: %d' % count)
    return html



# ---------------------------------------------------------------------------
# Заголовки колонок внутри карточек документов и регистров прототип рисует
# как ${c.label} — сырой русской подписью. Список колонок рядом несёт ключ
# (key:'col.<таблица>.<колонка>'), поэтому достаточно позвать общую функцию
# перевода и передать ей саму колонку.
# ---------------------------------------------------------------------------
def translate_column_headers(html):
    count = 0
    for old in ('${c.label}</th>', '${c.label}${arrow}</th>'):
        n = html.count(old)
        if n:
            html = html.replace(old, old.replace('${c.label}', "${typeof COLTH === 'function' ? COLTH(c) : c.label}"))
            count += n
    print('заголовков колонок в карточках переведено: %d' % count)
    return html


def main():
    html = open(SRC, encoding='utf-8').read()
    if '</body>' not in html:
        sys.exit('не найден </body>')
    # Подсказка в поле логина: в демо стоял адрес вымышленной компании.
    # Кириллицы в нём нет, поэтому общий проход по placeholder его не видит —
    # меняем явно и сразу вешаем ключ, чтобы подсказка жила в каталоге текстов.
    old_login_hint = 'placeholder="a.serikova@rogaikopyta.kz"'
    if old_login_hint in html:
        html = html.replace(
            old_login_hint,
            'data-i18n-placeholder="auth.placeholder.user_ahlers_com" '
            'placeholder="user@ahlers.com"', 1)
        print('подсказка логина заменена на user@ahlers.com')
    else:
        sys.exit('не найдена подсказка логина в демо')
    html = move_system_fields(html)
    html = mark_card_columns(html)
    html = mark_up(html)
    html = unkey_data_captions(html)
    html, used_proto = translate_prototype_js(html)
    html = translate_glued(html, used_proto)
    html = translate_column_headers(html)
    blocks = (PATCH + PATCH_DOCS + PATCH_CATALOG + PATCH_CARD + PATCH_SKU + PATCH_DRAFT
              + PATCH_AUTH + PATCH_ADMIN + PATCH_ACCESS + PATCH_SKU_EDIT + PATCH_PARTNERS
              + PATCH_PORTALDIR + PATCH_COLUMNS + PATCH_EXCEL + PATCH_IMPORT + PATCH_UNITS + PATCH_GROUPFILTER + PATCH_IMPORTLOG + PATCH_DEPVIS + PATCH_BILLING
              + PATCH_I18N)
    blocks, used = translate_blocks(blocks)
    used.update(used_proto)

    # Запасной русский текст нужен каждому ключу, который встречается
    # в собранной странице, а не только тем, которые подставила замена.
    # Ключ, записанный в коде константой (PACKAGING_TAB =
    # 'common.label.packaging'), заменой не проходит и в словарь не попадал —
    # и до загрузки переводов на экране показывался сам ключ вместо подписи.
    # Поэтому добираем все ключи каталога, которые есть в тексте страницы.
    whole = html + blocks
    added = 0
    for item in KEYS:
        key, ru = item.get('key'), item.get('ru')
        if not key or not ru or key in used:
            continue
        if ("'" + key + "'") in whole or ('"' + key + '"') in whole:
            used[key] = ru
            added += 1
    print('ключей добрано в запасной словарь: %d (всего %d)' % (added, len(used)))

    prelude = (PATCH_T.replace('__RU_FALLBACK__', _json.dumps(used, ensure_ascii=False))
                      .replace('__RU_TO_KEY__', _json.dumps(RU2KEY, ensure_ascii=False)))
    # T() нужна коду прототипа, а он выполняется раньше наших блоков —
    # поэтому объявление уходит в начало страницы, а не в конец
    if '</head>' in html:
        html = html.replace('</head>', prelude + '</head>', 1)
    else:
        html = html.replace('<body', prelude + '<body', 1)
    out = html.replace('</body>', blocks + '\n</body>', 1)
    import os
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    open(DST, 'w', encoding='utf-8').write(out)
    print('записано:', DST, len(out), 'байт')


if __name__ == '__main__':
    main()
