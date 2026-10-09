
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
  PORTAL.importLog = state;

  /** «09.10.2026, 13:45» из метки времени, как её отдаёт сервер. */
  function когда(iso){
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return String(iso).slice(0, 16).replace('T', ', ');
    const двa = n => String(n).padStart(2, '0');
    return двa(d.getDate()) + '.' + двa(d.getMonth() + 1) + '.' + d.getFullYear()
      + ', ' + двa(d.getHours()) + ':' + двa(d.getMinutes());
  }

  async function загрузить(){
    const адрес = '/api/admin/excel-imports'
      + (state.depositorId ? '?depositorId=' + encodeURIComponent(state.depositorId) : '');
    try {
      const resp = await fetch(адрес, {headers: {'Accept': 'application/json'}});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      const ответ = await resp.json();
      state.imports = ответ.imports || [];
      state.issues = ответ.issues || [];
      state.loaded = true;
    } catch (e){
      state.imports = [];
      state.issues = [];
      say(e.message);
    }
    нарисовать();
  }

  /* ================= Разметка ================= */

  function подготовить(){
    const панель = document.getElementById('admin-sub-excel');
    if (!панель || панель.dataset.wired) return;
    панель.dataset.wired = '1';

    // Выдуманные строки прототипа заменяем своей разметкой целиком:
    // шапка у настоящего журнала другая (добавился поклажедатель),
    // а подставлять данные в чужую таблицу значило бы зависеть
    // от порядка её колонок.
    панель.innerHTML = ''
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
      + '<th>' + esc(T_('admin.column.file')) + '</th>'
      + '<th>' + esc(T_('admin.column.line')) + '</th>'
      + '<th>' + esc(T_('admin.column.error')) + '</th>'
      + '</tr></thead><tbody id="impLogBody"></tbody></table></div>';

    const выбор = document.getElementById('impLogDepositor');
    выбор.innerHTML = '<option value="">' + esc(T_('common.option.all')) + '</option>'
      + (PORTAL.session && PORTAL.session.depositors || []).map(d =>
          '<option value="' + esc(d.depositorId) + '">' + esc(d.orgName) + '</option>').join('');
    выбор.onchange = () => { state.depositorId = выбор.value; загрузить(); };
  }

  function нарисовать(){
    const тело = document.getElementById('impLogBody');
    if (!тело) return;

    тело.innerHTML = state.issues.map(i =>
        '<tr><td style="white-space:nowrap;">' + esc(когда(i.startedAt)) + '</td>'
      + '<td>' + esc(dash(i.depositorName)) + '</td>'
      + '<td>' + esc(dash(i.login)) + '</td>'
      + '<td>' + esc(dash(i.fileName)) + '</td>'
      + '<td class="num">' + esc(dash(i.rowNumber)) + '</td>'
      + '<td>' + esc(i.message)
      + (i.fileValue ? ' <span class="count">' + esc(i.fileValue) + '</span>' : '')
      + '</td></tr>').join('')
      || '<tr><td colspan="6" style="color:var(--muted);">'
         + esc(T_('admin.text.import_log_empty')) + '</td></tr>';

    // Сводка по загрузкам: сколько файлов, сколько позиций создано
    // и обновлено. Без неё журнал отвечает только на вопрос
    // «что не загрузилось», а «что загрузилось» — нигде.
    const сумма = поле => state.imports.reduce((s, r) => s + (r[поле] || 0), 0);
    const сводка = document.getElementById('impLogSummary');
    if (сводка){
      сводка.innerHTML = state.imports.length
        ? '<div class="hint" style="margin:0 0 10px;">'
          + esc(T_('admin.text.import_count')) + ': ' + state.imports.length
          + ' · ' + esc(T_('sku.message.created_items')) + ': ' + сумма('createdRows')
          + ' · ' + esc(T_('sku.message.updated_items')) + ': ' + сумма('updatedRows')
          + '</div>'
        : '';
    }
  }

  /* ================= Установка ================= */

  function install(){
    // Подвкладку открывает showAdminSub прототипа; оборачиваем её,
    // чтобы журнал читался при открытии, а не висел устаревшим.
    const прежняя = window.showAdminSub;
    if (typeof прежняя === 'function' && !прежняя.__implog){
      const обёртка = function(sub){
        const r = прежняя.apply(this, arguments);
        if (sub === 'excel'){
          подготовить();
          загрузить();
        }
        return r;
      };
      обёртка.__implog = true;
      window.showAdminSub = обёртка;
    }
    PORTAL.onLogin(() => {
      state.loaded = false;
      state.depositorId = '';
      const панель = document.getElementById('admin-sub-excel');
      if (панель && панель.classList.contains('active')){
        подготовить();
        загрузить();
      }
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
