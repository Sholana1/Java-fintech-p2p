package com.fintechdemo.identity.domain.model;

import java.util.Objects;

public record PasswordHash(String value) {
    public PasswordHash {
        Objects.requireNonNull(value,"value");
    }

    @Override
    public String toString() {
        return  "PasswordHash[***]";
    }
}
