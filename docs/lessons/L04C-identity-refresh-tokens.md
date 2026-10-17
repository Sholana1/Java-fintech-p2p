# L04 Part C — Rotating refresh tokens, reuse detection, logout

Presented: 2026-10-17 · Status: learner typing · Verified in Claude's scratchpad against real PostgreSQL 18.6 (all checks below)

## Review (state at start of L04C)

- ✅ New since last time: `CustomerResponse.java`, `RegisterCustomerRequest.java`, both correct.
- ❌ `RegisterCustomerRequest.java:7`: `"RegisterCustomerRequestRequest[..."` → `"RegisterCustomerRequest[..."` (typo).
- ❌ **Still empty (9 L04A files)**: `CustomerController`, `CustomerRestMapper`, `IdentityExceptionHandler`, `CustomerJpaEntity`, `CustomerJpaRepository`, `CustomerPersistenceMapper`, `JpaCustomerAdapter`, `Argon2PasswordHasherAdapter`, `ClockConfig`, plus `application.yml` and `v1_create_customer.sql` (rename to `V1__create_customers.sql`).
- ❌ **L04B not started**: none of its 21 files exist; no `~/.fintechdemo/` key pair yet.
- ❌ **L03 still broken**: `Problems.java:23` and `:25`, empty platform-web `.imports`. **`mvn install` fails until these are fixed.**
- ❌ Docker: VMP still off.

⚠️ **Mentor's call:** you are now 3 lessons behind (L03 fixes, L04A, L04B), and every lesson builds on the last. Do the catch-up before L05, or each NEXT adds code you can't run. L04C below is complete and saved; type it after L04B.

## 1. Story

A thief copies Tolu's refresh token from a rooted phone. Both of them use it.
The second use of the same token proves a copy exists. We can't tell who is who, so we end the whole session: the thief is locked out and Tolu simply logs in again.

## 2. Goal

Login returns a refresh token too. `POST /auth/refresh` swaps it for a new pair (rotation). A replayed token revokes the whole session family. `POST /auth/logout` ends the session.

## 3. Files

```
services/identity-service/src/main/
├── java/com/fintechdemo/identity/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── IssuedTokens.java                          ← NEW
│   │   │   ├── RefreshToken.java                          ← NEW
│   │   │   └── RefreshTokenPolicy.java                    ← NEW
│   │   └── exception/
│   │       └── InvalidRefreshTokenException.java          ← NEW
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── LoginUseCase.java                      ← CHANGED (returns IssuedTokens)
│   │   │   │   ├── LogoutCommand.java                     ← NEW
│   │   │   │   ├── LogoutUseCase.java                     ← NEW
│   │   │   │   ├── RefreshTokensCommand.java              ← NEW
│   │   │   │   └── RefreshTokensUseCase.java              ← NEW
│   │   │   └── out/
│   │   │       ├── CustomerPort.java                      ← CHANGED (findById)
│   │   │       ├── GeneratedRefreshToken.java             ← NEW
│   │   │       ├── RefreshTokenGeneratorPort.java         ← NEW
│   │   │       └── RefreshTokenPort.java                  ← NEW
│   │   └── service/
│   │       ├── LoginService.java                          ← CHANGED (starts a session)
│   │       ├── LogoutService.java                         ← NEW
│   │       ├── RefreshTokensService.java                  ← NEW
│   │       └── TokenPairIssuer.java                       ← NEW
│   ├── adapter/
│   │   ├── in/rest/
│   │   │   ├── AuthController.java                        ← CHANGED (/refresh, /logout)
│   │   │   ├── IdentityExceptionHandler.java              ← CHANGED (invalid-refresh-token)
│   │   │   ├── RefreshTokenRequest.java                   ← NEW
│   │   │   └── TokenResponse.java                         ← CHANGED (refresh token fields)
│   │   └── out/
│   │       ├── persistence/
│   │       │   ├── JpaCustomerAdapter.java                ← CHANGED (findById)
│   │       │   ├── JpaRefreshTokenAdapter.java            ← NEW
│   │       │   ├── RefreshTokenJpaEntity.java             ← NEW
│   │       │   └── RefreshTokenJpaRepository.java         ← NEW
│   │       └── security/
│   │           └── SecureRandomRefreshTokenGeneratorAdapter.java   ← NEW
│   └── config/
│       ├── JwtConfig.java                                 ← CHANGED (RefreshTokenPolicy bean)
│       ├── JwtProperties.java                             ← CHANGED (refreshTokenTtl)
│       └── SecurityConfig.java                            ← CHANGED (permit /refresh, /logout)
└── resources/
    ├── application.yml                                    ← CHANGED (refresh-token-ttl)
    └── db/migration/
        └── V2__create_refresh_tokens.sql                  ← NEW
```

