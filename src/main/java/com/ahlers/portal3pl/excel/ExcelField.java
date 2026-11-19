package com.ahlers.portal3pl.excel;

/**
 * Колонка шаблона Excel и поле, в которое она загружается.
 *
 * <p>Общая для всех справочников, которые грузятся из Excel: товаров
 * и упаковок. Состав у каждого свой ({@link SkuTemplateFields},
 * {@link PackagingTemplateFields}), а описание колонки одно — по нему
 * и шаблон расставляет проверку данных, и загрузчик приводит значение.
 * Будь описаний два, шаблон и загрузчик однажды разошлись бы в том,
 * что считать числом.
 *
 * @param column     имя поля в базе
 * @param catalogId  идентификатор колонки в справочнике колонок — по нему
 *                   берётся подпись и ищется заголовок файла
 * @param kind       чем заполняется
 * @param dictionary имя справочника для выпадающего списка, иначе null
 * @param required   обязательна ли
 * @param textFormat хранить ли ячейку текстом: иначе Excel сам превратит
 *                   {@code 0012} в {@code 12}, а {@code 1-2} в дату
 * @param positive   число строго больше нуля, а не «не меньше»: у
 *                   коэффициента упаковки ноль бессмыслен, он делит
 */
public record ExcelField(String column, String catalogId, Kind kind, String dictionary,
                         boolean required, boolean textFormat, boolean positive) {

    /** Как вести себя с ячейкой: тип определяет проверку в Excel и разбор. */
    public enum Kind { TEXT, NUMBER, BOOLEAN, DICTIONARY }
}
