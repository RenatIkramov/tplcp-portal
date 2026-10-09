
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const n = v => v === null || v === undefined ? 0 : Number(v);
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /** Строка состава документа в том виде, в каком её ждут колонки демо. */
  function toLine(type, l){
    const base = {
      code: l.skuCode,
      name: l.skuName,
      unit: l.unitName || '',
      price: l.price === null || l.price === undefined ? null : n(l.price),
      currency: l.currencyCode || '',
      lineComment: dash(l.lineComment),
      lineNumber: l.lineNumber,
      batchCount: n(l.batchCount),
      sum: l.lineSum === null || l.lineSum === undefined ? null : n(l.lineSum)
    };
    if (type === 'receipts'){
      return Object.assign(base, {
        expected: n(l.expectedQty),
        expectedWms: l.expectedQtyWms === null ? null : n(l.expectedQtyWms),
        accepted: n(l.acceptedQty),
        defect: n(l.defectQty)
      });
    }
    if (type === 'orders'){
      return Object.assign(base, {
        ordered: n(l.orderedQty),
        orderedWms: l.orderedQtyWms === null ? null : n(l.orderedQtyWms),
        picked: n(l.pickedQty),
        weight: n(l.weightKg)
      });
    }
    return Object.assign(base, { qty: n(l.qty), qtyWms: n(l.qtyWms) });
  }

  /** Запись партии для экрана «Детализация». */
  function toBatch(d){
    const qty = d.acceptedQty !== undefined && d.acceptedQty !== null ? d.acceptedQty
              : (d.pickedQty !== undefined && d.pickedQty !== null ? d.pickedQty : d.qty);
    return {
      q: dash(d.quality),
      batch: dash(d.batchNumber),
      series: dash(d.seriesNumber),
      exp: ruDate(d.expiryDate),
      stock: n(qty),
      lineNumber: d.lineNumber
    };
  }

  async function fetchCard(type, docId){
    const url = '/api/' + type + '/' + encodeURIComponent(docId);
    const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
    if (!resp.ok){
      throw new Error(await PORTAL.responseMessage(resp));
    }
    return resp.json();
  }

  /**
   * Открытие карточки: подкладываем документ с реальными данными в массив,
   * из которого демо его берёт, и вызываем штатную функцию отрисовки.
   */
  async function openDocument(type, no){
    const row = (PORTAL.docs[type] || []).find(r => r.no === no);
    if (!row){
      if (typeof notify === 'function') notify('Документ ' + no + ' не найден в загруженном списке');
      return;
    }
    try {
      const card = await fetchCard(type, row.docId);

      const lines = (card.lines || []).map(l => toLine(type, l));
      const details = (card.lineDetails || []).map(toBatch);
      // партии раскладываем по строкам: демо берёт их из line.batches
      lines.forEach(line => {
        line.batches = details.filter(b => b.lineNumber === line.lineNumber);
      });

      const doc = Object.assign({}, row, {lines: lines, items: lines.length});

      // Массивы receipts/orders/transfers объявлены в демо через let, то есть
      // в window их нет — обращаемся к ним напрямую и подменяем содержимое
      // (именно содержимое, а не саму переменную: на неё могут быть ссылки).
      if (type === 'receipts'){
        receipts.length = 0; receipts.push(doc);
        openReceipt(no);
      } else if (type === 'orders'){
        orders.length = 0; orders.push(doc);
        openOrder(no);
      } else if (typeof transfers !== 'undefined'){
        transfers.length = 0; transfers.push(doc);
        if (typeof openTransfer === 'function') openTransfer(no);
      }
    } catch (e){
      if (typeof notify === 'function') notify('Не удалось открыть документ: ' + e.message);
    }
  }

  /** Клик по строке списка. Тип определяем по тому, какой список сейчас открыт. */
  window.portalOpenDocument = function(no){
    const type = (PORTAL.docs.receipts || []).some(r => r.no === no) ? 'receipts'
               : ((PORTAL.docs.orders || []).some(r => r.no === no) ? 'orders' : 'transfers');
    openDocument(type, no);
  };

  window.portalOpenReceipt = no => openDocument('receipts', no);
  window.portalOpenOrder = no => openDocument('orders', no);
})();
