# L01 Part A — Monorepo, parent POM, local infrastructure, ADRs

Presented: 2026-10-17 · Status: learner typing · Part B: JSON logs, correlation ID, OpenTelemetry

## 1. Story

Friday, 6 pm, payday. Tolu's ₦10,000 transfer works on your laptop, but CI fails with "unsupported class file version".
On your new Mac, `./mvnw` says "permission denied". Then an auditor asks: "Can the identity service read the ledger database?"
Nobody knows. At a bank, "works on my machine" and "probably not" are both incidents.
Today we build ground that is identical everywhere and locked down by default.

## 2. Goal

One command proves the build environment is correct. One command starts identical local infrastructure (Postgres with a locked-down database per service, Redis, Kafka, Grafana), on Windows now and on the Mac later.

## 3. Files

| # | Path | New / changed | Purpose |
| --- | --- | --- | --- |
| 1 | `.gitattributes` | new | Store text files with LF, so Windows and Mac see identical files |
| 2 | `.gitignore` | new | Keep build output, IDE files and secrets out of Git |
| 3 | `.editorconfig` | new | Editors use UTF-8, LF and the same indents |
| 4 | `.env.example` | new | Template for local secrets (`.env` itself is never committed) |
| 5 | `pom.xml` | new | Parent POM: Spring Boot version anchor, Java 25, build guards |
| 6 | `docker-compose.yml` | new | Postgres, Redis, Kafka and Grafana LGTM for local development |
| 7 | `infrastructure/postgres/init/01-create-service-databases.sql` | new | One database plus owner and app roles per service |
| 8 | `README.md` | new | How to start the project |
| 9 | `docs/adr/0001-tech-stack-versions.md` | new | Verified versions, with sources |
| 10 | `docs/adr/0002-service-level-objectives.md` | new | SLO targets and latency budget |
| – | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | generated | Created by a command in Run. Don't type them |

## 4. Code

**`.gitattributes`**: tells Git how to store line endings. Windows editors write CRLF, macOS and Linux write LF. A shell script with CRLF breaks inside a Linux container.

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

**`.gitignore`**

```gitignore
# Build output
target/
*.class

# Maven wrapper runs in only-script mode: no jar is committed
.mvn/wrapper/maven-wrapper.jar

# IDE and editor files
.idea/
*.iml
.vscode/
*.code-workspace

# Operating system files
.DS_Store
Thumbs.db

# Logs and JVM crash dumps
*.log
hs_err_pid*
replay_pid*

# Secrets - never commit
.env
*.pem
*.key
*.p12
*.jks
*service-account*.json
```

**`.editorconfig`**: a standard file most editors read. Antigravity, like VS Code, may need the "EditorConfig" extension.

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.{yml,yaml,json}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false

[*.{cmd,bat,ps1}]
end_of_line = crlf
```

**`.env.example`**: Docker Compose reads a file named `.env` automatically. We commit only this template.

```dotenv
# Copy this file to .env and change every value. .env is git-ignored: never commit it.
# Local sandbox values only - no real credentials, ever.

# Which optional infrastructure to start: observability, messaging (comma-separated).
COMPOSE_PROFILES=observability

POSTGRES_ADMIN_USER=fintech_admin
POSTGRES_ADMIN_PASSWORD=change-me-admin
SERVICE_DB_PASSWORD=change-me-service
REDIS_PASSWORD=change-me-redis
```

**`pom.xml`**: a *parent POM* is the file every module inherits from. Ours inherits from Spring Boot's parent, which brings a *BOM* (bill of materials: a list of library versions tested together). The *Maven Enforcer* plugin checks the build environment before anything compiles.

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

    <!-- Modules are added lesson by lesson (platform/platform-money arrives in L02). -->
    <modules>
    </modules>

    <properties>
        <java.version>25</java.version>
        <required.java.range>[25,26)</required.java.range>
        <required.maven.range>[3.9.9,)</required.maven.range>
    </properties>

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

`[25,26)` is Maven range syntax: 25 or higher, below 26.

**`docker-compose.yml`**: a *healthcheck* is a command Docker runs repeatedly. A container is `healthy` only while it passes. *Profiles* make a service opt-in. `$$` stops Compose from expanding a variable, so the container's own shell expands it.

```yaml
# FINTECHDEMO local sandbox infrastructure. Development only - never production.
# Every port binds to 127.0.0.1, so nothing is reachable from your Wi-Fi network.
# Kafka and observability are opt-in profiles: choose them with COMPOSE_PROFILES in .env.
name: fintechdemo

