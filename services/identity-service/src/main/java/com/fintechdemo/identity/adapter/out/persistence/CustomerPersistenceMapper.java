package com.fintechdemo.identity.adapter.out.persistence;

import com.fintechdemo.identity.domain.model.Customer;
import com.fintechdemo.identity.domain.model.CustomerId;
import com.fintechdemo.identity.domain.model.EmailAddress;
import com.fintechdemo.identity.domain.model.KycTier;
import com.fintechdemo.identity.domain.model.PasswordHash;
import com.fintechdemo.identity.domain.model.PhoneNumber;

/** Converts between the domain Customer and the JPA row. */
final class CustomerPersistenceMapper {

    private CustomerPersistenceMapper() {
    }

    static CustomerJpaEntity toEntity(Customer customer) {
        return new CustomerJpaEntity(
            customer.id().value(),
            customer.phoneNumber().value(),
            customer.email().value(),
            customer.firstName(),
            customer.lastName(),
            customer.passwordHash().value(),
            customer.kycTier().name(),
            customer.createdAt());
    }

    static Customer toDomain(CustomerJpaEntity entity) {
        return Customer.restore(
            new CustomerId(entity.getId()),
            new PhoneNumber(entity.getPhoneNumber()),
            new EmailAddress(entity.getEmail()),
            entity.getFirstName(),
            entity.getLastName(),
            new PasswordHash(entity.getPasswordHash()),
            KycTier.valueOf(entity.getKycTier()),
            entity.getCreatedAt());
    }
}
