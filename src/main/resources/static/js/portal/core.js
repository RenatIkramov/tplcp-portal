
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
    sessionLoaders: [],    // что зависит только от учётной записи, а не от клиента
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
  PORTAL.timeLocale = function(){
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
      bar.innerHTML = T('stock.text.loading_data');
      return;
    }
    if (PORTAL.error){
      bar.innerHTML = '<span style="color:#c0392b;">' + T('common.message.could_not_get_the_data')
        + ': ' + PORTAL.error
        + '</span> · <a href="#" id="portalStockRefresh">' + T('common.text.retry') + '</a>';
    } else {
      const t = PORTAL.loadedAt
        ? PORTAL.loadedAt.toLocaleTimeString(PORTAL.timeLocale(),
            {hour:'2-digit', minute:'2-digit'})
        : '—';
      bar.innerHTML = '' + T('admin.column.depositor') + ' <b>' + PORTAL.depositorCode + '</b> · '
        + T('common.text.data_as_of') + ' ' + t
        + ' · <a href="#" id="portalStockRefresh">' + T('stock.text.refresh') + '</a>';
    }
    const link = document.getElementById('portalStockRefresh');
    if (link) link.onclick = e => { e.preventDefault(); loadStock(); };
  }

  async function loadStock(){
    PORTAL.loading = true;
    PORTAL.error = null;
    renderStatusBar();
    try {
      PORTAL.rows = (await PORTAL.fetchJson('/api/stock')).map(toRow);
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
  const BY_CODE = {
    400: T('stock.text.request_rejected_check_the_fields'),
    401: T('stock.text.please_sign_in_again'),
    403: T('stock.text.this_action_is_not_available_to_yo'),
    404: T('error.status.not_found'),
    409: T('error.status.conflict'),
    422: T('stock.text.some_required_data_is_missing'),
    429: T('stock.text.too_many_attempts_try_again_later'),
    500: T('stock.text.portal_could_not_complete_the'),
    502: T('stock.text.no_connection_to_the_portal_server'),
    503: T('stock.text.portal_is_temporarily_unavaila')
  };
  PORTAL.errorText = (status, data) =>
    (data && (data.message || data.detail)) || BY_CODE[status]
    || ('запрос не выполнен (код ' + status + ')');

  /** Разбирает неудачный ответ fetch и возвращает готовое сообщение. */
  PORTAL.responseMessage = async resp => {
    let data = null;
    try { data = await resp.clone().json(); } catch (e) {}
    return PORTAL.errorText(resp.status, data);
  };

  /**
   * Запрос к порталу с разбором ответа.
   *
   * Отказ превращается в исключение с готовым сообщением — тем самым,
   * которое увидит человек. Вызывающему остаётся один `try` вокруг
   * всего обращения, без проверки `resp.ok` внутри него: бросать
   * исключение в том же блоке, который его ловит, — приём, на который
   * справедливо ругается анализатор, да и читается он плохо.
   */
  PORTAL.fetchJson = async (url, options) => {
    const resp = await fetch(url, Object.assign(
        {headers: {'Accept': 'application/json'}}, options || {}));
    if (!resp.ok) {
      throw new Error(await PORTAL.responseMessage(resp));
    }
    return resp.status === 204 ? null : resp.json();
  };

  /**
   * Видит ли вошедший разделы администрирования.
   *
   * <p>Право решает сервер (AdminUserService.requireAdmin — admin
   * и developer). Здесь это нужно для другого: не ходить за данными,
   * которых всё равно не дадут. Раньше экраны администрирования
   * грузились у всех, и сеанс обычного клиента начинался с девяти
   * отказов 403 — они ничего не ломали, но каждый был лишним походом
   * на сервер и строкой отказа в журнале.
   */
  PORTAL.isAdmin = () => ['admin', 'developer'].includes(
      (PORTAL.session && PORTAL.session.roleCode) || '');

  PORTAL.onLogin = fn => { PORTAL.loaders.push(fn); };
  PORTAL.reloadAll = () => { PORTAL.loaders.forEach(fn => { try { fn(); } catch (e) {} }); };

  /**
   * Что зависит только от того, КТО вошёл, а не от выбранного клиента.
   *
   * <p>Разделение появилось не на ровном месте. Язык — свойство человека,
   * но применялся он из `onLogin`, то есть вместе с загрузкой данных
   * клиента. Данным ждать выбора поклажедателя правильно (§6.4),
   * а языку — нет: администратору доступны все клиенты, выбор у него
   * не сделан, `reloadAll` не вызывался — и до выбора клиента портал
   * оставался на языке формы входа, а после выбора менял язык на глазах.
   * Той же болезнью болела кнопка «Тексты интерфейса».
   *
   * <p>Сюда подписывается то, что зависит от учётной записи: язык,
   * кнопки по роли. Данные клиента остаются на `onLogin`.
   */
  PORTAL.onSession = fn => { PORTAL.sessionLoaders.push(fn); };

  /**
   * Прогоняет подписчиков и ОЖИДАЕТ их.
   *
   * <p>Ожидание здесь не роскошь: язык применяется запросом к серверу,
   * а подписи сразу после входа собираются кодом. Не дождавшись языка,
   * вход показывал английские надписи в русском портале — и всплывающее
   * сообщение «выберите поклажедателя», и строку режима пользователя.
   * Переводить их потом некому: это не разметка с data-i18n, а текст,
   * вписанный один раз.
   */
  PORTAL.reloadSession = () => Promise.all(PORTAL.sessionLoaders.map(fn => {
    try {
      return Promise.resolve(fn()).catch(() => null);
    } catch (e) {
      return null;
    }
  })).catch(() => null);

  /**
   * Подпись, которая переживёт смену языка.
   *
   * <p>`T('ключ')` подставляет текст **один раз** — в тот момент, когда
   * строка разметки собирается. Переключение языка такую подпись уже
   * не найдёт: `applyTo` ищет узлы с атрибутом `data-i18n`, а у текста,
   * вписанного кодом, атрибута нет. Отсюда и брались английские надписи
   * посреди русского портала: экран рисовался до того, как применился
   * язык учётной записи, и исправить его потом было некому.
   *
   * <p>Поэтому подпись оборачивается в `<span data-i18n="…">`: при любой
   * последующей смене языка она перерисуется сама. Порядок загрузки
   * модулей перестаёт определять язык надписей.
   *
   * <p>Для кнопок и заголовков, которые собираются как элементы DOM,
   * тот же смысл даёт `PORTAL.label(el, key)`.
   */
  PORTAL.t = key => '<span data-i18n="' + key + '">'
      + (typeof T === 'function' ? T(key) : key) + '</span>';

  /** То же для готового элемента: подпись и атрибут разом. */
  PORTAL.label = (el, key) => {
    if (!el) return el;
    el.setAttribute('data-i18n', key);
    el.textContent = (typeof T === 'function' ? T(key) : key);
    return el;
  };

  /**
   * Подпись ПОЛЯ: имя клиента, если он его переименовал, иначе перевод.
   *
   * <p>Правило одно на весь портал: где у подписи есть идентификатор
   * колонки, там действует переименование поклажедателем. Раньше каждое
   * место решало само — списки брали подпись из раскладки, карточка
   * просмотра из разметки, форма правки из ключа текста, — и выходило,
   * что на просмотре поле зовётся «Артикул клиента», а в правке снова
   * «Коммерческий код».
   *
   * <p>Имя колонки принимается в любом виде: формы правки хранят имена
   * колонок базы (`weight_gross`), а справочник колонок — имена
   * раскладки (`weightGross`). Приводим здесь, чтобы вызывающий
   * об этом не думал.
   *
   * <p>Важно, ЧТО возвращается: переименование — готовый текст без
   * `data-i18n`, перевод — span с ключом. Повесить `data-i18n` на
   * переименованную подпись нельзя: смена языка нашла бы этот узел
   * и вернула бы портальное название.
   */
  const camel = name => String(name == null ? '' : name)
      .replace(/_([a-z0-9])/g, (m, c) => c.toUpperCase());

  PORTAL.fieldRename = (tableKey, column) => {
    const columns = PORTAL.columns;
    if (!columns || !columns.renameOf) return null;
    return columns.renameOf(tableKey, camel(column));
  };

  PORTAL.fieldLabel = (tableKey, column, textKey) => {
    const own = PORTAL.fieldRename(tableKey, column);
    if (own) {
      return String(own).replace(/&/g, '&amp;').replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
    return PORTAL.t(textKey);
  };

  /**
   * Кнопка «Назад» на экране-детали: куда она ведёт, решает тот,
   * кто этот экран открыл.
   *
   * <p>Экран `view-admin-user-edit` переиспользуется четырьмя разными
   * местами — карточкой пользователя, доступами менеджера, доступами
   * кладовщика и карточкой поклажедателя, — а кнопка «Назад» была одна
   * и прошита в разметке: «к пользователям», в Справочники. Человек
   * заходил из Администрирования и возвращался в другой раздел.
   *
   * <p>Поэтому возврат задаётся вместе с открытием: ключ текста и
   * действие. Угадывать, откуда пришли, кнопка не может — а хранить
   * историю переходов ради одной кнопки незачем.
   *
   * @param buttonId идентификатор кнопки в разметке экрана
   * @param textKey  ключ текста (значение уже содержит стрелку «←»)
   * @param run      что сделать при нажатии
   */
  PORTAL.setBack = (buttonId, textKey, run) => {
    const b = document.getElementById(buttonId);
    if (!b) return;
    b.setAttribute('data-i18n', textKey);
    b.textContent = (typeof T === 'function' ? T(textKey) : textKey);
    b.onclick = run;
  };

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
        + (PORTAL.error ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';

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
