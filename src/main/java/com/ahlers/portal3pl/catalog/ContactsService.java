package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.DepositorContext;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Страница «Контакты»: реквизиты оператора, склады и менеджеры.
 *
 * <p>Раньше всё это было вписано в вёрстку демо. Переезд склада или
 * смена менеджера означали правку HTML — то есть работу разработчика
 * там, где её быть не должно: и склады, и пользователи и так ведутся
 * в справочниках портала.
 *
 * <p>Кому что видно:
 * <ul>
 *   <li><b>реквизиты</b> — поклажедателю одна организация, та, с которой
 *       у него договор ({@code depositor.operator_id}); остальным все
 *       активные, потому что менеджер и администратор работают со всеми;</li>
 *   <li><b>склады</b> — те же, что доступны человеку в документах:
 *       представление {@code v_warehouses} уже учитывает выданные
 *       поклажедателю склады, и второго правила для той же сущности
 *       заводить не надо;</li>
 *   <li><b>менеджеры</b> — все активные, свой помечен и идёт первым.</li>
 * </ul>
 *
 * <p>Обе функции в базе — {@code SECURITY DEFINER}: клиенту нужно увидеть
 * организацию и сотрудников оператора, а прав на эти таблицы у него нет
 * и быть не должно. Функции отдают строго контактные поля.
 */
@Service
public class ContactsService {

    private final JdbcClient jdbc;
    private final DepositorScope scope;

    public ContactsService(JdbcClient jdbc, DepositorScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> page(PortalUser user) {
        String depositorId = DepositorContext.depositorId();
        boolean isClient = "depositor".equals(user.roleCode());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operators", operators(isClient ? depositorId : null));
        out.put("warehouses", warehouses(depositorId));
        out.put("managers", managers(depositorId));
        return out;
    }

    private List<Map<String, Object>> operators(String depositorId) {
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.contact_operators(:dep::uuid)")
                .param("dep", depositorId)
                .query()
                .listOfRows());
    }

    /**
     * Склады.
     *
     * <p>Поклажедатель выбран — читаем {@code v_warehouses} под его
     * контекстом: представление само отдаст только выданные ему склады.
     * Не выбран (администратор ещё не выбрал клиента) — показываем все
     * действующие: ограничивать нечем, а пустая страница выглядела бы
     * как поломка.
     */
    private List<Map<String, Object>> warehouses(String depositorId) {
        if (depositorId == null || depositorId.isBlank()) {
            return SqlRows.toCamelCase(jdbc
                    .sql("SELECT * FROM dbo.v_warehouses_all WHERE is_active ORDER BY name")
                    .query()
                    .listOfRows());
        }
        scope.apply();
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.v_warehouses ORDER BY name")
                .query()
                .listOfRows());
    }

    private List<Map<String, Object>> managers(String depositorId) {
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.contact_managers(:dep::uuid)")
                .param("dep", depositorId)
                .query()
                .listOfRows());
    }
}
