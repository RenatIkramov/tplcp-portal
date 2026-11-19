package com.ahlers.portal3pl.excel;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * Прогрев библиотеки Excel сразу после запуска портала.
 *
 * <p>Первый шаблон после старта собирался около двух секунд, следующие —
 * за десятки миллисекунд. Разница — не работа портала, а загрузка самой
 * библиотеки: при первом обращении Java подгружает тысячи её классов
 * и схемы формата .xlsx. Человек, нажавший «Скачать шаблон» первым,
 * ждал эти секунды и нажимал ещё раз.
 *
 * <p>Поэтому одна пустая книга собирается в фоне, как только портал
 * поднялся: загрузка классов происходит до первого клика. Запуск портала
 * это не задерживает — работа идёт в отдельном потоке, — а ошибка здесь
 * ни на что не влияет: шаблон всё равно соберётся по запросу, просто
 * медленнее.
 */
@Component
public class ExcelWarmup {

    private static final Logger log = LoggerFactory.getLogger(ExcelWarmup.class);

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread worker = new Thread(ExcelWarmup::buildEmptyWorkbook, "excel-warmup");
        worker.setDaemon(true);
        worker.start();
    }

    /** То же, что делают шаблон и загрузка: лист, ячейка, список, примечание, запись и чтение. */
    static void buildEmptyWorkbook() {
        long started = System.nanoTime();
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("warmup");
            sheet.createRow(0).createCell(0).setCellValue("warmup");
            var helper = sheet.getDataValidationHelper();
            sheet.addValidationData(helper.createValidation(
                    helper.createExplicitListConstraint(new String[]{"a", "b"}),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, 1, 0, 0)));
            var anchor = wb.getCreationHelper().createClientAnchor();
            var comment = sheet.createDrawingPatriarch().createCellComment(anchor);
            comment.setString(wb.getCreationHelper().createRichTextString("warmup"));
            sheet.getRow(0).getCell(0).setCellComment(comment);
            wb.write(out);
            // и обратно — так же, как разбирается загруженный файл
            new XSSFWorkbook(new java.io.ByteArrayInputStream(out.toByteArray())).close();
            log.info("Библиотека Excel прогрета за {} мс", (System.nanoTime() - started) / 1_000_000);
        } catch (Exception | LinkageError e) {
            log.warn("Прогрев библиотеки Excel не удался: {}", e.toString());
        }
    }
}
