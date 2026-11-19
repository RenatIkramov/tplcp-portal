package com.ahlers.portal3pl.files;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Предел размера загрузки на уровне сервера (V145).
 *
 * <p>У Spring по умолчанию — 1 МБ на файл: файл клиента в 5 МБ отказывался
 * ещё до портала, без понятной причины. Здесь — верхняя граница
 * ({@code portal.files.upload-max-mb}, 100 МБ): дальше рабочий предел,
 * который задаёт администратор (до 100 МБ), проверяет служба и объясняет
 * отказ человеку.
 */
@Configuration
public class UploadLimits {

    @Bean
    public MultipartConfigElement multipartConfigElement(@Value("${portal.files.upload-max-mb:100}") long maxMb) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(maxMb));
        factory.setMaxRequestSize(DataSize.ofMegabytes(maxMb * 3));
        return factory.createMultipartConfig();
    }
}
