package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Журнал загрузок из Excel — подвкладка администрирования
 * «Ошибки загрузки Excel» (§8.5.6).
 *
 * <p>Администратор ищет здесь причину, по которой у клиента что-то
 * не загрузилось. Поэтому главное на экране — плоский список
 * замечаний: когда, кто, какой файл, строка, что не так. Сами
 * загрузки идут рядом списком, для сводки «что вообще грузили».
 *
 * <p>Читается через функции с правами владельца: таблицы под RLS,
 * а журнал нужен по всем поклажедателям — под RLS администратор видел
 * бы только текущего клиента. Право на раздел проверяет портал, как
 * и в остальном администрировании.
 *
 * <p>Замечание в журнале означает, что строка в справочник
 * <b>не записана</b>. Записанные строки в журнале не нужны: их
 * результат виден в самом справочнике.
 */
@Service
public class ImportLogService {

    private final JdbcClient jdbc;
    private final AdminUserService users;

    public ImportLogService(JdbcClient jdbc, AdminUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /**
     * Журнал: загрузки и замечания.
     *
     * @param depositorId фильтр по клиенту; null — все
     */
    @Transactional(readOnly = true)
    public Map<String, Object> log(PortalUser actor, String depositorId) {
        users.requireAdmin(actor);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imports", rows("SELECT * FROM dbo.admin_excel_imports(:dep::uuid, 200)",
                depositorId));
        out.put("issues", rows("SELECT * FROM dbo.admin_excel_import_issues(:dep::uuid, 500)",
                depositorId));
        return out;
    }

    private List<Map<String, Object>> rows(String sql, String depositorId) {
        return SqlRows.toCamelCase(jdbc.sql(sql)
                .param("dep", depositorId)
                .query()
                .listOfRows());
    }
}
