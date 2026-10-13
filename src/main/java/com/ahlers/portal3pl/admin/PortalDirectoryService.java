package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.MsgRef;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Справочники уровня портала: склады, поклажедатели, организации оператора,
 * типы заказов и типы приходных документов. Общие для всех клиентов, поэтому ведёт их только
 * администратор — поклажедатель не должен менять то, чем пользуются другие.
 *
 * <p>Поклажедатели живут под RLS, и обычный запрос показал бы администратору
 * одного его клиента. Поэтому они читаются и пишутся через функции базы
 * с правами владельца, а право на это проверяет портал — так же, как
 * в остальном администрировании (V18–V20).
 *
 * <p>Склад и поклажедателя почти никогда нельзя удалить: на них ссылается
 * вся история. Рабочий способ вывести из обращения — архив; удаление
 * оставлено для случайно заведённых записей, и портал объясняет, что
 * именно мешает.
 */
@Service
public class PortalDirectoryService {

    /**
     * Что можно править в складе. Идентификатор склада задаётся при создании.
     *
     * <p>Ключи — имена колонок базы: они подставляются в запрос, и белый
     * список здесь не удобство, а защита от подстановки чужого имени.
     * Площадь и режим работы добавлены в V45 — они показываются
     * на странице «Контакты».
     */
    private static final Map<String, String> WAREHOUSE_COLUMNS = Map.of(
            "code", "varchar",
            "name", "varchar",
            "address", "varchar",
            "responsible", "varchar",
            "phone", "varchar",
            "email", "varchar",
            "area_m2", "numeric",
            "work_hours", "varchar");

    private final JdbcClient jdbc;
    private final AdminUserService users;

    public PortalDirectoryService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /* ======================= Склады ======================= */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> warehouses(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc
                .sql("SELECT * FROM dbo.v_warehouses_all ORDER BY code")
                .query()
                .listOfRows());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> warehouse(PortalUser actor, String warehouseId) {
        users.requireAdmin(actor);
        return warehouseRow(warehouseId);
    }