31 files: 20 new, 11 changed. Everything else stays as in L04A/L04B.

## 4. Code

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/RefreshToken.java`

```java
package com.fintechdemo.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One refresh token, stored only as a hash. Tokens from the same login share a familyId:
 * each refresh replaces the token with a successor in the same family, so a stolen, replayed token
 * can be traced to its family and the whole family revoked.
 */
public record RefreshToken(UUID id, UUID familyId, CustomerId customerId, String tokenHash,
        Instant issuedAt, Instant expiresAt) {

    public RefreshToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (!Objects.requireNonNull(expiresAt, "expiresAt").isAfter(issuedAt)) {
            throw new IllegalArgumentException("A refresh token must expire after it is issued");
        }
    }

    /** The first token of a new login session. */
    public static RefreshToken startFamily(CustomerId customerId, String tokenHash, Instant now, Duration ttl) {
        return new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), customerId, tokenHash, now, now.plus(ttl));
    }

    /** The token that replaces this one: same family and customer, new secret, fresh expiry. */
    public RefreshToken successor(String newTokenHash, Instant now, Duration ttl) {
        return new RefreshToken(UUID.randomUUID(), familyId, customerId, newTokenHash, now, now.plus(ttl));
    }

    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", familyId=" + familyId + ", expiresAt=" + expiresAt + "]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/RefreshTokenPolicy.java`

```java
package com.fintechdemo.identity.domain.model;

import java.time.Duration;
import java.util.Objects;

/** How long a refresh token lives. Each successful refresh starts a new period (sliding expiry). */
public record RefreshTokenPolicy(Duration timeToLive) {

    public RefreshTokenPolicy {
        if (Objects.requireNonNull(timeToLive, "timeToLive").isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("Refresh token time to live must be positive");
        }
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/IssuedTokens.java`

```java
package com.fintechdemo.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** What a login or a refresh hands back: a short-lived access token and a long-lived refresh token. */
public record IssuedTokens(AccessToken accessToken, String refreshToken, Instant refreshTokenExpiresAt) {

    public IssuedTokens {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
    }

    public long refreshTokenExpiresInSeconds() {
        return Duration.between(accessToken.issuedAt(), refreshTokenExpiresAt).toSeconds();
    }

    @Override
    public String toString() {
        return "IssuedTokens[accessToken=***, refreshToken=***, refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/exception/InvalidRefreshTokenException.java`

```java
package com.fintechdemo.identity.domain.exception;

/** Unknown, expired, already used or revoked. One message for all: the client must log in again. */
public final class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid, expired or revoked. Please log in again.");
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/LoginUseCase.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.application.port.in;

import com.fintechdemo.identity.domain.model.IssuedTokens;

/** Inbound port: exchange a phone number and password for an access token and a refresh token. */
public interface LoginUseCase {

    IssuedTokens login(LoginCommand command);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/RefreshTokensCommand.java`

```java
package com.fintechdemo.identity.application.port.in;

/** Input to a refresh: the raw refresh token the client holds. */
public record RefreshTokensCommand(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokensCommand[refreshToken=***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/RefreshTokensUseCase.java`

```java
package com.fintechdemo.identity.application.port.in;

import com.fintechdemo.identity.domain.model.IssuedTokens;

/** Inbound port: trade a refresh token for a new access token AND a new refresh token (rotation). */
public interface RefreshTokensUseCase {

    IssuedTokens refresh(RefreshTokensCommand command);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/LogoutCommand.java`

```java
package com.fintechdemo.identity.application.port.in;

/** Input to logout: the refresh token of the session to end. */
public record LogoutCommand(String refreshToken) {

    @Override
    public String toString() {
        return "LogoutCommand[refreshToken=***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/LogoutUseCase.java`

