package com.ahlers.portal3pl.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Защитные заголовки на каждый ответ портала (аудит 11.11.2026, S7).
 *
 * <p>ЧТО БЫЛО. Ответ нёс только {@code Cache-Control} и тип содержимого.
 * Страницу портала можно было вставить в чужой сайт во фрейме (и
 * подсовывать кнопки поверх настоящих), браузер угадывал тип файлов
 * сам, а адрес портала с параметрами уходил в заголовке Referer на
 * любой внешний сайт по ссылке.
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — тип файла не угадывать;</li>
 *   <li>{@code Content-Security-Policy: frame-ancestors 'none'} и
 *       {@code X-Frame-Options: DENY} — портал не встраивается во фреймы
 *       (второй — для старых браузеров);</li>
 *   <li>{@code Referrer-Policy: same-origin} — адрес портала не уходит
 *       наружу;</li>
 *   <li>{@code Strict-Transport-Security} — только если запрос пришёл по
 *       HTTPS: по HTTP браузер его всё равно не примет, а на машине
 *       разработчика он закрыл бы http://localhost на год.</li>
 * </ul>
 *
 * <p>{@code Content-Security-Policy} — полный (аудит A3, 14.11.2026):
 * скрипты только файлами с самого портала, без встроенных и без
 * обработчиков строкой ({@code onclick="…"}): их больше нет ни в разметке,
 * ни в собираемом коде (JsHelpersTest). Даже если в страницу попадёт
 * чужая разметка, её скрипт браузер не выполнит. Стили встроенные
 * разрешены — в разметке их сотни, и выполнить через них ничего нельзя.
 * Картинки — с портала, {@code data:} и {@code blob:} (предпросмотр);
 * когда фото документов поедут с хранилища файлов (S3), его адрес
 * дописать в {@code img-src}.
 *
 * <p>Если перед порталом встанет обратный прокси со своими заголовками,
 * эти не мешают: значения те же. За прокси HTTPS видно портала только
 * после {@code server.forward-headers-strategy} (application-prod.yml).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    static final long HSTS_SECONDS = 365L * 24 * 60 * 60;

    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("X-Content-Type-Options", "nosniff");
        http.setHeader("X-Frame-Options", "DENY");
        http.setHeader("Content-Security-Policy", CSP);
        http.setHeader("Referrer-Policy", "same-origin");
        if (request.isSecure()) {
            http.setHeader("Strict-Transport-Security", "max-age=" + HSTS_SECONDS);
        }
        chain.doFilter(request, response);
    }
}
