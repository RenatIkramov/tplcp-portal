/*
 * Отчёты клиента (V138, V139; решения заказчика 14.11.2026):
 *   «Отчёты» на главной (место 01), «Биллинг» (02), карточка товара (06).
 *
 * В каждом месте — список отчётов с кнопкой «Сформировать» и таблица
 * запусков: статус, кто и когда запустил, «Скачать». WMS готовит отчёт
 * не сразу: строка появляется «В очереди / Формируется», таблица сама
 * перечитывается, пока есть незаконченные, и «Скачать» становится
 * активной, когда отчёт готов.
 *
 * «Сформировать» заблокирована, пока этот человек формирует этот отчёт
 * (решение 14.11.2026): чтобы не нажимали много раз. Сервер держит то же
 * правило (V139) — и для двух окон браузера.
 *
 * Отказ сервера в окне параметров — в самом окне: плашка портала
 * оказывается под окном и не видна (урок «Настройки отчётов», 14.11.2026).
 *
 * Демо прототипа (js/demo/reports.js, кнопка «Печать» в карточках
 * документов) удалено 14.11.2026: печатные формы приходят из WMS
 * файлами во вкладку «Прикреплённые файлы».
 */
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const {esc, dash, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);
  const api = PORTAL.api;

  /** Перечитывать таблицу, пока есть незаконченные запуски. */
  const REFRESH_MS = 5000;
  const OPEN = ['01', '02'];
  const DATE = '01';
  const NUMBER = '02';
  const DIRECTORY = ['04', '05', '06', '07', '08'];

  /**
   * Открытое место: где рисуем, какие отчёты, какие запуски.
   * @type {{host: HTMLElement | null, place: string, skuId: string | null,
   *         reports: any[], runs: any[], busy: Set<string>, timer: number | null,
   *         form: {report: any, values: Record<string, string>, error: string, sending: boolean} | null}}
   */
  const view = {host: null, place: '', skuId: null, reports: [], runs: [], busy: new Set(), timer: null, form: null};

  /** Значения справочников для выпадающих списков: тип → [{code, name}]. */
  /** @type {Record<string, {code: string, name: string}[]>} */
  let choices = {};

  const me = () => (PORTAL.session && PORTAL.session.login) || '';

  /** Этот человек уже формирует этот отчёт — кнопка заблокирована. */
  const running = (/** @type {string} */ reportId) => view.busy.has(reportId)
      || view.runs.some(r => r.reportId === reportId && r.requestedLogin === me() && OPEN.includes(r.statusCode));

  function when(iso){
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso);
    try {
      return d.toLocaleString(PORTAL.timeLocale(), {dateStyle: 'short', timeStyle: 'short'});
    } catch (e){
      return d.toLocaleString();
    }
  }

  /* ======================= Загрузка ======================= */

  function runsUrl(){
    return '/api/reports/runs?place=' + encodeURIComponent(view.place)
      + (view.skuId ? '&skuId=' + encodeURIComponent(view.skuId) : '');
  }

  async function load(){
    try {
      const [reports, runs] = await Promise.all([
        api('GET', '/api/reports?place=' + encodeURIComponent(view.place)),
        api('GET', runsUrl())
      ]);
      view.reports = reports || [];
      view.runs = runs || [];
    } catch (e){
      view.reports = [];
      view.runs = [];
      say(messageOf(e));
    }
    render();
    schedule();
  }

  async function reloadRuns(){
    try {
      view.runs = await api('GET', runsUrl()) || [];
    } catch (e){
      say(messageOf(e));
    }
    render();
    schedule();
  }

  /** Перечитать через REFRESH_MS, если есть незаконченные и место ещё на экране. */
  function schedule(){
    if (view.timer) window.clearTimeout(view.timer);
    view.timer = null;
    if (!view.runs.some(r => OPEN.includes(r.statusCode))) return;
    view.timer = window.setTimeout(() => {
      view.timer = null;
      const host = view.host;
      if (host && document.body.contains(host) && host.offsetParent !== null) reloadRuns();
    }, REFRESH_MS);
  }

  /* ======================= Разметка ======================= */

  function render(){
    const host = view.host;
    if (!host) return;
    host.innerHTML = ''
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('common.label.report_name')) + '</th><th></th>'
      + '</tr></thead><tbody data-reports-list>'
      + (view.reports.map(r => '<tr data-report="' + esc(r.reportId) + '"><td>' + esc(r.name) + '</td>'
          + '<td style="text-align:right;white-space:nowrap;">'
          + '<button type="button" class="btn primary" data-report-run' + (running(r.reportId) ? ' disabled' : '')
          + PORTAL.call('PORTAL.clientReports.open', r.reportId) + '>'
          + esc(T_(running(r.reportId) ? 'reports.button.producing' : 'common.button.generate_report')) + '</button>'
          + '</td></tr>').join('')
        || '<tr><td colspan="2" style="color:var(--muted);">' + esc(T_('reports.text.no_reports')) + '</td></tr>')
      + '</tbody></table></div>'
      + '<div class="section-title">' + esc(T_('reports.title.finished')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 10px;">' + esc(T_('reports.hint.finished')) + '</div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('common.label.report_name')) + '</th>'
      + '<th>' + esc(T_('admin.column.parameters')) + '</th>'
      + '<th>' + esc(T_('reports.column.requested_by')) + '</th>'
      + '<th>' + esc(T_('reports.column.requested_at')) + '</th>'
      + '<th>' + esc(T_('reports.column.status')) + '</th><th></th>'
      + '</tr></thead><tbody data-reports-runs>'
      + (view.runs.map(runRow).join('')
        || '<tr><td colspan="6" style="color:var(--muted);">' + esc(T_('reports.text.no_runs')) + '</td></tr>')
      + '</tbody></table></div>';
  }

  /** @param {any} r */
  function runRow(r){
    const badge = r.statusCode === '03' ? 'ok' : r.statusCode === '04' ? 'err' : 'warn';
    // подпись параметра, если человек её видел в окне; иначе — код WMS
    const report = view.reports.find(x => x.reportId === r.reportId);
    const labelOf = (/** @type {string} */ k) =>
        ((report && report.params || []).find((/** @type {any} */ p) => p.key === k) || {label: k}).label;
    const params = Object.entries(r.params || {})
        .map(([k, v]) => '<span class="count">' + esc(labelOf(k)) + '</span> ' + esc(v)).join('<br>');
    const error = r.statusCode === '04'
      ? '<div class="hint" style="color:var(--err-fg);">'
        + esc(r.errorKey ? T_(r.errorKey) : '') + (r.errorDetail ? ' ' + esc(r.errorDetail) : '') + '</div>'
      : '';
    return '<tr data-run="' + esc(r.runId) + '" data-run-report="' + esc(r.reportId)
      + '" data-status="' + esc(r.statusCode) + '">'
      + '<td>' + esc(r.reportName) + '</td>'
      + '<td style="font-size:12px;">' + (params || '—') + '</td>'
      + '<td>' + esc(dash(r.requestedName || r.requestedLogin)) + '</td>'
      + '<td style="white-space:nowrap;">' + esc(when(r.requestedAt)) + '</td>'
      + '<td><span class="badge ' + badge + '"><span class="dot"></span>' + esc(r.statusName) + '</span>' + error + '</td>'
      + '<td style="text-align:right;"><button type="button" class="btn ghost" data-run-download'
      + (r.statusCode === '03' ? '' : ' disabled')
      + PORTAL.call('PORTAL.clientReports.download', r.runId) + '>' + esc(T_('reports.button.download')) + '</button></td>'
      + '</tr>';
  }

  /* ======================= Окно параметров ======================= */

  function overlay(){
    let host = document.getElementById('reportRunOverlay');
    if (!host){
      host = document.createElement('div');
      host.className = 'col-overlay';
      host.id = 'reportRunOverlay';
      host.style.alignItems = 'center';
      host.style.justifyContent = 'center';
      host.setAttribute('data-backdrop', 'PORTAL.clientReports.close');
      document.body.appendChild(host);
    }
    return host;
  }

  /** Значение, с которым поле открывается: текущая дата или умолчание из каталога. */
  function initial(/** @type {any} */ p){
    if (p.useCurrentDate) return new Date().toISOString().slice(0, 10);
    return p.defaultValue || '';
  }

  /** @param {any} p */
  function fieldHtml(p){
    const f = /** @type {NonNullable<typeof view.form>} */ (view.form);
    const value = f.values[p.key] || '';
    const id = 'reportParam_' + p.key.replace(/[^A-Za-z0-9_-]/g, '_');
    const label = '<label for="' + esc(id) + '">' + esc(p.label) + (p.required ? ' *' : '') + '</label>';
    const input = PORTAL.callOnInput('PORTAL.clientReports.value', p.key, PORTAL.VALUE);
    if (DIRECTORY.includes(p.type)){
      const list = choices[p.type] || [];
      return '<div class="kv">' + label + '<select class="kv-input kv-input-editable" id="' + esc(id) + '"'
        + ' data-param="' + esc(p.key) + '"' + PORTAL.callOnChange('PORTAL.clientReports.value', p.key, PORTAL.VALUE) + '>'
        + '<option value="">' + esc(T_('common.option.not_selected')) + '</option>'
        + list.map(c => '<option value="' + esc(c.code) + '"' + (c.code === value ? ' selected' : '') + '>'
            + esc(c.name) + ' (' + esc(c.code) + ')</option>').join('')
        + '</select></div>';
    }
    const type = p.type === DATE ? 'date' : p.type === NUMBER ? 'number' : 'text';
    return '<div class="kv">' + label + '<input type="' + type + '" class="kv-input kv-input-editable" id="' + esc(id) + '"'
      + ' data-param="' + esc(p.key) + '" value="' + esc(value) + '"' + input + '></div>';
  }

  function renderForm(){
    const f = view.form;
    if (!f) return;
    const host = overlay();
    host.innerHTML = '<div class="col-panel" style="width:440px;max-width:96vw;box-shadow:0 8px 32px rgba(0,0,0,.24);'
      + 'border-radius:10px;max-height:90vh;overflow-y:auto;">'
      + '<h3 style="font-size:16px;">' + esc(f.report.name) + '</h3>'
      + '<div class="hint">' + esc(T_('reports.hint.parameters')) + '</div>'
      + '<div style="display:flex;flex-direction:column;gap:14px;margin-top:12px;">'
      + f.report.params.map(fieldHtml).join('') + '</div>'
      + (f.error ? '<div class="form-error" id="reportRunError" role="alert" style="margin-top:14px;color:#c0392b;'
          + 'background:#fdecea;border:1px solid #f5c6cb;border-radius:6px;padding:8px 10px;">' + esc(f.error) + '</div>' : '')
      + '<div class="col-actions">'
      + '<button type="button" class="btn primary" id="reportRunSubmit" style="flex:1;"' + (f.sending ? ' disabled' : '')
      + PORTAL.call('PORTAL.clientReports.submit') + '>' + esc(T_('common.button.generate_report')) + '</button>'
      + '<button type="button" class="btn ghost"' + PORTAL.call('PORTAL.clientReports.close') + '>'
      + esc(T_('common.button.cancel')) + '</button></div></div>';
    host.classList.add('active');
  }

  async function start(/** @type {any} */ report, /** @type {Record<string, string>} */ values){
    view.busy.add(report.reportId);
    render();
    try {
      await api('POST', '/api/reports/' + encodeURIComponent(report.reportId) + '/runs',
          {values: values, skuId: view.skuId});
    } finally {
      view.busy.delete(report.reportId);
    }
    say(T_('reports.message.started'));
    await reloadRuns();
  }

  /* ======================= Действия ======================= */

  const actions = {
    async open(reportId){
      const report = view.reports.find(r => r.reportId === reportId);
      if (!report || running(reportId)) return;
      if (!report.params.length){
        // параметров нет — спрашивать нечего, сразу в работу
        try { await start(report, {}); } catch (e){ say(messageOf(e)); render(); }
        return;
      }
      const types = [...new Set(report.params.map((/** @type {any} */ p) => p.type).filter((/** @type {string} */ t) =>
          DIRECTORY.includes(t) && !choices[t]))];
      try {
        await Promise.all(types.map(async t => {
          choices[t] = await api('GET', '/api/reports/choices?type=' + encodeURIComponent(t)) || [];
        }));
      } catch (e){ say(messageOf(e)); return; }
      /** @type {Record<string, string>} */
      const values = {};
      report.params.forEach((/** @type {any} */ p) => { values[p.key] = initial(p); });
      view.form = {report, values, error: '', sending: false};
      renderForm();
    },

    value(key, value){ if (view.form) view.form.values[key] = value; },

    close(){
      view.form = null;
      document.getElementById('reportRunOverlay')?.classList.remove('active');
    },

    async submit(){
      const f = view.form;
      if (!f || f.sending) return;
      f.sending = true;
      f.error = '';
      renderForm();
      try {
        await start(f.report, f.values);
      } catch (e){
        f.sending = false;
        f.error = messageOf(e);
        renderForm();
        render();
        return;
      }
      actions.close();
    },

    async download(runId){
      try {
        await PORTAL.downloadFile('/api/reports/runs/' + encodeURIComponent(runId) + '/file', 'report');
      } catch (e){
        // отказ — чаще всего запуск уже убран по сроку: показать и обновить список
        say(messageOf(e));
        await reloadRuns();
      }
    }
  };

  PORTAL.clientReports = Object.assign(actions, {
    /**
     * Нарисовать отчёты места {@code place} в host; {@code skuId} — карточка
     * товара: отчёты по этому товару и его запуски.
     * @param {HTMLElement} host
     * @param {{place: string, skuId?: string | null}} where
     */
    show(host, where){
      view.host = host;
      view.place = where.place;
      view.skuId = where.skuId || null;
      view.reports = [];
      view.runs = [];
      host.innerHTML = '<div class="hint">' + esc(T_('stock.text.loading_data')) + '</div>';
      load();
    }
  });

  /** Смена языка: перерисовать открытое (i18n.js зовёт по имени). */
  window.renderClientReports = function(){
    choices = {};
    if (view.host && document.body.contains(view.host)) load();
    if (view.form) renderForm();
  };

  function install(){
    // «Отчёты» на главной и «Биллинг» — свои контейнеры в index.html.
    PORTAL.beforeTab(tab => {
      if (tab === 'reports'){
        const host = document.getElementById('reportsHost');
        if (host) window.setTimeout(() => PORTAL.clientReports && PORTAL.clientReports.show(host, {place: '01'}), 0);
      }
      if (tab === 'billing'){
        const host = document.getElementById('billingReportsHost');
        if (host) window.setTimeout(() => PORTAL.clientReports && PORTAL.clientReports.show(host, {place: '02'}), 0);
      }
      return true;
    });
    PORTAL.onSession(() => {
      choices = {};
      view.host = null;
      if (view.timer) window.clearTimeout(view.timer);
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
