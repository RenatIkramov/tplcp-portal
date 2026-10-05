package com.ahlers.portal3pl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Единственное место, где приложение «включает» изоляцию данных: выставляет
 * переменную сессии app.depositor_id, по которой база применяет политики RLS.
 *
 * Два важных момента.
 *
 * 1. Вызывать это нужно ВНУТРИ транзакции (методы-потребители помечены
 *    @Transactional). Приложение работает через пул соединений: если выставить
 *    переменную вне транзакции, запрос данных может уйти в другое соединение,
 *    где её нет. Транзакция держит одно соединение от начала до конца.
 *
 * 2. Третий аргумент set_config = true означает is_local: значение живёт до
 *    конца транзакции и не остаётся в соединении, когда оно вернётся в пул.
 *    Иначе следующий запрос мог бы унаследовать чужого поклажедателя.
 */
@Component
public class DepositorScope {

    private final JdbcClient jdbc;
    private final boolean devMode;

    public DepositorScope(JdbcClient jdbc,
                          @Value("${portal.dev-mode:false}") boolean devMode) {
        this.jdbc = jdbc;
        this.devMode = devMode;
    }

    /** Определяет поклажедателя, проверяет право доступа и выставляет контекст. */
    public void apply() {
        String depositorId = resolveDepositorId();

        String login = DepositorContext.userLogin();
        if (login != null && !login.isBlank()) {
            Boolean allowed = jdbc
                    .sql("SELECT dbo.user_may_access_depositor(:login, :id::uuid)")
                    .param("login", login)
                    .param("id", depositorId)
                    .query(Boolean.class)
                    .single();
            if (!Boolean.TRUE.equals(allowed)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Пользователю недоступен этот поклажедатель");
            }
        }

        jdbc.sql("SELECT set_config('app.depositor_id', :id, true)")
                .param("id", depositorId)
                .query(String.class)
                .single();

        DepositorContext.setResolvedDepositorId(depositorId);
    }

    /** Поклажедатель текущего запроса в виде uuid. Доступен после apply(). */
    public String currentDepositorId() {
        String id = DepositorContext.resolvedDepositorId();
        if (id == null) {
            throw new IllegalStateException("apply() не вызывался — контекст поклажедателя не выставлен");
        }
        return id;
    }

    private String resolveDepositorId() {
        String id = DepositorContext.depositorId();
        if (id != null && !id.isBlank()) {
            return id;
        }

        // Удобство разработки: разрешаем указать код вместо UUID.
        // В продакшне dev-mode выключен и этот путь недоступен.
        String code = DepositorContext.depositorCode();
        if (devMode && code != null && !code.isBlank()) {
            String resolved = jdbc
                    .sql("SELECT dbo.dev_depositor_by_code(:code)::text")
                    .param("code", code)
                    .query(String.class)
                    .optional()
                    .orElse(null);
            if (resolved == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Поклажедатель с кодом " + code + " не найден");
            }
            return resolved;
        }

        // §6.4: пока поклажедатель не выбран, данные не отдаются
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                devMode
                        ? "Не выбран поклажедатель: добавьте ?depositorCode=D-001 "
                          + "или заголовок X-Depositor-Id"
                        : "Не выбран поклажедатель");
    }
}
