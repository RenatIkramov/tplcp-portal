
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

  const store = {users: [], depositors: [], warehouseList: [], depositorAccess: []};

  async function load(){
    // Раздел доступов — только для администратора. Без этой проверки
    // четыре запроса уходили у каждого клиента и возвращались отказом.
    if (!PORTAL.isAdmin()){
      store.users = [];
      store.depositorAccess = [];
      return;
    }
    try {
      const [users, depositors, warehouseList, access] = await Promise.all([
        api('GET', '/api/admin/users'),
        api('GET', '/api/admin/depositors'),
        api('GET', '/api/admin/warehouses'),
        api('GET', '/api/admin/depositor-access')
      ]);
      store.users = users;
      store.depositors = depositors;
      store.warehouseList = warehouseList;
      store.depositorAccess = access;
    } catch (e){
      // 403 — раздел доступен только администратору, это нормально
      store.users = [];
      store.depositorAccess = [];
    }
    drawManagerAccess();
    drawStaffAccess();
    drawDepositorWarehouses();
  }

  /** Общий блок галочек: один список, одно правило разметки. */
  function checkboxes(items, selectedItems, attr, label, note){
    const chosen = new Set(selectedItems || []);
    if (!items.length){
      return '<div style="color:var(--muted);font-size:13.5px;">' + T('admin.text.list_is_empty') + '</div>';
    }
    return items.map(x =>
      '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
      + 'padding:9px 0;border-bottom:1px solid var(--line);">'
      + '<input type="checkbox" ' + attr + '="' + esc(x.id) + '"'
      + (chosen.has(x.id) ? ' checked' : '') + '> ' + esc(label(x))
      + (note && note(x)
          ? ' <span style="color:var(--muted);">· ' + esc(note(x)) + '</span>'
          : '')
      + '</label>').join('');
  }

  function checked(attr){
    return [...document.querySelectorAll('#adminUserEditContent input[' + attr + ']:checked')]
      .map(cb => cb.getAttribute(attr));
  }

  function buttons(section, handler){
    const el = byId('adminUserEditContent');
    el.querySelector('[data-save]').onclick = handler;
    el.querySelector('[data-cancel]').onclick = () => {
      showView('admin');
      showAdminSub(section);
    };
  }

  const actions = ''
    + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
    + '  <button class="btn primary" type="button" data-save>' + T('portal.text.save') + '</button>'
    + '  <button class="btn ghost" type="button" data-cancel>' + T('common.button.cancel') + '</button>'
    + '</div>';

  const readOnly = () => currentUserRole === 'developer';

  function lockEditing(){
    if (!readOnly()) return;
    const el = byId('adminUserEditContent');
    el.insertAdjacentHTML('afterbegin', '<div style="background:#eef2ff;border:1px solid #c7d2fe;'
      + 'color:#3730a3;border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px;">'
      + '👁 Режим просмотра — менять доступы вправе только администратор.</div>');
    el.querySelectorAll('input').forEach(e => e.disabled = true);
    const button = el.querySelector('[data-save]');
    if (button) button.style.display = 'none';
  }

  /* ======================================================================
     1. «Доступы» — поклажедатели менеджера
     ====================================================================== */

  function managers(){
    return store.users.filter(u => u.roleCode === 'manager');
  }

  function drawManagerAccess(){
    const body = byId('adminAccessBody');
    if (!body) return;
    const list = managers();
    body.innerHTML = list.length
      ? list.map(u => '<tr onclick="portalOpenManagerAccess(\'' + u.userId + '\')">'
          + '<td><strong>' + esc(u.fullName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + esc(u.login) + '</div></td>'
          + '<td>' + esc(u.scope) + '</td></tr>').join('')
      : '<tr><td colspan="2" style="color:var(--muted);">' + T('admin.text.no_managers_defined') + '</td></tr>';
  }

  window.portalOpenManagerAccess = async function(userId){
    const u = store.users.find(x => x.userId === userId);
    if (!u) return;
    let granted;
    try { granted = await api('GET', '/api/admin/users/' + encodeURIComponent(userId) + '/depositors'); }
    catch (e){ granted = []; }

    byId('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступы менеджера — ' + esc(u.fullName) + '</h2>'
      + '  <div class="meta">' + esc(u.userPosition || '—') + ' · '
      +      esc(u.login) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.depositors_this_manager_works_with') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Менеджер видит только отмеченных. '
      + 'Если доступен не один, портал при входе спросит, с кем он работает.</div>'
      + checkboxes(store.depositors.map(d => ({id: d.depositorId, name: d.orgName, code: d.code})),
                granted, 'data-depositor-id', x => x.name, x => x.code)
      + '</div>'
      + actions;

    buttons('access', async () => {
      try {
        await api('PUT', '/api/admin/users/' + encodeURIComponent(userId) + '/depositors',
                  {depositorIds: checked('data-depositor-id')});
        await load();
        if (typeof notify === 'function') notify('Доступы обновлены: ' + u.fullName);
        showView('admin');
        showAdminSub('access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    // возврат — в ту подвкладку, из которой открыли
    PORTAL.setBack('adminUserEditBack', 'admin.button.back_to_manager_access',
        () => { showView('admin'); showAdminSub('access'); });
    lockEditing();
    showView('admin-user-edit');
  };

  /* ======================================================================
     2. «Доступы сотрудников» — склады кладовщика
     ====================================================================== */

  function warehouseStaff(){
    return store.users.filter(u => u.roleCode === 'staff');
  }

  function drawStaffAccess(){
    const body = byId('adminStaffAccessBody');
    if (!body) return;
    const list = warehouseStaff();
    body.innerHTML = list.length
      ? list.map(u => '<tr onclick="portalOpenStaffAccess(\'' + u.userId + '\')">'
          + '<td><strong>' + esc(u.fullName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + esc(u.login) + '</div></td>'
          + '<td>' + esc(u.scope) + '</td>'
          + '<td>' + esc(u.depositorCodes || 'по складам') + '</td></tr>').join('')
      : '<tr><td colspan="3" style="color:var(--muted);">' + T('admin.text.no_warehouse_staff_defined') + '</td></tr>';
  }

  window.portalOpenStaffAccess = async function(userId){
    const u = store.users.find(x => x.userId === userId);
    if (!u) return;
    let granted;
    try { granted = await api('GET', '/api/admin/users/' + encodeURIComponent(userId) + '/warehouses'); }
    catch (e){ granted = []; }

    byId('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступы сотрудника — ' + esc(u.fullName) + '</h2>'
      + '  <div class="meta">' + esc(u.userPosition || '—') + ' · '
      +      esc(u.login) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.warehouses_this_employee_works_at') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Поклажедатели сотруднику не назначаются '
      + 'отдельно: он видит тех, чей товар лежит на его складах.</div>'
      + checkboxes(store.warehouseList.map(w => ({id: w.warehouseId, name: w.name, address: w.address})),
                granted, 'data-warehouse-id', x => x.name, x => x.address)
      + '</div>'
      + actions;

    buttons('staff-access', async () => {
      try {
        await api('PUT', '/api/admin/users/' + encodeURIComponent(userId) + '/warehouses',
                  {warehouseIds: checked('data-warehouse-id')});
        await load();
        if (typeof notify === 'function') notify('Склады сотрудника обновлены: ' + u.fullName);
        showView('admin');
        showAdminSub('staff-access');
      } catch (e){
        if (typeof notify === 'function') notify('Не удалось сохранить: ' + e.message);
      }
    });
    // возврат — в ту подвкладку, из которой открыли
    PORTAL.setBack('adminUserEditBack', 'admin.button.back_to_staff_access',
        () => { showView('admin'); showAdminSub('staff-access'); });
    lockEditing();
    showView('admin-user-edit');
  };

  /* ======================================================================
     3. «Склады поклажедателей»
     ====================================================================== */

  function drawDepositorWarehouses(){
    const body = byId('adminDepositorAccessBody');
    if (!body) return;
    const list = store.depositorAccess;
    body.innerHTML = list.length
      ? list.map(d => '<tr onclick="portalOpenDepositorWarehouses(\'' + d.depositorId + '\')">'
          + '<td><strong>' + esc(d.orgName) + '</strong>'
          + '<div style="color:var(--muted);font-size:12.5px;">' + esc(d.code) + '</div></td>'
          + '<td>' + (d.warehouses
              ? esc(d.warehouses)
              : '<span style="color:var(--muted);">' + T('admin.text.all_warehouses') + '</span>') + '</td>'
          + '<td></td></tr>').join('')
      : '<tr><td colspan="3" style="color:var(--muted);">' + T('admin.text.no_depositors_defined') + '</td></tr>';
  }

  window.portalOpenDepositorWarehouses = async function(depositorId){
    const d = store.depositorAccess.find(x => x.depositorId === depositorId);
    if (!d) return;
    let granted;
    try {
      granted = await api('GET', '/api/admin/depositors/'
                + encodeURIComponent(depositorId) + '/warehouses');
    } catch (e){ granted = []; }

    byId('adminUserEditContent').innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>Доступные склады — ' + esc(d.orgName) + '</h2>'
      + '  <div class="meta">Код поклажедателя: ' + esc(d.code) + '</div></div></div>'
      + '<div class="section-title" style="margin-top:0;">' + T('admin.text.warehouses_where_this_depositor_st') + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">Отмеченные склады поклажедатель видит '
      + 'в документах. Если не отмечен ни один — доступны все: так забытая настройка '
      + 'не оставит клиента без складов.</div>'
      + checkboxes(store.warehouseList.map(w => ({id: w.warehouseId, name: w.name, address: w.address})),
                granted, 'data-warehouse-id', x => x.name, x => x.address)
      + '</div>'
      + actions;

    buttons('depositor-access', async () => {
      try {
        store.depositorAccess = await api('PUT', '/api/admin/depositors/'
                + encodeURIComponent(depositorId) + '/warehouses',
                {warehouseIds: checked('data-warehouse-id')});
        drawDepositorWarehouses();
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
    // возврат — в ту подвкладку, из которой открыли
    PORTAL.setBack('adminUserEditBack', 'admin.button.back_to_depositor_access',
        () => { showView('admin'); showAdminSub('depositor-access'); });
    lockEditing();
    showView('admin-user-edit');
  };

  /* ======================================================================
     4. Установка
     ====================================================================== */

  function install(){
    window.renderAdminAccess = drawManagerAccess;
    window.renderAdminStaffAccess = drawStaffAccess;
    window.renderAdminDepositorAccess = drawDepositorWarehouses;
    // старые демо-обработчики строк больше не нужны
    window.openAdminAccessEdit = () => {};
    window.openAdminStaffAccessEdit = () => {};
    window.openAdminDepositorAccessEdit = window.portalOpenDepositorWarehouses;
    window.setStaffWarehouseFilter = window.setStaffWarehouseFilter || (() => {});
    // Доступы ведутся по всему порталу и от выбранного клиента
    // не зависят — грузим сразу после входа (см. PORTAL.onSession).
    PORTAL.onSession(load);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
