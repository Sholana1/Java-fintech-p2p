package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;

public class PasswordPolicy {
    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {

    }

    public static void check(String rawPassword) {
        int length = rawPassword == null ? 0 : rawPassword.codePointCount(0, rawPassword.length());
        if(length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new InvalidCustomerDetailsException("Password must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters long");
        }
    }
}
