package com.ahlers.portal3pl.files;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Администрирование → «Интеграция» → «Хранилище файлов» (V146):
 * {@code GET /api/admin/file-storage} — папка, файлы по видам, место
 * на диске, итог ночной уборки.
 *
 * <p>Папку здесь только показывают (решение заказчика 19.11.2026):
 * меняется она в {@code application.yml} с перезапуском — смена на ходу
 * оставила бы прежние файлы в старой папке, а адрес на сервере в руках
 * администратора портала давал бы писать куда угодно.
 */
@RestController
public class FileStorageController {

    private final JdbcClient jdbc;
    private final FileStore store;
    private final StoredFileCleanup cleanup;
    private final AdminUserService users;
    private final SessionUser session;

    public FileStorageController(JdbcClient jdbc, FileStore store, StoredFileCleanup cleanup,
                                 AdminUserService users, SessionUser session) {
        this.jdbc = jdbc;
        this.store = store;
        this.cleanup = cleanup;
        this.users = users;
        this.session = session;
    }

    @GetMapping("/api/admin/file-storage")
    public Map<String, Object> storage(HttpServletRequest request) {
        users.requireAdmin(session.requireUser(request));
        FileStore.Usage usage = store.usage();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("location", usage.location());
        out.put("exists", usage.exists());
        out.put("filesOnDisk", usage.files());
        out.put("bytesOnDisk", usage.bytes());
        out.put("freeBytes", usage.freeBytes());
        Map<String, Object> kinds = new LinkedHashMap<>();
        jdbc.sql("SELECT kind, file_count, total_bytes FROM dbo.admin_file_storage_stats()").query().listOfRows()
                .forEach(r -> kinds.put(String.valueOf(r.get("kind")),
                        Map.of("count", Objects.requireNonNullElse(r.get("file_count"), 0L),
                                "bytes", Objects.requireNonNullElse(r.get("total_bytes"), 0L))));
        out.put("kinds", kinds);
        StoredFileCleanup.Sweep last = cleanup.last();
        out.put("lastSweep", last == null ? null : Map.of("at", last.at().toString(), "expired", last.expired(),
                "orphans", last.orphans(), "missing", last.missing()));
        return out;
    }
}
