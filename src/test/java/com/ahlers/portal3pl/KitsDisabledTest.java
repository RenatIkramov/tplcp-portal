package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.DepositorSettingsService;
import com.ahlers.portal3pl.catalog.KitService;
import com.ahlers.portal3pl.common.PortalException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * «Используются комплекты» — настройка клиента (V155).
 *
 * <p>Галочку клиента D-001 проверка запоминает и после возвращает.
 */
class KitsDisabledTest extends PortalTestBase {

    @Autowired
    KitService kits;
    @Autowired
    DepositorSettingsService settings;
    @Autowired
    com.ahlers.portal3pl.config.CurrentUser currentUser;

    private boolean before;

    private String dep() {
        return depositorId(USER, DEPOSITOR);
    }

    private boolean stored() {
        return owner().jdbc.sql("SELECT kits_enabled FROM dbo.depositor_settings_of(:d::uuid)")
                .param("d", dep()).query(Boolean.class).single();
    }

    private void set(boolean on) {
        owner().jdbc.sql("SELECT dbo.admin_save_depositor_kits(:d::uuid, :on)")
                .param("d", dep()).param("on", on).query(rs -> { });
    }

    @BeforeEach
    void remember() {
        before = stored();
    }

    @AfterEach
    void restore() {
        set(before);
    }

    /** Выключено — экран узнаёт это из /api/me, а адреса комплектов отказывают. */
    @Test
    void switchedOffKitsAreRefused() {
        set(false);
        signIn();
        assertEquals(false, currentUser.settings().get("kitsEnabled"), "экран не узнает, что комплекты выключены");
        PortalException e = assertThrows(PortalException.class, () -> kits.list());
        assertEquals(403, e.getStatusCode().value());
        assertEquals("error.kit.disabled", e.key());

        set(true);
        assertEquals(true, currentUser.settings().get("kitsEnabled"));
        kits.list();
    }

    /** Галочку ставит администратор в карточке клиента; старый запрос без неё её не трогает. */
    @Test
    void adminSavesTheFlag() {
        var admin = portalAdmin();
        Map<String, Object> s = settings.settings(admin, dep());
        boolean h = Boolean.TRUE.equals(s.get("hideTransfersTab"));
        boolean r = Boolean.TRUE.equals(s.get("priceInReceipts"));
        boolean o = Boolean.TRUE.equals(s.get("priceInOrders"));

        assertEquals(false, settings.save(admin, dep(), new DepositorSettingsService.Settings(h, r, o, false))
                .get("kitsEnabled"));
        assertEquals(false, stored());
        // без галочки комплектов (прежний вид запроса) — не меняется
        assertEquals(false, settings.save(admin, dep(), new DepositorSettingsService.Settings(h, r, o))
                .get("kitsEnabled"));
        assertEquals(true, settings.save(admin, dep(), new DepositorSettingsService.Settings(h, r, o, true))
                .get("kitsEnabled"));
        assertEquals(List.of(h, r, o), List.of(stored("hide_transfers_tab"), stored("price_in_receipts"),
                stored("price_in_orders")), "галочка комплектов задела другие настройки");
    }

    private boolean stored(String column) {
        return owner().jdbc.sql("SELECT " + column + " FROM dbo.depositor_settings_of(:d::uuid)")
                .param("d", dep()).query(Boolean.class).single();
    }
}
