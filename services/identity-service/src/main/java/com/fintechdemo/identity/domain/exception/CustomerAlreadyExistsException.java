package com.fintechdemo.identity.domain.exception;

public class CustomerAlreadyExistsException extends RuntimeException{
    public CustomerAlreadyExistsException() {
        super("A customer with this phone number or email already exists");
    }
}
