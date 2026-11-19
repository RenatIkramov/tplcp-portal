package com.ahlers.portal3pl.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Содержимое файлов — в папке на сервере портала (V144).
 *
 * <p>Папка — {@code portal.files.dir}; не задана — {@code data/files}
 * рядом с порталом. Файл лежит как {@code <первые два знака id>/<id>}:
 * тысячи файлов в одной папке файловые системы листают медленно.
 *
 * <p>Имя на диске собирается только из {@link UUID} — путь за пределы
 * папки построить нельзя.
 */
@Component
public class FolderFileStore implements FileStore {

    private final Path root;

    public FolderFileStore(@Value("${portal.files.dir:data/files}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    private Path pathOf(UUID fileId) {
        String name = fileId.toString();
        return root.resolve(name.substring(0, 2)).resolve(name);
    }

    @Override
    public void write(UUID fileId, byte[] content) {
        Path target = pathOf(fileId);
        try {
            Files.createDirectories(target.getParent());
            // сначала рядом, потом переименование: недописанный файл под своим именем не появится
            Path part = target.resolveSibling(target.getFileName() + ".part");
            Files.write(part, content);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось записать файл " + fileId + " в " + root, e);
        }
    }

    @Override
    public byte[] read(UUID fileId) {
        Path file = pathOf(fileId);
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось прочитать файл " + fileId, e);
        }
    }

    @Override
    public void delete(UUID fileId) {
        try {
            Files.deleteIfExists(pathOf(fileId));
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось удалить файл " + fileId, e);
        }
    }

    @Override
    public Usage usage() {
        if (!Files.isDirectory(root)) {
            // свободное место — у ближайшей существующей папки выше
            Path probe = root;
            while (probe != null && !Files.isDirectory(probe)) {
                probe = probe.getParent();
            }
            return new Usage(root.toString(), false, 0, 0, probe == null ? -1 : probe.toFile().getUsableSpace());
        }
        long files = 0;
        long bytes = 0;
        try (Stream<Path> all = Files.walk(root, 2)) {
            for (Path file : (Iterable<Path>) all.filter(Files::isRegularFile)::iterator) {
                files++;
                bytes += Files.size(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось прочитать папку файлов " + root, e);
        }
        return new Usage(root.toString(), true, files, bytes, root.toFile().getUsableSpace());
    }

    @Override
    public Map<UUID, Instant> list() {
        Map<UUID, Instant> out = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(root, 2)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                try {
                    // чужие файлы в папке (и недописанные .part) — не наши: в сверку не идут
                    out.put(UUID.fromString(file.getFileName().toString()), Files.getLastModifiedTime(file).toInstant());
                } catch (IllegalArgumentException notOurs) {
                    // имя — не идентификатор файла
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось прочитать папку файлов " + root, e);
        }
        return out;
    }
}
