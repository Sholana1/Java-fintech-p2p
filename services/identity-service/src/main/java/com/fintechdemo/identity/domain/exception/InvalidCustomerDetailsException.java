package com.fintechdemo.identity.domain.exception;

public class InvalidCustomerDetailsException extends RuntimeException{
    public InvalidCustomerDetailsException(String message) {
        super(message);
    }
}
