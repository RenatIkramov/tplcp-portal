/* =====================================================================
   Администрирование → «Интеграция» (V143, план «Кэш остатков», К7 и К5)
   =====================================================================

   Настройки слоя перевода между WMS и порталом — то, что не зависит от
   документов обмена: учёт перемещений в остатках, расписание ночной
   сверки и три соответствия — адрес WMS склада (склады в API WMS —
   разные порты), код клиента в WMS, код качества WMS → качество портала.
   Ниже — журнал сверок остатков и кнопка «Сверить сейчас».

   Сохраняется всё одной кнопкой. Отказ сервера показывается здесь же,
   в блоке, а не только плашкой.

   Сервер: WmsSettingsController (/api/admin/wms-settings, /api/admin/stock-sync).
   ===================================================================== */

(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const {esc, dash, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);
  const api = PORTAL.api;

  /**
   * @type {{settings: any, runs: any[], error: string, saving: boolean, syncing: boolean,
   *         openRun: string | null, diff: any[], storage: any}}
   */
  const state = {settings: null, runs: [], error: '', saving: false, syncing: false, openRun: null, diff: [],
                 storage: null};

  const panel = () => document.getElementById('admin-sub-wms');

  async function load(){
    try {
      state.settings = await api('GET', '/api/admin/wms-settings');
      state.runs = await api('GET', '/api/admin/stock-sync') || [];
      state.storage = await api('GET', '/api/admin/file-storage');
      state.error = '';
    } catch (e){
      state.error = messageOf(e);
    }
    render();
  }

  /* ======================= Разметка ======================= */

  const errorBox = () => state.error
      ? '<div class="form-error" id="wmsSettingsError" role="alert" style="margin:12px 0;color:#c0392b;'
        + 'background:#fdecea;border:1px solid #f5c6cb;border-radius:6px;padding:8px 10px;">' + esc(state.error) + '</div>'
      : '';

  const input = (/** @type {string} */ value, /** @type {string} */ handler, /** @type {any[]} */ args,
                 /** @type {string} */ attrs) =>
      '<input type="text" value="' + esc(value || '') + '" ' + attrs
      + PORTAL.callOnInput(handler, ...args, PORTAL.VALUE) + '>';

  function settingsHtml(){
    const s = state.settings;
    if (!s) return '';
    const qualityOptions = (/** @type {string} */ chosen) => s.qualityOptions.map((/** @type {any} */ q) =>
        '<option value="' + esc(q.code) + '"' + (q.code === chosen ? ' selected' : '') + '>' + esc(q.name) + '</option>').join('');
    return ''
      + '<div class="section-title" style="margin-top:0;">' + esc(T_('integration.title.stock')) + '</div>'
      + '<label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="wmsCountTransfers"'
      + (s.countTransfers ? ' checked' : '') + PORTAL.callOnChange('PORTAL.integration.set', 'countTransfers', PORTAL.CHECKED)
      + '> ' + esc(T_('integration.label.count_transfers')) + '</label>'
      + '<div class="hint" style="margin:4px 0 14px 24px;">' + esc(T_('integration.hint.count_transfers')) + '</div>'
      + '<div style="display:flex;gap:18px;align-items:center;flex-wrap:wrap;">'
      + '<label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="wmsSyncEnabled"'
      + (s.syncEnabled ? ' checked' : '') + PORTAL.callOnChange('PORTAL.integration.set', 'syncEnabled', PORTAL.CHECKED)
      + '> ' + esc(T_('integration.label.sync_enabled')) + '</label>'
      + '<label style="display:flex;gap:8px;align-items:center;">' + esc(T_('integration.label.sync_time'))
      + ' <input type="time" id="wmsSyncTime" style="width:120px;" value="' + esc(s.syncTime || '') + '"'
      + PORTAL.callOnInput('PORTAL.integration.set', 'syncTime', PORTAL.VALUE) + '></label></div>'

      + '<div class="section-title">' + esc(T_('integration.title.warehouses')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 10px;">' + esc(T_('integration.hint.warehouses')) + '</div>'
      + '<div class="table-card"><table style="width:100%;"><thead><tr><th>' + esc(T_('references.button.warehouses'))
      + '</th><th style="width:55%;">' + esc(T_('integration.column.wms_url')) + '</th></tr></thead><tbody>'
      + s.warehouses.map((/** @type {any} */ w, /** @type {number} */ i) => '<tr><td><strong>' + esc(w.warehouseId)
          + '</strong> ' + esc(dash(w.name)) + '</td><td>'
          + input(w.baseUrl, 'PORTAL.integration.setRow', ['warehouses', i, 'baseUrl'],
              'data-wms-url="' + esc(w.warehouseId) + '" placeholder="http://…:port" style="width:100%;"')
          + '</td></tr>').join('')
      + '</tbody></table></div>'

      + '<div class="section-title">' + esc(T_('integration.title.depositors')) + '</div>'
      + '<div class="table-card"><table style="width:100%;"><thead><tr><th>' + esc(T_('references.button.depositors'))
      + '</th><th style="width:30%;">' + esc(T_('integration.column.wms_code')) + '</th></tr></thead><tbody>'
      + s.depositors.map((/** @type {any} */ d, /** @type {number} */ i) => '<tr><td><strong>' + esc(d.code)
          + '</strong> ' + esc(dash(d.orgName)) + '</td><td>'
          + input(d.wmsCode, 'PORTAL.integration.setRow', ['depositors', i, 'wmsCode'],
              'data-wms-depositor="' + esc(d.code) + '" maxlength="20" style="width:100%;"')
          + '</td></tr>').join('')
      + '</tbody></table></div>'

      + '<div class="section-title">' + esc(T_('integration.title.qualities')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 10px;">' + esc(T_('integration.hint.qualities')) + '</div>'
      + '<div class="table-card"><table style="width:100%;"><thead><tr><th style="width:30%;">'
      + esc(T_('integration.column.wms_code')) + '</th><th>' + esc(T_('integration.column.portal_quality'))
      + '</th><th></th></tr></thead><tbody>'
      + s.qualities.map((/** @type {any} */ q, /** @type {number} */ i) => '<tr><td>'
          + input(q.wmsCode, 'PORTAL.integration.setRow', ['qualities', i, 'wmsCode'], 'maxlength="35" style="width:100%;"')
          + '</td><td><select' + PORTAL.callOnChange('PORTAL.integration.setRow', 'qualities', i, 'qualityCode', PORTAL.VALUE)
          + '>' + qualityOptions(q.qualityCode) + '</select></td>'
          + '<td style="text-align:right;"><button type="button" class="btn ghost"'
          + PORTAL.call('PORTAL.integration.removeQuality', i) + '>✕</button></td></tr>').join('')
      + '</tbody></table></div>'
      + '<button type="button" class="btn ghost" style="margin-top:8px;"' + PORTAL.call('PORTAL.integration.addQuality') + '>'
      + esc(T_('integration.button.add_code')) + '</button>'

      + '<div class="section-title">' + esc(T_('integration.title.client_files')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 10px;">' + esc(T_('integration.hint.client_files')) + '</div>'
      + '<div style="display:flex;gap:18px;align-items:center;flex-wrap:wrap;">'
      + '<label style="display:flex;gap:8px;align-items:center;">' + esc(T_('integration.label.file_max_mb'))
      + ' <input type="number" id="wmsFileMaxMb" min="1" max="100" style="width:90px;" value="' + esc(s.fileMaxMb ?? '') + '"'
      + PORTAL.callOnInput('PORTAL.integration.set', 'fileMaxMb', PORTAL.VALUE) + '></label>'
      + '<label style="display:flex;gap:8px;align-items:center;flex:1;min-width:280px;">' + esc(T_('integration.label.file_types'))
      + ' <input type="text" id="wmsFileTypes" style="flex:1;" value="' + esc(s.fileTypes || '') + '"'
      + PORTAL.callOnInput('PORTAL.integration.set', 'fileTypes', PORTAL.VALUE) + '></label></div>'

      + errorBox()
      + '<div style="margin-top:16px;"><button type="button" class="btn primary" id="wmsSettingsSave"'
      + (state.saving ? ' disabled' : '') + PORTAL.call('PORTAL.integration.save') + '>'
      + esc(T_('common.button.save')) + '</button></div>';
  }

  const when = (/** @type {string | null} */ iso) => {
    if (!iso) return '—';
    const d = new Date(iso);
    const two = (/** @type {number} */ n) => String(n).padStart(2, '0');
    return two(d.getDate()) + '.' + two(d.getMonth() + 1) + '.' + d.getFullYear() + ' ' + two(d.getHours()) + ':' + two(d.getMinutes());
  };

  /** Текст запуска: ключ текста — на языке смотрящего, остальное — как записано. */
  const runError = (/** @type {string | null} */ m) => !m ? '' : /^error\.[a-z_.]+$/.test(m) ? T_(m) : m;

  function diffHtml(){
    return '<tr data-sync-diff><td colspan="7" style="background:var(--bg);"><table style="width:100%;"><thead><tr>'
      + '<th>' + esc(T_('col.stock.code')) + '</th><th>' + esc(T_('col.stock.name')) + '</th>'
      + '<th>' + esc(T_('stock.column.quality')) + '</th>'
      + '<th class="num">' + esc(T_('integration.column.portal_qty')) + '</th>'
      + '<th class="num">' + esc(T_('integration.column.wms_qty')) + '</th></tr></thead><tbody>'
      + state.diff.map(d => '<tr><td><strong>' + esc(d.skuCode) + '</strong></td><td style="white-space:normal;">'
          + esc(d.skuName) + '</td><td>' + esc(d.qualityName) + '</td><td class="num">' + esc(String(Number(d.portalQty)))
          + '</td><td class="num">' + esc(String(Number(d.wmsQty))) + '</td></tr>').join('')
      + '</tbody></table></td></tr>';
  }

  function logHtml(){
    return '<div class="section-title">' + esc(T_('integration.title.sync_log')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 10px;">' + esc(T_('integration.hint.sync_log')) + '</div>'
      + '<button type="button" class="btn" id="wmsSyncNow" style="margin-bottom:10px;"' + (state.syncing ? ' disabled' : '')
      + PORTAL.call('PORTAL.integration.syncNow') + '>'
      + esc(T_(state.syncing ? 'integration.button.syncing' : 'integration.button.sync_now')) + '</button>'
      + '<div class="table-card"><table style="width:100%;"><thead><tr>'
      + '<th>' + esc(T_('integration.column.started')) + '</th><th>' + esc(T_('integration.column.trigger')) + '</th>'
      + '<th>' + esc(T_('references.button.depositors')) + '</th><th>' + esc(T_('references.button.warehouses')) + '</th>'
      + '<th>' + esc(T_('reports.column.status')) + '</th><th class="num">' + esc(T_('integration.column.rows')) + '</th>'
      + '<th class="num">' + esc(T_('integration.column.differences')) + '</th></tr></thead><tbody id="wmsSyncLog">'
      + (state.runs.map(r => {
          const failed = r.statusCode !== '01';
          const diffs = Number(r.diffCount) || 0;
          return '<tr data-sync-run="' + esc(r.syncRunId) + '" data-status="' + esc(r.statusCode) + '">'
            + '<td style="white-space:nowrap;">' + esc(when(r.startedAt)) + '</td>'
            + '<td>' + esc(T_(r.triggerSource === 'schedule' ? 'integration.text.trigger_schedule' : 'integration.text.trigger_manual')) + '</td>'
            + '<td>' + esc(dash(r.depositorCode)) + '</td><td>' + esc(dash(r.warehouseId)) + '</td>'
            + '<td style="white-space:normal;"><span class="badge ' + (failed ? 'err' : 'ok') + '"><span class="dot"></span>'
            + esc(r.statusName) + '</span>' + (r.errorMessage ? '<div class="hint">' + esc(runError(r.errorMessage)) + '</div>' : '') + '</td>'
            + '<td class="num">' + (failed ? '—' : esc(r.rowsReceived + ' / ' + r.rowsApplied)) + '</td>'
            + '<td class="num">' + (diffs
                ? '<button type="button" class="btn ghost" data-sync-open' + PORTAL.call('PORTAL.integration.openRun', r.syncRunId)
                  + '>' + diffs + '</button>'
                : (failed ? '—' : '0')) + '</td></tr>'
            + (state.openRun === r.syncRunId ? diffHtml() : '');
        }).join('')
        || '<tr><td colspan="7" style="color:var(--muted);">' + esc(T_('integration.text.no_runs')) + '</td></tr>')
      + '</tbody></table></div>';
  }

  /** «12,4 МБ»: объём для человека. */
  const size = (/** @type {number} */ bytes) => {
    const n = Number(bytes);
    if (!(n >= 0)) return '—';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let v = n, i = 0;
    while (v >= 1024 && i < units.length - 1){ v /= 1024; i++; }
    return (i === 0 ? String(v) : v.toFixed(1)) + ' ' + units[i];
  };

  /** Хранилище файлов (V146): папку только показываем — меняется в application.yml с перезапуском. */
  function storageHtml(){
    const st = state.storage;
    if (!st) return '';
    const kind = (/** @type {string} */ k) => {
      const v = (st.kinds || {})[k] || {count: 0, bytes: 0};
      return esc(T_('integration.text.files_size', {count: v.count, size: size(v.bytes)}));
    };
    const line = (/** @type {string} */ label, /** @type {string} */ html, /** @type {string} */ id) =>
        '<div style="display:flex;gap:12px;padding:4px 0;"><div style="width:200px;color:var(--muted);">'
        + esc(T_(label)) + '</div><div' + (id ? ' id="' + id + '"' : '') + ' style="flex:1;word-break:break-all;">'
        + html + '</div></div>';
    const sweep = st.lastSweep
        ? esc(T_('integration.text.sweep_result', {at: PORTAL.dateTimeText(st.lastSweep.at),
            expired: st.lastSweep.expired, orphans: st.lastSweep.orphans, missing: st.lastSweep.missing}))
        : esc(T_('integration.text.sweep_never'));
    return '<div class="section-title">' + esc(T_('integration.title.file_storage')) + '</div>'
      + '<div class="hint" style="margin:-6px 0 10px;">' + esc(T_('integration.hint.file_storage')) + '</div>'
      + '<div class="table-card" style="padding:10px 14px;">'
      + line('integration.label.folder', '<code>' + esc(st.location) + '</code>'
          + (st.exists ? '' : ' <span class="hint">' + esc(T_('integration.text.folder_missing')) + '</span>'), 'fileStorageFolder')
      + line('integration.label.kind_report', kind('report'), '')
      + line('integration.label.kind_client', kind('client'), '')
      + line('integration.label.on_disk', esc(T_('integration.text.files_size',
          {count: st.filesOnDisk, size: size(st.bytesOnDisk)})), '')
      + line('integration.label.free_space', esc(size(st.freeBytes)), '')
      + line('integration.label.last_sweep', sweep, '')
      + '</div>';
  }

  function render(){
    const host = panel();
    if (!host) return;
    host.innerHTML = state.settings ? settingsHtml() + storageHtml() + logHtml() : errorBox();
  }

  /* ======================= Действия ======================= */

  PORTAL.integration = {
    set(key, value){ if (state.settings) state.settings[key] = value; },

    setRow(list, index, key, value){
      const row = state.settings && state.settings[list] && state.settings[list][index];
      if (row) row[key] = value;
    },

    addQuality(){
      if (!state.settings) return;
      const first = state.settings.qualityOptions[0];
      state.settings.qualities.push({wmsCode: '', qualityCode: first ? first.code : ''});
      render();
    },

    removeQuality(index){
      if (!state.settings) return;
      state.settings.qualities.splice(index, 1);
      render();
    },

    async save(){
      const s = state.settings;
      if (!s || state.saving) return;
      state.saving = true;
      state.error = '';
      render();
      try {
        state.settings = await api('PUT', '/api/admin/wms-settings', {
          countTransfers: !!s.countTransfers, syncEnabled: !!s.syncEnabled, syncTime: s.syncTime,
          fileMaxMb: s.fileMaxMb === '' || s.fileMaxMb === null ? null : Number(s.fileMaxMb), fileTypes: s.fileTypes,
          warehouses: s.warehouses.map((/** @type {any} */ w) => ({warehouseId: w.warehouseId, baseUrl: w.baseUrl || ''})),
          depositors: s.depositors.map((/** @type {any} */ d) => ({depositorId: d.depositorId, wmsCode: d.wmsCode || ''})),
          qualities: s.qualities.map((/** @type {any} */ q) => ({wmsCode: q.wmsCode || '', qualityCode: q.qualityCode}))
        });
        say(T_('integration.message.saved'));
      } catch (e){
        state.error = messageOf(e);
      }
      state.saving = false;
      render();
    },

    async syncNow(){
      if (state.syncing) return;
      state.syncing = true;
      state.error = '';
      render();
      try {
        const result = await api('POST', '/api/admin/stock-sync');
        // не удалось — об этом скажет строка журнала; «завершена» тут вводила бы в заблуждение
        if (result && !result.failed) say(T_('integration.message.sync_done'));
      } catch (e){
        state.error = messageOf(e);
      }
      try { state.runs = await api('GET', '/api/admin/stock-sync') || []; } catch (e){ state.error = messageOf(e); }
      state.syncing = false;
      render();
    },

    async openRun(runId){
      if (state.openRun === runId){ state.openRun = null; render(); return; }
      try {
        state.diff = await api('GET', '/api/admin/stock-sync/' + encodeURIComponent(runId) + '/diff') || [];
        state.openRun = runId;
      } catch (e){ state.error = messageOf(e); }
      render();
    }
  };

  /** Смена языка: перерисовать открытое (i18n.js зовёт по имени). */
  window.renderIntegration = function(){
    const host = panel();
    if (host && host.classList.contains('active')) load();
  };

  function install(){
    if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen('wms', load);
    else PORTAL.onAdminSub(sub => { if (sub === 'wms') load(); });
    PORTAL.onSession(() => { state.settings = null; state.runs = []; state.openRun = null; });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
})();
