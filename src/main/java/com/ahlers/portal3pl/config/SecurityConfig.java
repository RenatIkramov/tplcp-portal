package com.ahlers.portal3pl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Хеширование паролей.
 * <p>
 * BCrypt выбран потому, что он намеренно медленный и с солью: даже если база
 * утечёт, перебор паролей остаётся дорогим. Стоимость 10 — разумный баланс
 * между временем входа и стойкостью; её можно поднять, старые хеши при этом
 * продолжат проверяться, так как стоимость записана в самом хеше.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
