
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
      /** @type {PortalHttpError} */
      const e = new Error(PORTAL.errorText(resp.status, data));
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
  const builtinFetch = window.fetch.bind(window);
  let sessionLost = false;
  window.fetch = async function(url, opts){
    const resp = await builtinFetch(url, opts);
    // Адрес бывает строкой, URL или Request; у URL нет поля url —
    // раньше для него получалась пустая строка (нашла проверка типов).
    const address = typeof url === 'string' ? url
      : url instanceof Request ? url.url : String(url || '');
    // Только если сессия была: до входа 401 — обычный ответ, а не
    // «сессия закончилась». Прежде экран входа ловил 401 на запросах,
    // ушедших до входа (панели фильтров при смене языка), и стирал
    // пароль, который человек уже набирал, — проверки так и падали.
    if (resp.status === 401 && PORTAL.session && address.indexOf('/api/') === 0
        && address.indexOf('/api/auth/') !== 0 && !sessionLost){
      sessionLost = true;
      showLogin(T('auth.text.session_ended_please_sign_in_again'));
    }
    return resp;
  };
  /* --------------------------------------------------------------------------
     2. Экран входа
     -------------------------------------------------------------------------- */

  function field(id){ return document.getElementById(id); }

  // Стартовая страница — вход. Сразу при разборе скрипта, а не после
  // ответа сервера: иначе портал мелькает пустым, пока идёт проверка
  // сессии (install ниже). Жива сессия — signIn сам откроет портал.
  (function bootScreen(){
    const shell = field('appShell');
    if (shell) shell.style.display = 'none';
    field('loginScreen')?.classList.add('active');
    /** @type {(HTMLInputElement | null)[]} */ ([
      /** @type {HTMLInputElement | null} */ (field('loginEmail')),
      /** @type {HTMLInputElement | null} */ (field('loginPassword'))
    ]).forEach(input => { if (input) input.value = ''; });
  })();

  // Список поклажедателей закрывается щелчком мимо него.
  document.addEventListener('click', e => {
    const wrap = field('tabsOrg')?.closest('.tabs-org-wrap');
    if (wrap && !wrap.contains(/** @type {Node} */ (e.target))) field('depositorSwitcher')?.classList.remove('active');
  });

  function error(msg){
    const el = field('portalLoginError');
    if (!el) return;
    el.textContent = msg || '';
    el.style.display = msg ? '' : 'none';
  }

  /** Включает штатную форму демо (в прототипе она была заблокирована). */
  function enableLoginForm(){
    const card = document.querySelector('#loginScreen .login-card');
    if (!card) return;

    ['loginEmail', 'loginPassword'].forEach(id => {
      const el = field(id);
      if (!el) return;
      el.disabled = false;
    });
    const email = field('loginEmail');
    if (email) email.placeholder = 'client@roga-kopyta.kz';

    const btn = /** @type {HTMLButtonElement | null} */ (card.querySelector('button.btn.primary'));
    if (btn){
      btn.disabled = false;
      btn.style.opacity = '';
      btn.style.cursor = 'pointer';
      btn.removeAttribute('title');
    }

    // Вход идёт через ОТПРАВКУ ФОРМЫ, а не по щелчку по кнопке.
    // Это не стилистика: менеджер паролей Chrome предлагает сохранить
    // пароль, увидев отправку формы с полями логина и пароля. Обработчик
    // на кнопке такого события не создаёт, и сохранять Хрому нечего.
    // Enter в поле тоже отправляет форму сам — отдельный обработчик
    // клавиши больше не нужен.
    const form = field('loginForm');
    if (form && !form.dataset.wired){
      form.addEventListener('submit', e => { e.preventDefault(); window.doLogin(); });
      form.dataset.wired = '1';
    }

    // убираем всё, что относилось к демо-доступу
    card.querySelectorAll('.hint').forEach(el => el.remove());
    const divider = card.querySelector('.login-divider');
    if (divider) divider.remove();
    const list = field('demoUserList');
    if (list) list.remove();
    document.querySelectorAll('.demo-flag').forEach(el => el.remove());

    if (!field('portalLoginError')){
      const err = document.createElement('div');
      err.id = 'portalLoginError';
      err.style.cssText = 'display:none;margin-top:10px;padding:8px 10px;border-radius:6px;'
        + 'background:#fdecea;color:#c0392b;font-size:13px;text-align:center;';
      card.appendChild(err);
    }
  }

  function showLogin(msg){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = null;
    PORTAL.user = null;
    PORTAL.depositorCode = '';
    const shell = field('appShell');
    if (shell) shell.style.display = 'none';
    field('loginScreen')?.classList.add('active');
    const pwd = field('loginPassword');
    if (pwd) pwd.value = '';
    error(msg);
  }

  /* --------------------------------------------------------------------------
     3. Вход и выход
     -------------------------------------------------------------------------- */

  window.doLogin = async function(){
    const login = (field('loginEmail')?.value || '').trim();
    const password = field('loginPassword')?.value || '';
    const remember = field('loginRemember')?.checked === true;
    if (!login || !password){ error(T('auth.text.enter_your_login_and_password')); return; }
    error('');
    try {
      const sess = await api('POST', '/api/auth/login',
          {login: login, password: password, remember: remember});
      // Пока пароль под рукой и мы знаем, что он сработал: страницу
      // портал не меняет, и сам менеджер паролей вход не заметит.
      // Зовём до signIn — тот перерисовывает экран целиком.
      if (PORTAL.offerToSavePassword){
        await PORTAL.offerToSavePassword(login, password, sess.fullName || login);
      }
      await signIn(sess);
    } catch (e){
      error(e.message || T('auth.text.sign_in_failed'));
    }
  };

  // Демо-вход выбором пользователя из списка больше не существует.
  window.quickLogin = function(){};
  window.renderDemoUsers = function(){};

  window.logout = async function(){
    try { await api('POST', '/api/auth/logout'); } catch (e) {}
    sessionLost = false;
    const email = field('loginEmail');
    if (email) email.value = '';
    // язык уходит вместе с человеком: форма входа — на языке портала
    if (PORTAL.resetLocale) PORTAL.resetLocale();
    showLogin('');
  };

  /** Собирает объект пользователя в том виде, в каком его ждёт интерфейс демо. */
  function uiUser(sess, me){
    const hidden = [];
    if (me.hideTransfersTab) hidden.push('transfers');
    if (me.hideBillingTab) hidden.push('billing');
    return {
      role: sess.roleCode,
      login: sess.login,
      full_name: sess.fullName,
      position: me.position || '—',
      organization: me.organization || '—',
      phone: me.phone || '',
      lastLogin: me.lastLogin || '',
      manager: modeLabel(sess),
      hiddenTabs: hidden,
      hideServicesTab: me.hideServicesTab === true,
      canCreateReceipts: me.canCreateReceipts !== false,
      canCreateOrders: me.canCreateOrders !== false,
      canCreateTransfers: me.canCreateTransfers !== false,
      canUploadSkuExcel: me.canUploadSkuExcel !== false,
      // Своё право, а не право на товары: прежде здесь стояло
      // me.canUploadSkuExcel, и снятая галочка «загрузка упаковок»
      // не прятала кнопку — отказ человек получал уже после выбора файла.
      canUploadPackagingExcel: me.canUploadPackagingExcel !== false,
      defaultDepositor: sess.depositorId
    };
  }

  function modeLabel(sess){
    const names = (sess.depositors || []).map(d => d.orgName).join(', ');
    switch (sess.roleCode){
      case 'manager':   return T('auth.text.manager_mode_depositors',
                                 {names: names || T('common.text.none')});
      case 'admin':     return T('auth.text.administrator_mode_users_and_acces');
      case 'staff':     return T('auth.text.warehouse_staff_mode_only_granted');
      default:          return T('auth.text.organization') + ': ' + (names || '—');
    }
  }

  async function signIn(sess){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = sess;
    const me = await api('GET', '/api/me');
    PORTAL.user = me;

    // Язык учётной записи применяем ДО того, как собираются подписи:
    // строка режима пользователя и всплывающие сообщения берут текст
    // один раз, и позже их уже никто не переведёт.
    await PORTAL.reloadSession();

    const user = uiUser(sess, me);
    PORTAL.uiUser = user;

    sessionLost = false;
    field('loginScreen')?.classList.remove('active');
    const shell = field('appShell');
    if (shell) shell.style.display = '';
    error('');
    const pwd = field('loginPassword');
    if (pwd) pwd.value = '';

    applyTabs(user);
    applyCreateRights(user);

    const admin = user.role === 'admin';
    const show = (id, visible) => { const el = field(id); if (el) el.style.display = visible ? '' : 'none'; };
    show('adminTabBtn', admin);
    // Кнопок справочников портала здесь больше нет: типы, поклажедатели,
    // склады и пользователи переехали в «Администрирование → Общие
    // справочники», а весь раздел целиком и так показан по роли
    // (кнопка adminTabBtn выше).

    const managerLine = field('managerLine');
    if (managerLine) managerLine.textContent = user.manager;
    if (typeof renderProfile === 'function') renderProfile(user);

    applyDepositor();

    PORTAL.lastView = 'orders';
    window.showTab('orders');

    // §6.2: пароль выдал администратор — сначала свой пароль, данных пока нет
    if (sess.mustChangePassword && typeof window.portalOpenPasswordChange === 'function'){
      window.portalOpenPasswordChange('forced');
      return;
    }

    // §6.4: если доступен не один поклажедатель и выбор не сделан — сначала выбор
    if (sess.needsDepositorChoice){
      field('depositorSwitcher')?.classList.add('active');
      if (typeof notify === 'function') notify(T('auth.message.select_the_depositor_you_are_worki'));
    } else {
      PORTAL.reloadAll();
      if (typeof notify === 'function') notify(T('auth.message.signed_in_as2') + user.full_name);
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
  PORTAL.refreshSettings = async function(){
    let me;
    try {
      me = await api('GET', '/api/me');
    } catch (e){
      return;                       // не смогли спросить — оставляем как есть
    }
    PORTAL.user = me;
    const user = PORTAL.uiUser;
    if (!user) return;
    user.hiddenTabs = [];
    if (me.hideTransfersTab) user.hiddenTabs.push('transfers');
    if (me.hideBillingTab) user.hiddenTabs.push('billing');
    user.hideServicesTab = me.hideServicesTab === true;
    user.canCreateReceipts = me.canCreateReceipts !== false;
    user.canCreateOrders = me.canCreateOrders !== false;
    user.canCreateTransfers = me.canCreateTransfers !== false;
    user.canUploadSkuExcel = me.canUploadSkuExcel !== false;
    user.canUploadPackagingExcel = me.canUploadPackagingExcel !== false;
    // открытый раздел мог только что стать скрытым — applyTabs уведёт на заказы
    applyTabs(user);
    applyCreateRights(user);
  };

  /** Разделы меню, закрытые человеку (флаги видимости, этап 5). */
  function applyTabs(user){
    const hidden = new Set((user && user.hiddenTabs) || []);
    /** @type {NodeListOf<HTMLButtonElement>} */ (document.querySelectorAll('nav.tabs button[data-tab]'))
        .forEach(btn => { btn.style.display = hidden.has(btn.dataset.tab || '') ? 'none' : ''; });
    if (hidden.has(PORTAL.lastView)) window.showTab('orders');
  }

  /**
   * Кнопки «Создать» и загрузки Excel — по правам человека. Кнопки
   * загрузки документов знает doc-import.js: он отдаёт свою часть
   * через PORTAL.applyImportRights.
   */
  function applyCreateRights(user){
    const may = (/** @type {keyof PortalUiUser} */ right) => !user || user[right] !== false;
    /** @type {[string, keyof PortalUiUser][]} */
    const buttons = [['createReceiptBtn', 'canCreateReceipts'], ['createTransferBtn', 'canCreateTransfers'],
                     ['createOrderBtn', 'canCreateOrders'], ['uploadSkuExcelBtn', 'canUploadSkuExcel'],
                     ['uploadPackagingExcelBtn', 'canUploadPackagingExcel']];
    buttons.forEach(([id, right]) => {
      const b = field(id);
      if (b) b.style.display = may(right) ? '' : 'none';
    });
    if (typeof PORTAL.applyImportRights === 'function') PORTAL.applyImportRights(user);
  }

  /* --------------------------------------------------------------------------
     4. Переключатель поклажедателей
     -------------------------------------------------------------------------- */

  function currentDepositor(){
    const s = PORTAL.session;
    if (!s || !s.depositorId) return null;
    return (s.depositors || []).find(d => d.depositorId === s.depositorId) || null;
  }

  /** Заголовок с названием организации и состояние переключателя. */
  function applyDepositor(){
    const s = PORTAL.session || {depositors: []};
    const current = currentDepositor();
    PORTAL.depositorCode = current ? current.code : '';
    PORTAL.depositorName = current ? current.orgName : '';

    const org = field('tabsOrg');
    if (org){
      org.textContent = current ? current.orgName : T('auth.text.select_a_depositor');
      org.classList.toggle('switchable', (s.depositors || []).length > 1);
    }
    window.renderDepositorSwitcher();
  }

  window.renderDepositorSwitcher = function(){
    const el = field('depositorSwitcher');
    if (!el) return;
    const s = PORTAL.session;
    const selected = s ? s.depositorId : null;
    el.innerHTML = '<div class="ks-title">' + T('auth.text.switch_depositor') + '</div>'
      + ((s && s.depositors) || []).map(d =>
          '<div class="ks-item ' + (d.depositorId === selected ? 'current' : '') + '"'
          + ' onclick="portalSelectDepositor(\'' + d.depositorId + '\')">'
          + '<span>' + d.orgName + '</span>'
          + (d.depositorId === selected ? '<span>✓</span>' : '') + '</div>').join('');
  };

  window.toggleDepositorSwitcher = function(){
    const s = PORTAL.session;
    if (!s || (s.depositors || []).length <= 1) return;
    window.renderDepositorSwitcher();
    field('depositorSwitcher')?.classList.toggle('active');
  };

  window.portalSelectDepositor = async function(id){
    try {
      const sess = await api('POST', '/api/auth/depositor', {depositorId: id});
      if (PORTAL.forgetAll) PORTAL.forgetAll();
      PORTAL.session = sess;
      field('depositorSwitcher')?.classList.remove('active');
      applyDepositor();
      // видимость разделов зависит от клиента, поэтому спрашиваем заново
      await PORTAL.refreshSettings();
      PORTAL.reloadAll();
      if (typeof notify === 'function') notify(T('auth.message.depositor_selected', {name: PORTAL.depositorName}));
    } catch (e){
      if (typeof notify === 'function') notify(T('auth.message.depositor_switch_failed', {error: e.message}));
    }
  };
  // в прототипе у кладовщика был свой переключатель — теперь он общий
  window.selectDepositor = window.portalSelectDepositor;
  window.setStaffDepositorFilter = window.portalSelectDepositor;

  /* --------------------------------------------------------------------------
     5. Старт страницы
     -------------------------------------------------------------------------- */

  async function install(){
    enableLoginForm();
    // Обновление страницы не должно выбрасывать из портала: если сессия
    // ещё жива, сервер её узнает и экран входа даже не мелькнёт.
    let sess;
    try {
      sess = await api('GET', '/api/auth/session');
    } catch (e){
      // Сессии нет — экран входа уже открыт (bootScreen). Звать здесь
      // showLogin нельзя: он стирает пароль, а человек мог начать его
      // вводить, пока портал спрашивал сервер (проверки так и падали:
      // пароль вписан, ответ «сессии нет» его стёр), — а если успел
      // войти, этот поздний ответ закрыл бы ему портал.
      return;
    }
    try {
      await signIn(sess);
    } catch (e){
      showLogin('');
    }
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
