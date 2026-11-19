package com.ahlers.portal3pl;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Служебные перечисления (V90): вместо английских слов в колонках —
 * коды 01…, имена на языке пользователя.
 */
class ServiceEnumsTest extends PortalTestBase {

    @Autowired
    AdminUserService users;

    @AfterEach
    void restoreLocale() {
        owner().jdbc.sql("UPDATE dbo.app_user SET locale_id = NULL WHERE login = :login")
                .param("login", ADMIN).update();
    }

    private Map<String, Object> user(List<Map<String, Object>> all, String login) {
        return all.stream().filter(u -> login.equals(u.get("login"))).findFirst().orElseThrow();
    }

    /**
     * Статус пользователя не хранится — выводится из признаков: так он
     * не может разойтись с тем, что на самом деле действует при входе.
     */
    @Test
    void userStatusFollowsTheFlags() {
        PortalUser admin = portalAdmin();
        List<Map<String, Object>> all = users.users(admin);
        assertEquals("03", user(all, "blocked@roga-kopyta.kz").get("userStatusCode"));
        assertEquals("Blocked", user(all, "blocked@roga-kopyta.kz").get("userStatusName"));
        assertEquals("01", user(all, USER).get("userStatusCode"));

        localeOf(admin, "ru");
        signInAs(ADMIN, null);
        assertEquals("Заблокирован", user(users.users(admin), "blocked@roga-kopyta.kz").get("userStatusName"));
    }

    /** Ни одного прежнего слова в колонках не осталось — только коды. */
    @Test
    void columnsHoldCodesNotWords() {
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.receipts WHERE origin !~ '^[0-9]{2}$'"));
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.sync_run"
                + " WHERE status !~ '^[0-9]{2}$' OR direction !~ '^[0-9]{2}$'"));
        assertTrue(Integer.parseInt(singleValue("SELECT count(*)::text FROM dbo.enum_registry"
                + " WHERE enum_key IN ('enum_user_status', 'enum_transport_state', 'enum_import_status',"
                + " 'enum_import_kind', 'enum_sync_status', 'enum_sync_direction', 'enum_document_origin')")) == 7,
                "все служебные перечисления видны на вкладке «Перечисления»");
    }
}
