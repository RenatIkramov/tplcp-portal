package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Списки для карточки пользователя: роли и все поклажедатели портала.
 * <p>
 * Отдельно от `/api/depositors` (его нет) и от `/api/suppliers` и прочих
 * справочников поклажедателя: здесь данные всего портала, и доступны они
 * только администратору.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminRefController {

    private final AdminUserService users;
    private final SessionUser session;

    public AdminRefController(AdminUserService users, SessionUser session) {
        this.users = users;
        this.session = session;
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles(HttpServletRequest request) {
        return users.roles(session.requireUser(request));
    }

    @GetMapping("/depositors")
    public List<Map<String, Object>> depositors(HttpServletRequest request) {
        return users.depositors(session.requireUser(request));
    }
}
