package com.ahlers.portal3pl.files;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Где лежит содержимое файлов портала (V144).
 *
 * <p>Остальной код знает файл только по идентификатору: имя, которое
 * прислали человек или WMS, в место хранения не попадает. Сейчас место —
 * папка на сервере ({@link FolderFileStore}, решение заказчика
 * 16.11.2026); другое место хранения — другая реализация этого интерфейса.
 */
public interface FileStore {

    /** Записать содержимое. Запись до конца или никак: недописанный файл под своим именем не появляется. */
    void write(UUID fileId, byte[] content);

    /** Содержимое файла; {@code null} — файла нет. */
    byte[] read(UUID fileId);

    /** Удалить; файла нет — не ошибка. */
    void delete(UUID fileId);

    /** Все файлы хранилища и время их записи — для ночной сверки с базой. */
    Map<UUID, Instant> list();

    /** Сводка для администратора (V146): где лежит, сколько файлов и байт, сколько свободно. */
    record Usage(String location, boolean exists, long files, long bytes, long freeBytes) {
    }

    Usage usage();
}
