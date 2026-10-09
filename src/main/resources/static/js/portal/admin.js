
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

  const экран = id => document.getElementById(id);
  const экранировать = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  /* ======================================================================
     1. Окно смены пароля
     ====================================================================== */

  // Два режима: обычная смена из личного кабинета и обязательная — когда
  // пароль выдал администратор. В обязательном режиме окно не закрывается:
  // до смены пароля сервер всё равно не отдаёт данных.
  let обязательная = false;

  function создатьОкно(){
    if (экран('pwOverlay')) return;
    const окно = document.createElement('div');
    окно.className = 'col-overlay';
    окно.id = 'pwOverlay';
    окно.style.cssText = 'align-items:center;justify-content:center;';
    окно.onclick = e => { if (e.target === окно && !обязательная) закрыть(); };
    окно.innerHTML = ''
      + '<div class="col-panel" id="pwPanel" style="width:460px;box-shadow:0 8px 32px rgba(0,0,0,.24);'
      + 'border-radius:10px;">'
      + '  <h3 id="pwTitle" style="font-size:17px;">' + T('admin.text.change_password') + '</h3>'
      + '  <div class="hint" id="pwHint">' + T('admin.text.choose_a_password_that_meets_the_r') + '</div>'
      + '  <div class="kv" style="margin-top:12px;"><label>' + T('admin.text.current_password') + '</label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwCurrent" autocomplete="current-password"></div>'
      + '  <div class="kv" style="margin-top:12px;"><label>' + T('admin.text.new_password') + '</label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwNew" autocomplete="new-password"></div>'
      + '  <div class="kv" style="margin-top:12px;"><label>' + T('admin.text.repeat_new_password') + '</label>'
      + '    <input type="password" class="kv-input kv-input-editable" id="pwRepeat" autocomplete="new-password"></div>'
      + '  <div id="pwRules" style="margin-top:12px;padding:10px 12px;background:var(--bg);'
      + 'border-radius:6px;font-size:12.5px;line-height:1.9;">'
      + '    <div style="color:var(--muted);margin-bottom:2px;">' + T('admin.text.password_requirements') + '</div>'
      + '    <div data-pw-rule="length"><span class="pw-mark">○</span>' + ' ' + T('admin.text.at_least_8_characters') + '</div>'
      + '    <div data-pw-rule="case"><span class="pw-mark">○</span> латинские буквы: '
      + 'и заглавные, и строчные</div>'
      + '    <div data-pw-rule="extra"><span class="pw-mark">○</span> цифра или знак '
      + '(например - _ . ! #)</div>'
      + '  </div>'
      + '  <div id="pwError" style="display:none;margin-top:12px;padding:8px 10px;border-radius:6px;'
      + 'background:#fdecea;color:#c0392b;font-size:13px;"></div>'
      + '  <div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
      + '    <button class="btn primary" id="pwSave">' + T('admin.text.change_password2') + '</button>'
      + '    <button class="btn ghost" id="pwCancel">' + T('common.button.cancel') + '</button>'
      + '  </div>'
      + '</div>';
    document.body.appendChild(окно);
    экран('pwSave').onclick = сохранить;
    экран('pwCancel').onclick = закрыть;
    ['pwCurrent','pwNew','pwRepeat'].forEach(id => {
      экран(id).addEventListener('keydown', e => { if (e.key === 'Enter') сохранить(); });
    });
    экран('pwNew').addEventListener('input', отметитьТребования);
    отметитьТребования();
  }

  /**
   * Те же правила, что и на сервере, — здесь только чтобы человек видел,
   * чего не хватает, ещё до нажатия кнопки. Решение всё равно за сервером.
   */
  function выполненные(пароль){
    const цифраИлиЗнак = /[0-9]/.test(пароль) || /[^0-9A-Za-zА-Яа-яЁё]/.test(пароль);
    return {
      length: пароль.length >= 8,
      case: /[a-z]/.test(пароль) && /[A-Z]/.test(пароль),
      extra: цифраИлиЗнак
    };
  }

  function отметитьТребования(){
    const поле = экран('pwNew');
    const пароль = поле ? поле.value : '';
    const готово = выполненные(пароль);
    const список = экран('pwRules');
    if (!список) return;
    список.querySelectorAll('[data-pw-rule]').forEach(строка => {
      const ok = готово[строка.getAttribute('data-pw-rule')] === true;
      строка.style.color = пароль && ok ? '#1e7a3c' : 'inherit';
      const метка = строка.querySelector('.pw-mark');
      if (метка) метка.textContent = пароль && ok ? '✓' : '○';
    });
  }

  function ошибка(текст){
    const el = экран('pwError');
    if (!el) return;
    el.textContent = текст || '';
    el.style.display = текст ? '' : 'none';
  }

  function закрыть(){
    if (обязательная) return;
    экран('pwOverlay')?.classList.remove('active');
    ['pwCurrent','pwNew','pwRepeat'].forEach(id => { const el = экран(id); if (el) el.value = ''; });
    ошибка('');
  }

  /** Открыть окно. principal === 'forced' — обязательная смена после входа. */
  window.portalOpenPasswordChange = function(режим){
    создатьОкно();
    обязательная = режим === 'forced';
    экран('pwTitle').textContent = обязательная ? T('admin.text.set_your_password') : T('admin.text.change_password');
    экран('pwHint').textContent = обязательная
      ? 'Пароль выдал администратор портала — работать можно только со своим. '
        + T('admin.text.enter_the_password_you_were_given')
      : T('admin.text.choose_a_password_that_meets_the_r');
    экран('pwCancel').style.display = обязательная ? 'none' : '';
    ошибка('');
    экран('pwOverlay').classList.add('active');
    setTimeout(() => экран('pwCurrent').focus(), 50);
  };

  async function сохранить(){
    const кнопка = экран('pwSave');
    if (кнопка.disabled) return;          // второй клик, пока идёт первый запрос
    const текущий = экран('pwCurrent').value;
    const новый = экран('pwNew').value;
    const повтор = экран('pwRepeat').value;
    if (!текущий || !новый){ ошибка(T('common.message.fill_in_all_fields')); return; }
    if (новый !== повтор){ ошибка(T('admin.text.new_password_and_its_repeat_do')); return; }
    // Проверка пароля на сервере по своей природе небыстрая — хеш считается
    // медленно намеренно. Показываем, что запрос идёт, и не даём кликать дважды.
    const подпись = кнопка.textContent;
    кнопка.disabled = true;
    кнопка.textContent = T('admin.text.checking');
    ошибка('');
    try {
      await api('POST', '/api/me/password', {currentPassword: текущий, newPassword: новый});
      const былаОбязательная = обязательная;
      обязательная = false;
      закрыть();
      if (typeof notify === 'function') notify(T('server.message.password_changed'));
      if (былаОбязательная){
        // до смены пароля данные не загружались — делаем это теперь
        if (PORTAL.session) PORTAL.session.mustChangePassword = false;
        PORTAL.reloadAll();
      }
    } catch (e){
      // единственная причина отказа 403 на этом адресе — не тот текущий пароль
      ошибка(e.status === 403 ? T('admin.text.current_password_is_incorrect') : e.message);
    } finally {
      кнопка.disabled = false;
      кнопка.textContent = подпись;
    }
  }

  /** Кнопка «Сменить пароль» в личном кабинете — та же, что была в прототипе. */
  function подключитьКнопкуВКабинете(){
    const штатный = window.renderProfile;
    if (typeof штатный !== 'function') return;
    window.renderProfile = function(user){
      штатный(user);
      const host = экран('profileContent');
      if (!host) return;
      [...host.querySelectorAll('button')]
        .filter(b => b.textContent.trim().indexOf(T('admin.text.change_password2')) === 0)
        .forEach(b => { b.onclick = () => window.portalOpenPasswordChange(); });
    };
  }

  /* ======================================================================
     2. Справочник «Пользователи» из базы портала
     ====================================================================== */

  const админ = {пользователи: [], роли: [], поклажедатели: [], ошибка: null};

  async function загрузитьПользователей(){
    try {
      const [список, роли, поклажедатели] = await Promise.all([
        api('GET', '/api/admin/users'),
        api('GET', '/api/admin/roles'),
        api('GET', '/api/admin/depositors')
      ]);
      админ.пользователи = список;
      админ.роли = роли;
      админ.поклажедатели = поклажедатели;
      админ.ошибка = null;
    } catch (e){
      админ.пользователи = [];
      // 403 — обычное дело: раздел видит только администратор
      админ.ошибка = e.status === 403 ? null : e.message;
    }
    отрисоватьСписок();
  }

  function отрисоватьСписок(){
    const body = экран('adminUsersBody');
    if (!body) return;
    if (админ.ошибка){
      body.innerHTML = '<tr><td colspan="6" style="color:#c0392b;">Не удалось получить список: '
        + экранировать(админ.ошибка) + '</td></tr>';
      return;
    }
    if (!админ.пользователи.length){
      body.innerHTML = '<tr><td colspan="6" style="color:var(--muted);">' + T('admin.text.no_data') + '</td></tr>';
      return;
    }
    body.innerHTML = админ.пользователи.map(u => {
      const статус = u.isActive
        ? '<span class="badge ok"><span class="dot"></span>' + T('admin.text.active') + '</span>'
        : '<span class="badge err"><span class="dot"></span>' + T('admin.text.blocked') + '</span>';
      const пароль = u.mustChangePassword
        ? ' <span class="badge warn" title="Пароль выдан администратором и будет сменён при входе">'
          + '<span class="dot"></span>' + T('admin.text.must_change_password') + '</span>'
        : '';
      return '<tr onclick="portalOpenAdminUser(\'' + u.userId + '\')">'
        + '<td><strong>' + экранировать(u.fullName) + '</strong></td>'
        + '<td>' + экранировать(u.roleName || u.roleCode) + '</td>'
        + '<td>' + экранировать(u.scope) + '</td>'
        + '<td>' + экранировать(u.login) + '</td>'
        + '<td>' + экранировать(u.depositorCodes || '—') + '</td>'
        + '<td>' + статус + пароль + '</td>'
        + '</tr>';
    }).join('');
  }

  const галочка = (id, подпись, включено) =>
    '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;margin-bottom:12px;">'
    + '<input type="checkbox" id="' + id + '"' + (включено ? ' checked' : '') + '> ' + подпись + '</label>';

  const строкаДанных = (подпись, значение) =>
    '<div class="kv"><label>' + подпись + '</label><div>' + экранировать(значение || '—') + '</div></div>';

  const полеВвода = (id, подпись, значение, подсказка) =>
    '<div class="kv"><label>' + подпись + '</label><div>'
    + '<input type="text" class="kv-input kv-input-editable" id="' + id + '"'
    + ' value="' + экранировать(значение || '') + '"'
    + (подсказка ? ' placeholder="' + экранировать(подсказка) + '"' : '') + '></div></div>';

  const полеРоли = (выбрана) =>
    '<div class="kv"><label>' + T('admin.text.portal_role') + '</label><div>'
    + '<select class="kv-input kv-input-editable" id="auRole">'
    + админ.роли.map(r => '<option value="' + r.code + '"'
        + (r.code === выбрана ? ' selected' : '') + '>' + экранировать(r.name) + '</option>').join('')
    + '</select></div></div>';

  /**
   * Галочки доступных поклажедателей. Для складского сотрудника их не
   * показываем: ему доступ выдаётся по складам, это отдельный экран.
   */
  function блокДоступов(выданные){
    const выбрано = new Set(выданные || []);
    return '<div class="section-title">' + T('admin.text.accessible_depositors') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;" id="auDepositors">'
      + '  <div class="hint" style="margin:-4px 0 10px;">' + T('admin.hint.depositors_of_a_user') + '</div>'
      + (админ.поклажедатели.length
          ? админ.поклажедатели.map(d =>
              '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
              + 'padding:8px 0;border-bottom:1px solid var(--line);">'
              + '<input type="checkbox" data-depositor-id="' + d.depositorId + '"'
              + (выбрано.has(d.depositorId) ? ' checked' : '') + '> '
              + экранировать(d.orgName) + ' <span style="color:var(--muted);">· '
              + экранировать(d.code) + '</span></label>').join('')
          : '<div style="color:var(--muted);font-size:13.5px;">' + T('admin.text.no_depositors_have_been_created_ye') + '</div>')
      + '</div>';
  }

  function отмеченныеПоклажедатели(){
    return [...document.querySelectorAll('#auDepositors input[data-depositor-id]:checked')]
      .map(cb => cb.getAttribute('data-depositor-id'));
  }

  /**
   * Карточка пользователя. Одна и та же форма работает на правку и на
   * создание: при создании `userId` не передан, доступен ввод логина
   * и пароля, а блоки «последний вход» и «пароль» выглядят иначе.
   */
  window.portalOpenAdminUser = async function(userId){
    const u = userId ? админ.пользователи.find(x => x.userId === userId) : null;
    if (userId && !u) return;
    const новый = !u;
    const el = экран('adminUserEditContent');
    if (!el) return;
    const свой = !новый && PORTAL.session && PORTAL.session.login === u.login;
    const толькоЧтение = currentUserRole === 'developer';

    // Какие поклажедатели уже выданы — спрашиваем отдельно, в списке их нет
    let выданные = [];
    if (!новый){
      try { выданные = await api('GET', '/api/admin/users/' + encodeURIComponent(u.userId) + '/depositors'); }
      catch (e){ выданные = []; }
    }

    el.innerHTML = ''
      + '<div class="detail-head">'
      + '  <div><h2>' + (новый ? T('admin.text.new_user') : экранировать(u.fullName)) + '</h2>'
      + '    <div class="meta">' + (новый
            ? T('admin.text.fill_in_the_details_and_issue_a_pa')
            : экранировать(u.userPosition || '—') + ' · ' + экранировать(u.organization || '—')) + '</div></div>'
      + (новый ? '' : ''
      + '  <div><label style="display:flex;align-items:center;gap:8px;font-size:13.5px;cursor:pointer;">'
      + '    <input type="checkbox" id="userIsActive"' + (u.isActive ? ' checked' : '')
      +      (свой ? ' disabled title="Свою учётную запись заблокировать нельзя"' : '') + '>'
      + '    <span class="badge ' + (u.isActive ? 'ok' : 'err') + '" id="userIsActiveBadge">'
      + '<span class="dot"></span>' + (u.isActive ? T('admin.text.active') : T('admin.text.blocked')) + '</span>'
      + '  </label></div>')
      + '</div>'

      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.user_details') + '</div>'
      + '<div class="detail-head"><div class="kv-grid" '
      + 'style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">'
      + полеВвода('auFullName', T('admin.text.full_name'), новый ? '' : u.fullName, T('admin.placeholder.full_name_example'))
      + полеВвода('auLogin', T('common.label.email_login'), новый ? '' : u.login, 'j.smith@company.com')
      + полеВвода('auPosition', T('admin.text.position'), новый ? '' : u.userPosition, T('admin.text.logistics_specialist'))
      + полеВвода('auOrganization', T('admin.text.organization'), новый ? '' : u.organization, T('admin.placeholder.organization_example'))
      + полеВвода('auPhone', T('partners.label.phone'), новый ? '' : u.phone, '+7 701 000 00 00')
      + полеРоли(новый ? 'depositor' : u.roleCode)
      + (новый ? '' : строкаДанных(T('admin.text.last_sign_in'),
            u.lastLoginAt ? ruДатаВремя(u.lastLoginAt) : T('common.text.never')))
      + (новый ? '' : строкаДанных(T('admin.text.password_set'),
            u.passwordChangedAt ? ruДатаВремя(u.passwordChangedAt) : '—'))
      + '</div></div>'
      + (свой ? '<div class="hint" style="margin:-6px 0 14px;">Это ваша учётная запись: '
          + 'роль и блокировку для себя менять нельзя.</div>' : '')

      + блокДоступов(выданные)

      + '<div class="section-title">' + T('common.label.password') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div style="font-size:13.5px;color:var(--muted);margin-bottom:12px;">'
      + (новый
          ? T('admin.hint.new_user_password')
          : T('admin.hint.administrator_cannot_see_password')) + '</div>'
      + '  <div style="display:flex;gap:10px;align-items:flex-end;flex-wrap:wrap;">'
      + '    <div class="kv" style="margin:0;min-width:260px;"><label>'
      + (новый ? T('common.label.password') : T('admin.text.new_password')) + '</label>'
      + '      <input type="text" class="kv-input kv-input-editable" id="adminNewPassword" '
      + 'data-i18n-placeholder="admin.placeholder.password_rule" placeholder="латинские буквы разного регистра + цифра или знак"></div>'
      + '    <button class="btn" id="adminGenPassword" type="button">' + T('admin.text.generate') + '</button>'
      + (новый ? '' : '    <button class="btn primary" id="adminSetPassword" type="button">'
          + '' + T('admin.button.set_password') + '</button>')
      + '  </div>'
      + '  <div id="adminPasswordMsg" style="display:none;margin-top:12px;font-size:13px;"></div>'
      + '</div>'

      + (новый ? '' : ''
      + '<div class="section-title">' + T('admin.text.menu_visibility') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + галочка('menuVisServicesTab', T('admin.text.show_the_services_tab_in_documents'), !u.hideServicesTab)
      + галочка('menuVisBilling', T('admin.text.show_the_billing_section'), !u.hideBillingTab)
      // «Показывать раздел «Перемещения»» переехало к поклажедателю (V35):
      // это свойство клиента, а не человека, и живёт в карточке доступов.
      + галочка('menuVisCreateReceipts', T('admin.text.allow_creating_receipts'), u.canCreateReceipts)
      + галочка('menuVisCreateOrders', T('admin.text.allow_creating_shipment_orders'), u.canCreateOrders)
      + галочка('menuVisCreateTransfers', T('admin.text.allow_creating_transfers'), u.canCreateTransfers)
      + галочка('menuVisUploadSku', T('admin.text.allow_importing_products_from_exce'), u.canUploadSkuExcel)
      + галочка('menuVisUploadPackaging', T('admin.text.allow_importing_packaging_from_exc'), u.canUploadPackagingExcel)
      + галочка('menuVisEditTransport', T('admin.text.allow_editing_transport_details'), u.canEditTransport)
      + '</div>')

      + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
      + '  <button class="btn primary" id="adminSaveUser" type="button">'
      + (новый ? T('admin.text.create_user') : T('portal.text.save')) + '</button>'
      + '  <button class="btn ghost" id="adminCancelUser" type="button">' + T('common.button.cancel') + '</button>'
      + '</div>';

    if (!новый){
      const бейдж = экран('userIsActiveBadge');
      экран('userIsActive').onchange = function(){
        бейдж.className = 'badge ' + (this.checked ? 'ok' : 'err');
        бейдж.innerHTML = '<span class="dot"></span>' + (this.checked ? T('admin.text.active') : T('admin.text.blocked'));
      };
      экран('adminSetPassword').onclick = () => задатьПароль(u);
      // роль своей учётной записи не меняем — иначе можно потерять доступ
      if (свой) экран('auRole').disabled = true;
    }
    экран('adminGenPassword').onclick = () => { экран('adminNewPassword').value = придумать(); };
    экран('adminSaveUser').onclick = () => новый ? создатьПользователя() : сохранитьКарточку(u);
    экран('adminCancelUser').onclick = () => { showTab('references'); showReferencesSub('users'); };

    if (толькоЧтение){
      el.insertAdjacentHTML('afterbegin', '<div style="background:#eef2ff;border:1px solid #c7d2fe;'
        + 'color:#3730a3;border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px;">'
        + '👁 Режим просмотра — менять пользователей вправе только администратор.</div>');
      el.querySelectorAll('input, select, textarea').forEach(e => e.disabled = true);
      ['adminSetPassword','adminSaveUser','adminGenPassword'].forEach(id => {
        const b = экран(id);
        if (b) b.style.display = 'none';
      });
    }
    showView('admin-user-edit');
  };

  function ruДатаВремя(iso){
    const d = new Date(iso);
    return isNaN(d) ? String(iso)
      : d.toLocaleString('ru-RU', {day:'2-digit', month:'2-digit', year:'numeric',
                                   hour:'2-digit', minute:'2-digit'});
  }

  /** Пароль из слогов и цифр: читается вслух и переносится без ошибок. */
  function придумать(){
    const согласные = 'бвгдкмнпрстфх', гласные = 'аеиоуя';
    let слово = '';
    for (let i = 0; i < 4; i++){
      слово += согласные[Math.floor(Math.random() * согласные.length)]
             + гласные[Math.floor(Math.random() * гласные.length)];
    }
    const латиница = 'kmnprst', гл = 'aeiou';
    let вторая = '';
    for (let i = 0; i < 3; i++){
      вторая += латиница[Math.floor(Math.random() * латиница.length)]
              + гл[Math.floor(Math.random() * гл.length)];
    }
    return вторая.charAt(0).toUpperCase() + вторая.slice(1) + '-'
      + Math.floor(1000 + Math.random() * 9000);   // Kinuta-4100: регистр, знак, цифры
  }

  function сообщение(текст, успех){
    const el = экран('adminPasswordMsg');
    if (!el) return;
    el.textContent = текст;
    el.style.color = успех ? '#1e7a3c' : '#c0392b';
    el.style.display = текст ? '' : 'none';
  }

  async function задатьПароль(u){
    const пароль = (экран('adminNewPassword').value || '').trim();
    if (!пароль){ сообщение(T('admin.text.enter_or_generate_a_password'), false); return; }
    try {
      await api('POST', '/api/admin/users/' + encodeURIComponent(u.userId) + '/password',
                {newPassword: пароль});
      сообщение('Пароль задан: ' + пароль + ' — передайте его пользователю, при первом входе '
        + 'портал попросит сменить', true);
      await загрузитьПользователей();
    } catch (e){
      сообщение(e.message, false);
    }
  }

  const значение = id => (экран(id)?.value || '').trim();

  function анкета(){
    return {
      login: значение('auLogin'),
      fullName: значение('auFullName'),
      roleCode: экран('auRole') ? экран('auRole').value : 'depositor',
      position: значение('auPosition'),
      organization: значение('auOrganization'),
      phone: значение('auPhone'),
      depositorIds: отмеченныеПоклажедатели()
    };
  }

  async function создатьПользователя(){
    const кнопка = экран('adminSaveUser');
    if (кнопка.disabled) return;
    const данные = анкета();
    if (!данные.login || !данные.fullName){
      сообщение(T('admin.text.fill_in_the_full_name_and_login'), false);
      return;
    }
    данные.password = (экран('adminNewPassword').value || '').trim();
    кнопка.disabled = true;
    const подпись = кнопка.textContent;
    кнопка.textContent = T('admin.text.creating');
    try {
      const ответ = await api('POST', '/api/admin/users', данные);
      await загрузитьПользователей();
      if (typeof notify === 'function') notify('Пользователь создан: ' + данные.fullName);
      // открываем уже созданного — там же задаётся видимость разделов,
      // и пароль показываем в его карточке: записать его можно только сейчас
      if (ответ.userId) await window.portalOpenAdminUser(ответ.userId);
      сообщение('Пароль: ' + ответ.password + ' — передайте его пользователю, при первом '
        + 'входе портал попросит сменить', true);
    } catch (e){
      сообщение(e.message, false);
    } finally {
      if (экран('adminSaveUser')){
        экран('adminSaveUser').disabled = false;
        экран('adminSaveUser').textContent = подпись;
      }
    }
  }

  async function сохранитьКарточку(u){
    const вкл = id => экран(id)?.checked === true;
    const кнопка = экран('adminSaveUser');
    if (кнопка.disabled) return;
    кнопка.disabled = true;
    const подпись = кнопка.textContent;
    кнопка.textContent = T('admin.text.saving');
    try {
      // сначала анкета, роль и доступы, затем видимость разделов
      await api('PUT', '/api/admin/users/' + encodeURIComponent(u.userId) + '/profile', анкета());
      админ.пользователи = await api('PUT', '/api/admin/users/' + encodeURIComponent(u.userId), {
        isActive: вкл('userIsActive'),
        hideServicesTab: !вкл('menuVisServicesTab'),
        hideBillingTab: !вкл('menuVisBilling'),
        canCreateReceipts: вкл('menuVisCreateReceipts'),
        canCreateOrders: вкл('menuVisCreateOrders'),
        canCreateTransfers: вкл('menuVisCreateTransfers'),
        canUploadSkuExcel: вкл('menuVisUploadSku'),
        canUploadPackagingExcel: вкл('menuVisUploadPackaging'),
        canEditTransport: вкл('menuVisEditTransport')
      });
      отрисоватьСписок();
      if (typeof notify === 'function') notify('Изменения сохранены: ' + u.fullName);
      // если администратор правил себя — применяем настройки сразу
      if (PORTAL.session && PORTAL.session.login === u.login){
        // та же функция, что и при смене поклажедателя: правила
        // видимости разделов должны считаться в одном месте
        await PORTAL.обновитьНастройки();
      }
      showTab('references');
      showReferencesSub('users');
    } catch (e){
      сообщение(e.message, false);
      if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
    } finally {
      if (экран('adminSaveUser')){
        экран('adminSaveUser').disabled = false;
        экран('adminSaveUser').textContent = подпись;
      }
    }
  }

  /* ======================================================================
     3. Установка
     ====================================================================== */

  function install(){
    подключитьКнопкуВКабинете();
    window.renderAdminUsers = отрисоватьСписок;
    const создать = document.querySelector('#usersRefActions .btn.primary');
    if (создать) создать.onclick = () => window.portalOpenAdminUser(null);
    PORTAL.onLogin(загрузитьПользователей);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
