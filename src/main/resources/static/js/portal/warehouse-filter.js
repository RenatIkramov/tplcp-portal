/*
  Склад в шапке портала (V94) — общий фильтр списков.

  Что было. Список складов в шапке достался от прототипа: два склада
  («Алматы», «Караганда») были вписаны в код, со справочником складов
  связи не было. Новый склад в нём не появлялся, названия расходились
  со справочником, а выбор сравнивал выдуманные коды прототипа (almaty)
  со складами базы (WH-ALA) и не отбирал ничего.

  Как теперь (решение заказчика от 30.10.2026):
    * список — из справочника: склады, доступные выбранному
      поклажедателю (GET /api/warehouses, его же видит карточка
      документа в поле «Склад»);
    * выбор — общий фильтр поступлений, заказов, перемещений и запасов.
      Отбирает сервер (параметр warehouse), по всем страницам, а не
      только по видимой: со страницами на сервере отбор на экране
      видел бы двадцать строк из тысячи;
    * у перемещения подходит документ, где склад — отправитель или
      получатель;
    * новый черновик получает выбранный склад (у перемещения — склад
      отправителя).
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)

  const {esc} = window.PORTAL;

  /** Списки, которые отбираются складом из шапки. */
  const SCOPED = ['receipts', 'orders', 'transfers', 'stock'];

  PORTAL.warehouse = '';         // выбранный склад; пусто — все
  PORTAL.warehouses = [];        // [{id, name}] доступные поклажедателю

  PORTAL.warehouseName = id => {
    const w = PORTAL.warehouses.find(x => x.id === id);
    return w ? w.name : '';
  };

  async function load(){
    try {
      const rows = await PORTAL.fetchJson('/api/warehouses');
      PORTAL.warehouses = (rows || []).map(w => ({id: w.warehouseId, name: w.name}));
    } catch (e){
      PORTAL.warehouses = [];
    }
    // Склад другого поклажедателя выбранным не остаётся
    if (PORTAL.warehouse && !PORTAL.warehouses.some(w => w.id === PORTAL.warehouse)){
      PORTAL.warehouse = '';
    }
    paint();
  }

  /** Подпись в шапке и возможность раскрыть список. */
  function paint(){
    const el = document.getElementById('warehouseFilter');
    if (!el) return;
    el.textContent = PORTAL.warehouse
      ? PORTAL.warehouseName(PORTAL.warehouse) : T('common.button.all_warehouses');
    el.classList.toggle('switchable', PORTAL.warehouses.length > 1);
  }

  function item(id, name){
    const current = (PORTAL.warehouse || '') === id;
    return '<div class="ks-item' + (current ? ' current' : '') + '" data-warehouse="' + esc(id) + '">'
      + '<span>' + esc(name) + '</span>' + (current ? '<span>✓</span>' : '') + '</div>';
  }

  // Список складов закрывается щелчком мимо него.
  document.addEventListener('click', e => {
    const wrap = document.getElementById('warehouseFilter')?.closest('.tabs-org-wrap');
    if (wrap && !wrap.contains(/** @type {Node} */ (e.target))) document.getElementById('warehouseSwitcher')?.classList.remove('active');
  });

  // Список — из справочника складов (V94).
  window.toggleWarehouseSwitcher = function(){
    const box = document.getElementById('warehouseSwitcher');
    if (!box || PORTAL.warehouses.length <= 1) return;
    box.innerHTML = '<div class="ks-title">' + esc(T('common.title.warehouse_filter')) + '</div>'
      + item('', T('common.button.all_warehouses'))
      + PORTAL.warehouses.map(w => item(w.id, w.name)).join('');
    box.querySelectorAll('[data-warehouse]').forEach(el => {
      el.onclick = () => window.setWarehouseFilter(el.getAttribute('data-warehouse'));
    });
    box.classList.toggle('active');
  };

  window.setWarehouseFilter = function(id){
    PORTAL.warehouse = (id && id !== 'all') ? id : '';
    const box = document.getElementById('warehouseSwitcher');
    if (box) box.classList.remove('active');
    paint();
    // Отбор изменился — с первой страницы: пятая страница прежнего
    // отбора в новом может оказаться пустой.
    SCOPED.forEach(t => {
      PORTAL.tables.firstPage(t);
    });
    if (typeof PORTAL.reloadDocs === 'function'){
      PORTAL.reloadDocs('receipts');
      PORTAL.reloadDocs('orders');
    }
    if (typeof PORTAL.reloadTransfers === 'function') PORTAL.reloadTransfers();
    if (typeof PORTAL.reloadStock === 'function') PORTAL.reloadStock();
  };

  /** Склад из шапки — в запрос каждого списка, который им отбирается. */
  function wrapQuery(){
    const original = PORTAL.listQuery;
    if (typeof original !== 'function' || original.__warehouse) return;
    /** @this {unknown} */
    const wrapped = function(table){
      const query = original.call(this, table);
      if (!PORTAL.warehouse || SCOPED.indexOf(table) < 0) return query;
      return (query ? query + '&' : '?') + 'warehouse=' + encodeURIComponent(PORTAL.warehouse);
    };
    wrapped.__warehouse = true;
    PORTAL.listQuery = wrapped;
  }

  function install(){
    wrapQuery();
    // Склады — свои у каждого поклажедателя: перечитываем при входе
    // и при смене поклажедателя.
    PORTAL.onLogin(load);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
