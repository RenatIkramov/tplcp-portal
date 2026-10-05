#!/usr/bin/env python3
"""Этап 4: подключаем экран «Запасы» демо к реальному API портала.

Правка минимальная и обратимая: исходный файл не переписывается построчно,
в конец <body> добавляется один блок <script>, который переопределяет
источник данных для экрана «Запасы». Остальные экраны продолжают работать
на демо-данных, пока под них не появятся эндпоинты.
"""
import sys, re

SRC = '/root/.claude/uploads/9b555f04-0ff6-56df-8eda-edbeec9ab0c5/52c39c71-3pl-customer-portal-demo_1.html'
DST = '/home/claude/tplcp-portal/src/main/resources/static/index.html'

PATCH = r"""
<!-- ================= ЭТАП 4: экран «Запасы» из реального API =================
     Этот блок добавлен к демо-файлу. Он не меняет вёрстку — только подменяет
     источник данных для таблицы «Запасы»: вместо массива products в памяти
     данные берутся из GET /api/stock.

     Остальные экраны пока работают на демо-данных. По мере появления
     эндпоинтов они переключаются тем же способом.
================================================================================ -->
<script>
(function(){
  'use strict';

  // Код поклажедателя. Пока нет аутентификации (этап 5), берём из адреса:
  //   /index.html?depositorCode=D-002
  const PORTAL = {
    depositorCode: new URLSearchParams(location.search).get('depositorCode') || 'D-001',
    rows: [],
    loadedAt: null,
    error: null,
    loading: false
  };
  window.PORTAL = PORTAL;

  const num = v => v === null || v === undefined ? 0 : Number(v);

  /** Приводит строку API к виду, который ожидают колонки таблицы демо. */
  function toRow(r){
    return {
      // поля, по которым работают фильтры и сортировка демо
      code: r.skuCode,
      name: r.skuName,
      group: r.itemGroupName || '—',
      unit: r.unitName || '',
      warehouse: r.warehouseId,
      warehouseName: r.warehouseName,
      // количества
      stock: num(r.quantity),
      reserved: num(r.reservedQty),
      blocked: num(r.blockedQty),
      available: num(r.availableQty),
      defect: num(r.defectQty),
      incoming: num(r.incomingQty),
      batchCount: num(r.batchCount),
      syncedAt: r.syncedAt
    };
  }

  /** Колонки экрана «Запасы» начинают читать значения прямо из строки API. */
  function patchColumns(){
    if (typeof stockColumnDefs === 'undefined') return;
    const set = (id, render) => {
      const c = stockColumnDefs.find(c => c.id === id);
      if (c) c.render = render;
    };
    set('warehouse', p => p.warehouseName || p.warehouse);
    set('code',      p => '<strong>' + p.code + '</strong>');
    set('name',      p => p.name);
    set('group',     p => p.group);
    set('unit',      p => p.unit);
    set('stock',     p => p.stock);
    set('available', p => p.available);
    set('reserved',  p => p.reserved);
    set('blocked',   p => p.blocked);
    set('defect',    p => p.defect);
    set('incoming',  p => p.incoming);
  }

  /** Строка состояния: «данные на 14:32 · Обновить» (решение Р4 — кэш WMS). */
  function renderStatusBar(){
    const host = document.getElementById('stockBody');
    if (!host) return;
    const table = host.closest('table');
    if (!table) return;
    let bar = document.getElementById('portalStockStatus');
    if (!bar){
      bar = document.createElement('div');
      bar.id = 'portalStockStatus';
      bar.style.cssText = 'margin:6px 0 10px; font-size:12px; color:var(--muted);';
      table.parentNode.insertBefore(bar, table);
    }
    if (PORTAL.loading){
      bar.innerHTML = 'Загрузка данных из портала…';
      return;
    }
    if (PORTAL.error){
      bar.innerHTML = '<span style="color:#c0392b;">Не удалось получить данные: '
        + PORTAL.error + '</span> · <a href="#" id="portalStockRefresh">Повторить</a>';
    } else {
      const t = PORTAL.loadedAt
        ? PORTAL.loadedAt.toLocaleTimeString('ru-RU', {hour:'2-digit', minute:'2-digit'})
        : '—';
      bar.innerHTML = 'Поклажедатель <b>' + PORTAL.depositorCode + '</b> · данные на ' + t
        + ' · <a href="#" id="portalStockRefresh">Обновить</a>';
    }
    const link = document.getElementById('portalStockRefresh');
    if (link) link.onclick = e => { e.preventDefault(); loadStock(); };
  }

  async function loadStock(){
    PORTAL.loading = true;
    PORTAL.error = null;
    renderStatusBar();
    try {
      const url = '/api/stock?depositorCode=' + encodeURIComponent(PORTAL.depositorCode);
      const resp = await fetch(url, {headers: {'Accept': 'application/json'}});
      if (!resp.ok){
        let detail = 'код ' + resp.status;
        try {
          const body = await resp.json();
          if (body && body.message) detail = body.message;
        } catch (ignored) {}
        throw new Error(detail);
      }
      PORTAL.rows = (await resp.json()).map(toRow);
      PORTAL.loadedAt = new Date();
    } catch (e){
      PORTAL.rows = [];
      PORTAL.error = e.message;
    } finally {
      PORTAL.loading = false;
      renderStatusBar();
      if (typeof renderStock === 'function') renderStock();
    }
  }
  window.portalReloadStock = loadStock;

  /** Клик по строке: карточки товара из API пока нет (появится на этапе 6). */
  window.portalOpenStockRow = function(code){
    if (typeof notify === 'function'){
      notify('Карточка товара ' + code + ' появится, когда будет эндпоинт /api/sku');
    }
  };

  /**
   * Замена штатного renderStock: та же фильтрация, сортировка и постраничный
   * вывод, но источник — PORTAL.rows вместо массива products.
   */
  function renderStockFromApi(){
    const body = document.getElementById('stockBody');
    if (!body) return;

    const codeQ = (document.getElementById('stockSearchCode')?.value || '').trim().toLowerCase();
    const nameQ = (document.getElementById('stockSearchName')?.value || '').trim().toLowerCase();
    const groupQ = document.getElementById('stockFilterGroup')?.value || '';
    const defectOnly = document.getElementById('stockDefectFilterCb')?.checked;

    let rows = PORTAL.rows.slice();
    if (codeQ)  rows = rows.filter(p => (p.code || '').toLowerCase().includes(codeQ));
    if (nameQ)  rows = rows.filter(p => (p.name || '').toLowerCase().includes(nameQ));
    if (groupQ) rows = rows.filter(p => p.group === groupQ);
    if (defectOnly) rows = rows.filter(p => p.defect > 0);

    if (typeof fcApplySort === 'function') rows = fcApplySort('stock', rows);

    let items = rows, totalPages = 1, total = rows.length;
    if (typeof getPage === 'function'){
      const page = getPage('stock', rows);
      items = page.items; totalPages = page.totalPages; total = page.total;
    }

    const counter = document.getElementById('stockCount');
    if (counter) counter.textContent = '(' + total + ')';

    if (typeof renderTableHead === 'function') renderTableHead('stock');
    body.innerHTML = items.length
      ? items.map(p => renderTableRow('stock', p, 'portalOpenStockRow')).join('')
      : '<tr><td colspan="20" style="color:var(--muted);">'
        + (PORTAL.error ? 'Данные недоступны' : 'Ничего не найдено') + '</td></tr>';

    if (typeof renderPaginationBar === 'function') renderPaginationBar('stock', totalPages, total);
    if (typeof updateTopScrollWidth === 'function') setTimeout(() => updateTopScrollWidth('stock'), 0);
    renderStatusBar();
  }

  function install(){
    patchColumns();
    window.renderStock = renderStockFromApi;
    loadStock();
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
</script>
"""


def main():
    html = open(SRC, encoding='utf-8').read()
    if '</body>' not in html:
        sys.exit('не найден </body>')
    out = html.replace('</body>', PATCH + '\n</body>', 1)
    import os
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    open(DST, 'w', encoding='utf-8').write(out)
    print('записано:', DST, len(out), 'байт')


if __name__ == '__main__':
    main()
