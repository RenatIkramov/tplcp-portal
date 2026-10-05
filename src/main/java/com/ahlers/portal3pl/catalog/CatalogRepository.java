package com.ahlers.portal3pl.catalog;

import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Чтение справочников. Фильтра по поклажедателю нет — его накладывает RLS.
 */
@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(CatalogType type, String search, String group, String itemType) {
        String searchCondition = type.searchColumns().stream()
                .map(c -> "COALESCE(" + c + "::text,'') ILIKE :search::text")
                .collect(Collectors.joining(" OR "));

        String extra = type == CatalogType.SKU
                ? """
                  AND (:group::text IS NULL OR item_group_name = :group::text)
                  AND (:itemType::text IS NULL OR item_type_name = :itemType::text)
                  """
                : "";

        String sql = """
                SELECT * FROM dbo.%s
                WHERE (:search::text IS NULL OR (%s))
                %s
                ORDER BY %s
                """.formatted(type.view(), searchCondition, extra, type.orderBy());

        var spec = jdbc.sql(sql)
                .param("search", blank(search) ? null : "%" + search.trim() + "%");
        if (type == CatalogType.SKU) {
            spec = spec.param("group", blank(group) ? null : group)
                       .param("itemType", blank(itemType) ? null : itemType);
        }
        return SqlRows.toCamelCase(spec.query().listOfRows());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
