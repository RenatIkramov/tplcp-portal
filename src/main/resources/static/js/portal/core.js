
(function(){
  'use strict';

  // Поклажедатель и пользователь берутся из сеанса (этап 5): сервер знает,
  // кто вошёл, по cookie сессии. В адресе и заголовках ничего не передаётся —
  // подставить чужой код поклажедателя из браузера невозможно.
  // Объект собирается по частям — здесь и в модулях, — поэтому тип
  // объявлен целиком (src/main/types/portal.d.ts), а начальная часть
  // приводится к нему.
  const PORTAL = /** @type {Portal} */ (/** @type {unknown} */ ({
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
  }));

  /**
   * Коды статусов документов (V87) — один список для поступлений,
   * заказов и перемещений. Имя статуса приходит с сервера уже
   * переведённым; здесь только коды, от которых зависит поведение
   * экрана, и цвет плашки.
   */
  PORTAL.STATUS = Object.freeze({DRAFT: '01', SENT_TO_WMS: '02', SENDING_FAILED: '03'});

  /**
   * Логическое значение на экран (V91): «Да» / «Нет» на языке пользователя,
   * пусто — «—». Одно место на весь портал: прежде пар ключей было две,
   * а прототип писал «Да» и «Нет» прямо в коде.
   */
  PORTAL.yesNo = v => v === true ? T('common.value.yes')
      : (v === false ? T('common.value.no') : '—');

  /** Передача транспорта в WMS (V90): код enum_transport_state. */
  PORTAL.TRANSPORT = Object.freeze({SENT: '01', NOT_SENT: '02'});

  /**
   * Цвет плашки статуса документа (согласовано 29.10.2026):
   * серый — черновик и отменён, жёлтый — в процессе, зелёный — отгружен
   * и принят, красный — ошибка отправки и расхождение.
   *
   * <p>Отменённый — «muted», а не «draft», хотя цвет тот же: прототип
   * по классу «draft» решает, можно ли документ править.
   * Незнакомый код (заведённый автоматически по WMS, Р5) — жёлтым:
   * документ в работе у склада, а что именно с ним — скажет имя.
   */
  const STATUS_BADGE = {
    '01': 'draft', '90': 'muted',
    '02': 'warn', '10': 'warn', '20': 'warn', '21': 'warn', '30': 'warn', '40': 'warn', '51': 'warn',
    '50': 'ok', '60': 'ok',
    '03': 'err', '61': 'err'
  };
  PORTAL.statusBadge = code => STATUS_BADGE[code] || 'warn';

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

  /**
   * Отметка времени на экран: «01.12.2026 14:35» в поясе того, кто смотрит.
   *
   * <p>Нужна с V62: «дата создания» и «дата отгрузки» документа стали
   * отметками времени, а не сутками. Их нельзя показывать, отрезав первые
   * десять знаков от ISO: в строке мгновение по UTC, и у всего, что
   * случилось в Алматы до пяти утра, так отрезались бы прошлые сутки.
   */
  PORTAL.dateTimeText = function(iso){
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? String(iso)
      : d.toLocaleString(PORTAL.timeLocale(), {day:'2-digit', month:'2-digit', year:'numeric',
                                               hour:'2-digit', minute:'2-digit'});
  };

  /**
   * Календарный день отметки времени — «2026-12-01», по местному поясу.
   *
   * <p>Для фильтров «с ... по ...»: человек выбирает дни, и сравнивать
   * надо день с днём. Сравнение дня с мгновением обманывает молча —
   * «2026-12-01T14:35:00Z» больше, чем «2026-12-01», и документ
   * выпадает из последнего дня диапазона.
   */
  PORTAL.localDay = function(iso){
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso).slice(0, 10);
    const pad = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
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
      if (PORTAL.filtersReady) await PORTAL.filtersReady;
      const data = await PORTAL.fetchJson('/api/stock' + PORTAL.listQuery('stock'));
      PORTAL.rows = (data.rows || []).map(toRow);
      PORTAL.rowsTotal = data.total || 0;
      // Номер страницы из ответа: сервер мог вернуть первую вместо
      // запрошенной пятой, если отбор сузился и пятой больше нет.
      PORTAL.tables.setPage('stock', data.page);
      PORTAL.loadedAt = new Date();
    } catch (e){
      PORTAL.rows = [];
      PORTAL.rowsTotal = 0;
      PORTAL.error = e.message;
    } finally {
      PORTAL.loading = false;
      renderStatusBar();
      renderStock();
    }
  }
  window.portalReloadStock = loadStock;
  // Этим же вызовом за списком ходит панель фильтров: отбор изменился —
  // надо идти к серверу, перерисовкой имеющихся строк тут не обойтись.
  PORTAL.reloadStock = loadStock;

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
    || T('common.message.request_failed_with_code', {code: status});

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
   * <p>Право решает сервер (AdminUserService.requireAdmin). Здесь это нужно для другого: не ходить за данными,
   * которых всё равно не дадут. Раньше экраны администрирования
   * грузились у всех, и сеанс обычного клиента начинался с девяти
   * отказов 403 — они ничего не ломали, но каждый был лишним походом
   * на сервер и строкой отказа в журнале.
   */
  PORTAL.isAdmin = () => !!PORTAL.session && PORTAL.session.roleCode === 'admin';

  /**
   * Предложить браузеру запомнить пароль.
   *
   * <p>ЗАЧЕМ ОТДЕЛЬНО. Менеджер паролей узнаёт вход по тому, что
   * страница после отправки формы сменилась. Портал страницу не меняет:
   * вход уходит запросом, а форма остаётся на месте — и предложения
   * сохранить пароль человек не видит никогда. То же и при смене
   * пароля: менеджер хранит старый и будет подставлять его и дальше.
   *
   * <p>Поэтому зовём менеджер явно. Поддержка есть не везде
   * (PasswordCredential — это Chrome и прочие на том же движке),
   * и работает она только в защищённом окружении: по https или
   * с localhost. Где не поддержано — молчим: предложение сохранить
   * пароль это удобство, и его отсутствие не повод ронять вход.
   *
   * @param login    логин, под которым вошли
   * @param password пароль, который сработал
   * @param name     как показать учётную запись в менеджере
   */
  PORTAL.offerToSavePassword = async (login, password, name) => {
    try {
      if (!window.PasswordCredential || !navigator.credentials || !login || !password) return;
      await navigator.credentials.store(new window.PasswordCredential({
        id: login,
        password: password,
        name: name || login
      }));
    } catch (e){
      // Отказ менеджера, небезопасное окружение, запрет настройками —
      // всё это не наше дело и не повод мешать человеку работать.
      console.warn('Сохранение пароля в менеджере: ' + e.message);
    }
  };

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
    PORTAL.rowsTotal = 0;
    PORTAL.loadedAt = null;
    PORTAL.error = null;
    // PORTAL.page и PORTAL.pageTotal — страницы товаров и упаковок.
    // Забывать их обязательно: это строки ПРЕДЫДУЩЕГО клиента, и до
    // ответа сервера они оставались бы на экране нового.
    [PORTAL.docs, PORTAL.docsError, PORTAL.docsLoadedAt, PORTAL.catalog, PORTAL.catalogError,
     PORTAL.page, PORTAL.pageTotal]
      .forEach(store => {
        if (!store) return;
        Object.keys(store).forEach(k => { store[k] = Array.isArray(store[k]) ? [] : null; });
      });
    if (PORTAL.tables) PORTAL.tables.redrawAll();
    ['renderBillingServices','renderBillingInvoices','renderSuppliers','renderConsignees']
      .forEach(fn => { if (typeof window[fn] === 'function'){ try { window[fn](); } catch (e) {} } });
  };

  /* ================= Навигация, плашка, даты =================
     Перенесено из prototype.js (11.11.2026). Имена остались прежними
     и глобальными: их зовут модули, onclick разметки и проверки
     (HandlerNamesTest сверяет, что всё названное в onclick есть). */

  /** Какой раздел открыт последним — к нему ведут кнопки «Назад». */
  PORTAL.lastView = 'orders';

  /** Показать экран (section.view) по имени, без смены вкладки. */
  window.showView = function(name){
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById('view-' + name)?.classList.add('active');
    window.scrollTo({top: 0, behavior: 'instant'});
  };

  /*
   * Крючки переключения. Модулю, которому надо вмешаться в переход,
   * — подписка, а не подмена функции: подмены ставились друг на друга
   * цепочкой, и порядок подключения файлов молча решал, кто чью
   * обёртку обернёт.
   */
  /** @type {Array<(tab: string) => boolean | void>} */
  const beforeTabHooks = [];
  /** @type {Array<(sub: string) => void>} */
  const adminSubHooks = [];

  /** Перед переходом в раздел; вернула false — переход отменён (draft.js: несохранённый документ). */
  PORTAL.beforeTab = fn => { beforeTabHooks.push(fn); };
  /** После того как человек открыл подвкладку администрирования (admin-tabs.js, import-log.js). */
  PORTAL.onAdminSub = fn => { adminSubHooks.push(fn); };

  /** Открыть раздел верхнего меню. */
  window.showTab = function(tab){
    if (beforeTabHooks.some(fn => fn(tab) === false)) return;
    if (tab === 'admin' && !PORTAL.isAdmin()) tab = 'orders';
    /** @type {NodeListOf<HTMLButtonElement>} */ (document.querySelectorAll('nav.tabs button'))
        .forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    PORTAL.lastView = tab;
    window.showView(tab);
    if (tab === 'admin'){
      ['populateReportsConfigDepositorFilter', 'renderReportsConfigTable']
        .forEach(fn => { if (typeof window[fn] === 'function') window[fn](); });
    }
    if (tab === 'references') window.showReferencesSub(referencesSub);
    setTimeout(() => PORTAL.tables.updateScroll(tab), 0);
  };

  /*
   * В «Справочниках» — справочники ПОКЛАЖЕДАТЕЛЯ: товары, упаковки,
   * поставщики, грузополучатели. Справочники всего портала живут
   * в «Администрирование → Общие справочники».
   */
  let referencesSub = 'sku';
  window.showReferencesSub = function(sub){
    referencesSub = sub;
    /** @type {NodeListOf<HTMLElement>} */ (document.querySelectorAll('[data-refsub]'))
        .forEach(b => b.classList.toggle('active', b.dataset.refsub === sub));
    ['sku', 'packaging', 'suppliers', 'consignees'].forEach(s => {
      document.getElementById('ref-sub-' + s)?.classList.toggle('active', s === sub);
    });
    if (sub === 'sku' || sub === 'packaging') PORTAL.tables.redraw(sub);
    if (sub === 'suppliers' && typeof window.renderSuppliers === 'function') window.renderSuppliers();
    if (sub === 'consignees' && typeof window.renderConsignees === 'function') window.renderConsignees();
  };

  /**
   * Показать панель подвкладки администрирования — и только. Так её
   * открывает admin-tabs.js сам, когда человек ещё ничего не выбирал.
   */
  PORTAL.showAdminPane = function(sub){
    /** @type {NodeListOf<HTMLElement>} */ (document.querySelectorAll('.admin-subtab-btn[data-sub]'))
        .forEach(b => b.classList.toggle('active', b.dataset.sub === sub));
    document.querySelectorAll('.admin-sub[id^="admin-sub-"]')
        .forEach(s => s.classList.toggle('active', s.id === 'admin-sub-' + sub));
    if (sub === 'reports-config' && typeof window.renderReportsConfigTable === 'function'){
      window.renderReportsConfigTable();
    }
  };

  /** Человек открыл подвкладку администрирования. */
  window.showAdminSub = function(sub){
    PORTAL.showAdminPane(sub);
    adminSubHooks.forEach(fn => fn(sub));
  };

  window.openAbout = () => window.showView('about');
  // «Контакты» — contacts.js, «Личный кабинет» — profile.js: открывают
  // экран и читают данные

  /** Плашка вверху экрана — ответ портала на действие. */
  window.notify = function(msg){
    const toast = document.createElement('div');
    toast.textContent = msg;
    toast.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);'
      + 'background:#1b2430;color:#fff;padding:10px 18px;border-radius:6px;font-size:13px;'
      + 'z-index:100;box-shadow:0 4px 14px rgba(0,0,0,.2);opacity:0;transition:opacity .2s;';
    document.body.appendChild(toast);
    requestAnimationFrame(() => { toast.style.opacity = '1'; });
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 250); }, 2200);
  };

  /**
   * Цветная плашка статуса. Подпись приходит готовой — из перечисления
   * на языке человека или из каталога текстов.
   */
  window.badge = function(status, label){
    return '<span class="badge ' + status + '"><span class="dot"></span>' + label + '</span>';
  };

  /*
   * Даты: русская запись ↔ ISO. Карточка документа держит дату
   * по-русски, а поле <input type="date"> понимает только ISO.
   */
  window.toISODate = function(str){
    if (!str || str === '—') return '';
    const parts = String(str).split('.');
    if (parts.length !== 3) return '';
    const [d, m, y] = parts;
    return y + '-' + m.padStart(2, '0') + '-' + d.padStart(2, '0');
  };
  window.fromISODate = function(iso){
    if (!iso) return '—';
    const [y, m, d] = String(iso).split('-');
    return d + '.' + m + '.' + y;
  };

  function wireTabs(){
    /** @type {NodeListOf<HTMLButtonElement>} */ (document.querySelectorAll('nav.tabs button'))
        .forEach(btn => btn.addEventListener('click', () => window.showTab(btn.dataset.tab || '')));
  }
  // Сразу, а не по DOMContentLoaded: вёрстка меню уже разобрана —
  // core.js подключён после неё, — и щелчок не должен ждать.
  wireTabs();

  /** «Запасы» — страница, которую отдал сервер (отбор, порядок, страница — там, V79). */
  function renderStock(){
    PORTAL.tables.renderList('stock', {rows: PORTAL.rows || [], total: PORTAL.rowsTotal || 0,
                                       error: PORTAL.error, open: 'portalOpenStockRow'});
    renderStatusBar();
  }

  function install(){
    PORTAL.tables.register('stock', {load: loadStock, render: renderStock});
    PORTAL.onLogin(loadStock);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
