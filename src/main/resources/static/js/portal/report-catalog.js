/*
 * Каталог отчётов WMS (V135, решение 13.11.2026):
 *   Администрирование → «Настройка отчётов» — общий каталог: отчёт,
 *       его код в WMS, где показывать кнопку, параметры, переводы;
 *   карточка поклажедателя → вкладка «Отчёты» — какие отчёты выданы
 *       клиенту, под каким своим названием, в каком порядке.
 *
 * Разметку целиком рисует этот файл: в index.html — пустой контейнер.
 * Демо прототипа (настройка в памяти страницы) удалено 14.11.2026.
 *
 * Поля окна пишут в state.form по вводу и не перерисовывают окно —
 * иначе курсор терялся бы на каждой букве. Перерисовка — только когда
 * меняется состав: добавили, убрали, переставили параметр, сменили тип.
 */
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const {esc, dash, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);
  const api = PORTAL.api;

  /** Тип «дата»: только у него бывает «текущая дата». */
  const DATE = '01';

  /**
   * @typedef {{key: string, label: string, type: string, required: boolean,
   *            defaultValue: string, useCurrentDate: boolean, hidden: boolean,
   *            texts: Record<string, string>}} ReportParamForm
   * @typedef {{reportId: string | null, wmsCode: string, name: string, place: string,
   *            sortOrder: number, active: boolean, texts: Record<string, string>,
   *            params: ReportParamForm[]}} ReportForm
   * @typedef {{code: string, name: string}} CodeName
   */

  /**
   * @type {{options: {places: CodeName[], paramTypes: CodeName[], setByPortal: string[],
   *                   locales: CodeName[]} | null,
   *         rows: any[], place: string, form: ReportForm | null, confirmDelete: boolean, error: string,
   *         retention: number | null}}
   */
  const state = {options: null, rows: [], place: '', form: null, confirmDelete: false, error: '', retention: null};

  const setByPortal = (/** @type {string} */ type) =>
      !!state.options && state.options.setByPortal.includes(type);

  async function options(){
    if (!state.options) state.options = await api('GET', '/api/admin/report-options');
    return state.options;
  }

  /* ======================= Каталог ======================= */

  function prepare(){
    const panel = document.getElementById('admin-sub-reports-config');
    if (!panel || panel.dataset.wired) return;
    panel.dataset.wired = '1';
    panel.innerHTML = '';
  }

  async function load(){
    try {
      await options();
      state.rows = await api('GET', '/api/admin/reports') || [];
      // срок хранения готовых отчётов (V139) — здесь же, где отчёты настраивают
      state.retention = (await api('GET', '/api/admin/report-settings') || {}).retentionDays ?? null;
    } catch (e){
      state.rows = [];
      say(messageOf(e));
    }
    render();
  }

  function render(){
    const panel = document.getElementById('admin-sub-reports-config');
    if (!panel || !state.options) return;
    const places = state.options.places;
    const rows = state.rows.filter(r => !state.place || r.placeCode === state.place);
    panel.innerHTML = ''
      + '<div class="panel-head" style="margin-bottom:0;">'
      + '<div class="section-title" style="margin:0;">' + esc(T_('admin.button.report_settings')) + '</div>'
      + '<div class="actions"><button type="button" class="btn primary" id="reportAdd"'
      + PORTAL.call('PORTAL.reportCatalog.add') + '>' + esc(T_('admin.button.add_report')) + '</button></div>'
      + '</div>'
      + '<div class="hint" style="margin:6px 0 12px;">' + esc(T_('reports.hint.catalog')) + '</div>'
      + '<div class="filters" id="reportRetention"><div class="fgroup">'
      + '<label for="reportRetentionDays">' + esc(T_('reports.label.retention_days')) + '</label>'
      + '<input type="number" min="1" max="365" step="1" class="kv-input kv-input-editable" id="reportRetentionDays"'
      + ' style="width:110px;" value="' + esc(state.retention ?? '') + '"'
      + PORTAL.callOnInput('PORTAL.reportCatalog.retentionSet', PORTAL.VALUE) + '></div>'
      + '<button type="button" class="btn" id="reportRetentionSave"'
      + PORTAL.call('PORTAL.reportCatalog.retentionSave') + '>' + esc(T_('common.button.save')) + '</button>'
      + '<div class="spacer"></div></div>'
      + '<div class="filters"><div class="fgroup">'
      + '<label for="reportPlaceFilter">' + esc(T_('admin.column.location')) + '</label>'
      + '<select id="reportPlaceFilter"' + PORTAL.callOnChange('PORTAL.reportCatalog.filter', PORTAL.VALUE) + '>'
      + '<option value="">' + esc(T_('common.option.all')) + '</option>'
      + places.map(p => '<option value="' + esc(p.code) + '"' + (p.code === state.place ? ' selected' : '') + '>'
          + esc(p.name) + '</option>').join('')
      + '</select></div><div class="spacer"></div></div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('admin.column.location')) + '</th>'
      + '<th class="num">' + esc(T_('reports.column.order')) + '</th>'
      + '<th>' + esc(T_('admin.column.report_name')) + '</th>'
      + '<th>' + esc(T_('admin.column.wms_report_code')) + '</th>'
      + '<th class="num">' + esc(T_('admin.column.parameters')) + '</th>'
      + '<th class="num">' + esc(T_('reports.column.clients')) + '</th>'
      + '<th>' + esc(T_('reports.column.active')) + '</th>'
      + '</tr></thead><tbody id="reportCatalogBody">'
      + (rows.map(r => '<tr class="clickable" data-report="' + esc(r.reportId) + '"'
            + PORTAL.call('PORTAL.reportCatalog.edit', r.reportId) + '>'
          + '<td>' + esc(dash(r.placeName)) + '</td>'
          + '<td class="num">' + esc(dash(r.sortOrder)) + '</td>'
          + '<td>' + esc(r.name) + '</td>'
          + '<td><code>' + esc(r.wmsCode) + '</code></td>'
          + '<td class="num">' + esc(r.paramCount) + '</td>'
          + '<td class="num">' + esc(r.depositorCount) + '</td>'
          + '<td>' + esc(PORTAL.yesNo(r.isActive)) + '</td></tr>').join('')
        || '<tr><td colspan="7" style="color:var(--muted);">' + esc(T_('reports.text.catalog_empty')) + '</td></tr>')
      + '</tbody></table></div>';
  }

  /* ======================= Окно отчёта ======================= */

  /** @returns {ReportParamForm} */
  const emptyParam = () => ({key: '', label: '', type: DATE, required: false, defaultValue: '',
    useCurrentDate: false, hidden: false, texts: {}});

  function overlay(){
    let host = document.getElementById('reportEditOverlay');
    if (!host){
      host = document.createElement('div');
      host.className = 'col-overlay';
      host.id = 'reportEditOverlay';
      host.style.alignItems = 'center';
      host.style.justifyContent = 'center';
      host.setAttribute('data-backdrop', 'PORTAL.reportCatalog.close');
      document.body.appendChild(host);
    }
    return host;
  }

  /** @param {string} key @param {string} id @param {string} value @param {string} call @param {...any} args */
  function field(key, id, value, call, ...args){
    return '<div class="kv"><label for="' + id + '">' + esc(key) + '</label>'
      + '<input type="text" class="kv-input kv-input-editable" id="' + id + '" value="' + esc(value || '') + '"'
      + PORTAL.callOnInput(call, ...args, PORTAL.VALUE) + '></div>';
  }

  function renderForm(){
    const f = state.form;
    const o = state.options;
    if (!f || !o) return;
    const host = overlay();
    const locales = o.locales;
    const typeOptions = (/** @type {string} */ current) => o.paramTypes.map(t =>
        '<option value="' + esc(t.code) + '"' + (t.code === current ? ' selected' : '') + '>' + esc(t.name) + '</option>').join('');

    const params = f.params.map((p, i) => {
      const portal = setByPortal(p.type);
      const flag = (/** @type {string} */ name, /** @type {boolean} */ on, /** @type {string} */ key) =>
          '<label style="display:inline-flex;gap:6px;align-items:center;font-size:13px;">'
          + '<input type="checkbox" data-param-flag="' + name + '"' + (on ? ' checked' : '')
          + PORTAL.callOnChange('PORTAL.reportCatalog.paramFlag', i, name, PORTAL.CHECKED) + '> '
          + esc(T_(key)) + '</label>';
      return '<div class="report-param" data-param="' + i + '"'
        + ' style="border:1px solid var(--line);border-radius:8px;padding:10px;display:flex;flex-direction:column;gap:8px;">'
        + '<div style="display:grid;grid-template-columns:1fr 1.4fr 1fr auto;gap:8px;align-items:end;">'
        + '<div><label class="hint">' + esc(T_('reports.column.param_key')) + '</label>'
        + '<input type="text" class="kv-input kv-input-editable" data-param-field="key" value="' + esc(p.key) + '"'
        + PORTAL.callOnInput('PORTAL.reportCatalog.paramSet', i, 'key', PORTAL.VALUE) + '></div>'
        + '<div><label class="hint">' + esc(T_('reports.column.param_label')) + '</label>'
        + '<input type="text" class="kv-input kv-input-editable" data-param-field="label" value="' + esc(p.label) + '"'
        + PORTAL.callOnInput('PORTAL.reportCatalog.paramSet', i, 'label', PORTAL.VALUE) + '></div>'
        + '<div><label class="hint">' + esc(T_('reports.column.param_type')) + '</label>'
        + '<select class="kv-input kv-input-editable" data-param-field="type"'
        + PORTAL.callOnChange('PORTAL.reportCatalog.paramType', i, PORTAL.VALUE) + '>' + typeOptions(p.type) + '</select></div>'
        + '<div style="display:flex;gap:4px;">'
        + '<button type="button" class="btn ghost" title="' + esc(T_('reports.button.move_up')) + '"'
        + (i === 0 ? ' disabled' : '') + PORTAL.call('PORTAL.reportCatalog.paramMove', i, -1) + '>↑</button>'
        + '<button type="button" class="btn ghost" title="' + esc(T_('reports.button.move_down')) + '"'
        + (i === f.params.length - 1 ? ' disabled' : '') + PORTAL.call('PORTAL.reportCatalog.paramMove', i, 1) + '>↓</button>'
        + '<button type="button" class="btn ghost" data-param-remove title="' + esc(T_('reports.button.remove_param')) + '"'
        + PORTAL.call('PORTAL.reportCatalog.paramRemove', i) + '>✕</button></div></div>'
        + (portal
          ? '<div class="hint">' + esc(T_('reports.text.set_by_portal')) + '</div>'
          : '<div style="display:flex;gap:14px;flex-wrap:wrap;align-items:center;">'
            + flag('required', p.required, 'reports.label.required')
            + (p.type === DATE ? flag('useCurrentDate', p.useCurrentDate, 'reports.label.current_date') : '')
            + flag('hidden', p.hidden, 'reports.label.hidden')
            + (p.hidden ? '<span class="hint">' + esc(T_('reports.hint.hidden_value')) + '</span>' : '')
            + '<input type="' + (p.type === DATE ? 'date' : p.type === '02' ? 'number' : 'text') + '"'
            + ' class="kv-input kv-input-editable" data-param-field="defaultValue" style="max-width:220px;"'
            + ' placeholder="' + esc(T_('reports.column.default')) + '" value="' + esc(p.defaultValue) + '"'
            + (p.useCurrentDate ? ' disabled' : '')
            + PORTAL.callOnInput('PORTAL.reportCatalog.paramSet', i, 'defaultValue', PORTAL.VALUE) + '></div>')
        + (locales.length
          ? '<div style="display:flex;gap:8px;flex-wrap:wrap;">' + locales.map(l =>
              '<input type="text" class="kv-input kv-input-editable" style="max-width:260px;"'
              + ' data-param-text="' + esc(l.code) + '" placeholder="' + esc(T_('reports.column.param_label') + ' — ' + l.name) + '"'
              + ' value="' + esc(p.texts[l.code] || '') + '"'
              + PORTAL.callOnInput('PORTAL.reportCatalog.paramText', i, l.code, PORTAL.VALUE) + '>').join('') + '</div>'
          : '')
        + '</div>';
    }).join('');

    host.innerHTML = '<div class="col-panel" style="width:760px;max-width:96vw;box-shadow:0 8px 32px rgba(0,0,0,.24);'
      + 'border-radius:10px;max-height:90vh;overflow-y:auto;">'
      + '<h3 style="font-size:16px;">' + esc(T_(f.reportId ? 'reports.title.edit' : 'reports.title.new')) + '</h3>'
      + '<div style="display:flex;flex-direction:column;gap:12px;margin-top:12px;">'
      + field(T_('admin.column.wms_report_code'), 'reportWmsCode', f.wmsCode, 'PORTAL.reportCatalog.set', 'wmsCode')
      + field(T_('admin.label.base_name'), 'reportName', f.name, 'PORTAL.reportCatalog.set', 'name')
      + locales.map(l => field(T_('admin.column.report_name') + ' — ' + l.name, 'reportName_' + l.code,
          f.texts[l.code] || '', 'PORTAL.reportCatalog.text', l.code)).join('')
      + '<div class="kv"><label for="reportPlace">' + esc(T_('admin.column.location')) + '</label>'
      + '<select class="kv-input kv-input-editable" id="reportPlace"'
      + PORTAL.callOnChange('PORTAL.reportCatalog.set', 'place', PORTAL.VALUE) + '>'
      + o.places.map(p => '<option value="' + esc(p.code) + '"' + (p.code === f.place ? ' selected' : '') + '>'
          + esc(p.name) + '</option>').join('') + '</select></div>'
      + '<div class="kv"><label for="reportOrder">' + esc(T_('reports.column.order')) + '</label>'
      + '<input type="number" step="1" class="kv-input kv-input-editable" id="reportOrder" value="' + esc(f.sortOrder) + '"'
      + PORTAL.callOnInput('PORTAL.reportCatalog.set', 'sortOrder', PORTAL.VALUE) + '></div>'
      + '<label style="display:inline-flex;gap:6px;align-items:center;">'
      + '<input type="checkbox" id="reportActive"' + (f.active ? ' checked' : '')
      + PORTAL.callOnChange('PORTAL.reportCatalog.set', 'active', PORTAL.CHECKED) + '> '
      + esc(T_('reports.column.active')) + '</label>'
      + '<div class="line-info-title" style="padding-top:4px;">' + esc(T_('admin.column.parameters')) + '</div>'
      + '<div class="hint" style="margin-top:-6px;">' + esc(T_('reports.hint.params')) + '</div>'
      + '<div id="reportParams" style="display:flex;flex-direction:column;gap:10px;">'
      + (params || '<div class="hint">' + esc(T_('reports.text.no_params')) + '</div>') + '</div>'
      + '<div><button type="button" class="btn ghost" id="reportParamAdd"'
      + PORTAL.call('PORTAL.reportCatalog.paramAdd') + '>' + esc(T_('reports.button.add_param')) + '</button></div>'
      + '</div>'
      // Отказ сервера — здесь, в окне: плашка портала оказывается под ним
      // и не видна (заказчик 14.11.2026: «Сохранить не работает»).
      + (state.error ? '<div class="form-error" id="reportError" role="alert" style="margin-top:14px;color:#c0392b;'
          + 'background:#fdecea;border:1px solid #f5c6cb;border-radius:6px;padding:8px 10px;">'
          + esc(state.error) + '</div>' : '')
      + (state.confirmDelete
        ? '<div class="hint" style="margin-top:14px;">' + esc(T_('reports.text.delete_confirm')) + '</div>'
          + '<div class="col-actions">'
          + '<button type="button" class="btn primary" id="reportDeleteYes" style="flex:1;background:#c0392b;border-color:#c0392b;"'
          + PORTAL.call('PORTAL.reportCatalog.remove') + '>' + esc(T_('common.button.yes_delete')) + '</button>'
          + '<button type="button" class="btn ghost"' + PORTAL.call('PORTAL.reportCatalog.deleteAsk', false) + '>'
          + esc(T_('common.button.cancel')) + '</button></div>'
        : '<div class="col-actions">'
          + '<button type="button" class="btn primary" id="reportSave" style="flex:1;"'
          + PORTAL.call('PORTAL.reportCatalog.save') + '>' + esc(T_('common.button.save')) + '</button>'
          + (f.reportId ? '<button type="button" class="btn ghost" id="reportDelete"'
              + PORTAL.call('PORTAL.reportCatalog.deleteAsk', true) + '>' + esc(T_('reports.button.delete')) + '</button>' : '')
          + '<button type="button" class="btn ghost"' + PORTAL.call('PORTAL.reportCatalog.close') + '>'
          + esc(T_('common.button.cancel')) + '</button></div>')
      + '</div>';
    host.classList.add('active');
  }

  /** @param {any} card @returns {ReportForm} */
  function formOf(card){
    return {
      reportId: card.reportId, wmsCode: card.wmsCode || '', name: card.name || '',
      place: card.placeCode, sortOrder: card.sortOrder || 0, active: card.isActive !== false,
      texts: Object.assign({}, card.texts || {}),
      params: (card.params || []).map((/** @type {any} */ p) => ({
        key: p.key || '', label: p.label || '', type: p.type, required: !!p.required,
        defaultValue: p.defaultValue || '', useCurrentDate: !!p.useCurrentDate, hidden: !!p.hidden,
        texts: Object.assign({}, p.texts || {})}))
    };
  }

  /** @param {ReportForm} f */
  function body(f){
    return {
      wmsCode: f.wmsCode, name: f.name, place: f.place, sortOrder: Number(f.sortOrder) || 0, active: f.active,
      texts: f.texts,
      params: f.params.map(p => ({
        key: p.key, label: p.label, type: p.type, required: p.required,
        defaultValue: p.useCurrentDate ? null : p.defaultValue, useCurrentDate: p.useCurrentDate,
        hidden: p.hidden, texts: p.texts}))
    };
  }

  /* ======================= Отчёты клиента ======================= */

  /**
   * Вкладка показывает только выданные клиенту отчёты; новый добавляется
   * кнопкой «+ Добавить отчёт» из тех, что в каталоге есть, а клиенту ещё
   * не выданы (заказчик 14.11.2026: «у поклажедателя нет кнопки добавить
   * отчёт»). Под строкой отчёта — его скрытые параметры: значение для
   * этого клиента (V137), пусто — уходит значение по умолчанию из каталога.
   * @type {{depositorId: string | null, rows: any[], host: HTMLElement | null,
   *         picking: boolean, error: string}}
   */
  const mine = {depositorId: null, rows: [], host: null, picking: false, error: ''};

  async function loadDepositor(depositorId){
    mine.rows = await api('GET', '/api/admin/depositor-directory/' + encodeURIComponent(depositorId) + '/reports') || [];
    mine.depositorId = depositorId;
    mine.picking = false;
    mine.error = '';
  }

  const placeName = (/** @type {string} */ code) => state.options
      ? (state.options.places.find(p => p.code === code) || {name: code}).name : code;

  /** @param {any} r @param {number} i */
  function hiddenRow(r, i){
    const hidden = /** @type {any[]} */ (r.hiddenParams || []);
    if (!hidden.length) return '';
    const values = r.paramValues || {};
    return '<tr class="report-hidden" data-report-hidden="' + esc(r.reportId) + '"><td></td><td colspan="5">'
      + '<div class="hint" style="margin:0 0 6px;">' + esc(T_('reports.label.hidden_values')) + '</div>'
      + '<div style="display:flex;gap:12px;flex-wrap:wrap;">'
      + hidden.map(h => '<label style="display:flex;flex-direction:column;gap:4px;font-size:13px;">'
          + '<span>' + esc(h.label) + ' <span class="count">' + esc(h.key) + '</span></span>'
          // «текущая дата» — значение подставится само, вводить нечего
          + (h.useCurrentDate
            ? '<span class="hint" style="padding:8px 0;">' + esc(T_('reports.label.current_date')) + '</span>'
            : '<input type="' + (h.type === DATE ? 'date' : h.type === '02' ? 'number' : 'text') + '"'
              + ' class="kv-input kv-input-editable" style="min-width:200px;" data-hidden-key="' + esc(h.key) + '"'
              + ' placeholder="' + esc(h.defaultValue || '') + '" value="' + esc(values[h.key] || '') + '"'
              + PORTAL.callOnInput('PORTAL.reportCatalog.depositorValue', i, h.key, PORTAL.VALUE) + '>'
              // поле даты подсказку не показывает — умолчание из каталога строкой
              + (h.defaultValue ? '<span class="hint">' + esc(T_('reports.text.default_is', {value: h.defaultValue})) + '</span>' : ''))
          + '</label>').join('')
      + '</div></td></tr>';
  }

  function renderDepositor(){
    const host = mine.host;
    if (!host) return;
    const assigned = mine.rows.map((r, i) => ({r, i})).filter(x => x.r.assigned);
    const free = mine.rows.filter(r => !r.assigned);
    host.innerHTML = '<div class="panel-head" style="margin-bottom:8px;">'
      + '<div class="hint" style="margin:0;">' + esc(T_('reports.hint.depositor')) + '</div>'
      + '<div class="actions"><button type="button" class="btn primary" id="depositorReportAdd"'
      + (free.length ? '' : ' disabled title="' + esc(T_('reports.text.all_assigned')) + '"')
      + PORTAL.call('PORTAL.reportCatalog.depositorPick', true) + '>' + esc(T_('reports.button.add_to_client')) + '</button></div>'
      + '</div>'
      + (mine.picking && free.length
        ? '<div class="filters" id="depositorReportPicker"><div class="fgroup">'
          + '<label for="depositorReportChoice">' + esc(T_('reports.label.choose_report')) + '</label>'
          + '<select id="depositorReportChoice"' + PORTAL.callOnChange('PORTAL.reportCatalog.depositorAdd', PORTAL.VALUE) + '>'
          + '<option value=""></option>'
          + free.map(r => '<option value="' + esc(r.reportId) + '">'
              + esc(placeName(r.placeCode) + ' · ' + r.name + ' (' + r.wmsCode + ')') + '</option>').join('')
          + '</select></div>'
          + '<button type="button" class="btn ghost"' + PORTAL.call('PORTAL.reportCatalog.depositorPick', false) + '>'
          + esc(T_('common.button.cancel')) + '</button><div class="spacer"></div></div>'
        : '')
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('admin.column.location')) + '</th>'
      + '<th>' + esc(T_('admin.column.report_name')) + '</th>'
      + '<th>' + esc(T_('reports.column.custom_name')) + '</th>'
      + '<th class="num">' + esc(T_('reports.column.order')) + '</th>'
      + '<th>' + esc(T_('reports.column.active')) + '</th>'
      + '<th></th>'
      + '</tr></thead><tbody id="depositorReportsBody">'
      + (assigned.map(({r, i}) => '<tr data-report="' + esc(r.reportId) + '">'
            + '<td>' + esc(dash(placeName(r.placeCode))) + '</td>'
            + '<td>' + esc(r.name) + ' <span class="count">' + esc(r.wmsCode) + '</span>'
            + (r.reportActive ? '' : ' <span class="count">' + esc(T_('reports.text.off_in_catalog')) + '</span>') + '</td>'
            + '<td><input type="text" class="kv-input kv-input-editable" data-dr="customName" style="min-width:220px;"'
            + ' placeholder="' + esc(r.name) + '" value="' + esc(r.customName || '') + '"'
            + PORTAL.callOnInput('PORTAL.reportCatalog.depositorSet', i, 'customName', PORTAL.VALUE) + '></td>'
            + '<td class="num"><input type="number" step="1" class="kv-input kv-input-editable" data-dr="sortOrder"'
            + ' style="width:80px;" value="' + esc(r.sortOrder) + '"'
            + PORTAL.callOnInput('PORTAL.reportCatalog.depositorSet', i, 'sortOrder', PORTAL.VALUE) + '></td>'
            + '<td><input type="checkbox" data-dr="isActive"' + (r.isActive ? ' checked' : '')
            + PORTAL.callOnChange('PORTAL.reportCatalog.depositorSet', i, 'isActive', PORTAL.CHECKED) + '></td>'
            + '<td><button type="button" class="btn ghost" data-dr="remove" title="' + esc(T_('reports.button.remove_from_client')) + '"'
            + PORTAL.call('PORTAL.reportCatalog.depositorRemove', i) + '>✕</button></td></tr>'
            + hiddenRow(r, i)).join('')
        || '<tr><td colspan="6" style="color:var(--muted);">' + esc(T_('reports.text.none_assigned')) + '</td></tr>')
      + '</tbody></table></div>'
      + (mine.error ? '<div class="form-error" id="depositorReportsError" role="alert" style="margin-top:12px;color:#c0392b;'
          + 'background:#fdecea;border:1px solid #f5c6cb;border-radius:6px;padding:8px 10px;">'
          + esc(mine.error) + '</div>' : '')
      + '<div class="col-actions" style="justify-content:flex-start;margin-top:12px;">'
      + '<button type="button" class="btn primary" id="depositorReportsSave"'
      + PORTAL.call('PORTAL.reportCatalog.depositorSave') + '>' + esc(T_('common.button.save')) + '</button>'
      + '<button type="button" class="btn ghost" id="depositorReportsCancel"'
      + PORTAL.call('PORTAL.reportCatalog.depositorCancel') + '>' + esc(T_('common.button.cancel')) + '</button></div>';
  }

  /* ======================= Действия ======================= */

  const actions = {
    filter(place){ state.place = place || ''; render(); },

    retentionSet(value){ state.retention = value === '' ? null : Number(value); },

    async retentionSave(){
      try {
        const answer = await api('PUT', '/api/admin/report-settings', {retentionDays: state.retention});
        state.retention = answer.retentionDays;
      } catch (e){ say(messageOf(e)); return; }
      say(T_('reports.message.retention_saved'));
    },

    async add(){
      try { await options(); } catch (e){ say(messageOf(e)); return; }
      const o = /** @type {NonNullable<typeof state.options>} */ (state.options);
      state.form = {reportId: null, wmsCode: '', name: '', place: state.place || (o.places[0] || {}).code,
        sortOrder: 0, active: true, texts: {}, params: []};
      state.confirmDelete = false;
      state.error = '';
      renderForm();
    },

    async edit(reportId){
      try {
        await options();
        state.form = formOf(await api('GET', '/api/admin/reports/' + encodeURIComponent(reportId)));
      } catch (e){ say(messageOf(e)); return; }
      state.confirmDelete = false;
      state.error = '';
      renderForm();
    },

    close(){
      state.form = null;
      state.confirmDelete = false;
      state.error = '';
      document.getElementById('reportEditOverlay')?.classList.remove('active');
    },

    set(name, value){
      if (!state.form) return;
      /** @type {any} */ (state.form)[name] = name === 'sortOrder' ? Number(value) || 0 : value;
    },
    text(code, value){ if (state.form) state.form.texts[code] = value; },

    paramAdd(){ if (state.form){ state.form.params.push(emptyParam()); renderForm(); } },
    paramRemove(i){ if (state.form){ state.form.params.splice(i, 1); renderForm(); } },
    paramMove(i, step){
      const list = state.form && state.form.params;
      if (!list || i + step < 0 || i + step >= list.length) return;
      const [p] = list.splice(i, 1);
      list.splice(i + step, 0, p);
      renderForm();
    },
    paramSet(i, name, value){
      const p = state.form && state.form.params[i];
      if (p) /** @type {any} */ (p)[name] = value;
    },
    paramText(i, code, value){
      const p = state.form && state.form.params[i];
      if (p) p.texts[code] = value;
    },
    paramType(i, type){
      const p = state.form && state.form.params[i];
      if (!p) return;
      p.type = type;
      if (type !== DATE) p.useCurrentDate = false;
      if (setByPortal(type)){ p.required = false; p.hidden = true; p.defaultValue = ''; }
      renderForm();
    },
    paramFlag(i, name, on){
      const p = state.form && state.form.params[i];
      if (!p) return;
      /** @type {any} */ (p)[name] = !!on;
      // «текущая дата» — значение уже есть, поле значения не нужно
      if (name === 'useCurrentDate') renderForm();
    },

    async save(){
      const f = state.form;
      if (!f) return;
      try {
        if (f.reportId) await api('PUT', '/api/admin/reports/' + encodeURIComponent(f.reportId), body(f));
        else await api('POST', '/api/admin/reports', body(f));
      } catch (e){ state.error = messageOf(e); renderForm(); return; }
      actions.close();
      say(T_('reports.message.saved'));
      await load();
    },

    deleteAsk(on){ state.confirmDelete = !!on; renderForm(); },

    async remove(){
      const f = state.form;
      if (!f || !f.reportId) return;
      try {
        await api('DELETE', '/api/admin/reports/' + encodeURIComponent(f.reportId));
      } catch (e){
        state.confirmDelete = false;
        state.error = messageOf(e);
        renderForm();
        return;
      }
      actions.close();
      say(T_('reports.message.deleted'));
      await load();
    },

    depositorSet(i, name, value){
      const r = mine.rows[i];
      if (r) r[name] = name === 'sortOrder' ? Number(value) || 0 : value;
    },

    depositorValue(i, key, value){
      const r = mine.rows[i];
      if (!r) return;
      r.paramValues = Object.assign({}, r.paramValues || {});
      r.paramValues[key] = value;
    },

    depositorPick(on){ mine.picking = !!on; renderDepositor(); },

    /** Выбрали отчёт в списке — он в таблице; сохраняется кнопкой «Сохранить». */
    depositorAdd(reportId){
      const r = mine.rows.find(x => x.reportId === reportId);
      if (!r) return;
      r.assigned = true;
      r.isActive = true;
      const max = Math.max(0, ...mine.rows.filter(x => x.assigned && x !== r).map(x => Number(x.sortOrder) || 0));
      r.sortOrder = max + 1;
      mine.picking = false;
      renderDepositor();
      /** @type {HTMLInputElement | null} */ (mine.host && mine.host.querySelector(
          'tr[data-report="' + CSS.escape(reportId) + '"] [data-dr="customName"]'))?.focus();
    },

    depositorRemove(i){
      const r = mine.rows[i];
      if (!r) return;
      r.assigned = false;
      renderDepositor();
    },

    async depositorSave(){
      const id = mine.depositorId;
      if (!id) return;
      const rows = mine.rows.filter(r => r.assigned).map(r => ({
        reportId: r.reportId, customName: r.customName || null, sortOrder: Number(r.sortOrder) || 0,
        active: r.isActive !== false, values: r.paramValues || {}}));
      try {
        mine.rows = await api('PUT', '/api/admin/depositor-directory/' + encodeURIComponent(id) + '/reports', rows) || [];
      } catch (e){
        mine.error = messageOf(e);
        renderDepositor();
        return;
      }
      mine.error = '';
      say(T_('reports.message.depositor_saved'));
      renderDepositor();
    },

    async depositorCancel(){
      if (!mine.depositorId) return;
      try { await loadDepositor(mine.depositorId); } catch (e){ say(messageOf(e)); return; }
      renderDepositor();
    }
  };

  PORTAL.reportCatalog = actions;

  /** Вкладка «Отчёты» карточки поклажедателя (portal-dir.js рисует ряд вкладок). */
  PORTAL.depositorReports = {
    TAB: {id: 'reports', key: 'reports.tab.reports'},
    async show(depositorId, hostEl){
      mine.host = hostEl;
      if (mine.depositorId !== depositorId){
        try {
          await options();
          await loadDepositor(depositorId);
        } catch (e){
          mine.depositorId = null;
          say(messageOf(e));
          return;
        }
      }
      renderDepositor();
    },
    leave(){ mine.host = null; },
    forget(){ mine.host = null; mine.depositorId = null; }
  };

  /** Смена языка: перерисовать открытое (i18n.js зовёт по имени). */
  window.renderReportCatalog = function(){
    state.options = null;
    const panel = document.getElementById('admin-sub-reports-config');
    if (panel && panel.classList.contains('active')) load();
    if (state.form) options().then(renderForm, e => say(messageOf(e)));
    if (mine.host) renderDepositor();
  };

  function install(){
    // admin-tabs.js зовёт и при выборе человеком, и когда сам открывает
    // первую вкладку группы — onAdminSub второго случая не видит.
    const open = () => { prepare(); load(); };
    if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen('reports-config', open);
    else PORTAL.onAdminSub(sub => { if (sub === 'reports-config') open(); });
    PORTAL.onSession(() => {
      state.options = null;
      state.rows = [];
      mine.depositorId = null;
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
