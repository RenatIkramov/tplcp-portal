package com.ahlers.portal3pl.config;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Кто выполняет действие. Логин берётся из сессии (см. AuthFilter),
 * идентификатор — из базы по логину.
 *
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
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT COALESCE(s.can_edit_transport, true)
                        FROM dbo.app_user u
                        LEFT JOIN dbo.user_settings s ON s.user_id = u.user_id
                        WHERE u.login = :login AND u.is_active
                        """)
                .param("login", login)
                .query(Boolean.class)
                .optional()
                .orElse(false));
    }

    /** Персональные настройки видимости разделов (§6.3) — экран их спрашивает. */
    public java.util.Map<String, Object> settings() {
        String login = DepositorContext.userLogin();
        return jdbc.sql("""
                        SELECT u.position, u.organization, u.phone,
                               to_char(u.last_login_at, 'DD.MM.YYYY, HH24:MI') AS "lastLogin",
                               COALESCE(s.hide_transfers_tab, false)  AS "hideTransfersTab",
                               COALESCE(s.hide_billing_tab, false)    AS "hideBillingTab",
                               COALESCE(s.hide_services_tab, false)   AS "hideServicesTab",
                               COALESCE(s.can_create_receipts, true)  AS "canCreateReceipts",
                               COALESCE(s.can_create_orders, true)    AS "canCreateOrders",
                               COALESCE(s.can_create_transfers, true) AS "canCreateTransfers",
                               COALESCE(s.can_upload_sku_excel, true) AS "canUploadSkuExcel",
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
    }
}
