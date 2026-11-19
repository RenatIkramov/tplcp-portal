package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Мёртвый код не возвращается (аудит 11.11.2026, D3–D5; V129).
 *
 * <p>Удалённое здесь никто не читал: ни код портала, ни функции базы, ни
 * тесты. Мёртвое опаснее отсутствующего — однажды кто-то найдёт
 * представление или правило CSS, решит, что оно поддержано, и начнёт на
 * него опираться.
 */
class DeadCodeTest extends PortalTestBase {

    private static final Path STATIC = Path.of("src", "main", "resources", "static");

    @Test
    void deadDatabaseObjectsAreGone() {
        int left = jdbc.sql("""
                        SELECT (SELECT count(*) FROM pg_views
                                 WHERE schemaname = 'dbo' AND viewname IN ('v_users', 'v_user_access', 'v_operators'))
                             + (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                                 WHERE n.nspname = 'dbo'
                                   AND p.proname IN ('admin_unlock_locked_columns', 'user_filters_copy'))
                             + (SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto')
                             + (SELECT count(*) FROM pg_tables
                                 WHERE schemaname = 'dbo' AND tablename IN ('cache_policy', 'document_photo'))
                        """)
                .query(Integer.class).single();
        assertEquals(0, left, "в базе остались мёртвые таблицы, представления, функции или pgcrypto");
    }

    @Test
    void deadCssAndScriptsAreGone() throws IOException {
        String css = Files.readString(STATIC.resolve("css/portal.css"), StandardCharsets.UTF_8);
        for (String rule : List.of(".depositor-box{", ".fc-row-added td", ".fc-row-edited td", ".fc-row-deleted td")) {
            assertFalse(css.contains(rule), "в portal.css осталось мёртвое правило " + rule);
        }
        for (String[] f : new String[][]{{"partners.js", "PORTAL.partnerEdit"}, {"portal-dir.js", "PORTAL.portalDir ="}}) {
            String js = Files.readString(STATIC.resolve("js/portal/" + f[0]), StandardCharsets.UTF_8);
            assertFalse(js.contains(f[1]), f[0] + ": снова " + f[1] + " — его никто не читает");
        }
    }
}