```java
package com.fintechdemo.identity.application.port.in;

/** Inbound port: end a login session by revoking its whole refresh-token family. */
public interface LogoutUseCase {

    void logout(LogoutCommand command);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/out/CustomerPort.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.application.port.out;

import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.CustomerId;
import com.fintechdemo.identity.domain.model.PhoneNumber;
import java.util.Optional;

/** Outbound port: where customers are stored. The application never knows it is JPA or PostgreSQL. */
public interface CustomerPort {

    /** Stores a new customer. Throws CustomerAlreadyExistsException if the phone or email is taken. */
    void add(Customer customer);

    Optional<Customer> findByPhoneNumber(PhoneNumber phoneNumber);

    Optional<Customer> findById(CustomerId id);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/out/GeneratedRefreshToken.java`

```java
package com.fintechdemo.identity.application.port.out;

/** A fresh refresh token: the raw value goes to the client once; only the hash is stored. */
public record GeneratedRefreshToken(String rawValue, String hash) {

    @Override
    public String toString() {
        return "GeneratedRefreshToken[rawValue=***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/out/RefreshTokenGeneratorPort.java`

```java
package com.fintechdemo.identity.application.port.out;

/** Outbound port: creates unguessable refresh tokens and hashes them for storage and lookup. */
public interface RefreshTokenGeneratorPort {

    GeneratedRefreshToken generate();

    /** The same input always gives the same hash, so a presented token can be looked up by its hash. */
    String hash(String rawValue);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/out/RefreshTokenPort.java`

```java
package com.fintechdemo.identity.application.port.out;

import com.fintechdemo.identity.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;

/** Outbound port: refresh-token storage with atomic, race-safe state changes. */
public interface RefreshTokenPort {

    void add(RefreshToken token);

    /**
     * Atomically marks the token as used, but only if it is still active (not used, not revoked, not expired).
     * Of two concurrent claims of the same token, exactly one succeeds. Empty means it could not be claimed.
     */
    Optional<RefreshToken> claim(String tokenHash, Instant now);

    /** If this token was ALREADY used, someone replayed it: revoke its whole family. Returns tokens revoked. */
    int revokeFamilyIfReused(String tokenHash, Instant now);

    /** Logout: revoke every still-active token in this token's family. Returns tokens revoked. */
    int revokeFamilyOf(String tokenHash, Instant now);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/service/TokenPairIssuer.java`

```java
package com.fintechdemo.identity.application.service;

import com.fintechdemo.identity.application.port.out.AccessTokenIssuerPort;
import com.fintechdemo.identity.application.port.out.GeneratedRefreshToken;
import com.fintechdemo.identity.application.port.out.RefreshTokenGeneratorPort;
import com.fintechdemo.identity.application.port.out.RefreshTokenPort;
import com.fintechdemo.identity.domain.model.AccessToken;
import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.IssuedTokens;
import com.fintechdemo.identity.domain.model.RefreshToken;
import com.fintechdemo.identity.domain.model.RefreshTokenPolicy;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Issues an access token plus a stored refresh token. Shared by login (new family) and refresh (same family). */
@Service
class TokenPairIssuer {

    private final AccessTokenIssuerPort accessTokens;
    private final RefreshTokenGeneratorPort generator;
    private final RefreshTokenPort refreshTokens;
    private final RefreshTokenPolicy policy;

    TokenPairIssuer(AccessTokenIssuerPort accessTokens, RefreshTokenGeneratorPort generator,
            RefreshTokenPort refreshTokens, RefreshTokenPolicy policy) {
        this.accessTokens = accessTokens;
        this.generator = generator;
        this.refreshTokens = refreshTokens;
        this.policy = policy;
    }

    IssuedTokens startSession(Customer customer, Instant now) {
        GeneratedRefreshToken secret = generator.generate();
        RefreshToken token = RefreshToken.startFamily(customer.id(), secret.hash(), now, policy.timeToLive());
        return issue(customer, token, secret, now);
    }

    IssuedTokens continueSession(Customer customer, RefreshToken usedToken, Instant now) {
        GeneratedRefreshToken secret = generator.generate();
        RefreshToken token = usedToken.successor(secret.hash(), now, policy.timeToLive());
        return issue(customer, token, secret, now);
    }

    private IssuedTokens issue(Customer customer, RefreshToken token, GeneratedRefreshToken secret, Instant now) {
        refreshTokens.add(token);
        AccessToken accessToken = accessTokens.issue(customer, now);
        return new IssuedTokens(accessToken, secret.rawValue(), token.expiresAt());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/service/LoginService.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.application.service;

import com.fintechdemo.identity.application.port.in.LoginCommand;
import com.fintechdemo.identity.application.port.in.LoginUseCase;
import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.application.port.out.PasswordHasherPort;
import com.fintechdemo.identity.domain.exception.InvalidCredentialsException;
import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.IssuedTokens;
import com.fintechdemo.identity.domain.model.PasswordHash;
import com.fintechdemo.identity.domain.model.PhoneNumber;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Logs a customer in and starts a new session (refresh-token family). An unknown phone number still costs
 * one full Argon2 check (against a decoy hash), so response time does not reveal whether it is registered.
 */
@Service
public class LoginService implements LoginUseCase {

    private final CustomerPort customers;
    private final PasswordHasherPort passwordHasher;
    private final TokenPairIssuer tokenPairs;
    private final Clock clock;
    private final PasswordHash decoyHash;

    LoginService(CustomerPort customers, PasswordHasherPort passwordHasher, TokenPairIssuer tokenPairs,
            Clock clock) {
        this.customers = customers;
        this.passwordHasher = passwordHasher;
        this.tokenPairs = tokenPairs;
        this.clock = clock;
        this.decoyHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Override
    public IssuedTokens login(LoginCommand command) {
        Optional<Customer> customer = customers.findByPhoneNumber(new PhoneNumber(command.phoneNumber()));
        PasswordHash hashToCheck = customer.map(Customer::passwordHash).orElse(decoyHash);

        boolean passwordMatches = passwordHasher.matches(command.password(), hashToCheck);
        if (customer.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return tokenPairs.startSession(customer.get(), clock.instant());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/service/RefreshTokensService.java`

