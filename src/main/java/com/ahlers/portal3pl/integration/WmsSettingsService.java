package com.ahlers.portal3pl.integration;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Настройки интеграции с WMS (V143, план «Кэш остатков», К7): учёт
 * перемещений в остатках, расписание ночной сверки и три соответствия
 * слоя перевода — адрес WMS склада, код клиента в WMS, код качества WMS.
 *
 * <p>Логина и пароля к WMS здесь нет: они в {@code application.yml}.
 * Читает и пишет только администратор.
 */
@Service
public class WmsSettingsService {

    private static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$");
    private static final Pattern FILE_TYPES = Pattern.compile("^[a-z0-9]{1,10}(,[a-z0-9]{1,10})*$");

    /** «PDF, .jpg,  xlsx» → «pdf,jpg,xlsx»: как человек ни напишет, хранится одинаково. */
    static String normalTypes(String raw) {
        return java.util.Arrays.stream(raw.toLowerCase(java.util.Locale.ROOT).split("[,;\\s]+"))
                .map(t -> t.startsWith(".") ? t.substring(1) : t)
                .filter(t -> !t.isEmpty()).distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** Адрес WMS склада; пустой — склад с WMS не сверяется. */
    public record Warehouse(String warehouseId, String baseUrl) {
    }

    /** Код клиента в WMS; пустой — клиент с WMS не сверяется. */
    public record Depositor(String depositorId, String wmsCode) {
    }

    /** Код качества WMS и качество портала, в которое он переводится. */
    public record Quality(String wmsCode, String qualityCode) {
    }

    /** Настройки целиком — экран сохраняет их одной кнопкой. */
    public record Settings(Boolean countTransfers, Boolean syncEnabled, String syncTime,
                           List<Warehouse> warehouses, List<Depositor> depositors, List<Quality> qualities,
                           Integer fileMaxMb, String fileTypes) {

        /** Без приёма файлов — как до V145: прежние значения не трогаются. */
        public Settings(Boolean countTransfers, Boolean syncEnabled, String syncTime,
                        List<Warehouse> warehouses, List<Depositor> depositors, List<Quality> qualities) {
            this(countTransfers, syncEnabled, syncTime, warehouses, depositors, qualities, null, null);
        }
    }

    private final JdbcClient jdbc;
    private final AdminUserService users;
    private final JsonMapper json = JsonMapper.builder().build();

    public WmsSettingsService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settings(PortalUser actor) {
        users.requireAdmin(actor);
        // Функцией владельца: список клиентов под RLS администратору без выбранного клиента не виден
        String body = jdbc.sql("SELECT dbo.admin_wms_settings()::text").query(String.class).single();
        return json.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    @Transactional
    public Map<String, Object> save(PortalUser actor, Settings s) {
        users.requireAdmin(actor);
        if (s == null || s.syncTime() == null || !TIME.matcher(s.syncTime().trim()).matches()) {
            throw Fail.badRequest("error.wms_settings.sync_time");
        }
        List<Warehouse> warehouses = s.warehouses() == null ? List.of() : s.warehouses();
        for (Warehouse w : warehouses) {
            String url = w.baseUrl() == null ? "" : w.baseUrl().trim();
            if (!url.isEmpty() && (!URL.matcher(url).matches() || url.length() > 255)) {
                throw Fail.badRequest("error.wms_settings.bad_url", "warehouse", w.warehouseId());
            }
        }
        List<Depositor> depositors = s.depositors() == null ? List.of() : s.depositors();
        Set<String> seen = new HashSet<>();
        for (Depositor d : depositors) {
            requireCode(d.wmsCode(), 20, seen);
        }
        List<Quality> qualities = s.qualities() == null ? List.of() : s.qualities();
        seen.clear();
        for (Quality q : qualities) {
            requireCode(q.wmsCode(), 35, seen);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("countTransfers", Boolean.TRUE.equals(s.countTransfers()));
        body.put("syncEnabled", Boolean.TRUE.equals(s.syncEnabled()));
        body.put("syncTime", s.syncTime().trim());
        body.put("warehouses", warehouses);
        body.put("depositors", depositors);
        body.put("qualities", qualities);
        // приём файлов клиента (V145): не прислан — прежнее значение остаётся
        if (s.fileMaxMb() != null || s.fileTypes() != null) {
            Map<String, Object> now = settings(actor);
            int maxMb = s.fileMaxMb() != null ? s.fileMaxMb() : ((Number) now.get("fileMaxMb")).intValue();
            if (maxMb < 1 || maxMb > 100) {
                throw Fail.badRequest("error.wms_settings.file_limit");
            }
            String types = s.fileTypes() != null ? normalTypes(s.fileTypes()) : String.valueOf(now.get("fileTypes"));
            if (!FILE_TYPES.matcher(types).matches()) {
                throw Fail.badRequest("error.wms_settings.file_types");
            }
            jdbc.sql("SELECT dbo.admin_client_file_settings_save(:m, :t)").param("m", maxMb).param("t", types)
                    .query(rs -> { });
        }
        jdbc.sql("SELECT dbo.admin_wms_settings_save(:p::jsonb)")
                .param("p", json.writeValueAsString(body)).query(rs -> { });
        return settings(actor);
    }

    /** Код WMS: без пробелов, не длиннее поля и не дважды в одном списке. Пустой — строки нет. */
    private static void requireCode(String raw, int max, Set<String> seen) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            return;
        }
        if (code.chars().anyMatch(Character::isWhitespace) || code.length() > max || !seen.add(code)) {
            throw Fail.badRequest("error.wms_settings.bad_code", "code", code);
        }
    }
}
