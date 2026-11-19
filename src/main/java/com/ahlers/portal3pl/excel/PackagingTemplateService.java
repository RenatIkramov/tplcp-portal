package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.catalog.SkuWriteService;
import com.ahlers.portal3pl.columns.ColumnService;
import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.excel.SkuTemplateService.Template;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Шаблон Excel для загрузки упаковок.
 *
 * <p>Устроен так же, как шаблон номенклатуры, и собирается тем же
 * {@link ExcelTemplateWriter}: умная таблица, проверка данных, скрытый
 * лист со списками и меткой клиента, подписи из справочника колонок
 * с учётом переименований. Отличие — в составе колонок
 * ({@link PackagingTemplateFields}).
 *
 * <p>Код товара — текстом, без выпадающего списка (V97, решение
 * заказчика от 02.11.2026): товаров у клиента могут быть тысячи.
 * Загрузчик сверяет код с базой, по всем товарам клиента на момент
 * загрузки.
 */
@Service
public class PackagingTemplateService {

    /** Предел строк тот же, что у номенклатуры: решение заказчика от 26.10.2026. */
    public static final int MAX_ROWS = SkuTemplateService.MAX_ROWS;

    /** Умная таблица шаблона: латиницей и не переводится, как у документов (V102). */
    static final String TABLE = "Packaging";

    private final JdbcClient jdbc;
    private final DepositorScope depositorScope;
    private final ColumnService columns;
    private final SkuWriteService sku;
    private final CurrentUser current;
    private final ActorText texts;

    public PackagingTemplateService(JdbcClient jdbc, DepositorScope depositorScope,
                                    ColumnService columns, SkuWriteService sku,
                                    CurrentUser current, ActorText texts) {
        this.jdbc = jdbc;
        this.depositorScope = depositorScope;
        this.columns = columns;
        this.sku = sku;
        this.current = current;
        this.texts = texts;
    }

    @Transactional(readOnly = true)
    public Template build(PortalUser user) {
        // §6.3: разрешение проверяется на сервере. Шаблон — часть загрузки:
        // кому загружать нельзя, тому и шаблон незачем, он только вводил
        // бы в заблуждение.
        current.requireExcelUpload("packaging");
        depositorScope.apply();
        Map<String, Object> depositor = SkuTemplateService.depositor(jdbc, depositorScope);
        Map<String, String> labels = new HashMap<>();
        Map<String, Integer> order = new HashMap<>();
        SkuTemplateService.readLayout(columns.layout(user, "packaging"), labels, order);

        // Колонка, запрещённая поклажедателю, в раскладке отсутствует —
        // значит её не будет и в шаблоне. Без товара и кода упаковки
        // строку не опознать, и такой шаблон отдавать нельзя: человек
        // заполнил бы его и получил отказ при загрузке.
        List<ExcelField> fields = ExcelTemplateWriter
                .ordered(PackagingTemplateFields.fields().values(), order).stream()
                .filter(f -> labels.containsKey(f.catalogId()))
                .toList();
        boolean keys = fields.stream().anyMatch(f -> f.column().equals(PackagingTemplateFields.SKU))
                && fields.stream().anyMatch(f -> f.column().equals("packaging_code"));
        if (!keys) {
            throw Fail.conflict("error.template.no_packaging_columns");
        }

        Map<String, List<String>> dictionaries = new LinkedHashMap<>();
        dictionaries.put(PackagingTemplateFields.TYPE_DICTIONARY, SkuTemplateService.names(
                sku.dictionaries().getOrDefault(PackagingTemplateFields.TYPE_DICTIONARY,
                        List.of())));

        try {
            byte[] bytes = ExcelTemplateWriter.write(texts.of("excel.packaging.sheet"), TABLE, fields, labels,
                    dictionaries, Map.of(), ExcelTemplateWriter.BooleanText.of(texts),
                    SkuTemplateService.str(depositor.get("code")), MAX_ROWS, texts);
            return new Template(ExcelTemplateWriter.fileName(texts.of("common.text.packaging_template_file_name"),
                    SkuTemplateService.str(depositor.get("org_name"))), bytes);
        } catch (IOException e) {
            throw Fail.internal("error.template.build_failed");
        }
    }
}
