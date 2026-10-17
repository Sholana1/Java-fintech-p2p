package com.fintechdemo.platform.web;

import com.fintechdemo.platform.money.CurrencyMismatchException;
import com.fintechdemo.platform.money.InvalidAmountException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The last line of error handling in every service: turns any exception into problem+json.
 * Lowest precedence, so a service's own @RestControllerAdvice for its domain exceptions runs first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidAmountException.class)
    ProblemDetail handleInvalidAmount(InvalidAmountException ex) {
        return Problems.of(HttpStatus.BAD_REQUEST, "invalid-amount", "Invalid amount", ex.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "currency-mismatch", "Currency mismatch", ex.getMessage());
    }

    // Never leak the exception message or stack trace: it can contain SQL, hostnames or secrets.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
            "Something went wrong on our side. Quote the correlationId when you contact support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
            "One or more fields are invalid.");
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Spring's built-in handlers (malformed JSON, 404, 405 ...) also get the correlationId.
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.withCorrelationId(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
