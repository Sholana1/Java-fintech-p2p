package com.fintechdemo.identity.adapter.out.security;

import com.fintechdemo.identity.application.port.out.PasswordHasherPort;
import com.fintechdemo.identity.domain.model.PasswordHash;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id with OWASP's minimum settings: 19 MiB memory, 2 iterations, 1 thread, 16-byte salt, 32-byte hash.
 * The output stores its own parameters ("$argon2id$v=19$m=19456,t=2,p=1$..."), so we can raise them later
 * and old hashes still verify.
 */
@Component
class Argon2PasswordHasherAdapter implements PasswordHasherPort {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder =
        new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return encoder.matches(rawPassword, hash.value());
    }
}
