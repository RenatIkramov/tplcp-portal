package com.ahlers.portal3pl.common;

/**
 * Коды служебных перечислений (V90), от которых зависит поведение портала.
 *
 * <p>Имена значений живут в базе и переводятся там же ({@code enum_name});
 * здесь только коды, которые программа пишет и сравнивает. Прежде на их
 * месте стояли английские слова — {@code portal}, {@code sent},
 * {@code checked}, {@code portal_to_wms}: слово не переведёшь, и у WMS
 * оно может оказаться другим.
 *
 * <p>Статусы документов — отдельно, {@code documents.DocumentStatus}.
 */
public final class EnumCodes {

    private EnumCodes() {
    }

    /** Где создан документ: {@code enum_document_origin}. */
    public static final class Origin {
        /** Создан на портале — пока черновик, он принадлежит порталу (Р5). */
        public static final String PORTAL = "01";
        /** Создан в WMS и пришёл синхронизацией. */
        public static final String WMS = "02";

        private Origin() {
        }
    }

    /** Передача транспорта в WMS: {@code enum_transport_state}. */
    public static final class Transport {
        public static final String SENT = "01";
        public static final String NOT_SENT = "02";

        private Transport() {
        }
    }

    /** Статус загрузки Excel: {@code enum_import_status}. */
    public static final class ImportStatus {
        public static final String CHECKED = "01";
        public static final String APPLIED = "02";
        public static final String CANCELLED = "03";
        /** Заказ из Excel не создан: в файле ошибки (V96). */
        public static final String REJECTED = "04";

        private ImportStatus() {
        }
    }

    /** Что загружали из Excel: {@code enum_import_kind}. */
    public static final class ImportKind {
        public static final String ITEMS = "01";
        public static final String PACKAGING = "02";
        public static final String ORDER = "03";
        public static final String RECEIPT = "04";
        public static final String TRANSFER = "05";

        private ImportKind() {
        }

        /**
         * Код по имени раздела, которым загрузку зовёт программа
         * ({@code sku}, {@code packaging}): то же имя служит ключом
         * таблицы колонок и права на загрузку, поэтому в коде остаётся.
         */
        public static String of(String section) {
            return switch (section) {
                case "sku" -> ITEMS;
                case "packaging" -> PACKAGING;
                case "order" -> ORDER;
                case "receipt" -> RECEIPT;
                case "transfer" -> TRANSFER;
                default -> throw new IllegalArgumentException("неизвестная загрузка: " + section);
            };
        }
    }

    /** Статус обмена с WMS: {@code enum_sync_status}. */
    public static final class SyncStatus {
        public static final String SUCCESS = "01";
        public static final String FAILED = "02";
        public static final String RETRY_SCHEDULED = "03";

        private SyncStatus() {
        }
    }

    /** Направление обмена с WMS: {@code enum_sync_direction}. */
    public static final class SyncDirection {
        public static final String PORTAL_TO_WMS = "01";
        public static final String WMS_TO_PORTAL = "02";

        private SyncDirection() {
        }
    }

    /** Статус пользователя: {@code enum_user_status}, выводится из признаков. */
    public static final class UserStatus {
        public static final String ACTIVE = "01";
        public static final String AWAITING_PASSWORD_CHANGE = "02";
        public static final String BLOCKED = "03";

        private UserStatus() {
        }
    }
}