services:
  postgres:
    image: postgres:18.6
    container_name: fintechdemo-postgres
    environment:
      POSTGRES_USER: ${POSTGRES_ADMIN_USER:?Set POSTGRES_ADMIN_USER in .env}
      POSTGRES_PASSWORD: ${POSTGRES_ADMIN_PASSWORD:?Set POSTGRES_ADMIN_PASSWORD in .env}
      POSTGRES_DB: postgres
      SERVICE_DB_PASSWORD: ${SERVICE_DB_PASSWORD:?Set SERVICE_DB_PASSWORD in .env}
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql
      - ./infrastructure/postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d postgres"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s
      start_interval: 2s

  redis:
    image: redis:8.10.2
    container_name: fintechdemo-redis
    # Cache only, never a system of record: no disk persistence, evict LRU keys when full.
    command:
      - redis-server
      - --requirepass
      - ${REDIS_PASSWORD:?Set REDIS_PASSWORD in .env}
      - --save
      - ""
      - --appendonly
      - "no"
      - --maxmemory
      - 128mb
      - --maxmemory-policy
      - allkeys-lru
    environment:
      REDISCLI_AUTH: ${REDIS_PASSWORD}
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD-SHELL", "redis-cli ping | grep -q PONG"]
      interval: 10s
      timeout: 3s
      retries: 10
      start_period: 10s
      start_interval: 2s

  kafka:
    image: apache/kafka:4.3.1
    container_name: fintechdemo-kafka
    hostname: kafka
    profiles: [messaging]
    ports:
      - "127.0.0.1:9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      # HOST: apps on your laptop (localhost:9092). DOCKER: other containers (kafka:19092).
      KAFKA_LISTENERS: HOST://:9092,DOCKER://:19092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: HOST://localhost:9092,DOCKER://kafka:19092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: HOST:PLAINTEXT,DOCKER:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: DOCKER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      # Topics are created on purpose by the services, never by a typo.
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_LOG_DIRS: /var/lib/kafka/data
      KAFKA_HEAP_OPTS: -Xms384m -Xmx384m
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD-SHELL", "KAFKA_HEAP_OPTS=-Xmx64m /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
      interval: 30s
      timeout: 15s
      retries: 10
      start_period: 60s
      start_interval: 5s

  otel-lgtm:
    image: grafana/otel-lgtm:0.37.0
    container_name: fintechdemo-otel-lgtm
    profiles: [observability]
    ports:
      - "127.0.0.1:3000:3000"   # Grafana UI
      - "127.0.0.1:4317:4317"   # OTLP over gRPC
      - "127.0.0.1:4318:4318"   # OTLP over HTTP (Spring Boot exports here)

volumes:
  postgres-data:
  kafka-data:
```

**`infrastructure/postgres/init/01-create-service-databases.sql`**: Postgres runs this once, on an empty volume. Three new ideas:
`\getenv` copies an environment variable into a psql variable (psql 15+).
`\gexec` runs each row of a query result as its own SQL statement (needed because `CREATE DATABASE` cannot run inside a transaction block).
`format('%I', ...)` / `format('%L', ...)` quote identifiers and literals safely: the same idea as prepared statements against SQL injection.

```sql
-- Runs ONCE: only when the Postgres volume is empty (the very first `docker compose up`).
-- Each service gets its own database and two login roles:
--   <service>_owner : runs Flyway migrations and owns the tables
--   <service>_app   : what the running service logs in as (data only, no DDL)
-- No role can connect to another service's database.

