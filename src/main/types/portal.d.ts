// Типы фронтенда портала — только для проверки (tsc, IntelliJ IDEA).
// Браузер этот файл не получает: он лежит вне static/.
//
// Правило: тип описывает то, что сервер отдаёт на самом деле, и
// поправляется вместе с ним. Тип, который расходится с сервером, хуже
// отсутствующего — проверка начинает ругаться на правильный код.
// Где поле может прийти пустым (null из базы), так и написано.
//
// Проверяются только файлы с первой строкой `// @ts-check`
// (см. tsconfig.json в корне проекта).

export {};

declare global {

  /* ===================== DOM ===================== */

  /**
   * Элемент по id — без проверки вида и наличия (решение 08.11.2026).
   *
   * Строго это значило бы прописать у каждого из ~900 обращений, что
   * это за элемент (поле, кнопка, список), и проверку на пустоту —
   * сотни правок в коде демо ради ошибок, которых на деле не бывает:
   * разметку и код пишем мы сами. Всё остальное проверяется строго.
   * Где элемент действительно может отсутствовать, код проверяет это
   * сам, как и раньше.
   */
  interface Document {
    getElementById(elementId: string): any;
  }

  /**
   * То же для поиска по селектору (querySelector/querySelectorAll):
   * элементы ищутся в нашей же разметке, а вид элемента по строке
   * селектора проверка всё равно не узнает.
   */
  interface ParentNode {
    querySelector(selectors: string): any;
    querySelectorAll(selectors: string): NodeListOf<any>;
  }

  /* ===================== Глобальные функции ===================== */

  /** Перевод ключа текста на язык пользователя (i18n-fallback.js, i18n.js). */
  function T(key: string, params?: Record<string, unknown>): string;

  /** Общий объект портала; он же window.PORTAL (core.js). */
  var PORTAL: Portal;

  /* ===================== Ответы сервера ===================== */

  /** Поклажедатель, доступный вошедшему (GET /api/auth/session). */
  interface SessionDepositor {
    depositorId: string;
    code: string;
    orgName: string;
    /** номер договора (V108); нет — null */
    contractNumber: string | null;
  }

  /** Сеанс (AuthController.sessionState). */
  interface PortalSession {
    login: string;
    fullName: string | null;
    roleCode: string;
    locale: string | null;
    mustChangePassword: boolean;
    depositors: SessionDepositor[];
    depositorId: string | null;
    needsDepositorChoice: boolean;
  }

  /** Замечание к файлу документа (DocumentImportService.Issue). */
  interface DocImportIssue {
    /** header | Goods | transport | file */
    table: string;
    /** строка листа; 0 — замечание ко всему файлу */
    row: number;
    key: string | null;
    field: string | null;
    /** уже на языке читающего */
    message: string;
    value: string | null;
    messageKey: string | null;
    messageParams: Record<string, unknown> | null;
  }

  /** Ответ загрузки документа из Excel (POST /api/{orders|receipts|transfers}/excel-import). */
  type DocImportAnswer =
    | {ok: true; docId: string; documentNo: string; lineCount: number; expandedKits?: string[]}
    | {ok: false; issues: DocImportIssue[]};

  /** Загрузка в журнале (dbo.admin_excel_imports). */
  interface ImportLogEntry {
    importId: string;
    startedAt: string;
    depositorCode: string | null;
    depositorName: string | null;
    login: string | null;
    fullName: string | null;
    fileName: string | null;
    rowCount: number | null;
    createdRows: number | null;
    updatedRows: number | null;
    errorCount: number | null;
    status: string;
    importKind: string;
    statusName: string | null;
    importKindName: string | null;
    /** номер черновика, созданного загрузкой документа (V96) */
    docNumber: string | null;
  }

  /** Замечание в журнале (dbo.admin_excel_import_issues, ImportLogService). */
  interface ImportLogIssue {
    importId: string;
    startedAt: string;
    depositorCode: string | null;
    depositorName: string | null;
    login: string | null;
    fullName: string | null;
    fileName: string | null;
    rowNumber: number | null;
    rowClass: string | null;
    kind: string | null;
    columnId: string | null;
    /** на языке администратора, если у замечания есть ключ (V98) */
    message: string;
    fileValue: string | null;
    portalValue: string | null;
    importKind: string;
    importKindName: string | null;
    messageKey: string | null;
  }

  /** Журнал загрузок (GET /api/admin/excel-imports). */
  interface ImportLogAnswer {
    imports: ImportLogEntry[];
    issues: ImportLogIssue[];
  }

  /* ===================== Экраны ===================== */

  /** Чем документ отличается на экране загрузки из Excel (doc-import.js). */
  interface DocImportScreen {
    importBtn: string;
    templateBtn: string;
    /** адрес загрузки */
    api: string;
    /** функция скачивания шаблона из excel.js */
    download: 'portalDownloadOrderTemplate' | 'portalDownloadReceiptTemplate' | 'portalDownloadTransferTemplate';
    /** право пользователя на создание документа (поле ответа /api/me) */
    right: 'canCreateOrders' | 'canCreateReceipts' | 'canCreateTransfers';
    /** ключи текстов */
    title: string;
    imported: string;
    header: string;
  }

  /** Вошедший — в том виде, в каком его держит экран (auth.js, uiUser). */
  interface PortalUiUser {
    role: string; login: string; fullName: string | null;
    position: string; organization: string; phone: string; lastLogin: string;
    /** строка режима под шапкой: «Организация: …», «Режим администратора…» */
    modeLine: string; hiddenTabs: string[]; hideServicesTab: boolean;
    canCreateReceipts: boolean; canCreateOrders: boolean; canCreateTransfers: boolean;
    canUploadSkuExcel: boolean; canUploadPackagingExcel: boolean;
  }

  /* ===================== window.PORTAL ===================== */

  /**
   * Общий объект портала (core.js и модули).
   *
   * Точно описано то, чем пользуются проверяемые файлы; остальное —
   * `any`, пока до этих модулей не дошла очередь. Неописанного члена
   * здесь нет совсем: опечатка в имени — ошибка проверки.
   */
  interface Portal {
    /** Запрос с разбором ответа; отказ — исключение с готовым сообщением. */
    fetchJson(url: string, options?: RequestInit): Promise<any>;
    /** Скачать файл с портала; имя — из Content-Disposition (core.js). Отказ — Error с сообщением. */
    downloadFile(url: string, fallbackName: string): Promise<string>;
    /** «История» карточек и подписи журнала действий (history.js, V118). */
    history: {
      value(v: unknown): string;
      partText(part: string | null | undefined): string;
      title(ev: {action: string, part: string | null}, createdKey: string): string;
      html(rows: any[], createdKey?: string): string;
      show(host: HTMLElement, url: string,
           opts?: {createdKey?: string, stillWanted?: () => boolean}): Promise<void>;
      url(objectType: string, id: string): string;
    };
    /** Запрос с телом JSON (core.js); отказ — PortalHttpError. */
    api(method: string, path: string, body?: unknown, headers?: Record<string, string>): Promise<any>;
    /** Сообщение для человека из неудачного ответа fetch. */
    responseMessage(resp: Response): Promise<string>;
    errorText(status: number, data?: {message?: string; detail?: string} | null): string;
    /** Подпись с ключом в разметке: перерисуется при смене языка. */
    t(key: string): string;
    /** Текст для вставки в разметку: & < > " ' экранированы (аудит S1). */
    esc(v: unknown): string;
    /** Пустое значение — прочерк (аудит A4). */
    dash<T>(v: T): T | string;
    /** Плашка с ответом портала. */
    say(m: string): void;
    /** Текст ошибки для человека. */
    messageOf(e: unknown): string;
    /** Текст интерфейса в разметку: только b, strong, i, em, br, ссылка mailto/https (аудит S5). */
    safeHtml(v: unknown): string;
    /**
     * Атрибуты вызова по щелчку: data-call и data-args. Данные в текст
     * скрипта не попадают (аудит S1); вызывает общий обработчик core.js.
     */
    call(fn: string, ...args: unknown[]): string;
    /** То же, но вызов по mousedown — для списков, что закрываются по blur. */
    callOnPress(fn: string, ...args: unknown[]): string;
    /** То же по change (аудит A3). */
    callOnChange(fn: string, ...args: unknown[]): string;
    /** Аргумент-метка: подставить состояние флажка в момент вызова. */
    CHECKED: string;
    /** Переходы кнопок «Назад» (аудит A3). */
    backToLastView(): void;
    backToLastTab(): void;
    openReferencesSub(sub: string): void;
    openAdminSub(sub: string): void;
    /** Метка: подставить значение поля. */
    VALUE: string;
    /** Метка: подставить сам элемент. */
    SELF: string;
    /** То же по input (аудит A3). */
    callOnInput(fn: string, ...args: unknown[]): string;
    /** То же для готового элемента. */
    label<E extends Element | null>(el: E, key: string): E;
    onLogin(fn: () => void): void;
    onSession(fn: () => unknown): void;
    session: PortalSession | null;
    /** подписчики onLogin: данные выбранного клиента */
    loaders: Array<() => void>;
    /** подписчики onSession: то, что зависит от учётной записи */
    sessionLoaders: Array<() => unknown>;
    reloadAll(): void;
    reloadSession(): Promise<unknown>;
    isAdmin(): boolean;
    /** Вошедший для экрана (auth.js); null — до входа. */
    uiUser?: PortalUiUser | null;
    /** Скрыт ли вошедшему раздел меню (transfers, billing): экран туда не ходит. */
    sectionHidden(tab: string): boolean;
    /** Используются ли у клиента комплекты (core.js, V155). */
    kitsEnabled(): boolean;
    /** Последний открытый раздел меню (core.js) — к нему ведут «Назад». */
    lastView: string;
    /** Перед переходом в раздел; false — переход отменён (core.js). */
    beforeTab(fn: (tab: string) => boolean | void): void;
    /** После того как человек открыл подвкладку администрирования (core.js). */
    onAdminSub(fn: (sub: string) => void): void;
    /** Показать панель подвкладки администрирования, не считая это выбором человека. */
    showAdminPane(sub: string): void;
    /** Права на кнопки загрузки документов (doc-import.js); зовёт auth.js. */
    applyImportRights?: (user: PortalUiUser | null) => void;
    depositorCode: string;
    depositorName: string;
    /** запасы (core.js): когда загружены и чем кончилось */
    loadedAt: Date | null;
    error: string | null;
    loading: boolean;
    rows: any[];
    rowsTotal: number;
    timeLocale(): string;
    dateTimeText(iso: string | null | undefined): string;
    localDay(iso: string | null | undefined): string;
    reloadDocs?: (type: string) => unknown;

    /** Карточка документа (doc-card.js). */
    card: DocCard;
    /** Описания колонок таблиц (table-columns.js). */
    tableDefs: Record<string, {keyField?: string; columns: TableColumn[]}>;
    /** Таблицы: колонки, списки, сортировка, страницы, ⚙ Колонки (tables.js). */
    tables: PortalTables;
    /** Окно ввода строки (line-modal.js). */
    lineModal: LineModal;
    /** Черновики (draft.js); нет — модуль не загрузился. */
    draft?: DraftApi;
    /** Карточка товара (sku-card.js): просмотр, правка, упаковки. */
    skuCard: {
      state: {mode: string; skuId: string | null; row: CardRow; tab: string};
      create(): Promise<void>; edit(): void; cancel(): void; save(): Promise<void>;
      archive(restore: boolean): Promise<void>; remove(): Promise<void>;
      tab(id: string): void; field(el: HTMLInputElement | HTMLSelectElement): void;
      packagingAdd(): void; packagingEdit(id: string): void; packagingCancel(): void;
      packagingSave(): Promise<void>; packagingDefault(id: string): Promise<void>;
      packagingDelete(id: string): Promise<void>; packagingField(el: HTMLInputElement | HTMLSelectElement): void;
      kitEdit(): void; kitField(i: number, name: 'code' | 'qty', value: string): void;
      kitAdd(): void; kitRemove(i: number): void; kitCancel(): void; kitSave(): Promise<void>;
      openKit(skuId: string, editNow: boolean): Promise<void>;
    };
    /** «Справочники → Комплекты» (kits.js, V154); нет — модуль не загрузился. */
    kits?: {
      load(): Promise<void>; open(skuId: string): Promise<void>;
      startAdd(): void; cancelAdd(): void; pick(): void;
    };
    /** Настройки клиента — вкладки карточки поклажедателя (dep-visibility.js, V111). */
    depositorSettings?: {
      TABS: {id: string; key: string}[];
      show(depositorId: string, tab: string, host: HTMLElement): Promise<void>;
      leave(): void;
      forget(): void;
    };
    /** Отчёты клиента: «Отчёты», «Биллинг», карточка товара (reports.js, V138). */
    clientReports?: Record<string, (...args: any[]) => any> & {
      show(host: HTMLElement, where: {place: string, skuId?: string | null}): void;
    };
    /** Каталог отчётов: «Настройка отчётов» и окно отчёта (report-catalog.js, V135). */
    reportCatalog?: Record<string, (...args: any[]) => any>;
    /** Администрирование → «Интеграция» (integration.js, V143). */
    integration?: Record<string, (...args: any[]) => any>;
    /** Вкладка «Прикреплённые файлы» документа (doc-files.js, V145). */
    docFiles?: Record<string, (...args: any[]) => any>;
    /** Вкладка «Отчёты» карточки поклажедателя (report-catalog.js, V135). */
    depositorReports?: {
      TAB: {id: string; key: string};
      show(depositorId: string, host: HTMLElement): Promise<void>;
      leave(): void;
      forget(): void;
    };
    /** Карточка остатка из «Запасов» (stock-card.js). */
    stockCard: {renderBatches(): void};

    /** Последний ответ загрузки документа (doc-import.js). */
    docImport: {last: DocImportAnswer | null; type: string | null};
    /** Состояние журнала загрузок (import-log.js). */
    importLog: {imports: ImportLogEntry[]; issues: ImportLogIssue[]; depositorId: string; loaded: boolean};

    // ---- ещё не описано ----
    STATUS: any; TRANSPORT: any; adminTabs: any; catalog: any; catalogError: any;
    columns: any; contacts: any;
    docs: any; docsError: any; docsLoadedAt: any; docsTotal: any; draftFields: any;
    enumValues: any; fieldAllowed: any; fieldLabel: any;
    fieldLabels: any; fieldRename: any; filterQuery: any; filtersReady: any; forgetAll: any;
    i18n: any; languages: any; listQuery: any;
    mountLanguageSwitch: any; offerToSavePassword: any;
    page: any; pageTotal: any; portalDirLists: any;
    qualities: any; rebuildFilters: any; refreshPasswordDialog: any; refreshSettings: any;
    reloadDirectories: any; reloadPackaging: any; reloadProducts: any;
    reloadStock: any; reloadTransfers: any; resetLocale: any;
    setBack: any;
    statusBadge: any; user: any; warehouse: any;
    warehouseName: any; warehouses: any; yesNo: any;
  }

  /** Колонка таблицы (table-columns.js). Подпись — ключ col.<таблица>.<id>. */
  interface TableColumn {
    id: string;
    /** подпись справочника колонок — для колонок, не описанных в table-columns.js */
    label?: string;
    num?: boolean;
    wrap?: boolean;
    /** по умолчанию скрыта */
    off?: boolean;
    field?: string;
    render: (row: any) => string;
  }

  /** Таблицы портала (tables.js). */
  interface PortalTables {
    register(key: string, handlers: {load?: () => unknown; render?: () => unknown}): void;
    columns(key: string): TableColumn[];
    visible(key: string): TableColumn[];
    label(key: string, column: TableColumn): string;
    head(key: string): void;
    row(key: string, item: any, open?: string): string;
    renderList(key: string, data: {rows: any[]; total: number; error?: string | null; open?: string}): void;
    redraw(key: string): void;
    redrawAll(): void;
    sort(key: string, columnId: string): unknown;
    page(key: string, delta: number): unknown;
    pageSize(key: string, size: number | string): unknown;
    pageOf(key: string): {page: number; pageSize: number};
    sortOf(key: string): {col: string | null; dir: number};
    firstPage(key: string): void;
    setPage(key: string, page: number | null | undefined): void;
    picker(key: string): void;
    pickerClose(): void;
    pickerToggle(id: string): void;
    pickerApply(): void;
    pickerReset(): void;
    updateScroll(key: string): void;
    dirSort(key: string, columnId: string): void;
    dirSorted<R>(key: string, rows: R[]): R[];
    dirRegister(key: string, render: () => unknown): void;
  }

  /** Карточка документа (doc-card.js). Документ и строки — в терминах экрана (draft.js toDocument). */
  interface DocCard {
    TYPES: Record<string, {view: string; field: string; title: string; contents: string;
                           qty: string; confirmDelete: string}>;
    /** Код качества «Годный». */
    GOOD: string;
    show(type: string, doc: any, keepTab?: boolean): void;
    render(type: string): void;
    doc(type: string): any;
    lines(type: string): any[];
    setLines(type: string, lines: any[]): void;
    forget(type: string): void;
    activeTab(type: string): string;
    tab(type: string, tab: string): void;
    renderLines(type: string): void;
    renderLineDetail(type: string): void;
    openLine(type: string, index: number): void;
    closeLine(type: string): void;
    check(type: string, index: number, on: boolean): void;
    checkAll(type: string, on: boolean): void;
    deleteChecked(type: string): void;
    askDelete(type: string): void;
    closeAskDelete(type: string): void;
    extraFields(type: string): string[];
    quality(line: any): string;
    qualityName(line: any): string;
  }

  /** Окно ввода строки документа (line-modal.js). */
  interface LineModal {
    add(type: string): void;
    edit(type: string, index: number): void;
    close(type: string): void;
    save(type: string): void;
    search(type: string, query: string): void;
    pick(type: string, code: string): void;
    clearSearch(type: string): void;
    packagingChanged(type: string): void;
    packageCountChanged(type: string): void;
    priceChanged(type: string): void;
    qtyInput(type: string): void;
    qualityChanged(type: string): void;
    resolveDuplicate(action: 'add' | 'replace' | 'cancel'): void;
    product(type: string): any;
    forgetStock(): void;
  }

  /** Черновики документов: справочники, чтение и запись через API (draft.js). */
  interface DraftApi {
    refs: {loaded: boolean; warehouses: SelectOption[]; suppliers: SelectOption[];
           consignees: SelectOption[]; skuByCode: Record<string, SkuRow>; packagings: PackagingRow[];
           orderTypes: SelectOption[]; docTypes: SelectOption[]; transportTypes?: SelectOption[];
           skuList?: any[], kits?: Record<string, any[]>};
    me: {canEditTransport: boolean; priceInReceipts: boolean; priceInOrders: boolean;
         loaded: boolean; roleCode?: string};
    loadDirectories(): Promise<unknown>;
    open(type: string, docId: string): Promise<unknown>;
    create(type: string): Promise<unknown>;
    save(type: string): Promise<unknown>;
    confirm(type: string): Promise<void>;
    /** Окно нехватки при подтверждении (V142): спросить WMS и подтвердить заново; закрыть. */
    checkStock(): Promise<void>;
    closeShortage(): void;
    /** «Отправить ещё раз» у документа с ошибкой отправки (V127). */
    resend(type: string): Promise<void>;
    remove(type: string): Promise<void>;
    saveTransport(type: string): Promise<void>;
    resendTransport(type: string): Promise<void>;
    transportEditable(doc: any): boolean;
    transportSavedSeparately(doc: any): boolean;
    transportStateHtml(type: string, doc: any): string;
    keepUnsaved(type: string): void;
    linesChanged(type: string): void;
    addressOptions(consigneeId: string): SelectOption[];
    consigneeChanged(): void;
    addressChanged(): void;
  }

  /** Кнопка, которая на время запроса помечается занятой. */
  type BusyButton = HTMLButtonElement | null | undefined;

  interface Window {
    /** Менеджер паролей браузера (Credential Management API) — в стандартных типах его нет. */
    PasswordCredential?: new (data: {id: string; password: string; name?: string}) => Credential;

    // Скачивание шаблонов Excel (excel.js)
    portalDownloadSkuTemplate(btn?: BusyButton): Promise<void>;
    portalDownloadPackagingTemplate(btn?: BusyButton): Promise<void>;
    portalDownloadOrderTemplate(btn?: BusyButton): Promise<void>;
    portalDownloadReceiptTemplate(btn?: BusyButton): Promise<void>;
    portalDownloadTransferTemplate(btn?: BusyButton): Promise<void>;

  }

  /** Отметка «эту функцию уже обернули» — чтобы не обернуть дважды. */
  interface Function {
    __docImport?: boolean;
    __implog?: boolean;
    __server?: boolean;
    __groups?: boolean;
    __warehouse?: boolean;
  }
}
