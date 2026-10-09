
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
    patchOwnFieldLabels();
    watchNewNodes();
    // Демо отказывалось переключаться на казахский заглушкой — теперь
    // язык выбирается из того, что есть в базе, и заглушка не нужна.
    window.setLanguage = code => window.portalSetLanguage(code);

    buildSwitch().then(() => {
      const initial = (state.locales.find(l => l.isDefault) || state.locales[0] || {}).code;
      return apply(initial);
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