\set ON_ERROR_STOP on
\getenv service_password SERVICE_DB_PASSWORD

CREATE TEMPORARY TABLE service_database (name text PRIMARY KEY);

INSERT INTO service_database (name) VALUES
    ('identity'),
    ('ledger'),
    ('transfer'),
    ('payment_rail'),
    ('risk'),
    ('notification'),
    ('reconciliation');

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', name || '_owner', :'service_password')
FROM service_database \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', name || '_app', :'service_password')
FROM service_database \gexec

SELECT format('CREATE DATABASE %I OWNER %I', name, name || '_owner')
FROM service_database \gexec

SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', name)
FROM service_database \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', name, name || '_app')
FROM service_database \gexec
```

**`README.md`**

````markdown
# FINTECHDEMO

High-speed P2P payments core for a Nigerian digital bank.
A sandbox and learning platform, **not a licensed bank**. No real customer data, sandbox credentials only.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| JDK | 25 (LTS), Eclipse Temurin | `java -version` |
| Docker Desktop | Compose v2 or newer | `docker compose version` |
| Git | any recent | `git --version` |

Maven comes from the wrapper (`./mvnw`), so everyone builds with the same version.

## First run

```bash
cp .env.example .env          # then change every password in .env
docker compose up -d          # postgres, redis + the profiles in COMPOSE_PROFILES
docker compose ps             # wait until every container is "healthy"
./mvnw validate               # Windows PowerShell: .\mvnw.cmd validate
```

## Local endpoints

| What | Address |
| --- | --- |
| PostgreSQL | `localhost:5432` (one database per service) |
| Redis | `localhost:6379` |
| Kafka (profile `messaging`) | `localhost:9092` |
| Grafana (profile `observability`) | http://localhost:3000 |
| OTLP collector (profile `observability`) | `localhost:4317` gRPC, `localhost:4318` HTTP |

## Where things are

- `docs/PROGRESS.md`: what is done, what is next
- `docs/adr/`: architecture decisions and the reasons for them
- `docs/lessons/`: lesson notes
- `docs/runbooks/`: what to do when something breaks
````

**`docs/adr/0001-tech-stack-versions.md`** and **`docs/adr/0002-service-level-objectives.md`**: an *ADR* (Architecture Decision Record) is a short file explaining one decision and why. Their full text is in the Appendix at the end of this lesson. The verified source copies are in Claude's scratchpad from 2026-10-17.

## 5. Why

- **Boot's parent is the version anchor.** Its BOM sets more than 25 library versions that are tested together. Rejected: picking each library's newest. Flyway 13.10.3 exists, for example, but Boot 4.1.1 is tested with 12.4.0.
- **Enforcer + wrapper.** A wrong JDK fails in 3 seconds with an instruction, not after an hour with a cryptic error. The wrapper pins Maven 3.10.0 for Windows, Mac and CI.
- **Owner/app roles plus `REVOKE ... FROM PUBLIC`.** By default Postgres lets *every* role connect to *every* database. Now "each service owns its database" is enforced by Postgres, not by good intentions. Rejected: one superuser for everything, where one SQL-injection bug in any service exposes the ledger. In L08 the ledger app role gets only SELECT and INSERT on journal tables, so the database itself enforces append-only.
- **Redis is a pure cache.** No persistence, LRU eviction. If Redis is wiped, we lose speed, never money.
- **Kafka in KRaft mode with auto-create off.** Kafka 4 has no ZooKeeper. With auto-create on, a typo like `transfer.complted` silently creates a new topic and swallows events.
- **127.0.0.1 ports, `.env`, profiles.** Dev databases can't be reached from café Wi-Fi, secrets never enter Git, and Kafka and Grafana start only when needed (this laptop has 8 GB).

## 6. Run (Windows PowerShell, in the project folder)

**0 · One-time Windows fix (Docker can't start without it).** Docker's log on 2026-10-17 said: `Virtual Machine Platform not enabled`. Your BIOS virtualization is ON, so only the Windows feature is missing. Open PowerShell **as Administrator**:

```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```

Restart Windows, then start Docker Desktop. Source: https://learn.microsoft.com/en-us/windows/wsl/install-manual (Step 3).

**1 · Generate the Maven wrapper** (PowerShell splits `-Dname=1.2.3` at the dots, so keep the quotes):

```powershell
mvn -N wrapper:wrapper "-Dmaven=3.10.0"
```

Expected: `mvnw`, `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties` appear. The properties file contains `distributionType=only-script` and `apache-maven-3.10.0`. ✅ verified by Claude

**2 · Check the build environment**

```powershell
.\mvnw.cmd validate
```

Expected (✅ verified):

```
[INFO] --- enforcer:3.6.3:enforce (enforce-build-environment) @ fintechdemo-parent ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion passed
[INFO] Rule 2: org.apache.maven.enforcer.rules.BanDuplicatePomDependencyVersions passed
[INFO] BUILD SUCCESS
```

Prove the guard works: `.\mvnw.cmd validate "-Drequired.java.range=[26,27)"` → `BUILD FAILURE` with `FINTECHDEMO builds on JDK 25 (LTS). macOS: brew install --cask temurin@25`. ✅ verified

**3 · Secrets file**

```powershell
Copy-Item .env.example .env
notepad .env        # change the three passwords
```

Without `.env`, `docker compose` fails with `required variable POSTGRES_ADMIN_PASSWORD is missing a value: Set POSTGRES_ADMIN_PASSWORD in .env`. ✅ verified

**4 · Start the infrastructure** (after step 0)

```powershell
docker compose up -d
docker compose ps
```

Expected: `fintechdemo-postgres` and `fintechdemo-redis` `(healthy)`, `fintechdemo-otel-lgtm` `Up`. ⚠️ Not verified: Docker could not start on this laptop. The compose file passed `docker compose config`.

**5 · See the databases**

```powershell
docker exec fintechdemo-postgres psql -U fintech_admin -d postgres -c "\l"
```

Expected: `identity`, `ledger`, `transfer`, `payment_rail`, `risk`, `notification`, `reconciliation`, each owned by `<name>_owner`. ⚠️ Not verified

**6 · Kafka, once, then stop it to save RAM**

```powershell
docker compose up -d kafka
docker compose ps kafka                     # wait for (healthy), about 30-60 s
docker exec fintechdemo-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose stop kafka
```

Expected: the topic list is empty (auto-create is off). ⚠️ Not verified

**7 · Grafana**: open http://localhost:3000 → the Grafana home page. ⚠️ Not verified

## 7. Break it

Try to make the identity service's runtime role read the ledger database:

```powershell
docker exec -e PGPASSWORD=<your SERVICE_DB_PASSWORD> fintechdemo-postgres psql -h localhost -U identity_app -d ledger -c "select 1"
```

Expected: `FATAL: permission denied for database "ledger"` / `DETAIL: User does not have CONNECT privilege.`
Then the allowed case: the same command with `-d identity -c "select current_user"` prints `identity_app`. ⚠️ Not verified (needs Docker)

## 8. Interview line

"Every service owns its database, and we enforce that with Postgres privileges, not convention. Each service has an owner role for migrations and a runtime role with data access only, and PUBLIC has no CONNECT. Versions are anchored to the Spring Boot BOM and the build refuses the wrong JDK, so Windows, Mac and CI produce the same artifact."

## 9. Checkpoint

1. Do step 0 (admin command + restart) so Docker works.
2. Type the 10 files. Run steps 1–7 and Break it.
3. Answer Q1 (throughput target) and Q2 (architecture changes) from `docs/PROGRESS.md`.
4. Commit:
   ```powershell
   git add .
   git add --chmod=+x mvnw
   git status            # .env must NOT be listed
   git commit -m "L01A: monorepo skeleton, parent POM, local infrastructure, ADR-0001/0002"
   ```
   Warnings like `CRLF will be replaced by LF` are expected and good.
5. Create a **private** repo at https://github.com/new named `fintechdemo` (no README, no .gitignore), then:
   ```powershell
   git remote add origin https://github.com/Sholana1/fintechdemo.git
   git push -u origin main
   ```
6. Type **NEXT**. Claude reviews your files and runs the ⚠️ checks, then teaches L01 Part B.

## Appendix: ADR files to type

**`docs/adr/0001-tech-stack-versions.md`**

````markdown
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
````

**`docs/adr/0002-service-level-objectives.md`**

````markdown
# ADR-0002: Service level objectives (SLOs)

- Status: Proposed (the throughput target waits for confirmation)
- Date: 2026-10-17

## Context

"Fast" means nothing until it is a number we measure. These are design targets, not promises.
L22 measures them with k6, and we adjust them with data.

## How we measure

- **Server-side latency**: time inside `transfer-service`, from request received to response written.
  Micrometer's `http.server.requests` timer records it with percentile histograms. Client network time is excluded.
- **Load**: k6 scripts in `load-tests/`, run against the local Docker Compose stack.
- **Machine**: every result records the machine it ran on. We never compare numbers from different machines.

## Reference machines

| Name | CPU | RAM |
| --- | --- | --- |
| win-laptop | Intel Core i5-6300U, 2 cores / 4 threads | 7.9 GB |
| mac | recorded on the first run there | |

## Targets

| Flow | Indicator | Target |
| --- | --- | --- |
| Internal P2P send | p50 server-side latency | ≤ 80 ms |
| Internal P2P send | p99 server-side latency | ≤ 250 ms |
| Internal P2P send | valid requests answered without a 5xx error | ≥ 99.9 % during a load test |
| Interbank send | p99 latency to the accepted (`PENDING`) response | ≤ 300 ms |
| Interbank send | time stuck in `PROCESSING` or `UNKNOWN` | alert after 15 min (our operations target, not a regulatory rule) |
| Debit / credit alert | p95 from ledger commit to push sent | ≤ 5 s |
| Outbox relay | p99 from commit to Kafka publish | ≤ 1 s |

## Throughput (proposal: confirm or change)

| Machine | Sustained for 10 min | Spike for 1 min |
| --- | --- | --- |
| win-laptop | 50 TPS | 100 TPS |
| mac | set after the first k6 run | |

Why this is modest: on a 2-core laptop, every service plus PostgreSQL, Kafka and observability share the same 4 threads.
A production target would be set per instance, on dedicated hardware.

## Invariants (not SLOs: never traded for speed)

- Zero unbalanced journals (total debits = total credits).
- Zero negative balances on non-overdraft accounts.
- Zero double spends, and zero duplicate postings for one idempotency key.

## Latency budget (estimates, replaced by L22 measurements)

| Step | p50 estimate |
| --- | --- |
| Gateway: verify JWT locally, route | 2 ms |
| Claim the idempotency key (one INSERT) | 2 ms |
| Resolve the recipient (Redis hit) | 1 ms |
| Parallel checks, slowest wins: PIN ~35 ms, limits ~3 ms, risk ~8 ms, fee ~1 ms | 35 ms |
| Ledger `PostJournal` over gRPC (one DB transaction plus outbox row) | 8 ms |
| Save the transfer status, write the response | 3 ms |
| **Total** | **≈ 51 ms** (target 80 ms) |

## Known risk

PIN hashing is CPU-heavy on purpose. 50 TPS × ~35 ms of CPU ≈ 1.75 cores busy only hashing PINs: almost half of this laptop.
L05 must measure the real cost and choose the PIN verification design with this budget in mind.

## Rule

If a lesson pushes a hot-path p99 above its target, we fix it before the next hot-path lesson.
````

## My notes

(learner: add your own notes here)