```java
package com.fintechdemo.identity.application.service;

import com.fintechdemo.identity.application.port.in.RefreshTokensCommand;
import com.fintechdemo.identity.application.port.in.RefreshTokensUseCase;
import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.application.port.out.RefreshTokenGeneratorPort;
import com.fintechdemo.identity.application.port.out.RefreshTokenPort;
import com.fintechdemo.identity.domain.exception.InvalidRefreshTokenException;
import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.IssuedTokens;
import com.fintechdemo.identity.domain.model.RefreshToken;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation (RFC 9700, section 4.14.2): every refresh consumes the presented token and issues a
 * successor. Presenting a token that was already consumed means two parties hold it: we cannot tell the
 * customer from the thief, so the whole family is revoked and both must log in again.
 */
@Service
public class RefreshTokensService implements RefreshTokensUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokensService.class);

    private final RefreshTokenPort refreshTokens;
    private final RefreshTokenGeneratorPort generator;
    private final CustomerPort customers;
    private final TokenPairIssuer tokenPairs;
    private final Clock clock;

    RefreshTokensService(RefreshTokenPort refreshTokens, RefreshTokenGeneratorPort generator,
            CustomerPort customers, TokenPairIssuer tokenPairs, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.customers = customers;
        this.tokenPairs = tokenPairs;
        this.clock = clock;
    }

    /**
     * One transaction: consuming the old token and storing its successor commit together, so a concurrent
     * reuse check always sees the successor and revokes it too. noRollbackFor: when we reject a replayed
     * token, the family revocation must still commit. No network calls inside: DB work and signing only.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedTokens refresh(RefreshTokensCommand command) {
        Instant now = clock.instant();
        String hash = generator.hash(command.refreshToken());

        RefreshToken consumed = refreshTokens.claim(hash, now).orElseThrow(() -> reject(hash, now));
        Customer customer = customers.findById(consumed.customerId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return tokenPairs.continueSession(customer, consumed, now);
    }

    private InvalidRefreshTokenException reject(String hash, Instant now) {
        int revoked = refreshTokens.revokeFamilyIfReused(hash, now);
        if (revoked > 0) {
            log.warn("Refresh token reuse detected: revoked {} active token(s) in the family", revoked);
        }
        return new InvalidRefreshTokenException();
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/service/LogoutService.java`

