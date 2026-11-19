package com.ahlers.portal3pl.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единственное место, через которое портал обращается к ArtWMS.
 * <p>
 * Контракт API пока не согласован, поэтому здесь заглушка: вызовы ничего
 * не делают и считаются успешными. Когда контракт появится, меняется только
 * этот класс — вся логика статусов, проверок и экранов остаётся как есть.
 * <p>
 * Отправка документа и отправка транспорта — разные вызовы: транспорт в WMS
 * хранится отдельными сущностями и привязывается к уже созданному документу
 * (решение Р5).
 */
@Component
public class WmsGateway {

    private static final Logger log = LoggerFactory.getLogger(WmsGateway.class);

    /** Передать документ в WMS. Исключение означает неуспех отправки. */
    public void sendDocument(DocumentType type, String docId) {
        // TODO этап 8: POST в ArtWMS, разбор ответа, сохранение doc_number.
        // Пока только запись в журнал — по ней видно, что портал дошёл до
        // отправки и с какими данными, а документ переводится в статус
        // «Отправлен в WMS» так же, как будет с настоящей интеграцией.
        log.info("Заглушка WMS: документ {} ({}) считается отправленным", docId, type.path());
    }

    /** Передать или обновить транспортную информацию по документу. */
    public void sendTransport(DocumentType type, String docId) {
        // TODO этап 8: отдельный запрос к сущностям транспорта ArtWMS
        log.info("Заглушка WMS: транспорт по документу {} ({}) считается переданным",
                docId, type.path());
    }

    /* ======================= Файлы клиента (V145) ======================= */

    /**
     * Приложить файл клиента к документу в WMS (хранилище файлов ArtWMS,
     * {@code /file-cloud/upload/new/file}: объект WEA — поступление, AUF —
     * заказ). Исключение — WMS не приняла; портал повторит.
     *
     * @return номер файла в WMS ({@code TxtId})
     */
    public String uploadFile(DocumentType type, String docId, String fileName, String contentType, byte[] content) {
        // TODO этап 8: multipart-запрос в file-cloud; объект и его идентификатор в WMS — по документам ArtWMS.
        log.info("Заглушка WMS: файл «{}» ({} байт) к документу {} ({}) считается принятым",
                fileName, content.length, docId, type.path());
        return "STUB-FILE-" + UUID.randomUUID();
    }

    /* ======================= Остатки (V142) ======================= */

    /**
     * Сколько товара WMS может зарезервировать прямо сейчас: наличие этого
     * качества на складе минус резерв заказов, которые WMS уже приняла.
     * Запрос «Проверка доступности товара» (поток 65 документа «Потоки
     * интеграции»). Исключение — WMS не ответила.
     *
     * @return количество; {@code null} — WMS числа не дала, портал остаётся
     *         при своих данных (так отвечает заглушка: своих остатков у неё нет)
     */
    public BigDecimal freeStock(String depositorId, String warehouseId, String skuCode, String qualityCode) {
        // TODO этап 8: REST-запрос ArtWMS B2BAPI «проверка доступности» по товару, складу и квалификации.
        log.info("Заглушка WMS: свободный остаток {} / {} / качество {} — числа нет", skuCode, warehouseId, qualityCode);
        return null;
    }

    /** Строка среза остатков в формате портала: код товара портала, код качества WMS, количество. */
    public record StockRow(String skuCode, String wmsQualityCode, BigDecimal qty) {
    }

    /**
     * Срез остатков клиента на складе — для сверки (V143). Склады в API
     * WMS — разные порты, поэтому адрес приходит со склада. Перевод ответа
     * WMS в строки портала — здесь же. Исключение — WMS не ответила.
     *
     * @return строки среза (пустой список — у клиента на складе ничего нет);
     *         {@code null} — данных нет (так отвечает заглушка)
     */
    public List<StockRow> stockSnapshot(String baseUrl, String depositorWmsCode) {
        // TODO этап 8: REST-запрос остатков ArtWMS по клиенту на адрес склада и перевод строк.
        log.info("Заглушка WMS: срез остатков клиента {} по адресу {} — данных нет", depositorWmsCode, baseUrl);
        return null;
    }

    /* ======================= Отчёты (V138) ======================= */

    /** Состояние задания отчёта в WMS. */
    public enum ReportStatus { RUNNING, READY, FAILED }

    /** Ответ WMS о задании: готов — с именем и типом файла, ошибка — с текстом WMS. */
    public record ReportState(ReportStatus status, String fileName, String contentType, String message) {
    }

    /** Файл готового отчёта. */
    public record ReportFile(String fileName, String contentType, byte[] content) {
    }

    /** Что запросили у заглушки — чтобы её «файл» показывал, с чем ушёл запрос. */
    private final Map<String, String> stubJobs = new ConcurrentHashMap<>();

    /**
     * Запросить отчёт: код отчёта и значения параметров (ключ — код
     * параметра в WMS). WMS отвечает номером задания; файл готов позже.
     * Исключение — WMS запрос не приняла.
     */
    public String requestReport(String wmsCode, Map<String, String> params) {
        // TODO этап 8: запрос к ArtWMS «код отчёта + параметры», в ответе — номер задания.
        String job = "STUB-" + UUID.randomUUID();
        StringBuilder csv = new StringBuilder("parameter;value\nreport;").append(wmsCode).append('\n');
        params.forEach((k, v) -> csv.append(k).append(';').append(v).append('\n'));
        stubJobs.put(job, wmsCode + "|" + csv);
        log.info("Заглушка WMS: отчёт {} запрошен, задание {}, параметры {}", wmsCode, job, params);
        return job;
    }

    /** Готово ли задание. Исключение — WMS не ответила (портал спросит ещё раз). */
    public ReportState reportState(String jobId) {
        // TODO этап 8: запрос состояния задания в ArtWMS.
        String job = stubJobs.get(jobId);
        if (job == null) {
            return new ReportState(ReportStatus.FAILED, null, null, "unknown job " + jobId);
        }
        return new ReportState(ReportStatus.READY, fileName(job), "text/csv", null);
    }

    /** Файл готового отчёта. Исключение — WMS файл не отдала. */
    public ReportFile reportFile(String jobId) {
        // TODO этап 8: скачивание файла задания из ArtWMS.
        String job = stubJobs.get(jobId);
        if (job == null) {
            throw new IllegalStateException("заглушка WMS не знает задание " + jobId
                    + " — портал перезапускали после запроса");
        }
        return new ReportFile(fileName(job), "text/csv",
                job.substring(job.indexOf('|') + 1).getBytes(StandardCharsets.UTF_8));
    }

    private static String fileName(String job) {
        return job.substring(0, job.indexOf('|')) + "-" + LocalDate.now() + ".csv";
    }
}
