
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

  const данные = {пользователи: [], поклажедатели: [], склады: [], доступыПоклажедателей: []};

  async function загрузить(){
    try {
      const [пользователи, поклажедатели, склады, доступы] = await Promise.all([
        api('GET', '/api/admin/users'),
        api('GET', '/api/admin/depositors'),
        api('GET', '/api/admin/warehouses'),
        api('GET', '/api/admin/depositor-access')
      ]);
      данные.пользователи = пользователи;
      данные.поклажедатели = поклажедатели;
      данные.склады = склады;
      данные.доступыПоклажедателей = доступы;
    } catch (e){
      // 403 — раздел доступен только администратору, это нормально
      данные.пользователи = [];
      данные.доступыПоклажедателей = [];
    }
    отрисоватьДоступыМенеджеров();
    отрисоватьДоступыСотрудников();
    отрисоватьСкладыПоклажедателей();
  }

  /** Общий блок галочек: один список, одно правило разметки. */
  function галочки(элементы, выбранные, атрибут, подпись, пояснение){
    const выбрано = new Set(выбранные || []);
    if (!элементы.length){
      return '<div style="color:var(--muted);font-size:13.5px;">' + T('admin.text.list_is_empty') + '</div>';
    }
    return элементы.map(x =>
      '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
      + 'padding:9px 0;border-bottom:1px solid var(--line);">'
      + '<input type="checkbox" ' + атрибут + '="' + экранировать(x.id) + '"'
      + (выбрано.has(x.id) ? ' checked' : '') + '> ' + экранировать(подпись(x))
      + (пояснение && пояснение(x)
          ? ' <span style="color:var(--muted);">· ' + экранировать(пояснение(x)) + '</span>'
          : '')
      + '</label>').join('');
  }

  function отмеченные(атрибут){
    return [...document.querySelectorAll('#adminUserEditContent input[' + атрибут + ']:checked')]
      .map(cb => cb.getAttribute(атрибут));
  }

  function кнопки(подраздел, обработчик){
    const el = экран('adminUserEditContent');
    el.querySelector('[data-save]').onclick = обработчик;
    el.querySelector('[data-cancel]').onclick = () => {
      showView('admin');
      showAdminSub(подраздел);
    };
  }

  const действия = ''
    + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
    + '  <button class="btn primary" type="button" data-save>' + T('portal.text.save') + '</button>'
    + '  <button class="btn ghost" type="button" data-cancel>' + T('common.button.cancel') + '</button>'
    + '</div>';

  const толькоЧтение = () => currentUserRole === 'developer';

  function запретитьПравку(){
    if (!толькоЧтение()) return;
    const el = экран('adminUserEditContent');
    el.insertAdjacentHTML('afterbegin', '<div style="background:#eef2ff;border:1px solid #c7d2fe;'
      + 'color:#3730a3;border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px;">'
      + '👁 Режим просмотра — менять доступы вправе только администратор.</div>');
    el.querySelectorAll('input').forEach(e => e.disabled = true);
    const кнопка = el.querySelector('[data-save]');
    if (кнопка) кнопка.style.display = 'none';
  }

  /* ======================================================================
     1. «Доступы» — поклажедатели менеджера
     ====================================================================== */

  function менеджеры(){
    return данные.пользователи.filter(u => u.roleCode === 'manager');
  }

  function отрисоватьДоступыМенеджеров(){
    const body = экран('adminAccessBody');
    if (!body) return;
    const список = менеджеры();
    body.innerHTML = список.length
      ? список.map(u => '<tr onclick="portalOpenManagerAccess(\'' + u.userId + '\')">'
          + '<td><strong>' + экранировать(u.fullName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + экранировать(u.login) + '</div></td>'
          + '<td>' + экранировать(u.scope) + '</td></tr>').join('')
      : '<tr><td colspan="2" style="color:var(--muted);">' + T('admin.text.no_managers_defined') + '</td></tr>';
  }

  window.portalOpenManagerAccess = async function(userId){
    const u = данные.пользователи.find(x => x.userId === userId);
    if (!u) return;
    let выданные = [];
    try { выданные = await api('GET', '/api/admin/users/' + encodeURIComponent(userId) + '/depositors'); }
    catch (e){ выданные = []; }

    экран('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступы менеджера — ' + экранировать(u.fullName) + '</h2>'
      + '  <div class="meta">' + экранировать(u.userPosition || '—') + ' · '
      +      экранировать(u.login) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.depositors_this_manager_works_with') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Менеджер видит только отмеченных. '
      + 'Если доступен не один, портал при входе спросит, с кем он работает.</div>'
      + галочки(данные.поклажедатели.map(d => ({id: d.depositorId, name: d.orgName, code: d.code})),
                выданные, 'data-depositor-id', x => x.name, x => x.code)
      + '</div>'
      + действия;

    кнопки('access', async () => {
      try {
        await api('PUT', '/api/admin/users/' + encodeURIComponent(userId) + '/depositors',
                  {depositorIds: отмеченные('data-depositor-id')});
        await загрузить();
        if (typeof notify === 'function') notify('Доступы обновлены: ' + u.fullName);
        showView('admin');
        showAdminSub('access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    запретитьПравку();
    showView('admin-user-edit');
  };

  /* ======================================================================
     2. «Доступы сотрудников» — склады кладовщика
     ====================================================================== */

  function кладовщики(){
    return данные.пользователи.filter(u => u.roleCode === 'staff');
  }

  function отрисоватьДоступыСотрудников(){
    const body = экран('adminStaffAccessBody');
    if (!body) return;
    const список = кладовщики();
    body.innerHTML = список.length
      ? список.map(u => '<tr onclick="portalOpenStaffAccess(\'' + u.userId + '\')">'
          + '<td><strong>' + экранировать(u.fullName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + экранировать(u.login) + '</div></td>'
          + '<td>' + экранировать(u.scope) + '</td>'
          + '<td>' + экранировать(u.depositorCodes || 'по складам') + '</td></tr>').join('')
      : '<tr><td colspan="3" style="color:var(--muted);">' + T('admin.text.no_warehouse_staff_defined') + '</td></tr>';
  }

  window.portalOpenStaffAccess = async function(userId){
    const u = данные.пользователи.find(x => x.userId === userId);
    if (!u) return;
    let выданные = [];
    try { выданные = await api('GET', '/api/admin/users/' + encodeURIComponent(userId) + '/warehouses'); }
    catch (e){ выданные = []; }

    экран('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступы сотрудника — ' + экранировать(u.fullName) + '</h2>'
      + '  <div class="meta">' + экранировать(u.userPosition || '—') + ' · '
      +      экранировать(u.login) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.warehouses_this_employee_works_at') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Поклажедатели сотруднику не назначаются '
      + 'отдельно: он видит тех, чей товар лежит на его складах.</div>'
      + галочки(данные.склады.map(w => ({id: w.warehouseId, name: w.name, address: w.address})),
                выданные, 'data-warehouse-id', x => x.name, x => x.address)
      + '</div>'
      + действия;

    кнопки('staff-access', async () => {
      try {
        await api('PUT', '/api/admin/users/' + encodeURIComponent(userId) + '/warehouses',
                  {warehouseIds: отмеченные('data-warehouse-id')});
        await загрузить();
        if (typeof notify === 'function') notify('Склады сотрудника обновлены: ' + u.fullName);
        showView('admin');
        showAdminSub('staff-access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    запретитьПравку();
    showView('admin-user-edit');
  };

  /* ======================================================================
     3. «Склады поклажедателей»
     ====================================================================== */

  function отрисоватьСкладыПоклажедателей(){
    const body = экран('adminDepositorAccessBody');
    if (!body) return;
    const список = данные.доступыПоклажедателей;
    body.innerHTML = список.length
      ? список.map(d => '<tr onclick="portalOpenDepositorWarehouses(\'' + d.depositorId + '\')">'
          + '<td><strong>' + экранировать(d.orgName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + экранировать(d.code) + '</div></td>'
          + '<td>' + (d.warehouses
              ? экранировать(d.warehouses)
              : '<span style="color:var(--muted);">' + T('admin.text.all_warehouses') + '</span>') + '</td>'
          + '<td></td></tr>').join('')
      : '<tr><td colspan="3" style="color:var(--muted);">' + T('admin.text.no_depositors_defined') + '</td></tr>';
  }

  window.portalOpenDepositorWarehouses = async function(depositorId){
    const d = данные.доступыПоклажедателей.find(x => x.depositorId === depositorId);
    if (!d) return;
    let выданные = [];
    try {
      выданные = await api('GET', '/api/admin/depositors/'
                + encodeURIComponent(depositorId) + '/warehouses');
    } catch (e){ выданные = []; }

    экран('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступные склады — ' + экранировать(d.orgName) + '</h2>'
      + '  <div class="meta">Код поклажедателя: ' + экранировать(d.code) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.warehouses_where_this_depositor_st') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Отмеченные склады поклажедатель видит '
      + 'в документах. Если не отмечен ни один — доступны все: так забытая настройка '
      + 'не оставит клиента без складов.</div>'
      + галочки(данные.склады.map(w => ({id: w.warehouseId, name: w.name, address: w.address})),
                выданные, 'data-warehouse-id', x => x.name, x => x.address)
      + '</div>'
      + действия;

    кнопки('depositor-access', async () => {
      try {
        данные.доступыПоклажедателей = await api('PUT', '/api/admin/depositors/'
                + encodeURIComponent(depositorId) + '/warehouses',
                {warehouseIds: отмеченные('data-warehouse-id')});
        отрисоватьСкладыПоклажедателей();
        if (typeof notify === 'function') notify('Доступные склады обновлены: ' + d.orgName);
        // если правили текущего поклажедателя — обновляем списки складов в документах
        if (PORTAL.session && PORTAL.session.depositorId === depositorId){
          PORTAL.reloadAll();
        }
        showView('admin');
        showAdminSub('depositor-access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    запретитьПравку();
    showView('admin-user-edit');
  };

  /* ======================================================================
     4. Установка
     ====================================================================== */

  function install(){
    window.renderAdminAccess = отрисоватьДоступыМенеджеров;
    window.renderAdminStaffAccess = отрисоватьДоступыСотрудников;
    window.renderAdminDepositorAccess = отрисоватьСкладыПоклажедателей;
    // старые демо-обработчики строк больше не нужны
    window.openAdminAccessEdit = () => {};
    window.openAdminStaffAccessEdit = () => {};
    window.openAdminDepositorAccessEdit = window.portalOpenDepositorWarehouses;
    window.setStaffWarehouseFilter = window.setStaffWarehouseFilter || (() => {});
    PORTAL.onLogin(загрузить);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
