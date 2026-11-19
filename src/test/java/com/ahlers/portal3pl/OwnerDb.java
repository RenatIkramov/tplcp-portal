package com.ahlers.portal3pl;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подключение владельцем схемы — для подготовки данных в проверках
 * (аудит 11.11.2026, S10; V133).
 *
 * <p>Портал ходит ролью {@code portal_app}, а ей больше нельзя писать в
 * {@code app_user}, доступы и токены «запомнить меня» напрямую — только
 * функциями. Проверкам же нужно ставить учётку в нужное состояние
 * (заблокировать, сменить роль, вернуть пароль), и для этого они ходят
 * владельцем {@code portal_owner} — тем же, под кем Flyway накатывает
 * миграции: адрес, пользователь и пароль берутся из {@code spring.flyway.*}
 * в {@code application.yml}, нового там прописывать не нужно.
 *
 * <p>Это отдельное соединение со своей транзакцией. Важно: строку, которую
 * в открытой транзакции портала уже изменили, владелец будет ждать до её
 * конца — то есть вечно, если ждёт сама проверка. Поэтому запись владельцем
 * — до или после транзакции портала, не внутри.
 *
 * <p>Пустой {@code app.actor}: правки владельцем не попадают в журнал
 * действий (триггеры его пропускают, V116) — это подготовка проверки,
 * а не действие человека.
 */
public final class OwnerDb {

    private static final Map<String, OwnerDb> CACHE = new ConcurrentHashMap<>();

    public final JdbcClient jdbc;
    public final TransactionTemplate tx;

    private OwnerDb(SimpleDriverDataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    static OwnerDb of(Environment env) {
        String url = env.getProperty("spring.flyway.url", env.getProperty("spring.datasource.url"));
        String user = env.getProperty("spring.flyway.user");
        String password = env.getProperty("spring.flyway.password", "");
        if (url == null || user == null) {
            throw new IllegalStateException("В application.yml нет spring.flyway.user — проверкам нужно"
                    + " подключение владельцем схемы portal_owner (см. V132 и README)");
        }
        return CACHE.computeIfAbsent(url + "|" + user, k -> {
            try {
                Driver driver = DriverManager.getDriver(url);
                return new OwnerDb(new SimpleDriverDataSource(driver, url, user, password));
            } catch (SQLException e) {
                throw new IllegalStateException("нет драйвера для " + url, e);
            }
        });
    }
}
