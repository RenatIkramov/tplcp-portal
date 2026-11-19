package com.ahlers.portal3pl.files;

import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.documents.DocumentType;
import com.ahlers.portal3pl.documents.WmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Файлы, которые клиент прикладывает к документу (V145, Ф3; решения
 * заказчика 16.11.2026 и 18.11.2026).
 *
 * <p>Файл ложится в хранилище портала ({@link FileStore}) и уходит в WMS:
 * у черновика — вслед за документом (статус «Отправлен в WMS» ставит его
 * в очередь триггером базы), у отправленного — сразу. Не дошёл — повтор
 * раз в минуту, после 15-й неудачи «Ошибка отправки». Дошедший файл
 * портал хранит, пока жив документ.
 *
 * <p>Проверка — на сервере: размер, расширение из списка, а для известных
 * типов — что содержимое и правда этого типа (exe, переименованный
 * в pdf, не пройдёт). Имя очищается от пути и служебных символов.
 */
@Service
public class ClientFileService {

    private static final Logger log = LoggerFactory.getLogger(ClientFileService.class);

    /** Начало содержимого известных типов: расширение → допустимые сигнатуры. */
    private static final Map<String, List<byte[]>> SIGNATURES = Map.of(
            "pdf", List.of(bytes(0x25, 0x50, 0x44, 0x46)),                       // %PDF
            "png", List.of(bytes(0x89, 0x50, 0x4E, 0x47)),
            "jpg", List.of(bytes(0xFF, 0xD8, 0xFF)),
            "jpeg", List.of(bytes(0xFF, 0xD8, 0xFF)),
            "doc", List.of(bytes(0xD0, 0xCF, 0x11, 0xE0)),                       // OLE
            "xls", List.of(bytes(0xD0, 0xCF, 0x11, 0xE0)),
            "docx", List.of(bytes(0x50, 0x4B, 0x03, 0x04)),                      // zip
            "xlsx", List.of(bytes(0x50, 0x4B, 0x03, 0x04)));

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "doc", "application/msword", "xls", "application/vnd.ms-excel",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Принятый к загрузке файл: имя, как его прислал браузер, и содержимое. */
    public record Upload(String name, byte[] content) {
    }

    /** Файл для отдачи. */
    public record Download(String name, String contentType, byte[] content) {
    }

    private final JdbcClient jdbc;
    private final DepositorScope scope;
    private final CurrentUser current;
    private final FileStore store;
    private final WmsGateway wms;
    private final TransactionTemplate tx;

    public ClientFileService(JdbcClient jdbc, DepositorScope scope, CurrentUser current, FileStore store,
                             WmsGateway wms, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.current = current;
        this.store = store;
        this.wms = wms;
        this.tx = new TransactionTemplate(txManager);
    }

    /* ======================= Чтение ======================= */

