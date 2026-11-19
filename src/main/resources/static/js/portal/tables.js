
/*
  Таблицы портала: колонки, списки, сортировка, страницы, окно ⚙ Колонки
  (12.11.2026).

  Прежде этим занимались три слоя: прототип (состояние колонок, шапка
  и строки, ряд страниц, окно колонок, полоса прокрутки), columns.js
  (раскладка с сервера и обёртки окна колонок) и шесть модулей списков,
  каждый со своей копией отрисовки. Сверху filters.js перехватывал
  щелчок по заголовку, а i18n.js подменял подпись колонки.

  Теперь всё здесь:
  - раскладка с сервера (/api/table-columns): порядок, видимость,
    «из таблицы убрать нельзя», переименование поля клиентом;
  - состояние таблиц: колонки (table-columns.js плюс дорисованные
    из справочника), порядок, скрытые, сортировка, страница;
  - отрисовка списка одной функцией на все списки (renderList);
  - окно ⚙ Колонки целиком;
  - верхняя полоса прокрутки и сортировка коротких справочников.

  Модули списков регистрируют себя: register(таблица, {load, render}).
  Запрос на сервер и перевод строки ответа остаются у них.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const el = id => document.getElementById(id);

  // общие помощники — core.js (аудит A4)
  const {esc, say} = window.PORTAL;

  async function api(method, url, body){
    /** @type {RequestInit} */
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers = {'Accept': 'application/json', 'Content-Type': 'application/json'};
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
    return resp.status === 204 ? null : resp.json();
  }

  /** Списки, страницы и порядок которых считает сервер. */
  const SERVER_LISTS = ['receipts', 'orders', 'transfers', 'stock', 'sku', 'packaging'];

  /* ===================== состояние таблиц ===================== */

  /**
   * @typedef {{columns: TableColumn[], keyField: string | undefined, order: string[],
   *            hidden: Set<string>, sort: {col: string | null, dir: number},
   *            page: {page: number, pageSize: number},
   *            load?: () => unknown, render?: () => unknown}} TableState
   * @type {Record<string, TableState>}
   */
  const tables = {};

  function table(key){
    if (tables[key]) return tables[key];
    const def = (PORTAL.tableDefs || {})[key] || {columns: []};
    const columns = def.columns.slice();
    tables[key] = {
      columns: columns,
      keyField: def.keyField,
      order: columns.map(c => c.id),
      hidden: new Set(columns.filter(c => c.off).map(c => c.id)),
      sort: {col: null, dir: 1},
      page: {page: 1, pageSize: 20}
    };
    return tables[key];
  }

  /** Колонки таблицы, которые видны, — в порядке, выбранном человеком. */
  function visible(key){
    const t = table(key);
    return t.order.filter(id => !t.hidden.has(id))
        .map(id => t.columns.find(c => c.id === id)).filter(c => c !== undefined);
  }

  /* ===================== раскладка с сервера ===================== */

  // Раскладка: таблица → строки сервера, известные экрану, в порядке показа.
  /** @type {Record<string, any[]>} */
  const layout = {};
  // Какие колонки сервер вообще отдал по таблице: это и есть «доступно
  // клиенту» — запрещённую колонку table_columns не отдаёт совсем.
  /** @type {Record<string, string[]>} */
  const raw = {};
  // Всё, что отдал сервер, строками целиком: переименовать можно и поле,
  // которое показывается только в карточке (доп. поля шапки документа).
  /** @type {Record<string, any[]>} */
  const all = {};

  /** Видимые первыми в своём порядке, за ними скрытые — как в окне колонок. */
  function ordered(rows){
    const vis = rows.filter(r => r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    const hid = rows.filter(r => !r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    return vis.concat(hid);
  }

  /**
   * Колонка есть в справочнике, но не описана в table-columns.js — рисуем
   * значением одноимённого поля строки. Так появились «Рейс» (V68),
   * десять доп. полей поклажедателя и колонки упаковок (V76).
   */
  function adopt(key, rows){
    const t = table(key);
    const known = new Set(t.columns.map(c => c.id));
    rows.forEach(r => {
      if (known.has(r.columnId)) return;
      known.add(r.columnId);
      t.columns.push({
        id: r.columnId,
        label: r.portalLabel || r.label,
        render: item => {
          const v = item[r.columnId];
          return esc(v === null || v === undefined || v === '' ? '—' : v);
        }
      });
    });
  }

  function applyTable(key, rows){
    raw[key] = rows.map(r => r.columnId);
    all[key] = rows;
    adopt(key, rows);
    const t = table(key);
    const here = ordered(rows.filter(r => t.columns.some(c => c.id === r.columnId)));
    if (!here.length) return;
    layout[key] = here;
    t.order = here.map(r => r.columnId);
    t.hidden = new Set(here.filter(r => !r.isVisible).map(r => r.columnId));
  }

  function applyAll(rows){
    /** @type {Record<string, any[]>} */
    const byTable = {};
    rows.forEach(r => { (byTable[r.tableKey] = byTable[r.tableKey] || []).push(r); });
    Object.keys(byTable).forEach(k => applyTable(k, byTable[k]));
  }

  async function reload(key){
    const rows = await api('GET', key ? '/api/table-columns/' + encodeURIComponent(key) : '/api/table-columns');
    if (key) applyTable(key, rows); else applyAll(rows);
    return rows;
  }

  /**
   * Как ЭТОТ клиент называет поле, или null, если не переименовывал.
   * Сервер отдаёт обе подписи — с переименованием и без; разница и есть
   * переименование. Готовую подпись не берём: если текста в каталоге нет,
   * сервер отдаёт ключ, и в карточке стояло бы «col.sku.manufacturer».
   * Переименование от языка не зависит (решение заказчика).
   */
  function renameOf(key, columnId){
    const rows = all[key];
    if (!rows) return null;
    const row = rows.find(r => r.columnId === columnId);
    if (!row || !row.portalLabel) return null;
    return row.label !== row.portalLabel ? row.label : null;
  }

  /** Неснимаемые колонки таблицы (V36): из своей таблицы убрать нельзя. */
  const pinned = key => new Set((layout[key] || []).filter(r => r.isPinned).map(r => r.columnId));

  /** Подпись колонки: переименование клиента, иначе перевод, иначе подпись справочника. */
  function label(key, column){
    const own = renameOf(key, column.id);
    if (own) return esc(own);
    const textKey = 'col.' + key + '.' + column.id;
    const text = T(textKey);
    if (text !== textKey) return esc(text);
    const server = (all[key] || []).find(r => r.columnId === column.id);
    return esc(column.label || (server && (server.portalLabel || server.label)) || column.id);
  }

  /* ===================== отрисовка списков ===================== */

  function head(key){
    const t = table(key);
    const sortable = SERVER_LISTS.indexOf(key) >= 0;
    const host = el(key + 'Head');
    if (!host) return;
    host.innerHTML = visible(key).map(c => {
      const arrow = t.sort.col === c.id ? (t.sort.dir === 1 ? ' ▲' : ' ▼') : '';
      return '<th data-col="' + c.id + '"' + (c.num ? ' class="num"' : '')
        + (sortable ? ' style="cursor:pointer; user-select:none;" title="' + esc(T('common.hint.sort_by_this_field'))
            + '"' + PORTAL.call('PORTAL.tables.sort', key, c.id) : '')
        + '>' + label(key, c) + arrow + '</th>';
    }).join('');
  }

  /** Строка таблицы; open — имя функции, которую зовёт щелчок (с ключом строки). */
  function row(key, item, open){
    const t = table(key);
    const cells = visible(key).map(c => {
      const cls = [c.num ? 'num' : '', c.wrap ? 'wrap' : ''].filter(Boolean).join(' ');
      return '<td data-col="' + c.id + '"' + (cls ? ' class="' + cls + '"' : '') + '>' + c.render(item) + '</td>';
    }).join('');
    const keyValue = t.keyField ? item[t.keyField] : '';
    // Ключ строки — данные (код товара вводит клиент): только в атрибут
    // данных, не в текст скрипта (аудит S1, см. PORTAL.call в core.js).
    return '<tr' + (open ? PORTAL.call(open, String(keyValue)) : '')
      + '>' + cells + '</tr>';
  }

  function pagination(key, total){
    const host = el(key + 'Pagination');
    if (!host) return;
    const p = table(key).page;
    if (!total){ host.innerHTML = ''; return; }
    const pages = Math.max(1, Math.ceil(total / (p.pageSize || 1)));
    // «Стр. 2 из 7 · всего 134» — одна фраза с подстановками, а не обрывки
    host.innerHTML = '<div class="page-size-select">' + esc(T('common.label.rows_per_page'))
      + ' <select' + PORTAL.callOnChange('PORTAL.tables.pageSize', key, PORTAL.VALUE) + '>'
      + [10, 20, 50, 100].map(n => '<option value="' + n + '"' + (p.pageSize === n ? ' selected' : '') + '>'
          + n + '</option>').join('') + '</select></div>'
      + '<div class="pager">'
      + '<button' + PORTAL.call('PORTAL.tables.page', key, -1) + (p.page <= 1 ? ' disabled' : '') + '>'
      + esc(T('common.button.prev_page')) + '</button>'
      + '<span class="page-info">' + esc(T('common.text.page_of', {page: p.page, pages: pages, total: total}))
      + '</span>'
      + '<button' + PORTAL.call('PORTAL.tables.page', key, 1) + (p.page >= pages ? ' disabled' : '') + '>'
      + esc(T('common.button.next_page')) + '</button></div>';
  }

  /**
   * Список: шапка, строки, счётчик, «ничего не найдено», ряд страниц.
   * Отбор, порядок и страницу посчитал сервер — здесь только отрисовка.
   * @param {string} key
   * @param {{rows: any[], total: number, error?: string | null, open?: string}} data
   */
  function renderList(key, data){
    const body = el(key + 'Body');
    if (!body) return;
    const counter = el(key + 'Count');
    if (counter) counter.textContent = '(' + (data.total || 0) + ')';
    head(key);
    body.innerHTML = data.rows.length
      ? data.rows.map(r => row(key, r, data.open)).join('')
      : '<tr><td colspan="' + Math.max(1, visible(key).length) + '" style="color:var(--muted);">'
        + esc(T(data.error ? 'partners.text.data_unavailable' : 'stock.text.nothing_found')) + '</td></tr>';
    pagination(key, data.total || 0);
    setTimeout(() => updateScroll(key), 0);
  }

  /** Перерисовать таблицу (смена раскладки, языка, клиента). Скрытый экран — не беда. */
  function redraw(key){
    const t = tables[key];
    if (!t || !t.render) return;
    try {
      t.render();
      updateScroll(key);
    } catch (e){ /* экран не открыт — перерисуется при открытии */ }
  }

  const redrawAll = () => Object.keys(tables).forEach(redraw);

  /** Сходить за списком заново: страница и порядок уже в состоянии. */
  function load(key){
    const t = tables[key];
    if (t && t.load) return t.load();
    if (t && t.render) return t.render();
    return undefined;
  }

  /* ===================== сортировка и страницы ===================== */

  /**
   * Щелчок по заголовку идёт на сервер: на странице лежат двадцать строк
   * из ста тридцати, и упорядочить их значило бы показать порядок,
   * которому человек поверит, — худший вид поломки. Порядок сменился —
   * начинаем с первой страницы.
   */
  function sort(key, columnId){
    const t = table(key);
    if (t.sort.col === columnId) t.sort.dir = -t.sort.dir;
    else t.sort = {col: columnId, dir: 1};
    t.page.page = 1;
    return load(key);
  }

  function page(key, delta){
    const p = table(key).page;
    p.page = Math.max(1, p.page + Number(delta));
    return load(key);
  }

  function pageSize(key, size){
    const p = table(key).page;
    p.pageSize = parseInt(String(size), 10) || 20;
    p.page = 1;
    return load(key);
  }

  /* ===================== окно ⚙ Колонки ===================== */

  /** @type {{key: string, order: string[], hidden: Set<string>} | null} */
  let picker = null;

  function pickerOpen(key){
    const t = table(key);
    picker = {key: key, order: t.order.slice(), hidden: new Set(t.hidden)};
    pickerList();
    el('colOverlay').classList.add('active');
  }

  function pickerClose(){
    el('colOverlay').classList.remove('active');
    picker = null;
  }

  /** Список колонок; неснимаемая — галочка серая и отмечена. */
  function pickerList(){
    if (!picker) return;
    const p = picker;
    const t = table(p.key);
    const fixed = pinned(p.key);
    el('colList').innerHTML = p.order.map((id, idx) => {
      const c = t.columns.find(x => x.id === id);
      if (!c) return '';
      const lock = fixed.has(id);
      return '<div class="col-item" draggable="true" data-idx="' + idx + '" data-col="' + id + '"'
        + (lock ? ' title="' + esc(T('references.hint.column_cannot_be_hidden')) + '" style="color:var(--muted);"' : '')
        + '><span class="handle">⠿</span><label><input type="checkbox"'
        + (lock || !p.hidden.has(id) ? ' checked' : '') + (lock ? ' disabled' : '')
        + PORTAL.callOnChange('PORTAL.tables.pickerToggle', id) + '>' + label(p.key, c) + '</label></div>';
    }).join('');
    let from = -1;
    Array.from(el('colList').querySelectorAll('.col-item')).forEach(item => {
      item.addEventListener('dragstart', () => { from = Number(item.dataset.idx); item.classList.add('dragging'); });
      item.addEventListener('dragend', () => item.classList.remove('dragging'));
      item.addEventListener('dragover', e => e.preventDefault());
      item.addEventListener('drop', e => {
        e.preventDefault();
        const to = Number(item.dataset.idx);
        if (from < 0 || from === to) return;
        p.order.splice(to, 0, p.order.splice(from, 1)[0]);
        pickerList();
      });
    });
  }

  function pickerToggle(id){
    if (!picker) return;
    if (picker.hidden.has(id)) picker.hidden.delete(id); else picker.hidden.add(id);
  }

  /**
   * Применить: экран меняется сразу, выбор уходит на сервер. Неснимаемые
   * колонки дописываются обратно; не осталось ни одной — отказ.
   */
  function pickerApply(){
    if (!picker) return;
    const p = picker;
    const key = p.key;
    const fixed = pinned(key);
    const chosen = p.order.filter(id => !p.hidden.has(id) || fixed.has(id));
    pickerClose();
    if (!chosen.length){
      say(T('common.message.keep_at_least_one_column'));
      return;
    }
    const t = table(key);
    t.order = p.order.slice();
    t.hidden = new Set(p.order.filter(id => p.hidden.has(id) && !fixed.has(id)));
    redraw(key);
    say(T('common.message.column_settings_applied'));
    api('PUT', '/api/table-columns/' + encodeURIComponent(key), {columns: chosen})
      .then(rows => { applyTable(key, rows); redraw(key); })
      .catch(err => {
        say(T('common.message.columns_shown_but_not_saved') + ': ' + err.message);
        reload(key).then(() => redraw(key)).catch(() => {});
      });
  }

  /**
   * «Сбросить» — вернуть настройки по умолчанию, а не «показать всё».
   * Применяется сразу, поэтому окно закрывается: иначе «Применить»
   * записало бы умолчания как личный выбор.
   */
  function pickerReset(){
    if (!picker) return;
    const key = picker.key;
    api('DELETE', '/api/table-columns/' + encodeURIComponent(key))
      .then(rows => {
        applyTable(key, rows);
        redraw(key);
        pickerClose();
        say(T('common.message.columns_reset_to_default'));
      })
      .catch(err => say(err.message));
  }

  /* ===================== верхняя полоса прокрутки ===================== */

  // Полоса над таблицей дублирует нижнюю: широкую таблицу листают,
  // не прокручивая страницу до конца.
  const SCROLLED = ['receipts', 'orders', 'stock', 'sku', 'transfers', 'packaging',
                    'receiptComposition', 'orderComposition', 'transferComposition'];

  function syncScroll(key){
    const top = el(key + 'ScrollTop');
    const x = el(key + 'ScrollX');
    if (!top || !x) return;
    let busy = false;
    top.addEventListener('scroll', () => { if (busy) return; busy = true; x.scrollLeft = top.scrollLeft; busy = false; });
    x.addEventListener('scroll', () => { if (busy) return; busy = true; top.scrollLeft = x.scrollLeft; busy = false; });
  }

  function updateScroll(key){
    const inner = el(key + 'ScrollTopInner');
    const x = el(key + 'ScrollX');
    const t = x && x.querySelector('table');
    if (inner && t) inner.style.width = t.scrollWidth + 'px';
  }

  /* ===================== короткие справочники ===================== */

  // Поставщики, грузополучатели, склады, поклажедатели лежат на странице
  // целиком — их сортирует браузер.
  /** @type {Record<string, {col: string | null, dir: number, render: (() => unknown) | null}>} */
  const dirs = {};

  const dirState = key => dirs[key] || (dirs[key] = {col: null, dir: 1, render: null});

  function dirSort(key, columnId){
    const s = dirState(key);
    if (s.col === columnId) s.dir = -s.dir; else { s.col = columnId; s.dir = 1; }
    if (s.render) s.render();
    dirArrows(key);
  }

  function dirArrows(key){
    const s = dirState(key);
    document.querySelectorAll('th[data-dir-sort="' + key + '"]').forEach(th => {
      if (!th.hasAttribute('data-label')) th.setAttribute('data-label', th.textContent.trim());
      th.textContent = th.getAttribute('data-label')
        + (s.col === th.getAttribute('data-dir-col') ? (s.dir === 1 ? ' ▲' : ' ▼') : '');
    });
  }

  /** Строки справочника в порядке, выбранном щелчком по заголовку. */
  function dirSorted(key, rows){
    const s = dirState(key);
    if (!s.col) return rows;
    const col = s.col;
    return rows.slice().sort((a, b) => {
      let va = a[col] ?? '', vb = b[col] ?? '';
      const na = parseFloat(va), nb = parseFloat(vb);
      if (!isNaN(na) && !isNaN(nb) && va !== '' && vb !== ''){ va = na; vb = nb; }
      return va < vb ? -s.dir : (va > vb ? s.dir : 0);
    });
  }

  /* ===================== наружу ===================== */

  PORTAL.columns = {layout, raw, all, renameOf, reload};

  PORTAL.tables = {
    /**
     * Зарегистрировать таблицу: load — сходить за данными (страница,
     * сортировка), render — нарисовать то, что есть.
     */
    register: (key, handlers) => { Object.assign(table(key), handlers); },
    columns: key => table(key).columns,
    visible,
    label,
    head,
    row,
    renderList,
    redraw,
    redrawAll,
    sort,
    page,
    pageSize,
    /** Страница и сортировка — для запроса списка (filters.js). */
    pageOf: key => table(key).page,
    sortOf: key => table(key).sort,
    /** Отбор изменился — с первой страницы. */
    firstPage: key => { table(key).page.page = 1; },
    /** Сервер вернул другую страницу (отбор сузился) — верим ему. */
    setPage: (key, n) => { if (n) table(key).page.page = n; },
    picker: pickerOpen,
    pickerClose,
    pickerToggle,
    pickerApply,
    pickerReset,
    updateScroll,
    dirSort,
    dirSorted,
    /** Короткий справочник: чем его перерисовать после щелчка по заголовку. */
    dirRegister: (key, render) => { dirState(key).render = render; }
  };

  function install(){
    SCROLLED.forEach(syncScroll);
    window.addEventListener('resize', () => SCROLLED.forEach(updateScroll));
    PORTAL.onLogin(() => {
      // колонки не загрузились — таблицы работают на своих умолчаниях
      reload(null).then(redrawAll).catch(() => {});
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
