package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PasswordService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Справочник «Пользователи» в режиме администрирования.
 * <p>
 * Права проверяются по базе, а не по тому, что лежит в сессии: роль могли
 * понизить уже после входа, и старая сессия не должна этого переживать.
 */
@Service
public class AdminUserService {

    private final JdbcClient jdbc;
    private final PasswordService passwords;

    public AdminUserService(JdbcClient jdbc, PasswordService passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> users(PortalUser actor) {
        requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_users()")
                .query()
                .listOfRows());
    }

    /** Карточка пользователя: блокировка и галочки видимости разделов (§6.3). */
    public record Settings(
            Boolean isActive,
            Boolean hideBillingTab, Boolean hideServicesTab,
            Boolean canCreateReceipts, Boolean canCreateOrders, Boolean canCreateTransfers,
            Boolean canUploadSkuExcel, Boolean canUploadPackagingExcel,
            Boolean canEditTransport) {
    }

    @Transactional
    public List<Map<String, Object>> save(PortalUser actor, String userId, Settings s) {
        requireAdmin(actor);
        userRow(userId);     // есть ли такой — иначе 404

        // Администратор не может заблокировать сам себя: иначе портал
        // остался бы без того, кто вправе разблокировать.
        // orDefault возвращает примитив, поэтому обёртка Boolean.FALSE.equals
        // только заворачивала его обратно в объект
        if (!orDefault(s.isActive(), true) && actor.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Нельзя заблокировать свою учётную запись");
        }

        jdbc.sql("""
                        SELECT dbo.admin_save_user(:id::uuid, :active,
                               :hideBilling, :hideServices,
                               :createReceipts, :createOrders, :createTransfers,
                               :uploadSku, :uploadPackaging, :editTransport)
                        """)
                .param("id", userId)
                .param("active", orDefault(s.isActive(), true))
                .param("hideBilling", orDefault(s.hideBillingTab(), false))
                .param("hideServices", orDefault(s.hideServicesTab(), false))
                .param("createReceipts", orDefault(s.canCreateReceipts(), true))
                .param("createOrders", orDefault(s.canCreateOrders(), true))
                .param("createTransfers", orDefault(s.canCreateTransfers(), true))
                .param("uploadSku", orDefault(s.canUploadSkuExcel(), true))
                .param("uploadPackaging", orDefault(s.canUploadPackagingExcel(), true))
                .param("editTransport", orDefault(s.canEditTransport(), true))
                .query()
                .listOfRows();

        return users(actor);
    }

    /* ================= Анкета, роль и доступы ================= */

    /** Данные пользователя из карточки. Логин меняется здесь же, но отдельно. */
    public record Profile(
            String login, String fullName, String roleCode,
            String position, String organization, String phone,
            List<String> depositorIds) {
    }

    /** Создание пользователя. Возвращает временный пароль, чтобы его передать человеку. */
    @Transactional
    public Map<String, Object> create(PortalUser actor, Profile p, String password) {
        requireAdmin(actor);
        String login = requireText(p.login(), "Укажите логин");
        requireLoginFree(login, null);
        requireText(p.fullName(), "Укажите ФИО");
        requireRoleExists(p.roleCode());

        String finalPassword = password == null || password.isBlank()
                ? com.ahlers.portal3pl.auth.PasswordService.generate()
                : password.trim();
        String hash = passwords.hashForNewUser(login, finalPassword);
        String userId;
        try {
            userId = jdbc.sql("""
                            SELECT dbo.admin_create_user(:login::text, :fullName::text,
                                   :role::text, :position::text, :organization::text,
                                   :phone::text, :hash::text)::text
                            """)
                    .param("login", login)
                    .param("fullName", p.fullName().trim())
                    .param("role", p.roleCode())
                    .param("position", p.position())
                    .param("organization", p.organization())
                    .param("phone", p.phone())
                    .param("hash", hash)
                    .query(String.class)
                    .single();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // гонка: логин заняли между проверкой и вставкой
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Логин «" + login + "» уже занят");
        }
        setDepositorAccess(userId, p.depositorIds());

        return Map.of("userId", userId, "password", finalPassword,
                "message", "Пользователь создан. Пароль нужно передать ему — "
                        + "при первом входе портал попросит задать свой");
    }

