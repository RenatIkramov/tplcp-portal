package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Списки для доступов: все склады портала и склады пользователя.
 * <p>
 * Сохраняет доступ человека карточка пользователя (AdminUserService,
 * V110): поклажедатели, склады и признаки «все» — одним запросом.
 */
@Service
public class AdminAccessService {

    private final JdbcClient jdbc;
    private final AdminUserService users;

    public AdminAccessService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /* ---------------- Склады портала ---------------- */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> warehouses(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_warehouses()")
                .query().listOfRows());
    }

    /* ---------------- Склады пользователя ---------------- */

    @Transactional(readOnly = true)
    public List<String> warehousesOfUser(PortalUser actor, String userId) {
        users.requireAdmin(actor);
        return jdbc.sql("SELECT warehouse_id FROM dbo.admin_user_warehouses(:id::uuid)")
                .param("id", userId)
                .query(String.class)
                .list();
    }
}
