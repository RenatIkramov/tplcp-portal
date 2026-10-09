
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
      ? badge(b.q === T('common.text.good') || b.q === T('common.text.good2') ? 'ok' : 'err', b.q)
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
        + (PORTAL.docsError.transfers ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';
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
