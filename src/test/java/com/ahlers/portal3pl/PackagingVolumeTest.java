package com.ahlers.portal3pl;

import com.ahlers.portal3pl.catalog.SkuWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Объём упаковки по размерам (V82) — тем путём, каким упаковку
 * заводят и правят в карточке товара.
 *
 * <p>Правило держит триггер в базе, поэтому проверяется запись, а не
 * экран: экран лишь показывает число заранее. Если кто-то уберёт
 * триггер или поменяет формулу, упадёт здесь, а не у клиента.
 */
class PackagingVolumeTest extends PortalTestBase {

    private static final String PREFIX = "QA-VOL-";

    @Autowired
    SkuWriteService sku;

    @AfterEach
    void cleanUp() {
        inContext("DELETE FROM dbo.product_packaging WHERE sku_id IN"
                + " (SELECT sku_id FROM dbo.sku WHERE code LIKE '" + PREFIX + "%')");
        inContext("DELETE FROM dbo.sku WHERE code LIKE '" + PREFIX + "%'");
    }

    private String product() {
        signIn();
        return String.valueOf(sku.createSku(Map.of("code", PREFIX + "A",
                "name", "Проверка объёма", "uom_id", 1)).get("skuId"));
    }

    private String packaging(String skuId, Map<String, Object> more) {
        signIn();
        Map<String, Object> values = new LinkedHashMap<>(more);
        values.put("packaging_code", "BOX");
        values.put("coefficient", 6);
        sku.createPackaging(skuId, values);
        return singleValue("SELECT packaging_id::text FROM dbo.product_packaging"
                + " WHERE sku_id = '" + skuId + "' AND packaging_code = 'BOX'");
    }

    private String volume(String packagingId) {
        return singleValue("SELECT COALESCE(volume_m3::text, '') FROM dbo.product_packaging"
                + " WHERE packaging_id = '" + packagingId + "'");
    }

    @Test
    void allThreeDimensionsGiveTheVolumeWhateverWasTyped() {
        String box = packaging(product(), Map.of("length_cm", "40", "width_cm", "30",
                "height_cm", "20", "volume_m3", "99"));
        assertEquals("0.02400", volume(box), "40 × 30 × 20 см = 0,024 м³");
    }

    @Test
    void changingADimensionRecalculates() {
        String box = packaging(product(), Map.of("length_cm", "40", "width_cm", "30",
                "height_cm", "20"));
        signIn();
        sku.updatePackaging(box, Map.of("height_cm", "10"));
        assertEquals("0.01200", volume(box));
    }

    @Test
    void withoutAllDimensionsTheVolumeIsKeptAsTyped() {
        String box = packaging(product(), Map.of("length_cm", "40", "width_cm", "30",
                "volume_m3", "0.5"));
        assertEquals("0.50000", volume(box), "высоты нет — объём введён руками");
    }

    /** Дробные сантиметры: пять знаков, округление по правилам. */
    @Test
    void fractionalDimensionsAreRoundedToFiveDigits() {
        String box = packaging(product(), Map.of("length_cm", "12.5", "width_cm", "3.3",
                "height_cm", "7.1"));
        // 12,5 × 3,3 × 7,1 = 292,875 см³ = 0,000292875 м³ → 0,00029
        assertEquals("0.00029", volume(box));
    }
}
