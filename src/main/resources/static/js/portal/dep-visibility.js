
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  async function api(method, path, body){
    const resp = await fetch(path, {
      method: method,
      headers: {'Content-Type':'application/json', 'Accept':'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (resp.status === 204) return null;
    const text = await resp.text();
    const data = text ? JSON.parse(text) : null;
    if (!resp.ok){
      const e = new Error(PORTAL.errorText(resp.status, data));
      e.status = resp.status;
      throw e;
    }
    return data;
  }

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  // Состояние открытой карточки. Галочки живут здесь, а не в DOM:
  // подвкладка перерисовывается целиком, и снятые на другой подвкладке
  // галочки иначе потерялись бы при переключении.
  const state = {
    depositorId: null, depositor: null,
    tab: 'sections',          // sections | fields
    group: null,              // активная подвкладка полей
    warehouses: [],           // выданные склады
    settings: {hideTransfersTab: false},
    columns: [],              // строки admin_depositor_columns по товару
    draft: {}                 // columnId -> доступно ли (правки до сохранения)
  };

  const readOnly = () => currentUserRole === 'developer';

  /* ============ Поля, недоступные поклажедателю ============ */

  /**
   * Доступна ли колонка этому клиенту.
   *
   * Раскладка с сервера запрещённых колонок не содержит вовсе
   * (table_columns их не отдаёт), поэтому «нет в раскладке» и значит
   * «клиенту недоступна». Пока раскладка не пришла, показываем всё:
   * мигание карточки хуже лишнего поля на долю секунды.
   */
  function isColumnAvailable(columnId){
    const rows = PORTAL.columns && PORTAL.columns.raw && PORTAL.columns.raw.sku;
    if (!rows) return true;
    return rows.indexOf(columnId) >= 0;
  }
  PORTAL.fieldAllowed = isColumnAvailable;

  /**
   * Строка карточки товара с привязкой к колонке.
   *
   * Сборка переписывает fcKv(...) в fcKvC('columnId', ...), чтобы строка
   * знала своё поле. Недоступное поле не рисуется — иначе запрет работал
   * бы только в шаблоне Excel и в выборе колонок, а в карточке поле
   * оставалось бы на месте.
   */
  window.fcKvC = function(columnId, label, value){
    if (!isColumnAvailable(columnId)) return '';
    return fcKv(label, value);
  };

  /* ================== Вкладки карточки ================== */

  function tabs(){
    const button = (id, textKey) =>
      '<button class="admin-subtab-btn' + (state.tab === id ? ' active' : '') + '"'
      + ' type="button" data-dep-tab="' + id + '">' + esc(T_(textKey)) + '</button>';
    return '<div class="admin-subtabs" id="depVisTabs">'
      + button('sections', 'admin.tab.sections_and_warehouses')
      + button('fields', 'admin.tab.product_fields')
      + '</div>';
  }

  /* ================== Вкладка «Разделы и склады» ================== */

  function warehousesAndSections(){
    const warehouseList = (DEPVIS.warehouseList || []).map(w => ({
      id: w.warehouseId, name: w.name, address: w.address
    }));
    const issued = new Set(state.warehouses || []);
    const warehouseRow = x =>
      '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
      + 'padding:9px 0;border-bottom:1px solid var(--line);">'
      + '<input type="checkbox" data-warehouse-id="' + esc(x.id) + '"'
      + (issued.has(x.id) ? ' checked' : '') + '> ' + esc(x.name)
      + (x.address ? ' <span style="color:var(--muted);">· ' + esc(x.address) + '</span>' : '')
      + '</label>';

    return ''
      + '<div class="section-title" style="margin-top:0;">' + esc(T_('admin.title.sections'))
      + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <label style="display:flex;align-items:center;gap:8px;font-size:13.5px;">'
      + '    <input type="checkbox" id="depVisTransfers"'
      + (state.settings.hideTransfersTab ? '' : ' checked') + '> '
      + esc(T_('admin.label.show_transfers_section')) + '</label>'
      + '  <div class="hint" style="margin:8px 0 0;">'
      + esc(T_('admin.hint.transfers_section')) + '</div>'
      + '</div>'
      + '<div class="section-title">'
      + esc(T_('admin.title.warehouses_available_to_depositors')) + '</div>'
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">'
      + esc(T_('admin.hint.depositor_warehouses')) + '</div>'
      + (warehouseList.length
          ? warehouseList.map(warehouseRow).join('')
          : '<div style="color:var(--muted);font-size:13.5px;">'
            + esc(T_('common.text.list_is_empty')) + '</div>')
      + '</div>';
  }

  /* ================== Вкладка «Поля товаров» ================== */

  /** Подвкладки в порядке карточки товара: порядок задаёт сама база. */
  function groups(){
    const out = [];
    const seen = new Set();
    state.columns.forEach(c => {
      const key = c.groupKey || '';
      if (!seen.has(key)){
        seen.add(key);
        out.push({key: key, label: c.groupLabel || key});
      }
    });
    return out;
  }

  function productFields(){
    const groupList = groups();
    if (!groupList.length){
      return '<div style="color:var(--muted);font-size:13.5px;">'
        + esc(T_('common.text.list_is_empty')) + '</div>';
    }
    if (!groupList.some(g => g.key === state.group)) state.group = groupList[0].key;

    const subtabs = '<div class="admin-subtabs" style="margin-top:4px;">'
      + groupList.map(g => '<button class="admin-subtab-btn'
          + (g.key === state.group ? ' active' : '') + '" type="button"'
          + ' data-dep-group="' + esc(g.key) + '">' + esc(g.label) + '</button>').join('')
      + '</div>';

    const fields = state.columns.filter(c => (c.groupKey || '') === state.group);
    const row = c => {
      const available = state.draft[c.columnId] !== undefined
        ? state.draft[c.columnId] : c.isAvailable !== false;
      const locked = c.isLocked === true;
      return '<label style="display:flex;align-items:center;gap:8px;font-size:13.5px;'
        + 'padding:9px 0;border-bottom:1px solid var(--line);'
        + (locked ? 'color:var(--muted);' : '') + '">'
        + '<input type="checkbox" data-column-id="' + esc(c.columnId) + '"'
        + (available ? ' checked' : '') + (locked ? ' disabled' : '') + '> '
        + '<span style="color:var(--ink);">' + esc(c.label) + '</span>'
        + (locked
            ? ' <span style="color:var(--muted);">· '
              + esc(T_('admin.text.required_field_locked')) + '</span>'
            : '')
        + (c.label !== c.portalLabel
            ? ' <span style="color:var(--muted);">· ' + esc(c.portalLabel) + '</span>'
            : '')
        + '</label>';
    };

    return subtabs
      + '<div class="table-card" style="padding:18px 20px;">'
      + '  <div class="hint" style="margin:-4px 0 10px;">'
      + esc(T_('admin.hint.product_fields')) + '</div>'
      + fields.map(row).join('')
      + '</div>';
  }

  /* ================== Сборка карточки ================== */

  const actions = ''
    + '<div class="actions" style="margin-top:18px;display:flex;gap:8px;">'
    + '  <button class="btn primary" type="button" data-dep-save></button>'
    + '  <button class="btn ghost" type="button" data-dep-cancel></button>'
    + '</div>';

  function render(){
    const el = document.getElementById('adminUserEditContent');
    if (!el) return;
    const d = state.depositor || {};
    el.innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>' + esc(d.orgName || '') + '</h2>'
      + '  <div class="meta">' + esc(T_('admin.column.depositor')) + ': '
      + esc(d.code || '') + '</div></div></div>'
      + tabs()
      + (state.tab === 'fields' ? productFields() : warehousesAndSections())
      + actions;

    el.querySelectorAll('[data-dep-tab]').forEach(b => {
      b.onclick = () => { remember(); state.tab = b.getAttribute('data-dep-tab'); render(); };
    });
    el.querySelectorAll('[data-dep-group]').forEach(b => {
      b.onclick = () => { remember(); state.group = b.getAttribute('data-dep-group'); render(); };
    });

    const save = el.querySelector('[data-dep-save]');
    save.textContent = T_('common.button.save');
    save.onclick = send;
    const cancel = el.querySelector('[data-dep-cancel]');
    cancel.textContent = T_('common.button.cancel');
    cancel.onclick = () => { showView('admin'); showAdminSub('depositor-access'); };

    if (readOnly()){
      el.querySelectorAll('input').forEach(e => e.disabled = true);
      save.style.display = 'none';
    }
  }

  /**
   * Переносит галочки с экрана в состояние.
   *
   * Вызывается перед каждой перерисовкой: подвкладка строится заново,
   * и без этого правка, сделанная на одной подвкладке, исчезала бы
   * при переходе на другую — человек бы решил, что сохранил.
   */
  function remember(){
    const el = document.getElementById('adminUserEditContent');
    if (!el) return;
    el.querySelectorAll('input[data-column-id]').forEach(cb => {
      if (cb.disabled) return;          // обязательные не трогаем
      state.draft[cb.getAttribute('data-column-id')] = cb.checked;
    });
    const warehouseList = [...el.querySelectorAll('input[data-warehouse-id]')];
    if (warehouseList.length) state.warehouses = warehouseList.filter(c => c.checked)
      .map(c => c.getAttribute('data-warehouse-id'));
    const transfers = el.querySelector('#depVisTransfers');
    if (transfers) state.settings.hideTransfersTab = !transfers.checked;
  }

  async function send(){
    remember();
    try {
      // Склады и настройку разделов отправляем всегда: их видно на одной
      // вкладке, и человек вправе рассчитывать, что кнопка сохраняет всё.
      DEPVIS.access = await api('PUT', '/api/admin/depositors/'
        + encodeURIComponent(state.depositorId) + '/warehouses',
        {warehouseIds: state.warehouses});
      await api('PUT', '/api/admin/depositors/'
        + encodeURIComponent(state.depositorId) + '/settings',
        {hideTransfersTab: state.settings.hideTransfersTab});

      // Поля — только изменённые: каждая строка отдельным запросом,
      // потому что база заводит запись лишь при отличии от портальной
      // настройки и сама убирает её, когда отличие исчезло.
      const changed = state.columns.filter(c => {
        const before = c.isAvailable !== false;
        const now = state.draft[c.columnId];
        return now !== undefined && now !== before;
      });
      for (const c of changed){
        await api('PUT', '/api/admin/depositor-columns', {
          depositorId: state.depositorId, table: 'sku', columnId: c.columnId,
          isAvailable: state.draft[c.columnId], isDefault: c.isDefault !== false
        });
      }

      say(changed.length
        ? T_('admin.message.fields_updated')
        : T_('admin.message.settings_updated'));

      if (typeof renderAdminDepositorAccess === 'function') renderAdminDepositorAccess();
      // правили текущего клиента — перечитываем раскладку и меню
      if (PORTAL.session && PORTAL.session.depositorId === state.depositorId){
        PORTAL.reloadAll();
      }
      showView('admin');
      showAdminSub('depositor-access');
    } catch (e){
      say(e.message);
    }
  }

  /* ================== Открытие ================== */

  const DEPVIS = {warehouseList: [], access: []};

  window.portalOpenDepositorSettings = async function(depositorId){
    const d = (DEPVIS.access || []).find(x => x.depositorId === depositorId);
    state.depositorId = depositorId;
    state.depositor = d || {};
    state.tab = 'sections';
    state.group = null;
    state.draft = {};
    try {
      const [warehouseList, issued, prefs, columnList] = await Promise.all([
        DEPVIS.warehouseList.length ? Promise.resolve(DEPVIS.warehouseList)
                             : api('GET', '/api/admin/warehouses'),
        api('GET', '/api/admin/depositors/' + encodeURIComponent(depositorId) + '/warehouses'),
        api('GET', '/api/admin/depositors/' + encodeURIComponent(depositorId) + '/settings'),
        api('GET', '/api/admin/depositor-columns?table=sku&depositorId='
            + encodeURIComponent(depositorId))
      ]);
      DEPVIS.warehouseList = warehouseList || [];
      state.warehouses = issued || [];
      state.settings = prefs || {hideTransfersTab: false};
      state.columns = columnList || [];
    } catch (e){
      say(e.message);
      return;
    }
    render();
    PORTAL.setBack('adminUserEditBack', 'admin.button.back_to_depositor_access',
        () => { showView('admin'); showAdminSub('depositor-access'); });
    showView('admin-user-edit');
  };

  /**
   * Заглушка «Раздел в разработке» в настройке полей поклажедателя.
   *
   * Этот раздел теперь существует — в карточке доступов поклажедателя.
   * Оставить заглушку значило бы держать на экране обещание, которое
   * уже выполнено в другом месте: администратор искал бы настройку там,
   * где её нет.
   */
  function hintWhereSetting(){
    const el = document.getElementById('fl-sub-visibility');
    if (!el || el.dataset.redirected) return;
    el.dataset.redirected = '1';
    el.innerHTML = '<div class="hint" style="margin-bottom:12px;">'
      + esc(T_('admin.hint.where_field_visibility')) + '</div>'
      + '<button class="btn primary" type="button" id="goDepositorAccess"></button>';
    const b = document.getElementById('goDepositorAccess');
    b.textContent = T_('admin.button.depositor_access');
    b.onclick = () => { showView('admin'); showAdminSub('depositor-access'); };
  }

  function install(){
    // Строку в списке открывает уже наша карточка: прежняя показывала
    // только склады, и две карточки на один экран не нужны.
    window.portalOpenDepositorWarehouses = window.portalOpenDepositorSettings;
    window.openAdminDepositorAccessEdit = window.portalOpenDepositorSettings;

    PORTAL.onLogin(async () => {
      try {
        const [warehouseList, access] = await Promise.all([
          api('GET', '/api/admin/warehouses'),
          api('GET', '/api/admin/depositor-access')
        ]);
        DEPVIS.warehouseList = warehouseList || [];
        DEPVIS.access = access || [];
      } catch (e){
        DEPVIS.warehouseList = [];
        DEPVIS.access = [];
      }
      hintWhereSetting();
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
