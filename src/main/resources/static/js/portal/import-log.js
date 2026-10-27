
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const state = {imports: [], issues: [], depositorId: '', loaded: false};

  /**
   * Что грузили — словами экрана. С V81 загрузок две, и строка
   * «не найдено в справочнике» без этой колонки двусмысленна: в каком?
   * Подписи те же, что у вкладок «Справочников».
   */
  const KIND_LABEL = {sku: 'references.button.products', packaging: 'references.title.packaging'};
  const kindOf = k => KIND_LABEL[k] ? T_(KIND_LABEL[k]) : dash(k);
  PORTAL.importLog = state;

  /** «09.10.2026, 13:45» из метки времени, как её отдаёт сервер. */
  function when(iso){
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso).slice(0, 16).replace('T', ', ');
    const two = n => String(n).padStart(2, '0');
    return two(d.getDate()) + '.' + two(d.getMonth() + 1) + '.' + d.getFullYear()
      + ', ' + two(d.getHours()) + ':' + two(d.getMinutes());
  }

  async function load(){
    const url = '/api/admin/excel-imports'
      + (state.depositorId ? '?depositorId=' + encodeURIComponent(state.depositorId) : '');
    try {
      const response = await PORTAL.fetchJson(url);
      state.imports = response.imports || [];
      state.issues = response.issues || [];
      state.loaded = true;
    } catch (e){
      state.imports = [];
      state.issues = [];
      say(e.message);
    }
    render();
  }

  /* ================= Разметка ================= */

  function prepare(){
    const panel = document.getElementById('admin-sub-excel');
    if (!panel || panel.dataset.wired) return;
    panel.dataset.wired = '1';

    // Выдуманные строки прототипа заменяем своей разметкой целиком:
    // шапка у настоящего журнала другая (добавился поклажедатель),
    // а подставлять данные в чужую таблицу значило бы зависеть
    // от порядка её колонок.
    panel.innerHTML = ''
      + '<div class="section-title" style="margin-top:0;">'
      + esc(T_('admin.title.excel_import_errors')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 12px;">'
      + esc(T_('admin.hint.import_log')) + '</div>'
      + '<div class="filters"><div class="fgroup">'
      + '<label>' + esc(T_('admin.label.depositor')) + '</label>'
      + '<select id="impLogDepositor"></select></div>'
      + '<div class="spacer"></div></div>'
      + '<div id="impLogSummary"></div>'
      + '<div class="table-card"><table><thead><tr>'
      + '<th>' + esc(T_('admin.column.date_and_time')) + '</th>'
      + '<th>' + esc(T_('admin.column.depositor')) + '</th>'
      + '<th>' + esc(T_('admin.column.user')) + '</th>'
      + '<th>' + esc(T_('admin.column.import_kind')) + '</th>'
      + '<th>' + esc(T_('admin.column.file')) + '</th>'
      + '<th>' + esc(T_('admin.column.line')) + '</th>'
      + '<th>' + esc(T_('admin.column.error')) + '</th>'
      + '</tr></thead><tbody id="impLogBody"></tbody></table></div>';

    const choice = document.getElementById('impLogDepositor');
    choice.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
      + (PORTAL.session && PORTAL.session.depositors || []).map(d =>
          '<option value="' + esc(d.depositorId) + '">' + esc(d.orgName) + '</option>').join('');
    choice.onchange = () => { state.depositorId = choice.value; load(); };
  }

  function render(){
    const body = document.getElementById('impLogBody');
    if (!body) return;

    body.innerHTML = state.issues.map(i =>
        '<tr><td style="white-space:nowrap;">' + esc(when(i.startedAt)) + '</td>'
      + '<td>' + esc(dash(i.depositorName)) + '</td>'
      + '<td>' + esc(dash(i.login)) + '</td>'
      + '<td>' + esc(kindOf(i.importKind)) + '</td>'
      + '<td>' + esc(dash(i.fileName)) + '</td>'
      + '<td class="num">' + esc(dash(i.rowNumber)) + '</td>'
      + '<td>' + esc(i.message)
      + (i.fileValue ? ' <span class="count">' + esc(i.fileValue) + '</span>' : '')
      + '</td></tr>').join('')
      || '<tr><td colspan="7" style="color:var(--muted);">'
         + esc(T_('admin.text.import_log_empty')) + '</td></tr>';

    // Сводка по загрузкам: сколько файлов, сколько позиций создано
    // и обновлено. Без неё журнал отвечает только на вопрос
    // «что не загрузилось», а «что загрузилось» — нигде.
    const sum = field => state.imports.reduce((s, r) => s + (r[field] || 0), 0);
    const summary = document.getElementById('impLogSummary');
    if (summary){
      summary.innerHTML = state.imports.length
        ? '<div class="hint" style="margin:0 0 10px;">'
          + esc(T_('admin.text.import_count')) + ': ' + state.imports.length
          + ' · ' + esc(T_('sku.message.created_items')) + ': ' + sum('createdRows')
          + ' · ' + esc(T_('sku.message.updated_items')) + ': ' + sum('updatedRows')
          + '</div>'
        : '';
    }
  }

  /* ================= Установка ================= */

  function install(){
    // Подвкладку открывает showAdminSub прототипа; оборачиваем её,
    // чтобы журнал читался при открытии, а не висел устаревшим.
    const previous = window.showAdminSub;
    if (typeof previous === 'function' && !previous.__implog){
      const wrapper = function(sub){
        const r = previous.apply(this, arguments);
        if (sub === 'excel'){
          prepare();
          load();
        }
        return r;
      };
      wrapper.__implog = true;
      window.showAdminSub = wrapper;
    }
    // Журнал загрузок администратор смотрит по всем клиентам,
    // выбирая нужного фильтром внутри экрана.
    PORTAL.onSession(() => {
      state.loaded = false;
      state.depositorId = '';
      const panel = document.getElementById('admin-sub-excel');
      if (panel && panel.classList.contains('active')){
        prepare();
        load();
      }
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
