package com.ahlers.portal3pl.config;

import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.PortalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ответы об ошибках в одном формате:
 * {"status": 409, "key": "error.enum.in_use", "params": {...},
 *  "message": "человеческий текст на языке вошедшего"}.
 * <p>
 * Зачем это нужно. Spring по умолчанию не включает текст ошибки в тело
 * ответа (защита от того, чтобы наружу не утекли подробности), поэтому
 * экран получал только код — и показывал «код 400» вместо «Пароль короче
 * восьми символов». Все отказы в портале мы формулируем сами и специально
 * пишем так, чтобы их можно было показать человеку, — значит, и отдавать
 * их нужно целиком.
 * <p>
 * Неожидаемые ошибки сюда не попадают наружу: в ответ уходит общий текст,
 * а подробности — в журнал приложения.
 */
/*
 * Методы ниже помечены @ExceptionHandler: их вызывает Spring, а не наш код.
 * Проверка кода в IDE этой связи не видит и сообщает «method is never used» —
 * срабатывание ложное. Удалять их нельзя: на них держится весь формат
 * отказов портала (400/403/404/409 с человеческим текстом), на который
 * опираются и экраны, и тесты. Подавляем, чтобы предупреждение не
 * всплывало при каждом коммите и не соблазняло «починить».
 */
@SuppressWarnings("unused")
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    private final ActorText texts;

    public ApiErrorHandler(ActorText texts) {
        this.texts = texts;
    }

    /**
     * Отказ портала: текст собирается по ключу на языке вошедшего.
     *
     * <p>Ключ и подстановки уходят в ответ рядом с текстом. Экрану они
     * не нужны — он показывает message, — но по ним разбирают отказ
     * тесты и журнал, и не приходится сличать строки.
     */
    @ExceptionHandler(PortalException.class)
    public ResponseEntity<Map<String, Object>> portalRejection(PortalException e) {
        Map<String, Object> body = body(texts.text(e.key(), e.params()), e.getStatusCode());
        body.put("key", e.key());
        if (!e.params().isEmpty()) {
            body.put("params", e.params());
        }
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> rejection(ResponseStatusException e) {
        String text = e.getReason() == null || e.getReason().isBlank()
                ? byStatus(e.getStatusCode())
                : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(body(text, e.getStatusCode()));
    }

    /**
     * Отказы самого Spring: неизвестный адрес (404), не тот метод (405),
     * не тот Content-Type, нет обязательного параметра, параметр не того
     * вида (400). У них свой правильный код ответа, и терять его,
     * превращая в 500, нельзя.
     *
     * <p>НАЙДЕНО АУДИТОМ (11.11.2026, S6). Прежде сюда попадали не все:
     * «нет такого адреса» и «не тот вид параметра» в Spring не наследуют
     * {@code ErrorResponseException}, уходили в общий обработчик и
     * отвечали 500 со стеком в журнале. Любой сканер адресов заливал
     * журнал «внутренними ошибками», и настоящие в нём тонули.
     *
     * <p>Текст — наш, по коду ответа, а не английская подробность Spring:
     * её человеку не покажешь. В журнал — одна строка без стека: это
     * ошибка того, кто прислал запрос, а не портала.
     */
    @ExceptionHandler({org.springframework.web.ErrorResponseException.class,
                       org.springframework.web.servlet.resource.NoResourceFoundException.class,
                       org.springframework.web.servlet.NoHandlerFoundException.class,
                       org.springframework.web.HttpRequestMethodNotSupportedException.class,
                       org.springframework.web.HttpMediaTypeNotSupportedException.class,
                       org.springframework.web.HttpMediaTypeNotAcceptableException.class,
                       org.springframework.web.bind.ServletRequestBindingException.class,
                       org.springframework.beans.TypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> springRejection(Exception e) {
        HttpStatusCode status = e instanceof org.springframework.web.ErrorResponse er
                ? er.getStatusCode()
                : HttpStatus.BAD_REQUEST;
        log.warn("Отказ запроса ({}): {}", status.value(), e.getMessage());
        return ResponseEntity.status(status).body(body(byStatus(status), status));
    }

    /** Ошибка в теле запроса: пришло не то, что ожидает контроллер. */
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
                       org.springframework.web.bind.MethodArgumentNotValidException.class,
                       IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        log.warn("Неверный запрос: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(body(texts.of("error.request.unreadable"), HttpStatus.BAD_REQUEST));
    }

    /**
     * База не смогла разобрать присланное значение: не идентификатор там,
     * где ждали идентификатор ({@code /api/sku/not-a-uuid}), не дата,
     * число вне диапазона. Это ошибка запроса — 400, не 500 (аудит
     * 11.11.2026, S6).
     *
     * <p>Ссылка документа на запись другого клиента (23T14, V128, аудит
     * S14) — тоже 400: такой uuid мог прислать только запрос в обход
     * экрана, и ответ ему — отказ, а не «внутренняя ошибка».
     *
     * <p>Остальные нарушения целостности (повтор ключа, ссылка на
     * несуществующее) — по-прежнему внутренняя ошибка: такие отказы
     * портал формулирует сам, до базы, и дошедший до базы — промах кода.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(
            org.springframework.dao.DataIntegrityViolationException e) {
        if (isBadInput(e)) {
            log.warn("Неверное значение в запросе: {}", e.getMostSpecificCause().getMessage());
            return ResponseEntity.badRequest()
                    .body(body(texts.of("error.request.unreadable"), HttpStatus.BAD_REQUEST));
        }
        if (FOREIGN_REFERENCE.equals(sqlState(e))) {
            log.warn("Ссылка на запись другого клиента: {}", e.getMostSpecificCause().getMessage());
            return ResponseEntity.badRequest()
                    .body(body(texts.of("error.document.foreign_reference"), HttpStatus.BAD_REQUEST));
        }
        return failure(e);
    }

    /** Отказ триггера own_references (V128). */
    static final String FOREIGN_REFERENCE = "23T14";

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /**
     * Коды PostgreSQL «значение не того вида»: 22P02 — не разобрать
     * (uuid, число), 22007/22008 — дата и время, 22003 — число вне
     * диапазона.
     */
    private static boolean isBadInput(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql) {
                String state = sql.getSQLState();
                return "22P02".equals(state) || "22007".equals(state)
                        || "22008".equals(state) || "22003".equals(state);
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> failure(Exception e) {
        // наружу — общий текст, в журнал — всё, иначе ошибку не найти
        log.error("Внутренняя ошибка портала", e);
        return ResponseEntity.internalServerError()
                .body(body(texts.of("error.internal"), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static Map<String, Object> body(String message, HttpStatusCode status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("status", status.value());
        return response;
    }

    /**
     * Если текст почему-то не задан — подставляем понятный по смыслу кода.
     *
     * <p>Так отвечают отказы не портала, а самого Spring (не тот метод,
     * не тот Content-Type): своего текста у них нет, а «400» человеку
     * ничего не говорит. Ключи те же, что у остальных сообщений, —
     * и переводятся тем же экраном.
     */
    private String byStatus(HttpStatusCode status) {
        String key = switch (status.value()) {
            case 400 -> "error.request.unreadable";
            case 401 -> "error.auth.sign_in_required";
            case 403 -> "error.status.forbidden";
            case 404 -> "error.status.not_found";
            case 405 -> "error.status.method_not_allowed";
            case 409 -> "error.status.conflict";
            case 422 -> "error.status.unprocessable";
            case 429 -> "error.status.too_many";
            default -> "error.status.failed";
        };
        return texts.of(key);
    }
}
