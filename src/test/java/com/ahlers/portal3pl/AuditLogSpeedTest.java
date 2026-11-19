package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Журнал действий не зарастает проверками и строится одним проходом
 * (13.11.2026, V131).
 *
 * <p>НАЙДЕНО. AuditLogTabTest падал по таймауту: первая страница журнала
 * строилась десятки секунд. Четыре пятых журнала писали сами проверки —
 * подготовка и возврат настроек клиента шли под его логином, — а сводка
 * событий считалась дважды и с JIT, которому здесь нечего ускорять.
 */
class AuditLogSpeedTest extends PortalTestBase {

    /**
     * Сколько событий клиента в журнале за сегодня. Сам журнал portal_app
     * не читает — только функциями администратора.
     */
    private long rows() {
        return jdbc.sql("SELECT (dbo.admin_audit_log(NULL, :a, NULL, current_date, NULL, 1, 1) ->> 'total')::bigint")
                .param("a", USER).query(Long.class).single();
    }

    /** Подготовка и возврат настроек клиента — под его логином, а журнал не растёт. */
    @Test
    void preparingSettingsIsNotJournaled() {
        signIn();   // соединения теперь несут логин клиента — как в любой проверке
        // проверка что-то поменяла клиенту — возврату есть что возвращать
        new org.springframework.transaction.support.TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.actor', '', true)").query().listOfRows();
            jdbc.sql("UPDATE dbo.user_settings SET hide_billing_tab = true WHERE user_id = :id::uuid")
                    .param("id", userId(USER)).update();
        });
        long before = rows();
        PersonalSettings settings = new PersonalSettings(owner(), List.of(USER, OTHER_USER),
                passwordEncoder.encode(USER_PASSWORD)).remember();
        settings.startClean();
        settings.restore();
        assertEquals(before, rows(), "подготовка настроек проверки попала в журнал действий");
    }

    /** Сводка журнала — один проход и без JIT. */
    @Test
    void auditFunctionsAreCheap() {
        String page = jdbc.sql("SELECT prosrc FROM pg_proc WHERE proname = 'admin_audit_log'")
                .query(String.class).single();
        assertTrue(page.contains("count(*) OVER ()"), "admin_audit_log снова сводит журнал дважды");
        List<String> withJit = jdbc.sql("""
                        SELECT proname FROM pg_proc
                         WHERE proname IN ('audit_net', 'admin_audit_log', 'audit_history', 'admin_audit_history')
                           AND NOT ('jit=off' = ANY (COALESCE(proconfig, '{}')))
                        """)
                .query(String.class).list();
        assertTrue(withJit.isEmpty(), "функции журнала без jit=off: " + withJit);
    }
}
