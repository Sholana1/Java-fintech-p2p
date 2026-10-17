# ADR-0001: Tech stack and versions

- Status: Accepted
- Date: 2026-10-17
- Verified: every version below was checked against the linked official source on 2026-10-17

## Context

We need one set of versions that works together on Windows, macOS and CI.
Picking each library's newest release on its own breaks things. Mixing major versions breaks them faster.

## Decision

1. **JDK 25 (LTS), Eclipse Temurin.** The build refuses any other JDK (Maven Enforcer range `[25,26)`).
2. **Spring Boot 4.1.1 is the version anchor.** Every library Boot manages uses Boot's version. We never override it without a new ADR.
3. Libraries Boot does not manage are pinned once, in the parent POM, by the lesson that first needs them.
4. Docker images use exact tags, never `latest`.
5. Re-verify everything before L39 (CI/CD) and before any upgrade.

### Runtime and build

| Component | Version | Official source |
| --- | --- | --- |
| JDK | 25 LTS (Temurin 25.0.4.1+1) | https://adoptium.net/support/ and https://openjdk.org/projects/jdk/ |
| Maven (via wrapper) | 3.10.0 | https://repo1.maven.org/maven2/org/apache/maven/maven-core/maven-metadata.xml |
| Maven Wrapper | 3.3.4 | https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-wrapper-plugin/maven-metadata.xml |
| Spring Boot | 4.1.1 (supports Java 17 to 26) | https://docs.spring.io/spring-boot/system-requirements.html |
| Spring Cloud | 2025.1.3 "Oakwood" (Boot 4.1.x from 2025.1.2) | https://spring.io/projects/spring-cloud |
| Docker Engine / Compose (Windows laptop) | 29.7.2 / v5.4.0 | installed |

### Managed by Spring Boot 4.1.1 (do not override)

Source: https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom

| Library | Version |
| --- | --- |
| Spring Framework | 7.0.9 |
| Spring Security | 7.1.1 |
| Spring Data (release train) | 2026.0.1 |
| Hibernate ORM | 7.4.5.Final |
| HikariCP | 7.0.2 |
| PostgreSQL JDBC driver | 42.7.13 |
| Flyway | 12.4.0 |
| Spring for Apache Kafka / kafka-clients | 4.1.1 / 4.2.1 |
| Spring gRPC / grpc-java / protobuf-java | 1.1.1 / 1.83.1 / 4.35.1 |
| protobuf-maven-plugin (io.github.ascopes) | 5.1.8 |
| Lettuce (Redis client) | 7.5.2.RELEASE |
| Jackson | 3.1.5 |
| Micrometer / OpenTelemetry SDK | 1.17.1 / 1.62.0 |
| Logback | 1.5.38 |
| JUnit Jupiter / Mockito / AssertJ | 6.0.3 / 5.23.0 / 3.27.7 |
| Testcontainers | 2.0.5 |

Spring Cloud 2025.1.3 brings Spring Cloud Gateway 5.0.3 (https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.1.3/spring-cloud-dependencies-2025.1.3.pom).

### Not managed by Boot (pinned by the lesson that first needs them)

| Library | Latest stable today | First lesson | Source |
| --- | --- | --- | --- |
| Resilience4j (`resilience4j-spring-boot4`) | 2.4.0 | L31 | https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-spring-boot4/maven-metadata.xml |
| ShedLock | 7.10.1 | L26 | https://repo1.maven.org/maven2/net/javacrumbs/shedlock/shedlock-bom/maven-metadata.xml |
| Bouncy Castle (needed by Argon2 in Spring Security) | 1.86 | L04 | https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml |
| k6 (Docker image `grafana/k6`) | 2.3.0 | L22 | https://hub.docker.com/r/grafana/k6 |

### Local infrastructure images

| Image | Tag | Official source |
| --- | --- | --- |
| PostgreSQL | `postgres:18.6` | https://hub.docker.com/_/postgres |
| Redis | `redis:8.10.2` | https://hub.docker.com/_/redis |
| Apache Kafka (KRaft, no ZooKeeper) | `apache/kafka:4.3.1` | https://hub.docker.com/r/apache/kafka |
| Grafana LGTM (OTel Collector, Prometheus, Tempo, Loki, Grafana) | `grafana/otel-lgtm:0.37.0` | https://github.com/grafana/docker-otel-lgtm |

Logging uses Spring Boot's built-in structured JSON logging (https://docs.spring.io/spring-boot/reference/features/logging.html).
Tracing uses `spring-boot-starter-opentelemetry` (https://docs.spring.io/spring-boot/reference/actuator/tracing.html).

## Alternatives rejected

- **Spring Boot 4.2.0-M2**: a milestone, not a stable release.
- **Java 27**: not LTS. Adoptium ends its support in March 2027, and Boot 4.1.1 is only tested up to Java 26.
- **Java 21**: still supported, but 25 is the newest LTS and Boot 4.1.1 supports it.
- **Flyway 13.x**: newer than Boot's managed 12.4.0. Boot's Flyway integration is tested against 12.4.0.
- **logstash-logback-encoder**: Spring Boot already writes JSON logs (ECS, Logstash or GELF). One dependency fewer.
- **Separate Prometheus, Tempo, Loki and Grafana containers**: too heavy for an 8 GB laptop. `grafana/otel-lgtm` bundles them for development. Grafana's README says it is not for production.

## Consequences

- An upgrade means bumping the Boot parent and running every test, not hunting individual versions.
- Every lesson that adds a library records its verified version in this ADR.
