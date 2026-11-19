package com.ahlers.portal3pl.excel;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Текст ячейки при загрузке Excel: неразрывные пробелы снимаются
 * так же, как обычные.
 *
 * <p>Прежде замена была {@code replace(' ', ' ')} — пробел на пробел,
 * то есть ничего, — и код «ABC-1» с неразрывным пробелом в конце,
 * скопированный из 1С или письма, в справочнике не находился. Пробелы
 * здесь записаны кодами: глазом U+00A0 от обычного не отличить, на этом
 * ошибка и прожила.
 */
class ExcelCellTextTest {

    @Test
    void nonBreakingSpacesAreTrimmedLikeOrdinaryOnes() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Row row = wb.createSheet("t").createRow(0);
            row.createCell(0).setCellValue(" ABC-1 ");
            row.createCell(1).setCellValue("1 234");
            row.createCell(2).setCellValue("  обычный  ");
            DataFormatter fmt = new DataFormatter();

            assertEquals("ABC-1", ExcelImportSupport.text(row, 0, fmt));
            assertEquals("1 234", ExcelImportSupport.text(row, 1, fmt),
                    "узкий неразрывный пробел между разрядами — обычный пробел");
            assertEquals("обычный", ExcelImportSupport.text(row, 2, fmt));
            assertEquals("", ExcelImportSupport.text(row, 5, fmt), "пустая ячейка — пустая строка");
        }
    }
}
