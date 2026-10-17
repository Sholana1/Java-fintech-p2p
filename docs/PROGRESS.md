# FINTECHDEMO — Progress tracker

Last updated: 2026-10-17, on the Windows laptop.
Claude updates this file after every step. Legend: ✅ done · 🟡 in progress · ⬜ not started · ⏸ waiting for learner

## Next step

0. ⏸ **Blocker: Docker can't start on the Windows laptop.** Docker's log says `Virtual Machine Platform not enabled`; BIOS virtualization is ON. Fix: in an admin PowerShell, run `dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart`, then restart Windows (source: https://learn.microsoft.com/en-us/windows/wsl/install-manual, Step 3). VirtualBox and Vagrant are installed: if the feature was turned off for VirtualBox, VirtualBox 7 can run alongside it but more slowly.
1. ⏸ Learner types the 7 files of **L03** (`docs/lessons/L03-error-model-problem-json.md`), runs `mvn verify`, and commits (including the uncommitted `InvalidAmountException` fix).
2. ⏸ Still open: replace both module POMs (L03 Appendix), ADR-0002, Maven wrapper, Git remote / push.
3. ⏸ **Before L04: Docker must work** (L04 needs Postgres). Run the admin `dism … VirtualMachinePlatform` command and restart.
4. ⏸ Learner types **NEXT**. Claude reviews, then teaches **L04 Part A** (identity-service skeleton, register, password hashing).

## Open questions (waiting for learner)

| # | Question | Claude's recommendation |
| --- | --- | --- |
| Q1 | Throughput target in ADR-0002: 50 TPS sustained / 100 TPS spike on the Windows laptop. Accept or change? | Not answered; the recommendation stands by default (2026-10-17). Re-baseline in L22 and again on the Mac |
| Q2 | Accept the 7 architecture changes proposed on 2026-10-17 (see Decisions)? | Not answered; the recommendation stands by default. L01B already uses C5 |
| Q3 | No Git remote yet. Create a **private** GitHub repo and push, so the Mac can clone it. | Still open after L01A. Do it at the end of L01B |

## Decisions

| Date | Decision | Status | Where |
| --- | --- | --- | --- |
| 2026-10-17 | JDK 25 LTS; Spring Boot 4.1.1 is the version anchor; use only Boot-managed versions | Accepted | `docs/adr/0001-tech-stack-versions.md` |
| 2026-10-17 | SLO targets and latency budget | Proposed (Q1) | `docs/adr/0002-service-level-objectives.md` |
| 2026-10-17 | C1: Docker Compose profiles: postgres and redis always run; `messaging` (Kafka) and `observability` are opt-in, because the laptop has 8 GB RAM | Proposed (Q2) | `docker-compose.yml` |
| 2026-10-17 | C2: One Postgres instance in dev, with one database plus `<svc>_owner` (Flyway) and `<svc>_app` (runtime) roles per service. `REVOKE CONNECT ... FROM PUBLIC` enforces DB ownership | Proposed (Q2) | `infrastructure/postgres/init/` |
| 2026-10-17 | C3: `grafana/otel-lgtm` (one container) for dev observability. `infrastructure/monitoring/` still holds our dashboards and alert rules | Proposed (Q2) | ADR-0001 |
| 2026-10-17 | C4: Spring Boot's built-in structured JSON logging (ECS) instead of logstash-logback-encoder | Proposed (Q2) | ADR-0001 |
| 2026-10-17 | C5: Correlation ID lives in `platform-observability`, not `platform-web`, because it must cross HTTP, gRPC and Kafka. `platform-web` keeps the error model and validation | Proposed (Q2) | L01B |
| 2026-10-17 | C6: Kafka in KRaft mode (Kafka 4 has no ZooKeeper), topic auto-create off | Proposed (Q2) | `docker-compose.yml` |
| 2026-10-17 | C7: Every port binds to 127.0.0.1; dev secrets come from a git-ignored `.env` | Proposed (Q2) | `docker-compose.yml`, `.env.example` |
| 2026-10-17 | **T1: No automated test code in the project** (learner's decision). Claude still verifies every lesson with its own scratchpad tests and shows the results. "Break it" becomes a hands-on experiment. k6 (L22) and the failure drills (L33, L40) stay. Claude's risk note: without in-repo tests there is no regression safety net, and "prove no double spend" relies on demos; this can be reversed any time | Accepted (learner) | `CLAUDE.md` working agreement + master prompt amended |

## Lesson status

| # | Lesson | Status | Notes |
| --- | --- | --- | --- |
| L01A | Monorepo, parent POM, Maven wrapper, docker-compose, Postgres init, ADR-0001, ADR-0002 | 🟡 committed (05bc70a), fixes pending | Reviewed 2026-10-17: 8 files exact ✅. Pending: `.gitattributes` is empty, ADR-0002 missing, wrapper not generated, not pushed. Docker checks not yet run |
| L01B | JSON logs, correlation ID, OpenTelemetry (platform-observability) | ✅ 7 source files typed (identical), committed; test files dropped per T1 | Verified in scratchpad: 9/9 tests green; Break it gives 2 failures as designed |
| L02 | Money value object (kobo, rounding) | ✅ typed; `;` fix done (uncommitted); build green. POM cleanup pending | `Money.java` identical ✅. Javadoc comments omitted in 3 files (fine). `platform-money/pom.xml` starts with a blank line before `<?xml` (invalid XML; Maven tolerates it) and still has junit/assertj test deps (unneeded under T1). Tests dropped per T1 | `platform/platform-money`: Money record (long minor units + Currency enum NGN/USD), ofMajor/toMajor, plus/minus with addExact, times(rate, RoundingMode), comparisons. Verified in scratchpad: 18/18 tests green, full build green, Break it (new BigDecimal(0.1) rejected) verified |
| L03 | Error model, validation, problem+json | 🟡 presented, learner typing | `platform/platform-web`: Problems factory (type URI `https://fintechdemo.example/problems/<slug>`, correlationId), FieldViolation, GlobalExceptionHandler (lowest precedence; generic 500; validation errors list; correlationId on Spring's built-in errors), auto-config. Verified in scratchpad with 7 scratchpad-only checks: validation, invalid amount, 500 leaks nothing, malformed JSON, 404, a service advice beats the catch-all even without @Order |
| L04 | Register, login, password hashing, JWT + refresh rotation | ⬜ | |
| L05 | Device binding, transaction PIN, lockout, PIN-verify gRPC | ⬜ | See design flag F1 |
| L06 | API gateway: JWT check, rate limiting, routing | ⬜ | Choose Gateway Server WebMVC vs WebFlux |
| L07 | Accounts, NUBAN-style check digit, balance fields | ⬜ | |
| L08 | Double-entry journal model + append-only tables | ⬜ | See F5 |
| L09 | Atomic posting engine | ⬜ | See F4 |
| L10 | Concurrency proof: 200 parallel debits, deadlocks, isolation | ⬜ | |
| L11 | Holds: reserve, capture, release | ⬜ | |
| L12 | Hot-account problem: sharded system accounts + benchmark | ⬜ | |
| L13 | Ledger gRPC API, deadlines, error mapping | ⬜ | |
| L14 | Balance + history reads: cursor pagination, indexes, partitioning | ⬜ | |
| L15 | transfer-service, Transfer aggregate, state machine | ⬜ | |
| L16 | Durable idempotency + replay protection | ⬜ | |
| L17 | Recipient directory, name masking, Redis cache | ⬜ | |
| L18 | Tier limits, atomic usage counters | ⬜ | Verify CBN tier rules; see F2 |
| L19 | Fees, VAT, levies: effective-dated, quote API | ⬜ | Verify official sources |
| L20 | Send internal P2P: parallel checks, ledger call, unknown outcome | ⬜ | See F2, F3, F4 |
| L21 | Outbox + Kafka + notification-service | ⬜ | |
| L22 | k6 load test, profiling, top bottleneck | ⬜ | Re-baseline SLOs |
| L23 | Beneficiaries | ⬜ | |
| L24 | Request money | ⬜ | |
| L25 | QR P2P | ⬜ | |
| L26 | Scheduled + recurring transfers | ⬜ | |
| L27 | Bulk transfers | ⬜ | |
| L28 | PaymentRail port + NIP simulator | ⬜ | |
| L29 | Name enquiry + bank list | ⬜ | Verify Flutterwave v3 vs v4 |
| L30 | Outbound interbank state machine, holds, status enquiry | ⬜ | |
| L31 | Flutterwave adapter + Resilience4j | ⬜ | |
| L32 | Webhooks + inbound credits via virtual accounts | ⬜ | |
| L33 | Failure drills | ⬜ | |
| L34 | risk-service rules | ⬜ | |
| L35 | Disputes, maker-checker, audit log | ⬜ | |
| L36 | reconciliation-service + exception queue | ⬜ | |
| L37 | Dashboards + alerts | ⬜ | |
| L38 | Security review (OWASP API Top 10) | ⬜ | |
| L39 | CI/CD, Docker images, deployment outline | ⬜ | Re-verify all versions |
| L40 | End-to-end chaos test + interview defence | ⬜ | |

## P2P scope checklist (master prompt §4)

| Feature | Lesson(s) | Status |
| --- | --- | --- |
| Internal P2P by account number | L20 | ⬜ |
| Internal P2P by phone number | L17, L20 | ⬜ |
| Internal P2P by @username tag | L17, L20 | ⬜ |
| Name enquiry with masked name | L17, L29 | ⬜ |
| Outbound interbank via Flutterwave + NIP simulator | L28–L31 | ⬜ |
| Bulk send (partial success) | L27 | ⬜ |
| Scheduled and recurring transfers | L26 | ⬜ |
| QR P2P (signed static + dynamic) | L25 | ⬜ |
| Internal credits | L20 | ⬜ |
| Inbound interbank via virtual accounts + verified webhook | L32 | ⬜ |
| Request money: create, pay, decline, cancel, expire | L24 | ⬜ |
| Transaction PIN, lockout, device binding, step-up | L05 | ⬜ |
| Idempotency on every money command | L16 | ⬜ |
| KYC tier limits | L18 | ⬜ |
| Fees, VAT, levies + quote API | L19 | ⬜ |
| Fraud and risk rules | L34 | ⬜ |
| Holds for in-flight interbank | L11, L30 | ⬜ |
| Debit/credit alerts via outbox + Kafka | L21 | ⬜ |
| History, receipt, status API, statement | L14 | ⬜ |
| Beneficiaries | L23 | ⬜ |
| Reversal after confirmed failure | L30, L33 | ⬜ |
| Disputes + maker-checker | L35 | ⬜ |
| Reconciliation | L36 | ⬜ |
| Admin ops + audit trail | L35 | ⬜ |

## Design flags (raised early, solved in the named lesson)

- **F1 (L05):** PIN hashing is CPU-heavy. At 50 TPS × ~35 ms it takes ~1.75 of this laptop's cores. A 4-digit PIN has only 10,000 values, so a slow hash alone cannot protect a leaked table. A server-side secret key (pepper, ideally in a KMS/HSM) plus attempt lockout matter more. Measure, then decide.
- **F2 (L18, L20):** limit usage lives in the transfer DB and balances in the ledger DB, so no single ACID transaction covers both. Reserve the limit before posting. Release it only when the posting has definitely failed. An unknown outcome keeps the reservation until it is resolved.
- **F3 (L20):** MDC values (correlation ID in logs) do not follow work onto other threads. The parallel checks need context propagation.
- **F4 (L09, L20):** the ledger must be idempotent on its own (unique journal reference), separate from transfer-service's idempotency key, so a retry after an unknown outcome can never post twice.
- **F5 (L08):** the `<svc>_app` role gets only SELECT and INSERT on journal and entry tables, so the database itself enforces append-only.

## Environment notes

### Windows laptop (current)

- Intel i5-6300U, 2 cores / 4 threads, 7.9 GB RAM, Windows 11.
- Temurin JDK 25.0.4.1, Maven 3.10.0 (the wrapper pins 3.10.0), Docker 29.7.2, Compose v5.4.0, Git 2.55.
- `git config core.autocrlf` is `true`. The `.gitattributes` rule `eol=lf` overrides it for this repo.
- Docker Desktop is installed per-user at `%LOCALAPPDATA%\Programs\DockerDesktop`. Start it before `docker compose`.

### Mac setup (later)

1. `brew install --cask temurin@25` (cask confirmed to exist on 2026-10-17)
2. Install Docker Desktop for Mac.
3. `git clone <your-private-repo-url> fintechdemo && cd fintechdemo`
4. `cp .env.example .env`, then set the passwords (`.env` is never committed).
5. `./mvnw validate`. If you get "permission denied", run `chmod +x mvnw`, then `git add --chmod=+x mvnw` and commit.
6. `docker compose up -d`
7. Open Claude Code in the folder. It reads `CLAUDE.md`, then this file.

Claude's local memory stays on the Windows machine. Everything that matters is in this repo.

## Session log

- **2026-10-17**: The project folder was empty (only `.git`; the earlier `common/`, `services/` and `pom.xml` had already been removed). Versions verified from official sources → ADR-0001. Architecture reviewed and changes C1–C7 proposed. Created `CLAUDE.md` (master prompt verbatim plus the resume protocol), this tracker, and `docs/lessons/L01A-monorepo-and-local-infrastructure.md`.
  - L01A verified in Claude's scratchpad: ✅ Maven wrapper generated (3.3.4, only-script, Maven 3.10.0); ✅ `mvnw validate` BUILD SUCCESS from Git Bash and PowerShell, all 3 enforcer rules pass; ✅ enforcer fails with our message on a wrong JDK range; ✅ `docker compose config` valid, and fails clearly without `.env`.
  - Checked against official sources, not run: `REDISCLI_AUTH` (redis.io), explicitly targeted profiled services start (docs.docker.com), the Kafka image creates `/var/lib/kafka/data` owned by `appuser`, and `KAFKA_HEAP_OPTS` is not turned into a broker property (apache/kafka source).
  - ⚠️ NOT verified (Docker engine down): containers starting and becoming healthy, the Postgres init SQL (`\getenv`, `\gexec`, roles, REVOKE), Kafka startup, Grafana UI. Claude runs these on the learner's real repo after NEXT.
- **2026-10-17 (later)**: The learner committed L01A (`05bc70a`) and typed NEXT. The learner's new standing rule: always show the complete folder tree and every file in full (saved in CLAUDE.md). Review: 8 files identical to the verified versions; `.gitattributes` empty; ADR-0002, the Maven wrapper and the remote are missing; Docker is still blocked (no restart). Q1/Q2 unanswered, so the defaults stand.
  - L01B built and verified in scratchpad: `mvnw verify` → 9 tests, 0 failures. Real output line: `"message":"ping received","traceId":"…","spanId":"…","correlationId":"tolu-retry-0001"`. Boot 4.1.1 facts checked in the jars: `org.springframework.boot.EnvironmentPostProcessor` is the current interface and spring.factories key (the `org.springframework.boot.env.` one still exists for compatibility); `FilterRegistrationBean` is in `spring-boot` (`org.springframework.boot.web.servlet`); `AutoConfigureMockMvc` is in `spring-boot-webmvc-test` (`org.springframework.boot.webmvc.test.autoconfigure`); `spring-boot-starter-opentelemetry` brings `micrometer-registry-otlp`, the OTel tracing bridge and the OTLP exporter.
  - The learner asked for "more code, short story" (saved in CLAUDE.md). The learner typed NEXT. Review: the 7 L01B main files are identical and `mvn verify` passes, but the 3 L01B tests are missing; `.gitattributes`, ADR-0002, the wrapper, the commit and the remote are still pending. Docker is still blocked. L02 presented (short format).
  - The learner asked Claude to remove tests from the build in CLAUDE.md and commit all changes. Claude amended CLAUDE.md (working agreement plus master prompt §3, naming table, §6, §8 layout, §9 stack, roadmap L02/L10), recorded decision T1, and committed on `main`. The commit includes L02 with a known compile error (reported to the learner).
  - Noted for later lessons: tracing is active in `@SpringBootTest` (trace IDs appear in test logs). The OTLP metrics registry logs "Publishing metrics … every 1m". Services may log export warnings when the LGTM container is down; check in L04.
