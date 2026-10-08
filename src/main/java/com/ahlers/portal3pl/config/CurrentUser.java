package com.ahlers.portal3pl.config;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Кто выполняет действие. Логин берётся из сессии (см. AuthFilter),
 * идентификатор — из базы по логину.
 * <p>
 * Для записи документов пользователь обязателен: поля «кто создал» и
 * «кто изменил» требует §11 спецификации, и ставить их наугад нельзя.
 */
@Component
public class CurrentUser {

    private final JdbcClient jdbc;

    public CurrentUser(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String requireUserId() {
        String login = DepositorContext.userLogin();
        if (login == null || login.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется вход в портал");
        }
        return jdbc.sql("SELECT user_id::text FROM dbo.app_user WHERE login = :login AND is_active")
                .param("login", login)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Пользователь не найден или заблокирован"));
    }

    /** Разрешено ли пользователю править транспортный раздел (решение Р5). */
    public boolean mayEditTransport() {
        String login = DepositorContext.userLogin();
        if (login == null || login.isBlank()) {
            return false;
        }
        // orElse(false) уже исключает null, поэтому обёртка Boolean.TRUE.equals
        // не нужна: распаковка значения здесь безопасна
        return jdbc.sql("""
                        SELECT COALESCE(s.can_edit_transport, true)
                        FROM dbo.app_user u
                        LEFT JOIN dbo.user_settings s ON s.user_id = u.user_id
                        WHERE u.login = :login AND u.is_active
                        """)
                .param("login", login)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    /**
     * Разрешена ли этому пользователю загрузка справочника из Excel (§6.3).
     *
     * <p>Проверяется на сервере, а не скрытием кнопки: §6.3 требует именно
     * этого. Шаблон тоже за этой проверкой — кому загружать нельзя, тому
     * и шаблон незачем, он только вводил бы в заблуждение.
     *
     * @param kind {@code sku} или {@code packaging}
     */
    public void requireExcelUpload(String kind) {
        String column = switch (kind) {
            case "sku" -> "can_upload_sku_excel";
            case "packaging" -> "can_upload_packaging_excel";
            default -> throw new IllegalArgumentException("Неизвестный справочник: " + kind);
        };
        String login = DepositorContext.userLogin();
        if (login == null || login.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется вход в портал");
        }
        boolean allowed = jdbc.sql("""
                        SELECT COALESCE(s.%s, true)
                        FROM dbo.app_user u
                        LEFT JOIN dbo.user_settings s ON s.user_id = u.user_id
                        WHERE u.login = :login AND u.is_active
                        """.formatted(column))
                .param("login", login)
                .query(Boolean.class)
                .optional()
                .orElse(false);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Загрузка из Excel для вас отключена");
        }
    }

    /** Персональные настройки видимости разделов (§6.3) — экран их спрашивает. */
    public java.util.Map<String, Object> settings() {
        String login = DepositorContext.userLogin();
        java.util.Map<String, Object> own = jdbc.sql("""
                        SELECT u.position, u.organization, u.phone,
                               to_char(u.last_login_at, 'DD.MM.YYYY, HH24:MI') AS "lastLogin",
                               COALESCE(s.hide_billing_tab, false)    AS "hideBillingTab",
                               COALESCE(s.hide_services_tab, false)   AS "hideServicesTab",
                               COALESCE(s.can_create_receipts, true)  AS "canCreateReceipts",
                               COALESCE(s.can_create_orders, true)    AS "canCreateOrders",
                               COALESCE(s.can_create_transfers, true) AS "canCreateTransfers",
                               COALESCE(s.can_upload_sku_excel, true) AS "canUploadSkuExcel",
                               COALESCE(s.can_upload_packaging_excel, true)
                                                                      AS "canUploadPackagingExcel",
                               COALESCE(s.can_edit_transport, true)   AS "canEditTransport"
                        FROM dbo.app_user u
                        LEFT JOIN dbo.user_settings s ON s.user_id = u.user_id
                        WHERE u.login = :login
                        """)
                .param("login", login)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(java.util.Map.of());
        if (own.isEmpty()) {
            return own;
        }

        // «Показать раздел перемещения» — настройка поклажедателя, а не
        // человека (V35). Экрану она по-прежнему приходит вместе с личными
        // настройками: ему важно, показывать ли раздел, а не на каком
        // уровне это решили.
        java.util.Map<String, Object> all = new java.util.LinkedHashMap<>(own);
        all.put("hideTransfersTab", hideTransfersTab());
        return all;
    }

    /** Скрыт ли раздел «Перемещения» у текущего поклажедателя. */
    private boolean hideTransfersTab() {
        String depositorId = DepositorContext.depositorId();
        if (depositorId == null || depositorId.isBlank()) {
            // поклажедатель ещё не выбран — скрывать нечего
            return false;
        }
        return jdbc.sql("SELECT hide_transfers_tab FROM dbo.depositor_settings_of(:dep::uuid)")
                .param("dep", depositorId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }
}
