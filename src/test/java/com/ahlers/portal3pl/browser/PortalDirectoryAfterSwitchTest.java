package com.ahlers.portal3pl.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Справочники портала переживают выбор и смену поклажедателя.
 *
 * <p>ЧТО БЫЛО. Администратор входит, выбирает клиента — и справочник
 * «Поклажедатели» пуст. Обновил страницу — список на месте. Не выбрал
 * клиента вовсе — список виден сразу. Заказчик так и описал.
 *
 * <p>ПОЧЕМУ. Списки складов, поклажедателей и организаций оператора
 * лежали в {@code PORTAL.catalog} — хранилище данных ВЫБРАННОГО
 * клиента. Смена клиента законно забывает его данные целиком
 * ({@code PORTAL.forgetAll}), а перечитать справочники портала было
 * некому: грузятся они на {@code onSession} («кто вошёл»), смена же
 * клиента прогоняет только {@code onLogin} («чьи данные»). Пустой
 * список при этом ничем не отличается от «записей нет» — человек
 * видит не поломку, а неправду.
 *
 * <p>ЧТО ПРОВЕРЯЕМ. Не наличие строк в хранилище, а строки в таблице
 * на экране: именно на экран человек и смотрел. Проверка идёт тем же
 * путём — вход, выбор клиента, переход в справочник, — и повторяет его
 * ещё раз на втором клиенте: забывчивость при ПЕРЕКЛЮЧЕНИИ и при
 * первом выборе — одна и та же беда, но разные вызовы.
 */
class PortalDirectoryAfterSwitchTest extends BrowserTestBase {

    @Test
    void portalDirectoriesStayFilledAfterChoosingADepositor() {
        // Администратора создаёт и наделяет доступами основа тестов:
        // без этого у него не было бы ни пароля, ни поклажедателей.
        portalAdmin();
        signInUi(ADMIN, ADMIN_PASSWORD);

        // Справочники портала грузятся сразу после входа, до выбора клиента.
        page.waitForFunction("() => PORTAL.portalDirLists"
                + " && (PORTAL.portalDirLists.rows.depositorsDir || []).length > 0");

        @SuppressWarnings("unchecked")
        List<String> depositors = (List<String>) page.evaluate(
                "() => (PORTAL.session.depositors || []).map(d => d.depositorId)");
        assertTrue(depositors.size() >= 2,
                "у администратора меньше двух поклажедателей — выбор не нужен,"
                        + " проверять нечего: " + depositors);

        for (String id : depositors.subList(0, 2)) {
            page.evaluate("id => portalSelectDepositor(id)", id);
            page.waitForFunction("id => PORTAL.session && PORTAL.session.depositorId === id", id);

            for (String dir : List.of("depositorsDir", "warehousesDir")) {
                openReferences(dir);
                int rows = rowCount(dir);
                assertTrue(rows > 0, "после выбора поклажедателя справочник " + dir
                        + " пуст — список забыли и не перечитали");
            }
        }
        assertNoJsErrors();
    }

    /** Строк в таблице справочника — считаем по экрану, а не по хранилищу. */
    private int rowCount(String dirName) {
        String body = dirName + "Body";
        page.waitForFunction("id => document.querySelectorAll('#' + id + ' tr').length > 0", body);
        // Пустой список — тоже одна строка, с подписью вместо данных.
        return ((Number) page.evaluate(
                "id => document.querySelectorAll('#' + id + ' tr td strong').length", body))
                .intValue();
    }
}
