// Функции, которые модули фронтенда кладут на window.
//
// Их зовут из разметки (onclick) и из других файлов, поэтому проверке
// надо знать, что они есть и сколько принимают аргументов. Типы
// аргументов пока не описаны (any) — описываются по мере надобности.
// Новая функция на window — новая строка здесь, иначе проверка скажет,
// что такого имени нет.

export {};

declare global {

  // portal/admin-tabs.js
  var showAdminGroup: (id: any) => any;

  // portal/admin.js
  var portalOpenPasswordChange: (mode?: any) => any;
  var portalOpenAdminUser: (userId: any) => Promise<any>;
  var portalCopyUserSettings: (toUserId: any) => Promise<any>;
  var renderAdminUsers: () => any;

  // portal/audit-log.js
  var renderAuditLog: () => any;

  // portal/report-runs.js
  var renderReportRuns: () => any;

  // portal/kits.js
  var renderKits: () => any;

  // portal/auth.js
  var doLogin: () => Promise<any>;
  var logout: () => Promise<any>;
  var renderDepositorSwitcher: () => any;
  var toggleDepositorSwitcher: () => any;
  var portalSelectDepositor: (id: any) => Promise<any>;

  // portal/catalog.js
  var renderBillingServices: () => any;
  var renderBillingInvoices: () => any;

  // portal/contacts.js
  var openContacts: () => any;

  // portal/core.js
  var showView: (name: any) => any;
  var showTab: (tab: any) => any;
  var showReferencesSub: (sub: any) => any;
  var showAdminSub: (sub: any) => any;
  var notify: (msg: any) => any;
  var badge: (status: any, label: any) => any;
  var toISODate: (str: any) => any;
  var fromISODate: (iso: any) => any;

  // portal/docs.js
  var portalReloadDocuments: () => any;

  // portal/draft.js
  var portalOpenDocument: (no: any) => any;

  // portal/enum-values.js
  var renderEnumValues: () => any;
  var renderReportCatalog: () => any;
  var renderIntegration: () => any;
  var renderDocFiles: () => any;
  var renderClientReports: () => any;

  // portal/filters.js
  var onReceiptsFilterChange: () => any;
  var onOrdersFilterChange: () => any;
  var onTransfersFilterChange: () => any;
  var onStockFilterChange: () => any;
  var onSkuFilterChange: () => any;
  var onPackagingFilterChange: () => any;
  var clearReceiptsFilters: () => any;
  var clearOrdersFilters: () => any;
  var clearTransfersFilters: () => any;
  var clearStockFilters: () => any;
  var clearSkuFilters: () => any;
  var clearPackagingFilters: () => any;
  var openFilterPicker: (table: any) => any;

  // portal/i18n.js
  var portalSetLanguage: (code: any) => Promise<any>;
  var portalUiTextsRender: () => any;
  var portalUiTextSave: (el: any) => Promise<any>;

  // portal/partners.js
  var portalPartnerField: (el: any) => any;
  var portalAddressField: (el: any) => any;
  var portalPartnerTab: (tab: any) => any;
  var portalPartnerBack: () => any;
  var portalOpenPartner: (kindName: any, id: any) => Promise<any>;
  var portalPartnerCreate: (kindName: any) => any;
  var portalPartnerEdit: () => any;
  var portalPartnerCancel: () => any;
  var portalPartnerSave: () => Promise<any>;
  var portalPartnerArchive: (archived: any) => Promise<any>;
  var portalPartnerDelete: () => Promise<any>;
  var portalAddressAdd: () => any;
  var portalAddressEdit: (id: any) => any;
  var portalAddressCancel: () => any;
  var portalAddressSave: () => Promise<any>;
  var portalAddressDefault: (id: any) => Promise<any>;
  var portalAddressDelete: (id: any) => Promise<any>;
  var portalPartnerArchiveFilter: (kindName: any) => any;
  var renderSuppliers: () => any;
  var renderConsignees: () => any;

  // portal/portal-dir.js
  var portalDirField: (el: any) => any;
  var portalDirTab: (tab: any) => any;
  var portalDirBack: () => any;
  var portalOpenPortalDir: (dirName: any, id: any) => Promise<any>;
  var portalDirCreate: (dirName: any) => any;
  var portalDirEdit: () => any;
  var portalDirCancel: () => any;
  var portalDirSave: () => Promise<any>;
  var portalDirArchive: (archived: any) => Promise<any>;
  var portalDirDelete: () => Promise<any>;
  var portalDirArchiveFilter: (dirName: any) => any;
  var renderWarehousesDir: () => any;
  var renderDepositorsDir: () => any;

  // portal/profile.js
  var openProfile: () => any;
  var renderProfile: (user: any) => any;

  // portal/sku-card.js
  var portalOpenSku: (code: any) => any;

  // portal/stock-card.js
  var portalOpenStockRow: (code: any) => Promise<any>;

  // portal/warehouse-filter.js
  var toggleWarehouseSwitcher: () => any;
  var setWarehouseFilter: (id: any) => any;
}
