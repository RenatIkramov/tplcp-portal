package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки портала для поклажедателя: по одному значению на клиента.
 *
 * <p>Почему отдельно от справочника организаций: {@code depositor} — это
 * реквизиты (БИН, адрес, договор), а здесь поведение портала. Разные вещи
 * с разной частотой правок, как {@code user_settings} отдельно от
 * {@code app_user}. И настроек тут со временем станет больше.
 *
 * <p>Списки сюда не попадают: склады лежат в {@code depositor_warehouse},
 * поля — в {@code depositor_table_column}. Там на клиента много строк,
 * в колонку такое не положишь.
 *
 * <p>Читается и пишется через SECURITY DEFINER: администратор работает
 * с чужим поклажедателем, а RLS пускает только к своему.
 */
@Service
public class DepositorSettingsService {

    private final JdbcClient jdbc;
    private final AdminUserService users;

    public DepositorSettingsService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** Настройки поклажедателя. Галочка «Показать раздел перемещения» — обратная. */
    public record Settings(Boolean hideTransfersTab) {
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settings(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        requireDepositor(depositorId);
        Boolean hidden = jdbc
                .sql("SELECT hide_transfers_tab FROM dbo.depositor_settings_of(:dep::uuid)")
                .param("dep", depositorId)
                .query(Boolean.class)
                .optional()
                .orElse(false);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("depositorId", depositorId);
        out.put("hideTransfersTab", Boolean.TRUE.equals(hidden));
        return out;
    }

    @Transactional
    public Map<String, Object> save(PortalUser actor, String depositorId, Settings s) {
        users.requireAdmin(actor);
        requireDepositor(depositorId);
        jdbc.sql("SELECT dbo.admin_save_depositor_settings(:dep::uuid, :hideTransfers::boolean)")
                .param("dep", depositorId)
                .param("hideTransfers", s == null || s.hideTransfersTab() == null
                        ? Boolean.FALSE : s.hideTransfersTab())
                .query()
                .listOfRows();
        return settings(actor, depositorId);
    }

    /**
     * Есть ли такой поклажедатель. Без этой проверки опечатка в
     * идентификаторе дала бы пустые настройки вместо 404, и администратор
     * решил бы, что сохранил, — а сохранять было некуда.
     */
    private void requireDepositor(String depositorId) {
        if (depositorId == null || depositorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите поклажедателя");
        }
        boolean known = Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dbo.admin_depositor_access()
                                        WHERE depositor_id::text = :dep::text)
                        """)
                .param("dep", depositorId)
                .query(Boolean.class)
                .single());
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Поклажедатель не найден");
        }
    }
}
