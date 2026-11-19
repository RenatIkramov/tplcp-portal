
/*
  Список перемещений с сервера: отбор, порядок и страницы считает сервер,
  здесь — перевод строки ответа в строку таблицы и отрисовка.

  Прежде этот код жил в sku.js рядом с карточкой товара и карточкой
  остатка; они ушли в sku-card.js и stock-card.js (11.11.2026).
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const n = v => v === null || v === undefined ? 0 : Number(v);

  // общие помощники — core.js (аудит A4)
  const {dash} = window.PORTAL;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /* ---------------- Перемещения ---------------- */

  function toTransfer(t){
    return {
      // «Номер» — номер в ERP клиента, «ID документа» — номер портала.
      // См. тот же разбор в docs.js: подставлять в «Номер» номер портала
      // значит показывать человеку чужое число как его собственное.
      no: t.documentNo, erpNo: dash(t.erpNumber), wmsNumber: dash(t.docNumber),
      status: PORTAL.statusBadge(t.statusCode), statusLabel: t.statusName || '',
      // Дата создания и дата отгрузки — отметки времени с V62: показываем
      // со временем, а фильтруем по местному дню (см. PORTAL.localDay).
      date: PORTAL.dateTimeText(t.docDate), dateIso: PORTAL.localDay(t.docDate),
      fromWarehouse: t.fromWarehouseName, toWarehouse: t.toWarehouseName,
      warehouse: t.fromWarehouseName,
      shipDate: PORTAL.dateTimeText(t.shipDate), receivedDate: ruDate(t.receivedDate),
      author: dash(t.authorName), trip: dash(t.trip), comment: dash(t.comment),
      lineCount: n(t.lineCount), totalPieces: n(t.totalPieces),
      weight: n(t.weightKg), volume: n(t.volumeM3),
      palletCount: n(t.palletCount), cargoPlaces: n(t.cargoPlaces),
      carrier: dash(t.carrier), carNumber: dash(t.carNumber),
      driverName: dash(t.driverName), driverPhone: dash(t.driverPhone),
      // Доп. поля поклажедателя. Копируются поимённо, а не
      // оставляются «где-то в ответе»: колонки списка рисуются
      // по одноимённому свойству строки, и без этого включённое
      // через ⚙ Колонки «Доп. поле 1» показывало бы прочерк.
      customField1: dash(t.customField1),
      customField2: dash(t.customField2),
      customField3: dash(t.customField3),
      customField4: dash(t.customField4),
      customField5: dash(t.customField5),
      customField6: dash(t.customField6),
      customField7: dash(t.customField7),
      customField8: dash(t.customField8),
      customField9: dash(t.customField9),
      customField10: dash(t.customField10),
      docId: t.docId
    };
  }

  /**
   * Перемещения с сервера, с отбором из панели фильтров.
   *
   * <p>Ожидание PORTAL.filtersReady — то же, что в docs.js: без него
   * запрос ушёл бы раньше, чем собралась панель, и отбор не попал бы
   * в запрос вовсе.
   */
  async function loadTransfers(){
    if (PORTAL.sectionHidden('transfers')){
      // раздел скрыт у этого клиента — сервер списка не отдаст (аудит S3)
      PORTAL.docs.transfers = [];
      PORTAL.docsTotal.transfers = 0;
      PORTAL.docsError.transfers = null;
      renderTransfers();
      return;
    }
    try {
      if (PORTAL.filtersReady) await PORTAL.filtersReady;
      const data = await PORTAL.fetchJson('/api/transfers' + PORTAL.listQuery('transfers'));
      PORTAL.docs.transfers = (data.rows || []).map(toTransfer);
      PORTAL.docsTotal.transfers = data.total || 0;
      // Номер страницы из ответа: сервер мог вернуть первую вместо
      // запрошенной пятой, если отбор сузился и пятой больше нет.
      PORTAL.tables.setPage('transfers', data.page);
      PORTAL.docsError.transfers = null;
    } catch (e){
      PORTAL.docs.transfers = [];
      PORTAL.docsTotal.transfers = 0;
      PORTAL.docsError.transfers = e.message;
    }
    renderTransfers();
  }

  /** Отбор, порядок и страница — на сервере: здесь только отрисовка. */
  function renderTransfers(){
    PORTAL.tables.renderList('transfers', {rows: PORTAL.docs.transfers || [],
        total: PORTAL.docsTotal.transfers || 0, error: PORTAL.docsError.transfers, open: 'portalOpenDocument'});
  }

  function install(){
    PORTAL.tables.register('transfers', {load: loadTransfers, render: renderTransfers});
    // Список перемещений живёт здесь, а поступления и заказы — в docs.js.
    // Общее «перечитать списки документов» должно обновлять все три.
    PORTAL.reloadTransfers = loadTransfers;
    PORTAL.onLogin(loadTransfers);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
