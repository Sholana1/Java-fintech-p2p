package com.fintechdemo.identity.domain.model;

import com.fintechdemo.identity.domain.exception.InvalidCustomerDetailsException;

import java.time.Instant;
import java.util.Objects;

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

    private Customer(CustomerId id, PhoneNumber phoneNumber, EmailAddress email, String firstName, String lastName, PasswordHash passwordHash, KycTier kycTier, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
        this.email = Objects.requireNonNull(email, "email");
        this.firstName = requireName(firstName, "First name");
        this.lastName = requireName(lastName, "Last name");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.kycTier = Objects.requireNonNull(kycTier, "kycTier");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A brand-new customer: New ID, lowest KYC tier. */
    public static Customer register(PhoneNumber phoneNumber, EmailAddress email, String firstName, String lastName, PasswordHash passwordHash, Instant now) {
        return new Customer(CustomerId.newId(), phoneNumber, email, firstName, lastName, passwordHash, KycTier.TIER_1, now);
    }

    // Rebuild a customer that already exist in storage
    public static Customer restore(CustomerId id, PhoneNumber phoneNumber, EmailAddress email, String firstName, String lastName, PasswordHash passwordHash, KycTier kycTier, Instant createdAt) {
        return new Customer(id, phoneNumber, email, firstName, lastName, passwordHash, kycTier, createdAt);
    }

    private static String requireName(String name, String label) {
        String trimmed = name == null ? "" : name.trim();
        if(trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
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
