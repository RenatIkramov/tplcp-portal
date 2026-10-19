package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.PortalTestBase;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Основа проверок через браузер.
 *
 * <p>ЗАЧЕМ ОНИ НУЖНЫ. Обычные тесты ходят в сервисы напрямую и об экране
 * не знают ничего. Между сервисом и экраном лежит слой, который ломался
 * уже не раз и каждый раз незаметно: переключение языка затирало
 * клиентское название колонки; «всегда брать подпись сервера» убрало
 * из карточки товара обязательные поля; ключ без текста выдавал человеку
 * {@code error.sku.not_found}. Ни одну из этих поломок не видно
 * из сервисного теста — их видно только на отрисованной странице.
 *
 * <p>КАК УСТРОЕНО. Spring Boot поднимает портал на свободном порту,
 * Playwright открывает его настоящим Chromium. Никаких заглушек: сервер
 * настоящий, база настоящая, тот же путь, которым ходит человек.
 *
 * <p>ПЕРВЫЙ ЗАПУСК скачивает Chromium (~130 МБ) в кэш Playwright. Если
 * сети нет или скачивание запрещено, проверки не падают, а помечаются
 * пропущенными: сборка на машине без браузера не должна краснеть
 * из-за отсутствия браузера. Пропуск виден в отчёте — это не тихое
 * «всё хорошо».
 *
 * <p>НАВИГАЦИЯ. Переходы делаются вызовом собственных функций портала
 * ({@code showTab}, {@code showReferencesSub}), а не щелчками по кнопкам.
 * Это не лень: щелчок по кнопке проверяет вёрстку кнопки, а нам нужно
 * проверить экран, на который она ведёт. Кнопки меняются чаще экранов,
 * и проверка, падающая от переноса кнопки, перестаёт приносить пользу.
 *
 * <p>Исключить из прогона: {@code mvn test -DexcludedGroups=browser}.
 */