```java
package com.fintechdemo.identity.application.service;

import com.fintechdemo.identity.application.port.in.LogoutCommand;
import com.fintechdemo.identity.application.port.in.LogoutUseCase;
import com.fintechdemo.identity.application.port.out.RefreshTokenGeneratorPort;
import com.fintechdemo.identity.application.port.out.RefreshTokenPort;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Ends a session: every token in the family is revoked. Unknown tokens are ignored silently, so logout
 * always "succeeds" and can't be used to test whether a token exists.
 * Access tokens already issued stay valid until they expire (at most 10 minutes): the price of stateless JWTs.
 */
@Service
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenPort refreshTokens;
    private final RefreshTokenGeneratorPort generator;
    private final Clock clock;

    LogoutService(RefreshTokenPort refreshTokens, RefreshTokenGeneratorPort generator, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.clock = clock;
    }

    @Override
    public void logout(LogoutCommand command) {
        refreshTokens.revokeFamilyOf(generator.hash(command.refreshToken()), clock.instant());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/RefreshTokenRequest.java`

```java
package com.fintechdemo.identity.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/** The JSON body of POST /auth/refresh and POST /auth/logout. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/TokenResponse.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.domain.model.IssuedTokens;

/**
 * Login and refresh result. The app sends accessToken as "Authorization: Bearer ..." on every call, and keeps
 * refreshToken in secure storage (Android Keystore / iOS Keychain) to get a new pair when the access token expires.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
        long refreshTokenExpiresIn) {

    static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken().value(), "Bearer", tokens.accessToken().expiresInSeconds(),
                tokens.refreshToken(), tokens.refreshTokenExpiresInSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, refreshToken=***, expiresIn=" + expiresIn + "]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/AuthController.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.application.port.in.LoginCommand;
import com.fintechdemo.identity.application.port.in.LoginUseCase;
import com.fintechdemo.identity.application.port.in.LogoutCommand;
import com.fintechdemo.identity.application.port.in.LogoutUseCase;
import com.fintechdemo.identity.application.port.in.RefreshTokensCommand;
import com.fintechdemo.identity.application.port.in.RefreshTokensUseCase;
import com.fintechdemo.identity.application.port.out.AccessTokenIssuerPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase login;
    private final RefreshTokensUseCase refreshTokens;
    private final LogoutUseCase logout;

    public AuthController(LoginUseCase login, RefreshTokensUseCase refreshTokens, LogoutUseCase logout) {
        this.login = login;
        this.refreshTokens = refreshTokens;
        this.logout = logout;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(login.login(new LoginCommand(request.phoneNumber(), request.password())));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(refreshTokens.refresh(new RefreshTokensCommand(request.refreshToken())));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        logout.logout(new LogoutCommand(request.refreshToken()));
    }

    /** Protected: Spring Security has already verified the token's signature, expiry, issuer and audience. */
    @GetMapping("/me")
    public AuthenticatedCustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new AuthenticatedCustomerResponse(jwt.getSubject(),
                jwt.getClaimAsString(AccessTokenIssuerPort.KYC_TIER_CLAIM), jwt.getExpiresAt());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/IdentityExceptionHandler.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.domain.exception.CustomerAlreadyExistsException;
import com.fintechdemo.identity.domain.exception.InvalidCredentialsException;
import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;
import com.fintechdemo.identity.domain.exception.InvalidRefreshTokenException;
import com.fintechdemo.platform.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps identity's domain exceptions to problem+json. Runs before platform-web's catch-all handler. */
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
        return Problems.of(HttpStatus.CONFLICT, "customer-already-exists", "Customer already exists",
                ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return Problems.of(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid credentials",
                ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return Problems.of(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", "Invalid refresh token",
                ex.getMessage());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/RefreshTokenJpaEntity.java`

```java
package com.fintechdemo.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** The refresh_tokens row. used_at and revoked_at are only ever changed by guarded bulk UPDATEs. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Transient
    private boolean newRow = true;

    protected RefreshTokenJpaEntity() {
        // for JPA
    }

    RefreshTokenJpaEntity(UUID id, UUID familyId, UUID customerId, String tokenHash, Instant issuedAt,
            Instant expiresAt) {
        this.id = id;
        this.familyId = familyId;
        this.customerId = customerId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newRow = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }

    UUID getFamilyId() {
        return familyId;
    }

    UUID getCustomerId() {
        return customerId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/RefreshTokenJpaRepository.java`

