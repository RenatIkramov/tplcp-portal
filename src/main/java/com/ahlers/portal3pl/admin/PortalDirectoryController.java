package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Справочники уровня портала — раздел администрирования:
 * <pre>
 *   GET            /api/admin/warehouse-directory   список для справочника
 *   POST           /api/admin/warehouses            создание
 *   GET|PUT|DELETE /api/admin/warehouses/{id}       карточка, правка, удаление
 *   POST           /api/admin/warehouses/{id}/archive
 *
 *   GET|POST       /api/admin/depositor-directory          список, создание
 *   GET|PUT|DELETE /api/admin/depositor-directory/{id}     карточка, правка, удаление
 *   POST           /api/admin/depositor-directory/{id}/archive
 *
 *   GET|POST       /api/admin/operator-directory           список, создание
 *   GET|PUT|DELETE /api/admin/operator-directory/{id}      карточка, правка, удаление
 *   POST           /api/admin/operator-directory/{id}/archive
 *
 * Перечисления (типы заказов и поступлений, единицы измерения и др.)
 * с V95 ведёт {@link com.ahlers.portal3pl.i18n.EnumValueController}.
 * </pre>
 *
 * <p>Списки справочников лежат на путях с суффиксом -directory: адреса
 * /api/admin/warehouses и /api/admin/depositors уже заняты короткими
 * списками для галочек на экранах доступов. Там нужны только действующие
 * записи и два-три поля, здесь — все, включая архивные, и целиком.
 * Смешивать выбор со справочником значило бы менять смысл существующих
 * адресов, на которые уже опираются экраны доступов.
 */
@RestController
@RequestMapping("/api/admin")
public class PortalDirectoryController {

    private final PortalDirectoryService directory;
    private final SessionUser session;

    public PortalDirectoryController(PortalDirectoryService directory, SessionUser session) {
        this.directory = directory;
        this.session = session;
    }

    /* ---- склады ---- */

    @GetMapping("/warehouse-directory")
    public List<Map<String, Object>> warehouses(HttpServletRequest request) {
        return directory.warehouses(actor(request));
    }

    @GetMapping("/warehouses/{id}")
    public Map<String, Object> warehouse(@PathVariable String id, HttpServletRequest request) {
        return directory.warehouse(actor(request), id);
    }

    @PostMapping("/warehouses")
    public Map<String, Object> createWarehouse(@RequestBody Map<String, Object> values,
                                               HttpServletRequest request) {
        return directory.createWarehouse(actor(request), values);
    }

    @PutMapping("/warehouses/{id}")
    public Map<String, Object> updateWarehouse(@PathVariable String id,
                                               @RequestBody Map<String, Object> values,
                                               HttpServletRequest request) {
        return directory.updateWarehouse(actor(request), id, values);
    }

    @PostMapping("/warehouses/{id}/archive")
    public Map<String, Object> archiveWarehouse(@PathVariable String id,
                                                @RequestBody(required = false) Map<String, Object> body,
                                                HttpServletRequest request) {
        return directory.setWarehouseActive(actor(request), id, active(body));
    }

    @DeleteMapping("/warehouses/{id}")
    public Map<String, Object> deleteWarehouse(@PathVariable String id, HttpServletRequest request) {
        directory.deleteWarehouse(actor(request), id);
        return Map.of("deleted", true);
    }

    /* ---- поклажедатели ---- */

    @GetMapping("/depositor-directory")
    public List<Map<String, Object>> depositors(HttpServletRequest request) {
        return directory.depositors(actor(request));
    }

    @GetMapping("/depositor-directory/{id}")
    public Map<String, Object> depositor(@PathVariable String id, HttpServletRequest request) {
        return directory.depositor(actor(request), id);
    }

    @PostMapping("/depositor-directory")
    public Map<String, Object> createDepositor(@RequestBody PortalDirectoryService.Depositor body,
                                               HttpServletRequest request) {
        return directory.saveDepositor(actor(request), null, body);
    }

    @PutMapping("/depositor-directory/{id}")
    public Map<String, Object> updateDepositor(@PathVariable String id,
                                               @RequestBody PortalDirectoryService.Depositor body,
                                               HttpServletRequest request) {
        return directory.saveDepositor(actor(request), id, body);
    }

    @PostMapping("/depositor-directory/{id}/archive")
    public Map<String, Object> archiveDepositor(@PathVariable String id,
                                                @RequestBody(required = false) Map<String, Object> body,
                                                HttpServletRequest request) {
        return directory.setDepositorActive(actor(request), id, active(body));
    }

    @DeleteMapping("/depositor-directory/{id}")
    public Map<String, Object> deleteDepositor(@PathVariable String id, HttpServletRequest request) {
        directory.deleteDepositor(actor(request), id);
        return Map.of("deleted", true);
    }

    /* ---- организации оператора ---- */

    @GetMapping("/operator-directory")
    public List<Map<String, Object>> operators(HttpServletRequest request) {
        return directory.operators(actor(request));
    }

    @GetMapping("/operator-directory/{id}")
    public Map<String, Object> operator(@PathVariable String id, HttpServletRequest request) {
        return directory.operator(actor(request), id);
    }

    @PostMapping("/operator-directory")
    public Map<String, Object> createOperator(@RequestBody PortalDirectoryService.Operator body,
                                              HttpServletRequest request) {
        return directory.saveOperator(actor(request), null, body);
    }

    @PutMapping("/operator-directory/{id}")
    public Map<String, Object> updateOperator(@PathVariable String id,
                                              @RequestBody PortalDirectoryService.Operator body,
                                              HttpServletRequest request) {
        return directory.saveOperator(actor(request), id, body);
    }

    @PostMapping("/operator-directory/{id}/archive")
    public Map<String, Object> archiveOperator(@PathVariable String id,
                                               @RequestBody(required = false) Map<String, Object> body,
                                               HttpServletRequest request) {
        return directory.setOperatorActive(actor(request), id, active(body));
    }

    @DeleteMapping("/operator-directory/{id}")
    public Map<String, Object> deleteOperator(@PathVariable String id, HttpServletRequest request) {
        directory.deleteOperator(actor(request), id);
        return Map.of("deleted", true);
    }

    /** {"active": true|false}; отсутствие тела означает «в архив». */
    private static boolean active(Map<String, Object> body) {
        return body != null && Boolean.TRUE.equals(body.get("active"));
    }

    private PortalUser actor(HttpServletRequest request) {
        return session.requireUser(request);
    }
}
