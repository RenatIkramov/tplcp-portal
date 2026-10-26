package com.ahlers.portal3pl.browser;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Переключатель языка видно на всех трёх фонах, где он стоит.
 *
 * <p>ПОЧЕМУ ЭТО ОТДЕЛЬНАЯ ПРОВЕРКА. Переключатель стоит в трёх местах
 * с разным фоном: тёмная шапка портала, белая карточка входа, белое
 * окно смены пароля. Правило цвета было написано под шапку — белый
 * текст на светлой подложке, — и на карточке входа и в окне пароля
 * выходило белым по белому. Заказчик так и сказал: «кнопки смены
 * языка нет ни на экране смены пароля, ни на экране логина». Она
 * была: нарисована, занимает место, отвечает на щелчок — и не видна.
 *
 * <p>ПОЧЕМУ СМОТРИМ ПИКСЕЛИ, А НЕ СТИЛИ. Соседняя проверка спрашивала,
 * есть ли элемент, — и проходила, пока человек смотрел на пустое
 * место. Следующая считала контраст по вычисленным стилям — и упала
 * на шапке: фон там градиент, а у градиента background-color
 * прозрачный, так что «фоном» оказывался белый body, и белый текст
 * на тёмной шапке выглядел невидимым. Обе меры говорили не о том.
 *
 * <p>Поэтому снимок самой кнопки и разброс яркости по его точкам.
 * Это ровно то, что видит человек: есть на картинке и светлое,
 * и тёмное — надпись читается; всё одного тона — перед нами пустое
 * место, какими бы ни были стили и каким бы способом ни был нарисован
 * фон.
 */
class LanguageSwitchTest extends BrowserTestBase {

    /**
     * Ниже этого на картинке нет ни надписи, ни рамки — один тон.
     * Яркость считается от 0 до 1, у белого по белому разброс около
     * нуля, у обычной надписи — больше половины. Порог низкий
     * нарочно: задача не оценивать вёрстку, а поймать «не видно».
     */
    private static final double MIN_SPREAD = 0.20;

    @Test
    void switchIsReadableOnTheLoginCard() {
        page.navigate(baseUrl() + "/");
        page.waitForSelector("#loginLang .lang-current");

        assertTrue(page.isVisible("#loginLang .lang-current"),
                "на форме входа переключатель языка не виден");
        double spread = spread("#loginLang .lang-current");
        assertTrue(spread >= MIN_SPREAD,
                "на форме входа переключатель языка сливается с карточкой:"
                        + " разброс яркости " + round(spread));
        assertNoJsErrors();
    }

    @Test
    void switchIsReadableInTheHeaderAndInThePasswordDialog() {
        ensurePassword(USER, USER_PASSWORD);
        signInUiAsClient();

        page.waitForSelector(".header-utility .lang .lang-current");
        double header = spread(".header-utility .lang .lang-current");
        assertTrue(header >= MIN_SPREAD,
                "в шапке портала переключатель языка сливается с фоном:"
                        + " разброс яркости " + round(header));

        page.evaluate("() => portalOpenPasswordChange()");
        page.waitForSelector("#pwOverlay.active");
        assertTrue(page.isVisible("#pwLang .lang-current"),
                "в окне смены пароля переключатель языка не виден");
        double dialog = spread("#pwLang .lang-current");
        assertTrue(dialog >= MIN_SPREAD,
                "в окне смены пароля переключатель языка сливается с панелью:"
                        + " разброс яркости " + round(dialog));
        assertNoJsErrors();
    }

    /* ---------------- помощники ---------------- */

    /**
     * Разброс яркости по точкам снимка кнопки: самая светлая минус
     * самая тёмная.
     *
     * <p>Снимок берётся у самого элемента, а не у страницы: нас
     * интересует кнопка, а не то, что вокруг неё.
     */
    private double spread(String selector) {
        Locator chip = page.locator(selector).first();
        byte[] png = chip.screenshot();
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(image != null && image.getWidth() > 0 && image.getHeight() > 0,
                "снимок кнопки языка пуст — проверять нечего");

        double min = 1.0;
        double max = 0.0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                // та же яркость, что в правилах доступности: зелёный
                // человек видит заметно лучше синего
                double l = (0.2126 * ((rgb >> 16) & 0xFF)
                        + 0.7152 * ((rgb >> 8) & 0xFF)
                        + 0.0722 * (rgb & 0xFF)) / 255.0;
                min = Math.min(min, l);
                max = Math.max(max, l);
            }
        }
        return max - min;
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 100) / 100.0);
    }
}
