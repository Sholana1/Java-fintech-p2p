# L01 Part B — JSON logs, correlation ID, OpenTelemetry (`platform-observability`)

Presented: 2026-10-17 · Status: learner typing · Verified by Claude in scratchpad: 9/9 tests pass, Break-it reproduces 2 failures

## Review of L01A (done before this lesson, 2026-10-17)

| # | Where | Problem | Fix |
| --- | --- | --- | --- |
| 1 | `.gitattributes` (0 bytes) | The file is empty, so no LF rule exists. Windows `core.autocrlf=true` normalised this commit, but a machine without that setting can commit CRLF, and `mvnw` with CRLF fails on the Mac | Type the content below, then `git add --renormalize .` |
| 2 | `docs/adr/0002-service-level-objectives.md` | Missing | Type it (full text below) |
| 3 | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Not generated, so the Mac would need its own Maven | `mvn -N wrapper:wrapper "-Dmaven=3.10.0"`, then `git add --chmod=+x mvnw` |
| 4 | Git remote | None, so nothing is pushed and the Mac can't clone | Create a private GitHub repo and push |
| 5 | Docker | Step 0 not done (no restart since 2026-10-16) | Admin PowerShell: `dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart`, then restart |

✅ The other 8 files match the verified versions exactly. `.env` is ignored and not committed. The commit contains no CRLF.
Q1 and Q2 were not answered, so Claude's recommendations stand (C5 is used in this lesson). The learner can still change them.

## 1. Story

2:14 pm. Tolu's transfer fails. She calls support and reads out the reference on her screen: `f3c9…`.
That minute, 40,000 log lines came from four services. Without a shared ID on every line, finding her six lines takes an hour.
With it, one search in Grafana takes two seconds. Today every request gets that ID, every log line becomes JSON, and traces start flowing.

## 2. Goal

Any service that adds one dependency gets JSON logs, a correlation ID on every log line of a request, and OpenTelemetry traces and metrics. Nine tests prove it.

## 3. Files

```
fintechdemo/
├── pom.xml                                                          ← CHANGED
└── platform/                                                        ← NEW folder
    └── platform-observability/                                      ← NEW module
        ├── pom.xml                                                  ← NEW
        └── src/
            ├── main/
            │   ├── java/
            │   │   └── com/fintechdemo/platform/observability/
            │   │       ├── CorrelationIdFilter.java                 ← NEW
            │   │       ├── ObservabilityAutoConfiguration.java      ← NEW
            │   │       └── ObservabilityDefaultsPostProcessor.java  ← NEW
            │   └── resources/
            │       └── META-INF/
            │           ├── spring.factories                         ← NEW
            │           └── spring/
            │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← NEW
            └── test/
                └── java/
                    └── com/fintechdemo/platform/observability/
                        ├── CorrelationIdFilterTest.java             ← NEW
                        ├── ObservabilityAutoConfigurationTest.java  ← NEW
                        └── ObservabilityDefaultsPostProcessorTest.java  ← NEW
```

