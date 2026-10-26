package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.common.Fail;

/**
 * Списки, которые не документы: запасы, товары, упаковки.
 *
 * <p>Перечисление заодно защищает от подстановки произвольного имени
 * представления в запрос: в SQL попадают только значения отсюда,
 * а не то, что пришло из адреса.
 */
public enum CatalogList implements ListKind {

    /**
     * Запасы.
     *
     * <p>Порядок по умолчанию — по коду товара и складу. Пара
     * устойчива: строка запасов это товар на складе, и дважды она
     * не встречается. Склад в хвосте обязателен: без него товар,
     * лежащий на двух складах, давал бы две строки с одинаковым
     * кодом в произвольном порядке — и при листании одна из них
     * могла бы попасться дважды, а вторая не попасться вовсе.
     */
    STOCK("stock", "v_stock_lines", (short) 11, "sku_code, warehouse_id", null),

    /**
     * Товары.
     *
     * <p>Порядок по коду: справочник ищут глазами, и алфавит здесь
     * полезнее даты. Хвост {@code sku_id} — ради устойчивости: код
     * товара уникален у одного поклажедателя, но администратор видит
     * сразу нескольких.
     *
     * <p>Архив: {@code is_active}. Погашенные товары в списке есть
     * только по просьбе — {@code withArchived=true}.
     */
    SKU("sku", "v_sku_full", (short) 4, "code, sku_id", "is_active"),

    /**
     * Упаковки.
     *
     * <p>Порядок — товар, затем вложенность: короб перед паллетой.
     * Хвост {@code packaging_id}: у одного товара бывает две упаковки
     * с одинаковым коэффициентом.
     *
     * <p>Архива нет: упаковка живёт при товаре, и гасится товар.
     */
    PACKAGING("packaging", "v_product_packaging", (short) 5,
            "sku_code, coefficient, packaging_id", null);

    private final String tableKey;
    private final String view;
    private final short objectTypeId;
    private final String defaultOrder;
    private final String archiveColumn;

    CatalogList(String tableKey, String view, short objectTypeId, String defaultOrder,
                String archiveColumn) {
        this.tableKey = tableKey;
        this.view = view;
        this.objectTypeId = objectTypeId;
        this.defaultOrder = defaultOrder;
        this.archiveColumn = archiveColumn;
    }

    public static CatalogList ofPath(String path) {
        for (CatalogList list : values()) {
            if (list.tableKey.equals(path)) {
                return list;
            }
        }
        throw Fail.notFound("error.list.unknown", "list", path);
    }

    @Override
    public String tableKey() {
        return tableKey;
    }

    @Override
    public String view() {
        return view;
    }

    @Override
    public short objectTypeId() {
        return objectTypeId;
    }

    @Override
    public String defaultOrder() {
        return defaultOrder;
    }

    @Override
    public String archiveColumn() {
        return archiveColumn;
    }
}
