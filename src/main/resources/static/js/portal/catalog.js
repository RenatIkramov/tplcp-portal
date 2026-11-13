
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

  // общие помощники — core.js (аудит A4)

  const {dash} = window.PORTAL;
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

  /**
   * Страница товаров: строки те, что прислал сервер (отбор, порядок
   * и страницы считает он). Колонки — table-columns.js.
   */
  function renderSku(){
    PORTAL.tables.renderList('sku', {rows: PORTAL.page.sku || [], total: PORTAL.pageTotal.sku || 0,
                                     error: PORTAL.catalogError.sku, open: 'portalOpenSku'});
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
      PORTAL.tables.setPage('sku', data.page);
      PORTAL.catalogError.sku = null;
    } catch (e){
      PORTAL.page.sku = [];
      PORTAL.pageTotal.sku = 0;
      PORTAL.catalogError.sku = e.message;
    }
    renderSku();
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

  // Статус счёта (V87): 01 ожидает оплаты, 02 оплачен, 03 просрочен
  const INVOICE_BADGE = {'01': 'warn', '02': 'ok', '03': 'err'};

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
    PORTAL.tables.register('sku', {load: loadSku, render: renderSku});
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
      // «Биллинг» скрыт — счета и тарифы сервер не отдаст (аудит S3)
      if (!PORTAL.sectionHidden('billing')){
        load('service-tariffs', renderTariffsFromApi);
        load('invoices', renderInvoicesFromApi);
      }
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
