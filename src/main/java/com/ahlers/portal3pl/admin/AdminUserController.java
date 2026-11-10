package com.ahlers.portal3pl.admin;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.auth.SessionUser;
import com.ahlers.portal3pl.common.ActorText;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Справочник «Пользователи» режима администрирования. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService users;
    private final SessionUser session;
    private final ActorText texts;

    public AdminUserController(AdminUserService users, SessionUser session, ActorText texts) {
        this.users = users;
        this.session = session;
        this.texts = texts;
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        return users.users(actor(request));
    }

    /** Сохранение карточки: блокировка и видимость разделов. Отдаёт список заново. */
    @PutMapping("/{userId}")
    public List<Map<String, Object>> save(@PathVariable String userId,
                                          @RequestBody AdminUserService.Settings body,
                                          HttpServletRequest request) {
        return users.save(actor(request), userId, body);
    }

    public record PasswordRequest(String newPassword) {
    }

    /** Создание пользователя. Пароль можно не присылать — портал придумает сам. */
    public record CreateRequest(String login, String fullName, String roleCode,
                                String position, String organization, String phone,
                                List<String> depositorIds, Boolean allDepositors,
                                Boolean allWarehouses, List<String> warehouseIds,
                                String password) {
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        return users.create(actor(request), new AdminUserService.Profile(
                body.login(), body.fullName(), body.roleCode(), body.position(),
                body.organization(), body.phone(), body.depositorIds(),
                body.allDepositors(), body.allWarehouses(), body.warehouseIds()), body.password());
    }

    /** Анкета, роль, логин и доступ — поклажедатели и склады. Отдаёт список заново. */
    @PutMapping("/{userId}/profile")
    public List<Map<String, Object>> saveProfile(@PathVariable String userId,
                                                  @RequestBody AdminUserService.Profile body,
                                                  HttpServletRequest request) {
        return users.saveProfile(actor(request), userId, body);
    }

    /** Поклажедатели, выданные пользователю — для галочек в карточке. */
    @GetMapping("/{userId}/depositors")
    public List<String> depositors(@PathVariable String userId, HttpServletRequest request) {
        return users.depositorsOfUser(actor(request), userId);
    }

    /** Пароль, заданный администратором, портал потребует сменить при входе. */
    @PostMapping("/{userId}/password")
    public Map<String, Object> setPassword(@PathVariable String userId,
                                           @RequestBody PasswordRequest body,
                                           HttpServletRequest request) {
        users.setPassword(actor(request), userId, body.newPassword());
        return Map.of("message", texts.of("info.user.password_set"));
    }

    private PortalUser actor(HttpServletRequest request) {
        return session.requireUser(request);
    }
}
