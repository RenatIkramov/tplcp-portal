package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.config.DepositorContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Общая основа тестов.
 * <p>
 * Тесты обращаются к сервисам напрямую, а не через HTTP. Причина практическая:
 * классы для тестирования веб-слоя в разных версиях Spring Boot лежат в разных
 * пакетах, и тесты из-за этого ломались при смене версии. Сервисный слой
 * стабилен, а проверять мы хотим именно правила (кто что может), а не механику
 * HTTP — коды ответов при этом всё равно проверяются, через статус исключения.
 */
@SpringBootTest
public abstract class PortalTestBase {

    protected static final String DEPOSITOR = "D-001";
    protected static final String OTHER_DEPOSITOR = "D-002";
    protected static final String USER = "client@roga-kopyta.kz";
    protected static final String USER_PASSWORD = "Portal123!";

    /**
     * Пользователь второго поклажедателя. Нужен для проверок изоляции:
     * с этапа 5 данные без вошедшего пользователя не отдаются вообще,
     * поэтому «посмотреть глазами другого клиента» означает именно войти
     * его учётной записью, а не подставить чужой идентификатор.
     */
    protected static final String OTHER_USER = "client@mol-reki.kz";

    /**
     * Служебный администратор для тестов.
     * <p>
     * Намеренно НЕ учётка `admin`: её первый пароль временный по замыслу
     * портала — человек меняет его при первом входе, и тест, завязанный
     * на этот пароль, обязан был сломаться. Тесты заводят себе отдельного
     * администратора и сами приводят его в известное состояние.
     */
    protected static final String ADMIN = "qa-admin@ahlers.com";
    protected static final String ADMIN_PASSWORD = "Qa-admin-2026";

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected AuthService authService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected PlatformTransactionManager txManager;

    /**
     * Администратор портала для тестов: создаётся при первом обращении,
     * дальше просто приводится к известному состоянию — активен, пароль
     * известен, смена не требуется, доступны все поклажедатели.
     */
    protected PortalUser portalAdmin() {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            String id = jdbc.sql("SELECT user_id::text FROM dbo.app_user WHERE login = :login")
                    .param("login", ADMIN)
                    .query(String.class)
                    .optional()
                    .orElse(null);
            if (id == null) {
                // Приведения ::text обязательны: иначе Postgres видит набор
                // «varchar, unknown, unknown…» и не может выбрать функцию
                id = jdbc.sql("SELECT dbo.admin_create_user(:login::text,"
                                + " 'Тестовый администратор'::text, 'admin'::text,"
                                + " 'Проверка'::text, 'Ahlers Logistics'::text, NULL::text,"
                                + " :hash::text)::text")
                        .param("login", ADMIN)
                        .param("hash", passwordEncoder.encode(ADMIN_PASSWORD))
                        .query(String.class)
                        .single();
            }
            jdbc.sql("SELECT dbo.auth_set_password(:id::uuid, :hash::text, false)")
                    .param("id", id)
                    .param("hash", passwordEncoder.encode(ADMIN_PASSWORD))
                    .query()
                    .listOfRows();
            jdbc.sql("UPDATE dbo.app_user SET is_active = true WHERE user_id = :id::uuid")
                    .param("id", id)
                    .update();
            jdbc.sql("SELECT dbo.admin_set_depositor_access(:id::uuid,"
                            + " (SELECT array_agg(depositor_id) FROM dbo.admin_depositors()))")
                    .param("id", id)
                    .query()
                    .listOfRows();
        });
        return authService.authenticate(ADMIN, ADMIN_PASSWORD);
    }

    /** Идентификатор пользователя по логину — нужен тестам доступов. */
    protected String userId(String login) {
        return jdbc.sql("SELECT user_id::text FROM dbo.app_user WHERE login = :login")
                .param("login", login)
                .query(String.class)
                .single();
    }

    /**
     * Представиться порталом: кто работает и с каким поклажедателем.
     * <p>
     * В приложении эти значения кладёт фильтр из серверной сессии; в тестах
     * выставляем их напрямую — проверяем правила доступа к данным, а не механику входа.
     */
    protected void signInAs(String login, String depositorCode) {
        DepositorContext.set(login, depositorCode == null ? null : depositorId(login, depositorCode));
    }

    /**
     * Идентификатор поклажедателя по коду — через функцию доступов, ту же,
     * которой пользуется вход. Так тест заодно подтверждает, что доступ есть.
     */
    protected String depositorId(String login, String code) {
        if (login == null) {
            throw new IllegalArgumentException(
                    "Укажите пользователя: с этапа 5 поклажедатель берётся из его доступов");
        }
        return new TransactionTemplate(txManager).execute(tx ->
                jdbc.sql("SELECT depositor_id::text FROM dbo.depositors_for_user(:login::text)"
                                + " WHERE code = :code")
                        .param("login", login)
                        .param("code", code)
                        .query(String.class)
                        .single());
    }

    /** Наименование организации поклажедателя — той же функцией доступов. */
    protected String depositorOrgName(String login, String code) {
        return new TransactionTemplate(txManager).execute(tx ->
                jdbc.sql("SELECT org_name FROM dbo.depositors_for_user(:login::text)"
                                + " WHERE code = :code")
                        .param("login", login)
                        .param("code", code)
                        .query(String.class)
                        .single());
    }

    protected void signIn() {
        signInAs(USER, DEPOSITOR);
    }

    @AfterEach
    void clearContext() {
        DepositorContext.clear();
    }

    /** Проверяет, что вызов отклонён с ожидаемым кодом ответа. */
    protected void expectRejection(int httpStatus, Runnable action) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(httpStatus, e.getStatusCode().value(),
                "ожидался код " + httpStatus + ", получено: " + e.getReason());
    }

    /**
     * Задаёт пользователю язык.
     *
     * <p>Вызывать обязательно перед любой проверкой подписи — колонки,
     * поля, сообщения. Пока язык у пользователя не выбран, действует язык
     * портала, а он английский: проверка ждала бы «Коммерческий код»,
     * а получила бы «Commercial code». На этом мы уже дважды спотыкались.
     */
    protected void localeOf(PortalUser user, String code) {
        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                jdbc.sql("SELECT dbo.user_set_locale(:id::uuid, :code::text)")
                        .param("id", user.userId())
                        .param("code", code)
                        .query()
                        .listOfRows());
    }

    /**
     * Чтение из базы напрямую. Таблицы под RLS, поэтому запрос выполняется
     * в транзакции с выставленным контекстом — иначе база честно вернёт ноль строк.
     */
    protected String singleValue(String sql) {
        return new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(USER, DEPOSITOR)).query(String.class).single();
            return jdbc.sql(sql).query(String.class).single();
        });
    }

    /** Изменение в базе под контекстом поклажедателя. */
    protected void inContext(String sql) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(USER, DEPOSITOR)).query(String.class).single();
            jdbc.sql(sql).update();
        });
    }
}
