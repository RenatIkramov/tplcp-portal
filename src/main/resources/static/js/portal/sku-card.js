
/*
  Карточка товара — просмотр, правка, создание, упаковки (11.11.2026).

  Прежде карточку рисовали три файла поверх друг друга: прототип —
  просмотр (с русскими подписями и генератором выдуманных характеристик
  на случай, если настоящих нет), sku-edit.js — кнопки, поля ввода
  и упаковки поверх уже нарисованного, sku.js — перевод ответа сервера
  в объект прототипа. У просмотра и правки было по своему списку полей.

  Теперь список полей один (TABS): у каждого поля колонка базы,
  идентификатор в справочнике колонок, ключ подписи и вид. Просмотр
  и правка рисуются из него и называют поле одинаково; закрытое
  клиенту поле не рисуется ни там, ни там.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /** Кто ведёт справочник: и сотрудник склада тоже (решение заказчика 11.11.2026). */
  const EDITOR_ROLES = ['depositor', 'manager', 'staff', 'admin'];
  const mayEdit = () => EDITOR_ROLES.includes(PORTAL.session ? PORTAL.session.roleCode : '');
  /** Состав комплекта вручную, пока его нет из WMS: администратор и сотрудник склада (V154). */
  const KIT_EDITOR_ROLES = ['staff', 'admin'];
  const mayEditKit = () => KIT_EDITOR_ROLES.includes(PORTAL.session ? PORTAL.session.roleCode : '');

  /**
   * Состояние карточки. Вкладки перерисовывают тело целиком, и после
   * переключения вкладки введённое должно остаться — поэтому всё в одном
   * объекте, а несохранённое — в draft.
   * @type {{mode: 'view' | 'edit' | 'create', skuId: string | null, row: CardRow,
   *         draft: CardRow, packagings: PackagingRow[],
   *         pkgForm: {packagingId: string | null, values: CardRow} | null,
   *         kitForm: Array<{code: string, qty: string}> | null,
   *         dict: Record<string, any[]> | null, tab: string}}
   */
  const state = {
    mode: 'view',
    skuId: null,
    row: {},
    draft: {},         // только изменённые поля — их и отправляем
    packagings: [],
    pkgForm: null,     // строка упаковки в правке
    kitForm: null,     // состав комплекта в правке (V154)
    dict: null,
    tab: 'general'
  };

  const el = id => document.getElementById(id);

  // общие помощники — core.js (аудит A4)
  const {dash, esc, say} = window.PORTAL;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };
  /** Колонка базы → поле ответа сервера (v_sku_full отдаёт camelCase). */
  const camel = col => col.replace(/_([a-z0-9])/g, (m, c) => c.toUpperCase());

  async function api(method, url, body){
    /** @type {RequestInit} */
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers = {'Accept': 'application/json', 'Content-Type': 'application/json'};
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
    return resp.status === 204 ? null : resp.json();
  }

  async function dictionaries(){
    if (!state.dict) state.dict = await api('GET', '/api/sku/dictionaries');
    return state.dict;
  }

  /* ===================== поля карточки ===================== */

  /**
   * Поле карточки.
   *  col   — колонка базы: под этим именем поле уходит на сервер. Нет —
   *          поле только показывается (его ставит WMS или сам портал);
   *  id    — идентификатор в справочнике колонок (доступ клиента,
   *          переименование). По умолчанию — col в camelCase;
   *  read  — поле ответа сервера для показа (по умолчанию — id);
   *  kind  — text | num | bool | dict | suggest | date;
   *  dict / suggest — какой справочник (/api/sku/dictionaries).
   * @typedef {{col?: string, id?: string, key: string, kind?: string, read?: string,
   *            dict?: string, suggest?: string, required?: boolean, suffix?: string,
   *            show?: () => string}} SkuField
   */

  /** Единица заказа — упаковка товара с признаком «по умолчанию». */
  function orderUnitText(){
    const p = state.packagings.find(x => x.isDefaultOrderUnit);
    return p ? p.packagingCode + ' — ' + dash(p.packagingTypeName) + ' (' + dash(p.coefficient)
        + (state.row.uomName ? ' ' + state.row.uomName : '') + ')' : '—';
  }

  /** @type {Array<{id: string, key: string, fields: SkuField[]}>} */
  const TABS = [
    {id: 'general', key: 'sku.text.general_information', fields: [
      {col: 'item_code', key: 'col.sku.itemCode'},
      {col: 'code', key: 'sku.text.commercial_code', required: true},
      {col: 'name', key: 'col.sku.name', required: true},
      {col: 'item_full_name', key: 'sku.label.full_item_name'},
      // Группа и тип — свободный текст (V38) с подсказкой уже встречавшихся
      // значений: чтобы «Сырьё» не стало тремя разными группами.
      {col: 'item_group', id: 'group', key: 'sku.label.product_group', kind: 'suggest',
       suggest: 'group', read: 'itemGroupName'},
      {col: 'item_type', id: 'productType', key: 'stock.label.item_type', kind: 'suggest',
       suggest: 'type', read: 'itemTypeName'},
      {col: 'uom_id', id: 'unit', key: 'sku.text.base_unit_of_measure', kind: 'dict', dict: 'uom',
       read: 'uomName', required: true},
      {id: 'orderUnit', key: 'col.sku.orderUnit', show: orderUnitText},
      {col: 'barcode', key: 'sku.text.barcode'}
    ]},
    {id: 'physical', key: 'sku.text.physical_properties', fields: [
      {col: 'length', key: 'sku.label.length_cm', kind: 'num'},
      {col: 'width', key: 'references.column.width_cm', kind: 'num'},
      {col: 'height', key: 'sku.text.height_cm', kind: 'num'},
      {col: 'volume', key: 'sku.label.volume_m', kind: 'num'},
      {col: 'weight_gross', key: 'sku.text.gross_weight_kg', kind: 'num'},
      {col: 'weight_net', key: 'sku.label.net_weight_kg', kind: 'num'},
      {col: 'color', key: 'col.sku.color'},
      {col: 'size', key: 'col.sku.size'}
    ]},
    {id: 'storage', key: 'sku.text.storage_conditions', fields: [
      {col: 'hazard_class_id', id: 'hazardClass', key: 'col.sku.hazardClass', kind: 'dict',
       dict: 'hazard_class', read: 'hazardClassName'},
      {col: 'hazard_group_id', id: 'hazardGroup', key: 'sku.text.hazard_group', kind: 'dict',
       dict: 'hazard_group', read: 'hazardGroupName'},
      {col: 'max_storage_temp', key: 'sku.text.max_storage_temperature_c', kind: 'num'},
      {col: 'min_storage_temp', key: 'sku.text.min_storage_temperature_c', kind: 'num'},
      {col: 'max_humidity', key: 'sku.label.max_storage_humidity', kind: 'num'},
      {col: 'min_humidity', key: 'sku.label.min_storage_humidity', kind: 'num'},
      {col: 'min_days_for_receipt', key: 'sku.text.minimum_days_for_receiving', kind: 'num'},
      {col: 'min_days_for_reservation', key: 'sku.label.minimum_days_for_reservation', kind: 'num'},
      {col: 'shelf_life_days', key: 'col.sku.shelfLifeDays', kind: 'num'}
    ]},
    {id: 'manufacturer', key: 'sku.text.manufacturer_and_classification', fields: [
      {col: 'manufacturer', key: 'sku.text.manufacturer'},
      {col: 'country_origin', key: 'col.sku.countryOrigin'},
      {col: 'brand', key: 'sku.label.brand'},
      {col: 'abc_class', key: 'sku.label.abc_class'},
      {col: 'xyz_class', key: 'sku.label.xyz_class'}
    ]},
    {id: 'codes', key: 'sku.text.codes_and_identifiers', fields: [
      {id: 'guid', key: 'col.sku.guid', read: 'skuId'},
      {col: 'hs_code', key: 'sku.text.hs_code'},
      {col: 'enstru_code', key: 'sku.text.enstru_code'},
      {col: 'currency_id', id: 'currency', key: 'col.sku.currency', kind: 'dict', dict: 'currency',
       read: 'currencyCode'},
      {col: 'cost', key: 'sku.text.cost', kind: 'num'}
    ]},
    {id: 'attributes', key: 'sku.text.attributes_and_tracking', fields: [
      {col: 'is_fragile', key: 'sku.label.fragile', kind: 'bool'},
      {col: 'is_serial_tracked', key: 'sku.text.serial_number_tracking', kind: 'bool'},
      {col: 'batch_tracking', key: 'sku.label.batch_tracking', kind: 'bool'},
      {col: 'expiry_tracking', key: 'col.sku.expiryTracking', kind: 'bool'},
      {col: 'is_kit', key: 'col.sku.isKit', kind: 'bool'},
      {col: 'is_marking_required', key: 'sku.label.mandatory_labelling', kind: 'bool'},
      {col: 'is_excise', key: 'sku.label.excisable_goods', kind: 'bool'},
      {col: 'is_controlled', key: 'sku.label.controlled_licensed_goods', kind: 'bool'}
    ]},
    {id: 'system', key: 'sku.text.system_information', fields: [
      {id: 'statusProduct', key: 'col.sku.statusProduct', read: 'statusName'},
      {id: 'isWmsActive', key: 'col.sku.isWmsActive', kind: 'bool'},
      {col: 'comment', key: 'sku.label.comment'},
      {id: 'createdBy', key: 'col.sku.createdBy'},
      {id: 'createdAt', key: 'col.sku.createdAt', kind: 'date'},
      {id: 'updatedBy', key: 'col.sku.updatedBy'},
      {id: 'updatedAt', key: 'col.sku.updatedAt', kind: 'date'}
    ]},
    // Доп. параметров в базе десять, и правятся все десять.
    {id: 'custom', key: 'sku.text.custom_attributes', fields: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => ({
      col: 'custom_attr' + i, key: 'sku.label.custom_attribute', suffix: ' ' + i
    }))},
    {id: 'packaging', key: 'common.label.packaging', fields: []},
    // состав комплекта — из WMS, только чтение (V151); у не-комплекта вкладки нет
    {id: 'kit', key: 'sku.tab.kit', fields: []},
    // отчёты по этому товару — история движения и т. п. (V139, место 06)
    {id: 'reports', key: 'reports.tab.reports', fields: []},
    // кто и когда менял товар (журнал действий, V118); у нового товара её нет
    {id: 'history', key: 'audit.tab.history', fields: []}
  ];

  const idOf = f => f.id || camel(/** @type {string} */ (f.col));

  /** Доступно ли поле этому поклажедателю (справочник колонок, «Доступы»). */
  const allowed = f => (f.col !== 'is_kit' || PORTAL.kitsEnabled())
      && (!PORTAL.fieldAllowed || PORTAL.fieldAllowed(idOf(f)));

  /**
   * Подпись поля: имя клиента, если он поле переименовал, иначе перевод.
   * Номер доп. параметра приписывается после — переименовывают поле,
   * а не его номер.
   */
  const labelOf = f => PORTAL.fieldLabel('sku', idOf(f), f.key) + (f.suffix ? esc(f.suffix) : '');

  /** Значение для правки: сначала несохранённое, потом из карточки. */
  function editValue(f){
    const col = /** @type {string} */ (f.col);
    if (Object.prototype.hasOwnProperty.call(state.draft, col)) return state.draft[col];
    return state.row[f.read && f.kind !== 'dict' ? f.read : camel(col)];
  }

  /** Значение для просмотра — как его показывает карточка. */
  function viewValue(f){
    if (f.show) return esc(f.show());
    const v = state.row[f.read || idOf(f)];
    if (f.kind === 'bool') return esc(PORTAL.yesNo(v === null || v === undefined ? v : !!v));
    if (f.kind === 'date') return esc(ruDate(v));
    return esc(dash(v));
  }

  function viewHtml(f){
    return '<div class="kv"><label>' + labelOf(f) + '</label><div>' + viewValue(f) + '</div></div>';
  }

  function editHtml(f){
    const v = editValue(f);
    // и ввод, и выбор (дата, список) — одна функция, один набор аргументов
    const attrs = 'data-col="' + f.col + '"' + PORTAL.callOnInput('PORTAL.skuCard.field', PORTAL.SELF)
        + ' data-change="PORTAL.skuCard.field"';
    let input;
    if (f.kind === 'dict'){
      const list = (state.dict && f.dict && state.dict[f.dict]) || [];
      input = '<select class="kv-input kv-input-editable" ' + attrs + '><option value="">—</option>'
        + list.map(o => '<option value="' + esc(o.id) + '"'
            + (String(o.id) === String(v) ? ' selected' : '') + '>' + esc(o.name) + '</option>').join('')
        + '</select>';
    } else if (f.kind === 'suggest'){
      // Ввод свободный, но с подсказкой уже использованных значений.
      const list = (state.dict && f.suggest && state.dict[f.suggest]) || [];
      const listId = 'skuSuggest_' + f.col;
      input = '<input class="kv-input kv-input-editable" type="text" list="' + listId + '" value="'
        + esc(v === null || v === undefined ? '' : v) + '" ' + attrs + '/>'
        + '<datalist id="' + listId + '">'
        + list.map(o => '<option value="' + esc(o.name) + '"></option>').join('') + '</datalist>';
    } else if (f.kind === 'bool'){
      input = '<select class="kv-input kv-input-editable" ' + attrs + '>'
        + '<option value="false"' + (v ? '' : ' selected') + '>' + esc(T('common.value.no')) + '</option>'
        + '<option value="true"' + (v ? ' selected' : '') + '>' + esc(T('common.value.yes')) + '</option></select>';
    } else {
      input = '<input class="kv-input kv-input-editable" type="' + (f.kind === 'num' ? 'number' : 'text') + '"'
        + (f.kind === 'num' ? ' step="any"' : '') + ' value="' + esc(v === null || v === undefined ? '' : v)
        + '" ' + attrs + '/>';
    }
    return '<div class="kv"><label>' + labelOf(f) + (f.required ? ' *' : '')
      + '</label><div style="min-width:280px;">' + input + '</div></div>';
  }

  /* ===================== отрисовка ===================== */

  /** Кнопки действий в шапке карточки — там, где у документов. */
  function headActions(){
    if (state.mode !== 'view'){
      return '<button class="btn primary"' + PORTAL.call('PORTAL.skuCard.save') + '>' + esc(T('portal.text.save')) + '</button>'
        + '<button class="btn ghost"' + PORTAL.call('PORTAL.skuCard.cancel') + '>' + esc(T('common.button.cancel')) + '</button>';
    }
    if (!mayEdit() || !state.skuId) return '';
    const archived = state.row.isActive === false;
    return '<button class="btn primary"' + PORTAL.call('PORTAL.skuCard.edit') + '>' + esc(T('sku.text.edit')) + '</button>'
      + '<button class="btn"' + PORTAL.call('PORTAL.skuCard.archive', archived) + '>'
      + esc(archived ? T('sku.text.restore_from_archive') : T('portal.text.archive')) + '</button>'
      + '<button class="btn ghost"' + PORTAL.call('PORTAL.skuCard.remove') + '>' + esc(T('common.button.delete')) + '</button>';
  }

  function bodyHtml(tab){
    if (tab.id === 'packaging') return packagingTabHtml();
    if (tab.id === 'kit') return kitTabHtml();
    if (tab.id === 'history' || tab.id === 'reports') return '';
    const visible = tab.fields.filter(allowed);
    if (state.mode === 'view'){
      return '<div class="kv-grid">' + visible.map(viewHtml).join('') + '</div>';
    }
    // В правке — только то, что правится: остальное ставит WMS или портал.
    const editable = visible.filter(f => f.col);
    if (!tab.fields.some(f => f.col)){
      return '<div style="color:var(--muted); font-size:13px;">' + esc(T('sku.hint.fields_set_by_wms')) + '</div>';
    }
    if (!editable.length){
      return '<div style="color:var(--muted); font-size:13px;">' + esc(T('sku.text.no_fields_on_this_tab')) + '</div>';
    }
    return '<div class="kv-grid">' + editable.map(editHtml).join('') + '</div>'
      + '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
      + esc(T('sku.hint.required_and_one_save')) + '</div>';
  }

  function render(){
    const head = document.querySelector('#view-sku-detail .panel-head');
    if (head){
      let actions = head.querySelector('.actions');
      if (!actions){
        actions = document.createElement('div');
        actions.className = 'actions';
        head.appendChild(actions);
      }
      actions.innerHTML = headActions();
    }
    const title = el('skuDetailTitle');
    if (state.mode === 'create') title.textContent = T('sku.text.new_item');
    else if (state.row.isActive === false){
      title.innerHTML = esc(state.row.name) + ' <span class="count">' + esc(T('partners.text.archived')) + '</span>';
    } else title.textContent = state.row.name || '';

    // «История» и «Отчёты» — у сохранённого товара и не во время правки
    const tabs = TABS.filter(t => t.id === 'kit' ? isKit() || !!state.kitForm
        : (t.id !== 'history' && t.id !== 'reports')
          || (state.skuId && state.mode === 'view' && (t.id !== 'reports' || !!PORTAL.clientReports)));
    if (!tabs.some(t => t.id === state.tab)) state.tab = 'general';
    el('skuDetailTabs').innerHTML = tabs.map(t =>
        '<button class="admin-subtab-btn' + (t.id === state.tab ? ' active' : '') + '" data-tab="' + t.id + '"'
        + PORTAL.call('PORTAL.skuCard.tab', t.id) + '>' + PORTAL.t(t.key) + '</button>').join('');
    const tab = TABS.find(t => t.id === state.tab) || TABS[0];
    el('skuDetailBody').innerHTML = bodyHtml(tab);
    if (tab.id === 'reports' && state.skuId && PORTAL.clientReports){
      PORTAL.clientReports.show(el('skuDetailBody'), {place: '06', skuId: state.skuId});
    }
    if (tab.id === 'history' && state.skuId){
      const skuId = state.skuId;
      PORTAL.history.show(el('skuDetailBody'), PORTAL.history.url('sku', skuId),
          {stillWanted: () => state.skuId === skuId && state.tab === 'history'});
    }
    showView('sku-detail');
  }

  /* ===================== состав комплекта ===================== */

  /** Комплект — у сохранённого товара с признаком или с составом из WMS. */
  function isKit(){
    return !!state.skuId && PORTAL.kitsEnabled() && (state.row.isKit === true || state.row.isKit === 'true'
        || (state.row.kitComponents || []).length > 0);
  }

  function kitTabHtml(){
    if (state.kitForm) return kitFormHtml();
    const rows = /** @type {any[]} */ (state.row.kitComponents || []);
    const canEdit = mayEditKit() && state.mode === 'view';
    const hint = '<div class="hint" style="margin-bottom:8px;">' + PORTAL.t('sku.hint.kit_from_wms') + '</div>';
    const edit = canEdit
      ? '<div style="margin-top:12px;"><button class="btn" data-kit-edit' + PORTAL.call('PORTAL.skuCard.kitEdit') + '>'
        + esc(T('sku.button.kit_edit')) + '</button></div>'
      : '';
    if (!rows.length){
      return hint + '<div style="color:var(--muted); font-size:13px;">' + esc(T('sku.text.kit_empty')) + '</div>' + edit;
    }
    const th = (key, num) => '<th' + (num ? ' class="num"' : '') + '>' + esc(T(key)) + '</th>';
    return hint + '<div class="table-card" style="margin-top:4px;"><table data-kit-components><thead><tr>'
      + th('references.column.code') + th('references.column.name') + th('sku.column.kit_qty', true)
      + th('sku.text.base_unit_of_measure') + '</tr></thead><tbody>'
      + rows.map(r => '<tr data-kit-component="' + esc(r.code) + '"><td><strong>' + esc(r.code) + '</strong></td>'
          + '<td>' + esc(r.name) + '</td><td class="num">' + esc(r.qty) + '</td>'
          + '<td>' + esc(dash(r.unitName)) + '</td></tr>').join('')
      + '</tbody></table></div>' + edit;
  }

  /** Товары, которые могут быть комплектующими: не сам товар и не комплекты. */
  function componentChoices(){
    const own = state.row.code;
    return /** @type {any[]} */ (PORTAL.catalog.sku || []).filter(s => s.code !== own
        && !(s.isKit === true || s.isKit === 'true'));
  }

  /** Правка состава: код (из списка товаров), наименование, количество на один комплект. */
  function kitFormHtml(){
    const rows = state.kitForm || [];
    const byCode = new Map(/** @type {any[]} */ (PORTAL.catalog.sku || []).map(s => [s.code, s]));
    const th = (key, num) => '<th' + (num ? ' class="num"' : '') + '>' + esc(T(key)) + '</th>';
    const body = rows.map((r, i) => {
      const sku = byCode.get(r.code);
      return '<tr data-kit-row="' + i + '">'
        + '<td><input class="kv-input kv-input-editable" list="kitComponentChoices" data-kit-code value="'
        + esc(r.code) + '"' + PORTAL.callOnChange('PORTAL.skuCard.kitField', i, 'code', PORTAL.VALUE) + '></td>'
        + '<td data-kit-name>' + esc(sku ? sku.name : '') + '</td>'
        + '<td class="num"><input class="kv-input kv-input-editable" type="number" step="any" min="0" data-kit-qty'
        + ' style="max-width:110px;" value="' + esc(r.qty) + '"'
        + PORTAL.callOnInput('PORTAL.skuCard.kitField', i, 'qty', PORTAL.VALUE) + '></td>'
        + '<td>' + esc(dash(sku ? sku.uomName : '')) + '</td>'
        + '<td style="text-align:right;"><button class="btn ghost icon" data-kit-remove'
        + PORTAL.call('PORTAL.skuCard.kitRemove', i) + ' aria-label="' + esc(T('common.button.delete')) + '">✕</button></td>'
        + '</tr>';
    }).join('');
    return '<div class="hint" style="margin-bottom:8px;">' + esc(T('sku.hint.kit_manual')) + '</div>'
      + '<datalist id="kitComponentChoices">' + componentChoices().map(s =>
          '<option value="' + esc(s.code) + '">' + esc(s.name) + '</option>').join('') + '</datalist>'
      + '<div class="table-card" style="margin-top:4px;"><table data-kit-form><thead><tr>'
      + th('references.column.code') + th('references.column.name') + th('sku.column.kit_qty', true)
      + th('sku.text.base_unit_of_measure') + '<th></th></tr></thead><tbody>' + body + '</tbody></table></div>'
      + '<div style="margin-top:12px; display:flex; gap:8px;">'
      + '<button class="btn ghost" data-kit-add' + PORTAL.call('PORTAL.skuCard.kitAdd') + '>'
      + esc(T('sku.button.kit_add_component')) + '</button>'
      + '<span style="flex:1;"></span>'
      + '<button class="btn" data-kit-cancel' + PORTAL.call('PORTAL.skuCard.kitCancel') + '>'
      + esc(T('common.button.cancel')) + '</button>'
      + '<button class="btn primary" data-kit-save' + PORTAL.call('PORTAL.skuCard.kitSave') + '>'
      + esc(T('sku.button.kit_save')) + '</button></div>';
  }

  function kitEdit(){
    state.kitForm = /** @type {any[]} */ (state.row.kitComponents || [])
        .map(r => ({code: String(r.code), qty: String(r.qty)}));
    if (!state.kitForm.length) state.kitForm.push({code: '', qty: '1'});
    state.tab = 'kit';
    render();
  }

  /** Поле строки состава: код перерисовывает строку (наименование), количество — нет. */
  function kitField(i, name, value){
    if (!state.kitForm || !state.kitForm[i]) return;
    state.kitForm[i][name] = String(value).trim();
    if (name === 'code') render();
  }

  async function kitSave(){
    if (!state.kitForm) return;
    const rows = state.kitForm.filter(r => r.code !== '');
    try {
      const card = await api('PUT', '/api/sku/' + state.skuId + '/kit', {components: rows});
      state.row = card;
      state.packagings = card.packagings || [];
      state.kitForm = null;
      await PORTAL.reloadProducts();
      // составы окна строки документа — заново: поступление раскладывает по ним
      if (PORTAL.reloadDirectories) await PORTAL.reloadDirectories();
      render();
      say(T((card.kitComponents || []).length ? 'sku.message.kit_saved' : 'sku.message.kit_cleared'));
    } catch (e){
      say(e.message);
    }
  }

  /** Из «Справочники → Комплекты»: карточка товара сразу на вкладке «Состав». */
  async function openKit(skuId, editNow){
    try {
      state.mode = 'view';
      state.tab = 'kit';
      state.kitForm = null;
      await openCard(skuId);
      if (editNow && mayEditKit()) kitEdit();
    } catch (e){
      say(T('common.message.card_open_failed', {error: e.message}));
    }
  }

  /* ===================== упаковки ===================== */

  function packagingTabHtml(){
    if (!state.skuId){
      return '<div style="color:var(--muted); font-size:13px;">' + esc(T('sku.hint.save_product_first')) + '</div>';
    }
    const rows = state.packagings;
    const types = (state.dict && state.dict.packaging_type) || [];
    const canEdit = mayEdit() && state.mode === 'view';
    const th = (key, num) => '<th' + (num ? ' class="num"' : '') + '>' + esc(T(key)) + '</th>';

    const table = rows.length
      ? '<div class="table-card" style="margin-top:4px;"><table><thead><tr>'
        + th('references.column.packaging_code') + th('references.column.packaging_type')
        + th('sku.text.packaging_barcode') + th('sku.text.factor', true) + th('sku.label.length_cm', true)
        + th('references.column.width_cm', true) + th('sku.text.height_cm', true)
        + th('sku.label.volume_m', true) + th('sku.text.gross_weight_kg', true) + th('col.sku.orderUnit')
        + (canEdit ? '<th></th>' : '') + '</tr></thead><tbody>'
        + rows.map(r => '<tr><td><strong>' + esc(r.packagingCode) + '</strong></td><td>'
            + esc(dash(r.packagingTypeName)) + '</td><td>' + esc(dash(r.barcode))
            + '</td><td class="num">' + esc(dash(r.coefficient)) + '</td><td class="num">' + esc(dash(r.lengthCm))
            + '</td><td class="num">' + esc(dash(r.widthCm)) + '</td><td class="num">' + esc(dash(r.heightCm))
            + '</td><td class="num">' + esc(dash(r.volumeM3)) + '</td><td class="num">' + esc(dash(r.grossWeightKg))
            + '</td><td>' + (r.isDefaultOrderUnit
                ? '★ ' + esc(T('sku.text.by_default'))
                : (canEdit ? '<button class="btn ghost"' + PORTAL.call('PORTAL.skuCard.packagingDefault', r.packagingId)
                    + '>' + esc(T('sku.text.set_as_order_unit')) + '</button>' : '—'))
            + '</td>'
            + (canEdit ? '<td style="white-space:nowrap;">'
                + '<button class="btn ghost"' + PORTAL.call('PORTAL.skuCard.packagingEdit', r.packagingId) + '>'
                + esc(T('portal.text.edit2')) + '</button> '
                + '<button class="btn ghost"' + PORTAL.call('PORTAL.skuCard.packagingDelete', r.packagingId) + '>'
                + esc(T('common.button.delete')) + '</button></td>' : '')
            + '</tr>').join('')
        + '</tbody></table></div>'
      : '<div style="color:var(--muted); font-size:13px;">' + esc(T('sku.text.no_packaging_defined_for_this_item')) + '</div>';

    let form = '';
    if (state.pkgForm){
      const f = state.pkgForm.values;
      const byDimensions = volumeOf(f) !== null;
      if (byDimensions) f.volume_m3 = volumeOf(f);
      const input = (col, label, num) => '<div class="kv"><label>' + esc(label) + '</label><div>'
        + '<input class="kv-input kv-input-editable" type="' + (num ? 'number' : 'text') + '"'
        + (num ? ' step="any"' : '') + ' value="' + esc(f[col] === undefined || f[col] === null ? '' : f[col])
        + '" data-pkg="' + col + '"' + PORTAL.callOnInput('PORTAL.skuCard.packagingField', PORTAL.SELF)
        + (col === 'volume_m3' && byDimensions ? ' readonly style="background:var(--bg);"' : '') + '/>'
        + (col === 'volume_m3'
            ? '<div data-pkg-volume-hint style="max-width:200px;margin-top:4px;font-size:11.5px;'
              + 'line-height:1.3;color:var(--muted);' + (byDimensions ? '' : 'display:none;') + '">'
              + PORTAL.t('sku.hint.volume_from_dimensions') + '</div>'
            : '')
        + '</div></div>';
      form = '<div class="table-card" style="margin-top:12px; padding:12px;">'
        + '<div class="section-title">' + esc(T(state.pkgForm.packagingId ? 'sku.text.edit_packaging' : 'sku.text.new_packaging'))
        + '</div><div class="kv-grid">'
        + input('packaging_code', T('sku.text.packaging_code2'))
        + '<div class="kv"><label>' + esc(T('references.column.packaging_type')) + '</label><div><select class="kv-input kv-input-editable"'
          + ' data-pkg="packaging_type_id"' + PORTAL.callOnChange('PORTAL.skuCard.packagingField', PORTAL.SELF)
          + '><option value="">—</option>'
          + types.map(t => '<option value="' + esc(t.id) + '"'
              + (String(t.id) === String(f.packaging_type_id) ? ' selected' : '') + '>' + esc(t.name) + '</option>').join('')
          + '</select></div></div>'
        + input('barcode', T('sku.text.packaging_barcode'))
        + input('coefficient', T('sku.text.factor2'), true)
        + input('length_cm', T('sku.label.length_cm'), true)
        + input('width_cm', T('references.column.width_cm'), true)
        + input('height_cm', T('sku.text.height_cm'), true)
        + input('volume_m3', T('sku.label.volume_m'), true)
        + input('gross_weight_kg', T('sku.text.gross_weight_kg'), true)
        + '</div><div style="margin-top:12px;">'
        + '<button class="btn primary"' + PORTAL.call('PORTAL.skuCard.packagingSave') + '>' + esc(T('sku.text.save_packaging')) + '</button> '
        + '<button class="btn ghost"' + PORTAL.call('PORTAL.skuCard.packagingCancel') + '>' + esc(T('common.button.cancel')) + '</button></div></div>';
    } else if (canEdit){
      form = '<div style="margin-top:12px;"><button class="btn"' + PORTAL.call('PORTAL.skuCard.packagingAdd') + '>'
        + esc(T('sku.text.add_packaging')) + '</button></div>';
    }
    return table + form;
  }

  /**
   * Объём по размерам — то же правило, что у триггера в базе (V82):
   * все три размера больше нуля → Д × Ш × В (см) / 1 000 000, пять
   * знаков. Иначе null: объём вводится руками. Окончательно считает
   * база; здесь — чтобы человек видел число сразу.
   */
  const DIMENSIONS = ['length_cm', 'width_cm', 'height_cm'];

  function volumeOf(values){
    const n = DIMENSIONS.map(c => Number(values[c]));
    if (n.some(x => !(x > 0))) return null;
    // В целых кубических сантиметрах до деления — без хвостов 0.0240000001
    return Number((n[0] * n[1] * n[2] / 1e6).toFixed(5));
  }

  /** Поле «Объём» вслед за размерами — без перерисовки, фокус остаётся. */
  function syncVolume(grid){
    if (!grid || !state.pkgForm) return;
    const values = state.pkgForm.values;
    const field = grid.querySelector('[data-pkg="volume_m3"]');
    const hint = grid.querySelector('[data-pkg-volume-hint]');
    if (!field) return;
    const volume = volumeOf(values);
    if (volume !== null){
      values.volume_m3 = volume;
      field.value = volume;
      field.readOnly = true;
      field.style.background = 'var(--bg)';
    } else {
      // Размер стёрли — объём снова вводится руками; посчитанное остаётся.
      field.readOnly = false;
      field.style.background = '';
    }
    if (hint) hint.style.display = volume !== null ? '' : 'none';
  }

  const PKG_FIELDS = ['packaging_code', 'packaging_type_id', 'barcode', 'coefficient',
                      'length_cm', 'width_cm', 'height_cm', 'volume_m3', 'gross_weight_kg'];

  /**
   * Упаковки изменились: перечитать список упаковок и справочники окна
   * строки документа — иначе там предлагалась бы удалённая упаковка.
   */
  async function packagingsChanged(){
    if (typeof PORTAL.reloadPackaging === 'function') await PORTAL.reloadPackaging();
    if (typeof PORTAL.reloadDirectories === 'function') PORTAL.reloadDirectories();
  }

  async function packagingSave(){
    const form = state.pkgForm;
    if (!form) return;
    try {
      state.packagings = form.packagingId
        ? await api('PUT', '/api/packagings/' + form.packagingId, form.values)
        : await api('POST', '/api/sku/' + state.skuId + '/packagings', form.values);
      state.pkgForm = null;
      await packagingsChanged();
      render();
      say(T('sku.message.packaging_saved'));
    } catch (e){
      say(e.message);
    }
  }

  async function packagingDefault(id){
    try {
      state.packagings = await api('POST', '/api/packagings/' + id + '/default');
      await PORTAL.reloadProducts();
      await packagingsChanged();
      render();
    } catch (e){
      say(e.message);
    }
  }

  async function packagingDelete(id){
    const r = state.packagings.find(p => p.packagingId === id);
    if (!window.confirm(T('sku.message.confirm_delete_packaging', {code: r ? r.packagingCode : id}))) return;
    try {
      state.packagings = await api('DELETE', '/api/packagings/' + id);
      await packagingsChanged();
      render();
      say(T('sku.text.packaging_deleted'));
    } catch (e){
      say(e.message);
    }
  }

  /* ===================== открыть, править, сохранить ===================== */

  /** Карточка из API — вместе с упаковками одним запросом. */
  async function openCard(skuId){
    const card = await api('GET', '/api/sku/' + skuId);
    await dictionaries();
    state.skuId = skuId;
    state.row = card;
    state.packagings = card.packagings || [];
    state.draft = {};
    state.pkgForm = null;
    state.kitForm = null;
    render();
  }

  /** Щелчок по строке списка товаров или упаковок (имя зовёт PORTAL.tables.row). */
  window.portalOpenSku = function(code){
    const row = (PORTAL.catalog.sku || []).find(s => s.code === code);
    if (!row){
      say(T('sku.message.not_in_loaded_list', {code: code}));
      return;
    }
    state.mode = 'view';
    state.tab = 'general';
    openCard(row.skuId).catch(e => say(T('common.message.card_open_failed', {error: e.message})));
  };

  async function create(){
    try {
      await dictionaries();
      Object.assign(state, {mode: 'create', skuId: null, row: {}, draft: {}, packagings: [],
                            pkgForm: null, kitForm: null, tab: 'general'});
      render();
    } catch (e){
      say(T('sku.message.new_sku_open_failed', {error: e.message}));
    }
  }

  function cancel(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      showTab('references');
      showReferencesSub('sku');
      return;
    }
    state.mode = 'view';
    render();
  }

  async function save(){
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      render();
      return;
    }
    try {
      const card = creating
        ? await api('POST', '/api/sku', state.draft)
        : await api('PUT', '/api/sku/' + state.skuId, state.draft);
      Object.assign(state, {mode: 'view', skuId: card.skuId, row: card,
                            packagings: card.packagings || [], draft: {}});
      await PORTAL.reloadProducts();
      render();
      say(T(creating ? 'sku.text.item_created' : 'sku.text.changes_saved'));
    } catch (e){
      say(e.message);
    }
  }

  async function archive(restore){
    try {
      const card = await api('POST', '/api/sku/' + state.skuId + '/archive', {active: restore});
      state.row = card;
      state.packagings = card.packagings || [];
      await PORTAL.reloadProducts();
      render();
      say(T(restore ? 'sku.text.item_restored' : 'sku.text.item_archived'));
    } catch (e){
      say(e.message);
    }
  }

  async function remove(){
    // Подтверждение нужно: удаление необратимо, в отличие от архива
    if (!window.confirm(T('sku.message.confirm_delete_item', {code: state.row.code || ''})
        + '\n' + T('sku.text.if_it_is_used_anywhere_the_portal'))) return;
    try {
      await api('DELETE', '/api/sku/' + state.skuId);
      await PORTAL.reloadProducts();
      say(T('sku.message.item_deleted'));
      showTab('references');
      showReferencesSub('sku');
    } catch (e){
      say(e.message);
    }
  }

  /* ===================== наружу ===================== */

  PORTAL.skuCard = {
    state,
    create,
    edit: () => { state.mode = 'edit'; state.draft = {}; render(); },
    cancel,
    save,
    archive,
    remove,
    tab: id => { state.tab = id; render(); },
    /** Поле правки изменилось: запоминаем, отправится по «Сохранить». */
    field: el => {
      const col = el.getAttribute('data-col') || '';
      const v = el.value;
      // да/нет уходит логическим значением, пустое — как «нет значения»
      state.draft[col] = el.tagName === 'SELECT' && (v === 'true' || v === 'false') ? v === 'true'
          : (v === '' ? null : v);
    },
    packagingAdd: () => { state.pkgForm = {packagingId: null, values: {}}; render(); },
    packagingEdit: id => {
      const r = state.packagings.find(p => p.packagingId === id);
      if (!r) return;
      /** @type {CardRow} */
      const values = {};
      PKG_FIELDS.forEach(col => { values[col] = /** @type {CardRow} */ (r)[camel(col)]; });
      state.pkgForm = {packagingId: id, values: values};
      render();
    },
    packagingCancel: () => { state.pkgForm = null; render(); },
    kitEdit,
    kitField,
    kitAdd: () => { if (state.kitForm){ state.kitForm.push({code: '', qty: '1'}); render(); } },
    kitRemove: i => { if (state.kitForm){ state.kitForm.splice(i, 1); render(); } },
    kitCancel: () => { state.kitForm = null; render(); },
    kitSave,
    openKit,
    packagingSave,
    packagingDefault,
    packagingDelete,
    packagingField: el => {
      if (!state.pkgForm) return;
      const col = el.getAttribute('data-pkg') || '';
      state.pkgForm.values[col] = el.value === '' ? null : el.value;
      if (DIMENSIONS.includes(col)) syncVolume(el.closest('.kv-grid'));
    }
  };
})();
