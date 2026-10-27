package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Шаблон Excel для загрузки номенклатуры.
 *
 * <p>Подписи берутся из справочника колонок, то есть на языке
 * пользователя и с учётом того, как клиент переименовал поле. Колонка,
 * запрещённая этому поклажедателю, в шаблон не попадает — иначе запрет
 * на экране ничего не значил бы.
 *
 * <p>Кода товара WMS в шаблоне нет: его присваивает WMS. Поклажедателя
 * тоже — он берётся из сеанса. В имя файла идёт наименование его
 * организации (для человека), а код — в скрытую метку (для машины):
 * по ней загрузка отличит файл, собранный для другого клиента, и метка
 * переживёт переименование файла.
 *
 * <p>Как раскладывается сам лист, решает {@link ExcelTemplateWriter} —
 * общий с шаблоном упаковок.
 */
@Service
public class SkuTemplateService {

    /**
     * Предел строк при загрузке. Сам шаблон на столько строк не
     * растягивается (см. {@link #TEMPLATE_ROWS}) — это ограничение
     * загрузчика и граница, до которой расставлена проверка данных.
     * Тот же предел у упаковок: решение заказчика от 26.10.2026.
     */
    public static final int MAX_ROWS = 100;

    /** Сколько строк данных в умной таблице шаблона — см. {@link ExcelTemplateWriter}. */
    public static final int TEMPLATE_ROWS = ExcelTemplateWriter.TEMPLATE_ROWS;

    private static final String SHEET = "Номенклатура";

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final ColumnService columns;
    private final SkuWriteService sku;
    private final CurrentUser current;

    public SkuTemplateService(JdbcClient jdbc, DepositorScope depositorScope,
                              ColumnService columns, SkuWriteService sku,
                              CurrentUser current) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.columns = columns;
        this.sku = sku;
        this.current = current;
    }

    /** Готовый файл и имя, под которым его отдать. */
    public record Template(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public Template build(PortalUser user) {
        // §6.3: разрешение проверяется на сервере. Шаблон — часть загрузки:
        // кому загружать нельзя, тому и шаблон незачем, он только вводил
        // бы в заблуждение.
        current.requireExcelUpload("sku");
        depositorScope.apply();
        Map<String, Object> depositor = depositor(jdbc, depositorScope);
        Map<String, String> labels = new HashMap<>();
        Map<String, Integer> order = new HashMap<>();
        readLayout(columns.layout(user, "sku"), labels, order);

        // Колонка, запрещённая поклажедателю, в раскладке отсутствует —
        // значит её не будет и в шаблоне.
        List<ExcelField> fields = SkuTemplateFields.ordered(order).stream()
                .filter(f -> labels.containsKey(f.catalogId()))
                .toList();
        if (fields.isEmpty()) {
            throw Fail.conflict("error.template.no_columns");
        }

        try {
            byte[] bytes = ExcelTemplateWriter.write(SHEET, fields, labels, dictionaryValues(),
                    Map.of(), str(depositor.get("code")), MAX_ROWS);
            return new Template(ExcelTemplateWriter.fileName("Шаблон_Номенклатура",
                    str(depositor.get("org_name"))), bytes);
        } catch (IOException e) {
            throw Fail.internal("error.template.build_failed");
        }
    }

    /** Подписи и порядок колонок из раскладки пользователя. */
    static void readLayout(List<Map<String, Object>> layout, Map<String, String> labels,
                           Map<String, Integer> order) {
        for (Map<String, Object> row : layout) {
            String id = String.valueOf(row.get("columnId"));
            labels.put(id, String.valueOf(row.get("label")));
            order.put(id, ((Number) row.get("sortOrder")).intValue());
        }
    }

    /** Код и наименование текущего поклажедателя. */
    static Map<String, Object> depositor(JdbcClient jdbc, DepositorScope depositorScope) {
        return jdbc.sql("SELECT code, org_name FROM dbo.depositor"
                        + " WHERE depositor_id = :id::uuid")
                .param("id", depositorScope.currentDepositorId())
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElse(Map.of());
    }

    static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Названия справочных значений — ровно те, что видит человек на экране. */
    private Map<String, List<String>> dictionaryValues() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> all = sku.dictionaries();
        for (String kind : new HashSet<>(SkuTemplateFields.DICTIONARY.values())) {
            out.put(kind, names(all.getOrDefault(kind, List.of())));
        }
        return out;
    }

    /** Непустые названия из строк справочника. */
    static List<String> names(List<Map<String, Object>> rows) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                names.add(String.valueOf(name));
            }
        }
        return names;
    }

    /* ============ Чтение метки при загрузке ============ */

    /**
     * Код поклажедателя из скрытой метки, если она есть.
     * Нужен загрузке: файл, собранный для другого клиента, принимать нельзя.
     */
    public static String markerOf(Workbook wb) {
        return ExcelTemplateWriter.markerOf(wb);
    }
}
