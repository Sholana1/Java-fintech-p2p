package com.fintechdemo.identity.application.port.out;

import com.fintechdemo.identity.domain.model.PasswordHash;

public interface PasswordHasherPort {
    PasswordHash hash(String rawPassword);
    boolean matches(String rawPassword, PasswordHash hash);
}
