package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Экраны доступов: склады кладовщика и склады поклажедателя.
 * <p>
 * Доступы менеджера к поклажедателям живут в AdminUserService — там же,
 * где карточка пользователя: это один и тот же набор галочек, просто
 * открывается с двух сторон.
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

    /* ---------------- Склады кладовщика ---------------- */

    @Transactional(readOnly = true)
    public List<String> warehousesOfUser(PortalUser actor, String userId) {
        users.requireAdmin(actor);
        return jdbc.sql("SELECT warehouse_id FROM dbo.admin_user_warehouses(:id::uuid)")
                .param("id", userId)
                .query(String.class)
                .list();
    }

    @Transactional
    public List<String> saveWarehousesOfUser(PortalUser actor, String userId, List<String> ids) {
        users.requireAdmin(actor);
        jdbc.sql("SELECT dbo.admin_set_warehouse_access(:id::uuid, :ids::text[])")
                .param("id", userId)
                .param("ids", sqlArray(ids))
                .query()
                .listOfRows();
        return warehousesOfUser(actor, userId);
    }

    /* ---------------- Склады поклажедателя ---------------- */

    /** Поклажедатели со списком своих складов — список экрана. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> depositorAccess(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_depositor_access()")
                .query().listOfRows());
    }

    @Transactional(readOnly = true)
    public List<String> warehousesOfDepositor(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        return jdbc.sql("SELECT warehouse_id FROM dbo.admin_depositor_warehouses(:id::uuid)")
                .param("id", depositorId)
                .query(String.class)
                .list();
    }

    @Transactional
    public List<Map<String, Object>> saveWarehousesOfDepositor(PortalUser actor, String depositorId,
                                                                List<String> ids) {
        users.requireAdmin(actor);
        jdbc.sql("SELECT dbo.admin_set_depositor_warehouses(:id::uuid, :ids::text[])")
                .param("id", depositorId)
                .param("ids", sqlArray(ids))
                .query()
                .listOfRows();
        return depositorAccess(actor);
    }

    /**
     * Список складов в вид, который понимает Postgres как text[].
     * Пустой список — это «ограничений нет», и он допустим: именно так
     * поклажедателю возвращают все склады.
     */
    private static String sqlArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return "{" + String.join(",", values.stream().map(v -> '"' + v + '"').toList()) + "}";
    }
}
