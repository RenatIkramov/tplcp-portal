package com.ahlers.portal3pl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Страница и скрипты портала: браузер обязан сверяться с сервером.
 *
 * <p>ЧТО БЫЛО. Статика отдавалась без {@code Cache-Control}, только с
 * {@code Last-Modified}. Тогда браузер решает сам, сколько держать файл,
 * не спрашивая сервер, — обычно десятую часть его «возраста». Файлу,
 * не менявшемуся месяц, это три дня. После обновления портала человек
 * получал новую страницу со старыми скриптами: так после V81 у
 * администратора не появилась кнопка загрузки упаковок — прежний
 * {@code import.js} прятал её как заглушку.
 *
 * <p>{@code no-cache} — не «не хранить», а «хранить, но перед
 * использованием спросить»: браузер присылает {@code If-Modified-Since},
 * и неизменный файл возвращается ответом 304 без тела. Цена — один
 * короткий запрос на файл; выигрыш — обновление портала доходит до
 * человека сразу, без Ctrl+F5.
 *
 * <p>Сборщика с хешами в именах файлов у портала нет (решение Р3), поэтому
 * долгого кэша с подменой имени здесь не будет: свежесть обеспечивает
 * сверка.
 */
@Configuration
public class StaticResourcesConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Тот же адрес и то же место, что у обработчика Spring Boot по
        // умолчанию: встретив уже объявленный «/**», он свой не добавляет.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }
}
