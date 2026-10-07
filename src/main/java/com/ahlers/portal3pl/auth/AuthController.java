package com.ahlers.portal3pl.auth;

import com.ahlers.portal3pl.i18n.LocaleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Вход, выход и выбор поклажедателя.
 * <p>
 * Правило §6.4 реализовано здесь: если пользователю доступен ровно один
 * поклажедатель, он выбирается сразу; если несколько — не выбирается никакой,
 * и данные не отдаются, пока человек не укажет, с кем работает. Это защита
 * от того, чтобы менеджер по ошибке посмотрел или изменил данные не того клиента.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final SessionUser session;
    private final LocaleService locales;

    public AuthController(AuthService auth, SessionUser session, LocaleService locales) {
        this.auth = auth;
        this.session = session;
        this.locales = locales;
    }

    public record LoginRequest(String login, String password) {
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        PortalUser user = auth.authenticate(body.login(), body.password());
        session.setUser(request, user);

        List<Map<String, Object>> depositors = auth.availableDepositors(user.login());
        if (depositors.size() == 1) {
            session.selectDepositor(request, String.valueOf(depositors.getFirst().get("depositorId")));
        }
        return sessionState(user, depositors, session.depositorId(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        session.signOut(request);
    }

    /** Текущее состояние сеанса — экран спрашивает его при загрузке. */
    @GetMapping("/session")
    public Map<String, Object> current(HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        return sessionState(user, auth.availableDepositors(user.login()), session.depositorId(request));
    }

    public record DepositorRequest(String depositorId) {
    }

    /** Выбор поклажедателя менеджером или администратором. */
    @PostMapping("/depositor")
    public Map<String, Object> selectDepositor(@RequestBody DepositorRequest body,
                                               HttpServletRequest request) {
        PortalUser user = session.requireUser(request);
        if (!auth.mayAccess(user.login(), body.depositorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Этот поклажедатель недоступен вашей учётной записи");
        }
        session.selectDepositor(request, body.depositorId());
        return sessionState(user, auth.availableDepositors(user.login()), body.depositorId());
    }

    private Map<String, Object> sessionState(PortalUser user, List<Map<String, Object>> depositors,
                                          String selected) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("login", user.login());
        response.put("fullName", user.fullName());
        response.put("roleCode", user.roleCode());
        // Язык портала берётся из учётной записи: экран применяет его сразу
        // после входа, не дожидаясь, пока человек переключит сам
        response.put("locale", locales.localeOf(user.login()));
        // §6.2: пароль, заданный администратором, портал требует сменить сразу
        response.put("mustChangePassword", user.mustChangePassword());
        response.put("depositors", depositors);
        response.put("depositorId", selected);
        // §6.4: пока поклажедатель не выбран, экран показывает пустое состояние
        response.put("needsDepositorChoice", selected == null && depositors.size() > 1);
        return response;
    }
}
