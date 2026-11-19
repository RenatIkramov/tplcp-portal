
/*
  Описания колонок всех таблиц портала (12.11.2026).

  Прежде описания жили в прототипе с русскими подписями и демо-
  отрисовкой, а модули списков переписывали отрисовку части колонок
  поверх (docs.js, core.js, catalog.js, transfers-list.js, sku-lists.js).
  Теперь здесь та отрисовка, которая работает на самом деле, и только
  она.

  Колонка: id (идентификатор в справочнике колонок), num (число — вправо),
  wrap (переносить текст), off (по умолчанию скрыта; пока не пришла
  раскладка с сервера), render (строка таблицы → разметка ячейки).
  Подпись колонки — ключ col.<таблица>.<id>; её разрешает tables.js
  (переименование клиента, иначе перевод).

  Колонки, которые есть в справочнике колонок, но не описаны здесь
  (рейс, доп. поля документа, колонки упаковок), tables.js дорисовывает
  сам — значением одноимённого поля строки.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, dash} = window.PORTAL;
  /** Значение как текст: пустое — прочерк, разметка экранируется. */
  const text = v => esc(dash(v));
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /**
   * Колонка, которая показывает поле строки (по умолчанию — одноимённое).
   * @param {string} id
   * @param {{field?: string, num?: boolean, wrap?: boolean, off?: boolean,
   *          render?: (row: any) => string}} [o]
   * @returns {TableColumn}
   */
  const col = (id, o) => {
    const field = (o && o.field) || id;
    return Object.assign({id: id, render: (/** @type {any} */ r) => text(r[field])}, o || {});
  };
  const bold = (id, field) => col(id, {render: r => '<strong>' + text(r[field || id]) + '</strong>'});
  const yesNo = (id, field, off) => col(id, {off: off, render: r => esc(PORTAL.yesNo(r[field || id]))});
  /** Объём — с одним знаком, как в документах. */
  const volume = (o) => col('volume', Object.assign({num: true,
      render: (/** @type {any} */ r) => text(typeof r.volume === 'number' ? r.volume.toFixed(1) : r.volume)}, o || {}));
  /** Десять доп. полей / доп. параметров — по умолчанию скрыты. */
  const ten = (prefix) => Array.from({length: 10}, (_, i) => col(prefix + (i + 1), {off: true}));

  /** Транспорт и измерения — общие колонки трёх списков документов. */
  const docTail = () => [
    col('palletCount', {num: true, off: true}), col('cargoPlaces', {num: true, off: true}),
    col('totalPieces', {num: true, off: true}), col('carrier', {off: true}),
    col('transportType', {off: true}), col('transportBrand', {off: true}),
    col('trailerNumber', {off: true}), col('techPassportNumber', {off: true}),
    col('containerNumber', {off: true}), col('sealNumber', {off: true}),
    col('driverPhone', {off: true}), col('driverIdNumber', {off: true}), col('driverInn', {off: true})
  ];
  const status = () => col('status', {render: r => badge(r.status, esc(r.statusLabel))});

  /** Партии строки документа: качество, партия, серия, срок, количество, доп. поля. */
  const lineDetail = () => [col('q'), col('batch'), col('series'), col('exp'), col('stock', {num: true}),
                            ...ten('customField')];

  /** Строка состава документа. */
  const lineHead = () => [col('code'), col('name', {wrap: true}), col('unit')];
  const lineSum = qty => col('sum', {num: true,
      render: l => (l.price && l[qty]) ? (l.price * l[qty]).toFixed(2) : '—'});
  const quality = () => col('quality', {render: l => esc(PORTAL.card.qualityName(l))});

  /** @type {Record<string, {keyField?: string, columns: TableColumn[]}>} */
  PORTAL.tableDefs = {
    receipts: {keyField: 'no', columns: [
      status(), bold('erpNo'), col('wmsNumber'), col('warehouse'), col('date'), col('eta'),
      col('receivingEndDate'), col('supplier'), col('lineCount', {num: true}), col('weight', {num: true}),
      volume(), col('carNumber'), col('driverName'), col('comment'),
      col('no', {off: true}), col('author', {off: true}), col('docType', {off: true}),
      col('arrivalDate', {off: true}), col('accompanyingDocNumber', {off: true}), col('gtd', {off: true}),
      ...docTail()
    ]},
    orders: {keyField: 'no', columns: [
      status(), bold('erpNo'), col('warehouse'), col('date'), col('ship'), col('consignee'),
      col('lineCount', {num: true}), col('weight', {num: true}), volume(), col('carNumber'),
      col('driverName'), col('comment'), col('deliveryAddress'),
      col('no', {off: true}), col('wmsNumber', {off: true}), col('author', {off: true}),
      col('orderType', {off: true}), col('plannedShip', {off: true}),
      ...docTail()
    ]},
    transfers: {keyField: 'no', columns: [
      status(), bold('erpNo'), col('fromWarehouse'), col('toWarehouse'), col('date'), col('shipDate'),
      col('receivedDate'), col('lineCount', {num: true}), col('weight', {num: true}), volume(),
      col('carNumber'), col('driverName'), col('comment'),
      col('no', {off: true}), col('wmsNumber', {off: true}), col('author', {off: true}),
      ...docTail()
    ]},
    stock: {keyField: 'code', columns: [
      col('warehouse', {render: p => text(p.warehouseName || p.warehouse)}), bold('code'),
      col('name', {wrap: true}), col('group'), col('unit'),
      col('stock', {num: true}), col('available', {num: true}), col('reserved', {num: true}),
      col('blocked', {num: true}), col('defect', {num: true}), col('incoming', {num: true})
    ]},
    stockBatch: {columns: [
      // резерва и «доступно» по партии нет: их портал считает по товару и складу (V141)
      col('batch'), col('series'), col('exp'), col('stock', {num: true}),
      // цвет плашки — по коду качества (V89): 01 «Годный» зелёный, остальное красный
      col('q', {render: b => b.qualityCode ? badge(b.qualityCode === '01' ? 'ok' : 'err', esc(b.q)) : text(b.q)}),
      ...ten('customField')
    ]},
    // Список товаров — строки /api/sku как есть (v_sku_full в camelCase).
    sku: {keyField: 'code', columns: [
      col('itemCode'),
      col('code', {render: p => '<strong>' + text(p.code) + '</strong>'
          + (p.isActive === false ? ' <span class="count">' + esc(T('portal.text.archived2')) + '</span>' : '')}),
      col('name', {wrap: true}), col('unit', {field: 'uomName'}),
      yesNo('hasPackaging', null, true), col('orderUnit', {field: 'orderUnitName', off: true}),
      col('barcode'), col('group', {field: 'itemGroupName'}), col('productType', {field: 'itemTypeName'}),
      col('length', {num: true}), col('width', {num: true}), col('height', {num: true}),
      col('volume', {num: true}), col('weightGross', {num: true}),
      col('manufacturer'), col('countryOrigin'), col('brand'),
      yesNo('isFragile'), yesNo('isSerialTracked'), yesNo('batchTracking'), yesNo('expiryTracking'),
      col('statusProduct', {field: 'statusName'}),
      col('itemFullName', {wrap: true, off: true}),
      col('hazardClass', {field: 'hazardClassName', off: true}),
      col('hazardGroup', {field: 'hazardGroupName', off: true}),
      col('weightNet', {num: true, off: true}),
      col('maxStorageTemp', {num: true, off: true}), col('minStorageTemp', {num: true, off: true}),
      col('maxHumidity', {num: true, off: true}), col('minHumidity', {num: true, off: true}),
      col('minDaysForReceipt', {num: true, off: true}), col('minDaysForReservation', {num: true, off: true}),
      col('shelfLifeDays', {num: true, off: true}),
      col('color', {off: true}), col('size', {off: true}), col('abcClass', {off: true}),
      col('xyzClass', {off: true}), col('guid', {field: 'skuId', off: true}),
      col('hsCode', {off: true}), col('enstruCode', {off: true}),
      col('currency', {field: 'currencyCode', off: true}), col('cost', {num: true, off: true}),
      col('comment', {wrap: true, off: true}),
      yesNo('isKit', null, true), yesNo('isMarkingRequired', null, true), yesNo('isExcise', null, true),
      yesNo('isControlled', null, true), yesNo('isWmsActive', null, true),
      col('createdBy', {off: true}), col('createdAt', {off: true, render: p => esc(ruDate(p.createdAt))}),
      col('updatedBy', {off: true}), col('updatedAt', {off: true, render: p => esc(ruDate(p.updatedAt))}),
      ...ten('customAttr')
    ]},
    // Колонки упаковок целиком приходят из справочника колонок (V76).
    packaging: {keyField: 'skuCode', columns: [
      bold('skuCode'),
      col('coefficient', {num: true}), col('lengthCm', {num: true}), col('widthCm', {num: true}),
      col('heightCm', {num: true}), col('volumeM3', {num: true}), col('grossWeightKg', {num: true}),
      // единица заказа (V81) — признак: без своей отрисовки в ячейке стояло бы true/false
      col('isDefaultOrderUnit', {render: r => esc(T(r.isDefaultOrderUnit === true ? 'common.value.yes' : 'common.value.no'))})
    ]},
    receiptComposition: {columns: [
      ...lineHead(), col('expected', {num: true}),
      col('expectedWms', {num: true, render: l => text(l.expectedWms ?? l.expected)}),
      col('accepted', {num: true}), col('defect', {num: true}), col('price', {num: true}),
      lineSum('expected'), col('currency'), col('lineComment', {wrap: true}), quality()
    ]},
    orderComposition: {columns: [
      ...lineHead(), col('ordered', {num: true}),
      col('orderedWms', {num: true, render: l => text(l.orderedWms ?? l.ordered)}),
      col('picked', {num: true}), col('weight', {num: true}), col('price', {num: true}),
      lineSum('ordered'), col('currency'), col('lineComment', {wrap: true}), quality()
    ]},
    transferComposition: {columns: [
      ...lineHead(), col('qty', {num: true}),
      col('qtyWms', {num: true, render: l => text(l.qtyWms ?? l.qty)}),
      col('lineComment', {wrap: true}), quality()
    ]},
    receiptLineDetail: {columns: lineDetail()},
    orderLineDetail: {columns: lineDetail()},
    transferLineDetail: {columns: lineDetail()}
  };
})();
