package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Excel: шаблоны и загрузка из файла — товары и упаковки.
 * <pre>
 *   GET    /api/sku/excel-template        шаблон для загрузки товаров
 *   POST   /api/sku/excel-import          разобрать и проверить файл
 *   GET    /api/sku/excel-import/{id}     результат проверки
 *   DELETE /api/sku/excel-import/{id}     закрыть окно проверки
 *   POST   /api/sku/excel-import/{id}/create  создать новые позиции
 *   POST   /api/sku/excel-import/{id}/update  обновить существующие
 *
 *   GET    /api/packaging/excel-template  то же для упаковок
 *   POST   /api/packaging/excel-import
 *   GET    /api/packaging/excel-import/{id}
 *   DELETE /api/packaging/excel-import/{id}
 *   POST   /api/packaging/excel-import/{id}/create
 *   POST   /api/packaging/excel-import/{id}/update
 * </pre>
 *
 * <p>Адреса упаковок не делят пути с адресами товаров, хотя служба под
 * ними общая ({@link ExcelImportSupport}): загрузка упаковок,
 * отправленная на адрес товаров, получила бы «не найдено», а не
 * применилась бы как товары.
 *
 * <p>Разбор ничего не пишет в справочник товаров: он складывает
 * результат проверки и возвращает его идентификатор. Запись — отдельные
 * вызовы по классам строк, и тела у них нет нарочно: что записывать,
 * сервер знает из результата проверки, а не из того, что прислал
 * браузер.
 */
@RestController
public class ExcelController {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final SkuTemplateService template;
    private final SkuImportService imports;
    private final PackagingTemplateService packagingTemplate;
    private final PackagingImportService packagingImports;
    private final SessionUser session;
    private final CurrentUser current;

    public ExcelController(SkuTemplateService template, SkuImportService imports,
                           PackagingTemplateService packagingTemplate,
                           PackagingImportService packagingImports,
                           SessionUser session, CurrentUser current) {
        this.template = template;
        this.imports = imports;
        this.packagingTemplate = packagingTemplate;
        this.packagingImports = packagingImports;
        this.session = session;
        this.current = current;
    }

    @GetMapping("/api/sku/excel-template")
    public ResponseEntity<Resource> skuTemplate(HttpServletRequest request) {
        // Право на загрузку проверяет сама служба шаблона (§6.3).
        return download(template.build(session.requireUser(request)));
    }

    @GetMapping("/api/packaging/excel-template")
    public ResponseEntity<Resource> packagingTemplate(HttpServletRequest request) {
        return download(packagingTemplate.build(session.requireUser(request)));
    }

    private static ResponseEntity<Resource> download(SkuTemplateService.Template file) {
        // Имя файла с кодом клиента и по-русски: отдаём и в ASCII-виде,
        // и в UTF-8 (filename*), иначе часть браузеров покажет кракозябры.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(XLSX))
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }

    /**
     * Разбор и проверка файла.
     *
     * <p>Имя файла берётся из загруженного файла, а не из отдельного
     * поля: по нему проверяется, не для другого ли клиента собран файл.
     */
    @PostMapping("/api/sku/excel-import")
    public Map<String, Object> importCheck(@RequestParam("file") MultipartFile file,
                                           HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        String importId = imports.check(user, file == null ? null : file.getOriginalFilename(),
                bytesOf(file));
        return imports.result(importId);
    }

    @PostMapping("/api/packaging/excel-import")
    public Map<String, Object> packagingCheck(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        String importId = packagingImports.check(user,
                file == null ? null : file.getOriginalFilename(), bytesOf(file));
        return packagingImports.result(importId);
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw Fail.badRequest("error.file.not_chosen");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw Fail.badRequest("error.file.unreadable");
        }
    }

    @GetMapping("/api/sku/excel-import/{importId}")
    public Map<String, Object> importResult(@PathVariable String importId,
                                            HttpServletRequest request) {
        session.requireUser(request);
        current.requireExcelUpload("sku");
        return imports.result(importId);
    }

    /**
     * Создать новые позиции.
     *
     * <p>Возвращает тот же результат, что {@code GET}, плюс сколько
     * записано и сколько не удалось: экран перерисовывается целиком
     * и не додумывает итог по коду ответа.
     */
    @PostMapping("/api/sku/excel-import/{importId}/create")
    public Map<String, Object> importCreate(@PathVariable String importId,
                                            HttpServletRequest request) {
        return imports.apply(session.requireUser(request), importId, true);
    }

    /** Обновить существующие позиции — заполнить их пустые поля. */
    @PostMapping("/api/sku/excel-import/{importId}/update")
    public Map<String, Object> importUpdate(@PathVariable String importId,
                                            HttpServletRequest request) {
        return imports.apply(session.requireUser(request), importId, false);
    }

    @DeleteMapping("/api/sku/excel-import/{importId}")
    public void importCancel(@PathVariable String importId, HttpServletRequest request) {
        session.requireUser(request);
        current.requireExcelUpload("sku");
        imports.cancel(importId);
    }

    @GetMapping("/api/packaging/excel-import/{importId}")
    public Map<String, Object> packagingResult(@PathVariable String importId,
                                               HttpServletRequest request) {
        session.requireUser(request);
        current.requireExcelUpload("packaging");
        return packagingImports.result(importId);
    }

    @PostMapping("/api/packaging/excel-import/{importId}/create")
    public Map<String, Object> packagingCreate(@PathVariable String importId,
                                               HttpServletRequest request) {
        return packagingImports.apply(session.requireUser(request), importId, true);
    }

    @PostMapping("/api/packaging/excel-import/{importId}/update")
    public Map<String, Object> packagingUpdate(@PathVariable String importId,
                                               HttpServletRequest request) {
        return packagingImports.apply(session.requireUser(request), importId, false);
    }

    @DeleteMapping("/api/packaging/excel-import/{importId}")
    public void packagingCancel(@PathVariable String importId, HttpServletRequest request) {
        session.requireUser(request);
        current.requireExcelUpload("packaging");
        packagingImports.cancel(importId);
    }
}