```java
package com.fintechdemo.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Guarded UPDATEs: the WHERE clause is the rule. PostgreSQL row locks make two concurrent UPDATEs of the same
 * row run one after the other; the second re-checks the WHERE against the first one's result and matches 0 rows.
 */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshTokenJpaEntity t SET t.usedAt = :now
            WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now""")
    int markUsedIfActive(@Param("hash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now
            WHERE t.revokedAt IS NULL AND t.familyId = (
                SELECT r.familyId FROM RefreshTokenJpaEntity r WHERE r.tokenHash = :hash AND r.usedAt IS NOT NULL)""")
    int revokeFamilyIfUsed(@Param("hash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now
            WHERE t.revokedAt IS NULL AND t.familyId = (
                SELECT r.familyId FROM RefreshTokenJpaEntity r WHERE r.tokenHash = :hash)""")
    int revokeFamilyOf(@Param("hash") String tokenHash, @Param("now") Instant now);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/JpaRefreshTokenAdapter.java`

```java
package com.fintechdemo.identity.adapter.out.persistence;

import com.fintechdemo.identity.application.port.out.RefreshTokenPort;
import com.fintechdemo.identity.domain.model.CustomerId;
import com.fintechdemo.identity.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** RefreshTokenPort backed by PostgreSQL. Each method is one short transaction; no network call inside any. */
@Component
class JpaRefreshTokenAdapter implements RefreshTokenPort {

    private final RefreshTokenJpaRepository repository;

    JpaRefreshTokenAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void add(RefreshToken token) {
        repository.save(new RefreshTokenJpaEntity(token.id(), token.familyId(), token.customerId().value(),
                token.tokenHash(), token.issuedAt(), token.expiresAt()));
    }

    @Override
    @Transactional
    public Optional<RefreshToken> claim(String tokenHash, Instant now) {
        if (repository.markUsedIfActive(tokenHash, now) == 0) {
            return Optional.empty();
        }
        return repository.findByTokenHash(tokenHash).map(JpaRefreshTokenAdapter::toDomain);
    }

    @Override
    @Transactional
    public int revokeFamilyIfReused(String tokenHash, Instant now) {
        return repository.revokeFamilyIfUsed(tokenHash, now);
    }

    @Override
    @Transactional
    public int revokeFamilyOf(String tokenHash, Instant now) {
        return repository.revokeFamilyOf(tokenHash, now);
    }

    private static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return new RefreshToken(entity.getId(), entity.getFamilyId(), new CustomerId(entity.getCustomerId()),
                entity.getTokenHash(), entity.getIssuedAt(), entity.getExpiresAt());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/JpaCustomerAdapter.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.adapter.out.persistence;

import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.domain.exception.CustomerAlreadyExistsException;
import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.CustomerId;
import com.fintechdemo.identity.domain.model.PhoneNumber;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * CustomerPort backed by PostgreSQL via JPA.
 * No "SELECT ... WHERE phone = ?" check first: two registrations racing would both pass it.
 * The UNIQUE constraints decide, atomically; we translate their violation into a domain exception.
 */
@Component
class JpaCustomerAdapter implements CustomerPort {

    // PostgreSQL error code for unique_violation (Appendix A of the PostgreSQL docs).
    private static final String UNIQUE_VIOLATION = "23505";

    private final CustomerJpaRepository repository;

    JpaCustomerAdapter(CustomerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(Customer customer) {
        try {
            // saveAndFlush: run the INSERT now, inside this try, not later at commit time.
            repository.saveAndFlush(CustomerPersistenceMapper.toEntity(customer));
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueViolation(violation)) {
                throw new CustomerAlreadyExistsException();
            }
            throw violation; // NOT NULL or CHECK failed: that is a bug, not a duplicate.
        }
    }

    @Override
    public Optional<Customer> findByPhoneNumber(PhoneNumber phoneNumber) {
        return repository.findByPhoneNumber(phoneNumber.value()).map(CustomerPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return repository.findById(id.value()).map(CustomerPersistenceMapper::toDomain);
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException violation) {
        return violation.getMostSpecificCause() instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/security/SecureRandomRefreshTokenGeneratorAdapter.java`

