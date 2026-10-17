package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;
import java.util.regex.Pattern;

/** A phone number in E.164 format: "+", country code, number; at most 15 digits. E.g. +2348031234567. */
public record PhoneNumber(String value) {

    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

    public PhoneNumber {
        if (value == null || !E164.matcher(value).matches()) {
            throw new InvalidCustomerDetailsException("Phone number must be in E.164 format, e.g. +2348031234567");
        }
    }

    /** Safe for screens and logs: +234*******567. */
    public String masked() {
        return value.substring(0, 4) + "*".repeat(value.length() - 7) + value.substring(value.length() - 3);
    }
}
