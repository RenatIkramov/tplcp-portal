
/*
  Окно ввода строки документа — одно на поступление, заказ и
  перемещение (10.11.2026).

  Прежде окно было в прототипе тремя копиями, а draft.js оборачивал
  каждую: подкладывал настоящие товары и упаковки, дописывал остатки,
  прятал цену, предупреждал о превышении. Теперь всё это здесь, одной
  реализацией; различия видов — в таблице MODAL.

  Разметка окна в index.html пока тремя блоками со своими
  идентификаторами (lineEdit…, orderLineEdit…, transferLineEdit…).
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /**
   * id — начало идентификаторов окна; qtyId — поле количества;
   * qty — свойство количества строки; warehouse — поле шапки, по складу
   * которого считаются остатки; price — есть ли в окне цена;
   * warn — предупреждать ли о превышении доступного (поступление товар
   * привозит, превышать там нечего).
   */
  const MODAL = {
    receipts: {id: 'lineEdit', qtyId: 'lineEditExpected', qty: 'expected',
               qtyLabel: 'common.label.expected_quantity', invalid: 'common.message.enter_a_valid_expected_quantity',
               warehouse: 'rf_warehouse', price: 'priceInReceipts', warn: false},
    orders: {id: 'orderLineEdit', qtyId: 'orderLineEditQty', qty: 'ordered',
             qtyLabel: 'col.orderComposition.ordered', invalid: 'common.message.enter_a_valid_quantity',
             warehouse: 'of_warehouse', price: 'priceInOrders', warn: true},
    transfers: {id: 'transferLineEdit', qtyId: 'transferLineEditQty', qty: 'qty',
                qtyLabel: 'common.label.quantity', invalid: 'common.message.enter_a_valid_quantity',
                warehouse: 'tf_fromWarehouse', price: null, warn: true}
  };

  /**
   * Состояние окна каждого вида: правка или добавление, номер строки,
   * выбранный товар.
   * @type {Record<string, {mode: 'add' | 'edit', index: number | null, product: any}>}
   */
  const state = {
    receipts: {mode: 'add', index: null, product: null},
    orders: {mode: 'add', index: null, product: null},
    transfers: {mode: 'add', index: null, product: null}
  };

  /** Окно с вопросом «товар уже добавлен» — общее на все виды. */
  /** @type {{type: string, line: any, qty: number, finish: () => void} | null} */
  let duplicate = null;

  const el = id => document.getElementById(id);
  const part = (type, name) => el(MODAL[type].id + name);

  // общие помощники — core.js (аудит A4)
  const {esc: text, esc: attr} = window.PORTAL;
  const refs = () => (PORTAL.draft && PORTAL.draft.refs) || {skuList: [], packagings: [], kits: {}};
  const products = () => refs().skuList || [];
  const card = () => PORTAL.card;
  const isDraft = doc => !!doc && doc.statusCode === PORTAL.STATUS.DRAFT;

  /* ===================== товар ===================== */

  /** Числа и подписи окна по выбранному товару. */
  function fillProduct(type, p){
    const m = MODAL[type];
    part(type, 'Code').textContent = p ? p.code : '—';
    part(type, 'Name').textContent = p ? p.name : '—';
    part(type, 'Unit').textContent = p ? p.unit : '—';
    const label = part(type, 'Expected') ? part(type, 'ExpectedLabel') : part(type, 'QtyLabel');
    if (label) label.textContent = T(m.qtyLabel) + (p && p.unit ? ', ' + p.unit : '');
    setupPackaging(type, p, p ? (p.defaultOrderPackaging || null) : null, null);
    setupPrice(type, p, null, null);
    refreshStock(type);
  }

  function search(type, query){
    const box = part(type, 'ProductResults');
    const q = String(query || '').trim().toLowerCase();
    if (!q){ box.style.display = 'none'; box.innerHTML = ''; return; }
    const found = products().filter(p => String(p.code).toLowerCase().includes(q)
        || String(p.name).toLowerCase().includes(q)).slice(0, 8);
    box.innerHTML = found.length
      ? found.map(p => '<div style="padding:9px 12px; font-size:13px; cursor:pointer;'
          + ' border-bottom:1px solid var(--line);"'
          + PORTAL.callOnPress('PORTAL.lineModal.pick', type, p.code) + '>'
          + '<strong>' + text(p.code) + '</strong> — ' + text(p.name)
          + ' <span style="color:var(--muted);">(' + text(p.unit) + ')</span></div>').join('')
      : '<div style="padding:10px 12px; font-size:13px; color:var(--muted);">'
        + text(T('stock.text.nothing_found')) + '</div>';
    box.style.display = '';
  }

  function pick(type, code){
    const p = products().find(x => x.code === code);
    if (!p) return;
    state[type].product = p;
    fillProduct(type, p);
    part(type, 'ProductSearch').value = p.code + ' — ' + p.name;
    const box = part(type, 'ProductResults');
    box.style.display = 'none';
    box.innerHTML = '';
  }

  function clearSearch(type){
    state[type].product = null;
    fillProduct(type, null);
    const input = part(type, 'ProductSearch');
    input.value = '';
    const box = part(type, 'ProductResults');
    box.style.display = 'none';
    box.innerHTML = '';
    input.focus();
  }

  /* ===================== упаковка ===================== */

  /**
   * Количество по упаковке: «5 паллет × 100 = 500 кг». Упаковки — свои
   * у клиента (/api/packaging/options, draft.js).
   */
  const packagingsOf = code => (refs().packagings || []).filter(x => x.skuCode === code);
  const packagingName = (pk, unit) => (pk.packagingTypeName || pk.packagingCode)
      + ' (' + (Number(pk.coefficient) || 1) + (unit || '') + ')';

  function setupPackaging(type, product, packagingCode, packageCount){
    const options = product ? packagingsOf(product.code) : [];
    const qty = el(MODAL[type].qtyId);
    if (!options.length){
      part(type, 'PackagingWrap').style.display = 'none';
      part(type, 'PackageCountWrap').style.display = 'none';
      qty.readOnly = false;
      return;
    }
    part(type, 'PackagingWrap').style.display = '';
    part(type, 'Packaging').innerHTML = '<option value="">' + text(T('common.option.no_packaging')) + '</option>'
        + options.map(o => '<option value="' + attr(o.packagingCode) + '"'
            + (o.packagingCode === packagingCode ? ' selected' : '') + '>'
            + text(packagingName(o, product.unit)) + '</option>').join('');
    const chosen = !!packagingCode && options.some(o => o.packagingCode === packagingCode);
    part(type, 'PackageCountWrap').style.display = chosen ? '' : 'none';
    part(type, 'PackageCount').value = chosen ? (packageCount ?? '') : '';
    qty.readOnly = chosen;
  }

  function packagingChanged(type){
    const code = part(type, 'Packaging').value;
    const qty = el(MODAL[type].qtyId);
    if (!code){
      part(type, 'PackageCountWrap').style.display = 'none';
      part(type, 'PackageCount').value = '';
      qty.readOnly = false;
      return;
    }
    part(type, 'PackageCountWrap').style.display = '';
    qty.readOnly = true;
    packageCountChanged(type);
  }

  function packageCountChanged(type){
    const code = part(type, 'Packaging').value;
    if (!code) return;
    const product = state[type].product;
    const pkg = product ? packagingsOf(product.code).find(x => x.packagingCode === code) : null;
    const count = Number(part(type, 'PackageCount').value) || 0;
    el(MODAL[type].qtyId).value = pkg ? count * (Number(pkg.coefficient) || 1) : '';
    priceChanged(type);
  }

  /* ===================== цена ===================== */

  /**
   * Нужны ли клиенту цена и сумма в окне (V61) — настройка поклажедателя,
   * отдельно по поступлениям и заказам. У перемещения цены нет вовсе.
   * Это удобство, а не право: уже введённую цену скрытое поле не стирает.
   */
  function priceShown(type){
    const m = MODAL[type];
    return !!m.price && !!PORTAL.draft && PORTAL.draft.me[m.price] === true;
  }

  function setupPrice(type, product, price, currency){
    const priceEl = part(type, 'Price');
    const currencyEl = part(type, 'Currency');
    if (!priceEl || !currencyEl) return;
    const shown = priceShown(type);
    ['Price', 'Sum'].forEach(name => {
      const field = part(type, name);
      const row = field && field.closest ? field.closest('.kv') : null;
      if (row) row.style.display = shown ? '' : 'none';
    });
    currencyEl.textContent = currency || (product && product.currency) || '—';
    if (price !== null && price !== undefined){
      priceEl.value = price;
    } else {
      // Цену из карточки товара подставляем только там, где её видно:
      // спрятанное поле молча проставило бы цены всем открытым строкам.
      priceEl.value = shown && product && product.cost !== null && product.cost !== undefined ? product.cost : '';
    }
    priceChanged(type);
  }

  function priceChanged(type){
    const priceEl = part(type, 'Price');
    const sumEl = part(type, 'Sum');
    if (!priceEl || !sumEl) return;
    const qty = Number(el(MODAL[type].qtyId).value) || 0;
    const price = Number(priceEl.value) || 0;
    sumEl.textContent = qty && price && priceShown(type) ? (qty * price).toFixed(2) : '—';
  }

  /** Ввод количества: подсветка неверного числа и пересчёт суммы. */
  function qtyInput(type){
    const qty = el(MODAL[type].qtyId);
    qty.classList.toggle('kv-input-invalid', qty.value !== '' && (isNaN(Number(qty.value)) || Number(qty.value) < 0));
    priceChanged(type);
  }

  /* ===================== качество ===================== */

  /** Список качества строится при каждом открытии: язык могли сменить, значения — дополнить. */
  function fillQuality(type, code){
    const box = part(type, 'Quality');
    if (!box) return;
    const list = PORTAL.qualities || [];
    box.innerHTML = list.map(q => '<option value="' + attr(q.code) + '">' + text(q.name) + '</option>').join('');
    box.value = code || card().GOOD;
    if (!box.value && list.length) box.value = list[0].code;
  }

  const chosenQuality = type => {
    const box = part(type, 'Quality');
    return (box && box.value) || card().GOOD;
  };

  /* ===================== остатки ===================== */

  /**
   * «Остаток», «Доступно» и «К получению» — по складу документа (для
   * перемещения — склад-отправитель): со склада можно забрать только то,
   * что на нём лежит. Превышение доступного не запрещается, а
   * предупреждается: последнее слово за WMS (решение Р4, §8.3.2).
   *
   * Прочитанное: склад → код товара → строка (null — товара на складе нет).
   * Спрашиваем по одному товару: запасы отдаются страницами (V79).
   * @type {Record<string, Record<string, any>>}
   */
  const stockByWarehouse = {};

  async function stockFor(warehouseId, code){
    if (!warehouseId || !code) return null;
    const byCode = stockByWarehouse[warehouseId] || (stockByWarehouse[warehouseId] = {});
    if (Object.prototype.hasOwnProperty.call(byCode, code)) return byCode[code];
    const answer = await PORTAL.fetchJson('/api/stock?filter.warehouse=' + encodeURIComponent(warehouseId)
        + '&filter.code=' + encodeURIComponent(code) + '&pageSize=50');
    // Фильтр ищет ВХОЖДЕНИЕ («RK-1» находит и «RK-10») — нужную строку
    // выбираем сами, по коду и по складу: лучше ноль, чем чужое число.
    const same = (a, b) => String(a ?? '').trim().toUpperCase() === String(b ?? '').trim().toUpperCase();
    const rows = answer.rows || [];
    const row = rows.find(r => same(r.skuCode, code) && same(r.warehouseId, warehouseId)) || null;
    if (!row && rows.length){
      console.warn(`остатки: ${code}/${warehouseId} — среди ${rows.length} строк нужной нет`);
    }
    byCode[code] = row;
    return row;
  }

  /** Склад окна: из поля шапки, а если поля нет — из самого документа. */
  function warehouseOf(type){
    const field = el(MODAL[type].warehouse);
    if (field && field.value) return field.value;
    const doc = card().doc(type);
    if (!doc) return null;
    return type === 'transfers' ? doc.fromWarehouse : doc.warehouse;
  }

  /** Остаток и доступное одного качества (quality_qty запасов, V89). */
  function qualityStock(row, code){
    let parsed;
    try { parsed = row && row.qualityQty ? JSON.parse(row.qualityQty) : {}; } catch (e){ parsed = {}; }
    const q = parsed[code] || {};
    return {quantity: Number(q.quantity) || 0, available: Number(q.available) || 0};
  }

  /**
   * Пишет три числа — и только их. Три случая путать нельзя:
   * товар не выбран или ответа ещё нет — «—»; на складе нет — ноль;
   * есть — числа. Ноль до ответа выглядел бы как ответ.
   * Числа заказа — по выбранному качеству.
   */
  function paintStock(type){
    const fields = ['StockTotal', 'StockAvail', 'StockIncoming'].map(name => part(type, name));
    const product = state[type].product;
    const byCode = stockByWarehouse[warehouseOf(type)];
    const row = product && byCode ? byCode[product.code] : undefined;
    if (!product || row === undefined){
      fields.forEach(f => { if (f) f.textContent = '—'; });
      return;
    }
    const unit = product.unit || '';
    const byQuality = row && type === 'orders' ? qualityStock(row, chosenQuality(type)) : null;
    const values = !row ? [0, 0, 0]
        : byQuality ? [byQuality.quantity, byQuality.available, row.incomingQty]
        : [row.quantity, row.availableQty, row.incomingQty];
    fields.forEach((f, i) => {
      if (f) f.textContent = (values[i] === undefined || values[i] === null ? 0 : values[i]) + ' ' + unit;
    });
  }

  function refreshStock(type){
    const product = state[type].product;
    const warehouseId = warehouseOf(type);
    const code = product && product.code;
    paintStock(type);
    return stockFor(warehouseId, code)
        .then(() => paintStock(type))
        .catch(() => {
          // запрос не удался — числа неизвестны: «—», а не ноль
          const byCode = stockByWarehouse[warehouseId];
          if (byCode) delete byCode[code];
          paintStock(type);
        });
  }

  /** Доступное количество выбранного товара (null — неизвестно). */
  function availableOf(type){
    const product = state[type].product;
    const byCode = stockByWarehouse[warehouseOf(type)];
    const row = product && byCode ? byCode[product.code] : null;
    if (!row) return null;
    return type === 'orders' ? qualityStock(row, chosenQuality(type)).available : Number(row.availableQty);
  }

  /* ===================== открыть, закрыть, сохранить ===================== */

  function open(type, mode, index){
    const doc = card().doc(type);
    if (!isDraft(doc)) return;
    // Справочники окна грузятся при входе; если ещё не успели — дочитываем.
    if (PORTAL.draft && !PORTAL.draft.refs.loaded){
      PORTAL.draft.loadDirectories().then(() => open(type, mode, index)).catch(() => {});
      return;
    }
    // остатки — свежие при каждом открытии: за время работы их меняют
    // синхронизация, другие документы и состав комплекта
    Object.keys(stockByWarehouse).forEach(id => delete stockByWarehouse[id]);
    const s = state[type];
    const line = mode === 'edit' ? card().lines(type)[index] : null;
    if (mode === 'edit' && !line) return;
    s.mode = mode;
    s.index = mode === 'edit' ? index : null;
    s.product = line ? (products().find(p => p.code === line.code) || null) : null;
    part(type, 'Overlay').classList.add('active');
    part(type, 'Title').textContent = T(mode === 'edit' ? 'common.title.edit_line' : 'common.title.add_line');
    part(type, 'SearchWrap').style.display = mode === 'edit' ? 'none' : '';
    part(type, 'ProductSearch').value = '';
    part(type, 'ProductResults').style.display = 'none';
    const qty = el(MODAL[type].qtyId);
    fillQuality(type, line ? card().quality(line) : card().GOOD);
    fillProduct(type, s.product);
    qty.value = line ? (line[MODAL[type].qty] ?? '') : '';
    qty.classList.remove('kv-input-invalid');
    if (line){
      setupPackaging(type, s.product, line.packagingCode || null, line.packageCount || null);
      setupPrice(type, s.product, line.price ?? null, line.currency || null);
    }
    part(type, 'Comment').value = line ? (line.lineComment || '') : '';
    if (mode === 'add') part(type, 'ProductSearch').focus();
  }

  function close(type){
    part(type, 'Overlay').classList.remove('active');
    state[type].index = null;
    state[type].product = null;
  }

  function save(type){
    const m = MODAL[type];
    const s = state[type];
    const qtyEl = el(m.qtyId);
    if (qtyEl.value === '' || isNaN(Number(qtyEl.value)) || Number(qtyEl.value) < 0){
      qtyEl.classList.add('kv-input-invalid');
      notify(T(m.invalid));
      return;
    }
    const product = s.product;
    if (!product){
      notify(T('common.message.select_an_item_from_the_list'));
      return;
    }
    const qty = Number(qtyEl.value);
    const available = m.warn ? availableOf(type) : null;
    const lineComment = part(type, 'Comment').value.trim();
    const packagingCode = part(type, 'Packaging').value || null;
    const values = {
      code: product.code, skuId: product.skuId, name: product.name, unit: product.unit,
      [m.qty]: qty, lineComment, packagingCode,
      packageCount: packagingCode ? (Number(part(type, 'PackageCount').value) || 0) : null,
      quality: chosenQuality(type)
    };
    if (m.price){
      const priceEl = part(type, 'Price');
      const currency = part(type, 'Currency').textContent;
      values.price = priceEl.value !== '' ? Number(priceEl.value) : null;
      values.currency = currency !== '—' ? currency : null;
    }
    const mode = s.mode;
    const lines = card().lines(type).slice();
    // Комплект в поступлении и перемещении (V153) — сразу комплектующими:
    // WMS принимает и перевозит только то, что лежит на складе
    const kit = type !== 'orders' ? (refs().kits || {})[product.skuId] : null;
    if (kit && kit.length){
      const parts = kit.map((/** @type {any} */ c) => Object.assign({checked: false, series: '', batch: '', exp: '—'}, {
        code: c.code, skuId: c.skuId, name: c.name, unit: c.unitName || '',
        [m.qty]: Number((qty * Number(c.qty)).toFixed(3)), lineComment, packagingCode: null, packageCount: null,
        quality: values.quality
      }, m.price ? {price: null, currency: null} : {}));
      if (mode !== 'add' && s.index !== null){
        lines.splice(s.index, 1, ...parts);
      } else {
        parts.forEach(p => {
          const same = lines.find(l => l.code === p.code
              && card().quality(l) === p.quality && (l.lineComment || '') === lineComment);
          if (same) same[m.qty] = Number(((Number(same[m.qty]) || 0) + p[m.qty]).toFixed(3));
          else lines.push(p);
        });
      }
      close(type);
      card().setLines(type, lines);
      notify(T('common.message.kit_expanded', {code: product.code, count: parts.length}));
      return;
    }
    const finish = () => {
      close(type);
      card().setLines(type, lines);
      notify(T(mode === 'add' ? 'common.message.line_added' : 'common.message.line_updated'));
      // предупреждение — не запрет: последнее слово за WMS
      if (available !== null && qty > available){
        notify(T('common.message.over_available_stock',
            {count: qty + ' ' + (product.unit || ''), available: available + ' ' + (product.unit || '')}));
      }
    };
    if (mode === 'add'){
      // Повтор — тот же товар с тем же качеством и комментарием (V85, V89,
      // V100, V101): разное качество или комментарий — разные строки.
      const existing = lines.find(l => l.code === product.code
          && card().quality(l) === values.quality && (l.lineComment || '') === lineComment);
      if (existing){
        duplicate = {type, line: existing, qty, finish};
        el('duplicateLineMessage').textContent = T('common.text.item_already_in_document',
            {name: product.name, qty: (existing[m.qty] ?? 0) + ' ' + (product.unit || '')});
        el('duplicateLineOverlay').classList.add('active');
        return;
      }
      lines.push(Object.assign({checked: false, series: '', batch: '', exp: '—'}, values));
    } else if (s.index !== null){
      lines[s.index] = Object.assign({}, lines[s.index], values);
    }
    finish();
  }

  /** Ответ на «товар уже добавлен»: прибавить, заменить или отменить. */
  function resolveDuplicate(action){
    const d = duplicate;
    el('duplicateLineOverlay').classList.remove('active');
    duplicate = null;
    if (!d || action === 'cancel') return;
    const field = MODAL[d.type].qty;
    d.line[field] = action === 'add' ? (Number(d.line[field]) || 0) + d.qty : d.qty;
    d.finish();
  }

  /* ===================== наружу ===================== */

  PORTAL.lineModal = {
    add: type => open(type, 'add', null),
    edit: (type, index) => open(type, 'edit', index),
    close,
    save,
    search,
    pick,
    clearSearch,
    packagingChanged,
    packageCountChanged,
    priceChanged,
    qtyInput,
    qualityChanged: type => paintStock(type),
    resolveDuplicate,
    /** Выбранный в окне товар (null — не выбран). */
    product: type => state[type].product,
    /** Остатки привязаны к поклажедателю: при смене входа забываем. */
    forgetStock: () => Object.keys(stockByWarehouse).forEach(id => delete stockByWarehouse[id])
  };
})();
