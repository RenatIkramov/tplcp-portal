
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /** Кто ведёт справочник. Кладовщик и разработчик — только смотрят. */
  const EDITOR_ROLES = ['depositor', 'manager', 'admin'];
  const mayEdit = () => EDITOR_ROLES.includes(PORTAL.session && PORTAL.session.roleCode);

  // Состояние карточки держим в одном объекте: вкладки перерисовывают
  // тело целиком, и после переключения вкладки введённое должно остаться.
  const state = {
    mode: 'view',      // view | edit | create
    skuId: null,
    draft: {},         // только изменённые поля — их и отправляем
    packagings: [],
    pkgForm: null,     // строка упаковки в правке: {packagingId|null, values}
    dict: null
  };
  PORTAL.skuEdit = state;

  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
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

  /* ================= Какие поля правятся и на какой вкладке =================
     Названия вкладок и подписи совпадают с прототипом: в режиме просмотра
     человек видит ровно те же строки, просто без полей ввода. Поля, которых
     здесь нет, портал не меняет — их ставит синхронизация с WMS или сам
     портал (идентификатор, статус, аудит, единица заказа).
  ========================================================================= */
  // Заголовок вкладки переводится, поэтому опознавать её по тексту нельзя:
  // держим ключ, а подпись берём через T() — так же, как её рисует прототип.
  /**
   * Доступно ли поле этому поклажедателю.
   *
   * TABS хранит имена полей базы (weight_gross), а справочник колонок —
   * идентификаторы карточки (weightGross), и у шести полей-ссылок они
   * расходятся по-особому (uom_id -> unit). Те же правила, что
   * в SkuTemplateFields на сервере: список один, иначе разъедутся.
   */
  const COL_OF = {
    uom_id: 'unit', item_group: 'group', item_type: 'productType',
    currency_id: 'currency', hazard_class_id: 'hazardClass',
    hazard_group_id: 'hazardGroup'
  };
  function fieldAllowed(column){
    const id = COL_OF[column]
      || column.replace(/_([a-z0-9])/g, (m, c) => c.toUpperCase());
    return !PORTAL.fieldAllowed || PORTAL.fieldAllowed(id);
  }

  const TABS = {
    'sku.text.general_information': [
      {col: 'item_code', label: T('col.sku.itemCode')},
      {col: 'code', label: T('sku.text.commercial_code'), required: true},
      {col: 'name', label: T('col.sku.name'), required: true},
      {col: 'item_full_name', label: T('sku.label.full_item_name')},
      // Справочников групп и типов больше нет (V38): это свободный
      // текст. Подсказка — значения, которые у этого клиента уже
      // встречаются, чтобы «Сырьё» не превратилось в «сырье», «Сырьё.»
      // и «Сырье » тремя разными группами.
      {col: 'item_group', label: T('sku.label.product_group'), suggest: 'group',
       readCol: 'itemGroupName'},
      {col: 'item_type', label: T('stock.label.item_type'), suggest: 'type',
       readCol: 'itemTypeName'},
      {col: 'uom_id', label: T('sku.text.base_unit_of_measure'), dict: 'uom', required: true},
      {col: 'barcode', label: T('sku.text.barcode')}
    ],
    'sku.text.physical_properties': [
      {col: 'length', label: T('sku.label.length_cm'), num: true},
      {col: 'width', label: T('references.column.width_cm'), num: true},
      {col: 'height', label: T('sku.text.height_cm'), num: true},
      {col: 'volume', label: T('sku.label.volume_m'), num: true},
      {col: 'weight_gross', label: T('sku.text.gross_weight_kg'), num: true},
      {col: 'weight_net', label: T('sku.label.net_weight_kg'), num: true},
      {col: 'color', label: T('col.sku.color')},
      {col: 'size', label: T('col.sku.size')}
    ],
    'sku.text.storage_conditions': [
      {col: 'hazard_class_id', label: T('col.sku.hazardClass'), dict: 'hazard_class'},
      {col: 'hazard_group_id', label: T('sku.text.hazard_group'), dict: 'hazard_group'},
      {col: 'max_storage_temp', label: T('sku.text.max_storage_temperature_c'), num: true},
      {col: 'min_storage_temp', label: T('sku.text.min_storage_temperature_c'), num: true},
      {col: 'max_humidity', label: T('sku.label.max_storage_humidity'), num: true},
      {col: 'min_humidity', label: T('sku.label.min_storage_humidity'), num: true},
      {col: 'min_days_for_receipt', label: T('sku.text.minimum_days_for_receiving'), num: true},
      {col: 'min_days_for_reservation', label: T('sku.label.minimum_days_for_reservation'), num: true},
      {col: 'shelf_life_days', label: T('col.sku.shelfLifeDays'), num: true}
    ],
    'sku.text.manufacturer_and_classification': [
      {col: 'manufacturer', label: T('sku.text.manufacturer')},
      {col: 'country_origin', label: T('col.sku.countryOrigin')},
      {col: 'brand', label: T('sku.label.brand')},
      {col: 'abc_class', label: T('sku.label.abc_class')},
      {col: 'xyz_class', label: T('sku.label.xyz_class')}
    ],
    'sku.text.codes_and_identifiers': [
      {col: 'hs_code', label: T('sku.text.hs_code')},
      {col: 'enstru_code', label: T('sku.text.enstru_code')},
      {col: 'currency_id', label: T('col.sku.currency'), dict: 'currency'},
      {col: 'cost', label: T('sku.text.cost'), num: true}
    ],
    'sku.text.attributes_and_tracking': [
      {col: 'is_fragile', label: T('sku.label.fragile'), bool: true},
      {col: 'is_serial_tracked', label: T('sku.text.serial_number_tracking'), bool: true},
      {col: 'batch_tracking', label: T('sku.label.batch_tracking'), yesNoText: true},
      {col: 'expiry_tracking', label: T('col.sku.expiryTracking'), yesNoText: true},
      {col: 'is_kit', label: T('col.sku.isKit'), bool: true},
      {col: 'is_marking_required', label: T('sku.label.mandatory_labelling'), bool: true},
      {col: 'is_excise', label: T('sku.label.excisable_goods'), bool: true},
      {col: 'is_controlled', label: T('sku.label.controlled_licensed_goods'), bool: true}
    ],
    'sku.text.system_information': [
      {col: 'comment', label: T('sku.label.comment')}
    ],
    // Доп. параметров в базе десять, и правятся все десять.
    'sku.text.custom_attributes': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => ({
      col: 'custom_attr' + i, label: T('sku.label.custom_attribute')+' '+i
    }))
  };

  /** Колонка базы → поле строки API (v_sku_full отдаёт их в camelCase). */
  const camel = col => col.replace(/_([a-z])/g, (m, c) => c.toUpperCase());

  /** Что показать в поле: сначала несохранённое, потом из карточки. */
  function value(field){
    if (Object.prototype.hasOwnProperty.call(state.draft, field.col)) {
      return state.draft[field.col];
    }
    const row = state.row || {};
    // Поле отправляется под именем колонки базы, а читается из
    // представления: у группы и типа имена расходятся (item_group ->
    // itemGroupName), поэтому место чтения задаётся явно.
    return row[field.readCol || camel(field.col)];
  }

  /* ================= Карточка товара ================= */

  const PACKAGING_TAB = 'common.label.packaging';

  /** Подпись активной вкладки → её ключ. */
  function tabKey(title){
    const keys = Object.keys(TABS).concat([PACKAGING_TAB]);
    return keys.find(k => (typeof T === 'function' ? T(k) : k) === title) || title;
  }

  function fieldHtml(field){
    const v = value(field);
    const name = 'data-col="' + field.col + '" oninput="portalSkuField(this)"'
               + ' onchange="portalSkuField(this)"';
    let input;
    if (field.dict){
      const list = (state.dict && state.dict[field.dict]) || [];
      input = '<select class="kv-input" ' + name + '><option value="">—</option>'
        + list.map(o => '<option value="' + esc(o.id) + '"'
            + (String(o.id) === String(v) ? ' selected' : '') + '>' + esc(o.name) + '</option>').join('')
        + '</select>';
    } else if (field.suggest){
      // Ввод свободный, но с подсказкой уже использованных значений:
      // datalist предлагает их списком и не мешает написать своё.
      const list = (state.dict && state.dict[field.suggest]) || [];
      const listId = 'skuSuggest_' + field.col;
      input = '<input class="kv-input" type="text" list="' + listId + '" value="'
        + esc(v === null || v === undefined ? '' : v) + '" ' + name + '/>'
        + '<datalist id="' + listId + '">'
        + list.map(o => '<option value="' + esc(o.name) + '"></option>').join('')
        + '</datalist>';
    } else if (field.bool){
      input = '<select class="kv-input" ' + name + '>'
        + '<option value="false"' + (v ? '' : ' selected') + '>' + T('sku.text.no') + '</option>'
        + '<option value="true"' + (v ? ' selected' : '') + '>' + T('sku.text.yes') + '</option></select>';
    } else if (field.yesNoText){
      // в базе это текст «Да»/«Нет», а не логический признак
      input = '<select class="kv-input" ' + name + '><option value="">—</option>'
        + [T('sku.text.yes'), T('sku.text.no')].map(o => '<option' + (o === v ? ' selected' : '') + '>' + o + '</option>').join('')
        + '</select>';
    } else {
      input = '<input class="kv-input" type="' + (field.num ? 'number' : 'text') + '"'
        + (field.num ? ' step="any"' : '') + ' value="' + esc(v === null || v === undefined ? '' : v)
        + '" ' + name + '/>';
    }
    return '<div class="kv"><label>' + esc(field.label) + (field.required ? ' *' : '')
      + '</label><div style="min-width:280px;">' + input + '</div></div>';
  }

  window.portalSkuField = function(el){
    const col = el.getAttribute('data-col');
    let v = el.value;
    if (el.tagName === 'SELECT' && (v === 'true' || v === 'false')) v = (v === 'true');
    state.draft[col] = v === '' ? null : v;
  };

  /** Кнопки действий в шапке карточки — там, где у документов. */
  function headActions(){
    if (state.mode !== 'view'){
      return '<button class="btn primary" onclick="portalSkuSave()">' + T('portal.text.save') + '</button>'
        + '<button class="btn ghost" onclick="portalSkuCancel()">' + T('common.button.cancel') + '</button>';
    }
    if (!mayEdit() || !state.skuId) return '';
    const archived = state.row && state.row.isActive === false;
    return '<button class="btn primary" onclick="portalSkuEdit()">' + T('sku.text.edit') + '</button>'
      + '<button class="btn" onclick="portalSkuArchive(' + (archived ? 'true' : 'false') + ')">'
      + (archived ? T('sku.text.restore_from_archive') : T('portal.text.archive')) + '</button>'
      + '<button class="btn ghost" onclick="portalSkuDelete()">' + T('common.button.delete') + '</button>';
  }

  function packagingTabHtml(){
    if (!state.skuId){
      return '<div style="color:var(--muted); font-size:13px;">'
        + 'Сначала сохраните товар — упаковки задаются уже для него</div>';
    }
    const rows = state.packagings;
    const types = (state.dict && state.dict.packaging_type) || [];
    const canEdit = mayEdit() && state.mode === 'view';

    const table = rows.length
      ? '<div class="table-card" style="margin-top:4px;"><table><thead><tr>'
        + '<th>' + T('references.column.packaging_code') + '</th><th>' + T('references.column.packaging_type') + '</th><th>' + T('sku.text.packaging_barcode') + '</th>'
        + '<th class="num">' + T('sku.text.factor') + '</th><th class="num">' + T('sku.label.length_cm') + '</th><th class="num">' + T('references.column.width_cm') + '</th>'
        + '<th class="num">' + T('sku.text.height_cm') + '</th><th class="num">' + T('sku.label.volume_m') + '</th><th class="num">' + T('sku.text.gross_weight_kg') + '</th>'
        + '<th>' + T('col.sku.orderUnit') + '</th>' + (canEdit ? '<th></th>' : '') + '</tr></thead><tbody>'
        + rows.map(r => '<tr><td><strong>' + esc(r.packagingCode) + '</strong></td><td>'
            + dash(esc(r.packagingTypeName)) + '</td><td>' + dash(esc(r.barcode))
            + '</td><td class="num">' + dash(r.coefficient) + '</td><td class="num">' + dash(r.lengthCm)
            + '</td><td class="num">' + dash(r.widthCm) + '</td><td class="num">' + dash(r.heightCm)
            + '</td><td class="num">' + dash(r.volumeM3) + '</td><td class="num">' + dash(r.grossWeightKg)
            + '</td><td>' + (r.isDefaultOrderUnit
                ? '★ по умолчанию'
                : (canEdit ? '<button class="btn ghost" onclick="portalPackagingDefault(\'' + r.packagingId
                    + '\')">' + T('sku.text.set_as_order_unit') + '</button>' : '—'))
            + '</td>'
            + (canEdit ? '<td style="white-space:nowrap;">'
                + '<button class="btn ghost" onclick="portalPackagingEdit(\'' + r.packagingId + '\')">' + T('portal.text.edit2') + '</button> '
                + '<button class="btn ghost" onclick="portalPackagingDelete(\'' + r.packagingId + '\')">' + T('common.button.delete') + '</button></td>' : '')
            + '</tr>').join('')
        + '</tbody></table></div>'
      : '<div style="color:var(--muted); font-size:13px;">' + T('sku.text.no_packaging_defined_for_this_item') + '</div>';

    let form = '';
    if (state.pkgForm){
      const f = state.pkgForm.values;
      const input = (col, label, num) => '<div class="kv"><label>' + label + '</label><div>'
        + '<input class="kv-input" type="' + (num ? 'number' : 'text') + '"'
        + (num ? ' step="any"' : '') + ' value="' + esc(f[col] === undefined ? '' : f[col])
        + '" data-pkg="' + col + '" oninput="portalPackagingField(this)"/></div></div>';
      form = '<div class="table-card" style="margin-top:12px; padding:12px;">'
        + '<div class="section-title">' + (state.pkgForm.packagingId ? T('sku.text.edit_packaging') : T('sku.text.new_packaging')) + '</div>'
        + '<div class="kv-grid">'
        + input('packaging_code', T('sku.text.packaging_code2'))
        + '<div class="kv"><label>' + T('references.column.packaging_type') + '</label><div><select class="kv-input"'
          + ' data-pkg="packaging_type_id" onchange="portalPackagingField(this)"><option value="">—</option>'
          + types.map(t => '<option value="' + esc(t.id) + '"'
              + (String(t.id) === String(f.packaging_type_id) ? ' selected' : '') + '>'
              + esc(t.name) + '</option>').join('')
          + '</select></div></div>'
        + input('barcode', T('sku.text.packaging_barcode'))
        + input('coefficient', T('sku.text.factor2'), true)
        + input('length_cm', T('sku.label.length_cm'), true)
        + input('width_cm', T('references.column.width_cm'), true)
        + input('height_cm', T('sku.text.height_cm'), true)
        + input('volume_m3', T('sku.label.volume_m'), true)
        + input('gross_weight_kg', T('sku.text.gross_weight_kg'), true)
        + '</div><div style="margin-top:12px;">'
        + '<button class="btn primary" onclick="portalPackagingSave()">' + T('sku.text.save_packaging') + '</button> '
        + '<button class="btn ghost" onclick="portalPackagingCancel()">' + T('common.button.cancel') + '</button></div></div>';
    } else if (canEdit){
      form = '<div style="margin-top:12px;">'
        + '<button class="btn" onclick="portalPackagingAdd()">' + T('sku.text.add_packaging') + '</button></div>';
    }
    return table + form;
  }

  window.portalPackagingField = function(el){
    state.pkgForm.values[el.getAttribute('data-pkg')] = el.value === '' ? null : el.value;
  };

  /** Дорисовываем карточку прототипа: кнопки в шапке и, в правке, поля ввода. */
  function decorate(code){
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
    const title = document.getElementById('skuDetailTitle');
    if (title && state.mode === 'create') title.textContent = T('sku.text.new_item');
    if (title && state.row && state.row.isActive === false){
      title.innerHTML = esc(state.row.name) + ' <span class="count">' + T('partners.text.archived') + '</span>';
    }

    const body = document.getElementById('skuDetailBody');
    if (!body) return;
    // fcActiveSkuTab объявлена через let: это лексическая глобальная переменная,
    // и обращаться к ней надо по имени — свойства window у неё нет
    // прототип держит в fcActiveSkuTab подпись вкладки — переводим её обратно в ключ
    const tab = tabKey(fcActiveSkuTab);
    if (tab === PACKAGING_TAB){
      body.innerHTML = packagingTabHtml();
      return;
    }
    if (state.mode === 'view') return;     // просмотр рисует прототип
    if (!TABS[tab]){
      body.innerHTML = '<div style="color:var(--muted); font-size:13px;">'
        + 'Эти поля портал не меняет: их ставит обмен с WMS или сам портал</div>';
      return;
    }
    // Поля, закрытые этому поклажедателю, не правятся: иначе запрет
    // действовал бы только в просмотре, а в правке поле оставалось бы.
    const fields = TABS[tab].filter(f => fieldAllowed(f.col));
    if (!fields.length){
      body.innerHTML = '<div style="color:var(--muted); font-size:13px;">'
        + 'На этой вкладке у вас нет доступных полей</div>';
      return;
    }
    body.innerHTML = '<div class="kv-grid">' + fields.map(fieldHtml).join('') + '</div>'
      + '<div style="color:var(--muted); font-size:12px; margin-top:12px;">'
      + 'Поля со звёздочкой обязательны. Правки со всех вкладок сохраняются одной кнопкой.</div>';
  }

  /* ================= Открытие карточки ================= */

  async function openCard(skuId){
    const card = await api('GET', '/api/sku/' + skuId);
    await dictionaries();
    state.skuId = skuId;
    state.row = card;
    state.packagings = card.packagings || [];
    state.draft = {};
    state.pkgForm = null;
    const p = PORTAL.skuToProduct(card);
    products.length = 0;
    products.push(p);
    openSkuItem(card.code);
  }

  // Список товаров открывает карточку из API: в строке списка есть skuId,
  // а карточка приходит вместе с упаковками одним запросом.
  window.portalOpenSku = function(code){
    const row = (PORTAL.catalog.sku || []).find(s => s.code === code);
    if (!row){
      say('Товар ' + code + ' не найден в загруженном списке');
      return;
    }
    state.mode = 'view';
    openCard(row.skuId).catch(e => say('Не удалось открыть карточку: ' + e.message));
  };

  window.portalSkuCreate = async function(){
    try {
      await dictionaries();
      state.mode = 'create';
      state.skuId = null;
      state.row = {};
      state.draft = {};
      state.packagings = [];
      state.pkgForm = null;
      fcActiveSkuTab = typeof T === 'function' ? T('sku.text.general_information')
                                                : T('sku.text.general_information');
      products.length = 0;
      products.push(PORTAL.skuToProduct({code: '', name: T('sku.text.new_item')}));
      openSkuItem('');
    } catch (e){
      say('Не удалось открыть создание товара: ' + e.message);
    }
  };

  window.portalSkuEdit = function(){
    state.mode = 'edit';
    state.draft = {};
    openSkuItem(state.row.code);
  };

  window.portalSkuCancel = function(){
    state.draft = {};
    if (state.mode === 'create'){
      state.mode = 'view';
      showTab('references');
      showReferencesSub('sku');
      return;
    }
    state.mode = 'view';
    openSkuItem(state.row.code);
  };

  window.portalSkuSave = async function(){
    const creating = state.mode === 'create';
    if (!creating && Object.keys(state.draft).length === 0){
      state.mode = 'view';
      openSkuItem(state.row.code);
      return;
    }
    try {
      const card = creating
        ? await api('POST', '/api/sku', state.draft)
        : await api('PUT', '/api/sku/' + state.skuId, state.draft);
      state.mode = 'view';
      state.skuId = card.skuId;
      state.row = card;
      state.packagings = card.packagings || [];
      state.draft = {};
      await reloadList();
      products.length = 0;
      products.push(PORTAL.skuToProduct(card));
      openSkuItem(card.code);
      say(creating ? T('sku.text.item_created') : T('sku.text.changes_saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalSkuArchive = async function(archived){
    try {
      const card = await api('POST', '/api/sku/' + state.skuId + '/archive', {active: archived});
      state.row = card;
      state.packagings = card.packagings || [];
      await reloadList();
      products.length = 0;
      products.push(PORTAL.skuToProduct(card));
      openSkuItem(card.code);
      say(archived ? T('sku.text.item_restored') : T('sku.text.item_archived'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalSkuDelete = async function(){
    // Подтверждение нужно: удаление необратимо, в отличие от архива
    if (!window.confirm('Удалить товар «' + state.row.code + '» без возможности восстановления?\n'
        + T('sku.text.if_it_is_used_anywhere_the_portal'))) return;
    try {
      await api('DELETE', '/api/sku/' + state.skuId);
      await reloadList();
      say(T('sku.message.item_deleted'));
      showTab('references');
      showReferencesSub('sku');
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Упаковки в карточке ================= */

  const PKG_FIELDS = ['packaging_code', 'packaging_type_id', 'barcode', 'coefficient',
                      'length_cm', 'width_cm', 'height_cm', 'volume_m3', 'gross_weight_kg'];

  window.portalPackagingAdd = function(){
    state.pkgForm = {packagingId: null, values: {}};
    openSkuItem(state.row.code);
  };

  window.portalPackagingEdit = function(id){
    const r = state.packagings.find(p => p.packagingId === id);
    if (!r) return;
    const values = {};
    PKG_FIELDS.forEach(col => { values[col] = r[camel(col)]; });
    state.pkgForm = {packagingId: id, values: values};
    openSkuItem(state.row.code);
  };

  window.portalPackagingCancel = function(){
    state.pkgForm = null;
    openSkuItem(state.row.code);
  };

  window.portalPackagingSave = async function(){
    const form = state.pkgForm;
    try {
      state.packagings = form.packagingId
        ? await api('PUT', '/api/packagings/' + form.packagingId, form.values)
        : await api('POST', '/api/sku/' + state.skuId + '/packagings', form.values);
      state.pkgForm = null;
      await reloadPackagingScreen();
      openSkuItem(state.row.code);
      say(T('sku.message.packaging_saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalPackagingDefault = async function(id){
    try {
      state.packagings = await api('POST', '/api/packagings/' + id + '/default');
      await reloadList();
      await reloadPackagingScreen();
      openSkuItem(state.row.code);
    } catch (e){
      say(e.message);
    }
  };

  window.portalPackagingDelete = async function(id){
    const r = state.packagings.find(p => p.packagingId === id);
    if (!window.confirm('Удалить упаковку «' + (r ? r.packagingCode : id) + '»?')) return;
    try {
      state.packagings = await api('DELETE', '/api/packagings/' + id);
      await reloadPackagingScreen();
      openSkuItem(state.row.code);
      say(T('sku.text.packaging_deleted'));
    } catch (e){
      say(e.message);
    }
  };

  /* ================= Экран «Упаковки» ================= */

  /**
   * Список товаров. С сервера забираем вместе с архивом и держим полный
   * набор отдельно: иначе архивную позицию нечем вернуть в работу — её
   * просто не видно. В списке по умолчанию показываем рабочие, а архив
   * открывается флажком.
   */
  async function reloadList(){
    try {
      PORTAL.catalog.skuAll = await api('GET', '/api/sku?withArchived=true');
      PORTAL.catalogError.sku = null;
    } catch (e){
      PORTAL.catalog.skuAll = PORTAL.catalog.skuAll || [];
      PORTAL.catalogError.sku = e.message;
    }
    applyArchiveFilter();
  }

  function applyArchiveFilter(){
    const show = document.getElementById('skuShowArchived')?.checked;
    PORTAL.catalog.sku = (PORTAL.catalog.skuAll || []).filter(s => show || s.isActive !== false);
    if (typeof renderSku === 'function') renderSku();
  }

  window.portalSkuArchiveFilter = applyArchiveFilter;

  /** Архивную позицию в списке надо отличать глазом, а не по памяти. */
  function markArchivedRows(){
    if (typeof tableColumns === 'undefined' || !tableColumns.sku) return;
    const c = tableColumns.sku.find(x => x.id === 'code');
    if (!c) return;
    c.render = p => '<strong>' + esc(dash(p.code)) + '</strong>'
      + (p.isActive === false ? ' <span class="count">' + T('portal.text.archived2') + '</span>' : '');
  }

  function addArchiveCheckbox(){
    const filters = document.querySelector('#ref-sub-sku .filters');
    if (!filters || document.getElementById('skuShowArchived')) return;
    const box = document.createElement('div');
    box.className = 'fgroup';
    box.innerHTML = '<label>' + T('sku.text.archive') + '</label><label style="font-size:13px; font-weight:500;">'
      + '<input type="checkbox" id="skuShowArchived" onchange="portalSkuArchiveFilter()"/>'
      + ' ' + T('portal.text.show_archived') + '</label>';
    const spacer = filters.querySelector('.spacer');
    filters.insertBefore(box, spacer || null);
  }

  async function reloadPackagingScreen(){
    try {
      PORTAL.catalog.packaging = await api('GET', '/api/packaging');
      PORTAL.catalogError.packaging = null;
    } catch (e){
      PORTAL.catalog.packaging = [];
      PORTAL.catalogError.packaging = e.message;
    }
    renderPackagingFromApi();
  }

  function renderPackagingFromApi(){
    const body = document.getElementById('packagingBody');
    if (!body) return;
    const val = id => (document.getElementById(id)?.value || '').trim().toLowerCase();
    const codeQ = val('packagingSearchCode');
    const nameQ = val('packagingSearchName');

    let rows = (PORTAL.catalog.packaging || []).slice();
    if (codeQ) rows = rows.filter(r => (r.skuCode || '').toLowerCase().includes(codeQ));
    if (nameQ) rows = rows.filter(r => (r.skuName || '').toLowerCase().includes(nameQ));

    if (typeof fcDirSortArray === 'function'){
      const map = {productCode: 'skuCode', productName: 'skuName', unit: 'uomName',
                   packagingCode: 'packagingCode', type: 'packagingTypeName',
                   displayName: 'packagingTypeName', barcode: 'barcode',
                   coefficient: 'coefficient', length: 'lengthCm', width: 'widthCm',
                   height: 'heightCm', volume: 'volumeM3', weightGross: 'grossWeightKg'};
      rows = fcDirSortArray('packaging', rows, r => {
        const col = dirSortState.packaging.col;
        return r[map[col] || col] ?? '';
      });
    }
    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage('packaging', rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }
    const counter = document.getElementById('packagingCount');
    if (counter) counter.textContent = '(' + total + ')';

    body.innerHTML = items.length
      ? items.map(r => '<tr style="cursor:pointer;" onclick="portalOpenSku(\'' + esc(r.skuCode) + '\')">'
          + '<td><strong>' + esc(r.skuCode) + '</strong></td><td>' + dash(esc(r.skuName))
          + '</td><td>' + dash(esc(r.uomName)) + '</td><td>' + esc(r.packagingCode)
          + '</td><td>' + dash(esc(r.packagingTypeName)) + '</td><td>'
          + dash(esc(r.packagingTypeName) + ' (' + dash(r.coefficient) + dash(esc(r.uomName)) + ')')
          + '</td><td>' + dash(esc(r.barcode)) + '</td><td class="num">' + dash(r.coefficient)
          + '</td><td class="num">' + dash(r.lengthCm) + '</td><td class="num">' + dash(r.widthCm)
          + '</td><td class="num">' + dash(r.heightCm) + '</td><td class="num">' + dash(r.volumeM3)
          + '</td><td class="num">' + dash(r.grossWeightKg) + '</td></tr>').join('')
      : '<tr><td colspan="13" style="color:var(--muted);">'
        + (PORTAL.catalogError.packaging ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';
    if (typeof renderPaginationBar === 'function') renderPaginationBar('packaging', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('packaging'), 0);
  }

  /* ================= Установка ================= */

  function addCreateButton(){
    const actions = document.querySelector('#ref-sub-sku .panel-head .actions');
    if (!actions || document.getElementById('skuCreateBtn')) return;
    const btn = document.createElement('button');
    btn.id = 'skuCreateBtn';
    btn.className = 'btn primary';
    btn.textContent = T('sku.button.create_item');
    btn.onclick = () => window.portalSkuCreate();
    // первым в группе: создание — главное действие экрана, Excel рядом
    actions.insertBefore(btn, actions.firstChild);
  }

  function install(){
    const origOpenSkuItem = window.openSkuItem;
    window.openSkuItem = function(code){
      origOpenSkuItem(code);
      decorate(code);
    };
    window.renderPackaging = renderPackagingFromApi;

    PORTAL.onLogin(() => {
      markArchivedRows();
      addArchiveCheckbox();
      reloadList().catch(() => {});
      reloadPackagingScreen().catch(() => {});
      if (mayEdit()) addCreateButton();
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
