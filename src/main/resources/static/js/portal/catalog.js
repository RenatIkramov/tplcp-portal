
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // Справочник целиком — для выбора: окно строки документа и карточка
  // ищут товар по всему набору, а не по показанной странице.
  PORTAL.catalog = {};
  PORTAL.catalogError = {};
  // Страница списка — то, что сейчас на экране. Держится отдельно
  // от набора для выбора: одно имя на двоих означало бы, что открытая
  // вторая страница товаров сужает поиск в окне строки.
  PORTAL.page = {};
  PORTAL.pageTotal = {};

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

  /**
   * Страница товаров: строки те, что прислал сервер.
   *
   * <p>Отбор, порядок и страницы здесь больше не считаются. Считались:
   * экран держал весь справочник и резал его сам. У клиента с тысячей
   * позиций это значило тысячу строк в браузере на каждый вход — и,
   * что хуже, сортировку, которая упорядочивала только показанные
   * двадцать, оставаясь на вид настоящей.
   */
  function renderSkuFromApi(){
    const body = document.getElementById('skuBody');
    if (!body) return;
    const rows = PORTAL.page.sku || [];
    const total = PORTAL.pageTotal.sku || 0;
    const size = (typeof paginationState !== 'undefined' && paginationState.sku)
        ? paginationState.sku.pageSize : rows.length;
    const totalPages = Math.max(1, Math.ceil(total / (size || 1)));

    const counter = document.getElementById('skuCount');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead('sku');
    body.innerHTML = rows.length
      ? rows.map(p => renderTableRow('sku', p, 'portalOpenSku')).join('')
      : '<tr><td colspan="24" style="color:var(--muted);">'
        + (PORTAL.catalogError.sku ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';
    if (typeof renderPaginationBar === 'function') renderPaginationBar('sku', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('sku'), 0);
  }

  /**
   * Сходить за страницей товаров.
   *
   * <p>Ждём панель фильтров: без неё запрос ушёл бы без отбора, и
   * человек, вошедший с отмеченным архивом или заполненным полем,
   * увидел бы не свой список, а весь.
   */
  async function loadSku(){
    try {
      if (PORTAL.filtersReady) await PORTAL.filtersReady;
      const data = await PORTAL.fetchJson('/api/sku' + PORTAL.listQuery('sku'));
      PORTAL.page.sku = data.rows || [];
      PORTAL.pageTotal.sku = data.total || 0;
      // Номер страницы из ответа: сервер мог вернуть первую вместо
      // запрошенной пятой, если отбор сузился и пятой больше нет.
      if (typeof paginationState !== 'undefined' && paginationState.sku && data.page){
        paginationState.sku.page = data.page;
      }
      PORTAL.catalogError.sku = null;
    } catch (e){
      PORTAL.page.sku = [];
      PORTAL.pageTotal.sku = 0;
      PORTAL.catalogError.sku = e.message;
    }
    renderSkuFromApi();
  }

  /**
   * Справочник товаров целиком — для выбора, не для экрана.
   *
   * <p>Вместе с архивом: погашенную позицию надо уметь открыть, иначе
   * вернуть её в работу нечем — отмеченный на панели архив показывает
   * строку, а щёлкнуть по ней было бы некуда.
   */
  async function loadSkuOptions(){
    try {
      PORTAL.catalog.sku = await fetchCatalog('sku/options?withArchived=true');
    } catch (e){
      PORTAL.catalog.sku = PORTAL.catalog.sku || [];
      console.warn('Справочник товаров для выбора: ' + e.message);
    }
  }

  // Заглушка portalOpenSku удалена: карточку товара открывает sku.js,
  // а правку — sku-edit.js, и оба грузятся позже этого файла.

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
          // В колонке PDF стоит прочерк, и кнопки там нет нарочно. Признак
          // наличия файла сервер присылает, но самого файла портал пока
          // не хранит и отдать его не может. Кнопка, которая нажимается
          // и показывает плашку «появится позже», хуже её отсутствия:
          // человек щёлкает по ней заново, пока не сочтёт портал
          // неисправным. Вернётся с хранилищем файлов (MinIO/S3, этап 9).
          return '<tr><td><strong>' + dash(inv.invoiceNumber) + '</strong></td><td>' + period
            + '</td><td>' + ruDate(inv.issueDate) + '</td><td class="num">' + money(inv.amountNoVat)
            + '</td><td class="num">' + money(inv.amountWithVat) + '</td><td>' + badgeHtml
            + '</td><td>—</td></tr>';
        }).join('')
      : '<tr><td colspan="7" style="color:var(--muted);">'
        + (PORTAL.catalogError.invoices ? T('partners.text.data_unavailable')
            : T('common.text.no_invoices_for_this_depositor')) + '</td></tr>';
  }

  function install(){
    patchSkuColumns();
    window.renderSku = renderSkuFromApi;
    // Ряд страниц прототипа зовёт TABLE_RENDER_FN[ключ] — а новая
    // страница теперь приходит с сервера, перерисовкой её не добыть.
    if (typeof TABLE_RENDER_FN !== 'undefined') TABLE_RENDER_FN.sku = loadSku;
    window.renderBillingServices = renderTariffsFromApi;
    window.renderBillingInvoices = renderInvoicesFromApi;
    // Поставщиков и грузополучателей ведёт отдельный блок (этап 7б):
    // там они не только показываются, но и правятся, поэтому список
    // грузится вместе с архивом и рисуется там же.

    // Перечитать список товаров умеют теперь и другие блоки: после
    // загрузки из Excel созданные позиции должны оказаться в списке,
    // а не ждать следующего входа. Перечитываются оба набора: и страница
    // экрана, и справочник для выбора — созданный товар нужен и там.
    PORTAL.reloadProducts = () => Promise.all([loadSku(), loadSkuOptions()]);

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
