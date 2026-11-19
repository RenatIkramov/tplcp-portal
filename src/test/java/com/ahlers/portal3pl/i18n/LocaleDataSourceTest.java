package com.ahlers.portal3pl.i18n;

import com.ahlers.portal3pl.config.DepositorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Соединение из пула не уносит чужое имя в журнал (аудит 11.11.2026, A7).
 *
 * <p>НАЙДЕНО АУДИТОМ. Язык и {@code app.actor} пишутся в соединение при
 * выдаче. Если запрос языка падал, в соединении оставались значения
 * прежнего владельца — и правка нового попала бы в журнал под чужим
 * логином. Сбой изображает заведомо неверный запрос языка; соединение —
 * одно и то же физическое, как бывает в пуле.
 */
@SpringBootTest
class LocaleDataSourceTest {

    @Autowired
    DataSource dataSource;

    @AfterEach
    void clear() {
        DepositorContext.clear();
    }

    @Test
    void failedLocaleQueryDoesNotKeepPreviousActor() throws Exception {
        DataSource pool = ((DelegatingDataSource) dataSource).getTargetDataSource();
        try (Connection physical = pool.getConnection()) {
            try (Statement st = physical.createStatement()) {
                st.execute("SELECT set_config('app.actor', 'previous.user@example.com', false),"
                        + " set_config('app.locale', 'xx', false)");
            }
            DataSource same = new SingleConnectionDataSource(physical, true);
            DataSource broken = new LocaleDataSource.Wrapper(same, "SELECT dbo.no_such_function(?, ?)");

            DepositorContext.set("next.user@example.com", null);
            Connection handed = broken.getConnection();
            assertEquals("next.user@example.com", setting(handed, "app.actor"),
                    "соединение выдано с логином прежнего владельца");
            assertEquals("", setting(handed, "app.locale"), "остался язык прежнего владельца");
        }
    }

    private static String setting(Connection c, String name) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT current_setting('" + name + "', true)")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
