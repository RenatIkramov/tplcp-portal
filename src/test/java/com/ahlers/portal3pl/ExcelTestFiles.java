package com.ahlers.portal3pl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Помощники проверок шаблонов документов Excel (заказ, поступление):
 * открыть книгу, найти умную таблицу, заполнить поле по ключу и строки
 * состава — как это сделал бы человек.
 */
final class ExcelTestFiles {

    private ExcelTestFiles() {
    }

    static XSSFWorkbook open(byte[] bytes) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static XSSFTable table(XSSFWorkbook wb, String name) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            for (XSSFTable t : wb.getSheetAt(i).getTables()) {
                if (t.getName().equals(name)) {
                    return t;
                }
            }
        }
        return null;
    }

    static List<String> keys(XSSFTable t) {
        AreaReference a = t.getArea();
        int c = a.getFirstCell().getCol() + 2;
        List<String> out = new ArrayList<>();
        for (int r = a.getFirstCell().getRow() + 1; r <= a.getLastCell().getRow(); r++) {
            Row row = t.getXSSFSheet().getRow(r);
            if (row != null && row.getCell(c) != null) {
                out.add(row.getCell(c).getStringCellValue());
            }
        }
        return out;
    }

    /** Значение поля по ключу — как это сделал бы человек в колонке «Значение». */
    static void set(XSSFWorkbook wb, String tableName, String key, String value) {
        XSSFTable t = table(wb, tableName);
        assertNotNull(t, "нет таблицы " + tableName);
        AreaReference a = t.getArea();
        int c = a.getFirstCell().getCol();
        for (int r = a.getFirstCell().getRow() + 1; r <= a.getLastCell().getRow(); r++) {
            Row row = t.getXSSFSheet().getRow(r);
            if (row != null && row.getCell(c + 2) != null
                    && key.equals(row.getCell(c + 2).getStringCellValue())) {
                row.getCell(c + 1).setCellValue(value);
                return;
            }
        }
        throw new AssertionError("в таблице " + tableName + " нет поля " + key);
    }

    /** Строки состава — с первой строки данных, ниже таблицы тоже. */
    static void goods(XSSFWorkbook wb, List<List<String>> rows) {
        XSSFTable t = table(wb, "Goods");
        assertNotNull(t);
        XSSFSheet sheet = t.getXSSFSheet();
        int first = t.getArea().getFirstCell().getRow() + 1;
        for (int i = 0; i < rows.size(); i++) {
            Row row = sheet.getRow(first + i) != null ? sheet.getRow(first + i) : sheet.createRow(first + i);
            List<String> values = rows.get(i);
            for (int c = 0; c < values.size(); c++) {
                if (!values.get(c).isEmpty()) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
        }
    }
}
