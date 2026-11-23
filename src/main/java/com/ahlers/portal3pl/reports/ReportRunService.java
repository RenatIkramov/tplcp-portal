package com.ahlers.portal3pl.reports;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.SqlRows;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.documents.WmsGateway;
import com.ahlers.portal3pl.files.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Запуск отчёта WMS (V138).
 *
 * <p>WMS отвечает на запрос заданием, файл готов позже (решение 13.11.2026):
 * <ol>
 *   <li>{@link #start} — проверить значения, собрать запрос, записать
 *       запуск «в очереди» и (уже вне транзакции) отправить запрос в WMS:
 *       приняла — «формируется», нет — «ошибка»;</li>
 *   <li>{@link #pollDue} — раз в {@code portal.reports.poll-ms} спросить
 *       WMS о готовности: готов — «готов», ошибка — «ошибка»; дольше
 *       {@code portal.reports.timeout-minutes} — «ошибка» по времени;</li>
 *   <li>{@link #file} — файл готового отчёта у WMS по номеру задания:
 *       своего хранилища у портала пока нет.</li>
 * </ol>
 *
 * <p>Что уходит в WMS ({@code код параметра → значение}):
 * <ul>
 *   <li>параметр, который видит человек, — введённое значение; пусто —
 *       «текущая дата» или значение по умолчанию из каталога;</li>
 *   <li>скрытый — значение клиента (V137), иначе по умолчанию или текущая дата;</li>
 *   <li>09 — код поклажедателя, 10 — номер документа: их подставляет портал,
 *       присланное человеком для них не берётся.</li>
 * </ul>
 * Дата уходит как {@code ГГГГ-ММ-ДД}; значение справочника — код, и он
 * сверяется со справочниками клиента: чужой склад или поставщик в WMS не уйдёт.
 */
@Service
public class ReportRunService {

    private static final Logger log = LoggerFactory.getLogger(ReportRunService.class);

    /** Место кнопки → таблица документа, по которому строится отчёт. */
    private static final Map<String, String> DOCUMENT_TABLES = Map.of(
            "03", "receipts", "04", "orders", "05", "transfers");

    /** Код справочника, которого должно быть у клиента: тип параметра → запрос. */
    private static final Map<String, String> DIRECTORY_CHECKS = Map.of(
            "04", "SELECT EXISTS (SELECT 1 FROM dbo.v_warehouses WHERE warehouse_id = :v)",
            "05", "SELECT EXISTS (SELECT 1 FROM dbo.supplier WHERE code = :v)",
            "06", "SELECT EXISTS (SELECT 1 FROM dbo.consignee WHERE code = :v)",
            "07", "SELECT EXISTS (SELECT 1 FROM dbo.enum_order_type WHERE code = :v AND is_active)",
            "08", "SELECT EXISTS (SELECT 1 FROM dbo.enum_receipt_doc_type WHERE code = :v AND is_active)");

    private final JdbcClient jdbc;
    private final DepositorScope scope;
    private final CurrentUser current;
    private final WmsGateway wms;
    private final FileStore files;
    private final TransactionTemplate tx;
    private final JsonMapper json = JsonMapper.builder().build();
    private final int checkSeconds;
    private final int timeoutMinutes;

    public ReportRunService(JdbcClient jdbc, DepositorScope scope, CurrentUser current, WmsGateway wms,
                            FileStore files, PlatformTransactionManager txManager,
                            @Value("${portal.reports.poll-ms:10000}") long pollMs,
                            @Value("${portal.reports.timeout-minutes:60}") int timeoutMinutes) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.current = current;
        this.wms = wms;
        this.files = files;
        this.tx = new TransactionTemplate(txManager);
        this.checkSeconds = (int) Math.max(1, pollMs / 1000);
        this.timeoutMinutes = timeoutMinutes;
    }

    /** Запрос человека: значения параметров, которые он видел, и документ — у отчётов карточки. */
    public record Request(Map<String, String> values, String docId, String skuId) {
        public Request(Map<String, String> values, String docId) {
            this(values, docId, null);
        }
    }

    private record Prepared(String runId, String wmsCode, Map<String, String> params) {
    }

    /**
     * Запустить отчёт. Неверные значения — отказ 400 до всякой записи;
     * отказ WMS — запуск со статусом «ошибка» (его видно в списке).
     *
     * @return запуск, как его показывает список
     */
    public Map<String, Object> start(PortalUser user, String reportId, Request request) {
        Prepared run;
        try {
            run = tx.execute(s -> prepare(user, reportId, request == null ? new Request(null, null) : request));
        } catch (DuplicateKeyException e) {
            // этот человек уже формирует этот отчёт (V139: report_run_one_open)
            throw Fail.conflict("error.report_run.already_running");
        }
        if (run == null) {
            throw new IllegalStateException("запуск не записан");
        }
        String job;
        try {
            job = wms.requestReport(run.wmsCode(), run.params());
        } catch (RuntimeException e) {
            log.warn("WMS не приняла отчёт {}: {}", run.wmsCode(), e.getMessage());
            tx.executeWithoutResult(s -> finish(run.runId(), "error.report_run.wms_unavailable", e.getMessage()));
            return one(run.runId());
        }
        tx.executeWithoutResult(s -> jdbc.sql("SELECT dbo.report_run_started(:id::uuid, :job, :sec)")
                .param("id", run.runId()).param("job", job).param("sec", checkSeconds).query().listOfRows());
        return one(run.runId());
    }

    private Prepared prepare(PortalUser user, String reportId, Request request) {
        scope.apply();
        Map<String, Object> report = reportId == null || !reportId.matches("[0-9a-fA-F-]{36}") ? null
                : jdbc.sql("""
                        SELECT c.report_id::text, c.name, c.place_code, c.wms_code,
                               COALESCE(dr.param_values, '{}')::text AS client_values
                          FROM dbo.v_client_reports c
                          JOIN dbo.depositor_report dr ON dr.report_id = c.report_id
                         WHERE c.report_id = :id::uuid
                        """).param("id", reportId).query().listOfRows().stream().findFirst().orElse(null);
        if (report == null) {
            throw Fail.notFound("error.report_run.not_found");
        }
        String place = (String) report.get("place_code");
        if (("02".equals(place) && current.billingHidden()) || ("05".equals(place) && current.transfersHidden())) {
            throw Fail.forbidden("error.access.section_hidden");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> clientValues = json.readValue((String) report.get("client_values"), Map.class);
        Map<String, String> given = request.values() == null ? Map.of() : request.values();

        String[] document = {null, null};
        String[] item = {null, null};
        Map<String, String> params = new LinkedHashMap<>();
        for (Map<String, Object> p : jdbc.sql("""
                        SELECT rp.param_key, dbo.report_param_label(rp.param_id, rp.label) AS label, t.code AS type,
                               rp.is_required, rp.default_value, rp.use_current_date, rp.is_hidden
                          FROM dbo.report_param rp JOIN dbo.enum_report_param_type t ON t.id = rp.type_id
                         WHERE rp.report_id = :id::uuid ORDER BY rp.sort_order
                        """).param("id", reportId).query().listOfRows()) {
            String key = (String) p.get("param_key");
            String label = (String) p.get("label");
            String type = (String) p.get("type");
            String value = switch (type) {
                case "09" -> depositorCode();
                case "10" -> {
                    document[0] = request.docId();
                    document[1] = documentNo(place, request.docId());
                    yield document[1];
                }
                case "11" -> {
                    item[0] = request.skuId();
                    item[1] = skuCode(request.skuId());
                    yield item[1];
                }
                default -> {
                    String v = Boolean.TRUE.equals(p.get("is_hidden")) ? clientValues.get(key) : given.get(key);
                    v = v == null || v.isBlank() ? null : v.trim();
                    if (v == null && Boolean.TRUE.equals(p.get("use_current_date"))) {
                        v = LocalDate.now().toString();
                    }
                    if (v == null) {
                        String d = (String) p.get("default_value");
                        v = d == null || d.isBlank() ? null : d.trim();
                    }
                    if (v == null && (Boolean.TRUE.equals(p.get("is_required")) || Boolean.TRUE.equals(p.get("is_hidden")))) {
                        throw Fail.badRequest("error.report_run.value_required", "label", label);
                    }
                    yield v == null ? null : checked(type, label, v);
                }
            };
            if (value != null) {
                params.put(key, value);
            }
        }

        String runId = jdbc.sql("""
                        INSERT INTO dbo.report_run (depositor_id, report_id, requested_by, requested_login,
                                                    report_name, wms_code, params, doc_id, document_no,
                                                    sku_id, sku_code)
                        VALUES (dbo.current_depositor_id(), :report::uuid,
                                (SELECT user_id FROM dbo.app_user WHERE login = :login), :login,
                                :name, :code, :params::jsonb, :doc::uuid, :no, :sku::uuid, :skuCode)
                        RETURNING run_id::text
                        """)
                .param("report", reportId).param("login", user.login())
                .param("name", report.get("name")).param("code", report.get("wms_code"))
                .param("params", json.writeValueAsString(params))
                .param("doc", document[0]).param("no", document[1])
                .param("sku", item[0]).param("skuCode", item[1])
                .query(String.class).single();
        return new Prepared(runId, (String) report.get("wms_code"), params);
    }

    /** Значение в том виде, в каком оно уходит в WMS, — или понятный отказ. */
    private String checked(String type, String label, String value) {
        switch (type) {
            case "01" -> {
                try {
                    return LocalDate.parse(value).toString();
                } catch (DateTimeParseException e) {
                    throw Fail.badRequest("error.report_run.bad_date", "label", label);
                }
            }
            case "02" -> {
                try {
                    return new BigDecimal(value.replace(',', '.')).toPlainString();
                } catch (NumberFormatException e) {
                    throw Fail.badRequest("error.report_run.bad_number", "label", label);
                }
            }
            default -> {
                String sql = DIRECTORY_CHECKS.get(type);
                if (sql != null && !jdbc.sql(sql).param("v", value).query(Boolean.class).single()) {
                    throw Fail.badRequest("error.report_run.unknown_value", "label", label, "value", value);
                }
                return value;
            }
        }
    }

    private String depositorCode() {
        return jdbc.sql("SELECT code FROM dbo.depositor WHERE depositor_id = dbo.current_depositor_id()")
                .query(String.class).single();
    }

    /** Номер документа клиента для отчёта из карточки — под RLS: чужой документ не найдётся. */
    private String documentNo(String place, String docId) {
        String table = DOCUMENT_TABLES.get(place);
        if (table == null || docId == null || !docId.matches("[0-9a-fA-F-]{36}")) {
            throw Fail.badRequest("error.report_run.document_required");
        }
        return jdbc.sql("SELECT document_no FROM dbo." + table + " WHERE doc_id = :id::uuid")
                .param("id", docId).query(String.class).optional()
                .orElseThrow(() -> Fail.badRequest("error.report_run.document_required"));
    }

    /** Код товара клиента для отчёта из карточки товара — под RLS: чужой товар не найдётся. */
    private String skuCode(String skuId) {
        if (skuId == null || !skuId.matches("[0-9a-fA-F-]{36}")) {
            throw Fail.badRequest("error.report_run.item_required");
        }
        return jdbc.sql("SELECT code FROM dbo.sku WHERE sku_id = :id::uuid")
                .param("id", skuId).query(String.class).optional()
                .orElseThrow(() -> Fail.badRequest("error.report_run.item_required"));
    }

    /** Закрыть запуск ошибкой. «Готов» ставит {@code report_run_file_ready} — вместе с файлом (V144). */
    private void finish(String runId, String errorKey, String detail) {
        jdbc.sql("SELECT dbo.report_run_finish(:id::uuid, '04', :k, :d, NULL, NULL)")
                .param("id", runId).param("k", errorKey).param("d", detail).query().listOfRows();
    }

    /* ======================= Выбор значения ======================= */

    /** Значения для выпадающего списка параметра: тип справочника → запрос (код и имя). */
    private static final Map<String, String> CHOICES = Map.of(
            "04", "SELECT warehouse_id AS code, name FROM dbo.v_warehouses WHERE is_active ORDER BY name",
            "05", "SELECT code, name FROM dbo.supplier WHERE is_active ORDER BY name",
            "06", "SELECT code, name FROM dbo.consignee WHERE is_active ORDER BY name",
            "07", "SELECT code, dbo.enum_name('enum_order_type', id::text, name) AS name"
                    + " FROM dbo.enum_order_type WHERE is_active ORDER BY sort_order, code",
            "08", "SELECT code, dbo.enum_name('enum_receipt_doc_type', id::text, name) AS name"
                    + " FROM dbo.enum_receipt_doc_type WHERE is_active ORDER BY sort_order, code");

    /**
     * Что предложить в окне параметров для параметра-справочника: справочник
     * клиента, в WMS уходит код (его же сверяет {@link #start}). Тип не
     * справочник — пустой список.
     */
    public List<Map<String, Object>> choices(String type) {
        String sql = CHOICES.get(type);
        if (sql == null) {
            return List.of();
        }
        return tx.execute(s -> {
            scope.apply();
            return jdbc.sql(sql).query().listOfRows();
        });
    }

    /* ======================= Список и файл ======================= */

    private static final String LIST_SQL = """
            SELECT run_id::text, report_id::text, report_name, wms_code, params::text AS params, document_no,
                   sku_id::text, sku_code, place_code,
                   status_code, status_name, error_key, error_detail, file_name,
                   requested_login, requested_name, requested_at, finished_at
              FROM dbo.v_report_runs
            """;

    /** Запуски клиента — всех его пользователей (решение 13.11.2026), новые сверху. */
    public List<Map<String, Object>> list() {
        return list(null, null);
    }

    /**
     * Запуски для таблицы раздела: {@code place} — отчёты этого места
     * (пусто — все), {@code skuId} — по этому товару (карточка товара).
     * Запуски отчётов скрытого раздела не показываются, как и сам раздел.
     */
    public List<Map<String, Object>> list(String place, String skuId) {
        String p = place == null || place.isBlank() ? null : place;
        String sku = skuId == null || !skuId.matches("[0-9a-fA-F-]{36}") ? null : skuId;
        boolean billing = current.billingHidden();
        boolean transfers = current.transfersHidden();
        return tx.execute(s -> {
            scope.apply();
            return SqlRows.toCamelCase(jdbc.sql(LIST_SQL + """
                             WHERE (:place::text IS NULL OR place_code = :place::text)
                               AND (:sku::uuid IS NULL OR sku_id = :sku::uuid)
                               AND NOT (COALESCE(place_code, '') = '02' AND :billing)
                               AND NOT (COALESCE(place_code, '') = '05' AND :transfers)
                             ORDER BY requested_at DESC LIMIT 200
                            """)
                    .param("place", p).param("sku", sku).param("billing", billing).param("transfers", transfers)
                    .query().listOfRows()).stream().map(this::shaped).toList();
        });
    }

    private Map<String, Object> one(String runId) {
        return tx.execute(s -> {
            scope.apply();
            return SqlRows.toCamelCase(jdbc.sql(LIST_SQL + " WHERE run_id = :id::uuid").param("id", runId)
                    .query().listOfRows()).stream().map(this::shaped).findFirst()
                    .orElseThrow(() -> Fail.notFound("error.report_run.not_found"));
        });
    }

    private Map<String, Object> shaped(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("params", json.readValue(String.valueOf(row.get("params")), Map.class));
        return out;
    }

    /**
     * Файл готового отчёта своего клиента — из хранилища портала (V144):
     * у WMS файл забирается один раз, когда задание готово.
     */
    public WmsGateway.ReportFile file(String runId) {
        Map<String, Object> run = tx.execute(s -> {
            scope.apply();
            return runId == null || !runId.matches("[0-9a-fA-F-]{36}") ? null
                    : jdbc.sql("""
                            SELECT s.code AS status, r.file_name, r.content_type, f.file_id
                              FROM dbo.report_run r
                              JOIN dbo.enum_report_run_status s ON s.id = r.status_id
                              LEFT JOIN dbo.stored_file f ON f.report_run_id = r.run_id
                             WHERE r.run_id = :id::uuid
                            """).param("id", runId).query().listOfRows().stream().findFirst().orElse(null);
        });
        if (run == null) {
            throw Fail.notFound("error.report_run.not_found");
        }
        if (!"03".equals(run.get("status"))) {
            throw Fail.conflict("error.report_run.not_ready");
        }
        UUID fileId = (UUID) run.get("file_id");
        byte[] content = fileId == null ? null : files.read(fileId);
        if (content == null) {
            // запуск готов, а файла нет: сформирован до хранилища либо файл пропал из папки
            log.warn("Файла отчёта {} в хранилище нет (описание: {})", runId, fileId);
            throw Fail.conflict("error.report_run.file_gone");
        }
        return new WmsGateway.ReportFile((String) run.get("file_name"), (String) run.get("content_type"), content);
    }

    /* ======================= Опрос готовности ======================= */

    /** Спросить WMS о заданиях, которым пора. Сбой одного не мешает остальным. */
    @Scheduled(fixedDelayString = "${portal.reports.poll-ms:10000}",
               initialDelayString = "${portal.reports.poll-ms:10000}")
    public void pollDue() {
        List<Map<String, Object>> due = tx.execute(s -> jdbc.sql(
                        "SELECT run_id::text, wms_job_id FROM dbo.report_run_due(:t, :key)")
                .param("t", timeoutMinutes).param("key", "error.report_run.timeout").query().listOfRows());
        for (Map<String, Object> run : due) {
            String runId = (String) run.get("run_id");
            try {
                WmsGateway.ReportState state = wms.reportState((String) run.get("wms_job_id"));
                switch (state.status()) {
                    case READY -> keepFile(runId, (String) run.get("wms_job_id"), state);
                    case FAILED -> tx.executeWithoutResult(s ->
                            finish(runId, "error.report_run.wms_failed", state.message()));
                    case RUNNING -> tx.executeWithoutResult(s -> checkedLater(runId));
                }
            } catch (RuntimeException e) {
                // WMS не ответила — спросим в следующий раз; время ожидания ограничено report_run_due
                log.warn("Готовность отчёта {} не узнать: {}", runId, e.getMessage());
                tx.executeWithoutResult(s -> checkedLater(runId));
            }
        }
    }

    /**
     * Задание готово: забрать файл у WMS, положить в хранилище и закрыть
     * запуск как «Готов» (V144). Содержимое пишется раньше описания: если
     * запуск к этому моменту уже не ждёт (закрыт по времени, удалён), файл
     * убирается. WMS файл не отдала — исключение, запуск спросят ещё раз.
     */
    private void keepFile(String runId, String jobId, WmsGateway.ReportState state) {
        WmsGateway.ReportFile file = wms.reportFile(jobId);
        String name = file.fileName() != null ? file.fileName() : state.fileName();
        String type = file.contentType() != null ? file.contentType() : state.contentType();
        UUID fileId = UUID.randomUUID();
        files.write(fileId, file.content());
        Boolean kept;
        try {
            kept = tx.execute(s -> jdbc.sql("SELECT dbo.report_run_file_ready(:run::uuid, :file, :name, :type, :size, :sha)")
                    .param("run", runId).param("file", fileId)
                    .param("name", name == null || name.isBlank() ? "report" : name).param("type", type)
                    .param("size", (long) file.content().length).param("sha", sha256(file.content()))
                    .query(Boolean.class).single());
        } catch (RuntimeException e) {
            files.delete(fileId);
            throw e;
        }
        if (!kept) {
            files.delete(fileId);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Раз в сутки — удалить запуски старше срока хранения (V139). Ночью, не в ровный час. */
    @Scheduled(cron = "0 23 3 * * *")
    public void cleanup() {
        Integer removed = tx.execute(s -> jdbc.sql("SELECT dbo.report_run_cleanup()").query(Integer.class).single());
        if (removed > 0) {
            log.info("Запуски отчётов: удалено старше срока хранения — {}", removed);
        }
    }

    private void checkedLater(String runId) {
        jdbc.sql("SELECT dbo.report_run_checked(:id::uuid, :sec)")
                .param("id", runId).param("sec", checkSeconds).query().listOfRows();
    }
}
