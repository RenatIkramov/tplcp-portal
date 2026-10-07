package com.ahlers.portal3pl.auth;

/**
 * Пользователь портала в том виде, в каком он нужен для входа и проверки прав.
 * Пароль здесь не хранится — только то, что можно показывать и на что опираться.
 */
public record PortalUser(
        String userId,
        String login,
        String fullName,
        String roleCode,
        boolean active,
        boolean mustChangePassword
) {

    /** Администратор и разработчик управляют пользователями и доступами. */
    public boolean isAdmin() {
        return "admin".equals(roleCode) || "developer".equals(roleCode);
    }

    /** Та же учётная запись, но пароль уже свой — после смены пароля. */
    public PortalUser withOwnPassword() {
        return new PortalUser(userId, login, fullName, roleCode, active, false);
    }
    public boolean isOperatorStaff() {
        return "manager".equals(roleCode) || "admin".equals(roleCode)
                || "staff".equals(roleCode) || "developer".equals(roleCode);
    }
}