@Tag("browser")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BrowserTestBase extends PortalTestBase {

    /**
     * Порт, который выдал Spring. Берём свойством, а не аннотацией
     * {@code @LocalServerPort}: свойство называется так же во всех
     * версиях Spring Boot, а пакет аннотации при обновлениях переезжал.
     */
    @Value("${local.server.port}")
    protected int port;

    private static Playwright playwright;
    private static Browser browser;

    protected BrowserContext context;
    protected Page page;

    /** Ошибки, которые страница написала в консоль за время проверки. */
    private final List<String> jsErrors = new ArrayList<>();

    /** Запросы, на которые сервер ответил отказом или ошибкой. */
    private final List<String> refused = new ArrayList<>();

    /** Из них те, что сломались на сервере (500 и выше). */
    private final List<String> broken = new ArrayList<>();

    @BeforeAll
    static void startBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(!Boolean.parseBoolean(System.getProperty("portal.browser.headed"))));
        } catch (RuntimeException e) {
            // Нет браузера — проверка не выполнена. Это пропуск, не успех.
            Assumptions.abort("Playwright не смог запустить Chromium: " + e.getMessage()
                    + ". Первый запуск скачивает браузер — нужна сеть."
                    + " Исключить проверки: mvn test -DexcludedGroups=browser");
        }
    }

    @AfterAll
    static void stopBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
        page = context.newPage();
        page.setDefaultTimeout(15_000);
        jsErrors.clear();
        refused.clear();
        broken.clear();
        page.onConsoleMessage(this::rememberIfError);
        page.onPageError(text -> jsErrors.add("pageerror: " + text));
        page.onResponse(response -> {
            if (response.status() < 400) {
                return;
            }
            String line = response.status() + " " + response.request().method()
                    + " " + response.url();
            refused.add(line);
            if (response.status() >= 500) {
                broken.add(line);
            }
        });
    }

    private void rememberIfError(ConsoleMessage message) {
        if (!"error".equals(message.type())) {
            return;
        }
        // Значок страницы портал не отдаёт, и браузер пишет об этом
        // ошибкой. К интерфейсу это отношения не имеет.
        if (message.text().contains("favicon")) {
            return;
        }
        // «Failed to load resource» — это не ошибка кода, а пересказ кода
        // ответа сервера: 401 до входа, 403 на разделы, которых этому
        // пользователю не видно. Отказ — штатный ответ портала, и ронять
        // из-за него проверку нельзя. За сломанными ответами (500 и выше)
        // следим отдельно, по самим ответам, а не по их пересказу.
        if (message.text().startsWith("Failed to load resource")) {
            return;
        }
        jsErrors.add("console: " + message.text());
    }

    /**
     * Снимок экрана после каждой проверки — и когда она прошла, и когда нет.
     *
     * <p>Снимок прошедшей проверки тоже нужен: по нему видно, что
     * проверка смотрела на тот экран, на который мы думаем, а не
     * на пустую страницу, где «ничего лишнего» выполняется само собой.
     */
    @AfterEach
    void closePage(TestInfo info) {
        if (page != null) {
            try {
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(shotPath(info))
                        .setFullPage(true));
            } catch (RuntimeException e) {
                // снимок — удобство, из-за него проверка падать не должна
            }
            writeRefused(info);
        }
        if (context != null) {
            context.close();
            context = null;
            page = null;
        }
    }

    /**
     * Список отказов рядом со снимком.
     *
     * <p>Отказ сам по себе не ошибка, но полезно видеть, чего экран
     * просил и чего ему не дали: лишний запрос к разделу, который этому
     * человеку не положен, — повод поправить экран, а не сервер.
     */
    private void writeRefused(TestInfo info) {
        try {
            Path file = Paths.get(shotPath(info).toString().replace(".png", ".http.txt"));
            Files.createDirectories(file.getParent());
            Files.writeString(file, refused.isEmpty()
                    ? "отказов не было\n"
                    : String.join("\n", refused) + "\n");
        } catch (IOException | RuntimeException e) {
            // ведение списка — удобство, проверку оно ронять не должно
        }
    }

    private Path shotPath(TestInfo info) {
        String name = info.getTestClass().map(Class::getSimpleName).orElse("browser")
                + "." + info.getTestMethod().map(Method::getName).orElse("check") + ".png";
        return Paths.get("target", "verify", name);
    }

    /* ----------------------------- вход ----------------------------- */

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Приводит пароль пользователя к известному и снимает требование
     * его сменить — иначе портал встретит проверку экраном смены пароля.
     */
    protected void ensurePassword(String login, String password) {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            String id = userId(login);
            jdbc.sql("SELECT dbo.auth_set_password(:id::uuid, :hash::text, false)")
                    .param("id", id)
                    .param("hash", passwordEncoder.encode(password))
                    .query()
                    .listOfRows();
            jdbc.sql("UPDATE dbo.app_user SET is_active = true WHERE user_id = :id::uuid")
                    .param("id", id)
                    .update();
        });
    }

    /** Вход через настоящую форму входа, как это делает человек. */
    protected void signInUi(String login, String password) {
        ensurePassword(login, password);
        page.navigate(baseUrl() + "/");
        page.fill("#loginEmail", login);
        page.fill("#loginPassword", password);
        page.click("#loginForm button[type=submit]");
        // Оболочка портала появляется после /api/me и загрузки текстов
        page.waitForFunction("() => { const s = document.getElementById('appShell');"
                + " return s && s.style.display !== 'none'; }");
    }

    /** Вход клиентом «Рога и Копыта» — у него один поклажедатель, выбор не нужен. */
    protected void signInUiAsClient() {
        signInUi(USER, USER_PASSWORD);
    }

    /* --------------------------- переходы --------------------------- */

    /**
     * Ждёт, пока справочник колонок доедет до экрана.
     *
     * <p>{@code PORTAL.columns.all} — объект «таблица → строки», а не
     * список: ключи в нём появляются по мере того, как сервер отдаёт
     * раскладку. Поэтому ждём появления ключей, а не длины массива.
     */
    protected void waitForColumns() {
        page.waitForFunction("() => window.PORTAL && PORTAL.columns && PORTAL.columns.all"
                + " && Object.keys(PORTAL.columns.all).length > 0");
    }

    /** То же для конкретной таблицы: sku, orders, receipts, stock. */
    protected void waitForColumns(String tableKey) {
        page.waitForFunction("key => window.PORTAL && PORTAL.columns && PORTAL.columns.all"
                + " && Array.isArray(PORTAL.columns.all[key])"
                + " && PORTAL.columns.all[key].length > 0", tableKey);
    }

    /**
     * Открывает справочник товаров и ждёт, пока список доедет.
     *
     * <p>Карточка товара открывается из уже загруженного списка
     * ({@code portalOpenSku} ищет строку в {@code PORTAL.catalog.sku}),
     * поэтому сразу после входа её открыть нельзя — списка ещё нет.
     */
    protected void openSkuList() {
        openReferences("sku");
        page.waitForFunction("() => window.PORTAL && PORTAL.catalog"
                + " && Array.isArray(PORTAL.catalog.sku) && PORTAL.catalog.sku.length > 0");
        waitForColumns("sku");
    }

    protected void openReferences(String subTab) {
        page.evaluate("sub => { showTab('references'); showReferencesSub(sub); }", subTab);
    }

    /** Текст заголовков таблицы: id строки заголовков, например skuHead. */
    protected List<String> headers(String headRowId) {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) page.evaluate(
                "id => Array.from(document.querySelectorAll('#' + id + ' th'))"
                        + ".map(th => th.textContent.trim())", headRowId);
        return out;
    }

    /** Весь видимый текст страницы — для проверок «на экране нет ключей». */
    protected String visibleText() {
        return (String) page.evaluate("() => document.body.innerText");
    }

    /* --------------------------- проверки --------------------------- */

    /** Запросы, на которые сервер ответил 400 и выше: «код метод адрес». */
    protected List<String> refusedRequests() {
        return List.copyOf(refused);
    }

    /**
     * Страница не должна ругаться в консоль.
     *
     * <p>Вызывать в конце каждой проверки. Ошибка в консоли означает, что
     * часть отрисовки не состоялась, — а дальше проверка может найти
     * нужный текст просто потому, что экран остался прежним.
     */
    protected void assertNoJsErrors() {
        assertTrue(jsErrors.isEmpty(), "страница написала в консоль ошибки:\n"
                + String.join("\n", jsErrors));
        assertTrue(broken.isEmpty(), "сервер ответил ошибкой:\n" + String.join("\n", broken));
    }
}