    /** Правка анкеты, роли, логина и доступов к поклажедателям. */
    @Transactional
    public List<Map<String, Object>> saveProfile(PortalUser actor, String userId, Profile p) {
        requireAdmin(actor);
        Map<String, Object> currentRow = userRow(userId);
        requireText(p.fullName(), "Укажите ФИО");
        requireRoleExists(p.roleCode());

        // Себе роль не меняем: администратор, случайно ставший поклажедателем,
        // потеряет доступ к этому разделу и не сможет вернуть его обратно.
        if (actor.userId().equals(userId) && !actor.roleCode().equals(p.roleCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Нельзя изменить роль своей учётной записи");
        }

        jdbc.sql("""
                        SELECT dbo.admin_update_user(:id::uuid, :fullName::text, :role::text,
                               :position::text, :organization::text, :phone::text)
                        """)
                .param("id", userId)
                .param("fullName", p.fullName().trim())
                .param("role", p.roleCode())
                .param("position", p.position())
                .param("organization", p.organization())
                .param("phone", p.phone())
                .query()
                .listOfRows();

        String newLogin = p.login() == null ? null : p.login().trim();
        if (newLogin != null && !newLogin.isBlank()
                && !newLogin.equalsIgnoreCase((String) currentRow.get("login"))) {
            requireLoginFree(newLogin, userId);
            jdbc.sql("SELECT dbo.admin_change_login(:id::uuid, :login::text)")
                    .param("id", userId)
                    .param("login", newLogin)
                    .query()
                    .listOfRows();
        }

        setDepositorAccess(userId, p.depositorIds());
        return users(actor);
    }

    /** Поклажедатели, выданные пользователю — для галочек в карточке. */
    @Transactional(readOnly = true)
    public List<String> depositorsOfUser(PortalUser actor, String userId) {
        requireAdmin(actor);
        return jdbc.sql("SELECT depositor_id::text FROM dbo.admin_user_depositors(:id::uuid)")
                .param("id", userId)
                .query(String.class)
                .list();
    }

    /** Только доступы — экран «Доступы» меняет их, не трогая анкету. */
    @Transactional
    public List<String> saveDepositorsOfUser(PortalUser actor, String userId, List<String> ids) {
        requireAdmin(actor);
        userRow(userId);
        setDepositorAccess(userId, ids == null ? List.of() : ids);
        return depositorsOfUser(actor, userId);
    }

    /** Все поклажедатели и все роли — списки для карточки. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> depositors(PortalUser actor) {
        requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_depositors()")
                .query().listOfRows());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> roles(PortalUser actor) {
        requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_roles()")
                .query().listOfRows());
    }

    /**
     * Доступы к поклажедателям. Передан null — экран их не редактировал,
     * трогать нечего; передан пустой список — доступы сняты сознательно.
     */
    private void setDepositorAccess(String userId, List<String> depositorIds) {
        if (depositorIds == null) {
            return;
        }
        jdbc.sql("SELECT dbo.admin_set_depositor_access(:id::uuid, :ids::uuid[])")
                .param("id", userId)
                .param("ids", "{" + String.join(",", depositorIds) + "}")
                .query()
                .listOfRows();
    }

    private void requireLoginFree(String login, String exceptUserId) {
        boolean taken = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dbo.app_user
                                        WHERE lower(login) = lower(:login)
                                          AND (:except::uuid IS NULL OR user_id <> :except::uuid))
                        """)
                .param("login", login)
                .param("except", exceptUserId)
                .query(Boolean.class)
                .single();
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Логин «" + login + "» уже занят");
        }
    }

    private void requireRoleExists(String roleCode) {
        boolean exists = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dbo.enum_user_role
                                        WHERE code = :code AND is_active)
                        """)
                .param("code", roleCode == null ? "" : roleCode)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите роль пользователя");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /** Администратор задаёт пользователю новый пароль (§6.2). */
    @Transactional
    public void setPassword(PortalUser actor, String userId, String newPassword) {
        requireAdmin(actor);
        Map<String, Object> target = userRow(userId);
        passwords.setByAdmin(userId, (String) target.get("login"), newPassword);
    }

    private Map<String, Object> userRow(String userId) {
        return jdbc.sql("SELECT user_id::text, login, full_name FROM dbo.app_user WHERE user_id = :id::uuid")
                .param("id", userId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Пользователь не найден"));
    }

    /** Та же проверка для соседних экранов администрирования (доступы). */
    public void requireAdmin(PortalUser actor) {
        String role = jdbc.sql("SELECT dbo.auth_role_by_login(:login::text)")
                .param("login", actor.login())
                .query(String.class)
                .optional()
                .orElse("");
        if (!"admin".equals(role) && !"developer".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Раздел доступен только администратору портала");
        }
    }

    private static boolean orDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
