
/*
  Список «Упаковки» и кнопка «Создать товар» в справочниках (11.11.2026).

  Список товаров — catalog.js, карточка товара — sku-card.js, колонки
  обоих списков — table-columns.js. Прежде всё это жило в sku-edit.js
  вместе с карточкой.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /** Кто ведёт справочник: и сотрудник склада тоже (решение заказчика 11.11.2026). */
  const EDITOR_ROLES = ['depositor', 'manager', 'staff', 'admin'];
  const mayEdit = () => EDITOR_ROLES.includes(PORTAL.session ? PORTAL.session.roleCode : '');

  // общие помощники — core.js (аудит A4)

  const {dash} = window.PORTAL;

  /**
   * Страница упаковок с сервера.
   *
   * <p>Справочник упаковок целиком тоже нужен — окну строки документа, —
   * но это другой набор и другой адрес: /api/packaging/options, его
   * читает draft.js. Один набор на две задачи означал бы, что открытая
   * вторая страница упаковок меняет выбор в окне строки.
   */
  async function reloadPackagingScreen(){
    try {
      if (PORTAL.filtersReady) await PORTAL.filtersReady;
      const data = await PORTAL.fetchJson('/api/packaging' + PORTAL.listQuery('packaging'));
      PORTAL.page.packaging = data.rows || [];
      PORTAL.pageTotal.packaging = data.total || 0;
      PORTAL.tables.setPage('packaging', data.page);
      PORTAL.catalogError.packaging = null;
    } catch (e){
      PORTAL.page.packaging = [];
      PORTAL.pageTotal.packaging = 0;
      PORTAL.catalogError.packaging = e.message;
    }
    renderPackaging();
  }

  /**
   * «Наименование упаковки» собирается экраном: в ответе сервера такого
   * поля нет, есть тип, коэффициент и единица измерения.
   */
  function withDisplayName(r){
    return Object.assign({}, r, {
      displayName: dash(r.packagingTypeName) + ' (' + dash(r.coefficient) + ' ' + dash(r.uomName) + ')'
    });
  }

  /** Список упаковок открывает карточку товара — по коду товара. */
  function renderPackaging(){
    PORTAL.tables.renderList('packaging', {rows: (PORTAL.page.packaging || []).map(withDisplayName),
        total: PORTAL.pageTotal.packaging || 0, error: PORTAL.catalogError.packaging, open: 'portalOpenSku'});
  }

  function addCreateButton(){
    const actions = document.querySelector('#ref-sub-sku .panel-head .actions');
    if (!actions || document.getElementById('skuCreateBtn')) return;
    const btn = document.createElement('button');
    btn.id = 'skuCreateBtn';
    btn.className = 'btn primary';
    PORTAL.label(btn, 'sku.button.create_item');
    btn.onclick = () => PORTAL.skuCard.create();
    // первым в группе: создание — главное действие экрана, Excel рядом
    actions.insertBefore(btn, actions.firstChild);
  }

  function install(){
    PORTAL.tables.register('packaging', {load: reloadPackagingScreen, render: renderPackaging});
    // Панель фильтров перечитывает список этим вызовом.
    PORTAL.reloadPackaging = reloadPackagingScreen;
    PORTAL.onLogin(() => {
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
