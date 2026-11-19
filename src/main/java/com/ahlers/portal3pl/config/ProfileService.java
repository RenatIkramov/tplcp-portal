package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Личный кабинет (11.11.2026): то, что человек может узнать о себе.
 *
 * <p>Прежде ниже шапки кабинета было демо прототипа: реквизиты и менеджер
 * с прочерками, адрес склада, вписанный в код, списки, которые никто не
 * наполнял. Теперь всё — из базы, теми же правилами, что и в остальном
 * портале:
 * <ul>
 *   <li><b>зона доступа</b> — признаки «все» и списки из карточки
 *       пользователя (V110): поклажедатели — функцией входа
 *       {@code depositors_for_user}, склады — свои или все действующие;</li>
 *   <li><b>организация</b> — реквизиты выбранного клиента, только под его
 *       контекстом (RLS): чужие реквизиты прочитать нечем;</li>
 *   <li><b>менеджеры</b> — те, у кого есть доступ к этому клиенту:
 *       та же функция, что у страницы «Контакты» ({@code contact_managers}),
 *       только «свои».</li>
 * </ul>
 * Пользователей своей организации кабинет не показывает (решение
 * заказчика): кто ещё работает за клиента, видит администратор.
 */
@Service
public class ProfileService {

    private final JdbcClient jdbc;
    private final DepositorScope scope;

    public ProfileService(JdbcClient jdbc, DepositorScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(PortalUser user) {
        Map<String, Object> me = jdbc.sql("""
                        SELECT dbo.enum_name('enum_user_role', r.id::text, r.name) AS "roleName",
                               u.all_depositors AND r.code <> 'depositor' AS "allDepositors",
                               u.all_warehouses AS "allWarehouses",
                               u.user_id::text AS "userId"
                          FROM dbo.app_user u
                          JOIN dbo.enum_user_role r ON r.id = u.role_id
                         WHERE u.login = :login
                        """)
                .param("login", user.login())
                .query()
                .singleRow();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roleName", me.get("roleName"));
        out.put("allDepositors", me.get("allDepositors"));
        out.put("allWarehouses", me.get("allWarehouses"));
        out.put("depositors", jdbc.sql("""
                        SELECT code, org_name AS "orgName"
                          FROM dbo.depositors_for_user(:login::text) ORDER BY org_name
                        """)
                .param("login", user.login())
                .query().listOfRows());
        out.put("warehouses", jdbc.sql("""
                        SELECT w.warehouse_id AS "warehouseId", w.name, w.address
                          FROM dbo.warehouse w
                         WHERE w.is_active
                           AND (:all OR EXISTS (SELECT 1 FROM dbo.user_warehouse_access a
                                                 WHERE a.user_id = :id::uuid
                                                   AND a.warehouse_id = w.warehouse_id))
                         ORDER BY w.name
                        """)
                .param("all", Boolean.TRUE.equals(me.get("allWarehouses")))
                .param("id", me.get("userId"))
                .query().listOfRows());

        // Организация и менеджеры — только когда клиент выбран: без него
        // реквизиты не прочитать (RLS), а менеджеры ничьи.
        String depositorId = DepositorContext.depositorId();
        if (depositorId != null && !depositorId.isBlank()) {
            scope.apply();
            List<Map<String, Object>> org = SqlRows.toCamelCase(jdbc.sql("""
                            SELECT code, org_name, bin, legal_address, phone, email,
                                   contract_number, contract_valid_until::text AS contract_valid_until
                              FROM dbo.v_depositors
                            """)
                    .query().listOfRows());
            out.put("organization", org.isEmpty() ? null : org.getFirst());
            out.put("managers", SqlRows.toCamelCase(jdbc.sql("""
                            SELECT full_name, job_title, phone, email
                              FROM dbo.contact_managers(:dep::uuid) WHERE is_yours
                            """)
                    .param("dep", depositorId)
                    .query().listOfRows()));
        } else {
            out.put("organization", null);
            out.put("managers", List.of());
        }
        return out;
    }
}
