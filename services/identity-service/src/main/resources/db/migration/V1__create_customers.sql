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
