
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
    tab: 'sections',          // sections | fields | labels
    object: 'sku',            // объект на вкладке полей
    group: null,              // подвкладка карточки товара
    warehouses: [],           // выданные склады
    settings: {hideTransfersTab: false},
    columns: [],              // строки admin_depositor_columns по ВСЕМ таблицам
    // Правки до сохранения. Ключ — «таблица.колонка»: одно имя колонки
    // живёт в разных таблицах («вес» есть и в заказе, и в его строке),
    // и по одному columnId они бы слиплись.
    draft: {},                // доступно ли поле клиенту
    defaults: {}              // показывать ли его сразу
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
    // Подпись берём из раскладки по идентификатору колонки: там и перевод,
    // и переименование поля клиентом. В разметку карточки подписи вписаны
    // русскими строками, и по строке переименование не найти — только
    // по идентификатору.
    const own = PORTAL.columns && PORTAL.columns.renameOf
        ? PORTAL.columns.renameOf('sku', columnId) : null;
    return fcKv(own || label, value);
  };

  /* ================== Вкладки карточки ================== */

  function tabs(){
    const button = (id, textKey) =>
      '<button class="admin-subtab-btn' + (state.tab === id ? ' active' : '') + '"'
      + ' type="button" data-dep-tab="' + id + '">' + esc(T_(textKey)) + '</button>';
    return '<div class="admin-subtabs" id="depVisTabs">'
      + button('sections', 'admin.tab.sections_and_warehouses')
      + button('fields', 'admin.tab.fields')
      // Переименование полей переехало сюда из отдельного раздела
      // администрирования: настройка одного клиента должна лежать
      // в одном месте.
      + button('labels', 'admin.button.custom_field_names')
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

  /* ================== Вкладка «Поля» ================== */

  /**
   * Объект поля по имени таблицы.
   *
   * Не берём из v_column_tables: там объект таблицы вычисляется как
   * самый частый объект её колонок (mode), и состав перемещения
   * попадает в «Товар» — колонок товара в нём больше, чем колонок
   * самого перемещения. Для раскладки экрана это неверно: состав
   * перемещения настраивают вместе с перемещением.
   */
  const OBJECT_OF_TABLE = {
    sku: 'sku',
    receipts: 'receipts', receiptComposition: 'receipts', receiptLineDetail: 'receipts',
    orders: 'orders', orderComposition: 'orders', orderLineDetail: 'orders',
    transfers: 'transfers', transferComposition: 'transfers',
    transferLineDetail: 'transfers',
    stock: 'stock', stockBatch: 'stock'
  };

  /** Порядок объектов на экране — от самого частого к редкому. */
  const OBJECTS = [
    {code: 'sku', key: 'references.title.products'},
    {code: 'receipts', key: 'common.button.receipts'},
    {code: 'orders', key: 'common.button.orders'},
    {code: 'transfers', key: 'transfers.title.transfers'},
    {code: 'stock', key: 'stock.title.stock'}
  ];

  const columnsOf = objectCode =>
    state.columns.filter(c => OBJECT_OF_TABLE[c.tableKey] === objectCode);

  /** Ключ правки: таблица и колонка — одно имя колонки живёт в разных таблицах. */
  const draftKey = c => c.tableKey + '.' + c.columnId;

  const isAvailable = c => {
    const own = state.draft[draftKey(c)];
    return own === undefined ? c.isAvailable !== false : own;
  };

  /** Сколько полей объекта закрыто клиенту — видно прямо на подвкладке. */
  function hiddenCount(objectCode){
    return columnsOf(objectCode).filter(c => !isAvailable(c)).length;
  }

  function objectTabs(){
    const available = OBJECTS.filter(o => columnsOf(o.code).length);
    if (!available.some(o => o.code === state.object)){
      state.object = (available[0] || {}).code;
    }
    return '<div class="admin-subtabs" style="margin-top:4px;">'
      + available.map(o => {
          const hidden = hiddenCount(o.code);
          return '<button class="admin-subtab-btn'
            + (o.code === state.object ? ' active' : '') + '" type="button"'
            + ' data-dep-object="' + esc(o.code) + '">' + esc(T_(o.key))
            + (hidden ? ' <span class="count">' + hidden + ' '
                + esc(T_('admin.text.object_fields_count')) + '</span>' : '')
            + '</button>';
        }).join('')
      + '</div>';
  }

  /**
   * Строка поля: две галочки, как на прежнем экране колонок клиента.
   *
   * «Доступно» — видит ли клиент поле вообще. «Показывать сразу» —
   * попадёт ли оно в таблицу по умолчанию; это разные вопросы, и
   * раньше они были на разных экранах. Недоступное поле «показывать
   * сразу» не может по определению, поэтому вторая галочка гаснет.
   */
  function fieldRow(c){
    const available = isAvailable(c);
    const byDefault = state.defaults[draftKey(c)] === undefined
      ? c.isDefault !== false : state.defaults[draftKey(c)];
    const locked = c.isLocked === true;
    const box = (kind, checked, disabled) =>
      '<input type="checkbox" data-field="' + kind + '"'
      + ' data-column-id="' + esc(c.columnId) + '"'
      + ' data-table-key="' + esc(c.tableKey) + '"'
      + (checked ? ' checked' : '') + (disabled ? ' disabled' : '') + '>';
    return '<div style="display:flex;align-items:center;gap:18px;font-size:13.5px;'
      + 'padding:9px 0;border-bottom:1px solid var(--line);'
      + (locked ? 'color:var(--muted);' : '') + '">'
      + '<label style="display:flex;align-items:center;gap:8px;flex:1;min-width:0;">'
      + box('available', available, locked)
      + '<span style="color:var(--ink);">' + esc(c.label) + '</span>'
      + (locked
          ? ' <span style="color:var(--muted);">· '
            + esc(T_('admin.text.required_field_locked')) + '</span>'
          : '')
      + (c.label !== c.portalLabel
          ? ' <span style="color:var(--muted);">· ' + esc(c.portalLabel) + '</span>'
          : '')
      + '</label>'
      + '<label style="display:flex;align-items:center;gap:8px;color:var(--muted);'
      + 'white-space:nowrap;">'
      + box('default', byDefault, !available)
      + esc(T_('references.column.show_at_once')) + '</label>'
      + '</div>';
  }

  /**
   * Подвкладки объекта — вкладки его карточки.
   *
   * У товара это восемь вкладок карточки товара (V35), у документа —
   * общая информация, транспорт, доп. поля, состав и детали строки
   * (V53). Правило одно: администратор настраивает поле там же, где
   * клиент его видит, а не в списке «все поля подряд».
   *
   * Порядок задаём явно. По данным его не вывести: строки приходят
   * отсортированными по имени таблицы, и состав поступления оказался бы
   * раньше самого поступления просто потому, что receiptComposition
   * идёт по алфавиту прежде receipts.
   */
  const GROUP_ORDER = [
    'common.title.general_information',
    'common.title.transport_section',
    'common.title.additional_information',
    'receipts.title.receipt_contents',
    'orders.title.order_contents',
    'transfers.title.transfer_contents',
    'admin.table.line_details',
    'admin.table.stock_lines',
    'admin.table.details'
  ];

  function groups(objectCode){
    const out = [];
    const seen = new Set();
    columnsOf(objectCode).forEach(c => {
      const key = c.groupKey || '';
      if (seen.has(key)) return;
      seen.add(key);
      out.push({key: key, label: c.groupLabel || key});
    });
    // Товар оставляем в порядке карточки — он задан сортировкой колонок
    // в базе; остальные объекты расставляем по списку выше.
    if (objectCode === 'sku') return out;
    return out.sort((a, b) => {
      const ia = GROUP_ORDER.indexOf(a.key);
      const ib = GROUP_ORDER.indexOf(b.key);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });
  }

  /** Сколько полей вкладки закрыто клиенту. */
  function hiddenInGroup(objectCode, groupKey){
    return columnsOf(objectCode)
      .filter(c => (c.groupKey || '') === groupKey && !isAvailable(c)).length;
  }

  function objectFields(objectCode){
    const groupList = groups(objectCode);
    if (!groupList.some(g => g.key === state.group)){
      state.group = (groupList[0] || {}).key;
    }
    const subtabs = '<div class="admin-subtabs" style="margin-top:4px;">'
      + groupList.map(g => {
          const hidden = hiddenInGroup(objectCode, g.key);
          return '<button class="admin-subtab-btn'
            + (g.key === state.group ? ' active' : '') + '" type="button"'
            + ' data-dep-group="' + esc(g.key) + '">' + esc(g.label)
            + (hidden ? ' <span class="count">' + hidden + '</span>' : '')
            + '</button>';
        }).join('')
      + '</div>';

    const fields = columnsOf(objectCode).filter(c => (c.groupKey || '') === state.group);
    return subtabs
      + '<div class="table-card" style="padding:18px 20px;">'
      + (fields.length
          ? fields.map(fieldRow).join('')
          : '<div style="color:var(--muted);font-size:13.5px;">'
            + esc(T_('common.text.list_is_empty')) + '</div>')
      + '</div>';
  }

  function fields(){
    if (!state.columns.length){
      return '<div style="color:var(--muted);font-size:13.5px;">'
        + esc(T_('common.text.list_is_empty')) + '</div>';
    }
    return objectTabs()
      + '<div class="hint" style="margin:10px 0 0;max-width:760px;">'
      + esc(T_('admin.hint.depositor_fields')) + '</div>'
      + objectFields(state.object);
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
    // Вкладка наименований полей сохраняет каждую правку сразу, своим
    // окном: общая кнопка «Сохранить» на ней сохраняла бы пустоту,
    // а человек решил бы, что она сохранила названия.
    const labels = state.tab === 'labels';
    const body = labels
      ? (PORTAL.fieldLabels ? PORTAL.fieldLabels.html() : '')
      : (state.tab === 'fields' ? fields() : warehousesAndSections());
    el.innerHTML = ''
      + '<div class="detail-head"><div>'
      + '  <h2>' + esc(d.orgName || '') + '</h2>'
      + '  <div class="meta">' + esc(T_('admin.column.depositor')) + ': '
      + esc(d.code || '') + '</div></div></div>'
      + tabs()
      + body
      + (labels ? '' : actions);

    el.querySelectorAll('[data-dep-tab]').forEach(b => {
      b.onclick = () => { remember(); state.tab = b.getAttribute('data-dep-tab'); render(); };
    });
    el.querySelectorAll('[data-dep-group]').forEach(b => {
      b.onclick = () => { remember(); state.group = b.getAttribute('data-dep-group'); render(); };
    });
    el.querySelectorAll('[data-dep-object]').forEach(b => {
      b.onclick = () => {
        remember();
        state.object = b.getAttribute('data-dep-object');
        state.group = null;       // у нового объекта свои подвкладки
        render();
      };
    });
    // «Показывать сразу» без «Доступно» не бывает: гасим вторую галочку
    // сразу, не дожидаясь перерисовки, иначе человек щёлкает и не видит
    // отклика.
    el.querySelectorAll('input[data-field="available"]').forEach(cb => {
      cb.onchange = () => {
        const pair = el.querySelector('input[data-field="default"][data-column-id="'
          + cb.getAttribute('data-column-id') + '"][data-table-key="'
          + cb.getAttribute('data-table-key') + '"]');
        if (pair) pair.disabled = !cb.checked;
      };
    });

    if (labels){
      if (PORTAL.fieldLabels) PORTAL.fieldLabels.wire(el, render);
      if (readOnly()){
        el.querySelectorAll('input, select').forEach(e => e.disabled = true);
        el.querySelectorAll('#flAddBtn, [data-fl-del]').forEach(b => b.remove());
        el.querySelectorAll('[data-fl-row]').forEach(tr => {
          tr.onclick = null;
          tr.style.cursor = 'default';
        });
        // фильтры разработчику оставляем: смотреть не запрещено
        el.querySelectorAll('#flFilterObject2, #flFilterField2')
          .forEach(e => e.disabled = false);
      }
      return;
    }

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
      const key = cb.getAttribute('data-table-key') + '.' + cb.getAttribute('data-column-id');
      if (cb.getAttribute('data-field') === 'default') state.defaults[key] = cb.checked;
      else state.draft[key] = cb.checked;
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
        const key = c.tableKey + '.' + c.columnId;
        const available = state.draft[key];
        const byDefault = state.defaults[key];
        return (available !== undefined && available !== (c.isAvailable !== false))
            || (byDefault !== undefined && byDefault !== (c.isDefault !== false));
      });
      for (const c of changed){
        const key = c.tableKey + '.' + c.columnId;
        const available = state.draft[key] === undefined
          ? c.isAvailable !== false : state.draft[key];
        await api('PUT', '/api/admin/depositor-columns', {
          depositorId: state.depositorId,
          // таблица своя у каждой строки: одно и то же поле настраивается
          // отдельно в документе, в его составе и в деталях строки
          table: c.tableKey,
          columnId: c.columnId,
          isAvailable: available,
          // недоступное поле «показывать сразу» не может по определению
          isDefault: available && (state.defaults[key] === undefined
            ? c.isDefault !== false : state.defaults[key])
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
    state.object = 'sku';
    state.group = null;
    state.draft = {};
    state.defaults = {};
    try {
      const [warehouseList, issued, prefs, columnList] = await Promise.all([
        DEPVIS.warehouseList.length ? Promise.resolve(DEPVIS.warehouseList)
                             : api('GET', '/api/admin/warehouses'),
        api('GET', '/api/admin/depositors/' + encodeURIComponent(depositorId) + '/warehouses'),
        api('GET', '/api/admin/depositors/' + encodeURIComponent(depositorId) + '/settings'),
        // Без table — вся раскладка клиента разом, по всем двенадцати
        // таблицам: вкладка настраивает не только товар.
        api('GET', '/api/admin/depositor-columns?depositorId='
            + encodeURIComponent(depositorId)),
        // наименования полей — третья вкладка этой же карточки
        PORTAL.fieldLabels ? PORTAL.fieldLabels.load(depositorId) : Promise.resolve()
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

  function install(){
    // Строку в списке открывает уже наша карточка: прежняя показывала
    // только склады, и две карточки на один экран не нужны.
    window.portalOpenDepositorWarehouses = window.portalOpenDepositorSettings;
    window.openAdminDepositorAccessEdit = window.portalOpenDepositorSettings;

    // Экран администрирования: склады и доступы по всем клиентам.
    PORTAL.onSession(async () => {
      if (!PORTAL.isAdmin()){
        DEPVIS.warehouseList = [];
        DEPVIS.access = [];
        return;
      }
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
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
