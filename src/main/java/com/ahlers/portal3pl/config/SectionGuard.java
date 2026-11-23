package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.common.Fail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Скрытый раздел закрыт и адресом, а не только кнопкой (аудит
 * 11.11.2026, S3; ТЗ §6.3 требует проверки на сервере).
 *
 * <pre>
 *   /api/transfers/**                      скрыт раздел «Перемещения» у поклажедателя
 *   /api/invoices/**, /api/service-tariffs/**   скрыт «Биллинг» у пользователя
 * </pre>
 *
 * <p>ЧТО БЫЛО. Галочки видимости разделов прятали вкладку на экране,
 * а сервер отвечал по-прежнему: список перемещений и счета отдавались
 * любому, кто наберёт адрес.
 *
 * <p>Правило одно для всех ролей — экран тоже прячет раздел всем.
 * Перехватчик стоит после фильтра входа: пользователь и поклажедатель
 * уже в контексте запроса. Без входа сюда не доходит — фильтр ответил
 * 401 раньше.
 */
@Configuration
public class SectionGuard implements WebMvcConfigurer {

    private final CurrentUser current;

    public SectionGuard(CurrentUser current) {
        this.current = current;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull Object handler) {
                if (current.transfersHidden()) {
                    throw Fail.forbidden("error.access.section_hidden");
                }
                return true;
            }
        }).addPathPatterns("/api/transfers", "/api/transfers/**");

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull Object handler) {
                if (current.billingHidden()) {
                    throw Fail.forbidden("error.access.section_hidden");
                }
                return true;
            }
        }).addPathPatterns("/api/invoices", "/api/invoices/**",
                "/api/service-tariffs", "/api/service-tariffs/**");
    }
}
