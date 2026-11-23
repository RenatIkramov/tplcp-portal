/*
  Группа администрирования «Перечисления» (V95).

  Решение заказчика от 02.11.2026: все перечисления — в одной группе
  администрирования; значение создаётся и правится в модальном окне,
  и в том же окне — его переводы на включённые языки. Прежние вкладки
  «Единицы измерения», «Типы заказов», «Типы поступлений» в «Общих
  справочниках» убраны: их перечисления здесь, вместе с остальными.

  Экран:
    * слева — список перечислений: сколько значений и сколько из них
      без перевода. Отсюда видно, где ещё есть работа переводчику;
    * справа — значения выбранного перечисления: код, основное имя,
      переводы, порядок, «используется», «действует». Щелчок по строке
      открывает окно;
    * окно: код, основное (английское) имя, второй код — обозначение
      единицы или буквенный код валюты, порядок, «действует» и блок
      «Переводы» — по полю на каждый включённый язык, кроме основного.
      Сохраняется всё одним запросом.

  Что нельзя и почему — решает сервер, экран только не предлагает
  того, что упрётся в отказ:
    * код используемого значения не меняется — на него ссылаются записи;
    * значение, на код которого опирается программа (черновик, роль
      «admin»…), помечено «системное»: код не меняется, отключить
      и удалить его нельзя;
    * удалить можно только значение, на которое ничего не ссылается.

  Сводная вкладка «Тексты интерфейса → Перечисления» (enums.js)
  осталась: там переводы всех перечислений одной таблицей.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, say} = window.PORTAL;
  const tr = (k, p) => (typeof T === 'function' ? T(k, p) : k);
  const byId = id => document.getElementById(id);

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
    return resp.json();
  }

  /**
   * @type {{enums: EnumDef[], locales: EnumLocale[], rows: EnumValueRow[],
   *         current: string | null, form: {enumKey: string, valueId: string | null} | null,
   *         loaded: boolean}}
   */
  const state = {enums: [], locales: [], rows: [], current: null, form: null, loaded: false};
  PORTAL.enumValues = state;

  const isAdmin = () => PORTAL.isAdmin();

  /** @type {EnumTranslation} */
  const NO_TRANSLATION = {name: null, symbol: null};

  const enumOf = key => state.enums.find(e => e.enumKey === key) || null;
  const rowsOf = key => state.rows.filter(r => r.enumKey === key);

  /** Значению не хватает перевода хотя бы на один включённый язык. */
  function untranslated(row){
    const e = enumOf(row.enumKey);
    return state.locales.some(l => {
      const v = (row.values && row.values[l.code]) || NO_TRANSLATION;
      return !v.name || (!!e && e.hasSymbol && !v.symbol);
    });
  }

  /* ================= Разметка вкладки ================= */

  function createTab(){
    if (byId('admin-sub-enumValues')) return;
    const host = byId('view-admin');
    if (!host) return;

    const panel = document.createElement('div');
    panel.className = 'admin-sub';
    panel.id = 'admin-sub-enumValues';
    panel.innerHTML = ''
      + '<div class="panel-head" style="margin-top:0;">'
      + '<div class="section-title" style="margin:0;">' + PORTAL.t('admin.button.enums') + '</div></div>'
      + '<div class="hint" style="margin:-6px 0 12px;">' + PORTAL.t('admin.hint.enum_values') + '</div>'
      + '<div class="enum-layout">'
      + '  <div class="table-card enum-list" id="enumValuesList"></div>'
      + '  <div class="enum-values">'
      + '    <div class="filters">'
      + '      <div class="fgroup"><label for="enumValuesSearch">'
      +          PORTAL.t('common.text.search_by_text_or_key') + '</label>'
      + '        <input id="enumValuesSearch"/></div>'
      + '      <div class="fgroup"><label for="enumValuesFilter">' + PORTAL.t('common.text.show') + '</label>'
      + '        <select id="enumValuesFilter"></select></div>'
      + '      <div class="spacer"></div>'
      + '      <button class="btn primary" type="button" id="enumValuesAdd">'
      +          PORTAL.t('admin.button.add_enum_value') + '</button>'
      + '    </div>'
      + '    <div class="table-card"><div class="table-scroll-x">'
      + '      <table><thead><tr id="enumValuesHead"></tr></thead>'
      + '      <tbody id="enumValuesBody"></tbody></table></div></div>'
      + '  </div>'
      + '</div>';
    host.appendChild(panel);

    byId('enumValuesSearch').oninput = renderValues;
    byId('enumValuesFilter').onchange = renderValues;
    byId('enumValuesAdd').onclick = () => openForm(null);
    createModal();
  }

  async function load(){
    if (!isAdmin()) return;
    createTab();
    try {
      apply(await api('GET', '/api/admin/enum-values'));
    } catch (e){
      say(e.message);
    }
  }

  /** @param {EnumValuesAnswer} answer */
  function apply(answer){
    state.enums = answer.enums || [];
    state.locales = answer.locales || [];
    state.rows = answer.rows || [];
    state.loaded = true;
    if (!state.current || !enumOf(state.current)){
      state.current = state.enums.length ? state.enums[0].enumKey : null;
    }
    render();
  }

  /* ================= Отрисовка ================= */

  function render(){
    if (!byId('enumValuesList')) return;
    renderList();
    fillFilter();
    renderValues();
  }

  function renderList(){
    const box = byId('enumValuesList');
    box.innerHTML = state.enums.map(e => {
      const rows = rowsOf(e.enumKey);
      const missing = state.locales.length ? rows.filter(untranslated).length : 0;
      return '<button type="button" class="enum-item' + (e.enumKey === state.current ? ' current' : '')
        + '" data-enum-key="' + esc(e.enumKey) + '">'
        + '<span class="enum-item-name">' + esc(tr(e.textKey)) + '</span>'
        + '<span class="count">' + rows.length + '</span>'
        + (missing ? '<span class="badge warn" data-untranslated="' + missing + '">'
            + esc(tr('admin.text.untranslated_count', {count: missing})) + '</span>' : '')
        + '</button>';
    }).join('');
    box.querySelectorAll('[data-enum-key]').forEach(b => {
      b.onclick = () => {
        state.current = b.getAttribute('data-enum-key');
        render();
      };
    });
  }

  function fillFilter(){
    const f = byId('enumValuesFilter');
    const keep = f.value;
    f.innerHTML = '<option value="">' + esc(tr('admin.option.all_values')) + '</option>'
      + '<option value="missing">' + esc(tr('common.text.missing_translations_only')) + '</option>';
    f.value = keep;
  }

  function visibleRows(){
    const q = (byId('enumValuesSearch').value || '').trim().toLowerCase();
    const onlyMissing = byId('enumValuesFilter').value === 'missing';
    return rowsOf(state.current).filter(r => {
      if (onlyMissing && !untranslated(r)) return false;
      if (!q) return true;
      const texts = [r.code, r.name, r.extra];
      Object.values(r.values || {}).forEach(v => { texts.push(v.name, v.symbol); });
      return texts.some(t => String(t || '').toLowerCase().includes(q));
    });
  }

  function renderValues(){
    const body = byId('enumValuesBody');
    if (!body) return;
    const e = enumOf(state.current);
    byId('enumValuesAdd').disabled = !e;
    if (!e){
      body.innerHTML = '';
      return;
    }
    byId('enumValuesHead').innerHTML = ''
      + '<th>' + esc(tr('admin.column.enum_code')) + '</th>'
      + '<th>' + esc(tr('admin.column.enum_base')) + '</th>'
      + (e.extraLabelKey ? '<th>' + esc(tr(e.extraLabelKey)) + '</th>' : '')
      + (e.flagLabelKey ? '<th>' + esc(tr(e.flagLabelKey)) + '</th>' : '')
      + state.locales.map(l => '<th>' + esc(l.name) + '</th>').join('')
      + '<th class="num">' + esc(tr('partners.text.order')) + '</th>'
      + '<th class="num">' + esc(tr('admin.column.enum_usage')) + '</th>'
      + '<th></th>';

    const rows = visibleRows();
    const span = 5 + state.locales.length + (e.extraLabelKey ? 1 : 0) + (e.flagLabelKey ? 1 : 0);
    body.innerHTML = rows.map(r => row(e, r)).join('')
      || '<tr><td colspan="' + span + '" style="color:var(--muted);">'
         + esc(tr('partners.text.directory_is_empty')) + '</td></tr>';
    body.querySelectorAll('[data-value-id]').forEach(tr_ => {
      tr_.onclick = () => openForm(tr_.getAttribute('data-value-id'));
    });
  }

  function row(e, r){
    const translations = state.locales.map(l => {
      const v = (r.values || {})[l.code] || {};
      const text = v.name
        ? esc(v.name) + (e.hasSymbol && v.symbol
            ? ' <span style="color:var(--muted);">(' + esc(v.symbol) + ')</span>' : '')
        : '<span style="color:var(--muted);">—</span>';
      return '<td>' + text + '</td>';
    }).join('');
    const marks = (r.isSystem ? '<span class="badge muted">' + esc(tr('admin.text.system_value')) + '</span> ' : '')
      + (r.isActive === false ? '<span class="badge">' + esc(tr('admin.text.inactive_value')) + '</span>' : '');
    return '<tr class="enum-row" data-value-id="' + esc(r.valueId) + '">'
      + '<td><strong>' + esc(r.code) + '</strong></td>'
      + '<td>' + esc(r.name) + '</td>'
      + (e.extraLabelKey ? '<td>' + esc(r.extra) + '</td>' : '')
      + (e.flagLabelKey ? '<td>' + esc(tr(r.flag ? 'common.value.yes' : 'common.value.no')) + '</td>' : '')
      + translations
      + '<td class="num">' + esc(r.sortOrder === null || r.sortOrder === undefined ? '' : r.sortOrder) + '</td>'
      + '<td class="num">' + (r.usage ? esc(r.usage) : '<span style="color:var(--muted);">0</span>') + '</td>'
      + '<td style="white-space:nowrap;">' + marks + '</td>'
      + '</tr>';
  }

  /* ================= Окно значения ================= */

  function createModal(){
    if (byId('enumValueOverlay')) return;
    const modal = document.createElement('div');
    modal.className = 'col-overlay';
    modal.id = 'enumValueOverlay';
    modal.style.cssText = 'align-items:center;justify-content:center;';
    modal.onclick = ev => { if (ev.target === modal) closeForm(); };
    modal.innerHTML = ''
      + '<div class="col-panel enum-panel" id="enumValuePanel">'
      + '  <h3 id="enumValueTitle"></h3>'
      + '  <div class="hint" id="enumValueEnum"></div>'
      + '  <div id="enumValueFields"></div>'
      + '  <div id="enumValueError" class="enum-error" style="display:none;"></div>'
      + '  <div class="col-actions">'
      + '    <button class="btn primary" type="button" id="enumValueSave"></button>'
      + '    <button class="btn ghost" type="button" id="enumValueCancel"></button>'
      + '    <div style="flex:1;"></div>'
      + '    <button class="btn ghost" type="button" id="enumValueDelete"></button>'
      + '  </div>'
      + '  <div class="enum-confirm" id="enumValueConfirm" style="display:none;">'
      + '    <span id="enumValueConfirmText"></span>'
      + '    <button class="btn primary" type="button" id="enumValueConfirmYes"></button>'
      + '    <button class="btn ghost" type="button" id="enumValueConfirmNo"></button>'
      + '  </div>'
      + '</div>';
    document.body.appendChild(modal);
    PORTAL.label(byId('enumValueSave'), 'common.button.save');
    PORTAL.label(byId('enumValueCancel'), 'common.button.cancel');
    PORTAL.label(byId('enumValueDelete'), 'common.button.delete');
    PORTAL.label(byId('enumValueConfirmYes'), 'common.button.delete');
    PORTAL.label(byId('enumValueConfirmNo'), 'common.button.cancel');
    byId('enumValueSave').onclick = save;
    byId('enumValueCancel').onclick = closeForm;
    byId('enumValueDelete').onclick = askDelete;
    byId('enumValueConfirmYes').onclick = remove;
    byId('enumValueConfirmNo').onclick = () => { byId('enumValueConfirm').style.display = 'none'; };
    document.addEventListener('keydown', ev => {
      if (ev.key === 'Escape' && state.form) closeForm();
    });
  }

  function field(id, labelKey, value, opts){
    const o = opts || {};
    return '<div class="kv" style="margin-top:12px;"><label for="' + id + '">' + PORTAL.t(labelKey) + '</label>'
      + '<input class="kv-input' + (o.readOnly ? '' : ' kv-input-editable') + '" id="' + id + '"'
      + (o.type ? ' type="' + o.type + '"' : '')
      + (o.readOnly ? ' readonly' : '')
      + (o.placeholder ? ' placeholder="' + esc(o.placeholder) + '"' : '')
      + ' value="' + esc(value) + '"/>'
      + (o.hint ? '<div class="enum-field-hint">' + o.hint + '</div>' : '')
      + '</div>';
  }

  function openForm(valueId){
    const e = enumOf(state.current);
    if (!e) return;
    const r = valueId === null ? null : rowsOf(e.enumKey).find(x => x.valueId === valueId);
    if (valueId !== null && !r) return;
    state.form = {enumKey: e.enumKey, valueId: valueId};

    const system = !!(r && r.isSystem);
    const used = !!(r && r.usage > 0);
    const codeLocked = system || used;

    PORTAL.label(byId('enumValueTitle'), r ? 'admin.title.edit_enum_value' : 'admin.title.new_enum_value');
    byId('enumValueEnum').textContent = tr(e.textKey);

    let html = '';
    if (system){
      html += '<div class="hint enum-note">' + PORTAL.t('admin.hint.enum_system_value') + '</div>';
    }
    html += '<div class="enum-grid">'
      + field('enumValueCode', 'admin.column.enum_code', r ? r.code : '', {
          readOnly: codeLocked,
          placeholder: e.codeExample || '',
          hint: used && !system ? PORTAL.t('admin.hint.enum_code_in_use') : ''})
      + (e.extraLabelKey
          ? field('enumValueExtra', e.extraLabelKey, r ? r.extra : '', {placeholder: e.extraExample || ''})
          : '')
      + field('enumValueSort', 'partners.text.order', r && r.sortOrder !== null ? r.sortOrder : '', {type: 'number'})
      + '</div>'
      + field('enumValueName', 'admin.label.base_name', r ? r.name : '')
      + '<label class="enum-check"><input type="checkbox" id="enumValueActive"'
      + ((!r || r.isActive !== false) ? ' checked' : '') + (system ? ' disabled' : '') + '/> '
      + PORTAL.t('admin.label.enum_active') + '</label>'
      // признак «да/нет» перечисления (у статуса — «можно править транспорт», V150);
      // у системного значения он правится: системность бережёт код и «действует»
      + (e.flagLabelKey
          ? '<label class="enum-check"><input type="checkbox" id="enumValueFlag"'
            + (r && r.flag ? ' checked' : '') + '/> ' + PORTAL.t(e.flagLabelKey) + '</label>'
            + (e.flagHintKey ? '<div class="hint" style="margin-top:-4px;">' + PORTAL.t(e.flagHintKey) + '</div>' : '')
          : '');

    html += '<div class="enum-translations"><div class="enum-subtitle">'
      + PORTAL.t('admin.title.enum_translations') + '</div>';
    if (!state.locales.length){
      html += '<div class="hint">' + PORTAL.t('admin.hint.enum_no_languages') + '</div>';
    } else {
      html += '<div class="hint" style="margin-bottom:4px;">' + PORTAL.t('admin.hint.enum_translations') + '</div>';
      state.locales.forEach(l => {
        const v = (r && r.values && r.values[l.code]) || NO_TRANSLATION;
        html += '<div class="enum-translation">'
          + '<label for="enumValueT_' + esc(l.code) + '">' + esc(l.name) + '</label>'
          + '<input class="kv-input kv-input-editable" id="enumValueT_' + esc(l.code) + '"'
          + ' data-locale="' + esc(l.code) + '" data-part="name" value="' + esc(v.name) + '"'
          + ' placeholder="' + esc(r ? r.name : '') + '"/>'
          + (e.hasSymbol
              ? '<input class="kv-input kv-input-editable enum-symbol" data-locale="' + esc(l.code) + '"'
                + ' data-part="symbol" value="' + esc(v.symbol) + '" placeholder="'
                + esc(tr('admin.label.enum_symbol')) + '" aria-label="'
                + esc(tr('admin.label.enum_symbol')) + ' (' + esc(l.name) + ')"/>'
              : '')
          + '</div>';
      });
    }
    html += '</div>';
    byId('enumValueFields').innerHTML = html;

    // Удаление: нового значения ещё нет; системное и используемое —
    // не удаляются, и кнопка честно говорит почему, а не упирается в отказ.
    const del = byId('enumValueDelete');
    del.style.display = r ? '' : 'none';
    del.disabled = system || used;
    del.title = system ? tr('admin.hint.enum_system_value')
      : used ? tr('admin.hint.enum_delete_in_use', {count: r.usage}) : '';
    byId('enumValueConfirm').style.display = 'none';
    showError('');

    byId('enumValueOverlay').classList.add('active');
    const first = codeLocked ? byId('enumValueName') : byId('enumValueCode');
    if (first) first.focus();
  }

  function closeForm(){
    state.form = null;
    const o = byId('enumValueOverlay');
    if (o) o.classList.remove('active');
  }

  function showError(text){
    const box = byId('enumValueError');
    if (!box) return;
    box.textContent = text || '';
    box.style.display = text ? '' : 'none';
  }

  function collect(){
    const val = id => { const el = byId(id); return el ? el.value.trim() : ''; };
    const sort = val('enumValueSort');
    const translations = {};
    state.locales.forEach(l => {
      const get = part => {
        const el = /** @type {HTMLInputElement | null} */ (document.querySelector(
            '#enumValueFields [data-locale="' + l.code + '"][data-part="' + part + '"]'));
        return el ? el.value.trim() : '';
      };
      translations[l.code] = {name: get('name'), symbol: get('symbol')};
    });
    return {
      code: val('enumValueCode'),
      name: val('enumValueName'),
      extra: byId('enumValueExtra') ? val('enumValueExtra') : null,
      sortOrder: sort === '' ? null : Number(sort),
      isActive: byId('enumValueActive').checked,
      flag: byId('enumValueFlag') ? /** @type {HTMLInputElement} */ (byId('enumValueFlag')).checked : null,
      translations: translations
    };
  }

  async function save(){
    const form = state.form;
    if (!form) return;
    const url = '/api/admin/enum-values/' + encodeURIComponent(form.enumKey)
      + (form.valueId === null ? '' : '/' + encodeURIComponent(form.valueId));
    try {
      apply(await api(form.valueId === null ? 'POST' : 'PUT', url, collect()));
      closeForm();
      say(tr('portal.text.saved'));
    } catch (e){
      // Ошибка — в окне: человек исправит поле, не открывая окно заново
      showError(e.message);
    }
  }

  function askDelete(){
    const form = state.form;
    if (!form || form.valueId === null) return;
    const r = rowsOf(form.enumKey).find(x => x.valueId === form.valueId);
    byId('enumValueConfirmText').textContent =
        tr('admin.confirm.delete_enum_value', {name: r ? r.name : form.valueId});
    byId('enumValueConfirm').style.display = '';
  }

  async function remove(){
    const form = state.form;
    if (!form || form.valueId === null) return;
    try {
      apply(await api('DELETE', '/api/admin/enum-values/' + encodeURIComponent(form.enumKey)
          + '/' + encodeURIComponent(form.valueId)));
      closeForm();
      say(tr('portal.text.deleted'));
    } catch (e){
      byId('enumValueConfirm').style.display = 'none';
      showError(e.message);
    }
  }

  /* ================= Установка ================= */

  // Смена языка: подписи перечислений и колонок — из перевода,
  // данные заново не нужны.
  window.renderEnumValues = () => { if (state.loaded) render(); };

  function install(){
    PORTAL.onSession(() => {
      if (!isAdmin()) return;
      createTab();
      state.loaded = false;
      state.rows = [];
      if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen('enumValues', load);
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
