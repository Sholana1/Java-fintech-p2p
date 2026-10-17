# L03 — Error model, validation, problem+json (`platform-web`)

Presented: 2026-10-17 · Status: learner typing · Verified in Claude's scratchpad: 7/7 checks green (scratchpad-only tests, per T1)

## Review (state at start of L03)

- ✅ `InvalidAmountException.java:6`: `;` added; `mvn verify` is green. The fix is not committed yet.
- ❌ `platform/platform-money/pom.xml:1`: blank line before `<?xml`, plus unused junit/assertj test deps → replace with the Appendix file.
- ❌ `platform/platform-observability/pom.xml`: unused webmvc/webmvc-test test deps → replace with the Appendix file.
- ❌ Still open: ADR-0002, Maven wrapper, GitHub remote + push.
- ❌ Docker: still blocked. **L04 needs Postgres**, so run the admin `dism` command and restart before L04.

## 1. Story

Tolu types ₦10.505 and her app shows "Error 500". Support asks for details; she has none.
Meanwhile the app team parses three different error shapes from three services.

## 2. Goal

Every service answers every error with one RFC 9457 `application/problem+json` shape, which includes the correlationId and never leaks internals.

## 3. Files

```
fintechdemo/
├── pom.xml                                                        ← CHANGED
└── platform/
    ├── platform-money/                                            (unchanged)
    ├── platform-observability/                                    (unchanged)
    └── platform-web/                                              ← NEW module
        ├── pom.xml                                                ← NEW
        └── src/
            └── main/
                ├── java/
                │   └── com/fintechdemo/platform/web/
                │       ├── ErrorHandlingAutoConfiguration.java     ← NEW
                │       ├── FieldViolation.java                     ← NEW
                │       ├── GlobalExceptionHandler.java             ← NEW
                │       └── Problems.java                           ← NEW
                └── resources/
                    └── META-INF/
                        └── spring/
                            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← NEW
```

| # | File | New / changed | Purpose |
| --- | --- | --- | --- |
| 1 | `pom.xml` | changed | Adds the platform-web module and its managed version |
| 2 | `platform/platform-web/pom.xml` | new | Web MVC + validation, plus our money and observability modules |
| 3 | `Problems.java` | new | One factory for every problem response |
| 4 | `FieldViolation.java` | new | One invalid field (never echoes the value) |
| 5 | `GlobalExceptionHandler.java` | new | Any exception becomes problem+json |
| 6 | `ErrorHandlingAutoConfiguration.java` | new | Every service gets it automatically |
| 7 | `…AutoConfiguration.imports` | new | Registers the auto-configuration |

## 4. Code

### `pom.xml` (root, CHANGED: whole file)

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
    </modules>

    <properties>
        <java.version>25</java.version>
        <required.java.range>[25,26)</required.java.range>
        <required.maven.range>[3.9.9,)</required.maven.range>
    </properties>

    <!-- Our own modules: services declare them without a version. -->
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

### `platform/platform-web/pom.xml`

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

    <artifactId>platform-web</artifactId>
    <name>platform-web</name>
    <description>Shared HTTP error model (RFC 9457 problem+json) and validation. No business logic.</description>

    <dependencies>
        <dependency>
            <groupId>com.fintechdemo</groupId>
            <artifactId>platform-money</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fintechdemo</groupId>
            <artifactId>platform-observability</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <!-- Bean Validation (@NotBlank, @Positive ...) on request DTOs. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
    </dependencies>
</project>
```

### `platform/platform-web/src/main/java/com/fintechdemo/platform/web/Problems.java`

```java
package com.fintechdemo.platform.web;

import com.fintechdemo.platform.observability.CorrelationIdFilter;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 problem details the same way in every service.
 * "type" is the stable identifier clients switch on; "correlationId" lets support find the logs.
 */
public final class Problems {

    // .example is reserved (RFC 2606): nobody can own it. RFC 9457 allows a non-resolvable type URI.
    private static final String TYPE_BASE = "https://fintechdemo.example/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return withCorrelationId(problem);
    }

    /** Adds the current request's correlation ID (set by CorrelationIdFilter), if there is one. */
    public static ProblemDetail withCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
```

### `platform/platform-web/src/main/java/com/fintechdemo/platform/web/FieldViolation.java`

```java
package com.fintechdemo.platform.web;

/** One invalid request field. The rejected value is never echoed back: it could be a PIN or a password. */
public record FieldViolation(String field, String message) {
}
```

### `platform/platform-web/src/main/java/com/fintechdemo/platform/web/GlobalExceptionHandler.java`

`@RestControllerAdvice` is a class whose `@ExceptionHandler` methods catch exceptions thrown by any controller. `ResponseEntityExceptionHandler` is Spring's base class that already turns Spring's own errors (malformed JSON, 404, 405…) into problem details.

```java
package com.fintechdemo.platform.web;

