// Ответы сервера, которые читает фронтенд.
//
// Правило то же, что в portal.d.ts: тип описывает то, что сервер отдаёт
// на самом деле, и правится вместе с ним. У каждого типа — откуда он
// (класс Java или функция базы), чтобы было что сверить.
// Поле, которое может прийти пустым (null из базы), так и описано.

export {};

declare global {

  /* ============ Перечисления (EnumValueService, /api/admin/enum-values) ============ */

  interface EnumDef {
    enumKey: string;
    textKey: string;
    hasSymbol: boolean;
    extraLabelKey: string | null;
    codeExample: string | null;
    extraExample: string | null;
  }

  interface EnumLocale {
    code: string;
    name: string;
  }

  interface EnumTranslation {
    name: string | null;
    symbol: string | null;
  }

  interface EnumValueRow {
    enumKey: string;
    valueId: string;
    code: string;
    name: string;
    extra: string | null;
    sortOrder: number | null;
    isActive: boolean | null;
    isSystem: boolean;
    /** сколько записей ссылается на значение */
    usage: number;
    /** переводы по кодам языков */
    values: Record<string, EnumTranslation>;
  }

  interface EnumValuesAnswer {
    enums: EnumDef[];
    locales: EnumLocale[];
    rows: EnumValueRow[];
  }

  /* ============ Пользователи (AdminUserService, /api/admin/users, roles, depositors) ============ */

  /** Строка справочника пользователей (dbo.admin_users). */
  interface AdminUser {
    userId: string;
    login: string;
    fullName: string | null;
    roleCode: string;
    roleName: string | null;
    userPosition: string | null;
    organization: string | null;
    phone: string | null;
    isActive: boolean | null;
    mustChangePassword: boolean | null;
    lastLoginAt: string | null;
    passwordChangedAt: string | null;
    /** «все поклажедатели» (V110); у роли depositor всегда false */
    allDepositors: boolean | null;
    /** «все склады» (V110) */
    allWarehouses: boolean | null;
    depositorCodes: string | null;
    depositorNames: string | null;
    warehouseNames: string | null;
    hideBillingTab: boolean | null;
    hideServicesTab: boolean | null;
    canCreateReceipts: boolean | null;
    canCreateOrders: boolean | null;
    canCreateTransfers: boolean | null;
    canUploadSkuExcel: boolean | null;
    canUploadPackagingExcel: boolean | null;
    canEditTransport: boolean | null;
    userStatusCode: string | null;
    userStatusName: string | null;
  }

  /** Роль (dbo.admin_roles). */
  interface AdminRole {
    code: string;
    name: string;
  }

  /** Поклажедатель для выбора администратором (dbo.admin_depositors). */
  interface AdminDepositor {
    depositorId: string;
    code: string;
    orgName: string;
  }

  /** Ошибка запроса с кодом ответа — её бросают помощники api() модулей. */
  interface PortalHttpError extends Error {
    status?: number;
    /** Ключ отказа сервера (PortalException) и его подстановки — если сервер их прислал. */
    key?: string | null;
    params?: Record<string, any> | null;
  }

  /* ============ Контакты (ContactsService, /api/contacts) ============ */

  /** (contact_operators) */
  interface ContactOperator {
    operatorId: string;
    code: string;
    name: string;
    bin: string | null;
    legalAddress: string | null;
    bankName: string | null;
    bankAccount: string | null;
    phone: string | null;
    email: string | null;
  }

  /** (v_warehouses) */
  interface WarehouseRow {
    warehouseId: string;
    code: string;
    name: string;
    address: string | null;
    responsible: string | null;
    phone: string | null;
    email: string | null;
    isActive: boolean | null;
    areaM2: number | null;
    workHours: string | null;
  }

  /** (contact_managers) */
  interface ContactManager {
    userId: string;
    fullName: string | null;
    jobTitle: string | null;
    phone: string | null;
    email: string | null;
    isYours: boolean | null;
  }

  interface ContactsAnswer {
    operators: ContactOperator[];
    warehouses: WarehouseRow[];
    managers: ContactManager[];
  }

  /* ============ Доступы (AdminAccessService, /api/admin/warehouses) ============ */

  /** (admin_warehouses) */
  interface AdminWarehouse {
    warehouseId: string;
    code: string;
    name: string;
    address: string | null;
  }

  /* ============ Языки и тексты (LocaleService) ============ */

  /** Язык для переключателя (GET /api/i18n/locales). */
  interface PortalLocale {
    code: string;
    name: string;
    isDefault: boolean | null;
  }

  /** Язык в администрировании (GET /api/admin/locales, v_locales_all) */
  interface LocaleAdminRow {
    localeId: number;
    code: string;
    name: string;
    isDefault: boolean | null;
    isActive: boolean | null;
    sortOrder: number | null;
    textCount: number | null;
  }

  /** Строка матрицы текстов (GET /api/admin/ui-texts, LocaleService.matrix). */
  interface UiTextRow {
    textKey: string;
    scope: string | null;
    element: string | null;
    owner: string | null;
    /** код языка → текст */
    values: Record<string, string | null>;
  }

  /* ============ Товары и упаковки (CatalogList: v_sku_full, v_product_packaging) ============ */

  /** Товар (GET /api/sku, /api/sku/options; v_sku_full) */
  interface SkuRow {
    skuId: string;
    depositorId: string;
    depositorName: string | null;
    itemCode: string | null;
    code: string;
    name: string;
    itemFullName: string | null;
    barcode: string | null;
    uomName: string | null;
    uomId: number | null;
    orderUnitName: string | null;
    orderUnitId: string | null;
    itemGroupName: string | null;
    itemTypeName: string | null;
    hasPackaging: boolean | null;
    length: number | null;
    width: number | null;
    height: number | null;
    volume: number | null;
    weightGross: number | null;
    weightNet: number | null;
    manufacturer: string | null;
    countryOrigin: string | null;
    brand: string | null;
    isFragile: boolean | null;
    isSerialTracked: boolean | null;
    batchTracking: boolean | null;
    expiryTracking: boolean | null;
    minStorageTemp: number | null;
    maxStorageTemp: number | null;
    minHumidity: number | null;
    maxHumidity: number | null;
    minDaysForReceipt: number | null;
    minDaysForReservation: number | null;
    shelfLifeDays: number | null;
    color: string | null;
    size: string | null;
    abcClass: string | null;
    xyzClass: string | null;
    hsCode: string | null;
    enstruCode: string | null;
    cost: number | null;
    currencyCode: string | null;
    currencyId: number | null;
    isKit: boolean | null;
    isMarkingRequired: boolean | null;
    isExcise: boolean | null;
    isControlled: boolean | null;
    isWmsActive: boolean | null;
    hazardClassName: string | null;
    hazardClassId: number | null;
    hazardGroupName: string | null;
    hazardGroupId: number | null;
    statusName: string | null;
    comment: string | null;
    isActive: boolean | null;
    isInWms: boolean | null;
    syncedAt: string | null;
    wmsVersion: string | null;
    createdBy: string | null;
    createdAt: string | null;
    updatedBy: string | null;
    updatedAt: string | null;
    customAttr1: string | null;
    customAttr2: string | null;
    customAttr3: string | null;
    customAttr4: string | null;
    customAttr5: string | null;
    customAttr6: string | null;
    customAttr7: string | null;
    customAttr8: string | null;
    customAttr9: string | null;
    customAttr10: string | null;
  }

  /** Упаковка (GET /api/packaging, /api/packaging/options; v_product_packaging) */
  interface PackagingRow {
    packagingId: string;
    depositorId: string;
    skuId: string;
    skuCode: string | null;
    skuName: string | null;
    uomName: string | null;
    packagingCode: string | null;
    packagingTypeId: number | null;
    packagingTypeName: string | null;
    barcode: string | null;
    coefficient: number | null;
    lengthCm: number | null;
    widthCm: number | null;
    heightCm: number | null;
    volumeM3: number | null;
    grossWeightKg: number | null;
    isDefaultOrderUnit: boolean | null;
  }

  /** Пункт выпадающего списка, собранный из справочника. */
  interface SelectOption {
    value: string | number;
    text: string;
  }

  /* ============ Контрагенты (PartnerWriteService) ============ */

  /** Адрес доставки грузополучателя (v_delivery_points) */
  interface DeliveryPoint {
    addressId: string;
    consigneeId: string | null;
    consigneeCode: string | null;
    consigneeName: string | null;
    address: string | null;
    isDefault: boolean | null;
    sortOrder: number | null;
    contact: string | null;
    orderContact: string | null;
  }

  /**
   * Строка справочника в общем редакторе карточек (контрагенты, справочники
   * портала): состав колонок задаёт описание справочника, а не тип.
   */
  type CardRow = Record<string, any>;

  /* ============ Видимость и подписи полей клиента (ColumnService) ============ */

  /** (admin_depositor_columns) */
  interface DepositorColumnRow {
    tableKey: string;
    columnId: string | null;
    label: string | null;
    portalLabel: string | null;
    groupKey: string | null;
    groupLabel: string | null;
    isAvailable: boolean | null;
    isDefault: boolean | null;
    isLocked: boolean | null;
    sortOrder: number | null;
  }

  /** (admin_field_labels) */
  interface FieldLabelRow {
    objectCode: string;
    objectName: string | null;
    fieldId: string | null;
    customLabel: string | null;
    shownIn: string | null;
  }

  /** (admin_field_catalog) */
  interface FieldCatalogRow {
    objectCode: string;
    objectName: string | null;
    fieldId: string | null;
    portalLabel: string | null;
    shownIn: string | null;
  }

  /** Галочки карточки клиента (DepositorSettingsService.settings). */
  interface DepositorSettings {
    depositorId?: string;
    hideTransfersTab: boolean;
    priceInReceipts: boolean;
    priceInOrders: boolean;
  }
}
