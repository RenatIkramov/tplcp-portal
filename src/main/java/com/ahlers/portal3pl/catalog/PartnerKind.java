package com.ahlers.portal3pl.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            "поставщика", "Поставщик", "supplier_usage",
            Map.of("receipts", "поступлениях")),

    CONSIGNEE("consignees", "consignee", "v_consignees", "consignee_id",
            "грузополучателя", "Грузополучатель", "consignee_usage",
            // адреса удаляются вместе с карточкой, помехой их не считаем:
            // в счёт идут только документы
            Map.of("orders", "заказах"));

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
    private final String accusative;
    private final String nominative;
    private final String usageFunction;
    private final Map<String, String> usageLabels;

    PartnerKind(String path, String table, String view, String key, String accusative,
                String nominative, String usageFunction, Map<String, String> usageLabels) {
        this.path = path;
        this.table = table;
        this.view = view;
        this.key = key;
        this.accusative = accusative;
        this.nominative = nominative;
        this.usageFunction = usageFunction;
        this.usageLabels = usageLabels;
    }

    public static PartnerKind ofPath(String path) {
        for (PartnerKind k : values()) {
            if (k.path.equals(path)) {
                return k;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный справочник: " + path);
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

    /** «создать поставщика», «грузополучателя не найден» — винительный падеж. */
    public String accusative() {
        return accusative;
    }

    /** «Поставщик с кодом … уже есть» — именительный. */
    public String nominative() {
        return nominative;
    }

    public String usageFunction() {
        return usageFunction;
    }

    /** Колонка ответа функции использования → как назвать её человеку. */
    public Map<String, String> usageLabels() {
        return usageLabels;
    }
}
