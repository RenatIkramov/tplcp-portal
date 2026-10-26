
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  PORTAL.docs = { receipts: [], orders: [] };
  // Сколько строк нашлось ВСЕГО — не на странице. Ряду страниц
  // нужно «всего 134», а по двадцати присланным строкам это
  // не сосчитать.
  PORTAL.docsTotal = { receipts: 0, orders: 0, transfers: 0 };
  PORTAL.docsLoadedAt = { receipts: null, orders: null };
  PORTAL.docsError = { receipts: null, orders: null };

  const num = v => v === null || v === undefined ? 0 : Number(v);

  /** Код статуса из базы → класс бейджа демо (draft / warn / ok / err). */
  const BADGE = {
    draft: 'draft',
    awaiting: 'warn', in_progress: 'warn', received: 'ok', discrepancy: 'err',
    reserved: 'warn', picking: 'warn', shipped: 'ok', delayed: 'err',
    forming: 'warn', in_transit: 'warn', form_error: 'err'
  };

  /** 2026-09-01 → 01.09.2026 (формат, в котором даты показывает демо). */
  function ruDate(iso){
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  }
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;

  function commonFields(r){
    return {
      // Три номера у документа, и путать их нельзя:
      //   erpNo      — колонка «Номер»: номер документа в ERP клиента.
      //                Это его номер, по нему он документ и зовёт;
      //   no         — колонка «ID документа»: номер, который выдал
      //                портал. По умолчанию колонка скрыта, включается
      //                через ⚙ Колонки;
      //   wmsNumber  — колонка «Номер документа в WMS».
      //
      // Раньше в «Номер» подставлялся номер портала — «чтобы колонка
      // не пустовала». Выходило хуже пустоты: человек видел в поле
      // «номер в ERP» чужое число и считал его своим.
      no: r.documentNo,
      erpNo: dash(r.erpNumber),
      erpNumber: dash(r.erpNumber),   // по нему работает поиск по номеру
      wmsNumber: dash(r.docNumber),
      status: BADGE[r.statusCode] || 'warn',
      statusLabel: r.statusName || '',
      statusCode: r.statusCode,
      // Дата создания — отметка времени с V62, поэтому показываем её
      // со временем и фильтруем по местному дню, а не по первым десяти
      // знакам ISO: там мгновение по UTC.
      date: PORTAL.dateTimeText(r.docDate),
      dateIso: PORTAL.localDay(r.docDate),
      author: dash(r.authorName),
      trip: dash(r.trip),
      comment: dash(r.comment),
      lineCount: num(r.lineCount),
      weight: num(r.weightKg),
      volume: num(r.volumeM3),
      palletCount: num(r.palletCount),
      cargoPlaces: num(r.cargoPlaces),
      totalPieces: num(r.totalPieces),
      carrier: dash(r.carrier),
      transportType: dash(r.transportType),
      transportBrand: dash(r.transportBrand),
      carNumber: dash(r.carNumber),
      trailerNumber: dash(r.trailerNumber),
      techPassportNumber: dash(r.techPassportNumber),
      containerNumber: dash(r.containerNumber),
      sealNumber: dash(r.sealNumber),
      driverName: dash(r.driverName),
      driverPhone: dash(r.driverPhone),
      driverIdNumber: dash(r.driverIdNumber),
      // Доп. поля поклажедателя. Копируются поимённо, а не
      // оставляются «где-то в ответе»: колонки списка рисуются
      // по одноимённому свойству строки, и без этого включённое
      // через ⚙ Колонки «Доп. поле 1» показывало бы прочерк.
      customField1: dash(r.customField1),
      customField2: dash(r.customField2),
      customField3: dash(r.customField3),
      customField4: dash(r.customField4),
      customField5: dash(r.customField5),
      customField6: dash(r.customField6),
      customField7: dash(r.customField7),
      customField8: dash(r.customField8),
      customField9: dash(r.customField9),
      customField10: dash(r.customField10),
      driverInn: dash(r.driverInn),
      docId: r.docId
    };
  }

  const toReceipt = r => Object.assign(commonFields(r), {
    warehouse: r.warehouseName || r.warehouseId,
    supplier: dash(r.supplierName),
    docType: dash(r.docTypeName),
    eta: ruDate(r.etaDate),
    arrivalDate: ruDate(r.arrivalDate),
    receivingEndDate: ruDate(r.receivingEndDate),
    accompanyingDocNumber: dash(r.accompanyingDocNumber),
    invoiceNumber: dash(r.invoiceNumber),
    gtd: dash(r.gtd)
  });

  const toOrder = o => Object.assign(commonFields(o), {
    warehouse: o.warehouseName || o.warehouseId,
    consignee: dash(o.consigneeName),
    deliveryAddress: dash(o.deliveryAddress),
    orderType: dash(o.orderTypeName),
    plannedShip: ruDate(o.plannedShipDate),
    // Дата отгрузки с V62 со временем, планируемая осталась днём:
    // первое — событие, второе — обещание на день.
    ship: PORTAL.dateTimeText(o.shipDate),
    deliveryDate: ruDate(o.deliveryDate)
  });

  /** Колонки, которые в демо вычислялись из mock-логики, читают поля API. */
  function patchColumns(){
    if (typeof tableColumns === 'undefined') return;
    const set = (table, id, render) => {
      const c = (tableColumns[table] || []).find(c => c.id === id);
      if (c) c.render = render;
    };
    set('receipts', 'warehouse', r => r.warehouse);
    set('receipts', 'receivingEndDate', r => r.receivingEndDate);
    set('receipts', 'arrivalDate', r => r.arrivalDate);
    set('receipts', 'volume', r => r.volume);
    set('orders', 'warehouse', o => o.warehouse);
    set('orders', 'volume', o => o.volume);
  }

  function statusBar(table){
    const body = document.getElementById(table + 'Body');
    if (!body) return;
    const host = body.closest('table');
    if (!host) return;
    let bar = document.getElementById('portal-status-' + table);
    if (!bar){
      bar = document.createElement('div');
      bar.id = 'portal-status-' + table;
      bar.style.cssText = 'margin:6px 0 10px; font-size:12px; color:var(--muted);';
      host.parentNode.insertBefore(bar, host);
    }
    const err = PORTAL.docsError[table];
    if (err){
      bar.innerHTML = '<span style="color:#c0392b;">' + T('common.message.could_not_get_the_data')
        + ': ' + err + '</span>'
        + ' · <a href="#" data-portal-reload="' + table + '">' + T('common.text.retry') + '</a>';
    } else {
      const t = PORTAL.docsLoadedAt[table];
      bar.innerHTML = '' + T('admin.column.depositor') + ' <b>' + PORTAL.depositorCode + '</b> · '
        + T('common.text.data_as_of') + ' '
        + (t ? t.toLocaleTimeString(PORTAL.timeLocale(),
              {hour:'2-digit', minute:'2-digit'}) : '—')
        + ' · <a href="#" data-portal-reload="' + table + '">' + T('stock.text.refresh') + '</a>';
    }
    const link = bar.querySelector('[data-portal-reload]');
    if (link) link.onclick = e => { e.preventDefault(); load(table); };
  }

  /**
   * Список с сервера, с отбором из панели фильтров.
   *
   * <p>Ожидание PORTAL.filtersReady не формальность: подписчики входа
   * прогоняются не дожидаясь друг друга, и без ожидания запрос уходил
   * бы раньше, чем появилась панель, — то есть без отбора и без
   * диапазона дат по умолчанию.
   */
  async function load(table){
    try {
      if (PORTAL.filtersReady) await PORTAL.filtersReady;
      const data = await PORTAL.fetchJson('/api/' + table + PORTAL.listQuery(table));
      PORTAL.docs[table] = (data.rows || []).map(table === 'receipts' ? toReceipt : toOrder);
      PORTAL.docsTotal[table] = data.total || 0;
      // Сервер мог вернуть не ту страницу, которую просили: отбор
      // сузился, и пятой страницы больше нет. Номер в ответе —
      // не любезность, а то, что экран обязан подхватить, иначе
      // ряд страниц покажет «Стр. 5», а на экране будет первая.
      if (typeof paginationState !== 'undefined' && paginationState[table] && data.page){
        paginationState[table].page = data.page;
      }
      PORTAL.docsLoadedAt[table] = new Date();
      PORTAL.docsError[table] = null;
    } catch (e){
      PORTAL.docs[table] = [];
      PORTAL.docsTotal[table] = 0;
      PORTAL.docsError[table] = e.message;
    }
    render(table);
  }

  function render(table){
    const body = document.getElementById(table + 'Body');
    if (!body) return;

    // Отбор, порядок и страница — всё на сервере (DocumentFilters,
    // DocumentPaging). Здесь не осталось ничего, кроме отрисовки.
    //
    // Так было не всегда. Отбор жил тут и отбирал по тому, что уже
    // лежит на странице: статус сравнивался по переведённой подписи,
    // и на английском не находилось ничего. Сортировка осталась
    // клиентской дольше — и ошибалась тише: она упорядочивала
    // двадцать строк текущей страницы, так что «самый тяжёлый
    // документ» был самым тяжёлым из видимых.
    const items = PORTAL.docs[table];
    const total = PORTAL.docsTotal[table] || 0;
    const size = (typeof paginationState !== 'undefined' && paginationState[table])
        ? paginationState[table].pageSize : items.length;
    const totalPages = Math.max(1, Math.ceil(total / (size || 1)));

    const counter = document.getElementById(table + 'Count');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead(table);
    body.innerHTML = items.length
      ? items.map(r => renderTableRow(table, r, 'portalOpenDocument')).join('')
      : '<tr><td colspan="24" style="color:var(--muted);">'
        + (PORTAL.docsError[table] ? T('partners.text.data_unavailable') : T('stock.text.nothing_found')) + '</td></tr>';

    if (typeof renderPaginationBar === 'function') renderPaginationBar(table, totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth(table), 0);
    statusBar(table);
  }

  // Заглушка portalOpenDocument удалена: карточку открывает card.js,
  // а редактор черновика — draft.js, и оба грузятся позже этого файла.

  /** Перезагрузка списков — используется редактором черновиков. */
  /**
   * Перечитать списки документов — все три.
   *
   * <p>Перемещения загружает другой файл, поэтому они подключаются
   * через PORTAL.reloadTransfers. Без этого созданное перемещение
   * в списке не появлялось: обновлялись только поступления и заказы.
   */
  window.portalReloadDocuments = function(){
    load('receipts');
    load('orders');
    if (typeof PORTAL.reloadTransfers === 'function') PORTAL.reloadTransfers();
  };

  function install(){
    patchColumns();
    window.renderReceipts = () => render('receipts');
    window.renderOrders = () => render('orders');
    // Панель фильтров перечитывает список этим вызовом: отбор
    // изменился — за данными надо идти заново, перерисовкой
    // имеющихся строк тут не обойтись.
    PORTAL.reloadDocs = load;

    // Ряд страниц и щелчки по заголовкам ведут за данными, а не
    // перерисовывают имеющиеся: страницу и порядок решает сервер.
    // Прототип зовёт TABLE_RENDER_FN[ключ] — подменяем запись,
    // а не кнопки: кнопок в ряду три, и каждая нашла бы свой путь
    // мимо подмены.
    if (typeof TABLE_RENDER_FN !== 'undefined'){
      TABLE_RENDER_FN.receipts = () => load('receipts');
      TABLE_RENDER_FN.orders = () => load('orders');
    }
    PORTAL.onLogin(() => { load('receipts'); load('orders'); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
