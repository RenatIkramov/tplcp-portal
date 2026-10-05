package com.ahlers.portal3pl.config;

/**
 * Контекст текущего запроса: кто пользователь и с каким поклажедателем
 * он работает.
 *
 * Пока аутентификации нет (этап 5), значения приходят в заголовках
 * X-User-Login и X-Depositor-Id, а в режиме разработки — ещё и как
 * код поклажедателя (заголовок X-Depositor-Code или параметр
 * ?depositorCode=D-001), чтобы не искать UUID вручную.
 *
 * Когда появится вход по логину, значения будут браться из сессии —
 * остальной код не изменится, он обращается только сюда.
 *
 * ThreadLocal: каждый HTTP-запрос обрабатывается своим потоком, поэтому
 * значения разных запросов не перемешиваются.
 */
public final class DepositorContext {

    private record Ctx(String userLogin, String depositorId, String depositorCode) {
    }

    /** Идентификатор поклажедателя, уже приведённый к uuid (см. DepositorScope). */
    private static final ThreadLocal<String> RESOLVED = new ThreadLocal<>();

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    private DepositorContext() {
    }

    public static void set(String userLogin, String depositorId, String depositorCode) {
        CURRENT.set(new Ctx(userLogin, depositorId, depositorCode));
    }

    public static String userLogin() {
        Ctx c = CURRENT.get();
        return c == null ? null : c.userLogin();
    }

    public static String depositorId() {
        Ctx c = CURRENT.get();
        return c == null ? null : c.depositorId();
    }

    public static String depositorCode() {
        Ctx c = CURRENT.get();
        return c == null ? null : c.depositorCode();
    }

    public static void setResolvedDepositorId(String depositorId) {
        RESOLVED.set(depositorId);
    }

    public static String resolvedDepositorId() {
        return RESOLVED.get();
    }

    public static void clear() {
        CURRENT.remove();
        RESOLVED.remove();
    }
}
