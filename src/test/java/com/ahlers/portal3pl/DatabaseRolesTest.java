package com.ahlers.portal3pl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Роли базы (аудит 11.11.2026, S10; V132).
 *
 * <p>НАЙДЕНО АУДИТОМ. Владелец схемы и всех объектов — суперпользователь
 * {@code postgres}: 71 функция {@code SECURITY DEFINER} выполнялась с его
 * правами. Теперь владелец — {@code portal_owner} без суперправ.
 *
 * <p>Если проверка {@link #everyObjectBelongsToPortalOwner()} упала после
 * новой миграции — значит, Flyway накатил её не под {@code portal_owner}
 * (в {@code application.yml} остался {@code spring.flyway.user: postgres}).
 * Исправить владельца: {@code ALTER … OWNER TO portal_owner} под postgres,
 * и сменить пользователя Flyway — см. комментарий в V132.
 *
 * <p>Вторая часть (V133): портал ролью {@code portal_app} не пишет
 * в учётные записи, доступы и токены «запомнить меня» и не читает хэши
 * паролей — только функциями владельца.
 *
 * <p>Третья часть (V134): функции вызывает только портал, у функций
 * владельца {@code pg_temp} в конце {@code search_path}, новая таблица
 * у портала — только на чтение.
 */
class DatabaseRolesTest extends PortalTestBase {

    @Test
    void ownerIsNotSuperuser() {
        Map<String, Object> owner = jdbc.sql("""
                        SELECT rolsuper, rolbypassrls, rolcreaterole, rolcreatedb
                          FROM pg_roles WHERE rolname = 'portal_owner'
                        """).query().singleRow();
        assertEquals(false, owner.get("rolsuper"), "portal_owner — суперпользователь");
        assertEquals(false, owner.get("rolcreaterole"), "portal_owner может создавать роли");
        assertEquals(false, owner.get("rolcreatedb"), "portal_owner может создавать базы");
        assertEquals(true, owner.get("rolbypassrls"),
                "у portal_owner нет BYPASSRLS — функции администратора и журнала перестанут видеть всех клиентов");
    }

    @Test
    void applicationRoleSeesOnlyItsOwnClient() {
        Map<String, Object> app = jdbc.sql("""
                        SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user
                        """).query().singleRow();
        assertEquals(false, app.get("rolsuper"), "портал подключается суперпользователем");
        assertEquals(false, app.get("rolbypassrls"), "портал подключается ролью, которая обходит RLS");
    }

    @Test
    void everyObjectBelongsToPortalOwner() {
        List<String> foreign = jdbc.sql("""
                        SELECT 'схема dbo — ' || pg_get_userbyid(nspowner)
                          FROM pg_namespace WHERE nspname = 'dbo' AND pg_get_userbyid(nspowner) <> 'portal_owner'
                        UNION ALL
                        SELECT c.oid::regclass || ' — ' || pg_get_userbyid(c.relowner)
                          FROM pg_class c
                         WHERE c.relnamespace = 'dbo'::regnamespace
                           AND c.relname <> 'flyway_schema_history'
                           AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                           AND pg_get_userbyid(c.relowner) <> 'portal_owner'
                        UNION ALL
                        SELECT p.oid::regprocedure || ' — ' || pg_get_userbyid(p.proowner)
                          FROM pg_proc p
                         WHERE p.pronamespace = 'dbo'::regnamespace
                           AND pg_get_userbyid(p.proowner) <> 'portal_owner'
                        UNION ALL
                        SELECT t.oid::regtype || ' — ' || pg_get_userbyid(t.typowner)
                          FROM pg_type t
                         WHERE t.typnamespace = 'dbo'::regnamespace AND t.typrelid = 0
                           AND t.typtype IN ('e', 'd', 'c', 'r', 'm')
                           AND pg_get_userbyid(t.typowner) <> 'portal_owner'
                         ORDER BY 1
                        """).query(String.class).list();
        assertTrue(foreign.isEmpty(), "объекты не у portal_owner — миграция накатана другим пользователем: "
                + String.join(", ", foreign.subList(0, Math.min(10, foreign.size())))
                + (foreign.size() > 10 ? " … всего " + foreign.size() : ""));
    }

    /**
     * Журнал Flyway остаётся у прежнего владельца (передать его, пока Flyway
     * держит журнал, нельзя — см. V132), но писать в него portal_owner может.
     * В песочнице без Flyway журнала нет — тогда проверять нечего.
     */
    @Test
    void portalOwnerWritesFlywayHistory() {
        List<String> closed = jdbc.sql("""
                        SELECT c.oid::regclass::text
                          FROM pg_class c
                         WHERE c.relname = 'flyway_schema_history' AND c.relkind IN ('r', 'p')
                           AND NOT (has_table_privilege('portal_owner', c.oid, 'SELECT')
                                AND has_table_privilege('portal_owner', c.oid, 'INSERT')
                                AND has_table_privilege('portal_owner', c.oid, 'UPDATE')
                                AND has_table_privilege('portal_owner', c.oid, 'DELETE'))
                        """).query(String.class).list();
        assertTrue(closed.isEmpty(), "portal_owner не может писать в журнал Flyway " + closed
                + " — следующая миграция под ним не накатится");
        List<String> open = jdbc.sql("""
                        SELECT c.oid::regclass::text
                          FROM pg_class c
                         WHERE c.relname = 'flyway_schema_history' AND c.relkind IN ('r', 'p')
                           AND has_table_privilege(c.oid, 'SELECT, INSERT, UPDATE, DELETE')
                        """).query(String.class).list();
        assertTrue(open.isEmpty(), "портал может читать или менять журнал Flyway " + open);
    }

    @Test
    void newTablesGetRightsOnlyFromPortalOwner() {
        List<String> others = jdbc.sql("""
                        SELECT DISTINCT pg_get_userbyid(defaclrole)
                          FROM pg_default_acl
                         WHERE defaclnamespace = 'dbo'::regnamespace
                           AND pg_get_userbyid(defaclrole) <> 'portal_owner'
                        """).query(String.class).list();
        assertTrue(others.isEmpty(), "права по умолчанию в dbo заведены и от имени " + others);
    }

    /**
     * Чего портал больше не может прямым запросом (V133). Будь у него
     * эти права, одна ошибка с внедрением SQL дала бы хэши паролей
     * и выдачу себе роли администратора.
     */
    @Test
    void applicationRoleCannotTouchAccountsDirectly() {
        List<String> allowed = jdbc.sql("""
                        SELECT t || ': ' || p
                          FROM unnest(ARRAY['app_user', 'user_depositor_access', 'user_warehouse_access',
                                            'auth_remember_token']) t,
                               unnest(ARRAY['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE']) p
                         WHERE has_table_privilege('dbo.' || t, p)
                        UNION ALL
                        SELECT 'auth_remember_token: SELECT' WHERE has_table_privilege('dbo.auth_remember_token', 'SELECT')
                        UNION ALL
                        SELECT 'app_user.password_hash: SELECT'
                         WHERE has_column_privilege('dbo.app_user', 'password_hash', 'SELECT')
                         ORDER BY 1
                        """).query(String.class).list();
        assertTrue(allowed.isEmpty(), "portal_app может напрямую: " + allowed);
    }

    /** Остальные колонки учётки портал читает — экраны и проверка сеанса на них стоят. */
    @Test
    void applicationRoleStillReadsAccounts() {
        List<String> hidden = jdbc.sql("""
                        SELECT attname::text FROM pg_attribute
                         WHERE attrelid = 'dbo.app_user'::regclass AND attnum > 0 AND NOT attisdropped
                           AND attname <> 'password_hash'
                           AND NOT has_column_privilege('dbo.app_user', attname, 'SELECT')
                        """).query(String.class).list();
        assertTrue(hidden.isEmpty(), "portal_app не видит колонки app_user " + hidden
                + " — новой колонке нужен GRANT SELECT (колонка) ON app_user TO portal_app");
    }

    /** Функции dbo — не всем ролям сервера, а только порталу (V134). */
    @Test
    void functionsAreNotOpenToEveryone() {
        List<String> open = jdbc.sql("""
                        SELECT p.oid::regprocedure::text
                          FROM pg_proc p
                         WHERE p.pronamespace = 'dbo'::regnamespace
                           AND (p.proacl IS NULL
                                OR EXISTS (SELECT 1 FROM aclexplode(p.proacl) a
                                            WHERE a.grantee = 0 AND a.privilege_type = 'EXECUTE'))
                         ORDER BY 1
                        """).query(String.class).list();
        assertTrue(open.isEmpty(), "функции, доступные любой роли сервера (PUBLIC): "
                + String.join(", ", open.subList(0, Math.min(10, open.size())))
                + (open.size() > 10 ? " … всего " + open.size() : ""));
    }

    /**
     * Функции владельца не ищут имена во временной схеме вызывающего
     * первыми — иначе временной таблицей с тем же именем можно подменить
     * то, что функция читает правами владельца (V134).
     */
    @Test
    void ownerFunctionsPutPgTempLast() {
        List<String> exposed = jdbc.sql("""
                        SELECT p.oid::regprocedure::text
                          FROM pg_proc p
                         WHERE p.pronamespace = 'dbo'::regnamespace AND p.prosecdef
                           AND NOT ('search_path=dbo, pg_temp' = ANY (COALESCE(p.proconfig, '{}')))
                         ORDER BY 1
                        """).query(String.class).list();
        assertTrue(exposed.isEmpty(), "SECURITY DEFINER без search_path = dbo, pg_temp: " + exposed);
    }

    /** Новая таблица у портала — только на чтение; запись миграция выдаёт явно (V134). */
    @Test
    void newTablesAreReadOnlyByDefault() {
        List<String> rights = jdbc.sql("""
                        SELECT a.privilege_type
                          FROM pg_default_acl d, aclexplode(d.defaclacl) a
                         WHERE d.defaclnamespace = 'dbo'::regnamespace
                           AND d.defaclobjtype = 'r'
                           AND pg_get_userbyid(d.defaclrole) = 'portal_owner'
                           AND a.grantee = 'portal_app'::regrole
                         ORDER BY 1
                        """).query(String.class).list();
        assertEquals(List.of("SELECT"), rights, "права portal_app на новые таблицы по умолчанию");
        List<String> functions = jdbc.sql("""
                        SELECT CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END
                          FROM pg_default_acl d, aclexplode(d.defaclacl) a
                         WHERE d.defaclnamespace = 'dbo'::regnamespace
                           AND d.defaclobjtype = 'f'
                           AND pg_get_userbyid(d.defaclrole) = 'portal_owner'
                           AND a.privilege_type = 'EXECUTE'
                         ORDER BY 1
                        """).query(String.class).list();
        assertTrue(functions.contains("portal_app") && !functions.contains("PUBLIC"),
                "новые функции по умолчанию вызывают " + functions + " — нужен portal_app и не PUBLIC");
    }
}
