package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Настройки поклажедателя.
     * <p>
     * Галочка «Показать раздел перемещения» на экране обратная по смыслу
     * имени колонки, а две галочки цены (V61) — прямые: отмечена значит
     * «показывать». У перемещения цены нет вовсе, поэтому флага для него
     * не существует.
     */
    public record Settings(Boolean hideTransfersTab,
                           Boolean priceInReceipts,
                           Boolean priceInOrders) {
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settings(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        requireDepositor(depositorId);
        Map<String, Object> stored = jdbc.sql("""
                        SELECT hide_transfers_tab AS "hideTransfersTab",
                               price_in_receipts  AS "priceInReceipts",
                               price_in_orders    AS "priceInOrders"
                        FROM dbo.depositor_settings_of(:dep::uuid)
                        """)
                .param("dep", depositorId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(Map.of());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("depositorId", depositorId);
        out.put("hideTransfersTab", Boolean.TRUE.equals(stored.get("hideTransfersTab")));
        out.put("priceInReceipts", Boolean.TRUE.equals(stored.get("priceInReceipts")));
        out.put("priceInOrders", Boolean.TRUE.equals(stored.get("priceInOrders")));
        return out;
    }

    @Transactional
    public Map<String, Object> save(PortalUser actor, String depositorId, Settings s) {
        users.requireAdmin(actor);
        requireDepositor(depositorId);
        jdbc.sql("""
                        SELECT dbo.admin_save_depositor_settings(:dep::uuid,
                                   :hideTransfers::boolean,
                                   :priceInReceipts::boolean,
                                   :priceInOrders::boolean)
                        """)
                .param("dep", depositorId)
                .param("hideTransfers", flag(s == null ? null : s.hideTransfersTab()))
                .param("priceInReceipts", flag(s == null ? null : s.priceInReceipts()))
                .param("priceInOrders", flag(s == null ? null : s.priceInOrders()))
                .query()
                .listOfRows();
        return settings(actor, depositorId);
    }

    /**
     * Не присланная галочка — снятая.
     * <p>
     * Экран всегда присылает все три: карточка клиента сохраняется целиком,
     * а не по одному полю. Но запрос может прийти и не от экрана, и тогда
     * «ничего не сказано» должно значить одно и то же каждый раз.
     */
    private static boolean flag(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    /**
     * Есть ли такой поклажедатель. Без этой проверки опечатка в
     * идентификаторе дала бы пустые настройки вместо 404, и администратор
     * решил бы, что сохранил, — а сохранять было некуда.
     */
    private void requireDepositor(String depositorId) {
        if (depositorId == null || depositorId.isBlank()) {
            throw Fail.badRequest("error.depositor.required");
        }
        boolean known = Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM dbo.admin_depositor_access()
                                        WHERE depositor_id::text = :dep::text)
                        """)
                .param("dep", depositorId)
                .query(Boolean.class)
                .single());
        if (!known) {
            throw Fail.notFound("error.depositor.not_found");
        }
    }
}
