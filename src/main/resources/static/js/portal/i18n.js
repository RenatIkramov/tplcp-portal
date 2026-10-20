
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  // fromAccount — язык взят из учётной записи вошедшего, и портальный
  // язык по умолчанию его больше не перебивает (см. install).
  const state = {locales: [], texts: {}, code: null, matrix: null, fromAccount: false};
  PORTAL.i18n = state;

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
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
    safely('ссылка «О нас»', applyAboutLink);
    // Панели фильтров документов собираются из справочника колонок,
    // и подписи в них приходят с сервера на языке человека. Разметки
    // с data-i18n там нет — перевести их нечем, кроме пересборки.
    // Набранный отбор при этом сохраняется (см. filters.js).
    safely('панели фильтров', () => {
      if (typeof PORTAL.rebuildFilters === 'function') PORTAL.rebuildFilters();
    });
    rerender();
    markSwitch(code);
  }

  /**
   * Подсказка в поле, всплывающая подпись и имя поля для читалки
   * с экрана — это атрибуты, а не текст узла, поэтому ключ для них
   * лежит в отдельном атрибуте: data-i18n-placeholder, data-i18n-title
   * и data-i18n-aria.
   *
   * aria-label нужен там, где видимой подписи нет и быть не может:
   * галочка «выбрать все» в шапке таблицы, поле поиска с подсказкой
   * внутри. Переводить его обязательно: иначе на английском портал
   * называет поля по-русски.
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
    Array.from(root.querySelectorAll('[data-i18n-aria]')).forEach(el => {
      const t = state.texts[el.getAttribute('data-i18n-aria')];
      if (t) el.setAttribute('aria-label', t);
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
    document.querySelectorAll('[data-i18n-aria]').forEach(el => {
      const t = state.texts[el.getAttribute('data-i18n-aria')];
      if (t) el.setAttribute('aria-label', t);
    });
  }

  /**
   * Подпись колонки: сначала переименование клиентом, потом перевод.
   *
   * Переименование спрашиваем у раскладки с сервера — только она
   * о нём знает. Если клиент поле не переименовывал, переводим
   * по ключу, как и раньше.
   *
   * Переименование действует на любом языке (решение заказчика):
   * клиент назвал поле по-своему, и язык интерфейса его слово
   * не отменяет. Прежний код прятал переименование на всех языках
   * кроме русского — и это была одна из двух причин, по которым
   * его не видел никто. Вторая: эта функция присваивалась здесь
   * целиком, затирая обёртку columns.js, которая о переименовании
   * знала.
   */
  const ownLabel = (tableKey, colId) => {
    if (!tableKey || !colId) return null;
    const columns = window.PORTAL && PORTAL.columns;
    return columns && columns.renameOf ? columns.renameOf(tableKey, colId) : null;
  };

  function patchColumnLabels(){
    window.localizeColumnLabel = function(ruLabel, tableKey, colId){
      const own = ownLabel(tableKey, colId);
      if (own) return own;

      let key = (tableKey && colId) ? 'col.' + tableKey + '.' + colId : null;
      if (!key && typeof colPickerTable === 'string' && colPickerTable){
        // окно выбора колонок зовёт эту функцию без таблицы — берём открытую
        const c = (tableColumns[colPickerTable] || []).find(x => x.label === ruLabel);
        if (c){
          const mine = ownLabel(colPickerTable, c.id);
          if (mine) return mine;
          if (c.key) key = c.key;
        }
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
      const columns = window.PORTAL && PORTAL.columns;
      const table = columns && columns.tableOfColumn ? columns.tableOfColumn(c) : null;
      const own = ownLabel(table, c.id);
      if (own) return own;
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
     'renderReportsConfigTable', 'renderReportRows',
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
    document.querySelectorAll('.lang').forEach(box => {
      const chip = box.querySelector('.lang-current');
      if (!chip) return;
      chip.textContent = (code || '').toUpperCase();
      chip.setAttribute('data-locale', code || '');
      box.querySelectorAll('.lang-menu [data-locale]').forEach(b => {
        b.classList.toggle('active', b.getAttribute('data-locale') === code);
      });
    });
  }

  /**
   * Выбор языка: чип с кодом текущего языка, по щелчку — список.
   *
   * <p>Кнопка на каждый язык занимала место и росла с каждым новым
   * языком. Обычный `<select>` в шапку не встал: системный вид списка
   * спорит с оформлением портала. Поэтому вид остался прежним — тот же
   * чип, что был у активного языка, — а выбор переехал в список,
   * который открывается по щелчку.
   *
   * <p>В списке код и название: код человек узнаёт по шапке, название
   * нужно тому, кто код не опознал.
   */
  function fillSwitch(box){
    const label = (typeof T === 'function' ? T('common.aria.choose_language')
                                           : 'Язык интерфейса');
    box.innerHTML = '<span class="active lang-current" role="button" tabindex="0"'
      + ' aria-haspopup="listbox" aria-expanded="false"'
      + ' data-i18n-aria="common.aria.choose_language" aria-label="' + esc(label) + '">'
      + esc((state.code || defaultCode() || '').toUpperCase()) + '</span>'
      + '<div class="lang-menu" role="listbox" hidden>'
      + state.locales.map(l => '<button type="button" role="option"'
          + ' data-locale="' + esc(l.code) + '">'
          + '<b>' + esc(l.code.toUpperCase()) + '</b> ' + esc(l.name) + '</button>').join('')
      + '</div>';

    const chip = box.querySelector('.lang-current');
    const menu = box.querySelector('.lang-menu');
    const open = on => {
      menu.hidden = !on;
      chip.setAttribute('aria-expanded', on ? 'true' : 'false');
    };
    chip.onclick = e => { e.stopPropagation(); open(menu.hidden); };
    chip.onkeydown = e => {
      if (e.key === 'Enter' || e.key === ' '){ e.preventDefault(); open(menu.hidden); }
      if (e.key === 'Escape') open(false);
    };
    menu.querySelectorAll('[data-locale]').forEach(b => {
      b.onclick = e => {
        e.stopPropagation();
        open(false);
        window.portalSetLanguage(b.getAttribute('data-locale'));
      };
    });
    // Щелчок мимо списка закрывает его: иначе список висит до выбора,
    // а человек уже занят другим.
    if (!document.body.dataset.langMenuWired){
      document.addEventListener('click', () => {
        document.querySelectorAll('.lang-menu').forEach(m => { m.hidden = true; });
        document.querySelectorAll('.lang-current').forEach(c =>
            c.setAttribute('aria-expanded', 'false'));
      });
      document.body.dataset.langMenuWired = '1';
    }
    markSwitch(state.code || defaultCode());
  }

  /** Код языка портала по умолчанию из того, что отдал сервер. */
  function defaultCode(){
    const d = state.locales.find(l => l.isDefault) || state.locales[0];
    return d ? d.code : '';
  }

  /**
   * Кнопка «О нас» ведёт на сайт оператора, в новой вкладке.
   *
   * <p>Своей страницы «О нас» у портала нет: её содержимое — это сайт
   * оператора, и держать его копию в портале значило бы поддерживать две
   * версии одного текста. Адрес лежит в таблице переводов под ключом
   * `about.url`, то есть у каждого языка свой и правится из
   * «Текстов интерфейса» без пересборки.
   *
   * <p>Схема адреса проверяется: значение попадает в `href`, и `javascript:`
   * оттуда превратил бы кнопку в исполняемый код. Пусто или не http(s) —
   * кнопки нет совсем, потому что кнопка, ведущая в никуда, хуже её
   * отсутствия.
   */
  function applyAboutLink(){
    const link = document.querySelector('a[data-i18n="common.text.about_us"]');
    if (!link) return;
    const url = String(state.texts['about.url'] || '').trim();
    const ok = /^https?:\/\//i.test(url);
    link.style.display = ok ? '' : 'none';
    if (!ok) return;
    link.setAttribute('href', url);
    link.setAttribute('target', '_blank');
    // rel обязателен: без noopener открытая страница получает доступ
    // к window.opener нашего портала
    link.setAttribute('rel', 'noopener noreferrer');
    link.onclick = null;
    link.removeAttribute('onclick');
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
      say(T('common.message.language_switch_failed', {error: e.message}));
    }
  };

  /**
   * Выход из портала: язык возвращается к портальному.
   *
   * <p>Иначе форма входа осталась бы на языке ушедшего человека — на общем
   * компьютере следующий увидел бы чужую настройку и решил, что портал
   * так устроен.
   */
  /**
   * Пересобрать переключатель языка.
   *
   * <p>Нужен справочнику языков: включили или выключили язык — список
   * выбора обязан это показать сразу. Иначе выключенный язык остаётся
   * в списке до перезагрузки страницы, и его можно выбрать.
   */
  state.rebuildSwitch = async () => {
    try {
      state.locales = await api('GET', '/api/i18n/locales');
    } catch (e){
      return;
    }
    document.querySelectorAll('.lang').forEach(fillSwitch);
    // Если язык, на котором человек работает, только что выключили,
    // портал переходит на язык по умолчанию — иначе он остался бы
    // на языке, которого в портале больше нет.
    const codes = state.locales.map(l => l.code);
    if (state.code && !codes.includes(state.code)){
      await apply(defaultCode());
    } else {
      markSwitch(state.code);
    }
  };

  PORTAL.resetLocale = () => {
    state.fromAccount = false;
    const initial = (state.locales.find(l => l.isDefault) || state.locales[0] || {}).code;
    if (initial && initial !== state.code){
      apply(initial).catch(() => {});
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
    btn.textContent = T('common.text.interface_texts');
    btn.onclick = () => window.portalOpenUiTexts();
    tabs.appendChild(btn);

    const section = document.createElement('div');
    section.className = 'admin-sub';
    section.id = 'admin-sub-uitexts';
    section.innerHTML =
        '<div class="panel-head" style="margin-top:0;">'
      + '<div class="section-title" style="margin:0;"'
      + ' data-i18n="admin.button.interface_texts">' + T('common.text.interface_texts') + '</div>'
      + '<span class="count" id="uiTextsCount"></span>'
      + '</div>'
      + '<div class="filters">'
      + '<div class="fgroup"><label for="uiTextsSearch">' + T('common.text.search_by_text_or_key') + '</label>'
      + '<input id="uiTextsSearch" oninput="portalUiTextsRender()" placeholder="напр. Создать"/></div>'
      + '<div class="fgroup"><label for="uiTextsScope">Раздел</label>'
      + '<select id="uiTextsScope" onchange="portalUiTextsRender()">'
      + '<option value="">Все разделы</option></select></div>'
      + '<div class="fgroup"><label for="uiTextsElement">Вид</label>'
      + '<select id="uiTextsElement" onchange="portalUiTextsRender()">'
      + '<option value="">Все виды</option></select></div>'
      + '<div class="fgroup"><label for="uiTextsFilter">' + T('common.text.show') + '</label>'
      + '<select id="uiTextsFilter" onchange="portalUiTextsRender()">'
      + '<option value="">' + T('common.text.all_keys') + '</option>'
      + '<option value="missing">' + T('common.text.missing_translations_only') + '</option></select></div>'
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
      // список всех языков — для колонок таблицы переводов
      state.allLocales = await api('GET', '/api/admin/locales');
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
    // ВСЕ языки, а не только включённые. Иначе получался замкнутый круг:
    // выключенному языку нельзя завести переводы, потому что его колонки
    // нет на экране, а включить его без переводов нельзя — человек
    // увидел бы портал на языке отката.
    const codes = (state.allLocales || state.locales).map(l => l.code);
    const head = document.getElementById('uiTextsHead');
    if (head){
      head.innerHTML = '<th style="min-width:90px;">' + T('common.text.key') + '</th><th>Раздел</th><th>Вид</th>'
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
      : '<tr><td colspan="' + (codes.length + 4) + '" style="color:var(--muted);">' + T('stock.text.nothing_found') + '</td></tr>';
  };

  /** Сохранение по уходу из поля: отдельной кнопки на три сотни строк не напасёшься. */
  window.portalUiTextSave = async function(el){
    const key = el.getAttribute('data-key');
    const locale = el.getAttribute('data-locale');
    try {
      state.matrix = await api('PUT', '/api/admin/ui-texts',
          {textKey: key, locale: locale, value: el.value});
      if (locale === state.code) await apply(state.code);
      el.style.borderColor = 'var(--ok-fg)';
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
    watchNewNodes();
    // Демо отказывалось переключаться на казахский заглушкой — теперь
    // язык выбирается из того, что есть в базе, и заглушка не нужна.
    window.setLanguage = code => window.portalSetLanguage(code);

    buildSwitch().then(() => {
      // Если человек успел войти, пока грузился список языков, язык его
      // учётной записи уже применён — портальный по умолчанию его
      // перебивать не должен. Иначе язык зависел бы от того, что пришло
      // раньше, то есть от скорости сети.
      if (state.fromAccount) return null;
      const initial = (state.locales.find(l => l.isDefault) || state.locales[0] || {}).code;
      return apply(initial);
    }).catch(() => {});

    // onSession, а не onLogin: язык и кнопка зависят от учётной записи,
    // а не от выбранного поклажедателя. На onLogin администратор, которому
    // доступны все клиенты, не получал ни того, ни другого до выбора.
    PORTAL.onSession(() => {
      if (['admin', 'developer'].includes(PORTAL.session && PORTAL.session.roleCode)){
        addAdminButton();
      }
      // язык из учётной записи: он мог отличаться от языка формы входа.
      // Обещание ВОЗВРАЩАЕМ — вход его ждёт, прежде чем собирать подписи.
      if (PORTAL.session && PORTAL.session.locale){
        state.fromAccount = true;
        if (PORTAL.session.locale !== state.code){
          return apply(PORTAL.session.locale).catch(() => {});
        }
      }
      return null;
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
