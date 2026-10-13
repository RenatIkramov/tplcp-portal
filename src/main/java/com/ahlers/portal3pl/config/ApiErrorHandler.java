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
 * {"status": 409, "key": "error.unit.in_use", "params": {...},
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
     * Остальные ошибки самого Spring (неподдерживаемый метод, не тот
     * Content-Type, не разобранный параметр) — у них свой правильный код
     * ответа, и терять его, превращая в 500, нельзя.
     */
    @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
    public ResponseEntity<Map<String, Object>> springError(
            org.springframework.web.ErrorResponseException e) {
        String text = e.getBody().getDetail();
        return ResponseEntity.status(e.getStatusCode())
                .body(body(text == null || text.isBlank() ? byStatus(e.getStatusCode()) : text,
                        e.getStatusCode()));
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
            case 401 -> "error.auth.sign_in_required";
            case 403 -> "error.status.forbidden";
            case 404 -> "error.status.not_found";
            case 409 -> "error.status.conflict";
            case 422 -> "error.status.unprocessable";
            case 429 -> "error.status.too_many";
            default -> "error.status.failed";
        };
        return texts.of(key);
    }
}
