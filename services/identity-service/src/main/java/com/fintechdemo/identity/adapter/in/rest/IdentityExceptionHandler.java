package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.domain.exception.CustomerAlreadyExistsException;
import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;
import com.fintechdemo.platform.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {
    @ExceptionHandler(InvalidCustomerDetailsException.class)
    ProblemDetail handleInvalidDetails(InvalidCustomerDetailsException ex) {
        return Problems.of(HttpStatus.BAD_REQUEST, "invalid-customer-details", "Invalid customer details",
            ex.getMessage());
    }

    @ExceptionHandler(CustomerAlreadyExistsException.class)
    ProblemDetail handleAlreadyExists(CustomerAlreadyExistsException ex) {
        return Problems.of(HttpStatus.CONFLICT, "customer-already-exists", "customer already exists", ex.getMessage());
    }
}
