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