```java
package com.fintechdemo.identity.adapter.out.security;

import com.fintechdemo.identity.application.port.out.GeneratedRefreshToken;
import com.fintechdemo.identity.application.port.out.RefreshTokenGeneratorPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 256 random bits from the OS's secure random source, encoded URL-safe (43 characters).
 * Stored as SHA-256: a fast hash is enough here because the input is random, so nobody can guess
 * or brute-force it. Passwords are different: humans choose them, so they need slow Argon2id.
 */
@Component
class SecureRandomRefreshTokenGeneratorAdapter implements RefreshTokenGeneratorPort {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public GeneratedRefreshToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawValue = encoder.encodeToString(bytes);
        return new GeneratedRefreshToken(rawValue, hash(rawValue));
    }

    @Override
    public String hash(String rawValue) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every Java runtime must support SHA-256", impossible);
        }
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/config/JwtProperties.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/** The identity.jwt.* settings from application.yml (access and refresh tokens), type-checked at startup. */
@ConfigurationProperties(prefix = "identity.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String keyId,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Resource privateKeyLocation,
        Resource publicKeyLocation) {
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/config/JwtConfig.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.config;

import com.fintechdemo.identity.domain.model.RefreshTokenPolicy;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Loads the RSA key pair (PEM files), builds the JWT encoder/decoder, and the refresh-token policy. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    RSAKey signingKey(JwtProperties properties) throws IOException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        try (InputStream in = properties.publicKeyLocation().getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(in);
        }
        try (InputStream in = properties.privateKeyLocation().getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(in);
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(properties.keyId()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** Verifies signature and expiry (defaults), plus OUR issuer and OUR audience: anything else is rejected. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        JwtClaimValidator<List<String>> audience = new JwtClaimValidator<>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
        return decoder;
    }

    @Bean
    RefreshTokenPolicy refreshTokenPolicy(JwtProperties properties) {
        return new RefreshTokenPolicy(properties.refreshTokenTtl());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/config/SecurityConfig.java` (CHANGED: whole file)

```java
package com.fintechdemo.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless API security: no sessions, no cookies, every protected call carries a Bearer JWT.
 * CSRF protection is off because CSRF abuses cookies the browser sends automatically; we use none.
 * Refresh and logout are open: possession of the refresh token IS the credential (the access token may be expired).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/customers", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

### `services/identity-service/src/main/resources/application.yml` (CHANGED: whole file)

```yaml
spring:
  application:
    name: identity-service
  # Local dev: read SERVICE_DB_PASSWORD from the repo's git-ignored .env (paths are relative to this module).
  config:
    import: optional:file:../../.env[.properties]
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/identity
    # Runtime role: data only (SELECT, INSERT, UPDATE). It cannot create, alter or drop tables.
    username: identity_app
    password: ${SERVICE_DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
  flyway:
    # Migration role: owns the schema. Used only by Flyway at startup.
    user: identity_owner
    password: ${SERVICE_DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

server:
  port: 8081

identity:
  jwt:
    issuer: fintechdemo-identity
    audience: fintechdemo-api
    key-id: identity-dev-1
    access-token-ttl: 10m
    # Sliding: every refresh issues a new token that lives this long again.
    refresh-token-ttl: 14d
    # The key pair lives OUTSIDE the repo, in your home folder: a private key must never be committed.
    private-key-location: file:${user.home}/.fintechdemo/identity-jwt.key
    public-key-location: file:${user.home}/.fintechdemo/identity-jwt.pub
```

### `services/identity-service/src/main/resources/db/migration/V2__create_refresh_tokens.sql`

```sql
-- Refresh tokens are stored ONLY as SHA-256 hashes: a leaked table cannot be replayed.
-- Tokens from one login share family_id; reuse detection and logout revoke the whole family.

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    family_id   UUID        NOT NULL,
    customer_id UUID        NOT NULL REFERENCES customers (id),
    token_hash  VARCHAR(64) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_lifetime CHECK (expires_at > issued_at)
);

-- Every revoke statement finds a family by family_id.
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);

