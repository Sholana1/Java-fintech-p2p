# L04 Part A — identity-service: register a customer (Argon2id, Flyway, least-privilege DB)

Presented: 2026-10-17 · Status: learner typing · Verified in Claude's scratchpad against a real PostgreSQL 18.6 (all checks below)

## Review of L03 (commit 7f26af9)

- ❌ `platform/platform-web/src/main/java/com/fintechdemo/platform/web/Problems.java:23`: `correlationIdFilter.MDC_KEY` → `CorrelationIdFilter.MDC_KEY` (class names start with a capital). **The build fails until this is fixed.**
- ❌ `Problems.java:25`: `"corrolationId"` → `"correlationId"`. That field name is an API contract; apps and support would look for the wrong key.
- ❌ `platform/platform-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: the file is empty, so `GlobalExceptionHandler` never loads → line 1 must be `com.fintechdemo.platform.web.ErrorHandlingAutoConfiguration`.
- ❌ `platform/platform-web/pom.xml:1` and `platform/platform-money/pom.xml:1`: blank line before `<?xml` → delete it.
- ❌ `platform-money/pom.xml` and `platform-observability/pom.xml`: unused test deps → use the L03 Appendix files.
- ❌ Still open: ADR-0002, Maven wrapper, GitHub push.
- ❌ Docker: `wsl --status` still says Virtual Machine Platform is not enabled. (`HypervisorPresent=True` comes from Windows' virtualization-based security, not from VMP.) In an **Administrator** PowerShell run `wsl.exe --install --no-distribution` (the command WSL itself suggests), then restart.
- ✅ `pom.xml`, `ErrorHandlingAutoConfiguration.java`, `GlobalExceptionHandler.java` (formatting only), `FieldViolation.java`: fine.

## 1. Story

Tolu downloads the app and signs up with +2348031234567. Her brother signs up a second later with the same number on a second phone.
Exactly one account must exist, her password must never be stored readable, and the service's DB login must not be able to drop the table.

## 2. Goal

`POST /customers` registers a customer in PostgreSQL. The password is stored as Argon2id, duplicates are rejected atomically, and the service runs with a data-only DB role.

## 3. Files

```
fintechdemo/
├── pom.xml                                                    ← CHANGED (module + Bouncy Castle version)
└── services/                                                  ← NEW folder
    └── identity-service/                                      ← NEW service
        ├── pom.xml                                            ← NEW
        └── src/
            └── main/
                ├── java/
                │   └── com/fintechdemo/identity/
                │       ├── IdentityServiceApplication.java                ← NEW
                │       ├── domain/
                │       │   ├── model/
                │       │   │   ├── Customer.java                          ← NEW
                │       │   │   ├── CustomerId.java                        ← NEW
                │       │   │   ├── EmailAddress.java                      ← NEW
                │       │   │   ├── KycTier.java                           ← NEW
                │       │   │   ├── PasswordHash.java                      ← NEW
                │       │   │   ├── PasswordPolicy.java                    ← NEW
                │       │   │   └── PhoneNumber.java                       ← NEW
                │       │   └── exception/
                │       │       ├── CustomerAlreadyExistsException.java    ← NEW
                │       │       └── InvalidCustomerDetailsException.java   ← NEW
                │       ├── application/
                │       │   ├── port/
                │       │   │   ├── in/
                │       │   │   │   ├── RegisterCustomerCommand.java       ← NEW
                │       │   │   │   └── RegisterCustomerUseCase.java       ← NEW
                │       │   │   └── out/
                │       │   │       ├── CustomerPort.java                  ← NEW
                │       │   │       └── PasswordHasherPort.java            ← NEW
                │       │   └── service/
                │       │       └── RegisterCustomerService.java           ← NEW
                │       ├── adapter/
                │       │   ├── in/
                │       │   │   └── rest/
                │       │   │       ├── CustomerController.java            ← NEW
                │       │   │       ├── CustomerResponse.java              ← NEW
                │       │   │       ├── CustomerRestMapper.java            ← NEW
                │       │   │       ├── IdentityExceptionHandler.java      ← NEW
                │       │   │       └── RegisterCustomerRequest.java       ← NEW
                │       │   └── out/
                │       │       ├── persistence/
                │       │       │   ├── CustomerJpaEntity.java             ← NEW
                │       │       │   ├── CustomerJpaRepository.java         ← NEW
                │       │       │   ├── CustomerPersistenceMapper.java     ← NEW
                │       │       │   └── JpaCustomerAdapter.java            ← NEW
                │       │       └── security/                              ← NEW folder (added to CLAUDE.md layout)
                │       │           └── Argon2PasswordHasherAdapter.java   ← NEW
                │       └── config/
                │           └── ClockConfig.java                           ← NEW
                └── resources/
                    ├── application.yml                                    ← NEW
                    └── db/
                        └── migration/
                            └── V1__create_customers.sql                   ← NEW
