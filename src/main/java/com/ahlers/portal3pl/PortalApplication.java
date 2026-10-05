package com.ahlers.portal3pl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа. @SpringBootApplication включает автоконфигурацию:
 * Spring сам поднимает веб-сервер, создаёт пул соединений к базе
 * по настройкам из application.yml и находит все классы-компоненты
 * в этом пакете и вложенных.
 */
@SpringBootApplication
public class PortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
