
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
    const address = String(typeof url === 'string' ? url : (url && url.url) || '');
    if (resp.status === 401 && address.indexOf('/api/') === 0
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
      el.addEventListener('keydown', e => { if (e.key === 'Enter') window.doLogin(); });
    });
    const email = field('loginEmail');
    if (email) email.placeholder = 'client@roga-kopyta.kz';

    const btn = card.querySelector('button.btn.primary');
    if (btn){
      btn.disabled = false;
      btn.style.opacity = '';
      btn.style.cursor = 'pointer';
      btn.removeAttribute('title');
      btn.onclick = () => window.doLogin();
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
    if (!login || !password){ error(T('auth.text.enter_your_login_and_password')); return; }
    error('');
    try {
      const sess = await api('POST', '/api/auth/login', {login: login, password: password});
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
      canUploadPackagingExcel: me.canUploadSkuExcel !== false,
      defaultDepositor: sess.depositorId
    };
  }

  function modeLabel(sess){
    const names = (sess.depositors || []).map(d => d.orgName).join(', ');
    switch (sess.roleCode){
      case 'manager':   return 'Режим менеджера: доступны поклажедатели — ' + (names || 'нет');
      case 'admin':     return T('auth.text.administrator_mode_users_and_acces');
      case 'developer': return T('auth.text.developer_mode_document_and_field');
      case 'staff':     return T('auth.text.warehouse_staff_mode_only_granted');
      default:          return 'Организация: ' + (names || '—');
    }
  }

  async function signIn(sess){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = sess;
    const me = await api('GET', '/api/me');
    PORTAL.user = me;

    const user = uiUser(sess, me);
    currentUser = user;
    currentUserRole = user.role;

    sessionLost = false;
    field('loginScreen')?.classList.remove('active');
    const shell = field('appShell');
    if (shell) shell.style.display = '';
    error('');
    const pwd = field('loginPassword');
    if (pwd) pwd.value = '';

    if (typeof applyUserTabVisibility === 'function') applyUserTabVisibility(user);
    if (typeof applyUserCreatePermissions === 'function') applyUserCreatePermissions(user);

    const admin = user.role === 'admin' || user.role === 'developer';
    const show = (id, visible) => { const el = field(id); if (el) el.style.display = visible ? '' : 'none'; };
    show('adminTabBtn', admin);
    show('devSettingsTabBtn', user.role === 'developer');
    ['refSubBtnOrderTypes','refSubBtnDocTypes','refSubBtnDepositorsDir',
     'refSubBtnWarehousesDir','refSubBtnUsers'].forEach(id => show(id, admin));

    const managerLine = field('managerLine');
    if (managerLine) managerLine.textContent = user.manager;
    if (typeof renderProfile === 'function') renderProfile(user);

    applyDepositor();

    lastMainView = 'orders';
    if (typeof showTab === 'function') showTab('orders');

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
    if (typeof currentUser === 'undefined' || !currentUser) return;
    currentUser.hiddenTabs = [];
    if (me.hideTransfersTab) currentUser.hiddenTabs.push('transfers');
    if (me.hideBillingTab) currentUser.hiddenTabs.push('billing');
    currentUser.hideServicesTab = me.hideServicesTab === true;
    currentUser.canCreateReceipts = me.canCreateReceipts !== false;
    currentUser.canCreateOrders = me.canCreateOrders !== false;
    currentUser.canCreateTransfers = me.canCreateTransfers !== false;
    currentUser.canUploadSkuExcel = me.canUploadSkuExcel !== false;
    currentUser.canUploadPackagingExcel = me.canUploadPackagingExcel !== false;
    if (typeof applyUserTabVisibility === 'function') applyUserTabVisibility(currentUser);
    if (typeof applyUserCreatePermissions === 'function') {
      applyUserCreatePermissions(currentUser);
    }
    // открытый раздел мог только что стать скрытым — уводим на заказы
    if (typeof showTab === 'function' && typeof lastMainView === 'string'
        && currentUser.hiddenTabs.indexOf(lastMainView) >= 0){
      showTab('orders');
    }
  };

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
    const s = PORTAL.session || {depositors: []};
    const selected = s.depositorId;
    el.innerHTML = '<div class="ks-title">' + T('auth.text.switch_depositor') + '</div>'
      + (s.depositors || []).map(d =>
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
      if (typeof notify === 'function') notify('Выбран поклажедатель: ' + PORTAL.depositorName);
    } catch (e){
      if (typeof notify === 'function') notify('Не удалось переключить: ' + e.message);
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
    try {
      const sess = await api('GET', '/api/auth/session');
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
