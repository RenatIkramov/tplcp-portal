
/*
  Карточка остатка — щелчок по строке «Запасов»: количества товара
  и разбивка по партиям (11.11.2026).

  Прежде её рисовал прототип (openProduct), а sku.js подкладывал ему
  партии через подмену mockBatches и groupBatchRows. Надписи «Код:»,
  «Группа:», «Есть брак», «Без замечаний» стояли в коде по-русски.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const el = id => document.getElementById(id);
  const n = v => v === null || v === undefined ? 0 : Number(v);

  // общие помощники — core.js (аудит A4)
  const {dash, esc} = window.PORTAL;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /**
   * Открытая карточка: строка «Запасов» и её партии.
   * @type {{row: any, batches: any[]}}
   */
  const state = {row: null, batches: []};

  /** Партии — в том виде, в каком их ждут колонки таблицы stockBatch. */
  const toBatch = b => ({
    q: dash(b.quality), qualityCode: b.qualityCode || null,
    batch: dash(b.batchNumber), series: dash(b.seriesNumber), exp: ruDate(b.expiryDate),
    stock: n(b.quantity), blocked: n(b.blockedQty), warehouse: b.warehouseName
  });

  function renderHead(){
    const p = state.row;
    const unit = p.unit ? ' ' + esc(p.unit) : '';
    const kv = (id, value) => '<div class="kv"><label>' + PORTAL.fieldLabel('stock', id, 'col.stock.' + id)
        + '</label><div>' + esc(value) + unit + '</div></div>';
    const defect = n(p.defect) > 0;
    el('productDetailHead').innerHTML = '<div><h2>' + esc(p.name) + '</h2>'
      + '<div class="meta">' + esc(T('references.column.code')) + ': ' + esc(p.code) + ' · '
      + esc(T('references.label.group')) + ': ' + esc(dash(p.group)) + '</div>'
      + '<div class="kv-grid">' + kv('stock', p.stock) + kv('reserved', p.reserved) + kv('defect', p.defect)
      + kv('blocked', p.blocked) + kv('available', p.available) + '</div></div>'
      + '<div>' + badge(defect ? 'warn' : 'ok', esc(T(defect ? 'stock.text.has_defects' : 'stock.text.no_issues')))
      + '</div>';
  }

  /** Таблица партий — по колонкам, выбранным человеком (⚙ Колонки). */
  function renderBatches(){
    if (!state.row) return;
    const visible = PORTAL.tables.visible('stockBatch');
    el('stockBatchHead').innerHTML = visible.map(c =>
        '<th' + (c.num ? ' class="num"' : '') + '>' + PORTAL.tables.label('stockBatch', c) + '</th>').join('');
    el('productDetailBatches').innerHTML = state.batches.length
      ? state.batches.map(b => '<tr>' + visible.map(c => '<td' + (c.num ? ' class="num"' : '') + '>'
          + c.render(b) + '</td>').join('') + '</tr>').join('')
      : '<tr><td colspan="' + visible.length + '" style="color:var(--muted);">'
        + esc(T('admin.text.no_data')) + '</td></tr>';
  }

  /** Щелчок по строке «Запасов» (имя зовёт PORTAL.tables.row). */
  window.portalOpenStockRow = async function(code){
    const row = (PORTAL.rows || []).find(r => r.code === code);
    if (!row){
      if (typeof notify === 'function') notify(T('sku.message.not_in_stock_list', {code: code}));
      return;
    }
    try {
      const batches = await PORTAL.fetchJson('/api/stock/details?skuCode=' + encodeURIComponent(code));
      state.row = row;
      state.batches = (batches || []).map(toBatch);
      renderHead();
      renderBatches();
      showView('product-detail');
    } catch (e){
      if (typeof notify === 'function') notify(T('sku.message.batches_failed', {error: e.message}));
    }
  };

  PORTAL.tables.register('stockBatch', {render: renderBatches});

  PORTAL.stockCard = {
    renderBatches
  };
})();