GRANT SELECT, INSERT, UPDATE ON refresh_tokens TO identity_app;
```

## 5. Why

- **Rotation with reuse detection** (RFC 9700 §4.14.2, https://www.rfc-editor.org/rfc/rfc9700#section-4.14.2): each refresh consumes the token and issues a successor. A second use of a consumed token proves a copy exists, so the whole family is revoked.
- **Guarded UPDATE as the gate**: `UPDATE … SET used_at = now WHERE token_hash = ? AND used_at IS NULL AND …`. PostgreSQL's row lock makes concurrent claims queue up, and only one matches. Verified: 5 parallel refreshes give `200` once and `401` four times. This is the same pattern the ledger uses in L09.
- **One transaction for claim + successor**, with `noRollbackFor`: the first design used separate transactions, and a revoke could slip between them and miss the new token. Verified: in the race, the winner's fresh token was revoked too.
- **Stored as SHA-256, not Argon2**: the token is 256 random bits, so it can't be guessed, and a fast hash is safe and gives us an indexed lookup. Passwords are low-entropy, so they need slow Argon2. A leaked table is useless: the raw value is never stored (verified, 0 rows).
- **Logout always returns 204**: unknown tokens are ignored, so logout can't be used to probe for valid tokens. Access tokens stay valid for at most 10 minutes; that is the stateless trade-off.
- **The trade-off we accept**: an app that retries a refresh after a network drop looks like a replay and gets logged out. That is safe, and the RFC accepts it.

## 6. Run (after L03 fixes, L04A, L04B; Docker running)

```powershell
mvn -q install
cd services/identity-service
java -jar target/identity-service-0.1.0-SNAPSHOT.jar
```

Expected: `Successfully applied 1 migration` (V2), then `Started IdentityServiceApplication`.

Git Bash:

```bash
H='Content-Type: application/json'
R1=$(curl -s -H "$H" -d '{"phoneNumber":"+2348031234567","password":"correct horse battery"}' \
  http://localhost:8081/auth/login | sed -E 's/.*"refreshToken":"([^"]+)".*/\1/')
curl -s -H "$H" -d "{\"refreshToken\":\"$R1\"}" http://localhost:8081/auth/refresh
```

✅ Verified responses (scratchpad, PostgreSQL 18.6):

```
login    {"accessToken":"eyJ…","tokenType":"Bearer","expiresIn":600,"refreshToken":"3Sst34qOEasQ-4omncAgy5XB3r5mGRzwX038433kfyc","refreshTokenExpiresIn":1209600}
refresh R1                → 200, new access + NEW refresh token R2
refresh R1 again (replay) → 401 {"detail":"Refresh token is invalid, expired or revoked. Please log in again.",…,"type":".../invalid-refresh-token",…}
refresh R2 after replay   → 401  (whole family revoked)
logout R3                 → 204, then refresh R3 → 401
refresh "not-a-real-token" → 401;  logout unknown token → 204
server log                → WARN "Refresh token reuse detected: revoked 2 active token(s) in the family"
```

## 7. Break it

Send the same refresh token 5 times at the same instant:

```bash
R=$(curl -s -H "$H" -d '{"phoneNumber":"+2348031234567","password":"correct horse battery"}' \
  http://localhost:8081/auth/login | sed -E 's/.*"refreshToken":"([^"]+)".*/\1/')
for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{http_code} ' -H "$H" -d "{\"refreshToken\":\"$R\"}" \
  http://localhost:8081/auth/refresh & done; wait
```

✅ Verified: `200 401 401 401 401`. In the database, both tokens of that family show `revoked = t`, including the brand-new one the winner received.

## 8. Interview line

"Refresh tokens are 256-bit random values stored only as SHA-256 hashes and rotated on every use, as RFC 9700 recommends. A guarded UPDATE is the atomic gate, so concurrent refreshes of one token yield exactly one success. A second use of a consumed token revokes the whole session family, in the same transaction that would have stored the successor, so even a race can't leave the thief with a live token."

## 9. Checkpoint

1. **Catch up first**: L03 fixes → finish L04A → type L04B (+ key pair). Run `mvn -q install` after each.
2. Type the 31 L04C files.
3. Enable VMP and restart (Docker); then run everything plus Break it.
4. Commit after each lesson works, e.g. `git add .` then `git commit -m "L04C refresh token rotation"`.
5. Type **NEXT** → L05: device binding, transaction PIN (keyed hash + lockout), PIN-verify gRPC.

## My notes

(learner: add your own notes here)
