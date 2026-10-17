package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public EmailAddress {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()){
            throw new InvalidCustomerDetailsException("Email address is not valid");
        }
    }


}
