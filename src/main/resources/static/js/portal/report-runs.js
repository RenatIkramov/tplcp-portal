/*
  «Запуски отчётов» — администрирование, группа «Отчёты» (V149).

  Запуски отчётов всех клиентов, новые сверху: кто и когда запустил, с
  какими параметрами, по какому документу или товару, чем кончилось.
  Готовый отчёт можно скачать, у неудачного видна причина. У клиента
  те же запуски — в его разделах (reports.js), только свои.

  Отбор и страницы считает сервер (GET /api/admin/report-runs).
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, dash, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);

  const PANE = 'reports-runs';

  const emptyFilter = () => ({depositorId: '', reportId: '', status: '', from: '', to: ''});

  const state = {
    page: 1, pageSize: 50, total: 0,
    /** @type {any[]} */ rows: [],
    filter: emptyFilter()
  };

  const field = id => /** @type {HTMLInputElement | HTMLSelectElement | null} */ (document.getElementById(id));

  /* ================= Разметка ================= */

  function prepare(){
    const panel = document.getElementById('admin-sub-' + PANE);
    if (!panel || panel.dataset.wired) return;
    panel.dataset.wired = '1';

    const date = (id, key) => '<div class="fgroup"><label for="' + id + '">' + esc(T_(key)) + '</label>'
        + '<input type="date" id="' + id + '"></div>';
    const select = (id, key) => '<div class="fgroup"><label for="' + id + '">' + esc(T_(key)) + '</label>'
        + '<select id="' + id + '"><option value="">' + esc(T_('common.option.all')) + '</option></select></div>';
    panel.innerHTML = ''
      + '<div class="section-title" style="margin:0 0 6px;">' + esc(T_('admin.button.report_runs')) + '</div>'
      + '<div class="hint" style="margin:0 0 12px;font-size:13px;color:var(--muted);">'
      + esc(T_('reports.hint.runs')) + '</div>'
      + '<div class="filters">'
      + select('runsDepositor', 'reports.column.client')
      + select('runsReport', 'common.label.report_name')
      + select('runsStatus', 'reports.column.status')
      + date('runsFrom', 'audit.label.date_from')
      + date('runsTo', 'audit.label.date_to')
      + '<div class="fgroup" style="justify-content:flex-end;"><label>&nbsp;</label><div style="display:flex;gap:8px;">'
      + '<button class="btn primary" type="button" id="runsSearch">' + esc(T_('common.button.search')) + '</button>'
      + '<button class="btn" type="button" id="runsReset">' + esc(T_('common.button.reset')) + '</button>'
      + '</div></div>'
      + '<div class="spacer"></div></div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('reports.column.requested_at')) + '</th>'
      + '<th>' + esc(T_('reports.column.client')) + '</th>'
      + '<th>' + esc(T_('reports.column.requested_by')) + '</th>'
      + '<th>' + esc(T_('common.label.report_name')) + '</th>'
      + '<th>' + esc(T_('admin.column.parameters')) + '</th>'
      + '<th>' + esc(T_('reports.column.subject')) + '</th>'
      + '<th>' + esc(T_('reports.column.status')) + '</th>'
      + '<th>' + esc(T_('reports.column.finished_at')) + '</th><th></th>'
      + '</tr></thead><tbody id="runsBody"></tbody></table>'
      + '<div class="pagination-bar" id="runsPager"></div></div>';

    const deps = field('runsDepositor');
    if (deps) deps.innerHTML += (PORTAL.session && PORTAL.session.depositors || []).map(d =>
        '<option value="' + esc(d.depositorId) + '">' + esc(d.orgName) + '</option>').join('');

    // Поля — из отбора, который уже действует: панель собирается заново и
    // при смене языка, и отбор при этом не должен молча меняться.
    show('runsDepositor', state.filter.depositorId);
    show('runsFrom', state.filter.from);
    show('runsTo', state.filter.to);

    const byId = id => document.getElementById(id);
    const search = byId('runsSearch');
    if (search) search.onclick = () => { readFilter(); state.page = 1; load(); };
    const reset = byId('runsReset');
    if (reset) reset.onclick = () => {
      state.filter = emptyFilter();
      ['runsDepositor', 'runsReport', 'runsStatus', 'runsFrom', 'runsTo'].forEach(id => show(id, ''));
      state.page = 1;
      load();
    };
    const body = byId('runsBody');
    if (body) body.addEventListener('click', e => {
      const btn = /** @type {HTMLElement} */ (e.target).closest('[data-run-download]');
      if (btn && !(/** @type {HTMLButtonElement} */ (btn)).disabled) download(btn.getAttribute('data-run-download') || '');
    });
  }

  function show(/** @type {string} */ id, /** @type {string} */ v){
    const el = field(id);
    if (el) el.value = v;
  }

  function readFilter(){
    const v = id => { const el = field(id); return el ? el.value : ''; };
    state.filter = {depositorId: v('runsDepositor'), reportId: v('runsReport'), status: v('runsStatus'),
      from: v('runsFrom'), to: v('runsTo')};
  }

  /* ================= Данные ================= */

  async function load(){
    const q = new URLSearchParams({page: String(state.page), pageSize: String(state.pageSize)});
    Object.entries(state.filter).forEach(([k, v]) => { if (v) q.set(k, v); });
    try {
      const answer = await PORTAL.fetchJson('/api/admin/report-runs?' + q.toString());
      state.rows = answer.rows || [];
      state.total = answer.total || 0;
      state.page = answer.page || 1;
    } catch (e){
      state.rows = [];
      state.total = 0;
      say(messageOf(e));
    }
    render();
  }

  /** Отчёты каталога и статусы — для отборов; выбранное в них остаётся. */
  async function loadChoices(){
    try {
      const [reports, statuses] = await Promise.all([
        PORTAL.fetchJson('/api/admin/reports'),
        PORTAL.fetchJson('/api/admin/report-runs/statuses')]);
      const fill = (/** @type {string} */ id, /** @type {string} */ keep,
                    /** @type {Array<{value: string, text: string}>} */ items) => {
        const sel = field(id);
        if (!sel) return;
        sel.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
            + items.map(i => '<option value="' + esc(i.value) + '">' + esc(i.text) + '</option>').join('');
        sel.value = keep;
      };
      fill('runsReport', state.filter.reportId, (reports || []).map((/** @type {any} */ r) =>
          ({value: r.reportId, text: (r.name || r.wmsCode) + ' · ' + r.wmsCode})));
      fill('runsStatus', state.filter.status, (statuses || []).map((/** @type {any} */ s) =>
          ({value: s.code, text: s.name})));
    } catch (e){
      say(messageOf(e));
    }
  }

  async function download(/** @type {string} */ runId){
    try {
      await PORTAL.downloadFile('/api/admin/report-runs/' + encodeURIComponent(runId) + '/file', 'report');
    } catch (e){
      // чаще всего — запуск уже убран по сроку хранения: показать и обновить
      say(messageOf(e));
      load();
    }
  }

  /* ================= Отрисовка ================= */

  /** @param {any} r */
  function runRow(r){
    const badge = r.statusCode === '03' ? 'ok' : r.statusCode === '04' ? 'err' : 'warn';
    const params = Object.entries(r.params || {})
        .map(([k, v]) => '<span class="count">' + esc(k) + '</span> ' + esc(v)).join('<br>');
    const subject = r.documentNo ? T_('reports.text.document', {v: r.documentNo})
        : r.skuCode ? T_('reports.text.sku', {v: r.skuCode}) : '';
    const error = r.statusCode === '04'
      ? '<div class="hint" style="color:var(--err-fg);">'
        + esc(r.errorKey ? T_(r.errorKey) : '') + (r.errorDetail ? ' ' + esc(r.errorDetail) : '') + '</div>'
      : '';
    const ready = r.statusCode === '03' && r.hasFile;
    return '<tr data-run="' + esc(r.runId) + '" data-status="' + esc(r.statusCode) + '">'
      + '<td style="white-space:nowrap;">' + esc(PORTAL.dateTimeText(r.requestedAt)) + '</td>'
      + '<td>' + esc(dash(r.depositorName || r.depositorCode)) + '</td>'
      + '<td>' + esc(dash(r.requestedName || r.requestedLogin)) + '</td>'
      + '<td>' + esc(r.reportName) + '<div class="hint">' + esc(r.wmsCode) + '</div></td>'
      + '<td style="font-size:12px;">' + (params || '—') + '</td>'
      + '<td>' + esc(dash(subject)) + '</td>'
      + '<td><span class="badge ' + badge + '"><span class="dot"></span>' + esc(r.statusName) + '</span>' + error + '</td>'
      + '<td style="white-space:nowrap;">' + esc(r.finishedAt ? PORTAL.dateTimeText(r.finishedAt) : '—') + '</td>'
      + '<td style="text-align:right;"><button type="button" class="btn ghost" data-run-download="'
      + esc(r.runId) + '"' + (ready ? '' : ' disabled') + '>' + esc(T_('reports.button.download')) + '</button></td>'
      + '</tr>';
  }

  function render(){
    const body = document.getElementById('runsBody');
    if (!body) return;
    body.innerHTML = state.rows.length ? state.rows.map(runRow).join('')
      : '<tr><td colspan="9" style="color:var(--muted);">' + esc(T_('reports.text.runs_not_found')) + '</td></tr>';
    renderPager();
  }

  function renderPager(){
    const host = document.getElementById('runsPager');
    if (!host) return;
    const pages = Math.max(1, Math.ceil(state.total / state.pageSize));
    // та же разметка, что у рядов страниц списков (tables.js)
    host.innerHTML = '<div></div><div class="pager">'
        + '<button type="button" data-step="-1"' + (state.page <= 1 ? ' disabled' : '') + '>'
        + esc(T_('common.button.prev_page')) + '</button>'
        + '<span class="page-info">' + esc(T_('common.text.page_of', {page: state.page, pages: pages, total: state.total}))
        + '</span><button type="button" data-step="1"' + (state.page >= pages ? ' disabled' : '') + '>'
        + esc(T_('common.button.next_page')) + '</button></div>';
    host.querySelectorAll('button[data-step]').forEach(b => {
      /** @type {HTMLButtonElement} */ (b).onclick = () => {
        state.page += Number(b.getAttribute('data-step'));
        load();
      };
    });
  }

  /* ================= Установка ================= */

  function open(){
    prepare();
    loadChoices();
    load();
  }

  function install(){
    // Читается при каждом открытии вкладки: запуски идут, пока смотрят.
    if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen(PANE, open);
    else PORTAL.onAdminSub(sub => { if (sub === PANE) open(); });
    PORTAL.onSession(() => {
      state.page = 1;
      state.filter = emptyFilter();
      const panel = document.getElementById('admin-sub-' + PANE);
      if (panel){
        panel.innerHTML = '';
        delete panel.dataset.wired;
      }
    });
  }

  /**
   * Смена языка: разметка собрана кодом, статусы переводит сервер —
   * собираем и читаем заново, если вкладка открыта. Зовёт i18n.js по имени.
   */
  window.renderReportRuns = function(){
    const panel = document.getElementById('admin-sub-' + PANE);
    if (!panel || !panel.classList.contains('active')) return;
    panel.innerHTML = '';
    delete panel.dataset.wired;
    open();
  };

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