    /**
     * Создание склада. Идентификатор — не придуманный номер, а код склада
     * в WMS: по нему приходят документы обмена, и выдумывать его портал
     * не вправе. Поэтому он обязателен и дальше не меняется.
     */
    @Transactional
    public Map<String, Object> createWarehouse(PortalUser actor, Map<String, Object> values) {
        users.requireAdmin(actor);
        Map<String, Object> clean = filter(values, WAREHOUSE_COLUMNS);
        String id = text(values.get("warehouse_id"));
        if (id == null) {
            throw Fail.badRequest("error.warehouse.wms_id_required");
        }
        requireFields(clean, List.of("code", "name"), "error.warehouse.create_field_required");

        List<String> columns = new ArrayList<>(clean.keySet());
        String names = String.join(", ", columns);
        String params = columns.stream()
                .map(c -> ":" + c + "::" + WAREHOUSE_COLUMNS.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("""
                        INSERT INTO dbo.warehouse (warehouse_id, %s, is_active)
                        VALUES (:__id::varchar, %s, true)
                        """.formatted(names, params))
                .param("__id", id);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        try {
            spec.update();
        } catch (DuplicateKeyException e) {
            throw Fail.conflict("error.warehouse.duplicate");
        }
        return warehouseRow(id);
    }

    @Transactional
    public Map<String, Object> updateWarehouse(PortalUser actor, String warehouseId,
                                               Map<String, Object> values) {
        users.requireAdmin(actor);
        warehouseRow(warehouseId);        // есть ли такой — иначе 404

        Map<String, Object> clean = filter(values, WAREHOUSE_COLUMNS);
        if (clean.isEmpty()) {
            return warehouseRow(warehouseId);
        }
        for (String field : List.of("code", "name")) {
            if (clean.containsKey(field) && isBlank(clean.get(field))) {
                throw Fail.badRequest("error.field.empty", "field", field);
            }
        }
        String assignments = clean.keySet().stream()
                .map(c -> c + " = :" + c + "::" + WAREHOUSE_COLUMNS.get(c))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        var spec = jdbc.sql("UPDATE dbo.warehouse SET %s WHERE warehouse_id = :__id::varchar"
                        .formatted(assignments))
                .param("__id", warehouseId);
        for (var e : clean.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        try {
            spec.update();
        } catch (DuplicateKeyException e) {
            throw Fail.conflict("error.warehouse.code_taken");
        }
        return warehouseRow(warehouseId);
    }

    @Transactional
    public Map<String, Object> setWarehouseActive(PortalUser actor, String warehouseId, boolean active) {
        users.requireAdmin(actor);
        warehouseRow(warehouseId);
        jdbc.sql("UPDATE dbo.warehouse SET is_active = :active WHERE warehouse_id = :id::varchar")
                .param("active", active)
                .param("id", warehouseId)
                .update();
        return warehouseRow(warehouseId);
    }

    @Transactional
    public void deleteWarehouse(PortalUser actor, String warehouseId) {
        users.requireAdmin(actor);
        warehouseRow(warehouseId);

        Map<String, Object> usage = usage("warehouse_usage", warehouseId, "varchar");
        List<MsgRef> where = new ArrayList<>();
        addUsage(where, usage.get("receipts"), "error.usage.receipts");
        addUsage(where, usage.get("orders"), "error.usage.orders");
        addUsage(where, usage.get("transfers"), "error.usage.transfers");
        addUsage(where, usage.get("stockRows"), "error.usage.stock");
        addUsage(where, usage.get("depositors"), "error.usage.depositor_access");
        addUsage(where, usage.get("staff"), "error.usage.staff_access");
        requireUnused(where, "error.warehouse.in_use");

        jdbc.sql("DELETE FROM dbo.warehouse WHERE warehouse_id = :id::varchar")
                .param("id", warehouseId)
                .update();
    }

    /* ======================= Поклажедатели ======================= */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> depositors(PortalUser actor) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_depositor_list()")
                .query()
                .listOfRows());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> depositor(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        return depositorRow(depositorId);
    }

    /** Карточка поклажедателя. id = null — создание. */
    public record Depositor(
            String code, String orgName, String bin, String legalAddress,
            String phone, String email, String contractNumber, String contractValidUntil) {
    }

    @Transactional
    public Map<String, Object> saveDepositor(PortalUser actor, String depositorId, Depositor d) {
        users.requireAdmin(actor);
        if (isBlank(d.code()) || isBlank(d.orgName())) {
            throw Fail.badRequest("error.depositor.code_and_name_required");
        }
        if (depositorId != null) {
            depositorRow(depositorId);    // есть ли такой — иначе 404
        }
        String id;
        try {
            id = jdbc.sql("""
                            SELECT dbo.admin_save_depositor(:id::uuid, :code::text, :orgName::text,
                                   :bin::text, :legalAddress::text, :phone::text, :email::text,
                                   :contractNumber::text, :contractValidUntil::date)
                            """)
                    .param("id", depositorId)
                    .param("code", text(d.code()))
                    .param("orgName", text(d.orgName()))
                    .param("bin", text(d.bin()))
                    .param("legalAddress", text(d.legalAddress()))
                    .param("phone", text(d.phone()))
                    .param("email", text(d.email()))
                    .param("contractNumber", text(d.contractNumber()))
                    .param("contractValidUntil", text(d.contractValidUntil()))
                    .query(String.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw Fail.conflict("error.depositor.code_taken", "code", d.code());
        }
        return depositorRow(id);
    }

    @Transactional
    public Map<String, Object> setDepositorActive(PortalUser actor, String depositorId, boolean active) {
        users.requireAdmin(actor);
        depositorRow(depositorId);
        jdbc.sql("SELECT dbo.admin_set_depositor_active(:id::uuid, :active)")
                .param("id", depositorId)
                .param("active", active)
                .query()
                .listOfRows();
        return depositorRow(depositorId);
    }

    @Transactional
    public void deleteDepositor(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);
        depositorRow(depositorId);

        Map<String, Object> usage = usage("depositor_usage", depositorId, "uuid");
        List<MsgRef> where = new ArrayList<>();
        addUsage(where, usage.get("users"), "error.usage.granted_access");
        addUsage(where, usage.get("items"), "error.usage.items");
        addUsage(where, usage.get("receipts"), "error.usage.receipts");
        addUsage(where, usage.get("orders"), "error.usage.orders");
        addUsage(where, usage.get("stockRows"), "error.usage.stock");
        requireUnused(where, "error.depositor.in_use");

        jdbc.sql("SELECT dbo.admin_delete_depositor(:id::uuid)")
                .param("id", depositorId)
                .query()
                .listOfRows();
    }

    /* ======================= Типы заказов и документов ======================= */

    /**
     * Короткие перечисления: код, наименование, порядок и признак
     * «используется». Значения пришли из WMS, поэтому ключ — заданное
     * число, а не автонумерация; новым строкам номер выдаёт база.
     */
    public enum EnumKind {
        ORDER_TYPES("order-types", "enum_order_type", "order_type_usage", "orders",
                "error.order_type"),
        RECEIPT_DOC_TYPES("receipt-doc-types", "enum_receipt_doc_type", "receipt_doc_type_usage",
                "receipts", "error.receipt_doc_type");

        private final String path;
        private final String table;
        private final String usageFunction;
        private final String usageColumn;
        private final String errorPrefix;

        EnumKind(String path, String table, String usageFunction, String usageColumn,
                 String errorPrefix) {
            this.path = path;
            this.table = table;
            this.usageFunction = usageFunction;
            this.usageColumn = usageColumn;
            this.errorPrefix = errorPrefix;
        }

        /**
         * Ключ сообщения об отказе для этого справочника.
         *
         * <p>Раньше здесь лежало русское название («Тип заказа»)
         * и подставлялось в текст ошибки — в английском сообщении оно
         * так и оставалось русским. Теперь у справочника свой ключ,
         * и переводчик видит фразу целиком.
         */
        public String error(String what) {
            return errorPrefix + "." + what;
        }

        public static EnumKind ofPath(String path) {
            for (EnumKind k : values()) {
                if (k.path.equals(path)) {
                    return k;
                }
            }
            throw Fail.notFound("error.directory.unknown", "path", path);
        }
    }

    public record EnumRow(String code, String name, Integer sortOrder, Boolean isActive) {
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> enumRows(PortalUser actor, EnumKind kind) {
        users.requireAdmin(actor);
        return SqlRows.toCamelCase(jdbc.sql("""
                        SELECT id, code, name, sort_order, COALESCE(is_active, true) AS is_active
                        FROM dbo.%s ORDER BY sort_order NULLS LAST, name
                        """.formatted(kind.table))
                .query()
                .listOfRows());
    }

    @Transactional
    public List<Map<String, Object>> saveEnumRow(PortalUser actor, EnumKind kind,
                                                 Integer id, EnumRow row) {
        users.requireAdmin(actor);
        if (isBlank(row.code()) || isBlank(row.name())) {
            throw Fail.badRequest("error.directory.code_and_name_required");
        }
        try {
            if (id == null) {
                jdbc.sql("""
                                INSERT INTO dbo.%s (id, code, name, sort_order, is_active)
                                VALUES (dbo.next_enum_id(:table::text), :code::varchar, :name::varchar,
                                        :sortOrder, COALESCE(:isActive, true))
                                """.formatted(kind.table))
                        .param("table", kind.table)
                        .param("code", text(row.code()))
                        .param("name", text(row.name()))
                        .param("sortOrder", row.sortOrder())
                        .param("isActive", row.isActive())
                        .update();
            } else {
                requireEnumRow(kind, id);
                jdbc.sql("""
                                UPDATE dbo.%s SET code = :code::varchar, name = :name::varchar,
                                       sort_order = :sortOrder, is_active = COALESCE(:isActive, true)
                                 WHERE id = :id
                                """.formatted(kind.table))
                        .param("code", text(row.code()))
                        .param("name", text(row.name()))
                        .param("sortOrder", row.sortOrder())
                        .param("isActive", row.isActive())
                        .param("id", id)
                        .update();
            }
        } catch (DuplicateKeyException e) {
            throw Fail.conflict(kind.error("code_taken"), "code", row.code());
        }
        return enumRows(actor, kind);
    }

    @Transactional
    public List<Map<String, Object>> deleteEnumRow(PortalUser actor, EnumKind kind, int id) {
        users.requireAdmin(actor);
        requireEnumRow(kind, id);

        Long count = jdbc.sql("SELECT %s FROM dbo.%s(:id::smallint)"
                        .formatted(kind.usageColumn, kind.usageFunction))
                .param("id", id)
                .query(Long.class)
                .single();
        if (count != null && count > 0) {
            throw Fail.conflict(kind.error("in_use"), "count", count);
        }
        jdbc.sql("DELETE FROM dbo.%s WHERE id = :id".formatted(kind.table))
                .param("id", id)
                .update();
        return enumRows(actor, kind);
    }

    /* ======================= Вспомогательное ======================= */

    private Map<String, Object> warehouseRow(String warehouseId) {
        List<Map<String, Object>> rows = jdbc
                .sql("SELECT * FROM dbo.v_warehouses_all WHERE warehouse_id = :id::varchar")
                .param("id", warehouseId)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            throw Fail.notFound("error.warehouse.not_found");
        }
        return new LinkedHashMap<>(asText(SqlRows.toCamelCase(rows)).getFirst());
    }

    /* ================= Организации оператора =================
       Таблица operator создавалась под решение Р2 (два юрлица), но Р2
       отменено: счета и акты приходят из интеграции готовыми. Остался
       справочник реквизитов — и дом для содержимого страниц «О нас»
       и «Контакты», которое до сих пор зашито в вёрстку.

       Нигде в коде по operator_id ничего не ветвится: это именно
       справочник, а не механизм выбора организации.
    ========================================================== */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> operators(PortalUser actor) {
        users.requireAdmin(actor);
        return asText(SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.admin_operator_list()")
                .query()
                .listOfRows()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> operator(PortalUser actor, String operatorId) {
        users.requireAdmin(actor);
        return operatorRow(operatorId);
    }

    /** Карточка организации. id = null — создание. Логотип появится с файловым хранилищем. */
    public record Operator(
            String code, String name, String bin, String legalAddress,
            String bankName, String bankAccount, String phone, String email,
            String aboutContent, String contactsContent) {
    }

    @Transactional
    public Map<String, Object> saveOperator(PortalUser actor, String operatorId, Operator o) {
        users.requireAdmin(actor);
        if (isBlank(o.code()) || isBlank(o.name())) {
            throw Fail.badRequest("error.operator.code_and_name_required");
        }
        if (operatorId != null) {
            operatorRow(operatorId);      // есть ли такая — иначе 404
        }
        String id;
        try {
            id = jdbc.sql("""
                            SELECT dbo.admin_save_operator(:id::uuid, :code::text, :name::text,
                                   :bin::text, :legalAddress::text, :bankName::text,
                                   :bankAccount::text, :phone::text, :email::text,
                                   :aboutContent::text, :contactsContent::text)
                            """)
                    .param("id", operatorId)
                    .param("code", text(o.code()))
                    .param("name", text(o.name()))
                    .param("bin", text(o.bin()))
                    .param("legalAddress", text(o.legalAddress()))
                    .param("bankName", text(o.bankName()))
                    .param("bankAccount", text(o.bankAccount()))
                    .param("phone", text(o.phone()))
                    .param("email", text(o.email()))
                    .param("aboutContent", text(o.aboutContent()))
                    .param("contactsContent", text(o.contactsContent()))
                    .query(String.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw Fail.conflict("error.operator.code_taken", "code", o.code());
        }
        return operatorRow(id);
    }

    @Transactional
    public Map<String, Object> setOperatorActive(PortalUser actor, String operatorId,
                                                 boolean active) {
        users.requireAdmin(actor);
        operatorRow(operatorId);
        jdbc.sql("SELECT dbo.admin_set_operator_active(:id::uuid, :active)")
                .param("id", operatorId)
                .param("active", active)
                .query()
                .listOfRows();
        return operatorRow(operatorId);
    }

    /**
     * Удаление организации. Внешний ключ и так не даст удалить ту,
     * на которую ссылаются поклажедатели, но человеку нужно объяснение,
     * а не текст ошибки базы.
     */
    @Transactional
    public void deleteOperator(PortalUser actor, String operatorId) {
        users.requireAdmin(actor);
        operatorRow(operatorId);

        Map<String, Object> usage = usage("operator_usage", operatorId, "uuid");
        List<MsgRef> where = new ArrayList<>();
        addUsage(where, usage.get("depositors"), "error.usage.depositors");
        if (!where.isEmpty()) {
            throw Fail.conflict("error.operator.in_use", "where", where);
        }
        jdbc.sql("SELECT dbo.admin_delete_operator(:id::uuid)")
                .param("id", operatorId)
                .query()
                .listOfRows();
    }

    private Map<String, Object> operatorRow(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw Fail.notFound("error.operator.not_found");
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.sql("SELECT * FROM dbo.admin_operator_card(:id::uuid)")
                    .param("id", operatorId)
                    .query()
                    .listOfRows();
        } catch (org.springframework.dao.DataAccessException e) {
            // не-uuid в адресе — это «не найдено», а не ошибка сервера
            throw Fail.notFound("error.operator.not_found");
        }
        if (rows.isEmpty()) {
            throw Fail.notFound("error.operator.not_found");
        }
        return new LinkedHashMap<>(asText(SqlRows.toCamelCase(rows)).getFirst());
    }

    private Map<String, Object> depositorRow(String depositorId) {
        List<Map<String, Object>> rows = jdbc
                .sql("SELECT * FROM dbo.admin_depositor_card(:id::uuid)")
                .param("id", depositorId)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            throw Fail.notFound("error.depositor.not_found");
        }
        return new LinkedHashMap<>(asText(SqlRows.toCamelCase(rows)).getFirst());
    }

    private void requireEnumRow(EnumKind kind, int id) {
        boolean exists = Boolean.TRUE.equals(jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM dbo.%s WHERE id = :id)".formatted(kind.table))
                .param("id", id)
                .query(Boolean.class)
                .single());
        if (!exists) {
            throw Fail.notFound(kind.error("not_found"));
        }
    }

    private Map<String, Object> usage(String function, String id, String type) {
        return SqlRows.toCamelCase(jdbc.sql("SELECT * FROM dbo.%s(:id::%s)".formatted(function, type))
                        .param("id", id)
                        .query()
                        .listOfRows())
                .getFirst();
    }

    /** Место использования — тоже текст портала, поэтому ключом, а не словом. */
    private static void addUsage(List<MsgRef> where, Object count, String key) {
        if (count != null && ((Number) count).longValue() > 0) {
            where.add(MsgRef.of(key, "count", count));
        }
    }

    private static void requireUnused(List<MsgRef> where, String messageKey) {
        if (!where.isEmpty()) {
            throw Fail.conflict(messageKey, "where", where);
        }
    }

    private static Map<String, Object> filter(Map<String, Object> values, Map<String, String> allowed) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (values == null) {
            return clean;
        }
        values.forEach((key, value) -> {
            if (allowed.containsKey(key)) {
                clean.put(key, value instanceof String s && s.isBlank() ? null : value);
            }
        });
        return clean;
    }

    private static void requireFields(Map<String, Object> clean, List<String> required,
                                      String messageKey) {
        for (String field : required) {
            if (!clean.containsKey(field) || isBlank(clean.get(field))) {
                throw Fail.badRequest(messageKey, "field", field);
            }
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static String text(Object value) {
        return isBlank(value) ? null : String.valueOf(value).trim();
    }

    /** Идентификаторы — строками (см. SkuWriteService). */
    private static List<Map<String, Object>> asText(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            row.replaceAll((key, value) -> value instanceof UUID ? value.toString() : value);
        }
        return rows;
    }
}
