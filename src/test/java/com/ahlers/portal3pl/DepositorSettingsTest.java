package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.DepositorSettingsService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Настройки поклажедателя: что видно у него в портале.
 *
 * <p>С V61 их три: показывать ли раздел «Перемещения» и нужны ли цена
 * с суммой в окне ввода строки поступления и заказа. У перемещения цены
 * нет вовсе — товар не меняет владельца, — поэтому третьего флага цены
 * не существует, и это проверять нечем: его просто нет в записи.
 *
 * <p>Права здесь не проверяются: «настройки правит только администратор»
 * уже проверено в {@code ColumnDirectoryTest.onlyAdminChangesDepositorSections}
 * — повторять это во втором месте значит получить две проверки, которые
 * однажды разойдутся.
 *
 * <p>Главное, что здесь проверяется: настройки сохраняются ПО ОТДЕЛЬНОСТИ.
 * Карточка клиента отправляет их одним запросом, и достаточно забыть одно
 * поле в этом запросе, чтобы сохранение раздела молча сбрасывало цену.
 */
class DepositorSettingsTest extends PortalTestBase {

    @Autowired
    DepositorSettingsService settings;

    /**
     * Настройки «Доступов» поклажедателя: тест начинает с портальных,
     * а после себя возвращает те, что были, — не стирает настроенное
     * человеком для живой проверки (03.11.2026).
     */
    private Runnable restoreSetup = () -> { };

    @BeforeEach
    void portalSetup() {
        restoreSetup = startFromPortalSetup(DEPOSITOR);
    }

    @AfterEach
    void restoreDepositorSetup() {
        restoreSetup.run();
    }

    /** По умолчанию цены в окне строки нет: так просил заказчик. */
    @Test
    void priceIsOffByDefault() {
        PortalUser actor = portalAdmin();
        Map<String, Object> now = settings.settings(actor, depositorId(ADMIN, DEPOSITOR));
        assertEquals(false, now.get("priceInReceipts"), "цена в поступлении включена по умолчанию");
        assertEquals(false, now.get("priceInOrders"), "цена в заказе включена по умолчанию");
    }

    /** Каждый флаг живёт сам по себе и не сбивает соседние. */
    @Test
    void flagsAreSavedIndependently() {
        PortalUser actor = portalAdmin();
        String depositor = depositorId(ADMIN, DEPOSITOR);

        Map<String, Object> saved = settings.save(actor, depositor,
                new DepositorSettingsService.Settings(false, true, false));
        assertEquals(true, saved.get("priceInReceipts"), "цена в поступлении не сохранилась");
        assertEquals(false, saved.get("priceInOrders"), "цена в заказе включилась сама");
        assertEquals(false, saved.get("hideTransfersTab"), "раздел перемещений скрылся сам");

        // Меняем раздел — цена обязана остаться там, где была.
        saved = settings.save(actor, depositor,
                new DepositorSettingsService.Settings(true, true, false));
        assertEquals(true, saved.get("hideTransfersTab"), "раздел перемещений не скрылся");
        assertEquals(true, saved.get("priceInReceipts"),
                "сохранение раздела сбросило цену в поступлении");

        saved = settings.save(actor, depositor,
                new DepositorSettingsService.Settings(false, false, true));
        assertEquals(false, saved.get("priceInReceipts"), "цена в поступлении не выключилась");
        assertEquals(true, saved.get("priceInOrders"), "цена в заказе не включилась");
    }
}
