/*
  Пользовательское наименование полей — вкладка карточки поклажедателя.

  Раньше это был отдельный раздел администрирования со своим фильтром
  по клиенту. Настройка одного клиента была разбросана по двум экранам,
  поэтому вкладка переехала в карточку доступов поклажедателя: клиент
  задан карточкой, фильтр по нему больше не нужен, и в таблице нет
  колонки «Поклажедатель».

  Правка — кнопкой и окном, как в прототипе: строк тут мало, заводят их
  редко, а полей в окне три, и объект с полем связаны (список полей
  зависит от объекта). Правка прямо в строке потребовала бы держать
  в таблице два зависимых списка.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  async function api(method, path, body){
    const resp = await fetch(path, {
      method: method,
      headers: {'Content-Type':'application/json', 'Accept':'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (resp.status === 204) return null;
    const text = await resp.text();
    const data = text ? JSON.parse(text) : null;
    if (!resp.ok) throw new Error(PORTAL.errorText(resp.status, data));
    return data;
  }

  // catalog — один на весь портал (какие поля вообще можно
  // переименовать), rows — что переименовано у ЭТОГО клиента.
  const state = {
    depositorId: null,
    catalog: [],
    rows: [],
    filterObject: '',
    filterField: ''
  };
  PORTAL.fieldLabels = state;

  /* ======================= Данные ======================= */

  state.load = async function(depositorId){
    state.depositorId = depositorId;
    state.filterObject = '';
    state.filterField = '';
    const [catalog, rows] = await Promise.all([
      // каталог полей не зависит от клиента — читаем один раз за вход
      state.catalog.length ? Promise.resolve(state.catalog)
                           : api('GET', '/api/admin/field-catalog'),
      api('GET', '/api/admin/field-labels?depositorId=' + encodeURIComponent(depositorId))
    ]);
    state.catalog = catalog || [];
    state.rows = rows || [];
  };

  /** Объекты каталога, по одному разу, в порядке каталога. */
  function objects(){
    const out = [];
    const seen = new Set();
    state.catalog.forEach(f => {
      if (seen.has(f.objectCode)) return;
      seen.add(f.objectCode);
      out.push({code: f.objectCode, name: f.objectName});
    });
    return out;
  }

  function fieldsOf(objectCode){
    return state.catalog.filter(f => f.objectCode === objectCode);
  }

  /** Поле каталога по паре «объект + поле»: нужно его портальное имя. */
  function fieldOf(objectCode, fieldId){
    return state.catalog.find(f => f.objectCode === objectCode && f.fieldId === fieldId);
  }

  function visible(){
    const needle = state.filterField.trim().toLowerCase();
    return state.rows.filter(r =>
      (!state.filterObject || r.objectCode === state.filterObject)
      && (!needle
          || String(r.fieldId || '').toLowerCase().indexOf(needle) >= 0
          || String(r.customLabel || '').toLowerCase().indexOf(needle) >= 0));
  }

  /* ====================== Разметка ====================== */

  function option(value, text, selected){
    return '<option value="' + esc(value) + '"' + (selected ? ' selected' : '') + '>'
      + esc(text) + '</option>';
  }

  state.html = function(){
    const rows = visible();
    const head = '<tr>'
      + '<th>' + PORTAL.t('admin.column.object') + '</th>'
      + '<th>' + PORTAL.t('admin.column.field_id') + '</th>'
      + '<th>' + PORTAL.t('admin.column.custom_field_name') + '</th>'
      + '<th>' + PORTAL.t('admin.column.shown_in') + '</th>'
      + '<th></th></tr>';

    const row = r => '<tr style="cursor:pointer;" data-fl-row="'
      + esc(r.objectCode) + '|' + esc(r.fieldId) + '">'
      + '<td>' + esc(r.objectName) + '</td>'
      + '<td>' + esc(r.fieldId) + '</td>'
      + '<td><strong>' + esc(r.customLabel) + '</strong></td>'
      + '<td style="color:var(--muted);">' + esc(r.shownIn || '—') + '</td>'
      + '<td><button class="btn ghost" type="button" data-fl-del="'
      + esc(r.objectCode) + '|' + esc(r.fieldId) + '" data-i18n="common.button.delete">'
      + esc(T_('common.button.delete')) + '</button></td>'
      + '</tr>';

    return ''
      + '<div class="panel-head" style="margin:14px 0 0;">'
      + '  <div class="hint" style="margin:0;max-width:720px;">'
      + PORTAL.t('admin.hint.depositor_field_labels') + '</div>'
      + '  <div class="actions"><button class="btn primary" type="button" id="flAddBtn"'
      + ' data-i18n="admin.button.add">' + esc(T_('admin.button.add')) + '</button></div>'
      + '</div>'
      + '<div class="filters">'
      + '  <div class="fgroup"><label for="flFilterObject2">'
      + PORTAL.t('common.label.object') + '</label>'
      + '    <select id="flFilterObject2">'
      + option('', T_('common.option.all'), !state.filterObject)
      + objects().map(o => option(o.code, o.name, o.code === state.filterObject)).join('')
      + '    </select></div>'
      + '  <div class="fgroup"><label for="flFilterField2">'
      + PORTAL.t('admin.label.field') + '</label>'
      + '    <input type="text" id="flFilterField2" value="' + esc(state.filterField) + '"'
      + ' data-i18n-placeholder="admin.placeholder.field_code"'
      + ' placeholder="' + esc(T_('admin.placeholder.field_code')) + '"></div>'
      + '  <div class="spacer"></div>'
      + '  <button class="btn ghost" type="button" id="flClearBtn"'
      + ' data-i18n="admin.button.clear_filters">'
      + esc(T_('admin.button.clear_filters')) + '</button>'
      + '</div>'
      + '<div class="table-card"><table><thead>' + head + '</thead><tbody>'
      + (rows.length ? rows.map(row).join('')
          : '<tr><td colspan="5" style="color:var(--muted);">'
            + PORTAL.t('common.text.list_is_empty') + '</td></tr>')
      + '</tbody></table></div>';
  };

  /**
   * Обработчики после вставки разметки.
   *
   * repaint — перерисовка всей карточки: вкладку собирает
   * dep-visibility.js, и вызывать его отрисовку изнутри нельзя,
   * не замкнув два файла друг на друга.
   */
  state.wire = function(el, repaint){
    const add = el.querySelector('#flAddBtn');
    if (add) add.onclick = () => openModal(null, repaint);

    const objectFilter = el.querySelector('#flFilterObject2');
    if (objectFilter) objectFilter.onchange = () => {
      state.filterObject = objectFilter.value;
      repaint();
    };

    // Фильтруем по вводу, без кнопки «Найти»: строк тут десятки,
    // искать есть смысл сразу.
    const fieldFilter = el.querySelector('#flFilterField2');
    if (fieldFilter) fieldFilter.oninput = () => {
      state.filterField = fieldFilter.value;
      const at = fieldFilter.selectionStart;
      repaint();
      const again = document.getElementById('flFilterField2');
      if (again){ again.focus(); again.setSelectionRange(at, at); }
    };

    const clear = el.querySelector('#flClearBtn');
    if (clear) clear.onclick = () => {
      state.filterObject = '';
      state.filterField = '';
      repaint();
    };

    el.querySelectorAll('[data-fl-row]').forEach(tr => {
      tr.onclick = e => {
        if (e.target.closest('[data-fl-del]')) return;   // кнопка удаления своя
        openModal(tr.getAttribute('data-fl-row'), repaint);
      };
    });

    el.querySelectorAll('[data-fl-del]').forEach(b => {
      b.onclick = async () => {
        const [objectCode, fieldId] = b.getAttribute('data-fl-del').split('|');
        const r = state.rows.find(x => x.objectCode === objectCode && x.fieldId === fieldId);
        const portal = fieldOf(objectCode, fieldId);
        // Удаляется не поле, а его переименование: пишем именно это,
        // иначе кнопка «Удалить» в такой таблице читается как «убрать
        // поле у клиента», а это соседняя вкладка.
        const shown = { field: r ? r.customLabel : fieldId };
        if (!window.confirm(portal
            ? T('admin.message.confirm_reset_field_label',
                { field: shown.field, portal: portal.portalLabel })
            : T('admin.message.confirm_reset_field_label_plain', shown))) return;
        await save(objectCode, fieldId, '', repaint);
      };
    });
  };

  /* ======================= Окно ======================= */

  // Окно строим один раз и держим в body: внутри карточки оно оказалось
  // бы внутри перерисовываемого куска и исчезало бы вместе с ним.
  function overlay(){
    let el = document.getElementById('flOverlay2');
    if (el) return el;
    el = document.createElement('div');
    el.className = 'col-overlay';
    el.id = 'flOverlay2';
    el.style.alignItems = 'center';
    el.style.justifyContent = 'center';
    el.innerHTML = ''
      + '<div class="col-panel" style="width:480px;border-radius:10px;'
      + 'box-shadow:0 8px 32px rgba(0,0,0,.24);">'
      + '<h3 id="flTitle2" style="font-size:17px;"></h3>'
      + '<div style="display:flex;flex-direction:column;gap:16px;margin-top:14px;">'
      + '  <div class="kv"><label for="flObject2">' + PORTAL.t('common.label.object')
      + '</label><select class="kv-input kv-input-editable" id="flObject2"></select></div>'
      + '  <div class="kv"><label for="flField2">' + PORTAL.t('admin.label.field')
      + '</label><select class="kv-input kv-input-editable" id="flField2"></select></div>'
      + '  <div class="kv"><label for="flLabel2">' + PORTAL.t('common.label.custom_name')
      + '</label><input type="text" class="kv-input kv-input-editable" id="flLabel2"></div>'
      + '  <div class="hint" style="margin:0;">'
      + PORTAL.t('admin.hint.empty_label_restores_portal_name') + '</div>'
      + '</div>'
      + '<div style="display:flex;justify-content:flex-end;gap:10px;margin-top:20px;">'
      + '  <button class="btn ghost" type="button" id="flCancel2"'
      + ' data-i18n="common.button.cancel"></button>'
      + '  <button class="btn primary" type="button" id="flSave2"'
      + ' data-i18n="common.button.save"></button>'
      + '</div></div>';
    document.body.appendChild(el);
    el.onclick = e => { if (e.target === el) close(); };
    return el;
  }

  function close(){
    const el = document.getElementById('flOverlay2');
    if (el) el.classList.remove('active');
  }

  /** key — «объект|поле» для правки, null для новой записи. */
  function openModal(key, repaint){
    const el = overlay();
    const existing = key
      ? state.rows.find(r => r.objectCode + '|' + r.fieldId === key)
      : null;

    el.querySelector('#flTitle2').textContent =
      T_(existing ? 'common.title.edit_override' : 'common.title.add_override');
    el.querySelector('#flCancel2').textContent = T_('common.button.cancel');
    el.querySelector('#flSave2').textContent = T_('common.button.save');

    const objectSelect = el.querySelector('#flObject2');
    const fieldSelect = el.querySelector('#flField2');
    const labelInput = el.querySelector('#flLabel2');
    const list = objects();
    objectSelect.innerHTML = list
      .map(o => option(o.code, o.name,
          existing ? o.code === existing.objectCode : false)).join('');
    if (!existing && list.length) objectSelect.value = list[0].code;

    const fillFields = () => {
      const fields = fieldsOf(objectSelect.value);
      fieldSelect.innerHTML = fields.map(f => option(f.fieldId,
          f.portalLabel + ' · ' + f.fieldId,
          existing ? f.fieldId === existing.fieldId : false)).join('');
    };
    fillFields();
    objectSelect.onchange = fillFields;
    // Правка существующей записи меняет только название: объект и поле
    // и есть её ключ, сменить их — это другая запись.
    objectSelect.disabled = !!existing;
    fieldSelect.disabled = !!existing;

    labelInput.value = existing ? (existing.customLabel || '') : '';

    el.querySelector('#flCancel2').onclick = close;
    el.querySelector('#flSave2').onclick = async () => {
      await save(objectSelect.value, fieldSelect.value, labelInput.value, repaint);
    };

    el.classList.add('active');
    labelInput.focus();
  }

  async function save(objectCode, fieldId, label, repaint){
    try {
      state.rows = await api('PUT', '/api/admin/field-labels', {
        depositorId: state.depositorId,
        objectCode: objectCode,
        fieldId: fieldId,
        label: label
      }) || [];
    } catch (e){
      say(e.message);
      return;
    }
    close();
    say(T_('portal.text.saved'));
    repaint();
    // Правили текущего клиента — подписи на экранах его же: перечитываем
    // раскладку, иначе новое название появилось бы только после входа.
    if (PORTAL.session && PORTAL.session.depositorId === state.depositorId){
      PORTAL.reloadAll();
    }
  }

  /* ===================== Установка ===================== */

  PORTAL.onSession(() => {
    // Каталог полей отдаётся на языке администратора — после смены
    // языка или входа он чужой.
    state.catalog = [];
    state.rows = [];
    state.depositorId = null;
  });
})();
