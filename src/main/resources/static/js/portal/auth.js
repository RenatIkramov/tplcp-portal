
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
      const e = new Error(PORTAL.текстОшибки(resp.status, data));
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
  const штатныйFetch = window.fetch.bind(window);
  let сеансПотерян = false;
  window.fetch = async function(url, opts){
    const resp = await штатныйFetch(url, opts);
    const адрес = String(typeof url === 'string' ? url : (url && url.url) || '');
    if (resp.status === 401 && адрес.indexOf('/api/') === 0
        && адрес.indexOf('/api/auth/') !== 0 && !сеансПотерян){
      сеансПотерян = true;
      показатьВход(T('auth.text.session_ended_please_sign_in_again'));
    }
    return resp;
  };
  /* --------------------------------------------------------------------------
     2. Экран входа
     -------------------------------------------------------------------------- */

  function поле(id){ return document.getElementById(id); }

  function ошибка(текст){
    const el = поле('portalLoginError');
    if (!el) return;
    el.textContent = текст || '';
    el.style.display = текст ? '' : 'none';
  }

  /** Включает штатную форму демо (в прототипе она была заблокирована). */
  function включитьФормуВхода(){
    const card = document.querySelector('#loginScreen .login-card');
    if (!card) return;

    ['loginEmail', 'loginPassword'].forEach(id => {
      const el = поле(id);
      if (!el) return;
      el.disabled = false;
      el.addEventListener('keydown', e => { if (e.key === 'Enter') window.doLogin(); });
    });
    const email = поле('loginEmail');
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
    const list = поле('demoUserList');
    if (list) list.remove();
    document.querySelectorAll('.demo-flag').forEach(el => el.remove());

    if (!поле('portalLoginError')){
      const err = document.createElement('div');
      err.id = 'portalLoginError';
      err.style.cssText = 'display:none;margin-top:10px;padding:8px 10px;border-radius:6px;'
        + 'background:#fdecea;color:#c0392b;font-size:13px;text-align:center;';
      card.appendChild(err);
    }
  }

  function показатьВход(текст){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = null;
    PORTAL.user = null;
    PORTAL.depositorCode = '';
    const shell = поле('appShell');
    if (shell) shell.style.display = 'none';
    поле('loginScreen')?.classList.add('active');
    const pwd = поле('loginPassword');
    if (pwd) pwd.value = '';
    ошибка(текст);
  }

  /* --------------------------------------------------------------------------
     3. Вход и выход
     -------------------------------------------------------------------------- */

  window.doLogin = async function(){
    const login = (поле('loginEmail')?.value || '').trim();
    const password = поле('loginPassword')?.value || '';
    if (!login || !password){ ошибка(T('auth.text.enter_your_login_and_password')); return; }
    ошибка('');
    try {
      const сеанс = await api('POST', '/api/auth/login', {login: login, password: password});
      await войтиВПортал(сеанс);
    } catch (e){
      ошибка(e.message || T('auth.text.sign_in_failed'));
    }
  };

  // Демо-вход выбором пользователя из списка больше не существует.
  window.quickLogin = function(){};
  window.renderDemoUsers = function(){};

  window.logout = async function(){
    try { await api('POST', '/api/auth/logout'); } catch (e) {}
    сеансПотерян = false;
    const email = поле('loginEmail');
    if (email) email.value = '';
    показатьВход('');
  };

  /** Собирает объект пользователя в том виде, в каком его ждёт интерфейс демо. */
  function пользовательДляИнтерфейса(сеанс, me){
    const скрытые = [];
    if (me.hideTransfersTab) скрытые.push('transfers');
    if (me.hideBillingTab) скрытые.push('billing');
    return {
      role: сеанс.roleCode,
      login: сеанс.login,
      full_name: сеанс.fullName,
      position: me.position || '—',
      organization: me.organization || '—',
      phone: me.phone || '',
      lastLogin: me.lastLogin || '',
      manager: подписьРежима(сеанс),
      hiddenTabs: скрытые,
      hideServicesTab: me.hideServicesTab === true,
      canCreateReceipts: me.canCreateReceipts !== false,
      canCreateOrders: me.canCreateOrders !== false,
      canCreateTransfers: me.canCreateTransfers !== false,
      canUploadSkuExcel: me.canUploadSkuExcel !== false,
      canUploadPackagingExcel: me.canUploadSkuExcel !== false,
      defaultDepositor: сеанс.depositorId
    };
  }

  function подписьРежима(сеанс){
    const имена = (сеанс.depositors || []).map(d => d.orgName).join(', ');
    switch (сеанс.roleCode){
      case 'manager':   return 'Режим менеджера: доступны поклажедатели — ' + (имена || 'нет');
      case 'admin':     return T('auth.text.administrator_mode_users_and_acces');
      case 'developer': return T('auth.text.developer_mode_document_and_field');
      case 'staff':     return T('auth.text.warehouse_staff_mode_only_granted');
      default:          return 'Организация: ' + (имена || '—');
    }
  }

  async function войтиВПортал(сеанс){
    if (PORTAL.forgetAll) PORTAL.forgetAll();
    PORTAL.session = сеанс;
    const me = await api('GET', '/api/me');
    PORTAL.user = me;

    const user = пользовательДляИнтерфейса(сеанс, me);
    currentUser = user;
    currentUserRole = user.role;

    сеансПотерян = false;
    поле('loginScreen')?.classList.remove('active');
    const shell = поле('appShell');
    if (shell) shell.style.display = '';
    ошибка('');
    const pwd = поле('loginPassword');
    if (pwd) pwd.value = '';

    if (typeof applyUserTabVisibility === 'function') applyUserTabVisibility(user);
    if (typeof applyUserCreatePermissions === 'function') applyUserCreatePermissions(user);

    const админ = user.role === 'admin' || user.role === 'developer';
    const показать = (id, видно) => { const el = поле(id); if (el) el.style.display = видно ? '' : 'none'; };
    показать('adminTabBtn', админ);
    показать('devSettingsTabBtn', user.role === 'developer');
    ['refSubBtnOrderTypes','refSubBtnDocTypes','refSubBtnDepositorsDir',
     'refSubBtnWarehousesDir','refSubBtnUsers'].forEach(id => показать(id, админ));

    const managerLine = поле('managerLine');
    if (managerLine) managerLine.textContent = user.manager;
    if (typeof renderProfile === 'function') renderProfile(user);

    применитьПоклажедателя();

    lastMainView = 'orders';
    if (typeof showTab === 'function') showTab('orders');

    // §6.2: пароль выдал администратор — сначала свой пароль, данных пока нет
    if (сеанс.mustChangePassword && typeof window.portalOpenPasswordChange === 'function'){
      window.portalOpenPasswordChange('forced');
      return;
    }

    // §6.4: если доступен не один поклажедатель и выбор не сделан — сначала выбор
    if (сеанс.needsDepositorChoice){
      поле('depositorSwitcher')?.classList.add('active');
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
  PORTAL.обновитьНастройки = async function(){
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

  function текущийПоклажедатель(){
    const s = PORTAL.session;
    if (!s || !s.depositorId) return null;
    return (s.depositors || []).find(d => d.depositorId === s.depositorId) || null;
  }

  /** Заголовок с названием организации и состояние переключателя. */
  function применитьПоклажедателя(){
    const s = PORTAL.session || {depositors: []};
    const текущий = текущийПоклажедатель();
    PORTAL.depositorCode = текущий ? текущий.code : '';
    PORTAL.depositorName = текущий ? текущий.orgName : '';

    const org = поле('tabsOrg');
    if (org){
      org.textContent = текущий ? текущий.orgName : T('auth.text.select_a_depositor');
      org.classList.toggle('switchable', (s.depositors || []).length > 1);
    }
    window.renderDepositorSwitcher();
  }

  window.renderDepositorSwitcher = function(){
    const el = поле('depositorSwitcher');
    if (!el) return;
    const s = PORTAL.session || {depositors: []};
    const выбран = s.depositorId;
    el.innerHTML = '<div class="ks-title">' + T('auth.text.switch_depositor') + '</div>'
      + (s.depositors || []).map(d =>
          '<div class="ks-item ' + (d.depositorId === выбран ? 'current' : '') + '"'
          + ' onclick="portalSelectDepositor(\'' + d.depositorId + '\')">'
          + '<span>' + d.orgName + '</span>'
          + (d.depositorId === выбран ? '<span>✓</span>' : '') + '</div>').join('');
  };

  window.toggleDepositorSwitcher = function(){
    const s = PORTAL.session;
    if (!s || (s.depositors || []).length <= 1) return;
    window.renderDepositorSwitcher();
    поле('depositorSwitcher')?.classList.toggle('active');
  };

  window.portalSelectDepositor = async function(id){
    try {
      const сеанс = await api('POST', '/api/auth/depositor', {depositorId: id});
      if (PORTAL.forgetAll) PORTAL.forgetAll();
      PORTAL.session = сеанс;
      поле('depositorSwitcher')?.classList.remove('active');
      применитьПоклажедателя();
      // видимость разделов зависит от клиента, поэтому спрашиваем заново
      await PORTAL.обновитьНастройки();
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
    включитьФормуВхода();
    // Обновление страницы не должно выбрасывать из портала: если сессия
    // ещё жива, сервер её узнает и экран входа даже не мелькнёт.
    try {
      const сеанс = await api('GET', '/api/auth/session');
      await войтиВПортал(сеанс);
    } catch (e){
      показатьВход('');
    }
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
