package com.ahlers.portal3pl.reports;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.files.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Запуски отчётов» для администратора (V149): запуски всех клиентов.
 *
 * <p>У клиента запуски под RLS ({@link ReportRunService}); здесь читают
 * функции базы с правами владельца, и потому каждый вход начинается с
 * проверки, что спрашивает администратор.
 */
@Service
public class AdminReportRunService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportRunService.class);

    private final JdbcClient jdbc;
    private final AdminUserService users;
    private final FileStore files;

    public AdminReportRunService(JdbcClient jdbc, AdminUserService users, FileStore files) {
        this.jdbc = jdbc;
        this.users = users;
        this.files = files;
    }

    /** Страница запусков: готовый JSON {@code {total, page, pageSize, rows}}; новые сверху. */
    @Transactional(readOnly = true)
    public String page(PortalUser admin, UUID depositorId, UUID reportId, String status,
                       LocalDate from, LocalDate to, int page, int pageSize) {
        users.requireAdmin(admin);
        return jdbc.sql("""
                        SELECT dbo.admin_report_runs(:dep, :report, :status::text, :from::date, :to::date,
                                                     :page, :size)::text
                        """)
                .param("dep", depositorId)
                .param("report", reportId)
                .param("status", status == null || status.isBlank() ? null : status)
                .param("from", from)
                .param("to", to)
                .param("page", page)
                .param("size", pageSize)
                .query(String.class).single();
    }

    /** Статусы запуска — для отбора, на языке читающего. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> statuses(PortalUser admin) {
        users.requireAdmin(admin);
        return SqlRows.toCamelCase(jdbc.sql("""
                        SELECT code, dbo.enum_name('enum_report_run_status', id::text, name) AS name
                          FROM dbo.enum_report_run_status ORDER BY id
                        """).query().listOfRows());
    }

    /** Файл готового отчёта любого клиента — из хранилища портала. */
    @Transactional(readOnly = true)
    public WmsGateway.ReportFile file(PortalUser admin, UUID runId) {
        users.requireAdmin(admin);
        Map<String, Object> run = jdbc.sql("SELECT * FROM dbo.admin_report_run_file(:id)")
                .param("id", runId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> Fail.notFound("error.report_run.not_found"));
        if (!"03".equals(run.get("status"))) {
            throw Fail.conflict("error.report_run.not_ready");
        }
        UUID fileId = (UUID) run.get("file_id");
        byte[] content = fileId == null ? null : files.read(fileId);
        if (content == null) {
            log.warn("Файла отчёта {} в хранилище нет (описание: {})", runId, fileId);
            throw Fail.conflict("error.report_run.file_gone");
        }
        return new WmsGateway.ReportFile((String) run.get("file_name"), (String) run.get("content_type"), content);
    }
}
