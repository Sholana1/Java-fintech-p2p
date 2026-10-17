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
