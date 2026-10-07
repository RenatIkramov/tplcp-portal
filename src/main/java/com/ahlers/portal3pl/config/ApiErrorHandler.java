package com.ahlers.portal3pl.config;

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
 * Ответы об ошибках в одном формате: {"message": "человеческий текст"}.
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
                .body(body("Запрос не распознан порталом", HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> failure(Exception e) {
        // наружу — общий текст, в журнал — всё, иначе ошибку не найти
        log.error("Внутренняя ошибка портала", e);
        return ResponseEntity.internalServerError()
                .body(body("Внутренняя ошибка портала. Обратитесь к администратору",
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static Map<String, Object> body(String message, HttpStatusCode status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("status", status.value());
        return response;
    }

    /** Если текст почему-то не задан — подставляем понятный по смыслу кода. */
    private static String byStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "Требуется вход в портал";
            case 403 -> "Действие недоступно вашей учётной записи";
            case 404 -> "Запись не найдена";
            case 409 -> "Действие сейчас невозможно";
            case 422 -> "Данные заполнены не полностью";
            case 429 -> "Слишком много попыток. Повторите позже";
            default -> "Запрос не выполнен";
        };
    }
}
