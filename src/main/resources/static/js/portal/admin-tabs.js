
/* =====================================================================
   Группы вкладок администрирования
   =====================================================================

   ЗАЧЕМ. Раздел администрирования рос вкладками по одной: доступы
   менеджеров, доступы поклажедателей, доступы сотрудников склада,
   ошибки интеграции, ошибки загрузки Excel, настройка отчётов, тексты
   интерфейса, организации оператора. Рядом, в «Справочниках», лежали
   справочники ВСЕГО портала — склады, пользователи, поклажедатели,
   типы заказов и поступлений, единицы измерения, языки, — и стояли
   они в одном ряду с товарами и контрагентами выбранного клиента.
   Ряд из тринадцати подписей порядка уже не держал: чтобы найти
   вкладку, её приходилось вычитывать.

   КАК УСТРОЕНО. Два ряда. Верхний — группы, нижний — вкладки
   выбранной группы. Группа из одной вкладки второго ряда не рисует:
   «Настройка отчётов» и «Тексты интерфейса» открываются сразу,
   как раньше.

   Состав рядов описан ЗДЕСЬ, одним списком, а не собирается из
   разметки. Прежде кнопки добавляли в ряд сами модули — организации
   оператора из portal-dir.js, тексты интерфейса из i18n.js, единицы
   и языки вообще в чужой раздел, — и порядок вкладок зависел от
   порядка загрузки файлов. Теперь модуль создаёт свою ПАНЕЛЬ
   и сообщает, чем её наполнить (`onOpen`); где стоять вкладке,
   решает этот список.

   ПОЧЕМУ ВКЛАДКА ПОЯВЛЯЕТСЯ НЕ СРАЗУ. Панели единиц измерения,
   языков и организаций оператора создаются кодом уже после входа.
   Поэтому ряд пересобирается на каждой записи в `onOpen`, а вкладка
   показывается только тогда, когда её панель существует: кнопка,
   ведущая в пустоту, хуже отсутствующей кнопки.
   ===================================================================== */

