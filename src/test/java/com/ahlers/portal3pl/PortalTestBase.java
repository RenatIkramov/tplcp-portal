package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.AuthService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.config.DepositorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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

    @Autowired
    private Environment environment;

    /**
     * Владелец схемы — писать в {@code app_user} и доступы, куда портал
     * больше не пишет напрямую (V133). Не внутри транзакции портала,
     * см. {@link OwnerDb}.
     */
    protected OwnerDb owner() {
        return OwnerDb.of(environment);
    }

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
            jdbc.sql("SELECT dbo.admin_set_depositor_access(:id::uuid,"
                            + " (SELECT array_agg(depositor_id) FROM dbo.admin_depositors()))")
                    .param("id", id)
                    .query()
                    .listOfRows();
        });
        // «Все поклажедатели» — признаком, а не только списком: на чистой
        // базе учётка создаётся без признака, и проверки, которые на него
        // опираются (ProfileTest), зависели бы от того, правили ли её руками.
        // Владельцем и после транзакции: учётку она могла только что создать.
        owner().jdbc.sql("UPDATE dbo.app_user SET is_active = true, all_depositors = true WHERE login = :login")
                .param("login", ADMIN)
                .update();
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

    /**
     * Шапка черновика с номером ERP: без него черновик не сохраняется,
     * а номер не должен повторяться (V98). Свой номер — у каждого вызова.
     */
    protected static Map<String, Object> withErp(Map<String, Object> header) {
        Map<String, Object> out = new java.util.LinkedHashMap<>(header);
        out.putIfAbsent("erp_number", uniqueErp());
        return out;
    }

    /** Номер ERP, который заведомо ещё не занят. */
    protected static String uniqueErp() {
        return "QA-" + java.util.UUID.randomUUID().toString().substring(0, 12);
    }

    protected void signIn() {
        signInAs(USER, DEPOSITOR);
    }

    @AfterEach
    void clearContext() {
        DepositorContext.clear();
    }

    /**
     * Личные настройки клиентов, под которыми ходят тесты: запомнить,
     * начать с известного состояния, после теста вернуть как были.
     * Под этими же учётками заказчик проверяет портал руками —
     * подробности в {@link PersonalSettings}.
     * <p>
     * Методы базового класса JUnit вызывает раньше {@code @BeforeEach}
     * наследника и позже его {@code @AfterEach}: снимок снят до любой
     * подготовки теста, а возврат идёт после любой его уборки.
     */
    private PersonalSettings personal;

    /** Хэш тестового пароля — один на прогон: шифровать его на каждый тест долго. */
    private static String testPasswordHash;

    @Autowired
    protected com.ahlers.portal3pl.auth.LoginThrottle loginThrottle;

    /**
     * Счёт неудачных входов по адресу — с нуля (V123). Все проверки входят
     * с одного адреса и часть из них ошибается намеренно: без этого
     * двадцатая чужая неудача закрыла бы вход следующей проверке.
     */
    @BeforeEach
    void forgetLoginFailures() {
        loginThrottle.forgetAll();
    }

    @BeforeEach
    void rememberPersonalSettings() {
        if (testPasswordHash == null) {
            testPasswordHash = passwordEncoder.encode(USER_PASSWORD);
        }
        personal = new PersonalSettings(owner(), List.of(USER, OTHER_USER),
                testPasswordHash).remember();
        personal.startClean();
    }

    @AfterEach
    void restorePersonalSettings() {
        if (personal != null) {
            personal.restore();
        }
    }

    /** Чем вернуть раздел «Перемещения» клиентам тестов, как был до теста. */
    private final List<Runnable> transfersSection = new java.util.ArrayList<>();

    /**
     * Раздел «Перемещения» у клиентов тестов — показан на время теста.
     *
     * <p>ЗАЧЕМ (аудит S3, 11.11.2026). С тех пор как скрытый раздел закрыт
     * и на сервере (SectionGuard), галочка «скрыть перемещения», которую
     * человек поставил «Рогам и Копытам» для живой проверки, роняла бы
     * все проверки перемещений отказом 403. Проверка не должна зависеть
     * от ручных настроек: раздел открывается до теста, а после — та же
     * галочка возвращается, как была. Пишется, только если раздел скрыт.
     */
    @BeforeEach
    void showTransfersSection() {
        transfersSection.clear();
        for (String[] who : new String[][]{{USER, DEPOSITOR}, {OTHER_USER, OTHER_DEPOSITOR}}) {
            String id = depositorId(who[0], who[1]);
            Map<String, Object> was = jdbc.sql("SELECT hide_transfers_tab, price_in_receipts, price_in_orders"
                            + " FROM dbo.depositor_settings_of(:id::uuid)")
                    .param("id", id).query().listOfRows().stream().findFirst().orElse(Map.of());
            if (!Boolean.TRUE.equals(was.get("hide_transfers_tab"))) {
                continue;
            }
            boolean receipts = Boolean.TRUE.equals(was.get("price_in_receipts"));
            boolean orders = Boolean.TRUE.equals(was.get("price_in_orders"));
            saveDepositorSettings(id, false, receipts, orders);
            transfersSection.add(() -> {
                // цены тест мог поменять сам и вернуть сам — возвращаем только галочку раздела
                Map<String, Object> now = jdbc.sql("SELECT price_in_receipts, price_in_orders"
                                + " FROM dbo.depositor_settings_of(:id::uuid)")
                        .param("id", id).query().listOfRows().stream().findFirst().orElse(Map.of());
                saveDepositorSettings(id, true, Boolean.TRUE.equals(now.get("price_in_receipts")),
                        Boolean.TRUE.equals(now.get("price_in_orders")));
            });
        }
    }

    @AfterEach
    void restoreTransfersSection() {
        transfersSection.forEach(Runnable::run);
        transfersSection.clear();
    }

    private void saveDepositorSettings(String depositorId, boolean hideTransfers,
                                       boolean priceInReceipts, boolean priceInOrders) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId).query(String.class).single();
            jdbc.sql("SELECT dbo.admin_save_depositor_settings(:id::uuid,"
                            + " :h::boolean, :r::boolean, :o::boolean)")
                    .param("id", depositorId)
                    .param("h", hideTransfers)
                    .param("r", priceInReceipts)
                    .param("o", priceInOrders)
                    .query().listOfRows();
        });
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

    /**
     * Строки из ответа списка документов.
     *
     * <p>Имя не {@code rowsOf}: так называется помощник в проверках
     * загрузки Excel, и у него свой предмет — строки разобранного
     * файла. Два одноимённых помощника с разным смыслом, один
     * в основе проверок, другой рядом, — это ловушка, а не удобство.
     *
     * <p>Список отдаёт конверт {@code {rows, total, page, pageSize}},
     * а не массив: страницу режет сервер (V78), и вместе со строками
     * экрану нужно, сколько всего строк нашлось.
     */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> listRows(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("rows");
    }

    /**
     * Сколько строк нашлось всего — по всему отбору, а не на странице.
     *
     * <p>Проверки «фильтр сузил список» должны сравнивать именно это:
     * число строк на странице ограничено её размером и о величине
     * отбора не говорит ничего.
     */
    protected int listTotal(Map<String, Object> answer) {
        return ((Number) answer.get("total")).intValue();
    }

    /** Изменение в базе под контекстом поклажедателя. */
    protected void inContext(String sql) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(USER, DEPOSITOR)).query(String.class).single();
            jdbc.sql(sql).update();
        });
    }

    /**
     * Запомнить всё, что администратор настроил поклажедателям в «Доступах»,
     * и вернуть функцию, которая вернёт это ровно как было: колонки всех
     * таблиц, переименованные поля и галочки карточки клиента.
     * <p>
     * Для тестов, которые сбрасывают настройки к портальным: раньше они
     * после себя оставляли «как у портала» и стирали то, что человек
     * настроил «Рогам и Копытам» для живой проверки (03.11.2026).
     * Таблицы под FORCE ROW LEVEL SECURITY, поэтому чтение и запись — под
     * контекстом каждого поклажедателя.
     */
    protected Runnable keepDepositorSetup(String... depositorCodes) {
        portalAdmin();
        List<Runnable> restores = new java.util.ArrayList<>();
        for (String code : depositorCodes) {
            restores.add(keepDepositorSetupOf(depositorId(ADMIN, code)));
        }
        return () -> restores.forEach(Runnable::run);
    }

    /**
     * Запомнить настройки «Доступов» поклажедателей и начать тест
     * с портальных: колонки как у портала, переименований полей нет,
     * галочки карточки клиента сняты. Возвращает функцию, которая вернёт
     * настройки ровно как были.
     * <p>
     * Сбрасывается всё, а не только то, что трогает тест: иначе проверка
     * зависела бы от того, что человек настроил для живой проверки
     * (переименованное поле — лишняя подпись в справочнике полей).
     */
    protected Runnable startFromPortalSetup(String... depositorCodes) {
        Runnable restore = keepDepositorSetup(depositorCodes);
        for (String code : depositorCodes) {
            String depositorId = depositorId(ADMIN, code);
            new TransactionTemplate(txManager).executeWithoutResult(tx -> {
                jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                        .param("id", depositorId).query(String.class).single();
                jdbc.sql("DELETE FROM dbo.depositor_table_column"
                        + " WHERE depositor_id = dbo.current_depositor_id()").update();
                jdbc.sql("DELETE FROM dbo.depositor_field_label"
                        + " WHERE depositor_id = dbo.current_depositor_id()").update();
                jdbc.sql("SELECT dbo.admin_save_depositor_settings(:id::uuid, false, false, false)")
                        .param("id", depositorId).query().listOfRows();
            });
        }
        return restore;
    }

    private Runnable keepDepositorSetupOf(String depositorId) {
        record Saved(List<Map<String, Object>> columns,
                     List<Map<String, Object>> labels,
                     Map<String, Object> settings) {
        }
        Saved saved = new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId).query(String.class).single();
            return new Saved(
                    jdbc.sql("SELECT table_key, column_id, is_available, is_default, sort_order"
                            + " FROM dbo.depositor_table_column"
                            + " WHERE depositor_id = dbo.current_depositor_id()").query().listOfRows(),
                    jdbc.sql("SELECT object_type_id, field_id, custom_label"
                            + " FROM dbo.depositor_field_label"
                            + " WHERE depositor_id = dbo.current_depositor_id()").query().listOfRows(),
                    jdbc.sql("SELECT hide_transfers_tab, price_in_receipts, price_in_orders"
                                    + " FROM dbo.depositor_settings_of(:id::uuid)")
                            .param("id", depositorId).query().listOfRows()
                            .stream().findFirst().orElse(Map.of()));
        });
        return () -> new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId).query(String.class).single();
            jdbc.sql("DELETE FROM dbo.depositor_table_column"
                    + " WHERE depositor_id = dbo.current_depositor_id()").update();
            for (Map<String, Object> r : saved.columns()) {
                jdbc.sql("INSERT INTO dbo.depositor_table_column"
                                + " (depositor_id, table_key, column_id, is_available, is_default, sort_order)"
                                + " VALUES (dbo.current_depositor_id(), :t, :c, :a, :d, :o)")
                        .param("t", r.get("table_key"))
                        .param("c", r.get("column_id"))
                        .param("a", r.get("is_available"))
                        .param("d", r.get("is_default"))
                        .param("o", r.get("sort_order"))
                        .update();
            }
            jdbc.sql("DELETE FROM dbo.depositor_field_label"
                    + " WHERE depositor_id = dbo.current_depositor_id()").update();
            for (Map<String, Object> r : saved.labels()) {
                jdbc.sql("INSERT INTO dbo.depositor_field_label"
                                + " (depositor_id, object_type_id, field_id, custom_label)"
                                + " VALUES (dbo.current_depositor_id(), :o, :f, :l)")
                        .param("o", r.get("object_type_id"))
                        .param("f", r.get("field_id"))
                        .param("l", r.get("custom_label"))
                        .update();
            }
            Map<String, Object> s = saved.settings();
            jdbc.sql("SELECT dbo.admin_save_depositor_settings(:id::uuid,"
                            + " :h::boolean, :r::boolean, :o::boolean)")
                    .param("id", depositorId)
                    .param("h", Boolean.TRUE.equals(s.get("hide_transfers_tab")))
                    .param("r", Boolean.TRUE.equals(s.get("price_in_receipts")))
                    .param("o", Boolean.TRUE.equals(s.get("price_in_orders")))
                    .query().listOfRows();
        });
    }

    /**
     * Запрос под контекстом поклажедателя «Рога и Копыта» — так, как его
     * видит экран. Без контекста {@code table_columns} отдаёт и колонки,
     * закрытые поклажедателю, и подписи без его переименований, — и
     * проверки ругались на правильный экран, стоило настроить
     * «Доступы» для живой проверки (03.11.2026).
     */
    protected <T> T inDepositorContext(java.util.function.Supplier<T> query) {
        return new TransactionTemplate(txManager).execute(tx -> {
            jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                    .param("id", depositorId(USER, DEPOSITOR)).query(String.class).single();
            return query.get();
        });
    }

    /** Подпись колонки так, как её видит клиент: с переименованием поклажедателя. */
    protected String columnLabel(String table, String columnId) {
        return inDepositorContext(() -> jdbc
                .sql("SELECT label FROM dbo.table_columns(:user::uuid, 'ru'::text, :table::text)"
                        + " WHERE column_id = :column::text")
                .param("user", userId(USER))
                .param("table", table)
                .param("column", columnId)
                .query(String.class)
                .single());
    }
}
