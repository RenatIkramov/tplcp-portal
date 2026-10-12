package com.ahlers.portal3pl.columns;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Колонки таблиц:
 * <pre>
 *   GET    /api/table-columns                 раскладка всех таблиц
 *   GET    /api/table-columns/{table}         раскладка одной таблицы
 *   PUT    /api/table-columns/{table}         сохранить свой выбор
 *   DELETE /api/table-columns/{table}         сбросить к умолчаниям
 *   DELETE /api/table-columns                 сбросить все таблицы
 *
 *   GET    /api/admin/column-tables                       разделы  (администратор)
 *   GET    /api/admin/depositor-columns?depositorId=&amp;table=  настройки клиента
 *   PUT    /api/admin/depositor-columns                   сохранить одну колонку
 *   DELETE /api/admin/depositor-columns?depositorId=      вернуть портальные
 *   GET    /api/admin/field-catalog                       поля, которые можно переименовать
 *   GET    /api/admin/field-labels?depositorId=           переименованные поля
 *   PUT    /api/admin/field-labels                        переименовать поле
 *   POST   /api/admin/users/copy-settings                 скопировать настройки
 * </pre>
 *
 * <p>Раскладка приходит готовой: подпись уже на языке пользователя и уже
 * с учётом переименования поля клиентом, видимость и порядок посчитаны.
 * Экран ничего не вычисляет — иначе те же правила пришлось бы повторять
 * в каждой таблице и в окне выбора колонок, и они бы разошлись.
 */
@RestController
public class ColumnController {

    private final ColumnService columns;
    private final SessionUser session;

    public ColumnController(ColumnService columns, SessionUser session) {
        this.columns = columns;
        this.session = session;
    }

    @GetMapping("/api/table-columns")
    public List<Map<String, Object>> all(HttpServletRequest request) {
        return columns.layout(session.requireUser(request), null);
    }

    @GetMapping("/api/table-columns/{table}")
    public List<Map<String, Object>> one(@PathVariable String table,
                                         HttpServletRequest request) {
        return columns.layout(session.requireUser(request), table);
    }

    public record Chosen(List<String> columns) {
    }

    @PutMapping("/api/table-columns/{table}")
    public List<Map<String, Object>> save(@PathVariable String table,
                                          @RequestBody Chosen body,
                                          HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        return columns.saveMine(user, table, body.columns());
    }

    @DeleteMapping("/api/table-columns/{table}")
    public List<Map<String, Object>> reset(@PathVariable String table,
                                           HttpServletRequest request) {
        return columns.resetMine(session.requireUser(request), table);
    }

    @DeleteMapping("/api/table-columns")
    public List<Map<String, Object>> resetAll(HttpServletRequest request) {
        return columns.resetMine(session.requireUser(request), null);
    }

    /* ---- администрирование ---- */

    @GetMapping("/api/admin/column-tables")
    public List<Map<String, Object>> tables(HttpServletRequest request) {
        return columns.tables(session.requireUser(request));
    }

    @GetMapping("/api/admin/depositor-columns")
    public List<Map<String, Object>> depositorColumns(
            @RequestParam String depositorId,
            @RequestParam(required = false) String table,
            HttpServletRequest request) {
        return columns.depositorColumns(session.requireUser(request), depositorId, table);
    }

    public record DepositorColumn(String depositorId, String table, String columnId,
                                  boolean isAvailable, boolean isDefault) {
    }

    @PutMapping("/api/admin/depositor-columns")
    public List<Map<String, Object>> saveDepositorColumn(@RequestBody DepositorColumn body,
                                                         HttpServletRequest request) {
        return columns.saveDepositorColumn(session.requireUser(request), body.depositorId(),
                body.table(), body.columnId(), body.isAvailable(), body.isDefault());
    }

    @DeleteMapping("/api/admin/depositor-columns")
    public List<Map<String, Object>> resetDepositorColumns(
            @RequestParam String depositorId,
            @RequestParam(required = false) String table,
            HttpServletRequest request) {
        return columns.resetDepositorColumns(session.requireUser(request), depositorId, table);
    }

    /* ---- переименование полей поклажедателем ---- */

    @GetMapping("/api/admin/field-labels")
    public List<Map<String, Object>> fieldLabels(@RequestParam String depositorId,
                                                 HttpServletRequest request) {
        return columns.fieldLabels(session.requireUser(request), depositorId);
    }

    @GetMapping("/api/admin/field-catalog")
    public List<Map<String, Object>> fieldCatalog(HttpServletRequest request) {
        return columns.fieldCatalog(session.requireUser(request));
    }

    public record FieldLabel(String depositorId, String objectCode, String fieldId,
                             String label) {
    }

    @PutMapping("/api/admin/field-labels")
    public List<Map<String, Object>> saveFieldLabel(@RequestBody FieldLabel body,
                                                    HttpServletRequest request) {
        return columns.saveFieldLabel(session.requireUser(request), body.depositorId(),
                body.objectCode(), body.fieldId(), body.label());
    }

    public record CopySettings(String fromUserId, String toUserId) {
    }

    @PostMapping("/api/admin/users/copy-settings")
    public Map<String, Object> copySettings(@RequestBody CopySettings body,
                                            HttpServletRequest request) {
        columns.copySettings(session.requireUser(request), body.fromUserId(), body.toUserId());
        return Map.of("copied", true);
    }
}
