package com.ahlers.portal3pl.excel;

import com.ahlers.portal3pl.admin.AdminUserService;
import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.SqlRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final ActorText texts;

    public ImportLogService(JdbcClient jdbc, AdminUserService users, ActorText texts) {
        this.jdbc = jdbc;
        this.users = users;
        this.texts = texts;
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
        out.put("issues", inReadersLanguage(
                rows("SELECT * FROM dbo.admin_excel_import_issues(:dep::uuid, 500)", depositorId)));
        return out;
    }

    /**
     * Причина на языке администратора, а не того, кто загружал (V98).
     *
     * <p>У причины с ключом фраза собирается заново, по ключу и
     * подстановкам. У записей до V98 (документы) и до V102 (товары
     * и упаковки) ключа нет — остаётся записанный текст.
     */
    private List<Map<String, Object>> inReadersLanguage(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new LinkedHashMap<>(row);
            Map<String, Object> params = params(r.remove("paramNames"), r.remove("paramValues"));
            Object key = r.get("messageKey");
            if (key != null) {
                r.put("message", texts.text(String.valueOf(key), params));
            }
            out.add(r);
        }
        return out;
    }

    /**
     * Подстановки причины из двух массивов базы — имён и значений.
     * Общий шаг журнала и окна проверки загрузки: JSON разбирает база,
     * а не портал.
     */
    static Map<String, Object> params(Object names, Object values) {
        List<String> n = strings(names);
        List<String> v = strings(values);
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < n.size() && i < v.size(); i++) {
            out.put(n.get(i), v.get(i));
        }
        return out;
    }

    private static List<String> strings(Object array) {
        if (array instanceof java.sql.Array a) {
            try {
                Object[] items = (Object[]) a.getArray();
                List<String> out = new ArrayList<>(items.length);
                for (Object o : items) {
                    out.add(o == null ? null : String.valueOf(o));
                }
                return out;
            } catch (java.sql.SQLException e) {
                return List.of();
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> rows(String sql, String depositorId) {
        return SqlRows.toCamelCase(jdbc.sql(sql)
                .param("dep", depositorId)
                .query()
                .listOfRows());
    }
}