| # | Path | New / changed | Purpose |
| --- | --- | --- | --- |
| 1 | `pom.xml` | changed | Registers the module, and lets services use it without a version |
| 2 | `platform/platform-observability/pom.xml` | new | Module dependencies: actuator, OpenTelemetry, optional servlet |
| 3 | `.../observability/CorrelationIdFilter.java` | new | Gives every request an ID, puts it in the MDC, echoes it back |
| 4 | `.../observability/ObservabilityAutoConfiguration.java` | new | Registers the filter in every servlet service automatically |
| 5 | `.../observability/ObservabilityDefaultsPostProcessor.java` | new | Default JSON logs and OTLP export (services can override) |
| 6 | `.../resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | new | Tells Spring Boot about our auto-configuration |
| 7 | `.../resources/META-INF/spring.factories` | new | Tells Spring Boot about our defaults post-processor |
| 8 | `.../test/.../CorrelationIdFilterTest.java` | new | Unit tests for the filter |
| 9 | `.../test/.../ObservabilityDefaultsPostProcessorTest.java` | new | Defaults are applied, and services can override them |
| 10 | `.../test/.../ObservabilityAutoConfigurationTest.java` | new | End to end: a real request produces a JSON log line with the ID |

(`.../` = `platform/platform-observability/src/main/java/com/fintechdemo/platform/observability/`, or `src/main/resources/` / `src/test/java/...` as shown in the tree.)

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
        <module>platform/platform-observability</module>
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
                <artifactId>platform-observability</artifactId>
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

### `platform/platform-observability/pom.xml`

An **optional** dependency is one we compile against but don't force on users of our library.

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

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### `platform/platform-observability/src/main/java/com/fintechdemo/platform/observability/CorrelationIdFilter.java`

- **MDC** (Mapped Diagnostic Context): a per-thread map; the logger copies every entry into each log line written on that thread.
- **`OncePerRequestFilter`**: Spring's base class for code that wraps every HTTP request exactly once.

```java
package com.fintechdemo.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every HTTP request a correlation ID: reuses a well-formed incoming one, otherwise creates one.
 * The ID sits in the logging MDC for the whole request and goes back to the caller in a response header.
 */
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String incoming) {
        if (incoming != null && WELL_FORMED.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
```

### `platform/platform-observability/src/main/java/com/fintechdemo/platform/observability/ObservabilityAutoConfiguration.java`

**Auto-configuration**: a configuration class that Spring Boot loads automatically from any jar that lists it in an `.imports` file. The service doesn't need to know it exists.

```java
package com.fintechdemo.platform.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Plugs the platform's observability pieces into every servlet-based service.
 * A service gets this just by depending on platform-observability.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ObservabilityAutoConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
```

### `platform/platform-observability/src/main/java/com/fintechdemo/platform/observability/ObservabilityDefaultsPostProcessor.java`

- **`EnvironmentPostProcessor`**: a hook that adds configuration before the application starts.
- **"Default properties"**: Spring's lowest-priority property source, so anything a service sets wins.
- **OTLP**: the protocol traces and metrics use to reach the collector (port 4318).
- **ECS**: the Elastic Common Schema JSON log format.

```java
package com.fintechdemo.platform.observability;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Platform-wide observability defaults: JSON logs, and traces and metrics sent to the local OTLP collector.
 * They have the lowest priority, so a service's application.yml, environment variables or
 * command-line arguments always win.
 */
public class ObservabilityDefaultsPostProcessor implements EnvironmentPostProcessor {

    static final Map<String, Object> DEFAULTS = Map.of(
            "logging.structured.format.console", "ecs",
            "management.tracing.sampling.probability", "1.0",
            "management.opentelemetry.tracing.export.otlp.endpoint", "http://localhost:4318/v1/traces",
            "management.otlp.metrics.export.url", "http://localhost:4318/v1/metrics");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        DefaultPropertiesPropertySource.addOrMerge(DEFAULTS, environment.getPropertySources());
    }
}
```

### `platform/platform-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

The file name is long on purpose: Spring Boot looks for exactly this name.

```text
com.fintechdemo.platform.observability.ObservabilityAutoConfiguration
```

### `platform/platform-observability/src/main/resources/META-INF/spring.factories`

The key is the Spring Boot 4 interface name; the old `org.springframework.boot.env.` name is deprecated.

```properties
org.springframework.boot.EnvironmentPostProcessor=\
com.fintechdemo.platform.observability.ObservabilityDefaultsPostProcessor
```

### `platform/platform-observability/src/test/java/com/fintechdemo/platform/observability/CorrelationIdFilterTest.java`

```java
package com.fintechdemo.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String UUID_FORMAT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfers");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> idDuringRequest = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> idDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

    @Test
    void reusesAWellFormedIncomingId() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "tolu-retry-0001");

        filter.doFilter(request, response, chain);

        assertThat(idDuringRequest.get()).isEqualTo("tolu-retry-0001");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("tolu-retry-0001");
    }

    @Test
    void createsAnIdWhenTheCallerSendsNone() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(idDuringRequest.get()).matches(UUID_FORMAT);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(idDuringRequest.get());
    }

    @Test
    void replacesAnIdThatTriesToForgeALogLine() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "x\n{\"message\":\"refund approved\"}");

        filter.doFilter(request, response, chain);

        assertThat(idDuringRequest.get()).matches(UUID_FORMAT);
    }

    @Test
    void replacesAnOversizedId() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "a".repeat(65));

        filter.doFilter(request, response, chain);

        assertThat(idDuringRequest.get()).matches(UUID_FORMAT);
    }

    @Test
    void removesTheIdFromTheThreadWhenTheRequestEnds() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void removesTheIdEvenWhenTheRequestFails() {
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("ledger unavailable");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
```

### `platform/platform-observability/src/test/java/com/fintechdemo/platform/observability/ObservabilityDefaultsPostProcessorTest.java`

`MockEnvironment` contains only what the test puts in it, so your PC's environment variables can't change the result.

```java
package com.fintechdemo.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityDefaultsPostProcessorTest {

    private final ObservabilityDefaultsPostProcessor postProcessor = new ObservabilityDefaultsPostProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    @Test
    void switchesOnJsonLogsByDefault() {
        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    @Test
    void letsAServiceOverrideADefault() {
        environment.setProperty("management.tracing.sampling.probability", "0.1");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
    }
}
```

### `platform/platform-observability/src/test/java/com/fintechdemo/platform/observability/ObservabilityAutoConfigurationTest.java`

- `@SpringBootTest` starts a real Spring application.
- `MockMvc` sends HTTP requests to it without opening a network port.
- `OutputCaptureExtension` records everything printed to the console, so we can check the log line.

```java
package com.fintechdemo.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ObservabilityAutoConfigurationTest.PingApplication.class)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityAutoConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logsEveryRequestLineAsJsonWithTheCorrelationId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/ping").header(CorrelationIdFilter.HEADER, "tolu-retry-0001"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "tolu-retry-0001"));

        String pingLogLine = output.getOut().lines()
                .filter(line -> line.contains("ping received"))
                .findFirst()
                .orElseThrow();
        assertThat(pingLogLine)
                .startsWith("{")
                .contains("\"correlationId\":\"tolu-retry-0001\"");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @RestController
    static class PingApplication {

        private static final Logger log = LoggerFactory.getLogger(PingApplication.class);

        @GetMapping("/ping")
        String ping() {
            log.info("ping received");
            return "pong";
        }
    }
}
```

## 5. Why

- **Correlation ID ≠ trace ID.** The OpenTelemetry trace ID is for engineers, and production samples traces (for example 10%). The correlation ID is the business reference: always present, returned to the caller, and (from L15) stored on the transfer. Every log line carries both.
- **Validate, don't trust.** An incoming ID must match `[A-Za-z0-9-]{1,64}`, or we create a new one. Rejected: refusing the request (a bad header must not block a payment) and accepting anything (forged log lines, and kilobytes repeated on every line).
- **`finally { MDC.remove(...) }`.** Servers reuse threads. Without it, the next customer's request logs the previous customer's ID, and an investigation reads the wrong person's data.
- **Defaults in one place, with the lowest priority.** Nine services get identical JSON logs and OTLP export from one dependency, and any service can still override in `application.yml` or an env var. Rejected: copying the same lines into nine `application.yml` files, which drift apart.
- **The filter runs first (`HIGHEST_PRECEDENCE`)**, so even the first log line of a request has the ID. The servlet dependencies are optional and the auto-configuration only activates in servlet apps, so a non-web service is never forced to pull in Tomcat.
- **Metrics are pushed over OTLP** to the collector (the LGTM container includes Prometheus), so no `/actuator/prometheus` scraping is needed. 100% trace sampling is a dev default; production overrides it.

## 6. Run

```powershell
.\mvnw.cmd verify
```

The first run downloads about 100 MB of dependencies (2 minutes on Claude's run). Expected (✅ verified in Claude's scratchpad):

```
[INFO] Running com.fintechdemo.platform.observability.CorrelationIdFilterTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.fintechdemo.platform.observability.ObservabilityAutoConfigurationTest
{"@timestamp":"...","log":{"level":"INFO","logger":"...ObservabilityAutoConfigurationTest$PingApplication"},"process":{...},"service":{"node":{}},"message":"ping received","traceId":"cd97d5dd5f9a03eabf206a0efbbe3808","spanId":"ea1102ab2a22bfc8","correlationId":"tolu-retry-0001","ecs":{"version":"8.11"}}
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.fintechdemo.platform.observability.ObservabilityDefaultsPostProcessorTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] fintechdemo-parent ................................. SUCCESS
[INFO] platform-observability ............................. SUCCESS
[INFO] BUILD SUCCESS
```

That one JSON line is the whole lesson: the message, the trace ID and span ID from OpenTelemetry, and our correlation ID.
`"service":{"node":{}}` is empty because the test app has no `spring.application.name`; real services set it.
Harmless warnings you will also see: "Mockito is currently self-attaching…" and "sun.misc.Unsafe…" (from protobuf). They come from libraries running on JDK 25; nothing to fix now.

## 7. Break it

Comment out the cleanup line in `CorrelationIdFilter.java`:

```java
        } finally {
            // MDC.remove(MDC_KEY);
        }
```

```powershell
.\mvnw.cmd -pl platform/platform-observability test "-Dtest=CorrelationIdFilterTest"
```

Expected (✅ verified):

```
[ERROR] Tests run: 6, Failures: 2
[ERROR] CorrelationIdFilterTest.removesTheIdFromTheThreadWhenTheRequestEnds
expected: null
 but was: "b51f13d5-5f46-44ef-9d5a-8abb6e15d09e"
[ERROR] CorrelationIdFilterTest.removesTheIdEvenWhenTheRequestFails
```

That leftover UUID would be printed on the next customer's log lines. Put the line back and run again: 6/6 green.

## 8. Interview line

"Every request gets a correlation ID at the edge. We reuse a well-formed one from the caller or generate a UUID, put it in the MDC so every JSON log line carries it, echo it in the response, and always clear it in a finally block because server threads are reused. It is separate from the OpenTelemetry trace ID: the trace ID is for engineers and can be sampled, while the correlation ID is the business reference support uses. It all ships as a platform starter, so every service gets identical logging and tracing by adding one dependency."

## 9. Checkpoint

1. Fix the L01A review items 1–3, and do item 5 (Docker) when you can restart.
2. Type the 10 files. Run `.\mvnw.cmd verify` and expect 9 tests green. Do Break it, then restore the line.
3. Commit and push:
   ```powershell
   git add .
   git add --renormalize .
   git add --chmod=+x mvnw
   git status            # .env must NOT appear
   git commit -m "L01: fix gitattributes, add Maven wrapper and ADR-0002; platform-observability with JSON logs, correlation ID, OpenTelemetry"
   ```
   Create a **private** repo at https://github.com/new named `fintechdemo` (no README), then:
   ```powershell
   git remote add origin https://github.com/Sholana1/fintechdemo.git
   git push -u origin main
   ```
4. Type **NEXT** → Claude reviews, then teaches **L02: Money value object (kobo, rounding) + tests**.

## Appendix: L01A files to fix

### `.gitattributes` (yours is empty; whole file)

```gitattributes
# Store every text file with LF line endings, so Windows and macOS see identical files.
* text=auto eol=lf

# Windows-only scripts keep CRLF.
*.cmd text eol=crlf
*.bat text eol=crlf
*.ps1 text eol=crlf

# Shell scripts must be LF or they break inside Linux containers.
mvnw text eol=lf
*.sh text eol=lf

# Binary files: never touch them.
*.jar binary
*.png binary
*.jpg binary
*.p12 binary
```

### `docs/adr/0002-service-level-objectives.md`

The full text is in `docs/lessons/L01A-monorepo-and-local-infrastructure.md`, Appendix (unchanged).

## My notes

(learner: add your own notes here)
