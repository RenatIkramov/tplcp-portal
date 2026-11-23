package com.ahlers.portal3pl;

import com.ahlers.portal3pl.files.FolderFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Папка файлового хранилища (V144): записал — прочитал — удалил; чужие
 * и недописанные файлы в папке своими не считаются. Без базы и Spring.
 */
class FolderFileStoreTest {

    @Test
    void writesReadsListsAndDeletes(@TempDir Path dir) throws Exception {
        FolderFileStore store = new FolderFileStore(dir.resolve("files").toString());
        assertEquals(Set.of(), store.list().keySet(), "папки ещё нет — список пуст, а не ошибка");

        UUID id = UUID.randomUUID();
        byte[] content = "отчёт".getBytes(StandardCharsets.UTF_8);
        store.write(id, content);
        assertArrayEquals(content, store.read(id));
        // файл лежит под своим идентификатором, а не под присланным именем
        try (Stream<Path> all = Files.walk(dir)) {
            assertEquals(1, all.filter(p -> p.getFileName().toString().equals(id.toString())).count());
        }

        // чужой файл и недописанный .part — не наши
        Path folder = dir.resolve("files").resolve(id.toString().substring(0, 2));
        Files.writeString(folder.resolve("readme.txt"), "x");
        Files.writeString(folder.resolve(UUID.randomUUID() + ".part"), "x");
        assertEquals(Set.of(id), store.list().keySet());

        store.write(id, "заново".getBytes(StandardCharsets.UTF_8));
        assertEquals("заново", new String(store.read(id), StandardCharsets.UTF_8), "повторная запись не заменила файл");

        store.delete(id);
        assertNull(store.read(id));
        store.delete(id);   // нет файла — не ошибка
    }
}
