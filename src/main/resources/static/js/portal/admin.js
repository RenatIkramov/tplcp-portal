
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

  const byId = id => document.getElementById(id);
  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  /* ======================================================================
     1. Окно смены пароля
     ====================================================================== */

  // Два режима: обычная смена из личного кабинета и обязательная — когда
  // пароль выдал администратор. В обязательном режиме окно не закрывается:
  // до смены пароля сервер всё равно не отдаёт данных.
  let required = false;

  function createModal(){
    if (byId('pwOverlay')) return;
    const modal = document.createElement('div');
    modal.className = 'col-overlay';
    modal.id = 'pwOverlay';
    modal.style.cssText = 'align-items:center;justify-content:center;';
    modal.onclick = e => { if (e.target === modal && !required) close(); };
    // Разметка без подписей: их ставит retext(). Так подписи живут
    // в одном месте и переставляются при смене языка — иначе окно
    // осталось бы на том языке, на котором его собрали.
    modal.innerHTML = ''
      + '<div class="col-panel" id="pwPanel" style="width:460px;box-shadow:0 8px 32px rgba(0,0,0,.24);'
      + 'border-radius:10px;">'
      + '  <div style="display:flex;align-items:flex-start;gap:12px;">'
      + '    <h3 id="pwTitle" style="font-size:17px;flex:1;"></h3>'
      + '    <div class="lang" id="pwLang"></div>'
      + '  </div>'
      + '  <div class="hint" id="pwHint"></div>'
      // Поля — внутри формы, и первым идёт скрытый логин: без него
      // менеджер паролей не знает, чью запись обновлять, и предлагает
      // завести новую. Форма сама никуда не отправляется — её submit
      // перехвачен, — но разметка нужна менеджеру именно такая.
      + '  <form id="pwForm" autocomplete="on">'
      + '  <input type="text" id="pwLogin" name="username" autocomplete="username"'
      + ' tabindex="-1" aria-hidden="true" style="position:absolute;opacity:0;'
      + 'width:1px;height:1px;pointer-events:none;">'
      + '  <div class="kv" style="margin-top:12px;"><label id="pwCurrentLabel"></label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwCurrent" autocomplete="current-password"></div>'
      + '  <div class="kv" style="margin-top:12px;"><label id="pwNewLabel"></label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwNew" autocomplete="new-password"></div>'
      + '  <div class="kv" style="margin-top:12px;"><label id="pwRepeatLabel"></label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwRepeat" autocomplete="new-password"></div>'
      + '  </form>'
      + '  <div id="pwRules" style="margin-top:12px;padding:10px 12px;background:var(--bg);'
      + 'border-radius:6px;font-size:12.5px;line-height:1.9;">'
      + '    <div id="pwRulesTitle" style="color:var(--muted);margin-bottom:2px;"></div>'
      + '    <div data-pw-rule="length"><span class="pw-mark">○</span> <span data-pw-text></span></div>'
      + '    <div data-pw-rule="case"><span class="pw-mark">○</span> <span data-pw-text></span></div>'
      + '    <div data-pw-rule="extra"><span class="pw-mark">○</span> <span data-pw-text></span></div>'
      + '  </div>'
      + '  <div id="pwError" style="display:none;margin-top:12px;padding:8px 10px;border-radius:6px;'
      + 'background:#fdecea;color:#c0392b;font-size:13px;"></div>'
      + '  <div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
      + '    <button class="btn primary" id="pwSave"></button>'
      + '    <button class="btn ghost" id="pwCancel"></button>'
      + '  </div>'
      + '</div>';
    document.body.appendChild(modal);
    retext();
    // Свой переключатель языка: окно — накладка во весь экран,
    // и переключатель шапки остаётся под ней. Человеку с выданным
    // паролем деваться с этого окна некуда, а язык ему может быть
    // нужен раньше, чем пароль.
    if (typeof PORTAL.mountLanguageSwitch === 'function'){
      PORTAL.mountLanguageSwitch(byId('pwLang'));
    }
    byId('pwForm').addEventListener('submit', e => { e.preventDefault(); save(); });
    byId('pwSave').onclick = save;
    byId('pwCancel').onclick = close;
    ['pwCurrent','pwNew','pwRepeat'].forEach(id => {
      byId(id).addEventListener('keydown', e => { if (e.key === 'Enter') save(); });
    });
    byId('pwNew').addEventListener('input', markRules);
    markRules();
  }

  /**
   * Подписи окна — все разом, из каталога.
   *
   * <p>Зовётся при сборке, при открытии и при смене языка. Поля ввода
   * при этом не трогаются: набранный пароль терять нельзя, а смена
   * языка на полпути — обычное дело именно здесь.
   *
   * <p>Заголовок и подсказка зависят от режима: при обязательной смене
   * человек пришёл не по своей воле, и сказать ему надо другое.
   */
  function retext(){
    if (!byId('pwOverlay')) return;
    const set = (id, text) => { const el = byId(id); if (el) el.textContent = text; };
    set('pwTitle', required ? T('admin.text.set_your_password') : T('admin.text.change_password'));
    set('pwHint', required
        ? T('admin.text.password_issued_by_admin') + ' '
          + T('admin.text.enter_the_password_you_were_given')
        : T('admin.text.choose_a_password_that_meets_the_r'));
    set('pwCurrentLabel', T('admin.text.current_password'));
    set('pwNewLabel', T('admin.text.new_password'));
    set('pwRepeatLabel', T('admin.text.repeat_new_password'));
    set('pwRulesTitle', T('admin.text.password_requirements'));
    const rules = {
      length: T('admin.text.at_least_8_characters'),
      case: T('admin.text.latin_letters_both_cases'),
      extra: T('admin.text.digit_or_sign')
    };
    Object.keys(rules).forEach(rule => {
      const cell = document.querySelector('#pwRules [data-pw-rule="' + rule + '"] [data-pw-text]');
      if (cell) cell.textContent = rules[rule];
    });
    set('pwSave', T('admin.text.change_password2'));
    set('pwCancel', T('common.button.cancel'));
    const cancel = byId('pwCancel');
    if (cancel) cancel.style.display = required ? 'none' : '';
    markRules();
  }

  // Смена языка перебирает подписи окна, если оно уже собрано.
  PORTAL.refreshPasswordDialog = retext;

  /**
   * Те же правила, что и на сервере, — здесь только чтобы человек видел,
   * чего не хватает, ещё до нажатия кнопки. Решение всё равно за сервером.
   */
  function done(pwd){
    const digitOrSign = /[0-9]/.test(pwd) || /[^0-9A-Za-zА-Яа-яЁё]/.test(pwd);
    return {
      length: pwd.length >= 8,
      case: /[a-z]/.test(pwd) && /[A-Z]/.test(pwd),
      extra: digitOrSign
    };
  }

  function markRules(){
    const field = byId('pwNew');
    const pwd = field ? field.value : '';
    const ready = done(pwd);
    const list = byId('pwRules');
    if (!list) return;
    list.querySelectorAll('[data-pw-rule]').forEach(row => {
      const ok = ready[row.getAttribute('data-pw-rule')] === true;
      row.style.color = pwd && ok ? '#1e7a3c' : 'inherit';
      const mark = row.querySelector('.pw-mark');
      if (mark) mark.textContent = pwd && ok ? '✓' : '○';
    });
  }

  function error(msg){
    const el = byId('pwError');
    if (!el) return;
    el.textContent = msg || '';
    el.style.display = msg ? '' : 'none';
  }

  function close(){
    if (required) return;
    byId('pwOverlay')?.classList.remove('active');
    ['pwCurrent','pwNew','pwRepeat'].forEach(id => { const el = byId(id); if (el) el.value = ''; });
    error('');
  }

  /** Открыть окно. principal === 'forced' — обязательная смена после входа. */
  window.portalOpenPasswordChange = function(mode){
    required = mode === 'forced';
    createModal();
    retext();
    const who = byId('pwLogin');
    if (who) who.value = (PORTAL.session && PORTAL.session.login) || '';
    error('');
    byId('pwOverlay').classList.add('active');
    setTimeout(() => byId('pwCurrent').focus(), 50);
  };

  async function save(){
    const button = byId('pwSave');
    if (button.disabled) return;          // второй клик, пока идёт первый запрос
    const current = byId('pwCurrent').value;
    const isNew = byId('pwNew').value;
    const repeat = byId('pwRepeat').value;
    if (!current || !isNew){ error(T('common.message.fill_in_all_fields')); return; }
    if (isNew !== repeat){ error(T('admin.text.new_password_and_its_repeat_do')); return; }
    // Проверка пароля на сервере по своей природе небыстрая — хеш считается
    // медленно намеренно. Показываем, что запрос идёт, и не даём кликать дважды.
    button.disabled = true;
    button.textContent = T('admin.text.checking');
    error('');
    try {
      await api('POST', '/api/me/password', {currentPassword: current, newPassword: isNew});
      // Менеджер паролей хранит прежний и будет подставлять его и
      // дальше: страницу портал не меняет, и смены он не заметит.
      if (PORTAL.offerToSavePassword && PORTAL.session){
        await PORTAL.offerToSavePassword(PORTAL.session.login, isNew,
            PORTAL.session.fullName || PORTAL.session.login);
      }
      const wasRequired = required;
      required = false;
      close();
      if (typeof notify === 'function') notify(T('info.password.changed'));
      if (wasRequired){
        // До смены пароля портал не отдаёт ничего (§6.2): все запросы
        // отбиваются 403. Поэтому перечитываем и то, что зависит от
        // клиента (reloadAll), и то, что зависит только от учётной
        // записи (reloadSession) — экраны администрирования грузятся
        // именно там, и без этой строки они остались бы пустыми.
        if (PORTAL.session) PORTAL.session.mustChangePassword = false;
        PORTAL.reloadSession();
        PORTAL.reloadAll();
      }
    } catch (e){
      // единственная причина отказа 403 на этом адресе — не тот текущий пароль
      error(e.status === 403 ? T('admin.text.current_password_is_incorrect') : e.message);
    } finally {
      button.disabled = false;
      // Подпись берётся заново из каталога, а не из запомненной
      // переменной: язык могли сменить, пока шёл запрос.
      button.textContent = T('admin.text.change_password2');
    }
  }

  /** Кнопка «Сменить пароль» в личном кабинете — та же, что была в прототипе. */
  function wireProfileButton(){
    const builtin = window.renderProfile;
    if (typeof builtin !== 'function') return;
    window.renderProfile = function(user){
      builtin(user);
      const host = byId('profileContent');
      if (!host) return;
      [...host.querySelectorAll('button')]
        .filter(b => b.textContent.trim().indexOf(T('admin.text.change_password2')) === 0)
        .forEach(b => { b.onclick = () => window.portalOpenPasswordChange(); });
    };
  }

  /* ======================================================================
     2. Справочник «Пользователи» из базы портала
     ====================================================================== */

  const admin = {users: [], roles: [], depositors: [], error: null};

  async function loadUsers(){
    // Справочник пользователей видит только администратор.
    if (!PORTAL.isAdmin()){
      admin.users = [];
      admin.error = null;
      drawList();
      return;
    }
    try {
      const [list, roles, depositors] = await Promise.all([
        api('GET', '/api/admin/users'),
        api('GET', '/api/admin/roles'),
        api('GET', '/api/admin/depositors')
      ]);
      admin.users = list;
      admin.roles = roles;
      admin.depositors = depositors;
      admin.error = null;
    } catch (e){
      admin.users = [];
      // 403 — обычное дело: раздел видит только администратор
      admin.error = e.status === 403 ? null : e.message;
    }
    drawList();
  }

  function drawList(){
    const body = byId('adminUsersBody');
    if (!body) return;
    if (admin.error){
      body.innerHTML = '<tr><td colspan="6" style="color:#c0392b;">'
        + T('common.message.could_not_get_the_data') + ': ' + esc(admin.error) + '</td></tr>';
      return;
    }
    if (!admin.users.length){
      body.innerHTML = '<tr><td colspan="6" style="color:var(--muted);">' + T('admin.text.no_data') + '</td></tr>';
      return;
    }
    body.innerHTML = admin.users.map(u => {
      const stateBadge = u.isActive
        ? '<span class="badge ok"><span class="dot"></span>' + T('admin.text.active') + '</span>'
        : '<span class="badge err"><span class="dot"></span>' + T('admin.text.blocked') + '</span>';
      const pwd = u.mustChangePassword
        ? ' <span class="badge warn" title="'
          + esc(T('admin.hint.must_change_password_badge')) + '">'
          + '<span class="dot"></span>' + T('admin.text.must_change_password') + '</span>'
        : '';
      return '<tr onclick="portalOpenAdminUser(\'' + u.userId + '\')">'
        + '<td><strong>' + esc(u.fullName) + '</strong></td>'
        + '<td>' + esc(u.roleName || u.roleCode) + '</td>'
        + '<td>' + esc(u.scope) + '</td>'
        + '<td>' + esc(u.login) + '</td>'
        + '<td>' + esc(u.depositorCodes || '—') + '</td>'
        + '<td>' + stateBadge + pwd + '</td>'
        + '</tr>';
    }).join('');
  }

  const checkbox = (id, label, enabled) =>
    '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;">'
    + '<input type="checkbox" id="' + id + '"' + (enabled ? ' checked' : '') + '> ' + label + '</label>';

  const dataRow = (label, valueOf_) =>
    '<div class="kv"><label>' + label + '</label><div>' + esc(valueOf_ || '—') + '</div></div>';

  const input = (id, label, valueOf_, hint) =>
    '<div class="kv"><label>' + label + '</label><div>'
    + '<input type="text" class="kv-input kv-input-editable" id="' + id + '"'
    + ' value="' + esc(valueOf_ || '') + '"'
    + (hint ? ' placeholder="' + esc(hint) + '"' : '') + '></div></div>';

  const roleField = (isSelected) =>
    '<div class="kv"><label>' + T('admin.text.portal_role') + '</label><div>'
    + '<select class="kv-input kv-input-editable" id="auRole">'
    + admin.roles.map(r => '<option value="' + r.code + '"'
        + (r.code === isSelected ? ' selected' : '') + '>' + esc(r.name) + '</option>').join('')
    + '</select></div></div>';

  /**
   * Галочки доступных поклажедателей. Для складского сотрудника их не
   * показываем: ему доступ выдаётся по складам, это отдельный экран.
   */
  function accessBlock(granted){
    const chosen = new Set(granted || []);
    return '<div class="section-title">' + T('admin.text.accessible_depositors') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;" id="auDepositors">'
      + '  <div class="hint" style="margin:-4px 0 10px;">' + T('admin.hint.depositors_of_a_user') + '</div>'
      + (admin.depositors.length
          ? admin.depositors.map(d =>
              '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
              + 'padding:8px 0;border-bottom:1px solid var(--line);">'
              + '<input type="checkbox" data-depositor-id="' + d.depositorId + '"'
              + (chosen.has(d.depositorId) ? ' checked' : '') + '> '
              + esc(d.orgName) + ' <span style="color:var(--muted);">· '
              + esc(d.code) + '</span></label>').join('')
          : '<div style="color:var(--muted);font-size:13.5px;">' + T('admin.text.no_depositors_have_been_created_ye') + '</div>')
      + '</div>';
  }

  function checkedDepositors(){
    return [...document.querySelectorAll('#auDepositors input[data-depositor-id]:checked')]
      .map(cb => cb.getAttribute('data-depositor-id'));
  }

  /**
   * Карточка пользователя. Одна и та же форма работает на правку и на
   * создание: при создании `userId` не передан, доступен ввод логина
   * и пароля, а блоки «последний вход» и «пароль» выглядят иначе.
   */
  window.portalOpenAdminUser = async function(userId){
    const u = userId ? admin.users.find(x => x.userId === userId) : null;
    if (userId && !u) return;
    const isNew = !u;
    const el = byId('adminUserEditContent');
    if (!el) return;
    const isSelf = !isNew && PORTAL.session && PORTAL.session.login === u.login;
    const readOnly = currentUserRole === 'developer';

    // Какие поклажедатели уже выданы — спрашиваем отдельно, в списке их нет
    let granted = [];
    if (!isNew){
      try { granted = await api('GET', '/api/admin/users/' + encodeURIComponent(u.userId) + '/depositors'); }
      catch (e){ granted = []; }
    }

    el.innerHTML = ''
      + '<div class="detail-head">'
      + '  <div><h2>' + (isNew ? T('admin.text.new_user') : esc(u.fullName)) + '</h2>'
      + '    <div class="meta">' + (isNew
            ? T('admin.text.fill_in_the_details_and_issue_a_pa')
            : esc(u.userPosition || '—') + ' · ' + esc(u.organization || '—')) + '</div></div>'
      + (isNew ? '' : ''
      + '  <div><label style="display:flex;align-items:center;gap:8px;font-size:13.5px;cursor:pointer;">'
      + '    <input type="checkbox" id="userIsActive"' + (u.isActive ? ' checked' : '')
      +      (isSelf ? ' disabled title="' + esc(T('admin.hint.cannot_block_yourself')) + '"' : '') + '>'
      + '    <span class="badge ' + (u.isActive ? 'ok' : 'err') + '" id="userIsActiveBadge">'
      + '<span class="dot"></span>' + (u.isActive ? T('admin.text.active') : T('admin.text.blocked')) + '</span>'
      + '  </label></div>')
      + '</div>'

      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.user_details') + '</div>'
      + '<div class="detail-head"><div class="kv-grid" '
      + 'style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">'
      + input('auFullName', T('admin.text.full_name'), isNew ? '' : u.fullName, T('admin.placeholder.full_name_example'))
      + input('auLogin', T('common.label.email_login'), isNew ? '' : u.login, 'j.smith@company.com')
      + input('auPosition', T('admin.text.position'), isNew ? '' : u.userPosition, T('admin.text.logistics_specialist'))
      + input('auOrganization', T('admin.text.organization'), isNew ? '' : u.organization, T('admin.placeholder.organization_example'))
      + input('auPhone', T('partners.label.phone'), isNew ? '' : u.phone, '+7 701 000 00 00')
      + roleField(isNew ? 'depositor' : u.roleCode)
      + (isNew ? '' : dataRow(T('admin.text.last_sign_in'),
            u.lastLoginAt ? ruDateTime(u.lastLoginAt) : T('common.text.never')))
      + (isNew ? '' : dataRow(T('admin.text.password_set'),
            u.passwordChangedAt ? ruDateTime(u.passwordChangedAt) : '—'))
      + '</div></div>'
      + (isSelf ? '<div class="hint" style="margin:-6px 0 14px;">'
          + T('admin.hint.own_account') + '</div>' : '')

      + accessBlock(granted)

      + '<div class="section-title">' + T('common.label.password') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div style="font-size:13.5px;color:var(--muted);margin-bottom:12px;">'
      + (isNew
          ? T('admin.hint.new_user_password')
          : T('admin.hint.administrator_cannot_see_password')) + '</div>'
      + '  <div style="display:flex;gap:10px;align-items:flex-end;flex-wrap:wrap;">'
      + '    <div class="kv" style="margin:0;min-width:260px;"><label>'
      + (isNew ? T('common.label.password') : T('admin.text.new_password')) + '</label>'
      + '      <input type="text" class="kv-input kv-input-editable" id="adminNewPassword" '
      + 'data-i18n-placeholder="admin.placeholder.password_rule" placeholder="'
      + esc(T('admin.placeholder.password_rule')) + '"></div>'
      + '    <button class="btn" id="adminGenPassword" type="button">' + T('admin.text.generate') + '</button>'
      + (isNew ? '' : '    <button class="btn primary" id="adminSetPassword" type="button">'
          + '' + T('admin.button.set_password') + '</button>')
      + '  </div>'
      + '  <div id="adminPasswordMsg" style="display:none;margin-top:12px;font-size:13px;"></div>'
      + '</div>'

      + (isNew ? '' : ''
      + '<div class="section-title">' + T('admin.text.menu_visibility') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + checkbox('menuVisServicesTab', T('admin.text.show_the_services_tab_in_documents'), !u.hideServicesTab)
      + checkbox('menuVisBilling', T('admin.text.show_the_billing_section'), !u.hideBillingTab)
      // «Показывать раздел «Перемещения»» переехало к поклажедателю (V35):
      // это свойство клиента, а не человека, и живёт в карточке доступов.
      + checkbox('menuVisCreateReceipts', T('admin.text.allow_creating_receipts'), u.canCreateReceipts)
      + checkbox('menuVisCreateOrders', T('admin.text.allow_creating_shipment_orders'), u.canCreateOrders)
      + checkbox('menuVisCreateTransfers', T('admin.text.allow_creating_transfers'), u.canCreateTransfers)
      + checkbox('menuVisUploadSku', T('admin.text.allow_importing_products_from_exce'), u.canUploadSkuExcel)
      + checkbox('menuVisUploadPackaging', T('admin.text.allow_importing_packaging_from_exc'), u.canUploadPackagingExcel)
      + checkbox('menuVisEditTransport', T('admin.text.allow_editing_transport_details'), u.canEditTransport)
      + '</div>')

      + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
      + '  <button class="btn primary" id="adminSaveUser" type="button">'
      + (isNew ? T('admin.text.create_user') : T('portal.text.save')) + '</button>'
      + '  <button class="btn ghost" id="adminCancelUser" type="button">' + T('common.button.cancel') + '</button>'
      + '</div>';

    if (!isNew){
      const badge = byId('userIsActiveBadge');
      byId('userIsActive').onchange = function(){
        badge.className = 'badge ' + (this.checked ? 'ok' : 'err');
        badge.innerHTML = '<span class="dot"></span>' + (this.checked ? T('admin.text.active') : T('admin.text.blocked'));
      };
      byId('adminSetPassword').onclick = () => setPassword(u);
      // роль своей учётной записи не меняем — иначе можно потерять доступ
      if (isSelf) byId('auRole').disabled = true;
    }
    byId('adminGenPassword').onclick = () => { byId('adminNewPassword').value = generate(); };
    byId('adminSaveUser').onclick = () => isNew ? createUser() : saveCard(u);
    byId('adminCancelUser').onclick = () => { showTab('admin'); showAdminSub('users'); };

    if (readOnly){
      el.insertAdjacentHTML('afterbegin', '<div style="background:#eef2ff;border:1px solid #c7d2fe;'
        + 'color:#3730a3;border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px;">'
        + T('admin.text.view_mode_users') + '</div>');
      el.querySelectorAll('input, select, textarea').forEach(e => e.disabled = true);
      ['adminSetPassword','adminSaveUser','adminGenPassword'].forEach(id => {
        const b = byId(id);
        if (b) b.style.display = 'none';
      });
    }
    // карточку пользователя открывают из справочника пользователей
    PORTAL.setBack('adminUserEditBack', 'admin.button.back_to_users',
        () => { showTab('admin'); showAdminSub('users'); });
    showView('admin-user-edit');
  };

  function ruDateTime(iso){
    const d = new Date(iso);
    return isNaN(d) ? String(iso)
      : d.toLocaleString('ru-RU', {day:'2-digit', month:'2-digit', year:'numeric',
                                   hour:'2-digit', minute:'2-digit'});
  }

  /**
   * Пароль из слогов и цифр: читается вслух и переносится без ошибок.
   *
   * <p>Здесь же собирался второй, кириллический вариант слова —
   * и никуда не шёл: в ответ уходит только латинский. Выглядело
   * это хуже, чем было: правило пароля требует латинских букв,
   * и мёртвые строки читались как «портал выдаёт пароль,
   * который сам же и отвергнет».
   */
  function generate(){
    const letters = 'kmnprst', vowels = 'aeiou';
    let second = '';
    for (let i = 0; i < 3; i++){
      second += letters[Math.floor(Math.random() * letters.length)]
              + vowels[Math.floor(Math.random() * vowels.length)];
    }
    return second.charAt(0).toUpperCase() + second.slice(1) + '-'
      + Math.floor(1000 + Math.random() * 9000);   // Kinuta-4100: регистр, знак, цифры
  }

  function showMessage(msg, isOk){
    const el = byId('adminPasswordMsg');
    if (!el) return;
    el.textContent = msg;
    el.style.color = isOk ? '#1e7a3c' : '#c0392b';
    el.style.display = msg ? '' : 'none';
  }

  async function setPassword(u){
    const pwd = (byId('adminNewPassword').value || '').trim();
    if (!pwd){ showMessage(T('admin.text.enter_or_generate_a_password'), false); return; }
    try {
      await api('POST', '/api/admin/users/' + encodeURIComponent(u.userId) + '/password',
                {newPassword: pwd});
      showMessage(T('admin.message.password_issued', {password: pwd}), true);
      await loadUsers();
    } catch (e){
      showMessage(e.message, false);
    }
  }

  const valueOf_ = id => (byId(id)?.value || '').trim();

  function card(){
    return {
      login: valueOf_('auLogin'),
      fullName: valueOf_('auFullName'),
      roleCode: byId('auRole') ? byId('auRole').value : 'depositor',
      position: valueOf_('auPosition'),
      organization: valueOf_('auOrganization'),
      phone: valueOf_('auPhone'),
      depositorIds: checkedDepositors()
    };
  }

  async function createUser(){
    const button = byId('adminSaveUser');
    if (button.disabled) return;
    const form = card();
    if (!form.login || !form.fullName){
      showMessage(T('admin.text.fill_in_the_full_name_and_login'), false);
      return;
    }
    form.password = (byId('adminNewPassword').value || '').trim();
    button.disabled = true;
    const label = button.textContent;
    button.textContent = T('admin.text.creating');
    try {
      const response = await api('POST', '/api/admin/users', form);
      await loadUsers();
      if (typeof notify === 'function') notify(T('admin.message.user_created', {name: form.fullName}));
      // открываем уже созданного — там же задаётся видимость разделов,
      // и пароль показываем в его карточке: записать его можно только сейчас
      if (response.userId) await window.portalOpenAdminUser(response.userId);
      showMessage(T('admin.message.password_issued', {password: response.password}), true);
    } catch (e){
      showMessage(e.message, false);
    } finally {
      if (byId('adminSaveUser')){
        byId('adminSaveUser').disabled = false;
        byId('adminSaveUser').textContent = label;
      }
    }
  }

  async function saveCard(u){
    const isOn = id => byId(id)?.checked === true;
    const button = byId('adminSaveUser');
    if (button.disabled) return;
    button.disabled = true;
    const label = button.textContent;
    button.textContent = T('admin.text.saving');
    try {
      // сначала анкета, роль и доступы, затем видимость разделов
      await api('PUT', '/api/admin/users/' + encodeURIComponent(u.userId) + '/profile', card());
      admin.users = await api('PUT', '/api/admin/users/' + encodeURIComponent(u.userId), {
        isActive: isOn('userIsActive'),
        hideServicesTab: !isOn('menuVisServicesTab'),
        hideBillingTab: !isOn('menuVisBilling'),
        canCreateReceipts: isOn('menuVisCreateReceipts'),
        canCreateOrders: isOn('menuVisCreateOrders'),
        canCreateTransfers: isOn('menuVisCreateTransfers'),
        canUploadSkuExcel: isOn('menuVisUploadSku'),
        canUploadPackagingExcel: isOn('menuVisUploadPackaging'),
        canEditTransport: isOn('menuVisEditTransport')
      });
      drawList();
      if (typeof notify === 'function') notify(T('admin.message.user_saved', {name: u.fullName}));
      // если администратор правил себя — применяем настройки сразу
      if (PORTAL.session && PORTAL.session.login === u.login){
        // та же функция, что и при смене поклажедателя: правила
        // видимости разделов должны считаться в одном месте
        await PORTAL.refreshSettings();
      }
      showTab('admin');
      showAdminSub('users');
    } catch (e){
      showMessage(e.message, false);
      if (typeof notify === 'function') notify(T('common.message.save_failed', {error: e.message}));
    } finally {
      if (byId('adminSaveUser')){
        byId('adminSaveUser').disabled = false;
        byId('adminSaveUser').textContent = label;
      }
    }
  }

  /* ======================================================================
     3. Установка
     ====================================================================== */

  function install(){
    wireProfileButton();
    window.renderAdminUsers = drawList;
    const create = document.querySelector('#usersRefActions .btn.primary');
    if (create) create.onclick = () => window.portalOpenAdminUser(null);
    // onSession, а не onLogin: справочник пользователей — это портал
    // целиком, а не данные выбранного клиента. На onLogin он не грузился
    // до выбора поклажедателя, и администратор, которому выбор обязателен,
    // видел пустой список.
    PORTAL.onSession(() => {
      // Кнопку «Создать пользователя» разработчику не показываем: раздел
      // ему открыт для разбора, а не для ведения. Прежде это правило
      // жило в переключателе подвкладок справочников — вкладка оттуда
      // переехала, и правило переехало вместе с ней.
      const actions = document.getElementById('usersRefActions');
      if (actions){
        actions.style.display =
            (PORTAL.session && PORTAL.session.roleCode) === 'developer' ? 'none' : '';
      }
      if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen('users', drawList);
      return loadUsers();
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
