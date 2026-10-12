
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const isAdmin = () => ['admin', 'developer'].includes(PORTAL.session && PORTAL.session.roleCode);
  // Разработчик смотрит, но не правит: раздел ему открыт для разбора,
  // а не для ведения справочников.
  const mayEdit = () => PORTAL.session && PORTAL.session.roleCode === 'admin';

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

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

  /* ================= Два справочника с карточкой ================= */

  const DIRS = {
    warehousesDir: {
      titleKey: 'sku.label.warehouse', newTitleKey: 'portal.text.new_warehouse',
      list: '/api/admin/warehouse-directory', base: '/api/admin/warehouses',
      key: 'warehouseId', body: 'warehousesDirBody', count: 'warehousesDirCount',
      // идентификатор склада = его код в WMS, задаётся один раз при создании
      idField: {col: 'warehouse_id', readCol: 'warehouseId', labelKey: 'portal.label.wms_identifier'},
      fields: [
        {col: 'code', labelKey: 'references.column.code', required: true},
        {col: 'name', labelKey: 'portal.label.warehouse_name', required: true},
        {col: 'address', labelKey: 'portal.label.address'},
        {col: 'area_m2', readCol: 'areaM2',
         labelKey: 'portal.label.warehouse_area', num: true},
        {col: 'work_hours', readCol: 'workHours', labelKey: 'portal.label.work_hours'},
        {col: 'responsible', labelKey: 'portal.label.responsible_person'},
        {col: 'phone', labelKey: 'partners.label.phone'},
        {col: 'email', labelKey: 'common.column.email'}
      ],
      columns: ['code', 'name', 'address', 'responsible', 'phone', 'email']
    },
    depositorsDir: {
      titleKey: 'admin.column.depositor', newTitleKey: 'portal.text.new_depositor',
      list: '/api/admin/depositor-directory', base: '/api/admin/depositor-directory',
      key: 'depositorId', body: 'depositorsDirBody', count: 'depositorsDirCount',
      // поклажедателя портал принимает анкетой целиком, а не набором колонок
      asRecord: true,
      fields: [
        {col: 'code', labelKey: 'references.column.code', required: true},
        {col: 'orgName', labelKey: 'portal.text.company_name', required: true},
        {col: 'bin', labelKey: 'portal.text.company_bin'},
        {col: 'legalAddress', labelKey: 'portal.text.legal_address'},
        {col: 'phone', labelKey: 'partners.label.phone'},
        {col: 'email', labelKey: 'common.column.email'},
        {col: 'contractNumber', labelKey: 'portal.text.contract_number'},
        {col: 'contractValidUntil', labelKey: 'portal.label.contract_valid_until', date: true}
      ],
      columns: ['code', 'orgName', 'bin', 'legalAddress', 'phone', 'email',
                'contractNumber', 'contractValidUntil']
    },
    operatorsDir: {
      titleKey: 'admin.title.operator_organization', newTitleKey: 'admin.title.new_organization',
      list: '/api/admin/operator-directory', base: '/api/admin/operator-directory',
      key: 'operatorId', body: 'operatorsDirBody', count: 'operatorsDirCount',
      asRecord: true,
      fields: [
        {col: 'code', labelKey: 'admin.column.organization_code', required: true},
        {col: 'name', labelKey: 'portal.text.company_name', required: true},
        {col: 'bin', labelKey: 'portal.text.company_bin'},
        {col: 'legalAddress', labelKey: 'portal.text.legal_address'},
        {col: 'bankName', labelKey: 'admin.column.bank'},
        {col: 'bankAccount', labelKey: 'admin.column.bank_account'},
        {col: 'phone', labelKey: 'partners.label.phone'},
        {col: 'email', labelKey: 'common.column.email'},
        // страницы портала — абзацы текста, им нужна не строка ввода
        {col: 'aboutContent', labelKey: 'admin.label.about_page', long: true},
        {col: 'contactsContent', labelKey: 'admin.label.contacts_page', long: true}
      ],
      columns: ['code', 'name', 'bin', 'bankName', 'bankAccount', 'phone', 'email']
    }
  };

  const state = {dir: null, mode: 'view', id: null, row: null, draft: {}};
  PORTAL.portalDir = state;

  function ensureView(){
    if (document.getElementById('view-portaldir-detail')) return;
    const anchor = document.getElementById('view-sku-detail');
    if (!anchor) return;
    const section = document.createElement('section');
    section.className = 'view';
    section.id = 'view-portaldir-detail';
    section.innerHTML =
        '<div class="panel-head">'
      + '<button class="back-btn" onclick="portalDirBack()">' + T('portal.text.back_to_list') + '</button>'
      + '<div class="actions" id="portalDirActions"></div></div>'
      + '<div class="detail-head"><h2 id="portalDirTitle" style="margin:0 0 16px;"></h2></div>'
      + '<div id="portalDirBody" style="margin-top:16px;"></div>';
    anchor.parentNode.insertBefore(section, anchor.nextSibling);
  }

  function value(field){
    if (Object.prototype.hasOwnProperty.call(state.draft, field.col)) return state.draft[field.col];
    // Отправляем поле под именем колонки базы, а приходит оно в camelCase:
    // readCol говорит, где его искать в ответе.
    return (state.row || {})[field.readCol || field.col];
  }

  window.portalDirField = function(el){
    state.draft[el.getAttribute('data-col')] = el.value === '' ? null : el.value;
  };

  function fieldHtml(field, readOnly){
    const v = value(field);
    if (readOnly){
      if (field.long){
        return '<div class="kv" style="grid-column:1/-1;"><label>' + PORTAL.t(field.labelKey)
          + '</label><div style="white-space:pre-wrap;">' + esc(dash(v)) + '</div></div>';
      }
      return '<div class="kv"><label>' + PORTAL.t(field.labelKey) + '</label><div>'
        + esc(field.date ? ruDate(v) : dash(v)) + '</div></div>';
    }
    // Содержимое страниц «О нас» и «Контакты» — это абзацы текста,
    // в одну строку ввода они не помещаются.
    if (field.long){
      return '<div class="kv" style="grid-column:1/-1;"><label>' + PORTAL.t(field.labelKey)
        + '</label><div><textarea class="kv-input" rows="6"'
        + ' style="width:100%; resize:vertical; font-family:inherit;"'
        + ' data-col="' + field.col + '" oninput="portalDirField(this)">'
        + esc(v === null || v === undefined ? '' : v) + '</textarea></div></div>';
    }
    return '<div class="kv"><label>' + PORTAL.t(field.labelKey) + (field.required ? ' *' : '')
      + '</label><div style="min-width:320px;"><input class="kv-input" type="'
      + (field.date ? 'date' : 'text') + '" value="'
      + esc(v === null || v === undefined ? '' : (field.date ? String(v).slice(0, 10) : v))
      + '" data-col="' + field.col + '" oninput="portalDirField(this)"/></div></div>';
  }

  function render(){
    ensureView();
    const dir = DIRS[state.dir];
    const editing = state.mode !== 'view';
    const archived = state.row && state.row.isActive === false;
    document.getElementById('portalDirTitle').innerHTML =
        esc(state.mode === 'create' ? T(dir.newTitleKey)
            : (state.row && (state.row.name || state.row.orgName)) || T(dir.titleKey))
      + (archived ? ' <span class="count">' + T('partners.text.archived') + '</span>' : '');

    let actions = '';
    if (editing){
      actions = '<button class="btn primary" onclick="portalDirSave()">' + T('portal.text.save') + '</button>'
        + '<button class="btn ghost" onclick="portalDirCancel()">' + T('common.button.cancel') + '</button>';
    } else if (mayEdit() && state.id){
      actions = '<button class="btn primary" onclick="portalDirEdit()">' + T('sku.text.edit') + '</button>'
        + '<button class="btn" onclick="portalDirArchive(' + (archived ? 'true' : 'false') + ')">'
        + (archived ? T('sku.text.restore_from_archive') : T('portal.text.archive')) + '</button>'
        + '<button class="btn ghost" onclick="portalDirDelete()">' + T('common.button.delete') + '</button>';
    }
    document.getElementById('portalDirActions').innerHTML = actions;

    const fields = [];
    if (dir.idField){
      // идентификатор задаётся только при создании: по нему приходят
      // документы обмена, и менять его задним числом нельзя
      fields.push(Object.assign({}, dir.idField, {required: true}));
    }
    dir.fields.forEach(f => fields.push(f));

    let html = '<div class="kv-grid">' + fields.map(f =>
        fieldHtml(f, !editing || (dir.idField && f.col === dir.idField.col && state.mode !== 'create'))
      ).join('') + '</div>';
    if (state.row && state.row.userCount !== undefined && !editing){
      html += '<div style="color:var(--muted); font-size:13px; margin-top:12px;">'
        + 'Пользователей с доступом: ' + state.row.userCount + '</div>';
    }
    if (editing){
      html += '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
        + 'Поля со звёздочкой обязательны.</div>';
    }
    document.getElementById('portalDirBody').innerHTML = html;
    showView('portaldir-detail');
  }

  window.portalDirBack = function(){
    // Организации оператора лежат в администрировании, остальные
    // справочники — в разделе «Справочники». Возврат должен попадать
    // туда, откуда человек пришёл.
    if (state.dir === 'operatorsDir'){
      showTab('admin');
      if (typeof showAdminSub === 'function') showAdminSub('operator');
      return;
    }
    showTab('references');
    showReferencesSub(state.dir);
  };

  window.portalOpenPortalDir = async function(dirName, id){
    try {
      state.dir = dirName;
      state.mode = 'view';
      state.draft = {};
      state.row = await api('GET', DIRS[dirName].base + '/' + encodeURIComponent(id));
      state.id = id;
      render();
    } catch (e){
      say('Не удалось открыть карточку: ' + e.message);
    }
  };

  window.portalDirCreate = function(dirName){
    state.dir = dirName;
    state.mode = 'create';
    state.id = null;
    state.row = {};
    state.draft = {};
    render();
  };

  window.portalDirEdit = function(){
    state.mode = 'edit';
    state.draft = {};
    render();
  };

  window.portalDirCancel = function(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      portalDirBack();
      return;
    }
    state.mode = 'view';
    render();
  };

  /**
   * Анкета уходит целиком: сервер принимает запись, а не набор полей.
   *
   * Поля берём у открытого справочника, а не у поклажедателя: раньше
   * список был прибит к DIRS.depositorsDir, и у организации оператора
   * уезжали чужие поля — страницы «О нас» и «Контакты» не сохранялись,
   * потому что их в том списке просто нет.
   */
  function recordBody(){
    const out = {};
    (DIRS[state.dir].fields || []).forEach(f => {
      const v = Object.prototype.hasOwnProperty.call(state.draft, f.col)
        ? state.draft[f.col] : (state.row || {})[f.readCol || f.col];
      out[f.col] = v === undefined ? null : v;
    });
    return out;
  }

  window.portalDirSave = async function(){
    const dir = DIRS[state.dir];
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      render();
      return;
    }
    try {
      const body = dir.asRecord ? recordBody() : state.draft;
      const card = creating
        ? await api('POST', dir.base, body)
        : await api('PUT', dir.base + '/' + encodeURIComponent(state.id), body);
      state.mode = 'view';
      state.row = card;
      state.id = card[dir.key];
      state.draft = {};
      await reload(state.dir);
      render();
      say(creating ? T('portal.text.record_created') : T('sku.text.changes_saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalDirArchive = async function(archived){
    try {
      state.row = await api('POST', DIRS[state.dir].base + '/'
          + encodeURIComponent(state.id) + '/archive', {active: archived});
      await reload(state.dir);
      render();
      say(archived ? T('portal.text.restored') : T('portal.text.archived3'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalDirDelete = async function(){
    const name = (state.row && (state.row.name || state.row.orgName)) || '';
    if (!window.confirm('Удалить «' + name + '» без возможности восстановления?\n'
        + T('portal.text.if_the_record_is_used_anywhere_the'))) return;
    try {
      await api('DELETE', DIRS[state.dir].base + '/' + encodeURIComponent(state.id));
      await reload(state.dir);
      say(T('portal.message.record_deleted'));
      portalDirBack();
    } catch (e){
      say(e.message);
    }
  };

  /* ---------------- Списки ---------------- */

  async function reload(dirName){
    const dir = DIRS[dirName];
    try {
      PORTAL.catalog[dirName] = await api('GET', dir.list);
      PORTAL.catalogError[dirName] = null;
    } catch (e){
      PORTAL.catalog[dirName] = PORTAL.catalog[dirName] || [];
      PORTAL.catalogError[dirName] = e.message;
    }
    renderDir(dirName);
  }

  function renderDir(dirName){
    const dir = DIRS[dirName];
    const body = document.getElementById(dir.body);
    if (!body) return;
    const show = document.getElementById(dirName + 'ShowArchived')?.checked;
    let rows = (PORTAL.catalog[dirName] || []).filter(r => show || r.isActive !== false);
    if (typeof fcDirSortArray === 'function'){
      rows = fcDirSortArray(dirName, rows.slice(), r => r[dirSortState[dirName].col] ?? '');
    }
    const counter = document.getElementById(dir.count);
    if (counter) counter.textContent = '(' + rows.length + ')';

    body.innerHTML = rows.length
      ? rows.map(r => '<tr style="cursor:pointer;" onclick="portalOpenPortalDir(\'' + dirName
          + '\',\'' + esc(r[dir.key]) + '\')">'
          + dir.columns.map((c, i) => {
              const v = c === 'contractValidUntil' ? ruDate(r[c]) : dash(r[c]);
              const mark = (i === 0 && r.isActive === false)
                ? ' <span class="count">' + T('portal.text.archived2') + '</span>' : '';
              return '<td>' + (i === 0 ? '<strong>' + esc(v) + '</strong>' : esc(v)) + mark + '</td>';
            }).join('')
          + '</tr>').join('')
      : '<tr><td colspan="' + dir.columns.length + '" style="color:var(--muted);">'
        + (PORTAL.catalogError[dirName] ? T('partners.text.data_unavailable') : T('partners.text.directory_is_empty')) + '</td></tr>';
  }

  window.portalDirArchiveFilter = renderDir;

  // Принимает КЛЮЧ текста кнопки, а не готовую подпись: подпись,
  // подставленная один раз, осталась бы на языке момента отрисовки.
  function decorateList(dirName, labelKey){
    const head = document.querySelector('#ref-sub-' + dirName + ' .panel-head');
    if (!head || document.getElementById(dirName + 'CreateBtn')) return;
    let actions = head.querySelector('.actions');
    if (!actions){
      actions = document.createElement('div');
      actions.className = 'actions';
      head.appendChild(actions);
    }
    actions.insertAdjacentHTML('afterbegin',
        '<label style="font-size:13px; color:var(--muted); margin-right:12px;">'
      + '<input type="checkbox" id="' + dirName + 'ShowArchived"'
      + ' onchange="portalDirArchiveFilter(\'' + dirName + '\')"/> '
      + PORTAL.t('portal.text.show_archived') + '</label>'
      + '<button class="btn primary" id="' + dirName + 'CreateBtn"'
      + ' data-i18n="' + labelKey + '"'
      + ' onclick="portalDirCreate(\'' + dirName + '\')">' + esc(T(labelKey)) + '</button>');
  }

  /* ================= Типы заказов и документов ================= */

  // Это короткие перечисления, карточка им не нужна: правка идёт прямо
  // в строке списка — так быстрее и видно сразу все значения.
  const ENUMS = {
    orderTypes: {path: 'order-types', body: 'orderTypesBody', titleKey: 'portal.title.order_type'},
    docTypes: {path: 'receipt-doc-types', body: 'docTypesBody', titleKey: 'portal.title.document_type'}
  };
  const enumState = {rows: {}, form: null};
  PORTAL.portalEnums = enumState;

  async function reloadEnum(name){
    const e = ENUMS[name];
    try {
      enumState.rows[name] = await api('GET', '/api/admin/' + e.path);
    } catch (err){
      enumState.rows[name] = [];
    }
    renderEnum(name);
  }

  window.portalEnumField = function(el){
    enumState.form.values[el.getAttribute('data-enum')] = el.value === '' ? null : el.value;
  };

  function renderEnum(name){
    const body = document.getElementById(ENUMS[name].body);
    if (!body) return;
    const head = body.parentNode.querySelector('thead tr');
    const canEdit = mayEdit();
    if (head){
      head.innerHTML = '<th>' + T('references.column.code') + '</th><th>' + T('references.column.name') + '</th><th class="num">' + T('partners.text.order') + '</th>'
        + '<th>' + T('portal.text.in_use') + '</th>' + (canEdit ? '<th></th>' : '');
    }
    const rows = enumState.rows[name] || [];
    const form = enumState.form && enumState.form.name === name ? enumState.form : null;

    const line = r => {
      if (form && form.id === r.id){
        return '<tr>' + inputCell('code', form.values.code) + inputCell('name', form.values.name)
          + inputCell('sortOrder', form.values.sortOrder, true)
          + '<td>' + (r.isActive ? T('portal.text.yes') : 'нет') + '</td>'
          + '<td style="white-space:nowrap;">'
          + '<button class="btn primary" onclick="portalEnumSave()">' + T('portal.text.save') + '</button> '
          + '<button class="btn ghost" onclick="portalEnumCancel()">' + T('common.button.cancel') + '</button></td></tr>';
      }
      return '<tr><td><strong>' + esc(r.code) + '</strong></td><td>' + esc(r.name)
        + '</td><td class="num">' + dash(r.sortOrder) + '</td>'
        + '<td>' + (r.isActive ? T('portal.text.yes') : '<span class="count">' + T('portal.text.disabled') + '</span>') + '</td>'
        + (canEdit ? '<td style="white-space:nowrap;">'
            + '<button class="btn ghost" onclick="portalEnumEdit(\'' + name + '\',' + r.id + ')">' + T('portal.text.edit2') + '</button> '
            + '<button class="btn ghost" onclick="portalEnumToggle(\'' + name + '\',' + r.id + ')">'
            + (r.isActive ? T('portal.text.disable') : T('portal.text.enable')) + '</button> '
            + '<button class="btn ghost" onclick="portalEnumDelete(\'' + name + '\',' + r.id + ')">' + T('common.button.delete') + '</button></td>' : '')
        + '</tr>';
    };

    let html = rows.map(line).join('');
    if (form && form.id === null){
      html += '<tr>' + inputCell('code', form.values.code) + inputCell('name', form.values.name)
        + inputCell('sortOrder', form.values.sortOrder, true) + '<td>' + T('portal.text.yes') + '</td>'
        + '<td style="white-space:nowrap;">'
        + '<button class="btn primary" onclick="portalEnumSave()">' + T('portal.text.save') + '</button> '
        + '<button class="btn ghost" onclick="portalEnumCancel()">' + T('common.button.cancel') + '</button></td></tr>';
    }
    body.innerHTML = html || '<tr><td colspan="5" style="color:var(--muted);">' + T('partners.text.directory_is_empty') + '</td></tr>';

    if (canEdit){
      const panel = document.querySelector('#ref-sub-' + name + ' .panel-head');
      if (panel && !document.getElementById(name + 'CreateBtn')){
        let actions = panel.querySelector('.actions');
        if (!actions){
          actions = document.createElement('div');
          actions.className = 'actions';
          panel.appendChild(actions);
        }
        actions.insertAdjacentHTML('afterbegin',
            '<button class="btn primary" id="' + name + 'CreateBtn"'
            + ' data-i18n="admin.button.add" onclick="portalEnumAdd(\''
            + name + '\')">' + esc(T('admin.button.add')) + '</button>');
      }
    }
  }

  function inputCell(col, v, num){
    return '<td><input class="kv-input" type="' + (num ? 'number' : 'text') + '" value="'
      + esc(v === null || v === undefined ? '' : v) + '" data-enum="' + col
      + '" oninput="portalEnumField(this)"/></td>';
  }

  window.portalEnumAdd = function(name){
    enumState.form = {name: name, id: null, values: {}};
    renderEnum(name);
  };

  window.portalEnumEdit = function(name, id){
    const r = (enumState.rows[name] || []).find(x => x.id === id);
    if (!r) return;
    enumState.form = {name: name, id: id,
      values: {code: r.code, name: r.name, sortOrder: r.sortOrder}};
    renderEnum(name);
  };

  window.portalEnumCancel = function(){
    const name = enumState.form.name;
    enumState.form = null;
    renderEnum(name);
  };

  window.portalEnumSave = async function(){
    const form = enumState.form;
    const e = ENUMS[form.name];
    const body = {code: form.values.code, name: form.values.name,
      sortOrder: form.values.sortOrder === null || form.values.sortOrder === undefined
        ? null : Number(form.values.sortOrder)};
    try {
      enumState.rows[form.name] = form.id === null
        ? await api('POST', '/api/admin/' + e.path, body)
        : await api('PUT', '/api/admin/' + e.path + '/' + form.id,
            Object.assign({}, body, {isActive: true}));
      enumState.form = null;
      renderEnum(form.name);
      say(T('portal.text.saved'));
    } catch (err){
      say(err.message);
    }
  };

  window.portalEnumToggle = async function(name, id){
    const r = (enumState.rows[name] || []).find(x => x.id === id);
    try {
      enumState.rows[name] = await api('PUT', '/api/admin/' + ENUMS[name].path + '/' + id,
          {code: r.code, name: r.name, sortOrder: r.sortOrder, isActive: !r.isActive});
      renderEnum(name);
    } catch (err){
      say(err.message);
    }
  };

  window.portalEnumDelete = async function(name, id){
    const r = (enumState.rows[name] || []).find(x => x.id === id);
    if (!window.confirm('Удалить «' + (r ? r.name : id) + '»?')) return;
    try {
      enumState.rows[name] = await api('DELETE', '/api/admin/' + ENUMS[name].path + '/' + id);
      renderEnum(name);
      say(T('portal.text.deleted'));
    } catch (err){
      say(err.message);
    }
  };

  /* ========= Организации оператора в администрировании =========
     В разметке демо такой подвкладки нет: таблица operator создавалась
     под решение Р2, а экрана у неё не было. Поэтому кнопку и панель
     добавляем сами, рядом с остальными подвкладками администрирования.
  ============================================================= */

  function ensureOperatorSub(){
    const tabs = document.querySelector('#view-admin .admin-subtabs');
    if (!tabs || document.getElementById('admin-sub-operator')) return;

    const btn = document.createElement('button');
    btn.className = 'admin-subtab-btn';
    btn.setAttribute('data-sub', 'operator');
    btn.setAttribute('data-i18n', 'admin.title.operator_organizations');
    btn.textContent = T('admin.title.operator_organizations');
    btn.onclick = () => showAdminSub('operator');
    tabs.appendChild(btn);

    const pane = document.createElement('div');
    pane.className = 'admin-sub';
    pane.id = 'admin-sub-operator';
    pane.innerHTML =
        '<div class="panel-head" style="margin-top:0;">'
      + '<div class="section-title" style="margin:0;" data-i18n="admin.title.operator_organizations">'
      + '' + T('admin.title.operator_organizations') + '</div>'
      + '<div class="actions" id="operatorsDirActions"></div></div>'
      + '<div class="hint" data-i18n="admin.hint.operator_directory"'
      + ' style="margin:-4px 0 12px;">'
      + 'Реквизиты организаций оператора и содержимое страниц «О нас» и «Контакты». '
      + 'Логотип появится вместе с файловым хранилищем.</div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th data-i18n="admin.column.organization_code">' + T('admin.column.organization_code') + '</th>'
      + '<th data-i18n="admin.column.organization_name">' + T('portal.text.company_name') + '</th>'
      + '<th data-i18n="references.column.company_bin">' + T('portal.text.company_bin') + '</th>'
      + '<th data-i18n="admin.column.bank">' + T('admin.column.bank') + '</th>'
      + '<th data-i18n="admin.column.bank_account">' + T('admin.column.bank_account') + '</th>'
      + '<th data-i18n="portal.text.phone">' + T('partners.label.phone') + '</th>'
      + '<th data-i18n="common.column.email">' + T('common.column.email') + '</th>'
      + '</tr></thead><tbody id="operatorsDirBody"></tbody></table></div>'
      + '<div class="count" id="operatorsDirCount" style="margin-top:8px;"></div>';
    const host = document.getElementById('view-admin');
    if (host) host.appendChild(pane);

    if (mayEdit() && !document.getElementById('operatorsDirCreateBtn')){
      const create = document.createElement('button');
      create.className = 'btn primary';
      create.id = 'operatorsDirCreateBtn';
      create.type = 'button';
      create.setAttribute('data-i18n', 'admin.button.create_organization');
      create.textContent = T('admin.button.create_organization');
      create.onclick = () => portalDirCreate('operatorsDir');
      document.getElementById('operatorsDirActions').appendChild(create);
    }
  }

  /* ================= Установка ================= */

  function install(){
    window.renderWarehousesDir = () => renderDir('warehousesDir');
    window.renderDepositorsDir = () => renderDir('depositorsDir');
    window.renderOperatorsDir = () => renderDir('operatorsDir');
    window.renderOrderTypes = () => renderEnum('orderTypes');
    window.renderDocTypes = () => renderEnum('docTypes');

    // onSession, а не onLogin: склады, поклажедатели, организации
    // оператора и типы документов — справочники ВСЕГО портала,
    // к выбранному клиенту отношения не имеют. На onLogin они не
    // грузились до выбора поклажедателя, и администратор, которому
    // выбор обязателен, видел пустые справочники.
    PORTAL.onSession(() => {
      // Раздел открыт только администратору и разработчику; остальным
      // эти экраны и так не показываются (showReferencesSub).
      if (!isAdmin()) return;
      ensureView();
      if (mayEdit()){
        decorateList('warehousesDir', 'portal.button.create_warehouse');
        decorateList('depositorsDir', 'portal.button.create_depositor');
      }
      ensureOperatorSub();
      reload('warehousesDir').catch(() => {});
      reload('depositorsDir').catch(() => {});
      reload('operatorsDir').catch(() => {});
      reloadEnum('orderTypes').catch(() => {});
      reloadEnum('docTypes').catch(() => {});
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
