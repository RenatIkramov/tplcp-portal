package com.ahlers.portal3pl;

import com.ahlers.portal3pl.config.DepositorContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Общая основа тестов.
 *
 * Тесты обращаются к сервисам напрямую, а не через HTTP. Причина практическая:
 * классы для тестирования веб-слоя в разных версиях Spring Boot лежат в разных
 * пакетах, и тесты из-за этого ломались при смене версии. Сервисный слой
 * стабилен, а проверять мы хотим именно правила (кто что может), а не механику
 * HTTP — коды ответов при этом всё равно проверяются, через статус исключения.
 */
@SpringBootTest(properties = "portal.dev-mode=true")
public abstract class PortalTestBase {

    protected static final String DEPOSITOR = "D-001";
    protected static final String OTHER_DEPOSITOR = "D-002";
    protected static final String USER = "client@roga-kopyta.kz";

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected PlatformTransactionManager txManager;

    /** Представиться порталом: кто работает и с каким поклажедателем. */
    protected void войтиКак(String login, String depositorCode) {
        DepositorContext.set(login, null, depositorCode);
    }

    protected void войти() {
        войтиКак(USER, DEPOSITOR);
    }

    @AfterEach
    void очиститьКонтекст() {
        DepositorContext.clear();
    }

    /** Проверяет, что вызов отклонён с ожидаемым кодом ответа. */
    protected void ожидатьОтказ(int httpStatus, Runnable действие) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, действие::run);
        assertEquals(httpStatus, e.getStatusCode().value(),
                "ожидался код " + httpStatus + ", получено: " + e.getReason());
    }

    /**
     * Чтение из базы напрямую. Таблицы под RLS, поэтому запрос выполняется
     * в транзакции с выставленным контекстом — иначе база честно вернёт ноль строк.
     */
    protected String идентификатор(String sql) {
        return new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', dbo.dev_depositor_by_code(:code)::text, true)")
                    .param("code", DEPOSITOR).query(String.class).single();
            return jdbc.sql(sql).query(String.class).single();
        });
    }

    /** Изменение в базе под контекстом поклажедателя. */
    protected void вКонтексте(String sql) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', dbo.dev_depositor_by_code(:code)::text, true)")
                    .param("code", DEPOSITOR).query(String.class).single();
            jdbc.sql(sql).update();
        });
    }
}
