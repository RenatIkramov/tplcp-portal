package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Чтение справочников целиком. Фильтра по поклажедателю нет — его
 * накладывает RLS.
 *
 * <p>Страниц здесь нет намеренно: это списки, которые экран держит
 * целиком — склады, поставщики, тарифы, а также товары и упаковки
 * для выбора в окне строки. У кого есть экран с отбором и страницами,
 * тот читается через {@code ListRepository}.
 */
@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(CatalogType type, boolean withArchived) {
        // Архивные записи по умолчанию не показываем: в документах их выбрать
        // нельзя, и в рабочем списке они только мешают. Экран может попросить
        // их отдельно — чтобы открыть карточку и вернуть позицию в работу.
        String archive = type.hasArchive()
                ? "WHERE (:withArchived::boolean OR is_active)"
                : "";

        String sql = "SELECT * FROM dbo.%s %s ORDER BY %s"
                .formatted(type.view(), archive, type.orderBy());

        var spec = jdbc.sql(sql);
        if (type.hasArchive()) {
            spec = spec.param("withArchived", withArchived);
        }
        return SqlRows.toCamelCase(spec.query().listOfRows());
    }
}
