package com.ahlers.portal3pl.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ночная уборка файлового хранилища (V144).
 *
 * <ol>
 *   <li>Описания с истёкшим сроком удаляются, за ними — содержимое.</li>
 *   <li>Папка сверяется с базой: файл без описания удаляется (описание
 *       ушло вместе с запуском отчёта по сроку хранения), описание без
 *       файла — в журнал: скачать такой файл нельзя.</li>
 * </ol>
 *
 * <p>Файл без описания удаляется, только если он старше часа: описание
 * записывается ПОСЛЕ содержимого, и свежий файл может быть как раз между
 * двумя шагами.
 */
@Component
public class StoredFileCleanup {

    private static final Logger log = LoggerFactory.getLogger(StoredFileCleanup.class);
    private static final Duration FRESH = Duration.ofHours(1);

    private final JdbcClient jdbc;
    private final FileStore store;
    private final TransactionTemplate tx;

    /** Итог последней уборки — для сводки администратора (V146); после перезапуска портала пуст. */
    public record Sweep(Instant at, int expired, int orphans, int missing) {
    }

    private volatile Sweep last;

    public Sweep last() {
        return last;
    }

    public StoredFileCleanup(JdbcClient jdbc, FileStore store, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.store = store;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Раз в сутки, после чистки запусков отчётов (03:23). */
    @Scheduled(cron = "0 41 3 * * *")
    public void nightly() {
        int[] done = sweep(Instant.now().minus(FRESH));
        if (done[0] + done[1] + done[2] > 0) {
            log.info("Файловое хранилище: по сроку удалено {}, без описания удалено {}, без файла — {}",
                    done[0], done[1], done[2]);
        }
    }

    /**
     * Уборка; файлы без описания удаляются, если записаны раньше {@code orphansBefore}.
     *
     * @return сколько удалено по сроку, сколько удалено файлов без описания, сколько описаний без файла
     */
    public int[] sweep(Instant orphansBefore) {
        List<UUID> expired = tx.execute(s -> jdbc.sql("SELECT dbo.stored_file_expire()").query(UUID.class).list());
        for (UUID id : expired == null ? List.<UUID>of() : expired) {
            store.delete(id);
        }
        Map<UUID, Instant> onDisk = store.list();
        List<Map<String, Object>> problems = tx.execute(s -> jdbc.sql(
                        "SELECT file_id, problem FROM dbo.stored_file_check(:ids)")
                .param("ids", onDisk.keySet().toArray(new UUID[0])).query().listOfRows());
        int orphans = 0;
        int missing = 0;
        for (Map<String, Object> p : problems == null ? List.<Map<String, Object>>of() : problems) {
            UUID id = (UUID) p.get("file_id");
            if ("orphan".equals(p.get("problem"))) {
                if (onDisk.get(id).isBefore(orphansBefore)) {
                    store.delete(id);
                    orphans++;
                }
            } else {
                missing++;
                log.warn("Файловое хранилище: описание {} есть, а файла в папке нет", id);
            }
        }
        int[] out = {expired == null ? 0 : expired.size(), orphans, missing};
        last = new Sweep(Instant.now(), out[0], out[1], out[2]);
        return out;
    }
}
