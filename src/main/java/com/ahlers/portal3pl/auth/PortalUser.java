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

    /** Та же учётная запись, но пароль уже свой — после смены пароля. */
    public PortalUser withOwnPassword() {
        return new PortalUser(userId, login, fullName, roleCode, active, false);
    }
}
