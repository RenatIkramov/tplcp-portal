
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const EDITOR_ROLES = ['depositor', 'manager', 'admin'];
  const mayEdit = () => EDITOR_ROLES.includes(PORTAL.session && PORTAL.session.roleCode);

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  /* Два справочника устроены одинаково, различия собраны здесь —
     как и на сервере (PartnerKind). */
  const KINDS = {
    suppliers: {
      path: 'suppliers', key: 'supplierId', title: T('server.message.supplier'),
      newTitle: T('partners.text.new_supplier'), body: 'suppliersBody', count: 'suppliersCount',
      columns: 7, addresses: false
    },
    consignees: {
      path: 'consignees', key: 'consigneeId', title: T('partners.title.consignee'),
      newTitle: T('partners.text.new_consignee'), body: 'consigneesBody', count: 'consigneesCount',
      columns: 8, addresses: true
    }
  };

  const FIELDS = [
    {col: 'code', label: T('references.column.code'), required: true},
    {col: 'name', label: T('references.column.name'), required: true},
    {col: 'bin', label: T('partners.label.bin')},
    {col: 'legal_address', label: T('portal.text.legal_address')},
    {col: 'contact_name', label: T('references.column.contact_person')},
    {col: 'contact_phone', label: T('partners.label.phone')},
    {col: 'contact_email', label: T('common.column.email')}
  ];

  const camel = col => col.replace(/_([a-z])/g, (m, c) => c.toUpperCase());

  const state = {
    kind: null, mode: 'view', id: null, row: null, draft: {},
    addresses: [], addressForm: null, tab: 'card'
  };
  PORTAL.partnerEdit = state;

  /* ================= Карточка ================= */

  function ensureView(){
    if (document.getElementById('view-partner-detail')) return;
    const sku = document.getElementById('view-sku-detail');
    if (!sku) return;
    const section = document.createElement('section');
    section.className = 'view';
    section.id = 'view-partner-detail';
    section.innerHTML =
        '<div class="panel-head">'
      + '<button class="back-btn" onclick="portalPartnerBack()">' + T('portal.text.back_to_list') + '</button>'
      + '<div class="actions" id="partnerActions"></div></div>'
      + '<div class="detail-head"><h2 id="partnerTitle" style="margin:0 0 16px;"></h2>'
      + '<div class="admin-subtabs" id="partnerTabs" style="margin-bottom:0;"></div></div>'
      + '<div id="partnerBody" style="margin-top:16px;"></div>';
    sku.parentNode.insertBefore(section, sku.nextSibling);
  }

  function value(field){
    if (Object.prototype.hasOwnProperty.call(state.draft, field.col)) return state.draft[field.col];
    return (state.row || {})[camel(field.col)];
  }

  window.portalPartnerField = function(el){
    state.draft[el.getAttribute('data-col')] = el.value === '' ? null : el.value;
  };

  function cardHtml(){
    const editing = state.mode !== 'view';
    const rows = FIELDS.map(f => {
      const v = value(f);
      const body = editing
        ? '<input class="kv-input" type="text" value="' + esc(v === null || v === undefined ? '' : v)
          + '" data-col="' + f.col + '" oninput="portalPartnerField(this)"/>'
        : esc(dash(v));
      return '<div class="kv"><label>' + esc(f.label) + (f.required && editing ? ' *' : '')
        + '</label><div' + (editing ? ' style="min-width:320px;"' : '') + '>' + body + '</div></div>';
    }).join('');
    const hint = editing
      ? '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
        + 'Поля со звёздочкой обязательны.</div>'
      : '';
    return '<div class="kv-grid">' + rows + '</div>' + hint;
  }

  function addressesHtml(){
    if (!state.id){
      return '<div style="color:var(--muted); font-size:13px;">'
        + 'Сначала сохраните карточку — адреса задаются уже для грузополучателя</div>';
    }
    const canEdit = mayEdit() && state.mode === 'view';
    const rows = state.addresses;
    const table = rows.length
      ? '<div class="table-card" style="margin-top:4px;"><table><thead><tr>'
        + '<th>' + T('partners.text.delivery_address') + '</th><th class="num">' + T('partners.text.order') + '</th><th>' + T('partners.text.default') + '</th>'
        + (canEdit ? '<th></th>' : '') + '</tr></thead><tbody>'
        + rows.map(a => '<tr><td>' + esc(a.address) + '</td><td class="num">'
            + dash(a.sortOrder) + '</td><td>'
            + (a.isDefault ? '★ основной'
                : (canEdit ? '<button class="btn ghost" onclick="portalAddressDefault(\'' + a.addressId
                    + '\')">' + T('partners.text.set_as_default') + '</button>' : '—'))
            + '</td>'
            + (canEdit ? '<td style="white-space:nowrap;">'
                + '<button class="btn ghost" onclick="portalAddressEdit(\'' + a.addressId + '\')">' + T('portal.text.edit2') + '</button> '
                + '<button class="btn ghost" onclick="portalAddressDelete(\'' + a.addressId + '\')">' + T('common.button.delete') + '</button></td>' : '')
            + '</tr>').join('')
        + '</tbody></table></div>'
      : '<div style="color:var(--muted); font-size:13px;">' + T('partners.text.no_delivery_addresses_defined') + '</div>';

    let form = '';
    if (state.addressForm){
      const f = state.addressForm.values;
      form = '<div class="table-card" style="margin-top:12px; padding:12px;">'
        + '<div class="section-title">'
        + (state.addressForm.addressId ? T('partners.text.edit_address') : T('partners.text.new_address')) + '</div>'
        + '<div class="kv-grid">'
        + '<div class="kv"><label>' + T('partners.text.delivery_address2') + '</label><div><input class="kv-input" type="text"'
          + ' style="min-width:420px;" value="' + esc(f.address || '') + '" data-addr="address"'
          + ' oninput="portalAddressField(this)"/></div></div>'
        + '<div class="kv"><label>' + T('partners.text.order') + '</label><div><input class="kv-input" type="number"'
          + ' value="' + esc(f.sortOrder === null || f.sortOrder === undefined ? '' : f.sortOrder)
          + '" data-addr="sortOrder" oninput="portalAddressField(this)"/></div></div>'
        + '</div><div style="margin-top:12px;">'
        + '<button class="btn primary" onclick="portalAddressSave()">' + T('partners.text.save_address') + '</button> '
        + '<button class="btn ghost" onclick="portalAddressCancel()">' + T('common.button.cancel') + '</button></div></div>';
    } else if (canEdit){
      form = '<div style="margin-top:12px;">'
        + '<button class="btn" onclick="portalAddressAdd()">' + T('partners.text.add_address') + '</button></div>';
    }
    return table + form;
  }

  window.portalAddressField = function(el){
    state.addressForm.values[el.getAttribute('data-addr')] = el.value === '' ? null : el.value;
  };

  function actionsHtml(){
    if (state.mode !== 'view'){
      return '<button class="btn primary" onclick="portalPartnerSave()">' + T('portal.text.save') + '</button>'
        + '<button class="btn ghost" onclick="portalPartnerCancel()">' + T('common.button.cancel') + '</button>';
    }
    if (!mayEdit() || !state.id) return '';
    const archived = state.row && state.row.isActive === false;
    return '<button class="btn primary" onclick="portalPartnerEdit()">' + T('sku.text.edit') + '</button>'
      + '<button class="btn" onclick="portalPartnerArchive(' + (archived ? 'true' : 'false') + ')">'
      + (archived ? T('sku.text.restore_from_archive') : T('portal.text.archive')) + '</button>'
      + '<button class="btn ghost" onclick="portalPartnerDelete()">' + T('common.button.delete') + '</button>';
  }

  function render(){
    ensureView();
    const kind = KINDS[state.kind];
    const name = state.mode === 'create' ? kind.newTitle : (state.row && state.row.name) || kind.title;
    const archived = state.row && state.row.isActive === false;
    document.getElementById('partnerTitle').innerHTML =
        esc(name) + (archived ? ' <span class="count">' + T('partners.text.archived') + '</span>' : '');
    document.getElementById('partnerActions').innerHTML = actionsHtml();

    const tabs = [{id: 'card', title: T('partners.title.card')}];
    if (kind.addresses) tabs.push({id: 'addresses', title: T('partners.title.delivery_addresses')});
    if (!tabs.some(t => t.id === state.tab)) state.tab = 'card';
    document.getElementById('partnerTabs').innerHTML = tabs.map(t =>
        '<button class="admin-subtab-btn ' + (t.id === state.tab ? 'active' : '') + '"'
        + ' onclick="portalPartnerTab(\'' + t.id + '\')">' + t.title + '</button>').join('');

    document.getElementById('partnerBody').innerHTML =
        state.tab === 'addresses' ? addressesHtml() : cardHtml();
    showView('partner-detail');
  }

  window.portalPartnerTab = function(tab){
    state.tab = tab;
    render();
  };

  window.portalPartnerBack = function(){
    showTab('references');
    showReferencesSub(state.kind);
  };

  /* ================= Действия ================= */

  window.portalOpenPartner = async function(kindName, id){
    try {
      state.kind = kindName;
      state.mode = 'view';
      state.tab = 'card';
      state.draft = {};
      state.addressForm = null;
      const card = await api('GET', '/api/' + kindName + '/' + id);
      state.id = id;
      state.row = card;
      state.addresses = card.addresses || [];
      render();
    } catch (e){
      say('Не удалось открыть карточку: ' + e.message);
    }
  };

  window.portalPartnerCreate = function(kindName){
    state.kind = kindName;
    state.mode = 'create';
    state.tab = 'card';
    state.id = null;
    state.row = {};
    state.draft = {};
    state.addresses = [];
    state.addressForm = null;
    render();
  };

  window.portalPartnerEdit = function(){
    state.mode = 'edit';
    state.draft = {};
    state.tab = 'card';
    render();
  };

  window.portalPartnerCancel = function(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      portalPartnerBack();
      return;
    }
    state.mode = 'view';
    render();
  };

  window.portalPartnerSave = async function(){
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      render();
      return;
    }
    try {
      const card = creating
        ? await api('POST', '/api/' + state.kind, state.draft)
        : await api('PUT', '/api/' + state.kind + '/' + state.id, state.draft);
      state.mode = 'view';
      state.id = card[KINDS[state.kind].key];
      state.row = card;
      state.addresses = card.addresses || [];
      state.draft = {};
      await reload(state.kind);
      render();
      say(creating ? T('portal.text.record_created') : T('sku.text.changes_saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalPartnerArchive = async function(archived){
    try {
      const card = await api('POST', '/api/' + state.kind + '/' + state.id + '/archive',
          {active: archived});
      state.row = card;
      state.addresses = card.addresses || state.addresses;
      await reload(state.kind);
      render();
      say(archived ? T('partners.text.partner_restored') : T('partners.text.partner_archived'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalPartnerDelete = async function(){
    if (!window.confirm('Удалить «' + (state.row.name || '') + '» без возможности восстановления?\n'
        + T('partners.text.if_the_partner_appears_in_document'))) return;
    try {
      await api('DELETE', '/api/' + state.kind + '/' + state.id);
      await reload(state.kind);
      say(T('partners.text.record_deleted'));
      portalPartnerBack();
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Адреса ================= */

  window.portalAddressAdd = function(){
    state.addressForm = {addressId: null, values: {}};
    render();
  };

  window.portalAddressEdit = function(id){
    const a = state.addresses.find(x => x.addressId === id);
    if (!a) return;
    state.addressForm = {addressId: id, values: {address: a.address, sortOrder: a.sortOrder}};
    render();
  };

  window.portalAddressCancel = function(){
    state.addressForm = null;
    render();
  };

  window.portalAddressSave = async function(){
    const form = state.addressForm;
    try {
      state.addresses = form.addressId
        ? await api('PUT', '/api/addresses/' + form.addressId, form.values)
        : await api('POST', '/api/consignees/' + state.id + '/addresses', form.values);
      state.addressForm = null;
      await reload('consignees');
      render();
      say(T('partners.text.address_saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalAddressDefault = async function(id){
    try {
      state.addresses = await api('POST', '/api/addresses/' + id + '/default');
      await reload('consignees');
      render();
    } catch (e){
      say(e.message);
    }
  };

  window.portalAddressDelete = async function(id){
    const a = state.addresses.find(x => x.addressId === id);
    if (!window.confirm('Удалить адрес «' + (a ? a.address : id) + '»?')) return;
    try {
      state.addresses = await api('DELETE', '/api/addresses/' + id);
      await reload('consignees');
      render();
      say(T('partners.message.address_deleted'));
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Списки ================= */

  async function reload(kindName){
    try {
      PORTAL.catalog[kindName + 'All'] = await api('GET', '/api/' + kindName + '?withArchived=true');
      PORTAL.catalogError[kindName] = null;
    } catch (e){
      PORTAL.catalog[kindName + 'All'] = PORTAL.catalog[kindName + 'All'] || [];
      PORTAL.catalogError[kindName] = e.message;
    }
    renderList(kindName);
  }

  function visible(kindName){
    const show = document.getElementById(kindName + 'ShowArchived')?.checked;
    return (PORTAL.catalog[kindName + 'All'] || []).filter(r => show || r.isActive !== false);
  }

  function renderList(kindName){
    const kind = KINDS[kindName];
    const body = document.getElementById(kind.body);
    if (!body) return;
    let rows = visible(kindName);
    PORTAL.catalog[kindName] = rows;

    if (typeof fcDirSortArray === 'function'){
      rows = fcDirSortArray(kindName, rows.slice(), r => r[dirSortState[kindName].col] ?? '');
    }
    const counter = document.getElementById(kind.count);
    if (counter) counter.textContent = '(' + rows.length + ')';

    const cell = v => '<td>' + esc(dash(v)) + '</td>';
    body.innerHTML = rows.length
      ? rows.map(r => {
          const id = r[kind.key];
          const archived = r.isActive === false
            ? ' <span class="count">' + T('portal.text.archived2') + '</span>' : '';
          return '<tr style="cursor:pointer;" onclick="portalOpenPartner(\'' + kindName + '\',\''
            + id + '\')"><td><strong>' + esc(dash(r.code)) + '</strong>' + archived + '</td>'
            + cell(r.name)
            + (kind.addresses
                ? '<td>' + esc(dash(r.defaultAddress))
                  + (r.addressCount > 1 ? ' <span class="count">+' + (r.addressCount - 1) + '</span>' : '')
                  + '</td>'
                : '')
            + cell(r.bin) + cell(r.legalAddress) + cell(r.contactName)
            + cell(r.contactPhone) + cell(r.contactEmail) + '</tr>';
        }).join('')
      : '<tr><td colspan="' + kind.columns + '" style="color:var(--muted);">'
        + (PORTAL.catalogError[kindName] ? T('partners.text.data_unavailable') : T('partners.text.directory_is_empty')) + '</td></tr>';
  }

  window.portalPartnerArchiveFilter = renderList;

  /** Кнопка создания и флажок архива в шапке списка. */
  function decorateList(kindName){
    const head = document.querySelector('#ref-sub-' + kindName + ' .panel-head');
    if (!head || document.getElementById(kindName + 'CreateBtn')) return;
    let actions = head.querySelector('.actions');
    if (!actions){
      actions = document.createElement('div');
      actions.className = 'actions';
      head.appendChild(actions);
    }
    const label = kindName === 'suppliers' ? T('partners.button.create_supplier') : T('partners.button.create_consignee');
    actions.insertAdjacentHTML('afterbegin',
        '<label style="font-size:13px; color:var(--muted); margin-right:12px;">'
      + '<input type="checkbox" id="' + kindName + 'ShowArchived"'
      + ' onchange="portalPartnerArchiveFilter(\'' + kindName + '\')"/>' + ' ' + T('portal.text.show_archived') + '</label>'
      + '<button class="btn primary" id="' + kindName + 'CreateBtn"'
      + ' onclick="portalPartnerCreate(\'' + kindName + '\')">' + label + '</button>');
  }

  function install(){
    window.renderSuppliers = () => renderList('suppliers');
    window.renderConsignees = () => renderList('consignees');

    PORTAL.onLogin(() => {
      ensureView();
      if (mayEdit()){
        decorateList('suppliers');
        decorateList('consignees');
      }
      reload('suppliers').catch(() => {});
      reload('consignees').catch(() => {});
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
