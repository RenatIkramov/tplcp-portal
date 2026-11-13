package com.ahlers.portal3pl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа. @SpringBootApplication включает автоконфигурацию:
 * Spring сам поднимает веб-сервер, создаёт пул соединений к базе
 * по настройкам из application.yml и находит все классы-компоненты
 * в этом пакете и вложенных.
 *
 * <p>@EnableScheduling — для заданий по расписанию: ночная чистка
 * журнала действий старше срока хранения (AuditService, V117).
 */
@SpringBootApplication
@EnableScheduling
public class PortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