    /** Файлы документа и ограничения приёма — для вкладки «Прикреплённые файлы». */
    public Map<String, Object> list(DocumentType type, String docId) {
        return tx.execute(s -> {
            scope.apply();
            current.requireUserId();
            requireVisible(type, docId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("files", SqlRows.toCamelCase(jdbc.sql("""
                            SELECT f.file_id::text AS file_id, f.file_name, f.content_type, f.size_bytes, f.created_at,
                                   f.wms_state, f.last_error, f.sent_at, COALESCE(u.full_name, u.login) AS uploaded_by_name
                              FROM dbo.stored_file f LEFT JOIN dbo.app_user u ON u.user_id = f.uploaded_by
                             WHERE f.kind = 'client' AND f.doc_type = :type AND f.doc_id = :doc::uuid
                             ORDER BY f.created_at, f.file_name
                            """).param("type", type.path()).param("doc", docId).query().listOfRows()));
            out.put("maxMb", maxMb());
            out.put("types", types());
            return out;
        });
    }

    public Download download(DocumentType type, String docId, String fileId) {
        Map<String, Object> row = tx.execute(s -> {
            scope.apply();
            current.requireUserId();
            requireVisible(type, docId);
            return fileRow(type, docId, fileId);
        });
        byte[] content = store.read(UUID.fromString(fileId));
        if (content == null) {
            log.warn("Файла клиента {} в хранилище нет", fileId);
            throw Fail.conflict("error.client_file.gone");
        }
        return new Download((String) row.get("file_name"), (String) row.get("content_type"), content);
    }

    /* ======================= Запись ======================= */

    /**
     * Приложить файлы. Все проверяются до записи: один негодный — не
     * записывается ни один, человек исправит и пришлёт снова.
     */
    public Map<String, Object> add(DocumentType type, String docId, List<Upload> uploads) {
        String userId = tx.execute(s -> {
            scope.apply();
            String id = current.requireUserId();
            current.requireMayCreate(type);
            String status = requireVisible(type, docId);
            if ("90".equals(status)) {
                throw Fail.conflict("error.client_file.document_cancelled");
            }
            return id;
        });
        if (uploads == null || uploads.isEmpty()) {
            throw Fail.badRequest("error.client_file.empty", "name", "—");
        }
        int maxMb = tx.execute(s -> maxMb());
        List<String> allowed = tx.execute(s -> types());
        List<String[]> checked = new ArrayList<>();   // имя, расширение
        for (Upload u : uploads) {
            String name = cleanName(u.name());
            String ext = extension(name);
            byte[] content = u.content() == null ? new byte[0] : u.content();
            if (content.length == 0) {
                throw Fail.badRequest("error.client_file.empty", "name", name);
            }
            if (content.length > (long) maxMb * 1024 * 1024) {
                throw Fail.badRequest("error.client_file.too_big", "name", name, "max", maxMb);
            }
            if (!allowed.contains(ext)) {
                throw Fail.badRequest("error.client_file.type_not_allowed", "name", name,
                        "types", String.join(", ", allowed));
            }
            if (SIGNATURES.containsKey(ext) && SIGNATURES.get(ext).stream().noneMatch(sig -> startsWith(content, sig))) {
                throw Fail.badRequest("error.client_file.content_mismatch", "name", name, "ext", ext);
            }
            checked.add(new String[]{name, ext});
        }

        List<UUID> written = new ArrayList<>();
        try {
            for (int i = 0; i < uploads.size(); i++) {
                UUID id = UUID.randomUUID();
                store.write(id, uploads.get(i).content());
                written.add(id);
            }
            tx.executeWithoutResult(s -> {
                scope.apply();
                for (int i = 0; i < written.size(); i++) {
                    byte[] content = uploads.get(i).content();
                    String[] nameExt = checked.get(i);
                    jdbc.sql("SELECT dbo.client_file_add(:type, :doc::uuid, :id, :name, :ctype, :size, :sha, :user::uuid)")
                            .param("type", type.path()).param("doc", docId).param("id", written.get(i))
                            .param("name", nameExt[0])
                            .param("ctype", CONTENT_TYPES.getOrDefault(nameExt[1], "application/octet-stream"))
                            .param("size", (long) content.length).param("sha", sha256(content))
                            .param("user", userId).query(rs -> { });
                }
            });
        } catch (RuntimeException e) {
            written.forEach(store::delete);
            throw e;
        }
        deliverDue();   // у отправленного документа — сразу, не дожидаясь расписания
        return list(type, docId);
    }

    /** Убрать файл — пока документ черновик и файл ещё не ушёл. */
    public Map<String, Object> remove(DocumentType type, String docId, String fileId) {
        Boolean removed = tx.execute(s -> {
            scope.apply();
            current.requireUserId();
            current.requireMayCreate(type);
            String status = requireVisible(type, docId);
            fileRow(type, docId, fileId);
            if (!"01".equals(status)) {
                throw Fail.conflict("error.client_file.not_draft");
            }
            return jdbc.sql("SELECT dbo.client_file_remove(:id::uuid)").param("id", fileId)
                    .query(Boolean.class).single();
        });
        if (Boolean.TRUE.equals(removed)) {
            store.delete(UUID.fromString(fileId));
        }
        return list(type, docId);
    }

    /** «Отправить ещё раз» у файла с ошибкой отправки. */
    public Map<String, Object> resend(DocumentType type, String docId, String fileId) {
        tx.executeWithoutResult(s -> {
            scope.apply();
            current.requireUserId();
            requireVisible(type, docId);
            Map<String, Object> row = fileRow(type, docId, fileId);
            if (!"failed".equals(row.get("wms_state"))) {
                throw Fail.conflict("error.client_file.not_failed");
            }
            jdbc.sql("SELECT dbo.client_file_resend(:id::uuid)").param("id", fileId).query(rs -> { });
        });
        deliverDue();
        return list(type, docId);
    }

    /* ======================= Отправка в WMS ======================= */

    /**
     * Отправить в WMS файлы, которым пора. Раз в минуту и сразу после
     * загрузки к отправленному документу. WMS — вне транзакции; сбой
     * одного файла не мешает остальным.
     */
    @Scheduled(fixedDelayString = "${portal.client-files.retry-ms:60000}",
               initialDelayString = "${portal.client-files.retry-ms:60000}")
    public void deliverDue() {
        List<Map<String, Object>> due = tx.execute(s -> jdbc.sql(
                        "SELECT file_id, depositor_id::text AS depositor_id, doc_type, doc_id::text AS doc_id,"
                                + " file_name, content_type FROM dbo.client_file_due(20)")
                .query().listOfRows());
        for (Map<String, Object> f : due == null ? List.<Map<String, Object>>of() : due) {
            UUID id = (UUID) f.get("file_id");
            String wmsId = null;
            String error = null;
            try {
                byte[] content = store.read(id);
                if (content == null) {
                    error = "file content is missing on the portal";
                } else {
                    wmsId = wms.uploadFile(DocumentType.ofPath((String) f.get("doc_type")), (String) f.get("doc_id"),
                            (String) f.get("file_name"), (String) f.get("content_type"), content);
                    if (wmsId == null || wmsId.isBlank()) {
                        error = "WMS returned no file id";
                        wmsId = null;
                    }
                }
            } catch (RuntimeException e) {
                error = String.valueOf(e.getMessage());
                log.warn("Файл {} не ушёл в WMS: {}", id, error);
            }
            String result = wmsId;
            String failure = error;
            tx.executeWithoutResult(s -> jdbc.sql("SELECT dbo.client_file_attempt(:id, :wms, :err)")
                    .param("id", id).param("wms", result).param("err", failure).query(rs -> { }));
        }
    }

    /* ======================= Помощники ======================= */

    /** Документ своего клиента и виден человеку (склады — под RLS); возвращает код статуса. */
    private String requireVisible(DocumentType type, String docId) {
        if (docId == null || !docId.matches("[0-9a-fA-F-]{36}")) {
            throw Fail.notFound("error.document.not_found");
        }
        return jdbc.sql("""
                        SELECT COALESCE(es.code, '01') FROM dbo.%s h LEFT JOIN dbo.enum_status es ON es.id = h.status_id
                         WHERE h.doc_id = :doc::uuid
                        """.formatted(type.headerTable()))
                .param("doc", docId).query(String.class).optional()
                .orElseThrow(() -> Fail.notFound("error.document.not_found"));
    }

    private Map<String, Object> fileRow(DocumentType type, String docId, String fileId) {
        if (fileId == null || !fileId.matches("[0-9a-fA-F-]{36}")) {
            throw Fail.notFound("error.client_file.not_found");
        }
        return jdbc.sql("""
                        SELECT file_name, content_type, wms_state FROM dbo.stored_file
                         WHERE file_id = :id::uuid AND kind = 'client' AND doc_type = :type AND doc_id = :doc::uuid
                        """).param("id", fileId).param("type", type.path()).param("doc", docId)
                .query().listOfRows().stream().findFirst()
                .orElseThrow(() -> Fail.notFound("error.client_file.not_found"));
    }

    private int maxMb() {
        return jdbc.sql("SELECT value::int FROM dbo.portal_setting WHERE setting_key = 'client_file_max_mb'")
                .query(Integer.class).optional().orElse(20);
    }

    private List<String> types() {
        String raw = jdbc.sql("SELECT value FROM dbo.portal_setting WHERE setting_key = 'client_file_types'")
                .query(String.class).optional().orElse("");
        return Arrays.stream(raw.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    /**
     * Имя без пути и служебных символов: браузер на Windows присылает
     * «C:\…\накладная.pdf», а «..», «/», «:» и управляющие символы
     * в имени файла — повод для неприятностей у того, кто его скачает.
     */
    static String cleanName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            name = "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] sig) {
        if (content.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (content[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
