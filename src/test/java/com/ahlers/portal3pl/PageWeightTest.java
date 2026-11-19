package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Страница портала — без картинок внутри и без вписанных демо-данных
 * (аудит 11.11.2026, A2, D1; V124).
 *
 * <p>НАЙДЕНО АУДИТОМ. {@code index.html} весил 2,4 МБ, из них 2,3 МБ —
 * восемь картинок страницы «О нас» в base64. Их грузил каждый, и на форме
 * входа тоже, хотя ссылка «О нас» давно ведёт на сайт оператора. В
 * «Контактах» были вписаны реквизиты, склады и менеджеры с телефонами —
 * contacts.js их перерисовывал, но в отданной странице они оставались.
 *
 * <p>Проверка читает файл, а не страницу в браузере: важно, что уходит
 * по сети, а не что видно после скриптов.
 */
class PageWeightTest {

    private static final Path INDEX = Path.of("src", "main", "resources", "static", "index.html");

    /** Предел с запасом: сама разметка весит около 100 КБ. */
    private static final long MAX_BYTES = 300 * 1024;

    @Test
    void pageIsLightAndHasNoEmbeddedImages() throws IOException {
        long size = Files.size(INDEX);
        assertTrue(size < MAX_BYTES, "index.html весит " + size / 1024 + " КБ — картинки снова внутри?");
        assertFalse(html().contains("data:image"), "картинка вписана в разметку base64");
    }

    /** Демо-данные, которые экран всё равно перерисовывал, в отданной странице не нужны. */
    @Test
    void noHardcodedContactsOrDemoScreens() throws IOException {
        String html = html();
        for (String leftover : List.of("view-about", "view-admin-access-edit", "feedback@",
                "Написать нам", "n.nazarbayev@", "s.akhmetova@", "8 905 895 95 85", "150840009312")) {
            assertFalse(html.contains(leftover), "в разметке остался демо-след: " + leftover);
        }
        Matcher manager = Pattern.compile("id=\"managerLine\">([^<]*)<").matcher(html);
        assertTrue(manager.find() && manager.group(1).isBlank(),
                "в строке менеджера вписано имя — его ставит вход");
    }

    private static String html() throws IOException {
        return Files.readString(INDEX, StandardCharsets.UTF_8);
    }
}
