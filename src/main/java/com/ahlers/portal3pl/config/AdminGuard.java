package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Всё под {@code /api/admin/} — только администратору, и проверка стоит
 * до метода контроллера (аудит 11.11.2026, S2).
 *
 * <p>ЧТО БЫЛО. Право проверял каждый метод сам, первой строкой. Это
 * держалось, пока методов было мало: забыть строку в новом — и раздел
 * открыт всем вошедшим. И порядок был не тот: Spring сперва разбирает
 * параметры, и запрос без обязательного параметра падал раньше
 * проверки — клиент получал 500 вместо 403 и узнавал, что адрес есть.
 *
 * <p>Проверки в методах остаются: перехватчик — второй замок, а не
 * замена первого. Адрес Spring сопоставляет уже разобранный, поэтому
 * {@code /%61pi/admin/...} сюда тоже попадает.
 *
 * <p>Отказ — исключением: его превращает в ответ общий
 * {@link ApiErrorHandler}, с текстом на языке вошедшего.
 */
@Configuration
public class AdminGuard implements WebMvcConfigurer, HandlerInterceptor {

    private final SessionUser session;
    private final AdminUserService users;

    public AdminGuard(SessionUser session, AdminUserService users) {
        this.session = session;
        this.users = users;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        PortalUser user = session.requireUser(request);
        users.requireAdmin(user);
        return true;
    }
}
