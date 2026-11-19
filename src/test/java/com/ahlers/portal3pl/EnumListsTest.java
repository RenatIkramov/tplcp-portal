package com.ahlers.portal3pl;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.documents.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Согласованные списки перечислений (V88): типы заказов и поступлений,
 * валюты по ISO 4217, ДОПОГ.
 *
 * <p>Проверяется то, что увидит человек, — подписи в выпадающих
 * списках карточки документа на его языке, — и коды, которыми портал
 * обменивается с WMS.
 */
class EnumListsTest extends PortalTestBase {

    @Autowired
    DocumentService documents;

    @SuppressWarnings("unchecked")
    private List<String> names(String dictionary) {
        signIn();
        Map<String, Object> all = documents.dictionaries();
        return ((List<Map<String, Object>>) all.get(dictionary)).stream()
                .map(r -> String.valueOf(r.get("name")))
                .toList();
    }

    @Test
    void documentTypesAreTheAgreedOnesInTheUserLanguage() {
        PortalUser user = authService.authenticate(USER, USER_PASSWORD);
        assertEquals(List.of("Standard order", "Express", "Shipment to production",
                "Cross-docking", "Return to supplier", "Write-off"), names("orderTypes"));
        localeOf(user, "ru");
        assertEquals(List.of("Обычный заказ", "Экспресс", "Отгрузка в производство",
                "Кросс-докинг", "Возврат поставщику", "Списание"), names("orderTypes"));
        assertEquals(List.of("Обычная поставка", "Импорт (таможенный склад)", "Возврат от покупателя",
                "Производство", "Кросс-докинг", "Оприходование"), names("receiptDocTypes"));
    }

    /** Код типа — две цифры: по нему тип уходит в WMS. */
    @Test
    void typeCodesAreTwoDigits() {
        assertEquals("01,02,03,04,05,06", singleValue(
                "SELECT string_agg(code, ',' ORDER BY sort_order) FROM dbo.enum_order_type"));
        assertEquals("01,02,03,04,05,06", singleValue(
                "SELECT string_agg(code, ',' ORDER BY sort_order) FROM dbo.enum_receipt_doc_type"));
    }

    /**
     * Валюта: код перечисления — цифровой ISO 4217, буквенный остаётся
     * для людей и Excel.
     */
    @Test
    void currenciesCarryBothIsoCodes() {
        assertEquals("KZT 398,USD 840,EUR 978,RUB 643,CNY 156", singleValue(
                "SELECT string_agg(code || ' ' || num_code, ',' ORDER BY sort_order) FROM dbo.enum_currency"));
        assertEquals("num_code", singleValue(
                "SELECT code_column FROM dbo.enum_registry WHERE enum_key = 'enum_currency'"));
    }

    @Test
    void hazardClassesFollowAdr() {
        assertEquals("0,1,2,3,4.1,4.2,4.3,5.1,5.2,6.1,6.2,7,8,9", singleValue(
                "SELECT string_agg(code, ',' ORDER BY sort_order) FROM dbo.enum_hazard_class"));
        assertEquals("0,I,II,III", singleValue(
                "SELECT string_agg(code, ',' ORDER BY sort_order) FROM dbo.enum_hazard_group"));
    }
}
