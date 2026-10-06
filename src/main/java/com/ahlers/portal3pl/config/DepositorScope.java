package com.ahlers.portal3pl.config;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Единственное место, где приложение «включает» изоляцию данных: выставляет
 * переменную сессии базы app.depositor_id, по которой применяются политики RLS.
 * <p>
 * Три вещи, которые здесь важны.
 * <p>
 * 1. Поклажедатель берётся из серверной сессии, а не из запроса. Подставить
 *    чужой идентификатор снаружи невозможно: в браузере его просто нет.
 * <p>
 * 2. Вызывать нужно ВНУТРИ транзакции. Приложение работает через пул соединений:
 *    вне транзакции нет гарантии, что запрос данных уйдёт в то же соединение,
 *    где выставлена переменная.
 * <p>
 * 3. Третий аргумент set_config = true означает is_local: значение живёт
 *    до конца транзакции и не остаётся в соединении, вернувшемся в пул.
 */
@Component
public class DepositorScope {

    private final JdbcClient jdbc;

    public DepositorScope(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void apply() {
        String login = DepositorContext.userLogin();
        if (login == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется вход в портал");
        }
        String depositorId = DepositorContext.depositorId();
        if (depositorId == null || depositorId.isBlank()) {
            // §6.4: пока поклажедатель не выбран, никакие данные не отдаются
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не выбран поклажедатель");
        }
        jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                .param("id", depositorId)
                .query(String.class)
                .single();
    }

    /** Поклажедатель текущего запроса. Доступен после apply(). */
    public String currentDepositorId() {
        String id = DepositorContext.depositorId();
        if (id == null) {
            throw new IllegalStateException("apply() не вызывался — контекст поклажедателя не выставлен");
        }
        return id;
    }
}
