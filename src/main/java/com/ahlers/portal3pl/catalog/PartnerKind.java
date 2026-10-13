package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.Fail;

import java.util.List;
import java.util.Map;

/**
 * Поставщики и грузополучатели. Справочники устроены одинаково — код,
 * наименование, БИН, юридический адрес и контакты, — поэтому ведутся одним
 * сервисом, а различия собраны здесь: таблица, представление, имя ключа и
 * чем отличается в речи («поставщик» / «грузополучатель»).
 *
 * <p>Как и у документов, перечисление заодно защищает от подстановки
 * произвольного имени таблицы в запрос: из адреса приходит не имя таблицы,
 * а одно из двух известных значений.
 */
public enum PartnerKind {

    SUPPLIER("suppliers", "supplier", "v_suppliers", "supplier_id",
            "error.supplier", "supplier_usage",
            Map.of("receipts", "error.usage.receipts")),

    CONSIGNEE("consignees", "consignee", "v_consignees", "consignee_id",
            "error.consignee", "consignee_usage",
            // адреса удаляются вместе с карточкой, помехой их не считаем:
            // в счёт идут только документы
            Map.of("orders", "error.usage.orders"));

    /** Колонки, доступные правке, и их тип для приведения в SQL. */
    public static final Map<String, String> COLUMNS = Map.of(
            "code", "varchar",
            "name", "varchar",
            "bin", "varchar",
            "legal_address", "varchar",
            "contact_name", "varchar",
            "contact_phone", "varchar",
            "contact_email", "varchar");

    public static final List<String> REQUIRED = List.of("code", "name");

    private final String path;
    private final String table;
    private final String view;
    private final String key;
    private final String errorPrefix;
    private final String usageFunction;
    private final Map<String, String> usageLabels;

    PartnerKind(String path, String table, String view, String key, String errorPrefix,
                String usageFunction, Map<String, String> usageLabels) {
        this.path = path;
        this.table = table;
        this.view = view;
        this.key = key;
        this.errorPrefix = errorPrefix;
        this.usageFunction = usageFunction;
        this.usageLabels = usageLabels;
    }

    public static PartnerKind ofPath(String path) {
        for (PartnerKind k : values()) {
            if (k.path.equals(path)) {
                return k;
            }
        }
        throw Fail.notFound("error.directory.unknown", "path", path);
    }

    public String table() {
        return table;
    }

    public String view() {
        return view;
    }

    public String key() {
        return key;
    }

    /**
     * Ключ сообщения об отказе для этого справочника.
     *
     * <p>Раньше здесь лежали русские слова в двух падежах и они
     * подставлялись в текст ошибки. В английском сообщении оставалось
     * русское слово, да и падежей в других языках нет вовсе. Теперь
     * у каждого справочника свой ключ: {@code error.supplier.not_found},
     * {@code error.consignee.not_found} — и переводчик видит фразу
     * целиком, с согласованием и родом.
     */
    public String error(String what) {
        return errorPrefix + "." + what;
    }

    public String usageFunction() {
        return usageFunction;
    }

    /** Колонка ответа функции использования → ключ текста «в заказах (3)». */
    public Map<String, String> usageLabels() {
        return usageLabels;
    }
}
