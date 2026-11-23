/*
  «Журнал действий» — администрирование, группа «Журналы» (V117).

  Кто, когда и что менял в портале: документы, справочники, пользователи
  и доступы, настройки, неудачные входы. Пишут журнал триггеры в базе
  (V116), здесь — только чтение. Отбор и страницы считает сервер
  (GET /api/admin/audit): журнал за год — это сотни тысяч записей.

  События одной транзакции по одной записи сервер уже свёл в одно:
  сохранение состава «удалить всё — вставить заново» здесь не выглядит
  удалением и добавлением всех строк.

  Срок хранения — настройка здесь же: сколько месяцев держать журнал;
  старше удаляется раз в сутки и сразу после смены срока.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, dash, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);

  /** Сколько изменений показывать в строке, пока её не раскрыли. */
  const FIELDS_SHOWN = 3;

  /** Виды объектов для отбора — в порядке, в каком их ищут чаще. */
  const OBJECT_TYPES = ['receipts', 'orders', 'transfers', 'sku', 'packaging', 'supplier', 'consignee',
    'app_user', 'login', 'depositor', 'warehouse', 'operator', 'report', 'enum', 'ui_text', 'locale', 'setting'];

  /**
   * С какой даты журнал открывается: неделя назад (решение заказчика
   * 13.11.2026). Без периода сервер сводит всю историю, а это сотни тысяч
   * записей — первая страница строилась десятки секунд. Поле «с» можно
   * стереть, и тогда видна вся история.
   */
  const DEFAULT_DAYS = 7;

  /** Дата {@link DEFAULT_DAYS} дней назад по местному времени, как её ждёт input type=date. */
  function defaultFrom(){
    const d = new Date();
    d.setDate(d.getDate() - (DEFAULT_DAYS - 1));
    const two = (/** @type {number} */ n) => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + two(d.getMonth() + 1) + '-' + two(d.getDate());
  }

  const emptyFilter = () => ({depositorId: '', actor: '', objectType: '', from: defaultFrom(), to: ''});

  const state = {
    page: 1, pageSize: 50, total: 0,
    /** @type {any[]} */ rows: [],
    filter: emptyFilter(),
    /** @type {Record<string, boolean>} */ open: {}
  };

  const field = id => /** @type {HTMLInputElement | HTMLSelectElement | null} */ (document.getElementById(id));

  /* ================= Значения ================= */

  // вид значений и подписи частей записи — общие с «Историей» (history.js)
  const value = v => PORTAL.history.value(v);
  const partText = part => PORTAL.history.partText(part);

  /** Объект события: «Заказ O261111003 · строка 2». */
  function objectText(r){
    const type = T_('audit.object.' + r.objectType);
    const label = r.objectType === 'setting' ? T_('audit.setting.' + r.objectId)
        : r.objectType === 'login' ? '' : dash(r.objectLabel);
    const part = partText(r.part);
    return esc(type) + (label ? ' <strong>' + esc(label) + '</strong>' : '')
        + (part ? ' · ' + esc(part) : '');
  }

  /** «Поле: было → стало»; у создания — только «стало», у удаления — «было». */
  function fieldText(f, action){
    const name = esc(T_(f.fieldKey));
    if (action === 'create' || action === 'login_failed') return name + ': ' + esc(value(f.newText));
    if (action === 'delete') return name + ': ' + esc(value(f.oldText));
    return name + ': ' + esc(value(f.oldText)) + ' → ' + esc(value(f.newText));
  }

  function changesHtml(r){
    const fields = r.fields || [];
    if (!fields.length) return '';
    const open = !!state.open[r.eventId];
    const shown = open ? fields : fields.slice(0, FIELDS_SHOWN);
    const more = fields.length - shown.length;
    return shown.map(f => '<div>' + fieldText(f, r.action) + '</div>').join('')
        + (more > 0 ? '<a href="#" class="audit-more" data-event="' + esc(r.eventId) + '">'
            + esc(T_('audit.text.more_fields', {n: more})) + '</a>' : '');
  }

  /* ================= Разметка ================= */

  function prepare(){
    const panel = document.getElementById('admin-sub-audit');
    if (!panel || panel.dataset.wired) return;
    panel.dataset.wired = '1';

    const date = (id, key) => '<div class="fgroup"><label>' + esc(T_(key)) + '</label>'
        + '<input type="date" id="' + id + '"></div>';
    const select = (id, key) => '<div class="fgroup"><label>' + esc(T_(key)) + '</label>'
        + '<select id="' + id + '"></select></div>';
    panel.innerHTML = ''
      + '<div class="panel-head" style="margin-bottom:6px;">'
      + '<div class="section-title" style="margin:0;">' + esc(T_('admin.button.audit_log')) + '</div>'
      + '<div class="actions" style="align-items:center;gap:8px;">'
      + '<label for="auditRetention" style="font-size:13px;color:var(--muted);">'
      + esc(T_('audit.label.retention_months')) + '</label>'
      + '<input type="number" id="auditRetention" min="1" max="120" class="kv-input-editable"'
      + ' style="width:80px;padding:6px 8px;border:1px solid var(--line);border-radius:6px;">'
      + '<button class="btn" type="button" id="auditRetentionSave">' + esc(T_('portal.text.save')) + '</button>'
      + '</div></div>'
      + '<div class="hint" style="margin:0 0 12px;font-size:13px;color:var(--muted);">'
      + esc(T_('audit.hint.audit_log')) + '</div>'
      + '<div class="filters">'
      + select('auditDepositor', 'admin.column.depositor')
      + select('auditActor', 'admin.column.user')
      + select('auditObjectType', 'audit.label.object_type')
      + date('auditFrom', 'audit.label.date_from')
      + date('auditTo', 'audit.label.date_to')
      + '<div class="fgroup" style="justify-content:flex-end;"><label>&nbsp;</label><div style="display:flex;gap:8px;">'
      + '<button class="btn primary" type="button" id="auditSearch">' + esc(T_('common.button.search')) + '</button>'
      + '<button class="btn" type="button" id="auditReset">' + esc(T_('common.button.reset')) + '</button>'
      + '</div></div>'
      + '<div class="spacer"></div></div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('audit.column.when')) + '</th>'
      + '<th>' + esc(T_('audit.column.who')) + '</th>'
      + '<th>' + esc(T_('admin.column.depositor')) + '</th>'
      + '<th>' + esc(T_('audit.column.object')) + '</th>'
      + '<th>' + esc(T_('audit.column.action')) + '</th>'
      + '<th>' + esc(T_('audit.column.changes')) + '</th>'
      + '</tr></thead><tbody id="auditBody"></tbody></table>'
      + '<div class="pagination-bar" id="auditPager"></div></div>';

    const types = field('auditObjectType');
    if (types) types.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
        + OBJECT_TYPES.map(t => '<option value="' + t + '">' + esc(T_('audit.object.' + t)) + '</option>').join('');
    const deps = field('auditDepositor');
    if (deps) deps.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
        + (PORTAL.session && PORTAL.session.depositors || []).map(d =>
            '<option value="' + esc(d.depositorId) + '">' + esc(d.orgName) + '</option>').join('');

    // Поля — из отбора, который уже действует: панель собирается заново и
    // при смене языка, и отбор при этом не должен молча меняться.
    const show = (/** @type {string} */ id, /** @type {string} */ v) => { const el = field(id); if (el) el.value = v; };
    show('auditObjectType', state.filter.objectType);
    show('auditDepositor', state.filter.depositorId);
    show('auditFrom', state.filter.from);
    show('auditTo', state.filter.to);

    const byId = id => document.getElementById(id);
    const search = byId('auditSearch');
    if (search) search.onclick = () => { readFilter(); state.page = 1; load(); };
    const reset = byId('auditReset');
    // «Сбросить» — к отбору по умолчанию, то есть к последней неделе
    if (reset) reset.onclick = () => {
      state.filter = emptyFilter();
      ['auditDepositor', 'auditActor', 'auditObjectType', 'auditTo'].forEach(id => show(id, ''));
      show('auditFrom', state.filter.from);
      state.page = 1;
      load();
    };
    const save = byId('auditRetentionSave');
    if (save) save.onclick = saveRetention;
    const body = byId('auditBody');
    if (body) body.addEventListener('click', e => {
      const link = /** @type {HTMLElement} */ (e.target).closest('.audit-more');
      if (!link) return;
      e.preventDefault();
      state.open[link.getAttribute('data-event') || ''] = true;
      render();
    });
  }

  function readFilter(){
    const v = id => { const el = field(id); return el ? el.value : ''; };
    state.filter = {depositorId: v('auditDepositor'), actor: v('auditActor'),
      objectType: v('auditObjectType'), from: v('auditFrom'), to: v('auditTo')};
  }

  /* ================= Данные ================= */

  async function load(){
    const f = state.filter;
    const q = new URLSearchParams({page: String(state.page), pageSize: String(state.pageSize)});
    Object.entries(f).forEach(([k, v]) => { if (v) q.set(k, v); });
    try {
      const answer = await PORTAL.fetchJson('/api/admin/audit?' + q.toString());
      state.rows = answer.rows || [];
      state.total = answer.total || 0;
      state.page = answer.page || 1;
      state.open = {};
    } catch (e){
      state.rows = [];
      state.total = 0;
      say(messageOf(e));
    }
    render();
  }

  async function loadChoices(){
    try {
      const [actors, settings] = await Promise.all([
        PORTAL.fetchJson('/api/admin/audit/actors'),
        PORTAL.fetchJson('/api/admin/audit/settings')]);
      const sel = field('auditActor');
      if (sel){
        const keep = sel.value || state.filter.actor;
        sel.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
            + (actors || []).map((/** @type {any} */ a) => '<option value="' + esc(a.actor) + '">'
                + esc(a.actorName) + '</option>').join('');
        sel.value = keep;
      }
      const months = field('auditRetention');
      if (months) months.value = String(settings.retentionMonths);
    } catch (e){
      say(messageOf(e));
    }
  }

  async function saveRetention(){
    const el = field('auditRetention');
    if (!el) return;
    try {
      await PORTAL.api('PUT', '/api/admin/audit/settings', {retentionMonths: Number(el.value)});
      say(T_('audit.message.retention_saved'));
      load();
    } catch (e){
      say(messageOf(e));
    }
  }

  /* ================= Отрисовка ================= */

  function render(){
    const body = document.getElementById('auditBody');
    if (!body) return;
    body.innerHTML = state.rows.length
      ? state.rows.map(r => '<tr>'
          + '<td style="white-space:nowrap;">' + esc(PORTAL.dateTimeText(r.at)) + '</td>'
          + '<td>' + esc(dash(r.actorName || r.actor)) + '</td>'
          + '<td>' + esc(dash(r.depositorName)) + '</td>'
          + '<td>' + objectText(r) + '</td>'
          + '<td>' + esc(T_('audit.action.' + r.action)) + '</td>'
          + '<td style="font-size:12.5px;min-width:260px;white-space:normal;overflow-wrap:anywhere;">' + changesHtml(r) + '</td>'
          + '</tr>').join('')
      : '<tr><td colspan="6" style="color:var(--muted);">' + esc(T_('audit.text.nothing_found')) + '</td></tr>';
    renderPager();
  }

  function renderPager(){
    const host = document.getElementById('auditPager');
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

  function install(){
    // Журнал читается при каждом открытии вкладки: он пополняется на глазах.
    PORTAL.onAdminSub(sub => {
      if (sub === 'audit'){
        prepare();
        loadChoices();
        load();
      }
    });
    PORTAL.onSession(() => {
      state.page = 1;
      state.filter = emptyFilter();
      const panel = document.getElementById('admin-sub-audit');
      if (panel){
        panel.innerHTML = '';
        delete panel.dataset.wired;
      }
    });
  }

  /**
   * Смена языка: разметка панели собрана кодом, слова в строках (статусы,
   * склады, да/нет) переводит сервер — собираем и читаем заново, если
   * вкладка открыта. Зовёт i18n.js по имени.
   */
  window.renderAuditLog = function(){
    const panel = document.getElementById('admin-sub-audit');
    if (!panel || !panel.classList.contains('active')) return;
    panel.innerHTML = '';
    delete panel.dataset.wired;
    prepare();
    loadChoices();
    load();
  };

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