```

30 files. Dependency direction: `adapter → application → domain`. The domain imports nothing from Spring or JPA.

## 4. Code

### `pom.xml (root, CHANGED: whole file)`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Spring Boot's parent gives every module tested library versions and plugin defaults. -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.fintechdemo</groupId>
    <artifactId>fintechdemo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>fintechdemo-parent</name>
    <description>FINTECHDEMO P2P payments core - parent POM: modules, versions, plugins only</description>

    <!-- Modules are added lesson by lesson. -->
    <modules>
        <module>platform/platform-money</module>
        <module>platform/platform-observability</module>
        <module>platform/platform-web</module>
        <module>services/identity-service</module>
    </modules>

    <properties>
        <java.version>25</java.version>
        <required.java.range>[25,26)</required.java.range>
        <required.maven.range>[3.9.9,)</required.maven.range>
        <!-- Not managed by Spring Boot: pinned here (see ADR-0001). -->
        <bouncycastle.version>1.86</bouncycastle.version>
    </properties>

    <!-- Versions for our own modules and for libraries Boot does not manage: modules then omit <version>. -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.fintechdemo</groupId>
                <artifactId>platform-money</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fintechdemo</groupId>
                <artifactId>platform-observability</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fintechdemo</groupId>
                <artifactId>platform-web</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- Argon2 password hashing in Spring Security needs Bouncy Castle. -->
            <dependency>
                <groupId>org.bouncycastle</groupId>
                <artifactId>bcprov-jdk18on</artifactId>
                <version>${bouncycastle.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <!-- Fail fast on the wrong JDK or Maven, on Windows, macOS and CI alike. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-build-environment</id>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <requireJavaVersion>
                                    <version>${required.java.range}</version>
                                    <message>FINTECHDEMO builds on JDK 25 (LTS). macOS: brew install --cask temurin@25</message>
                                </requireJavaVersion>
                                <requireMavenVersion>
                                    <version>${required.maven.range}</version>
                                    <message>Use the Maven wrapper: ./mvnw (macOS) or .\mvnw.cmd (Windows).</message>
                                </requireMavenVersion>
                                <banDuplicatePomDependencyVersions/>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### `services/identity-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.fintechdemo</groupId>
        <artifactId>fintechdemo-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>identity-service</artifactId>
    <name>identity-service</name>
    <description>Customers, credentials, KYC tier, devices, PIN and tokens.</description>

    <dependencies>
        <!-- Our platform: problem+json errors, validation, Money, JSON logs, tracing. -->
        <dependency>
            <groupId>com.fintechdemo</groupId>
            <artifactId>platform-web</artifactId>
        </dependency>

        <!-- Persistence: JPA over PostgreSQL, schema owned by Flyway migrations. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Password hashing only (Argon2id). Full Spring Security arrives with JWT in L04 Part B. -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>org.bouncycastle</groupId>
            <artifactId>bcprov-jdk18on</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Builds a runnable jar and gives us `mvn spring-boot:run`. -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/IdentityServiceApplication.java`

```java
package com.fintechdemo.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/CustomerId.java`

```java
package com.fintechdemo.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A customer's identity. Generated by us, never by the client. */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/PhoneNumber.java`

```java
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
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/EmailAddress.java`

```java
package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;
import java.util.Locale;
import java.util.regex.Pattern;

/** An email address, trimmed and lower-cased so "Tolu@Mail.com" and "tolu@mail.com" are one customer. */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public EmailAddress {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new InvalidCustomerDetailsException("Email address is not valid");
        }
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/KycTier.java`

```java
package com.fintechdemo.identity.domain.model;

/**
 * KYC level. Every new customer starts at TIER_1; upgrades come from verification (seed data or admin for now).
 * The limits for each tier are not here: L18 loads them from CBN's official rules.
 */
public enum KycTier {
    TIER_1,
    TIER_2,
    TIER_3
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/PasswordHash.java`

