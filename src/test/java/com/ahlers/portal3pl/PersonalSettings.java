package com.ahlers.portal3pl;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Личные настройки учётных записей клиентов, под которыми ходят тесты,
 * — снимок до теста и возврат после (03.11.2026).
 * <p>
 * Тесты входят под {@code client@roga-kopyta.kz} и {@code client@mol-reki.kz},
 * а под этими же учётками заказчик проверяет портал руками. Тесты меняли
 * им язык, выбор колонок и фильтров, права («можно создавать перемещения»),
 * пароль — и оставляли после себя. Теперь {@link PortalTestBase} перед
 * каждым тестом запоминает всё это, приводит к известному состоянию
 * ({@link #startClean()}), а после теста возвращает ровно как было.
 * <p>
 * Что запоминается: строка учётки (пароль, блокировка, язык, имя
 * и реквизиты, роль, активность), права из {@code user_settings}, выбор
 * колонок и фильтров, доступы к поклажедателям и складам.
 * <p>
 * Всё это — владельцем схемы ({@link OwnerDb}): портал в {@code app_user}
 * и доступы напрямую больше не пишет (V133). Заодно подготовка не попадает
 * в журнал действий: у соединения владельца нет {@code app.actor}, а без
 * него триггеры журнала строку пропускают (V116) — прежде соединение несло
 * логин клиента, и подготовка была четырьмя пятыми всего журнала.
 * Что приводится к известному состоянию: пароль — тестовый
 * ({@code USER_PASSWORD}), учётка активна и не заблокирована, смена пароля
 * не требуется; права — все включены, разделы не скрыты; язык — портальный;
 * колонки и фильтры — как у портала; склады — все (V110: склады выдаются
 * человеку, и закрытый руками склад прятал бы данные, на которые
 * рассчитывают проверки). Без известного пароля тесты не войдут:
 * стоило сменить пароль руками, и падали почти все. Доступы
 * к поклажедателям не трогаются — их задаёт демо-набор.
 */
final class PersonalSettings {

    private static final String APP_USER_COLUMNS = "password_hash, full_name, role_id, position,"
            + " organization, phone, is_active, failed_login_count, locked_until,"
            + " password_changed_at, must_change_password, locale_id, all_depositors, all_warehouses";

    /** Таблицы со строками пользователя: имя → колонки кроме user_id. */
    private static final Map<String, List<String>> ROW_TABLES = Map.of(
            "user_settings", List.of("hide_billing_tab", "hide_services_tab",
                    "can_create_receipts", "can_create_orders", "can_create_transfers",
                    "can_upload_sku_excel", "can_upload_packaging_excel", "can_edit_transport"),
            "user_table_column", List.of("table_key", "column_id", "sort_order"),
            "user_table_filter", List.of("table_key", "column_id", "sort_order"),
            "user_depositor_access", List.of("depositor_id"),
            "user_warehouse_access", List.of("warehouse_id"));

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final List<String> logins;
    private final String passwordHash;
    private final List<Saved> saved = new ArrayList<>();

    private record Saved(Object userId, Map<String, Object> user, Map<String, List<Map<String, Object>>> rows) {
    }

    /** @param passwordHash хэш тестового пароля — с ним учётки начинают тест */
    PersonalSettings(OwnerDb owner, List<String> logins, String passwordHash) {
        this.jdbc = owner.jdbc;
        this.tx = owner.tx;
        this.logins = logins;
        this.passwordHash = passwordHash;
    }

    /** Запомнить. Учётки, которой в базе нет, просто нет в снимке. */
    PersonalSettings remember() {
        tx.executeWithoutResult(status -> {
            for (String login : logins) {
                List<Map<String, Object>> found = jdbc.sql("SELECT user_id, " + APP_USER_COLUMNS
                                + " FROM dbo.app_user WHERE login = :login")
                        .param("login", login).query().listOfRows();
                if (found.isEmpty()) {
                    continue;
                }
                Map<String, Object> user = found.get(0);
                Object userId = user.get("user_id");
                Map<String, List<Map<String, Object>>> rows = new java.util.LinkedHashMap<>();
                ROW_TABLES.forEach((table, columns) -> rows.put(table,
                        jdbc.sql("SELECT " + String.join(", ", columns) + " FROM dbo." + table
                                        + " WHERE user_id = :id")
                                .param("id", userId).query().listOfRows()));
                saved.add(new Saved(userId, user, rows));
            }
        });
        return this;
    }

    /** Тестовый пароль, права включены, разделы не скрыты, язык, колонки и фильтры портальные. */
    void startClean() {
        tx.executeWithoutResult(status -> {
            for (Saved s : saved) {
                jdbc.sql("""
                                INSERT INTO dbo.user_settings (user_id, hide_billing_tab,
                                    hide_services_tab, can_create_receipts, can_create_orders,
                                    can_create_transfers, can_upload_sku_excel,
                                    can_upload_packaging_excel, can_edit_transport)
                                VALUES (:id, false, false, true, true, true, true, true, true)
                                ON CONFLICT (user_id) DO UPDATE SET
                                    hide_billing_tab = false, hide_services_tab = false,
                                    can_create_receipts = true, can_create_orders = true,
                                    can_create_transfers = true, can_upload_sku_excel = true,
                                    can_upload_packaging_excel = true,
                                    can_edit_transport = true
                                """)
                        .param("id", s.userId()).update();
                jdbc.sql("""
                                UPDATE dbo.app_user
                                   SET password_hash = :hash, must_change_password = false,
                                       failed_login_count = 0, locked_until = NULL,
                                       is_active = true, locale_id = NULL,
                                       all_warehouses = true
                                 WHERE user_id = :id
                                """)
                        .param("hash", passwordHash)
                        .param("id", s.userId()).update();
                jdbc.sql("DELETE FROM dbo.user_table_column WHERE user_id = :id")
                        .param("id", s.userId()).update();
                jdbc.sql("DELETE FROM dbo.user_table_filter WHERE user_id = :id")
                        .param("id", s.userId()).update();
            }
        });
    }

    /** Вернуть ровно как было. */
    void restore() {
        tx.executeWithoutResult(status -> {
            for (Saved s : saved) {
                StringBuilder set = new StringBuilder();
                for (String column : APP_USER_COLUMNS.split(",\\s*")) {
                    set.append(set.isEmpty() ? "" : ", ").append(column).append(" = :").append(column);
                }
                JdbcClient.StatementSpec update = jdbc.sql("UPDATE dbo.app_user SET " + set
                        + " WHERE user_id = :user_id");
                for (Map.Entry<String, Object> e : s.user().entrySet()) {
                    update = update.param(e.getKey(), e.getValue());
                }
                update.update();

                s.rows().forEach((table, rows) -> {
                    jdbc.sql("DELETE FROM dbo." + table + " WHERE user_id = :id")
                            .param("id", s.userId()).update();
                    List<String> columns = ROW_TABLES.get(table);
                    String insert = "INSERT INTO dbo." + table + " (user_id, " + String.join(", ", columns)
                            + ") VALUES (:user_id, :" + String.join(", :", columns) + ")";
                    for (Map<String, Object> row : rows) {
                        JdbcClient.StatementSpec spec = jdbc.sql(insert).param("user_id", s.userId());
                        for (String column : columns) {
                            spec = spec.param(column, row.get(column));
                        }
                        spec.update();
                    }
                });
            }
        });
    }
}
