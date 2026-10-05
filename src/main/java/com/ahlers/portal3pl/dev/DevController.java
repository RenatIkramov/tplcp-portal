package com.ahlers.portal3pl.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Служебные эндпоинты для разработки: посмотреть, какие поклажедатели есть
 * в базе и какие у них идентификаторы, не открывая psql.
 *
 * @ConditionalOnProperty: этот контроллер вообще не создаётся, если
 * в настройках не стоит portal.dev-mode=true. В продакшне его просто нет.
 *
 * Удалить вместе с переходом на аутентификацию (этап 5).
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "portal.dev-mode", havingValue = "true")
public class DevController {

    private final JdbcClient jdbc;

    public DevController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** GET /api/dev/depositors — список поклажедателей с кодами и UUID. */
    @GetMapping("/depositors")
    public List<Map<String, Object>> depositors() {
        return jdbc.sql("SELECT * FROM dbo.dev_all_depositors()")
                .query().listOfRows();
    }

    /** GET /api/dev/users — логины для подстановки в X-User-Login. */
    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return jdbc.sql("SELECT * FROM dbo.dev_all_users()")
                .query().listOfRows();
    }
}
