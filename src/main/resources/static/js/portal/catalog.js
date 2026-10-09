
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  PORTAL.catalog = {};
  PORTAL.catalogError = {};

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const yesNo = v => v === true ? T('sku.text.yes') : (v === false ? T('sku.text.no') : '—');
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
      throw new Error(await PORTAL.responseMessage(resp));
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
    if (groupQ && groupQ !== T('admin.option.all')) rows = rows.filter(p => p.itemGroupName === groupQ);
    if (typeQ && typeQ !== T('admin.option.all'))   rows = rows.filter(p => p.itemTypeName === typeQ);

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
        + (PORTAL.catalogError.sku ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';
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
        + (PORTAL.catalogError['service-tariffs'] ? T('partners.text.data_unavailable')
            : T('common.text.no_rates_defined_for_this_deposito')) + '</td></tr>';
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
            ? '<button class="btn ghost" onclick="event.stopPropagation(); notify(\'Выгрузка PDF появится вместе с генерацией счетов\')">' + T('common.text.download_pdf') + '</button>'
            : '—';
          return '<tr><td><strong>' + dash(inv.invoiceNumber) + '</strong></td><td>' + period
            + '</td><td>' + ruDate(inv.issueDate) + '</td><td class="num">' + money(inv.amountNoVat)
            + '</td><td class="num">' + money(inv.amountWithVat) + '</td><td>' + badgeHtml
            + '</td><td>' + pdfBtn + '</td></tr>';
        }).join('')
      : '<tr><td colspan="7" style="color:var(--muted);">'
        + (PORTAL.catalogError.invoices ? T('partners.text.data_unavailable')
            : T('common.text.no_invoices_for_this_depositor')) + '</td></tr>';
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
    PORTAL.reloadProducts = () => load('sku', renderSkuFromApi);

    PORTAL.onLogin(() => {
      PORTAL.reloadProducts();
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
