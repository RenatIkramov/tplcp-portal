package com.ahlers.portal3pl.config;

/**
 * Контекст текущего запроса: кто его выполняет и с каким поклажедателем.
 * <p>
 * Значения кладёт AuthFilter, беря их из серверной сессии. Из браузера
 * ни логин, ни поклажедатель прийти не могут — только идентификатор сессии,
 * поэтому подменить их в запросе нельзя.
 * <p>
 * ThreadLocal: каждый HTTP-запрос обрабатывается своим потоком, значения
 * разных запросов не перемешиваются.
 */
public final class DepositorContext {

    private record Ctx(String userLogin, String depositorId) {
    }

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    private DepositorContext() {
    }

    public static void set(String userLogin, String depositorId) {
        CURRENT.set(new Ctx(userLogin, depositorId));
    }

    public static String userLogin() {
        Ctx c = CURRENT.get();
        return c == null ? null : c.userLogin();
    }

    public static String depositorId() {
        Ctx c = CURRENT.get();
        return c == null ? null : c.depositorId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
