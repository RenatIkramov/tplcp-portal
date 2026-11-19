package com.ahlers.portal3pl.i18n;

import com.ahlers.portal3pl.config.DepositorContext;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Язык пользователя — в сессию базы (V86).
 *
 * <p>Перечисления (статусы, типы, единицы…) переводит сама база: функция
 * {@code enum_name()} берёт язык из {@code app.locale}. Чтобы он там был,
 * портал пишет его при каждой выдаче соединения из пула — одним
 * запросом, который заодно и выясняет язык: свой язык пользователя,
 * иначе язык портала по умолчанию. То же правило, что у
 * {@link com.ahlers.portal3pl.common.ActorText#locale()}.
 *
 * <p>Почему здесь, а не в каждом сервисе. Контекст поклажедателя
 * ставится в сервисах ({@code DepositorScope.apply()}), и забытый вызов
 * тихо опустошает выдачу — на этом уже попадались. Язык нужен всем
 * запросам без исключения, включая справочники администрирования,
 * где поклажедателя нет вовсе. Одно место на всех не забудешь.
 *
 * <p>Значение пишется на сессию ({@code false}), а не на транзакцию:
 * часть чтений идёт без транзакции. Соединение из пула переиспользуется,
 * поэтому язык пишется заново при КАЖДОЙ выдаче — иначе следующий
 * пользователь увидел бы портал на языке предыдущего.
 *
 * <p>Тем же запросом пишется {@code app.actor} — кто действует, для журнала
 * действий (V116): триггеры берут логин оттуда. Тот же довод: журнал
 * нужен всем записям, и одно место не забудешь. Без входа значение
 * пустое — триггер тогда не пишет (миграции, служебные запросы).
 * Не {@code app.user_login}: тот включает ограничение по складам (V110)
 * и ставится только вместе с поклажедателем.
 *
 * <p>СБОЙ ЗАПРОСА (аудит 11.11.2026, A7). Значения живут в соединении, а
 * соединение уходит следующему. Прежде при сбое запроса в соединении
 * оставались язык и {@code app.actor} ПРЕДЫДУЩЕГО пользователя — журнал
 * действий приписал бы правку не тому. Теперь при сбое оба значения
 * ставятся отдельным запросом без таблиц (логин — свой, язык — пустой,
 * т. е. по умолчанию); не удалось и это — соединение закрывается и
 * наружу не выдаётся.
 *
 * <p>{@code @NullMarked} — как у самого Spring 7: методы, которые здесь
 * переопределяются, объявлены там «без null», и без пометки анализатор
 * считал наши переопределения нарушающими этот договор.
 */
@Component
@NullMarked
public class LocaleDataSource implements BeanPostProcessor {

    private static final String SET_LOCALE = """
            SELECT set_config('app.locale', COALESCE(
                     (SELECT l.code
                        FROM dbo.app_user u
                        JOIN dbo.dictionary_locale l
                          ON l.locale_id = u.locale_id AND l.is_active
                       WHERE u.login = ?),
                     (SELECT code FROM dbo.dictionary_locale WHERE is_default),
                     ''), false),
                   set_config('app.actor', ?, false)
            """;

    /** Запасной путь при сбое {@link #SET_LOCALE}: без таблиц, только значения. */
    private static final String RESET = """
            SELECT set_config('app.locale', '', false), set_config('app.actor', ?, false)
            """;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof Wrapper)) {
            return new Wrapper(ds);
        }
        return bean;
    }

    /** Пул соединений, который каждой выдаче проставляет язык. */
    static final class Wrapper extends DelegatingDataSource {

        private final String localeSql;

        Wrapper(DataSource target) {
            this(target, SET_LOCALE);
        }

        /** Свой запрос языка — для проверки сбоя (LocaleDataSourceTest). */
        Wrapper(DataSource target, String localeSql) {
            super(target);
            this.localeSql = localeSql;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return withLocale(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return withLocale(super.getConnection(username, password));
        }

        private Connection withLocale(Connection c) throws SQLException {
            String login = DepositorContext.userLogin();
            String actor = login == null ? "" : login;
            try (PreparedStatement ps = c.prepareStatement(localeSql)) {
                ps.setString(1, actor);
                ps.setString(2, actor);
                ps.execute();
            } catch (SQLException e) {
                // До миграций (первый запуск, Flyway) таблиц ещё нет —
                // язык тогда не нужен, а падать из-за него нельзя.
                // Свой откат: ошибка в транзакции не должна её отравить.
                if (!c.getAutoCommit()) {
                    c.rollback();
                }
                reset(c, actor);
            }
            return c;
        }

        /**
         * Затереть значения прежнего владельца соединения (A7). Не вышло —
         * соединение не выдаётся: лучше отказ запроса, чем правка в
         * журнале под чужим именем.
         */
        private static void reset(Connection c, String actor) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement(RESET)) {
                ps.setString(1, actor);
                ps.execute();
            } catch (SQLException e) {
                c.close();
                throw e;
            }
        }
    }
}