```java
package com.fintechdemo.identity.domain.model;

import java.util.Objects;

/** An encoded password hash (e.g. "$argon2id$v=19$m=19456,t=2,p=1$..."). Never a raw password. */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/PasswordPolicy.java`

```java
package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;

/**
 * Password rules from NIST SP 800-63B (rev. 4): length only, no composition rules.
 * 8 is NIST's minimum when the password is one of two factors: L05 binds a trusted device as the second.
 * NIST also requires a blocklist check of common/breached passwords: planned for the L38 security review.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void check(String rawPassword) {
        // Count characters (code points), not Java chars: an emoji is one character, two chars.
        int length = rawPassword == null ? 0 : rawPassword.codePointCount(0, rawPassword.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new InvalidCustomerDetailsException(
                    "Password must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters long");
        }
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/model/Customer.java`

```java
package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;
import java.time.Instant;
import java.util.Objects;

/** The customer aggregate: who they are, how they log in, and their KYC tier. Plain Java, no framework. */
public final class Customer {

    private static final int MAX_NAME_LENGTH = 100;

    private final CustomerId id;
    private final PhoneNumber phoneNumber;
    private final EmailAddress email;
    private final String firstName;
    private final String lastName;
    private final PasswordHash passwordHash;
    private final KycTier kycTier;
    private final Instant createdAt;

    private Customer(CustomerId id, PhoneNumber phoneNumber, EmailAddress email, String firstName,
            String lastName, PasswordHash passwordHash, KycTier kycTier, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
        this.email = Objects.requireNonNull(email, "email");
        this.firstName = requireName(firstName, "First name");
        this.lastName = requireName(lastName, "Last name");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.kycTier = Objects.requireNonNull(kycTier, "kycTier");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A brand-new customer: new ID, lowest KYC tier. */
    public static Customer register(PhoneNumber phoneNumber, EmailAddress email, String firstName,
            String lastName, PasswordHash passwordHash, Instant now) {
        return new Customer(CustomerId.newId(), phoneNumber, email, firstName, lastName,
                passwordHash, KycTier.TIER_1, now);
    }

    /** Rebuilds a customer that already exists in storage. */
    public static Customer restore(CustomerId id, PhoneNumber phoneNumber, EmailAddress email, String firstName,
            String lastName, PasswordHash passwordHash, KycTier kycTier, Instant createdAt) {
        return new Customer(id, phoneNumber, email, firstName, lastName, passwordHash, kycTier, createdAt);
    }

    private static String requireName(String name, String label) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidCustomerDetailsException(label + " must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public CustomerId id() {
        return id;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public EmailAddress email() {
        return email;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public KycTier kycTier() {
        return kycTier;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/exception/InvalidCustomerDetailsException.java`

```java
package com.fintechdemo.identity.domain.exception;

/** A customer detail breaks a domain rule (phone format, email shape, password length, name). */
public final class InvalidCustomerDetailsException extends RuntimeException {

    public InvalidCustomerDetailsException(String message) {
        super(message);
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/domain/exception/CustomerAlreadyExistsException.java`

```java
package com.fintechdemo.identity.domain.exception;

/** The phone number or email is already registered. We don't say which: that would help enumeration. */
public final class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException() {
        super("A customer with this phone number or email already exists.");
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/RegisterCustomerCommand.java`

```java
package com.fintechdemo.identity.application.port.in;

/** Input to registration, in raw form. The domain validates it. */
public record RegisterCustomerCommand(String phoneNumber, String email, String firstName, String lastName,
        String password) {

    // Records print every field by default: never let a password (or PII) reach a log line.
    @Override
    public String toString() {
        return "RegisterCustomerCommand[phoneNumber=***, email=***, password=***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/in/RegisterCustomerUseCase.java`

```java
package com.fintechdemo.identity.application.port.in;

import com.fintechdemo.identity.domain.model.Customer;

/** Inbound port: what the outside world (REST today) may ask the application to do. */
public interface RegisterCustomerUseCase {

    Customer register(RegisterCustomerCommand command);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/out/CustomerPort.java`

```java
package com.fintechdemo.identity.application.port.out;

import com.fintechdemo.identity.domain.model.Customer;

/** Outbound port: where customers are stored. The application never knows it is JPA or PostgreSQL. */
public interface CustomerPort {

    /** Stores a new customer. Throws CustomerAlreadyExistsException if the phone or email is taken. */
    void add(Customer customer);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/port/out/PasswordHasherPort.java`

