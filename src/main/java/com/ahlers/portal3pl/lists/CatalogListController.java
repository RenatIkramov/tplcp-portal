package com.ahlers.portal3pl.lists;

import com.ahlers.portal3pl.auth.SessionUser;
import com.ahlers.portal3pl.catalog.CatalogService;
import com.ahlers.portal3pl.catalog.CatalogType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Товары и упаковки: страница списка и полный набор для выбора.
 * <pre>
 *   GET /api/sku?filter.group=Сырьё&page=2&sort=name&withArchived=true
 *   GET /api/packaging?filter.packagingTypeName=Короб
 *   GET /api/sku/options
 *   GET /api/packaging/options
 * </pre>
 *
 * <p>ПОЧЕМУ ДВА АДРЕСА НА ОДИН СПРАВОЧНИК. У них разные задачи, и
 * ответы у них разной формы — одним не обойтись.
 *
 * <p>Первый — ЭКРАН справочника. Товаров у клиента бывают тысячи, и
 * отбор, порядок и страницы считает сервер: ответ — двадцать строк
 * и «всего 1340». Щелчок по заголовку, поле на панели и ряд страниц
 * означают новый запрос, а не перерисовку того, что уже в браузере.
 *
 * <p>Второй — ВЫБОР товара: окно строки документа, карточка, колонка
 * «Используются упаковки». Там нужен весь набор целиком, потому что
 * искать человек начинает с середины наименования, и страницами такой
 * поиск не собрать. Поэтому полный набор остался — но отдельным
 * адресом, а не видом первого: иначе экран и окно строки спорили бы
 * об одном ответе, и выиграл бы тот, кто попросил последним.
 *
 * <p>{@code withArchived=true} у выбора нужен не ради списка, а ради
 * карточки: погашенный товар надо уметь открыть, чтобы вернуть его
 * в работу.
 */
@RestController
public class CatalogListController {

    private final ListService lists;
    private final CatalogService catalogs;
    private final SessionUser session;

    public CatalogListController(ListService lists, CatalogService catalogs,
                                 SessionUser session) {
        this.lists = lists;
        this.catalogs = catalogs;
        this.session = session;
    }

    @GetMapping("/api/{list:sku|packaging}")
    public Map<String, Object> page(@PathVariable String list,
                                    @RequestParam Map<String, String> params,
                                    HttpServletRequest request) {
        return lists.page(session.requireUser(request), CatalogList.ofPath(list), params);
    }

    /**
     * Весь справочник, без страниц.
     *
     * <p>Путь объявлен литералом рядом с {@code /api/sku/{id}} из
     * {@code SkuWriteController}: точное совпадение сильнее образца
     * с подстановкой, поэтому слово {@code options} за идентификатор
     * товара не примут.
     */
    @GetMapping("/api/{list:sku|packaging}/options")
    public List<Map<String, Object>> options(
            @PathVariable String list,
            @RequestParam(required = false, defaultValue = "false") boolean withArchived) {
        return catalogs.list(CatalogType.ofPath(list), withArchived);
    }
}
