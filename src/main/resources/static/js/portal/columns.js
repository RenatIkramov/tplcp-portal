
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
    return resp.status === 204 ? null : resp.json();
  }

  // Раскладка с сервера: таблица -> [{columnId, label, isVisible, sortOrder, isPinned}]
  //
  // isPinned — «из таблицы убрать нельзя» (V36). Это не то же, что
  // «поклажедателю скрыть нельзя»: служебные поля товара клиенту
  // скрыть нельзя, но себе в таблице человек их убирает свободно.
  const layout = {};
  // raw — какие колонки сервер вообще отдал по таблице, до отбрасывания
  // тех, которых нет в вёрстке. Именно это и есть «доступно клиенту»:
  // запрещённую колонку table_columns не отдаёт совсем.
  const raw = {};
  // Всё, что отдал сервер по таблице, строками целиком. Отличается
  // от layout: туда попадают только колонки, которые прототип умеет
  // рисовать. Поля, которые живут в карточке, а не в таблице —
  // например доп. поля шапки документа, — в layout не попадают,
  // но переименование у них есть, и искать его надо здесь.
  const all = {};
  PORTAL.columns = {layout: layout, raw: raw, all: all};

  /* ============ Применение раскладки к состоянию прототипа ============ */

  // Видимые идут первыми в своём порядке, за ними скрытые: так выглядит
  // список в окне выбора, и так же человек ожидает его увидеть.
  function ordered(rows){
    const vis = rows.filter(r => r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    const hid = rows.filter(r => !r.isVisible).sort((a, b) => a.sortOrder - b.sortOrder);
    return vis.concat(hid);
  }

  // tableColumnState и соседи объявлены в прототипе через const на верхнем
  // уровне. Такие объявления лексические: свойства у window для них
  // не создаётся, и обращение через window.tableColumnState даёт
  // undefined. Поэтому читаем по голому имени, прикрываясь typeof.
  const hasGlobal = name => {
    try { return eval('typeof ' + name) !== 'undefined'; } catch (e){ return false; }
  };

  /**
   * Колонка есть в справочнике, но её нет в вёрстке демо — дорисовываем
   * сами.
   *
   * <p>Здесь стояло обратное: такие колонки отбрасывались, «рисовать
   * её нечем, поэтому ориентируемся на то, что умеет прототип».
   * Рассуждение верное для колонки, ПРОПАВШЕЙ из вёрстки, и неверное
   * для новой. А новые появились: V68 завёл «Рейс» как настоящую
   * колонку всех трёх списков, и десять «Доп. полей» поклажедателя
   * были в справочнике с самого начала. Ни одной из них не было
   * в окне ⚙ Колонки — включить их человек не мог, хотя в базе они
   * ему разрешены. Получалось, что справочник колонок решает всё,
   * кроме того, что человек видит.
   *
   * <p>Рисуется значением одноимённого свойства строки списка.
   * Подпись берётся без переименования клиента (portalLabel):
   * переименование накладывает сам прототип в localizeColumnLabel,
   * и готовая подпись применилась бы дважды.
   */
  function adopt(tableKey, rows){
    const cols = tableColumns[tableKey];
    if (!cols) return;
    const known = new Set(cols.map(c => c.id));
    rows.forEach(r => {
      if (known.has(r.columnId)) return;
      known.add(r.columnId);
      cols.push({
        id: r.columnId,
        label: r.portalLabel || r.label,
        render: item => {
          const v = item[r.columnId];
          return v === null || v === undefined || v === '' ? '—' : v;
        }
      });
    });
  }

  function applyTable(tableKey, rows){
    raw[tableKey] = rows.map(r => r.columnId);
    all[tableKey] = rows;
    if (!hasGlobal('tableColumnState') || !tableColumnState[tableKey]) return;
    adopt(tableKey, rows);
    const known = new Set((tableColumns[tableKey] || []).map(c => c.id));
    const rowsHere = ordered(rows.filter(r => known.has(r.columnId)));
    if (!rowsHere.length) return;

    layout[tableKey] = rowsHere;
    tableColumnState[tableKey] = rowsHere.map(r => r.columnId);
    tableColumnHidden[tableKey] = new Set(
        rowsHere.filter(r => !r.isVisible).map(r => r.columnId));
    // tableColumnLocked НЕ трогаем. В прототипе это значит «не рисовать
    // эту колонку в таблице» (renderTableHead и renderTableRow её
    // отфильтровывают), а вовсе не «нельзя скрыть». Когда в V36
    // появилось прикрепление и я записал прикреплённые сюда, коммерческий
    // код, наименование и единица пропали и из списка товаров, и из окна
    // выбора колонок. Запрет «эти не убрать» живёт в обработчике
    // сохранения (он дописывает их обратно) и в серой галочке окна.
  }

  function applyAll(rows){
    const byTable = {};
    rows.forEach(r => { (byTable[r.tableKey] = byTable[r.tableKey] || []).push(r); });
    Object.keys(byTable).forEach(t => applyTable(t, byTable[t]));
  }

  /**
   * Как ЭТОТ клиент называет поле, или null, если не переименовывал.
   *
   * Сервер отдаёт обе подписи: label (с переименованием) и portal_label
   * (без). Разница между ними и есть переименование — единственное,
   * чего экран не может узнать сам: перевод он берёт по ключу,
   * а подписи карточек вписаны в разметку.
   *
   * Отдаём именно разницу, а не готовую подпись. Если текста в каталоге
   * нет, серверный COALESCE доходит до ключа, и подстановка подписи
   * всегда поставила бы в карточку «col.sku.manufacturer» вместо
   * «Производитель». На этом уже попалась проверка карточки товара.
   *
   * Переименование от языка не зависит (решение заказчика): клиент
   * назвал поле по-своему, и язык интерфейса его слово не отменяет.
   * Поэтому раскладку после смены языка перечитывать не нужно —
   * разница между подписями останется той же.
   *
   * Ищем в полном ответе сервера, а не в раскладке: раскладка — это то,
   * что рисует таблица, а переименовать можно и поле, которое
   * показывается только в карточке (доп. поля шапки документа).
   */
  function renameOf(tableKey, columnId){
    const rows = all[tableKey];
    if (!rows) return null;
    const row = rows.find(r => r.columnId === columnId);
    if (!row || !row.portalLabel) return null;
    return row.label !== row.portalLabel ? row.label : null;
  }
  PORTAL.columns.renameOf = renameOf;

  /** Какая таблица содержит этот объект колонки прототипа. */
  function tableOfColumn(column){
    for (const t of Object.keys(layout)){
      if ((tableColumns[t] || []).some(x => x === column)) return t;
    }
    return null;
  }
  PORTAL.columns.tableOfColumn = tableOfColumn;

  // Перерисовка таблицы после смены раскладки. Имена функций берём
  // у прототипа: каждая таблица рисуется своей.
  const RENDER = {
    receipts: 'renderReceipts', orders: 'renderOrders', transfers: 'renderTransfers',
    stock: 'renderStock', stockBatch: 'renderStockBatchTable', sku: 'renderSku',
    receiptComposition: 'renderReceiptComposition',
    orderComposition: 'renderOrderComposition',
    transferComposition: 'renderTransferComposition',
    receiptLineDetail: 'renderReceiptLineDetailTable',
    orderLineDetail: 'renderOrderLineDetailTable',
    transferLineDetail: 'renderTransferLineDetailTable',
    packaging: 'renderPackaging'
  };

  // Таблица может быть на скрытом экране, и её отрисовка падает на
  // отсутствующем элементе. Одна такая ошибка не должна обрывать
  // остальные — та же беда уже была при смене языка.
  function redraw(tableKey){
    const name = RENDER[tableKey];
    if (!name || typeof window[name] !== 'function') return;
    try {
      window[name]();
      if (typeof updateTopScrollWidth === 'function') updateTopScrollWidth(tableKey);
    } catch (e){ /* экран не открыт — перерисуется при открытии */ }
  }

  function redrawAll(){ Object.keys(layout).forEach(redraw); }

  async function reload(tableKey){
    const url = tableKey ? '/api/table-columns/' + encodeURIComponent(tableKey)
                         : '/api/table-columns';
    const rows = await api('GET', url);
    if (tableKey) applyTable(tableKey, rows); else applyAll(rows);
    return rows;
  }
  PORTAL.columns.reload = reload;

  /* ============ Сохранение выбора ============ */

  function wrapPicker(){
    const applyOrig = window.applyColumnPicker;
    if (typeof applyOrig === 'function'){
      window.applyColumnPicker = function(){
        // прочитать ДО вызова оригинала: он закрывает окно и обнуляет обе
        const tableKey = hasGlobal('colPickerTable') ? colPickerTable : null;
        const draft = hasGlobal('colPickerDraft') ? colPickerDraft : null;
        // Прототип сначала применяет выбор у себя — экран меняется
        // сразу, не дожидаясь ответа сервера.
        //
        // В try нарочно. Отрисовку он ищет по своей карте таблиц,
        // а таблиц у портала больше: упаковки (V76) там нет вовсе,
        // и вызов падал на undefined — окно оставалось открытым,
        // а выбор не доходил до сервера. Своя перерисовка идёт ниже
        // и этой карты не спрашивает.
        try {
          applyOrig.apply(this, arguments);
        } catch (e){
          // Выбор он к этому моменту уже применил — падает позже,
          // на отрисовке, и до закрытия окна не доходит. Закрываем
          // сами: окно, оставшееся висеть после «Применить», человек
          // читает как «не сработало» и жмёт второй раз.
          console.warn('Окно колонок, применение в прототипе: ' + e.message);
          if (typeof closeColumnPicker === 'function') closeColumnPicker();
        }
        if (!tableKey || !draft) return;
        const chosen = draft.order.filter(id => !draft.hidden.has(id));
        const locked = (layout[tableKey] || []).filter(r => r.isPinned).map(r => r.columnId);
        locked.forEach(id => { if (!chosen.includes(id)) chosen.push(id); });
        if (!chosen.length){
          say(T('common.message.keep_at_least_one_column'));
          reload(tableKey).then(() => redraw(tableKey)).catch(() => {});
          return;
        }
        api('PUT', '/api/table-columns/' + encodeURIComponent(tableKey), {columns: chosen})
          .then(rows => { applyTable(tableKey, rows); redraw(tableKey); })
          .catch(err => {
            say(T('common.message.columns_shown_but_not_saved') + ': ' + err.message);
            reload(tableKey).then(() => redraw(tableKey)).catch(() => {});
          });
      };
    }

    // Прикреплённую колонку снять нельзя — галочка серая и отмечена.
    // Запрет работает и без этого (обработчик сохранения дописывает её
    // обратно), но тогда человек снимает галочку, нажимает «Применить»
    // и видит колонку на месте — выглядит как сломанное окно.
    const renderOrig = window.renderColumnPickerList;
    if (typeof renderOrig === 'function'){
      window.renderColumnPickerList = function(){
        renderOrig.apply(this, arguments);
        const tableKey = hasGlobal('colPickerTable') ? colPickerTable : null;
        if (!tableKey) return;
        const pinned = new Set((layout[tableKey] || [])
            .filter(r => r.isPinned).map(r => r.columnId));
        if (!pinned.size) return;
        const list = document.getElementById('colList');
        if (!list) return;
        [...list.querySelectorAll('.col-item')].forEach(el => {
          const cb = el.querySelector('input[type=checkbox]');
          const onchange = cb && cb.getAttribute('onchange') || '';
          const m = onchange.match(/toggleColVisibility\('([^']+)'\)/);
          if (!m || !pinned.has(m[1])) return;
          cb.checked = true;
          cb.disabled = true;
          el.title = T('references.hint.column_cannot_be_hidden');
          el.style.color = 'var(--muted)';
        });
      };
    }

    // «Сбросить» теперь значит «вернуть настройки по умолчанию», а не
    // «показать всё»: именно этого человек от кнопки и ждёт. Действие
    // применяется сразу, поэтому окно закрывается — иначе осталась бы
    // кнопка «Применить», которая записала бы умолчания как личный выбор.
    window.resetColumnPicker = function(){
      const tableKey = hasGlobal('colPickerTable') ? colPickerTable : null;
      if (!tableKey) return;
      // Сброс рисует экран сам (redraw ниже), карта прототипа
      // здесь не участвует — но таблицу он всё равно должен знать.
      api('DELETE', '/api/table-columns/' + encodeURIComponent(tableKey))
        .then(rows => {
          applyTable(tableKey, rows);
          redraw(tableKey);
          if (typeof closeColumnPicker === 'function') closeColumnPicker();
          say(T('common.message.columns_reset_to_default'));
        })
        .catch(err => say(err.message));
    };
  }

  /* ============ Колонки поклажедателя: экран переехал ============ */
  /*
   * Здесь была вкладка «Колонки» карточки поклажедателя в справочниках:
   * двенадцать таблиц слева, колонки с галочками справа.
   *
   * Она настраивала ровно то же, что вкладка «Поля» в карточке доступов
   * поклажедателя, и писала в те же строки depositor_table_column.
   * Два экрана на одну настройку рано или поздно расходятся: на одном
   * появляется признак, которого нет на другом, и человек перестаёт
   * понимать, какой из них главный. Решение заказчика: всё про одного
   * клиента — в его карточке в «Доступах поклажедателей», там же, где
   * склады, разделы и переименование полей.
   *
   * API (/api/admin/depositor-columns) не тронут: им пользуется та самая
   * вкладка. Удалён только второй вход в него.
   */

  /* ============ Копирование настроек пользователю ============ */

  // Право решает сервер; здесь — только показывать ли блок вовсе.
  // Разработчик ходит по порталу в режиме чтения, ему править нельзя.
  const mayEdit = () => ['admin'].includes(
      PORTAL.session && PORTAL.session.roleCode);


  function wrapUserCard(){
    const openOrig = window.portalOpenAdminUser;
    if (typeof openOrig !== 'function') return;
    window.portalOpenAdminUser = async function(userId){
      await openOrig.apply(this, arguments);
      if (!mayEdit() || !userId) return;
      await addCopyBlock(userId);
    };
  }

  async function addCopyBlock(userId){
    const host = document.getElementById('adminSetPassword');
    const row = host && host.parentNode;
    if (!row || document.getElementById('copySettingsFrom')) return;
    let users;
    try {
      users = await api('GET', '/api/admin/users');
    } catch (e){ return; }
    const others = users.filter(u => u.userId !== userId && u.isActive !== false);
    if (!others.length) return;
    const box = document.createElement('div');
    box.style.cssText = 'display:flex; gap:10px; align-items:flex-end; flex-wrap:wrap;'
        + ' margin-top:14px; width:100%;';
    box.innerHTML = '<div class="kv" style="margin:0; min-width:280px;">'
      + '<label>' + T('admin.label.copy_settings_from_user') + '</label>'
      + '<select class="kv-input kv-input-editable" id="copySettingsFrom">'
      + '<option value="">' + T('common.option.not_selected') + '</option>'
      + others.map(u => '<option value="' + esc(u.userId) + '">'
          + esc(u.fullName || u.login) + ' (' + esc(u.login) + ')</option>').join('')
      + '</select></div>'
      + '<button class="btn" type="button" onclick="portalCopyUserSettings(\''
      + esc(userId) + '\')">' + T('admin.button.copy') + '</button>'
      + '<div class="hint" style="width:100%; margin:0;">'
      + '' + T('admin.hint.what_copy_settings_transfers') + '</div>';
    row.parentNode.insertBefore(box, row.nextSibling);
  }

  window.portalCopyUserSettings = async function(toUserId){
    const sel = document.getElementById('copySettingsFrom');
    if (!sel || !sel.value){
      say(T('admin.message.pick_whom_to_copy_from'));
      return;
    }
    try {
      await api('POST', '/api/admin/users/copy-settings',
          {fromUserId: sel.value, toUserId: toUserId});
      say(T('admin.message.settings_copied'));
    } catch (e){
      say(e.message);
    }
  };

  /* ============ Установка ============ */

  function install(){
    wrapPicker();
    wrapUserCard();

    PORTAL.onLogin(() => {
      reload(null).then(redrawAll).catch(() => {
        // колонки не загрузились — прототип работает на своих умолчаниях,
        // экран остаётся рабочим
      });
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
