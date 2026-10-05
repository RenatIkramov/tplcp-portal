package com.ahlers.portal3pl.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Фильтр — код, выполняющийся до контроллера на каждом запросе.
 * Достаёт пользователя и выбранного поклажедателя из заголовков
 * (а в dev-режиме — и из параметра запроса) и кладёт в DepositorContext.
 * После обработки обязательно очищает.
 *
 * Очистка критична: потоки в пуле переиспользуются, и забытое значение
 * утекло бы в следующий запрос другого пользователя.
 */
@Component
public class DepositorFilter implements Filter {

    public static final String DEPOSITOR_ID_HEADER = "X-Depositor-Id";
    public static final String DEPOSITOR_CODE_HEADER = "X-Depositor-Code";
    public static final String USER_HEADER = "X-User-Login";

    /** Параметр запроса для dev-режима: /api/stock?depositorCode=D-001 */
    public static final String DEPOSITOR_CODE_PARAM = "depositorCode";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest http) {
                String code = http.getHeader(DEPOSITOR_CODE_HEADER);
                if (code == null || code.isBlank()) {
                    code = http.getParameter(DEPOSITOR_CODE_PARAM);
                }
                DepositorContext.set(
                        http.getHeader(USER_HEADER),
                        http.getHeader(DEPOSITOR_ID_HEADER),
                        code);
            }
            chain.doFilter(request, response);
        } finally {
            DepositorContext.clear();
        }
    }
}
