package com.ahlers.portal3pl.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Неудачные входы по адресу отправителя — в памяти приложения (аудит
 * 11.11.2026, S8).
 *
 * <p>ЗАЧЕМ. Учётка больше не запирается (решение заказчика, V123), и
 * перебор паролей сдерживают две вещи: нарастающая задержка ответа на
 * неудачу по учётке (AuthController) и это ограничение по адресу.
 * Больше {@link #LIMIT} неудач с одного адреса за {@link #WINDOW} — отказ
 * «слишком много попыток», пока окно не сдвинется. Считаются только
 * неудачи: человек, который входит и работает, лимит не трогает.
 *
 * <p>И второе: вход под несуществующим логином пишется в журнал не чаще
 * раза в минуту на пару «адрес + логин». Прежде каждая такая попытка
 * была строкой журнала, и цикл запросов раздувал его без предела.
 *
 * <p>Память, а не база: счёт нужен быстро и только на время окна, а
 * после перезапуска начать заново не страшно. Адрес — тот, что видит
 * сервер; за прокси он станет адресом клиента после настройки S12.
 */
@Component
public class LoginThrottle {

    static final int LIMIT = 20;
    static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration JOURNAL_GAP = Duration.ofMinutes(1);
    /** Сколько адресов помнить, прежде чем вычистить устаревшие. */
    private static final int SWEEP_AT = 10_000;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> journaled = new ConcurrentHashMap<>();

    /** С этого адреса неудач уже больше лимита — входить пока нельзя. */
    public boolean blocked(String address) {
        if (address == null) {
            return false;
        }
        Deque<Long> times = failures.get(address);
        if (times == null) {
            return false;
        }
        synchronized (times) {
            prune(times, System.currentTimeMillis());
            return times.size() >= LIMIT;
        }
    }

    /** Отметить неудачу с адреса. */
    public void failed(String address) {
        if (address == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (failures.size() > SWEEP_AT) {
            sweep(now);
        }
        Deque<Long> times = failures.computeIfAbsent(address, a -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            times.addLast(now);
        }
    }

    /**
     * Писать ли в журнал неудачу с несуществующим логином: да, если за
     * последнюю минуту с этого адреса этот логин не записывался.
     */
    public boolean journalUnknown(String address, String login) {
        long now = System.currentTimeMillis();
        if (journaled.size() > SWEEP_AT) {
            journaled.values().removeIf(at -> now - at > JOURNAL_GAP.toMillis());
        }
        String key = address + "\n" + (login == null ? "" : login.trim().toLowerCase());
        Long before = journaled.get(key);
        if (before != null && now - before < JOURNAL_GAP.toMillis()) {
            return false;
        }
        journaled.put(key, now);
        return true;
    }

    /**
     * Забыть всё. Для проверок: они входят с одного адреса и намеренно
     * ошибаются, и счёт одной проверки не должен закрывать вход следующей.
     */
    public void forgetAll() {
        failures.clear();
        journaled.clear();
    }

    private static void prune(Deque<Long> times, long now) {
        long since = now - WINDOW.toMillis();
        while (!times.isEmpty() && times.peekFirst() < since) {
            times.pollFirst();
        }
    }

    private void sweep(long now) {
        for (Iterator<Map.Entry<String, Deque<Long>>> it = failures.entrySet().iterator(); it.hasNext(); ) {
            Deque<Long> times = it.next().getValue();
            synchronized (times) {
                prune(times, now);
                if (times.isEmpty()) {
                    it.remove();
                }
            }
        }
    }
}
