
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

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

  // Раскладка с сервера: таблица -> [{columnId, label, isVisible, sortOrder, isPinned}]
  //
  // isPinned — «из таблицы убрать нельзя» (V36). Это не то же, что
  // «поклажедателю скрыть нельзя»: служебные поля товара клиенту
  // скрыть нельзя, но себе в таблице человек их убирает свободно.
  const layout = {};
  // raw — какие колонки сервер вообще отдал по таблице, до отбрасывания
  // тех, которых нет в вёрстке. Именно это и есть «доступно клиенту»:
  // запрещённую колонку table_columns не отдаёт совсем.
  const raw = {};
  PORTAL.columns = {layout: layout, raw: raw};

  /* ============ Применение раскладки к состоянию прототипа ============ */

  // Видимые идут первыми в своём порядке, за ними скрытые: так выглядит
  // список в окне выбора, и так же человек ожидает его увидеть.
  function ordered(rows){
    const vis = rows.filter(r => r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    const hid = rows.filter(r => !r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    return vis.concat(hid);
  }

  // tableColumnState и соседи объявлены в прототипе через const на верхнем
  // уровне. Такие объявления — лексические глобали: свойства у window для
  // них не создаётся, и обращение через window.tableColumnState даёт
  // undefined. Поэтому читаем по голому имени, прикрываясь typeof.
  const есть = name => {
    try { return eval('typeof ' + name) !== 'undefined'; } catch (e){ return false; }
  };

  function applyTable(tableKey, rows){
    raw[tableKey] = rows.map(r => r.columnId);
    if (!есть('tableColumnState') || !tableColumnState[tableKey]) return;
    const known = new Set((tableColumns[tableKey] || []).map(c => c.id));
    // колонка могла пропасть из вёрстки, но остаться в базе — рисовать
    // её нечем, поэтому ориентируемся на то, что умеет прототип
    const rowsHere = ordered(rows.filter(r => known.has(r.columnId)));
    if (!rowsHere.length) return;

    layout[tableKey] = rowsHere;
    tableColumnState[tableKey] = rowsHere.map(r => r.columnId);
    tableColumnHidden[tableKey] = new Set(
        rowsHere.filter(r => !r.isVisible).map(r => r.columnId));
    // tableColumnLocked НЕ трогаем. В прототипе это значит «не рисовать
    // эту колонку в таблице» (renderTableHead и renderTableRow её
    // отфильтровывают), а вовсе не «нельзя скрыть». Когда в V36
    // появилось прикрепление и я записал прикреплённые сюда, коммерческий
    // код, наименование и единица пропали и из списка товаров, и из окна
    // выбора колонок. Запрет «эти не убрать» живёт в обработчике
    // сохранения (он дописывает их обратно) и в серой галочке окна.
  }

  function applyAll(rows){
    const byTable = {};
    rows.forEach(r => { (byTable[r.tableKey] = byTable[r.tableKey] || []).push(r); });
    Object.keys(byTable).forEach(t => applyTable(t, byTable[t]));
  }

  /** Подпись колонки из раскладки: в ней уже и перевод, и переименование. */
  function labelOf(tableKey, columnId){
    const rows = layout[tableKey];
    if (!rows) return null;
    const row = rows.find(r => r.columnId === columnId);
    return row ? row.label : null;
  }

  // Перерисовка таблицы после смены раскладки. Имена функций берём
  // у прототипа: каждая таблица рисуется своей.
  const RENDER = {
    receipts: 'renderReceipts', orders: 'renderOrders', transfers: 'renderTransfers',
    stock: 'renderStock', stockBatch: 'renderStockBatchTable', sku: 'renderSku',
    receiptComposition: 'renderReceiptComposition',
    orderComposition: 'renderOrderComposition',
    transferComposition: 'renderTransferComposition',
    receiptLineDetail: 'renderReceiptLineDetailTable',
    orderLineDetail: 'renderOrderLineDetailTable',
    transferLineDetail: 'renderTransferLineDetailTable'
  };

  // Таблица может быть на скрытом экране, и её отрисовка падает на
  // отсутствующем элементе. Одна такая ошибка не должна обрывать
  // остальные — та же беда уже была при смене языка.
  function redraw(tableKey){
    const name = RENDER[tableKey];
    if (!name || typeof window[name] !== 'function') return;
    try {
      window[name]();
      if (typeof updateTopScrollWidth === 'function') updateTopScrollWidth(tableKey);
    } catch (e){ /* экран не открыт — перерисуется при открытии */ }
  }

  function redrawAll(){ Object.keys(layout).forEach(redraw); }

  async function reload(tableKey){
    const url = tableKey ? '/api/table-columns/' + encodeURIComponent(tableKey)
                         : '/api/table-columns';
    const rows = await api('GET', url);
    if (tableKey) applyTable(tableKey, rows); else applyAll(rows);
    return rows;
  }
  PORTAL.columns.reload = reload;

  /* ============ Сохранение выбора ============ */

  function wrapPicker(){
    const applyOrig = window.applyColumnPicker;
    if (typeof applyOrig === 'function'){
      window.applyColumnPicker = function(){
        // прочитать ДО вызова оригинала: он закрывает окно и обнуляет обе
        const tableKey = есть('colPickerTable') ? colPickerTable : null;
        const draft = есть('colPickerDraft') ? colPickerDraft : null;
        // прототип сначала применяет выбор у себя — экран меняется сразу,
        // не дожидаясь ответа сервера
        applyOrig.apply(this, arguments);
        if (!tableKey || !draft) return;
        const chosen = draft.order.filter(id => !draft.hidden.has(id));
        const locked = (layout[tableKey] || []).filter(r => r.isPinned).map(r => r.columnId);
        locked.forEach(id => { if (!chosen.includes(id)) chosen.push(id); });
        if (!chosen.length){
          say(T('common.message.keep_at_least_one_column'));
          reload(tableKey).then(() => redraw(tableKey)).catch(() => {});
          return;
        }
        api('PUT', '/api/table-columns/' + encodeURIComponent(tableKey), {columns: chosen})
          .then(rows => { applyTable(tableKey, rows); redraw(tableKey); })
          .catch(err => {
            say(T('common.message.columns_shown_but_not_saved') + ': ' + err.message);
            reload(tableKey).then(() => redraw(tableKey)).catch(() => {});
          });
      };
    }

    // Прикреплённую колонку снять нельзя — галочка серая и отмечена.
    // Запрет работает и без этого (обработчик сохранения дописывает её
    // обратно), но тогда человек снимает галочку, нажимает «Применить»
    // и видит колонку на месте — выглядит как сломанное окно.
    const renderOrig = window.renderColumnPickerList;
    if (typeof renderOrig === 'function'){
      window.renderColumnPickerList = function(){
        renderOrig.apply(this, arguments);
        const tableKey = есть('colPickerTable') ? colPickerTable : null;
        if (!tableKey) return;
        const pinned = new Set((layout[tableKey] || [])
            .filter(r => r.isPinned).map(r => r.columnId));
        if (!pinned.size) return;
        const list = document.getElementById('colList');
        if (!list) return;
        [...list.querySelectorAll('.col-item')].forEach(el => {
          const cb = el.querySelector('input[type=checkbox]');
          const onchange = cb && cb.getAttribute('onchange') || '';
          const m = onchange.match(/toggleColVisibility\('([^']+)'\)/);
          if (!m || !pinned.has(m[1])) return;
          cb.checked = true;
          cb.disabled = true;
          el.title = T('references.hint.column_cannot_be_hidden');
          el.style.color = 'var(--muted)';
        });
      };
    }

    // «Сбросить» теперь значит «вернуть настройки по умолчанию», а не
    // «показать всё»: именно этого человек от кнопки и ждёт. Действие
    // применяется сразу, поэтому окно закрывается — иначе осталась бы
    // кнопка «Применить», которая записала бы умолчания как личный выбор.
    window.resetColumnPicker = function(){
      const tableKey = есть('colPickerTable') ? colPickerTable : null;
      if (!tableKey) return;
      api('DELETE', '/api/table-columns/' + encodeURIComponent(tableKey))
        .then(rows => {
          applyTable(tableKey, rows);
          redraw(tableKey);
          if (typeof closeColumnPicker === 'function') closeColumnPicker();
          say(T('common.message.columns_reset_to_default'));
        })
        .catch(err => say(err.message));
    };
  }

  /* ============ Подписи ============ */

  function wrapLabels(){
    // COLTH зовут шапки таблиц в карточках документов и регистрах
    const colthOrig = window.COLTH;
    window.COLTH = function(c){
      if (c && c.id){
        for (const t of Object.keys(layout)){
          if ((tableColumns[t] || []).some(x => x === c)){
            const own = labelOf(t, c.id);
            if (own) return own;
          }
        }
      }
      return typeof colthOrig === 'function' ? colthOrig(c) : (c && c.label) || '';
    };

    const locOrig = window.localizeColumnLabel;
    window.localizeColumnLabel = function(ruLabel, tableKey, colId){
      if (tableKey && colId){
        const own = labelOf(tableKey, colId);
        if (own) return own;
      }
      // окно выбора зовёт без таблицы — берём открытую
      const open = есть('colPickerTable') ? colPickerTable : null;
      if (!tableKey && open){
        const rows = layout[open] || [];
        const cols = tableColumns[open] || [];
        const col = cols.find(x => x.label === ruLabel);
        if (col){
          const row = rows.find(r => r.columnId === col.id);
          if (row) return row.label;
        }
      }
      return typeof locOrig === 'function'
        ? locOrig.apply(this, arguments) : ruLabel;
    };
  }

  /* ============ Администрирование: колонки поклажедателя ============ */

  const admin = {depositorId: null, table: null, tables: [], rows: []};

  const isAdmin = () => ['admin', 'developer'].includes(
      PORTAL.session && PORTAL.session.roleCode);
  const mayEdit = () => PORTAL.session && PORTAL.session.roleCode === 'admin';

  function tablesHtml(){
    return '<div style="display:flex; gap:18px; align-items:flex-start; flex-wrap:wrap;">'
      + '<div style="min-width:220px;">'
      + admin.tables.map(t =>
          '<div class="col-item" style="cursor:pointer;'
          + (t.tableKey === admin.table ? ' font-weight:700;' : '') + '"'
          + ' onclick="portalDepositorColumnsTable(\'' + esc(t.tableKey) + '\')">'
          + esc(t.objectName || t.tableKey) + ' <span class="count">'
          + esc(t.tableKey) + ', ' + t.columnCount + '</span></div>').join('')
      + '</div>'
      + '<div style="flex:1; min-width:320px;" id="depColList"></div>'
      + '</div>';
  }

  function columnsHtml(){
    if (!admin.rows.length) return '<div class="hint">' + T('references.text.pick_a_section_on_the_left') + '</div>';
    const ro = mayEdit() ? '' : ' disabled';
    return '<table class="table"><thead><tr>'
      + '<th>' + T('references.column.column') + '</th>'
      + '<th>' + T('references.column.label_for_client') + '</th>'
      + '<th style="text-align:center;">' + T('references.column.available') + '</th>'
      + '<th style="text-align:center;">' + T('references.column.show_at_once') + '</th>'
      + '</tr></thead><tbody>'
      + admin.rows.map(r => {
          const renamed = r.label !== r.portalLabel;
          return '<tr><td>' + esc(r.columnId) + '</td><td>' + esc(r.label)
            + (renamed ? ' <span class="count">(' + T('references.text.instead_of') + ' «'
                + esc(r.portalLabel) + '»)</span>' : '')
            + '</td>'
            + '<td style="text-align:center;"><input type="checkbox"' + ro
            + (r.isAvailable ? ' checked' : '')
            + ' onchange="portalDepositorColumnSave(\'' + esc(r.columnId)
            + '\', this.checked, null)"></td>'
            + '<td style="text-align:center;"><input type="checkbox"' + ro
            + (r.isDefault ? ' checked' : '') + (r.isAvailable ? '' : ' disabled')
            + ' onchange="portalDepositorColumnSave(\'' + esc(r.columnId)
            + '\', null, this.checked)"></td></tr>';
        }).join('')
      + '</tbody></table>'
      + (mayEdit()
          ? '<div style="margin-top:12px;"><button class="btn" type="button"'
            + ' onclick="portalDepositorColumnsReset()">'
            + T('references.button.restore_portal_defaults') + '</button></div>'
          : '')
      + '<div class="hint" style="margin-top:10px;">'
      + '' + T('references.hint.unavailable_column_is_hidden') + '</div>';
  }

  window.portalDepositorColumnsTable = async function(tableKey){
    admin.table = tableKey;
    try {
      admin.rows = await api('GET', '/api/admin/depositor-columns?depositorId='
          + encodeURIComponent(admin.depositorId) + '&table=' + encodeURIComponent(tableKey));
    } catch (e){
      admin.rows = [];
      say(e.message);
    }
    const host = document.getElementById('depColBody');
    if (host) host.innerHTML = tablesHtml();
    const list = document.getElementById('depColList');
    if (list) list.innerHTML = columnsHtml();
  };

  // Строки хранятся только там, где настройка отличается от портальной,
  // поэтому «вернуть как у портала» — это убрать их. Сбрасываем весь
  // раздел: по одной колонке это и так делается галочками.
  window.portalDepositorColumnsReset = async function(){
    if (!admin.depositorId || !admin.table) return;
    try {
      admin.rows = await api('DELETE', '/api/admin/depositor-columns?depositorId='
          + encodeURIComponent(admin.depositorId)
          + '&table=' + encodeURIComponent(admin.table));
      const list = document.getElementById('depColList');
      if (list) list.innerHTML = columnsHtml();
      say(T('common.message.columns_reset_to_default'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalDepositorColumnSave = async function(columnId, available, byDefault){
    const row = admin.rows.find(r => r.columnId === columnId);
    if (!row) return;
    try {
      admin.rows = await api('PUT', '/api/admin/depositor-columns', {
        depositorId: admin.depositorId,
        table: admin.table,
        columnId: columnId,
        isAvailable: available === null ? row.isAvailable : available,
        isDefault: byDefault === null ? row.isDefault : byDefault
      });
      const list = document.getElementById('depColList');
      if (list) list.innerHTML = columnsHtml();
    } catch (e){
      say(e.message);
      portalDepositorColumnsTable(admin.table);
    }
  };

  /** Вкладка «Колонки» в карточке поклажедателя. */
  async function openDepositorColumns(depositorId){
    admin.depositorId = depositorId;
    admin.table = null;
    admin.rows = [];
    try {
      if (!admin.tables.length) admin.tables = await api('GET', '/api/admin/column-tables');
    } catch (e){
      say(e.message);
      return;
    }
    const host = document.getElementById('depColBody');
    if (host) host.innerHTML = tablesHtml();
    if (admin.tables.length) portalDepositorColumnsTable(admin.tables[0].tableKey);
  }

  // Карточка поклажедателя в прототипе без вкладок, поэтому полоску
  // вкладок добавляем сами, а не переписываем карточку целиком.
  function wrapDepositorCard(){
    const renderOrig = window.portalDirRenderHook;
    const state = {tab: 'card'};

    function decorate(){
      if (!PORTAL.columns.currentDir || PORTAL.columns.currentDir !== 'depositorsDir') return;
      const body = document.getElementById('portalDirBody');
      if (!body || document.getElementById('depTabs')) return;
      const id = PORTAL.columns.currentId;
      if (!id) return;
      const strip = document.createElement('div');
      strip.className = 'admin-subtabs';
      strip.id = 'depTabs';
      strip.style.marginBottom = '12px';
      strip.innerHTML =
          '<button class="admin-subtab-btn ' + (state.tab === 'card' ? 'active' : '') + '"'
          + ' onclick="portalDepositorTab(\'card\')">' + T('partners.title.card') + '</button>'
          + '<button class="admin-subtab-btn ' + (state.tab === 'columns' ? 'active' : '') + '"'
          + ' onclick="portalDepositorTab(\'columns\')">' + T('references.title.columns') + '</button>';
      body.parentNode.insertBefore(strip, body);
      const pane = document.createElement('div');
      pane.id = 'depColBody';
      pane.style.display = state.tab === 'columns' ? '' : 'none';
      body.parentNode.insertBefore(pane, body.nextSibling);
      body.style.display = state.tab === 'columns' ? 'none' : '';
      if (state.tab === 'columns') openDepositorColumns(id);
    }

    window.portalDepositorTab = function(tab){
      state.tab = tab;
      const body = document.getElementById('portalDirBody');
      const pane = document.getElementById('depColBody');
      const strip = document.getElementById('depTabs');
      if (!body || !pane || !strip) return;
      body.style.display = tab === 'columns' ? 'none' : '';
      pane.style.display = tab === 'columns' ? '' : 'none';
      strip.querySelectorAll('.admin-subtab-btn').forEach((b, i) => {
        b.classList.toggle('active', (i === 0) === (tab === 'card'));
      });
      if (tab === 'columns') openDepositorColumns(PORTAL.columns.currentId);
    };

    const openOrig = window.portalOpenPortalDir;
    if (typeof openOrig === 'function'){
      window.portalOpenPortalDir = async function(dirName, id){
        PORTAL.columns.currentDir = dirName;
        PORTAL.columns.currentId = id;
        state.tab = 'card';
        const strip = document.getElementById('depTabs');
        if (strip) strip.remove();
        const pane = document.getElementById('depColBody');
        if (pane) pane.remove();
        await openOrig.apply(this, arguments);
        if (isAdmin() && dirName === 'depositorsDir') decorate();
      };
    }
    return renderOrig;
  }

  /* ============ Копирование настроек пользователю ============ */

  function wrapUserCard(){
    const openOrig = window.portalOpenAdminUser;
    if (typeof openOrig !== 'function') return;
    window.portalOpenAdminUser = async function(userId){
      await openOrig.apply(this, arguments);
      if (!mayEdit() || !userId) return;
      addCopyBlock(userId);
    };
  }

  async function addCopyBlock(userId){
    const host = document.getElementById('adminSetPassword');
    const row = host && host.parentNode;
    if (!row || document.getElementById('copySettingsFrom')) return;
    let users = [];
    try {
      users = await api('GET', '/api/admin/users');
    } catch (e){ return; }
    const others = users.filter(u => u.userId !== userId && u.isActive !== false);
    if (!others.length) return;
    const box = document.createElement('div');
    box.style.cssText = 'display:flex; gap:10px; align-items:flex-end; flex-wrap:wrap;'
        + ' margin-top:14px; width:100%;';
    box.innerHTML = '<div class="kv" style="margin:0; min-width:280px;">'
      + '<label>' + T('admin.label.copy_settings_from_user') + '</label>'
      + '<select class="kv-input kv-input-editable" id="copySettingsFrom">'
      + '<option value="">' + T('common.option.not_selected') + '</option>'
      + others.map(u => '<option value="' + esc(u.userId) + '">'
          + esc(u.fullName || u.login) + ' (' + esc(u.login) + ')</option>').join('')
      + '</select></div>'
      + '<button class="btn" type="button" onclick="portalCopyUserSettings(\''
      + esc(userId) + '\')">' + T('admin.button.copy') + '</button>'
      + '<div class="hint" style="width:100%; margin:0;">'
      + '' + T('admin.hint.what_copy_settings_transfers') + '</div>';
    row.parentNode.insertBefore(box, row.nextSibling);
  }

  window.portalCopyUserSettings = async function(toUserId){
    const sel = document.getElementById('copySettingsFrom');
    if (!sel || !sel.value){
      say(T('admin.message.pick_whom_to_copy_from'));
      return;
    }
    try {
      await api('POST', '/api/admin/users/copy-settings',
          {fromUserId: sel.value, toUserId: toUserId});
      say(T('admin.message.settings_copied'));
    } catch (e){
      say(e.message);
    }
  };

  /* ============ Установка ============ */

  function install(){
    wrapPicker();
    wrapLabels();
    wrapDepositorCard();
    wrapUserCard();

    PORTAL.onLogin(() => {
      reload(null).then(redrawAll).catch(() => {
        // колонки не загрузились — прототип работает на своих умолчаниях,
        // экран остаётся рабочим
      });
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
