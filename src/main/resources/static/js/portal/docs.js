
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
      bar.innerHTML = '<span style="color:#c0392b;">' + T('common.message.could_not_get_the_data')
        + ': ' + err + '</span>'
        + ' · <a href="#" data-portal-reload="' + table + '">' + T('common.text.retry') + '</a>';
    } else {
      const t = PORTAL.docsLoadedAt[table];
      bar.innerHTML = '' + T('admin.column.depositor') + ' <b>' + PORTAL.depositorCode + '</b> · '
        + T('common.text.data_as_of') + ' '
        + (t ? t.toLocaleTimeString(PORTAL.timeLocale(),
              {hour:'2-digit', minute:'2-digit'}) : '—')
        + ' · <a href="#" data-portal-reload="' + table + '">' + T('stock.text.refresh') + '</a>';
    }
    const link = bar.querySelector('[data-portal-reload]');
    if (link) link.onclick = e => { e.preventDefault(); load(table); };
  }

  async function load(table){
    try {
      const data = await PORTAL.fetchJson('/api/' + table);
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
    if (statusQ && statusQ !== T('admin.option.all')){
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
        + (PORTAL.docsError[table] ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';

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
