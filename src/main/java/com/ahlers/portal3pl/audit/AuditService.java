package com.ahlers.portal3pl.audit;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.DepositorScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Журнал действий для администратора (§11 ТЗ, V116–V117).
 *
 * <p>Пишут журнал триггеры в базе — здесь только чтение, срок хранения
 * и чистка. Читается через функции с правами владельца: журнал нужен
 * по всем клиентам, а под RLS администратор видел бы одного. Право на
 * раздел проверяет портал, как и в остальном администрировании.
 *
 * <p>Ответ журнала собирает база целиком (JSON): строки, «всего»,
 * страница. Здесь он только передаётся дальше.
 */
@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    /** Данные клиента: история читается под RLS, как сама карточка. */
    private static final Set<String> CLIENT_OBJECTS = Set.of("sku", "packaging", "supplier", "consignee");
    /** Карточки администрирования: история — только администратору. */
    private static final Set<String> ADMIN_OBJECTS = Set.of("app_user", "warehouse", "depositor", "operator");

    private final JdbcClient jdbc;
    private final AdminUserService users;
    private final DepositorScope scope;

    public AuditService(JdbcClient jdbc, AdminUserService users, DepositorScope scope) {
        this.jdbc = jdbc;
        this.users = users;
        this.scope = scope;
    }

    /**
     * «История» карточки (V118): кто, когда и что менял.
     *
     * <p>Товар, упаковка, контрагенты — данные клиента: история видна
     * тому, кто видит карточку, и только своего клиента (RLS). Пользователь,
     * склад, поклажедатель, организация оператора — администрирование:
     * только администратору. Документы — свой адрес у документа.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(PortalUser user, String objectType, String objectId) {
        String function;
        if (CLIENT_OBJECTS.contains(objectType)) {
            scope.apply();
            function = "audit_history";
        } else if (ADMIN_OBJECTS.contains(objectType)) {
            users.requireAdmin(user);
            function = "admin_audit_history";
        } else {
            throw Fail.notFound("error.audit.unknown_object");
        }
        return SqlRows.toCamelCase(jdbc.sql("""
                        SELECT event_id, to_json(at) #>> '{}' AS at, actor, actor_name, action, part,
                               field, field_key, old_text, new_text
                          FROM dbo.%s(:type, :id)
                        """.formatted(function))
                .param("type", objectType)
                .param("id", objectId)
                .query().listOfRows());
    }

    /**
     * Страница журнала.
     *
     * @param depositorId клиент или null — все
     * @param actor       логин или null — все
     * @param objectType  вид объекта (receipts, sku, app_user…) или null — все
     * @param from        с какого дня, включительно; null — без начала
     * @param to          по какой день, включительно; null — без конца
     */
    @Transactional(readOnly = true)
    public String page(PortalUser admin, String depositorId, String actor, String objectType,
                       LocalDate from, LocalDate to, int page, int pageSize) {
        users.requireAdmin(admin);
        return jdbc.sql("""
                        SELECT dbo.admin_audit_log(:dep::uuid, :actor::text, :type::text,
                                                   :from::date, :to::date, :page, :size)::text
                        """)
                .param("dep", blankToNull(depositorId))
                .param("actor", blankToNull(actor))
                .param("type", blankToNull(objectType))
                .param("from", from)
                .param("to", to)
                .param("page", page)
                .param("size", pageSize)
                .query(String.class).single();
    }

    /** Кто встречается в журнале — для отбора «Пользователь». */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> actors(PortalUser admin) {
        users.requireAdmin(admin);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_audit_actors()").query().listOfRows());
    }

    /** Сколько месяцев хранится журнал. */
    @Transactional(readOnly = true)
    public Map<String, Object> settings(PortalUser admin) {
        users.requireAdmin(admin);
        int months = jdbc.sql("SELECT value::int FROM dbo.portal_setting WHERE setting_key = 'audit_retention_months'")
                .query(Integer.class).single();
        return Map.of("retentionMonths", months);
    }

    /** Сменить срок хранения: от 1 до 120 месяцев; старое удаляется сразу. */
    @Transactional
    public Map<String, Object> saveSettings(PortalUser admin, Integer months) {
        users.requireAdmin(admin);
        if (months == null || months < 1 || months > 120) {
            throw Fail.badRequest("error.audit.retention_range", "min", 1, "max", 120);
        }
        int removed = jdbc.sql("SELECT dbo.admin_set_audit_retention(:m)")
                .param("m", months).query(Integer.class).single();
        return Map.of("retentionMonths", months, "removed", removed);
    }

    /**
     * Раз в сутки — удалить журнал старше срока. Ночью, когда портал
     * почти не трогают; час — не ровный, чтобы не совпасть с чужими
     * ночными заданиями на том же сервере.
     */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void cleanup() {
        int removed = jdbc.sql("SELECT dbo.audit_cleanup()").query(Integer.class).single();
        if (removed > 0) {
            LOG.info("Журнал действий: удалено записей старше срока хранения — {}", removed);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
