/**
 * Панель фильтров списка документов и окно ⚙ Фильтры.
 *
 * ЧТО ЗДЕСЬ. Панель над таблицей поступлений, заказов, перемещений,
 * запасов, товаров и упаковок больше не написана в разметке. Она строится из справочника —
 * GET /api/list-filters/{список} — и отбор делает сервер: значения
 * уходят параметрами запроса, а не отсеиваются в браузере.
 *
 * ЗАЧЕМ ТАК. Прежняя панель была набором полей, вписанных в index.html
 * по одному на экран, и фильтровала то, что уже лежит на странице.
 * Из этого росли пять поломок сразу:
 *   — отбор по статусу шёл по переведённой подписи, и на английском
 *     языке не находил ничего;
 *   — «Дата отгрузки с/по» у заказов отбирала дату документа —
 *     подпись обещала одно, фильтр делал другое;
 *   — «Дата по» отсекала последний день: дата стала отметкой времени,
 *     а сравнение осталось по первым десяти знакам;
 *   — у перемещений не было ни статуса, ни складов, хотя перемещение
 *     этой парой складов и определяется;
 *   — отобрать можно было только по тем шести полям, что вписаны
 *     в разметку, сколько бы колонок человек себе ни включил.
 * Все пять — следствия одного: панель не знала про справочник колонок.
 * Теперь знает, и подпись фильтра — та же, что подпись колонки,
 * вместе с переименованием поклажедателя.
 *
 * ОДНО ПОЛЕ — ОДНО ПОНЯТИЕ. Здесь было объединённое поле «Номер или
 * контрагент», искавшее сразу по трём номерам и по контрагенту.
 * Заказчику не понравилось, и возражение верное: подписать такое поле
 * нечем — человек не знает, что он сейчас ищет, и не может понять,
 * почему не нашлось. Вместо него отдельные поля с отдельными
 * подписями: «Номер», «Поставщик», «Рейс». Чего нет на панели —
 * добавляется через ⚙ Фильтры.
 *
 * ⚙ ФИЛЬТРЫ. Фильтруемых колонок около тридцати на экран; все тридцать
 * на панели были бы хуже шести. Поэтому рисуется выбранный набор,
 * а окно ⚙ Фильтры позволяет его поменять — ровно как ⚙ Колонки,
 * теми же галочками и тем же перетаскиванием. Выбор хранится
 * у человека (user_table_filter, V70); не настраивал — действует
 * набор по умолчанию.
 *
 * ДАТЫ. Одна колонка — одна запись в окне ⚙ Фильтры, но на панели она
 * рисуется парой полей, «с» и «по»: диапазон человеку нужен чаще
 * одной даты. Диапазон по умолчанию — с первого числа прошлого месяца
 * по сегодня, как было в прототипе; без него список открывался бы
 * целиком за все годы.
 */
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  /**
   * Списки с панелью фильтров. Имя — оно же ключ таблицы в справочнике
   * колонок и кусок адреса запроса.
   */
  const TABLES = ['receipts', 'orders', 'transfers', 'stock', 'sku', 'packaging'];

  /** Справочник фильтров, как его отдал сервер. */
  const catalogue = {};
  TABLES.forEach(t => { catalogue[t] = []; });

  /** Виды фильтров, у которых две границы, а не одно значение. */
  const RANGED = ['date', 'number'];
  const ranged = kind => RANGED.includes(kind);

  /**
   * Где у экрана панель.
   *
   * <p>У документов и запасов это свой раздел — {@code #view-receipts}.
   * Товары и упаковки живут вкладками внутри «Справочников», и у них
   * своё место в разметке. Строить селектор из имени списка больше
   * нельзя: именно так панель товаров и не находилась бы, причём молча,
   * а молча не найденная панель — это экран вообще без фильтров.
   */
  const PANEL = {
    sku: '#ref-sub-sku .filters',
    packaging: '#ref-sub-packaging .filters'
  };

  const panelOf = table =>
      document.querySelector(PANEL[table] || ('#view-' + table + ' .filters'));

  /**
   * Списки с архивом: там, где запись не удаляют, а гасят.
   *
   * <p>Архив не фильтр панели, а свойство списка, поэтому его галочка
   * не приходит из справочника и в ⚙ Фильтрах её нет: убрать её человек
   * не может, иначе архив стал бы недостижим — а вернуть позицию
   * в работу можно только открыв её карточку.
   */
  const ARCHIVED = ['sku'];
  const archiveId = table => 'filter-' + table + '-archived';

  /** Поле панели. Имя строится из экрана и колонки — больше ниоткуда. */
  const fieldId = (table, columnId, bound) =>
      'filter-' + table + '-' + columnId + (bound ? '-' + bound : '');

  /**
   * Значение поля. У флажка значения нет — есть отметка, и в запрос
   * она уходит словом: пустое значение сервер понимает как
   * «не фильтровать».
   */
  const valueOf = id => {
    const el = document.getElementById(id);
    if (!el) return '';
    if (el.type === 'checkbox') return el.checked ? 'true' : '';
    return (el.value || '').trim();
  };

  async function api(method, url, body){
    const resp = await fetch(url, {
      method: method,
      headers: {'Accept': 'application/json', 'Content-Type': 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
    return resp.status === 204 ? null : resp.json();
  }

  /* ---------------- сборка панели ---------------- */

  function fgroup(id, label, control){
    const box = document.createElement('div');
    box.className = 'fgroup';
    const cap = document.createElement('label');
    cap.setAttribute('for', id);
    cap.textContent = label;
    box.appendChild(cap);
    box.appendChild(control);
    return box;
  }

  function input(id, type){
    const el = document.createElement('input');
    el.id = id;
    if (type) el.type = type;
    return el;
  }

  function select(id, options){
    const el = document.createElement('select');
    el.id = id;
    // Первый вариант — «Все», и значение у него пустое: пустое поле
    // означает «не фильтровать», так его и понимает сервер.
    const any = document.createElement('option');
    any.value = '';
    any.textContent = T('admin.option.all');
    el.appendChild(any);
    (options || []).forEach(o => {
      const item = document.createElement('option');
      item.value = String(o.value);
      item.textContent = o.label;
      el.appendChild(item);
    });
    return el;
  }

  /**
   * Флажок: «есть ли». Подпись — название колонки, а что именно
   * делает флажок, говорит подсказка: одна на все флажки, потому что
   * правило у них одно.
   */
  function checkbox(id, label, hintKey){
    const box = document.createElement('div');
    box.className = 'fgroup';
    // Пустая подпись сверху — чтобы флажок встал вровень с соседними
    // полями, а не подпрыгнул к их подписям.
    const spacer = document.createElement('label');
    spacer.innerHTML = '&nbsp;';
    const line = document.createElement('label');
    line.className = 'filter-flag';
    const hint = hintKey || 'common.hint.only_nonzero';
    line.title = T(hint);
    line.setAttribute('data-i18n-title', hint);
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.id = id;
    const text = document.createElement('span');
    text.textContent = label;
    line.appendChild(input);
    line.appendChild(text);
    box.appendChild(spacer);
    box.appendChild(line);
    return box;
  }

  /** Поля одного фильтра: одно, а для диапазона — два. */
  function fieldsOf(table, f){
    if (ranged(f.filterKind)){
      const type = f.filterKind === 'date' ? 'date' : 'number';
      return ['from', 'to'].map(bound => fgroup(
          fieldId(table, f.columnId, bound),
          f.label + ' ' + T(bound === 'from' ? 'common.label.range_from'
                                             : 'common.label.range_to'),
          input(fieldId(table, f.columnId, bound), type)));
    }
    if (f.filterKind === 'enum'){
      return [fgroup(fieldId(table, f.columnId), f.label,
          select(fieldId(table, f.columnId), f.options))];
    }
    if (f.filterKind === 'flag'){
      return [checkbox(fieldId(table, f.columnId), f.label)];
    }
    // Галочка по логической колонке: «Только хрупкие». Подсказка у неё
    // своя — «только отмеченные»: у флажка речь о числе больше нуля,
    // и обещать то же про «Да/Нет» значило бы объяснять неверно.
    if (f.filterKind === 'bool'){
      return [checkbox(fieldId(table, f.columnId), f.label, 'common.hint.only_marked')];
    }
    return [fgroup(fieldId(table, f.columnId), f.label,
        input(fieldId(table, f.columnId)))];
  }

  /** Фильтры, которые человек держит на панели, в его порядке. */
  const chosenOf = table => (catalogue[table] || []).filter(f => f.isChosen);

  /**
   * Перерисовывает панель экрана, сохраняя набранное.
   *
   * <p>Сохранять приходится потому, что панель собирается заново при
   * смене языка и после окна ⚙ Фильтры: подписи приходят с сервера
   * на языке человека, а выброшенный вместе с ними отбор человек
   * воспринял бы как поломку.
   */
  function build(table){
    const panel = panelOf(table);
    if (!panel) return;

    const kept = snapshot(table);

    // Убираем только то, что нарисовали сами: кнопки «Найти»,
    // «Очистить фильтры», «⚙ Фильтры» и «⚙ Колонки» остаются на месте.
    panel.querySelectorAll('.fgroup').forEach(node => node.remove());

    // Поля дорисовываются в конец: значки «Найти» и «Очистить» стоят
    // в разметке первыми и остаются слева, а поля идут правее
    // и переносятся на следующую строку при переполнении.
    //
    // .filters — flex-контейнер, и .fgroup обязан быть его ПРЯМЫМ
    // потомком: внутри обёртки подпись и поле встанут в строку
    // вместо столбика.
    const add = node => panel.appendChild(node);

    chosenOf(table).forEach(f => fieldsOf(table, f).forEach(add));
    // Архив — последним: это не отбор по полю, а «показать и то, чего
    // в работе больше нет». Дорисовывается здесь, а не один раз при
    // входе, потому что панель собирается заново при смене языка
    // и после ⚙ Фильтров — вписанная однажды галочка исчезла бы
    // вместе с полями.
    if (ARCHIVED.indexOf(table) >= 0){
      add(checkbox(archiveId(table), T('portal.text.show_archived'),
          'common.hint.show_archived'));
    }

    restore(table, kept);
    wire(table);
  }

  function snapshot(table){
    const panel = panelOf(table);
    const kept = {};
    // У флажка запоминается отметка, у остальных — значение: панель
    // собирается заново при смене языка и после окна ⚙ Фильтры,
    // и потерять при этом отбор человека нельзя.
    if (panel) panel.querySelectorAll('.fgroup [id]').forEach(el => {
      kept[el.id] = el.type === 'checkbox' ? el.checked : el.value;
    });
    return kept;
  }

  function restore(table, kept){
    Object.keys(kept).forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      if (el.type === 'checkbox') el.checked = kept[id] === true;
      else el.value = kept[id];
    });
  }

  /**
   * Enter в любом поле — то же, что нажать «Найти».
   *
   * <p>На выпадающих списках здесь висел ещё и onchange: выбрал
   * статус — список перечитался сам. Казалось удобным, а вышло
   * непоследовательно: текстовые поля ждут кнопки, а статус нет,
   * и человек не может набрать отбор целиком — панель уходит
   * на сервер на полпути. Отбор начинается по кнопке или по Enter,
   * и только так.
   */
  function wire(table){
    const panel = panelOf(table);
    if (!panel) return;
    panel.querySelectorAll('.fgroup input, .fgroup select').forEach(el => {
      el.onkeydown = e => { if (e.key === 'Enter') apply(table); };
    });
  }

  /* ---------------- запрос ---------------- */

  /**
   * Параметры запроса из состояния панели.
   *
   * <p>Пустые поля не отправляются: пустое поле — это «не фильтровать»,
   * и отправлять его значило бы просить сервер решать за экран.
   */
  PORTAL.filterQuery = function(table){
    const parts = [];
    chosenOf(table).forEach(f => {
      if (ranged(f.filterKind)){
        ['from', 'to'].forEach(bound => {
          const v = valueOf(fieldId(table, f.columnId, bound));
          if (v) parts.push('filter.' + f.columnId + '.' + bound + '=' + encodeURIComponent(v));
        });
      } else {
        const v = valueOf(fieldId(table, f.columnId));
        if (v) parts.push('filter.' + f.columnId + '=' + encodeURIComponent(v));
      }
    });
    return parts.length ? '?' + parts.join('&') : '';
  };

  /**
   * Весь запрос списка: отбор, страница и порядок.
   *
   * <p>Страница и сортировка берутся из состояния самого экрана
   * ({@code paginationState}, {@code sortState}) — того, которым
   * управляют ряд страниц и щелчки по заголовкам. Заводить рядом
   * второе состояние значило бы дважды хранить одно и то же:
   * ровно так расходятся номер страницы на кнопке и номер страницы
   * в запросе.
   *
   * <p>Сортировка уходит по ИДЕНТИФИКАТОРУ колонки экрана
   * (weight, supplier). Во что он превратится в ORDER BY, решает
   * сервер по справочнику колонок: имя колонки базы экрану знать
   * незачем, а подставлять в запрос пришедшее с экрана — дыра.
   */
  PORTAL.listQuery = function(table){
    const parts = [];
    const query = PORTAL.filterQuery(table);
    if (query) parts.push(query.slice(1));

    const page = typeof paginationState !== 'undefined' ? paginationState[table] : null;
    if (page){
      parts.push('page=' + Math.max(1, page.page || 1));
      parts.push('pageSize=' + (page.pageSize || 20));
    }
    const sort = typeof sortState !== 'undefined' ? sortState[table] : null;
    if (sort && sort.col){
      parts.push('sort=' + encodeURIComponent(sort.col));
      parts.push('dir=' + (sort.dir < 0 ? 'desc' : 'asc'));
    }
    if (ARCHIVED.indexOf(table) >= 0 && valueOf(archiveId(table))){
      parts.push('withArchived=true');
    }
    return parts.length ? '?' + parts.join('&') : '';
  };

  /**
   * Перечитать список экрана с нынешним отбором.
   *
   * <p>Отбор изменился — возвращаемся на первую страницу. Человек
   * стоял на пятой, сузил список до десяти строк, и пятая страница
   * пуста: экран показал бы «ничего не найдено» там, где найдено.
   */
  function apply(table){
    if (typeof paginationState !== 'undefined' && paginationState[table]){
      paginationState[table].page = 1;
    }
    reload(table);
  }

  /** Сходить за списком заново — отбор, страница и порядок уже в состоянии. */
  function reload(table){
    if (table === 'transfers'){
      if (typeof PORTAL.reloadTransfers === 'function') PORTAL.reloadTransfers();
    } else if (table === 'stock'){
      if (typeof PORTAL.reloadStock === 'function') PORTAL.reloadStock();
    } else if (table === 'sku'){
      if (typeof PORTAL.reloadProducts === 'function') PORTAL.reloadProducts();
    } else if (table === 'packaging'){
      if (typeof PORTAL.reloadPackaging === 'function') PORTAL.reloadPackaging();
    } else if (typeof PORTAL.reloadDocs === 'function'){
      PORTAL.reloadDocs(table);
    }
  }

  /** Списки, за страницы и порядок которых отвечает сервер. */
  const SERVER_PAGED = ['receipts', 'orders', 'transfers', 'stock', 'sku', 'packaging'];

  /**
   * Щелчок по заголовку колонки идёт на сервер.
   *
   * <p>Прототип на такой щелчок перерисовывал таблицу: сортировать
   * было что — весь список лежал на странице. Теперь на странице
   * лежат двадцать строк из ста тридцати, и перерисовка упорядочила
   * бы ровно их. Выглядит это как работающая сортировка — и это
   * худший вид поломки: человек видит порядок и верит ему.
   *
   * <p>Списки без страниц на сервере (поставщики, грузополучатели)
   * сортируются по-прежнему в браузере: они лежат на странице целиком,
   * и сортировать там действительно есть что.
   */
  function interceptSort(){
    if (typeof window.onSortColumnClick !== 'function' || window.onSortColumnClick.__server){
      return;
    }
    const previous = window.onSortColumnClick;
    const wrapper = function(table, columnId){
      if (SERVER_PAGED.indexOf(table) < 0) return previous(table, columnId);
      const st = typeof sortState !== 'undefined' ? sortState[table] : null;
      if (!st) return previous(table, columnId);
      if (st.col === columnId){ st.dir = -st.dir; } else { st.col = columnId; st.dir = 1; }
      // Порядок сменился — начинаем с первой страницы: остаться
      // на пятой означало бы показать середину нового порядка.
      // Стрелку в заголовке рисует renderTableHead при перерисовке
      // списка — она читает тот же sortState.
      apply(table);
      return undefined;
    };
    wrapper.__server = true;
    window.onSortColumnClick = wrapper;
  }

  /**
   * «Очистить фильтры»: пусто везде.
   *
   * <p>Прежде поля дат при очистке заполнялись заново диапазоном
   * по умолчанию — с первого числа прошлого месяца по сегодня.
   * Диапазон стоял не ради человека, а ради размера списка: сервер
   * отдавал весь отбор целиком, и без него экран получал бы всё.
   * Страницы считает сервер, список больше не распухает — а молча
   * спрятанные документы старше полутора месяцев остались бы
   * поломкой, которую человек объясняет себе сам.
   */
  function clear(table){
    const panel = panelOf(table);
    // Флажок очищается снятием отметки, а не пустым значением:
    // у checkbox свойство value — это то, что уйдёт на сервер при
    // отметке, и обнуление его отметку не снимает. Ровно на этом
    // «Очистить» оставляла включённым «Только брак», причём молча:
    // на панели галочка видна, а что она и есть причина короткого
    // списка — уже нет.
    if (panel) panel.querySelectorAll('.fgroup input, .fgroup select')
        .forEach(el => {
          if (el.type === 'checkbox') el.checked = false;
          else el.value = '';
        });
    apply(table);
  }

  /* ---------------- окно ⚙ Фильтры ---------------- */

  // Черновик выбора: пока окно открыто, правки живут здесь и на панель
  // не влияют. Иначе каждая галочка перестраивала бы панель и дёргала
  // сервер — а человек ещё не решил.
  let picker = null;

  function overlay(){
    let box = document.getElementById('filterPickerOverlay');
    if (box) return box;
    // Разметка своя, а не общая с ⚙ Колонками: окна похожи, но
    // состояние у них разное, и одно на двоих рано или поздно
    // приведёт к тому, что одно окно закроет другое.
    box = document.createElement('div');
    box.className = 'col-overlay';
    box.id = 'filterPickerOverlay';
    box.innerHTML =
        '<div class="col-panel">'
      + '<h3 id="filterPickerTitle"></h3>'
      + '<div class="hint" id="filterPickerHint"></div>'
      + '<div id="filterPickerList" class="col-list"></div>'
      + '<div class="col-actions">'
      + '<button class="btn primary" id="filterPickerApply" style="flex:1;"></button>'
      + '<button class="btn ghost" id="filterPickerReset"></button>'
      + '</div></div>';
    document.body.appendChild(box);
    box.onclick = e => { if (e.target === box) closePicker(); };
    document.getElementById('filterPickerApply').onclick = applyPicker;
    document.getElementById('filterPickerReset').onclick = resetPicker;
    return box;
  }

  function openPicker(table){
    const rows = catalogue[table] || [];
    if (!rows.length){
      say(T('partners.text.data_unavailable'));
      return;
    }
    // Порядок черновика: сначала выбранные в их порядке, потом
    // остальные. Так человек видит свою панель сверху, а не ищет
    // галочки среди тридцати строк.
    picker = {
      table: table,
      order: rows.filter(f => f.isChosen).map(f => f.columnId)
          .concat(rows.filter(f => !f.isChosen).map(f => f.columnId)),
      chosen: new Set(rows.filter(f => f.isChosen).map(f => f.columnId))
    };
    const box = overlay();
    document.getElementById('filterPickerTitle').textContent = T('common.title.filter_settings');
    document.getElementById('filterPickerHint').textContent = T('common.hint.filter_settings');
    document.getElementById('filterPickerApply').textContent = T('common.button.apply');
    document.getElementById('filterPickerReset').textContent = T('common.button.reset');
    renderPicker();
    box.classList.add('active');
  }

  function closePicker(){
    const box = document.getElementById('filterPickerOverlay');
    if (box) box.classList.remove('active');
    picker = null;
  }

  function renderPicker(){
    if (!picker) return;
    const rows = catalogue[picker.table] || [];
    const list = document.getElementById('filterPickerList');
    list.innerHTML = '';
    picker.order.forEach((columnId, idx) => {
      const f = rows.find(r => r.columnId === columnId);
      if (!f) return;
      const item = document.createElement('div');
      item.className = 'col-item';
      item.draggable = true;
      item.dataset.idx = String(idx);
      const handle = document.createElement('span');
      handle.className = 'handle';
      handle.textContent = '⠿';
      const cap = document.createElement('label');
      const cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.checked = picker.chosen.has(columnId);
      cb.onchange = () => {
        if (cb.checked) picker.chosen.add(columnId);
        else picker.chosen.delete(columnId);
      };
      cap.appendChild(cb);
      // Диапазон на панели — два поля, и в окне про это надо сказать:
      // иначе человек отметит одну строку и удивится двум клеткам.
      cap.appendChild(document.createTextNode(f.label
          + (ranged(f.filterKind)
              ? ' (' + T('common.label.range_from') + '…' + T('common.label.range_to') + ')'
              : '')));
      item.appendChild(handle);
      item.appendChild(cap);
      list.appendChild(item);
    });
    attachDrag(list);
  }

  function attachDrag(list){
    let from = null;
    list.querySelectorAll('.col-item').forEach(el => {
      el.addEventListener('dragstart', () => {
        from = +el.dataset.idx;
        el.classList.add('dragging');
      });
      el.addEventListener('dragend', () => el.classList.remove('dragging'));
      el.addEventListener('dragover', e => e.preventDefault());
      el.addEventListener('drop', e => {
        e.preventDefault();
        const to = +el.dataset.idx;
        if (from === null || from === to) return;
        picker.order.splice(to, 0, picker.order.splice(from, 1)[0]);
        renderPicker();
      });
    });
  }

  function applyPicker(){
    if (!picker) return;
    const table = picker.table;
    // Порядок — порядок окна, а не порядок справочника: человек
    // перетаскивал строки именно для этого.
    const chosen = picker.order.filter(id => picker.chosen.has(id));
    // Окно не закрывается: человеку надо вернуть галочку, а не искать
    // окно заново. Запрет есть и на сервере — здесь он для того, чтобы
    // человек узнал о нём до нажатия, а не после.
    if (!chosen.length){
      say(T('error.filter.at_least_one'));
      return;
    }
    closePicker();
    api('PUT', '/api/list-filters/' + encodeURIComponent(table), {columns: chosen})
      .then(rows => {
        catalogue[table] = rows;
        build(table);
        apply(table);
        notify(T('common.message.filter_settings_applied'));
      })
      .catch(err => say(T('common.message.filters_shown_but_not_saved') + ': ' + err.message));
  }

  function resetPicker(){
    if (!picker) return;
    const table = picker.table;
    closePicker();
    api('DELETE', '/api/list-filters/' + encodeURIComponent(table))
      .then(rows => {
        catalogue[table] = rows;
        build(table);
        apply(table);
        say(T('common.message.filters_reset_to_default'));
      })
      .catch(err => say(err.message));
  }

  /* ---------------- загрузка справочника ---------------- */

  async function loadCatalogue(table){
    try {
      catalogue[table] = await PORTAL.fetchJson('/api/list-filters/' + table);
    } catch (e){
      // Без справочника панель не нарисовать. Молчать нельзя: человек
      // увидел бы список без фильтров и решил, что их тут и не бывает.
      catalogue[table] = [];
      console.warn('Справочник фильтров ' + table + ': ' + e.message);
    }
    build(table);
  }

  /**
   * Справочники всех списков — одним заходом.
   *
   * <p>Обещание складывается в PORTAL.filtersReady, и списки документов
   * его дожидаются. Иначе порядок выходил такой: подписчики входа
   * прогоняются один за другим, не дожидаясь друг друга, и список
   * успевал уйти на сервер раньше, чем появилась панель, — то есть
   * без отбора и без диапазона дат по умолчанию. Человек видел список
   * за все годы и заполненные поля даты, которые ни на что не влияли.
   *
   * <p>Обещание живёт с самой загрузки файла, а не появляется
   * при входе. Раньше оно появлялось только в первом вызове
   * loadAll — и тот, кто подписался на вход РАНЬШЕ панели
   * (экран запасов: core.js подключается до filters.js), читал
   * undefined, ждать было нечего и список уходил на сервер без
   * отбора. Порядок подключения файлов — плохая опора: его меняют,
   * не думая о фильтрах.
   */
  let filtersLoaded;
  PORTAL.filtersReady = new Promise(resolve => { filtersLoaded = resolve; });

  function loadAll(){
    const done = Promise.all(TABLES.map(loadCatalogue));
    PORTAL.filtersReady = done;
    // Первое ожидание разрешаем в любом случае: не дождавшийся
    // панели экран должен открыться хотя бы без отбора, а не
    // остаться пустым навсегда.
    done.then(() => filtersLoaded(), () => filtersLoaded());
    return done;
  }

  function install(){
    // Прототип объявлял свои обработчики для каждого экрана; они
    // отбирали строки в браузере и обращались к полям, которых
    // в разметке больше нет. Подменяем их целиком — оставить
    // половину значило бы получить панель, часть которой работает.
    window.onReceiptsFilterChange = () => apply('receipts');
    window.onOrdersFilterChange = () => apply('orders');
    window.onTransfersFilterChange = () => apply('transfers');
    window.onStockFilterChange = () => apply('stock');
    window.onSkuFilterChange = () => apply('sku');
    window.onPackagingFilterChange = () => apply('packaging');
    window.clearReceiptsFilters = () => clear('receipts');
    window.clearOrdersFilters = () => clear('orders');
    window.clearTransfersFilters = () => clear('transfers');
    window.clearStockFilters = () => clear('stock');
    window.clearSkuFilters = () => clear('sku');
    window.clearPackagingFilters = () => clear('packaging');
    window.openFilterPicker = openPicker;
    window.closeFilterPicker = closePicker;
    interceptSort();

    // При смене языка панель собирается заново: подписи фильтров —
    // это подписи колонок, и приходят они с сервера.
    PORTAL.rebuildFilters = loadAll;

    PORTAL.onLogin(loadAll);
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