import com.fintechdemo.platform.money.CurrencyMismatchException;
import com.fintechdemo.platform.money.InvalidAmountException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The last line of error handling in every service: turns any exception into problem+json.
 * Lowest precedence, so a service's own @RestControllerAdvice for its domain exceptions runs first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidAmountException.class)
    ProblemDetail handleInvalidAmount(InvalidAmountException ex) {
        return Problems.of(HttpStatus.BAD_REQUEST, "invalid-amount", "Invalid amount", ex.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "currency-mismatch", "Currency mismatch", ex.getMessage());
    }

    // Never leak the exception message or stack trace: it can contain SQL, hostnames or secrets.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "Something went wrong on our side. Quote the correlationId when you contact support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
                "One or more fields are invalid.");
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Spring's built-in handlers (malformed JSON, 404, 405 ...) also get the correlationId.
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.withCorrelationId(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
```

### `platform/platform-web/src/main/java/com/fintechdemo/platform/web/ErrorHandlingAutoConfiguration.java`

```java
package com.fintechdemo.platform.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/** Gives every servlet service the same problem+json error responses, just by depending on platform-web. */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
```

### `platform/platform-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```text
com.fintechdemo.platform.web.ErrorHandlingAutoConfiguration
```

## 5. Why

- **RFC 9457 problem+json**: one standard shape for every service. Clients switch on `type`, show `detail`, and quote `correlationId`. Rejected: `about:blank` plus a custom `code` field (non-standard).
- **500s say nothing internal.** The message and stack trace (SQL, hosts, secrets) go to the server log only, under the same correlationId.
- **Never echo rejected values**: a validation error on `pin` must not send the PIN back.
- **Lowest precedence**: a service's own advice (insufficient funds → 409) runs first. Verified, even without `@Order` on the service advice.
- **`createResponseEntity` hook**: Spring's built-in errors (malformed JSON, 404) get the correlationId too.
- **`.example` type URIs**: RFC 9457 allows non-resolvable type URIs (https://www.rfc-editor.org/rfc/rfc9457.html), and `.example` is reserved, so we never point at someone else's domain.

## 6. Run

```powershell
mvn verify          # or .\mvnw.cmd verify once the wrapper exists
```

Expected: `platform-money`, `platform-observability` and `platform-web` all `SUCCESS`, then `BUILD SUCCESS`. (Not yet run on your repo: you haven't typed L03.)

Real responses from Claude's scratchpad check (✅ verified, Spring Boot 4.1.1, `Content-Type: application/problem+json`):

```json
{"detail":"One or more fields are invalid.","instance":"/transfers","status":400,"title":"Validation failed","type":"https://fintechdemo.example/problems/validation-failed","correlationId":"tolu-0001","errors":[{"field":"amount","message":"must not be null"},{"field":"recipient","message":"must not be blank"}]}
{"detail":"10.505 is not an exact NGN amount (max 2 decimal places)","instance":"/transfers","status":400,"title":"Invalid amount","type":"https://fintechdemo.example/problems/invalid-amount","correlationId":"tolu-0002"}
{"detail":"Something went wrong on our side. Quote the correlationId when you contact support.","instance":"/boom","status":500,"title":"Internal error","type":"https://fintechdemo.example/problems/internal-error","correlationId":"tolu-0003"}
{"detail":"Failed to read request","instance":"/transfers","status":400,"title":"Bad Request","correlationId":"tolu-0004"}
{"detail":"No static resource nope.","instance":"/nope","status":404,"title":"Not Found","correlationId":"tolu-0005"}
```

For the 500, the exception said `password=secret`. The client body above doesn't contain it. The server log line does: `"level":"ERROR" … "message":"Unhandled exception","correlationId":"tolu-0003","error":{"type":"java.lang.IllegalStateException", … "stack_trace": …}`.

## 7. Break it

No service runs yet. In L04 you'll curl `identity-service` with a bad body and a bad amount and see these exact shapes.

## 8. Interview line

"Every service returns RFC 9457 problem+json from one shared handler. Clients switch on the type URI, and every error carries the correlationId so support can jump straight to the logs. Unexpected errors return a generic 500 while the full stack trace stays in the server log, and we never echo rejected values, because one of them could be a PIN."

## 9. Checkpoint

1. Fix the review items (POMs from the Appendix; ADR-0002; wrapper).
2. **Before L04: enable Virtual Machine Platform and restart** (L04 needs Postgres in Docker).
3. Type the 7 files, then run `mvn verify`.
4. Commit and push:
   ```powershell
   git add .
   git commit -m "L02 fix + L03 platform-web problem+json error model"
   ```
5. Type **NEXT** → L04 (identity-service: register, login, password hashing, JWT; split into Part A and Part B).

## Appendix: fixed POMs (from the L02 review)

### `platform/platform-money/pom.xml`

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

    <artifactId>platform-money</artifactId>
    <name>platform-money</name>
    <description>Money and Currency value objects. Plain Java: no Spring, no dependencies.</description>
</project>
```

### `platform/platform-observability/pom.xml`

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

    <artifactId>platform-observability</artifactId>
    <name>platform-observability</name>
    <description>Shared logging, tracing and metrics setup for every service. No business logic.</description>

    <dependencies>
        <!-- Health checks and metrics endpoints. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <!-- Traces and metrics exported over OTLP (OpenTelemetry protocol). -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-opentelemetry</artifactId>
        </dependency>

        <!-- Optional: used only when the service is a servlet web app; never forced on a service. -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

## My notes

(learner: add your own notes here)
