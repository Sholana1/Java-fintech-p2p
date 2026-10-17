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