```java
package com.fintechdemo.identity.application.port.out;

import com.fintechdemo.identity.domain.model.PasswordHash;

/** Outbound port: one-way password hashing. The algorithm is an adapter detail. */
public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/application/service/RegisterCustomerService.java`

```java
package com.fintechdemo.identity.application.service;

import com.fintechdemo.identity.application.port.in.RegisterCustomerCommand;
import com.fintechdemo.identity.application.port.in.RegisterCustomerUseCase;
import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.application.port.out.PasswordHasherPort;
import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.EmailAddress;
import com.fintechdemo.identity.domain.model.PasswordHash;
import com.fintechdemo.identity.domain.model.PasswordPolicy;
import com.fintechdemo.identity.domain.model.PhoneNumber;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Registers a customer. Deliberately NOT @Transactional: hashing takes tens of milliseconds of CPU,
 * and a transaction here would hold a DB connection the whole time. Only the INSERT needs one.
 */
@Service
public class RegisterCustomerService implements RegisterCustomerUseCase {

    private final CustomerPort customers;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public RegisterCustomerService(CustomerPort customers, PasswordHasherPort passwordHasher, Clock clock) {
        this.customers = customers;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    public Customer register(RegisterCustomerCommand command) {
        PhoneNumber phoneNumber = new PhoneNumber(command.phoneNumber());
        EmailAddress email = new EmailAddress(command.email());
        PasswordPolicy.check(command.password());

        PasswordHash passwordHash = passwordHasher.hash(command.password());
        Customer customer = Customer.register(phoneNumber, email, command.firstName(), command.lastName(),
                passwordHash, clock.instant());

        customers.add(customer);
        return customer;
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/RegisterCustomerRequest.java`

```java
package com.fintechdemo.identity.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * The JSON body of POST /customers. The edge only checks presence; the domain owns the real rules,
 * so they live in one place.
 */
public record RegisterCustomerRequest(
        @NotBlank String phoneNumber,
        @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String password) {

    @Override
    public String toString() {
        return "RegisterCustomerRequest[phoneNumber=***, email=***, password=***]";
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/CustomerResponse.java`

```java
package com.fintechdemo.identity.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

/** What the API returns about a customer. Never the password hash; the phone number is masked. */
public record CustomerResponse(UUID customerId, String phoneNumber, String email, String firstName,
        String lastName, String kycTier, Instant createdAt) {
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/CustomerRestMapper.java`

```java
package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.application.port.in.RegisterCustomerCommand;
import com.fintechdemo.identity.domain.model.Customer;

/** Converts between the REST DTOs and the application's command / domain model. DTOs never enter the domain. */
final class CustomerRestMapper {

    private CustomerRestMapper() {
    }

    static RegisterCustomerCommand toCommand(RegisterCustomerRequest request) {
        return new RegisterCustomerCommand(request.phoneNumber(), request.email(), request.firstName(),
                request.lastName(), request.password());
    }

    static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.id().value(),
                customer.phoneNumber().masked(),
                customer.email().value(),
                customer.firstName(),
                customer.lastName(),
                customer.kycTier().name(),
                customer.createdAt());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/CustomerController.java`

```java
package com.fintechdemo.identity.adapter.in.rest;

import com.fintechdemo.identity.application.port.in.RegisterCustomerUseCase;
import com.fintechdemo.identity.domain.model.Customer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final RegisterCustomerUseCase registerCustomer;

    public CustomerController(RegisterCustomerUseCase registerCustomer) {
        this.registerCustomer = registerCustomer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer = registerCustomer.register(CustomerRestMapper.toCommand(request));
        return CustomerRestMapper.toResponse(customer);
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/in/rest/IdentityExceptionHandler.java`

```java
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
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/CustomerJpaEntity.java`

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

/**
 * The customers table row. Lives only inside the persistence adapter; the domain never sees it.
 * Persistable tells Spring Data the row is new, so save() runs one INSERT instead of SELECT + INSERT
 * (our IDs are generated before saving, so Spring cannot guess "new" from a null ID).
 */