(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // Разработчику раздел открыт для разбора, как и прежде: он смотрит,
  // но не правит — это решают сами экраны, а не ряд вкладок.
  const isAdmin = () => ['admin', 'developer']
      .includes(PORTAL.session && PORTAL.session.roleCode);

  /** Группы и их состав; порядок списка — порядок на экране. */
  const GROUPS = [
    {id: 'directories', labelKey: 'admin.button.shared_directories',
     subs: ['operator', 'warehousesDir', 'users', 'depositorsDir',
            'units', 'orderTypes', 'docTypes', 'languages']},
    {id: 'accesses', labelKey: 'admin.button.accesses',
     subs: ['access', 'depositor-access', 'staff-access']},
    {id: 'errors', labelKey: 'admin.button.errors',
     subs: ['integration', 'excel']},
    {id: 'reports', labelKey: 'admin.button.report_settings',
     subs: ['reports-config']},
    {id: 'uitexts', labelKey: 'admin.button.interface_texts',
     subs: ['uitexts']}
  ];

  /**
   * Подписи вкладок.
   *
   * <p>Ключи те же, что были у кнопок в разметке: вкладка переехала
   * в другой ряд, но называется по-прежнему. «Доступы → Доступы
   * менеджеров» читается с повтором — зато найти вкладку по памяти
   * можно, а это важнее.
   */
  const LABELS = {
    'operator':         'admin.title.operator_organization',
    'warehousesDir':    'references.button.warehouses',
    'users':            'references.button.users',
    'depositorsDir':    'references.button.depositors',
    'units':            'references.button.units',
    'orderTypes':       'references.button.order_types',
    'docTypes':         'references.button.expected_document_types',
    'languages':        'references.button.languages',
    'access':           'admin.button.manager_access',
    'depositor-access': 'admin.button.depositor_access',
    'staff-access':     'admin.button.warehouse_staff_access',
    'integration':      'admin.button.integration_errors',
    'excel':            'admin.button.excel_import_errors',
    'reports-config':   'admin.button.report_settings',
    'uitexts':          'admin.button.interface_texts'
  };

  const state = {group: null, sub: null, chosen: false, openers: {}, last: {}};

  /** Прототипное переключение панелей — подменяется ниже обёрткой. */
  let base = null;

  /* ---------------------------- состав ---------------------------- */

  const paneExists = sub => !!document.getElementById('admin-sub-' + sub);

  const groupOf = sub => GROUPS.find(g => g.subs.indexOf(sub) >= 0) || null;

  const available = group => group.subs.filter(paneExists);

  const visibleGroups = () => GROUPS.filter(g => available(g).length > 0);

  /* ---------------------------- отрисовка ---------------------------- */

  function tab(labelKey, active, onclick, sub){
    const btn = document.createElement('button');
    btn.className = 'admin-subtab-btn' + (active ? ' active' : '');
    btn.type = 'button';
    if (sub) btn.setAttribute('data-sub', sub);
    // Подпись через PORTAL.label: она несёт ключ, и смена языка её найдёт.
    PORTAL.label(btn, labelKey);
    btn.onclick = onclick;
    return btn;
  }

  function build(){
    if (!isAdmin()) return;
    const top = document.getElementById('adminGroupTabs');
    const row = document.getElementById('adminSubTabs');
    if (!top || !row) return;

    const groups = visibleGroups();
    if (!groups.length) return;
    let group = groups.find(g => g.id === state.group);
    if (!group){
      group = groups[0];
      state.group = group.id;
    }

    top.innerHTML = '';
    groups.forEach(g => top.appendChild(
        tab(g.labelKey, g.id === group.id, () => openGroup(g.id), null)));

    const subs = available(group);
    row.innerHTML = '';
    // Группа из одной вкладки второго ряда не рисует: ряд из одной
    // кнопки, повторяющей название группы, ничего не сообщает.
    row.style.display = subs.length > 1 ? '' : 'none';
    if (subs.length > 1){
      subs.forEach(sub => row.appendChild(
          tab(LABELS[sub] || sub, sub === state.sub,
              () => window.showAdminSub(sub), sub)));
    }

    // Пока человек сам вкладку не выбирал, открыта первая вкладка
    // первой группы. Пересчитывается при каждой сборке: панели
    // единиц, языков и организаций оператора появляются позже входа,
    // и до их появления «первой» была бы другая вкладка.
    if (!state.chosen && subs.length && state.sub !== subs[0]){
      apply(subs[0]);
    }
  }

  /* ---------------------------- переключение ---------------------------- */

  function runOpener(sub){
    const open = state.openers[sub];
    if (typeof open !== 'function') return;
    try {
      open();
    } catch (e){
      // Вкладка открылась, наполнить не удалось: ронять переключение
      // из-за этого нельзя — человек останется на пустом экране
      // без возможности уйти.
      console.warn('вкладка ' + sub + ': ' + e.message);
    }
  }

  /** Открыть вкладку, не считая это выбором человека. */
  function apply(sub){
    const group = groupOf(sub);
    if (group) state.group = group.id;
    state.sub = sub;
    if (typeof base === 'function') base(sub);
    build();
    runOpener(sub);
  }

  /**
   * Щелчок по группе открывает ту вкладку, на которой человек в этой
   * группе остановился: вернуться к своему экрану чаще нужно, чем
   * к первому в ряду.
   */
  function openGroup(id){
    const group = GROUPS.find(g => g.id === id);
    if (!group) return;
    const subs = available(group);
    if (!subs.length) return;
    const want = subs.indexOf(state.last && state.last[id]) >= 0
        ? state.last[id] : subs[0];
    window.showAdminSub(want);
  }

  /* ---------------------------- установка ---------------------------- */

  PORTAL.adminTabs = {
    /**
     * Чем наполнить вкладку, когда её открыли.
     *
     * <p>Вызов заодно пересобирает ряд: модуль записывается сюда
     * сразу после того, как создал свою панель, и это единственный
     * надёжный признак, что вкладке есть что показать.
     */
    onOpen: function(sub, fn){
      state.openers[sub] = fn;
      build();
    },
    refresh: build,
    /** Открытая вкладка и группа — для проверок. */
    state: state
  };

  function install(){
    if (typeof window.showAdminSub !== 'function' || window.showAdminSub.__groups){
      return;
    }
    base = window.showAdminSub;
    const wrapper = function(sub){
      const group = groupOf(sub);
      if (group) state.last[group.id] = sub;
      state.chosen = true;
      apply(sub);
    };
    wrapper.__groups = true;
    window.showAdminSub = wrapper;
    window.showAdminGroup = openGroup;

    PORTAL.onSession(() => {
      if (!isAdmin()) return;
      // Новый вход — новый человек: выбор вкладки не наследуется.
      state.chosen = false;
      state.group = null;
      state.sub = null;
      state.last = {};
      build();
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
