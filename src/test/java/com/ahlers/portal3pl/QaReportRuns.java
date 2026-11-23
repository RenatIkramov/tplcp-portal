package com.ahlers.portal3pl;

import com.ahlers.portal3pl.files.FileStore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Запуски отчётов для проверок экрана «Запуски отчётов» (V149): заводятся
 * владельцем схемы прямо в базе, со своим кодом WMS, и после проверки
 * убираются вместе с файлами. Через портал их не завести: клиент видит
 * только своего клиента, а WMS — заглушка.
 */
public final class QaReportRuns {

    public static final String WMS_CODE = "QA-RPT-RUNS";

    private QaReportRuns() {
    }

    /**
     * Запуск клиента {@code depositorId} в статусе {@code status} ('01'…'04');
     * у готового ('03') — файл {@code content} в хранилище портала.
     *
     * @return run_id
     */
    public static String add(OwnerDb owner, FileStore files, String depositorId, String reportName,
                             String status, byte[] content) {
        String runId = owner.jdbc.sql("""
                        INSERT INTO dbo.report_run (depositor_id, requested_login, report_name, wms_code, params,
                                                    status_id, error_key, error_detail, file_name, content_type,
                                                    started_at, finished_at)
                        SELECT :dep::uuid, 'qa-runs', :name, :code, '{"date_from": "2026-11-01"}'::jsonb, s.id,
                               CASE WHEN s.code = '04' THEN 'error.report_run.wms_failed' END,
                               CASE WHEN s.code = '04' THEN 'QA: WMS отказала' END,
                               CASE WHEN s.code = '03' THEN :name || '.xlsx' END,
                               CASE WHEN s.code = '03' THEN 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' END,
                               now(), CASE WHEN s.code IN ('03', '04') THEN now() END
                          FROM dbo.enum_report_run_status s WHERE s.code = :status
                        RETURNING run_id::text
                        """)
                .param("dep", depositorId).param("name", reportName).param("code", WMS_CODE).param("status", status)
                .query(String.class).single();
        if ("03".equals(status)) {
            UUID fileId = UUID.randomUUID();
            files.write(fileId, content);
            owner.jdbc.sql("""
                            INSERT INTO dbo.stored_file (file_id, depositor_id, kind, report_run_id, file_name,
                                                         content_type, size_bytes, sha256)
                            VALUES (:id, :dep::uuid, 'report', :run::uuid, :name, 'application/octet-stream', :size, :sha)
                            """)
                    .param("id", fileId).param("dep", depositorId).param("run", runId)
                    .param("name", reportName + ".xlsx").param("size", (long) content.length)
                    .param("sha", sha256(content)).update();
        }
        return runId;
    }

    /** Убрать запуски проверок и их файлы. */
    public static void removeAll(OwnerDb owner, FileStore files) {
        List<UUID> stored = owner.jdbc.sql("""
                        SELECT f.file_id FROM dbo.stored_file f JOIN dbo.report_run r ON r.run_id = f.report_run_id
                         WHERE r.wms_code = :code
                        """).param("code", WMS_CODE).query(UUID.class).list();
        owner.jdbc.sql("""
                        DELETE FROM dbo.stored_file
                         WHERE report_run_id IN (SELECT run_id FROM dbo.report_run WHERE wms_code = :code)
                        """).param("code", WMS_CODE).update();
        owner.jdbc.sql("DELETE FROM dbo.report_run WHERE wms_code = :code").param("code", WMS_CODE).update();
        stored.forEach(files::delete);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