@Entity
@Table(name = "customers")
public class CustomerJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "kyc_tier", nullable = false, length = 16)
    private String kycTier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean newRow = true;

    protected CustomerJpaEntity() {
        // for JPA
    }

    CustomerJpaEntity(UUID id, String phoneNumber, String email, String firstName, String lastName,
            String passwordHash, String kycTier, Instant createdAt) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.kycTier = kycTier;
        this.createdAt = createdAt;
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

    String getPhoneNumber() {
        return phoneNumber;
    }

    String getEmail() {
        return email;
    }

    String getFirstName() {
        return firstName;
    }

    String getLastName() {
        return lastName;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getKycTier() {
        return kycTier;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/CustomerJpaRepository.java`

```java
package com.fintechdemo.identity.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation at startup. Login (L04 Part B) adds findByPhoneNumber here. */
interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/CustomerPersistenceMapper.java`

```java
package com.fintechdemo.identity.adapter.out.persistence;

import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.CustomerId;
import com.fintechdemo.identity.domain.model.EmailAddress;
import com.fintechdemo.identity.domain.model.KycTier;
import com.fintechdemo.identity.domain.model.PasswordHash;
import com.fintechdemo.identity.domain.model.PhoneNumber;

/** Converts between the domain Customer and the JPA row. */
final class CustomerPersistenceMapper {

    private CustomerPersistenceMapper() {
    }

    static CustomerJpaEntity toEntity(Customer customer) {
        return new CustomerJpaEntity(
                customer.id().value(),
                customer.phoneNumber().value(),
                customer.email().value(),
                customer.firstName(),
                customer.lastName(),
                customer.passwordHash().value(),
                customer.kycTier().name(),
                customer.createdAt());
    }

    static Customer toDomain(CustomerJpaEntity entity) {
        return Customer.restore(
                new CustomerId(entity.getId()),
                new PhoneNumber(entity.getPhoneNumber()),
                new EmailAddress(entity.getEmail()),
                entity.getFirstName(),
                entity.getLastName(),
                new PasswordHash(entity.getPasswordHash()),
                KycTier.valueOf(entity.getKycTier()),
                entity.getCreatedAt());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/persistence/JpaCustomerAdapter.java`

```java
package com.fintechdemo.identity.adapter.out.persistence;

import com.fintechdemo.identity.application.port.out.CustomerPort;
import com.fintechdemo.identity.domain.exception.CustomerAlreadyExistsException;
import com.fintechdemo.identity.domain.model.Customer;
import java.sql.SQLException;
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

    private static boolean isUniqueViolation(DataIntegrityViolationException violation) {
        return violation.getMostSpecificCause() instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/adapter/out/security/Argon2PasswordHasherAdapter.java`

```java
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
```

### `services/identity-service/src/main/java/com/fintechdemo/identity/config/ClockConfig.java`

```java
package com.fintechdemo.identity.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One injectable clock in UTC. Code asks the Clock for "now" instead of calling Instant.now() directly. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

### `services/identity-service/src/main/resources/application.yml`

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
```

### `services/identity-service/src/main/resources/db/migration/V1__create_customers.sql`

```sql
-- Runs as identity_owner (Flyway). The service itself runs as identity_app.

CREATE TABLE customers (
    id            UUID         PRIMARY KEY,
    phone_number  VARCHAR(16)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    kyc_tier      VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_customers_phone_number UNIQUE (phone_number),
    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_kyc_tier CHECK (kyc_tier IN ('TIER_1', 'TIER_2', 'TIER_3')),
    CONSTRAINT ck_customers_email_lower CHECK (email = lower(email))
);

-- Least privilege: the app may read and write rows, never change the schema or delete customers.
GRANT SELECT, INSERT, UPDATE ON customers TO identity_app;
```

## 5. Why

- **Argon2id, OWASP minimum (19 MiB, t=2, p=1)**: memory-hard, so GPU cracking is expensive (https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Rejected: BCrypt (OWASP lists it for legacy systems; it truncates input at 72 bytes). The hash stores its own parameters, so we can raise them later.
- **The UNIQUE constraint decides, not a SELECT first**: the 5-way race gave exactly one 201. The first version caught `DuplicateKeyException`; verification showed JPA actually throws `DataIntegrityViolationException`, so we check PostgreSQL's `23505` code. That is why we verify.
- **No `@Transactional` around hashing**: Argon2 is CPU work. A transaction would hold a pooled DB connection the whole time; only the INSERT needs one.
- **Two DB roles**: Flyway migrates as `identity_owner`; the app runs as `identity_app` with SELECT/INSERT/UPDATE only. A SQL-injection bug cannot DROP or DELETE.
- **Password rules from NIST SP 800-63B rev. 4**: length 8–128, no composition rules (https://pages.nist.gov/800-63-4/sp800-63b.html). 8 is the minimum because L05 adds the trusted device as the second factor. The NIST blocklist check is scheduled for L38.
- **Records hide secrets in `toString()`**: the command and the request mask password, phone and email, so a stray `log.info(request)` leaks nothing.

## 6. Run (after Docker works)

```powershell
docker compose up -d
mvn -q install
cd services/identity-service
java -jar target/identity-service-0.1.0-SNAPSHOT.jar
```

Expected: `Successfully applied 1 migration to schema "public"`, then `Started IdentityServiceApplication in ~21 seconds`. If the `observability` profile is not running, OpenTelemetry logs `Failed to export spans ... localhost:4318`; that is harmless.

In **Git Bash** (the same commands work on the Mac):

```bash
curl -s -H 'Content-Type: application/json' -H 'X-Correlation-Id: l04a-001' \
  -d '{"phoneNumber":"+2348031234567","email":"Tolu.Ade@Example.com","firstName":"Tolu","lastName":"Ade","password":"correct horse battery"}' \
  http://localhost:8081/customers
```

✅ Verified responses (scratchpad, PostgreSQL 18.6):

```
201 {"customerId":"ff5fbb1c-…","phoneNumber":"+234*******567","email":"tolu.ade@example.com","firstName":"Tolu","lastName":"Ade","kycTier":"TIER_1","createdAt":"2026-10-17T11:39:53.594885300Z"}
409 same phone              → {"detail":"A customer with this phone number or email already exists.",…,"type":"https://fintechdemo.example/problems/customer-already-exists",…}
409 TOLU.ADE@example.com    → same 409 (email is lower-cased)
400 08031234567             → {"detail":"Phone number must be in E.164 format, e.g. +2348031234567",…,"type":".../invalid-customer-details"}
400 password "short"        → {"detail":"Password must be 8 to 128 characters long",…}
400 missing fields          → {"type":".../validation-failed","errors":[{"field":"lastName","message":"must not be blank"},…]}
timing (warm, incl. Argon2) → 0.071–0.080 s
```

Look in the database (the password is your `SERVICE_DB_PASSWORD`):

```bash
docker exec -e PGPASSWORD=change-me-service fintechdemo-postgres psql -h localhost -U identity_app -d identity \
  -c "SELECT phone_number, kyc_tier, left(password_hash, 40) FROM customers"
```

✅ Verified: `+2348031234567 | TIER_1 | $argon2id$v=19$m=19456,t=2,p=1$m/DJhoV6C…`

## 7. Break it

**Race:** fire 5 identical registrations at the same moment.

```bash
for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{http_code} ' -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+2348077777777","email":"race@example.com","firstName":"R","lastName":"C","password":"correct horse battery"}' \
  http://localhost:8081/customers & done; wait
```

✅ Verified: `201 409 409 409 409`.

**Least privilege:** try to destroy data as the app's own DB user.

```bash
docker exec -e PGPASSWORD=change-me-service fintechdemo-postgres psql -h localhost -U identity_app -d identity -c "DROP TABLE customers"
```

✅ Verified (same SQL via JDBC): `DELETE` → `permission denied for table customers`, `DROP`/`ALTER` → `must be owner of table customers`. Connecting `identity_app` to the `ledger` database → `FATAL: permission denied for database "ledger"`.

## 8. Interview line

"Registration hashes the password with Argon2id at OWASP's parameters, outside any DB transaction, and lets a UNIQUE constraint settle duplicate races atomically: five parallel sign-ups give exactly one 201. The service runs as a data-only Postgres role, and Flyway uses a separate owner role, so even an injection bug can't drop or delete a table. The domain is plain Java behind ports, so swapping JPA or the hashing algorithm touches only one adapter."

## 9. Checkpoint

1. Fix the L03 review items (the build is red until `Problems.java` is fixed).
2. Enable Virtual Machine Platform (`wsl.exe --install --no-distribution` in an Administrator PowerShell), restart, then start Docker Desktop.
3. Type the 30 files. Run `mvn -q install` (it must succeed even before Docker works).
4. Once Docker works: run the service, the curls, and Break it.
5. Commit: `git add .` then `git commit -m "L03 fixes + L04A identity-service registration"`.
6. Type **NEXT** → L04 Part B: login, JWT access tokens, rotating refresh tokens with reuse detection, Spring Security.

## My notes

(learner: add your own notes here)
