
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const state = {
    result: null,              // ответ сервера целиком
    tab: 'all',                // all | new | existing
    onlyErrors: false,
    showNew: false,
    showExisting: false
  };

  /* ================= Разбор ответа ================= */

  /**
   * JSONB приходит текстом.
   *
   * Сервер отдаёт cell_values, fill_fields и issues приведёнными к text:
   * иначе наружу уходит объект драйвера PostgreSQL и в JSON превращается
   * в мусор вида {"type":"jsonb","value":"..."}. Разбираем здесь, один раз
   * на ответ.
   */
  function разобрать(ответ){
    const json = (v, запас) => {
      if (v === null || v === undefined) return запас;
      try { return JSON.parse(v); } catch (e){ return запас; }
    };
    const r = {
      importId: ответ.importId,
      fileName: ответ.fileName,
      rowCount: ответ.rowCount || 0,
      toCreate: ответ.toCreate || 0,
      toUpdate: ответ.toUpdate || 0,
      errorCount: ответ.errorCount || 0,
      columns: json(ответ.columnsUsedJson, []),
      skipped: json(ответ.columnsSkippedJson, []),
      rows: (ответ.rows || []).map(row => ({
        rowNumber: row.rowNumber,
        rowClass: row.rowClass,
        isValid: row.isValid === true,
        applied: row.applied === true,
        rowKey: row.rowKey,
        values: json(row.cellValuesJson, {}),
        display: json(row.cellDisplayJson, {}),
        fill: json(row.fillFieldsJson, []),
        issues: json(row.issuesJson, [])
      }))
    };
    return r;
  }

  /* ================= Отбор строк ================= */

  /**
   * Галочки фильтруют по двум признакам независимо.
   *
   * «Только ошибки» режет по годности, «Новые» и «Существующие» —
   * по классу и складываются между собой. Ни одна не отмечена —
   * видно всё: иначе человек, открыв экран, увидел бы пустую таблицу
   * и решил, что файл не прочитался.
   */
  function отобрать(rows){
    const классы = [];
    if (state.showNew) классы.push('new');
    if (state.showExisting) классы.push('existing');
    return rows.filter(r => {
      if (state.onlyErrors && r.isValid) return false;
      if (классы.length && классы.indexOf(r.rowClass) < 0) return false;
      return true;
    });
  }

  /* ================= Отрисовка ================= */

  const КЛАСС_ПОДПИСЬ = {
    new: 'sku.text.state_create',
    existing: 'sku.text.state_update'
  };

  function состояние(row){
    if (row.applied){
      return '<span class="badge ok">'
        + esc(T_(row.rowClass === 'new' ? 'sku.text.state_created' : 'sku.text.state_updated'))
        + '</span>';
    }
    if (!row.isValid){
      return '<span class="badge err">' + esc(T_('sku.text.state_error')) + '</span>';
    }
    const ключ = КЛАСС_ПОДПИСЬ[row.rowClass];
    let подпись = esc(T_(ключ || 'sku.text.state_error'));
    if (row.rowClass === 'existing' && row.fill.length){
      подпись += ' <span class="count">' + row.fill.length + '</span>';
    }
    return '<span class="badge">' + подпись + '</span>';
  }

  /** Замечания по конкретной колонке — ими подсвечивается ячейка. */
  function замечанияПоКолонке(row, columnId){
    return row.issues.filter(i => i.columnId === columnId);
  }

  /** Замечания к строке целиком: показываются под таблицей строки. */
  function замечанияСтроки(row){
    return row.issues.filter(i => !i.columnId);
  }

  /**
   * Что показать в ячейке.
   *
   * У ссылочных полей в values лежит идентификатор — то, что уйдёт
   * в базу. Человеку он не говорит ничего, поэтому сервер прислал
   * рядом display: «шт» вместо «1». Для текста и чисел display пуст —
   * показываем само значение.
   */
  function показать(row, column){
    const поле = dbColumnOf(column);
    const готовое = row.display ? row.display[поле] : undefined;
    return готовое !== undefined && готовое !== null ? готовое : row.values[поле];
  }

  function ячейка(row, column){
    const значение = показать(row, column);
    const замечания = замечанияПоКолонке(row, column.columnId);
    if (!замечания.length){
      return '<td>' + esc(значение === undefined ? '' : значение) + '</td>';
    }
    const i = замечания[0];
    // Расхождение показываем парой значений: без этого человек
    // не поймёт, почему строку пропустили.
    const тело = (i.portalValue !== null && i.portalValue !== undefined)
      ? esc(T_('sku.text.in_file')) + ' ' + esc(i.fileValue)
        + ' · ' + esc(T_('sku.text.in_portal')) + ' ' + esc(i.portalValue)
      // Значение не разобралось — показываем то, что стояло в файле:
      // двойной значок вместо текста ничего не объясняет.
      : esc(значение === undefined || значение === null
            ? (i.fileValue === null || i.fileValue === undefined ? '—' : i.fileValue)
            : значение);
    return '<td class="cell-bad" title="' + esc(i.message) + '">⚠ ' + тело + '</td>';
  }

  // Колонки файла приходят как {header, columnId}; значения разобранной
  // строки лежат под именами полей базы. Связь восстанавливаем по тому
  // же правилу, что на сервере.
  // Группа и тип товара стали свободным текстом (V38), поэтому
  // отдельного соответствия им больше не нужно: item_group
  // и item_type получаются из group и productType по общему правилу.
  const СВОИ = {
    unit: 'uom_id', group: 'item_group', productType: 'item_type',
    currency: 'currency_id', hazardClass: 'hazard_class_id',
    hazardGroup: 'hazard_group_id'
  };
  function dbColumnOf(column){
    const id = column.columnId;
    return СВОИ[id] || id.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
  }

  function таблица(rows, columns){
    if (!rows.length){
      return '<div style="color:var(--muted);font-size:13.5px;padding:12px 0;">'
        + esc(T_('common.text.list_is_empty')) + '</div>';
    }
    const шапка = '<tr><th>' + esc(T_('sku.column.file_row')) + '</th>'
      + '<th>' + esc(T_('sku.column.row_state')) + '</th>'
      + columns.map(c => '<th>' + esc(c.header) + '</th>').join('') + '</tr>';

    const тело = rows.map(row => {
      const общие = замечанияСтроки(row);
      let html = '<tr><td>' + row.rowNumber + '</td><td>' + состояние(row) + '</td>'
        + columns.map(c => ячейка(row, c)).join('') + '</tr>';
      if (общие.length){
        html += '<tr class="row-note"><td></td><td colspan="' + (columns.length + 1) + '"'
          + ' style="color:var(--muted);font-size:12.5px;">'
          + общие.map(i => esc(i.message)).join(' · ') + '</td></tr>';
      }
      return html;
    }).join('');

    return '<div class="table-card"><div class="table-scroll-x"><table>'
      + '<thead>' + шапка + '</thead><tbody>' + тело + '</tbody></table></div></div>';
  }

  function сводка(r){
    const счётчик = (ключ, значение, вид) =>
      '<span class="imp-count ' + вид + '">' + esc(T_(ключ)) + ' <b>' + значение + '</b></span>';
    let html = '<div class="hint" style="margin:-4px 0 10px;">'
      + esc(r.fileName) + ' · ' + r.rowCount + ' ' + esc(T_('sku.text.rows_in_file'));
    if (r.skipped.length){
      // Число рядом с перечислением лишнее, да и согласование числа
      // со словом в переводе пришлось бы решать отдельно.
      html += ' · ' + esc(T_('sku.text.columns_not_recognized'))
        + ': ' + r.skipped.map(esc).join(', ');
    }
    html += '</div><div class="imp-counts" style="display:flex;gap:16px;margin-bottom:12px;">'
      + счётчик('sku.text.count_create', r.toCreate, 'ok')
      + счётчик('sku.text.count_update', r.toUpdate, 'ok')
      + счётчик('sku.text.count_errors', r.errorCount, 'err')
      + '</div>';
    return html;
  }

  function галочки(){
    const г = (id, ключ, включено) =>
      '<label style="display:inline-flex;align-items:center;gap:6px;font-size:13.5px;'
      + 'margin-right:18px;"><input type="checkbox" data-imp-filter="' + id + '"'
      + (включено ? ' checked' : '') + '> ' + esc(T_(ключ)) + '</label>';
    return '<div class="filters" style="padding:10px 0;">'
      + г('onlyErrors', 'sku.label.only_errors', state.onlyErrors)
      + г('showNew', 'sku.label.show_new', state.showNew)
      + г('showExisting', 'sku.label.show_existing', state.showExisting)
      + '</div>';
  }

  function вкладки(){
    const кнопка = (id, ключ) =>
      '<button class="admin-subtab-btn' + (state.tab === id ? ' active' : '') + '"'
      + ' type="button" data-imp-tab="' + id + '">' + esc(T_(ключ)) + '</button>';
    return '<div class="admin-subtabs" id="impTabs">'
      + кнопка('all', 'sku.tab.all_rows')
      + кнопка('new', 'sku.tab.new_items')
      + кнопка('existing', 'sku.tab.existing_items')
      + '</div>';
  }

  function нарисовать(){
    const r = state.result;
    const view = document.getElementById('skuImportPanel');
    if (!view || !r) return;

    let html = '<div style="display:flex;align-items:flex-start;gap:12px;">'
      + '<h3 style="font-size:17px;flex:1;">' + esc(T_('sku.title.import_check')) + '</h3>'
      + '<button class="btn ghost" type="button" data-imp-close="1">'
      + esc(T_('common.button.close')) + '</button></div>'
      + сводка(r) + вкладки();

    if (state.tab === 'all'){
      html += галочки() + таблица(отобрать(r.rows), r.columns);
    } else if (state.tab === 'new'){
      // Только то, что действительно запишется: вкладка отвечает
      // на вопрос «что произойдёт», а не «что лежит в файле».
      const rows = r.rows.filter(x => x.rowClass === 'new' && x.isValid);
      html += таблица(rows, r.columns)
        + кнопкаПрименения('create', 'sku.button.create_new_items', rows);
    } else {
      const rows = r.rows.filter(x => x.rowClass === 'existing' && x.isValid);
      html += подсказкаЗаполнения(rows) + таблица(rows, r.columns)
        + кнопкаПрименения('update', 'sku.button.update_existing_items', rows);
    }

    document.getElementById('skuImportPanel').innerHTML = html;
    привязать();
  }

  function подсказкаЗаполнения(rows){
    if (!rows.length) return '';
    const поля = new Set();
    rows.forEach(r => r.fill.forEach(f => поля.add(f)));
    if (!поля.size) return '';
    // fill приходит именами полей базы — человеку они ничего не говорят.
    // Подписи берём из колонок файла: это те же названия, которые он
    // видит в шапке своего файла.
    const подпись = {};
    (state.result.columns || []).forEach(c => { подпись[dbColumnOf(c)] = c.header; });
    const имена = [...поля].map(f => подпись[f] || f);
    return '<div class="hint" style="margin:-4px 0 10px;">'
      + esc(T_('sku.text.will_fill')) + ': ' + имена.map(esc).join(', ') + '</div>';
  }

  function кнопкаПрименения(kind, ключ, rows){
    const можно = rows.some(r => !r.applied);
    return '<div class="actions" style="margin-top:16px;">'
      + '<button class="btn primary" type="button" data-imp-apply="' + kind + '"'
      + (можно ? '' : ' disabled') + '>' + esc(T_(ключ))
      + ' <span class="count">' + rows.filter(r => !r.applied).length + '</span>'
      + '</button></div>';
  }

  function привязать(){
    const view = document.getElementById('skuImportPanel');
    view.querySelectorAll('[data-imp-tab]').forEach(b => {
      b.onclick = () => { state.tab = b.getAttribute('data-imp-tab'); нарисовать(); };
    });
    view.querySelectorAll('[data-imp-filter]').forEach(cb => {
      cb.onchange = () => {
        state[cb.getAttribute('data-imp-filter')] = cb.checked;
        нарисовать();
      };
    });
    view.querySelectorAll('[data-imp-close]').forEach(b => {
      b.onclick = () => закрыть();
    });
    view.querySelectorAll('[data-imp-apply]').forEach(b => {
      b.onclick = () => применить(b.getAttribute('data-imp-apply'));
    });
  }

  /**
   * Запись в справочник.
   *
   * Тела у запроса нет: что записывать, сервер знает из результата
   * проверки. Ответ — тот же результат целиком, поэтому экран
   * перерисовывается им и ничего не додумывает: какие строки стали
   * «создано», какие остались, сколько осталось применить.
   *
   * Кнопка на время запроса гаснет: человек не должен нажать её
   * дважды. Защита от второго нажатия есть и на сервере (признак
   * applied), но заметить разницу человек должен сразу.
   */
  async function применить(kind){
    const кнопка = document.querySelector('[data-imp-apply="' + kind + '"]');
    if (кнопка) кнопка.disabled = true;
    try {
      const resp = await fetch('/api/sku/excel-import/'
          + encodeURIComponent(state.result.importId) + '/' + kind,
          {method: 'POST', headers: {'Accept': 'application/json'}});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      const ответ = await resp.json();
      state.result = разобрать(ответ);
      нарисовать();
      say(итог(kind, ответ));
      // Справочник под окном показывает прежний список: перечитываем
      // его, чтобы созданные позиции были на месте, когда окно закроют.
      if (typeof PORTAL.перечитатьТовары === 'function') PORTAL.перечитатьТовары();
    } catch (e){
      say(e.message);
      if (кнопка) кнопка.disabled = false;
    }
  }

  /** Итог словами: «Создано позиций: 60». */
  function итог(kind, ответ){
    const записано = ответ.applied || 0;
    const сбой = ответ.failed || 0;
    if (!записано && !сбой) return T_('sku.message.nothing_to_apply');
    const ключ = kind === 'create'
      ? 'sku.message.created_items' : 'sku.message.updated_items';
    let текст = T_(ключ) + ': ' + записано;
    if (сбой) текст += '. ' + T_('sku.message.apply_partly');
    return текст;
  }

  /* ================= Окно ================= */

  function создатьОкно(){
    if (document.getElementById('skuImportOverlay')) return;
    const окно = document.createElement('div');
    окно.className = 'col-overlay';
    окно.id = 'skuImportOverlay';
    окно.style.cssText = 'align-items:center;justify-content:center;';
    // Щелчок по затемнению — то же, что «Закрыть»: привычное поведение
    // окна. Но только по самому затемнению, иначе окно закрывалось бы
    // от щелчка по таблице внутри.
    окно.onclick = e => { if (e.target === окно) закрыть(); };
    окно.innerHTML = ''
      + '<div class="col-panel" id="skuImportPanel" style="width:95vw;max-width:1600px;'
      + 'max-height:92vh;overflow:auto;border-radius:10px;'
      + 'box-shadow:0 8px 32px rgba(0,0,0,.24);"></div>';
    document.body.appendChild(окно);

    // Подсветка проблемных ячеек. Своих классов у прототипа для этого
    // нет, а подсветка здесь — главное, что человек видит.
    const style = document.createElement('style');
    style.textContent = '#skuImportPanel td.cell-bad{background:#fef2f2;color:#991b1b;}'
      + '#skuImportPanel tr.row-note td{background:#fffbeb;}'
      + '#skuImportPanel .imp-count{font-size:13.5px;color:var(--muted);}'
      + '#skuImportPanel .imp-count b{color:var(--text);font-size:15px;}'
      + '#skuImportPanel .imp-count.err b{color:#991b1b;}'
      + '#skuImportPanel .table-scroll-x{max-height:60vh;overflow:auto;}';
    document.head.appendChild(style);

    // Esc закрывает окно. Обработчик один на страницу и проверяет,
    // открыто ли окно: иначе он мешал бы остальным окнам портала.
    document.addEventListener('keydown', e => {
      if (e.key !== 'Escape') return;
      const o = document.getElementById('skuImportOverlay');
      if (o && o.classList.contains('active')) закрыть();
    });
  }

  /**
   * Закрытие отменяет загрузку.
   *
   * Разобранные строки живут на сервере только ради этого окна.
   * Если их не применили, держать их незачем: следующая загрузка
   * того же файла начнётся заново, а не продолжит прошлую.
   *
   * Отказ сервера на отмене не показываем: человек уже закрыл окно,
   * и сообщение про отмену ему нечего делать. Строки всё равно
   * не применены.
   */
  async function закрыть(){
    const окно = document.getElementById('skuImportOverlay');
    const importId = state.result && state.result.importId;
    state.result = null;
    if (окно) окно.classList.remove('active');
    if (importId){
      try {
        await fetch('/api/sku/excel-import/' + encodeURIComponent(importId),
                    {method: 'DELETE'});
      } catch (e){ /* молча: окно уже закрыто */ }
    }
  }

  function открыть(ответ){
    создатьОкно();
    state.result = разобрать(ответ);
    state.tab = 'all';
    state.onlyErrors = false;
    state.showNew = false;
    state.showExisting = false;
    нарисовать();
    document.getElementById('skuImportOverlay').classList.add('active');
  }

  window.portalOpenSkuImport = открыть;

  /* ================= Выбор файла ================= */

  function выбратьФайл(){
    let input = document.getElementById('skuImportFile');
    if (!input){
      input = document.createElement('input');
      input.type = 'file';
      input.id = 'skuImportFile';
      input.accept = '.xlsx';
      input.style.display = 'none';
      document.body.appendChild(input);
      input.onchange = () => {
        const file = input.files && input.files[0];
        // значение сбрасываем, иначе повторный выбор того же файла
        // не вызовет onchange и человек решит, что кнопка сломалась
        input.value = '';
        if (file) отправить(file);
      };
    }
    input.click();
  }

  async function отправить(file){
    const body = new FormData();
    body.append('file', file);
    try {
      const resp = await fetch('/api/sku/excel-import', {method: 'POST', body: body});
      if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
      открыть(await resp.json());
    } catch (e){
      say(e.message);
    }
  }

  /**
   * Прячет кнопки «Загрузить из Excel», которые ещё ничего не делают.
   *
   * Загрузка сделана только для товаров. На поступлениях, заказах,
   * перемещениях, отчётах и упаковках кнопки остались заглушками
   * из прототипа: нажатие показывает сообщение и всё. Кнопка, которая
   * выглядит рабочей и не работает, хуже отсутствующей — человек
   * решит, что загрузил.
   *
   * Вернуть легко: убрать этот вызов.
   */
  function спрятатьЗаглушки(){
    const свои = ['uploadSkuExcelBtn'];
    document.querySelectorAll('button').forEach(btn => {
      if (свои.indexOf(btn.id) >= 0) return;
      const onclick = btn.getAttribute('onclick') || '';
      if (/notify\('(Диалог загрузки|Загрузка ранее подготовленного)/.test(onclick)){
        btn.style.display = 'none';
      }
    });
  }

  function install(){
    PORTAL.onLogin(() => {
      const btn = document.getElementById('uploadSkuExcelBtn');
      if (btn && !btn.dataset.wired){
        btn.dataset.wired = '1';
        btn.removeAttribute('onclick');
        btn.onclick = () => выбратьФайл();
      }
      спрятатьЗаглушки();
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
