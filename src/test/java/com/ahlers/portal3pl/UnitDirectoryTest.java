package com.ahlers.portal3pl;

import com.ahlers.portal3pl.catalog.UnitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Единицы измерения (V38): справочник общий для всех поклажедателей.
 * <p>
 * Главное, что здесь проверяется: список видят все, править вправе
 * только администратор, а единицу, которой пользуются товары или
 * запасы, удалить нельзя — её отключают.
 */
class UnitDirectoryTest extends PortalTestBase {

    /**
     * Свои единицы помечаем кодом МКЕИ из заведомо свободного диапазона.
     *
     * <p>Ровно три знака: в `enum_uom` колонка `mkei_code` —
     * varchar(3), таков и сам код МКЕИ. Пятизначный код роняет вставку
     * ошибкой базы, а не отказом портала — на этом тест уже падал.
     */
    private static final String MKEI = "990";
    private static final String MKEI_OTHER = "991";

    @Autowired
    UnitService units;

    @AfterEach
    void removeTestRows() {
        // enum_uom не под RLS, поэтому убирается обычным запросом
        inContext("DELETE FROM dbo.enum_uom WHERE mkei_code LIKE '99%'");
    }

    @Test
    void depositorSeesListButMayNotChangeIt() {
        signIn();
        Map<String, Object> answer = units.list();
        assertFalse(rows(answer).isEmpty(), "без единиц измерения не заполнить ни один товар");
        assertEquals(Boolean.FALSE, answer.get("canEdit"),
                "поклажедателю справочник только для чтения: он общий для всех");

        expectRejection(403, () -> units.save(null,
                new UnitService.Unit(MKEI, "ящ", "Ящик", 900, true)));
        expectRejection(403, () -> units.delete(unitId("шт")));
    }

    @Test
    void administratorCreatesRenamesAndArchives() {
        portalAdmin();
        signInAs(ADMIN, DEPOSITOR);

        Map<String, Object> after = units.save(null,
                new UnitService.Unit(MKEI, "ящ", "Ящик", 900, true));
        assertEquals(Boolean.TRUE, after.get("canEdit"));
        Map<String, Object> row = find(after, "ящ");
        assertNotNull(row, "созданная единица должна появиться в списке");
        assertEquals("Ящик", row.get("name"));
        assertEquals(MKEI, row.get("mkeiCode"));

        int id = Integer.parseInt(String.valueOf(row.get("unitId")));

        row = find(units.save(id, new UnitService.Unit(MKEI, "ящ", "Ящик деревянный", 900, true)),
                "ящ");
        assertEquals("Ящик деревянный", row.get("name"));

        // Отключение — рабочий способ вывести единицу из обращения
        row = find(units.setActive(id, false), "ящ");
        assertEquals(Boolean.FALSE, row.get("isActive"));
        row = find(units.setActive(id, true), "ящ");
        assertEquals(Boolean.TRUE, row.get("isActive"));

        assertNull(find(units.delete(id), "ящ"), "свободную единицу удалить можно");
    }

    @Test
    void tooLongValuesAreRefusedByThePortalNotByTheDatabase() {
        portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        // Код МКЕИ — три знака, обозначение — десять. Портал обязан
        // сказать это словами, а не отдать ошибку базы про varchar(3).
        expectRejection(400, () -> units.save(null,
                new UnitService.Unit("99001", "ящ", "Ящик", 900, true)));
        expectRejection(400, () -> units.save(null,
                new UnitService.Unit(MKEI, "ящик деревянный", "Ящик", 900, true)));
    }

    @Test
    void codeAndNameAreRequired() {
        portalAdmin();
        signInAs(ADMIN, DEPOSITOR);

        expectRejection(400, () -> units.save(null,
                new UnitService.Unit(MKEI, "", "Ящик", null, true)));
        expectRejection(400, () -> units.save(null,
                new UnitService.Unit(MKEI, "ящ", "  ", null, true)));
        // Без кода МКЕИ единицу не опознают внешние системы
        expectRejection(400, () -> units.save(null,
                new UnitService.Unit(null, "ящ", "Ящик", null, true)));
    }

    @Test
    void unitInUseCannotBeDeleted() {
        portalAdmin();
        signInAs(ADMIN, DEPOSITOR);

        // «шт» стоит в товарах демонстрационных данных — и это проверяется,
        // а не предполагается: иначе тест прошёл бы и на пустой базе.
        int id = unitId("шт");
        assertTrue(usage(id) > 0, "для проверки нужна единица, которой пользуются");
        expectRejection(409, () -> units.delete(id));
        assertNotNull(find(units.list(), "шт"), "после отказа единица должна остаться");
    }

    @Test
    void sameSymbolTwiceIsRejected() {
        portalAdmin();
        signInAs(ADMIN, DEPOSITOR);

        units.save(null, new UnitService.Unit(MKEI, "ящ", "Ящик", 900, true));
        // Два «ящ» в справочнике означают, что в карточке товара человек
        // выбирает между двумя одинаковыми строками.
        expectRejection(409, () -> units.save(null,
                new UnitService.Unit(MKEI_OTHER, "ящ", "Ящик другой", 901, true)));
        // Регистр не спасает: «ЯЩ» и «ящ» для человека одно и то же
        expectRejection(409, () -> units.save(null,
                new UnitService.Unit(MKEI_OTHER, "ЯЩ", "Ящик другой", 901, true)));
        // Код МКЕИ тоже один на единицу — иначе внешняя система
        // не поймёт, какую из двух мы имеем в виду
        expectRejection(409, () -> units.save(null,
                new UnitService.Unit(MKEI, "пал", "Паллета", 902, true)));
    }

    @Test
    void missingUnitIsNotFound() {
        portalAdmin();
        signInAs(ADMIN, DEPOSITOR);
        expectRejection(404, () -> units.setActive(32000, false));
        expectRejection(404, () -> units.delete(32000));
    }

    /* ---------------- Вспомогательное ---------------- */

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("units");
    }

    private Map<String, Object> find(Map<String, Object> answer, String symbol) {
        return rows(answer).stream()
                .filter(r -> symbol.equals(r.get("symbol")))
                .findFirst()
                .orElse(null);
    }

    private int unitId(String symbol) {
        Map<String, Object> row = find(units.list(), symbol);
        assertNotNull(row, "в справочнике нет единицы «" + symbol + "»");
        return Integer.parseInt(String.valueOf(row.get("unitId")));
    }

    private long usage(int id) {
        return Long.parseLong(singleValue(
                "SELECT (sku_count + stock_count)::text FROM dbo.unit_usage(" + id + "::smallint)"));
    }
}
